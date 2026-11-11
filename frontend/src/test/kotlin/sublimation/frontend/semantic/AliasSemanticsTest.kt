package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirFile
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t47（ADR-31）：prelude 权威性 / 别名接收者扩展分派 / ctor 实参位别名展开。
 * 修复前（21:00Z 前探针实测）：
 * - 用户 `type List[T] = Nil | Cons[T]` 落 E-DUP-DECL（BUILTIN_TYPES 仍占位容器名）;
 * - 别名接收者扩展函数三形态分派全落「无同名扩展函数」（expand 把别名身份抹成 Union 头）;
 * - 嵌套 ctor 实参 `f(Some(Some(3)))` 对 `Optional[Optional[Nat]]` 落 E-TYPE-MISMATCH（期望侧别名未展开）。
 */
class AliasSemanticsTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "t47.subl")
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

    private fun errs(bag: DiagBag) = bag.diags.filter { it.severity == Severity.ERROR }
    private fun errCodes(bag: DiagBag) = errs(bag).map { it.code }
    private fun errMsgs(bag: DiagBag) = errs(bag).joinToString("; ") { it.code + ":" + it.message }

    // ---- 任务 1：BUILTIN_TYPES 让位给 prelude 虚拟声明 ----

    @Test
    fun userListAliasOverridesPrelude() {
        // 修复前：E-DUP-DECL「类型别名 List 与既有类型重名」
        val (_, bag) = script(
            "type List[T] = Nil | Cons[T]\n" +
            "struct Nil()\n" +
            "struct Cons[T](head: T, tail: List[T])\n" +
            "var xs: List[Nat] = Nil")
        assertFalse(errs(bag).isNotEmpty(), "用户同名别名按 P-7 覆盖；actual=" + errMsgs(bag))
    }

    @Test
    fun preludeContainerWorksWhenNotDeclared() {
        val (_, bag) = script("var xs: List[Nat] = Nil")
        assertFalse(errs(bag).isNotEmpty(), "未声明时 prelude 虚拟联合别名兜底；actual=" + errMsgs(bag))
    }

    @Test
    fun userStructNamedNilNoDup() {
        val (_, bag) = script("struct Nil()")
        assertFalse(errs(bag).isNotEmpty(), "虚拟 struct 名无占位；actual=" + errMsgs(bag))
    }

    @Test
    fun userOptionalAliasTakesEffect() {
        // P-7：用户别名生效（类型位按用户 target 解析），不再因占位报 E-DUP-DECL
        val (_, bag) = script("type Optional[T] = Nat\nvar x: Optional[Nat] = 0")
        assertFalse(errs(bag).isNotEmpty(), "用户别名 Optional 生效；actual=" + errMsgs(bag))
    }

    // ---- 任务 2：别名接收者的扩展函数分派（三形态） ----

    private val optSrc = "type Opt[T] = None | Some[T]\n" +
        "struct None()\n" +
        "struct Some[T](v: T)\n" +
        "fun inc(n: Nat): Nat = n + 1\n" +
        "fun toOpt(n: Nat): Opt[Nat] = Some(n)\n" +
        "fun Opt[a].map[a, b](f: (a) => b): Opt[b] = None\n"

    @Test
    fun aliasReceiverMethodSugarDispatch() {
        val (_, bag) = script(optSrc +
            "fun g(): Opt[Nat] { var s: Opt[Nat] = Some(3) ; return s.map(inc) }")
        assertFalse(errs(bag).isNotEmpty(), "方法糖 s.map(f) 按别名头分派；actual=" + errMsgs(bag))
    }

    @Test
    fun aliasReceiverFreeFormDispatch() {
        val (_, bag) = script(optSrc +
            "fun g(): Opt[Nat] { var s: Opt[Nat] = Some(3) ; return map(s, inc) }")
        assertFalse(errs(bag).isNotEmpty(), "自由式 map(s, f) 按别名头分派；actual=" + errMsgs(bag))
    }

    @Test
    fun aliasReceiverExtSelfRecursion() {
        // 扩展函数体里调用同族扩展（自递归形态）
        val (_, bag) = script(optSrc +
            "fun Opt[a].chain[a, b](f: (a) => Opt[b]): Opt[b] = None\n" +
            "fun g(): Opt[Nat] { var s: Opt[Nat] = Some(3) ; return s.chain(toOpt) }")
        assertFalse(errs(bag).isNotEmpty(), "扩展自递归分派；actual=" + errMsgs(bag))
    }

    @Test
    fun aliasReceiverDispatchStillChecksArgs() {
        // 分派成功后签名核对不放宽：map 收函数，传 Nat → E-TYPE-MISMATCH
        val (_, bag) = script(optSrc +
            "fun g(): Opt[Nat] { var s: Opt[Nat] = Some(3) ; return s.map(1) }")
        assertTrue(errCodes(bag).contains("E-TYPE-MISMATCH"), "分派后签名核对不放宽；actual=" + errMsgs(bag))
    }

    @Test
    fun preludeListReceiverUserExtDispatch() {
        // prelude List 接收者 + 用户扩展（别名身份来自 prelude 虚拟联合别名）
        val (_, bag) = script(
            "fun List[a].sizeOf[a](): Nat = 0\n" +
            "fun g(): Nat { var xs: List[Nat] = Cons(3, Nil) ; return xs.sizeOf() }")
        assertFalse(errs(bag).isNotEmpty(), "prelude List 接收者的用户扩展方法糖分派；actual=" + errMsgs(bag))
    }

    // ---- 任务 3：ctor 实参位的 expected-type / 别名展开 ----

    @Test
    fun nestedCtorArgMatchesAliasUnion() {
        // 修复前：E-TYPE-MISMATCH「实参 1 Some[Some[Nat]] 与形参 None | Some[Optional[Nat]] 不符」
        val (_, bag) = script("fun f(o: Optional[Optional[Nat]]): Nat = 0\nvar r1 = f(Some(Some(3)))")
        assertFalse(errs(bag).isNotEmpty(), "嵌套 ctor 实参按联合成员展开比对；actual=" + errMsgs(bag))
    }

    @Test
    fun varThenCtorArgMatchesAliasUnion() {
        val (_, bag) = script(
            "fun f(o: Optional[Optional[Nat]]): Nat = 0\n" +
            "var x: Optional[Nat] = Some(3)\n" +
            "var r2 = f(Some(x))")
        assertFalse(errs(bag).isNotEmpty(), "先 var 后 ctor 的实参匹配；actual=" + errMsgs(bag))
    }

    @Test
    fun threeLevelNestingMatches() {
        val (_, bag) = script(
            "fun f(o: Optional[Optional[Optional[Nat]]]): Nat = 0\n" +
            "var r3 = f(Some(Some(Some(3))))")
        assertFalse(errs(bag).isNotEmpty(), "三层嵌套通过；actual=" + errMsgs(bag))
    }

    @Test
    fun ctorArgDeepMismatchStillReports() {
        // 反例 1：内层类型真不符（Bool vs Nat）——必须保持 E-TYPE-MISMATCH
        val (_, bag) = script("fun f(o: Optional[Optional[Nat]]): Nat = 0\nvar r4 = f(Some(Some(True)))")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag), "actual=" + errMsgs(bag))
    }

    @Test
    fun ctorArgLevelMismatchStillReports() {
        // 反例 2：实参嵌套层数多于形参（3 层 Some 对 2 层 Optional）——结构性真错，不放宽
        val (_, bag) = script("fun f(o: Optional[Optional[Nat]]): Nat = 0\nvar r5 = f(Some(Some(Some(3))))")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag), "actual=" + errMsgs(bag))
    }
}