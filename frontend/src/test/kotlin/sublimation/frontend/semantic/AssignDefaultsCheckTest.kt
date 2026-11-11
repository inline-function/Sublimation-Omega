package sublimation.frontend.semantic

import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirFile
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * t60 健全性漏洞补齐：赋值/字段赋值零类型核对 + 形参（字段）默认值零核对（t59 登记的 G1/G2）。
 * 三处均走与形参/返回值/var 标注**同一条** adaptOk 判据（R-38 落点闸同住）：
 * - 名字赋值：右值 <: 目标，不符落 E-TYPE-MISMATCH（赋值右值 X 与目标 Y 不符）。
 * - 字段赋值：①可变性（字段须 @mut 且接收者可写 → 否则 E-IMMUT-ASSIGN）②类型（t60-1b：@mut
 *   接收者按字段声明型核）。两闸独立、缺一不可。
 * - 默认值：声明位核 typeof(default) <: typeof(param)，不符落 E-DEFAULT-TYPE（t60 新码，
 *   E-DEFAULT-ORDER 同族）。
 *
 * 每条负例配对照例（仅类型不同、其余相同）；覆盖 Nat/Str、Nat/Bool、结构体互赋、联合→成员
 * （须 is 收窄）、Nothing→任意、任意→Any、数值塔升降级。
 */
class AssignDefaultsCheckTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src.trimIndent(), "t60.subl")
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

    private fun errs(bag: DiagBag): List<Diag> = bag.diags.filter { it.severity == Severity.ERROR }

    private fun mismatch(bag: DiagBag) = errs(bag).any { it.code == "E-TYPE-MISMATCH" }

