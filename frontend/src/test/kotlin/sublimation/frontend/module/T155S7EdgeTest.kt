
package sublimation.frontend.module

import sublimation.frontend.cli.FrontendOutput
import sublimation.frontend.cli.ModuleUnit
import sublimation.frontend.cli.compileModuleFrontend
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirField
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * t155（S7 限面收边）：三块遗留面的收口测套——
 * 1. **跨模块不纯记账**：模块挂载态下 calleeImpure 真化（t141 真化面的模块态延续）——子模块 A2
 *    纯度闭包终态（ModuleSnapshot.funPurity）携带过边界;纯域调模块 impure ⇒ E-IMPURE-CALL;
 *    模块 impure 调用计 H-IMPURE-REDUNDANT 证据（免标注误报）。
 * 2. **三段调用链**：a.b.c 限定链模块段递归（根别名 → 中段子挂载 → 末段符号）;中段未命中单错落位。
 * 3. **多文件诊断行号**：模块体多文件拼接的累计行号 + 源件归属（Diag.file——报错指回源件）。
 *
 * 纪律：负例先红（三面实现前实测红/缺）;零分配 nid（快照纯度表按名回收，无新类型求值）;
 * 单文件/单件模块零侵入（file 留 null、形态逐字不变——68 门 SAME 面的承诺）。 */
class T155S7EdgeTest {

    private fun root(tag: String): File {
        val d = File("frontend/build/tmp/t155/" + tag)
        if (d.exists()) d.deleteRecursively()
        d.mkdirs()
        return d
    }

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    /** 深搜首个 HirCall（callee=HirField(name)——模块限定调用载体）。 */
    private fun findModuleCall(o: FrontendOutput, name: String): HirCall? {
        var out: HirCall? = null
        fun walk(n: Any?) {
            when (n) {
                is HirCall -> {
                    val f = n.callee as? HirField
                    if (out == null && f != null && f.name == name) out = n
                    walk(n.callee); n.args.forEach { walk(it) }
                }
                is sublimation.frontend.hir.HirBlock -> n.stmts.forEach { walk(it) }
                is sublimation.frontend.hir.HirExprStmt -> walk(n.expr)
                is sublimation.frontend.hir.HirUnchecked -> walk(n.inner)
                is sublimation.frontend.hir.HirStmtEntry -> walk(n.stmt)
                is sublimation.frontend.hir.HirDeclEntry -> walk(n.decl)
                is sublimation.frontend.hir.HirFunDecl -> n.body?.let { walk(it) }
                is sublimation.frontend.hir.HirVarStmt -> walk(n.value)
                is sublimation.frontend.hir.HirReturn -> n.expr?.let { walk(it) }
                else -> { }
            }
        }
        o.hir.entries.forEach { walk(it) }
        return out
    }

    private fun childOf(units: List<ModuleUnit>, path: String): FrontendOutput? =
        units.firstOrNull { it.unitPath == path }?.output

    // -------------------- 1. 跨模块不纯记账 --------------------

    /** 纯域调模块**推断**不纯函数（子模块 A2 闭包终态经快照携带过边界）⇒ 主单元 E-IMPURE-CALL + calleeImpure 真化。 */
    @Test fun 推断不纯跨边界真化纯域拦() {
        val r = root("face1-inferred")
        File(r, "main.subl").writeText(
            "fun main() : Nat {\n  imp.count()\n  return 1\n}\n", Charsets.UTF_8)
        val imp = File(r, "imp"); imp.mkdirs()
        File(imp, "imp.subl").writeText(
            "fun count() : Nat {\n  print(9)\n  return 1\n}\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount imp\n", Charsets.UTF_8)
        val out = compileModuleFrontend(r)
        val mainErrs = errs(out.main)
        assertTrue(mainErrs.contains("E-IMPURE-CALL"),
            "纯域调模块推断不纯函数须 E-IMPURE-CALL（模块态纯度门接通）: " + mainErrs)
        val call = findModuleCall(out.main, "count")
        assertNotNull(call, "限定调用载体存在")
        assertTrue(call.calleeImpure, "calleeImpure 须真化= true（子模块 A2 终态跨边界携带）")
    }

    /** 标注 @impure 的调用方：模块 impure 调用计证据——H-IMPURE-REDUNDANT 不误报 + 调用位零 E-IMPURE-CALL。 */
    @Test fun 模块不纯调用计证据免冗余误报() {
        val r = root("face1-evidence")
        File(r, "main.subl").writeText(
            "@impure fun main() : Null {\n  imp.hello()\n}\n", Charsets.UTF_8)
        val imp = File(r, "imp"); imp.mkdirs()
        File(imp, "imp.subl").writeText(
            "@impure fun hello() : Null { print(1) }\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount imp\n", Charsets.UTF_8)
        val out = compileModuleFrontend(r)
        val mainErrs = errs(out.main)
        assertFalse(mainErrs.contains("E-IMPURE-CALL"),
            "impure 调用方域内模块 impure 调用免闸: " + mainErrs)
        assertFalse(out.main.diag.diags.any { it.code == "H-IMPURE-REDUNDANT" },
            "模块 impure 调用计证据——H-IMPURE-REDUNDANT 不误报: " + out.main.diag.diags.map { it.code })
        assertTrue(findModuleCall(out.main, "hello")?.calleeImpure == true,
            "注解位 calleeImpure 真化维持（既有 t131 面零回归）")
    }

