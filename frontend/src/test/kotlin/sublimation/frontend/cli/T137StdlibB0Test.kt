package sublimation.frontend.cli

import sublimation.frontend.diag.Severity
import sublimation.frontend.module.ModuleNode
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.semantic.compile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * t137（stdlib B0-0 / R-23）：PRELUDE_FUNS 三项 retire → stdlib 源的**零差分**落地件。
 *
 * 机制（R-23 裁决口径）:
 * - **数据源替换、分发机制不变**：PRELUDE_FUNS Kotlin 表退役；同名单由挂载 stdlib 模块快照提供
 * （SymbolTable.preludeFun 数据源 = moduleFunNames；Elaborator.preludeFunSig 经快照 sigOf 解析）。
 *   裸名 sum(xs) 走 lookupValue 链原序位 → builtinEntry 懒登记 → HIR 形态零差分（67/68 逐字节 SAME 的机制保证）。
 * - **默认挂载**：编译根（目录/单文件两入口）解析 stdlib 根 .settings 的挂载约定并入挂载表（用户挂载优先——P-7）。
 * - **--no-stdlib 旗**：默认挂载关闭 → 裸名 sum/range/summation 落 E-UNBOUND-NAME（CI 门）。
 * - **stdlib 件旗**：非限定链符号面只吃 stdlib 快照（R-22 用户件限定唯一通道口径不变——裸名不泄漏）。 */
