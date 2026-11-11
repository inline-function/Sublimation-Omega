package sublimation.frontend.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * t111（D-12 修复，队长代码审查发现）:模式真值单源化——scriptMode 由 CLI 唯一源决定（.subs 扩展名 /
 * --script 旗标，Main.kt:46)，经 compileFrontend → compile → Elaborator / HirJson.emit 一路透传；
 * 废除两处自算：
 *   ① Elaborator「顶层语句存在⇒脚本」——工程模式顶层 axiom 语句（Parser:204）落 StmtEntry 致误标；
 *   ② HirJson.unitMode 同一判据继承同一错。
 * 验收门重定：68 样本 67 SAME,公理化.subl envelope mode script→project 为**缺陷修复预期差**（附 D-12）。
 * （修前红态证据：同一源在 CLI 直编 mode="script"，已随任务归档。）
 */
class T111ModeTruthTest {

    private fun mode(json: String): String =
        if (json.contains("\"mode\": \"script\"")) "script" else "project"

    /** D-12 红态遗留:工程 .subl 顶层 axiom,默认（project）下 envelope 必为 project。 */
    @Test fun 工程模式顶层axiom判为project() {
        val src = "axiom moduleReady\n" +
            "fun produceSetup(): Null <setupOk> { axiom setupOk }"
        val out = compileFrontend(src, "公理化.subl")   // 默认 project
        assertEquals("project", mode(out.json),
            "D-12:工程 .subl 顶层 axiom 不得再误标 script（旧自算判据 Elaborator:578 / HirJson:187）")
    }

    /** 透传:scriptMode=true 时 envelope 为 script（CLI 唯一源显式覆盖——含 .subl 扩展名边界）。 */
    @Test fun 显式scriptMode覆盖扩展名() {
        val out = compileFrontend("var f = { _ * 3 }\nvar r = f(4)", "t.subl", scriptMode = true)
        assertEquals("script", mode(out.json),
            "--script 旗标为 CLI 真值:即便扩展名 .subl,真值即 script")
        assertTrue(out.diag.diags.filter { it.severity == sublimation.frontend.diag.Severity.ERROR }.isEmpty(),
            "scriptMode=true:Parser 按脚本分派,顶层语句放行（真值单源下脚本宽待仍生效）")
    }

    /** D-12:compileFrontend 不嗅探扩展名——真值只从调用方传入（Main.kt:46 的 .subs 判定在 CLI 层）。 */
    @Test fun 函数层不嗅探扩展名() {
        val out = compileFrontend("print(1)", "t.subs")   // 未传 scriptMode → 默认 project
        assertEquals("project", mode(out.json),
            "扩展名判定在 CLI 层（Main.kt:46）,compileFrontend 只吃调用方真值——杜绝第二真值源")
    }

    /** 默认 project:纯声明树 envelope 为 project（emit 直测兼容口径——envelope 不读树条目）。 */
    @Test fun emit默认判为project() {
        val out = compileFrontend("fun f(): Nat = 1", "lib.subl")
        assertEquals("project", mode(out.json),
            "emit 默认 project;废 unitMode 自算后 envelope 不由 HirStmtEntry 存在与否决定")
    }
}
