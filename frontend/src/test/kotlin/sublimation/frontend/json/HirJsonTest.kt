package sublimation.frontend.json

import java.io.File

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.SourcePos
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.hir.HirAxiom
import sublimation.frontend.hir.HirAnonFun
import sublimation.frontend.hir.HirAssign
import sublimation.frontend.hir.HirAtomicType
import sublimation.frontend.hir.HirBinExpr
import sublimation.frontend.hir.HirBorrow
import sublimation.frontend.hir.HirBlock
import sublimation.frontend.hir.HirByExpr
import sublimation.frontend.hir.HirByStmt
import sublimation.frontend.hir.HirBecause
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirTraitDecl
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirErrorDecl
import sublimation.frontend.hir.HirErrorExpr
import sublimation.frontend.hir.HirErrorPattern
import sublimation.frontend.hir.HirErrorStmt
import sublimation.frontend.hir.HirErrorType
import sublimation.frontend.hir.HirRefType
import sublimation.frontend.hir.HirExistsElim
import sublimation.frontend.hir.HirExistsIntro
import sublimation.frontend.hir.HirExprStmt
import sublimation.frontend.hir.HirField
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirFlow
import sublimation.frontend.hir.HirFloatLit
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirFunRef
import sublimation.frontend.hir.HirFunType
import sublimation.frontend.hir.HirIf
import sublimation.frontend.hir.HirIfProp
import sublimation.frontend.hir.HirInst
import sublimation.frontend.hir.HirIntLit
import sublimation.frontend.hir.HirLambda
import sublimation.frontend.hir.HirLocalFun
import sublimation.frontend.hir.HirMutType
import sublimation.frontend.hir.HirNameRef
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirNote
import sublimation.frontend.hir.HirNoteKind
import sublimation.frontend.hir.HirParam
import sublimation.frontend.hir.HirPatBind
import sublimation.frontend.hir.HirPatCond
import sublimation.frontend.hir.HirPatCtor
import sublimation.frontend.hir.HirPatElse
import sublimation.frontend.hir.HirPatIs
import sublimation.frontend.hir.HirPatLit
import sublimation.frontend.hir.HirPatTuple
import sublimation.frontend.hir.HirPAnd
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirPBot
import sublimation.frontend.hir.HirPExists
import sublimation.frontend.hir.HirPExists1
import sublimation.frontend.hir.HirPForall
import sublimation.frontend.hir.HirPIff
import sublimation.frontend.hir.HirPImp
import sublimation.frontend.hir.HirPNot
import sublimation.frontend.hir.HirPOr
import sublimation.frontend.hir.HirPTop
import sublimation.frontend.hir.HirProofContext
import sublimation.frontend.hir.HirProofStatus
import sublimation.frontend.hir.HirProp
import sublimation.frontend.hir.HirPropParam
import sublimation.frontend.hir.HirPurity
import sublimation.frontend.hir.HirQualifiedType
import sublimation.frontend.hir.HirReturn
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirStrategy
import sublimation.frontend.hir.HirSelfType  // P-8/A：卷 TS-29
import sublimation.frontend.hir.HirStrLit
import sublimation.frontend.hir.HirStructCtor
import sublimation.frontend.hir.HirStructDecl
import sublimation.frontend.hir.HirTBin
import sublimation.frontend.hir.HirTBot
import sublimation.frontend.hir.HirTCall
import sublimation.frontend.hir.HirTDollar
import sublimation.frontend.hir.HirTField
import sublimation.frontend.hir.HirTInst
import sublimation.frontend.hir.HirTInt
import sublimation.frontend.hir.HirTName
import sublimation.frontend.hir.HirTParamRef
import sublimation.frontend.hir.HirTStr
import sublimation.frontend.hir.HirTSymbolCall
import sublimation.frontend.hir.HirTTop
import sublimation.frontend.hir.HirTUni
import sublimation.frontend.hir.HirTask
import sublimation.frontend.hir.HirTerm
import sublimation.frontend.hir.HirTuple
import sublimation.frontend.hir.HirTupleType
import sublimation.frontend.hir.HirType
import sublimation.frontend.hir.HirTypeAlias
import sublimation.frontend.hir.HirTypeParam
import sublimation.frontend.hir.HirTypeTest
import sublimation.frontend.hir.HirUnchecked
import sublimation.frontend.hir.HirUniExpr
import sublimation.frontend.hir.HirUnionType
import sublimation.frontend.hir.HirVarExpr
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.hir.HirVerdict
import sublimation.frontend.hir.HirWhen
import sublimation.frontend.hir.HirWhenArm
import sublimation.frontend.hir.HirWhenProp
import sublimation.frontend.hir.HirWhenPropArm
import sublimation.frontend.hir.SymbolKind
import sublimation.frontend.hir.SymbolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * HIR→JSON 发射器测试（ADR-1 §3.7；P-6 手写发射器）。
 *
 * 覆盖四线：
 * 1. 齐备性双判据——发射分派为 sealed 穷尽 when（编译期，见 HirJson.kt）+ 93 节点
 *    （含 t7 补绘的程序面 HirBinExpr/HirUniExpr）
 *    样本名集对账（运行期：发射出的节点 kind 集与期望节点名集逐一命中，零缺失零多余；
 *    计数仅认 Hir 前缀的节点 kind，排除符号引用/notes 的同名小写字段）；
 * 2. 契约金样——ADR-1 §3.7 示例 A/B/C 对账（envelope/证明节点/脱糖留痕）；
 * 3. 保真——转义（RFC 8259）、字面量原文、null/空集合显式发射、诊断全字段、枚举小写、
 *    单元模式推导、ok 旗标（N-SEV-BLOCK：仅 ERROR 置 false）；
 * 4. 纪律——纯函数可复现（S-1 层间总契约 3）、产物文件落盘 UTF-8、深树发射不爆栈
 *    （发射器为递归实现，深度上界与 JVM 默认栈深同阶——与解析器同类约束，
 *    裁决见 ADR-6；此处取 200 层验证）。
 */
class HirJsonTest {
    private fun pt(line: Int, col: Int): SourceRange = SourceRange(SourcePos(line, col), null)

    private fun rng(line: Int, col: Int, line2: Int, col2: Int): SourceRange =
        SourceRange(SourcePos(line, col), SourcePos(line2, col2))

    private fun emptyFile(): HirFile = HirFile(1, pt(1, 1), entries = emptyList())

    // ---------- 1. 齐备性 ----------

