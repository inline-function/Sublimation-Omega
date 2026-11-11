package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirPImp
import sublimation.frontend.hir.HirPTop
import sublimation.frontend.hir.HirTCall
import sublimation.frontend.hir.HirTInt
import sublimation.frontend.hir.HirTName
import sublimation.frontend.hir.HirTerm

/**
 * t109（证明器 V1 核,R3-V1 §四 P2;R-11/R-12 裁决批）:证明通道 + PROOF_BUDGET 的直接级验收。
 *
 * **测试形态**:ProofChecker 直接构造（判据 oracle = 测试伪实现,同 [ElaboratorOracle] 契约）——
 * 编译器内部同一通道的准入边界,不经语法层（`<:`/`:` 关系原子尚无命题位写法,写法随 P-11/R-02 落）。
 *
 * **验收尺**：
 * - 负例先红:每个通道的**真红 case**(判据本身不相容 / 无定义性归约规则 / 预算耗尽）落 negative test
 *   (proves → null=FAILED,不被任何段救活,proof 不发明接受）;
 * - 置换原则（R-12 收为正式规则）:proof:* 命中的类型位,**算法兜底直查**必须同时为真——
 *   证明永不创造无算法支撑的接受;
 * - PROOF_BUDGET(OQ-2):耗尽=可观测形态（D-PROOF-BUDGET 通道足迹入诊断,去重一次/单元）。
 */
class T109ProverV1Test {

    private var nid = 0
    private fun nid(): Int = ++nid

    private fun named(name: String): HirNamedType = HirNamedType(name, emptyList(), null, nid(), SYNTHETIC_POS)
    private fun term(name: String): HirTName = HirTName(name, null, nid(), SYNTHETIC_POS)
    private fun intTerm(v: String): HirTInt = HirTInt(v, nid(), SYNTHETIC_POS)
    private fun call(callee: String, args: List<HirTerm>): HirTCall = HirTCall(term(callee), args, nid(), SYNTHETIC_POS)
    private fun atom(head: String, terms: List<HirTerm>): HirPAtom =
        HirPAtom(head, terms, nid(), SYNTHETIC_POS)

    /** 测试 oracle:数值阶层 Nat<Int<Rat + 登记型 + 定义性归约规则表（与 ElaboratorOracle 同契约）。 */
    private class TestOracle(pool: Int = Int.MAX_VALUE) : ProverOracle {
        private val rank = mapOf("Nat" to 0, "Int" to 1, "Rat" to 2)
        private val bindings = mutableMapOf("x" to "Nat", "y" to "Int")
        private val rules = mutableMapOf<String, Pair<List<String>, HirTerm>>()
        private val traces = mutableListOf<String>()
        private var spent = 0
        private val cap = pool
        private var reported = false

        fun bind(name: String, type: String) { bindings[name] = type }
        fun define(name: String, params: List<String>, body: HirTerm) { rules[name] = params to body }

        private fun rankOf(t: sublimation.frontend.hir.HirType): Int? =
            (t as? HirNamedType)?.name?.let { rank[it] }

        override fun judgeSubtype(lo: sublimation.frontend.hir.HirType, hi: sublimation.frontend.hir.HirType): Boolean {
            val a = rankOf(lo); val b = rankOf(hi)
            return a != null && b != null && a <= b
        }

        override fun termType(t: sublimation.frontend.hir.HirTerm): sublimation.frontend.hir.HirType? = when (t) {
            is HirTName -> (bindings[t.name] ?: if (t.name in rank) t.name else null)?.let { tn ->
                HirNamedType(tn, emptyList(), null, 0, SYNTHETIC_POS)
            }
            else -> null
        }

        override fun fitsLiteral(value: String, t: sublimation.frontend.hir.HirType): Boolean {
            val r = rankOf(t) ?: return false
            val n = value.toLongOrNull() ?: return false
            return r >= 1 || n >= 0L
        }

        override fun definitionalExpand(name: String, args: List<sublimation.frontend.hir.HirTerm>): sublimation.frontend.hir.HirTerm? {
            val rule = rules[name] ?: return null
            val (params, body) = rule
            if (args.size != params.size) return null
            return subst(body, params.zip(args).toMap())
        }

