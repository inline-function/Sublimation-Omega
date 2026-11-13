package sublimation.frontend.semantic

import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirMutType
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import sublimation.frontend.semantic.compileWithRounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * t125 S5b（R-06 脚本模式 @mut 自动提升）。
 *
 * - **T1 局部二次赋值** / **T2 形参被赋值** ⇒ 单调提升（SymbolEntry.mutable + 视图 @mut）;
 * - 机制：赋值位登记需求（scriptPromotable 候选过滤——字段 C-5/循环变量/lambda 形参不提升）
 *   ⇒ 驱动器**单轮预应用重跑**（mut-promoted 轮：声明先于赋值、单调 ⇒ 一轮收敛，轮次确定性）;
 * - D-MUT-INFERRED 审计四元组（名 + 提升视图 + 触发分类 + 证据清单）;
 * - 工程模式零通道（声明制——E-IMMUT-ASSIGN 逐字不变，t8/t98 冻结测保护）;
 * - C-5：字段层不提升（写位仍落 E-IMMUT-ASSIGN）。
 */
class T125MutPromotionTest {

    private fun scriptRounds(src: String) =
        parseScriptSourceWithNotes(src, "t125b.subs").let { compileWithRounds(it.file, it.notes, scriptMode = true) }

    private fun projRounds(src: String) =
        parseSourceWithNotes(src, "t125b.subl").let { compileWithRounds(it.file, it.notes, scriptMode = false) }

    private fun errCodes(rs: RoundsReport): List<String> = rs.diag.diags.map { it.code }

    private fun inferRender(t: sublimation.frontend.hir.HirType): String {
        // 视图渲染（忽略 nid/origin——只比对名字与形态）
        return render(t).replace(" ", "")
    }

    private fun render(t: sublimation.frontend.hir.HirType): String = when (t) {
        is sublimation.frontend.hir.HirNamedType ->
            t.name + (if (t.args.isEmpty()) "" else "[" + t.args.joinToString(",") { render(it) } + "]")
        is sublimation.frontend.hir.HirMutType -> "@mut " + render(t.inner)
        is sublimation.frontend.hir.HirRefType -> "@ref " + render(t.inner)
        else -> t.toString()
    }

    private fun varStmtOf(hir: HirFile, name: String): HirVarStmt? =
        hir.entries.mapNotNull { (it as? HirStmtEntry)?.stmt as? HirVarStmt }.firstOrNull { it.name == name }

    /** R-28 击穿 R-06 提升写回（队长冲突点 1，待裁）：提升后 x 视图 @mut Nat，
     *  RHS plain Nat → PLAIN→MUT 拒格——提升机制结构证据保留，写回落点翻转。 */
    @Test fun T1局部二次赋值提升() {
        val rs = scriptRounds("var x = 1\nx = x + 1\nprint(x)\n")
        val es = errCodes(rs).filter { it.startsWith("E-") }
        assertEquals(listOf("E-TYPE-MISMATCH"), es, "R-28: 提升后写回 PLAIN→MUT 拒格（旧隐式深拷废止）")
        assertTrue(rs.mutPromoted, "mut-promoted 重跑发生（R-06 提升机制本身保持）")
        val v = varStmtOf(rs.hir, "x")
        assertNotNull(v)
        assertTrue(v.type is HirMutType, "var 视图提升为 @mut Nat")
        val inner = (v.type as HirMutType).inner as? sublimation.frontend.hir.HirNamedType
        assertNotNull(inner)
        assertEquals("Nat", inner.name, "视图内层为 Nat（R-06 提升视图 @mut Nat）")
    }

    @Test fun D_MUT_INFERRED四元组落袋() {
        val rs = scriptRounds("var x = 1\nx = x + 1\n")
        val d = rs.diag.diags.filter { it.code == "D-MUT-INFERRED" }
        assertEquals(1, d.size, "D-MUT-INFERRED 恰一条（变量 x）")
        val msg = d[0].message
        assertTrue(msg.contains("x"), "四元组①名")
        assertTrue(msg.contains("@mut"), "四元组②提升视图")
        assertTrue(msg.contains("T1"), "四元组③触发分类")
        assertTrue(msg.contains("use:"), "四元组④证据尝试清单")
    }

    /** R-28 fallout（队长冲突点 1）：提升后形参视图 @mut Nat，body 写回 + 调用位 plain 实参双双落拒格。 */
    @Test fun T2形参被赋值提升() {
        val rs = scriptRounds("fun f(a) {\n  a = a + 1\n  return a\n}\nprint(f(1))\n")
        val es = errCodes(rs).filter { it.startsWith("E-") }
        assertTrue(es.isNotEmpty() && es.all { it == "E-TYPE-MISMATCH" }, "R-28: 提升形参写回 + plain 实参落 E-TYPE-MISMATCH（旧深拷废止）: $es")
        val f = rs.hir.entries.mapNotNull { (it as? sublimation.frontend.hir.HirDeclEntry)?.decl as? sublimation.frontend.hir.HirFunDecl }.first { it.name == "f" }
        val pt = f.params.first().type
        assertTrue(pt is HirMutType, "形参视图提升 @mut（R-06：提升出的 @mut 即形参推导类型）")
        // 名称引用结论侧亦同步（调用位按 R-01 表深拷核）
    }

