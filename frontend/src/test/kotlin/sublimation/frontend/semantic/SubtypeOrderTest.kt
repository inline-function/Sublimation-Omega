package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.cli.compileStdlibUnits
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * t62（ADR-39，P-10 步骤 2）：子类型偏序验收——subtype 为全前端唯一相容性判据，
 * 正负例成对覆盖 ST-1~ST-15 承诺形态（每条负例配同族正例或结构对照例）。
 */
class SubtypeOrderTest {

    // t138（R-23 B1）：prelude 虚拟层 retire——编译挂 stdlib 快照（与 CLI 默认挂载同构；
    // 虚拟 struct/别名数据源已换 stdlib 源——P-7 用户同名优先口径不变）
    private val stdlibMounts: Map<String, ModuleSnapshot> =
        compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            .let { (u, _) -> u.associate { it.alias to it.snapshot } }
    private fun script(src: String): DiagBag {
        val pr = parseScriptSourceWithNotes(src, "t62.subl")
        val (_, bag) = compile(pr.file, pr.notes, scriptMode = false, mounted = stdlibMounts)
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

    private fun noCode(name: String, src: String, forbidden: String) {
        val c = codes(src)
        assertFalse(c.contains(forbidden), "负例「" + name + "」不得残留旧码 " + forbidden + "（G3 码名迁移）；actual=" + c)
    }

    private fun pass(name: String, src: String) {
        val e = script(src).diags.filter { it.severity == Severity.ERROR }
        assertTrue(e.isEmpty(), "正例「" + name + "」须无诊断；actual=" + e)
    }

    // -------------------- ST-1~ST-5/ST-10/ST-11：数值塔（Nat <: Int <: Rat 单向；Real 不入塔）+ Any 真顶 --------------------
    @Test
    fun st01_st05_towerAndAny() {
        pass("TOWER-NAT-INT-RAT", """
            fun id(a: Int): Rat = a
            
        """)
        reject("TOWER-STR-TO-NAT", """
            var x: Nat = "abc"
        """, "E-TYPE-MISMATCH")
        reject("TOWER-RAT-TO-NAT", """
            fun id(a: Rat): Nat = a
        """, "E-TYPE-MISMATCH")
        reject("TOWER-NAT-TO-REAL", """
            struct Real
            var x: Real = 1
        """, "E-TYPE-MISMATCH")
        reject("TOWER-REAL-TO-NAT", """
            struct Real
            var r: Real = 1
            fun id(a: Nat): Nat = r
        """, "E-TYPE-MISMATCH")
        pass("ST11-NAT-TO-ANY", """
            var x: Any = 1
            
        """)
    }

    // -------------------- ST-6：Nothing 真底（单向供给者；want=Nothing 仅接受自身） --------------------
    @Test
    fun st06_bottomNothing() {
        pass("BOTTOM-NOTHING-SUPPLY", """
            // t97 阶段 B:递归无缩小实参——unchecked 放行（本例证的是 Nothing 真底供给，非停机性）
            fun boom(): Nothing {
                unchecked return boom()
            }
            var x: Nat = boom()
            
        """)
        reject("BOTTOM-NAT-TO-NOTHING", """
            fun id(a: Nothing): Nothing = 1
        """, "E-TYPE-MISMATCH")
    }

    // -------------------- ST-8：联合单向注入（成员→联合成立；联合→成员须 is 收窄） --------------------
    @Test
    fun st08_unionInjection() {
        pass("UNION-MEMBER-INJECT", """
            fun f(o: Optional[Nat]): Nat = 0
            var r = f(Some(3))
            
        """)
        reject("UNION-TO-MEMBER-NO-NARROW", """
            var u: Optional[Nat] = Some(3)
            var x: Nat = u
        """, "E-TYPE-MISMATCH")
        pass("UNION-WIDER-BOTH", """
            type W = Nat | Str
            type V = W | Bool
            var w: W = 1
            var v: V = w
            
        """)
    }

    // -------------------- ST-7：函数型参数逆变 + 返回协变（正例/违例各两方向） --------------------
    @Test
    fun st07_funTypeVariance() {
        pass("FUN-CONTRA-LEGAL", """
            fun g(f: (Nat) => Nat): Nat = f(1)
            var r = g(fun (n: Rat): Nat = 0)
            
        """)
        pass("FUN-CO-LEGAL", """
            fun g(f: (Nat) => Rat): Rat = f(1)
            var r = g(fun (n: Nat): Nat = n + 1)
            
        """)
        reject("FUN-CONTRA-VIOLATION", """
            fun g(f: (Any) => Nat): Nat = f(1)
            var r = g(fun (n: Nat): Nat = n + 1)
        """, "E-TYPE-MISMATCH")
        reject("FUN-CO-VIOLATION", """
            fun g(f: (Nat) => Nat): Nat = 0
            var r = g(fun (n: Nat): Any = n)
        """, "E-TYPE-MISMATCH")
    }

    // -------------------- ST-11.1：@mut 视图（@mut T <: T 单向；T <: @mut T 不成立；@mut T <: @mut U 仅当 T=U） --------------------
    @Test
    fun st11_1_mutView() {
        pass("MUT-READ-VIEW", """
            var a: @mut Nat = 1
            var b: Nat = a
            
        """)
        pass("MUT-BOX-INIT", """
            var a: @mut Nat = 1
            
        """)
        reject("MUT-PLAIN-TO-MUT-FUNRET", """
            fun g(a: Nat): @mut Nat = a
        """, "E-TYPE-MISMATCH")
        reject("MUT-LIST-INVARIANT", """
            fun f(xs: @mut List[Nat]): @mut List[Any] = xs
        """, "E-TYPE-MISMATCH")
        pass("MUT-LIST-COVAR", """
            fun g(xs: List[Nat]): List[Nat] = xs
            
        """)
    }

    // -------------------- ST-13.1：List[A] <: List[B] ⟺ A <: B（协变；@mut 不变性见上节） --------------------
    @Test
    fun st13_1_containerVariance() {
        pass("LIST-COVARIANT-ANY", """
            fun g(xs: List[Nat]): List[Any] = xs
            
        """)
    }

    // -------------------- 2.9：结构体结构满足（宽度扩展 + 深度子类型） --------------------
    @Test
    fun st2_9_structural() {
        pass("STRUCT-WIDEN", """
            struct P(x: Nat, y: Nat)
            struct Q(x: Nat, y: Nat, z: Nat)
            var p: P = Q(1, 1, 1)
            
        """)
        reject("STRUCT-MISSING-FIELD", """
            struct P(x: Nat, y: Nat)
            struct Q(x: Nat)
            var p: P = Q(1)
        """, "E-TYPE-MISMATCH")
        reject("STRUCT-DEEP-FIELD", """
            struct A(x: Nat)
            struct B(x: Int)
            var a: A = B(1)
        """, "E-TYPE-MISMATCH")
        pass("STRUCT-DEEP-FIELD-OK", """
            struct A(x: Rat)
            struct B(x: Int)
            var a: A = B(1)
            
        """)
        pass("STRUCT-SAME-NAME", """
            struct A(x: Nat)
            var a: A = A(1)
            
        """)
    }

    // -------------------- ST-3/ST-9：满足即子类型（结构满足）+ 特质继承（父链闭包） --------------------
    @Test
    fun st03_st09_trait() {
        pass("TRAIT-SATISFY", """
            trait Show { fun show(): Str }
            struct P(x: Nat)
            fun P.show(): Str = "p"
            var s: Show = P(1)
            
        """)
        reject("TRAIT-MISSING-EXT", """
            trait Show { fun show(): Str }
            struct P(x: Nat)
            var s: Show = P(1)
        """, "E-TYPE-MISMATCH")
        pass("TRAIT-LEVELS", """
            trait Base { fun baseOp(): Nat }
            trait Sub : Base { fun subOp(): Nat }
            struct W(v: Nat)
            fun W.baseOp(): Nat = self.v
            fun W.subOp(): Nat = self.v
            var s: Sub = W(1)
            var b: Base = s
            
        """)
        reject("TRAIT-LEVELS-MISSING", """
            trait Base { fun baseOp(): Nat }
            trait Sub : Base { fun subOp(): Nat }
            struct W(v: Nat)
            fun W.baseOp(): Nat = self.v
            var s: Sub = W(1)
        """, "E-TYPE-MISMATCH")
    }

    // -------------------- ST-13/ST-14：界——[T: X] 合法（上界）；声明位不变 + 界 → E-BOUND-INVARIANT --------------------
    @Test
    fun st13_st14_bounds() {
        pass("BOUND-LEGAL-CONCRETE", """
            fun use[T: Nat](a: T): Nat = 0
            var r = use(1)
            
        """)
        reject("BOUND-INVARIANT-MIXED", """
            fun k[T: Nat](x: T): T = x
            var z = k(1)
        """, "E-BOUND-INVARIANT")
        noCode("BOUND-NO-OLD-TC-BOUNDARY", """
            fun k[T: Nat](x: T): T = x
            var z = k(1)
        """, "E-TC-BOUNDARY")
        reject("BOUND-UNRESOLVED", """
            fun use[T: NoSuch](a: T): Nat = 0
        """, "E-UNBOUND-NAME")
        reject("BOUND-REJECT-STR", """
            fun use[T: Nat](a: T): Nat = 0
            var r = use("abc")
        """, "E-TYPE-MISMATCH")
    }

}