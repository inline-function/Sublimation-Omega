package sublimation.frontend.parser

import sublimation.frontend.ast.*
import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.Severity
import sublimation.frontend.diag.SourcePos
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.lexer.Kind
import sublimation.frontend.lexer.Lexer
import sublimation.frontend.lexer.Token

/**
 * 手写递归下降语法分析器（S 层短语结构 + D 层邻接消歧，《形式化文法》§2/§3）。
 *
 * 契约：
 * - **上下文敏感分派**：换行不是 token，语句边界由邻接规则涌现（§0 记号约定）；
 *   全部消歧收敛为 D-1~D-21 的有限规则（§3.4 邻接消歧全表），判据来源是 token 的
 *   [Token.precededBySpace]（L-7）与同行关系，语法层不查符号表（零语义，S-1 §2）。
 * - **容错解析**（S-1 §2）：语法错误不抛异常——记 [Diag]（E-SYNTAX）并在错误处产出
 *   Error* 占位节点（可挽救的局部子树保留在相邻字段）；每个列表循环带进展保证
 *   （[ensureProgress]），解析必终止且不丢 token。保留异常的只有片段入口的宿主契约
 *   （[strict] + [ParseFailure]）。
 * - **解析期脱糖**（§2.13）：D-INTP 字符串插值、S-1 标识符中缀糖、S-1a in 调用、
 *   S-3 量词 ASCII 别名、S-4 E::A 折叠、S-5 无特质 impl 展开、S-7 模板 when、
 *   S-8 裸 ∵/∴ 补 ⊤、S-9 尾随 lambda、S-10 ifis→when。脱糖留痕经 [ParseNote] 挂节点（D-9）。
 * - 位置由 [Token.range] 折算（D-9/D-10）；节点定位口径沿用旧实现（函数名/成员名/初等项 token）。
 */
