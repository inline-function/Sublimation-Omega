package sublimation.frontend.cli

import sublimation.frontend.json.HirJson
import sublimation.frontend.json.HirSchema
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * t158（HIR JSON schema v3 冻结声明·版本守门）：把 HIR JSON 产品正式作为前后端契约冻结——
 * 产品**必带**顶层 schema 字段且版本=声明的契约版本（sublimation/hir@v3）;未来 breaking
 * 变更走 v4，不破坏既有消费方。
 *
 * 四门（负例先红：门 3 守门负例直接验「缺失/版本不符=失败」）:
 * - **门 1（单文件面）**:compileFrontend 产品的 schema 字段在位且版本锁;顶层键集固定
 *   {schema, compiler, unit, ok, diagnostics, hir}。
 * - **门 2（全导出路径面）**:模块编译（main + 子件 units，含 stdlib 默认挂载的 10 件侧）
 *   每一枚 envelope 的 schema 字段一致——schema 缺失即真契约缺口（先补齐再冻结，实测全绿）。
 * - **门 3（守门负例）**:HirSchema.check 对缺字段/版本不符/诱导文本/非法 envelope **必失败**;
 *   正确 envelope 通过（后端消费方守门样例——v4 产物将被拒收，不静默兼容）。
 * - **门 4（冻结快照）**:HirSchema.CONTRACT 与 HirJson.SCHEMA 单点同源;版本标识逐字冻结为
 *   sublimation/hir@v3（改版本须同步契约文档与本守门——breaking 走新 ADR）。 */
class T158SchemaGateTest {

    private fun schemaOf(json: String): String {
        val q = '"'.toString()   // 引号字面量（char 拼接免转义脑税）
        val m = Regex(q + "schema" + q + ": " + q + "([^" + q + "]*)").find(json)
        return m!!.groupValues[1]
    }

    /** 门 1：单文件面——schema 字段在位 + 顶层键集固定（单文件/脚本两模式）。 */
    @Test fun 单文件面schema字段锁() {
        val o = compileFrontend("fun f(): Nat { return 1 }", "t158a.subl")
        val j = o.json
        assertEquals("sublimation/hir@v3", schemaOf(j), "单文件产品 schema 锁定 v3")
        assertNull(HirSchema.check(j), "守门通过: " + HirSchema.check(j))
        // 顶层键集固定（发射序）：schema → compiler → unit → ok → diagnostics → hir
        val keys = Regex("(?m)^  \"([a-z]+)\":").findAll(j).map { it.groupValues[1] }.toList()
        assertEquals(listOf("schema", "compiler", "unit", "ok", "diagnostics", "hir"), keys,
            "envelope 顶层键集与发射序冻结: " + keys)
        val s = compileFrontend("print(1)", "t158s.subl", scriptMode = true)
        assertEquals("sublimation/hir@v3", schemaOf(s.json), "脚本模式产品同样锁定 v3")
        assertNull(HirSchema.check(s.json), "脚本模式守门通过")
    }

    /** 门 2：全导出路径面——模块编译 main + 全部子件 envelopes（含 stdlib 默认挂载侧）schema 一致。 */
    @Test fun 全导出路径schema一致() {
        val mod = File("frontend/src/test/resources/e2e/examples/模块系统")
        val out = compileModuleFrontend(mod)
        val envs = mutableListOf(out.main.json)
        out.children.forEach { envs.add(it.output.json) }
        val stdlibUnits = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false).first
        stdlibUnits.forEach { envs.add(it.unit.output.json) }
        val n = envs.size
        assertTrue(n >= 18, "覆盖面：模块主件 + 全部挂载子件 + stdlib 默认挂载件: $n");
        envs.forEachIndexed { idx, j ->
            assertEquals("sublimation/hir@v3", schemaOf(j), "路径 $idx schema 锁定 v3")
            assertNull(HirSchema.check(j), "路径 $idx 守门失败: " + HirSchema.check(j))
        }
    }

    /** 门 3（守门负例）：缺字段/版本不符/诱导/非法 envelope——check 必失败;正确 envelope 通过。 */
    @Test fun 守门负例缺失版本不符必失败() {
        // (1) 缺 schema 键 → 失败
        // (1) 缺 schema 键 → 失败
        val miss = """{ "compiler": {"version": "0.1-SNAPSHOT"}, "ok": true }"""
        assertNotNull(HirSchema.check(miss), "缺 schema 字段须失败")
        // (2) 版本不符（v2 旧版 / v4 未来版）→ 失败（v4 亦拒收——不许就地兼容）
        val v2 = """{ "schema": "sublimation/hir@v2", "ok": true }"""
        assertNotNull(HirSchema.check(v2), "旧版本须失败")
        val v4 = """{ "schema": "sublimation/hir@v4", "ok": true }"""
        assertNotNull(HirSchema.check(v4), "未来 v4 须失败（消费方拒收，不静默兼容）")
        // (3) 诱导文本：诊断消息内含冒号+假版本（串内文本）但顶层正确 → 通过
        val q = '"' + ""
        val decoy = ("{" + q + "schema" + q + ": " + q + "sublimation/hir@v3" + q + ", " + q + "diagnostics" + q +
            ": [ { " + q + "message" + q + ": " + q + "trap " + q + "schema" + q + ": " + q + "fake" + q + " in-string" + q + " } ] }")
        assertNull(HirSchema.check(decoy), "诱导文本不误伤（串内同名文本非顶层键）")
        // (4) 嵌套同名键（hir 节点内）——深度 ≥1 无效，顶层缺 → 失败
        val nested = """{ "hir": { "kind": "HirFile", "schema": "sublimation/hir@v3" } }"""
        assertNotNull(HirSchema.check(nested), "嵌套 schema 键无效（只认顶层）")
        // (5) 非法 envelope（空串/非对象）→ 失败
        assertNotNull(HirSchema.check(""), "空串须失败")
        assertNotNull(HirSchema.check("[1, 2, 3]"), "非对象须失败")
        assertNotNull(HirSchema.check("[1, 2, 3]"), "非对象须失败")
    }

    /** 门 4：冻结快照——契约版本与发射器单源同体; v3 标识逐字冻结。 */
    @Test fun 契约版本单源冻结() {
        assertEquals(HirJson.SCHEMA, HirSchema.CONTRACT, "守门契约版本透传发射器唯一真源")
        assertEquals("sublimation/hir@v3", HirSchema.CONTRACT, "v3 冻结标识逐字锁定（breaking 走新 ADR + v4）")
        // 守门与等价产品同构：人造型 v3 envelope 通过（守门不依赖具体样本内容）
        assertNull(HirSchema.check("""{ "schema": "sublimation/hir@v3" }"""),
            "人造型 v3 envelope 守门通过（守门只断 schema 契约面）")
    }
}
