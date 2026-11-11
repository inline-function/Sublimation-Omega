# ADR-7：语法分析与容错模型（S/D 层实现裁决）

| 字段 | 值 |
|---|---|
| 编号 | ADR-7 |
| 主题 | 手写递归下降语法分析器：三入口分发、容错恢复、邻接消歧实现口径、脱糖留痕 |
| 任务 | t4（语法分析器 S/D 层） |
| 依赖条款 | S-1 §2（解析层契约）、S-2 §3.2（依赖规则）、《形式化文法》§2/§3/§2.13、ADR-1 §3.5（ParseResult 契约）、ADR-2（词法错误恢复）、ADR-3/ADR-4（AST 零语义 + 位置模型）、D-9/D-10 |
| 上游 | t2 lexer（token 流 + LexError）、t3 AST（带位置节点） |
| 下游 | t6 语义层（消费 ParseResult）、t9 checker（消费容错节点与诊断）、t10 JSON（notes 通道） |

## 1. 背景

《形式化文法》§2 给出 42 条产生式（S-1~S-42）与三入口（§2.1 工程/脚本/片段），§3 给出 21 条
邻接消歧规则（D-1~D-21），§2.13 给出解析期脱糖表。旧实现（KotlinProject/sublimation）是手写
递归下降 + 异常驱动的错误处理：任何语法错都抛 ParseFailure 中断整个文件解析，且 AST 节点
无位置。新工程契约（S-1 §2、ADR-1 §3.5）要求：

1. 语法错误**不抛异常**（文件入口）：记诊断 + 在错误处产出 Error* 占位节点，可挽救的局部子树
   保留在相邻字段（半成品原则）；
2. 片段入口（注解处理器宿主）保留**异常契约**（ParseFailure）——宿主以片段编辑 AST，片段
   破损即宿主编程错，必须硬失败；
3. 诊断走 diag 横切包的统一模型（ADR-4 后 parser 单向依赖 diag）。

## 2. 决策

### 2.1 三入口分发（§2.1）

| 入口 | 函数 | 顶层 | 异常 |
|---|---|---|---|
| 工程 | parseSource / parseSourceWithNotes | 仅声明与 axiom（S-2/S-3） | 无（容错） |
| 脚本 | parseScriptSource / parseScriptSourceWithNotes | 任意语句 + 声明 | 无（容错） |
| 片段 | parseDeclSnippet / parseFunBodySnippet / parseStmtsSnippet | — | ParseFailure（strict） |

strict 构造：片段入口首个 ERROR 级诊断即 strictFail 抛 ParseFailure（S-3 片段须耗尽 +
无语法错），再要求整段耗尽（requireSnippetEof："$what 片段后面有多余内容"）。

### 2.2 容错恢复模型（S-1 §2）

- **诊断与占位双通道**：每处语法错记一条 E-SYNTAX 诊断（ sev=ERROR，带 D-10 结构化位置），
  同时在错误处产出 ErrorDecl / ErrorStmt / ErrorExpr / ErrorType / ErrorPattern 占位节点；
  可挽救的局部子树（如 ifis 组合条件中的 then/else 分支、impl 成员签名）保留在相邻字段，
  语义层可对含错文件继续检查（t6/t9 的输入前提）。
- **进展保证**：所有列表循环（文件入口、语句块、参数/实参/理论参数列表、when 分支）带
  ensureProgress——循环体未消耗任何 token 时强制跳过一个 token，永不死循环。
- **声明级失败同步**：无名可救的破损声明（如 `fun 1`）由 errDeclSync 跳到声明边界
  （入口关键字 / 分号 / 闭合括号 / EOF），残段不进 AST；对比"占位后不动"会残留垃圾 token
  被入口循环二次诊断为"顶层只能写声明"，一棵破损声明产两条误导诊断。
- **块内容错**：块内语句错不阻断后续语句（`var 1 = 2\nprint(3)` → ErrorStmt + 继续解析）。

### 2.3 邻接消歧的实现口径（D-1~D-21）

换行不是 token，语句边界由邻接涌现——全部判据是 [Token.precededBySpace]（L-7）与同行关系，
语法层零符号表（S-1 §2）。实现约定：**peek/peekAt 一律相对当前游标**（peekAt(i) = pos+i），
isCtorForm 等助手收相对偏移（实施中发现的偏差记录见 §5 复盘）。