    @Test
    fun allNodeKindsCovered() {
        val json = HirJson.emit(zoo(), DiagBag(), "zoo.subl")
        val actual = Regex("""kind": "(Hir\w+)""").findAll(json).map { it.groupValues[1] }.toSet()
        val expected = setOf(
            // 文件 + 声明（P-9 t40：HirEnumDecl/HirCtor 移除；HirPropDef 随 def 废除移除 t41；HirTraitConst 保留）+ 理论参数 2
            "HirFile", "HirFunDecl", "HirTraitDecl", "HirStructDecl",
            "HirTypeAlias", "HirErrorDecl", "HirTypeParam", "HirPropParam",
            "HirParam",
            // 语句 15 + 命题 when 分臂
            "HirVarStmt", "HirAssign", "HirAxiom", "HirByStmt", "HirUnchecked", "HirExprStmt",
            "HirBecause", "HirWhenProp", "HirWhenPropArm", "HirIfProp", "HirExistsIntro",
            "HirExistsElim", "HirLocalFun", "HirStrategy", "HirReturn", "HirErrorStmt",
            // 表达式 23 + when 分臂
            "HirIntLit", "HirFloatLit", "HirStrLit", "HirNameRef", "HirBinExpr", "HirUniExpr", "HirCall", "HirField",
            "HirFunRef", "HirTypeTest", "HirTask", "HirFlow", "HirInst", "HirLambda",
            "HirAnonFun", "HirTuple", "HirStructCtor", "HirByExpr", "HirVarExpr", "HirBlock",
            "HirIf", "HirWhen", "HirWhenArm", "HirErrorExpr",
            // 命题面 11 + 命题项 13
            "HirPTop", "HirPBot", "HirPNot", "HirPAnd", "HirPOr", "HirPImp", "HirPIff",
            "HirPForall", "HirPExists", "HirPExists1", "HirPAtom",
            "HirTInt", "HirTStr", "HirTName", "HirTDollar", "HirTParamRef", "HirTTop",
            "HirTBot", "HirTBin", "HirTUni", "HirTCall", "HirTInst", "HirTField",
            "HirTSymbolCall",
            // 类型 8 + 模式 8
            "HirNamedType", "HirFunType", "HirTupleType", "HirMutType", "HirAtomicType", "HirRefType", "HirBorrow",
            "HirUnionType", "HirQualifiedType", "HirErrorType",
            "HirPatLit", "HirPatCtor", "HirPatTuple", "HirPatIs", "HirPatBind", "HirPatElse",
            "HirPatCond", "HirErrorPattern",
        )
        assertEquals(91, expected.size)  // t101：&v 借用节点加入后 91（t95：90）
        assertEquals(expected, actual)
        // P-8/A（卷 TS-31）：HirSelfType 固定输出 {"k": "self"}、不带 kind——不出现在名集
        // 对账里，由 selfTypeEmitsFixedKSelfForm 专测覆盖；此处断言其存在于 zoo 产物。
        assertTrue(json.contains("\"k\": \"self\""), "HirSelfType 固定形态须在产物中（第 93 个 HirNode 叶子）")
    }

