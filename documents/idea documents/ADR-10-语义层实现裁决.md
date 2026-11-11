# ADR-10：语义层（t7）实现裁决

- 状态：已落档（2026-11-04，t7 实现期）
- 范围：AST→HIR 转换与名称解析（ADR-6 §五 t7 骨架）的实现层裁决；不触碰 ADR-1/ADR-5 的既有决策，仅为其落地补充边界判定
- 关联：ADR-1（映射表/消解闭名单/信息保留清单）、ADR-5（符号引用/SymbolKind/逃逸头不复刻）、ADR-6（四维解耦/两阶段/单一驱动）、ADR-7（解析层已吞的糖）、ADR-8（类型改向待定）

## 一、交付物（frontend/src/main/kotlin/sublimation/frontend/semantic/）

| 文件 | 职责 |
|---|---|
| Semantic.kt | 对外唯一入口 `compile(file, parseNotes)：Pair<HirFile, DiagBag>`——collect + elaborate，吸收两袋 |
| SymbolTable.kt | 全局表（struct/enum/class/alias/ctor/fun/theorem/propdef/method/impl）+ 帧栈 + 46 内建函数/15 内建类型登记 |
| Collector.kt | 两遍：pass1 声明登记（E-DUP-DECL 旧码集消息按 decl 族区分；_ 函数名不入表；@theorem 分表）；pass2 类型别名 vs 既有非别名类型 |
| NameResolver.kt | 值/类型/构造子解析：E-UNBOUND-NAME（含 _ 丢弃标记旧专怨口径）；方法名解析静默 |
| PropNormalizer.kt | 命题/项归一（逻辑连接词、量词、∈→in、ParamRefExpr 决策65 改写、E-PROP-FORM 半成品） |
| Elaborator.kt | 单一驱动：全 AST→HIR 构造，闭名单四条改写上树，nid 前序分配 |

## 二、裁决清单

### 2.1 elaborate 纯函数契约的边界（ADR-6 §二落地）

- AST 只读：不改写任何 AST 节点（when 槽位归一以**拷贝**实现，替代旧 checker 的原地改写——ADR-1 §3.1 病根对策）。
- SymbolTable 全局层只读：t7 单文件编译无任何全局修改源（collect 已完成登记后 elaborate 才进入）；帧栈/类型参数栈均为瞬态 push/pop，成对平衡（inline frame 帮手 + try/finally）。
- 脚本顶层语句的隐式帧：declareLocal 在帧栈为空时开一帧（同旧 Frame 链根帧口径）——否则顶层 var 绑定无处可查。
- nid 前序单调：每个 h* 节点在构造其子节点前分配 id；同一源两次编译产物逐字相等（数据类相等）。
- compile 不吸收解析期诊断（层间归属，S-1 层间总契约 1/2）：调用方（CLI/t8）负责并袋。

### 2.2 新增诊断码（表内未列即本裁决引入，动机均回溯 ADR-5 决策 3）

| 码 | 语义 | 半成品落树 |
|---|---|---|
| E-PROP-POS | 命题专属语法（$/⊤/⊥/量词）出现在程序位 | HirErrorExpr |
| E-PROP-FORM | 命题（项）位出现无法归一的程序面表达式（ADR-5 决策 3：逃逸头不复刻） | 命题 HirPBot / 项 `__unrepresentable__` 哨兵 + D-PROP-UNREPR |
| E-CTOR-ARITY | 载荷构造子裸名用作 when 分臂顶层模式（旧实现由 ctor 裁定隐含，无显式码） | HirPatCtor(args=[]) |

### 2.3 新增留痕码（D 族，DESUGAR）

D-METHOD-SUGAR（receiver 首参展开）、D-NAMED-ARG（命名实参重排）、D-DOLLAR-N（上下文 $n→形参名，决策65）、D-PAT-SLOT（槽位旧名→相等比较）、D-PAT-CTOR（零字段/裸名构造子归一，I2）、D-PROP-UNREPR（命题位半成品留痕）。

### 2.4 符号解析的 t7 边界（symbol=null 静默，不落诊断）

以下解析需类型导向，归 t8/t9：HirField.field（接收者类型）、方法糖 callee 之外的**分派**（方法表按名查找可有，按接收者分派无）、HirCall.dictionary/slot/dictArgs（类型类字典）、理论参数约束解析保留但类型参数引用本身 symbol=null（**不设 SymbolKind.TYPEPARAM**——hir/Symbol.kt 为 t10 在途契约，不改）。

