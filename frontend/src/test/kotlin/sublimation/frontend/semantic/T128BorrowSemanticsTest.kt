package sublimation.frontend.semantic

import sublimation.frontend.diag.Severity
import sublimation.frontend.cli.compileStdlibUnits
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.hir.HirBorrow
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirMutType
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirParam
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import sublimation.frontend.semantic.compileWithRounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * t128（S6 / R-21 落地）：& 借用运行时语义——
 *
 * - **R-01 want 合成**：调用边界 got 不动、want 加 @ref 衣（[TypeInfer.refWrap]），
 *   copyDispatch 矩阵 16 格逐字复用（共享/深拷两通过形态 + 两拒绝格落 E-TYPE-MISMATCH）；
 * - **R-21.1 视图规范化**：@ref @ref T ≡ @ref T 折叠 + 联合别名归一（振荡同根治理）；
 * - **R-04.3 W-BORROW-REDUNDANT**：只读形参位点借用（分派不变）落 warn，含提升轮免陈旧口径；
 * - **后段契约**：分派结论挂 HirBorrow.notes（D-REF-DISPATCH「借用实参 i」）、形参视图回写
 *   既有载体（S5 元变量解/提升衣）、纯度门照门（借用不绕过 E-IMPURE-REQUIRED 链）。
 *
 * 差分对拍 fixture：frontend/src/test/resources/diff-pairs/borrow-*（5 对双形态样本）。 */
class T128BorrowSemanticsTest {

    // t138（R-23 B1）：prelude 虚拟层 retire——编译挂 stdlib 快照（与 CLI 默认挂载同构）
    private val stdlibMounts: Map<String, ModuleSnapshot> =
        compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            .let { (u, _) -> u.associate { it.alias to it.snapshot } }

