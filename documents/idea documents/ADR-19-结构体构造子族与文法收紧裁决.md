# ADR-19：结构体构造子族与两处文法收紧裁决（t20）

| 字段 | 值 |
|---|---|
| 编号 | ADR-19 |
| 主题 | B4 结构体 ctor 解析 / B5 ctor 默认值 / C 两处文法收紧（先裁决后修） |
| 任务 | t20（t17 归因表 B4/B5/C） |
| 依赖条款 | S-16 struct 声明、S-42 模式、S-34 初等项、D-9 信息保留、ADR-1 §3.2、ADR-7、ADR-12 §三、ADR-17 泛型实例化 |
| 上游 | t4 解析层、t8 类型推导、t13 语义修复、t18 调用点泛型实例化 |
| 下游 | t17 后续归因项（越界残留归其所属族） |

## §一 B4：结构体名即位置式构造入口

**根因**：Collector 的 StructDecl 分支只把结构体名登记入**类型**分表（declareGlobal(STRUCT)），
`User("alice", 30)` 这类位置式调用经 NameResolver.resolveValue 找 ctor/fun 分表 → E-UNBOUND-NAME。
枚举 ctor 有 registerEnum 的 declareCtor 通道，结构体名没有——解析层形态正确（解析器按
大小写+括号把位置式调用产出 CallExpr，嵌套位的 Bill("rice", 10) 同形态），缺口在**符号登记与
调用核对的语义层**。

**裁决与修法**（要点：与枚举 ctor 同通道，不新造节点族）：
1. Collector 的 StructDecl 分支：结构体名成功登记类型表后，复用 declareCtor 把
   （声明名, 空字段判定 nullary, enumName=自身名）登记入 ctor 分表；与既有 ctor 撞名落
   H-AMBIGUOUS-CTOR 提示（同枚举跨表口径）。
2. callExpr 的 CTOR 分支：enumDecls[ci.enumName] 取不到时回落 structDecls[ci.enumName]，
   形参表取结构体 fields 的字段类型，theory 同枚举走 enterTypeParams（泛型实例化经
   instCall 回代，与 t18 同口径）。
3. 不动解析层：样本里「嵌套于 Cons(...) 内的 ctor 调用失败」与「@mut 结构体 ctor 名未注册」
   同根（名字登记缺口），非解析形态问题。

**旧实现点查**：旧 Parser 把 `User(...)` 位置式形态解析为 CallExpr，旧 Checker 经
syms.structs 命中 checkStructCall（CallCheck.kt:779 起）——命名/位置式双形态共用结构体符号。

**对账**：特性巡礼.subl User ×2（110:14、116:20 Cell ×3）+ 日常-账单统计.subl Bill ×4（44:22）
→ 修复后 0；B5 路径另清 结构体与枚举.subl:31、特性巡礼.subl:111。B4 预期 5 样本中本族错误
全部清零，余者归其他族（§五）。

## §二 B5：ctor 默认值参与 arity/字段核对（required 共享计算）

**要求与旧口径**：核对按「有默认值的尾字段可省」；旧实现两处口径点查：
- 命名构造（StructCtorExpr）：CallCheck.kt:50 `fp.name !in seen && fp.default == null` → 缺字段
  且无默认值才报 E-FIELD-MISSING（逐字段）。
- 位置式（CallExpr → ctor）：checkStructCall O3（CallCheck.kt:793-797）：实参多于字段报
  E-TYPE-MISMATCH；少于字段时，被跳过的尾部字段**任一**无默认值报「缺尾部字段且无默认值」。

**裁决**：位置式与命名构造复用**同一套 required 语义**，不引入新参数族：
1. infer.callCtor 增 fieldDefaults 形参（默认空表=枚举 ctor 精确 arity——枚举 ctor 字段
   无默认值，旧 checkCtorCall 口径）；位置式 required = 字段数 - 尾部连续有默认值的后缀长
   （前缀供给规则，与旧 drop(any !default) 等价）。
2. infer.structCtor 增 fieldDefaults（与字段表同序），仅「未赋值且无默认值」的字段落
   E-FIELD-MISSING；消息沿用旧文本。

**正反例验证**（StructCtorTest）：正例 User("alice")（尾部默认 age 省略）、User()（全默认）、
Pair(1, "s") 泛型回代、命名 User(name = "alice") 均无错；反例 struct User(name: Str, age: Nat = 0)
位置式 User() 报「缺尾部字段且无默认值」、命名 User(age = 1) 报「缺字段 name 且无默认值」。
## §C1 裁决：⊤/⊥ 出现在程序位（值与字面量.subl:33-34）