- 命名参数重排仅当 callee 为登记在册的全局 FUN/THEOREM 且 Collector 记录了 paramNames（destructParams → 旧码 E-NAMED-ARG）；缺参/默认值核对（含 main 形参规则、HKT arity、@root 白名单）属 t8 类型层，t7 不重复旧 checkDeclTypes。
- FunType 的理论参数只承载类型参数（类型位无命题参数通道）：AST 的 PropParam 若出现在类型位被静默丢弃（语法层应保证不产生）。
- 类型位上下文（FunType.context）的命题不做 $n 改写（形参不在词法作用域）。
- 模块限定（QualifiedType）：本期单文件，模块段折叠留给后期模块挂载任务（ADR-1 §3.2）；不落诊断、symbol=null。

### 2.5 旧码集对齐（P-7）

沿用：E-DUP-DECL（按族消息：函数/定理函数/结构体/类型类/枚举/类型别名/自定义命题/构造子重复）、H-AMBIGUOUS-CTOR（跨枚举同名 ctor 裸名歧义提示）、E-UNBOUND-NAME（值/类型/构造子三通道；_ 丢弃标记旧专怨口径只保留在值位）、E-NAMED-ARG（方法糖不支持/method-destructuring/未知参数/位置占用四消息）、E-PARAM-REF（上下文 $n 越界）/H-PARAM-REF（可用参数名提示）/E-PARAM-REF-IN-BODY、E-SYNTAX（IsPatExpr 过渡节点未脱糖哨兵）。prelude 枚举豁免名单（Bool/Null/Optional）保留。

### 2.6 hir 契约补绘（跨 t10 协调项）

ADR-1 §3.2 映射表原遗漏程序面 BinExpr/UniExpr（解析器对算术/比较/一元一律产出 AST BinExpr/UniExpr，HIR 程序面无落点）。补 `HirBinExpr(op,left,right,type,...)` 与 `HirUniExpr(op,operand,type,...)`（程序面；命题位一元仍归一 HirPNot），HirJson.kt 发射分支同步（t10 已确认，json 测试由 t10 同步）。

### 2.7 遗产层推迟项（t8/t9/t10 食谱）

类型推导与重载分派、mut 绑定 mutableTarget 核定（HirAssign/VarExpr.deepCopy/mutableTarget 恒 false/构造期默认）、证明 obligation verdict 默认（proofContext=空/rule=null/verdict=null；唯 axiom 落 R-AXIOM+PROVEN）、HirTask/Flow.purity、方法糖 await 注入。

### 2.8 空参 PatCtor 即裸名（解析层契约）

解析层在 ctor/tuple 实参位与臂顶层对「裸名」「零参构造 C()」产出**同形**的 `PatCtor(name, args=[])`（臂顶层另以 PatBind 送裸名）。故 elaborator 的 pattern() 对空参 PatCtor 与 PatBind 统一走**裸名裁定**（bareNamePattern，按槽位模式分岔）：
- SLOT 位：外层同名 → 相等比较拷贝（D-PAT-SLOT）；否则新名绑定；
- TOP 位：ctor 表命中且零字段 → HirPatCtor（D-PAT-CTOR）；命中带载荷 → E-CTOR-ARITY；未命中 → 绑定。

显式 `C()` 在槽位位与裸名同口径（解析层同形所致；旧实现同构）。

### 2.9 prelude 值字面量的名字层兜底（BUILTIN_VALUES）

`null` 在词法层是 IDENT（解析层产出 NameRef），单文件期未挂 prelude ⇒ 无 Ctor/Null 值可解析。t7 在 lookupValue 末段兜底登记 `BUILTIN_VALUES = {null, true, false}`（kind=BUILTIN，懒登记）；类型归属（Null/Bool）属 t8。prelude 枚举豁免名单（Bool/Null/Optional）保留——用户同名 enum 不覆盖兜底值。

## 三、验收口径

- :frontend:compileKotlin 绿；
- SemanticTest（22 用例）覆盖：作用域遮蔽/帧退出、未解析半成品、闭名单四条+构造子裁定（裸名/is/载荷三形态）、重复声明三场景、nid 确定性、AST 只读、留痕搬移（D-TRAILING）、内建/类型参数/命题定义符号、哨兵三码、Error 容错；
- 全模块 :frontend:test 绿（22 SemanticTest + t10 同步后的 19 json 测试；parser/hir 全套 -- 187+ 用例无失败）。
