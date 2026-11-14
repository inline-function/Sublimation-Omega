package sublimation.frontend.hir

import sublimation.frontend.diag.NodeId
import sublimation.frontend.diag.SourceRange

/**
 * 解析期留痕（D-9：脱糖留痕与 hints/supplements 随节点落盘的载体）。
 *
 * 契约：kind=DESUGAR 时 from/to 记录原形/目标形态标签，code 沿用脱糖规则编号（如 D-INTP）；
 * kind=HINT/SUPPLEMENT 对应旧体系四族中的两个非阻断族（D-10）。
 * 消费者：LSP hover、教学提示（H 族）、审计（D 族）、CLI -v（ADR-1 §3.3 #3）。
 */
data class HirNote(
    val kind: HirNoteKind,
    val code: String,
    val message: String,
    val pos: SourceRange,
    val from: String? = null,
    val to: String? = null,
    /** t160（R4-A0）：元素层深拷计数槽——D-DEEP-COPY 指令 note 携带的结构化预算提示
     *  （R-14 容器实参位下钻计数;默认 0 零发射，v3 加性纪律）。 */
    val deepElements: Int = 0,
)

/** 留痕种类：脱糖通道 + 旧体系非阻断两族（D-10 四族口径）。 */
enum class HirNoteKind { HINT, SUPPLEMENT, DESUGAR }

/**
 * HIR 节点公共契约（S-1 §3；ADR-1 §3.1.4「五件套」）：每个节点携带源位置
 * （S-1 层间总契约 2 + D-9）、节点身份与解析期留痕；名称解析结论与推导类型
 * 按节点种类齐备于各层级（ADR-1 §3.3 信息保留清单）。
 *
 * 层级（S-1 §3 命题与证明唯一住所——程序面与命题面在 HIR 分家）：
 * 程序面 [HirDecl] / [HirStmt] / [HirExpr] / [HirPattern] / [HirType]；
 * 命题面 [HirProp] / [HirTerm]；辅助记录（参数/构造子/分臂）同为节点。
 *
 * 全 val 不可变：构造完成后只读，改造一律复制构造（ADR-1 §3.1——
 * 旧实现 AST 原地突变与旁挂表是两类病根，约束从节点体系开始）。
 */
sealed interface HirNode {
    val nid: NodeId
    val origin: SourceRange
    val notes: List<HirNote>
}

/** 顶层条目容器（同 AST Entry：条目本身不携带身份——位置与五件套由所含声明/语句承载）。 */
sealed interface HirEntry

/** 工程模式：顶层声明条目。 */
data class HirDeclEntry(val decl: HirDecl) : HirEntry

/** 脚本模式：顶层语句条目。 */
data class HirStmtEntry(val stmt: HirStmt) : HirEntry

/**
 * HIR 编译单元（S-1 §3：语义、类型、证明的家园；前端最终产物 D-8，不再降级）。
 *
 * 契约：条目保序映射源码顺序（S-1 §3「与源码结构保持映射——诊断、跳转、LSP 依赖此性质」）；
 * origin 为文件级源位置（构造层由首/末条目位置合成，空文件取零点）。
 */
data class HirFile(
    override val nid: NodeId,
    override val origin: SourceRange,
    override val notes: List<HirNote> = emptyList(),
    val entries: List<HirEntry>,
) : HirNode
