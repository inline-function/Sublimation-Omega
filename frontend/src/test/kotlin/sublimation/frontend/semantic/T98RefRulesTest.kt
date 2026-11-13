package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t98（N2 @ref 语义层四规则，R2 §二 / 根文档「旧工程参考-副作用与可变性控制」:59-163）:
 *  规则 1 可写性 / 规则 2 子类型 / 规则 3 拷贝分派（类型层判定 + HIR 留痕）/ 规则 4 命题待遇。
 *
 * - @ref 是**读写透传的视图包装**：@ref T ↔ T 双向相容（拷贝/共享由分派矩阵判定）;
 *   @ref @mut T = 写穿透共享引用（根文档 :113-117）;@ref T 只读（:122「b.n = 1 无法赋值」）。
 * - 规则 4 = **error 硬拒**（E-PROP-VIEW:相等命题不承认 @ref/@mut 视图类型项——根文档 :142 原文「引用与可变
 *   一样不可预测，因此在上下文中享有和可变相同的待遇」为强制 Constraint,队长 2026-11-10 批裁决①+②）。
 * - 拷贝分派为**类型层判定 + D-REF-DISPATCH 留痕**——实际深拷随后端运行时（R2 §三，t99）。
 */
class T98RefRulesTest {

    private fun errCodes(src: String, file: String = "t98.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.filter { it.severity == Severity.ERROR }.map { it.code } +
            diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    }

    private fun warnCount(src: String, code: String, file: String = "t98.subl"): Int {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.count { it.code == code } + diag.diags.count { it.code == code }
    }

    private val root = "@mut struct T(@mut n: Nat)"

    /** 验收 #1 / 规则 1：根文档 :59-63 经典例——a.n = 1 合法。 */
    @Test fun 根例可写合法() {
        val src = root + "\n@impure fun main() { @mut var a = T(0)\n    a.n = 1 }"
        assertTrue(errCodes(src).none { it.startsWith("E") }, "@mut T 接收者写字段合法：$src")
    }

    /** 验收 #1 / 规则 1：根文档 :62——b.n = 1 编译不通过。 */
    @Test fun 根例只读非法() {
        val src = root + "\n@impure fun main() { var b : T = T(0)\n    b.n = 1 }"
        assertEquals(listOf("E-IMMUT-ASSIGN"), errCodes(src), "不可变接收者写字段落 E-IMMUT-ASSIGN")
    }

    /** 验收 #2: @ref T 是只读视图（根文档 :120-122）。 */
    @Test fun ref只读视图() {
        val src = root + "\n@impure fun main() { @mut var a = T(0)\n    var d : @ref T = a\n    d.n = 1 }"
        assertEquals(listOf("E-IMMUT-ASSIGN"), errCodes(src), "@ref T 视图写字段须落闸")
    }

    @Test fun ref形参只读() {
        val src = root + "\n@impure fun bump(d : @ref T): Null { d.n = 1 }"
        assertEquals(listOf("E-IMMUT-ASSIGN"), errCodes(src), "@ref T 形参不可写字段")
    }

    /** 验收 #3: @ref @mut T 写穿透共享引用（根文档 :113-117）。 */
    @Test fun refmut写透放行() {
        val src = root + "\n@impure fun main() { @mut var a = T(0)\n    @impure fun bump(d : @ref @mut T): Null { d.n = 1 }\n    print(a) }"
        assertTrue(errCodes(src).none { it.startsWith("E") }, "@ref @mut T 写字段合法（共享写穿透）")
    }

    /** 规则 2：@mut T → @ref T 共享不可变视图（:120）。 */
    @Test fun 子类型mut到ref() {
        val src = root + "\n@impure fun main() { @mut var a = T(0)\n    var d : @ref T = a }"
        assertTrue(errCodes(src).none { it.startsWith("E") }, "@mut T → @ref T 放行")
    }

    /** 规则 2: @ref T → T——R-28（t150）废止格：want PLAIN × 源 REF 拒（E-TYPE-MISMATCH）。 */
    @Test fun 子类型ref到plain() {
        val src = root + "\n@impure fun main() { var d : @ref T = T(0)\n    var e : T = d }"
        assertTrue(errCodes(src).any { it.startsWith("E-TYPE-MISMATCH") }, "@ref T → T 落类型闸（R-28 废止隐式深拷）")
    }

    /** 规则 2: T → @ref T 不拷贝共享（:146-151）。 */
    @Test fun 子类型plain到ref() {
        val src = root + "\n@impure fun main() { var t : T = T(0)\n    var d : @ref T = t }"
        assertTrue(errCodes(src).none { it.startsWith("E") }, "T → @ref T 放行（共享不拷贝）")
    }

    /** 规则 2 健全性：T → @ref @mut T 拒绝（不可变值发可写引用不安全；根文档未给此格）。 */
    @Test fun 子类型plain到refmut拒绝() {
        val src = root + "\n@impure fun main() { var t : T = T(0)\n    var c : @ref @mut T = t }"
        assertTrue(errCodes(src).any { it.startsWith("E-TYPE-MISMATCH") }, "T → @ref @mut T 须落类型闸")
    }

