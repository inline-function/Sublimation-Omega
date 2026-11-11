package sublimation.frontend.semantic

import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirFile
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P-10 步骤 1 健全化收口（ADR-36）：四条纯收紧规则的正例/负例与对照例。
 *
 * 方法论（验收方案）：负例先于实现——先以旧实现跑红作基线，再改实现跑绿；
 * 每条负例配「仅规则关注处不同」的对照例，对照例落错则负例无效。
 *
 * 四规则：
 * - R-34 synthetic 位不再一律放行（adaptOk 完成后判定通道严格；推导未完成由宽松合一通道承担）
 * - R-35 Nothing 单向（got 为 Nothing 放行；want 为 Nothing 仅 want=got 落 typeEq）
 * - R-36 函数类型参数位逆变（wantP <: gotP）、返回位协变（gotRet <: wantRet）
 * - R-37 函数值调用按其函数类型核对实参（消除「调用f」合成位）
 * - 收口侧：内建泛型位改具名类型变量标记（(内建T) 由 instCall 解出）；合成位触发的派生
 *   E-TYPE-MISMATCH 在已有根因诊断时抑制（一个根因一条，同 t51/t52 口径）
 */
class SoundnessTighteningTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src.trimIndent(), "t58.subl")
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


    // ============ P1：函数体与声明返回不符（synthetic 不再一律放行前的真失配兜底） ============

    /** P1 负例：Nothing 体返回数值字面量——R-35 单向后 Nothing 不吸收任意体类型。 */
    @Test
    fun p1NothingBodyRefused() {
        val e = script("""
            fun f(): Nothing = 5
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "P1 负例须落 E-TYPE-MISMATCH：${errs(e).map { it.code }}")
    }

    /** P1 对照例：仅返回类型被改成关注的 Nat（其余全同）——正常数值体放行。 */
    @Test
    fun p1NatBodyPasses() {
        val e = script("""
            fun g(): Nat = 5
        """).second
        assertTrue(errs(e).isEmpty(), "P1 对照例不得落错：${errs(e).map { it.code }}")
    }

    // ============ R-35 反向：want=Nothing 除同型外被拒 ============

    @Test
    fun r35NothingExpectRefused() {
        val e = script("""
            var x: Nothing = 5
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "5 不得适配 Nothing")
    }

    @Test
    fun r35NothingContrastPasses() {
        val e = script("""
            var y: Nothing = panic()
        """).second
        assertTrue(errs(e).isEmpty(), "panic() 返回 Nothing 适配 Nothing：${errs(e).map { it.code }}")
    }

    // ============ P2：函数类型参数位逆变 ============

    /** P2 负例：(Nat) => Nat 赋给 (Rat) => Nat 形参——参数位变上/运表伍逆向，纵参级塔单向不可补。 */
    @Test
    fun p2CovariantParamRefused() {
        val e = script("""
            fun pv(n: Nat): Nat = n
            fun use(f: (Rat) => Nat): Nat = f(1)
            var r = use(pv)
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "P2 负例须落 E-TYPE-MISMATCH：${errs(e).map { it.code }}")
    }

    /** P2 对照例：仅参数位上下游(Rat)运表，其余全同——逆变后 (Rat) => Nat 赋给 (Nat) => Nat 形参放行。 */
    @Test
    fun p2ContravariantContrastPasses() {
        val e = script("""
            fun pv2(n: Rat): Nat = 1
            fun use(f: (Nat) => Nat): Nat = f(1)
            var r = use(pv2)
        """).second
        assertTrue(errs(e).isEmpty(), "P2 对照例（参数位逆变成立）不得落错：${errs(e).map { it.code }}")
    }

    // ============ P3：推导失败但被放过的用例（P4 前置）============

    /** P3：panic() 返回 Nothing，作为 Nat 体放行（Nothing 单向的合法侧）。 */
    @Test
    fun p3NothingBodyPasses() {
        val e = script("""
            fun f(): Nat = panic()
        """).second
        assertTrue(errs(e).isEmpty(), "P3：Nothing 体放行：${errs(e).map { it.code }}")
    }

    // ============ P4：推导失败被放过必须报错 ============

    /** P4 负例：泛型模板内 T 抽象、Nat 非 T——类型抽象不能冒充具体类型兜底。 */
    @Test
    fun p4SyntheticLeakRefused() {
        val e = script("""
            fun apply[T](f: (T) => T): T = f(0)
            var r: Nat = apply({ n => n })
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "P4：推导失败被放过须落错：${errs(e).map { it.code }}")
    }

    /** P4 对照例：调用点给出能解出 T 的实参——同名模板正常实例化，不落错。 */
    @Test
    fun p4SoundContrastPasses() {
        val e = script("""
            fun apply[T](f: (T) => T, x: T): T = f(x)
            var r: Nat = apply({ n => n }, 0)
        """).second
        assertTrue(errs(e).isEmpty(), "P4 对照例不得落错：${errs(e).map { it.code }}")
    }

    // ============ R-37：函数值调用按其函数类型核对 ============

    @Test
    fun r37FunValueCallChecked() {
        val e = script("""
            fun g(f: (Nat) => Nat): Nat = f(1)
        """).second
        assertTrue(errs(e).isEmpty(), "函数值调用按函数类型核对（R-37，消除「调用f」合成位）：${errs(e).map { it.code }}")
    }

    // ============ 收口侧：一个根因一条诊断（合成位派生抑制） ============

    @Test
    fun cascadeOnlyRootReported() {
        val e = script("""
            fun f(): Nat = undefined + 1
        """).second
        val codes = errs(e).map { it.code }
        assertEquals(listOf("E-UNBOUND-NAME"), codes, "根因诊断之外不得追加派生 E-TYPE-MISMATCH")
        assertTrue(e.hasError)
    }

    private fun errs(bag: DiagBag): List<Diag> = bag.diags.filter { it.severity == Severity.ERROR }
}