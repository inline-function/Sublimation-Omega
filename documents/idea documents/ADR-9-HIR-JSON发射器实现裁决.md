# ADR-9 HIR→JSON 发射器实现裁决

| 字段 | 值 |
| --- | --- |
| 编号 | ADR-9（承接 ADR-8） |
| 主题 | t10：sublimation.frontend.json 包实现期裁决 |
| 上游依据 | ADR-1 §3.7（schema 草案 v1）、P-6（手写发射器）、D-9（信息保留）、D-10（诊断四级，N-SEV-BLOCK）、S-2 §3.3（json 包仅依赖 hir + diag）、S-1 层间总契约 3（可复现） |
| 下游影响 | 后端 / 解释器 / LSP 的产物消费方；t11（CLI 集成）的落盘口径 |

## 一、背景

t10 落地 HIR→JSON 序列化：手写发射器（P-6 已决，零第三方运行时依赖），schema 与 ADR-1 §3.7 草案 v1 对账。
交付：JsonWriter.kt（写入底座 + RFC 8259 转义）与 HirJson.kt（91 节点穷尽分派 + envelope）。
实现期有若干 ADR-1 草案未细化的形态选择，逐项裁决如下；草案明确允许「t10 实现期可微调，breaking 变更须追加新 ADR」。

## 二、决策

### 2.1 统一节点形态覆盖辅助记录

HirParam / HirCtor / HirWhenArm / HirWhenPropArm 等辅助记录一律按节点统一形态发射（kind / nid / pos / notes + 语义字段）。
ADR-1 示例 A 文档中 HirParam 以紧凑形态展示——文档自述「省略号不是 schema 的一部分」，且 §3.7 节点统一形态的口径是「每个 HIR 节点」；
辅助记录在 t6（ADR-5）即 HirNode 实现，发射端不做第二套形态。
动机：消费方单一解析路径（D-9 五件套的一致性），避免「哪些节点有 notes」成为隐式知识。

### 2.2 字段顺序与空值策略

- 字段顺序 = Kotlin 声明顺序（data class 属性序），保序输出由 JsonWriter 的构造序天然保证；
- **从不省略字段**：空集合显式发射（"annotations": []），可空引用显式 null（"matched": null、"default": null、"symbol": null）。
动机：schema 自描述、产物可 diff、回归可逐字节对账（S-1 层间总契约 3）；省略字段会把「缺省」与「未解析」混为一谈。

### 2.3 位置与枚举口径

- pos 紧凑形态：单点 "l:c"，区间 "l:c-l:c"（ADR-1 §3.7 原口径）；
- 枚举一律小写：节点 kind（Kotlin 类名原样，Hir 前缀）、SymbolKind（fun/param/builtin/…）、severity、purity、proof status、note kind；
- 字面量保留源码原文（"value": "100"），不做规范化（后端 codegen 依赖原文，ADR-1 §3.3 #8）。

### 2.4 转义策略（RFC 8259 §7）

强制转义 \"、\\ 与 C0（U+0000~U+001F）：其中 \b\t\n\f\r 取短转义，其余 C0 取 \u00xx 小写十六进制；
DEL（U+007F）与非 ASCII 字符原样输出（产物以 UTF-8 写出）。
动机：全量转义无谓放大体积且损失可读性；DEL 与非 ASCII 在 UTF-8 产物中无歧义。

### 2.5 格式与 envelope

- 换行 + 2 空格缩进；空对象/空数组取紧凑形态 {} / []；
- envelope = {schema, compiler.version, unit{path, mode}, ok, diagnostics[…], hir}，不含 sha256（v1 未列入；breaking 变更须追加 ADR）；
- unit.mode 推导：HirStmtEntry 在场即 "script"，否则 "project"（空文件 → project）；
- ok 旗标：仅当诊断袋含 ERROR 时 false（N-SEV-BLOCK / D-10；warn/hint/supplement 不阻断）；
- 版本常量：SCHEMA = "sublimation/hir@v1"，COMPILER_VERSION = "0.1-SNAPSHOT"（与根 build.gradle.kts version 对齐）。

### 2.6 证明节点形态

proofContext = {facts: [完整命题节点（内联不引用）], matched: SymbolRef | null}；rule 字符串；verdict = {status, diagCode | null}。
HirBecause.conclusion 按 t6（ADR-5）的具体形态发射为**单个命题节点**——ADR-1 示例 B 的 "conclusion": […] 中括号为文档省略写法（同 premises 的「…同构命题…」），不是列表语义的裁决依据。

### 2.7 深递归上界（风险裁决）

发射器为递归实现，深度上界与 JVM 默认栈深同阶：**实测 1000 层嵌套 HirPNot 触发 StackOverflowError，200 层稳定**（每层约 5~8 帧：obj/arr/field/item λ 嵌套）。
与解析器（t4 递归下降）同类约束：深嵌套源码会先在 parse 期越界，HIR 不会无端达到该深度。
v1 不采用缓解（迭代式发射需重写 91 节点分派；-Xss 属构建配置而非库契约）；单测以 200 层覆盖并断言层级计数与括号平衡。
若后端 generated HIR 需要更深嵌套，追加新 ADR（迭代发射或深度上限 + 诊断 E-DEEP-EMIT）。

## 三、验证

- 齐备性双判据：发射分派为 sealed 穷尽 when（编译期）+ 91 节点样本名集运行期对账（零缺失零多余）；
- 契约金样：示例 A / B 逐字节对账（envelope/证明节点/verdict/matched），示例 C（脱糖 note）形态断言；
- 保真：RFC 8259 转义、DEL/非 ASCII 原样、字面量原文、null/空集合显式、诊断全字段（四级 severity + module + nid）、枚举小写、unit.mode 推导、ok 旗标；
- 纪律：纯函数可复现、产物文件 UTF-8 落盘、200 层深树结构平衡；
- 信任边界自证：json 主源 import 仅 sublimation.frontend.hir + diag（+ java.io.File），JsonWriter 零 import（S-2 §3.3 落实）；
- 全工程 :frontend:test 165 tests / 0 failures（其中 HirJsonTest 19 项）。

## 四、后果

- 正向：零依赖、schema 完全自控、产物逐字节可 diff 可回归；消费方按统一节点形态解析，无隐式缺省；
- 负向：发射器代码量 ≈ 节点数 × 分派（91 节点约 720 行，P-6 已预见）；深递归上界如 2.7 所述。

## 五、修订记录

- 2026-11-04（t7 联动）：hir 程序面补绘 HirBinExpr（算术/比较）与 HirUniExpr（一元），节点数 91→93；发射器 HirJson.kt 两分支（op/left/right/type、op/operand/type）已随 t7 落地，json 测试同步（名集 93 + zoo 样本补构造 + 两行显式导入），:frontend:test json 包 19/19 绿。补绘动机与 hir 落档属 t7（其裁决编号经协调取 ADR-10）。

---

## 附：schema v2 修订（P-8/C，t29，2026-11-06）

SCHEMA 已按卷 TS-31 升为 `sublimation/hir@v2`（breaking 变更）：ImplDecl/HirImplDecl 删除（TS-30）、
HirFunDecl.receiver 省略/必现口径、HirSelfType 固定形态、HirClassConst 新增。按本 ADR「breaking 变更
须追加新 ADR」纪律，裁决与偏差登记见 **ADR-24《HIR schema v2 与 impl 节点删除》**；本文历史文本保持原文不动。
