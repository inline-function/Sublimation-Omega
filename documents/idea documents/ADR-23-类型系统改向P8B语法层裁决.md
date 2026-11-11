# ADR-23 类型系统改向 P-8/B：语法层裁决（扩展函数产生式 / impl 废除 / class 体收紧 / for 释放）

状态：🟢 已拍板（t28 交付验收通过）
日期：2026-11-06
依据：类型系统卷-TS-v1（TS-2 / TS-6 / TS-9 / TS-30；§8 待决问题 Q5、Q6；§9 诊断码表）、ADR-8、ADR-21

## 一、背景

P-8 类型系统改向（卷 TS-v1 立卷）：impl 声明废除，改以「接收者前缀的扩展函数」表达类型类成员的实现；
class 体只允许规范（签名），实现一律外移。t28 为 P-8/B 阶段——**语法层先行**：
parser 产出新形态、旧形态落诊断错误声明并保持容错链不断，语义分派（P-8/D，t30）与节点删除
（P-8/C，t29）不在本阶段。分层理由见 2026-11-06 日志 P-8 立项 DAG（每步保持树可编译）。

## 二、决策

### 2.1 扩展函数产生式（TS-9）

产生式（BNF 草稿，形式化文法.md 尚未迁移，草稿另存于 2026-11-06 日志 t28 条目）：

```
FunDecl      ::= 'fun' ExtPrefix? Name TypeParams? Params ReturnType? FunBody?
ExtPrefix    ::= TypeExpr '.'            —— 接收者类型表达式（具名/应用/限定/嵌套），与函数名以单个 '.' 分隔
TypeExpr     ::= Name TypeArgs? ('.' Name TypeArgs?)*
```

**邻接判据（裁定 D-1~D-3，确定性、零回退）**：fun 后前瞻扫描只读的「名 [类型实参] . 名 [类型实参] . …」链——

| 判据 | 输入形态 | 判决 | 样例 |
| --- | --- | --- | --- |
| D-1 | 链长 = 1（无点号） | 自由函数，receiver = null | fun T(a: Nat): Nat（函数名就叫 T，合法） |
| D-2 | 链长 >= 2（>=1 点号） | 扩展函数：**末段为函数名**（其方括号为理论参数），其余段为 Receiver | fun T.f(a): R -> name=f, receiver=T；fun List[a].map(f: a): List[a] -> name=map, receiver=List[a] |
| D-3 | 段方括号归属 | 接收者段的方括号=类型实参；末段方括号=函数理论参数 | fun List[a].flatten[b](x: b): b -> theory=[b], receiver=List[a] |

实现要点（Parser.kt）：
- scanDottedChain() **只读前瞻**（不移动游标、不发诊断）；段间方括号按配平跳过。
- parseReceiver(segCount) 按段解析：单段 -> NamedType，多段 -> QualifiedType（module=除末段外路径）。
- **不得**用 parseType() 投机接收者：parseNamedType 的点号循环会把
  List[A].map 吞成 List.map[A]（限定类型 map 带 args）——这正是本判据要排除的歧义。
- self 绑定词汇层无改动（self 本就是普通标识，非关键字；绑定规则归 TS-10/语义层）。

### 2.2 impl 废除（TS-2 / TS-30 / §8-Q6）

- impl **保留为关键字**（唯一入口 → 干净落诊）；parseImpl 改为：**立即报 E-IMPL-REMOVED +
  迁移模板并产 ErrorDecl**，成员体按配平整段跳过，无体头按配平深度同步到下一入口。
- 消息文本（验收口径，逐字对账）：
  impl 已废除（P-8 类型系统改向）：请改写为扩展函数——「impl T { fun f(a: A): R }」/
  「impl C for T { … }」→「fun T.f(a: A): R」；「by 字段」委托废除，请手工转发
- **不设兼容期**（Q6：新工程无外部用户）；旧 S-5 脱糖（无类型类 impl -> self 首参自由函数、
  kind 占位提升）随通道撤除——等价表达由扩展函数产生式承担。
- 同步删除的死代码：collectImplPlaceholders、collectShorthandVars、parseFile 的 S-5 脱糖分支、
  classNames 字段（HKT 简写判定专用，随 parseImpl 简写路径消失）。
- 保留 Kind.IMPL 与 parseDecl 的 IMPL 路由（错误出口）。

### 2.3 class 体收紧（TS-6）

class 体只允许：
1. **函数规范**：fun 名 [理论参数] (形参表): 返回类型——**无体**（=/块体即报
   「类型类函数规范不得含实现体」）、**无默认值**（报「类型类函数规范不得含默认值」）；
