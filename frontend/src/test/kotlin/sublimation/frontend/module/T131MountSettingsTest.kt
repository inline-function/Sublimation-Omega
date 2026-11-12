package sublimation.frontend.module

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * t131（S7 / R-22）：.settings 挂载层行解析——mount 行文法（正/负）。
 * 负例先红：非法行在解析期即落 E-MOUNT-SYNTAX，不进模块编译（挂载期预判早死）。 */
class T131MountSettingsTest {

    private fun parse(text: String): Pair<List<MountSpec>, List<String>> {
        val bag = DiagBag()
        val specs = MountSettings.parse(text, File("t131.settings"), bag)
        return specs to bag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    }

    /** 正：三形态（裸路径 / as 别名 / 默认别名）+ 双注释（# 与 //）+ 空行。 */
    @Test fun mount行三形态与双注释() {
        val (specs, errs) = parse("# 约定注释\n\nmount tool\nmount ../lib as lib\n// 行注释\nmount a/b\n")
        assertEquals(listOf("tool", "../lib", "a/b"), specs.map { it.rawPath })
        assertEquals(listOf(null, "lib", null), specs.map { it.alias })
        assertEquals(emptyList<String>(), errs, "合法 .settings 零红")
    }

    /** 负：行文法四红（缺路径 / 冗余段 / 非 mount 行 / 别名含路径符）。 */
    @Test fun 行文法红挂载期早死() {
        val (specs, errs) = parse("mount\nmount tool extra as alias\nalias x\nmount p as a/b\n")
        assertEquals(0, specs.size, "非法行不产挂载条目")
        assertEquals(4, errs.size, "四行各一红")
        assertTrue(errs.all { it == "E-MOUNT-SYNTAX" }, "码族恒为 E-MOUNT-SYNTAX: $errs")
    }
}
