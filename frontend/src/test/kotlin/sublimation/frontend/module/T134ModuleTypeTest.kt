package sublimation.frontend.module

import sublimation.frontend.cli.FrontendOutput
import sublimation.frontend.cli.compileModuleFrontend
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirField
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirUnionType
import sublimation.frontend.hir.HirVarStmt
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * t134（S7 快照扩类型面 / R-23）：跨模块**类型别名/结构体/扩展函数**限定通道。
 * 通道形态（R-22 查询链 + R-23 非限定链）：
 * - 类型面（别名/结构体）：限定形态 tool.T[args] 折叠解析 + 父域声明索引注入（P-7 用户覆盖）；
 * - 扩展函数：方法糖查询链（本模块 > 挂载子 > prelude 虚拟）；
 * - 结构体构造：非限定名（moduleCtor 层——P-7 用户 ctor > 挂载模块 > prelude 虚拟）。 */
class T134ModuleTypeTest {

    private fun root(tag: String): File {
        val d = File("frontend/build/tmp/t134/" + tag)
        if (d.exists()) d.deleteRecursively()
        d.mkdirs()
        return d
    }

    /** 挂载模块：type Box[T] = Some[T] | None（别名）+ struct Pair（私有结构体）+ fun Nat.dbl 扩展。 */
    private fun typeSample(tag: String, userBody: String): File {
        val r = root(tag)
        File(r, "主.subl").writeText(userBody, Charsets.UTF_8)
        val tool = File(r, "tool"); tool.mkdirs()
        File(tool, "t.subl").writeText(
            "type Box[T] = Some[T] | None\n" +
            "struct Pair(a : Nat, b : Nat)\n" +
            "@impure fun dbl(n : Nat) : Nat = n * 2\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount tool\n", Charsets.UTF_8)
        return r
    }

    /** 扩展函数样本（fun Nat.dbl(): Nat = self * 2——Nat 接收者扩展）。 */
    private fun extSample(tag: String, userBody: String): File {
        val r = root(tag)
        File(r, "主.subl").writeText(userBody, Charsets.UTF_8)
        val tool = File(r, "tool"); tool.mkdirs()
        File(tool, "t.subl").writeText(
            "fun Nat.dbl() : Nat = self * 2\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount tool\n", Charsets.UTF_8)
        return r
    }

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun varType(o: FrontendOutput, name: String): sublimation.frontend.hir.HirType? {
        var out: sublimation.frontend.hir.HirType? = null
        fun walk(n: Any?) {
            when (n) {
                is HirVarStmt -> if (out == null && n.name == name) out = n.type
                is sublimation.frontend.hir.HirBlock -> n.stmts.forEach { walk(it) }
                is sublimation.frontend.hir.HirStmtEntry -> walk(n.stmt)
                is sublimation.frontend.hir.HirDeclEntry -> walk(n.decl)
                is sublimation.frontend.hir.HirFunDecl -> n.body?.let { walk(it) }
                else -> { }
            }
        }
        o.hir.entries.forEach { walk(it) }
        return out
    }

    /** 绿 1：限定别名展开——var x : tool.Box[Nat] = Some(3)（Some/None 经父域名义解析）。 */
    @Test fun 限定别名展开() {
        val r = typeSample("alias-green",
            "fun main() {\n" +
            "    var x : tool.Box[Nat] = Some(3)\n" +
            "    unchecked print(x)\n" +
            "}\n")
        val out = compileModuleFrontend(r)
        assertEquals(emptyList<String>(), errs(out.main), "限定别名赋值零红: " + errs(out.main))
        val t1 = varType(out.main, "x")
        assertTrue(t1 is HirUnionType, "tool.Box[Nat] 展开为联合 Some[Nat] | None")
    }

    /** 绿 2：限定结构体类型 + 模块私有构造器（非限定构造通道 + moduleCtor 层）。 */
    @Test fun 限定结构体与私有构造() {
        val r = typeSample("struct-green",
            "fun main() {\n" +
            "    var p : tool.Pair = Pair(1, 2)\n" +
            "    unchecked print(p)\n" +
            "}\n")
        val out = compileModuleFrontend(r)
        assertEquals(emptyList<String>(), errs(out.main), "模块私有结构体构造零红: " + errs(out.main))
        val t1 = varType(out.main, "p") as? HirNamedType
        assertNotNull(t1, "tool.Pair 解析为具名类型")
        assertEquals("Pair", t1?.name)
        assertNotNull(t1?.symbol, "限定结构体类型落模块符号引用")
    }

