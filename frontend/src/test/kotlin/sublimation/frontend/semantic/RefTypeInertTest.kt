package sublimation.frontend.semantic

import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirMutType
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirRefType
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.json.HirJson
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * t95（N2 @ref 语法层）：HIR 产物与 JSON 序列化断言（@ref 可解析、产物登记）。
 * 语义层四规则在 t98 —— t98 后本组核对「语法产物形态」与「t98 语义落点」:
 * @mut Box → @ref Box/@ref @mut Box 皆共享放行（根文档 :113/:120）;不可变 Box →
 * @ref @mut Box 落 E-TYPE-MISMATCH（不可变值发可写引用不安全，t98 规则 2 健全性）。
 */
class RefTypeInertTest {

    // R-28：@mut 类型位不可由 plain 构造——o 改 @mut var 绑定（view : @ref Box 共享格形态不变）
    private val src = """
        @mut struct Box(@mut n: Nat)
        @mut var o = Box(0)
        var view : @ref Box = o
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
    fun `t95_hir_1_ref类型节点产出`() {
        val (hir, bag) = script(src)
        val errors = bag.diags.filter { it.severity == Severity.ERROR }
        assertTrue(errors.isEmpty(), "@mut Box → @ref Box 不可变视图共享放行：${errors.map { it.message }}")
        val view = stmts(hir).filterIsInstance<HirVarStmt>().first { it.name == "view" }
        val ty = assertIs<HirRefType>(view.type)
        assertEquals("Box", (ty.inner as HirNamedType).name)
    }

    /**
     * 叠加形态 @ref @mut T 的节点结构与类型链登记（t98 语义两态）。
     */
    @Test
    fun `t98_hir_2_refMut叠加形态`() {
        // R-28 fallout（队长冲突点 2）：工程模式 @mut Box 无合法构造——@mut var 绑定得 plain Box，
        //  var view : @ref @mut Box = o 落 PLAIN→REFMUT 拒格（旧深拷放行格废止）
        val (hir, bag) = script("""
            @mut struct Box(@mut n: Nat)
            @mut var o = Box(0)
            var view : @ref @mut Box = o
        """.trimIndent())
        val errors = bag.diags.filter { it.severity == Severity.ERROR }
        assertTrue(errors.map { it.code } == listOf("E-TYPE-MISMATCH"), "@mut 类型位失效 → PLAIN→REFMUT 拒格（R-28）：${errors.map { it.message }}")
        val view = stmts(hir).filterIsInstance<HirVarStmt>().first { it.name == "view" }
        val outer = assertIs<HirRefType>(view.type)
        assertIs<HirMutType>(outer.inner)
        val (b2, bag2) = script("""
            @mut struct Box(@mut n: Nat)
            var o : Box = Box(0)
            var view : @ref @mut Box = o
        """.trimIndent())
        val errs2 = bag2.diags.filter { it.severity == Severity.ERROR }
        assertEquals(1, errs2.size, "不可变 → @ref @mut 须落 1 条 ERROR：${errs2.map { it.message }}")
        assertEquals("E-TYPE-MISMATCH", errs2.first().code)
        val view2 = stmts(b2).filterIsInstance<HirVarStmt>().first { it.name == "view" }
        assertIs<HirRefType>(view2.type)
    }

    @Test
    fun `t95_json_1_ref节点序列化`() {
        val (hir, bag) = script(src)
        val json = HirJson.emit(hir, bag, "test.subl")
        assertTrue(json.contains("\"schema\": \"sublimation/hir@v3\""), "schema 须仍为 v3（新增节点为附加项）")
        assertTrue(json.contains("\"kind\": \"HirRefType\""), "产物须含 HirRefType 节点头")
        assertTrue(json.contains("\"kind\": \"HirNamedType\""), "内层具名类型节点随行")
    }
}
