package sublimation.frontend.module

import sublimation.frontend.cli.FrontendOutput
import sublimation.frontend.cli.ModuleUnit
import sublimation.frontend.cli.childJsonName
import sublimation.frontend.cli.compileModuleFrontend
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirExpr
import sublimation.frontend.hir.HirField
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * t131（S7 / R-22）：模块系统端到端——目录即模块编译 + 限定引用语义层消歧。
 * 覆盖提案 §2.3 消歧序、§2.4 HIR 载体、§3 可见性、§4 符号表作用域化、nid 纪律。 */
class T131ModuleCompileTest {

    private fun root(tag: String): File {
        val d = File("frontend/build/tmp/t131/" + tag)
        if (d.exists()) d.deleteRecursively()
        d.mkdirs()
        return d
    }

    /** 冻红样本同构件：根模块 fun main 跨模块限定调用 tool.triple(4) / tool.square(3)。 */
    private fun greenSample(tag: String): File {
        val r = root(tag)
        File(r, "主.subl").writeText(
            "fun main() {\n" +
            "    unchecked print(tool.triple(4))   // 12\n" +
            "    unchecked print(tool.square(3))   // 9\n" +
            "}\n", Charsets.UTF_8)
        val tool = File(r, "tool"); tool.mkdirs()
        File(tool, "工具函数.subl").writeText(
            "fun triple(n : Nat) : Nat = n * 3\nfun square(n : Nat) : Nat = n * n\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount tool\n", Charsets.UTF_8)
        return r
    }

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    /** 深搜取首个 HirField(name) 节点（模块限定载体=提案 §2.4 B-i：复用 HirField + D-MODULE-REF）。 */
    private fun findField(o: FrontendOutput, name: String): HirField? {
        var out: HirField? = null
        fun walk(n: Any?) {
            when (n) {
                is HirField -> if (out == null && n.name == name) out = n
                is HirCall -> { walk(n.callee); n.args.forEach { walk(it) } }
                is sublimation.frontend.hir.HirBlock -> n.stmts.forEach { walk(it) }
                is sublimation.frontend.hir.HirExprStmt -> walk(n.expr)
                is sublimation.frontend.hir.HirUnchecked -> walk(n.inner)
                is sublimation.frontend.hir.HirStmtEntry -> walk(n.stmt)
                is sublimation.frontend.hir.HirDeclEntry -> walk(n.decl)
                is sublimation.frontend.hir.HirFunDecl -> n.body?.let { walk(it) }
                is sublimation.frontend.hir.HirVarStmt -> walk(n.value)
                else -> { }
            }
        }
        o.hir.entries.forEach { walk(it) }
        return out
    }

    /** 取 module 限定调用的 D-MODULE-REF 旁注。 */
    private fun findCallNote(o: FrontendOutput, calleeName: String): sublimation.frontend.hir.HirNote? {
        var out: sublimation.frontend.hir.HirNote? = null
        fun walk(n: Any?) {
            when (n) {
                is HirCall -> {
                    val f = n.callee as? HirField
                    if (out == null && f != null && f.name == calleeName) out = n.notes.firstOrNull { it.code == "D-MODULE-REF" }
                    n.args.forEach { walk(it) }
                }
                is sublimation.frontend.hir.HirBlock -> n.stmts.forEach { walk(it) }
                is sublimation.frontend.hir.HirExprStmt -> walk(n.expr)
                is sublimation.frontend.hir.HirUnchecked -> walk(n.inner)
                is sublimation.frontend.hir.HirStmtEntry -> walk(n.stmt)
                is sublimation.frontend.hir.HirDeclEntry -> walk(n.decl)
                is sublimation.frontend.hir.HirFunDecl -> n.body?.let { walk(it) }
                else -> { }
            }
        }
        o.hir.entries.forEach { walk(it) }
        return out
    }

