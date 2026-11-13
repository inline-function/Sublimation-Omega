package sublimation.frontend.semantic

import sublimation.frontend.cli.compileStdlibUnits
import sublimation.frontend.diag.Severity
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.semantic.compileWithRounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t153（诊断扩批二 / H 静态 ×4 + D 自动活 ×3）：批二七新码——
 * H 族「这段代码可以写得更好」（hint，合法但可改进）;D 族「编译器自动做的活」（supplement，一律不拦）。
 *
 * - H-CONS-BUILD：Cons 链手拼 ≥2 层（第二位嵌套计数，纯 AST）→ 建议 listOf（R-26.3 正道）。
 * - H-UNUSED（V1 仅函数局部域——噪声控制）：形参/局部 var 声明后体 HIR 零名引用（destruct/默认值/
 *   _ 前缀/契约 by 断言（posts）一律免判定）。
 * - H-EXPLICIT-TYPE-ARG：单理论参数的全部形参位实参推导类型与显式角表渲染一致 → 可省略
 *   （零分配：AST 形参位 occursInTypeAst + 既有 argTypes 渲染比对）。
 * - H-IMPURE-REDUNDANT：@impure 标注在但体无任何不纯证据（impure 名集 ∪ 内建 / 方法糖 / 字段写）。
 * - D-SHADOW：局部遮蔽外层同名局部/形参——告示遮蔽解析语义将生效（与 H-SHADOW 互补：H 含全局/
 *   同域/字段面，D 专外层局部/形参 + 自动活告示口径）。
 * - D-VARIANCE（V1 保守口径）：本文件声明的类型参数 σ_imm/σ_mut 双视图格结论——仅非平凡
 *   （逆变/不变/双视分歧）发；σ_imm=σ_mut=协变（默认即协）不发（刷屏控制）。
 * - D-TRAIT-LOCAL：满足判定路由命中帧链来源扩展（局部扩展）——具体类型在本作用域满足特质；
 *   全局扩展满足是正常通道不报（用户亲定口径）。
 *
 * 纪律：负例先红（实现前七码实测零落）;零分配 nid（批一教训：禁 etype/sigOf——HIR 体 walker
 * 只读既有节点字段，AST 判定走 occursInTypeAst/consChainDepth 等纯静态判）。 */
class T153DiagBatchTwoTest {

    private val stdlibMounts: Map<String, ModuleSnapshot> =
        compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            .let { (u, _) -> u.associate { it.alias to it.snapshot } }

    private fun sc(src: String) =
        parseScriptSourceWithNotes(src, "t153.subs").let { compileWithRounds(it.file, it.notes, scriptMode = true, mounted = stdlibMounts) }