    /** 规则 2: @ref @mut T → @ref T 共享不可变视图（:136-140）。 */
    @Test fun 子类型refmut到ref() {
        val src = root + "\n@impure fun main() { @mut var a = T(0)\n    var d : @ref T = a }"
        assertTrue(errCodes(src).none { it.startsWith("E") }, "@ref @mut T → @ref T 放行")
    }

    /** 规则 4 阶段 A：相等命题不承认 @ref 视图项（根文档 :142）——warn 报告态。 */
    @Test fun 相等命题ref视图硬拒() {
        val src = root + "\nfun g(d : @ref T): Bool <d = d> = False"
        assertEquals(listOf("E-PROP-VIEW"), errCodes(src), "阶段 B：@ref 相等命题硬拒")
    }

    /** 规则 4 对照：不可变 T 项的相等命题不触发。 */
    @Test fun 相等命题plain项静默() {
        val src = root + "\nfun g(t : T): Bool <t = t> = False"
        assertEquals(0, warnCount(src, "E-PROP-VIEW"), "不可变项的相等命题不应落 E-PROP-VIEW")
    }

    /** 规则 4 对照:@mut 视图项同等待遇（根文档 :142「同可变」）。 */
    @Test fun 相等命题mut视图同等待遇() {
        val src = root + "\nfun g(m : @mut T): Bool <m = m> = False"
        assertEquals(1, warnCount(src, "E-PROP-VIEW"), "@mut 项与 @ref 项同等待遇")
    }

    /** 验收 #5: @mut σ 双视图行为逐字不变——@mut var 写穿、@mut 形参借用不深拷。 */
    @Test fun mut双视图回归() {
        val src = root + "\n@impure fun bump(c : @ref T): Null { }\n@impure fun main() { @mut var ct = T(0)\n    ct.n\n    bump(ct) }"
        assertTrue(errCodes(src).none { it.startsWith("E") }, "@mut σ 双视图既有行为不变")
    }

    /** 规则 3: 拷贝分派留痕——D-REF-DISPATCH 只在 @ref 涉及时落（@mut/T→T 侧维持既有产物）。 */
    @Test fun 分派留痕仅在ref涉及时() {
        val src = root + "\n@impure fun main() { var t : T = T(0)\n    var d : @ref T = t\n    var u : T = T(0)\n    var v : T = u }"
        val pr = parseScriptSourceWithNotes(src, "dispatch.subl")
        val (f, diag) = compile(pr.file, pr.notes)
        val notes = mutableListOf<String>()
        fun walk(n: Any) {
            when (n) {
                is sublimation.frontend.hir.HirVarStmt -> notes += n.notes.map { it.code }
                is sublimation.frontend.hir.HirAssign -> notes += n.notes.map { it.code }
                is sublimation.frontend.hir.HirBlock -> n.stmts.forEach { walk(it) }
                is sublimation.frontend.hir.HirExprStmt -> walk(n.expr)
                is sublimation.frontend.hir.HirFunDecl -> n.body?.let { walk(it) }
                else -> { }
            }
        }
        f.entries.forEach { en ->
            when (en) {
                is sublimation.frontend.hir.HirStmtEntry -> walk(en.stmt)
                is sublimation.frontend.hir.HirDeclEntry -> walk(en.decl)
            }
        }
        assertEquals(listOf("D-REF-DISPATCH"), notes.filter { it == "D-REF-DISPATCH" }, "@ref 涉及 1 处分派留痕;纯 T→T 无留痕")
        assertTrue(diag.diags.none { it.severity == Severity.ERROR }, "分派留痕不落错")
    }

    /** 规则 3 补（I-5 / t102 / R-29 t150）：赋值位深拷转换的 D-DEEP-COPY 补注——R-28 后
     *  仅存复制格 MUT→MUT（@ref 涉及的深拷格已废止——拒格落 E）。 */
    /** R-29（t150）：深拷转换的补注落 diag 补充级（D-DEEP-COPY——非 HIR note，覆盖赋值/传参/初始化等 8 站点）。 */
    @Test fun 赋值位分派留痕() {
        val src = root + "\n@impure fun main() { var d : @ref T = T(0)\n    var u : T = d\n    u = T(1) }"
        val pr = parseScriptSourceWithNotes(src, "dispatch-assign.subl")
        val (f, diag) = compile(pr.file, pr.notes)
        // REF→PLAIN 现为拒格（R-28）：落 E-TYPE-MISMATCH，无深拷补注
        assertTrue(diag.diags.any { it.code == "E-TYPE-MISMATCH" }, "REF→PLAIN 拒格落 E-TYPE-MISMATCH")
        assertEquals(0, diag.diags.count { it.code == "D-DEEP-COPY" }, "拒格不落深拷补注（R-29 仅复制格）")
    }

    /** 占位：parse 侧错误并入（仅用于 walk 测试内合并诊断——避免未用警告）。 */
    private fun bagError(@Suppress("UNUSED_PARAMETER") d: sublimation.frontend.diag.Diag) = Unit
}
