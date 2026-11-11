package sublimation.frontend.cli

import sublimation.frontend.ast.AstNode
import sublimation.frontend.ast.DeclEntry
import sublimation.frontend.ast.Entry
import sublimation.frontend.ast.FileAst
import sublimation.frontend.ast.Param
import sublimation.frontend.ast.StmtEntry
import sublimation.frontend.ast.TheoryParam
import sublimation.frontend.ast.WhenArm
import sublimation.frontend.ast.WhenPropArm
import sublimation.frontend.diag.SourceRange

/**
 * AST 树形打印（parse 子命令的产物；S-1 §2 零语义树的可读呈现）。
 *
 * 形态：缩进 2 空格；节点行 = 「Kind 主标量 位置」（位置取节点携带的区间，紧凑形态）；
 * 孩子带字段名前缀（如 body: BlockExpr），保序（S-1 §0 源序保留）。
 *
 * 完整性判据：node 的 when 穷尽 sealed AstNode——节点增删即编译错误，
 * 与 HirJson 发射器同口径（S-4 禁死代码 = 无 else 穷尽）。
 */
internal fun astTreeString(file: FileAst): String = buildString {
    append("FileAst(${file.entries.size} 条目)\n")
    file.entries.forEachIndexed { i, e -> entryLine(i, e, 1, this) }
}

private fun entryLine(i: Int, e: Entry, indent: Int, out: StringBuilder) {
    when (e) {
        is DeclEntry -> node(e.decl, indent, out, "entry[$i]: ")
        is StmtEntry -> node(e.stmt, indent, out, "entry[$i]: ")
    }
}

