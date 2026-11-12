package sublimation.frontend.semantic

import sublimation.frontend.ast.Param
import sublimation.frontend.diag.SourcePos
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.ast.FunDecl
import sublimation.frontend.hir.SymbolKind
import sublimation.frontend.hir.SymbolRef

/**
 * 符号登记条目（ADR-6 §三「SymbolId 体系」的最小载体）。
 *
 * 契约（ADR-1 §3.5）：id 为构造期内唯一编号（序=全局按 Collector 两遍序、局部按 Elaborator 先序），
 * 相同输入必产相同编号（S-1 层间总契约 3）。[paramNames] 为命名实参重排（ADR-1 §3.1.2.2）
 * 所需的形参名序列——仅全局 FUN/THEOREM 条目填充；[destructParams] 标记形参含解构分量，
 * 旧 CallCheck 判据「含解构或 _ 参数的函数不支持命名实参」的登记侧依据；
 * [type] 为 t8 推导类型（PARAM/LOCAL 由 Elaborator 登记时填入——Collector 期未知，
 * 全局 FUN 的签名由 Elaborator 的声明索引按名取用）；[mutable] 为 @mut 绑定位（E-IMMUT-ASSIGN 判据）。
 */
internal data class SymbolEntry(
    val id: Int,
    val kind: SymbolKind,
    val name: String,
    val origin: SourceRange,
    val paramNames: List<String>? = null,
    val destructParams: Boolean = false,
    val type: sublimation.frontend.hir.HirType? = null,
    /** t125（S5b）：声明位可提升旗——仅 VarStmt 局部/脚本顶层 var 与 PARAM 形参在**脚本模式**置位；
     *  其余声明位（字段 C-5 / 循环变量 / 模式绑定 / lambda 形参）恒 false ⇒ 赋值位不登记提升需求。 */
    val scriptPromotable: Boolean = false,
    var mutable: Boolean = false,
) {
    fun ref(): SymbolRef = SymbolRef(id, kind, name)
}

/** 构造子补充信息（I2 零字段构造子判定 + 裸名/复合名解析共用）。 */
internal data class CtorInfo(val entry: SymbolEntry, val nullary: Boolean, val enumName: String)

/**
 * 分层符号表（ADR-6 §三）：全局声明层（类型空间 / 值空间 / 方法表分表登记，对齐旧 Symbols.kt）
 * + 帧栈（函数帧 / 块作用域，遮蔽由帧链内→外天然实现）。
 *
 * 只读边界（ADR-6 §二「elaborate 纯函数，AST 与符号表只读」的落地口径，裁决见 ADR-9）：
 * 全局层在 collect 完成后对 elaborate 只读——elaborate 期间不新增全局符号；
 * 帧为构造期 transient 状态（enterFrame/exitFrame 成对平衡，不跨调用泄漏），
 * 故相同输入（file + 表）恒产相同输出。标识 id 的顺序分配是构造期记账，不属于全局层内容。
 */
internal class SymbolTable(private val hirNid: () -> Int) {
    // ---- 全局声明层：类型空间（struct/enum/trait/alias 分表，互不冲突——旧实现同构） ----
    private val structs = LinkedHashMap<String, SymbolEntry>()
    private val enums = LinkedHashMap<String, SymbolEntry>()
    private val traits = LinkedHashMap<String, SymbolEntry>()
    private val aliases = LinkedHashMap<String, SymbolEntry>()

    // ---- 全局声明层：值空间（ctor/fun/theorem/propdef 分表） ----
    private val ctors = LinkedHashMap<String, CtorInfo>()   // 裸名与 "Enum::C" 复合名同指
    private val funs = LinkedHashMap<String, SymbolEntry>()
    private val theorems = LinkedHashMap<String, SymbolEntry>()


    // ---- P-8/D（卷 TS-11）：扩展函数桶——键 (接收者类型头, 名字)，与自由函数分表隔离 ----
    private val extFuns = LinkedHashMap<Pair<String, String>, MutableList<SymbolEntry>>()
    /** t39：全局扩展函数的 AST 声明（签名核对与满足判定；局部可见集查询的全局兜底源）。 */
    private val extFunDecls = LinkedHashMap<Pair<String, String>, sublimation.frontend.ast.FunDecl>()

    /** P-8/D：扩展函数签名去重位置簿——(头, 名字) → 已登记位置序列（t38 判重口径；sigKey 仅入诊断文本）。 */
    private val extFunOrigins = LinkedHashMap<Pair<String, String>, MutableList<SourceRange>>()

/** P-8/B（卷 TS-6②）：特质常量规范分表（常量规范按名登记，V1 满足判定不参与）。 */
private val classConsts = LinkedHashMap<String, SymbolEntry>()

    // ---- 帧栈（函数帧 / 块作用域） ----
    private val frames = ArrayDeque<Scope>()

    private var nextId = 0

    /** t39（ADR-27）：遮蔽提示钩子——H-SHADOW 为 hint 族（声明方允许遮蔽，仅留痕）；由拥有 diag 的
     *  驱动（Semantic.kt compile）注入，参数 = (名字, 新声明的种类中文名, 被遮蔽者位置描述, 新声明位置)。 */
    internal var shadowHintSink: ((name: String, what: String, where: String, at: SourceRange) -> Unit)? = null

