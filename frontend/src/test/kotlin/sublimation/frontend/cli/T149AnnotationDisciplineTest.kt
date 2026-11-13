package sublimation.frontend.cli

import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t149（R-26.2 注解纪律）：未知注解 E-UNKNOWN-ANN + 注解错位 E-ANN-POSITION（合法位注册表）。
 *
 * 旧状：未知注解静默忽略（anns 收集后无校验，语义层只按名消费）。本件落注册表：
 * 值通道（声明/成员/语句/var/形参）逐注解校验——未知名 ⇒ E-UNKNOWN-ANN「无此注解」；
 * 已知名但通道错 ⇒ E-ANN-POSITION（报该注解合法位）。类型位既有 E 级诊断承接升级
 * （parseType 的 errType 精确报改挂同族码）。@ref 形参名前既有 E 口径逐字承接（t95）。 */
class T149AnnotationDisciplineTest {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun errMessages(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.message }

    private fun scriptStd(src: String): FrontendOutput {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        return compileFrontendImpl(src, "t149.subl", false, false, false,
            su.associate { it.alias to it.snapshot }, sd)
    }

    // -------------------- 门 1：未知注解 ⇒ E-UNKNOWN-ANN（静默放行旧状先红）--------------------

    /** 函数声明位未知注解 ⇒ E-UNKNOWN-ANN。 */
    @Test fun 声明位未知注解拒() {
        val o = scriptStd("""@ghost fun f(): Null {}
@impure fun main(): Null { unchecked print(1) }""")
        assertTrue(errs(o).contains("E-UNKNOWN-ANN"), "未知注解须落 E-UNKNOWN-ANN: " + errs(o))
        assertFalse(errs(o).isEmpty(), "不准静默放行")
    }

    /** 语句位 var 未知注解 ⇒ E-UNKNOWN-ANN。 */
    @Test fun 语句位未知注解拒() {
        val o = scriptStd("""@impure fun main(): Null {
    @ghost var x = 1
    unchecked print(x)
}""")
        assertTrue(errs(o).contains("E-UNKNOWN-ANN"), "语句位未知注解须落 E-UNKNOWN-ANN: " + errs(o))
    }

    /** 形参位未知注解 ⇒ E-UNKNOWN-ANN。 */
    @Test fun 形参位未知注解拒() {
        val o = scriptStd("""fun f(@ghost a: Nat): Nat = a
@impure fun main(): Null { unchecked print(f(1)) }""")
        assertTrue(errs(o).contains("E-UNKNOWN-ANN"), "形参位未知注解须落 E-UNKNOWN-ANN: " + errs(o))
    }

    /** 类型位未知注解 ⇒ E-UNKNOWN-ANN（parseType 既有 E 级升级挂码）。 */
    @Test fun 类型位未知注解拒() {
        val o = scriptStd("""@impure fun main(): Null {
    var x: @ghost Nat = 1
    unchecked print(x)
}""")
        assertTrue(errs(o).contains("E-UNKNOWN-ANN"), "类型位未知注解须落 E-UNKNOWN-ANN: " + errs(o))
    }

    /** @unchecked 不是注解（逃逸位是语句前缀 unchecked <stmt>）——写 @unchecked 注解 ⇒ E-UNKNOWN-ANN。 */
    @Test fun unchecked作注解拒() {
        val o = scriptStd("""@unchecked fun f(): Null {}
@impure fun main(): Null { unchecked print(1) }""")
        assertFalse(errs(o).isEmpty(), "@unchecked 非注解（逃逸位=语句前缀）须落 E 级（既有「期望注解名」承接）: " + errs(o))
    }

    // -------------------- 门 2：注解错位 ⇒ E-ANN-POSITION（报合法位）--------------------

    /** @impure 写在 var 位（合法位=函数声明/函数类型）⇒ E-ANN-POSITION。 */
    @Test fun impure错位var拒() {
        val o = scriptStd("""@impure fun main(): Null {
    @impure var x = 1
    unchecked print(x)
}""")
        val codes = errs(o)
        assertTrue(codes.contains("E-ANN-POSITION"), "@impure 错位须落 E-ANN-POSITION: " + codes)
        assertFalse(codes.contains("E-UNKNOWN-ANN"), "@impure 是已知注解——不落未知码")
    }

