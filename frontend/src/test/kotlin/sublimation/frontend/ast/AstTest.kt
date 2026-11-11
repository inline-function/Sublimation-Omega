package sublimation.frontend.ast

import sublimation.frontend.diag.SourcePos
import sublimation.frontend.diag.SourceRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * AST 节点体系测试（S-1 §2 + 《形式化文法》§2）。
 *
 * 齐备性判据有两层：
 * 1. 编译期——[declName]/[stmtName]/[exprName]/[patternName]/[typeName] 对各 sealed 层级
 *    写**无 else 的穷尽 when**：新增节点不补分支即编译失败（sealed 穷尽性）。
 * 2. 运行期——样本构造集与期望名集逐层级断言（缺节点即名单不齐）。
 */
class AstTest {
    private fun pos(line: Int) = SourceRange(SourcePos(line, 1), null)
    private fun lit(line: Int) = IntLit("1", pos(line))
    private fun name(line: Int, s: String = "x") = NameRef(s, pos(line))
    private fun nat(line: Int) = NamedType("Nat", emptyList(), pos(line))
    private fun exprStmt(line: Int) = ExprStmt(lit(line), pos(line))
    private fun block(line: Int) = BlockExpr(emptyList(), pos(line))

    // ---------- 穷尽名映射（sealed 穷尽性 = 编译期齐备性证明）----------

    private fun nodeName(n: AstNode): String = when (n) {
        is Decl -> declName(n)
        is Stmt -> stmtName(n)
        is Expr -> exprName(n)
        is Pattern -> patternName(n)
        is Type -> typeName(n)
    }

    private fun declName(d: Decl): String = when (d) {
        is FunDecl -> "FunDecl"
        is TraitDecl -> "TraitDecl"
        is StructDecl -> "StructDecl"
        is TypeAliasDecl -> "TypeAliasDecl"
        is ErrorDecl -> "ErrorDecl"
    }

    private fun stmtName(s: Stmt): String = when (s) {
        is VarStmt -> "VarStmt"
        is AssignStmt -> "AssignStmt"
        is AxiomStmt -> "AxiomStmt"
        is ByStmt -> "ByStmt"
        is UncheckedStmt -> "UncheckedStmt"
        is ExprStmt -> "ExprStmt"
        is BTSStmt -> "BTSStmt"
        is WhenPropStmt -> "WhenPropStmt"
        is StrategyStmt -> "StrategyStmt"
        is ExistIntroStmt -> "ExistIntroStmt"
        is ExistsElimStmt -> "ExistsElimStmt"
        is FunStmt -> "FunStmt"
        is IfPropStmt -> "IfPropStmt"
        is ReturnStmt -> "ReturnStmt"
        is ErrorStmt -> "ErrorStmt"
    }

    private fun exprName(e: Expr): String = when (e) {
        is IntLit -> "IntLit"
        is FloatLit -> "FloatLit"
        is StrLit -> "StrLit"
        is NameRef -> "NameRef"
        is ReturnSym -> "ReturnSym"
        is ParamRefExpr -> "ParamRefExpr"
        is TopExpr -> "TopExpr"
        is BotExpr -> "BotExpr"
        is BinExpr -> "BinExpr"
        is UniExpr -> "UniExpr"
        is BorrowExpr -> "BorrowExpr"   // t101
        is TypeTestExpr -> "TypeTestExpr"
        is IsPatExpr -> "IsPatExpr"
        is TaskExpr -> "TaskExpr"
        is FlowExpr -> "FlowExpr"
        is CallExpr -> "CallExpr"
        is InstExpr -> "InstExpr"
        is FieldExpr -> "FieldExpr"
        is FunRefExpr -> "FunRefExpr"
        is SymbolCallExpr -> "SymbolCallExpr"
        is LambdaExpr -> "LambdaExpr"
        is TupleExpr -> "TupleExpr"
        is StructCtorExpr -> "StructCtorExpr"
        is ByExpr -> "ByExpr"
        is VarExpr -> "VarExpr"
        is AnonFunExpr -> "AnonFunExpr"
        is BlockExpr -> "BlockExpr"
        is IfExpr -> "IfExpr"
        is WhenExpr -> "WhenExpr"
        is QuantExpr -> "QuantExpr"
        is ErrorExpr -> "ErrorExpr"
    }

