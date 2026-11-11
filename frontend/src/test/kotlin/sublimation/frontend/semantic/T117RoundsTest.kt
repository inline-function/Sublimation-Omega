package sublimation.frontend.semantic

import sublimation.frontend.cli.compileFrontend
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirPurity
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t117（S2 落地 C，t112 §2.1 **按附录 B 修订口径**）验收：R 轮重跑驱动器（推导不动点迭代）。
 *
 * 范围（任务书 t117；先红后绿纪律——负例为首：真不收敛必须真红/真退化，不得假绿）：
 * 1. **零元变量 ⇒ 单趟直通**（保护条款）：零推导单元逐字同单趟——68 工程样本 SAME 门的机器基础；
 * 2. **R 轮驱动**（C-1/C-6）：收敛判据 = 逐元变量 typeEq 结构相等，停于首个收敛轮；
 *    共享单调 NodeIdSource（H1——nid 布局依收敛轮数，确定性保留）；
 * 3. **未收敛处置**（分型表 §3.1）：工程 E-INFER-NOT-CONVERGED 硬拒（真红）/ 脚本 R-02 退化触发点
 *    （不收敛拥有者 impure 化——Any 填充本体 t118）；
 * 4. **后到约束消化**：轮末复解收口（D-INFER-PENDING 退役）；**块体 return 推导**（AST 谓词）；
 * 5. **轮次确定性**：同输入双编译产物逐字同（nid 与轮数确定）。
 *
 * R 池观测（附录 B H3）：rep.rounds 落表（round / label / metaCount / fuelMax / converged）——
 * 每轮一全新 FUEL_DEFAULT 池（每轮新 Elaborator），分布口径供 OQ-2 终裁。
 */
class T117RoundsTest {

    // ============ 先红基线（t116 后态）：无 R 轮 ⇒ 真不收敛样本假绿 ============

    private fun scriptRounds(src: String): RoundsReport {
        val pr = parseScriptSourceWithNotes(src, "t117.subs")
        return compileWithRounds(pr.file, pr.notes, scriptMode = true)
    }

    private fun projectRounds(src: String): RoundsReport {
        val pr = parseSourceWithNotes(src, "t117.subl")
        return compileWithRounds(pr.file, pr.notes, scriptMode = false)
    }

    private fun errCodes(rep: RoundsReport) = rep.diag.diags
        .filter { it.severity == Severity.ERROR }.map { it.code }

    private fun allCodes(rep: RoundsReport) = rep.diag.diags.map { it.code }

