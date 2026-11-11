package sublimation.frontend.hir

/**
 * 证明上下文（D-9：证明上下文齐备保留）：义务核对时刻作用域内的事实集——
 * P-FRAME 沿父链本帧事实 ∪ 全局事实（P-PURE 等）的归一化并集（去重保序），
 * 及匹配到的定理符号引用（∵∴ 定理空间匹配）。
 *
 * 丢弃边界（ADR-1 §3.8 丢弃项 1）：证明**搜索过程状态**（闭包、visited、实例化矩阵）
 * 不入树——判定算法的运行时状态不是程序信息。
 */
data class HirProofContext(
    val facts: List<HirProp> = emptyList(),
    val matched: SymbolRef? = null,
)

/**
 * 证明核对结论状态。R-NO-GUESS：Γ ⊬ P 绝不降级为成立，故 FAILED 恒对应一条
 * ERROR 诊断（S-1 §3 契约：证明检查失败即编译失败）。
 */
enum class HirProofStatus { PROVEN, FAILED }

/** 证明义务核对结论：diagCode 反引触发诊断（失败时的 E- 族码）。 */
data class HirVerdict(
    val status: HirProofStatus,
    val diagCode: String? = null,
)

/**
 * 证明义务节点契约（ADR-1 §3.4）：命题与证明的唯一住所中承担义务的语句——
 * axiom / by / ∵∴ / 命题 when / if[p] / ∃ 引入与消去 / @simp 策略。
 * rule 为 discharge 所用规则编号（R-MP / R-MT / R-CONTRA / R-FORALL-INST /
 * R-EXISTS-MATCH / R-AXIOM / 算术通道 A- 族），null = 尚未核对。
 */
interface HirProofObligation {
    val proofContext: HirProofContext
    val rule: String?
    val verdict: HirVerdict?
}

/** 函数/函数值的效应结论（P-PURE：未标 @impure 的具名函数自动 pure；ADR-1 §3.3 #12）。 */
enum class HirPurity { PURE, IMPURE }
