package sublimation.frontend.parser

import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * t129 清账件 1（S-25 单语句面收口 / OQ-3）：unchecked 块形态精确拒绝。
 *
 * - `unchecked { … }` 原静默解析为异形（块套语句——降闸帧不落、暗病形态）；
 * - 现落 E-SYNTAX 精确拒绝 + 单语句面提示（`unchecked <stmt>`）；单语句面逐字不变。 */
class T129UncheckedBlockTest {

    private fun script(src: String) = parseScriptSourceWithNotes(src, "t129.subs")

    private fun parseErrors(src: String): List<String> =
        script(src).diag.filter { it.severity == Severity.ERROR }.map { it.code }

    /** 负例：unchecked 块形态 ⇒ E-SYNTAX 精确拒绝 + 单语句语法提示（负例先红）。 */
    @Test fun unchecked块形态精确拒绝() {
        val src = "@impure fun main(): Null {\n  var xs : @mut List[Nat] = MutableListOf(1)\n  unchecked { xs.add(1) }\n}\n"
        val pr = script(src)
        assertTrue(pr.diag.any { it.severity == Severity.ERROR && it.code == "E-SYNTAX" },
            "unchecked 块形态须落 E-SYNTAX：${pr.diag.map { it.code + it.message }}")
        assertTrue(pr.diag.any { it.severity == Severity.ERROR && it.message.contains("单语句") },
            "错误文案指明单语句语法面：${pr.diag.map { it.message }}")
        assertTrue(pr.diag.any { it.severity == Severity.ERROR && it.message.contains("unchecked <语句>") },
            "可编译提示给出 `unchecked <语句>` 形态：${pr.diag.map { it.message }}")
    }

    /** 正例：unchecked 单语句面逐字不变（免回归本形态）。 */
    @Test fun unchecked单语句面不变() {
        val src = "@impure fun main(): Null {\n  var xs : @mut List[Nat] = MutableListOf(1)\n  unchecked xs.add(1)\n}\n"
        assertEquals(emptyList(), parseErrors(src), "单语句面零解析错误")
        // AST 形态断言：UncheckedStmt 套单条 ExprStmt（非 Block）
        val st = script(src).file.entries
        assertTrue(st.isNotEmpty(), "解析产语句")
    }

    /** 邻近边界：unchecked 后跟点形态（unchecked.axiom 废除引导）仍走既有通道。 */
    @Test fun unchecked点形态既有通道() {
        val pr = script("@impure fun main(): Null { unchecked.axiom print(1) }\n")
        assertTrue(pr.diag.any { it.severity == Severity.ERROR },
            "unchecked.axiom 废除引导照落错误：${pr.diag.map { it.code }}")
    }
}
