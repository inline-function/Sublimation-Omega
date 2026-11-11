package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirWhen
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t22（B3，ADR-20）：when 穷尽性双向用例——真穷尽不报 / 真非穷尽必报。
 *
 * 密封主题口径：Bool（true/false 两值）+ t44（ADR-29）后 prelude 容器（Optional/Result/List/Set/Map）
 * 经联合别名展平走 t41 联合穷尽性通道；守卫分支不吸收（旧 WhenCheck.bindingCovered），
 * 嵌套字段位按密封构造子联合覆盖（flatten）。
 */
class WhenExhaustivenessTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "exhaustive.subl")
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

    private fun errCodes(bag: DiagBag) = bag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    private fun allCodes(bag: DiagBag) = bag.diags.map { it.code }

    // t41（ADR-28 R-20）：enum 废除 → 联合形态穷尽性（全具名 struct 成员 + is X 分支覆盖）
    @Test
    fun unionAllStructMembersExhaustiveNoReport() {
        val (hir, bag) = script("struct A()\nstruct B()\ntype E = A | B\nfun f(x: E): Nat = when (x) { is A -> 1 ; is B -> 2 }")
        assertFalse(bag.hasError, "联合成员齐 → 无 E-NON-SEALED-MATCH")
        val f = hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? HirFunDecl }.first()
        val w = f.body as HirWhen
        assertEquals("Nat", (w.type as HirNamedType).name, "密封 join 取真类型不退化")
        assertTrue(w.exhaustive, "HirWhen.exhaustive 置真")
    }

    @Test
    fun boolTrueAndFalseCoverNoReport() {
        val (_, bag) = script("var b = True\nfun g(): Nat = when (b) { True -> 1 ; False -> 0 }")
        assertFalse(bag.hasError, "Bool True/False 齐 → 无 W/E")
    }

    @Test
    fun preludeOptionalSomeNoneCoverNoReport() {
        val (_, bag) = script("fun h(o: Optional[Nat], d: Nat): Nat = when (o) { is Some(v) -> v ; None -> d }")
        assertFalse(bag.hasError, "Optional Some/None 联合覆盖 → 无 W/E")
    }

    @Test
    fun nestedOptionalFlattenCoverNoReport() {
        val (_, bag) = script(
            "fun k(oo: Optional[Optional[Nat]]): Nat = when (oo) { is Some(is Some(y)) -> y ; is Some(None) -> 0 ; None -> 0 }")
        assertFalse(bag.hasError, "嵌套 Optional 联合覆盖 → 无 W/E")
    }

    @Test
    fun unionMissingMemberReports() {
        val (_, bag) = script("struct A()\nstruct B()\nstruct C()\ntype E = A | B | C\nfun m(x: E): Nat = when (x) { is A -> 1 ; is B -> 2 }")
        assertTrue(errCodes(bag).contains("E-NON-SEALED-MATCH"), "缺 C → E-NON-SEALED-MATCH（funBody 体为非密封 when）")
        assertTrue(allCodes(bag).contains("W-NON-EXHAUSTIVE"), "缺 C → W-NON-EXHAUSTIVE")
    }

    @Test
    fun boolMissingValueWarns() {
        val (_, bag) = script("var b = True\nfun n(): Nat = when (b) { True => 1 }")
        assertTrue(allCodes(bag).contains("W-NON-EXHAUSTIVE"), "缺 False → W-NON-EXHAUSTIVE")
    }

    @Test
    fun preludeOptionalMissingCtorReports() {
        val (_, bag) = script("fun p(o: Optional[Nat]): Nat = when (o) { is Some(v) => v }")
        assertTrue(errCodes(bag).contains("E-NON-SEALED-MATCH"), "缺 None → E-NON-SEALED-MATCH")
    }
}
