package sublimation.frontend.json

import java.io.File

import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.DiagBag
import sublimation.frontend.diag.SourceRange
import sublimation.frontend.hir.HirAssign
import sublimation.frontend.hir.HirAxiom
import sublimation.frontend.hir.HirBinExpr
import sublimation.frontend.hir.HirBlock
import sublimation.frontend.hir.HirByExpr
import sublimation.frontend.hir.HirByStmt
import sublimation.frontend.hir.HirBecause
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirTraitDecl
import sublimation.frontend.hir.HirTraitConst
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirEntry
import sublimation.frontend.hir.HirErrorDecl
import sublimation.frontend.hir.HirErrorExpr
import sublimation.frontend.hir.HirErrorPattern
import sublimation.frontend.hir.HirErrorStmt
import sublimation.frontend.hir.HirErrorType
import sublimation.frontend.hir.HirExistsElim
import sublimation.frontend.hir.HirExistsIntro
import sublimation.frontend.hir.HirExprStmt
import sublimation.frontend.hir.HirField
import sublimation.frontend.hir.HirFile
import sublimation.frontend.hir.HirFlow
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirFunRef
import sublimation.frontend.hir.HirFunType
import sublimation.frontend.hir.HirIf
import sublimation.frontend.hir.HirIfProp
import sublimation.frontend.hir.HirInst
import sublimation.frontend.hir.HirIntLit
import sublimation.frontend.hir.HirFloatLit
import sublimation.frontend.hir.HirStrLit
import sublimation.frontend.hir.HirTop
import sublimation.frontend.hir.HirBot
import sublimation.frontend.hir.HirTask
import sublimation.frontend.hir.HirLambda
import sublimation.frontend.hir.HirAnonFun
import sublimation.frontend.hir.HirLocalFun
import sublimation.frontend.hir.HirMutType
import sublimation.frontend.hir.HirAtomicType
import sublimation.frontend.hir.HirBorrow
import sublimation.frontend.hir.HirRefType
import sublimation.frontend.hir.HirNameRef
import sublimation.frontend.hir.HirNamedType
import sublimation.frontend.hir.HirNode
import sublimation.frontend.hir.HirNote
import sublimation.frontend.hir.HirParam
import sublimation.frontend.hir.HirPatBind
import sublimation.frontend.hir.HirPatCtor
import sublimation.frontend.hir.HirPatCond
import sublimation.frontend.hir.HirPatElse
import sublimation.frontend.hir.HirPatIs
import sublimation.frontend.hir.HirPatLit
import sublimation.frontend.hir.HirPatTuple
import sublimation.frontend.hir.HirPAtom
import sublimation.frontend.hir.HirPAnd
import sublimation.frontend.hir.HirPBot
import sublimation.frontend.hir.HirPExists
import sublimation.frontend.hir.HirPExists1
import sublimation.frontend.hir.HirPForall
import sublimation.frontend.hir.HirPIff
import sublimation.frontend.hir.HirPImp
import sublimation.frontend.hir.HirPNot
import sublimation.frontend.hir.HirPOr
import sublimation.frontend.hir.HirPTop
import sublimation.frontend.hir.HirProofObligation
import sublimation.frontend.hir.HirPropParam
import sublimation.frontend.hir.HirQualifiedType
import sublimation.frontend.hir.HirReturn
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirSelfType  // P-8/A：卷 TS-29 新增节点
import sublimation.frontend.hir.HirStrategy
import sublimation.frontend.hir.HirStructCtor
import sublimation.frontend.hir.HirStructDecl
import sublimation.frontend.hir.HirTBin
import sublimation.frontend.hir.HirTBot
import sublimation.frontend.hir.HirTCall
import sublimation.frontend.hir.HirTDollar
import sublimation.frontend.hir.HirTField
import sublimation.frontend.hir.HirTInst
import sublimation.frontend.hir.HirTInt
import sublimation.frontend.hir.HirTName
import sublimation.frontend.hir.HirTParamRef
import sublimation.frontend.hir.HirTStr
import sublimation.frontend.hir.HirTSymbolCall
import sublimation.frontend.hir.HirTTop
import sublimation.frontend.hir.HirTUni
import sublimation.frontend.hir.HirTuple
import sublimation.frontend.hir.HirTupleType
import sublimation.frontend.hir.HirTypeAlias
import sublimation.frontend.hir.HirTypeParam
import sublimation.frontend.hir.HirTypeTest
import sublimation.frontend.hir.HirUnchecked
import sublimation.frontend.hir.HirUniExpr
import sublimation.frontend.hir.HirUnionType
import sublimation.frontend.hir.HirVarExpr
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.hir.HirWhen
import sublimation.frontend.hir.HirWhenArm
import sublimation.frontend.hir.HirWhenProp
import sublimation.frontend.hir.HirWhenPropArm
import sublimation.frontend.hir.SymbolRef

