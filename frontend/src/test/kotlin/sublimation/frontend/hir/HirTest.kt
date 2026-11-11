package sublimation.frontend.hir

import sublimation.frontend.diag.NodeId
import sublimation.frontend.diag.SourcePos
import sublimation.frontend.diag.SourceRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * HIR 节点体系测试（S-1 §3；ADR-1 §3.1~§3.4）。
 *
 * 齐备性判据两层（同 t3 双判据）：
 * 1. 编译期——各 sealed 层级写**无 else 的穷尽 when**（sealed 穷尽性 = 编译期齐备性证明）。
 * 2. 运行期——样本构造集与期望名集逐层级对账。
 *
 * 另覆盖：五件套（nid/origin/notes + 各面 symbol/type）、P-STRUCT-EQ 显式结构相等、
 * 证明义务节点字段、文件结构与条目保序、解析期留痕、不可变复制构造。
 */
class HirTest {
    private fun pos(line: Int) = SourceRange(SourcePos(line, 1), null)
    private fun int1(nid: NodeId, line: Int) = HirIntLit("1", nid, pos(line))
    private fun atom(nid: NodeId, line: Int) =
        HirPAtom("<=", listOf(HirTDollar(nid + 1, pos(line)), HirTInt("1", nid + 2, origin = pos(line))), nid, pos(line))
    private fun nat(nid: NodeId, line: Int) = HirNamedType("Nat", nid = nid, origin = pos(line))

    // ---------- 穷尽名映射（sealed 穷尽性 = 编译期齐备性证明）----------

    private fun declName(d: HirDecl): String = when (d) {
        is HirFunDecl -> "fun"
        is HirTraitDecl -> "trait"
        is HirStructDecl -> "struct"
        is HirTypeAlias -> "typealias"
        is HirErrorDecl -> "error"
        is HirTraitConst -> "traitconst"   // P-8/B（TS-6②）；P-9 后无喂养路径，节点保留
    }

    private fun stmtName(s: HirStmt): String = when (s) {
        is HirVarStmt -> "var"
        is HirAssign -> "assign"
        is HirAxiom -> "axiom"
        is HirByStmt -> "by"
        is HirUnchecked -> "unchecked"
        is HirExprStmt -> "expr"
        is HirBecause -> "because"
        is HirWhenProp -> "when[p]"
        is HirIfProp -> "if[p]"
        is HirExistsIntro -> "exists-intro"
        is HirExistsElim -> "exists-elim"
        is HirLocalFun -> "local-fun"
        is HirStrategy -> "strategy"
        is HirReturn -> "return"
        is HirErrorStmt -> "error"
    }

    private fun exprName(e: HirExpr): String = when (e) {
        is HirIntLit -> "int"
        is HirFloatLit -> "float"
        is HirStrLit -> "str"
        is HirTop -> "top"      // t20 程序面 Bool 字面量（ADR-19 §C1）
        is HirBot -> "bot"      // 同上
        is HirBinExpr -> "bin"      // t7 契约补绘：程序面双目运算
        is HirUniExpr -> "uni"      // t7 契约补绘：程序面一元运算
        is HirBorrow -> "borrow"    // t101：&v 借用（零语义中间态）
        is HirNameRef -> "name"
        is HirCall -> "call"
        is HirField -> "field"
        is HirFunRef -> "ref"
        is HirTypeTest -> "is"
        is HirTask -> "task"
        is HirFlow -> "flow"
        is HirInst -> "inst"
        is HirLambda -> "lambda"
        is HirAnonFun -> "anon-fun"
        is HirTuple -> "tuple"
        is HirStructCtor -> "struct-ctor"
        is HirByExpr -> "by-expr"
        is HirVarExpr -> "var-expr"
        is HirBlock -> "block"
        is HirIf -> "if"
        is HirWhen -> "when"
        is HirErrorExpr -> "error"
    }

    private fun propName(p: HirProp): String = when (p) {
        is HirPTop -> "top"
        is HirPBot -> "bot"
        is HirPNot -> "not"
        is HirPAnd -> "and"
        is HirPOr -> "or"
        is HirPImp -> "imp"
        is HirPIff -> "iff"
        is HirPForall -> "forall"
        is HirPExists -> "exists"
        is HirPExists1 -> "exists1"
        is HirPAtom -> "atom"
    }

