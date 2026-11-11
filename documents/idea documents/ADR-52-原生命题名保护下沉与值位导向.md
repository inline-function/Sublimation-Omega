# ADR-52——原生命题名保护下沉到所有绑定位 + 值位导向

**日期**：2026-11-09（t86：qa 复核 t77 交来的两条 finding F6 / F2 收口）
**关联**：ADR-48（可编译提示纪律）、ADR-49（原生命题注册表与命题位分派）、ADR-50（Q12）

## 一、背景（qa 测绘）

原生命题名（§5 注册表：`∧ ∨ → ↔ ¬`）此前只在**声明位**保护：
`fun ∧(...)` / `var ∧ = ...` 落 `E-NATIVE-REDECL`。但它们是**编译器内建符号**而非普通标识符，
qa 实测以下位置都能占用它们（保护名单只挡 fun/var 声明）：

| 位置 | 写法 | 原状 |
|---|---|---|
| 形参 | `fun f(∧: Int)` | **允许** |
| 字段 | `struct S(∧: Int)` | **允许** |
| lambda 形参 / 模式绑定 / 解构分量 | 类似 | **允许** |

危害：`fun f(∧: Bool): Null <∧ ∧ p>` 会让「一个 `∧` 是变量、另一个是合取」的
读法成立，诊断却只有 `H-SHADOW`（遮蔽合法级）。成本为零（全仓 0 处这样写）。

## 二、裁决（队长已裁）

1. 保护判定从「声明位」**下沉到「引入名字的位」**：注册表内名字不容于任何引入名字的位置。
2. **复用 `E-NATIVE-REDECL`**（语义同为「试图占用原生命题名」），**不新增诊断码**（表已够长）。
3. 文案按位置分化：
   - 声明位（fun / var / ctor / 扩展函数名）：「……（N 元），**不可重定义**」（既有文案逐字保持）
   - 其余绑定位（形参 / 字段 / 局部 / lambda / 模式 / when / 解构）：「……（N 元），**不可用作变量名**」
4. **只拦不阻断**（半成品树原则：登记照旧，下游可消费已成功部分）。

## 三、实现（声明通道统一拦）

保护点收敛进 `SymbolTable` 的**声明通道**（而非散在 Elaborator 各分支）——单一守卫函数
`guardNativeName(name, what, origin)`，由 `Semantic.compile` 注入 `nativeReclSink` 落诊断：

| 通道 | 覆盖的写法 | what |
|---|---|---|
| `declareFun` | `fun ∧(...)` / 局部 fun | 不可重定义 |
| `declareScriptTopVar` / `declareLocal` | `var ∧ = …`（顶层/块内）| 不可重定义 |
| `declareLocal`（默认参数）| 形参、字段、lambda 形参、模式绑定、when 绑定、解构分量、量词绑定 | 不可用作变量名 |
| `declareCtor` / `declareGlobal` | `struct ∧` / `enum ∧` / `alias ∧` | 不可重定义 |
| `declareExtFun` / `declareLocalExtFun` | `fun Int.∧(…)` | 不可重定义 |

Elaborator 原两处声明位守卫（funDecl / VarStmt）**移除**——否则同名会重复报错；其文案由中央守卫逐字承担。
`declareLocalFun`（局部自由函数名）以 `what = 不可重定义` 透传，保持与 fun 声明一致。

## 四、F2：值位出现原生命题名的导向

qa 实测三个盲区都落 `E-UNBOUND-NAME` 但不告诉用户该怎么办：

| 写法 | 目标 |
|---|---|
| `val x = ∧` / `val x = ∧<p, q>` | `E-UNBOUND-NAME` + 「`∧` 是命题连接词，只能出现在命题位；值位的布尔与写作 `&`」 |
| `val x = ∨` | 同上，值位的布尔或写作 `|` |
| `val x = →` / `val x = ↔` | 同上，仅定位（蕴含/等价无值位写法） |
| `val x = ∧(p, q)` | **保持**（值位调用形态回落 BinExpr——`infer.isBuiltinOp` 与 `propCtors` 条件交集恰为 `∧ ∨ → ↔`；实参类型由类型检查负责） |

落点：`NameResolver.resolveValue` 追加 `nativeHint`（提示串拼在 `boolHint` 之后）。`¬p` 的否定提示
（推荐 `!p`）与 `p∧q` 粘连提示均保持原位；提示推荐写法本身可编译（ADR-48 §八）。

## 五、验证

- **绑定保护**：形参/字段/lambda/模式/解构/块内var 全部落 `E-NATIVE-REDECL` 且文案正确；
  命题位 `fun f[p, q]: Null <p ∧ q>` **零错误**（保护不影响命题位）。
- **全量 `:frontend:test --rerun-tasks`：804 / 0**（790 → 804：新增 `T86NativeNameProtectionTest` 14 例；
  P4 #7/#7b、P5Batch2 #8 等既有断言逐字保持）。e2e **65/68**（三冻结红逐字不变）。
- **68 样本 JSON**：vs `base-t74`（t80/t82 源码迁移**前**的基线）SAME=55 / DIFF=13——13 处与 t84 终态
  发射的归因集**逐处一致**（10 纯 t80 字面量迁移 + list 的 t80 `&&→&` + logic 的 t82 优先级 +
  值与字面量的 t80 `⊤⊥→True/False`）；**vs 任务起点 `out/t84` 发射：68 / 68 零差异**——
  t86 只加诊断路径，未改动任何正确程序的 HIR（验收 #8 的零差异口径）。

## 六、纪律

- 复用既有码、不新增诊断码；文案分化而非码分化。
- 中央拦 + sink 注入（同 `shadowHintSink` 的既有模式），避免守卫散落在 Elaborator 各分支。
- 探针矩阵留在 `frontend/probe-mut/cases-t86/`（qa 可复测）。