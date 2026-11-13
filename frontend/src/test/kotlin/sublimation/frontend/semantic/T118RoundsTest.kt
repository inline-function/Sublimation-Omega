package sublimation.frontend.semantic

import sublimation.frontend.cli.compileFrontend
import sublimation.frontend.cli.runCli
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirPurity
import sublimation.frontend.hir.HirStructDecl
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t118（S2 落地收口件 D）验收：R-02 退化通道本体 + --no-degrade 门（OQ-5）+ 收敛 soundness 两因
 * （t117 排队根因：Any 软顶假收敛 / 算术合成名入种子）+ 义务生产点位（提案 §3.2 injectSyntheticFact）。
 *
 * 先红纪律（负例为首）：
 * 1. **无解线（健全性优先）**：lub ⊄ U ⇒ E-INFER-NO-SOLUTION 硬拒——**不可退化**（双模式）；
 *    上界约束跨轮保形（种子链携带），终轮复检无解线不消失；
 * 2. **退化本体**：脚本模式不收敛/不可定 且非无解 ⇒ 空位填 Any（t108 载体 inferred=false 零发射）+
 *    函数 impure（t97 停机闸跳过 / t96 纯度面记账）+ D-INFER-DEGRADED 观察级
 *    （函数+空位+证据尝试清单+退化原因分类）；局部退化（仅仍在变的空位）；
 * 3. **--no-degrade 门（OQ-5 队长裁=给）**：脚本退化即失败（CI 可用性门，--no-trust 族同设）；
 * 4. **假收敛不得假绿**：互递归元组生长样本必须真不收敛（R 放满），不被 Any 软顶吸收伪装成收敛；
 * 5. **边界登记**：字段无证据默认 Nothing（E-INFER-NO-EVIDENCE 报错口径留 §10-Q3 裁决）。
 */
class T118RoundsTest {

    private val noSol = "fun g2(x: Nat) = 0\nfun f(y) = g2(y)\nvar s = f(\"a\")"
    private val nonConv = "fun a(x: Nat) = (b(x), 0)\nfun b(x: Nat) = (a(x), 1)\nvar m = a(5)"
    private val conv = "fun main() = double(5)\nfun double(x) = x"

    private fun scriptRounds(src: String, noDegrade: Boolean = false): RoundsReport {
        val pr = parseScriptSourceWithNotes(src, "t118.subs")
        return compileWithRounds(pr.file, pr.notes, scriptMode = true, noDegrade = noDegrade)
    }

    private fun errCodes(rep: RoundsReport) = rep.diag.diags
        .filter { it.severity == Severity.ERROR }.map { it.code }

    private fun allCodes(rep: RoundsReport) = rep.diag.diags.map { it.code }

    private fun degMsgs(rep: RoundsReport) = rep.diag.diags
        .filter { it.code == "D-INFER-DEGRADED" }.map { it.message }

