package sublimation.frontend.module

import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.SourcePos
import sublimation.frontend.diag.SourceRange
import java.io.File

/**
 * S7（t131 / R-22 裁决）：模块系统——目录即模块 + .settings 显式挂载层。
 *
 * - 挂载不是源码语句（硬约束：语言无 import 产生式、Parser 无 ImportStmt）——本层位于解析**前**，
 *   由编译入口驱动：扫目录树 + 解析 .settings → ModuleTree（只读拓扑，全管线共享）。
 * - 层位（提案 §1.4 / OQ-1 队长裁）：module → diag（+ java.io），被编译入口依赖；不依赖 parser/semantic。
 * - 挂载期错误族（挂载期预判早死——提案 OQ-7 口径）：E-MOUNT-SYNTAX（行文法）、E-MOUNT-OUT-OF-ROOT
 *   （逃逸编译根）、E-MOUNT-CYCLE（挂载环）、E-MOUNT-DUP-ALIAS（别名撞）、E-MOUNT-MISSING（目标非目录）、
 *   E-MODULE-EMPTY（模块体无 .subl）。
 * - nid 纪律：本层零 HIR 节点、零 nid 消费（挂载拓扑不入 HIR——R-22 模块挂载不得引入新 nid 面）。 */

/** .settings 一行挂载说明（解析产物；路径相对本 .settings 所在目录）。 */
internal data class MountSpec(val rawPath: String, val alias: String?, val pos: SourceRange)

/** 模块节点：目录树 ↔ 模块树（目录即模块——模块体 = 目录内全部 .subl 按名字序）。 */
internal class ModuleNode(
    /** 模块名 = 目录名（编译根的 name 无挂载语义——仅产物 unit.path 用）。 */
    val name: String,
    /** 挂载别名：as 显式给则用之，否则 = 目录名。 */
    val alias: String,
    val dir: File,
    /** 模块体源文件（目录内 .subl 按文件名字典序——确定性）。 */
    val sourceFiles: List<File>,
    val isRoot: Boolean = false,
) {
    /** 挂载的子模块（按 .settings 声明序）。 */
    val mounts: MutableList<MountedModule> = mutableListOf()
}

/** 一次挂载（别名 + 目标节点 + 挂载声明位置）。 */
internal data class MountedModule(val alias: String, val node: ModuleNode, val mountPos: SourceRange)

/**
 * .settings 行解析（非源码语句——独立行扫描，不进 Parser/AST）。
 * 文法：一行一挂载 mount <相对路径> [as <别名>]；# 为行注释、空行略。
 */
internal object MountSettings {

    fun parse(text: String, settingsFile: File, diag: DiagBag): List<MountSpec> {
        val out = mutableListOf<MountSpec>()
        var ln = 0
        for (raw in text.lineSequence()) {
            ln++
            val line = raw.trim()
            // 行注释双形态：#（.settings 约定，类 .gitignore）与 //（源码同构）
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
            val pos = SourceRange(SourcePos(ln, 1), null)
            val parts = line.split(Regex("[ \\t]+"))
            when {
                parts.size < 2 || parts[0] != "mount" -> diag.error(E_MOUNT_SYNTAX, pos,
                    "settings 行文法：须为 mount <路径> [as <别名>]（行首 mount + 路径；# 与 // 为行注释）")
                parts.size == 2 -> out += MountSpec(parts[1], null, pos)
                parts.size == 4 && parts[2] == "as" -> {
                    val alias = parts[3]
                    if (alias.isEmpty() || alias.contains("/") || alias.contains("\\") ||
                        alias.contains(".") || alias.contains(" "))
                        diag.error(E_MOUNT_SYNTAX, pos, "挂载别名须为裸标识符（无路径符）：$alias")
                    else out += MountSpec(parts[1], alias, pos)
                }
                else -> diag.error(E_MOUNT_SYNTAX, pos,
                    "settings 行文法：多余段——mount <路径> [as <别名>] 至多四段，当前 ${parts.size} 段")
            }
        }
        return out
    }
}

