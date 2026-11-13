
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
 * t154（诊断扩批三 / D 级效应告示 ×2）：效应分析结论的 D 级告示（t151 origin 追踪可依）——
 * - D-EFFECT-LOCAL：@mut 方法调用的接收者经来源判定为**局部**（构造点/复制格代入/@ref 注解位保守口径，R-26.4）
 *   ⇒ 告示「写入局限本域、未逃逸」——纯函数局部修改的正面告示（同域修改=纯判据的放行留痕）。
 * - D-EFFECT-ESCAPE：@mut 方法调用的接收者为**外域**（借入形参/祖先帧捕获/全局别名/含糊——R5 保守地板）
 *   ⇒ 告示「效应逃逸路径」（R-21 放行不封，告示代替;与 W-REF-ESCAPE 互补：引用逃逸 vs 效应逃逸）。
 *
 * 纪律：口径跟 t151 现行判定（形参一律外域保守地板、@ref 注解位不折入类型）——**只告示不变判定**
 * （mutMethodCalls 登记条件逐字同 t151：外域才入 @mut 方法闸）;D 级一律 supplement 不拦;零分配 nid
 * （复用既有 receiverOriginIsLocal 判定 + 接收者形态分型，无新类型求值）;负例先红（实现前二码实测零落）。 */
class T154DiagBatchThreeTest {

    private val stdlibMounts: Map<String, ModuleSnapshot> =
        compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            .let { (u, _) -> u.associate { it.alias to it.snapshot } }

    private fun sc(src: String) =
        parseScriptSourceWithNotes(src, "t154.subs").let { compileWithRounds(it.file, it.notes, scriptMode = true, mounted = stdlibMounts) }

    private fun supps(rs: RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.SUPPLEMENT }.map { it.code }
    private fun suppMsgs(rs: RoundsReport, code: String): List<String> =
        rs.diag.diags.filter { it.severity == Severity.SUPPLEMENT && it.code == code }.map { it.message }
    private fun errs(rs: RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    // -------------------- D-EFFECT-LOCAL --------------------

    /** 脚本顶层局部来源接收者写（判别性样本面）：var xs = mutableListOf(1); xs.add(2) —— 局限本域、未逃逸。 */
    @Test fun localTopLevel() {
        val rs = sc("var xs = mutableListOf(1)\nxs.add(2)\nprint(xs)\n")
        assertEquals(emptyList(), errs(rs), "局部写合法编译（D 级不拦）: ${errs(rs)}")
        assertTrue(supps(rs).contains("D-EFFECT-LOCAL"), "局部来源接收者写须落 D-EFFECT-LOCAL: ${supps(rs)}")
        assertFalse(supps(rs).contains("D-EFFECT-ESCAPE"), "局部来源不落逃逸告示: ${supps(rs)}")
    }

    /** 纯函数内局部修改（R-26.4 同域修改=纯——放行留痕）：无 E-IMPURE-REQUIRED + D-EFFECT-LOCAL。 */
    @Test fun localInPureFunction() {
        val rs = sc("fun g() : Nat {\n  var xs = mutableListOf(1)\n  xs.add(2)\n  return 1\n}\nprint(g())\n")
        assertEquals(emptyList(), errs(rs), "纯函数局部写合法编译: ${errs(rs)}")
        assertTrue(supps(rs).contains("D-EFFECT-LOCAL"), "纯函数局部写须落 D-EFFECT-LOCAL（放行留痕）: ${supps(rs)}")
    }

    /** 字段随属主回溯（R3 一层）：b.fld.add(2)——属主局部即局部。 */
    @Test fun localFieldOwner() {
        val rs = sc("@mut struct Box(@mut fld : @mut List[Nat])\n@impure fun main() : Null {\n  var b = Box(mutableListOf(1))\n  b.fld.add(2)\n  print(1)\n}\n")
        assertEquals(emptyList(), errs(rs), "字段接收者写合法编译: ${errs(rs)}")
        assertTrue(supps(rs).contains("D-EFFECT-LOCAL"), "属主局部的字段接收者写落 D-EFFECT-LOCAL: ${supps(rs)}")
    }

    // -------------------- D-EFFECT-ESCAPE --------------------

    /** 外域形参（R5 一律保守地板）：写效应经形参流出本函数。 */
    @Test fun escapeParam() {
        val rs = sc("@impure fun f(xs : @mut List[Nat]) : Null { xs.add(2) }\n@impure fun main() : Null { f(mutableListOf(1)) }\n")
        assertEquals(emptyList(), errs(rs), "外域形参写合法编译: ${errs(rs)}")
        assertTrue(supps(rs).contains("D-EFFECT-ESCAPE"), "外域形参写须落 D-EFFECT-ESCAPE: ${supps(rs)}")
        assertTrue(suppMsgs(rs, "D-EFFECT-ESCAPE").any { it.contains("外域形参") }, "逃逸路径须分型为外域形参: ${suppMsgs(rs, "D-EFFECT-ESCAPE")}")
    }

    /** 祖先帧捕获：inner 内 xs 为捕获变量——效应经捕获流出。 */
    @Test fun escapeCapture() {
        val rs = sc("@impure fun outer() : Null {\n  var xs = mutableListOf(1)\n  @impure fun inner() : Null { xs.add(2) }\n  inner()\n}\nouter()\n")
        assertEquals(emptyList(), errs(rs), "捕获写合法编译: ${errs(rs)}")
        assertTrue(supps(rs).contains("D-EFFECT-ESCAPE"), "捕获写须落 D-EFFECT-ESCAPE: ${supps(rs)}")
        assertTrue(suppMsgs(rs, "D-EFFECT-ESCAPE").any { it.contains("祖先帧捕获") }, "逃逸路径须分型为祖先帧捕获: ${suppMsgs(rs, "D-EFFECT-ESCAPE")}")
    }

    // -------------------- 保守豁免面（零误报防护） --------------------

    /** 纯方法（无 @mut 注解——map 无写效应）：零 D-EFFECT-* 告示。 */
    @Test fun pureMethodNoEffectNote() {
        val rs = sc("var xs = listOf(1)\nprint(xs.map { x => x + 1 })\n")
        assertEquals(emptyList(), errs(rs), "纯方法调用合法编译: ${errs(rs)}")
        assertFalse(supps(rs).contains("D-EFFECT-LOCAL"), "纯方法不须 D-EFFECT-LOCAL: ${supps(rs)}")
        assertFalse(supps(rs).contains("D-EFFECT-ESCAPE"), "纯方法不须 D-EFFECT-ESCAPE: ${supps(rs)}")
    }

    /** 局部/逃逸互斥：局部来源面只落 LOCAL，绝不落 ESCAPE（反之同——escapeParam 已覆）。 */
    @Test fun mutualExclusionLocalNeverEscape() {
        val rs = sc("var xs = mutableListOf(1)\nxs.add(2)\nprint(xs)\n")
        assertFalse(supps(rs).contains("D-EFFECT-ESCAPE"), "局部来源面绝不落逃逸告示: ${supps(rs)}")
    }
}
