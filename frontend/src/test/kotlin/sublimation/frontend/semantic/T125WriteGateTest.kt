package sublimation.frontend.semantic

import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseSourceWithNotes
import sublimation.frontend.semantic.compileWithRounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /** R-28 fallout（队长冲突点 2 待裁）：工程模式 plain → @mut 形参不再隐式深拷放行——
     *  写闸对称性以赋值位错（E-TYPE-MISMATCH）间接证实（旧 E-IMMUT-ASSIGN 已不落）。 */
    @Test fun 形参类型位mut可写() {
        val rs = rounds("@impure fun main(): Null { fun f(a: @mut Nat): Nat { a = a + 1; return a } print(f(1)) }")
        val codes = errCodes(rs).filter { it.startsWith("E-") }
        assertTrue(codes.contains("E-TYPE-MISMATCH"), "R-28: plain 实参 → @mut 形参落 E-TYPE-MISMATCH（旧隐式深拷格废止）: $codes")
        assertFalse(codes.contains("E-IMMUT-ASSIGN"), "写闸对称性：@mut 类型位写不再落 E-IMMUT-ASSIGN（旧错已消）")
    }

    @Test fun 普通形参写仍拒() {
        val rs = rounds("@impure fun main(): Null { fun f(a: Nat): Nat { a = a + 1; return a } print(f(1)) }")
        assertTrue(errCodes(rs).contains("E-IMMUT-ASSIGN"), "无 @mut 类型位的形参写仍落 E-IMMUT-ASSIGN")
    }

    /** R-28 fallout：var x: @mut Nat 的类型位不可由 plain 字面构造——可写形态改 @mut var 绑定。 */
    @Test fun 局部变量类型位mut可写() {
        val rs = rounds("@impure fun main(): Null { @mut var x = 1; x = x + 1; print(x) }")
        assertEquals(emptyList(), errCodes(rs).filter { it.startsWith("E-") }, "@mut var 绑定可写（R-28 后唯一合法可写局部形态）")
    }

    /** R-28 fallout：工程模式 @mut Box 无合法构造（@mut 类型位初始化拒）——
     *  写穿透语义改脚本模式借用形态（& 是脚本专属）。 */
    @Test fun refMut写穿透视图可写() {
        val rs = rounds("@mut struct Box(@mut n: Nat)\n" +
            "@impure fun main(): Null { fun f(b: @ref @mut Box): Null { b.n = 3 } @mut var x = Box(0); f(x) }")
        // R-28：plain 源 → @ref @mut 形参落 E-TYPE-MISMATCH（旧深拷放行格废止）——
        // 字段写闸 b.n = 3 不再落 E-IMMUT-ASSIGN（写穿透代码路径保持放行语义）
        val codes = errCodes(rs).filter { it.startsWith("E-") }
        assertEquals(listOf("E-TYPE-MISMATCH"), codes, "@ref @mut 写穿透：仅 R-28 拒格落点（写闸零 E-IMMUT-ASSIGN）")
    }

    @Test fun 只读引用写仍拒() {
        val rs = rounds("@mut struct Box(@mut n: Nat)\n" +
            "@impure fun main(): Null { fun f(b: @ref Box): Null { b.n = 3 } @mut var x: @mut Box = Box(0); f(x) }")
        assertTrue(errCodes(rs).contains("E-IMMUT-ASSIGN"), "@ref T 只读视图写仍拒（接收者闸既判据）")
    }
}