/**
 * HIR → JSON 产物发射器（P-6 决议：手写发射器、零第三方依赖，S-2 §3.3；
 * schema v1：ADR-1 §3.7；依赖方向 json → hir/diag，S-2 §3.2）。
 *
 * 契约（schema v1）：
 * - envelope：`{schema, compiler{version}, unit{path,mode}, ok, diagnostics, hir}`；
 * - 节点统一形态：`{kind, nid, pos, notes, …语义字段}`——kind/nid/pos/notes 对**所有**
 *   HirNode（含辅助记录）齐备（ADR-1 §3.7；对示例 A 中省略辅助记录 kind/notes 的紧凑
 *   写法做统一化——统一形态使消费者无需按位置特判，schema 稳定性优先，见 ADR-6 决策 2）；
 * - pos 紧凑串「l:c-l:c」，单点（end == null）取「l:c」；
 * - null 与空集合显式发射（`null` / `[]`）——固定字段集让消费者无需处理键缺失
 *   （D-9 信息保留与 schema 稳定性）；
 * - 字面量保留原文（"value": "100"），供后端 codegen 复用（ADR-1 §3.3 #8）；
 * - 证明义务节点输出 proofContext/rule/verdict 三字段（ADR-1 §3.4/§3.7）。
 *
 * 纪律：发射为**纯函数**（相同输入产相同输出，S-1 层间总契约 3）；JSON 只有序列化
 * HirProp，不引入第二个命题表示（S-2 §3.2：json 只依赖 hir；命题唯一住所判据 1）。
 */
object HirJson {
    /** 产物 schema 标识（envelope.schema；ADR-1 §3.7。breaking 变更须追加新 ADR，不就地改版本）。 */
    const val SCHEMA = "sublimation/hir@v3"  // v2：P-8/C（卷 TS-31，t29）——声明层实例节点删除（TS-30），按 ADR-9 纪律以新 ADR 裁决（ADR-24）；v3：P-10 class→trait 更名（t56，ADR-33）

    /**
     * 编译器版本（envelope.compiler.version）。
     * 动机：与根 build.gradle.kts 的 allprojects.version 对齐——产物消费者以此做兼容判据，
     * 两侧失同步会把「构建版本」与「产物版本」拆成两条事实源；版本升级时须同步修订本常量。
     */
    const val COMPILER_VERSION = "0.1-SNAPSHOT"

    /** envelope.unit.mode：工程模式（顶层全为声明条目）。 */
    const val MODE_PROJECT = "project"

    /** envelope.unit.mode：脚本模式（含顶层语句条目）。 */
    const val MODE_SCRIPT = "script"

    /**
     * 发射完整 JSON 产物文档（envelope + diagnostics + hir）。
     *
     * 契约：`ok` 按 N-SEV-BLOCK 由诊断袋 ERROR 决定（D-10；S-1 §3 证明检查失败即编译失败
     * 落为 ok=false）；diagnostics 按落袋顺序保序输出；hir 为 [HirFile] 全节点发射。
     */
    fun emit(
        hir: HirFile, diagnostics: DiagBag, unitPath: String,
        /** D-12（模式真值单源）:compile 调用方透传;默认 project。废除由 HirStmtEntry 存在与否自算
         *（工程模式顶层 axiom 语句 → 旧 envelope 误标 script,如 jsoncmp 样本 公理化.subl）。 */
        scriptMode: Boolean = false,
    ): String {
        val writer = JsonWriter(StringBuilder())
        writer.obj {
            field("schema") { str(SCHEMA) }
            obj("compiler") { field("version") { str(COMPILER_VERSION) } }
            obj("unit") {
                field("path") { str(unitPath) }
                field("mode") { str(if (scriptMode) MODE_SCRIPT else MODE_PROJECT) }
            }
            field("ok") { bool(!diagnostics.hasError) }
            arr("diagnostics") {
                for (d in diagnostics.diags) item { diag(d) }
            }
            field("hir") { node(hir) }
        }
        return writer.result()
    }

