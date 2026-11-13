package sublimation.frontend.cli

import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t137 B0-1（collection 五件迁源 / R-23）: 限定引用通道 + 裸名链隔离口径。
 *
 * - 五件全家经**限定引用**（optional.getOrElse / list.head 形态）跨件解引用;
 * - **裸名链只吃 retire 替身三项**（range/sum/summation）——collection 全家不入裸名链
 *   （e2e 冻红样本的 getOrElse/add/put 未解析态保持不变 —— 67/67 零差分的机制保证）。 */
class T137StdlibCollectionTest {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun scriptStd(src: String, scriptMode: Boolean = true): FrontendOutput {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode, noDegrade = false, noLift = false)
        return compileFrontendImpl(src, "t137b1.subl", scriptMode, false, false,
            su.associate { it.alias to it.snapshot }, sd)
    }

    /** 门 1：五件编译零红（stdlib 全件绿——侧目录基线成立的前提）。 */
    @Test fun stdlib五件全家零红() {
        val (_, sd) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        assertFalse(sd.hasError, "stdlib 全件编译零红: " + sd.diags.map { it.code + it.message }.joinToString(";"))
    }

    /** 门 2：限定引用通道——optional.getOrElse(optional.head(xs), d) + list.sum(xs) 跨件解析绿
     * （B1 分层后：head/firstOr 归 optional 件托管，list 件只留纯 List 域——DAG 无环）。 */
    @Test fun 限定引用全家绿() {
        val o = scriptStd(
            "fun m(): Nat {\n" +
            "    unchecked var xs : List[Nat] = Cons(1, Cons(2, Nil))\n" +
            "    return optional.getOrElse(optional.head(xs), 0) + list.sum(xs)\n" +
            "}")
        assertEquals(emptyList<String>(), errs(o), "限定引用跨件解析绿: " + errs(o))
    }

    /** 门 3：裸名链隔离——getOrElse/listOf 等裸名不可解析（红态保持，e2e 冻红同构）。 */
    @Test fun 裸名链隔离红() {
        val o = scriptStd("fun m(): Nat { var o = Some(1) return getOrElse(o, 0) }")
        assertTrue("E-UNBOUND-NAME" in errs(o), "collection 全家不入裸名链——getOrElse 须红: " + errs(o))
    }

    /** 门 4：retire 替身三项仍在裸名链（B0-0 口径不变）。 */
    @Test fun retire替身裸名绿() {
        val o = scriptStd("fun m(): Nat = sum(range(1, 10))")
        assertEquals(emptyList<String>(), errs(o), "range/sum 裸名仍绿: " + errs(o))
    }

    /** 门 5：五件条目在位（限定引用查得见）。 */
    @Test fun 五件快照条目在位() {
        val (su, _) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        val by = su.associateBy { it.alias }
        // t140（B2.2）：加 traits 件（HKT 特质族——限定形态引用 traits.Functor 等）
        // t156（B3）：加 axioms 件（公理库 A1–A3 + L1–L4）
        assertEquals(setOf("list", "optional", "result", "set", "map", "traits", "math", "axioms"),
            by.keys, "五件 + traits + math + axioms")
        val opt = by["optional"]!!
        assertTrue("getOrElse" in opt.snapshot.funs && "isSome" in opt.snapshot.funs &&
            setOf("head", "firstOr", "get", "toResult").all { it in opt.snapshot.funs.keys },
            "optional 全家条目（含 B1 分层托管的 List→Optional 访问器 head/firstOr/get、toResult）")
        val lst = by["list"]!!
        assertTrue(setOf("range", "sum", "slice", "joinToString").all { it in lst.snapshot.funs.keys },
            "list 全家条目（含 B0-1 迁源自由函数；产 Optional 访问器按 B1 DAG 分层移 optional 件）")
        val set = by["set"]!!.snapshot
        assertTrue("add" in set.funs && "union" in set.funs && "toSet" in set.funs, "set 全家条目（含 B1 托管 toSet）")
        val mp = by["map"]!!.snapshot
        assertTrue("put" in mp.funs && "get" in mp.funs && "toMap" in mp.funs, "map 全家条目（含 B1 托管 toMap）")
        val res = by["result"]!!.snapshot
        assertTrue("unwrapOr" in res.funs && "mapOk" in res.funs, "result 全家条目")
    }

    /** 门 6：跨限定通道互挂——list 件内 firstOr 体调 optional.getOrElse（body 层限定引用）。 */
    @Test fun list件内跨件限定引用() {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        assertFalse(sd.hasError, "list 件 firstOr 的 optional.getOrElse 限定调用解析通过: " +
            sd.diags.map { it.code }.joinToString(";"))
        val lst = su.first { it.alias == "list" }
        assertFalse(lst.unit.output.diag.hasError, "list 单件零红")
    }
}
