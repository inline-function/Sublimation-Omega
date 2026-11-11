package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t107（R-07 闸增强,裁决记录 §R-07）:捕获的自由变量其类型含 @mut 且被访问（读或写）⇒ 该函数不纯。
 *
 * - 判据 = **类型含 @mut 即可变**（@ref @mut 同计——@ref 只是视图,背后可经别名变;R-07c）。
 * - 任何访问都算依赖:读 = 依赖可变外部态;写 = 本就是变化效应（R-07b 仅读也算）。
 * - 双模式同效;**不纯上下文内无需管**（@impure 体内的捕获不作分析——既有 t96 口径）。
 * - 传播:named 函数（全局预扫描 / 局部 fun 登记回写）走既有 t96 链,调用方在纯作用域内调它即落
 *   E-IMPURE-FUN-CALL;闭包本体在其作用域推入时翻不纯（体内调用闸放开）。
 * - 捕获量的类型**不含** @mut（如 var n: Nat）则保持纯——类型判据,与可变性注解位无关。
 *
 * 工程模式无顶层 var（Parser:201）:捕获形态落在函数体内（外层局部 / 形参被内层闭包捕获）,
 * 本测试一律工程模式（无顶层语句,避开 D-12 模式真值缺陷待裁位）。
 */
class T107R07CaptureTest {

    private fun errCodes(src: String, file: String = "t107.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.filter { it.severity == Severity.ERROR }.map { it.code } +
            diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    }

    /** R-07 核心:纯函数内的 lambda 捕获外层 @mut 局部并读 ⇒ E-MUT-CAPTURE（今日放行——负例先红）。 */
    @Test fun 纯函数lambda捕获可变局部必拒() {
        val src = "fun counter(): Nat {\n" +
            "    var n: @mut Nat = 0\n" +
            "    var step = { n + 1 }\n" +
            "    return step()\n" +
            "}"
        val errs = errCodes(src)
        assertTrue(errs.any { it == "E-MUT-CAPTURE" }, "lambda 捕获外层 @mut 局部 n 并访问:$src")
    }

    /** R-07b:仅读也算依赖——捕获后只在纯表达式里读同样落诊断。 */
    @Test fun 仅读捕获也算依赖() {
        val src = "fun peek(): Nat {\n" +
            "    var n: @mut Nat = 0\n" +
            "    var probe = { n }\n" +
            "    return probe()\n" +
            "}"
        assertTrue(errCodes(src).any { it == "E-MUT-CAPTURE" }, "仅读捕获同样计依赖:$src")
    }

    /** 判据是类型含 @mut:捕获不可变局部（var n: Nat）保持纯。 */
    @Test fun 捕获不可变局部保持纯() {
        val src = "fun pureStep(): Nat {\n" +
            "    var n: Nat = 0\n" +
            "    var step = { n + 1 }\n" +
            "    return step()\n" +
            "}"
        assertEquals(emptyList(), errCodes(src), "var n: Nat 不可变（无 @mut）:捕获保持纯")
    }

    /** 捕获写位（赋值给捕获量）同样落 E-MUT-CAPTURE。 */
    @Test fun 捕获写位必拒() {
        val src = "fun bump(): Nat {\n" +
            "    var n: @mut Nat = 0\n" +
            "    var step = { n = n + 1; n }\n" +
            "    return step()\n" +
            "}"
        assertTrue(errCodes(src).any { it == "E-MUT-CAPTURE" }, "对捕获量赋值=R-07 访问（写）:$src")
    }

    /** R-07c:@ref @mut 同计——捕获 @ref @mut 视图同样落诊断。 */
    @Test fun refMut视图同计() {
        val src = "fun viaRef(): Nat {\n" +
            "    var n: @mut Nat = 0\n" +
            "    var r: @ref @mut Nat = n\n" +
            "    var step = { r + 1 }\n" +
            "    return step()\n" +
            "}"
        val errs = errCodes(src)
        assertTrue(errs.any { it == "E-MUT-CAPTURE" }, "捕获 @ref @mut 视图同计:$src")
    }

    /** 纯函数内的局部 fun 捕获 @mut 局部:自身落 E-MUT-CAPTURE,且调用方走 t96 链落 E-IMPURE-FUN-CALL。 */
    @Test fun 局部fun捕获可变局部必拒() {
        val src = "fun host(): Nat {\n" +
            "    var n: @mut Nat = 0\n" +
            "    fun step(): Nat = n + 1\n" +
            "    return step()\n" +
            "}"
        val errs = errCodes(src)
        assertTrue(errs.any { it == "E-MUT-CAPTURE" }, "局部 fun 捕获 @mut 局部:$src")
    }

    /** 不纯上下文内无需管:@impure 函数体内的捕获不作分析（既有 t96 口径）。 */
    @Test fun 不纯上下文内不分析() {
        val src = "@impure fun main(): Nat {\n" +
            "    var n: @mut Nat = 0\n" +
            "    var step = { n + 1 }\n" +
            "    return step()\n" +
            "}"
        assertEquals(emptyList(), errCodes(src), "@impure 体内捕获不闸（e2e 样本唯一形态,零暴露基线）")
    }

    /** 外层函数自身的局部访问不是捕获（n 是 counter 的局部,非自由变量）。 */
    @Test fun 自身局部非捕获() {
        val src = "fun self(): Nat {\n" +
            "    var n: @mut Nat = 0\n" +
            "    n = n + 1\n" +
            "    return n\n" +
            "}"
        assertEquals(emptyList(), errCodes(src), "函数访问自己的局部 @mut:不是捕获,R-07 不适用")
    }

    /** 捕获外层不可变形参（param: Nat）保持纯（类型不含 @mut）。 */
    @Test fun 捕获不可变形参保持纯() {
        val src = "fun adder(x: Nat): Nat {\n" +
            "    var step = { x + 1 }\n" +
            "    return step()\n" +
            "}"
        assertEquals(emptyList(), errCodes(src), "形参 x: Nat 不可变:捕获保持纯")
    }

    /** 捕获外层**可变类型形参**（@mut 形参）⇒ E-MUT-CAPTURE（R-07 对形参捕获同判）。 */
    @Test fun 捕获可变形参必拒() {
        val src = "fun watch(x: @mut Nat): Nat {\n" +
            "    var step = { x + 1 }\n" +
            "    return step()\n" +
            "}"
        assertTrue(errCodes(src).any { it == "E-MUT-CAPTURE" }, "捕获 @mut 形参同判:$src")
    }

    /** 嵌套 lambda:内层捕获归内层自身（外层不替内层背锅——创建闭包≠其捕获的效应）。 */
    @Test fun 嵌套lambda各归自身() {
        val src = "fun nest(): Nat {\n" +
            "    var n: @mut Nat = 0\n" +
            "    var m: Nat = 0\n" +
            "    var outer = { m + 1 }\n" +
            "    return outer()\n" +
            "}"
        val errs = errCodes(src)
        assertFalse(errs.any { it == "E-MUT-CAPTURE" }, "外层 lambda 只捕获不可变 m:不落诊断:$src")
    }

    /** 全局具名纯函数的调用方在纯作用域内调它:t96 传播链落 E-IMPURE-FUN-CALL。 */
    @Test fun 全局捕获函数调用方传播() {
        val src = "fun stash(): Nat {\n" +
            "    var n: @mut Nat = 0\n" +
            "    fun snapshot(): Nat = n\n" +
            "    return snapshot()\n" +
            "}"
        val errs = errCodes(src)
        assertTrue(errs.any { it == "E-MUT-CAPTURE" }, "局部 fun 捕获 + 调用方传播:$src")
    }
}
