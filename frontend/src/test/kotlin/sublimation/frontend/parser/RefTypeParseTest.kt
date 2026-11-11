package sublimation.frontend.parser

import sublimation.frontend.ast.DeclEntry
import sublimation.frontend.ast.StmtEntry
import sublimation.frontend.ast.FunDecl
import sublimation.frontend.ast.MutType
import sublimation.frontend.ast.NamedType
import sublimation.frontend.ast.RefType
import sublimation.frontend.ast.VarStmt
import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * t95（N2 @ref 语法层）：@ref T / @ref @mut T 的解析形态与非法位置诊断。
 * 叠加序 = 书写序（与 @mut 注解通道同构，不规范化）：@ref @mut T → RefType(MutType(T))。
 * 纯语法层——@ref 在本期「可解析、无效果」（语义四规则是 t97 的域，此处不测）。
 */
class RefTypeParseTest {

    private fun scriptStmt(src: String, i: Int): VarStmt {
        val r = parseScriptSourceWithNotes(src, "t.subl")
        assertTrue(r.diag.none { it.severity == Severity.ERROR }, "脚本不应有解析错：${r.diag.map { it.message }}")
        val s = (r.file.entries[i] as StmtEntry).stmt
        return assertIs<VarStmt>(s)
    }

    @Test
    fun `t95_1_refType单注解解析`() {
        val v = scriptStmt("var b: @ref T = a", 0)
        val ty = assertIs<RefType>(v.type)
        assertEquals("T", (ty.inner as NamedType).name)
    }

    @Test
    fun `t95_2_refMut叠加解析`() {
        val v = scriptStmt("var b: @ref @mut T = a", 0)
        val outer = assertIs<RefType>(v.type)
        val inner = assertIs<MutType>(outer.inner)
        assertEquals("T", (inner.inner as NamedType).name)
    }

    @Test
    fun `t95_3_叠加序等书写序不规范化`() {
        val v = scriptStmt("var b: @mut @ref T = a", 0)
        val outer = assertIs<MutType>(v.type)
        assertIs<RefType>(outer.inner)
        // 反向写法与正向不同构：RefType(MutType(T)) ≠ MutType(RefType(T))——顺序敏感
        val v2 = scriptStmt("var b: @ref @mut T = a", 0)
        assertIs<RefType>((v2 as VarStmt).type)
    }

    @Test
    fun `t95_4_形参类型位ref解析`() {
        val r = parseScriptSourceWithNotes("fun f(a: @ref T): Null", "t.subl")
        assertTrue(r.diag.none { it.severity == Severity.ERROR })
        val d = (r.file.entries[0] as DeclEntry).decl as FunDecl
        assertIs<RefType>(d.params[0].type)
    }

    @Test
    fun `t95_5_返回类型位ref解析`() {
        val r = parseScriptSourceWithNotes("fun f(): @ref T = a", "t.subl")
        assertTrue(r.diag.none { it.severity == Severity.ERROR }, "${r.diag.map { it.message }}")
        val d = (r.file.entries[0] as DeclEntry).decl as FunDecl
        assertIs<RefType>(d.retType)
    }

    @Test
    fun `t95_neg1_函数体语句位ref落诊断`() {
        // @ref 不是语句：注解后必须跟 var（S-29 值注解通道），否则落语法诊断
        val r = parseScriptSourceWithNotes("fun f(): Null { @ref x }", "t.subl")
        assertTrue(r.diag.any { it.severity == Severity.ERROR }, "语句位 @ref 须落语法诊断：${r.diag.map { it.message }}")
    }

    @Test
    fun `t95_neg2_形参名前ref落诊断`() {
        // @ref 是类型位注解：形参名前是值注解位（mut 的域），混用落语法诊断
        val r = parseScriptSourceWithNotes("fun f(@ref a: T): Null", "t.subl")
        assertTrue(
            r.diag.any { it.severity == Severity.ERROR && it.message.contains("@ref") },
            "形参注解位 @ref 须落带 @ref 提示的诊断：${r.diag.map { it.message }}"
        )
    }
}
