package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t110（OQ-5 最小结构归纳，队长裁进 V1）：t97 停机闸的 **measure 递减证明通道**。
 *
 * - **正例**:语法缩小（isShrinkingArg）之外的停机证明——数值 measure（Nat/Int/Rat 良基 <)
 *   义务 `e < p` 经既有通道族（A-LINARITH / A-DEFRULE 定义性归约 / 用户公理）证出 ⇒ 免闸;
 * - **负例先红**（验收尺）:数值**递增** / **非结构** / **无数值 measure 位** 的递归须**真红**——
 *   闸照落 E-NON-STRUCTURAL-REC(R-02 口径不变,工程硬拒）,measure 通道不硬造;
 * - **置换原则自查**(R-12):audit 记录的 measure 义务规则必属既有通道族——proof:* 位不引入私有判据;
 * - **用户公理 fallback**（现役口径）:手工喂归纳原理公理 ⇒ 留一审计入 trustedBase（compileNarrowAudit 观察）。
 */
class T110TerminationMeasureTest {

    private fun fires(src: String, file: String = "t110.subl"): Int {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.count { it.code == "E-NON-STRUCTURAL-REC" } +
            diag.diags.count { it.code == "E-NON-STRUCTURAL-REC" }
    }

    private fun errCount(src: String, file: String = "t110.subl"): Int {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.count { it.severity == Severity.ERROR } +
            diag.diags.count { it.severity == Severity.ERROR }
    }

    // ============================ 正例:measure 递减可证 ⇒ 停机 ============================

    /** 正例#1:定义性归约相助——pred(n) 经 A-DEFRULE 归约为 n - 1,分支事实 n > 0 下 n-1 < n 证出。 */
    @Test fun 定义性归约相助证出() {
        val src = "fun pred(x: Int): Int = x - 1\n" +
            "fun down(n: Int): Int { if (n > 0) { return down(pred(n)) } return 0 }"
        assertEquals(0, fires(src), "pred(n) 归约后 measure 义务 n-1 < n 可证 ⇒ 免闸")
        assertEquals(0, errCount(src), "无其他错误")
    }

    /** 正例#2:深归约嵌套——pred(pred(n)) → n - 2 < n（子项递归归约）。 */
    @Test fun 深归约嵌套证出() {
        val src = "fun pred(x: Int): Int = x - 1\n" +
            "fun down2(n: Int): Int { if (n > 1) { return down2(pred(pred(n))) } return n }"
        assertEquals(0, fires(src), "双层归约后 n-2 < n 可证")
    }

    /** 正例#3:变量减法 + 分支事实——f(n, n - k) 在 k > 0 守卫下 n - k < n 证出（isShrinkingArg 只认字面量,此例原落闸）。 */
    @Test fun 变量减法分支事实证明出() {
        val src = "fun f(n: Nat, k: Nat): Nat { if (k > 0) { return f(n, n - k) } return 0 }"
        assertEquals(0, fires(src), "分支事实 k > 0 ⇒ n - k < n 经 A-LINARITH 证出")
    }

    /** 正例#4:多参数任一 measure 位——measure 位在第二形参 k 亦有效。 */
    @Test fun 多参数measure位() {
        val src = "fun g(n: Nat, k: Nat): Nat { if (n > 0) { return g(n, k - 1) } return 0 }"
        assertEquals(0, fires(src), "k - 1 < k（k 的 measure 递减）证出")
    }

    // ============================ 正例:measure 递减可证 ⇒ 停机 ============================

    /** 正例#5:用户公理 fallback（现役口径）——手工喂 measure 递减事实（本地公理 k > 0 供 n - k < n）免闸。
     * 注:谓词套调用项的 ∀ 公理实例化不在 V1 闭包能力内（∩ 既有 E-CANNOT-PROVE 边界）,扁平公理是手工喂归纳原理的现役形态。 */
    @Test fun 用户公理手工喂measure事实() {
        val src = "fun f(n: Int, k: Int): Int { axiom k > 0; return f(n, n - k) }"
        assertEquals(0, fires(src), "公理 k > 0 供 n - k < n 证出 ⇒ 免闸（无数值分支事实时公理是唯一支撑）")
    }

    // ============================ 负例先红:非结构递归须真红或退化,不得假绿 ============================

    /** 负例#1（真红）:数值递增——up(n + 1) 的 measure 义务 n+1 < n 不可证（分支事实 n > 0 反向）⇒ 闸落。 */
    @Test fun 数值递增真红() {
        val src = "fun up(n: Nat): Nat { if (n > 0) { return up(n + 1) } return 0 }"
        assertEquals(1, fires(src), "n+1 < n 证不出 ⇒ E-NON-STRUCTURAL-REC 真红")
        assertEquals(1, errCount(src), "工程模式硬拒")
    }

    /** 负例#2（真红）:非结构递归——collatz 形 3 * n + 1（非线性）measure 不可证 ⇒ 闸落。 */
    @Test fun 非结构递归真红() {
        val src = "fun collatz(n: Nat): Nat { if (n > 1) { return collatz(3 * n + 1) } return 1 }"
        assertEquals(1, fires(src), "非线性 measure 3n+1 < n 证不出 ⇒ 真红")
    }

    /** 负例#3（真红）:无数值 measure 位——非数值形参的递归（无数值结构依据）⇒ 闸落,不硬造。 */
    @Test fun 无数值measure位真红() {
        val src = "fun echo(s: Str): Str = echo(s)"
        assertEquals(1, fires(src), "无数值 measure 位 ⇒ 回落既有闸（不发明归纳依据）")
    }

    /** 负例#4（真红）:measure 尝试失败后文案带通道足迹（可观测形态——已尝试未证出）。 */
    @Test fun 失败文案含通道足迹() {
        val pr = parseScriptSourceWithNotes("fun up(n: Nat): Nat { if (n > 0) { return up(n + 1) } return 0 }", "t110.subl")
        val (_, diag) = compile(pr.file, pr.notes)
        val msg = diag.diags.firstOrNull { it.code == "E-NON-STRUCTURAL-REC" }?.message ?: ""
        assertTrue(msg.contains("数值 measure 证明通道已尝试"), "失败文案含通道足迹: " + msg)
    }

    // ============================ 置换原则自查 + 审计观察（R-12 / C-8）============================

    /**
     * 验收尺（R-12 正式规则）:measure 义务的证明规则必属**既有通道族**——证明通道不引入私有判据,
     * proof:* 位的算法兜底可查（A-LINARITH 线性算术 / 定义性归约后线性算术 / 用户公理经 Γ）。
     */
    @Test fun 置换原则_measure规则属既有通道族() {
        val src = "fun pred(x: Int): Int = x - 1\n" +
            "fun down(n: Int): Int { if (n > 0) { return down(pred(n)) } return 0 }"
        val pr = parseScriptSourceWithNotes(src, "t110.subl")
        val rep = compileNarrowAudit(pr.file, pr.notes)
        assertEquals(1, rep.audit.measureProofs.size, "measure 证明审计条 1")
        val mp = rep.audit.measureProofs.first()
        assertTrue(mp.rule in CHANNEL_FAMILY, "规则 " + mp.rule + " 必属既有通道族")
        assertTrue(mp.goal.contains("<"), "义务为 < 形 measure")
        assertTrue(mp.evidence.contains("分支") || mp.evidence.contains("公理"), "证据来源可查")
    }

    /** 用户公理 fallback 的 trustedBase 留一审计（必要公理入信任基——k > 0 为 measure 唯一支撑）。 */
    @Test fun 公理fallback入trustedBase() {
        val src = "fun f(n: Int, k: Int): Int { axiom k > 0; return f(n, n - k) }"
        val pr = parseScriptSourceWithNotes(src, "t110.subl")
        val rep = compileNarrowAudit(pr.file, pr.notes)
        assertEquals(0, rep.diag.diags.count { it.severity == Severity.ERROR }, "无错误")
        assertEquals(1, rep.audit.measureProofs.size, "measure 条目 1")
        assertTrue(rep.audit.measureProofs.first().evidence.contains("必要公理"), "必要公理证据")
        assertTrue(rep.audit.trustedBase.any { it.contains("k") && it.contains(">") }, "留一法判定 k > 0 必要 ⇒ trustedBase: " + rep.audit.trustedBase)
    }

    /** 算法自足 measure（无需公理）不入 trustedBase——留一审计只认必要公理（免污染,同 narrowMinus）。 */
    @Test fun 算法自足measure不污染trustedBase() {
        val src = "fun down(n: Nat): Nat { if (n > 0) { return down(n - 1) } return 0 }"
        val pr = parseScriptSourceWithNotes(src, "t110.subl")
        val rep = compileNarrowAudit(pr.file, pr.notes)
        // 语法缩小正例不经 measure 通道（isShrinkingArg 先命中）⇒ measureProofs 空、trustedBase 空
        assertEquals(0, rep.audit.measureProofs.size, "语法缩小位不经通道")
        assertEquals(0, rep.audit.trustedBase.size, "无公理支撑 ⇒ trustedBase 空")
    }

    private companion object {
        /** 既有通道族白名单（proves 返回的规则名集合——proof:* 位的算法兜底可查性）。 */
        val CHANNEL_FAMILY = setOf("R-CONST", "A-LINARITH", "R-SUBST", "R-MP", "A-SUBTYPE", "A-TYPEOF", "A-DEFRULE")
    }
}
