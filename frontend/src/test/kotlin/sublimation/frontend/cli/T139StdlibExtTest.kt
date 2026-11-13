package sublimation.frontend.cli

import sublimation.frontend.diag.Severity
import sublimation.frontend.semantic.PreludeDecls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * t139 B2（虚拟扩展方法 retire → stdlib 源 + **ext 限定调位**新通道 / R-23）: 数据源替换纪律的回归门。
 *
 * - EXT_METHODS 虚拟表 retire（唯一条目 @mut List[a].add）→ stdlib list 件源声明，**体仍空**
 * （OQ-4 队长裁 b 签名面延续：体随「运行时桥规格」后续件落，R-15 兑现债登记）；
 * - t141（R-24 v2）：add 落体改函数级 @mutable 形态（≡ @mut @ref 接收者糖，两形态 HIR 同源）；
 * - 查询链不变（本模块 > 挂载子 > prelude 虚拟层）：retired 键经 RETIRED_EXT_METHODS 允许表注入
 * 父域虚拟层（SymbolTable.virtualExtMethods——builtins 懒登记 id 序零漂移），stdlib 快照面
 * 对 retire 键跳过（方法糖解析源 = 注入虚拟层，HIR 形态零差分）；
 * - **ext 限定调位**（B2 新绿面）：模块名.扩展名（接收者, 实参）经由快照 ext 面解析（自由函数面
 * 未命中时兜底），HIR = HirField 模块别名/field=ext 符号 + D-MODULE-REF（提案 §2.4 zero schema）。 */
