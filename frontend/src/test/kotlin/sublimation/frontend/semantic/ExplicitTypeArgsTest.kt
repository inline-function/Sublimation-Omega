package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 显式类型实参族验收（t23；B8，ADR-21）。
 *
 * - 调用位紧贴角表（D-1 后缀实例化）的显式类型实参按**类型域**读取（旧 CallCheck:158-168
 *   的 instTermsOf + typeOfTerm 口径），target 按名消解，不再把 target/terms 当值消解；
 * - 显式实参**先绑定、推导不覆盖**（形式语义01 §2.4/§4「显式优先，一次绑定不覆盖」），
 *   冲突在形参位核对处落 E-TYPE-MISMATCH（id<Nat>("a") → 实参 Str 与形参 Nat 不符）；
 * - t44（ADR-29）后 prelude 泛型 ctor（Channel）经**虚拟 struct**走 ctorCall 核对：容量 Nat 可省
 *   且不超过一个（旧 CallCheck:201-215 特判口径；cap 默认 0=无缓冲，P-7 点查）；旧的 PRELUDE_CTORS
 *   枚举字段表已删除（prelude 去枚举化，见 PreludeDecls）。
 */
class ExplicitTypeArgsTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "explicit-tps.subl")
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

    private fun errCodes(bag: DiagBag): List<String> =
        bag.diags.filter { it.severity == Severity.ERROR }.map { it.code }

    private fun firstCall(hir: HirFile): HirCall {
        val vs = hir.entries.mapNotNull { (it as? HirStmtEntry)?.stmt }.filterIsInstance<HirVarStmt>()
        return vs.first().value as HirCall
    }

    @Test
    fun `B8 Channel 显式类型实参构造返回 Channel 加 Nat 实参`() {
        val (hir, bag) = script("var ch: Channel[Nat] = Channel<Nat>(1)")
        assertFalse(bag.hasError, "显式类型实参构造解析+类型核对通过（B8）: " +
            bag.diags.joinToString { it.message })
        assertEquals("Channel", (firstCall(hir).type as HirNamedType).name)
        assertEquals(listOf("Nat"),
            (firstCall(hir).type as HirNamedType).args.map { (it as HirNamedType).name })
    }

    @Test
    fun `B8 Channel 容量实参可省`() {
        val (_, bag) = script("var ch = Channel<Nat>()")
        assertFalse(bag.hasError, "容量实参有默认（旧 CallCheck 特判口径），零实参合法")
    }

    @Test
    fun `B8 Channel 容量实参须为 Nat`() {
        val (_, bag) = script("var ch = Channel<Nat>(\"s\")")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag),
            "容量实参 Str 与字段 Nat 不符: ${bag.diags.joinToString { it.message }}")
        assertTrue(bag.diags.any { it.message.contains("与字段") })
    }

    @Test
    fun `B8 Channel 最多一个容量实参`() {
        val (_, bag) = script("var ch = Channel<Nat>(1, 2)")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag))
        assertTrue(bag.diags.any { it.message.contains("期望 1 实参，实际 2") })
    }

    @Test
    fun `B8 泛型函数显式实参参与实例化`() {
        val (hir, bag) = script("fun id[T](x: T): T = x\nvar a = id<Nat>(1)")
        assertEquals("Nat", (firstCall(hir).type as HirNamedType).name)
        assertFalse(bag.hasError, "id<Nat>(1) 实例化为 Nat: " + bag.diags.joinToString { it.message })
    }

    @Test
    fun `B8 显式实参与实参推导冲突时报类型不符`() {
        val (hir, _) = script("fun id[T](x: T): T = x\nvar a = id<Nat>(\"s\")")
        val call = firstCall(hir)
        assertEquals("Nat", (call.type as HirNamedType).name, "显式绑定不被实参反推覆盖")
        val (_, bag2) = script("fun id[T](x: T): T = x\nvar a = id<Nat>(\"s\")")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag2))
        assertTrue(bag2.diags.any { it.message.contains("实参 1") && it.message.contains("形参") },
            "冲突落在形参位核对: ${bag2.diags.joinToString { it.message }}")
    }

    @Test
    fun `B8 命题供给角表不被实参个数核对误伤`() {
        // 角表三义：witness<tagged<7>>() 是命题显式供给（S-41/§2.10 第 4 义），terms 非 types——
        // 旧 checkFnCall 只按 i<count 绑定前 N 个，不做类型参数个数核对
        val src = "fun witness<p>() { } " +
            "fun needTagged() { } fun main() { witness<tagged<7>>() }"
        val (_, bag) = script(src)
        assertFalse(bag.diags.any { it.code == "E-TYPE-MISMATCH" && it.message.contains("类型参数") },
            "命题供给不落 instArity: ${bag.diags.joinToString { it.message }}")
    }
}
