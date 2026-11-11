package sublimation.frontend.ast

import sublimation.frontend.diag.SourceRange

// ---------- 声明（S-5~S-14）----------

/** S-5【声明】根层级：函数 / 特质 / 结构体 / 类型别名（P-9：枚举/实现/命题定义废除，见附录 B）。 */
sealed interface Decl : AstNode

/**
 * S-7【函数声明】：fun 名 [理论参数] [形参表] [头部角组] [: 类型 [角组]] [体]（顺序固定，§5.3）。
 * 体为 null = 签名（特质成员 / 定理声明）；preps/posts 为返回区前后的上下文角组，
 * 其命题含义由 HIR 解释（AST 零语义，S-1 §2）。constraints 为约束表（D-13，V1 只登记不校验）。
 */
data class FunDecl(
    val name: String,
    val annotations: List<String>,              // S-6【注解】：原始名逐字保留，语义角色由语义层解释
    val theory: List<TheoryParam>,              // S-8【理论参数】
    val params: List<Param>,                    // S-8【形参表】
    val preps: List<Expr>,                      // S-7 头部角组（前置上下文）
    val retType: Type?,                         // S-7 返回区类型
    val posts: List<Expr>,                      // S-7 返回区后置角组（上下文）
    val body: Expr?,                            // S-7 函数体：= 表达式 | 块（二者互斥，D-11）
    val constraints: List<Expr> = emptyList(),  // D-13 约束表（fun f<score <= 100>）
    override val pos: SourceRange,
    // P-8/A（卷 TS-28）：扩展函数接收者类型（null = 自由函数，语法形态 TS-9 说明）；
    // 末位以兼容既有位置构造点——A 阶段默认 null 保持现有行为，parser 填充由 t28 落。
    val receiver: Type? = null,
) : Decl

/** S-9【特质声明】：trait 名 [理论参数] [: 父特质链] [{ 成员 }]；无成员可省花括号。 */
data class TraitDecl(
    val name: String,
    val theory: List<TheoryParam>,
    val annotations: List<String>,
    val members: List<FunDecl>,
    val parents: List<Type>,
    override val pos: SourceRange,
) : Decl

/** S-10【结构体声明】：struct 名 [理论参数] [形参表] [角组]；props 为伴生命题（struct P(n: Nat) <n ≠ 0>）。 */
data class StructDecl(
    val name: String,
    val theory: List<TheoryParam>,
    val fields: List<Param>,
    val annotations: List<String>,
    val props: List<Expr>,
    override val pos: SourceRange,
) : Decl

/** S-13【类型别名】：type 名 [理论参数] = 类型（P-9/B.4：右值为完整类型，含联合与自引用）。 */
data class TypeAliasDecl(
    val name: String,
    val theory: List<TheoryParam>,
    val target: Type,
    override val pos: SourceRange,
) : Decl

/**
 * 解析失败处的声明占位节点（S-1 §2 容错）：保留位置与失败原因。
 * 可挽救的局部子树保留在相邻节点字段中，不由本节点承载。
 */
data class ErrorDecl(
    val message: String,
    override val pos: SourceRange,
) : Decl

// ---------- 理论参数与形参（S-8）----------

/** S-8【理论参数】根层级：命题参数 | 类型参数。 */
sealed interface TheoryParam

/**
 * S-8【类型参数】：[T] / [T: Show] / [F[_]] arity=1 / [F[_, _]] arity=2。
 * constraint 为特质约束的原始名（零语义：AST 不解析它）；arity 是类型构造子实参个数。
 */
data class TypeParam(
    val name: String,
    val constraint: String?,
    val arity: Int = 0,
    val pos: SourceRange,
) : TheoryParam

/** S-8【命题参数】：[@p]——命题元变量。 */
data class PropParam(
    val name: String,
    val pos: SourceRange,
) : TheoryParam

/**
 * S-8【形参】：注解? [解构] 名: 类型 [= 默认值]。
 * destruct 为元组解构形参分量名（fun f((a, b): Point)），此时 name 为占位；
 * 默认值仅 struct 字段允许（函数形参带默认值由语义层报错，非本层职责）。
 */
data class Param(
    val name: String,
    // t113（R3 S1→S2 先决件，t112 §1.1）:形参类型标注可省——
    // 解析接受省略（无 E-SYNTAX 诊断,模式未知 D-12）;工程模式硬拒 E-OMITTED-TYPE 是语义层 R-13 的活;
    // 结构体字段同节点随改。FunDecl.retType / VarStmt.type 本就可空（扩 S2 槽位）。
    val type: Type?,
    val annotations: List<String> = emptyList(),
    val default: Expr? = null,
    val destruct: List<String>? = null,
    val pos: SourceRange,
)
