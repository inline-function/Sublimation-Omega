package sublimation.frontend.cli

import java.io.File
import java.io.PrintStream
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.lexer.Lexer
import sublimation.frontend.lexer.Token
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import kotlin.system.exitProcess

internal const val USAGE = """sublimation-frontend — Sublimation 前端编译器（词法 / 语法 / HIR / JSON 产物）
用法:
  tokens <file>                        打印 token 流（含邻接标记）
  parse <file> [--script]              词法 + 语法分析，打印语法树
  check <file> [--script] [-v]         前端全分析（HIR 构造含名称解析与证明校验），诊断分级输出
  compile <file> [-o <out.json>] [--script] [-v] [--no-degrade] [--no-lift]   全管线：源代码 → HIR → JSON 文件写盘
选项:
  --script    按脚本模式解析（顶层允许任意语句与声明；默认工程模式）
              输入文件扩展名 .subs 等价于 --script（R3-S0）；.subl 保持工程模式
  -v          输出提示/补充级诊断（默认隐藏补充族，与旧 CLI 同口径）
  -o <path>   JSON 产物路径（compile；默认 <file>.hir.json）
  --no-degrade  脚本模式 R-02 退化即失败（CI 可用性门；工程模式恒硬拒）
  --no-lift     脚本模式 R-16 上文上提即失败（CI 可用性门，双口形态同 --no-degrade）
退出码：0 成功；1 存在 ERROR 级诊断；2 用法错误。"""

private val COMMANDS = setOf("tokens", "parse", "check", "compile")

fun main(args: Array<String>) {
    exitProcess(runCli(args))
}

/**
 * CLI 执行体（返回退出码）：[main] 的可测试形态——输出流可注入，退出码语义见 USAGE。
 * 子命令风格对齐旧 cli/Main.kt 的 parse/tokens/check（仅取前端相关；无代码生成）。
 */
fun runCli(args: Array<String>, out: PrintStream = System.out, err: PrintStream = System.err): Int {
    if (args.isEmpty() || args[0] !in COMMANDS) { err.println(USAGE); return 2 }
    val cmd = args[0]
    val (positional, opts) = parseArgs(args.drop(1), err) ?: return 2
    if (positional.isEmpty()) { err.println(USAGE); return 2 }
    if (positional.size > 1) { err.println("[错误] 只接受单个源文件"); return 2 }
    val srcFile = File(positional[0])
    if (!srcFile.exists()) { err.println("[错误] 文件不存在: ${positional[0]}"); return 2 }
    // R3-S0（t100，S-1 §5）：.subs 扩展名自动开 scriptMode（等价 --script）；.subl 与其他扩展名保持默认工程模式。
    val scriptMode = opts.script || positional[0].endsWith(".subs")
    // t131（S7 / R-22）：目录即模块——compile/check 支持目录形态（.settings 挂载层是解析前层，
    // parse/tokens 按单文件精度工作，目录形态不支持）。
    if (srcFile.isDirectory) {
        if (cmd != "compile" && cmd != "check") {
            err.println("[错误] 目录形态仅支持 compile/check（模块挂载是解析前层——parse/tokens 需单源文件）")
            return 2
        }
        val r = compileModuleFrontend(srcFile, scriptMode, opts.noDegrade, opts.noLift)
        return when (cmd) {
            "check" -> {
                report(r.main.diag, r.mainSource, opts.verbose, out)
                r.children.forEach { out.println("[模块 ${it.unitPath}]"); report(it.output.diag, it.source, opts.verbose, out) }
                if (!r.ok) { err.println("[错误] 前端检查未通过"); 1 }
                else { out.println("[完成] 前端检查通过：主模块 + ${r.children.size} 个挂载模块"); 0 }
            }
            "compile" -> {
                val target = File(opts.output ?: srcFile.path + ".hir.json")
                target.writeText(r.main.json, Charsets.UTF_8)
                r.children.forEach { File(childJsonName(target.path, it.unitPath)).writeText(it.output.json, Charsets.UTF_8) }
                report(r.main.diag, r.mainSource, opts.verbose, out)
                r.children.forEach { out.println("[模块 ${it.unitPath}]"); report(it.output.diag, it.source, opts.verbose, out) }
                if (!r.ok) { err.println("[错误] 前端检查未通过，已生成带诊断产物 ${target.path}（${r.main.json.length} 字节）"); 1 }
                else { out.println("[完成] 已生成 ${target.path}（${r.main.json.length} 字节）+ ${r.children.size} 个挂载模块产物"); 0 }
            }
            else -> 2
        }
    }
    val source = srcFile.readText(Charsets.UTF_8)
    val unitPath = srcFile.name
    return when (cmd) {
        "tokens" -> {
            Lexer(source, unitPath).tokenize().forEach { out.println(tokenLine(it)) }
            0
        }
        "parse" -> {
            val pr = if (scriptMode) parseScriptSourceWithNotes(source, unitPath)
            else parseSourceWithNotes(source, unitPath)
            val bag = DiagBag().apply { absorbAll(pr.diag) }
            out.print(astTreeString(pr.file))
            report(bag, source, opts.verbose, out)
            if (bag.hasError) { err.println("[错误] 语法分析未通过"); 1 } else 0
        }
        "check" -> {
            val r = compileFrontend(source, unitPath, scriptMode, opts.noDegrade, opts.noLift)
            report(r.diag, source, opts.verbose, out)
            if (r.diag.hasError) { err.println("[错误] 前端检查未通过"); 1 }
            else { out.println("[完成] 前端检查通过"); 0 }
        }
        "compile" -> {
            val r = compileFrontend(source, unitPath, scriptMode, opts.noDegrade, opts.noLift)
            val target = File(opts.output ?: srcFile.path + ".hir.json")
            target.writeText(r.json, Charsets.UTF_8)
            report(r.diag, source, opts.verbose, out)
            // D-9 半成品原则：即便存在 ERROR 级诊断，JSON 产物也照常写盘
            // （envelope 的 ok 旗标携带判定结论）；退出码仍反映 ERROR，供构建脚本判定。
            if (r.diag.hasError) {
                err.println("[错误] 前端检查未通过，已生成带诊断产物 ${target.path}（${r.json.length} 字节）")
                1
            } else {
                out.println("[完成] 已生成 ${target.path}（${r.json.length} 字节）")
                0
            }
        }
        else -> { err.println(USAGE); 2 }
    }
}

