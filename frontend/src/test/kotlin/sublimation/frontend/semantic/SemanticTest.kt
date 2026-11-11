
package sublimation.frontend.semantic

// P-9/t40：以下 1 条用例（crossEnumCtorAmbiguityHint）引用已废除的枚举节点，已临时移除，由 t41 以联合类型形态恢复

import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirBlock
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirIntLit
import sublimation.frontend.hir.HirNode
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirPatBind
import sublimation.frontend.hir.HirPatCtor
import sublimation.frontend.hir.HirPatLit
import sublimation.frontend.hir.HirPatTuple
import sublimation.frontend.hir.HirReturn
import sublimation.frontend.hir.HirTName
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.hir.HirWhen
import sublimation.frontend.hir.SymbolKind
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 语义层验收（S-3 验收三件套；ADR-6 §五 t7 范围）：作用域/遮蔽/帧退出；未解析诊断+半成品；
 * 消解闭名单四条（方法糖/命名参数/形式参名改写/when 槽位）与构造子裁定；纯函数契约
 * （nid 前序确定性/AST 只读/留痕搬移/Error 容错）；声明层旧码集诊断。
 * 破例说明：compile 不吸收解析期诊断（层间归属），测试统一并袋。
 */
class SemanticTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "test.subl")
        return mergeParseDiag(pr.file, pr.notes, pr.diag)
    }

    private fun project(src: String): Pair<HirFile, DiagBag> {
        val pr = parseSourceWithNotes(src, "test.subl")
        return mergeParseDiag(pr.file, pr.notes, pr.diag)
    }

    private fun mergeParseDiag(
        file: sublimation.frontend.ast.FileAst,
        notes: Map<sublimation.frontend.ast.AstNode, List<sublimation.frontend.parser.ParseNote>>,
        parseDiag: List<Diag>,
    ): Pair<HirFile, DiagBag> {
        val (hir, bag) = compile(file, notes)
        parseDiag.forEach { d ->
            when (d.severity) {
                Severity.ERROR -> bag.error(d.code, d.pos, d.message)
                Severity.WARN -> bag.warn(d.code, d.pos, d.message)
                Severity.HINT -> bag.hint(d.code, d.pos, d.message)
                Severity.SUPPLEMENT -> bag.supplement(d.code, d.pos, d.message)
            }
        }
        return hir to bag
    }

    private fun decls(hir: HirFile) = hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl }

    /** 脚本模式顶层语句（var/fun-stmt 等都是 StmtEntry）。 */
    private fun stmts(hir: HirFile) = hir.entries.mapNotNull { (it as? sublimation.frontend.hir.HirStmtEntry)?.stmt }

    private fun errCodes(bag: DiagBag) = bag.diags
        .filter { it.severity == Severity.ERROR }.map { it.code }

    private fun allCodes(bag: DiagBag) = bag.diags.map { it.code }

    private fun noteCodes(n: HirNode) = n.notes.map { it.code }

    // ============ 作用域 ============

    @Test
    fun scopeShadowing() {
        val (hir, bag) = script("""
            var y = 10
            fun g(): Nat {
                var y = 20
                return y
            }
        """.trimIndent())
        assertFalse(bag.hasError, allCodes(bag).toString())
        val g = decls(hir).filterIsInstance<HirFunDecl>().first()
        val ret = ((g.body as HirBlock).stmts.last() as HirReturn).expr as sublimation.frontend.hir.HirNameRef
        val globalY = stmts(hir).filterIsInstance<HirVarStmt>().first()
        assertEquals(SymbolKind.LOCAL, ret.symbol?.kind, "内层 y 解析到局部绑定")
        assertNotEquals(globalY.symbol?.id, ret.symbol?.id, "内层 y 遮蔽外层 y，符号身份必不同")
        assertEquals("y", ret.name)
    }

    @Test
    fun frameExitHidesName() {
        val (hir, bag) = script("""
            var c = 1
            if (c) { var t = 1 } else { var t = 2 }
            var u = t
        """.trimIndent())
        assertEquals(listOf("E-UNBOUND-NAME"), errCodes(bag), allCodes(bag).toString())
        val varU = hir.entries.mapNotNull { (it as? sublimation.frontend.hir.HirStmtEntry)?.stmt }
            .filterIsInstance<sublimation.frontend.hir.HirVarStmt>().last()
        assertNull((varU.value as sublimation.frontend.hir.HirNameRef).symbol, "未解析名半成品 symbol=null")
        assertTrue(hir.entries.isNotEmpty())
    }

    // ============ 未解析与半成品 ============

    @Test
    fun unboundNameHalfBaked() {
        val (hir, bag) = script("fun f(): Nat = undefined + 1")
        assertEquals(listOf("E-UNBOUND-NAME"), errCodes(bag))
        val f = decls(hir).filterIsInstance<HirFunDecl>().first()
        val bin = f.body as sublimation.frontend.hir.HirBinExpr
        val name = bin.left as sublimation.frontend.hir.HirNameRef
        assertEquals("undefined", name.name)
        assertNull(name.symbol, "半成品落树：未解析引用 symbol=null")
        assertTrue(bag.hasError)
    }

    // ============ 消解闭名单 ============

    @Test
    fun methodSugarDesugars() {
        // t28（卷 TS-2/Q6）：impl 通道废除；定义侧重写为自由函数 fun show(self: Nat)，
        // 调用侧 `n.show()` 仍按 TS-12③ 方法糖展开（receiver 入首参位）
        val (hir, bag) = script("""
            fun show(self: Nat): Str = "s"
            var n = 3
            var s = n.show()
        """.trimIndent())
        assertFalse(bag.hasError, allCodes(bag).toString())
        val varS = stmts(hir).filterIsInstance<HirVarStmt>().last()
        val call = varS.value as HirCall
        assertTrue(call.methodSugar, "xs.f(a) 展开为 f(xs, a)")
        assertEquals(1, call.args.size, "receiver 入首参位")
        assertEquals("n", (call.args[0] as sublimation.frontend.hir.HirNameRef).name)
        val callee = call.callee as sublimation.frontend.hir.HirNameRef
        assertEquals("show", callee.name)
        // t28 范围边界：callee.symbol / D-METHOD-SUGAR 留痕随 impl 方法表通道废除而缺位——
        // 扩展函数接收者分派与闭名单留痕是 P-8/C 语义层任务（ADR-23 §范围边界），本测只锁定解析层平稳落地
    }

    @Test
    fun methodSugarRejectsNamedArg() {
        // t28：自由函数定义侧 + 方法糖调用侧（TS-12③）；消解闭名单 2 不因 impl 废除放宽
        val (hir, bag) = script("""
            fun show(self: Nat): Str = "s"
            var n = 3
            var s = n.show(x = 1)
        """.trimIndent())
        assertEquals(listOf("E-NAMED-ARG"), errCodes(bag), allCodes(bag).toString())
        val call = (stmts(hir).filterIsInstance<HirVarStmt>().last()).value as HirCall
        assertTrue(call.methodSugar)
        assertEquals("show", (call.callee as sublimation.frontend.hir.HirNameRef).name, "半成品树保留形态")
    }

    @Test
    fun namedArgsReordered() {
        val (hir, bag) = script("""
            fun add(a: Nat, b: Nat): Nat = a
            var r = add(b = 2, a = 1)
        """.trimIndent())
        assertFalse(bag.hasError, allCodes(bag).toString())
        val call = (stmts(hir).filterIsInstance<HirVarStmt>().last()).value as HirCall
        assertEquals(listOf("1", "2"), call.args.map { (it as HirIntLit).value }, "重排为声明序 a=1, b=2（字面量保留原文）")
        assertEquals(SymbolKind.FUN, call.symbol?.kind)
        assertTrue(noteCodes(call).contains("D-NAMED-ARG"), "闭名单留痕 D-NAMED-ARG")
    }

    @Test
    fun namedArgErrors() {
        val unknown = script("""
            fun add(a: Nat, b: Nat): Nat = a
            var r = add(c = 1)
        """.trimIndent()).second
        assertEquals(listOf("E-NAMED-ARG"), errCodes(unknown))

        val taken = script("""
            fun add(a: Nat, b: Nat): Nat = a
            var r = add(1, a = 2)
        """.trimIndent()).second
        assertEquals(listOf("E-NAMED-ARG"), errCodes(taken))
    }


    @Test
    fun dollarNRewritesToParamName() {
        val (hir, bag) = script("fun cmp(a: Int, b: Int)<$1 < $2>: Bool = True()")
        assertFalse(bag.hasError, allCodes(bag).toString())
        val cmp = decls(hir).filterIsInstance<HirFunDecl>().first()
        val pre = cmp.preps[0] as HirPAtom
        assertEquals("<", pre.head)
        val t0 = pre.terms[0] as HirTName
        assertEquals("a", t0.name, "上下文美元符 1 改写为形参名 a")
        assertNotNull(t0.symbol)
        assertTrue(noteCodes(t0).contains("D-DOLLAR-N"), "改写留痕 D-DOLLAR-N")
        assertTrue(allCodes(bag).contains("H-PARAM-REF"), "提示码 H-PARAM-REF")
    }

    @Test
    fun dollarNBounds() {
        val oob = script("fun f(a: Int)<$2 > 0>: Bool = True()").second
        assertEquals(listOf("E-PARAM-REF"), errCodes(oob), "越界")

        val inBody = script("fun f(a: Nat): Nat = $1").second
        assertEquals(listOf("E-PARAM-REF-IN-BODY"), errCodes(inBody), "函数体内禁止上下文引用")
    }

    @Test
    fun whenSlotOldNameBecomesComparison() {
        val (hir, bag) = script("""
            var n = 1
            var p = (2, 3)
            var q = when (p) { (n, b) -> b else -> 0 }
        """.trimIndent())
        assertFalse(bag.hasError, allCodes(bag).toString())
        val w = (stmts(hir).filterIsInstance<HirVarStmt>().last()).value as HirWhen
        val tup = w.arms[0].pattern as HirPatTuple
        assertTrue(tup.items[0] is HirPatLit, "槽位旧名归一为相等比较")
        assertTrue(noteCodes(tup.items[0]).contains("D-PAT-SLOT"), "闭名单留痕 D-PAT-SLOT")
        val fresh = tup.items[1] as HirPatBind
        assertEquals("b", fresh.name)
        assertEquals(SymbolKind.LOCAL, fresh.symbol?.kind, "新名绑定入帧")
    }

    @Test
    fun bareAndIsCtorAdjudication() {
        val (hir, bag) = script("""
            struct None()
            var x = 0
            var r1 = when (x) { None -> 1 else -> 0 }
            var r2 = when (x) { is None -> 1 else -> 0 }
        """.trimIndent())
        assertFalse(bag.hasError, allCodes(bag).toString())
        val vars = stmts(hir).filterIsInstance<HirVarStmt>()
        val bare = (vars[1].value as HirWhen).arms[0].pattern as HirPatCtor
        assertEquals("None", bare.name)
        assertNotNull(bare.ctor, "裸名构造子按 ctor 表裁定")
        assertTrue(noteCodes(bare).contains("D-PAT-CTOR"), "留痕 D-PAT-CTOR")
        // t41（ADR-28 R-19）：is None 是**类型形态**（None 为 struct 注册类型，不归一为 ctor 模式）
        val isForm = (vars[2].value as HirWhen).arms[0].pattern as sublimation.frontend.hir.HirPatIs
        assertEquals("None", (isForm.type as sublimation.frontend.hir.HirNamedType).name, "is 零字段 struct 保留类型形态模式")
    }

    @Test
    fun payloadedCtorBareArmErrors() {
        val (hir, bag) = script("""
            struct Some(x: Nat)
            var x = 0
            var r = when (x) { Some -> 1 else -> 0 }
        """.trimIndent())
        assertEquals(listOf("E-CTOR-ARITY"), errCodes(bag), allCodes(bag).toString())
        val w = (stmts(hir).filterIsInstance<HirVarStmt>().last()).value as HirWhen
        val pat = w.arms[0].pattern as HirPatCtor
        assertEquals("Some", pat.name, "半成品仍落构造子形态")
    }

    // ============ 声明层诊断（旧码集）============

    @Test
    fun duplicateDeclAndAliasClash() {
        val dup = project("""
            fun f(): Nat = 1
            fun f(): Nat = 2
        """.trimIndent()).second
        assertEquals(listOf("E-DUP-DECL"), errCodes(dup))

        val alias = project("""
            struct S(x: Nat)
            type S = Nat
        """.trimIndent()).second
        assertEquals(listOf("E-DUP-DECL"), errCodes(alias), "类型别名与既有类型重名")
    }

