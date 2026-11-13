package sublimation.frontend.cli

import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t148（R-26.1 收口 / t144 验收退件修）：调用位 <> 路由回既有命题参数供给通路。
 *
 * t144 验收退件（队长查 t122n 冻锚）：witness<谓词>() 在 t122n 时已绿——callee 声明 theory 含
 * 命题参数（PropParam → HIR HirPropParam，t22 B6 机制），调用位 <> 是命题供给的正确形态。
 * 本修只改 <> 侧路由：bracket=false 时 callee 有命题理论参数 ⇒ t122n 既有通路供给（复绿）；
 * 无命题参数才保留 E-CALL-ANGLE（[] 通路不动——t144 已验收）。 */
class T148PropSupplyRouteTest {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun scriptStd(src: String, scriptMode: Boolean = false): FrontendOutput {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode, noDegrade = false, noLift = false)
        return compileFrontendImpl(src, "t148.subl", scriptMode, false, false,
            su.associate { it.alias to it.snapshot }, sd)
    }

    // -------------------- 门 1：复绿（既有命题供给通路） --------------------

    /** 调用位 <> 供给 callee 命题理论参数（witness[@q] 声明形态，上下文弥散:24/:37 骨架）⇒ 复绿。 */
    @Test fun 调用位尖括号命题供给复绿() {
        val o = scriptStd("""fun witness[@q]<q>: Null {}
@impure fun main(): Null {
    witness<tagged<7>>()
    unchecked print(1)
}""")
        assertEquals(emptyList<String>(), errs(o), "调用位 <> 命题供给（既有 t122n 通路）须复绿: " + errs(o))
    }

    /** e2e 样本本体（上下文弥散.subl—t144 误红件）复绿——零 E-。 */
    @Test fun 上下文弥散样本复绿() {
        val src = javaClass.getResourceAsStream("/e2e/examples/上下文弥散.subl")
            ?.bufferedReader()?.use { it.readText() } ?: ""
        assertFalse(src.isEmpty(), "样本可读")
        val o = scriptStd(src)
        assertEquals(emptyList<String>(), errs(o), "上下文弥散.sample 复绿（t122n 口径恢复）: " + errs(o))
    }

    /** 供给后 Γ 弥散复用（needTagged 契约命中供给事实——样本同族链路）。 */
    @Test fun 命题供给后契约弥散绿() {
        val o = scriptStd("""axiom forall n. tagged<n>
fun witness[@q]<q>: Null {}
fun needTagged()<tagged<7>>: Null {}
@impure fun main(): Null {
    witness<tagged<7>>()
    needTagged()
    unchecked print(1)
}""")
        assertEquals(emptyList<String>(), errs(o), "命题供给通路须与样本全链一致绿: " + errs(o))
    }

    // -------------------- 门 2：E-CALL-ANGLE 保留面（无命题参数 callee） --------------------

    /** callee 无命题理论参数（纯类型参数 [T]）⇒ E-CALL-ANGLE 保留（迁移指引精确）。 */
    @Test fun 无命题参数调用位尖括号仍拒() {
        val o = scriptStd("""fun id[T](x: T): T = x
@impure fun main(): Null {
    var a = id<Nat>(1)
    unchecked print(a)
}""")
        val codes = errs(o)
        assertTrue(codes.contains("E-CALL-ANGLE"), "无命题参数 callee 仍须落 E-CALL-ANGLE: " + codes)
        assertFalse(codes.isEmpty(), "不准放宽为通过")
    }

    /** 构造位 callee（prelude 虚拟构造，无用户命题参数）⇒ E-CALL-ANGLE 保留。 */
    @Test fun 构造位尖括号仍拒() {
        val o = scriptStd("""@impure fun m(): Null {
    var ch = Channel<Nat>(2)
    unchecked print(ch)
}""")
        val codes = errs(o)
        assertTrue(codes.contains("E-CALL-ANGLE"), "构造位 <> 仍须落 E-CALL-ANGLE: " + codes)
        assertFalse(codes.isEmpty(), "不准放宽为通过")
    }

    // -------------------- 门 3：[] 通路不动（t144 已验收） --------------------

    /** 调用位方括号类型实参绿（通路与形态零回归）。 */
    @Test fun 方括号通路仍绿() {
        val o = scriptStd("""fun id[T](x: T): T = x
@impure fun main(): Null {
    var a = id[Nat](1)
    unchecked print(a)
}""")
        assertEquals(emptyList<String>(), errs(o), "[] 类型实参通路须零回归绿: " + errs(o))
    }

    /** 命题面 <>（preps 声明槽 + by 断言）零回归。 */
    @Test fun 命题面角表零回归绿() {
        val o = scriptStd("""fun produceP(): Null <p> {
    axiom p
}
fun consumeP()<p>: Null {}
@impure fun main(): Null {
    produceP()
    consumeP() by [p]
    unchecked print("by")
}""")
        assertEquals(emptyList<String>(), errs(o), "命题面 <> 须零回归绿: " + errs(o))
    }
}
