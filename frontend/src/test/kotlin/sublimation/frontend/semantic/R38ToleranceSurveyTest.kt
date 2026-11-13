package sublimation.frontend.semantic

import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirFile
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * R-38 容忍面穷举（t59，P-10 步骤 1 收尾）：把「推导未完成即容忍」的闸能触达的源级形态
 * 钉成测试。每条「可掩盖真错」形态断言**当前放行**（记录当前行为；封堵后这批测试必须翻，
 * 否则封堵未生效）。负向证据（n*）证明闸的判别性：具体型通道照常落错。
 *
 * 形态清单与清零路线见 documents/P-10-R38容忍面清单.md。M7（方法糖扩展函数桶 lambda 实参位）
 * 与 M8（fold 族的未解接收者理论参数）依赖 project/扩展注册路径，脚本模式无法构造最小复现，
 * 以 e2e 样本（core/collection/set.subl:52 的 s.fold(0){...} 链）为最小复现，见清单文档。
 */
class R38ToleranceSurveyTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src.trimIndent(), "t59.subl")
        val (hir, bag) = compile(pr.file, pr.notes)
        pr.diag.forEach { d ->
            when (d.severity) {
                Severity.ERROR -> bag.error(d.code, d.pos, d.message)
                Severity.WARN -> bag.warn(d.code, d.pos, d.message)
                Severity.HINT -> bag.hint(d.code, d.pos, d.message)
                Severity.SUPPLEMENT -> bag.supplement(d.code, d.pos, d.message)
            }
        }
        return hir to bag
    }

    private fun errs(bag: DiagBag): List<Diag> = bag.diags.filter { it.severity == Severity.ERROR }

    /**
     * R-38 待封堵（M1：lambda 形参位 var 标注）。
     * var f: (Nat) => Nat = { x => "abc" }：lambda 形参位未回代 → 初值型含合成叶 → 闸容忍。
     * 封堵（var 标注位双向回代）后本测试须翻 assertTrue（真错落 E-TYPE-MISMATCH）。
     */
    @Test
    fun m1_lambdaVarAnnotationBodyErrorTolerated() {
        val e = script("""
            var f: (Nat) => Nat = { x => "abc" }
        """).second
        assertTrue(errs(e).isEmpty(), "M1 当前：无任何诊断（真错被静默）；R-38 待封堵")
    }

    /**
     * R-38 待封堵（M2：未知字段/无签名方法糖的合成返回位）。
     * (5).unknown → 合成型「字段unknown」（无签名、无根因诊断）→ 标注位核对被闸容忍。
     */
    @Test
    fun m2_unknownFieldErrorTolerated() {
        val e = script("""
            var v: Nat = (5).unknown
        """).second
        assertTrue(errs(e).isEmpty(), "M2 当前：无任何诊断；R-38 待封堵")
    }

    /**
     * R-38 待封堵（M2b：prelude 方法无单文件签名 —— Channel.isClosed）。
     * 接收者 Channel[Nat] 真型已知，但 isClosed 返回合成位「方法isClosed」→ 标注位被闸容忍。
     */
    @Test
    fun m2b_methodWithoutSignatureTolerated() {
        val e = script("""
            var ch: Channel[Nat] = Channel[Nat](0)   // t144（R-26.1）：调用位类型实参迁 []
            var b: Nat = ch.isClosed()
        """).second
        assertTrue(errs(e).isEmpty(), "M2b 当前：无任何诊断（真错 Bool 被静默）；R-38 待封堵")
    }

    /**
     * R-38 待封堵（M3：双目算符合成操作数 —— binExpr 自带 isSynthetic 旁路，非闸内）。
     * v 的型为合成位 → v == 1 两边混型无声放过（真错：左操作数型未知却参与比较）。
     */
    @Test
    fun m3_binopSyntheticOperandTolerated() {
        val e = script("""
            var v = (5).unknown
            var b: Bool = v == 1
        """).second
        assertTrue(errs(e).none { it.code == "E-TYPE-MISMATCH" }, "M3 当前：比较真错被静默；R-38 同族待封堵")
    }

    /**
     * R-38 待封堵（M4：函数体合成型 vs 声明返回位）。
     * fun g(): Nat = (5).unknown：体为合成位（M2 上游），funBody 核对被闸容忍。
     */
    @Test
    fun m4_funReturnSynthBodyTolerated() {
        val e = script("""
            fun g(): Nat = (5).unknown
        """).second
        assertTrue(errs(e).isEmpty(), "M4 当前：无任何诊断；R-38 待封堵")
    }

    /**
     * R-38 待封堵（M5：联合分支含合成位）。
     * if (True) { v } else { 0 } → Union[合成, Nat] → 合并型含合成叶 → 全下游核对静默。
     */
    @Test
    fun m5_unionSynthBranchTolerated() {
        val e = script("""
            var v = (5).unknown
            var n: Nat = if (True) { v } else { 0 }
        """).second
        assertTrue(errs(e).none { it.code == "E-TYPE-MISMATCH" }, "M5 当前：真错被静默；R-38 待封堵")
    }

    /**
     * R-38 待封堵（M6：when 解构主题为合成型）。
     * 主题合成 → 模式变量无真型 → 分支值合成 → 标注位静默（e2e 值与字面量.subl 同族）。
     */
    @Test
    fun m6_whenDeconstructSynthSubjectTolerated() {
        val e = script("""
            var p = (5).unknown
            var n: Nat = when (p) { (a, b) -> a else -> 0 }
        """).second
        assertTrue(errs(e).none { it.code == "E-TYPE-MISMATCH" }, "M6 当前：真错被静默；R-38 待封堵")
    }

    /**
     * G1/G2 —— t59 登记为「当前放行」的两处与闸无关同源 V1 缺口，**t60 已封堵**：
     * 赋值右值 / 形参默认值均走与形参/返回值同一 adaptOk 判据（E-TYPE-MISMATCH / E-DEFAULT-TYPE）。
     */
    @Test
    fun g1_assignmentTypeCheckFires() {
        val e = script("""
            var y: @mut Nat = 0
            y = "abc"
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "G1 已封堵（t60）：赋值右值型不符须落 E-TYPE-MISMATCH")
    }

    @Test
    fun g2_defaultTypeCheckFires() {
        val e = script("""
            fun df(a: Nat = "abc"): Nat = a
            var r = df()
        """).second
        assertTrue(errs(e).any { it.code == "E-DEFAULT-TYPE" }, "G2 已封堵（t60）：默认值型不符须落 E-DEFAULT-TYPE")
    }

    /**
     * 负向证据（对照）：lambda 实参在形参具体时由 ADR-22 回代 → 真错被报（不静默）。
     */
    @Test
    fun n1_lambdaArgAtConcreteFormalCatchesBodyError() {
        val e = script("""
            fun m(x: Nat, f: (Nat) => Nat): Nat = f(x)
            var r: Nat = m(1, { n => "s" })
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "lambda 实参位真错落错（R-38 闸的判别证据）")
    }

    /**
     * 负向证据（对照）：双目算符两边具体型 → 真错被报。
     */
    @Test
    fun n2_binopConcreteOperandsCatch() {
        val e = script("""
            var b: Bool = "s" == 1
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "具体型比较真错落错")
    }
}