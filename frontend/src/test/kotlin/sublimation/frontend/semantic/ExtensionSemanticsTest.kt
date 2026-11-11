package sublimation.frontend.semantic

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirTraitDecl
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirSelfType
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * t30（P-8/D，ADR-24）：扩展函数桶（D1）、三种调用形态（D2）、结构化满足（D3）、约束检查（D4）、
 * Self 与其余新码（D5）的正反例——卷 TS v1 §2~§5/§9。
 */
class ExtensionSemanticsTest {

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "ext.subl")
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
    private fun lastVar(hir: HirFile) = stmts(hir).filterIsInstance<HirVarStmt>().last()

    // -------------------- D1：扩展函数登记与桶（TS-11/TS-13） --------------------

    @Test
    fun extFunRegistersAndMethodSugarResolves() {
        val (hir, bag) = script("fun Nat.succ(): Nat = self + 1\n" +
            "var z = 1.succ()")
        assertFalse(bag.hasError, errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as sublimation.frontend.hir.HirNamedType).name)
    }

    @Test
    fun dupExtReportsBothPositions() {
        val (hir, bag) = script("fun Nat.dbl(x: Nat): Nat = x\n" +
            "fun Nat.dbl(x: Nat): Nat = x")
        val dups = bag.diags.filter { it.code == "E-DUP-EXT" }
        assertEquals(2, dups.size, "TS-13：两处位置均报；actual=" + dups.map { it.message })
    }

    @Test
    fun dupExtSameHeadSameNameIgnoresParamTypeList() {
        val (hir, bag) = script("fun Nat.f(x: Nat): Nat = x\n" +
            "fun Nat.f(x: Str): Str = \"s\"")
        val dups = bag.diags.filter { it.code == "E-DUP-EXT" }
        assertEquals(2, dups.size, "(a) t38/ADR-26：同 (接收者头, 名字) 不同形参类型列表 → 禁止重载，两处位置均报；actual=" + errMsgs(bag))
        assertFalse(bag.diags.any { it.code != "E-DUP-EXT" && it.severity == Severity.ERROR }, "不应级联其它错误；actual=" + errMsgs(bag))
    }

    @Test
    fun dupExtSameHeadSameNameIgnoresArity() {
        val (hir, bag) = script("fun Nat.g(x: Nat): Nat = x\n" +
            "fun Nat.g(x: Nat, y: Nat): Nat = x")
        val dups = bag.diags.filter { it.code == "E-DUP-EXT" }
        assertEquals(2, dups.size, "(b) t38：同 (接收者头, 名字) 不同 arity 同样报 E-DUP-EXT（V1 禁止重载）；actual=" + errMsgs(bag))
    }
    @Test
    fun extFunBucketIsolatedFromFreeFuns() {
        val (hir, bag) = script("fun g(x: Nat): Nat = x\n" +
            "fun Nat.g(a: Nat, b: Nat): Nat = a\n" +
            "var z = g(1)")
        assertFalse(bag.hasError, "自由函数与扩展函数同名分桶共存（TS-11 互不遮蔽）；actual=" + errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as sublimation.frontend.hir.HirNamedType).name)
    }

    @Test
    fun obsoleteReceiverHeadWarns() {
        val (hir, bag) = script("fun Natt.foo(): Nat = 1")
        assertTrue(bag.diags.any { it.code == "W-EXT-OBSOLETE" }, "W-EXT-OBSOLETE 未落")
        assertFalse(bag.hasError)
    }

    @Test
    fun receiverTypeVarMustBeInTheory() {
        val (hir, bag) = script("fun List[A].len(): Nat = 0")
        assertTrue(errCodes(bag).contains("E-UNBOUND-NAME"), "TS-15：Receiver 的 A 未在理论参数表声明；actual=" + errMsgs(bag))
        val (hir2, bag2) = script("fun List[A].len[A](): Nat = 0")
        assertFalse(bag2.hasError, "理论参数表声明 A 后无错；actual=" + errMsgs(bag2))
    }

    // -------------------- D2：三种调用形态（TS-12/TS-14/TS-10） --------------------

    @Test
    fun directCallExtOnlyResolvesByArg1Head() {
        val (hir, bag) = script("fun Nat.succ(): Nat = self + 1\n" +
            "var z = succ(1)")
        assertFalse(bag.hasError, "直接调用按实参 1 类型头查扩展桶（TS-14）；actual=" + errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as sublimation.frontend.hir.HirNamedType).name)
    }

    @Test
    fun typeQualifiedCallResolves() {
        val (hir, bag) = script("fun Nat.succ(): Nat = self + 1\n" +
            "var z = Nat::succ(3)")
        assertFalse(bag.hasError, "类型限定 T::f 按给定类型头查扩展桶（TS-12）；actual=" + errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as sublimation.frontend.hir.HirNamedType).name)
    }

    @Test
    fun freeFunWinsOverExtOnDirectCall() {
        val (hir, bag) = script("fun h(x: Nat): Nat = x\n" +
            "fun Nat.h(x: Nat, y: Nat): Nat = x\n" +
            "var z = h(1, 2)")
        assertEquals(listOf("E-ARITY"), errCodes(bag),
            "TS-14 自由函数优先：同名自由函数存在即归自由函数，arity 不符报 E-ARITY 不回退扩展桶")
    }

    @Test
    fun selfBindsToReceiverStaticType() {
        val (hir, bag) = script("fun Nat.double(): Nat = self + self\n" +
            "var z = 3.double()")
        assertFalse(bag.hasError, "self 绑定为接收者静态类型（TS-10）；actual=" + errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as sublimation.frontend.hir.HirNamedType).name)
    }

    @Test
    fun selfWrongTypeReports() {
        val (hir, bag) = script("fun Str.bad(): Nat = self + 1")
        assertTrue(errCodes(bag).contains("E-TYPE-MISMATCH") &&
            bag.diags.any { it.code == "E-TYPE-MISMATCH" && it.message.contains("Str 与 Nat") },
            "self: Str 体内 + 1 → E-TYPE-MISMATCH（不放宽，体兜底错允许级联）；actual=" + errMsgs(bag))
    }

    @Test
    fun selfParamNameClashReports() {
        val (hir, bag) = script("fun Nat.bad(self: Nat): Nat = self")
        assertTrue(errCodes(bag).contains("E-DUP-NAME"), "形参与保留接收者名 self 同名（TS-10）；actual=" + errMsgs(bag))
    }

    // -------------------- D3：结构化满足（TS-17~TS-20/TS-27） --------------------

    @Test
    fun satisfiesFullyNoDiag() {
        // t62（ST-13）：T 出现在形参与返回（不变）+ 带界 → E-BOUND-INVARIANT，故改为单形参形态
        val src = "trait Monoid { fun append(a: Self, b: Self): Self }\n" +
            "fun Nat.append(b: Nat): Nat = self + b\n" +
            "fun use[T: Monoid](a: T): Nat = 0\n" +
            "var z = use(1)"
        val (hir, bag) = script(src)
        assertFalse(bag.hasError, "完全满足 → 无诊断（TS-17/TS-18）；actual=" + errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as sublimation.frontend.hir.HirNamedType).name)
    }

    @Test
    fun missingOneSpecReportsWithList() {
        // t62（ST-13）：单形参形态（去 E-BOUND-INVARIANT 干扰）
        val src = "trait Ring { fun add(a: Self, b: Self): Self ; fun zero(): Self }\n" +
            "fun Nat.add(b: Nat): Nat = self + b\n" +
            "fun use[T: Ring](a: T): Nat = 0\n" +
            "var z = use(1)"
        val (hir, bag) = script(src)
        val codes = errCodes(bag)
        assertEquals(listOf("E-TC-NOT-SATISFIED"), codes, "缺一条规范 → E-TC-NOT-SATISFIED；actual=" + errMsgs(bag))
        val msg = bag.diags.first { it.code == "E-TC-NOT-SATISFIED" }.message
        assertTrue(msg.contains("zero(): Nat"), "TS-20 缺失规范清单含替换后期望签名 zero(): Nat；actual=" + msg)
        assertTrue(msg.contains("Ring"), "清单标注来源特质；actual=" + msg)
    }

    @Test
    fun inheritanceClosureSatisfies() {
        val src = "trait Base { fun baseOp(): Nat }\n" +
            "trait Sub : Base { fun subOp(): Nat }\n" +
            "fun Nat.baseOp(): Nat = 1\n" +
            "fun Nat.subOp(): Nat = 2\n" +
            "fun use[T: Sub](x: T): Nat = 0\n" +
            "var z = use(1)"
        val (hir, bag) = script(src)
        assertFalse(bag.hasError, "继承闭包满足：Sub 与父 Base 规范均由 Nat 扩展覆盖（TS-8/TS-17）；actual=" + errMsgs(bag))
    }

    @Test
    fun selfResidueFailsClosed() {
        val src = "trait U { fun chk(x: Nat | Self): Bool }\n" +
            "fun use[T: U](x: T): Bool = True\n" +
            "var z = use(1)"
        val (hir, bag) = script(src)
        val codes = errCodes(bag)
        assertEquals(listOf("E-TC-NOT-SATISFIED"), codes, "actual=" + errMsgs(bag))
        val msg = bag.diags.first { it.code == "E-TC-NOT-SATISFIED" }.message
        assertTrue(msg.contains("Self"), "TS-27 Self 残留原因入消息；actual=" + msg)
    }

    @Test
    fun forwardReferenceToExtension() {
        // t62（ST-13）：单形参形态（T 不作返回位 → 非不变，界合法）
        val src = "fun use[T: Monoid](a: T): Nat = 0\n" +
            "var z = use(1)\n" +
            "trait Monoid { fun append(a: Self, b: Self): Self }\n" +
            "fun Nat.append(b: Nat): Nat = self + b"
        val (hir, bag) = script(src)
        assertFalse(bag.hasError, "TS-19：判定基于全集，前向引用合法；actual=" + errMsgs(bag))
    }

    // -------------------- D4：约束检查（TS-21~TS-24） --------------------

    @Test
    fun hktConstraintReportsBoundary() {
        val (hir, bag) = script("fun h[F[_]: Show](x: F[Nat]): Nat = 0\ntrait Show { fun show(): Str }")
        assertTrue(errCodes(bag).contains("E-TC-BOUNDARY"), "高阶类型约束（TS-24 V1 边界）；actual=" + errMsgs(bag))
    }

    @Test
    // t62（ST-13 负例/ST-14）：[T: Nat] 界已合法（ST-14）；T 又作形参又作返回 → E-BOUND-INVARIANT（L6）
    fun invariantParamWithBoundReportsInvariant() {
        val (hir, bag) = script("fun k[T: Nat](x: T): T = x\nvar z = k(1)")
        assertTrue(errCodes(bag).contains("E-BOUND-INVARIANT"), "不变参数带界 → E-BOUND-INVARIANT；actual=" + errMsgs(bag))
    }

    @Test
    fun constraintCheckAtCallSiteOnlyWhenSolved() {
        val src = "trait Show { fun show(): Str }\n" +
            "fun Nat.show(): Str = \"n\"\n" +
            "fun use[T: Show](x: T): Str = \"?\"\n" +
            "var z = use(1)"
        val (hir, bag) = script(src)
        assertFalse(bag.hasError, "T:=Nat 且满足 → 无诊断；actual=" + errMsgs(bag))
    }

    // -------------------- D5：Self 与 HIR 保真（TS-25/TS-29） --------------------

    @Test
    fun selfOutsideClassReports() {
        val (hir, bag) = script("fun f(x: Self): Nat = 0")
        assertTrue(errCodes(bag).contains("E-SELF-OUTSIDE-CLASS"), "TS-25：Self 出现在 trait 体外；actual=" + errMsgs(bag))
    }

    @Test
    fun selfInsideClassElaboratesToHirSelfType() {
        val (hir, bag) = script("trait C { fun m(s: Self): Bool }")
        assertFalse(bag.hasError, "trait 体内 Self 合法；actual=" + errMsgs(bag))
        val cls = hir.entries.mapNotNull { (it as? sublimation.frontend.hir.HirDeclEntry)?.decl }
            .filterIsInstance<HirTraitDecl>().first()
        val memberParam = cls.members.first().params.first()
        assertTrue(memberParam.type is HirSelfType,
            "TS-29：规范内 Self 以 HirSelfType 表示（不替换进 HIR 树本体）；actual=" + memberParam.type)
    }
    @Test
    fun diffHeadsSameNameCoexistLegally() {
        val (hir, bag) = script("fun Nat.h(): Nat = 1\n" +
            "fun Str.h(): Str = \"s\"")
        assertFalse(bag.hasError, "(c) t38：不同接收者头同名扩展合法共存（Nat.h 与 Str.h）；actual=" + errMsgs(bag))
    }

    @Test
    fun tupleReceiverRegistersAndSugarLookupsByTupleArity() {
        val (hir, bag) = script("fun (Nat, Nat).add(): Nat = 1\n" +
            "var z = (1, 2).add()")
        assertFalse(bag.hasError, "元组接收者 (Nat,Nat) → 键 Tuple/2：方法糖按元数查桶；actual=" + errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as sublimation.frontend.hir.HirNamedType).name)
    }

    @Test
    fun tupleReceiverDirectCallLooksUpByArgHead() {
        val (hir, bag) = script("fun (Nat, Nat).add(): Nat = 1\n" +
            "var z = add((1, 2))")
        assertFalse(bag.hasError, "直接调用 add((1,2))：实参 1 静态类型为元组 → 头 Tuple/2 命中；actual=" + errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as sublimation.frontend.hir.HirNamedType).name)
    }

    @Test
    fun funTypeReceiverMethodSugarOnFunctionTypedParam() {
        val src = "fun ((Nat) => Nat).tag(): Nat = 1\n" +
            "fun Nat.id(x: Nat): Nat = x\n" +
            "fun use(fn: (Nat) => Nat): Nat = fn.tag()\n" +
            "fun id2(x: Nat): Nat = x\n" +
            "var z = use(id2)"
        val (hir, bag) = script(src)
        assertFalse(bag.hasError, "函数类型接收者 (Nat)=>Nat → 键 Fun/1：方法糖按实参静态类型查桶；actual=" + errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as sublimation.frontend.hir.HirNamedType).name)
    }

    @Test
    fun distinctTupleAritiesAreDistinctHeads() {
        val (hir, bag) = script("fun (Nat, Nat).two(): Nat = 2\n" +
            "fun (Nat, Nat, Nat).three(): Nat = 3\n" +
            "var z = (1, 2).two()")
        assertFalse(bag.hasError, "Tuple/2 与 Tuple/3 是不同头：同键才唯一性，不同头同名合法；actual=" + errMsgs(bag))
        assertEquals("Nat", (lastVar(hir).type as sublimation.frontend.hir.HirNamedType).name)
    }
}