    /** 齐备性样本：覆盖全部 94 个 HirNode 叶子类型（93 个带 kind 发射 + TS-31 固定形态的 HirSelfType；t95 加 HirRefType、t101 加 HirBorrow；P-9 t40 删除 HirEnumDecl/HirCtor；nid 仅需文件内唯一，此处顺序分配）。 */
    @Suppress("LongMethod")
    private fun zoo(): HirFile {
        var next = 0
        fun nid(): Int = ++next
        fun pos(): SourceRange = pt(1, 1)
        fun ref(kind: SymbolKind, name: String, id: Int = 1): SymbolRef = SymbolRef(id, kind, name)
        fun nat(): HirNamedType = HirNamedType("Nat", nid = nid(), origin = pos())
        fun nameRef(): HirNameRef =
            HirNameRef("x", symbol = ref(SymbolKind.LOCAL, "x"), type = nat(), nid = nid(), origin = pos())
        fun intLit(): HirIntLit = HirIntLit("1", nid = nid(), origin = pos())
        fun atom(head: String): HirPAtom = HirPAtom(head, emptyList(), nid = nid(), origin = pos())

        val boxArgs: List<HirType> = listOf(
            HirTupleType(listOf(nat(), nat()), nid = nid(), origin = pos()),
            HirMutType(nat(), nid = nid(), origin = pos()),
            HirAtomicType(nat(), nid = nid(), origin = pos()),
            HirRefType(nat(), nid = nid(), origin = pos()),   // t95（N2 @ref 语法层中间态）
            HirUnionType(listOf(nat(), nat()), nid = nid(), origin = pos()),
        )
        val boxType = HirNamedType("Box", args = boxArgs, nid = nid(), origin = pos())

        val allTerms: List<HirTerm> = listOf(
            HirTInt("1", nid = nid(), origin = pos()),
            HirTStr("s", nid = nid(), origin = pos()),
            HirTName("t", symbol = ref(SymbolKind.PARAM, "t"), nid = nid(), origin = pos()),
            HirTDollar(nid = nid(), origin = pos()),
            HirTParamRef(1, nid = nid(), origin = pos()),
            HirTTop(nid = nid(), origin = pos()),
            HirTBot(nid = nid(), origin = pos()),
            HirTBin("+", HirTInt("2", nid = nid(), origin = pos()), HirTInt("3", nid = nid(), origin = pos()), nid = nid(), origin = pos()),
            HirTUni("-", HirTInt("4", nid = nid(), origin = pos()), nid = nid(), origin = pos()),
            HirTCall(HirTName("f", nid = nid(), origin = pos()), listOf(HirTInt("5", nid = nid(), origin = pos())), nid = nid(), origin = pos()),
            HirTInst(HirTName("g", nid = nid(), origin = pos()), listOf(HirTInt("6", nid = nid(), origin = pos())), nid = nid(), origin = pos()),
            HirTField(HirTName("r", nid = nid(), origin = pos()), "fld", nid = nid(), origin = pos()),
            HirTSymbolCall("<", listOf(HirTInt("7", nid = nid(), origin = pos())), nid = nid(), origin = pos()),
        )
        val fullAtom = HirPAtom("valid", allTerms, nid = nid(), origin = pos())
        val forallP = HirPForall(listOf("n"), atom("p"), nid = nid(), origin = pos())
        val existsP = HirPExists(listOf("x"), atom("q"), nid = nid(), origin = pos())
        val exists1P = HirPExists1(listOf("y"), atom("r"), nid = nid(), origin = pos())
        val impP = HirPImp(atom("a"), atom("b"), nid = nid(), origin = pos())

        val funBody = HirBlock(listOf(
            // 语句层 15 种
            HirVarStmt(
                "v", symbol = ref(SymbolKind.LOCAL, "v"), annotations = listOf("m"), type = nat(),
                value = HirStructCtor(
                    "S", assigns = listOf("f" to intLit()), symbol = ref(SymbolKind.STRUCT, "S"),
                    type = nat(), nid = nid(), origin = pos(),
                ),
                destruct = listOf("d"), deepCopy = true, nid = nid(), origin = pos(),
            ),
            HirAssign(nameRef(), intLit(), mutableTarget = true, nid = nid(), origin = pos()),
            HirAxiom(
                props = listOf(fullAtom),
                proofContext = HirProofContext(facts = listOf(fullAtom)),
                rule = "R-AXIOM", verdict = HirVerdict(HirProofStatus.PROVEN),
                nid = nid(), origin = pos(),
            ),
            HirByStmt(
                props = listOf(forallP), proofContext = HirProofContext(),
                rule = "R-MP", verdict = HirVerdict(HirProofStatus.FAILED, "E-CANNOT-PROVE"),
                nid = nid(), origin = pos(),
            ),
            HirUnchecked(HirExprStmt(intLit(), nid = nid(), origin = pos()), nid = nid(), origin = pos()),
            HirExprStmt(intLit(), nid = nid(), origin = pos()),
            HirBecause(
                form = exists1P,
                premises = listOf(HirPOr(atom("l"), atom("rr"), nid = nid(), origin = pos())),
                conclusion = HirPAnd(atom("c1"), atom("c2"), nid = nid(), origin = pos()),
                proofContext = HirProofContext(facts = listOf(fullAtom), matched = ref(SymbolKind.THEOREM, "thm", 5)),
                rule = "R-MP", verdict = HirVerdict(HirProofStatus.PROVEN),
                nid = nid(), origin = pos(),
            ),
            HirWhenProp(
                disjunction = HirPOr(fullAtom, fullAtom, nid = nid(), origin = pos()),
                arms = listOf(
                    HirWhenPropArm(
                        HirPNot(fullAtom, nid = nid(), origin = pos()),
                        listOf(HirExprStmt(intLit(), nid = nid(), origin = pos())),
                        nid = nid(), origin = pos(),
                    ),
                ),
                proofContext = HirProofContext(), rule = "R-MP",
                verdict = HirVerdict(HirProofStatus.PROVEN), nid = nid(), origin = pos(),
            ),
            HirIfProp(
                prop = impP,
                thenStmts = listOf(HirExprStmt(intLit(), nid = nid(), origin = pos())),
                elseStmts = listOf(HirExprStmt(intLit(), nid = nid(), origin = pos())),
                proofContext = HirProofContext(), rule = "R-MT",
                verdict = HirVerdict(HirProofStatus.PROVEN), nid = nid(), origin = pos(),
            ),
            HirExistsIntro(
                names = listOf("w"), witnesses = listOf(nameRef()), prop = existsP,
                proofContext = HirProofContext(), rule = "R-EXISTS-MATCH",
                verdict = HirVerdict(HirProofStatus.PROVEN), nid = nid(), origin = pos(),
            ),
            HirExistsElim(
                name = "z", prop = existsP,
                body = listOf(HirExprStmt(intLit(), nid = nid(), origin = pos())),
                proofContext = HirProofContext(), rule = "R-FORALL-INST",
                verdict = HirVerdict(HirProofStatus.PROVEN), nid = nid(), origin = pos(),
            ),
            HirLocalFun(
                HirFunDecl(name = "helper", body = HirBlock(emptyList(), nid = nid(), origin = pos()), nid = nid(), origin = pos()),
                nid = nid(), origin = pos(),
            ),
            HirStrategy(
                "simp", HirPNot(fullAtom, nid = nid(), origin = pos()),
                proofContext = HirProofContext(), rule = "A-ARITH",
                verdict = HirVerdict(HirProofStatus.PROVEN), nid = nid(), origin = pos(),
            ),
            HirReturn(intLit(), label = "loop", nid = nid(), origin = pos()),
            HirErrorStmt("boom", nid = nid(), origin = pos()),
            // 表达式补齐：剩余 20 种（含 t7 补绘的程序面 Bin/UniExpr）
            HirExprStmt(
                HirBinExpr("+", intLit(), intLit(), type = nat(), nid = nid(), origin = pos()),
                nid = nid(), origin = pos(),
            ),
            HirExprStmt(
                HirUniExpr("-", intLit(), type = nat(), nid = nid(), origin = pos()),
                nid = nid(), origin = pos(),
            ),
            HirExprStmt(   // t101（R3 S6 &v 借用，零语义中间态）
                HirBorrow(
                    HirNameRef("v", symbol = ref(SymbolKind.LOCAL, "v"), type = nat(), nid = nid(), origin = pos()),
                    type = nat(), nid = nid(), origin = pos(),
                ),
                nid = nid(), origin = pos(),
            ),
            HirExprStmt(
                HirCall(
                    nameRef(), listOf(intLit()), symbol = ref(SymbolKind.FUN, "f"), type = nat(),
                    dictionary = "Eq", slot = "ord", dictArgs = listOf("Int"),
                    methodSugar = true, awaitPoint = true, calleeImpure = true,
                    nid = nid(), origin = pos(),
                ),
                nid = nid(), origin = pos(),
            ),
            HirExprStmt(HirField(nameRef(), "fld", field = ref(SymbolKind.FIELD, "fld"), type = nat(), nid = nid(), origin = pos()), nid = nid(), origin = pos()),
            HirExprStmt(HirFunRef(nameRef(), "g", symbol = ref(SymbolKind.FUN, "g"), type = nat(), nid = nid(), origin = pos()), nid = nid(), origin = pos()),
            HirExprStmt(HirTypeTest(nameRef(), nat(), smartCast = true, nid = nid(), origin = pos()), nid = nid(), origin = pos()),
            HirExprStmt(HirTask(HirBlock(emptyList(), nid = nid(), origin = pos()), type = nat(), nid = nid(), origin = pos()), nid = nid(), origin = pos()),
            HirExprStmt(HirFlow(HirBlock(emptyList(), nid = nid(), origin = pos()), type = nat(), nid = nid(), origin = pos()), nid = nid(), origin = pos()),
            HirExprStmt(HirInst(nameRef(), listOf(nameRef()), symbol = ref(SymbolKind.FUN, "inst"), type = nat(), nid = nid(), origin = pos()), nid = nid(), origin = pos()),
            HirExprStmt(
                HirLambda(
                    emptyList(), nameRef(), implicitIt = true,
                    type = HirFunType(listOf(nat()), nat(), nid = nid(), origin = pos()),
                    purity = HirPurity.IMPURE, nid = nid(), origin = pos(),
                ),
                nid = nid(), origin = pos(),
            ),
            HirExprStmt(
                HirAnonFun(
                    "_",
                    theory = listOf(HirTypeParam("T", nid = nid(), origin = pos())),
                    params = listOf(HirParam("b", type = nat(), nid = nid(), origin = pos())),
                    preps = listOf(fullAtom), retType = nat(), posts = listOf(fullAtom),
                    body = nameRef(),
                    type = HirFunType(listOf(nat()), nat(), nid = nid(), origin = pos()),
                    purity = HirPurity.IMPURE, nid = nid(), origin = pos(),
                ),
                nid = nid(), origin = pos(),
            ),
            HirExprStmt(
                HirTuple(
                    listOf(intLit(), HirStrLit("s", nid = nid(), origin = pos()), HirFloatLit("1.5", nid = nid(), origin = pos())),
                    type = nat(), nid = nid(), origin = pos(),
                ),
                nid = nid(), origin = pos(),
            ),
            HirExprStmt(
                HirByExpr(
                    nameRef(), listOf(fullAtom), proofContext = HirProofContext(),
                    rule = "R-AXIOM", verdict = HirVerdict(HirProofStatus.PROVEN),
                    nid = nid(), origin = pos(),
                ),
                nid = nid(), origin = pos(),
            ),
            HirExprStmt(
                HirVarExpr(
                    "ve", symbol = ref(SymbolKind.LOCAL, "ve"), annotations = listOf("a"),
                    type = nat(), value = intLit(), destruct = listOf("z"), deepCopy = true,
                    nid = nid(), origin = pos(),
                ),
                nid = nid(), origin = pos(),
            ),
            HirExprStmt(
                HirIf(
                    nameRef(),
                    HirBlock(emptyList(), nid = nid(), origin = pos()),
                    HirBlock(emptyList(), nid = nid(), origin = pos()),
                    type = nat(), diverges = true, nid = nid(), origin = pos(),
                ),
                nid = nid(), origin = pos(),
            ),
            HirExprStmt(
                HirWhen(
                    nameRef(), subjectBinding = "e",
                    arms = listOf(
                        HirWhenArm(
                            pattern = HirPatCtor(
                                "Some",
                                listOf(
                                    HirPatTuple(
                                        listOf(
                                            HirPatBind("x", symbol = ref(SymbolKind.LOCAL, "x"), nid = nid(), origin = pos()),
                                            HirPatLit(intLit(), nid = nid(), origin = pos()),
                                        ),
                                        nid = nid(), origin = pos(),
                                    ),
                                ),
                                ctor = ref(SymbolKind.CTOR, "Some"), nid = nid(), origin = pos(),
                            ),
                            guard = nameRef(), body = nameRef(), cond = null,
                            nid = nid(), origin = pos(),
                        ),
                        HirWhenArm(
                            pattern = HirPatIs(nat(), bind = "y", bindSymbol = ref(SymbolKind.LOCAL, "y"), nid = nid(), origin = pos()),
                            guard = null, body = nameRef(), cond = nameRef(),
                            nid = nid(), origin = pos(),
                        ),
                        HirWhenArm(HirPatElse(nid = nid(), origin = pos()), guard = null, body = nameRef(), cond = null, nid = nid(), origin = pos()),
                        HirWhenArm(HirPatCond(nid = nid(), origin = pos()), guard = null, body = nameRef(), cond = nameRef(), nid = nid(), origin = pos()),
                        HirWhenArm(HirErrorPattern("bad", nid = nid(), origin = pos()), guard = null, body = nameRef(), cond = null, nid = nid(), origin = pos()),
                    ),
                    type = nat(), diverges = false, exhaustive = true, nid = nid(), origin = pos(),
                ),
                nid = nid(), origin = pos(),
            ),
            HirExprStmt(HirErrorExpr("bad-expr", nid = nid(), origin = pos()), nid = nid(), origin = pos()),
        ), nid = nid(), origin = pos())

        val zooFun = HirFunDecl(
            name = "zoo", symbol = ref(SymbolKind.FUN, "zoo"), annotations = listOf("async"),
            theory = listOf(
                HirTypeParam("T", constraint = ref(SymbolKind.TRAIT, "Eq"), arity = 1, nid = nid(), origin = pos()),
                HirPropParam("P", nid = nid(), origin = pos()),
            ),
            params = listOf(
                HirParam(
                    "a", type = nat(), symbol = ref(SymbolKind.PARAM, "a"), annotations = listOf("tag"),
                    default = intLit(), destruct = listOf("p", "q"), nid = nid(), origin = pos(),
                ),
            ),
            preps = listOf(HirPTop(nid = nid(), origin = pos())),
            retType = HirFunType(
                params = listOf(nat()), ret = nat(), purity = HirPurity.IMPURE,
                theory = listOf(HirTypeParam("K", nid = nid(), origin = pos())),
                context = listOf(HirPBot(nid = nid(), origin = pos())),
                nid = nid(), origin = pos(),
            ),
            posts = listOf(fullAtom),
            constraints = listOf(HirPIff(HirPTop(nid = nid(), origin = pos()), HirPBot(nid = nid(), origin = pos()), nid = nid(), origin = pos())),
            purity = HirPurity.IMPURE, async = true, body = funBody,
            nid = nid(), origin = pos(),
        )

        val entries = listOf(
            HirDeclEntry(zooFun),
            // P-8/A 增量（卷 TS-28/29）：扩展函数——接收者嵌套实参原样保留 + Self 类型节点；
            // P-8/C：原 impl 条目的 boxType（Tuple/Mut/Atomic/Union 四类型载体）移入此接收者实参
            HirDeclEntry(
                HirFunDecl(
                    name = "fmap",
                    receiver = HirNamedType(
                        "List",
                        args = listOf(
                            HirNamedType("Optional", args = listOf(nat()), nid = nid(), origin = pos()),
                            boxType,
                        ),
                        symbol = ref(SymbolKind.TRAIT, "List"),
                        nid = nid(), origin = pos(),
                    ),
                    retType = HirSelfType(nid = nid(), origin = pos()),
                    nid = nid(), origin = pos(),
                ),
            ),
            HirDeclEntry(
                HirTraitDecl(
                    "Cls", symbol = ref(SymbolKind.TRAIT, "Cls"), parents = listOf(nat()),
                    members = listOf(HirFunDecl(name = "m", nid = nid(), origin = pos())),
                    nid = nid(), origin = pos(),
                ),
            ),
            HirDeclEntry(
                HirStructDecl(
                    "S", symbol = ref(SymbolKind.STRUCT, "S"),
                    fields = listOf(HirParam("e", type = HirErrorType("bad-type", nid = nid(), origin = pos()), nid = nid(), origin = pos())),
                    props = listOf(fullAtom), nid = nid(), origin = pos(),
                ),
            ),
    HirDeclEntry(
                HirTypeAlias(
                    "TA", symbol = ref(SymbolKind.ALIAS, "TA"),
                    target = HirQualifiedType(listOf("core"), "Nat", symbol = ref(SymbolKind.ALIAS, "Nat"), nid = nid(), origin = pos()),
                    nid = nid(), origin = pos(),
                ),
            ),
            HirDeclEntry(HirErrorDecl("bad-decl", nid = nid(), origin = pos())),
        )
        return HirFile(nid = nid(), origin = pos(), entries = entries)
    }