    /** 绿 1：冻红同构样本转绿——主 ok（零红）+ 子模块单元 ok + 限定调用载体 + 旁注。 */
    @Test fun 冻红样本转绿限定调用() {
        val r = greenSample("compile-green")
        val out = compileModuleFrontend(r)
        assertTrue(out.ok, "主+子模块皆绿")
        // t137（R-23）：stdlib 默认挂载并入编译根——子产物 = 用户挂载 tool + stdlib 件（B0-1 后 6 件；
        // t140（B2.2）加 traits 件 = 7 件；钻石形备忘按规范路径记一次：
        // optional/set 记在 list 命名空间下（unit.path 口径）
        assertEquals(8, out.children.size, "用户挂载 tool + stdlib（list/result/math/traits + list/* 嵌套 3）")
        val f = findField(out.main, "triple")
        assertNotNull(f, "限定调用载体 = HirField（提案 B-i 零 schema）")
        assertNotNull(f.field, "field 位挂模块函数符号")
        assertEquals("triple", f.field?.name)
        assertNull((f.target as? sublimation.frontend.hir.HirNameRef)?.symbol,
            "限定段载体为模块名引用（无值符号——模块面）")
        val note = findCallNote(out.main, "triple")
        assertNotNull(note, "限定调用落 D-MODULE-REF 旁注")
        assertEquals("D-MODULE-REF", note?.code)
        assertEquals(emptyList<String>(), errs(out.main), "主模块零红（冻红样本转绿）")
    }

