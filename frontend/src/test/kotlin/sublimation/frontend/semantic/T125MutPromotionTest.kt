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
 * t125 S5b（R-06 脚本模式 @mut 自动提升）—— **R-31（t163）重裁**：提升改走**声明位通道**。
 *
 * - **T1 局部二次赋值** / **T2 形参被赋值** ⇒ 单调提升——提升产出=**声明位槽位可变标记**
 *   （SymbolEntry.mutable，同 @mut var 记账），**类型保持 plain T**（不再合成 @mut 视图类型;
 *   t150 翻转的视图合成通道作废，本件 4 测翻回绿态）;
 * - 语义后果：提升槽位的写回=**属主写**（写闸走符号 mutable 位），不进 R-28 copyDispatch 矩阵
 *   （矩阵只管值位/子类型转换的跨变量伪造——矩阵拒格零回归见 T125WriteGate/T150）;
 * - 机制保持：赋值位登记需求（scriptPromotable 候选过滤）⇒ 驱动器**单轮预应用重跑**
 *   （mut-promoted 轮：声明先于赋值、单调 ⇒ 一轮收敛，轮次确定性）;
 * - D-MUT-INFERRED 审计四元组（名 + 提升视图 @mut + 触发分类 + 证据清单）——@mut 视图仅入审计渲染;
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

    /** R-31（t163）：T1 提升翻回绿——声明位通道（符号 mutable，类型 plain）⇒ 写回=属主写，
     *  不进 R-28 矩阵（PLAIN→MUT 拒格不再适用于提升槽位的属主写）。 */
    @Test fun T1局部二次赋值提升() {
        val rs = scriptRounds("var x = 1\nx = x + 1\nprint(x)\n")
        val es = errCodes(rs).filter { it.startsWith("E-") }
        assertEquals(emptyList(), es, "R-31: 提升槽位写回=属主写，零 E-TYPE-MISMATCH（矩阵拒格零回归见 T125WriteGate）")
        assertTrue(rs.mutPromoted, "mut-promoted 重跑发生（R-06 提升机制本身保持）")
        val v = varStmtOf(rs.hir, "x")
        assertNotNull(v)
        // R-31：类型保持 plain（声明位可变标记入 SymbolEntry——写回成立仅可由符号 mutable 解释）
        val nt = v.type as? sublimation.frontend.hir.HirNamedType
        assertNotNull(nt, "提升槽位类型保持 plain（R-31 声明位通道——不再合成 @mut 视图）")
        assertEquals("Nat", nt.name, "var 推导类型为 Nat（视图 @mut 仅入 D-MUT-INFERRED 审计渲染）")
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

    /** R-31（t163）：T2 形参提升翻回绿——形参槽位走声明位通道（符号 mutable，类型 plain）：
     *  body 写回=属主写（零 E-TYPE-MISMATCH），调用位 plain 实参按 plain 形参传递。
     *  R-06 原话「提升出的 @mut 即形参推导类型」与声明位通道口径相反——如实记录冲突，
     *  R-31 裁决下 T2 形参类型保持 plain（未硬并，@mut 视图仅入审计渲染）。 */
    @Test fun T2形参被赋值提升() {
        val rs = scriptRounds("fun f(a) {\n  a = a + 1\n  return a\n}\nprint(f(1))\n")
        val es = errCodes(rs).filter { it.startsWith("E-") }
        assertEquals(emptyList(), es, "R-31: 提升形参写回=属主写 + plain 实参按 plain 形参传递（零拒格）: $es")
        val f = rs.hir.entries.mapNotNull { (it as? sublimation.frontend.hir.HirDeclEntry)?.decl as? sublimation.frontend.hir.HirFunDecl }.first { it.name == "f" }
        val nt = f.params.first().type as? sublimation.frontend.hir.HirNamedType
        assertNotNull(nt, "形参类型保持 plain（R-31 声明位通道）")
        assertEquals("Nat", nt.name, "形参推导类型 plain Nat（可变标记入 SymbolEntry）")
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
        // R-31（t163）：脚本提升翻绿——声明位通道（符号 mutable，类型 plain），写回/调用零拒格
        assertEquals(emptyList(), errCodes(rs).filter { it.startsWith("E-") }, "脚本侧提升绿态（属主写零 E）")
        assertTrue(errCodes(rp).contains("E-IMMUT-ASSIGN"), "工程侧同源拒绝位（声明制）：未标 @mut 的形参/局部写落 E-IMMUT-ASSIGN")
        assertTrue(rs.mutPromoted, "脚本侧 mut-promoted 重跑")
        assertFalse(rp.mutPromoted, "工程侧零提升通道")
        // 声明位通道分型证据：脚本侧形参类型 plain（可变标记入符号）
        val pf = rs.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? sublimation.frontend.hir.HirFunDecl }.firstOrNull { it.name == "bump" }
        assertNotNull(pf)
        val pnt = pf.params.first().type as? sublimation.frontend.hir.HirNamedType
        assertNotNull(pnt, "脚本侧形参 a 类型 plain（R-31 声明位通道）")
        assertEquals("Nat", pnt.name)
    }

    @Test fun 差分对拍标注等价_视图同() {
        val pairDir = java.io.File("frontend/src/test/resources/diff-pairs")
        val rs = parseScriptSourceWithNotes(pairDir.resolve("mut-promote-eq.subs").readText(), "mut-promote-eq.subs")
            .let { compileWithRounds(it.file, it.notes, scriptMode = true) }
        val rp = parseSourceWithNotes(pairDir.resolve("mut-promote-eq.subl").readText(), "mut-promote-eq.subl")
            .let { compileWithRounds(it.file, it.notes, scriptMode = false) }
        // R-31（t163）：等价口径改**行为等价**（可写槽位）——subs 推导位（声明位标记，类型 plain）
        //  与 subl 标注位（@mut Nat 类型视图）同为可写形参;subl 侧 plain 实参→@mut 形参仍落
        //  R-28 拒格（矩阵零回归），subs 侧 plain→plain 零拒格（提升绿态恢复）
        assertEquals(emptyList(), errCodes(rs).filter { it.startsWith("E-") }, "subs 侧提升绿态（R-31）")
        assertTrue(errCodes(rp).contains("E-TYPE-MISMATCH"), "subl 侧矩阵拒格零回归（plain 实参 → @mut 形参）")
        val fs = rs.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? sublimation.frontend.hir.HirFunDecl }.first()
        val fp = rp.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? sublimation.frontend.hir.HirFunDecl }.first()
        assertEquals("Nat", (fs.params.first().type as sublimation.frontend.hir.HirNamedType).name,
            "subs 形参 plain Nat（声明位标记）")
        assertEquals("@mutNat", inferRender(fp.params.first().type!!),
            "subl 标注位 @mut Nat（两侧行为等价：同为可写形参槽位;inferRender 去空格口径）")
    }
}