class Parser(
    private val toks: List<Token>,
    private val fileName: String,
    private val scriptMode: Boolean = false,
    /** 片段入口（宿主契约）：解析末尾若有任何 ERROR 级诊断即抛 [ParseFailure]。文件入口恒为 false。 */
    private val strict: Boolean = false,
) {
    /** 解析游标（指向当前待消费 token）。 */
    private var pos = 0

    /** 本文件已声明的特质名（impl 的 Trait[…] 简写判定，S-12）。 */

    /** 游标安全读取：进展保证可能把游标推过 EOF token 位置。 */
    private fun cur(): Token = toks[minOf(pos, toks.size - 1)]
    private fun peek(kind: Kind): Boolean = cur().kind == kind
    private fun peekAt(offset: Int): Token? = toks.getOrNull(pos + offset)
    private fun next(): Token = toks[pos++]

    /** 期望消费某类 token：不符则记诊断、**不消费**（由调用方决定恢复路径）。 */
    private fun expect(kind: Kind, what: String = kind.name): Token? {
        val t = cur()
        if (t.kind != kind) {
            diagError("期望$what，实际'${t.text}'", t)
            return null
        }
        return next()
    }

    // ---------- 诊断、留痕与错误占位（S-1 §2）----------

    private val _diag = mutableListOf<Diag>()
    private val _notes = mutableMapOf<AstNode, MutableList<ParseNote>>()

    /** 解析诊断快照（词法诊断在入口处并入同袋——层间总契约 2）。 */
    internal fun diag(): List<Diag> = _diag.toList()

    /** 节点级留痕（D-9）：去重后冻结，供 HIR 构造期搬移到 HirNode.notes。 */
    internal fun notes(): Map<AstNode, List<ParseNote>> = _notes.mapValues { it.value.distinct() }

    /** code 默认 E-SYNTAX；P-8/B（t28）废除性形态（如 impl）以专属码落诊（卷 TS §9）。 */
    private fun diagError(msg: String, t: Token, code: String = "E-SYNTAX") {
        _diag += Diag(Severity.ERROR, code, t.range(), msg)
    }

    /** AST 节点位置版的诊断（成员级核对：常量规范/函数规范违规挂在节点 pos 上）。 */
    private fun diagErrorPos(msg: String, pos: SourceRange, code: String = "E-SYNTAX") {
        _diag += Diag(Severity.ERROR, code, pos, msg)
    }

    /** 脱糖留痕：挂在脱糖产出的节点上（ADR-1 §3.5 旁挂通道）。 */
    private fun note(node: AstNode, code: String, msg: String, t: Token) {
        _notes.getOrPut(node) { mutableListOf() } += ParseNote(code, msg, t.range())
    }

    private fun errExpr(msg: String, t: Token): ErrorExpr {
        diagError(msg, t)
        return ErrorExpr(msg, t.range())
    }

    private fun errDecl(msg: String, t: Token, code: String = "E-SYNTAX"): ErrorDecl {
        diagError(msg, t, code)
        return ErrorDecl(msg, t.range())
    }

    private fun errStmt(msg: String, t: Token): ErrorStmt {
        diagError(msg, t)
        return ErrorStmt(msg, t.range())
    }

    private fun errType(msg: String, t: Token): ErrorType {
        diagError(msg, t)
        return ErrorType(msg, t.range())
    }

    private fun errPattern(msg: String, t: Token): ErrorPattern {
        diagError(msg, t)
        return ErrorPattern(msg, t.range())
    }

    /**
     * 列表循环的进展保证（容错终止条件，S-1 §2）：单项解析未消费任何 token 时
     * 跳过当前符号并记诊断——entry/语句/成员/分支各层循环由此必终止且不丢 token。
     */
    private fun ensureProgress(before: Int) {
        if (pos == before && !peek(Kind.EOF)) {
            diagError("无法继续解析，跳过符号 '${cur().text}'", cur())
            pos++
        }
    }

    /** token 的源位置区间（D-9/D-10）：跨行字符串的 end 未知（单行 token 可算末列）。 */
    private fun Token.range(): SourceRange =
        SourceRange(SourcePos(line, col), if ('\n' in text) null else SourcePos(line, col + text.length - 1))

    private fun skipSemis() { while (peek(Kind.SEMI)) pos++ }

    /** 跳过一对配对花括号（含嵌套）——误写体的恢复：整段作废、游标出到括号后。 */
    private fun skipBraced() {
        if (!peek(Kind.LBRACE)) return
        var depth = 0
        while (!peek(Kind.EOF)) {
            when (cur().kind) {
                Kind.LBRACE -> depth++
                Kind.RBRACE -> { depth--; if (depth == 0) { pos++; return } }
                else -> {}
            }
            pos++
        }
    }

    /** 跳过一对配对圆括号（含嵌套）——模式位误写 `A(…)` 的恢复。 */
    private fun skipParens() {
        if (!peek(Kind.LPAREN)) return
        var depth = 0
        while (!peek(Kind.EOF)) {
            when (cur().kind) {
                Kind.LPAREN -> depth++
                Kind.RPAREN -> { depth--; if (depth == 0) { pos++; return } }
                else -> {}
            }
            pos++
        }
    }

    /** 声明同步边界：入口关键字/分号/成员闭合括号——破损声明的残段跳到这里为止（含成员语境）。 */
    private val DECL_SYNC = setOf(
        Kind.SEMI, Kind.FUN, Kind.TRAIT, Kind.STRUCT, Kind.ENUM, Kind.IMPL, Kind.TYPE, Kind.DEF, Kind.AT,
        Kind.RBRACE, Kind.RBRACKET, Kind.RPAREN,
    )

    /** 声明级失败占位 + 同步：跳到声明边界——无名可救的破损声明其残段不进 AST（S-1 §2 局部恢复）。 */
    private fun errDeclSync(msg: String, t: Token): ErrorDecl {
        val d = errDecl(msg, t)
        while (cur().kind !in DECL_SYNC && !peek(Kind.EOF)) pos++
        return d
    }

    /** 名字 token 判定（函数名/形参名/理论参数名通用通道：标识符、符号名与可作名的算符）。 */
    private fun nameToken(): String? {
        val t = cur()
        return when (t.kind) {
            Kind.IDENT, Kind.SYMBOL, Kind.PLUS, Kind.MINUS, Kind.STAR, Kind.SLASH, Kind.PERCENT,
            Kind.EQ, Kind.NEQ, Kind.LE, Kind.GE, Kind.AMP, Kind.PIPE, Kind.IFF, Kind.LT, Kind.GT,
            -> next().text
            else -> { diagError("期望名字（标识符或运算符），实际'${t.text}'", t); null }
        }
    }

    // ============ 文件与顶层（S-1/S-2/S-3）===========

    internal fun parseFile(): FileAst {
        val entries = ArrayList<Entry>()
        while (true) {
            skipSemis()
            if (peek(Kind.EOF)) break
            val before = pos
            val e = parseEntry()
            ensureProgress(before)
            entries += e
        }
        return FileAst(entries)
    }

    /** S-2【顶层条目】分派：声明关键字 → 声明；@ 前缀 → @x fun/struct/trait 放行；其余脚本模式→语句、工程模式→仅 axiom。 */
    private fun parseEntry(): Entry = when (cur().kind) {
        Kind.FUN, Kind.TRAIT, Kind.STRUCT, Kind.ENUM, Kind.IMPL, Kind.TYPE, Kind.DEF -> DeclEntry(parseDecl())
        Kind.AT -> {
            val save = pos
            val anns = parseAnnotations()
            when (cur().kind) {
                Kind.FUN -> DeclEntry(parseFun(anns))
                Kind.STRUCT -> DeclEntry(parseStruct(anns))
                Kind.TRAIT -> DeclEntry(parseTrait(anns))
                else -> if (scriptMode) { pos = save; StmtEntry(parseStmt()) } else noTopLevelEntry()
            }
        }
        else -> if (scriptMode || (cur().kind == Kind.IDENT && cur().text == "axiom")) StmtEntry(parseStmt())
        else noTopLevelEntry()
    }

    /** 决策 74（工程模式）：顶层只允许声明与 axiom 语句——记诊断 + ErrorStmt 占位 + 同步到下一入口边界。 */
    private fun noTopLevelEntry(): Entry {
        val t = cur()
        // t53（P-9 收口 R3 口径差）：工程模式顶层若为废除关键字（val/enum/def/impl），优先给
        // 专属废除诊断 + 迁移指引，而非通用「顶层只能写声明」——诊断第一职责是告知语法已废止
        val rk = removedKeywordMsg(t)
        if (rk != null) {
            diagError(rk.second, t, rk.first)
            skipRemovedDeclBody()
            return StmtEntry(ErrorStmt(rk.second, t.range()))
        }
        val msg = "顶层只能写声明，执行代码请放进 `fun main()`（脚本模式后续支持）"
        diagError(msg, t)
        while (cur().kind !in ENTRY_SYNC && !peek(Kind.EOF)) pos++
        return StmtEntry(ErrorStmt(msg, t.range()))
    }

    /** 特质成员边界（TS-6 收紧后成员循环同步用）。 */
    private val TRAIT_BODY_SYNC = setOf(
        Kind.RBRACE, Kind.SEMI, Kind.FUN, Kind.TRAIT, Kind.STRUCT, Kind.ENUM, Kind.AT,
    )

    /** 入口同步边界：分号、声明关键字、注解前缀（跳到下一可识别入口）。 */
    private val ENTRY_SYNC = setOf(
        Kind.SEMI, Kind.FUN, Kind.TRAIT, Kind.STRUCT, Kind.ENUM, Kind.IMPL, Kind.TYPE, Kind.DEF, Kind.AT,
    )

    // ============ 声明（S-5~S-14）===========

    internal fun parseDecl(): Decl = when (cur().kind) {
        Kind.FUN -> parseFun(emptyList())
        Kind.TRAIT -> parseTrait(emptyList())
        Kind.STRUCT -> parseStruct(emptyList())
        // P-9（2026-11-06）：enum/def 废除——保留关键字仅作错误入口（同 impl 先例，附录 B.1/B.2）
        Kind.ENUM -> parseEnumRemoved()
        Kind.IMPL -> parseImpl()
        Kind.TYPE -> parseTypeAlias()
        Kind.DEF -> parseDefRemoved()
        else -> errDecl("声明（顶层只能写 fun/struct/trait/type 声明；执行语句请放进 `fun main() { … }` 里）", cur())
    }

    /** S-13【类型别名】：type 名 [理论参数] = 类型（B.4：右值是完整类型——含 `|` 联合与自引用归纳形）。 */
    private fun parseTypeAlias(): Decl {
        val t = next()   // type（分派保证）
        val name = nameToken() ?: return errDeclSync("期望类型别名名", cur())
        val theory = if (peek(Kind.LBRACKET)) parseTheory() else emptyList()
        if (expect(Kind.ASSIGN, "=") == null) return errDeclSync("类型别名须以 = 给出右值类型", cur())
        return TypeAliasDecl(name, theory, parseType(), t.range())
    }

    /**
     * S-14【命题定义→废除】（t40，P-9，附录 B.2）：`def` 不再是语言要素。
     * 命题无需预先定义即可直接使用；需要等价式请以 `axiom` 写出。
     * 解析到即报 E-DEF-REMOVED + 指引并产 ErrorDecl；头部的 < … > 角组与 ↔ 等价式残段须配平越过
     * （同 parseImpl 的同步口径：[ ] ( ) 配平、遇 { 整段吞体、入口边界停）。
     */
    private fun parseDefRemoved(): Decl {
        val defTok = next()   // def（分派保证）
        val (code, msg) = removedKeywordMsg(defTok)!!   // t53：文案与语句位同一真相
        val d = errDecl(msg, defTok, code)
        skipRemovedDeclBody()
        return d
    }

    /** t53（P-9 收口 R3 口径差）：废除关键字统一诊断文案（val/enum/def/impl）——语句位、
     * 工程模式顶层与既有的声明位共用同一文案，杜绝「同关键字不同位置不同真相」。 */
    private fun removedKeywordMsg(t: Token): Pair<String, String>? = when {
        t.kind == Kind.IDENT && t.text == "val" ->
            "E-VAL-REMOVED" to ("val 已废除（P-9，2026-11-06）。可变引用一律用 `var` 声明：" +
                "var 只允许在脚本模式顶层与函数体内（工程模式顶层执行代码请放进 `fun main()`，" +
                "特质体禁止字段声明，数据请放在 struct 的字段里）")
        t.kind == Kind.ENUM ->
            "E-ENUM-REMOVED" to ("enum 已废除（P-9，2026-11-06）。枚举请用结构体 + 联合类型别名表达：\n" +
                "  struct A\n  struct B(payload: Nat)\n  type E = A | B")
        t.kind == Kind.DEF ->
            "E-DEF-REMOVED" to "def 已废除（P-9，2026-11-06）：命题无需预先定义即可直接使用；需要等价式请用 axiom 写出"
        t.kind == Kind.IMPL ->
            "E-IMPL-REMOVED" to ("impl 已废除（P-8 类型系统改向）：请改写为扩展函数——" +
                "「impl T { fun f(a: A): R }」/「impl C for T { … }」→「fun T.f(a: A): R」；「by 字段」委托废除，请手工转发")
        else -> null
    }

    /** t53：当前 token 是否废除关键字（val 为词法 IDENT，按文本识别；enum/def/impl 为关键字 token）。 */
    private fun isRemovedKeyword(t: Token): Boolean = removedKeywordMsg(t) != null

    /** t53：语句位废除关键字——落专属废除诊断 + 迁移指引，并同步到语句边界
     * （; / } 于深度 0 停；[ ] ( ) 配平越过；遇 { 整段吞体——同 skipRemovedDeclBody 的容错口径）。 */
    private fun removedKeywordStmt(t: Token): Stmt {
        val (code, msg) = removedKeywordMsg(t)!!
        diagError(msg, t, code)
        pos++   // 越过关键字本身
        var depth = 0
        while (!peek(Kind.EOF)) {
            when (cur().kind) {
                Kind.LBRACKET, Kind.LPAREN -> depth++
                Kind.RBRACKET, Kind.RPAREN -> { if (depth > 0) depth-- }
                Kind.LBRACE -> { skipBraced(); continue }
                else -> if (depth == 0 && (cur().kind == Kind.SEMI || cur().kind == Kind.RBRACE)) break
            }
            pos++
        }
        return ErrorStmt(msg, t.range())
    }

    private fun parseAnnotations(): List<String> {
        val out = ArrayList<String>()
        while (peek(Kind.AT)) {
            pos++
            val n = expect(Kind.IDENT, "注解名")?.text ?: break
            out += n
        }
        return out
    }

    /**
     * S-7【函数声明】：fun 名 [理论参数] [形参表] [头部角组] [: 类型 [角组]] [体]——顺序固定（S-7 备注）。
     * 体两形态互斥（D-11）：= 表达式 | { 块 }；签名/定理可无体。
     * 位置取函数名 token（rename/definition 定位锚点，不指向 fun 关键字本身）。
     */
    internal fun parseFun(anns: List<String>): Decl {
        next()   // fun（分派保证）
        // S-43 修订（t37）：ExtPrefix 的 Receiver 增加括号化形态——fun (Type).name。
        // 判据**确定性**（无回溯、不吞错）：S-43 函数名位无任何以 ( 起始的产生式（形参表须先有
        // 函数名），故 fun 之后**直接**遇 ( 必然是括号化接收者的左括号；LBRACKET 起首不属于此形。
        // 内层类型由 parseType 原地承接（S-15 的 (…) 分组/元组/函数类型与联合尾皆可用：单层括号
        // 即分组，(Nat) ≡ Nat；((T) => U) 为函数类型；(A | B) 为联合）。
        val receiver: Type? = if (peek(Kind.LPAREN)) {
            val lp = cur()
            if (peekAt(1)?.kind == Kind.RPAREN)
                return errDeclSync("括号化接收者不能为空：期望 (类型).名字，实际 ()", lp)
            val rt = parseType()
            if (!peek(Kind.DOT)) {
                // 缺 . 与函数名：已吃到函数类型者，是漏了改变优先级的外层括号（=> 会吞掉 . 与名字）
                val hint = if (rt is FunType) "；函数类型接收者须整体再加一层括号改变优先级，写作 ( (T) => U ).f 形"
                    else "；括号化接收者须写作 (类型).名字"
                return errDeclSync("期望 . 与函数名$hint", cur())
            }
            pos++   //（. 已判实）
            rt
        } else {
            // TS-9（t28，P-8/B）：扩展函数——fun Receiver.name[…]。邻接判据（ADR-23 §2.1）：
            // 点号链（≥2 段）出现即判为扩展函数；Receiver = 末段前的一切（类型表达式，可含实参/嵌套/限定）。
            // 前瞻只读不消费、不发诊断；无点号时按普通函数名原路解析（零回退副作用）。
            val chain = scanDottedChain()
            if (chain.size >= 2) {
                val rt = parseReceiver(chain.size - 1)
                expect(Kind.DOT, ".")   // Receiver 与函数名的确定性分隔
                rt
            } else null
        }
        val nameTok = cur()
        val name = nameToken() ?: return errDeclSync("期望函数名", nameTok)
        val theory = if (peek(Kind.LBRACKET)) parseTheory() else emptyList()
        val params = if (peek(Kind.LPAREN)) parseParams() else emptyList()
        var constraints: List<Expr> = emptyList()
        val preps = if (peek(Kind.LT)) {
            if (constraintListAhead()) { constraints = parseConstraintList(); emptyList() } else parseAngleList()
        } else emptyList()
        var ret: Type? = null
        var posts: List<Expr> = emptyList()
        if (peek(Kind.COLON)) {
            pos++
            if (peek(Kind.LT)) posts = parseAngleList()        // : <q> 无返回类型的后置上下文
            else { ret = parseType(); if (peek(Kind.LT)) posts = parseAngleList() }   // : T <q>
        }
        val body: Expr? = when {
            peek(Kind.ASSIGN) && peekAt(1)?.kind == Kind.LBRACE -> {
                // D-11：= 与 { 互斥——诊断 + ErrorExpr 占位，混写体整段跳过（片段入口经 strictFail 转抛）
                val lb = peekAt(1)!!
                pos++   // =
                skipBraced()
                errExpr("函数体不能 `= {…}` 混写：单表达式请去掉花括号写 `= 表达式`，块体请去掉等号写 `{ … }`", lb)
            }
            peek(Kind.ASSIGN) -> { pos++; parseExpr(angle = false) }
            peek(Kind.LBRACE) -> parseBlock()
            else -> null
        }
        return FunDecl(name, anns, theory, params, preps, ret, posts, body, constraints, nameTok.range(), receiver)
    }

    /**
     * TS-9（t28，P-8/B）：前瞻扫描 fun 后的「名 [类型实参] . 名 [类型实参] . …」链。
     * **只读**（不移动 pos、不发诊断）：段名间方括号按配平跳过；末段后的方括号归理论参数。
     * 返回段名表：≥2 段 ⇒ 扩展函数（末段=函数名，其余=Receiver 链）。
     */
    private fun scanDottedChain(): List<String> {
        val out = ArrayList<String>()
        var p = pos
        while (p < toks.size) {
            val t = toks[p]
            if (t.kind != Kind.IDENT) break
            out += t.text
            p++
            if (p < toks.size && toks[p].kind == Kind.LBRACKET) {   // 段类型实参：配平跳过
                var depth = 0
                while (p < toks.size) {
                    when (toks[p].kind) {
                        Kind.LBRACKET -> depth++
                        Kind.RBRACKET -> { depth--; if (depth == 0) { p++; break } }
                        else -> {}
                    }
                    p++
                }
            }
            if (p < toks.size && toks[p].kind == Kind.DOT) { p++; continue }
            break
        }
        return out
    }

    /** TS-9（t28）：按前瞻段数解析 Receiver（末段前的类型表达式链：单段 NamedType，多段 QualifiedType）。 */
    private fun parseReceiver(segCount: Int): Type {
        val firstTok = cur()
        val segs = ArrayList<Pair<String, List<Type>>>()
        repeat(segCount) {
            if (segs.isNotEmpty()) expect(Kind.DOT, ".")
            val nm = nameToken() ?: return errType("期望接收者类型段", cur())
            segs += nm to (if (peek(Kind.LBRACKET)) { pos++; parseTypeArgs() } else emptyList())
        }
        return if (segs.size == 1) NamedType(segs[0].first, segs[0].second, firstTok.range())
        else QualifiedType(segs.map { it.first }.dropLast(1), segs.last().first, segs.last().second, firstTok.range())
    }

    /** D-13 约束表前瞻：角括内首项 IDENT 且其后是**带空格的**比较/种类运算（紧贴的 valid<7> 实例化不算）。 */
    private fun constraintListAhead(): Boolean {
        val nm = peekAt(1)
        val op = peekAt(2)
        if (nm?.kind != Kind.IDENT || op == null || !op.precededBySpace) return false
        return op.kind in setOf(Kind.LE, Kind.GE, Kind.EQ, Kind.NEQ) || (op.kind == Kind.IDENT && op.text == "in")
    }

    /** v3.0 约束表（<score <= 100>）：只登记不校验。 */
    private fun parseConstraintList(): List<Expr> {
        next()   // <（分派保证）
        val out = ArrayList<Expr>()
        while (true) {
            val cnameTok = cur()
            val cname = nameToken() ?: break
            val opTok = cur()
            pos++
            val rhs = parseExpr(angle = true)
            out += if (opTok.text == "in")
                CallExpr(NameRef("in", cnameTok.range()), listOf(NameRef(cname, cnameTok.range()), rhs), pos = cnameTok.range())
            else BinExpr(opTok.text, NameRef(cname, cnameTok.range()), rhs, opTok.range())
            if (peek(Kind.COMMA)) { pos++; if (peek(Kind.GT)) break; continue }
            break
        }
        expect(Kind.GT, ">")
        return out
    }

    /** S-8【理论参数】：[@p] / [T] / [T: Show] / [F[_]] / [F[_, _]]。 */
    private fun parseTheory(): List<TheoryParam> {
        next()   // [（分派保证）
        val out = ArrayList<TheoryParam>()
        while (true) {
            if (peek(Kind.AT)) {
                pos++
                val nt = cur()
                val n = nameToken() ?: break
                out += PropParam(n, nt.range())
            } else {
                val nt = cur()
                val n = nameToken() ?: break
                var arity = 0
                if (peek(Kind.LBRACKET)) {
                    pos++
                    while (true) {
                        if (peek(Kind.IDENT) && cur().text == "_") { pos++; arity++ }
                        else { diagError("期望构造子占位 _", cur()); break }
                        if (peek(Kind.COMMA)) { pos++; if (peek(Kind.RBRACKET)) break; continue }
                        break
                    }
                    expect(Kind.RBRACKET, "]")
                }
                val c = if (peek(Kind.COLON)) { pos++; nameToken() } else null
                out += TypeParam(n, c, arity, nt.range())
            }
            if (peek(Kind.COMMA)) { pos++; if (peek(Kind.RBRACKET)) break; continue }
            break
        }
        expect(Kind.RBRACKET, "]")
        return out
    }

    /** S-8【形参表】：注解 [解构] 名: 类型 [= 默认值]——默认值一律解析，合法性由语义层判（仅 struct 字段允许）。 */
    private fun parseParams(): List<Param> {
        next()   // (（分派保证）
        val out = ArrayList<Param>()
        if (!peek(Kind.RPAREN)) {
            while (true) {
                val before = pos
                val anns = ArrayList<String>()
                while (peek(Kind.AT)) {
                    pos++
                    val at = cur()
                    val n = expect(Kind.IDENT, "注解名")?.text ?: break
                    // t95：@ref 是类型位注解（写 a: @ref T）——形参名前的注解位是值注解通道，混用落语法诊断
                    if (n == "ref") diagError("@ref 是类型位注解：写在类型上（a: @ref T），不能放在形参名前（此处是值注解位）", at)
                    else anns += n
                }
                val destruct = if (peek(Kind.LPAREN)) parseParamDestruct() else null
                val nt = cur()
                val n = if (destruct != null) destruct.first() else nameToken()
                if (n == null) { ensureProgress(before); break }
                // t113（S2 先决件）：无冒号 = 类型标注省略——解析接受（空类型位,槽位语义见 S2）;解析器不知模式（D-12
                // 真值单源）,工程模式的 E-OMITTED-TYPE 硬拒归语义层 R-13。
                val ty = if (peek(Kind.COLON)) { pos++; parseType() } else null
                val dft = if (peek(Kind.ASSIGN)) { pos++; parseExpr(angle = false) } else null
                out += Param(n, ty, anns, dft, destruct, nt.range())
                if (peek(Kind.COMMA)) { pos++; if (peek(Kind.RPAREN)) break; continue }
                break
            }
        }
        expect(Kind.RPAREN, ")")
        return out
    }

    /** S-8【解构形参】：(a, b) 扁平分量名表。 */
    private fun parseParamDestruct(): List<String> {
        next()   // （分派保证）
        val out = ArrayList<String>()
        if (!peek(Kind.RPAREN)) {
            while (true) {
                if (nameToken()?.also { out += it } == null) break
                if (peek(Kind.COMMA)) { pos++; if (peek(Kind.RPAREN)) break; continue }
                break
            }
        }
        expect(Kind.RPAREN, ")")
        return out
    }

    /** S-36【lambda 解构参数】：(a, b) → 编码特殊参数名 "(a,b)"。 */
    private fun parseLambdaDestructName(): String {
        next()   // （分派保证）
        val parts = ArrayList<String>()
        if (!peek(Kind.RPAREN)) {
            while (true) {
                if (nameToken()?.also { parts += it } == null) break
                if (peek(Kind.COMMA)) { pos++; if (peek(Kind.RPAREN)) break; continue }
                break
            }
        }
        expect(Kind.RPAREN, ")")
        return "(" + parts.joinToString(",") + ")"
    }

    /** D-14 命名字段构造前瞻：(IDENT = 形态；() 空括号不算（留给 None() 零参构造）。 */
    private fun looksLikeNamedCtor(): Boolean {
        if (!peek(Kind.LPAREN)) return false
        val a = peekAt(1) ?: return false
        val b = peekAt(2) ?: return false
        return a.kind == Kind.IDENT && b.kind == Kind.ASSIGN
    }

    /** S-33 命名字段构造实参：A(name = e, …)。 */
    private fun parseStructCtorArgs(): List<Pair<String, Expr>> {
        next()   // （分派保证）
        val out = ArrayList<Pair<String, Expr>>()
        if (!peek(Kind.RPAREN)) {
            while (true) {
                val fname = nameToken() ?: break
                if (expect(Kind.ASSIGN, "=") == null) break
                out += fname to parseExpr(angle = false)
                if (peek(Kind.COMMA)) { pos++; if (peek(Kind.RPAREN)) break; continue }
                break
            }
        }
        expect(Kind.RPAREN, ")")
        return out
    }

    /** S-15【多态函数类型】前瞻：[T](...) => ... —— 方括内仅名字/逗号且紧跟 ( ；否则不是理论前缀。 */
    private fun polyFunTypeAhead(): Boolean {
        var depth = 0
        var j = pos
        while (j < toks.size) {
            val k = toks[j].kind
            when {
                k == Kind.LBRACKET -> depth++
                k == Kind.RBRACKET -> { depth--; if (depth == 0) return peekAt(j + 1)?.kind == Kind.LPAREN }
                k == Kind.EOF -> return false
                depth == 1 && (k == Kind.IDENT || k == Kind.COMMA) -> {}
                depth == 1 -> return false
                else -> {}
            }
            j++
        }
        return false
    }

    /** S-15【多态函数类型】：[T](T) => T——不成函数类型则诊断（元组不能携带理论前缀，不静默回退）。 */
    private fun parsePolyFunType(): Type {
        val lb = next()   // [（分派保证）
        val tps = ArrayList<TypeParam>()
        while (true) {
            val nt = cur()
            val n = nameToken() ?: break
            tps += TypeParam(n, null, 0, nt.range())
            if (!peek(Kind.COMMA)) break
            pos++
            if (peek(Kind.RBRACKET)) break
        }
        if (expect(Kind.RBRACKET, "]") == null)
            return errType("类型理论参数前缀仅用于函数类型（[T](...) => ...），元组不能携带", lb)
        val ft = parseFunOrTupleType()
        return if (ft is FunType) ft.copy(theory = tps)
        else errType("类型理论参数前缀仅用于函数类型（[T](...) => ...），元组不能携带", lb)
    }

    /** S-15【类型】：注解前缀（@mut/@atomic/@impure/@ref——@ref 为 t95 语法层中间态，语义在 t97）| 多态函数类型 | 函数或元组类型 | 具名类型，带和类型尾（构造即展平，I4）。 */
    private fun parseType(): Type {
        val t = cur()
        if (t.kind == Kind.AT) {
            pos++
            val an = expect(Kind.IDENT, "类型注解名") ?: return errType("期望类型注解名（@mut / @atomic / @impure / @ref）", cur())
            return when (an.text) {
                "mut" -> MutType(parseType(), t.range())
                "atomic" -> AtomicType(parseType(), t.range())
                // t95（N2,@ref 语法层）：@ref T / @ref @mut T——叠加序=书写序（RefType(MutType(T))），顺序不规范化；
                // 语义（可写性/子类型/拷贝）是 t97 的域，本层只产 AST/HIR 节点。
                "ref" -> RefType(parseType(), t.range())
                "impure" -> {
                    val ft = parseType()
                    if (ft is FunType) ft.copy(purity = "impure")
                    else errType("@impure 类型注解只能修饰函数类型（写法：f: @impure (A) => Null）", t)
                }
                else -> errType("类型位注解只支持 @mut / @atomic / @impure / @ref", t)
            }
        }
        val base: Type = when {
            peek(Kind.LBRACKET) -> if (polyFunTypeAhead()) parsePolyFunType()
            else errType("类型理论参数前缀仅用于函数类型（[T](...) => ...），元组不能携带", cur())
            peek(Kind.LPAREN) -> parseFunOrTupleType()
            else -> parseNamedType()
        }
        var ty = base
        if (peek(Kind.PIPE)) {
            val variants = ArrayList<Type>()
            variants += ty
            while (peek(Kind.PIPE)) {
                pos++
                variants += if (peek(Kind.LPAREN)) parseFunOrTupleType() else parseNamedType()
            }
            ty = UnionType(variants.flatMap { if (it is UnionType) it.variants else listOf(it) }, variants[0].pos)
        }
        return ty
    }

    /** S-15【具名类型】：名字（点号链限定 core.Point）+ 可选 [] 类型实参。 */
    private fun parseNamedType(): Type {
        val firstTok = cur()
        val first = nameToken() ?: return errType("期望类型名", cur())
        val segs = ArrayList<String>()
        segs += first
        while (peek(Kind.DOT)) {
            pos++
            val seg = nameToken() ?: break
            segs += seg
        }
        val args = if (peek(Kind.LBRACKET)) { pos++; parseTypeArgs() } else emptyList()
        return if (segs.size == 1) NamedType(first, args, firstTok.range())
        else QualifiedType(segs.dropLast(1), segs.last(), args, firstTok.range())
    }

    /**
     * S-15【函数或元组类型】：(…) [<ctx>] => T 为函数类型；≥2 项逗号是元组类型；
     * 单项一律是分组括号（(T) ≡ T，I4 裁决）；() 为零元组类型位；() => C 为零参函数类型。
     */
    private fun parseFunOrTupleType(): Type {
        val lp = next()   // （分派保证）
        val ps = ArrayList<Type>()
        if (!peek(Kind.RPAREN)) {
            while (true) {
                ps += parseType()
                if (peek(Kind.COMMA)) { pos++; if (peek(Kind.RPAREN)) break; continue }
                break
            }
        }
        expect(Kind.RPAREN, ")")
        val ctx = if (peek(Kind.LT)) parseAngleList() else null     // 函数类型上下文 (T)<$1 ∈ xs> => Null
        return if (peek(Kind.DARROW)) {
            pos++
            FunType(ps, parseType(), theory = emptyList(), context = ctx, pos = lp.range())
        } else if (ps.size == 1) ps[0]
        else TupleType(ps, lp.range())
    }

    private fun parseTypeArgs(): List<Type> {
        val out = ArrayList<Type>()
        while (true) {
            out += parseType()
            if (peek(Kind.COMMA)) { pos++; if (peek(Kind.RBRACKET)) break; continue }
            break
        }
        expect(Kind.RBRACKET, "]")
        return out
    }

    /** S-41【角表】：<e₁, e₂>（angle 语境：GT 默认终止符，D-2；带空格的 < 仍是中缀）。 */
    private fun parseAngleList(): List<Expr> {
        next()   // <（分派保证）
        val out = ArrayList<Expr>()
        while (true) {
            out += parseExpr(angle = true)
            if (peek(Kind.COMMA)) { pos++; if (peek(Kind.GT)) break; continue }
            break
        }
        expect(Kind.GT, ">")
        return out
    }

    /** S-16【块】：{ 语句* }——循环带进展保证（容错）。 */
    private fun parseBlock(): BlockExpr {
        val lb = next()
        val stmts = ArrayList<Stmt>()
        while (!peek(Kind.RBRACE) && !peek(Kind.EOF)) {
            skipSemis()
            if (peek(Kind.RBRACE) || peek(Kind.EOF)) break
            val before = pos
            stmts += parseStmt()
            ensureProgress(before)
        }
        expect(Kind.RBRACE, "}")
        return BlockExpr(stmts, lb.range())
    }

    /** S-9【特质声明】：trait 名 [理论参数] [: 父特质链] [{ 成员 }]——无成员可省花括号。 */
    private fun parseTrait(anns: List<String>): Decl {
        next()   // trait（分派保证）
        val nameTok = cur()
        val name = nameToken() ?: return errDeclSync("期望特质名", nameTok)
        val theory = if (peek(Kind.LBRACKET)) parseTheory() else emptyList()
        val parents = ArrayList<Type>()
        if (peek(Kind.COLON)) {
            pos++
            while (true) {
                parents += parseNamedType()
                if (peek(Kind.COMMA)) { pos++; if (peek(Kind.LBRACE) || peek(Kind.RBRACE)) break; continue }
                break
            }
        }
        if (!peek(Kind.LBRACE)) return TraitDecl(name, theory, anns, emptyList(), parents, nameTok.range())
        next()
        val members = ArrayList<FunDecl>()
        while (!peek(Kind.RBRACE) && !peek(Kind.EOF)) {
            skipSemis()
            if (peek(Kind.RBRACE)) break
            val before = pos
            val ma = parseAnnotations()
            val m: Decl = when (cur().kind) {
                Kind.FUN -> parseFun(ma)
                // P-9/B.3（t40）：val 已废除——trait 不得携带状态，落 E-VAL-REMOVED + 指引，
                // 并跳残段至下一成员边界（同 parseImpl 同步口径，避免重复诊断）
                Kind.IDENT -> if (cur().text == "val") {
                    val vd = errDecl(
                        "val 已废除（P-9，2026-11-06）。trait 不得携带状态：特质只声明函数规范；" +
                            "数据请放在 struct 的字段里", cur(), "E-VAL-REMOVED")
                    pos++   // 越过 val
                    while (cur().kind !in TRAIT_BODY_SYNC && !peek(Kind.EOF) && !peek(Kind.RBRACE)) pos++
                    vd
                }
                else errDecl("特质体只允许函数规范（fun）（TS-6，B.3 后无 val 形态）：" +
                    "「${cur().text}」既非规范也不是已登记形态", cur())
                Kind.VAR -> errDecl("特质体内禁止字段声明（TS-6）：字段属类型声明（struct），" +
                    "请把 `${cur().text} …` 移出 trait 体", cur())
                // TS-6 禁嵌套类型声明
                Kind.TRAIT, Kind.STRUCT, Kind.ENUM -> errDecl("特质体内禁止嵌套类型声明（TS-6）：" +
                    "`${cur().text} …` 请移至顶层声明", cur())
                else -> parseFun(ma)   // 既兜底又保诊断口径（破损成员按原通道落 E-SYNTAX）
            }
            when (m) {
                is FunDecl -> {
                    // TS-6/TS-7：函数规范不得含实现体与默认值（实现细节归扩展函数）
                    if (m.body != null)
                        diagErrorPos("特质函数规范不得含实现体（TS-6/TS-7）：`${m.name}` 请去掉体，" +
                            "实现请以扩展函数 `fun ${name}.${m.name}…` 另写", m.body.pos)
                    m.params.firstOrNull { it.default != null }?.let { p ->
                        diagErrorPos("特质函数规范不得含默认值（TS-7）：默认值属实现细节，归扩展函数", p.pos)
                    }
                    members += m
                }
                else -> {}   // 破损成员剔除（诊断已记，S-1 §2 局部恢复）
            }
            ensureProgress(before)
        }
        expect(Kind.RBRACE, "}")
        return TraitDecl(name, theory, anns, members, parents, nameTok.range())
    }


    /** S-10【结构体声明】：struct 名 [理论参数] [形参表] [伴生命题 <…>]；体是圆括号参数列表，大括号体拒绝（决策 69）。 */
    private fun parseStruct(anns: List<String>): Decl {
        next()   // struct（分派保证）
        val nameTok = cur()
        val name = nameToken() ?: return errDeclSync("期望结构体名", nameTok)
        val theory = if (peek(Kind.LBRACKET)) parseTheory() else emptyList()
        if (peek(Kind.LBRACE)) {
            val lb = cur()
            skipBraced()
            return errDecl("struct 体是参数列表，请改写为 $name(字段: 类型 = 默认值)", lb)
        }
        val fields = if (peek(Kind.LPAREN)) parseParams() else emptyList()
        val props = if (peek(Kind.LT)) parseAngleList() else emptyList()
        return StructDecl(name, theory, fields, anns, props, nameTok.range())
    }

    /**
     * S-11【枚举声明→废除】（t40，P-9，附录 B.1）：`enum` 不再是语言要素。
     * 枚举请用结构体 + 联合类型别名表达（迁移模板随诊断给出）。解析到即报 E-ENUM-REMOVED +
     * 指引并产 ErrorDecl；头与体的残段按 parseImpl 同口径同步跳过（[ ] ( ) 配平、遇 { 整段吞体、
     * 入口边界停），保证容错链不断、后续声明可继续解析。
     */
    private fun parseEnumRemoved(): Decl {
        val enumTok = next()   // enum（分派保证）
        val (code, msg) = removedKeywordMsg(enumTok)!!   // t53：文案与语句位同一真相（模板同步为新形态 struct A）
        val d = errDecl(msg, enumTok, code)
        skipRemovedDeclBody()
        return d
    }

    /** 废除性声明（enum/def）的残段同步：[ ] ( ) 配平越过；遇 { 必配平吞体；深度 0 遇入口边界停。 */
    private fun skipRemovedDeclBody() {
        var depth = 0
        while (!peek(Kind.EOF)) {
            when (cur().kind) {
                Kind.LBRACKET, Kind.LPAREN -> depth++
                Kind.RBRACKET, Kind.RPAREN -> { if (depth > 0) depth-- }
                Kind.LBRACE -> { skipBraced(); continue }
                else -> if (depth == 0 && cur().kind in ENTRY_SYNC) break
            }
            pos++
        }
    }

    /**
     * S-12【实现声明→废除】（t28，卷 TS-2/Q6、TS-30）：`impl` 不再是语言要素。
     * 解析到即报 E-IMPL-REMOVED + 迁移模板并产 ErrorDecl；花括号体整段跳过（容错链不断）。
     * **不设兼容期**（Q6）——旧写法请按模板改写为扩展函数。
     */
    private fun parseImpl(): Decl {
        val implTok = next()   // impl（分派保证）
        val (code, msg) = removedKeywordMsg(implTok)!!   // t53：文案与语句位同一真相
        val d = errDecl(msg, implTok, code)
        // 体（若有）整段跳过：`impl Trait for T` 的头与体之间无定界符，且 [ ] ( ) 皆属同步集，
        // 故头部的类型实参须配平越过；遇 { 必配平吞体（否则文件循环会把 {...} 当入口二次解析）
        var depth = 0
        while (!peek(Kind.EOF)) {
            when (cur().kind) {
                Kind.LBRACKET, Kind.LPAREN -> depth++
                Kind.RBRACKET, Kind.RPAREN -> { if (depth > 0) depth-- }
                Kind.LBRACE -> { if (depth == 0) break }
                else -> if (depth == 0 && cur().kind in DECL_SYNC) break
            }
            pos++
        }
        if (peek(Kind.LBRACE)) skipBraced()
        return d
    }


    // ============ 语句（S-16~S-30）——分派顺序即优先级（§2.5 表） ============

    private fun parseStmt(): Stmt = when {
        // t53（P-9 收口 R3 口径差）：废除关键字在语句位也落专属废除诊断 + 迁移指引——
        // val 是词法 IDENT（旧路径当名字引用落 E-UNBOUND-NAME），enum/def/impl 是关键字
        // （旧路径落「期望表达式」+ 跳符号级联），统一改走废除通道
        isRemovedKeyword(cur()) -> removedKeywordStmt(cur())
        peek(Kind.EXISTS) -> parseExistsStmt()                                                    // S-18
        peek(Kind.FUN) -> {                                                                        // S-19
            val fn = parseFun(emptyList())
            if (fn is FunDecl) FunStmt(fn, fn.pos) else ErrorStmt((fn as ErrorDecl).message, fn.pos)
        }
        peek(Kind.IF) && peekAt(1)?.kind == Kind.LBRACKET -> parseIfProp()                          // S-20
        peek(Kind.WHEN) && peekAt(1)?.kind == Kind.LBRACKET -> parseWhenProp()                       // S-21
        peek(Kind.THEREFORE) -> {                                                                   // S-22 裸 ∴
            val t = next()
            BTSStmt(TopExpr(t.range()), emptyList(), parseExpr(angle = false), t.range())            // S-8 反糖：∵ ⊤ ∴ q
        }
        peek(Kind.IDENT) && cur().text == "axiom" && canStartAxiomProp() -> parseAxiomStmt()         // S-23/D-19
        peek(Kind.BECAUSE) -> parseBTS()                                                            // S-24
        peek(Kind.UNCHECKED) -> parseUnchecked()                                                    // S-25
        peek(Kind.RET) -> parseReturn()                                                             // S-26
        peek(Kind.BY) -> { val t = next(); ByStmt(parseBracketList(), t.range()) }                  // S-27
        peek(Kind.AT) -> {                                                                          // S-28/S-29
            if (peekAt(1)?.kind == Kind.IDENT && peekAt(2)?.kind == Kind.LBRACKET) {
                val t = next()
                val strategy = next().text
                expect(Kind.LBRACKET, "[")
                val goal = parseExpr(angle = true)
                expect(Kind.RBRACKET, "]")
                StrategyStmt(strategy, goal, t.range())
            } else {
                val at = cur()
                val anns = parseAnnotations()
                if (peek(Kind.VAR)) parseVar(anns) else errStmt("期望 var 变量声明", at)
            }
        }
        peek(Kind.VAR) -> parseVar(emptyList())                                                     // S-29
        else -> parseAssignOrExprStmt()                                                             // S-30
    }

    /** S-24【因为所以】：∵ form, p₁…pₙ ∴ conclusion；裸形（无逗号无 ∴）反糖为 ∵ ⊤ ∴ p（S-8）。 */
    private fun parseBTS(): Stmt {
        val t = next()   // ∵（分派保证）
        val form = parseExpr(angle = false)
        if (!peek(Kind.COMMA) && !peek(Kind.THEREFORE))
            return BTSStmt(TopExpr(t.range()), emptyList(), form, t.range())
        val premises = ArrayList<Expr>()
        while (peek(Kind.COMMA)) {
            pos++
            if (peek(Kind.THEREFORE)) break   // 尾随逗号
            premises += parseExpr(angle = false)
        }
        if (expect(Kind.THEREFORE, "∴") == null)
            return BTSStmt(form, premises, errExpr("期望 ∴ 结论", cur()), t.range())
        return BTSStmt(form, premises, parseExpr(angle = false), t.range())
    }

    /** D-19：axiom 起命题判定——下一 token 可起命题才作 axiom 语句，否则退回 S-30 名字引用。 */
    private fun canStartAxiomProp(): Boolean {
        val nx = peekAt(1) ?: return false
        return nx.kind in setOf(
            Kind.IDENT, Kind.SYMBOL, Kind.INT, Kind.FLOAT, Kind.STR, Kind.LPAREN,
            Kind.BANG, Kind.TOP, Kind.BOT, Kind.MINUS, Kind.FORALL, Kind.EXISTS, Kind.EXISTS1,   // P4 批次 3：¬ 已是 IDENT（Kind.LNOT 不再产出）
        )
    }

    /** S-23【axiom 语句】：axiom p₁, p₂（词法层 axiom 是 IDENT，语句位按文本识别）。 */
    private fun parseAxiomStmt(): Stmt {
        val t = next()   // axiom（分派保证）
        val props = ArrayList<Expr>()
        props += parseExpr(angle = false)
        while (peek(Kind.COMMA)) {
            pos++
            val nx = cur()
            if (nx.kind == Kind.RBRACE || nx.kind == Kind.SEMI || nx.kind == Kind.EOF) break   // 尾随逗号
            props += parseExpr(angle = false)
        }
        return AxiomStmt(props, t.range())
    }

    /** S-21【命题 when】：when[p] { 条件 -> 块 }——分支箭头可选（建议恒写），体必须是块。
     *  t42（2026-11-06）：分支箭头随 S-20 when 统一为 `->`（同裁决）。 */
    private fun parseWhenProp(): Stmt {
        val t = next()   // when（分派保证）
        expect(Kind.LBRACKET, "[")
        val disjunction = parseExpr(angle = true)
        expect(Kind.RBRACKET, "]")
        expect(Kind.LBRACE, "{")
        val arms = ArrayList<WhenPropArm>()
        while (!peek(Kind.RBRACE) && !peek(Kind.EOF)) {
            val before = pos
            val cond = parseExpr(angle = false)
            // t42：`->` 消费；`=>` 报统一提示并越过（容错链不断）；缺箭头沿用旧可选口径
            when (cur().kind) {
                Kind.ARROW -> pos++
                Kind.DARROW -> {
                    diagError("when[命题] 分支箭头已统一为 `->`（`=>` 与函数类型箭头冲突）：" +
                        "请把分支箭头改写为 `->`（例如 `p -> { ∵ f ∴ r }`）", cur())
                    pos++
                }
                else -> {}
            }
            val body = parseBlock().stmts
            arms += WhenPropArm(cond, body, cond.pos)
            ensureProgress(before)
        }
        expect(Kind.RBRACE, "}")
        return WhenPropStmt(disjunction, arms, t.range())
    }

    /** S-20【命题 if】：if[p] { } else { }——排中律，块内是语句。 */
    private fun parseIfProp(): Stmt {
        val t = next()   // if（分派保证）
        expect(Kind.LBRACKET, "[")
        val prop = parseExpr(angle = true)
        expect(Kind.RBRACKET, "]")
        val thenBlk = parseBlock()
        val elseBlk = if (peek(Kind.ELSE)) { pos++; parseBlock() } else null
        return IfPropStmt(prop, thenBlk.stmts, elseBlk?.stmts ?: emptyList(), t.range())
    }

    /** S-26【返回语句】：return [表达式] [标签]；裸形判据（下一 token ∈ { }, ;, EOF}），@label 须紧贴（D-18）。 */
    private fun parseReturn(): Stmt {
        val t = next()   // return（分派保证）
        var label: String? = null
        if (peek(Kind.AT) && !cur().precededBySpace) {
            pos++
            label = expect(Kind.IDENT, "标签名")?.text
        }
        val nx = cur()
        val bare = nx.kind == Kind.RBRACE || nx.kind == Kind.SEMI || nx.kind == Kind.EOF
        return if (bare || label != null) ReturnStmt(null, label, t.range())
        else ReturnStmt(parseExpr(angle = false), label, t.range())
    }

    /** S-18【存在语句】：∃ x = t. P（引入，多见证）/ ∃ x . P [块]（消去，D-20）。 */
    private fun parseExistsStmt(): Stmt {
        val t = next()   // ∃（分派保证）
        val x = expect(Kind.IDENT, "存在量词变量名")?.text
            ?: return errStmt("期望存在量词变量名", cur())
        if (peek(Kind.ASSIGN)) {
            val names = ArrayList<String>()
            val witnesses = ArrayList<Expr>()
            names.add(x)
            pos++
            witnesses.add(parseExpr(angle = false))
            while (peek(Kind.COMMA)) {
                pos++
                val n = expect(Kind.IDENT, "存在量词变量名")?.text ?: break
                names.add(n)
                if (expect(Kind.ASSIGN, "=") == null) break
                witnesses.add(parseExpr(angle = false))
            }
            if (expect(Kind.DOT, ".") == null) return errStmt("存在引入须以句点收尾（如 ∃ x = t. P）", cur())
            return ExistIntroStmt(names, witnesses, parseExpr(angle = true), t.range())
        }
        if (expect(Kind.DOT, ".") == null) return errStmt("存在消去须以句点收尾（如 ∃ x. P）", cur())
        val prop = parseExpr(angle = true)
        // 可选块受尾随 lambda 规则 D-9 威胁（§5.1 已知限制）：P 以可调用表达式结尾时块被吞
        return if (peek(Kind.LBRACE)) ExistsElimStmt(x, prop, parseBlock().stmts, t.range())
        else ExistsElimStmt(x, prop, emptyList(), t.range())
    }

    /** S-25【unchecked 语句】：unchecked <语句>；unchecked.axiom 形态引导改写（诊断 + ErrorStmt）。 */
    private fun parseUnchecked(): Stmt {
        val t = cur()
        if (peek(Kind.DOT)) {
            val isAxiom = peekAt(1)?.kind == Kind.IDENT && peekAt(1)?.text == "axiom"
            val msg = if (isAxiom) "公理请直接写 `axiom <命题>` 语句（unchecked.axiom 已废除）"
            else "unchecked 后须直接跟语句"
            diagError(msg, t)
            return ErrorStmt(msg, t.range())
        }
        next()
        return UncheckedStmt(parseStmt(), t.range())
    }

    /** S-29【变量声明】：注解 var [解构] [: 类型] = 表达式。 */
    private fun parseVar(anns: List<String>): Stmt {
        val t = cur()
        next()   // var（分派保证）
        val destruct = parseVarDestruct()
        val nt = cur()
        val name = if (destruct != null) destruct.first() else nameToken()
        if (name == null) return errStmt("期望变量名", nt)
        val ty = if (peek(Kind.COLON)) { pos++; parseType() } else null
        val v = if (peek(Kind.ASSIGN)) { pos++; parseExpr(angle = false) }
        else errExpr("期望 = 初值表达式", cur())
        return VarStmt(name, anns, ty, v, destruct, t.range())
    }

    /** S-29 解构：var (a, b) = e 扁平分量名表（空 () 合法——无分量绑定）。 */
    private fun parseVarDestruct(): List<String>? {
        if (!peek(Kind.LPAREN)) return null
        pos++
        val names = ArrayList<String>()
        val first = nameToken() ?: return names
        names += first
        while (peek(Kind.COMMA)) {
            if (peekAt(1)?.kind == Kind.RPAREN) break    // 尾随逗号
            pos++
            val n = nameToken() ?: break
            names += n
        }
        expect(Kind.RPAREN, ")")
        return names
    }

    /** S-35【var 表达式】：声明即表达式（var n = var m = 0 合法）。 */
    private fun parseVarExpr(anns: List<String>): Expr {
        val t = cur()
        next()   // var（分派保证）
        val destruct = parseVarDestruct()
        val nt = cur()
        val name = if (destruct != null) destruct.first() else nameToken()
            ?: return errExpr("期望变量名", nt)
        val ty = if (peek(Kind.COLON)) { pos++; parseType() } else null
        val v = if (peek(Kind.ASSIGN)) { pos++; parseExpr(angle = false) }
        else errExpr("期望 = 初值表达式", cur())
        return VarExpr(name, anns, ty, v, t.range())
    }

    /** S-34【匿名函数】：fun _[T](a: T): T = a 或省略式 fun[T](a:T):T = a（省略时名为 _）。 */
    private fun parseAnonFunExpr(): Expr {
        val t = cur()
        next()   // fun（分派保证）
        val name = if (peek(Kind.LBRACKET) || peek(Kind.LPAREN)) "_" else nameToken()
            ?: return errExpr("期望匿名函数名（_ 或省略）", cur())
        val theory = if (peek(Kind.LBRACKET)) parseTheory() else emptyList()
        val params = if (peek(Kind.LPAREN)) parseParams() else emptyList()
        val preps = if (peek(Kind.LT)) parseAngleList() else emptyList()
        val ret = if (peek(Kind.COLON)) { pos++; parseType() } else null
        val posts = if (peek(Kind.LT)) parseAngleList() else emptyList()
        val body: Expr? = when {
            peek(Kind.ASSIGN) && peekAt(1)?.kind == Kind.LBRACE -> {
                val lb = peekAt(1)!!
                pos++
                skipBraced()
                errExpr("函数体不能 `= {…}` 混写：单表达式请去掉花括号写 `= 表达式`，块体请去掉等号写 `{ … }`", lb)
            }
            peek(Kind.ASSIGN) -> { pos++; parseExpr(angle = false) }
            peek(Kind.LBRACE) -> parseBlock()
            else -> null
        }
        return AnonFunExpr(name, theory, params, preps, ret, posts, body, t.range())
    }

    /**
     * S-30【赋值或表达式语句】：先前瞻解析后缀表达式——左值为名字/字段且紧接 = 时作赋值；
     * 否则回滚（游标、前瞻期诊断与留痕一并还原——旧实现以 try/catch ParseFailure 达成），
     * 按表达式语句解析（= 在此作命题等式中缀）。
     */
    private fun parseAssignOrExprStmt(): Stmt {
        val save = pos
        val dBase = _diag.size
        val noted = _notes.keys.toMutableSet()
        val lhs = parsePostfix(stopArrow = false, angle = false)
        if (peek(Kind.ASSIGN) && (lhs is NameRef || lhs is FieldExpr)) {
            next()
            return AssignStmt(lhs, parseExpr(angle = false), lhs.pos)
        }
        pos = save
        if (_diag.size > dBase) _diag.subList(dBase, _diag.size).clear()
        _notes.keys.retainAll(noted)
        val e = parseExpr(angle = false)
        return ExprStmt(e, e.pos)
    }

    // ============ 表达式（S-31~S-37/S-40）——四层结构全左结合、无优先级（D-10） ============

    /** 双目算符集合（§3.1：程序位 => 不在其中——when 分支/lambda 参数表由此正确切分）。 */
    private val binops = setOf(
        Kind.PLUS, Kind.MINUS, Kind.STAR, Kind.SLASH, Kind.PERCENT, Kind.POW, Kind.ASSIGN, Kind.COLON,
        Kind.LE, Kind.GE, Kind.AMP, Kind.PIPE, Kind.IFF, Kind.EQ, Kind.NEQ,
        // t83（ADR-51,Q21 终态）：LAND/LOR 出 binops——`&&`/`||` 由 Parser 中缀循环顶拒绝（E-OP-RETIRED）,
        // Lexer 保留其 Kind 产出仅作拒绝锚点（文本即用户写法），故枚举量非死但不再走 binops 通道。
        // t82 F3（qa/t77 提请）：IMPLIES/EQUIV 已不再由 Lexer 产出（t74 后 →/↔ 是名字材料走 D-5），
        // 仅保留 Kind 枚举量（LNOT 先例，S-4 同口径），不入 binops 以免不可达成员。
    )

    /** t82（ADR-50 §六）中缀层号——越大越紧，同层左结合、仅 → 右结合。 */
    private val LVL_ARITH = 10                                                      // 算术单层（层内无优先级）/ .. / is
    private val LVL_REL = 9                                                         // 关系与用户定义中缀（裁决 B）
    private val LVL_AND = 8                                                         // & / ∧
    private val LVL_OR = 7                                                          // | / ∨
    private val LVL_IMP = 6                                                         // →（右结合）
    private val LVL_IFF = 5                                                         // ↔

    /** 决策 88：可作函数名前缀调用的运算符（不含 MINUS——一元负优先）。 */
    private val opPrefixKinds = setOf(
        Kind.PLUS, Kind.STAR, Kind.SLASH, Kind.PERCENT, Kind.AMP, Kind.PIPE, Kind.IFF,
    )

    /** S-34 符号调用起首 <(1, 2)。 */
    private val symbolCallStart = setOf(Kind.LT, Kind.GT, Kind.EQ, Kind.NEQ, Kind.LE, Kind.GE)

    /** S-40 量词 ASCII 别名归一（S-3）。 */
    private val asciiQuant = mapOf("forall" to "∀", "exists" to "∃", "exists1" to "∃!")

    /** E-35【数学风 if-then-else 停止词】：防 then/else 被中缀糖吞（§3.5）。 */
    private var stopIdents: Set<String> = emptySet()

    /** if 条件位放行 is 构造模式的哨兵开关（§2.8/§3.5）。 */
    private var ifIsCond = false

    /**
     * S-31【表达式】：一元表达式 + 中缀循环。判定顺序即优先级（§2.6 备注）：
     * stopIdents → stopArrow(D-21) → by → GT(D-2) → LT(D-1) → is(D-4) → .. → in → 双目算符
     * → 带空格 SYMBOL(D-6) → 的内部值 → 带空格 IDENT 中缀糖(D-5)。
     */
    private fun parseExpr(angle: Boolean, stopArrow: Boolean = false): Expr = parseExpr(angle, stopArrow, 0)

    /**
     * parseExpr（t82/ADR-50：平坦左折叠 → 优先级爬升）。层号越大越紧、同层左结合、仅 → 右结合：
     *   10 算术 + - * / % ^（**单层**：层内无优先级，`a+b*c` = `(a+b)*c`,H-ARITH-PREC 提示保留）、.. 区间、is
     *   9  关系 = ≠ ∈ <: >= <= < >、in、用户定义中缀（符号中缀 / D-5 标识符中缀，裁决 B 与关系同级）
     *   8  &（表达式）/ ∧（命题）;7 | / ∨;6 →（**右结合**）;5 ↔;¬ 前缀走 parseUnary
     * 中缀判定的**前瞻（是不是中缀）与层号（多紧）同一函数** infixLevel（qa/t77 风险表最高项）;语境特例
     * （stopIdents / stopArrow / GT 的 angle 消歧 / LT 紧贴 / IS 同行与构造子形态）仍由各分支自持。
     * 后缀（BY / 的内部值）仅在顶层循环 minPrec=0 时绑定——复现旧左折叠的「后缀绑整条已积表达式」语义。
     * 括号层级仍由 depthAt(t71/ADR-47) 管，此机制不改。
     */
    private fun parseExpr(angle: Boolean, stopArrow: Boolean, minPrec: Int): Expr {
        var left = parseUnary(stopArrow, angle)
        while (true) {
            val t = cur()
            if (t.kind == Kind.IDENT && t.text in stopIdents) break                       // 数学风 if-then-els
            if (stopArrow && t.kind == Kind.ARROW) break       // D-21/t42：when 分支箭头让路——分支边界是 `->`；`=>` 属函数类
            if (stopArrow && t.kind == Kind.LPAREN && t.precededBySpace) break            // D-21：空格 ( 防下一 a
            // t83（ADR-51,Q21 终态）：&&/|| 一律拒绝（E-OP-RETIRED)——在 infixLevel 层闸之前，任何语境层级都报错。
            // 文本经 Lexer 保留为用户写法；右操作数照解析后丢弃（ErrorExpr 占位，控级联 E-SYNTAX)。
            if (t.kind == Kind.LAND || t.kind == Kind.LOR) {
                val want = if (t.kind == Kind.LAND) '&' else '|'
                _diag += Diag(Severity.ERROR, "E-OP-RETIRED", t.range(),
                    "${if (t.kind == Kind.LAND) "布尔与" else "布尔或"}算符 `${t.text}` 已退役：表达式层一律用 `$want`（`p $want q` 可直接编译通过）；命题层专用 `∧` `∨`。")
                pos++
                parseExpr(angle, stopArrow, 0)              // 吞右操作数：级联诊断归右式自身，不重复退役码
                left = ErrorExpr("`${t.text}` 已退役，改用 `$want`", t.range())
                continue
            }
            if (t.kind == Kind.BY) {                                                     // S-31 by 后缀（无层号）
                if (minPrec > 0) break                                                   // 递归内部让位：后缀由顶层循环绑整条
                pos++
                left = ByExpr(left, parseBracketList(), t.range())
                continue
            }
            if (t.kind == Kind.IDENT && t.text == "的内部值" && t.precededBySpace) {      // Optional 读法（后缀：无层号）
                if (minPrec > 0) break
                pos++
                left = CallExpr(NameRef("的内部值", t.range()), listOf(left), pos = t.range())
                continue
            }
            val p = infixLevel(t)
            if (p == 0 || p < minPrec) break
            if (t.kind == Kind.GT) {
                // D-2：angle 语境中 > 默认终止，前有空格且后一 token 可起表达式时才是比较中缀（右操作数继承 angle 语境）
                if (angle && !(t.precededBySpace && nextStartsOperand())) break
                pos++
                left = BinExpr(">", left, parseExpr(angle, stopArrow, LVL_REL + 1), t.range())
                continue
            }
            if (t.kind == Kind.LT) {
                if (!t.precededBySpace) break                                            // D-1：紧贴的 < 属后缀实例化
                pos++
                left = BinExpr("<", left, parseExpr(angle, stopArrow, LVL_REL + 1), t.range())
                continue
            }
            if (t.kind == Kind.IS) {
                // D-4：is 仅同行作中缀类型测；跨行的 is 属 when 下一分支模式
                val leftEnd = peekAt(-1)?.line
                if (leftEnd != null && leftEnd != t.line) break
                if (isCtorForm(1)) {   // peekAt 为相对偏移：is 之后第一 token 起判定构造子形态
                    // 构造子解构形态：if 条件位（ifIsCond）解成 IsPatExpr 哨兵，否则视为下一分支模式起始
                    if (ifIsCond) {
                        left = IsPatExpr(left, parsePattern(), t.range())
                        continue
                    }
                    break
                }
                pos++
                left = TypeTestExpr(left, parseType(), t.range())
                continue
            }
            if (t.kind == Kind.RANGE) {                                                  // S-31 区间中缀
                pos++
                left = BinExpr("..", left, parseExpr(angle, stopArrow, LVL_ARITH + 1), t.range())
                continue
            }
            if (t.kind == Kind.IDENT && t.text == "in") {                                // S-1a：a in b → in
                pos++
                left = CallExpr(NameRef("in", t.range()), listOf(left, parseRangeOperand(stopArrow, angle)), pos = t.range())
                continue
            }
            if (t.kind in binops) {
                maybeArithHint(left, t.text, t)                                          // t71（ADR-46）提示级劝告
                pos++
                val nextMin = if (p == LVL_IMP) p else p + 1                             // → 右结合：右操作数同层继续
                left = BinExpr(t.text, left, parseExpr(angle, stopArrow, nextMin), t.range())
                continue
            }
            if (t.kind == Kind.SYMBOL && t.precededBySpace) {                            // D-6 带空格符号中缀
                // 关系（∈ ≠ …）与用户符号中缀同为 LVL_REL（§六：用户定义中缀与关系同级）——
                // ∧ ∨ → ↔ 是名字材料（t74）走 D-5 通道，不落此分支，故无单独映射（S-4 禁死分支）
                pos++
                left = BinExpr(t.text, left, parseExpr(angle, stopArrow, LVL_REL + 1), t.range())
                continue
            }
            // 到此必是 D-5 标识符中缀糖（infixLevel 已统一闸过 precededBySpace && infixOperandAhead）
            val name = next().text
            val nextMin = if (p == LVL_IMP) p else p + 1
            val call = CallExpr(NameRef(name, t.range()), listOf(left, parseExpr(angle, stopArrow, nextMin)), pos = t.range())
            note(call, "D-INFIX", "中缀 `a $name b` 展开为自由函数 `$name(a, b)`（中缀糖，D-5）", t)
            left = call
            continue
        }
        return left
    }

    /**
     * t82（ADR-50 §六）token 的中缀层号（0 = 非中缀）。**前瞻（是不是中缀）与层号（多紧）同一函数**：
     * binops 成员按 Kind;SYMBOL 带空格 → LVL_REL（关系/用户符号中缀）;IDENT 的 `in` → LVL_REL,
     * 其余 IDENT 需 precededBySpace && infixOperandAhead()（D-5 守卫原样）再按 identInfixLevel 定层。
     */
    private fun infixLevel(t: Token): Int = when {
        t.kind == Kind.IDENT && t.text == "in" -> LVL_REL
        t.kind == Kind.IDENT && t.precededBySpace && infixOperandAhead() -> identInfixLevel(t.text)
        t.kind == Kind.SYMBOL && t.precededBySpace -> LVL_REL
        t.kind == Kind.GT || t.kind == Kind.LT -> LVL_REL
        t.kind == Kind.IS || t.kind == Kind.RANGE -> LVL_ARITH
        t.kind in binops -> when (t.kind) {
            Kind.PLUS, Kind.MINUS, Kind.STAR, Kind.SLASH, Kind.PERCENT, Kind.POW -> LVL_ARITH
            Kind.AMP -> LVL_AND                             // t83:LAND/LOR 不入此表——它们在循环顶即拒
            Kind.PIPE -> LVL_OR
            Kind.IFF -> LVL_IFF
            else -> LVL_REL                                // ASSIGN COLON LE GE EQ NEQ
        }
        else -> 0
    }

    /** t82（ADR-50 §六）D-5 标识符中缀层号：原生命题连接词按表；其余（用户定义中缀）与关系同级（裁决 B)。 */
    private fun identInfixLevel(name: String): Int = when (name) {
        "∧" -> LVL_AND
        "∨" -> LVL_OR
        "→" -> LVL_IMP
        "↔" -> LVL_IFF
        else -> LVL_REL
    }

    /** D-5 中缀糖右操作数判定：带空格、同行、且是合法表达式起始 token（排除集防吞下一分支模式）。 */
    private fun infixOperandAhead(): Boolean {
        val nx = peekAt(1) ?: return false
        if (!nx.precededBySpace) return false
        if (cur().line != nx.line) return false
        val prev = peekAt(-1)
        if (prev != null && prev.line != cur().line) return false
        return nx.kind !in setOf(
            Kind.ARROW, Kind.DARROW, Kind.COMMA, Kind.RBRACE, Kind.RBRACKET, Kind.SEMI, Kind.RPAREN, Kind.ASSIGN, Kind.RET,
        )
    }

    /** D-2：> 的消歧——后一 token 可起表达式则作中缀（>> 序列内层 > 的后继仍是 >，正确终止）。 */
    private fun nextStartsOperand(): Boolean {
        val t = peekAt(1) ?: return false
        return t.kind in setOf(
            Kind.INT, Kind.FLOAT, Kind.STR, Kind.DOLLAR, Kind.IDENT, Kind.SYMBOL,
            Kind.LPAREN, Kind.MINUS, Kind.BANG, Kind.LT,               // P4 批次 3：¬ 走 IDENT 通道（Kind.LNOT 不再产出）
        )
    }

    /** t71（ADR-46）算术劝告分类：^ 一类、* / % 一类、+ - 一类；其余算符返回 0（不判）。 */
    private fun arithClass(op: String): Int = when (op) {
        "^" -> 1
        "*", "/", "%" -> 2
        "+", "-" -> 3
        else -> 0
    }

    /**
     * t71（ADR-46）token 的括号层级：AST 无括号节点（parsePrimary 的括号分支返回内层表达式），
     * 「括号重置层级」只能由 token 序列算（LPAREN 进、RPAREN 出；调用/元组括号同理，语义上是
     * 算术链的天然边界）。BinExpr.pos 恰指向算符 token，故深度按算符位置比对即可。
     */
    private val depthAt: Map<Pair<Int, Int>, Int> = buildMap {
        var depth = 0
        for (t in toks) {
            put(t.line to t.col, depth)
            when (t.kind) {
                Kind.LPAREN -> depth++
                Kind.RPAREN -> depth--
                else -> {}
            }
        }
    }

    /**
     * t71（ADR-46，用户裁决 A1-A3 重写）：**同一括号层级**内一条算术链混用不同类算符 ⇒ 提示级
     * H-ARITH-PREC（同类不提示；括号已定本意的不提示；链只报一次——此前结合点已报即不重复）。
     * 位置指向**跨类的那个算符**（当前结合的 tok）。
     */
    private fun maybeArithHint(left: Expr, op: String, tok: Token) {
        if (left !is BinExpr) return
        val mine = arithClass(op)
        if (mine == 0) return
        val here = depthAt[tok.line to tok.col] ?: return
        val chain = mutableSetOf<Int>()
        var node: Expr = left
        while (node is BinExpr) {
            val lvl = depthAt[node.pos.start.line to node.pos.start.col] ?: break
            if (lvl != here) break            // 括号重置层级：跨层的算符不属于同一条链
            val c = arithClass(node.op)
            if (c == 0) break                 // 非六算符节点截断链（如 .. / 自定义中缀混杂）
            chain.add(c)
            node = node.left
        }
        if (chain.size > 1) return            // 链本身已跨类：更早的结合点已报，不重复
        if (chain.size == 1 && chain.first() != mine) {
            _diag += Diag(Severity.HINT, "H-ARITH-PREC", tok.range(),
                "算术链混用了不同类算符（`${left.op}` 与 `$op`）：中缀一律左结合，写出来的顺序就是计算顺序" +
                "——如 `a + b * c` 实际是 `(a + b) * c`。请用圆括号显式指定本意（如 `a + (b * c)`）",
            )
        }
    }

    /** S-1a：in 的右操作数——lo..hi 区间形态。 */
    private fun parseRangeOperand(stopArrow: Boolean = false, angle: Boolean = false): Expr {
        var e = parseUnary(stopArrow, angle)
        if (peek(Kind.RANGE)) { pos++; e = BinExpr("..", e, parseUnary(stopArrow, angle), cur().range()) }
        return e
    }

    /** S-32【一元表达式】：- / ! / not；命题括号 <e>（单项）。¬ 自 P4 批次 3 起是名字材料：¬<e> 走命题应用角表。 */
    private fun parseUnary(stopArrow: Boolean = false, angle: Boolean = false): Expr {
        val t = cur()
        if (t.kind == Kind.MINUS) { pos++; return UniExpr("-", parseUnary(stopArrow, angle), t.range()) }
        if (t.kind == Kind.BANG) { pos++; return UniExpr("!", parseUnary(stopArrow, angle), t.range()) }
        // P4（批次 3，用户裁决 N1）：¬ 已是名字材料（IDENT "¬"）。¬<p> 走命题应用
        // 角表通道（与 p<a,b> 同一 parseAngleList）：一元形态归一为 UniExpr——与 ¬(p) / not p
        // 同产物（除位置）；元数非 1（¬<p, q>）留 InstExpr 由语义层落 E-ARITY。
        // 裸前缀形态（¬p 整体）自此是单个标识符——不再拦截（用户裁决 N1）。
        if (t.kind == Kind.IDENT && t.text == "¬" && peekAt(1)?.kind == Kind.LT) {
            pos++
            val terms = parseAngleList()
            return if (terms.size == 1) UniExpr("¬", terms[0], t.range())
                else InstExpr(NameRef("¬", t.range()), terms, t.range())
        }
        if (t.kind == Kind.LT) {
            val lt = t
            val inner = parseAngleList()
            if (inner.size != 1) diagError("命题尖括号内只能有一个表达式", lt)
            return inner.getOrElse(0) { errExpr("命题尖括号内缺少表达式", lt) }
        }
        return parsePostfix(stopArrow, angle)
    }

    /**
     * S-33【后缀表达式】：初等项 + 后缀链。
     * ::（D-16 折叠/方法引用）、.（D-3 字段访问）、(（调用/命名字段构造 D-14）、
     * 紧贴 <（D-1 实例化，失败回滚）、尾随 lambda（D-9）、Task/Flow 块（体是代码块非 lambda）。
     */
    private fun parsePostfix(stopArrow: Boolean = false, angle: Boolean = false): Expr {
        var e = parsePrimary(angle)
        while (true) {
            val t = cur()
            when {
                stopArrow && t.kind == Kind.LPAREN && t.precededBySpace -> break                // D-21
                t.kind == Kind.COLON2 -> {                                                      // D-16
                    pos++
                    val member = nameToken()
                    if (member == null) break
                    e = if (e is NameRef && e.name.isNotEmpty() && e.name.first().isUpperCase())
                        NameRef(e.name + "::" + member, e.pos)
                    else FunRefExpr(e, member, e.pos)
                }
                // D-3：点前无空格，或点在行首（换行链）；同行带空格的点是上一表达式的收尾
                t.kind == Kind.DOT && (!t.precededBySpace || peekAt(-1)?.line != t.line) -> {
                    pos++
                    val mt = cur()   // 成员 token（定位锚点，非接收者）
                    val nm = nameToken()
                    if (nm == null) break
                    e = FieldExpr(e, nm, mt.range())
                }
                t.kind == Kind.LPAREN -> {
                    if (e is NameRef && !e.name.contains("::") && e.name.isNotEmpty() &&
                        e.name.first().isUpperCase() && looksLikeNamedCtor())
                        e = StructCtorExpr(e.name, parseStructCtorArgs(), e.pos)
                    else {
                        val (pa, na) = parseArgs()
                        e = CallExpr(e, pa, na, e.pos)
                    }
                }
                t.kind == Kind.LT && !t.precededBySpace -> {                                     // D-1 实例化/显式供给
                    val save = pos
                    val dBase = _diag.size
                    val noted = _notes.keys.toMutableSet()
                    val terms = parseAngleList()
                    if (_diag.size == dBase) e = InstExpr(e, terms, e.pos)
                    else {
                        // 解析失败回滚（旧实现以 try/catch 达成）：游标、诊断、留痕一并还原
                        pos = save
                        _diag.subList(dBase, _diag.size).clear()
                        _notes.keys.retainAll(noted)
                        break
                    }
                }
                t.kind == Kind.LBRACE && (braceIsLambda(pos) ||
                    (braceHasImplicitBody(pos) && !(e is NameRef && (e.name == "Task" || e.name == "Flow")))) &&
                    !(e is IntLit || e is StrLit || e is BinExpr || e is UniExpr) -> {          // D-9 尾随 lambda
                    val lam = parseBraceLambda()
                    val call = if (e is CallExpr) e.copy(args = e.args + lam) else CallExpr(e, listOf(lam), pos = e.pos)
                    note(call, "D-TRAILING", "尾随 lambda 已作为最后一个实参传入（等价在括号里写 `{ … }`）", t)
                    e = call
                }
                t.kind == Kind.LBRACE && e is NameRef && e.name == "Task" -> {                   // 任务块（体是代码块）
                    e = TaskExpr(parseBlock(), e.pos)
                }
                t.kind == Kind.LBRACE && e is NameRef && e.name == "Flow" -> {                   // 惰性冷流块
                    e = FlowExpr(parseBlock(), e.pos)
                }
                else -> break
            }
        }
        return e
    }

    /** S-33【实参】：位置实参 + 命名实参通道（P8：IDENT = 形态）；尾随逗号容忍。 */
    private fun parseArgs(): Pair<List<Expr>, Map<String, Expr>> {
        next()   // （分派保证）
        val posArgs = ArrayList<Expr>()
        val named = LinkedHashMap<String, Expr>()
        if (!peek(Kind.RPAREN)) {
            while (true) {
                if (cur().kind == Kind.IDENT && peekAt(1)?.kind == Kind.ASSIGN) {
                    val nm = cur().text
                    pos += 2
                    named[nm] = parseArgExpr()
                } else {
                    posArgs += parseArgExpr()
                }
                if (peek(Kind.COMMA)) { pos++; if (peek(Kind.RPAREN)) break; continue }
                break
            }
        }
        expect(Kind.RPAREN, ")")
        return posArgs to named
    }

    /**
     * R3 S6 t101（R-01/R-03/R-04）：**参数表达式起始位**的前缀 & 借用——脚本模式专属。
     * - 脚本模式：&v（仅变量，R-04.1）→ BorrowExpr；&(a+b) 拒（表达式位不可借用）；
     *   && / & & 双重借用拒（R-04.2，无生命周期系统）
     * - 工程模式：拒（R-03），出路指向 @ref 类型注解（工程模式表达位的引用写法）
     * 中缀 & 合取逐字不动——本函数只在实参起始位拦 AMP/LAND，不触中缀层（样本
     * 模式匹配.subl:35 !=(m,0) & !=(m,1) 走 parseExpr 既有 infix 通道）。
     * 错误恢复：吞掉出错的 & / && 后按普通实参继续解析（单诊断、控级联）。
     */
    private fun parseArgExpr(): Expr {
        val t = cur()
        if (t.kind == Kind.LAND) {   // && 单符号（lexer 归一），实参起始位只可能是双重借用误写
            diagError("\"&&\" 是双重借用，已禁（无生命周期系统）——借用只写一次：&v", t)
            pos++
            return parseExpr(angle = false)
        }
        if (t.kind == Kind.AMP) {
            if (!scriptMode) {
                diagError("工程模式不支持借用表达式 &v——借用是脚本模式专属语法；" +
                    "工程模式的引用请在类型位写注解（形参 a: @ref T，变量 b: @ref T）", t)
                pos++
                return parseExpr(angle = false)
            }
            if (peekAt(1)?.kind == Kind.AMP) {   // & & 空格分开的双重借用
                diagError("\"& &\" 是双重借用，已禁——借用只写一次：&v", t)
                pos += 2
                return parseExpr(angle = false)
            }
            pos++
            val n = cur()
            if (n.kind != Kind.IDENT) {   // &(a+b) / &1 等：仅变量形态（R-04.1）
                diagError("借用只能作用于变量（命名存储）——&(a + 1) 等表达式位不可借用，请写 &v", t)
                return parseExpr(angle = false)   // & 已吞，剩式按普通实参解析
            }
            pos++
            return BorrowExpr(NameRef(n.text, n.range()), t.range())
        }
        return parseExpr(angle = false)
    }

    /**
     * D-INTP 字符串插值脱糖："…$name… ${expr}…" → concat(toStr(…)) 链（不引入新 AST 节点）。
     * ${…} 内由子 Parser 重 lex+解析；子解析器诊断并入本袋。
     * 已知限制（同旧行为）：插值段内位置为段内相对定位（不映回外层文件），供后期改进。
     */
    private fun parseStringLit(t: Token): Expr {
        pos++
        val raw = t.text
        if ('$' !in raw) return StrLit(raw, t.range())
        val parts = ArrayList<Expr>()
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '$' && i + 1 < raw.length) {
                val nx = raw[i + 1]
                if (nx == '{') {
                    val j = matchInterpBrace(raw, i + 1)
                    if (j >= 0) {
                        if (sb.isNotEmpty()) { parts += StrLit(sb.toString(), t.range()); sb.clear() }
                        val inner = raw.substring(i + 2, j)
                        if (inner.isNotBlank()) {
                            val sub = Parser(Lexer(inner, fileName).tokenize(), fileName, scriptMode = true)
                            val e = sub.parseExpr(angle = false)
                            _diag += sub.diag()
                            sub.notes().forEach { (k, v) -> _notes.getOrPut(k) { mutableListOf() } += v }
                            parts += CallExpr(NameRef("toStr", t.range()), listOf(e), pos = t.range())
                        }
                        i = j + 1
                        continue
                    }
                } else if (nx.isLetter()) {
                    var j = i + 1
                    while (j < raw.length && (raw[j].isLetterOrDigit() || raw[j] == '_')) j++
                    if (sb.isNotEmpty()) { parts += StrLit(sb.toString(), t.range()); sb.clear() }
                    parts += CallExpr(
                        NameRef("toStr", t.range()),
                        listOf(NameRef(raw.substring(i + 1, j), t.range())),
                        pos = t.range(),
                    )
                    i = j
                    continue
                }
            }
            sb.append(c)
            i++
        }
        if (sb.isNotEmpty()) parts += StrLit(sb.toString(), t.range())
        var acc = parts.first()
        for (k in 1 until parts.size) acc = CallExpr(NameRef("concat", t.range()), listOf(acc, parts[k]), pos = t.range())
        return acc
    }

    /** 从指向 { 的位置找匹配 }（嵌套计数），找不到返回 -1。 */
    private fun matchInterpBrace(raw: String, bracePos: Int): Int {
        var depth = 0
        var k = bracePos
        while (k < raw.length) {
            when (raw[k]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return k }
            }
            k++
        }
        return -1
    }

    /** S-34【初等项】：字面量 / $ / ⊤⊥ / 元组或括号 / lambda / var / 匿名函数 / if / when / 量词 / 名字 / 符号调用 / 运算符前缀调用。 */
    private fun parsePrimary(angle: Boolean = false): Expr {
        val t = cur()
        return when {
            t.kind == Kind.INT -> { pos++; IntLit(t.text, t.range()) }
            t.kind == Kind.FLOAT -> { pos++; FloatLit(t.text, t.range()) }
            t.kind == Kind.STR -> parseStringLit(t)
            t.kind == Kind.DOLLAR -> {                                                     // $ 返回值 / $n 形参引用
                pos++
                if (t.text.length > 1) ParamRefExpr(t.text.drop(1).toInt(), t.range())
                else ReturnSym(t.range())
            }
            t.kind == Kind.TOP -> { pos++; TopExpr(t.range()) }
            t.kind == Kind.BOT -> { pos++; BotExpr(t.range()) }
            t.kind == Kind.LPAREN -> {
                pos++
                val first = parseExpr(angle = false)
                if (peek(Kind.COMMA)) {                                                    // 逗号是元组判据（决策 62）
                    val items = ArrayList<Expr>()
                    items += first
                    while (peek(Kind.COMMA)) {
                        pos++
                        if (peek(Kind.RPAREN)) break
                        items += parseExpr(angle = false)
                    }
                    expect(Kind.RPAREN, ")")
                    TupleExpr(items, t.range())
                } else {
                    expect(Kind.RPAREN, ")")
                    first
                }
            }
            t.kind == Kind.LBRACE && (braceIsLambda(pos) || braceHasImplicitBody(pos)) -> parseBraceLambda()
            t.kind == Kind.VAR -> parseVarExpr(emptyList())
            t.kind == Kind.AT -> {
                val at = cur()
                val a = parseAnnotations()
                if (peek(Kind.VAR)) parseVarExpr(a) else errExpr("期望 var 变量声明", at)
            }
            t.kind == Kind.FUN -> parseAnonFunExpr()
            t.kind == Kind.IF -> parseIf()
            t.kind == Kind.WHEN -> parseWhen()
            t.kind == Kind.FORALL || t.kind == Kind.EXISTS || t.kind == Kind.EXISTS1 -> parseQuant(next().text, angle = angle)
            t.kind == Kind.IDENT && t.text in asciiQuant && peekAt(1)?.kind == Kind.IDENT ->
                parseQuant(asciiQuant.getValue(next().text), angle = angle)                        // S-3 ASCII 别名归一
            t.kind == Kind.IDENT || t.kind == Kind.SYMBOL -> { pos++; NameRef(t.text, t.range()) }
            t.kind in symbolCallStart && peekAt(1)?.kind == Kind.LPAREN -> {
                val op = next().text
                SymbolCallExpr(op, parseArgs().first, t.range())                            // 符号调用只取位置实参
            }
            t.kind in opPrefixKinds && peekAt(1)?.kind == Kind.LPAREN -> {                  // 运算符前缀调用 +(1,2)
                val opTok = next()   // 先消费算符（同 symbolCallStart 分支：parseArgs 的 next() 消费开括号）
                val (pa, na) = parseArgs()
                CallExpr(NameRef(opTok.text, t.range()), pa, na, t.range())
            }
            else -> errExpr("期望表达式", t)
        }
    }

    /** S-40【量词表达式】：q 变量名+ . 体；@p 命题量化、#A 类型量化（名字带前缀存入）。 */
    private fun parseQuant(q: String, angle: Boolean): Expr {
        val t = cur()   // 首个变量名 token（量词 token 已由调用者消费）
        val names = ArrayList<String>()
        while (cur().kind != Kind.DOT) {
            if (peek(Kind.EOF)) { diagError("量词变量列表后的句点（如 ∀x. body）", cur()); break }
            val isProp = peek(Kind.AT); if (isProp) pos++
            val isType = peek(Kind.HASH); if (isType) pos++
            val n = nameToken() ?: break
            names += (if (isProp) "@" else if (isType) "#" else "") + n
            if (peek(Kind.COMMA)) {
                pos++
                if (cur().kind == Kind.RPAREN || cur().kind == Kind.RBRACKET || cur().kind == Kind.RBRACE) break
            }
        }
        if (expect(Kind.DOT, ".") == null)
            return QuantExpr(q, names, errExpr("量词变量表后须以句点收尾", cur()), t.range())
        return QuantExpr(q, names, parseExpr(angle = angle), t.range())
    }

    /** D-4：is 之后是否构造子模式形态（Ctor( / E::A(，圆括号实参）；参数为相对游标的偏移。 */
    private fun isCtorForm(i: Int): Boolean {
        if (peekAt(i)?.kind != Kind.IDENT) return false
        if (peekAt(i + 1)?.kind == Kind.LPAREN) return true
        if (peekAt(i + 1)?.kind == Kind.COLON2)
            return peekAt(i + 2)?.kind == Kind.IDENT && peekAt(i + 3)?.kind == Kind.LPAREN
        return false
    }

    /**
     * S-34【if 表达式】：
     * - 圆括号形态：if (cond) then else——cond 解析期间放行 is 构造模式（ifIsCond）；
     *   cond 是 IsPatExpr 时脱糖为 when（S-10：if (e is PAT) A else B → when (e) { is PAT => A; else => B }）；
     *   组合条件含模式哨兵则诊断 + ErrorExpr 半成品（模式绑定无法进入组合作用域）。
     * - 数学风命题形态：if c then a else b（then/else 停止词防中缀糖吞）。
     */
    private fun parseIf(): Expr {
        val ifTok = cur()
        next()   // if（分派保证）
        if (peek(Kind.LPAREN)) {
            pos++
            val saved = ifIsCond
            ifIsCond = true
            val cond = try { parseExpr(angle = false) } finally { ifIsCond = saved }
            expect(Kind.RPAREN, ")")
            if (cond !is IsPatExpr && containsIsPat(cond)) {
                val msg = "ifis 智能解构条件须单独为 `表达式 is 构造模式`（如 `o is Some(e)`）；组合条件请改用 when 表达式"
                _diag += Diag(Severity.ERROR, "E-SYNTAX", cond.pos, msg)
                return IfExpr(ErrorExpr(msg, cond.pos), parseBranchBlock(),
                    if (peek(Kind.ELSE)) { pos++; parseBranchBlock() } else null, ifTok.range())
            }
            val thenB = parseBranchBlock()
            val elseB = if (peek(Kind.ELSE)) { pos++; parseBranchBlock() } else null
            return if (cond is IsPatExpr) {
                val arms = ArrayList<WhenArm>()
                arms += WhenArm(pattern = cond.pat, guard = null, body = thenB, pos = cond.pos)
                if (elseB != null) arms += WhenArm(pattern = PatElse(cond.pos), guard = null, body = elseB, pos = cond.pos)
                WhenExpr(cond.target, null, arms, ifTok.range())
            } else IfExpr(cond, thenB, elseB, ifTok.range())
        }
        val saved = stopIdents
        stopIdents = setOf("then")
        val cond = parseExpr(angle = false)
        stopIdents = saved
        val tt = cur()
        if (tt.kind != Kind.IDENT || tt.text != "then")
            return errExpr("数学风 if 缺少 then（写法：if c then a else b）", tt)
        pos++
        stopIdents = setOf("else")
        val thenE = parseExpr(angle = false)
        stopIdents = saved
        val et = cur()
        if (et.kind != Kind.ELSE && !(et.kind == Kind.IDENT && et.text == "else"))
            return errExpr("数学风 if 缺少 else（写法：if c then a else b）", et)
        pos++
        val elseE = parseExpr(angle = false)
        return IfExpr(cond, BlockExpr(listOf(ExprStmt(thenE, thenE.pos)), thenE.pos),
            BlockExpr(listOf(ExprStmt(elseE, elseE.pos)), elseE.pos), ifTok.range())
    }

    /** 表达式里是否藏着 IsPatExpr 哨兵——防组合条件/实参位吞掉模式绑定。 */
    private fun containsIsPat(e: Expr): Boolean = when (e) {
        is IsPatExpr -> true
        is BinExpr -> containsIsPat(e.left) || containsIsPat(e.right)
        is UniExpr -> containsIsPat(e.operand)
        is CallExpr -> containsIsPat(e.callee) || e.args.any { containsIsPat(it) } || e.namedArgs.values.any { containsIsPat(it) }
        is FieldExpr -> containsIsPat(e.target)
        is InstExpr -> containsIsPat(e.target) || e.terms.any { containsIsPat(it) }
        is TupleExpr -> e.items.any { containsIsPat(it) }
        is ByExpr -> containsIsPat(e.target) || e.props.any { containsIsPat(it) }
        is TypeTestExpr -> containsIsPat(e.target)
        is IfExpr -> containsIsPat(e.cond) || containsIsPat(e.thenBlock) || (e.elseBlock?.let { containsIsPat(it) } ?: false)
        is WhenExpr -> (e.subject?.let { containsIsPat(it) } ?: false) ||
            e.arms.any { a ->
                (a.guard?.let { containsIsPat(it) } ?: false) || (a.cond?.let { containsIsPat(it) } ?: false) ||
                    containsIsPat(a.body)
            }
        is LambdaExpr -> containsIsPat(e.body)
        is BlockExpr -> e.stmts.any { containsIsPatStmt(it) }
        is StructCtorExpr -> e.assigns.any { containsIsPat(it.second) }
        is FunRefExpr -> containsIsPat(e.target)
        is SymbolCallExpr -> e.args.any { containsIsPat(it) }
        is TaskExpr -> containsIsPat(e.body)
        is FlowExpr -> containsIsPat(e.body)
        is QuantExpr -> containsIsPat(e.body)
        else -> false
    }

    private fun containsIsPatStmt(s: Stmt): Boolean = when (s) {
        is ExprStmt -> containsIsPat(s.expr)
        is VarStmt -> containsIsPat(s.value)
        is AssignStmt -> containsIsPat(s.target) || containsIsPat(s.value)
        is ReturnStmt -> s.expr?.let { containsIsPat(it) } ?: false
        is UncheckedStmt -> containsIsPatStmt(s.inner)
        else -> false
    }

    /** if/when 分支体：{ 语句… } 块或单表达式（自动包装成块）。 */
    private fun parseBranchBlock(): BlockExpr =
        if (peek(Kind.LBRACE)) parseBlock()
        else { val e = parseExpr(angle = false); BlockExpr(listOf(ExprStmt(e, e.pos)), e.pos) }

    /**
     * S-37【when 表达式】：主题可选（when(x) / when(var e = 0) / 无主题）。
     * D-15 模板 when：主题 t in _ → 脱糖为无主题 when，分支模式 p 变条件 t in p。
     * 分支 => 必写；无主题布尔分支用 PatCond 哨兵 + WhenArm.cond。
     */
    private fun parseWhen(): Expr {
        val t = next()   // when（分派保证）
        var binding: String? = null
        var subject: Expr? = null
        var templateBase: Expr? = null
        if (peek(Kind.LPAREN)) {
            pos++
            subject = if (peek(Kind.VAR)) {
                pos++
                binding = nameToken()
                if (expect(Kind.ASSIGN, "=") == null) null else parseExpr(angle = false)
            } else parseExpr(angle = false)
            expect(Kind.RPAREN, ")")
            (subject as? CallExpr)?.let { ce ->                                                  // D-15 模板脱糖
                val r = ce.args.getOrNull(1)
                if ((ce.callee as? NameRef)?.name == "in" && r is NameRef && r.name == "_") {
                    templateBase = ce.args.getOrNull(0)
                    subject = null
                }
            }
        }
        expect(Kind.LBRACE, "{")
        val arms = ArrayList<WhenArm>()
        while (!peek(Kind.RBRACE) && !peek(Kind.EOF)) {
            val before = pos
            val al = cur()
            var pat: Pattern
            var guard: Expr? = null
            var cond: Expr? = null
            when {
                templateBase != null -> {                                                         // 模板分支：表达式模式，else/_ 兜底
                    pat = if (peek(Kind.ELSE)) { pos++; PatElse(al.range()) }
                    else {
                        val pe = parseExpr(angle = false, stopArrow = true)
                        if (pe is NameRef && pe.name == "_") PatElse(al.range())
                        else {
                            cond = CallExpr(NameRef("in", al.range()), listOf(templateBase!!, pe), pos = al.range())
                            PatCond(al.range())
                        }
                    }
                    guard = if (peek(Kind.IF)) { pos++; parseExpr(angle = false, stopArrow = true) } else null
                }
                subject == null && peek(Kind.ELSE) -> { pos++; pat = PatElse(al.range()) }
                subject == null -> {                                                              // 无主题：布尔条件存 cond（PatCond 哨兵）
                    cond = parseExpr(angle = false, stopArrow = true)
                    pat = PatCond(al.range())
                }
                else -> {
                    pat = parsePattern()
                    guard = if (peek(Kind.IF)) { pos++; parseExpr(angle = false, stopArrow = true) } else null
                }
            }
            // t42（用户裁决 2026-11-06）：分支箭头统一为 `->`——`is (`…`)` 后遇 `=>` 属函数类型续读、
            // 遇 `->` 才是模式结束（`is (Nat)=>Nat -> 2` / `is (a, b) -> …` 确定性消歧，禁回溯试探）
            when (cur().kind) {
                Kind.ARROW -> pos++
                Kind.DARROW -> {
                    diagError("when 分支箭头已统一为 `->`（`=>` 与函数类型箭头冲突）：" +
                        "请把分支箭头改写为 `->`（例如 `is A -> 1`）", cur())
                    pos++   // 消费 => 继续读分支体（容错链不断）
                }
                else -> { diagError("期望分支箭头 `->`（when 分支箭头已统一为单箭头；例如 `is A -> 1`）", cur()); break }
            }
            arms += WhenArm(pattern = pat, guard = guard, body = parseBranchBody(stopArrow = templateBase != null),
                cond = cond, pos = al.range())
            skipSemis()
            if (peek(Kind.COMMA)) pos++
            ensureProgress(before)
        }
        expect(Kind.RBRACE, "}")
        return WhenExpr(subject, binding, arms, t.range())
    }

    /** S-42【模式】：is 构造解构 / is 类型 as / else / 元组 / 字面量 / 名字（解构必须显式 is）。 */
    private fun parsePattern(): Pattern {
        val t = cur()
        if (t.kind == Kind.IS) {
            pos++
            if (peek(Kind.IDENT) && peekAt(1)?.kind == Kind.COLON2) {                            // is E::A[(模式实参)]
                val en = next().text
                if (expect(Kind.COLON2, "::") == null) return errPattern("期望枚举成员路径（如 is E::A(x)）", cur())
                val ctor = nameToken() ?: return errPattern("期望构造子名", cur())
                val full = "$en::$ctor"
                if (peek(Kind.LPAREN)) {
                    pos++
                    val args = ArrayList<Pattern>()
                    if (!peek(Kind.RPAREN)) {
                        while (true) {
                            val before = pos
                            args += parsePattern()
                            ensureProgress(before)
                            if (peek(Kind.COMMA)) { pos++; if (peek(Kind.RPAREN)) break; continue }
                            break
                        }
                    }
                    expect(Kind.RPAREN, ")")
                    return PatCtor(full, args, t.range(), paren = true)
                }
                return PatCtor(full, emptyList(), t.range())
            }
            if (peek(Kind.IDENT) && peekAt(1)?.kind == Kind.LPAREN) {                            // is Ctor(模式实参)
                val ctor = next().text
                pos++
                val args = ArrayList<Pattern>()
                if (!peek(Kind.RPAREN)) {
                    while (true) {
                        val before = pos
                        args += parsePattern()
                        ensureProgress(before)
                        if (peek(Kind.COMMA)) { pos++; if (peek(Kind.RPAREN)) break; continue }
                        break
                    }
                }
                expect(Kind.RPAREN, ")")
                return PatCtor(ctor, args, t.range(), paren = true)
            }
            val ty = parseType()                                                                 // is 类型 [as 绑定名]
            val bind = if (peek(Kind.IDENT) && cur().text == "as") { pos++; nameToken() } else null
            return PatIs(ty, bind, t.range())
        }
        if (t.kind == Kind.ELSE || (t.kind == Kind.IDENT && t.text == "else")) {
            pos++
            return PatElse(t.range())
        }
        if (t.kind == Kind.LPAREN) {                                                             // 元组模式（至少一个逗号）
            pos++
            val items = ArrayList<Pattern>()
            items += parsePattern()
            while (peek(Kind.COMMA)) {
                pos++
                if (peek(Kind.RPAREN)) break
                items += parsePattern()
            }
            expect(Kind.RPAREN, ")")
            return if (items.size == 1) items[0] else PatTuple(items, t.range())
        }
        if (t.kind == Kind.INT) { pos++; return PatLit(IntLit(t.text, t.range()), t.range()) }
        if (t.kind == Kind.STR) { pos++; return PatLit(StrLit(t.text, t.range()), t.range()) }
        if (t.kind == Kind.IDENT) {
            val nm = next().text
            if (nm == "_") return PatBind("_", t.range())
            if (peek(Kind.COLON2)) {                                                             // E::A 折叠为构造子复合名
                pos++
                val ctor = nameToken() ?: return errPattern("期望构造子名", cur())
                val full = "$nm::$ctor"
                if (peek(Kind.LPAREN) && peekAt(1)?.kind != Kind.RPAREN) {                       // 非空括号模式拒绝（须显式 is）
                    diagError("以 is 开头的构造子解构（类型解构须显式 is：`is $full(...)`）", cur())
                    skipParens()
                    return errPattern("类型解构须显式 is", t)
                }
                if (peek(Kind.LPAREN)) { pos++; expect(Kind.RPAREN, ")") }                        // E::A() 空参名字模式（S-42）
                return PatCtor(full, emptyList(), t.range(), paren = true)
            }
            if (peek(Kind.LPAREN) && peekAt(1)?.kind != Kind.RPAREN) {                           // 非空括号模式拒绝（须显式 is）
                diagError("以 is 开头的构造子解构（类型解构须显式 is：`is $nm(...)`）", cur())
                skipParens()
                return errPattern("类型解构须显式 is", t)
            }
            val parenWritten = peek(Kind.LPAREN)                                               // t41：A() 与裸名 A 的区分印记
            if (parenWritten) { pos++; expect(Kind.RPAREN, ")") }                                 // A() 无参构造子值比较（S-42 名字模式）
            return PatCtor(nm, emptyList(), t.range(), paren = parenWritten)                      // 无参构造子与绑定变量同形，语义层裁定
        }
        return errPattern("期望模式", t)
    }

    /** [q₁, q₂] 命题列表，供 by/axiom 复用。 */
    private fun parseBracketList(): List<Expr> {
        next()   // [（分派保证）
        val out = ArrayList<Expr>()
        if (!peek(Kind.RBRACKET)) {
            while (true) {
                out += parseExpr(angle = false)
                if (peek(Kind.COMMA)) { pos++; if (peek(Kind.RBRACKET)) break; continue }
                break
            }
        }
        expect(Kind.RBRACKET, "]")
        return out
    }

    /** 分支体：{ 语句 } 块 / { 参数 => 体 } lambda / 单表达式；stopArrow=true（模板 when）防后缀括号吞下一 arm。
     *  t42：lambda 诗据（D-17a，体内仍用 `=>`）显式判定——与 parsePrimary 同口径，**仅**显式带 => 的花括号
     *  才按 lambda 解（隐式体不在此放宽：`{ unchecked print(n) }` 仍是语句块，防暗病）。 */
    private fun parseBranchBody(stopArrow: Boolean = false): Expr =
        if (peek(Kind.LBRACE)) {
            if (braceIsLambda(pos)) parseBraceLambda()
            else parseBlock()
        } else parseExpr(angle = false, stopArrow = stopArrow)

    /**
     * S-36【lambda】：{ a, b => 体 } / 零参 { => 7 } / 解构 {(a, b) => …} / 隐式单参 { x + 1 }。
     * 诗据（D-17）由 [braceIsLambda]/[braceHasImplicitBody] 在词法层先行判定（扫描 token 流不消费）。
     */
    private fun parseBraceLambda(): Expr {
        val start = pos   // 指向 {（未消费）
        val lb = next()
        val ps = ArrayList<String>()
        var hasArrow = false
        if (braceIsLambda(start)) {
            while (!peek(Kind.DARROW) && !peek(Kind.RBRACE) && !peek(Kind.EOF)) {                 // 显式参数表（可为空 = 零参）
                val before = pos
                val p = if (peek(Kind.LPAREN)) parseLambdaDestructName() else nameToken()
                if (p == null) { ensureProgress(before); break }
                ps += p
                if (peek(Kind.COMMA)) pos++
            }
            hasArrow = expect(Kind.DARROW, "=>") != null
        }
        val body = parseLambdaBody()
        expect(Kind.RBRACE, "}")
        return if (hasArrow) LambdaExpr(ps, body, pos = lb.range())
        else LambdaExpr(listOf("_"), body, implicitIt = true, pos = lb.range())                   // 隐式单参（_ 引用形参，唯一 _ 可引用通道）
    }

    /** lambda 体：语句序列；仅单条表达式语句时退化为表达式体。 */
    private fun parseLambdaBody(): Expr {
        val stmts = ArrayList<Stmt>()
        while (!peek(Kind.RBRACE) && !peek(Kind.EOF)) {
            skipSemis()
            if (peek(Kind.RBRACE) || peek(Kind.EOF)) break
            val before = pos
            stmts += parseStmt()
            ensureProgress(before)
        }
        return if (stmts.size == 1 && stmts[0] is ExprStmt) (stmts[0] as ExprStmt).expr
        else BlockExpr(stmts, stmts.firstOrNull()?.pos ?: cur().range())
    }

    /**
     * D-17a lambda 诗据：深度 1 内首个 => 之前只允许名字/逗号/解构括号（括号内任意），
     * 且存在至少一个参数项或零参空表——防 when 分支体 { 1 => x } 的箭头被误判。
     */
    private fun braceIsLambda(start: Int): Boolean {
        var depth = 0
        var pd = 0   // 解构参数的括号配对深度 {(a, b) => …}
        var j = start
        var sawBad = false
        while (j < toks.size) {
            val k = toks[j].kind
            when {
                k == Kind.LBRACE -> depth++
                k == Kind.RBRACE -> { depth--; if (depth == 0) return false }
                k == Kind.DARROW -> return if (depth == 1) !sawBad else false
                k == Kind.EOF -> return false
                depth == 1 && k == Kind.LPAREN -> pd++
                depth == 1 && k == Kind.RPAREN -> { if (pd > 0) pd-- else sawBad = true }
                depth == 1 && pd > 0 -> {}
                depth == 1 && (k == Kind.IDENT || k == Kind.SYMBOL) -> {}
                depth == 1 && k == Kind.COMMA -> {}
                depth == 1 -> sawBad = true
                else -> {}
            }
            j++
        }
        return false
    }

    /** D-17b 隐式单参诗据：深度 1 内无 => 且无 :，闭合于深度 0——{ 表达式 } 形态即隐式体 lambda。 */
    private fun braceHasImplicitBody(start: Int): Boolean {
        var depth = 0
        var j = start
        while (j < toks.size) {
            when (toks[j].kind) {
                Kind.LBRACE -> depth++
                Kind.RBRACE -> { depth--; if (depth == 0) return true }
                Kind.COLON, Kind.DARROW, Kind.ARROW -> return false
                Kind.EOF -> return false
                else -> {}
            }
            j++
        }
        return false
    }

    // ============ 片段入口（§2.1）——宿主契约：整段耗尽 + 无 ERROR 级诊断，否则抛 [ParseFailure] ============

    internal fun snippetDecl(): Decl {
        val anns = if (peek(Kind.AT)) parseAnnotations() else emptyList()
        val d = parseDecl()
        val merged: Decl = when {
            anns.isEmpty() -> d
            d is FunDecl -> d.copy(annotations = anns + d.annotations)
            d is StructDecl -> d.copy(annotations = anns + d.annotations)
            d is TraitDecl -> d.copy(annotations = anns + d.annotations)
            else -> d
        }
        strictFail()
        requireSnippetEof("声明")
        return merged
    }

    internal fun snippetFunBody(): Expr {
        val body: Expr = when {
            peek(Kind.ASSIGN) && peekAt(1)?.kind == Kind.LBRACE -> {
                val lb = peekAt(1)!!
                pos++
                skipBraced()
                errExpr("函数体不能 `= {…}` 混写：单表达式请去掉花括号写 `= 表达式`，块体请去掉等号写 `{ … }`", lb)
            }
            peek(Kind.ASSIGN) -> { pos++; parseExpr(angle = false) }
            peek(Kind.LBRACE) -> parseBlock()
            else -> parseExpr(angle = false)
        }
        strictFail()
        requireSnippetEof("函数体")
        return body
    }

    internal fun snippetStmts(): List<Stmt> {
        skipSemis()
        val out = ArrayList<Stmt>()
        while (!peek(Kind.EOF)) {
            val before = pos
            out += parseStmt()
            ensureProgress(before)
            skipSemis()
        }
        strictFail()
        return out
    }

    private fun requireSnippetEof(what: String) {
        skipSemis()
        if (!peek(Kind.EOF)) throw ParseFailure(fileName, cur().range(), "$what 片段后面有多余内容")
    }

    private fun strictFail() {
        _diag.firstOrNull { it.severity == Severity.ERROR }?.let {
            throw ParseFailure(fileName, it.pos, it.message)
        }
    }

}