    /** t86（F6 收口）：原生命题名保护从 fun/var 声明位**下沉到所有引入名字的位**——声明通道统一拦
     *  （此前形参 / 字段 / 局部 / lambda / 模式 / when / 解构 / 扩展函数名都能遮蔽编译器内建符号，
     *  诊断只在遮蔽级别）。语义与既有 E-NATIVE-REDECL 一致("试图占用原生命题名")，不新增诊断码；
     *  由拥有 diag 的驱动（Semantic.kt compile）注入；参数 = (名字, 元数, 位置文案尾, 位置)。 */
    internal var nativeReclSink: ((name: String, arity: Int, what: String, at: SourceRange) -> Unit)? = null

    /** t86（F6）：保护判定——NativeProps 注册表内名字一律不可占用。what = 位置文案尾：
     *  声明位"不可重定义" / 其余绑定位"不可用作变量名"（文案按位置分化，码不分）。
     *  只拦不阻断（半成品树原则：登记照旧，下游可消费）。 */
    private fun guardNativeName(name: String, what: String, at: SourceRange) {
        NativeProps.arityOf(name)?.let { ar -> nativeReclSink?.invoke(name, ar, what, at) }
    }

    /** t39：位置 → 「line:col」短文本（H-SHADOW 提示用；与诊断渲染同基）。 */
private fun SourceRange.posStr() = start.line.toString() + ":" + start.col

/** t39：SymbolKind → 诊断文本中文名。 */
    private fun kindLabel(kind: SymbolKind): String = when (kind) {
        SymbolKind.FUN -> "函数"; SymbolKind.THEOREM -> "定理函数"; SymbolKind.LOCAL -> "变量"
        SymbolKind.PARAM -> "参数"; SymbolKind.FIELD -> "字段"; SymbolKind.CTOR -> "构造子"
        SymbolKind.BUILTIN -> "内建"; SymbolKind.TRAIT_CONST -> "特质常量"; else -> "绑定"
    }

    init {
        // 内建名字（类型/函数/值字面量）一律懒登记（[builtinEntry]，BUILTIN_KIND 走 lookupBuiltin* 通道，
        // 不经 declareGlobal——BUILTIN 无全局分表）；单文件期无 prelude，名字层先行登记（P-7 对齐）。
    }

    /** 全局符号登记（Collector pass1）；同名同表重复 → 返回既有条目（调用方落 E-DUP-DECL）。 */
    fun declareGlobal(kind: SymbolKind, name: String, origin: SourceRange,
                      paramNames: List<String>? = null, destructParams: Boolean = false): SymbolEntry? {
        guardNativeName(name, "不可重定义", origin)   // t86（F6）：enum/alias 等全局声明位同样不可占用
        val table = valueOrTypeTable(kind) ?: return null
        if (table.containsKey(name)) return null
        val entry = SymbolEntry(nextId++, kind, name, origin, paramNames, destructParams)
        table[name] = entry
        return entry
    }

    /** 全局函数/定理登记（Collector pass1；@theorem → THEOREM 分表）；同名重名 → null（调用方落 E-DUP-DECL）。 */
    fun declareFun(name: String, origin: SourceRange, isTheorem: Boolean,
                   paramNames: List<String>? = null, destructParams: Boolean = false): SymbolEntry? {
        guardNativeName(name, "不可重定义", origin)   // t86（F6）：原 Elaborator 声明位守卫移入此（中央统一）
        val table = if (isTheorem) theorems else funs
        if (table.containsKey(name)) return null
        val kind = if (isTheorem) SymbolKind.THEOREM else SymbolKind.FUN
        val entry = SymbolEntry(nextId++, kind, name, origin, paramNames, destructParams)
        table[name] = entry
        return entry
    }

    /** 登记构造子（裸名 + 复合名同指）；返回既有同裸名条目（调用方按同/异枚举落 E-DUP-DECL 或 H-AMBIGUOUS-CTOR）。 */
    fun declareCtor(name: String, origin: SourceRange, nullary: Boolean, enumName: String): CtorInfo? {
        guardNativeName(name, "不可重定义", origin)   // t86（F6）：struct/enum 构造名同样不可占用
        if (ctors.containsKey(name)) return ctors[name]
        val entry = SymbolEntry(nextId++, SymbolKind.CTOR, name, origin)
        val info = CtorInfo(entry, nullary, enumName)
        ctors[name] = info
        ctors[enumName + "::" + name] = info
        return null   // 登记成功；返回既有条目仅发生在上方裸名重复路径
    }

    /**
     * P-8/D（TS-11/TS-13）：登记扩展函数——键 (接收者类型头, 名字)。
     * t38（ADR-26）：V1 **禁止重载**——同 (头, 名字) 只允许一个声明，不论形参类型列表或 arity
     * 是否不同（Go 派方向：无函数重载）；重复 → 返回既有位置表（调用方对**两处位置均报** E-DUP-EXT）。
     * 形参骨架键 sigKey 仅保留作诊断文本，**不参与判重**。
     */
    fun declareExtFun(
        name: String, receiverHead: String, sigKey: String, origin: SourceRange,
        decl: sublimation.frontend.ast.FunDecl,
    ): List<SourceRange>? {
        guardNativeName(name, "不可重定义", origin)   // t86（F6）：扩展函数名同样不可占用
        val dupKey = receiverHead to name                       // t38：判重只看 (头, 名字)
        extFunOrigins[dupKey]?.let { return it + origin }
        val entry = SymbolEntry(nextId++, SymbolKind.FUN, name, origin)
        extFuns.getOrPut(receiverHead to name) { mutableListOf() }.add(entry)
        extFunDecls[receiverHead to name] = decl
        extFunOrigins.getOrPut(dupKey) { mutableListOf() }.add(origin)
        return null
    }