    private fun funDecl(rep: RoundsReport, name: String): HirFunDecl =
        rep.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl }
            .filterIsInstance<HirFunDecl>().first { it.name == name }

    private fun typeName(t: sublimation.frontend.hir.HirType?): String =
        (t as? HirNamedType)?.name ?: "<null>"

    // ============================ 1. 零元变量单趟直通（保护条款） ============================

    /** 零元变量单元：R 池单轮即停（metaCount==0）——SAME 门的机器基础（nids 同单趟）。 */
    @Test
    fun 零元变量单元单趟直通() {
        val rep = projectRounds("fun add(a: Nat, b: Nat): Nat = a + b")
        assertEquals(1, rep.roundsUsed, "零元变量 ⇒ 单趟直通: " + rep.rounds)
        assertEquals(1, rep.rounds.size)
        assertTrue(rep.rounds.single().converged)
        assertEquals(0, rep.rounds.single().metaCount)
        assertFalse(rep.notConverged)
        assertFalse(rep.degraded)
        assertEquals(emptyList<String>(), errCodes(rep), "零元变量单元不应报错: " + allCodes(rep))
    }

    // ============================ 2. R 轮驱动（C-1/C-6） ============================

    /** 先使用后定义（main 先于 double）：R=3 收敛停于首个收敛轮；终轮解落位 + 证据可追溯。 */
    @Test
    fun r轮收敛停于首个收敛轮() {
        val rep = scriptRounds("fun main() = double(5)\nfun double(x) = x")
        assertEquals(emptyList<String>(), errCodes(rep), "收敛单元不应报错: " + allCodes(rep))
        assertTrue(rep.roundsUsed in 2..INFER_ROUNDS_DEFAULT, "收敛 ⇒ 至少两轮（首解+验证），至多 R: " + rep.roundsUsed)
        assertTrue(rep.rounds.last().converged, "终轮收敛")
        assertFalse(rep.notConverged)
        val d = funDecl(rep, "double")
        assertEquals("Nat", typeName(d.params[0].type), "形参 x 终轮落位 Nat")
        assertTrue(d.params[0].inferred)
        assertEquals("Nat", typeName(d.retType), "返回位终轮落位 Nat")
        assertTrue(d.inferred)
    }

    /** 共享单调 nids（H1）：同输入双编译 ⇒ 产物 JSON 逐字同（nid 布局依收敛轮数但确定）。 */
    @Test
    fun 轮次确定性双编译同产物() {
        val src = "fun main() = double(5)\nfun double(x) = x"
        val a = compileFrontend(src, "t117d.subs", scriptMode = true)
        val b = compileFrontend(src, "t117d.subs", scriptMode = true)
        assertEquals(a.json, b.json, "同输入 ⇒ 轮次与 nid 确定 ⇒ JSON 逐字同")
        assertFalse(a.json.contains("(推导:元变量"), "终轮种子全覆盖 ⇒ 产物零占位")
        assertFalse(b.json.contains("(推导:元变量"), "终轮种子全覆盖 ⇒ 产物零占位")
    }

    // ============================ 3. 未收敛处置（先红：真不收敛必须真红/真退化） ============================

    /** **负例先红**：真不收敛样本（a 的返回类型经 List 构造逐轮自生长——永不达不动点）。
     *  脚本模式 ⇒ R-02 退化触发点：不收敛拥有者 impure 化再跑一轮（本体 Any 填充归 t118）。 */
    @Test
    fun 真不收敛脚本退化impure() {
        val rep = scriptRounds("fun a(x: Nat) = (b(x), 0)\nfun b(x: Nat) = (a(x), 1)\nvar m = a(5)")
        assertTrue(rep.notConverged, "真不收敛必须报 notConverged（不得假绿）")
        assertTrue(rep.degraded, "脚本 ⇒ R-02 退化触发点再跑一轮")
        assertEquals(INFER_ROUNDS_DEFAULT, rep.roundsUsed, "不收敛 ⇒ 放满 R 轮")
        assertEquals("infer-degraded", rep.rounds.last().label, "退化轮 label 落表: " + rep.rounds)
        assertEquals(1, rep.rounds.count { it.label == "infer-degraded" }, "退化恰好一轮")
        assertFalse(errCodes(rep).any { it == "E-INFER-NOT-CONVERGED" },
            "脚本模式不落工程硬拒码（退化通道优先）: " + allCodes(rep))
        val a = funDecl(rep, "a")
        assertEquals(HirPurity.IMPURE, a.purity, "不收敛拥有者退化轮内置 impure（R-02 触发点）")
    }

    /** **负例先红（工程）**：同一不收敛样本 ⇒ E-INFER-NOT-CONVERGED 真红并阻塞（不退化）。 */
    @Test
    fun 真不收敛工程硬拒() {
        val rep = projectRounds("fun a(x: Nat) = (b(x), 0)\nfun b(x: Nat) = (a(x), 1)")
        assertTrue(rep.notConverged)
        assertFalse(rep.degraded, "工程模式不退化——硬拒通道")
        assertTrue(rep.diag.diags.any {
            it.code == "E-INFER-NOT-CONVERGED" && it.severity == Severity.ERROR
        }, "E-INFER-NOT-CONVERGED 真红: " + allCodes(rep))
        assertTrue(rep.diag.hasError, "硬拒=阻塞（hasError）")
    }

    // ============================ 4. 后到约束消化 + 块体 return 推导 ============================

    /** 先定义后使用：后到约束（声明位之后到达的调用位）经轮末复解消化（D-INFER-PENDING 退役）。 */
    @Test
    fun 后到约束轮末复解消化() {
        val rep = scriptRounds("fun double(x) = x\nfun main() = double(5)")
        assertEquals(emptyList<String>(), errCodes(rep), "后到约束消化不应误报: " + allCodes(rep))
        assertTrue(rep.roundsUsed in 2..INFER_ROUNDS_DEFAULT)
        assertFalse(rep.notConverged)
        val x = funDecl(rep, "double").params[0]
        assertEquals("Nat", typeName(x.type), "后到约束消化为落位解 Nat")
        assertTrue(x.inferred, "落位解带 inferred 标记（非默认填充）")
        assertFalse(rep.diag.diags.any { it.code == "D-INFER-PENDING" }, "R 轮闭合 ⇒ PENDING 退役")
    }

    /** t117 块体 return 推导：块体含 return ⇒ 返回位元变量收集 return 值下界（AST 谓词先行）。
     *  块体无 return 保持 Null 语义（main 不被建元变量——e2e 零命中门）。 */
    @Test
    fun 块体return推导() {
        val rep = scriptRounds("fun f(n) {\n  return n + 1\n}\nvar m = f(4)")
        assertEquals(emptyList<String>(), errCodes(rep), "块体 return 推导不应报错: " + allCodes(rep))
        val f = funDecl(rep, "f")
        assertEquals("Nat", typeName(f.retType), "返回类型由 return 值推导为 Nat")
        assertTrue(f.inferred, "返回位 inferred 标记")
        assertTrue(f.evidence.isNotEmpty(), "返回位证据=return 位（可追溯）")
        // 对照：块体无 return ⇒ 无返回位元变量（Null 语义保持——保护 e2e 68 门）
        val rep2 = scriptRounds("fun main() {\n  var a = 5\n}")
        val main2 = funDecl(rep2, "main")
        assertFalse(main2.inferred, "块体无 return ⇒ 不建返回位元变量（零载体默认零发射）")
        assertEquals(emptyList<String>(), main2.evidence)
    }

    // ============================ 5. R 池观测（H3：按轮 label + 燃料分布） ============================

    /** R 池观测表：每轮一全新 FUEL_DEFAULT 池；未耗尽（预算形态落 OQ-2）；label 序列形态机器可检。 */
    @Test
    fun r池观测表() {
        val rep = scriptRounds("fun double(x) = x\nfun main() = double(5)")
        val labels = rep.rounds.map { it.label }
        assertTrue(labels.isNotEmpty() && labels.all { it.startsWith("infer-r") }, "label 按轮落表: " + labels)
        rep.rounds.forEach { o ->
            assertTrue(o.metaCount >= 0)
            assertTrue(o.fuelMaxConsumed < 237500, "每轮池独立且未耗尽（<FUEL_DEFAULT）: " + o)
        }
        // 收敛单元轮数确定（同输入同轮数——确定性机器证据）
        val rep2 = scriptRounds("fun double(x) = x\nfun main() = double(5)")
        assertEquals(rep.roundsUsed, rep2.roundsUsed, "同输入 ⇒ 同轮数")
        assertEquals(rep.rounds.map { it.metaCount }, rep2.rounds.map { it.metaCount }, "同输入 ⇒ 逐轮 metaCount 同")
    }

}