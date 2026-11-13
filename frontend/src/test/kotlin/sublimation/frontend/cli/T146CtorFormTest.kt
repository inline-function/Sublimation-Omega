package sublimation.frontend.cli

import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t146（R-26.5 / B2.1 构造函数形态）：mutableListOf/listOf 构造族落 stdlib 源（@list 变长形参——
 * t48/ADR-32 已存机制：末位吸收 0..n 实参），虚拟ctor retire（数据源替换同 B0/B1/B2 纪律 +
 * RETIRED_FUNS 允许表）。旧状=虚拟 ctor 单参（first: T）签名：零参/多变参形态落 E-ARITY/缺实参;
 * 本件后零参/单参/多变参皆绿（@mut List[T] / List[T]）。
 *
 * 范围内：构造面实测（0/1/n 实参）+ Cons 自拼合法回归保护（R-26.3 不作诊断）+ 元素类型推导
 * 失败精确报（E 级落码：不是 E-ARITY，是实参-元素不符）。范围外：效应分析/命题通道/后端运行时。 */
class T146CtorFormTest {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun scriptStd(src: String, scriptMode: Boolean = false): FrontendOutput {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode, noDegrade = false, noLift = false)
        return compileFrontendImpl(src, "t146.subl", scriptMode, false, false,
            su.associate { it.alias to it.snapshot }, sd)
    }

    // -------------------- 门 1：可变构造面（零参/单参/多变参全绿） --------------------

    /** mutableListOf() 零参——变长位吸收 0 实参（t48 空变长回退 synthetic）。 */
    @Test fun mutableListOf零参绿() {
        val o = scriptStd("""fun main(): Null {
    var xs : @mut List[Nat] = mutableListOf()
    unchecked print(xs)
}""")
        assertEquals(emptyList<String>(), errs(o), "mutableListOf() 零参须绿: " + errs(o))
    }

    /** mutableListOf(1) 单参——@mut List[Nat]（t143 双探针形态承接）。 */
    @Test fun mutableListOf单参绿() {
        val o = scriptStd("""fun main(): Null {
    var xs : @mut List[Nat] = mutableListOf(1)
    unchecked print(xs)
}""")
        assertEquals(emptyList<String>(), errs(o), "mutableListOf(1) 单参须绿: " + errs(o))
    }

    /** mutableListOf(1, 2, 3) 多变参——末位变长位吸收全部实参。 */
    @Test fun mutableListOf多变参绿() {
        val o = scriptStd("""fun main(): Null {
    var xs : @mut List[Nat] = mutableListOf(1, 2, 3)
    unchecked print(xs)
}""")
        assertEquals(emptyList<String>(), errs(o), "mutableListOf(1, 2, 3) 变长吸收须绿: " + errs(o))
    }

    /** listOf() 零参 + (1) + (1, 2)——不可变同族三形态。 */
    @Test fun listOf三形态绿() {
        val o = scriptStd("""fun main(): Null {
    var a : List[Nat] = listOf()
    var b : List[Nat] = listOf(1)
    var c : List[Nat] = listOf(1, 2)
    unchecked print(a)
    unchecked print(b)
    unchecked print(c)
}""")
        assertEquals(emptyList<String>(), errs(o), "listOf 零/单/多变参三形态须绿: " + errs(o))
    }

    /** 元素类型由实参推导——mutableListOf(Cons(1, Nil)) → List[List[Nat]] 高阶形态。 */
    @Test fun 元素类型实参推导绿() {
        val o = scriptStd("""fun main(): Null {
    var xs : List[List[Nat]] = listOf(Cons(1, Nil))
    unchecked print(xs)
}""")
        assertEquals(emptyList<String>(), errs(o), "元素类型由实参推导（高阶 List[List[Nat]]）须绿: " + errs(o))
    }

    // -------------------- 门 2：负例（元素类型推导失败精确报——不是元数错位） --------------------

    /** mutableListOf(True, 1) 混型实参——变长位逐一 unify 不一致落 E（E-TYPE-MISMATCH 面， */
    /** 非 E-ARITY——虚拟 ctor 旧口径把多实参报成元数错，新形态报元素类型不符）。 */
    @Test fun 混型实参精确报() {
        val o = scriptStd("""fun main(): Null {
    var xs : @mut List[Nat] = mutableListOf(True, 1)
    unchecked print(xs)
}""")
        assertFalse(errs(o).isEmpty(), "混型实参须落 E 级（精确报义务）")
        assertFalse(errs(o).contains("E-ARITY"), "混型实参不是元数错位（变长位吸收 0..n）——应报元素类型不符: " + errs(o))
    }

    /** 变长位多实参混型（mutableListOf(1, 2, "x")）——元素一致性报延伸面。 */
    @Test fun 变长多实参混型报() {
        val o = scriptStd("""fun main(): Null {
    var xs : @mut List[Nat] = mutableListOf(1, 2, "x")
    unchecked print(xs)
}""")
        assertFalse(errs(o).isEmpty(), "变长多实参混型须落 E 级: " + errs(o))
        assertFalse(errs(o).contains("E-ARITY"), "多实参不是元数错位（变长位吸收）: " + errs(o))
    }

    // -------------------- 门 3：Cons 自拼合法（R-26.3 回归保护——不作诊断） --------------------

    /** Cons(1, Nil) 显式自拼合法——R-26.3 不把底层自拼判红（构造正道并存）。 */
    @Test fun Cons自拼合法绿() {
        val o = scriptStd("""fun main(): Null {
    var xs : List[Nat] = Cons(1, Nil)
    var ys : List[Nat] = Cons(2, xs)
    unchecked print(ys)
}""")
        assertEquals(emptyList<String>(), errs(o), "Cons 自拼合法（R-26.3 回归保护）须绿: " + errs(o))
    }

    /** 构造族与 Cons 自拼混用（mutableListOf 拼 Cons 尾）——两形态共存零回归。 */
    @Test fun 构造族与Cons混用绿() {
        val o = scriptStd("""fun main(): Null {
    var head : List[Nat] = listOf(1)
    var xs : List[Nat] = Cons(2, head)
    var ys : @mut List[Nat] = mutableListOf(3, 4)
    unchecked print(xs)
    unchecked print(ys)
}""")
        assertEquals(emptyList<String>(), errs(o), "构造族与 Cons 自拼混用须绿: " + errs(o))
    }
}
