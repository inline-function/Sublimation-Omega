package sublimation.frontend.lexer

/**
 * 手写扫描器（S-1 §1）：单源文件字符序列 → 词法单元流。
 *
 * 契约：
 * - **单遍自左向右、最长匹配**（L-1~L-12，分派顺序见 [readToken]）；token 流以 EOF 终结。
 * - **流式**：[tokens] 是惰性序列，可逐个消费、提前终止，为增量编译与 LSP 留路（S-1 §1）。
 * - **带位置**：每个 token 携带源位置（行/列），诊断同样携带位置（S-1 层间总契约 2）。
 * - **错误不停机**：非法字符、字符串未闭合、空反引号标识符（E-BACKTICK-EMPTY）一律记诊断并继续——错误恢复
 *   从第一层开始（S-1 §1 末条）；诊断通过 [errors] 暴露。
 *
 * 单次消费约束：本实现以游标状态推进，[tokens]/[tokenize] 只能完整消费一遍。
 */
class Lexer(private val src: String, private val fileName: String) {
    private var i = 0
    private var line = 1
    private var col = 1
    private val _errors = mutableListOf<LexError>()

    /** 词法诊断快照：应在 token 流消费完成后读取；为空即词法全清。 */
    val errors: List<LexError>
        get() = _errors.toList()

    /** 完整词法：返回含 EOF 结尾的 token 列表。 */
    fun tokenize(): List<Token> = tokens().toList()

    /** 流式词法（S-1 §1）：惰性序列逐个产出至 EOF。 */
    fun tokens(): Sequence<Token> = sequence {
        while (true) {
            val hadSpace = skipTrivial()
            if (i >= src.length) break
            val sl = line
            val sc = col
            val tok = readToken()
            if (tok != null) {
                yield(Token(tok.first, tok.second, sl, sc, hadSpace))
            }
            // readToken 返回 null：非法字符已记诊断并跳过一格，循环继续（S-1 §1 不停机）
        }
        // L-7：EOF 恒带 precededBySpace=⊤
        yield(Token(Kind.EOF, "<eof>", line, col, true))
    }

    /** L-9：标识符读出后查表折算为硬关键字；反引号名（L-6）与普通标识（如 in）不走此表。 */
    private val keywords = mapOf(
        "fun" to Kind.FUN, "trait" to Kind.TRAIT, "struct" to Kind.STRUCT,
        "enum" to Kind.ENUM, "var" to Kind.VAR, "impl" to Kind.IMPL,
        // Q5（t28，卷 §8）：for 释放为普通标识符——唯一用处是 impl，impl 已废除（E-IMPL-REMOVED）
        "if" to Kind.IF, "else" to Kind.ELSE,
        "unchecked" to Kind.UNCHECKED, "when" to Kind.WHEN, "is" to Kind.IS, "by" to Kind.BY,
        "type" to Kind.TYPE, "return" to Kind.RET, "def" to Kind.DEF,
    )

    companion object {
        /** L-8【逻辑单例】：这些 Unicode 符号是固定单 token，禁止并入 SYMBOL 符号名。 */
        // P5 批次 2（t74）：∧ ∨ → ↔ 转名字材料（N1 同通道）—— ∀ ∃ ⊤ ⊥ 保留为固定 token
        val logicalSingletons = setOf('∀', '∃', '⊤', '⊥')

        /** P4（批次 3，用户裁决 N1）【名字材料】：¬ 是标识符字符（¬p = 单个标识符）——
         *  §5 注册表的 1 元原生命题。¬ 与 ∧ ∨ → ↔（t74，P5 批次 2）随之退出【逻辑单例】入名字材料；
         *  ⊤ ⊥ 保留固定 token（Q12：程序位 Bool 字面量 + 命题位恒真恒假的双身份，不可作名字）。 */
        val nameMaterialSingletons = setOf('¬', '∧', '∨', '→', '↔')

        /** 全角标点集合（§1.5/§5.4）：一律词法错误并在诊断中提示改半角——须先于符号名通道拦截。 */
        const val fullWidth = "（）【】＜＞＝，；：＂＇！＆｜"
    }