    /**
     * 发射并写入产物文件（UTF-8）。
     *
     * 契约：父目录缺失即创建；文件内容与 [emit] 逐字符一致（产物文件是 HIR 的落盘交付，
     * D-8/D-9——后端与解释器的唯一输入）。
     */
    fun writeTo(
        target: File, hir: HirFile, diagnostics: DiagBag, unitPath: String,
        scriptMode: Boolean = false,
    ): File {
        target.parentFile?.mkdirs()
        target.writeText(emit(hir, diagnostics, unitPath, scriptMode), Charsets.UTF_8)
        return target
    }
}

// =========== 节点发射 ===========
// 分派为 sealed 穷尽 when（无 else）——编译期齐备性证明；运行期名集对账见 HirJsonTest。
// 字段取节点全部公开 val（D-9：尽可能多保留），发射顺序即字段声明顺序（输出可 diff）。

/** HIR 节点 → JSON 对象（节点统一形态 + 语义字段）。 */
private fun JsonWriter.node(n: HirNode): Unit = when (n) {
    // ---------- 文件（HirEntry 透传：条目不持身份，位置与五件套由所含节点承载） ----------
    is HirFile -> obj {
        head(n, "HirFile")
        arr("entries") { for (e in n.entries) item { entry(e) } }
    }
    // ---------- 声明层（S-5~S-14 映射，ADR-1 §3.2） ----------
    is HirFunDecl -> obj {
        head(n, "HirFunDecl")
        fieldStr("name", n.name)
        // 卷 TS-31：自由函数省略 receiver（与本发射器 ADR-9 显式 null 口径的显式例外，
        // 规范性依据 TS-31）；扩展函数必现且保留完整接收者类型子树（TS-28）。
        n.receiver?.let { fieldNode("receiver", it) }
        fieldSymbol("symbol", n.symbol)
        fieldStrings("annotations", n.annotations)
        fieldNodes("theory", n.theory)
        fieldNodes("params", n.params)
        fieldNodes("preps", n.preps)
        fieldNode("retType", n.retType)
        fieldNodes("posts", n.posts)
        fieldNodes("constraints", n.constraints)
        fieldEnum("purity", n.purity)
        fieldBool("async", n.async)
        // t108（R3 S1，C-8 按需）：推导元信息——仅推导位携带时发射（默认态零新字段，保基线）
        if (n.inferred || n.evidence.isNotEmpty()) {
            fieldBool("inferred", n.inferred)
            if (n.evidence.isNotEmpty()) fieldStrings("evidence", n.evidence)
        }
        fieldNode("body", n.body)
    }
    is HirTraitDecl -> obj {
        head(n, "HirTraitDecl")
        fieldStr("name", n.name)
        fieldSymbol("symbol", n.symbol)
        fieldNodes("theory", n.theory)
        fieldStrings("annotations", n.annotations)
        fieldNodes("members", n.members)
        fieldNodes("parents", n.parents)
        fieldNodes("constants", n.constants)   // P-8/B（TS-6②）
    }
    is HirTraitConst -> obj {   // P-8/B（TS-6②）：常量规范——签名登记无实现，schema 细则归 t29
        head(n, "HirTraitConst")
        fieldStr("name", n.name)
        fieldSymbol("symbol", n.symbol)
        fieldNode("type", n.type)
    }
    is HirStructDecl -> obj {
        head(n, "HirStructDecl")
        fieldStr("name", n.name)
        fieldSymbol("symbol", n.symbol)
        fieldNodes("theory", n.theory)
        fieldNodes("fields", n.fields)
        fieldStrings("annotations", n.annotations)
        fieldNodes("props", n.props)
        // t41（ADR-28 R-18）：空构造子单例旗标——JSON 可见，供后端保证引用相等
        fieldBool("nullary", n.nullary)
    }
    is HirTypeAlias -> obj {
        head(n, "HirTypeAlias")
        fieldStr("name", n.name)
        fieldSymbol("symbol", n.symbol)
        fieldNodes("theory", n.theory)
        fieldNode("target", n.target)
    }
    is HirErrorDecl -> obj {
        head(n, "HirErrorDecl")
        fieldStr("message", n.message)
    }
    // ---------- 理论参数与辅助记录（S-8/S-10/S-11；统一形态见 ADR-6 决策 2） ----------
    is HirTypeParam -> obj {
        head(n, "HirTypeParam")
        fieldStr("name", n.name)
        fieldSymbol("constraint", n.constraint)
        fieldNum("arity", n.arity)
    }
    is HirPropParam -> obj {
        head(n, "HirPropParam")
        fieldStr("name", n.name)
    }
    is HirParam -> obj {
        head(n, "HirParam")
        fieldStr("name", n.name)
        fieldNode("type", n.type)
        fieldSymbol("symbol", n.symbol)
        fieldStrings("annotations", n.annotations)
        fieldNode("default", n.default)
        fieldStringsNullable("destruct", n.destruct)
        // t108（R3 S1，C-8 按需）：推导元信息——仅推导位携带时发射（默认态零新字段，保基线）
        if (n.inferred || n.evidence.isNotEmpty()) {
            fieldBool("inferred", n.inferred)
            if (n.evidence.isNotEmpty()) fieldStrings("evidence", n.evidence)
        }
    }
    // ---------- 语句层（S-16~S-30；命题位语句承载证明义务，S-1 §3） ----------
    is HirVarStmt -> obj {
        head(n, "HirVarStmt")
        fieldStr("name", n.name)
        fieldSymbol("symbol", n.symbol)
        fieldStrings("annotations", n.annotations)
        fieldNode("type", n.type)
        fieldNode("value", n.value)
        fieldStringsNullable("destruct", n.destruct)
        fieldBool("deepCopy", n.deepCopy)
        // t108（R3 S1，C-8 按需）：推导元信息——仅推导位携带时发射（默认态零新字段，保基线）
        if (n.inferred || n.evidence.isNotEmpty()) {
            fieldBool("inferred", n.inferred)
            if (n.evidence.isNotEmpty()) fieldStrings("evidence", n.evidence)
        }
    }
    is HirAssign -> obj {
        head(n, "HirAssign")
        fieldNode("target", n.target)
        fieldNode("value", n.value)
        fieldBool("mutableTarget", n.mutableTarget)
    }
    is HirAxiom -> obj {
        head(n, "HirAxiom")
        fieldNodes("props", n.props)
        proof(n)
    }
    is HirByStmt -> obj {
        head(n, "HirByStmt")
        fieldNodes("props", n.props)
        proof(n)
    }
    is HirUnchecked -> obj {
        head(n, "HirUnchecked")
        fieldNode("inner", n.inner)
    }
    is HirExprStmt -> obj {
        head(n, "HirExprStmt")
        fieldNode("expr", n.expr)
    }
    is HirBecause -> obj {
        head(n, "HirBecause")
        fieldNode("form", n.form)
        fieldNodes("premises", n.premises)
        fieldNode("conclusion", n.conclusion)
        proof(n)
    }
    is HirWhenProp -> obj {
        head(n, "HirWhenProp")
        fieldNode("disjunction", n.disjunction)
        fieldNodes("arms", n.arms)
        proof(n)
    }
    // 命题 when 分支（S-21 辅助记录）：条件命题 + 分支体（统一形态，ADR-6 决策 2）
    is HirWhenPropArm -> obj {
        head(n, "HirWhenPropArm")
        fieldNode("condition", n.condition)
        fieldNodes("body", n.body)
    }
    is HirIfProp -> obj {
        head(n, "HirIfProp")
        fieldNode("prop", n.prop)
        fieldNodes("thenStmts", n.thenStmts)
        fieldNodes("elseStmts", n.elseStmts)
        proof(n)
    }
    is HirExistsIntro -> obj {
        head(n, "HirExistsIntro")
        fieldStrings("names", n.names)
        fieldNodes("witnesses", n.witnesses)
        fieldNode("prop", n.prop)
        proof(n)
    }
    is HirExistsElim -> obj {
        head(n, "HirExistsElim")
        fieldStr("name", n.name)
        fieldNode("prop", n.prop)
        fieldNodes("body", n.body)
        proof(n)
    }
    is HirLocalFun -> obj {
        head(n, "HirLocalFun")
        fieldNode("fn", n.fn)
    }
    is HirStrategy -> obj {
        head(n, "HirStrategy")
        fieldStr("strategy", n.strategy)
        fieldNode("goal", n.goal)
        proof(n)
    }
    is HirReturn -> obj {
        head(n, "HirReturn")
        fieldNode("expr", n.expr)
        fieldStrNullable("label", n.label)
    }
    is HirErrorStmt -> obj {
        head(n, "HirErrorStmt")
        fieldStr("message", n.message)
    }
    // ---------- 表达式层（S-31~S-37/S-40；程序面值空间） ----------
    is HirIntLit -> obj { head(n, "HirIntLit"); fieldStr("value", n.value) }
    is HirFloatLit -> obj { head(n, "HirFloatLit"); fieldStr("value", n.value) }
    is HirStrLit -> obj { head(n, "HirStrLit"); fieldStr("value", n.value) }
    // 程序面 ⊤/⊥ Bool 字面量（t20 补绘，ADR-19 §C1；命题位对应 HirPTop/HirPBot 的独立发射）
    is HirTop -> obj { head(n, "HirTop"); fieldNode("type", n.type) }
    is HirBot -> obj { head(n, "HirBot"); fieldNode("type", n.type) }
    is HirNameRef -> obj {
        head(n, "HirNameRef")
        fieldStr("name", n.name)
        fieldSymbol("symbol", n.symbol)
        fieldNode("type", n.type)
    }
    // 程序面双目/一元运算（t7 契约补绘：HirBinExpr/HirUniExpr，见 hir/Expr.kt 注释）
    is HirBinExpr -> obj {
        head(n, "HirBinExpr")
        fieldStr("op", n.op)
        fieldNode("left", n.left)
        fieldNode("right", n.right)
        fieldNode("type", n.type)
    }
    is HirUniExpr -> obj {
        head(n, "HirUniExpr")
        fieldStr("op", n.op)
        fieldNode("operand", n.operand)
        fieldNode("type", n.type)
    }
    // t101（R3 S6 语法层中间态）：&v 借用——type 透传内层（零语义）
    is HirBorrow -> obj {
        head(n, "HirBorrow")
        fieldNode("operand", n.operand)
        fieldNode("type", n.type)
    }
    is HirCall -> obj {
        head(n, "HirCall")
        fieldNode("callee", n.callee)
        fieldNodes("args", n.args)
        fieldSymbol("symbol", n.symbol)
        fieldNode("type", n.type)
        fieldStrNullable("dictionary", n.dictionary)
        fieldStrNullable("slot", n.slot)
        fieldStrings("dictArgs", n.dictArgs)
        fieldBool("methodSugar", n.methodSugar)
        fieldBool("awaitPoint", n.awaitPoint)
        fieldBool("calleeImpure", n.calleeImpure)
    }
    is HirField -> obj {
        head(n, "HirField")
        fieldNode("target", n.target)
        fieldStr("name", n.name)
        fieldSymbol("field", n.field)
        fieldNode("type", n.type)
    }
    is HirFunRef -> obj {
        head(n, "HirFunRef")
        fieldNode("target", n.target)
        fieldStr("name", n.name)
        fieldSymbol("symbol", n.symbol)
        fieldNode("type", n.type)
    }
    is HirTypeTest -> obj {
        head(n, "HirTypeTest")
        fieldNode("target", n.target)
        fieldNode("type", n.type)
        fieldBool("smartCast", n.smartCast)
    }
    is HirTask -> obj {
        head(n, "HirTask")
        fieldNode("body", n.body)
        fieldNode("type", n.type)
    }
    is HirFlow -> obj {
        head(n, "HirFlow")
        fieldNode("body", n.body)
        fieldNode("type", n.type)
    }
    is HirInst -> obj {
        head(n, "HirInst")
        fieldNode("target", n.target)
        fieldNodes("terms", n.terms)
        fieldSymbol("symbol", n.symbol)
        fieldNode("type", n.type)
    }
    is HirLambda -> obj {
        head(n, "HirLambda")
        fieldNodes("params", n.params)
        fieldNode("body", n.body)
        fieldBool("implicitIt", n.implicitIt)
        fieldNode("type", n.type)
        fieldEnum("purity", n.purity)
    }
    is HirAnonFun -> obj {
        head(n, "HirAnonFun")
        fieldStr("name", n.name)
        fieldNodes("theory", n.theory)
        fieldNodes("params", n.params)
        fieldNodes("preps", n.preps)
        fieldNode("retType", n.retType)
        fieldNodes("posts", n.posts)
        fieldNode("body", n.body)
        fieldNode("type", n.type)
        fieldEnum("purity", n.purity)
    }
    is HirTuple -> obj {
        head(n, "HirTuple")
        fieldNodes("items", n.items)
        fieldNode("type", n.type)
    }
    is HirStructCtor -> obj {
        head(n, "HirStructCtor")
        fieldStr("struct", n.struct)
        // 有序对象数组（保字段声明顺序；对象映射会丢失顺序且重名字段相吞——见 ADR-6 决策 5）
        arr("assigns") {
            for ((fieldName, expr) in n.assigns) item {
                obj {
                    fieldStr("field", fieldName)
                    fieldNode("value", expr)
                }
            }
        }
        fieldSymbol("symbol", n.symbol)
        fieldNode("type", n.type)
    }
    is HirByExpr -> obj {
        head(n, "HirByExpr")
        fieldNode("target", n.target)
        fieldNodes("props", n.props)
        proof(n)
    }
    is HirVarExpr -> obj {
        head(n, "HirVarExpr")
        fieldStr("name", n.name)
        fieldSymbol("symbol", n.symbol)
        fieldStrings("annotations", n.annotations)
        fieldNode("type", n.type)
        fieldNode("value", n.value)
        fieldStringsNullable("destruct", n.destruct)
        fieldBool("deepCopy", n.deepCopy)
        // t108（R3 S1，C-8 按需）：推导元信息——仅推导位携带时发射（默认态零新字段，保基线）
        if (n.inferred || n.evidence.isNotEmpty()) {
            fieldBool("inferred", n.inferred)
            if (n.evidence.isNotEmpty()) fieldStrings("evidence", n.evidence)
        }
    }
    is HirBlock -> obj {
        head(n, "HirBlock")
        fieldNodes("stmts", n.stmts)
    }
    is HirIf -> obj {
        head(n, "HirIf")
        fieldNode("cond", n.cond)
        fieldNode("thenBlock", n.thenBlock)
        fieldNode("elseBlock", n.elseBlock)
        fieldNode("type", n.type)
        fieldBool("diverges", n.diverges)
    }
    is HirWhen -> obj {
        head(n, "HirWhen")
        fieldNode("subject", n.subject)
        fieldStrNullable("subjectBinding", n.subjectBinding)
        fieldNodes("arms", n.arms)
        fieldNode("type", n.type)
        fieldBool("diverges", n.diverges)
        fieldBool("exhaustive", n.exhaustive)
    }
    is HirWhenArm -> obj {
        head(n, "HirWhenArm")
        fieldNode("pattern", n.pattern)
        fieldNode("guard", n.guard)
        fieldNode("body", n.body)
        fieldNode("cond", n.cond)
    }
    is HirErrorExpr -> obj {
        head(n, "HirErrorExpr")
        fieldStr("message", n.message)
    }
    // ---------- 命题面（S-1 §3 唯一住所；JSON 只序列化 HirProp，不引入第二表示） ----------
    is HirPTop -> obj { head(n, "HirPTop") }
    is HirPBot -> obj { head(n, "HirPBot") }
    is HirPNot -> obj { head(n, "HirPNot"); fieldNode("p", n.p) }
    is HirPAnd -> obj { head(n, "HirPAnd"); fieldNode("a", n.a); fieldNode("b", n.b) }
    is HirPOr -> obj { head(n, "HirPOr"); fieldNode("a", n.a); fieldNode("b", n.b) }
    is HirPImp -> obj { head(n, "HirPImp"); fieldNode("a", n.a); fieldNode("b", n.b) }
    is HirPIff -> obj { head(n, "HirPIff"); fieldNode("a", n.a); fieldNode("b", n.b) }
    is HirPForall -> obj {
        head(n, "HirPForall"); fieldStrings("names", n.names); fieldNode("body", n.body)
    }
    is HirPExists -> obj {
        head(n, "HirPExists"); fieldStrings("names", n.names); fieldNode("body", n.body)
    }
    is HirPExists1 -> obj {
        head(n, "HirPExists1"); fieldStrings("names", n.names); fieldNode("body", n.body)
    }
    is HirPAtom -> obj {
        head(n, "HirPAtom"); fieldStr("head", n.head); fieldNodes("terms", n.terms)
    }
    // ---------- 命题项（P-STRUCT-EQ 结构相等；位置只作呈现元数据随节点落盘，D-9） ----------
    is HirTInt -> obj { head(n, "HirTInt"); fieldStr("value", n.value) }
    is HirTStr -> obj { head(n, "HirTStr"); fieldStr("value", n.value) }
    is HirTName -> obj { head(n, "HirTName"); fieldStr("name", n.name); fieldSymbol("symbol", n.symbol) }
    is HirTDollar -> obj { head(n, "HirTDollar") }
    is HirTParamRef -> obj { head(n, "HirTParamRef"); fieldNum("index", n.index) }
    is HirTTop -> obj { head(n, "HirTTop") }
    is HirTBot -> obj { head(n, "HirTBot") }
    is HirTBin -> obj {
        head(n, "HirTBin"); fieldStr("op", n.op); fieldNode("left", n.left); fieldNode("right", n.right)
    }
    is HirTUni -> obj {
        head(n, "HirTUni"); fieldStr("op", n.op); fieldNode("operand", n.operand)
    }
    is HirTCall -> obj {
        head(n, "HirTCall"); fieldNode("callee", n.callee); fieldNodes("args", n.args)
    }
    is HirTInst -> obj {
        head(n, "HirTInst"); fieldNode("target", n.target); fieldNodes("terms", n.terms)
    }
    is HirTField -> obj {
        head(n, "HirTField"); fieldNode("target", n.target); fieldStr("name", n.name)
    }
    is HirTSymbolCall -> obj {
        head(n, "HirTSymbolCall"); fieldStr("op", n.op); fieldNodes("args", n.args)
    }
    // ---------- 类型层（S-15） ----------
    is HirNamedType -> obj {
        head(n, "HirNamedType"); fieldStr("name", n.name); fieldNodes("args", n.args); fieldSymbol("symbol", n.symbol)
    }
    is HirFunType -> obj {
        head(n, "HirFunType")
        fieldNodes("params", n.params)
        fieldNode("ret", n.ret)
        fieldEnum("purity", n.purity)
        fieldNodes("theory", n.theory)
        fieldNodesNullable("context", n.context)
    }
    is HirTupleType -> obj { head(n, "HirTupleType"); fieldNodes("items", n.items) }
    is HirMutType -> obj { head(n, "HirMutType"); fieldNode("inner", n.inner) }
    is HirAtomicType -> obj { head(n, "HirAtomicType"); fieldNode("inner", n.inner) }
    is HirRefType -> obj { head(n, "HirRefType"); fieldNode("inner", n.inner) }   // t95：@ref T（零语义中间态）
    is HirUnionType -> obj { head(n, "HirUnionType"); fieldNodes("variants", n.variants) }
    is HirQualifiedType -> obj {
        head(n, "HirQualifiedType")
        fieldStrings("module", n.module)
        fieldStr("name", n.name)
        fieldNodes("args", n.args)
        fieldSymbol("symbol", n.symbol)
    }
    // 卷 TS-31：HirSelfType 固定输出 {"k": "self"}——不带 kind/nid/pos/notes
    // （Self 是待替换占位 TS-26，判定在检查层，HIR 不承载位置元数据以外的判据信息）。
    is HirSelfType -> obj { fieldStr("k", "self") }
    is HirErrorType -> obj { head(n, "HirErrorType"); fieldStr("message", n.message) }
    // ---------- 模式层（S-42） ----------
    is HirPatLit -> obj { head(n, "HirPatLit"); fieldNode("value", n.value) }
    is HirPatCtor -> obj {
        head(n, "HirPatCtor"); fieldStr("name", n.name); fieldNodes("args", n.args); fieldSymbol("ctor", n.ctor)
    }
    is HirPatTuple -> obj { head(n, "HirPatTuple"); fieldNodes("items", n.items) }
    is HirPatIs -> obj {
        head(n, "HirPatIs")
        fieldNode("type", n.type)
        fieldStrNullable("bind", n.bind)
        fieldSymbol("bindSymbol", n.bindSymbol)
    }
    is HirPatBind -> obj { head(n, "HirPatBind"); fieldStr("name", n.name); fieldSymbol("symbol", n.symbol) }
    is HirPatElse -> obj { head(n, "HirPatElse") }
    is HirPatCond -> obj { head(n, "HirPatCond") }
    is HirErrorPattern -> obj { head(n, "HirErrorPattern"); fieldStr("message", n.message) }
}