internal const val E_MOUNT_SYNTAX = "E-MOUNT-SYNTAX"
internal const val E_MOUNT_OUT_OF_ROOT = "E-MOUNT-OUT-OF-ROOT"
internal const val E_MOUNT_CYCLE = "E-MOUNT-CYCLE"
internal const val E_MOUNT_DUP_ALIAS = "E-MOUNT-DUP-ALIAS"
internal const val E_MOUNT_MISSING = "E-MOUNT-MISSING"
internal const val E_MODULE_EMPTY = "E-MODULE-EMPTY"

/**
 * 目录树扫描器：自编译根起，递归解析 .settings 挂载，建 ModuleNode 树。
 *
 * - 路径相对**本 .settings 所在目录**；目标须为**目录**且在**编译根内**（可审计拓扑边界，提案 OQ-11 裁：拒逃逸）。
 * - 挂载图须 DAG：当前挂载链上重访即环（钻石形重复挂载允许——编译侧按规范路径记一次，见编译入口备忘）。 */
/** requireRootSource：编译根本身是否须有 .subl（用户根=true；
 * t137（R-23）stdlib 根=纯挂载容器——零源自容，不报 E-MODULE-EMPTY）。 */
internal class ModuleScanner(private val root: File, private val diag: DiagBag,
                             private val requireRootSource: Boolean = true) {

    fun scan(): ModuleNode {
        val rootDir = root.absoluteFile.normalize()
        if (!rootDir.isDirectory) return ModuleNode(rootDir.name, rootDir.name, rootDir, emptyList(), isRoot = true)
        val rootNode = makeNode(rootDir, rootDir.name, isRoot = true)
        scanInto(rootNode, rootDir, ArrayDeque())
        return rootNode
    }

    private fun makeNode(dir: File, alias: String, isRoot: Boolean = false): ModuleNode {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".subl") }
            ?.sortedBy { it.name } ?: emptyList()
        if (files.isEmpty() && !(isRoot && !requireRootSource))
            diag.error(E_MODULE_EMPTY, SourceRange(SourcePos(1, 1), null),
                "模块 ${dir.name} 无 .subl 源文件（目录即模块——模块体须至少一个 .subl）")
        return ModuleNode(dir.name, alias, dir, files, isRoot)
    }

    private fun scanInto(node: ModuleNode, rootDir: File, chain: ArrayDeque<File>) {
        val settings = File(node.dir, ".settings")
        val specs = if (settings.isFile) MountSettings.parse(settings.readText(Charsets.UTF_8), settings, diag)
            else emptyList()
        chain.addLast(node.dir)
        val aliases = mutableSetOf<String>()
        for (spec in specs) {
            val target = File(node.dir, spec.rawPath).absoluteFile.normalize()
            when {
                !target.isDirectory -> diag.error(E_MOUNT_MISSING, spec.pos,
                    "挂载目标不是目录：${spec.rawPath}（目录即模块——须挂目录）")
                !target.path.startsWith(rootDir.path + File.separator) && target != rootDir ->
                    diag.error(E_MOUNT_OUT_OF_ROOT, spec.pos,
                    "挂载 ${spec.rawPath} 逃逸编译根（${rootDir.path} 以外不可挂——模块拓扑须可审计，提案 OQ-11）")
                chain.any { it.path == target.path } -> diag.error(E_MOUNT_CYCLE, spec.pos,
                    "挂载环：${spec.rawPath}（挂载链 ${chain.joinToString(" → ") { it.name }} 已含该目录——模块图须 DAG）")
                spec.alias != null && !aliases.add(spec.alias) -> diag.error(E_MOUNT_DUP_ALIAS, spec.pos,
                    "挂载别名 ${spec.alias} 重复（同模块内别名唯一——限定引用前缀须无歧义）")
                else -> {
                    val alias = spec.alias ?: target.name
                    val child = makeNode(target, alias)
                    node.mounts += MountedModule(alias, child, spec.pos)
                    scanInto(child, rootDir, chain)
                }
            }
        }
        chain.removeLast()
    }
}
