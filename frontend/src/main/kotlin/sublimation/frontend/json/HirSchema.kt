package sublimation.frontend.json

/**
 * HIR JSON **契约守门**（t158：schema v3 冻结声明——前后端契约的版本闸门）。
 *
 * 冻结决议（specification/HIR-JSON-Schema-v3.md）：
 * - 产物 envelope 必带顶层 `schema` 字段，其值即契约版本标识；既有锚为 `sublimation/hir@v3`;
 * - **版本号约定**:breaking 变更（字段删改/语义翻转）须经新 ADR 裁决并升至 v4——旧版本产物
 *   识别为「不可消费」而非「尝试解析」，**不破坏既有消费方**（v3 消费方遇 v4 应拒收并报告，
 *   不许就地兼容降级）;
 * - 加性扩展（仅新增可选字段、不删不改语义）在 v3 内演进——由 ADR-6 决策 2「统一形态 +
 *   固定字段集」原则保障（消费方按字段集读取，新增字段应忽略而非报错）。
 *
 * 守门口径：**envelope 存在性 + 版本等价**。本对象为消费方（后端、解释器、对拍脚本）提供
 * 单一入口 [check]——通过返回 null，失败返回原因串（缺失/版本不符/非法 envelope 一律失败，
 * 不静默放行——「带病冻结」即真契约缺口，先补齐再冻结）。
 *
 * 实现纪律：不引入第三方 JSON 解析器（P-6 手写发射器同尺度的最小扫描器）;扫描按 JSON
 * 结构定位**根对象内（根 '{' 之后，depth 1）**的 `schema` 键——嵌套对象/数组内的同名键与字符串值内部的
 * 诱导文本一律无效（转义正确处理）。 */
object HirSchema {

    /** 契约版本标识——唯一真源为 [HirJson.SCHEMA]（发射器与守门共用一处声明，防双源漂移）。 */
    val CONTRACT: String get() = HirJson.SCHEMA

    /**
     * 守门：校验 envelope 顶层 `schema` 字段的存在性与版本等价。
     *
     * @return null=通过；否则=失败原因（schema 缺失 / 版本不符 / 顶层非对象）
     */
    fun check(envelope: String): String? {
        if (!envelope.trimStart().startsWith("{")) return "envelope 顶层非 JSON 对象（契约：'{' 起的 envelope）"
        val v = topLevelString(envelope, "schema")
            ?: return "envelope 缺顶层 schema 字段（契约必带，版本= $CONTRACT）"
        if (v != CONTRACT) return "envelope schema 版本不符：实得 '$v'，契约 '$CONTRACT'（breaking 变更经新 ADR 升至 v4，不就地兼容）"
        return null
    }

    /**
     * 最小结构扫描器：定位顶层（depth 0）指定字符串键的值。
     *
     * 纪律：单遍扫描、处理字符串转义（`\\`、`\"`）、嵌套 `{}`/`[]` 深度——键的判定只认
     * 「字符串后跟 `:`」且深度为 0;键的值只取紧随其后的字符串字面量。顶层非对象、键缺失、
     * 值非字符串一律返回 null（守门失败语义由 [check] 统一落位）。
     */
    private fun topLevelString(json: String, key: String): String? {
        var i = 0
        var depth = 0
        while (i < json.length) {
            val c = json[i]
            when {
                c == '"' -> {
                    val (str, end) = readString(json, i)
                    var j = end
                    while (j < json.length && json[j].isWhitespace()) j++
                    if (depth == 1 && j < json.length && json[j] == ':') {
                        // 键候选：其后须为字符串值（契约字段值为字符串;非字符串值=不匹配，跳过）
                        var k = j + 1
                        while (k < json.length && json[k].isWhitespace()) k++
                        if (k < json.length && json[k] == '"') {
                            val (value, vend) = readString(json, k)
                            if (str == key) return value
                            i = vend
                            continue
                        }
                        i = j + 1
                        continue
                    }
                    i = end
                }
                c == '{' || c == '[' -> { depth++; i++ }
                c == '}' || c == ']' -> { depth--; i++ }
                else -> i++
            }
        }
        return null
    }

    /** 读字符串字面量（起点为开引号下标）→ (去转义后的值, 闭引号后下标)。 */
    private fun readString(json: String, start: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var i = start + 1
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                sb.append(json[i + 1]); i += 2; continue   // 转义字符原样回填（契约面只取字面值）
            }
            if (c == '"') return sb.toString() to (i + 1)
            sb.append(c); i++
        }
        return sb.toString() to json.length
    }
}