/** 条目透传：HirDeclEntry/HirStmtEntry 不持身份，直接发射所含节点。 */
private fun JsonWriter.entry(e: HirEntry): Unit = when (e) {
    is HirDeclEntry -> node(e.decl)
    is HirStmtEntry -> node(e.stmt)
}

// =========== 字段发射工具（节点统一形态与横切结构，ADR-1 §3.7） ==========

/** 统一形态四公共字段：kind / nid / pos / notes。 */
private fun JsonWriter.head(n: HirNode, kind: String) {
    field("kind") { str(kind) }
    field("nid") { num(n.nid) }
    field("pos") { range(n.origin) }
    arr("notes") { for (note in n.notes) item { note(note) } }
}

/** pos 紧凑串：「l:c-l:c」；单点（end == null）取「l:c」。 */
private fun JsonWriter.range(r: SourceRange) {
    val start = "${r.start.line}:${r.start.col}"
    str(if (r.end == null) start else "$start-${r.end.line}:${r.end.col}")
}

/** 解析期留痕（D-9：脱糖与提示/补充随节点落盘）。 */
private fun JsonWriter.note(n: HirNote) = obj {
    fieldEnum("kind", n.kind)
    fieldStr("code", n.code)
    fieldStr("message", n.message)
    field("pos") { range(n.pos) }
    fieldStrNullable("from", n.from)
    fieldStrNullable("to", n.to)
}

