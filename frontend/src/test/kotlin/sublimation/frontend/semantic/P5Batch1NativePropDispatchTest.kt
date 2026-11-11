package sublimation.frontend.semantic

import sublimation.frontend.ast.CallExpr
import sublimation.frontend.ast.Expr
import sublimation.frontend.ast.InstExpr
import sublimation.frontend.ast.NameRef
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirPAnd
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirPBot
import sublimation.frontend.hir.HirPIff
import sublimation.frontend.hir.HirPImp
import sublimation.frontend.hir.HirProp
import sublimation.frontend.hir.HirPNot
import sublimation.frontend.hir.HirPOr
import sublimation.frontend.hir.HirPTop
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * P5 批次 1（t73）：命题位 CallExpr/InstExpr 的表驱动分派——**先修路再通车**。
 *
 * 背景（qa t72 测绘，队长核实）：`∧ ∨ → ↔` 目前仍是词法单例 token，本批次**不变词法**，
 * 故这些 CallExpr 路径对源代码**当前不可达**；P5 名字化后 `p ∧ q` 经 D-5 标识符中缀糖落
 * `CallExpr("∧", [p, q])`——若归一层默认产 HirPAtom 则**静默降级**（不报错、产物错、混进事实集 Γ）。
 * 本测试**直接构造 AST** 调 prop()（S-2 语义层内部入口）验证分派已就位——"没有红的红"必须先拦在产物层。
 *
 * 表驱动（验收 #5）：NativeProps.propCtors 是分派的单一来源——新增注册条目只需加一行，分派代码
 * 不动，`注册项_全项一致分派` 自动覆盖新行。
 *
 * `¬` 的命题应用保持 P4 终态（CallExpr 命题位 E-PROP-FORM、InstExpr nativeNotProp，ADR-48 §一），
 * 不进分派表——t73 任务书把 ¬ 列入分派表与此冲突，已按 t70 裁决保留、上报队长。
 */
class P5Batch1NativePropDispatchTest {

    /** 最小装配：空符号表 + 空诊断袋 + 全否类型参数判定（分派本身不依赖任何符号）。 */
    private fun normalizer(diag: DiagBag = DiagBag()): PropNormalizer {
        val nids = NodeIdSource()
        val symbols = SymbolTable(nids::alloc)
        return PropNormalizer(nids::alloc, NameResolver(symbols, diag), diag, { false }, { null }, { t -> t.toString() })
    }

    private fun ref(n: String) = NameRef(n, SYNTHETIC_POS)
    private fun call(name: String, args: List<Expr>) = CallExpr(ref(name), args, pos = SYNTHETIC_POS)
    private fun inst(name: String, args: List<Expr>) = InstExpr(ref(name), args, SYNTHETIC_POS)

    private fun two() = listOf(ref("p"), ref("q"))

    /** #3（唯一实质断言）：`CallExpr("∧", [p, q])` → HirPAnd——而非 HirPAtom("∧")。 */
    @Test fun callExpr_与_分派HirPAnd() {
        assertIs<HirPAnd>(normalizer().prop(call("∧", two())))
    }

    @Test fun callExpr_或_分派HirPOr() { assertIs<HirPOr>(normalizer().prop(call("∨", two()))) }

    @Test fun callExpr_蕴含_分派HirPImp() { assertIs<HirPImp>(normalizer().prop(call("→", two()))) }

    @Test fun callExpr_等价_分派HirPIff() { assertIs<HirPIff>(normalizer().prop(call("↔", two()))) }

    @Test fun callExpr_恒真_分派HirPTop() { assertIs<HirPTop>(normalizer().prop(call("⊤", emptyList()))) }

    @Test fun callExpr_恒假_分派HirPBot() { assertIs<HirPBot>(normalizer().prop(call("⊥", emptyList()))) }

    /** 角表通道（`∧<p, q>` 形态）同分派。 */
    @Test fun instExpr_与_分派HirPAnd() { assertIs<HirPAnd>(normalizer().prop(inst("∧", two()))) }

    /** #4：元数错 ⇒ E-ARITY + 半成品 PBot 占位。 */
    @Test fun callExpr_元数错_落E_ARITY() {
        val diag = DiagBag()
        assertIs<HirPBot>(normalizer(diag).prop(call("∧", listOf(ref("p")))))
        val err = diag.diags.first { it.code == "E-ARITY" }
        assertTrue(err.message.contains("2 元"), "元数说明缺失：${err.message}")
        assertTrue(err.severity == Severity.ERROR)
    }

    /** #5 表驱动：分派的单一来源是 NativeProps.propCtors——本循环覆盖注册表**每一项**
     *  （新增第 N 项注册条目只需 propCtors 加一行，分派代码与分派测试均不动）。 */
    @Test fun 注册项_全项一致分派() {
        assertTrue(NativeProps.propCtors.keys.all { it in NativeProps.arity }, "分派表应是 §5 保护注册表的子集")
        for ((name, entry) in NativeProps.propCtors) {
            val (arity, _) = entry
            val ok = normalizer().prop(call(name, (1..arity).map { ref("v$it") }))
            assertIs<HirProp>(ok, "$name：$arity 元应成功分派")
            val diag = DiagBag()
            val bad = normalizer(diag).prop(call(name, (0..arity).map { ref("v$it") }))
            assertIs<HirPBot>(bad, "$name：元数错应以 PBot 占位")
            assertTrue(diag.diags.any { it.code == "E-ARITY" && it.severity == Severity.ERROR },
                "$name：元数错应报 E-ARITY")
        }
    }

    /** 注册表外的名字保持原默认：HirPAtom（本轮行为零变化）。 */
    @Test fun 非注册名_默认原子() {
        assertIs<HirPAtom>(normalizer().prop(call("foo", two())))
    }

    /** `¬` 的命题应用保持 P4 终态（ADR-48 §一）：CallExpr 命题位落 E-PROP-FORM，不被新分派改变。 */
    @Test fun not_CallExpr_保持E_PROP_FORM() {
        val diag = DiagBag()
        assertIs<HirPBot>(normalizer(diag).prop(call("¬", listOf(ref("p")))))
        val err = diag.diags.first { it.code == "E-PROP-FORM" }
        assertTrue(err.message.contains("¬<p>"), "应提示 ¬<p>：${err.message}")
    }

    @Test fun not_InstExpr_保持HirPNot() {
        assertIs<HirPNot>(normalizer().prop(inst("¬", listOf(ref("p")))))
    }

    /** 值位 `¬true`（名字）的提示必须是值位正确的 `!True`（§八：推荐的写法须本身正确；t84 后小写字面量不合法）。 */
    @Test fun 值位未定义否定_提示用感叹号() {
        val diags = allDiags("var b = ¬true")
        assertTrue(diags.any { it.startsWith("E-UNBOUND-NAME") && it.contains("!True") },
            "值位否定提示应是 !True：$diags")
    }

    private fun allDiags(src: String, file: String = "t73.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.map { it.code + " :: " + it.message } + diag.diags.map { it.code + " :: " + it.message }
    }
}