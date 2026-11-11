# ADR-53：表达式层连接词闭合（t92）与 `<->` 退役

## 状态
已落地（t92，2026-11-09）。裁决 4「表达式层一律 `&` `|` `!`」的表达式层闭合半边。

## 背景
- t74 名字材料化后，`a ∧ b` 经 D-5 糖落 `CallExpr(∧, [a, b])`；expr() 有「`isBuiltinOp(nm)` ∧ `propCtors[nm].arity == 2`」
  的条件交集，把值位的 `∧ ∨ → ↔`（及 `<->` 的 BinExpr）回落为 Bool 内建——qa/t77 测绘未覆盖的盲区。
- 队长裁（待裁-A）：裁决 4 字面 ⇒ 值位应当拒绝；68 样本值位 `∧ ∨ → ↔` 使用 = 0（仅两处字符串字面量误命中）。
- `<->`（待裁-B）：`&&`/`||` 的同族（t83 退役先例），内建含义退役、保留拒绝锚点与 R2 用户自定义通道。

## 决策
1. **值位拒绝原生命题连接词**：`expr()` 的 CallExpr 分支——`NativeProps.propCtors[nm] != null`（含 D-5 糖的中缀形态与
   符号前缀 `∧<a, b>` 形态）——落 **E-PROP-FORM**（复用：与 t85 命题位 `&` 反向同码、同半成品模式），按算符给可编译
   提示（ADR-48 §八）：`∧`→`&`（`a & b`）、`∨`→`|`（`a | b`）；`→`/`↔` 值位无等价写法——只定位，不杜撰算符。
   半成品为 `HirErrorExpr`（与 propOnlyExpr 同模式；操作数不求值，单错不级联）。
2. **内建签名清理（`TypeInfer` 两处镜像同步）**：`builtinOps` 集合与 `builtinOp` when 分支移除 `<-> ∧ ∨ → ↔`；
   保留 `&` `|` `!` `¬`（`¬` 是命题层一元，其值位名字材料回落 t74 既有机制不在本任务）。
3. **`<->` 退役**：`Lexer.kt:106` 仍产 `Kind.IFF`（与 LAND/LOR 同为「拒绝锚点」保留 token 产出；命题位 `<->` 须仍可解析）。
   - `Parser.binops` 保留 IFF（命题位 + 用户中缀都要可解析）；`Elaborator` 的 BinExpr ASCII 路由追加 `e.op == "<->"`
     走用户函数调用通道——**R2 保留**：用户可定义 `fun <->(a: Bool, b: Bool)` 并作中缀调用。
   - 未声明时 `NameResolver.resolveValue` 落 **E-OP-RETIRED**（同 `&&` 先例，不再走旧 E-OP-UNCLASSIFIED），
     提示命题层 `↔`。
4. **命题位/项位不受影响**：`PropNormalizer` 按 `propCtors` 分派原生节点（`HirPAnd/HirPOr/HirPImp/HirPIff`）逐字不变；
   项位连接词经 `nativeTermBinName` 产 `HirTBin`，不经 expr()——验收用例 `<p ∨ q>` / `axiom ⊤` / 项位 `→` 全绿。

## 改向登记（只改向、不改码）
- `P5Batch2NativePropNameTest #5`：BEFORE（t74 回落：零错误 + `HirBinExpr("∧")`）→ AFTER（E-PROP-FORM + 值位 `&` 提示）。
  与 n5b（t83 `&&`）同一行为变更登记卡模式。
- `T86 f2c`：值位调用形态 `∧(True, False)` 从「回落不变」改向「拒绝同码」。

## 验证
- 全量 **813/0**（804 基线 + `T92ValuePositionRejectionTest` 9 例；改向两例无增减）。
- e2e **65/68**，三冻结红逐字不变（list 13 项 / 手动证明 2 项 / 模块系统 4 项）。
- 68 样本 JSON：vs 起点 out/t86 **SAME=68/68 零差异**；vs 权威基线 base-t74 SAME=55/DIFF=13——diff 集与 t86 继承集
  逐处一致（全为 t80/t82 源码迁移，本任务零新增）。
- 零新增诊断码：E-PROP-FORM / E-OP-RETIRED 均为既有码复用。

## 探针矩阵
`frontend/probe-mut/cases-t92/`（p1–p12，先红后绿）：值位 `∧ ∨ → ↔ ↔-unicode` 全拒（code=1），值位 `& | !` /
命题位 `∨` / `axiom ⊤` / 命题位 `↔` / 用户 `fun <->` 全绿（code=0），未定义 `<->` 落 E-OP-RETIRED。
