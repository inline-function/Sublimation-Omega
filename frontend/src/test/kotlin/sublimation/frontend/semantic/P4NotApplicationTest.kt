package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirPNot
import sublimation.frontend.hir.HirProp
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t70（P4，用户裁决 N1）：否定写作 **`¬<p>`**；`¬` 是名字材料（批次 3 落地）。
 * *
 * ## HIR 同构口径（队长 2026-11-08 裁决）
 * `HirProp` 结构相等（P-STRUCT-EQ，hir/Prop.kt）：**忽略** nid（节点编号）、origin（源位置定位
 * 元数据）、notes（解析留痕）——三者均非项同一性基础；**全等** head / terms / 子命题结构。
 * 终态（批次 3 收口）：唯一合法否定形态是 `¬<p>`。
 * `¬p`/N-prefix 形态（含 `¬(p)`）于批次 3 退役：`¬p` 是单个标识符（未声明落 E-UNBOUND-NAME + 提示），
 * `¬(p)` 是**值位调用形态**——命题位落 E-PROP-FORM 并提示 `¬<p>`。等价性证据只取文法内形态。
 * （`not` 是未入文法的别名，队长已报用户裁决——不作等价性证据，见 ADR-48 §七。）
 */
class P4NotApplicationTest {

    private fun errCodes(src: String, file: String = "t70.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.filter { it.severity == Severity.ERROR }.map { it.code } +
            diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    }

    private fun allDiags(src: String, file: String = "t70.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.map { it.code + " :: " + it.message } +
            diag.diags.map { it.code + " :: " + it.message }
    }

