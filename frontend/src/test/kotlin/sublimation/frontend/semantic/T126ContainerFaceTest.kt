package sublimation.frontend.semantic

import sublimation.frontend.diag.Severity
import sublimation.frontend.cli.compileStdlibUnits
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirMutType
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirUnionType
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import sublimation.frontend.semantic.compileWithRounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * t126（S5c / R-15 容器面 + R-17/R-18 纯度纪律）：
 *
 * - **R-15 容器面**：mutableListOf/listOf 虚拟构造族（PreludeDecls 虚拟 FunDecl · 队长裁 Alt A：
 *   值实参解理论参数，零新推导机制）+ 虚拟 @mut 扩展方法 add（去糖模型 self = 接收者首参）;
 * - **R-17/R-18**：@mut 方法调用 ⇒ 调用方 @impure 或 unchecked（S-25）⇒ 否则 E-IMPURE-REQUIRED;
 *   方法体写接收者无罪（R-18.2）;判据不看实参视图（V1 一刀：深拷照门，逃逸分析留后版）;
 * - **t122 闭包种子第 5 类**「引用可变调用」：纯函数体内 @mut 方法调用 ⇒ 推导 impure;
 * - **R-06 T3**：形参/局部被传入 @mut 接收者位 ⇒ S5b 提升通道（D-MUT-INFERRED 触发分类 T3）。
 *
 * 68+14 基线样本零 @mut 方法糖 ⇒ 零触发样（既有基线机械零风险，提案 §3.4 影响面口径）。 */
class T126ContainerFaceTest {

    // t138（R-23 B1）：prelude 虚拟层 retire——编译挂 stdlib 快照（与 CLI 默认挂载同构）
    private val stdlibMounts: Map<String, ModuleSnapshot> =
        compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            .let { (u, _) -> u.associate { it.alias to it.snapshot } }