    /** 绿 3：跨模块扩展函数（方法糖查询链兜底层命中——dbl 为自由面同名 fallback）。 */
    @Test fun 跨模块扩展方法糖() {
        val r = extSample("ext-green",
            "@impure fun main() {\n" +
            "    var x : Nat = 3\n" +
            "    unchecked print(x.dbl())\n" +
            "}\n")
        val out = compileModuleFrontend(r)
        assertEquals(emptyList<String>(), errs(out.main), "跨模块方法糖零红: " + errs(out.main))
        var call: HirCall? = null
        fun walk(n: Any?) {
            when (n) {
                is HirCall -> { val nr = n.callee as? sublimation.frontend.hir.HirNameRef
                    if (call == null && nr?.name == "dbl") call = n; n.args.forEach { walk(it) } }
                is sublimation.frontend.hir.HirBlock -> n.stmts.forEach { walk(it) }
                is sublimation.frontend.hir.HirExprStmt -> walk(n.expr)
                is sublimation.frontend.hir.HirUnchecked -> walk(n.inner)
                is sublimation.frontend.hir.HirStmtEntry -> walk(n.stmt)
                is sublimation.frontend.hir.HirDeclEntry -> walk(n.decl)
                is sublimation.frontend.hir.HirFunDecl -> n.body?.let { walk(it) }
                else -> { }
            }
        }
        out.main.hir.entries.forEach { walk(it) }
        val c = call
        assertNotNull(c, "方法糖 x.dbl() 命中跨模块扩展")
        assertEquals(true, c?.methodSugar)
        val t = c?.type as? HirNamedType
        assertEquals("Nat", t?.name, "ext 调用类型 Nat")
        assertNotNull(c?.symbol, "落模块扩展符号引用")
    }

    /** 消歧序：本模块同名扩展优先（P-7——查询链本模块 > 挂载子）。 */
    @Test fun 本模块扩展优先遮蔽模块扩展() {
        val r = extSample("ext-shadow",
            "fun Nat.dbl() : Str = \"user\"\n" +
            "@impure fun main() {\n" +
            "    var x : Nat = 3\n" +
            "    var s = x.dbl()\n" +
            "    unchecked print(s)\n" +
            "}\n")
        val out = compileModuleFrontend(r)
        assertEquals(emptyList<String>(), errs(out.main), "用户扩展优先零红: " + errs(out.main))
        var call: HirCall? = null
        fun walk(n: Any?) {
            when (n) {
                is HirCall -> {
                    val nr = n.callee as? sublimation.frontend.hir.HirNameRef
                    if (call == null && nr?.name == "dbl" && n.methodSugar) call = n
                    n.args.forEach { walk(it) }
                }
                is sublimation.frontend.hir.HirBlock -> n.stmts.forEach { walk(it) }
                is sublimation.frontend.hir.HirVarStmt -> walk(n.value)
                is sublimation.frontend.hir.HirExprStmt -> walk(n.expr)
                is sublimation.frontend.hir.HirUnchecked -> walk(n.inner)
                is sublimation.frontend.hir.HirStmtEntry -> walk(n.stmt)
                is sublimation.frontend.hir.HirDeclEntry -> walk(n.decl)
                is sublimation.frontend.hir.HirFunDecl -> n.body?.let { walk(it) }
                else -> { }
            }
        }
        out.main.hir.entries.forEach { walk(it) }
        val t = call?.type as? HirNamedType
        assertEquals("Str", t?.name, "本模块扩展（Str）胜出——模块 Nat.dbl 不遮蔽")
    }

    /** P-7 同名结构体：用户 struct 覆盖模块注入（构造形状以用户为准）。 */
    @Test fun 用户结构体覆盖模块注入() {
        val r = typeSample("struct-p7",
            "struct Pair(c : Nat)\n" +
            "@impure fun main() {\n" +
            "    var p = Pair(1)\n" +
            "    unchecked print(p)\n" +
            "}\n")
        val out = compileModuleFrontend(r)
        assertEquals(emptyList<String>(), errs(out.main), "用户形状胜出零红: " + errs(out.main))
    }

    /** 负 1：限定类型引用未命中 → E-UNBOUND-NAME（负例先红）。 */
    @Test fun 限定类型未命中红() {
        val r = typeSample("alias-miss",
            "fun main() {\n" +
            "    var x : tool.Nope[Nat] = 1\n" +
            "    unchecked print(x)\n" +
            "}\n")
        val out = compileModuleFrontend(r)
        val codes = errs(out.main)
        assertTrue("E-UNBOUND-NAME" in codes, "模块类型面无 Nope 须红: $codes")
        assertFalse(out.ok)
    }

    /** 负 2：方法糖扩展未命中（三类通道皆无）→ E-UNBOUND-NAME 既有口径。 */
    @Test fun 跨模块扩展未命中红() {
        val r = extSample("ext-miss",
            "@impure fun main() {\n" +
            "    var x : Nat = 3\n" +
            "    unchecked print(x.zzz())\n" +
            "}\n")
        val out = compileModuleFrontend(r)
        val codes = errs(out.main)
        assertTrue("E-UNBOUND-NAME" in codes, "扩展未命中须红（模块面也无）: $codes")
        assertFalse(out.ok)
    }

    /** nid 纪律：类型面/扩展面双编译主+子 envelope 逐字节同（零 nid 消费延续）。 */
    @Test fun 双编译emit逐字节同() {
        val r = typeSample("nid",
            "fun main() {\n" +
            "    var x : tool.Box[Nat] = Some(3)\n" +
            "    var p : tool.Pair = Pair(1, 2)\n" +
            "    unchecked print(x)\n" +
            "    unchecked print(p)\n" +
            "}\n")
        val a = compileModuleFrontend(r)
        val b = compileModuleFrontend(r)
        assertEquals(a.main.json, b.main.json, "主 envelope 双编译逐字节同")
        a.children.forEachIndexed { i, u ->
            assertEquals(u.output.json, b.children[i].output.json, "子 envelope $i 双编译逐字节同")
        }
    }
}
