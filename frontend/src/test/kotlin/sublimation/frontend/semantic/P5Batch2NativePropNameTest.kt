package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirBinExpr
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirPAnd
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirPIff
import sublimation.frontend.hir.HirPImp
import sublimation.frontend.hir.HirPOr
import sublimation.frontend.hir.HirProp
import sublimation.frontend.hir.HirTBin
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t74（P5 批次 2）：`∧ ∨ → ↔` 名字材料化 + 路由回归。
 *
 * ## 三条不变量（本测试锁死）
 * 1. **同产物**：命题位 `p ∧ q`（D-5 中缀糖 → CallExpr）与 `∧<p, q>`（角表 → InstExpr）→ 同一个原生节点；
 * 2. **结合性**：`→` 右结合 `a → b → c` = `a → (b → c)`（ADR-50 用户裁决 A/Q22:优先级落地，t82 前 → 随大流左结合）；
 * 3. **产物逐字不变**：值位 `True ∧ False` 仍走原 BinExpr 通道、项位 `→` 仍产 `HirTBin`
 *    （队长 jsoncmp：68 样本 HIR JSON 逐字比对 SAME=68 / DIFF=0）。
 */
class P5Batch2NativePropNameTest {

    private fun errCodes(src: String, file: String = "t74.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.filter { it.severity == Severity.ERROR }.map { it.code } +
            diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    }

    private fun allDiags(src: String, file: String = "t74.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.map { it.code + " :: " + it.message } + diag.diags.map { it.code + " :: " + it.message }
    }

