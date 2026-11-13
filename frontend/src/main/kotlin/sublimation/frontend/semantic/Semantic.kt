package sublimation.frontend.semantic

import sublimation.frontend.ast.AstNode
import sublimation.frontend.ast.FileAst
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.SourcePos
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.diag.ident
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.hir.HirFile
import sublimation.frontend.parser.ParseNote

/**
 * 语义层对外唯一入口（S-2 模块划分 / ADR-6 §三）：compile = collect（两遍符号收集，Collector.kt）
 * → elaborate（单一驱动构造 HIR，Elaborator.kt）。
 *
 * 消费契约（ADR-10）：入参全只读——AST 不改写、parseNotes 不改写；返回 (HirFile, DiagBag)：
 * 诊断袋吸收两阶段全部诊断（collect 的重复声明/构造子歧义 + elaborate 的名称解析/改写/哨兵诊断），
 * 同一源文件的 collect 与 elaborate 相互不吞错误（旧实现吞过——ADR-6 §二 收回）；t8 起 elaborate 的
 * 调用点签名核对会在声明位的同名同消息诊断后产生重复条目——compile 收口前 dedupe（DiagBag 语义，
 * 不吞任何唯一诊断）。
 *
 * 半成品树原则（ADR-1 §3.2）：即使有 error 级诊断，HIR 树仍完整返回——下游（类型层 t8 / JSON t10）
 * 可消费已成功部分，hasError 由 DiagBag 判定（S-1 §2 容错链）。
 */
/** t117（C-6）：R 轮上限。 */
internal const val INFER_ROUNDS_DEFAULT = 8

/** t117 驱动观测报告。 */
internal class RoundsReport(
    val hir: HirFile,
    val diag: DiagBag,
    val rounds: List<RoundObservation>,
    val roundsUsed: Int,
    val notConverged: Boolean,
    val degraded: Boolean,
    /** t125（S5b）：可变性提升重跑发生（测试观察位）。 */
    val mutPromoted: Boolean = false,
    val changedOwners: Set<String>,
)

fun compile(
    file: FileAst,
    parseNotes: Map<AstNode, List<ParseNote>> = emptyMap(),
    /**
     * D-12（模式真值单源）:编译单元形态（project / script）由 CLI 唯一源决定（`.subs` 扩展名 /
     * `--script` 旗标,Main.kt:46）,经 compileFrontend 透传至 elaborate 与 HirJson envelope。
     * 默认 project。废除「顶层语句存在⇒脚本」自算——工程模式顶层 axiom 语句（Parser:204）会被误标
     * （jsoncmp 样本 公理化.subl 已带病基线,本次修复为预期差）。
     */
    scriptMode: Boolean = false,
    /** t118（OQ-5 队长裁=给）：--no-degrade——脚本模式「退化即失败」CI 门（--no-trust 族同设）。 */
    noDegrade: Boolean = false,
    noLift: Boolean = false,
): Pair<HirFile, DiagBag> {
    val report = compileWithRounds(file, parseNotes, scriptMode, noDegrade, noLift)
    return report.hir to report.diag
}

/** t131（S7 / R-22）：compile 的**模块形态**重载（internal——公共 API 契约零动；仅供模块编译入口调用）。
 * 挂载模块快照按别名入表，限定引用语义层消歧（提案 §2.3）。 */
internal fun compile(
    file: FileAst,
    parseNotes: Map<AstNode, List<ParseNote>> = emptyMap(),
    scriptMode: Boolean = false,
    noDegrade: Boolean = false,
    noLift: Boolean = false,
    mounted: Map<String, ModuleSnapshot> = emptyMap(),
): Pair<HirFile, DiagBag> {
    val report = compileWithRounds(file, parseNotes, scriptMode, noDegrade, noLift, mounted)
    return report.hir to report.diag
}

/** t117 R 轮重跑的单轮结果 + 观测（附录 B 修订口径的驱动层载体）。 */
internal class InferenceRound(
    val hir: HirFile,
    val diag: DiagBag,
    val solutions: Map<MetaSlotKey, MetaSolution>,
    val metaCount: Int,
    val converged: Boolean,
    val changedOwners: Set<String>,
    /** t118：上一轮→本轮仍在变的空位（R-02 退化空位集——按空位/按函数局部退化，不牵连全单元）。 */
    val changedSolutions: Map<MetaSlotKey, MetaSolution>,
    val fuelMaxConsumed: Int,
    /** t122（R-16 A2）：不纯闭包单轮规模（v+e，O(v+E) 单列——提案 OQ-9 观测）。 */
    val closureSize: Int,
    /** t125（S5b / R-06）：本轮登记的可变性提升需求（声明位键 → 赋值位证据清单）——驱动器跨轮唯一通道。 */
    val mutPromotions: Map<String, List<SourceRange>> = emptyMap(),
    /** t126（S5c）：提升需求的触发分类账（T1/T2/T3）——驱动器跨轮唯一通道。 */
    val mutPromotionTriggers: Map<String, String> = emptyMap(),
)

