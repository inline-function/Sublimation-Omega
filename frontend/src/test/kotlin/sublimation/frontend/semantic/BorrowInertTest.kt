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
 * t101（R3 S6 语法层）：&v 借用的 HIR/JSON **中间态**断言——零语义（可解析、无效果）。
 * 视图推导/冗余 warn/copyDispatch 联动（R-01 want 合成 @ref）是 t96/t99 的域，本组不测。
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
    fun `t101_hir_1_借用节点产出且零诊断`() {
        val (hir, bag) = script(src)
        val errors = bag.diags.filter { it.severity == Severity.ERROR }
        assertTrue(errors.isEmpty(), "中间态不应落任何 ERROR（借用透明过境）：${errors.map { it.message }}")
        // t96 的冗余 warn 属后续阶段——本期不出任何借用相关诊断
        assertTrue(bag.diags.none { it.message.contains("借用") }, "零语义中间态：不应有借用相关诊断")
        val y = stmts(hir).filterIsInstance<HirVarStmt>().first { it.name == "y" }
        val call = assertIs<sublimation.frontend.hir.HirCall>(y.value)
        val b = assertIs<HirBorrow>(call.args[0])
        assertIs<HirNameRef>(b.operand).also { assertEquals("x", it.name) }
        // 类型透传内层（零语义口径）
        assertEquals("Nat", (b.type as sublimation.frontend.hir.HirNamedType).name)
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
