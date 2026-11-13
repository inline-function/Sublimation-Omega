package sublimation.frontend.semantic

import sublimation.frontend.cli.compileStdlibUnits
import sublimation.frontend.diag.Severity
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.parser.parseSourceWithNotes
import sublimation.frontend.semantic.compileWithRounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * t151（效应分析实现 / R-26.4 同域修改=纯 + R-28 矩阵口径）：
 *
 * - **S1 接收者来源溯源**：帧序比较（SymbolTable.bindingInCurrentFun——零 schema，OQ-1）+ 局部来源登记
 *   （localOriginIds：复制格代入 / 构造点 / 字面量 / 同域别名一层回溯，R3）;
 * - **S2 E-IMPURE-REQUIRED 判据收窄**：局部声明域接收者（同域修改=纯）放行;外域（借入形参 / 祖先帧
 *   捕获 / 全局别名 / 含糊）维持拦截——保守地板 R5;
 * - **判别性样本**：@mut var xs = mutableListOf(1); xs.add(2) 在纯函数体内翻绿（t141 现状误拦）;
 * - **负例面**：借入形参 / 祖先帧捕获 皆红（宁可误拦不漏放，R5）。
 *
 * 形参通道一律按外域（R5 保守地板）：callee 无法证明调用方不 `&` 借用（R2 借入歧义）——
 * 借用不绕过纯度门（t128 既有样本口径延续）。@ref 注解位不折入类型（annotations=["ref"]，
 * HIR 型仍 @mut T）——别名写经 MUT→MUT 复制格（R-28）⇒ 局部;可写借入唯经形参 @mut want（保守地板拦面）。 */
class T151EffectOriginTest {

    // 与 T126 同构：编译挂 stdlib 快照（构造族 + add 在快照内）
    private val stdlibMounts: Map<String, ModuleSnapshot> =
        compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            .let { (u, _) -> u.associate { it.alias to it.snapshot } }

    private fun pc(src: String) =
        parseSourceWithNotes(src, "t151.subl").let { compileWithRounds(it.file, it.notes, scriptMode = false, mounted = stdlibMounts) }
    private fun errs(rs: RoundsReport): List<String> =
        rs.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    // -------------------- 判别性样本：局部 = 纯（翻绿面） --------------------

    /** R-26.4 判别性：局部构造族接收者在纯函数体内调用 @mut 方法 ⇒ 翻绿（t141 现状误拦）。 */
    @Test fun 局部构造族接收者翻绿() {
        val rs = pc(
            "fun f(): Nat {\n  var xs : @mut List[Nat] = mutableListOf(1)\n  xs.add(2)\n  return 0\n}\n" +
            "@impure fun main(): Null { f(); print(1) }\n")
        assertEquals(emptyList(), errs(rs), "同域修改=纯（局部构造族接收者免 E-IMPURE-REQUIRED）: ${errs(rs)}")
    }

    /** 复制格代入（R-28 MUT→MUT）——深拷产生新对象 ⇒ 源局部 ⇒ 翻绿。 */
    @Test fun 复制格代入局部翻绿() {
        val rs = pc(
            "fun f(): Nat {\n  var a : @mut List[Nat] = mutableListOf(1)\n  var u : @mut List[Nat] = a\n  u.add(3)\n  return 0\n}\n" +
            "@impure fun main(): Null { f(); print(1) }\n")
        assertEquals(emptyList(), errs(rs), "复制格代入来源局部（R-28 深拷新对象）: ${errs(rs)}")
    }

    /** @ref 注解位 + @mut 类型 = MUT→MUT 复制格（@ref 不折入类型）⇒ 局部 ⇒ 翻绿。 */
    @Test fun ref注解var复制格翻绿() {
        val rs = pc(
            "fun f(): Nat {\n  var a : @mut List[Nat] = mutableListOf(1)\n  @ref var v : @mut List[Nat] = a\n  v.add(3)\n  return 0\n}\n" +
            "@impure fun main(): Null { f(); print(1) }\n")
        assertEquals(emptyList(), errs(rs), "@ref 注解位=MUT→MUT 复制格（局部新对象）: ${errs(rs)}")
    }

    // -------------------- 负例面：红（保守地板 R5） --------------------

    /** 负例 1 借入形参：形参通道（含 @mut want 值参）一律按外域——callee 无法证明调用方不 `&` 借用（R2/R5）。 */
    @Test fun 借入形参维持拦() {
        val rs = pc(
            "fun f(ys : @mut List[Nat]): Null {\n  ys.add(2)\n}\n" +
            "@impure fun main(): Null { var v : @mut List[Nat] = mutableListOf(1); f(v); print(1) }\n")
        assertTrue(errs(rs).contains("E-IMPURE-REQUIRED"), "形参通道=外域维持拦: ${errs(rs)}")
    }

    /** 负例 2 祖先帧捕获：闭包内调用经捕获的外层 @mut var ⇒ 维持拦（E-MUT-CAPTURE 同判据互补）。 */
    @Test fun 祖先帧捕获维持拦() {
        val rs = pc(
            "fun outer(): Nat {\n  @mut var xs = mutableListOf(1)\n  fun inner(): Nat { xs.add(2); return 0 }\n  return inner()\n}\n" +
            "@impure fun main(): Null { outer(); print(1) }\n")
        assertTrue(errs(rs).any { it == "E-MUT-CAPTURE" || it == "E-IMPURE-REQUIRED" }, "祖先帧捕获=外域维持拦: ${errs(rs)}")
    }

    /** 负例 3 保守地板交界：类型位 @ref @mut（REFMUT 视图）接收位调 add ⇒ R-28 REF→MUT 拒格
     *  ——可写借入不经类型位 @ref 通道;唯经形参 @mut want（负例 1 拦面）。 */
    @Test fun refmut类型位接收位拒格() {
        val rs = pc(
            "fun f(ys : @mut List[Nat]): Null {\n  var w : @ref @mut List[Nat] = ys\n  w.add(2)\n}\n" +
            "@impure fun main(): Null { var v : @mut List[Nat] = mutableListOf(1); f(v); print(1) }\n")
        assertTrue(errs(rs).contains("E-TYPE-MISMATCH"), "REFMUT 接收位按 R-28 拒格: ${errs(rs)}")
    }

    /** @impure 与 unchecked 两路放行不变（既有口径零回归）。 */
    @Test fun 两路放行零回归() {
        val i = pc("@impure fun m(): Null { var xs : @mut List[Nat] = mutableListOf(1); xs.add(2); print(xs) }\n")
        assertEquals(emptyList(), errs(i), "@impure 调用方放行: ${errs(i)}")
        val u = pc("fun g(): Nat { var xs : @mut List[Nat] = mutableListOf(1); unchecked xs.add(2); return 0 }\n@impure fun main(): Null { g(); print(1) }\n")
        assertEquals(emptyList(), errs(u), "unchecked 单语句豁免: ${errs(u)}")
    }
}
