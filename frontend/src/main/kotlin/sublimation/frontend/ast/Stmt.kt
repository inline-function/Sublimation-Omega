package sublimation.frontend.ast

import sublimation.frontend.diag.SourceRange

// ---------- 语句（S-16~S-30）----------

/** S-17【语句】根层级（块 S-16 为语句序列，表达式层见 Expr）。 */
sealed interface Stmt : AstNode

/** S-29【变量声明】：注解 var [解构] [: 类型] = 表达式；destruct 为元组解构分量名。 */
data class VarStmt(
    val name: String,
    val annotations: List<String>,
    val type: Type?,
    val value: Expr,
    val destruct: List<String>? = null,
    override val pos: SourceRange,
) : Stmt

/** S-30【赋值语句】：仅当左值为名字引用/字段表达式；其余位置的 = 是命题等式中缀（解析层分派）。 */
data class AssignStmt(
    val target: Expr,
    val value: Expr,
    override val pos: SourceRange,
) : Stmt

/** S-23【axiom 语句】：axiom p₁, p₂,…——公理注入（顶层与函数内通用，不经证明器）。 */
data class AxiomStmt(
    val props: List<Expr>,
    override val pos: SourceRange,
) : Stmt

/** S-27【by 语句】：by [命题列表]——策略式前提注入（与 impl 的 by 委托在不同语法位置，无歧义）。 */
data class ByStmt(
    val props: List<Expr>,
    override val pos: SourceRange,
) : Stmt

/** S-25【unchecked 语句】：unchecked <语句>——纯度/可变性逃逸，消费一条内层语句。 */
data class UncheckedStmt(
    val inner: Stmt,
    override val pos: SourceRange,
) : Stmt

/** S-30【赋值或表达式语句】：表达式形态。 */
data class ExprStmt(
    val expr: Expr,
    override val pos: SourceRange,
) : Stmt

/** S-24【因为所以】：∵ 前提₁…ₙ ∴ 结论；裸形（无逗号无 ∴）的 ⊤ 补全由解析层完成。 */
data class BTSStmt(
    val form: Expr,
    val premises: List<Expr>,
    val conclusion: Expr,
    override val pos: SourceRange,
) : Stmt

/** S-21【命题 when】：when[p] { 分支* }——对析取式做 case 分析，体必须是块。 */
data class WhenPropStmt(
    val disjunction: Expr,
    val arms: List<WhenPropArm>,
    override val pos: SourceRange,
) : Stmt

/** S-21【命题 when 分支】：条件 => 块（分支箭头可选，建议恒写）。 */
data class WhenPropArm(
    val condition: Expr,
    val body: List<Stmt>,
    val pos: SourceRange,
)

/** S-28【策略语句】：@simp[(3*2+6)=12]——命名策略证明目标命题；strategy 为原始名。 */
data class StrategyStmt(
    val strategy: String,
    val goal: Expr,
    override val pos: SourceRange,
) : Stmt

/** S-18【存在语句-引入】：∃ x₁ = t₁, x₂ = t₂. P（多见证引入）。 */
data class ExistIntroStmt(
    val names: List<String>,
    val witnesses: List<Expr>,
    val prop: Expr,
    override val pos: SourceRange,
) : Stmt

/** S-18【存在语句-消去】：∃ x. P { 语句… }（块可省，仅单名）。 */
data class ExistsElimStmt(
    val name: String,
    val prop: Expr,
    val body: List<Stmt>,
    override val pos: SourceRange,
) : Stmt

/** S-19【局部函数】：块内 fun 声明（∀ 引入：定义即证明）。 */
data class FunStmt(
    val fn: FunDecl,
    override val pos: SourceRange,
) : Stmt

/** S-20【命题 if】：if[p] { } else { }——排中律，两支证明相同结论集。 */
data class IfPropStmt(
    val prop: Expr,
    val thenStmts: List<Stmt>,
    val elseStmts: List<Stmt>,
    override val pos: SourceRange,
) : Stmt

/** S-26【返回语句】：return [表达式] [标签]；label 须紧贴 return（D-18），裸形判据在解析层。 */
data class ReturnStmt(
    val expr: Expr?,
    val label: String? = null,
    override val pos: SourceRange,
) : Stmt

/** 解析失败处的语句占位节点（S-1 §2 容错）：保留位置与原因，可挽救子树保留在相邻节点。 */
data class ErrorStmt(
    val message: String,
    override val pos: SourceRange,
) : Stmt
