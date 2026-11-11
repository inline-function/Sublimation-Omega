package sublimation.frontend.diag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * diag 横切包契约测试（S-2 §3.2 / D-11）：diag 零依赖（只依赖 Kotlin 标准库）——
 * 本测试文件本身即证据：除 diag 自身与 kotlin.test 外无任何前端层 import。
 */
class DiagMetaTest {
    @Test
    fun 位置模型齐备() {
        // SourcePos/SourceRange/NodeId 宿于 diag（ADR-4）
        val pos = SourcePos(3, 7)
        val range = SourceRange(pos, SourcePos(3, 9))
        assertEquals(3, range.start.line)
        assertEquals(9, range.end?.col)
        val endlesss = SourceRange(pos, null)
        assertNull(endlesss.end)
    }

    @Test
    fun NodeId为稳定序号类型() {
        val n: NodeId = 42
        // typealias Int：构造期先序分配（D-11；JSON 产物即数字，ADR-1 §3.7）
        assertEquals(42, n)
    }
}