// ---------- 负例（须报错） / 对照例（须通过） ----------

    /**
     * Str 赋给 @mut Nat 变量须落 E-TYPE-MISMATCH。
     */
    @Test
    fun a1_assignStrToNatFires() {
        val e = script("""
            var x: @mut Nat = 1
            x = "abc"
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "Str 赋给 @mut Nat 变量须落 E-TYPE-MISMATCH")
    }

    /**
     * Bool 赋给 @mut Nat 变量须落 E-TYPE-MISMATCH。
     */
    @Test
    fun a2_assignBoolToNatFires() {
        val e = script("""
            var x: @mut Nat = 1
            x = True
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "Bool 赋给 @mut Nat 变量须落 E-TYPE-MISMATCH")
    }

    /**
     * 结构体 B 赋给 @mut A 变量须落 E-TYPE-MISMATCH。
     */
    @Test
    fun a3_assignStructToStructFires() {
        val e = script("""
            struct A(a: Nat)
            struct B(b: Nat)
            var p: @mut A = A(1)
            p = B(1)
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "结构体 B 赋给 @mut A 变量须落 E-TYPE-MISMATCH")
    }

    /**
     * 联合 Optional[Nat] 直赋成员型 Nat 须落 E-TYPE-MISMATCH（无 is 收窄）。
     */
    @Test
    fun a4_assignUnionToMemberWithoutNarrowFires() {
        val e = script("""
            var u: Optional[Nat] = Some(1)
            var m: @mut Nat = 0
            m = u
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "联合 Optional[Nat] 直赋成员型 Nat 须落 E-TYPE-MISMATCH（无 is 收窄）")
    }

    /**
     * Nat 赋给 @mut Nothing 变量须落 E-TYPE-MISMATCH。
     */
    @Test
    fun a5_assignNatToNothingFires() {
        val e = script("""
            fun boom(): Nothing = panic()
            var z: @mut Nothing = boom()
            z = 1
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "Nat 赋给 @mut Nothing 变量须落 E-TYPE-MISMATCH")
    }

    /**
     * Rat 降级赋给 Int 变量须落 E-TYPE-MISMATCH（数值塔单向，同 varAnnotated）。
     */
    @Test
    fun a6_towerDemoteOnAssignFires() {
        val e = script("""
            @mut var x: Int = 1
            x = 3.5
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "Rat 降级赋给 Int 变量须落 E-TYPE-MISMATCH（数值塔单向，同 varAnnotated）")
    }

    /**
     * 同型赋值（含 x + 0 自更新）不得报错。
     */
    @Test
    fun c1_assignNatToNatOk() {
        val e = script("""
            @mut var x = 1
            x = 5
            x = x + 0
        """).second
        assertTrue(errs(e).isEmpty(), "同型赋值（含 x + 0 自更新）不得报错")
    }

    /**
     * 同结构体互赋不得报错。
     */
    @Test
    fun c2_assignSameStructOk() {
        val e = script("""
            struct A(a: Nat)
            var p: @mut A = A(1)
            p = A(2)
        """).second
        assertTrue(errs(e).isEmpty(), "同结构体互赋不得报错")
    }

    /**
     * Nat 升级赋给 Int 变量须照常通过（数值塔单向升兼容）。
     */
    @Test
    fun c3_towerUpgradeOnAssignOk() {
        val e = script("""
            @mut var x: Int = 1
            x = 0
        """).second
        assertTrue(errs(e).isEmpty(), "Nat 升级赋给 Int 变量须照常通过（数值塔单向升兼容）")
    }

    /**
     * Nothing 赋给任意型须通过（R-35 单向百搭）。
     */
    @Test
    fun c4_nothingToAnythingOk() {
        val e = script("""
            fun boom(): Nothing = panic()
            var x: @mut Nat = 0
            x = boom()
        """).second
        assertTrue(errs(e).isEmpty(), "Nothing 赋给任意型须通过（R-35 单向百搭）")
    }

    /**
     * 任意型赋给 Any 目标须通过（Any 通配）。
     */
    @Test
    fun c5_assignToAnyTargetOk() {
        val e = script("""
            @mut var y: Any = 5
            y = "abc"
            y = 1
        """).second
        assertTrue(errs(e).isEmpty(), "任意型赋给 Any 目标须通过（Any 通配）")
    }

    /**
     * is 收窄后的成员赋给成员型须通过。
     */
    @Test
    fun c6_unionNarrowedAssignOk() {
        val e = script("""
            var u: Optional[Nat] = Some(1)
            var m: @mut Nat = 0
            when (u) { is Some(n) -> m = n else -> {} }
        """).second
        assertTrue(errs(e).isEmpty(), "is 收窄后的成员赋给成员型须通过")
    }

    /**
     * 字段写右值 Str 须落 E-TYPE-MISMATCH（@mut 接收者按字段声明型核）。
     */
    @Test
    fun a7_fieldWriteTypeMismatchFires() {
        val e = script("""
            @mut struct Box(@mut n: Nat)
            var b: @mut Box = Box(1)
            b.n = "s"
        """).second
        assertTrue(errs(e).any { it.code == "E-TYPE-MISMATCH" }, "字段写右值 Str 须落 E-TYPE-MISMATCH（@mut 接收者按字段声明型核）")
    }

    /**
     * 写不可变字段须落 E-IMMUT-ASSIGN。
     */
    @Test
    fun a8_fieldWriteImmutableFieldFires() {
        val e = script("""
            struct P(x: Nat)
            var p: @mut P = P(1)
            p.x = 5
        """).second
        assertTrue(errs(e).any { it.code == "E-IMMUT-ASSIGN" }, "写不可变字段须落 E-IMMUT-ASSIGN")
    }

    /**
     * 通过不可变接收者写字段须落 E-IMMUT-ASSIGN。
     */
    @Test
    fun a9_fieldWriteImmutableBindingFires() {
        val e = script("""
            @mut struct Box(@mut n: Nat)
            var b: Box = Box(1)
            b.n = 5
        """).second
        assertTrue(errs(e).any { it.code == "E-IMMUT-ASSIGN" }, "通过不可变接收者写字段须落 E-IMMUT-ASSIGN")
    }

    /**
     * 合法字段写（@mut var 绑定 / @mut 类型位 / @mut 形参）不得报错。
     */
    @Test
    fun c7_fieldWriteAllLegalFormsOk() {
        val e = script("""
            @mut struct Box(@mut n: Nat)
            @mut var b: Box = Box(1)
            b.n = 2
            var c: @mut Box = Box(3)
            c.n = 4
            @mut struct Counter(@mut v: Nat)
            @impure fun bump(k: @mut Counter): Null {
                k.v = k.v + 1
            }
        """).second
        assertTrue(errs(e).isEmpty(), "合法字段写（@mut var 绑定 / @mut 类型位 / @mut 形参）不得报错")
    }

    /**
     * 默认值 Str 配 Nat 形参须落 E-DEFAULT-TYPE（声明位）。
     */
    @Test
    fun a10_paramDefaultTypeMismatchFires() {
        val e = script("""
            fun f(a: Nat = "abc"): Nat = a
            var r = f()
        """).second
        assertTrue(errs(e).any { it.code == "E-DEFAULT-TYPE" }, "默认值 Str 配 Nat 形参须落 E-DEFAULT-TYPE（声明位）")
    }

    /**
     * 字段默认值 Str 配 Nat 字段须落 E-DEFAULT-TYPE。
     */
    @Test
    fun a11_fieldDefaultTypeMismatchFires() {
        val e = script("""
            @mut struct Box(@mut n: Nat = "s")
            var b: @mut Box = Box()
        """).second
        assertTrue(errs(e).any { it.code == "E-DEFAULT-TYPE" }, "字段默认值 Str 配 Nat 字段须落 E-DEFAULT-TYPE")
    }

    /**
     * 合法默认值（0 配 Nat）不得报错。
     */
    @Test
    fun c8_paramDefaultLegalOk() {
        val e = script("""
            fun f(a: Nat = 0): Nat = a
            var r = f()
        """).second
        assertTrue(errs(e).isEmpty(), "合法默认值（0 配 Nat）不得报错")
    }

    /**
     * 合法字段默认值（5 配 Nat）不得报错。
     */
    @Test
    fun c9_fieldDefaultLegalOk() {
        val e = script("""
            @mut struct Box(@mut n: Nat = 5)
            var b: @mut Box = Box()
        """).second
        assertTrue(errs(e).isEmpty(), "合法字段默认值（5 配 Nat）不得报错")
    }

}