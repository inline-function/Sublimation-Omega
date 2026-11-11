package sublimation.frontend.semantic

import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseSourceWithNotes
import sublimation.frontend.semantic.compileWithRounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** t125 S5a（OQ-3 对称修复）：名字位写闸补读**类型位** @mut。
 *
 * - 工程模式显式 `a: @mut T` 形参今日落 E-IMMUT-ASSIGN（判据不对称——名字位只读 SymbolEntry.mutable，
 *   而字段闸 fieldWriteOk 两口径都读）⇒ R-06「工程侧必须先 @mut」在此之前是死路（标了也不让写）。
 * - 修复：名字位判据补 `isWritableView(symType)`——@mut T / @ref @mut T 可写，与字段位同口径。
 * - 既有面零命中（e2e 68 + out-subs 14 全样本 @mut 类型位 ZERO）⇒ SAME 68/68 机械零变。
 * - R-18 去糖模型（self: @ref @mut T 写穿透）依赖此对称位。 */
class T125WriteGateTest {

    private fun rounds(src: String) =
        parseSourceWithNotes(src, "t125.subl").let { compileWithRounds(it.file, it.notes, scriptMode = false) }

    private fun errCodes(rs: RoundsReport): List<String> = rs.diag.diags.map { it.code }

    @Test fun 形参类型位mut可写() {
        val rs = rounds("@impure fun main(): Null { fun f(a: @mut Nat): Nat { a = a + 1; return a } print(f(1)) }")
        assertEquals(emptyList(), errCodes(rs).filter { it.startsWith("E-") }, "类型位 @mut 形参可写——不再落 E-IMMUT-ASSIGN")
    }

    @Test fun 普通形参写仍拒() {
        val rs = rounds("@impure fun main(): Null { fun f(a: Nat): Nat { a = a + 1; return a } print(f(1)) }")
        assertTrue(errCodes(rs).contains("E-IMMUT-ASSIGN"), "无 @mut 类型位的形参写仍落 E-IMMUT-ASSIGN")
    }

    @Test fun 局部变量类型位mut可写() {
        val rs = rounds("@impure fun main(): Null { var x: @mut Nat = 1; x = x + 1; print(x) }")
        assertEquals(emptyList(), errCodes(rs).filter { it.startsWith("E-") }, "var x: @mut T 类型位可写（既有形态回归保护）")
    }

    @Test fun refMut写穿透视图可写() {
        val rs = rounds("@mut struct Box(@mut n: Nat)\n" +
            "@impure fun main(): Null { fun f(b: @ref @mut Box): Null { b.n = 3 } @mut var x: @mut Box = Box(0); f(x) }")
        assertEquals(emptyList(), errCodes(rs).filter { it.startsWith("E-") }, "@ref @mut 写穿透视图的字段写放行")
    }

    @Test fun 只读引用写仍拒() {
        val rs = rounds("@mut struct Box(@mut n: Nat)\n" +
            "@impure fun main(): Null { fun f(b: @ref Box): Null { b.n = 3 } @mut var x: @mut Box = Box(0); f(x) }")
        assertTrue(errCodes(rs).contains("E-IMMUT-ASSIGN"), "@ref T 只读视图写仍拒（接收者闸既判据）")
    }
}