    /**
     * t39（ADR-27）：局部扩展函数声明——入**当前帧**的桶（帧退即消失，复用既有 frame push/pop，
     * 无独立生命周期）。同帧同 (头, 名字) → 返回 [既有声明位, 本次声明位]（调用方两处报 E-DUP-EXT）；
     * 遮蔽外层帧/全局同键 → H-SHADOW 提示（**异键不受影响**：内层 Nat.f 不遮蔽外层 Str.f）。
     */
    fun declareLocalExtFun(
        name: String, receiverHead: String, origin: SourceRange,
        decl: sublimation.frontend.ast.FunDecl,
    ): List<SourceRange>? {
        guardNativeName(name, "不可重定义", origin)   // t86（F6）
        if (frames.isEmpty()) enterFrame(ScopeKind.BLOCK)
        val frame = frames.last()
        val key = receiverHead to name
        frame.extFuns[key]?.let { return listOf(it.entry.origin, origin) }   // 同域同键：E-DUP-EXT 两处报
        val entry = SymbolEntry(nextId++, SymbolKind.FUN, name, origin)
        frame.extFuns[key] = LocalExtFun(entry, decl)
        // H-SHADOW：外层帧 → 全局，同键各报一次（遮蔽按 (头, 名字) 键）
        for (i in 0 until frames.size - 1) {
            frames[i].extFuns[key]?.let { le -> shadowHintSink?.invoke(name, "扩展函数", "外层（" + le.entry.origin.posStr() + "）", origin); return null }
        }
        extFuns[key]?.let { list ->
            shadowHintSink?.invoke(name, "扩展函数", "全局（" + list.first().origin.posStr() + "）", origin)
        }
        return null
    }

    /** t39：扩展函数符号可见集查询——帧链内→外按 (头,名字) 键遮蔽（内层命中即返回，不回退）+ 全局兜底。 */
    fun lookupExtFunVisible(receiverHead: String, name: String): List<SymbolEntry> {
        val key = receiverHead to name
        for (i in frames.indices.reversed()) frames[i].extFuns[key]?.let { return listOf(it.entry) }
        if (extFuns[key] != null) return extFuns.getValue(key)
        // t126（S5c / R-15）：虚拟 @mut 扩展方法兜底（用户同键扩展优先——extFuns 表先查）
        return preludeVirtualExt(receiverHead, name)?.let { listOf(it) } ?: emptyList()
    }

    /** t39：扩展函数 AST 声明可见集查询（方法糖/直接调用的签名核对 + TS-19 满足判定）；同键遮蔽同上。 */
    fun lookupExtDeclVisible(receiverHead: String, name: String): sublimation.frontend.ast.FunDecl? {
        val key = receiverHead to name
        for (i in frames.indices.reversed()) frames[i].extFuns[key]?.let { return it.decl }
        if (extFunDecls[key] != null) return extFunDecls.getValue(key)
        // t126（S5c / R-15）：虚拟 @mut 扩展方法声明兜底（方法糖签名核对 + @mut 注解读取）
        return PreludeDecls.EXT_METHODS.firstOrNull { ExtFunHeads.ofAst(it.receiver!!) == receiverHead && it.name == name }
    }

    /** t39：名字级前置判定（TS-14 直接调用）——任一可见帧或全局存在该名的扩展函数。 */
    fun hasExtFunVisible(name: String): Boolean {
        for (i in frames.indices.reversed()) if (frames[i].extFuns.values.any { it.entry.name == name }) return true
        return extFuns.values.any { list -> list.any { it.name == name } }
    }

    /** t134（S7 / R-23）：挂载模块结构体的**构造器条目层**（lookupCtor/lookupValue 链：用户 ctor > 挂载
     *  模块结构体 > prelude 虚拟——P-7 用户优先；模块私有结构体的构造经非限定名解析（R-23 非限定链））。 */
    private val moduleCtors = LinkedHashMap<String, SymbolEntry>()

    /** t134：构造器形状面（CtorInfo 查询层——nullary 位由注入方按结构体字段决，enumName=结构体名）。 */
    private val moduleCtorInfos = LinkedHashMap<String, CtorInfo>()

    /** t134：父域注入挂载模块结构体构造器条目（Elaborator init 期——先于 Collector 用户声明）。 */
    fun declareModuleCtor(name: String, moduleId: Int, origin: SourceRange, nullary: Boolean) {
        val entry = SymbolEntry(moduleId, SymbolKind.CTOR, name, origin, null, false)
        moduleCtors[name] = entry
        moduleCtorInfos[name] = CtorInfo(entry, nullary, name)
    }

