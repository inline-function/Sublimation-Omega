package sublimation.frontend.cli

import sublimation.frontend.diag.Severity
import sublimation.frontend.module.ModuleSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t138 B1（虚拟 struct/联合别名 retire → stdlib 源 / R-23）: 数据源替换纪律的回归门。
 *
 * - 11 虚拟 struct（Nil/Cons/None/Some/Ok/Err/SetNil/SetCons/MapNil/MapCons）+ 5 联合别名
 *   （Optional/List/Result/Set/Map）retire 为 stdlib collection 源声明；**Channel 留虚拟**（无
 *   channel 件——登记下一批）;
 * - 查询链不变（用户 > 挂载 stdlib 子 > prelude 虚拟层），retired 类型经快照注入父域
 *   （PreludeDecls 允许表门控——stdlib-local 非 retired 名裸名不可见）;
 * - HIR 符号 id 由 builtins 缓存惰性分配（preludeCtorEntry/builtinEntry 顺序零动）——retire
 *   前后同源等价（t122n/t131mod 用户面 52/68 逐字节同 + 16 例差异全为 retType 联合展开位，剥差后逐字节同，
 *   差分台账 jsoncmp/README.md）。 */
class T138StdlibRetireTest {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun scriptStd(src: String, scriptMode: Boolean = true): FrontendOutput {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode, noDegrade = false, noLift = false)
        return compileFrontendImpl(src, "t138.subl", scriptMode, false, false,
            su.associate { it.alias to it.snapshot }, sd)
    }

    /** 门 1：快照条目级——10 struct + 5 联合别名入 stdlib 快照（数据源替换举证）。 */
    @Test fun 快照条目retire在位() {
        val (su, _) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        val names = su.filter { it.snapshot.stdlib }.flatMap { s -> s.snapshot.structs.keys }.toSet()
        setOf("Nil", "Cons", "None", "Some", "Ok", "Err", "SetNil", "SetCons", "MapNil", "MapCons").forEach {
            assertTrue(it in names, "retired structure 入 stdlib 快照: $it")
        }
        val aliases = su.filter { it.snapshot.stdlib }.flatMap { it.snapshot.aliases.keys }.toSet()
        setOf("Optional", "List", "Result", "Set", "Map").forEach {
            assertTrue(it in aliases, "retired 联合别名入 stdlib 快照: $it")
        }
    }

    /** 门 2：用户面解析等价——retired 构造子/别名裸名在用户域解析到 stdlib 源声明（符号 ctor 面）。 */
    @Test fun 用户面解析等价() {
        val o = scriptStd(
            "fun m(): Nat {\n" +
            "    unchecked var xs : List[Nat] = Cons(1, Cons(2, Nil))\n" +
            "    unchecked var o : Optional[Nat] = Some(list.size(xs))\n" +
            "    unchecked var r : Result[Nat, Str] = Ok(1)\n" +
            "    unchecked var s : Set[Nat] = SetCons(1, SetNil)\n" +
            "    unchecked var mp : Map[Nat, Str] = MapCons(1, \"a\", MapNil)\n" +
            "    return optional.getOrElse(o, 0)\n" +
            "}")
        assertEquals(emptyList<String>(), errs(o), "用户面 retired 全家解析绿: " + errs(o))
        // ctor 符号面举证：JSON 含 std 源构造子（Cons/Nil/Some）符号项
        val ctorJson = Regex("""kind":\s*"ctor"\s*,\s*"name":\s*"(\w+)"""").findAll(o.json).map { it.groupValues[1] }.toSet()
        setOf("Cons", "Nil", "Some").forEach {
            assertTrue(it in ctorJson, "HIR 符号项中出现 retired 构造子（std 源 ctor 面）: $it")
        }
    }

    /** 门 3：retired 结构形状（字段序）——同虚拟层形状（数据源替换不改形状）。 */
    @Test fun 结构形状同构() {
        val (su, _) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        val decls = su.filter { it.snapshot.stdlib }.flatMap { s -> s.snapshot.structs.values }.associate { it.decl.name to it.decl.theory.isNotEmpty() }
        assertTrue(decls["Cons"] == true, "Cons: 有类型参")
        assertTrue(decls["Some"] == true, "Some: 有类型参")
        assertTrue(decls["MapCons"] == true, "MapCons: 有类型参")
        assertTrue(decls["Nil"] == false, "Nil: 无类型参")
        assertTrue(decls["None"] == false, "None: 无类型参")
        assertTrue(decls["SetNil"] == false, "SetNil: 无类型参")
    }

    /** 门 4：负例先红——构造子元数/形态误用（retire 后仍红——std 源形状同构）。 */
    @Test fun 构造误用先红() {
        val o = scriptStd("fun m(): Nat { unchecked return Cons(1) }")
        assertTrue(errs(o).isNotEmpty(), "Cons 缺尾字段 => 元数红")
        val o2 = scriptStd("fun m(): Nat { unchecked return Nil() }")
        assertTrue(errs(o2).isNotEmpty(), "Nil() 空元构造 => 零参括号红")
        val o3 = scriptStd("fun m(): Nat { unchecked return Some() }")
        assertTrue(errs(o3).isNotEmpty(), "Some() 缺值 => 元数红")
    }

    /** 门 5：裸名面隔离——retired 类型名经允许表注入父域；非允许表 stdlib-local 名（结构体字段、
     *  stdlib 自由函数、件内辅助）裸名不可见（V1 限定引用唯一通道）。 */
    @Test fun 裸名面隔离() {
        val o = scriptStd("fun m(): Nat { unchecked return add(SetNil, 1) }")
        assertTrue(errs(o).any { it == "E-UNBOUND-NAME" }, "add 为 set 件 stdlib 函数：裸名不可解析")
        val o2 = scriptStd("fun m(): Nat { unchecked return getOrElse(Some(1), 0) }")
        assertTrue(errs(o2).any { it == "E-UNBOUND-NAME" }, "getOrElse 为 optional 件 stdlib 函数：裸名不可解析")
        // retired 类型名经允许表可见（对照）
        val o3 = scriptStd("fun n(xs: List[Nat]): Nat = 1")
        assertEquals(emptyList<String>(), errs(o3), "List 别名经允许表入父域：裸名可见（对照）")
    }

    /** 门 6：--no-stdlib（无挂载编译）retired 类型裸名红——虚拟层撤回举证。 */
    @Test fun 无stdlib裸名红() {
        val o = compileFrontendImpl("fun m(): Nat { unchecked return Cons(1, Nil) }", "t138ns.subl",
            true, false, false, emptyMap(), sublimation.frontend.diag.DiagBag())
        assertTrue(o.diag.diags.filter { it.severity == Severity.ERROR }.isNotEmpty(),
            "无 stdlib 挂载：Cons/Nil 裸名红（虚拟层撤回）")
        assertEquals(0, Regex("""kind":\s*"ctor"""").findAll(o.json).count(),
            "无 stdlib 挂载：用户域无 stdlib ctor 面")
    }

    /** 门 7：Channel 留虚拟——无 channel 件，PreludeDecls 虚拟层继续承载（登记下一批）。 */
    @Test fun channel留虚拟() {
        val o = scriptStd("fun m(c: Channel[Nat]): Null { unchecked return null }")
        assertEquals(emptyList<String>(), errs(o), "Channel 虚拟声明仍解析绿（未 retire）")
    }

    /** 门 8：模块编译确定性——同源双编译 HIR JSON 字节同（retire 数据源替换后 id 分配稳定）。 */
    @Test fun 模块样本确定性() {
        val o1 = scriptStd(
            "fun m(): Nat {\n" +
            "    unchecked var xs : List[Nat] = Cons(1, Nil)\n" +
            "    return optional.getOrElse(optional.head(xs), 0)\n" +
            "}")
        val o2 = scriptStd(
            "fun m(): Nat {\n" +
            "    unchecked var xs : List[Nat] = Cons(1, Nil)\n" +
            "    return optional.getOrElse(optional.head(xs), 0)\n" +
            "}")
        assertEquals(o1.json, o2.json, "双导出字节同（含 ctor 符号 id 顺序）")
    }

    /** 门 9：挂载 DAG 无环——B1 分层（list 底层 + optional 托管访问器）后五件全家零红。 */
    @Test fun 分层DAG全家绿() {
        val (_, sd) = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)
        assertFalse(sd.hasError, "B1 分层后 stdlib 全件零红: " + sd.diags.map { it.code }.joinToString(", "))
    }
}
