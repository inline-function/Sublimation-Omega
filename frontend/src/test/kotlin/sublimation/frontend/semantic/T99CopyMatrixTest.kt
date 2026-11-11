package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirBlock
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirExprStmt
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t99（N3 深拷贝矩阵 · R2 §三）：`documents/赋值语义矩阵.md` 的 16 格表 = t98 [TypeInfer.copyDispatch] 类型层判定
 * 的**机械对账**——每格一个可编译示例（验收 #2），格判定（共享/深拷/拒绝）与代码零矛盾（验收 #3）。
 *
 * - 留痕策略（t98）：`D-REF-DISPATCH` 仅在 **@ref 涉及**时落（@mut/T 侧产物零改动）⇒ 不涉 @ref 的 4 格
 *   判定在 copyDispatch 内（规则存在、HIR 不可观察），本组以「无错编译」锁定。
 * - 实际深拷随后端运行时（前端无运行时层，R2 §三）——本组只测类型层判定与诊断。
 */
class T99CopyMatrixTest {

    private val prelude = "@mut struct Box(@mut n: Nat)"

    /** 判定：share / copy / reject / none（无 @ref 涉及——留痕策略不落 note）。 */
    private fun judge(src: String): Triple<String, List<String>, String> {
        val pr = parseScriptSourceWithNotes(src, "t99.subl")
        val (hir, bag) = compile(pr.file, pr.notes)
        pr.diag.forEach { d ->
            when (d.severity) {
                Severity.ERROR -> bag.error(d.code, d.pos, d.message)
                Severity.WARN -> bag.warn(d.code, d.pos, d.message)
                Severity.HINT -> bag.hint(d.code, d.pos, d.message)
                Severity.SUPPLEMENT -> bag.supplement(d.code, d.pos, d.message)
            }
        }
        val errs = bag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
        val last = lastDispatchNote(hir)
        val judge = when {
            "E-TYPE-MISMATCH" in errs -> "reject"
            last.isEmpty() -> "none"
            last.contains("共享同一对象") -> "share"
            last.contains("深拷隔离") -> "copy"
            else -> "none"
        }
        return Triple(judge, errs, if (last.isEmpty()) "" else last)
    }

    private fun lastDispatchNote(hir: HirFile): String {
        val notes = mutableListOf<String>()
        fun walk(n: Any) {
            when (n) {
                is HirVarStmt -> notes += n.notes.filter { it.code == "D-REF-DISPATCH" }.map { it.message }
                is HirBlock -> n.stmts.forEach { walk(it) }
                is HirExprStmt -> walk(n.expr)
                is HirFunDecl -> n.body?.let { walk(it) }
                else -> { }
            }
        }
        hir.entries.forEach { en -> when (en) {
            is HirStmtEntry -> walk(en.stmt)
            is HirDeclEntry -> walk(en.decl)
            else -> { }
        } }
        return notes.lastOrNull() ?: ""
    }

    /** 验收 #1/#2/#3：16 格表驱动逐格对账（示例 = 文档表格逐格示例）。 */
    @Test fun 矩阵16格逐格对账() {
        data class Cell(val name: String, val body: String, val expect: String)
        val cells = listOf(
            Cell("T→T", "var t : Box = Box(0)\n var u : Box = t", "none"),
            Cell("T→@mut T", "var t : Box = Box(0)\n var u : @mut Box = t", "none"),
            Cell("T→@ref T", "var t : Box = Box(0)\n var u : @ref Box = t", "share"),
            Cell("T→@ref @mut T", "var t : Box = Box(0)\n var u : @ref @mut Box = t", "reject"),
            Cell("@mut T→T", "var a : @mut Box = Box(0)\n var u : Box = a", "none"),
            Cell("@mut T→@mut T", "var a : @mut Box = Box(0)\n var u : @mut Box = a", "none"),
            Cell("@mut T→@ref T", "var a : @mut Box = Box(0)\n var u : @ref Box = a", "share"),
            Cell("@mut T→@ref @mut T", "var a : @mut Box = Box(0)\n var u : @ref @mut Box = a", "share"),
            Cell("@ref T→T", "var a : @ref Box = Box(0)\n var u : Box = a", "copy"),
            Cell("@ref T→@mut T", "var a : @ref Box = Box(0)\n var u : @mut Box = a", "copy"),
            Cell("@ref T→@ref T", "var a : @ref Box = Box(0)\n var u : @ref Box = a", "share"),
            Cell("@ref T→@ref @mut T", "var a : @ref Box = Box(0)\n var u : @ref @mut Box = a", "reject"),
            Cell("@ref @mut T→T", "var a : @mut Box = Box(0)\n var b : @ref @mut Box = a\n var u : Box = b", "copy"),
            Cell("@ref @mut T→@mut T", "var a : @mut Box = Box(0)\n var b : @ref @mut Box = a\n var u : @mut Box = b", "copy"),
            Cell("@ref @mut T→@ref T", "var a : @mut Box = Box(0)\n var b : @ref @mut Box = a\n var u : @ref Box = b", "share"),
            Cell("@ref @mut T→@ref @mut T", "var a : @mut Box = Box(0)\n var b : @ref @mut Box = a\n var u : @ref @mut Box = b", "share"),
        )
        cells.forEach { c ->
            val (got, errs, note) = judge(prelude + "\n@impure fun main() {\n " + c.body.replace("\n", "\n ") + "\n}")
            assertEquals(c.expect, got, "格 ${c.name}:判定期望 ${c.expect} 实得 $got（note=$note）")
            when (c.expect) {
                "reject" -> assertEquals(listOf("E-TYPE-MISMATCH"), errs, "格 ${c.name}:拒绝格落 E-TYPE-MISMATCH")
                "none" -> assertTrue(errs.none { it.startsWith("E") }, "格 ${c.name}:无 @ref 涉及——无错编译")
                else -> assertTrue(errs.none { it.startsWith("E") }, "格 ${c.name}:可编译示例——错误须为空")
            }
        }
    }

    /** 验收 #3 补：留痕策略——纯 @mut/T 侧产物零 D-REF-DISPATCH（t98 回归保护）。 */
    @Test fun 留痕仅ref涉及时() {
        val (got, errs, _) = judge(prelude + "\n@impure fun main() { @mut var a = Box(0)\n a.n = 2\n var u : Box = a }")
        assertEquals("none", got, "@mut 侧不落分派留痕")
        assertTrue(errs.none { it.startsWith("E") }, "@mut σ 既有行为不变")
    }
}
