package sublimation.frontend.semantic

import sublimation.frontend.ast.AnonFunExpr
import sublimation.frontend.ast.AssignStmt
import sublimation.frontend.ast.BinExpr
import sublimation.frontend.ast.BlockExpr
import sublimation.frontend.ast.AstNode
import sublimation.frontend.ast.AxiomStmt
import sublimation.frontend.ast.BotExpr
import sublimation.frontend.ast.BTSStmt
import sublimation.frontend.ast.ByExpr
import sublimation.frontend.ast.ByStmt
import sublimation.frontend.ast.CallExpr
import sublimation.frontend.ast.TraitDecl
import sublimation.frontend.ast.Decl
import sublimation.frontend.ast.DeclEntry
import sublimation.frontend.ast.ErrorDecl
import sublimation.frontend.ast.ErrorExpr
import sublimation.frontend.ast.ErrorPattern
import sublimation.frontend.ast.ErrorStmt
import sublimation.frontend.ast.ErrorType
import sublimation.frontend.ast.ExistIntroStmt
import sublimation.frontend.ast.ExistsElimStmt
import sublimation.frontend.ast.Expr
import sublimation.frontend.ast.ExprStmt
import sublimation.frontend.ast.FieldExpr
import sublimation.frontend.ast.FileAst
import sublimation.frontend.ast.FloatLit
import sublimation.frontend.ast.FlowExpr
import sublimation.frontend.ast.FunDecl
import sublimation.frontend.ast.FunRefExpr
import sublimation.frontend.ast.FunStmt
import sublimation.frontend.ast.IfExpr
import sublimation.frontend.ast.IfPropStmt
import sublimation.frontend.ast.InstExpr
import sublimation.frontend.ast.IntLit
import sublimation.frontend.ast.IsPatExpr
import sublimation.frontend.ast.LambdaExpr
import sublimation.frontend.ast.NameRef
import sublimation.frontend.ast.NamedType
import sublimation.frontend.ast.Param
import sublimation.frontend.ast.ParamRefExpr
import sublimation.frontend.ast.PatBind
import sublimation.frontend.ast.PatCtor
import sublimation.frontend.ast.PatIs
import sublimation.frontend.ast.PatLit
import sublimation.frontend.ast.PatTuple
import sublimation.frontend.ast.Pattern
import sublimation.frontend.ast.QualifiedType
import sublimation.frontend.ast.QuantExpr
import sublimation.frontend.ast.ReturnStmt
import sublimation.frontend.ast.ReturnSym
import sublimation.frontend.ast.StrLit
import sublimation.frontend.ast.StrategyStmt
import sublimation.frontend.ast.StructCtorExpr
import sublimation.frontend.ast.StructDecl
import sublimation.frontend.ast.Stmt
import sublimation.frontend.ast.StmtEntry
import sublimation.frontend.ast.SymbolCallExpr
import sublimation.frontend.ast.TaskExpr
import sublimation.frontend.ast.TheoryParam
import sublimation.frontend.ast.TopExpr
import sublimation.frontend.ast.TupleExpr
import sublimation.frontend.ast.Type
import sublimation.frontend.ast.TypeAliasDecl
import sublimation.frontend.ast.TypeParam
import sublimation.frontend.ast.TypeTestExpr
import sublimation.frontend.ast.BorrowExpr
import sublimation.frontend.ast.UniExpr
import sublimation.frontend.ast.UncheckedStmt
import sublimation.frontend.ast.VarExpr
import sublimation.frontend.ast.VarStmt
import sublimation.frontend.ast.WhenArm
import sublimation.frontend.ast.WhenExpr
import sublimation.frontend.ast.WhenPropStmt
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.ident
import sublimation.frontend.diag.NodeId
import sublimation.frontend.diag.SourcePos
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.hir.HirAxiom
import sublimation.frontend.hir.HirBecause
import sublimation.frontend.hir.HirBinExpr
import sublimation.frontend.hir.HirBlock
import sublimation.frontend.hir.HirByExpr
import sublimation.frontend.hir.HirAnonFun
import sublimation.frontend.hir.HirByStmt
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirTraitDecl
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirErrorDecl
import sublimation.frontend.hir.HirErrorExpr
import sublimation.frontend.hir.HirErrorPattern
import sublimation.frontend.hir.HirErrorStmt
import sublimation.frontend.hir.HirErrorType
import sublimation.frontend.hir.HirExistsElim
import sublimation.frontend.hir.HirExistsIntro
import sublimation.frontend.hir.HirExpr
import sublimation.frontend.hir.HirExprStmt
import sublimation.frontend.hir.HirField
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirFloatLit
import sublimation.frontend.hir.HirFunType
import sublimation.frontend.hir.HirFlow
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirFunRef
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
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirParam
import sublimation.frontend.hir.HirPatBind
import sublimation.frontend.hir.HirPatCond
import sublimation.frontend.hir.HirPatCtor
import sublimation.frontend.hir.HirPatElse
import sublimation.frontend.hir.HirPatIs
import sublimation.frontend.hir.HirPatLit
import sublimation.frontend.hir.HirPatTuple
import sublimation.frontend.hir.HirPNot
import sublimation.frontend.hir.HirProofContext
import sublimation.frontend.hir.HirProofStatus
import sublimation.frontend.hir.HirProp
import sublimation.frontend.hir.HirPurity
import sublimation.frontend.hir.HirReturn
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirStmt
import sublimation.frontend.hir.HirStrLit
import sublimation.frontend.hir.HirStrategy
import sublimation.frontend.hir.HirStructCtor
import sublimation.frontend.hir.HirStructDecl
import sublimation.frontend.hir.HirTask
import sublimation.frontend.hir.HirTuple
import sublimation.frontend.hir.HirTypeAlias
import sublimation.frontend.hir.HirType
import sublimation.frontend.hir.HirTypeParam
import sublimation.frontend.hir.HirTName
import sublimation.frontend.hir.HirTypeTest
import sublimation.frontend.hir.HirTop
import sublimation.frontend.hir.HirBot
import sublimation.frontend.hir.HirBorrow
import sublimation.frontend.hir.HirUniExpr
import sublimation.frontend.hir.HirUnionType
import sublimation.frontend.hir.HirUnchecked
import sublimation.frontend.hir.HirVarExpr
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.hir.HirVerdict
import sublimation.frontend.hir.HirWhen
import sublimation.frontend.hir.HirWhenArm
import sublimation.frontend.hir.SymbolKind
import sublimation.frontend.hir.SymbolRef
import sublimation.frontend.parser.ParseNote

/**
 * 阶段二 elaborate（ADR-6 §二/§四 4.3 单一驱动）：语义层状态的唯一全权消费者——一次深度遍历，
 * 边解析边把全部 t7 结论上树（ADR-6 结论上树原则）：
 * - 值/类型/构造子名解析结论落 [SymbolRef]（未解析 → 诊断 + symbol=null 半成品，ADR-1 §3.2）；
 * - 消解闭名单（ADR-1 §3.1.2）在构造期改写：方法糖 / 命名参数重排 / $n → 形参名 / when 槽位归一，
 *   每条闭形态留 D-* 留痕于对应 HirNode.notes（替代旧实现的旁挂表与原地改写）；
 * - 符号表帧栈 [SymbolTable.frame]（变量遮蔽内→外）+ 类型参数词法栈（Γ 快照，ADR-6 §四）；
 * - 解析期 ParseNote 搬移至节点（ADR-1 §3.5）。
 *
 * 纯函数契约（ADR-6 §二）：AST 只读、symbols 全局层只读（t7 单文件无全局修改源——帧栈是瞬态的），
 * 一切结论经返回值/诊断传递；nid 前序单调（深度遍历构造顺序）。
 *
 * 接口裁决（ADR-10）：命题位不可表达形态落 E-PROP-FORM 半成品（ADR-5 决策 3）；命题专属语法出现在
 * 程序位落 E-PROP-POS；载荷构造子裸名分臂落 E-CTOR-ARITY；字段/方法/dict 符号解析属 t8/t9
 * 类型导向层——构造期 symbol=null 静默。
 */
internal fun elaborate(
    file: FileAst,
    symbols: SymbolTable,
    notes: Map<AstNode, List<ParseNote>>,
    nids: NodeIdSource,
    /** D-12:模式真值（compile 透传;默认 project——CLI 为唯一源） */
    scriptMode: Boolean = false,
    varianceSink: ((VarianceInfer) -> Unit)? = null,
): Pair<HirFile, DiagBag> {
    val diag = DiagBag()
    val el = Elaborator(symbols, notes, diag, nids, scriptMode)
    val hir = el.file(file)
    // t96（N1 纯度闸 · 阶段 A 报告态，R2 §一）:elaboration 完成后回看整树（lambda 纯度经 t94 推导底向可知）
    el.runPurityGate(hir)
    el.acceptVariance(varianceSink)
    return hir to diag
}

/** t117（S2 落地 C，附录 B 修订口径）：**R 轮重跑的单轮执行**——compileWithRounds 驱动器每轮调一次。
 *  - 每轮 new Elaborator（全部推导状态实例内——t114 体检结论「累积成员清单为空」成立）⇒ 每轮一全新
 *    FUEL_DEFAULT 燃料池（label 标记轮次——H3 R 池观测落表 OQ-2）。
 *  - injectedSolutions（H2 轮外驱动解映射）经构造参数注入——零改 SymbolTable。
 *  - sink 轮内接管（H8）：遮蔽提示 / 原生命题占用下沉到轮内袋（编译级袋只供 collect 单趟）。
 *  - 收敛判据（C-1 / P-11 §3.5）：逐元变量 **typeEq 结构相等**（复用 TypeInfer.typeEq——同 ADR-39
 *    判据口径），停于首个收敛轮；证据因使用位点静态（同输入同轮）不参与比对。
 *  - 中间轮 HIR/诊断一律弃，仅取终轮（半成品树原则：未收敛终态=最后轮 HIR + 工程诊断红掉）。 */
internal fun elaborateRound(
    file: FileAst,
    symbols: SymbolTable,
    notes: Map<AstNode, List<ParseNote>>,
    nids: NodeIdSource,
    scriptMode: Boolean,
    injected: Map<MetaSlotKey, MetaSolution>,
    roundLabel: String,
    degradedFuns: Set<String> = emptySet(),
    /** t123（R-16 S4 / OQ-5）：--no-lift 透传（上提即失败的 CI 门）。 */
    noLift: Boolean = false,
    /** t125（S5b / R-06）：轮外驱动可变性提升映射（上一轮需求，声明位预应用）。 */
    mutPromoted: Map<String, List<SourceRange>> = emptyMap(),
): InferenceRound {
    val diag = DiagBag()
    // t117 H8：sink 轮内接管（编译期袋的 collect 单趟专用，轮内重复落袋消除）
    wireShadowHint(symbols, diag)
    wireNativeRecl(symbols, diag)
    val el = Elaborator(symbols, notes, diag, nids, scriptMode, injected, roundLabel, degradedFuns, noLift, mutPromoted)
    el.setFuelLabel(roundLabel)
    val hir = el.file(file)
    el.runPurityGate(hir)
    val solutions = el.metaSolutions()
    val changed = mutableSetOf<String>()
    val changedSolutions = linkedMapOf<MetaSlotKey, MetaSolution>()   // t118：R-02 退化空位集（按空位局部退化）
    var converged = true
    solutions.forEach { (k, s) ->
        val prev = injected[k]
        if (prev == null || !el.typeEq(s.type, prev.type)) {
            converged = false; changed.add(s.owner); changedSolutions[k] = s
        }
    }
    if (injected.keys.any { it !in solutions.keys }) converged = false   // 防御：键集缩（声明面静态 ⇒ 不发生）
    val metaCount = el.metaCount()
    if (metaCount == 0) converged = true                                  // 零元变量直通（保护条款）
    return InferenceRound(hir, diag, solutions, metaCount, converged, changed, changedSolutions, el.fuelMaxConsumed(),
        el.gateClosureSize,   // t122：本轮闭包规模（runPurityGate 记）
        el.mutPromotions())   // t125（S5b）：本轮提升需求导出
}

/** t66（ADR-43）：compileNarrowAudit 的配套——收窄审计观察通道（不改变 elaborate 行为）。 */
internal fun elaborateNarrow(
    file: FileAst, symbols: SymbolTable, notes: Map<AstNode, List<ParseNote>>, nids: NodeIdSource,
    scriptMode: Boolean = false,
    sink: ((NarrowObserver) -> Unit)? = null,
): Pair<HirFile, DiagBag> {
    val diag = DiagBag()
    val el = Elaborator(symbols, notes, diag, nids, scriptMode)
    val hir = el.file(file)
    sink?.invoke(el.narrowObserver())
    return hir to diag
}

/** t66：收窄审计只读视图（不暴露私有 Elaborator）。t110:measure 证明审计同视图。 */
internal class NarrowObserver(
    val attempts: Int,
    val trustedBase: List<String>,
    val measureProofs: List<MeasureProof> = emptyList(),
)

/** t110（OQ-5）：停机闸 measure 证明审计条目（compileNarrowAudit 族只读视图）。 */
internal data class MeasureProof(
    val fn: String,
    val goal: String,
    val rule: String,
    val evidence: String,
)

/** t64（ADR-41）：compileVariance 的配套——elaborate 结束后取已建的变构表（无泛型声明则空表）。 */
internal fun elaborateWithVariance(
    file: FileAst, symbols: SymbolTable, notes: Map<AstNode, List<ParseNote>>, nids: NodeIdSource,
    scriptMode: Boolean = false,
): Triple<HirFile, DiagBag, Map<String, List<Pair<String, VariancePair>>>> {
    var table: Map<String, List<Pair<String, VariancePair>>> = emptyMap()
    val (hir, diag) = elaborate(file, symbols, notes, nids, scriptMode) { v -> table = v.table() }
    return Triple(hir, diag, table)
}

/** t66（ADR-43，ST-10.2）：收窄审计——证明尝试次数 + 信任基（必要公理渲染）。仅供工具/测试观察（生产路径 compile）。 */
internal data class NarrowAudit(
    val attempts: Int,
    val trustedBase: List<String>,
    val measureProofs: List<MeasureProof> = emptyList(),
)

internal fun elaborateWithNarrowAudit(
    file: FileAst, symbols: SymbolTable, notes: Map<AstNode, List<ParseNote>>, nids: NodeIdSource,
    scriptMode: Boolean = false,
): Triple<HirFile, DiagBag, NarrowAudit> {
    var audit: NarrowAudit = NarrowAudit(0, emptyList())
    val (hir, diag) = elaborateNarrow(file, symbols, notes, nids, scriptMode) { o ->
        audit = NarrowAudit(o.attempts, o.trustedBase, o.measureProofs)
    }
    return Triple(hir, diag, audit)
}

/** 模式位（臂顶层（ctor 裁定）vs 解构槽位（新名绑定/旧名相等比较），S-42）。 */
private enum class PatternMode { TOP, SLOT }

/**
 * t109（R3-V1 §四 P2,C-2 提前;R-11/R-12 裁决）:[ProverOracle] 实现体——混合编码形态 C 的判据通道:
 *
 * - **judgeSubtype**（裁决 1):`<:` 原子的 A-SUBTYPE 段委托 TypeInfer.subtype——算法判据本体经此调用,
 *   不旁路 ADR-39 唯一判据;置换原则（R-12 正式规则）保 proof:* 位必可查算法兜底;
 * - **termType/fitsLiteral**（裁决 2):(e:T) 通道:项经符号表/数值阶层判型;
 * - **definitionalExpand**（裁决 3):单表达式纯函数（表达式体 + 无 @impure,声明位源序登记）的
 *   ∀x. f(x)=body 代换——P-12 裁决 B 范围,深归约无终止依据不展开（深度上界在 ProofChecker）;
 * - **PROOF_BUDGET**（裁决 4/OQ-2):每编译单元预算界限,饱和轮/R-SUBST/P2 通道同池消费;耗尽=可观测
 *   形态（通道足迹经 D-PROOF-BUDGET 落诊断,去重一次/单元）,证不出回落兜底或硬拒——不发明新放行。
 *
 * 合成 atom 的 provenance=C-8 分账:user（用户书写）/synthetic（编译器合成——类型事实族/narrowMinus
 * 收窄目标族）;trustedBase 留一审计只认 user 公理来源（axiomFacts）,synthetic 永不入信任基。
 */

/**
 * t115（S2 落地 B,P-11 §3.1/§3.2/§3.3）：推导元变量——四空位（形参/返回/字段/局部）的待解占位。
 *
 * - **内存对象**：不落 HIR、不贡献 nid（D-14 单计数器纪律）；结论写回 t108 载体槽位（type + inferred + evidence）。
 * - **约束袋**：[lower]/[upper] =（类型,使用位）二元组——下界来自调用实参/返回位/初值/构造位；上界来自
 *   使用位对既有类型的诉求（体内再调用传参等）。**复用既有判据**（subtype/adaptOk），不另立。
 * - **证据** [evidence] = 约束源位置（"use:行:列"）源序去重——t108 回填契约 + t110 trustedBase 同款诚实性。
 * - **求解**：候选 = 下界 lub（正位口径——四空位本皆正位；负位/取交容器位随 t116 极性复用而来）;
 *   无下界 → 默认填充（P-11 §3.6：形参 Nothing）;候选须过全部上界（subtypeBody），否则 E-INFER-NO-SOLUTION。
 * - **单轮边界（t115）**：求解在声明构造位收口——「后到达」的约束登记但不消费。
 * - **R 轮收口（t117，S2 落地 C）**：声明位不再闭合，全部求解移至轮末 metaSolutions()——后到约束
 *   随轮复解消化（D-INFER-PENDING 观察级退役）；种子=上轮终解作迭代起点（typeEq 结构相等收敛判据）。
 */
/** t117（附录 B H2）：轮外驱动解映射键——（空位种类, 声明 AST 实体, 位序）。
 *  **身份相等**（AST 不可变输入、跨轮同一对象）——结构相等会令两同名同构声明撞键；identityHashCode 定位。
 *  解不经符号条目传递（SymbolEntry 只读、ADR-6 只读边界），本键是 compile 层 Map 的唯一索引。 */
internal class MetaSlotKey(
    private val kind: MetaSlotKind, private val decl: AstNode, private val index: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is MetaSlotKey && kind == other.kind && decl === other.decl && index == other.index
    override fun hashCode(): Int = System.identityHashCode(decl) * 31 + index * 7 + kind.ordinal
    override fun toString(): String = kind.label + "#" + index
}

/** t117：轮间解传递值——解类型 + 证据（t108 契约：使用位点静态、源序稳定，跨轮携带可追溯）。
 * t118（R-02 通道本体）：
 *  - [uppers] = 上轮收纳的上界约束（跨轮保形——无解线 E-INFER-NO-SOLUTION 双模式硬拒的轮间证据）；
 *  - [unsolvable] = 本轮判过无解（退化禁区——健全性优先，不可退化）；
 *  - [degraded] = R-02 退化标记（空位填 Any；evidence 留作退化诊断的**证据尝试清单**，
 *    不进 t108 载体——载体侧 inferred=false 零发射）。 */
internal class MetaSolution(
    val type: sublimation.frontend.hir.HirType,
    val evidence: List<String>,
    val owner: String,
    val uppers: List<Pair<sublimation.frontend.hir.HirType, SourceRange>> = emptyList(),
    val unsolvable: Boolean = false,
    val degraded: Boolean = false,
)

private class InferMeta(
    val kind: MetaSlotKind,
    val owner: String,         // 拥有者名（函数名/结构体名）
    val name: String,          // 空位名（形参名/字段名；RET 取函数名）
    val origin: SourceRange,   // 声明位（可追溯）
    val key: MetaSlotKey,      // t117 轮外驱动键
) {
    val lower = mutableListOf<Pair<sublimation.frontend.hir.HirType, SourceRange>>()
    val upper = mutableListOf<Pair<sublimation.frontend.hir.HirType, SourceRange>>()
    /** t117：证据按源序去重（t108 契约）——种子证据与本轮收据合并同一集合。 */
    private val evidenceSet = linkedSetOf<String>()
    var resolved: sublimation.frontend.hir.HirType? = null
        private set
    /** t117：E-INFER-NO-SOLUTION 每元变量每轮一次（export 复解不重复落袋）。 */
    private var noSolutionReported = false
    /** t118（R-02）：退化空位旗——驱动器注入 Any 种子时置位：载体侧 inferred=false（退化≠推导，
     *  Any 填充不冒充有证据的解）；轮内复解/证据重收跳过（保 Any 落位）。 */
    internal var degradedSlot: Boolean = false
        private set

    /** 占位名（唯一）："(推导:元变量:形参 f.x"——反查通道键；isMetaSlot 判据此前缀。 */
    val slotName: String = "(推导:元变量:" + kind.label + " " + owner + "." + name + ")"

    fun slot(infer: TypeInfer): sublimation.frontend.hir.HirType = infer.namedT(slotName, pos = origin)

    /** 占位是否已作为约束证据落过（决定求解后 inferred 标记 vs 默认填充）。 */
    fun hasEvidence(): Boolean = evidenceSet.isNotEmpty()

    fun addEvidence(pos: SourceRange): Boolean = evidenceSet.add(evidenceOf(pos))

    fun evidenceList(): List<String> = evidenceSet.toList()

    /** t117：种入上一轮终解 + 证据（param()/签名位的轮 k 起点）。 */
    fun seed(t: sublimation.frontend.hir.HirType, evidence: List<String>) {
        resolved = t
        evidenceSet.addAll(evidence)
    }

    fun resolve(t: sublimation.frontend.hir.HirType) { resolved = t }

    /** t117：export 复解允许覆盖（后到约束消化）。 */
    fun reResolve(t: sublimation.frontend.hir.HirType) { resolved = t }

    fun reportNoSolutionOnce(): Boolean = !noSolutionReported.also { noSolutionReported = it }

    /** t118：无解旗（导出口径——驱动器据此把无解空位排除出退化集）。 */
    fun noSolution(): Boolean = noSolutionReported

    /** t118（R-02）：标记退化空位（seedSlot 专用——Any 种子落位）。 */
    fun markDegraded() { degradedSlot = true }

    companion object {
        fun evidenceOf(pos: SourceRange): String = "use:" + pos.start.line + ":" + pos.start.col
    }
}

internal enum class MetaSlotKind(val label: String) {
    PARAM("形参"), RET("返回"), FIELD("字段");
}

private class ElaboratorOracle(
    private val infer: TypeInfer,
    private val symbols: SymbolTable,
    private val propNormZero: PropNormalizer,
    private val diag: DiagBag,
    private val alloc: () -> NodeId,
) : ProverOracle {
    /** 单表达式纯函数定义表:name →（形参名, 体项）;声明位源序登记——后定义者对早义务不可见。 */
    private val defRules = mutableMapOf<String, Pair<List<String>, sublimation.frontend.hir.HirTerm>>()
    private var spent = 0
    private val channelTraces = mutableListOf<String>()
    private var budgetReported = false

    /** 声明位登记:表达式体可命题项化 + 无 @impure 注解（纯度同 gate 判据）才作定义性归约规则。 */
    fun registerFunDef(d: sublimation.frontend.ast.FunDecl) {
        if ("impure" in d.annotations) return
        val body = d.body ?: return
        val bodyTerm = propNormZero.termQuiet(body) ?: return      // 块体/不可命题化形态 → null,不登记
        defRules[d.name] = d.params.map { it.name } to bodyTerm
    }

    override fun judgeSubtype(lo: sublimation.frontend.hir.HirType, hi: sublimation.frontend.hir.HirType): Boolean =
        infer.subtype(lo, hi)

    override fun termType(t: sublimation.frontend.hir.HirTerm): sublimation.frontend.hir.HirType? =
        (t as? sublimation.frontend.hir.HirTName)?.let { symbols.typeOfName(it.name) }

    override fun fitsLiteral(value: String, t: sublimation.frontend.hir.HirType): Boolean {
        val n = value.toLongOrNull() ?: return false
        val rank = (t as? sublimation.frontend.hir.HirNamedType)?.name?.let {
            when (it) { "Nat" -> 0; "Int" -> 1; "Rat" -> 2; else -> null }
        } ?: return false
        return rank >= 1 || n >= 0L      // Nat 需值 ≥ 0;Int/Rat 接整数（数值阶层同 TypeInfer.rank 口径）
    }

    override fun definitionalExpand(name: String, args: List<sublimation.frontend.hir.HirTerm>): sublimation.frontend.hir.HirTerm? {
        val rule = defRules[name] ?: return null
        val (names, body) = rule
        if (args.size != names.size) return null
        return substTermAlloc(body, names.zip(args).toMap(), { 0 })   // 瞬态项:零 nid,不耗全局计数器（同 reduceSubterms）      // ProofChecker.substTerm(项级代换,形参→实参）
    }

    override fun spend(n: Int): Boolean {
        if (spent + n > PROOF_BUDGET) return false
        spent += n
        return true
    }
    override fun trace(channel: String) { channelTraces.add(channel) }
    override fun snapshotTraces(): List<String> = channelTraces.toList()
    override fun budgetLabel(): String = "PROOF_BUDGET=" + PROOF_BUDGET + "（已耗 " + spent + "）"
    override fun budgetReported(): Boolean = budgetReported
    override fun markBudgetReported() { budgetReported = true }

    companion object {
        /** 预算界限（OQ-2）:每编译单元证明尝试总预算——源序消费,同输入同停;耗尽回落兜底/硬拒。 */
        internal const val PROOF_BUDGET = 4096
    }
}