/** t117（附录 B H3）：R 池按轮 label 观测条目——每轮一全新 FUEL_DEFAULT 池（每轮新 Elaborator）；
 *  分布口径供 OQ-2 终裁（不收敛/预算形态落表）。 */
internal data class RoundObservation(
    val round: Int,
    val label: String,
    val metaCount: Int,
    val fuelMaxConsumed: Int,
    val converged: Boolean,
    /** t122：闭包规模列（v+e;默认 0 兼容既有观察口径）。 */
    val closureSize: Int = 0,
)

/** t117（t112 §2.1 **按附录 B 修订口径**）：R 轮重跑驱动器。
 *  - **共享单调 NodeIdSource**（H1）：compile 既有唯一源跨轮复用——禁每轮 fresh（同 id 异节点=F1 病）；
 *    正面条款吸收：nid 布局依收敛轮数（同输入 ⇒ 同轮数 ⇒ 同 ids，确定性保留）。
 *  - **零元变量 ⇒ 单趟直通**（保护条款/真门）：首轮 metaCount==0 即停——零推导单元逐字同单趟
 *    （68 工程样本 SAME 门的机器证据）。
 *  - **解传递**（H2）：轮外驱动 Map（MetaSlotKey → MetaSolution）注入每轮 Elaborator，零改 SymbolTable。
 *  - **终轮诊断**：中间轮 diag 一律弃；未收敛处置走分型表（工程 E-INFER-NOT-CONVERGED 硬拒 /
 *    脚本 R-02 退化触发点——impure 化再跑一轮，Any 填充本体 t118）。
 *  - **sink 轮内接管**（H8）：shadowHintSink / nativeReclSink 挂轮内袋，编译级袋只供 collect 单趟。 */
