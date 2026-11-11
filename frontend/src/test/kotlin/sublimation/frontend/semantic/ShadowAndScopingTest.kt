package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirBlock
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirExprStmt
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirIf
import sublimation.frontend.hir.HirLocalFun
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t39（ADR-27）：名解析与遮蔽——顶层唯一 / 局部同域后遮蔽前 / 局部扩展函数帧链 / H-SHADOW 提示。
 *
 * 用户裁决（2026-11-06）：同域允许重名（后面的遮蔽前面的，不报错）；**顶层**自由变量与函数须唯一
 *（工程模式顶层 var 本就被禁止；脚本模式顶层 var 允许但同名仍报 E-DUP-DECL）；扩展函数按
 * (接收者类型头, 名字) 键遮蔽，**异键共存**（内层 Nat.f 不遮蔽外层 Str.f）。
 */
class ShadowAndScopingTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "shadow.subl")
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
    private fun errMsgs(bag: DiagBag) =
        bag.diags.filter { it.severity == Severity.ERROR }.joinToString(" | ") { it.code + ":" + it.message }
    private fun hintMsgs(bag: DiagBag) =
        bag.diags.filter { it.severity == Severity.HINT }.map { it.message }
    private fun shadowHints(bag: DiagBag) = hintMsgs(bag).filter { it.contains("遮蔽") }

    /** 块内（含 if 分支 / 函数体）所有 var 声明——遮蔽解析结论的 HIR 侧证据。 */
    private fun allVarStmts(hir: HirFile): List<HirVarStmt> {
        val out = mutableListOf<HirVarStmt>()
        // 局部函数不可前向引用——合并为单一 walk（HirVarStmt 叶子 + 结构节点透传）
        fun walk(n: Any) {
            when (n) {
                is HirVarStmt -> out.add(n)
                is HirBlock -> n.stmts.forEach { walk(it) }
                is HirIf -> { walk(n.thenBlock); n.elseBlock?.let { walk(it) } }
                is HirExprStmt -> walk(n.expr)
                is HirLocalFun -> n.fn.body?.let { walk(it) }
                else -> { }
            }
        }
        hir.entries.forEach { en ->
            when (en) {
                is HirStmtEntry -> walk(en.stmt)
                is HirDeclEntry -> (en.decl as? HirFunDecl)?.body?.let { walk(it) }
            }
        }
        return out
    }

    private fun varType(hir: HirFile, name: String): String? =
        allVarStmts(hir).lastOrNull { it.name == name }?.type?.let { (it as? HirNamedType)?.name }

    /** 块内局部函数的返回类型序列——「后声明者」在 HIR 中确实存在的证据（局部函数调用 v1 推导为合成类型，故以声明侧为准）。 */
    private fun localFunRets(hir: HirFile): List<String?> {
        val out = mutableListOf<HirLocalFun>()
        fun walk(n: Any) {
            when (n) {
                is HirBlock -> n.stmts.forEach { walk(it) }
                is HirIf -> { walk(n.thenBlock); n.elseBlock?.let { walk(it) } }
                is HirExprStmt -> walk(n.expr)
                is HirLocalFun -> { out.add(n); n.fn.body?.let { walk(it) } }
                is HirFunDecl -> n.body?.let { walk(it) }
                else -> { }
            }
        }
        hir.entries.forEach { en ->
            when (en) {
                is HirStmtEntry -> walk(en.stmt)
                is HirDeclEntry -> walk(en.decl)
            }
        }
        return out.map { (it.fn.retType as? HirNamedType)?.name }
    }

    // -------------------- 任务 2：局部同域允许重名，后遮蔽前 --------------------

    /** 正例：局部同域两个 fun f（不同签名）→ 不报 E-，出现 H-SHADOW，解析到后声明者。 */
    @Test
    fun localFunSameScopeShadowResolvesLater() {
        val src = """
            if (True) {
                fun f(): Nat = 1
                fun f(): Str = "later"
                var z = f()
            }
        """.trimIndent()
        val (hir, bag) = script(src)
        assertEquals(emptyList<String>(), errCodes(bag), "同域重名不报 E-：" + errMsgs(bag))
        assertTrue(shadowHints(bag).size >= 1, "H-SHADOW 提示缺失：" + hintMsgs(bag).joinToString(" | "))
        assertTrue(shadowHints(bag).any { it.contains("f") && it.contains("同域") },
            "应为同域后遮蔽前提示：" + shadowHints(bag).joinToString(" | "))
        // HIR 侧证据：两个局部函数都在（先 Nat 后 Str），遮蔽后解析不塌树
        assertEquals(listOf("Nat", "Str"), localFunRets(hir), "两个 f 均落 HIR（局部函数 v1 调用类型为合成体，以声明侧为准）")
    }

    /** 正例：同域两个 var x → 不报 E-，H-SHADOW，引用取后者。 */
    @Test
    fun localVarSameScopeShadowTakesLast() {
        val src = """
            if (True) {
                var x = 1
                var x = 2
                var y = x
            }
        """.trimIndent()
        val (hir, bag) = script(src)
        assertEquals(emptyList<String>(), errCodes(bag), "同域 var 重名不报 E-：" + errMsgs(bag))
        assertTrue(shadowHints(bag).any { it.contains("x") && it.contains("同域") },
            "var 同域遮蔽提示缺失：" + shadowHints(bag).joinToString(" | "))
        assertEquals("Nat", varType(hir, "y"), "引用解析到前者类型 Nat")
        assertEquals(2, allVarStmts(hir).count { it.name == "x" }, "两次 var x 均保留在 HIR 中")
    }

    /** 正例（用户原文场景，遮蔽不回退）：顶层 fun f(a: Nat)；块内 fun f(a: Str) → 块内 f(0) 必须报类型不符。 */
    @Test
    fun innerShadowDoesNotFallBackToOuter() {
        val src = """
            fun f(a: Nat): Nat = a + 1
            fun probe(): Nat {
                fun f(a: Str): Str = a
                f(0)
            }
        """.trimIndent()
        val (hir, bag) = script(src)
        val codes = errCodes(bag)
        assertTrue(codes.isNotEmpty(), "f(0) 必须报类型不符（不得回退外层 Nat 版）")
        assertFalse(codes.contains("E-UNBOUND-NAME"), "内层 f 被解析到了，不得落 UNBOUND")
        assertFalse(codes.any { it == "E-DUP-DECL" }, "局部遮蔽不是重复声明：" + errMsgs(bag))
        assertTrue(shadowHints(bag).any { it.contains("f") }, "遮蔽提示缺失：" + shadowHints(bag).joinToString(" | "))
    }

    // -------------------- 任务 3：局部扩展函数（核心特性） --------------------

    /** 正例：局部扩展函数 fun Nat.bump() 块内可用；块外调用 → E-UNBOUND-NAME。 */
    @Test
    fun localExtFunVisibleInsideBlockOnly() {
        val src = """
            if (True) {
                fun Nat.bump(): Nat = 40 + 2
                var z = 3.bump()
            }
            var w = 1.bump()
        """.trimIndent()
        val (hir, bag) = script(src)
        val codes = errCodes(bag)
        assertEquals("Nat", varType(hir, "z"), "块内方法糖解析到局部扩展")
        assertTrue(codes.isNotEmpty(), "块外调用退帧后的局部扩展须报错：" + errMsgs(bag))
        assertTrue(codes.any { it == "E-UNBOUND-NAME" || it.contains("MISMATCH") || it.contains("UNBOUND") },
            "块外 1.bump() 须报不可解析类诊断：" + errMsgs(bag))
    }

    /** 正例：内层 fun Nat.f() 遮蔽全局 Nat.f()；全局 Str.f() 仍可见（异键不遮蔽）。 */
    @Test
    fun innerExtFunShadowsSameKeyOnly() {
        val src = """
            fun Str.f(): Str = "sf"
            fun Nat.f(): Nat = 0
            if (True) {
                fun Nat.f(): Nat = 1
                var a = 1.f()
                var b = "s".f()
            }
        """.trimIndent()
        val (hir, bag) = script(src)
        assertEquals(emptyList<String>(), errCodes(bag), "异键共存不报错：" + errMsgs(bag))
        assertEquals("Nat", varType(hir, "a"), "Nat.f 解析到内层")
        assertEquals("Str", varType(hir, "b"), "全局 Str.f 不被遮蔽")
        assertTrue(shadowHints(bag).any { it.contains("f") && it.contains("扩展函数") },
            "局部扩展遮蔽提示缺失：" + shadowHints(bag).joinToString(" | "))
    }

    // -------------------- 任务 1：顶层唯一性 --------------------

    /** 正例：脚本模式顶层 var x 通过。 */
    @Test
    fun scriptTopLevelVarAllowed() {
        val src = """
            var x = 1
            var y = 2
        """.trimIndent()
        val (hir, bag) = script(src)
        assertEquals(emptyList<String>(), errCodes(bag), "脚本顶层 var 合法：" + errMsgs(bag))
        assertEquals("Nat", varType(hir, "x"))
    }

    /** 正例：脚本模式顶层 var 与顶层自由函数仍须唯一——同名报 E-DUP-DECL。 */
    @Test
    fun scriptTopLevelVarDupRejected() {
        val src = """
            var x = 1
            var x = 2
        """.trimIndent()
        val (hir, bag) = script(src)
        assertEquals(listOf("E-DUP-DECL"), errCodes(bag), "顶层 var 同名须报 E-DUP-DECL：" + errMsgs(bag))
    }

    /** 正例：脚本顶层 var 撞顶层 fun 同名 → E-DUP-DECL（var↔fun 顶层唯一）。 */
    @Test
    fun scriptTopVarClashesTopFun() {
        val src = """
            fun f(): Nat = 1
            var f = 2
        """.trimIndent()
        val (hir, bag) = script(src)
        assertEquals(listOf("E-DUP-DECL"), errCodes(bag), "var↔fun 顶层同名须报 E-DUP-DECL：" + errMsgs(bag))
    }

    /** 反例：工程模式顶层 var → 仍按「顶层只能写声明」报错（既有行为不回归）。 */
    @Test
    fun projectTopLevelVarStillRejected() {
        val src = "var x = 1"
        val pr = parseSourceWithNotes(src, "proj.subl")
        val (hir, bag) = compile(pr.file, pr.notes)
        pr.diag.forEach { d -> if (d.severity == Severity.ERROR) bag.error(d.code, d.pos, d.message) }
        val msgs = bag.diags.filter { it.severity == Severity.ERROR }.joinToString(" | ") { it.message }
        assertTrue(bag.diags.any { it.severity == Severity.ERROR }, "工程模式顶层 var 必须报错")
        assertTrue(msgs.contains("顶层"), "应为「顶层只能写声明」类诊断：" + msgs)
    }

    /** 反例：局部同域两个同键扩展函数 fun Nat.f() → E-DUP-EXT（两处报）。 */
    @Test
    fun localSameKeyExtFunDupRejected() {
        val src = """
            if (True) {
                fun Nat.f(): Nat = 1
                fun Nat.f(): Nat = 2
            }
        """.trimIndent()
        val (hir, bag) = script(src)
        val codes = errCodes(bag)
        assertEquals(2, codes.count { it == "E-DUP-EXT" }, "同域同键扩展重复须两处均报：" + errMsgs(bag))
        assertTrue(codes.all { it == "E-DUP-EXT" }, "不应有其它错误：" + errMsgs(bag))
    }

    // -------------------- 任务 4：满足判定可见集（TS-19） --------------------

    /** 正例：同一类型在不同词法位置满足性不同——块内局部 Str.name 使 combine[String] 满足 Named；
     *  块外无局部声明 → E-TC-NOT-SATISFIED。这是局部扩展函数的正当结果，非缺陷（ADR-27 R-14）。 */
    @Test
    fun satisfyVisibilityDiffersByLexicalPosition() {
        val src = """
            trait Named { fun name(self): Str }
            fun Nat.name(): Str = "nat"
            // t62（ST-13）：单形参形态（T 不入返回位）
            fun combine[T: Named](a: T, b: T): Nat = 0
            fun probe(): Str {
                fun Str.name(): Str = "local"
                var a = combine("x", "y")
                "p"
            }
            var b = combine("x", "z")
        """.trimIndent()
        val (hir, bag) = script(src)
        val codes = errCodes(bag)
        // probe 内：局部 Str.name 可见 → 满足（combine 调用无错）；块外：仅全局 Nat.name → 不满足
        assertTrue(codes.contains("E-TC-NOT-SATISFIED"), "块外 combine[String] 须报 E-TC-NOT-SATISFIED：" + errMsgs(bag))
        val miss = bag.diags.filter { it.code == "E-TC-NOT-SATISFIED" }.map { it.message }.joinToString(" | ")
        assertTrue(miss.contains("name"), "缺失规范清单含 name：" + miss)
    }

    // -------------------- 任务 5：H-SHADOW severity = HINT --------------------

    /** H-SHADOW 必须是 hint 族（非 error/warn）。 */
    @Test
    fun shadowHintIsHintSeverity() {
        val src = """
            var g: Nat = 1
            if (True) {
                var g = 2
            }
        """.trimIndent()
        val (hir, bag) = script(src)
        val shadows = bag.diags.filter { it.code == "H-SHADOW" }
        assertTrue(shadows.isNotEmpty(), "须有 H-SHADOW 条目")
        assertTrue(shadows.all { it.severity == Severity.HINT }, "H-SHADOW severity 必须是 HINT")
        assertEquals("Nat", varType(hir, "g"))
    }

    /** H-SHADOW 文本格式（名字 + 种类 + 不回退结论）。 */
    @Test
    fun shadowHintMessageForm() {
        val src = """
            var g: Nat = 1
            if (True) {
                var g = 2
            }
        """.trimIndent()
        val (hir, bag) = script(src)
        val msg = shadowHints(bag).first()
        assertTrue(msg.contains("这里的 `g` 遮蔽了"), "消息格式（名字+遮蔽）：" + msg)
        assertTrue(msg.contains("变量"), "消息须含种类：" + msg)
        assertTrue(msg.contains("不回退"), "消息须含不回退结论：" + msg)
        assertEquals("Nat", varType(hir, "g"))
    }
}