    private fun termName(t: HirTerm): String = when (t) {
        is HirTInt -> "int"
        is HirTStr -> "str"
        is HirTName -> "name"
        is HirTDollar -> "dollar"
        is HirTParamRef -> "param-ref"
        is HirTTop -> "top"
        is HirTBot -> "bot"
        is HirTBin -> "bin"
        is HirTUni -> "uni"
        is HirTCall -> "call"
        is HirTInst -> "inst"
        is HirTField -> "field"
        is HirTSymbolCall -> "symbol-call"
    }

    private fun typeName(t: HirType): String = when (t) {
        is HirNamedType -> "named"
        is HirFunType -> "fun"
        is HirTupleType -> "tuple"
        is HirMutType -> "mut"
        is HirAtomicType -> "atomic"
        is HirRefType -> "ref"   // t95
        is HirUnionType -> "union"
        is HirQualifiedType -> "qualified"
        is HirSelfType -> "self"  // P-8/A：卷 TS-29 新增节点（被动穷尽补支）
        is HirErrorType -> "error"
    }

    private fun patternName(p: HirPattern): String = when (p) {
        is HirPatLit -> "lit"
        is HirPatCtor -> "ctor"
        is HirPatTuple -> "tuple"
        is HirPatIs -> "is"
        is HirPatBind -> "bind"
        is HirPatElse -> "else"
        is HirPatCond -> "cond"
        is HirErrorPattern -> "error"
    }

    // ---------- 声明层齐备（S-5~S-14 + 容错镜像）----------

    @Test
    fun 声明层级齐备() {
        val decls: List<HirDecl> = listOf(
            HirFunDecl("f", nid = 1, origin = pos(1)),
            HirTraitDecl("C", nid = 2, origin = pos(2)),
            HirStructDecl("S", nid = 5, origin = pos(4)),
            HirTypeAlias("T", target = nat(7, 6), nid = 8, origin = pos(6)),
            HirErrorDecl("解析失败", nid = 10, origin = pos(8)),
        )
        // P-9（t40）：HirEnumDecl 随 enum 废除移除；HirPropDef 随 def 废除移除（t41）
        assertEquals(setOf("fun", "trait", "struct", "typealias", "error"),
            decls.map(::declName).toSet())
    }

    // ---------- 语句层齐备（S-16~S-30 + 证明义务节点 + 容错镜像）----------

    @Test
    fun 语句层级齐备() {
        val stmts: List<HirStmt> = listOf(
            HirVarStmt("x", value = int1(1, 1), nid = 2, origin = pos(1)),
            HirAssign(HirNameRef("x", nid = 3, origin = pos(2)), int1(4, 2), nid = 5, origin = pos(2)),
            HirAxiom(listOf(atom(6, 3)), nid = 7, origin = pos(3)),
            HirByStmt(listOf(atom(8, 4)), nid = 9, origin = pos(4)),
            HirUnchecked(HirExprStmt(int1(10, 5), nid = 11, origin = pos(5)), nid = 12, origin = pos(5)),
            HirExprStmt(int1(13, 6), nid = 14, origin = pos(6)),
            HirBecause(atom(15, 7), listOf(atom(16, 7)), atom(17, 7), nid = 18, origin = pos(7)),
            HirWhenProp(
                atom(19, 8),
                listOf(HirWhenPropArm(atom(20, 8), listOf(HirExprStmt(int1(21, 8), nid = 22, origin = pos(8))), nid = 23, origin = pos(8))),
                nid = 24, origin = pos(8),
            ),
            HirIfProp(atom(25, 9), listOf(HirExprStmt(int1(26, 9), nid = 27, origin = pos(9))), nid = 28, origin = pos(9)),
            HirExistsIntro(listOf("y"), listOf(int1(29, 10)), atom(30, 10), nid = 31, origin = pos(10)),
            HirExistsElim("y", atom(32, 11), nid = 33, origin = pos(11)),
            HirLocalFun(HirFunDecl("g", nid = 34, origin = pos(12)), nid = 35, origin = pos(12)),
            HirStrategy("ring", atom(36, 13), nid = 37, origin = pos(13)),
            HirReturn(int1(38, 14), nid = 39, origin = pos(14)),
            HirErrorStmt("解析失败", nid = 40, origin = pos(15)),
        )
        assertEquals(
            setOf("var", "assign", "axiom", "by", "unchecked", "expr", "because", "when[p]", "if[p]", "exists-intro", "exists-elim", "local-fun", "strategy", "return", "error"),
            stmts.map(::stmtName).toSet(),
        )
    }