// ============ 纯函数契约 ============

    @Test
    fun nidPreOrderDeterministic() {
        val src = """
            fun f(a: Nat, b: Nat): Bool <⊤> = True()   // t9：posts 须可证（a < b 无界证不出，改平凡命题）
            struct None()
            var x = 0
            var q = when (x) { None -> 1 else -> 0 }
        """.trimIndent()
        val (hir1, bag1) = script(src)
        val (hir2, bag2) = script(src)
        assertFalse(bag1.hasError, allCodes(bag1).toString())
        assertEquals(hir1, hir2, "同一源两次编译，HIR（含 nid）完全一致")
        assertFalse(hir1 === hir2, "两次产物是独立树（非缓存共享）")
    }

    @Test
    fun astReadOnlyContract() {
        val pr = parseScriptSourceWithNotes("fun f(): Nat = 1\nvar x = f()", "t")
        val before = pr.file.toString()
        val beforeNotes = pr.notes.mapValues { it.value.size }
        val (hir, bag) = compile(pr.file, pr.notes)
        assertFalse(bag.hasError, allCodes(bag).toString())
        assertEquals(before, pr.file.toString(), "elaborate 不得改写 AST")
        assertEquals(beforeNotes, pr.notes.mapValues { it.value.size }, "解析期留痕映射不改写")
        assertNotNull(hir)
    }

    @Test
    fun parseNotesRelocatedToHirNodes() {
        val (hir, bag) = script("fun app(a: Nat, f: (Nat) => Nat): Nat = a\nvar s = app(1) { x => x }")
        assertFalse(bag.hasError, allCodes(bag).toString())
        val call = (stmts(hir).filterIsInstance<HirVarStmt>().first()).value as HirCall
        assertTrue(noteCodes(call).contains("D-TRAILING"), "解析期留痕搬移到对应 HirCall")
        val note = call.notes.first { it.code == "D-TRAILING" }
        assertEquals(sublimation.frontend.hir.HirNoteKind.DESUGAR, note.kind, "D 留痕归类 DESUGAR")
    }

    @Test
    fun builtinAndTypeParamSymbols() {
        val (hir, bag) = script("""
            var s = toStr(1)
            fun id[T](x: T): T = x
        """.trimIndent())
        assertFalse(bag.hasError, allCodes(bag).toString())
        val built = (stmts(hir).filterIsInstance<HirVarStmt>().first()).value as HirCall
        assertEquals(SymbolKind.BUILTIN, built.symbol?.kind, "内建函数按 BUILTIN 登记")
        val id = decls(hir).filterIsInstance<HirFunDecl>().first()
        val paramT = id.params[0].type as sublimation.frontend.hir.HirNamedType
        assertNull(paramT.symbol, "理论类型参数引用静默（无 SymbolKind.TYPEPARAM）")
        val retT = id.retType as sublimation.frontend.hir.HirNamedType
        assertNull(retT.symbol)
    }

    @Test
    fun defRemovedReportsErrorCode() {
        val (_, bag) = project("def P<n> ↔ n > 0")
        // P-9（t40）：def 已废除——命题等价式改由顶层 axiom 断言承担（t41 任务 5：axiom Even(4) ↔ …）
        assertTrue(errCodes(bag).contains("E-DEF-REMOVED"), allCodes(bag).toString())
    }

    // ============ 哨兵与容错 ============

    @Test
    fun propOnlySyntaxInProgramPosition() {
        // t79（Q20，用户裁决 2026-11-09）：⊤/⊥ 是命题专用关键词——程序位落 E-PROP-POS 并提示
        // True / False。BEFORE（旧 C1 / S-34 / Checker:1006 双身份口径）：`var x = ⊤` 合法产
        // HirTop；AFTER：落 E-PROP-POS（红→绿属规格强制，ADR-50 §二记录）。
        // 其余命题专属语法（量词 / $ 返回符号）仍落同一哨兵。
        val (_, bagT) = script("var x = ⊤")
        assertTrue(bagT.diags.any { it.code == "E-PROP-POS" }, "⊤ 于程序位落 E-PROP-POS: " + allCodes(bagT))
       	val (_, bagB) = script("var x = ⊥")
        assertTrue(bagB.diags.any { it.code == "E-PROP-POS" }, "⊥ 于程序位落 E-PROP-POS: " + allCodes(bagB))
        val (_, bagQ) = script("var q = ∀x . True")
        assertTrue(bagQ.diags.any { it.code == "E-PROP-POS" }, "量词于程序位仍报 E-PROP-POS")
    }

    @Test
    fun propFormUnrepresentable() {
        val (hir, bag) = script("fun f(): Bool <{ 1 }> = True()")
        assertEquals(listOf("E-PROP-FORM"), errCodes(bag), allCodes(bag).toString())
        val f = decls(hir).filterIsInstance<HirFunDecl>().first()
        assertTrue(f.posts[0] is sublimation.frontend.hir.HirPBot, "命题位半成品 PBot 占位")
    }

    @Test
    fun errorNodesDoNotCrash() {
        val (hir, bag) = script("var x = 1 +")
        assertTrue(bag.hasError)
        assertTrue(bag.diags.any { it.code.startsWith("E-") }, "诊断落袋：" + allCodes(bag))
        assertNotNull(hir, "半成品树仍返回")
    }
}

