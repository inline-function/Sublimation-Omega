package sublimation.frontend.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.SourcePos
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirExprStmt
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirIntLit
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirParam
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarExpr
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.json.HirJson
import sublimation.frontend.diag.Severity

/**
 * t108（R3 S1，C-8 裁「v3 按需加字段」）:HIR 推导元信息最小集验收。
 *
 * 范围 1+3:四载体（HirFunDecl retType 槽 / HirParam 形参+字段槽 / HirVarStmt 局部变量槽 /
 * HirVarExpr 表达位 var 槽）的 inferred 标记 + evidence 位；HirJson 按需发射——
 * **未带推导位的产物不得出现新字段**（按需验收尺,schema 保持 v3）。
 * 范围 2:unit.mode 落位核对——envelope 直读 scriptMode 真值（D-12 单源,供推导态闸读取）。
 * 精确语义（defaulted/provenance 等 C-10 四字段）不在本期。
 */
class T108DerivedMetaTest {
    private var next = 0
    private fun nid(): Int = ++next
    private fun pos(): SourceRange = SourceRange(SourcePos(1, 1), SourcePos(1, 2))
    private fun nat(): HirNamedType = HirNamedType("Nat", nid = nid(), origin = pos())
    private fun intLit(): HirIntLit = HirIntLit("1", nid = nid(), origin = pos())

    /** 推导位携带态:四载体全部按需发射（正例）。 */
    @Test fun t108_1_推导位四载体按需发射() {
        val file = HirFile(nid(), pos(), entries = listOf(
            HirDeclEntry(HirFunDecl(
                "f",
                params = listOf(
                    HirParam("a", type = nat(), nid = nid(), origin = pos(),
                        inferred = true, evidence = listOf("use:3:7", "use:5:2")),
                ),
                retType = nat(),
                body = intLit(),
                nid = nid(), origin = pos(),
                inferred = true, evidence = listOf("use:4:1"),
            )),
            HirStmtEntry(HirVarStmt("x", type = nat(), value = intLit(), nid = nid(), origin = pos(),
                inferred = true, evidence = listOf("use:2:3"))),
            HirStmtEntry(HirExprStmt(
                HirVarExpr("y", type = nat(), value = intLit(), nid = nid(), origin = pos(),
                    inferred = true),
                nid = nid(), origin = pos(),
            )),
        ))
        val json = HirJson.emit(file, DiagBag(), "t.subs", scriptMode = true)
        assertEquals(4, Regex(""""inferred": true""").findAll(json).count(),
            "四载体推导位全部发射 inferred:fun(retType)/参数/var/varExpr")
        assertTrue(Regex(""""use:3:7",\s*"use:5:2"""").containsMatchIn(json), "evidence 使用处位置列表原样保留（数组按缩进换行格式,按元素断言）")
        assertEquals(3, Regex(""""evidence": \[""").findAll(json).count(), "evidence 共 3 处（varExpr 仅 inferred 无 evidence）")
    }

    /** 默认态（未带推导位）:产物零新字段——按需验收尺的负例（68 样本保基线的机器可验形态）。 */
    @Test fun t108_2_默认态零新字段() {
        val file = HirFile(nid(), pos(), entries = listOf(
            HirDeclEntry(HirFunDecl("f",
                params = listOf(HirParam("a", type = nat(), nid = nid(), origin = pos())),
                retType = nat(), body = intLit(), nid = nid(), origin = pos())),
            HirStmtEntry(HirVarStmt("x", type = nat(), value = intLit(), nid = nid(), origin = pos())),
            HirStmtEntry(HirExprStmt(HirVarExpr("y", type = nat(), value = intLit(),
                nid = nid(), origin = pos()), nid = nid(), origin = pos())),
        ))
        val json = HirJson.emit(file, DiagBag(), "t.subl")
        assertFalse(json.contains("inferred"), "默认态不得出现 inferred 字段（按需验收尺）")
        assertFalse(json.contains("evidence"), "默认态不得出现 evidence 字段（按需验收尺）")
    }

    /** 仅 evidence 位（inferred=false）也发射:evidence 是独立的可追溯位。 */
    @Test fun t108_3_仅evidence位也发射() {
        val file = HirFile(nid(), pos(), entries = listOf(
            HirStmtEntry(HirVarStmt("x", type = nat(), value = intLit(), nid = nid(), origin = pos(),
                evidence = listOf("use:9:1"))),
        ))
        val json = HirJson.emit(file, DiagBag(), "t.subs", scriptMode = true)
        assertEquals(1, Regex(""""inferred": false""").findAll(json).count(),
            "evidence 非空时同播 inferred=false:分布信息可消费（默认态不会单独冒出 inferred）")
        assertTrue(json.contains(""""use:9:1""""), "evidence 独立发射（元素位：带 JSON 引号的元素形态）")
    }

    /** 范围 2:unit.mode 落位核对——envelope 直读 scriptMode 真值,推导态闸读同一真值（D-12 单源）。 */
    @Test fun t108_4_unit_mode真值单源落位() {
        // 默认 project:纯声明树 envelope
        val project = compileFrontend("fun f(): Nat = 1", "lib.subl")
        assertTrue(project.json.contains(""""mode": "project"""), "默认真值 → envelope.mode=project")
        // 脚本真值:同一源经 scriptMode 透传
        val script = compileFrontend("var x = 1", "t.subs", scriptMode = true)
        assertTrue(script.json.contains(""""mode": "script"""), "脚本真值 → envelope.mode=script")
        assertTrue(script.diag.diags.filter { it.severity == Severity.ERROR }.isEmpty(),
            "脚本真值下推导态闸放行顶层 var（闸读同一真值）")
        // 闸侧对称:默认 project 真值下,同一源被工程门控拒（真值不嗅探扩展名）
        val gate = compileFrontend("var x = 1", "t.subs")
        assertTrue(gate.json.contains(""""mode": "project"""), ".subs 默认真值仍 project（不嗅探扩展名）")
        assertFalse(gate.diag.diags.filter { it.severity == Severity.ERROR }.isEmpty(),
            "工程真值下顶层语句被门控拒（envelope 与闸同源）")
    }
}