    // ---------- 表达式层齐备（S-31~S-37 + 程序面扩展节点）----------

    @Test
    fun 表达式层级齐备() {
        val block = HirBlock(nid = 1, origin = pos(1))
        val exprs: List<HirExpr> = listOf(
            int1(2, 1),
            HirFloatLit("1.5", nid = 3, origin = pos(1)),
            HirStrLit("s", nid = 4, origin = pos(1)),
            HirNameRef("x", nid = 5, origin = pos(1)),
            HirCall(HirNameRef("f", nid = 6, origin = pos(1)), emptyList(), nid = 7, origin = pos(1)),
            HirField(HirNameRef("o", nid = 8, origin = pos(1)), "a", nid = 9, origin = pos(1)),
            HirFunRef(HirNameRef("o", nid = 10, origin = pos(1)), "f", nid = 11, origin = pos(1)),
            HirTypeTest(HirNameRef("o", nid = 12, origin = pos(1)), nat(13, 1), nid = 14, origin = pos(1)),
            HirTask(block, nid = 15, origin = pos(1)),
            HirFlow(block, nid = 16, origin = pos(1)),
            HirInst(HirNameRef("w", nid = 17, origin = pos(1)), emptyList(), nid = 18, origin = pos(1)),
            HirLambda(body = int1(19, 1), nid = 20, origin = pos(1)),
            HirAnonFun("_", body = int1(21, 1), nid = 22, origin = pos(1)),
            HirTuple(emptyList(), nid = 23, origin = pos(1)),
            HirStructCtor("S", emptyList(), nid = 24, origin = pos(1)),
            HirByExpr(int1(25, 1), listOf(atom(26, 1)), nid = 27, origin = pos(1)),
            HirVarExpr("x", value = int1(28, 1), nid = 29, origin = pos(1)),
            block,
            HirIf(int1(30, 1), HirBlock(nid = 31, origin = pos(1)), nid = 32, origin = pos(1)),
            HirWhen(nid = 33, origin = pos(1)),
            HirErrorExpr("解析失败", nid = 34, origin = pos(1)),
        )
        assertEquals(21, exprs.size)
        assertEquals(
            setOf("int", "float", "str", "name", "call", "field", "ref", "is", "task", "flow", "inst", "lambda", "anon-fun", "tuple", "struct-ctor", "by-expr", "var-expr", "block", "if", "when", "error"),
            exprs.map(::exprName).toSet(),
        )
    }

    // ---------- 命题面齐备（S-38~S-39；与程序面分家，S-1 §3）----------

    @Test
    fun 命题层级齐备() {
        val a = atom(1, 1)
        val props: List<HirProp> = listOf(
            HirPTop(nid = 1, origin = pos(1)),
            HirPBot(nid = 2, origin = pos(1)),
            HirPNot(a, nid = 3, origin = pos(1)),
            HirPAnd(HirPTop(nid = 4, origin = pos(1)), a, nid = 5, origin = pos(1)),
            HirPOr(HirPBot(nid = 6, origin = pos(1)), a, nid = 7, origin = pos(1)),
            HirPImp(a, a, nid = 8, origin = pos(1)),
            HirPIff(a, a, nid = 9, origin = pos(1)),
            HirPForall(listOf("x"), a, nid = 10, origin = pos(1)),
            HirPExists(listOf("x"), a, nid = 11, origin = pos(1)),
            HirPExists1(listOf("x"), a, nid = 12, origin = pos(1)),
            a,
        )
        assertEquals(
            setOf("top", "bot", "not", "and", "or", "imp", "iff", "forall", "exists", "exists1", "atom"),
            props.map(::propName).toSet(),
        )
    }

