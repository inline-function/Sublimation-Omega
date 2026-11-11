package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirBinExpr
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirReturn
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t66（ADR-43，ST-10.2）：减法收窄验收——Nat - Nat 之外的位仍是 Int；当 Γ ⊢ 左 >= 右 可证时
 *  智能转换结果为 Nat（严格触发 + 既有 proves + 信任审计 + 逐字失败回退）。
 * 10 例正负成对（#1~#10 对应任务书验收表）+ 补充例（调用实参/var/赋值/预算上界/HIR 留痕）。
 */
class NarrowSubtractionTest {


    private fun errCodes(src: String, file: String = "t66.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        val parseErrs = pr.diag.filter { it.severity == Severity.ERROR }.map { it.code }
        return parseErrs + diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    }

    private fun allDiags(src: String, file: String = "t66.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        val msgs = diag.diags.map { it.code + " :: " + it.message }
        return pr.diag.map { it.code + " :: " + it.message } + msgs
    }

    private fun auditOf(src: String, file: String = "t66.subl"): Pair<List<String>, List<String>> {
        val pr = parseScriptSourceWithNotes(src, file)
        val rep = compileNarrowAudit(pr.file, pr.notes)
        val parseMsgs = pr.diag.map { it.code + " :: " + it.message }
        return (pr.diag.filter { it.severity == Severity.ERROR }.map { it.code } +
            rep.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }) to
            (parseMsgs + rep.diag.diags.map { it.code + " :: " + it.message })
    }

    private fun narrowNoteOf(src: String, file: String = "t66.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val rep = compileNarrowAudit(pr.file, pr.notes)
        val notes = mutableListOf<String>()
        rep.hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl }.forEach { d ->
            val b = (d as? HirFunDecl)?.body ?: return@forEach
            val bin = when (b) {
                is HirBinExpr -> listOf(b)
                is sublimation.frontend.hir.HirBlock -> b.stmts.mapNotNull { st ->
                    (st as? HirReturn)?.expr as? HirBinExpr }
                else -> emptyList()
            }
            bin.forEach { it.notes.forEach { n -> notes.add(n.code) } }
        }
        return notes
    }

    /** #1：a: Nat - b: Nat，Γ ⊢ a >= b（用户公理）⇒ 返回 Nat 通过（收窄为 Nat）。 */
    @Test fun r1_axiomProvableNarrowsToNat() {
        assertFalse(errCodes("fun f(a: Nat, b: Nat): Nat { axiom a >= b; return a - b }").any { it.startsWith("E-") },
            "#1 证出 a >= b 时减法应收窄为 Nat（无错）")
    }

    /** #2：无公理 ⇒ E-TYPE-MISMATCH（Int 与 Nat 不符——逐字回退）。 */
    @Test fun r2_noProofStaysIntMismatch() {
        val codes = errCodes("fun f(a: Nat, b: Nat): Nat = a - b")
        assertTrue(codes.contains("E-TYPE-MISMATCH"), "#2 无证明时保持 Int，落 E-TYPE-MISMATCH：$codes")
    }

    /** #3：if (n == 0) else 支 ⇒ n >= 1（分支事实 + 类型事实）⇒ n - 1 收窄通过。 */
    @Test fun r3_branchFactNarrowsToOne() {
        assertFalse(errCodes(
            "fun f(n: Nat): Nat { if (n == 0) { return 0 } else { return n - 1 } }").any { it.startsWith("E-") },
            "#3 else 支 n != 0 + 类型事实 n >= 0 应证出 n >= 1")
    }

    /** #4：无守卫的 n - 1（期望 Nat）⇒ E-TYPE-MISMATCH。 */
    @Test fun r4_guardedAbsenceRejected() {
        val codes = errCodes("fun f(n: Nat): Nat = n - 1")
        assertTrue(codes.contains("E-TYPE-MISMATCH"), "#4 无守卫时 n - 1 保持 Int：$codes")
    }

    /** #5/#7：期望 Int ⇒ 通过且**不尝试**证明（严格触发）。 */
    @Test fun r5_intExpectNoAttempt() {
        val (codes, _) = auditOf("fun f(a: Nat, b: Nat): Int = a - b")
        assertFalse(codes.any { it.startsWith("E-") }, "#5 期望 Int 应原样通过")
    }

    /** #6：期望 Rat ⇒ 通过（Int <: Rat 升宽，不收窄）。 */
    @Test fun r6_ratExpectWidens() {
        val (codes, _) = auditOf("fun f(a: Nat, b: Nat): Rat = a - b")
        assertFalse(codes.any { it.startsWith("E-") }, "#6 期望 Rat 应升宽通过（无需收窄）")
    }

    /** #7：期望 Int 时收窄尝试次数 = 0（严格触发可观察）。 */
    @Test fun r7_intExpectZeroAttempts() {
        val pr = parseScriptSourceWithNotes("fun f(a: Nat, b: Nat): Int = a - b", "t66.subl")
        val rep = compileNarrowAudit(pr.file, pr.notes)
        assertEquals(0, rep.audit.attempts, "#7 期望 Int 时不得尝试证明（实际 ${rep.audit.attempts} 次）")
    }

    /** #8：消费用户公理的收窄 ⇒ 公理进 trustedBase。 */
    @Test fun r8_axiomConsumedInTrustedBase() {
        val pr = parseScriptSourceWithNotes(
            "fun f(a: Nat, b: Nat): Nat { axiom a >= b; return a - b }", "t66.subl")
        val rep = compileNarrowAudit(pr.file, pr.notes)
        assertTrue(rep.audit.trustedBase.any { it.contains("a") && it.contains("b") && it.contains(">=") },
            "#8 收窄消费的公理应进 trustedBase：${rep.audit.trustedBase}")
    }

    /** #9：仅类型/分支事实的收窄 ⇒ trustedBase 空。 */
    @Test fun r9_typeBranchFactsEmptyTrustedBase() {
        val pr = parseScriptSourceWithNotes(
            "fun f(n: Nat): Nat { if (n == 0) { return 0 } else { return n - 1 } }", "t66.subl")
        val rep = compileNarrowAudit(pr.file, pr.notes)
        assertEquals(emptyList<String>(), rep.audit.trustedBase,
            "#9 类型/分支事实支撑的收窄 trustedBase 应为空")
    }

    /** #10：失败诊断含「无法证明」+ Γ 实况 + 可操作断言建议。 */
    @Test fun r10_failureHintActionable() {
        val msgs = allDiags("fun f(a: Nat, b: Nat): Nat = a - b")
        val hint = msgs.firstOrNull { it.startsWith("D-NARROW-SCOPE") }
        assertNotNull(hint, "#10 应落 D-NARROW-SCOPE 提示")
        assertTrue(hint.contains("无法证明"), "#10 提示含「无法证明」")
        assertTrue(hint.contains("当前 Γ"), "#10 提示含 Γ 实况")
        assertTrue(hint.contains("可先断言"), "#10 提示含可操作断言建议")
    }

    // ---- 补充例（严格性/泛化/预算/留痕）----

    /** 补 S1：块体 return 通道（E-RETURN-TYPE 等价拒绝）+ 有公理则通过。 */
    @Test fun s1_blockReturnRejectsAndAccepts() {
        assertTrue(errCodes("fun f(a: Nat, b: Nat): Nat { return a - b }").contains("E-RETURN-TYPE"),
            "S1 块体无证明落 E-RETURN-TYPE")
        assertFalse(errCodes("fun f(a: Nat, b: Nat): Nat { axiom a >= b; return a - b }")
            .any { it.startsWith("E-") }, "S1 块体有公理应通过")
    }

    /** 补 S2：调用实参位收窄（本规则存在的理由：take(t, n - 1)）。 */
    @Test fun s2_callArgNarrows() {
        assertFalse(errCodes(
            "fun take(xs: List[Nat], n: Nat): List[Nat] = xs\n" +
            "fun f(t: List[Nat], n: Nat): List[Nat] { if (n == 0) { return take(t, n) } else { return take(t, n - 1) } }")
            .any { it.startsWith("E-") }, "S2 调用实参 n - 1（形参 Nat）应收窄")
    }

    /** 补 S3：var 标注位收窄。 */
    @Test fun s3_varAnnotatedNarrows() {
        assertFalse(errCodes(
            "fun f(a: Nat, b: Nat): Null { axiom a >= b; var x: Nat = a - b; return null }")
            .any { it.startsWith("E-") }, "S3 var x: Nat = a - b 应收窄通过")
    }

    /** 补 S4：赋值右值位收窄。 */
    @Test fun s4_assignNarrows() {
        assertFalse(errCodes(
            "fun f(a: Nat, b: Nat): Null { axiom a >= b; var x: @mut Nat = 0; x = a - b; return null }")
            .any { it.startsWith("E-") }, "S4 x = a - b（x: Nat）应收窄通过")
    }

    /** 补 S5：Int 操作数不触发（Int - Int 期望 Nat 仍拒）。 */
    @Test fun s5_intOperandsNotTriggered() {
        assertTrue(errCodes("fun f(a: Int, b: Int): Nat = a - b").contains("E-TYPE-MISMATCH"),
            "S5 Int - Int 不得收窄")
        val pr = parseScriptSourceWithNotes("fun f(a: Int, b: Int): Nat = a - b", "t66.subl")
        val rep = compileNarrowAudit(pr.file, pr.notes)
        assertEquals(0, rep.audit.attempts, "S5 Int 操作数不得尝试")
    }

    /** 补 S6：每函数帧尝试上界（8 次后不再尝试，确定性截断）。 */
    @Test fun s6_perFunctionAttemptCap() {
        val src = "fun f(a: Nat): Nat { var n: Nat = 0; " +
            "n = a - 1; n = a - 2; n = a - 3; n = a - 4; n = a - 5; n = a - 6; n = a - 7; n = a - 8; " +
            "n = a - 9; n = a - 10; return n }"
        val pr = parseScriptSourceWithNotes(src, "t66.subl")
        val rep = compileNarrowAudit(pr.file, pr.notes)
        assertEquals(8, rep.audit.attempts, "S6 尝试上界 8（源序确定性截断）")
    }

    /** 补 S7：收窄成功在 HIR 落 proof:arith-narrow 留痕（provenance 证据）。 */
    @Test fun s7_hirNoteProvenance() {
        val notes = narrowNoteOf("fun f(a: Nat, b: Nat): Nat { axiom a >= b; return a - b }")
        assertTrue(notes.contains("proof:arith-narrow"), "S7 HIR 应留 proof:arith-narrow：$notes")
    }

    /** 补 S8：收窄成功后仍禁越界——期望 Nat 但操作数之一为 Int 混合不触发。 */
    @Test fun s8_mixedOperandsNotTriggered() {
        val pr = parseScriptSourceWithNotes("fun f(a: Nat, b: Int): Nat = a - b", "t66.subl")
        val rep = compileNarrowAudit(pr.file, pr.notes)
        assertEquals(0, rep.audit.attempts, "S8 Nat - Int 不得触发")
        assertTrue(rep.diag.diags.any { it.code == "E-TYPE-MISMATCH" }, "S8 Nat - Int 仍拒")
    }

    /** 补 S9：默认值位收窄。 */
    @Test fun s9_paramDefaultNarrows() {
        assertFalse(errCodes(
            "fun g(k: Nat): Nat = k\n" +
            "fun f(a: Nat, b: Nat): Nat { axiom a >= b; return g(a - b) }")
            .any { it.startsWith("E-") }, "S9 形参默认值位/调用位应收窄通过")
    }
}