    private fun posts(src: String, file: String = "t70.subl"): List<HirProp> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (hir, diag) = compile(pr.file, pr.notes)
        val decls = hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl }
        return (decls.firstOrNull() as? HirFunDecl)?.posts ?: emptyList()
    }

    /** #1：`p → ¬<p>` 作为契约后件通过（编译零错）。 */
    @Test fun r1_arrowConsequentCompiles() {
        assertFalse(errCodes("fun f[p]: Null <p → ¬<p>>").any { it.startsWith("E") },
            "#1 ¬<p> 通过：" + allDiags("fun f[p]: Null <p → ¬<p>>"))
    }

    /** #1b：数学风样本形（排中律）通过。 */
    @Test fun r1b_tertiumNonDaturCompiles() {
        assertFalse(errCodes("@theorem\nfun tertiumNonDatur[p]: Null <p ∨ ¬<p>>").any { it.startsWith("E") },
            "#1b 排中律 ¬<p> 通过：" + allDiags("@theorem\nfun tertiumNonDatur[p]: Null <p ∨ ¬<p>>"))
    }

    /** #2：两写法分野（批次 3 收口）——`¬<p>` 合法；`¬(p>`... 见下。 */
    @Test fun r2_twoFormsDivide() {
        // 唯一合法形态通过
        assertFalse(errCodes("fun f[p]: Null <p → ¬<p>>").any { it.startsWith("E") },
            "#2 ¬<p> 通过：" + allDiags("fun f[p]: Null <p → ¬<p>>"))
        // `¬(p)`（值位调用形态）在命题位拒绝——E-PROP-FORM 且提示 `¬<p>`
        val codes = errCodes("fun f[p]: Null <p → ¬(p)>")
        assertTrue(codes.contains("E-PROP-FORM"), "#2 ¬(p) 命题位落 E-PROP-FORM：$codes")
        assertTrue(allDiags("fun f[p]: Null <p → ¬(p)>").any { it.contains("¬<p>") },
            "#2 提示写出 ¬<p>")
    }

    /** #3：双否定 `¬<¬<p>>` 通过且结构为 PNot(PNot(Atom p))（P-STRUCT-EQ 口径）。 */
    @Test fun r3_doubleNegation() {
        assertFalse(errCodes("fun dneg[p]: Null <¬<¬<p>>>").any { it.startsWith("E") },
            "#3 双否定通过：" + allDiags("fun dneg[p]: Null <¬<¬<p>>>"))
        val a = posts("fun dneg[p]: Null <¬<¬<p>>>")[0]
        val inner = assertIs<HirPNot>(a)
        val deep = assertIs<HirPNot>(inner.p)
        val atom = assertIs<HirPAtom>(deep.p)
        assertEquals("p", atom.head, "#3 原子头")
    }

    /** #4：内层尖括号与外层后件括号相邻（`<p ∨ ¬<p>>`）嵌套配对正常。 */
    @Test fun r4_adjacentAngleNesting() {
        assertFalse(errCodes("fun tertium[p]: Null <p ∨ ¬<p>>").any { it.startsWith("E") },
            "#4 相邻尖括号配对：" + allDiags("fun tertium[p]: Null <p ∨ ¬<p>>"))
    }

    /** #5（批次 3）：`¬p` 整体已是标识符——未声明落 E-UNBOUND-NAME 且提示正确写法 `¬<p>`。 */
    /** #5：值位 `¬p` 未声明 ⇒ E-UNBOUND-NAME；提示须是**值位正确**的 `!p`——
     * §八可编译性规则：`¬<p>` 是命题写法，向值位推荐它是「对的语法、错的语义」。 */
    @Test fun r5_bareNotIsUnboundNameWithHint() {
        val codes = errCodes("fun f(): Bool = ¬p")
        assertTrue(codes.contains("E-UNBOUND-NAME"), "#5 ¬p 未声明落 E-UNBOUND-NAME：$codes")
        val msgs = allDiags("fun f(): Bool = ¬p")
        assertTrue(msgs.any { it.contains("!p") }, "#5 值位提示写出 !p：$msgs")
    }

    /** #5b（追加验收 #8）：命题位 `¬p` 未声明 ⇒ 拒绝 + 提示写 `¬<p>`（不静默通过）。 */
    @Test fun r5b_propPositionBareNotRejected() {
        val codes = errCodes("fun f[p]: Null <p → ¬p>")
        assertTrue(codes.contains("E-UNBOUND-NAME"), "#5b 命题位 ¬p 落 E-UNBOUND-NAME：$codes")
        assertTrue(allDiags("fun f[p]: Null <p → ¬p>").any { it.contains("¬<p>") },
            "#5b 提示写出 ¬<p>")
    }

    /** #6：元数错 `¬<p, q>` 拒绝（E-ARITY；表驱动按 §5 注册表元数核对）。 */
    @Test fun r6_arityMismatchRejected() {
        val codes = errCodes("fun bad[p, q]: Null <¬<p, q>>")
        assertTrue(codes.contains("E-ARITY"), "#6 元数错落 E-ARITY：$codes")
    }

    /** #6b：值位（程序位）出现的 `¬<1, 2>` 同样落 E-ARITY。 */
    @Test fun r6b_valuePositionArityRejected() {
        val codes = errCodes("fun bad(): Int = ¬<1, 2>")
        assertTrue(codes.contains("E-ARITY"), "#6b 值位元数错落 E-ARITY：$codes")
    }

    /** #7（批次 3）：`fun ¬(p)` 声明原生命题名 ⇒ E-NATIVE-REDECL（表驱动；位置指向声明处名字）。 */
    @Test fun r7_funNativeRedeclRejected() {
        val codes = errCodes("fun ¬(p): Null <p>")
        assertTrue(codes.contains("E-NATIVE-REDECL"), "#7 声明 ¬ 落 E-NATIVE-REDECL：$codes")
        val msgs = allDiags("fun ¬(p): Null <p>")
        assertTrue(msgs.any { it.contains("内置的原生命题") }, "#7 文案含「内置的原生命题」：$msgs")
    }

    /** #7b（批次 3）：局部变量同名 `var ¬ = 1` 同样落 E-NATIVE-REDECL。 */
    @Test fun r7b_varNativeRedeclRejected() {
        val codes = errCodes("fun f() { var ¬ = 1 }")
        assertTrue(codes.contains("E-NATIVE-REDECL"), "#7b var ¬ 落 E-NATIVE-REDECL：$codes")
    }

    /** #7c（批次 3）：换个名字就行——用户声明 `not_` 且使用 `¬<not_<p>>` 通过。 */
    @Test fun r7c_renamedPositive() {
        val src = "@theorem\nfun not_[p]: Null <p>\n@theorem\nfun q[p]: Null <¬<not_<p>>>"
        assertFalse(errCodes(src).any { it.startsWith("E") }, "#7c 换名通过：" + allDiags(src))
    }

    /** #9：证明通道行为不变——MT 形（preps `<p → q, ¬<q>>` ⊢ posts `¬<p>`）编译通过。 */
    @Test fun r9_proofChannelUnchanged() {
        val src = "@theorem\nfun mt[p, q] <p → q, ¬<q>>: Null <¬<p>>"
        assertFalse(errCodes(src).any { it.startsWith("E") }, "#9 证明通道不变：" + allDiags(src))
    }

    /** #10：`∀x. P<x>`（量词关键词）逐字不动。 */
    @Test fun r10_quantifierUntouched() {
        val src = "@theorem\nfun inst[P]: Null <∀x. P<x> → ⊤>"
        assertFalse(errCodes(src).any { it.startsWith("E") }, "#10 量词样本不变：" + allDiags(src))
    }
}