package sublimation.frontend.ast

import sublimation.frontend.diag.SourceRange

/**
 * 类型表示（S-15）。零语义（S-1 §2）：仅承载语法结构——
 * 旧实现在此层内嵌的类型代数（render/substT）与推导占位（InferMarker）已剔除，
 * 类型代数与推导归属 semantic 层（S-2 §2），类型解析结论入 HIR 字段（ADR-1-HIR设计 §3.3）。
 * 与旧实现的另一偏差：类型节点从无位置改为携带源位置（D-9：诊断/LSP 需要类型级定位）。
 */
sealed interface Type : AstNode

/** S-15【具名类型】：名义类型 / 类型应用（Nat、Optional[Nat]、类型参数引用 T）。 */
data class NamedType(
    val name: String,
    val args: List<Type> = emptyList(),
    override val pos: SourceRange,
) : Type

/**
 * S-15【函数类型】：(参数) => 返回。
 * theory 为多态函数类型前缀（[T](T) => T）；context 为函数类型上下文（(T)<$1 ∈ xs> => Null，
 * 命题表达式列表，null = 无上下文）；purity 为 @impure 原始文本（"" = 无注解）。
 */
data class FunType(
    val params: List<Type>,
    val ret: Type,
    val purity: String = "",
    val theory: List<TypeParam> = emptyList(),
    val context: List<Expr>? = null,
    override val pos: SourceRange,
) : Type

/**
 * S-15【元组类型】：恒 ≥2 项——单项是分组括号（(T) ≡ T，I4 裁决），零元组/单元组走具名形态。
 */
data class TupleType(
    val items: List<Type>,
    override val pos: SourceRange,
) : Type

/** S-15【类型注解】@mut T：可变视图类型（视图语义 M-1/M-2 判定在语义层）。 */
data class MutType(
    val inner: Type,
    override val pos: SourceRange,
) : Type

/** S-15【类型注解】@atomic T：原子读写声明（编译期声明，运行时透明反射内层类型）。 */
data class AtomicType(
    val inner: Type,
    override val pos: SourceRange,
) : Type

/**
 * S-15【类型注解】@ref T：引用视图类型（t95 语法层中间态——**零语义**：可解析、可走 HIR/JSON，
 * 可写性/子类型/拷贝分派/命题待遇四规则是 t97 的域，本节点不携带任何行为）。
 * 叠加序 = 书写序：@ref @mut T → RefType(MutType(T))（与 @mut 注解通道同构，不规范化顺序）。
 */
data class RefType(
    val inner: Type,
    override val pos: SourceRange,
) : Type

/** S-15【和类型尾】：A | B | C；构造即展平——联合是变体集合（I4 裁决）。 */
data class UnionType(
    val variants: List<Type>,
    override val pos: SourceRange,
) : Type

/** S-15【限定类型】：core.Point——module 为路径段（不含末段符号名）；本期单文件，模块折叠留后期（S-2 §2）。 */
data class QualifiedType(
    val module: List<String>,
    val name: String,
    val args: List<Type> = emptyList(),
    override val pos: SourceRange,
) : Type

/** 解析失败处的类型占位节点（S-1 §2 容错）：形参/返回区等类型位破裂时由解析层填入。 */
data class ErrorType(
    val message: String,
    override val pos: SourceRange,
) : Type
