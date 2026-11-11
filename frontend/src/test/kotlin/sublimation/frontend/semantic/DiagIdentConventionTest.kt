package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.diag.ident
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * t69（D2 诊断规范）防回退测试：诊断中出现的标识符一律反引号包裹（用户裁决 2026-11-08）。
 *
 * 两层覆盖：
 * - 管线层：代表性诊断码（E-DUP-DECL / E-UNBOUND-NAME / E-TYPE-MISMATCH / E-DEFAULT-TYPE /
 *   E-VARARG-LAST / H-SHADOW）各一条，断言输出文本含反引号包裹的名字；
 *   E-FIXITY-CONFLICT 在当前码集中不存在（grep 零命中），按任务「若存在」口径略过。
 * - 助手层：ident() 的转义契约——反引号转义、换行/回车/制表/引号转义显示（诊断不得跨行）、
 *   空格名成对反引号。
 */
class DiagIdentConventionTest {

    private fun script(src: String): DiagBag {
        val pr = parseScriptSourceWithNotes(src, "t69.subl")
        val (_, bag) = compile(pr.file, pr.notes)
        pr.diag.forEach { d ->
            when (d.severity) {
                Severity.ERROR -> bag.error(d.code, d.pos, d.message)
                Severity.WARN -> bag.warn(d.code, d.pos, d.message)
                Severity.HINT -> bag.hint(d.code, d.pos, d.message)
                Severity.SUPPLEMENT -> bag.supplement(d.code, d.pos, d.message)
            }
        }
        return bag
    }

    private fun msgs(bag: DiagBag) = bag.diags.joinToString(" | ") { it.code + ":" + it.message }

    // ============================ 管线层：代表性诊断码 ============================

    @Test
    fun dupDeclNameIsQuoted() {
        val bag = script("fun f(): Nat = 1\nfun f(): Nat = 2")
        val hit = bag.diags.any { it.code == "E-DUP-DECL" && it.message.contains("函数 `f` 重复声明") }
        assertTrue(hit, "E-DUP-DECL 文案含 重复声明`f`。实际: " + msgs(bag))
    }

    @Test
    fun unboundNameIsQuoted() {
        val bag = script("var x = g()")
        val hit = bag.diags.any { it.code == "E-UNBOUND-NAME" && it.message.contains("`g`") }
        assertTrue(hit, "E-UNBOUND-NAME 文案含 `g`。实际: " + msgs(bag))
    }

    @Test
    fun typeMismatchNameIsQuoted() {
        val bag = script("fun f(): Str = 1")
        val hit = bag.diags.any { it.code == "E-TYPE-MISMATCH" && it.message.contains("函数 `f` 声明返回") }
        assertTrue(hit, "E-TYPE-MISMATCH 文案含 函数`f`。实际: " + msgs(bag))
    }

    @Test
    fun defaultTypeNameIsQuoted() {
        val bag = script("fun f(x: Nat = \"s\"): Nat = x")
        val hit = bag.diags.any { it.code == "E-DEFAULT-TYPE" && it.message.contains("形参 `x` 的类型") }
        assertTrue(hit, "E-DEFAULT-TYPE 文案含 形参`x`。实际: " + msgs(bag))
    }

    @Test
    fun varargLastNameIsQuoted() {
        val bag = script("fun f(a: Nat, @list b: Nat, c: Nat): Nat = a")
        val hit = bag.diags.any { it.code == "E-VARARG-LAST" && it.message.contains("变长形参 `b` 只能出现在最后一个形参位") }
        assertTrue(hit, "E-VARARG-LAST 文案含 变长形参`b`。实际: " + msgs(bag))
    }

    @Test
    fun shadowHintNameIsQuoted() {
        val bag = script("fun f(): Nat { var x: Nat = 1\nvar x: Nat = 2\nx }")
        val hit = bag.diags.any { it.code == "H-SHADOW" && it.message.contains("这里的 `x` 遮蔽了") }
        assertTrue(hit, "H-SHADOW 文案含 这里的`x`遮蔽了。实际: " + msgs(bag))
    }

    /**
     * 验收 #3 管线层反向用例（契约焊接）：源里 \` 是字面反引号（t68 B2/ADR-45），
     * 诊断里标识符反引号包裹且内嵌反引号转义（t69 D2/ADR-46）——两者写法必须一致，
     * 诊断文本可原样复制回源码。
     */
    @Test
    fun backtickNamedDupRoundtrips() {
        val bag = script("fun `a\\`b`(): Nat = 1\nfun `a\\`b`(): Nat = 2")
        val hit = bag.diags.any { it.code == "E-DUP-DECL" && it.message.contains("`a\\`b`") }
        assertTrue(hit, "反引号名双声明落 E-DUP-DECL，且诊断文本含 `a\\`b`（可复制回源码）。实际: " + msgs(bag))
    }

    // ============================ 助手层：ident() 转义契约（验收 3/4/5） ============================

    @Test
    fun identWrapsPlainName() {
        assertEquals("`foo`", ident("foo"), "普通名 → `foo`")
    }

    @Test
    fun identEscapesInnerBacktickRoundtrip() {
        // 反向用例（任务核心交付）：名字本身含反引号 ⇒ 输出 `a\\`b`——转义正确、不被截断、可复制回源码
        assertEquals("`a\\`b`", ident("a`b"), "a`b → `a\\`b`")
    }

    @Test
    fun identRendersSpaceName() {
        assertEquals("` `", ident(" "), "空格名 → ` `（成对反引号，不是空白）")
    }

    @Test
    fun identEscapesControlCharsSingleLine() {
        // 诊断不得跨行：换行/回车/制表一律显示形态（Validation 验收 #5）
        val out = ident("a\nb\tc\rd")
        assertEquals("`a\\nb\\tc\\rd`", out, "控制字符转义为字面 \\n/\\t/\\r")
        assertTrue(out.lines().size == 1, "输出单行不跨行")
        assertEquals("`a\\nb`", ident("a\nb"), "换行名字 → `a\\nb`")
    }

    @Test
    fun identEscapesQuoteChars() {
        // t68 后名字可含引号（队长提醒）：先行就绪——转义不破坏包裹配对
        val out = ident("a\"b")
        assertTrue(out == "`a\\\"b`", "引号转义: " + out)
    }

    @Test
    fun identRoundtripsBacktickNameInDiagnostic() {
        // 名字含反引号时诊断文本完整可辨（验收 #3 的助手层佐证）
        val bag = script("fun f(): Nat = 1\nfun f(): Nat = 2")
        val dup = bag.diags.first { it.code == "E-DUP-DECL" }.message
        assertTrue(dup.count { it == '`' } >= 2 && dup.contains("重复声明"), "含名诊断带成对反引号")
        assertTrue(!dup.contains("重复声明 f"), "旧裸名形态已绝迹")
    }
}
