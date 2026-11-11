
package sublimation.frontend.semantic

import sublimation.frontend.cli.compileFrontend
import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirStructDecl
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * t115（R3 S2 落地 B，P-11 §3）验收：元变量推导链（InferMeta + 约束袋 + 单轮求解 + 四载体回填）。
 *
 * 范围（任务书 t115；先红后绿纪律——负例为本类首条，R-13 工程硬拒先于一切正例落档）：
 * 1. **R-13 工程硬拒（先红）**：形参/字段省略标注=脚本专属——工程模式 E-OMITTED-TYPE 真红并阻塞
 *    （队长 OQ-1 裁决：允许返回类型从函数体推导；禁止形参从使用点反推）；
 * 2. **单轮求解**：约束袋（调用实参→下界 / 体内再传递→上界） + 声明位收口（先形参后返回）+
 *    默认填充（P-11 §3.6：无证据形参/字段 → Nothing）；
 * 3. **四载体回填**：HirParam/HirFunDecl（t108 槽位 inferred + evidence 源序可追溯）；
 * 4. **单轮边界（诚实性）**：先定义后使用的后到约束落 D-INFER-PENDING 登记不消费（t116 R 轮闭合体）；
 * 5. **真门**：元变量不落 HIR（D-14）——产物不含占位；零载体默认零发射（SAME 门）。
 *
 * 破例说明：compile 默认工程模式（D-12 模式真值单源）——脚本用例显式传 scriptMode = true。
 */
class T115InferenceTest {

