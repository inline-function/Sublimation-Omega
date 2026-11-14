package sublimation.frontend.cli

import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirFile
import sublimation.frontend.json.HirJson
import sublimation.frontend.parser.ParseResult
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.semantic.compile

/**
 * 一次前端编译产物（S-2 §2 对外契约）：HIR + 诊断袋 + JSON 产物文本。
 */
data class FrontendOutput(
    val hir: HirFile,
    val diag: DiagBag,
    val json: String,
)

/**
 * 单源文件一次编译：源代码 → 词法 → AST → HIR → JSON（S-2 §2；S-1 管线四层一气呵成）。
 *
 * 契约：
 * - 诊断一袋汇总（词法 + 语法 + 语义，层间总契约 2）；解析期 ParseNote 由语义层搬入
 *   HIR 节点 notes（D-9），不入诊断袋；
 * - 半成品原则（ADR-1 §3.2）：即使存在 ERROR 级诊断，也返回完整 HIR 树与 JSON
 *   （envelope 的 ok 旗标=false 携带判定结论，N-SEV-BLOCK）——产物是诊断可回链的
 *   信息载体，不只是成功的纪念品；
 * - 输入→输出可复现（层间总契约 3）：相同源码与模式必产相同 JSON。
 */
fun compileFrontend(
    source: String, unitPath: String, scriptMode: Boolean = false,
    /** t118（OQ-5）：--no-degrade——脚本模式「退化即失败」CI 门（Main.kt 旗标透传）。 */
    noDegrade: Boolean = false,
    /** t123（R-16 S4 / OQ-5）：--no-lift——脚本模式「上文上提即失败」CI 门（同 --no-degrade 双口形态）。 */
    noLift: Boolean = false,
): FrontendOutput = compileFrontendImpl(source, unitPath, scriptMode, noDegrade, noLift)

/**
 * 带 **stdlib 默认挂载**的一次编译入口（t165 / P-L1，LSP 前置件，LSP-框架大纲 §4.4 裁决）。
 *
 * 与 [compileFrontend] 的差异只在挂载面：本入口按 CLI 单文件口径装配 stdlib 默认挂载
 * （stdlib 根定位 → [compileStdlibUnits] 编为只读快照 → 经 mounted 注入语义层 + 挂载期
 * 诊断并入主袋），使**进程内嵌入方**（LSP 语言服务）与 CLI 走**同一通道**，不复刻装配——
 * 复刻漂移教训（动机注释）：旧 LSP 自行拼接 core 目录与编译 CLI 口径分家（旧工程
 * LspAnalysis.kt :349）；手工复刻若漏挂载期诊断还会丢 E-MOUNT-MISSING（T165 门 4 负例）。
 *
 * 契约：
 * - [noStdlib] = true ⇒ 零挂载（此时逐字等价 [compileFrontend]，裸名 sum/range 红，
 *   旗门同 CLI --no-stdlib 的 CI 口径，T137StdlibB0Test 门 3）；
 * - 挂载期红（E-MOUNT-MISSING 等）并入主袋：ok=false 且半成品产物照常返回（ADR-1 §3.2）；
 * - 输入→输出可复现（层间总契约 3）：相同源码 + 相同 stdlib 根 ⇒ 相同 JSON。
 *
 * 形态约束（动机注释）：不暴露 mounted 参数为公共形态——ModuleSnapshot 为 internal 类型
 * （t137），公共形态只能取旗标；internal 全参数重载 [compileFrontendImpl] 仍是装配的
 * 单一落点（R-23 口径不变）。
 */
fun compileFrontendWithStdlib(
    source: String, unitPath: String, scriptMode: Boolean = false,
    noDegrade: Boolean = false,
    noLift: Boolean = false,
    /** t165（P-L1）：stdlib 默认挂载关闭门（同 CLI --no-stdlib；false=挂载，CLI 默认口径）。 */
    noStdlib: Boolean = false,
): FrontendOutput {
    val (stdlibUnits, stdlibDiag) = compileStdlibUnits(noStdlib, scriptMode, noDegrade, noLift)
    val mounted = stdlibUnits.associate { it.alias to it.snapshot }
    return compileFrontendImpl(source, unitPath, scriptMode, noDegrade, noLift, mounted, stdlibDiag)
}

/** t137（R-23）：单文件入口的**内部全参数**实现（stdlib 默认挂载快照 + 件诊断并入——
 * ModuleSnapshot 为 internal 类型：公共 5 参数契约零动，本 internal 重载承载挂载面）。 */
internal fun compileFrontendImpl(
    source: String, unitPath: String, scriptMode: Boolean,
    noDegrade: Boolean, noLift: Boolean,
    mounted: Map<String, ModuleSnapshot> = emptyMap(),
    extraDiag: DiagBag = DiagBag(),
): FrontendOutput {
    val pr: ParseResult = if (scriptMode) {
        parseScriptSourceWithNotes(source, unitPath)
    } else {
        parseSourceWithNotes(source, unitPath)
    }
    val diag = DiagBag()
    diag.absorbAll(pr.diag)
    val (hir, semDiag) = compile(pr.file, pr.notes, scriptMode, noDegrade, noLift, mounted)
    diag.absorb(extraDiag)
    diag.absorb(semDiag)
    val json = HirJson.emit(hir, diag, unitPath, scriptMode)
    return FrontendOutput(hir, diag, json)
}

/** 把诊断列表按原严重度并入诊断袋（保序；ParseResult.diag → DiagBag 的桥）。 */
internal fun DiagBag.absorbAll(ds: List<Diag>) {
    for (d in ds) when (d.severity) {
        Severity.ERROR -> error(d.code, d.pos, d.message, d.nid)
        Severity.WARN -> warn(d.code, d.pos, d.message, d.nid)
        Severity.HINT -> hint(d.code, d.pos, d.message, d.nid)
        Severity.SUPPLEMENT -> supplement(d.code, d.pos, d.message, d.nid)
    }
}
