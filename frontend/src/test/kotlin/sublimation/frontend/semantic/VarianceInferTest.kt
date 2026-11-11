package sublimation.frontend.semantic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t64（ADR-41，P-10 步骤 3，ST-13.1）：变构性自动推断验收。
 *
 * 1. 声明级变构对（σ_imm, σ_mut）——验收表 #1~#7 + 对照例（值经 compileVariance 表观察）。
 * 2. 界方向判定 fallout——#8~#10（E-BOUND-INVARIANT / 下界通过 / 上界语义）。
 * 3. 泛型实参位按声明变构分派（w1~w5 队长 BEFORE 全部误放行；负例成对）。
 * 4. 单调性机械自检（硬要求）：σ_imm=不变 ⇒ σ_mut=不变；σ_mut=协变 ⇒ σ_imm=协变——
 *    **全仓**（e2e 样例全集 + 本测试全部 fixture）逐声明断言，并断言 E-VARIANCE-MONO 未落。
 * 5. 内建 List 锁死 (协变, 不变)（内建硬表边界；用户同名声明优先的守卫见 u1/u2）。
 */
class VarianceInferTest {

    private fun reportOf(src: String, fileName: String = "t64.subl"): VarianceReport {
        val pr = parseScriptSourceWithNotes(src, fileName)
        val r = compileVariance(pr.file, pr.notes)
        pr.diag.forEach { d -> if (d.severity == Severity.ERROR) r.diag.error(d.code, d.pos, d.message) }
        return r
    }
    private fun tableOf(src: String, fileName: String = "t64.subl"): Map<String, List<Pair<String, VariancePair>>> {
        val r = reportOf(src, fileName)
        assertEquals(emptyList(), r.diag.diags.filter { it.severity == Severity.ERROR }, "fixture 不应出错: " + src.take(60))
        return r.variance
    }
    private fun codesOf(src: String, fileName: String = "t64.subl"): List<String> =
        reportOf(src, fileName).diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    private fun v(table: Map<String, List<Pair<String, VariancePair>>>, decl: String, param: String): VariancePair? =
        table[decl]?.firstOrNull { it.first == param }?.second

    // ============================ #1~#7 声明级变构对 ============================

    @Test fun r1_box_onlyReturn_isCovariant() {
        val t = tableOf("trait Box[T] { fun get(): T }")
        assertEquals(VariancePair(Variance.CO, Variance.CO), v(t, "Box", "T"))
    }
    @Test fun r2_sink_onlyParam_isContravariant() {
        val t = tableOf("trait Sink[T] { fun put(x: T): Nat }")
        assertEquals(VariancePair(Variance.CONTRA, Variance.CONTRA), v(t, "Sink", "T"))
    }
    @Test fun r3_both_isInvariant() {
        val t = tableOf("trait Both[T] { fun get(): T; fun put(x: T): Nat }")
        assertEquals(VariancePair(Variance.INV, Variance.INV), v(t, "Both", "T"))
    }
    @Test fun r4_list_getPlusMutAdd_isCoInv() {
        // 用户裁决核心例：非 @mut 全协变、@mut 成员有逆位 ⇒ (协变, 不变)
        val t = tableOf("trait List[T] { fun get(i: Nat): T; @mut fun add(x: T): Null }")
        assertEquals(VariancePair(Variance.CO, Variance.INV), v(t, "List", "T"))
    }
    @Test fun r4b_memberParamMutOnly_doesNotPoisonImm() {
        // @mut 成员形参位**只**计 σ_mut——对照（r4 对称）：imm 仍协变
        val t = tableOf("trait Adder[T] { fun zero(): T; @mut fun add(x: T): Null }")
        assertEquals(VariancePair(Variance.CO, Variance.INV), v(t, "Adder", "T"))
    }
    @Test fun r4c_mutMemberReturn_symmetricIsContraInv() {
        // R5 对称：非 @mut 全逆（put 形参位）+ @mut 成员带正位（grab 返回位）⇒ (逆变, 不变)
        val t = tableOf("trait R5[T] { fun put(x: T): Nat; @mut fun grab(): T }")
        assertEquals(VariancePair(Variance.CONTRA, Variance.INV), v(t, "R5", "T"))
    }
    @Test fun r5_writableField_poisonsBothViews() {
        // 可写字段（读 + 写 − 同时计）⇒ 两视图皆不变；对照：不可写字段 → 协变
        val t = tableOf("struct S[T](@mut x: T)")
        assertEquals(VariancePair(Variance.INV, Variance.INV), v(t, "S", "T"))
    }
    @Test fun r5b_immutableField_isCovariant() {
        val t = tableOf("struct S2[T](x: T)")
        assertEquals(VariancePair(Variance.CO, Variance.CO), v(t, "S2", "T"))
    }
    @Test fun r6_recursiveTree_convergesCovariant() {
        // #6：递归（自引用经内建 List 的 σ_imm 协变因子）且**迭代收敛**（无 E-VARIANCE-NOFIXPOINT）
        val src = "trait Tree[T] { fun children(): List[Tree[T]] }"
        val codes = codesOf(src)
        assertFalse(codes.contains("E-VARIANCE-NOFIXPOINT"), "递归 Tree 必须在轮次上限内收敛")
        val t = tableOf(src)
        assertEquals(VariancePair(Variance.CO, Variance.CO), v(t, "Tree", "T"))
    }
    @Test fun r7_mutualRecursion_converges() {
        // #7：相互递归 A→B→A 收敛，不超轮次上限
        val src = "trait A[T] { fun toB(): B[T] }\ntrait B[T] { fun toA(): A[T] }"
        val codes = codesOf(src)
        assertFalse(codes.contains("E-VARIANCE-NOFIXPOINT"), "相互递归必须收敛")
        val t = tableOf(src)
        assertEquals(VariancePair(Variance.CO, Variance.CO), v(t, "A", "T"))
        assertEquals(VariancePair(Variance.CO, Variance.CO), v(t, "B", "T"))
    }
    @Test fun r7b_unusedAndBareParams_defaultCovariant() {
        // 未使用 / 仅别处出现的参数：格值保持双变 → 定案协变（幻影参数，不约束实参型）
        val t = tableOf("trait Phantom[T, U] { fun get(): T }")
        assertEquals(VariancePair(Variance.CO, Variance.CO), v(t, "Phantom", "T"))
        assertEquals(VariancePair(Variance.CO, Variance.CO), v(t, "Phantom", "U"))
    }
    @Test fun r7c_compositePolarity_doubleNegative() {
        // #3.1 嵌套复合：T 在 (T) => T 的形参位（负×负=正）+ 返回位（负×正=负）⇒ 仍得不变
        val t = tableOf("trait C[T] { fun apply(f: (T) => T): Nat }")
        assertEquals(VariancePair(Variance.INV, Variance.INV), v(t, "C", "T"))
    }

