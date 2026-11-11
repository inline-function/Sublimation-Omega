package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t51（ADR-33）：双向类型检查燃料——有限停机 + 耗尽拒绝 E-FUEL-EXHAUSTED。
 * 正例：正常程序（含深但有限）绝不耗尽；反例：归纳别名互追的发散结构比较必须被拒绝且不崩溃。
 */
class FuelMeteringTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "t51.subl")
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
    private fun msgs(bag: DiagBag) = errs(bag).joinToString(";") { it.code + ":" + it.message }
    private fun fuelDiags(bag: DiagBag) = bag.diags.filter { it.code == "E-FUEL-EXHAUSTED" }

    // ---------------- 任务 5·正例：正常程序不耗尽（深但有限） ----------------

    /** 50 层嵌套 struct——深但严格有限的类型链。 */
    @Test
    fun deepButFiftyNestedStructsDoNotExhaust() {
        val sb = StringBuilder()
        for (i in 0..49) {
            sb.append(if (i == 0) "struct D0() " else "struct D" + i + "(d: D" + (i - 1) + ") ")
        }
        var ctor = "D0"
        for (i in 1..49) ctor = "D" + i + "(" + ctor + ")"
        sb.append("var x: D49 = " + ctor)
        val (hir, bag) = script(sb.toString())
        assertEquals(0, errs(bag).size, "深但有限应整体通过；actual=" + msgs(bag))
        assertEquals(0, fuelDiags(bag).size, "正常深嵌套不得触燃料哨")
        val v = hir.entries.mapNotNull { (it as? HirStmtEntry)?.stmt as? HirVarStmt }.last()
    }

    /** 常见程序形态（数值塔/联合/变长/普通调用）一律不耗尽。 */
    @Test
    fun ordinaryProgramsDoNotExhaust() {
        val srcs = listOf(
            "fun f(n: Nat): Nat = n + 1\nvar r: Nat = f(1)",
            "struct None()\nstruct Some[T](v: T)\ntype Opt[T] = None | Some[T]\nvar o: Opt[Nat] = Some(1)",
            "fun len[T](@list xs: T): Nat = 0\nvar n = len(1, 2, 3)",
            "fun add(a: Nat, b: Nat): Nat = a + b\nvar y = add(1, 2)",
        )
        srcs.forEach { src ->
            val (_, bag) = script(src)
            assertEquals(0, errs(bag).size, "正常样本应通过；actual=" + msgs(bag))
            assertEquals(0, fuelDiags(bag).size)
        }
    }

    // ---------------- 任务 5·反例：归纳别名互追（必发散） ----------------

    /** A/B 互归纳别名经同一双参函数求理论参数 → unifyInto 双向展开互追不终止。 */
    @Test
    fun divergentMutualInductiveAliasReportsExhausted() {
        val src = "struct Zero()\n" +
            "struct Succ[T](pred: T)\n" +
            "type A = Zero | Succ[A]\n" +
            "type B = Zero | Succ[B]\n" +
            "fun g[T](t: T, u: T): T = t\n" +
            "var a: A = Zero\n" +
            "var b: B = Zero\n" +
            "var r = g(a, b)"
        val (hir, bag) = script(src)
        val fs = fuelDiags(bag)
        assertEquals(1, fs.size, "恰好一份 E-FUEL-EXHAUSTED（每池一次）;actual=" + msgs(bag))
        assertEquals("编译器进行检查时无法停机", fs.first().message)
        val sup = bag.diags.firstOrNull { it.code == "D-FUEL-EXHAUSTED" }
        assertTrue(sup != null, "必须附 D-FUEL-EXHAUSTED 补充信息")
        val sm = sup!!.message
        assertTrue(sm.contains("①触发位置："), "补充信息含位置；actual=" + sm)
        assertTrue(sm.contains("②检查目标：实际"), "补充信息含实际类型；actual=" + sm)
        assertTrue(sm.contains("/ 期望"), "补充信息含期望类型；actual=" + sm)
        assertTrue(sm.contains("③已消耗"), "补充信息含已消耗步数；actual=" + sm)
        assertTrue(sm.contains("上限 237500"), "补充信息含上限；actual=" + sm)
        assertTrue(sm.contains("④建议"), "补充信息含建议；actual=" + sm)
        assertTrue(sm.contains("递归深度"), "发散互追由深度哨先于栈溢出机械触发（步骤预算 + 深度哨双口径）；actual=" + sm)
        assertTrue(errs(bag).all { it.code == "E-FUEL-EXHAUSTED" || it.code == "E-TYPE-MISMATCH" },
            "耗尽后仅允许级联的签名核对错误；actual=" + msgs(bag))
    }

    /** 耗尽后同一编译内后续检查不受污染（池按顶层请求隔离）。 */
    @Test
    fun exhaustedPoolDoesNotPolluteLaterChecks() {
        val src = "struct Zero()\n" +
            "struct Succ[T](pred: T)\n" +
            "type A = Zero | Succ[A]\n" +
            "type B = Zero | Succ[B]\n" +
            "fun g[T](t: T, u: T): T = t\n" +
            "var a: A = Zero\n" +
            "var b: B = Zero\n" +
            "var r = g(a, b)\n" +
            "var after: Nat = 1 + 2\n" +
            "var s: Nat = after * 2"
        val (hir, bag) = script(src)
        assertEquals(1, fuelDiags(bag).size, "仅发散请求那一份耗尽诊断")
        val laterErrs = errs(bag).filter { (it.pos?.start?.line ?: 0) >= 9 }
        assertEquals(0, laterErrs.size, "后续检查不应有错误（池隔离）；actual=" + laterErrs.joinToString { it.message })
        val vs = hir.entries.mapNotNull { (it as? HirStmtEntry)?.stmt as? HirVarStmt }
        assertEquals("after", vs[vs.size - 2].name)
        assertEquals("s", vs.last().name)
    }

    /** 同输入同计数（确定性）：两次独立编译的耗尽步数一致。 */
    @Test
    fun fuelCountIsDeterministicPerInput() {
        val src = "struct Zero()\n" +
            "struct Succ[T](pred: T)\n" +
            "type A = Zero | Succ[A]\n" +
            "type B = Zero | Succ[B]\n" +
            "fun g[T](t: T, u: T): T = t\n" +
            "var a: A = Zero\n" +
            "var b: B = Zero\n" +
            "var r = g(a, b)"
        fun steps(): String? {
            val (_, bag) = script(src)
            val m = bag.diags.first { it.code == "D-FUEL-EXHAUSTED" }.message
            return Regex("已消耗 \\d+").find(m)?.value
        }
        val a = steps()
        val b = steps()
        assertFalse(a.isNullOrEmpty())
        assertEquals(a, b, "同输入两次编译的耗尽步数必须一致（确定性计数）")
    }
}