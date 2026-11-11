package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirNameRef
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirPBot
import sublimation.frontend.hir.HirPImp
import sublimation.frontend.hir.HirProp
import sublimation.frontend.hir.HirPTop
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t79（用户裁决 Q12 + Q20，2026-11-09）：Bool 字面量 True / False；⊤ / ⊥ 命题专用。
 *
 * ## 裁决口径
 * - True / False 为唯一 Bool 字面量写法（大写）；HIR 形态与旧 true/false 字面量同构（值位
 *   HirNameRef + 内建符号 + Bool 类型——唯名差异）。t84（Q12 收口）：小写 true/false 已拒收——
 *   未声明 ⇒ E-UNBOUND-NAME + 大写写法提示（NameResolver t84，ADR-48 §八可编译提示）。
 * - ⊤ / ⊥ 是命题关键词（恒真/恒假命题）：**值位**（表达式/实参/返回/模式）复用 E-PROP-POS
 *   （「命题出现在值位」家族），提示 True / False 本身可编译（ADR-48 §八）。命题位逐字不变
 *   （PropNormalizer → HirPTop / HirPBot）。
 * - S-8 补真内部路径（∴ / ∵ 裸形反糖产 TopExpr，BTS 前提经 [PropNormalizer.prop] 不经值位
 *   守卫）由 bts 相关测锁定：既不落 E-PROP-POS，命题层仍产 HirPTop。
 */
class Q12BoolLiteralsTest {

    private fun errCodes(src: String): List<String> {
        val pr = parseScriptSourceWithNotes(src, "t79.subl")
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.filter { it.severity == Severity.ERROR }.map { it.code } +
            diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    }

    private fun allDiags(src: String): List<String> {
        val pr = parseScriptSourceWithNotes(src, "t79.subl")
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.map { it.code + " :: " + it.message } +
            diag.diags.map { it.code + " :: " + it.message }
    }

    private fun lastVar(src: String): HirVarStmt {
        val pr = parseScriptSourceWithNotes(src, "t79.subl")
        val (hir, _) = compile(pr.file, pr.notes)
        return hir.entries.mapNotNull { (it as? HirStmtEntry)?.stmt as? HirVarStmt }.last()
    }

