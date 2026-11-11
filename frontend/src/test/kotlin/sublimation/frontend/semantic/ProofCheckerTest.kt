package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirAxiom
import sublimation.frontend.hir.HirBecause
import sublimation.frontend.hir.HirBlock
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirExistsElim
import sublimation.frontend.hir.HirExistsIntro
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirPAnd
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirPBot
import sublimation.frontend.hir.HirPIff
import sublimation.frontend.hir.HirPImp
import sublimation.frontend.hir.HirPNot
import sublimation.frontend.hir.HirPOr
import sublimation.frontend.hir.HirPForall
import sublimation.frontend.hir.HirPExists
import sublimation.frontend.hir.HirProofStatus
import sublimation.frontend.hir.HirStrategy
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirTBin
import sublimation.frontend.hir.HirTInt
import sublimation.frontend.hir.HirTName
import sublimation.frontend.hir.HirTerm
import sublimation.frontend.hir.HirTDollar
import sublimation.frontend.hir.HirVerdict
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 命题与证明检查 V1 验收（S-3 验收三件套；ADR-6 §四 t9 范围）。
 *
 * 上半部：[ProofChecker] 代数/通道的纯函数级断言（renders/normalize/groundTerms/substDollar/
 * closure（MP/MT/矛盾/∀ 实例化）/proves 通道顺序（常量/算术/代换/闭包）——旧 Prop.kt·ArithProve.kt
 * 可判定主干移植，P-7 对齐）。
 * 下半部：义务核对落树——经 compile 全管线，按义务节点（HirAxiom/HirBecause/HirStrategy/
 * HirExistsIntro/HirExistsElim 等）的 proofContext/rule/verdict 断言（ADR-6 §四 4.1 结论上树），
 * 诊断码按 ERROR / WARN / SUPPLEMENT 三族分检（E- 前缀错误、W- 前缀警告、D- 前缀留痕）。
 *
 * 语法注意（本语言无算术优先级、左结合；D-10）：
 * - 命题蕴含写 `→`（`->` 在命题位不构成中缀）；嵌套算术须括号（`d = (a - b)`，非 `d = a - b`）；
 * - ∃ 引入句点前须留空（`∃ x = (1) . P`），否则被解析为字段访问。
 */
class ProofCheckerTest {

    // ============================ 代数件（旧 PropLogic/ArithProve 移植，P-7） ============================

    private var seq = 5000
    private fun mkProver(): ProofChecker = ProofChecker({ seq++ }, DiagBag())
    private fun nid() = seq++
    private fun i(v: Long) = HirTInt(v.toString(), nid(), SYNTHETIC_POS)
    private fun nm(s: String) = HirTName(s, null, nid(), SYNTHETIC_POS)
    private fun atom(head: String, vararg ts: HirTerm): HirPAtom = HirPAtom(head, ts.toList(), nid(), SYNTHETIC_POS)
    private fun imp(a: HirPAtom, b: HirPAtom) = HirPImp(a, b, nid(), SYNTHETIC_POS)
    private fun and(a: HirPAtom, b: HirPAtom) = HirPAnd(a, b, nid(), SYNTHETIC_POS)

    @Test
    fun renderFormatsAtomImpAndQuantifier() {
        val pc = mkProver()
        assertEquals("p", pc.render(atom("p")))
        assertEquals("p<x, y>", pc.render(atom("p", nm("x"), nm("y"))))
        assertEquals("(p -> q)", pc.render(imp(atom("p"), atom("q"))))
        assertEquals("∀x,y. p", pc.render(HirPForall(listOf("x", "y"), atom("p"), nid(), SYNTHETIC_POS)))
        assertEquals("¬<p>", pc.render(HirPNot(atom("p"), nid(), SYNTHETIC_POS)))   // P4 批次 3：渲染即源码（D2 同源口径）
        assertEquals("(p & q)", pc.render(and(atom("p"), atom("q"))))
    }

