package sublimation.frontend.parser

import sublimation.frontend.ast.BinExpr
import sublimation.frontend.ast.BorrowExpr
import sublimation.frontend.ast.CallExpr
import sublimation.frontend.ast.NameRef
import sublimation.frontend.ast.StmtEntry
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * t101（R3 S6 语法层，R-01/R-03/R-04）：前缀 & 借用——参数表达式起始位、脚本模式专属、零语义。
 * 中缀 & 合取（样本模式匹配.subl:35）逐字不变；本组只测前缀形态与拒绝位。
 */
class BorrowSyntaxTest {

    private fun stmtExpr(src: String, i: Int = 0): sublimation.frontend.ast.Expr {
        val r = parseScriptSourceWithNotes(src, "t.subl")
        assertTrue(r.diag.none { it.severity == Severity.ERROR }, "脚本不应有解析错：${r.diag.map { it.message }}")
        return (r.file.entries[i] as StmtEntry).stmt.let { (it as sublimation.frontend.ast.ExprStmt).expr }
    }

    @Test
    fun `t101_1_借用变量形态产BorrowExpr`() {
        val e = stmtExpr("f(&x)")
        val c = assertIs<CallExpr>(e)
        val b = assertIs<BorrowExpr>(c.args[0])
        assertIs<NameRef>(b.operand).also { assertEquals("x", it.name) }
    }

    @Test
    fun `t101_2_多参数借用与普通实参并存`() {
        val c = assertIs<CallExpr>(stmtExpr("f(&x, y, &z)"))
        assertIs<BorrowExpr>(c.args[0])
        assertIs<NameRef>(c.args[1])
        assertIs<BorrowExpr>(c.args[2])
    }

    @Test
    fun `t101_3_嵌套调用的内层借用`() {
        val outer = assertIs<CallExpr>(stmtExpr("g(f(&x))"))
        val inner = assertIs<CallExpr>(outer.args[0])
        assertIs<BorrowExpr>(inner.args[0])
    }

    @Test
    fun `t101_4_命名实参值位借用`() {
        val c = assertIs<CallExpr>(stmtExpr("f(a = &x)"))
        val b = assertIs<BorrowExpr>(c.namedArgs["a"])
        assertIs<NameRef>(b.operand)
    }

    @Test
    fun `t101_5_中缀合取不产借用节点`() {
        // 样本形态（模式匹配.subl:35 的合取通道）：整体是 BinExpr，无 BorrowExpr
        val e = stmtExpr("p(a) & q(b)")
        val bin = assertIs<BinExpr>(e)
        assertEquals("&", bin.op)
        assertIs<CallExpr>(bin.left)
        assertIs<CallExpr>(bin.right)
    }

    @Test
    fun `t101_6_借用实参与外层中缀合取并存`() {
        val bin = assertIs<BinExpr>(stmtExpr("f(&x) & g(y)"))
        val left = assertIs<CallExpr>(bin.left)
        assertIs<BorrowExpr>(left.args[0])
    }

    @Test
    fun `t101_neg1_工程模式拒绝且出路指向@ref`() {
        val r = parseSourceWithNotes("fun f(): Null { g(&x) }", "t.subl")
        val d = r.diag.firstOrNull { it.severity == Severity.ERROR && it.message.contains("工程模式") }
        assertTrue(d != null, "工程模式前缀 & 须落 E-SYNTAX：${r.diag.map { it.message }}")
        assertTrue(d.message.contains("@ref"), "诊断出路须指向 @ref 类型注解：${d.message}")
    }

    @Test
    fun `t101_neg2_表达式位借用拒绝`() {
        val r = parseScriptSourceWithNotes("f(&(a + 1))", "t.subl")
        assertTrue(
            r.diag.any { it.severity == Severity.ERROR && it.message.contains("只能作用于变量") },
            "&(a+1) 须落仅变量拒绝：${r.diag.map { it.message }}"
        )
    }

    @Test
    fun `t101_neg3_双符号双重借用拒绝`() {
        val r = parseScriptSourceWithNotes("f(&&x)", "t.subl")
        assertTrue(
            r.diag.any { it.severity == Severity.ERROR && it.message.contains("双重借用") },
            "&& 须落双重借用拒绝：${r.diag.map { it.message }}"
        )
    }

    @Test
    fun `t101_neg4_空格分开的双重借用拒绝`() {
        val r = parseScriptSourceWithNotes("f(& &x)", "t.subl")
        assertTrue(
            r.diag.any { it.severity == Severity.ERROR && it.message.contains("双重借用") },
            "& & 须落双重借用拒绝：${r.diag.map { it.message }}"
        )
    }
}