private fun node(n: AstNode, indent: Int, out: StringBuilder, prefix: String = "") {
    out.append("  ".repeat(indent)).append(prefix)
    when (n) {
        // ---- 声明（S-5~S-14）----
        is sublimation.frontend.ast.FunDecl -> {
            out.append("FunDecl ${n.name} ${fmt(n.pos)}\n")
            n.theory.forEachIndexed { i, t -> theoryKid("theory[$i]", t, indent + 1, out) }
            n.params.forEachIndexed { i, p -> paramKid("params[$i]", p, indent + 1, out) }
            n.preps.forEachIndexed { i, e -> kid("preps[$i]", e, indent + 1, out) }
            n.retType?.let { kid("retType", it, indent + 1, out) }
            n.posts.forEachIndexed { i, e -> kid("posts[$i]", e, indent + 1, out) }
            n.constraints.forEachIndexed { i, e -> kid("constraints[$i]", e, indent + 1, out) }
            n.body?.let { kid("body", it, indent + 1, out) }
        }
        is sublimation.frontend.ast.TraitDecl -> {
            out.append("TraitDecl ${n.name} ${fmt(n.pos)}\n")
            n.theory.forEachIndexed { i, t -> theoryKid("theory[$i]", t, indent + 1, out) }
            n.parents.forEachIndexed { i, e -> kid("parents[$i]", e, indent + 1, out) }
            n.members.forEachIndexed { i, e -> kid("members[$i]", e, indent + 1, out) }
        }
is sublimation.frontend.ast.StructDecl -> {
            out.append("StructDecl ${n.name} ${fmt(n.pos)}\n")
            n.theory.forEachIndexed { i, t -> theoryKid("theory[$i]", t, indent + 1, out) }
            n.fields.forEachIndexed { i, p -> paramKid("fields[$i]", p, indent + 1, out) }
            n.props.forEachIndexed { i, e -> kid("props[$i]", e, indent + 1, out) }
        }
is sublimation.frontend.ast.TypeAliasDecl -> {
            out.append("TypeAliasDecl ${n.name} ${fmt(n.pos)}\n")
            n.theory.forEachIndexed { i, t -> theoryKid("theory[$i]", t, indent + 1, out) }
            kid("target", n.target, indent + 1, out)
        }
is sublimation.frontend.ast.ErrorDecl -> out.append("ErrorDecl ${n.message} ${fmt(n.pos)}\n")
        // ---- 语句（S-16~S-30）----
        is sublimation.frontend.ast.VarStmt -> {
            out.append("VarStmt ${n.name} ${fmt(n.pos)}\n")
            n.type?.let { kid("type", it, indent + 1, out) }
            kid("value", n.value, indent + 1, out)
        }
        is sublimation.frontend.ast.AssignStmt -> {
            out.append("AssignStmt ${fmt(n.pos)}\n")
            kid("target", n.target, indent + 1, out)
            kid("value", n.value, indent + 1, out)
        }
        is sublimation.frontend.ast.AxiomStmt -> {
            out.append("AxiomStmt ${fmt(n.pos)}\n")
            n.props.forEachIndexed { i, e -> kid("props[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.ByStmt -> {
            out.append("ByStmt ${fmt(n.pos)}\n")
            n.props.forEachIndexed { i, e -> kid("props[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.UncheckedStmt -> {
            out.append("UncheckedStmt ${fmt(n.pos)}\n")
            kid("inner", n.inner, indent + 1, out)
        }
        is sublimation.frontend.ast.ExprStmt -> {
            out.append("ExprStmt ${fmt(n.pos)}\n")
            kid("expr", n.expr, indent + 1, out)
        }
        is sublimation.frontend.ast.BTSStmt -> {
            out.append("BTSStmt ${fmt(n.pos)}\n")
            kid("form", n.form, indent + 1, out)
            n.premises.forEachIndexed { i, e -> kid("premises[$i]", e, indent + 1, out) }
            kid("conclusion", n.conclusion, indent + 1, out)
        }
        is sublimation.frontend.ast.WhenPropStmt -> {
            out.append("WhenPropStmt ${fmt(n.pos)}\n")
            kid("disjunction", n.disjunction, indent + 1, out)
            n.arms.forEachIndexed { i, a -> whenPropArmKid("arms[$i]", a, indent + 1, out) }
        }
        is sublimation.frontend.ast.StrategyStmt -> {
            out.append("StrategyStmt ${n.strategy} ${fmt(n.pos)}\n")
            kid("goal", n.goal, indent + 1, out)
        }
        is sublimation.frontend.ast.ExistIntroStmt -> {
            out.append("ExistIntroStmt ${fmt(n.pos)}\n")
            n.witnesses.forEachIndexed { i, e -> kid("witnesses[$i]", e, indent + 1, out) }
            kid("prop", n.prop, indent + 1, out)
        }
        is sublimation.frontend.ast.ExistsElimStmt -> {
            out.append("ExistsElimStmt ${n.name} ${fmt(n.pos)}\n")
            kid("prop", n.prop, indent + 1, out)
            n.body.forEachIndexed { i, e -> kid("body[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.FunStmt -> {
            out.append("FunStmt ${fmt(n.pos)}\n")
            kid("fn", n.fn, indent + 1, out)
        }
        is sublimation.frontend.ast.IfPropStmt -> {
            out.append("IfPropStmt ${fmt(n.pos)}\n")
            kid("prop", n.prop, indent + 1, out)
            n.thenStmts.forEachIndexed { i, e -> kid("then[$i]", e, indent + 1, out) }
            n.elseStmts.forEachIndexed { i, e -> kid("else[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.ReturnStmt -> {
            out.append("ReturnStmt ${n.label ?: ""} ${fmt(n.pos)}\n")
            n.expr?.let { kid("expr", it, indent + 1, out) }
        }
        is sublimation.frontend.ast.ErrorStmt -> out.append("ErrorStmt ${n.message} ${fmt(n.pos)}\n")
        // ---- 表达式（S-31~S-40）----
        is sublimation.frontend.ast.IntLit -> out.append("IntLit ${n.value} ${fmt(n.pos)}\n")
        is sublimation.frontend.ast.FloatLit -> out.append("FloatLit ${n.value} ${fmt(n.pos)}\n")
        is sublimation.frontend.ast.StrLit -> out.append("StrLit ${n.value} ${fmt(n.pos)}\n")
        is sublimation.frontend.ast.NameRef -> out.append("NameRef ${n.name} ${fmt(n.pos)}\n")
        is sublimation.frontend.ast.ReturnSym -> out.append("ReturnSym ${fmt(n.pos)}\n")
        is sublimation.frontend.ast.ParamRefExpr -> out.append("ParamRefExpr $${n.index} ${fmt(n.pos)}\n")
        is sublimation.frontend.ast.TopExpr -> out.append("TopExpr ${fmt(n.pos)}\n")
        is sublimation.frontend.ast.BotExpr -> out.append("BotExpr ${fmt(n.pos)}\n")
        is sublimation.frontend.ast.BinExpr -> {
            out.append("BinExpr ${n.op} ${fmt(n.pos)}\n")
            kid("left", n.left, indent + 1, out)
            kid("right", n.right, indent + 1, out)
        }
        is sublimation.frontend.ast.UniExpr -> {
            out.append("UniExpr ${n.op} ${fmt(n.pos)}\n")
            kid("operand", n.operand, indent + 1, out)
        }
        is sublimation.frontend.ast.BorrowExpr -> {   // t101：&v 借用（语法层中间态）
            out.append("BorrowExpr ${fmt(n.pos)}\n")
            kid("operand", n.operand, indent + 1, out)
        }
        is sublimation.frontend.ast.TypeTestExpr -> {
            out.append("TypeTestExpr ${fmt(n.pos)}\n")
            kid("target", n.target, indent + 1, out)
            kid("type", n.type, indent + 1, out)
        }
        is sublimation.frontend.ast.IsPatExpr -> {
            out.append("IsPatExpr ${fmt(n.pos)}\n")
            kid("target", n.target, indent + 1, out)
            kid("pat", n.pat, indent + 1, out)
        }
        is sublimation.frontend.ast.TaskExpr -> {
            out.append("TaskExpr ${fmt(n.pos)}\n")
            kid("body", n.body, indent + 1, out)
        }
        is sublimation.frontend.ast.FlowExpr -> {
            out.append("FlowExpr ${fmt(n.pos)}\n")
            kid("body", n.body, indent + 1, out)
        }
        is sublimation.frontend.ast.CallExpr -> {
            out.append("CallExpr ${fmt(n.pos)}\n")
            kid("callee", n.callee, indent + 1, out)
            n.args.forEachIndexed { i, e -> kid("args[$i]", e, indent + 1, out) }
            n.namedArgs.forEach { (k, e) -> kid("named[$k]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.InstExpr -> {
            out.append("InstExpr ${fmt(n.pos)}\n")
            kid("target", n.target, indent + 1, out)
            n.terms.forEachIndexed { i, e -> kid("terms[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.FieldExpr -> {
            out.append("FieldExpr ${n.name} ${fmt(n.pos)}\n")
            kid("target", n.target, indent + 1, out)
        }
        is sublimation.frontend.ast.FunRefExpr -> {
            out.append("FunRefExpr ${n.name} ${fmt(n.pos)}\n")
            kid("target", n.target, indent + 1, out)
        }
        is sublimation.frontend.ast.SymbolCallExpr -> {
            out.append("SymbolCallExpr ${n.op} ${fmt(n.pos)}\n")
            n.args.forEachIndexed { i, e -> kid("args[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.LambdaExpr -> {
            out.append("LambdaExpr ${n.params} ${fmt(n.pos)}\n")
            kid("body", n.body, indent + 1, out)
        }
        is sublimation.frontend.ast.TupleExpr -> {
            out.append("TupleExpr ${fmt(n.pos)}\n")
            n.items.forEachIndexed { i, e -> kid("items[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.StructCtorExpr -> {
            out.append("StructCtorExpr ${n.struct} ${fmt(n.pos)}\n")
            n.assigns.forEach { (k, e) -> kid("assigns[$k]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.ByExpr -> {
            out.append("ByExpr ${fmt(n.pos)}\n")
            kid("target", n.target, indent + 1, out)
            n.props.forEachIndexed { i, e -> kid("props[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.VarExpr -> {
            out.append("VarExpr ${n.name} ${fmt(n.pos)}\n")
            n.type?.let { kid("type", it, indent + 1, out) }
            kid("value", n.value, indent + 1, out)
        }
        is sublimation.frontend.ast.AnonFunExpr -> {
            out.append("AnonFunExpr ${n.name} ${fmt(n.pos)}\n")
            n.theory.forEachIndexed { i, t -> theoryKid("theory[$i]", t, indent + 1, out) }
            n.params.forEachIndexed { i, p -> paramKid("params[$i]", p, indent + 1, out) }
            n.preps.forEachIndexed { i, e -> kid("preps[$i]", e, indent + 1, out) }
            n.retType?.let { kid("retType", it, indent + 1, out) }
            n.posts.forEachIndexed { i, e -> kid("posts[$i]", e, indent + 1, out) }
            n.body?.let { kid("body", it, indent + 1, out) }
        }
        is sublimation.frontend.ast.BlockExpr -> {
            out.append("BlockExpr ${fmt(n.pos)}\n")
            n.stmts.forEachIndexed { i, e -> kid("stmts[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.IfExpr -> {
            out.append("IfExpr ${fmt(n.pos)}\n")
            kid("cond", n.cond, indent + 1, out)
            kid("thenBlock", n.thenBlock, indent + 1, out)
            n.elseBlock?.let { kid("elseBlock", it, indent + 1, out) }
        }
        is sublimation.frontend.ast.WhenExpr -> {
            out.append("WhenExpr ${n.subjectBinding ?: ""} ${fmt(n.pos)}\n")
            n.subject?.let { kid("subject", it, indent + 1, out) }
            n.arms.forEachIndexed { i, a -> whenArmKid("arms[$i]", a, indent + 1, out) }
        }
        is sublimation.frontend.ast.QuantExpr -> {
            out.append("QuantExpr ${n.q} ${n.names} ${fmt(n.pos)}\n")
            kid("body", n.body, indent + 1, out)
        }
        is sublimation.frontend.ast.ErrorExpr -> out.append("ErrorExpr ${n.message} ${fmt(n.pos)}\n")
        // ---- 类型（S-15）----
        is sublimation.frontend.ast.NamedType -> {
            out.append("NamedType ${n.name} ${fmt(n.pos)}\n")
            n.args.forEachIndexed { i, e -> kid("args[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.FunType -> {
            out.append("FunType ${fmt(n.pos)}\n")
            n.theory.forEachIndexed { i, t -> theoryKid("theory[$i]", t, indent + 1, out) }
            n.params.forEachIndexed { i, e -> kid("params[$i]", e, indent + 1, out) }
            n.context?.forEachIndexed { i, e -> kid("context[$i]", e, indent + 1, out) }
            kid("ret", n.ret, indent + 1, out)
        }
        is sublimation.frontend.ast.TupleType -> {
            out.append("TupleType ${fmt(n.pos)}\n")
            n.items.forEachIndexed { i, e -> kid("items[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.MutType -> {
            out.append("MutType ${fmt(n.pos)}\n")
            kid("inner", n.inner, indent + 1, out)
        }
        is sublimation.frontend.ast.AtomicType -> {
            out.append("AtomicType ${fmt(n.pos)}\n")
            kid("inner", n.inner, indent + 1, out)
        }
        is sublimation.frontend.ast.RefType -> {     // t95：@ref T（语法层中间态，语义在 t97）
            out.append("RefType ${fmt(n.pos)}\n")
            kid("inner", n.inner, indent + 1, out)
        }
        is sublimation.frontend.ast.UnionType -> {
            out.append("UnionType ${fmt(n.pos)}\n")
            n.variants.forEachIndexed { i, e -> kid("variants[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.QualifiedType -> {
            out.append("QualifiedType ${n.module}::${n.name} ${fmt(n.pos)}\n")
            n.args.forEachIndexed { i, e -> kid("args[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.ErrorType -> out.append("ErrorType ${n.message} ${fmt(n.pos)}\n")
        // ---- 模式（S-42）----
        is sublimation.frontend.ast.PatLit -> {
            out.append("PatLit ${fmt(n.pos)}\n")
            kid("value", n.value, indent + 1, out)
        }
        is sublimation.frontend.ast.PatCtor -> {
            out.append("PatCtor ${n.name} ${fmt(n.pos)}\n")
            n.args.forEachIndexed { i, e -> kid("args[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.PatTuple -> {
            out.append("PatTuple ${fmt(n.pos)}\n")
            n.items.forEachIndexed { i, e -> kid("items[$i]", e, indent + 1, out) }
        }
        is sublimation.frontend.ast.PatIs -> {
            out.append("PatIs ${n.bind ?: ""} ${fmt(n.pos)}\n")
            kid("type", n.type, indent + 1, out)
        }
        is sublimation.frontend.ast.PatBind -> out.append("PatBind ${n.name} ${fmt(n.pos)}\n")
        is sublimation.frontend.ast.PatElse -> out.append("PatElse ${fmt(n.pos)}\n")
        is sublimation.frontend.ast.PatCond -> out.append("PatCond ${fmt(n.pos)}\n")
        is sublimation.frontend.ast.ErrorPattern -> out.append("ErrorPattern ${n.message} ${fmt(n.pos)}\n")
    }
}

private fun kid(prefix: String, c: AstNode, indent: Int, out: StringBuilder) {
    node(c, indent, out, "$prefix: ")
}

/** 理论参数（TypeParam/PropParam 非 AstNode，位置自携）。 */
private fun theoryKid(prefix: String, t: TheoryParam, indent: Int, out: StringBuilder) {
    out.append("  ".repeat(indent)).append(prefix).append(": ")
    when (t) {
        is sublimation.frontend.ast.TypeParam ->
            out.append("TypeParam ${t.name} arity=${t.arity}${if (t.constraint != null) " constraint=${t.constraint}" else ""} ${fmt(t.pos)}\n")
        is sublimation.frontend.ast.PropParam -> out.append("PropParam ${t.name} ${fmt(t.pos)}\n")
    }
}

/** 形参（非 AstNode：类型与默认值作为子节点）。 */
private fun paramKid(prefix: String, p: Param, indent: Int, out: StringBuilder) {
    out.append("  ".repeat(indent)).append(prefix).append(": Param ${p.name} ${fmt(p.pos)}\n")
    p.type?.let { kid("type", it, indent + 1, out) }   // t113：省略位不打印类型子节点
    p.default?.let { kid("default", it, indent + 1, out) }
}

/** when 表达式臂（非 AstNode）。 */
private fun whenArmKid(prefix: String, a: WhenArm, indent: Int, out: StringBuilder) {
    out.append("  ".repeat(indent)).append("$prefix: WhenArm ${fmt(a.pos)}\n")
    kid("pattern", a.pattern, indent + 1, out)
    a.guard?.let { kid("guard", it, indent + 1, out) }
    a.cond?.let { kid("cond", it, indent + 1, out) }
    kid("body", a.body, indent + 1, out)
}

/** when 命题语句臂（非 AstNode）。 */
private fun whenPropArmKid(prefix: String, a: WhenPropArm, indent: Int, out: StringBuilder) {
    out.append("  ".repeat(indent)).append("$prefix: WhenPropArm ${fmt(a.pos)}\n")
    kid("condition", a.condition, indent + 1, out)
    a.body.forEachIndexed { i, e -> kid("body[$i]", e, indent + 1, out) }
}

/** 位置紧凑形态（对齐 JSON 产物 pos 口径：单点 l:c / 区间 l:c-l:c）。 */
private fun fmt(p: SourceRange): String =
    if (p.end == null) "${p.start.line}:${p.start.col}"
    else "${p.start.line}:${p.start.col}-${p.end.line}:${p.end.col}"