    // ---------- 2. 契约金样（ADR-1 §3.7 示例对账） ----------

    /** ADR-1 §3.7 示例 A 的 HIR 构造（bounded.subl：命题注入 axiom + 证明上下文）。 */
    private fun exampleA(): HirFile {
        fun nat(nid: Int, pos: SourceRange): HirNamedType =
            HirNamedType("Nat", nid = nid, origin = pos, symbol = SymbolRef(0, SymbolKind.BUILTIN, "Nat"))

        val param = HirParam(
            "n", type = nat(4, rng(1, 16, 1, 19)), symbol = SymbolRef(2, SymbolKind.PARAM, "n"),
            nid = 3, origin = rng(1, 13, 1, 14),
        )
        val axiomProp = HirPAtom(
            "<=",
            listOf(
                HirTName("n", symbol = SymbolRef(2, SymbolKind.PARAM, "n"), nid = 9, origin = rng(2, 9, 2, 10)),
                HirTInt("100", nid = 10, origin = rng(2, 13, 2, 15)),
            ),
            nid = 8, origin = rng(2, 9, 2, 15),
        )
        val pureFact = HirPAtom(
            "pure",
            listOf(HirTName("bounded", symbol = SymbolRef(1, SymbolKind.FUN, "bounded"), nid = 12, origin = rng(1, 5, 1, 12))),
            nid = 11, origin = rng(1, 5, 1, 12),
        )
        val axiom = HirAxiom(
            props = listOf(axiomProp),
            proofContext = HirProofContext(facts = listOf(pureFact)),
            rule = "R-AXIOM", verdict = HirVerdict(HirProofStatus.PROVEN),
            nid = 7, origin = rng(2, 3, 2, 15),
        )
        val tail = HirNameRef(
            "n", symbol = SymbolRef(2, SymbolKind.PARAM, "n"), type = nat(15, rng(1, 16, 1, 19)),
            nid = 14, origin = rng(3, 3, 3, 4),
        )
        val block = HirBlock(
            listOf(HirExprStmt(tail, nid = 13, origin = rng(3, 3, 3, 4))),
            nid = 6, origin = rng(1, 27, 4, 1),
        )
        val fn = HirFunDecl(
            name = "bounded", symbol = SymbolRef(1, SymbolKind.FUN, "bounded"),
            params = listOf(param), retType = nat(5, rng(1, 23, 1, 26)),
            purity = HirPurity.PURE, body = block,
            nid = 2, origin = rng(1, 1, 4, 1),
        )
        return HirFile(nid = 1, origin = rng(1, 1, 4, 1), entries = listOf(HirDeclEntry(fn)))
    }