    @Test fun 工程模式零通道声明制() {
        val rs = projRounds("@impure fun main(): Null { var x = 1\n x = x + 1\n print(x) }")
        assertTrue(errCodes(rs).contains("E-IMMUT-ASSIGN"), "工程模式重赋值逐字 E-IMMUT-ASSIGN（R-06 工程侧）")
        assertFalse(rs.mutPromoted, "工程模式 mutPromotions 恒空")
    }

    @Test fun C5字段不提升() {
        val rs = scriptRounds("@mut struct Box(@mut n: Nat)\nvar b = Box(0)\nb.n = 1\n")
        assertTrue(errCodes(rs).contains("E-IMMUT-ASSIGN"), "字段写位不可提升（C-5 声明制）")
    }

    @Test fun 混标单向蕴含() {
        val rs = scriptRounds("@mut var x = 1\nx = x + 1\nprint(x)\n")
        assertEquals(emptyList(), errCodes(rs).filter { it.startsWith("E-") }, "已 @mut 声明直接可写（无需求、无重跑）")
        // 提升通道不激活——符号 mutable 已 true（混标单向蕴含）
    }

    @Test fun 轮次确定性() {
        val src = "var x = 1\nx = x + 1\nfun f(a) { a = a + 1; return a }\nprint(f(x))\n"
        val a = scriptRounds(src)
        val b = scriptRounds(src)
        assertEquals(a.rounds.map { it.label }, b.rounds.map { it.label }, "轮次标签序列逐字相同")
        assertEquals(a.roundsUsed, b.roundsUsed)
    }

    @Test fun 差分对拍同源双门_脚本提升工程拒绝() {
        val pairDir = java.io.File("frontend/src/test/resources/diff-pairs")
        val src = pairDir.resolve("mut-promote.subs").readText()
        val rs = parseScriptSourceWithNotes(src, "mut-promote.subs").let { compileWithRounds(it.file, it.notes, scriptMode = true) }
        val rp = parseSourceWithNotes(pairDir.resolve("mut-promote.subl").readText(), "mut-promote.subl").let { compileWithRounds(it.file, it.notes, scriptMode = false) }
        // R-28 fallout（队长冲突点 1）：脚本提升后写回落拒格（旧零错误基线废止）
        assertTrue(errCodes(rs).filter { it.startsWith("E-") }.isNotEmpty(), "脚本侧 R-28 拒格落点（提升写回 PLAIN→MUT）")
        assertTrue(errCodes(rp).contains("E-IMMUT-ASSIGN"), "工程侧同源拒绝位（声明制）：未标 @mut 的形参/局部写落 E-IMMUT-ASSIGN")
        assertTrue(rs.mutPromoted, "脚本侧 mut-promoted 重跑")
        assertFalse(rp.mutPromoted, "工程侧零提升通道")
        // 形参提升视图（推导位 vs 工程拒绝位的分型证据）
        val pf = rs.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? sublimation.frontend.hir.HirFunDecl }.firstOrNull { it.name == "bump" }
        assertNotNull(pf)
        assertTrue(pf.params.first().type is HirMutType, "脚本侧形参 a 提升视图 @mut Nat")
    }

    @Test fun 差分对拍标注等价_视图同() {
        val pairDir = java.io.File("frontend/src/test/resources/diff-pairs")
        val rs = parseScriptSourceWithNotes(pairDir.resolve("mut-promote-eq.subs").readText(), "mut-promote-eq.subs")
            .let { compileWithRounds(it.file, it.notes, scriptMode = true) }
        val rp = parseSourceWithNotes(pairDir.resolve("mut-promote-eq.subl").readText(), "mut-promote-eq.subl")
            .let { compileWithRounds(it.file, it.notes, scriptMode = false) }
        // R-28 fallout：subs 侧提升写回落拒格（冲突点 1）;subl 工程标注位（@mut Nat 形参）无 plain 调用源亦拒
        assertTrue(errCodes(rs).filter { it.startsWith("E-") }.isNotEmpty(), "subs 侧 R-28 拒格落点")
        assertTrue(errCodes(rp).filter { it.startsWith("E-") }.isNotEmpty(), "subl 侧 R-28 拒格落点")
        val fs = rs.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? sublimation.frontend.hir.HirFunDecl }.first()
        val fp = rp.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? sublimation.frontend.hir.HirFunDecl }.first()
        assertEquals(inferRender(fp.params.first().type!!), inferRender(fs.params.first().type!!),
            "形参视图同：脚本推导位与工程标注位结构等价（@mut Nat）")
    }
}