    private fun patternName(p: Pattern): String = when (p) {
        is PatLit -> "PatLit"
        is PatCtor -> "PatCtor"
        is PatTuple -> "PatTuple"
        is PatIs -> "PatIs"
        is PatBind -> "PatBind"
        is PatElse -> "PatElse"
        is PatCond -> "PatCond"
        is ErrorPattern -> "ErrorPattern"
    }

    private fun typeName(t: Type): String = when (t) {
        is NamedType -> "NamedType"
        is FunType -> "FunType"
        is TupleType -> "TupleType"
        is MutType -> "MutType"
        is AtomicType -> "AtomicType"
        is RefType -> "RefType"   // t95
        is UnionType -> "UnionType"
        is QualifiedType -> "QualifiedType"
        is ErrorType -> "ErrorType"
    }

    // ---------- 样本构造（每层级一个节点；顶层节点 pos 注入行号 = 序号+1，子节点同行）----------

    private fun declSamples(): List<Decl> = listOf(
        FunDecl("f", emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList(), null, emptyList(), pos(1)),
        TraitDecl("C", emptyList(), emptyList(), emptyList(), emptyList(), pos(2)),
        StructDecl("S", emptyList(), emptyList(), emptyList(), emptyList(), pos(3)),
        TypeAliasDecl("T", emptyList(), nat(4), pos(4)),
        ErrorDecl("语法错", pos(5)),
    )

    private fun stmtSamples(): List<Stmt> = listOf(
        VarStmt("n", emptyList(), null, lit(1), null, pos(1)),
        AssignStmt(name(2), lit(2), pos(2)),
        AxiomStmt(listOf(lit(3)), pos(3)),
        ByStmt(listOf(lit(4)), pos(4)),
        UncheckedStmt(exprStmt(5), pos(5)),
        exprStmt(6),
        BTSStmt(lit(7), listOf(lit(7)), lit(7), pos(7)),
        WhenPropStmt(lit(8), listOf(WhenPropArm(lit(8), listOf(exprStmt(8)), pos(8))), pos(8)),
        StrategyStmt("simp", lit(9), pos(9)),
        ExistIntroStmt(listOf("x"), listOf(lit(10)), lit(10), pos(10)),
        ExistsElimStmt("x", lit(11), listOf(exprStmt(11)), pos(11)),
        FunStmt(FunDecl("g", emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList(), null, emptyList(), pos(12)), pos(12)),
        IfPropStmt(lit(13), listOf(exprStmt(13)), listOf(exprStmt(13)), pos(13)),
        ReturnStmt(lit(14), null, pos(14)),
        ErrorStmt("语法错", pos(15)),
    )

    private fun exprSamples(): List<Expr> = listOf(
        IntLit("42", pos(1)),
        FloatLit("1.0", pos(2)),
        StrLit("s", pos(3)),
        NameRef("f", pos(4)),
        ReturnSym(pos(5)),
        ParamRefExpr(1, pos(6)),
        TopExpr(pos(7)),
        BotExpr(pos(8)),
        BinExpr("+", lit(9), lit(9), pos(9)),
        UniExpr("-", lit(10), pos(10)),
        TypeTestExpr(name(11), nat(11), pos(11)),
        IsPatExpr(name(12), PatBind("x", pos(12)), pos(12)),
        TaskExpr(block(13), pos(13)),
        FlowExpr(block(14), pos(14)),
        CallExpr(name(15), listOf(lit(15)), mapOf("k" to lit(15)), pos(15)),
        InstExpr(name(16), listOf(lit(16)), pos(16)),
        FieldExpr(name(17), "f", pos(17)),
        FunRefExpr(name(18), "f", pos(18)),
        SymbolCallExpr("<", listOf(lit(19)), pos(19)),
        LambdaExpr(listOf("a"), lit(20), implicitIt = false, pos(20)),
        TupleExpr(listOf(lit(21), lit(21)), pos(21)),
        StructCtorExpr("A", listOf("f" to lit(22)), pos(22)),
        ByExpr(name(23), listOf(lit(23)), pos(23)),
        VarExpr("n", emptyList(), null, lit(24), pos(24)),
        AnonFunExpr("_", emptyList(), emptyList(), emptyList(), null, emptyList(), null, pos(25)),
        BlockExpr(listOf(exprStmt(26)), pos(26)),
        IfExpr(lit(27), block(27), null, pos(27)),
        WhenExpr(null, null, listOf(WhenArm(PatElse(pos(28)), null, lit(28), null, pos(28))), pos(28)),
        QuantExpr("∀", listOf("a"), lit(29), pos(29)),
        ErrorExpr("语法错", pos(30)),
    )