    @Test
    fun 命题项层级齐备() {
        val n = HirTName("x", nid = 1, origin = pos(1))
        val i = HirTInt("1", nid = 2, origin = pos(1))
        val terms: List<HirTerm> = listOf(
            i,
            HirTStr("s", nid = 3, origin = pos(1)),
            n,
            HirTDollar(nid = 4, origin = pos(1)),
            HirTParamRef(0, nid = 5, origin = pos(1)),
            HirTTop(nid = 6, origin = pos(1)),
            HirTBot(nid = 7, origin = pos(1)),
            HirTBin("+", n, i, nid = 8, origin = pos(1)),
            HirTUni("-", n, nid = 9, origin = pos(1)),
            HirTCall(n, listOf(i), nid = 10, origin = pos(1)),
            HirTInst(n, listOf(i), nid = 11, origin = pos(1)),
            HirTField(n, "a", nid = 12, origin = pos(1)),
            HirTSymbolCall("<", listOf(n, i), nid = 13, origin = pos(1)),
        )
        assertEquals(13, terms.size)
        assertEquals(
            setOf("int", "str", "name", "dollar", "param-ref", "top", "bot", "bin", "uni", "call", "inst", "field", "symbol-call"),
            terms.map(::termName).toSet(),
        )
    }

    // ---------- 类型层齐备（S-15 + 容错镜像）----------

    @Test
    fun 类型层级齐备() {
        val types: List<HirType> = listOf(
            nat(1, 1),
            HirFunType(emptyList(), nat(2, 1), nid = 3, origin = pos(1)),
            HirTupleType(emptyList(), nid = 4, origin = pos(1)),
            HirMutType(nat(5, 1), nid = 6, origin = pos(1)),
            HirAtomicType(nat(7, 1), nid = 8, origin = pos(1)),
            HirUnionType(emptyList(), nid = 9, origin = pos(1)),
            HirQualifiedType(listOf("M"), "T", nid = 10, origin = pos(1)),
            HirErrorType("解析失败", nid = 11, origin = pos(1)),
        )
        assertEquals(
            setOf("named", "fun", "tuple", "mut", "atomic", "union", "qualified", "error"),
            types.map(::typeName).toSet(),
        )
    }

    // ---------- 模式层齐备（S-42 + 容错镜像）----------

    @Test
    fun 模式层级齐备() {
        val pats: List<HirPattern> = listOf(
            HirPatLit(int1(1, 1), nid = 2, origin = pos(1)),
            HirPatCtor("S", emptyList(), nid = 3, origin = pos(1)),
            HirPatTuple(emptyList(), nid = 4, origin = pos(1)),
            HirPatIs(nat(5, 1), "x", nid = 6, origin = pos(1)),
            HirPatBind("x", nid = 7, origin = pos(1)),
            HirPatElse(nid = 8, origin = pos(1)),
            HirPatCond(nid = 9, origin = pos(1)),
            HirErrorPattern("解析失败", nid = 10, origin = pos(1)),
        )
        assertEquals(
            setOf("lit", "ctor", "tuple", "is", "bind", "else", "cond", "error"),
            pats.map(::patternName).toSet(),
        )
    }

    // ---------- 五件套（ADR-1 §3.1.4）----------

    @Test
    fun 节点五件套齐备() {
        val node = HirFunDecl(
            name = "bounded",
            nid = 7, origin = pos(3),
            notes = listOf(HirNote(HirNoteKind.DESUGAR, "D-SUGAR", "方法糖改写", pos(3), "xs.f(a)", "f(xs,a)")),
            params = listOf(HirParam("n", nat(8, 3), nid = 9, origin = pos(3))),
        )
        assertEquals(7, node.nid)
        assertEquals(SourcePos(3, 1), node.origin.start)
        assertEquals(1, node.notes.size)
        assertEquals(HirNoteKind.DESUGAR, node.notes[0].kind)
        assertEquals("xs.f(a)", node.notes[0].from)
        assertEquals("f(xs,a)", node.notes[0].to)
        // 默认 notes 为空（未携带留痕的节点）
        assertEquals(emptyList<HirNote>(), HirPTop(nid = 1, origin = pos(1)).notes)
        // 推导/解析未到位时为 null 而非缺席（S-1 §2 容错：半成品落树）
        assertNull(HirNameRef("x", nid = 1, origin = pos(1)).symbol)
    }