    private fun hints(rs: RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.HINT }.map { it.code }
    private fun supps(rs: RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.SUPPLEMENT }.map { it.code }
    private fun errs(rs: RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    // -------------------- H-CONS-BUILD --------------------

    /** Cons(1, Cons(2, Nil)) = 2 层手拼——listOf 正道告知。 */
    @Test fun consBuild两层嵌套() {
        val rs = sc("var xs = Cons(1, Cons(2, Nil))\nprint(xs)\n")
        assertEquals(emptyList(), errs(rs), "Cons 手拼合法编译: ${errs(rs)}")
        assertTrue(hints(rs).contains("H-CONS-BUILD"), "≥2 层 Cons 手拼须落 H-CONS-BUILD: ${hints(rs)}")
    }

    /** Cons(1, Nil) = 1 层——单层构造不告示（listOf(1) 并无实质改进）。 */
    @Test fun consBuild单层不发() {
        val rs = sc("var xs = Cons(1, Nil)\nprint(xs)\n")
        assertEquals(emptyList(), errs(rs), "单层 Cons 合法编译: ${errs(rs)}")
        assertFalse(hints(rs).contains("H-CONS-BUILD"), "单层 Cons 不须告示: ${hints(rs)}")
    }

    /** 三层链（最深面）与计数。 */
    @Test fun consBuild三层链() {
        val rs = sc("var xs = Cons(1, Cons(2, Cons(3, Nil)))\nprint(xs)\n")
        assertEquals(emptyList(), errs(rs), "三层 Cons 合法编译: ${errs(rs)}")
        assertTrue(hints(rs).contains("H-CONS-BUILD"), "三层 Cons 链须落 H-CONS-BUILD: ${hints(rs)}")
    }

    // -------------------- H-UNUSED（V1 仅函数局部域） --------------------

    /** 未用局部 var——死代码告知。 */
    @Test fun unused局部变量() {
        val rs = sc("fun f() : Nat {\n  var dead = 3\n  return 1\n}\nprint(f())\n")
        assertEquals(emptyList(), errs(rs), "未用局部合法编译: ${errs(rs)}")
        assertTrue(hints(rs).contains("H-UNUSED"), "未用局部须落 H-UNUSED: ${hints(rs)}")
    }

    /** 未用形参——接口噪音告知（块体真面）。 */
    @Test fun unused形参() {
        val rs = sc("fun f(x : Nat) : Nat {\n  return 1\n}\nprint(f(1))\n")
        assertEquals(emptyList(), errs(rs), "未用形参合法编译: ${errs(rs)}")
        assertTrue(hints(rs).contains("H-UNUSED"), "未用形参须落 H-UNUSED: ${hints(rs)}")
    }

    /** 表达式体未用形参（签名/占位惯用法）——V1 免判定（噪声控制;net/regex 占位内建同形）。 */
    @Test fun unused表达式体豁免() {
        val rs = sc("fun g(x : Nat) : Nat = 0\nprint(g(1))\n")
        assertEquals(emptyList(), errs(rs), "表达式体未用形参合法编译: ${errs(rs)}")
        assertFalse(hints(rs).contains("H-UNUSED"), "表达式体未用形参不须告示: ${hints(rs)}")
    }

    /** 已用局部/形参——零告示。 */
    @Test fun unused已用不发() {
        val rs = sc("fun f(x : Nat) : Nat {\n  var y = x + 1\n  return y\n}\nprint(f(1))\n")
        assertEquals(emptyList(), errs(rs), "已用绑定合法编译: ${errs(rs)}")
        assertFalse(hints(rs).contains("H-UNUSED"), "已用绑定不须告示: ${hints(rs)}")
    }

    /** _ 前缀名（弃用惯例）——免判定（噪声控制）。 */
    @Test fun unused弃用名豁免() {
        val rs = sc("fun f(_x : Nat) : Nat {\n  var _dead = 3\n  return 1\n}\nprint(f(1))\n")
        assertEquals(emptyList(), errs(rs), "弃用名合法编译: ${errs(rs)}")
        assertFalse(hints(rs).contains("H-UNUSED"), "_ 前缀名不须告示: ${hints(rs)}")
    }

    /** 契约 by 断言（posts）可引形参名——整函数跳过（保守免假阳性）。 */
    @Test fun unused契约面豁免() {
        val rs = sc("fun f(x : Nat) : Nat by [x > 0] = 1\nprint(f(1))\n")
        assertFalse(hints(rs).contains("H-UNUSED"), "posts 非空的函数不判定 H-UNUSED: ${hints(rs)}")
    }

    // -------------------- H-EXPLICIT-TYPE-ARG --------------------

    /** 显式角表与形参位实参推导一致——冗余告知（构造子位）。 */
    @Test fun explicitTypeArg构造子位() {
        val rs = sc("struct Box[T](v : T)\nvar b = Box[Nat](3)\nprint(b)\n")
        assertEquals(emptyList(), errs(rs), "显式角表合法编译: ${errs(rs)}")
        assertTrue(hints(rs).contains("H-EXPLICIT-TYPE-ARG"), "冗余角表须落 H-EXPLICIT-TYPE-ARG: ${hints(rs)}")
    }

    /** 显式角表与实参推导一致（Str 面）——一致即冗余，仍发。 */
    @Test fun explicitTypeArg一致即发() {
        val rs = sc("struct Box[T](v : T)\nvar b : Box[Str] = Box[Str](\"a\")\nprint(b)\n")
        assertEquals(emptyList(), errs(rs), "角表与实参一致（Str）合法编译: ${errs(rs)}")
        assertTrue(hints(rs).contains("H-EXPLICIT-TYPE-ARG"), "一致角表仍须告示: ${hints(rs)}")
    }

    /** 理论参数只在返回位——显式角表必须，免告示（可推导性判越界）。 */
    @Test fun explicitTypeArg返回位理论参数豁免() {
        val rs = sc("fun wrap[T]() : Optional[T] = None\nvar v = wrap[Nat]()\nprint(v)\n")
        assertEquals(emptyList(), errs(rs), "返回位理论参数函数合法编译: ${errs(rs)}")
        assertFalse(hints(rs).contains("H-EXPLICIT-TYPE-ARG"), "返回位理论参数不告示（显式角表不可省——必须位）: ${hints(rs)}")
    }

    // -------------------- H-IMPURE-REDUNDANT --------------------

    /** @impure 标注 + 体纯——冗余标注告知。 */
    @Test fun impureRedundant体纯() {
        val rs = sc("@impure fun pureAdd(a : Nat, b : Nat) : Nat = a + b\nprint(pureAdd(1, 2))\n")
        assertEquals(emptyList(), errs(rs), "@impure 体纯合法编译: ${errs(rs)}")
        assertTrue(hints(rs).contains("H-IMPURE-REDUNDANT"), "冗余 @impure 须落 H-IMPURE-REDUNDANT: ${hints(rs)}")
    }

    /** @impure 标注 + 体真不纯（调内建 print）——零告示。 */
    @Test fun impureRedundant体不纯不发() {
        val rs = sc("@impure fun f() : Null { print(1) }\nf()\n")
        assertEquals(emptyList(), errs(rs), "@impure 体不纯合法编译: ${errs(rs)}")
        assertFalse(hints(rs).contains("H-IMPURE-REDUNDANT"), "体不纯不须告示: ${hints(rs)}")
    }

    // -------------------- D-SHADOW --------------------

    /** 局部遮蔽外层形参——遮蔽语义生效告示（D 级一律不拦：编译仍过）。 */
    @Test fun dShadow局部遮蔽形参() {
        val rs = sc("fun f(x : Nat) : Nat {\n  var x = x + 1\n  return x\n}\nprint(f(1))\n")
        assertEquals(emptyList(), errs(rs), "遮蔽合法编译（D 级不拦）: ${errs(rs)}")
        assertTrue(supps(rs).contains("D-SHADOW"), "遮蔽外层形参须落 D-SHADOW: ${supps(rs)}")
    }

    /** 无遮蔽——零告示。 */
    @Test fun dShadow无遮蔽不发() {
        val rs = sc("fun f(x : Nat) : Nat {\n  var y = x + 1\n  return y\n}\nprint(f(1))\n")
        assertEquals(emptyList(), errs(rs), "无遮蔽合法编译: ${errs(rs)}")
        assertFalse(supps(rs).contains("D-SHADOW"), "无遮蔽不须告示: ${supps(rs)}")
    }

    /** 局部遮蔽外层局部（块嵌套）——同发。 */
    @Test fun dShadow局部遮蔽局部() {
        val rs = sc("fun f() : Nat {\n  var x = 1\n  {\n    var x = 2\n  }\n  return x\n}\nprint(f())\n")
        assertEquals(emptyList(), errs(rs), "块内遮蔽合法编译: ${errs(rs)}")
        assertTrue(supps(rs).contains("D-SHADOW"), "遮蔽外层局部须落 D-SHADOW: ${supps(rs)}")
    }

    // -------------------- D-VARIANCE --------------------

    /** 特质类型参数仅作成员形参位（负位）→ 逆变——自动判定告示。 */
    @Test fun dVariance逆变参数() {
        val rs = sc("trait Sink[T] {\n  fun Sink[T].take(v : T) : Null\n}\nprint(1)\n")
        assertEquals(emptyList(), errs(rs), "泛型特质合法编译: ${errs(rs)}")
        assertTrue(supps(rs).contains("D-VARIANCE"), "逆变参数须落 D-VARIANCE: ${supps(rs)}")
    }

    /** 纯协变参数（仅正位出现，σ_imm=σ_mut=协变）——默认即协，不告示（刷屏控制）。 */
    @Test fun dVariance纯协变不发() {
        val rs = sc("trait Box[T] {\n  fun Box[T].get() : T\n}\nprint(1)\n")
        assertEquals(emptyList(), errs(rs), "协变特质合法编译: ${errs(rs)}")
        assertFalse(supps(rs).contains("D-VARIANCE"), "纯协变参数不须告示: ${supps(rs)}")
    }

    // -------------------- D-TRAIT-LOCAL --------------------

    /** 局部扩展使具体类型在本作用域满足特质——满足判定路由命中局部扩展（用户亲定口径原例）。 */
    @Test fun dTraitLocal局部扩展满足() {
        val rs = sc("trait T { fun test() }\n" +
            "fun takesT(x : T) : Nat = 1\n" +
            "@impure fun f() : Null {\n" +
            "  fun Nat.test() = print(self)\n" +
            "  1.test()\n" +
            "  print(takesT(1))\n" +
            "}\n" +
            "f()\n")
        assertEquals(emptyList(), errs(rs), "局部满足合法编译: ${errs(rs)}")
        assertTrue(supps(rs).contains("D-TRAIT-LOCAL"), "局部扩展满足须落 D-TRAIT-LOCAL: ${supps(rs)}")
    }

    /** 全局扩展满足特质——正常通道，不告示。 */
    @Test fun dTraitLocal全局扩展不发() {
        val rs = sc("trait T { fun test() }\n" +
            "fun Nat.test() = print(self)\n" +
            "fun takesT(x : T) : Nat = 1\n" +
            "print(takesT(1))\n")
        assertEquals(emptyList(), errs(rs), "全局满足合法编译: ${errs(rs)}")
        assertFalse(supps(rs).contains("D-TRAIT-LOCAL"), "全局扩展满足不须告示: ${supps(rs)}")
    }
}
