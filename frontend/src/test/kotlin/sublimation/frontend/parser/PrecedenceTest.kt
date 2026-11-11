package sublimation.frontend.parser

import sublimation.frontend.ast.*
import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * t82（ADR-50 §六/§八验收矩阵#1-#11):parseExpr 优先级爬升。层号：算术单层 10 / 关系与用户中缀 9 /
 * &∧8 / |∨7 / →6 右结合 / ↔5。矩阵 #12（全量）与 #13（68 样本 JSON) 由队长在干净树验收，此处不跑。
 */
class PrecedenceTest {

    /** 表达式体取 AST（解析层口径——未声明名不碍树形判定）。 */
    private fun ex(s: String): Expr {
        val r = parseSourceWithNotes("fun f(): Nat = $s", "t82.subl")
        assertFalse(r.diag.any { it.severity == Severity.ERROR }, "解析期无错：${r.diag.map { it.code }}")
        val d = r.file.entries.filterIsInstance<DeclEntry>().single().decl as FunDecl
        return d.body!!
    }

    private fun bin(e: Expr) = assertIs<BinExpr>(e)
    private fun call(e: Expr) = assertIs<CallExpr>(e)

    /** #1:x ∈ S → x >= l ⇒ (x ∈ S) → (x >= l)。 */
    @Test fun t82_1_relThenImp() {
        val e = ex("x ∈ S → x >= l")
        val imp = call(e)
        assertEquals("→", (imp.callee as NameRef).name)
        bin(imp.args[0]).let { assertEquals("∈", it.op) }
        bin(imp.args[1]).let { assertEquals(">=", it.op) }
    }

    /** #2:p ∨ q ∧ r ⇒ p ∨ (q ∧ r)。 */
    @Test fun t82_2_orOverAnd() {
        val e = ex("p ∨ q ∧ r")
        val or = call(e); assertEquals("∨", (or.callee as NameRef).name)
        val and = call(or.args[1]); assertEquals("∧", (and.callee as NameRef).name)
        assertEquals("q", (and.args[0] as NameRef).name)
    }

    /** #3(红线）:p → q → r ⇒ p → (q → r)——→ 唯一右结合。 */
    @Test fun t82_3_impRightAssoc() {
        val e = ex("p → q → r")
        val outer = call(e); assertEquals("→", (outer.callee as NameRef).name)
        assertEquals("p", (outer.args[0] as NameRef).name)
        val inner = call(outer.args[1]); assertEquals("→", (inner.callee as NameRef).name)
        assertEquals("q", (inner.args[0] as NameRef).name)
        assertEquals("r", (inner.args[1] as NameRef).name)
    }

    /** #4:p ↔ q → r ⇒ p ↔ (q → r)。 */
    @Test fun t82_4_iffOverImp() {
        val e = ex("p ↔ q → r")
        val iff = call(e); assertEquals("↔", (iff.callee as NameRef).name)
        val imp = call(iff.args[1]); assertEquals("→", (imp.callee as NameRef).name)
    }

    /** #5:a & b | c ⇒ (a & b) | c。 */
    @Test fun t82_5_andOverOr() {
        val e = ex("a & b | c")
        val or = bin(e); assertEquals("|", or.op)
        val and = bin(or.left); assertEquals("&", and.op)
        assertEquals("c", (or.right as NameRef).name)
    }

    /** #6(红线）:a + b * c ⇒ (a + b) * c——算术层内无优先级，H-ARITH-PREC 提示保留。 */
    @Test fun t82_6_arithSingleLevel() {
        val r = parseSourceWithNotes("fun f(): Nat = a + b * c", "t82.subl")
        val e = (r.file.entries.filterIsInstance<DeclEntry>().single().decl as FunDecl).body!!
        val mul = bin(e); assertEquals("*", mul.op)
        val plus = bin(mul.left); assertEquals("+", plus.op)
        assertTrue(r.diag.any { it.code == "H-ARITH-PREC" }, "层内混类提示保留：${r.diag.map { it.code }}")
    }

    /** #7:x + 1 >= l ⇒ (x + 1) >= l;#8:x >= l + 1 ⇒ x >= (l + 1)。 */
    @Test fun t82_7_8_arithOverRel() {
        val e7 = ex("x + 1 >= l")
        val g7 = bin(e7); assertEquals(">=", g7.op); assertEquals("+", bin(g7.left).op)
        val e8 = ex("x >= l + 1")
        val g8 = bin(e8); assertEquals(">=", g8.op); assertEquals("+", bin(g8.right).op)
    }

    /** #9:(x ∈ S) → (x >= l) 括号形态与 #1 树同构。 */
    @Test fun t82_9_parenSameShape() {
        val e = ex("(x ∈ S) → (x >= l)")
        val imp = call(e); assertEquals("→", (imp.callee as NameRef).name)
        bin(imp.args[0]).let { assertEquals("∈", it.op) }
        bin(imp.args[1]).let { assertEquals(">=", it.op) }
    }

    /** #10:p ⊗ q → r(⊗ 用户中缀）⇒ (p ⊗ q) → r。 */
    @Test fun t82_10_userInfixRel() {
        val e = ex("p ⊗ q → r")
        val imp = call(e); assertEquals("→", (imp.callee as NameRef).name)
        val otimes = bin(imp.args[0]); assertEquals("⊗", otimes.op)
    }

    /** #11:¬<p> ∧ q ⇒ (¬<p>) ∧ q。 */
    @Test fun t82_11_negPrefix() {
        val e = ex("¬<p> ∧ q")
        val and = call(e); assertEquals("∧", (and.callee as NameRef).name)
        assertIs<UniExpr>(and.args[0]).let { assertEquals("¬", it.op) }
        assertEquals("q", (and.args[1] as NameRef).name)
    }
}