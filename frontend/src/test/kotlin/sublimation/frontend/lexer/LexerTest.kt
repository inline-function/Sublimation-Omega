package sublimation.frontend.lexer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 词法层测试。组成：
 * 1. 回归移植旧 frontend/src/test/.../lexer/LexerTest.kt（旧测试是行为规格，D-4；S-3 §2.3）
 * 2. 《形式化文法》§1（L-1~L-12）逐项覆盖与 S-1 §1 契约（流式/带位置/错误不停机）。
 */
class LexerTest {
    private fun toks(s: String) = Lexer(s, "t.subl").tokenize()
    private fun lex(s: String) = Lexer(s, "t.subl")

    // ===== 回归移植：旧 LexerTest.kt =====

    @Test
    fun 紧贴尖括号无空格标记() {
        val ts = toks("witness<torch>()")
        assertEquals(Kind.IDENT, ts[0].kind)
        assertEquals(Kind.LT, ts[1].kind)
        assertFalse(ts[1].precededBySpace)
        assertEquals(Kind.GT, ts[3].kind)
        assertFalse(ts[3].precededBySpace)
    }

    @Test
    fun 带空格尖括号标记为比较() {
        val ts = toks("a < b")
        assertTrue(ts[1].precededBySpace)
    }

    @Test
    fun 符号名成为SYMBOL标识符() {
        val ts = toks("fun ○(a: Bool): Bool")
        assertEquals(Kind.SYMBOL, ts[1].kind)
        assertEquals("○", ts[1].text)
    }

    /** L-8【逻辑单例】：P5 批次 2 后仅剩 ∀ ∃ ⊤ ⊥（∧ ∨ → ↔ 已转名字材料，见下条）。 */
    @Test
    fun 逻辑符单token() {
        val ts = toks("∀ ∃! ⊤ ⊥")
        assertEquals(listOf(Kind.FORALL, Kind.EXISTS1, Kind.TOP, Kind.BOT), ts.dropLast(1).map { it.kind })
    }

    /** P5 批次 2（t74）：∧ ∨ → ↔ 退出【逻辑单例】进名字材料——同 ¬ 通道，可粘连成名字。 */
    @Test
    fun 连接词名字材料() {
        val ts = toks("p ∧ q")
        assertEquals(listOf(Kind.IDENT, Kind.IDENT, Kind.IDENT), ts.dropLast(1).map { it.kind })
        assertEquals("p", ts[0].text); assertEquals("∧", ts[1].text); assertEquals("q", ts[2].text)
        val ts2 = toks("p∧q")          // 无空格 → 单个标识符（R1：粘连即整体）
        assertEquals("p∧q", ts2[0].text)
    }

    /** P4（批次 3，用户裁决 N1）：¬ 是名字材料——退出【逻辑单例】，进标识符通道。 */
    @Test
    fun not名字材料() {
        val ts = toks("¬p")
        assertEquals(listOf(Kind.IDENT), ts.dropLast(1).map { it.kind })
        assertEquals("¬p", ts[0].text)
        // 双否定也是单个标识符
        val ts2 = toks("¬¬p")
        assertEquals("¬¬p", ts2[0].text)
        // 裸 ¬ 单独成标识符（¬<p> 命题应用通道的首 token）
        val ts3 = toks("¬ <")
        assertEquals(listOf(Kind.IDENT, Kind.LT), ts3.dropLast(1).map { it.kind })
        assertEquals("¬", ts3[0].text)
        // ¬ 作普通名字的内部字符（名字材料，非符号通道）
        val ts4 = toks("a¬b")
        assertEquals(listOf(Kind.IDENT), ts4.dropLast(1).map { it.kind })
        assertEquals("a¬b", ts4[0].text)
    }

    @Test
    fun 字符串转义() {
        val ts = toks("\"a\\nb\"")
        assertEquals(Kind.STR, ts[0].kind)
        assertEquals("a\nb", ts[0].text)
    }

    @Test
    fun 注释后行号() {
        val ts = toks("// x\n1")
        assertEquals(2, ts[0].line)
    }

    @Test
    fun 非法字符带行列的诊断() {
        // 旧实现抛 LexFailure；新契约 S-1 §1「非法字符不停机」——错误改为诊断输出，
        // 回归保留行列断言。Unicode 标识符特性后 \u0003 仍为非法字符。
        val l = lex("\u0003")
        val ts = l.tokenize()
        assertEquals(1, l.errors[0].line)
        assertEquals(1, l.errors[0].col)
        assertEquals(listOf(Kind.EOF), ts.map { it.kind })
    }

