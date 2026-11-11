package sublimation.frontend.json

/**
 * JSON 写入底座（RFC 8259；P-6 决议——手写发射器以保 :frontend 零第三方运行时依赖，S-2 §3.3）。
 *
 * 契约：
 * - 对象字段与数组元素**保序**输出（构造顺序即输出顺序）：相同输入必产相同输出
 *   （S-1 层间总契约 3——产物可 diff、可缓存、可回归对账）。
 * - 换行 + 2 空格缩进：envelope 与 HIR 产物的首要消费者是人与 QA 回归，JSON 解析器
 *   不感知空白，故 v1 取人可读格式（动机注释，格式选择不影响机器消费）。
 * - 空对象/空数组取紧凑形态 `{}` / `[]`。
 *
 * 转义策略（RFC 8259 §7）：强制转义 `"`、`\` 与 C0 控制字符（U+0000~U+001F）；
 * 其中 `\b\t\n\f\r` 取短转义，其余 C0 取 `\u00xx` 小写十六进制。DEL（U+007F）与
 * 非 ASCII 字符原样输出——产物以 UTF-8 写出，全量转义会无谓放大体积且损失可读性。
 */
internal class JsonWriter(private val out: StringBuilder, private val indent: String = "  ") {
    private var depth = 0

    /** 当前容器是否已写入元素——决定下一元素的前置逗号。 */
    private val written = ArrayDeque<Boolean>()

    /** 对象。 */
    fun obj(body: JsonWriter.() -> Unit) = container('{', '}', body)

    /** 数组。 */
    fun arr(body: JsonWriter.() -> Unit) = container('[', ']', body)

    /** 命名对象（`"name": { ... }`）。 */
    fun obj(name: String, body: JsonWriter.() -> Unit) = field(name) { obj(body) }

    /** 命名数组（`"name": [ ... ]`）。 */
    fun arr(name: String, body: JsonWriter.() -> Unit) = field(name) { arr(body) }

    /** 数组元素（前置逗号与换行由本方法处理）。 */
    fun item(body: JsonWriter.() -> Unit) {
        sep()
        body()
    }

    /** 对象字段（前置逗号与换行由本方法处理，字段名经转义）。 */
    fun field(name: String, body: JsonWriter.() -> Unit) {
        sep()
        out.append(escapeJsonString(name)).append(": ")
        body()
    }

    // ---------- 原始量（只作字段值或数组元素体，不处理逗号与换行） ----------

    /** 字符串字面量（已转义、带引号）。 */
    fun str(v: String) {
        out.append(escapeJsonString(v))
    }

    /** 整数（NodeId、SymbolRef.id 等数值字段）。 */
    fun num(v: Int) {
        out.append(v.toString())
    }

    fun bool(v: Boolean) {
        out.append(if (v) "true" else "false")
    }

    fun nil() {
        out.append("null")
    }

    /** 汇总产物文本。 */
    fun result(): String = out.toString()

    // ---------- 内部 ----------

    private fun container(open: Char, close: Char, body: JsonWriter.() -> Unit) {
        out.append(open)
        depth++
        written.addLast(false)
        body()
        val nonEmpty = written.removeLast()
        depth--
        if (nonEmpty) newline()
        out.append(close)
    }

    private fun sep() {
        if (written.last()) out.append(',')
        newline()
        written[written.lastIndex] = true
    }

    private fun newline() {
        out.append('\n')
        repeat(depth) { out.append(indent) }
    }
}

/**
 * RFC 8259 字符串字面量（含两侧引号）。
 * 供 [JsonWriter.str]/[JsonWriter.field] 与测试直接复用。
 */
internal fun escapeJsonString(s: String): String = buildString(s.length + 2) {
    append('"')
    for (c in s) when (c) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\b' -> append("\\b")
        '\t' -> append("\\t")
        '\n' -> append("\\n")
        '\u000C' -> append("\\f")
        '\r' -> append("\\r")
        else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
    }
    append('"')
}
