package sublimation.frontend.semantic

import sublimation.frontend.ast.BinExpr
import sublimation.frontend.ast.Expr
import sublimation.frontend.ast.NameRef
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirPBot
import sublimation.frontend.hir.HirTBin
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * t85（用户裁决 Q21 命题侧收口）边界测试：命题位的 & / | 只作为**顶层连接词**时被拒
 * （复用 E-PROP-FORM，提示 ∧ / ∨）；括号内、实参里、关系算符操作数位的 & / | 是**表达式**
 * （用户裁决的分层：表达式层 & | !，命题层 ∧ ∨ → ↔ ¬），必须照常通过。
 * 直接构造 AST 调 prop()（同 P5Batch1 装配），绕过解析层精确定位边界；另配两条全管线用例。
 */
class PropOpBoundaryTest {

    private fun normalizer(diag: DiagBag = DiagBag()): PropNormalizer {
        val nids = NodeIdSource()
        val symbols = SymbolTable(nids::alloc)
        return PropNormalizer(nids::alloc, NameResolver(symbols, diag), diag, { false }, { null }, { t -> t.toString() })
    }

    private fun ref(n: String) = NameRef(n, SYNTHETIC_POS)
    private fun bin(op: String, l: Expr, r: Expr) = BinExpr(op, l, r, SYNTHETIC_POS)

    /** 负例 1（队长判例）：`<p & q>` —— 顶层连接词位，拒 + 提示 ∧ + PBot 半成品占位。 */
    @Test
    fun 顶层连接词amp_拒并提示wedge() {
        val diag = DiagBag()
        assertIs<HirPBot>(normalizer(diag).prop(bin("&", ref("p"), ref("q"))))
        val err = diag.diags.first { it.code == "E-PROP-FORM" }
        assertTrue(err.message.contains("∧"), "应提示 ∧：${err.message}")
        assertTrue(err.message.contains("&"), "应指出问题写法 &：${err.message}")
        assertTrue(err.severity == Severity.ERROR)
    }

    /** 负例 2：`<p | q>` —— 同上，提示 ∨。 */
    @Test
    fun 顶层连接词bar_拒并提示vee() {
        val diag = DiagBag()
        assertIs<HirPBot>(normalizer(diag).prop(bin("|", ref("p"), ref("q"))))
        val err = diag.diags.first { it.code == "E-PROP-FORM" }
        assertTrue(err.message.contains("∨"), "应提示 ∨：${err.message}")
        assertTrue(err.severity == Severity.ERROR)
    }

    /** 正例 1（队长判例）：`<(a & b) = c>` —— 关系算符操作数位走 term() 表达式通道，放行。 */
    @Test
    fun 关系操作数位的amp_放行落HirTBin() {
        val diag = DiagBag()
        val p = normalizer(diag).prop(bin("=", bin("&", ref("a"), ref("b")), ref("c")))
        assertIs<HirPAtom>(p)
        assertIs<HirTBin>(p.terms.first(), "括号内 a & b 是 Bool 表达式，应落 HirTBin：${p.terms}")
        assertTrue(diag.diags.isEmpty(), "表达式位的 & 不应报错：${diag.diags}")
    }

    /** 正例 2（队长判例）：`<p(x & y)>` —— 谓词实参位的 & 是表达式，放行。 */
    @Test
    fun 谓词实参位的amp_放行落HirTBin() {
        val diag = DiagBag()
        val nids = NodeIdSource()
        val call = sublimation.frontend.ast.CallExpr(ref("p"), listOf(bin("&", ref("x"), ref("y"))), pos = SYNTHETIC_POS)
        val p = PropNormalizer(nids::alloc, NameResolver(SymbolTable(nids::alloc), diag), diag, { false }, { null }, { t -> t.toString() }).prop(call)
        assertIs<HirPAtom>(p)
        assertIs<HirTBin>(p.terms.first(), "实参 x & y 是 Bool 表达式，应落 HirTBin：${p.terms}")
        assertTrue(diag.diags.isEmpty(), "实参位的 & 不应报错：${diag.diags}")
    }

    /** 全管线佐证：源码形态收敛一致。 */
    @Test
    fun 全管线_拒绝与放行同边界() {
        val bad = allDiags("fun f[p, q]: Null <p & q>")
        assertTrue(bad.any { it.startsWith("E-PROP-FORM") && it.contains("∧") },
            "顶层连接词 & 应落 E-PROP-FORM + 提示 ∧：$bad")
        val good = allDiags("fun f[p, q]: Null <p ∧ q>")
        assertTrue(good.none { it.startsWith("E-") }, "p ∧ q 应干净通过：$good")
    }

    private fun allDiags(src: String, file: String = "t85.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.map { it.code + " :: " + it.message } + diag.diags.map { it.code + " :: " + it.message }
    }
}