class T139StdlibExtTest {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun scriptStd(src: String, scriptMode: Boolean = true): FrontendOutput {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode, noDegrade = false, noLift = false)
        return compileFrontendImpl(src, "t139.subl", scriptMode, false, false,
            su.associate { it.alias to it.snapshot }, sd)
    }

    /** 门 1：快照条目级——ext add 入 stdlib list 快照（数据源替换举证）+ 虚拟表撤回。 */
    @Test fun 快照ext条目落位() {
        val (su, _) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        val extHeads = su.filter { it.snapshot.stdlib }.flatMap { s -> s.snapshot.exts.keys }.toSet()
        assertTrue("List" in extHeads, "List 头扩展面入快照")
        val listExts = su.filter { it.snapshot.stdlib }.first { it.alias == "list" }.snapshot.exts["List"]?.keys
        assertTrue(listExts != null && "add" in listExts, "add 入 list 件扩展面: $listExts")
        assertEquals(0, PreludeDecls.EXT_METHODS.size, "EXT_METHODS 虚拟表 retire（空表）")
        assertEquals(true, ("List" to "add") in PreludeDecls.RETIRED_EXT_METHODS, "retire 键面在位")
    }

    /** 门 2：体仍空——签名面延续（OQ-4 b：别写结构性体，R-15 兑现债登记）。 */
    @Test fun add体仍空() {
        val (su, _) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        val d = su.filter { it.snapshot.stdlib }.first { it.alias == "list" }.snapshot.exts["List"]?.get("add")?.decl
        assertNotNull(d, "list 快照含 add 扩展声明")
        assertEquals(true, d?.body == null, "add 体仍空（体随运行时桥规格后续件落）")
        assertEquals(true, d.annotations.contains("mutable"), "add 持 @mutable 函数级注解（R-24 v2 纯糖形态）")
        assertEquals(true, (d.receiver as? sublimation.frontend.ast.NamedType)?.name == "List",
            "snapshot 声明接收者保持 List 头（@mut @ref 提升在 HIR 化层——快照面零形态差）")
    }

    /** 门 3：方法糖解析源零差分——xs.add(v) 走注入虚拟层（builtins 懒登记符号），HIR 形态等同
     *  retire 前（D-METHOD-SUGAR 无 D-MODULE-REF——模块面命中会落模块符号 id 而漂移）。 */
    @Test fun 方法糖解析源零差分() {
        val o = scriptStd(
            "@impure fun m(): Null {\n    var xs : @mut List[Nat] = mutableListOf(1)\n    unchecked xs.add(2)\n    print(xs)\n}")
        assertEquals(emptyList<String>(), errs(o), "方法糖 add 解析绿: " + errs(o))
        val addCall = Regex("\"name\":\\s*\"add\"[^}]*\"symbol\"").findAll(o.json).count()
        assertTrue(addCall > 0, "HIR add 调用位落符号引用")
        assertTrue(Regex("\"methodSugar\":\\s*true").containsMatchIn(o.json), "方法糖位 true")
        assertFalse(Regex("""D-MODULE-REF""").containsMatchIn(o.json), "add 经注入层解析：无模块面旁注")
    }

    /** 门 4：ext 限定调位（B2 新绿面）——list.add(xs, v) / list.map(xs, f) 经快照 ext 面解析。 */
    @Test fun ext限定调位绿() {
        val o = scriptStd(
            "fun dbl(n: Nat): Nat = n * 2\nfun m(): Nat {\n    var xs : List[Nat] = Cons(1, Nil)\n    var ys : List[Nat] = list.add(xs, 9)\n    unchecked return list.fold(list.map(xs, dbl), 0) { acc, x => acc + x }\n}")
        assertEquals(emptyList<String>(), errs(o), "限定 ext 全家绿: " + errs(o))
        assertTrue(Regex("""D-MODULE-REF""").containsMatchIn(o.json), "限定调位落 D-MODULE-REF 旁注")
    }

    /** 门 5：负例先红——缺件早死（挂载模块未设 ext 名）+ 无挂载通道撤回 + 直接调形态隔离。 */
    @Test fun 负例先红() {
        val o = scriptStd(
            "fun dbl(n: Nat): Nat = n * 2\nfun m(): Nat {\n    var xs : List[Nat] = Cons(1, Nil)\n    unchecked return math.map(xs, dbl)\n}")
        assertTrue(errs(o).any { it == "E-UNBOUND-NAME" }, "math 无 map 扩展：限定调位缺件先红")
        val bare = scriptStd(
            "fun m(): Nat {\n    var xs : @mut List[Nat] = mutableListOf(1)\n    unchecked return add(xs, 9)\n}")
        assertTrue(errs(bare).any { it == "E-UNBOUND-NAME" }, "裸名直接调 add 不解析（R-22：方法糖/限定唯一通道）")
        val ns = compileFrontendImpl(
            "fun m(): Nat { var xs : Nat = 1; unchecked return list.add(xs, 9) }", "t139ns.subl",
            true, false, false, emptyMap(), sublimation.frontend.diag.DiagBag())
        assertTrue(ns.diag.diags.filter { it.severity == Severity.ERROR }.isNotEmpty(),
            "无 stdlib 挂载：list 限定面撤回（未挂载早死）")
    }

    /** 门 6：用户同名扩展优先（P-7——查询链本模块 > 注入层替身）。 */
    @Test fun 用户同名扩展优先() {
        val o = scriptStd(
            "fun List[a].add[a](v: a): List[a] = Cons(v, self)\n@impure fun main(): Null {\n  var xs : List[Nat] = Nil\n  unchecked print(xs.add(1))\n}")
        assertTrue(errs(o).none { it == "E-IMPURE-REQUIRED" }, "用户同名扩展优先（替身让位，无 @mut 门）")
    }

    /** 门 7：编译确定性——含 add 调用面同源双导出字节同（数据源替换 id 分配稳定）。 */
    @Test fun 编译确定性() {
        val src = "@impure fun m(): Null {\n    var xs : @mut List[Nat] = mutableListOf(1)\n    unchecked xs.add(2)\n    print(xs)\n}"
        val o1 = scriptStd(src)
        val o2 = scriptStd(src)
        assertEquals(o1.json, o2.json, "双导出字节同（注入层 builtins id 序稳定）")
    }

    /** 门 8：stdlib 全件零红——B2 后 DAG 分层不变。 */
    @Test fun 全件零红() {
        val (_, sd) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        assertFalse(sd.hasError, "B2 后 stdlib 全件零红: " + sd.diags.map { it.code }.joinToString(", "))
    }
}
