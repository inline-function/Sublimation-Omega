package sublimation.frontend.cli

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 前端编译入口与最小 CLI 测试（S-2 §2；S-3 §3 验收证据）。
 * 覆盖线：一次编译 API（compileFrontend 可复现）+ 四子命令（tokens/parse/check/compile）
 * + 诊断带源位置 + JSON 产物写盘（含带诊断半成品产物）+ 用法/退出码口径。
 */
class CliTest {

    private val projectSrc = "fun f(x: Nat): Nat = x"
    private val scriptSrc = """
        var y = 10
        fun g(): Nat {
            var y = 20
            return y
        }
    """.trimIndent()
    private val errorSrc = "fun f(): Nat = undefined"

    /** 运行 CLI（输出流注入）：返回（退出码, stdout, stderr）。 */
    private fun run(vararg args: String): Triple<Int, String, String> {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val out = PrintStream(outBytes, true, Charsets.UTF_8)
        val err = PrintStream(errBytes, true, Charsets.UTF_8)
        val code = runCli(arrayOf(*args), out, err)
        return Triple(code, outBytes.toString(Charsets.UTF_8), errBytes.toString(Charsets.UTF_8))
    }

    private fun source(content: String, name: String): File {
        val dir = File("frontend/build/tmp/cli")
        dir.mkdirs()
        val f = File(dir, name)
        f.writeText(content, Charsets.UTF_8)
        return f
    }

    // ============ 一次编译 API（S-2 §2 对外契约） ============

    @Test
    fun compileFrontendEmitsEnvelopeAndIsDeterministic() {
        val a = compileFrontend(projectSrc, "sample.subl")
        val b = compileFrontend(projectSrc, "sample.subl")
        assertFalse(a.diag.hasError)
        assertTrue(a.json.contains("\"schema\": \"sublimation/hir@v3\"" ), "envelope schema 对账 ADR-1 §3.7")
        assertTrue(a.json.contains("\"ok\": true"))
        assertTrue(a.json.contains("\"kind\": \"HirFunDecl\""), "HIR 节点形态对账")
        assertEquals(a.json, b.json, "相同输入必产相同 JSON（层间总契约 3）")
    }

    @Test
    fun compileFrontendHalfBakedOnError() {
        val r = compileFrontend(errorSrc, "bad.subl")
        assertTrue(r.diag.hasError)
        assertFalse(r.hir.entries.isEmpty(), "半成品 HIR 树照常返回（ADR-1 §3.2）")
        assertTrue(r.json.contains("\"ok\": false"), "ok 旗标携带判定结论")
        assertTrue(r.json.contains("E-UNBOUND-NAME"), "诊断并入 envelope")
    }

    // ============ 子命令：tokens ============

    @Test
    fun tokensPrintsStreamWithAdjacency() {
        val f = source(projectSrc, "tokens.subl")
        val (code, out, err) = run("tokens", f.path)
        assertEquals(0, code, err)
        assertTrue(out.contains("1:1"), "带位置（S-1 §1）")
        assertTrue(out.contains("EOF"), "流以 EOF 结尾")
        assertTrue(out.contains("FUN"), "硬关键字折算（L-9）")
        assertTrue(out.contains("\tadj"), "邻接标记（L-7）：f 后紧接 ( ")
        assertTrue(out.contains("\tsp"), "邻接标记：空格后")
    }

    // ============ 子命令：parse ============

    @Test
    fun parsePrintsTree() {
        val f = source(projectSrc, "parse.subl")
        val (code, out, err) = run("parse", f.path)
        assertEquals(0, code, err)
        assertTrue(out.contains("FileAst(1 条目)"), "条目计数")
        assertTrue(out.contains("FunDecl f "), "声明行（主标量）")
        assertTrue(out.contains("params[0]: Param x"), "形参孩子（带前缀）")
        assertTrue(out.contains("retType: NamedType Nat"), "类型孩子")
        assertTrue(out.contains("body: NameRef x"), "函数体孩子")
        assertTrue(out.contains("1:1"), "节点位置")
    }

    @Test
    fun parseScriptModeFlag() {
        val f = source(scriptSrc, "script.subl")
        val (codeProj, outProj, errProj) = run("parse", f.path)
        assertEquals(1, codeProj, errProj)
        assertTrue(outProj.contains("E-"), "工程模式拒绝顶层语句")
        val (codeScr, outScr, errScr) = run("parse", f.path, "--script")
        assertEquals(0, codeScr, errScr)
        assertTrue(outScr.contains("VarStmt y"), "脚本模式解析顶层语句")
        assertTrue(outScr.contains("FunDecl g"))
    }

    // ============ 子命令：check ============

    @Test
    fun checkPass() {
        val f = source(projectSrc, "check.subl")
        val (code, out, err) = run("check", f.path)
        assertEquals(0, code, err)
        assertTrue(out.contains("[完成] 前端检查通过"))
    }

    @Test
    fun checkReportsErrorWithSourceLocation() {
        val f = source(errorSrc, "bad.subl")
        val (code, out, err) = run("check", f.path)
        assertEquals(1, code, err)
        assertTrue(out.contains("E-UNBOUND-NAME"), "诊断码")
        assertTrue(out.contains("[1:16]"), "带源位置（D-10 渲染口径）")
        assertTrue(out.contains(" | fun f(): Nat = undefined"), "附源码行")
        assertTrue(out.contains("^"), "位置箭头")
    }

    @Test
    fun checkScriptModePass() {
        val f = source(scriptSrc, "script-check.subl")
        val (code, out, err) = run("check", f.path, "--script")
        assertEquals(0, code, err)
        assertTrue(out.contains("[完成] 前端检查通过"))
    }

