package sublimation.frontend.diag

/**
 * 诊断严重级（D-10：四族 E/W/D/H 与旧 Diag.kt 四级枚举全对齐，P-7）。
 *
 * 契约（N-SEV-BLOCK）：阻断性由**严重级**判定而非码族字母——只有 ERROR 阻断编译。
 * 族与级同名同义：E→ERROR、W→WARN、H→HINT、D→SUPPLEMENT（确认性/补充族）。
 */
enum class Severity { ERROR, WARN, HINT, SUPPLEMENT }

/**
 * 结构化诊断（D-10/D2：SourceRange 位置 + envelope 字段）。
 *
 * 契约（S-1 层间总契约 2）：每条诊断携带源位置（pos 从不为 null——无位置的编译级
 * 结论由调用方落文件级合成位置）。
 * code 为旧体系四族码（旧实测 109 码全对齐，P-7）；nid 反引 HIR 节点身份（D-9），
 * 未关联节点时 null（lexer/parser 期诊断）。
 */
data class Diag(
    val severity: Severity,
    val code: String,
    val pos: SourceRange,
    val message: String,
    val modulePath: String = "",
    /** t155（S7 限面收边·多文件诊断行号）：源件归属——模块体多文件拼接（行号连续累加）时指回
     *  诊断所在源件（相对编译根的模块内路径/件名）;单文件编译与挂载期诊断留 null。 */
    val file: String? = null,
    val nid: NodeId? = null,
) {
    /**
     * 渲染（旧格式兼容「[错误][1:2] CODE: msg」，D-10 渲染层口径）。
     * source 给出时附源码行片段与箭头（行列取 pos.start）。
     */
    fun render(source: String? = null): String {
        val tag = when (severity) {
            Severity.ERROR -> "[错误]"
            Severity.WARN -> "[警告]"
            Severity.HINT -> "[提示]"
            Severity.SUPPLEMENT -> "[补充]"
        }
        // t155：文件归属前置（多文件模块：报错指回源件——行号仍按模块拼接连续累加口径）
        val loc = if (file != null) "${file}:${pos.start.line}:${pos.start.col}"
            else "${pos.start.line}:${pos.start.col}"
        val base = "${tag}[${loc}] ${code}: ${message}"
        if (source == null) return base
        val srcLines = source.split("\n")
        val line = pos.start.line
        if (line !in 1..srcLines.size) return base
        val text = srcLines[line - 1]
        val width = srcLines.size.toString().length
        val lno = " ".repeat(maxOf(0, width - line.toString().length)) + line
        val pad = " ".repeat(width)
        val carets = " ".repeat((pos.start.col - 1).coerceIn(0, text.length)) + "^"
        return "$base\n  $lno | $text\n  $pad | $carets"
    }
}

/**
 * 诊断袋（旧 DiagBag 行为全对齐，P-7）：四族分方法落袋（各携带强制源位置，
 * S-1 层间总契约 2）；absorb 合并他袋；dedupe 按（code, pos, message）去重；
 * hasError 判阻断（N-SEV-BLOCK）；report 默认隐藏 SUPPLEMENT（CLI -v 打开）
 * 并按落袋顺序输出。
 *
 * 行为差异（相对旧 pos:String 时代，D-10 口径）：pos 为结构化 SourceRange；
 * 落袋方法可选附 nid 反引 HIR 节点；报告渲染由调用方以 source 供给源码上下文。
 */
class DiagBag {
    private val bag = ArrayList<Diag>()

    /** 所属模块（多文件编译时贯穿落袋诊断）。 */
    var modulePath: String = ""

    fun error(code: String, pos: SourceRange, message: String, nid: NodeId? = null) {
        bag += Diag(Severity.ERROR, code, pos, message, modulePath, nid = nid)
    }

    fun warn(code: String, pos: SourceRange, message: String, nid: NodeId? = null) {
        bag += Diag(Severity.WARN, code, pos, message, modulePath, nid = nid)
    }

    fun hint(code: String, pos: SourceRange, message: String, nid: NodeId? = null) {
        bag += Diag(Severity.HINT, code, pos, message, modulePath, nid = nid)
    }

    fun supplement(code: String, pos: SourceRange, message: String, nid: NodeId? = null) {
        bag += Diag(Severity.SUPPLEMENT, code, pos, message, modulePath, nid = nid)
    }

    /** 全量诊断（保序）。 */
    val diags: List<Diag> get() = bag

    /** 阻断判定（N-SEV-BLOCK：只有 ERROR 阻断）。 */
    val hasError: Boolean get() = bag.any { it.severity == Severity.ERROR }

    /** 合并他袋（保序追加）。 */
    fun absorb(other: DiagBag) {
        bag += other.bag
    }

    /**
     * t155（S7 限面收边·多文件诊断行号）：文件归属标注——按累计行号映射给每条诊断补 [Diag.file]
     * （行号连续累加口径不变——pos 仍是模块拼接行;file 指回源件）。映射函数返回 null 即不标注
     * （单文件/挂载期诊断）。 */
    fun attributeFiles(map: (Int) -> String?) {
        val src = ArrayList(bag)
        bag.clear()
        for (x in src) {
            val f = map(x.pos.start.line)
            if (f != null && x.file == null) bag += x.copy(file = f) else bag += x
        }
    }

    /** 丢弃 modulePath 不在 rootPath 内的补充族诊断（旧 dropSupplementsOutside）。 */
    fun dropSupplementsOutside(rootPath: String) {
        bag.removeAll { it.severity == Severity.SUPPLEMENT && it.modulePath != rootPath }
    }

    /** 按（code, pos, message）去重（保留首次出现）。 */
    fun dedupe() {
        val seen = HashSet<Triple<String, SourceRange, String>>()
        bag.removeAll { !seen.add(Triple(it.code, it.pos, it.message)) }
    }

    /** 报告渲染：includeSupplement=false 时隐藏补充族（默认隐藏，CLI -v 打开）。 */
    fun report(includeSupplement: Boolean = false, source: String? = null): String =
        bag.filter { includeSupplement || it.severity != Severity.SUPPLEMENT }
            .joinToString("\n") { it.render(source) }
}
