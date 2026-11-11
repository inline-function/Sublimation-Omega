package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t96（N1 纯度闸 · 阶段 A 报告态，R2 §一 / wiki 04-效应）：pure<f> 命题 + 调用闸 + 三路放行 + 借用形参。
 *
 * - 闸触发落 **error 级**（E-IMPURE-CALL;阶段 A warn 报告态已验毕，队长 2026-11-10 批阶段 B 翻硬拒）。
 * - 全量/e2e/JSON 口径同收档报告（acceptance #4/#5)。
 * - 放行三路 + 借用形参：@impure 调用方（效应沿调用链向上传播）/ unchecked / Γ⊢pure<f>
 *   （上文角组 <pure<f>> 与 axiom pure<f> 供给）/ @impure 函数类型位形参（借用效应）。
 * - lambda 内的不纯调用经 t94 推导被闭包吸收（不外溢）；impure 闭包被**直接调用**才计（函数值闸最小形态）。
 */
class T96PurityGateTest {

    /** 落 E-IMPURE-CALL 诊断的条数（severity 不限——阶段 A=warn）。 */
    private fun gateFires(src: String, file: String = "t96.subl", scriptMode: Boolean = false): Int {
        // D-12(t111):mode 真值单源——compile 默认 project（project 严判:函数内调 impure 必闸）;
        // 仅含顶层语句的脚本宽待用例显式传 scriptMode = true（topPure = false → 脚本顶层不闸）。
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes, scriptMode)
        return pr.diag.count { it.code == "E-IMPURE-CALL" } +
            diag.diags.count { it.code == "E-IMPURE-CALL" }
    }

    private fun errCodes(src: String, file: String = "t96.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.filter { it.severity == Severity.ERROR }.map { it.code } +
            diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    }

    /** 验收 #2：纯函数体内调 @impure ⇒ 闸触发（阶段 A = warn，不阻断）。 */
    @Test fun 纯函数体调impure声明() {
        val src = "@impure fun p(a: Nat): Nat = a\nfun q(): Null { p(1) }"
        assertEquals(1, gateFires(src), "E-IMPURE-CALL 须落 1 条")
        assertEquals(listOf("E-IMPURE-CALL"), errCodes(src), "阶段 B:error 硬拒")
    }

    /** 纯函数体调内建不纯名（print）⇒ 闸触发。 */
    @Test fun 纯函数体调内建不纯() {
        assertEquals(1, gateFires("fun q(): Null { print(\"x\") }"))
    }

    /** 验收 #3: Γ 持有 pure<f> 时即便 @impure 也放行——上文角组 <pure<side>>（特性巡礼同款形态）。 */
    @Test fun 上文角组供给pure释放() {
        val src = "@impure fun side(): Null {}\nfun pureCaller()<pure<side>>: Null { side() }"
        assertEquals(0, gateFires(src), "Γ⊢pure<side> ⇒ 放行")
        assertTrue(errCodes(src).none { it.startsWith("E") }, "上文角组合法：")
    }

    /** Γ 供给第二条通道：axiom pure<f>（wiki 的命题层免验证供给）。 */
    @Test fun axiom供给pure释放() {
        val src = "axiom pure<w>\n@impure fun w(): Null {}\nfun q(): Null { w() }"
        assertEquals(0, gateFires(src), "axiom pure<w> ⇒ 放行")
    }

    /** 三路放行：unchecked 单语句豁免。 */
    @Test fun unchecked豁免() {
        val src = "@impure fun p(a: Nat): Nat = a\nfun q(): Null { unchecked p(1) }"
        assertEquals(0, gateFires(src), "unchecked ⇒ 豁免")
    }

    /** 三路放行：@impure 调用方体内放行（上文转嫁——效应沿调用链向上传播）。 */
    @Test fun impure调用方放行() {
        val src = "@impure fun p(a: Nat): Nat = a\n@impure fun r(): Null { p(1) }"
        assertEquals(0, gateFires(src), "调用方非纯 ⇒ 放行")
    }

    /** 四步升级之 4:借用形参——@impure 函数类型位形参即显式效应能力，纯函数可调。 */
    @Test fun 借用形参放行() {
        val src = "fun k(f: @impure () => Null): Null { f() }"
        assertEquals(0, gateFires(src), "形参类型位 @impure ⇒ 借用豁免")
        assertTrue(errCodes(src).none { it.startsWith("E") }, "借用形参合法：")
    }

    /** lambda 内层不纯调用经 t94 被闭包吸收（不外溢）;impure 闭包**直接调用**才计（函数值闸最小形态）。 */
    @Test fun lambda吸收与直接调用() {
        assertEquals(0, gateFires("fun q(): Null { var g = { => print(\"x\") } }"), "impure 闭包字面量：吸收不溢出")
        assertEquals(1, gateFires("fun q(): Null { ({ => print(\"x\") })() }"), "impure 闭包被直接调用 ⇒ 闸")
        assertEquals(0, gateFires("fun q(): Null { var g = { => 1 + 1 } }"), "纯闭包无效应")
    }

    /** 脚本模式顶层 = 隐式非纯入口（wiki topPure = !scriptMode)——顶层直接调 impure 不闸。 */
    @Test fun 脚本顶层非纯入口() {
        // D-12(t111):顶层 p(1) 的脚本宽待须 scriptMode = true（真值单源:compile 默认 project 会闸）
        assertEquals(0, gateFires("@impure fun p(a: Nat): Nat = a\np(1)", scriptMode = true))
    }

    /** 阶段 A 闸级别为 warn（非 error)——阶段 B 单点翻 error 后此用例同步改。 */
    @Test fun 阶段B为error级() {
        val pr = parseScriptSourceWithNotes("@impure fun p(a: Nat): Nat = a\nfun q(): Null { p(1) }", "t96.subl")
        val (_, diag) = compile(pr.file, pr.notes)
        val g = diag.diags.firstOrNull { it.code == "E-IMPURE-CALL" }
        assertEquals(Severity.ERROR, g?.severity, "阶段 B:error 硬拒")
    }
}