    /**
     * 跳过 trivia（L-1~L-4）：空白、BOM、// 行注释。
     * 返回是否跳过过任何 trivia——它决定后继 token 的 precededBySpace（L-7）。
     */
    private fun skipTrivial(): Boolean {
        var skipped = false
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\uFEFF' -> { adv(); skipped = true }                        // L-3
                c == ' ' || c == '\t' || c == '\r' || c == '\n' -> { adv(); skipped = true }  // L-2
                c == '/' && i + 1 < src.length && src[i + 1] == '/' -> {           // L-4：仅行注释，无块注释
                    while (i < src.length && src[i] != '\n') adv()
                    skipped = true
                }
                else -> return skipped
            }
        }
        return skipped
    }

    /**
     * 读取一个 token（单遍最长匹配，《形式化文法》§1）。
     * 返回 null 表示当前位置为非法字符——已记诊断并跳过一格，调用方继续循环（S-1 §1 不停机）。
     * 分派顺序不可调换：字面量/反引号/标识符/符号名通道优先，多字符算符（L-12）次之，
     * 单字符分派与 $n 粘连（L-11）最后。
     */
    private fun readToken(): Pair<Kind, String>? {
        val c = src[i]
        if (c in '0'..'9') return readInt()                                       // L-10
        if (c == '"') return readString()                                         // L-10
        if (c == '`') return readBacktickIdent()                                 // L-6
        // L-5/L-8：∵/∴ 是固定单 token（后面的单字符分派），不得作为标识符/符号名首字符
        val isBecauseSym = c == '∵' || c == '∴'
        if (!isBecauseSym && (c == '_' || c.isLetter() || c in nameMaterialSingletons)) return readIdent()       // L-5
        if (!isBecauseSym && isSymbolStart(c)) return readSymbol()                // L-8
        // L-12：多字符算符，固定匹配顺序，顺序不可调换（须先于各单字符）
        if (c == '<' && peekAt(i + 1) == '-' && peekAt(i + 2) == '>') { adv(3); return Kind.IFF to "<->" }
        if (c == '=' && peekAt(i + 1) == '>') { adv(2); return Kind.DARROW to "=>" }
        if (c == '=' && peekAt(i + 1) == '=') { adv(2); return Kind.EQ to "==" }
        if (c == '!' && peekAt(i + 1) == '=') { adv(2); return Kind.NEQ to "!=" }
        if (c == '<' && peekAt(i + 1) == '=') { adv(2); return Kind.LE to "<=" }
        if (c == '>' && peekAt(i + 1) == '=') { adv(2); return Kind.GE to ">=" }
        if (c == '-' && peekAt(i + 1) == '>') { adv(2); return Kind.ARROW to "->" }
        if (c == '∃' && peekAt(i + 1) == '!') { adv(2); return Kind.EXISTS1 to "∃!" }
        if (c == '+' && peekAt(i + 1) == '∵') { adv(2); return Kind.PLUS_BECAUSE to "+∵" }
        if (c == '.' && peekAt(i + 1) == '.') { adv(2); return Kind.RANGE to ".." }
        if (c == ':' && peekAt(i + 1) == ':') { adv(2); return Kind.COLON2 to "::" }
        // t83（ADR-51,Q21 终态）：&&/|| 停止归一映射 ∧/∨——Kind.LAND/LOR 仅保留为**拒绝锚点**：
        // 文本保留用户实际写法（供 Parser 落 E-OP-RETIRED 时引用），Parser 中缀循环顶见之即拒，不入 binops。
        if (c == '&' && peekAt(i + 1) == '&') { adv(2); return Kind.LAND to "&&" }
        if (c == '|' && peekAt(i + 1) == '|') { adv(2); return Kind.LOR to "||" }

        // L-11：$ 紧贴数字粘连为单 DOLLAR token（文本含 $）；单独的 $ 是返回值符号
        if (c == '$') {
            adv()
            if (i < src.length && src[i] in '0'..'9') {
                val sb = StringBuilder("$")
                while (i < src.length && src[i] in '0'..'9') { sb.append(src[i]); adv() }
                return Kind.DOLLAR to sb.toString()
            }
            return Kind.DOLLAR to "$"
        }

        // 单字符分派：ASCII 标点/算符 + Unicode 固定单 token
        val one = when (c) {
            '(' -> Kind.LPAREN; ')' -> Kind.RPAREN
            '[' -> Kind.LBRACKET; ']' -> Kind.RBRACKET
            '{' -> Kind.LBRACE; '}' -> Kind.RBRACE
            ',' -> Kind.COMMA; ';' -> Kind.SEMI; ':' -> Kind.COLON; '.' -> Kind.DOT
            '@' -> Kind.AT; '\\' -> Kind.BACKSLASH; '#' -> Kind.HASH
            '+' -> Kind.PLUS; '-' -> Kind.MINUS; '*' -> Kind.STAR; '/' -> Kind.SLASH; '%' -> Kind.PERCENT; '^' -> Kind.POW
            '=' -> Kind.ASSIGN; '<' -> Kind.LT; '>' -> Kind.GT
            '&' -> Kind.AMP; '|' -> Kind.PIPE; '!' -> Kind.BANG
            '∀' -> Kind.FORALL; '∃' -> Kind.EXISTS
            // P5 批次 2（t74）：∧ ∨ → ↔ 已转名字材料（上方 readIdent 通道先收）。
            // t83 终态：`&&`/`||` 停止归一——LAND/LOR 保留产出仅作拒绝锚点（活枚举），Parser 即拒；
            // IMPLIES/EQUIV 无 Unicode 产出（死枚举量保留，S-4 同 LNOT 口径——两类口径勿混）
            '⊤' -> Kind.TOP; '⊥' -> Kind.BOT
            '∵' -> Kind.BECAUSE; '∴' -> Kind.THEREFORE
            // ≠（U+2260）不在【逻辑单例】，被 readSymbol 通道先收为 SYMBOL（§1.2 注 5），
            // 故此处无单字符分支——特此溯源，避免误补 NEQ 分支制造不可达代码（S-4 禁死代码）
            else -> {
                // S-1 §1：非法字符不停机——记诊断（全角命中附提示）并跳过，返回 null 让循环继续
                val hint = if (c in fullWidth) "（这是中文全角符号，请换成英文半角符号）" else ""
                _errors += LexError(fileName, line, col, "这个字符 '" + c + "' 语言不认识" + hint)
                adv()
                return null
            }
        }
        adv()
        return one to c.toString()
    }

    /**
     * L-10：整数字面量 数字+；浮点 数字+ "." 数字+。
     * 点号后必须紧跟数字才粘连：1. 与 1.x 都是 INT 紧跟 DOT。
     */
    private fun readInt(): Pair<Kind, String> {
        val sb = StringBuilder()
        while (i < src.length && src[i] in '0'..'9') { sb.append(src[i]); adv() }
        val afterDot = peekAt(i + 1)
        if (i < src.length && src[i] == '.' && afterDot != null && afterDot in '0'..'9') {
            sb.append('.'); adv()
            while (i < src.length && src[i] in '0'..'9') { sb.append(src[i]); adv() }
            return Kind.FLOAT to sb.toString()
        }
        return Kind.INT to sb.toString()
    }

    /**
     * L-10：字符串字面量——内容可含换行（词法不挡）；转义 n/t/r/"/\，其余 \x 原样保留该字符。
     * 未闭合（遇 EOF）不停机：记诊断，已读内容作为 STR 恢复 token 返回（S-1 §1）。
     */
    private fun readString(): Pair<Kind, String> {
        val sl = line
        val sc = col
        adv() // 开引号
        val sb = StringBuilder()
        while (true) {
            if (i >= src.length) {
                _errors += LexError(fileName, sl, sc, "字符串未闭合")
                return Kind.STR to sb.toString()
            }
            val c = src[i]
            when {
                c == '"' -> { adv(); return Kind.STR to sb.toString() }
                c == '\\' -> {
                    adv()
                    if (i >= src.length) {
                        _errors += LexError(fileName, sl, sc, "字符串未闭合")
                        return Kind.STR to sb.toString()
                    }
                    val e = when (src[i]) {
                        'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'
                        '"' -> '"'; '\\' -> '\\'; else -> src[i]
                    }
                    sb.append(e); adv()
                }
                else -> { sb.append(c); adv() }
            }
        }
    }

    /**
     * L-6：反引号名——内容为除反引号与换行外的任意字符；一律 IDENT，不查关键字表
     *（`when` 是普通名字而非关键字）。
     * 不停机（S-1 §1）：跨行与未闭合记诊断，已读内容作为 IDENT 恢复 token 返回，
     * 换行符留待 trivia 通道处理（行号正常推进）。
     */
    /**
     * L-6 反引号标识符（t68，用户 2026-11-08 裁决放开）：内容任意字符——结构字符、ASCII 符号、
     * CJK、空格、**裸换行**（B1）；唯一转义 \'` = 字面反引号（B2）；反引号本身不进名字，
     * `foo` 与 foo 同名（B3）；单个/多个空格是合法名字（B4）；空名落 E-BACKTICK-EMPTY（B5）。
     * 诊断一律指向 token 起始位置；裸换行随 adv() 推进行号（token 记起始行）。
     */
    private fun readBacktickIdent(): Pair<Kind, String> {
        val sl = line
        val sc = col
        adv() // 开反引号
        val sb = StringBuilder()
        while (true) {
            if (i >= src.length) {
                _errors += LexError(fileName, sl, sc, "反引号标识符未闭合")
                return Kind.IDENT to sb.toString()
            }
            val c = src[i]
            when {
                c == '\\' && i + 1 < src.length && src[i + 1] == '`' -> { sb.append('`'); adv(); adv() }   // B2：\\'` = 字面反引号
                c == '`' -> {
                    adv()
                    if (sb.isEmpty()) _errors += LexError(fileName, sl, sc,
                        "空反引号标识符——零长度名字无意义", code = "E-BACKTICK-EMPTY")   // B5
                    return Kind.IDENT to sb.toString()
                }
                c == '\r' -> adv()   // CRLF 行尾：CR 不进名字（与字符串口径一致）
                else -> { sb.append(c); adv() }   // B1：含裸 \n，行号随 adv() 推进
            }
        }
    }

    /**
     * L-5：Unicode 标识符——字母/下划线为首，字母/数字/下划线续（isLetterOrDigit 覆盖中文、
     * 希腊字母等）；读后查关键字表折算（L-9）。
     */
    private fun readIdent(): Pair<Kind, String> {
        val sb = StringBuilder()
        while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_' || src[i] in nameMaterialSingletons)) {
            sb.append(src[i]); adv()
        }
        // $ 段粘连（合成名 recomposion$Box$1）：仅当 $ 不在开头且后随字母/下划线时续读——
        // 开头的 $1 走 L-11 参数引用通道；内部 $ 后随数字（如 a$1）不粘连，同样留给 L-11。
        // 段尾再吃可选的 "$"+数字（合成名的次序段），幂等循环处理多段。
        while (i < src.length && src[i] == '$' && i + 1 < src.length &&
            (src[i + 1].isLetter() || src[i + 1] == '_')) {
            sb.append(src[i]); adv()   // $
            while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) { sb.append(src[i]); adv() }
            if (i < src.length && src[i] == '$' && i + 1 < src.length && src[i + 1] in '0'..'9') {
                sb.append(src[i]); adv()   // 次序位的 $
                while (i < src.length && src[i] in '0'..'9') { sb.append(src[i]); adv() }
            }
        }
        val text = sb.toString()
        return (keywords[text] ?: Kind.IDENT) to text
    }

    /**
     * L-8【符号首】：非 ASCII、非空白、非字母数字、且不在【逻辑单例】的符号字符。
     * 全角标点另需剔除：它们是词法错误（§1.5/§5.4，带改半角提示），不得被符号名通道吞没——
     * 旧实现未剔除使该提示分支不可达，此为落实文法保留的修正（见 ADR-2）。
     */
    private fun isSymbolStart(c: Char): Boolean =
        c.code > 127 && !c.isWhitespace() && !c.isLetterOrDigit() &&
            c !in logicalSingletons && c !in nameMaterialSingletons && c !in fullWidth

    /** L-8：读连续符号字符为一个 SYMBOL 标识符（○、⊗、∈…），遇非符号首字符停止。 */
    private fun readSymbol(): Pair<Kind, String> {
        val sb = StringBuilder()
        while (i < src.length) {
            val c = src[i]
            if (isSymbolStart(c)) { sb.append(c); adv() } else break
        }
        return Kind.SYMBOL to sb.toString()
    }

    private fun adv() {
        if (src[i] == '\n') { line++; col = 1 } else col++
        i++
    }

    private fun adv(n: Int) { repeat(n) { adv() } }

    private fun peekAt(j: Int): Char? = if (j < src.length) src[j] else null
}
