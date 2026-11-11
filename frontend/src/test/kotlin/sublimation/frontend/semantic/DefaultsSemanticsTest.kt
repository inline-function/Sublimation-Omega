package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirNameRef
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.hir.HirBlock
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirIntLit
import sublimation.frontend.hir.HirReturn
import sublimation.frontend.hir.SymbolKind
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * t45（ADR-30）：默认值求值环境与顺序约束。
 * 修复前（17:46Z 探针实测）：默认值 AST 被塞进调用点实参位、在**调用者帧**求值——
 * ① 默认值静默捕获调用者局部变量；② 默认值引用前序形参反落 E-UNBOUND-NAME。
 * 修复后：默认值在**声明处作用域**（被调函数帧：theory + self + 前序形参）求值；
 * 后序名落 E-DEFAULT-ORDER；位置实参只能从尾部连续省略。
 */
class DefaultsSemanticsTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "t45.subl")
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

    private fun errs(bag: DiagBag) = bag.diags.filter { it.severity == Severity.ERROR }
    private fun errCodes(bag: DiagBag) = errs(bag).map { it.code }
    private fun errMsgs(bag: DiagBag) = errs(bag).joinToString("\n") { it.code + ":" + it.message }
    private fun lastVar(hir: HirFile) = hir.entries.mapNotNull { (it as? HirStmtEntry)?.stmt as? HirVarStmt }.last()

    // ---- 任务 1：默认值求值环境 = 声明处作用域 ----

    @Test
    fun defaultSeesPrecedingParamAndBindsParamSymbol() {
        // 修复前：f(a = 1) 的 b 位默认值 a 在调用点求值 → E-UNBOUND-NAME
        val (hir, bag) = script("fun f(a: Nat, b: Nat = a): Nat = a + b\nvar r = f(a = 1)")
        assertFalse(errs(bag).isNotEmpty(), "默认值引用前序形参合法；actual=" + errMsgs(bag))
        // HIR 落 ParamRef 语义：填补的默认值是绑定到**形参符号**的 NameRef（不是调用点的同名量）
        val call = lastVar(hir).value as HirCall
        val filled = call.args[1] as HirNameRef
        assertEquals("a", filled.name)
        assertEquals(SymbolKind.PARAM, filled.symbol?.kind, "默认值绑定形参符号（声明处环境）")
    }

    @Test
    fun callerLocalNotCapturedByDefault() {
        // 核心对照：默认值 a 在声明处无同名绑定；调用者块里有局部 a。
        // 修复前：静默捕获块局部 a（ec: 无错 / 若类型不符则 E-TYPE-MISMATCH）；修复后：E-UNBOUND-NAME。
        val (_, bag) = script(
            "fun f(x: Nat, y: Nat = a): Nat = x + y\n" +
            "fun g(): Nat { var a = \"s\" ; return f(x = 1) }")
        assertEquals(listOf("E-UNBOUND-NAME"), errCodes(bag),
            "默认值不得捕获调用者局部变量；actual=" + errMsgs(bag))
        assertTrue(errs(bag).any { it.message.contains("`a`") })
    }

    @Test
    fun defaultBindsDeclarationSiteGlobalNotCallerLocal() {
        // 同名穿透：顶层 var a（声明处在望）+ 块局部 a 同名——默认值按**声明处**词法解析绑顶层。
        // 用类型分辨：块局部是 Str；若捕获块局部则 E-TYPE-MISMATCH，绑定顶层则零错。
        val (hir, bag) = script(
            "var a: Nat = 5\n" +
            "fun f(x: Nat, y: Nat = a): Nat = x + y\n" +
            "fun g(): Nat { var a = \"s\" ; return f(x = 1) }")
        assertFalse(errs(bag).isNotEmpty(), "默认值按声明处解析（顶层 a: Nat）；actual=" + errMsgs(bag))
        val g = hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? HirFunDecl }.first { it.name == "g" }
        val blk = g.body as HirBlock
        val call = (blk.stmts.mapNotNull { it as? HirReturn }.first().expr) as HirCall
        val filled = call.args[1] as HirNameRef
        assertEquals("a", filled.name)
        assertFalse(filled.symbol?.kind == SymbolKind.PARAM, "绑定声明处顶层 var，非形参")
    }

    @Test
    fun forwardParamInDefaultReportsE_DEFAULT_ORDER() {
        val (_, bag) = script("fun f(a: Nat = b, b: Nat): Nat = a + b")
        assertEquals(listOf("E-DEFAULT-ORDER"), errCodes(bag), "actual=" + errMsgs(bag))
        assertTrue(errs(bag).any { it.message == "默认值只能引用先于它声明的参数；`b` 声明在 `a` 之后" },
            "诊断文案逐字；actual=" + errMsgs(bag))
    }

    @Test
    fun selfVisibleInExtDefault() {
        // self（扩展函数接收者）在默认值位可见（队长口径）
        val (hir, bag) = script("fun Nat.f(x: Nat = self): Nat = self + x\nvar r = 5.f()")
        assertFalse(errs(bag).isNotEmpty(), "默认值引用 self 合法；actual=" + errMsgs(bag))
        val d = hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? HirFunDecl }.first { it.name == "f" }
        val def = (d.params.first().default as? HirNameRef)
        assertEquals("self", def?.name, "默认值 HIR 绑定 self")
    }

    // ---- 任务 2：struct 字段默认值同口径 ----

    @Test
    fun structFieldDefaultSeesPrecedingField() {
        val (_, bag) = script("struct P(x: Nat, y: Nat = x)\nvar p = P(1)")
        assertFalse(errs(bag).isNotEmpty(), "字段默认值引用前序字段合法；actual=" + errMsgs(bag))
    }

    @Test
    fun structFieldForwardReportsE_DEFAULT_ORDER() {
        val (_, bag) = script("struct P(x: Nat = y, y: Nat)")
        assertEquals(listOf("E-DEFAULT-ORDER"), errCodes(bag), "actual=" + errMsgs(bag))
        assertTrue(errs(bag).any { it.message == "默认值只能引用先于它声明的参数；`y` 声明在 `x` 之后" })
    }

    @Test
    fun structFieldDefaultNotCaptureCtorSiteLocal() {
        // 字段默认值在声明处求值；构造点的局部量不可见
        val (_, bag) = script("struct P(x: Nat, y: Nat = a)\nfun g(): P { var a = \"s\" ; return P(x = 1) }")
        assertEquals(listOf("E-UNBOUND-NAME"), errCodes(bag), "actual=" + errMsgs(bag))
    }

    // ---- 任务 3：类型标注必填（parse 期诊断固定） ----

    @Test
    fun funParamTypeAnnotationRequired() {
        val (_, bag) = script("fun f(n = 0): Nat = 0")
        assertTrue(errCodes(bag).isNotEmpty(), "缺形参类型标注必须报错")
        assertTrue(errs(bag).any { it.message.contains("形参类型标注") }, "actual=" + errMsgs(bag))
    }

    @Test
    fun structFieldTypeAnnotationRequired() {
        val (_, bag) = script("struct P(x = 0)")
        assertTrue(errCodes(bag).isNotEmpty(), "缺字段类型标注必须报错")
        assertTrue(errs(bag).any { it.message.contains("形参类型标注") }, "actual=" + errMsgs(bag))
    }

    // ---- 任务 4：默认值缺省位置规则 ----

    @Test
    fun positionalTailContinuousOmissionOk() {
        val (_, bagA) = script("fun f(a: Nat, b: Nat = 0): Nat = a + b\nvar r = f(1)")
        assertFalse(errs(bagA).isNotEmpty(), "尾部连续默认可省略（位置式）；actual=" + errMsgs(bagA))
        val (_, bagB) = script("fun f(a: Nat, b: Nat = 0, c: Nat = 2): Nat = a + b + c\nvar r = f(1)")
        assertFalse(errs(bagB).isNotEmpty(), "多级尾部默认连续省略；actual=" + errMsgs(bagB))
    }

    @Test
    fun midDefaultWithTailRequiredPositionalReports() {
        // 中间位有默认值但尾部必填：位置实参只能从尾部连续省略——c 不可跳
        val (_, bag) = script("fun f(a: Nat, b: Nat = 0, c: Nat): Nat = a + b + c\nvar r = f(1)")
        // 修复前：静默放行（c 缺失未检）
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag), "actual=" + errMsgs(bag))
        assertTrue(errs(bag).any { it.message.contains("函数 `f` 缺实参：`c`") }, "actual=" + errMsgs(bag))
    }

    @Test
    fun namedArgSkipsMiddleAndFillsDefault() {
        val (hir, bag) = script("fun f(a: Nat, b: Nat = 0, c: Nat): Nat = a + b + c\nvar r = f(1, c = 2)")
        assertFalse(errs(bag).isNotEmpty(), "命名实参跨位省略 + 默认填补；actual=" + errMsgs(bag))
        // 填补的 b 位是默认值 0（IntLit），非调用点供给
        val call = lastVar(hir).value as HirCall
        assertEquals(3, call.args.size)
        assertEquals("0", (call.args[1] as HirIntLit).value)
    }

    @Test
    fun missingNonDefaultNamedArgReports() {
        val (_, bag) = script("fun f(a: Nat, b: Nat, c: Nat = 0): Nat = a + b + c\nvar r = f(a = 1, c = 2)")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag), "命名填补后无默认值的空位照旧报缺实参；actual=" + errMsgs(bag))
        assertTrue(errs(bag).any { it.message.contains("缺实参：`b`") })
    }

    @Test
    fun noDefaultMissingAllReports() {
        // 既有口径（无默认值族）：缺位参数按名列出
        val (_, bag) = script("fun g(a: Nat, b: Nat): Nat = a + b\nvar r = g()")
        assertTrue(errs(bag).any { it.code == "E-TYPE-MISMATCH" && it.message.contains("函数 `g` 缺实参：`a`, `b`") },
            "actual=" + errMsgs(bag))
    }
}