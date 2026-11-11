package sublimation.frontend.semantic

import java.io.File
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirAnonFun
import sublimation.frontend.hir.HirBlock
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirExprStmt
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirIntLit
import sublimation.frontend.hir.HirLambda
import sublimation.frontend.hir.HirNameRef
import sublimation.frontend.hir.HirPurity
import sublimation.frontend.hir.HirReturn
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarExpr
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * t122（R-16 A2 · I-1 收口第二期）不纯闭包 + 判据状态机事件化锁定。
 *
 * - **单趟事件回放**：t96/t107/t94 判据经主趟帧链事件（消 gateWalk/mutCapture 六族平行遍历），
 *   闸判据（E-IMPURE-CALL/E-MUT-CAPTURE）三路放行逐字不变——caller 豁免维持**注解口径**。
 * - **不纯闭包单调不动点**：f 调 g 且 g 不纯 ⇒ f 不纯（提案 §1.2/R-16 A2 六类种子 + 调用边）；
 *   推导入 [HirFunDecl.purity] 产物面回写 + D-PURITY-INFERRED 审计四元组（名/种子源/路径/规则）。
 * - **OQ-7（队长裁 T）**：HirCall.calleeImpure 阶段 2 真化（被调侧结论，mode 无关）。
 * - **OQ-2（挂 unit 级）**：脚本顶层语句隐式不纯——D-SCRIPT-IMPLICIT-IMPURE 可见，不造合成 main。
 * - **差分对拍门（P-11 §9.1）**：同逻辑 .subl/.subs 忽略 inferred/evidence 后 hir 结构相等;
 *   护栏③ 真「纯函数调不纯」样本（dp-pure-calls-impure）入对拍集，机器锁定。
 */
class T122A2ClosureTest {

    private val pairDir = File("frontend/src/test/resources/diff-pairs")

    private fun load(name: String): String = File(pairDir, name).readText(Charsets.UTF_8)

    private fun projRounds(src: String, file: String = "t122.subl") =
        parseSourceWithNotes(src, file).let { compileWithRounds(it.file, it.notes, scriptMode = false) }

    private fun scriptRounds(src: String, file: String = "t122.subs") =
        parseScriptSourceWithNotes(src, file).let { compileWithRounds(it.file, it.notes, scriptMode = true) }

    private fun errorsOf(report: RoundsReport): Int =
        report.diag.diags.count { it.severity == Severity.ERROR }

    private fun supplement(report: RoundsReport, code: String): List<sublimation.frontend.diag.Diag> =
        report.diag.diags.filter { it.code == code }

    /**
     * hir 结构签名（差分对拍口径：忽略 inferred/evidence——只描结构位）。
     * 差分对拍即以此签名替代全量 JSON 比对：结构位（kind/名/纯度/calleeImpure/参数序）逐字同，
     * 推导装饰位（inferred/evidence）与诊断序不签约。
     */
    private fun hirSig(hir: HirFile): String {
        val sb = StringBuilder()
        fun sig(e: Any?) {
            when (e) {
                is HirFunDecl -> { sb.append("FD(").append(e.name).append(",p=").append(e.purity)
                    sb.append(",params=["); e.params.forEach { sb.append(it.name).append(",") }
                    sb.append("],body="); sig(e.body); sb.append(")") }
                is HirCall -> { sb.append("CALL(callee="); sig(e.callee); sb.append(",args=[");
                    e.args.forEach { sig(it); sb.append(",") }
                    sb.append("],calleeImpure=").append(e.calleeImpure).append(")") }
                is HirNameRef -> sb.append("NR(").append(e.name).append(")")
                is HirIntLit -> sb.append("I(").append(e.value).append(")")
                is HirBlock -> { sb.append("B["); e.stmts.forEach { sig(it); sb.append(",") }; sb.append("]") }
                is HirExprStmt -> { sb.append("ES("); sig(e.expr); sb.append(")") }
                is HirReturn -> { sb.append("R("); sig(e.expr); sb.append(")") }
                is HirVarStmt -> { sb.append("VS(").append(e.name).append("="); sig(e.value); sb.append(")") }
                is HirVarExpr -> { sb.append("VE(").append(e.name).append("="); sig(e.value); sb.append(")") }
                is HirLambda -> { sb.append("L(p=").append(e.purity).append(",body="); sig(e.body); sb.append(")") }
                is HirAnonFun -> { sb.append("AF(p=").append(e.purity).append(",body="); sig(e.body); sb.append(")") }
                null -> sb.append("null")
                else -> sb.append(e.javaClass.simpleName)
            }
        }
        hir.entries.forEach { ent ->
            when (ent) {
                is HirDeclEntry -> sig(ent.decl)
                is HirStmtEntry -> sig(ent.stmt)
                else -> sb.append(ent.javaClass.simpleName)
            }
            sb.append(";")
        }
        return sb.toString()
    }

