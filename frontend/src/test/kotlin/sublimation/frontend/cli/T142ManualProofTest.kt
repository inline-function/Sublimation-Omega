package sublimation.frontend.cli

import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t142（手动证明通道：by 证据链）回归门——by 断言的**供给链**修通。
 *
 * 形态：函数契约的上文角组（preps，`fun consumeP()<p>: Null`）在调用点须供给；
 * by 断言（`consumeP() by [p]`）= 手动供给通道——每条契约位须有单条证据证出
 * （复用 prover.proves 判据）；全部到位 ⇒ 证据注入 Γ（既有事实槽 injectFact，
 * 下文弥散 Diffusion 推迟项不动的手动形态）；任一契约位无证据 ⇒ **E-CANNOT-PROVE
 * 精确报**（不放宽为通过）。无契约目标（callee 无 preps / 非调用）零变——
 * byAsserts 旧口径（近证据从 Γ 核对）保留。 */
class T142ManualProofTest {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun scriptStd(src: String, scriptMode: Boolean = false): FrontendOutput {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode, noDegrade = false, noLift = false)
        return compileFrontendImpl(src, "t142.subl", scriptMode, false, false,
            su.associate { it.alias to it.snapshot }, sd)
    }

    /** 样本骨架（同 e2e examples/手动证明.subl 的契约形态，精简到证明面）。 */
    private val skeleton = """fun produceP(): Null <p> {
    axiom p
}
fun consumeP()<p>: Null {}
@impure fun main(): Null {
    produceP()
    consumeP() by [p]
    unchecked print("by")
}"""

    // -------------------- 门 1：正例（冻红转绿） --------------------

    /** by 证据逐条供给契约位 ⇒ 绿（ok=true，零 ERROR）——冻红样本同形态转绿。 */
    @Test fun by供给契约位转绿() {
        val o = scriptStd(skeleton)
        assertEquals(emptyList<String>(), errs(o), "by 证据供给契约位须绿: " + errs(o))
    }

    /** e2e 冻红样本本体（examples/手动证明.subl）转绿——ok=true、零 E- 诊断。 */
    @Test fun 冻红样本转绿() {
        val src = javaClass.getResourceAsStream("/e2e/examples/手动证明.subl")
            ?.bufferedReader()?.use { it.readText() } ?: ""
        assertFalse(src.isEmpty(), "e2e 样本资源可读")
        val o = scriptStd(src)
        assertEquals(emptyList<String>(), errs(o), "手动证明样本转绿（ok=true）: " + errs(o))
    }

    // -------------------- 门 2：负例先红（证据不足精确拒） --------------------

    /** by 证据不含契约位所需命题 ⇒ E-CANNOT-PROVE 精确报（不准放宽为通过）。 */
    @Test fun by证据不足精确拒() {
        val o = scriptStd("""fun consumeP()<p>: Null {}
@impure fun main(): Null {
    consumeP() by [q]
    unchecked print(1)
}""")
        val codes = errs(o)
        assertTrue(codes.contains("E-CANNOT-PROVE"), "证据不足须落 E-CANNOT-PROVE（精确拒）: " + codes)
        assertFalse(codes.isEmpty(), "不准放宽为通过")
    }

    /** 契约位两条只供一条 ⇒ 仍红（逐条覆盖判据）。 */
    @Test fun 契约多位只供其一仍红() {
        val o = scriptStd("""fun consumeP()<p, r>: Null {}
@impure fun main(): Null {
    consumeP() by [p]
    unchecked print(1)
}""")
        assertTrue(errs(o).contains("E-CANNOT-PROVE"), "多位契约只供其一 ⇒ 仍红: " + errs(o))
    }

    // -------------------- 门 3：供给生效（Γ 注入，后续可用） --------------------

    /** 供给成功的证据注入 Γ——后续同契约会话直接成立（第二处 by 无需再证）。 */
    @Test fun 供给注入Γ后续可用() {
        val o = scriptStd("""fun consumeP()<p>: Null {}
@impure fun main(): Null {
    consumeP() by [p]
    consumeP() by [p]
    unchecked print(1)
}""")
        assertEquals(emptyList<String>(), errs(o), "供给注入 Γ 后第二处 by 复用: " + errs(o))
    }

    // -------------------- 门 4：无契约目标零变（byAsserts 旧口径保留） --------------------

    /** 无 preps 的调用 + by 断言——旧口径（近证据从 Γ 核对）保留：Γ 无 q ⇒ 红。 */
    @Test fun 无契约by旧口径仍红() {
        val o = scriptStd("""fun go(): Null {}
@impure fun main(): Null {
    go() by [q]
    unchecked print(1)
}""")
        assertTrue(errs(o).contains("E-CANNOT-PROVE"), "无契约 by 从 Γ 核对未变（q ∉ Γ 红）: " + errs(o))
    }

    /** 无 preps 调用 + Γ 已有事实的 by——旧口径绿（断言核对通道零回归）。 */
    @Test fun 无契约byΓ有证仍绿() {
        val o = scriptStd("""@impure fun main(): Null {
    axiom base
    unchecked print(1) by [base]
}""")
        assertEquals(emptyList<String>(), errs(o), "Γ 有证的非契约 by 绿: " + errs(o))
    }

    // -------------------- 门 5：既有 ∵∴/自动证明面零回归 --------------------

    /** ∵∴ 链 + by 共存：MP 闭包与 by 供给链独立（自动证明机制零动）。 */
    @Test fun 自动证明面零回归() {
        val o = scriptStd("""@impure fun main(): Null {
    axiom offer
    axiom offer → granted
    ∵ offer ∴ granted
    unchecked print(1) by [granted]
}""")
        assertEquals(emptyList<String>(), errs(o), "∵∴ 闭包 + by 断言共存绿: " + errs(o))
    }
}