    // ============ 子命令：compile ============

    @Test
    fun compileWritesArtifactByDefault() {
        val f = source(projectSrc, "compile.subl")
        val (code, out, err) = run("compile", f.path)
        assertEquals(0, code, err)
        val target = File(f.path + ".hir.json")
        assertTrue(target.exists(), "默认产物 <file>.hir.json 写盘")
        val json = target.readText(Charsets.UTF_8)
        assertTrue(json.contains("\"schema\": \"sublimation/hir@v3\""))
        assertTrue(json.contains("\"ok\": true"))
        assertTrue(json.contains("\"path\": \"compile.subl\""), "unit.path 为文件名")
        assertTrue(out.contains("[完成] 已生成"))
    }

    @Test
    fun compileDashOOverridesOutput() {
        val f = source(projectSrc, "compile-o.subl")
        val target = File("frontend/build/tmp/cli/custom.json")
        val (code, out, err) = run("compile", f.path, "-o", target.path)
        assertEquals(0, code, err)
        assertTrue(target.exists(), "-o 指定路径写盘")
        assertTrue(out.contains(target.path))
    }

    @Test
    fun compileWritesHalfBakedArtifactOnError() {
        val f = source(errorSrc, "bad-compile.subl")
        val (code, out, err) = run("compile", f.path)
        assertEquals(1, code, "退出码反映 ERROR（构建脚本可见）")
        val target = File(f.path + ".hir.json")
        assertTrue(target.exists(), "D-9 半成品原则：带诊断产物也写盘")
        val json = target.readText(Charsets.UTF_8)
        assertTrue(json.contains("\"ok\": false"))
        assertTrue(json.contains("E-UNBOUND-NAME"))
        assertTrue(out.contains("E-UNBOUND-NAME"), "诊断分级输出")
        assertTrue(err.contains("已生成带诊断产物"))
    }

    // ============ 用法与退出码 ============

    @Test
    fun usageOnEmptyArgs() {
        val (code, out, err) = run()
        assertEquals(2, code)
        assertTrue(err.contains("用法"))
    }

    @Test
    fun usageOnUnknownCommand() {
        val (code, out, err) = run("bogus", "x.subl")
        assertEquals(2, code)
        assertTrue(err.contains("用法"))
    }

    @Test
    fun errorsOnUnknownOptionAndMissingFile() {
        val (codeOpt, _, errOpt) = run("check", "frontend/build/tmp/cli/none.subl", "--bogus")
        assertEquals(2, codeOpt)
        assertTrue(errOpt.contains("未知选项"))
        val (codeFile, _, errFile) = run("check", "frontend/build/tmp/cli/does-not-exist.subl")
        assertEquals(2, codeFile)
        assertTrue(errFile.contains("文件不存在"))
    }

    // ============ R3-S0（t100）：.subs 扩展名分发 ============

    /** .subs 扩展名等价 --script：顶层语句直接通过。 */
    @Test
    fun subsExtensionEqualsScriptFlag() {
        val f = source(scriptSrc, "auto.subs")
        val (codeParse, outParse, errParse) = run("parse", f.path)
        assertEquals(0, codeParse, errParse)
        assertTrue(outParse.contains("VarStmt y"), ".subs 顶层语句按脚本模式解析")
        assertTrue(outParse.contains("FunDecl g"))
        val (codeCheck, outCheck, errCheck) = run("check", f.path)
        assertEquals(0, codeCheck, errCheck)
        assertTrue(outCheck.contains("[完成] 前端检查通过"))
        val target = File("frontend/build/tmp/cli/subs.json")
        val (codeCmp, outCmp, errCmp) = run("compile", f.path, "-o", target.path)
        assertEquals(0, codeCmp, errCmp)
        assertTrue(target.exists(), ".subs compile 写盘")
        val json = target.readText(Charsets.UTF_8)
        assertTrue(json.contains("\"schema\": \"sublimation/hir@v3\""))
        assertTrue(json.contains("\"ok\": true"))
    }

    /** .subl 工程模式拒绝脚本形态：消息与既有路径逐字一致（E-VAL-REMOVED 定向文案）。 */
    @Test
    fun sublEngineeringModeRejectsScriptFormsUnchanged() {
        val f = source(scriptSrc, "eng.subl")
        val (code, out, err) = run("parse", f.path)
        assertEquals(1, code, err)
        val msg = out.split('\n').first { it.contains("E-VAL-REMOVED") || it.contains("E-SYNTAX") }
        assertTrue(msg.contains("顶层只能写声明"), "拒绝消息与既有解析层口径逐字一致：$msg")
        // .subs 与 --script 打开同一通道后，同一源不再拒绝
        val fSubs = source(scriptSrc, "eng.subs")
        val (code2, _, err2) = run("parse", fSubs.path)
        assertEquals(0, code2, err2)
    }

    /** 双开：--script 显式 + .subl 扩展名仍合法（解析层只看 scriptMode 一个布尔）。 */
    @Test
    fun scriptFlagWithSublStillLegal() {
        val f = source(scriptSrc, "dual.subl")
        val (code, out, err) = run("parse", f.path, "--script")
        assertEquals(0, code, err)
        assertTrue(out.contains("VarStmt y"), "--script 配 .subl 仍开脚本模式")
        // .subs 配 --script（双信号同向）亦然
        val f2 = source(scriptSrc, "dual.subs")
        val (code2, out2, err2) = run("parse", f2.path, "--script")
        assertEquals(0, code2, err2)
        assertTrue(out2.contains("VarStmt y"))
    }
}
