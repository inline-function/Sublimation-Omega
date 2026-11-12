package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirBorrow
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirNameRef
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.json.HirJson
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * t101（R3 S6 语法层）：&v 借用的 HIR/JSON 节点断言。
 * t128（S6 / R-21）后语义已激活：want 合成 @ref 衣 + D-REF-DISPATCH 留痕 + 只读形参位
 * W-BORROW-REDUNDANT warn（R-04.3）——本组锚节点形态/JSON 序列化不变，诊断按新口径断言。
 */
class BorrowInertTest {

    private val src = """
        fun double(n: Nat): Nat = n + n
        var x : Nat = 3
        var y : Nat = double(&x)
    """.trimIndent()

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "test.subl")
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

    private fun stmts(hir: HirFile) = hir.entries.mapNotNull { (it as? HirStmtEntry)?.stmt }

    @Test
    fun `t101_hir_1_借用节点产出且零错误诊断`() {
        val (hir, bag) = script(src)
        val errors = bag.diags.filter { it.severity == Severity.ERROR }
        assertTrue(errors.isEmpty(), "中间态不应落任何 ERROR（借用透明过境）：${errors.map { it.message }}")
        // t128（S6 / R-04.3 落地后）：只读形参（Nat）位点的借用落 W-BORROW-REDUNDANT warn——
        // want 合成 + 分派留痕在 HirBorrow.notes（D-REF-DISPATCH），唯此一条借用相关诊断
        val borrowDiags = bag.diags.filter { it.message.contains("借用") }
        assertEquals(1, borrowDiags.size, "仅 W-BORROW-REDUNDANT 一条：${borrowDiags.map { it.message }}")
        assertEquals("W-BORROW-REDUNDANT", borrowDiags.first().code)
        val y = stmts(hir).filterIsInstance<HirVarStmt>().first { it.name == "y" }
        val call = assertIs<sublimation.frontend.hir.HirCall>(y.value)
        val b = assertIs<HirBorrow>(call.args[0])
        assertIs<HirNameRef>(b.operand).also { assertEquals("x", it.name) }
        // 类型透传内层（零语义口径）
        assertEquals("Nat", (b.type as sublimation.frontend.hir.HirNamedType).name)
        // 分派留痕（want 合成 @ref 衣：Nat → @ref Nat —— 两格皆共享 ⇒ 冗余 warn 机械同源）
        val note = b.notes.firstOrNull { it.code == "D-REF-DISPATCH" }
        assertTrue(note != null && note.message.contains("借用实参 1 视图分派：Nat → @ref Nat"),
            "D-REF-DISPATCH 留痕：${b.notes.map { it.message }}")
    }

    @Test
    fun `t101_hir_2_借用变量为未定义名仍走既有通道`() {
        // &x 中 x 未声明：借用本身透明，未定义诊断由内层 NameRef 的既有 E-UNBOUND-NAME 通道落
        val (hir, bag) = script("fun f(n: Nat): Nat = n + n\nvar y : Nat = f(&q)")
        val unbound = bag.diags.filter { it.severity == Severity.ERROR && it.message.contains("q") }
        assertTrue(unbound.isNotEmpty(), "未定义名 q 须落既有 E-UNBOUND-NAME：${bag.diags.map { it.message }}")
    }

    @Test
    fun `t101_json_1_借用节点序列化`() {
        val (hir, bag) = script(src)
        val json = HirJson.emit(hir, bag, "test.subl")
        assertTrue(json.contains("\"schema\": \"sublimation/hir@v3\""), "schema 须仍为 v3（新增节点为附加项）")
        assertTrue(json.contains("\"kind\": \"HirBorrow\""), "产物须含 HirBorrow 节点头")
        assertTrue(json.contains("\"kind\": \"HirNameRef\""), "内层 NameRef 随行")
    }
}