        private fun subst(t: HirTerm, sub: Map<String, HirTerm>): HirTerm = when (t) {
            is HirTName -> sub[t.name] ?: t
            is HirTCall -> HirTCall(subst(t.callee, sub), t.args.map { subst(it, sub) }, 0, SYNTHETIC_POS)
            else -> t
        }

        override fun spend(n: Int): Boolean {
            if (spent + n > cap) return false
            spent += n
            return true
        }
        override fun trace(channel: String) { traces.add(channel) }
        override fun snapshotTraces(): List<String> = traces.toList()
        override fun budgetLabel(): String = "PROOF_BUDGET=$cap（测试池,已耗 $spent）"
        override fun budgetReported(): Boolean = reported
        override fun markBudgetReported() { reported = true }
    }

    private fun prover(oracle: ProverOracle, diag: DiagBag = DiagBag()): ProofChecker =
        ProofChecker({ nid() }, diag, oracle)

    // ============================ A-SUBTYPE（裁决 1）============================

    /** 裁决 1 正例:数值阶层相容位 Nat <: Int —— 合成 <: atom 命中判据 oracle → A-SUBTYPE。 */
    @Test fun ASUBTYPE_相容位命中判据oracle() {
        val rule = prover(TestOracle()).proves(emptyList(), atom("<:", listOf(term("Nat"), term("Int"))))
        assertEquals("A-SUBTYPE", rule, "合成 <: atom 经 ADR-39 判据 oracle 证出")
    }

    /** 负例#1（真红）:Int <: Nat 判据本体不相容——oracle 与用户公理（空 Γ）都救不回,proves=FAILED。 */
    @Test fun ASUBTYPE_不相容位真红() {
        val rule = prover(TestOracle()).proves(emptyList(), atom("<:", listOf(term("Int"), term("Nat"))))
        assertNull(rule, "判据不相容 ⇒ FAILED(null)：proof 不发明接受（R-NO-GUESS）")
    }

    /** 反身位 Nat <: Nat 成立（≤ 判据自反同口径）。 */
    @Test fun ASUBTYPE_自反位成立() {
        val rule = prover(TestOracle()).proves(emptyList(), atom("<:", listOf(term("Nat"), term("Nat"))))
        assertEquals("A-SUBTYPE", rule, "自反位经 oracle 判出")
    }

    // ============================ (e:T) 通道（裁决 2）============================

    /** 裁决 2 正例:字面量项 (1 : Nat) —— 数值阶层判出 → A-TYPEOF。 */
    @Test fun eT_正字面量判出() {
        val rule = prover(TestOracle()).proves(emptyList(), atom(":", listOf(intTerm("1"), term("Nat"))))
        assertEquals("A-TYPEOF", rule, "1 : Nat 经数值阶层判出")
    }

    /** 负例#2（真红）:(-1 : Nat) —— Nat 需值 ≥ 0,判据红,proves=FAILED。 */
    @Test fun eT_负字面量真红() {
        val rule = prover(TestOracle()).proves(emptyList(), atom(":", listOf(intTerm("-1"), term("Nat"))))
        assertNull(rule, "-1 : Nat 判据红 ⇒ FAILED，回落兜底/硬拒（分型 R-12）")
    }

    /** 正例:登记项 (x : Nat)（x 绑定 Nat）→ A-TYPEOF。 */
    @Test fun eT_登记项判出() {
        val oracle = TestOracle(); oracle.bind("x", "Nat")
        val rule = prover(oracle).proves(emptyList(), atom(":", listOf(term("x"), term("Nat"))))
        assertEquals("A-TYPEOF", rule, "x : Nat 符号表登记型判出")
    }

    /** 负例#3（真红）:(y : Nat)（y 绑定 Int,Int ≬ Nat）→ FAILED,不发明接受。 */
    @Test fun eT_登记项不相容真红() {
        val rule = prover(TestOracle()).proves(emptyList(), atom(":", listOf(term("y"), term("Nat"))))
        assertNull(rule, "y : Nat 不相容 ⇒ FAILED")
    }

