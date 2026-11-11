# ADR-44 符号中缀（`1 ⊗ 2`）降为函数调用

**日期**：2026-11-08（P-1 修复，t67）
**状态**：已落（用户裁决 R2「中缀调用适用于所有二元操作符，不需 infix 关键词」）

## 一、决策

用户声明的符号函数支持中缀调用——语义层把 D-6 符号中缀产的 `BinExpr` 按函数调用处理（`HirCall`），
与 D-5 标识符中缀糖、符号前缀调用 `⊗(1, 2)` **同产物**；不再要求符号名在内建算符表内可定类。

判据二分（语义期，因解析期不知符号身份）：

| 名字身份 | 通道 | 理由 |
|---|---|---|
| 内建算符（`+ - * / % = == != < <= > >= ! ¬ & | <-> ∧ ∨ → ↔`) | 既有 `BinExpr` → `infer.binExpr`（数字塔/ST-10.1） | 逐字不变 |
| Unicode 符号且为声明的函数 | 降为 `callExpr`（`HirCall`） | 操作符即函数（R2/R3/R4 方向） |
| Unicode 符号且未声明 | 复用既有 `E-UNBOUND-NAME`（「未定义的名称 `⊗`…」） | 复用既有码面，不新增诊断码 |
| ASCII 表外算符（`^` 等） | 既有 `E-OP-UNCLASSIFIED` 通道 | 触发面逐字不变 |

## 二、路径选择：路径 2（语义层），不开路径 1

队长倾向路径 1（解析期一律 `CallExpr`，语义期按内建身份回退 `BinExpr` 语义）。本 ADR 选**路径 2**（解析期保留
`BinExpr`，语义层分流），理由：

- **命题位 `a ∈ b` 零风险**：`∈` 词法为 SYMBOL kind，经 D-6 产 `BinExpr("∈")`；`PropNormalizer`（:87）把
  `BinExpr("∈")` 归一为 `HirPAtom("in", terms)`，`IfPropStmt` 条件位（Elaborator:387）也按 `cond.op == "∈"` 识别。
  若改解析层产 `CallExpr`/`SymbolCallExpr`，命题位归一头名会变成 `"∈"` 而非 `"in"`，命题位用法（验收 #7）被破坏，
  且需同时改 PropNormalizer/IfPropStmt/ProofChecker 三处识别点。路径 2 不动解析层 → `BinExpr("∈")` 形态逐字保持。
- **内建算符通道零风险**：`+ - * /` 等为专用 Kind（binops 集合），D-6 分流条件 `!isBuiltinOp(op) && op 含非 ASCII 字符`
  精确排除全部 ASCII 算符（含用户自定义 `fun +(a, b)` 的情形——仍走内建数字塔，验收 #6 逐字不变）。
- 路径 1 的收益（语法层即同产物）在路径 2 下由**符号前缀调用同通道**补齐：`SymbolCallExpr` 的非内建分支一并转入
  `callExpr`（见下），使 `1 ⊗ 2` 与 `⊗(1, 2)` 的 HIR 结构相同（验收 #3）。

## 三、实现

| 位置 | 改动 |
|---|---|
| `TypeInfer` | 新增 `builtinOps` 集合（`= builtinOp when 的 keys 镜像，改表须两处同步）+ `isBuiltinOp(op)` |
| `Elaborator` `is BinExpr` | `!isBuiltinOp(e.op) && e.op.any { it.code >= 128 }` → `infer.checkScope { callExpr(CallExpr(NameRef(op), [l, r])) }`；否则既有 `infer.binExpr`/`narrowMinus` 通道 |
| `Elaborator` `is SymbolCallExpr` | 内建 op 保持既有 builtinOp/二元口径；非内建 op 转 `callExpr`（与中缀同产物；未声明由 `resolveValue` 落既有 E-UNBOUND-NAME） |
| `SymbolInfixCallTest`（新，9 方法） | 验收 8 例正负成对 |

不改：Parser/词法、AST 结构、`builtinOp`/`binExpr` 内建分支、PropNormalizer、诊断码集。

**结合性**：D-6 解析循环为左结合（`1 ⊗ 2 ⊗ 3` → `(1 ⊗ 2) ⊗ 3`），HirCall 嵌套形态即左结合证据（测试 #8 断言）。
零优先级机制变化（D-10 现状保持）。

## 四、验收（BEFORE → AFTER）

BEFORE（修复前探针，CLI `check`）:

```
1 ⊗ 2（⊗ 已声明）  → E-OP-UNCLASSIFIED: 中缀 ⊗: Nat 与 Nat 无法定类（不在内建算符表内…）
" x " ⊗ "y"         → E-OP-UNCLASSIFIED: 中缀 ⊗: Str 与 Str 无法定类…
1 ⊗ 2（⊗ 未声明）   → E-OP-UNCLASSIFIED … 无法定类
⊗(1, 2)             → 通过（既有 synthetic 放行）
```

AFTER：

1. `1 ⊗ 2`（声明）→ 通过；2. `⊗(1, 2)` → 通过；3. 两者 HIR 同构（`HirCall`：callee 名/symbol/args 数/返回类型名相同，仅位置不同）；
4. `" x " ⊗ "y"`（Str 符号函数）→ 通过；5. 未声明 → `E-UNBOUND-NAME: 未定义的名称 \`⊗\`（作用域中无此变量/函数/构造子）`，不再 `E-OP-UNCLASSIFIED`；
6. `1 + 2` 数字塔通过 / `a - b`(Nat）`E-TYPE-MISMATCH` 逐字不变；7. `axiom forall x. (x ∈ a) → (x ∈ b)` 归一 `HirPAtom("in")` 逐字不变；
8. `1 ⊗ 2 ⊗ 3` 通过且左结合。

## 五、回归与既有失败归因

- 全量 `:frontend:test --rerun-tasks` = **675 tests / 8 failures**（≥647 下限达成）。其中 8 处失败经**回退归因验证**：
  把 t67 全部改动从 `Elaborator.kt` 移除后重跑 `DefaultsSemanticsTest` / `StructCtorTest` / `TypeInferTest` 三类，
  失败数 **2/1/5 逐字一致**（合计 8）——即这 8 处为**既有失败**（默认值填补「缺实参」/ unify / when 解集回代等），与 t67 无关。
  详见 log 与致队长的报告。
- e2e = **65/68**（list.subl 13 项为 t66 重冻结口径，红集 = list/手动证明/模块系统-主，未改任何样本）。
- 双快照零警告：compileKotlin / compileTestKotlin 均 BUILD SUCCESSFUL 无 e:/warning。

## 六、后果

- `1 ⊗ 2` 与 `⊗(1,2)` 同产物，为 R3/R4（中缀形式对偶/统一）打好产物层基础。
- ASCII 算符（`+ - * / %` 等）与命题位 `∈` 行为逐字不变。
- 优先级机制（D-10）与词法（ASCII 符号标识符/粘连规则/`<:`）留给 P2/P3/P4。