    @Test
    fun normalizeFactsSplitsConjunctionAndIff() {
        val p = atom("p"); val q = atom("q")
        val pc = mkProver()
        assertEquals(listOf(p, q), pc.normalizeFacts(listOf(and(p, q))))
        val iff = HirPIff(p, q, nid(), SYNTHETIC_POS)
        val out = pc.normalizeFacts(listOf(iff))
        assertEquals(2, out.size)
        assertTrue(out.all { it is HirPImp })
        // 双否消去：¬<¬<p>> → p
        assertEquals(listOf(p), pc.normalizeFacts(listOf(HirPNot(HirPNot(p, nid(), SYNTHETIC_POS), nid(), SYNTHETIC_POS))))
    }

    @Test
    fun groundTermsCollectsCompoundPostOrder() {
        val pc = mkProver()
        val call = HirTBin("-", nm("a"), i(1), nid(), SYNTHETIC_POS)
        val out = pc.groundTerms(listOf(atom(">", call, i(0))))
        assertTrue(out.any { it == nm("a") })
        assertTrue(out.any { it == i(1) })
        assertTrue(out.any { it == call })
    }

    @Test
    fun substDollarReplacesReturnValuePlaceholders() {
        val pc = mkProver()
        val post = atom(">", HirTDollar(nid(), SYNTHETIC_POS), i(0))
        val out = pc.substDollar(post, i(7))
        assertEquals("><7, 0>", pc.render(out))
    }

    @Test
    fun constAtomValueHandlesComparisonsAndRange() {
        val pc = mkProver()
        val rg = HirTBin("..", i(1), i(10), nid(), SYNTHETIC_POS)
        assertEquals(true, pc.constAtomValue(HirPAtom("in", listOf(i(5), rg), nid(), SYNTHETIC_POS)))
        assertEquals(false, pc.constAtomValue(HirPAtom("in", listOf(i(20), rg), nid(), SYNTHETIC_POS)))
        assertEquals(true, pc.constAtomValue(HirPAtom("<", listOf(i(1), i(2)), nid(), SYNTHETIC_POS)))
        assertEquals(false, pc.constAtomValue(HirPAtom("=", listOf(i(1), i(2)), nid(), SYNTHETIC_POS)))
        assertNull(pc.constAtomValue(HirPAtom("<", listOf(i(1), nm("n")), nid(), SYNTHETIC_POS)))
    }

    // ============================ 闭包（旧 Prover.closure 普通模式） ============================

    @Test
    fun closureAppliesModusPonens() {
        val pc = mkProver()
        val p = atom("p"); val q = atom("q")
        val out = pc.closure(listOf(p, imp(p, q)))
        assertTrue(out.any { it == q })
    }

    @Test
    fun closureAppliesModusTollens() {
        val pc = mkProver()
        val p = atom("p"); val q = atom("q")
        val out = pc.closure(listOf(HirPNot(q, nid(), SYNTHETIC_POS), imp(p, q)))
        assertTrue(out.any { it is HirPNot && it.p == p })
    }

    @Test
    fun closureDetectsContradiction() {
        val pc = mkProver()
        val p = atom("p")
        val out = pc.closure(listOf(p, HirPNot(p, nid(), SYNTHETIC_POS)))
        assertTrue(out.any { it is HirPBot })
    }

    @Test
    fun closureInstantiatesUniversalOverTerms() {
        val pc = mkProver()
        val fa = HirPForall(listOf("x"), atom("q", nm("x")), nid(), SYNTHETIC_POS)
        val a = nm("a")
        val out = pc.closure(listOf(fa), emptyList(), listOf(atom("r", a)))
        assertTrue(out.any { it is HirPAtom && it.head == "q" && it.terms.size == 1 && it.terms[0] == a })
    }

    // ============================ proves 通道顺序（P-7 对齐旧 proveOrDeep V1 子集） ============================

    @Test
    fun provesConstantAtomsDirectly() {
        val pc = mkProver()
        val goal = HirPAtom("=", listOf(i(1), i(1)), nid(), SYNTHETIC_POS)
        assertEquals("R-CONST", pc.proves(emptyList(), goal))
        assertNull(pc.proves(emptyList(), HirPAtom("=", listOf(i(1), i(2)), nid(), SYNTHETIC_POS)))
    }