    // ============================ A-DEFRULE 定义性归约（裁决 3）============================

    /** 裁决 3 正例:id(1)（单表达式纯函数 ∀x.id(x)=x)归约为 1 后 (1 : Nat) 判出 → A-DEFRULE。 */
    @Test fun ADEFRULE_单表达式纯函数归约() {
        val oracle = TestOracle().apply { define("id", listOf("x"), term("x")) }
        val rule = prover(oracle).proves(emptyList(), atom(":", listOf(call("id", listOf(intTerm("1"))), term("Nat"))))
        assertEquals("A-DEFRULE", rule, "id(1) 定义性归约为 1 后 (1 : Nat) 判出")
    }

    /** 正例:嵌套调用归约——inc(id(1)) → 1（深度递归子项归约）。 */
    @Test fun ADEFRULE_嵌套调用归约() {
        val oracle = TestOracle().apply {
            define("id", listOf("x"), term("x"))
            define("inc", listOf("x"), term("x"))
        }
        val inner = call("id", listOf(intTerm("1")))
        val rule = prover(oracle).proves(emptyList(), atom(":", listOf(call("inc", listOf(inner)), term("Nat"))))
        assertEquals("A-DEFRULE", rule, "inc(id(1)) 嵌套归约为 1 后判出")
    }

    /** 负例#4（真红）:无规则调用 (f(1) : Nat) —— 定义性归约无规则,不降级为成立,FAILED。 */
    @Test fun ADEFRULE_无规则不降级() {
        val oracle = TestOracle()
        val rule = prover(oracle).proves(emptyList(), atom(":", listOf(call("f", listOf(intTerm("1"))), term("Nat"))))
        assertNull(rule, "无归约规则 + Γ 空 ⇒ FAILED：proof 不发明接受")
    }

    /** 负例#5（真红）:@impure 函数不作定义性归约规则（纯度判据同 gate）——登记者的规则不经证明通道。 */
    @Test fun ADEFRULE_impure函数不入规则() {
        val oracle = TestOracle()     // 不注册 side（@impure,生产端 registerFunDef 拒注）
        val rule = prover(oracle).proves(emptyList(), atom(":", listOf(call("side", listOf(intTerm("1"))), term("Nat"))))
        assertNull(rule, "@impure 调用不作定义性归约 ⇒ FAILED")
    }

    // ============================ PROOF_BUDGET（裁决 4 / OQ-2）============================

    /** 裁决 4 正例:预算耗尽=可观测形态——D-PROOF-BUDGET 落诊断（通道足迹 + 余量）,proves 回落 null。 */
    @Test fun PROOFBUDGET_耗尽落诊断且回落() {
        val diag = DiagBag()
        val oracle = TestOracle(pool = 4)   // 每次 proves ≈ R-SUBST + P2 通道各 1 单位（同池消费粒度）
        val prover = prover(oracle, diag)
        val goal = atom("<:", listOf(term("Nat"), term("Int")))
        assertNotNull(prover.proves(emptyList(), goal), "首次预算内证出")
        assertNotNull(prover.proves(emptyList(), goal), "二次预算内证出")
        assertNull(prover.proves(emptyList(), goal), "第三次预算耗尽 ⇒ 回落 null(不发明放行）")
        val budget = diag.diags.filter { it.code == "D-PROOF-BUDGET" }
        assertEquals(1, budget.size, "去重一次/单元")
        val msg = budget.first().message
        assertTrue(msg.contains("PROOF_BUDGET"), "预算余量入文案：$msg")
        assertTrue(msg.contains("通道足迹"), "通道足迹入文案")
        assertEquals(Severity.SUPPLEMENT, budget.first().severity, "耗尽=D-级可观测,非阻断")
    }