    // ================= 差分对拍门（P-11 §9.1）=================

    /** 对拍 A：同逻辑双模式共享面——纯函数链 hir 结构（忽略 inferred/evidence）逐字同。 */
    @Test fun 差分对拍A_同逻辑双模式_hir结构同() {
        val rp = projRounds(load("dp-mode-share.subl"), "dp-mode-share.subl")
        val rs = scriptRounds(load("dp-mode-share.subs"), "dp-mode-share.subs")
        assertEquals(0, errorsOf(rp), "工程模式无错")
        assertEquals(0, errorsOf(rs), "脚本模式无错")
        assertEquals(hirSig(rp.hir), hirSig(rs.hir), "同逻辑双模式 hir 结构签相等（inferred/evidence 忽略）")
    }

    /** 对拍 B（护栏③ 真「纯函数调不纯」样本）：未注解 q 调 @impure p——两模式 hir 结构同。 */
    @Test fun 差分对拍B_纯调不纯_hir结构同() {
        val rp = projRounds(load("dp-pure-calls-impure.subl"), "dp-pure-calls-impure.subl")
        val rs = scriptRounds(load("dp-pure-calls-impure.subs"), "dp-pure-calls-impure.subs")
        assertEquals(hirSig(rp.hir), hirSig(rs.hir),
            "q 的推导纯度与 calleeImpure 真化两模式一致——推导与闸口径 mode 无关")
    }

    // ================= OQ-7 真化 + 回写 + 审计四元组 =================

