# ADR-16：上下文组与量词邻接口径（t15 修复裁决）

| 字段 | 值 |
|---|---|
| 编号 | ADR-16 |
| 主题 | 上下文组（返回类型后的 `<…>`）内量词表达式的 angle 语境透传 |
| 任务 | t15（R6/F2 量词形态族修复） |
| 依赖条款 | S-1 §2、《形式化文法》§2.2（上下文组）、D-1/D-2（邻接消歧）、ADR-7（语法分析与容错模型） |
| 上游 | t4 语法分析器（parseExpr/parseAngleList/parseQuant） |
| 下游 | t12/t17 e2e 回归（zfc/derived/peano 样本族） |

## 1. 背景与症状（R6/F2）

t12 e2e 验收归因 F2（队长探针实录收窄为 R6）：定理声明的上下文组以量词开头时解析失败——

- 探针 h：`fun `set pairing`[a, b]: Null <∃c . ∀x . ((x ∈ c) ↔ ((x = a) ∨ (x = b)))>`
  → `[错误] E-SYNTAX: 期望表达式`（锚在 EOF，组未消费完毕）。
- 探针 f（同形但组以 `(` 开头、量词在括号内）解析正常——量词**直接**位于 `<` 之后才触发。

实测修复前 e2e 语料的量词族 E-SYNTAX 分布（parseSourceWithNotes 单遍口径）：

| 样本 | E-SYNTAX 条数 | 失败签名（行位） |
|---|---|---|
| core/zfc/zfc.subl | 6 | 66/67 · 75/76 · 86/87 |
| core/zfc/derived.subl | 14 | 33/34 · 52/53 · 57/58 · 62/63 · 88/89 · 94/95（各 ×2）+ 124:50 尾部 ×2 |
| core/math/peano.subl | 4 | 43/44 · 61/62 |
| 合计 | **24** | 11 个声明 × 2 条级联 + derived:124 尾部 2 条 |

（任务书引 t12 的「34 处」为按行位/声明位口径的计法；本裁决以**诊断条数** 24 为准，覆盖的行位与 F2/R6 点名清单完全一致。）

## 2. 根因

parseExpr 的 angle 语境（D-2：angle 语境中 `>` 默认是组闭合、带空格且后随操作数时才是比较中缀）
在向初等项的传递链上断裂：

```
parseExpr(angle=true) → parseUnary(angle) → parsePostfix(angle) → parsePrimary()  ← 无 angle 参数
                                                           → parseQuant(angle = false)  ← 硬编码
```

parsePrimary 不收 angle、量词分支硬编码 `angle = false`，于是量词体（QuantExpr.body）回到
parseExpr 时丢失 angle 语境：闭合 `>` 被当作比较中缀 consumed，右操作数 parsing 继续吞掉
**下一声明的 `@theorem`**（parsePrimary 的 `@` 分支后随非 `var` → 报「期望 var 变量声明」）
直至 EOF；parseAngleList 的 expect(GT) 落在 `fun`/EOF → 「期望>，实际'fun'」。每条破损声明
恰好产出这两条级联诊断——与上表的 ×2 分布一一对应。

## 3. 裁决

1. **angle 语境逐层透传**：`parsePrimary(angle: Boolean = false)`，由 parsePostfix 以
   `parsePrimary(angle)` 转发；量词两分支（unicode 量词 token 与 S-3 ASCII 别名）改为
   `parseQuant(…, angle = angle)`。量词体继承外层 angle 语境。
2. **括号重置口径（不变，行为此前正确）**：括号表达式分支内 parseExpr 恒 angle=false——
   括号内的 `>` 永远不是组闭合；组以 `(` 开头时（探针 f），闭合 `>` 由组顶层的 angle 语境
   消费。此口径为本修复无需改动的既有部分，且被 f 形态用例锁定。
3. **组内实例化分层（D-1 与 D-2 正交）**：`<∀x . valid<x>>` 中量词体内的紧贴 `<` 走后缀
   实例化（D-1），组闭合的第二个 `>` 由组顶层 break（D-2）——新增双重尖括号用例锁定。
4. **量词变量表的句点收尾不受影响**：`∃c . ∀x . …` 的嵌套量词体仍在同一 parseQuant 通道
   内递归（parseExpr → parsePrimary → parseQuant），ASCII 别名（forall/exists）同路径。

修复范围：Parser.kt 4 行（签名 + 3 处转发），无新增节点/新码族；诊断码族不变（族内错误
本就是级联伪影，修复后自然消失，不需要新的 E-SYNTAX 码）。

## 4. 后果与回归锁定

- 修复后三个样本 **E-SYNTAX 全部为 0**（totalDiag 亦为 0——量词族是该三样本唯一的解析错因）；
  探针 f/g/h 与 ∃! 形态全部诊断空。
- 回归固化为 ParserTest 5 例（上下文组量词：量词开头的后置上下文组 / 全称量词组 / 存在唯一
  形态 / 括号前缀与尾随蕴含 / 组内实例化与双重尖括号）。
- 无回归：`:frontend:test --rerun-tasks` 299 tests 0 failures（ParserTest 63 含 5 新例；
  e2e、semantic、json、cli、lexer 全绿）。
- 与 P-8（类型系统改向，ADR-8）正交：本修复只涉及解析层的语境传递，不触碰 impl/类型类体系。
- 语义侧既有的 R3/R4（量词绑定变量作用域、类型参数作命题项）不受本修复影响——解析层产出
  正确的 QuantExpr 后，作用域问题仍在 t13（semantics）范围内。
