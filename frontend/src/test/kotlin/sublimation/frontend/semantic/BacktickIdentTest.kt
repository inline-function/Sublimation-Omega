package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirFile
import sublimation.frontend.json.HirJson
import sublimation.frontend.lexer.Kind
import sublimation.frontend.lexer.Lexer
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t68（用户 2026-11-08 17:0x 裁决放开反引号标识符）：B1 任意字符含裸换行；B2 唯一转义 \'` =
 * 字面反引号；B3 反引号不进名字（`foo` ≡ foo)；B4 单/多空格为合法名字且互不相同；
 * B5 空名落新码 E-BACKTICK-EMPTY。诊断指向 token 起始行；跨行 token 不触发中缀糖（D-5 同行口径）。
 */
class BacktickIdentTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "bt.subl")
        val (hir, bag) = compile(pr.file, pr.notes)
        pr.diag.forEach { d ->
            when (d.severity) {
                Severity.ERROR -> bag.error(d.code, d.pos, d.message)
                Severity.WARN -> bag.warn(d.code, d.pos, d.message)
                Severity.HINT -> bag.hint(d.code, d.pos, d.message)
                Severity.SUPPLEMENT -> bag.supplement(d.code, d.pos, d.message)
            }
        }
        return hir to bag
    }

    private fun errCodes(bag: DiagBag) = bag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    private fun lex(src: String): Pair<List<sublimation.frontend.lexer.Token>, List<sublimation.frontend.lexer.LexError>> {
        val l = Lexer(src, "bt.subl")
        return l.tokens().toList().filter { it.kind != Kind.EOF } to l.errors
    }

    /** #1：结构字符齐特的合法函数名——声明 + 调用通过。 */
    @Test fun t68_1_arbitraryStructCharsName() {
        val (ts, errs) = lex("`fghjk)(*&^%$#`")
        assertEquals(1, ts.size, "单 token；actual=" + ts)
        assertEquals(Kind.IDENT, ts[0].kind)
        assertEquals("fghjk)(*&^%$#", ts[0].text)
        assertEquals(emptyList<sublimation.frontend.lexer.LexError>(), errs)
        val (_, bag) = script("fun `fghjk)(*&^%$#`(a: Nat): Nat = a\nvar r = `fghjk)(*&^%$#`(1)")
        assertTrue(errCodes(bag).none { it.startsWith("E-") }, "#1 声明+调用应通过，actual=" + errCodes(bag))
    }

    /** #2:CJK 与 ASCII 混杂名字。 */
    @Test fun t68_2_cjkMixedName() {
        val (ts, errs) = lex("`】；23htfv中文中文`")
        assertEquals(1, ts.size)
        assertEquals("】；23htfv中文中文", ts[0].text)
        assertEquals(emptyList<sublimation.frontend.lexer.LexError>(), errs)
        val (_, bag) = script("fun `】；23htfv中文中文`(a: Nat): Nat = a\nvar r = `】；23htfv中文中文`(1)")
        assertTrue(errCodes(bag).none { it.startsWith("E-") }, "#2 CJK 名字应通过，actual=" + errCodes(bag))
    }

    /** #3:单空格与双空格是两个不同标识符（B4）——词法层区分 + 语义层并存无 E-DUP-DECL。 */
    @Test fun t68_3_spaceNamesAreDistinct() {
        val (ts, errs) = lex("` ` + `  `")
        val idents = ts.filter { it.kind == Kind.IDENT }   // + 号是独立 PLUS token，不进名字
        assertEquals(2, idents.size)
        assertEquals(" ", idents[0].text)
        assertEquals("  ", idents[1].text)
        assertEquals(emptyList<sublimation.frontend.lexer.LexError>(), errs)
        val (_, bag) = script("fun ` `(a: Nat): Nat = a\nfun `  `(b: Nat): Nat = b")
        assertFalse(errCodes(bag).contains("E-DUP-DECL"), "#3 两个空格名互不冲突，actual=" + errCodes(bag))
    }

    /** #4:B2 转义——`\`` 的名字是单个反引号字符。 */
    @Test fun t68_4_escapedBacktickIsSingleQuote() {
        val (ts, errs) = lex("`\\``")
        assertEquals(1, ts.size)
        assertEquals("`", ts[0].text, "B2:名字为一个反引号字符")
        assertEquals(emptyList<sublimation.frontend.lexer.LexError>(), errs)
        val (_, bag) = script("fun `\\``(a: Nat): Nat = a\nvar r = `\\``(1)")
        assertTrue(errCodes(bag).none { it.startsWith("E-") }, "#4 转义名声明+调用应通过，actual=" + errCodes(bag))
    }

    /** #5:B1 裸换行进名字——单 token、token 行号=起始行、声明+调用通过；诊断指向起始行。 */
    @Test fun t68_5_multilineNameStaysOneToken() {
        val (ts, errs) = lex("`x\ny`")
        assertEquals(1, ts.size, "跨行名字仍是单 token")
        assertEquals("x\ny", ts[0].text)
        assertEquals(1, ts[0].line, "token 记起始行")
        assertEquals(emptyList<sublimation.frontend.lexer.LexError>(), errs)
        val (_, bag) = script("fun `x\ny`(a: Nat): Nat = a\nvar r = `x\ny`(1)")
        assertTrue(errCodes(bag).none { it.startsWith("E-") }, "#5 跨行名声明+调用应通过，actual=" + errCodes(bag))
        // 跨行名的引用诊断指向 token 起始行（未声明探针）
        val (h2, b2) = script("var r = `x\ny`(1)")
        val unbound = b2.diags.firstOrNull { it.code == "E-UNBOUND-NAME" }
        assertTrue(unbound != null, "#5 未声明应报 E-UNBOUND-NAME")
        assertEquals(1, unbound!!.pos.start.line, "诊断指向 token 起始行")
    }

    /** #6:B3——`foo` 与 foo 同名，重复声明落 E-DUP-DECL。 */
    @Test fun t68_6_backtickEqualsPlainName() {
        val (_, bag) = script("fun foo(a: Nat): Nat = a\nfun `foo`(b: Nat): Nat = b")
        assertTrue(errCodes(bag).contains("E-DUP-DECL"), "#6 `foo` 与 foo 同名须 E-DUP-DECL,actual=" + errCodes(bag))
    }

    /** #7:B5——空反引号落新码 E-BACKTICK-EMPTY（词法 + 语义两层）。 */
    @Test fun t68_7_emptyBacktickRejected() {
        val (ts, errs) = lex("``")
        assertEquals(1, ts.size, "空名仍产 IDENT 占位（不停机，S-1 §1）")
        assertEquals("", ts[0].text)
        assertEquals("E-BACKTICK-EMPTY", errs.single().code, "#7 词法层码")
        val (_, bag) = script("fun ``(a: Nat): Nat = a")
        assertTrue(errCodes(bag).contains("E-BACKTICK-EMPTY"), "#7 语义诊断袋含码，actual=" + errCodes(bag))
    }

    /** #8:名字含裸换行 → hir.json 转义完整（\n 序列存在，无裸换行夹引号）。 */
    @Test fun t68_8_jsonEscapesMultilineName() {
        val (hir, bag) = script("fun `x\ny`(a: Nat): Nat = a\nvar r = `x\ny`(1)")
        assertEquals(emptyList<String>(), errCodes(bag))
        val json = HirJson.emit(hir, bag, "bt.subl")
        assertTrue(json.contains("\"x\\ny\""), "#8 名字须转义为 \"x\\ny\" 形态")
        assertFalse(json.contains("\"x\ny\""), "#8 字符串值内不得出现裸换行")
    }

    /** #9:既有反引号用法（如 `set pairing`）逐字不变。 */
    @Test fun t68_9_existingBacktickUsageUnchanged() {
        val (ts, errs) = lex("`set pairing`")
        assertEquals(1, ts.size)
        assertEquals("set pairing", ts[0].text)
        assertEquals(emptyList<sublimation.frontend.lexer.LexError>(), errs)
        val (_, bag) = script("fun `set pairing`(a: Nat): Nat = a\nvar r = `set pairing`(1)")
        assertTrue(errCodes(bag).none { it.startsWith("E-") }, "#9 既有用法逐字不变，actual=" + errCodes(bag))
    }

    /** #10:`a` 与 `a` 同名重复——E-DUP-DECL（B3 对同形反引号名同样成立）。 */
    @Test fun t68_10_sameBacktickNameDuplicates() {
        val (_, bag) = script("fun `a`(a: Nat): Nat = a\nfun `a`(b: Nat): Nat = b")
        assertTrue(errCodes(bag).contains("E-DUP-DECL"), "#10 须 E-DUP-DECL,actual=" + errCodes(bag))
    }
}