    /** 扩展函数桶查询——可见集口径（帧链内→外按键遮蔽 + 全局；t39 封装于 lookupExtFunVisible）。 */
    fun lookupExtFun(receiverHead: String, name: String): List<SymbolEntry> =
        lookupExtFunVisible(receiverHead, name)

    /** t134（S7 / R-23）：本模块扩展函数（帧链→全局，不含 prelude 虚拟/挂载面）——方法糖查询链第一层。 */
    fun lookupExtLocal(receiverHead: String, name: String): Pair<FunDecl, SymbolEntry>? {
        val key = receiverHead to name
        for (i in frames.indices.reversed()) frames[i].extFuns[key]?.let { return it.decl to it.entry }
        if (extFuns[key] != null && extFunDecls[key] != null)
            return extFunDecls.getValue(key) to extFuns.getValue(key).first()
        return null
    }

    /** t134：prelude 虚拟扩展方法声明（方法糖查询链第三层兜底——virtualExtMethods 直查）。 */
    fun preludeVirtualExtDecl(receiverHead: String, name: String): FunDecl? =
        virtualExtMethods[receiverHead to name]

    /** 该名是否存在可见的扩展函数（直接调用形态的静默前置判定，TS-14；t39 可见集）。 */
    fun hasExtFun(name: String): Boolean = hasExtFunVisible(name)

    // ---- 局部帧（elaborate 期间）----

    /** 在当前帧顶声明局部绑定；同名覆盖（旧 Frame.declareVar 的遮蔽口径，P-7 对齐）。
     *  t8 附入推导类型与 @mut 标记（E-IMMUT-ASSIGN / 值引用类型结论的查询侧）。 */
    fun declareLocal(
        kind: SymbolKind, name: String, origin: SourceRange,
        type: sublimation.frontend.hir.HirType? = null, mutable: Boolean = false,
        what: String = "不可用作变量名",
        /** t125（S5b）：声明位可提升旗透传（赋值位按此过滤提升候选——仅局部/形参/脚本顶层 var）。 */
        scriptPromotable: Boolean = false,
    ): SymbolEntry {
        // 脚本顶层无显式帧——隐式开一帧（同旧 Frame 链的根帧口径），绑定才能被查到
        if (frames.isEmpty()) enterFrame(ScopeKind.BLOCK)
        // t86（F6）：原生命题名不可用作任何绑定的名字（形参/字段/局部/lambda/模式/when/解构）——
        // 声明位形（var/局部 fun）由调用方传 what=不可重定义 保持既有文案
        guardNativeName(name, what, origin)
        val frame = frames.last()
        // t39（ADR-27）：H-SHADOW —— 同域重名「后遮蔽前」/ 局部遮蔽外层或全局（均不报错，遮蔽合法）
        frame.bindings[name]?.let { b ->
            shadowHintSink?.invoke(name, kindLabel(kind), "同域（" + b.origin.posStr() + "）", origin)
        } ?: lookupValueOuter(name)?.let { b ->
            shadowHintSink?.invoke(name, kindLabel(kind), whereLabel(b) + "（" + b.origin.posStr() + "）", origin)
        }
        val entry = SymbolEntry(nextId++, kind, name, origin, type = type,
            mutable = if (mutable) true else false, scriptPromotable = scriptPromotable)
        frame.bindings.put(name, entry)
        return entry
    }

    /**
     * t52（ADR-34 R-32）：局部自由函数声明——登记局部符号（遮蔽口径同 [declareLocal]，kind 仍 LOCAL
     * 以保 lookupValue/遮蔽提示不变）并附 AST 声明入**当前帧**的槽（帧退即消失，复用 frame push/pop）。
     * 调用点按名查签名（局部优先于全局同名——帧链内→外，最后才到全局分表）。
     */
    fun declareLocalFun(
        name: String, origin: SourceRange, paramNames: List<String>,
        destructParams: Boolean, decl: sublimation.frontend.ast.FunDecl,
    ): SymbolEntry {
        // 遮蔽提示与帧绑定走 declareLocal（kind=LOCAL，口径同局部变量），再补形参名/解构旗标入条目
        val base = declareLocal(SymbolKind.LOCAL, name, origin, what = "不可重定义")
        val frame = frames.last()
        val entry = base.copy(paramNames = paramNames, destructParams = destructParams)
        frame.bindings[name] = entry
        frame.localFuns[name] = LocalFun(entry, decl)
        return entry
    }

    /** t52：可见局部自由函数声明（帧链内→外；全局 funDecls 由 Elaborator 兜底）。 */
    fun lookupLocalFunDecl(name: String): sublimation.frontend.ast.FunDecl? =
        frames.reversed().firstOrNull { it.localFuns.containsKey(name) }?.localFuns?.get(name)?.decl

    /** t52：可见局部自由函数符号条目（命名实参重排通道）。 */
    fun lookupLocalFunEntry(name: String): SymbolEntry? =
        frames.reversed().firstOrNull { it.localFuns.containsKey(name) }?.localFuns?.get(name)?.entry

