package sublimation.frontend.semantic

import java.io.File
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirAtomProvenance
import sublimation.frontend.hir.HirBlock
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirExprStmt
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirNameRef
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirTName
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * t123（R-16 S4 第三机理 · 上文上提）锁定。
 *
 * - **失败义务的签名位合成**（脚本模式；工程模式不开——R-02 对偶硬拒通道零变）：纯声明链 FUN 帧内
 *   调 impure 被调且帧内三路供给皆无 ⇒ f.preps 合成 <pure<g>> 欠条（HirPAtom.provenance=SYNTHETIC，
 *   分账同 t118/C-8：不入信任基）+ D-LIFTED-CONTEXT 审计四元组（函数/义务原文/失败原因分类/证据尝试）。
 * - **链式上提**与 t122 闭包**共用同一调用边集**（零新增遍历）：欠条沿反向调用边转嫁给声明纯调用方;
 *   吸收终态 = 声明 impure 帧（注解/退化/R-07 捕获）/ 脚本顶层（root 隐式不纯入口）。三级正交：
 *   Γ 帧级事实（t108）/签名级欠条（本件）/产物级元信息（t118）载体互不流动，不改 purity。
 * - **分型红线（R-16）**：仅纯度族（pure<f>）可上提——停机族/类型确立族不可（R-02 红线，不开绕过口子）。
 * - **OQ-5 队长裁=给**：--no-lift 门——脚本上提即失败（E-LIFT-FORBIDDEN，同 --no-degrade 双口形态）。
 */
class T123LiftTest {

    private val pairDir = File("frontend/src/test/resources/diff-pairs")

    private fun load(name: String): String = File(pairDir, name).readText(Charsets.UTF_8)

    private fun projRounds(src: String, file: String = "t123.subl") =
        parseSourceWithNotes(src, file).let { compileWithRounds(it.file, it.notes, scriptMode = false) }

    private fun scriptRounds(src: String, file: String = "t123.subs", noLift: Boolean = false) =
        parseScriptSourceWithNotes(src, file)
            .let { compileWithRounds(it.file, it.notes, scriptMode = true, noLift = noLift) }

    private fun errorsOf(report: RoundsReport): Int =
        report.diag.diags.count { it.severity == Severity.ERROR }

    private fun supplement(report: RoundsReport, code: String): List<sublimation.frontend.diag.Diag> =
        report.diag.diags.filter { it.code == code }

