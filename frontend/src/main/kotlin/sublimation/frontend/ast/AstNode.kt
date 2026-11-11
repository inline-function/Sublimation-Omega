package sublimation.frontend.ast

import sublimation.frontend.diag.SourceRange

/**
 * AST 节点公共契约（S-1 §2）：**纯语法结构、零语义**——节点不认识任何名字、类型与证明；
 * 每个节点携带源位置区间（S-1 层间总契约 2；D-9：源位置一律保留）。
 * 五个层级：[Decl] / [Stmt] / [Expr] / [Pattern] / [Type]，均 seal 于本接口。
 */
sealed interface AstNode {
    val pos: SourceRange
}

/**
 * S-2【顶层条目】容器：工程模式顶层为声明与 axiom 语句，脚本模式为任意语句与声明（S-1）。
 * 条目本身不是源位置节点（位置由各条目自身携带）。
 */
sealed interface Entry

/** S-2：声明条目 */
data class DeclEntry(val decl: Decl) : Entry

/** S-2：语句条目 */
data class StmtEntry(val stmt: Stmt) : Entry

/**
 * S-1【编译单元】：顶层条目序列，保序映射源码顺序——诊断、跳转与 LSP 依赖此性质（S-1 §3）。
 * 文件级容器：本节点不携带源位置（各条目自携；HIR 的文件级 origin 由解析层合成）。
 */
data class FileAst(val entries: List<Entry>)