    /** ADR-1 §3.7 示例 B 的 HIR 构造（HirBecause：verdict failed / matched / rule）。 */
    private fun exampleB(): HirFile {
        val because = HirBecause(
            form = HirPForall(
                listOf("n"),
                HirPAtom(
                    ">",
                    listOf(
                        HirTName("n", symbol = SymbolRef(2, SymbolKind.PARAM, "n"), nid = 24, origin = rng(3, 10, 3, 11)),
                        HirTInt("0", nid = 25, origin = rng(3, 15, 3, 16)),
                    ),
                    nid = 23, origin = rng(3, 10, 3, 16),
                ),
                nid = 22, origin = rng(3, 4, 3, 17),
            ),
            premises = listOf(HirPAtom("p1", emptyList(), nid = 31, origin = pt(4, 3))),
            conclusion = HirPAtom("p2", emptyList(), nid = 32, origin = pt(5, 3)),
            proofContext = HirProofContext(
                facts = listOf(HirPAtom("Q", emptyList(), nid = 33, origin = pt(6, 3))),
                matched = SymbolRef(5, SymbolKind.THEOREM, "allPositive"),
            ),
            rule = "R-MP",
            verdict = HirVerdict(HirProofStatus.FAILED, "E-CANNOT-PROVE"),
            nid = 21, origin = rng(3, 3, 3, 35),
        )
        return HirFile(nid = 1, origin = pt(1, 1), entries = listOf(HirStmtEntry(because)))
    }

    @Test
    fun dumpExamples() {
        // 落盘：发射产物经人工核对 ADR-1 §3.7 后回填金样（见 exampleAGolden/becauseNodeGolden）；
        // zoo 产物一并落盘，供 93 节点发射形态的人工抽检（S-3 §3 验收证据）。
        val dir = File("frontend/build/tmp/hirjson")
        dir.mkdirs()
        File(dir, "example-a.json").writeText(HirJson.emit(exampleA(), DiagBag(), "bounded.subl"), Charsets.UTF_8)
        File(dir, "example-b.json").writeText(HirJson.emit(exampleB(), DiagBag(), "chain.subl"), Charsets.UTF_8)
        File(dir, "zoo.json").writeText(HirJson.emit(zoo(), DiagBag(), "zoo.subl"), Charsets.UTF_8)
    }

    @Test
    fun exampleAGolden() {
        // 金样逐字节对账（envelope + 示例 A 全树）
        assertEquals(EXAMPLE_A_JSON, HirJson.emit(exampleA(), DiagBag(), "bounded.subl"))
    }

    @Test
    fun becauseNodeGolden() {
        // 金样逐字节对账（HirBecause：proofContext/matched/rule/verdict 全字段）
        assertEquals(EXAMPLE_B_JSON, HirJson.emit(exampleB(), DiagBag(), "chain.subl"))
    }

    @Test
    fun desugarNoteFormMatchesExampleC() {
        val note = HirNote(
            HirNoteKind.DESUGAR, "D-INTP", "字符串插值脱糖为拼接调用", rng(1, 20, 1, 34),
            from = "StrLit(插值)", to = "HirCall(concat, …)",
        )
        val file = HirFile(nid = 1, origin = pt(1, 1), notes = listOf(note), entries = emptyList())
        val json = HirJson.emit(file, DiagBag(), "n.subl")
        // ADR-1 §3.7 示例 C（note 附于 HirFile：外层缩进即文件级）
        assertTrue(json.contains(
            """    "notes": [
      {
        "kind": "desugar",
        "code": "D-INTP",
        "message": "字符串插值脱糖为拼接调用",
        "pos": "1:20-1:34",
        "from": "StrLit(插值)",
        "to": "HirCall(concat, …)"
      }
    ]"""
        ))
    }

