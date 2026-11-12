package sublimation.frontend.semantic

import sublimation.frontend.ast.ErrorType
import sublimation.frontend.ast.TraitDecl
import sublimation.frontend.ast.Decl
import sublimation.frontend.ast.DeclEntry
import sublimation.frontend.ast.ErrorDecl
import sublimation.frontend.ast.FileAst
import sublimation.frontend.ast.FunDecl
import sublimation.frontend.ast.NamedType
import sublimation.frontend.ast.TypeParam
import sublimation.frontend.ast.StructDecl
import sublimation.frontend.ast.FunType
import sublimation.frontend.ast.TupleType
import sublimation.frontend.ast.TypeAliasDecl
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.ident
import sublimation.frontend.hir.SymbolKind

/**
 * 阶段一 collect（ADR-1 §3.5 / ADR-6 §二）：两遍扫描登记符号。
 *
 * - pass1 [collectDecl]：声明级登记（fun/theorem/struct/enum/trait/alias/propdef/ctor/method/impl），
 *   同表重名落 E-DUP-DECL（旧码集口径，P-7）；
 * - pass2 [checkDecl]：全表就位后的跨表核对（类型别名与既有类型重名——构造子跨枚举歧义随 pass1
 *   的 putIfAbsent 顺带落 H-AMBIGUOUS-CTOR）。
 *
 * 两遍的意义即「别名前向引用」：一切名字解析都发生在**全表齐备之后**（由 elaborate 的类型/值解析
 * 单点产出，不重复落诊断——ADR-9 裁决）。范围边界（t7 骨架）：签名类型可解析性、默认值类型、
 * main 形参规则、HKT arity、@root 白名单等旧 checkDeclTypes 条目属类型/效应层（t8）。
 */
internal fun SymbolTable.collectFile(file: FileAst, diag: DiagBag) {
    for (entry in file.entries) (entry as? DeclEntry)?.decl?.let { collectDecl(it, diag) }
    for (entry in file.entries) (entry as? DeclEntry)?.decl?.let { checkDecl(it, diag) }
}

/** pass1：登记单个声明（工程模式顶层；脚本模式的局部 fun 由 elaborate 的局部帧登记）。 */
private fun SymbolTable.collectDecl(decl: Decl, diag: DiagBag) {
    when (decl) {
        is FunDecl -> collectFun(decl, diag)
        is StructDecl -> {
            if (declareGlobal(SymbolKind.STRUCT, decl.name, decl.pos) == null)
                diag.error("E-DUP-DECL", decl.pos, "结构体 " + ident(decl.name) + " 重复声明")
            else
                // B4（ADR-19）：结构体名即位置式构造入口——与枚举 ctor 同通道登记入 ctor 分表，
                // 位置式调用 User("a", 1) 于值空间解析；命名字段构造仍走解析器特设节点。
                declareCtor(decl.name, decl.pos, decl.fields.isEmpty(), decl.name)?.let { prev ->
                    diag.hint("H-AMBIGUOUS-CTOR", decl.pos,
                        "结构体 " + ident(decl.name) + " 与既有构造子 " + ident(prev.entry.name) + "（" + ident(prev.enumName) + "）撞名，裸名引用有歧义")
                }
        }
        is TraitDecl -> {
            if (declareGlobal(SymbolKind.TRAIT, decl.name, decl.pos) == null)
                diag.error("E-DUP-DECL", decl.pos, "特质 " + ident(decl.name) + " 重复声明")
        }
is TypeAliasDecl -> {
            if (declareGlobal(SymbolKind.ALIAS, decl.name, decl.pos) == null)
                diag.error("E-DUP-DECL", decl.pos, "类型别名 " + ident(decl.name) + " 重复声明")
        }
is ErrorDecl -> { }   // 容错节点：位置与原因已由解析层落 E-SYNTAX 诊断
    }
}