    private fun patternSamples(): List<Pattern> = listOf(
        PatLit(lit(1), pos(1)),
        PatCtor("Some", listOf(PatBind("x", pos(2))), pos(2)),
        PatTuple(listOf(PatBind("a", pos(3)), PatBind("b", pos(3))), pos(3)),
        PatIs(nat(4), null, pos(4)),
        PatBind("_", pos(5)),
        PatElse(pos(6)),
        PatCond(pos(7)),
        ErrorPattern("语法错", pos(8)),
    )

    private fun typeSamples(): List<Type> = listOf(
        NamedType("Nat", emptyList(), pos(1)),
        FunType(listOf(nat(2)), nat(2), theory = emptyList(), context = null, pos = pos(2)),
        TupleType(listOf(NamedType("A", emptyList(), pos(3)), NamedType("B", emptyList(), pos(3))), pos(3)),
        MutType(NamedType("T", emptyList(), pos(4)), pos(4)),
        AtomicType(NamedType("T", emptyList(), pos(5)), pos(5)),
        UnionType(listOf(NamedType("A", emptyList(), pos(6)), NamedType("B", emptyList(), pos(6))), pos(6)),
        QualifiedType(listOf("core"), "Point", emptyList(), pos(7)),
        ErrorType("语法错", pos(8)),
    )

    // ---------- 齐备性：期望名集逐层级对账（《形式化文法》§2）----------

    @Test
    fun 声明层齐备_S5_S14() {
        // P-9（t40）：EnumDecl/PropDefDecl 随 enum/def 废除——层级只剩四声明 + 错误占位
        val expected = listOf(
            "FunDecl", "TraitDecl", "StructDecl", "TypeAliasDecl", "ErrorDecl",
        )
        assertEquals(expected, declSamples().map { declName(it) })
    }

    @Test
    fun 语句层齐备_S16_S30() {
        val expected = listOf(
            "VarStmt", "AssignStmt", "AxiomStmt", "ByStmt", "UncheckedStmt", "ExprStmt",
            "BTSStmt", "WhenPropStmt", "StrategyStmt", "ExistIntroStmt", "ExistsElimStmt",
            "FunStmt", "IfPropStmt", "ReturnStmt", "ErrorStmt",
        )
        assertEquals(expected, stmtSamples().map { stmtName(it) })
    }

    @Test
    fun 表达式层齐备_S31_S37_含S40() {
        val expected = listOf(
            "IntLit", "FloatLit", "StrLit", "NameRef", "ReturnSym", "ParamRefExpr",
            "TopExpr", "BotExpr", "BinExpr", "UniExpr", "TypeTestExpr", "IsPatExpr",
            "TaskExpr", "FlowExpr", "CallExpr", "InstExpr", "FieldExpr", "FunRefExpr",
            "SymbolCallExpr", "LambdaExpr", "TupleExpr", "StructCtorExpr", "ByExpr",
            "VarExpr", "AnonFunExpr", "BlockExpr", "IfExpr", "WhenExpr", "QuantExpr",
            "ErrorExpr",
        )
        assertEquals(expected, exprSamples().map { exprName(it) })
    }

    @Test
    fun 模式层齐备_S42() {
        val expected = listOf(
            "PatLit", "PatCtor", "PatTuple", "PatIs", "PatBind", "PatElse", "PatCond",
            "ErrorPattern",
        )
        assertEquals(expected, patternSamples().map { patternName(it) })
    }

    @Test
    fun 类型层齐备_S15() {
        val expected = listOf(
            "NamedType", "FunType", "TupleType", "MutType", "AtomicType", "UnionType",
            "QualifiedType", "ErrorType",
        )
        assertEquals(expected, typeSamples().map { typeName(it) })
    }

    // ---------- 携带源位置（S-1 §2 + D-9）----------