    /**
     * t39：脚本模式顶层值声明（var）——顶层自由变量与函数**同域唯一**（用户裁决 2026-11-06）：
     * var↔var（顶层隐式帧）或 var↔fun（Collector 登记的全局分表）同名 → 返回 null（调用方落
     * E-DUP-DECL「顶层自由变量与函数不允许重名」）；块内重名不受此限（走 declareLocal + H-SHADOW）。
     */
    fun declareScriptTopVar(
        name: String, origin: SourceRange,
        type: sublimation.frontend.hir.HirType? = null, mutable: Boolean = false,
        /** t125（S5b）：脚本顶层 var 可提升旗（R-06 脚本自动提升 T1 顶层族）。 */
        scriptPromotable: Boolean = false,
    ): SymbolEntry? {
        if (frames.isEmpty()) enterFrame(ScopeKind.SCRIPT_TOP)
        val frame = frames.last()
        if (frame.kind != ScopeKind.SCRIPT_TOP) return declareLocal(SymbolKind.LOCAL, name, origin, type, mutable,
            scriptPromotable = scriptPromotable)
        guardNativeName(name, "不可重定义", origin)   // t86（F6）：var 声明位（替代原 Elaborator E:785 守卫）
        frame.bindings[name]?.let { return null }        // var↔var 顶层重名
        if (funs[name] != null) return null              // var↔fun 顶层重名（Collector 已登记）
        val entry = SymbolEntry(nextId++, SymbolKind.LOCAL, name, origin, type = type,
            mutable = if (mutable) true else false, scriptPromotable = scriptPromotable)
        frame.bindings.put(name, entry)
        return entry
    }

    /** t39：值位名查询（跳过当前帧）——遮蔽探测：外层帧 → 全局分表；内层命中由调用方自行判定。 */
    private fun lookupValueOuter(name: String): SymbolEntry? {
        for (i in 0 until frames.size - 1) frames[i].bindings[name]?.let { return it }
        return ctors[name]?.entry ?: moduleCtors[name] ?: preludeCtor(name)?.entry ?: funs[name] ?: theorems[name]
            ?: preludeVirtualFun(name)
    }

    /** t39：被遮蔽条目的来源标签（外层帧 / 全局分表）。 */
    private fun whereLabel(b: SymbolEntry): String =
        if (funs[b.name] === b || theorems[b.name] === b || ctors[b.name]?.entry === b) "全局"
        else "外层"

    /** 名字是否为 @mut 绑定（旧 Frame.isMut：帧链内→外）。 */
    fun isMut(name: String): Boolean {
        for (i in frames.indices.reversed()) {
            frames[i].bindings[name]?.let { return it.mutable }
        }
        return false
    }

    /** 帧作用域内求值——成对 push/pop 的结构化通道（elaborate 遍历期的唯一入帧方式）。 */
    inline fun <T> frame(kind: ScopeKind, block: () -> T): T {
        enterFrame(kind)
        return try { block() } finally { exitFrame() }
    }

    /** t39：当前帧是否为脚本顶层帧（顶层 var 唯一判定用）。 */
    fun isScriptTopFrame(): Boolean = frames.lastOrNull()?.kind == ScopeKind.SCRIPT_TOP

    fun enterFrame(kind: ScopeKind) = frames.addLast(Scope(kind))
    fun exitFrame() = frames.removeLast()

    /** t45（ADR-30 R-23）：默认值求值环境重建专用——把帧链切到「脚本顶层帧」为止，
     *  调用者的局部帧（块/函数帧）不参与名字解析；默认值按声明处词法作用域求值。
     *  block 内自行 enterFrame/frame 推入方法帧（被调函数 theory + 形参），用完整体还原。 */
    inline fun <T> isolatedFrame(block: () -> T): T {
        val saved = frames.toMutableList()
        frames.clear()
        for (f in saved) if (f.kind == ScopeKind.SCRIPT_TOP) { frames.addLast(f) ; break }
        return try { block() } finally { frames.clear(); frames.addAll(saved) }
    }

    // ---- 查询（纯读）----

    /** 值位名查询：帧链内→外（遮蔽）→ ctor（用户 → prelude 兜底）→ fun → theorem → propdef → 内建函数。 */
    fun lookupValue(name: String): SymbolEntry? {
        for (i in frames.indices.reversed()) {
            frames[i].bindings[name]?.let { return it }
        }
        return ctors[name]?.entry ?: moduleCtors[name] ?: preludeCtor(name)?.entry ?: funs[name] ?: theorems[name]
            ?: lookupBuiltinFun(name) ?: preludeFun(name) ?: lookupBuiltinValue(name)
            ?: preludeVirtualFun(name)
    }

    /** t109（R3-V1 §四 P2）:项名 → 登记型（(e:T) 通道的 termType 判据原料——遮蔽同 lookupValue,
     *  值位查不到再查类型位:类型名项（Nat 等）还原为类型表登记表目）。 */
    fun typeOfName(name: String): sublimation.frontend.hir.HirType? {
        for (i in frames.indices.reversed()) {
            frames[i].bindings[name]?.let { return it.type }
        }
        val value = ctors[name]?.entry ?: moduleCtors[name] ?: preludeCtor(name)?.entry ?: funs[name] ?: theorems[name]
            ?: lookupBuiltinFun(name) ?: preludeFun(name) ?: lookupBuiltinValue(name)
        return value?.type ?: lookupType(name)?.type
    }