private class Elaborator(
    private val symbols: SymbolTable,
    private val parseNotes: Map<AstNode, List<ParseNote>>,
    private val diag: DiagBag,
    private val nids: NodeIdSource,
    /** D-12:模式真值（CLI 唯一源透传;旧「顶层语句存在⇒脚本」自算已废——工程模式顶层 axiom 误标） */
    private val scriptMode: Boolean,
    /** t117（附录 B H2）：轮外驱动解映射——上一轮终解（+ 证据）按 MetaSlotKey 注入；空=首轮（R=1）。 */
    private val injectedSolutions: Map<MetaSlotKey, MetaSolution> = emptyMap(),
    /** t117（附录 B H3）：按轮燃料 label（每轮新 Elaborator=每轮新 FUEL_DEFAULT 池；观测定分布 OQ-2）。 */
    private val roundLabel: String? = null,
    /** t117（分型表 §3.1，R-02 触发点）：不收敛拥有者函数 → 退化轮内 impure 化（本体 t118）。 */
    private val degradedFuns: Set<String> = emptySet(),
    /** t123（R-16 S4 / OQ-5 队长裁=给）：--no-lift——脚本模式「上文上提即失败」CI 门（同 --no-degrade 双口形态）。 */
    private val noLift: Boolean = false,
    /** t125（S5b / R-06）：轮外驱动可变性提升映射——上一轮登记的提升需求（声明位键 "行:列#名" → 赋值位
     *  证据清单）。单调（只加不减）+ 静态键（AST 声明位 + 名跨轮稳定）⇒ **一轮预应用重跑即收敛**
     * （声明先于赋值，词法序天然无环）。工程模式恒空（R-06 工程侧声明制）。 */
    private val mutPromoted: Map<String, List<SourceRange>> = emptyMap(),
) {
    private val resolver = NameResolver(symbols, diag)

    /** t8 推导器（纯函数插入件：不自走树，由构造分支调用，ADR-6 §四 4.3）。 */
    // t44（ADR-29）：注入联合别名展开件——adaptOk 的实参具名联合别名（Optional[Json] 等未展开形态）比对用
    // t62（ADR-39）：注入偏序侧件——traitSubtype（满足即子类型 + 特质父链）、structFieldsOf（结构满足）。
    private val infer = TypeInfer(::freshNid, diag, { t: sublimation.frontend.hir.HirType -> expand(t) }, ::traitSub, ::structFieldTypes,
        // t64（ADR-41，ST-13.1）：同名同参应用的逐参变构性——TraitParams→(σ_imm, σ_mut) 按当前视图取用
        { n: String, i: Int -> variance().varianceOf(n, i) })

    // t98（N2 @ref · 规则 4）:PropNormalizer 的名字 → 类型查表 + 渲染（命题待遇的视图项原料）
    private val propNorm = PropNormalizer(::freshNid, resolver, diag, ::isTypeParam, { name ->
        symbols.lookupValue(name)?.type
    }, infer::render)
    /** t109 定义性归约的体项转换位:零分配 nid——瞬态项不入树,不移动全局计数器
     *  （68 样本逐字比对基线的硬约束:任何额外全局 nid 消费都会漂移 nid 位）。 */
    private val propNormZero = PropNormalizer({ 0 }, resolver, diag, ::isTypeParam, { name ->
        symbols.lookupValue(name)?.type
    }, infer::render)

    private val typeParams = ArrayDeque<MutableSet<String>>()

    /** 声明索引（file 预扫描：调用点的签名/字段/构造子来源；AST 只读）。 */
    private val funDecls = mutableMapOf<String, FunDecl>()
    private val structDecls = mutableMapOf<String, StructDecl>()
    private val aliasDecls = mutableMapOf<String, TypeAliasDecl>()
    private val aliasExpanding = mutableSetOf<String>()

    /** t64（ADR-41）：变构性推断器——声明索引在 file() 预扫描就位后可用，首次需处惰性建表（单次不动点）。 */
    private var varianceCache: VarianceInfer? = null
    private fun variance(): VarianceInfer = varianceCache ?: VarianceInfer(traitDecls, structDecls, aliasDecls, diag).also { varianceCache = it }

    /** t64（ADR-41）：file() 结束后向 sink 交付推断器（惰性建表者——无泛型声明时保持 null）。 */
    internal fun acceptVariance(sink: ((VarianceInfer) -> Unit)?) {
        val v = varianceCache
        if (sink != null && v != null) sink(v)
    }

    init {
        // t44（ADR-29）：prelude 去枚举化——虚拟 struct / 虚拟联合别名预注入声明索引（用户同名
        // 条目随 file 预扫描覆盖之，P-7 用户优先）。虚拟声明不落 HIR/JSON 产物（非文件条目），
        // 亦不参与声明级检查（重复/环/归纳判定——prelude 内容既定良构）。
        PreludeDecls.STRUCTS.forEach { structDecls[it.name] = it }
        PreludeDecls.ALIASES.forEach { aliasDecls[it.name] = it }
    }

    /** P-8/D（卷 TS-11/TS-19）：扩展函数声明索引——(接收者类型头, 名字) → 声明集合；
     *  与自由函数 funDecls 分桶，判定基于全集（声明顺序无关，前向引用合法）。 */
    /** P-8/D（卷 TS-8/TS-17）：特质声明索引（满足判定的规范来源）。 */
    private val traitDecls = mutableMapOf<String, TraitDecl>()

    /** return 上下文帧（函数/lambda：声明返回类型 + sawReturn 计数，E-RETURN-OUTSIDE/E-MISSING-RETURN 判据）。 */
    private val funFrames = ArrayDeque<FunFrame>()

    /** t97:unchecked 语句深度（放行闸检查——不豁免效应存在，见 ADR-55 §unchecked 不对称口径）。 */
    private var uncheckedDepth = 0

    /** t125（S5b / R-06）：可变性提升需求累计（脚本模式）——赋值位按声明位键登记，轮末导出供驱动器重跑。
     *  需求=单调证据清单（赋值位源序）；非提升候选（字段 C-5 / 循环变量 / lambda 形参）不登记。 */
    private val mutDemands = linkedMapOf<String, MutableList<SourceRange>>()
    private fun mutKey(pos: SourceRange, name: String): String {
        val p = pos.start
        return p.line.toString() + ":" + p.col + "#" + name
    }
    private fun demandMut(key: String, pos: SourceRange) {
        mutDemands.getOrPut(key) { mutableListOf() }.add(pos)
    }
    /** t125：提升应用——视图包 @mut（已 @mut 形态单调穿过不重包，R-06 混标单向蕴含）。 */
    private fun promotedView(tt: HirType, origin: SourceRange): HirType =
        if (tt is sublimation.frontend.hir.HirMutType) tt
        else sublimation.frontend.hir.HirMutType(tt, freshNid(), origin)
    /** t125：D-MUT-INFERRED 审计四元组（名 + 提升视图 + 触发分类 + 证据尝试清单），supplement 级。 */
    private fun mutNote(name: String, tt: HirType, origin: SourceRange, trigger: String, evidence: List<SourceRange>) {
        diag.supplement("D-MUT-INFERRED", origin,
            ident(name) + " 可变性提升为 <" + infer.render(tt) + ">（R-06 脚本模式自动提升：" + trigger +
                ";单调只加不减;工程模式须显式 @mut)。证据：" +
                evidence.joinToString(",") { e -> "use:" + e.start.line + ":" + e.start.col })
    }

    /**
     * t97（N1 停机闸形态①）：when 臂的模式槽位来源栈——名 → when 主题名（臂级生命周期）。`
     * is Cons(h, t)` 的 t 来源为 xs ⇒ 递归实参 t 视作原参数 xs 的子结构。嵌套 when 经来源链传递。
     */
    private val patOrigins = ArrayDeque<MutableMap<String, String>>()

    /** t9 证明器（纯函数插入件：不自走树，由构造分支调用，ADR-6 §四 4.2/4.3）。 */
    /**
     * t109（R3-V1 §四 P2,R-11/R-12 裁决）:证明器判据 oracle 实现——判据本体委托 [infer]/[symbols]/[propNorm],
     * PROOF_BUDGET 预算池（每编译单元,源序消费;耗尽落 D-PROOF-BUDGET 可观测形态,不发明放行）。
     */
    private val oracleImpl = ElaboratorOracle(infer, symbols, propNormZero, diag, ::freshNid)
    private val prover = ProofChecker(::freshNid, diag, oracleImpl)

    /** Γ 帧栈（ADR-6 §四 4.4）：函数帧/命题分支帧的事实集——allFacts 沿栈并集，注入只写栈顶，
     *  检查顺序不再影响结果（旧全局 allFacts 可变池废除）。 */
    /** t65（ADR-42）：类型事实注入上界（每帧）——超出按声明顺序截断并落 H-TYPEFACT-CAP。 */
    private val TYPE_FACT_CAP = 64
    private val factFrames = ArrayDeque<MutableSet<HirProp>>()

    // —— t66（ADR-43，ST-10.2）：减法收窄（证明驱动的智能转换）——
    /** 直接期望栈：被直接适配到期望类型的子表达式（return 值/调用实参/var 值/赋值右值/默认值）
     *  的期望类型；入栈站点用 [expectAt]，BinExpr 位读取栈顶（内层站点恒为直接期望——如
     *  return f(a-b) 时 a-b 的直接期望来自 f 的形参而非外层 retT）。 */
    private val expectTypes = ArrayDeque<sublimation.frontend.hir.HirType?>()

    private inline fun <T> expectAt(expect: sublimation.frontend.hir.HirType?, block: () -> T): T {
        expectTypes.addLast(expect)
        try { return block() } finally { expectTypes.removeLast() }
    }

    /** 用户公理来源事实登记（AxiomStmt 注入位登记；trustedBase 判定原料）。 */
    private val axiomFacts = mutableSetOf<HirProp>()
    /** 收窄消费的**必要公理**（留一法：去掉后证不出者才计入）——类型/分支事实不进。 */
    private val trustedBase = mutableListOf<HirProp>()
    /** 收窄证明尝试总次数（严格触发：仅期望 Nat + 减双 Nat；期望 Int/Rat 时为 0——验收 #7）。 */
    private var narrowAttempts = 0
    private val NARROW_TRY_CAP = 8   // 每函数帧上界（源序确定性截断，不随机）

    /** t30 后缀 by 断言队列：延迟到包围语句/函数体检查完（事实注入就绪）后逐条核对。 */
    private val pendingByAsserts = mutableListOf<Pair<List<HirProp>, SourceRange>>()

    /** core.classic 挂载旗标（文件内任一声明加 @classic 注解即挂载）。 */
    private var classicMounted = false

    /** P-8/D（卷 TS-5/TS-25）：trait 体内 Self 语义标志（0 = 体外）。 */
    private var inTraitBody = 0

    /** B9（ADR-22）：lambda 实参的期望形参类型（两遍定型第二遍的供给通道；取用即还）。 */
    private var lambdaExpect: List<HirType>? = null

    // ============================ t115（S2 落地 B，P-11 §3.1/§3.2/§3.3）：推导元变量登记表 ============================
    //  纪律：元变量是内存对象——不落 HIR、不贡献 nid（D-14）；结论落四载体既有 t108 槽位（inferred/evidence）。
    //  键 = 声明 AST 实体（FunDecl/StructDecl）+ 位序；占位名（"(推导:元变量..."）→ 元变量的反查通道，
    //  供使用位点（调用实参/体内再传递）约束收集时按占位认主。零元变量单元全程不经本表（真门：单趟直通）。
    //  声明 AST 经 file() 既有全局索引（funDecls/structDecls：先于条目映射建成）按名取——零解析副作用。
    private val metaSlots = mutableMapOf<String, InferMeta>()                    // 占位名 → 元变量（反查通道）
    private val funMetas = mutableMapOf<FunDecl, Array<InferMeta?>>()            // 函数 → 元变量槽（0..n-1=形参,n=返回）
    private val structMetas = mutableMapOf<StructDecl, Array<InferMeta?>>()       // 结构体 → 字段元变量槽（声明序）

    // ---- t115 元变量机制（生成/约束收集/求解落位）+ t117 轮间种子（H2 轮外驱动解映射）----
    /** 形参元变量（脚本模式 + 省略标注位专用——已标注位不得建元变量：真门/默认值逐字不变）。 */
    private fun metaForParam(d: FunDecl, index: Int): InferMeta? {
        if (!scriptMode || index >= d.params.size || d.params[index].type != null) return null
        return slotOf(d, index, MetaSlotKind.PARAM, d.name, d.params[index].name, d.params[index].pos)
    }
    /** 返回位元变量：表达式体，或块体含 return（t117 块体 return 推导——块体无 return 保持 Null 语义）。
     *  两模式共享（工程模式允许返回类型从函数体推导，OQ-1 裁决 2）。 */
    private fun metaForRet(d: FunDecl): InferMeta? {
        if (d.retType != null || d.body == null) return null
        if (d.body is BlockExpr && !blockHasReturn(d.body)) return null
        return slotOf(d, d.params.size, MetaSlotKind.RET, d.name, d.name, d.body.pos)
    }
    /** 字段元变量（脚本模式 + 省略标注位专用）。 */
    private fun metaForField(d: StructDecl, index: Int): InferMeta? {
        if (!scriptMode || index >= d.fields.size || d.fields[index].type != null) return null
        return slotOf(d, index, MetaSlotKind.FIELD, d.name, d.fields[index].name, d.fields[index].pos)
    }
    private fun slotOf(d: FunDecl, i: Int, kind: MetaSlotKind, owner: String, name: String, origin: SourceRange): InferMeta {
        val arr = funMetas.getOrPut(d) { arrayOfNulls(d.params.size + 1) }
        var m = arr[i]
        if (m == null) {
            m = InferMeta(kind, owner, name, origin, MetaSlotKey(kind, d, i))
            seedSlot(m)   // t117 H2 轮 k 起点种子 + t118 R-02 通道本体（上界保形/退化标记）
            arr[i] = m; metaSlots[m.slotName] = m
        }
        return m
    }
    private fun slotOf(d: StructDecl, i: Int, kind: MetaSlotKind, owner: String, name: String, origin: SourceRange): InferMeta {
        val arr = structMetas.getOrPut(d) { arrayOfNulls(d.fields.size) }
        var m = arr[i]
        if (m == null) {
            m = InferMeta(kind, owner, name, origin, MetaSlotKey(kind, d, i))
            seedSlot(m)
            arr[i] = m; metaSlots[m.slotName] = m
        }
        return m
    }

    /** t118（R-02 通道本体）：轮外解映射的轮内消化——
     *  ① 常规种子：上轮终解 + 证据（t117 H2）**并补入上界约束**（按使用位去重——同位不重复记账），
     *     令无解线（lub ⊄ U ⇒ E-INFER-NO-SOLUTION）跨轮复检、双模式硬拒（约束位证据不再重收时，
     *     上界只经此通道存活——健全性优先，不可退化）；
     *  ② 退化种子（degraded=true，驱动器把不收敛空位换 Any）：落位 Any、**证据不入载体**（inferred=false、
     *     D-INFER-DEGRADED 观察级随既有 DiagBag 落袋，零新增 walker）——退化只走「不收敛且非无解」。 */
    private fun seedSlot(m: InferMeta) {
        val s = injectedSolutions[m.key] ?: return
        if (s.degraded) {
            m.resolve(infer.namedT("Any", pos = m.origin))
            m.markDegraded()
            diag.supplement("D-INFER-DEGRADED", m.origin,
                "函数 " + ident(m.owner) + " 的" + m.kind.label + "位 " + ident(m.name) + " 经 R=" +
                    INFER_ROUNDS_DEFAULT + " 轮仍未收敛（退化原因分类：R 轮耗尽·类型随轮生长/振荡）——" +
                    "按 R-02 退化通道填 Any：" + s.evidence.joinToString("、") + "（证据尝试清单）；" +
                    "所属函数记 impure（t97 停机闸跳过、t96 纯度面不纯记账）。工程模式不退化" +
                    "（E-INFER-NOT-CONVERGED 硬拒）；--no-degrade 可把脚本退化开成 CI 门")
            return
        }
        m.seed(s.type, s.evidence)
        s.uppers.forEach { up -> if (m.upper.none { it.second == up.second }) m.upper.add(up) }
    }

    /** t117（块体 return 推导）：块体是否含 return 语句——AST 谓词（只读分析，不构造、不占 nid）。
     *  块体无 return 的函数保持既有 Null 语义（不建返回位元变量——e2e 零命中，SAME 门零漂）。 */
    private fun blockHasReturn(b: BlockExpr): Boolean = b.stmts.any { stmtHasReturn(it) }
    private fun stmtHasReturn(s: Stmt): Boolean = when (s) {
        is ReturnStmt -> true
        is VarStmt -> exprHasReturn(s.value)
        is AssignStmt -> exprHasReturn(s.target) || exprHasReturn(s.value)
        is ExprStmt -> exprHasReturn(s.expr)
        is UncheckedStmt -> stmtHasReturn(s.inner)
        is FunStmt -> false               // 局部 fun 的 return 归其自身
        is AxiomStmt -> false; is ByStmt -> false; is WhenPropStmt -> false; is StrategyStmt -> false
        is ExistIntroStmt -> false; is ExistsElimStmt -> false; is IfPropStmt -> false; is BTSStmt -> false
        is ErrorStmt -> false
    }
    private fun exprHasReturn(e: Expr): Boolean = when (e) {
        is BlockExpr -> e.stmts.any { stmtHasReturn(it) }
        is IfExpr -> e.let { x -> exprHasReturn(x.cond) || exprHasReturn(x.thenBlock) || (x.elseBlock?.let { exprHasReturn(it) } ?: false) }
        is WhenExpr -> e.arms.any { exprHasReturn(it.body) }
        is CallExpr -> exprHasReturn(e.callee) || e.args.any { exprHasReturn(it) } || e.namedArgs.values.any { exprHasReturn(it) }
        is BinExpr -> exprHasReturn(e.left) || exprHasReturn(e.right)
        is UniExpr -> exprHasReturn(e.operand)
        is BorrowExpr -> exprHasReturn(e.operand)
        is TypeTestExpr -> exprHasReturn(e.target)
        is IsPatExpr -> exprHasReturn(e.target)
        is TupleExpr -> e.items.any { exprHasReturn(it) }
        is StructCtorExpr -> e.assigns.any { exprHasReturn(it.second) }
        is VarExpr -> exprHasReturn(e.value)
        is InstExpr -> e.terms.any { exprHasReturn(it) }
        is TaskExpr -> e.body.stmts.any { stmtHasReturn(it) }
        is FlowExpr -> e.body.stmts.any { stmtHasReturn(it) }
        is ByExpr -> exprHasReturn(e.target) || e.props.any { exprHasReturn(it) }
        is SymbolCallExpr -> e.args.any { exprHasReturn(it) }
        else -> false      // 字面量/名字引用/lambda（return 归 lambda 自身）/命题位形态
    }

    /** 调用位约束收集（约束袋 t115；t117 去除单轮闭合限制）：callee 省略形参 ← 实参下界；callee 已标形参 +
     *  实参为占位 → 上界（负位证据）。复用既有判据（subtype/adaptOk 不另立）；源位置逐条登记（证据可追溯）。
     *  t117：约束一律登记（后到约束经轮末 export 复解消化——R 轮闭合，PENDING 观察级退役）。 */
    private fun recordCallArgs(calleeName: String, argTypes: List<sublimation.frontend.hir.HirType>, pos: SourceRange) {
        val d = funDecls[calleeName] ?: symbols.lookupLocalFunDecl(calleeName) ?: return
        // 形参位（省略型）：实参类型作下界（源序声明序）
        d.params.forEachIndexed { i, q ->
            if (q.type != null || i >= argTypes.size) return@forEachIndexed
            val m = metaForParam(d, i) ?: return@forEachIndexed
            m.lower.add(substMetas(argTypes[i]) to pos)
            m.addEvidence(pos)
        }
        // 上界侧：callee 形参已标注 + 实参为未决元变量占位 → ?arg <: τ（占位按底语义穿核对，此处只记账）
        d.params.forEachIndexed { i, q ->
            val tau = q.type ?: return@forEachIndexed
            val argT = argTypes.getOrNull(i) as? sublimation.frontend.hir.HirNamedType ?: return@forEachIndexed
            val m = with(infer) { if (!argT.isMetaSlot()) null else metaSlots[argT.name] } ?: return@forEachIndexed
            if (m.resolved != null) return@forEachIndexed
            m.upper.add(expand(type(tau)) to pos)
            m.addEvidence(pos)
        }
    }

    /** t118（提案 §3.2 ②）：callee 闭合签名事实——实参为具名变量处按形参位注入 (arg : T)（位置式 +
     *  命名式两形态按形参名对位）。调用点即跨声明的签名可见点（R 轮按轮传递的天然红利：
     *  callee 的上一轮终解在本轮调用点入 Γ）。 */
    private fun injectSigFacts(d: FunDecl, e: sublimation.frontend.ast.CallExpr) {
        fun at(a: sublimation.frontend.ast.Expr, i: Int) {
            if (i < 0 || i >= d.params.size) return
            val n = (a as? sublimation.frontend.ast.NameRef)?.name ?: return
            val t = metaForParam(d, i)?.resolved
            if (t is sublimation.frontend.hir.HirNamedType && t.args.isEmpty()) injectTypingFact(n, t, a.pos)
        }
        e.args.forEachIndexed { i, a -> at(a, i) }
        e.namedArgs.forEach { (n, a) -> at(a, d.params.indexOfFirst { it.name == n }) }
    }

    /** 构造位约束收集：结构体构造（位置式/命名式）→ 省略字段 ← 实参下界（t117：一律登记）。 */
    private fun recordCtorFields(d: StructDecl, named: List<Pair<String, sublimation.frontend.hir.HirType>>, pos: SourceRange) {
        if (!scriptMode) return
        named.forEach { (fn, t) ->
            val i = d.fields.indexOfFirst { it.name == fn }
            if (i < 0) return@forEach
            val m = metaForField(d, i) ?: return@forEach
            m.lower.add(substMetas(t) to pos)
            m.addEvidence(pos)
        }
    }

    /** 求解候选（P-11 §3.3 最小解 + §3.4 极性 + §3.6 默认；t117 种子=轮间保持口径）：
     *  - 正位（下界存在）：lub(下界)——本轮完整证据即 F(sol_{k-1})，**不并入陈种子**（上轮体类型
     *    是更 stale 的近似；并入会令 Any 软顶吸收新解 ⇒ 互递归假收敛——先红探针实测根因）；
     *  - 负位（无下界、有上界）：glb(上界)——t117 收口（t115 的 Nothing 底改为负位真解）；
     *  - 无下界+无上界+**有种**子 ⇒ 种子（约束位证据不再重收的保形：负位/声明先序口径）；
     *  - 无证据无种子：Nothing 默认填充（§3.6）。
     *  判定一律走既有 subtype/subtypeBody（ADR-39 唯一判据）——不另立判据。 */
    private fun inferLub(a: sublimation.frontend.hir.HirType, b: sublimation.frontend.hir.HirType, pos: SourceRange):
            sublimation.frontend.hir.HirType = when {
        infer.subtype(a, b) -> b
        infer.subtype(b, a) -> a
        else -> flattenUnion(sublimation.frontend.hir.HirUnionType(listOf(a, b), freshNid(), pos, emptyList()))
    }
    private fun inferGlb(a: sublimation.frontend.hir.HirType, b: sublimation.frontend.hir.HirType): sublimation.frontend.hir.HirType = when {
        infer.subtype(a, b) -> a
        infer.subtype(b, a) -> b
        else -> a            // 不可比：取首侧为半成品——后续上界核对兜底（无解判 E-INFER-NO-SOLUTION）
    }
    /** 未决推导占位 → ⊥（下界集归一：占位无解信息，作底参与 lub 不污染候选）。
     *  t117：覆盖**全部**推导态合成名——元变量占位（"(推导:元变量…"）+ 算术等中缀合成名
     *  （"(推导:中缀+…"，首轮操作数未决时的瞬态结果）——后者源自 TypeInfer 算术通道，
     *  不经元变量登记表，未决时一律按底归一（否则瞬态合成名入种子 ⇒ 不动点永不收敛）。 */
    private fun normPlaceholder(t: sublimation.frontend.hir.HirType): sublimation.frontend.hir.HirType {
        if (t !is sublimation.frontend.hir.HirNamedType) return t
        return if (t.name.startsWith("(推导:") && metaSlots[t.name]?.resolved == null) infer.namedT("Nothing") else t
    }
    private fun solveCandidate(m: InferMeta): sublimation.frontend.hir.HirType {
        val seed = m.resolved          // t117：轮外注入的上一轮解（无=首轮）
        val cand = when {
            m.lower.isNotEmpty() -> m.lower.map { normPlaceholder(it.first) }.reduce { a, b -> inferLub(a, b, m.origin) }
            m.upper.isNotEmpty() -> m.upper.map { it.first }.reduce { a, b -> inferGlb(a, b) }
            seed != null -> seed                 // 本轮无新约束 ⇒ 保持上轮解（轮间不变式）
            else -> infer.namedT("Nothing")     // 默认填充（§3.6）
        }
        m.upper.forEach { (u, upos) ->
            if (!infer.subtype(cand, u)) {
                if (m.reportNoSolutionOnce()) {
                    val lpos = m.lower.firstOrNull()?.second
                    diag.error("E-INFER-NO-SOLUTION", m.origin,
                        "推导无解：" + m.kind.label + " " + ident(m.owner + "." + m.name) + " 的候选 " +
                            infer.render(cand) + "（来自 " + (lpos?.let { InferMeta.evidenceOf(it) } ?: "声明位") +
                            "）与使用位 " + InferMeta.evidenceOf(upos) + " 的上界 " + infer.render(u) + " 不相容——" +
                            "最小解定理判无解（P-11 §3.3）。建议：在声明位加一处类型标注打破冲突")
                }
                return m.lower.firstOrNull()?.first?.let { normPlaceholder(it) } ?: infer.namedT("Nothing")   // 半成品继续
            }
        }
        return cand
    }
    /** 求解并落位（单轮）：形参/字段恒填（默认 Nothing,P-11 §3.6）；返回位无证据时返回 null（保留既有 Null 语义）。 */
    private fun solveRetMeta(m: InferMeta): sublimation.frontend.hir.HirType? {
        if (!m.hasEvidence()) return null
        val t = solveCandidate(m); m.resolve(t); return t
    }

    /** t117（附录 B H2）：本轮解映射导出——**轮末收口**：对全部元变量按本轮完整约束袋复解
     *  （含声明位后才到达的后到约束——单轮 D-INFER-PENDING 在此消化为闭合），种子并入单调合并。
     *  未决占位经 normPlaceholder 归一，**解映射零占位**。 */
    /** t125（S5b）：可变性提升需求导出——驱动器跨轮唯一传递通道（与 metaSolutions 同族）。 */
    internal fun mutPromotions(): Map<String, List<SourceRange>> = mutDemands

    internal fun metaSolutions(): Map<MetaSlotKey, MetaSolution> {
        val out = linkedMapOf<MetaSlotKey, MetaSolution>()
        fun export(m: InferMeta) {
            if (m.hasEvidence()) m.reResolve(solveCandidate(m))
            val t = m.resolved ?: infer.namedT("Nothing")
            // t118：上界约束随解映射跨轮传递（无解线复检原料）；unsolvable=无解旗（退化禁区）
            out[m.key] = MetaSolution(t, m.evidenceList(), m.owner,
                uppers = m.upper.toList(), unsolvable = m.noSolution())
        }
        funMetas.values.forEach { arr -> arr.forEach { m -> if (m != null) export(m) } }
        structMetas.values.forEach { arr -> arr.forEach { m -> if (m != null) export(m) } }
        return out
    }
    internal fun metaCount(): Int =
        funMetas.values.sumOf { arr -> arr.count { it != null } } + structMetas.values.sumOf { arr -> arr.count { it != null } }
    internal fun typeEq(a: sublimation.frontend.hir.HirType, b: sublimation.frontend.hir.HirType): Boolean = infer.typeEq(a, b)
    internal fun fuelMaxConsumed(): Int = infer.fuelMaxConsumed()
    /** t117（H3）：轮 label 下挂——构造后由 elaborateRound 调（init 先于 infer 初始化，不能内联）。 */
    internal fun setFuelLabel(label: String) = infer.setFuelLabel(label)
    /** t117：当前帧所属函数的返回位元变量（块体 return 推导的证据通道——每轮重收 return 值下界，
     *  种子作下界并入单调迭代；帧软通道保证不硬拒近似解）。 */
    private fun retMetaOfFrame(frame: FunFrame?): InferMeta? {
        val name = frame?.name ?: return null
        val d = funDecls[name] ?: return null
        return funMetas[d]?.getOrNull(d.params.size)
    }

    /** 字段签名位（构造子核对通道）：省略字段元变量的落位解/Any 顶近似（脚本模式）；工程模式 ErrorType 逐字。 */
    private fun fieldSigType(d: StructDecl, f: Param): sublimation.frontend.hir.HirType =
        if (f.type != null) expand(type(f.type))
        else if (scriptMode) fieldMetaType(d.name, f.name) ?: infer.namedT("Any")
        else expand(type(ErrorType("形参类型标注省略", f.pos)))

    /** 字段读取位：省略字段元变量的落位解（脚本模式；未决/工程模式 → null 走既有 synthetic 兜底）。 */
    private fun fieldMetaType(structName: String?, fieldName: String): sublimation.frontend.hir.HirType? {
        val d = structDecls[structName] ?: return null
        val i = d.fields.indexOfFirst { it.name == fieldName }
        if (i < 0) return null
        return metaForField(d, i)?.resolved
    }

    /** 占位替换：求解后类型树里残留的元变量占位 → 落位解（循环引用按已解者替换；未解保持占位由本类再无引用保证）。 */
    private fun substMetas(t: sublimation.frontend.hir.HirType): sublimation.frontend.hir.HirType = when (t) {
        is sublimation.frontend.hir.HirNamedType -> {
            val m = metaSlots[t.name]
            if (m != null && m.resolved != null) substMetas(m.resolved!!)
            else sublimation.frontend.hir.HirNamedType(t.name, t.args.map { substMetas(it) }, t.symbol, t.nid, t.origin, t.notes)
        }
        is sublimation.frontend.hir.HirUnionType ->
            sublimation.frontend.hir.HirUnionType(t.variants.map { substMetas(it) }, t.nid, t.origin, t.notes)
        is sublimation.frontend.hir.HirTupleType ->
            sublimation.frontend.hir.HirTupleType(t.items.map { substMetas(it) }, t.nid, t.origin, t.notes)
        is sublimation.frontend.hir.HirMutType -> sublimation.frontend.hir.HirMutType(substMetas(t.inner), t.nid, t.origin, t.notes)
        is sublimation.frontend.hir.HirRefType -> sublimation.frontend.hir.HirRefType(substMetas(t.inner), t.nid, t.origin, t.notes)
        is sublimation.frontend.hir.HirAtomicType -> sublimation.frontend.hir.HirAtomicType(substMetas(t.inner), t.nid, t.origin, t.notes)
        is sublimation.frontend.hir.HirFunType ->
            sublimation.frontend.hir.HirFunType(t.params.map { substMetas(it) }, substMetas(t.ret), t.purity,
                t.theory, t.context, t.nid, t.origin, t.notes)
        else -> t
    }

    /** R-08:implicit-It 糖 `{ e }` 的 `_` 用法预扫描（AST 体:嵌套 lambda / 匿名 fun 的 `_` 归其自身绑定,不外借）。 */
    private fun usesImplicitIt(e: Expr): Boolean = when (e) {
        is NameRef -> e.name == "_"
        is LambdaExpr -> false           // 嵌套 lambda 自己绑定自己的 _
        is AnonFunExpr -> false          // 匿名 fun 的 _ 是其形参名
        is IntLit -> false; is FloatLit -> false; is StrLit -> false; is ReturnSym -> false;
        is ParamRefExpr -> false; is TopExpr -> false; is BotExpr -> false; is ErrorExpr -> false
        is BinExpr -> usesImplicitIt(e.left) || usesImplicitIt(e.right)
        is UniExpr -> usesImplicitIt(e.operand)
        is BorrowExpr -> usesImplicitIt(e.operand)
        is TypeTestExpr -> usesImplicitIt(e.target)
        is IsPatExpr -> usesImplicitIt(e.target)
        is TaskExpr -> e.body.stmts.any { usesImplicitItStmt(it) }
        is FlowExpr -> e.body.stmts.any { usesImplicitItStmt(it) }
        is CallExpr -> usesImplicitIt(e.callee) || e.args.any { usesImplicitIt(it) } ||
            e.namedArgs.values.any { usesImplicitIt(it) }
        is InstExpr -> usesImplicitIt(e.target) || e.terms.any { usesImplicitIt(it) }
        is FieldExpr -> usesImplicitIt(e.target)
        is FunRefExpr -> usesImplicitIt(e.target)
        is SymbolCallExpr -> e.args.any { usesImplicitIt(it) }
        is TupleExpr -> e.items.any { usesImplicitIt(it) }
        is StructCtorExpr -> e.assigns.any { usesImplicitIt(it.second) }
        is ByExpr -> usesImplicitIt(e.target) || e.props.any { usesImplicitIt(it) }
        is VarExpr -> usesImplicitIt(e.value)
        is BlockExpr -> e.stmts.any { usesImplicitItStmt(it) }
        is IfExpr -> usesImplicitIt(e.cond) || e.thenBlock.stmts.any { usesImplicitItStmt(it) } ||
            (e.elseBlock?.stmts?.any { usesImplicitItStmt(it) } ?: false)
        is WhenExpr -> (e.subject?.let { usesImplicitIt(it) } ?: false) ||
            e.arms.any { (it.guard?.let { g -> usesImplicitIt(g) } ?: false) || usesImplicitIt(it.body) }
        is QuantExpr -> usesImplicitIt(e.body)   // 量词名是其绑定,体内 _ 归本 lambda
    }

    private fun usesImplicitItStmt(s: Stmt): Boolean = when (s) {
        is ExprStmt -> usesImplicitIt(s.expr)
        is VarStmt -> usesImplicitIt(s.value)
        is AssignStmt -> usesImplicitIt(s.target) || usesImplicitIt(s.value)
        is ReturnStmt -> s.expr?.let { usesImplicitIt(it) } ?: false
        is UncheckedStmt -> usesImplicitItStmt(s.inner)
        is AxiomStmt -> s.props.any { usesImplicitIt(it) }
        is ByStmt -> s.props.any { usesImplicitIt(it) }
        is BTSStmt -> usesImplicitIt(s.form) || s.premises.any { usesImplicitIt(it) } || usesImplicitIt(s.conclusion)
        is WhenPropStmt -> s.arms.any { arm -> usesImplicitIt(arm.condition) ||
            arm.body.any { usesImplicitItStmt(it) } }
        is StrategyStmt -> usesImplicitIt(s.goal)
        is ExistIntroStmt -> s.witnesses.any { usesImplicitIt(it) } || usesImplicitIt(s.prop)
        is ErrorStmt -> false
        is FunStmt -> false              // 局部 fun 声明的形参是它自己的
        is IfPropStmt -> usesImplicitIt(s.prop) || s.thenStmts.any { usesImplicitItStmt(it) } ||
            s.elseStmts.any { usesImplicitItStmt(it) }
        is ExistsElimStmt -> s.body.any { usesImplicitItStmt(it) }
    }

    /** HIR 节点 id 自唯一源（ADR-14：SymbolTable 合成类型与本类 freshNid 同源）。 */
    private fun freshNid(): NodeId = nids.alloc()

    /** Γ 并集（沿栈自外向内；归一化在快照口收）。 */
    private fun allFacts(): List<HirProp> = factFrames.flatMap { it.toList() }

    private fun injectFact(p: HirProp) { factFrames.lastOrNull()?.add(p) }

    private inline fun <T> factFrame(seed: List<HirProp> = emptyList(), block: () -> T): T {
        factFrames.addLast(LinkedHashSet(seed))
        typeFactCounts.addLast(0)
        typeFactCappedFlags.addLast(false)
        try { return block() } finally {
            factFrames.removeLast(); typeFactCounts.removeLast(); typeFactCappedFlags.removeLast()
        }
    }

    /** t65（ADR-42）：变体事实帧的注入预算与留痕——上界按帧计，超界按**声明顺序**截断并落
     *  H-TYPEFACT-CAP 一次（同帧同理由只报一次）；帧退出即复位（不随机丢弃）。 */
    private val typeFactCounts = ArrayDeque<Int>()
    private val typeFactCappedFlags = ArrayDeque<Boolean>()

    /** t65（ADR-42）：类型事实注入——变量/形参/扩展接收者 self 按其**数值类型**入当前事实帧：
     *  Nat ⇒ v >= 0；Int/Rat/其余 ⇒ 无；结构体（无类型实参）⇒ 其 Nat 字段逐字段（c.f >= 0）。
     *  类型位 @mut 同样注入（demoteView 后值仍是 Nat）。声明位调用（帧退即失效——生命周期与
     *  变量作用域一致，块级帧由 [block] 负责）；无事实帧（结构体声明位等）跳过。事实带
     *  provenance=type 留痕（HirNote）——**不进用户公理信任审计**（R-AXIOM / @axiom 的
     *  D-AXIOM-POST 免证通道完全隔离，a14 测试断言）。 */
    private fun injectTypeFacts(name: String, varType: sublimation.frontend.hir.HirType, pos: SourceRange) {
        if (factFrames.isEmpty()) return
        val dt = infer.demoteView(varType)
        if (dt !is sublimation.frontend.hir.HirNamedType) return
        if (dt.name == "Nat") { injectTypeFactProp(name, null, pos); return }
        if (dt.args.isEmpty()) {
            val sd = structDecls[dt.name] ?: return
            sd.fields.forEach { f ->
                val ft = expand(type(f.type ?: ErrorType("形参类型标注省略", f.pos)))   // t113 机械适配：省略位兜底走 ErrorType 通道（S2 推导位 t115 接管）
                if (ft is sublimation.frontend.hir.HirNamedType && ft.name == "Nat") injectTypeFactProp(name, f.name, pos)
            }
        }
    }

    private fun injectTypeFactProp(name: String, field: String?, pos: SourceRange) {
        val n = typeFactCounts.lastOrNull() ?: return
        if (n >= TYPE_FACT_CAP) {
            if (!(typeFactCappedFlags.lastOrNull() ?: false)) {
                typeFactCappedFlags[typeFactCappedFlags.lastIndex] = true
                diag.hint("H-TYPEFACT-CAP", pos,
                    "当前作用域的类型事实注入达上界 " + TYPE_FACT_CAP + "：按声明顺序截断，超出的事实不再注入 Γ")
            }
            return
        }
        typeFactCounts[typeFactCounts.lastIndex] = n + 1
        val target = sublimation.frontend.hir.HirTName(name, null, freshNid(), pos)
        val term = if (field == null) target
            else sublimation.frontend.hir.HirTField(target, field, freshNid(), pos)
        val prop = sublimation.frontend.hir.HirPAtom(">=", listOf(term,
            sublimation.frontend.hir.HirTInt("0", freshNid(), pos)), freshNid(), pos,
            listOf(sublimation.frontend.hir.HirNote(sublimation.frontend.hir.HirNoteKind.SUPPLEMENT,
                "provenance=type", "类型事实：按声明类型注入（非用户公理）", pos)),
            provenance = sublimation.frontend.hir.HirAtomProvenance.SYNTHETIC)   // t109 C-8:编译器合成 atom 分账
        injectFact(prop)
    }

    /** t118（提案 §3.2 injectSyntheticFact 的合成 (x : T) 族 atom——解闭合点/签名事实/L2 契约回写共用）：
     *  - provenance=SYNTHETIC（C-8 分账）：不进 axiomFacts，**trustedBase 留一审计只认 user 公理来源**
     *    （公理族隔离由既有分账承担——synthetic 永不入信任基）；
     *  - CAP 同 TYPE_FACT_CAP（与既有 Nat 类型事实同预算、同 H-TYPEFACT-CAP 截断提示）；
     *  - 仅简单具名类型（Nat/Str/结构体名）：复合类型（List[T]/元组）在项层无形态——边界登记不注入；
     *  - 位置：t115/t117 的证据使用位口径（HirNote 留痕「非用户公理」）。 */
    private fun injectTypingFact(name: String, t: sublimation.frontend.hir.HirNamedType, pos: SourceRange) {
        if (factFrames.isEmpty()) return
        val n = typeFactCounts.lastOrNull() ?: return
        if (n >= TYPE_FACT_CAP) {
            if (!(typeFactCappedFlags.lastOrNull() ?: false)) {
                typeFactCappedFlags[typeFactCappedFlags.lastIndex] = true
                diag.hint("H-TYPEFACT-CAP", pos,
                    "当前作用域的类型事实注入达上界 " + TYPE_FACT_CAP + "：按声明顺序截断，超出的事实不再注入 Γ")
            }
            return
        }
        typeFactCounts[typeFactCounts.lastIndex] = n + 1
        val prop = sublimation.frontend.hir.HirPAtom(":",
            listOf(
                sublimation.frontend.hir.HirTName(name, null, freshNid(), pos),          // 值项（Γ 内变量名）
                sublimation.frontend.hir.HirTName(t.name, null, freshNid(), pos)),       // 类型名项（A-TYPEOF 通道原料）
            freshNid(), pos,
            listOf(sublimation.frontend.hir.HirNote(sublimation.frontend.hir.HirNoteKind.SUPPLEMENT,
                "provenance=synthetic", "签名事实：推导解闭合注入（非用户公理）", pos)),
            provenance = sublimation.frontend.hir.HirAtomProvenance.SYNTHETIC)
        injectFact(prop)
    }

    /** t65（ADR-42）：if 表达式条件的可命题化判定——比较/等值二元（两侧均可命题化为项）及其 ¬ 组合；
     *  其余（调用 Bool 函数、字面量等）返回 null（不注入分支事实）。 */
    private fun branchProp(cond: sublimation.frontend.ast.Expr): HirProp? = when (cond) {
        is sublimation.frontend.ast.BinExpr -> {
            val op = if (cond.op == "∈") "in" else cond.op
            if (op in BRANCH_CMP_OPS) {
                val l = propNorm.termQuiet(cond.left)
                val r = propNorm.termQuiet(cond.right)
                if (l != null && r != null)
                    sublimation.frontend.hir.HirPAtom(op, listOf(l, r), freshNid(), cond.pos)
                else null
            } else null
        }
        is sublimation.frontend.ast.UniExpr -> branchProp(cond.operand)?.let {
            sublimation.frontend.hir.HirPNot(it, freshNid(), cond.pos) }
        else -> null
    }

    /** t66（ADR-43，ST-10.2）：减法收窄——仅当①运算符 `-`；②两操作数（降级后）均为 Nat；
     *  ③直接期望类型为 `Nat` 时，在 allFacts() 上以既有 [prover.proves] 证 `左 >= 右`：
     *  证出 ⇒ 类型记 Nat 并落 proof:arith-narrow 留痕（证据=必要公理 + 类型/分支事实声明）；
     *  证不出 ⇒ **逐字保持旧类型**（通常 Int）并在期望位为 Nat 时落 D-NARROW-SCOPE 可操作提示。
     *  预算=每函数帧尝试上界（源序确定性截断）+ prover 既有搜索预算（不开新池）。
     *  信任审计：证出时对公理来源事实跑留一法——去掉后证不出者进 trustedBase（#8）；
     *  仅类型/分支事实支撑 ⇒ trustedBase 空（#9）。返回 (类型, 留痕注?)。 */
    private fun narrowMinus(
        e: sublimation.frontend.ast.BinExpr,
        leftT: sublimation.frontend.hir.HirType,
        rightT: sublimation.frontend.hir.HirType,
        raw: sublimation.frontend.hir.HirType,
    ): Pair<sublimation.frontend.hir.HirType, sublimation.frontend.hir.HirNote?> {        fun nt(t: sublimation.frontend.hir.HirType): String =
            (infer.demoteView(t) as? sublimation.frontend.hir.HirNamedType)?.name ?: ""
        // 触发严格性：op + 双 Nat + 结果 Int + 直接期望 Nat
        if (e.op != "-" || nt(leftT) != "Nat" || nt(rightT) != "Nat") return raw to null
        val rawN = (raw as? sublimation.frontend.hir.HirNamedType)?.name ?: return raw to null
        if (rawN != "Int") return raw to null
        val want = expectTypes.lastOrNull()
        val wantN = (want as? sublimation.frontend.hir.HirNamedType)?.name ?: return raw to null
        if (wantN != "Nat") return raw to null
        // 预算：函数帧上界（源序；无函数帧的位不尝试——表达式位收窄只发生在直接期望上下文）
        val frame = funFrames.lastOrNull() ?: return raw to null
        if (frame.narrowTries >= NARROW_TRY_CAP) return raw to null
        frame.narrowTries++
        narrowAttempts++
        val lTerm = propNorm.termQuiet(e.left) ?: return raw to null
        val rTerm = propNorm.termQuiet(e.right) ?: return raw to null
        val goal = sublimation.frontend.hir.HirPAtom(">=", listOf(lTerm, rTerm), freshNid(), e.pos,
            provenance = sublimation.frontend.hir.HirAtomProvenance.SYNTHETIC)   // t109 C-8:收窄义务=编译器合成 atom
        val facts = allFacts()
        // 证明：复用既有 proves（R-NO-GUESS / E-CANNOT-PROVE 判据未动——此处失败不是错误）
        val rule = prover.proves(facts, goal)
        if (rule == null) {
            // 失败：保持旧类型 + 可操作提示（不写承诺：断言成立确实能证——公理/守卫即供给事实）
            diag.supplement("D-NARROW-SCOPE", e.pos,
                "无法证明 " + prover.render(goal) + "，减法 " + prover.render(goal) + " 保持 " +
                infer.render(raw) + "（ST-10.2 收窄未生效）。当前 Γ：" +
                facts.joinToString("；") { prover.render(it) } + "。" +
                "可先断言 " + prover.render(goal) + "（axiom " + prover.render(goal) + "，或以 if 守卫引入分支事实），证出后本减法按 ST-10.2 收窄为 Nat")
            return raw to null
        }
        // 证出：留一法判定必要公理（仅消费用户公理才计信任审计）
        val essential = axiomFacts.filter { ax ->
            facts.any { it == ax } && prover.proves(facts.filter { it != ax }, goal) == null
        }
        trustedBase.addAll(essential)
        val evidence = if (essential.isEmpty()) "类型/分支事实"
            else "必要公理：" + essential.joinToString("，") { prover.render(it) }
        val note = sublimation.frontend.hir.HirNote(
            sublimation.frontend.hir.HirNoteKind.SUPPLEMENT, "proof:arith-narrow",
            "减法按 ST-10.2 收窄为 Nat：Γ ⊢ " + prover.render(goal) + "（规则 " + rule + "；证据：" + evidence + "）",
            e.pos)
        return infer.namedT("Nat") to note
    }

    /** t109（R3-V1 §三 编码形态 C）:确立型义务的**合成 atom 入 Γ**——provenance=SYNTHETIC 分账
     *  （C-8）,不入 axiomFacts（trustedBase 留一审计只认 user 公理来源,免污染）。选择型义务走
     *  临时快照直译（narrowMinus 同族写法:goal 打 SYNTHETIC 后 allFacts 快照调 proves,不入 Γ）。
     * 供 R-02 退化通道/t110 义务上提接驳——消费侧零改（d.preps/factFrames 既有形态承载）。 */
    private fun injectSyntheticFact(p: sublimation.frontend.hir.HirProp) {
        injectFact(when (p) {
            is sublimation.frontend.hir.HirPAtom -> if (p.provenance == sublimation.frontend.hir.HirAtomProvenance.SYNTHETIC) p
                else p.copy(provenance = sublimation.frontend.hir.HirAtomProvenance.SYNTHETIC)
            else -> p
        })
    }

    /** t66：观察通道（compileNarrowAudit 用——只读视图）。 */
    internal fun narrowObserver(): NarrowObserver =
        NarrowObserver(narrowAttempts, trustedBase.map { prover.render(it) }, measureProofs.toList())

    /** t65：可作 if 分支事实的比较/等值算子集（与 prover 的 consume 消费口径一致）。 */
    private val BRANCH_CMP_OPS = setOf("==", "!=", "≠", "<", "<=", "≤", ">", ">=", "≥", "in")

    /** 清空待核 by 断言队列：逐条 proveOrDeep 核对（断言不注入 Γ——能证出者本就可由 Γ 推出）。 */
    private fun flushByAsserts() {
        if (pendingByAsserts.isEmpty()) return
        val pending = pendingByAsserts.toList()
        pendingByAsserts.clear()
        for ((props, pos) in pending) prover.byAsserts(props, allFacts(), pos)
    }

    /** 解析期留痕搬移（ADR-1 §3.5）：D-* → DESUGAR / H-* → HINT / 其余 → SUPPLEMENT。 */
    private fun notesOf(node: AstNode, extra: List<HirNote> = emptyList()): List<HirNote> {
        val moved = parseNotes[node]?.map { n ->
            val kind = when {
                n.code.startsWith("D-") -> HirNoteKind.DESUGAR
                n.code.startsWith("H-") -> HirNoteKind.HINT
                else -> HirNoteKind.SUPPLEMENT
            }
            HirNote(kind, n.code, n.message, n.pos ?: node.pos)
        } ?: emptyList()
        return if (extra.isEmpty()) moved else moved + extra
    }

    // ============================ 条目 ============================

    fun file(f: FileAst): HirFile {
        val nid = freshNid()
        // t8 声明索引（先于条目映射：调用点签名向前引用；同一驱动内的预扫描，非第二次遍历检查）
        f.entries.mapNotNull { (it as? DeclEntry)?.decl }.forEach { d ->
            when (d) {
                is FunDecl -> {
                    // P-8/D：扩展函数经 Collector→SymbolTable 统一登记（含 AST 声明，t39）；
                    // 自由函数仍按名索引（两桶隔离）。扩展 AST 索引随符号表走「帧链可见集」。
                    if (d.receiver == null) funDecls[d.name] = d
                }
                is StructDecl -> structDecls[d.name] = d
                is TraitDecl -> traitDecls[d.name] = d
                is TypeAliasDecl -> aliasDecls[d.name] = d
                else -> { }
            }
        }
        // core.classic 挂载判定（文件内任一声明加 @classic 注解）
        classicMounted = f.entries.mapNotNull { (it as? DeclEntry)?.decl }.any { d ->
            when (d) {
                is FunDecl -> "classic" in d.annotations
                is TraitDecl -> "classic" in d.annotations
                is StructDecl -> "classic" in d.annotations
                else -> false
            }
        }
        gateFileStart()   // t122：闸帧根（脚本=隐式不纯入口/工程=空栈）先于条目构造
        val entries = symbols.frame(ScopeKind.SCRIPT_TOP) {
            factFrame {
                val es = f.entries.map { e ->
                    when (e) {
                        is DeclEntry -> HirDeclEntry(decl(e.decl))
                        is StmtEntry -> HirStmtEntry(stmt(e.stmt))
                    }
                }
                // 顶层 by 断言在文件事实帧内清空（语句/函数体之外的落点，t30 同款口径）
                flushByAsserts()
                es
            }
        }
        val origin = if (f.entries.isEmpty()) {
            SourceRange(SourcePos(0, 0), null)   // 空文件零点
        } else {
            val first = entryPos(f.entries.first())
            val last = entryPos(f.entries.last())
            SourceRange(first.start, last.end ?: last.start)
        }
        return HirFile(nid, origin, emptyList(), entries)
    }

    private fun entryPos(e: sublimation.frontend.ast.Entry): SourceRange = when (e) {
        is DeclEntry -> e.decl.pos
        is StmtEntry -> e.stmt.pos
    }

    // ============================ 声明 ============================

    private fun decl(d: Decl): sublimation.frontend.hir.HirDecl = when (d) {
        is FunDecl -> funDecl(d, symbols.funEntry(d.name)?.ref(), global = true)
        is TraitDecl -> {
            // P-8/D（卷 TS-8）：继承闭包环检（E-CYCLE）——全集索引就位后 DFS
            traitCycleCheck(d)
            enterTypeParams(d.theory)
            val nid = freshNid()
            val theory = d.theory.map { theoryParam(it, d.theory,
                d.members.map { m -> m.retType },
                d.members.filter { m -> "mut" !in m.annotations }.flatMap { m -> m.params.map { q -> q.type } },
                d.members.filter { m -> "mut" in m.annotations }.flatMap { m -> m.params.map { q -> q.type } }) }
            // TS-5/TS-25：trait 体内 Self 有效（体外 → E-SELF-OUTSIDE-CLASS，见 type()）
            inTraitBody++
            val members = try { d.members.map { funDecl(it, null) } } finally { inTraitBody-- }
            val parents = d.parents.map { type(it) }
            // P-9/B.3（t40）：常量规范随 val 废除切除——HirTraitDecl.constants 保留空默认（消费归 t41）
            val result = HirTraitDecl(
                d.name, symbols.traitEntry(d.name)?.ref(), theory, d.annotations, members, parents,
                nid, d.pos, notesOf(d),
            )
            exitTypeParams()
            result
        }
        is StructDecl -> {
            enterTypeParams(d.theory)
            val nid = freshNid()
            val theory = d.theory.map { theoryParam(it, d.theory,
                d.fields.map { f -> f.type },
                d.fields.filter { f -> "mut" in f.annotations }.map { f -> f.type }) }
            // 字段先入帧：props 命题以字段名为项（旧 synthetic frame 口径）
            val result = symbols.frame(ScopeKind.BLOCK) {
                val fields = d.fields.mapIndexed { i, f ->
                    // t45（ADR-30 R-22）：字段默认值求值环境=声明处；后序字段判 E-DEFAULT-ORDER
                    // t115：省略字段（脚本模式）→ 元变量占位
                    param(f, SymbolKind.FIELD, d.fields.drop(i + 1).map { it.name }.toSet(), metaForField(d, i))
                }
                val propList = d.props.map { propNorm.prop(it) }
                // t115：字段元变量已于 param() 声明位求解落位（先到的构造位约束）；此表只读不再回填
                HirStructDecl(
                    d.name, symbols.structEntry(d.name)?.ref(), theory, fields, d.annotations, propList,
                    // t41（ADR-28 R-18）：空构造子单例旗标——判据=字段数 0
                    d.fields.isEmpty(),
                    nid, d.pos, notesOf(d),
                )
            }
            exitTypeParams()
            result
        }
is TypeAliasDecl -> {
            enterTypeParams(d.theory)
            val nid = freshNid()
            val theory = d.theory.map { theoryParam(it, d.theory, listOf(d.target)) }
            val target = type(d.target)
            // t41（ADR-28 R-16）：成员不得重复（裸名键/结构键）——E-DUP-UNION-MEMBER
            // t52（ADR-34 R-33）：按**原始 AST 成员**判重（HIR 侧自引用会被解析展开，把 alias 自身扩成
            // 整个目标联合，产生「成员 B 重复」级的误诊——P-9 裁决 7 已确认）。自引用（成员名 == 被定义
            // 别名）**不计入成员集合**——交给归纳判定（D-INDUCTIVE-TYPE / E-CYCLE）；同一自引用写第二遍
            // 仍是真重复，报被定义名本身（裁决 7：type A = A | A 应报）。
            (d.target as? sublimation.frontend.ast.UnionType)?.let { u0 ->
                val seenKeys = mutableSetOf<String>()
                var selfSeen = false
                u0.variants.forEach { v0 ->
                    val nm = (v0 as? sublimation.frontend.ast.NamedType)?.takeIf { it.args.isEmpty() }?.name
                    if (nm == d.name) {
                        if (selfSeen) diag.error("E-DUP-UNION-MEMBER", v0.pos,
                            "联合类型 " + ident(d.name) + " 的成员 " + ident(nm) + " 重复声明（联合成员不得重复）")
                        selfSeen = true
                        return@forEach
                    }
                    val k = nm ?: infer.render(type(v0))
                    if (!seenKeys.add(k)) diag.error("E-DUP-UNION-MEMBER", v0.pos,
                        "联合类型 " + ident(d.name) + " 的成员 " + ident(k) + " 重复声明（联合成员不得重复）")
                }
            }
            // t41（ADR-28 R-17）：归纳判定（D-INDUCTIVE-TYPE 补充级）/ 环检（E-CYCLE）
            // 判据：右值成员**全部**引用自身 → E-CYCLE；部分引用 → 归纳类型
            val members = (d.target as? sublimation.frontend.ast.UnionType)?.variants?.toList() ?: listOf(d.target)
            val refs = members.map { aliasChainContains(it, d.name, d.name, mutableSetOf()) }
            val selfCount = refs.count { it }
            when {
                selfCount > 0 && refs.all { it } ->
                    diag.error("E-CYCLE", d.pos, "类型 " + ident(d.name) +
                        " 的右值成员全部引用自身（无基态）：归纳类型需至少一个不含自引用的构造子")
                selfCount > 0 ->
                    diag.supplement("D-INDUCTIVE-TYPE", d.pos, ident(d.name) +
                        " 是归纳类型定义（自引用 " + selfCount + " 处）。归纳类型需至少一个不含自引用的构造子作为基态。")
            }
            val result = HirTypeAlias(d.name, symbols.aliasEntry(d.name)?.ref(), theory, target, nid, d.pos, notesOf(d))
            exitTypeParams()
            result
        }
is ErrorDecl -> sublimation.frontend.hir.HirErrorDecl(d.message, freshNid(), d.pos, notesOf(d))
    }

    private fun funDecl(d: FunDecl, symbol: SymbolRef?, global: Boolean = false, slotKey: String? = null): HirFunDecl {
        enterTypeParams(d.theory)
        // t86（F6 收口）：原生命题名保护已下沉到 SymbolTable.declareFun（Collector 声明通道统一，
        // 文案「不可重定义」）——此前的声明位守卫移除，否则同名字会重复报错。
        val nid = freshNid()
        // P-8/D（卷 TS-9/TS-10/TS-15）：扩展函数接收者——类型按 d.theory 作用域解释，
        // self 绑定于函数帧（静态类型 = Receiver，TS-10）；形参同名 self → E-DUP-NAME。
        // P-8/D（卷 TS-9/TS-10/TS-15）：接收者解释——头未知 → W-EXT-OBSOLETE（不阻断，替代头名报错，
        // TS-9 §9）；头已知 → 全量查名（Receiver 中的类型参数须经理论参数表声明，TS-15，由 type() 落
        // E-UNBOUND-NAME；实参中的未声明名同源）。Self 形态接收者由 type() 落 E-SELF-OUTSIDE-CLASS（TS-26）。
        val recvHeadOk = (d.receiver as? NamedType)?.name?.let { symbols.lookupType(it) != null } ?: true
        val recvT: sublimation.frontend.hir.HirType? = d.receiver?.let {
            if (recvHeadOk) type(it) else quietReceiverHead(it)
        }
        if (recvT != null && !recvHeadOk) {
            val head = ExtFunHeads.ofAst(d.receiver!!) ?: ""
            diag.warn("W-EXT-OBSOLETE", d.receiver.pos,
                "接收者类型头 " + ident(head) + " 未对应任何类型声明（疑似拼写错误；扩展函数仍登记，调用解析按该头匹配）")
        }
        if (recvT != null && d.params.any { it.name == "self" })
            diag.error("E-DUP-NAME", d.pos,
                "扩展函数 " + ident(d.name) + " 的形参 self 与保留接收者名同名（接收者以 self 绑定，TS-10）")
        val result = factFrame {
            symbols.frame(ScopeKind.FUNCTION) {
            val theory = d.theory.map { theoryParam(it, d.theory, listOf(d.retType),
                d.params.map { q -> q.type } + listOfNotNull(d.receiver)) }
            if (recvT != null) { symbols.declareLocal(SymbolKind.PARAM, "self", d.receiver!!.pos, recvT); injectTypeFacts("self", recvT, d.receiver!!.pos) }   // TS-10 / t65
            // t48（ADR-32 R-30）：@list 变长形参的声明位口径——须在末位、不得带默认值
            d.params.forEachIndexed { i, q ->
                if ("list" in q.annotations) {
                    if (i != d.params.lastIndex)
                        diag.error("E-VARARG-LAST", q.pos,
                            "@list 变长形参 " + ident(q.name) + " 只能出现在最后一个形参位（其后的形参无法按位置供给实参）")
                    if (q.default != null)
                        diag.error("E-VARARG-DEFAULT", q.pos,
                            "@list 变长形参 " + ident(q.name) + " 不能带默认值（变长位吸收 0 个或多个实参，默认值无供给语义）")
                }
            }
            val params = d.params.mapIndexed { i, q ->
                // t45（ADR-30 R-22）：默认值求值环境=声明处；后序形参判 E-DEFAULT-ORDER
                // t115：省略形参（脚本模式）→ 元变量占位入符号槽（判定面按底语义穿过）
                param(q, SymbolKind.PARAM, d.params.drop(i + 1).map { it.name }.toSet(), metaForParam(d, i))
            }
            // t118（提案 §3.2 ① 解闭合点）：形参元变量的轮 k 种子解闭合成型（R 轮迭代起点——瞬态轮注入
            // 随其 HIR 一并弃，终轮事实进产物）⇒ (x : T) 族合成 atom 入 Γ（provenance=SYNTHETIC 分账，
            // trustedBase 留一审计只认 user 公理；CAP 同 TYPE_FACT_CAP）。首轮未决不注入（占位无信息）；
            // 复合类型在项层无形态（边界登记）；零元变量单元零注入（e2e SAME 门机器保护）。
            d.params.forEachIndexed { i, q ->
                val t = metaForParam(d, i)?.resolved
                if (t is sublimation.frontend.hir.HirNamedType && t.args.isEmpty()) injectTypingFact(q.name, t, q.pos)
            }
            // 决策 65：上/下文/约束位 $n 改写为形参名（H-PARAM-REF 提示逐点落，D-DOLLAR-N 留痕随命题节点）
            val preps = d.preps.map { propNorm.prop(it, d.params) }
            // 上文（前置上下文角组）注入函数帧 Γ（调用方在调用点须证，t9 V1 暂仅在声明位登记）
            preps.forEach { injectFact(it) }
            // t115：返回位元变量（表达式体 + 省略标注，两模式共享——工程模式亦允许返回推导，队长 OQ-1 裁）
            val retMeta = metaForRet(d)
            // t117：种子优先（轮外注入的上一轮解——终轮全覆盖 ⇒ 零占位）；无种则 null（帧按 Null 语义）
            val retType = d.retType?.let { expand(type(it)) } ?: retMeta?.resolved
            val posts = d.posts.map { propNorm.prop(it, d.params) }
            val constraints = d.constraints.map { propNorm.prop(it, d.params) }
            // t109 裁决 3:P2 定义性归约的规则登记（单表达式纯函数,源序）；t117 退化函数（R-02 触发点）不登记
            oracleImpl.registerFunDef(d)
            // t8 return 上下文帧：体核对在体内遍历完成后于帧尾收口（E-RETURN-* / E-MISSING-RETURN / 体类型核对）
            // t97（N1 停机闸）：帧带名 + @impure 逃逸位 + 参数集（含 self 接收者入首参位口径）
            // t117（分型表 §3.1/R-02）：不收敛拥有者函数退化轮内置 impure（停机闸跳过既有逃逸位）
            funFrames.addLast(FunFrame(retType, d.name, "impure" in d.annotations || d.name in degradedFuns,
                d.params.map { q -> q.name }.toSet() + if (recvT != null) setOf("self") else emptySet(),
                metaRet = retMeta != null))
            // t66：体命的直接期望 = 声明返回类型（收窄触发条件③）
            val gateFrame = gateFunEnter(d.name, d.annotations, params, preps, global, slotKey)   // t122：闸帧+闭包键
            enterEffectScope()   // t121：体效应累加（具名函数 purity 走注解/退化位，结论 A2 闭包消费）
            val body = d.body?.let { b -> expectAt(retType) { expr(b) } }
            val frame = funFrames.removeLast()
            gateFunExit(gateFrame, exitEffectScope())   // t122：体效应结论出栈即闭包种子（A2 消费位）
            // t115 求解收口（P-11 §3.3）；t117：返回位**每轮复解**——体类型/return 证据每轮重收，
            // 种子作下界单调并入（先轮的占位态近似解不会硬化为「声明返回」——假门根因：收敛轮 2 误报）
            val mRet = if (retMeta != null) {
                // t118（R-02）：退化空位保 Any 落位——轮内体证据/复解一律跳过（Any 填充不冒充推导）
                if (retMeta.degradedSlot) retMeta.resolved
                else {
                    if (body != null) { retMeta.lower.add(substMetas(etype(body)) to d.body.pos)
                        retMeta.addEvidence(d.body.pos) }
                    solveRetMeta(retMeta)
                }
            } else retType
            // t115 返回位载体（t108 最小集槽位）：求解证据源序落表，默认态零发射；
            // t118：退化空位零发射（inferred=false——退化不是推导，D-INFER-DEGRADED 承载审计信息）
            val retInferred = retMeta != null && !retMeta.degradedSlot && mRet != null && retMeta.hasEvidence()
            val retEvidence = if (retInferred) retMeta!!.evidenceList() else emptyList()
            if (body != null) {
                // 块体（{ … }）：走 Kotlin 语义路径（E-MISSING-RETURN）；表达式体（= …）：按值类型核对
                val isBlockBody = body is sublimation.frontend.hir.HirBlock
                val bodyT = substMetas(etype(body))
                // t117：返回位为元变量 ⇒ 体级核对全暂缓（返回类型=体推导位本身，核对自反无意义；
                // 声明返回的硬核对只在 retType 非 meta 位成立）
                val deferred = retMeta != null
                infer.checkScope(d.body!!.pos) {
                    // 已求解时按解核对；未解（无证据返回位）保持既有 null→Null 语义
                    infer.funBody(d.name, mRet ?: retType, bodyT, isBlockBody, frame.sawReturn, d.body!!.pos, deferred)
                }
            }
            // t30：体检查完、事实注入就绪后核对 by 断言（old flushByAsserts 落点）
            flushByAsserts()
            // t9 下文（posts）：$ 代换为体尾表达式后由函数帧 Γ 证（@axiom 免证，D-AXIOM-POST）
            if (d.posts.isNotEmpty() && d.body != null) {
                val tail = dollarTermOf(d.body)
                if (tail != null) {
                    // E-PROP-FORM 半成品占位（PBot）已落诊断，跳过其义务核对（不重复报）
                    val goals = posts.filter { it !is sublimation.frontend.hir.HirPBot }
                        .map { prover.substDollar(it, tail) }
                    prover.postcondition(d.name, goals, allFacts(), "axiom" in d.annotations, d.pos)
                }
            }
            // t117（R-02 触发点）：不收敛拥有者函数（退化轮注入）按 impure 记账——纯度闸/停机闸既有消费
            val purity = if ("impure" in d.annotations || d.name in degradedFuns) HirPurity.IMPURE else HirPurity.PURE
            val async = "async" in d.annotations
            HirFunDecl(
                d.name, symbol, d.annotations, theory, params, preps, mRet, posts, constraints,
                purity, async, body, nid, d.pos, notesOf(d), recvT,
                inferred = retInferred, evidence = retEvidence,
            )
            }
        }
        exitTypeParams()
        return result
    }

    /** P-8/D：头未声明的接收者静默构造（头位 symbol=null 不落 E-UNBOUND-NAME——改落 W-EXT-OBSOLETE；实参仍走全量查名）。 */
    private fun quietReceiverHead(t: Type): sublimation.frontend.hir.HirType = when (t) {
        is NamedType ->
            HirNamedType(t.name, t.args.map { type(it) }, null, freshNid(), t.pos, notesOf(t))
        is sublimation.frontend.ast.MutType -> HirMutType(quietReceiverHead(t.inner), freshNid(), t.pos, notesOf(t))
        is sublimation.frontend.ast.AtomicType ->
            sublimation.frontend.hir.HirAtomicType(quietReceiverHead(t.inner), freshNid(), t.pos, notesOf(t))
        else -> type(t)
    }

    /**
     * P-8/D（TS-8）：特质继承闭包环检（E-CYCLE）——自 d 起沿 parents 做带回溯的 DFS，
     * 回到 d 即环（互环 / 自继承 / 长链回环均报）；done 沉淀已探查无环节点，跨分叉共享。
     * t34 修正：原迭代版 onPath 不回缩（菱形误报）且 seen 去重先于环判定（A:A 漏报）。
     */
    private fun traitCycleCheck(d: TraitDecl) {
        val done = mutableSetOf<String>()
        fun report(pn: String) {
            diag.error("E-CYCLE", d.pos,
                "特质继承链出现环（" + ident(d.name) + " ← " + ident(pn) + "，TS-8 循环继承报 E-CYCLE")
        }
        fun visit(n: String): Boolean {
            if (n == d.name) return true                    // 回到自身 = 环
            if (n in done) return false                     // 已探查无环（菱形共享父类不重复探）
            if (!traitDecls.containsKey(n)) return false    // 未声明名（外部特质/报错名）不构成环
            traitDecls[n]!!.parents.forEach { p ->
                val pn = (p as? NamedType)?.name ?:
                    return@forEach
                if (visit(pn)) return true
            }
            done += n
            return false
        }
        d.parents.forEach { p ->
            val pn = (p as? NamedType)?.name ?:
                return@forEach
            if (visit(pn)) { report(pn); return }
        }
    }

    /** 体尾表达式 → 项（旧 dollarOf：块体取唯一 return 值或尾表达式，否则 null 字面量意）。 */
    private fun dollarTermOf(body: Expr): sublimation.frontend.hir.HirTerm? = when (body) {
        is BlockExpr -> {
            val rets = body.stmts.filterIsInstance<ReturnStmt>().mapNotNull { it.expr }
            if (rets.size == 1) propNorm.termQuiet(rets[0])
            else body.tailExpr?.let { propNorm.termQuiet(it) }
        }
        else -> propNorm.termQuiet(body)
    }

    /** Γ 快照：理论类型参数的词法作用域（t7 不设 SymbolKind.TYPEPARAM——类型引用 symbol=null 静默）。 */
    private fun enterTypeParams(theory: List<TheoryParam>) {
        typeParams.addLast(theory.filterIsInstance<TypeParam>().mapTo(mutableSetOf()) { it.name })
    }

    private fun exitTypeParams() = typeParams.removeLast()

    private fun isTypeParam(name: String): Boolean = typeParams.any { name in it }

    // ============================ 语句 ============================

    private fun stmt(s: Stmt): sublimation.frontend.hir.HirStmt = when (s) {
        is VarStmt -> {
            val nid = freshNid()
            // t86（F6 收口）：原生命题名保护已下沉到 SymbolTable.declareScriptTopVar / declareLocal
            // （声明通道统一）——此处声明位守卫移除，避免重复报错
            val declared = s.type?.let { expand(type(it)) }
            // t66：var 值的直接期望 = 标注类型（先求值再登记：var n = n + 1 引用旧 n）
            val value = expectAt(declared) { expr(s.value) }
            val initT = etype(value)
            if (s.destruct != null) {
                // 元组解构（决策 91 声明版）：分量类型按元组分量逐名登记
                val partTypes = infer.varDestruct(initT, s.destruct, s.pos)
                s.destruct.forEachIndexed { i, n ->
                    val partT = partTypes.getOrElse(i) { infer.syntheticT("分量" + n) }
                    // t125（S5b）：解构分量按名提升（分量同属 T1 局部族;键=声明位+分量名，不与变量名撞键）
                    val pEvi = if (scriptMode) mutPromoted[mutKey(s.pos, n)] else null
                    val pT = if (pEvi != null) promotedView(partT, s.pos) else partT
                    if (pEvi != null) mutNote(n, pT, s.pos, "局部二次赋值 T1", pEvi)
                    declareLocal(n, SymbolKind.LOCAL, s.pos, pT, scriptPromotable = scriptMode)
                    injectTypeFacts(n, pT, s.pos)   // t65
                }
                HirVarStmt(s.name, null, s.annotations, initT, value, s.destruct, false, nid, s.pos, notesOf(s))
            } else {
                val finalT = infer.checkScope(s.pos) { infer.varAnnotated(s.name, declared, initT, s.pos) }
                // t125（S5b / R-06）：脚本模式可变性提升**预应用**——上一轮登记的需求在此落位（声明先于赋值，
                // 词法序天然;单调：已 @mut 穿过不重包——混标单向蕴含）。工程模式零此通道（声明制）。
                val mutEvi = if (scriptMode) mutPromoted[mutKey(s.pos, s.name)] else null
                val varT = if (mutEvi != null) promotedView(declared ?: finalT, s.pos) else (declared ?: finalT)
                if (mutEvi != null) mutNote(s.name, varT, s.pos, "局部二次赋值 T1", mutEvi)
                // 可写来源：语句注解 @mut 或类型位 @mut T（旧 Frame.isMut 两个口径）或提升预应用
                // t39：脚本模式顶层 var 与顶层自由变量/函数同域唯一（E-DUP-DECL）；块内重名走遮蔽（H-SHADOW）
                // t39：脚本模式顶层 var 与顶层自由变量/函数同域唯一（E-DUP-DECL）；块内重名走遮蔽（H-SHADOW）
                val symbol: SymbolRef? = if (symbols.isScriptTopFrame() && s.name != "_") {
                    val entry = symbols.declareScriptTopVar(s.name, s.pos, varT,
                        "mut" in s.annotations || declared is sublimation.frontend.hir.HirMutType || mutEvi != null,
                        scriptPromotable = scriptMode)
                    if (entry == null) {
                        diag.error("E-DUP-DECL", s.pos,
                            "顶层名称 " + ident(s.name) + " 重复声明（脚本模式顶层自由变量与函数不允许重名，t39）")
                        null
                    } else entry.ref()
                } else declareLocal(s.name, SymbolKind.LOCAL, s.pos, varT,
                    "mut" in s.annotations || declared is sublimation.frontend.hir.HirMutType || mutEvi != null,
                    what = "不可重定义", scriptPromotable = scriptMode)
                injectTypeFacts(s.name, varT, s.pos)   // t65
                // t98（规则 3 拷贝分派留痕）：仅 @ref 涉及时落 D-REF-DISPATCH（@mut 侧维持既有产物）
                val dn = if (declared?.let { infer.containsRef(it) } == true || infer.containsRef(initT))
                    listOf(refDispatchNote(s.pos, initT, varT, s.name)) else emptyList()
                HirVarStmt(s.name, symbol, s.annotations, varT, value, null, false, nid, s.pos, notesOf(s) + dn)
            }
        }
        is AssignStmt -> {
            val nid = freshNid()
            val target = expr(s.target)
            // t66：赋值右值的直接期望 = 目标类型（收窄触发条件③）
            val value = expectAt(etype(target)) { expr(s.value) }
            // t8 可写核定（旧 Frame.isMut 路径）：名字目标查 mut 标记；未定义名的 E-UNBOUND-NAME 已于 expr 解析路径落
            // t60-2：可写核定（t8 名字位 + t60 字段位）——字段写两闸：①可变性（字段须 @mut 且接收者可写）
            // t125（S5a / OQ-3 对称修复）：名字位写闸补读**类型位** @mut——与字段闸（:2897 两口径）
            // 同判据：符号 mutable 位（语句注解 @mut）或类型位 @mut T / @ref @mut T（写穿透视图）皆可写。
            // R-06 工程侧判据对称化：脚本提升（S5b）与工程显式 `a: @mut T` 走同一条写闸。
            val mutable = when (val tn = s.target) {
                is NameRef -> {
                    val sym = symbols.lookupValue(tn.name)
                    sym?.mutable == true || (sym?.type?.let { infer.isWritableView(it) } == true)
                }
                is FieldExpr -> fieldWriteOk(tn, target, s.pos)
                else -> {
                    diag.error("E-ASSIGN-TARGET", s.pos, "等号左边必须是个变量或字段，不能对表达式赋值")
                    false
                }
            }
            // t125（S5b / R-06）：脚本模式可变性提升登记——不可写**名字目标**若为提升候选（scriptPromotable）
            //  ⇒ 登记单调需求（本轮不落错;轮末驱动器以 mutPromoted 预应用重跑：符号 mutable + 视图 @mut
            //  一次性就位，树一致）。工程模式逐字 E-IMMUT-ASSIGN（R-06 工程侧声明制）。
            if (!mutable && s.target is NameRef) {
                val lhsName = (s.target as NameRef).name
                val sym = symbols.lookupValue(lhsName)
                when {
                    sym == null -> { /* E-UNBOUND-NAME 已于 expr 解析路径落，不重复报 */ }
                    scriptMode && sym.scriptPromotable -> demandMut(mutKey(sym.origin, lhsName), s.pos)
                    else -> diag.error("E-IMMUT-ASSIGN", s.pos,
                        ident(lhsName) + " 是不可变的，不能赋值。要改它？声明时写成 `var " + lhsName + " : @mut T`")
                }
            }
            // t60-1：赋值类型核对——右值 <: 目标（与形参/返回值/var 标注同一条 adaptOk 判据，R-38 落点闸同住）
            // t60-1b：@mut 接收者的字段目标型按字段声明核（读路径的 @mut 剥离不在 t60 范围）
            val targetT = if (s.target is FieldExpr) {
                val hf = target as? sublimation.frontend.hir.HirField
                val recvT = hf?.let { etype(hf.target) }
                val core = (recvT as? sublimation.frontend.hir.HirMutType)?.inner ?: recvT
                val sn = (core as? sublimation.frontend.hir.HirNamedType)?.name
                val fld = sn?.let { s2 -> structDecls[s2]?.fields?.firstOrNull { f -> f.name == (s.target as FieldExpr).name } }
                fld?.type?.let { expand(type(it)) } ?: etype(target)
            } else {
                etype(target)
            }
            // t98（规则 3）：复用既有 valueT（etype 非幂等——每次调用落新类型节点吃 nid，不得重复调用）
            val valueT = etype(value)
            infer.checkScope(s.pos) { infer.assignTypes(targetT, valueT, s.pos) }
            // t98（规则 3）：赋值位同口径留痕（仅 @ref 涉及时）
            val an = if (infer.containsRef(targetT) || infer.containsRef(valueT))
                listOf(refDispatchNote(s.pos, valueT, targetT, "赋值")) else emptyList()
            sublimation.frontend.hir.HirAssign(target, value, mutable, nid, s.pos, notesOf(s) + an)
        }
        is AxiomStmt -> {
            val nid = freshNid()
            val props = s.props.map { propNorm.prop(it) }
            // R-AXIOM：公理免验证直接点亮（ADR-1 §3.4 rule 清单）；事实注入帧栈栈顶（Γ += 公理）
            // t66（ADR-43）：登记公理来源事实（trustedBase 判定原料——与 provenance=type 事实分家）
            axiomFacts.addAll(props)
            val ctx = prover.snapshot(allFacts())
            props.forEach { injectFact(it) }
            HirAxiom(props, ctx, "R-AXIOM", prover.axiom(), nid, s.pos, notesOf(s))
        }
        is ByStmt -> {
            val nid = freshNid()
            val props = s.props.map { propNorm.prop(it) }
            // t30：语句式 by 免验证注入已移除（by 只保留委托语义）——编译期错误 + 迁移指引
            val verdict = prover.byStmt(props, s.pos)
            HirByStmt(props, prover.snapshot(allFacts()), null, verdict, nid, s.pos, notesOf(s))
        }
        is UncheckedStmt -> {
            val nid = freshNid()
            val gateFrame = gateUncheckedEnter()   // t122：闸帧降闸（三路放行之一）
            uncheckedDepth++   // t97:unchecked 放行深度（停机闸/纯度闸同口径——只豁免检查，不豁免效应存在）
            val inner = stmt(s.inner)
            uncheckedDepth--
            gateUncheckedExit(gateFrame)
            HirUnchecked(inner, nid, s.pos, notesOf(s))
        }
        is ExprStmt -> {
            val nid = freshNid()
            val value = expr(s.expr)
            HirExprStmt(value, nid, s.pos, notesOf(s))
        }
        is BTSStmt -> {
            val nid = freshNid()
            val form = propNorm.prop(s.form)
            val premises = s.premises.map { propNorm.prop(it) }
            val conclusion = propNorm.prop(s.conclusion)
            // ∵∴：前提逐条证 → 结论由 Γ+前提证 → PROVEN 时结论注入帧栈栈顶（Γ += 结论）
            val r = prover.because(premises, conclusion, allFacts(), s.pos)
            if (r.verdict.status == HirProofStatus.PROVEN) injectFact(conclusion)
            HirBecause(form, premises, conclusion, r.context, r.rule, r.verdict, nid, s.pos, notesOf(s))
        }
        is WhenPropStmt -> {
            val nid = freshNid()
            val disjunction = propNorm.prop(s.disjunction)
            // 每支假设一个析取支成立（注入支帧 Γ），收集各支 BTS 结论取交集注入块外
            val armConclusions = ArrayList<List<HirProp>>()
            val arms = s.arms.map { a ->
                val armNid = freshNid()
                val condition = propNorm.prop(a.condition)
                val body = factFrame(listOf(condition)) {
                    symbols.frame(ScopeKind.BLOCK) { a.body.map { stmt(it) } }
                }
                armConclusions += body.filterIsInstance<HirBecause>().map { it.conclusion }
                sublimation.frontend.hir.HirWhenPropArm(condition, body, armNid, a.pos, emptyList())
            }
            val r = prover.whenProp(disjunction, armConclusions, allFacts(), s.pos)
            r.common.forEach { injectFact(it) }
            val rule = if (r.common.isNotEmpty()) "R-OR-ELIM" else null
            sublimation.frontend.hir.HirWhenProp(disjunction, arms, r.context, rule, r.verdict, nid, s.pos, notesOf(s))
        }
        is IfPropStmt -> {
            val nid = freshNid()
            val prop = propNorm.prop(s.prop)
            // 排中律：两支各注入 p / ¬p，取两支新事实的结构相等交作为「与 p 无关、两支都成立」的结论
            val ctx = prover.snapshot(allFacts())
            val thenNew = ArrayList<HirProp>()
            val elseNew = ArrayList<HirProp>()
            val negProp = HirPNot(prop, freshNid(), prop.origin)
            val thenStmts = factFrame(listOf(prop)) {
                val r = symbols.frame(ScopeKind.BLOCK) { s.thenStmts.map { stmt(it) } }
                thenNew.addAll(factFrames.last().filter { it != prop })
                r
            }
            val elseStmts = factFrame(listOf(negProp)) {
                val r = symbols.frame(ScopeKind.BLOCK) { s.elseStmts.map { stmt(it) } }
                elseNew.addAll(factFrames.last().filter { it != negProp })
                r
            }
            val r = prover.ifProp(prop, thenNew, elseNew, classicMounted, s.pos)
            r.common.forEach { injectFact(it) }
            val rule = if (r.common.isNotEmpty()) "R-EM" else null
            HirIfProp(prop, thenStmts, elseStmts, ctx, rule, r.verdict, nid, s.pos, notesOf(s))
        }
        is ExistIntroStmt -> {
            val nid = freshNid()
            val witnesses = s.witnesses.map { expr(it) }
            // ∃ 名先入 scratch 符号帧再命题化（x 约束 P 体内的同名引用）
            val prop = symbols.frame(ScopeKind.BLOCK) {
                s.names.forEach { declareLocal(it, SymbolKind.LOCAL, s.pos) }
                propNorm.prop(s.prop)
            }
            // ∃ 引入：P(x₁,…)[xᵢ→见证项] 须可由 Γ 证 → PROVEN 时 Γ += ∃names.prop
            val wTerms = s.witnesses.map { propNorm.termQuiet(it) }
            if (wTerms.all { it != null }) {
                val r = prover.existsIntro(s.names, wTerms.filterNotNull(), prop, allFacts(), s.pos)
                if (r.verdict.status == HirProofStatus.PROVEN) injectFact(r.existsProp)
                val rule = if (r.verdict.status == HirProofStatus.PROVEN) "R-EXISTS-INTRO" else null
                HirExistsIntro(s.names, witnesses, prop, r.context, rule, r.verdict, nid, s.pos, notesOf(s))
            } else {
                // 见证项不可命题化（命题面半成品）：E-PROP-FORM 已于 termQuiet 之外落点不可达——
                // 保守落 FAILED + E-QUANT-UNPROVEN 形态（不冒充成立，R-NO-GUESS）
                diag.error("E-QUANT-UNPROVEN", s.pos,
                    "存在引入的见证项无法命题化（仅字面量/名字/运算/调用/实例化/字段可作项）")
                HirExistsIntro(s.names, witnesses, prop, prover.snapshot(allFacts()), null,
                    HirVerdict(HirProofStatus.FAILED, "E-QUANT-UNPROVEN"), nid, s.pos, notesOf(s))
            }
        }
        is ExistsElimStmt -> {
            val nid = freshNid()
            var captured: HirProp? = null
            // x 先入块帧再命题化 P(x)；体子的 P(x) 注入块帧（x 作用域限于块）
            val body = factFrame {
                symbols.frame(ScopeKind.BLOCK) {
                    declareLocal(s.name, SymbolKind.LOCAL, s.pos)
                    val p = propNorm.prop(s.prop)
                    captured = p
                    injectFact(p)
                    s.body.map { stmt(it) }
                }
            }
            val prop = captured!!
            // Γ 有渲染同形的 ∃ 命题 → R-EXISTS-MATCH（块后核对：Γ 为外层事实并集快照）
            val verdict = prover.existsElim(s.name, prop, allFacts(), s.pos)
            // 块外 Γ += 体子的 BTS 结论（旧同款口径）
            body.filterIsInstance<HirBecause>().forEach { injectFact(it.conclusion) }
            val rule = if (verdict.status == HirProofStatus.PROVEN) "R-EXISTS-MATCH" else null
            HirExistsElim(s.name, prop, body, prover.snapshot(allFacts()), rule, verdict, nid, s.pos, notesOf(s))
        }
        is FunStmt -> {
            val nid = freshNid()
            // t39（ADR-27）：局部扩展函数——receiver != null 时登记入**当前帧**的桶（帧退即消失，
            // 复用既有 frame push/pop）；同帧同 (头, 名字) → E-DUP-EXT 两处报；遮蔽外层/全局同键 → H-SHADOW。
            // 自由函数照旧（先登记再走体，同旧 fnInit 机制：递归引用可解析）
            // t39（ADR-27）：局部扩展函数——receiver 可规范化时登记入**当前帧**的桶（帧退即消失，
            // 复用既有 frame push/pop）；同帧同 (头, 名字) → E-DUP-EXT 两处报；遮蔽外层/全局同键 → H-SHADOW。
            // head == null（ErrorType 接收者）：解析层已落诊断，按自由函数兜底。
            val head = if (s.fn.receiver != null) ExtFunHeads.ofAst(s.fn.receiver!!) else null
            if (head != null) {
                val dups = symbols.declareLocalExtFun(s.fn.name, head, s.fn.pos, s.fn)
                dups?.forEach { o ->
                    diag.error("E-DUP-EXT", o, "扩展函数 " + ident(head) + "." + ident(s.fn.name) +
                        " 重复声明（同接收者类型头/名字即重复——同域唯一，V1 禁止重载，TS-13 两处均报）")
                }
                val extSymbol = symbols.lookupExtFunVisible(head, s.fn.name).firstOrNull()?.ref()
                val slotKey = gateLocalRegister(s.fn.name, s.fn.annotations)   // t122：局部 fun 登记（词法序+闭包键）
                val fn = funDecl(s.fn, extSymbol, slotKey = slotKey)
                // ∀ 引入：定义即证明（有体 → PROVEN + D-LOCAL-FUN 留痕；签名 → E-QUANT-UNPROVEN）
                prover.localFun(s.fn.name, s.fn.body != null, s.pos)
                HirLocalFun(fn, nid, s.pos, notesOf(s))
            } else {
                // 自由函数照旧（先登记再走体，同旧 fnInit 机制：递归引用可解析）
                // t52（ADR-34 R-32）：签名随帧登记（局部函数与全局函数走**同一**调用点签名核对通道——
                // 形参/返回/默认值/变长旗标全部可查，不再让调用点拿不到签名而静默放行）
                val symbol = symbols.declareLocalFun(s.fn.name, s.fn.pos,
                    s.fn.params.map { it.name }, s.fn.params.any { it.destruct != null }, s.fn)
                val slotKey = gateLocalRegister(s.fn.name, s.fn.annotations)   // t122：局部 fun 登记（词法序+闭包键）
                val fn = funDecl(s.fn, symbol?.ref(), slotKey = slotKey)
                // ∀ 引入：定义即证明（有体 → PROVEN + D-LOCAL-FUN 留痕；签名 → E-QUANT-UNPROVEN）
                prover.localFun(s.fn.name, s.fn.body != null, s.pos)
                HirLocalFun(fn, nid, s.pos, notesOf(s))
            }
        }
        is StrategyStmt -> {
            val nid = freshNid()
            val goal = propNorm.prop(s.goal)
            // @simp → 算术通道；@subst → 莱布尼茨代换；其余 E-STRATEGY-FAILED
            val verdict = prover.strategy(s.strategy, goal, allFacts(), s.pos)
            if (verdict.status == HirProofStatus.PROVEN) injectFact(goal)
            val rule = if (verdict.status == HirProofStatus.PROVEN) "R-STRATEGY" else null
            HirStrategy(s.strategy, goal, prover.snapshot(allFacts()), rule, verdict, nid, s.pos, notesOf(s))
        }
        is ReturnStmt -> {
            val nid = freshNid()
            val frame = funFrames.lastOrNull()
            // t66：return 值的直接期望 = 函数声明返回类型（收窄触发条件③）
            // t117：元变量返回位的帧类型=轮序种子（近似）——return 值走软通道（下界入袋），不硬拒；
            // 声明返回位（metaRet=false）保持硬核对（E-RETURN-TYPE 真门逐字不变）
            val frameRet = if (frame?.metaRet == true) null else frame?.retType
            val value = s.expr?.let { v -> expectAt(frameRet) { expr(v) } }
            infer.returnStmt(frameRet, value?.let { etype(it) }, frame != null, s.pos)
            if (frame != null) frame.sawReturn = true
            // t117 块体 return 推导：返回位元变量（未决）收集 return 值下界 + 证据（R 轮内闭合）
            retMetaOfFrame(frame)?.let { m ->
                if (value != null) { m.lower.add(substMetas(etype(value)) to s.pos); m.addEvidence(s.pos) }
            }
            HirReturn(value, s.label, nid, s.pos, notesOf(s))
        }
        is ErrorStmt -> sublimation.frontend.hir.HirErrorStmt(s.message, freshNid(), s.pos, notesOf(s))
    }

    /** 局部绑定登记（_ 丢弃标记不入引用通道——只有 lambda 隐式 _ 例外，见 lambda 分支）。
     *  t8 附入推导类型与 @mut 标记（E-IMMUT-ASSIGN / 名字引用类型结论的登记侧）。 */
    private fun declareLocal(
        name: String, kind: SymbolKind, pos: SourceRange,
        type: HirType? = null, mutable: Boolean = false, what: String = "不可用作变量名",
        /** t125（S5b）：声明位可提升旗透传（R-06 提升候选过滤）。 */
        scriptPromotable: Boolean = false,
    ): SymbolRef? =
        if (name == "_") null else symbols.declareLocal(kind, name, pos, type, mutable, what,
            scriptPromotable = scriptPromotable).let { sym ->
            gateRecordVarBound(sym.ref())   // t122：R-07 绑定集统一登记位（声明即绑定——词序同旧 walker 树序）
            sym.ref()
        }

    // ============================ 表达式 ============================

    private fun expr(e: Expr): HirExpr = when (e) {
        is IntLit -> HirIntLit(e.value, freshNid(), e.pos, notesOf(e))
        is FloatLit -> HirFloatLit(e.value, freshNid(), e.pos, notesOf(e))
        is StrLit -> HirStrLit(e.value, freshNid(), e.pos, notesOf(e))
        is NameRef -> {
            val symbol = resolver.resolveValue(e.name, e.pos)
            val hir = HirNameRef(e.name, symbol, nameRefType(e.name, symbol), freshNid(), e.pos, notesOf(e))
            gateRecordNameRef(hir)   // t122：R-07 捕获候选（自由变量 + 类型含 @mut）
            hir
        }
        is ReturnSym -> propOnlyExpr(e.pos, "$")
        // Q12/Q20（t79，用户裁决 2026-11-09）：⊤/⊥ 是命题专用关键词（恒真/恒假命题）——程序面不再有
        // 双身份，值位出现即落 E-PROP-POS，提示大写字面量 True / False（提示本身可编译，ADR-48 §八）。
        // 命题位（含 S-8 ∴/∵ 反糖内部补真的 TopExpr，BTS 前提走 PropNormalizer 命题层）不经此处，
        // HirPTop/HirPBot 逐字不变。
        is TopExpr -> propOnlyExpr(e.pos, "⊤", "值位布尔真请写 True")
        is BotExpr -> propOnlyExpr(e.pos, "⊥", "值位布尔假请写 False")
        is ParamRefExpr -> {
            // 决策 65 通道：$n 只允许出现在前件/后件；函数体内出现即报错（旧码 E-PARAM-REF-IN-BODY）
            diag.error("E-PARAM-REF-IN-BODY", e.pos, "函数体内不能用 $" + e.index + " 引用参数，请直接使用参数名")
            HirErrorExpr("程序位 $" + e.index + " 参数引用", freshNid(), e.pos, notesOf(e))
        }
        is QuantExpr -> propOnlyExpr(e.pos, "量词（" + e.q + "）")
        is BinExpr -> {
            // t67（P-1 修复，用户裁决 R2「中缀调用适用于所有二元操作符」）：Unicode 符号中缀按函数调用
            // 处理（与 D-5 标识符中缀、符号前缀调用同产物 HirCall）；未声明由 resolveValue 复用既有
            // E-UNBOUND-NAME 面（不再是 E-OP-UNCLASSIFIED「无法定类」）。ASCII 算符（binops Kind 来源与
            // ^ 等表外 ASCII 算符）保持既有 builtinOp/数字塔/ST-10.1/E-OP-UNCLASSIFIED 通道逐字不变。
            // t92（待裁-B 退役）：`<->` 不再是内建算符——与 Unicode 符号中缀同通道走用户函数调用
            // （R2：用户可自定义 `fun <->(a, b)`；未声明由 resolveValue 落 E-OP-RETIRED 并提示 `↔`）
            if (!infer.isBuiltinOp(e.op) && (e.op.any { it.code >= 128 } || e.op == "<->")) {
                infer.checkScope(e.pos) {
                    callExpr(CallExpr(callee = NameRef(e.op, e.pos), args = listOf(e.left, e.right), pos = e.pos))
                }
            } else {
                val l = expr(e.left)
                val r = expr(e.right)
                val raw = infer.binExpr(e.op, etype(l), etype(r), e.pos)
                // t66（ADR-43，ST-10.2）：Γ ⊢ 左 >= 右 ⇒ Nat - Nat 收窄为 Nat（否则逐字保持旧类型）
                val (t66, t66note) = narrowMinus(e, etype(l), etype(r), raw)
                HirBinExpr(e.op, l, r, t66, freshNid(), e.pos, notesOf(e, if (t66note != null) listOf(t66note) else emptyList()))
            }
        }
        is UniExpr -> {
            val operand = expr(e.operand)
            val t = infer.uniExpr(e.op, etype(operand))
            HirUniExpr(e.op, operand, t, freshNid(), e.pos, notesOf(e))
        }
        is BorrowExpr -> {   // t101（R3 S6 语法层中间态）：&v——类型透传内层，零语义
            val operand = expr(e.operand)
            HirBorrow(operand, etype(operand), freshNid(), e.pos, notesOf(e))
        }
        is TypeTestExpr -> {
            val target = expr(e.target)
            val t = type(e.type)
            HirTypeTest(target, t, false, freshNid(), e.pos, notesOf(e))
        }
        is TaskExpr -> HirTask(block(e.body), infer.namedT("Task", listOf(infer.syntheticT("T"))), freshNid(), e.pos, notesOf(e))
        is FlowExpr -> HirFlow(block(e.body), infer.namedT("Flow", listOf(infer.syntheticT("T"))), freshNid(), e.pos, notesOf(e))
        // t51（ADR-33）：表达式级检查请求开池（调用签名核对 + 实参推导共享一个燃料池）
        // P5（批次 2）：值位的原生二元命题名（∧ ∨ → ↔）走 D-5 糖后会成 CallExpr；为保持既有值位语义与
        // HIR 产物逐字不变（`true ∧ false` 与 `true && false` 同产物），在此按原 BinExpr 通道回落。
        // 命题位不经此处（PropNormalizer 按 NativeProps.propCtors 分派为原生命题节点）。
        is CallExpr -> {
            val nm = (e.callee as? NameRef)?.name
            // t92（裁决 4 表达式层闭合）：值位的原生命题连接词（D-5 中缀糖 `a ∧ b` → CallExpr，
            // 以及符号前缀 `∧<a, b>` 形态）不再回落 Bool 内建（t74 遗留）——复用 E-PROP-FORM
            // （与 t85 命题位 `&` 反向同码、同半成品模式），按算符给可编译提示（ADR-48 §八；
            // →/↔ 值位无等价写法，只定位不杜撰算符）。命题位不经此处（PropNormalizer 分派原生节点）。
            if (nm != null && e.namedArgs.isEmpty() && NativeProps.propCtors[nm] != null) {
                diag.error("E-PROP-FORM", e.pos, valuePropHint(nm))
                HirErrorExpr("命题连接词出现在值位：" + nm, freshNid(), e.pos, notesOf(e))
            } else infer.checkScope(e.pos) { callExpr(e) }
        }
        is InstExpr -> {
            val target = expr(e.target)
            val symbol = (target as? HirNameRef)?.symbol
            // t22（B6，ADR-20）：命题参数 [@p] 的显式供给位（f<谓词>()）按命题面口径 elaboration——
            // 供给的是命题/谓词原子（如 witness<tagged<7>>()），其头名不是值位名字，静默不报。
            val calleeName = (e.target as? NameRef)?.name
            // P4（批次 3）：值位命题应用元数错（如 `¬<p, q>`）落 E-ARITY——表驱动按 §5 注册表元数核对
            val nativeAr = calleeName?.let { NativeProps.arityOf(it) }
            if (nativeAr != null && e.terms.size != nativeAr)
                diag.error("E-ARITY", e.pos, "原生命题 " + ident(calleeName!!) + " 是 " + nativeAr + " 元，实参 " + e.terms.size + " 个不符（否定写作：`¬<p>`）")

            val propPos = calleeName?.let { funDecls[it] }?.theory?.map { it is sublimation.frontend.ast.PropParam } ?: emptyList()
            val terms = e.terms.mapIndexed { i, t ->
                if (propPos.getOrNull(i) == true) resolver.schematicNames { expr(t) } else expr(t)
            }
            // 显式供给的类型参数个数核对 + 实例化返回类型（V1：原样取声明返回，实例化替换留 t9）
            val t = calleeName?.let { funDecls[it] }?.let { d ->
                infer.instArity(calleeName!!, d.theory.size, e.terms.size, e.pos)
                d.retType?.let { type(it) } ?: infer.syntheticT("实例化" + calleeName)
            } ?: infer.syntheticT("实例化")
            HirInst(target, terms, symbol, t, freshNid(), e.pos, notesOf(e))
        }
        is FieldExpr -> {
            val target = expr(e.target)
            // t8 字段类型：声明索引按接收者类型查（@mut 字段视图不在此核），未登记字段命中内建属性行 | synthetic
            val tt = etype(target)
            val structName = (tt as? sublimation.frontend.hir.HirNamedType)?.name
            val field = structDecls[structName]?.fields?.firstOrNull { it.name == e.name }
            val bt = infer.builtinSig(e.name)
            val t: HirType = when {
                // t115：省略字段读 → 元变量落位解（未决/工程模式仍走既有 synthetic 兜底）
                field != null -> field.type?.let { expand(type(it)) }
                    ?: fieldMetaType(structName, e.name) ?: infer.syntheticT("字段" + e.name)
                structDecls[structName] != null -> {
                    infer.fieldMissing(structName ?: "?", e.name, e.pos)
                    infer.syntheticT("字段" + e.name)
                }
                bt != null && (bt.first.isEmpty() || infer.looseEq(tt, bt.first[0])) -> bt.second
                else -> infer.syntheticT("字段" + e.name)
            }
            HirField(target, e.name, null, t, freshNid(), e.pos, notesOf(e))
        }
        is FunRefExpr -> {
            val target = expr(e.target)
            val ctor = symbols.lookupCtor(e.name)
            val symbol = ctor?.entry?.ref() ?: resolver.resolveValue(e.name, e.pos)
            val t: HirType = when {
                ctor != null -> infer.namedT(ctor.enumName, emptyList())
                funDecls[e.name] != null -> sigFunType(funDecls.getValue(e.name), receiverBound = false)
                else -> infer.funRef(etype(target), e.name, false, emptyList(), null, false, e.pos)
            }
            HirFunRef(target, e.name, symbol, t, freshNid(), e.pos, notesOf(e))
        }
        is SymbolCallExpr -> infer.checkScope(e.pos) {
            if (infer.isBuiltinOp(e.op)) {
                // 算符前缀调用（内建表内）：算符即函数名——builtinOp/二元口径，@mut T 按内层 T 分类（t61 同 demoteView 口径）
                val callee = HirNameRef(e.op, null, null, freshNid(), e.pos, emptyList())
                val args = e.args.map { expr(it) }
                onOpCall(callee)   // t121：t94 效应订阅（t97 不订阅——旧实现此位无停机检查，零变保真）
                val t = infer.builtinOp(e.op, args.map { infer.demoteView(etype(it)) }) ?: infer.syntheticT("算符" + e.op)
                HirCall(callee, args, null, t, null, null, emptyList(), false, false, false,
                    freshNid(), e.pos, notesOf(e))
            } else {
                // t67：符号前缀调用与符号中缀同通道——声明的符号函数按普通调用核对（未声明落 E-UNBOUND-NAME）
                callExpr(CallExpr(callee = NameRef(e.op, e.pos), args = e.args, pos = e.pos))
            }
        }
        is LambdaExpr -> {
            symbols.frame(ScopeKind.FUNCTION) {
                val nid = freshNid()
                // R-08 / R-10（裁决记录 §R-08 / §R-10）:implicit-It 糖 `{ e }` 的 arity 由期望类型定——
                // 已知期望（lambdaExpect 单通道:声明型直调实参位,含尾随 lambda）:
                //   0 参 ⇒ 零参（`_` 用即未绑定）;1 参 ⇒ 一参（`_` 启用,参数可不用——Kotlin 对齐）;
                //   ≥2 参 ⇒ 无箭头形态不可（须写显式参数表,落 E-ARITY）。
                // 未知期望:工程模式严格拒 `_`（补法 2）/ 脚本模式 `_` 用法自定单参（补法 1）。
                val given = lambdaExpect
                lambdaExpect = null   // 取用即还:嵌套 lambda 不继承外层期望
                // 注:parser 对 implicit-It 糖已把 "_" 填入 params（ParserTest:551-554）,判据仅取 implicitIt;
                // 显式参数（含 `{ => 体 }` 零参与 `{ (a,b) => 体 }` 解构）无条件保留 e.params。
                val implicit = e.implicitIt
                val arity = if (!implicit) e.params.size
                    else when {
                        given != null && given.size >= 2 -> {
                            diag.error("E-ARITY", e.pos,
                                "期望函数类型有 " + given.size + " 个参数,无箭头 lambda 不可:请写 " +
                                "{ " + (0 until given.size).joinToString(", ") { "a" + it } + " => … } 显式参数表（R-08）")
                            0   // 按零参继续（D-11 于调用位另落元数诊断）
                        }
                        given != null -> given.size   // 0 参按零参 / 1 参 `_` 启用（参数可不用,R-10）
                        scriptMode -> if (usesImplicitIt(e.body)) 1 else 0   // 补法 1:用法自定
                        usesImplicitIt(e.body) -> {                    // 工程模式补法 2:严格拒
                            diag.error("E-IMPLICIT-IT", e.pos,
                                "工程模式下无期望类型的隐式参数 _ 不可用:请写 { x => … } 显式参数," +
                                "或将该 lambda 置于函数类型实参位（R-08 补法 2;脚本模式允许用法自定单参）")
                            1   // 半成品树继续（_ 照常声明,体可分析）
                        }
                        else -> 0   // 无期望 + 无 `_` 用法:默认零参
                    }
                val names = if (!implicit) e.params else List(arity) { "_" }
                // B9（ADR-22）:形参类型取期望位（两遍定型第二遍）;无期望落 synthetic 承接
                val paramTypes = names.mapIndexed { i, n -> given?.getOrNull(i) ?: infer.syntheticT("lambda参数" + n) }
                val params = names.mapIndexed { i, n ->
                    val symbol = symbols.declareLocal(SymbolKind.PARAM, n, e.pos, paramTypes[i]).ref()
                    HirParam(n, paramTypes[i], symbol, emptyList(), null, null, freshNid(), e.pos, emptyList())
                }
                funFrames.addLast(FunFrame(null))   // lambda 内 return 放行（返回类型反推未启用）
                val gateFrame = gateClosureEnter(GateFrameKind.LAMBDA, params)   // t122：闸帧（体效应/捕获共用）
                enterEffectScope()   // t121：体效应累加作用域（闭包创建≠效应外溢）
                val body = factFrame { expr(e.body) }
                funFrames.removeLast()
                flushByAsserts()   // lambda 体检查完、事实注入就绪后核对 by 断言
                // t94（N1 硬前置：lambda 纯度推导，Fork B）:节点 purity 按 body 推导（ground truth）；
                // HirFunType.purity 是**名义位**（恒 PURE）——typeEq 的 purity 严格相等于 t96 的
                // pure<f> / PURE<:IMPURE 子类型序设计统一收口（有意过渡态，登记 ADR-54）
                val purity = if (exitEffectScope()) HirPurity.IMPURE else HirPurity.PURE
                gateClosureExit(gateFrame, purity == HirPurity.IMPURE)   // t122：t94 结论回填帧声明口径
                val t = sublimation.frontend.hir.HirFunType(
                    paramTypes, etype(body), HirPurity.PURE, emptyList(), null, freshNid(), e.pos, emptyList(),
                )
                HirLambda(params, body, e.implicitIt, t, purity, nid, e.pos, notesOf(e))
            }
        }
        is TupleExpr -> {
            val items = e.items.map { expr(it) }
            val t = sublimation.frontend.hir.HirTupleType(items.map { etype(it) }, freshNid(), e.pos, emptyList())
            HirTuple(items, t, freshNid(), e.pos, notesOf(e))
        }
        is StructCtorExpr -> {
            val nid = freshNid()
            val assigns = e.assigns.map { (n, v) -> n to expr(v) }
            val symbol = resolver.resolveType(e.struct, e.pos)
            // t8 命名构造核对：声明字段集合（缺/重复/逐字段类型）；未声明结构体的 E-UNBOUND-NAME 已于 resolveType 落
            val decl = structDecls[e.struct]
            val fields = decl?.fields?.map { p ->
                p.name to (p.type?.let { expand(type(it)) } ?: infer.syntheticT("字段" + p.name))
            } ?: emptyList()
            // B5（ADR-19）：有默认值的字段可缺省（旧 CallCheck:50 口径）
            val defaults = decl?.fields?.map { it.default != null } ?: emptyList()
            // t115r（F1 修复）：etype 是**合成器**（HirStrLit→namedT("Str") 各 alloc 一 nid，
            // Elaborator:1912），不是纯取值——求值一次、两处复用（约束袋 + 构造核对），
            // 杜绝瞬态 alloc 烧 nid（t109 propNormZero 铁 / D-14/D-17 同族）。
            val assignTypes = assigns.map { etype(it.second) }
            val t = if (fields.isNotEmpty()) {
                // t115：命名构造位约束收集（脚本模式省略字段 ← 实参下界）
                if (decl != null) recordCtorFields(decl, e.assigns.map { it.first }.zip(assignTypes), e.pos)
                // t118（§3.2 ② 签名事实·构造位）：字段元变量种子闭合 + 实参为具名变量 ⇒ (arg : FieldT) 注入
                if (decl != null) e.assigns.forEach { (fn, v) ->
                    val n = (v as? sublimation.frontend.ast.NameRef)?.name ?: return@forEach
                    val i = decl.fields.indexOfFirst { it.name == fn }
                    val t = if (i >= 0) metaForField(decl, i)?.resolved else null
                    if (t is sublimation.frontend.hir.HirNamedType && t.args.isEmpty()) injectTypingFact(n, t, v.pos)
                }
                infer.structCtor(e.struct, fields, defaults, e.assigns.map { it.first }, assignTypes, e.pos)
            }
            else infer.syntheticT("构造" + e.struct)
            HirStructCtor(e.struct, assigns, symbol, t, nid, e.pos, notesOf(e))
        }
        is ByExpr -> {
            val nid = freshNid()
            val target = expr(e.target)
            val props = e.props.map { propNorm.prop(it) }
            // t30：props 待核断言——延迟到包围语句/函数体检查完（名字绑定与事实注入就绪）后核对
            if (e.props.isNotEmpty()) pendingByAsserts += props to e.pos
            HirByExpr(target, props, prover.snapshot(allFacts()), null, null, nid, e.pos, notesOf(e))
        }
        is VarExpr -> {
            val nid = freshNid()
            val declared = e.type?.let { expand(type(it)) }
            // t66：var 值的直接期望 = 标注类型（先求值再登记：var n = var m = 0 的内侧引用旧名）
            val value = expectAt(declared) { expr(e.value) }
            val finalT = infer.checkScope(e.pos) { infer.varAnnotated(e.name, declared, etype(value), e.pos) }
            val symbol = declareLocal(e.name, SymbolKind.LOCAL, e.pos, finalT,
                "mut" in e.annotations || declared is sublimation.frontend.hir.HirMutType)
            injectTypeFacts(e.name, declared ?: finalT, e.pos)   // t65
            gateRecordVarBound(symbol)   // t122：R-07 绑定集词法序累计
            HirVarExpr(e.name, symbol, e.annotations, declared ?: finalT, value, null, false, nid, e.pos, notesOf(e))
        }
        is AnonFunExpr -> {
            enterTypeParams(e.theory)
            val nid = freshNid()
            val result = symbols.frame(ScopeKind.FUNCTION) {
                val theory = e.theory.map { theoryParam(it, e.theory, listOf(e.retType), e.params.map { q -> q.type }) }
                val params = e.params.map { param(it, SymbolKind.PARAM) }
                val preps = e.preps.map { propNorm.prop(it, e.params) }
                val retType = e.retType?.let { expand(type(it)) }
                val posts = e.posts.map { propNorm.prop(it, e.params) }
                // t97:匿名具名 fun 同停机闸供给（AnonFunExpr 无注解语法位——纯度默认纯;局部 fun 的 @impure 见 FunStmt）
                funFrames.addLast(FunFrame(retType, e.name, false, e.params.map { q -> q.name }.toSet()))
                val gateFrame = gateClosureEnter(GateFrameKind.ANONFUN, params)   // t122：闸帧（同 lambda 通道）
                enterEffectScope()   // t121：体效应累加作用域（闭包创建≠效应外溢）
                // t66：体命的直接期望 = 声明返回类型（收窄触发条件③）
                val body = e.body?.let { b -> expectAt(retType) { expr(b) } }
                val frame = funFrames.removeLast()
                val bodyImpure = exitEffectScope()
                if (body != null)
                    infer.checkScope(e.body!!.pos) {
                        infer.funBody(e.name, retType, etype(body), body is sublimation.frontend.hir.HirBlock, frame.sawReturn, e.body!!.pos)
                    }
                // t94（Fork B，同 lambda 规则）:HirAnonFun 节点 purity 按 body 推导（body 可空）；
                // 类型位 PURE 同名义位约定
                val purity = if (body != null && bodyImpure) HirPurity.IMPURE else HirPurity.PURE
                gateClosureExit(gateFrame, purity == HirPurity.IMPURE)   // t122：t94 结论回填帧声明口径
                val t = sublimation.frontend.hir.HirFunType(
                    params.map { it.type ?: infer.syntheticT("参数") },
                    retType ?: body?.let { etype(it) } ?: infer.namedT("Null"),
                    HirPurity.PURE, theory.mapNotNull { it as? sublimation.frontend.hir.HirTypeParam },
                    null, freshNid(), e.pos, emptyList(),
                )
                sublimation.frontend.hir.HirAnonFun(
                    e.name, theory, params, preps, retType, posts, body, t, purity,
                    nid, e.pos, notesOf(e),
                )
            }
            exitTypeParams()
            result
        }
        is sublimation.frontend.ast.BlockExpr -> block(e)
        is IfExpr -> {
            val nid = freshNid()
            val cond = expr(e.cond)
            // t65（ADR-42）：分支事实——条件为可命题化的比较/等值（含 ¬）时，then 支注入 cond、
            // else 支注入 ¬cond（复用 if[p] 的 factFrame(prop) / factFrame(¬prop) 注入口径，不另起通道）；
            // 条件不可命题化（调用 Bool 函数等）不注入。
            val condProp = branchProp(e.cond)
            val thenBlock = if (condProp == null) block(e.thenBlock)
                else factFrame(listOf(condProp)) { block(e.thenBlock) }
            val elseBlock = if (condProp == null) e.elseBlock?.let { block(it) }
                else e.elseBlock?.let { factFrame(listOf(HirPNot(condProp, freshNid(), e.cond.pos))) { block(it) } }
            // t8 分支合并：else 缺失 → Null；有则按 unify join（公共类型，无则取 then 分支）
            val t = if (elseBlock == null) infer.namedT("Null")
                else infer.unify(blockType(thenBlock), blockType(elseBlock)) ?: blockType(thenBlock)
            HirIf(cond, thenBlock, elseBlock, t, false, nid, e.pos, notesOf(e))
        }
        is WhenExpr -> {
            val nid = freshNid()
            // 主题表达式在绑定帧外求值：when (xs.first()) { … } 不可见绑定名
            val subject = e.subject?.let { expr(it) }
            symbols.frame(ScopeKind.BLOCK) {
                // t33：主题类型前移——模式绑定按其实例化 ctor 字段类型（同既有 subjectBinding 口径）
                val subjectT = subject?.let { etype(it) }
                e.subjectBinding?.let { n -> declareLocal(n, SymbolKind.LOCAL, e.pos, subjectT) }
                // t97:主题为名参时把来源名带进各臂（模式槽位 ⇒ 子结构）;主题为表达式（xs.first() 等）则无来源
                val subjOrigin = (e.subject as? NameRef)?.name
                val arms = e.arms.map { arm(it, subjectT, subjOrigin) }
                // t8 when 结论：密封枚举主题 → 分支 join（同名义）；否则 unsealedT（§49：固定 Null + 标记）
                val subjName = (subjectT as? sublimation.frontend.hir.HirNamedType)?.name
                // t22（B3，ADR-20）：密封主题 = prelude 密封容器 / Bool（true/false 两值）
                // t41（ADR-28 R-20）：联合主题穷尽性——全 struct 根成员静态判定；含非具名成员须 else
                val sealCtors = subjName?.let { sealedCtorNames(it) }
                val unionSeal = unionSealCtors(subjectT)
                val subjectSealed = sealCtors != null || unionSeal != null
                val hasElse = arms.any { it.pattern is sublimation.frontend.hir.HirPatElse }
                val unionAllNamed = unionSeal?.second == true
                val covered = when {
                    unionSeal != null && !unionAllNamed -> false    // 非具名成员不可静态枚举 → 仅 else 可救
                    unionSeal != null -> whenCovered(arms, unionSeal.first, subjName ?: "", subjectT)
                    sealCtors != null -> sealCtors.let { cs -> whenCovered(arms, cs, subjName, subjectT) }
                    else -> false
                }
                val reason = if (unionSeal != null && !unionAllNamed)
                    "联合含非具名成员，无法静态穷尽，请补 else" else null
                infer.whenNonExhaustive(e.pos, hasElse, subjectSealed, covered, reason)
                val armTypes = arms.map { etype(it.body) }
                val joined = if (armTypes.isEmpty()) infer.namedT("Null") else armTypes.reduce { a, b -> infer.unify(a, b) ?: a }
                val t = if (subjectSealed && (covered || hasElse)) joined
                    else if (subjectSealed) infer.unsealedT()
                    else if (hasElse) joined
                    else infer.unsealedT()
                HirWhen(subject, e.subjectBinding, arms, t, false, hasElse || covered, nid, e.pos, notesOf(e))
            }
        }

        is IsPatExpr -> {
            // 解析层过渡节点（§2.8）：ifis 已在解析层脱糖为 when——正常 AST 不应出现
            diag.error("E-SYNTAX", e.pos, "解析层过渡节点 is 模式未脱糖（ifis 应在解析层展开为 when）")
            HirErrorExpr("IsPatExpr 未脱糖", freshNid(), e.pos, notesOf(e))
        }
        is ErrorExpr -> HirErrorExpr(e.message, freshNid(), e.pos, notesOf(e))
    }

    private fun block(b: sublimation.frontend.ast.BlockExpr): HirBlock {
        val nid = freshNid()
        // t65（ADR-42）：块级事实帧——块内 var/模式绑定的类型事实随块退出失效（生命周期=作用域）。
        return factFrame {
            symbols.frame(ScopeKind.BLOCK) {
                val stmts = b.stmts.map { stmt(it) }
                HirBlock(stmts, nid, b.pos, notesOf(b))
            }
        }
    }



    // —— t22（B3，ADR-20）：when 穷尽性覆盖判定（旧 WhenCheck.bindingCovered 口径 + 密封主题扩展） ——

    /** 密封主题的构造子清单：Bool（true/false）。
     *  t44（ADR-29）：prelude 容器（Optional/Result/List/Set/Map）的穷尽性改走 t41 联合通道——
     *  [unionSealCtors] 于主题经 [expand] 展平为联合后取 struct 根成员；密封枚举清单表已删除。 */
    private fun sealedCtorNames(name: String): List<String>? =
        if (name == "Bool") listOf("true", "false") else null

    /**
     * t41（ADR-28 R-20）：联合主题的穷尽性成员表——（成员根名表, 是否全部可静态枚举）。
     * 可枚举 = 成员的根名是用户 struct（Succ[Peano] → Succ；元组/函数/容器/别名落空），模式
     * 以 is X / X(...) 覆盖即可静态判定；不可枚举（非具名）成员 → 只能靠 else 兜底。
     */
    private fun unionSealCtors(subjectT: sublimation.frontend.hir.HirType?): Pair<List<String>, Boolean>? {
        // 具名联合别名参数（fun f(x: E)）经 expand 取联合；已展平的联合主题直取
        val u = (subjectT as? HirUnionType) ?: (expand(subjectT ?: return null) as? HirUnionType) ?: return null
        val roots = u.variants.mapNotNull { v -> (v as? HirNamedType)?.name }
        val enumerable = roots.filter { structDecls[it] != null }
        return enumerable to (enumerable.size == u.variants.size)
    }

    /** 全部分支覆盖所有密封构造子（守卫分支缩小匹配集，不吸收——旧 bindingCovered 同口径）。 */
    private fun whenCovered(
        arms: List<sublimation.frontend.hir.HirWhenArm>, ctors: List<String>, subjName: String,
        subjectT: sublimation.frontend.hir.HirType?,
    ): Boolean {
        val guardless = arms.filter { it.guard == null }
        if (guardless.any { totalPattern(it.pattern) }) return true
        if (subjName == "Bool") {
            val lits = guardless.mapNotNull { boolLitName(it.pattern) }.toSet()
            return "true" in lits && "false" in lits
        }
        return ctors.all { c -> ctorCovered(guardless, c, subjName, subjectT) }
    }

    /** 顶层绑定/else 模式 = 全吸收（旧 PTop 同义：∃ 绑定恒真）。 */
    private fun totalPattern(p: sublimation.frontend.hir.HirPattern): Boolean =
        p is sublimation.frontend.hir.HirPatBind || p is sublimation.frontend.hir.HirPatElse

    /** 模式于字段位的构造子名：构造子模式，或引用密封构造子的零参值字面量（嵌套 None 同形）。 */
    private fun patCtorRefName(p: sublimation.frontend.hir.HirPattern): String? {
        val ctor = (p as? sublimation.frontend.hir.HirPatCtor)?.name
        if (ctor != null) return ctor
        val v = (p as? sublimation.frontend.hir.HirPatLit)?.value as? sublimation.frontend.hir.HirNameRef
        return v?.name?.takeIf { it.isNotEmpty() && it[0].isUpperCase() }
    }
    /** Bool 字面量模式名（True/False 归一小写计覆盖；t84 后小写臂为未声明错臂，仅计覆盖不挡）。 */
    private fun boolLitName(p: sublimation.frontend.hir.HirPattern): String? =
        (p as? sublimation.frontend.hir.HirPatLit)?.value?.let { v ->
            (v as? sublimation.frontend.hir.HirNameRef)?.name?.takeIf { it == "true" || it == "false" }
                ?: (v as? sublimation.frontend.hir.HirNameRef)?.name?.takeIf { it == "True" || it == "False" }?.lowercase()
                ?: (v as? sublimation.frontend.hir.HirTop)?.let { "true" } ?: (v as? sublimation.frontend.hir.HirBot)?.let { "false" }
        }

    /** 构造子 ctor 被无守卫分支联合覆盖：全吸收形，或逐字段位联合覆盖。 */
    private fun ctorCovered(
        arms: List<sublimation.frontend.hir.HirWhenArm>, ctor: String, subjName: String, subjectT: sublimation.frontend.hir.HirType?,
    ): Boolean {
        val argSets = arms.mapNotNull { arm ->
            val p = arm.pattern
            when {
                p is sublimation.frontend.hir.HirPatCtor && p.name == ctor -> p.args
                // t41（ADR-28 R-20）：联合主题的用户 struct 成员——is A（类型形态，无实参）同作覆盖证据
                p is sublimation.frontend.hir.HirPatIs && (p.type as? HirNamedType)?.name == ctor -> emptyList()
                else -> null
            }
        }
        if (argSets.isEmpty()) return false
        if (argSets.any { args -> args.all { totalPattern(it) } }) return true
        // 逐字段位联合覆盖：构造子的字段类型为密封枚举时，各分支该位的 ctor 模式合并须盖全
        val arity = argSets.maxOf { it.size }
        return (0 until arity).all { i ->
            val argsAt = argSets.mapNotNull { it.getOrNull(i) }
            if (argsAt.any { totalPattern(it) }) true
            else {
                val fieldSealed = ctorFieldCtorNames(ctor, i, subjectT)
                fieldSealed != null &&
                    argsAt.all { it !is sublimation.frontend.hir.HirPatCtor || it.args.all { a -> totalPattern(a) } } &&
                    fieldSealed.all { fc -> argsAt.any { patCtorRefName(it) == fc } }
            }
        }
    }

    /** 构造子字段位的密封清单（t44，ADR-29）：字段类型为联合且成员**全为具名 struct** 时取其根名表。
     *  旧 prelude 构造子字段规格通道（PRELUDE_* 表）删除——用户与 prelude 虚拟 struct 统一走
     *  [structFieldTypes]（主题实参代入后展开联合），与 t41 用户 struct 同一通道。 */
    private fun ctorFieldCtorNames(ctor: String, index: Int, subjectT: sublimation.frontend.hir.HirType?): List<String>? {
        val fieldT = structFieldTypes(ctor, subjectT).getOrNull(index) ?: return null
        val seal = unionSealCtors(fieldT)
        return seal?.takeIf { it.second }?.first
    }
    private fun arm(a: WhenArm, subjectT: HirType? = null, origin: String? = null): HirWhenArm {
        val nid = freshNid()
        return factFrame {   // t65（ADR-42）：臂级事实帧——模式槽位的类型事实随臂退出失效（反串味）
            symbols.frame(ScopeKind.BLOCK) {
                // t97（N1 停机闸形态①）：本臂模式绑定登记来源名（when 主题名）——
                // t 是 xs 的解构槽位 ⇒ 递归实参 t 视作原参数的子结构（递归到空结构停）。臂退出即失效。
                patOrigins.addLast(mutableMapOf())
                try {
                    // 分臂帧：模式归一化的新旧名裁定在内层帧做（槽位旧名判定穿透本帧查看外层——同旧实现）
                    val pattern = pattern(a.pattern, PatternMode.TOP, null, subjectT)
                    // t97:登记名取 HIR 模式的绑定位（裸名 t 解析为 PatCtor 同形，语义层裁定后才知是绑定还是构造子）
                    origin?.let { o -> hirPatBindNames(pattern).forEach { nm -> if (nm != "_") patOrigins.last()[nm] = o } }
                    val guard = a.guard?.let { expr(it) }
                    val body = expr(a.body)
                    val cond = a.cond?.let { expr(it) }
                    HirWhenArm(pattern, guard, body, cond, nid, a.pos, emptyList())
                } finally {
                    patOrigins.removeLast()
                }
            }
        }
    }

    // ============================ t8 推导结论提取 / 签名索引 ============================

    /** HIR 表达式的类型结论（字面量等无 type 字段的形态兜底；其余读节点字段——结论上树，ADR-6 §四 4.1）。 */
    private fun etype(h: HirExpr): HirType = when (h) {
        is HirIntLit -> infer.namedT("Nat")
        is HirFloatLit -> infer.namedT("Rat")
        is HirStrLit -> infer.namedT("Str")
        // C1（ADR-19 §C1）：程序面 ⊤/⊥ 结点即 Bool 字面量（构造时已写回 Bool 类型，此处与字面量族同通道）
        is HirTop -> infer.namedT("Bool")
        is HirBot -> infer.namedT("Bool")
        is HirErrorExpr -> infer.syntheticT("错误表达式")
        is HirTypeTest -> infer.namedT("Bool")
        is HirByExpr -> etype(h.target)
        is sublimation.frontend.hir.HirBlock -> blockType(h)
        is HirNameRef -> h.type ?: infer.syntheticT("未定义" + h.name)
        is HirBinExpr -> h.type ?: infer.namedT("Bool")
        is HirUniExpr -> h.type ?: infer.namedT("Bool")
        is HirBorrow -> h.type ?: etype(h.operand)   // t101：借用透传内层类型
        is HirTask -> h.type ?: infer.namedT("Task")
        is HirFlow -> h.type ?: infer.namedT("Flow")
        is HirInst -> h.type ?: infer.syntheticT("实例化")
        is HirLambda -> h.type ?: infer.syntheticT("lambda")
        is sublimation.frontend.hir.HirAnonFun -> h.type ?: infer.syntheticT("匿名函数")
        is HirTuple -> h.type ?: infer.syntheticT("元组")
        is HirStructCtor -> h.type ?: infer.syntheticT("构造")
        is HirVarExpr -> h.type ?: infer.syntheticT("var" + h.name)
        is HirCall -> h.type ?: infer.syntheticT("调用")
        is HirField -> h.type ?: infer.syntheticT("字段")
        is HirFunRef -> h.type ?: infer.syntheticT("函数引用")
        is HirIf -> h.type ?: infer.namedT("Null")
        is sublimation.frontend.hir.HirWhen -> h.type ?: infer.unsealedT()
    }

    /** 块表达式的值类型：末条 ExprStmt 的表达式（S-16 尾表达式投影）；
     *  末条 return 的块以返回值类型为块值（Kotlin 语义：return 后无续行，块为发散位而非 Null）。 */
    private fun blockType(b: sublimation.frontend.hir.HirBlock): HirType = when (val last = b.stmts.lastOrNull()) {
        is sublimation.frontend.hir.HirExprStmt -> etype(last.expr)
        is sublimation.frontend.hir.HirReturn -> last.expr?.let { etype(it) } ?: infer.namedT("Null")
        else -> infer.namedT("Null")
    }

    /** 值位名字的推导类型（帧局部 > 声明索引签名 > ctor/内建兜底；未定义名 → synthetic，诊断已于解析路径落）。 */
    private fun nameRefType(name: String, symbol: SymbolRef?): HirType {
        val frame = symbols.lookupValue(name)
        if (frame?.type != null) return frame.type
        return when (symbol?.kind) {
            SymbolKind.FUN, SymbolKind.THEOREM -> {
                // 全局函数签名优先；接收者导向的扩展函数桶 lookup 归 t30
                funDecls[name]?.let { sigFunType(it, receiverBound = false) }
                    ?: infer.syntheticT("函数" + name)
            }
            SymbolKind.CTOR -> symbols.lookupCtor(name)?.let { ci ->
                // t44（ADR-29）：构造子的 theory 取自 struct 声明（prelude 构造子为虚拟 struct）；
                // 旧 preludeEnumTheoryOf 枚举表已删除。
                val theory = structDecls[ci.enumName]?.theory?.filterIsInstance<TypeParam>()?.size ?: 0
                infer.namedT(ci.enumName, (0 until theory).map { infer.syntheticT("类型参数") })
            } ?: infer.syntheticT("构造子" + name)
            SymbolKind.BUILTIN -> frame?.type
                ?: infer.builtinSig(name)?.second
                ?: preludeFunSig(name)?.second
                ?: infer.syntheticT("内建" + name)
            else -> infer.syntheticT("未定义" + name)
        }
    }

    /** prelude 自由函数签名（t19 B1：core 模块声明的名字层兜底，调用点实参核对按本签名）。
     *  t44（ADR-29）：PreludeField → HIR 类型形态自此内联（旧 ctor 字段规格通道已随枚举表删除，
     *  本函数只服务自由函数签名表 PRELUDE_FUNS）。 */
    private fun preludeFunSig(name: String): Pair<List<HirType>, HirType>? =
        symbols.preludeFunSpec(name)?.let { spec ->
            spec.params.map { field -> preludeFieldType(field) } to preludeFieldType(spec.ret)
        }

    /** prelude 字段规格 → HIR 类型形态（root 应用 params 为零实参具名类型；自由函数签名用）。 */
    private fun preludeFieldType(field: PreludeField): HirType =
        HirNamedType(field.root, field.params.map { HirNamedType(it, emptyList(), null, freshNid(), SYNTHETIC_POS, emptyList()) },
            null, freshNid(), SYNTHETIC_POS, emptyList())

    /** 函数声明签名（形参/返回类型，调用点与值引用共用）。
     *  重解析时压入被引用声明的类型参数作用域（ADR-14：调用点签名里的 T 须可解析，
     *  无作用域则泛型签名全成「未知类型」）。
     *  t115（S2）：省略位按元变量解喂参——已求解用解（真实核对）；未决喂 Any 顶近似放行
     *  （约束由 recordCallArgs 独立收集，不依赖此处放行）。工程模式逐字保持 t113 半成品口径。 */
    private fun sigOf(d: FunDecl): Pair<List<HirType>, HirType?> {
        enterTypeParams(d.theory)
        return try {
            d.params.mapIndexed { i, q -> paramSigType(d, q, i) } to retSigType(d)
        } finally { exitTypeParams() }
    }
    /** t125（S5b）：调用方签名同步提升——与 param() 声明位同源（视图位 @mut ⇒ 调用按 R-01 表深拷核）。 */
    private fun paramSigType(d: FunDecl, q: Param, i: Int): HirType {
        val base = if (q.type != null) type(q.type)
        else if (scriptMode) {
            val m = metaForParam(d, i)
            if (m != null && m.resolved != null && m.hasEvidence()) m.resolved!! else infer.namedT("Any")
        }
        else type(ErrorType("形参类型标注省略", q.pos))
        val evi = if (scriptMode) mutPromoted[mutKey(q.pos, q.name)] else null
        return if (evi != null) promotedView(base, q.pos) else base
    }
    private fun retSigType(d: FunDecl): HirType? {
        if (d.retType != null) return type(d.retType)
        val m = metaForRet(d) ?: return null
        return if (m.resolved != null && m.hasEvidence()) m.resolved else infer.namedT("Any")
    }

    /** 函数声明的函数类型值（receiverBound=true 去 self 首参——o::f 引用形态）。 */
    private fun sigFunType(d: FunDecl, receiverBound: Boolean): sublimation.frontend.hir.HirFunType {
        val (ps, rt) = sigOf(d)
        return sublimation.frontend.hir.HirFunType(
            if (receiverBound) ps.drop(1) else ps, rt ?: infer.namedT("Null"),
            // t94：引用类型与声明注解口径一致化（E:705 同源）——@impure 声明的引用类型带 IMPURE；
            // t117 R-02 退化触发点：不收敛拥有者函数退化轮内同 impure 口径
            if ("impure" in d.annotations || d.name in degradedFuns) HirPurity.IMPURE else HirPurity.PURE,
            emptyList(), null, freshNid(), d.pos, emptyList(),
        )
    }

    /**
     * 类型别名展开（决策 30；深度上限 + 环保护——双向递归别名截断为 synthetic）。
     * t41（ADR-28 R-16）：带实参的联合别名一并展开——type Box[T] = Some[T] | None，
     * Box[Nat] → Some[Nat] | None（理论参数按位代入后展平）。
     */
    private fun expand(t: HirType, depth: Int = 0): HirType {
        if (depth > 8 || t !is sublimation.frontend.hir.HirNamedType) return t
        if (t.name in aliasExpanding) return infer.syntheticT("递归别名")
        val alias = aliasDecls[t.name] ?: return t
        // t41：带实参形态——理论参数按位代入目标后同走展平
        if (t.args.isNotEmpty()) {
            val tps = alias.theory.filterIsInstance<TypeParam>().map { it.name }
            if (tps.size != t.args.size) return t
            aliasExpanding += t.name
            enterTypeParams(alias.theory)
            val target = try { subTpNames(type(alias.target), tps.zip(t.args).toMap()) }
                finally { exitTypeParams(); aliasExpanding -= t.name }
            val flat = (target as? HirUnionType)?.let { flattenUnion(it, depth) } ?: target
            return if (flat is HirUnionType) flat else expand(flat, depth + 1)
        }
        aliasExpanding += t.name
        enterTypeParams(alias.theory)
        val target = try { type(alias.target) } finally { exitTypeParams(); aliasExpanding -= t.name }
        val flat = (target as? HirUnionType)?.let { flattenUnion(it, depth) } ?: target
        return if (flat is HirUnionType) flat else expand(flat, depth + 1)
    }

    /**
     * t41（ADR-28 R-21）：结构体 ctor 模式的槽位类型——成员字段声明类型 + 主题实参代入。
     * 代入源：联合主题里匹配成员的实参（Box[Nat] 展平后 Some[Nat] 位 → T := Nat），
     * 或带实参具名联合别名的实参；无名/不齐理论参数时取原始字段类型。
     */
    private fun structFieldTypes(ctorName: String, subjectT: sublimation.frontend.hir.HirType?): List<sublimation.frontend.hir.HirType> {
        val sd = structDecls[ctorName] ?: return emptyList()
        enterTypeParams(sd.theory)
        val base = try { sd.fields.map { expand(type(it.type ?: ErrorType("形参类型标注省略", it.pos))) } } finally { exitTypeParams() }
        val tps = sd.theory.filterIsInstance<TypeParam>().map { it.name }
        if (tps.isEmpty()) return base
        // t44（ADR-29）：主题先归一为联合（具名联合别名经 expand 展平），再取本 ctor 成员的实参——
        // 成员的 theory 与联合别名的 theory 可不同元（Result[T,E] 的成员 Ok[T]）：以**成员变体实参**为代入源，
        // 不用别名整体实参（旧具名分支在异元情形会误判 tps 不齐而回落未解析的参数）。
        val subj = (subjectT as? HirUnionType)
            ?: (subjectT?.let { expand(it) } as? HirUnionType)
        val args = (subj?.variants?.firstOrNull {
            (it as? sublimation.frontend.hir.HirNamedType)?.name == ctorName
        } as? sublimation.frontend.hir.HirNamedType)?.args
        if (args == null || args.size != tps.size) return base
        return base.map { subTpNames(it, tps.zip(args).toMap()) }
    }

    /**
     * t41（ADR-28 R-16）：联合成员就地展平——变体里的**具名别名**展开为目标（嵌套联合拼接）。
     * 自引用/环成员经 [expand] 深度上限后保留原具名（环检见 [aliasChainContains]）；容器实参位不动。
     */
    private fun flattenUnion(t: HirUnionType, depth: Int = 0): HirUnionType {
        if (depth > 8 || t.variants.isEmpty()) return t
        var changed = false
        val out = mutableListOf<sublimation.frontend.hir.HirType>()
        t.variants.forEach { v ->
            val ev = if (v is sublimation.frontend.hir.HirNamedType && v.args.isEmpty()) expand(v, 0) else v
            when {
                ev is HirUnionType -> { out.addAll(ev.variants); changed = true }
                else -> { if (ev !== v) changed = true; out.add(ev) }
            }
        }
        return if (!changed) t else HirUnionType(out, freshNid(), t.origin, t.notes)
    }

    /** t41：联合成员判重键——裸具名取名字，其余取结构渲染（元组/函数/应用形状）。 */
    private fun memberKey(v: sublimation.frontend.hir.HirType): String =
        (v as? sublimation.frontend.hir.HirNamedType)?.takeIf { it.args.isEmpty() }?.name ?: infer.render(v)

    /**
     * t41（ADR-28 R-17）：别名成员链是否（经别名展开）含目标名——环检/归纳判定的基。
     * seen 预置**被定义者自身**：成员直接写自身名也算引用；别名链只展开未见过的别名（防无限递归）。
     */
    private fun aliasChainContains(t: Type, target: String, self: String, seen: MutableSet<String>): Boolean = when (t) {
        is NamedType -> t.name == target ||
            t.args.any { aliasChainContains(it, target, self, seen) } ||
            (t.args.isEmpty() && t.name != self && aliasDecls[t.name] != null && seen.add(t.name) &&
                aliasChainContains(aliasDecls[t.name]!!.target, target, self, seen))
        is sublimation.frontend.ast.UnionType -> t.variants.any { aliasChainContains(it, target, self, seen) }
        is sublimation.frontend.ast.TupleType -> t.items.any { aliasChainContains(it, target, self, seen) }
        is sublimation.frontend.ast.FunType -> t.params.any { aliasChainContains(it, target, self, seen) } ||
            aliasChainContains(t.ret, target, self, seen)
        is sublimation.frontend.ast.MutType -> aliasChainContains(t.inner, target, self, seen)
        is sublimation.frontend.ast.AtomicType -> aliasChainContains(t.inner, target, self, seen)
        is sublimation.frontend.ast.RefType -> aliasChainContains(t.inner, target, self, seen)   // t95
        else -> false
    }

    /**
     * 调用消解（消解闭名单 1/2，ADR-1 §3.1.2.1/2）：
     * - 方法糖 xs.f(a) → 自由函数形态 f(xs, a)——HirCall(methodSugar=true)，D-METHOD-SUGAR 留痕；
     * - 命名参数按形参声明序重排（仅全局 FUN/THEOREM callee 有 paramNames）——D-NAMED-ARG 留痕；
     * - callee 解析结论落 symbol（NameRef → 值域；InstExpr → 实例化目标名引用结论）。
     */
    private fun callExpr(e: CallExpr): HirCall {
        val nid = freshNid()
        when (val c = e.callee) {
            is FieldExpr -> {
                // 消解闭名单 1：方法糖（S-5 无特质 impl 方法 → self 首参自由函数）
                if (e.namedArgs.isNotEmpty()) {
                    diag.error("E-NAMED-ARG", e.pos,
                        "方法 ." + ident(c.name) + "(x: 1) 不支持命名参数。命名实参只用于自由函数：f(x: 1)")
                }
                // P-8/D（卷 TS-12）：方法糖改道——按 x 的静态类型头查**扩展函数桶**
                gateBatchOpen()   // t122：实参批次（以调用位归并回放序）
                val args = (listOf(c.target) + e.args).map { expr(it) }
                val argTypes = args.map { etype(it) }
                // t47（ADR-31 R-27）：分派头保留**别名身份**——接收者静态类型经 expand 后是联合
                // （None | Some[Nat] ← Opt[Nat]）时键取别名头，而非 Union/N（成员头集合反查）
                val head = extHeadOf(argTypes.firstOrNull(), c.name)
                // t39：扩展函数按可见集解析（帧链内→外按键遮蔽 + 全局）
                val ext = if (head != null) symbols.lookupExtDeclVisible(head, c.name) else null
                val calleeSymbol = if (head != null) symbols.lookupExtFun(head, c.name).firstOrNull()?.ref() else null
                val callee = HirNameRef(c.name, calleeSymbol, null, freshNid(), c.pos, emptyList())
                val note = HirNote(HirNoteKind.DESUGAR, "D-METHOD-SUGAR",
                    "方法糖改道扩展函数桶（receiver 首参 + 体内 self 绑定为 Receiver）", e.pos,
                    "target." + c.name + "(args)", c.name + "(target, args)")
                // 签名核对：扩展桶命中 → 接收者作首参；否则内建兜底 | synthetic
                // t38/ADR-26 R-9：桶内至多 1 项；t39 可见集单值——命中即核对，arity/类型不符由 extCallType → callFun 报 E-ARITY
                // t39（ADR-27）：扩展桶未命中且无内建签名 → E-UNBOUND-NAME（局部扩展退帧后块外调用同此通道；修前静默降级为合成类型）
                // TS-12③：自由函数 receiver 入首参位的方法糖形态合法——仅在**三类通道皆无**时报错
                if (ext == null && infer.builtinSig(c.name) == null && e.namedArgs.isEmpty() &&
                    symbols.lookupValue(c.name) == null && !symbols.preludeMethod(head, c.name))
                    diag.error("E-UNBOUND-NAME", c.pos, "方法 ." + ident(c.name) +
                        " 不可解析：作用域内与全局均无同名扩展函数，也不是内建方法（局部扩展随作用域退出而不可见）")
                val t: HirType = ext?.let { d ->
                    extCallType(d, argTypes, e.pos)
                } ?: infer.builtinSig(c.name)?.let { (ps, rt) ->
                    infer.callFun(c.name, ps, null, retType = rt, argTypes = argTypes, pos = e.pos,
                            theoryNames = infer.builtinTheoryNames(c.name))
                } ?: infer.syntheticT("方法" + c.name)
                val node = HirCall(
                    callee = callee, args = args, symbol = calleeSymbol, type = t, dictionary = null,
                    slot = null, dictArgs = emptyList(), methodSugar = true, awaitPoint = false,
                    calleeImpure = false, nid = nid, origin = e.pos, notes = notesOf(e, listOf(note)),
                )
                onCall(node, c.name)   // t97+t94+t122 订阅（事件化；接收者入首实参位）
                return node
            }
            else -> {
                gateBatchOpen()   // t122：实参/默认填补/被调者同批次（以调用位归并回放序）
                val callee: HirExpr
                val symbol: SymbolRef?
                val explicitTps: List<HirType>   // B8（t23，ADR-21）：角表显式类型实参（类型域读取）
                when (c) {
                    is NameRef -> {
                        // P-8/D（卷 TS-14）：直接调用先查自由函数——值域无该名且存在扩展函数候选 →
                        // 静默（不落 E-UNBOUND-NAME），待按实参 1 的类型头查扩展桶（或 T::f 限定头）
                        symbol = if (symbols.lookupValue(c.name) == null && extCallPlan(c.name) != null)
                            null else resolver.resolveValue(c.name, c.pos)
                        callee = HirNameRef(c.name, symbol, null, freshNid(), c.pos, notesOf(c))
                        explicitTps = emptyList()
                    }
                    is InstExpr -> {
                        // B8：调用位 callee 的紧贴角表——按名解析，terms 按类型域读取（旧 CallCheck:158-168 口径），
                        // 不再把 target/terms 当值消解（`Channel<Nat>(1)` 的两个 E-UNBOUND-NAME 根因）
                        val nm = (c.target as? NameRef)?.name
                        if (nm != null && symbols.lookupValue(nm) != null) {
                            symbol = resolver.resolveValue(nm, c.target.pos)
                            callee = HirNameRef(nm, symbol, null, freshNid(), c.target.pos, notesOf(c.target))
                            explicitTps = c.terms.map { termType(it) }
                        } else {
                            callee = expr(e.callee)
                            symbol = (callee as? HirInst)?.let { it.target as? HirNameRef }?.symbol
                            explicitTps = emptyList()
                        }
                    }
                    else -> {
                        callee = expr(e.callee)
                        symbol = null
                        explicitTps = emptyList()
                    }
                }
                var reordered: ReorderResult? = null
                var reorderFailed = false
                if (e.namedArgs.isNotEmpty() && c is NameRef) {
                    // funEntry 口径：funs ?: theorems（定理函数同享命名参数通道）
                    // t52：局部自由函数条目同享（帧链内→外，局部优先于全局同名）
                    val entry = symbols.funEntry(c.name) ?: symbols.lookupLocalFunEntry(c.name)
                    if (entry?.paramNames != null) {
                        reorderFailed = true
                        reordered = reorderNamedArgs(entry, c.name, e)
                        if (reordered != null) reorderFailed = false
                    }
                }
                val calleeName = (c as? NameRef)?.name ?: symbol?.name ?: ""
                val rawArgs = reordered?.args?.map { it ?: sublimation.frontend.ast.ErrorExpr("缺实参", e.pos) } ?: e.args
                // t45（ADR-30 R-23）：默认填补位在**声明处环境**求值（重建被调函数帧：theory + self + 前序形参），
                // 调用者局部变量不被默认值捕获
                val defaultHirs = mutableMapOf<Int, sublimation.frontend.hir.HirExpr>()
                reordered?.fills?.forEach { (argIdx, paramIdx) ->
                    defaultExprAtParam(calleeName, paramIdx, e.pos)?.let { defaultHirs[argIdx] = it }
                }
                // t52：两遍通道同样按局部函数声明取形参（局部优先于全局同名）
                // t66（ADR-43）：直接调用实参的收窄期望——**只看形参 AST 静态形态**（零解析副作用：
                //  形参声明须就是裸 Nat 才推入期望；理论参数/模块外类型/别名一律不推——e2e 逐字不变）
                val calleeDeclT66 = if (calleeName.isNotEmpty())
                    symbols.lookupLocalFunDecl(calleeName) ?: funDecls[calleeName] else null
                val paramExpects = calleeDeclT66?.params?.map { q ->
                    (q.type as? NamedType)?.takeIf { it.name == "Nat" && it.args.isEmpty() } }
                val args = if (calleeName.isNotEmpty() && calleeDeclLambda(calleeName) != null &&
                    rawArgs.any { it is LambdaExpr })
                    lambdaTwoPassArgs(calleeDeclLambda(calleeName)!!, rawArgs, explicitTps, defaultHirs)
                else rawArgs.mapIndexed { i, a ->
                    defaultHirs[i] ?: expectAt(paramExpects?.getOrNull(i)?.let { infer.namedT("Nat") }) { expr(a) } }
                val notes = if (reordered != null)
                    notesOf(e, listOf(HirNote(HirNoteKind.DESUGAR, "D-NAMED-ARG",
                        "命名实参按形参声明序重排", e.pos, "f(n = v)", "f(v)")))
                else notesOf(e)
                // t8 调用签名核对（重排失败 → 已落 E-NAMED-ARG，跳过参数检查避免重复诊断）
                val argTypes = args.map { etype(it) }
                // P-8/D（卷 TS-12/TS-14）：扩展函数直接调用（f(x, a)）——静默名或 T::f 限定，
                // 按实参 1 的类型头（或限定头）查扩展桶；自由函数优先（已在上面 resolveValue 判定）
                if (symbol == null && c is NameRef) {
                    val plan = extCallPlan(c.name)
                    when {
                        plan == null -> { }   // 非扩展候选：原行为（E-UNBOUND-NAME 已由 resolveValue 落）
                        e.namedArgs.isNotEmpty() ->
                            diag.error("E-NAMED-ARG", e.pos,
                                "扩展函数 " + ident(plan.second) + " 直接调用不支持命名参数。命名实参只用于自由函数")
                        else -> {
                            val head = plan.first ?: extHeadOf(argTypes.firstOrNull(), plan.second)
                            // t39：可见集（帧链内→外按键遮蔽 + 全局）
                            val exts = if (head != null) symbols.lookupExtDeclVisible(head, plan.second) else null
                            // 桶内至多 1 项（V1 禁止重载，ADR-26）；t39 可见集单值（局部扩展可遮蔽全局）
                            val d = exts
                            if (d != null) {
                                val extSymbol = symbols.lookupExtFun(head!!, plan.second).firstOrNull()?.ref()
                                val tExt = extCallType(d, argTypes, e.pos)
                                val extCallee = HirNameRef(plan.second, extSymbol, null, freshNid(), c.pos, notesOf(c))
                                val node = HirCall(
                                    callee = extCallee,
                                    args = args, symbol = extSymbol, type = tExt, dictionary = null,
                                    slot = null, dictArgs = emptyList(), methodSugar = false, awaitPoint = false,
                                    calleeImpure = false, nid = nid, origin = e.pos, notes = notes,
                                )
                                onCall(node, plan.second)   // t97+t94+t122 订阅（事件化）
                                return node
                            }
                            if (plan.first == null) resolver.resolveValue(c.name, c.pos)   // 静默落空补诊断
                        }
                    }
                }
                // t52：被调声明——局部帧槽优先（遮蔽全局同名），全局分表兜底
                val calleeDecl = symbols.lookupLocalFunDecl(calleeName) ?: funDecls[calleeName]
                // t115（S2，约束袋）：调用位实参约束——形参省略处收下界、实参为未决占位处收上界（判据仍走既有
                // callFun/sigCheckedCall：此处只登记源序证据，不改变核对结果）
                if (!reorderFailed) recordCallArgs(calleeName, argTypes, e.pos)
                // t118（提案 §3.2 ② 跨声明签名事实）：调用点对 callee 闭合签名的按轮传递（R 轮天然红利）
                // ——callee 形参位为元变量且已种子闭合 + 实参为具名变量 ⇒ (arg : T) 注入（同①分账/CAP）
                if (!reorderFailed) calleeDecl?.let { d -> injectSigFacts(d, e) }
                val t: HirType = if (reorderFailed) infer.syntheticT("调用" + calleeName)
                else when (symbol?.kind) {
                    // t52：局部函数与全局函数同走 sigCheckedCall（一份真相，不另开特判通道）
                    SymbolKind.FUN, SymbolKind.THEOREM, SymbolKind.LOCAL -> calleeDecl?.let { d ->
                        sigCheckedCall(d, calleeName, argTypes, e.pos, explicitTps)
                    } ?: funValueCall(calleeName, argTypes, e.pos, explicitTps)
                    SymbolKind.CTOR -> symbols.lookupCtor(calleeName)?.let { ci ->
                        ctorCall(calleeName, ci, argTypes, e.pos, explicitTps)
                    } ?: infer.syntheticT("构造子" + calleeName)
                    SymbolKind.BUILTIN -> infer.builtinSig(calleeName)?.let { (ps, rt) ->
                        infer.callFun(calleeName, ps, null, retType = rt, argTypes = argTypes, pos = e.pos,
                            theoryNames = infer.builtinTheoryNames(calleeName))
                    } ?: preludeFunSig(calleeName)?.let { (ps, rt) ->
                        // t19（B1，ADR-18）：core 模块自由函数（sum/range/summation）单文件期签名兜底
                        infer.callFun(calleeName, ps, null, retType = rt, argTypes = argTypes, pos = e.pos,
                            theoryNames = infer.builtinTheoryNames(calleeName))
                    } ?: symbols.lookupValue(calleeName)?.type ?: infer.syntheticT("内建" + calleeName)
                    else -> funValueCall(calleeName, argTypes, e.pos, explicitTps)
                }
                // P-8/D（卷 TS-21/TS-22）：自由函数调用点约束检查（理论参数解出具体类型后查 satisfies）
                if (symbol?.kind == SymbolKind.FUN || symbol?.kind == SymbolKind.THEOREM ||
                    symbol?.kind == SymbolKind.LOCAL)
                    calleeDecl?.let { d ->
                        val tpsD = d.theory.filterIsInstance<TypeParam>().map { it.name }
                        if (tpsD.isNotEmpty()) {
                            val (psD, _) = sigOf(d)
                            checkConstraints(d, infer.instCall(psD.map { expand(it) }, tpsD, argTypes, explicitTps).first, e.pos)
                        }
                    }
                val node = HirCall(
                    callee = callee, args = args, symbol = symbol, type = t, dictionary = null,
                    slot = null, dictArgs = emptyList(), methodSugar = false, awaitPoint = false,
                    calleeImpure = false, nid = nid, origin = e.pos, notes = notes,
                )
                onCall(node, callCalleeName(callee))   // t97+t94+t122 订阅（事件化）
                return node
            }
        }
    }

    /** B8（t23，ADR-21）：调用位角表的显式类型实参按**类型域**读取（旧 typeOfTerm，V1：具名类型与
     * 实例化应用；`(T)=>U` 形态后续）。紧贴角表三义（实例化/显式供给/上下文约束，§2.10）中，
     * 调用位按理论参数实例化读取——命题供给位属定理供给路径（不改本表）。 */
    private fun termType(e: Expr): HirType = when (e) {
        is NameRef -> type(sublimation.frontend.ast.NamedType(e.name, emptyList(), e.pos))
        is InstExpr -> {
            val nm = (e.target as? NameRef)?.name
            if (nm != null) infer.namedT(nm, e.terms.map { termType(it) }) else infer.syntheticT("类型实参")
        }
        else -> infer.syntheticT("类型实参")
    }

    /**
     * B9（ADR-22）：lambda 实参两遍定型——
     * 第一遍：非 lambda 实参（含显式类型实参）先经 [sublimation.frontend.semantic.TypeInfer.lambdaExpectFormals]
     * 解理论参数并回代形参；第二遍：lambda 位以回代后的形参参数位定型（新增供给 lambdaExpect，
     * 取用即还）。全量核对（含报错）仍由调用签名处的 instCall/adaptOk 承担，本通道不放宽任何诊断。
     */
    /**
     * t47（ADR-31 R-27）：扩展分派的接收者头——保留**别名身份**。
     * * - 静态类型为具名（未展开）→ ExtFunHeads.ofHir（原口径，含 Tuple/Fun 形状规范化）；
     * * - 静态类型为联合（expand 后形态：var s: Opt[Nat] → None | Some[Nat]）→ 按**成员头集合**
     *   反查别名（用户别名 + prelude 虚拟联合别名），键取别名头（Opt/List…）——登记侧
     *   （ExtFunHeads.ofAst 取 receiver 名）与查找侧重新同构。多别名同头集合时优先取
     *   已注册该名字扩展桶的候选；其次用户别名优先于 prelude。
     */
    /**
     * t48（ADR-32 R-29）：struct 头 → 所属联合别名头集合（成员隶属索引）。
     * aliasDecls 就绪后惰性构建；成员只计具名头（联合/元组/函数形状头不参与隶属）。 */
    private val unionMembership: Map<String, LinkedHashSet<String>> by lazy {
        val out = mutableMapOf<String, LinkedHashSet<String>>()
        for ((aliasName, d) in aliasDecls) {
            val body = d.target as? sublimation.frontend.ast.UnionType ?: continue
            body.variants.mapNotNull { (it as? sublimation.frontend.ast.NamedType)?.name }.forEach { m ->
                out.getOrPut(m) { LinkedHashSet() } += aliasName
            }
        }
        out
    }

    private fun extHeadOf(t: sublimation.frontend.hir.HirType?, methodName: String = ""): String? {
        val direct = ExtFunHeads.ofHir(t)
        val u = t as? sublimation.frontend.hir.HirUnionType
        // 非联合接收者：原口径（具名头 / Tuple·Fun 形状键）
        if (u == null) {
            // t48（ADR-32 R-29）：非联合（struct 头）直查 miss → 按成员隶属回退到联合别名头再查。
            // 只在 methodName 非空（分派上下文）且直头确非形状键时回退；命中多个取**声明序最晚者**
            // （aliasDecls 顺序=prelude 先于用户，最晚=用户后声明，符合 P-7 覆盖精神），并留 supplement 痕。
            if (direct != null && methodName.isNotEmpty() && direct !in setOf("Union", "Tuple", "Fun") &&
                !direct.startsWith("Union/") && !direct.startsWith("Tuple/") && !direct.startsWith("Fun/") &&
                symbols.lookupExtDeclVisible(direct, methodName) == null) {
                val owners = unionMembership[direct] ?: return direct
                val winners = owners.filter { symbols.lookupExtDeclVisible(it, methodName) != null }
                if (winners.isEmpty()) return direct
                if (winners.size > 1)
                    diag.supplement("D-EXT-UNION-AMBIG", t?.origin ?: SYNTHETIC_POS,
                        "实参头 " + ident(direct) + " 同时隶属多个声明了 ." + ident(methodName) + " 的联合（" +
                        winners.joinToString(", ") { ident(it) } + "）；按声明序取最晚者 " + ident(winners.last()))
                return winners.last()
            }
            return direct
        }
        // 联合接收者：**别名身份优先**（var s: Opt[Nat] 的 None | Some[Nat] → Opt；
        // expand 把别名身份抹掉是分派失效的根因）。成员须全具名才可反查别名。
        val memberHeads = u.variants.mapNotNull { v -> ExtFunHeads.ofHir(v) }.toSet()
        if (memberHeads.isEmpty() || memberHeads.size != u.variants.size) return direct
        val candidates = mutableListOf<String>()
        for ((name, d) in aliasDecls) {
            val body = d.target as? sublimation.frontend.ast.UnionType ?: continue
            val heads = body.variants.mapNotNull { v ->
                (v as? sublimation.frontend.ast.NamedType)?.name?.let { ExtFunHeads.ofHir(infer.namedT(it)) }
            }.toSet()
            if (heads.size == body.variants.size && heads == memberHeads) candidates.add(name)
        }
        if (candidates.isEmpty()) return direct
        return candidates.firstOrNull { c -> methodName.isNotEmpty() && symbols.lookupExtDeclVisible(c, methodName) != null }
            ?: candidates.first()
    }

    /**
     * t52（ADR-34 R-32）：调用点签名核对共享件——全局函数与局部函数同路（一份真相）：
     * sigOf 取形参/返回（声明位 expand），变长旗标（R-30）与默认值位旗标随调用下传 callFun，
     * 实参类型核对/缺实参/arity 全在 callFun，本件不做任何特判。
     */
    private fun sigCheckedCall(
        d: FunDecl, calleeName: String, argTypes: List<HirType>,
        pos: SourceRange, explicitTps: List<HirType>,
    ): HirType {
        // B8：显式类型实参先绑定（instCall 内显式优先+不覆盖）；个数核对**不做**——
        // 角表三义：命题供给（witness<p>()）的 terms 非 types，旧 checkFnCall 亦只按
        // `i < count` 绑定前 N 个（个数核对仅旧 FunType 值调用路径有，本路径对齐其沉默）
        val (ps, rt) = sigOf(d)
        val tps = d.theory.filterIsInstance<TypeParam>().map { it.name }
        // t48（R-30）：变长旗标随调用下传（末位 @list）
        val vp = d.params.map { "list" in it.annotations }
        return infer.callFun(calleeName, ps.map { expand(it) }, d.params.map { it.name },
            d.params.map { it.default != null }, rt?.let { expand(it) }, argTypes, pos, tps, explicitTps, vp)
    }

    /** t52：lambda 两遍通道用——被调函数声明（局部优先于全局同名）。 */
    private fun calleeDeclLambda(name: String): FunDecl? =
        symbols.lookupLocalFunDecl(name) ?: funDecls[name]

    /**
     * t58（ADR-36 R-37）：函数值调用——被调本身是函数类型的值（形参 f: (T) => U、变量 fn），
     * 按其函数类型核对实参并取返回类型；旧路径给合成「调用f」（推导未完成的合成位在严格 adaptOk 下被拒，
     * 且把返回类型错取成整个函数类型）。函数值无命名形参/默认值/@list（全 false/空）。
     */
    private fun funValueCall(
        name: String, argTypes: List<HirType>, pos: SourceRange, explicitTps: List<HirType>,
    ): HirType {
        val vt = symbols.lookupValue(name)?.type as? sublimation.frontend.hir.HirFunType
            ?: return infer.syntheticT("调用" + name)
        return infer.callFun(name, vt.params, null, vt.params.map { false }, vt.ret,
            argTypes, pos, vt.theory.map { it.name }, explicitTps, vt.params.map { false })
    }

    private fun lambdaTwoPassArgs(
        d: FunDecl, rawArgs: List<Expr>, explicitTps: List<HirType>,
        preHirs: Map<Int, sublimation.frontend.hir.HirExpr> = emptyMap(),
    ): List<sublimation.frontend.hir.HirExpr> {
        val (ps, _) = sigOf(d)
        val tpsNames = d.theory.filterIsInstance<TypeParam>().map { it.name }
        val formalTypes = ps.map { expand(it) }
        val slots = ArrayList<HirExpr?>(rawArgs.size)
        repeat(rawArgs.size) { slots.add(null) }
        // 两遍序：非 lambda 实参先定型（lambda 位推后，避免无期望形参先定型）
        val preArgTypes = ArrayList<HirType>(rawArgs.size)
        rawArgs.forEachIndexed { i, a ->
            if (a is LambdaExpr) preArgTypes.add(i, infer.syntheticT("lambda位"))
            else {
                // t45：默认填补位用声明处环境求得的 HIR，不在调用点重解
                val h = preHirs[i] ?: expr(a)
                slots[i] = h
                preArgTypes.add(i, etype(h))
            }
        }
        val expects = infer.lambdaExpectFormals(formalTypes, tpsNames, preArgTypes, explicitTps)
        rawArgs.forEachIndexed { i, a ->
            if (a is LambdaExpr) {
                lambdaExpect = (expects.getOrNull(i) as? sublimation.frontend.hir.HirFunType)?.params
                try { slots[i] = expr(a) } finally { lambdaExpect = null }
            }
        }
        return slots.map { it!! }
    }

    /** 构造子调用核对入口（单一 struct 源：用户 struct + t44 prelude 虚拟 struct 同通道）。
     * t18 泛型实例化 + t20 B5 默认值（位置式前缀供给）+ t23 B8 显式类型实参（显式优先不覆盖）。
     * t41（ADR-28 R-19）：空构造子单例——判据=字段数 0：用户 struct 与 prelude None()/Nil()/SetNil()/
     * MapNil() 一并报 E-NULLARY-PAREN（prelude 通道不再豁免，用户裁决）。
     * t44（ADR-29）：prelude 构造子的旧枚举字段规格兜底（preludeFormals 等）随枚举表删除。 */
    private fun ctorCall(
        calleeName: String, ci: CtorInfo, argTypes: List<HirType>, pos: SourceRange,
        explicitTps: List<HirType>,
    ): HirType {
        val st = structDecls[ci.enumName] ?: return infer.syntheticT("构造子" + calleeName)
        if (st.fields.isEmpty())
            diag.error("E-NULLARY-PAREN", pos,
                "空构造子 " + ident(calleeName) + " 不能用 () 构造，请直接写 " + ident(calleeName) + "（空构造子全局唯一）")
        enterTypeParams(st.theory)
        // t41（ADR-28 R-16）：字段类型里的联合别名就展开（List[T] → Nil | Cons[T]），供实例化后逐位核对
        // t115：省略字段（脚本模式）→ 元变量落位解（有证据）/ Any 顶近似（默认填充）；工程模式逐字不变
        val (formals, defaults) = try {
            st.fields.map { f -> fieldSigType(st, f) } to st.fields.map { f -> f.default != null }
        } finally { exitTypeParams() }
        val tps = st.theory.filterIsInstance<TypeParam>().map { it.name }
        // t115：构造位约束收集（位置式，脚本模式省略字段 ← 实参下界）
        recordCtorFields(st, st.fields.map { it.name }.zip(argTypes), pos)
        return infer.callCtor(calleeName, ci.enumName, formals, tps, argTypes, pos, defaults, explicitTps)
    }


    /** 命名实参重排（消解闭名单 2）：destruct/_ 形参 / 未知参数 / 位置占用 → 旧码集 E-NAMED-ARG。
     *  t45（ADR-30 R-23）：默认填补位登记（argIdx → paramIdx），实参的求值由调用点按声明处环境重建。 */
    private fun reorderNamedArgs(entry: SymbolEntry, calleeName: String, e: CallExpr): ReorderResult? {
        val names = entry.paramNames ?: return null
        if (entry.destructParams) {
            diag.error("E-NAMED-ARG", e.pos, "函数 " + ident(calleeName) + " 含解构或 _ 参数，不支持命名参数")
            return null
        }
        val slots = arrayOfNulls<Expr>(names.size)
        val taken = BooleanArray(names.size)
        e.args.forEachIndexed { i, a -> if (i < names.size) { slots[i] = a; taken[i] = true } }
        for ((n, v) in e.namedArgs) {
            val i = names.indexOf(n)
            if (i < 0) {
                diag.error("E-NAMED-ARG", e.pos, "函数 " + ident(calleeName) + " 没有叫 " + ident(n) + " 的参数（拼写错了？可用参数：" +
                    names.joinToString(", ") { ident(it) } + ")")
                return null
            }
            if (taken[i]) {
                diag.error("E-NAMED-ARG", e.pos, "函数 " + ident(calleeName) + " 的参数 " + ident(names[i]) + " 已被位置实参占用")
                return null
            }
            slots[i] = v
            taken[i] = true
        }
        // 默认值填补（决策 69 扩展，ADR-15）：未占位的形参位登记为填补位（t45：默认值 AST 仅作占位，
        // 求值环境由调用点按声明处环境重建，见 defaultExprAtParam），保持位置对齐；命名实参跨位省略
        // （f(1, c = 3) 的 b 位）不再移位错配；无默认值的空位落旧口径 E-TYPE-MISMATCH 缺实参
        // + 调用点哨兵承接（type 位 synthetic 不干扰其余形参核对）。
        val declParams = funDecls[calleeName]?.params
        val fills = mutableMapOf<Int, Int>()
        val args = names.mapIndexed { i, n ->
            val supplied = slots[i]
            if (supplied != null) supplied
            else {
                val idx = declParams?.indexOfFirst { it.name == n } ?: -1
                if (idx >= 0 && declParams!![idx].default != null) {
                    fills[i] = idx
                    declParams[idx].default!!   // 占位：调用点按声明处环境求值后替换
                } else {
                    diag.error("E-TYPE-MISMATCH", e.pos, "函数 " + ident(calleeName) + " 缺实参：" + ident(n))
                    null
                }
            }
        }
        return ReorderResult(args, fills)
    }

    /** t45（ADR-30 R-23）：命名重排结果——实参位（默认填补位保留声明 AST 占位）+ 填补登记。 */
    private data class ReorderResult(val args: List<Expr?>, val fills: Map<Int, Int>)

    /**
     * t45（ADR-30 R-23）：默认值表达式的**声明处求值环境重建**——被调函数的理论参数 + 接收者 self
     * + **前序**形参（类型按声明位解释）依次入函数帧，默认值在此帧内 expr() 求值；调用者局部变量
     * 不可见（词法作用域天然保证）。声明位已落 E-DEFAULT-ORDER 的后序引用此处不再填补（重复无信息量）。
     */
    private fun defaultExprAtParam(funName: String, paramIdx: Int, callPos: SourceRange): sublimation.frontend.hir.HirExpr? {
        // t52：局部函数声明优先于全局同名（遮蔽口径同符号查找）
        val d = symbols.lookupLocalFunDecl(funName) ?: funDecls[funName] ?: return null
        val dp = d.params
        val def = dp.getOrNull(paramIdx)?.default ?: return null
        if (forwardName(def, dp.drop(paramIdx + 1).map { it.name }.toSet()) != null) return null
        return symbols.isolatedFrame {
            enterTypeParams(d.theory)
            symbols.frame(ScopeKind.FUNCTION) {
                if (d.receiver != null)
                    declareLocal("self", SymbolKind.PARAM, d.receiver!!.pos, type(d.receiver!!))
                dp.take(paramIdx).forEach { p -> declareLocal(p.name, SymbolKind.PARAM, p.pos, type(p.type ?: ErrorType("形参类型标注省略", p.pos))) }
                    expr(def)
                }
            }
        }

    /** t60-2：字段写可写核定——①字段须 @mut 声明；②接收者可写（@mut 类型位 或 @mut 绑定）。
     *  不可写落 E-IMMUT-ASSIGN（与名字位同码同族）。字段未登记/结构体未知时不重复报（上游 fieldMissing
     *  已落），仅返回 true 让 t60-1 类型闸继续——两闸独立、缺一不可（可变性 ≠ 类型）。 */
    private fun fieldWriteOk(fe: FieldExpr, elaborated: sublimation.frontend.hir.HirExpr, pos: SourceRange): Boolean {
        val hf = elaborated as? sublimation.frontend.hir.HirField ?: return true
        val recvT = etype(hf.target)
        // t98（N2 @ref · 规则 1 可写性）:@mut T 或 @ref @mut T 可写（写穿透共享引用）;
        //  @ref T / T 只读（根文档 :59-63「b.n = 1 无法赋值」）
        val recvWritable = infer.isWritableView(recvT) ||
            ((fe.target is NameRef) && symbols.lookupValue((fe.target as NameRef).name)?.mutable == true)
        // t98：@ref 视图包裹时剥层取结构体名（字段登记侧与可写核定同口径）
        var recvCore = recvT
        while (recvCore is sublimation.frontend.hir.HirRefType) recvCore = recvCore.inner
        val structName = (recvCore as? sublimation.frontend.hir.HirNamedType)?.name
            ?: (recvCore as? sublimation.frontend.hir.HirMutType)?.inner
                ?.let { (it as? sublimation.frontend.hir.HirNamedType)?.name }
        val field = structName?.let { sn -> structDecls[sn]?.fields?.firstOrNull { f -> f.name == fe.name } }
        return when {
            field == null -> true      // 上游 fieldMissing/未登记已落诊断；t60-1 类型闸照常继续
            "mut" !in field.annotations -> {
                diag.error("E-IMMUT-ASSIGN", pos,
                    "字段 " + ident(fe.name) + " 不可变，不能写。要改它？在结构体声明里给字段加 @mut")
                false
            }
            !recvWritable -> {
                diag.error("E-IMMUT-ASSIGN", pos,
                    "不能通过不可变接收者写字段 " + ident(fe.name) + "。要改它？把接收者声明写成 @mut 类型（或 @ref @mut 共享引用）或 @mut var")
                false
            }
            else -> true
        }
    }

    /** t98（规则 3 拷贝分派留痕）：got→want 的共享/深拷判定落 D-REF-DISPATCH 备注下（@ref 涉及时）。 */
    private fun refDispatchNote(pos: SourceRange, got: sublimation.frontend.hir.HirType, want: sublimation.frontend.hir.HirType, what: String): sublimation.frontend.hir.HirNote {
        val copy = infer.copyDispatch(got, want)
        return sublimation.frontend.hir.HirNote(sublimation.frontend.hir.HirNoteKind.SUPPLEMENT, "D-REF-DISPATCH",
            what + " 视图分派：" + infer.render(got) + " → " + infer.render(want) +
                (if (copy) "（深拷隔离——源端可写/目标无引用视图）" else "（共享同一对象——目标持引用视图）" +
                    "（前端类型层判定，实际深拷随后端运行时，R2 §三）"),
            pos, infer.render(got), infer.render(want))
    }

    // ============================ 模式 ============================

    /**
     * t33：slotT = 所属 ctor 字段声明类型（模式绑定取静态类型——TS-12/TS-14 三形态对称的根）；
     * subjectT = 本层 ctor 模式匹配的主题类型（枚举实参位替换：enum E[T] 与主题 E[实参] → 字段类型按位实例化）。
     */
    private fun pattern(
        p: Pattern, mode: PatternMode,
        slotT: sublimation.frontend.hir.HirType? = null, subjectT: sublimation.frontend.hir.HirType? = null,
    ): sublimation.frontend.hir.HirPattern {
        return when (p) {
        is PatLit -> HirPatLit(expr(p.value), freshNid(), p.pos, notesOf(p))
        is PatCtor -> {
            val nid = freshNid()
            // t41（ADR-28 R-19）：空构造子的括号模式（None() / is None() / is None(x)）——E-NULLARY-PAREN
            if (p.paren) {
                val ci = symbols.lookupCtor(p.name)
                if (ci != null && ci.nullary && structDecls[ci.enumName]?.fields?.isEmpty() == true)
                    diag.error("E-NULLARY-PAREN", p.pos,
                        "空构造子 " + ident(p.name) + " 不能用 () 构造，请直接写 " + ident(p.name) + "（空构造子全局唯一）")
            }
            // 空参 PatCtor 是裸名形态（解析层约定：ctor 实参位的裸名与 C() 同形，由语义层裁定）
            if (p.args.isEmpty())
                return bareNamePattern(nid, p.name, p.pos, mode, notesOf(p), slotT, p.paren)
            // t41（R-21）：结构体 ctor 槽位类型重建——字段声明类型按联合主题实参实例化
            val fieldTs = structFieldTypes(p.name, subjectT)
            HirPatCtor(p.name, p.args.mapIndexed { i, a ->
                val st = fieldTs.getOrNull(i)
                pattern(a, PatternMode.SLOT, st, st)   // 嵌套 ctor 以本槽位类型为题继续实例化
            }, resolver.resolveCtor(p.name, p.pos), nid, p.pos, notesOf(p))
        }
        is PatTuple -> {
            val nid = freshNid()
            val items = p.items.map { pattern(it, PatternMode.SLOT) }
            HirPatTuple(items, nid, p.pos, notesOf(p))
        }
        is PatIs -> {
            val nid = freshNid()
            // I2（作者裁决 2026-11-01）：is T 中零字段构造子归一为构造子模式（is None → None()）
            val tn = (p.type as? NamedType)?.name
            if (tn != null && p.type.args.isEmpty() && !symbols.isTypeName(tn)) {
                val ctor = symbols.lookupCtor(tn)
                if (ctor != null && ctor.nullary) {
                    // t33：is 零字段 ctor 归一时绑定登记 is 类型
                    if (p.bind != null && p.bind != "_")
                        symbols.declareLocal(SymbolKind.LOCAL, p.bind, p.pos, type(p.type))
                    val note = HirNote(HirNoteKind.DESUGAR, "D-PAT-CTOR",
                        "零字段构造子归一为构造子模式", p.pos, "is " + tn, tn + "()")
                    return HirPatCtor(tn, emptyList(), ctor.entry.ref(), nid, p.pos, notesOf(p, listOf(note)))
                }
            }
            // t33：as 绑定登记 is 类型（按主题实例化）
            val isT = subTpNames(type(p.type), ctorSubst(subjectT, (p.type as? NamedType)?.name ?: ""))
            val bindSymbol = if (p.bind != null && p.bind != "_")
                symbols.declareLocal(SymbolKind.LOCAL, p.bind, p.pos, isT).ref() else null
            HirPatIs(isT, p.bind, bindSymbol, nid, p.pos, notesOf(p))
        }
        is PatBind -> {
            val nid = freshNid()
            // t22（B3，ADR-20）：true/false 于臂顶层是 Bool 值模式（字面量匹配），不按名字绑定——
            // 密封 Bool 主题的覆盖判定按 true/false 两值计入。
            // t84（Q12 收口）：小写 true/false 经此分支调 resolveValue ⇒ 落 E-UNBOUND-NAME + 大写
            // 提示（半成品 HirPatLit 保留，覆盖计数照常——文件已因错失败）。
            if (mode == PatternMode.TOP && (p.name == "true" || p.name == "false" || p.name == "True" || p.name == "False")) {
                val symbol = resolver.resolveValue(p.name, p.pos)
                val note = HirNote(HirNoteKind.DESUGAR, "D-PAT-BOOL",
                    "Bool 值模式（true/false 按字面量匹配，不作绑定名）", p.pos, p.name, p.name)
                val lit = HirNameRef(p.name, symbol, infer.namedT("Bool"), freshNid(), p.pos, emptyList())
                return sublimation.frontend.hir.HirPatLit(lit, nid, p.pos, notesOf(p, listOf(note)))
            }
            // 消解闭名单 4：槽位（ctor/tuple 实参位）旧名 → 相等比较；臂顶层裸名 → 构造子裁定
            if (mode == PatternMode.SLOT) {
                if (p.name != "_" && symbols.lookupValue(p.name) != null) {
                    val symbol = symbols.lookupValue(p.name)!!.ref()
                    val note = HirNote(HirNoteKind.DESUGAR, "D-PAT-SLOT",
                        "解构槽位重名按相等比较处理（要绑定请用新名字）", p.pos,
                        "slot " + p.name, "cmp " + p.name)
                    val value = HirNameRef(p.name, symbol, null, freshNid(), p.pos, emptyList())
                    return sublimation.frontend.hir.HirPatLit(value, nid, p.pos, notesOf(p, listOf(note)))
                }
                // t33：槽位绑定登记 ctor 字段声明类型（pattern-bound 变量的静态类型）
                val symbol = declareLocal(p.name, SymbolKind.LOCAL, p.pos, slotT)
                injectTypeFacts(p.name, slotT ?: infer.syntheticT("模式槽位" + p.name), p.pos)   // t65
                return HirPatBind(p.name, symbol, nid, p.pos, notesOf(p))
            }
            val ctor = symbols.lookupCtor(p.name)
            if (ctor != null) {
                if (ctor.nullary) {
                    val note = HirNote(HirNoteKind.DESUGAR, "D-PAT-CTOR",
                        "裸名构造子归一为构造子模式", p.pos, p.name, p.name + "()")
                    return HirPatCtor(p.name, emptyList(), ctor.entry.ref(), nid, p.pos, notesOf(p, listOf(note)))
                }
                // 新码：载荷构造子裸名分臂必须解构（旧实现由 ctor 裁定隐含）
                diag.error("E-CTOR-ARITY", p.pos,
                    "构造子 " + ident(p.name) + " 带载荷，分臂必须解构：" + ident(p.name) + "(x)")
                return HirPatCtor(p.name, emptyList(), ctor.entry.ref(), nid, p.pos, notesOf(p))
            }
            val symbol = declareLocal(p.name, SymbolKind.LOCAL, p.pos)
            HirPatBind(p.name, symbol, nid, p.pos, notesOf(p))
        }
        is sublimation.frontend.ast.PatElse -> sublimation.frontend.hir.HirPatElse(freshNid(), p.pos, notesOf(p))
        is sublimation.frontend.ast.PatCond -> sublimation.frontend.hir.HirPatCond(freshNid(), p.pos, notesOf(p))
            is ErrorPattern -> sublimation.frontend.hir.HirErrorPattern(p.message, freshNid(), p.pos, notesOf(p))
        }
    }

    /**
     * 裸名模式裁定（消解闭名单 4 的核心；解析层以空参 PatCtor/PatBind 两种形态送入，统一处理）：
     * - SLOT 位（ctor/tuple 实参位）：外层已有同名 → 相等比较拷贝（D-PAT-SLOT）；否则新名绑定入帧；
     * - TOP 位（臂顶层）：构造子表命中且零字段 → 构造子模式（D-PAT-CTOR）；命中带载荷 → E-CTOR-ARITY；
     *   未命中 → 名字绑定。
     */
    private fun bareNamePattern(
        nid: NodeId, name: String, pos: SourceRange, mode: PatternMode, notes: List<HirNote>,
        slotT: sublimation.frontend.hir.HirType? = null, parenWritten: Boolean = false,
    ): sublimation.frontend.hir.HirPattern {
        if (mode == PatternMode.SLOT) {
            if (name != "_" && symbols.lookupValue(name) != null) {
                val symbol = symbols.lookupValue(name)!!.ref()
                val note = HirNote(HirNoteKind.DESUGAR, "D-PAT-SLOT",
                    "解构槽位重名按相等比较处理（要绑定请用新名字）", pos, "slot " + name, "cmp " + name)
                val value = HirNameRef(name, symbol, null, freshNid(), pos, emptyList())
                return sublimation.frontend.hir.HirPatLit(value, nid, pos, notes + note)
            }
            val slotSym = declareLocal(name, SymbolKind.LOCAL, pos, slotT)
            injectTypeFacts(name, slotT ?: infer.syntheticT("模式槽位" + name), pos)   // t65
            return sublimation.frontend.hir.HirPatBind(name, slotSym, nid, pos, notes)
        }
        // t22（B3）：臂顶层 true/false 为 Bool 值模式（与 [pattern] 的 PatBind 分支同口径）。
        // t84（Q12 收口）：小写见上——resolveValue 落 E-UNBOUND-NAME + 大写提示（同口径）。
        if (name == "true" || name == "false" || name == "True" || name == "False") {
            val symbol = resolver.resolveValue(name, pos)
            val note = HirNote(HirNoteKind.DESUGAR, "D-PAT-BOOL",
                "Bool 值模式（true/false 按字面量匹配，不作绑定名）", pos, name, name)
            val lit = HirNameRef(name, symbol, infer.namedT("Bool"), freshNid(), pos, emptyList())
            return sublimation.frontend.hir.HirPatLit(lit, nid, pos, notes + note)
        }
        val ctor = symbols.lookupCtor(name)
        if (ctor != null) {
            if (ctor.nullary) {
                // t41（ADR-28 R-19）：括号显式书写的空构造子（解析层 paren 印记）——E-NULLARY-PAREN
                if (parenWritten && structDecls[ctor.enumName]?.fields?.isEmpty() == true)
                    diag.error("E-NULLARY-PAREN", pos,
                        "空构造子 " + ident(name) + " 不能用 () 构造，请直接写 " + ident(name) + "（空构造子全局唯一）")
                val note = HirNote(HirNoteKind.DESUGAR, "D-PAT-CTOR",
                    "裸名构造子归一为构造子模式", pos, name, name + "()")
                return sublimation.frontend.hir.HirPatCtor(name, emptyList(), ctor.entry.ref(), nid, pos, notes + note)
            }
            // 新码：载荷构造子裸名分臂必须解构（旧实现由 ctor 裁定隐含）
            diag.error("E-CTOR-ARITY", pos, "构造子 " + ident(name) + " 带载荷，分臂必须解构：" + ident(name) + "(x)")
            return sublimation.frontend.hir.HirPatCtor(name, emptyList(), ctor.entry.ref(), nid, pos, notes)
        }
        return sublimation.frontend.hir.HirPatBind(name, declareLocal(name, SymbolKind.LOCAL, pos), nid, pos, notes)
    }

    /** t33：按 when 主题类型构造 ctor 字段类型的实例化替换表（enum E[T] + 主题 E[arg] → T := arg）。 */
    private fun ctorSubst(subjectT: sublimation.frontend.hir.HirType?, ctorName: String): Map<String, sublimation.frontend.hir.HirType> {
        // 用户枚举已废除（P-9，t40）：替换表来源切除；prelude/结构体的字段实例化归 t41
        return emptyMap()
    }

    /** t33：类型变量按名替换（零实参具名位；其余结构递归重建）。 */
    private fun subTpNames(t: sublimation.frontend.hir.HirType, subst: Map<String, sublimation.frontend.hir.HirType>): sublimation.frontend.hir.HirType {
        if (subst.isEmpty()) return t
        return when (t) {
            is HirNamedType ->
                if (t.args.isEmpty()) subst[t.name] ?: t
                else HirNamedType(t.name, t.args.map { subTpNames(it, subst) }, t.symbol, freshNid(), t.origin, t.notes)
            is sublimation.frontend.hir.HirFunType -> sublimation.frontend.hir.HirFunType(
                t.params.map { subTpNames(it, subst) }, subTpNames(t.ret, subst), t.purity, t.theory, t.context,
                freshNid(), t.origin, t.notes)
            is sublimation.frontend.hir.HirTupleType ->
                sublimation.frontend.hir.HirTupleType(t.items.map { subTpNames(it, subst) }, freshNid(), t.origin, t.notes)
            is HirMutType -> HirMutType(subTpNames(t.inner, subst), freshNid(), t.origin, t.notes)
            is sublimation.frontend.hir.HirAtomicType ->
                sublimation.frontend.hir.HirAtomicType(subTpNames(t.inner, subst), freshNid(), t.origin, t.notes)
            is sublimation.frontend.hir.HirRefType ->
                sublimation.frontend.hir.HirRefType(subTpNames(t.inner, subst), freshNid(), t.origin, t.notes)
            // t41：联合成员里的理论参数同代入（Some[T] → Some[Nat]）
            is HirUnionType ->
                HirUnionType(t.variants.map { subTpNames(it, subst) }, freshNid(), t.origin, t.notes)
            else -> t
        }
    }

    /** 程序位出现命题专属语法（$/⊤/⊥/量词）：程序面与命题面分家的哨兵（ADR-1 §3.2），半成品落树。
     * t79（Q20）：[hint] 为该形态的可编译替代写法（接在消息后，以 —— 分隔；遵守 ADR-48 §八）。 */
    /** t92（裁决 4 表达式层闭合）：值位原生命题连接词的拒绝文案——按算符给可编译提示（ADR-48 §八）。 */
    private fun valuePropHint(nm: String): String = when (nm) {
        "∧" -> ident(nm) + " 是命题层连接词（命题专用）——表达式层的布尔与写作 " + ident("&") + "（" + ident("a & b") + " 可直接编译通过）"
        "∨" -> ident(nm) + " 是命题层连接词（命题专用）——表达式层的布尔或写作 " + ident("|") + "（" + ident("a | b") + " 可直接编译通过）"
        else -> ident(nm) + " 是命题层连接词（命题专用）——表达式层没有等价写法"
    }
    /** R-07（裁决记录 §R-07）:类型含 @mut 即可变——@ref 只是视图（其背后可经别名变，R-07c 同计）,递归穿透判 inner。 */
    private fun containsMutT(t: sublimation.frontend.hir.HirType?): Boolean = when (t) {
        is sublimation.frontend.hir.HirMutType -> true
        is sublimation.frontend.hir.HirRefType -> containsMutT(t.inner)
        is sublimation.frontend.hir.HirAtomicType -> containsMutT(t.inner)
        is sublimation.frontend.hir.HirTupleType -> t.items.any { containsMutT(it) }
        is sublimation.frontend.hir.HirUnionType -> t.variants.any { containsMutT(it) }
        is sublimation.frontend.hir.HirQualifiedType -> t.args.any { containsMutT(it) }
        is sublimation.frontend.hir.HirFunType -> containsMutT(t.ret) || t.params.any { containsMutT(it) }
        else -> false
    }

    /**
     * R-07 捕获分析:函数体内对**自由变量**（非本函数绑定:外层局部 / 形参 / 全局变量;函数与构造子名非
     * 捕获口径）的访问（读或写——R-07b 仅读也算依赖;写本就是变化效应）,其类型含 @mut ⇒ 该函数不纯。
     * [bound] 为本函数已绑定符号 id 集合（形参 + 词法序累计的局部声明,扫描内即时累计）;嵌套 lambda /
     * 匿名 fun / 局部 fun 的捕获归其自身作用域,本级扫描不下降（创建闭包≠其捕获的效应）。[report]
     * 控制定位诊断落点——仅纯链内报告（不纯上下文内无需管）。
     */
    // ---------- t122（R-16 A2 · I-1 收口第二期）：判据状态机 + 主趟事件回放 + 不纯闭包 ----------
    // t96 纯度闸 / t107 R-07 捕获 / t94 体效应共用同一条帧链与事件流——构造位发事件，runPurityGate
    // 阶段 2 回放（消 gateWalkExpr/gateWalkStmt/gateWalkFun/mutCaptureScan/mutCaptureStmtScan/gateMutCapture
    // 六族平行遍历）。行为零变口径：闸诊断（E-IMPURE-CALL/E-MUT-CAPTURE）判据逐字保持——caller 豁免仍读
    // 声明口径（注解/t94/捕获/unchecked/脚本顶层），闭包只富化**被调侧**分类与产物面 purity 回写。
    // 事件序=构造序=旧 walker 树序：子帧引用入父帧 ordered，实参/默认填补以调用位归并批次（两遍实参
    // 重排不可见）。

    /** 闸帧种类：函数体 / lambda 体 / 匿名 fun 体 / unchecked 降闸 / 文件根（脚本=隐式不纯入口）。 */
    private enum class GateFrameKind { FUN, LAMBDA, ANONFUN, UNCHECKED, ROOT }

    /** 局部 fun 登记位（词法序）：注解先填，捕获/闭包推导阶段 2 补——调用方经帧链查此位分类（遮蔽同名全局）。 */
    private class GateSlot(var impure: Boolean, val key: String)

    /** 调用位记录（主趟 onCall 发射，阶段 2 回放闸判据 + 闭包边/直接种子收集）。 */
    private class GateCall(val node: HirCall, val calleeName: String?, val frame: GateFrame)

    /** R-07 捕获记录（主趟 NameRef 构造位发射——自由变量 + 类型含 @mut 才记）。 */
    private class GateCapture(val origin: SourceRange, val frame: GateFrame)

    /** 闭包节点（单调不动点，提案 §1.2/R-16 A2）：V = 全局具名函数（g:名）/ 局部 fun（s:序）。 */
    private class GateNode(val key: String, val name: String, val annotated: Boolean) {
        var impure = false; var seedClass: String? = null; var path: String? = null
    }

    /**
     * 闸帧：判据状态机节点（与符号帧同生命周期进出）。[impureByDecl] 声明口径（FUN=注解 /
     * LAMBDA/ANONFUN=t94 出栈结论 / UNCHECKED/ROOT=降闸位）——**不含闭包推导**：阶段 B 闸判据维持
     * 声明口径（T96「impure 调用方放行」注解位语义零变）。[ordered] 本帧直系事件序（GateCapture /
     * GateCall / 子帧引用）——回放序=旧 walker 树序。
     */
    private class GateFrame(
        val parent: GateFrame?,
        val kind: GateFrameKind,
        val name: String?,
        var impureByDecl: Boolean,
        val borrowed: Set<String>,
        val facts: Set<String>,
        val closureKey: String?,
        val slot: GateSlot?,
        val locals: MutableMap<String, GateSlot> = mutableMapOf(),
        val bound: MutableSet<Int> = mutableSetOf(),
        val ordered: MutableList<Any> = mutableListOf(),
        var hasCapture: Boolean = false,
        var bodyEffect: Boolean = false,
        var varDirect: Boolean = false,
    ) {
        /** 调用方豁免链（含自身——同旧 g.stack.any{it.impure}）：任一帧声明口径不纯即降闸。 */
        fun chainImpure(): Boolean {
            var f: GateFrame? = this
            while (f != null) { if (f.impureByDecl || f.hasCapture) return true; f = f.parent }
            return false
        }
        /** 祖先链（不含自身——旧 !g.stack.any{it.impure} 于捕获报告口径，报告判据自身另判）。 */
        fun ancestorsImpure(): Boolean = parent?.chainImpure() ?: false
        /** 借用名（@impure 函数类型形参名）沿链并集（同旧 g.stack.any{nm in it.borrowed}）。 */
        fun borrowedUp(): Set<String> {
            val s = mutableSetOf<String>(); var f: GateFrame? = this
            while (f != null) { s.addAll(f.borrowed); f = f.parent }
            return s
        }
        /** 纯事实（preps pure<f> 原子）沿链并集（同旧 g.stack.any{nm in it.facts}）。 */
        fun factsUp(): Set<String> {
            val s = mutableSetOf<String>(); var f: GateFrame? = this
            while (f != null) { s.addAll(f.facts); f = f.parent }
            return s
        }
        /** R-07 绑定集所在帧：最近的 FUN/LAMBDA/ANONFUN（unchecked 借外层集——旧 mutCaptureStmtScan 口径）。 */
        fun boundFrame(): GateFrame? {
            var f: GateFrame? = this
            while (f != null && f.kind != GateFrameKind.FUN && f.kind != GateFrameKind.LAMBDA &&
                f.kind != GateFrameKind.ANONFUN) f = f.parent
            return f
        }
        /** 闭包边的调用方归属：最近的 FUN 帧；先遇 LAMBDA/ANONFUN 则该调用属闭包自身（不归函数，同 t94 口径）。 */
        fun funNodeFrame(): GateFrame? {
            var f: GateFrame? = this
            while (f != null) {
                if (f.kind == GateFrameKind.FUN) return f
                if (f.kind == GateFrameKind.LAMBDA || f.kind == GateFrameKind.ANONFUN) return null
                f = f.parent
            }
            return null
        }
        /** 局部 fun 名按词法序查最近外层声明（gateLocalOf 口径）。 */
        fun lookupLocal(nm: String): GateSlot? {
            var f: GateFrame? = this
            while (f != null) { val s = f.locals[nm]; if (s != null) return s; f = f.parent }
            return null
        }
    }

    /** 闸帧栈顶（内层帧）；ROOT 帧在 file() 起始创建。 */
    private var gateTop: GateFrame? = null
    private var gateSlotSeq = 0
    /** 实参批次栈（两遍实参/默认填补以调用位归并——回放序=旧 walker「先诊断后下降实参」树序）。 */
    private val gateBatches = ArrayDeque<MutableList<Any>>()
    /** 闭包规模（R 轮观测单列 O(v+E)：节点数 + 边数）。 */
    internal var gateClosureSize = 0
        private set

    /** t123（R-16 S4 上文上提）：未豁免 impure 调用边——闭包边集的**同源视图**（collectCalls 记录，gateLift 消费，零新增遍历）。 */
    private class GateLiftEdge(val caller: String, val target: String?, val name: String, val origin: SourceRange)
    private var gateLiftEdges: List<GateLiftEdge> = emptyList()
    /** 节点供给侧写（节点键 → 帧 factsUp / borrowedUp / 声明口径吸收位）——gateLift 的「证出 pure<g>」判据。 */
    private var gateNodeFacts: Map<String, Set<String>> = emptyMap()
    private var gateNodeBorrowed: Map<String, Set<String>> = emptyMap()
    private var gateNodeAbsorbed: Map<String, Boolean> = emptyMap()

    private fun gateEmit(item: Any) {
        val b = gateBatches.lastOrNull()
        if (b != null) b.add(item) else gateTop?.ordered?.add(item)
    }

    // ---- 事件订阅（主趟构造位调用——零二次遍历）----

    /** 文件根帧（file() 起始）：脚本模式=隐式不纯入口（P-11 §4/R-16 OQ-2「挂 unit 级」）；工程模式=空栈口径。 */
    private fun gateFileStart() {
        gateTop = GateFrame(null, GateFrameKind.ROOT, null, scriptMode, emptySet(), emptySet(), null, null)
        gateBatches.clear()
        gateSlotSeq = 0
    }

    /** 进实参批次（调用位实参消解前——callee 与 args 同入一批）。 */
    private fun gateBatchOpen() { gateBatches.addLast(mutableListOf()) }

    /** 调用位记录归并：调用记录先行（同旧 walker 先 gateCheckCall 后下降），批次事件随后。 */
    private fun gateBatchCloseWithCall(call: GateCall) {
        if (gateBatches.isNotEmpty()) {
            val b = gateBatches.removeLast()
            gateEmit(call)
            b.forEach { gateEmit(it) }
        } else gateEmit(call)
    }

    /** onCall 事件（主趟调用构造位发射——三调用形态共用）：t97 停机闸 + t94 效应累加 + t122 闸记录/闭包收集。 */
    private fun onCall(node: HirCall, calleeName: String?) {
        checkTermination(calleeName, node.args, node.origin)      // t97 订阅（既有判据逐字不动）
        if (impureCallee(node.callee)) effectAcc = true           // t94 订阅：被执行的不纯调用记账（效应存在口径）
        val f = gateTop ?: return
        val callee = node.callee
        // S4b 种子候选：函数值位（类型位 @impure）的直接调用——非借用名即执行了不纯能力。
        if (callee is HirNameRef && calleeName != null && calleeName !in f.borrowedUp() &&
            (callee.type as? HirFunType)?.purity == HirPurity.IMPURE)
            f.funNodeFrame()?.let { it.varDirect = true }
        gateBatchCloseWithCall(GateCall(node, calleeName, f))
    }

    /** 主趟 NameRef 构造位：R-07 捕获候选（自由变量 + 类型含 @mut 才记——同旧 gateMutCapture 判据）。 */
    private fun gateRecordNameRef(h: HirNameRef) {
        val f = gateTop?.boundFrame() ?: return      // 文件根/顶层不参与（旧 walker 不扫顶层）
        val sym = h.symbol ?: return
        if (sym.id in f.bound) return
        val variable = when (sym.kind) {
            SymbolKind.LOCAL, SymbolKind.PARAM, SymbolKind.FIELD, SymbolKind.TRAIT_CONST -> true
            else -> false
        }
        if (!variable || !containsMutT(h.type)) return
        f.hasCapture = true
        gateEmit(GateCapture(h.origin, f))
    }

    /** 主趟 VarExpr 构造位：R-07 绑定集词法序累计（形参在帧进栈时装）。 */
    private fun gateRecordVarBound(sym: SymbolRef?) {
        val id = sym?.id ?: return
        gateTop?.boundFrame()?.bound?.add(id)
    }

    /** FUN 帧进栈（funDecl 体构造前——参数/preps 已就绪）。global=文件级声明（闭包节点 g:名，同 Γ 注册口径）。 */
    private fun gateFunEnter(name: String, annotations: List<String>, params: List<sublimation.frontend.hir.HirParam>,
                             preps: List<sublimation.frontend.hir.HirProp>, global: Boolean, slotKey: String?): GateFrame {
        val parent = gateTop!!
        val borrowed = params.mapNotNull { p ->
            if ((p.type as? HirFunType)?.purity == HirPurity.IMPURE) p.name else null
        }.toSet()
        val key = if (global) "g:" + name else slotKey
        val slot = if (global) null else slotKey?.let { GateSlot(gatePurityOf(name, annotations), it) }
        val frame = GateFrame(parent, GateFrameKind.FUN, name, gatePurityOf(name, annotations), borrowed,
            preps.mapNotNull { pureAtomName(it) }.toSet(), key, slot)
        frame.bound.addAll(params.mapNotNull { it.symbol?.id })
        gateEmit(frame)
        gateTop = frame
        return frame
    }

    /** FUN 帧出栈：A1 体效应暂存结论转入闭包种子（R-16 A2 消费位——未归因调用边时兜底种子）。 */
    private fun gateFunExit(frame: GateFrame, bodyEffect: Boolean) {
        frame.bodyEffect = bodyEffect
        gateTop = frame.parent
    }

    /** 局部 fun 登记（FunStmt 位——先于其体构造：递归调用可见）：返回闭包键（并入帧登记位）。 */
    private fun gateLocalRegister(name: String, annotations: List<String>): String {
        val key = "s:" + gateSlotSeq++
        gateTop?.locals?.put(name, GateSlot(gatePurityOf(name, annotations), key))
        return key
    }

    /** LAMBDA/ANONFUN 帧进栈（体构造前——形参符号已就绪）。 */
    private fun gateClosureEnter(kind: GateFrameKind, params: List<sublimation.frontend.hir.HirParam>): GateFrame {
        val parent = gateTop!!
        val frame = GateFrame(parent, kind, null, false, emptySet(), emptySet(), null, null)
        frame.bound.addAll(params.mapNotNull { it.symbol?.id })
        gateEmit(frame)
        gateTop = frame
        return frame
    }

    /** LAMBDA/ANONFUN 帧出栈：t94 纯度结论回填声明口径（回放判据 = e.purity||captured，同旧）。 */
    private fun gateClosureExit(frame: GateFrame, t94Impure: Boolean) {
        frame.impureByDecl = t94Impure
        gateTop = frame.parent
    }

    /** UNCHECKED 帧进出（降闸=三路放行之一）。 */
    private fun gateUncheckedEnter(): GateFrame {
        val f = GateFrame(gateTop!!, GateFrameKind.UNCHECKED, null, true, emptySet(), emptySet(), null, null)
        gateEmit(f); gateTop = f; return f
    }
    private fun gateUncheckedExit(frame: GateFrame) { gateTop = frame.parent }

    // ---- 阶段 2：闭包单调不动点 + 事件回放 ----

    /** 阶段 2 回放上下文：被调侧富化表（Γ 注册口径：全局具名函数）+ axiom 纯事实池。 */
    private class GateReplayCtx(val enriched: Map<String, Boolean>, val axiomPure: Set<String>) {
        fun proven(nm: String, facts: Set<String>): Boolean =
            nm in axiomPure || enriched[nm] == false || nm in facts
    }

    /** 声明口径纯度（@root 名按内建纯度表判——保守口径，同旧）：声明位两形态同判。 */
    private fun gatePurityOf(name: String, annotations: List<String>): Boolean =
        if ("root" in annotations) name in infer.builtinImpureNames else "impure" in annotations
    private fun gatePurityOf(d: HirFunDecl): Boolean = gatePurityOf(d.name, d.annotations)

    internal fun runPurityGate(hir: sublimation.frontend.hir.HirFile) {
        val root = gateTop ?: return
        // 1) Γ 注册（全局具名函数——注解口径，同旧）+ 声明节点表（回写/审计用）
        val enriched = mutableMapOf<String, Boolean>()
        val globalDecls = mutableMapOf<String, HirFunDecl>()
        hir.entries.forEach { ent ->
            val d = (ent as? sublimation.frontend.hir.HirDeclEntry)?.decl as? HirFunDecl ?: return@forEach
            enriched[d.name] = gatePurityOf(d)
            globalDecls[d.name] = d
        }
        // 2) axiom pure<f> 全局事实池（axiomFacts 收集全部 axiom 命题——逐名提取 pure 原子）
        val axiomPure = mutableSetOf<String>()
        axiomFacts.forEach { p -> pureAtomName(p)?.let { axiomPure.add(it) } }
        // 3) 不纯闭包单调不动点（R-16 A2）：种子六类 + 调用边 → 富化表/回写/审计（详见 gateClosure）
        val nodes = gateClosure(root, enriched, axiomPure)
        // 4) 回写（注解优先、推导补位）：HirFunDecl.purity + D-PURITY-INFERRED 审计四元组
        nodes.forEach { (key, n) ->
            if (!key.startsWith("g:")) return@forEach
            val name = key.removePrefix("g:")
            val d = globalDecls[name] ?: return@forEach
            if (n.impure && d.purity != HirPurity.IMPURE) {
                d.purity = HirPurity.IMPURE
                diag.supplement("D-PURITY-INFERRED", d.origin,
                    "函数 " + ident(name) + " 推导为 impure（R-16 A2 自动 @impure;注解优先——可显式标 @impure 定型）。" +
                        "种子源：" + (n.seedClass ?: "-") + "；传播路径：" +
                        (if (n.path != null) name + " ← " + n.path else name) +
                        "（闭包单调不动点：f 调 g 且 g 不纯 ⇒ f 不纯）")
            }
        }
        // 5) 事件回放（帧序=旧 walker 树序）：E-MUT-CAPTURE / E-IMPURE-CALL（判据逐字同旧）+ calleeImpure 真化
        val ctx = GateReplayCtx(enriched, axiomPure)
        gateReplay(root, ctx)
        // 6) 脚本顶层隐式不纯载体（OQ-2 裁：挂 unit 级 + 诊断可见——含顶层语句的脚本文件一条）
        if (scriptMode) gateScriptImplicit(hir)
        // 7) 上文上提（R-16 S4 第三机理，脚本模式）：失败义务签名位合成——共用闭包边集（零新增遍历）
        if (scriptMode) gateLift(nodes, globalDecls, axiomPure)
        gateClosureSize = nodes.size + closureEdges
    }

    /** 闭包边表（runPurityGate 观测用）。 */
    private var closureEdges = 0

    /**
     * 不纯闭包（提案 §1.2）：V=fun 帧（g:名 全局 / s:序 局部）+ E=调用位记录边。种子六类：注解 /
     * 内建不纯被调 / R-07 捕获 / impure 闭包直调 / 函数值位直调 / 退化；体效应（A1 结论）为未归因兜底。
     * 单调工作表源序 O(v+E)——SCC 特例无需 Tarjan（传播只单向）。带判据的边/种子走 gateCheckCall 同款
     * 三路放行（借用形参/Γ⊢pure<f>），纯口径与闸判据一致；直接闭包/体效应种子无豁免（同 t94 效应口径）。
     */
    private fun gateClosure(root: GateFrame, enriched: MutableMap<String, Boolean>, axiomPure: Set<String>): MutableMap<String, GateNode> {
        val nodes = mutableMapOf<String, GateNode>()
        val frameOrder = mutableListOf<GateFrame>()
        fun collect(f: GateFrame) {
            f.ordered.forEach { item -> if (item is GateFrame) {
                if (item.closureKey != null) {
                    nodes[item.closureKey!!] = GateNode(item.closureKey!!, item.name!!,
                        item.impureByDecl || (item.closureKey!!.startsWith("g:") && item.name!! in degradedFuns))
                    frameOrder.add(item)
                }
                collect(item)
            } }
        }
        collect(root)
        // 注解种子回填 seedClass + 富化表初值
        frameOrder.forEach { f ->
            val n = nodes[f.closureKey!!]!!
            if (n.annotated) {
                n.impure = true
                n.seedClass = if (f.closureKey!!.startsWith("g:") && f.name!! in degradedFuns) "退化（R-02 不收敛）" else "注解/内建位"
            }
            if (f.hasCapture) { n.impure = true; n.seedClass = n.seedClass ?: "R-07 捕获外层可变变量" }
        }
        val ctx = GateReplayCtx(enriched, axiomPure)
        val outEdges = mutableMapOf<String, MutableList<Pair<String, SourceRange>>>()
        val liftEdges = mutableListOf<GateLiftEdge>()   // t123：gateLift 的边集（同闭包边——零新增遍历）
        val nodeFacts = mutableMapOf<String, Set<String>>()
        val nodeBorrowed = mutableMapOf<String, Set<String>>()
        val nodeAbsorbed = mutableMapOf<String, Boolean>()
        // 供给侧写（节点帧 factsUp/borrowedUp/声明天吸收位——gateLift 判据用）
        frameOrder.forEach { f ->
            val k = f.closureKey!!
            nodeFacts[k] = f.factsUp()
            nodeBorrowed[k] = f.borrowedUp()
            nodeAbsorbed[k] = f.impureByDecl || f.hasCapture   // 声明口径吸收（注解/退化/R-07 捕获=终态）
        }
        // 调用位记录遍：边收集（三路放行同闸判据）+ 直接种子（内建/闭包直调/函数值位）
        fun collectCalls(f: GateFrame) {
            f.ordered.forEach { item -> when (item) {
                is GateCall -> {
                    val n = item.node
                    val nm = item.calleeName
                    val caller = item.frame.funNodeFrame()?.closureKey
                    val facts = item.frame.factsUp()
                    // 豁免仅认声明确证（借用形参/axiom 纯事实/上文 pure<f>）——enriched 声明态「未注解=纯」
                    // 不得在闭包相位前置截断传播边（r 调未注解 q：q 的推导不纯须经边传播给 r）。
                    val exempt = nm != null && (nm in item.frame.borrowedUp() || nm in axiomPure || nm in facts)
                    if (!exempt && caller != null && nm != null) {
                        val slot = item.frame.lookupLocal(nm)
                        val target = slot?.key ?: ("g:" + nm).takeIf { it in nodes }
                        if (target != null) {
                            outEdges.getOrPut(caller) { mutableListOf() }.add(target to n.origin)
                            liftEdges.add(GateLiftEdge(caller, target, nm, n.origin))   // t123：闭包边集同源视图
                        } else if (n.symbol?.kind == SymbolKind.BUILTIN && nm in infer.builtinImpureNames) {
                            val cn = nodes[caller]!!
                            cn.impure = true; cn.seedClass = cn.seedClass ?: "内建不纯 " + ident(nm)
                            cn.path = ident(nm) + "@" + n.origin.toString()
                            liftEdges.add(GateLiftEdge(caller, null, nm, n.origin))      // t123：内建不纯欠条（无名位目标）
                        }
                    }
                    if (!exempt) {
                        val callee = n.callee
                        if ((callee is HirLambda && callee.purity == HirPurity.IMPURE) ||
                            (callee is sublimation.frontend.hir.HirAnonFun && callee.purity == HirPurity.IMPURE)) {
                            if (caller != null) {
                                val cn = nodes[caller]!!
                                cn.impure = true; cn.seedClass = cn.seedClass ?: "impure 闭包直调"
                                cn.path = "闭包直调@" + n.origin.toString()
                            }
                        }
                    }
                }
                is GateFrame -> collectCalls(item)
                else -> {}
            } }
        }
        collectCalls(root)
        // 函数值位/体效应兜底种子
        frameOrder.forEach { f ->
            val n = nodes[f.closureKey!!]!!
            if (f.varDirect) { n.impure = true; n.seedClass = n.seedClass ?: "impure 函数值位直调（类型位 @impure）" }
            if (f.bodyEffect && !n.impure) { n.impure = true; n.seedClass = "体效应（t94 结论：直接执行不纯调用）" }
        }
        // 单调工作表（源序迭代至不动点——O(v+E)）
        var changed = true
        while (changed) {
            changed = false
            outEdges.forEach { (from, edges) ->
                val fn = nodes[from] ?: return@forEach
                if (!fn.impure) {
                    val hit = edges.firstOrNull { (to, _) -> nodes[to]?.impure == true }
                    if (hit != null) {
                        val gn = nodes[hit.first]!!
                        fn.impure = true; changed = true
                        fn.seedClass = fn.seedClass ?: "传播（f 调 g 且 g 不纯 ⇒ f 不纯）"
                        fn.path = gn.name + "@" + hit.second.toString() + " ← " + (gn.path ?: gn.name)
                    }
                }
            }
        }
        // 富化表 + 局部登记位消费同一结论（调用方分类同表同位，gateClassify 单源）
        nodes.forEach { (key, n) ->
            if (key.startsWith("g:")) enriched[key.removePrefix("g:")] = n.impure
        }
        frameOrder.forEach { f -> f.slot?.let { it.impure = it.impure || (nodes[f.closureKey!!]?.impure == true) } }
        closureEdges = outEdges.values.sumOf { it.size }
        gateLiftEdges = liftEdges
        gateNodeFacts = nodeFacts
        gateNodeBorrowed = nodeBorrowed
        gateNodeAbsorbed = nodeAbsorbed
        return nodes
    }

    /** 事件回放（帧序=旧 walker 树序）：E-MUT-CAPTURE（捕获报告）+ E-IMPURE-CALL（调用闸）+ calleeImpure 真化（OQ-7=T）。 */
    private fun gateReplay(f: GateFrame, ctx: GateReplayCtx) {
        f.ordered.forEach { item ->
            when (item) {
                is GateCapture -> {
                    val owner = item.frame
                    if (!owner.impureByDecl && !owner.ancestorsImpure())   // 纯链 + 声明纯才报告（同旧）
                        diag.error("E-MUT-CAPTURE", item.origin,
                            "纯函数访问了捕获的外层可变变量——捕获的可变状态在调用间可变,同输入可异输出（R-07）。" +
                                "三路出路任选其一:把该依赖量改作形参显式传入（fun f(x: T)）;或先取不变快照（val s = 该变量,函数内只用 s）;" +
                                "或给该函数标 @impure 放弃纯度检查")
                }
                is GateCall -> {
                    val node = item.node
                    val kind = gateClassify(node.callee, node.symbol, item.frame, ctx)
                    if (kind.impure) node.calleeImpure = true   // OQ-7（队长裁 T）：calleeImpure 真化——被调侧结论入产物
                    if (!item.frame.chainImpure()) {   // 三路放行之「调用方非纯」
                        val nm = calleeNameOf(node.callee)
                        val exempt = nm != null && (nm in item.frame.borrowedUp() || ctx.proven(nm, item.frame.factsUp()))
                        if (!exempt && kind.impure)
                            gateDiag(node.origin, "纯作用域内调用了非纯的 " + kind.label + "。" +
                                "三路放行任选其一：给调用方函数标 @impure（效应沿调用链向上传播）、" +
                                "unchecked 前缀包住该语句（单语句豁免）、或经上文 <pure<名>> / axiom pure<名> 供给 pure 命题")
                    }
                }
                is GateFrame -> gateReplay(item, ctx)
                else -> {}
            }
        }
    }

    /** 被调者分类（impure 判定 + 诊断 label 单源派生，t96 阶段 B;R-16 A2 富化被调侧 + 局部登记位消费）。 */
    private fun gateClassify(callee: HirExpr, symbol: SymbolRef?, frame: GateFrame, ctx: GateReplayCtx): CalleeKind {
        if (callee is HirNameRef && callee.symbol?.kind != SymbolKind.BUILTIN) {
            val slot = frame.lookupLocal(callee.name)
            if (slot != null) return CalleeKind(slot.impure, (if (slot.impure) "@impure 函数 " else "函数 ") + ident(callee.name))
        }
        return when (callee) {
            is HirNameRef -> {
                val fn = callee.name
                val funTypeImpure = (callee.type as? HirFunType)?.purity == HirPurity.IMPURE   // 函数值闸
                val impure = when (symbol?.kind) {
                    SymbolKind.BUILTIN -> fn in infer.builtinImpureNames
                    SymbolKind.FUN, SymbolKind.THEOREM -> ctx.enriched[fn] == true
                    else -> funTypeImpure
                }
                val label = when {
                    fn in infer.builtinImpureNames && !ctx.enriched.containsKey(fn) -> "内建不纯函数 " + ident(fn)
                    funTypeImpure -> "impure 函数值（类型位 @impure，函数值闸）"
                    else -> "@impure 函数 " + ident(fn)
                }
                CalleeKind(impure, label)
            }
            is HirLambda -> CalleeKind(callee.purity == HirPurity.IMPURE, "impure 闭包/匿名函数（t94 推导，直接调用）")
            is sublimation.frontend.hir.HirAnonFun -> CalleeKind(callee.purity == HirPurity.IMPURE, "impure 闭包/匿名函数（t94 推导，直接调用）")
            else -> CalleeKind(false, "")
        }
    }

    /** 局部 fun 名按词法序查最近外层声明（遮蔽同名全局）;无则 null（哨兵 API，保留调试面）。 */
    private fun gateLocalImpure(frame: GateFrame, nm: String): Boolean? = frame.lookupLocal(nm)?.impure

    /** 脚本顶层隐式不纯载体（OQ-2 裁：「挂 unit 级 + 诊断可见」——不造合成 main，D-12 真值单源教训）。 */
    private fun gateScriptImplicit(hir: sublimation.frontend.hir.HirFile) {
        val first = hir.entries.firstOrNull { it is sublimation.frontend.hir.HirStmtEntry }
            as? sublimation.frontend.hir.HirStmtEntry ?: return
        val count = hir.entries.count { it is sublimation.frontend.hir.HirStmtEntry }
        diag.supplement("D-SCRIPT-IMPLICIT-IMPURE", first.stmt.origin,
            "脚本顶层语句即隐式不纯入口（P-11 §4;R-16 OQ-2「挂 unit 级 + 诊断可见」）：本文件含 " +
                count + " 条顶层语句，整编译单元按 impure 记隐式入口（不造合成 main——D-12 真值单源）。" +
                "顶层调用不纯函数免闸（既有口径）;函数体内仍按声明口径受闸")
    }
    // ---------- t123（R-16 S4 第三机理 · 上文上提）：失败义务的签名位合成（脚本模式，OQ-4/OQ-5）----------
    // 触发 = 纯声明链 FUN 帧内调 impure callee 无三路供给（同 gateCheckCall 判据——边记录已过滤豁免）
    // ⇒ 该名上提为 f.preps 的 <pure<g>> 欠条（HirPAtom provenance=SYNTHETIC——分账同 t118/C-8:
    // 不入 axiomFacts 信任基;JSON 不发射 provenance，合成位随 preps 产物可见）。链式上提与 t122 闭包**共用
    // 同一调用边集**（零新增遍历）：欠条沿反向调用边转嫁给声明纯调用方（h 调 f 且 f 背 pure<g> 欠条 ⇒ h 同欠），
    // 吸收终态① 声明 impure 帧（注解/退化/R-07 捕获）/ ② 脚本顶层（root 隐式不纯入口吸收——OQ-2 unity）/
    // ③ 供给命中（f 的欠条不被 f 帧已证出的名表吸收——欠条是**未证**之账）。
    // **分型红线（R-16）**：仅纯度族（pure<f> 契约）可上提——契约天然可转嫁;停机族（E-NON-STRUCTURAL-REC）/类型
    // 确立族（E-INFER-NO-SOLUTION）**不可**——R-02 健全性优先,本处不开任何绕过口子。
    // **三级正交**：Γ 帧级事实（t108 factFrames）/签名级欠条（本件）/产物级元信息（t118 inferred/evidence）
    // 载体互不流动——合成 prep 不入帧栈、不入 trustedBase、不改 purity（purity 由 A2 闭包单口径回写）。
    // 局部 fun（s:序）欠条：无独立于包围函数的可调用签名面——欠条随反向边转嫁至包围 fun 的全局签名,
    // 不在局部 fun 产物位合成（边界落档:R3-S4 §2.2 消费链的最新全局签名面）。
    private fun gateLift(
        nodes: Map<String, GateNode>,
        globalDecls: Map<String, HirFunDecl>,
        axiomPure: Set<String>,
    ) {
        // (1) 种子：未豁免 impure 调用的契约名（目标节点 post-闭包不纯 / 内建）——源序首发记证据尝试清单
        val lacks = mutableMapOf<String, MutableSet<String>>()
        val tried = LinkedHashMap<String, String>()   // caller 键 → 首条「名@调用位」（审计四元组:证据尝试）
        gateLiftEdges.forEach { e ->
            val cn = nodes[e.caller] ?: return@forEach
            if (gateNodeAbsorbed[e.caller] == true) return@forEach      // 声明口径吸收终态:不记欠条
            // 声明口径真值才产直欠条（注解/退化/R-07 捕获 + 内建）——推导不纯（t122 闭包）的被调
            // 不令调用方欠 pure<f>:其契约是欠条本身，随链式转嫁（提案 §2.2 链式上提口径）
            val imp = e.target == null || gateNodeAbsorbed[e.target] == true
            if (!imp) return@forEach
            lacks.getOrPut(e.caller) { mutableSetOf() }.add(e.name)
            tried.putIfAbsent(e.caller, ident(e.name) + '@' + e.origin.toString())
        }
        // (2) 链式上提：反向调用边转嫁（单调加集至不动点——节点序=源序,O(v+e) 同闭包口径）
        val rev = mutableMapOf<String, MutableList<String>>()
        gateLiftEdges.forEach { e ->
            if (e.target != null) rev.getOrPut(e.target!!) { mutableListOf() }.add(e.caller)
        }
        var changed = true
        while (changed) {
            changed = false
            nodes.keys.forEach { f ->
                val L = lacks[f] ?: return@forEach
                rev[f]?.forEach { h ->
                    if (gateNodeAbsorbed[h] == true || nodes[h] == null) return@forEach   // 吸收终态/安全
                    val add = L.filter { g ->
                        g !in (gateNodeFacts[h] ?: emptySet()) && g !in axiomPure &&
                        g !in (gateNodeBorrowed[h] ?: emptySet())
                    }
                    if (add.isNotEmpty()) {
                        val s = lacks.getOrPut(h) { mutableSetOf() }
                        val sz = s.size; s.addAll(add)
                        if (s.size > sz) changed = true
                    }
                }
            }
        }
        // (3) 发射：全局 fun 欠条 → 合成 <pure<g>> prep + D-LIFTED-CONTEXT 审计四元组（+ noLift 门红）
        nodes.keys.forEach { key ->
            val L = lacks[key] ?: return@forEach
            if (!key.startsWith("g:") || L.isEmpty()) return@forEach
            val d = globalDecls[key.removePrefix("g:")] ?: return@forEach
            val have = d.preps.mapNotNull { pureAtomName(it) }.toSet()
            val fresh = (L - have).sorted()   // 源序排序——轮次确定性（同输入同结果,P-11 §9.3）
            if (fresh.isEmpty()) return@forEach
            val origin = d.origin
            d.preps = d.preps + fresh.map { g ->
                sublimation.frontend.hir.HirPAtom(
                    "pure",
                    listOf(sublimation.frontend.hir.HirTName(g, null, freshNid(), origin)),
                    freshNid(),
                    origin,
                    notes = listOf(sublimation.frontend.hir.HirNote(
                        sublimation.frontend.hir.HirNoteKind.SUPPLEMENT, "D-LIFTED-CONTEXT",
                        "R-16 上文上提合成欠条（证据尝试见 D-LIFTED-CONTEXT 诊断）", origin)),
                    provenance = sublimation.frontend.hir.HirAtomProvenance.SYNTHETIC,
                )
            }
            val triedNote = tried[key] ?: "-"
            diag.supplement("D-LIFTED-CONTEXT", origin,
                "函数 " + ident(d.name) + " 的签名合成 " + fresh.joinToString(" ") { "<pure<" + it + ">>" } +
                    " 契约（R-16 上文上提）：体内调用了 impure 被调但帧内三路供给（借用形参 / axiom pure<f> /" +
                    " 签名角组）皆无——契约上提为签名欠条（脚本模式记账;--no-lift 门可拒）。" +
                    "证据尝试：" + triedNote + "；失败原因分类：无帧内供给" +
                    "（分型表:纯度族可上提,停机/类型确立族不可——R-02 红线）")
            if (noLift)
                diag.error("E-LIFT-FORBIDDEN", origin,
                    "脚本模式发生 R-16 上文上提（函数 " + ident(d.name) + " 的 " +
                        fresh.joinToString(" ") { "<pure<" + it + ">>" } + " 契约），" +
                        "--no-lift 门要求上提即失败——给该函数标 @impure、删去 impure 调用," +
                        "或声明期供给 <pure<f>> 契约（双口形态同 --no-degrade）")
        }
    }

    // ---- t94 纯度推导：t121（R-16 A1）I-1 收口第一期——旧 containsImpureCall 族（expr/block
    //   /stmt 三函数平行遍历 41 行）已消：效应存在性经主趟 onCall 事件累加（enterEffectScope /
    //   exitEffectScope 出栈即结论）。impureCallee 单点判据保留（onCall 订阅 + t96 gateClassify 复用）。----

    /** 被调者的效应结论：内建不纯名（用户同名声明遮蔽时以声明注解为准）/ 用户 @impure 声明 /
     *  被直接调用的内层闭合（purity 经 elaboration 递归天然底向算好）/ funRef 携带的类型位。 */
    private fun impureCallee(c: HirExpr): Boolean = when (c) {
        is HirNameRef -> impureName(c.name)
        is HirField -> impureName(c.name)
        is HirFunRef -> (c.type as? sublimation.frontend.hir.HirFunType)?.purity == HirPurity.IMPURE
        is HirLambda -> c.purity == HirPurity.IMPURE
        is sublimation.frontend.hir.HirAnonFun -> c.purity == HirPurity.IMPURE
        else -> false
    }

    private fun impureName(name: String): Boolean =
        funDecls[name]?.annotations?.contains("impure") == true ||
            (funDecls[name] == null && name in infer.builtinImpureNames)
    // ---------- t96（N1 纯度闸 · 阶段 A 报告态，R2 §一 / wiki 04-效应）----------
    // 四步升级（qa t93 方案）:1 Γ 注册（未标 @impure 的具名函数注册 pure<f>，@root 名按内建纯度表判）;
    // 2 调用闸（纯作用域调非纯被调者且 Γ 无 pure<f> ⇒ 诊断）;3 上文转嫁（上文角组 <pure<f>> 与
    // axiom pure<f> 事实供给 Γ ⇒ 放行）;4 借用形参（@impure 函数类型位形参即显式效应能力，纯函数可调）。
    // 三路放行：调用方非纯 / unchecked / Γ⊢pure<f>。阶段 A（warn 报告态，已验毕收档）⇒ 阶段 B=error 硬拒（队长 2026-11-10 批）;
    // 阶段 B 已上线（error 硬拒）;E-IMPURE-CALL 闸码。

    /** t96 阶段 B：闸级别单点——error 硬拒（队长 2026-11-10 批）;阶段 A warn 已收档。 */
    private fun gateDiag(pos: SourceRange, message: String) = diag.error("E-IMPURE-CALL", pos, message)

    /** I-2（队长结构审查 t97 反馈）：被调者分类单源——impure 判定与文案 label 同源派生，不再两处各判。 */
    private data class CalleeKind(val impure: Boolean, val label: String)

    private fun calleeNameOf(callee: HirExpr?): String? = (callee as? HirNameRef)?.name

    /** t97:调用位的被调者名（NameRef / 实例化 T::f 的头 / 函数引用同名调用）。 */
    private fun callCalleeName(callee: HirExpr): String? = when (callee) {
        is HirNameRef -> callee.name
        is HirInst -> (callee.target as? HirNameRef)?.name
        is HirFunRef -> callee.name
        else -> null
    }


    /** pure<f> 命题原子取供给名：HirPAtom(head=pure, terms=[HirTName(f)])。 */
    private fun pureAtomName(p: sublimation.frontend.hir.HirProp): String? {
        val atom = p as? HirPAtom ?: return null
        if (atom.head != "pure" || atom.terms.size != 1) return null
        return (atom.terms[0] as? sublimation.frontend.hir.HirTName)?.name
    }

    // ---------- t97（N1 停机闸 · 阶段 A 报告态，R2 §一「停机闸」/ wiki 04「纯函数与停机」）----------
    // 纯函数的**直接自递归**须有实参在「变小」（旧 wiki 两形态）:
    // ① 子结构缩小——实参为形参的解构槽位（when 模式来源链）或字段链（递归到空结构停）;
    // ② 数值靠拢——实参为 `n - k` 形（向某数值靠拢，到达停）。
    // @impure 函数逃逸（不要求停机证明，验收 #3）;unchecked 语句整体放行（同 t96 通道）。
    // 码 **E-NON-STRUCTURAL-REC**——与 E-FUEL-EXHAUSTED「编译器自查燃料」无关，禁止复用其码或机制。
    // 阶段 B=error 硬拒（队长 2026-11-10 批）;阶段 A warn 报告态已验毕收档。方法糖接收者计入首实参位。


    // ---------- t110（OQ-5 最小结构归纳，队长裁进 V1）:t97 停机闸的 measure 递减证明通道 ----------

    /** 数值 measure 族（良基 <）:Nat/Int/Rat。 */
    private val MEASURE_NUMERIC = setOf("Nat", "Int", "Rat")

    /** measure 归约深度上界（深归约无终止依据不展开——同 P-12 §4.4 口径）。 */
    private val MEASURE_REDUCE_CAP = 4

    /**
     * t110 measure 通道:t97 闸的**结构归纳原理**最小形态。
     *
     * - **结构族**:数值阶层（Nat/Int/Rat,良基 <)。自递归调用的实参 e 对当前帧任一数值形参 p
     *   建确立型义务 `e < p`,经**既有证明通道族**（A-LINARITH 线性算术 / A-DEFRULE 定义性归约 /
     *   用户公理 MP 闭包）证出 ⇒ 停机（免 E-NON-STRUCTURAL-REC);
     * - **用户公理 fallback**（现役口径，范围 #3）:手工喂归纳原理的公理参与证明,留一法审计
     *   （trustedBase 同 [narrowMinus]——必要公理入信任基,--no-trust CI 口径同设）;
     * - **归纳原理不硬造**（范围 #4）:无数值 measure 位 / 证不出 ⇒ 回落既有闸（R-02 退化不纯口径不变,
     *   工程模式 E-NON-STRUCTURAL-REC 硬拒）——绝不假证;
     * - **置换原则**（R-12）:义务只经既有通道族证明,measure 通道不自带判据。
     *
     * @return (rule, attempted)——rule=证出规则名（null=证出失败）;attempted=是否尝试过 measure 义务。
     */
    private fun measureProofRule(calleeName: String, args: List<HirExpr>, frame: FunFrame): Pair<String?, Boolean>? {
        if (frame.impure || frame.name == null) return null
        val numericParams = frame.paramNames.filter { p ->
            (symbols.typeOfName(p) as? sublimation.frontend.hir.HirNamedType)?.name in MEASURE_NUMERIC
        }
        if (numericParams.isEmpty()) return null      // 无数值 measure 位:无结构依据,不走通道（不硬造）
        var attempted = false
        val facts = allFacts()
        for (a in args) {
            val raw = hirMeasureTerm(a) ?: continue
            val arg = measureReduce(raw, 0) ?: raw
            for (p in numericParams) {
                attempted = true
                // 双形态尝试:归约后项（A-DEFRULE 相助）优先;原始项留给用户公理 fallback(公理头随源形态）
                val forms = if (arg == raw) listOf(arg) else listOf(arg, raw)
                var hit: Pair<sublimation.frontend.hir.HirPAtom, String>? = null
                for (f in forms) {
                    val goal = sublimation.frontend.hir.HirPAtom("<",
                        listOf(f, sublimation.frontend.hir.HirTName(p, null, 0, SYNTHETIC_POS)),
                        0, SYNTHETIC_POS, provenance = sublimation.frontend.hir.HirAtomProvenance.SYNTHETIC)
                    val rule = prover.proves(facts, goal)
                    if (rule != null) { hit = goal to rule; break }
                }
                val (goal, rule) = hit ?: continue
                // 留一审计（用户公理 fallback 同 narrowMinus 口径）
                val essential = axiomFacts.filter { ax ->
                    facts.any { it == ax } && prover.proves(facts.filter { it != ax }, goal) == null
                }
                trustedBase.addAll(essential)
                measureProofs.add(MeasureProof(frame.name, prover.render(goal), rule,
                    if (essential.isEmpty()) "类型/分支事实"
                    else "必要公理:" + essential.joinToString(",") { prover.render(it) }))
                return rule to true     // 证出（规则, attempted=true)
            }
        }
        return null to attempted          // 未证出（rule=null, attempted=是否尝试过 measure）
    }

    /** HIR 表达式 → 命题项（measure 通道专用;零 nid 瞬态项,不耗全局计数器,不入 JSON）。 */
    private fun hirMeasureTerm(e: HirExpr): sublimation.frontend.hir.HirTerm? {
        return when (e) {
        is sublimation.frontend.hir.HirNameRef -> sublimation.frontend.hir.HirTName(e.name, null, 0, SYNTHETIC_POS)
        is sublimation.frontend.hir.HirIntLit -> sublimation.frontend.hir.HirTInt(e.value, 0, SYNTHETIC_POS)
        is sublimation.frontend.hir.HirBinExpr -> {
            val l = hirMeasureTerm(e.left) ?: return null
            val r = hirMeasureTerm(e.right) ?: return null
            sublimation.frontend.hir.HirTBin(e.op, l, r, 0, SYNTHETIC_POS)
        }
        is sublimation.frontend.hir.HirCall -> {
            val callee = hirMeasureTerm(e.callee) ?: return null
            val terms = e.args.map { hirMeasureTerm(it) }
            if (terms.any { it == null }) return null
            sublimation.frontend.hir.HirTCall(callee, terms.filterNotNull(), 0, SYNTHETIC_POS)
        }
        is sublimation.frontend.hir.HirField -> hirMeasureTerm(e.target)?.let {
            sublimation.frontend.hir.HirTField(it, e.name, 0, SYNTHETIC_POS)
        }
        else -> null
        }
    }

    /** measure 义务项预归约（定义性展开 + 子项递归;瞬态零 nid）——A-DEFRULE 同 P-12 裁决 B 范围。 */
    private fun measureReduce(t: sublimation.frontend.hir.HirTerm, depth: Int): sublimation.frontend.hir.HirTerm? {
        if (depth > MEASURE_REDUCE_CAP) return null
        val call = t as? sublimation.frontend.hir.HirTCall ?: return reduceMeasureSub(t, depth)
        val name = (call.callee as? sublimation.frontend.hir.HirTName)?.name
        if (name != null) {
            val expanded = oracleImpl.definitionalExpand(name, call.args)
            if (expanded != null)
                return measureReduce(expanded, depth + 1) ?: expanded
        }
        return reduceMeasureSub(t, depth)
    }

    private fun reduceMeasureSub(t: sublimation.frontend.hir.HirTerm, depth: Int): sublimation.frontend.hir.HirTerm? = when (t) {
        is sublimation.frontend.hir.HirTBin -> {
            val l = measureReduce(t.left, depth)
            val r = measureReduce(t.right, depth)
            if (l == null && r == null) null
            else sublimation.frontend.hir.HirTBin(t.op, l ?: t.left, r ?: t.right, 0, SYNTHETIC_POS)
        }
        is sublimation.frontend.hir.HirTCall -> {
            val subs = t.args.map { measureReduce(it, depth) }
            if (subs.all { it == null }) null
            else sublimation.frontend.hir.HirTCall(t.callee, t.args.zip(subs).map { i -> i.second ?: i.first }, 0, SYNTHETIC_POS)
        }
        else -> null
    }

    private val measureProofs = mutableListOf<MeasureProof>()

    /** t97 阶段 B:闸级别单点——error 硬拒（队长 2026-11-10 批;阶段 A warn 报告态已验毕收档）。 */
    private fun termDiag(pos: SourceRange, message: String) = diag.error("E-NON-STRUCTURAL-REC", pos, message)

    /**
     * t97 调用闸:callExpr 三返回位单 pass（新工程 callee+实参位置信息齐备——不做事后回看）。
     * 内层**具名纯函数**（lambda 帧无名——延迟调用不计直接自递归）的**直接自递归**
     * 且无任一实参「变小」⇒ 诊断。
     */
    private fun checkTermination(calleeName: String?, args: List<HirExpr>, pos: SourceRange) {
        if (uncheckedDepth > 0) return         // unchecked 放行（同 t96 三路之一——豁免检查不豁免效应存在）
        val frame = funFrames.lastOrNull() ?: return
        val name = frame.name ?: return          // lambda 帧：保守跳过（闭包 创建/传递不等于调用）
        if (frame.impure) return                 // @impure 逃逸（验收 #3）
        if (calleeName != name) return
        if (args.any { isShrinkingArg(it, frame) }) return
        // t110（OQ-5 最小结构归纳）:数值 measure 递减义务证出 ⇒ 停机（免闸）；证不出/无数值位 ⇒ 回落既有闸（R-02 口径不变,不硬造）
        val measure = measureProofRule(calleeName, args, frame)
        if (measure != null && measure.first != null) return
        val attemptedMeasure = measure?.second ?: false
        termDiag(pos, "纯函数 " + ident(name) + " 的自递归调用没有实参在「变小」——" +
            "停机证明须至少一个实参为形参的子结构（如 when 解构槽位 / 字段访问）" +
            "或向某数值靠拢（如 n - 1）" +
            (if (attemptedMeasure) ";数值 measure 证明通道已尝试（e < p 义务未证出——可先断言递减关系或手工喂归纳原理公理）" else "") +
            ";或给该函数标 @impure 放弃停机检查（@impure 是停机闸的唯一逃逸）")
    }

    // ---------- t121（R-16 A1 · I-1 收口第一期）：主趟事件框架——构造位发事件，判据订阅 ----------
    // 节点种类只在主趟 expr() 一处枚举；t94/t97 改事件订阅（消 containsImpureCall 族）。
    // A2 收 t96/t107（gateWalk/mutCaptureScan），A3 收 AST 谓词族。行为零变口径：t94 订阅判据
    // 与旧 containsImpureCall 单点同源（impureCallee）；t97 订阅即既有 checkTermination 调用位。

    /** 当前效应作用域的「体含被执行的不纯调用」累加位（t94 订阅 onCall 写）。 */
    private var effectAcc = false

    /** 效应作用域栈：lambda/anonFun/函数体一层一个——内层闭包效应不外溢（创建闭包≠效应，同旧口径）。 */
    private val effectScopeStack = ArrayDeque<Boolean>()

    /** 进效应作用域（体构造前：保存外层累加位、本层清零）。 */
    private fun enterEffectScope() {
        effectScopeStack.addLast(effectAcc)
        effectAcc = false
    }

    /** 出效应作用域（体构造后）：返回本层累加结论、外层位恢复（内层不并入外层）。 */
    private fun exitEffectScope(): Boolean {
        val mine = effectAcc
        effectAcc = effectScopeStack.removeLast()
        return mine
    }

    /** onOpCall 事件（算符前缀内建形态）：仅 t94 订阅——旧实现该位无 t97 停机检查，零变保真。 */
    private fun onOpCall(callee: HirExpr) {
        if (impureCallee(callee)) effectAcc = true
    }

    private fun isShrinkingArg(a: HirExpr, frame: FunFrame): Boolean = when (a) {
        is HirNameRef -> patSubOf(a.name, frame)                     // ① 解构槽位（非形参本身）
        is HirField -> fieldRoot(a.target, frame)                     // ① 字段链
        is HirBinExpr -> a.op == "-" && a.left is HirNameRef &&       // ② 减法靠拢:形参 - 字面
            (a.left as HirNameRef).name in frame.paramNames && a.right is HirIntLit
        else -> false
    }

    /** 名为模式槽位时其来源（when 主题）链至形参 ⇒ 形参的子结构（嵌套 when 经来源链传递）。 */
    private fun patSubOf(name: String, frame: FunFrame): Boolean {
        var cur: String? = name
        val seen = mutableSetOf<String>()
        while (cur != null && seen.add(cur)) {
            val o = patOrigins.mapNotNull { it[cur] }.lastOrNull() ?: return false
            if (o in frame.paramNames) return true
            cur = o
        }
        return false
    }

    private fun fieldRoot(t: HirExpr, frame: FunFrame): Boolean = when (t) {
        is HirNameRef -> t.name in frame.paramNames || patSubOf(t.name, frame)
        is HirField -> fieldRoot(t.target, frame)
        else -> false
    }

    /** t97 形态①数据供给：HIR 模式的绑定名（HirPatBind 名 / is-T 的 as 绑定 / 构造子与元组的子模式）。 */
    private fun hirPatBindNames(p: sublimation.frontend.hir.HirPattern): List<String> = when (p) {
        is HirPatBind -> listOf(p.name)
        is HirPatIs -> listOfNotNull(p.bind)
        is HirPatCtor -> p.args.flatMap { hirPatBindNames(it) }
        is HirPatTuple -> p.items.flatMap { hirPatBindNames(it) }
        else -> emptyList()
    }
    private fun propOnlyExpr(pos: SourceRange, what: String, hint: String? = null): HirExpr {
        diag.error("E-PROP-POS", pos, what + " 只能出现在命题位（程序面无此形态）" + (if (hint != null) "——" + hint else ""))
        return HirErrorExpr("命题专属语法出现在程序位：" + what, freshNid(), pos, emptyList())
    }

    // ============================ 类型 ============================

    private fun type(t: Type): sublimation.frontend.hir.HirType = when (t) {
        is NamedType -> {
            val nid = freshNid()
            val args = t.args.map { type(it) }
            // P-8/D（卷 TS-5/TS-25/TS-29）：trait 体内 Self → HirSelfType（带实参形态保留具名承载，
            // 满足判定层替换——HIR 树本体不替换，TS-29）；体外 → E-SELF-OUTSIDE-CLASS
            if (t.name == "Self" && !isTypeParam(t.name)) when {
                inTraitBody > 0 && t.args.isEmpty() ->
                    sublimation.frontend.hir.HirSelfType(nid, t.pos, notesOf(t))
                inTraitBody > 0 ->
                    HirNamedType("Self", args, null, nid, t.pos, notesOf(t))
                else -> {
                    diag.error("E-SELF-OUTSIDE-CLASS", t.pos,
                        "Self 只在特质体内有意义（指代满足该特质的类型，TS-25）")
                    sublimation.frontend.hir.HirErrorType("Self 体外使用", nid, t.pos, notesOf(t))
                }
            } else HirNamedType(t.name, args, if (isTypeParam(t.name)) null else resolver.resolveType(t.name, t.pos), nid, t.pos, notesOf(t))
        }
        is sublimation.frontend.ast.FunType -> {
            val nid = freshNid()
            // 类型位的理论参数只承载类型参数（命题参数无类型位通道，ADR-10 裁决）
            val theory = t.theory.mapNotNull { tp ->
                (tp as? TypeParam)?.let { theoryParam(it, t.theory, listOf(t.ret), t.params) as sublimation.frontend.hir.HirTypeParam }
            }
            val params = t.params.map { type(it) }
            val ret = type(t.ret)
            // 类型位上下文：无 $n 改写目标（形参不在词法作用域，ADR-10 裁决）
            val context = t.context?.map { propNorm.prop(it) }
            val purity = if ("impure" in t.purity) HirPurity.IMPURE else HirPurity.PURE
            sublimation.frontend.hir.HirFunType(params, ret, purity, theory, context, nid, t.pos, notesOf(t))
        }
        is sublimation.frontend.ast.TupleType ->
            sublimation.frontend.hir.HirTupleType(t.items.map { type(it) }, freshNid(), t.pos, notesOf(t))
        is sublimation.frontend.ast.MutType ->
            HirMutType(type(t.inner), freshNid(), t.pos, notesOf(t))
        is sublimation.frontend.ast.AtomicType ->
            sublimation.frontend.hir.HirAtomicType(type(t.inner), freshNid(), t.pos, notesOf(t))
        is sublimation.frontend.ast.RefType ->
            sublimation.frontend.hir.HirRefType(type(t.inner), freshNid(), t.pos, notesOf(t))   // t95（N2）：@ref T
        is sublimation.frontend.ast.UnionType ->
            // t41（ADR-28 R-16）：联合构造即展平——变体里的具名别名就地展开（嵌套联合拼接，S-15 既有规则沿联合外沿）
            flattenUnion(HirUnionType(t.variants.map { type(it) }, freshNid(), t.pos, notesOf(t)))
        is QualifiedType -> {
            // 本期单文件：模块段折叠留给后期模块挂载任务（ADR-1 §3.2）——形态保留、不落诊断
            val nid = freshNid()
            val args = t.args.map { type(it) }
            sublimation.frontend.hir.HirQualifiedType(t.module, t.name, args, null, nid, t.pos, notesOf(t))
        }
        is ErrorType -> HirErrorType(t.message, freshNid(), t.pos, notesOf(t))
    }

    // ============================ P-8/D（卷 TS-11~TS-24）：扩展函数调用与满足判定 ============================


    /**
     * 直接调用的扩展计划（TS-12/TS-14）：
     * - `f`（无 `::`）：值域无该名且存在任一接收者头下的同名扩展 → (null, f)（头待实参 1 定）；
     * - `T::f`（类型限定）：T 为类型头且该头下存在 f 扩展 → (T, f)。
     */
    private fun extCallPlan(name: String): Pair<String?, String>? {
        val i = name.indexOf("::")
        if (i < 0) return if (symbols.hasExtFun(name)) null to name else null
        val pre = name.substring(0, i)
        val post = name.substring(i + 2)
        return if (post.isNotEmpty() && pre.isNotEmpty() && symbols.isTypeName(pre) &&
            symbols.lookupExtDeclVisible(pre, post) != null) pre to post else null
    }

    /** P-8/D（TS-12/TS-10）：扩展函数调用类型——接收者作首参的 callFun + 调用点约束检查（TS-22）。 */
    private fun extCallType(d: FunDecl, argTypes: List<sublimation.frontend.hir.HirType>, pos: SourceRange): sublimation.frontend.hir.HirType {
        enterTypeParams(d.theory)
        return try {
            val recvT = type(d.receiver!!)
            val (ps, rt) = sigOf(d)
            val tps = d.theory.filterIsInstance<TypeParam>().map { it.name }
            // t48（R-30）：变长形参（末位 @list）——扩展签名里 receiver 占首位，变长位随其后 +1
            val vp = d.params.map { "list" in it.annotations }
            val varIdx = if (vp.isNotEmpty() && vp.last()) d.params.lastIndex + 1 else -1
            val formalTypes = listOf(recvT) + ps.map { expand(it) }
            // D4：先解理论参数（约束检查），再全量核对（callFun 内 instCall 二次解无副作用——解集一致）
            checkConstraints(d, infer.instCall(formalTypes, tps, argTypes, varargIdx = varIdx).first, pos)
            infer.callFun(d.name, formalTypes, listOf("self") + d.params.map { it.name },
                listOf(false) + d.params.map { it.default != null }, rt?.let { expand(it) }, argTypes, pos, tps,
                variadic = listOf(false) + vp)
        } finally { exitTypeParams() }
    }

    /** P-8/D（TS-21/TS-22/TS-24）：调用点约束检查——V1 仅「单个类型变量 : 特质」；越界落 E-TC-BOUNDARY。 */
    private fun checkConstraints(d: FunDecl, solved: Map<String, sublimation.frontend.hir.HirType>, pos: SourceRange) {
        for (tp in d.theory) if (tp is TypeParam) {
            val cn = tp.constraint ?: continue
            if (tp.arity > 0) {
                // t62 ST-14：保留「不可为界」口径（高阶约束 V1 不支持）——E-TC-BOUNDARY 新语义
                diag.error("E-TC-BOUNDARY", pos,
                    "理论参数 " + ident(tp.name) + " 的高阶类型约束（arity > 0）不可为界（V1 不支持，见 TS-24）")
                continue
            }
            val concrete = solved[tp.name] ?: continue    // 未解出（synthetic 位）→ V1 不查
            // t62（ST-14）：界核对并入子类型偏序（上界 T <: X；X 为特质时即满足判定，单一通道）。
            // 界方向推断（协变上界/逆向下界/不变报错 E-BOUND-INVARIANT 声明位）按 ST-12/ST-13 属步骤 3；
            // V1 取上界口径（satisfaction 与既有样本对齐）。
            if (!infer.subtype(concrete, infer.namedT(cn))) {
                val miss = (satisfiesCheck(concrete, cn) as? SatisfyMissing)?.list
                if (miss != null)
                    diag.error("E-TC-NOT-SATISFIED", pos,
                        "类型 " + infer.render(concrete) + " 不满足特质 " + ident(cn) +
                            "——缺失规范：" + miss + "（TS-20 部分满足即不满足）")
                else
                    diag.error("E-TYPE-MISMATCH", pos,
                        "类型实参 " + infer.render(concrete) + " 不满足界 " + ident(cn) + "（上界 T <: " + ident(cn) + "）")
            }
        }
    }

    // ---------- 满足判定（TS-17~TS-20：使用期现场判定 + 单次编译缓存，TS-18） ----------

    /** (具体类型渲染, 特质名) → 判定结果缓存（TS-18 单次编译内缓存）。 */
    private val satisfiesCache = LinkedHashMap<Pair<String, String>, SatisfyOutcome>()

    private fun satisfiesCheck(concrete: sublimation.frontend.hir.HirType, traitName: String): SatisfyOutcome {
        val key = infer.render(concrete) to traitName
        satisfiesCache[key]?.let { return it }
        // 继承闭包（TS-8）：父类规范并入要求；环由 traitCycleCheck 在声明期已落 E-CYCLE，此作访问保护
        val specs = mutableListOf<Pair<String, FunDecl>>()
        val seen = mutableSetOf(traitName)
        val stack = ArrayDeque<String>().apply { addLast(traitName) }
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            val cd = traitDecls[cur] ?: continue
            cd.members.forEach { m -> specs += cur to m }   // P-9/B.3 后 trait 体只剩函数规范（val 常量已废除）
            cd.parents.forEach { p ->
                val pn = (p as? NamedType)?.name ?: return@forEach
                if (seen.add(pn)) stack.addLast(pn)
            }
        }
        val missing = mutableListOf<String>()
        var residue = false
        for ((src, sigma) in specs) {
            when (extSpecMatch(sigma, concrete)) {
                SpecMatch.UNMATCHED -> missing.add(src + "." + renderSpecAfterSelf(sigma, concrete))
                SpecMatch.SELF_RESIDUE -> residue = true
                SpecMatch.MATCHED -> { }
            }
        }
        val out: SatisfyOutcome = when {
            residue -> SatisfySelfResidue("签名含未被 " + infer.render(concrete) + " 替换的 Self")
            missing.isNotEmpty() -> SatisfyMissing(missing.joinToString("；"))
            else -> SatisfyOk
        }
        satisfiesCache[key] = out
        return out
    }

    /** 单条规范与扩展函数集匹配（TS-17）：接收者头下的同名扩展任一签名一致即满足。 */
    private fun extSpecMatch(sigma: FunDecl, concrete: sublimation.frontend.hir.HirType): SpecMatch {
        val head = ExtFunHeads.ofHir(concrete) ?: return SpecMatch.UNMATCHED
        // t39（TS-19 可见集）：调用点可见的扩展函数——帧链内→外按键遮蔽 + 全局其余键
        val exts = symbols.lookupExtDeclVisible(head, sigma.name) ?: return SpecMatch.UNMATCHED
        inTraitBody++
        val ok = try {
            enterTypeParams(sigma.theory)
            val specPs = sigma.params.map { specTypeQuiet(it.type ?: ErrorType("形参类型标注省略", it.pos)) }
            val specR = sigma.retType?.let { specTypeQuiet(it) } ?: infer.namedT("Null")
            val absorb = specPs.isNotEmpty() && isSelfForm(specPs.first())   // 替换前判定（ADR-24 接收者吸收位）
            val sPs = specPs.map { substSelf(it, concrete) }
            val sR = substSelf(specR, concrete)
            if (sPs.any { containsSelf(it) } || containsSelf(sR)) return SpecMatch.SELF_RESIDUE   // TS-27
            // t39：可见集内唯一声明（V1 禁止重载）——同一域声明顺序无关性质保持
            extSigMatch(sigma, exts, sPs, sR, absorb)
        } finally { exitTypeParams(); inTraitBody-- }
        return if (ok) SpecMatch.MATCHED else SpecMatch.UNMATCHED
    }

    /**
     * 替换后的规范签名与扩展签名合一（TS-17：名字（桶键已匹配）+ 形参逐位 + 返回类型；
     * 理论参数两侧均作变量，unifyInto 收集绑定）。
     * ADR-24 裁决：**规范首位形参为 Self 形态（Self / Self[…]）时该位由接收者承担**，
     * 其余位与扩展函数形参逐位对齐（fa: Self[A] ↔ 接收者 List[A]）；首位非 Self → 接收者不占形参位。
     */
    private fun extSigMatch(
        sigma: FunDecl, ext: FunDecl,
        sPs: List<sublimation.frontend.hir.HirType>, sR: sublimation.frontend.hir.HirType,
        absorb: Boolean,
    ): Boolean {
        enterTypeParams(ext.theory)
        return try {
            val recvT = ext.receiver?.let { specTypeQuiet(it) } ?: return false
            val extPs = ext.params.map { specTypeQuiet(it.type ?: ErrorType("形参类型标注省略", it.pos)) }
            val extR = ext.retType?.let { specTypeQuiet(it) } ?: infer.namedT("Null")
            val specTpNames = sigma.theory.filterIsInstance<TypeParam>().map { it.name }
            val extTpNames = ext.theory.filterIsInstance<TypeParam>().map { it.name }
            val vars = (specTpNames + extTpNames).toSet()
            val sub = LinkedHashMap<String, sublimation.frontend.hir.HirType>()
            val recvOk = if (absorb) infer.unifyInto(recvT, sPs.first(), vars, sub) else true
            val effSpec = if (absorb) sPs.drop(1) else sPs
            val paramsOk = effSpec.size == extPs.size &&
                effSpec.zip(extPs).all { infer.unifyInto(it.first, it.second, vars, sub) }
            val retOk = infer.unifyInto(sR, extR, vars, sub)
            recvOk && paramsOk && retOk
        } finally { exitTypeParams() }
    }

    /** 规范签名（Self 已替换）渲染——缺失规范清单的条目形态：map(fa: List[Nat], f: (A) => B): List[B]。 */
    private fun renderSpecAfterSelf(sigma: FunDecl, concrete: sublimation.frontend.hir.HirType): String {
        inTraitBody++
        return try {
            val ps = sigma.params.map { substSelf(specTypeQuiet(it.type ?: ErrorType("形参类型标注省略", it.pos)), concrete) }
            val rt = substSelf(sigma.retType?.let { specTypeQuiet(it) } ?: infer.namedT("Null"), concrete)
            sigma.name + "(" + ps.joinToString(", ") { infer.render(it) } + "): " + infer.render(rt)
        } finally { inTraitBody-- }
    }

    /** trait 体语义的静默类型读取（规范签名计算专用：无诊断、Self→HirSelfType、未知名→HirErrorType）。 */
    private fun specTypeQuiet(t: Type): sublimation.frontend.hir.HirType = when (t) {
        is NamedType -> {
            val args = t.args.map { specTypeQuiet(it) }
            when {
                t.name == "Self" && t.args.isEmpty() ->
                    sublimation.frontend.hir.HirSelfType(freshNid(), t.pos, emptyList())
                t.name == "Self" ->
                    HirNamedType("Self", args, null, freshNid(), t.pos, emptyList())
                isTypeParam(t.name) || symbols.lookupType(t.name) != null ->
                    HirNamedType(t.name, args, null, freshNid(), t.pos, emptyList())
                else -> sublimation.frontend.hir.HirErrorType(t.name, freshNid(), t.pos, emptyList())
            }
        }
        is sublimation.frontend.ast.FunType ->
            sublimation.frontend.hir.HirFunType(
                t.params.map { specTypeQuiet(it) }, specTypeQuiet(t.ret),
                if ("impure" in t.purity) HirPurity.IMPURE else HirPurity.PURE,
                t.theory.mapNotNull { (it as? TypeParam)?.let { tp -> theoryParam(tp, t.theory) as sublimation.frontend.hir.HirTypeParam } },
                null, freshNid(), t.pos, emptyList())
        is sublimation.frontend.ast.TupleType ->
            sublimation.frontend.hir.HirTupleType(t.items.map { specTypeQuiet(it) }, freshNid(), t.pos, emptyList())
        is sublimation.frontend.ast.MutType ->
            HirMutType(specTypeQuiet(t.inner), freshNid(), t.pos, emptyList())
        is sublimation.frontend.ast.AtomicType ->
            sublimation.frontend.hir.HirAtomicType(specTypeQuiet(t.inner), freshNid(), t.pos, emptyList())
        is sublimation.frontend.ast.RefType ->
            sublimation.frontend.hir.HirRefType(specTypeQuiet(t.inner), freshNid(), t.pos, emptyList())
        is sublimation.frontend.ast.UnionType ->
            HirUnionType(t.variants.map { specTypeQuiet(it) }, freshNid(), t.pos, emptyList())
        is QualifiedType ->
            sublimation.frontend.hir.HirQualifiedType(t.module, t.name, t.args.map { specTypeQuiet(it) }, null, freshNid(), t.pos, emptyList())
        is sublimation.frontend.ast.ErrorType ->
            sublimation.frontend.hir.HirErrorType(t.message, freshNid(), t.pos, emptyList())
    }

    /** Self := concrete 替换（TS-17：bare Self → concrete；Self[A…] → concrete 的头应用其实参）。 */
    private fun substSelf(t: sublimation.frontend.hir.HirType, concrete: sublimation.frontend.hir.HirType): sublimation.frontend.hir.HirType = when (t) {
        is sublimation.frontend.hir.HirSelfType -> concrete
        is HirNamedType -> if (t.name == "Self") {
            if (concrete is HirNamedType) infer.namedT(concrete.name, t.args) else concrete
        } else HirNamedType(t.name, t.args.map { substSelf(it, concrete) }, t.symbol, freshNid(), t.origin, t.notes)
        is sublimation.frontend.hir.HirFunType -> sublimation.frontend.hir.HirFunType(
            t.params.map { substSelf(it, concrete) }, substSelf(t.ret, concrete), t.purity, t.theory, t.context,
            freshNid(), t.origin, t.notes)
        is sublimation.frontend.hir.HirTupleType ->
            sublimation.frontend.hir.HirTupleType(t.items.map { substSelf(it, concrete) }, freshNid(), t.origin, t.notes)
        is HirMutType -> HirMutType(substSelf(t.inner, concrete), freshNid(), t.origin, t.notes)
        is sublimation.frontend.hir.HirAtomicType ->
            sublimation.frontend.hir.HirAtomicType(substSelf(t.inner, concrete), freshNid(), t.origin, t.notes)
        is sublimation.frontend.hir.HirRefType ->
            sublimation.frontend.hir.HirRefType(substSelf(t.inner, concrete), freshNid(), t.origin, t.notes)
        else -> t
    }

    /** Self 形态判定（替换后）：HirSelfType，或具名 Self 头。 */
    private fun isSelfForm(t: sublimation.frontend.hir.HirType): Boolean =
        t is sublimation.frontend.hir.HirSelfType || (t is HirNamedType && t.name == "Self")

    /** 签名内是否残留 Self（TS-27）。 */
    private fun containsSelf(t: sublimation.frontend.hir.HirType): Boolean = when (t) {
        is sublimation.frontend.hir.HirSelfType -> true
        is HirNamedType -> t.name == "Self" || t.args.any { containsSelf(it) }
        is sublimation.frontend.hir.HirFunType -> t.params.any { containsSelf(it) } || containsSelf(t.ret)
        is sublimation.frontend.hir.HirTupleType -> t.items.any { containsSelf(it) }
        is HirMutType -> containsSelf(t.inner)
        is sublimation.frontend.hir.HirAtomicType -> containsSelf(t.inner)
        is sublimation.frontend.hir.HirRefType -> containsSelf(t.inner)   // t95
        is sublimation.frontend.hir.HirUnionType -> t.variants.any { containsSelf(it) }
        else -> false
    }

    // ============================ 形参 / 理论参数 ============================

    private fun param(
        p: Param, kind: SymbolKind, laterNames: Set<String> = emptySet(),
        /** t115（S2）：省略形参/字段的元变量（脚本模式入口；两模式返回位不经此）——声明位求解后落位。 */
        meta: InferMeta? = null,
    ): HirParam {
        val nid = freshNid()
        // 声明先于默认值（Kotlin 规则口径），t8 推导类型同时入登记——名字引用结论的查询侧
        val t = when {
            p.type != null -> type(p.type)
            // t115 R-13（工程模式硬拒，队长 OQ-1 裁决落档）：形参/字段省略标注=脚本专属——工程 E-OMITTED-TYPE 真红
            !scriptMode -> {
                diag.error("E-OMITTED-TYPE", p.pos,
                    (if (kind == SymbolKind.FIELD) "字段 " else "形参 ") + ident(p.name) +
                        " 省略了类型标注——工程模式要求显式标注（仅脚本模式开放推导，R-13）")
                type(ErrorType("形参类型标注省略", p.pos))   // t113 半成品口径不变（树继续，诊断已红）
            }
            else -> {
                // t117（R 轮驱动 §2.1 按附录 B 修订）：声明位 = 轮序起点——**有种用种**（上一轮终解，
                // 终轮种子全覆盖 ⇒ 终轮零占位）；无种（首轮）用占位（瞬态：首轮 HIR 弃，占位不入产物）。
                // 声明位不再闭合求解——求解全部移至轮末 metaSolutions()（含后到约束的消化）。
                val m = meta
                if (m == null) type(ErrorType("形参类型标注省略", p.pos))
                else m.resolved ?: m.slot(infer)
            }
        }
        // t125（S5b / R-06）：形参可变性提升**预应用**（T2 形参被赋值 ⇒ @mut——R-06 原话「提升出的 @mut
        //  即形参推导类型」：视图入签名 ⇒ 调用按 R-01 表深拷核）。C-5：字段（FIELD）不提升。
        val mutEvi = if (scriptMode && kind != SymbolKind.FIELD) mutPromoted[mutKey(p.pos, p.name)] else null
        val t2 = if (mutEvi != null) promotedView(t, p.pos) else t
        if (mutEvi != null) mutNote(p.name, t2, p.pos, "形参被赋值 T2", mutEvi)
        // t48（ADR-32 R-30）：@list 变长形参——HirParam 保留**元素类型** + 注解旗标（D-9），
        // 体位符号按 List[元素] 登记（序列值）；调用位按元素类型核对剩余实参。
        val isVararg = "list" in p.annotations
        val symbol = declareLocal(p.name, kind, p.pos,
            if (isVararg) infer.namedT("List", listOf(t2)) else t2,
            scriptPromotable = scriptMode && kind != SymbolKind.FIELD)
        if (kind != SymbolKind.FIELD) injectTypeFacts(p.name, t2, p.pos)   // t65（字段声明位无事实帧——跳过）
        // t45（ADR-30 R-22）：默认值求值环境=声明处——前序形参/字段（+ self，由 funDecl 先行登记）可见；
        // 引用**后序**名落 E-DEFAULT-ORDER，且跳过本处 elaborate（不再重复报 E-UNBOUND-NAME）
        val default = p.default?.let { def ->
            val forward = forwardName(def, laterNames)
            if (forward != null) {
                diag.error("E-DEFAULT-ORDER", def.pos,
                    "默认值只能引用先于它声明的参数；" + ident(forward) + " 声明在 " + ident(p.name) + " 之后")
                null
            } else expectAt(expand(t2)) { expr(def) }   // t66：默认值的直接期望 = 形参声明类型
        }
        if (default != null) {
            // t60-3：默认值类型核对（声明位，与形参/返回值/var 标注同一 adaptOk 判据；E-DEFAULT-TYPE）
            infer.checkScope(p.pos) { infer.defaultType(p.name, expand(t2), etype(default), p.pos) }
        }
        p.destruct?.forEach { n -> declareLocal(n, SymbolKind.PARAM, p.pos, infer.syntheticT("分量" + n),
            scriptPromotable = scriptMode && kind != SymbolKind.FIELD) }
        // t115/t117：载体回填（t108 槽位）——证据源序去重落表（种子证据 + 本轮收据合并）；
        // 默认填充零证据零标记（零发射）
        // t118（R-02）：退化空位零发射（inferred=false——Any 填充不冒充有证据的解）
        return HirParam(p.name, t2, symbol, p.annotations, default, p.destruct, nid, p.pos, emptyList(),
            inferred = meta != null && meta.hasEvidence() && !meta.degradedSlot,
            evidence = if (meta != null && meta.hasEvidence() && !meta.degradedSlot) meta.evidenceList()
                else emptyList())
    }

    /** t45（ADR-30 R-22）：默认值 AST 里的自由名字里首个命中后序名者（无则 null）。
     *  不穿嵌套作用域（lambda/块/if/when/量词/Task/Flow/匿名函数）——其内名字归该层自解析。 */
    private fun forwardName(e: sublimation.frontend.ast.Expr, laterNames: Set<String>): String? {
        if (laterNames.isEmpty()) return null
        val out = LinkedHashSet<String>()
        freeNameRefs(e, out)
        return out.firstOrNull { it in laterNames }
    }

    /** t45：默认值表达式的自由名字（NameRef 名收集；嵌套作用域边界止步）。 */
    private fun freeNameRefs(e: sublimation.frontend.ast.Expr, out: MutableSet<String>) {
        when (e) {
            is sublimation.frontend.ast.NameRef -> out += e.name
            is sublimation.frontend.ast.BinExpr -> { freeNameRefs(e.left, out); freeNameRefs(e.right, out) }
            is sublimation.frontend.ast.UniExpr -> freeNameRefs(e.operand, out)
            is sublimation.frontend.ast.TypeTestExpr -> freeNameRefs(e.target, out)
            is sublimation.frontend.ast.IsPatExpr -> freeNameRefs(e.target, out)
            is sublimation.frontend.ast.CallExpr -> {
                freeNameRefs(e.callee, out); e.args.forEach { freeNameRefs(it, out) }
                e.namedArgs.values.forEach { freeNameRefs(it, out) }
            }
            is sublimation.frontend.ast.InstExpr -> { freeNameRefs(e.target, out); e.terms.forEach { freeNameRefs(it, out) } }
            is sublimation.frontend.ast.FieldExpr -> freeNameRefs(e.target, out)
            is sublimation.frontend.ast.FunRefExpr -> freeNameRefs(e.target, out)
            is sublimation.frontend.ast.SymbolCallExpr -> e.args.forEach { freeNameRefs(it, out) }
            is sublimation.frontend.ast.TupleExpr -> e.items.forEach { freeNameRefs(it, out) }
            is sublimation.frontend.ast.StructCtorExpr -> e.assigns.forEach { freeNameRefs(it.second, out) }
            is sublimation.frontend.ast.ByExpr -> { freeNameRefs(e.target, out); e.props.forEach { freeNameRefs(it, out) } }
            is sublimation.frontend.ast.VarExpr -> freeNameRefs(e.value, out)
            // 嵌套作用域：名字归该层解析，不外提（Lambda/块/if/when/量词/Task/Flow/匿名函数/字面量/其它叶子）
            else -> { }
        }
    }

    /** t62（ADR-39）：AST 类型位点出现判定（直接位极性——形参=负、返回/字段/别名目标=正；复合极性属步骤 3）。 */
    private fun occursInAst(name: String, sites: List<sublimation.frontend.ast.Type?>): Boolean =
        sites.any { it != null && occursInTypeAst(name, it) }

    private fun occursInTypeAst(name: String, t: sublimation.frontend.ast.Type): Boolean = when (t) {
        is sublimation.frontend.ast.NamedType ->
            t.name == name || t.args.any { occursInTypeAst(name, it) }
        is sublimation.frontend.ast.FunType ->
            t.params.any { occursInTypeAst(name, it) } || occursInTypeAst(name, t.ret)
        is sublimation.frontend.ast.TupleType -> t.items.any { occursInTypeAst(name, it) }
        is sublimation.frontend.ast.MutType -> occursInTypeAst(name, t.inner)
        is sublimation.frontend.ast.RefType -> occursInTypeAst(name, t.inner)   // t95
        else -> false
    }

    /** t62（ADR-39 ST-3/ST-9）：got 满足特质（结构满足——扩展函数覆盖规范），或 got 特质经声明父链 <: 该特质。 */
    private fun traitSub(got: sublimation.frontend.hir.HirType, traitName: String): Boolean {
        // 仅声明特质参与满足子类型（非特质名走名义/结构位，不得被空规范集空洞地「满足」——健全性）
        if (traitDecls[traitName] == null) return false
        val cn = (got as? sublimation.frontend.hir.HirNamedType)?.name
        if (cn != null && traitDecls.containsKey(cn)) {
            // 特质→特质：ST-9 父链（TS-8 继承闭包；环由 traitCycleCheck 声明期已落 E-CYCLE）
            val seen = mutableSetOf(cn)
            val stack = ArrayDeque<String>().apply { addLast(cn) }
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                if (cur == traitName) return true
                traitDecls[cur]?.parents?.forEach { pp ->
                    val pn = (pp as? sublimation.frontend.ast.NamedType)?.name ?: return@forEach
                    if (seen.add(pn)) stack.addLast(pn)
                }
            }
            return false
        }
        return satisfiesCheck(got, traitName) is SatisfyOk
    }

    /** t62（2.9 结构满足）：结构体字段清单（名→HIR 类型）；泛型结构体/未声明名 → null（走名义同名位）。 */
    private fun structFieldTypes(name: String): List<Pair<String, sublimation.frontend.hir.HirType>>? {
        val sd = structDecls[name] ?: return null
        if (sd.theory.isNotEmpty()) return null
        return sd.fields.map { f -> f.name to expand(type(f.type ?: ErrorType("形参类型标注省略", f.pos))) }
    }

    private fun theoryParam(
        tp: TheoryParam, allTheory: List<TheoryParam>,
        posSites: List<Type?> = emptyList(), negSites: List<Type?> = emptyList(),
        mutNegSites: List<Type?> = emptyList(),
    ): sublimation.frontend.hir.HirTheoryParam = when (tp) {
        is TypeParam -> {
            val nid = freshNid()
            // 约束（特质名）解析结论；类型参数名本身由类型参数栈支撑（无 SymbolKind.TYPEPARAM）
            val constraint = tp.constraint?.let { resolver.resolveType(it, tp.pos) }
            // P-8/D（TS-24）：高阶类型约束 V1 不支持——声明期即落 E-TC-BOUNDARY（不等调用点解出）
            if (tp.arity > 0 && tp.constraint != null)
                diag.error("E-TC-BOUNDARY", tp.pos,
                    "理论参数 " + ident(tp.name) + " 的高阶类型约束（arity > 0）不可为界（V1 不支持，见 TS-24）")
            // t62（ADR-39，L6）/ t64（ADR-41，ST-13.1）：声明位「不变 + 写了界」→ E-BOUND-INVARIANT。
            // 极性口径（t64 升级）：形参位 = 负、返回位 = 正、字段/别名目标 = 正；@mut 成员形参位与
            // 可写字段写位按双视图计入（σ_mut 专有 / 贯通两视图）；复合极性（负×负=正）沿应用链
            // 由 VarianceInfer 乘下去。
            // t64：每个泛型声明都经推断器建表（无界参数亦得 σ 方向；表惰性且单次不动点）。
            val vp = variance().sitesVariance(tp, allTheory, posSites, negSites, mutNegSites)
            if (tp.constraint != null && vp.imm == Variance.INV)
                diag.error("E-BOUND-INVARIANT", tp.pos,
                    "类型参数 " + ident(tp.name) + " 在使用处既作协变又作逆变（不变），: 界无法确定是上界还是下界；" +
                        "请改为 [T] 不加界，或调整使用处使 " + ident(tp.name) + " 的变构性单一")
            HirTypeParam(tp.name, constraint, tp.arity, nid, tp.pos, emptyList())
        }
        is sublimation.frontend.ast.PropParam ->
            sublimation.frontend.hir.HirPropParam(tp.name, freshNid(), tp.pos, emptyList())
    }
}

