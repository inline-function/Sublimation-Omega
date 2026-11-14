package sublimation.frontend.cli

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t165（P-L1 / LSP 前置件，LSP-框架大纲 §4.4 评估结论）：公共编译入口的 **stdlib 默认挂载**通道。
 *
 * 缺口（大纲 §4.4 实测，负例先红口径）:
 * - 公共零挂载入口 [compileFrontend] 不装配 stdlib——进程内嵌入方（LSP）编裸名 sum/range/
 *   Cons/Nil 落 E-UNBOUND-NAME（门 0 红态锁定 = 缺口举证 + **旧入口语义零回归守卫**：P-L1
 *   不得改动既有公共入口行为——前端零回归纪律）;
 * - 单文件 CLI 装配（stdlibRoot → compileStdlibUnits → mounted 快照 + 挂载期诊断并入主袋）
 *   原写死在 Main.kt——嵌入方手工复刻即与 CLI 口径分家（**复刻漂移**：旧 LSP 自拼 core 目录
 *   之教训，大纲 §0.1）。门 4 负例举证：复刻若漏挂载期诊断（extraDiag）即丢 E-MOUNT-MISSING。
 *
 * 落地（P-L1 裁决）:新增公共入口 [compileFrontendWithStdlib]——CLI 单文件层复用同一通道
 * （Main.kt 不再自行装配）；门 6 机械证 = CLI compile 单文件产物与新入口产物**逐字节同**。 */
class T165PublicStdlibEntryTest {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code + ":" + it.message }

    /** 门 0（缺口举证 + 零回归守卫）：旧公共入口（零挂载）裸名 Cons/Nil/sum 红。 */
    @Test fun 旧公共入口零挂载裸名红() {
        val o = compileFrontend("fun m(): Nat { var xs = Cons(1, Nil) return sum(xs) }",
            "t165gap.subs", scriptMode = true)
        val codes = errs(o).map { it.substringBefore(":") }
        assertTrue("E-UNBOUND-NAME" in codes, "零挂载公共入口：Cons/Nil/sum 裸名须红（缺口举证）: $codes")
        // 本门同时是 P-L1 的零回归守卫：既有公共入口 compileFrontend 的零挂载语义不许被
        // P-L1 改动（E2ERegressionTest 等既有消费方按此语义落盘——68 门口径保护）。
    }

    /** 门 1：新入口脚本模式裸名绿（stdlib 快照注入，同 CLI 单文件口径）。 */
    @Test fun 新入口脚本模式裸名绿() {
        val o = compileFrontendWithStdlib("fun m(): Nat = sum(range(1, 10))",
            "t165s.subs", scriptMode = true)
        assertEquals(emptyList<String>(), errs(o), "sum/range 经 stdlib 快照解析: " + errs(o))
        val o2 = compileFrontendWithStdlib("fun m(): Nat = summation(1, 101)",
            "t165s2.subs", scriptMode = true)
        assertEquals(emptyList<String>(), errs(o2), "summation 经 stdlib(math) 快照解析: " + errs(o2))
    }

    /** 门 2：新入口工程模式裸名绿（unchecked 段内构造同 T137StdlibB0Test 工程 形态口径）。 */
    @Test fun 新入口工程模式裸名绿() {
        val o = compileFrontendWithStdlib(
            "fun m(): Nat {\n" +
            "    unchecked var xs = Cons(1, Cons(2, Nil))\n" +
            "    return sum(xs)\n" +
            "}", "t165p.subl", scriptMode = false)
        assertEquals(emptyList<String>(), errs(o), "工程模式 sum(xs) 经 stdlib 快照绿: " + errs(o))
    }

    /** 门 3：noStdlib=true → 零挂裸名红（旗门同 --no-stdlib CI 口径）。 */
    @Test fun noStdlib旗零挂载裸名红() {
        val o = compileFrontendWithStdlib("fun m(): Nat { var xs = Cons(1, Nil) return sum(xs) }",
            "t165ns.subs", scriptMode = true, noStdlib = true)
        val codes = errs(o).map { it.substringBefore(":") }
        assertTrue("E-UNBOUND-NAME" in codes, "noStdlib 下裸名须红（stdlib 默认挂载关闭）: $codes")
        assertTrue(o.diag.hasError, "红态落定")
    }

    /** 门 4（复刻漂移负例）：stdlib 缺件 → 挂载期 E-MOUNT-MISSING 须并入主袋（ok=false）。
     *  手工复刻若只传 mounted、漏挂载期诊断，即丢此条——新入口为唯一装配通道的举证。 */
    @Test fun stdlib缺件挂载期诊断并入主袋() {
        val prev = System.getProperty("sublimation.stdlib")
        try {
            System.setProperty("sublimation.stdlib",
                createTempDir("t165-bad-stdlib").absolutePath + "/不存在")
            val o = compileFrontendWithStdlib("fun m(): Nat = 1", "t165missing.subl", scriptMode = false)
            assertTrue(o.diag.diags.any { it.severity == Severity.ERROR && it.code == "E-MOUNT-MISSING" },
                "挂载期诊断须并入主袋（复刻漏 extraDiag 即丢）: " + o.diag.diags.map { it.code + it.message })
            assertTrue(o.diag.hasError, "早死红态落定（ok=false）")
            assertTrue(o.json.contains("\"ok\": false"), "envelope 携带早死结论")
        } finally {
            if (prev == null) System.clearProperty("sublimation.stdlib") else System.setProperty("sublimation.stdlib", prev)
        }
    }

    /** 门 5：确定性——同源双调逐字节同（轮次确定性 + 层间总契约 3）。 */
    @Test fun 同源双调逐字节同() {
        val src = "fun m(): Nat = sum(range(1, 10))"
        val a = compileFrontendWithStdlib(src, "t165d.subs", scriptMode = true)
        val b = compileFrontendWithStdlib(src, "t165d.subs", scriptMode = true)
        assertFalse(a.diag.hasError, "绿态前提: " + errs(a))
        assertEquals(a.json, b.json, "相同输入必产相同 JSON（层间总契约 3）")
    }

    /** 门 6（CLI 通道复用零漂移）：CLI compile 单文件产物 JSON 与新入口产物逐字节同。 */
    @Test fun CLI通道复用产物逐字节同() {
        val src = "fun m(): Nat {\n" +
            "    unchecked var xs = Cons(1, Cons(2, Nil))\n" +
            "    return sum(xs)\n" +
            "}"
        val dir = File("frontend/build/tmp/cli"); dir.mkdirs()
        val f = File(dir, "t165cli.subl")
        f.writeText(src, Charsets.UTF_8)
        val target = File(dir, "t165cli.hir.json")
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val code = runCli(arrayOf("compile", f.path, "-o", target.path),
            PrintStream(outBytes, true, Charsets.UTF_8), PrintStream(errBytes, true, Charsets.UTF_8))
        assertEquals(0, code, "CLI compile 绿态前提: " + outBytes.toString(Charsets.UTF_8) + errBytes.toString(Charsets.UTF_8))
        val viaPublic = compileFrontendWithStdlib(src, "t165cli.subl", scriptMode = false)
        assertEquals(target.readText(Charsets.UTF_8), viaPublic.json,
            "CLI 走新入口通道：产物逐字节同（同一装配，无第二真值源）")
    }
}
