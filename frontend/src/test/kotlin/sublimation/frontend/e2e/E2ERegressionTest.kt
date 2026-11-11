package sublimation.frontend.e2e

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import sublimation.frontend.cli.compileFrontend
import sublimation.frontend.json.HirJson

/**
 * 前端 e2e 回归验收（t12；D-4「旧测试是行为规格」+ S-3 §3 验收证据 1）。
 *
 * 样本来源（S-3 §2.2 字典点查口径，自旧工程 F:\\KotlinProject\\sublimation 移植落
 * frontend/src/test/resources/e2e/）：
 * - examples/ 全套特性样例（含 模块系统/ 子目录双文件）；
 * - core/ 标准库片段（18 个 stdlib 模块的声明文件全套）。
 *
 * 验收判据（对齐任务书 t12 与 ADR-1 §3.7 schema v1）：
 * 1. 全管线 [compileFrontend] 对任何样例不抛异常（错误恢复 + 半成品原则，ADR-1 §3.2）；
 * 2. JSON 产物结构完整：envelope 五件套（schema/compiler/unit/ok/diagnostics/hir）
 *    字段齐备、取值合法；节点统一形态（kind/nid/pos/notes 四公共字段）全树成立；
 *    nid 全文件唯一（节点身份，D-9）；证明义务节点三字段齐备（proofContext/rule/verdict）；
 * 3. ok 旗标与诊断族一致（ok ⟺ 无 ERROR 级诊断，N-SEV-BLOCK / D-10）；
 * 4. 输入→输出可复现（相同源码必产相同 JSON，S-1 层间总契约 3）；
 * 5. unit.mode 与条目种类自洽（script ⟺ 存在顶层语句条目）。
 *
 * 样例层面的通过率与诊断码汇总表随测试标准输出，供验收报告（documents/）登记
 * 通过率与旧实现差异归因——ok=false 不判测试失败（V1 边界由 ADR-12 §三 落档），
 * **结构性违规与崩溃**才判失败。
 */
class E2ERegressionTest {

    // 样本根（构建工作目录为工程根，见 CliTest 同口径）
    private val root = File("frontend/src/test/resources/e2e")

    /** 单个样例：相对路径（/ 分隔，即 unit.path）/ 源码。 */
    private data class Sample(val unitPath: String, val source: String)

    /** 结构校验违规收集项（样例 + 原因）。 */
    private data class Violation(val unitPath: String, val reason: String)

    // ============ 样本装载 ============

    /** 全套样例（examples + core；按路径排序保序输出）。 */
    private fun loadSamples(): List<Sample> =
        root.walkTopDown().filter { it.isFile && it.extension == "subl" }.sortedBy { it.path }
            .map { f ->
                Sample(f.relativeTo(root).path.replace('\\', '/'), f.readText(Charsets.UTF_8))
            }.toList()
            .also { assertFalse(it.isEmpty(), "e2e 样本集不应为空") }

    // ============ 验收判据 ============

    /** D-4：旧前端单测与样例移植的覆盖齐备性（旧 examples 全量 + core 全量）。 */
    @Test
    fun 样本集覆盖齐备() {
        val samples = loadSamples()
        val examples = samples.count { it.unitPath.startsWith("examples/") }
        val core = samples.count { it.unitPath.startsWith("core/") }
        assertTrue(examples >= 30, "旧 examples/ 全套样例（实际 $examples）")
        assertTrue(core >= 30, "旧 core/ 标准库片段全套（实际 $core）")
        samples.forEach { s ->
            assertFalse(s.source.isBlank(), "样例不应为空文件: ${s.unitPath}")
            assertFalse(s.source.length > 20_000, "样例尺寸在合理区间: ${s.unitPath}")
        }
        // 模块系统子目录双文件（目录即模块的旧形态）一并纳入
        assertTrue(samples.any { it.unitPath.startsWith("examples/模块系统/") }, "模块系统样例")
    }

