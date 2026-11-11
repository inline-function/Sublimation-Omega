# ADR-6 语义层组织与 Checker 解耦方案

> 状态：🟡 队长设计记录（2026-11-04），t7 开工前发布；semantics 落地时的实现裁决以其后续 ADR 补全/修订为准
> 相关任务：t7 / t8 / t9；相关规范：S-1 §3、S-2 §4、D-9、ADR-1 §3.5、ADR-5
> 缘起：用户要求把语义层组织与 Checker 解耦方案落档——对话记忆会失传，规范不会（S-3 §2）

## 一、背景

旧 checker/ 是「巨石 + 旁挂表」结构：Checker.kt 单文件什么都查，结论经 15+ 张
IdentityHashMap 传给 JsCodeGen，从不落盘；AST 被原地突变（WhenArm.pattern 为 var）；
Prop 家族包内私设、codegen 整体擦除。t7~t9 要在 HIR 构造中重做全部语义分析，
必须先定代码组织，避免按旧形态照抄。

## 二、总体架构：collect → elaborate 两阶段（承 ADR-1 §3.5）

```
FileAst ──① Collector──> SymbolTable（符号表）
                            │
FileAst + SymbolTable ──② Elaborator──> HirFile + DiagBag（纯函数）
```

```kotlin
/** 契约：AST 与符号表只读；输出由输入唯一决定（S-1 层间总契约 3）。 */
fun elaborate(file: FileAst, symbols: SymbolTable, notes: ParseNotes): Pair<HirFile, DiagBag>
```

- **阶段一 collect**：只扫声明，建分层符号表；内部两遍（collectDecl → checkDeclTypes）
  支持类型别名前向引用（沿用旧 Symbols.kt 结构）。
- **阶段二 elaborate**：深度遍历 AST，一次构造带全部结论的 HIR——名称解析、类型推导、
  证明检查不是独立 pass，随构造完成（S-1 §3）。

## 三、包内布局（按职责拆，不按节点类型拆）

```
sublimation.frontend.semantic
├─ Semantic.kt        对外唯一入口：compile(FileAst, notes) → (HirFile, DiagBag)
├─ SymbolTable.kt     SymbolId 体系 + 分层作用域（全局层/函数帧/块作用域）
├─ Collector.kt       阶段一：两遍扫描登记符号
├─ Elaborator.kt      阶段二主驱动：深度遍历、复制构造 HIR、组装 nid
├─ NameResolver.kt    t7：NameRef → SymbolRef（限定引用、遮蔽、未解析→诊断）
├─ PropNormalizer.kt  t7/t9：命题位表达式 → HirProp/HirTerm 归一（ASCII⇄Unicode）
├─ TypeInfer.kt       t8：类型推导，结论写 type 字段
└─ ProofChecker.kt    t9：Γ 查询、MP 闭包、算术通道、证明义务核对
```

## 四、四维解耦

### 4.1 数据解耦：结论上树，消灭旁挂表

| 旧旁挂表 | 新落点 |
| --- | --- |
| dictHits / consHits / dictSubHits | HirCall.dictionary / slot / dictArgs |
| methodSugarArgs | HirCall.methodSugar |
| namedArgOrder | 命名参数重排后直接落实参序 |
| deepCopyHits | 绑定/赋值节点 deepCopy 标志 |
| asyncAwaitHits / flowEmitHits | awaitPoint / HirTask·HirFlow 效应字段 |
| mergedStructCtor / receiveSmartHits / hktKindPosHits | 各自节点的结论字段 |
| listMethodHits / rangeHits | 不设字段——后端由已解析 callee symbol 自行映射（ADR-5 裁） |
| inferredVars | 每节点 type 字段 |

checker 与 codegen 之间没有暗号表——**树即接口**。

### 4.2 职责解耦：一个巨石 → 四件套

```
旧文件                        →  新家
Symbols.kt                    →  Collector + SymbolTable
Types.kt(render/substT)       →  TypeInfer 内部（类型代数归语义层，AST 保持零语义）
TypeInfer.kt                  →  TypeInfer
Prop.kt(PAtom/termEq)         →  PropNormalizer + 树自身结构相等（P-STRUCT-EQ）
ArithProve.kt                 →  ProofChecker（算术通道）
CallCheck/WhenCheck/ImplCheck →  Elaborator 各节点构造分支 + 节点结论字段
Diffusion.kt(上下文弥散)      →  Elaborator 帧管理：调用后把下文命题并入当前帧事实集
Purity.kt / Aggressive.kt     →  纯度结论字段 / ProofChecker 激活实例化策略
Checker.kt(其余全部)          →  Elaborator 驱动的各构造分支
```

### 4.3 控制解耦：Elaborator 单驱动

只有 Elaborator 走树。TypeInfer、ProofChecker 都不遍历——它们是纯函数，
签名形如 `(节点, 上下文) → (结论, 诊断)`，由 Elaborator 构造到该节点时调用。
旧实现各检查器拿着共享状态各走各的；新实现没有「遍历竞争」。

### 4.4 状态解耦：帧栈 + Γ 快照

- 事实集挂**帧栈**（函数帧/块作用域），证明义务节点核对时刻**快照**进
  proofContext.facts（归一化并集保留；搜索过程状态按 ADR-1 §3.8 丢弃）。
- 旧全局 allFacts 可变池废除——检查顺序不再影响结果。
- 诊断单向落 DiagBag，任何检查不抛异常——半成品落树（S-1 §2 容错链路）。

## 五、落地节奏

t7 交 Elaborator 骨架 + NameResolver + SymbolTable；t8 把 TypeInfer 插进同一驱动；
t9 插 ProofChecker——**同一根驱动轴上逐步加注**，不是推倒重写。
P-7 全对齐验证靠旧测试移植回归兜底，t12 e2e 收口。

## 六、开放项

- 四件套之间的接口签名与文件内再细分（如 TypeInfer 是否再拆类型代数）由 t7 落地裁决，
  裁决记录追加新 ADR（不覆盖本文）。