    /** 纯函数调模块**纯**函数绿（gate 接通后的零回归门——纯模块调用不得误拦）。 */
    @Test fun 纯调模块纯函数绿() {
        val r = root("face1-pure-green")
        File(r, "main.subl").writeText(
            "fun main() : Nat {\n  return tool.triple(4)\n}\n", Charsets.UTF_8)
        val tool = File(r, "tool"); tool.mkdirs()
        File(tool, "t.subl").writeText("fun triple(n : Nat) : Nat = n * 3\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount tool\n", Charsets.UTF_8)
        val out = compileModuleFrontend(r)
        assertTrue(out.ok, "纯函数调模块纯函数零红（模块态闸不误伤）: " + errs(out.main))
        assertFalse(findModuleCall(out.main, "triple")?.calleeImpure == true, "纯函数 calleeImpure=false")
    }

    // -------------------- 2. 三段调用链 --------------------

    /** a.b.c 三段链解析绿（根别名 → 中段子挂载 → 末段符号）+ HIR 限定链逐段构造。 */
    @Test fun 三段链解析绿() {
        val r = root("face2-chain")
        File(r, "main.subl").writeText(
            "fun main() : Nat {\n  return outer.inner.f(1)\n}\n", Charsets.UTF_8)
        val outer = File(r, "outer"); outer.mkdirs()
        File(outer, ".settings").writeText("mount inner\n", Charsets.UTF_8)
        File(outer, "pad.subl").writeText("fun pad() : Nat = 0\n", Charsets.UTF_8)
        val inner = File(outer, "inner"); inner.mkdirs()
        File(inner, "b.subl").writeText("fun f(n : Nat) : Nat = n + 1\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount outer\n", Charsets.UTF_8)
        val out = compileModuleFrontend(r)
        assertTrue(out.ok, "三段链 outer.inner.f(1) 须绿: " + errs(out.main) +
            childOf(out.children, "outer")?.let { " / outer: " + errs(it) })
        val call = findModuleCall(out.main, "f")
        assertNotNull(call, "末段符号载体存在")
        val mid = (call.callee as? HirField)?.target as? HirField
        assertNotNull(mid, "中段模块段经 HirField 链承载（target=HirField(inner)）")
        assertEquals("inner", mid.name, "中段链段名")
    }

    /** 中段未命中（a.b.c 的 b 非子挂载模块）⇒ 单错落位于中段（无方法糖双报）。 */
    @Test fun 三段链中段未命中单错() {
        val r = root("face2-midmiss")
        File(r, "main.subl").writeText(
            "fun main() : Nat {\n  return outer.nope.f(1)\n}\n", Charsets.UTF_8)
        val outer = File(r, "outer"); outer.mkdirs()
        File(outer, "pad.subl").writeText("fun pad() : Nat = 0\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount outer\n", Charsets.UTF_8)
        val out = compileModuleFrontend(r)
        val unbounds = out.main.diag.diags.filter { it.code == "E-UNBOUND-NAME" }
        assertEquals(1, unbounds.size, "中段未命中须恰好一错（免双报）: " + unbounds.map { it.pos })
        val d = unbounds.first()
        assertTrue(d.message.contains("无子模块") && d.message.contains("nope"),
            "错误文案指名中段子模块缺失: " + d.message)
        assertEquals(2, d.pos.start.line, "错位=中段所在行（2:16——nope 段位）")
        assertEquals(16, d.pos.start.col, "错位列=中段段首")
    }

    // -------------------- 3. 多文件诊断行号 --------------------

    /** 多文件模块：累计行号（拼接口径）+ 源件归属（file=b.subl——报错指回源件）。 */
    @Test fun 多文件行号归属() {
        val r = root("face3-multifile")
        File(r, "a.subl").writeText(
            "fun one() : Nat = 1\nfun two() : Nat = 2\nfun three() : Nat = 3\n" +
                "fun four() : Nat = 4\nfun five() : Nat = 5\n", Charsets.UTF_8)
        File(r, "b.subl").writeText(
            "fun six() : Nat = 6\nfun bad() : Nat = nope\n", Charsets.UTF_8)
        val out = compileModuleFrontend(r)
        val u = out.main.diag.diags.firstOrNull { it.code == "E-UNBOUND-NAME" }
        assertNotNull(u, "必现一错")
        assertEquals("b.subl", u.file, "源件归属=b.subl（报错指回源件）")
        assertEquals(8, u.pos.start.line, "累计行号=a 的 5 行 + 分隔 1 行 + b 内 2 行 = 8（行号连续累加口径）")
        assertEquals(19, u.pos.start.col, "列位=源件内列位")
    }

    /** 单件模块：file 留 null（单文件编译零侵入——68 门 SAME 面承诺）。 */
    @Test fun 单件模块无文件归属() {
        val r = root("face3-singlefile")
        File(r, "a.subl").writeText("fun bad() : Nat = nope\n", Charsets.UTF_8)
        val out = compileModuleFrontend(r)
        val u = out.main.diag.diags.firstOrNull { it.code == "E-UNBOUND-NAME" }
        assertNotNull(u, "必现一错")
        assertNull(u.file, "单件模块 file=null（归属标注仅多文件拼接面）")
    }
}
