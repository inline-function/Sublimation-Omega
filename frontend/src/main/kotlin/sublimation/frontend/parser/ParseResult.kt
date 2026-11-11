package sublimation.frontend.parser

import sublimation.frontend.ast.AstNode
import sublimation.frontend.ast.Decl
import sublimation.frontend.ast.Expr
import sublimation.frontend.ast.FileAst
import sublimation.frontend.ast.Stmt
import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.Severity
import sublimation.frontend.diag.SourcePos
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.lexer.LexError
import sublimation.frontend.lexer.Lexer

/**
 * 解析诊断直接复用 diag 横切包的 [Diag]/[Severity]（D-10 诊断模型；S-2 §3.2 parser 单向依赖 diag，
 * 层间总契约 2：lexer/parser/语义同袋）。解析层的码族口径：
 * - E-SYNTAX：语法错误（容错恢复产出 Error* 节点并同时记诊断，S-1 §2）
 * - E-LEX：词法错误（[LexError] 在入口处并入解析诊断袋）
 * - H-ARITH-PREC：算术链同类判定劝告（t71/ADR-46：同一括号层级内混用不同类算符时劝加括号，不报错）
 * 留痕类信息（D-INFIX/D-TRAILING）走节点级 [ParseNote] 通道（不进诊断袋）。
 */

/**
 * 解析期脱糖留痕（D-9：解析期信息一律随节点落盘）：挂在被脱糖产出的 AST 节点上，
 * HIR 构造期按节点对应搬到 HirNode.notes（ADR-1-HIR设计 §3.5 旁挂通道）。
 * 码：D-INFIX（中缀糖展开）、D-TRAILING（尾随 lambda 实参追加）。
 */
data class ParseNote(
    val code: String,
    val message: String,
    val pos: SourceRange?,
)

/**
 * 一次解析的完整产物（ADR-1-HIR设计 §3.5）：AST + 节点级留痕 + 诊断袋。
 * 词法诊断在入口处并入 diag——层间总契约 2（lexer/parser/语义同袋，ADR-1 §3.6）。
 */
data class ParseResult(
    val file: FileAst,
    val notes: Map<AstNode, List<ParseNote>>,
    val diag: List<Diag>,
)

/** 片段入口的宿主契约异常：片段类型不符或「整段耗尽」被破坏时抛出。 */
class ParseFailure(val fileName: String, val pos: SourceRange, message: String) :
    RuntimeException("[语法][$fileName:${pos.start.line}:${pos.start.col}] $message")

/** 词法诊断并入解析诊断袋（E-LEX 码族；位置起点同 D-10 渲染口径）。 */
internal fun LexError.toDiag(): Diag =
    Diag(Severity.ERROR, code, SourceRange(SourcePos(line, col), null), message)

// ============ 三入口（§2.1）：工程 / 脚本 / 片段 ============

/** 工程模式解析（带留痕）：顶层只允许声明与 axiom 语句（S-2/S-3）。 */
fun parseSourceWithNotes(src: String, fileName: String = "<anon>"): ParseResult {
    val lexer = Lexer(src, fileName)
    val p = Parser(lexer.tokenize(), fileName)
    return ParseResult(p.parseFile(), p.notes(), p.diag() + lexer.errors.map { it.toDiag() })
}

/** 工程模式解析：仅取 AST（诊断经 *WithNotes 入口取）。 */
fun parseSource(src: String, fileName: String = "<anon>"): FileAst = parseSourceWithNotes(src, fileName).file

/**
 * 脚本模式解析（带留痕）：顶层允许任意语句与声明（S-1 脚本模式）。
 */
fun parseScriptSourceWithNotes(src: String, fileName: String = "<script>"): ParseResult {
    val lexer = Lexer(src, fileName)
    val p = Parser(lexer.tokenize(), fileName, scriptMode = true)
    return ParseResult(p.parseFile(), p.notes(), p.diag() + lexer.errors.map { it.toDiag() })
}

/** 脚本模式解析：仅取 AST。 */
fun parseScriptSource(src: String, fileName: String = "<script>"): FileAst =
    parseScriptSourceWithNotes(src, fileName).file

/** 片段解析：单条声明（注解处理器宿主用，要求整段耗尽且无语法错——宿主契约，失败抛 [ParseFailure]）。 */
fun parseDeclSnippet(src: String, fileName: String = "<snippet>"): Decl {
    val p = Parser(Lexer(src, fileName).tokenize(), fileName, scriptMode = true, strict = true)
    return p.snippetDecl()
}

/** 片段解析：函数体——块体或裸表达式（等号可写可不写），要求整段耗尽。 */
fun parseFunBodySnippet(src: String, fileName: String = "<snippet>"): Expr {
    val p = Parser(Lexer(src, fileName).tokenize(), fileName, scriptMode = true, strict = true)
    return p.snippetFunBody()
}

/** 片段解析：语句序列（零条或多条），要求整段耗尽。 */
fun parseStmtsSnippet(src: String, fileName: String = "<snippet>"): List<Stmt> {
    val p = Parser(Lexer(src, fileName).tokenize(), fileName, scriptMode = true, strict = true)
    return p.snippetStmts()
}
