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