    /** 绿 2：限定调用返回类型核对——triple(4) : Nat。 */
    @Test fun 限定调用签名核对Nat() {
        val r = greenSample("compile-sig")
        val out = compileModuleFrontend(r)
        var call: HirCall? = null
        fun walk(n: Any?) {
            when (n) {
                is HirCall -> {
                    val f = n.callee as? HirField
                    if (call == null && f != null && f.name == "triple") call = n
                    n.args.forEach { walk(it) }
                }
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
        val t = call?.type as? sublimation.frontend.hir.HirNamedType
        assertEquals("Nat", t?.name, "tool.triple(4) 的调用类型为 Nat（父域签名核对）")
    }

    /** 负 1：限定引用未命中 → E-UNBOUND-NAME（模块面无该函数——消歧序 1 命中模块后告红）。 */
    @Test fun 限定未命中红() {
        val r = greenSample("compile-miss")
        File(r, "主.subl").writeText("fun main() {\n    unchecked print(tool.unknown(1))\n}\n", Charsets.UTF_8)
        val out = compileModuleFrontend(r)
        val codes = errs(out.main)
        assertTrue("E-UNBOUND-NAME" in codes, "未命中模块函数须落 E-UNBOUND-NAME: $codes")
        assertFalse(out.ok)
    }

    /** 负 2 + 提示：局部/本模块同名符号遮蔽模块别名 → H-MODULE-SHADOW 提示不拦，取本模块符号（R-22 查询链）。 */
    @Test fun 同名遮蔽提示不拦本模块优先() {
        val r = greenSample("compile-shadow")
        File(r, "主.subl").writeText(
            "fun main() {\n" +
            "    var tool : Nat = 7\n" +
            "    unchecked print(tool.triple(4))\n" +
            "}\n", Charsets.UTF_8)
        val out = compileModuleFrontend(r)
        val hints = out.main.diag.diags.filter { it.severity == Severity.HINT }.map { it.code }
        assertTrue("H-MODULE-SHADOW" in hints, "局部同名遮蔽模块须落 H-MODULE-SHADOW 提示: $hints")
        val codes = errs(out.main)
        assertTrue("E-UNBOUND-NAME" in codes, "遮蔽后按本模块符号解析——Nat 无 triple 方法而红")
        assertNull(findField(out.main, "triple"), "未走模块通道（无 HirField 限定载体）")
    }

    /** 值位：tool.triple 作为函数值 → HirField(field=模块符号, type=函数类型)。 */
    @Test fun 值位限定引用() {
        val r = greenSample("compile-value")
        File(r, "主.subl").writeText(
            "fun main() {\n" +
            "    var f = tool.triple\n" +
            "    unchecked print(f(4))\n" +
            "}\n", Charsets.UTF_8)
        val out = compileModuleFrontend(r)
        assertEquals(emptyList<String>(), errs(out.main), "值位限定引用零红")
        val f = findField(out.main, "triple")
        assertNotNull(f, "值位载体 = HirField")
        assertNotNull(f?.field)
        assertTrue(f?.type is sublimation.frontend.hir.HirFunType, "值位类型 = 函数类型")
    }

    /** §4 符号表作用域化：两模块同名 helper 合法（模块作用域化 E-DUP-DECL）；同模块重名仍红。 */
    @Test fun E_DUP_DECL模块作用域化() {
        val r = root("compile-dupdecl")
        File(r, "主.subl").writeText(
            "fun helper(n : Nat) : Nat = n + 1\n" +
            "fun main() {\n" +
            "    unchecked print(helper(1))\n" +
            "    unchecked print(tool.helper(2))\n" +
            "}\n", Charsets.UTF_8)
        val tool = File(r, "tool"); tool.mkdirs()
        File(tool, "m.subl").writeText("fun helper(n : Nat) : Nat = n + 10\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount tool\n", Charsets.UTF_8)
        val out = compileModuleFrontend(r)
        assertTrue(out.ok, "两模块同名 helper 合法（跨模块同名不入 E-DUP-DECL）")
        // 同模块内重名仍红：tool 模块双 helper
        val r2 = root("compile-dupdecl2")
        File(r2, "主.subl").writeText("fun main() { }\n", Charsets.UTF_8)
        val tool2 = File(r2, "tool"); tool2.mkdirs()
        File(tool2, "m.subl").writeText(
            "fun helper(n : Nat) : Nat = n\nfun helper(n : Nat) : Nat = n\n", Charsets.UTF_8)
        File(r2, ".settings").writeText("mount tool\n", Charsets.UTF_8)
        val out2 = compileModuleFrontend(r2)
        val codes2 = errs(out2.children.first().output)
        assertTrue("E-DUP-DECL" in codes2, "同模块（单元内）重名仍落 E-DUP-DECL: $codes2")
        assertFalse(out2.ok)
    }

    /** nid 纪律：双编译逐字节同（主 + 子 envelope——模块挂载零新 nid 面，同 NidGuard 口径）。 */
    @Test fun 双编译emit逐字节同() {
        val r = greenSample("compile-nid")
        val a = compileModuleFrontend(r)
        val b = compileModuleFrontend(r)
        assertEquals(a.main.json, b.main.json, "主 envelope 双编译逐字节同")
        assertEquals(a.children.size, b.children.size)
        a.children.forEachIndexed { i, u ->
            assertEquals(u.output.json, b.children[i].output.json, "子 envelope $i 双编译逐字节同")
        }
    }

    /** 挂载期早死：E-MOUNT-CYCLE → 挂载作废，主模块零挂载单编译并并袋（无子产物）。 */
    @Test fun 挂载期早死主模块零挂载() {
        val r = root("compile-earlydeath")
        File(r, "主.subl").writeText("fun main() { }\n", Charsets.UTF_8)
        val a = File(r, "a"); a.mkdirs()
        File(a, "f.subl").writeText("fun fa() { }\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount a\n", Charsets.UTF_8)
        File(a, ".settings").writeText("mount ..\n", Charsets.UTF_8)
        val out = compileModuleFrontend(r)
        val codes = errs(out.main)
        assertTrue("E-MOUNT-CYCLE" in codes, "挂载诊断并入主 envelope（早死兜底）: $codes")
        assertEquals(0, out.children.size, "挂载作废 ⇒ 零子产物")
    }

    /** 子产物命名：`<基名>__<模块相对路径>.json`（e2e 双下划线同构）。 */
    @Test fun 子产物json命名() {
        assertEquals("base__tool.json", childJsonName("base.json", "tool"))
        assertEquals("base__tool__math.json", childJsonName("base.json", "tool/math"))
    }
}