    @Test
    fun provesArithmeticAbsoluteBound() {
        val pc = mkProver()
        val x = nm("x")
        val facts = listOf(HirPAtom("<=", listOf(x, i(5)), nid(), SYNTHETIC_POS))
        val goal = HirPAtom("<=", listOf(x, i(6)), nid(), SYNTHETIC_POS)
        assertEquals("A-LINARITH", pc.proves(facts, goal))
        assertNull(pc.proves(facts, HirPAtom("<=", listOf(x, i(4)), nid(), SYNTHETIC_POS)))
    }

    @Test
    fun provesArithmeticDifferenceEdge() {
        val pc = mkProver()
        val x = nm("x"); val y = nm("y")
        val facts = listOf(
            HirPAtom("<=", listOf(x, y), nid(), SYNTHETIC_POS),
            HirPAtom("<=", listOf(y, i(5)), nid(), SYNTHETIC_POS),
        )
        val goal = HirPAtom("<=", listOf(x, i(5)), nid(), SYNTHETIC_POS)
        assertEquals("A-LINARITH", pc.proves(facts, goal))
    }

    @Test
    fun provesArithmeticInRange() {
        val pc = mkProver()
        val n = nm("n")
        val facts = listOf(HirPAtom("in", listOf(n, HirTBin("..", i(1), i(10), nid(), SYNTHETIC_POS)), nid(), SYNTHETIC_POS))
        assertEquals("A-LINARITH", pc.proves(facts, HirPAtom("<=", listOf(n, i(10)), nid(), SYNTHETIC_POS)))
    }

    @Test
    fun provesArithmeticDefinitionChain() {
        val pc = mkProver()
        val d = nm("d"); val a = nm("a"); val b = nm("b")
        val facts = listOf(
            HirPAtom("=", listOf(d, HirTBin("-", a, b, nid(), SYNTHETIC_POS)), nid(), SYNTHETIC_POS),
            HirPAtom("=", listOf(a, i(5)), nid(), SYNTHETIC_POS),
            HirPAtom("=", listOf(b, i(3)), nid(), SYNTHETIC_POS),
        )
        assertEquals("A-LINARITH", pc.proves(facts, HirPAtom("=", listOf(d, i(2)), nid(), SYNTHETIC_POS)))
    }

    @Test
    fun provesArithmeticNeRefutedBounds() {
        val pc = mkProver()
        val x = nm("x")
        val facts = listOf(HirPAtom("=", listOf(x, i(1)), nid(), SYNTHETIC_POS))
        assertNotNull(pc.proves(facts, HirPAtom("!=", listOf(x, i(5)), nid(), SYNTHETIC_POS)))
    }

    @Test
    fun provesEqualityChainViaClosure() {
        val pc = mkProver()
        val x = nm("x"); val a = nm("a"); val b = nm("b")
        val facts = listOf(
            HirPAtom("=", listOf(x, a), nid(), SYNTHETIC_POS),
            HirPAtom("=", listOf(a, b), nid(), SYNTHETIC_POS),
        )
        assertNotNull(pc.proves(facts, HirPAtom("=", listOf(x, b), nid(), SYNTHETIC_POS)))
    }

    @Test
    fun provesSubstitutionChannelRewritesEquality() {
        val pc = mkProver()
        val x = nm("x"); val y = nm("y")
        val facts = listOf(
            HirPAtom("=", listOf(x, y), nid(), SYNTHETIC_POS),
            HirPAtom("=", listOf(y, i(5)), nid(), SYNTHETIC_POS),
        )
        val rule = pc.proves(facts, HirPAtom("=", listOf(x, i(5)), nid(), SYNTHETIC_POS))
        assertNotNull(rule)
    }

    @Test
    fun provesUnprovableReturnsNullSilently() {
        val pc = mkProver()
        assertNull(pc.proves(emptyList(), atom("q")))
        assertNull(pc.proves(listOf(atom("p")), atom("q")))
    }

    @Test
    fun provesImplicationByConstantDecomposition() {
        val pc = mkProver()
        val goal = imp(HirPAtom("=", listOf(i(1), i(2)), nid(), SYNTHETIC_POS), atom("q"))
        assertEquals("R-CONST", pc.proves(emptyList(), goal))   // 前件为假 → 蕴含平凡成立
        val goal2 = imp(HirPAtom("=", listOf(i(1), i(1)), nid(), SYNTHETIC_POS), atom("q"))
        assertNull(pc.proves(emptyList(), goal2))
    }

