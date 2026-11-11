package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t106（R-08 / R-10,lambda arity 按期望类型 + D-11 调用元数校验;裁决记录 §R-08 / §R-10）:
 *
 * - 期望已知（lambdaExpect 单通道:声明型直调实参位,含尾随 lambda）:
 *   零参 fn ⇒ 零参（`_` 用即未绑定）;单参 fn ⇒ 一参 lambda、参数可不用（Kotlin 对齐）;
 *   多参 fn ⇒ 无箭头形态不可,须写显式参数表。
 * - 期望未知:工程模式严格拒 `_`（补法 2,Kotlin 同款）/ 脚本模式 `_` 用法自定单参（补法 1,
 *   唯一超出 Kotlin 的宽松）。模式判据 = 顶层语句存在（与 HirJson.unitMode 同源）。
 * - D-11:函数型实参的**元数一致性**——元数是结构信息,不受 synthetic 容忍位
 *   （reportMismatch 的未完成容忍）保护;0 参 call 打单参 lambda 等形态必落诊断。
 *
 * canonical 探针:frontend/probe-lambda/{zero,noarrow,used,inferred}.subl（队长 jar 落盘）。
 * 通道未达位态（方法糖 / 函数值调用 / var / 赋值 / return）按「无期望类型」走模式分立——
 * 通道扩展属 S2（推导机）,不进 t106。
 */
class T106R08LambdaTest {

    // D-12:模式真值由调用方透传（默认 project）;脚本模式用例显式传 scriptMode = true。
    private fun errCodes(src: String, file: String = "t106.subl", scriptMode: Boolean = false): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes, scriptMode)
        return pr.diag.filter { it.severity == Severity.ERROR }.map { it.code } +
            diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    }

    private val zargs = "fun applyZero(f: () => Nat): Nat = f()\n"
    private val onearg = "fun apply(f: (Nat) => Nat, x: Nat): Nat = f(x)\n"
    private val twoarg = "fun apply2(f: (Nat, Nat) => Nat): Nat = f(1, 2)\n"

    /** R-08 已知期望·零参 + 显式零参:今日不变（zero canonical）。 */
    @Test fun 期望零参显式零参不变() {
        val src = zargs + "@root fun m(): Nat = applyZero({ => 7 })"
        assertEquals(emptyList(), errCodes(src), "显式零参 lambda + 零参期望:不变（zero canonical）")
    }

    /** R-08 已知期望·零参 + 无箭头无下划线:按零参接受（noarrow canonical;今日被当合成单参容忍放行,R-08 后真零参）。 */
    @Test fun 期望零参无箭头按零参() {
        val src = zargs + "@root fun m(): Nat = applyZero({ 7 })"
        assertEquals(emptyList(), errCodes(src), "无箭头 { 7 } 于零参期望位:按零参接受（noarrow canonical）")
    }

    /** R-08 负例监管目标:零参期望位用 `_` 必红（used canonical——今日放行,R-08 后未绑定）。 */
    @Test fun 期望零参用下划线必红() {
        val src = zargs + "@root fun m(): Nat = applyZero({ _ + 1 })"
        val errs = errCodes(src)
        assertTrue(errs.any { it == "E-UNBOUND-NAME" }, "零参期望位 `_` 用即未绑定（used canonical）:$src")
    }

    /** R-08/R-10 已知期望·单参 + 无下划线:保持通过（参数可不用,Kotlin 对齐）。 */
    @Test fun 期望单参无下划线保持通过() {
        val src = onearg + "@root fun m(): Nat = apply({ 7 }, 4)"
        assertEquals(emptyList(), errCodes(src), "单参期望位 { 7 }:一参 lambda、参数可不用（R-10）")
    }

    /** R-08 已知期望·单参 + `_`:不变（apply canonical）。 */
    @Test fun 期望单参下划线不变() {
        val src = onearg + "@root fun m(): Nat = apply({ _ * 3 }, 4)"
        assertEquals(emptyList(), errCodes(src), "单参期望位 { _ * 3 }:`_` 启用（apply canonical）")
    }

    /** R-08 已知期望·多参:无箭头形态不可,须写显式参数表（今日 synthetic 容忍放行——负例）。 */
    @Test fun 期望多参无箭头必拒() {
        val src = twoarg + "@root fun m(): Nat = apply2({ 7 })"
        val errs = errCodes(src)
        assertTrue(errs.any { it == "E-ARITY" }, "多参期望位无箭头形态:须写显式参数表:$src")
    }

    /** R-08 未知期望·工程模式:`_` 报错,须写 { x => … }（补法 2, Kotlin 同款;今日放行——负例）。 */
    @Test fun 工程模式无期望下划线必拒() {
        val src = "@impure fun m(): Nat { var f = { _ * 3 }; return f(4) }"
        val errs = errCodes(src)
        assertTrue(errs.any { it == "E-IMPLICIT-IT" }, "工程模式无期望位 `_` 不可用,须写 { x => … }（补法 2）:$src")
    }

    /** R-08 未知期望·工程模式:无 `_` 的无箭头 = 默认零参（今日被当合成单参并致 f() 缺实参——负例）。 */
    @Test fun 工程模式无期望零参体合法() {
        val src = "@impure fun m(): Nat { var f = { 7 }; return f() }"
        assertEquals(emptyList(), errCodes(src), "工程模式 { 7 } 无期望:默认零参,f() 合法")
    }

    /** R-08 未知期望·脚本模式:`_` 用法自定单参,类型由用法推（补法 1;inferred canonical,今日同形）。 */
    @Test fun 脚本模式用法定参() {
        val src = "var f = { _ * 3 }\nvar r = f(4)"
        assertEquals(emptyList(), errCodes(src, scriptMode = true),
            "脚本模式无期望位:用法自定单参（D-12 后:模式真值由调用方显式传入）")
    }

    /** D-11:函数型实参元数一致性——显式单参 lambda 贴零参形参位必落诊断（今日 synthetic 容忍放行——负例）。 */
    @Test fun D11显式单参贴零参形参必拒() {
        val src = zargs + "@root fun m(): Nat = applyZero({ x => x + 1 })"
        val errs = errCodes(src)
        assertTrue(errs.any { it == "E-ARITY" }, "D-11:1 参 lambda 贴 0 参形参位:元数不符必落诊断:$src")
    }
}