    private fun funDecl(rep: RoundsReport, name: String): HirFunDecl =
        rep.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl }
            .filterIsInstance<HirFunDecl>().first { it.name == name }

    private fun structDecl(rep: RoundsReport, name: String): HirStructDecl =
        rep.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl }
            .filterIsInstance<HirStructDecl>().first { it.name == name }

    private fun typeName(t: sublimation.frontend.hir.HirType?): String =
        (t as? HirNamedType)?.name ?: "<null>"

    // ============================ 1. 无解线（健全性优先——不可退化） ============================

    /** **负例先红**：无解样本（候选 Str 与上界 Nat 不相容）脚本模式 ⇒ E-INFER-NO-SOLUTION 真红；
     *  上界经种子链跨轮保形 ⇒ 终轮复检无解线不消失；且**不得退化**（健全性优先——无解≠不收敛）。 */
    @Test
    fun 无解样本脚本真红不退化() {
        val rep = scriptRounds(noSol)
        assertTrue(errCodes(rep).contains("E-INFER-NO-SOLUTION"),
            "无解线必须真红（上界跨轮保形，终轮复检不消失）: " + allCodes(rep))
        assertFalse(rep.degraded, "无解空位不得退化填 Any（健全性优先）")
        assertFalse(allCodes(rep).contains("D-INFER-DEGRADED"), "无解 ⇒ 无退化诊断: " + allCodes(rep))
    }

    /** 工程模式同一无解样本：硬拒红（omitted 形参 E-OMITTED-TYPE + 调用位 E-TYPE-MISMATCH）。 */
    @Test
    fun 无解样本工程模式硬拒() {
        val pr = parseSourceWithNotes(noSol, "t118.subl")
        val rep = compileWithRounds(pr.file, pr.notes, scriptMode = false)
        assertTrue(rep.diag.hasError, "工程模式无解样本必须硬拒红（不退化）: " + allCodes(rep))
        assertFalse(rep.degraded, "工程模式永不退化")
    }

    // ============================ 2. R-02 退化本体（可审计） ============================

    /** **负例先红**：真不收敛样本（互递归元组生长）⇒ 退化触发：空位填 Any + 载体零发射（inferred=false）+
     *  函数 impure（t97 停机闸跳过 / t96 纯度面记账）+ 无 ERROR（退化=退路，非错）。 */
    @Test
    fun 退化样本Any填充且载体零发射() {
        val rep = scriptRounds(nonConv)
        assertTrue(rep.degraded, "脚本不收敛 ⇒ R-02 退化再跑一轮")
        assertFalse(rep.diag.hasError, "退化是退路不是错（impure 记账替代报错）: " + allCodes(rep))
        val a = funDecl(rep, "a")
        assertEquals("Any", typeName(a.retType), "退化空位填 Any（R-02 本体）")
        assertFalse(a.inferred, "退化不是推导：载体 inferred=false（Any 填充不冒充有证据的解）")
        assertEquals(emptyList<String>(), a.evidence, "退化载体零证据发射（尝试清单归 D-INFER-DEGRADED）")
        assertEquals(HirPurity.IMPURE, a.purity, "退化函数记 impure：停机闸跳过 / 纯度面不纯记账")
    }

    /** D-INFER-DEGRADED 审计四元组：函数 + 空位 + 证据尝试清单 + 退化原因分类（OQ-9 分型）。 */
    @Test
    fun 退化诊断四元组可审计() {
        val rep = scriptRounds(nonConv)
        val msgs = degMsgs(rep)
        assertTrue(msgs.isNotEmpty(), "退化必须落 D-INFER-DEGRADED 观察级: " + allCodes(rep))
        val m = msgs.joinToString(" | ")
        assertEquals(2, msgs.size, "审计元 1：按拥有者逐函数落袋（a 与 b 各一）: " + msgs.size)
        assertTrue(msgs.all { it.contains("函数") && it.contains("返回位") }, "审计元 1+2：函数 + 空位")
        assertTrue(m.contains("返回"), "审计元 2：空位（种类 + 名）")
        assertTrue(m.contains("use:"), "审计元 3：证据尝试清单（使用位源序）")
        assertTrue(m.contains("未收敛") && m.contains("R 轮耗尽"), "审计元 4：退化原因分类")
        assertTrue(m.contains("--no-degrade"), "退化诊断指引 CI 门开关")
    }

    /** 局部退化口径：仅仍在变的空位（a/b 的返回位）退化；无关函数不受牵连。 */
    @Test
    fun 局部退化不牵连全单元() {
        val rep = scriptRounds(nonConv + "\nfun ok(): Nat = 7")
        assertFalse(errCodes(rep).any { it.startsWith("E-") }, "无关函数照常编译: " + allCodes(rep))
        val ok = funDecl(rep, "ok")
        assertEquals("Nat", typeName(ok.retType), "无关函数返回类型逐字不变（局部退化口径）")
        assertEquals(HirPurity.PURE, ok.purity, "无关函数保持纯（不因邻近退化被记账）")
    }

    // ============================ 3. --no-degrade 门（OQ-5 队长裁=给） ============================

    /** 门在退化样本上真红：E-INFER-DEGRADE-FORBIDDEN + 不跑退化轮（无 D-INFER-DEGRADED）。 */
    @Test
    fun noDegrade门在退化样本真红() {
        val rep = scriptRounds(nonConv, noDegrade = true)
        assertTrue(rep.diag.hasError, "--no-degrade：退化即失败（CI 门）")
        assertTrue(errCodes(rep).contains("E-INFER-DEGRADE-FORBIDDEN"), "门码落袋: " + allCodes(rep))
        assertFalse(allCodes(rep).contains("D-INFER-DEGRADED"), "门触发 ⇒ 不退化落袋: " + allCodes(rep))
    }

    /** 门在收敛样本上静默（无退化即无门触发）。 */
    @Test
    fun noDegrade门在收敛样本静默() {
        val out = compileFrontend(conv, "t118c.subs", scriptMode = true, noDegrade = true)
        assertFalse(out.diag.hasError, "收敛 + --no-degrade ⇒ 门静默: " + out.diag.diags.map { it.code })
    }

    /** 退化样本默认放行（无 --no-degrade ⇒ 退化=退路，无错可编译）。 */
    @Test
    fun 退化样本默认放行() {
        val out = compileFrontend(nonConv, "t118d.subs", scriptMode = true)
        assertFalse(out.diag.hasError, "默认口径：脚本退化放行（非错）: " + out.diag.diags.map { it.code })
        assertTrue(out.diag.diags.any { it.code == "D-INFER-DEGRADED" }, "退化诊断可见")
    }

    /** CLI 解析层：runCli --no-degrade 旗标透传 —— check 退出码随门翻转。 */
    @Test
    fun cliNoDegrade解析门翻转() {
        val f = File.createTempFile("t118cli", ".subs").apply { writeText(nonConv) }
        try {
            val gated = runCli(arrayOf("check", f.path, "--no-degrade"))
            assertEquals(1, gated, "check + --no-degrade + 退化样本 ⇒ 退出码 1（门真红）")
            val plain = runCli(arrayOf("check", f.path))
            assertEquals(0, plain, "check（无门）+ 退化样本 ⇒ 退出码 0（退化放行）")
            val bad = runCli(arrayOf("check", f.path, "--no-degrade-x"))
            assertEquals(2, bad, "未知选项 ⇒ 退出码 2（用法错误）")
        } finally { f.delete() }
    }

    // ============================ 4. 收敛 soundness 两因（先红：假收敛不得假绿） ============================

    /** **负例先红（soundness 因 ①）**：互递归元组生长样本必须真不收敛——R 放满 8 轮、无工程误判、
     *  不被 Any 软顶吸收伪装成收敛（t117 修复：正位不并入陈种子——并入则上轮 (Any,Nat) 种子
     *  吸收本轮 ((Any,Nat),Nat) 新解（协变+Any 顶）⇒ 假收敛）。 */
    @Test
    fun 假收敛样本不得假绿() {
        val rep = scriptRounds(nonConv)
        assertTrue(rep.notConverged, "真不收敛：不得被伪装成收敛（Any 软顶吸收）")
        assertEquals(INFER_ROUNDS_DEFAULT, rep.roundsUsed, "R 放满（无提前假停）")
        assertFalse(errCodes(rep).contains("E-INFER-NO-SOLUTION"), "非无解（生长，非矛盾）: " + allCodes(rep))
    }

    /** **soundness 因 ②（回归守卫）**：含算术合成名的块体 return 样本仍收敛（normPlaceholder 归一——
     *  未决合成名不入种子 ⇒ 不动点可达；防 round 8 不收敛假门）。 */
    @Test
    fun 算术合成名样本仍收敛() {
        val rep = scriptRounds("fun f(n) {\n  return n + 1\n}\nvar m = f(4)")
        assertFalse(rep.notConverged, "合成名归一后必须收敛（不得永不收敛）: " + rep.rounds)
        assertFalse(errCodes(rep).any { it.startsWith("E-") }, "收敛样本无错: " + allCodes(rep))
        val f = funDecl(rep, "f")
        assertEquals("Nat", typeName(f.retType), "块体 return 推导落位 Nat")
    }

    /** 退化产物确定性：同输入双编译（退化路径）⇒ JSON 逐字同（轮数/nid 确定）。 */
    @Test
    fun 退化产物确定性双编译同产物() {
        val a = compileFrontend(nonConv, "t118r.subs", scriptMode = true)
        val b = compileFrontend(nonConv, "t118r.subs", scriptMode = true)
        assertEquals(a.json, b.json, "退化路径亦确定：轮数与 nid 布局确定 ⇒ JSON 逐字同")
        assertFalse(a.json.contains("(推导:"), "终轮零占位/零合成名泄漏（normPlaceholder + 种子全覆盖）")
    }

    // ============================ 5. 边界登记（分型表余项 / 容器默认） ============================

    /** E-INFER-NO-EVIDENCE 边界：字段无任何使用处 ⇒ Nothing 默认填充（§3.6——健全最宽松），
     *  不落报错码（报错口径留 §10-Q3 裁决；V1 默认先行）。 */
    @Test
    fun 字段无证据默认Nothing() {
        val rep = scriptRounds("struct Box(v)\nfun main() = 0")
        assertFalse(rep.diag.hasError, "无证据字段默认填充不是错: " + allCodes(rep))
        val box = structDecl(rep, "Box")
        assertEquals("Nothing", typeName(box.fields.first().type), "无使用处字段 ⇒ Nothing 默认（§3.6）")
        assertFalse(box.fields.first().inferred, "默认填充不是推导（inferred=false）")
    }

    /** 容器默认（§3.6 listOf/mutableListOf 行）边界登记：当前语言无容器字面量/listOf 预声明
     *  （容器经 Nil/Cons 结构构造子位组装——其形参/字段空位走通用元变量机制）——本件不引入
     *  新预声明（P-11 待裁 V2）；已验证零nid直通门与 SAME 门不受影响。 */
    @Test
    fun 容器默认行为边界登记() {
        // 结构构造子组装的链表：空位经 ctor 实参证据推导（非 listOf 预声明通道）
        val rep = scriptRounds("struct Cons(head, tail)\nvar xs = Cons(0, Cons(1, Cons(2, Cons(3, 0))))")
        val cons = structDecl(rep, "Cons")
        // head 由 ctor 实参下界推导（脚本模式）；构造子位证据源序落表
        assertTrue(cons.fields.any { (it.type as? HirNamedType)?.name == "Nat" },
            "容器空位经通用推导通道（ctor 证据）: " + cons.fields.map { typeName(it.type) })
    }

    /** 义务生产点位（提案 §3.2）smoke：解闭合点/签名事实注入零干扰——有推导的脚本样本
     *  编译干净、产物零推导痕迹泄漏（注入 atom 仅入 Γ，provenance=SYNTHETIC 分账）。 */
    @Test
    fun 签名事实注入零干扰() {
        val out = compileFrontend(
            "fun calc(n) = n\nfun main() {\n  var v = calc(4)\n  print(v)\n}", "t118s.subs", scriptMode = true)
        assertFalse(out.diag.hasError, "签名事实注入不得引入错误: " + out.diag.diags.map { it.code })
        assertFalse(out.json.contains("(推导:"), "产物零推导痕迹（合成 atom 仅 Γ 内部，不入产物）")
    }

    /** R-12 置换原则自查（验收条）：合成签名事实只增 Γ 真事实（按已解类型构造），不得放行假目标——
     *  有签名事实注入的样本里，证不出的目标仍走既有硬拒通道（proof:* 位算法兜底：A-TYPEOF/A-SUBTYPE
     *  oracle 复用 TypeInfer.subtype 判据；证不出回落 closureProve（用户公理）否则 E-PROP-* 硬拒）。 */
    @Test
    fun r12置换自查假目标仍硬拒() {
        val rep = scriptRounds("fun g(n) = n\nfun main() {\n  var y = 8\n  var x = g(y)\n  ∴ (y : Str)\n}")
        assertTrue(rep.diag.hasError, "假目标（y:Str）必须硬拒——注入事实不得削弱拒绝: " + allCodes(rep))
    }
}