    /** @list 写在函数声明位（合法位=形参位末位变长）⇒ E-ANN-POSITION。 */
    @Test fun list错位声明拒() {
        val o = scriptStd("""@list fun f(): Null {}
@impure fun main(): Null { unchecked print(1) }""")
        assertTrue(errs(o).contains("E-ANN-POSITION"), "@list 错位须落 E-ANN-POSITION: " + errs(o))
    }

    /** @impure 写在 struct 声明位（合法位=函数级）⇒ E-ANN-POSITION。 */
    @Test fun impure错位struct拒() {
        val o = scriptStd("""@impure struct S(@mut n: Nat)
@impure fun main(): Null { unchecked print(1) }""")
        assertTrue(errs(o).contains("E-ANN-POSITION"), "@impure 写 struct 须落 E-ANN-POSITION: " + errs(o))
    }

    /** 类型位 @impure 修饰非函数类型（合法位=函数类型）⇒ E-ANN-POSITION。 */
    @Test fun impure错位非函数类型拒() {
        val o = scriptStd("""@impure fun main(): Null {
    var x: @impure Nat = 1
    unchecked print(x)
}""")
        val codes = errs(o)
        assertTrue(codes.contains("E-ANN-POSITION"), "@impure 修饰非函数类型须落 E-ANN-POSITION: " + codes)
        assertFalse(codes.contains("E-UNKNOWN-ANN"), "@impure 是已知注解——不落未知码")
    }

    // -------------------- 门 3：既有口径承接 + 合法形态零回归 --------------------

    /** @ref 形参名前既有 E 级口径承接（t95 消息逐字，E-SYNTAX 码不改）。 */
    @Test fun ref形参名前既有口径承接() {
        val o = scriptStd("""fun f(@ref a: Nat): Nat = a
@impure fun main(): Null { unchecked print(f(1)) }""")
        val msgs = errMessages(o)
        assertTrue(msgs.any { it.contains("@ref") && it.contains("类型位") },
            "@ref 形参名前既有口径（类型位注解提示）承接: " + msgs)
    }

    /** 合法形态面零回归——注册表全覆盖：@theorem/@root/@classic/@async/@mutable ext/@mut struct+字段+var/@list 形参/@impure 函数类型。 */
    @Test fun 合法形态全绿() {
        val o = scriptStd("""@theorem
fun th[p]: Null <p>
@impure @async
@root fun runBoth(f: () => Null, g: () => Null): Null
@classic
@theorem
fun tertiumNonDatur[p]: Null <p ∨ ¬<p>>
@mut struct Cell(@mut v: Nat)
@mutable fun List[a].add[a](e: a): List[a]
fun apply[T](x: T, f: @impure (T) => Null): Null = f(x)
@impure fun main(): Null {
    @mut var c = Cell(0)
    unchecked print(c)
}""")
        assertEquals(emptyList<String>(), errs(o), "合法形态面零回归: " + errs(o))
    }

    /** @ref var 共享只读视图（t97 语义——类型系统/特性巡礼样本形态）合法位零回归。 */
    @Test fun refVar视图位零回归绿() {
        val o = scriptStd("""@mut struct Box(@mut n: Nat)
@impure fun main(): Null {
    @mut var o : Box = Box(3)
    @ref var view : Box = o
    o.n = 4
    unchecked print(view.n)
}""")
        assertEquals(emptyList<String>(), errs(o), "@ref var 视图位零回归: " + errs(o))
    }

    /** 逃逸位语句前缀（unchecked <stmt>）零回归绿。 */
    @Test fun unchecked语句前缀零回归绿() {
        val o = scriptStd("""@impure fun main(): Null {
    unchecked print(1)
}""")
        assertEquals(emptyList<String>(), errs(o), "unchecked 逃逸位零回归: " + errs(o))
    }
}
