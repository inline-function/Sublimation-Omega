package sublimation.frontend.cli

import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t157（B3 后续件·2 注入一致性测试）：@axiom 落 funs 快照表后，注入（theorem 证明调用面）
 * 与快照（stdlib/axioms 规格面）的一致性门——**axioms 修改即报差，防静默漂移**。
 *
 * - **形态**：以 stdlib 编译产物的 HirFunDecl posts JSON 区段为基线，逐条断言 16 条公理的
 *   命题形关键片段（head/谓词名/构造子/字面量/量词符）——改任一公理的命题即触红（drift 门）。
 * - **注入≡规格**:A1 `nat non-negative` 的断言（n >= 0）与类型注入（D-NAT-PROP，t65
 *   provenance=type）证同一命题——用户 post <n >= 0> 走注入快路径绿，公理库声明同形规格。
 * - 同 t156 纪律：快路径与公理**分家**（注入带 provenance=type、公理为用户信任基）——
 *   本门只断言两者口径对应，不为注入加任何公理依赖（零 nid、零新通道）。 */
class T157AxiomsConsistencyTest {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun scriptStd(src: String, scriptMode: Boolean = true): FrontendOutput {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode, noDegrade = false, noLift = false)
        return compileFrontendImpl(src, "t157.subl", scriptMode, false, false,
            su.associate { it.alias to it.snapshot }, sd)
    }

    /** stdlib axioms 件的 HirFunDecl JSON 区段表（名 → 区段;引擎逐字口径见 HirJson）。 */
    private fun axiomRegions(): Map<String, String> {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        assertFalse(sd.hasError, "stdlib 全件绿（一致性基线前提）: " + sd.diags.map { it.code })
        val json = su.associateBy { it.alias }["axioms"]!!.unit.output.json
        val out = LinkedHashMap<String, String>()
        var i = json.indexOf("\"kind\": \"HirFunDecl\"")
        while (i >= 0) {
            val j = json.indexOf("\"kind\": \"HirFunDecl\"", i + 1)
            val seg = json.substring(i, if (j < 0) json.length else j)
            val n = seg.substringAfter("\"name\": \"").substringBefore('"')
            out[n] = seg
            i = j
        }
        return out
    }

    /** 门 1：stdlib 全件绿（axioms 件 16 条并入编译根后零红）。 */
    @Test fun stdlib全件绿() {
        val (_, sd) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        assertFalse(sd.hasError, "stdlib 全件（含 16 条公理）零红: " + sd.diags.map { it.code + it.message }.joinToString(";"))
    }

    /** 门 2：十六条公理在位（A1–A3 + L1–L4 + t157 扩容 M1–M2/S1–S2/T1–T3/G1）。 */
    @Test fun 十六条公理在位() {
        val r = axiomRegions()
        // 首批八条 + 扩容八条（M/S/T/G——规划 §3 评估后全可落，归纳法族仍暂缓）
        assertEquals(16, r.size, "公理恰十六条: ${r.keys}")
        assertTrue(setOf("map get put", "map put other key", "set insert contains", "set insert other",
            "text length concat", "text concat assoc", "text concat empty", "range membership").all { it in r },
            "M1–M2/S1–S2/T1–T3/G1 命名在位: ${r.keys}")
    }

    /** 门 3（drift 门）：逐条公理命题形核对——改任一公理的命题即红（防静默漂移）。 */
    @Test fun 公理命题形drift门() {
        val r = axiomRegions()
        // 名 → 命题形关键片段（head/谓词名/构造子/字面量/量词符——命题位静默解析，符号名即断言形）
        val expect = mapOf(
            "nat non-negative" to listOf<String>("\"head\": \">=\"", "\"value\": \"0\""),
            "nat add monotone" to listOf("\"head\": \">=\"", "\"op\": \"+\""),
            "nat sub non-negative" to listOf("\"kind\": \"HirPIff\"", "\"op\": \"-\""),
            "list length nil" to listOf("\"name\": \"Nil\"", "\"value\": \"0\""),
            "list length cons" to listOf("\"name\": \"Cons\"", "\"op\": \"+\""),
            "list append nil" to listOf("\"name\": \"append\"", "\"name\": \"Nil\""),
            "list append assoc" to listOf("\"name\": \"append\""),
            "list length append" to listOf("\"name\": \"append\"", "\"op\": \"+\""),
            "map get put" to listOf("\"name\": \"get\"", "\"name\": \"put\"", "\"name\": \"Some\""),
            "map put other key" to listOf("\"kind\": \"HirPImp\"", "\"head\": \"!=\""),
            "set insert contains" to listOf("\"name\": \"insert\"", "\"name\": \"contains\"", "\"name\": \"True\""),
            "set insert other" to listOf("\"kind\": \"HirPImp\"", "\"name\": \"insert\""),
            "text length concat" to listOf("\"name\": \"concat\"", "\"op\": \"+\""),
            "text concat assoc" to listOf("\"name\": \"concat\""),
            "text concat empty" to listOf("\"kind\": \"HirTStr\"", "\"value\": \"\""),
            "range membership" to listOf("\"kind\": \"HirPIff\"", "\"head\": \"in\"", "\"op\": \"..\""),
        )
        assertEquals(expect.keys, r.keys, "drift 门公理集（增删条目即红）")
        expect.forEach { (name, frags) ->
            val seg = r[name]!!
            frags.forEach { f ->
                assertTrue(f in seg, "公理 $name 命题形缺片段 $f（drift：命题被改即触红）")
            }
        }
        // 结合律/结合族出现次数下界（命题形强度：三处 append / 四处 concat）
        assertTrue((r["list append assoc"]!!.split("\"name\": \"append\"").size - 1) >= 3, "append 结合律须三处 append")
        assertTrue((r["text concat assoc"]!!.split("\"name\": \"concat\"").size - 1) >= 4, "concat 结合律须四处 concat")
    }

    /** 门 4（注入≡规格）：类型注入快路径与 A1 公理声明同一断言（n >= 0）——分家但口径对应。 */
    @Test fun 注入面与A1规格同断言() {
        // 用户 post <n >= 0> 走类型注入（D-NAT-PROP，provenance=type）绿——快路径侧
        val o = scriptStd("fun f(n: Nat): Nat <n >= 0> = n")
        assertEquals(emptyList<String>(), errs(o), "<n >= 0> 经类型注入可证（快路径侧）: " + errs(o))
        // A1 公理声明同形断言（>= 与 0）——规格侧（drift 门同一片段断言）
        val seg = axiomRegions()["nat non-negative"]!!
        assertTrue("\"head\": \">=\"" in seg && "\"value\": \"0\"" in seg,
            "A1 与注入断言同形（>= 0）——改 A1 即漂移报差")
    }
}