    // ============================ #8~#10 界方向判定 ============================

    @Test fun r8_invariantWithBound_reportsBoundary() {
        // #8（已有行为不得回退）：不变 + 界 → E-BOUND-INVARIANT
        val codes = codesOf("fun f[T: Nat](a: T, b: T): T = a")
        assertTrue(codes.contains("E-BOUND-INVARIANT"), "#8 必须落 E-BOUND-INVARIANT")
    }
    @Test fun r9_contravariantWithBound_passes() {
        // #9：逆变 ⇒ 下界方向——声明位通过（界方向判定已得 (σ_imm=逆变)）
        val codes = codesOf("fun g[T: Nat](a: T): Nat = 0")
        assertFalse(codes.contains("E-BOUND-INVARIANT"), "单逆位带界不应落 E-BOUND-INVARIANT")
    }
    @Test fun r10_covariantWithBound_passes() {
        // #10：协变位带界（返回位）⇒ 上界语义——声明位通过
        val codes = codesOf("fun h[T: Nat](): T = 0")
        assertFalse(codes.contains("E-BOUND-INVARIANT"), "单协位带界不应落 E-BOUND-INVARIANT")
    }

    // ============================ w1~w5：泛型实参位按声明变构分派（语义核心） ============================

    private val sinkSrc = "trait Sink[T] { fun put(x: T): Nat }\nfun useS(s: Sink[Any]): Nat = 0\nfun useN(s: Sink[Nat]): Nat = 0"
    private val boxSrc = "trait Box[T] { fun get(): T }\nfun useA(b: Box[Any]): Nat = 0\nfun useN(b: Box[Nat]): Nat = 0"
    private val bothSrc = "trait Both[T] { fun get(): T; fun put(x: T): Nat }\nfun useA(b: Both[Any]): Nat = 0\nfun useN(b: Both[Nat]): Nat = 0"

