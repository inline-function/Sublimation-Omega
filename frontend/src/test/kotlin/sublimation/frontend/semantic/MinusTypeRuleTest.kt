package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * t63（ADR-40，ST-10.1）：数字减法类型规则——减法结果 rank 至少 Int（Nat 无符号，Nat - Nat 可负）。
 * + * % 保持上确界（Nat 闭包）不得误伤；一元取负同族。
 */
class MinusTypeRuleTest {
    private fun script(src: String): DiagBag {
        val pr = parseScriptSourceWithNotes(src, "t63.subl")
        val (_, bag) = compile(pr.file, pr.notes)
        pr.diag.forEach { d -> when (d.severity) {
            Severity.ERROR -> bag.error(d.code, d.pos, d.message)
            Severity.SUPPLEMENT -> bag.supplement(d.code, d.pos, d.message)
            else -> {} } }
        return bag
    }

    private fun codes(src: String) =
        script(src).diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun reject(name: String, src: String, expected: String) {
        val c = codes(src)
        assertTrue(c.contains(expected), "负例「" + name + "」须拒绝（" + expected + "）；actual=" + c)
    }

    private fun pass(name: String, src: String) {
        val e = script(src).diags.filter { it.severity == Severity.ERROR }
        assertTrue(e.isEmpty(), "正例「" + name + "」须无诊断；actual=" + e)
    }

    // -------------------- ST-10.1 二元减法：rank(结果) = max(rank(l), rank(r), 1=Int) --------------------
    @Test
    fun minusBinaryRule() {
        reject("wantNat-arg", """
            fun wantNat(n: Nat): Nat = 0
            fun give(a: Nat, b: Nat): Nat = wantNat(a - b)
        """, "E-TYPE-MISMATCH")
        pass("wantInt-arg", """
            fun wantInt(n: Int): Nat = 0
            fun give(a: Nat, b: Nat): Nat = wantInt(a - b)
        """)
        pass("wantRat-arg", """
            fun wantRat(n: Rat): Nat = 0
            fun give(a: Nat, b: Nat): Nat = wantRat(a - b)
        """)
        reject("var-Nat-decl", """
            fun g(a: Nat, b: Nat): Int { unchecked var x: Nat = a - b; return x }
        """, "E-TYPE-MISMATCH")
        pass("var-Int-decl", """
            fun g(a: Nat, b: Nat): Int { unchecked var x: Int = a - b; return x }
        """)
        pass("int-minus-int", """
            fun f(a: Int, b: Int): Int = a - b
        """)
        pass("rat-minus-rat", """
            fun f(a: Rat, b: Rat): Rat = a - b
        """)
        pass("rat-minus-nat", """
            fun f(a: Rat, b: Nat): Rat = a - b
        """)
        reject("funret-Nat", """
            fun f(a: Nat, b: Nat): Nat = a - b
        """, "E-TYPE-MISMATCH")
        pass("funret-Int", """
            fun f(a: Nat, b: Nat): Int = a - b
        """)
    }

    // -------------------- ST-10.1 对照：+ * % 保持上确界（Nat 闭包）——不得误伤 --------------------
    @Test
    fun plusFamilyClosure() {
        pass("wantNat-plus", """
            fun wantNat(n: Nat): Nat = 0
            fun give(a: Nat, b: Nat): Nat = wantNat(a + b)
        """)
        pass("wantNat-mul", """
            fun wantNat(n: Nat): Nat = 0
            fun give(a: Nat, b: Nat): Nat = wantNat(a * b)
        """)
        pass("wantNat-mod", """
            fun wantNat(n: Nat): Nat = 0
            fun give(a: Nat, b: Nat): Nat = wantNat(a % b)
        """)
        pass("funret-plus", """
            fun f(a: Nat, b: Nat): Nat = a + b
        """)
    }

    // -------------------- 一元取负同族：rank 至少 Int（Nat 取负可负） --------------------
    @Test
    fun unaryMinusRule() {
        reject("unary-funret-Nat", """
            fun h(a: Nat): Nat = -a
        """, "E-TYPE-MISMATCH")
        pass("unary-funret-Int", """
            fun h(a: Nat): Int = -a
        """)
        reject("unary-var-Nat", """
            fun g(): Int { unchecked var j: Nat = -7; return j }
        """, "E-TYPE-MISMATCH")
        pass("unary-var-Int", """
            fun g(): Int { unchecked var i: Int = -7; return i }
        """)
        pass("unary-int-neg", """
            fun h(a: Int): Int = -a
        """)
    }

    // -------------------- 既有行为不回退：Rat → Int 的降级归塔 --------------------
    @Test
    fun existingFloorBehavior() {
        reject("floor-r-to-nat", """
            fun floor(r: Rat): Int = 0
            fun wantNat(n: Nat): Nat = 0
            fun give(r: Rat): Nat = wantNat(floor(r))
        """, "E-TYPE-MISMATCH")
        pass("floor-r-to-int", """
            fun floor(r: Rat): Int = 0
            fun wantInt(n: Int): Nat = 0
            fun give(r: Rat): Nat = wantInt(floor(r))
        """)
    }

}