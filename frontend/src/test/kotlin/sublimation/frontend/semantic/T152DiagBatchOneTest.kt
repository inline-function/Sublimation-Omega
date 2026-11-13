package sublimation.frontend.semantic

import sublimation.frontend.cli.compileStdlibUnits
import sublimation.frontend.diag.Severity
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.semantic.compileWithRounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * t152（诊断扩批一 / W 主战场 ×5）：批一五新警告码——「代码有很大风险，但依旧可以通过编译」。
 *
 * - W-REF-ESCAPE：@ref 视图流到 return 位 / 存入 @mut 字段（R-21 放行不封，风险告知）;V1 口径裁剪——
 *   「传入外域通道」面经 R-28 拒格不复（形参 @mut want 位即外域拒格，入参判据无独立覆盖面）。
 * - W-DEEP-COPY-LOOP：深拷转换点（D-DEEP-COPY 告知位）出现在递归帧内——V1 语言无循环构造
 *  （AST/词法无 while/for），递归帧 = 唯一迭代面（自调用 AST walk 判定，无循环则零该码）。
 * - W-RESULT-IGNORED：纯函数调用作为语句、结果丢弃——V1 保守口径：仅表达式体值函数（= …）且
 *   形参全无 @ref/@mut/@atomic 通道、无 @impure/@mut/@mutable/@theorem/@axiom/@list 位（噪声控制）。
 * - W-REC-NON-TAIL：递归调用非尾位（停机闸已过为前提——失败文件零该码）——尾位 = 块末语句 /
 *   return 值 / when 各臂体 / if 两支尾 / 表达式体本身，其余一律非尾。
 * - W-BRANCH-UNREACHABLE：when 分支被**无 guard** 前序分支完全包含（PatElse > PatBind("_") >
 *   同名 PatCtor 且前序子模式全通配 > 同型 PatIs > 同元全通配 PatTuple）⇒ 永不匹配（死分支）。
 *
 * 纪律：负例先红（实现前五码七面实测零 W）;零分配 nid（W 检查禁 etype/sigOf——68 门逐字
 * 比对基线硬约束，t109 零分配位纪律同源——exprRefType 物化字段读 + AST 通道静态判）。 */
class T152DiagBatchOneTest {

    private val stdlibMounts: Map<String, ModuleSnapshot> =
        compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            .let { (u, _) -> u.associate { it.alias to it.snapshot } }

