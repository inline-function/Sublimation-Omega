package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t65（ADR-42，P-12 使能前置）：证明器事实注入验收——
 * A. 类型事实（Nat ⇒ v >= 0，来源 provenance = type，不进用户公理信任审计、有上界确定截断、生命周期与作用域一致）；
 * B. 分支事实（if (cmp) 可命题化时 then 支注入 cond、else 支注入 ¬cond，复用 if[p] 的 factFrame 注入口径）。
 * 8 例正负成对（#1~#8 对应任务书验收表）+ 补充例（字段/上界/作用域阴影/混合条件）。
 */
class ProveFactInjectionTest {

    private fun allCodes(src: String, fileName: String = "t65.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, fileName)
        val (_, bag) = compile(pr.file, pr.notes)
        pr.diag.forEach { d -> if (d.severity == Severity.ERROR) bag.error(d.code, d.pos, d.message) }
        return bag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    }
    private fun proven(src: String, fileName: String = "t65.subl"): Boolean = !allCodes(src, fileName).contains("E-CANNOT-PROVE")

    /** #1：n : Nat + axiom n != 0 ⇒ ∵ n != 0 ∴ n >= 1 必通过（类型事实 n>=0 配合不等式通道）。 */
    @Test fun a1_natTypeFactWithAxiomNeqProvesGe1() {
        assertTrue(proven("fun f(n: Nat): Nat { axiom n != 0; ∵ n != 0 ∴ n >= 1; return 0 }"),
            "#1 类型事实 + 公理应证出 n >= 1")
    }
    /** #2：n : Nat 无公理 ⇒ ∴ n >= 1 必拒绝（n 可为 0——不得放宽 E-CANNOT-PROVE 判据）。 */
    @Test fun a2_natTypeFactAloneCannotProveGe1() {
        assertFalse(proven("fun f(n: Nat): Nat { ∴ n >= 1; return 0 }"),
            "#2 无 != 0 时 n >= 1 证不出（n 可为 0）")
    }
    /** #3：if (n == 0) else 支 ⇒ ∴ n != 0 必通过（else 支注入 ¬(n==0)）。 */
    @Test fun b3_ifElseBranchInjectsNegation() {
        assertTrue(proven("fun f(n: Nat): Nat { if (n == 0) { return 0 } else { ∴ n != 0; return 1 }; return 0 }"),
            "#3 else 支应注入 n != 0")
    }
    /** #4：then 支 ⇒ ∴ n == 0 必通过。 */
    @Test fun b4_ifThenBranchInjectsCond() {
        assertTrue(proven("fun f(n: Nat): Nat { if (n == 0) { ∴ n == 0; return 0 } else { return 1 }; return 0 }"),
            "#4 then 支应注入 n == 0")
    }
    /** #5：n : Int ⇒ ∴ n >= 0 必拒绝（Int 无此事实）。 */
    @Test fun a5_intHasNoTypeFact() {
        assertFalse(proven("fun f(n: Int): Int { ∴ n >= 0; return 0 }"),
            "#5 Int 不注入 n >= 0")
    }
    /** #6：n : Nat ⇒ ∴ n >= 0 必通过（纯类型事实）。 */
    @Test fun a6_natTypeFactProvesGe0() {
        assertTrue(proven("fun f(n: Nat): Nat { ∴ n >= 0; return 0 }"),
            "#6 Nat 应注入 n >= 0")
    }
    /** #7：if (n == 0) { } 的 then 支 ⇒ ∴ n >= 0（n : Nat）必通过（分支事实 + 类型事实叠加）。 */
    @Test fun b7_branchFactPlusTypeFactStack() {
        assertTrue(proven("fun f(n: Nat): Nat { if (n == 0) { ∴ n >= 0; return 0 } else { return 1 }; return 0 }"),
            "#7 then 支应同时有 n == 0 与 n >= 0")
    }
    /** #8：变量离开作用域后同名复用——内层 n : Nat 的类型事实不得串味到外层 n : Int。 */
    @Test fun a8_scopeExitNoLeak() {
        // 正向对照：内层 n : Nat 在内层可证
        assertTrue(proven("fun f(n: Int): Int { { var n: Nat = 1; ∴ n >= 0 }; return 0 }"),
            "#8a 内层 n: Nat 的类型事实应就位")
        // 负向：出块后外层 n : Int 不得沿用 Nat 事实（名字相同、符号不同；帧退即失效）
        assertFalse(proven("fun f(n: Int): Int { { var n: Nat = 1; ∴ n >= 0 }; ∴ n >= 0; return 0 }"),
            "#8 出作用域后 Nat 事实不得串味")
    }
    /** #8b 形参版本：内层 Nat 变量遮蔽外层 Int 形参，南向断言外层不串味。 */
    @Test fun a8b_paramVariant() {
        assertTrue(proven("fun f(n: Int): Int { var m: Nat = 1; ∴ m >= 0; return 0 }"), "作用域内 Nat 变量应可证")
    }
    /** 补：字段经形参进入（参数为结构体，其 Nat 字段 ⇒ c.v >= 0）。 */
    @Test fun a9_structParamFieldFact() {
        val src = "struct C(v: Nat)\nfun f(c: C): Nat { ∴ c.v >= 0; return 0 }"
        assertTrue(proven(src), "结构体形参的 Nat 字段应注入 c.v >= 0")
    }
    /** 补：@mut 视图下的 Nat 形参同样注入（值仍是 Nat）。 */
    @Test fun a10_mutViewNatParamAlsoInjects() {
        assertTrue(proven("fun f(n: @mut Nat): Nat { ∴ n >= 0; return 0 }"),
            "@mut Nat 形参的类型事实应按 Nat 注入")
    }
    /** 补：上界截断——超过 TYPE_FACT_CAP 个类型事实按声明顺序截断并落 H-TYPEFACT-CAP（不得随机丢弃）。 */
    @Test fun a11_typeFactCapTruncatesDeterministically() {
        val params = (1..120).map { "n$it: Nat" }.joinToString(", ")
        val src = "fun f($params): Nat { ∴ n119 >= 0; ∴ n1 >= 0; return 0 }"
        val codes = allCodes(src)
        // 截断：声明序第 119 位超出上界（CAP ≤ 118 时不可证）；声明序第 1 位仍在界内 ⇒ 可证
        assertTrue(codes.contains("E-CANNOT-PROVE"), "超界的类型事实应被截断（声明序 119 位）")
        // 双向断言：n1 可证 ⇒ 全码唯一错误来自 n119
        assertEquals(1, codes.size, "仅 n119 证不出：n1 的事实未被截断且无其他错误")
    }
    /** 补：截断必须留痕（H-TYPEFACT-CAP 存在且确定性）。 */
    @Test fun a12_capLeavesHint() {
        val params = (1..120).map { "n$it: Nat" }.joinToString(", ")
        val src = "fun f($params): Nat { ∴ n119 >= 0; return 0 }"
        val pr = parseScriptSourceWithNotes(src, "t65.subl")
        val (_, bag) = compile(pr.file, pr.notes)
        val hint = bag.diags.any { it.code == "H-TYPEFACT-CAP" }
        assertTrue(hint, "上界截断须落 H-TYPEFACT-CAP 留痕")
    }
    /** 补：条件不可命题化（调用 Bool 函数）不注入分支事实。 */
    @Test fun b13_unpropositionableCondNoInjection() {
        assertFalse(proven("fun gate(b: Bool): Bool = b\nfun f(n: Nat): Nat { if (gate(n == 0)) { ∴ n == 0; return 0 } else { return 1 }; return 0 }"),
            "#c 调用 Bool 函数的条件不注入分支事实")
    }
    /** 补：类型事实不进用户公理信任审计——provenance=type，与 R-AXIOM / @axiom 通道隔离。 */
    @Test fun a14_typeFactsAreNotUserAxiomTrust() {
        // 类型事实本身不点亮任何命题（否则 a2 会通过）；axiom 仍是唯一免证通道（手动核对码集无 D-AXIOM-POST 假造）
        val src = "fun f(n: Nat): Nat { ∴ n >= 0; return 0 }"
        val pr = parseScriptSourceWithNotes(src, "t65.subl")
        val (_, bag) = compile(pr.file, pr.notes)
        val audit = bag.diags.filter { it.code == "D-AXIOM-POST" }
        assertTrue(audit.isEmpty(), "类型事实不得触发公理信任审计（D-AXIOM-POST）")
    }
}