> 【2026-11-09 对账标注（t91）】本 §C1 的「⊤/⊥ 是合法初等项、程序位无除外条款」结论已被 ADR-50-Q12（裁决 1）推翻：⊤/⊥ 转命题专用，值位落 E-PROP-POS。

**文法条款**：S-34【初等项】 ::= … | "⊤" | "⊥"——⊤/⊥ 是合法初等项，程序位无除外条款。

**旧实现支持**：旧 Checker.kt:1006 `TopExpr, BotExpr, ReturnSym -> namedT("Bool")`——
程序位类型化 Bool；Prop.kt:30-31 命题位映射 PTop/PBot。样本注释亦写明「布尔：⊤ / ⊥ 是 Bool
仅有的两个字面量」。

**结论：文法合法 → 真缺陷，按文法修**（不放宽文法、不判样本越界）。修法为**程序面节点补绘**
（同 t7 HirBinExpr/HirUniExpr 先例）：新增 HirExpr 节点 HirTop/HirBot（携带 Bool 类型，
五件套齐备），Elaborator 程序位落该节点（etype 通道同字面量族），命题位仍归 PropNormalizer →
HirPTop/HirPBot（两分家不变）；HirJson 发射分支同步（穷尽 when 强制）。ADR-1 §3.2「程序面
不再有 ⊤/⊥」的表述随本裁决修正（hir/Expr.kt 的 KDoc 已改）。

**对账**：值与字面量.subl E-PROP-POS ×2 → 0；\$ 与量词等等其他命题专属语法的 E-PROP-POS
哨兵不变（SemanticTest.propOnlySyntaxInProgramPosition 同步改判：⊤ 落字面量、量词仍报）。

## §C2 裁决：无 is 的构造子解构（日常-容器范型.subl:15、类型类.subl:14-17、日常-账单统计.subl:37）

**文法条款**：S-42【名字模式】 ::= … | 【枚举路径】 ("(" ")")? | 【名字】 "(" ")"
（无参构造子值比较 A() / E::A()）；§2.12 注：**「解构必须显式 is：is Some(x)、is E::A(_)；
无 is 的括号模式仅允许空参 A()（非空括号在 parsePattern 直接报错并引导加 is）」**。
D-15/D-16 为模板 when 与 :: 折叠规则，与本形态正交。

**旧实现支持**：旧 Parser.kt:1644-1648——空参括号模式放行，非空括号模式报错引导加 is。

**结论分两支**：
- **空参 A() / E::A() 文法合法 → 修代码**：parsePattern 的「无 is 括号模式」分支原为一律拒绝
  （空参一并误拒），修正为仅「非空括号」落 E-SYNTAX + ErrorPattern；空参形态产出
  PatCtor(名, 空表)，与 is 模式实参位形态同通道（语义层裁定既有路径不变）。
- **非空括号 A(x) 文法不合法 → 判样本越界不改代码**：反例测试锁定（若写非空括号仍报
  「类型解构须显式 is」）。实际样本全部为空参形态（Excellent()/Good()/Pass()/Fail()/Nil()），
  修复后即合法。

**对账**：日常-容器范型.subl 2→0；类型类.subl 该族 8 条 E-SYNTAX 清零（14→6，余 6 归
类型类族）；日常-账单统计.subl 7→1。

## §五 范围边界（t20 不修，归其所属族）

- 类型类方法分派域（t8/t9 字典/方法糖）：类型类.subl self/show/name/printMyself ×6、
  特性巡礼.subl:78 impl 方法体内字段名（隐式 self 域）。
- 类型推导 V1 边界（ADR-12 §三延后清单）：特性巡礼.subl:27 when 返回值公共类型、
  日常-账单统计.subl:29 lambda 形参双向反推（决策 55/57）、core 谱系同族。
- 证明域（t9/t30）：手动证明.subl 的 by 断言等。
- HKT/跨文件/运行时域（t18/t19 范围）：Functor/Foldable 未知类型、map/fold 等。

## §六 影响面与验证

- 无回归：:frontend:test --rerun-tasks **305 tests 0 failures**（ParserTest 65 含 C2 新增 2、
  StructCtorTest 新增 8、SemanticTest 1 处按新口径改判、其余族不变）。
- e2e 全量扫描（compileFrontend 单跑 68 样本）：B4/B5/C1/C2 全族清零；结构性校验
  （E2ERegressionTest）不变（新节点走通用 kind/nid/pos/notes 形态）。
- 协作：修复期间 semantics 在改 semantic/*（多次瞬时编译失败后自愈），本任务的
  Collector/Elaborator/TypeInfer 改动为 t20 任务书指派范围；SemanticTest 的
  propOnlySyntaxInProgramPosition 按 C1 裁决改判（旧断言错读 ADR-1 §3.2 表）。

