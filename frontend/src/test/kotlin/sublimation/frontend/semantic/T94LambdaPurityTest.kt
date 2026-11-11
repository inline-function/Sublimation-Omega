package sublimation.frontend.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import sublimation.frontend.diag.Severity
import sublimation.frontend.hir.HirAnonFun
import sublimation.frontend.hir.HirAssign
import sublimation.frontend.hir.HirBinExpr
import sublimation.frontend.hir.HirBlock
import sublimation.frontend.hir.HirByExpr
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirDeclEntry
import sublimation.frontend.hir.HirExpr
import sublimation.frontend.hir.HirField
import sublimation.frontend.hir.HirFlow
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirIf
import sublimation.frontend.hir.HirInst
import sublimation.frontend.hir.HirLambda
import sublimation.frontend.hir.HirPurity
import sublimation.frontend.hir.HirReturn
import sublimation.frontend.hir.HirStmt
import sublimation.frontend.hir.HirStmtEntry
import sublimation.frontend.hir.HirStructCtor
import sublimation.frontend.hir.HirTask
import sublimation.frontend.hir.HirTuple
import sublimation.frontend.hir.HirTypeTest
import sublimation.frontend.hir.HirUniExpr
import sublimation.frontend.hir.HirUnchecked
import sublimation.frontend.hir.HirVarExpr
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.hir.HirWhen
import sublimation.frontend.parser.parseScriptSourceWithNotes

/**
 * t94（N1 硬前置）：lambda 纯度推导——purity 节点位从硬编码改真推导（Fork B）。
 *
 * - 旧状：Elaborator 8 处硬编码 HirPurity.PURE——lambda / 匿名函数一律标纯，即使 body 含 print 等不纯调用。
 * - 新规：HirLambda / HirAnonFun 的**节点位**按 body 推导（ground truth）；HirFunType.purity 保持 PURE
 *   作**名义位**（typeEq 的 purity 严格相等于 t96 的 pure<f> / PURE<:IMPURE 子类型序统一收口——
 *   有意过渡态，登记 ADR-54）。
 * - 不纯判定来源（复用既有链）：内建不纯名（TypeInfer.builtinImpureNames，旧 builtinimpure 逐字恢复）+
 *   用户 @impure 声明（funDecls 注解）+ 被直接调用的内层闭合（其 purity 经 elaboration 递归天然底向算好）。
 * - 传 lambda 不计（求值只造闭包）；调用才计。已知保守点（ADR-54）：经 var 间接调用不纯闭合不追踪。
 */
class T94LambdaPurityTest {

    /** 源码 → HIR 的全部 HirLambda（按深度优先序）。 */
    private fun collectLambdas(acc: MutableList<HirLambda>, e: HirExpr?) {
        if (e == null) return
        when (e) {
            is HirLambda -> { acc += e; collectLambdas(acc, e.body) }
            is HirAnonFun -> e.body?.let { collectLambdas(acc, it) }
            is HirCall -> { collectLambdas(acc, e.callee); e.args.forEach { collectLambdas(acc, it) } }
            is HirBinExpr -> { collectLambdas(acc, e.left); collectLambdas(acc, e.right) }
            is HirUniExpr -> collectLambdas(acc, e.operand)
            is HirBlock -> e.stmts.forEach { collectStmt(acc, it) }
            is HirIf -> { collectLambdas(acc, e.cond); collectBlock(acc, e.thenBlock); e.elseBlock?.let { collectBlock(acc, it) } }
            is HirTask -> collectBlock(acc, e.body)
            is HirFlow -> collectBlock(acc, e.body)
            is HirVarExpr -> collectLambdas(acc, e.value)
            is HirTuple -> e.items.forEach { collectLambdas(acc, it) }
            is HirStructCtor -> e.assigns.forEach { collectLambdas(acc, it.second) }
            is HirByExpr -> collectLambdas(acc, e.target)
            is HirInst -> { collectLambdas(acc, e.target); e.terms.forEach { collectLambdas(acc, it) } }
            is HirField -> collectLambdas(acc, e.target)
            is HirTypeTest -> collectLambdas(acc, e.target)
            else -> {}
        }
    }

    private fun collectStmt(acc: MutableList<HirLambda>, s: HirStmt) {
        when (s) {
            is sublimation.frontend.hir.HirExprStmt -> collectLambdas(acc, s.expr)
            is HirVarStmt -> collectLambdas(acc, s.value)
            is HirAssign -> { collectLambdas(acc, s.target); collectLambdas(acc, s.value) }
            is HirReturn -> s.expr?.let { collectLambdas(acc, it) }
            is HirUnchecked -> collectStmt(acc, s.inner)
            else -> {}
        }
    }