关键规则落点：
- D-1 紧贴 `<` 是后缀实例化、带空格 `<` 是比较中缀（约束列表前瞻用同一判据分派）；
- D-2/D-4 `>`/`is` 跨行即视为下一结构起始，不作中缀；
- D-3 句点前后的空格决定字段取用 vs 小数；
- D-5 中缀糖（`q add p`）展开为 CallExpr 并留痕 D-INFIX；
- D-13 约束列表前瞻（constraintListAhead）区分前置/后置上下文（§2.2 上下文弥散）；
- D-14 命名字段构造前瞻（大写 IDENT + `(` + `IDENT =`）分派 StructCtorExpr；
- D-15/D-16/D-17/D-18 when/ifis/花括号 lambda/@return 标签的形态判定；
- D-20 ∃ 语句块边界；D-21 箭头停止词。

### 2.4 解析期脱糖与留痕（§2.13 + D-9）

| 脱糖 | 产物 | 留痕 |
|---|---|---|
| D-INTP 字符串插值 | `"a${n}b"` → concat(concat("a", toStr(n)), "b") | 子解析诊断并入主袋 |
| S-1a in 中缀 | CallExpr(in, …) | — |
| S-5 无类型类 impl | 成员 → self 首参自由函数 + kind 占位提升为成员类型参数 | — |
| S-8 裸 ∵ | TopExpr | — |
| S-10 ifis | IfExpr→WhenExpr（主题+模式分支+else 兜底） | — |
| D-5 中缀糖 | CallExpr | D-INFIX（节点级 ParseNote） |
| 尾随 lambda | 并入末位实参 | D-TRAILING（节点级） |

留痕双通道（ADR-1 §3.5）：节点级 ParseNote（挂被脱糖产出的 AST 节点，HIR 构造期按节点
对应搬到 HirNode.notes，D-9）与诊断袋级 Diag（E-*/H-*）。提示级 H-ARITH-PREC（D-10：混优先级
劝加括号，不报错、不改树——语言无优先级、左结合）。

### 2.5 诊断模型并入 diag 包

parser 不再私有重复 Severity/ParseDiag——ParseResult.diag 直接用 diag.Diag
（构造序 severity, code, pos, message），LexError 在文件入口包装为 E-LEX 入袋
（层间总契约 2：lexer/parser/语义同袋；ADR-5 亦载明"LexError 由 parser 层包装入 DiagBag（t4 范围）"）。

## 3. 与旧实现的偏差（移植日志，S-3 §2.3）

1. **异常 → 容错**：旧文件级 ParseFailure（体形态互斥 / 顶层禁语句 / ifis 组合条件 /
   大括号 struct 体 / 语法错误带位置 / 空安全糖移除）一律改为诊断袋断言；异常只留片段入口
   与 strict 契约（ParserSnippetTest 11 例逐条等价移植）。
2. **位置携带**（ADR-3/ADR-4）：旧 assertSame(ReturnSym) 改类型断言；TheoryParam/TypeParam
   等值比较改名字列表比较（节点带位置后 equals 含 pos）。
3. **Type 无公共 name 字段**（t3）：旧测试对 Type.name 的断言改为 NamedType 转型取值；
   下游（t6 起统一 Eric）按此口径。
4. **移除项**：`T?` / `?.` / `?:` 词法层已删（t2，ADR-2），parser 无补救路径——
   未知字符以 E-LEX 入袋（旧 LexFailure 三例改诊断袋断言）。
5. **示例文件内联**：旧"整个示例文件可解析"读 examples/上下文弥散.subl；新工程无该目录，
   示例等价内联（内容与旧用例一致，断言力度不变）。

## 4. 后果

- 语义层拿到的是"尽力而为的 AST"：含 Error* 节点的文件仍产出完整 FileAst 结构（列表完整、
  位置完整），checker 可对错误文件继续检查并汇報语法诊断（ADR-5 载明容错节点 5 镜像入 HIR）。
- 驾驶手段明确：诊断袋为唯一的错误汇流处；ParseNote 为唯一的解析期留痕处。
- 片段宿主（注解处理器）的编程错仍硬失败，契约不退化。

## 5. 复盘：实施中发现的实现偏差

- **isCtorForm 相对/绝对偏移混淆**（ifis 五例回归红）：peekAt 是相对游标偏移，调用方误传
  绝对索引 pos+1 导致 `if (o is Some(e))` 落入 TypeTestExpr 路径。修正为相对偏移口径并写入
  §2.3 约定（同类助手一律收相对偏移）。
- **errDeclSync 的必要性**：`fun 1` 后残段若不同步，入口循环将其二次诊断为"顶层禁语句"，
  一棵破损声明产两条诊断且多一个垃圾 ErrorStmt 条目。
