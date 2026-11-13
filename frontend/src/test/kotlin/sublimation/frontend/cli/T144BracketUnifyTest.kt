package sublimation.frontend.cli

import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t144（R-26.1 括号语义统一）回归门——[] = 类型参数（声明+应用统一），<> = 命题参数。
 *
 * 调用位类型实参从 <> 迁到 []：f[T](args)（Parser 后缀方括号通道 → InstExpr 标记
 * bracket=true → 语义层 B8 角表类型域读取，既有 explicitTps 通路零改）。调用位 <>
 * 划归命题参数通道——调用侧命题供给未通则落 **E-CALL-ANGLE 精确 E 级**（指向 by 断言/
 * 方括号迁移），不准静默；命题面（preps/posts 声明槽、p<v> 谓词应用、¬<p>、by 断言）
 * <> 形态零变。声明位 [T] / 类型位 Channel[Nat] 零动。 */
class T144BracketUnifyTest {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun scriptStd(src: String, scriptMode: Boolean = false): FrontendOutput {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode, noDegrade = false, noLift = false)
        return compileFrontendImpl(src, "t144.subl", scriptMode, false, false,
            su.associate { it.alias to it.snapshot }, sd)
    }

    // -------------------- 门 1：正例（调用位方括号类型实参） --------------------

    /** 泛型函数调用位 id[Nat](1) 绿——显式类型实参经方括号通道（explicitTps 不变）。 */
    @Test fun 方括号调用类型实参绿() {
        val o = scriptStd("""fun id[T](x: T): T = x
@impure fun main(): Null {
    var a = id[Nat](1)
    unchecked print(a)
}""")
        assertEquals(emptyList<String>(), errs(o), "调用位方括号类型实参须绿: " + errs(o))
    }

    /** 调用位方括号多类型实参绿（f[A, B](args)）。 */
    @Test fun 方括号多类型实参绿() {
        val o = scriptStd("""fun pair[A, B](x: A, y: B): A = x
@impure fun main(): Null {
    var a = pair[Nat, Str](1, "a")
    unchecked print(a)
}""")
        assertEquals(emptyList<String>(), errs(o), "调用位多类型实参须绿: " + errs(o))
    }

    /** 通道构造 Channel[Nat](2) 绿——类型位 Channel[Nat]（零动）+ 调用位 Channel[Nat](2)。 */
    @Test fun 通道方括号构造绿() {
        val o = scriptStd("""@impure fun m(): Null {
    var ch: Channel[Nat] = Channel[Nat](2)
    unchecked print(ch)
}""")
        assertEquals(emptyList<String>(), errs(o), "Channel 方括号构造须绿（类型位零动）: " + errs(o))
    }

    // -------------------- 门 2：负例先红（调用位 <> 精确 E 级） --------------------

    /** 调用位尖括号带类型实参 ⇒ E-CALL-ANGLE 精确报（指向方括号迁移 + by 断言），不准静默。 */
    @Test fun 调用位尖括号类型实参精确拒() {
        val o = scriptStd("""fun id[T](x: T): T = x
@impure fun main(): Null {
    var a = id<Nat>(1)
    unchecked print(a)
}""")
        val codes = errs(o)
        assertTrue(codes.contains("E-CALL-ANGLE"), "调用位 <> 须落 E-CALL-ANGLE（精确 E 级）: " + codes)
        assertFalse(codes.isEmpty(), "不准放宽为通过（调用位 <> 命题通道未通）")
        val msg = o.diag.diags.first { it.code == "E-CALL-ANGLE" }.message
        assertTrue(msg.contains("by 断言"), "诊断须指向 by 断言通道: " + msg)
        assertTrue(msg.contains("["), "诊断须指向方括号迁移: " + msg)
    }

    /** 调用位尖括号带非命题实参（构造位）⇒ E-CALL-ANGLE 精确报。 */
    @Test fun 调用位尖括号非命题实参精确拒() {
        val o = scriptStd("""@impure fun m(): Null {
    var ch = Channel<Nat>(2)
    unchecked print(ch)
}""")
        val codes = errs(o)
        assertTrue(codes.contains("E-CALL-ANGLE"), "构造位 <> 须落 E-CALL-ANGLE: " + codes)
        assertFalse(codes.isEmpty(), "不准放宽为通过")
    }

    /** 调用位尖括号命题供给形态（嵌套角表 witness<谓词>()）⇒ E-CALL-ANGLE 精确报——e2e 上下文弥散样本同形态。 */
    @Test fun 调用位尖括号命题供给形态精确拒() {
        val o = scriptStd("""fun witness<p>(): Null {}
@impure fun m(): Null {
    witness<tagged<7>>()
    unchecked print(1)
}""")
        val codes = errs(o)
        assertTrue(codes.contains("E-CALL-ANGLE"), "命题供给形态 <> 须落 E-CALL-ANGLE（指向 by 断言）: " + codes)
        assertFalse(codes.isEmpty(), "不准放宽为通过（调用侧命题供给未通）")
    }

    // -------------------- 门 3：迁移样本（4 处调用位 <> 全部迁 []） --------------------

    private fun e2e(name: String): String =
        javaClass.getResourceAsStream("/e2e/examples/" + name)
            ?.bufferedReader()?.use { it.readText() } ?: ""

    /** 日常-异步任务.subl（:8 Channel<Nat>(4) 已迁 Channel[Nat](4)）绿。 */
    @Test fun 迁移样本日常异步任务绿() {
        val src = e2e("日常-异步任务.subl")
        assertFalse(src.isEmpty(), "样本可读")
        val o = scriptStd(src)
        assertEquals(emptyList<String>(), errs(o), "迁移样本须绿: " + errs(o))
    }

    /** 异步与并发.subl（:15 Channel<Nat>(1) 已迁）绿。 */
    @Test fun 迁移样本异步与并发绿() {
        val src = e2e("异步与并发.subl")
        assertFalse(src.isEmpty(), "样本可读")
        val o = scriptStd(src)
        assertEquals(emptyList<String>(), errs(o), "迁移样本须绿: " + errs(o))
    }

    /** 日常-订单状态机.subl（:87/92 Cons<OrderEvent>( 已迁 Cons[OrderEvent](）绿。 */
    @Test fun 迁移样本日常订单状态机绿() {
        val src = e2e("日常-订单状态机.subl")
        assertFalse(src.isEmpty(), "样本可读")
        val o = scriptStd(src)
        assertEquals(emptyList<String>(), errs(o), "迁移样本须绿: " + errs(o))
    }

    // -------------------- 门 4：类型位/命题面零回归 --------------------

    /** 声明位 [T] 与类型位 Channel[Nat] 零动（既有形态绿）。 */
    @Test fun 声明位类型位零动绿() {
        val o = scriptStd("""fun id[T](x: T): T = x
@impure fun main(): Null {
    var a: Nat = id[Nat](1)
    unchecked print(a)
}""")
        assertEquals(emptyList<String>(), errs(o), "声明位 [T] / 类型位形态须零动绿: " + errs(o))
    }

    /** 命题面 <> 零回归：preps 声明槽 + by 断言 + 谓词角表形态绿。 */
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
        assertEquals(emptyList<String>(), errs(o), "命题面 <>（preps/by 断言）须零回归绿: " + errs(o))
    }
}
