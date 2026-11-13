package sublimation.frontend.semantic

import sublimation.frontend.cli.compileStdlibUnits
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirFunType
import sublimation.frontend.hir.HirMutType
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirNoteKind
import sublimation.frontend.hir.HirPurity
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirTupleType
import sublimation.frontend.hir.HirType
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.hir.HirWhen
import sublimation.frontend.parser.parseScriptSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TypeInfer 双轨测试：上半部代数件直装（mkInfer），下半部经 compile 全管线按节点结论断言。
 *
 * 上半部（代数，旧 TypeInfer 移植，P-7）：unifyInto 单向合一（变量绑定 / 结构递归 / occurs 检查）、
 * unify 数值塔 join、synthetic 吸收、builtinOp/builtinSig 内建签名、render 渲染、adaptOk 容忍。
 * 下半部：结论落树——经 compile 全管线，按节点 type 字段断言（ADR-6 §四 4.1 结论上树）：
 * var 标注核对 / 数值塔 / return 族 / ctor 与 struct 构造核对 / 缺实参与多实参 /
 * 元组解构 / 不可变赋值 / when 穷尽性（sealed join + unsealed + W-NON-EXHAUSTIVE）/ 别名展开。
 *
 * 破例说明（同 SemanticTest）：compile 不吸收解析期诊断，script() 合并两袋。
 */
class TypeInferTest {

    /** 代数件直装：alloc 只管单调，无树上下文（TypeInfer 不遍历树，ADR-6 §四 4.3）。 */
    private fun mkInfer(): TypeInfer {
        var n = 500
        return TypeInfer({ n++ }, DiagBag())
    }

