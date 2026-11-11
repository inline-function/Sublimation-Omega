package sublimation.frontend.hir

import sublimation.frontend.diag.NodeId
import sublimation.frontend.diag.SourceRange

/**
 * HIR 模式层级（S-42 映射，ADR-1 §3.2）：when 分臂与解构目标的模式空间。
 * 绑定型模式的局部符号落于 symbol（名称解析结论）。
 */
sealed interface HirPattern : HirNode

/** 字面量模式（1 / "s" / true 等；value 为程序面字面量表达式）。 */
data class HirPatLit(
    val value: HirExpr,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirPattern

/** 构造子模式：ctor 为构造子解析结论。 */
data class HirPatCtor(
    val name: String,
    val args: List<HirPattern> = emptyList(),
    val ctor: SymbolRef? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirPattern

/** 元组模式。 */
data class HirPatTuple(
    val items: List<HirPattern> = emptyList(),
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirPattern

/** is 类型测模式：bind 为绑定名，bindSymbol 为其局部符号（智能转换入口）。 */
data class HirPatIs(
    val type: HirType,
    val bind: String? = null,
    val bindSymbol: SymbolRef? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirPattern

/** 名字绑定模式：symbol 为局部绑定解析结论。 */
data class HirPatBind(
    val name: String,
    val symbol: SymbolRef? = null,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirPattern

/** else 哨兵（无主题 when 的兜底臂）。 */
data class HirPatElse(
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirPattern

/** 条件哨兵（无主题 when 的条件臂由本哨兵占模式位，条件表达式在分臂 cond 字段）。 */
data class HirPatCond(
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirPattern

/** 模式层容错占位（AST ErrorPattern 的 HIR 镜像，S-1 §2 容错）。 */
data class HirErrorPattern(
    val message: String,
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
) : HirPattern