    /** 类型位名查询：struct/enum/trait/alias → 内建类型 → t44 prelude 虚拟 struct
     *  → t47（ADR-31 R-26）prelude 虚拟联合别名（List/Optional/Result/Map/Set 让位 BUILTIN_TYPES 后的兜底）。 */
    fun lookupType(name: String): SymbolEntry? =
        structs[name] ?: enums[name] ?: traits[name] ?: aliases[name] ?: lookupBuiltinType(name)
            ?: lookupPreludeStructType(name) ?: lookupPreludeAliasType(name)

    /** t44（ADR-29）：prelude 虚拟 struct 的类型位条目（Some[Nat] 等同 struct 类型位可用；用户同名声明优先）。 */
    fun lookupPreludeStructType(name: String): SymbolEntry? =
        if (PreludeDecls.structOf(name) != null) builtinEntry(name) else null

    /** t47（ADR-31 R-26）：prelude 虚拟联合别名的类型位条目（Optional[Nat] 等容器让位后的唯一来源；
     *  用户同名 alias 声明优先——aliases 表先查）。 */
    fun lookupPreludeAliasType(name: String): SymbolEntry? =
        if (PreludeDecls.aliasOf(name) != null) builtinEntry(name) else null

    /** 该名是否为可解析类型（I2 前置件：类型优先，同名 struct/enum 不改写为构造子）。 */
    fun isTypeName(name: String): Boolean = lookupType(name) != null

    /** 构造子查询（裸名 / Enum::C 复合名；用户声明优先，prelude 构造子名字层兜底）。 */
    fun lookupCtor(name: String): CtorInfo? = ctors[name] ?: moduleCtorInfos[name] ?: preludeCtor(name)

    /** prelude 构造子实例级缓存（编译实例内 lazy——跨编译不可共享：条目携带本实例的符号 id）。 */
    private val preludeCtorCache = LinkedHashMap<String, CtorInfo>()

    /** prelude 构造子查询（单文件期无 prelude 挂载的兜底；用户自声明的 ctor 优先于本表）。
     *  t44（ADR-29）：prelude 构造子改为**虚拟 struct**形态——nullary 判据=字段数 0（t41 空构造子
     *  单例语义），enumName 即 struct 名（构造调用返回 struct 名，同用户 struct 口径）；
     *  符号 kind 为 CTOR——调用位与 nameRefType 同用户构造子通道（旧 BUILTIN 通道随之删除）。 */
    fun preludeCtor(name: String): CtorInfo? = preludeCtorCache.getOrPut(name) {
        val sd = PreludeDecls.structOf(name) ?: return null
        CtorInfo(preludeCtorEntry(name), sd.fields.isEmpty(), name)
    }

    /** t44：prelude 虚拟 struct 构造子的符号条目（CTOR kind；与 builtinEntry 的 BUILTIN 条目分键缓存）。 */
    private fun preludeCtorEntry(name: String): SymbolEntry =
        builtins.getOrPut("ctor::" + name) { SymbolEntry(nextId++, SymbolKind.CTOR, name, SYNTHETIC_POS) }

    /**
     * t39（ADR-27 R-14）：prelude 值方法通道——Task/Channel 等异步内建泛型的**对象方法**
     * （start/join/isAlive、send/receive/close/isClosed）。旧实现（CallCheck:224-251，P-7 字典点查）
     * 在方法糖之前特判：它们是 codegen/运行时生成的对象字段（天然生成 t.start / ch.send），
     * 语义层名字层认「接收者头 + 方法名」即合法通道，不落 E-UNBOUND-NAME。
     */
    fun preludeMethod(receiverHead: String?, name: String): Boolean =
        receiverHead != null && (PRELUDE_METHODS[receiverHead]?.contains(name) == true)

    /** t39：prelude 值方法表（与旧实现 taskMethods/channelMethods 同源）。 */
    private val PRELUDE_METHODS = mapOf(
        "Task" to setOf("start", "join", "isAlive"),
        "Channel" to setOf("send", "receive", "close", "isClosed"),
    )

        /** t137（R-23 retire）：PRELUDE_FUNS Kotlin 表退役——同名单由 **挂载 stdlib 模块快照** 提供
     * （非限定链 R-23：本模块 > 挂载 stdlib 子 > prelude 虚拟层——数据源替换、分发机制不变：
     * builtinEntry 懒登记 → 用户裸名调用 HIR 形态零差分，67 逐字节 SAME 由之成立）。 */
    private val moduleFunNames = mutableSetOf<String>()
    fun declareModuleFunNames(names: Collection<String>) { moduleFunNames += names }
    fun preludeFun(name: String): SymbolEntry? = if (name in moduleFunNames) builtinEntry(name) else null

    /** t126（S5c / R-15 容器面）：虚拟自由构造族（MutableListOf/ListOf）——符号**懒登记**（零命中零
     *  nextId 消费，68+14 样本 SAME 基线逐字保护）。用户同名 fun 优先（funs 表先查）。
     *  声明索引在 Elaborator 侧预注入（funDecls 种子位），签名核对与理论参数推导走既有自由函数通道。 */
    private val virtualFuns = PreludeDecls.FUNS.associateBy { it.name }
    private val virtualFunEntries = mutableMapOf<String, SymbolEntry>()
    fun preludeVirtualFun(name: String): SymbolEntry? {
        if (name !in virtualFuns) return null
        return virtualFunEntries.getOrPut(name) {
            val d = virtualFuns.getValue(name)
            SymbolEntry(nextId++, SymbolKind.FUN, name, d.pos, d.params.map { it.name },
                d.params.any { it.destruct != null })
        }
    }

