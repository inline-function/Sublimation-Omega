package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * t52（ADR-34）：P-9 收尾修复的裁决对账用例——
 * 局部函数调用点实参检查（R-32）、裸自引用联合误报（R-33）、以及 t51 验收补充的
 * 燃料耗尽级联 E-TYPE-MISMATCH 抑制（同一请求子树内，独立请求不受影响）。
 */
class LocalFunUnionFixTest {
    private fun script(src: String): DiagBag {
        val pr = parseScriptSourceWithNotes(src, "t52.subl")
        val (hir, bag) = compile(pr.file, pr.notes)
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
    private fun errs(bag: DiagBag) = bag.diags.filter { it.severity == Severity.ERROR }
    private fun codes(bag: DiagBag) = errs(bag).map { it.code }

    // ===== 缺陷 1：局部函数调用点实参检查（R-32）====

    @Test fun localFunArgsChecked() {
        val src = "fun g(): Str {\n    fun f(a: Str): Str = a\n    return f(0)\n}"
        val e = errs(script(src))
        assertTrue(e.any { it.code == "E-TYPE-MISMATCH" && it.message.contains("实参 1 Nat 与形参 Str 不符") },
            "局部函数错参须落实参不匹配：" + e)
    }

    @Test fun localFunCorrectArgsOk() {
        val src = "fun g(): Nat {\n    fun f(a: Nat): Nat = a\n    return f(0)\n}"
        assertEquals(emptyList(), codes(script(src)), "局部函数正确实参不应报错")
    }

    @Test fun shadowedLocalFunArgsChecked() {
        val src = "fun f(a: Nat): Nat = a\nfun g(): Nat {\n    fun f(a: Str): Str = a\n    return f(0)\n}"
        val e = errs(script(src))
        // 裁决 8 末条：内层遮蔽 + f(0) 应非法（局部 f 形参 Str）
        assertTrue(e.any { it.code == "E-TYPE-MISMATCH" && it.message.contains("实参 1 Nat 与形参 Str 不符") },
            "遮蔽场景下局部函数错参须按局部签名核对：" + e)
    }

    @Test fun globalFunControlStillChecked() {
        val src = "fun f(a: Str): Str = a\nvar r = f(0)"
        val e = errs(script(src))
        assertTrue(e.any { it.code == "E-TYPE-MISMATCH" }, "全局函数对照应仍报错：" + e)
    }

    @Test fun localFunDefaultArgsWork() {
        val src = "fun g(): Nat {\n    fun f(a: Nat, b: Nat = 1): Nat = a + b\n    return f(1)\n}"
        assertEquals(emptyList(), codes(script(src)), "局部函数默认值须生效")
    }

    @Test fun localFunVariadicAndArityChecked() {
        val ok = "fun g(): Nat {\n    fun h(@list xs: Nat): Nat = 0\n    return h(2, 3)\n}"
        assertEquals(emptyList(), codes(script(ok)), "局部函数 @list 变长须生效")
        val missing = "fun g(): Nat {\n    fun f(a: Nat, b: Nat): Nat = a + b\n    return f(1)\n}"
        assertTrue(errs(script(missing)).any { it.code == "E-TYPE-MISMATCH" }, "局部函数缺实参须报错")
    }

    @Test fun localFunNamedArgsWork() {
        val src = "fun g(): Nat {\n    fun f(a: Nat, b: Nat): Nat = a + b\n    return f(b = 1, a = 2)\n}"
        assertEquals(emptyList(), codes(script(src)), "局部函数命名实参重排须生效")
    }

    // ===== 缺陷 2：裸自引用联合判重（R-33）====

    @Test fun selfRefUnionWithBasePasses() {
        val src = "struct B()\ntype A = A | B"
        val bag = script(src)
        assertFalse(errs(bag).any { it.code == "E-DUP-UNION-MEMBER" }, "自引用带基态须通过（P-9 裁决 7）")
    }

    @Test fun selfRefDupReportsOwnName() {
        val src = "struct B()\ntype A = A | A"
        val e = errs(script(src))
        assertTrue(e.any { it.code == "E-DUP-UNION-MEMBER" && it.message.contains("成员 `A` 重复声明") },
            "真重复须按被定义名报：" + e)
    }

    @Test fun selfRefOrderIndifferent() {
        val src = "struct B()\ntype A = B | A"
        val bag = script(src)
        assertFalse(errs(bag).any { it.code == "E-DUP-UNION-MEMBER" }, "顺序无关：自引用带基态须通过")
    }

    @Test fun peanoStylePasses() {
        val src = "struct Zero()\nstruct Succ[T](pred: T)\ntype Peano = Zero | Succ[Peano]"
        val bag = script(src)
        assertFalse(errs(bag).any { it.code == "E-DUP-UNION-MEMBER" }, "Peano 形态不得误报重复成员")
    }

    @Test fun realStructDupStillReports() {
        val src = "struct B()\ntype A = B | B"
        val e = errs(script(src))
        assertTrue(e.any { it.code == "E-DUP-UNION-MEMBER" && it.message.contains("成员 `B` 重复声明") },
            "真重复成员名须报对：" + e)
    }

    // ===== t51 验收补充：燃料耗尽的级联抑制 =====

    @Test fun fuelCascadeMismatchSuppressed() {
        val src = "struct Zero()\nstruct Succ[T](pred: T)\ntype A = Zero | Succ[A]\n" +
            "type B = Zero | Succ[B]\nfun g[T](t: T, u: T): T = t\n" +
            "var a: A = Zero\nvar b: B = Zero\nvar r = g(a, b)"
        val e = errs(script(src))
        assertEquals(1, e.size, "一个根因只报一条：" + e)
        assertEquals("E-FUEL-EXHAUSTED", e.first().code)
        assertEquals("编译器进行检查时无法停机", e.first().message)
    }

    @Test fun independentMismatchStillReportedAfterExhaustion() {
        val src = "struct Zero()\nstruct Succ[T](pred: T)\ntype A = Zero | Succ[A]\n" +
            "type B = Zero | Succ[B]\nfun g[T](t: T, u: T): T = t\n" +
            "var a: A = Zero\nvar b: B = Zero\nvar r = g(a, b)\nvar wrong: Nat = a"
        val e = errs(script(src))
        assertTrue(e.any { it.code == "E-FUEL-EXHAUSTED" }, "耗尽诊断保留")
        assertTrue(e.any { it.code == "E-TYPE-MISMATCH" }, "独立请求的类型错误须正常报（不被全局抑制）")
    }
}