    @Test
    fun u系算符退役_u箭头不再黏连为单一token() {
        // 修-1：删除 uOps 后 `u->` 不再是 u-系算符，而是 `u` 标识符 + `->` 箭头两个 token。
        val ts = toks("p u-> q")
        assertEquals(Kind.IDENT, ts[0].kind)   // p
        assertEquals("u", ts[1].text)          // u 标识符（不黏连）
        assertEquals(Kind.IDENT, ts[1].kind)
        assertEquals(Kind.ARROW, ts[2].kind)   // -> 箭头（非 IMPLIES，uOps 不存在）
    }

    // ===== 新契约：S-1 §1 与《形式化文法》§1（L-1~L-12）=====

    @Test
    fun Kind全表恰71项() {
        // §1.7：Token 全表共 71 项（含休眠 IN）
        assertEquals(71, Kind.entries.size)
    }

    @Test
    fun EOF恒带空格标记且文本固定() {
        val ts = toks("a")
        val eof = ts.last()
        assertEquals(Kind.EOF, eof.kind)
        assertEquals("<eof>", eof.text)
        assertTrue(eof.precededBySpace)
        assertEquals(listOf(Kind.EOF), toks("").map { it.kind })
    }

    @Test
    fun trivia记入后继token的precededBySpace() {
        // L-1/L-2/L-4/L-7：a 无前导 trivia；b 前有换行+制表+空格；d 前有一整行注释
        val ts = toks("a\n\t b // c\n d")
        assertFalse(ts[0].precededBySpace)
        assertEquals("b", ts[1].text)
        assertTrue(ts[1].precededBySpace)
        assertEquals("d", ts[2].text)
        assertTrue(ts[2].precededBySpace)
    }

    @Test
    fun BOM属trivia() {
        // L-3：BOM 不产生 token，其存在记入后继 token 的 precededBySpace
        val ts = toks("\uFEFF1")
        assertEquals(Kind.INT, ts[0].kind)
        assertTrue(ts[0].precededBySpace)
    }

    @Test
    fun 行注释吞到行尾() {
        // L-4：只有 // 行注释；注释内的引号与 ∀ 都不产生 token
        val l = lex("// 注释里可以有 \" 和 ∀\n42")
        val ts = l.tokenize()
        assertEquals(listOf(Kind.INT, Kind.EOF), ts.map { it.kind })
        assertEquals(2, ts[0].line)
        assertTrue(l.errors.isEmpty())
    }

    @Test
    fun 块注释不受支持_按除法与星号lex() {
        // L-4 备注：不存在 /* ... */；/ 按除法算符 lex
        val ts = toks("a /* b")
        assertEquals(listOf(Kind.IDENT, Kind.SLASH, Kind.STAR, Kind.IDENT, Kind.EOF), ts.map { it.kind })
    }

    @Test
    fun Unicode标识符() {
        // L-5：英文/中文/希腊字母均可；下划线允许为首字符
        val ts = toks("中文_α _tmp a1")
        assertEquals(listOf("中文_α", "_tmp", "a1"), ts.dropLast(1).map { it.text })
        assertTrue(ts.take(3).all { it.kind == Kind.IDENT })
    }

    @Test
    fun 标识符内部dollar段粘连() {
        // L-5：合成名 recomposion$Box$1 是单 token——$ 不在开头且后随字母/下划线时续读
        val ts = toks("recomposion\$Box\$1")
        assertEquals(listOf(Kind.IDENT, Kind.EOF), ts.map { it.kind })
        assertEquals("recomposion\$Box\$1", ts[0].text)
    }

    @Test
    fun 标识符内dollar后随数字不粘连() {
        // L-5/L-11 边界：a$1 不粘连——a 是 IDENT，$1 走参数引用通道
        val ts = toks("a\$1")
        assertEquals(listOf(Kind.IDENT, Kind.DOLLAR, Kind.EOF), ts.map { it.kind })
        assertEquals("a", ts[0].text)
        assertEquals("\$1", ts[1].text)
    }

    @Test
    fun 反引号名不查关键字表() {
        // L-6：内容可为任意字符（含空格与运算符），一律 IDENT
        val ts = toks("`when` `a b` `+`")
        assertEquals(listOf(Kind.IDENT, Kind.IDENT, Kind.IDENT), ts.dropLast(1).map { it.kind })
        assertEquals("when", ts[0].text)
        assertEquals("a b", ts[1].text)
        assertEquals("+", ts[2].text)
    }

    @Test
    fun 反引号可跨行单token() {
        // t68（ADR-45,B1,规格变更）：反引号内裸换行合法——单 token、名字含 \n、
        // token 记起始行、无词法错误。旧用例「跨行是词法错误（不停机两段恢复）」按本规格改造：
        // 该面已并入 t68 验收 #5(BacktickIdentTest.t68_5) 与下方未闭合用例。
        val l = lex("`a\nb`")
        val ts = l.tokenize()
        assertEquals(0, l.errors.size, "B1 裸换行不再报错")
        assertEquals(listOf(Kind.IDENT, Kind.EOF), ts.map { it.kind })
        assertEquals("a\nb", ts[0].text)
        assertEquals(1, ts[0].line)
    }