    private fun declOf(report: RoundsReport, nm: String): HirFunDecl =
        report.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? HirFunDecl }.first { it.name == nm }

    /** 合成 <pure<g>> 欠条名表（provenance=SYNTHETIC 分账;用户角组不计）。 */
    private fun liftedNames(d: HirFunDecl): List<String> =
        d.preps.mapNotNull {
            val a = it as? HirPAtom ?: return@mapNotNull null
            if (a.head == "pure" && a.terms.size == 1 && a.provenance == HirAtomProvenance.SYNTHETIC)
                (a.terms[0] as? HirTName)?.name else null
        }.sorted()

    // ================= 机理 1：签名位合成（静默 + 审计四元组）=================

    @Test fun 签名位合成_静默与审计四元组() {
        val src = "@impure fun p(a: Nat): Nat = a\n" +
            "fun q(): Null { p(1) }"
        val rs = scriptRounds(src)
        assertEquals(0, errorsOf(rs), "上提样本静默：默认口零错误（脚本记账）")
        assertEquals(listOf("p"), liftedNames(declOf(rs, "q")), "q.preps 合成 <pure<p>> 欠条（签名位）")
        val ds = supplement(rs, "D-LIFTED-CONTEXT")
        assertEquals(1, ds.size, "审计四元组恰 1 条")
        val msg = ds[0].message
        assertTrue(msg.contains("q") && msg.contains("<pure<p>>"), "文案含函数名与义务原文")
        assertTrue(msg.contains("无帧内供给"), "文案含失败原因分类")
        assertTrue(msg.contains("`p`@SourceRange(start=SourcePos(line=2"),
            "文案含证据尝试清单（调用位——同 D-PURITY-INFERRED 的 名@pos 审计口径）")
    }

    @Test fun 工程模式零上提_硬拒通道零变() {
        val src = "@impure fun p(a: Nat): Nat = a\n" +
            "fun q(): Null { p(1) }"
        val rp = projRounds(src)
        assertEquals(emptyList(), liftedNames(declOf(rp, "q")), "工程模式不开上提（R-02 对偶）")
        assertEquals(1, rp.diag.diags.count { it.code == "E-IMPURE-CALL" }, "红通道零变：q 体内 p 调用照闸")
        assertEquals(0, supplement(rp, "D-LIFTED-CONTEXT").size)
    }

    // ================= 机理 2：链式上提（共用闭包边集）=================

    @Test fun 链式上提_欠条逐层转嫁() {
        val src = "@impure fun g(a: Nat): Nat = a\n" +
            "fun f(): Null { g(1) }\n" +
            "fun h(): Null { f() }\n" +
            "fun s(): Null { h() }"
        val rs = scriptRounds(src)
        assertEquals(listOf("g"), liftedNames(declOf(rs, "f")), "种子：f 直调 g")
        assertEquals(listOf("g"), liftedNames(declOf(rs, "h")), "一层转嫁：h 调 f ⇒ h 同欠 pure<g>")
        assertEquals(listOf("g"), liftedNames(declOf(rs, "s")), "二层转嫁：s 调 h ⇒ s 同欠（同一欠条名）")
        assertEquals(3, supplement(rs, "D-LIFTED-CONTEXT").size, "f/h/s 各一条审计")
    }

    @Test fun 吸收终态_impure声明帧与脚本顶层() {
        val src = "@impure fun g(a: Nat): Nat = a\n" +
            "fun f(): Null { g(1) }\n" +
            "@impure fun h(): Null { f() }\n" +
            "fun s(): Null { f() }\n" +
            "s()"
        val rs = scriptRounds(src)
        assertEquals(listOf("g"), liftedNames(declOf(rs, "f")), "种子 f")
        assertEquals(emptyList(), liftedNames(declOf(rs, "h")), "@impure h 吸收终态①：声明不纯帧不背欠条")
        assertEquals(listOf("g"), liftedNames(declOf(rs, "s")), "声明纯调用方继续上提（顶层 root 吸收）")
        assertEquals(2, supplement(rs, "D-LIFTED-CONTEXT").size, "仅 f 与 s（h 被吸收、root 吸收）")
    }

    @Test fun 供给命中_用户角组免上提() {
        val src = "@impure fun side(): Null { }\n" +
            "fun pureCaller()<pure<side>>: Null { side() }"
        val rs = scriptRounds(src)
        assertEquals(emptyList(), liftedNames(declOf(rs, "pureCaller")), "用户角组供给命中——证出纯，不上提")
        assertEquals(0, supplement(rs, "D-LIFTED-CONTEXT").size)
    }

    @Test fun 内建不纯欠条_print() {
        val rs = scriptRounds("fun q(): Null { print(\"x\") }")
        assertEquals(listOf("print"), liftedNames(declOf(rs, "q")), "内建不纯 print 的契约名上提")
        assertEquals(1, supplement(rs, "D-LIFTED-CONTEXT").size)
    }

    // ================= 分型红线（R-16）=================

    @Test fun 分型红线_停机族不上提() {
        val src = "fun rec(n: Nat): Nat = rec(n)"
        val rs = scriptRounds(src)
        val gated = scriptRounds(src, noLift = true)
        assertEquals(0, supplement(rs, "D-LIFTED-CONTEXT").size, "停机族义务不可上提")
        assertEquals(0, supplement(gated, "D-LIFTED-CONTEXT").size, "--no-lift 门下停机族亦不上提")
        assertEquals(0, gated.diag.diags.count { it.code == "E-LIFT-FORBIDDEN" }, "停机族不落上提门红")
    }

    @Test fun 局部fun欠条_转嫁包围函数签名() {
        // 注：局部 fun 的 @impure 注解在解析层被丢弃（Parser.kt:879 parseFun(emptyList())——
        // 既有解析缺口，已登记结构债 I-6，不在本件范围;欠条机制按登记位口径工作）
        val src = "@impure fun p(a: Nat): Nat = a\n" +
            "fun f(): Null { fun inner(): Null { p(1) } inner() }"
        val rs = scriptRounds(src)
        assertEquals(listOf("p"), liftedNames(declOf(rs, "f")),
            "局部 fun 无独立签名面——欠条（p）转嫁包围 f 的全局签名")
        assertEquals(1, supplement(rs, "D-LIFTED-CONTEXT").size, "仅 f 一条审计（inner 不在产物位合成）")
    }

    // ================= OQ-5：--no-lift 门（双口形态）=================

    @Test fun noLift门_真红_双口形态() {
        val src = "@impure fun p(a: Nat): Nat = a\n" +
            "fun q(): Null { p(1) }"
        val gated = scriptRounds(src, noLift = true)
        val free = scriptRounds(src)
        assertTrue(gated.diag.diags.any { it.code == "E-LIFT-FORBIDDEN" }, "门下真红：E-LIFT-FORBIDDEN")
        assertEquals(1, supplement(gated, "D-LIFTED-CONTEXT").size, "门下仍记账（审计可见）")
        assertEquals(1, supplement(free, "D-LIFTED-CONTEXT").size, "默认口仅审计四元组（静默记账）")
        assertEquals(0, errorsOf(free), "默认口静默（同 --no-degrade 双口形态）")
        assertTrue(errorsOf(gated) >= 1, "门下真红")
    }

    // ================= 差分对拍 C（护栏③ 扩面）=================

    @Test fun 差分对拍C_链式上提_hir结构同() {
        val rp = projRounds(load("dp-lift-chain.subl"), "dp-lift-chain.subl")
        val rs = scriptRounds(load("dp-lift-chain.subs"), "dp-lift-chain.subs")
        assertEquals(hirSig(rp.hir), hirSig(rs.hir), "同逻辑双模式：结构位（纯度/calleeImpure/参数序）逐字同")
        assertEquals(3, supplement(rs, "D-LIFTED-CONTEXT").size, "脚本侧：f/h/s 三条欠条审计")
        assertEquals(0, supplement(rp, "D-LIFTED-CONTEXT").size, "工程侧零上提")
        assertEquals(listOf("g"), liftedNames(declOf(rs, "s")), "脚本侧欠条转嫁至链顶")
    }

    // ================= 轮次确定性（P-11 §9.3）=================

    @Test fun 上提确定性_同输入同产物() {
        val src = "@impure fun g(a: Nat): Nat = a\n" +
            "fun f(): Null { g(1) }\n" +
            "fun h(): Null { f() }"
        val a = scriptRounds(src)
        val b = scriptRounds(src)
        assertEquals(liftedNames(declOf(a, "h")), liftedNames(declOf(b, "h")), "欠条集确定")
        assertEquals(supplement(a, "D-LIFTED-CONTEXT").size, supplement(b, "D-LIFTED-CONTEXT").size)
    }

    /** hir 结构签名（差分对拍口径：忽略 preps 与 inferred/evidence——契约位是模式分歧通道：
     * 上提只在脚本模式记账欠条，工程模式硬拒——同 P-11 §9.1 的类型推导等价尺不签契约姿态）。 */
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
                is HirExprStmt -> { sb.append("ES("); sig(e.expr); sb.append(")") }
                is HirBlock -> { sb.append("B["); e.stmts.forEach { sig(it); sb.append(",") }; sb.append("]") }
                else -> sb.append("#")
            }
        }
        hir.entries.forEach { ent ->
            val d = (ent as? HirDeclEntry)?.decl
            if (d != null) sig(d) else sb.append("S")
        }
        return sb.toString()
    }
}
