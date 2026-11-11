package sublimation.frontend.semantic

import sublimation.frontend.ast.BinExpr
import sublimation.frontend.ast.BotExpr
import sublimation.frontend.ast.CallExpr
import sublimation.frontend.ast.ErrorExpr
import sublimation.frontend.ast.Expr
import sublimation.frontend.ast.FieldExpr
import sublimation.frontend.ast.InstExpr
import sublimation.frontend.ast.IntLit
import sublimation.frontend.ast.NameRef
import sublimation.frontend.ast.Param
import sublimation.frontend.ast.ParamRefExpr
import sublimation.frontend.ast.QuantExpr
import sublimation.frontend.ast.ReturnSym
import sublimation.frontend.ast.StrLit
import sublimation.frontend.ast.SymbolCallExpr
import sublimation.frontend.ast.TopExpr
import sublimation.frontend.ast.UniExpr
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.hir.HirMutType
import sublimation.frontend.hir.HirRefType
import sublimation.frontend.diag.ident
import sublimation.frontend.diag.NodeId
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.hir.HirNote
import sublimation.frontend.hir.HirNoteKind
import sublimation.frontend.hir.HirPAnd
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirPBot
import sublimation.frontend.hir.HirPExists
import sublimation.frontend.hir.HirPExists1
import sublimation.frontend.hir.HirPIff
import sublimation.frontend.hir.HirPImp
import sublimation.frontend.hir.HirPForall
import sublimation.frontend.hir.HirPNot
import sublimation.frontend.hir.HirPOr
import sublimation.frontend.hir.HirPTop
import sublimation.frontend.hir.HirProp
import sublimation.frontend.hir.HirTBin
import sublimation.frontend.hir.HirTBot
import sublimation.frontend.hir.HirTCall
import sublimation.frontend.hir.HirTDollar
import sublimation.frontend.hir.HirTField
import sublimation.frontend.hir.HirTInst
import sublimation.frontend.hir.HirTInt
import sublimation.frontend.hir.HirTName
import sublimation.frontend.hir.HirTParamRef
import sublimation.frontend.hir.HirTStr
import sublimation.frontend.hir.HirTSymbolCall
import sublimation.frontend.hir.HirTTop
import sublimation.frontend.hir.HirTUni
import sublimation.frontend.hir.HirTerm

/**
 * 命题位归一（ADR-6 §三 PropNormalizer 的 t7 结构部分）：命题位表达式 → HirProp / HirTerm。
 *
 * 落点（ADR-1 §3.2 命题面映射；ADR-5 决策 3：逃逸头不复刻）：
 * - 逻辑连接词 → / ↔ / ∧ / ∨ / ¬ → HirPImp / HirPIff / HirPAnd / HirPOr / HirPNot（P-IMP）；
 * - 量词 ∀ / ∃ / ∃! → HirPForall / HirPExists / HirPExists1；
 * - 原子命题：名字 / 参数化 / 比较 / 两参解糖 / ∈ 归一为 in → HirPAtom(head, terms)；
 * - 命题项：HirTerm 家族（HirTCall / HirTInst 以任意项为 callee/target，覆盖旧 __call__/__inst__ 逃逸形）；
 * - 命题位无法归一的程序面表达式 → 新码 E-PROP-FORM 诊断 + 半成品占位（PBot /
 *   __unrepresentable__ 哨兵项——编译已失败、下游不消费；见 ADR-9）。
 *
 * 决策 65（$n → 形参名，ADR-1 §3.1.2.3）：仅当前件/后件/约束位传入 dollarParams 时改写，
 * 逐点落 H-PARAM-REF 提示（旧口径）与 D-DOLLAR-N 留痕；越界落 E-PARAM-REF。
 * 其他命题位（axiom 事实、命题 when/if 条件等）保持 $n 原形（旧 PropLogic.fromExpr 同构）。
 */