/** P-8/D（卷 TS-17~TS-27）：满足判定结论。SatisfyOk / SatisfyMissing（缺失清单，TS-20）/ SatisfySelfResidue（TS-27）。 */
private sealed interface SatisfyOutcome
private object SatisfyOk : SatisfyOutcome
private data class SatisfyMissing(val list: String) : SatisfyOutcome
private data class SatisfySelfResidue(val detail: String) : SatisfyOutcome

/** P-8/D：单条规范与扩展函数集的匹配结论。 */
private enum class SpecMatch { MATCHED, UNMATCHED, SELF_RESIDUE }

/**
 * return 上下文帧（t8）：声明返回类型 + 块体内是否出现过 return（E-MISSING-RETURN 判据）。
 *
 * t97（N1 停机闸）：帧带名/纯度位/参数集——自递归判定须知道「当前在哪个具名函数内」、
 * 「它是否 @impure（逃逸口径）」、「原参数名集（缩小形态①的比对基准）」。
 */
private class FunFrame(
    val retType: HirType?,
    val name: String? = null,
    val impure: Boolean = false,
    val paramNames: Set<String> = emptySet(),
    var sawReturn: Boolean = false,
    /** t117：retType 来自返回位元变量的轮序种子（非声明的硬返回类型）——return/体级核对走软通道
     *  （种子是上一轮近似解，本轮证据仍可收紧；约束由元变量通道收集）。 */
    val metaRet: Boolean = false,
) {
    var narrowTries: Int = 0   // t66：收窄尝试预算（NARROW_TRY_CAP）
}