    /**
     * 主验收：全套样例经新前端编译产出 JSON 并校验结构完整 + 可复现。
     * 汇总表打印到标准输出（验收报告的通过率数据源）。
     */
    @Test
    fun 全套样本产出结构完整且可复现的JSON产物() {
        val samples = loadSamples()
        val violations = mutableListOf<Violation>()
        val rows = mutableListOf<String>()
        val detailLines = mutableListOf<String>()

        for (s in samples) {
            // ---- 判据 1：全管线不抛异常（半成品原则，ADR-1 §3.2） ----
            val compiled = try {
                compileFrontend(s.source, s.unitPath) to compileFrontend(s.source, s.unitPath)
            } catch (e: Throwable) {
                violations += Violation(s.unitPath, "全管线抛异常: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
            if (compiled == null) continue
            val (out1, out2) = compiled

            // ---- 判据 4：输入→输出可复现（S-1 层间总契约 3） ----
            if (out1.json != out2.json) {
                violations += Violation(s.unitPath, "相同源码两次编译产物不一致（不可复现）")
            }

            // ---- 判据 2/3/5：envelope 与全树结构校验 ----
            val envObj = try {
                parseJson(out1.json) as? JsonObj
            } catch (e: Throwable) {
                violations += Violation(s.unitPath, "JSON 产物不可解析（结构不完整）: ${e.message}")
                null
            }
            if (envObj == null) continue
            violations += checkEnvelope(envObj, s.unitPath)
            val hir = envObj["hir"] as? JsonObj
            if (hir != null) violations += checkTree(hir, s.unitPath)

            // ---- 汇总行（ok 状态与诊断码，供验收报告登记） ----
            val diags = (envObj["diagnostics"] as? JsonArr)?.items?.filterIsInstance<JsonObj>() ?: emptyList()
            val ok = (envObj["ok"] as? JsonBool)?.v
            val codes = diags.map { d -> (d["code"] as? JsonStr)?.v }.filterNotNull().toSet().sorted()
            val mode = (envObj["unit"] as? JsonObj)?.let { (it["mode"] as? JsonStr)?.v } ?: "?"
            val nids = hir?.let { countNodes(it) } ?: 0
            rows += listOf(s.unitPath, mode, ok?.toString() ?: "?", "${diags.size}项", "${nids}节点",
                if (codes.isEmpty()) "-" else codes.joinToString(",")).joinToString(" | ")
            // 诊断明细行（供验收报告的差异归因；构建日志不必然捕获 stdout，产物文件为准）
            detailLines += if (diags.isEmpty()) listOf("# ${s.unitPath}: 无诊断") else buildList {
                add("# ${s.unitPath}")
                diags.forEach { d ->
                    val sev = (d["severity"] as? JsonStr)?.v ?: "?"
                    val code = (d["code"] as? JsonStr)?.v ?: "?"
                    val pos = (d["pos"] as? JsonStr)?.v ?: "?"
                    val msg = (d["message"] as? JsonStr)?.v ?: "?"
                    add("  [$sev][$pos] $code: $msg")
                }
            }
        }

        // ---- 汇总表落盘（验收报告数据源；构建日志不必然捕获测试 stdout，产物文件为准） ----
        val okCount = rows.count { it.contains(" | project | true |") || it.contains(" | script | true |") }
        val summary = buildString {
            appendLine("=== e2e 汇总（${rows.size} 样例，ok=true $okCount）===")
            rows.forEach { appendLine(it) }
            appendLine("=== 结构性违规 ${violations.size} 项 ===")
        }
        val outDir = File("frontend/build/tmp/e2e")
        outDir.mkdirs()
        File(outDir, "e2e-summary.txt").writeText(summary, Charsets.UTF_8)
        File(outDir, "e2e-diagnostics.txt").writeText(detailLines.joinToString("\n"), Charsets.UTF_8)
        println(summary)

        // ---- 判决：只有结构性违规/崩溃判失败（V1 边界的 ok=false 不判失败，ADR-12 §三） ----
        if (violations.isNotEmpty()) {
            println("=== 结构性违规清单 ===")
            violations.forEach { println("${it.unitPath} :: ${it.reason}") }
        }
        assertTrue(violations.isEmpty(), "e2e 结构完整性违规 ${violations.size} 项:\n${violations.joinToString("\n") { "${it.unitPath} :: ${it.reason}" }}")
        assertFalse(rows.isEmpty(), "汇总表不应为空")
    }

    /** envelope 五件套字段级校验（ADR-1 §3.7 schema v1）。 */
    private fun checkEnvelope(env: JsonObj, unitPath: String): List<Violation> {
        val v = mutableListOf<Violation>()
        fun bad(reason: String) { v += Violation(unitPath, reason) }

        val schema = env["schema"] as? JsonStr
        if (schema == null) bad("envelope.schema 缺失/非字符串")
        else if (schema.v != HirJson.SCHEMA) bad("envelope.schema=${schema.v} != ${HirJson.SCHEMA}")
        val compiler = env["compiler"] as? JsonObj
        val version = compiler?.get("version") as? JsonStr
        if (version == null) bad("envelope.compiler.version 缺失/非字符串")
        else if (version.v != HirJson.COMPILER_VERSION) bad("compiler.version=${version.v} 不对齐构建版本")
        val unit = env["unit"] as? JsonObj
        if (unit == null) bad("envelope.unit 缺失")
        val path = unit?.get("path") as? JsonStr
        if (path?.v != unitPath) bad("unit.path=${path?.v} 与入参 unitPath 不一致")
        val mode = unit?.get("mode") as? JsonStr
        if (mode == null || mode.v !in setOf("project", "script")) bad("unit.mode=${mode?.v} 非合法枚举")

        // 诊断族（D-10 四族小写）
        val diags = env["diagnostics"] as? JsonArr
        if (diags == null) bad("envelope.diagnostics 缺失/非数组")
        diags?.items?.forEachIndexed { i, d ->
            val dd = d as? JsonObj
            if (dd == null) { bad("diagnostics[$i] 非对象"); return@forEachIndexed }
            val sev = (dd["severity"] as? JsonStr)?.v
            if (sev == null || sev !in setOf("error", "warn", "hint", "supplement")) bad("diagnostics[$i].severity=$sev 非四族小写")
            val code = (dd["code"] as? JsonStr)?.v
            if (code.isNullOrEmpty()) bad("diagnostics[$i].code 缺失")
            val pos = (dd["pos"] as? JsonStr)?.v
            if (pos == null || !POS_PATTERN.matches(pos)) bad("diagnostics[$i].pos=$pos 不是合法位置串")
            if ((dd["message"] as? JsonStr)?.v == null) bad("diagnostics[$i].message 缺失")
            val nid = dd["nid"]
            if (nid != null && nid !is JsonNum && nid !is JsonNull) bad("diagnostics[$i].nid 非 null/数值")
        }

        // ok 旗标与诊断族一致（N-SEV-BLOCK：ERROR 决定 ok，D-10）
        val ok = env["ok"] as? JsonBool
        if (ok == null) bad("envelope.ok 缺失/非布尔")
        val hasError = diags?.items?.filterIsInstance<JsonObj>()
            ?.any { (it["severity"] as? JsonStr)?.v == "error" } ?: false
        if (ok != null && ok.v == hasError) bad("ok=${ok.v} 与 ERROR 级诊断存在性不一致")

        // hir：HirFile 根；条目非空仅在存在 ERROR 诊断时强制（半成品原则：带错也必有树，
        // ADR-1 §3.2；零声明纯注释源文件条目为空是正确形态）
        val hir = env["hir"] as? JsonObj
        if (hir == null) bad("envelope.hir 缺失/非对象")
        else {
            if ((hir["kind"] as? JsonStr)?.v != "HirFile") bad("hir.kind != HirFile")
            val entries = hir["entries"] as? JsonArr
            if (entries == null) bad("hir.entries 缺失/非数组")
            else if (entries.items.isEmpty() && hasError) bad("存在 ERROR 诊断但 hir.entries 为空（半成品树缺失）")
            // unit.mode 与条目种类自洽（mode 由树唯一决定，HirJson.unitMode 动机注释）
            val entryKinds = entries?.items?.filterIsInstance<JsonObj>()
                ?.map { (it["kind"] as? JsonStr)?.v } ?: emptyList()
            val anyStmt = entryKinds.any { it in STMT_KINDS }
            if (mode != null) {
                // D-12(t111):mode 真值单源——mode 不再由条目种类自洽判。e2e 样本皆 .subl,
                // CLI 真值即 project（工程模式顶层 axiom 语句落 StmtEntry 亦判 project）。
                if (mode.v != "project") bad("mode=" + mode.v + " 与 CLI 真值 project 不符（D-12）")
            }
            val badKinds = entryKinds.filter { it == null || (it !in STMT_KINDS && it !in DECL_KINDS) }
            if (badKinds.isNotEmpty()) bad("顶层条目 kind=$badKinds 非声明/语句种类")
        }
        return v
    }

    /** 全树节点形态校验（统一形态四公共字段 + nid 唯一 + 证明义务三字段）。 */
    private fun checkTree(hir: JsonObj, unitPath: String): List<Violation> {
        val v = mutableListOf<Violation>()
        val firstSeen = mutableMapOf<Int, String>()
        walk(hir) { node ->
            val kind = (node["kind"] as? JsonStr)?.v
            val pos = (node["pos"] as? JsonStr)?.v
            val nid = node["nid"]
            if (kind == null) v += Violation(unitPath, "节点缺 kind")
            if (pos == null || !POS_PATTERN.matches(pos)) v += Violation(unitPath, "节点 $kind pos=$pos 非法")
            val nidNum = nid as? JsonNum
            if (nidNum == null) v += Violation(unitPath, "节点 $kind nid 非数值")
            else {
                // nid = 节点身份（D-9）：同一个 nid 只能对应同一个节点（kind/pos 一致）；
                // 同一节点在树中被多处引用（HIR 为 DAG）允许重复出现，但身份必须自洽。
                val here = "$kind@$pos"
                val prev = firstSeen.put(nidNum.v, here)
                if (prev != null && prev != here) v += Violation(unitPath, "nid=${nidNum.v} 身份冲突：$prev 与 $here（同一 nid 须对应同一节点，D-9）")
            }
            val notes = node["notes"]
            if (notes !is JsonArr) v += Violation(unitPath, "节点 $kind notes 非数组")
            if (kind in PROOF_KINDS) {
                if (node["proofContext"] == null) v += Violation(unitPath, "证明节点 $kind 缺 proofContext")
                if (!node.members.containsKey("rule")) v += Violation(unitPath, "证明节点 $kind 缺 rule")
                if (!node.members.containsKey("verdict")) v += Violation(unitPath, "证明节点 $kind 缺 verdict")
            }
        }
        return v
    }

    /**
     * 递归遍历所有 HIR 节点对象（kind 前缀「Hir」；符号引用/解析期留痕/verdict 等
     * 辅助记录不具节点形态——节点统一形态只对 HirNode 声明，ADR-1 §3.7）。
     */
    private fun walk(value: JsonVal, visit: (JsonObj) -> Unit) {
        when (value) {
            is JsonObj -> {
                val kind = (value["kind"] as? JsonStr)?.v
                if (kind != null && kind.startsWith("Hir")) visit(value)
                value.members.values.forEach { walk(it, visit) }
            }
            is JsonArr -> value.items.forEach { walk(it, visit) }
            else -> {}
        }
    }

    /** 统计节点数（汇总表用）。 */
    private fun countNodes(hir: JsonObj): Int {
        var n = 0
        walk(hir) { n++ }
        return n
    }

    companion object {
        /** 位置串：「l:c」或「l:c-l:c」（ADR-1 §3.7 紧凑串）。 */
        private val POS_PATTERN = Regex("""\d+:\d+(-\d+:\d+)?""")

        /** 顶层声明条目种类（HIR 声明层，S-5~S-14 映射）。 */
        private val DECL_KINDS = setOf(
            "HirFunDecl", "HirTraitDecl", "HirStructDecl",
            "HirEnumDecl", "HirTypeAlias", "HirPropDef", "HirErrorDecl",
        )

        /** 顶层语句条目种类（HIR 语句层，S-16~S-30 映射）。 */
        private val STMT_KINDS = setOf(
            "HirVarStmt", "HirAssign", "HirAxiom", "HirByStmt", "HirUnchecked",
            "HirExprStmt", "HirBecause", "HirWhenProp", "HirIfProp",
            "HirExistsIntro", "HirExistsElim", "HirLocalFun", "HirStrategy",
            "HirReturn", "HirErrorStmt",
        )

        /** 承载证明义务的节点种类（proofContext/rule/verdict 三字段，ADR-1 §3.4）。 */
        private val PROOF_KINDS = setOf(
            "HirAxiom", "HirByStmt", "HirBecause", "HirWhenProp", "HirIfProp",
            "HirExistsIntro", "HirExistsElim", "HirStrategy", "HirByExpr",
        )
    }
}

// =========== 测试用 JSON 解析底座（RFC 8259 子集；e2e 结构校验专用，不入 main） ===========

/** JSON 值（对象保序——校验字段存在性与顺序无关，保序仅便调试）。 */
private sealed interface JsonVal

/** JSON 对象（保序映射 + 下标取值）。 */
private class JsonObj(val members: LinkedHashMap<String, JsonVal>) : JsonVal {
    operator fun get(k: String): JsonVal? = members[k]
}

private class JsonStr(val v: String) : JsonVal
private class JsonNum(val v: Int) : JsonVal
private class JsonBool(val v: Boolean) : JsonVal
private object JsonNull : JsonVal
private class JsonArr(val items: List<JsonVal>) : JsonVal

/**
 * 极简递归下降 JSON 解析器（RFC 8259 子集：对象/数组/字符串/整数/true/false/null）。
 * 动机：JSON 产物结构完整性校验须由**解析**证明，不能用子串匹配冒充（S-4 三禁：
 * 未验证的断言）；e2e 测试专用，零第三方依赖（S-2 §3.3 同口径）。
 */
private fun parseJson(text: String): JsonVal {
    val p = JsonParser(text)
    val v = p.parseValue()
    p.skipWs()
    if (p.pos != text.length) throw IllegalStateException("JSON 尾部垃圾字符 @${p.pos}")
    return v
}

private class JsonParser(private val src: String) {
    var pos = 0

    fun parseValue(): JsonVal {
        skipWs()
        return when (val c = peek()) {
            '{' -> parseObj()
            '[' -> parseArr()
            '"' -> JsonStr(parseStr())
            't' -> { expect("true"); JsonBool(true) }
            'f' -> { expect("false"); JsonBool(false) }
            'n' -> { expect("null"); JsonNull }
            else -> if (c == '-' || c.isDigit()) parseNum() else throw IllegalStateException("非法字符 '$c' @$pos")
        }
    }

    private fun parseObj(): JsonObj {
        pos++ // '{'
        val members = LinkedHashMap<String, JsonVal>()
        skipWs()
        if (peek() == '}') { pos++; return JsonObj(members) }
        while (true) {
            skipWs()
            if (peek() != '"') throw IllegalStateException("对象键应为字符串 @$pos")
            val key = parseStr()
            skipWs()
            if (peek() != ':') throw IllegalStateException("缺冒号 @$pos")
            pos++
            members[key] = parseValue()
            skipWs()
            when (peek()) {
                ',' -> pos++
                '}' -> { pos++; return JsonObj(members) }
                else -> throw IllegalStateException("对象语法错误 @$pos")
            }
        }
    }

    private fun parseArr(): JsonArr {
        pos++ // '['
        val items = mutableListOf<JsonVal>()
        skipWs()
        if (peek() == ']') { pos++; return JsonArr(items) }
        while (true) {
            items += parseValue()
            skipWs()
            when (peek()) {
                ',' -> pos++
                ']' -> { pos++; return JsonArr(items) }
                else -> throw IllegalStateException("数组语法错误 @$pos")
            }
        }
    }

    private fun parseStr(): String {
        require(peek() == '"'); pos++
        val sb = StringBuilder()
        while (true) {
            val c = read()
            if (c == '"') return sb.toString()
            if (c == '\\') {
                val esc = read()
                sb.append(
                    when (esc) {
                        '"' -> '"'; '\\' -> '\\'; 'b' -> '\b'; 'f' -> '\u000C'
                        'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                        'u' -> {
                            val hex = src.substring(pos, pos + 4); pos += 4
                            hex.toInt(16).toChar()
                        }
                        else -> throw IllegalStateException("非法转义 '\\$esc'")
                    }
                )
            } else sb.append(c)
        }
    }

    private fun parseNum(): JsonNum {
        val start = pos
        if (peek() == '-') pos++
        while (pos < src.length && src[pos].isDigit()) pos++
        return JsonNum(src.substring(start, pos).toInt())
    }

    private fun peek(): Char = if (pos < src.length) src[pos] else throw IllegalStateException("意外的输入结尾")
    private fun read(): Char = if (pos < src.length) src[pos++] else throw IllegalStateException("意外的输入结尾")
    private fun expect(s: String) { for (c in s) if (read() != c) throw IllegalStateException("字面量 $s 不匹配 @$pos") }
    fun skipWs() { while (pos < src.length && src[pos].isWhitespace()) pos++ }
}
