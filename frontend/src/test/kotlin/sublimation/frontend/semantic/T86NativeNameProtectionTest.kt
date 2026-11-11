package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t86（qa 收口）：原生命题名保护**下沉到所有引入名字的位**（F6）+ 值位出现原生命题名的导向（F2）。
 *
 * ## F6：保护从声明位下沉到绑定位
 * - 背景（qa 测绘）：`fun ∧` / `var ∧` 已落 E-NATIVE-REDECL（声明位守卫），但**形参 / 字段 / 局部 /
 *   lambda 形参 / 模式绑定 / 解构**都能占用原生命题名——它们是**编译器内建符号**而非普通标识符，
 *   允许局部遮蔽会让 `fun f(∧: Bool): Null <∧ ∧ p>` 产生「一个 ∧ 是变量、另一个是合取」的读法，
 *   诊断却只有 H-SHADOW 级。
 * - 落地：SymbolTable 的声明通道统一拦（declareLocal / declareScriptTopVar / declareFun /
 *   declareCtor / declareGlobal / 扩展函数通道）——复用 E-NATIVE-REDECL，文案按位置分化
 *   （声明位「不可重定义」/ 其余绑定位「不可用作变量名」），**不新增诊断码**（表已够长）。
 * - 只拦不阻断（半成品树原则）；成本为零（全仓 0 处如此使用）。
 *
 * ## F2：值位导向
 * - `val x = ∧` / `∧<p, q>` 原先只落 E-UNBOUND-NAME 而无导向——现在告诉用户命题连接词只能出现
 *   在命题位、值位的布尔与/或写作 `&` / `|`（推荐写法本身可编译，ADR-48 §八）。
 * - `∧(p, q)` 的值位**调用形态回落保持不变**（既有语义，实参类型由类型检查负责）。
 */
class T86NativeNameProtectionTest {

    private fun errCodes(src: String, file: String = "t86.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.filter { it.severity == Severity.ERROR }.map { it.code } +
            diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    }

    private fun allDiags(src: String, file: String = "t86.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.map { it.code + " :: " + it.message } +
            diag.diags.map { it.code + " :: " + it.message }
    }

    // ---- F6：绑定位保护（「不可用作变量名」） ----

    private val and = "∧"
    private val or  = "∨"
    private val imp = "→"
    private val iff = "↔"

    /** 验收 #1：形参占用原生命题名。 */
    @Test fun f6_形参占用原生命题名() {
        val src = "fun f($and: Int): Int = 1"
        assertTrue(errCodes(src).contains("E-NATIVE-REDECL"), "形参 ∧ 落 E-NATIVE-REDECL：" + errCodes(src))
        assertTrue(allDiags(src).any { it.contains("不可用作变量名") }, "文案含「不可用作变量名」：" + allDiags(src))
    }

    /** 验收 #2：struct 字段占用原生命题名。 */
    @Test fun f6_字段占用原生命题名() {
        val src = "struct S($and: Int)"
        assertTrue(errCodes(src).contains("E-NATIVE-REDECL"), "字段 ∧ 落 E-NATIVE-REDECL：" + errCodes(src))
        assertTrue(allDiags(src).any { it.contains("不可用作变量名") }, "文案含「不可用作变量名」：" + allDiags(src))
    }

    /** lambda 形参占用原生命题名（声明通道同一守卫）。 */
    @Test fun f6_lambda形参占用原生命题名() {
        val src = "fun g(h: (Int) => Int): Int = 1\n@root fun f(): Int = g({ $and => 1 })"
        assertTrue(errCodes(src).contains("E-NATIVE-REDECL"), "lambda 形参 ∧ 落 E-NATIVE-REDECL：" + errCodes(src))
        assertTrue(allDiags(src).any { it.contains("不可用作变量名") }, "文案含「不可用作变量名」：" + allDiags(src))
    }

    /** 模式绑定（构造子分量）占用原生命题名。 */
    @Test fun f6_模式绑定占用原生命题名() {
        val src = "fun f(o: Optional[Nat]): Nat = when (o) { is Some($and) -> 1; is None -> 2 }"
        assertTrue(errCodes(src).contains("E-NATIVE-REDECL"), "模式绑定 ∧ 落 E-NATIVE-REDECL：" + errCodes(src))
        assertTrue(allDiags(src).any { it.contains("不可用作变量名") }, "文案含「不可用作变量名」：" + allDiags(src))
    }

    /** 解构绑定分量占用原生命题名。 */
    @Test fun f6_解构绑定占用原生命题名() {
        val src = "var (a, $and) = (1, 2)"
        assertTrue(errCodes(src).contains("E-NATIVE-REDECL"), "解构分量 ∧ 落 E-NATIVE-REDECL：" + errCodes(src))
    }

