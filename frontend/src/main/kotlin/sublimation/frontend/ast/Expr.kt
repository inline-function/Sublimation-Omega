package sublimation.frontend.ast

import sublimation.frontend.diag.SourceRange

/**
 * 表达式根层级（S-31）：四层结构（一元/后缀/初等/中缀）全部左结合、**无优先级**（D-10）——
 * 分层由解析层的括号结构承载，本层只记录形状。中缀右操作数只取一元表达式（解析层约束）。
 */
sealed interface Expr : AstNode

/** S-34【初等项-整数字面量】：值保留原文（D-9：供后端 codegen 与解释器值构造）。 */
data class IntLit(
    val value: String,
    override val pos: SourceRange,
) : Expr

/** S-34【初等项-浮点字面量】：1.0；值保留原文给后端。 */
data class FloatLit(
    val value: String,
    override val pos: SourceRange,
) : Expr

/** S-34【初等项-字符串字面量】：最终字面量——插值由解析期脱糖（D-INTP）展开后进入本节点。 */
data class StrLit(
    val value: String,
    override val pos: SourceRange,
) : Expr

/** S-34【初等项-名字引用】：原始名（零语义：名称解析在 HIR 层，未解析者落诊断 + 半成品节点）。 */
data class NameRef(
    val name: String,
    override val pos: SourceRange,
) : Expr

/**
 * S-34【初等项-$】：$ 返回值符号。
 * 与旧实现的偏差：旧为 data object（无逐点位置），本层改为携带位置的 data class——
 * 每个使用点（token）都有独立位置，S-1 §2「每个 token/节点携带源位置」方落实（D-9）。
 */
data class ReturnSym(
    override val pos: SourceRange,
) : Expr

/** S-34【初等项-$n】：第 n 形参引用（$ 后数字直接转整型；$0 词法合法，语义有效性待核）。 */
data class ParamRefExpr(
    val index: Int,
    override val pos: SourceRange,
) : Expr

/** S-34【初等项-⊤】：命题真；命题位形态由 HIR 归一（同 ReturnSym，由 data object 改带位置）。 */
data class TopExpr(
    override val pos: SourceRange,
) : Expr

/** S-34【初等项-⊥】：命题假（同上）。 */
data class BotExpr(
    override val pos: SourceRange,
) : Expr

/** S-31【双目运算】：op 为运算符原始文本（ASCII/Unicode 等价归一在命题层，§1.2 注 5）。 */
data class BinExpr(
    val op: String,
    val left: Expr,
    val right: Expr,
    override val pos: SourceRange,
) : Expr

/** S-32【一元表达式】：- / ! / ¬ / not。 */
data class UniExpr(
    val op: String,
    val operand: Expr,
    override val pos: SourceRange,
) : Expr

/**
 * R3 S6 t101【借用表达式】&v：脚本模式参数表达式起始位的前缀借用（R-01 调用点传参开关）。
 * 语法层中间态——零语义（可解析、无效果）：视图推导/冗余 warn/copyDispatch 联动是 t96/t99 的域。
 * R-04：仅变量形态（operand 必为 NameRef）；&(a+b)、&& 双重借用由解析层拒绝。
 * 中缀 & 合取（BinExpr "&"）逐字不变，本节点只承载前缀形态。
 */
data class BorrowExpr(
    val operand: Expr,
    override val pos: SourceRange,
) : Expr

/** S-31【中缀 is】：a is T 运行时类型测（D-4：仅同行作中缀类型测）。 */
data class TypeTestExpr(
    val target: Expr,
    val type: Type,
    override val pos: SourceRange,
) : Expr

/**
 * ifis 智能解构哨兵（§2.8）：if 条件位的 is 模式，parseIf 立即脱糖为 when——
 * 正常 AST 不应出现，保留为解析层过渡节点（旧实现兼容；HIR 无对应节点，ADR-1 §3.2 映射表）。
 */
data class IsPatExpr(
    val target: Expr,
    val pat: Pattern,
    override val pos: SourceRange,
) : Expr

/** S-33【后缀-Task 块】：Task { 语句… }——体是代码块（闭包语义），不是 lambda。 */
data class TaskExpr(
    val body: BlockExpr,
    override val pos: SourceRange,
) : Expr

/** S-33【后缀-Flow 块】：Flow { 语句… }——惰性冷流。 */
data class FlowExpr(
    val body: BlockExpr,
    override val pos: SourceRange,
) : Expr

/** S-33【后缀-调用】：位置实参 + 命名实参通道（D-14）混合，尾随 lambda 已并入 args 末位（D-9 留痕在解析层）。 */
data class CallExpr(
    val callee: Expr,
    val args: List<Expr>,
    val namedArgs: Map<String, Expr> = emptyMap(),
    override val pos: SourceRange,
) : Expr

/** S-33【后缀-实例化/显式供给】：紧贴的 <...>（p<v>、witness<torch>()）/ [...]（t144/R-26.1 调用位类型实参 f[T]）；角色由语义层判定。 */
data class InstExpr(
    val target: Expr,
    val terms: List<Expr>,
    val bracket: Boolean = false,   // t144（R-26.1）：true = 调用位方括号类型实参通道（f[T](args)）；false = 尖括号命题通道（p<v>、f<p>()）
    override val pos: SourceRange,
) : Expr

