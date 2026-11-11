package sublimation.frontend.semantic

import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirFile
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * t61 视图降级（ADR-38 / ST-11.1）：@mut T 在类型计算中须被当作 T；adaptOk 单向视图规则
 * （@mut T → T 成立，T → @mut T 不成立）；binExpr 不得猜类型（表外算符落 E-OP-UNCLASSIFIED + 合成位）。
 *
 * 负例（须报错）证明方向未搞反：plain 值返回 @mut 视图层被拒；@mut List[Nat] 与 @mut List[Any]
 * 经不变性（实参位 typeEq）被拒。正例（须通过）：队长实测的六条 BEFORE 错误形态。
 */
class ViewDemotionTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src.trimIndent(), "t61.subl")
        val (hir, bag) = compile(pr.file, pr.notes)
        pr.diag.forEach { d ->
            when (d.severity) {
                Severity.ERROR -> bag.error(d.code, d.pos, d.message)
                Severity.WARN -> bag.warn(d.code, d.pos, d.message)
                Severity.HINT -> bag.hint(d.code, d.pos, d.message)
                Severity.SUPPLEMENT -> bag.supplement(d.code, d.pos, d.message)
            }
        }
        return hir to bag
    }

    private fun errs(bag: DiagBag): List<Diag> = bag.diags.filter { it.severity == Severity.ERROR }

    private fun mismatch(bag: DiagBag) = errs(bag).any { it.code == "E-TYPE-MISMATCH" }

    /**
     * @mut Nat 上的算术（x = x + 1）须按 Nat 分类通过（t61 主修复）。
     */
    @Test
    fun v1_mutArithmeticOk() {
        val e = script("""
            var x: @mut Nat = 1
            x = x + 1
            x = x + 0
        """).second
        assertTrue(errs(e).isEmpty(), "@mut Nat 上的算术（x = x + 1）须按 Nat 分类通过（t61 主修复）")
    }

    /**
     * 声明返回 Nat 的函数体为 @mut Nat 须视图降级通过。
     */
    @Test
    fun v2_funReturnViewDemotedOk() {
        val e = script("""
            fun f(a: @mut Nat): Nat = a
        """).second
        assertTrue(errs(e).isEmpty(), "声明返回 Nat 的函数体为 @mut Nat 须视图降级通过")
    }

    /**
     * @mut Str 的拼接须按 Str 拼接通过。
     */
    @Test
    fun v3_mutStrConcatOk() {
        val e = script("""
            fun f(a: @mut Str): Str = a + "x"
        """).second
        assertTrue(errs(e).isEmpty(), "@mut Str 的拼接须按 Str 拼接通过")
    }

    /**
     * 区间 .. 的 @mut Nat 端点须按数值通过并得 Range[Nat]。
     */
    @Test
    fun v4_mutRangeEndpointOk() {
        val e = script("""
            fun f(a: @mut Nat): Range[Nat] = 0..a
        """).second
        assertTrue(errs(e).isEmpty(), "区间 .. 的 @mut Nat 端点须按数值通过并得 Range[Nat]")
    }

    /**
     * 基线对照：@mut P 接收者的字段算术（合成位路径，R-38 闸）保持通过。
     */
    @Test
    fun v5_mutFieldArithBaseline() {
        val e = script("""
            struct P(x: Nat)
            fun f(p: @mut P): Nat = p.x + 1
        """).second
        assertTrue(errs(e).isEmpty(), "基线对照：@mut P 接收者的字段算术（合成位路径，R-38 闸）保持通过")
    }

    /**
     * 基线对照：@mut Nat 比较须通过。
     */
    @Test
    fun v6_mutCompareBaseline() {
        val e = script("""
            fun f(a: @mut Nat): Bool = a < 2
        """).second
        assertTrue(errs(e).isEmpty(), "基线对照：@mut Nat 比较须通过")
    }

    /**
     * @mut Nat 形参直接参与算术须通过。
     */
    @Test
    fun v7_mutParamArithOk() {
        val e = script("""
            fun f(a: @mut Nat): Nat = a + 1
        """).second
        assertTrue(errs(e).isEmpty(), "@mut Nat 形参直接参与算术须通过")
    }

    /**
     * @mut Nat 透传（@mut → @mut）须通过。
     */
    @Test
    fun v8_mutPassThruOk() {
        val e = script("""
            fun f(a: @mut Nat): @mut Nat = a
        """).second
        assertTrue(errs(e).isEmpty(), "@mut Nat 透传（@mut → @mut）须通过")
    }

    /**
     * in 与区间的 @mut 端点须通过。
     */
    @Test
    fun v9_inMutRangeOk() {
        val e = script("""
            fun f(a: @mut Nat): Bool = 0 in 0..a
        """).second
        assertTrue(errs(e).isEmpty(), "in 与区间的 @mut 端点须通过")
    }

    /**
     * 一元取负（-a）的 @mut 操作数：结果承视图、返回位经视图降级通过。
     */
    @Test
    fun v10_unaryMinusOnMutOk() {
        val e = script("""
            fun f(a: @mut Nat): Int = -a
        """).second
        assertTrue(errs(e).isEmpty(), "一元取负的 @mut 操作数经视图降级须通过")
    }

    /**
     * 对照拒绝：plain Nat 不得升格为 @mut Nat 返回（方向不可搞反）。
     */
    @Test
    fun c1_plainToMutReturnRejected() {
        val e = script("""
            fun g(a: Nat): @mut Nat = a
        """).second
        assertTrue(mismatch(e), "对照拒绝：plain Nat 不得升格为 @mut Nat 返回（方向不可搞反）")
    }

    /**
     * 对照拒绝：@mut List[Nat] 与 @mut List[Any] 须经实参位 typeEq 拒绝（不变性不被破坏）。
     */
    @Test
    fun c2_mutListInvariantRejected() {
        val e = script("""
            fun g(a: @mut List[Nat]): @mut List[Any] = a
        """).second
        assertTrue(mismatch(e), "对照拒绝：@mut List[Nat] 与 @mut List[Any] 须经实参位 typeEq 拒绝（不变性不被破坏）")
    }

    /**
     * 对照拒绝：返回位不造格——plain List[Nat] 不得升格为 @mut List[Nat]。
     */
    @Test
    fun c3_plainListToMutListReturnRejected() {
        val e = script("""
            fun g(a: List[Nat]): @mut List[Nat] = a
        """).second
        assertTrue(mismatch(e), "对照拒绝：返回位不造格——plain List[Nat] 不得升格为 @mut List[Nat]")
    }

    /**
     * 不回退：@mut 初始化与字段写/算术自更新须照常通过。
     */
    @Test
    fun d1_mutInitAndAssignStillOk() {
        val e = script("""
            @mut struct Box(@mut n: Nat)
            var b: @mut Box = Box(1)
            b.n = 2
            var x: @mut Nat = 1
            x = 5
            x = x + 0
        """).second
        assertTrue(errs(e).isEmpty(), "不回退：@mut 初始化与字段写/算术自更新须照常通过")
    }

    /**
     * 不回退：plain 值经 @mut 形参借用（impureBump 语义）须照常通过。
     */
    @Test
    fun d2_plainArgToMutParamBorrowOk() {
        val e = script("""
            @mut struct Counter(@mut v: Nat)
            @impure fun bump(c: @mut Counter): Null {
                c.v = c.v + 1
            }
            @impure fun main() {
                @mut var ct = Counter(0)
                bump(ct)
            }
        """).second
        assertTrue(errs(e).isEmpty(), "不回退：plain 值经 @mut 形参借用（impureBump 语义）须照常通过")
    }

}