/** 子命令参数解析（位置参数 + 选项；-o 后必跟路径）。解析失败返回 null（错误已打印）。 */
private fun parseArgs(args: List<String>, err: PrintStream): Pair<List<String>, CliOptions>? {
    val positional = mutableListOf<String>()
    var script = false
    var verbose = false
    var noDegrade = false
    var noLift = false   // t123（R-16 S4 / OQ-5）：--no-lift 旗标
    var output: String? = null
    var i = 0
    while (i < args.size) {
        when {
            args[i] == "-o" -> {
                if (i + 1 >= args.size) { err.println("[错误] -o 后面要跟产物路径"); return null }
                output = args[i + 1]
                i += 2
            }
            args[i] == "--script" -> { script = true; i++ }
            args[i] == "--no-degrade" -> { noDegrade = true; i++ }
            args[i] == "--no-lift" -> { noLift = true; i++ }
            args[i] == "-v" -> { verbose = true; i++ }
            args[i].startsWith('-') -> { err.println("[错误] 未知选项 ${args[i]}"); return null }
            else -> { positional += args[i]; i++ }
        }
    }
    return positional to CliOptions(script, verbose, output, noDegrade, noLift)
}

private data class CliOptions(
    val script: Boolean = false, val verbose: Boolean = false, val output: String? = null,
    /** t118（OQ-5 队长裁=给）：--no-degrade——脚本退化即失败的 CI 门。 */
    val noDegrade: Boolean = false,
    /** t123（R-16 S4 / OQ-5 队长裁=给）：--no-lift——脚本上文上提即失败的 CI 门。 */
    val noLift: Boolean = false,
)

/** 诊断报告（带源码上下文；-v 打开补充族）。 */
private fun report(bag: DiagBag, source: String, verbose: Boolean, out: PrintStream) {
    val text = bag.report(includeSupplement = verbose, source = source)
    if (text.isNotEmpty()) out.println(text)
}

/** token 行（对齐旧 cli 的 tokens 输出口径：位置 / kind / 文本 / 邻接标记）。 */
internal fun tokenLine(t: Token): String =
    "${t.line}:${t.col}	${t.kind}	${t.text}	${if (t.precededBySpace) "sp" else "adj"}"