private fun SymbolTable.collectFun(decl: FunDecl, diag: DiagBag) {
    if (decl.name == "_") return                 // 指导§54：匿名占位名不入符号表
    // P-8/D（卷 TS-11/TS-13）：扩展函数（receiver != null）登记入扩展桶，与自由函数分表隔离；
    // 头经 ExtFunHeads 唯一规范化（元组/函数/联合按 形状/元数 取键，ADR-26）；
    // 同 (接收者头, 名字) 重复 → 两处位置均报 E-DUP-EXT（V1 禁止重载，不看形参列表/arity）。
    if (decl.receiver != null) {
        // t126（S5c / R-15）：用户同 (头, 名) 扩展优先——先摘虚拟扩展方法条目再登记（P-7 口径）
        dropPreludeVirtualExt(ExtFunHeads.ofAst(decl.receiver) ?: "", decl.name)
        val head = ExtFunHeads.ofAst(decl.receiver)
        if (head == null) return                // 头不可规范化（ErrorType 接收者）：解析层已落诊断，不登记
        val tpNames = decl.theory.filterIsInstance<TypeParam>().map { it.name }
        val sigKey = decl.params.joinToString(",") { sigSkeleton(it.type ?: ErrorType("形参类型标注省略", it.pos), tpNames) }   // t113 机械适配：省略位兜底 ErrorType（既有 sigSkeleton 全含）
        val dups = declareExtFun(decl.name, head, sigKey, decl.pos, decl)
        // TS-13：返回的位置表含既有声明与本次声明——逐位置报（两处均报，无冗余第三条）
        dups?.forEach { o -> diag.error("E-DUP-EXT", o,
            "扩展函数 " + ident(head) + "." + ident(decl.name) + "(" + sigKey + ") 重复声明（同接收者类型头/名字即重复——V1 禁止重载，TS-13 两处均报）") }
        return
    }
    // t126（S5c / R-15）：用户同名自由函数优先——先摘虚拟构造族符号再登记（P-7 口径）
    if (decl.name != "_") dropPreludeVirtualFun(decl.name)
    val isTheorem = "theorem" in decl.annotations
    val names = decl.params.map { it.name }
    val destruct = decl.params.any { it.destruct != null }
    if (declareFun(decl.name, decl.pos, isTheorem, names, destruct) == null) {
        // 旧体系：@theorem 函数登记于定理表（∴ 搜索只搜这里）。重载（首参类型区分）属 t8 类型层。
        val what = if (isTheorem) "定理函数" else "函数"
        diag.error("E-DUP-DECL", decl.pos, what + " " + ident(decl.name) + " 重复声明")
    }
}


/** pass2：全表就位后的跨表核对。 */
private fun SymbolTable.checkDecl(decl: Decl, diag: DiagBag) {
    when (decl) {
        is TypeAliasDecl -> {
            if (isNonAliasType(decl.name))
                diag.error("E-DUP-DECL", decl.pos, "类型别名 " + ident(decl.name) + " 与既有类型重名")
        }
        else -> { }
    }
}


// ============================ P-8/D（卷 TS-11/TS-13）扩展函数登记辅助 ============================

/**
 * 形参类型列表的骨架键（E-DUP-EXT 的去重口径）：结构递归渲染，理论参数按声明序位置归一为 \$n
 * （形参类型列表不同名同构视为同一签名——TS-13 的形参类型列表比较经理论参数位置归一）。
 */
private fun sigSkeleton(t: sublimation.frontend.ast.Type, tps: List<String>): String = when (t) {
    is NamedType -> {
        if (t.args.isEmpty() && t.name in tps) "\$" + tps.indexOf(t.name)
        else t.name + (if (t.args.isEmpty()) "" else "[" + t.args.joinToString(",") { sigSkeleton(it, tps) } + "]")
    }
    is FunType -> "(" + t.params.joinToString(",") { sigSkeleton(it, tps) } + ")=>" + sigSkeleton(t.ret, tps)
    is TupleType -> "(" + t.items.joinToString(",") { sigSkeleton(it, tps) } + ")"
    is sublimation.frontend.ast.MutType -> "@mut " + sigSkeleton(t.inner, tps)
    is sublimation.frontend.ast.AtomicType -> "@atomic " + sigSkeleton(t.inner, tps)
    is sublimation.frontend.ast.RefType -> "@ref " + sigSkeleton(t.inner, tps)   // t95
    is sublimation.frontend.ast.UnionType -> t.variants.joinToString("|") { sigSkeleton(it, tps) }
    is sublimation.frontend.ast.QualifiedType -> t.module.joinToString(".") + "::" + sigSkeleton(sublimation.frontend.ast.NamedType(t.name, t.args, t.pos), tps)
    is sublimation.frontend.ast.ErrorType -> "<err>"
}