2. **常量规范**：val 名 : 类型（新 AST 节点 ClassConst；= 即报「常量规范不得含实现」）；
3. 继承声明（class Sub : Base，header parents，既有支持不变）。

禁止并落 E- 诊断：字段声明（var/非 val 标识，报「类型类体内禁止字段声明」）、
带体函数、嵌套类型声明（class/struct/enum，报「类型类体内禁止嵌套类型声明」）。

AST 增量（ClassDecl.constants: List<ClassConst> = emptyList() 末位默认，
既有构造点零破坏）；HIR/JSON 增量（HirClassDecl.constants、HirClassConst、
fieldNodes("constants", ...)）为**保 D-9 不丢信息的最小被动映射**——常量的语义角色
（满足判定/默认值供给，TS-17）归 t30，schema 正式升版归 t29（现以 HirClassConst 形态出现）。

### 2.4 for 释放（§8-Q5）

Lexer 关键字表删除 for->FOR 映射；for 从此按普通 IDENT 解析（可作函数名/参数名/变量名）。
Kind.FOR 保留于 Token 种类枚举（零成本，删 TOKEN 枚举属 t29 节点清理）；
唯一引用点（parseImpl 的 for 头）已随 2.2 消失。

## 三、范围边界（不做的事项与原因）

| 事项 | 归属 | 原因 |
| --- | --- | --- |
| ImplDecl（AST）/ HirImplDecl（HIR）/ Kind.FOR 删除 + JSON schema v2 | t29（P-8/C） | 分层保持树可编译；本阶段 parser 已不再产出 impl |
| 接收者语义分派、扩展函数桶、方法糖改道、结构化满足、约束检查 | t30（P-8/D） | 语义层任务；t28 只填 FunDecl.receiver |
| n.show() 方法糖 callee.symbol 与 D-METHOD-SUGAR 留痕 | t30 | 留痕原挂在 impl 方法表通道上，通道已撤；解析层平稳落地先行（SemanticTest methodSugarDesugars 已按此适配并注明） |
| TS-6 常量规范的 HIR 满足判定参与 | 后继卷 | TS-17：V1 常量规范不参与满足判定 |
| e2e impl 样本迁移（7 个） | t31 | 依赖 t30 语义落地；E2ERegressionTest 判决口径为「只结构性违规判负」，当前不阻断 |
| self 绑定校验（E-DUP-NAME 遮蔽等） | t30 | TS-10 语义层规则 |
| 形式化文法.md 迁移 | 文档任务 | 文件尚未建立；本裁决的 BNF 草稿 append-only 存于 2026-11-06 日志 t28 条目 |

## 四、验收证据（2026-11-06）

- :frontend:compileKotlin EXIT=0（main 树可编译）。
- :frontend:test --rerun-tasks **342 tests / 0 failures / 0 errors**（17 类）；
  台账：LexerTest 39（38->39，新增 for 释放专测；硬关键字 16->15 改名对账）、
  ParserTest 77（67->77，新增 TS-9 邻接判据六专测 + TS-6 收紧四专测；3 个 impl 旧测改写为
  E-IMPL-REMOVED 容错口径）、SemanticTest 22（2 个方法糖测试由 impl 形态改写为自由函数 + 方法糖
  TS-12(3) 形态，语义分派缺位已按 §三 注明）、其余 14 类与 t27 基线一致。
  基线对账：t27 = 331 -> t28 = 342（+11：Lexer +1、Parser +10；改写不增减）。
- E2ERegressionTest 2/2 绿（判决口径：结构性违规为 0；impl 样本现产 E-IMPL-REMOVED，ok=false
  不判负，归 t31 迁移）。
- TS 卷条目对账：TS-9（§2.1 产生式+判据表+正反例单测）、TS-2/TS-30/Q6（§2.2 报错文本逐字落诊
  且有单测对账）、TS-6（§2.3 正反例单测）、Q5（§2.4 词法单测）。

## 五、备选方案与取舍

- **接收者用 parseType() 投机再回退**：被 parseNamedType 的点号循环证伪（List[A].map 吞成
  List.map[A]），且回退破坏「零消费/零诊断」容错口径 —— 否决。
- **impl 报错但保留软解析兼容**：与 Q6（不设兼容期）冲突 —— 否决。
- **常量规范先不入 HIR**：违反 D-9（AST->HIR 不丢信息）—— 否决，改为最小被动映射（§2.3）。
- **for 连 Token 种类一起删**：扩大本阶段爆炸半径（LexerTest/Parser 等多处引用 Kind）—— 否决，
  归 t29 统一清理。