    @Test
    fun 程序面结论字段齐备() {
        // 使用点推导类型 + 名称解析结论（ADR-1 §3.3 #4/#5）
        val resolved = HirNameRef("x", SymbolRef(1, SymbolKind.LOCAL, "x"), nat(2, 1), nid = 3, origin = pos(1))
        assertEquals(SymbolKind.LOCAL, resolved.symbol?.kind)
        assertEquals("Nat", (resolved.type as HirNamedType).name)
        // 调用解析结论（ADR-1 §3.3 #10）
        val call = HirCall(
            HirNameRef("map", nid = 4, origin = pos(1)), listOf(HirNameRef("xs", nid = 5, origin = pos(1))),
            symbol = SymbolRef(2, SymbolKind.FUN, "map"), dictionary = "Ord", slot = "s0",
            dictArgs = listOf("Ord"), methodSugar = true, awaitPoint = true, calleeImpure = true,
            nid = 6, origin = pos(1),
        )
        assertEquals("Ord", call.dictionary)
        assertEquals("s0", call.slot)
        assertTrue(call.methodSugar && call.awaitPoint && call.calleeImpure)
        // 效应结论（ADR-1 §3.3 #12）
        val impure = HirFunDecl("f", purity = HirPurity.IMPURE, async = true, nid = 7, origin = pos(1))
        assertEquals(HirPurity.IMPURE, impure.purity)
        assertTrue(impure.async)
        // 控制流与穷尽性判据（ADR-1 §3.3 #9/#11）
        val w = HirWhen(diverges = true, exhaustive = true, nid = 8, origin = pos(1))
        assertTrue(w.diverges && w.exhaustive)
        // 可变视图深拷贝点位 + 可变性核对结论（ADR-1 §3.3 #13）
        assertTrue(HirVarStmt("x", value = int1(9, 1), deepCopy = true, nid = 10, origin = pos(1)).deepCopy)
        assertTrue(HirAssign(HirNameRef("x", nid = 11, origin = pos(1)), int1(12, 1), mutableTarget = true, nid = 13, origin = pos(1)).mutableTarget)
    }

    // ---------- P-STRUCT-EQ：命题面结构相等显式忽略身份元数据 ----------

    @Test
    fun 命题面结构相等忽略身份元数据() {
        val a = atom(1, 1)
        val aAlt = HirPAtom(
            "<=", listOf(HirTDollar(nid = 99, origin = pos(9)), HirTInt("1", nid = 98, origin = pos(9))),
            nid = 97, origin = pos(9),
            notes = listOf(HirNote(HirNoteKind.HINT, "H-X", "提示", pos(9))),
        )
        assertEquals(a, aAlt)
        assertEquals(a.hashCode(), aAlt.hashCode())
        // 头不同即不同命题
        assertNotEquals(a, HirPAtom("<", a.terms, nid = 1, origin = pos(1)))
        // 嵌套结构相等仍忽略身份
        assertEquals(HirPImp(a, a, nid = 1, origin = pos(1)), HirPImp(aAlt, aAlt, nid = 2, origin = pos(2)))
        // 量词变量名顺序敏感
        assertNotEquals(
            HirPForall(listOf("x", "y"), a, nid = 1, origin = pos(1)),
            HirPForall(listOf("y", "x"), a, nid = 2, origin = pos(2)),
        )
        // 命题项名字的解析结论不参与结构相等（旧 termEq 同构）
        assertEquals(HirTName("x", SymbolRef(1, SymbolKind.PARAM, "x"), nid = 1, origin = pos(1)), HirTName("x", nid = 2, origin = pos(2)))
        // 命题面与程序面无共同节点形态：HirProp 与 HirExpr 为 sealed HirNode 下的不相交层级，
        // 编译器即证明（此分家由层级定义保证，无需运行期断言）
    }

    // ---------- 证明义务节点（ADR-1 §3.4）----------