    // ============ 测试前态（先红基线，CLI 探针 p1-p4 实测落档） ============
    // BEFORE（t113 后态）：fun f(x) = x 在工程/脚本均落 E-TYPE-MISMATCH
    // 「声明返回 Null，但函数体是 <错误类型:形参类型标注省略>」——省略位=HirErrorType、无约束收集。
    // AFTER：工程模式 R-13 硬拒（E-OMITTED-TYPE 真红）；脚本模式走元变量推导链。

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "t115.subs")
        return merge(pr.file, pr.notes, pr.diag, scriptMode = true)
    }

    private fun project(src: String): Pair<HirFile, DiagBag> {
        val pr = parseSourceWithNotes(src, "t115.subl")
        return merge(pr.file, pr.notes, pr.diag, scriptMode = false)
    }

    private fun merge(
        file: sublimation.frontend.ast.FileAst,
        notes: Map<sublimation.frontend.ast.AstNode, List<sublimation.frontend.parser.ParseNote>>,
        parseDiag: List<Diag>,
        scriptMode: Boolean,
    ): Pair<HirFile, DiagBag> {
        val (hir, bag) = compile(file, notes, scriptMode = scriptMode)
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

    private fun errCodes(bag: DiagBag) = bag.diags
        .filter { it.severity == Severity.ERROR }.map { it.code }

    private fun allCodes(bag: DiagBag) = bag.diags.map { it.code }

    private fun funDecl(hir: HirFile, name: String) =
        decls(hir).filterIsInstance<HirFunDecl>().first { it.name == name }

    private fun structDecl(hir: HirFile, name: String) =
        decls(hir).filterIsInstance<HirStructDecl>().first { it.name == name }

    private fun typeName(t: sublimation.frontend.hir.HirType?): String =
        (t as? HirNamedType)?.name ?: "<null>"

    /** 证据形态契约（P-11 §3.1 可追溯）：use:行:列。 */
    private fun assertEvidence(evidence: List<String>, expectCount: Int, msg: String) {
        assertEquals(expectCount, evidence.size, msg + "（数量）: $evidence")
        evidence.forEach { e ->
            // 形态 use:行:列（数字段数位不限）——证据可追溯的最小机器契约
            assertTrue(e.matches(Regex("use:[0-9]+:[0-9]+")), "$msg（形态 use:行:列）: $e")
        }
    }

    // ============================ 1. R-13 工程硬拒（负例先红） ============================

    /** t115 负例 1（先红）：工程模式省略形参 → E-OMITTED-TYPE 真红 + 阻塞（ok 由 ERROR 判定）。 */
    @Test
    fun r13工程模式省略形参硬拒() {
        // 先红态（t115 前）：此处无 E-OMITTED-TYPE——形参省略沿 t113 半成品路径静默穿过；
        // 本测先写后码：t115 落地后必须出现该码且阻塞编译。
        val (hir, bag) = project("fun f(x) = x")
        assertTrue(bag.diags.any {
            it.code == "E-OMITTED-TYPE" && it.severity == Severity.ERROR &&
                it.message.contains("形参") && it.message.contains("x")
        }, "E-OMITTED-TYPE 真红于形参位: " + allCodes(bag))
        assertTrue(bag.hasError, "R-13 硬拒=阻塞（hasError）")
        assertNotNull(decls(hir).filterIsInstance<HirFunDecl>().firstOrNull(), "半成品树仍返回")
    }

    /** t115 负例 2（先红）：工程模式省略字段 → 同门（字段与形参同判据）。 */
    @Test
    fun r13工程模式省略字段硬拒() {
        val (hir, bag) = project("struct P(x)")
        assertTrue(bag.diags.any {
            it.code == "E-OMITTED-TYPE" && it.severity == Severity.ERROR &&
                it.message.contains("字段") && it.message.contains("x")
        }, "E-OMITTED-TYPE 真红于字段位: " + allCodes(bag))
        assertTrue(bag.hasError)
        assertNotNull(decls(hir).filterIsInstance<HirStructDecl>().firstOrNull(), "半成品树仍返回")
    }

    // ============================ 2. 单轮求解 + 证据可追溯 ============================

    /** 正例（队长 OQ-1 裁决 2 落地）：工程模式允许返回类型从函数体推导——载体槽位齐备。 */
    @Test
    fun 工程模式返回类型从函数体推导() {
        val (hir, bag) = project("fun f(x: Nat) = x")
        assertEquals(emptyList<String>(), errCodes(bag), "工程模式返回推导不应报错: " + allCodes(bag))
        val f = funDecl(hir, "f")
        assertFalse(f.params[0].inferred, "形参声明位未经元变量——t108 默认零发射")
        assertEquals("x", f.params[0].name)
        assertEquals("Nat", typeName(f.params[0].type))
        assertEquals("Nat", typeName(f.retType), "返回类型由函数体推导落位")
        assertTrue(f.inferred, "返回位 t108 inferred 标记")
        assertEvidence(f.evidence, 1, "返回位证据=函数体位")
    }

    /** 正例：脚本模式形参推导——使用位实参约束（约束袋 → 下界 → 求解落位），证据指向调用处。 */
    @Test
    fun 脚本模式形参从使用位推导() {
        val (hir, bag) = script("fun main() = double(5)\nfun double(x) = x")
        assertEquals(emptyList<String>(), errCodes(bag), "脚本推导不应报错: " + allCodes(bag))
        val d = funDecl(hir, "double")
        val x = d.params[0]
        assertEquals("Nat", typeName(x.type), "形参 x 由调用实参 5 推导为 Nat")
        assertTrue(x.inferred, "形参位 inferred 标记")
        assertEvidence(x.evidence, 1, "形参证据=使用位（调用处）")
        assertEquals("Nat", typeName(d.retType), "返回位由体（x 的落位解）推导")
        assertTrue(d.inferred)
        assertEvidence(d.evidence, 1, "返回位证据=函数体位")
    }

    /** 正例：脚本模式省略字段推导（构造位约束）。 */
    @Test
    fun 脚本模式字段推导() {
        val (hir, bag) = script("fun mk() = P(5)\nstruct P(x)")
        assertEquals(emptyList<String>(), errCodes(bag), "字段推导不应报错: " + allCodes(bag))
        val p = structDecl(hir, "P")
        val x = p.fields[0]
        assertEquals("Nat", typeName(x.type), "字段 x 由构造实参 5 推导为 Nat")
        assertTrue(x.inferred)
        assertEvidence(x.evidence, 1, "字段证据=构造处")
    }

    /** 正例：默认填充（P-11 §3.6）——无使用证据的形参 → Nothing，inferred=false 零虚假。 */
    @Test
    fun 无证据形参默认填充Nothing() {
        val (hir, bag) = script("fun f(x) = x")
        assertEquals(emptyList<String>(), errCodes(bag), "无声使用不应报错: " + allCodes(bag))
        val f = funDecl(hir, "f")
        val x = f.params[0]
        assertEquals("Nothing", typeName(x.type), "P-11 §3.6：无下界形参默认底类型")
        assertFalse(x.inferred, "默认填充不是推导——零虚假 inferred")
        assertEquals(emptyList<String>(), x.evidence)
        // 返回位自身的体证据仍落（最小解=底，证据=函数体位）
        assertEquals("Nothing", typeName(f.retType))
        assertTrue(f.inferred)
        assertEvidence(f.evidence, 1, "返回位证据=函数体位")
    }

    /** t117（负位极性，P-11 §3.4）：未决形参作 g(y) 实参 → 负位上界 y <: Nat 收集，glb 求解为 Nat。
     *  使用位按底语义放行（subtype(占位, Nat) 不另立判据）；落位由轮末复解完成（不再声明位即闭）。 */
    @Test
    fun 未决形参负位极性求解() {
        val (hir, bag) = script("fun g(x: Nat) = 0\nfun f(y) = g(y)")
        assertEquals(emptyList<String>(), errCodes(bag), "未决形参不应在使用位产生类型诊断: " + allCodes(bag))
        val y = funDecl(hir, "f").params[0]
        assertEquals("Nat", typeName(funDecl(hir, "g").retType), "对照：g 返回由体推导")
        // 负位：无下界 + 上界 Nat → glb(U) = Nat（t115 的 Nothing 默认被极性修正——先红后绿）
        assertEquals("Nat", typeName(y.type), "负位形参 → glb(上界) = Nat（P-11 §3.4）")
        assertTrue(y.inferred, "负位解带 inferred 标记")
        assertEvidence(y.evidence, 1, "负位证据=使用位（g(y) 调用处）")
    }

    // ============================ 3. 后到约束（t117 R 轮消化） ============================

    /** t117：先定义后使用——后到约束（声明位之后到达的调用位）经轮末复解消化为落位解；
     *  D-INFER-PENDING 观察级随 R 轮闭合退役（不再登记）。 */
    @Test
    fun 后到约束经R轮消化() {
        val (hir, bag) = script("fun double(x) = x\nfun main() = double(5)")
        assertEquals(emptyList<String>(), errCodes(bag), "后到约束消化不应误报: " + allCodes(bag))
        val x = funDecl(hir, "double").params[0]
        assertEquals("Nat", typeName(x.type), "后到约束（main 的 double(5)）消化为落位解 Nat")
        assertTrue(x.inferred, "落位解带 inferred 标记")
        assertFalse(bag.diags.any { it.code == "D-INFER-PENDING" },
            "R 轮闭合 ⇒ D-INFER-PENDING 退役（约束不再滞后求解）: " + allCodes(bag))
    }

    // ============================ 4. 真门（D-14：元变量不落 HIR / 不占 nid） ============================

    /** 元变量不泄漏：有元变量单元的 JSON 全树不含占位名（HirJson.emit 通道）。 */
    @Test
    fun 元变量占位不出现在产物() {
        val out = compileFrontend(
            "fun main() = double(5)\nfun double(x) = x", "t115.subs", scriptMode = true)
        assertFalse(out.json.contains("(推导:元变量"), "产物不含元变量占位名: " + out.json)
        assertFalse(out.json.contains("形参 double.x"), "占位细节不泄漏")
    }

    /** 零元变量工程单元：载体默认零发射（既有产物 schema 无新字段——SAME 门）。 */
    @Test
    fun 零元变量工程载体零发射() {
        val out = compileFrontend("fun f(x: Nat): Nat = x", "t115.subl")
        assertFalse(out.json.contains("inferred"), "t108 inferred 默认零发射（SAME 门）: " + out.json)
        assertFalse(out.json.contains("evidence"))
        assertEquals("sublimation/hir@v3", sublimation.frontend.json.HirJson.SCHEMA)
    }
}
