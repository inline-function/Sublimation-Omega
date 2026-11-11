package sublimation.frontend.semantic

import sublimation.frontend.hir.HirAnonFun
import sublimation.frontend.hir.HirBlock
import sublimation.frontend.hir.HirCall
import sublimation.frontend.hir.HirExprStmt
import sublimation.frontend.hir.HirFunDecl
import sublimation.frontend.hir.HirLambda
import sublimation.frontend.hir.HirPurity
import sublimation.frontend.hir.HirReturn
import sublimation.frontend.hir.HirVarExpr
import sublimation.frontend.hir.HirVarStmt
import sublimation.frontend.parser.parseSourceWithNotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * t121（R-16 A1 · I-1 收口第一期）事件框架锁：t94 纯度经主趟 onCall 事件累加（消 containsImpureCall 族）
 * 与 t97 停机闸订阅的**行为零变**锁定——口径与旧后置扫描逐字对齐（闭包吸收 / 嵌套不外溢 / I-4 递归 / 局部不执行）。
 * 事件框架底层：enterEffectScope/exitEffectScope + onCall/onOpCall（见 Elaborator.kt）。
 */
class T121EventFrameTest {
    private fun compileLambda(src: String): List<HirLambda> {
        val pr = parseSourceWithNotes(src, "t121.subl")
        val rep = compileWithRounds(pr.file, pr.notes, scriptMode = false)
        val lambdas = mutableListOf<HirLambda>()
        fun walk(e: Any?) {
            when (e) {
                is HirLambda -> { lambdas.add(e); e.body?.let { walk(it) } }
                is HirAnonFun -> e.body?.let { walk(it) }
                is HirCall -> { walk(e.callee); e.args.forEach { walk(it) } }
                is HirBlock -> e.stmts.forEach { s ->
                    when (s) {
                        is HirExprStmt -> walk(s.expr)
                        is HirVarStmt -> walk(s.value)
                        is HirReturn -> s.expr?.let { walk(it) }
                        else -> {}
                    }
                }
                is HirVarExpr -> walk(e.value)
                else -> {}
            }
        }
        rep.hir.entries.forEach { ent ->
            val d = (ent as? sublimation.frontend.hir.HirDeclEntry)?.decl
            if (d is HirFunDecl) walk(d.body)
        }
        return lambdas
    }

    /** L1（s1 口径）：impure 闭包被直接调用——调用位记效应（purity=IMPURE）+ 闸落 E-IMPURE-CALL。 */
    @Test fun impure闭包直接调用落闸且纯度下推() {
        val pr = parseSourceWithNotes("fun q(): Null { ({ => print(\"x\") })() }", "t121.subl")
        val rep = compileWithRounds(pr.file, pr.notes, scriptMode = false)
        assertEquals(1, rep.diag.diags.count { it.code == "E-IMPURE-CALL" })
        val lambdas = compileLambda("fun q(): Null { ({ => print(\"x\") })() }")
        assertEquals(1, lambdas.size)
        assertEquals(HirPurity.IMPURE, lambdas[0].purity)
    }

    /** L2（s2 口径）：impure 闭包仅被吸收（存入变量）——效应记在闭包自身，外层零诊断。 */
    @Test fun impure闭包吸收外层零闸() {
        val pr = parseSourceWithNotes("fun q(): Null { var g = { => print(\"x\") } }", "t121.subl")
        val rep = compileWithRounds(pr.file, pr.notes, scriptMode = false)
        assertEquals(0, rep.diag.diags.count { it.severity == sublimation.frontend.diag.Severity.ERROR })
        val lambdas = compileLambda("fun q(): Null { var g = { => print(\"x\") } }")
        assertEquals(HirPurity.IMPURE, lambdas[0].purity)
    }

    /** L3（s4 口径，事件化关键锁）：嵌套闭包效应不外溢——内层 IMPURE、外层 PURE。 */
    @Test fun 嵌套闭包效应不外溢() {
        val lambdas = compileLambda("fun q(): Null { var g = { => var h = { => print(\"y\") } } }")
        assertEquals(2, lambdas.size)
        assertEquals(HirPurity.PURE, lambdas[0].purity, "外层闭包只造内层闭包——效应不外溢（创建闭包≠效应）")
        assertEquals(HirPurity.IMPURE, lambdas[1].purity)
    }

    /** L4（I-4 / s3 口径）：HirUnchecked 豁免检查不豁免效应——unchecked 体仍计 IMPURE。 */
    @Test fun unchecked不豁免效应() {
        val lambdas = compileLambda("fun q(): Null { var g = { => unchecked print(\"x\") } }")
        assertEquals(1, lambdas.size)
        assertEquals(HirPurity.IMPURE, lambdas[0].purity, "I-4：unchecked 包住的是检查豁免，效应仍计（t94 既有口径）")
    }

    /** L5（s5 口径）：局部 fun 声明不执行——其体效应不并入外层；t96 闸（后置 walker）照旧独立落 1 处。 */
    @Test fun 局部fun声明不执行() {
        val pr = parseSourceWithNotes("fun q(): Null { fun inner() { print(\"z\") } }", "t121.subl")
        val rep = compileWithRounds(pr.file, pr.notes, scriptMode = false)
        assertEquals(1, rep.diag.diags.count { it.code == "E-IMPURE-CALL" },
            "仅 t96 后置闸对局部 fun 体的 1 处落闸——主趟事件累加不得增减")
    }

    /** L6（anonFun 同道）：匿名具名 fun 体效应经事件累加入自身 purity。 */
    @Test fun 匿名fun体效应入自身纯度() {
        val src = "fun q(): Null { var g = fun g2() { print(\"w\") } }"
        val pr = parseSourceWithNotes(src, "t121.subl")
        val rep = compileWithRounds(pr.file, pr.notes, scriptMode = false)
        assertEquals(0, rep.diag.diags.count { it.severity == sublimation.frontend.diag.Severity.ERROR })
    }
}