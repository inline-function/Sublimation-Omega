package sublimation.frontend.parser

import sublimation.frontend.ast.*
import sublimation.frontend.diag.Severity
import sublimation.frontend.semantic.compile
import sublimation.frontend.ast.NameRef
import sublimation.frontend.ast.UniExpr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * t78（ADR-51，用户裁决 Q15/Q21):not 删除 + `&&`/`||` 退役。**t83 终态收紧**:
 * - `not`：回落普通标识符 ⇒ E-UNBOUND-NAME;
 * - `&&`/`||`:W-OP-RETIRED 警告态 ⇒ **E-OP-RETIRED 拒绝**（Parser 中缀循环顶即拒、产 ErrorExpr 占位；
 *   Lexer 停止 ∧/∨ 归一、保留 LAND/LOR 仅作拒绝锚点、文本即用户写法）。
 *   原 W 族码随之退役（t78 第 1 半支撑态的闭合——W→E 由本任务落地）。
 */
class OpRetirementTest {

    private fun src(expr: String) = parseSourceWithNotes("fun f(p: Bool, q: Bool): Bool = $expr", "op.subl")

    private fun retired(r: ParseResult) = r.diag.filter { it.code == "E-OP-RETIRED" }

    private fun body(r: ParseResult) = (r.file.entries.filterIsInstance<DeclEntry>().single().decl as FunDecl).body

    /** #1:not 删除——回落普通标识符：AST 是 NameRef("not")(不再产 UniExpr ¬),语义层落 E-UNBOUND-NAME。 */
    @Test fun t78_1_notAliasRemoved() {
        val r = src("not p")
        val d = r.file.entries.filterIsInstance<DeclEntry>().single().decl as FunDecl
        val b = StringBuilder()
        fun walk(e: Any?) { when (e) { is UniExpr -> b.append(e.op); is CallExpr -> { walk(e.callee); e.args.forEach(::walk) } is Expr -> {} else -> {} } }
        walk(d.body)
        assertFalse(b.toString().contains("¬"), "#1 AST 内不得再有 UniExpr ¬,actual=$b / body=$d")
        val pr = parseScriptSourceWithNotes("fun f(p: Bool): Bool = not p", "op.subl")
        val (hir, bag) = compile(pr.file, pr.notes)
        pr.diag.forEach { x -> if (x.severity == Severity.ERROR) bag.error(x.code, x.pos, x.message) }
        val unbound = bag.diags.firstOrNull { it.code == "E-UNBOUND-NAME" }
        assertTrue(unbound != null && unbound.message.contains("`not`"), "#1 not 应落 E-UNBOUND-NAME,actual=${bag.diags.map { it.code }}")
    }

    /**
     * #2(t83):&& 终态拒绝——E-OP-RETIRED（指出用户写法 && 与替代 &)，**不可编译**（E 族）。
     * BEFORE(t78):W-OP-RETIRED 警告态、仍产 BinExpr ∧。AFTER(t83):循环顶即拒、ErrorExpr 占位。
     */
    @Test fun t83_2_andRetiredRejects() {
        val r = src("p && q")
        val es = retired(r)
        assertEquals(1, es.size, "#2 恰 1 条 E-OP-RETIRED")
        val msg = es.single().message
        assertTrue(msg.contains("&&"), "#2 文案指出用户实际写法 &&:$msg")
        assertTrue(msg.contains("`&`"), "#2 文案指出替代算符 &:$msg")
        assertEquals(Severity.ERROR, es.single().severity, "#2 E 族错误")
        assertIs<ErrorExpr>(body(r), "#2 表达式体回落 ErrorExpr 占位")
    }

    /** #3(t83):|| 终态拒绝——同 &&。 */
    @Test fun t83_3_orRetiredRejects() {
        val r = src("p || q")
        val es = retired(r)
        assertEquals(1, es.size, "#3 恰 1 条 E-OP-RETIRED")
        val msg = es.single().message
        assertTrue(msg.contains("||") && msg.contains("`|`"), "#3 文案指出 || 与替代 |:$msg")
        assertIs<ErrorExpr>(body(r), "#3 表达式体回落 ErrorExpr 占位")
    }

    /** #4:& / |:行为不变——通过且无 E-OP-RETIRED。 */
    @Test fun t78_4_ampPipeKept() {
        for (e in listOf("p & q", "p | q")) {
            val r = src(e)
            assertEquals(0, retired(r).size, "#4 $e 不落 E-OP-RETIRED")
            assertFalse(r.diag.any { it.severity == Severity.ERROR }, "#4 $e 通过：${r.diag.map { it.code }}")
        }
    }

    /** #5:! 行为不变。 */
    @Test fun t78_5_bangKept() {
        val r = src("!p")
        assertEquals(0, retired(r).size, "#5 ! 不落 E-OP-RETIRED")
        assertFalse(r.diag.any { it.severity == Severity.ERROR }, "#5 !p 通过")
    }

    /** #6:命题层 ∧ ∨ 不受影响（未声明探针：算符仍是命题连接词，不落 E-OP-RETIRED)。 */
    @Test fun t78_6_propLayerUnaffected() {
        val r = parseSourceWithNotes("fun f(p: Bool, q: Bool): Null <p ∧ q> {}", "op.subl")
        assertEquals(0, retired(r).size, "命题层 ∧ 不落 E-OP-RETIRED")
        assertFalse(r.diag.any { it.severity == Severity.ERROR && it.code != "E-SYNTAX" }, "命题层诊断面未变")
    }
}