package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirAxiom
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirIntLit
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirNameRef
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirPImp
import sublimation.frontend.hir.HirPForall
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t67（P-1 修复，用户 2026-11-08 裁决 R2「中缀调用适用于所有二元操作符，不需 infix 关键词」）：
 * 符号中缀（D-6）降为函数调用——与 D-5 标识符中缀、符号前缀调用同产物 HirCall；
 * 未声明符号落既有 E-UNBOUND-NAME 面（不再 E-OP-UNCLASSIFIED「无法定类」）。
 * 内建 ASCII 算符逐字不变：数字塔、ST-10.1 减法、E-OP-UNCLASSIFIED 既有触发面。
 */
class SymbolInfixCallTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "sym.subl")
        val (hir, bag) = compile(pr.file, pr.notes)
        pr.diag.forEach { d ->
            when (d.severity) {
                Severity.ERROR -> bag.error(d.code, d.pos, d.message)
                Severity.WARN -> bag.warn(d.code, d.pos, d.message)
                Severity.HINT -> bag.hint(d.code, d.pos, d.message)
                Severity.SUPPLEMENT -> bag.supplement(d.code, d.pos, d.message)
            }
        }
        return hir to bag
    }

    private fun errCodes(bag: DiagBag) = bag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    private fun errMsgs(bag: DiagBag) =
        bag.diags.filter { it.severity == Severity.ERROR }.joinToString("\n") { it.code + ":" + it.message }
    private fun stmts(hir: HirFile) = hir.entries.mapNotNull { (it as? HirStmtEntry)?.stmt }
    private fun tailCall(hir: HirFile): HirCall =
        stmts(hir).filterIsInstance<HirVarStmt>().map { it.value }.filterIsInstance<HirCall>().single()

    /** #1：声明的符号函数支持中缀调用（BEFORE：E-OP-UNCLASSIFIED 无法定类）。 */
    @Test fun t67_1_infixDeclaredSymbolPasses() {
        val (_, bag) = script("fun ⊗(a: Nat, b: Nat): Nat = a + b\nvar r = 1 ⊗ 2")
        val codes = errCodes(bag)
        assertTrue(codes.none { it.startsWith("E-") }, "#1 声明的符号中缀应通过，actual=" + codes)
    }

    /** #2：符号前缀调用不得回退（既有通道，验收 #2）。 */
    @Test fun t67_2_prefixSymbolCallNotRegressed() {
        val (_, bag) = script("fun ⊗(a: Nat, b: Nat): Nat = a + b\nvar r = ⊗(1, 2)")
        val codes = errCodes(bag)
        assertTrue(codes.none { it.startsWith("E-") }, "#2 符号前缀调用不得回退，actual=" + codes)
    }

    /** #3：中缀与符号前缀调用产生同一 HIR 结构（除位置信息）。 */
    @Test fun t67_3_infixAndPrefixSameHir() {
        val (h1, b1) = script("fun ⊗(a: Nat, b: Nat): Nat = a + b\nvar r = 1 ⊗ 2")
        val (h2, b2) = script("fun ⊗(a: Nat, b: Nat): Nat = a + b\nvar r = ⊗(1, 2)")
        assertEquals(emptyList<String>(), errCodes(b1) + errCodes(b2))
        val c1 = tailCall(h1); val c2 = tailCall(h2)
        assertEquals("⊗", (c1.callee as HirNameRef).name)
        assertEquals((c1.callee as HirNameRef).name, (c2.callee as HirNameRef).name)
        assertEquals(2, c1.args.size); assertEquals(c2.args.size, c1.args.size)
        assertEquals(c1.symbol?.name, c2.symbol?.name)
        assertEquals((c1.type as? HirNamedType)?.name, (c2.type as? HirNamedType)?.name)
    }

    /** #4：符号中缀不限数值（Str 参数同样通过）。 */
    @Test fun t67_4_infixSymbolOverStrPasses() {
        val (_, bag) = script("fun ⊗(a: Str, b: Str): Str = a\nvar r = \"x\" ⊗ \"y\"")
        val codes = errCodes(bag)
        assertTrue(codes.none { it.startsWith("E-") }, "#4 符号中缀不限数值，actual=" + codes)
    }

    /** #5：未声明的符号中缀落 E-UNBOUND-NAME（未定义），而非 E-OP-UNCLASSIFIED（无法定类）。 */
    @Test fun t67_5_undeclaredSymbolIsUnboundNotUnclassified() {
        val (_, bag) = script("var r = 1 ⊗ 2")
        val codes = errCodes(bag)
        assertTrue(codes.contains("E-UNBOUND-NAME"), "#5 未声明符号应报未定义，actual=" + codes)
        assertFalse(codes.contains("E-OP-UNCLASSIFIED"), "#5 不应再报无法定类：" + errMsgs(bag))
        assertTrue(errMsgs(bag).contains("⊗"), "#5 诊断须指出名字 ⊗,actual=" + errMsgs(bag))
    }

    /** #6：内建算符行为逐字不变——加法数字塔通过（Nat 提升不报错）。 */
    @Test fun t67_6_asciiArithTowerUnchanged() {
        val (_, bag) = script("fun f(a: Nat, b: Nat): Nat = a + b")
        val codes = errCodes(bag)
        assertTrue(codes.none { it.startsWith("E-") }, "#6 数字塔 1+2 应通过，actual=" + codes)
    }

    /** #6b：ST-10.1 减法逐字不变——Nat - Nat 无证明时仍落 E-TYPE-MISMATCH（Int 不符 Nat）。 */
    @Test fun t67_6b_minusSt101Unchanged() {
        val (_, bag) = script("fun f(a: Nat, b: Nat): Nat = a - b")
        val codes = errCodes(bag)
        assertTrue(codes.contains("E-TYPE-MISMATCH"), "#6b ST-10.1 减法应落 E-TYPE-MISMATCH,actual=" + codes)
        assertFalse(codes.contains("E-UNBOUND-NAME") || codes.contains("E-OP-UNCLASSIFIED"),
            "#6b 减法不应经符号/未定义通道：" + errMsgs(bag))
    }

    /** #7：命题位 ∈ 归一逐字不变（PropNormalizer：BinExpr ∈ → HirPAtom head=in）。 */
    @Test fun t67_7_propInNormalizationUnchanged() {
        val (hir, bag) = script("axiom forall x. (x ∈ a) → (x ∈ b)")
        val codes = errCodes(bag)
        assertTrue(codes.none { it.startsWith("E-") }, "#7 命题位 ∈ 不应报错，actual=" + codes)
        val prop = stmts(hir).filterIsInstance<HirAxiom>().single().props.single()
        val imp = (prop as HirPForall).body as HirPImp
        val lhs = imp.a as HirPAtom; val rhs = imp.b as HirPAtom
        assertEquals("in", lhs.head); assertEquals("in", rhs.head)
        assertEquals(2, lhs.terms.size); assertEquals(2, rhs.terms.size)
    }

    /** #8：同符号两次中缀——D-6 解析循环左结合（(1 ⊗ 2) ⊗ 3），通过。 */
    @Test fun t67_8_sameSymbolTwiceLeftAssoc() {
        val (hir, bag) = script("fun ⊗(a: Nat, b: Nat): Nat = a + b\nvar r = 1 ⊗ 2 ⊗ 3")
        val codes = errCodes(bag)
        assertTrue(codes.none { it.startsWith("E-") }, "#8 同符号两次中缀应通过，actual=" + codes)
        val c = tailCall(hir)
        val inner = c.args[0]
        assertTrue(inner is HirCall && (inner as HirCall).args[0] is HirIntLit,
            "#8 左结合：(1 ⊗ 2) 为外层左实参，actual=" + inner)
        assertTrue(c.args[1] is HirIntLit)
    }
}