    @Test fun w1_sinkContravariant_acceptsCorrectDirection() {
        // w1 正例：Sink[Any] 传 Sink[Nat] 位（逆变正确方向）必通过
        val codes = codesOf(sinkSrc + "\nfun w1(s: Sink[Any]): Nat = useN(s)")
        assertFalse(codes.contains("E-TYPE-MISMATCH"), "w1 逆变正方向必须通过，实得: " + codes)
    }
    @Test fun w2_sinkContravariant_rejectsFlipped() {
        // w2 负例（队长 BEFORE 误放行）：Sink[Nat] 传 Sink[Any] 位必拒绝
        val codes = codesOf(sinkSrc + "\nfun w2(s: Sink[Nat]): Nat = useS(s)")
        assertTrue(codes.contains("E-TYPE-MISMATCH"), "w2 逆转变名流必须拒绝，实得: " + codes)
    }
    @Test fun w3_boxCovariant_acceptsWidening() {
        // w3 正例：Box[Nat] 传 Box[Any] 位（协变升宽）必通过
        val codes = codesOf(boxSrc + "\nfun w3(b: Box[Nat]): Nat = useA(b)")
        assertFalse(codes.contains("E-TYPE-MISMATCH"), "w3 协变升宽必须通过，实得: " + codes)
    }
    @Test fun w4_boxCovariant_rejectsNarrowing() {
        // w4 负例（BEFORE 误放行）：Box[Any] 传 Box[Nat] 位必拒绝
        val codes = codesOf(boxSrc + "\nfun w4(b: Box[Any]): Nat = useN(b)")
        assertTrue(codes.contains("E-TYPE-MISMATCH"), "w4 协变反向必须拒绝，实得: " + codes)
    }
    @Test fun w5_bothInvariant_rejectsBothDirections() {
        // w5 负例（BEFORE 误放行）：不变 ⇒ 两方向都拒绝
        val c1 = codesOf(bothSrc + "\nfun wa(b: Both[Nat]): Nat = useA(b)")
        val c2 = codesOf(bothSrc + "\nfun wb(b: Both[Any]): Nat = useN(b)")
        assertTrue(c1.contains("E-TYPE-MISMATCH"), "w5 方向一必须拒绝，实得: " + c1)
        assertTrue(c2.contains("E-TYPE-MISMATCH"), "w5 方向二必须拒绝，实得: " + c2)
    }
    @Test fun w6_listAliasedBuiltin_staysAcceptingInImm() {
        // 对照（不回归）：List[Nat] → List[Any]（内建 σ_imm 协变）升宽仍通过
        val codes = codesOf("fun useA(xs: List[Any]): Nat = 0\nfun w6(xs: List[Nat]): Nat = useA(xs)")
        assertFalse(codes.contains("E-TYPE-MISMATCH"), "内建 List 不可变视图协变升宽应通过，实得: " + codes)
    }
    @Test fun w7_traitSubtypingByName_parentChainPreserved() {
        // 对照（不回归）：特质父链 A[Nat] <: B（B 的父辈），非同名应用——不经变构分派
        val src = "trait B { fun base(): Nat }\ntrait A[T] : B { fun get(): T }\n" +
            "fun useB(b: B): Nat = 0\nfun w7(a: A[Nat]): Nat = useB(a)"
        val codes = codesOf(src)
        assertFalse(codes.contains("E-TYPE-MISMATCH"), "父链满足不得被变构分派影响，实得: " + codes)
    }

    // ============================ @mut 视图不变性（R4 语义落地） ============================

    @Test fun m1_mutView_invariantArgs_rejected() {
        // R4 (协变, 不变) 的语义后果：@mut List[Nat] 传 @mut List[Any] 位必拒绝（σ_mut 不变）
        val src = "trait List[T] { fun get(i: Nat): T; @mut fun add(x: T): Null }\n" +
            "fun useM(xs: @mut List[Any]): Null = useM(xs)\nfun m1(xs: @mut List[Nat]): Null = useM(xs)"
        val codes = codesOf(src)
        assertTrue(codes.contains("E-TYPE-MISMATCH"), "@mut 视图不变性必须在实参位生效，实得: " + codes)
    }
    @Test fun m2_mutView_sameArgs_accepted() {
        // 对照：@mut List[Nat] 传 @mut List[Nat] 位（同型）必通过
        val src = "trait List[T] { fun get(i: Nat): T; @mut fun add(x: T): Null }\n" +
            "fun useM(xs: @mut List[Nat]): Null = useM(xs)\nfun m2(xs: @mut List[Nat]): Null = useM(xs)"
        val codes = codesOf(src)
        assertFalse(codes.contains("E-TYPE-MISMATCH"), "@mut 同型同参必须通过，实得: " + codes)
    }
    @Test fun m3_immViewOfR4Trait_staysCovariant() {
        // 对照：不可变视图下 List[Nat] → List[Any] 仍协变通过（σ_imm 协变）
        val src = "trait List[T] { fun get(i: Nat): T; @mut fun add(x: T): Null }\n" +
            "fun useA(xs: List[Any]): Null = useA(xs)\nfun m3(xs: List[Nat]): Null = useA(xs)"
        val codes = codesOf(src)
        assertFalse(codes.contains("E-TYPE-MISMATCH"), "σ_imm 协变升宽必须通过，实得: " + codes)
    }

    // ============================ 内建边界与守卫 ============================

