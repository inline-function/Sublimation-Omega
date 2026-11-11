package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t97（N1 停机闸 · 阶段 A 报告态，R2 §一「停机闸」/ wiki 04「纯函数与停机」）：
 * 纯函数的**直接自递归**须有实参在「变小」:
 * ① 子结构缩小——实参为形参的 when 解构槽位 / 字段链（递归到空结构停）;
 * ② 数值靠拢——实参为 `n - k` 形。
 * @impure 逃逸（不要求停机证明）;unchecked 放行（豁免检查不豁免效应存在，ADR-55 §unchecked）。
 * 码 E-NON-STRUCTURAL-REC（**不复用** E-FUEL-EXHAUSTED 编译器自查燃料码与机制）;阶段 A warn 已收档，阶段 B error 上线（队长 2026-11-10 批）。
 *
 * 已登记局限:间接/互递归（`a→b→a`）静态不计——旧工程同口径。
 */
class T97TerminationGateTest {

    private val prelude = "struct Nil\n" +
        "struct Cons[T](head: T, tail: List[T])\n" +
        "type List[T] = Nil | Cons[T]\n\n"

    private fun fires(src: String, file: String = "t97.subl"): Int {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.count { it.code == "E-NON-STRUCTURAL-REC" } +
            diag.diags.count { it.code == "E-NON-STRUCTURAL-REC" }
    }

    /** 验收 #1:子结构缩小的自递归正例——通过。 */
    @Test fun 解构槽位缩小() {
        assertEquals(0, fires(prelude +
            "fun size[T](xs: List[T]): Nat = when(xs) {\n" +
            "    Nil -> 0\n" +
            "    is Cons(_, t) -> size(t) + 1\n" +
            "}"))
    }

    /** 验收 #1:方法糖形态——接收者入首实参位。 */
    @Test fun 方法糖接收者缩小() {
        assertEquals(0, fires(prelude +
            "fun List[a].count2[a](): Nat = when(self) {\n" +
            "    Nil -> 0\n" +
            "    is Cons(h, t) -> t.count2() + 1\n" +
            "}"))
    }

    /** 验收 #1:数值靠拢形态（n - k）。 */
    @Test fun 数值靠拢缩小() {
        assertEquals(0, fires("fun down(n: Nat): Nat {\n" +
            "    if (n > 0) { return down(n - 1) }\n    return 0\n}"))
    }

    /** 验收 #1:字段链形态。 */
    @Test fun 字段链缩小() {
        assertEquals(0, fires("fun f[T](xs: List[T]): Nat = f(xs.tail)"))
    }

    /** 嵌套 when 的来源链传递性（t2 ← t ← xs）。 */
    @Test fun 嵌套when传递() {
        assertEquals(0, fires(prelude +
            "fun f(xs: List[Nat]): Nat = when(xs) {\n" +
            "    Nil -> 0\n" +
            "    is Cons(h, t) -> when(t) {\n" +
            "        Nil -> 0\n" +
            "        is Cons(h2, t2) -> f(t2) + 1\n" +
            "    }\n" +
            "}"))
    }

    /** 验收 #2:无停机保证的自递归——落诊断（阶段 A = warn）。 */
    @Test fun 参数直传落闸() {
        val src = "fun f(n: Nat): Nat = f(n)"
        assertEquals(1, fires(src))
    }

    @Test fun 常量实参落闸() { assertEquals(1, fires("fun f(n: Nat): Nat = f(0)")) }

    /** 方法糖接收者不变（self 递归）⇒ 落闸。 */
    @Test fun 方法糖接收者不变落闸() {
        assertEquals(1, fires(prelude +
            "fun List[a].loop[a](): Nat = when(self) {\n" +
            "    Nil -> 0\n" +
            "    is Cons(h, t) -> self.loop()\n" +
            "}"))
    }

    /** 局部 fun 的自递归按内层函数计（遮蔽同名外层不误判）。 */
    @Test fun 局部fun自递归() {
        val src = "fun outer(n: Nat): Nat {\n" +
            "    fun inner(m: Nat): Nat = inner(m)\n" +
            "    return inner(n)\n" +
            "}"
        assertEquals(1, fires(src))
    }

    /** 阶段 A:warn 级（阶段 B 翻 error 同 t96 节奏）。 */
    @Test fun 阶段B为error级() {
        val pr = parseScriptSourceWithNotes("fun f(n: Nat): Nat = f(n)", "t97.subl")
        val (_, diag) = compile(pr.file, pr.notes)
        val d = diag.diags.firstOrNull { it.code == "E-NON-STRUCTURAL-REC" }
        assertEquals(Severity.ERROR, d?.severity, "阶段 B:error 硬拒")
    }

    /** 验收 #3:@impure 函数逃逸——不要求停机证明（旧工程口径）。 */
    @Test fun impure逃逸() {
        assertEquals(0, fires("@impure fun f(n: Nat): Nat = f(n)"))
    }

    /** unchecked 放行（豁免检查不豁免效应存在——ADR-55 §unchecked 不对称口径锁定测试）。 */
    @Test fun unchecked放行() {
        val src = "fun f(n: Nat): Nat {\n    unchecked return f(n)\n}"
        assertEquals(0, fires(src))
    }

    /** 已登记局限:互递归静态不计（直接自递归之外的环路不在本闸范围）。 */
    @Test fun 互递归不在范围() {
        assertEquals(0, fires("fun a(n: Nat): Nat = b(n)\nfun b(n: Nat): Nat = a(n)"))
    }
}
