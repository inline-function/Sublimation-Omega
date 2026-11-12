package sublimation.frontend.module

import sublimation.frontend.ast.AtomicType
import sublimation.frontend.ast.DeclEntry
import sublimation.frontend.ast.FunDecl
import sublimation.frontend.ast.FileAst
import sublimation.frontend.ast.MutType
import sublimation.frontend.ast.NamedType
import sublimation.frontend.ast.QualifiedType
import sublimation.frontend.ast.RefType
import sublimation.frontend.ast.StructDecl
import sublimation.frontend.ast.TypeAliasDecl
import sublimation.frontend.ast.Type as AstType
import sublimation.frontend.hir.SymbolKind
import sublimation.frontend.hir.SymbolRef

/**
 * S7（t131 / R-22）：已编译模块的**只读符号快照**——父模块限定引用的查询面。
 *
 * - 提案 §4.1 / OQ-5 队长裁=独立 Elaborator 实例：每模块 Own 符号表，挂载侧不经合并入父表
 *   （限定引用是唯一跨模块通道——R-22 查询链「本模块 > 挂载子 > prelude」仅对**限定**形态生效）。
 * - 符号 id 按模块 AST 声明序确定分配（快照内唯一、跨编译稳定——跨父引用的 HIR 标识不漂移）。
 * - t134（S7 快照扩类型面 / R-23）：扩 **别名/结构体/扩展函数** 三面（形态 C 类型面迁源前置件）。
 *   类型面经父域**声明索引注入**（structDecls/aliasDecls，同 prelude 虚拟声明通道——P-7 用户覆盖）；
 *   扩展函数经**方法糖查询链兜底**（本模块 > 挂载子 > prelude 虚拟扩展，Elaborator.sugarExt）。
 */
internal class ModuleSnapshot(
    val name: String,
    val alias: String,
    /** 自由函数：名 →（符号引用 + AST 声明；签名核对经 sigOf 在父域解析——提案 §4.1）。 */
    val funs: Map<String, ModuleFun>,
    /** t134：顶层类型别名（名 → 符号引用 + AST 声明；父域注入后按名展开——变体名在父域解析）。 */
    val aliases: Map<String, ModuleAlias>,
    /** t134：顶层结构体（名 → 符号引用 + AST 声明；父域注入后兼作构造器形状源）。 */
    val structs: Map<String, ModuleStruct>,
    /** t134：扩展函数（接收者头 → 名 → 符号引用 + AST 声明；方法糖查询链兜底层）。 */
    val exts: Map<String, Map<String, ModuleExt>>,
    /** t137（R-23）：stdlib 默认挂载件旗标——非限定链符号面（preludeFun 数据源）只吃 stdlib 件；
     *  用户件走 R-22 限定唯一通道，裸名不泄漏。默认 false（零侵入非 stdlib 快照）。 */
    val stdlib: Boolean = false,
) {
    fun lookupFun(name: String): ModuleFun? = funs[name]

    /** t134：扩展函数按（接收者头, 名）查——方法糖兜底通道的唯一查询面。 */
    fun lookupExt(receiverHead: String, name: String): ModuleExt? = exts[receiverHead]?.get(name)
}

internal data class ModuleFun(val symbol: SymbolRef, val decl: FunDecl)

/** t134：模块类型别名面（符号引用 kind=ALIAS，id 按模块声明序——零 nid 消费）。 */
internal data class ModuleAlias(val symbol: SymbolRef, val decl: TypeAliasDecl)

/** t134：模块结构体面（符号引用 kind=STRUCT；构造器通道经父域 structDecls 注入）。 */
internal data class ModuleStruct(val symbol: SymbolRef, val decl: StructDecl)

/** t134：模块扩展函数面（符号引用 kind=FUN；接收者头=ExtFunHeads 口径的模块内镜像）。 */
internal data class ModuleExt(val symbol: SymbolRef, val decl: FunDecl)

internal object ModuleSnapshots {

    /**
     * 由模块体的 FileAst 建快照：顶层声明按声明序进四表（自由函数/别名/结构体/扩展函数），
     * 符号 id 按序（零 nid 消费——SymbolRef 是跨模块引用标识，与 NodeIdSource 无关；R-22 nid 纪律）。
     * 同名取后者（模块内重复落 E-DUP-DECL 于模块自身编译——快照为符号视图，红模块的父域门由编排层把关）。 */
    fun fromAst(name: String, alias: String, file: FileAst, stdlib: Boolean = false): ModuleSnapshot {
        val funs = LinkedHashMap<String, ModuleFun>()
        val aliases = LinkedHashMap<String, ModuleAlias>()
        val structs = LinkedHashMap<String, ModuleStruct>()
        val exts = LinkedHashMap<String, MutableMap<String, ModuleExt>>()
        var nextId = 0
        for (entry in file.entries) {
            val d = (entry as? DeclEntry)?.decl ?: continue
            when (d) {
                is FunDecl -> if (d.receiver == null)
                    funs[d.name] = ModuleFun(SymbolRef(nextId++, SymbolKind.FUN, d.name), d)
                else {
                    val head = extHeadOf(d.receiver!!)
                    if (head.isNotEmpty())
                        exts.getOrPut(head) { LinkedHashMap() }[d.name] =
                            ModuleExt(SymbolRef(nextId++, SymbolKind.FUN, d.name), d)
                }
                is StructDecl ->
                    structs[d.name] = ModuleStruct(SymbolRef(nextId++, SymbolKind.STRUCT, d.name), d)
                is TypeAliasDecl ->
                    aliases[d.name] = ModuleAlias(SymbolRef(nextId++, SymbolKind.ALIAS, d.name), d)
                else -> { }
            }
        }
        return ModuleSnapshot(name, alias, funs, aliases, structs, exts, stdlib)
    }

    /** 扩展函数接收者头（ExtFunHeads.ofAst 的模块内最小镜像：具名/限定段链 + 视图族解包）。 */
    private fun extHeadOf(receiver: AstType): String = when (receiver) {
        is NamedType -> receiver.name
        is QualifiedType -> (receiver.module + receiver.name).joinToString(".")
        is MutType -> extHeadOf(receiver.inner)
        is AtomicType -> extHeadOf(receiver.inner)
        is RefType -> extHeadOf(receiver.inner)
        else -> ""
    }
}