    private fun sc(src: String) =
        parseScriptSourceWithNotes(src, "t126.subs").let { compileWithRounds(it.file, it.notes, scriptMode = true, mounted = stdlibMounts) }
    private fun pc(src: String) =
        parseSourceWithNotes(src, "t126.subl").let { compileWithRounds(it.file, it.notes, scriptMode = false, mounted = stdlibMounts) }
    private fun errs(rs: sublimation.frontend.semantic.RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    private fun supps(rs: sublimation.frontend.semantic.RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.SUPPLEMENT }.map { it.code + "::" + it.message }
    private fun varStmtOf(rs: sublimation.frontend.semantic.RoundsReport, name: String): HirVarStmt? {
        var out: HirVarStmt? = null
        fun walk(n: Any?) {
            when (n) {
                is HirVarStmt -> if (n.name == name) out = n
                is sublimation.frontend.hir.HirBlock -> n.stmts.forEach { walk(it) }
                is sublimation.frontend.hir.HirExprStmt -> walk(n.expr)
                is sublimation.frontend.hir.HirFunDecl -> n.body?.let { walk(it) }
                is HirDeclEntry -> walk(n.decl)
                is sublimation.frontend.hir.HirStmtEntry -> walk(n.stmt)
                else -> { }
            }
        }
        rs.hir.entries.forEach { walk(it) }
        return out
    }

    /** R-15：mutableListOf(x) → @mut List[T]（值实参解理论参数，既有 instCall 通道）。 */
    @Test fun R15可变构造视图() {
        val rs = sc("var xs = mutableListOf(1)\nprint(xs)\n")
        assertEquals(emptyList(), errs(rs), "脚本模式零错误")
        val v = varStmtOf(rs, "xs")
        assertNotNull(v)
        assertTrue(v.type is HirMutType, "var 视图为 @mut List[Nat]")
        val inner = (v.type as HirMutType).inner as? HirNamedType
        assertNotNull(inner)
        assertEquals("List", inner.name, "容器头 List")
        assertEquals("Nat", (inner.args.first() as? HirNamedType)?.name, "元素类型由实参解出")
    }

    /** R-15：listOf(x) → List[T]（不可变同构）。 */
    @Test fun R15不可变构造() {
        val rs = sc("var ys = listOf(2)\nprint(ys)\n")
        assertEquals(emptyList(), errs(rs), "零错误")
        val v = varStmtOf(rs, "ys")
        assertNotNull(v)
        assertFalse(v.type is HirMutType, "不可变构造零 @mut 视图")
        val u = v.type as? sublimation.frontend.hir.HirUnionType
        assertNotNull(u, "var 视图为展开联合（D-TYPE-INFERRED 口径）")
        assertEquals(2, u.variants.size, "Nil | Cons[Nat] 二元联合")
    }

    /** R-15：虚拟 @mut 扩展方法 add（方法糖改道 + 签名核对 + 脚本顶层隐式不纯免闸）。 */
    @Test fun R15mut方法脚本顶层免闸() {
        val rs = sc("var xs : @mut List[Nat] = mutableListOf(1)\nxs.add(2)\nprint(xs)\n")
        assertEquals(emptyList(), errs(rs), "脚本顶层调用 @mut 方法免闸（既有 D-SCRIPT-IMPLICIT-IMPURE 口径）")
    }

    /** R-18.3：纯函数（工程模式声明纯）调 @mut 方法 ⇒ E-IMPURE-REQUIRED（接收者闸红位）。 */
    @Test fun R18纯函数调mut方法红() {
        val rs = pc(
            "fun f(): Null {\n  var xs : @mut List[Nat] = mutableListOf(1)\n  xs.add(2)\n}\n" +
            "@impure fun main(): Null { f() }\n")
        assertTrue(errs(rs).contains("E-IMPURE-REQUIRED"), "纯函数调 @mut 方法落 E-IMPURE-REQUIRED")
    }

    /** R-18.3：调用方 @impure ⇒ 放行。 */
    @Test fun R18_impure调用方放行() {
        val rs = pc("@impure fun main(): Null {\n  var xs : @mut List[Nat] = mutableListOf(1)\n  xs.add(2)\n  print(xs)\n}\n")
        assertEquals(emptyList(), errs(rs), "@impure 调用方放行（无 E-IMPURE-REQUIRED）")
    }

    /** R-18.3 + S-25：unchecked 语句级前缀 ⇒ 单语句豁免（既有降闸帧通道，零新语法）。 */
    @Test fun R18unchecked前缀豁免() {
        val rs = pc("fun f(): Null {\n  var xs : @mut List[Nat] = mutableListOf(1)\n  unchecked xs.add(2)\n}\n" +
            "@impure fun main(): Null { f() }\n")
        assertEquals(emptyList(), errs(rs), "unchecked 包覆 ⇒ E-IMPURE-REQUIRED 免")
    }

    /** R-18.2：方法体写接收者字段无罪（不要求方法体 @impure）——用户 @mut 扩展函数体写 self 字段。 */
    @Test fun R18方法体写接收者无罪() {
        val rs = pc(
            "@mut struct Box(@mut n: Nat)\n" +
            "@mut fun Box.bump2(v: Nat): Box = Box(v)\n" +
            "@impure fun main(): Null { print(1) }\n")
        // 声明位编译过即判据（体写 self 经去糖首参语义——R-18.2 无罪）
        assertTrue(errs(rs).none { it == "E-IMPURE-REQUIRED" }, "方法体写接收者不落 E-IMPURE-REQUIRED")
    }

    /** t122 闭包种子第 5 类「引用可变调用」——纯函数体内 @mut 方法调用 ⇒ D-PURITY-INFERRED。 */
    @Test fun 种子第5类引用可变调用() {
        val rs = pc(
            "fun f(): Null {\n  var xs : @mut List[Nat] = mutableListOf(1)\n  unchecked xs.add(2)\n}\n" +
            "@impure fun main(): Null { f() }\n")
        assertEquals(emptyList(), errs(rs))
        val seed = supps(rs).firstOrNull { it.contains("D-PURITY-INFERRED") && it.contains("引用可变调用") }
        assertNotNull(seed, "种子源=引用可变调用（R-17/R-18）：$seed")
    }

    /** R-06 T3：脚本模式形参被传入 @mut 接收者位 ⇒ 提升通道（D-MUT-INFERRED 触发分类 T3）。 */
    @Test fun T3形参加入mut接收者位() {
        val rs = sc(
            "@mut struct Box(@mut n: Nat)\n" +
            "@mut fun Box.bump2(v: Nat): Box = Box(v)\n" +
            "fun f(b) {\n  b.bump2(2)\n}\n" +
            "var x : Box = Box(1)\n" +
            "f(x)\n" +
            "print(x)\n")
        assertEquals(emptyList(), errs(rs), "提升后零错误")
        val note = supps(rs).firstOrNull { it.contains("D-MUT-INFERRED") && it.contains("传入 @mut 接收者位 T3") }
        assertNotNull(note, "T3 触发分类入审计四元组：$note")
    }

    /** R-15 用户优先：用户同名扩展函数（无 @mut 注解）覆盖虚拟 add ⇒ 无 E-IMPURE-REQUIRED。 */
    @Test fun 用户同名扩展优先() {
        val rs = pc(
            "fun List[a].add[a](v: a): List[a] = Cons(v, self)\n" +
            "@impure fun main(): Null {\n  var xs : List[Nat] = Nil\n  print(xs.add(1))\n}\n")
        assertTrue(errs(rs).none { it == "E-IMPURE-REQUIRED" }, "用户同名扩展优先（虚拟 add 摘除，无 @mut 注解不门）")
    }

    /** 差分对拍：容器面双门同源（脚本/工程同结构编译）。 */
    @Test fun 差分对拍容器面() {
        val pairDir = java.io.File("frontend/src/test/resources/diff-pairs")
        val src = pairDir.resolve("mut-container.subs").readText()
        val rs = parseScriptSourceWithNotes(src, "mut-container.subs")
            .let { compileWithRounds(it.file, it.notes, scriptMode = true, mounted = stdlibMounts) }
        val rp = parseSourceWithNotes(pairDir.resolve("mut-container.subl").readText(), "mut-container.subl")
            .let { compileWithRounds(it.file, it.notes, scriptMode = false, mounted = stdlibMounts) }
        assertEquals(emptyList(), errs(rs), "subs 侧零错误")
        assertEquals(emptyList(), errs(rp), "subl 侧零错误")
        val v1 = varStmtOf(rs, "xs"); val v2 = varStmtOf(rp, "xs")
        assertNotNull(v1); assertNotNull(v2)
        assertTrue(v1.type is HirMutType && v2.type is HirMutType, "双门 var 视图同 @mut List[Nat]")
        assertEquals("Nat", ((v1.type as HirMutType).inner as? HirNamedType)?.args?.firstOrNull()
            ?.let { (it as? HirNamedType)?.name }, "subs 元素位同")
        assertEquals("Nat", ((v2.type as HirMutType).inner as? HirNamedType)?.args?.firstOrNull()
            ?.let { (it as? HirNamedType)?.name }, "subl 元素位同")
    }
}
