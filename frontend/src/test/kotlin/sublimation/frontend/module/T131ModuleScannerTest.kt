package sublimation.frontend.module

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * t131（S7 / R-22）：目录树 ↔ 模块树扫描——挂载期错误族（预判早死，提案 §1.2 / OQ-7）。
 * 负例先红：E-MOUNT-* / E-MODULE-EMPTY 在扫描期落袋，不进模块编译。 */
class T131ModuleScannerTest {

    private fun root(tag: String): File {
        val d = File("frontend/build/tmp/t131/" + tag)
        if (d.exists()) d.deleteRecursively()
        d.mkdirs()
        return d
    }

    private fun errCodes(bag: DiagBag): List<String> =
        bag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    /** 正：目录即模块 + 显式挂载 + 别名默认 + 源文件字典序。 */
    @Test fun 绿扫描挂载与源序() {
        val r = root("scan-green")
        File(r, "主.subl").writeText("fun main() { }\n", Charsets.UTF_8)
        val tool = File(r, "tool").apply { mkdirs() }
        File(tool, "b.subl").writeText("fun g(n : Nat) : Nat = n\n", Charsets.UTF_8)
        File(tool, "a.subl").writeText("fun f(n : Nat) : Nat = n\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount tool\n", Charsets.UTF_8)
        val bag = DiagBag()
        val tree = ModuleScanner(r, bag).scan()
        assertEquals(emptyList<String>(), errCodes(bag))
        assertEquals(1, tree.mounts.size)
        assertEquals("tool", tree.mounts.first().alias)
        assertEquals(listOf("a.subl", "b.subl"), tree.mounts.first().node.sourceFiles.map { it.name },
            "模块体按文件名字典序")
    }

    /** 正：无 .settings 的目录 = 零挂载单文件模块（子目录默认不自动挂载——R-22 否隐式）。 */
    @Test fun 无settings零挂载子目录不自动扫() {
        val r = root("scan-nosettings")
        File(r, "主.subl").writeText("fun main() { }\n", Charsets.UTF_8)
        val td = File(r, "tool"); td.mkdirs()
        File(td, "x.subl").writeText("fun x() { }\n", Charsets.UTF_8)
        val bag = DiagBag()
        val tree = ModuleScanner(r, bag).scan()
        assertEquals(emptyList<String>(), errCodes(bag))
        assertEquals(0, tree.mounts.size, "无 .settings ⇒ 零挂载（子目录非模块——挂载须显式）")
    }

    /** 负：挂载目标不是目录 → E-MOUNT-MISSING。 */
    @Test fun 挂载目标缺失红() {
        val r = root("scan-missing")
        File(r, "主.subl").writeText("fun main() { }\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount nope\n", Charsets.UTF_8)
        val bag = DiagBag()
        ModuleScanner(r, bag).scan()
        assertEquals(listOf("E-MOUNT-MISSING"), errCodes(bag))
    }

    /** 负：挂载逃逸编译根 → E-MOUNT-OUT-OF-ROOT（OQ-11 拒逃逸）。 */
    @Test fun 挂载逃逸根红() {
        val r = root("scan-outofroot")
        File(r, "主.subl").writeText("fun main() { }\n", Charsets.UTF_8)
        File(r.parentFile, "outside-t131").mkdirs()
        File(r, ".settings").writeText("mount ../outside-t131\n", Charsets.UTF_8)
        val bag = DiagBag()
        ModuleScanner(r, bag).scan()
        assertEquals(listOf("E-MOUNT-OUT-OF-ROOT"), errCodes(bag))
    }

    /** 负：挂载环 → E-MOUNT-CYCLE（模块图须 DAG）。 */
    @Test fun 挂载环红() {
        val r = root("scan-cycle")
        File(r, "主.subl").writeText("fun main() { }\n", Charsets.UTF_8)
        val a = File(r, "a").apply { mkdirs() }
        val b = File(r, "b").apply { mkdirs() }
        File(a, "f.subl").writeText("fun fa() { }\n", Charsets.UTF_8)
        File(b, "g.subl").writeText("fun gb() { }\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount a\n", Charsets.UTF_8)
        File(a, ".settings").writeText("mount ../b\n", Charsets.UTF_8)
        File(b, ".settings").writeText("mount ../a\n", Charsets.UTF_8)
        val bag = DiagBag()
        ModuleScanner(r, bag).scan()
        val codes = errCodes(bag)
        assertTrue("E-MOUNT-CYCLE" in codes, "环须落 E-MOUNT-CYCLE: $codes")
    }

    /** 负：同模块别名重复 → E-MOUNT-DUP-ALIAS。 */
    @Test fun 别名重复红() {
        val r = root("scan-dupalias")
        File(r, "主.subl").writeText("fun main() { }\n", Charsets.UTF_8)
        val ad = File(r, "a"); ad.mkdirs()
        File(ad, "f.subl").writeText("fun fa() { }\n", Charsets.UTF_8)
        val bd = File(r, "b"); bd.mkdirs()
        File(bd, "g.subl").writeText("fun gb() { }\n", Charsets.UTF_8)
        File(r, ".settings").writeText("mount a as t\nmount b as t\n", Charsets.UTF_8)
        val bag = DiagBag()
        ModuleScanner(r, bag).scan()
        assertEquals(listOf("E-MOUNT-DUP-ALIAS"), errCodes(bag))
    }

    /** 负：模块体无 .subl → E-MODULE-EMPTY。 */
    @Test fun 空模块红() {
        val r = root("scan-empty")
        File(r, "主.subl").writeText("fun main() { }\n", Charsets.UTF_8)
        File(r, "empty").mkdirs()
        File(r, ".settings").writeText("mount empty\n", Charsets.UTF_8)
        val bag = DiagBag()
        ModuleScanner(r, bag).scan()
        assertEquals(listOf("E-MODULE-EMPTY"), errCodes(bag))
    }
}