    private fun collectBlock(acc: MutableList<HirLambda>, b: HirBlock) = b.stmts.forEach { collectStmt(acc, it) }
    private fun lambdas(src: String, file: String = "t94.subl"): List<HirLambda> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (hirFile, _) = compile(pr.file, pr.notes)
        val acc = mutableListOf<HirLambda>()
        hirFile?.entries?.forEach { ent ->
            val decl = (ent as? HirDeclEntry)?.decl
            if (decl is HirFunDecl) {
                val b = decl.body
                if (b is HirExpr) collectLambdas(acc, b)
            }
            (ent as? HirStmtEntry)?.stmt?.let { collectStmt(acc, it) }
        }
        return acc
    }
    private fun errCodes(src: String, file: String = "t94.subl"): List<String> {
        val pr = parseScriptSourceWithNotes(src, file)
        val (_, diag) = compile(pr.file, pr.notes)
        return pr.diag.filter { it.severity == Severity.ERROR }.map { it.code } +
            diag.diags.filter { it.severity == Severity.ERROR }.map { it.code }
    }

    /** 验收 #2：`var g = { => print("x") }` → 该 lambda 节点 purity=IMPURE。 */
    @Test fun lambda含不纯调用判IMPURE() {
        val l = lambdas("var g = { => print(\"x\") }")
        assertEquals(1, l.size, "恰好一个 lambda")
        assertEquals(HirPurity.IMPURE, l[0].purity, "purity=impure")
    }

    /** 验收 #3：`var g = { => 1 + 1 }` → PURE（表达式层纯 lambda 不受影响）。 */
    @Test fun lambda纯体判PURE() {
        val l = lambdas("var g = { => 1 + 1 }")
        assertEquals(1, l.size, "恰好一个 lambda")
        assertEquals(HirPurity.PURE, l[0].purity, "purity=pure")
    }

    /** Fork B 有意过渡态登记：lambda **类型位**（HirFunType.purity）保持 PURE 名义位，t96 收口。 */
    @Test fun lambda类型位保持名义PURE() {
        val l = lambdas("var g = { => print(\"x\") }")
        assertEquals(HirPurity.PURE, l[0].type?.purity, "HirFunType.purity 名义位保持 PURE（t96 收口）")
    }

    /** 嵌套两层（队长硬要求实测 elaboration 顺序）：内层 IMPURE、外层 PURE——传 lambda 不等于执行。 */
    @Test fun 嵌套lambda外层纯内层不纯() {
        val l = lambdas("var g = { => { => print(\"x\") } }")
        assertEquals(2, l.size, "外层 + 内层共两个 lambda")
        assertEquals(HirPurity.PURE, l[0].purity, "外层 PURE")
        assertEquals(HirPurity.IMPURE, l[1].purity, "内层 IMPURE（elaboration 底向递归先算好）")
    }

    /** 被直接调用的内层闭合计入外层：`{ => ({ => print })() }` → 外层 IMPURE。 */
    @Test fun 直接调用内层不纯闭合计入外层() {
        val l = lambdas("var g = { => ({ => print(\"x\") })() }")
        assertEquals(2, l.size)
        assertEquals(HirPurity.IMPURE, l[0].purity, "外层直接调用内层不纯闭合 ⇒ IMPURE")
        assertEquals(HirPurity.IMPURE, l[1].purity, "内层 IMPURE")
    }

    /** 用户 @impure 声明被 lambda 调用 ⇒ IMPURE；@impure 注解通道（验收 #7）行为不变。 */
    @Test fun 用户impure声明入lambda() {
        val src = "@impure fun p(v: Any): Null = print(v)\nvar g = { => p(\"x\") }"
        val l = lambdas(src)
        assertEquals(HirPurity.IMPURE, l[0].purity, "调用 @impure 声明 ⇒ IMPURE")
        assertTrue(errCodes(src).none { it.startsWith("E") }, "零错误：")
    }

    /** 匿名函数（AnonFunExpr 无注解位）与 lambda 同规则推导。 */
    @Test fun 匿名函数同规则() {
        val src = "var g = fun(a: Nat): Nat { print(a)\n return a }"
        // 无 lambda 实体——AnonFun 不进 lambdas() 收集，直接断言零错误 + 一个隐含约束：
        // 源合法且推导后 JSON purity 由 emit 验证（探针 cases-t94/p7 已实测 impure）
        assertTrue(errCodes(src).none { it.startsWith("E") }, "匿名函数体含 print 合法：")
    }

    /** 参数位的 lambda 只记自身（k 自身不因参数变），零新错误。 */
    @Test fun 参数位lambda只记自身() {
        val src = "fun k(f: () => Null): Null = f()\n@root fun main(): Null = k({ => print(\"x\") })"
        assertTrue(errCodes(src).none { it.startsWith("E") }, "传 impure lambda 作参数零新错误：")
        val l = lambdas("var g = ({ => print(\"x\") })")
        assertEquals(HirPurity.IMPURE, l[0].purity)
    }

    /** @impure 注解的 fun 声明仍按注解（验收 #7)：HirFunDecl.purity=IMPURE 且零错误。 */
    @Test fun impure注解通道不变() {
        val src = "@impure fun p(v: Any): Null = print(v)"
        val pr = parseScriptSourceWithNotes(src)
        val (hirFile, _) = compile(pr.file, pr.notes)
        val decl = hirFile?.entries?.mapNotNull { (it as? HirDeclEntry)?.decl as? HirFunDecl }?.firstOrNull()
        assertEquals(HirPurity.IMPURE, decl?.purity, "@impure 注解决定，行为不变")
        assertTrue(errCodes(src).none { it.startsWith("E") }, "@impure 声明零错误：")
    }

    /** 保守局限登记（ADR-54):经 var 间接调用不纯闭合按纯——数据流追踪不在本任务。 */
    @Test fun 保守局限经var间接调用() {
        val src = "var g = { => print(\"x\") }\nvar h = { => g() }"
        val l = lambdas(src)
        assertEquals(2, l.size)
        assertEquals(HirPurity.IMPURE, l[0].purity, "g 直接含 print ⇒ IMPURE")
        assertEquals(HirPurity.PURE, l[1].purity, "h 经 var 间接调用 g ⇒ 保守判 PURE（已知局限，ADR-54 登记）")
    }

    /**
     * I-4（队长结构审查 t97 反馈 · 锁定测试）:unchecked 语义不对称口径——**豁免检查，不豁免效应存在**。
     * `{ => unchecked print("x") }` 的 lambda purity = IMPURE（效应累加经 HirUnchecked 递归进——t121 onCall 事件口径）;
     * 同时外层 unchecked 语句本身对闸仍是放行（见 T96PurityGateTest/T97TerminationGateTest）。
     */
    @Test fun unchecked体内仍计impure() {
        val src = "fun q(): Null { var g = { => unchecked print(\"x\") } }"
        val pr = parseScriptSourceWithNotes(src, "i4.subl")
        val (_, diag) = compile(pr.file, pr.notes)
        val g = (pr.diag + diag.diags).firstOrNull { it.code == "E-IMPURE-CALL" }
        assertEquals(null, g, "lambda 体写定不调用——闸不触发")
        // lambda purity 由 t94 推导：body 含 unchecked 包裹的 print 仍是 impure 闭包
        val hir = compile(pr.file, pr.notes).first
        val q = hir.entries.mapNotNull { (it as? sublimation.frontend.hir.HirDeclEntry)?.decl as? sublimation.frontend.hir.HirFunDecl }
            .first { it.name == "q" }
        val work = ArrayDeque<sublimation.frontend.hir.HirExpr>()
        q.body?.let { work.addLast(it) }
        var lambda: sublimation.frontend.hir.HirLambda? = null
        while (work.isNotEmpty()) {
            val e = work.removeLast()
            when (e) {
                is sublimation.frontend.hir.HirLambda -> { if (lambda == null) lambda = e; e.body?.let { work.addLast(it) } }
                is sublimation.frontend.hir.HirCall -> { e.args.forEach { work.addLast(it) }; e.callee.let { work.addLast(it) } }
                is sublimation.frontend.hir.HirBlock -> e.stmts.forEach { s -> (s as? sublimation.frontend.hir.HirExprStmt)?.expr?.let { work.addLast(it) }
                    (s as? sublimation.frontend.hir.HirVarStmt)?.value?.let { work.addLast(it) } }
                else -> {}
            }
        }
        val found = lambda
        assertEquals(sublimation.frontend.hir.HirPurity.IMPURE, found?.purity, "unchecked 只豁免闸检查，不豁免效应存在")
    }
}