    @Test
    fun 每个节点携带注入的源位置() {
        fun check(samples: List<AstNode>) {
            samples.forEachIndexed { i, node ->
                assertEquals(SourceRange(SourcePos(i + 1, 1), null), node.pos)
            }
        }
        check(declSamples())
        check(stmtSamples())
        check(exprSamples())
        check(patternSamples())
        check(typeSamples())
    }

    @Test
    fun 位置区间end可空_单点位置合法() {
        val node = NameRef("x", SourceRange(SourcePos(3, 7), null))
        assertEquals(3, node.pos.start.line)
        assertEquals(7, node.pos.start.col)
        assertNull(node.pos.end)
    }

    @Test
    fun 辅助记录携带源位置() {
        // Param / TypeParam / PropParam / WhenArm / WhenPropArm（非 AstNode 的记录同样自携位置；
        // Ctor 随 enum 废除移除样本，P-9 t40）
        val param = Param("x", nat(1), pos = pos(1))
        val tparam = TypeParam("T", null, 0, pos(2))
        val pparam = PropParam("p", pos(3))
        val arm = WhenArm(PatElse(pos(4)), null, lit(5), null, pos(4))
        val propArm = WhenPropArm(lit(6), listOf(exprStmt(6)), pos(5))
        assertEquals(pos(1), param.pos)
        assertEquals(pos(2), tparam.pos)
        assertEquals(pos(3), pparam.pos)
        assertEquals(pos(4), arm.pos)
        assertEquals(pos(5), propArm.pos)
    }

    // ---------- 容错节点（S-1 §2：语法错误处产出局部子树）----------

    @Test
    fun 容错节点属各层级并承载失败原因() {
        val d = ErrorDecl("期望 fun", pos(1))
        val s = ErrorStmt("期望语句", pos(2))
        val e = ErrorExpr("期望表达式", pos(3))
        val p = ErrorPattern("期望模式", pos(4))
        val t = ErrorType("期望类型", pos(5))
        assertEquals("期望 fun", d.message)
        assertEquals("期望语句", s.message)
        assertEquals("期望表达式", e.message)
        assertEquals("期望模式", p.message)
        assertEquals("期望类型", t.message)
        assertEquals(
            listOf("ErrorDecl", "ErrorStmt", "ErrorExpr", "ErrorPattern", "ErrorType"),
            listOf(nodeName(d), nodeName(s), nodeName(e), nodeName(p), nodeName(t)),
        )
    }

    @Test
    fun 容错节点可进入顶层条目_保局部子树() {
        // 工程模式顶层破裂 → ErrorDecl 占位；相邻可挽救子树保留在 Entry 序列中
        val file = FileAst(
            listOf(
                DeclEntry(declSamples().first()),
                DeclEntry(ErrorDecl("顶层条目无法识别", pos(2))),
                StmtEntry(exprStmt(3)),
            )
        )
        assertEquals(3, file.entries.size)
        assertIs<DeclEntry>(file.entries[0])
        assertEquals("FunDecl", nodeName((file.entries[0] as DeclEntry).decl))
        assertIs<DeclEntry>(file.entries[1])
        assertEquals("顶层条目无法识别", (file.entries[1] as DeclEntry).decl.let { it as ErrorDecl }.message)
        assertIs<StmtEntry>(file.entries[2])
    }

    // ---------- 结构性（data class：全 val、结构相等含位置）----------

    @Test
    fun 结构相等含位置_位置不同即不等() {
        val a = FunDecl("f", emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList(), null, emptyList(), pos(1))
        val b = FunDecl("f", emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList(), null, emptyList(), pos(1))
        val c = FunDecl("f", emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList(), null, emptyList(), pos(2))
        assertEquals(a, b)
        assertNotEquals(a, c)
        // copy 保持结构、可换位置（HIR 构造期拷贝归一化的基础）
        val d = c.copy(pos = pos(1))
        assertEquals(a, d)
    }

    @Test
    fun BlockExpr尾部表达式为末条ExprStmt_否则为null() {
        assertNull(BlockExpr(emptyList(), pos(1)).tailExpr)
        assertNull(BlockExpr(listOf(ReturnStmt(null, null, pos(2))), pos(1)).tailExpr)
        assertEquals(
            IntLit("1", pos(3)),
            BlockExpr(listOf(exprStmt(2), exprStmt(3)), pos(1)).tailExpr,
        )
    }
}
