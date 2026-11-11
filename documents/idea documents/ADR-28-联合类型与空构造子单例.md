# ADR-28：联合类型 / 空构造子单例 / 归纳类型 / when 穷尽性——语义层联合裁决

**任务**：t41（semantics） · **日期**：2026-11-06 · **前置**：ADR-24（HIR schema v2 与 enum 删除的载体工作，t40）、ADR-19（结构体构造子族与文法收紧）、ADR-17（调用点泛型实例化与合一）

## 一、背景与用户裁决

P-9/t40 删除 enum 后，「若干具名分支的析取」在语义层无载体：旧的 \`enum E { A, B }\` 语义（密封判定、ctor 判重、when 穷尽性、样本族 Cons/Some/Nil）全部失去落点。t41 的任务是在**不引入新文法节点**（t40 已定 \`->\` 箭头与 ADR-23 P8B 语法层）的前提下，用「结构体 + 联合类型别名」补齐等价能力，并把 18 条 t40 基线失败用例全部转绿。

**用户裁决（2026-11-06 15:10，逐条落到实现）**：
1. 联合支持一切类型表达式——struct 名、别名、类型应用（\`Some[T]\`）、元组、函数类型皆可作成员；
2. **空构造子判据 = 字段数 0**——\`struct P(x: Nat = 0)\` **不是**空构造子（有字段，仅带默认值），\`P()\` 合法；\\n   \`None()\` 调用报 **E-NULLARY-PAREN**（须写裸 \`None\`）；
3. \`is None\` 与裸名 \`None\` 模式**都允许**（两种形态语义等价、都计入覆盖）；
4. 空构造子是**全局唯一单例**（声明判重走既有 E-DUP-DECL）；
5. **归纳类型判据**：右值是联合且**至少一个成员不引用自身** → D-INDUCTIVE-TYPE（supplement 级留痕）；**全部成员引用自身** → E-CYCLE；
6. 命题面收尾：def 删除后命题可**原子使用**（未定义名不报未定义）；\`axiom Even(4) ↔ ∃k . 4 = 2 * k\` 须通过。

## 二、规则表（最终）

| 能力 | 载体 | 规则 |
|---|---|---|
| 联合声明 | \`type E = A | B\`（TypeAliasDecl） | 构造即展平；成员判重 E-DUP-UNION-MEMBER |
| 成员拓宽 | adaptOk | 成员 → 联合**单向**成立；联合 → 成员非法 |
| 空构造子 | \`struct None()\` | nullary 旗标（HIR + JSON）；\`None()\` 报 E-NULLARY-PAREN；裸名构造/模式/is 形态 |
| 归纳 | 同上联合 | 部分成员自引用 → D-INDUCTIVE-TYPE（SUPPLEMENT）；全部 → E-CYCLE |
| when 穷尽 | unionSealCtors | 全成员根名可枚举 → 覆盖判定；非具名成员 → 原因提示补 else |
| 结构体 ctor 槽位 | structFieldTypes | 字段声明类型按**主题实参**实例化（Box[Nat] → x: Nat） |

## 三、决策

### R-16：联合构造即展平 + 成员判重 + 带实参别名展开
- \`type E = A | B\` 在 TypeAliasDecl 分支与 AST \`type()\` 双侧经 \`flattenUnion(t, depth)\` 展平：变体中的**具名别名**就地展开（\`type E = A | D\` 且 \`type D = B | C\` → \`A | B | C\`），嵌套联合拼接；深度上限 8 杜绝无限展开。
- 成员判据 \`memberKey(v)\`：裸具名（\`A\`，无实参）取名字，其余取 HIR 结构键——**同名重复成员落 E-DUP-UNION-MEMBER**（\`type E = A | A\`）。
- **带实参的联合别名同展开**（本轮补）：\`type Box[T] = Some[T] | None\`，\`Box[Nat]\` → \`Some[Nat] | None\`——理论参数按位代入（\`subTpNames\` 的联合分支为本轮补：此前联合节点落 \`else\` 不代入，导致 \`var v: Box[Nat]\` 声明类型带未解 T）。展开在别名自身的理论参数作用域内重解目标（否则 alias.target 的 T 落「未知类型 T」伪错）。
- **联合感知的合一与适配**（本轮补，均严格增量）：\`unifyInto\` 联合按声明序成对合（拒绝指数对齐——真冲突由 adaptOk 落诊断）；\`adaptOk\` 联合→联合取「每个期望成员都有实际成员可注入」；\`renameTps/substTps/occurs\` 补联合递归（否则泛型联合形参解不出、返回类型不回代）。

### R-17：归纳类型环检（D-INDUCTIVE-TYPE / E-CYCLE）
- TypeAliasDecl 分支经 \`aliasChainContains(t, target, self, seen)\` 逐成员查自引用：
  - **全部成员引用自身** → E-CYCLE（无基态，归纳无意义）；
  - **部分成员引用自身** → D-INDUCTIVE-TYPE，severity = **SUPPLEMENT**，文案逐字：「X 是归纳类型定义（自引用 N 处）。归纳类型需至少一个不含自引用的构造子作为基态。」
- **判据解读（任务表 E-CYCLE 行的口径）**：\`A = B | C\` 且 \`B = A\` 的组合——当 **C 自由**（不引用 A）时，C 即基态，整体**归纳**而非环；表行「E-CYCLE」要求 **C 也引用 A**（A⇄B⇄C 全团无基态）才落错。本轮实现按此口径：成员**全部**在自身别名链上才报 E-CYCLE。
- 典型正例：\`type Lst[T] = Nil | Cons[T]\`（D-INDUCTIVE-TYPE）；\`type T = T\`（E-CYCLE）。

### R-18：空构造子单例
- 判据严格 = **字段数 0**（用户裁决 2）：\`struct None()\` nullary=true；\`struct P(x: Nat = 0)\` nullary=false（有默认值不等于无字段）。
- \`HirStructDecl.nullary: Boolean = false\` 默认值**保持既有构造兼容**（字段序内追加）；HIR JSON 经 \`fieldBool("nullary", ...)\` 出厂。
- 全局唯一：空构造子名与既有声明重名走既有 E-DUP-DECL（无特判）。

### R-19：E-NULLARY-PAREN（调用与模式双侧）
- \`None()\` **调用**：ctorCall 入口 gate \`st != null && st.fields.isEmpty()\` 落 E-NULLARY-PAREN「空构造子 X 不能用 () 构造，请直接写 X（空构造子全局唯一）」。
- \`None()\` **模式**：AST \`PatCtor(paren: Boolean = false)\` 由 Parser 四处（1697/1715/1751/1759）在圆括号形态置真；Elaborator 的 PatCtor 分支与 \`bareNamePattern(parenWritten)\` 同轨报错。
- 裸名 \`None\` 模式经既有 D-PAT-CTOR 归一为构造子模式；`is None` 保留**类型形态**（HirPatIs——None 是 struct 注册类型，不归一）。

### R-20：when 穷尽性（联合主题）
- \`unionSealCtors(subjectT)\`：联合主题（具名别名参数经 R-16 的 expand）取成员**根名表** + **是否全部可枚举**（根名在 structDecls）。全可枚举 → when 覆盖判定：`is A`（HirPatIs 名等）、构造子模式、裸名成员模式均计入；缺成员 → W-NON-EXHAUSTIVE + 未密封 when（函数体位落 E-NON-SEALED-MATCH，既有口径）。
- 联合含**非具名成员**（函数类型/元组成员等）→ \`whenNonExhaustive\` 携 \`reason\`「联合含非具名成员，无法静态穷尽，请补 else」。
- \`is A\` 覆盖证据：ctorCovered 补 \`HirPatIs && (p.type as? HirNamedType)?.name == ctor → emptyList()\`。

### R-21：结构体 ctor 槽位类型（模式绑定按主题实例化）
- 旧 enum 的 ctorSubst（替换表）随 t40 切成 emptyMap，模式槽位类型归 t41 重建：\`structFieldTypes(ctorName, subjectT)\`——成员字段声明类型 + **主题实参代入**，代入源两路：
  1. 联合主题里匹配成员的实参（\`Box[Nat]\` 展平后 \`Some[Nat]\` 位 → T := Nat）；
  2. 带实参具名联合别名的实参（\`fun f(x: E)\` 的参数走具名形态）。
- 名/参数数不齐时取原始字段类型（保守）；理论参数为空直接回退。
- 同源补丁：\`param()\` 不经 expand（保持具名头给扩展桶），故 unionSealCtors 与 adaptOk 自行展开。

## 四、命题面收尾（def 删除）

- HirPropDef 节点、HirJson 分支、SymbolTable 的 propDefs 表 / propDefEntry / lookupValue 回退 / whereLabel / PROPDEF 路由**全切除**（名解析遮蔽 R-10~R-15 通道清单不变——未新增通道）。
- 命题**原子使用**沿用既有 schematicNames 静默模式（NameResolver:51，propPos 位使用）：未定义名不报未定义。
- \`axiom Even(4) ↔ ∃k . 4 = 2 * k\` 通过：量词经 PropNormalizer 的 \`resolver.quantified(e.names, e.pos)\` 绑定（\`2k\` 邻接须写 \`2 * k\`——量词体不解析隐式乘）。

## 五、验证（2026-11-06T14:39Z，构建槽位独占）

- compileKotlin + compileTestKotlin **双快照 BUILD SUCCESSFUL，e/w=0**（14:42Z/14:43Z）。
- \`:frontend:test --rerun-tasks\`：**441 用例 / 0 失败**——t40 基线的 18 条失败**全部转绿**（PatternBindingSemanticsTest 6、SemanticTest 4、TypeInferTest 4、LambdaInferenceTest 2、StructCtorTest 1、WhenExhaustivenessTest 1；含 1 条任务表漏列的 bagD 负例），新增 UnionSemanticsTest 20 例全绿（任务 1~5 逐项覆盖）。
- 旧用例改写原则：**意图不变**（扩展桶三形态、TS-14 自由函数优先、泛型 ctor 推导、lambda 显式实参优先、空构造子判定……），仅 \`enum → struct + type 别名\`、\`=> → ->\`；两类断言随形态迁移：① struct 构造返回 **struct 名**（旧枚举返枚举名）；② 返回标注 \`X[T, E]\` 在 HIR 经 expand 为**联合**（成员携带解出的实参）。
- e2e **47/68**（= t40 基线；结构性违规 0；21 红的驱动与 t40 一致——19 enum 样本 E-ENUM-REMOVED + 手动证明 + 模块系统主文件，属 t46 样本迁移领地）。
- 教训：联合节点（HirUnionType）在**所有**结构遍历函数（subTpNames / renameTps / substTps / occurs / unifyInto / adaptOk）里都要有分支——else 分支不报错只静默不代入，是最难定位的一类 bug（本次定位靠单源探针 + diag 逐条打印）。