internal fun compileWithRounds(
    file: FileAst,
    parseNotes: Map<AstNode, List<ParseNote>> = emptyMap(),
    scriptMode: Boolean = false,
    /** t118（OQ-5 队长裁=给）：--no-degrade——脚本模式「退化即失败」CI 门（--no-trust 族同设）。 */
    noDegrade: Boolean = false,
    noLift: Boolean = false,
    /** t131（S7 / R-22）：挂载模块只读快照透传（默认空——非模块编译零影响）。 */
    mounted: Map<String, ModuleSnapshot> = emptyMap(),
): RoundsReport {
    val nids = NodeIdSource()   // HIR 节点 id 唯一源（ADR-14）：跨轮共享单调（附录 B H1）
    val symbols = SymbolTable(nids::alloc)
    val collectDiag = DiagBag()
    // t39（ADR-27）：遮蔽提示 H-SHADOW（hint 族——遮蔽合法，仅留痕）——collect 期挂编译级袋
    val hintDiag = DiagBag()
    wireShadowHint(symbols, hintDiag)
    // t86（F6 收口）：原生命题名占用——声明通道中央守卫（文案按位置分化，码复用 E-NATIVE-REDECL）
    wireNativeRecl(symbols, collectDiag)
    symbols.collectFile(file, collectDiag)
    // t137（R-23）：stdlib 默认挂载件的自由函数名入非限定链符号面（preludeFun 数据源——PRELUDE_FUNS
    // 表已 retire；stdlib 件旗过滤：用户挂载件不经此通道，R-22 限定唯一口径不变）
    // t137（R-23）：**retire 替身名表**——裸名（非限定）链只吃 retire 迁源的自由函数（B0-0:
    // range/sum/summation）。B0-1 collection 全家经**限定引用通道**（optional.getOrElse 形态）——
    // 不入裸名链：e2e 冻红样本的 getOrElse/add/put 未解析态保持不变（67/67 零差分保证）。
    val retired = PreludeDecls.RETIRED_FUNS   // B0-0 数族（t137 表）；B2.1 构造族经 virtualFuns 注入面，不经本通道
    symbols.declareModuleFunNames(mounted.values.filter { it.stdlib }.flatMap { it.funs.keys }.filter { it in retired })
    // R 轮循环
    var injected: Map<MetaSlotKey, MetaSolution> = emptyMap()
    var round = 0
    val rounds = mutableListOf<RoundObservation>()
    var last: InferenceRound? = null
    while (round < INFER_ROUNDS_DEFAULT) {
        round++
        val label = "infer-r" + round
        val r = elaborateRound(file, symbols, parseNotes, nids, scriptMode, injected, label, noLift = noLift, mounted = mounted)
        rounds.add(RoundObservation(round, label, r.metaCount, r.fuelMaxConsumed, r.converged, r.closureSize))
        last = r
        if (r.metaCount == 0) break               // 零元变量：单趟直通（保护条款）
        if (r.converged) break                    // 首个收敛轮停——保留终轮 HIR + 诊断
        injected = r.solutions
    }
    val finalRound = last!!
    val diag = DiagBag()
    diag.absorb(collectDiag)
    diag.absorb(hintDiag)
    var hir = finalRound.hir
    var notConverged = false
    var degraded = false
    // t117 分型表 §3.1 / t118 R-02 通道本体：R 轮未收敛处置
    if (!finalRound.converged && finalRound.metaCount > 0) {
        notConverged = true
        // 首诊断位（声明表首条目 pos 兜底）
        val firstPos = (file.entries.firstOrNull() as? sublimation.frontend.ast.DeclEntry)?.decl?.pos
            ?: (file.entries.firstOrNull() as? sublimation.frontend.ast.StmtEntry)?.stmt?.pos
            ?: SourceRange(SourcePos(0, 0), null)
        if (!scriptMode) {
            // 工程：E-INFER-NOT-CONVERGED 硬拒（P-11 §3.5——多半递归/多态递归，建议加标注打破环）
            diag.absorb(finalRound.diag)
            diag.error("E-INFER-NOT-CONVERGED", firstPos,
                "推导未收敛：R=" + INFER_ROUNDS_DEFAULT + " 轮后仍未到不动点（" +
                    finalRound.changedOwners.sorted().joinToString("、") + " 的推导位仍在变）。" +
                    "多半是递归/多态递归引起的类型环——建议在声明位加一处类型标注打破环（P-11 §3.5）")
        } else {
            // R-02 退化通道本体（脚本模式，提案 §3.3 锚点）：**不收敛/不可定 且非最小解矛盾**的空位填 Any
            // （t108 槽位 inferred=false）+ 所属函数 impure 化（t117 触发点：t97 停机闸跳过 + t96 纯度面记账）+
            // D-INFER-DEGRADED 观察级（函数+空位+证据尝试清单+退化原因分类，随轮内 DiagBag 落袋）。
            // 无解空位（lub ⊄ U ⇒ E-INFER-NO-SOLUTION）**不退化**——健全性优先（双模式硬拒，不可退化）。
            // 局部退化口径：仅仍在变的空位换 Any，其余空位/函数按上轮终解继续闭合（不牵连全单元）。
            val anyT = sublimation.frontend.hir.HirNamedType("Any", emptyList(), null, nids.alloc(), SYNTHETIC_POS, emptyList())
            val degSeeds = finalRound.solutions.mapValues { (k, v) ->
                val degradedSlot = k in finalRound.changedSolutions && !v.unsolvable
                if (degradedSlot) MetaSolution(anyT, v.evidence, v.owner, degraded = true) else v
            }
            if (noDegrade) {
                // OQ-5（队长裁=给）：--no-degrade 门——脚本退化即失败（CI 可用性门，--no-trust 族同设）
                hir = finalRound.hir
                diag.absorb(finalRound.diag)
                diag.error("E-INFER-DEGRADE-FORBIDDEN", firstPos,
                    "脚本模式发生 R-02 退化（" + finalRound.changedOwners.sorted().joinToString("、") +
                        " 的推导位 R=" + INFER_ROUNDS_DEFAULT + " 轮未收敛），--no-degrade 门要求退化即失败——" +
                        "建议在声明位加一处类型标注打破类型环（P-11 §3.5）")
            } else {
                val deg = elaborateRound(file, symbols, parseNotes, nids, scriptMode, degSeeds, "infer-degraded",
                    finalRound.changedOwners, noLift = noLift, mutPromoted = finalRound.mutPromotions,
                    mutPromotedTriggers = finalRound.mutPromotionTriggers, mounted = mounted)
                rounds.add(RoundObservation(round + 1, "infer-degraded", deg.metaCount, deg.fuelMaxConsumed, deg.converged, deg.closureSize))
                hir = deg.hir
                degraded = true
                diag.absorb(finalRound.diag)   // 终轮诊断（无解线等）不因退化丢弃（dedupe 去重两轮同文）
                diag.absorb(deg.diag)
            }
        }
    } else {
        diag.absorb(finalRound.diag)
    }
    // t125（S5b / R-06）：脚本模式可变性提升重跑——终轮有提升需求 ⇒ 单轮预应用重跑（声明先于赋值 +
    //  单调 ⇒ 一轮收敛;静态键同输入同需求 ⇒ 轮次确定性 P-11 §9.3）。退化（R-02）已并行携带 mutPromoted。
    //  工程模式 mutPromotions 恒空（零触发——R-06 工程侧声明制，SAME 门机械保护）。
    var mutPromotedRound = false
    if (scriptMode && last!!.mutPromotions.isNotEmpty() && !degraded) {
        val mr = elaborateRound(file, symbols, parseNotes, nids, true, last.solutions, "mut-promoted",
            last.changedOwners, noLift = noLift, mutPromoted = last.mutPromotions,
            mutPromotedTriggers = last.mutPromotionTriggers, mounted = mounted)
        rounds.add(RoundObservation(round + 1, "mut-promoted", mr.metaCount, mr.fuelMaxConsumed, mr.converged, mr.closureSize))
        hir = mr.hir
        mutPromotedRound = true
        diag.absorb(mr.diag)
    }
    diag.dedupe()
    return RoundsReport(hir, diag, rounds.toList(), round, notConverged, degraded, mutPromotedRound, finalRound.changedOwners)
}

