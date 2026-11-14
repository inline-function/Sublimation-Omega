# backend-js（Sublimation JS 后端 V2）

HIR JSON v3 → JavaScript 产物：源代码 → 词法单元流 → AST → 语义树(HIR) → **JS 生成**。
本包为仓内首个 JS 包，无 pnpm/workspace 基建，故采用 **plain JS ESM（.mjs）+ JSDoc**；
目录结构按提案保留（src/emit、gate、generate、run、verify），TS 置换时可原地改名升级。

## 运行

- 前置：F:jdk-17.0.13.11-hotspotinjava.exe + frontend/build/install/frontend/lib/* 的 CLI 编译产物；
  node v24+ 直接可用（无 npm/network 依赖）。
- 编译样本（复用既有产物）：F:Sublimationjsoncmpout-subs	162probe*.json
- 生成：node src/generate.mjs <envelope.json|目录> <outdir>
- 直跑：node <outdir>/<unit>.mjs（产物目录自包含 prelude.mjs）
- 验收（守门负例先红 + golden 对拍 + 差分台账）：
  node src/verify.mjs [products目录]

## V1 子集（已实现）

算术 + 容器 + print 自足子集：值与字面量、变量与绑定、函数与调用形式、模式匹配
（含卫条件/元组/嵌套解构/穷尽兜底）、struct/枚举（tagged 对象 + 字段默认值）、
when/IIFE 级联（else-if 链）、lambda、trait 扩展方法（receiver 首参 self）、
命题/类型族擦除零码（编译期槽位）、print 渲染（Array → [a, b]、bool → true/false、
number → String、tagged → Name(f1, …)）。

- 输入面守门（gate.mjs，先行）：schema=sublimation/hir@v3、compiler、unit{path,mode}、
  ok===true（诊断件不进运行时）、diagnostics 数组、hir 根=HirFile、entries 数组、
  **Error 族节点全扫拒收**、envelope 非对象拒收——守门负例 5/5 先红（见 verify 输出）。
- 发射面（emit.mjs）：94 kind 知名表（翻译/擦除/拒收/异步桩四态）；
  **未注册或未解析引用一律报清单、不准静默**——未解析名落 dynamicStub（运行时抛 +
  静态告警清单）；38 件 e2e 产物全量通过守门+发射（0 EMIT-REJECT）。

## V2：深拷引擎（R4 §2.3 / §2.4 兑现）

- **prelude 原语 P.deepcopy**：对象图 DFS + visited 表——同一次拷贝内已访问节点复用同一副本
  （保共享、保环）;栈深上限 DEEP_MAX（成本面与 W-DEEP-COPY-LOOP 同源，部分/惰性拷=V3 CoW 域）。
  标量/字符串/函数透传（函数值真拷=零成本透传——e2e lambda 样本 golden 实证）;Array 子类保原型
  （MutableList 形态）;deepCopyCount 仅审计（R-29，经 P.deepCopyCount 只读观测）。
- **八站点发射（emit.mjs）**：R4-A0 notes（code=D-DEEP-COPY）+ deepCopy 布尔单源信任，
  生成 $S.deepcopy(...) 调用——var 初始化 / 赋值 / 形参默认值 / call 实参（caller 侧，
  "实参 N" 按 message 定位;方法糖 receiver 首参透传不拷）/ 构造子字段 / return 值 / 函数体尾。
- **@mut/@ref 兜底（判据 B）**：@mut var x = y（值迁移型初值）兜底拷；@ref 视图槽零操作同指。
  必要性：前端 TypeInfer 的 var 初始化拷判定（varAnnotated）只吃标注类型、不合并 var 前缀的
  @mut/@ref——R4-A0 notes 在 @ref var d = c（应为别名）照发 deep、@mut var t = s（应为深拷）
  反不发（诚实登记，另报队长；后端按标注兜底保 R-28 语义）。
- **闭包创建快照（§2.4）**：lambda 求值=emit 期预收自由变量，可变复合槽位（编译期槽位表：
  var/param 的 @mut/@ref/标量性）创建时深拷入 env（(__snap => (params => body))({…}）);
  标量捕获=直播（JS 词法域重绑定可见;e2e lambda 样本 golden 实证）;@ref 捕获=共享透传。
- **MutableList 模型**：MList extends Array（OQ-9 Array 值模型不变），.n=当前持有值（末元素）
  ——golden 派生语义（e2e 特性巡礼 print(a.n) 注释预期 0/1 实证），非列表正规字段。
- **golden 抽取升级（verify.mjs）**：行尾注释剥离（…）后按值样判据（数/布/列表渲染/ctor 形/
  无空白分号 CJK 单 token）取 golden;非值样与无注释 print 行回落字符串字面量（print("done") 之属）;
  纯字面回落=无预期注释（NO-GOLDEN,不误判 FAIL）;print 跨多函数声明体的样本（声明位序≠执行位序）
  按多重集对拍（单体保序）。

## kind 计数锚定（权威面）

- **e2e 单文件导出面（本验收权威）：38 产物 walkAll 全扫命中 53 种 kind**（含 hir 根节点;
  t162 记 52 为未计根口径——产物面未变），全部在 94 kind 知名表内（0 EMIT-REJECT）。
- 编译器全量门（t160gate，77 件，含目录/stdlib 件）：60 kind。
- 差异在 README 本节写清：门内多出的 HirRefType、HirTStr 不出现在 e2e 自足样本中，
  且二者均为擦除态（编译期事实），不影响 V1 生成面。
- 早期口径「e2e=58 / gate=60」为正则漏数（HirPExists1 的数字后缀需计入）修正前的值；
  本验收以 walkAll 全扫计数为准（52=e2e 实命中，已去重）。

## 验收台账（V2 收口：node src/verify.mjs）

- PASS 29 / FAIL 3（全部范围外：V3-async×2、非确定×1）/ 无预期注释 6（纯字面回落件，
  与 t162 同 6 件：_probe_div/_scratch_bill/日常-数列与区间/日常-温度与BMI/日常-账单统计/脚本求值桥）。
- **V2 道标转绿（t162 FAIL 4 件 → PASS）**：
  - lambda与闭包（闭包捕获直播/函数值透传 + 字面回落 done）
  - 副作用与unchecked（call 实参拷隔离 + 多体多重集）
  - 特性巡礼（@mut 深拷/@ref 别名/闭包快照 7 + MList.n）
  - 类型系统（@mut var 兜底拷 + @ref 别名 [5,1]）
- 前后差分（vs t162 台账）：PASS 25→29（+4 全为 V2 道转绿）;其余 PASS 件零回归;
  范围外 FAIL 3 维持（异步×2、非确定×1）。
- 负例先红：深拷站点红态=V1 台账（四件 FAIL@V2-deepcopy 道实证）;守门负例 5/5 保持。

## 验收台账（V1 收口：node src/verify.mjs）— 存档

- PASS 25 / FAIL 7（全部为**范围外道标**）/ 无预期注释 6（_probe_div/_scratch_bill/日常-数列与区间/日常-温度与BMI/日常-账单统计/脚本求值桥——本批无预期注释件）。
- 失败 7 件分道：
  - V2-deepcopy：lambda与闭包（闭包捕获快照）、副作用与unchecked、特性巡礼、类型系统（@mut 拷贝矩阵）
  - V3-async：异步与并发、日常-异步任务（Channel/Task/Flow=报错桩）
  - 非确定：字符串与插值（timeNow/rand，预期逐行不稳定）
- 注意：可变性控制、高阶函数与lambda 虽分 V2 道，V1 语义下 stdout 已对上（提前 PASS）。
- 脚本求值桥（sublEval）为 V1.5 道。

## OQ 状态（提案 documents/JS后端-设计提案.md 决策）

- OQ-9：List 值模型=JS Array（Cons(h,t)=[h,...t]、Nil=[]、mutableListOf=数组）——
  prelude 模块态分派面兑现；e2e 自足样本中 Nil/Cons 为本单元声明 struct（tagged），
  本单元声明优先（未声明时回落 prelude 数组形/构造子形）。
- 其余 OQ 按提案倾向默认（@ref 透传、深拷落 V2、异步落 V3）。

## 范围外（V3+ 不做，已在台账列因）

CoW（V3）、异步面（V3）、bundle、LSP、R-06 修件（另任务独立域）。**深拷引擎 V2 已落**（下节）。

## 文件

- prelude/prelude.mjs：V1 运行时（渲染/字符串/JSON 族 tagged Option/List 构造族/
  异步面 asyncStub 桩/dynamicStub）；统一以 P 命名空间聚并导出。
- src/gate.mjs：输入面守门；src/emit.mjs：发射器；src/generate.mjs：生成驱动；
  src/run.mjs：node 执行器；src/verify.mjs：验收器（负例 + golden + 台账）。
- out/：验证期生成件（自包含）；snapshots/：stdout 快照；ledger.txt：差分台账。