    @Test
    fun 反引号跨行未闭合仍记诊断() {
        // 规格变更后的错误恢复面：跨了行仍缺闭反引号 → 未闭合诊断（已读内容含 \n 作 IDENT 恢复）
        val l = lex("`a\nb")
        val ts = l.tokenize()
        assertEquals("反引号标识符未闭合", l.errors[0].message)
        assertEquals(Kind.IDENT, ts[0].kind)
        assertEquals("a\nb", ts[0].text)
    }

    @Test
    fun 反引号未闭合记诊断() {
        val l = lex("`abc")
        val ts = l.tokenize()
        assertEquals("反引号标识符未闭合", l.errors[0].message)
        assertEquals(Kind.IDENT, ts[0].kind)
        assertEquals("abc", ts[0].text)
    }

    @Test
    fun 符号名连续符号最长匹配() {
        // L-8：非 ASCII 数学符号的最大匹配串；≥/≤/≠ 不在【逻辑单例】，落入 SYMBOL 通道
        val ts = toks("⊗∈ ≥")
        assertEquals(Kind.SYMBOL, ts[0].kind)
        assertEquals("⊗∈", ts[0].text)
        assertEquals(Kind.SYMBOL, ts[1].kind)
        assertEquals("≥", ts[1].text)
    }

    @Test
    fun 逻辑单例不并入符号名() {
        // L-8【逻辑单例】：∀ 是固定单 token，终止符号名的最长匹配
        val ts = toks("○∀")
        assertEquals(listOf(Kind.SYMBOL, Kind.FORALL, Kind.EOF), ts.map { it.kind })
    }

    @Test
    fun 十五个硬关键字折叠() {
        // L-9：标识符读出后查表折算，不可再作普通标识符
        // Q5（t28，卷 §8）：for 释放为普通标识符——唯一用处是 impl，impl 已废除（E-IMPL-REMOVED）
        val src = "fun trait struct enum var impl if else unchecked when is by type return def"
        val expected = listOf(Kind.FUN, Kind.TRAIT, Kind.STRUCT, Kind.ENUM, Kind.VAR, Kind.IMPL,
            Kind.IF, Kind.ELSE, Kind.UNCHECKED, Kind.WHEN, Kind.IS, Kind.BY,
            Kind.TYPE, Kind.RET, Kind.DEF)
        assertEquals(expected, toks(src).dropLast(1).map { it.kind })
    }

    @Test
    fun for去关键字化_t28() {
        // Q5（t28）：for 作普通标识解析（函数名/参数名/变量名通道）
        val ts = toks("fun for(for: Nat): Nat = for")
        assertEquals(listOf(Kind.FUN, Kind.IDENT, Kind.LPAREN, Kind.IDENT, Kind.COLON,
            Kind.IDENT, Kind.RPAREN, Kind.COLON, Kind.IDENT, Kind.ASSIGN, Kind.IDENT, Kind.EOF),
            ts.map { it.kind })
    }

    @Test
    fun in去关键字化_lexer从不产出IN() {
        // L-9 备注（P51）：in 是普通 IDENT——可作函数名/参数名；枚举有 IN 但 lexer 从不产出
        val ts = toks("in")
        assertEquals(Kind.IDENT, ts[0].kind)
    }

    @Test
    fun axiom等语义位置词是普通标识() {
        // L-9 备注：forall/exists/exists1、as、not、then/else、axiom、Task、Flow 均为普通标识
        val ts = toks("axiom forall exists exists1 then as not Task Flow")
        assertTrue(ts.dropLast(1).all { it.kind == Kind.IDENT })
    }

    @Test
    fun 整数与浮点判定() {
        // L-10：1.0 是 FLOAT；点号后必须紧跟数字才粘连——1. 与 1.x 都是 INT 紧跟 DOT
        val ts = toks("42 1.0 1. 1.x")
        assertEquals(
            listOf(Kind.INT, Kind.FLOAT, Kind.INT, Kind.DOT, Kind.INT, Kind.DOT, Kind.IDENT),
            ts.dropLast(1).map { it.kind },
        )
        assertEquals("1.0", ts[1].text)
    }

    @Test
    fun 字符串可跨行() {
        // L-10：字符串可跨行（词法不挡），位置记 token 首字符
        val ts = toks("\"a\nb\"")
        assertEquals(Kind.STR, ts[0].kind)
        assertEquals("a\nb", ts[0].text)
        assertEquals(1, ts[0].line)
    }