    /** t126（S5c / R-15）：虚拟 @mut 扩展方法族（add 等）——符号懒登记（同保护口径）;声明索引侧
     *  [lookupExtDeclVisible] 的虚拟兜底供方法糖签名核对。用户同 (头, 名) 扩展优先。 */
    private val virtualExtMethods = PreludeDecls.EXT_METHODS.associateBy {
        ExtFunHeads.ofAst(it.receiver!!)!! to it.name
    }
    private val virtualExtEntries = mutableMapOf<Pair<String, String>, SymbolEntry>()
    fun preludeVirtualExt(head: String, name: String): SymbolEntry? {
        val key = head to name
        if (key !in virtualExtMethods) return null
        return virtualExtEntries.getOrPut(key) {
            SymbolEntry(nextId++, SymbolKind.FUN, name, virtualExtMethods.getValue(key).pos)
        }
    }

    /** prelude 自由函数签名规格（params/ret；Elaborator 转为 HIR 类型形态供调用点核对）。 */

    /** t126（S5c / R-15）：用户同名声明优先——摘除虚拟构造族符号/虚拟扩展方法条目（Collector pass1 前）。
     *  符号侧摘除（声明索引侧由 file 预扫描自然覆盖：funDecls[d.name] = d 逐字覆盖虚拟种子）。 */
    fun dropPreludeVirtualFun(name: String) { virtualFunEntries.remove(name) }
    fun dropPreludeVirtualExt(head: String, name: String) { virtualExtEntries.remove(head to name) }

    /** 方法名查询；恰一个实现 → 条目，多实现 / 无 → null（接收者类型分派见 t8/t9）。 */

    /** 全局函数/定理条目（声明自符号取用，命名实参重排与声明 symbol 字段的来源）。 */
    fun funEntry(name: String): SymbolEntry? = funs[name] ?: theorems[name]
    fun structEntry(name: String): SymbolEntry? = structs[name]
    fun traitEntry(name: String): SymbolEntry? = traits[name]
    fun enumEntry(name: String): SymbolEntry? = enums[name]
    fun aliasEntry(name: String): SymbolEntry? = aliases[name]
    /** P-8/B（TS-6②）：特质常量规范条目。 */
    fun classConstEntry(name: String): SymbolEntry? = classConsts[name]

    /** 该名是否为**非别名**既有类型（struct/enum/trait/内建）——pass2「类型别名与既有类型重名」核对用。 */
    fun isNonAliasType(name: String): Boolean =
        structs.containsKey(name) || enums.containsKey(name) ||
            traits.containsKey(name) || lookupBuiltinType(name) != null

    private fun lookupBuiltinFun(name: String): SymbolEntry? =
        if (name in BUILTIN_FUNS) builtinEntry(name) else null
    private fun lookupBuiltinType(name: String): SymbolEntry? =
        if (name in BUILTIN_TYPES) builtinEntry(name) else null
    /** 值字面量（prelude 持有：null 为 Null 值、True/False 为 Bool 构造子——单文件期未挂 prelude，名字层兜底）。
     *  t8：条目附推导类型（null → Null、True/False → Bool），供推导层以值符号调用时定型。 */
    private fun lookupBuiltinValue(name: String): SymbolEntry? =
        if (name in BUILTIN_VALUES) builtinEntry(name, builtinValueTypeOf(name)) else null

    /** 内建值字面量的推导类型（HIR 节点 id 自唯一源 [hirNid] 分配——同源恒同值，供 HIR 引用定型，
     *  与 Elaborator 的 freshNid 同源，禁止第二节点计数器，ADR-14 / D-9 诊断↔节点身份绑定）。 */
    private val builtinValueTypes = LinkedHashMap<String, sublimation.frontend.hir.HirNamedType>()
    private fun builtinValueTypeOf(name: String): sublimation.frontend.hir.HirNamedType =
        builtinValueTypes.getOrPut(name) {
            val tn = if (name == "null") "Null" else "Bool"
            sublimation.frontend.hir.HirNamedType(tn, emptyList(), null, hirNid(), SYNTHETIC_POS, emptyList())
        }
    private val builtins = LinkedHashMap<String, SymbolEntry>()
    private fun builtinEntry(name: String, type: sublimation.frontend.hir.HirType? = null): SymbolEntry =
        builtins.getOrPut(name) { SymbolEntry(nextId++, SymbolKind.BUILTIN, name, SYNTHETIC_POS, type = type) }

    private fun valueOrTypeTable(kind: SymbolKind): MutableMap<String, SymbolEntry>? = when (kind) {
        SymbolKind.STRUCT -> structs
        SymbolKind.ENUM -> enums
        SymbolKind.TRAIT -> traits
        SymbolKind.ALIAS -> aliases
        SymbolKind.FUN, SymbolKind.THEOREM -> null   // 走 declareFun 的分表口径
        else -> null
    }
}

/** 作用域帧种类（ADR-6 §三 函数帧 / 块作用域）。 */
internal enum class ScopeKind { FUNCTION, BLOCK, SCRIPT_TOP }

