package sublimation.frontend.diag

/**
 * 源位置（1 基行列；S-1 层间总契约 2 + D-9：源位置一律保留）。
 *
 * 宿主裁定（ADR-4 / D-11）：位置模型下沉 diag 横切包，diag 零依赖，
 * lexer/ast/parser/hir/semantic/json 任层单向依赖（S-2 §3.2）——
 * 词法错误的诊断在 ast 之前，diag 若依赖 ast 会把第 2 层类型拖进第 1 层的依赖图。
 */
data class SourcePos(val line: Int, val col: Int)

/**
 * 源位置区间：start 为节点首字符位置（必填），end 为末字符位置（可空——单点位置或末尾未知时 null）。
 *
 * 渲染口径（D-10）：「line:col」取 start；诊断 render 输出「[错误][1:2] CODE: msg」旧格式。
 */
data class SourceRange(val start: SourcePos, val end: SourcePos?)

/**
 * 节点身份（D-11/ADR-4）：文件内稳定唯一编号（构造期先序分配；纯函数输入→输出可复现，
 * S-1 层间总契约 3）。
 *
 * 方案裁定（ADR-1 §4.3 开放项，t6 落地）：取构造期序号（Int）而非路径式——单文件为处理单位
 * （S-1 §0），序号在构造期内即唯一且紧凑；JSON 产物中即数字（ADR-1 §3.7）。跨模块符号身份
 * 由 HIR 侧 SymbolRef 承载，不经 NodeId。
 */
typealias NodeId = Int