class T137StdlibB0Test {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code + ":" + it.message }

    /** 脚本单文件编译（同构 Main 单文件入口：stdlib 快照注入 mounted 面）。 */
    private fun scriptStd(src: String, noStdlib: Boolean = false, scriptMode: Boolean = true): FrontendOutput {
        val (su, sd) = compileStdlibUnits(noStdlib, scriptMode, noDegrade = false, noLift = false)
        return compileFrontendImpl(src, "t137.subl", scriptMode = true, noDegrade = false, noLift = false,
            mounted = su.associate { it.alias to it.snapshot }, extraDiag = sd)
    }

    /** 绿 1：裸名 sum/range/summation 经 stdlib 快照解析（非限定链 R-23）零红。 */
    @Test fun 裸名stdlib函数绿() {
        val o = scriptStd("fun m(): Nat = sum(range(1, 10))")
        assertEquals(emptyList<String>(), errs(o), "sum/range 经 stdlib 快照签名核对通过: " + errs(o))
        val o2 = scriptStd("fun m(): Nat = summation(1, 101)")
        assertEquals(emptyList<String>(), errs(o2), "summation 经 stdlib(math) 快照解析: " + errs(o2))
    }

    /** 绿 2：工程模式单文件入口同构（compileFrontendImpl project 形态）。 */
    @Test fun 工程模式裸名绿() {
        val o = scriptStd(
            "fun m(): Nat {\n" +
            "    unchecked var xs = Cons(1, Cons(2, Nil))\n" +
            "    return sum(xs)\n" +
            "}", scriptMode = false)
        assertEquals(emptyList<String>(), errs(o), "工程模式 sum(xs) 经 stdlib 快照绿: " + errs(o))
    }

    /** 门 3：--no-stdlib → 裸名 sum 落 E-UNBOUND-NAME（旗 CI 门）。 */
    @Test fun noStdlib旗预言红() {
        val o = scriptStd("fun m(): Nat { var xs = Cons(1, Nil) return sum(xs) }", noStdlib = true)
        val codes = errs(o)
        assertTrue("E-UNBOUND-NAME" in codes.map { it.substringBefore(":") },
            "--no-stdlib 下 sum 须红（stdlib 默认挂载关闭）: $codes")
        assertTrue(o.diag.hasError, "红态落定")
    }

    /** 负 4：stdlib 缺件 → 挂载期早死 E-MOUNT-MISSING（sublimation.stdlib 系统属性指空树）。 */
    @Test fun stdlib缺件早死红() {
        val prev = System.getProperty("sublimation.stdlib")
        try {
            System.setProperty("sublimation.stdlib",
                createTempDir("t137-bad-stdlib").absolutePath + "/不存在")
            val (_, sd) = compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            assertTrue(sd.diags.any { it.severity == Severity.ERROR && it.code == "E-MOUNT-MISSING" },
                "stdlib 根未找到须 E-MOUNT-MISSING: " + sd.diags.map { it.code + it.message })
        } finally {
            if (prev == null) System.clearProperty("sublimation.stdlib") else System.setProperty("sublimation.stdlib", prev)
        }
    }

    /** 负 5：stdlib 根存在但挂的件缺失 → E-MOUNT-MISSING（缺件精确报）。 */
    @Test fun stdlib件缺失红() {
        val prev = System.getProperty("sublimation.stdlib")
        val fakeRoot = createTempDir("t137-fake-stdlib")
        try {
            java.io.File(fakeRoot, ".settings").writeText("mount 缺件目录\n", Charsets.UTF_8)
            System.setProperty("sublimation.stdlib", fakeRoot.absolutePath)
            val (_, sd) = compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            assertTrue(sd.diags.any { it.severity == Severity.ERROR && it.code == "E-MOUNT-MISSING" },
                "缺件须 E-MOUNT-MISSING（挂载期红并入主 envelope）: " + sd.diags.map { it.code })
        } finally {
            if (prev == null) System.clearProperty("sublimation.stdlib") else System.setProperty("sublimation.stdlib", prev)
        }
    }

    /** P-7 门 6：用户同名声明优先于 stdlib 注入名（不落 E-DUP-DECL + 形状以用户为准）。 */
    @Test fun 用户同名优先不报dup() {
        val o = scriptStd("fun sum(x: Str): Nat = 42\nfun m(): Nat = sum(\"abc\")")
        assertEquals(emptyList<String>(), errs(o), "用户 sum 胜出零红（stdlib 注入不经 Collector——无 E-DUP-DECL）: " + errs(o))
        val o2 = scriptStd("fun sum(x: Str): Nat = 42\nfun m(): Nat { var xs = Cons(1, Nil) return sum(xs) }")
        val codes2 = errs(o2)
        assertTrue("E-TYPE-MISMATCH" in codes2.map { it.substringBefore(":") },
            "用户 sum 形状（Str）覆盖 stdlib 注入口径——实参不配型须红: $codes2")
    }

    /** 条目 parity 7：stdlib 快照条目 = retire 表三项（range/sum ← list；summation ← math）。 */
    @Test fun 快照条目parity() {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
        assertFalse(sd.hasError)
        val byAlias = su.associateBy { it.alias }
        // B0-1 后 = 五件 + math（B0-0 阶段断言不可硬编小集——跟随实集）
        assertEquals(setOf("list", "optional", "result", "set", "map", "math"), byAlias.keys, "stdlib 件 = 五件 + math")
        // B0-1 后 list = 全家（B0-0 阶段断言不可硬编小集——retire 三项须在内）
        val listFuns = byAlias["list"]?.snapshot?.funs?.keys ?: emptySet()
        assertTrue(setOf("range", "sum").all { it in listFuns }, "list 件含 retire 的 range/sum")
        assertTrue(setOf("head", "slice", "toSet", "firstOr").all { it in listFuns }, "list 件含 B0-1 全家")
        assertEquals(setOf("summation"), byAlias["math"]?.snapshot?.funs?.keys, "math 件 = retire 的 summation")
        assertNotNull(byAlias["list"]?.snapshot?.exts?.get("List")?.get("fold"), "list 件 fold 扩展入快照 ext 面")
    }

    /** 确定性 8：stdlib 件双编译产物逐字节同（轮次确定性 + nid 纪律延续）。 */
    @Test fun stdlib双编译逐字节同() {
        val (a, _) = compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
        val (b, _) = compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
        assertEquals(a.size, b.size)
        a.zip(b).forEachIndexed { i, (x, y) ->
            assertEquals(x.unit.output.json, y.unit.output.json, "stdlib 件 $i 双编译逐字节同")
            assertEquals(x.snapshot.funs.keys, y.snapshot.funs.keys, "快照条目双编译同")
        }
    }

    /** 目录形态 9：编译根挂载并表——用户挂载与 stdlib 挂载共存（别名冲突用户优先）。 */
    @Test fun 目录形态stdlib并入挂载表() {
        val r = createTempDir("t137-dir")
        try {
            java.io.File(r, "主.subl").writeText("fun m(): Nat = summation(1, 11)\n", Charsets.UTF_8)
            val out = compileModuleFrontend(r, scriptMode = false, noDegrade = false, noLift = false)
            assertTrue(out.ok, "目录形态主模块绿（stdlib 裸名注入）: " + out.main.diag.diags.map { it.code })
            val paths = out.children.map { it.unitPath }.toSet()
            assertTrue("list" in paths && "math" in paths, "stdlib 件并入挂载表（children 含 list/math）: $paths")
        } finally { r.deleteRecursively() }
    }
}