    /** 局部 var（块内）占原生命题名——仍走声明位文案「不可重定义」（既有行为保持）。 */
    @Test fun f6_块内var保持声明位文案() {
        val src = "fun f() { var $and = 1 }"
        assertTrue(errCodes(src).contains("E-NATIVE-REDECL"), "块内 var ∧ 落 E-NATIVE-REDECL：" + errCodes(src))
        assertTrue(allDiags(src).any { it.contains("不可重定义") }, "声明位文案保持「不可重定义」：" + allDiags(src))
    }

    /** fun 声明位既有行为（P4 #7 / P5 #8 的回归哨兵）：码与文案均不变。 */
    @Test fun f6_fun声明位既有行为不变() {
        val src = "fun $and(p: Bool, q: Bool): Null = p"
        assertTrue(errCodes(src).contains("E-NATIVE-REDECL"), "fun ∧ 落 E-NATIVE-REDECL：" + errCodes(src))
        assertTrue(allDiags(src).any { it.contains("不可重定义") }, "文案保持「不可重定义」：" + allDiags(src))
    }

    /** 三个连接词轮一遍（表驱动）。 */
    @Test fun f6_三个连接词同样拒绝() {
        for (nm in listOf(and, or, imp)) {
            val src = "fun f($nm: Int): Int = 1"
            assertTrue(errCodes(src).contains("E-NATIVE-REDECL"), "形参 $nm 落 E-NATIVE-REDECL：" + errCodes(src))
        }
    }

    /** 验收 #3：命题位的 ∧ 不受保护影响——照旧通过。 */
    @Test fun f6_命题位不受影响() {
        val src = "fun f[p, q]: Null <p $and q>"
        val codes = errCodes(src)
        assertTrue(codes.none { it.startsWith("E") }, "命题位 ∧ 零错误：" + codes)
        assertTrue(allDiags(src).none { it.contains("E-NATIVE-REDECL") }, "无 E-NATIVE-REDECL：" + allDiags(src))
    }

    // ---- F2：值位导向 ----

    /** 验收 #4：值位裸命题连接词——E-UNBOUND-NAME + 值位写法导向。 */
    @Test fun f2_值位裸符号导向() {
        val src = "var x = $and"
        val codes = errCodes(src)
        assertTrue(codes.contains("E-UNBOUND-NAME"), "值位 ∧ 落 E-UNBOUND-NAME：" + codes)
        assertTrue(allDiags(src).any { it.contains("值位的布尔与写作 `&`") }, "提示值位 `&`：" + allDiags(src))
    }

    /** 验收 #5：值位角表形态 `∧<p, q>`——同一导向。 */
    @Test fun f2_值位角表导向() {
        val src = "var x = $and<True, False>"
        val codes = errCodes(src)
        assertTrue(codes.contains("E-UNBOUND-NAME"), "角表形态 ∧ 落 E-UNBOUND-NAME：" + codes)
        assertTrue(allDiags(src).any { it.contains("值位的布尔与写作 `&`") }, "提示值位 `&`：" + allDiags(src))
    }

    /** ∨ / → / ↔ 的导向各自正确（或给 `|`；蕴含/等价只定位）。 */
    @Test fun f2_或蕴含等价的导向() {
        val or_x = "var x = $or"
        assertTrue(allDiags(or_x).any { it.contains("值位的布尔或写作 `|`") }, "∨ 提示 `|`：" + allDiags(or_x))
        for (nm in listOf(imp, iff)) {
            val src = "var x = $nm"
            assertTrue(allDiags(src).any { it.contains("只能出现在命题位") }, "$nm 定位提示：" + allDiags(src))
        }
    }

    /** 导向的推荐写法本身可编译（ADR-48 §八）——`True & False` 值位通过。 */
    @Test fun f2_推荐的值位写法可编译() {
        val src = "var x: Bool = True & False"
        assertTrue(errCodes(src).none { it.startsWith("E") }, "True & False 零错误：" + errCodes(src))
    }

    /**
     * t92（裁决 4 表达式层闭合）强制改向：值位的 `∧(True, False)` 调用形态**不再回落** BinExpr——
     * 与中缀形态同码 E-PROP-FORM 并给值位写法提示。
     * BEFORE(t74/t86):qa 实测回落（isBuiltinOp ∧ propCtors-2 的条件交集恰为 ∧∨→↔ 走 BinExpr 通道）。
     * AFTER(t92):值位拒绝——表达式层一律 `&`（提示即推荐写法本身可编译，ADR-48 §八）。
     */
    @Test fun f2_值位调用形态拒绝() {
        val src = "var x = $and(True, False)"
        val codes = errCodes(src)
        assertTrue(codes.contains("E-PROP-FORM"), "调用形态落 E-PROP-FORM（不再回落）：" + codes)
        assertTrue(allDiags(src).any { it.contains("写作 `&`") }, "提示值位 `&`：" + allDiags(src))
    }
}