    @Test
    fun noteKindsLowercased() {
        val notes = listOf(
            HirNote(HirNoteKind.HINT, "H-CODE", "m", pt(1, 1)),
            HirNote(HirNoteKind.SUPPLEMENT, "D-CODE", "m", pt(1, 1)),
            HirNote(HirNoteKind.DESUGAR, "X-SUGAR", "m", pt(1, 1)),
        )
        val file = HirFile(1, pt(1, 1), notes = notes, entries = emptyList())
        val json = HirJson.emit(file, DiagBag(), "k.subl")
        assertTrue(json.contains(""""kind": "hint""""))
        assertTrue(json.contains(""""kind": "supplement""""))
        assertTrue(json.contains(""""kind": "desugar""""))
    }

    // ---------- 3. 保真 ----------

    @Test
    fun escapesPerRfc8259() {
        val bag = DiagBag()
        bag.error("E-ESC", pt(1, 1), "q\"q b\\b n\n t\t r\r f\u000C z\u0000 u\u001f")
        val json = HirJson.emit(emptyFile(), bag, "x.subl")
        // 短转义 \b\t\n\f\r；其余 C0 取 \u00xx 小写；引号与反斜杠转义
        assertTrue(json.contains(
            """q\"q b\\b n\n t\t r\r f\f z\u0000 u\u001f"""
        ))
    }

    @Test
    fun nonAsciiAndDelPassedThroughRaw() {
        val bag = DiagBag()
        bag.error("E-RAW", pt(1, 1), "中文 ok 𝄞 x\u007fy")
        val json = HirJson.emit(emptyFile(), bag, "x.subl")
        // DEL（U+007F）与非 ASCII 原样输出（UTF-8）：全量转义无谓放大体积且损失可读性
        assertTrue(json.contains("中文 ok 𝄞 x" + '\u007F' + "y"))
        assertFalse(json.contains("""\u4e2d"""))
    }

    @Test
    fun literalsKeepSourceText() {
        val file = HirFile(1, pt(1, 1), entries = listOf(HirStmtEntry(
            HirExprStmt(
                HirTuple(
                    listOf(HirIntLit("100", nid = 3, origin = pt(1, 1)), HirFloatLit("3.14e10", nid = 4, origin = pt(1, 1)), HirStrLit("hi", nid = 5, origin = pt(1, 1))),
                    nid = 6, origin = pt(1, 1),
                ),
                nid = 2, origin = pt(1, 1),
            ),
        )))
        val json = HirJson.emit(file, DiagBag(), "lit.subl")
        assertTrue(json.contains(""""value": "100""""))
        assertTrue(json.contains(""""value": "3.14e10""""))
        assertTrue(json.contains(""""value": "hi""""))
    }

    @Test
    fun unresolvedFieldsAreExplicitNullNotAbsent() {
        val file = HirFile(1, pt(1, 1), entries = listOf(HirStmtEntry(
            HirExprStmt(HirNameRef("x", nid = 3, origin = pt(1, 1)), nid = 2, origin = pt(1, 1)),
        )))
        val json = HirJson.emit(file, DiagBag(), "u.subl")
        assertTrue(json.contains(""""symbol": null"""))
        assertTrue(json.contains(""""type": null"""))
    }

    @Test
    fun posCompactForms() {
        val file = HirFile(1, pt(1, 1), entries = listOf(HirStmtEntry(
            HirAxiom(
                props = listOf(
                    HirPAtom(
                        "p",
                        listOf(
                            HirTInt("1", nid = 2, origin = rng(2, 13, 2, 15)),
                            HirTInt("2", nid = 3, origin = pt(5, 6)),
                        ),
                        nid = 4, origin = pt(1, 1),
                    ),
                ),
                nid = 5, origin = pt(1, 1),
            ),
        )))
        val json = HirJson.emit(file, DiagBag(), "p.subl")
        assertTrue(json.contains(""""pos": "2:13-2:15""""))
        assertTrue(json.contains(""""pos": "5:6""""))
    }

    @Test
    fun diagnosticsSerializedWithAllFields() {
        val bag = DiagBag()
        bag.modulePath = "core/math"
        bag.error("E-ONE", rng(2, 3, 2, 9), "boom", nid = 7)
        bag.warn("W-TWO", pt(1, 1), "w")
        bag.hint("H-THREE", pt(1, 1), "h")
        bag.supplement("D-FOUR", pt(1, 1), "d")
        val json = HirJson.emit(emptyFile(), bag, "x.subl")
        assertTrue(json.contains(
            """  "diagnostics": [
    {
      "severity": "error",
      "code": "E-ONE",
      "pos": "2:3-2:9",
      "message": "boom",
      "module": "core/math",
      "nid": 7
    },
    {
      "severity": "warn",
      "code": "W-TWO",
      "pos": "1:1",
      "message": "w",
      "module": "core/math",
      "nid": null
    },
    {
      "severity": "hint",
      "code": "H-THREE",
      "pos": "1:1",
      "message": "h",
      "module": "core/math",
      "nid": null
    },
    {
      "severity": "supplement",
      "code": "D-FOUR",
      "pos": "1:1",
      "message": "d",
      "module": "core/math",
      "nid": null
    }
  ],"""
        ))
    }

    @Test
    fun callResolutionFieldsRetained() {
        val call = HirCall(
            callee = HirNameRef("f", nid = 2, origin = pt(1, 1)),
            args = listOf(HirIntLit("1", nid = 3, origin = pt(1, 1))),
            symbol = SymbolRef(9, SymbolKind.FUN, "f"),
            type = HirNamedType("Nat", nid = 4, origin = pt(1, 1)),
            dictionary = "Eq", slot = "ord", dictArgs = listOf("Int"),
            methodSugar = true, awaitPoint = true, calleeImpure = true,
            nid = 5, origin = pt(1, 1),
        )
        val file = HirFile(1, pt(1, 1), entries = listOf(HirStmtEntry(HirExprStmt(call, nid = 6, origin = pt(1, 1)))))
        val json = HirJson.emit(file, DiagBag(), "c.subl")
        // 解析裁决信息全保留（字典/槽位/方法糖/await 点/被调不纯标记）——片段取字段名起的子串，
        // 避开外层引号的原始串边界歧义
        listOf(
            """dictionary": "Eq""", """slot": "ord""",
            """methodSugar": true""", """awaitPoint": true""", """calleeImpure": true""",
        ).forEach { fragment -> assertTrue(json.contains(fragment), fragment) }
        // dictArgs 数组形态：元素 12 空格、闭括号 10 空格缩进（产物缩进口径见 JsonWriter）
        val expected = "\"dictArgs\": [\n            \"Int\"\n          ]"
        assertTrue(json.contains(expected))
    }

    @Test
    fun structCtorAssignsPreserveOrder() {
        val ctor = HirStructCtor(
            "S",
            assigns = listOf(
                "b" to HirIntLit("1", nid = 2, origin = pt(1, 1)),
                "a" to HirIntLit("2", nid = 3, origin = pt(1, 1)),
            ),
            nid = 4, origin = pt(1, 1),
        )
        val file = HirFile(1, pt(1, 1), entries = listOf(HirStmtEntry(HirExprStmt(ctor, nid = 5, origin = pt(1, 1)))))
        val json = HirJson.emit(file, DiagBag(), "s.subl")
        val bIdx = json.indexOf(""""field": "b"""")
        val aIdx = json.indexOf(""""field": "a"""")
        assertTrue(bIdx in 0 until aIdx)
    }

    @Test
    fun unitModeProjectVsScript() {
        // D-12（t111）:模式真值单源——emit 的 scriptMode 形参（默认 project）,废除「条目在场⇒脚本」自算。
        // 默认值 project:含顶层语句条目的 exampleB 也判 project（真值不再读树条目）;显式传
        // scriptMode = true 才出 script（CLI 唯一源的函数层镜像）。
        val project = HirJson.emit(exampleA(), DiagBag(), "bounded.subl")
        val script = HirJson.emit(exampleB(), DiagBag(), "chain.subl")
        val scriptTruth = HirJson.emit(exampleB(), DiagBag(), "chain.subl", scriptMode = true)
        val empty = HirJson.emit(emptyFile(), DiagBag(), "e.subl")
        assertTrue(project.contains(""""mode": "project""""))
        assertTrue(script.contains(""""mode": "project""""),
            "D-12:默认 project——条目在场不再自算为 script")
        assertTrue(scriptTruth.contains(""""mode": "script""""),
            "D-12:scriptMode=true 显式真值才出 script")
        assertTrue(empty.contains(""""mode": "project""""))
    }

    @Test
    fun okFlagMirrorsHasError() {
        // N-SEV-BLOCK / D-10：仅 ERROR 置 ok=false；warn/hint/supplement 不阻断
        val err = DiagBag().apply { error("E-X", pt(1, 1), "e") }
        val soft = DiagBag().apply {
            warn("W-X", pt(1, 1), "w")
            hint("H-X", pt(1, 1), "h")
            supplement("D-X", pt(1, 1), "d")
        }
        assertTrue(HirJson.emit(emptyFile(), err, "e.subl").contains(""""ok": false"""))
        assertTrue(HirJson.emit(emptyFile(), soft, "s.subl").contains(""""ok": true"""))
        assertTrue(HirJson.emit(emptyFile(), DiagBag(), "n.subl").contains(""""ok": true"""))
    }

    // ---------- 4. 纪律 ----------

    @Test
    fun deterministicEmission() {
        val file = exampleA()
        val bag = DiagBag().apply { hint("H-D", pt(1, 1), "d") }
        assertEquals(HirJson.emit(file, bag, "bounded.subl"), HirJson.emit(file, bag, "bounded.subl"))
    }

    @Test
    fun deepTreeEmitsWithoutStackOverflow() {
        // 发射器为递归实现，深度上界与 JVM 默认栈深同阶（与解析器/作用域遍历同类约束；
        // 实测 1000 层越界、200 层稳定——上界裁决与缓解方案见 ADR-6）。
        val depth = 200
        var prop: HirProp = HirPAtom("p", emptyList(), nid = 1, origin = pt(1, 1))
        repeat(depth) { i -> prop = HirPNot(prop, nid = i + 2, origin = pt(1, 1)) }
        val file = HirFile(depth + 4, pt(1, 1), entries = listOf(HirStmtEntry(
            HirAxiom(props = listOf(prop), nid = depth + 3, origin = pt(1, 1)),
        )))
        val json = HirJson.emit(file, DiagBag(), "deep.subl")
        // 结构健全性：嵌套层级全数发射（HirPNot 计数=深度）、括号平衡（深树正确发射的证据）
        assertEquals(depth, Regex(""""kind": "HirPNot"""").findAll(json).count())
        assertEquals(1, Regex(""""kind": "HirPAtom"""").findAll(json).count())
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertEquals(json.count { it == '[' }, json.count { it == ']' })
    }

    @Test
    fun writeToArtifactFile() {
        val dir = File(System.getProperty("java.io.tmpdir"), "hirjson-artifact")
        val target = File(dir, "bounded.hir.json")
        target.delete()
        HirJson.writeTo(target, exampleA(), DiagBag(), "bounded.subl")
        assertTrue(target.exists())
        assertEquals(HirJson.emit(exampleA(), DiagBag(), "bounded.subl"), target.readText(Charsets.UTF_8))
    }

    companion object {
        // 金样：与 emit 逐字节对账（由 dumpExamples 落盘产物回填）
        private const val EXAMPLE_A_JSON = """{
  "schema": "sublimation/hir@v3",
  "compiler": {
    "version": "0.1-SNAPSHOT"
  },
  "unit": {
    "path": "bounded.subl",
    "mode": "project"
  },
  "ok": true,
  "diagnostics": [],
  "hir": {
    "kind": "HirFile",
    "nid": 1,
    "pos": "1:1-4:1",
    "notes": [],
    "entries": [
      {
        "kind": "HirFunDecl",
        "nid": 2,
        "pos": "1:1-4:1",
        "notes": [],
        "name": "bounded",
        "symbol": {
          "id": 1,
          "kind": "fun",
          "name": "bounded"
        },
        "annotations": [],
        "theory": [],
        "params": [
          {
            "kind": "HirParam",
            "nid": 3,
            "pos": "1:13-1:14",
            "notes": [],
            "name": "n",
            "type": {
              "kind": "HirNamedType",
              "nid": 4,
              "pos": "1:16-1:19",
              "notes": [],
              "name": "Nat",
              "args": [],
              "symbol": {
                "id": 0,
                "kind": "builtin",
                "name": "Nat"
              }
            },
            "symbol": {
              "id": 2,
              "kind": "param",
              "name": "n"
            },
            "annotations": [],
            "default": null,
            "destruct": null
          }
        ],
        "preps": [],
        "retType": {
          "kind": "HirNamedType",
          "nid": 5,
          "pos": "1:23-1:26",
          "notes": [],
          "name": "Nat",
          "args": [],
          "symbol": {
            "id": 0,
            "kind": "builtin",
            "name": "Nat"
          }
        },
        "posts": [],
        "constraints": [],
        "purity": "pure",
        "async": false,
        "body": {
          "kind": "HirBlock",
          "nid": 6,
          "pos": "1:27-4:1",
          "notes": [],
          "stmts": [
            {
              "kind": "HirExprStmt",
              "nid": 13,
              "pos": "3:3-3:4",
              "notes": [],
              "expr": {
                "kind": "HirNameRef",
                "nid": 14,
                "pos": "3:3-3:4",
                "notes": [],
                "name": "n",
                "symbol": {
                  "id": 2,
                  "kind": "param",
                  "name": "n"
                },
                "type": {
                  "kind": "HirNamedType",
                  "nid": 15,
                  "pos": "1:16-1:19",
                  "notes": [],
                  "name": "Nat",
                  "args": [],
                  "symbol": {
                    "id": 0,
                    "kind": "builtin",
                    "name": "Nat"
                  }
                }
              }
            }
          ]
        }
      }
    ]
  }
}"""
        private const val EXAMPLE_B_JSON = """{
  "schema": "sublimation/hir@v3",
  "compiler": {
    "version": "0.1-SNAPSHOT"
  },
  "unit": {
    "path": "chain.subl",
    "mode": "project"
  },
  "ok": true,
  "diagnostics": [],
  "hir": {
    "kind": "HirFile",
    "nid": 1,
    "pos": "1:1",
    "notes": [],
    "entries": [
      {
        "kind": "HirBecause",
        "nid": 21,
        "pos": "3:3-3:35",
        "notes": [],
        "form": {
          "kind": "HirPForall",
          "nid": 22,
          "pos": "3:4-3:17",
          "notes": [],
          "names": [
            "n"
          ],
          "body": {
            "kind": "HirPAtom",
            "nid": 23,
            "pos": "3:10-3:16",
            "notes": [],
            "head": ">",
            "terms": [
              {
                "kind": "HirTName",
                "nid": 24,
                "pos": "3:10-3:11",
                "notes": [],
                "name": "n",
                "symbol": {
                  "id": 2,
                  "kind": "param",
                  "name": "n"
                }
              },
              {
                "kind": "HirTInt",
                "nid": 25,
                "pos": "3:15-3:16",
                "notes": [],
                "value": "0"
              }
            ]
          }
        },
        "premises": [
          {
            "kind": "HirPAtom",
            "nid": 31,
            "pos": "4:3",
            "notes": [],
            "head": "p1",
            "terms": []
          }
        ],
        "conclusion": {
          "kind": "HirPAtom",
          "nid": 32,
          "pos": "5:3",
          "notes": [],
          "head": "p2",
          "terms": []
        },
        "proofContext": {
          "facts": [
            {
              "kind": "HirPAtom",
              "nid": 33,
              "pos": "6:3",
              "notes": [],
              "head": "Q",
              "terms": []
            }
          ],
          "matched": {
            "id": 5,
            "kind": "theorem",
            "name": "allPositive"
          }
        },
        "rule": "R-MP",
        "verdict": {
          "status": "failed",
          "diagCode": "E-CANNOT-PROVE"
        }
      }
    ]
  }
}"""
    }

    // ============ P-8/A 增量：receiver 与 Self（卷 TS-28/29/31，直接构造 HIR，不依赖 parser） ============

    @Test
    fun freeFunctionOmitsReceiverField() {
        val free = HirFunDecl(name = "free", nid = 1, origin = pt(1, 1))
        val json = HirJson.emit(HirFile(2, pt(1, 1), entries = listOf(HirDeclEntry(free))), DiagBag(), "free.subl")
        assertFalse(json.contains("\"receiver\""), "TS-31：自由函数省略 receiver 字段")
    }

    @Test
    fun extensionFunctionEmitsReceiverWithNestedArgs() {
        val receiver = HirNamedType(
            "List",
            args = listOf(HirNamedType("Optional", args = listOf(HirNamedType("A", nid = 3, origin = pt(1, 20))), nid = 2, origin = pt(1, 10))),
            nid = 1, origin = pt(1, 1),
        )
        val ext = HirFunDecl(name = "fmap", receiver = receiver, nid = 4, origin = pt(1, 1))
        val json = HirJson.emit(HirFile(5, pt(1, 1), entries = listOf(HirDeclEntry(ext))), DiagBag(), "ext.subl")
        assertTrue(json.contains("\"receiver\": {"), "TS-31：扩展函数 receiver 必现")
        val recvBlock = json.substring(json.indexOf("\"receiver\""))
        assertTrue(recvBlock.contains("\"name\": \"List\""), "TS-28：接收者类型头原样保留")
        assertTrue(recvBlock.contains("\"name\": \"Optional\"") && recvBlock.contains("\"name\": \"A\""), "TS-28：嵌套类型实参原样保留，不得丢弃")
    }

    @Test
    fun selfTypeEmitsFixedKSelfForm() {
        val m = HirFunDecl(name = "m", retType = HirSelfType(nid = 2, origin = pt(1, 10)), nid = 1, origin = pt(1, 1))
        val json = HirJson.emit(HirFile(3, pt(1, 1), entries = listOf(HirDeclEntry(m))), DiagBag(), "self.subl")
        assertTrue(json.contains("\"k\": \"self\""), "TS-31：HirSelfType 固定输出 {\"k\": \"self\"}")
        assertFalse(json.contains("HirSelfType"), "固定形态不带 kind/nid/pos/notes（判定在检查层）")
    }

    @Test
    fun receiverParticipatesInStructuralEquality() {
        // P3-STRUCT-EQ（D-9 口径）：新增字段须纳入 equals/hashCode；nid/origin/notes 的既有口径不变
        val a = HirFunDecl(name = "f", receiver = HirNamedType("Nat", nid = 7, origin = pt(2, 1)), nid = 1, origin = pt(1, 1))
        val b = HirFunDecl(name = "f", receiver = HirNamedType("Nat", nid = 7, origin = pt(2, 1)), nid = 1, origin = pt(1, 1))
        val free = HirFunDecl(name = "f", nid = 1, origin = pt(1, 1))
        assertEquals(a, b, "receiver 相同 → 结构相等")
        assertEquals(a.hashCode(), b.hashCode(), "hashCode 一致")
        assertNotEquals(a, free, "receiver 不同（扩展 vs 自由）→ 不等：新字段已纳入 equals")
    }
}