    @Test
    fun 字符串未知转义原样保留() {
        // L-10：反斜杠后非 n/t/r/引号/反斜杠时，原样保留该字符（转义符本身被消化）
        val ts = toks("\"\\q\"")
        assertEquals("q", ts[0].text)
    }

    @Test
    fun 字符串未闭合记诊断并继续() {
        // L-10 + S-1 §1：未闭合是词法错误但不停机——已读内容作 STR 恢复 token
        val l = lex("1 \"abc")
        val ts = l.tokenize()
        assertEquals("字符串未闭合", l.errors[0].message)
        assertEquals(1, l.errors[0].line)
        assertEquals(3, l.errors[0].col)
        assertEquals(listOf(Kind.INT, Kind.STR, Kind.EOF), ts.map { it.kind })
        assertEquals("abc", ts[1].text)
    }

    @Test
    fun L12多字符算符按固定顺序匹配() {
        // L-12：固定匹配顺序不可调换（∃! 须先于单 ∃、.. 须先于单 . 等）
        val src = "<-> => == != <= >= -> ∃! +∵ .. :: && ||"
        val expected = listOf(Kind.IFF, Kind.DARROW, Kind.EQ, Kind.NEQ, Kind.LE, Kind.GE,
            Kind.ARROW, Kind.EXISTS1, Kind.PLUS_BECAUSE, Kind.RANGE, Kind.COLON2, Kind.LAND, Kind.LOR)
        assertEquals(expected, toks(src).dropLast(1).map { it.kind })
    }

    @Test
    fun amp与pipe文本归一为Unicode() {
        // t83（ADR-51/Q21 强制改向）：BEFORE(t78) &&/|| 词法层归一 ∧/∨（文本归一通道）；
        // AFTER(t83) 停止归一——token 文本保留用户写法 `&&`/`||`,Kind.LAND/LOR 仅作拒绝锚点
        // （Parser 中缀循环顶落 E-OP-RETIRED;L-12 固定顺序匹配不变，Kind 仍是 LAND/LOR)。
        val ts = toks("&& ||")
        assertEquals("&&", ts[0].text)
        assertEquals("||", ts[1].text)
        assertEquals(Kind.LAND, ts[0].kind)
        assertEquals(Kind.LOR, ts[1].kind)
    }

    @Test
    fun 最长匹配_双字符优先于单字符() {
        val ts = toks("a..b ::c")
        assertEquals(listOf(Kind.IDENT, Kind.RANGE, Kind.IDENT, Kind.COLON2, Kind.IDENT, Kind.EOF), ts.map { it.kind })
    }

    @Test
    fun dollar_n参数引用粘连() {
        // L-11：$ 紧跟数字即粘连为单 DOLLAR token（文本含 $）；单独的 $ 是返回值符号
        val ts = toks("$0 $12 $")
        assertEquals(listOf(Kind.DOLLAR, Kind.DOLLAR, Kind.DOLLAR), ts.dropLast(1).map { it.kind })
        assertEquals("$0", ts[0].text)
        assertEquals("$12", ts[1].text)
        assertEquals("$", ts[2].text)
    }

    @Test
    fun 全角标点诊断附半角提示() {
        // §5.4：全角标点一律词法错误并提示改半角（先于符号名通道拦截，见 ADR-2）
        val l = lex("（")
        val ts = l.tokenize()
        assertEquals(1, l.errors.size)
        assertTrue(l.errors[0].message.contains("全角"))
        assertEquals(listOf(Kind.EOF), ts.map { it.kind })
    }

    @Test
    fun 非法字符后继续lex不停机() {
        // S-1 §1：错误恢复从第一层开始——非法字符跳过后，后续 token 照常产出
        val l = lex("1 ? 2")
        val ts = l.tokenize()
        assertEquals(listOf(Kind.INT, Kind.INT, Kind.EOF), ts.map { it.kind })
        assertEquals(1, l.errors.size)
    }

    @Test
    fun 行列位置随换行推进() {
        val ts = toks("a\n  b")
        assertEquals(1, ts[0].line)
        assertEquals(1, ts[0].col)
        assertEquals(2, ts[1].line)
        assertEquals(3, ts[1].col)
    }

    @Test
    fun tokens流式序列以EOF结尾() {
        // S-1 §1 流式：tokens() 是惰性序列，可逐个消费
        val seq = Lexer("1 2 3", "t.subl").tokens()
        assertEquals(listOf(Kind.INT, Kind.INT, Kind.INT, Kind.EOF), seq.map { it.kind }.toList())
    }

    @Test
    fun tokens流式可提前终止_不强制消费全文件() {
        // S-1 §1 流式：生产与消费解耦——take(1) 只产出首 token
        val seq = Lexer("1 2 3", "t.subl").tokens()
        assertEquals(listOf(Kind.INT), seq.take(1).map { it.kind }.toList())
    }
}
