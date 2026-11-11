package sublimation.frontend.ast

import sublimation.frontend.diag.SourceRange

/** S-42【模式】根层级：解构必须显式 is（无 is 的括号模式仅允许空参 A()——解析层判定）。 */
sealed interface Pattern : AstNode

/** S-42【字面量模式】：整数/字符串字面量等值比较。 */
data class PatLit(
    val value: Expr,
    override val pos: SourceRange,
) : Pattern

/** S-42【构造子解构模式】：Some(x)、E::A(_)（复合名由解析层折叠）；无参时 args 为空。
 *  t41（ADR-28 R-19）：paren 标记括号是否显式书写（A() 与裸名 A 的区分）——空构造子单例判定的解析层印记。 */
data class PatCtor(
    val name: String,
    val args: List<Pattern>,
    override val pos: SourceRange,
    val paren: Boolean = false,
) : Pattern

/** S-42【元组模式】：至少一个逗号；(p) 退化为子模式。 */
data class PatTuple(
    val items: List<Pattern>,
    override val pos: SourceRange,
) : Pattern

/** S-42【is 模式】：is T [as 绑定名]（类型/枚举路径/构造子解构三形，识别在解析层）。 */
data class PatIs(
    val type: Type,
    val bind: String?,
    override val pos: SourceRange,
) : Pattern

/** S-42【名字模式】：_（通配绑定，不可引用）与具名绑定 x（构造子或绑定变量由语义层裁定）。 */
data class PatBind(
    val name: String,
    override val pos: SourceRange,
) : Pattern

/** S-42【else 模式】：兜底分支，恒真。 */
data class PatElse(
    override val pos: SourceRange,
) : Pattern

/** 无主题 when 的条件分支占位模式（S-37，见 WhenArm.cond；pattern 不参与匹配）。由 data object 改带位置（同 ReturnSym）。 */
data class PatCond(
    override val pos: SourceRange,
) : Pattern

/** 解析失败处的模式占位节点（S-1 §2 容错）：保留位置与原因。 */
data class ErrorPattern(
    val message: String,
    override val pos: SourceRange,
) : Pattern