    private fun posts(src: String): List<HirProp> {
        val pr = parseScriptSourceWithNotes(src, "t79.subl")
        val (hir, _) = compile(pr.file, pr.notes)
        val decl = hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl }.firstOrNull()
        return (decl as? HirFunDecl)?.posts ?: emptyList()
    }

    // ============================ #1 True / False 可用 ============================

    @Test fun 大写True通过且HIR同构() {
        val src = "var b: Bool = True"
        assertFalse(errCodes(src).any { it.startsWith("E") }, "通过：" + allDiags(src))
        val v = lastVar(src).value
        assertIs<HirNameRef>(v)
        assertEquals("True", v.name)
        assertEquals("Bool", (v.type as? HirNamedType)?.name)
    }

    @Test fun 大写False通过且HIR同构() {
        val src = "var c: Bool = False"
        assertFalse(errCodes(src).any { it.startsWith("E") }, "通过：" + allDiags(src))
        val v = lastVar(src).value
        assertIs<HirNameRef>(v)
        assertEquals("False", v.name)
        assertEquals("Bool", (v.type as? HirNamedType)?.name)
    }

    /** t84（Q12 收口）：小写 true/false 拒绝 + 可编译提示（与 NameResolver 同口径）。 */
    @Test fun 小写字面量终态拒收并提示() {
        val src = "var b: Bool = true\nvar c: Bool = false\nvar d: Bool = True\nvar e: Bool = False"
        val codes = errCodes(src)
        assertEquals(listOf("E-UNBOUND-NAME", "E-UNBOUND-NAME"), codes, "小写 true/false 落 E-UNBOUND-NAME：$codes")
        allDiags(src).forEach { d ->
            val want = if (d.contains("`true`")) "True" else "False"
            assertTrue(d.contains("Bool 字面量写作 `" + want + "`"), "提示含可编译大写写法：$d")
        }
    }

    /** 大写字面量可直接参与值位布尔运算（表达式位 &；t83 后 && 退役）。 */
    @Test fun 大写字面量参与布尔运算() {
        val src = "var b: Bool = True & False"
        assertFalse(errCodes(src).any { it.startsWith("E") }, allDiags(src).toString())
    }

    // ============================ #2 ⊤ ⊥ 值位拒绝 ============================

    @Test fun top值位赋值拒绝并提示True() {
        val src = "var b: Bool = ⊤"
        assertTrue(errCodes(src).contains("E-PROP-POS"), "E-PROP-POS 必落：" + errCodes(src))
        val msg = allDiags(src).first { it.startsWith("E-PROP-POS ::") }
        assertTrue(msg.contains("True"), "提示须含可编译写法 True：" + msg)
    }

    @Test fun bot值位赋值拒绝并提示False() {
        val src = "var b: Bool = ⊥"
        assertTrue(errCodes(src).contains("E-PROP-POS"), "E-PROP-POS 必落：" + errCodes(src))
        val msg = allDiags(src).first { it.startsWith("E-PROP-POS ::") }
        assertTrue(msg.contains("False"), "提示须含可编译写法 False：" + msg)
    }

    @Test fun top值位实参拒绝() {
        val src = "fun g(x: Bool): Bool = x\nvar r: Bool = g(⊤)"
        assertTrue(errCodes(src).contains("E-PROP-POS"), "实参位守卫：" + errCodes(src))
    }

    @Test fun bot值位返回拒绝() {
        val src = "fun g(): Bool = ⊥"
        assertTrue(errCodes(src).contains("E-PROP-POS"), "返回位守卫：" + errCodes(src))
    }

    @Test fun top值位模式拒绝() {
        val src = "var b: Bool = True\nvar n: Nat = when (b) { ⊤ -> 1; False -> 0 }"
        assertTrue(errCodes(src).contains("E-PROP-POS"), "模式位守卫：" + errCodes(src))
    }

    // ============================ #3 命题位零变化 ============================

    @Test fun top命题位产HirPTop() {
        val src = "fun f[p]: Null <p → ⊤>"
        assertFalse(errCodes(src).any { it.startsWith("E") }, allDiags(src).toString())
        val imp = posts(src).single()
        assertIs<HirPImp>(imp)
        assertIs<HirPTop>(imp.b)
    }

    @Test fun bot命题位产HirPBot() {
        val src = "fun f[p]: Null <p → ⊥>"
        assertFalse(errCodes(src).any { it.startsWith("E") }, allDiags(src).toString())
        val imp = posts(src).single()
        assertIs<HirPImp>(imp)
        assertIs<HirPBot>(imp.b)
    }

    @Test fun top合取命题位仍归一() {
        val src = "fun f[p, q]: Null <p ∧ ⊤>"
        assertFalse(errCodes(src).any { it.startsWith("E") }, allDiags(src).toString())
    }

    // ============================ #4 补真内部路径零变化 ============================

    /** S-8：∴ 裸形反糖为 ∵ ⊤ ∴ 形——内部 TopExpr 走命题层（HirPTop），不落 E-PROP-POS。 */
    @Test fun bts补真零变化() {
        val diags = allDiags("fun f[p]: Null <p>\n∴ ⊤")
        assertFalse(diags.any { it.startsWith("E-PROP-POS") }, "内部补真不许误伤：" + diags)
        assertFalse(diags.any { it.startsWith("E-SYNTAX") }, "反糖路径不许破：" + diags)
    }

    /** ∵ 裸形同理（Parser 反糖于前提位内部补 ⊤）。 */
    @Test fun bts因为裸形补真不走值位() {
        val diags = allDiags("fun f[p]: Null <p>\n∵ p")
        assertFalse(diags.any { it.startsWith("E-PROP-POS") }, "∵ 裸形补真不许误伤：" + diags)
    }

    // ============================ #5 when 穷尽性（True/False 计覆盖） ============================

    @Test fun when大写字面量穷尽() {
        val src = "fun w(b: Bool): Nat = when (b) { True -> 1; False -> 0 }"
        assertFalse(errCodes(src).any { it.startsWith("E") }, allDiags(src).toString())
    }

    @Test fun when混写小写臂落E_UNBOUND_NAME并提示() {
        // t84：小写臂经 D-PAT-BOOL 分支调 resolveValue ⇒ E-UNBOUND-NAME + 大写提示
        val src = "fun w(b: Bool): Nat = when (b) { True -> 1; false -> 0 }"
        assertTrue(errCodes(src).contains("E-UNBOUND-NAME"), "小写臂落 E-UNBOUND-NAME：" + errCodes(src))
        assertTrue(allDiags(src).any { it.contains("Bool 字面量写作 `False`") }, "小写模式提示可编译写法：" + allDiags(src))
    }
}
