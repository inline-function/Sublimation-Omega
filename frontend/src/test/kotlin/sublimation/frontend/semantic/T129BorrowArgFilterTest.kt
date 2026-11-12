package sublimation.frontend.semantic

import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirBorrow
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.semantic.compileWithRounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * t129 清账件 3（S6 登记项锁定）：borrowArgOk 前置过滤——理论参数形参位的 W-BORROW-REDUNDANT 免。
 *
 * - S6 §3.2 判据含 `borrowArgOk(got, formal)` 前置：形参为**理论参数位**（如 List[a].add(v: a)
 *   的 v）时，未代入位核对不成立—— warn 免，避免误 warn（与 R-02 退化纪律同源：warn 须锚可核对视图）;
 * - 对照：具体类型只读形参位（Nat）的借用照常落 warn（过滤未越界）。 */
class T129BorrowArgFilterTest {

    private fun sc(src: String) =
        parseScriptSourceWithNotes(src, "t129.subs").let { compileWithRounds(it.file, it.notes, scriptMode = true) }
    private fun warns(rs: RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.WARN }.map { it.code }
    private fun borrowNote(rs: RoundsReport): String? {
        var out: HirBorrow? = null
        fun walk(n: Any?) {
            when (n) {
                is HirBorrow -> if (out == null) out = n
                is sublimation.frontend.hir.HirCall -> n.args.forEach { walk(it) }
                is sublimation.frontend.hir.HirBlock -> n.stmts.forEach { walk(it) }
                is sublimation.frontend.hir.HirExprStmt -> walk(n.expr)
                is sublimation.frontend.hir.HirVarStmt -> walk(n.value)
                is sublimation.frontend.hir.HirUnchecked -> walk(n.inner)
                is sublimation.frontend.hir.HirStmtEntry -> walk(n.stmt)
                is sublimation.frontend.hir.HirDeclEntry -> walk(n.decl)
                is sublimation.frontend.hir.HirFunDecl -> n.body?.let { walk(it) }
                else -> { }
            }
        }
        rs.hir.entries.forEach { walk(it) }
        return out?.notes?.firstOrNull { it.code == "D-REF-DISPATCH" }?.message
    }

    /** 免 warn 位 1：方法糖理论参数位（xs.add(&one) 的 v: a）——warn 免，留痕照常。 */
    @Test fun 方法糖理论参数位warn免() {
        val rs = sc("var one : Nat = 1\n" +
            "@impure fun main(): Null {\n  var xs : @mut List[Nat] = MutableListOf(1)\n  unchecked xs.add(&one)\n}\n")
        assertEquals(emptyList(), warns(rs), "理论参数形参位（a 未代入核对）零 warn")
        assertNotNull(borrowNote(rs), "借用留痕照落（分派结论仍可编码消费）")
    }

    /** 免 warn 位 2：通用函数理论参数位（fun g[T](x : T)，g(&v)）——warn 免。 */
    @Test fun 通用函数理论参数位warn免() {
        val rs = sc("fun g[T](x : T) { print(x) }\n" +
            "var v : Nat = 3\ng(&v)\nprint(v)\n")
        assertEquals(emptyList(), warns(rs), "泛型理论参数形参位零 warn")
    }

    /** 对照正例：具体类型只读形参位借用照常落 warn——前置过滤未越界（t128 既有判据）。 */
    @Test fun 具体只读形参位warn照常() {
        val rs = sc("fun double(n : Nat) { print(n + n) }\n" +
            "@impure fun main(): Null {\n  var x : Nat = 3\n  double(&x)\n}\n")
        assertEquals(listOf("W-BORROW-REDUNDANT"), warns(rs),
            "具体类型位过滤不影响：只读形参位点借用仍判冗余")
    }
}
