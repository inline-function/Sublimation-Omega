package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.cli.compileStdlibUnits
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirStructDecl
import sublimation.frontend.hir.HirWhen
import sublimation.frontend.json.HirJson
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t41（ADR-28）：联合类型（一切类型表达式）+ 空构造子单例 + 归纳类型诊断 + when 联合穷尽性 +
 * def 删除后的命题面收尾。
 *
 * 用户裁决（2026-11-06 15:10）：联合成员可以是 struct 名 / 别名 / 类型应用 / 元组 / 函数类型；
 * 成员→联合单向隐式拓宽；成员不得重复（E-DUP-UNION-MEMBER）；构造即展平。空构造子单例：判据
 *= 字段数 0（struct P(x: Nat = 0) 不算），None() 报 E-NULLARY-PAREN，is None 与 None 都合法。
 */
class UnionSemanticsTest {

    // t138（R-23 B1）：prelude 虚拟层 retire——编译挂 stdlib 快照（与 CLI 默认挂载同构；
    // 虚拟 struct/别名数据源已换 stdlib 源——P-7 用户同名优先口径不变）
    private val stdlibMounts: Map<String, ModuleSnapshot> =
        compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
            .let { (u, _) -> u.associate { it.alias to it.snapshot } }

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "union.subl")
        val (hir, bag) = compile(pr.file, pr.notes, scriptMode = false, mounted = stdlibMounts)
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
    private fun allCodes(bag: DiagBag) = bag.diags.map { it.code }
    private fun errMsgs(bag: DiagBag) =
        bag.diags.filter { it.severity == Severity.ERROR }.joinToString(" | ") { it.code + ":" + it.message }
    private fun supplements(bag: DiagBag) = bag.diags.filter { it.severity == Severity.SUPPLEMENT }

    private val unionBase = "struct A()\nstruct B()\ntype E = A | B\n"

    // ============================ 任务 1：联合类型语义 ============================

    @Test
    fun memberWidensToUnionImplicitly() {
        val (_, bag) = script(unionBase + "var x: E = A\nvar y: E = B\n")
        assertFalse(bag.hasError, "成员值可直接用于联合位（单向拓宽），无错。实际: " + errMsgs(bag))
    }

    @Test
    fun unionToMemberIsIllegal() {
        val (_, bag) = script(unionBase + "var x: E = A\nvar y: A = x\n")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag),
            "联合→成员非法（需要 is 判定）。实际: " + errMsgs(bag))
    }

    @Test
    fun unionAcceptsEveryMemberShape() {
        val src = "struct S(x: Nat)\ntype T = Nat\n" +
            "type U = S | T | List[Nat] | (Nat, Nat) | (Nat) => Str\n"
        val (_, bag) = script(src)
        assertFalse(bag.hasError, "成员= struct 名 / 别名 / 类型应用 / 元组 / 函数类型皆合法。实际: " + errMsgs(bag))
    }

    @Test
    fun nestedUnionFlattensOnConstruction() {
        val src = "struct A()\nstruct C()\nstruct F()\n" +
            "type B = A | C\ntype D = B | F\nvar x: D = A\n"
        val (_, bag) = script(src)
        assertFalse(bag.hasError, "B = A | C 再 D = B | F 展平为 A | C | F，D 位接受 A。实际: " + errMsgs(bag))
    }

    @Test
    fun dupMemberRejected() {
        val (_, bag) = script("struct A()\ntype X = A | A\n")
        assertTrue(errCodes(bag).contains("E-DUP-UNION-MEMBER"), "type X = A | A → E-DUP-UNION-MEMBER")
        assertTrue(errMsgs(bag).contains("成员 `A` 重复声明"), errMsgs(bag))
    }

    // ============================ 任务 2：空构造子单例 ============================

    @Test
    fun nullaryBareAndParensDeclBothLegal() {
        val (_, bag) = script("struct None\nstruct None2()\nvar a = None\nvar b = None2\n")
        assertFalse(bag.hasError, "struct None 与 struct None() 声明皆合法；裸名取单例。实际: " + errMsgs(bag))
    }

    @Test
    fun nullaryParensCallRejected() {
        val (_, bag) = script("struct None\nvar a = None()\n")
        assertTrue(errCodes(bag).contains("E-NULLARY-PAREN"), "None() → E-NULLARY-PAREN")
        assertTrue(errMsgs(bag).contains("空构造子 `None` 不能用 () 构造，请直接写 `None`（空构造子全局唯一）"), errMsgs(bag))
    }

    @Test
    fun nullaryParensPatternRejected() {
        val src = "struct None()\nstruct S(x: Nat)\ntype E = None | S\n" +
            "fun f(x: E): Nat = when (x) { None() -> 0 ; is S(v) -> v }\n"
        val (_, bag) = script(src)
        assertTrue(errCodes(bag).contains("E-NULLARY-PAREN"), "模式位 None() → E-NULLARY-PAREN")
    }

    @Test
    fun nullaryBareAndIsPatternsBothOk() {
        val src = "struct None()\nstruct S(x: Nat)\ntype E = None | S\n" +
            "fun f(x: E): Nat = when (x) { is None -> 0 ; is S(v) -> v }\n"
        val (_, bag) = script(src)
        assertFalse(bag.hasError, "is None 与裸名 None 模式都接受。实际: " + errMsgs(bag))
    }

    @Test
    fun defaultFieldStructIsNotNullary() {
        val (_, bag) = script("struct P(x: Nat = 0)\nvar p = P()\n")
        assertFalse(errCodes(bag).contains("E-NULLARY-PAREN"),
            "struct P(x: Nat = 0) 有默认值字段——P() 合法，不是空构造子。实际: " + errMsgs(bag))
    }

    @Test
    fun nullaryFlagOnHirStructDeclAndJson() {
        val (hir, _) = script("struct None\nstruct P(x: Nat = 0)\n")
        val decls = hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? HirStructDecl }
        val none = decls.first { it.name == "None" }
        val p = decls.first { it.name == "P" }
        assertTrue(none.nullary, "无字段 struct 带 nullary 旗标")
        assertFalse(p.nullary, "有字段 struct 不带 nullary 旗标（判据=字段数 0，非默认值）")
        val json = HirJson.emit(hir, DiagBag(), "union.subl")
        assertTrue(json.contains("""nullary": true"""), "JSON 输出体现空构造子单例旗标")
    }

    // ============================ 任务 3：归纳类型判定 ============================

    @Test
    fun inductiveTypeFlaggedAsSupplement() {
        val src = "struct Zero()\nstruct Succ(pred: Peano)\ntype Peano = Zero | Succ[Peano]\n"
        val (_, bag) = script(src)
        val ind = supplements(bag).firstOrNull { it.code == "D-INDUCTIVE-TYPE" }
        assertTrue(ind != null, "Peano = Zero | Succ[Peano] → D-INDUCTIVE-TYPE")
        assertFalse(bag.hasError, "归纳类型是合法定义，不落错误。实际: " + errMsgs(bag))
        assertEquals(Severity.SUPPLEMENT, ind!!.severity, "D-INDUCTIVE-TYPE 必须是补充级，不是 error/warn/hint")
        assertEquals("`Peano` 是归纳类型定义（自引用 1 处）。归纳类型需至少一个不含自引用的构造子作为基态。",
            ind.message, "裁决文案逐字")
    }

    @Test
    fun selfCycleRejected() {
        val (_, bag) = script("type A = A\n")
        assertTrue(errCodes(bag).contains("E-CYCLE"), "type A = A → E-CYCLE")
    }

    @Test
    fun partialRefCountIsStillInductive() {
        val (_, bag) = script("struct B()\ntype A = A | B\n")
        assertTrue(supplements(bag).any { it.code == "D-INDUCTIVE-TYPE" },
            "type A = A | B 有基态 B → 归纳类型")
    }

    @Test
    fun mutualCycleWithoutBaseRejected() {
        val src = "type A = B | C\ntype B = A\ntype C = A\n"
        val (_, bag) = script(src)
        assertTrue(errCodes(bag).contains("E-CYCLE"), "成员全部（经别名链）引用自身 → E-CYCLE")
    }

    // ============================ 任务 4：when 联合穷尽性 ============================

    @Test
    fun unionAllStructMembersExhaustiveNoReport() {
        val src = unionBase + "fun f(x: E): Nat = when (x) { is A -> 1 ; is B -> 2 }\n"
        val (hir, bag) = script(src)
        assertFalse(bag.hasError, "联合成员全为具名 struct 且分支齐 → 无错。实际: " + errMsgs(bag))
        assertFalse(allCodes(bag).contains("W-NON-EXHAUSTIVE"), "覆盖齐全不落 W-NON-EXHAUSTIVE")
        val f = hir.entries.mapNotNull { (it as? HirDeclEntry)?.decl as? sublimation.frontend.hir.HirFunDecl }.first()
        val w = f.body as HirWhen
        assertTrue(w.exhaustive, "HirWhen.exhaustive 置真")
        assertEquals("Nat", (w.type as HirNamedType).name)
    }

    @Test
    fun unionBareMemberPatternsAlsoExhaustive() {
        val src = unionBase + "fun f(x: E): Nat = when (x) { A -> 1 ; B -> 2 }\n"
        val (_, bag) = script(src)
        assertFalse(allCodes(bag).contains("W-NON-EXHAUSTIVE"), "裸名成员模式（D-PAT-CTOR 归一）同样计入覆盖")
    }

    @Test
    fun unionMissingMemberReports() {
        val src = unionBase + "fun f(x: E): Nat = when (x) { is A -> 1 }\n"
        val (_, bag) = script(src)
        assertTrue(allCodes(bag).contains("W-NON-EXHAUSTIVE"), "缺 B → W-NON-EXHAUSTIVE")
    }

    @Test
    fun nonNamedUnionMemberRequiresElse() {
        val head = "struct A()\ntype E2 = A | (Nat, Nat)\n"
        val (_, bagElse) = script(head + "fun g(x: E2): Nat = when (x) { is A -> 1 ; else -> 2 }\n")
        assertFalse(allCodes(bagElse).contains("W-NON-EXHAUSTIVE"), "含非具名成员 + else → 不落 W")
        val (_, bagNoElse) = script(head + "fun g(x: E2): Nat = when (x) { is A -> 1 }\n")
        val w = bagNoElse.diags.firstOrNull { it.code == "W-NON-EXHAUSTIVE" }
        assertTrue(w != null, "含非具名成员且无 else → W-NON-EXHAUSTIVE")
        assertTrue(w!!.message.contains("联合含非具名成员，无法静态穷尽，请补 else"), w.message)
    }

    // ============================ 任务 5：命题面收尾 ============================

    @Test
    fun topLevelAxiomWithFormulaPasses() {
        val (_, bag) = script("axiom Even(4) ↔ ∃k . 4 = 2 * k\n")
        assertFalse(bag.hasError, "axiom Even(4) ↔ ∃k . 4 = 2k 作为顶层公理通过。实际: " + errMsgs(bag))
    }

    @Test
    fun propAtomUsedWithoutDefinition() {
        val (_, bag) = script("axiom r ↔ Divides(2, 4)\n")
        assertFalse(bag.hasError, "命题项遇未定义名字不报未定义（命题可原子使用）。实际: " + errMsgs(bag))
    }
}