    /** 耗尽后下一次 attempts 亦不重复落 D-PROOF-BUDGET(去重)且仍证不出。 */
    @Test fun PROOFBUDGET_去重且持续回落() {
        val diag = DiagBag()
        val oracle = TestOracle(pool = 2)
        val prover = prover(oracle, diag)
        val goal = atom("<:", listOf(term("Nat"), term("Int")))
        assertNotNull(prover.proves(emptyList(), goal), "首次预算内证出（R-SUBST + P2 各 1 单位）")
        repeat(3) { assertNull(prover.proves(emptyList(), goal), "耗尽后持续回落 null") }
        assertEquals(1, diag.diags.count { it.code == "D-PROOF-BUDGET" }, "仅一条去重诊断")
    }

    // ============================ 置换原则自查（R-12 收为正式规则）============================

    /**
     * 验收尺（R-12 正式规则）:任一 proof:* 命中的类型位,其**算法兜底解必须同时可查**——
     * 直查不经 Γ、不经证明通道；证明永不创造无算法支撑的接受。
     */
    @Test fun 置换原则_命中位必有算法兜底() {
        val oracle = TestOracle().apply {
            define("id", listOf("x"), term("x"))
            bind("x", "Nat")
        }
        val prover = prover(oracle)
        val goals = listOf(
            atom("<:", listOf(term("Nat"), term("Int"))) as sublimation.frontend.hir.HirProp,
            atom(":", listOf(intTerm("1"), term("Nat"))),
            atom(":", listOf(term("x"), term("Nat"))),
            atom(":", listOf(call("id", listOf(intTerm("1"))), term("Nat"))),
        )
        goals.forEach { goal ->
            val rule = prover.proves(emptyList(), goal)
            assertNotNull(rule, "通道须证出此相容样本")
            assertTrue(directFallback(oracle, goal as HirPAtom), "置换原则:rule=$rule 的类型位须算法兜底可查")
        }
    }

    /** 算法兜底直查（不经证明通道、不经 Γ）——A-SUBTYPE 与 (e:T) 两族的判据本体。 */
    private fun directFallback(oracle: ProverOracle, goal: HirPAtom): Boolean {
        return when (goal.head) {
        "<:" -> {
            val lo = oracle.termType(goal.terms[0]) ?: return false
            val hi = oracle.termType(goal.terms[1]) ?: return false
            oracle.judgeSubtype(lo, hi)
        }
        ":" -> {
            val t = oracle.termType(goal.terms[1]) ?: return false
            val e = goal.terms[0].let { (it as? HirTCall)?.let { sub -> oracle.definitionalExpand(
                (sub.callee as? HirTName)?.name ?: "", sub.args) } ?: it } ?: return false
            if (e is HirTInt) oracle.fitsLiteral(e.value, t)
            else oracle.judgeSubtype(oracle.termType(e) ?: return false, t)
        }
        else -> false
        }
    }

    // ============================ 既有五段回归（通道不破旧契约）============================

    /** ⊤ 经既有段证出（五段行为不变——通道段不改变旧命题的可证性）。 */
    @Test fun 既有五段_恒真命题仍证出() {
        val goal = HirPTop(nid(), SYNTHETIC_POS)
        assertNotNull(prover(TestOracle()).proves(emptyList(), goal), "⊤ 经既有段证出（旧基线 R-MP）")
    }

    /** R-CONST 前置消解:⊤ → p 蕴含归约为 p 的证明（段序:常量段在通道段之前）。 */
    @Test fun 既有五段_常量消解不破() {
        val oracle = TestOracle()
        val goal = HirPImp(HirPTop(nid(), SYNTHETIC_POS), HirPTop(nid(), SYNTHETIC_POS), nid(), SYNTHETIC_POS)
        assertNotNull(prover(oracle).proves(emptyList(), goal), "⊤→⊤ 经既有段证出（旧基线 R-MP）")
    }

    /** 未注入 oracle 的零实现形态:五段行为不变（NoOracle → P2 段恒 null）。 */
    @Test fun 未注入通道_五段行为不变() {
        val plain = ProofChecker({ nid() }, DiagBag())
        assertNull(plain.proves(emptyList(), atom("<:", listOf(term("Nat"), term("Int")))),
            "零实现:通道段恒失败,回落既有五段口径")
        assertFalse(plain.proves(emptyList(), HirPTop(nid(), SYNTHETIC_POS)) == null,
            "零实现:R-CONST 段照常")
    }
}
