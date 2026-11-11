package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.ident
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.hir.SymbolKind
import sublimation.frontend.hir.SymbolRef

/**
 * 名称解析（ADR-6 §三 NameResolver）：ADR-1 §3.2「未解析 → 诊断 + 半成品节点」的落点。
 *
 * 遮蔽：分层作用域内→外（[SymbolTable.lookupValue]），与旧 Frame 链同构（旧 checkExpr 先查局部帧，
 * 再查 ctor/fun 等分表的口径）。限定引用：本期单文件——QualifiedType 的模块段折叠留给后期模块
 * 挂载任务（ADR-1 §3.2），模块限定形态不落诊断、symbol=null（形态保留）。
 *
 * 码集：E-UNBOUND-NAME 为旧码（P-7）；_ 丢弃标记的引用拦截为旧 E-UNBOUND-NAME 专怨口径
 * （作者裁决 2026-11-02：显式声明的 _ 不入引用通道，默认单参 lambda 的隐式 it 例外——
 * 由 lambda 帧内声明 _ 落地）。
 */
internal class NameResolver(private val syms: SymbolTable, private val diag: DiagBag) {

    /** t84：true/false → True/False（供否定提示等「推荐的写法」保持可编译——ADR-48 §八）。 */
    private fun boolLitCap(tail: String): String =
        when (tail) { "true" -> "True"; "false" -> "False"; else -> tail }

    /** 值位名解析：局部帧链 → ctor/fun/theorem/propdef/内建函数。未解析落 E-UNBOUND-NAME。 */
    fun resolveValue(name: String, pos: SourceRange): SymbolRef? {
        syms.lookupValue(name)?.let { return it.ref() }
        // t22（B6，ADR-20）：命题位静默模式——axiom/∵∴/命题参数供给的命题项自由变量按旧
        // PropLogic.fromExpr 口径不做值位查名（schematic 变量），symbol=null 不落诊断。
        if (silentNames) return null
        // t92（待裁-B 退役）：`<->` 内建含义退役——未声明即落 E-OP-RETIRED 并提示命题层 `↔`
        // （保留用户自定义 `fun <->(a, b)` 的合法通道，R2；命题位的 `<->` 由 PropNormalizer 直达 HirPIff）
        if (name == "<->") {
            diag.error("E-OP-RETIRED", pos,
                "`<->` 算符已退役：命题层的等价写作 `↔`；如需在值位使用，可自定义 `fun <->(a: Bool, b: Bool)`")
            return null
        }

        if (name == "_") {
            diag.error("E-UNBOUND-NAME", pos,
                "`_` 是丢弃标记，不能作为值使用——只有默认单参的 lambda（如 `{ _ + 1 }`）里 `_` 才指那个隐式参数；要引用请给变量取个名字")
        } else {
            // P4（批次 3，N1）：`¬x` 整体已是标识符——值位未声明时给**值位正确**的提示：值位只能 `!x` 取反
            // （`¬<…>` 是命题写法，对值位布尔是驴唇不对马嘴——ADR-48 §八可编译性规则）
            // t84（Q12 收口）：字面量尾 true/false 须提议大写写法，否则提示本身不可编译
            val notHint = if (name.length > 1 && name.startsWith('¬'))
                "——若想写布尔取反，请用 " + ident("!" + boolLitCap(name.substring(1)))
            else ""
            // t84（Q12 收口）：小写 true/false 已移出内建值——给可编译的大写写法（ADR-48 §八）
            val boolHint = if (name == "true" || name == "false")
                "——Bool 字面量写作 " + ident(if (name == "true") "True" else "False") else ""
            // t86（F2）：值位出现的原生命题连接词——qa 实测盲区：只落 E-UNBOUND-NAME 不给导向。
            // 命题连接词只能出现在命题位；值位的布尔与/或写作 & / |（推荐写法本身可编译，ADR-48 §八）
            val nativeHint = when (name) {
                "∧" -> "——`∧` 是命题连接词，只能出现在命题位；值位的布尔与写作 `&`"
                "∨" -> "——`∨` 是命题连接词，只能出现在命题位；值位的布尔或写作 `|`"
                "→", "↔" -> "——`$name` 是命题连接词，只能出现在命题位"
                "¬" -> "——`¬` 是命题连接词；值位的布尔取反写作 `!x`（命题位的否定与值位取反不同）"
                else -> ""
            }
            // P5（批次 2）：`p∧q` 粘连成一个标识符 ⇒ 说清"这是整体"，并给加空格的正确写法（R1）
            val glue = NativeProps.firstGluedSymbol(name)?.let { NativeProps.glueHint(name, it) } ?: ""
            diag.error("E-UNBOUND-NAME", pos,
                "未定义的名称 " + ident(name) + "（作用域中无此变量/函数/构造子）" + notHint + boolHint + nativeHint + glue)
        }
        return null
    }

    /** 类型位名解析：struct/enum/trait/alias/内建类型。未解析落 E-UNBOUND-NAME（旧码，P-7）。 */
    fun resolveType(name: String, pos: SourceRange): SymbolRef? {
        syms.lookupType(name)?.let { return it.ref() }
        diag.error("E-UNBOUND-NAME", pos, "未知类型 " + ident(name))
        return null
    }

    /** 静默值位查询（命题项位用：不落诊断，未解析返回 null——symbol 记录与否由调用方决定）。 */
    fun resolveValueQuiet(name: String): SymbolRef? = syms.lookupValue(name)?.ref()

    /** 静默类型位查询（命题项位的类型名，如 `(i : Nat)` 的 Nat——旧命题面不做查名）。 */
    fun resolveTypeQuiet(name: String): SymbolRef? = syms.lookupType(name)?.ref()

    /** 命题项自由变量静默模式（B6）：块内 resolveValue 未解析不落 E-UNBOUND-NAME。 */
    fun <T> schematicNames(block: () -> T): T {
        val old = silentNames
        silentNames = true
        try { return block() } finally { silentNames = old }
    }

    private var silentNames = false

    /** 构造子解析（模式/引用位）：裸名 / Enum::C 复合名。未解析落 E-UNBOUND-NAME。 */
    fun resolveCtor(name: String, pos: SourceRange): SymbolRef? {
        syms.lookupCtor(name)?.let { return it.entry.ref() }
        diag.error("E-UNBOUND-NAME", pos, "未定义的构造子 " + ident(name))
        return null
    }


    /**
     * 量词绑定变量作用域（ADR-14：∀/∃/∃! 的 names 须绑定进帧，体内引用才不落 E-UNBOUND-NAME）。
     * 成对 push/pop 的结构化通道：names 以 PARAM 登记进 BLOCK 帧，块内归一体，出栈即弃。
     */
    fun <T> quantified(names: List<String>, origin: SourceRange, block: () -> T): T =
        syms.frame(ScopeKind.BLOCK) {
            names.forEach { syms.declareLocal(SymbolKind.PARAM, it, origin) }
            block()
        }
}
