package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.cli.compileStdlibUnits
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t150（R-28 用户新裁 2026-11-13 + R-29 深拷补注）：可转换矩阵推翻旧实现——5 格隐式深拷改拒
 * （want PLAIN × 源 REF/REFMUT、want MUT × 源 ≠ MUT ⇒ E-TYPE-MISMATCH + 显式迁移指引 copy/toMutableList）。
 * R-28 后仅存复制格 MUT→PLAIN / MUT→MUT 落 D-DEEP-COPY 补注（R-29，非 E 级、不拦）。
 * 纪律：负例先红——实现前轮 12 红（5 拒格 + 落点 2 + 补注 5）/ 2 绿（共享格 + 既有拒格回归保护）；
 * 本轮源按 R-28 后合法形态（@mut 值唯经构造族 mutableListOf 产出）。 */

class T150R28MatrixTest {

    private val stdlibMounts: Map<String, ModuleSnapshot> =
        compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            .let { (u, _) -> u.associate { it.alias to it.snapshot } }

    private val prelude = "@mut struct Box(@mut n: Nat)"

    /** 错误（code::message）/ 全码 / 补注（code::message）。 */
    private fun judge(src: String): Triple<List<String>, List<String>, List<String>> {
        val pr = parseScriptSourceWithNotes(src, "t150.subl")
        val (hir, bag) = compile(pr.file, pr.notes, scriptMode = false, mounted = stdlibMounts)
        pr.diag.forEach { d ->
            when (d.severity) {
                Severity.ERROR -> bag.error(d.code, d.pos, d.message)
                Severity.WARN -> bag.warn(d.code, d.pos, d.message)
                Severity.HINT -> bag.hint(d.code, d.pos, d.message)
                Severity.SUPPLEMENT -> bag.supplement(d.code, d.pos, d.message)
            }
        }
        val errs = bag.diags.filter { it.severity == Severity.ERROR }.map { it.code + "::" + it.message }
        val supps = bag.diags.filter { it.severity == Severity.SUPPLEMENT }.map { it.code + "::" + it.message }
        return Triple(errs, bag.diags.map { it.code }, supps)
    }

    private fun body(vararg lines: String): String =
        prelude + "\n@impure fun main() {\n " + lines.joinToString("\n ") + "\n}"

    // ---------- 门 1：R-28 五拒格 ----------

    @Test fun R28拒格1_plain到mut_初始化位() {
        val (errs, _, _) = judge(body("var xs : List[Nat] = listOf(1)", "var ys : @mut List[Nat] = xs"))
        assertTrue(errs.any { it.startsWith("E-TYPE-MISMATCH") }, "PLAIN→MUT 须落 E-TYPE-MISMATCH（实得 $errs）")
    }

    @Test fun R28拒格2_ref到plain_初始化位() {
        val (errs, _, _) = judge(body("var a : @ref Box = Box(0)", "var u : Box = a"))
        assertTrue(errs.any { it.startsWith("E-TYPE-MISMATCH") }, "REF→PLAIN 须落 E-TYPE-MISMATCH（实得 $errs）")
    }

    @Test fun R28拒格3_refmut到plain_初始化位() {
        val (errs, _, _) = judge(prelude + "\n@impure fun main() {\n" +
            " var xs : @mut List[Nat] = mutableListOf(1)\n var r : @ref @mut List[Nat] = xs\n var u : List[Nat] = r\n}")
        assertTrue(errs.any { it.startsWith("E-TYPE-MISMATCH") }, "REFMUT→PLAIN 须落 E-TYPE-MISMATCH（实得 $errs）")
    }

    @Test fun R28拒格4_ref到mut_初始化位() {
        val (errs, _, _) = judge(body("var a : @ref Box = Box(0)", "var u : @mut Box = a"))
        assertTrue(errs.any { it.startsWith("E-TYPE-MISMATCH") }, "REF→MUT 须落 E-TYPE-MISMATCH（实得 $errs）")
    }

    @Test fun R28拒格5_refmut到mut_初始化位() {
        val (errs, _, _) = judge(prelude + "\n@impure fun main() {\n" +
            " var xs : @mut List[Nat] = mutableListOf(1)\n var r : @ref @mut List[Nat] = xs\n var u : @mut List[Nat] = r\n}")
        assertTrue(errs.any { it.startsWith("E-TYPE-MISMATCH") }, "REFMUT→MUT 须落 E-TYPE-MISMATCH（实得 $errs）")
    }

    @Test fun R28拒格消息迁移指引() {
        val (errs, _, _) = judge(body("var xs : List[Nat] = listOf(1)", "var ys : @mut List[Nat] = xs"))
        assertTrue(errs.any { it.contains("R-28") && it.contains("copy") && it.contains("toMutableList") },
            "拒格消息须带显式迁移指引（copy/toMutableList）: $errs")
    }

    // ---------- 门 2：拒格落点（传参位 + 赋值位）----------

