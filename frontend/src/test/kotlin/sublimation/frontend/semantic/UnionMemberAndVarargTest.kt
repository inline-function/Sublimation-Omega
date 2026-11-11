package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirFile
import sublimation.frontend.json.HirJson
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t48（ADR-32）：联合成员隶属分派（R-29）+ @list 变长形参（R-30）。
 * 修复前（23:39Z 探针实测）：
 * - show(Good)：实参静态头是 struct 名（Good），扩展登记在别名头 Verdict 下 → E-UNBOUND-NAME；
 * - fun listOf[T](@list items: T): List[T]：体位 items:T 与返回 List[T] 不符 + 调用位 E-ARITY/缺实参。
 */
class UnionMemberAndVarargTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "t48.subl")
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

    // ---- 任务 1：联合成员隶属分派（R-29） ----

    @Test
    fun unionMemberBareCtorDispatches() {
        val (_, bag) = script(
            "struct Good()\n" +
            "struct Bad()\n" +
            "type Verdict = Good | Bad\n" +
            "fun Verdict.show(): Nat = 1\n" +
            "var s = show(Good)")
        assertFalse(errs(bag).isNotEmpty(), "show(Good) 按成员隶属回退到别名头 Verdict；actual=" + errMsgs(bag))
    }

    @Test
    fun unionMemberTypedVarDispatches() {
        val (_, bag) = script(
            "struct Good()\n" +
            "struct Bad()\n" +
            "type Verdict = Good | Bad\n" +
            "fun Verdict.show(): Nat = 1\n" +
            "var v: Verdict = Good\n" +
            "var s = show(v)")
        assertFalse(errs(bag).isNotEmpty(), "typed var 走 t47 R-27 别名身份；actual=" + errMsgs(bag))
    }

    @Test
    fun unionMemberWithFieldsDispatches() {
        val (_, bag) = script(
            "struct Ver(n: Nat)\n" +
            "struct Bad()\n" +
            "type V2 = Ver | Bad\n" +
            "fun V2.show(): Nat = 1\n" +
            "var s = show(Ver(1))")
        assertFalse(errs(bag).isNotEmpty(), "带字段成员的 ctor 调用同样命中；actual=" + errMsgs(bag))
    }

    @Test
    fun multiUnionOneHasExtDispatches() {
        val (_, bag) = script(
            "struct Good()\n" +
            "struct Bad()\n" +
            "type Verdict = Good | Bad\n" +
            "type Mirror = Good | Bad\n" +
            "fun Verdict.show(): Nat = 1\n" +
            "fun Mirror.tag(): Nat = 2\n" +
            "var s = show(Good)")
        assertFalse(errs(bag).isNotEmpty(), "多联合成员时按桶注册筛选；actual=" + errMsgs(bag))
    }

    @Test
    fun multiUnionAmbiguityPicksLatest() {
        val (hir, bag) = script(
            "struct Good()\n" +
            "struct Bad()\n" +
            "type Verdict = Good | Bad\n" +
            "type Mirror = Good | Bad\n" +
            "fun Verdict.show(): Nat = 1\n" +
            "fun Mirror.show(): Nat = 2\n" +
            "var s = show(Good)")
        assertFalse(errs(bag).isNotEmpty(), "歧义候选仍可分派（不落 E-UNBOUND-NAME）；actual=" + errMsgs(bag))
        val ambig = bag.diags.any { it.code == "D-EXT-UNION-AMBIG" }
        assertTrue(ambig, "多方命中留 D-EXT-UNION-AMBIG supplement；actual=" + errMsgs(bag))
    }

    @Test
    fun unionMemberExtMissingReports() {
        val (_, bag) = script(
            "struct Good()\n" +
            "type Verdict = Good\n" +
            "var s = nosuch(Good)")
        assertEquals(listOf("E-UNBOUND-NAME"), errCodes(bag), "回退不得成万金油；actual=" + errMsgs(bag))
    }

    @Test
    fun methodSugarUnionMemberDispatches() {
        val (_, bag) = script(
            "struct Good()\n" +
            "struct Bad()\n" +
            "type Verdict = Good | Bad\n" +
            "fun Verdict.show(): Nat = 1\n" +
            "var s = Good.show()")
        assertFalse(errs(bag).isNotEmpty(), "方法糖按成员隶属回退；actual=" + errMsgs(bag))
    }

    // ---- 任务 2：@list 变长形参（R-30） ----

    @Test
    fun varargZeroOneManyOk() {
        val (_, bag) = script(
            "fun listOf[T](@list items: T): List[T] = items\n" +
            "var a = listOf()\n" +
            "var b = listOf(1)\n" +
            "var c = listOf(1, 2, 3)")
        assertFalse(errs(bag).isNotEmpty(), "0/1/3 实参全过；actual=" + errMsgs(bag))
    }

    @Test
    fun varargElementTypeMismatchReports() {
        val (_, bag) = script(
            "fun listOf[T](@list items: T): List[T] = items\n" +
            "var a = listOf(1, True)")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag), "actual=" + errMsgs(bag))
    }

    @Test
    fun varargNotLastReports() {
        val (_, bag) = script(
            "fun f(@list a: Nat, b: Nat): Nat = 0\n" +
            "var r = f(1, 2)")
        assertEquals(listOf("E-VARARG-LAST"), errCodes(bag), "actual=" + errMsgs(bag))
    }

    @Test
    fun varargWithDefaultReports() {
        val (_, bag) = script("fun f(@list a: Nat = 0): Nat = 0")
        assertEquals(listOf("E-VARARG-DEFAULT"), errCodes(bag), "actual=" + errMsgs(bag))
    }

    @Test
    fun varargWithRequiredPrefix() {
        val (_, bag) = script(
            "fun f(n: Nat, @list rest: Nat): Nat = n\n" +
            "var a = f(1)\n" +
            "var b = f(1, 2, 3)\n" +
            "var c = f()")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag), "前序必填位缺省照旧报缺实参；actual=" + errMsgs(bag))
    }

    @Test
    fun nonVarargArityUnchanged() {
        val (_, bag) = script(
            "fun f(a: Nat): Nat = a\n" +
            "var r1 = f(1, 2)\n" +
            "var r2 = f()")
        assertEquals(listOf("E-ARITY", "E-TYPE-MISMATCH"), errCodes(bag), "非变长函数 arity 口径不变；actual=" + errMsgs(bag))
    }

    @Test
    fun varargFlagEmittedInJson() {
        // D-9：HirParam 带变长旗标（annotations 含 list），调用点保留实际实参个数
        val (hir, _) = script("fun listOf[T](@list items: T): List[T] = items\nvar b = listOf(1, 2, 3)")
        val json = HirJson.emit(hir, DiagBag(), "t48.subl")
        val i = json.indexOf("HirParam")
        assertTrue(i > 0, "形参节点落 JSON")
        val seg = json.substring(i, (i + 900).coerceAtMost(json.length))
        assertTrue(seg.contains("annotations"), "形参节点带 annotations 旗标；seg=" + seg)
        assertTrue(seg.contains("list"), "变长旗标 @list 落 JSON；seg=" + seg)
    }
}