/** envelope 诊断条目（四族小写，D-10；nid 反引节点身份，D-9）。 */
private fun JsonWriter.diag(d: Diag) = obj {
    fieldEnum("severity", d.severity)
    fieldStr("code", d.code)
    field("pos") { range(d.pos) }
    fieldStr("message", d.message)
    fieldStr("module", d.modulePath)
    field("nid") { if (d.nid == null) nil() else num(d.nid) }
}

/** 符号引用（id 权威、kind/name 冗余可读，ADR-1 §3.7）。 */
private fun JsonWriter.symbolObj(s: SymbolRef) = obj {
    fieldNum("id", s.id)
    fieldEnum("kind", s.kind)
    fieldStr("name", s.name)
}

/** 证明义务三字段：proofContext（facts + matched）/ rule / verdict（ADR-1 §3.7）。 */
private fun JsonWriter.proof(o: HirProofObligation) {
    // 接口属性不可智能转换，先取局部 val 再判空（契约字段只读，拷贝无二义性）
    val matched = o.proofContext.matched
    val verdict = o.verdict
    field("proofContext") {
        obj {
            arr("facts") { for (fact in o.proofContext.facts) item { node(fact) } }
            field("matched") { if (matched == null) nil() else symbolObj(matched) }
        }
    }
    fieldStrNullable("rule", o.rule)
    field("verdict") { if (verdict == null) nil() else obj {
        fieldEnum("status", verdict.status)
        fieldStrNullable("diagCode", verdict.diagCode)
    } }
}