internal class PropNormalizer(
    private val freshNid: () -> NodeId,
    private val resolver: NameResolver,
    private val diag: DiagBag,
    /** 类型参数词法栈判定（Elaborator 侧 typeParams 的投影）：命题项位引用类型参数名静默 symbol=null，
     *  与 type() 的既有口径一致（ADR-14：ZFC 风格 x ∈ a 中 a 是函数类型参数，不报 E-UNBOUND-NAME）。 */
    private val isTypeParam: (String) -> Boolean,
    /** t98（N2 @ref · 规则 4）：名字 → HIR 类型查表（Elaborator 侧 symbols.lookupValue()?.type 投影）——
     *  相等命题的视图项（@ref/@mut）判定原料。 */
    private val nameType: (String) -> sublimation.frontend.hir.HirType?,
    private val typeRender: (sublimation.frontend.hir.HirType) -> String,
) {
    /** 命题位表达式 → 命题；dollarParams 非 null 时按决策 65 改写其中的 $n。 */
    fun prop(e: Expr, dollarParams: List<Param>? = null): HirProp = when (e) {
        is TopExpr -> HirPTop(freshNid(), e.pos)
        is BotExpr -> HirPBot(freshNid(), e.pos)
        is ReturnSym -> HirPAtom("$", emptyList(), freshNid(), e.pos)
        is ParamRefExpr -> dollarProp(e, dollarParams)
        is UniExpr -> HirPNot(prop(e.operand, dollarParams), freshNid(), e.pos)
        is BinExpr -> when (e.op) {
            "->", "→" -> HirPImp(prop(e.left, dollarParams), prop(e.right, dollarParams), freshNid(), e.pos)
            "<->", "↔" -> HirPIff(prop(e.left, dollarParams), prop(e.right, dollarParams), freshNid(), e.pos)
            "∧" -> HirPAnd(prop(e.left, dollarParams), prop(e.right, dollarParams), freshNid(), e.pos)
            "∨" -> HirPOr(prop(e.left, dollarParams), prop(e.right, dollarParams), freshNid(), e.pos)
            // t85（Q21 命题侧收口）：`&`/`|` 是程序面布尔运算——只作为**命题顶层连接词**时拒（括号内、
            // 实参里、关系算符操作数位的 & 走 term() 表达式通道，不受影响）。复用 E-PROP-FORM（ADR-10
            // 命题位形态哨兵），半成品落 HirPBot 占位，与 ¬(p) 提示分支同模式。
            "&" -> { diag.error("E-PROP-FORM", e.pos,
                "`&` 是程序面布尔运算（表达式层专用）——命题位的布尔与请写 `∧`"); HirPBot(freshNid(), e.pos) }
            "|" -> { diag.error("E-PROP-FORM", e.pos,
                "`|` 是程序面布尔运算（表达式层专用）——命题位的布尔或请写 `∨`，类型联合的 `|` 不受影响"); HirPBot(freshNid(), e.pos) }
            else -> {
                // t98（规则 4 命题待遇，根文档 :142）：相等命题（= / ≠）不承认 @ref 与 @mut 视图类型项
                val op = if (e.op == "∈") "in" else e.op
                if (op == "=" || op == "≠") { viewInEqTerm(e.left, e.pos); viewInEqTerm(e.right, e.pos) }
                HirPAtom(op, listOf(term(e.left, dollarParams), term(e.right, dollarParams)), freshNid(), e.pos)
            }
        }
        is NameRef -> {
            // P4（批次 3 收口，追加验收 #8）：`¬p` 整体是标识符（N1）；命题位出现且未声明（注册名/局部名皆查不到）
            // ⇒ E-UNBOUND-NAME + 否定写法提示——与值位 resolveValue 同口径。声明过的 `¬p` 名照常作原子（N1 的合法性）
            if (e.name.length > 1 && e.name.startsWith('¬') && resolver.resolveValueQuiet(e.name) == null) {
                diag.error("E-UNBOUND-NAME", e.pos,
                    "未定义的名称 " + ident(e.name) + "——若想写否定，请用 " + ident("¬<" + e.name.substring(1) + ">"))
                HirPBot(freshNid(), e.pos)
            } else if (NativeProps.firstGluedSymbol(e.name)?.let { NativeProps.glueHint(e.name, it) } != null &&
                resolver.resolveValueQuiet(e.name) == null
            ) {
                // P5（批次 2）：`p∧q` 粘连成一个标识符（R1 粘连即整体）——命题位给加空格的正确写法
                val sym = NativeProps.firstGluedSymbol(e.name)!!
                diag.error("E-UNBOUND-NAME", e.pos,
                    "未定义的名称 " + ident(e.name) + NativeProps.glueHint(e.name, sym))
                HirPBot(freshNid(), e.pos)
            } else HirPAtom(e.name, emptyList(), freshNid(), e.pos)
        }
        is SymbolCallExpr -> HirPAtom(e.op, e.args.map { term(it, dollarParams) }, freshNid(), e.pos)
        is CallExpr -> (e.callee as? NameRef)?.let { c ->
            // P4（批次 3 收口）：`¬(p)` 是**值位调用形态**——`¬` 是 §5 注册表的原生命题名，
            // 命题应用必须走角括号（只有 `¬<p>` 合法）。命题位落 E-PROP-FORM 并提示正确写法（半成品 PBot 占位）。
            if (c.name == "¬") {
                diag.error("E-PROP-FORM", e.pos,
                    "`¬(p)` 是值位调用形态——原生命题 `¬` 的命题应用须写角括号：`¬<p>`")
                HirPBot(freshNid(), e.pos)
            } else nativePropDispatch(c.name, e.args, e.pos, dollarParams)
                ?: HirPAtom(c.name, e.args.map { term(it, dollarParams) }, freshNid(), e.pos)
        } ?: propUnrepresentable(e)
        is InstExpr -> (e.target as? NameRef)?.let { t ->
            // P4（用户裁决 N1，符号标识符§5 注册表）：`¬<p>` 归一为 HirPNot；其余 §5 原生命题名（P5 批次 1）按注册表分派
            if (t.name == "¬") nativeNotProp(e.terms, e.pos, dollarParams)
            else nativePropDispatch(t.name, e.terms, e.pos, dollarParams)
                ?: HirPAtom(t.name, e.terms.map { term(it, dollarParams) }, freshNid(), e.pos)
        } ?: propUnrepresentable(e)
        is QuantExpr -> resolver.quantified(e.names, e.pos) {
            // 量词变量绑定进作用域帧（ADR-14）后再归一体——体内 x 引用可解析
            when (e.q) {
                "∀" -> HirPForall(e.names, prop(e.body, dollarParams), freshNid(), e.pos)
                "∃" -> HirPExists(e.names, prop(e.body, dollarParams), freshNid(), e.pos)
                else -> HirPExists1(e.names, prop(e.body, dollarParams), freshNid(), e.pos)
            }
        }
        else -> propUnrepresentable(e)
    }

    /** P4（用户裁决 N1）：`¬<p>` 的原生命题归一，与一元通道 `¬p` 同构（HirPNot(prop(operand))）。
     *  元数非 1 落 E-ARITY（¬ 是 1 元前缀，`¬<p, q>` 拒绝）。 */
    private fun nativeNotProp(args: List<Expr>, pos: SourceRange, dollarParams: List<Param>?): HirProp {
        if (args.size != 1) {
            diag.error("E-ARITY", pos,
                "原生命题 `¬` 是 1 元，实参 " + args.size + " 个不符（否定写作：`¬<p>`）")
            return HirPBot(freshNid(), pos)
        }
        return HirPNot(prop(args[0], dollarParams), freshNid(), pos)
    }

    /**
     * P5 批次 1（t73，qa t72 测绘）：§5 原生命题名的命题位分派——表驱动（[NativeProps.propCtors]）。
     *
     * 背景：P5 把 `∧ ∨ → ↔` 名字化后，`p ∧ q` 经 D-5 标识符中缀糖落 `CallExpr("∧", [p, q])`；
     * 若默认产 `HirPAtom("∧")` 则**静默降级为原子命题、不报错、混进事实集 Γ**——先修路再通车
     * （本轮不变词法：该路径当前不可达，见 P5Batch1NativePropDispatchTest 直接构造 AST 的断言）。
     *
     * - `¬` 不走本表（CallExpr 命题位 = P4 终态 E-PROP-FORM；InstExpr = nativeNotProp，ADR-48 §一）；
     * - 关系名（= ≠ ∈ : <:）无独立命题节点——默认原子分支即其正确形态（fall through 返回 null）；
     * - 元数不符 ⇒ E-ARITY，提示写法只用表内元数推出（可编译性规则：ADR-48 §八）。
     */
    private fun nativePropDispatch(name: String, args: List<Expr>, pos: SourceRange, dollarParams: List<Param>?): HirProp? {
        val entry = NativeProps.propCtors[name] ?: return null
        val (arity, ctor) = entry
        val subs = args.map { prop(it, dollarParams) }
        if (subs.size != arity) {
            diag.error("E-ARITY", pos,
                "原生命题 " + ident(name) + " 是 " + arity + " 元，实参 " + subs.size + " 个不符（写作：" + nativeWriteForm(name, arity) + "）")
            return HirPBot(freshNid(), pos)
        }
        return ctor(subs, freshNid(), pos)
    }

    /** P5（批次 2）：项位的原生二元命题名（∧ ∨ → ↔）——`CallExpr(name,[l,r])` 按旧 BinExpr 口径产 HirTBin。
     *  仅 2 元且实参恰好 2 个时命中；其余形态（元数不符）留给调用方原通道（与旧行为一致，不在此报错）。 */
    private fun nativeTermBinName(e: CallExpr): String? {
        val name = (e.callee as? NameRef)?.name ?: return null
        if (NativeProps.propCtors[name]?.first != 2 || e.args.size != 2) return null
        return name
    }

    /** 命题写法提示：从表内元数推出角表形态（不逐名硬编码；产物在 P5 名字化后均可通过编译）。 */
    private fun nativeWriteForm(name: String, arity: Int): String =
        if (arity == 0) ident(name)
        else ident(name + "<" + (1..arity).joinToString(", ") { if (it == 1) "p" else "q" } + ">")

    /**
     * 体尾表达式 → 项（静默版，t9 下文 $ 代换用）：不可命题化的形态返回 null 而不落诊断——
     * 调用方据此跳过该义务核对（半成品树已落 E-PROP-FORM 于命题位，不重复报）。
     */
    fun termQuiet(e: Expr): HirTerm? = when (e) {
        is IntLit -> HirTInt(e.value, freshNid(), e.pos)
        is StrLit -> HirTStr(e.value, freshNid(), e.pos)
        is NameRef -> HirTName(e.name, typeParamQuiet(e.name, e.pos), freshNid(), e.pos)
        is ReturnSym -> HirTDollar(freshNid(), e.pos)
        is TopExpr -> HirTTop(freshNid(), e.pos)
        is BotExpr -> HirTBot(freshNid(), e.pos)
        is BinExpr -> {
            val l = termQuiet(e.left) ?: return null
            val r = termQuiet(e.right) ?: return null
            HirTBin(e.op, l, r, freshNid(), e.pos)
        }
        is UniExpr -> termQuiet(e.operand)?.let { HirTUni(e.op, it, freshNid(), e.pos) }
        is CallExpr -> {
            // P5（批次 2）：项位的原生二元命题名保持旧口径 HirTBin（名字材料化后 `→` 经 D-5 糖落 CallExpr，
            // 若产 HirTCall 会改变既有 HIR 产物——队长 jsoncmp 68 样本逐字比对拦下的正是此处）。
            nativeTermBinName(e)?.let { n ->
                val l = termQuiet(e.args[0]) ?: return null
                val r = termQuiet(e.args[1]) ?: return null
                return HirTBin(n, l, r, freshNid(), e.pos)
            }
            val c = termQuiet(e.callee) ?: return null
            val args = e.args.map { termQuiet(it) ?: return null }
            HirTCall(c, args, freshNid(), e.pos)
        }
        is InstExpr -> {
            val t = termQuiet(e.target) ?: return null
            val terms = e.terms.map { termQuiet(it) ?: return null }
            HirTInst(t, terms, freshNid(), e.pos)
        }
        is FieldExpr -> termQuiet(e.target)?.let { HirTField(it, e.name, freshNid(), e.pos) }
        is SymbolCallExpr -> HirTSymbolCall(e.op, e.args.map { termQuiet(it) ?: return null }, freshNid(), e.pos)
        is ErrorExpr -> null
        else -> null
    }

    /** 命题项表达式 → 项。 */
    fun term(e: Expr, dollarParams: List<Param>? = null): HirTerm = when (e) {
        is IntLit -> HirTInt(e.value, freshNid(), e.pos)
        is StrLit -> HirTStr(e.value, freshNid(), e.pos)
        is NameRef -> HirTName(e.name, typeParamQuiet(e.name, e.pos), freshNid(), e.pos)
        is ReturnSym -> HirTDollar(freshNid(), e.pos)
        is ParamRefExpr -> dollarTerm(e, dollarParams)
        is TopExpr -> HirTTop(freshNid(), e.pos)
        is BotExpr -> HirTBot(freshNid(), e.pos)
        is BinExpr -> HirTBin(e.op, term(e.left, dollarParams), term(e.right, dollarParams), freshNid(), e.pos)
        is UniExpr -> HirTUni(e.op, term(e.operand, dollarParams), freshNid(), e.pos)
        is CallExpr -> nativeTermBinName(e)?.let { n -> HirTBin(n, term(e.args[0], dollarParams), term(e.args[1], dollarParams), freshNid(), e.pos) }
            ?: HirTCall(term(e.callee, dollarParams), e.args.map { term(it, dollarParams) }, freshNid(), e.pos)
        is InstExpr -> HirTInst(term(e.target, dollarParams), e.terms.map { term(it, dollarParams) }, freshNid(), e.pos)
        is FieldExpr -> HirTField(term(e.target, dollarParams), e.name, freshNid(), e.pos)
        is SymbolCallExpr -> HirTSymbolCall(e.op, e.args.map { term(it, dollarParams) }, freshNid(), e.pos)
        is ErrorExpr -> termPlaceholder(e, "解析错误节点：" + e.message)
        else -> termUnrepresentable(e)
    }

    /** 类型参数名静默（symbol=null）；否则走值位解析。
     *  t22（B6，ADR-20）：命题项位名称一律按旧 PropLogic.fromExpr 口径**不做值位查名**——
     *  已知名（值/类型）记 symbol，未知 schematic 变量（axiom 的 a/b、定理命题里的 s 等）与
     *  类型名（`(i : Nat)` 的 Nat）静默 symbol=null/类型引用，不落 E-UNBOUND-NAME。 */
    private fun typeParamQuiet(name: String, pos: SourceRange): sublimation.frontend.hir.SymbolRef? =
        if (isTypeParam(name)) null
        else resolver.resolveValueQuiet(name) ?: resolver.resolveTypeQuiet(name)

    // —— 决策 65：$n 改写为形参名（命题位/项位同源，逐点提示+留痕） ——

    private fun dollarProp(e: ParamRefExpr, params: List<Param>?): HirProp {
        val pi = params?.getOrNull(e.index - 1)
        if (params == null || pi == null) {
            if (params != null)
                diag.error("E-PARAM-REF", e.pos, "上下文里的 $" + e.index + " 越界（函数只有 " + params.size + " 个形参）")
            return HirPAtom("$" + e.index, emptyList(), freshNid(), e.pos)
        }
        diag.hint("H-PARAM-REF", e.pos, "上下文里的 $" + e.index + " 可以直接使用参数名 " + ident(pi.name))
        return HirPAtom(pi.name, emptyList(), freshNid(), e.pos, dollarNote(e, pi.name))
    }

    private fun dollarTerm(e: ParamRefExpr, params: List<Param>?): HirTerm {
        val pi = params?.getOrNull(e.index - 1)
        if (params == null || pi == null) {
            if (params != null)
                diag.error("E-PARAM-REF", e.pos, "上下文里的 $" + e.index + " 越界（函数只有 " + params.size + " 个形参）")
            return HirTParamRef(e.index, freshNid(), e.pos)
        }
        diag.hint("H-PARAM-REF", e.pos, "上下文里的 $" + e.index + " 可以直接使用参数名 " + ident(pi.name))
        return HirTName(pi.name, resolver.resolveValue(pi.name, e.pos), freshNid(), e.pos, dollarNote(e, pi.name))
    }

    private fun dollarNote(e: ParamRefExpr, paramName: String): List<HirNote> = listOf(
        HirNote(HirNoteKind.DESUGAR, "D-DOLLAR-N",
            "上下文 $" + e.index + " 改写为形参名 " + paramName, e.pos, "$" + e.index, paramName))

    // —— 半成品占位（ADR-5 决策 3：逃逸头不复刻，不可表达形态落诊断） ——

    private fun propUnrepresentable(e: Expr): HirProp {
        diag.error("E-PROP-FORM", e.pos,
            "命题位出现无法归一的程序面表达式（" + e.javaClass.simpleName + "）——命题只由逻辑连接词、量词与原子谓词构成")
        return HirPBot(freshNid(), e.pos, unreprNote(e, "命题位半成品占位"))
    }

    private fun termUnrepresentable(e: Expr): HirTerm {
        diag.error("E-PROP-FORM", e.pos,
            "命题项位出现无法归一的表达式（" + e.javaClass.simpleName + "）——命题项只覆盖字面量/名字/$/运算/调用/实例化/字段/符号前缀调用")
        return termPlaceholder(e, "命题项位半成品占位")
    }

    private fun termPlaceholder(e: Expr, message: String): HirTerm =
        HirTName("__unrepresentable__", null, freshNid(), e.pos, unreprNote(e, message))

    private fun unreprNote(e: Expr, message: String): List<HirNote> = listOf(
        HirNote(HirNoteKind.DESUGAR, "D-PROP-UNREPR", message + "（原形：" + e.javaClass.simpleName + "）", e.pos))

    /** t98（N2 @ref · 规则 4 命题待遇，根文档 :142「引用与可变一样不可预测，因此在上下文中享有和可变相同的待遇」）:
     *  相等命题两侧名字项的类型若含 @ref（或对称的 @mut，根文档 :142 强制）视图 ⇒ E-PROP-VIEW error（阶段 B 硬拒，队长 2026-11-10 批）。
     *  仅直接名字项——字段链项（b.n）与其余项形态留后续形态扩展。视图值可变不可预测，上下文不承认其相等。 */
    private fun viewInEqTerm(e: Expr, pos: SourceRange) {
        val nm = (e as? NameRef)?.name ?: return
        val t = nameType(nm) ?: return
        // t98 阶段 B（队长 2026-11-10 批裁决①）:warn 报告态收档，翻 error 硬拒
        if (containsView(t)) diag.error("E-PROP-VIEW", pos,
            "相等命题不承认 " + nm + " 的可变/引用视图类型 " + typeRender(t) + "——引用与可变一样不可预测（根文档 :142）。" +
            "要固定值？先把值拷贝到不可变量：var c : T = " + nm + "（前端类型层深拷判定，实际拷贝随后端运行时）")
    }

    private fun containsView(t: sublimation.frontend.hir.HirType): Boolean = when (t) {
        is HirRefType, is HirMutType -> true
        else -> false
    }
}
