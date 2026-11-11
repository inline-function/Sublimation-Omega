package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.ident
import sublimation.frontend.diag.NodeId
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.hir.HirPAnd
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirPBot
import sublimation.frontend.hir.HirPExists
import sublimation.frontend.hir.HirPExists1
import sublimation.frontend.hir.HirPIff
import sublimation.frontend.hir.HirPImp
import sublimation.frontend.hir.HirPForall
import sublimation.frontend.hir.HirPNot
import sublimation.frontend.hir.HirPOr
import sublimation.frontend.hir.HirPTop
import sublimation.frontend.hir.HirProp
import sublimation.frontend.hir.HirProofContext
import sublimation.frontend.hir.HirProofStatus
import sublimation.frontend.hir.HirTBin
import sublimation.frontend.hir.HirTBot
import sublimation.frontend.hir.HirTCall
import sublimation.frontend.hir.HirTDollar
import sublimation.frontend.hir.HirTField
import sublimation.frontend.hir.HirTInst
import sublimation.frontend.hir.HirTInt
import sublimation.frontend.hir.HirTName
import sublimation.frontend.hir.HirTParamRef
import sublimation.frontend.hir.HirTStr
import sublimation.frontend.hir.HirTSymbolCall
import sublimation.frontend.hir.HirTTop
import sublimation.frontend.hir.HirTUni
import sublimation.frontend.hir.HirTerm
import sublimation.frontend.hir.HirVerdict
import sublimation.frontend.hir.SymbolRef
import java.lang.Math.floorDiv

/**
 * 命题与证明检查 V1（t9；ADR-6 §四 4.2/4.3 插入件）：
 *
 * - **纯函数语义**：本类不遍历树、不持有帧——签名形如「命题 + 上下文 → 结论 + 诊断」，
 *   由 Elaborator 构造到该节点时调用；Γ 帧栈与事实注入在 Elaborator 侧（ADR-6 §四 4.4）。
 * - **命题代数归此**：旧 PropLogic 可判定片段主干（normalizeFacts/groundTerms/subst/match/
 *   render）从 AST 内嵌方法迁移至命题面（HirProp/HirTerm，P-STRUCT-EQ 显式结构相等替代
 *   termEq/propStructEq/propHash 三件套——identity 直用 Set<HirProp>）。
 * - **证明通道（proveOrDeep 的 V1 子集，P-7 对齐）**：常量拆解 → 常量原子真值 → 线性算术
 *   （ArithProve 整体移植：Lin 常量折叠、单变量绝对界、±1 差分图 Bellman–Ford、等式链定义
 *   消元、in 区间、≠ 目标界排除、splitNe 案拆）→ 莱布尼茨代换（substBfs）→ MP 闭包
 *   （饱和：∀ 单名/多名实例化 + MP + MT + 矛盾→⊥ + 收割生长封顶 256）。
 * - **义务核对**：axiom / ∵∴ / by（语句式 E-BY-REMOVED + 后缀式断言队列）/ @simp·@subst 策略 /
 *   命题 when / 命题 if / ∃ 引入·消去 / 局部函数 ∀ 引入 / 函数下文 posts——
 *   结论落 HirProofObligation 三件套（proofContext 快照 + rule + verdict），失败恒落一条
 *   ERROR 诊断（R-NO-GUESS：Γ ⊬ P 绝不降级为成立）。
 * - **V1 推迟**（载 ADR-13 §三）：定理空间搜索（@theorem 匹配）、激进会话（三维池/不动点/
 *   迭代加深/∨ 消去/反证法）、Diffusion 调用点下文注入、自定义命题展开（PropDefDecl）、
 *   类型判定 (e : T) 与 Real 代数通道、∃! 唯一性、enumGroundMembership 容器枚举。
 */
/**
 * t109（R3-V1 §四 P2）:证明器判据 oracle + 预算注入接口。ProofChecker 自带零实现 [NoOracle]——
 * Elaborator 注入实现体后通道才激活;零实现下所有段经此返回 null（既有五段行为不变）。
 */
internal fun substTermAlloc(t: HirTerm, sub: Map<String, HirTerm>, alloc: () -> NodeId): HirTerm = when (t) {
    is HirTName -> sub[t.name] ?: t
    is HirTBin -> HirTBin(t.op, substTermAlloc(t.left, sub, alloc), substTermAlloc(t.right, sub, alloc), alloc(), SYNTHETIC_POS)
    is HirTUni -> HirTUni(t.op, substTermAlloc(t.operand, sub, alloc), alloc(), SYNTHETIC_POS)
    is HirTCall -> HirTCall(substTermAlloc(t.callee, sub, alloc), t.args.map { substTermAlloc(it, sub, alloc) }, alloc(), SYNTHETIC_POS)
    is HirTInst -> HirTInst(substTermAlloc(t.target, sub, alloc), t.terms.map { substTermAlloc(it, sub, alloc) }, alloc(), SYNTHETIC_POS)
    is HirTField -> HirTField(substTermAlloc(t.target, sub, alloc), t.name, alloc(), SYNTHETIC_POS)
    is HirTSymbolCall -> HirTSymbolCall(t.op, t.args.map { substTermAlloc(it, sub, alloc) }, alloc(), SYNTHETIC_POS)
    else -> t
}

internal interface ProverOracle {
    /** A-SUBTYPE:类型项判 `<:`(算法判据本体:TypeInfer.subtype,ADR-39 唯一判据经此调用不旁路）。 */
    fun judgeSubtype(lo: sublimation.frontend.hir.HirType, hi: sublimation.frontend.hir.HirType): Boolean
    /** (e:T) 通道:项 → 类型(HirTName 查符号表登记型;字面量见 [fitsLiteral]);null = 不可判定。 */
    fun termType(t: sublimation.frontend.hir.HirTerm): sublimation.frontend.hir.HirType?
    /** (e:T) 字面量项的数值判定:Nat 需值 ≥ 0,Int/Rat 接整数（数值阶层 rank 0/1/2 同口径）。 */
    fun fitsLiteral(value: String, t: sublimation.frontend.hir.HirType): Boolean
    /** A-DEFRULE 定义性归约:单表达式纯函数 `name(args)` → 形参代换体;null = 不合规则（无深归约展开）。 */
    fun definitionalExpand(name: String, args: List<sublimation.frontend.hir.HirTerm>): sublimation.frontend.hir.HirTerm?
    /** PROOF_BUDGET:消费 n 单位;false = 耗尽（源序确定性:单线程声明序遍历,同输入同消耗同停）。 */
    fun spend(n: Int = 1): Boolean
    /** 通道足迹记录（耗尽/失败诊断的可观测原料）。 */
    fun trace(channel: String)
    /** 足迹快照（诊断文案用）。 */
    fun snapshotTraces(): List<String>
    /** 预算容量/已耗标签（诊断文案用）。 */
    fun budgetLabel(): String
    /** 预算耗尽诊断是否已报（去重:每编译单元一次,D-PROOF-BUDGET）。 */
    fun budgetReported(): Boolean
    /** 标记预算耗尽诊断已报。 */
    fun markBudgetReported()
}

/** 零实现:通道未注入时保持既有五段行为（A-SUBTYPE/A-TYPEOF/A-DEFRULE 段返回 null,预算恒足）。 */
internal object NoOracle : ProverOracle {
    override fun judgeSubtype(lo: sublimation.frontend.hir.HirType, hi: sublimation.frontend.hir.HirType) = false
    override fun termType(t: sublimation.frontend.hir.HirTerm): sublimation.frontend.hir.HirType? = null
    override fun fitsLiteral(value: String, t: sublimation.frontend.hir.HirType) = false
    override fun definitionalExpand(name: String, args: List<sublimation.frontend.hir.HirTerm>) = null
    override fun spend(n: Int) = true
    override fun trace(channel: String) {}
    override fun snapshotTraces(): List<String> = emptyList()
    override fun budgetLabel(): String = "未注入"
    override fun budgetReported() = false
    override fun markBudgetReported() {}
}