    @Test fun R28拒格传参位() {
        val (errs, _, _) = judge(prelude + "\n@impure fun g(z : @mut Box) { z.n = 1 }\n@impure fun main() {\n" +
            " var t : Box = Box(0)\n g(t)\n}")
        assertTrue(errs.any { it.startsWith("E-TYPE-MISMATCH") }, "传参位 PLAIN→MUT 须落 E-TYPE-MISMATCH（实得 $errs）")
        assertTrue(errs.any { it.contains("实参 1") }, "传参位消息须落实参位（实得 $errs）")
    }

    @Test fun R28拒格赋值位() {
        val (errs, _, _) = judge(body("var u : @mut List[Nat] = mutableListOf(1)", "var xs : List[Nat] = listOf(2)", "u = xs"))
        assertTrue(errs.any { it.startsWith("E-TYPE-MISMATCH") }, "赋值位 PLAIN→MUT 须落 E-TYPE-MISMATCH（实得 $errs）")
    }

    // ---------- 门 3：R-29 深拷补注（D-DEEP-COPY，非 E 级、不拦）----------

    @Test fun R29深拷补注_mut到plain() {
        val (errs, _, supps) = judge(prelude + "\n@impure fun main() {\n" +
            " var a : @mut List[Nat] = mutableListOf(1)\n var b : List[Nat] = a\n}")
        assertEquals(emptyList<String>(), errs.filter { it.startsWith("E") }, "深拷转换不拦（零 E）")
        val note = supps.lastOrNull { it.startsWith("D-DEEP-COPY::") }
        assertTrue(note != null && note.contains("隐式深拷"), "var b : T = a 须落 D-DEEP-COPY 补注含隐式深拷说明（实得 $supps）")
    }

    @Test fun R29深拷补注_mut到mut() {
        val (errs, _, supps) = judge(prelude + "\n@impure fun main() {\n" +
            " var a : @mut List[Nat] = mutableListOf(1)\n var u : @mut List[Nat] = a\n}")
        assertEquals(emptyList<String>(), errs.filter { it.startsWith("E") }, "MUT→MUT 深拷不拦")
        assertTrue(supps.any { it.startsWith("D-DEEP-COPY::") && it.contains("隐式深拷") },
            "MUT→MUT 须落 D-DEEP-COPY 补注（实得 $supps）")
    }

    @Test fun R29深拷补注_传参位() {
        val (errs, _, supps) = judge(prelude + "\n@impure fun g(z : List[Nat]) { }\n@impure fun main() {\n" +
            " var a : @mut List[Nat] = mutableListOf(1)\n g(a)\n}")
        assertEquals(emptyList<String>(), errs.filter { it.startsWith("E") }, "传参位深拷不拦")
        assertTrue(supps.any { it.startsWith("D-DEEP-COPY::") }, "传参位深拷须落补注（实得 $supps）")
    }

    @Test fun R29深拷补注_赋值位() {
        val (errs, _, supps) = judge(prelude + "\n@impure fun main() {\n" +
            " @mut var u : List[Nat] = Nil\n var a : @mut List[Nat] = mutableListOf(1)\n u = a\n}")
        assertEquals(emptyList<String>(), errs.filter { it.startsWith("E") }, "赋值位深拷不拦")
        assertTrue(supps.any { it.startsWith("D-DEEP-COPY::") }, "赋值位深拷须落补注（实得 $supps）")
    }

    // ---------- 门 4：共享格零回归 ----------

    @Test fun 共享格零回归() {
        val t1 = judge(body("var t : Box = Box(0)", "var u : Box = t"))
        assertEquals(emptyList<String>(), t1.first.filter { it.startsWith("E") }, "T→T 共享零 E")
        assertFalse(t1.third.any { it.startsWith("D-DEEP-COPY") }, "T→T 共享无深拷补注")
        val t2 = judge(prelude + "\n@impure fun main() {\n" +
            " var a : @mut List[Nat] = mutableListOf(1)\n var u : @ref List[Nat] = a\n}")
        assertEquals(emptyList<String>(), t2.first.filter { it.startsWith("E") }, "@mut T→@ref T 共享零 E（a 初始化的 MUT→MUT 深拷补注为合法格）")
        val t3 = judge(body("var t : Box = Box(0)", "var u : @ref Box = t"))
        assertEquals(emptyList<String>(), t3.first.filter { it.startsWith("E") }, "T→@ref T 共享零 E")
        assertFalse(t3.third.any { it.startsWith("D-DEEP-COPY") }, "共享格无深拷补注")
    }

    // ---------- 门 5：既有两拒格零回归（want REFMUT × 源 PLAIN/REF）+ 复制格两态 ----------

    @Test fun 既有拒格零回归() {
        val t1 = judge(body("var t : Box = Box(0)", "var u : @ref @mut Box = t"))
        assertTrue(t1.first.any { it.startsWith("E-TYPE-MISMATCH") }, "T→@ref @mut T 既有拒格仍拒")
        val t2 = judge(body("var a : @ref Box = Box(0)", "var u : @ref @mut Box = a"))
        assertTrue(t2.first.any { it.startsWith("E-TYPE-MISMATCH") }, "@ref T→@ref @mut T 既有拒格仍拒")
    }
}
