package sublimation.frontend.module

import sublimation.frontend.cli.FrontendOutput
import sublimation.frontend.cli.compileModuleFrontend
import sublimation.frontend.diag.Severity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t157（B3 后续件·1 跨模块命题实例化面）：挂载模块 callee 的紧贴角表通道收口测套。
 *
 * - **修前现状（负例先红，CLI 探针实测）**：限定 callee（mod.f[...]()/mod.f<p>() 形）的角表
 *   两路皆不通——[] 的类型实参按**值域**读落 E-UNBOUND-NAME（Nat 未定义）双错;<> 落
 *   E-CALL-ANGLE 兜底「callee」（被调声明只查本模块 funDecls，快照 funs 面未接入）。
 * - **t157 收口**：InstExpr callee 的 target 为限定链时，经模块段链消解 + 挂载快照被调声明——
 *   方括号=类型实参按类型域读（sigCheckedCall 同本地方案）;尖括号须被调含 PropParam
 *   理论参数（R-26.1/t148 同口径——限定后同判据），否则 E-CALL-ANGLE 精确落位（hint 指回
 *   限定链末段函数名）;中段未命中单错落位（同限定调位口径，免双错同源）。
 * - 零用户影响：本通道只对限定 callee 的新形态生效——68 门单文件面逐字不变（t157parity 门）。 */
class T157ModuleAngleCallTest {

    private fun root(tag: String): File {
        val d = File("frontend/build/tmp/t157/" + tag)
        if (d.exists()) d.deleteRecursively()
        d.mkdirs()
        return d
    }

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    /** 挂载 mod（witness 谓词供给 + gid 类型实参 + np 无命题参数）的主体编译。 */
    private fun angleMain(tag: String, mainBody: String): FrontendOutput {
        val r = root(tag)
        File(r, ".settings").writeText("mount mod\n", Charsets.UTF_8)
        val mod = File(r, "mod"); mod.mkdirs()
        File(mod, "w.subl").writeText(
            "fun witness[@q]<q>: Null {}\n" +
            "fun gid[T](x: T): T { return x }\n" +
            "fun np(x: Nat): Nat { return x }\n", Charsets.UTF_8)
        File(r, "main.subl").writeText("fun main() : Null {\n  $mainBody\n  return null\n}\n", Charsets.UTF_8)
        return compileModuleFrontend(r).main
    }

    /** 门 1：限定 callee 方括号类型实参通道——mod.gid[Nat](0) 跨件绿（修前 E-UNBOUND-NAME 双错）。 */
    @Test fun 限定callee方括号类型实参绿() {
        val e = errs(angleMain("bracket", "var v = mod.gid[Nat]((0))"))
        assertEquals(emptyList<String>(), e, "mod.gid[Nat](0) 限定方括号类型实参绿: " + e)
    }

    /** 门 2：限定 callee 尖括号命题供给通道——mod.witness<谓词>() 跨件绿（R-27 模块态完整）。 */
    @Test fun 限定callee尖括号命题供给绿() {
        val e = errs(angleMain("angle", "mod.witness<forall n. even<n>>()"))
        assertEquals(emptyList<String>(), e, "mod.witness<forall n. even<n>>() 限定命题供给绿: " + e)
    }

    /** 门 3（反例）：被调无命题理论参数的尖括号——E-CALL-ANGLE 精确落位（hint 指回函数名）。 */
    @Test fun 无命题参数尖括号精确E级() {
        val e = errs(angleMain("angle-reject", "mod.np<0>()"))
        assertTrue("E-CALL-ANGLE" in e, "mod.np<0>() 须 E-CALL-ANGLE（np 无 PropParam）: " + e)
    }

    /** 门 4：限定链中段未命中——单错落位（免双错同源，同限定调位口径）。 */
    @Test fun 中段未命中单错落位() {
        val e = errs(angleMain("mid-miss", "mod.sub.gid[Nat]()"))
        assertEquals(listOf("E-UNBOUND-NAME"), e, "mod.sub.gid[Nat]() 单错 E-UNBOUND-NAME 落位: " + e)
    }
}