/** t39：局部扩展函数帧槽——(符号条目, AST 声明) 同栖（查询要符号、签名核对要声明）。 */
internal class LocalExtFun(val entry: SymbolEntry, val decl: sublimation.frontend.ast.FunDecl)

/** t52（ADR-34 R-32）：局部自由函数帧槽——同栖口径（符号条目供调用分派、AST 声明供签名核对）。 */
internal class LocalFun(val entry: SymbolEntry, val decl: sublimation.frontend.ast.FunDecl)

/** 作用域帧：局部绑定表 + 内→外父链（遮蔽由 [SymbolTable.lookupValue] 沿帧链实现）。
 *  t39：局部扩展函数随帧同寿（帧退即消失，无独立生命周期）。 */
internal class Scope(val kind: ScopeKind) {
    val bindings = LinkedHashMap<String, SymbolEntry>()
    /** 局部扩展函数——(接收者类型头, 名字) 键唯一（同键遮蔽，异键共存）。 */
    val extFuns = LinkedHashMap<Pair<String, String>, LocalExtFun>()
    /** t52（ADR-34 R-32）：局部自由函数——名字键唯一（帧退即消失，同局部扩展函数生命周期）。 */
    val localFuns = LinkedHashMap<String, LocalFun>()
}

/** 内建符号无源位置——合成零点（位置消费者须容错；与「空文件取零点」同一标记）。 */
internal val SYNTHETIC_POS = SourceRange(SourcePos(0, 0), null)

/**
 * HIR 节点 id 单一分配源（ADR-14）：SymbolTable 的合成类型节点与 Elaborator 的 freshNid 同源——
 * 同一 nid 对应同一节点（ADR-1 §3.7 诊断↔节点身份绑定 / D-9），相同输入恒产相同编号（S-1 层间总契约 3）。
 * 禁止再引入第二个节点计数器（F1 blocker 级契约）。
 */
internal class NodeIdSource(private val start: Int = 1) {
    private var next = start
    fun alloc(): Int = next++
}

private val BUILTIN_TYPES = setOf(
    "Nat", "Int", "Rat", "Real", "Str", "Bool", "Null", "Nothing", "Any",
    "EmptyTuple", "SingleTuple",
    "Task", "Channel", "Mutex", "Flow",   // v2.0 异步内建泛类型
    "Range", "Json",                       // 旧 BASE_TYPES 同表（惰性区间 / JSON 值）
    // t47（ADR-31 R-26）：v1.0 容器（List/Optional/Result/Map/Set）**让位**给 t44 prelude 虚拟声明——
    // 唯一权威来源是 [PreludeDecls]，用户同名声明按 P-7 覆盖（不再判 E-DUP-DECL）；未声明时由
    // 虚拟声明兜底（lookupPreludeStructType）。本表只留真正的内建标量/单例/异步泛类型。
)

/** t44（ADR-29）注记：prelude 旧「枚举概念」四表（PRELUDE_CTORS / PRELUDE_ENUM_THEORY /
 * PRELUDE_ENUM_THEORY_NAMES / PRELUDE_ENUM_CTORS + PreludeCtor）已删除——prelude 内建类型改为
 * 虚拟 struct + 虚拟联合别名，见 [PreludeDecls]。t137（R-23 retire）：PreludeField/PreludeFun/
 * PRELUDE_FUNS 亦删除——自由函数签名表的数据源换成挂载 stdlib 模块快照（preludeFun）。 */

/** 内建函数名集合（旧 builtinimpure/builtinPure 两表的名字层点查对齐，P-7）。 */
private val BUILTIN_FUNS = setOf(
    "print", "readLine", "readFile", "writeFile", "getArgs", "sleep",
    "appendFile", "fileExists", "readDir", "timeNow", "rand",
    "delay", "runBoth", "runSeq",
    "collectToList", "collectCount", "collectAll",
    "withMutex", "bumpCounter", "bumpPair",
    "channelFromList", "sendAll", "channelDrain", "channelPipe",
    "sublEval", "sublRun",
    "emptyTuple", "singleTuple",
    "concat", "length", "charAt", "substring", "strCmp", "strUpper", "strLower",
    "toStr", "parseNat", "toRat", "toReal", "parseRat", "toNat",
    "panic", "in", "abs", "floor", "ceil", "pow",
    // t19（B1，ADR-18）：v2.0 标准库补全面——str/math/json 族名字层登记
    // （旧 builtinPure 表键全集，builtinSig 一一对应；此前漏带 strTrim 之后整段）。
    "strTrim", "startsWith", "endsWith", "strReplace", "strSplit", "strIndexOf",
    "mathMin", "mathMax", "mathClamp", "mathRound", "mathSin", "mathCos", "mathTan",
    "jsonParse", "jsonGet", "jsonAt", "jsonLen", "jsonStr", "jsonNum", "jsonBool",
    "jsonIsNull", "jsonIsArr", "jsonToStr", "jsonToList",
)

/** 内建值字面量名（prelude 兜底：null 为 Null 值、true/false 为 Bool 构造子单文件期名）。 */
private val BUILTIN_VALUES = setOf("null", "True", "False")     // t79（Q12）：大写 Bool 字面量。t84（Q12 收口）：小写 true/false 移除——未声明 ⇒ E-UNBOUND-NAME + 大写写法提示（NameResolver t84）