    @Test
    fun 证明义务节点字段齐备() {
        val obligations = listOf(
            HirAxiom(listOf(atom(1, 1)), nid = 2, origin = pos(1)),
            HirByStmt(listOf(atom(3, 1)), nid = 4, origin = pos(1)),
            HirBecause(atom(5, 1), emptyList(), atom(6, 1), nid = 7, origin = pos(1)),
            HirWhenProp(atom(8, 1), emptyList(), nid = 9, origin = pos(1)),
            HirIfProp(atom(10, 1), emptyList(), nid = 11, origin = pos(1)),
            HirExistsIntro(listOf("x"), emptyList(), atom(12, 1), nid = 13, origin = pos(1)),
            HirExistsElim("x", atom(14, 1), nid = 15, origin = pos(1)),
            HirStrategy("ring", atom(16, 1), nid = 17, origin = pos(1)),
            HirByExpr(int1(18, 1), listOf(atom(19, 1)), nid = 20, origin = pos(1)),
        )
        obligations.forEach { assertIs<HirProofObligation>(it) }
        // 默认：上下文空、规则与结论未核对
        obligations.forEach {
            assertEquals(emptyList<HirProp>(), it.proofContext.facts)
            assertNull(it.rule)
            assertNull(it.verdict)
        }
        // 核对完成态：规则 + 结论 + 匹配定理（∵∴ 定理空间）
        val checked = HirStrategy(
            "ring", atom(1, 1),
            proofContext = HirProofContext(listOf(atom(1, 1)), SymbolRef(0, SymbolKind.THEOREM, "T")),
            rule = "R-MP",
            verdict = HirVerdict(HirProofStatus.PROVEN),
            nid = 2, origin = pos(1),
        )
        assertEquals("R-MP", checked.rule)
        assertEquals(HirProofStatus.PROVEN, checked.verdict?.status)
        assertEquals(SymbolKind.THEOREM, checked.proofContext.matched?.kind)
        // 证不出即失败结论（R-NO-GUESS：Γ ⊬ P 不降级成立）
        val failed = HirAxiom(
            listOf(atom(1, 1)),
            verdict = HirVerdict(HirProofStatus.FAILED, "E-PROOF"),
            nid = 2, origin = pos(1),
        )
        assertEquals(HirProofStatus.FAILED, failed.verdict?.status)
        assertEquals("E-PROOF", failed.verdict?.diagCode)
    }

    // ---------- 文件结构（S-1 §3 保序映射）----------

    @Test
    fun 文件条目保序且携带身份() {
        val funDecl = HirFunDecl("f", nid = 1, origin = pos(1))
        val stmt = HirExprStmt(int1(2, 2), nid = 3, origin = pos(2))
        val file = HirFile(
            nid = 0, origin = SourceRange(SourcePos(1, 1), SourcePos(2, 11)),
            entries = listOf(HirDeclEntry(funDecl), HirStmtEntry(stmt)),
        )
        assertEquals(0, file.nid)
        assertEquals(SourcePos(2, 11), file.origin.end)
        val first = file.entries[0]
        assertIs<HirDeclEntry>(first)
        assertEquals(1, first.decl.nid)
        val second = file.entries[1]
        assertIs<HirStmtEntry>(second)
        assertEquals(3, second.stmt.nid)
        // 辅助记录五件套
        val arm = HirWhenArm(HirPatElse(nid = 4, origin = pos(1)), body = int1(5, 1), nid = 6, origin = pos(1))
        assertEquals(6, arm.nid)
        assertNotNull(arm.pattern.origin)
    }

    // ---------- 不可变性（全 val，改造走复制构造）----------

    @Test
    fun 节点不可变_改造走复制构造() {
        val before = HirVarStmt("x", value = int1(1, 1), nid = 2, origin = pos(1))
        val after = before.copy(name = "y")
        assertEquals("x", before.name)
        assertEquals("y", after.name)
        // 复制不改旧树子节点（复制构造不突变，ADR-1 §3.1 病根对策）
        assertSame(before.value, after.value)
    }

    @Test
    fun 块尾为结构性投影() {
        assertNull(HirBlock(nid = 1, origin = pos(1)).tailExpr)
        val block = HirBlock(listOf(HirExprStmt(int1(2, 1), nid = 3, origin = pos(1))), nid = 1, origin = pos(1))
        assertIs<HirIntLit>(block.tailExpr)
        assertEquals("1", block.tailExpr?.let { (it as HirIntLit).value })
    }
}
