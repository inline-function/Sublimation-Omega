package sublimation.frontend.cli

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.json.HirJson
import sublimation.frontend.module.ModuleNode
import sublimation.frontend.module.ModuleScanner
import sublimation.frontend.module.ModuleSnapshot
import sublimation.frontend.module.ModuleSnapshots
import sublimation.frontend.parser.ParseResult
import sublimation.frontend.diag.SourcePos
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.module.E_MOUNT_MISSING
import sublimation.frontend.module.MountedModule
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
    /** t137（R-23）：--no-stdlib——stdlib 默认挂载关闭（裸名 sum/range/summation 落 E-UNBOUND-NAME 的 CI 门）。 */
    noStdlib: Boolean = false,
): ModuleOutput {
    val mountDiag = DiagBag()
    val tree = ModuleScanner(rootDir, mountDiag).scan()
    // t137（R-23）：stdlib 默认挂载——stdlib 根 .settings 的挂载约定**并入编译根挂载表**（用户挂载优先：
    // 同名别名时用户 .settings 条目赢——P-7 口径延伸；stdlib 件编译一次，快照由父域编译共享）。
    val (stdlibUnits, stdlibDiag) = compileStdlibUnits(noStdlib, scriptMode, noDegrade, noLift)
    if (!noStdlib) {
        val userAliases = tree.mounts.map { it.alias }.toMutableSet()
        stdlibUnits.forEach { su ->
            if (su.alias !in userAliases) {
                userAliases += su.alias
                tree.mounts += MountedModule(su.alias, su.node, su.mountPos)
            }
        }
    }
    if (mountDiag.hasError || stdlibDiag.hasError) {
        // 挂载期早死：挂载作废——主模块零挂载单编译，挂载诊断（含 stdlib 挂载期）并入主 envelope
        val earlyDiag = DiagBag()
        earlyDiag.absorb(mountDiag); earlyDiag.absorb(stdlibDiag)
        val main = compileUnit(tree, tree.name, emptyMap(), scriptMode, noDegrade, noLift, earlyDiag)
        return ModuleOutput(main, tree.moduleSource(), emptyList())
    }
    // t137：stdlib 件目录集——compileNode 建快照时标 stdlib 旗（用户根重编译 stdlib 件时不丢旗）
    val stdlibDirs = stdlibUnits.map { it.node.dir.absoluteFile.normalize().path }.toSet()
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
            mounted[m.alias] = ModuleSnapshots.fromAst(m.node.name, m.alias, child.pr.file,
                stdlib = m.node.dir.absoluteFile.normalize().path in stdlibDirs)
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

/**
 * t137（R-23）：stdlib 默认挂载件——stdlib 根 .settings 的挂载约定（mount 条目，路径相对 stdlib 根）
 * 编译为只读快照（stdlib=true 旗）。三处消费：
 * - 目录形态：件节点并入编译根挂载表（用户 .settings 同名别名优先——P-7 延伸），件 envelope 落侧产物；
 * - 单文件形态：快照经 mounted 注入语义层（非限定链符号面 = preludeFun 数据源）；
 * - 挂载期红（缺件/环/空件）→ DiagBag 并入主 envelope（早死口径同 E-MOUNT-* 族）。
 * stdlib 根自身 = 纯挂载容器（零源——ModuleScanner(requireRootSource=false)，不编译、不入产物）。 */
internal data class StdlibUnit(
    val alias: String,
    val node: ModuleNode,
    val mountPos: SourceRange,
    val unit: ModuleUnit,
    val snapshot: ModuleSnapshot,
    val pr: ParseResult,
)

/** stdlib 默认挂载编译（shared：目录/单文件两入口共用）。 */
internal fun compileStdlibUnits(
    noStdlib: Boolean, scriptMode: Boolean, noDegrade: Boolean, noLift: Boolean,
): Pair<List<StdlibUnit>, DiagBag> {
    val diag = DiagBag()
    if (noStdlib) return emptyList<StdlibUnit>() to diag
    val root = stdlibRoot()
    if (root == null) {
        diag.error(E_MOUNT_MISSING, SourceRange(SourcePos(1, 1), null),
            "stdlib 根未找到：默认挂载的工作区 stdlib/ 缺失（标准库未安装；SUBLIMATION_STDLIB 环境变量可改指，--no-stdlib 关闭默认挂载）")
        return emptyList<StdlibUnit>() to diag
    }
    val stdTree = ModuleScanner(root, diag, requireRootSource = false).scan()
    if (diag.hasError) return emptyList<StdlibUnit>() to diag
    val compiled = LinkedHashMap<String, StdlibUnit>()
    fun compileNode(node: ModuleNode, rel: String): StdlibUnit {
        val key = node.dir.absoluteFile.normalize().path
        compiled[key]?.let { return it }
        val mounted = LinkedHashMap<String, ModuleSnapshot>()
        for (m in node.mounts) {
            val child = compileNode(m.node, if (node.isRoot) m.node.name else "$rel/${m.node.name}")
            mounted[m.alias] = child.snapshot
        }
        val unitPath = rel
        val raw = compileOne(node, unitPath, mounted, scriptMode, noDegrade, noLift)
        val unit = StdlibUnit(
            node.alias, node, SourceRange(SourcePos(1, 1), null),
            ModuleUnit(unitPath, raw.output, node.moduleSource()),
            ModuleSnapshots.fromAst(node.name, node.alias, raw.pr.file, stdlib = true),
            raw.pr,
        )
        compiled[key] = unit
        return unit
    }
    val units = stdTree.mounts.map { mm -> compileNode(mm.node, mm.node.name).copy(mountPos = mm.mountPos) }
    return units to diag
}

/**
 * t137（R-23）：stdlib 根定位——系统属性 sublimation.stdlib > SUBLIMATION_STDLIB 环境变量 >
 * 工作区 stdlib/ > 向上四级搜索
 * （gradle :frontend:test 的工作目录在 frontend/——向上探到工程根；e2e/CI 脚本在工程根直命中）。 */
internal fun stdlibRoot(): File? {
    // t137：系统属性（sublimation.stdlib）优先于环境变量——测试期可改指（CI 亦可注入）
    val prop = System.getProperty("sublimation.stdlib")
    val env = System.getenv("SUBLIMATION_STDLIB")
    val direct = when { prop != null -> File(prop); env != null -> File(env); else -> File("stdlib") }
    if (direct.isDirectory) return direct.absoluteFile.normalize()
    if (prop != null || env != null) return null   // 显式指定=权威：非目录即缺件（E-MOUNT-MISSING 负例口径）
    var cur = File(".").absoluteFile
    repeat(4) {
        val cand = File(cur, "stdlib")
        if (cand.isDirectory) return cand.absoluteFile.normalize()
        cur = cur.parentFile ?: return null
    }
    return null
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