// ---------- 字段发射简写（null 显式输出——固定字段集，D-9/schema 稳定性） ----------

private fun JsonWriter.fieldStr(name: String, v: String) = field(name) { str(v) }

private fun JsonWriter.fieldStrNullable(name: String, v: String?) = field(name) { if (v == null) nil() else str(v) }

private fun JsonWriter.fieldBool(name: String, v: Boolean) = field(name) { bool(v) }

private fun JsonWriter.fieldNum(name: String, v: Int) = field(name) { num(v) }

private fun JsonWriter.fieldEnum(name: String, v: Enum<*>) = field(name) { str(v.name.lowercase()) }

private fun JsonWriter.fieldSymbol(name: String, v: SymbolRef?) = field(name) { if (v == null) nil() else symbolObj(v) }

private fun JsonWriter.fieldNode(name: String, v: HirNode?): Unit = field(name) { if (v == null) nil() else node(v) }

private fun JsonWriter.fieldNodes(name: String, list: List<HirNode>): Unit = arr(name) {
    for (x in list) item { node(x) }
}

private fun JsonWriter.fieldNodesNullable(name: String, list: List<HirNode>?): Unit = field(name) {
    if (list == null) nil() else arr { for (x in list) item { node(x) } }
}

private fun JsonWriter.fieldStrings(name: String, list: List<String>) = arr(name) {
    for (x in list) item { str(x) }
}

private fun JsonWriter.fieldStringsNullable(name: String, list: List<String>?) = field(name) {
    if (list == null) nil() else arr { for (x in list) item { str(x) } }
}