    private fun sc(src: String) =
        parseScriptSourceWithNotes(src, "t128.subs").let { compileWithRounds(it.file, it.notes, scriptMode = true, mounted = stdlibMounts) }
    private fun pc2(src: String) =
        parseSourceWithNotes(src, "t128.subl").let { compileWithRounds(it.file, it.notes, scriptMode = false, mounted = stdlibMounts) }
    private fun errs(rs: RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    private fun warns(rs: RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.WARN }.map { it.code }
    private fun supps(rs: RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.SUPPLEMENT }.map { it.code + "::" + it.message }

    /** 全树首个 HirBorrow（walker：块体/实参位递归）。 */
    private fun borrowOf(rs: RoundsReport): HirBorrow? {
        var out: HirBorrow? = null
        fun walk(n: Any?) {
            when (n) {
                is HirBorrow -> if (out == null) out = n
                is sublimation.frontend.hir.HirCall -> n.args.forEach { walk(it) }
                is sublimation.frontend.hir.HirBlock -> n.stmts.forEach { walk(it) }
                is sublimation.frontend.hir.HirExprStmt -> walk(n.expr)
                is sublimation.frontend.hir.HirVarStmt -> walk(n.value)
                is sublimation.frontend.hir.HirUnchecked -> walk(n.inner)
                is HirFunDecl -> n.body?.let { walk(it) }
                is HirDeclEntry -> walk(n.decl)
                is sublimation.frontend.hir.HirStmtEntry -> walk(n.stmt)
                else -> { }
            }
        }
        rs.hir.entries.forEach { walk(it) }
        return out
    }

    private fun borrowNote(rs: RoundsReport): String? = borrowOf(rs)?.notes
        ?.firstOrNull { it.code == "D-REF-DISPATCH" }?.message

    private fun funParams(rs: RoundsReport, name: String): List<HirParam>? {
        var out: List<HirParam>? = null
        fun walk(n: Any?) {
            when (n) {
                is HirFunDecl -> if (n.name == name) out = n.params
                is HirDeclEntry -> walk(n.decl)
                else -> { }
            }
        }
        rs.hir.entries.forEach { walk(it) }
        return out
    }

    private val sharedSrc = "@impure fun bump(xs : @mut List[Nat]) { xs.add(1) }\n" +
        "@impure fun main(): Null {\n  var xs : @mut List[Nat] = mutableListOf(1)\n  unchecked bump(&xs)\n}\n"
    private val deepSrc = "@impure fun bump(xs : @mut List[Nat]) { xs.add(1) }\n" +
        "@impure fun main(): Null {\n  var xs : @mut List[Nat] = mutableListOf(1)\n  unchecked bump(xs)\n}\n"

    /** R-01 矩阵共享格（:113）：@mut 实参 × want 合成 @ref @mut ⇒ 类型通过 + D-REF-DISPATCH 留共享。 */
    @Test fun R01共享格类型通过() {
        val rs = sc(sharedSrc)
        assertEquals(emptyList(), errs(rs), "借用入 @mut 形参零错误")
        val note = borrowNote(rs)
        assertNotNull(note, "HirBorrow 须挂 D-REF-DISPATCH")
        assertTrue(note!!.contains("借用实参 1 视图分派：@mut List[Nat] → @ref @mut List[Nat]"),
            "want 合成渲染：$note")
        assertTrue(note.contains("共享同一对象"), "共享分派结论：$note")
        assertFalse(note.contains("元素层"), "共享路径零下钻（R-14 §2.2 既有覆盖）：$note")
    }

    /** R-01 对侧深拷格（:104）：同源无 & 形态零借用节点编译过（双形态矩阵同源）。 */
    @Test fun R01深拷格对照() {
        val rs = sc(deepSrc)
        assertEquals(emptyList(), errs(rs), "无 & 形态零错误")
        assertEquals(null, borrowOf(rs), "深拷侧无借用节点")
    }

    /** 矩阵拒绝格 1（T → @ref @mut T）：不可变实参借入 @mut 形参 ⇒ E-TYPE-MISMATCH（负例先红）。 */
    @Test fun 矩阵拒绝格1不可变实参() {
        val rs = sc("@impure fun bump(xs : @mut List[Nat]) { xs.add(1) }\n" +
            "@impure fun main(): Null {\n  var ys : List[Nat] = listOf(2)\n  unchecked bump(&ys)\n}\n")
        assertTrue(errs(rs).any { it == "E-TYPE-MISMATCH" }, "拒绝格 1 须红：${errs(rs)}")
    }

    /** 矩阵拒绝格 2（@ref T → @ref @mut T）：只读引用实参借入 @mut 形参 ⇒ E-TYPE-MISMATCH（负例先红）。 */
    @Test fun 矩阵拒绝格2ref实参() {
        val rs = sc("@impure fun g(z : @mut Nat) { z = z + 1 }\n" +
            "@impure fun main(): Null {\n  var v : @ref Nat = 3\n  unchecked g(&v)\n}\n")
        assertTrue(errs(rs).any { it == "E-TYPE-MISMATCH" }, "拒绝格 2 须红：${errs(rs)}")
    }

    /** 方法糖实参位借用：xs.add(&one) 走扩展签名 want 合成（receiver 位不借——R-03 解析通道）。 */
    @Test fun 方法糖实参位借用() {
        val rs = sc("var one : Nat = 1\n" +
            "@impure fun main(): Null {\n  var xs : @mut List[Nat] = mutableListOf(1)\n  unchecked xs.add(&one)\n}\n")
        assertEquals(emptyList(), errs(rs), "方法糖借用零错误")
        val note = borrowNote(rs)
        assertNotNull(note, "方法糖实参位留痕：$note")
        assertTrue(note!!.contains("借用实参 2"), "实参位序（receiver 首参后第一位）：$note")
    }

    /** 构造子位借用：Cons(&h, Nil) 走 callCtor want 合成（字段位同矩阵口径）。 */
    @Test fun 构造子位借用() {
        val rs = sc("@impure fun main(): Null {\n  var h : Nat = 1\n  var ys : List[Nat] = Cons(&h, Nil)\n}\n")
        assertEquals(emptyList(), errs(rs), "构造子借用零错误")
        assertNotNull(borrowNote(rs), "构造子位 D-REF-DISPATCH 留痕")
    }

    /** R-04.3 W-BORROW-REDUNDANT：只读形参（Nat）位点借用——分派不变（共享 vs 共享）⇒ warn。 */
    @Test fun WBORROWREDUNDANT只读形参位() {
        val rs = sc("fun double(n : Nat) { print(n + n) }\n" +
            "@impure fun main(): Null {\n  var x : Nat = 3\n  double(&x)\n}\n")
        assertEquals(emptyList(), errs(rs), "借用透明过境无错误")
        assertEquals(listOf("W-BORROW-REDUNDANT"), warns(rs), "仅一条冗余 warn")
        val msg = rs.diag.diags.first { it.code == "W-BORROW-REDUNDANT" }.message
        assertTrue(msg.contains("double") && msg.contains("n"), "warn 定位被调/形参：$msg")
    }

    /** R-04.3 非冗余口径：@mut 实参借入只读形参（无 & 深拷 vs 有 & 共享——分派有差）⇒ 零 warn。 */
    @Test fun WBORROW非冗余可变实参只读形参() {
        val rs = sc("fun len(xs) { print(xs) }\n" +
            "var xs : @mut List[Nat] = mutableListOf(1)\nunchecked len(&xs)\nprint(xs)\n")
        assertEquals(emptyList(), errs(rs))
        assertEquals(emptyList(), warns(rs), "深拷→共享有语义差，warn 免")
        assertNotNull(borrowNote(rs), "借用仍留痕（共享分派）")
    }

    /** R-04.3 陈旧口径：形参经 T3 提升为 @mut 后借用转为有效 ⇒ 提升轮免 warn（不翻案）。 */
    @Test fun 提升轮形参翻mut免陈旧warn() {
        val rs = sc("fun bump(xs) { unchecked xs.add(1) }\n" +
            "var xs : @mut List[Nat] = mutableListOf(1)\nunchecked bump(&xs)\nprint(xs)\n")
        assertEquals(emptyList(), errs(rs), "提升链零错误")
        assertEquals(emptyList(), warns(rs), "形参翻 @mut 后借用有效，无冗余 warn")
        assertTrue(supps(rs).any { it.contains("D-MUT-INFERRED") && it.contains("T3") },
            "T3 提升审计在位：${supps(rs)}")
    }

    /** §1.3 形参视图回写载体：省略型形参经提升闭合后 HirParam.type 载 @mut 衣（零新字段）。 */
    @Test fun 形参视图回写载体() {
        val rs = sc("fun bump(xs) { unchecked xs.add(1) }\n" +
            "var xs : @mut List[Nat] = mutableListOf(1)\nunchecked bump(&xs)\nprint(xs)\n")
        val ps = funParams(rs, "bump")
        assertNotNull(ps)
        assertEquals(1, ps!!.size)
        val t = ps[0].type
        assertTrue(t is HirMutType && (t.inner as? HirNamedType)?.name == "List",
            "形参视图载体为 @mut List[…]：$t")
    }

    /** R-21.1 嵌套 @ref 折叠：@ref @ref Nat 形参 + &x ⇒ want 合成折叠为 @ref Nat。 */
    @Test fun R211嵌套ref折叠() {
        val rs = sc("fun g(z : @ref @ref Nat) { print(z) }\n" +
            "@impure fun main(): Null {\n  var x : Nat = 3\n  g(&x)\n}\n")
        assertEquals(emptyList(), errs(rs), "折叠后类型核对通过")
        val note = borrowNote(rs)
        assertNotNull(note)
        assertTrue(note!!.contains("→ @ref Nat"), "want 折叠渲染（无 @ref @ref）：$note")
    }

    /** R-21.1 联合别名归一：List[Nat] 与 Nil | Cons[Nat] 跨轮同根治理（振荡退化消除）。 */
    @Test fun R211联合别名归一() {
        val rs = sc("fun bump(xs) { print(xs) }\n" +
            "var ys : List[Nat] = Nil\nbump(ys)\nprint(ys)\n")
        assertEquals(emptyList(), errs(rs))
        assertFalse(supps(rs).any { it.contains("D-INFER-DEGRADED") }, "振荡退化须已消除：${supps(rs)}")
        val ps = funParams(rs, "bump")
        assertNotNull(ps)
        val t = ps!![0].type as? HirNamedType
        assertNotNull(t, "归一为别名头形态：${ps[0].type}")
        assertEquals("List", t!!.name, "联合别名归一到 List 头")
    }

    /** 后段契约：借用链不绕过纯度门——callee @mut 方法（种子第 5 类）⇒ 传播闭包使调用方推导 impure。 */
    @Test fun 借用不绕过纯度门() {
        val rs = sc("fun g(xs : @mut List[Nat]) { xs.add(1) }\n" +
            "@pure fun f(): Null {\n  var xs : @mut List[Nat] = mutableListOf(1)\n  g(&xs)\n}\n" +
            "@impure fun main(): Null { print(1) }\n")
        assertTrue(supps(rs).any { it.contains("D-PURITY-INFERRED") && it.contains("引用可变调用") },
            "种子第 5 类经借用实参照燃：${supps(rs)}")
        assertTrue(supps(rs).any { it.contains("D-PURITY-INFERRED") && it.contains("传播") && it.contains("f") },
            "闭包传播链 f ← g 在位：${supps(rs)}")
    }

    /** 差分对拍：R-01 双形态 fixture 批——subs 侧借形态 / subl 侧对形态，红绿按矩阵格断言。 */
    @Test fun 差分对拍borrow双形态批() {
        val pairDir = java.io.File("frontend/src/test/resources/diff-pairs")
        val redGreen = listOf("borrow-neg-plain", "borrow-neg-ref")
        redGreen.forEach { name ->
            val rs = parseScriptSourceWithNotes(pairDir.resolve("$name.subs").readText(), "$name.subs")
                .let { compileWithRounds(it.file, it.notes, scriptMode = true, mounted = stdlibMounts) }
            val rp = parseSourceWithNotes(pairDir.resolve("$name.subl").readText(), "$name.subl")
                .let { compileWithRounds(it.file, it.notes, scriptMode = false, mounted = stdlibMounts) }
            assertTrue(errs(rs).any { it == "E-TYPE-MISMATCH" }, "$name.subs 拒绝格须红：${errs(rs)}")
            assertEquals(emptyList(), errs(rp), "$name.subl 对形态绿（造格放行）")
        }
    }

    /** 差分对拍：借/对形态通过批 + W-BORROW-REDUNDANT 负例对（warn 只落借用形态）。 */
    @Test fun 差分对拍borrow通过warn批() {
        val pairDir = java.io.File("frontend/src/test/resources/diff-pairs")
        listOf("borrow-shared", "borrow-ctor", "borrow-warn").forEach { name ->
            val rs = parseScriptSourceWithNotes(pairDir.resolve("$name.subs").readText(), "$name.subs")
                .let { compileWithRounds(it.file, it.notes, scriptMode = true, mounted = stdlibMounts) }
            val rp = parseSourceWithNotes(pairDir.resolve("$name.subl").readText(), "$name.subl")
                .let { compileWithRounds(it.file, it.notes, scriptMode = false, mounted = stdlibMounts) }
            assertEquals(emptyList(), errs(rs), "$name.subs 零错误")
            assertEquals(emptyList(), errs(rp), "$name.subl 零错误")
        }
        val rsW = parseScriptSourceWithNotes(pairDir.resolve("borrow-warn.subs").readText(), "borrow-warn.subs")
            .let { compileWithRounds(it.file, it.notes, scriptMode = true, mounted = stdlibMounts) }
        assertEquals(listOf("W-BORROW-REDUNDANT"), warns(rsW), "负例对借形态落 warn")
    }
}
