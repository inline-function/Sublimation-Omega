package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t25（B9，ADR-22）：lambda 返回类型反推（两遍定型）与显式类型实参交互的用例。
 */
class LambdaInferenceTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "lambda.subl")
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
    private fun stmts(hir: HirFile) = hir.entries.mapNotNull { (it as? HirStmtEntry)?.stmt }

    @Test
    fun lambdaReturnInfersFromBody() {
        val src = "struct Ok[T](v: T)\nstruct Err[E](e: E)\ntype Res[T, E] = Ok[T] | Err[E]\n" +
            "fun mapResult[T, E, U](r: Res[T, E], f: (T) => U): Res[U, E] = when(r) { is Ok(v) -> Ok(f(v)) ; is Err(e) -> Err(e) }\n" +
            "var r: Res[Nat, Str] = Ok(42)\n" +
            "var q = mapResult(r, { x => x + 1 })"
        val (hir, bag) = script(src)
        assertFalse(bag.hasError, "codes=" + bag.diags.map { it.code }.joinToString(","))
        val q = stmts(hir).filterIsInstance<HirVarStmt>().last()
        val t = q.type as sublimation.frontend.hir.HirUnionType
        val ok = t.variants.first { (it as HirNamedType).name == "Ok" } as HirNamedType
        val err = t.variants.first { (it as HirNamedType).name == "Err" } as HirNamedType
        assertEquals("Nat", (ok.args[0] as HirNamedType).name, "U 位按 lambda 体回代")
        assertEquals("Str", (err.args[0] as HirNamedType).name, "E 位按第一实参回代")
    }

    @Test
    fun lambdaPositionalParamsTakeExpectation() {
        val src = "fun app[T](a: T, f: (T) => T): T = a\n" +
            "var s = app(1, { x => x + x })"
        val (hir, bag) = script(src)
        assertFalse(bag.hasError, "codes=" + bag.diags.map { it.code }.joinToString(","))
        val s = stmts(hir).filterIsInstance<HirVarStmt>().first()
        assertEquals("Nat", (s.type as HirNamedType).name)
    }

    @Test
    fun lambdaMismatchWithFixedFormalReports() {
        val (hir, bag) = script("fun g(f: (Nat) => Nat): Nat = f(1)\n" +
            "var s = g({ x => \"s\" })")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag), "反例：lambda 体 Str 与形参返回 Nat 不可合一，必须报错")
    }

    @Test
    fun lambdaMismatchWithOtherArgSolutionReports() {
        val src = "fun first[T, U](a: T, init: U, f: (T) => U): U = init\n" +
            "var s = first(1, 0, { x => \"s\" })"
        val (hir, bag) = script(src)
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag), "反例：U 已由 init 解 Nat，lambda 体 Str 不再吸收，必须报错")
    }

    @Test
    fun explicitTypeArgsWinOverLambda() {
        val src = "struct Ok[T](v: T)\nstruct Err[E](e: E)\ntype Res[T, E] = Ok[T] | Err[E]\n" +
            "fun mapResult[T, E, U](r: Res[T, E], f: (T) => U): Res[U, E] = when(r) { is Ok(v) -> Ok(f(v)) ; is Err(e) -> Err(e) }\n" +
            "var r: Res[Nat, Str] = Ok(42)\n" +
            "var q = mapResult[Nat, Str, Bool](r, { x => True })"
        val (hir, bag) = script(src)
        assertFalse(bag.hasError, "codes=" + bag.diags.joinToString(",") { it.code + it.message })
        val q = stmts(hir).filterIsInstance<HirVarStmt>().last()
        // t41（ADR-28 R-16）：Res[U, E] 经 expand 为联合（成员 Ok/Err 带解出的实参）
        val tp = q.type as sublimation.frontend.hir.HirUnionType
        val ok = tp.variants.first { (it as HirNamedType).name == "Ok" } as HirNamedType
        val err = tp.variants.first { (it as HirNamedType).name == "Err" } as HirNamedType
        assertEquals("Bool", (ok.args[0] as HirNamedType).name, "U 按显式实参，不被 lambda 体改写")
        assertEquals("Str", (err.args[0] as HirNamedType).name)
    }

    @Test
    fun explicitTypeArgsConflictWithLambdaReports() {
        val src = "struct Ok[T](v: T)\nstruct Err[E](e: E)\ntype Res[T, E] = Ok[T] | Err[E]\n" +
            "fun mapResult[T, E, U](r: Res[T, E], f: (T) => U): Res[U, E] = when(r) { is Ok(v) -> Ok(f(v)) ; is Err(e) -> Err(e) }\n" +
            "var r: Res[Nat, Str] = Ok(42)\n" +
            "var q = mapResult[Nat, Str, Str](r, { x => x + 1 })"
        val (hir, bag) = script(src)
        assertTrue(errCodes(bag).contains("E-TYPE-MISMATCH"), "codes=" + bag.diags.joinToString(",") { it.code + ":" + it.message })
    }
}
