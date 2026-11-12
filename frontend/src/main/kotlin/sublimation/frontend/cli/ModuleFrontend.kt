package sublimation.frontend.cli

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.json.HirJson
import sublimation.frontend.module.ModuleNode
import sublimation.frontend.module.ModuleScanner
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.module.ModuleSnapshots
import sublimation.frontend.parser.ParseResult
import sublimation.frontend.parser.parseScriptSourceWithNotes
import sublimation.frontend.parser.parseSourceWithNotes
import sublimation.frontend.semantic.compile
import java.io.File

/**
 * t131（S7 / R-22 裁决落地）：**目录即模块**编译——.settings 挂载层 + 限定引用语义层消歧的编排层。
 *
 * 编排契约（提案 §1-§2）:
 * - 挂载树由 ModuleScanner 自编译根扫目录 + 解析 .settings 建成（**解析前**——限定引用消歧需模块表先立）;
 * - 编译序 = 挂载树 DAG 后序（子模块先编，产只读快照 ModuleSnapshot——OQ-5 队长裁=独立 Elaborator 实例），
 *   钻石形重复挂载按规范路径备忘（同规范路径只编译一次、快照共享）;
 * - 模块体 = 目录内全部 .subl 按名字序**串行拼接**为单源（提案 §1.2「串行入域」——单 ParseResult
 *   × 单 compile × 单 envelope；跨文件行号随拼接连续累加，unit.path 标识模块整体）;
 * - 产物：主模块 envelope 落 -o 位，挂载子模块落 `<o 基名>__<模块相对路径>.json`（e2e 双下划线命名同构）。
 *
 * 挂载期早死（提案 OQ-7）：E-MOUNT-* / E-MODULE-EMPTY 任一命中即**挂载作废**——主模块按零挂载单编译，
 * 挂载诊断并入主 envelope（红态仍落盘——D-9 半成品原则）。 */

/** 模块编译单元产物（envelope 单元——unit.path + 对外契约 FrontendOutput）。 */
internal data class ModuleUnit(
    /** 模块相对路径（主 = 根目录名；子 = 自根的目录相对路径，/ 分隔）。 */
    val unitPath: String,
    val output: FrontendOutput,
    /** 模块体串行拼接源（诊断上下文渲染用）。 */
    val source: String,
)

/** 目录编译产物。 */
internal data class ModuleOutput(
    val main: FrontendOutput,
    /** 主模块体串行拼接源（诊断上下文渲染用）。 */
    val mainSource: String,
    /** 全部挂载子模块（递归；钻石形共享按规范路径只列一次）。 */
    val children: List<ModuleUnit>,
) {
    val ok: Boolean get() = !main.diag.hasError && children.all { !it.output.diag.hasError }
}

internal fun compileModuleFrontend(
    rootDir: File,
    scriptMode: Boolean = false,
    noDegrade: Boolean = false,
    noLift: Boolean = false,
): ModuleOutput {
    val mountDiag = DiagBag()
    val tree = ModuleScanner(rootDir, mountDiag).scan()
    if (mountDiag.hasError) {
        // 挂载期早死：挂载作废——主模块零挂载单编译，挂载诊断并入主 envelope
        val main = compileUnit(tree, tree.name, emptyMap(), scriptMode, noDegrade, noLift, mountDiag)
        return ModuleOutput(main, tree.moduleSource(), emptyList())
    }
    // DAG 后序编译 + 规范路径备忘（钻石形挂载共享）
    data class Ctx(val unit: ModuleUnit, val pr: ParseResult)
    val compiled = LinkedHashMap<String, Ctx>()
    fun compileNode(node: ModuleNode, rel: String): Ctx {
        val key = node.dir.absoluteFile.normalize().path
        compiled[key]?.let { return it }
        val mounted = LinkedHashMap<String, ModuleSnapshot>()
        for (m in node.mounts) {
            val childRel = if (node.isRoot) m.node.name else "$rel/${m.node.name}"
            val child = compileNode(m.node, childRel)
            mounted[m.alias] = ModuleSnapshots.fromAst(m.node.name, m.alias, child.pr.file)
        }
        val unitPath = if (node.isRoot) node.name else rel
        val raw = compileOne(node, unitPath, mounted, scriptMode, noDegrade, noLift)
        val ctx = Ctx(ModuleUnit(unitPath, raw.output, node.moduleSource()), raw.pr)
        compiled[key] = ctx
        return ctx
    }
    val mainCtx = compileNode(tree, "")
    val children = compiled.values.filter { it.unit.unitPath != mainCtx.unit.unitPath }
        .distinctBy { it.unit.unitPath }
    return ModuleOutput(mainCtx.unit.output, mainCtx.unit.source, children.map { it.unit })
}

/** Single-module result: parse + compile + emit （单文件入口口径 + mounted 快照）。 */
internal data class SingleModule(val pr: ParseResult, val output: FrontendOutput)

/**
 * 单模块编译：模块体源 = 目录内 .subl 按名字序串行拼接（行号连续累加——单 ParseResult 口径）；
 * [extraDiag] 收挂载期诊断（早死兜底——并入单元袋后重新 emit）。 */
private fun compileUnit(
    node: ModuleNode, unitPath: String,
    mounted: Map<String, ModuleSnapshot>,
    scriptMode: Boolean, noDegrade: Boolean, noLift: Boolean,
    extraDiag: DiagBag = DiagBag(),
): FrontendOutput {
    val raw = compileOne(node, unitPath, mounted, scriptMode, noDegrade, noLift)
    if (!extraDiag.hasError) return raw.output
    val bag = DiagBag(); bag.absorb(extraDiag); bag.absorb(raw.output.diag)
    return FrontendOutput(raw.output.hir, bag,
        HirJson.emit(raw.output.hir, bag, unitPath, scriptMode))
}

/** 模块体源：目录内 .subl 按文件名字典序串行拼接（行号连续累加——单 ParseResult 口径）。 */
private fun ModuleNode.moduleSource(): String =
    sourceFiles.joinToString("\n") { it.readText(Charsets.UTF_8) }

private fun compileOne(
    node: ModuleNode, unitPath: String,
    mounted: Map<String, ModuleSnapshot>,
    scriptMode: Boolean, noDegrade: Boolean, noLift: Boolean,
): SingleModule {
    val source = node.moduleSource()
    val pr: ParseResult = if (scriptMode) parseScriptSourceWithNotes(source, unitPath)
        else parseSourceWithNotes(source, unitPath)
    val diag = DiagBag()
    diag.absorbAll(pr.diag)
    val (hir, semDiag) = compile(pr.file, pr.notes, scriptMode, noDegrade, noLift, mounted)
    diag.absorb(semDiag)
    val json = HirJson.emit(hir, diag, unitPath, scriptMode)
    return SingleModule(pr, FrontendOutput(hir, diag, json))
}

/**
 * 子模块产物 JSON 文件名：`<o 基名>__<模块相对路径>.json`（路径分隔转双下划线——e2e 命名同构）。
 * 例：基名 examples__模块系统.json + 子模块 tool → examples__模块系统__tool.json。 */
internal fun childJsonName(baseJson: String, relPath: String): String =
    baseJson.removeSuffix(".json") + "__" + relPath.replace("/", "__").replace("\\", "__") + ".json"