    // ============================ 义务核对（纯函数（上下文）→（verdict，诊断）） ============================

    @Test
    fun becauseProvenFromPremises() {
        val diag = DiagBag()
        val pc = ProofChecker({ seq++ }, diag)
        val p = atom("p"); val r = atom("r")
        val res = pc.because(listOf(p), r, listOf(p, imp(p, r)), SYNTHETIC_POS)
        assertEquals(HirProofStatus.PROVEN, res.verdict.status)
        assertEquals("R-MP", res.rule)
        assertTrue(diag.diags.any { it.code == "D-BTS" })
    }

    @Test
    fun becauseUnprovenPremiseFails() {
        val diag = DiagBag()
        val pc = ProofChecker({ seq++ }, diag)
        pc.because(listOf(atom("q")), atom("r"), listOf(atom("p")), SYNTHETIC_POS)
        assertTrue(diag.diags.any { it.code == "E-PREMISE-UNPROVEN" && it.severity == Severity.ERROR })
    }

    @Test
    fun becauseUnprovenConclusionFailsWithNoGuess() {
        val diag = DiagBag()
        val pc = ProofChecker({ seq++ }, diag)
        val res = pc.because(emptyList(), atom("r"), listOf(atom("p")), SYNTHETIC_POS)
        assertEquals(HirProofStatus.FAILED, res.verdict.status)
        assertEquals("E-CANNOT-PROVE", res.verdict.diagCode)
        assertTrue(diag.diags.any { it.code == "E-CANNOT-PROVE" })
    }

    @Test
    fun byStmtReportsRemovedInjection() {
        val diag = DiagBag()
        val pc = ProofChecker({ seq++ }, diag)
        val res = pc.byStmt(listOf(atom("q")), SYNTHETIC_POS)
        assertEquals(HirProofStatus.FAILED, res.status)
        assertTrue(diag.diags.any { it.code == "E-BY-REMOVED" && it.message.contains("axiom") })
    }

    @Test
    fun byAssertsVerifiedAgainstFacts() {
        val diag = DiagBag()
        val pc = ProofChecker({ seq++ }, diag)
        val p = atom("p")
        assertEquals(HirProofStatus.PROVEN, pc.byAsserts(listOf(p), listOf(p), SYNTHETIC_POS).status)
        val res2 = pc.byAsserts(listOf(atom("q")), listOf(p), SYNTHETIC_POS)
        assertEquals(HirProofStatus.FAILED, res2.status)
        assertTrue(diag.diags.any { it.code == "E-CANNOT-PROVE" })
    }

    @Test
    fun strategySimpUsesArithmeticChannel() {
        val diag = DiagBag()
        val pc = ProofChecker({ seq++ }, diag)
        val goal = HirPAtom("=", listOf(HirTBin("+", i(1), HirTBin("*", i(2), i(3), nid(), SYNTHETIC_POS), nid(), SYNTHETIC_POS), i(7)), nid(), SYNTHETIC_POS)
        assertEquals(HirProofStatus.PROVEN, pc.strategy("simp", goal, emptyList(), SYNTHETIC_POS).status)
        assertTrue(diag.diags.any { it.code == "D-STRATEGY" })
    }

    @Test
    fun strategyUnknownOrFailingFails() {
        val diag = DiagBag()
        val pc = ProofChecker({ seq++ }, diag)
        val res = pc.strategy("cute", atom("p"), emptyList(), SYNTHETIC_POS)
        assertEquals(HirProofStatus.FAILED, res.status)
        assertTrue(diag.diags.any { it.code == "E-STRATEGY-FAILED" })
    }

    @Test
    fun whenPropIntersectsArmConclusions() {
        val diag = DiagBag()
        val pc = ProofChecker({ seq++ }, diag)
        val disj = HirPOr(atom("p1"), atom("p2"), nid(), SYNTHETIC_POS)
        val r = atom("r"); val s = atom("s")
        val res = pc.whenProp(disj, listOf(listOf(r, s), listOf(r)), emptyList(), SYNTHETIC_POS)
        assertEquals(listOf(r), res.common)
        assertEquals(HirProofStatus.PROVEN, res.verdict.status)
        assertTrue(diag.diags.any { it.code == "D-WHEN-PROP" })
        // 无公共结论
        val res2 = pc.whenProp(disj, listOf(listOf(r), listOf(s)), emptyList(), SYNTHETIC_POS)
        assertEquals(HirProofStatus.FAILED, res2.verdict.status)
        assertTrue(diag.diags.any { it.code == "W-WHEN-NO-COMMON" })
        // 析取前提不在 Γ
        assertTrue(pc.whenProp(disj, emptyList(), emptyList(), SYNTHETIC_POS).verdict.status == HirProofStatus.FAILED)
        assertTrue(diag.diags.any { it.code == "W-PROP-UNBOUND" })
    }

