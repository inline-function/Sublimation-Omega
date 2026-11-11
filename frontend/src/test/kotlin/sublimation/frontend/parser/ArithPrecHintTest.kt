package sublimation.frontend.parser

import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t71（ADR-46，用户裁决 A1-A3)：H-ARITH-PREC 重构——**同类不提示 / 跨类必提示 / 括号重置层级**。
 * 类 1：^;类 2:* / %;类 3:+ -。同一括号层级内一条算术链混用不同类算符 ⇒ 提示级诊断（位置指
 * 向跨类算符，链只报一次）。括号内链独立判定；自定义中缀/比较算符不判。优先级永不存在——提示
 * 只是「不规范代码」劝告，不改变左结合语义。
 */
class ArithPrecHintTest {

    private fun hints(src: String): Pair<Int, List<Diag>> {
        val r = parseSourceWithNotes(src, "arith.subl")
        val hs = r.diag.filter { it.code == "H-ARITH-PREC" }
        return hs.size to hs
    }

    private fun count(expr: String): Int = hints("fun f(): Nat { var t = $expr }").first

    /** #1:a * b + c——跨类（* vs +）必提示，位置指向跨类算符 `+`。 */
    @Test fun t71_1_starThenPlus() {
        val (n, hs) = hints("fun f(): Nat { var t = 1 * 1 + 1 }")
        assertEquals(1, n, "#1 跨类必提示，actual=$n")
        val h = hs.single()
        assertEquals(Severity.HINT, h.severity, "#1 H 族提示级，不得升级")
        assertTrue(h.message.contains("左结合"), "#1 文案说清左结合：${h.message}")
        assertTrue(h.message.contains("*") && h.message.contains("+"), "#1 文案指出跨类双方：${h.message}")
    }

    /** #2:a + b / c——跨类（+ vs /）。 */
    @Test fun t71_2_plusThenDiv() { assertEquals(1, count("1 + 1 / 1"), "#2") }

    /** #3:a ^ b * c——跨类（^ vs *）。 */
    @Test fun t71_3_powThenStar() { assertEquals(1, count("1 ^ 1 * 1"), "#3") }

    /** #4:a + b ^ c——跨类（+ vs ^)。 */
    @Test fun t71_4_plusThenPow() { assertEquals(1, count("1 + 1 ^ 1"), "#4") }

    /** #5:a + b + c——同类（+) 不提示。 */
    @Test fun t71_5_plusOnly() { assertEquals(0, count("1 + 1 + 1"), "#5 同类不提示") }

    /** #6:(a + b) * c——括号重置层级，外层只剩单一算符，不提示（用户已显式括号）。 */
    @Test fun t71_6_parenResetsLevel() {
        val (n, _) = hints("fun f(): Nat { var t = (1 + 1) * 1 }")
        assertEquals(0, n, "#6 已加括号不提示（旧规则误报，本规则不报）")
    }

    /** #7:a - b + c——同类（+/-)。 */
    @Test fun t71_7_minusPlusSameClass() { assertEquals(0, count("1 - 1 + 1"), "#7") }

    /** #8:a * b / c——同类（* / /)。 */
    @Test fun t71_8_starDivSameClass() { assertEquals(0, count("1 * 1 / 1"), "#8") }

    /** #9:a % b + c——跨类（% vs +)。 */
    @Test fun t71_9_modThenPlus() { assertEquals(1, count("1 % 1 + 1"), "#9") }

    /** #10:a + b * c + d——三算符链只报一次（首个跨类结合点报，后续不重复）。 */
    @Test fun t71_10_threeOpsReportOnce() {
        val (n, _) = hints("fun f(): Nat { var t = 1 + 1 * 1 + 1 }")
        assertEquals(1, n, "#10 同一链不重复报，actual=$n")
    }

    /** #11:a + (b * c + d)——括号内层跨类以内层报（位置在内层），外层不报。 */
    @Test fun t71_11_innerChainReports() {
        val (n, hs) = hints("fun f(): Nat { var t = 1 + (1 * 1 + 1) }")
        assertEquals(1, n, "#11 内层报 1 次")
        assertTrue(n == 1 && hs.single().pos.start.line == 1, "#11 位置存在")
    }

    /** #12:a + b * c <= d——比较算符不判，算术部分跨类照报 1。 */
    @Test fun t71_12_comparisonNotJudged() {
        val (n, _) = hints("fun f(): Bool { var t = 1 + 1 * 1 <= 1 }")
        assertEquals(1, n, "#12 比较算符不计入判定，算术部分照报")
    }

    /** 护栏：自定义中缀与区间/成员算符不进判定。 */
    @Test fun t71_customInfixNotJudged() {
        val (n, _) = hints("fun f(p: Nat, q: Nat): Nat { axiom m add n; var t = m add n }")
        assertEquals(0, n, "自定义中缀 add 不判")
    }

    /** 护栏：解析期无错、提示落在 diag 而非 notes；左结合 AST 由既有 ParserTest.`算术混优先级提示H_ARITH_PREC` 断言。 */
    @Test fun t71_hintIsDiagNotNote() {
        val r = parseSourceWithNotes("fun f(): Nat { var t = 1 + 1 * 1 }", "arith.subl")
        assertFalse(r.diag.any { it.severity == Severity.ERROR }, "解析期无错")
        assertFalse(r.notes.values.flatten().any { it.code == "H-ARITH-PREC" }, "提示在 diag，不混入 notes")
        assertTrue(r.diag.any { it.code == "H-ARITH-PREC" })
    }
}