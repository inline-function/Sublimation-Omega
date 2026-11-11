# ADR-51 not 别名删除 + && / || 退役（表达式层只用 & | !）

**日期**：2026-11-09（t78，用户裁决 Q15 / Q21）
**状态**：第 1 半已落（警告态）；第 2 半（拒绝态）待 t80 样本迁移后收口

## 一、决策

用户 Q15: not 作为 ¬ 的隐藏别名**删除**——回落为普通标识符（未声明 ⇒ 既有 E-UNBOUND-NAME),
不再走 parseUnary 的别名分支。

用户 Q21: **表达式层一律 & | !;命题层专用 ∧ ∨ → ↔ ¬**。 && / || 退役，分两半：

| 阶段 | && / || 行为 | 诊码 |
|---|---|---|
| 第 1 半（本 ADR 已落） | **警告态**——仍编译通过，落 W-OP-RETIRED 劝写替代算符 | W-OP-RETIRED(W 族） |
| 第 2 半（t80 迁移 list.subl:103/107 两处后） | **拒绝**（Lexer 不再产 LAND/LOR 或解析层落 E) | 升级为拒绝 |

**为何分两半**:t80 负责 list.subl:103/107 两处样本迁移；在那之前整体拒绝会让全量红
(S-3 纪律：交付态必须绿）。任务书明示「开关式地只在样本迁移后生效，中间状态可短暂不全绿，
但不许把警告态当作最终完成」。

## 二、诊断码 W-OP-RETIRED（新增，W 族）

- 语义：**警告级，不阻编译**；位置指向算符 token;文案可编译（ADR-48 §八）+ 标识符反引号（ADR-46)。
- 模板：<name>算符 <written> 已退役：表达式层一律用 <want>（p <want> q 可直接编译通过）；命题层专用 ∧ ∨。
  written/want = &&→&、||→|。
- **Lexer 文本归一注意**:Lexer 把 && / || 归一为 ∧ / ∨(Kind.LAND/LOR 的 text)。若直接用 t.text,
  提示会写成用户没写的 ∧ ⇒ **须按 Kind 还原用户实际写法**。Unicode ∧ / ∨ 字面走 SYMBOL(D-6)
  通道，与本警告无关——不误伤命题层。
- 类型：Severity.WARN;已登记于诊断码对照审计。

## 三、实现（parser/Parser.kt)

| 位置 | 改动 |
|---|---|
| parseUnary 的 not 分支 | **整段删除**（Parser 内 not 前缀否定 0 处）；邻近注释的 not p 引用一并清理 |
| parseBinary 的 binops 分支 | Kind.LAND/LOR 落 W-OP-RETIRED 后**按原路径继续产 BinExpr**（警告态不改解析结果） |

**删除 not 后的诊断路径**:IDENT not → parsePostfix NameRef → 语义层 NameResolver 落既有
E-UNBOUND-NAME（未定义的名称 not）——任务验收 #1 即此。E-ARITY / E-NATIVE-REDECL 文案复查不含
not(t70 已改）。

## 四、验收（OpRetirementTest 6/0 + CLI 探针 5）

| # | 源 | BEFORE | AFTER |
|---|---|---|---|
| 1 | not p | 通过（别名生效）| **E-UNBOUND-NAME**（回落普通标识符）;AST 为 NameRef(not) 而非 UniExpr ¬ |
| 2 | p && q | 通过 | **W-OP-RETIRED**（含 && 与 &)，仍通过 |
| 3 | p || q | 通过 | **W-OP-RETIRED**（含 || 与 |)，仍通过 |
| 4 | p & q / p | q | 通过 | 通过、无 W |
| 5 | !p | 通过 | 通过、无 W |
| 6 | 命题层 p ∧ q | — | 不受影响（不落 W-OP-RETIRED) |

全量门槛 ≥753 只增不减、e2e 65/68、68 样本 HIR JSON 逐字零 diff(W 警告不进 compile JSON 的
diagnostics 段——机制同 t76:提示与警告均不进 JSON,以 jsoncmp/old 基线验证）。

## 五、与 t80 / t82 的协调

- **t80**(infra,pending):list.subl:103/107 的 acc || p(x) / acc && p(x) 迁移为 | / &。迁移前
  && / || 为警告态——这两处样本会**新增 2 条 W 警告**(e2e 的 ok 判定不含 W,故 65/68 不变；全量亦不红）。
  **t80 完成后由 t78 收口 tail 升级为拒绝**（第 2 半）。
- **t82**(优先级落地，依赖本任务）:logic.subl:105/107 两条 rangeBound 定理的解析树将校正（队长影响面
  预判 documents/t82-影响面预判.md)。本 ADR 的 W-OP-RETIRED 与 H-ARITH-PREC 同类——**只警告不改变解析**，
  与 t82 的优先级改造正交。

## 六、过程记录

- 字符串模板坑：$name算符 模板歧义 ⇒ ${name}算符；import 替换误删 Severity 导入导致编译失败、
  测试 XML 误读为旧结果——**改测试 import 后必须确认 BUILD SUCCESSFUL 再读 XML**。
- E-UNBOUND-NAME 属语义层诊断：parse 层 r.diag 不含它；测试需走 compile(file, notes) 通道合并。
- 验收 #1 的「AST 是 NameRef 而非 UniExpr ¬」是**解析层**核心证据（别名真删了）,E-UNBOUND-NAME
  是回落后的语义层后果——两者都要，分别落在不同管线段。