    @Test
    fun ifPropRequiresClassicAndCommonConclusion() {
        val diag = DiagBag()
        val pc = ProofChecker({ seq++ }, diag)
        val p = atom("p"); val r = atom("r"); val s = atom("s")
        val res = pc.ifProp(p, listOf(r), listOf(r), classicMounted = true, pos = SYNTHETIC_POS)
        assertEquals(listOf(r), res.common)
        val res2 = pc.ifProp(p, listOf(r), listOf(s), classicMounted = true, pos = SYNTHETIC_POS)
        assertEquals(HirProofStatus.FAILED, res2.verdict.status)
        assertTrue(diag.diags.any { it.code == "E-IF-CONCLUSION-MISMATCH" })
        val res3 = pc.ifProp(p, listOf(r), listOf(r), classicMounted = false, pos = SYNTHETIC_POS)
        assertEquals(HirProofStatus.FAILED, res3.verdict.status)
        assertTrue(diag.diags.any { it.code == "E-NEED-CLASSIC" })
    }

    @Test
    fun existsIntroVerifiesWitnessInstance() {
        val diag = DiagBag()
        val pc = ProofChecker({ seq++ }, diag)
        val body = HirPAtom(">", listOf(nm("x"), i(0)), nid(), SYNTHETIC_POS)
        val res = pc.existsIntro(listOf("x"), listOf(i(1)), body, emptyList(), SYNTHETIC_POS)
        assertEquals(HirProofStatus.PROVEN, res.verdict.status)
        assertEquals(listOf("x"), res.existsProp.names, "注入的 ∃ 命题携带引入的约束名")
        assertEquals(body, res.existsProp.body, "注入的 ∃ 命题体子为原性质（结构相等）")
        val res2 = pc.existsIntro(listOf("x"), listOf(i(0)), body, emptyList(), SYNTHETIC_POS)
        assertEquals(HirProofStatus.FAILED, res2.verdict.status)
        assertTrue(diag.diags.any { it.code == "E-QUANT-UNPROVEN" })
    }

    @Test
    fun existsElimMatchesRenderedQuantifier() {
        val diag = DiagBag()
        val pc = ProofChecker({ seq++ }, diag)
        val body = HirPAtom(">", listOf(nm("x"), i(0)), nid(), SYNTHETIC_POS)
        val exists = HirPExists(listOf("x"), body, nid(), SYNTHETIC_POS)
        assertEquals(HirProofStatus.PROVEN, pc.existsElim("x", body, listOf(exists), SYNTHETIC_POS).status)
        val res2 = pc.existsElim("x", body, emptyList(), SYNTHETIC_POS)
        assertEquals(HirProofStatus.FAILED, res2.status)
        assertTrue(diag.diags.any { it.code == "E-PROP-UNBOUND" })
    }

    @Test
    fun localFunRequirementIsBody() {
        val diag = DiagBag()
        val pc = ProofChecker({ seq++ }, diag)
        assertEquals(HirProofStatus.PROVEN, pc.localFun("g", hasBody = true, pos = SYNTHETIC_POS).status)
        assertTrue(diag.diags.any { it.code == "D-LOCAL-FUN" })
        assertEquals(HirProofStatus.FAILED, pc.localFun("g", hasBody = false, pos = SYNTHETIC_POS).status)
        assertTrue(diag.diags.any { it.code == "E-QUANT-UNPROVEN" })
    }