internal class ProofChecker(
    private val alloc: () -> NodeId,
    private val diag: DiagBag,
    /**
     * t109（R3-V1 §四 P2,C-2 提前;R-11/R-12 裁决）:判据 oracle + PROOF_BUDGET 注入。
     *
     * - **A-SUBTYPE**（裁决 1）:原子 `<:` 命中委托 [ProverOracle.judgeSubtype]——TypeInfer.subtype
     *   算法判据本体经此调用,**不旁路 ADR-39 唯一判据**;
     * - **A-TYPEOF**（裁决 2,(e:T) 通道）:项的类型判定,先行 **A-DEFRULE** 定义性归约（裁决 3,
     *   单表达式纯函数 ∀x. f(x)=body 重写,P-12 裁决 B 范围）;
     * - **PROOF_BUDGET**（裁决 4/OQ-2）:预算界限替代 R 轮硬断——耗经=可观测形态（通道足迹落诊断）,
     *   证不出回落兜底或硬拒,**不发明新放行**;
     * - 置换原则（R-12 收为正式规则）:证明出仅替代**已有独立算法支撑**的判定,proof:* 位必可查算法兜底。
     */
    private val oracle: ProverOracle = NoOracle,
) {
    private fun nid(): NodeId = alloc()

    /** 项级代换（文件级 [substTermAlloc] 的类内包装:沿本类节点分配）。 */
    private fun substTerm(t: HirTerm, sub: Map<String, HirTerm>): HirTerm =
        substTermAlloc(t, sub, ::nid)

    // ============================ 命题代数（旧 PropLogic 可判定片段）============================

    /** 诊断/去重键用的结构渲染（P-7：忽略 nid/origin/notes 的结构标签）。 */
    fun render(p: HirProp): String = when (p) {
        is HirPTop -> "⊤"
        is HirPBot -> "⊥"
        is HirPNot -> "¬<" + render(p.p) + ">"
        is HirPAnd -> "(" + render(p.a) + " & " + render(p.b) + ")"
        is HirPOr -> "(" + render(p.a) + " | " + render(p.b) + ")"
        is HirPImp -> "(" + render(p.a) + " -> " + render(p.b) + ")"
        is HirPIff -> "(" + render(p.a) + " <-> " + render(p.b) + ")"
        is HirPForall -> "∀" + p.names.joinToString(",") + ". " + render(p.body)
        is HirPExists -> "∃" + p.names.joinToString(",") + ". " + render(p.body)
        is HirPExists1 -> "∃!" + p.names.joinToString(",") + ". " + render(p.body)
        is HirPAtom -> if (p.terms.isEmpty()) p.head else p.head + "<" + p.terms.joinToString(", ") { termTag(it) } + ">"
    }

    fun termTag(t: HirTerm): String = when (t) {
        is HirTInt -> t.value
        is HirTStr -> "\"" + t.value + "\""
        is HirTName -> t.name
        is HirTDollar -> "$"
        is HirTParamRef -> "$" + t.index
        is HirTTop -> "⊤"
        is HirTBot -> "⊥"
        is HirTBin -> "(" + t.op + " " + termTag(t.left) + " " + termTag(t.right) + ")"
        is HirTUni -> "(" + t.op + " " + termTag(t.operand) + ")"
        is HirTCall -> termTag(t.callee) + "(" + t.args.joinToString(",") { termTag(it) } + ")"
        is HirTInst -> termTag(t.target) + "<" + t.terms.joinToString(",") { termTag(it) } + ">"
        is HirTField -> termTag(t.target) + "." + t.name
        is HirTSymbolCall -> t.op + "(" + t.args.joinToString(",") { termTag(it) } + ")"
    }

    /** 事实库归一化：合取分解 + 等价拆双蕴含 + 双否消去（定理表的可判定子集）。 */
    fun normalizeFacts(ps: List<HirProp>): List<HirProp> =
        ps.flatMap { expandIff(it) }.flatMap { flattenAnd(it) }.map { stripNN(it) }

    private fun expandIff(p: HirProp): List<HirProp> = when (p) {
        is HirPIff -> listOf(HirPImp(p.a, p.b, nid(), SYNTHETIC_POS), HirPImp(p.b, p.a, nid(), SYNTHETIC_POS))
        else -> listOf(p)
    }

    private fun flattenAnd(p: HirProp): List<HirProp> = when (p) {
        is HirPAnd -> flattenAnd(p.a) + flattenAnd(p.b)
        else -> listOf(p)
    }

    fun stripNN(p: HirProp): HirProp = when (p) {
        is HirPNot -> { val inner = stripNN(p.p); if (inner is HirPNot) inner.p else p }
        else -> p
    }

    /** Γ 中收集所有项（∀ 实例化 / ∃ 见证的候选，有限集保证饱和终止）。后序：内层先入。 */
    fun groundTerms(facts: List<HirProp>): List<HirTerm> {
        val out = LinkedHashSet<HirTerm>()
        fun collectTerm(e: HirTerm) {
            when (e) {
                is HirTBin -> { collectTerm(e.left); collectTerm(e.right) }
                is HirTUni -> collectTerm(e.operand)
                is HirTCall -> { collectTerm(e.callee); e.args.forEach { collectTerm(it) } }
                is HirTInst -> { collectTerm(e.target); e.terms.forEach { collectTerm(it) } }
                is HirTField -> collectTerm(e.target)
                is HirTSymbolCall -> e.args.forEach { collectTerm(it) }
                else -> {}
            }
            out.add(e)
        }
        fun collectProp(p: HirProp) {
            when (p) {
                is HirPAtom -> p.terms.forEach { collectTerm(it) }
                is HirPNot -> collectProp(p.p)
                is HirPAnd -> { collectProp(p.a); collectProp(p.b) }
                is HirPOr -> { collectProp(p.a); collectProp(p.b) }
                is HirPImp -> { collectProp(p.a); collectProp(p.b) }
                is HirPIff -> { collectProp(p.a); collectProp(p.b) }
                is HirPForall -> collectProp(p.body)
                is HirPExists -> collectProp(p.body)
                is HirPExists1 -> collectProp(p.body)
                else -> {}
            }
        }
        facts.forEach { collectProp(it) }
        return out.toList()
    }

    /** 项替换（wilds：量词约束名）。 */

    private fun substProp(p: HirProp, sub: Map<String, HirTerm>): HirProp = when (p) {
        is HirPAtom -> HirPAtom(p.head, p.terms.map { substTerm(it, sub) }, nid(), SYNTHETIC_POS)
        is HirPNot -> HirPNot(substProp(p.p, sub), nid(), SYNTHETIC_POS)
        is HirPAnd -> HirPAnd(substProp(p.a, sub), substProp(p.b, sub), nid(), SYNTHETIC_POS)
        is HirPOr -> HirPOr(substProp(p.a, sub), substProp(p.b, sub), nid(), SYNTHETIC_POS)
        is HirPImp -> HirPImp(substProp(p.a, sub), substProp(p.b, sub), nid(), SYNTHETIC_POS)
        is HirPIff -> HirPIff(substProp(p.a, sub), substProp(p.b, sub), nid(), SYNTHETIC_POS)
        is HirPForall -> HirPForall(p.names, substProp(p.body, sub.filterKeys { it !in p.names }), nid(), SYNTHETIC_POS)
        is HirPExists -> HirPExists(p.names, substProp(p.body, sub.filterKeys { it !in p.names }), nid(), SYNTHETIC_POS)
        is HirPExists1 -> HirPExists1(p.names, substProp(p.body, sub.filterKeys { it !in p.names }), nid(), SYNTHETIC_POS)
        else -> p
    }

    /** 模式匹配（合一的单向特化）：wilds 中的名字是通配，首次出现绑定、之后要求一致。 */
    private fun matchTerm(pattern: HirTerm, actual: HirTerm, wilds: Set<String>, binds: MutableMap<String, HirTerm>): Boolean =
        when {
            pattern is HirTName && pattern.name in wilds -> {
                val prev = binds[pattern.name]
                if (prev == null) { binds[pattern.name] = actual; true } else prev == actual
            }
            pattern is HirTBin && actual is HirTBin -> pattern.op == actual.op &&
                matchTerm(pattern.left, actual.left, wilds, binds) &&
                matchTerm(pattern.right, actual.right, wilds, binds)
            pattern is HirTCall && actual is HirTCall -> pattern.args.size == actual.args.size &&
                matchTerm(pattern.callee, actual.callee, wilds, binds) &&
                pattern.args.zip(actual.args).all { matchTerm(it.first, it.second, wilds, binds) }
            pattern is HirTInst && actual is HirTInst -> pattern.terms.size == actual.terms.size &&
                matchTerm(pattern.target, actual.target, wilds, binds) &&
                pattern.terms.zip(actual.terms).all { matchTerm(it.first, it.second, wilds, binds) }
            pattern is HirTField && actual is HirTField ->
                pattern.name == actual.name && matchTerm(pattern.target, actual.target, wilds, binds)
            pattern is HirTUni && actual is HirTUni ->
                pattern.op == actual.op && matchTerm(pattern.operand, actual.operand, wilds, binds)
            pattern is HirTSymbolCall && actual is HirTSymbolCall -> pattern.op == actual.op &&
                pattern.args.size == actual.args.size &&
                pattern.args.zip(actual.args).all { matchTerm(it.first, it.second, wilds, binds) }
            else -> pattern == actual
        }

    /** $ 替换：体内 ≡ 末表达式（下文 posts 的 $ 代换，旧 substDollar）。 */
    fun substDollar(p: HirProp, ret: HirTerm): HirProp = when (p) {
        is HirPAtom -> HirPAtom(p.head, p.terms.map { substTermDollar(it, ret) }, nid(), SYNTHETIC_POS)
        is HirPNot -> HirPNot(substDollar(p.p, ret), nid(), SYNTHETIC_POS)
        is HirPAnd -> HirPAnd(substDollar(p.a, ret), substDollar(p.b, ret), nid(), SYNTHETIC_POS)
        is HirPOr -> HirPOr(substDollar(p.a, ret), substDollar(p.b, ret), nid(), SYNTHETIC_POS)
        is HirPImp -> HirPImp(substDollar(p.a, ret), substDollar(p.b, ret), nid(), SYNTHETIC_POS)
        is HirPIff -> HirPIff(substDollar(p.a, ret), substDollar(p.b, ret), nid(), SYNTHETIC_POS)
        is HirPForall -> HirPForall(p.names, substDollar(p.body, ret), nid(), SYNTHETIC_POS)
        is HirPExists -> HirPExists(p.names, substDollar(p.body, ret), nid(), SYNTHETIC_POS)
        is HirPExists1 -> HirPExists1(p.names, substDollar(p.body, ret), nid(), SYNTHETIC_POS)
        else -> p
    }

    private fun substTermDollar(t: HirTerm, ret: HirTerm): HirTerm = when (t) {
        is HirTDollar -> ret
        is HirTBin -> HirTBin(t.op, substTermDollar(t.left, ret), substTermDollar(t.right, ret), nid(), SYNTHETIC_POS)
        is HirTUni -> HirTUni(t.op, substTermDollar(t.operand, ret), nid(), SYNTHETIC_POS)
        is HirTCall -> HirTCall(substTermDollar(t.callee, ret), t.args.map { substTermDollar(it, ret) }, nid(), SYNTHETIC_POS)
        is HirTInst -> HirTInst(substTermDollar(t.target, ret), t.terms.map { substTermDollar(it, ret) }, nid(), SYNTHETIC_POS)
        is HirTField -> HirTField(substTermDollar(t.target, ret), t.name, nid(), SYNTHETIC_POS)
        is HirTSymbolCall -> HirTSymbolCall(t.op, t.args.map { substTermDollar(it, ret) }, nid(), SYNTHETIC_POS)
        else -> t
    }

    /** 常量原子命题真值（in 区间分支先行——区间不是 IntLit，先 as? IntLit 会吞掉区间）。 */
    fun constAtomValue(p: HirProp): Boolean? {
        if (p !is HirPAtom || p.terms.size != 2) return null
        val a = (p.terms[0] as? HirTInt)?.value?.toLongOrNull() ?: return null
        if (p.head == "in" || p.head == "∈") {
            val rg = p.terms[1]
            if (rg !is HirTBin || rg.op != "..") return null
            val lo = (rg.left as? HirTInt)?.value?.toLongOrNull() ?: return null
            val hi = (rg.right as? HirTInt)?.value?.toLongOrNull() ?: return null
            return a in lo..hi
        }
        val b = (p.terms[1] as? HirTInt)?.value?.toLongOrNull() ?: return null
        return when (p.head) {
            "=", "==" -> a == b
            "!=", "≠" -> a != b
            "<" -> a < b
            ">" -> a > b
            "<=", "≤" -> a <= b
            ">=", "≥" -> a >= b
            else -> null
        }
    }

    // ============================ 证明通道（proveOrDeep 的 V1 子集）============================

    /**
     * 通道顺序（P27 对齐，命中即短路、未命中续走）：
     * PNot/PImp/PAnd 常量拆解 → 常量原子真值 → 线性算术 → 莱布尼茨代换 → MP 闭包。
     * 返回命中规则编号（R-CONST / A-LINARITH / R-SUBST / R-MP / R-MT / R-CONTRA），null = 未证出。
     */
    fun proves(facts: List<HirProp>, goal: HirProp): String? {
        if (goal is HirPNot) {
            val iv = constAtomValue(goal.p)
            if (iv == false) return "R-CONST"
            if (iv == true) return null
        }
        if (goal is HirPImp) {
            val av = constAtomValue(goal.a)
            if (av == false) return "R-CONST"
            if (av == true) return proves(facts, goal.b)
        }
        if (goal is HirPAnd) {
            val ra = proves(facts, goal.a) ?: return null
            val rb = proves(facts, goal.b) ?: return null
            return ra
        }
        if (constAtomValue(goal) == true) return "R-CONST"
        if (arith.proves(facts, goal, neLimit = 2) == true) return "A-LINARITH"
        if (substBfs(goal, facts, maxSteps = 40, maxNodes = 100)) return "R-SUBST"
        // t109（R3-V1 §四 P2,R-11/R-12 裁决）:第六段通道——定义性归约 A-DEFRULE → (e:T) 判定 A-TYPEOF →
        // A-SUBTYPE 判据 oracle。判据 oracle 即**算法兜底本体**:证出仅替代已有独立算法支撑的判定（置换原则,
        // R-12 收为正式可测规则）;证不出落 closureProve（用户公理段）或回落兜底/硬拒——不发明新放行。
        if (goal is HirPAtom && goal.terms.size == 2) {
            val p2 = proveAtomChannel(goal)
            if (p2 != null) return p2
        }
        return closureProve(facts, goal)
    }

    /**
     * t109 P2 段调度:命题原子 `<:`(A-SUBTYPE)/`:`((e:T)) 两族合成通道。
     * 预算先行消费(1 单位/次尝试;耗尽 → D-PROOF-BUDGET 可观测形态,返回 null=失败,不发明放行）。
     */
    private fun proveAtomChannel(goal: HirPAtom): String? {
        if (!oracle.spend()) { reportBudgetExhausted(goal.origin); return null }
        return when (goal.head) {
            "<:" -> {
                oracle.trace("A-SUBTYPE-oracle")
                val lo = oracle.termType(goal.terms[0]) ?: return null
                val hi = oracle.termType(goal.terms[1]) ?: return null
                if (oracle.judgeSubtype(lo, hi)) "A-SUBTYPE" else null
            }
            ":" -> {
                oracle.trace("A-TYPEOF")
                val t = oracle.termType(goal.terms[1]) ?: return null
                val reduced = definitionalReduce(goal.terms[0], 0)
                val e = reduced ?: goal.terms[0]
                if (typeOfJudged(e, t)) (if (reduced != null) "A-DEFRULE" else "A-TYPEOF") else null
            }
            else -> null
        }
    }

    /** (e:T) 判定:字面量走数值阶层,其余项查登记型后判 <:（算法判据经 oracle,不旁路）。 */
    private fun typeOfJudged(e: HirTerm, t: sublimation.frontend.hir.HirType): Boolean {
        if (e is HirTInt) return oracle.fitsLiteral(e.value, t)
        val et = oracle.termType(e) ?: return false
        return oracle.judgeSubtype(et, t)
    }

    /**
     * A-DEFRULE 定义性归约（裁决 3;P-12 裁决 B 范围）:单表达式纯函数调用 `f(args)` 代换为体（形参→实参）。
     * 深度上界 DEFRULE_DEPTH_CAP=4 防深归约无终止展开（P-12 §4.4 无终止依据不展开）;每步消费预算;
     * 体内嵌套调用与二元项递归归约（无可归约子项 → null,保留原项）。
     */
    private fun definitionalReduce(t: HirTerm, depth: Int): HirTerm? {
        if (depth > DEFRULE_DEPTH_CAP) return null
        val name = (t as? HirTCall)?.callee as? HirTName
        if (name != null) {
            val expanded = oracle.definitionalExpand(name.name, (t as HirTCall).args)
            if (expanded != null) {
                oracle.trace("A-DEFRULE")
                if (!oracle.spend()) { reportBudgetExhausted(t.origin); return null }
                return definitionalReduce(expanded, depth + 1) ?: expanded   // 展开体无可再归约位时返回展开体（null=原位未动）
            }
        }
        return reduceSubterms(t, depth)
    }

    private fun reduceSubterms(t: HirTerm, depth: Int): HirTerm? = when (t) {
        is HirTBin -> {
            val l = definitionalReduce(t.left, depth)
            val r = definitionalReduce(t.right, depth)
            if (l == null && r == null) null
            else HirTBin(t.op, l ?: t.left, r ?: t.right, 0, t.origin)
        }
        is HirTCall -> {
            val subs = t.args.map { definitionalReduce(it, depth) }
            if (subs.all { it == null }) null
            else HirTCall(t.callee, t.args.zip(subs).map { p -> p.second ?: p.first }, 0, t.origin)
        }
        else -> null
    }

    /**
     * PROOF_BUDGET 耗尽落诊断（每编译单元去重一次;OQ-2:耗尽=可观测形态——通道足迹 + 预算余量,
     * 义务回落既有兜底;预算是执行界限不是语义围栏）。
     */
    private fun reportBudgetExhausted(pos: SourceRange) {
        if (oracle.budgetReported()) return
        oracle.markBudgetReported()
        diag.supplement("D-PROOF-BUDGET", pos,
            "证明预算 " + oracle.budgetLabel() + " 耗尽——通道足迹:" +
            (oracle.snapshotTraces().ifEmpty { listOf("（未留痕）") }).joinToString(" → ") +
            "。预算是执行界限而非语义围栏:本义务回落既有兜底（算法判据/硬拒）,证明通道不发明放行（OQ-2）")
    }

    private val DEFRULE_DEPTH_CAP = 4


    // ============================ MP 闭包（旧 Prover.closure 普通模式）============================

    private val maxRounds = 114514
    private val maxHarvest = 256

    /** 饱和闭包：合取分解 + ∀ 实例化（候选项 = Γ 现有项 ∪ 目标项，收割生长封顶）+ MP + MT + 矛盾→⊥。 */
    fun closure(gamma: List<HirProp>, extraGoals: List<HirProp> = emptyList(), termSource: List<HirProp> = gamma): List<HirProp> {
        val facts = normalizeFacts(gamma).toMutableSet()
        val extraTerms = extraGoals.flatMap { groundTerms(listOf(it)) }
        var keyTerms = (groundTerms(termSource) + extraTerms).distinctBy { termTag(it) }
        fun buildSubs(terms: List<HirTerm>): List<Pair<HirPForall, List<List<HirTerm>>>> =
            facts.filterIsInstance<HirPForall>().mapNotNull { fa ->
                if (fa.names.isEmpty()) null else fa to when {
                    fa.names.size == 1 -> terms.map { listOf(it) }
                    else -> {
                        var acc: List<List<HirTerm>> = listOf(emptyList())
                        repeat(fa.names.size) { acc = acc.flatMap { comb -> terms.map { comb + it } } }
                        acc
                    }
                }
            }
        var forallSubs = buildSubs(keyTerms)
        val seenSubs = HashSet<String>()
        val harvested = HashSet<HirProp>()
        for (round in 0 until maxRounds) {
            // t109 OQ-2:饱和轮消费 PROOF_BUDGET——同池预算替代 R 轮硬断;耗尽即停（回落兜底/硬拒,不发明放行）
            if (!oracle.spend()) break
            var changed = false
            for ((fa, combs) in forallSubs) {
                for (comb in combs) {
                    val key = fa.names.joinToString("|") + "|" + comb.joinToString("|") { termTag(it) }
                    if (!seenSubs.add(key)) continue
                    val sub = fa.names.zip(comb).toMap()
                    normalizeFacts(listOf(substProp(fa.body, sub)))
                        .forEach { if (facts.add(it)) changed = true }
                }
            }
            for (im in facts.filterIsInstance<HirPImp>().toList()) {
                val ants = normalizeFacts(listOf(im.a))
                if (ants.isNotEmpty() && ants.all { facts.contains(it) }) {
                    normalizeFacts(listOf(im.b)).forEach { if (facts.add(it)) changed = true }
                }
            }
            for (im in facts.filterIsInstance<HirPImp>().toList()) {
                if (facts.contains(HirPNot(im.b, nid(), SYNTHETIC_POS))) {
                    normalizeFacts(listOf(HirPNot(im.a, nid(), SYNTHETIC_POS))).forEach { if (facts.add(it)) changed = true }
                }
            }
            if (!facts.contains(HirPBot(nid(), SYNTHETIC_POS))) {
                val negated = facts.filterIsInstance<HirPNot>().map { it.p }.toHashSet()
                if (facts.any { p -> p !is HirPNot && p in negated }) {
                    facts.add(HirPBot(nid(), SYNTHETIC_POS)); changed = true
                }
            }
            var poolGrew = false
            if (keyTerms.size < maxHarvest) {
                val fresh = facts.filter { harvested.add(it) }
                if (fresh.isNotEmpty()) {
                    val grown = (keyTerms + fresh.flatMap { groundTerms(listOf(it)) })
                        .distinctBy { termTag(it) }.take(maxHarvest)
                    if (grown.size != keyTerms.size) { keyTerms = grown; poolGrew = true }
                }
            }
            if (poolGrew) forallSubs = buildSubs(keyTerms)
            if (!changed && !poolGrew) break
        }
        return facts.toList()
    }

    /** 目标导向证明（旧 Prover.prove 的 V1 子集）：闭包事实 + 目标分解 + 等式图可达。深度预算 4。 */
    private fun closureProve(facts: List<HirProp>, goal: HirProp): String? {
        val cl = closure(facts, listOf(goal), termSource = facts)
        val eqs = cl.filterIsInstance<HirPAtom>()
            .filter { (it.head == "=" || it.head == "==") && it.terms.size == 2 }
        if (prove(cl, goal, 0, HashSet(), eqs)) return deriveRule(cl, goal)
        return null
    }

    /** 由闭包命中的形态回推规则编号：⊥ 爆炸 → R-CONTRA；MT 命中（cl 有 imp(目标前件 → b) 且 ¬b ∈ cl）→ R-MT；其余 → R-MP。 */
    private fun deriveRule(cl: List<HirProp>, goal: HirProp): String {
        if (goal !is HirPBot && cl.any { it is HirPBot }) return "R-CONTRA"
        if (goal is HirPNot) {
            val np = goal.p
            if (cl.any { it is HirPImp && it.a == np && cl.contains(HirPNot(it.b, nid(), SYNTHETIC_POS)) }) return "R-MT"
        }
        return "R-MP"
    }

    private fun prove(
        facts: List<HirProp>, goal: HirProp, d: Int, visited: MutableSet<HirProp>, eqs: List<HirPAtom>,
    ): Boolean {
        if (d > 4) return false
        if (goal !is HirPBot && facts.any { it is HirPBot }) return true   // 矛盾爆炸
        if (goal !is HirPBot && facts.any { it == goal }) return true
        if (!visited.add(goal)) return false
        val direct = when (goal) {
            is HirPTop -> true
            is HirPBot -> facts.any { it is HirPBot }
            is HirPAnd -> prove(facts, goal.a, d, visited, eqs) && prove(facts, goal.b, d, visited, eqs)
            is HirPOr -> prove(facts, goal.a, d, visited, eqs) || prove(facts, goal.b, d, visited, eqs)
            is HirPImp -> prove(facts + normalizeFacts(listOf(goal.a)), goal.b, d + 1, visited, eqs)
            is HirPIff -> prove(facts, HirPImp(goal.a, goal.b, nid(), SYNTHETIC_POS), d, visited, eqs) &&
                prove(facts, HirPImp(goal.b, goal.a, nid(), SYNTHETIC_POS), d, visited, eqs)
            is HirPExists -> {
                if (goal.names.size != 1) false
                else groundTerms(facts).any { c ->
                    prove(facts, substProp(goal.body, mapOf(goal.names[0] to c)), d + 1, visited, eqs)
                }
            }
            is HirPForall -> {
                if (goal.names.isEmpty()) prove(facts, goal.body, d + 1, visited, eqs)
                else {
                    val fresh = HashMap<String, HirTerm>()
                    goal.names.forEachIndexed { i, n -> fresh[n] = HirTName("#vg" + d + "_" + i, null, nid(), SYNTHETIC_POS) }
                    val g2 = substProp(goal.body, fresh)
                    val cl2 = closure(facts, listOf(g2), termSource = facts)
                    val eqs2 = cl2.filterIsInstance<HirPAtom>()
                        .filter { (it.head == "=" || it.head == "==") && it.terms.size == 2 }
                    prove(cl2, g2, d + 1, visited, eqs2)
                }
            }
            is HirPExists1 -> false   // 唯一性：V1 不做（ADR-13 §三）
            is HirPNot -> facts.any { it == goal } || facts.any { it is HirPBot } ||
                (goal.p is HirPNot && prove(facts, goal.p.p, d + 1, visited, eqs))
            is HirPAtom -> constAtomValue(goal) == true ||
                (goal.head in setOf("=", "==", "<=", "≤", ">=", "≥") && goal.terms.size == 2 &&
                    (goal.terms[0] == goal.terms[1] || eqReachable(eqs, goal.terms[0], goal.terms[1])))
        }
        return direct
    }

    /** 等式图可达（对称 + 传递，BFS 双向边）——x=a, a=b ⊢ x=b 的链式代换（P4）。 */
    private fun eqReachable(eqs: List<HirPAtom>, from: HirTerm, to: HirTerm): Boolean {
        if (eqs.isEmpty()) return false
        val adj = HashMap<HirTerm, MutableList<HirTerm>>()
        for (e in eqs) {
            adj.getOrPut(e.terms[0]) { mutableListOf() }.add(e.terms[1])
            adj.getOrPut(e.terms[1]) { mutableListOf() }.add(e.terms[0])
        }
        val seen = HashSet<HirTerm>()
        val queue = ArrayDeque<HirTerm>()
        queue.addLast(from); seen.add(from)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur == to) return true
            adj[cur]?.forEach { n -> if (seen.add(n)) queue.addLast(n) }
        }
        return false
    }

    // ============================ 线性算术通道（旧 ArithProve 整体移植）============================

    private val arith = ArithChannel()

    /** 线性项：Σ coefs·var + c。 */
    private data class Lin(val coefs: Map<String, Long>, val c: Long) {
        operator fun plus(o: Lin): Lin {
            val m = LinkedHashMap(coefs)
            o.coefs.forEach { (k, v) -> m[k] = (m[k] ?: 0L) + v }
            return Lin(trim(m), c + o.c)
        }
        operator fun minus(o: Lin): Lin {
            val m = LinkedHashMap(coefs)
            o.coefs.forEach { (k, v) -> m[k] = (m[k] ?: 0L) - v }
            return Lin(trim(m), c - o.c)
        }
        fun neg() = Lin(trim(coefs.mapValues { -it.value }), -c)
        fun scale(k: Long) = Lin(if (coefs.isEmpty()) coefs else trim(coefs.mapValues { it.value * k }), c * k)
        /** 规范化:剔零系数（n-1 与 n 的差含 {n:0} 会令 ceilDivSafe(_,0)=null——t110 measure 义务的可证性）。 */
        private fun trim(m: Map<String, Long>): Map<String, Long> = m.filter { it.value != 0L }
    }

    private fun constLin(c: Long) = Lin(emptyMap(), c)

    private inner class ArithChannel {
        private val POS_INF = Long.MAX_VALUE
        private val NEG_INF = Long.MIN_VALUE
        private val ZERO = 0

        private inner class Ctx {
            val lo = HashMap<String, Long>()
            val hi = HashMap<String, Long>()
            val edges = ArrayList<LongArray>()   // [from, to, w]：to ≤ from + w
            var contradicted = false
            val nameIndex = HashMap<String, Int>()
            val nameList = ArrayList<String>()
            var defs: Map<String, Lin> = emptyMap()
            fun v2idx(name: String): Int {
                if (name !in nameIndex) { nameIndex[name] = nameList.size + 1; nameList.add(name) }
                return nameIndex[name]!!
            }
            fun linD(t: HirTerm): Lin? = linOf(t)?.let { resolveDefs(it, defs) }
        }

        fun proves(gamma: List<HirProp>, goal: HirProp, neLimit: Int = 2): Boolean? {
            val ctx = Ctx()
            ctx.defs = buildDefs(gamma)
            val ne = ArrayList<Lin>()
            fun walk(p: HirProp) {
                when (p) {
                    is HirPAnd -> { walk(p.a); walk(p.b) }
                    is HirPAtom -> if (!ctx.consumeNe(p, ne)) ctx.consume(p)
                    is HirPNot -> (p.p as? HirPAtom)?.let { if (!ctx.consumeNegNe(it, ne)) ctx.consumeNeg(it) }
                    else -> {}
                }
            }
            gamma.forEach { walk(it) }
            if (ctx.contradicted) return true
            if (bfHasNegCycle(ctx.edges, ctx)) return true
            val cases = if (ne.isNotEmpty() && ne.size <= neLimit) ne else emptyList()
            if (cases.isEmpty()) return goalOf(goal, ctx)
            var sawNull = false
            for (branch in 0 until (1 shl cases.size)) {
                val cc = cloneCtx(ctx)
                var allExpressible = true
                cases.forEachIndexed { i, d ->
                    val ok = if (((branch shr i) and 1) == 0) applyRel("<=", d, constLin(-1), cc)
                             else applyRel(">=", d, constLin(1), cc)
                    if (!ok) allExpressible = false
                }
                if (cc.contradicted || bfHasNegCycle(cc.edges, cc)) continue
                val r = goalOf(goal, cc)
                when {
                    r == true -> {}
                    r == false -> if (allExpressible) return false else return null
                    else -> sawNull = true
                }
            }
            return if (sawNull) null else true
        }

        private fun goalOf(goal: HirProp, ctx: Ctx): Boolean? = when (goal) {
            is HirPAtom -> atomImplied(goal, ctx)
            is HirPOr -> disjRefute(goal, ctx)
            else -> null
        }

        /** 定义候选收集：合取展开下 v = 线性式 / 线性式 = v（v 单名裸项）；链式展开最多 32 轮。 */
        private fun buildDefs(gamma: List<HirProp>): Map<String, Lin> {
            val eqs = ArrayList<Pair<String, HirTerm>>()
            fun walk(p: HirProp) {
                when (p) {
                    is HirPAnd -> { walk(p.a); walk(p.b) }
                    is HirPAtom -> if ((p.head == "=" || p.head == "==") && p.terms.size == 2) {
                        val a = p.terms[0]; val b = p.terms[1]
                        val an = (a as? HirTName)?.name
                        val bn = (b as? HirTName)?.name
                        when {
                            an != null && b !is HirTName -> eqs += an to b
                            bn != null && a !is HirTName -> eqs += bn to a
                        }
                    }
                    else -> {}
                }
            }
            gamma.forEach { walk(it) }
            if (eqs.isEmpty()) return emptyMap()
            val defs = LinkedHashMap<String, Lin>()
            repeat(32) {
                var changed = false
                for ((v, rhs) in eqs) {
                    if (v in defs) continue
                    val l = linOf(rhs)?.let { resolveDefs(it, defs) } ?: continue
                    if (v in l.coefs) continue
                    defs[v] = l; changed = true
                }
                if (!changed) return defs
            }
            return defs
        }

        private fun resolveDefs(l: Lin, defs: Map<String, Lin>): Lin {
            if (defs.isEmpty()) return l
            var cur = l; var steps = 0
            while (steps++ < 32 && cur.coefs.keys.any { it in defs }) {
                val acc = LinkedHashMap<String, Long>()
                var c = cur.c
                cur.coefs.forEach { (v, k) ->
                    val d = defs[v]
                    if (d == null) acc[v] = (acc[v] ?: 0L) + k
                    else {
                        d.coefs.forEach { (w, m) -> acc[w] = (acc[w] ?: 0L) + k * m }
                        c += k * d.c
                    }
                }
                cur = Lin(acc, c)
            }
            return if (cur.coefs.keys.any { it in defs }) l else cur
        }

        private fun Ctx.consumeNe(p: HirPAtom, ne: MutableList<Lin>): Boolean {
            if (p.head != "!=" && p.head != "≠") return false
            if (p.terms.size != 2) return true
            val l = linOf(p.terms[0]) ?: return true
            val r = linOf(p.terms[1]) ?: return true
            ne += l.minus(r)
            return true
        }

        private fun Ctx.consumeNegNe(p: HirPAtom, ne: MutableList<Lin>): Boolean {
            if (p.head != "=" && p.head != "==") return false
            if (p.terms.size != 2) return true
            val l = linOf(p.terms[0]) ?: return true
            val r = linOf(p.terms[1]) ?: return true
            ne += l.minus(r)
            return true
        }

        private fun Ctx.consume(p: HirPAtom): Boolean {
            if (p.terms.size != 2) return false
            if (p.head == "in") {
                val r = rangeOf(p.terms[1]) ?: return false
                return applyRel(">=", linOf(p.terms[0]) ?: return false, constLin(r[0]), this) &&
                    applyRel("<=", linOf(p.terms[0]) ?: return false, constLin(r[1]), this)
            }
            val l = linOf(p.terms[0]) ?: return false
            val r = linOf(p.terms[1]) ?: return false
            return applyRel(p.head, l, r, this)
        }

        private fun Ctx.consumeNeg(p: HirPAtom): Boolean {
            if (p.terms.size != 2) return false
            val l = linOf(p.terms[0]) ?: return false
            val r = linOf(p.terms[1]) ?: return false
            val rel = when (p.head) {
                "<=", "≤" -> ">"
                "<" -> ">="
                ">=", "≥" -> "<"
                ">" -> "<="
                "!", "≠", "!=", "¬" -> "="
                else -> return false
            }
            return applyRel(rel, l, r, this)
        }

        /** 应用 G：L op R（整数域）。false = 不可表达（跳过）；矛盾置 contradicted。 */
        private fun applyRel(op: String, l: Lin, r: Lin, ctx: Ctx): Boolean {
            val d = l.minus(r)
            val (coefs, base) = when (op) {
                "<=", "≤" -> d.coefs to d.c
                "<" -> d.coefs to d.c + 1
                ">=", "≥" -> d.neg().coefs to -d.c
                ">" -> d.neg().coefs to -d.c + 1
                "=", "==" -> {
                    // = 拆 <= ∧ >= 是原子约束——任一侧不可表达则整体不注入（快照回滚防半注入）
                    val snapLo = HashMap(ctx.lo); val snapHi = HashMap(ctx.hi)
                    val snapEdges = ArrayList(ctx.edges); val snapContra = ctx.contradicted
                    val snapIdx = HashMap(ctx.nameIndex); val snapNames = ArrayList(ctx.nameList)
                    val ok = applyRel("<=", l, r, ctx) && applyRel(">=", l, r, ctx)
                    if (ok) return true
                    ctx.lo.clear(); ctx.lo.putAll(snapLo); ctx.hi.clear(); ctx.hi.putAll(snapHi)
                    ctx.edges.clear(); ctx.edges.addAll(snapEdges); ctx.contradicted = snapContra
                    ctx.nameIndex.clear(); ctx.nameIndex.putAll(snapIdx); ctx.nameList.clear(); ctx.nameList.addAll(snapNames)
                    return false
                }
                else -> return false
            }
            if (base == NEG_INF || base == POS_INF) return false
            val C = -base
            if (coefs.isEmpty()) {
                if (C < 0) { ctx.contradicted = true }
                return true
            }
            if (coefs.size == 1) {
                val (v, a) = coefs.entries.first()
                if (a > 0) {
                    val b = floorDivSafe(C, a) ?: return false
                    val cur = ctx.hi[v] ?: POS_INF
                    ctx.hi[v] = minOf(cur, b)
                } else {
                    val b = ceilDivSafe(C, a) ?: return false
                    val cur = ctx.lo[v] ?: NEG_INF
                    ctx.lo[v] = maxOf(cur, b)
                }
                val loV = ctx.lo[v] ?: NEG_INF; val hiV = ctx.hi[v] ?: POS_INF
                if (loV > hiV) ctx.contradicted = true
                return true
            }
            if (coefs.size == 2) {
                val es = coefs.entries.toList()
                val (v1, a1) = es[0]; val (v2, a2) = es[1]
                ctx.edges += when {
                    a1 == 1L && a2 == -1L -> longArrayOf(ctx.v2idx(v2).toLong(), ctx.v2idx(v1).toLong(), C)
                    a1 == -1L && a2 == 1L -> longArrayOf(ctx.v2idx(v1).toLong(), ctx.v2idx(v2).toLong(), C)
                    else -> return false
                }
                return true
            }
            return false
        }

        private fun cloneCtx(ctx: Ctx) = Ctx().apply {
            lo.putAll(ctx.lo); hi.putAll(ctx.hi); edges.addAll(ctx.edges)
            nameIndex.putAll(ctx.nameIndex); nameList.addAll(ctx.nameList)
            defs = ctx.defs
        }

        private fun linOf(e: HirTerm): Lin? { return when (e) {
            is HirTInt -> constLin(e.value.toLongOrNull() ?: return null)
            is HirTName -> Lin(mapOf(e.name to 1L), 0)
            is HirTUni -> if (e.op == "-" || e.op == "−") linOf(e.operand)?.neg() else null
            is HirTBin -> when (e.op) {
                "+" -> (linOf(e.left) ?: return null) + (linOf(e.right) ?: return null)
                "-", "−" -> (linOf(e.left) ?: return null) - (linOf(e.right) ?: return null)
                "*" -> {
                    val a = linOf(e.left) ?: return null; val b = linOf(e.right) ?: return null
                    val ka = constOf(a); val kb = constOf(b)
                    when {
                        ka != null -> b.scale(ka)
                        kb != null -> a.scale(kb)
                        else -> null
                    }
                }
                "/" -> {
                    val a = constOf(linOf(e.left) ?: return null) ?: return null
                    val b = constOf(linOf(e.right) ?: return null)
                    if (b == null || b == 0L) return null
                    if (a % b != 0L) return null
                    constLin(a / b)
                }
                else -> null
            }
            else -> null
        } }

        private fun constOf(l: Lin): Long? = if (l.coefs.isEmpty()) l.c else null

        private fun evalConst(e: HirTerm): Long? { return when (e) {
            is HirTInt -> e.value.toLongOrNull()
            is HirTUni -> if (e.op == "-" || e.op == "−") -(evalConst(e.operand) ?: return null) else null
            is HirTBin -> {
                val a = evalConst(e.left) ?: return null; val b = evalConst(e.right) ?: return null
                when (e.op) {
                    "+" -> a + b; "-", "−" -> a - b
                    "*" -> a * b
                    "/" -> if (b == 0L) null else a / b
                    else -> null
                }
            }
            else -> null
        } }

        private fun floorDivSafe(a: Long, b: Long): Long? {
            if (b == 0L) return null
            if (a == NEG_INF && b == -1L) return null
            return floorDiv(a, b)
        }

        private fun ceilDivSafe(a: Long, b: Long): Long? {
            if (b == 0L) return null
            if (a == NEG_INF) return null
            return -floorDiv(-a, b)
        }

        // ============ Bellman–Ford（差分图负环检测） ============

        private fun bfHasNegCycle(edges: List<LongArray>, ctx: Ctx): Boolean {
            if (edges.isEmpty()) return false
            val n = ctx.nameIndex.size + 1
            val dist = LongArray(n)
            repeat(n) {
                var changed = false
                for (e in edges) {
                    val u = e[0].toInt(); val v = e[1].toInt(); val w = e[2]
                    val nd = if (dist[u] == POS_INF) POS_INF else dist[u] + w
                    if (nd < dist[v]) { dist[v] = nd; changed = true }
                }
                if (!changed) return false
            }
            return true
        }

        /** 绝对上界：单变量界转 ZERO 边，与差分边一起从 ZERO 求最短路——x = y = z ≤ 7 时 upper(x) = 7。 */
        private fun upperBound(ctx: Ctx): Map<String, Long> {
            val edges = ArrayList<LongArray>(ctx.edges.size + ctx.lo.size + ctx.hi.size + 1)
            edges.addAll(ctx.edges)
            ctx.hi.forEach { (v, h) -> if (h != POS_INF) edges += longArrayOf(ZERO.toLong(), ctx.v2idx(v).toLong(), h) }
            ctx.lo.forEach { (v, l) -> if (l != NEG_INF) edges += longArrayOf(ctx.v2idx(v).toLong(), ZERO.toLong(), -l) }
            if (edges.isEmpty()) return emptyMap()
            val n = ctx.nameIndex.size + 1
            val dist = LongArray(n) { POS_INF }
            dist[ZERO] = 0L
            for (k in 0 until n - 1) {
                var changed = false
                for (e in edges) {
                    val u = e[0].toInt(); val v = e[1].toInt(); val w = e[2]
                    if (dist[u] == POS_INF) continue
                    val nd = dist[u] + w
                    if (nd < dist[v]) { dist[v] = nd; changed = true }
                }
                if (!changed) break
            }
            val out = HashMap<String, Long>()
            ctx.nameList.forEachIndexed { i, nm -> val d = dist[i + 1]; if (d != POS_INF) out[nm] = d }
            return out
        }

        // ============ 目标判定 ============

        private fun atomImplied(g: HirPAtom, ctx: Ctx): Boolean? {
            if (g.terms.size != 2) return null
            if (g.head == "in") {
                val r = rangeOf(g.terms[1]) ?: return null
                val v = (g.terms[0] as? HirTName)?.name ?: return null
                val loV = ctx.lo[v] ?: NEG_INF; val hiV = ctx.hi[v] ?: POS_INF
                return (loV == NEG_INF || loV >= r[0]) && (hiV == POS_INF || hiV <= r[1])
            }
            run {
                val ld = ctx.linD(g.terms[0]); val rd = ctx.linD(g.terms[1])
                if (ld != null && rd != null && ld.coefs.isEmpty() && rd.coefs.isEmpty()) {
                    val ca = ld.c; val cb = rd.c
                    return when (g.head) {
                        "=", "==" -> ca == cb; "!=", "≠" -> ca != cb
                        "<" -> ca < cb; ">" -> ca > cb
                        "<=", "≤" -> ca <= cb; ">=", "≥" -> ca >= cb
                        else -> null
                    }
                }
            }
            val ca = evalConst(g.terms[0]); val cb = evalConst(g.terms[1])
            if (ca != null && cb != null) return when (g.head) {
                "=", "==" -> ca == cb; "!=", "≠" -> ca != cb
                "<" -> ca < cb; ">" -> ca > cb
                "<=", "≤" -> ca <= cb; ">=", "≥" -> ca >= cb
                else -> null
            }
            if (g.head == "=" || g.head == "==") {
                val lc = evalConst(g.terms[0]); val rc = evalConst(g.terms[1])
                if (lc != null && rc != null) return lc == rc
                val l = ctx.linD(g.terms[0]) ?: return null
                val r = ctx.linD(g.terms[1]) ?: return null
                val okLe = negateAndApply("<=", l, r, cloneCtx(ctx))
                val okGe = negateAndApply(">=", l, r, cloneCtx(ctx))
                return when {
                    okLe == true && okGe == true -> true
                    okLe == null || okGe == null -> null
                    else -> false
                }
            }
            if (g.head == "!=" || g.head == "≠") {
                val l = ctx.linD(g.terms[0]) ?: return null
                val r = ctx.linD(g.terms[1]) ?: return null
                val d = l.minus(r)
                var loD = d.c; var loOk = true
                var hiD = d.c; var hiOk = true
                for ((v, a) in d.coefs) {
                    val lv = ctx.lo[v] ?: NEG_INF; val hv = ctx.hi[v] ?: POS_INF
                    if (a > 0) {
                        if (lv == NEG_INF) loOk = false else loD += a * lv
                        if (hv == POS_INF) hiOk = false else hiD += a * hv
                    } else {
                        if (hv == POS_INF) loOk = false else loD += a * hv
                        if (lv == NEG_INF) hiOk = false else hiD += a * lv
                    }
                }
                return (loOk && loD > 0) || (hiOk && hiD < 0)
            }
            val l = ctx.linD(g.terms[0]) ?: return null
            val r = ctx.linD(g.terms[1]) ?: return null
            return negateAndApply(g.head, l, r, cloneCtx(ctx))
        }

        private fun negateAndApply(op: String, l: Lin, r: Lin, ctx: Ctx): Boolean? {
            val d = l.minus(r)
            val (coefs, C) = when (op) {
                "<=", "≤" -> d.neg().coefs to d.c - 1
                "<" -> d.neg().coefs to d.c
                ">=", "≥" -> d.coefs to -d.c - 1
                ">" -> d.coefs to -d.c
                else -> return null
            }
            // 常数相消分支:按 op 语义直判（l 与 r 均非常量而差为常数——t110 measure 义务);
            // 旧实现 `C >= 0` 对四比较符一律符号反转（如 n+1 < n 被误证）,此分支在消零规范化前不可达。
            if (coefs.isEmpty()) return when (op) {
                "<=", "≤" -> d.c <= 0
                "<" -> d.c < 0
                ">=", "≥" -> d.c >= 0
                ">" -> d.c > 0
                else -> null
            }
            if (coefs.size == 1) {
                val (v, a) = coefs.entries.first()
                if (a > 0) {
                    val b = floorDivSafe(C, a) ?: return null
                    val loV = ctx.lo[v] ?: NEG_INF
                    return loV != NEG_INF && loV > b
                } else {
                    val b = ceilDivSafe(C, a) ?: return null
                    val up = upperBound(ctx)[v] ?: POS_INF
                    return up != POS_INF && up < b
                }
            }
            if (coefs.size == 2) {
                val es = coefs.entries.toList()
                val (v1, a1) = es[0]; val (v2, a2) = es[1]
                when {
                    a1 == 1L && a2 == -1L -> { ctx.edges += longArrayOf(ctx.v2idx(v2).toLong(), ctx.v2idx(v1).toLong(), C) }
                    a1 == -1L && a2 == 1L -> { ctx.edges += longArrayOf(ctx.v2idx(v1).toLong(), ctx.v2idx(v2).toLong(), C) }
                    else -> return null
                }
                return bfHasNegCycle(ctx.edges, ctx)
            }
            return null
        }

        private fun disjRefute(goal: HirPOr, ctx: Ctx): Boolean? {
            val leaves = flattenOr(goal)
            if (leaves.isEmpty()) return null
            val v = (leaves[0] as? HirPAtom)?.terms?.firstOrNull() as? HirTName ?: return null
            if (leaves.any { p -> (p as? HirPAtom)?.terms?.firstOrNull() != v }) return null
            var segs: MutableList<LongArray> = mutableListOf(longArrayOf(ctx.lo[v.name] ?: NEG_INF, ctx.hi[v.name] ?: POS_INF))
            for (p in leaves) {
                val r = leafRange(p) ?: return null
                val out = ArrayList<LongArray>()
                for (s in segs) {
                    if (s[1] < r[0] || r[1] < s[0]) { out += s; continue }
                    if (s[0] < r[0] && r[0] > NEG_INF) out += longArrayOf(s[0], r[0] - 1)
                    if (r[1] < s[1] && r[1] < POS_INF) out += longArrayOf(r[1] + 1, s[1])
                }
                segs = out
                if (segs.isEmpty()) return true
            }
            return false
        }

        private fun leafRange(p: HirProp): LongArray? {
            val pa = p as? HirPAtom ?: return null
            if (pa.terms.size != 2) return null
            if (pa.head == "in") return rangeOf(pa.terms[1])
            val l = linOf(pa.terms[0]) ?: return null
            val r = linOf(pa.terms[1]) ?: return null
            val d = l.minus(r)
            if (d.coefs.size != 1) return null
            val (_, a) = d.coefs.entries.first()
            val c = -d.c
            return when (pa.head) {
                "<=", "≤" -> if (a == 1L) longArrayOf(NEG_INF, c) else longArrayOf(ceilDivSafe(c, a) ?: return null, POS_INF)
                "<" -> if (a == 1L) longArrayOf(NEG_INF, c - 1) else longArrayOf(ceilDivSafe(c + 1, a) ?: return null, POS_INF)
                ">=", "≥" -> if (a == 1L) longArrayOf(c, POS_INF) else longArrayOf(NEG_INF, floorDivSafe(c, a) ?: return null)
                ">" -> if (a == 1L) longArrayOf(c + 1, POS_INF) else longArrayOf(NEG_INF, floorDivSafe(c - 1, a) ?: return null)
                "=", "==" -> longArrayOf(c, c)
                else -> null
            }
        }

        private fun rangeOf(e: HirTerm): LongArray? = when (e) {
            is HirTBin -> if (e.op == "..") consts(e.left, e.right) else null
            else -> null
        }

        private fun consts(a: HirTerm, b: HirTerm): LongArray? {
            val av = (a as? HirTInt)?.value?.toLongOrNull() ?: return null
            val bv = (b as? HirTInt)?.value?.toLongOrNull() ?: return null
            return longArrayOf(av, bv)
        }

        private fun flattenOr(p: HirProp): List<HirProp> = when (p) {
            is HirPOr -> flattenOr(p.a) + flattenOr(p.b)
            else -> listOf(p)
        }
    }

    // ============================ 莱布尼茨代换（旧 substBfs）============================

    /**
     * 目标经 Γ 里 = / == 等式（项级深替）与 ↔ 等价（命题级替）双向改写，多步 BFS 直到命中
     * 事实库。maxSteps/maxNodes 双上限防膨胀型等式指数爆炸（自动通道 40/100 低档）。
     */
    fun substBfs(goal: HirProp, facts: List<HirProp>, maxSteps: Int, maxNodes: Int): Boolean {
        // t109 OQ-2:R-SUBST 段消费预算;耗尽 → false（等同证不出,回落既有兜底）
        if (!oracle.spend()) return false
        val eqs = facts.filterIsInstance<HirPAtom>().filter { (it.head == "=" || it.head == "==") && it.terms.size == 2 }
        val iffs = facts.filterIsInstance<HirPIff>()
        if (eqs.isEmpty() && iffs.isEmpty()) return false
        val seen = HashMap<Int, MutableList<HirProp>>()
        val queue = ArrayDeque<HirProp>()
        queue.addLast(goal)
        var steps = 0
        var nodes = 0
        while (queue.isNotEmpty() && ++steps <= maxSteps && nodes <= maxNodes) {
            val cur = queue.removeFirst()
            if (facts.any { it == cur }) return true
            val succ = ArrayList<HirProp>()
            for (e in eqs) {
                succ += rewriteTerm(cur, e.terms[0], e.terms[1])
                succ += rewriteTerm(cur, e.terms[1], e.terms[0])
            }
            for (f in iffs) {
                succ += rewriteSubprop(cur, f.a, f.b)
                succ += rewriteSubprop(cur, f.b, f.a)
            }
            for (s in succ) {
                nodes++
                if (nodes > maxNodes) return false
                val h = s.hashCode()
                val bucket = seen.getOrPut(h) { mutableListOf() }
                if (bucket.none { it == s }) { bucket.add(s); queue.addLast(s) }
            }
        }
        return false
    }

    /** 项级单向深替：把 prop 中出现的 from 项整体替换为 to 项（一步，所有出现位）。 */
    private fun rewriteTerm(p: HirProp, from: HirTerm, to: HirTerm): HirProp = when (p) {
        is HirPAtom -> HirPAtom(p.head, p.terms.map { rewriteTermIn(it, from, to) }, nid(), SYNTHETIC_POS)
        is HirPNot -> HirPNot(rewriteTerm(p.p, from, to), nid(), SYNTHETIC_POS)
        is HirPAnd -> HirPAnd(rewriteTerm(p.a, from, to), rewriteTerm(p.b, from, to), nid(), SYNTHETIC_POS)
        is HirPOr -> HirPOr(rewriteTerm(p.a, from, to), rewriteTerm(p.b, from, to), nid(), SYNTHETIC_POS)
        is HirPImp -> HirPImp(rewriteTerm(p.a, from, to), rewriteTerm(p.b, from, to), nid(), SYNTHETIC_POS)
        is HirPIff -> HirPIff(rewriteTerm(p.a, from, to), rewriteTerm(p.b, from, to), nid(), SYNTHETIC_POS)
        is HirPForall -> if (anyVarFree(from, p.names)) p else HirPForall(p.names, rewriteTerm(p.body, from, to), nid(), SYNTHETIC_POS)
        is HirPExists -> if (anyVarFree(from, p.names)) p else HirPExists(p.names, rewriteTerm(p.body, from, to), nid(), SYNTHETIC_POS)
        is HirPExists1 -> if (anyVarFree(from, p.names)) p else HirPExists1(p.names, rewriteTerm(p.body, from, to), nid(), SYNTHETIC_POS)
        else -> p
    }

    private fun rewriteTermIn(t: HirTerm, from: HirTerm, to: HirTerm): HirTerm =
        if (t == from) to else when (t) {
            is HirTBin -> HirTBin(t.op, rewriteTermIn(t.left, from, to), rewriteTermIn(t.right, from, to), nid(), SYNTHETIC_POS)
            is HirTUni -> HirTUni(t.op, rewriteTermIn(t.operand, from, to), nid(), SYNTHETIC_POS)
            is HirTCall -> HirTCall(rewriteTermIn(t.callee, from, to), t.args.map { rewriteTermIn(it, from, to) }, nid(), SYNTHETIC_POS)
            is HirTInst -> HirTInst(rewriteTermIn(t.target, from, to), t.terms.map { rewriteTermIn(it, from, to) }, nid(), SYNTHETIC_POS)
            is HirTField -> HirTField(rewriteTermIn(t.target, from, to), t.name, nid(), SYNTHETIC_POS)
            is HirTSymbolCall -> HirTSymbolCall(t.op, t.args.map { rewriteTermIn(it, from, to) }, nid(), SYNTHETIC_POS)
            else -> t
        }

    /** from 含绑定名时不替换（避免捕获）。 */
    private fun anyVarFree(t: HirTerm, names: List<String>): Boolean = when (t) {
        is HirTName -> t.name in names
        is HirTBin -> anyVarFree(t.left, names) || anyVarFree(t.right, names)
        is HirTUni -> anyVarFree(t.operand, names)
        is HirTCall -> anyVarFree(t.callee, names) || t.args.any { anyVarFree(it, names) }
        is HirTInst -> anyVarFree(t.target, names) || t.terms.any { anyVarFree(it, names) }
        is HirTField -> anyVarFree(t.target, names)
        is HirTSymbolCall -> t.args.any { anyVarFree(it, names) }
        else -> false
    }

    /** 命题级单向替：目标中结构与 from 相同的子命题替换为 to。 */
    private fun rewriteSubprop(p: HirProp, from: HirProp, to: HirProp): HirProp {
        if (p == from) return to
        return when (p) {
            is HirPNot -> HirPNot(rewriteSubprop(p.p, from, to), nid(), SYNTHETIC_POS)
            is HirPAnd -> HirPAnd(rewriteSubprop(p.a, from, to), rewriteSubprop(p.b, from, to), nid(), SYNTHETIC_POS)
            is HirPOr -> HirPOr(rewriteSubprop(p.a, from, to), rewriteSubprop(p.b, from, to), nid(), SYNTHETIC_POS)
            is HirPImp -> HirPImp(rewriteSubprop(p.a, from, to), rewriteSubprop(p.b, from, to), nid(), SYNTHETIC_POS)
            is HirPIff -> HirPIff(rewriteSubprop(p.a, from, to), rewriteSubprop(p.b, from, to), nid(), SYNTHETIC_POS)
            else -> p
        }
    }

    // ============================ 义务核对（（节点，上下文）→（结论，诊断））============================

    /** Γ 快照（ADR-6 §四 4.4）：归一化并集，搜索过程状态丢弃（ADR-1 §3.8）。 */
    fun snapshot(facts: List<HirProp>, matched: SymbolRef? = null): HirProofContext =
        HirProofContext(normalizeFacts(facts), matched)

    /** axiom：免验证直接点亮（R-AXIOM）——诊断面无（公理不经证明器）。 */
    fun axiom(): HirVerdict = HirVerdict(HirProofStatus.PROVEN, null)

    /** ∵∴：前提逐条证 → 结论由 Γ + 前提证 → PROVEN（结论注入由 Elaborator 帧落）+ D-BTS。 */
    fun because(premises: List<HirProp>, conclusion: HirProp, facts: List<HirProp>, pos: SourceRange): BecauseResult {
        val ctx = snapshot(facts)
        var premisesOk = true
        for (pm in premises) {
            if (proves(facts, pm) == null) {
                premisesOk = false
                diag.error("E-PREMISE-UNPROVEN", pos,
                    "因为-所以的前提 " + render(pm) + " 没证出。当前已知事实 Γ：" + facts.joinToString("； ") { render(it) })
            }
        }
        if (premisesOk) {
            val rule = proves(facts + premises, conclusion)
            if (rule != null) {
                diag.supplement("D-BTS", pos, "因为-所以：∵…∴ " + render(conclusion) + " 成立")
                return BecauseResult(HirVerdict(HirProofStatus.PROVEN, null), rule, ctx)
            }
            diag.error("E-CANNOT-PROVE", pos,
                "因为-所以的结论 " + render(conclusion) + " 证不出。当前已知事实 Γ：" + facts.joinToString("； ") { render(it) })
        }
        return BecauseResult(HirVerdict(HirProofStatus.FAILED, "E-CANNOT-PROVE"), null, ctx)
    }

    /** by 语句式：免验证注入已移除（by 只保留委托语义），编译期错误 + 迁移指引。 */
    fun byStmt(props: List<HirProp>, pos: SourceRange): HirVerdict {
        diag.error("E-BY-REMOVED", pos,
            "语句式 by [q] 的免验证命题注入已移除（by 只保留委托语义）。供给命题改用 axiom q 语句（顶层/函数内通用）；断言表达式成立改用后缀式 expr by [q]（经证明器核对）")
        return HirVerdict(HirProofStatus.FAILED, "E-BY-REMOVED")
    }

    /** by 断言（后缀式 expr by [q]）：逐条核对——证不出报 E-CANNOT-PROVE；断言不注入 Γ。 */
    fun byAsserts(props: List<HirProp>, facts: List<HirProp>, pos: SourceRange): HirVerdict {
        var allOk = true
        for (q in props) {
            if (proves(facts, q) == null) {
                allOk = false
                diag.error("E-CANNOT-PROVE", pos,
                    "by 断言「" + render(q) + "」在当前上下文证不出。by 断言须由现有事实推出；要声明免验证的公理请改用 axiom 语句")
            }
        }
        return if (allOk) HirVerdict(HirProofStatus.PROVEN, null) else HirVerdict(HirProofStatus.FAILED, "E-CANNOT-PROVE")
    }

    /** 策略语句：simp → 算术通道（neLimit=4）；subst → 莱布尼茨代换（400/400 满档）；其余落 E-STRATEGY-FAILED。 */
    fun strategy(name: String, goal: HirProp, facts: List<HirProp>, pos: SourceRange): HirVerdict {
        val proved = when (name) {
            "simp" -> arith.proves(facts, goal, neLimit = 4) == true || constAtomValue(goal) == true
            "subst" -> substBfs(goal, facts, maxSteps = 400, maxNodes = 400)
            else -> false
        }
        if (proved) {
            diag.supplement("D-STRATEGY", pos, "@" + ident(name) + " 证出 " + render(goal))
            return HirVerdict(HirProofStatus.PROVEN, null)
        }
        diag.error("E-STRATEGY-FAILED", pos, "@" + ident(name) + " 无法证明 " + render(goal))
        return HirVerdict(HirProofStatus.FAILED, "E-STRATEGY-FAILED")
    }

    /** 命题 when：析取前提须在 Γ（W-PROP-UNBOUND）；各支 BTS 结论取交集注入块外（D-WHEN-PROP / W-WHEN-NO-COMMON）。 */
    fun whenProp(disjunction: HirProp, armConclusions: List<List<HirProp>>, facts: List<HirProp>, pos: SourceRange): WhenPropResult {
        val ctx = snapshot(facts)
        if (!facts.any { render(it) == render(disjunction) })
            diag.warn("W-PROP-UNBOUND", pos, "分类讨论的析取前提 " + render(disjunction) + " 不在 Γ 中")
        val common = if (armConclusions.isEmpty()) emptyList()
            else armConclusions.map { arms -> arms.distinctBy { render(it) } }
                .reduce { a, b -> a.filter { x -> b.any { y -> y == x } } }
        if (armConclusions.isEmpty() || common.isEmpty()) {
            diag.warn("W-WHEN-NO-COMMON", pos, "分类讨论各分支没有共同的结论")
            return WhenPropResult(HirVerdict(HirProofStatus.FAILED, "W-WHEN-NO-COMMON"), common, ctx)
        }
        diag.supplement("D-WHEN-PROP", pos, "分类讨论得出共同结论：" + common.joinToString("; ") { render(it) })
        return WhenPropResult(HirVerdict(HirProofStatus.PROVEN, null), common, ctx)
    }

    /** 命题 if（排中律）：未挂 @classic → E-NEED-CLASSIC；两支新事实取结构相等的交注入块外。 */
    fun ifProp(p: HirProp, thenNew: List<HirProp>, elseNew: List<HirProp>, classicMounted: Boolean, pos: SourceRange): IfPropResult {
        if (!classicMounted) {
            diag.warn("E-NEED-CLASSIC", pos, "if[p] 需要挂载 core.classic（文件内任一声明加 @classic 注解即挂载）")
            return IfPropResult(HirVerdict(HirProofStatus.FAILED, "E-NEED-CLASSIC"), emptyList())
        }
        val common = ArrayList<HirProp>()
        for (q in elseNew) {
            val m = thenNew.find { it == q }
            if (m != null) common.add(m)
        }
        if (common.isEmpty()) {
            diag.error("E-IF-CONCLUSION-MISMATCH", pos, "if[p] 两支没有共同结论（排中律要求两支证出同一个结论）")
            return IfPropResult(HirVerdict(HirProofStatus.FAILED, "E-IF-CONCLUSION-MISMATCH"), emptyList())
        }
        return IfPropResult(HirVerdict(HirProofStatus.PROVEN, null), common)
    }

    /** ∃ 引入：实例代入后可证 → PROVEN + 注入 ∃name 命题（注入由 Elaborator 落）+ D-EXIST-INTRO。 */
    fun existsIntro(names: List<String>, witnesses: List<HirTerm>, prop: HirProp, facts: List<HirProp>, pos: SourceRange): ExistsIntroResult {
        val sub = names.zip(witnesses).toMap()
        val inst = substProp(prop, sub)
        val existsProp = HirPExists(names, prop, nid(), SYNTHETIC_POS)
        val ctx = snapshot(facts)
        if (proves(facts, inst) != null) {
            diag.supplement("D-EXIST-INTRO", pos, "∃ 引入成立：∃" + names.joinToString(",") + ". " + render(prop))
            return ExistsIntroResult(HirVerdict(HirProofStatus.PROVEN, null), existsProp, inst, ctx)
        }
        diag.error("E-QUANT-UNPROVEN", pos,
            "∃ 引入失败：想证明存在某个值满足性质，但给出的实例「" + render(inst) + "」满足不了。检查实例能否推出目标性质")
        return ExistsIntroResult(HirVerdict(HirProofStatus.FAILED, "E-QUANT-UNPROVEN"), existsProp, inst, ctx)
    }

    /** ∃ 消去：Γ 有渲染同形的 ∃ 命题 → PROVEN（R-EXISTS-MATCH），体子的 P(x) 注入由 Elaborator 落。 */
    fun existsElim(name: String, prop: HirProp, facts: List<HirProp>, pos: SourceRange): HirVerdict {
        val goal = HirPExists(listOf(name), prop, nid(), SYNTHETIC_POS)
        if (facts.any { it is HirPExists && render(it) == render(goal) })
            return HirVerdict(HirProofStatus.PROVEN, null)
        diag.warn("E-PROP-UNBOUND", pos,
            "前提 " + render(goal) + " 不在已知事实里（先用 ∃ 引入或 ∵ 注入）。当前已知事实：" + facts.joinToString("；") { render(it) })
        return HirVerdict(HirProofStatus.FAILED, "E-PROP-UNBOUND")
    }

    /** 局部函数（∀ 引入）：有体 → PROVEN（定义即证明）+ D-LOCAL-FUN；签名即证明 → E-QUANT-UNPROVEN。 */
    fun localFun(name: String, hasBody: Boolean, pos: SourceRange): HirVerdict {
        if (!hasBody) {
            diag.warn("E-QUANT-UNPROVEN", pos, "局部函数 " + ident(name) + " 必须体（∀ 引入要求有体，不是签名即证明）")
            return HirVerdict(HirProofStatus.FAILED, "E-QUANT-UNPROVEN")
        }
        diag.supplement("D-LOCAL-FUN", pos, "局部函数 " + ident(name) + " 登记为局部定理（∀ 引入）")
        return HirVerdict(HirProofStatus.PROVEN, null)
    }

    /** 函数下文（posts）：$ 代换后的目标可由函数帧 Γ 证 → PROVEN / E-POST-UNPROVEN；@axiom 函数免证（D-AXIOM-POST）。 */
    fun postcondition(fnName: String, goals: List<HirProp>, facts: List<HirProp>, isAxiom: Boolean, pos: SourceRange): HirVerdict {
        if (goals.isEmpty()) return HirVerdict(HirProofStatus.PROVEN, null)
        if (isAxiom) {
            diag.supplement("D-AXIOM-POST", pos, "函数 " + ident(fnName) + " 标了 @axiom：下文签名即公理（编译器信任，不做自动证明）")
            return HirVerdict(HirProofStatus.PROVEN, null)
        }
        var allOk = true
        for (goal in goals) {
            if (proves(facts, goal) == null) {
                allOk = false
                diag.error("E-POST-UNPROVEN", pos,
                    "函数 " + ident(fnName) + " 声明了返回后的保证（下文「" + render(goal) + "」），但函数体没证出。检查 return 的值是否满足它，或用 ∵ 前提供给可推出的事实、axiom 语句声明公理")
            }
        }
        return if (allOk) HirVerdict(HirProofStatus.PROVEN, null) else HirVerdict(HirProofStatus.FAILED, "E-POST-UNPROVEN")
    }

    // ============================ 义务核对结果 ============================

    data class BecauseResult(val verdict: HirVerdict, val rule: String?, val context: HirProofContext)

    data class WhenPropResult(val verdict: HirVerdict, val common: List<HirProp>, val context: HirProofContext)

    data class IfPropResult(val verdict: HirVerdict, val common: List<HirProp>)

    data class ExistsIntroResult(val verdict: HirVerdict, val existsProp: HirPExists, val inst: HirProp, val context: HirProofContext)
}