/** S-33【后缀-字段访问】：.name（D-3：点前无空格，或点在行首）。 */
data class FieldExpr(
    val target: Expr,
    val name: String,
    override val pos: SourceRange,
) : Expr

/** S-33【后缀-引用】：o::f（E::A 的构造子复合名折叠在解析层，D-16）。 */
data class FunRefExpr(
    val target: Expr,
    val name: String,
    override val pos: SourceRange,
) : Expr

/** S-34【符号调用】：<(1, 2) 等算符前缀调用形态（作为普通函数名调用）。 */
data class SymbolCallExpr(
    val op: String,
    val args: List<Expr>,
    override val pos: SourceRange,
) : Expr

/**
 * S-36【lambda】：{ 参数表 => 体 } / { 隐式体 }。params 为空表或 ["_"] + implicitIt=true =
 * 解析器合成的隐式参数糖——**糖在语法层零语义**：`_` 是否成参（以及 arity）由语义层按
 * 期望类型定（R-08 / R-10，Elaborator 侧）：单参期望位 `_` 启用（参数可不用，Kotlin 对齐）、
 * 零参期望位成零参、多参期望须显式参数表;无期望位走模式分立（工程拒 / 脚本用法自定）。
 * 解构参数编码为特殊名 "(a,b)"；零参形态参数表为空但箭头必写。
 */
data class LambdaExpr(
    val params: List<String>,
    val body: Expr,
    val implicitIt: Boolean = false,
    override val pos: SourceRange,
) : Expr

/** S-34【元组字面量】：≥2 项（单项是括号表达式，不产出本节点）。 */
data class TupleExpr(
    val items: List<Expr>,
    override val pos: SourceRange,
) : Expr

/** S-33【命名字段构造】：A(name = e, …)（D-14 前瞻判定在解析层）。 */
data class StructCtorExpr(
    val struct: String,
    val assigns: List<Pair<String, Expr>>,
    override val pos: SourceRange,
) : Expr

/** S-31【中缀 by】：e by [q]——后缀断言式。 */
data class ByExpr(
    val target: Expr,
    val props: List<Expr>,
    override val pos: SourceRange,
) : Expr

/** S-35【var 表达式】：声明即表达式（var n = var m = 0 合法）。 */
data class VarExpr(
    val name: String,
    val annotations: List<String>,
    val type: Type?,
    val value: Expr,
    override val pos: SourceRange,
) : Expr

/** S-34【匿名函数】：fun _[T](a: T): T = a 或省略式 fun[T](a:T):T = a；name 为 _ 或空串（不入符号表）。 */
data class AnonFunExpr(
    val name: String,
    val theory: List<TheoryParam>,
    val params: List<Param>,
    val preps: List<Expr>,
    val retType: Type?,
    val posts: List<Expr>,
    val body: Expr?,
    override val pos: SourceRange,
) : Expr

/** S-16【块表达式】：语句序列作为表达式值；tailExpr 为末条 ExprStmt 的表达式（结构性投影）。 */
data class BlockExpr(
    val stmts: List<Stmt>,
    override val pos: SourceRange,
) : Expr {
    val tailExpr: Expr? get() = (stmts.lastOrNull() as? ExprStmt)?.expr
}

/** S-34【if 表达式】：值位条件（命题位 if 走 IfPropStmt，方括号/圆括号判据 D-12 在解析层）。 */
data class IfExpr(
    val cond: Expr,
    val thenBlock: BlockExpr,
    val elseBlock: BlockExpr?,
    override val pos: SourceRange,
) : Expr

/** S-37【when 表达式】：subject 为 null = 无主题布尔分支；subjectBinding 为 when(var e = 0) 绑定主题。 */
data class WhenExpr(
    val subject: Expr?,
    val subjectBinding: String?,
    val arms: List<WhenArm>,
    override val pos: SourceRange,
) : Expr

/**
 * S-37【when 分支】：模式/条件 => 分支体；cond 为无主题 when 的条件（此时 pattern 为 PatCond 哨兵）。
 * 不可变（val pattern）：模式归一化（解构槽位旧名 → 相等比较）在 HIR 构造期以拷贝完成——
 * 替代旧实现 checker 的原地改写（ADR-1-HIR设计 §3.1 病根对策）。
 */
data class WhenArm(
    val pattern: Pattern,
    val guard: Expr?,
    val body: Expr,
    val cond: Expr? = null,
    val pos: SourceRange,
)

/** S-40【量词表达式】：q ∈ {∀, ∃, ∃!}（ASCII 别名由解析层归一为 Unicode 后填入）；names 为量词变量列表。 */
data class QuantExpr(
    val q: String,
    val names: List<String>,
    val body: Expr,
    override val pos: SourceRange,
) : Expr

/** 解析失败处的表达式占位节点（S-1 §2 容错）：保留位置与原因，可挽救子树保留在相邻节点。 */
data class ErrorExpr(
    val message: String,
    override val pos: SourceRange,
) : Expr
