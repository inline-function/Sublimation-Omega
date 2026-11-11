package sublimation.frontend.parser

import sublimation.frontend.ast.*
import sublimation.frontend.diag.Diag
import sublimation.frontend.diag.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 语法分析器回归移植（旧工程 ParserTest）+ t4 新契约证据（S-1 §2 容错 / D-9 留痕 / 脱糖）。
 *
 * 移植口径（S-3 §2.3 行为等价，契约差异按新契约改写）：
 * - 旧以 ParseFailure 表达的**文件级**语法错（= {- 混写 / 顶层禁语句 / ifis 组合条件 /
 *   大括号 struct 体 / 语法错误带位置 / 空安全糖移除）——新契约文件入口容错不停机，
 *   改断言 parseSourceWithNotes 的诊断袋（E-SYNTAX / E-LEX），异常断言只留在片段入口。
 * - 旧 assertSame(ReturnSym, …) —— ReturnSym 由 data object 改为带位置的 data class（t3），
 *   改断言类型。
 * - 旧 TheoryParam / TypeParam 等值比较 —— 节点带位置后改取名字列表比较。
 * - 「整个示例文件可解析」—— 新工程无 examples 目录，示例内联为常量（同内容、同断言力度）。
 */
class ParserTest {
    /** 决策 74（顶层禁语句）：语句级解析测试统一包进函数体再取回其语句列表。 */
    private fun stmtsOf(body: String): List<Stmt> {
        val f = parseSource("fun __t() { $body }", "t")
        return (((f.entries[0] as DeclEntry).decl as FunDecl).body as BlockExpr).stmts
    }

    /** 文件级解析的诊断袋（容错入口，S-1 §2）。 */
    private fun diagOf(src: String): List<Diag> = parseSourceWithNotes(src, "t").diag

    @Test
    fun `上下文弥散声明`() {
        val f = parseSource(
            "fun test1(): None <p> { axiom p }\nfun test2()<p>: None {}",
            "t",
        )
        val d1 = (f.entries[0] as DeclEntry).decl as FunDecl
        assertEquals("test1", d1.name)
        assertEquals(listOf("p"), d1.posts.map { (it as NameRef).name })
        assertTrue((d1.body as BlockExpr).stmts[0] is AxiomStmt)
        val d2 = (f.entries[1] as DeclEntry).decl as FunDecl
        assertEquals(listOf("p"), d2.preps.map { (it as NameRef).name })
        assertEquals("None", (d2.retType as? NamedType)?.name)
    }

    @Test
    fun `上文中的带空格比较`() {
        val f = parseSource("fun cmp(a: Int, b: Int)<a < b>: Bool = True()", "t")
        val d = (f.entries[0] as DeclEntry).decl as FunDecl
        val pre = d.preps[0] as BinExpr
        assertEquals("<", pre.op)
    }

    @Test
    fun `冒号分界上下文 - 后置可省略返回类型 前置可省略括号`() {
        // 用户拍板（v1.1）：`:` 前是上文（前置）、后是下文（后置）。
        // `: <p>` 无返回类型的后置上下文；`<p>` 前置上下文可省略 () 与返回类型。
        val f = parseSource(
            "fun giveP: <p> { axiom p }\nfun takeP<p> {}",
            "t",
        )
        val d1 = (f.entries[0] as DeclEntry).decl as FunDecl
        assertEquals(listOf("p"), d1.posts.map { (it as NameRef).name })
        assertNull(d1.retType, "后置上下文可省略返回类型")
        assertEquals(0, d1.preps.size)
        val d2 = (f.entries[1] as DeclEntry).decl as FunDecl
        assertEquals(listOf("p"), d2.preps.map { (it as NameRef).name })
        assertNull(d2.retType, "前置上下文可省略 () 与返回类型")
    }

    @Test
    fun `嵌套尖括号显式供给`() {
        val call = (stmtsOf("witness<valid<7>>()")[0] as ExprStmt).expr as CallExpr
        val inst = call.callee as InstExpr
        assertEquals("witness", (inst.target as NameRef).name)
        val inner = inst.terms[0] as InstExpr
        assertEquals("valid", (inner.target as NameRef).name)
    }

    @Test
    fun `返回值符号与等式`() {
        val f = parseSource("fun get7(): Int <$ = 7> { return 7 }", "t")
        val d = (f.entries[0] as DeclEntry).decl as FunDecl
        val post = d.posts[0] as BinExpr
        assertEquals("=", post.op)
        assertIs<ReturnSym>(post.left)   // t3：ReturnSym 带位置，改断言类型
    }

    @Test
    fun `全称量词与紧贴实例化`() {
        val ax = stmtsOf("axiom forall n. valid<n>")[0] as AxiomStmt
        val q = ax.props[0] as QuantExpr
        assertEquals("∀", q.q)
        val body = q.body as InstExpr
        assertEquals("valid", (body.target as NameRef).name)
    }

    @Test
    fun `命题参数理论`() {
        val f = parseSource("fun witness[@q]<q>: None {}", "t")
        val d = (f.entries[0] as DeclEntry).decl as FunDecl
        assertEquals(listOf("q"), d.theory.filterIsInstance<PropParam>().map { it.name })
    }

    @Test
    fun `类型参数与 class 成员`() {
        val f = parseSource("trait Show[T] { fun show(v: T): Str }", "t")
        val c = (f.entries[0] as DeclEntry).decl as TraitDecl
        assertEquals(listOf("T"), c.theory.filterIsInstance<TypeParam>().map { it.name })
        assertEquals("show", c.members[0].name)
    }

    @Test
    fun `enum 已废除报错并继续保持容错 B1`() {
        // enum 直接报 E-ENUM-REMOVED + 迁移模板，ErrorDecl 占位，花括号体整段跳过
        val r = parseSourceWithNotes(
            "struct A()\nstruct B()\nenum E { A, B }\nfun k(): Nat = 1",
            "t",
        )
        assertTrue(r.diag.any { it.code == "E-ENUM-REMOVED" && it.message.contains("枚举请用结构体") },
            r.diag.joinToString { it.message })
        // 后续条目不受影响
        val names = r.file.entries.mapNotNull { (it as? DeclEntry)?.decl?.let { d -> (d as? FunDecl)?.name } }
        assertEquals(listOf("k"), names)
    }

    @Test
    fun `struct 与 impl 废除报错并继续保持容错`() {
        // TS-30/Q6（t28）：impl 直接报 E-IMPL-REMOVED + 迁移模板，ErrorDecl 占位，后续条目不受影响
        val r = parseSourceWithNotes(
            "struct Point(x: Nat, y: Nat)\nimpl Show for Point { fun show(v: Point): Str = \"pt\" }\nfun k(): Nat = 1",
            "t",
        )
        val f = r.file
        assertTrue((f.entries[0] as DeclEntry).decl is StructDecl)
        val d = (f.entries[1] as DeclEntry).decl
        assertTrue(d is ErrorDecl, "impl 产错误声明节点")
        assertTrue(r.diag.any { it.code == "E-IMPL-REMOVED" },
            "应落 E-IMPL-REMOVED：${r.diag.joinToString { it.message }}")
        assertTrue(r.diag.any { it.message.contains("fun T.f") }, "迁移提示给扩展函数模板")
        assertTrue((f.entries[2] as DeclEntry).decl is FunDecl, "impl 体整段跳过，后续声明可解析")
    }

    @Test
    fun `符号中缀两参解糖`() {
        val ax = stmtsOf("axiom q ○ p")[0] as AxiomStmt
        assertEquals("○", (ax.props[0] as BinExpr).op)
    }

    @Test
    fun `无优先级左结合`() {
        val mul = (stmtsOf("var t = 1 + 1 * 1")[0] as VarStmt).value as BinExpr
        assertEquals("*", mul.op)
        assertEquals("+", (mul.left as BinExpr).op)
    }

    @Test
    fun `符号函数声明`() {
        val f = parseSource("fun +(a: Int, b: Int): Int = a", "t")
        assertEquals("+", ((f.entries[0] as DeclEntry).decl as FunDecl).name)
    }

    @Test
    fun `lambda 大括号两形态`() {
        val st = stmtsOf("var f1 = { x => x }\nvar f2 = { y => y }")
        assertTrue((st[0] as VarStmt).value is LambdaExpr)
        assertTrue((st[1] as VarStmt).value is LambdaExpr)
    }

    @Test
    fun `尾随 lambda`() {
        val call = (stmtsOf("each(1) { v => v }")[0] as ExprStmt).expr as CallExpr
        assertTrue(call.args.last() is LambdaExpr)
    }

    @Test
    fun `if 表达式`() {
        assertTrue((stmtsOf("var z = if (True()) { 1 } else { 2 }")[0] as VarStmt).value is IfExpr)
    }

    @Test
    fun `when 字面量分支与 else 兜底`() {
        val w = (stmtsOf("var odd = when(n) { 1 -> True() else -> False() }")[0] as VarStmt).value as WhenExpr
        assertEquals(2, w.arms.size)
        assertTrue(w.arms[0].pattern is PatLit)
        assertTrue(w.arms[1].pattern is PatElse)
    }

    @Test
    fun `when 解构与守卫`() {
        val w = (stmtsOf("when(o) { is Some(x) -> { 1 } is Nat if o < 1 -> 2 _ -> 0 }")[0] as ExprStmt).expr as WhenExpr
        val p0 = w.arms[0].pattern as PatCtor
        assertEquals("Some", p0.name)
        assertEquals(listOf("x"), p0.args.map { (it as PatCtor).name })   // 裸名由语义层裁定为绑定
        assertTrue(w.arms[0].body is BlockExpr)
        assertEquals("Nat", ((w.arms[1].pattern as PatIs).type as NamedType).name)
        assertTrue(w.arms[1].guard is BinExpr)
        assertTrue(w.arms[2].pattern is PatBind)
    }

    @Test
    fun `when 绑定主题与 is as`() {
        val w = (stmtsOf("when(var e = 0) { is Nat as m -> m }")[0] as ExprStmt).expr as WhenExpr
        assertEquals("e", w.subjectBinding)
        assertEquals("m", (w.arms[0].pattern as PatIs).bind)
    }

    @Test
    fun `ifis 智能解构脱糖为 when`() {
        // `if (o is Some(e)) A else B` → `when (o) { is Some(e) -> A; else -> B }`（S-10；t42 后分支箭头为 `->`）
        val w = (stmtsOf("if (o is Some(e)) { print(e) } else { print(0) }")[0] as ExprStmt).expr as WhenExpr
        assertEquals("o", (w.subject as NameRef).name)
        assertNull(w.subjectBinding)
        val a0 = w.arms[0].pattern as PatCtor
        assertEquals("Some", a0.name)
        assertEquals(listOf("e"), a0.args.map { (it as PatCtor).name })   // 裸名由语义层裁定为绑定
        assertTrue(w.arms[1].pattern is PatElse)
    }

    @Test
    fun `ifis 无 else 只留模式分支`() {
        val w = (stmtsOf("if (o is Some(e)) { print(e) }")[0] as ExprStmt).expr as WhenExpr
        assertEquals(1, w.arms.size)
        assertTrue(w.arms[0].pattern is PatCtor)
    }

    @Test
    fun `ifis 嵌套与枚举路径形态`() {
        val w = (stmtsOf("if (o is Some(is Some(v))) { print(v) }")[0] as ExprStmt).expr as WhenExpr
        val inner = (w.arms[0].pattern as PatCtor).args[0] as PatCtor
        assertEquals("Some", inner.name)
        val pathed = (stmtsOf("if (o is E::A(x)) { print(x) }")[0] as ExprStmt).expr as WhenExpr
        assertEquals("E::A", (pathed.arms[0].pattern as PatCtor).name)
    }

    @Test
    fun `ifis 不影响普通 if 与 is 类型测`() {
        // 布尔条件的 if 仍是 IfExpr；`o is Rat`（无括号解构）仍是 TypeTestExpr 智能转换
        val if1 = (stmtsOf("if (x > 0) { print(1) } else { print(2) }")[0] as ExprStmt).expr as IfExpr
        assertTrue(if1.cond is BinExpr)
        val if2 = (stmtsOf("if (o is Rat) { print(o) }")[0] as ExprStmt).expr as IfExpr
        assertTrue(if2.cond is TypeTestExpr)
    }

    @Test
    fun `ifis 组合条件报解析错`() {
        // 模式绑定无法进入组合条件作用域——挡在解析期并给出改写指引（新契约：诊断 + ErrorExpr 半成品）
        // t83（ADR-51/Q21 强制改向）：BEFORE 组合条件用 `&&`;AFTER 表达式层一律 `&`
        // （`&&` 已被 E-OP-RETIRED 拒绝，沿用会把 ifis 诊断顶成退役提示）。测试本意是 ifis
        // 模式绑定无法进入组合条件作用域——与布尔算符写法无关，故改 `&` 保原断言。
        val r = parseSourceWithNotes("fun f(o: Optional[Nat]): Nat = if (o is Some(e) & e > 0) e else 0", "t.subl")
        assertTrue(r.diag.any { it.message.contains("ifis") }, "实际: ${r.diag.joinToString { it.message }}")
        val d = (r.file.entries[0] as DeclEntry).decl as FunDecl
        assertIs<IfExpr>(d.body)          // 半成品：错误处仍给出可用的局部子树
        assertIs<ErrorExpr>((d.body as IfExpr).cond)
    }

    @Test
    fun `by 两种形态解析（t30 后语句式由 Checker 报 E-BY-REMOVED，解析保留）`() {
        val st = stmtsOf("by [p]\nvar q = 1 by [r]")
        assertTrue(st[0] is ByStmt)
        val v = (st[1] as VarStmt).value
        assertTrue(v is ByExpr && v.props.size == 1)
    }

    @Test
    fun `type 别名声明解析`() {
        val f = parseSource("type Pair[T] = Optional[T]", "t")
        val a = (f.entries[0] as DeclEntry).decl as TypeAliasDecl
        assertEquals("Pair", a.name)
        assertEquals(listOf("T"), a.theory.filterIsInstance<TypeParam>().map { it.name })
        assertEquals("Optional", (a.target as NamedType).name)
        assertEquals("T", (a.target.args.single() as NamedType).name)
    }

    @Test
    fun `T3 括号表达式不是元组`() {
        assertTrue((stmtsOf("var x = (1)")[0] as VarStmt).value is IntLit)
    }

    @Test
    fun `T3 元组字面量与元组模式解析`() {
        val f = parseSource("fun g(p: (Nat,Nat)): Nat = when(p) { (a, b) -> a }\nfun main() { var t = (1, 2) }", "t")
        val g = (f.entries[0] as DeclEntry).decl as FunDecl
        assertTrue((g.body as WhenExpr).arms[0].pattern is PatTuple)              // 单表达式体
        assertTrue((stmtsOf("var t = (1, 2)")[0] as VarStmt).value is TupleExpr)  // 元组字面量
    }

    @Test
    fun `体形态互斥 - 等号接花括号报错`() {
        // 决策 73：`= {…}` 混写解析层拒绝（新契约：诊断 + ErrorExpr 占位，不再抛异常）
        val r = parseSourceWithNotes("fun f(): Nat = { 1 }", "t")
        assertTrue(r.diag.any { it.message.contains("不能 `= {") }, "实际: ${r.diag.joinToString { it.message }}")
        val d = (r.file.entries[0] as DeclEntry).decl as FunDecl
        assertIs<ErrorExpr>(d.body)
    }

    @Test
    fun `顶层禁语句 - 执行代码须进 main`() {
        // 决策 74：顶层只允许声明（新契约：诊断 + ErrorStmt 占位，不再抛异常）
        val r = parseSourceWithNotes("foo()", "t")
        assertTrue(r.diag.any { it.message.contains("顶层只能写声明") }, "实际: ${r.diag.joinToString { it.message }}")
        assertIs<ErrorStmt>((r.file.entries[0] as StmtEntry).stmt)
    }

    @Test
    fun `块体无等号合法`() {
        val f = parseSource("fun f(): Nat { return 1 }", "t")
        val d = (f.entries[0] as DeclEntry).decl as FunDecl
        assertTrue(d.body is BlockExpr)
    }

    @Test
    fun `O1 impl 已废除——成员块体整段跳过`() {
        // TS-30（t28）：impl 报 E-IMPL-REMOVED；本测保留双层花括号归属的容错压力（skipBraced 配平）
        val r = parseSourceWithNotes("trait B { fun m(): Null }\nimpl B for Int { fun m(): Null { return null } }\nfun k(): Nat = 1", "t")
        val cls = (r.file.entries[0] as DeclEntry).decl as TraitDecl
        assertNull(cls.members[0].body)                       // 无体签名不受影响
        assertTrue(r.file.entries[1] is DeclEntry && (r.file.entries[1] as DeclEntry).decl is ErrorDecl)
        assertTrue(r.diag.any { it.code == "E-IMPL-REMOVED" })
        assertTrue((r.file.entries[2] as DeclEntry).decl is FunDecl, "跨嵌套花括号后继续解析")
    }

    @Test
    fun `O1 匿名函数块体`() {
        val v = (stmtsOf("var g = fun _(x: Nat): Nat { return x }")[0] as VarStmt).value as AnonFunExpr
        assertTrue(v.body is BlockExpr)
    }

    @Test
    fun `O3 大括号struct体报语法错并给指引`() {
        // 决策 69：struct 体是圆括号参数列表，大括号明确拒绝（带指向性文案；新契约：诊断 + ErrorDecl）
        val r = parseSourceWithNotes("struct A {\n  name: Str = \"u\"\n}", "t")
        assertTrue(r.diag.any { it.message.contains("struct 体是参数列表") }, "实际: ${r.diag.joinToString { it.message }}")
        assertIs<ErrorDecl>((r.file.entries[0] as DeclEntry).decl)
    }

    @Test
    fun `命名字段构造解析出 StructCtorExpr`() {
        // 决策 69：`A(name = "x")`（大写 + `IDENT =` 前瞻）→ 独立节点
        val v = (stmtsOf("var a = A(name = \"x\")")[0] as VarStmt).value
        assertTrue(v is StructCtorExpr && v.struct == "A" && v.assigns.single().first == "name")
    }

    @Test
    fun `return 语句与带值形态解析`() {
        val st = stmtsOf("if (n > 0) { return 1 }\nreturn")
        val ifStmt = (st[0] as ExprStmt).expr as IfExpr
        assertTrue((ifStmt.thenBlock.stmts[0] as ReturnStmt).expr != null)   // return 1
        assertTrue((st[1] as ReturnStmt).expr == null)                        // 裸 return
    }

    @Test
    fun `mut 注解与赋值语句`() {
        val st = stmtsOf("@mut var c = 0\nc = c + 1")
        assertEquals(listOf("mut"), (st[0] as VarStmt).annotations)
        assertTrue(st[1] is AssignStmt)
    }

    @Test
    fun `unchecked 纯度逃逸`() {
        val f = parseSource("fun log(m: Str): None { unchecked print(m) }", "t")
        val d = (f.entries[0] as DeclEntry).decl as FunDecl
        val inner = (d.body as BlockExpr).stmts[0] as UncheckedStmt
        assertTrue(inner.inner is ExprStmt)
    }

    @Test
    fun `语法错误带位置`() {
        // 新契约：诊断袋携带源位置（D-10）
        val e = diagOf("fun 1").first { it.severity == Severity.ERROR }
        assertEquals(1, e.pos.start.line)
    }

    @Test
    fun `整个示例文件可解析`() {
        // 旧工程读 examples/上下文弥散.subl；新工程无该目录，等价内联（内容与旧用例一致）
        val src = """
            // test1 的下文 <p> 由体内 axiom 兑现；调用后 p 进入作用域
            fun test1(): Null <p> {
                axiom p
            }

            // test2 的上文 <p> 由作用域中弥散的 p 自动供给
            fun test2()<p>: Null {}

            // $ 指代返回值：get7 唯一 return 7，后件 $ = 7 成立
            fun get7(): Int <$ = 7> {
                return 7
            }

            // 全称命题经合一自动实例化：需要 tagged<7>，由 forall n. tagged<n> 供给
            fun needTagged()<tagged<7>>: Null {}

            // 命题参数 @q 由调用点显式供给
            fun witness[@q]<q>: Null {}

            // 邻接规则：带空格的 < 是比较中缀（构成命题 a < b），紧贴的 < 是命题施加
            fun cmp(a: Int, b: Int) <a < b>: Bool {
                return true
            }

            fun main() {
                axiom forall n. tagged<n>
                test1()
                test2()
                unchecked print(get7())     // 7
                needTagged()
                witness<tagged<7>>()
            }
        """.trimIndent()
        val r = parseSourceWithNotes(src, "diffuse.subl")
        assertTrue(r.diag.isEmpty(), "实际: ${r.diag.joinToString { it.message }}")
        assertEquals(7, r.file.entries.size)
    }

    // ============ 空安全语法已移除：`T?` / `?.` / `?:` 均不再支持 ============

    @Test
    fun `T问号 后缀语法糖已移除`() {
        // 空安全糖移除：`Rat?` 不再是 Optional[Rat]——`?` 已从词法层删除，是未知字符
        // 新契约：lexer 错误并入解析诊断袋（层间总契约 2），不再抛 LexFailure
        val r = parseSourceWithNotes("fun f(x: Rat?): Null = null", "t")
        assertTrue(r.diag.any { it.message.contains("不认识") }, "实际: ${r.diag.joinToString { it.message }}")
    }

    @Test
    fun `elvis 空替代运算符已移除`() {
        val r = parseSourceWithNotes("fun f(): Null = a ?: b", "t")
        assertTrue(r.diag.any { it.message.contains("不认识") }, "实际: ${r.diag.joinToString { it.message }}")
    }

    @Test
    fun `safecall 安全调用运算符已移除`() {
        val r = parseSourceWithNotes("fun f(): Null = s?.length()", "t")
        assertTrue(r.diag.any { it.message.contains("不认识") }, "实际: ${r.diag.joinToString { it.message }}")
    }

    @Test
    fun `Optional 显式形态仍可用`() {
        // 移除的是空安全语法糖，Optional 本身仍是普通枚举容器：显式 `Optional[T]` 正常解析
        val f = parseSource("fun f(x: Optional[Rat]): Optional[Str] = null", "t")
        val d = (f.entries[0] as DeclEntry).decl as FunDecl
        assertEquals("Optional", (d.params[0].type as NamedType).name)
        assertEquals("Rat", ((d.params[0].type as NamedType).args.single() as NamedType).name)
        val r = d.retType!! as NamedType
        assertEquals("Optional", r.name)
        assertEquals("Str", (r.args.single() as NamedType).name)
    }

    @Test
    fun `typetest is 类型测解析`() {
        val v = (stmtsOf("var z = x is Rat")[0] as VarStmt).value as TypeTestExpr
        assertEquals("x", (v.target as NameRef).name)
        assertEquals("Rat", (v.type as NamedType).name)
    }

    // ============ t4 新契约证据（S-1 §2 容错 / D-9 留痕 / §2.13 脱糖 / §2.1 三入口） ============

    @Test
    fun `容错恢复_破损声明产出ErrorDecl并继续后续解析`() {
        val r = parseSourceWithNotes("fun 1\nfun g(): Nat = 2", "t")
        assertIs<ErrorDecl>((r.file.entries[0] as DeclEntry).decl)
        val g = (r.file.entries[1] as DeclEntry).decl as FunDecl
        assertEquals("g", g.name)
        assertTrue(r.diag.any { it.message.contains("期望函数名") })
    }

    @Test
    fun `容错恢复_块内语法错不阻断后续语句`() {
        val st = stmtsOf("var 1 = 2\nprint(3)")
        assertEquals(3, st.size)
        assertIs<ErrorStmt>(st[0])
        assertIs<ExprStmt>(st[2])
        assertTrue((st[2] as ExprStmt).expr is CallExpr)
    }

    @Test
    fun `工程模式顶层语句记录诊断并同步到下一声明`() {
        val r = parseSourceWithNotes("foo()\nfun g(): Nat = 2", "t")
        assertIs<ErrorStmt>((r.file.entries[0] as StmtEntry).stmt)
        assertIs<FunDecl>((r.file.entries[1] as DeclEntry).decl)
        assertTrue(r.diag.any { it.message.contains("顶层只能写声明") })
    }

    @Test
    fun `中缀糖展开留痕D_INFIX`() {
        // D-5：`q add p` → CallExpr(add, q, p)，留痕挂节点（D-9）
        val r = parseSourceWithNotes("fun f(): Null { axiom q add p }", "t")
        val notes = r.notes.values.flatten()
        assertTrue(notes.any { it.code == "D-INFIX" && it.message.contains("add") }, "实际: $notes")
    }

    @Test
    fun `尾随lambda留痕D_TRAILING`() {
        // D-9：each(1) { v => v } 的尾随 lambda 并入末位实参
        val r = parseSourceWithNotes("fun f(): Null { each(1) { v => v } }", "t")
        val notes = r.notes.values.flatten()
        assertTrue(notes.any { it.code == "D-TRAILING" }, "实际: $notes")
    }

    @Test
    fun `算术链同类提示H_ARITH_PREC`() {
        // t71（ADR-47）：同层跨类只产提示级诊断，不报错、节点仍是左结合（无优先级）
        val r = parseSourceWithNotes("fun f(): Nat { var t = 1 + 1 * 1 }", "t")
        val hint = r.diag.single { it.code == "H-ARITH-PREC" }
        assertEquals(Severity.HINT, hint.severity)
        assertTrue(r.diag.none { it.severity == Severity.ERROR })
        val mul = (stmtsOf("var t = 1 + 1 * 1")[0] as VarStmt).value as BinExpr
        assertEquals("*", mul.op)
    }

    @Test
    fun `无特质 impl 已废除——脱糖通道撤除`() {
        // TS-2/TS-30（t28）：`impl T { … }` 一律报 E-IMPL-REMOVED；旧 S-5 脱糖
        // （self 首参展开/占位提升）随 impl 通道撤除——接收者改由扩展函数 `fun T.f(…)` 表达
        val r = parseSourceWithNotes("impl List[a] { fun head(v: List[a]): a = v }\nfun k(): Nat = 1", "t")
        assertTrue(r.file.entries[0] is DeclEntry && (r.file.entries[0] as DeclEntry).decl is ErrorDecl)
        assertTrue(r.diag.any { it.code == "E-IMPL-REMOVED" })
        assertTrue((r.file.entries[1] as DeclEntry).decl is FunDecl)
    }

    @Test
    fun `脚本模式顶层允许语句`() {
        val f = parseScriptSource("print(1)\nvar x = 2\nfun g(): Nat = 3", "s")
        assertIs<StmtEntry>(f.entries[0])
        assertIs<StmtEntry>(f.entries[1])
        assertIs<DeclEntry>(f.entries[2])
    }

    @Test
    fun `命题if与when的方括号形态`() {
        // D-12：`if [p]`/`when [p]` 是命题位（语句），`if (c)` 是值位（表达式）
        val st = stmtsOf("if [p] { print(1) } else { print(2) }\nwhen [q] { q -> { print(3) } }")
        assertIs<IfPropStmt>(st[0])
        assertIs<WhenPropStmt>(st[1])
    }

    @Test
    fun `lambda 零参与解构参数形态`() {
        val st = stmtsOf("var z = { => 7 }")
        val lam = (st[0] as VarStmt).value as LambdaExpr
        assertTrue(lam.params.isEmpty() && !lam.implicitIt)          // 零参（箭头必写）
        val st2 = stmtsOf("var g = { (a, b) => a }")
        val lam2 = (st2[0] as VarStmt).value as LambdaExpr
        assertEquals(listOf("(a,b)"), lam2.params)                   // 解构参数编码名（S-36）
        val st3 = stmtsOf("var h = { a + 1 }")
        val lam3 = (st3[0] as VarStmt).value as LambdaExpr
        assertEquals(listOf("_"), lam3.params)                       // 隐式单参（_ 可引用）
        assertTrue(lam3.implicitIt)
    }

    @Test
    fun `字符串插值脱糖为concat链`() {
        // D-INTP："a${n}b" → concat(concat("a", toStr(n)), "b")（不引入新 AST 节点）
        val v = (stmtsOf("var s = \"a\${n}b\"")[0] as VarStmt).value
        val cat = v as CallExpr
        assertEquals("concat", (cat.callee as NameRef).name)
        assertIs<StrLit>(cat.args[1])
        val inner = cat.args[0] as CallExpr
        assertEquals("concat", (inner.callee as NameRef).name)
        assertIs<StrLit>(inner.args[0])
        val toStr = inner.args[1] as CallExpr
        assertEquals("toStr", (toStr.callee as NameRef).name)
        assertEquals("n", (toStr.args[0] as NameRef).name)
    }

    @Test
    fun `def 已废除报错 B2`() {
        val r = parseSourceWithNotes("def eq ↔ a = b\nfun k(): Nat = 1", "t")
        assertTrue(r.diag.any { it.code == "E-DEF-REMOVED" && it.message.contains("命题无需预先定义") },
            r.diag.joinToString { it.message })
    }

    @Test
    fun `类型位注解与和类型`() {
        // S-15：@mut T 视图类型；A | B 和类型（构造即展平）
        val f = parseSource("fun f(x: @mut Nat, y: A | B): Null = null", "t")
        val d = (f.entries[0] as DeclEntry).decl as FunDecl
        val t0 = d.params[0].type
        assertIs<MutType>(t0)
        assertIs<NamedType>(t0.inner)
        val u = d.params[1].type as UnionType
        assertEquals(listOf("A", "B"), u.variants.map { (it as NamedType).name })
    }

    // ============ t15 回归：上下文组（返回类型后的 <…>）与量词形态（ADR-16） ============

    @Test
    fun `上下文组量词_量词开头的后置上下文组`() {
        // t15：上下文组内量词直接开头——量词体须继承 angle 语境，否则闭合 > 被当比较中缀
        // 吞掉并级联吞噬下一声明的 @theorem（旧 24 处 E-SYNTAX 族，见 ADR-16）
        val r = parseSourceWithNotes(
            "@theorem\nfun `set pairing`[a, b]: Null <∃c . ∀x . ((x ∈ c) ↔ ((x = a) ∨ (x = b)))>",
            "t.subl",
        )
        assertTrue(r.diag.isEmpty(), "实际: ${r.diag.joinToString { it.render() }}")
        val d = (r.file.entries[0] as DeclEntry).decl as FunDecl
        val q = d.posts[0] as QuantExpr
        assertEquals("∃", q.q)
        assertEquals(listOf("c"), q.names)
        val inner = q.body as QuantExpr
        assertEquals("∀", inner.q)
        assertEquals(listOf("x"), inner.names)
    }

    @Test
    fun `上下文组量词_全称量词组`() {
        // 探针 g：<∀x . (x ∈ c)> 整组无诊断
        val r = parseSourceWithNotes("fun p(): Null <∀x . (x ∈ c)>", "t.subl")
        assertTrue(r.diag.isEmpty(), "实际: ${r.diag.joinToString { it.render() }}")
        val q = ((r.file.entries[0] as DeclEntry).decl as FunDecl).posts[0] as QuantExpr
        assertEquals("∀", q.q)
        assertEquals(listOf("x"), q.names)
        assertEquals("∈", (q.body as BinExpr).op)
    }

    @Test
    fun `上下文组量词_存在唯一形态`() {
        val r = parseSourceWithNotes("fun p(): Null <∃!c . (x ∈ c)>", "t.subl")
        assertTrue(r.diag.isEmpty(), "实际: ${r.diag.joinToString { it.render() }}")
        val q = ((r.file.entries[0] as DeclEntry).decl as FunDecl).posts[0] as QuantExpr
        assertEquals("∃!", q.q)
    }

    @Test
    fun `上下文组量词_括号前缀与尾随蕴含`() {
        // 探针 f：组以 ( 开头（量词在括号内、括号内重置 angle）——闭合 > 由组顶层 angle 语境消费
        val r = parseSourceWithNotes(
            "fun `set empty unique`[e1, e2]: Null <((∀x . ¬(x ∈ e1)) & (∀x . ¬(x ∈ e2))) → (e1 = e2)>",
            "t.subl",
        )
        assertTrue(r.diag.isEmpty(), "实际: ${r.diag.joinToString { it.render() }}")
        val d = (r.file.entries[0] as DeclEntry).decl as FunDecl
        // P5 批次 2：→ 名字化后 `A → B` 落 D-5 中缀糖 CallExpr("→", [A, B])
        val imp = d.posts[0] as CallExpr
        assertEquals("→", (imp.callee as NameRef).name)
        assertEquals("&", (imp.args[0] as BinExpr).op)
        val conj = (imp.args[0] as BinExpr).left as QuantExpr            // 括号解包：(... ) 体内即量词本身
        assertEquals("∀", conj.q)
        // P4（批次 3）：¬ 是名字材料——¬(x ∈ e1) 走调用形态（CallExpr，callee ¬）
        val notBody = conj.body as CallExpr
        assertEquals("¬", (notBody.callee as NameRef).name)
    }

    @Test
    fun `上下文组量词_组内实例化与双重尖括号`() {
        // 量词体内紧贴 < 实例化（D-1）与组闭合 >（D-2）的邻接分层：<∀x . valid<x>>
        val r = parseSourceWithNotes("fun p(): Null <∀x . valid<x>>", "t.subl")
        assertTrue(r.diag.isEmpty(), "实际: ${r.diag.joinToString { it.render() }}")
        val q = ((r.file.entries[0] as DeclEntry).decl as FunDecl).posts[0] as QuantExpr
        val inst = q.body as InstExpr
        assertEquals("valid", (inst.target as NameRef).name)
        assertEquals("x", (inst.terms[0] as NameRef).name)
    }

    @Test
    fun `无 is 的空参构造子模式合法`() {
        // C2（ADR-19 §C2）：S-42 名字模式——无 is 的括号模式仅允许空参 A() / E::A()（文法 §2.12 注）
        val w = (stmtsOf("when(o) { Excellent() -> 1 }")[0] as ExprStmt).expr as WhenExpr
        val p = w.arms[0].pattern as PatCtor
        assertEquals("Excellent", p.name)
        assertTrue(p.args.isEmpty(), "A() 空参形态无实参模式")
        val pathed = (stmtsOf("when(o) { E::A() -> 1 }")[0] as ExprStmt).expr as WhenExpr
        assertEquals("E::A", (pathed.arms[0].pattern as PatCtor).name)
        // is 模式实参位内的空参形态同通道（S-42 槽位）
        val nested = (stmtsOf("when(o) { is Cons(h, Nil()) -> 1 }")[0] as ExprStmt).expr as WhenExpr
        val inner = (nested.arms[0].pattern as PatCtor).args[1] as PatCtor
        assertEquals("Nil", inner.name)
        assertTrue(inner.args.isEmpty())
    }

    @Test
    fun `无 is 的非空括号模式仍拒绝`() {
        // S-42 注：非空括号模式必须显式 is（样本越界不改代码——判据见 ADR-19 §C2）
        val r = parseSourceWithNotes("fun f(o: Nat): Nat = when(o) { Cons(h, t) -> 1 }", "c2.subl")
        assertTrue(r.diag.any { it.code == "E-SYNTAX" && it.message.contains("类型解构须显式 is") },
            "实际: ${r.diag.joinToString { it.render() }}")
        val r2 = parseSourceWithNotes("fun f(o: Nat): Nat = when(o) { E::A(x) -> 1 }", "c2.subl")
        assertTrue(r2.diag.any { it.code == "E-SYNTAX" && it.message.contains("类型解构须显式 is") },
            "实际: ${r2.diag.joinToString { it.render() }}")
    }

    // ---- B7（t23，ADR-21 §B7）：运算符前缀调用的实参收集（恢复点修复） ----

    @Test
    fun `运算符前缀调用按位置实参收集`() {
        // 决策 88：+(4, 5) 当作普通函数名调用——实参是 4 与 5 两个位置实参，不是 (4, 5) 元组。
        // 旧 Parser.kt:1415-1419 口径：先 next() 消费算符，parseArgs 消费开括号。
        val r = parseSourceWithNotes("fun main() { print(+(4, 5)) }", "b7.subl")
        assertTrue(r.diag.isEmpty(), "实际: ${r.diag.joinToString { it.render() }}")
        val call = (stmtsOf("print(+(4, 5))")[0] as ExprStmt).expr as CallExpr
        val inner = call.args.first() as CallExpr
        assertEquals("+", (inner.callee as NameRef).name)
        assertEquals(2, inner.args.size, "+(4, 5) 收两个位置实参，不被元组吞并")
    }

    @Test
    fun `运算符前缀调用嵌套于实参位不吞闭括号`() {
        // 修复前：(+(4,5)) 的符号线 parseArgs 之后多 next() 一次，吞掉 print 的闭括号，
        // 落「期望)，实际'print'」并级联 E-UNBOUND-NAME（函数与调用形式.subl:46-47 的恢复点缺陷）
        val r = parseSourceWithNotes("fun main() { unchecked print(*(6, 7)) }", "b7.subl")
        assertTrue(r.diag.isEmpty(), "实际: ${r.diag.joinToString { it.render() }}")
        val outer = (stmtsOf("unchecked print(*(6, 7))")[0] as UncheckedStmt).inner as ExprStmt
        val inner = (outer.expr as CallExpr).args.first() as CallExpr
        assertEquals("*", (inner.callee as NameRef).name)
        assertEquals(listOf(6, 7), inner.args.map { (it as IntLit).value.toInt() })
    }

    // ============ t28（P-8/B）：扩展函数产生式 / class 体收紧 / 邻接判据 ============

    @Test
    fun `TS-9 扩展函数——简单接收者点号链`() {
        val f = parseSource("fun Nat.show(): Str = \"s\"", "t")
        val d = (f.entries[0] as DeclEntry).decl as FunDecl
        assertEquals("show", d.name)
        val rcv = d.receiver as? NamedType
        assertNotNull(rcv, "receiver 为 NamedType")
        assertEquals("Nat", rcv.name)
    }

    @Test
    fun `TS-9 扩展函数——接收者带类型实参与嵌套`() {
        val f = parseSource("fun List[a].map(f: a): List[a] = Nil", "t")
        val d = (f.entries[0] as DeclEntry).decl as FunDecl
        assertEquals("map", d.name)
        val rcv = d.receiver as NamedType
        assertEquals("List", rcv.name)
        assertEquals(1, rcv.args.size)
        assertEquals("a", (rcv.args[0] as NamedType).name)
        // 嵌套形态：List[Optional[a]] 的接收者原样保留
        val f2 = parseSource("fun List[Optional[a]].head(): a = x", "t")
        val d2 = (f2.entries[0] as DeclEntry).decl as FunDecl
        val inner = (d2.receiver as NamedType).args[0] as NamedType
        assertEquals("Optional", inner.name)
        assertEquals("a", (inner.args[0] as NamedType).name)
    }

    @Test
    fun `TS-9 扩展函数——限定接收者链`() {
        val f = parseSource("fun core.Point.distTo(p: core.Point): Nat = 0", "t")
        val d = (f.entries[0] as DeclEntry).decl as FunDecl
        assertEquals("distTo", d.name, "末段为函数名")
        val rcv = d.receiver as QualifiedType
        assertEquals(listOf("core"), rcv.module)
        assertEquals("Point", rcv.name)
    }

    @Test
    fun `TS-9 邻接判据——无点号即自由函数（D-1）`() {
        // 函数名本身可以叫 T / List / 任何标识——不该被误判为接收者
        val f = parseSource("fun T(a: Nat): Nat = a\nfun List: Nat = 1\nfun f: Nat = 2", "t")
        val ds = f.entries.map { (it as DeclEntry).decl as FunDecl }
        ds.forEach { assertNull(it.receiver, "${it.name} 不带接收者") }
        assertEquals(listOf("T", "List", "f"), ds.map { it.name })
    }

    @Test
    fun `TS-9 邻接判据——fun T-f 点号与同名自由函数的确定性区分 D-2`() {
        val f = parseSource("fun T.f(a: Nat): Nat = a\nfun T: Nat = 1", "t")
        val ext = (f.entries[0] as DeclEntry).decl as FunDecl
        val plain = (f.entries[1] as DeclEntry).decl as FunDecl
        assertEquals("f", ext.name)
        assertEquals("T", (ext.receiver as NamedType).name)
        assertEquals("T", plain.name)
        assertNull(plain.receiver)
    }

    @Test
    fun `TS-9 扩展函数理论参数归属末段`() {
        // 末段方括号是函数的理论参数；接收者段的方括号是类型实参（D-3）
        val f = parseSource("fun List[a].flatten[b](x: b): b = x", "t")
        val d = (f.entries[0] as DeclEntry).decl as FunDecl
        assertEquals("flatten", d.name)
        assertEquals(listOf("b"), d.theory.filterIsInstance<TypeParam>().map { it.name })
        assertEquals("List", (d.receiver as NamedType).name)
    }
    // ============ t37（S-43 修订）：括号化接收者 ============

    @Test
    fun `S-43③ 括号化接收者——元组与函数类型`() {
        val f = parseSourceWithNotes("fun (Nat, Nat).add(): Nat = 0", "t")
        val d = (f.file.entries[0] as DeclEntry).decl as FunDecl
        assertEquals("add", d.name)
        val tuple = d.receiver as TupleType
        assertEquals(2, tuple.items.size)
        assertEquals(listOf("Nat", "Nat"), tuple.items.map { (it as NamedType).name })
        val f2 = parseSourceWithNotes("fun ((Nat) => Nat).after(): Nat = 0", "t")
        val d2 = (f2.file.entries[0] as DeclEntry).decl as FunDecl
        assertEquals("after", d2.name)
        val ft = d2.receiver as FunType
        assertEquals(1, ft.params.size)
        assertEquals("Nat", (ft.params[0] as NamedType).name)
        assertEquals("Nat", (ft.ret as NamedType).name)
        assertTrue(f.diag.none { it.severity == Severity.ERROR }, f.diag.joinToString { it.message })
        assertTrue(f2.diag.none { it.severity == Severity.ERROR }, f2.diag.joinToString { it.message })
    }

    @Test
    fun `S-43③ 括号化接收者——单项括号即分组与类型实参`() {
        // (Nat) ≡ Nat（S-15 单项括号即分组）——与 fun Nat.f() 等价形态
        val f = parseSourceWithNotes("fun (Nat).f(): Nat = 0\nfun (List[Nat]).g(): Nat = 0", "t")
        val ds = f.file.entries.map { (it as DeclEntry).decl as FunDecl }
        assertEquals("Nat", (ds[0].receiver as NamedType).name)
        val rcv = ds[1].receiver as NamedType
        assertEquals("List", rcv.name)
        assertEquals("Nat", (rcv.args[0] as NamedType).name)
        assertTrue(f.diag.none { it.severity == Severity.ERROR }, f.diag.joinToString { it.message })
    }

    @Test
    fun `S-43③ 括号化接收者——联合与嵌套函数类型`() {
        val f = parseSourceWithNotes("fun (A | B).f(): Nat = 0", "t")
        val d = (f.file.entries[0] as DeclEntry).decl as FunDecl
        val union = d.receiver as UnionType
        assertEquals(listOf("A", "B"), union.variants.map { (it as NamedType).name })
        val f2 = parseSourceWithNotes("fun ((Nat, Nat) => Nat).apply(): Nat = 0", "t")
        val d2 = (f2.file.entries[0] as DeclEntry).decl as FunDecl
        val ft = d2.receiver as FunType
        // (Nat, Nat) => Nat：元组形作函数类型的参数表（S-15），故 params 为两项 Nat、Nat
        assertEquals(2, ft.params.size)
        assertEquals(listOf("Nat", "Nat"), ft.params.map { (it as NamedType).name })
        assertEquals("Nat", (ft.ret as NamedType).name)
        assertTrue(f.diag.none { it.severity == Severity.ERROR }, f.diag.joinToString { it.message })
        assertTrue(f2.diag.none { it.severity == Severity.ERROR }, f2.diag.joinToString { it.message })
    }

    @Test
    fun `S-43③ 反例——无外层括号的函数类型接收者须提示加括号`() {
        // fun (Nat) => Nat.f()：=> 会吞掉 . 与函数名——本就非法，提示整体再加一层括号
        val r = parseSourceWithNotes("fun (Nat) => Nat.f(): Nat = 0", "t")
        assertTrue(r.diag.any { it.code == "E-SYNTAX" && it.message.contains("期望 . 与函数名")
            && it.message.contains("再加一层括号") }, r.diag.joinToString { it.message })
    }

    @Test
    fun `S-43③ 反例——括号化接收者缺点号与空括号`() {
        val r = parseSourceWithNotes("fun (Nat, Nat)\nfun k(): Nat = 1", "t")
        assertTrue(r.diag.any { it.code == "E-SYNTAX" && it.message.contains("期望 . 与函数名")
            && it.message.contains("(类型).名字") }, r.diag.joinToString { it.message })
        val r2 = parseSourceWithNotes("fun ().f(): Nat = 0", "t")
        assertTrue(r2.diag.any { it.code == "E-SYNTAX" && it.message.contains("括号化接收者不能为空") },
            r2.diag.joinToString { it.message })
    }

    // ============ t40（P-9）：val/enum/def 废除 + type 右值放宽 ============

    @Test
    fun `TS-6② 常量规范已废除——val 落 E-VAL-REMOVED B3`() {
        val r = parseSourceWithNotes("trait C { fun m(v: Nat): Nat; val k: Nat }", "t")
        assertTrue(r.diag.any { it.code == "E-VAL-REMOVED" && it.message.contains("trait 不得携带状态") },
            r.diag.joinToString { it.message })
        // 函数规范不受影响
        val c = (r.file.entries[0] as DeclEntry).decl as TraitDecl
        assertEquals(1, c.members.size)
        assertEquals("m", c.members[0].name)
    }

    @Test
    fun `S-13 B4 type 右值放宽——联合别名`() {
        // a) struct A / struct B + type E = A | B
        val r = parseSourceWithNotes("struct A()\nstruct B()\ntype E = A | B", "t")
        val d = (r.file.entries[2] as DeclEntry).decl as TypeAliasDecl
        assertIs<UnionType>(d.target)
        assertEquals(listOf("A", "B"), d.target.variants.map { (it as NamedType).name })
        assertTrue(r.diag.none { it.severity == Severity.ERROR }, r.diag.joinToString { it.message })
    }

    @Test
    fun `S-13 B4 type 右值放宽——归纳自引用`() {
        // b) type Nat = Zero | Succ[Nat]（自引用归纳形：语法层放行，判定归语义层 t41）
        val r = parseSourceWithNotes("struct Zero()\nstruct Succ[T](pred: T)\ntype Nat = Zero | Succ[Nat]", "t")
        val d = (r.file.entries[2] as DeclEntry).decl as TypeAliasDecl
        assertIs<UnionType>(d.target)
        assertEquals(2, d.target.variants.size)
        assertTrue(r.diag.none { it.severity == Severity.ERROR }, r.diag.joinToString { it.message })
    }

    @Test
    fun `S-13 B4 type 右值放宽——非归纳自引用`() {
        // c) type A = A（自引用非归纳形：语法层放行，E-CYCLE 归 t41）
        val r = parseSourceWithNotes("type A = A", "t")
        val d = (r.file.entries[0] as DeclEntry).decl as TypeAliasDecl
        assertIs<NamedType>(d.target)
        assertEquals("A", (d.target as NamedType).name)
        assertTrue(r.diag.none { it.severity == Severity.ERROR }, r.diag.joinToString { it.message })
    }

    @Test
    fun `TS-6 特质体禁字段与嵌套类型`() {
        val r = parseSourceWithNotes("trait C { var x: Nat }\nfun k(): Nat = 1", "t")
        assertTrue(r.diag.any { it.code == "E-SYNTAX" && it.message.contains("禁止字段") }, r.diag.joinToString { it.message })
        assertTrue((r.file.entries[1] as DeclEntry).decl is FunDecl)
        val r2 = parseSourceWithNotes("trait C { struct S(n: Nat) }", "t")
        assertTrue(r2.diag.any { it.message.contains("禁止嵌套类型") })
    }

    @Test
    fun `TS-6 函数规范不得含体与默认值`() {
        val r = parseSourceWithNotes("trait C { fun m(v: Nat): Nat = v }", "t")
        assertTrue(r.diag.any { it.message.contains("不得含实现体") }, r.diag.joinToString { it.message })
        val r2 = parseSourceWithNotes("trait C { fun m(v: Nat = 0): Nat }", "t")
        assertTrue(r2.diag.any { it.message.contains("默认值") })
    }

    @Test
    fun `TS-6② 常量规范形态已随 val 废除 B3`() {
        // 旧「常量规范不得含实现」判据的样本现在落 E-VAL-REMOVED（诊断已含废除指引）
        val r = parseSourceWithNotes("trait C { val k: Nat = 0 }", "t")
        assertTrue(r.diag.any { it.code == "E-VAL-REMOVED" }, r.diag.joinToString { it.message })
    }

    // ============ t42（P-9 后裁决 2026-11-06）：when 分支箭头统一为 `->` ============

    @Test
    fun `S37 t42 用户原文三例 is 位联合与函数类型零冲突`() {
        // 裁决原文样本：type U = A | (Nat)=>Nat | (Nat, Nat)；when 三臂同存，必须无诊断解析通过
        val r = parseSourceWithNotes(
            "type U = A | (Nat)=>Nat | (Nat, Nat)\n" +
                "struct A()\n" +
                "fun f(u: U): Nat = when(u) { is A -> 1; is (Nat)=>Nat -> 2; is (a, b) -> a + b }",
            "t",
        )
        assertTrue(r.diag.none { it.severity == Severity.ERROR }, r.diag.joinToString { it.message })
        val w = (r.file.entries[2] as DeclEntry).decl as FunDecl
        val arms = w.body as WhenExpr
        assertEquals(3, arms.arms.size)
        assertTrue(arms.arms[0].pattern is PatIs, "第一臂 is A")
        assertTrue(arms.arms[1].pattern is PatIs, "第二臂是 is (Nat)=>Nat 类型测试")
    }

    @Test
    fun `t42 分支体是 lambda 箭头分家不冲突`() {
        // 分支箭头 `->`；体内 lambda 仍用 `=>`（S-36 花括号显式形态）
        val r = parseSourceWithNotes("struct A()\nfun f(x: A): Nat = when(x) { is A -> { y => y + 1 } }", "t")
        assertTrue(r.diag.none { it.severity == Severity.ERROR }, r.diag.joinToString { it.message })
    }

    @Test
    fun `S21 t42 命题 when 分支箭头同样用单箭头`() {
        val st = stmtsOf("axiom r\nwhen[r] { r -> { ∵ r ∴ r } }")
        assertTrue(st.last() is WhenPropStmt, "when[命题] 解析为语句")
    }

    @Test
    fun `t42 嵌套 when 分支边界正确`() {
        val r = parseSourceWithNotes(
            "struct A()\n" +
                "fun f(x: A): Nat = when(x) { is A -> when(x) { is A -> 1; else -> 0 }; else -> 2 }",
            "t",
        )
        assertTrue(r.diag.none { it.severity == Severity.ERROR }, r.diag.joinToString { it.message })
    }

    @Test
    fun `t42 反例 when 分支位用双箭头报统一诊断`() {
        val r = parseSourceWithNotes("struct A()\nfun f(x: A): Nat = when(x) { is A => 1; else -> 0 }", "t")
        assertTrue(r.diag.any {
            it.code == "E-SYNTAX" && it.message.contains("when 分支箭头已统一为") && it.message.contains("例如 `is A -> 1`")
        }, r.diag.joinToString { it.message })
    }

    @Test
    fun `t42 反例 缺失分支箭头文案含单箭头`() {
        val r = parseSourceWithNotes("struct A()\nfun f(x: A): Nat = when(x) { is A 1; else -> 0 }", "t")
        assertTrue(r.diag.any { it.code == "E-SYNTAX" && it.message.contains("分支箭头 `->`") },
            r.diag.joinToString { it.message })
    }

    // ============ t53（P-9 收口 R3 口径差）：废除关键字在语句位的诊断质量 ============
    // 旧退化：函数体内/脚本顶层 val 被当标识符落 E-UNBOUND-NAME（无废除指引）；
    // 工程顶层落通用 E-SYNTAX；enum/def/impl 在函数体落「期望表达式」+ 跳符号级联。
    // 修复后四关键字在所有位置一律落专属废除码 + 迁移指引（行为仍拒绝，只改诊断质量）。

    @Test
    fun `t53 val 四位置落 E-VAL-REMOVED 且带 var 迁移指引`() {
        // 函数体内（旧：E-UNBOUND-NAME val/x）
        val inFun = parseSourceWithNotes("fun f() { val x = 1 }", "t")
        assertTrue(inFun.diag.any { it.code == "E-VAL-REMOVED" && it.message.contains("可变引用一律用 `var`") },
            inFun.diag.joinToString { it.message })
        // 脚本模式顶层（旧：E-UNBOUND-NAME）
        val script = parseScriptSourceWithNotes("val x = 1", "s")
        assertTrue(script.diag.any { it.code == "E-VAL-REMOVED" && it.message.contains("var 只允许在脚本模式顶层与函数体内") },
            script.diag.joinToString { it.message })
        // 工程模式顶层（旧：通用 E-SYNTAX 顶层只能写声明）
        val top = parseSourceWithNotes("val x = 1", "p")
        assertTrue(top.diag.any { it.code == "E-VAL-REMOVED" && it.message.contains("fun main()") },
            top.diag.joinToString { it.message })
        // 特质体（既有形态不回归）
        val cls = parseSourceWithNotes("trait C { fun m(v: Nat): Nat; val k: Nat }", "c")
        assertTrue(cls.diag.any { it.code == "E-VAL-REMOVED" && cls.diag.any { it.message.contains("trait 不得携带状态") } },
            cls.diag.joinToString { it.message })
        // 语句边界同步：val 残段越过，块内后续 var 声明照常解析，且只报一次废除诊断
        val after = parseSourceWithNotes("fun f() { val x = 1; var y = 2 }", "t")
        assertEquals(1, after.diag.count { it.code == "E-VAL-REMOVED" },
            after.diag.joinToString { it.message })
    }

    @Test
    fun `t53 enum 语句位落 E-ENUM-REMOVED 且容错链不断`() {
        // 函数体内（旧：E-SYNTAX 期望表达式 + 跳符号 + E-UNBOUND-NAME E）
        val inFun = parseSourceWithNotes("fun f() { enum E { A } }", "t")
        assertTrue(inFun.diag.any { it.code == "E-ENUM-REMOVED" && it.message.contains("枚举请用结构体") },
            inFun.diag.joinToString { it.message })
        // 花括号体整段吞掉、分号边界停、块正常闭合，后续语句可续解析
        val after = parseSourceWithNotes("fun f() { enum E { A }; var y = 2 }", "t")
        assertTrue(after.diag.any { it.code == "E-ENUM-REMOVED" }, after.diag.joinToString { it.message })
        assertEquals(1, after.diag.count { it.code == "E-ENUM-REMOVED" }, after.diag.joinToString { it.message })
    }

    @Test
    fun `t53 def 与 impl 语句位落专属废除诊断`() {
        val defInFun = parseSourceWithNotes("fun f() { def D }", "t")
        assertTrue(defInFun.diag.any { it.code == "E-DEF-REMOVED" && it.message.contains("axiom") },
            defInFun.diag.joinToString { it.message })
        val implInFun = parseSourceWithNotes("fun f() { impl A for B { } }", "t")
        assertTrue(implInFun.diag.any { it.code == "E-IMPL-REMOVED" && it.message.contains("改写为扩展函数") },
            implInFun.diag.joinToString { it.message })
        // 顶层既有形态不回归（同一文案真相）
        val top = parseSourceWithNotes("enum E { A, B }\nfun k(): Nat = 1", "p")
        assertTrue(top.diag.any { it.code == "E-ENUM-REMOVED" && it.message.contains("枚举请用结构体") },
            top.diag.joinToString { it.message })
    }

    // ============ t56（P-10 步骤 0.2-A）：class→trait 改名——旧路径已死 ============
    // Lexer/Kind 已不识别 class（落 IDENT），Parser 无 class 分派：`class Foo` 报错而非静默当 trait。

    @Test
    fun `t56 class 关键字已废除——class Foo 不被静默当 trait 解析`() {
        // 工程模式顶层：class 落通用顶层错误，AST 中不产任何 TraitDecl
        val r = parseSourceWithNotes("class Foo { }", "t")
        assertTrue(r.diag.any { it.severity == Severity.ERROR }, r.diag.joinToString { it.message })
        val names = r.file.entries.mapNotNull { (it as? DeclEntry)?.decl?.let { d -> (d as? TraitDecl)?.name } }
        assertEquals(emptyList<String>(), names, "class Foo 不得被静默解析为 trait")
    }

    @Test
    fun `t56 trait 关键字正常解析为 TraitDecl`() {
        val r = parseSourceWithNotes("trait Show[T] { fun show(): Str }", "t")
        assertTrue(r.diag.none { it.severity == Severity.ERROR }, r.diag.joinToString { it.message })
        val d = (r.file.entries[0] as DeclEntry).decl as TraitDecl
        assertEquals("Show", d.name)
        assertEquals(listOf("T"), d.theory.filterIsInstance<TypeParam>().map { it.name })
    }
}
