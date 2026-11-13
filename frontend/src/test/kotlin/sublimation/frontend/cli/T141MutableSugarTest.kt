package sublimation.frontend.cli

import sublimation.frontend.diag.Severity
import sublimation.frontend.semantic.PreludeDecls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * t141（R-24 v2 / @mutable 注解）: 函数级 @mutable ≡ @mut @ref 接收者组合的纯糖 回归门。
 *
 * - **等价**：@mutable fun List[a].f[a](e: a) ≡ fun (@mut @ref List[a]).f[a](e: a)——
 *   提升位注解不入 HIR（hirAnns 剥 "mutable"）、接收者映 @mut @ref、两形态 HIR 逐字节同
 *   （pos/nid 剥——书写形态源位差，机械口径同 jsoncmp 剥差同）；
 * - **⇒ impure**：按引用改调用方状态（接收者 @mut 同源口径）——纯度闸拦纯域调用；
 * - **等价化**：self 是函数自带的可变形参（非外层捕获）——接收者 @mut/@mutable 形态
 *   声明口径 impure ⇒ E-MUT-CAPTURE 误伤消（纯函数的真捕获仍红——闸范围不放宽）；
 * - **H-MUT-RECEIVER**（H 家族提示·不拦）：仅接收者显式 @mut @ref 且未用 @mutable 时发；
 *   函数级零提示（@mutable 今天被静默忽略=零语义的状态结案）；
 * - **stdlib add 落体改函数级**：@mutable fun List[a].add[a](e: a): List[a]（体仍空=OQ-4 b
 *   签名面延续 / R-15 债不变）；调用方纯域闸口径同 @mut 方法糖（E-IMPURE-REQUIRED）。 */
class T141MutableSugarTest {

    private fun errs(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun hints(o: FrontendOutput): List<String> =
        o.diag.diags.filter { it.severity == Severity.HINT }.map { it.code }

    private fun scriptStd(src: String, scriptMode: Boolean = false): FrontendOutput {
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode, noDegrade = false, noLift = false)
        return compileFrontendImpl(src, "t141.subl", scriptMode, false, false,
            su.associate { it.alias to it.snapshot }, sd)
    }

    private fun stdlibUnits() = compileStdlibUnits(noStdlib = false, scriptMode = false, noDegrade = false, noLift = false)

    /** 剥空格后的子串核对（对紧凑/带空格两 JSON 排版同免疫）。 */
    private fun flat(o: FrontendOutput): String = o.json.replace(" ", "")

