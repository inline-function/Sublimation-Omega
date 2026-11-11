package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t34（收口补测）：TS-8 特质继承环 E-CYCLE 与正闭包、TS-16 扩展函数自递归。
 */
class TraitInheritanceAndRecursionTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "t34.subl")
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

    private fun errs(bag: DiagBag) = bag.diags.filter { it.severity == Severity.ERROR }
    private fun errMsgs(bag: DiagBag) = errs(bag).joinToString("\n") { it.code + ":" + it.message }
    private fun stmts(hir: HirFile) = hir.entries.mapNotNull { (it as? HirStmtEntry)?.stmt }
    private fun lastVar(hir: HirFile) = stmts(hir).filterIsInstance<HirVarStmt>().last()

    // -------------------- TS-8：E-CYCLE（环） --------------------

    @Test
    fun mutualCycleReportsBothDeclarations() {
        val src = "trait A : B { }\ntrait B : A { }"
        val (hir, bag) = script(src)
        val cycles = errs(bag).filter { it.code == "E-CYCLE" }
        assertEquals(2, cycles.size, "A:B / B:A 互相循环——两处声明位置均报；actual=" + errMsgs(bag))
        assertTrue(cycles.all { it.message.contains("TS-8") }, "诊断文本标注 TS-8；actual=" + cycles.map { it.message })
        assertFalse(errs(bag).any { it.code != "E-CYCLE" }, "环之外不应级联其它错误；actual=" + errMsgs(bag))
    }

    @Test
    fun selfCycleReports() {
        val (hir, bag) = script("trait S : S { }")
        val cycles = errs(bag).filter { it.code == "E-CYCLE" }
        assertEquals(1, cycles.size, "自继承 A:A 是平凡环（TS-8）；actual=" + errMsgs(bag))
    }

    @Test
    fun diamondAcyclicHasNoCycle() {
        val (hir, bag) = script("trait D { }\ntrait B : D { }\ntrait C : D { }\ntrait A : B, C { }")
        assertFalse(errs(bag).any { it.code == "E-CYCLE" }, "菱形（共同父类非环）；actual=" + errMsgs(bag))
    }

    // -------------------- TS-8：继承闭包（满足子类 ⇒ 满足父类） --------------------

    @Test
    fun inheritanceClosureSatisfiesFromLeafToRoot() {
        val src = "trait C { fun cOp(): Nat }\n" +
            "trait B : C { fun bOp(): Nat }\n" +
            "trait A : B { fun aOp(): Nat }\n" +
            "fun Nat.cOp(): Nat = 1\n" +
            "fun Nat.bOp(): Nat = 2\n" +
            "fun Nat.aOp(): Nat = 3\n" +
            "fun use[T: A](x: T): Nat = 0\n" +
            "var z = use(1)"
        val (hir, bag) = script(src)
        assertFalse(errs(bag).isNotEmpty(), "合法继承链 A:B:C——Nat 扩展覆盖闭包全部规范即满足 A（TS-8 闭包）；actual=" + errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as HirNamedType).name)
    }

    @Test
    fun inheritanceClosureMissingRootSpecReports() {
        val src = "trait C { fun cOp(): Nat }\n" +
            "trait B : C { fun bOp(): Nat }\n" +
            "trait A : B { fun aOp(): Nat }\n" +
            "fun Nat.bOp(): Nat = 2\n" +
            "fun Nat.aOp(): Nat = 3\n" +
            "fun use[T: A](x: T): Nat = 0\n" +
            "var z = use(1)"
        val (hir, bag) = script(src)
        val codes = errs(bag).map { it.code }
        assertEquals(listOf("E-TC-NOT-SATISFIED"), codes, "缺 cOp（来自祖父 C）→ E-TC-NOT-SATISFIED；actual=" + errMsgs(bag))
        val msg = errs(bag).first { it.code == "E-TC-NOT-SATISFIED" }.message
        assertTrue(msg.contains("cOp()"), "缺失清单含闭包来源规范 cOp()；actual=" + msg)
    }

    // -------------------- TS-16：扩展函数自递归 --------------------

    @Test
    fun extFunSelfRecursionViaSugarCompiles() {
        // t63（ST-10.1）：self - 1 : Int ⇒ 接收者改 Int（Nat 无符号：递减可负，诚实声明）。
        val src = "fun Int.countDown(): Nat = if (self == 0) 0 else (self - 1).countDown()\n" +
            "var n: Int = 9\n" +
            "var z = n.countDown()"
        val (hir, bag) = script(src)
        assertFalse(errs(bag).isNotEmpty(), "方法糖自递归：解析与类型检查通过（TS-16）；actual=" + errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as HirNamedType).name)
    }

    @Test
    fun extFunSelfRecursionViaDirectCallCompiles() {
        val src = "fun Int.countDown2(): Nat = if (self == 0) 0 else countDown2(self - 1)\n" +
            "var n: Int = 9\n" +
            "var z = countDown2(n)"
        val (hir, bag) = script(src)
        assertFalse(errs(bag).isNotEmpty(), "直接调用自递归：解析与类型检查通过（TS-16）；actual=" + errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as HirNamedType).name)
    }

    @Test
    fun extFunMixedRecursionFormsCompiles() {
        val src = "fun Int.mix(): Nat = if (self == 0) 0 else if (self > 5) mix(self - 1) else (self - 1).mix()\n" +
            "var n: Int = 9\n" +
            "var z = n.mix()"
        val (hir, bag) = script(src)
        assertFalse(errs(bag).isNotEmpty(), "递归形态混用（直接调用 + 方法糖调自身）均解析；actual=" + errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as HirNamedType).name)
    }
}