/** t117（附录 B H8）：sink 下挂——文案逐字不变（collect 期=编译级袋；轮内=轮袋，逐轮重复落袋消除）。 */
/** t153（D-SHADOW, supplement 族——编译器自动活告示，一律不拦）：外层局部/形参被遮蔽时下挂。 */
internal fun wireShadowHint(symbols: SymbolTable, diag: DiagBag) {
    symbols.shadowHintSink = { name, what, where, at ->
        diag.hint("H-SHADOW", at,
            "这里的 " + ident(name) + " 遮蔽了" + where + "的同名" + what +
                "：本作用域内只会解析到这里的声明，外层同名者不可见（不回退）。")
    }
    symbols.shadowSuppSink = { name, at ->
        diag.supplement("D-SHADOW", at,
            "这里的 " + ident(name) + " 遮蔽了外层同名局部/形参——遮蔽语义将生效：本作用域内解析到此声明，" +
                "外层同名者不可见（不回退）。遮蔽合法（自动解析），此为解析路径留痕")
    }
}

/** t117（附录 B H8）：原生命题占用守卫下挂（文案逐字不变，t86）。 */
internal fun wireNativeRecl(symbols: SymbolTable, diag: DiagBag) {
    symbols.nativeReclSink = { name, arity, what, at ->
        diag.error("E-NATIVE-REDECL", at,
            ident(name) + " 是编译器内置的原生命题（" + arity + " 元），" + what)
    }
}

/** t64（ADR-41，P-10 步骤 3）：compile + 变构性表——推断器在声明索引就位后惰性建表（一次不动点），
 *  本入口仅供工具/测试观察声明级变构对（生产路径用 compile）。 */
internal data class VarianceReport(
    val hir: HirFile,
    val diag: DiagBag,
    val variance: Map<String, List<Pair<String, VariancePair>>>,
)
internal fun compileVariance(
    file: FileAst, parseNotes: Map<AstNode, List<ParseNote>> = emptyMap(),
    /** t138（R-23 B1）：stdlib 挂载透传（retire 后虚拟层类型经快照注入）。 */
    mounted: Map<String, ModuleSnapshot> = emptyMap(),
): VarianceReport {
    val nids = NodeIdSource()
    val symbols = SymbolTable(nids::alloc)
    val collectDiag = DiagBag()
    symbols.collectFile(file, collectDiag)
    val (hir, elaborateDiag, table) = elaborateWithVariance(file, symbols, parseNotes, nids, mounted = mounted)
    val diag = DiagBag()
    diag.absorb(collectDiag)
    diag.absorb(elaborateDiag)
    diag.dedupe()
    return VarianceReport(hir, diag, table)
}

/** t66（ADR-43，ST-10.2）：compile + 收窄审计——证明尝试次数与信任基（生产路径用 compile）。
 *  观察量仅供工具/测试断言严格触发与信任审计衔接（验收 #7/#8/#9）。 */
internal data class NarrowReport(
    val hir: HirFile,
    val diag: DiagBag,
    val audit: NarrowAudit,
)
internal fun compileNarrowAudit(
    file: FileAst, parseNotes: Map<AstNode, List<ParseNote>> = emptyMap(),
): NarrowReport {
    val nids = NodeIdSource()
    val symbols = SymbolTable(nids::alloc)
    val collectDiag = DiagBag()
    symbols.collectFile(file, collectDiag)
    val (hir, elaborateDiag, audit) = elaborateWithNarrowAudit(file, symbols, parseNotes, nids)
    val diag = DiagBag()
    diag.absorb(collectDiag)
    diag.absorb(elaborateDiag)
    diag.dedupe()
    return NarrowReport(hir, diag, audit)
}
