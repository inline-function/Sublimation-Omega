package sublimation.frontend.hir

import sublimation.frontend.diag.NodeId
import sublimation.frontend.diag.SourceRange

/**
 * HIR 类型层级（S-15 映射，ADR-1 §3.2）：AST 类型 + 解析结论——
 * 名称解析落于 symbol（指向声明），推导类型落于使用点节点的 type 字段（ADR-1 §3.3）。
 * 旧实现的类型代数（render/substT 推导函数）不住在节点体系（ADR-3 决策 2），
 * 类型代数与推导是构造层（semantic）的状态，非程序信息。
 */
sealed interface HirType : HirNode

/** 具名类型：args 为类型实参，symbol 指向声明（内建为 BUILTIN）。 */
data class HirNamedType(
    val name: String,
    val args: List<HirType> = emptyList(),
    val symbol: SymbolRef? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirType

/** 函数类型：context 为上下文角组归一的命题列表（命题面唯一住所），null=无角组。 */
data class HirFunType(
    val params: List<HirType>,
    val ret: HirType,
    val purity: HirPurity = HirPurity.PURE,
    val theory: List<HirTypeParam> = emptyList(),
    val context: List<HirProp>? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirType

/** 元组类型（≥2 项）。 */
data class HirTupleType(
    val items: List<HirType>,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirType

/** 可变类型（mut T：跨外壳与钓鱼例题的可变视图通道）。 */
data class HirMutType(
    val inner: HirType,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirType

/** 原子类型（atomic T：跨线程隔离传递）。 */
data class HirAtomicType(
    val inner: HirType,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirType

/**
 * 引用视图类型（t95 语法层中间态——零语义：仅承载解析形态，可写性/子类型/拷贝分派归 t97）。
 * JSON 头 "HirRefType"（schema 增量，v3 不变——新增节点为附加项，既有节点形态不动）。
 */
data class HirRefType(
    val inner: HirType,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirType

/** 并集类型（T | U：可错路径的判据）。 */
data class HirUnionType(
    val variants: List<HirType>,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirType

/** 限定类型（M::T：本期保留形态——解析层尚不折叠模块限定，ADR-1 §3.2）。 */
data class HirQualifiedType(
    val module: List<String>,
    val name: String,
    val args: List<HirType> = emptyList(),
    val symbol: SymbolRef? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirType

/** 类型层容错占位（AST ErrorType 的 HIR 镜像，S-1 §2 容错半成品落点）。 */
data class HirErrorType(
    val message: String,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirType

/** 特质规范内的 Self 占位（卷 TS-29）：专门节点，不得替换成具体类型或 unknown——
 * 满足判定发生在检查层（使用期 TS-18 / 结构化满足 TS-17），HIR 层只保真携带。 */
data class HirSelfType(
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirType
