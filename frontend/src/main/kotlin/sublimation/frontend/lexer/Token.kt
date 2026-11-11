package sublimation.frontend.lexer

/**
 * 词法单元五元组 (kind, text, line, col, precededBySpace)——《形式化文法》§1。
 *
 * 契约：
 * - line/col 为 token 首字符的源位置（1 基）；字符串可跨行，位置仍记首字符。
 * - [precededBySpace] 记录 token 前是否出现过 trivia（空白/BOM/行注释，L-7）。
 *   它是邻接消歧（D 层）的唯一词法输入：词法层只记录事实，消歧全部在语法层。
 */
data class Token(
    val kind: Kind,
    val text: String,
    val line: Int,
    val col: Int,
    val precededBySpace: Boolean,
)

/**
 * 词法诊断记录：非法字符、字符串未闭合、反引号跨行等词法错误的位置与说明。
 *
 * 契约（S-1 §1）：错误 token 不停机——lexer 产出恢复 token 并继续，错误恢复从第一层
 * 开始；诊断只携带位置与说明，不影响 token 流的产出。诊断体系（P-4）尚未拍板，本记录
 * 刻意保持最小（文件/行/列/信息），届时整体折叠进 diag 横切包。
 */
data class LexError(
    val fileName: String,
    val line: Int,
    val col: Int,
    val message: String,
    val code: String = "E-LEX",   // t68：E-BACKTICK-EMPTY 等特定词法码；默认保持既有 E-LEX
)