    @Test
    fun postconditionChecksAndAxiomExemption() {
        val diag = DiagBag()
        val pc = ProofChecker({ seq++ }, diag)
        val n = nm("n")
        val facts = listOf(HirPAtom("<=", listOf(n, i(5)), nid(), SYNTHETIC_POS))
        val ok = pc.postcondition("f", listOf(HirPAtom("<=", listOf(n, i(6)), nid(), SYNTHETIC_POS)), facts, isAxiom = false, pos = SYNTHETIC_POS)
        assertEquals(HirProofStatus.PROVEN, ok.status)
        val bad = pc.postcondition("f", listOf(HirPAtom("<=", listOf(n, i(3)), nid(), SYNTHETIC_POS)), facts, isAxiom = false, pos = SYNTHETIC_POS)
        assertEquals(HirProofStatus.FAILED, bad.status)
        assertTrue(diag.diags.any { it.code == "E-POST-UNPROVEN" })
        val ax = pc.postcondition("f", listOf(atom("anything")), emptyList(), isAxiom = true, pos = SYNTHETIC_POS)
        assertEquals(HirProofStatus.PROVEN, ax.status)
        assertTrue(diag.diags.any { it.code == "D-AXIOM-POST" })
    }

    @Test
    fun snapshotNormalizesFacts() {
        val pc = mkProver()
        val p = atom("p"); val q = atom("q")
        val ctx = pc.snapshot(listOf(and(p, q)))
        assertEquals(listOf(p, q), ctx.facts)
    }

