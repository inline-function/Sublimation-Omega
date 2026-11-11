package sublimation.frontend.hir

import sublimation.frontend.diag.NodeId
import sublimation.frontend.diag.SourceRange

/**
 * HIR 语句层级（S-16~S-30 映射，ADR-1 §3.2）：程序面命令空间。
 * 命题位语法（by[...]、when[p]、if[p]、∃×2、@simp、∵∴）的承载节点同时实现
 * [HirProofObligation]——命题与证明的唯一住所对语句形态的覆盖。
 */
sealed interface HirStmt : HirNode

/**
 * S-29 变量声明：name 的局部绑定符号、声明或推导类型（ADR-1 §3.3 #5）、
 * deepCopy=可变视图深拷贝点位（ADR-1 §3.3 #13——根文档 §4 拷贝 4 条的解析结论，
 * 后端按位生成根深拷贝而非浅引用）。
 */
data class HirVarStmt(
    val name: String,
    val symbol: SymbolRef? = null,
    val annotations: List<String> = emptyList(),
    val type: HirType? = null,
    val value: HirExpr,
    val destruct: List<String>? = null,
    val deepCopy: Boolean = false,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
    /**
     * t108（R3 S1，C-8 裁「v3 按需加字段」）：推导元信息最小集——省略类型由脚本模式推导填入本槽位时
     * inferred=true 并落 evidence（参与求解的使用处位置列表，P-11 §3.1「元变量必须可追溯」）。
     * 精确语义（defaulted/provenance 等）待 C-10 二次裁，本期只做最小集。默认态零产零发射——
     * HirJson 仅在 inferred||evidence 非空时写字段（既有样本产物零新字段，schema 保持 v3）。
     */
    val inferred: Boolean = false,
    val evidence: List<String> = emptyList(),
) : HirStmt

/**
 * S-30 赋值语句：mutableTarget=目标可赋值的核对结论（E-IMMUT-ASSIGN 判据——
 * 目标定位 mut 绑定，或在 unchecked 内）。
 */
data class HirAssign(
    val target: HirExpr,
    val value: HirExpr,
    val mutableTarget: Boolean = false,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt

/** S-23 axiom 语句：免验证公理注入（R-AXIOM：公理不经证明器直接点亮）。 */
data class HirAxiom(
    val props: List<HirProp>,
    override val proofContext: HirProofContext = HirProofContext(),
    override val rule: String? = null,
    override val verdict: HirVerdict? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt, HirProofObligation

/** S-27 by 语句：策略式前提注入（与 impl 的 by 委托在不同语法位置，无歧义）。 */
data class HirByStmt(
    val props: List<HirProp>,
    override val proofContext: HirProofContext = HirProofContext(),
    override val rule: String? = null,
    override val verdict: HirVerdict? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt, HirProofObligation

/** S-25 unchecked 语句：纯度/可变性逃逸，消费一条内层语句。 */
data class HirUnchecked(
    val inner: HirStmt,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt

/** S-30 表达式形态语句。 */
data class HirExprStmt(
    val expr: HirExpr,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt

/**
 * S-24 ∵∴ 证明链（唯一住所支柱之一）：form 为命题形态（⊤ 补全由解析层完成），
 * premises 为前提序列，conclusion 为结论命题。
 */
data class HirBecause(
    val form: HirProp,
    val premises: List<HirProp>,
    val conclusion: HirProp,
    override val proofContext: HirProofContext = HirProofContext(),
    override val rule: String? = null,
    override val verdict: HirVerdict? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt, HirProofObligation

/** S-21 命题 when：对析取式做 case 分析。 */
data class HirWhenProp(
    val disjunction: HirProp,
    val arms: List<HirWhenPropArm>,
    override val proofContext: HirProofContext = HirProofContext(),
    override val rule: String? = null,
    override val verdict: HirVerdict? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt, HirProofObligation

/** S-21 命题 when 分支（辅助记录）：条件命题 + 分支体。 */
data class HirWhenPropArm(
    val condition: HirProp,
    val body: List<HirStmt>,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirNode

/** S-20 命题 if：排中律——两支证明相同结论集（结论集核对由构造层完成）。 */
data class HirIfProp(
    val prop: HirProp,
    val thenStmts: List<HirStmt>,
    val elseStmts: List<HirStmt> = emptyList(),
    override val proofContext: HirProofContext = HirProofContext(),
    override val rule: String? = null,
    override val verdict: HirVerdict? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt, HirProofObligation

/** S-18 存在语句-引入：names 与 witnesses 逐位对应（多见证引入）。 */
data class HirExistsIntro(
    val names: List<String>,
    val witnesses: List<HirExpr>,
    val prop: HirProp,
    override val proofContext: HirProofContext = HirProofContext(),
    override val rule: String? = null,
    override val verdict: HirVerdict? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt, HirProofObligation

/** S-18 存在语句-消去：∃ x. P { 语句… }（块可省，仅单名）。 */
data class HirExistsElim(
    val name: String,
    val prop: HirProp,
    val body: List<HirStmt> = emptyList(),
    override val proofContext: HirProofContext = HirProofContext(),
    override val rule: String? = null,
    override val verdict: HirVerdict? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt, HirProofObligation

/** S-19 局部函数（块内 fun 声明：∀ 引入——定义即证明）。 */
data class HirLocalFun(
    val fn: HirFunDecl,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt

/** S-28 策略语句：@simp[...] 命名策略证明目标命题；strategy 为原始名。 */
data class HirStrategy(
    val strategy: String,
    val goal: HirProp,
    override val proofContext: HirProofContext = HirProofContext(),
    override val rule: String? = null,
    override val verdict: HirVerdict? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt, HirProofObligation

/** S-26 返回语句（发散判据由 [HirIf]/[HirWhen] 的 diverges 字段承载）。 */
data class HirReturn(
    val expr: HirExpr? = null,
    val label: String? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt

/** 语句层容错占位（AST ErrorStmt 的 HIR 镜像，S-1 §2 容错）。 */
data class HirErrorStmt(
    val message: String,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirStmt
