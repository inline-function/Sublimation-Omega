# jsoncmp —— HIR 产物逐字比对台（**基线保护区**）

## 目录职责

| 目录 | 职责 | 谁可以写 |
|---|---|---|
| `base-t74/` | ✅ **权威基线**：t74 终态的 68 样本 HIR JSON + 日志（2026-11-09 重建，与可信旧基线 **56/56 逐字一致**） | **没有人**。只读。 |
| `old/` | 历史基线（**12 个 json 已污染**，见下）——留作取证，**不再用于比对** | **没有人**。只读。 |
| `out/<tag>/` | 每次比对的**导出目录**（tag 自取，如 `t82`）| 任何执行比对的人 |
| `*.ps1` | 导出脚本 | 队长 |
| `Lexer.t74.bak` | t74 词法改动的现场备份（取证用）| 队长 |

## 铁律

**导出脚本一律写 `out/<tag>/`，绝不允许写 `old/`。**

原因（事故记录，2026-11-09 10:52）：
一名成员执行「BEFORE 基线核对」时误用写 `old/` 的脚本，**覆盖了 25 个基线文件**（`core__async__*`、`core__classic__*`、`core__collection__*` 前 12 项），
这些文件从此**不再可信**——它们是被改过的树导出的，不是 t74 前基线。
剩余 **56 个 `.json` + 55 个 `.log`**（mtime 2026-11-08 19:47/19:48）**仍是原基线**。

> 计数更正（2026-11-09 13:0x，qa 指出）：队长先写「43/68 仍可信」是**错的**——把 25 个污染文件（12 json + 13 log）当成 json 从 68 里减了。**可信 json = 68 − 12 = 56**。

## 被污染清单（25 个 `.json` + 对应 `.log`）

```
core__async__async.subl
core__async__channel.subl
core__async__flow.subl
core__async__mutex.subl
core__async__task.subl
core__classic__classic.subl
core__collection__list.subl
core__collection__map.subl
core__collection__optional.subl
core__collection__result.subl
core__collection__set.subl
core__collection__traits.subl
```

## 影响与补救

- **不影响既有结论**：`SAME=68 / DIFF=0` 的比对于 2026-11-09 10:2x **在污染之前**完成并已记入 ADR-49 §六与当日 log。
- **影响后续比对**：这 12 个样本失去"t74 前基线"，不能再做逐字比对；它们只能做**结构比对**（忽略 nid/origin/notes）。
- **补救**：在 t79/t80（Bool 字面量与样本迁移）落地后，从**已知稳定点**重新导出并冻结为 `out/base-t80/`，此后以它为基线。
- 教训（队长）：**任务书里"自己重新导出比对"没有指定输出目录**，成员就沿用了现有脚本的默认写入位置。规格不写死，等于交给运气。

## 2026-11-09 13:0x 更新：基线已重建

- `out/base-t74final/` 由 semantics 用**恢复后的 t74 终态树**重新导出（68 json + 68 log）；
- 队长校验：与 `old/` 中**全部 56 个可信 json 逐字一致**（`SAME=56 / DIFF=0`）⇒ 可作权威基线；
- 已提升为 `base-t74/`。**此后一切比对以 `base-t74/` 为准**；`old/` 仅作事故取证；
- 注意 `.log` **不可逐字比对**（含运行元数据）；契约只看 `.json`。

## 2026-11-11 t111（D-12）:预期差登记——公理化.subl

- D-12 修复（模式真值单源化）后，`examples__公理化.subl.json` 的 `unit.mode` 由 **script → project**：
  该样本顶层 `axiom moduleReady`（Parser:204 工程模式允许顶层 axiom 语句），旧判据「顶层语句存在⇒脚本」
  误标 script；真值单源后按 CLI 真值（.subl = project）落 project。**此差为缺陷修复预期差（D-12 号）**，
  后续比对（t109+）见该项 DIFF 应按此条判白，不得回退修复。
- 其余 67 项保持逐字一致（t111 验收实测 SAME=67/68）。

## 2026-11-12 t122（R-16 A2 · I-1 收口第二期）——基线重落 + 差分台账

OQ-7 队长裁 = **T（calleeImpure 真化）**；A2 落地后 68 样本全量重导（`out/t122n/`），与 `out/t113n/` 的结构差分逐项登记如下（**后续 SAME 门以 `t122n/` 为基线**）:

| 差分项 | 计数 | 性质 | 说明 |
|---|---|---|---|
| `calleeImpure: false→true` | **249 处** | OQ-7 真化（预期内） | 阶段 2 闸回写被调侧结论——`calleeImpure` 从构造位恒 false 升为真值;闸判据（E-IMPURE-CALL 三路放行/借用形参）行不变，故 `calleeImpure` 翻位不伴任何新红 |
| `purity: PURE→IMPURE` | **6 处** | 闭包推导回写（新增语义，队长裁 A2 允许） | 68 样本中 6 个未注解函数闭包推导为不纯并回写 `HirFunDecl.purity`：`_probe_div:main`、`上下文弥散:main`、`副作用与unchecked:rootZero/pureWrap`、`模块系统__主:main`、`特性巡礼:pureCaller`(种子源：内建不纯 print 直调 ×4 / 体效应 t94 ×2) — 逐项伴随 D-PURITY-INFERRED 审计四元组（supplement 级，非红） |
| 新诊断 | 6 条 D-PURITY-INFERRED | supplement | 同上——冻红不受影响 |
| 红诊断（error 级） | **0 差异** | 冻红 | 68+脚本样本全量 ERROR 级诊断与旧树逐字同（16 条红项计数/码/文案/位置全一致，见 `build/probe-t122/after.txt`） |

另：`out-subs/t01/` 同期重落（同批 OQ-7 真化 + 2 条 D-SCRIPT-IMPLICIT-IMPURE——脚本顶层语句隐式不纯载体，OQ-2「挂 unit 级+诊断可见」）；脚本门重跑 **SAME=14/14**。

差分对拍门（P-11 §9.1，t122 新建）：`frontend/src/test/resources/diff-pairs/` 四件（`dp-mode-share.subl/.subs` 纯函数链双模式；`dp-pure-calls-impure.subl/.subs` 护栏③ 真「纯函数调不纯」样本）——`T122A2ClosureTest` 锁定「同逻辑 .subl/.subs 忽略 inferred/evidence 后 hir 结构签相等」。

## 2026-11-12 t123（R-16 S4 第三机理 · 上文上提）——SAME 基线 t122n 不动 + 脚本契约记账通道

A2 收口后 SAME 基线为 `out/t122n/`；t123 落地**未动该基线**（工程模式零 lift ⇒ t123 导出与 t122n **逐字节 SAME 68/68**，冻红 10 项一致），基线延续护栏②。**唯一产物面新增**在脚本模式契约通道：

| 通道 | 形态 | 说明 |
|---|---|---|
| 签名级欠条 | `HirFunDecl.preps` 合成 `<pure<g>>`（`provenance=SYNTHETIC`，JSON 产物可见，不入 axiomFacts 信任基——分账同 t118/C-8） | 纯声明链函数体调 impure 被调且三路供给皆无 ⇒ 上提为签名欠条（脚本模式记账;工程模式保持 E-IMPURE-CALL 硬拒零变） |
| 链式上提 | 欠条沿**闭包同一边集**（t122 A2 复用，零新增遍历）反传至声明纯调用方 | 吸收终态：声明 impure 帧（注解/退化/R-07 捕获）/ 脚本顶层 root 隐式入口 |
| 审计四元组 | `D-LIFTED-CONTEXT`（函数 / 义务原文 / 失败原因分类 / 证据尝试 `名@pos`） | OQ-4——同 D-PURITY-INFERRED/D-INFER-DEGRADED 审计口径，supplement 级 |
| `--no-lift` 门 | `E-LIFT-FORBIDDEN`（OQ-5 队长裁=给） | 同 `--no-degrade` 双口形态：默认静默记账 / 门下真红（build/probe-t123 双口快照已验） |

**分型红线（R-16）**：仅纯度族（`pure<f>` 契约）可上提——停机族（E-NON-STRUCTURAL-REC）/类型确立族（E-INFER-NO-SOLUTION）**不可**（R-02 健全性优先，未开绕过口子）。**三级正交**：Γ 帧级（t108 factFrames）/签名级（本件 preps）/产物级（t118 inferred/evidence）载体互不流动。

脚本体（out-subs 14 样本）无 lift 触发样 ⇒ 脚本门 14/14 重跑**逐字节同**（t01 基线延续，未重落）。差分对拍集扩面（护栏③）：新增 `dp-lift-chain.subl/.subs`（三层链式上提）入 `frontend/src/test/resources/diff-pairs/`——`T123LiftTest` 锁定「双模式忽略 preps/inferred 后 hir 结构签相等 + 脚本侧三层欠条入产物」。
## 2026-11-12 t131（S7 / R-22 落地：目录即模块 + 限定引用）——SAME 基线 t122n 不动 + 模块新侧基线

**67 非模块样本逐字节 SAME 67/67 vs t122n**（模块系统零侵入的机器证明：`mounted` 默认空 ⇒ 语义层零改）。
冻红 2 项（core__collection__list / examples__手动证明）与既有基线同态（红态 json 逐字同）。

**模块系统样本**按 OQ-10 队长裁：**基线走新侧目录 `out/t131mod/`**（t122n 不翻、68 计数零动）：

| 产物 | 形态 | 状态 |
 |---|---|---|
 | `examples__模块系统.json` | 目录形态主 envelope（unit.path=模块系统） | **冻红→绿**（ok=true，零 E- 诊断） |
 | `examples__模块系统__tool.json` | 挂载子模块 envelope（unit.path=tool） | 绿（ok=true） |
 | 脚本 | `scripts/t131mod-export.ps1` + `manifest/export-t131mod-list.txt` | 68 行（67 单文件 + 1 目录行） |

模块产物双编译逐字节 SAME （内置确定性，nid 纪律：模块挂载零新 nid 面）。模块样本的**单文件形态**
（manifest 旧行 compile 主.subl）与 t122n 逐字同——冻红保形（限定引用无通道的既有态原样保留）。
限定引用 HIR 载体 = HirCall(callee=HirField(field=模块函数符号)) + D-MODULE-REF 旁注（提案 OQ-3 B-i 零 schema）。

约束登记：跨模块**不纯**调用的纯度记账 V1 不贯通（calleeImpure 落位、effect 记账未接——提案 §9 限制面）；
跨模块**类型别名/结构体/扩展函数**限定通道 V1 未通（仅自由函数面）。

---

## t137 分账（stdlib B0-0 + B0-1，2026-11-12）

| 目录/件 | 职责 | 谁可以写 |
|---|---|---|
| `out/t137stdlib/` | stdlib 件**侧基线**（e2e 主样本零动的差异收敛口径——stdlib 件不入主基线判 diff） | 执行 t137 门的人 |
| `scripts/t137-parity.ps1` | retire 机械证据三段：① SymbolTable.kt retire 活动件核对（val PRELUDE_FUNS/preludeFunSpec/PreludeField/PreludeFun 地毯式零命中）② 7 依赖样本 vs `out/t122n/` 逐字节零差分 ③ stdlib 件基线导出（list/math/… 于模块系统样本内） | 执行 t137 门的人 |
| `out-subs/t137gate*/` | 落实证据（68 门 SAME 重跑产物——同 t122n 逐字节） | 执行 t137 门的人 |

**t137 侧基线口径**（R-23 裁决）：e2e 68 样本逐字节 SAME 为主门（retire 零差分机器证明）；stdlib 件本身是**新面**（B0-0 list/math + B0-1 五件全家），产物落 `out/t137stdlib/` 侧目录登记，不入 t74/t122n 基线判 diff。钻石形备忘按首次编译路径记 unitPath——optional/set 件产物见于 list 命名空间下（`examples__模块系统__list__optional.json` 形态），不重复入计。
---

## t138 分账（stdlib B1：虚拟 struct + 联合别名 retire，2026-11-13）

| 目录/件 | 职责 | 谁可以写 |
|---|---|---|
| `out-subs/t138gate-ledger.txt` | B1 68 门差分台账（SAME/剥差同 逐样本登记） | 执行 t138 门的人 |
| `scripts/t138-parity.ps1` | B1 机械证据四段：① PreludeDecls retire 核对（STRUCTS 仅 Channel、ALIASES 空、RETIRED 允许表 + stdlib 源声明在位）② 7 依赖样本 vs `out/t122n` 剥差门（逐字节同或 pos/message 盲同）③ 68 门台账导出 ④ 模块双导出确定性（1+7 件集合） | 执行 t138 门的人 |
| `out-subs/t138gate*` / `t138work` / `t138det-*` | 落实证据（68 门重跑、7 依赖重跑、双导出产物） | 执行 t138 门的人 |

**B1 侧基线口径**（队长死纪律：t122n 与 68 计数零动；B1 差异全入差分台账）:

| 量 | 结果 | 性质 |
|---|---|---|
| 68 门（vs t131mod） | **SAME 52 + 剥差同 16 + 失败 0** | 16 例差异位**全部**为 `$.hir.entries.N.retType.pos` 联合展开位（含 `variants.N.pos`），共 138 处（message/其他位差异 = 0）——别名展开的变体位置由虚拟声明 SYNTHETIC `0:0` 变为 stdlib 模块源实坐标（如 list.subl:12 的 `Nil`/`Cons` 位）；**剥差（删除 pos 字段）后 16/16 逐字节同 ⇒ 行为零变**（OQ-10 B1 口径：差异限定在声明位/旁注形态变化） |
| 脚本门（out-subs/t01） | **14/14 逐字节同**（12 件 + 2 件 --no-degrade） | 脚本模式 B1 零侵入 |
| 模块双导出确定性 | **8/8 逐字节同** | B1 分层后件集合 1+7（主 + tool/list/optional/optional__result/set/map/math）；B0 时件集合同为 8（1+7，命名重排——optional/set 不再嵌于 list 下） |
| 套件 | **1120/1120 全绿**（t137 1111 → +9 T138StdlibRetireTest） | 套件只增不减 |
| 模块件全家绿 | list/optional/result/set/map/math + 嵌套 unit **8/8 ok=true** | DAG 分层（list 底层 + optional 托管产 Optional 访问器）后零红 |

**DAG 分层登记（B1 纪律增量）**：模块图须 DAG 且「限定构造形态 V1 未通」⇒ 件间类型经**挂载**解决（list↔optional 互依成环不可）。分层：list 底层纯 List 域；optional 挂 list+result 托管 head/last/elementAt/find/indexOf/maxNat/minNat/get/firstOr/toResult/toList；set/map 各挂 list（map 兼挂 optional）并托管 toSet/toMap。九函数件属移位（函数全家与 e2e 冻红零变——裸名不可见，用户面不动）。

**工具路径同构**：`compile/compileWithRounds/compileVariance/elaborate` 新增 `mounted` 透传参（默认空，非模块编译零影响）；12 件旧测试（PreludeDeEnum/AliasSemantics/T126/T128/T99/Subtype/Narrow/Union×2/Assign/When/Variance）script helper 改挂 stdlib 快照（同 t19 口径）。


---

## t139 分账（stdlib B2：EXT_METHODS retire + ext 限定调位，2026-11-13）

| 目录/件 | 职责 | 谁可以写 |
|---|---|---|
| `out-subs/t139gate-ledger.txt` | B2 68 门差分台账（SAME/剥差同 逐样本登记） | 执行 t139 门的人 |
| `scripts/t139-parity.ps1` | B2 机械证据五段：① PreludeDecls retire 核对（EXT_METHODS 空表、RETIRED_EXT_METHODS((List,add))、list.subl add 落体/体空）② 7 依赖样本剥差门（vs t122n）③ 68 门台账导出（vs t131mod）④ stdlib 侧基线比对（vs t137stdlib：预期仅 list 件差 = add 声明位）⑤ 模块双导出确定性 8/8 + 脚本门 14/14 | 执行 t139 门的人 |
| `out-subs/t139gate*` / `t139work` / `t139dev` / `t139stdlib` / `t139det-*` | 落实证据（68 门重跑、7 依赖重跑、stdlib 侧基线重跑、双导出产物） | 执行 t139 门的人 |

**B2 数据源替换台账**（EXT_METHODS 唯一条目 @mut List[a].add → stdlib list 源声明，OQ-4 = b 签名面延续）:

| 量 | 结果 | 性质 |
|---|---|---|
| 68 门（vs t131mod） | **SAME 68 + 剥差同 0 + 失败 0** | B2 后全样本逐字节同（t138 台账 16 剥差同条目在 B2 树态收敛为 SAME——重跑 t138-parity 同得 68/68，两证一致） |
| 7 依赖样本（vs t122n） | **SAME 6 + 剥差同 1**（std 库集合：联合展开 pos 位） | 延续 B1 口径的行为零变 |
| stdlib 侧基线（vs t137stdlib） | **逐字节同 3 件 + 预期差 list 件** | 差 = add 新声明位（HirFunDecl 条目 + 源位）; optional/result/set/map 件不在 t137 基线覆盖面（模块样本挂载集差异），以双导出确定性 8/8 兜底 |
| 脚本门（out-subs/t01） | **14/14 逐字节同** | 脚本模式 B2 零侵入 |
| 模块双导出确定性 | **8/8 逐字节同** | 挂载件集合 1+7 不变 |
| 套件 | **1128/1128 全绿**（t138 1120 → +8 T139StdlibExtTest） | 套件只增不减 |
| 模块件全家绿 | list/optional/result/set/map/math **8/8 ok=true** | DAG 分层不变；add 体仍空 |

**B2 机制登记**（R-23 纪律增量）:
- 查询链不变：本模块 > 挂载子 > prelude 虚拟层。retired 键 (List,add) 经 RETIRED_EXT_METHODS 允许表由 Elaborator init 注入 `SymbolTable.virtualExtMethods`（可变注入表，EXT_METHODS 撤后唯一数据源），`preludeVirtualExt` 的 builtins 懒登记与 id 序零改 ⇒ 方法糖/直接调用面 HIR 形态零差分（测套门 3：无 D-MODULE-REF 旁注）。stdlib 快照 ext 凡遇 retire 键在 `sugarExt` 跳过（否则模块面命中会落模块符号 id 而漂移）；用户件（snap.stdlib=false）维持 t134 挂载面口径。
- **ext 限定调位**（B2 新绿面）：`moduleQualifiedCall` 自由函数面未命中时查快照 ext 面（实参 1 = 接收者位，头推导复用 `extHeadOf`），命中落 `HirField(模块别名, 名, field=ext 符号)` + D-MODULE-REF 旁注（zero schema 变动）；签名核对复用 `extCallType`（接收者首参），借用实参位同方法糖口径（borrowArgNotes）。限定调位诊断文案升格为「自由函数面与扩展函数面均无该名」。
- **add 体仍空**（OQ-4 队长裁 b，别写结构性体——a 方案在 codegen 数组特判下语义假）：`@mut fun (@mut List[a]).add[a](v: a): List[a]` 无体 + 注释登记；R-15 兑现债随「运行时桥规格」（R4）后续件落（提案 §4 结论）。
- **FUNS 不动**（MutableListOf/ListOf 分 B2.1 后置——本批只 retire EXT_METHODS 一条）。
## t140 分账（stdlib B2.2: HKT 特质族·接收者形态 + 满足面双通道 + 快照 trait 面，2026-11-13）

**变更画像**（字面: 既有件零字节变化 + 唯一 additive 新子件）:

| 门 | 结果 | 证据 |
|---|---|---|
| 精确差分（vs t139 冻结产物 `out-subs/t139gate`） | **67 e2e 件逐字节同**；模块样本主件 + 8 子件逐字节同 | out-subs/t140diff-ledger.txt、t140-parity ③ |
| 唯一 additive | 新子件 `examples__模块系统__主.subl__traits.json`（stdlib 默认挂载第 7 件的产物） | t140-parity ③ |
| 68 门（vs **t122n 死冻锚**，队长修正令口径） | SAME 51 + 剥差同 16（历史盲差 pos/message，t131 时代起未收敛）+ 结构差 1（模块样本——t131 冻红→绿演化叠加本批 traits 子件，非回归） | out-subs/t140gate-ledger.txt |
| stdlib 侧（vs t137stdlib） | 逐字节同 3 件（math/tool/主件）+ 预期差 1 = list（**t139 的 add 声明位，保持**） | t140-parity ④ |
| t139→t140 快照侧 | 8/8 逐字节同 + traits 新件 | t140-parity ④ |
| 套件 | **1144/1144**（1128 基线 + 16 T140StdlibTraitsTest；存量 3 处库存断言更新——2 处 stdlib 件清单 + 1 处模块子产物 7→8，测试不减） | :frontend:test |
| 模块确定性 | 9/9（原 8/8 → +traits 子件） | t140-parity ⑤ |
| 脚本门 | 14/14 | t140-parity ⑤ |

**台账登记（新面/预期差/挂账）**:

- **stdlib 新件**: `stdlib/collection/traits/traits.subl`（五特质族 + 头注）入默认挂载——模块样本新增 `__traits` 子件（additive 台账登记，零既有字节变化）;
- **pure 自由函数实例回退**: 曾落 list/optional/set 三件的 `fun pure[a](x: a)` 实例（满足面第二通道），模块路径触发裸名链 E-DUP-DECL 三件同名 + R-23 裸名链污染（特质实例名入用户裸名面）⇒ **回退**; 容器 pure 实例保留既有**扩展形态**，自由函数通道由语言面测试覆盖（用户模块级能力）;
- **历史盲差不收敛声明**: vs t122n 的 16 剥差同为 pos/message 盲差（t131 时代起，与 t140 无关——精确差分证明）; t131mod 活目录不作锚（队长修正令）;
- **traverse 单子约束挂账**: `M[_]: Applicative` 落 E-TC-BOUNDARY（TS-24 V1 不可为界）⇒ Traversable 本体无界形态声明 + 债登记（提案 §14）;
- **stdlib 特质裸名界不解析**（R-22）: 界名裸写落 E-UNBOUND-NAME，唯一通道 = 限定父链（`traits.Functor`）——门内证。

机械证据脚本: `jsoncmp/scripts/t140-parity.ps1` 五段（traits 落位核对 / 68 门 t122n 冻锚台账 / 精确差分 vs t139 冻结 / 侧基线 / 确定性 + 脚本门），rc=0 全绿。

## t141 分账（R-24 v2：@mutable 注解 ≡ @mut @ref 接收者糖，2026-11-13）

**变更画像**（窄差分：唯一产品差 = stdlib list 件的 add 声明形态；零新增件；e2e 调用面零差分）:

| 门 | 结果 | 证据 |
|---|---|---|
| 精确差分（vs t140 冻结产物 `out-subs/t140gate`） | **67 e2e 件逐字节同** + 模块样本主件同 + 子件 7 同 / **list 件差 = add 声明形态** | t141-parity ③ |
| list 件差内容（窄差分） | t140 `anns=["mut"] purity=pure recv=HirMutType(HirNamedType(List))` → t141 `anns=[] purity=impure recv=HirMutType(HirRefType(List))`（形参 v→e）；ok 位同、件长 Δ+173 | t141-parity ③ + 台账 |
| e2e 调用面零差分 | `xs.add(…)` 调用位 calleeImpure 继承既有惯性（虚拟层路径）⇒ 67 件 + 主件逐字节同 | t141-parity ③ |
| 68 门（vs **t122n 死冻锚**） | SAME 51 + 剥差同 16（历史盲差）+ 结构差 1（模块样本——与 t140 同口径） | out-subs/t141gate-ledger.txt |
| stdlib 侧（vs t140stdlib） | 逐字节同 8 件 + 预期差 1 = list（声明形态变化） | t141-parity ④ |
| 套件 | **1158/1158**（1144 基线 + 14 T141MutableSugarTest；T139 门 2 断言随 stdlib 形态更新 1 处、类 doc 补 1 行——测试不减） | :frontend:test |
| 模块确定性 | 9/9 | t141-parity ⑤ |
| 脚本门 | 14/14 | t141-parity ⑤ |

**等价性机器证明**（T141 门 1）: `@mutable fun List[a].addx[a](e: a)` 与 `fun (@mut @ref List[a]).addx[a](e: a)` 同名同体双编译——HIR 子树 **pos/nid 剥后逐字节同**（机械口径同 jsoncmp 剥差同；注解表剥 "mutable"、接收者映 @mut @ref、purity=impure）。

**台账登记（新面/预期差/边界）**:

- **语言面新通道**: 接收者 @mut ⇒ impure（purity + 闸帧 enriched + FunFrame 同源）——self 访问免 E-MUT-CAPTURE 误伤（self=函数自带可变形参），纯函数真捕获判据零变（E-MUT-CAPTURE 保留）; @mutable 函数级注解提升（recvAst 包 @mut @ref + hirAnns 剥 mutable）; H-MUT-RECEIVER 提示（H 家族不拦）仅接收者 @mut @ref 写出形态发、函数级零提示;
- **调用方闸**: 方法糖/限定 ext 登记位（mutMethodCalls）+ @mutable 同位 ⇒ E-IMPURE-REQUIRED（虚拟层路径，enriched 表无 stdlib add ⇒ 单报无叠）——与 @mut 形态口径同源; 被调侧已 impure-by-decl 时 E-IMPURE-REQUIRED 让位纯度闸（免双报）; T3 接收者提升（R-06）@mutable 同位触发（T128 提升轮/形参视图回写面零回归）; calleeImpure 位虚拟层惯性保留（= t139/t140 口径，零调用面差分）;
- **@mut 函数级语义零变**（R-24 v2 纪律）: 纯度旗语义保持（purity=pure——T141 门 6 机器证）; @impure 注解/退化函数口径零变。

机械证据脚本: `jsoncmp/scripts/t141-parity.ps1` 五段（stdlib 形态核对 / 68 门 t122n 冻锚台账 / 精确差分 vs t140 冻结 / 侧基线 / 确定性 + 脚本门），rc=0 全绿。

## t142 分账（手动证明 by 证据链：契约位供给通道，2026-11-13）

**变更画像**（窄差分：唯一 e2e 产品差 = 手动证明样本冻红转绿；零新增件；stdlib/模块样本零触及）:

| 门 | 结果 | 证据 |
|---|---|---|
| 精确差分（vs t141 冻结产物 `out-subs/t141gate`） | **66 e2e 件逐字节同** + 仅 **手动证明转绿差**（ok false→true；Δ+58；by 诊断消失；模块样本主件+8 子件逐字节同） | t142-parity ③ |
| 68 门（vs **t122n 死冻锚**） | SAME **51→50**（手动证明从冻红集合除名 → 预期转绿=1）+ 剥差同 16 + 结构差 1（模块样本，t141 口径延续） | out-subs/t142gate-ledger.txt |
| 套件 | **1166/1166**（1158 + 8 T142ManualProofTest；存量零改） | :frontend:test |
| stdlib 侧（vs t141stdlib） | 逐字节同 9 件（by 通道不触 stdlib） | t142-parity ④ |
| 模块确定性 | 9/9 | t142-parity ⑤ |
| 脚本门 | 14/14 | t142-parity ⑤ |

**实现位点**（Elaborator.kt，两处；证明器零改）:

1. `ByExpr` 分支（手动证明供给链）：目标为**带上文角组（preps 契约位）的调用**时，by 证据逐条供给——每条 preps 须有单条证据经既有 `prover.proves` 判据证出；缺位 ⇒ **E-CANNOT-PROVE 精确报契约位**（不放宽为通过，负例 `by [q]` / 多位只供其一均红）；供给成功的证据条经既有 `injectFact` 注入当前块帧 Γ（下文弥散 Diffusion——ProofChecker 头注 V1 推迟项——不动的 by 通道手动形态）；
2. `block()` 块尾补 `flushByAsserts()` 落点：by 断言延迟核对原仅在函数体尾/文件尾，块内注入的事实（axiom/供给链）随块帧退栈丢失（上下文截断）——块尾核对使 byAsserts 的 Γ 完整（旧文件级/函数级行为对无 by 块文件零影响）。

**台账登记（新面/预期差/边界）**:

- **契约映射**：callee 解析复用调用点既有位（`lookupLocalFunDecl/funDecls` 同帧链口径）；preps 规范化复用声明位 `propNorm.prop(it, d.params)`；**零新增遍历、零改证明器主机制**（proves/render/快照复用）；
- **判据双轨保留**：契约案例（callee 有 preps）→ 契约位判据（失败位精确单一报——byAsserts 免叠报；成功位队列保留，供给条已入 Γ 由块尾复核 reflexive）；无契约目标（无 preps / 非调用 / 内建）→ byAsserts 旧口径（近证据从 Γ 核对）逐字零变；
- **负例先红**：`by [q]`（证据不足）/ `<p, r>` 只供其一 ⇒ E-CANNOT-PROVE；`go() by [q]`（无契约 Γ 无证）⇒ E-CANNOT-PROVE（旧口径）；
- **冻红集合变化**：手动证明样本自冻红集合除名（转绿登记：SAME 51→50 + 预期转绿 1 + 剥差同 16 + 模块样本 1 = 68 计数闭合）；
- **挂账**：调用点 preps 义务仍 V1 仅声明位登记（`consumeP()` 无 by 不拦——t9 既有口径）；下文弥散 Diffusion 依旧 V1 推迟（本批 by 通道为手动供给形态，非自动弥散）。

机械证据脚本: `jsoncmp/scripts/t142-parity.ps1` 五段（供给链落位正/负/旧口径 / 68 门 t122n 冻锚台账含转绿登记 / 精确差分 vs t141 冻结 / 侧基线 / 确定性 + 脚本门），rc=0 全绿。

## t143 分账（R-25 虚拟构造小写化 mutableListOf/listOf，2026-11-13）

**变更画像**（纯命名件：51 处改名 / 17 件；语义零变——值参形态 R-20 Alt A 不变；**e2e 导出 68 门零引用该构造族 ⇒ 零产品差**）:

| 门 | 结果 | 证据 |
|---|---|---|
| 精确差分（vs t142 冻结产物 `out-subs/t142gate`） | **76 件逐字节同**（68 门全家 + 模块主件+8 子件——纯命名件零产品差；诊断消息不含构造名 ⇒ 无消息差） | t143-parity ③ |
| 68 门（vs **t122n 死冻锚**） | **零波动**：SAME 50 + 剥差同 16 + 预期转绿 1（t142 手动证明除名口径延续）+ 模块样本 1（t141 口径） | out-subs/t143gate-ledger.txt |
| 套件 | **1166/1166**（改名 51 处后存量零 failures——语义零变的机器证） | :frontend:test |
| stdlib 侧（vs t142stdlib） | 逐字节同 9 件 | t143-parity ④ |
| 模块确定性 | 9/9 | t143-parity ⑤ |
| 脚本门 | 14/14 | t143-parity ⑤ |

**改名面（实测扫描为准）**:

- **主码 2 件 / 6 处**: PreludeDecls.kt（ctorFun 定义 2 + 头注注释 2 + R-20 注释行）、SymbolTable.kt:466 注释（2 处旧名引用）；
- **Kotlin 测试 10 件 / 45 处**（任务书估 32 处，实测 45 处——命名面含文档注释: T126ContainerFaceTest 11 / T128BorrowSemanticsTest 8 / T141MutableSugarTest 5 / T118RoundsTest 4 / T139StdlibExtTest 3 / T129NidGuardTest 3 / T129UncheckedBlockTest 2 / T129LocalFunAnnotationsTest 2 / T129BorrowArgFilterTest 1 / T125 系列经 diff-pairs 件间接）；
- **diff-pairs 6 件 / 6 处**: borrow-neg-plain（.subl/.subs）、borrow-shared（.subl/.subs）、mut-container（.subl/.subs）；
- **stdlib 实测 0 处**（任务书登记"1 处"未复现——全 stdlib 树 grep 零命中；任务前面测或指 PreludeDecls 旁注，本批按实测零改 stdlib 体）；
- **archive/ documents/ 历史件不改**（日志/计划属历史记录，t143 范围=活码面）。

机械证据脚本: `jsoncmp/scripts/t143-parity.ps1` 五段（落位核对+双探针（mutableListOf/listOf 绿地）/ 68 门 t122n 冻锚零波动台账 / 精确差分 vs t142 冻结全同 / 侧基线 / 确定性 + 脚本门），rc=0 全绿。

## t144 分账（R-26.1 括号语义统一：[] 类型实参 + <> 命题参数，2026-11-13）

**变更画像**（调用位类型实参 `<>` → `[]`；<> 划归命题通道；声明位 `[T]` / 类型位 `[]` / 命题面零动）:

| 门 | 结果 | 证据 |
|---|---|---|
| 精确差分（vs t143 冻结产物 `out-subs/t143gate`） | **75 件逐字节同** + 1 件差（上下文弥散：`witness<tagged<7>>()` 调用位因 E-CALL-ANGLE 误红——t148 已修复逐字节回锚 | t144-parity ③ |
| 68 门（vs **t122n 死冻锚**） | SAME **49** + 剥差同 16 + 预期转绿 1（手动证明 t142 口径）+ 模块样本 1（计数闭合 68）；**4 处括号糖迁移样本维持 SAME**（Channel×2、Cons×3 簇）——纯语法糖零 HIR 影响已证。**⚠ 后记（t148 撤账）**：本表"预期转红 1（上下文弥散）"登记经队长验收查 t122n 冻锚证为事实错误（witness<谓词>() t122n 时已绿——callee theory 含 PropParam，按 R-26 是正确形态）——该登记**除名**，复绿修见 t148 分账（SAME 口径回 t143 基线） | out-subs/t144gate-ledger.txt |
| 套件 | **1177/1177**（1166 + 11 T144：门1 方括号×3 / 门2 负例×3 / 门3 迁移样本×3 / 门4 零回归×2） | :frontend:test |
| stdlib 侧（vs t143stdlib） | 逐字节同 9 件 | t144-parity ④ |
| 模块确定性 | 9/9 | t144-parity ⑤ |
| 脚本门 | 14/14 | t144-parity ⑤ |

**实现位点**（三主码件）:

1. **ast/Expr.kt**：`InstExpr` 增 `bracket: Boolean = false` 标记（括号来源——true = 调用位方括号 `f[T](args)`；false = 尖括号命题通道 `p<v>`、`f<p>()`；数据类默认参数，既有构造位命名化 `pos=` 兼容）;
2. **parser/Parser.kt**：新增 `parseBracketTypeArgList()`（`[` 起的实参表，RBRACKET 终止，空表拒收落 E-SYNTAX 精确报）+ parsePostfix 后缀链新增 **LBRACKET 分支**（同 D-1 角表通道的失败回滚口径——游标/诊断/留痕一并还原）；既有 LT 分支注释改为命题通道口径；
3. **semantic/Elaborator.kt** 调用位 B8 分型：`bracket=true` → 既有 explicitTps 类型域读取通路（**零改**）；`bracket=false` → **E-CALL-ANGLE 精确 E 级**（消息含命题通道口径 + `callee[…]` 方括号迁移指引 + `callee() by [命题]` 断言指引）+ 降级为普通调用（不复读类型实参——单点精确报）。

**迁移面**（调用位 `<>` → `[]` 机械迁移）:

- **e2e 样本 3 件 4 位点**（任务书登记）：日常-异步任务:8（`Channel<Nat>(4)`→`Channel[Nat](4)`）、日常-订单状态机:87/92（`Cons<OrderEvent>(`×6 簇）、异步与并发:15（`Channel<Nat>(1)` + 注释口径 `Channel[T](cap)`）；
- **Kotlin 测试件**：ExplicitTypeArgsTest（`id<Nat>(`×5、`Channel<Nat>(`×4 + 注释）、LambdaInferenceTest（`mapResult<Nat, Str, Bool/Str>(`×2）、PreludeDeEnumTest（`Channel<Nat>(`×4）、R38ToleranceSurveyTest:70（`Channel<Nat>(0)`——迁移后 M2b 容忍面断言口径不变）；
- **嵌套角表漏项发现**（任务书 4 位点实测漏项——正则 `[^>]*>` 不跨嵌套尖括号）：**上下文弥散.subl:37 `witness<tagged<7>>()`**（t22 B6 命题供给位形态——callee theory 含 PropParam）——队长验收查 t122n 冻锚证其 t122n 时已绿（任务书"调用侧命题供给未通"为事实错误 F3，记队长账）：**该形态按 R-26 是正确形态**，复绿修见 t148 分账（R-26.1 收口——本分账"预期转红"登记撤账，SAME 口径回到 t143 基线）；
- **零改位**：声明位 `[T]`、类型位 `[Nat]`、命题面（preps/posts 槽、`<p>` 命题括号、`p<v>` 谓词应用、`¬<p>`、by 断言、axiom/∴ 命题列表）逐字零变。

机械证据脚本: `jsoncmp/scripts/t144-parity.ps1` 五段（行为探针正/负 / 68 门 t122n 冻锚台账含双登记 / 精确差分 vs t143 冻结 / 侧基线 / 确定性 + 脚本门），rc=0 全绿。

## t148 分账（R-26.1 收口：调用位 <> 路由回既有命题参数供给通路，t144 验收退件修，2026-11-13）

**变更画像**（t144 验收退件：队长查 t122n 冻锚发现 witness<谓词>() 在 t122n 时已绿——callee 声明 theory 含命题参数 PropParam，调用位 <> 是命题供给的正确形态；任务书"调用侧命题供给未通"为队长事实错误，记队长账 F3）:

| 门 | 结果 | 证据 |
|---|---|---|
| 68 门（vs **t122n 死冻锚**） | **SAME 50**（含**上下文弥散复绿逐字节回锚**）+ 剥差同 16 + 预期转绿 1（手动证明）+ 模块样本 1（闭合 68）——**t144 预期转红撤账** | out-subs/t148gate-ledger.txt |
| 精确差分（vs t144 冻结产物 `out-subs/t144gate`） | **75 件逐字节同** + 唯一登记差（上下文弥散复绿——t144 误红件的回滚） | t148-parity ③ |
| 套件 | **1184/1184**（1177 + 7 T148：复绿×3 / 保留拒×2 / [] 零回归×2；T144 11 测试全绿——无过截，负例 callee 均无命题理论参数） | :frontend:test |
| stdlib 侧（vs t143stdlib） | 逐字节同 9 件 | t148-parity ④ |
| 模块确定性 | 9/9 | t148-parity ⑤ |
| 脚本门 | 14/14 | t148-parity ⑤ |

**实现位点**（一处，零改通路）:

- **semantic/Elaborator.kt** 调用位 B8 分型收口：`bracket=false` 时先查 callee 命题理论参数 `funDecls[nm]?.theory?.any { it is PropParam }`（AST PropParam = HIR HirPropParam 同源判据，t22 B6 既有通路 L2071 同法）；**有**则按 t122n 既有通路供给（按名解析 + HirNameRef + explicitTps 类型域读取——与 bracket=true 同一通路，RANGE 只改 <> 侧路由）；**无**才保留 E-CALL-ANGLE 精确 E 级（指向方括号迁移 + by 断言）。**[] 通路与样本零改**（样本本身按 R-26 是正确形态——witness<谓词>() 不迁移）。

**判别性**（router 边界）:

- 复绿面：callee theory 含 PropParam（`fun witness[@q]<q>` 形态——上下文弥散样本）；
- 保留拒面：纯类型参数 callee（`fun id[T]`）、prelude 虚拟构造（Channel）、preps 契约形态（`fun witness<p>`——契约供给走 by 断言，t142 口径）——三者 theory 无 PropParam ⇒ E-CALL-ANGLE 精确报。

机械证据脚本: `jsoncmp/scripts/t148-parity.ps1` 五段（行为探针正/保留/[] / 68 门 t122n 冻锚台账含上下文弥散复绿回锚 / 精确差分 vs t144 冻结（75 同+登记差） / 侧基线 / 确定性 + 脚本门），rc=0 全绿。

**队长令裁定挂账**：声明侧 `[@q]`（命题参数在方括号内）与 R-26 的 `<>` 口径相悖——队长裁为**语义层裁决项**（不代决），本件不碰声明侧（上下文弥散:24 `fun witness[@q]<q>` 维持原样复绿）。

## t149 分账（R-26.2 注解纪律：未知注解 E-UNKNOWN-ANN + 错位 E-ANN-POSITION + 合法位注册表，2026-11-13）

**变更画像**（旧状=未知注解静默忽略：值通道（声明/成员/语句/var/形参）注解收集后零校验；本件落注册表逐注解校验）:

| 门 | 结果 | 证据 |
|---|---|---|
| 68 门（vs **t122n 死冻锚**） | **零波动**：SAME 50（含上下文弥散）+ 剥差同 16 + 预期转绿 1 + 模块样本 1（闭合 68）——注解纪律零产品差（样本/stdlib 全用合法位形态） | out-subs/t149gate-ledger.txt |
| 精确差分（vs t148gate 一次性基线） | **76 件逐字节同**（e2e 全家 + 模块主件 + 子件） | t149-parity ③ |
| 套件 | **1197/1197**（1184 + 13 T149：未知×4（声明/语句/形参/类型位） + 错位×4（impure var/list 声明/impure struct/impure 非函数类型） + 承接×2（@ref 形参既有口径/unchecked 语句前缀零回归） + 合法形态全绿 + @ref var 视图位零回归） | :frontend:test |
| 负例先红 | 实现前 8 红（未知名静默放行/错位无报）+ 4 绿（既有承接）；实现后 13/13 绿 | T149 测试轮 |
| stdlib 侧（vs t143stdlib） | 逐字节同 9 件 | t149-parity ④ |
| 模块确定性 | 9/9 | t149-parity ⑤ |
| 脚本门 | 14/14 | t149-parity ⑤ |

**实现位点**（Parser.kt 一件，zero 新依赖）:

1. **`AnnPositions` 注册表**（private object）：通道 × 合法位表——FUN={impure,root,classic,async,mutable,mut,theorem,axiom}；STRUCT={mut,classic}；TRAIT={classic}；VAR={mut,ref}（ref=t97 共享只读视图，样本实证）；PARAM={list}；FIELD={mut}；TYPE={mut,atomic,ref,impure}（parseType 既有纪律承接同源表）；ALL_OK hint + 每注解合法位文案（POS_HINT）。
2. **`parseAnnotations(allowed, kindLabel, toksOut?)`** 升级：逐注解即时校验 + AT 位透传（两段式通道——注解在前、种类关键字在后的位点用 `checkAnnotations` 批量回补）。
3. **五处通道收口**：parseEntry（fun/struct/trait 分种类校验）/ parseTrait 成员（特质成员函数位）/ parseStmt（var/fun 分通道）/ 初等式 var 表达式位 / parseParams（形参位 vs 结构体字段位分通道——`isField` 形参）+ snippetDecl 片段前置注解（合并后按种类校验，防双报）。
4. **两码**：`E-UNKNOWN-ANN`（无此注解 @x + 全注册表文案）；`E-ANN-POSITION`（@x 不能写在〈通道〉+ 该注解合法位文案）。parseType 既有 errType 升级挂同族码（类型位未知→E-UNKNOWN-ANN；@impure 非函数类型→E-ANN-POSITION）。
5. **承接不变**：@ref 形参名前既有 E-SYNTAX 口径逐字（t95；ref 不入注册表校验防双报）；策略语句 `@x[…]`（证明策略通道，非注解）与 `return @label`（标签）零触及；@unchecked 是语句前缀关键字（写 @unchecked 注解落既有「期望注解名」E 级）。

**注册表表（文档面）**:

| 注解 | 合法位 | 消费面 |
|---|---|---|
| @impure | 函数声明位（含局部 fun/特质成员）/ 函数类型位 | 纯度闸帧（Elaborator）|
| @mutable | 扩展函数声明位（接收者存在） | @mut @ref 接收者纯糖（R-24 v2，t141）|
| @root | 函数声明位 | 声明口径纯度内建表 |
| @classic | 声明位（fun/struct/trait） | 经典逻辑挂载 |
| @async | 函数声明位 | 异步标记 |
| @theorem | 函数声明位 | 定理表（Collector）|
| @axiom | 函数声明位 | 后件公理登记 |
| @mut | 类型位 / var 位 / struct 声明位 / 结构体字段位 / 扩展函数+特质成员 | 可写性视图/容器可变/方法糖 |
| @ref | 类型位 / var 位（共享只读视图，t97） | 可写性/拷贝规则 |
| @atomic | 类型位 | 原子类型 |
| @list | 形参位（末位变长） | 变长实参 codegen |
| @unchecked | **非注解**——逃逸位是语句前缀 `unchecked <stmt>` | 逃逸闸 |

机械证据脚本: `jsoncmp/scripts/t149-parity.ps1` 五段（行为探针正/负/合法面 + 68 门 t122n 冻锚台账 + 精确差分 vs t148gate 一次性基线 + 侧基线 + 确定性 + 脚本门），rc=0 全绿。

## t146 分账（R-26.5 / B2.1 构造函数正道：mutableListOf/listOf 落 stdlib 源 + 虚拟构造 retire，2026-11-13）

**变更画像**（旧状=虚拟 ctor（PreludeDecls 单参 first: T 签名）：零参/多变参形态落 E-ARITY/缺实参；构造族 e2e 零引用——产物零差预期）:

| 门 | 结果 | 证据 |
|---|---|---|
| 68 门（vs **t122n 死冻锚**） | **零波动**：SAME 50 + 剥差同 16 + 预期转绿 1 + 模块样本 1（闭合 68）——构造族 e2e 零引用⇒零产品差实证 | out-subs/t146gate-ledger.txt |
| 精确差分（vs t149gate 一次性基线） | **75 件逐字节同 + 登记差 1 件** = stdlib list 件 envelope（新增 mutableListOf 声明 + listOf 去体签名化；ok=true 不变）——案内预期迁源面 | t146-parity ③ |
| 套件 | **1206/1206**（1197 + 9 T146：零/单/多变参 + 三形态 + 推导 + 混型精确报×2 + Cons 自拼 + 混用） | :frontend:test |
| 负例先红 | 实现前 6 红（零参/多变参/listOf 三形态/混型×2/混用=虚拟 ctor 旧口径）+ 3 绿（单参/推导/Cons 承接）；实现后 9/9 绿 | T146 测试轮 |
| stdlib 侧（vs t143stdlib） | 8 件逐字节同 + 登记差 1 件（list 件构造族迁源面） | t146-parity ④ |
| 模块确定性 | 9/9 | t146-parity ⑤ |
| 脚本门 | 14/14 | t146-parity ⑤ |

**实现**（数据源替换纪律同 B0/B1/B2——机制零改，只换数据源）:

1. **stdlib 源**（`stdlib/collection/list/list.subl`）：`fun mutableListOf[T](@list e : T) : @mut List[T]` + `fun listOf[T](@list e : T) : List[T]`——@list 变长形参（t48/ADR-32 已存机制：末位吸收 0..n 实参）；体随「运行时桥规格」（R4）后续件落（同 B2 扩展方法纪律——签名面，不写结构性体）。
2. **retire**（PreludeDecls）：`FUNS` 虚拟 ctor 表清空（ctorFun 私有构造件删除）+ 新增 `RETIRED_CTOR_FUNS` 允许表 = {mutableListOf, listOf}（B1/B2 同形表：RETIRED_STRUCTS/ALIASES/EXT_METHODS 族第 4 件）。B0-0 数族表（RETIRED_FUNS=range/sum/summation）口径不动（t137）。
3. **注入面**（构造族解析通道逐字承接 t126 FUN-kind——非 BUILTIN 通道）：Elaborator init — stdlib 快照经 RETIRED_CTOR_FUNS 过滤注入 `funDecls` 种子位 + SymbolTable `virtualFuns` 改注入面（`declareModuleCtorFun`——同 B2 `declareModuleExt` 纪律；preludeVirtualFun 懒登记 id 序零漂移）。⇒ 符号解析走 sigCheckedCall（@list 变长旗标 + 别名展开（List→Nil|Cons 联合）+ 理论参数解），T126 R15 两面（@mut 视图/联合展开）逐字保存。
4. **调用面**：零参（空变长回退 synthetic，t48）/单参/多变参（元素逐一 unify，首解为准）皆绿；混型实参（mutableListOf(True, 1)）落 E-TYPE-MISMATCH（元素类型精确报，非元数错位）。
5. **Cons 自拼合法**（R-26.3 不作诊断）：Cons(1, Nil) / 与构造族混用零回归。

**一处自纠**：首轮将构造族改走 preludeFunSig/BUILTIN 通道（preludeFun/preludeFunSig 加变长旗标+理论参数透传）——T126 R15不可变构造红（listOf(2) 的 List→Nil|Cons 联合展开丢失：BUILTIN 通道不展开别名）⇒ 回到注入面 FUN-kind 通道（数据源替换口径，机制零改），preludeFunSig 逐字还原（B0-0 零漂移）。

机械证据脚本: `jsoncmp/scripts/t146-parity.ps1` 五段（行为探针正/负 + 68 门 t122n 冻锚台账 + 精确差分 vs t149gate 一次性基线（list 件登记差校验：须含 mutableListOf 声明）+ stdlib 侧基线（list 件登记差）+ 确定性 + 脚本门），rc=0 全绿。
