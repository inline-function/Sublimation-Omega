package sublimation.frontend.diag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 诊断模型测试（D-10/D2；ADR-1 §3.6；旧 Diag.kt 行为全对齐，P-7）。
 *
 * 覆盖：四族严重级与渲染标签、结构化字段（SourceRange + nid + modulePath）、
 * 诊断袋四族落袋与保序、hasError 阻断判定（N-SEV-BLOCK）、absorb 合并、
 * dedupe 去重、report 默认隐藏补充族与源码箭头渲染。
 */
class DiagTest {
    private fun pos(line: Int, col: Int = 1) = SourceRange(SourcePos(line, col), null)

    @Test
    fun 四族严重级齐备() {
        assertEquals(4, Severity.entries.size)
        // 渲染标签与旧格式对齐（D-10 渲染层口径）
        assertEquals("[错误][1:2] E-X: m", Diag(Severity.ERROR, "E-X", pos(1, 2), "m").render())
        assertEquals("[警告][3:4] W-X: m", Diag(Severity.WARN, "W-X", pos(3, 4), "m").render())
        assertEquals("[提示][5:6] H-X: m", Diag(Severity.HINT, "H-X", pos(5, 6), "m").render())
        assertEquals("[补充][7:8] D-X: m", Diag(Severity.SUPPLEMENT, "D-X", pos(7, 8), "m").render())
    }

    @Test
    fun 诊断结构化字段齐备() {
        val d = Diag(Severity.ERROR, "E-NAME", pos(1, 2), "未解析", "mod.subl", 7)
        assertEquals(Severity.ERROR, d.severity)
        assertEquals("E-NAME", d.code)
        assertEquals(1, d.pos.start.line)
        assertEquals(2, d.pos.start.col)
        assertEquals("mod.subl", d.modulePath)
        assertEquals(7, d.nid)
        // 默认值：无模块关联、无节点反引
        val plain = Diag(Severity.WARN, "W-X", pos(1), "m")
        assertEquals("", plain.modulePath)
        assertNull(plain.nid)
    }

    @Test
    fun 诊断袋四族落袋保序且判阻断() {
        val bag = DiagBag()
        bag.error("E-A", pos(1), "a")
        bag.warn("W-B", pos(2), "b")
        bag.hint("H-C", pos(3), "c")
        bag.supplement("D-D", pos(4), "d")
        assertEquals(4, bag.diags.size)
        // 只有 ERROR 阻断（N-SEV-BLOCK：族字母不判阻断）
        val soft = DiagBag().apply {
            warn("W-A", pos(1), "a")
            hint("H-B", pos(2), "b")
            supplement("D-C", pos(3), "c")
        }
        assertFalse(soft.hasError)
        val hard = DiagBag().apply { error("E-A", pos(1), "a") }
        assertTrue(hard.hasError)
        // nid 反引节点
        val linked = DiagBag().apply { error("E-A", pos(1), "a", 42) }
        assertEquals(42, linked.diags[0].nid)
    }

    @Test
    fun 诊断袋modulePath贯穿落袋() {
        val bag = DiagBag()
        bag.modulePath = "root.subl"
        bag.error("E-A", pos(1), "a")
        bag.supplement("D-B", pos(2), "b")
        assertEquals("root.subl", bag.diags[0].modulePath)
        assertEquals("root.subl", bag.diags[1].modulePath)
    }

    @Test
    fun 诊断袋合并保序() {
        val a = DiagBag().apply { error("E-A", pos(1), "a") }
        val b = DiagBag().apply { warn("W-B", pos(2), "b") }
        a.absorb(b)
        assertEquals(2, a.diags.size)
        assertEquals("E-A", a.diags[0].code)
        assertEquals("W-B", a.diags[1].code)
    }

    @Test
    fun 诊断袋按码位置文本去重保留首次() {
        val bag = DiagBag()
        bag.error("E-DUP", pos(1, 2), "重复")
        bag.error("E-DUP", pos(1, 2), "重复")
        bag.error("E-DUP", pos(3, 4), "不同位置则保留")
        bag.error("E-DUP", pos(1, 2), "不同文本则保留")
        bag.dedupe()
        assertEquals(3, bag.diags.size)
        assertTrue(bag.hasError)
    }

    @Test
    fun report默认隐藏补充族且附源码箭头() {
        val bag = DiagBag()
        bag.modulePath = "t.subl"
        bag.error("E-X", pos(2, 5), "示例")
        bag.supplement("D-Y", pos(1), "回声")
        // 默认隐藏补充族
        val reported = bag.report()
        assertTrue(reported.contains("[错误][2:5] E-X: 示例"))
        assertFalse(reported.contains("D-Y"))
        // -v 打开补充族
        val full = bag.report(includeSupplement = true)
        assertTrue(full.contains("D-Y"))
        // 位置越界者只输出基调行（防御渲染）
        val over = DiagBag().apply { hint("H-X", pos(9, 9), "超界") }
        assertEquals("[提示][9:9] H-X: 超界", over.report(source = "one\ntwo"))
        // 无源码时基调行
        assertEquals("[错误][2:5] E-X: 示例", Diag(Severity.ERROR, "E-X", pos(2, 5), "示例").render())
    }

    @Test
    fun 报告渲染附源码行与箭头() {
        val d = Diag(Severity.ERROR, "E-X", pos(1, 5), "示例")
        val src = "val x = 1\nval y = 2"
        val expected = "[错误][1:5] E-X: 示例\n  1 | val x = 1\n    |     ^"
        assertEquals(expected, d.render(src))
    }

    @Test
    fun 模块外补充族被丢弃() {
        val bag = DiagBag()
        bag.modulePath = "t.subl"
        bag.supplement("D-IN", pos(1), "保留")
        bag.error("E-ROOT", pos(1), "保留")
        val other = DiagBag().apply {
            modulePath = "other.subl"
            supplement("D-OUT", pos(1), "丢弃")
        }
        bag.absorb(other)
        bag.dropSupplementsOutside("t.subl")
        val codes = bag.diags.map { it.code }.toSet()
        assertTrue(codes.contains("D-IN"))
        assertFalse(codes.contains("D-OUT"))
        // 补充族以外不受影响
        assertTrue(codes.contains("E-ROOT"))
    }

    @Test
    fun 源范围为结构化呈现() {
        val range = SourceRange(SourcePos(1, 2), SourcePos(1, 5))
        assertEquals(1, range.start.line)
        assertEquals(5, range.end?.col)
        // 空诊断袋的契约
        val empty = DiagBag()
        assertFalse(empty.hasError)
        assertEquals("", empty.report())
    }
}