    /** B 工程模式：q 推导 IMPURE（产物面回写）+ D-PURITY-INFERRED 审计四元组 + calleeImpure 真化 + 闸 1。 */
    @Test fun 纯调不纯_推导回写与审计四元组() {
        val rp = projRounds(load("dp-pure-calls-impure.subl"), "dp-pure-calls-impure.subl")
        val d = rp.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? HirFunDecl }
            .first { it.name == "q" }
        assertEquals(HirPurity.IMPURE, d.purity, "q 未注解但调 @impure p ⇒ 闭包推导回写 IMPURE")
        val audits = supplement(rp, "D-PURITY-INFERRED")
        assertEquals(1, audits.size, "D-PURITY-INFERRED 恰 1 条（p 注解位不出审计）")
        val msg = audits[0].message
        listOf("q", "种子源：", "传播路径", "闭包单调不动点").forEach { kw ->
            assertTrue(msg.contains(kw), "审计四元组缺构件：$kw")
        }
        assertEquals(1, rp.diag.diags.count { it.code == "E-IMPURE-CALL" },
            "caller 豁免维持注解口径——q 推导 IMPURE 不放行其体内调用闸（T96 行为不变）")
    }

    /** B 脚本模式：根隐式不纯入口免闸，但推导与真化与工程模式同（mode 无关的语义层）。 */
    @Test fun 纯调不纯_脚本模式免闸且推导同() {
        val rs = scriptRounds(load("dp-pure-calls-impure.subs"), "dp-pure-calls-impure.subs")
        assertEquals(0, rs.diag.diags.count { it.code == "E-IMPURE-CALL" }, "脚本隐式不纯入口：体内调用免闸")
        val d = rs.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? HirFunDecl }
            .first { it.name == "q" }
        assertEquals(HirPurity.IMPURE, d.purity, "脚本模式推导同——闭包 mode 无关")
        assertEquals(1, supplement(rs, "D-PURITY-INFERRED").size)
    }

    /** 传播链二层：r（未注解）调 q（推导 IMPURE）——r 亦回写 IMPURE，r 处调用位 calleeImpure 真化。 */
    @Test fun 传播链二层回写() {
        val src = "@impure fun p(a: Nat): Nat = a\n" +
            "fun q(): Nat { p(1) }\n" +
            "fun r(): Nat { q() }"
        val rp = projRounds(src)
        fun purityOf(nm: String) = rp.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? HirFunDecl }
            .first { it.name == nm }.purity
        assertEquals(HirPurity.IMPURE, purityOf("q"), "一层传播：q 调 @impure p")
        assertEquals(HirPurity.IMPURE, purityOf("r"), "二层传播：r 调推导 IMPURE 的 q")
        assertEquals(2, supplement(rp, "D-PURITY-INFERRED").size, "q 与 r 各一条审计")
    }

    // ================= 种子覆盖（S2 内建 / S3 捕获 / S6 体效应、unchecked）=================

    /** unchecked 只豁免检查不豁免效应：pureWrap 回写 IMPURE，闸 0，调用位仍真化。 */
    @Test fun unchecked不豁免产物回写() {
        val src = "@impure fun impureZero(): Null { print(\"x\") }\n" +
            "fun pureWrap(): Null { unchecked impureZero() }"
        val rp = projRounds(src)
        val d = rp.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? HirFunDecl }
            .first { it.name == "pureWrap" }
        assertEquals(HirPurity.IMPURE, d.purity, "unchecked 包住的是检查豁免——效应仍计，推导回写")
        assertEquals(1, supplement(rp, "D-PURITY-INFERRED").size)
        assertEquals(0, rp.diag.diags.count { it.code == "E-IMPURE-CALL" }, "unchecked 三路放行不变")
    }

    /** 局部 fun 富化（s:序 键）：纯 g 内局部 @impure inner 被调——g 经边传播回写，调用位分类用登记位。 */
    @Test fun 局部fun富化分类() {
        val src = "fun g(): Null { @impure fun inner(): Null { print(\"z\") } inner() }"
        val rp = projRounds(src)
        val g = rp.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? HirFunDecl }
            .first { it.name == "g" }
        assertEquals(HirPurity.IMPURE, g.purity, "g 经局部 fun 边传播回写 IMPURE")
        assertEquals(1, rp.diag.diags.count { it.code == "E-IMPURE-CALL" },
            "局部 @impure fun 调用位照闸——注解位分类（登记位）零变")
    }

    // ================= OQ-2 脚本顶层隐式不纯（挂 unit 级）=================

    /** 脚本顶层 var 语句：D-SCRIPT-IMPLICIT-IMPURE 1 条 + 不造合成 main（条目仍只含源语句）。 */
    @Test fun 脚本顶层隐式不纯载体可见() {
        val src = "var top: Nat = 0\nfun h(): Nat = top"
        val rs = scriptRounds(src)
        val ds = supplement(rs, "D-SCRIPT-IMPLICIT-IMPURE")
        assertEquals(1, ds.size, "含 1 条顶层语句 ⇒ D-SCRIPT-IMPLICIT-IMPURE 恰 1 条")
        assertTrue(ds[0].message.contains("1 条顶层语句"), "诊断含顶层语句计数")
        assertEquals(2, rs.hir.entries.size, "不造合成 main——条目=源 1 语句 + 1 声明")
        // 对照：无顶层语句的脚本不落 D-SCRIPT
        val rs2 = scriptRounds("fun h(): Nat = 1")
        assertEquals(0, supplement(rs2, "D-SCRIPT-IMPLICIT-IMPURE").size)
    }

    // ================= R 轮观测（OQ-9 单列）=================

    /** 闭包规模（v+e）入 R 轮观测：B 样本 2 节点 + 1 边 > 0。 */
    @Test fun 闭包规模入R轮观测() {
        val rp = projRounds(load("dp-pure-calls-impure.subl"), "dp-pure-calls-impure.subl")
        assertTrue(rp.rounds.isNotEmpty(), "R 轮观测非空")
        val last = rp.rounds.last()
        assertTrue(last.closureSize > 0, "末轮闭包规模 v+e > 0（2 节点 1 边）——观测单列落位")
    }
}