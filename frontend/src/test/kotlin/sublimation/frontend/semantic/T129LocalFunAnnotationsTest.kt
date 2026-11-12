package sublimation.frontend.semantic

import sublimation.frontend.diag.Severity
import sublimation.frontend.cli.compileStdlibUnits
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.hir.HirLocalFun
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.semantic.compileWithRounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * t129 清账件 2（I-6 收口）：局部 fun 注解透传。
 *
 * - 原解析层 AT 位只放 var：语句位 `@impure fun g(…) {…}` 落 E-SYNTAX「期望 var 变量声明」错拒；
 * - 现注解经 parseFun(anns) 落 FunDecl.annotations → 语义层既有注解位（funDecl 纯度/闸帧）——
 *   @impure 局部 fun 的 HIR purity 与顶层同判；@pure 局部 fun 的 @mut 体调用判据与顶层一致（R-16 A2）。 */
class T129LocalFunAnnotationsTest {

    // t138（R-23 B1）：prelude 虚拟层 retire——编译挂 stdlib 快照（与 CLI 默认挂载同构）
    private val stdlibMounts: Map<String, ModuleSnapshot> =
        compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            .let { (u, _) -> u.associate { it.alias to it.snapshot } }

    private fun sc(src: String) =
        parseScriptSourceWithNotes(src, "t129.subs").let { compileWithRounds(it.file, it.notes, scriptMode = true, mounted = stdlibMounts) }
    private fun errs(rs: RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun localFun(rs: RoundsReport, name: String): HirLocalFun? {
        var out: HirLocalFun? = null
        fun walk(n: Any?) {
            when (n) {
                is HirLocalFun -> if (n.fn.name == name) out = n
                is sublimation.frontend.hir.HirFunDecl -> n.body?.let { walk(it) }
                is sublimation.frontend.hir.HirBlock -> n.stmts.forEach { walk(it) }
                is sublimation.frontend.hir.HirStmtEntry -> walk(n.stmt)
                is sublimation.frontend.hir.HirDeclEntry -> walk(n.decl)
                is sublimation.frontend.hir.HirExprStmt -> walk(n.expr)
                else -> { }
            }
        }
        rs.hir.entries.forEach { walk(it) }
        return out
    }

    /** 正例：@impure 局部 fun 零错误 + 注解落 HirFunDecl.annotations + purity IMPURE（同顶层口径）。 */
    @Test fun impure局部fun注解透传() {
        val rs = sc("@impure fun main(): Null {\n  @impure fun g(): Null { print(1) }\n  g()\n}\n")
        assertEquals(emptyList(), errs(rs), "局部 @impure fun 零错误（原 E-SYNTAX 错拒已收口）")
        val lf = localFun(rs, "g")
        assertTrue(lf != null, "HirLocalFun 产出")
        assertEquals(listOf("impure"), lf!!.fn.annotations, "注解透传至 FunDecl")
        assertEquals(sublimation.frontend.hir.HirPurity.IMPURE, lf.fn.purity, "@impure 纯度位接通")
    }

    /** 正例：bare 局部 fun（无注解）原形态不变——annotations 空、零错误。 */
    @Test fun bare局部fun不变() {
        val rs = sc("@impure fun main(): Null {\n  fun g(): Null { print(1) }\n  g()\n}\n")
        assertEquals(emptyList(), errs(rs))
        val lf = localFun(rs, "g")
        assertTrue(lf != null && lf!!.fn.annotations.isEmpty(), "bare 局部 fun 无注解")
    }

    /** 语义位同判：@pure 局部 fun 的 @mut 体调用按既有 R-16 A2 口径（与顶层同判，不另设判据）。 */
    @Test fun pure局部fun与顶层同判() {
        val local = sc("@impure fun main(): Null {\n  @pure fun bad(xs : @mut List[Nat]) { xs.add(1) }\n" +
            "  var xs : @mut List[Nat] = MutableListOf(1)\n  unchecked bad(xs)\n}\n")
        val top = sc("@pure fun bad(xs : @mut List[Nat]) { xs.add(1) }\n" +
            "@impure fun main(): Null {\n  var xs : @mut List[Nat] = MutableListOf(1)\n  unchecked bad(xs)\n}\n")
        assertEquals(errs(top), errs(local), "局部 @pure fun 判据同顶层（R-16 A2 推导口径一致）")
        assertEquals(emptyList(), errs(local), "两形态皆按既有口径零错误")
    }

    /** 负例：AT 位非法形态（@foo x = …）保持精确错拒（误解放宽回归保护——断言挂解析层诊断）。 */
    @Test fun at位非法形态保持错拒() {
        val pr = parseScriptSourceWithNotes("@impure fun main(): Null {\n  @bogus thing = 1\n}\n", "t129.subs")
        assertTrue(pr.diag.any { it.severity == Severity.ERROR && it.code == "E-SYNTAX" },
            "AT 位非 var/fun 仍须 E-SYNTAX：${pr.diag.map { it.code }}")
    }
}