    // ============================ 结论上树（全管线，ADR-6 §四 4.1） ============================

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "proof.subl")
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

    private fun errCodes(bag: DiagBag) = bag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    private fun allCodes(bag: DiagBag) = bag.diags.map { it.code }
    private fun stmts(hir: HirFile) = hir.entries.mapNotNull { (it as? HirStmtEntry)?.stmt }
    private fun axioms(hir: HirFile) = stmts(hir).filterIsInstance<HirAxiom>()
    private fun becauses(hir: HirFile) = stmts(hir).filterIsInstance<HirBecause>()
    private fun strategies(hir: HirFile) = stmts(hir).filterIsInstance<HirStrategy>()
    private fun intro(hir: HirFile) = stmts(hir).filterIsInstance<HirExistsIntro>()
    private fun elim(hir: HirFile) = stmts(hir).filterIsInstance<HirExistsElim>()
    private fun bodyBecAuses(hir: HirFile): List<HirBecause> =
        hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? HirFunDecl }
            .flatMap { (it.body as? HirBlock)?.stmts ?: emptyList() }
            .filterIsInstance<HirBecause>()
    private fun proven(v: HirVerdict?) = v != null && v.status == HirProofStatus.PROVEN

    @Test
    fun axiomNodeCarriesRulingAndContext() {
        val (hir, bag) = script("axiom p")
        val a = axioms(hir).single()
        assertEquals("R-AXIOM", a.rule)
        assertTrue(proven(a.verdict))
        assertEquals(emptyList<HirPAtom>(), a.proofContext.facts)
        assertEquals(emptyList<String>(), errCodes(bag))
    }

    @Test
    fun btsNodeProvenFromAxiomFacts() {
        val (hir, bag) = script("axiom p\n∵ f ∴ p")
        val b = becauses(hir).single()
        assertTrue(proven(b.verdict))
        assertEquals("R-MP", b.rule)
        // proofContext 含义务时刻的 Γ（ axiom 已注入帧栈）
        assertTrue(b.proofContext.facts.any { it is HirPAtom && it.head == "p" })
        assertTrue(allCodes(bag).contains("D-BTS"))
        assertEquals(emptyList<String>(), errCodes(bag))
    }

    @Test
    fun btsPremiseUnprovenPipeline() {
        val (hir, bag) = script("axiom p\n∵ f, q ∴ p")
        assertEquals(listOf("E-PREMISE-UNPROVEN"), errCodes(bag))
        assertFalse(proven(becauses(hir).single().verdict))
    }

    @Test
    fun btsConclusionUnprovenPipeline() {
        val (hir, bag) = script("axiom p\n∵ f ∴ s")
        assertEquals(listOf("E-CANNOT-PROVE"), errCodes(bag))
        val v = becauses(hir).single().verdict
        assertTrue(v != null && v.status == HirProofStatus.FAILED && v.diagCode == "E-CANNOT-PROVE")
    }

    @Test
    fun byStmtPipelineReportsRemoval() {
        val (hir, bag) = script("by [q]")
        assertEquals(listOf("E-BY-REMOVED"), errCodes(bag))
    }

    @Test
    fun byAssertPipelineFlushedAtFrameEnd() {
        val (ok, bagOk) = script("axiom big\nvar x = 1 by [big]")
        assertEquals(emptyList<String>(), errCodes(bagOk))
        val (bad, bagBad) = script("var x = 1 by [q]")
        assertEquals(listOf("E-CANNOT-PROVE"), errCodes(bagBad))
    }

    @Test
    fun strategyPipelineSimpAndSubst() {
        val (ok, bagOk) = script("@simp[(3 * 2 + 6) = 12]")
        assertTrue(proven(strategies(ok).single().verdict))
        assertTrue(allCodes(bagOk).contains("D-STRATEGY"))
        val (bad, bagBad) = script("@simp[1 = 2]")
        assertEquals(listOf("E-STRATEGY-FAILED"), errCodes(bagBad))
        val (sub, bagSub) = script("var x = 0\nvar y = 0\naxiom x = y\naxiom y = 5\n@subst[x = 5]")
        assertTrue(proven(strategies(sub).single().verdict))
        assertEquals(emptyList<String>(), errCodes(bagSub))
    }

    @Test
    fun mpMtContraClosuresPipeline() {
        val (mp, bagMp) = script("axiom p\naxiom p → s\n∵ f ∴ s")
        assertTrue(proven(becauses(mp).single().verdict))
        assertEquals("R-MP", becauses(mp).single().rule)
        assertEquals(emptyList<String>(), errCodes(bagMp))
        val (mt, bagMt) = script("axiom ¬<s>\naxiom p → s\n∵ f ∴ ¬<p>")
        assertTrue(proven(becauses(mt).single().verdict))
        assertEquals("R-MT", becauses(mt).single().rule)
        val (contra, bagContra) = script("axiom p\naxiom ¬<p>\n∵ f ∴ q")
        assertTrue(proven(becauses(contra).single().verdict))
        assertEquals("R-CONTRA", becauses(contra).single().rule)
    }

    @Test
    fun whenPropPipeline() {
        val src = "axiom (p ∨ q)\naxiom r\nwhen[p ∨ q] { p -> { ∵ f ∴ r }  q -> { ∵ f ∴ r } }"
        val (hir, bag) = script(src)
        assertEquals(emptyList<String>(), errCodes(bag))
        assertTrue(allCodes(bag).contains("D-WHEN-PROP"))
        // 各支 BTS 结论取交集注入块外：后续 ∵ f ∴ r 不带前提也可证
        val (hir2, bag2) = script(src + "\n∵ g ∴ r")
        assertTrue(proven(becauses(hir2).last().verdict))
        // 无公共结论
        val (hir3, bag3) = script("axiom r\naxiom s\nwhen[p ∨ q] { p -> { ∵ f ∴ r }  q -> { ∵ f ∴ s } }")
        assertTrue(errCodes(bag3).isEmpty())
        assertTrue(allCodes(bag3).contains("W-WHEN-NO-COMMON"))
        // 析取前提不在 Γ
        val (hir4, bag4) = script("when[p ∨ q] { p -> { ∵ f ∴ r }  q -> { ∵ f ∴ s } }")
        assertTrue(allCodes(bag4).contains("W-PROP-UNBOUND"))
    }

    @Test
    fun ifPropPipeline() {
        val (noClassic, bagN) = script("if[p] { axiom r } else { axiom r }")
        assertTrue(allCodes(bagN).contains("E-NEED-CLASSIC"))
        val ok = "@classic fun f(): Nat = 1\naxiom r\nif[p] { ∵ f ∴ r } else { ∵ f ∴ r }"
        val (hir, bag) = script(ok)
        assertEquals(emptyList<String>(), errCodes(bag))
        // 排中律结论 r 注入块外
        val (hir2, bag2) = script(ok + "\n∵ g ∴ r")
        assertTrue(proven(becauses(hir2).last().verdict))
        val (mism, bagM) = script("@classic fun f(): Nat = 1\naxiom r\naxiom s\nif[p] { ∵ f ∴ r } else { ∵ f ∴ s }")
        assertTrue(errCodes(bagM).contains("E-IF-CONCLUSION-MISMATCH"))
    }

    @Test
    fun quantifierExistsPipeline() {
        val (ok, bagOk) = script("∃ x = (1) . x > 0")
        assertTrue(proven(intro(ok).single().verdict))
        assertEquals(emptyList<String>(), errCodes(bagOk))
        assertTrue(allCodes(bagOk).contains("D-EXIST-INTRO"))
        val (bad, bagBad) = script("∃ x = (0) . x > 0")
        assertEquals(listOf("E-QUANT-UNPROVEN"), errCodes(bagBad))
        val (el, bagEl) = script("∃ x = (1) . x = 1\n∃ x . x = 1 { var t = 1 }")
        assertTrue(proven(elim(el).single().verdict))
        assertEquals("R-EXISTS-MATCH", elim(el).single().rule)
        assertEquals(emptyList<String>(), errCodes(bagEl))
        val (elBad, bagElBad) = script("∃ x . x = 1 { var t = 1 }")
        assertTrue(allCodes(bagElBad).contains("E-PROP-UNBOUND"))
    }

    @Test
    fun localFunPipelineInBlock() {
        val (hir, bag) = script("fun outer(): Nat {\n    fun g(): Nat = 1\n    return 1\n}")
        assertEquals(emptyList<String>(), errCodes(bag))
        assertTrue(allCodes(bag).contains("D-LOCAL-FUN"))
    }

    @Test
    fun postconditionPipeline() {
        val (ok, bagOk) = script("fun f(): Nat < $ > 0 > = 1")
        assertEquals(emptyList<String>(), errCodes(bagOk))
        val (bad, bagBad) = script("fun f(): Nat < $ > 100 > = 1")
        assertEquals(listOf("E-POST-UNPROVEN"), errCodes(bagBad))
        val (ax, bagAx) = script("@axiom fun f(): Nat < $ > 100 > = 1")
        assertEquals(emptyList<String>(), errCodes(bagAx))
        assertTrue(allCodes(bagAx).contains("D-AXIOM-POST"))
    }

    @Test
    fun prepsInjectedIntoFunctionFrame() {
        val (hir, bag) = script("fun f(a: Int)<\$1 > 0>: Int {\n    ∵ f ∴ a > 0\n    return a\n}")
        assertEquals(emptyList<String>(), errCodes(bag))
        assertTrue(proven(bodyBecAuses(hir).single().verdict))
    }

    @Test
    fun factFrameIsScopedPerFunction() {
        val (hir, bag) = script("fun f(): Nat {\n    axiom p\n    return 1\n}\n∵ g ∴ p")
        // p 在函数帧内：块外不可见 → E-CANNOT-PROVE（帧栈弹出，Γ 不串扰）
        assertTrue(errCodes(bag).contains("E-CANNOT-PROVE"))
        val (hir2, bag2) = script("fun f(): Nat {\n    axiom p\n    ∵ g ∴ p\n    return 1\n}")
        assertEquals(emptyList<String>(), errCodes(bag2))
    }

    @Test
    fun arithmeticPipelineChannels() {
        val (diff, bagD) = script("var x = 0\nvar y = 0\naxiom x ≤ y\naxiom y ≤ 5\n∵ f ∴ x ≤ 5")
        assertTrue(proven(becauses(diff).single().verdict))
        assertEquals("A-LINARITH", becauses(diff).single().rule)
        val (rg, bagR) = script("var n = 3\naxiom n in 1..10\n∵ f ∴ n ≤ 10")
        assertTrue(proven(becauses(rg).single().verdict))
        val (cf, bagC) = script("∵ f ∴ (1 + (2 * 3)) = 7")
        assertTrue(proven(becauses(cf).single().verdict))
        val (dc, bagDc) = script("var d = 0\nvar a = 5\nvar b = 3\naxiom d = (a - b)\naxiom a = 5\naxiom b = 3\n∵ f ∴ d = 2")
        assertTrue(proven(becauses(dc).single().verdict))
        val (eq, bagEq) = script("var p = 0\nvar q = 0\naxiom p = q\naxiom q = 5\n∵ f ∴ p = 5")
        assertTrue(proven(becauses(eq).single().verdict))
    }
}
