# 冻结红重冻结台账：t66（ST-10.2 收窄）对 core/collection/list.subl 的基线变更

> 日期：2026-11-08 · 任务：t66（ADR-43）· 变更性质：**规格直接强制**（非顺手整齐）
> 相关：documents/log/2026-11-08.md（t66 节）、ADR-43 §五「为什么这次基线可以动」

## 一、变更结论

| 项 | BEFORE（t65 末态） | AFTER（t66） |
|---|---|---|
| e2e ok 计数 | 65/68 | **65/68（不变）** |
| list.subl | red（**15 项**，码集 5 码）| red（**13 项**，码集 5+1 码）|
| 手动证明.subl | red · 2 项 · D-BTS,E-CANNOT-PROVE | **逐字不变** |
| 模块系统/主.subl | red · 4 项 · E-UNBOUND-NAME | **逐字不变** |
| 结构性违规 | 0 | 0 |

其余 64 个 true 样本的码集/项数**逐字不变**（t66-before-samples.txt vs t66-after-samples.txt：68 行 diff 仅 list.subl 一行）。

## 二、list.subl 15 项 → 13 项逐条对应

### 不变（11 项）

| # | 码 | 位置 | 诊断 |
|---|---|---|---|
| 1 | H-SHADOW | 58:5 同名遮蔽 | `head` 遮蔽全局同名字段 |
| 2-5 | D-TYPE-INFERRED | 41:19 / 131:26 / 138:26 / 147:40 | `var rest`、`var r`×3 推导为 `Nil | Cons[T]` |
| 6-8 | E-UNBOUND-NAME | 81:40 / 221:22 / 227:19 | `getOrElse`/`add`/`put` 未定义 |
| 9 | W-NON-EXHAUSTIVE | 226:23 | when 非穷尽 |

### 消失（4 项，均为 E-TYPE-MISMATCH「实参 2 Int 与形参 Nat 不符」）

| # | 位置 | 源 | 消失原因（规格直接后果） |
|---|---|---|---|
| 10 | 71:53 | `elementAt(t, i - 1)` | else 支 Γ：`i >= 0`（类型事实，ADR-42）+ `¬(i == 0)`（分支事实）⇒ prover 证出 `i >= 1` ⇒ `i - 1` 按 ST-10.2 收窄为 Nat ⇒ 实参 2 适配通过 |
| 11 | 131:34 | `take(t, n - 1)` | 同上（`n >= 0` + `¬(n == 0)` ⊢ `n >= 1`）——**任务书典型场景本尊**：「这是本规则存在的理由」 |
| 12 | 138:34 | `drop(t, n - 1)` | 同上 |
| 13 | 148:20 | `slice(t, from - 1, to - 1)` 实参 2 | `if (from == 0)` 的 else 支：`from >= 0` + `¬(from == 0)` ⊢ `from >= 1` ⇒ 收窄 |

### 保留（2 项，E-TYPE-MISMATCH「实参 3 Int 与形参 Nat 不符」——`to - 1` 位）

| # | 位置 | 源 | 证不出的原因 |
|---|---|---|---|
| 14 | 147:48 | `slice(t, 0, to - 1)` 实参 3 | Γ 无 `to >= 1` 来源：外层 else 支分支事实只有 `¬(to <= from)`；ArithChannel（±1 差分图 + consumeNe）**不处理 ≤ 的否定**（无从导出 `to - from >= 1`）；类型事实只有 `to >= 0`。故 `Γ ⊢ to >= 1` 证不出 ⇒ 收窄失败 ⇒ 保持 Int + 实参 3 失配（与 t65 逐字相同，只多了下方提示）|
| 15 | 148:20 | `slice(t, from - 1, to - 1)` 实参 3 | 同上 |

### 新增（2 项，D-NARROW-SCOPE supplement——失败可操作提示）

| # | 位置 | 源 | 内容 |
|---|---|---|---|
| 新1 | 147:63 | `to - 1` | 「无法证明 `to >= 1`，减法 … 保持 Int（ST-10.2 收窄未生效）。当前 Γ：`from >= 0`；`to >= 0`；`¬(to <= from)`；`==<from, 0>`。可先断言 `to >= 1`（axiom …，或以 if 守卫引入分支事实）…」|
| 新2 | 148:42 | `to - 1` | 同上（Γ 略异：`¬(from == 0)`）|

净账：15 − 4（收窄消除）+ 2（失败提示）= 13；ok 计数不变（list.subl 仍为 red——其未解红 E-UNBOUND-NAME×3 等与减法无关）。

## 三、为什么允许此次基线变更（判据）

判据：**基线改动必须由规格变更直接强制，不得由「顺手整齐」驱动。**

- 判据成立性：ST-10.2 落地后，`elementAt/take/drop/slice` 的 `n - 1`/`from - 1` 在 else 支非负性**真的可证**——4 条 E-TYPE-MISMATCH **本该消失**；若为维持「15 项」保留它们，等于让已修好的证据维持错误状态（伪造基线）。
- 反例对照：此前 t63 讨论的「改 take/drop/slice 签名为 `Nat → Int`」**不**满足本判据（签名改型不是收窄强制的），已被驳回且维持驳回。
- 同时覆盖：队长 2026-11-08 裁决（A 案）原文确认：「改动必须由规格变更直接强制，不得由顺手整齐驱动；这里的改动正是规格强制的」。

## 四、证据文件

- `t66-before-samples.txt` / `t66-after-samples.txt`（68 行逐样本快照，diff = list.subl 一行）
- `t66-list-before.txt` / `t66-list-after.txt`（list.subl 诊断明细，含 position）
- ADR-43（设计 + 验收 + 门槛）；NarrowSubtractionTest（19 例，含 S2 take 实参位收窄）