    /** hir 子树提取（花括号配对——顶层键 "hir" 的对象体）。 */
    private fun hirOf(json: String): String {
        val key = json.indexOf("\"hir\":")
        if (key < 0) return ""
        var i = json.indexOf('{', key)
        var depth = 0
        while (i < json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return json.substring(json.indexOf('{', key), i + 1) }
            }
            i++
        }
        return ""
    }

    /** 剥 pos/nid 键值（手卷扫描——书写形态的源位差，机械口径同 jsoncmp 剥差同）。 */
    private fun stripPosNid(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s.startsWith("\"pos\"", i) || s.startsWith("\"nid\"", i)) {
                val c = s.indexOf(',', i)
                i = if (c < 0) s.length else c + 1
            } else { sb.append(s[i]); i++ }
        }
        return sb.toString()
    }

    // -------------------- 门 1：两形态 HIR 逐字节同（等价性机器证明） --------------------

    /** @mutable 函数级与 @mut @ref 接收者形态：HIR 逐字节同（pos/nid 剥）——注解表/接收者/纯度同源。 */
    @Test fun 两形态HIR逐字节同() {
        val a = scriptStd("@mutable fun List[a].addx[a](e: a): List[a]\n@impure fun m(): Null { unchecked print(1) }")
        val b = scriptStd("fun (@mut @ref List[a]).addx[a](e: a): List[a]\n@impure fun m(): Null { unchecked print(1) }")
        assertEquals(emptyList<String>(), errs(a), "@mutable 形态编译绿: " + errs(a))
        assertEquals(emptyList<String>(), errs(b), "@mut @ref 接收者形态编译绿: " + errs(b))
        assertEquals(stripPosNid(hirOf(a.json)), stripPosNid(hirOf(b.json)),
            "两形态 HIR 逐字节同（pos/nid 剥）：提升位注解剥 + 接收者映 @mut @ref")
        assertFalse(flat(a).contains("\"annotations\":[\"mutable\"]"),
            "提升位注解剥——HIR 注解表无 \"mutable\"")
    }

    /** 提升位接收者形态核对（HirMutType(HirRefType(List))——@mut @ref 组合）+ ⇒ impure。 */
    @Test fun 提升形态接收者核对() {
        val o = scriptStd("@mutable fun List[a].addx[a](e: a): List[a]")
        assertEquals(emptyList<String>(), errs(o), "@mutable 扩展声明零错误: " + errs(o))
        assertTrue(flat(o).contains("\"kind\":\"HirMutType\""), "HIR 接收者外层 HirMutType（@mut）")
        assertTrue(flat(o).contains("\"kind\":\"HirRefType\""), "HIR 接收者内层 HirRefType（@ref）")
        assertTrue(flat(o).contains("\"purity\":\"impure\""), "@mutable ⇒ impure（decl purity 位）")
    }

    // -------------------- 门 2：⇒ impure（纯度闸拦截口径） --------------------

    /** @mutable 本地扩展在纯函数体调用 ⇒ 纯度闸拦（按引用改调用方状态；双闸码任一即红）。 */
    @Test fun mutable纯域调用红() {
        val o = scriptStd("@mutable fun List[a].addx[a](e: a): List[a]\n" +
            "fun f(): Null { var xs : @mut List[Nat] = mutableListOf(1); xs.addx(2); print(xs) }\n" +
            "@impure fun main(): Null { f() }")
        assertTrue(errs(o).any { it == "E-IMPURE-CALL" || it == "E-IMPURE-REQUIRED" },
            "纯域调用 @mutable 扩展须红（⇒ impure 闸口径）: " + errs(o))
    }

    /** @impure 调用方 + unchecked 两路放行绿（三路口径同纯度闸）。 */
    @Test fun mutable调用放行两路绿() {
        val impure = scriptStd("@mutable fun List[a].addx[a](e: a): List[a]\n" +
            "@impure fun go(): Null { var xs : @mut List[Nat] = mutableListOf(1); xs.addx(2); print(xs) }")
        assertEquals(emptyList<String>(), errs(impure), "@impure 调用方放行绿: " + errs(impure))
        val un = scriptStd("@mutable fun List[a].addx[a](e: a): List[a]\n" +
            "fun go(): Null { var xs : @mut List[Nat] = mutableListOf(1); unchecked xs.addx(2); unchecked print(xs) }")
        assertEquals(emptyList<String>(), errs(un), "unchecked 单语句豁免绿: " + errs(un))
    }

    // -------------------- 门 3：E-MUT-CAPTURE 误伤消（等价化） --------------------

    /** 接收者 @mut @ref + 体访 self 绿（今日误伤消——self 是函数自带可变形参，非外层捕获）。 */
    @Test fun 接收者mutref体访self绿() {
        val o = scriptStd("fun (@mut @ref List[a]).addx[a](e: a): List[a] = Cons(e, self)\n" +
            "@impure fun m(): Null { unchecked print(1) }")
        assertEquals(emptyList<String>(), errs(o), "@mut @ref 接收者形态体访 self 绿: " + errs(o))
    }

    /** @mutable 形态体访 self 绿（等价化同源）。 */
    @Test fun mutable形态体访self绿() {
        val o = scriptStd("@mutable fun List[a].addx[a](e: a): List[a] = Cons(e, self)\n" +
            "@impure fun m(): Null { unchecked print(1) }")
        assertEquals(emptyList<String>(), errs(o), "@mutable 体访 self 绿: " + errs(o))
    }

    /** 纯函数 lambda 捕获外层 @mut 局部仍红——闸范围不放宽（真捕获保留，判据逐字同旧）。 */
    @Test fun 纯函数真捕获仍红() {
        val o = scriptStd("fun peeky(): Nat {\n" +
            "    var n: @mut Nat = 0\n" +
            "    var step = { n + 1 }\n" +
            "    return step()\n" +
            "}\n@impure fun main(): Null { unchecked print(1) }")
        assertTrue(errs(o).any { it == "E-MUT-CAPTURE" }, "纯函数捕获外层 @mut 仍红（闸范围不放宽）: " + errs(o))
    }

    // -------------------- 门 4：H-MUT-RECEIVER 提示纪律 --------------------

    /** 接收者 @mut @ref 形态 → H-MUT-RECEIVER 提示发（建议提升 @mutable）。 */
    @Test fun HMUTRECEIVER接收者形态发() {
        val o = scriptStd("fun (@mut @ref List[a]).addx[a](e: a): List[a]")
        assertEquals(listOf("H-MUT-RECEIVER"), hints(o), "仅 H-MUT-RECEIVER 一条提示")
        assertEquals(emptyList<String>(), errs(o), "提示不拦（H 家族）")
    }

    /** 函数级 @mutable 零提示（已提升——无需再建议）。 */
    @Test fun mutable函数级零提示() {
        val o = scriptStd("@mutable fun List[a].addx[a](e: a): List[a]")
        assertEquals(emptyList<String>(), hints(o), "@mutable 函数级零提示")
    }

    /** @mut 无 @ref 接收者 / 无注解接收者：零提示（提示仅 @mut @ref 组合位）。 */
    @Test fun 提示边界mut无ref零提示() {
        val mutOnly = scriptStd("@mut fun (@mut List[a]).addx[a](e: a): List[a]")
        assertEquals(emptyList<String>(), hints(mutOnly), "@mut 接收者（无 @ref）零提示")
        val plain = scriptStd("fun List[a].addx[a](e: a): List[a]")
        assertEquals(emptyList<String>(), hints(plain), "纯接收者零提示")
    }

    // -------------------- 门 5：@mutable 注解识别（静默忽略结案） --------------------

    /** @mutable 无接收者（非扩展函数）：无映射——静默（零语义，边界同未知注解口径）。 */
    @Test fun mutable无接收者静默() {
        val o = scriptStd("@mutable fun go(): Nat { unchecked return 1 }")
        assertEquals(emptyList<String>(), errs(o), "@mutable 无接收者静默过: " + errs(o))
        assertFalse(flat(o).contains("\"purity\":\"impure\""),
            "无映射位不改纯度（@mutable 无接收者=零语义）")
    }

    // -------------------- 门 6：stdlib add 落体改函数级（调用面零差分） --------------------

    /** stdlib add 快照面：@mutable 函数级注解 + 接收者 List 头不变（糖渠道解析面零形态差）。 */
    @Test fun stdlibAdd快照形态() {
        val (su, diag) = stdlibUnits()
        assertFalse(diag.hasError, "stdlib 全件零红（@mutable 落体后）: " +
            diag.diags.filter { it.severity == Severity.ERROR }.joinToString { it.message })
        val d = su.filter { it.snapshot.stdlib }.first { it.alias == "list" }.snapshot.exts["List"]?.get("add")?.decl
        assertNotNull(d, "list 快照含 add 扩展声明")
        assertEquals(true, d.annotations.contains("mutable"), "add 持 @mutable 函数级注解")
        assertEquals(true, (d.receiver as? sublimation.frontend.ast.NamedType)?.name == "List",
            "快照声明接收者 List 头不变（提升在 HIR 化层）")
        assertEquals(true, d.body == null, "体仍空（OQ-4 b 签名面延续 / R-15 债不变）")
        assertEquals(0, PreludeDecls.EXT_METHODS.size, "EXT_METHODS 虚拟表 retire（空表不变）")
    }

    /** stdlib add 纯域调用走 @mut 方法糖闸（E-IMPURE-REQUIRED——R-24 v2 调用方口径同 @mut 形态）。 */
    @Test fun stdlibAdd纯域闸口径() {
        val o = scriptStd("fun f(): Null { var xs : @mut List[Nat] = mutableListOf(1); xs.add(2); print(xs) }\n" +
            "@impure fun main(): Null { f() }")
        assertTrue(errs(o).any { it == "E-IMPURE-REQUIRED" },
            "stdlib add 纯域调用走 @mut 方法糖闸（E-IMPURE-REQUIRED）: " + errs(o))
        val ok = scriptStd("@impure fun m(): Null { var xs : @mut List[Nat] = mutableListOf(1); xs.add(2); print(xs) }")
        assertEquals(emptyList<String>(), errs(ok), "@impure 调用方放行（stdlib add 等价口径）: " + errs(ok))
    }

    /** @mut 函数级语义不变（R-24 v2：@mut 保持纯度旗语义——非 @mutable 等价物）。 */
    @Test fun mut函数级语义不变() {
        val o = scriptStd("@mut fun List[a].addx[a](e: a): List[a]\n" +
            "@impure fun m(): Null { unchecked print(1) }")
        assertEquals(emptyList<String>(), errs(o), "@mut 函数级声明绿: " + errs(o))
        assertTrue(flat(o).contains("\"purity\":\"pure\""),
            "@mut 函数级保持纯度旗语义不变（⇒ 非 impure——与 @mutable 语义分离）")
    }
}