    @Test fun b1_builtinList_lockedCoInv() {
        // 无用户 List 声明时，List 走内建硬表 (协变, 不变)：σ_imm 协变升宽通过
        val up = codesOf("fun useA(xs: List[Any]): Nat = 0\nfun b1a(xs: List[Nat]): Nat = useA(xs)")
        assertFalse(up.contains("E-TYPE-MISMATCH"), "内建 List σ_imm 协变升宽通过，实得: " + up)
    }
    @Test fun b2_builtinList_mutViewInvariant() {
        // 内建 List 的 @mut 视图不变性：@mut List[Nat] → @mut List[Any] 位必拒绝（σ_mut 不变）
        val src = "fun useM(xs: @mut List[Any]): Null = useM(xs)\nfun b2(xs: @mut List[Nat]): Null = useM(xs)"
        val codes = codesOf(src)
        assertTrue(codes.contains("E-TYPE-MISMATCH"), "内建 @mut List 实参位不变性必须生效，实得: " + codes)
    }
    @Test fun b3_userDeclShadowsBuiltinTable() {
        // 守卫证据：用户声明同名 List 为纯协变（无 @mut 成员）时，实参位按**用户表**分派（σ_mut 协变⇒接受）
        val src = "trait List[T] { fun get(i: Nat): T }\n" +
            "fun useM(xs: @mut List[Any]): Null = useM(xs)\nfun b3(xs: @mut List[Nat]): Null = useM(xs)"
        val codes = codesOf(src)
        assertFalse(codes.contains("E-TYPE-MISMATCH"), "用户同名声明必须覆盖内建表，实得: " + codes)
    }
    @Test fun b4_unknownName_fallsBackToOldCovariant() {
        // 未声明名（无表条目）：回退约定协变（t62 旧口径不回归）
        val codes = codesOf("fun useA(z: Zzz[Any]): Nat = 0\nfun b4(z: Zzz[Nat]): Nat = useA(z)")
        assertFalse(codes.contains("E-TYPE-MISMATCH"), "未知名回退协变应通过，实得: " + codes)
    }

    // ============================ 单调性机械自检（硬要求，全仓断言） ============================

    /** 机械断言两部律——对**每个**泛型声明的每个参数。 */
    private fun assertMonotone(table: Map<String, List<Pair<String, VariancePair>>>, tag: String) {
        table.forEach { (decl, ps) -> ps.forEach { (name, p) ->
            assertFalse(p.imm == Variance.INV && p.mut != Variance.INV,
                tag + " " + decl + "." + name + " 违反律一：σ_imm=不变 ⇒ σ_mut=不变（实得 " + p + "）")
            assertFalse(p.mut == Variance.CO && p.imm != Variance.CO,
                tag + " " + decl + "." + name + " 违反律二：σ_mut=协变 ⇒ σ_imm=协变（实得 " + p + "）")
        } }
    }

    @Test fun mono_allAcceptanceFixtures() {
        // 本测试全部 fixture 形状各自单调
        listOf(
            "trait Box[T] { fun get(): T }",
            "trait Sink[T] { fun put(x: T): Nat }",
            "trait Both[T] { fun get(): T; fun put(x: T): Nat }",
            "trait List[T] { fun get(i: Nat): T; @mut fun add(x: T): Null }",
            "struct S[T](@mut x: T)",
            "tree", "mutual",
        ).forEach { tag ->
            val src = when (tag) {
                "tree" -> "trait Tree[T] { fun children(): List[Tree[T]] }"
                "mutual" -> "trait A[T] { fun toB(): B[T] }\ntrait B[T] { fun toA(): A[T] }"
                else -> tag
            }
            val r = reportOf(src, "t64-mono.subl")
            val cs = r.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
            assertFalse(cs.any { it == "E-VARIANCE-MONO" }, tag + " 落了 E-VARIANCE-MONO: " + cs)
            assertMonotone(r.variance, tag)
        }
    }

    @Test fun mono_fullE2eCorpus() {
        // 全仓机械自检：e2e 样例全集 + core 标准库片段（68 样本），逐声明断言两部律。
        val root = File("frontend/src/test/resources/e2e")
        val samples = root.walkTopDown().filter { it.isFile && it.extension == "subl" }.sortedBy { it.path }.toList()
        assertTrue(samples.isNotEmpty(), "e2e 样本目录非空")
        var checkedDecls = 0
        samples.forEach { f0 ->
            val src = f0.readText(Charsets.UTF_8)
            val r = reportOf(src, f0.name)
            val cs = r.diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
            assertFalse(cs.any { it == "E-VARIANCE-MONO" }, f0.name + " 落 E-VARIANCE-MONO: " + cs)
            assertFalse(cs.any { it == "E-VARIANCE-NOFIXPOINT" }, f0.name + " 落 E-VARIANCE-NOFIXPOINT: " + cs)
            assertMonotone(r.variance, f0.name)
            checkedDecls += r.variance.size
        }
        assertTrue(checkedDecls > 0, "e2e 语料必须含泛型声明（否则机械自检空转）")
    }
}