    private fun sc(src: String) =
        parseScriptSourceWithNotes(src, "t152.subs").let { compileWithRounds(it.file, it.notes, scriptMode = true, mounted = stdlibMounts) }
    private fun warns(rs: RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.WARN }.map { it.code }
    private fun errs(rs: RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    // -------------------- W-REF-ESCAPE：return 位（表达式体 / 块体）+ 外域字段存入 --------------------

    /** @ref 视图作为表达式体函数的返回值——引用直通调用方（快照预期被打破的风险）。 */
    @Test fun refEscape表达式体返回位() {
        val rs = sc("fun g(v : @ref List[Nat]) : @ref List[Nat] = v\n" +
            "var xs : @mut List[Nat] = mutableListOf(1)\n" +
            "var rs2 : @ref List[Nat] = xs\n" +
            "var out : @ref List[Nat] = g(rs2)\n" +
            "print(1)\n")
        assertEquals(emptyList(), errs(rs), "表达式体 @ref 返回合法编译: ${errs(rs)}")
        assertTrue(warns(rs).contains("W-REF-ESCAPE"), "return 位 @ref 直通须落 W-REF-ESCAPE: ${warns(rs)}")
    }

    /** @ref 视图经 return 语句显式返回——同源直通面（块体逐点落）。 */
    @Test fun refEscape块体return位() {
        val rs = sc("fun g(v : @ref List[Nat]) : @ref List[Nat] {\n  return v\n}\n" +
            "var xs : @mut List[Nat] = mutableListOf(1)\n" +
            "var rs2 : @ref List[Nat] = xs\n" +
            "var out : @ref List[Nat] = g(rs2)\n" +
            "print(1)\n")
        assertEquals(emptyList(), errs(rs), "块体 return @ref 合法编译: ${errs(rs)}")
        assertTrue(warns(rs).contains("W-REF-ESCAPE"), "return 语句 @ref 直通须落 W-REF-ESCAPE: ${warns(rs)}")
    }

    /** @ref 视图存入 @mut 字段——属主对象逃逸作用域时引用随之逃逸。 */
    @Test fun refEscape字段存入() {
        val rs = sc("@mut struct Box(@mut fld : @ref List[Nat])\n" +
            "@impure fun main(): Null {\n" +
            "  var xs : @mut List[Nat] = mutableListOf(1)\n" +
            "  var rs2 : @ref List[Nat] = xs\n" +
            "  @mut var bb = Box(xs)\n" +
            "  bb.fld = rs2\n" +
            "  print(1)\n" +
            "}\n")
        assertEquals(emptyList(), errs(rs), "@mut 字段存 @ref 值合法编译: ${errs(rs)}")
        assertTrue(warns(rs).contains("W-REF-ESCAPE"), "字段存 @ref 须落 W-REF-ESCAPE: ${warns(rs)}")
    }

    // -------------------- W-DEEP-COPY-LOOP：递归帧内的深拷转换点 --------------------

    /** 自调用函数体内 D-DEEP-COPY 点（var 深 @mut 源 + 实参位）——每层递归重复深拷。 */
    @Test fun 深拷递归帧() {
        val rs = sc("fun countdown(a : @mut List[Nat], n : Nat) : Nat {\n" +
            "  var b : List[Nat] = a\n" +
            "  return if (n == 0) 0 else countdown(a, n - 1)\n}\n" +
            "var xs : @mut List[Nat] = mutableListOf(1)\n" +
            "print(countdown(xs, 3))\n")
        assertEquals(emptyList(), errs(rs), "递归帧深拷合法编译: ${errs(rs)}")
        val dc = warns(rs).count { it == "W-DEEP-COPY-LOOP" }
        assertTrue(dc >= 1, "递归帧内 D-DEEP-COPY 点须落 W-DEEP-COPY-LOOP: ${warns(rs)}")
    }

    /** 非递归函数的深拷点零该码（控制面：仅递归帧伴发）。 */
    @Test fun 深拷非递归帧免() {
        val rs = sc("fun once(a : @mut List[Nat]) : Nat {\n" +
            "  var b : List[Nat] = a\n" +
            "  return 0\n}\n" +
            "var xs : @mut List[Nat] = mutableListOf(1)\n" +
            "print(once(xs))\n")
        assertEquals(emptyList(), errs(rs), "非递归深拷合法编译: ${errs(rs)}")
        assertTrue(!warns(rs).contains("W-DEEP-COPY-LOOP"), "非递归深拷免 W-DEEP-COPY-LOOP: ${warns(rs)}")
    }

    // -------------------- W-RESULT-IGNORED：纯函数调用结果丢弃 --------------------

    /** 表达式体纯函数调用作为语句、结果丢弃——纯调用无副作用，疑似 bug。 */
    @Test fun 纯函数结果丢弃() {
        val rs = sc("fun double(x : Nat) : Nat = x * 2\n" +
            "double(3)\n" +
            "print(1)\n")
        assertEquals(emptyList(), errs(rs), "结果丢弃语句合法编译: ${errs(rs)}")
        assertTrue(warns(rs).contains("W-RESULT-IGNORED"), "纯函数结果丢弃须落 W-RESULT-IGNORED: ${warns(rs)}")
    }

    /** 块体函数豁免（语句式用法，噪声控制）——t129 既有样本回归保护。 */
    @Test fun 块体函数豁免() {
        val rs = sc("fun g[T](x : T) { print(x) }\n" +
            "var v : Nat = 3\ng(&v)\nprint(v)\n")
        assertTrue(!warns(rs).contains("W-RESULT-IGNORED"), "块体函数免 W-RESULT-IGNORED: ${warns(rs)}")
    }

    /** 块尾表达式=值位投影（if 分支块 / when 臂块），结果不被丢弃——豁免（误报防护）。 */
    @Test fun 块尾值位豁免() {
        val rs = sc("fun double(x : Nat) : Nat = x * 2\n" +
            "fun g(x : Nat) : Nat = if (x == 0) { double(1) } else { double(2) }\n" +
            "print(g(3))\n")
        assertEquals(emptyList(), errs(rs), "块尾投影位合法编译: ${errs(rs)}")
        assertTrue(!warns(rs).contains("W-RESULT-IGNORED"), "块尾表达式（值位投影）免 W-RESULT-IGNORED: ${warns(rs)}")
    }

    /** @impure 函数调用豁免（副作用已知，非疑似 bug）。 */
    @Test fun impure调用豁免() {
        val rs = sc("@impure fun p() : Null { print(1) }\n" +
            "var r = p()\n" +
            "print(2)\n")
        assertTrue(!warns(rs).contains("W-RESULT-IGNORED"), "@impure 调用免 W-RESULT-IGNORED: ${warns(rs)}")
    }

    // -------------------- W-REC-NON-TAIL：递归调用非尾位 --------------------

    /** 非尾位递归（结果参与外层运算）——每层递归占栈帧。 */
    @Test fun 非尾位递归() {
        val rs = sc("fun fact(n : Nat) : Nat = if (n == 0) 1 else n * fact(n - 1)\n" +
            "print(fact(3))\n")
        assertEquals(emptyList(), errs(rs), "非尾递归合法编译（停机闸已过）: ${errs(rs)}")
        assertTrue(warns(rs).contains("W-REC-NON-TAIL"), "非尾位递归须落 W-REC-NON-TAIL: ${warns(rs)}")
    }

    /** 尾位递归免（块末返回值即自调用——尾位判定正确性）。 */
    @Test fun 尾位递归免() {
        val rs = sc("fun sumTo(n : Nat, acc : Nat) : Nat = if (n == 0) acc else sumTo(n - 1, acc + n)\n" +
            "print(sumTo(3, 0))\n")
        assertEquals(emptyList(), errs(rs), "尾递归合法编译: ${errs(rs)}")
        assertTrue(!warns(rs).contains("W-REC-NON-TAIL"), "尾位递归免 W-REC-NON-TAIL: ${warns(rs)}")
    }

    // -------------------- W-BRANCH-UNREACHABLE：when 分支被前序包含 --------------------

    /** 先 is Cons(_, _) 后 is Cons(a, b)——后序分支永不匹配（死分支）。 */
    @Test fun 构造分支被前序包含() {
        val rs = sc("fun f(xs : List[Nat]) : Nat = when (xs) { is Cons(_, _) -> 1; is Cons(a, b) -> 2; else -> 0 }\n" +
            "print(f(listOf(1)))\n")
        assertEquals(emptyList(), errs(rs), "死分支合法编译: ${errs(rs)}")
        assertTrue(warns(rs).contains("W-BRANCH-UNREACHABLE"), "被包含分支须落 W-BRANCH-UNREACHABLE: ${warns(rs)}")
    }

    /** 无 guard 前序才包含——带 guard 分支不遮蔽后序（guard 使前序部分匹配）。 */
    @Test fun 带guard前序不含() {
        val rs = sc("fun f(xs : List[Nat]) : Nat = when (xs) { is Cons(h, t) if h > 0 -> 1; is Cons(a, b) -> 2; else -> 0 }\n" +
            "print(f(listOf(1)))\n")
        assertTrue(!warns(rs).contains("W-BRANCH-UNREACHABLE"), "带 guard 前序不遮蔽后序: ${warns(rs)}")
    }

    /** 分支互斥（不同构造子）零该码——正常穷尽不受影响。 */
    @Test fun 互斥分支免() {
        val rs = sc("fun f(xs : List[Nat]) : Nat = when (xs) { is Nil -> 0; is Cons(h, t) -> 1 }\n" +
            "print(f(listOf(1)))\n")
        assertTrue(!warns(rs).contains("W-BRANCH-UNREACHABLE"), "互斥分支免 W-BRANCH-UNREACHABLE: ${warns(rs)}")
    }
}