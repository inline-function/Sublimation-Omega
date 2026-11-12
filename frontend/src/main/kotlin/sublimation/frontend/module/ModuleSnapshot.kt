package sublimation.frontend.module

import sublimation.frontend.ast.DeclEntry
import sublimation.frontend.ast.FunDecl
import sublimation.frontend.ast.FileAst
import sublimation.frontend.hir.SymbolKind
import sublimation.frontend.hir.SymbolRef

/**
 * S7（t131 / R-22）：已编译模块的**只读符号快照**——父模块限定引用的查询面。
 *
 * - 提案 §4.1 / OQ-5 队长裁=独立 Elaborator 实例：每模块Own符号表，挂载侧不经合并入父表
 *   （限定引用是唯一跨模块通道——R-22 查询链「本模块 > 挂载子 > prelude」仅对**限定**形态生效）。
 * - 符号 id 按模块 AST 声明序确定分配（快照内唯一、跨编译稳定——跨父引用的 HIR 标识不漂移）。 */
internal class ModuleSnapshot(
    val name: String,
    val alias: String,
    /** 自由函数：名 →（符号引用 + AST 声明；签名核对经 sigOf 在父域解析——提案 §4.1）。 */
    val funs: Map<String, ModuleFun>,
) {
    fun lookupFun(name: String): ModuleFun? = funs[name]
}

internal data class ModuleFun(val symbol: SymbolRef, val decl: FunDecl)

internal object ModuleSnapshots {

    /**
     * 由模块体的 FileAst 建快照：顶层 fun 条目按声明序，符号 id 按序（零 nid 消费——SymbolRef
     * 是跨模块引用标识，与 NodeIdSource 无关；R-22 nid 纪律）。 */
    fun fromAst(name: String, alias: String, file: FileAst): ModuleSnapshot {
        val funs = LinkedHashMap<String, ModuleFun>()
        var nextId = 0
        for (entry in file.entries) {
            val fn = (entry as? DeclEntry)?.decl as? FunDecl ?: continue
            if (fn.receiver != null) continue      // 扩展函数不进快照值域（R-22：扩展函数唯限定通道，V1 不通）
            funs[fn.name] = ModuleFun(SymbolRef(nextId++, SymbolKind.FUN, fn.name), fn)
        }
        return ModuleSnapshot(name, alias, funs)
    }
}