    private fun script(src: String): Pair<HirFile, DiagBag> {
        val pr = parseScriptSourceWithNotes(src, "infer.subl")
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
    private fun allCodes(bag: DiagBag) = bag.diags.map { it.code }
    private fun stmts(hir: HirFile) = hir.entries.mapNotNull { (it as? HirStmtEntry)?.stmt }

    // ============================ 代数（旧 TypeInfer 移植，P-7） ============================

    @Test
    fun unifyIntoBindsVariableToConcrete() {
        val infer = mkInfer()
        val subst = mutableMapOf<String, HirType>()
        assertTrue(infer.unifyInto(infer.namedT("T"), infer.namedT("Nat"), setOf("T"), subst))
        assertEquals("Nat", (subst["T"] as HirNamedType).name)
    }

    @Test
    fun unifyIntoIsSymmetric() {
        val infer = mkInfer()
        val subst = mutableMapOf<String, HirType>()
        assertTrue(infer.unifyInto(infer.namedT("Nat"), infer.namedT("T"), setOf("T"), subst))
        assertEquals("Nat", (subst["T"] as HirNamedType).name)
    }

    @Test
    fun unifyIntoVarToVarBindsFirst() {
        val infer = mkInfer()
        val subst = mutableMapOf<String, HirType>()
        assertTrue(infer.unifyInto(infer.namedT("T"), infer.namedT("U"), setOf("T", "U"), subst))
        assertEquals("U", (subst["T"] as HirNamedType).name)
    }

    @Test
    fun unifyIntoStructureRecursive() {
        val infer = mkInfer()
        val subst = mutableMapOf<String, HirType>()
        assertTrue(infer.unifyInto(
            infer.namedT("List", listOf(infer.namedT("T"))),
            infer.namedT("List", listOf(infer.namedT("Nat"))),
            setOf("T"), subst))
        assertEquals("Nat", (subst["T"] as HirNamedType).name)
    }

    @Test
    fun unifyIntoFunAndParamStructural() {
        val infer = mkInfer()
        val subst = mutableMapOf<String, HirType>()
        val f1 = HirFunType(
            listOf(infer.namedT("T")), infer.namedT("Str"),
            HirPurity.PURE, emptyList(), null,
            1, sublimation.frontend.semantic.SYNTHETIC_POS,
        )
        val f2 = HirFunType(
            listOf(infer.namedT("Nat")), infer.namedT("Str"),
            HirPurity.PURE, emptyList(), null,
            2, sublimation.frontend.semantic.SYNTHETIC_POS,
        )
        assertTrue(infer.unifyInto(f1, f2, setOf("T"), subst))
        assertEquals("Nat", (subst["T"] as HirNamedType).name)
    }

    @Test
    fun unifyIntoOccursCheckRejects() {
        val infer = mkInfer()
        val subst = mutableMapOf<String, HirType>()
        val f1 = HirFunType(
            listOf(infer.namedT("T")), infer.namedT("Null"),
            HirPurity.PURE, emptyList(), null,
            1, sublimation.frontend.semantic.SYNTHETIC_POS,
        )
        assertFalse(infer.unifyInto(infer.namedT("T"), f1, setOf("T"), subst))
    }

    @Test
    fun unifyIntoNumericLoosePasses() {
        val infer = mkInfer()
        val subst = mutableMapOf<String, HirType>()
        assertTrue(infer.unifyInto(infer.namedT("Int"), infer.namedT("Nat"), emptySet(), subst))
    }

    @Test
    fun unifyJoinsNumericTower() {
        val infer = mkInfer()
        assertEquals("Int", infer.unify(infer.namedT("Nat"), infer.namedT("Int"))?.let { (it as HirNamedType).name })
        assertEquals("Rat", infer.unify(infer.namedT("Rat"), infer.namedT("Nat"))?.let { (it as HirNamedType).name })
    }

    @Test
    fun unifySyntheticAbsorbsAndNominalFails() {
        val infer = mkInfer()
        assertEquals("Nat", infer.unify(infer.syntheticT("占位"), infer.namedT("Nat"))?.let { (it as HirNamedType).name })
        assertNull(infer.unify(infer.namedT("Str"), infer.namedT("Nat")))
        val joined = infer.unify(
            infer.namedT("Optional", listOf(infer.namedT("Nat"))),
            infer.namedT("Optional", listOf(infer.namedT("Nat"))),
        )
        assertEquals("Optional", (joined as HirNamedType).name)
    }

    @Test
    fun builtinOpSignatures() {
        val infer = mkInfer()
        assertEquals("Nat", infer.builtinOp("+", listOf(infer.namedT("Nat"), infer.namedT("Nat")))
            ?.let { (it as HirNamedType).name })
        assertEquals("Rat", infer.builtinOp("/", listOf(infer.namedT("Nat"), infer.namedT("Int")))
            ?.let { (it as HirNamedType).name })
        assertEquals("Bool", infer.builtinOp("==", listOf(infer.namedT("Str"), infer.namedT("Str")))
            ?.let { (it as HirNamedType).name })
        assertEquals("Bool", infer.builtinOp("!", listOf(infer.namedT("Bool")))
            ?.let { (it as HirNamedType).name })
        assertNull(infer.builtinOp("+", listOf(infer.namedT("Str"), infer.namedT("Str"))),
            "Str 相加由 binExpr 显式分支（旧 checkBin 口径）")
        assertNull(infer.builtinOp("??", listOf(infer.namedT("Bool"))))
    }

    @Test
    fun renderFormat() {
        val infer = mkInfer()
        assertEquals("List[Nat]", infer.render(infer.namedT("List", listOf(infer.namedT("Nat")))))
        assertEquals("(Nat) => Null", infer.render(HirFunType(
            listOf(infer.namedT("Nat")), infer.namedT("Null"),
            HirPurity.PURE, emptyList(), null,
            1, sublimation.frontend.semantic.SYNTHETIC_POS,
        )))
        assertEquals("(Nat, Str)", infer.render(HirTupleType(
            listOf(infer.namedT("Nat"), infer.namedT("Str")),
            1, sublimation.frontend.semantic.SYNTHETIC_POS,
        )))
        assertEquals("@mut Nat", infer.render(HirMutType(
            infer.namedT("Nat"),
            1, sublimation.frontend.semantic.SYNTHETIC_POS,
        )))
    }

    @Test
    fun adaptOkTolerances() {
        val infer = mkInfer()
        assertTrue(infer.adaptOk(infer.namedT("Nat"), infer.namedT("Rat")))
        assertFalse(infer.adaptOk(infer.namedT("Rat"), infer.namedT("Nat")))
        // t58（ADR-36 R-34）：合成位不再一律放行——P-10 收紧后仅「推导未完成」由其他宽松通道承担，完成后必须拒绝（旧契约删除）
        assertFalse(infer.adaptOk(infer.syntheticT("半成品"), infer.namedT("Nat")))
        assertTrue(infer.adaptOk(infer.namedT("Nat"), infer.namedT("Any")))
        // t62（ADR-39 ST-11.1）：adaptOk = subtype（严格）。T <: @mut T **不成立**——升格是造格，
        // 不是子类型；调用位（var/赋值/实参）自行 demoteView(want) 承担造格，adaptOk 不再放行
        assertFalse(infer.adaptOk(infer.namedT("Nat"),
            HirMutType(infer.namedT("Nat"), 1, sublimation.frontend.semantic.SYNTHETIC_POS)))
        // 正向视图成立：@mut T <: T
        assertTrue(infer.adaptOk(
            HirMutType(infer.namedT("Nat"), 1, sublimation.frontend.semantic.SYNTHETIC_POS),
            infer.namedT("Nat")))
        // 不可变位互视：@mut T <: @mut U 仅当 T = U
        assertTrue(infer.adaptOk(
            HirMutType(infer.namedT("Nat"), 1, sublimation.frontend.semantic.SYNTHETIC_POS),
            HirMutType(infer.namedT("Nat"), 1, sublimation.frontend.semantic.SYNTHETIC_POS)))
    }

    // ============================ 结论落树（ADR-6 §四 4.1） ============================

    @Test
    fun varInferredTypeOnNode() {
        val (hir, bag) = script("var x = 1")
        assertFalse(bag.hasError)
        val v = stmts(hir).filterIsInstance<HirVarStmt>().first()
        assertEquals("Nat", (v.type as HirNamedType).name, "IntLit → Nat 结论落 HirVarStmt.type")
        assertEquals("Nat", (v.type as HirNamedType).name)
        assertTrue(allCodes(bag).contains("D-TYPE-INFERRED"), "未标注 var 落 D-TYPE-INFERRED 补充")
    }

    @Test
    fun varAnnotatedMismatchReports() {
        val (hir, bag) = script("var x: Str = 1")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag))
        assertTrue(bag.diags.any { it.message.contains("var x 标注了 Str，但初值是 Nat") }, "旧 checkStmtInner 消息口径（P-7）")
        val v = stmts(hir).filterIsInstance<HirVarStmt>().first()
        assertEquals("Str", (v.type as HirNamedType).name, "核对失败时类型回落标注位")
    }

    @Test
    fun varNumericPromotionAndDemotion() {
        val (_, bagA) = script("var x: Rat = 1")
        assertFalse(bagA.hasError, "Nat 初值可升 Rat（数值塔单向兼容）")
        val (_, bagB) = script("var x: Nat = 1.5")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bagB), "Rat 初值降 Nat 报错（只升不降）")
    }

    @Test
    fun varAliasTypeExpands() {
        val (_, bag) = script("type Count = Nat\nvar c: Count = 1")
        assertFalse(bag.hasError, "别名展开（决策 30）：标注位按 Nat 核对")
    }

    @Test
    fun funRetMismatchReports() {
        val (_, bag) = script("fun f(): Str = 1")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag))
        assertTrue(bag.diags.any { it.message.contains("函数 `f` 声明返回 Str，但函数体是 Nat") })
    }

    @Test
    fun funBlockMissingReturnReports() {
        val (_, bag) = script("fun f(): Nat {\n    var y = 20\n}")
        assertEquals(listOf("E-MISSING-RETURN"), errCodes(bag))
    }

    @Test
    fun funBlockReturnOk() {
        val (_, bag) = script("fun f(): Nat {\n    var y = 20\n    return y\n}")
        assertFalse(bag.hasError)
    }

    @Test
    fun retValueMismatchReports() {
        val (_, bag) = script("fun f(): Nat {\n    return 1.5\n}")
        assertEquals(listOf("E-RETURN-TYPE"), errCodes(bag))
        assertTrue(bag.diags.any { it.message.contains("return 值类型 Rat 与声明 Nat 不符") })
    }

    @Test
    fun returnOutsideFunctionReports() {
        val (_, bag) = script("return 1")
        assertEquals(listOf("E-RETURN-OUTSIDE"), errCodes(bag))
    }

    @Test
    fun funCallArityAndArgChecks() {
        val (_, bagA) = script("fun f(): Nat = 1\nvar x = f(1)")
        assertEquals(listOf("E-ARITY"), errCodes(bagA), "旧码：f 只想要 0 个实参，但你传了 1 个（多了）")
        val (_, bagB) = script("fun g(a: Nat): Nat = a\nvar y = g()")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bagB))
        assertTrue(bagB.diags.any { it.message.contains("函数 `g` 缺实参：`a`") })
        val (_, bagC) = script("fun g(a: Nat): Nat = a\nvar z = g(\"s\")")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bagC))
        assertTrue(bagC.diags.any { it.message.contains("实参 1 Str 与形参 Nat 不符") })
    }

    @Test
    fun ctorCallArityAndFieldType() {
        // t41（ADR-28）：enum ctor → struct 构造同通道
        val (_, bagA) = script("struct Some(x: Nat)\nvar a = Some(1)")
        assertFalse(bagA.hasError, "Some(1) 与字段 Nat 对齐")
        val (_, bagB) = script("struct Some(x: Nat)\nvar b = Some(1, 2)")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bagB))
        assertTrue(bagB.diags.any { it.message.contains("构造子 `Some` 期望 1 实参，实际 2") })
        val (_, bagC) = script("struct Some(x: Nat)\nvar c = Some(\"s\")")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bagC))
        assertTrue(bagC.diags.any { it.message.contains("构造子 `Some` 实参 Str 与字段 Nat 不符") })
    }

    @Test
    fun genericCtorInferenceT18() {
        // t41（ADR-28）：泛型 ctor → 泛型 struct（T 按实参推导）；递归列表用归纳联合
        val (_, bagA) = script("struct Box[T](x: T)\nfun make(): Box[Nat] = Box(1)")
        assertFalse(bagA.hasError, "泛型构造子 Box(1) 推导 T=Nat，不再报「实参 Nat 与字段 T 不符」")
        val (hir, bagB) = script("struct Nil()\nstruct Cons[T](head: T, tail: Lst[T])\ntype Lst[T] = Nil | Cons[T]\nvar x = Cons(1, Nil)")
        assertFalse(bagB.hasError, "嵌套构造子推导：内层 Nil 的联合位由外层 Cons 的 T=Nat 回代")
        val x = stmts(hir).filterIsInstance<HirVarStmt>().first().value as HirCall
        val t = x.type as HirNamedType
        assertEquals("Cons", t.name, "struct 构造返回 struct 名（旧枚举返枚举名 Lst）")
        assertEquals("Nat", (t.args.first() as HirNamedType).name)
        // t41：联合形态的跨字段一致性——尾部 Lst[Nat]（Nil | Cons[Nat]）拒 Cons[Str]
        val (_, bagC) = script("struct Nil()\nstruct Cons[T](head: T, tail: Lst[T])\ntype Lst[T] = Nil | Cons[T]\nfun make(): Lst[Nat] = Cons(1, Cons(\"x\", Nil))")
        assertTrue(bagC.diags.any { it.severity == Severity.ERROR && it.message.contains("构造子 `Cons` 实参 Cons[Str] 与字段") },
            "解集回代后跨字段一致性落 E-TYPE-MISMATCH")
        // t41：联合形态负例（Box 为 struct）
        val (_, bagD) = script("struct Box[T](x: T)\nfun make(): Box[Nat] = Box(\"s\")")
        val errs = bagD.diags.filter { it.severity == Severity.ERROR }.joinToString(";") { it.code + ":" + it.message }
        assertTrue(errs.contains("E-TYPE-MISMATCH") && errs.contains("Box[Str]") && errs.contains("Box[Nat]"),
            "Box(\"s\") 解出 Box[Str]，与声明的 Box[Nat] 不符：实际诊断=$errs")
        val (_, bagE) = script("fun id[T](x: T): T = x\nfun main(): Nat = id(1)")
        assertFalse(bagE.hasError, "泛型函数 id(1) 推导 T=Nat，实参核对与返回类型回代均通过")
    }

    @Test
    fun stdlibNamesAndPanicBodyT19() {
        // t137（R-23 retire）：sum/range/summation 的数据源由 PRELUDE_FUNS Kotlin 表退役为 **stdlib
        // 默认挂载快照**——本测按生产路径同构（compileStdlibUnits 取快照 → compile mounted 面）。
        val (su, sd) = compileStdlibUnits(noStdlib = false, scriptMode = true, noDegrade = false, noLift = false)
        assertFalse(sd.hasError, "stdlib 件编译零红（B0-0: list + math）")
        val mounted = su.associate { it.alias to it.snapshot }
        fun scriptStd(src: String): Pair<HirFile, DiagBag> {
            val pr = parseScriptSourceWithNotes(src, "infer.subl")
            val (hir, bag) = compile(pr.file, pr.notes, true, false, false, mounted)
            pr.diag.forEach { d ->
                when (d.severity) {
                    Severity.ERROR -> bag.error(d.code, d.pos, d.message)
                    Severity.WARN -> bag.warn(d.code, d.pos, d.message)
                    Severity.HINT -> bag.hint(d.code, d.pos, d.message)
                    else -> bag.supplement(d.code, d.pos, d.message)
                }
            }
            return hir to bag
        }
        val (_, bagA) = scriptStd("fun m(): Nat = sum(range(1, 10))")
        assertFalse(bagA.hasError, "sum/range 按 stdlib 迁源的签名核对通过")
        val (_, bagB) = scriptStd("fun m(): Nat = sum(strTrim(\" x \"))")
        assertTrue(bagB.diags.any { it.severity == Severity.ERROR && it.message.contains("实参 1 Str 与形参 List[Nat] 不符") },
            "stdlib 迁源签名参与实参核对：strTrim Str 不入 sum(List[Nat])")
        val (_, bagC) = scriptStd("fun nativeOp(x: Nat): Str = panic()")
        assertFalse(bagC.hasError, "panic() 体不再误判「函数体是 Nothing」与声明不符")
    }

    @Test
    fun nestedCallTypeFlowsIntoArg() {
        val (hir, bag) = script("fun g(a: Nat): Nat = a\nvar z = g(g(1))")
        assertFalse(bag.hasError, "内层 g(1) 结论 Nat 作为外层实参（结论上树供调用点消费）")
        val call = stmts(hir).filterIsInstance<HirVarStmt>().first().value as HirCall
        assertEquals("Nat", ((call.args.first() as HirCall).type as HirNamedType).name)
    }

    @Test
    fun structCtorChecks() {
        val (_, bagA) = script("struct P(x: Nat, y: Nat)\nvar p = P(x = 1, y = 2)")
        assertFalse(bagA.hasError)
        val (_, bagB) = script("struct P(x: Nat, y: Nat)\nvar q = P(x = 1)")
        assertEquals(listOf("E-FIELD-MISSING"), errCodes(bagB))
        val (_, bagC) = script("struct P(x: Nat, y: Nat)\nvar r = P(x = 1, x = 2, y = 3)")
        assertEquals(listOf("E-DUP-FIELD"), errCodes(bagC))
        val (_, bagD) = script("struct P(x: Nat, y: Nat)\nvar s = P(x = \"a\", y = 2)")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bagD))
        assertTrue(bagD.diags.any { it.message.contains("构造 `P` 字段 `x` 的实参 Str 与 Nat 不符") })
    }

    @Test
    fun tupleDestructureChecks() {
        val (hir, bag) = script("var (a, b) = (1, \"s\")")
        assertFalse(bag.hasError)
        val t = stmts(hir).filterIsInstance<HirVarStmt>().first().type as HirTupleType
        assertEquals("Nat", (t.items[0] as HirNamedType).name)
        assertEquals("Str", (t.items[1] as HirNamedType).name)
        val (_, bagB) = script("var (a, b) = 1")
        assertEquals(listOf("E-TUPLE-DESTRUCT"), errCodes(bagB))
        val (_, bagC) = script("var (a, b, c) = (1, 2)")
        assertEquals(listOf("E-TUPLE-ARITY"), errCodes(bagC))
    }

    @Test
    fun immutableAssignReportsAndMutAllows() {
        val (_, bagA) = script("var x = 1\nx = 2")
        assertEquals(listOf("E-IMMUT-ASSIGN"), errCodes(bagA))
        assertTrue(bagA.diags.any { it.message.contains("是不可变的，不能赋值") })
        // R-28：类型位 @mut 不可由 plain 初始化——可写绑定用 @mut var 形态
        val (_, bagB) = script("@mut var x = 1\nx = 2")
        assertFalse(bagB.hasError, "@mut 绑定可写")
    }

    @Test
    fun binExprRangeAndBool() {
        val (hir, bag) = script("var r = 1..10\nvar b = r in (1..10)")
        assertFalse(bag.hasError)
        val vs = stmts(hir).filterIsInstance<HirVarStmt>()
        assertEquals("Range", (vs[0].type as HirNamedType).name, "旧 checkBin：.. → Range[l]")
        assertEquals("Bool", (vs[1].type as HirNamedType).name, "in → Bool")
    }

    @Test
    fun binExprStrConcatKeepsStr() {
        val (hir, bag) = script("var s = \"a\" + \"b\"")
        assertFalse(bag.hasError)
        val v = stmts(hir).filterIsInstance<HirVarStmt>().first()
        assertEquals("Str", (v.type as HirNamedType).name, "Str+Str 显式 Str（P6 旧口径）")
    }

    @Test
    fun binExprNominalMismatchReports() {
        // t41（ADR-28）：enum 废除 → 联合形态（不同联合的成员作中缀比较仍报错）
        val (_, bag) = script("struct A()\nstruct B()\ntype E = A | B\nstruct C()\nstruct D()\ntype F = C | D\nvar b = A < C")
        assertEquals(listOf("E-TYPE-MISMATCH"), errCodes(bag))
        assertTrue(bag.diags.any { it.message.contains("中缀") })
    }

    @Test
    fun whenSealedJoinAndUnsealedReport() {
        val src = "struct A()\nstruct B()\ntype E = A | B\nvar x = 0"
        val (hir, bag0) = script(src + "\nvar q = when (x) { is A -> 1 else -> 2 }")
        // x 为 Nat 非枚举主题但带 else 兜底 → join 分支（Nat）；仅函数体位才 E-NON-SEALED-MATCH
        assertFalse(bag0.hasError)
        assertEquals("Nat", ((stmts(hir).filterIsInstance<HirVarStmt>()[1]).type as HirNamedType).name)
        val (hirN, bagN) = script(src + "\nvar q2 = when (x) { 1 -> 1 }")
        assertFalse(bagN.hasError)
        assertEquals("(未密封)", ((stmts(hirN).filterIsInstance<HirVarStmt>()[1]).type as HirNamedType).name,
            "非枚举主题无 else → unsealedT（§49 固定 Null 语义）")
        // t41：联合主题缺成员 → E-NON-SEALED-MATCH（同旧枚举口径）
        val (_, bag1) = script("struct A()\nstruct B()\ntype E = A | B\nfun f(x: E): Nat = when (x) { is A -> 1 }")
        assertEquals(listOf("E-NON-SEALED-MATCH"), errCodes(bag1))
        val (_, bag2) = script("struct A()\nstruct B()\ntype E = A | B\nfun f(x: E): Nat = when (x) { is A -> 1 else -> 2 }")
        assertFalse(bag2.hasError, "else 兜底 → 密封 join")
    }

    @Test
    fun whenNonExhaustiveWarns() {
        val (_, bag) = script("var b = True\nvar q = when (b) { True -> 1 }")
        assertFalse(bag.hasError)
        assertTrue(allCodes(bag).contains("W-NON-EXHAUSTIVE"), "值位 when 缺 else → W-NON-EXHAUSTIVE")
    }

    @Test
    fun ifJoinAndMissingElse() {
        val (hir, bag) = script("var c = True\nvar v = if (c) 1 else 2.5")
        assertFalse(bag.hasError)
        val v = stmts(hir).filterIsInstance<HirVarStmt>()[1]
        assertEquals("Rat", (v.type as HirNamedType).name, "Nat/Rat 分支 join（数值塔升格）")
        val (hir2, bag2) = script("var c = True\nvar v = if (c) 1")
        assertFalse(bag2.hasError)
        val v2 = stmts(hir2).filterIsInstance<HirVarStmt>()[1]
        assertEquals("Null", (v2.type as HirNamedType).name, "缺 else → Null")
    }

    @Test
    fun lambdaSyntheticToleranceAtCallSite() {
        val (_, bag) = script("fun app(a: Nat, f: (Nat) => Nat): Nat = a\nvar s = app(1) { x => x }")
        assertFalse(bag.hasError, "V1：lambda 形参 synthetic 经 adaptOk 函数结构递归放行（双向反推留决策 55/57）")
    }

    @Test
    fun builtinSigKnownAndUnknown() {
        val infer = mkInfer()
        val toStr = infer.builtinSig("toStr")
        assertEquals("Str", (toStr?.second as HirNamedType).name)
        val length = infer.builtinSig("length")
        assertEquals("Nat", (length?.second as HirNamedType).name)
        assertNull(infer.builtinSig("nope"))
    }

    @Test
    fun trailingLambdaValuePositionInfers() {
        val (hir, bag) = script("fun app(a: Nat, f: (Nat) => Nat): Nat = a\nvar s = app(1) { x => x }")
        assertFalse(bag.hasError)
        val s = stmts(hir).filterIsInstance<HirVarStmt>().first()
        assertEquals("Nat", (s.type as HirNamedType).name, "调用的返回类型结论落 HirCall.type")
        assertTrue(s.value is HirCall)
    }
}