    private fun funDecl(src: String, file: String = "t74.subl"): HirFunDecl {
        val pr = parseScriptSourceWithNotes(src, file)
        val (hir, _) = compile(pr.file, pr.notes)
        val decls = hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl }
        return decls.firstOrNull() as HirFunDecl
    }

    private fun posts(src: String): List<HirProp> = funDecl(src).posts

    /** #1：命题位 `p ∧ q` 归一为原生 HirPAnd（不是 HirPAtom("∧")——静默降级是本批次要防的 bug）。 */
    @Test fun n1_infixConjIsNativeAnd() {
        assertFalse(errCodes("fun f[p, q]: Null <p ∧ q>").any { it.startsWith("E") },
            "#1 p ∧ q 通过：" + allDiags("fun f[p, q]: Null <p ∧ q>"))
        val p = posts("fun f[p, q]: Null <p ∧ q>")[0]
        assertIs<HirPAnd>(p, "#1 命题位 ∧ 必须是 HirPAnd 而非 HirPAtom：$p")
    }

    /** #2：`p ∧ q` 与 `∧<p, q>` 同产物（P-STRUCT-EQ 口径：只比结构，节点编号/位置/留痕不参与）。 */
    @Test fun n2_infixAndAngleSameProduct() {
        val infix = posts("fun f[p, q]: Null <p ∧ q>")[0]
        val angle = posts("fun f[p, q]: Null <∧<p, q>>")[0]
        assertIs<HirPAnd>(angle, "#2 角表形态也是 HirPAnd：$angle")
        assertEquals(psub(infix), psub(angle), "#2 两写法结构全等")
    }

    /** #3：`∨` / `→` / `↔` 各自落对应原生节点。 */
    @Test fun n3_orImpIffNative() {
        assertIs<HirPOr>(posts("fun f[p, q]: Null <p ∨ q>")[0], "#3 ∨ ⇒ HirPOr")
        assertIs<HirPImp>(posts("fun f[p, q]: Null <p → q>")[0], "#3 → ⇒ HirPImp")
        assertIs<HirPIff>(posts("fun f[p, q]: Null <p ↔ q>")[0], "#3 ↔ ⇒ HirPIff")
    }

    /**
     * #4（t82/ADR-50 强制更改）：`→` 是**右结合**——`a → b → c` = `a → (b → c)`。
     * BEFORE(t74):断言左结合 `(a → b) → c`(当时全部中缀随大流左结合，t71 用户裁决口径）。
     * AFTER(t82/ADR-50 Q22):用户裁决 → 右结合 ⇒ 顶层右子树是内层 HirPImp。
     */
    @Test fun n4_impIsRightAssoc() {
        val p = posts("fun f[a, b, c]: Null <a → b → c>")[0]
        val outer = assertIs<HirPImp>(p, "#4 顶层是 HirPImp")
        val inner = assertIs<HirPImp>(outer.b, "#4 右子树必须是 HirPImp（→ 右结合）")
        assertEquals("b", head(inner.a), "#4 内层左子命题是 b")
        assertEquals("c", head(inner.b), "#4 内层右子命题是 c")
    }

    /** #4b：`a ∧ b ∧ c` = `(a ∧ b) ∧ c`。 */
    @Test fun n4b_conjIsLeftAssoc() {
        val p = posts("fun f[a, b, c]: Null <a ∧ b ∧ c>")[0]
        val outer = assertIs<HirPAnd>(p, "#4b 顶层 HirPAnd")
        assertIs<HirPAnd>(outer.a, "#4b 左子树 HirPAnd（左结合）")
    }

    /**
     * t92（裁决 4 表达式层闭合）强制改向：值位 `True ∧ False` **拒绝**——表达式层一律 `&`。
     * BEFORE(t74/t85/t86):值位 ∧ 走 t74 条件交集回落 BinExpr + Bool 签名（qa 测绘口径：HirBinExpr("∧")）。
     * AFTER(t92):回落移除——复用 E-PROP-FORM（与命题位 `&` 反向同码）+ 值位写法提示（可编译，ADR-48 §八）
     * + 半成品 HirErrorExpr。与 n5b（t83 `&&` 改向）同一「行为变更登记卡」模式。
     */
    @Test fun n5_valuePositionRejected() {
        val src = "fun f(): Bool = True ∧ False"
        assertTrue(errCodes(src).contains("E-PROP-FORM"), "#5 值位 ∧ 落 E-PROP-FORM：" + errCodes(src))
        assertTrue(allDiags(src).any { it.contains("布尔与写作 `&`") }, "#5 提示值位 `&`：" + allDiags(src))
    }

    /**
     * #5b（t83 强制更改）：&& / || 已退役——表达式层用 &，命题层专用 ∧ / ∨。
     * BEFORE(t78):`&&`/`||` 与 Unicode 形态同产物（词法归一）。
     * AFTER(t83):`&&`/`||` 落 E-OP-RETIRED 并提示可编译写法（ADR-48 §八）。
     */
    @Test fun n5b_asciiAliasesSameProduct() {
        assertTrue(errCodes("fun f(): Bool = True && False").contains("E-OP-RETIRED"),
            "#5b && 退役落 E-OP-RETIRED：" + allDiags("fun f(): Bool = True && False"))
        assertTrue(errCodes("fun f(): Bool = True || False").contains("E-OP-RETIRED"),
            "#5b || 退役落 E-OP-RETIRED：" + allDiags("fun f(): Bool = True || False"))
        assertFalse(errCodes("fun f(): Bool = True & False").any { it.startsWith("E") },
            "#5b 表达式位 & 通过：" + allDiags("fun f(): Bool = True & False"))
    }

    /**
     * #6（t82/ADR-50 强制更改）：项位 `→` 仍产 HirTBin（68 样本 HIR JSON 逐字比对的回归哨兵）。
     * BEFORE(t74):源写 `x ∈ (l..r) → x >= l`——当时全中缀左结合恰好把 `((x ∈ l..r) → x) >= l` 落成
     *   `>=` 原子 + 项位 `→` 的 HirTBin,测试借此当哨兵。
     * AFTER(t82):优先级落地后该源的意义校正为 `(x ∈ l..r) → (x >= l)`(HirPImp,命题位 →),项位哨兵
     *   改用**显式括号**的等价分组 `(x ∈ (l..r) → x) >= l`——树形与 BEFORE 逐字相同（atom(>=) 项位
     *   TBin(→)),哨兵不丢。
     */
    @Test fun n6_termPositionArrowKeepsHirTBin() {
        val p = posts("fun t[x, l, r]: Null <(x ∈ (l..r) → x) >= l>")[0]
        val atom = assertIs<HirPAtom>(p, "#6 顶层是 >= 原子（显式括号定项位分组）")
        val t0 = assertIs<HirTBin>(atom.terms[0], "#6 项位 → 必须是 HirTBin，不得退化为 HirTCall")
        assertEquals("→", t0.op, "#6 项位算符文本")
    }

    /** #7：无空格粘连 `p∧q` 是一个标识符（R1）——未声明落 E-UNBOUND-NAME 并教加空格。 */
    @Test fun n7_gluedNameRejectedWithSpaceHint() {
        val src = "fun f[p, q]: Null <p∧q>"
        assertTrue(errCodes(src).contains("E-UNBOUND-NAME"), "#7 粘连名落 E-UNBOUND-NAME：" + allDiags(src))
        assertTrue(allDiags(src).any { it.contains("加空格") }, "#7 提示加空格：" + allDiags(src))
    }

    /** #8：原生命题不可重定义（表驱动保护，P4 的 E-NATIVE-REDECL 对 P5 名字同样生效）。 */
    @Test fun n8_nativeRedeclRejected() {
        assertTrue(errCodes("fun ∧[p, q]: Null <p>").contains("E-NATIVE-REDECL"),
            "#8 fun ∧ 落 E-NATIVE-REDECL：" + allDiags("fun ∧[p, q]: Null <p>"))
        assertTrue(errCodes("fun →[p, q]: Null <p>").contains("E-NATIVE-REDECL"),
            "#8 fun → 落 E-NATIVE-REDECL：" + allDiags("fun →[p, q]: Null <p>"))
    }

    /** #9：元数错 `∧<p, q, r>` ⇒ E-ARITY，且提示写法本身可编译（ADR-48 §八）。 */
    @Test fun n9_arityMismatchRejectedWithCompilableForm() {
        val src = "fun f[p, q, r]: Null <∧<p, q, r>>"
        assertTrue(errCodes(src).contains("E-ARITY"), "#9 元数错落 E-ARITY：" + allDiags(src))
        assertTrue(allDiags(src).any { it.contains("∧<p, q>") }, "#9 提示可编译写法：" + allDiags(src))
    }

    private fun head(p: HirProp): String = (p as? HirPAtom)?.head ?: "<非原子>"

    private fun psub(p: HirProp): String = when (p) {
        is HirPAtom -> p.head
        is HirPAnd -> "(" + psub(p.a) + "∧" + psub(p.b) + ")"
        else -> p::class.simpleName ?: "?"
    }
}