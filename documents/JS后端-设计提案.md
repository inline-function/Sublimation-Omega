# JS 后端设计提案（HIR JSON v3 → 可直接跑的 JS）

> **本件是提案**（大件规矩：设计 → 队长审 → 用户拍板 → 才实现）。**零实现代码**——前端、stdlib、
> 套件、脚本门一律不动;产出仅 `documents/JS后端-设计提案.md`。

## 0. 定位与裁决对齐

- **用户裁决（α 路线）**：先做 JS 后端、生成**能直接跑的 JS**——后端配 JS 运行时 prelude，承接
  stdlib 体空的债。
- **工程形态（OQ-1 已定 C 路）**:TS 包，读 HIR JSON v3 为唯一输入（D-6/D-8），复用 jsoncmp
  工具链与对拍纪律;包目录 `backend-js`（S-2 §1 建制留痕，见 §1.3）。
- **与 R4 后端提案的关系**:R4 的 V1「解释器」分期被本提案替换为 **codegen 直出 JS**——R4 的
  深拷引擎（§2.3/§2.4）、值模型表、借用透传、CoW 目录、R-06 三态影响分析全部承接不变;
  本提案补的是「JS 宿主上怎么落」。R4 的 V4 codegen 列由本提案提前到首期。
- **本提案不替用户裁决**:R-06（三态）、OQ-3 CoW 兑现期、异步面——只写影响。

## 1. 工程形态（C 路 TS 包）

### 1.1 输入与工具链

- 输入 = **envelope JSON 数组**（schema v3;HirSchema 守门复用 T158 形态:缺字段/版本不符/非对象即
  拒收;**只收 ok=true 件**——ok=false 件代码输出对错误恢复产物无定义，见 §2.8）。
- 输出 = **每 unit 一个 `.mjs` ES module**（+ 一个 `prelude.mjs` + 依赖清单），node 直跑（`node
  out/main.mjs`），bundle 为单文件 **后置**（可选 esbuild;V1 直跑多 module 即可验收）。
- 语言/工具：TypeScript（strict），`pnpm` workspace 复用根仓;产物零依赖（prelude 纯手写）。
- 复用 jsoncmp 纪律：golden 输出文件 + 差分台账 + 守门负例（详见 §7）。

### 1.2 单态化在 codegen 路线下无义（对 OQ-1 的论证）

P-11 单态化裁决（多态丢失即拒 vs C-2 推翻）在宿主为 JS 时**不影响产物形态**：

| 裁决态 | JS codegen 产物 | 说明 |
| --- | --- | --- |
| 单态（现行） | 动态分派 JS | HIR 已无类型参数残留（推导期已解/拒）;emitted code 天然 uniform |
| C-2 多态救援 | 同左 | JS 值=动态标签，List[Nat] 与 List[Str] 同走 Array;分派发生在运行时 |

静态特化（R4 §5.2 给 Kotlin 路的收益——具体形参类型 ⇒ 直传/内联候选）在 JS 上收益面为零:
JS 引擎的隐藏类/内联由**实际值形态**驱动，与编译期类型标注无关，且 HIR 中类型已被擦除（§2.6）。
⇒ **本路线不等 OQ-1 裁决**，两种裁决同产物。

### 1.3 建制与留痕（S-2 §1）

- 包名 `backend-js`，目录：`backend-js/{src,test,scripts,snapshots}`;前端既有模块零改动（构建
  依赖只读 jsoncmp 的产物目录，无 Gradle 依赖）。
- 启用纪律同 R4:新包登记入 S-2 §1 表的时机=用户拍板本提案后、实现首期开工时。

## 2. 94 kind → JS 映射策略

### 2.1 总策略：表驱动 + 按族分层 + 编译期擦除三态

94 kind 按 schema v3 §3 分九族。映射分三类：**翻译**（产 JS 语句/表达式）、**擦除**（编译期
事实，零运行时代码）、**拒收**（错误恢复通道）。全表 `kind → emitter 函数` 注册表驱动——新增 kind
= 未知字段忽略（加性纪律）但**未注册 kind 报错**（不静默丢节点;守门负例覆盖）。

实发面 vs 全集面：t160 门产品 77 件实发 **59 kind**(族覆盖完整但部分 kind 零命中，如 HirLocalFun/HirTask）;
表按 94 全集编写、以实发 59 为 V1 验收面，未发 kind 留 emitter 占位（未命中不阻塞）。

### 2.2 声明族 + 文件辅助（12 kind）

| kind | 映射 | 备注 |
| --- | --- | --- |
| HirFile | 顶层 module 骨架：imports + 逐 entry | entries 两通道：Decl/Stmt |
| HirFunDecl | function 声明 + export | 理论参数已解 ⇒ 泛型零残;posts/proofContext 擦除 |
| HirStructDecl | ctor 工厂注册 `__ctors[name]` | 运行时=tagged 对象构造器（§3.1） |
| HirTraitDecl / HirTraitConst | 擦除（分派表在 prelude，§3.4) | trait 方法体空 ⇒ prelude 分发 |
| HirTypeAlias | 擦除 | 类型=编译期事实 |
| HirTypeParam / HirPropParam | 擦除 | 推导期已解 |
| HirParam | 形参绑定（含默认值 `p = <expr>`） | 默认值深拷站点见 §5 |
| HirWhenArm / HirWhenPropArm | when 分支生成 | 命题臂擦除（§2.6） |
| HirErrorDecl | **拒收** | §2.8 |

### 2.3 语句族（15 kind）

| kind | 映射 | 备注 |
| --- | --- | --- |
| HirVarStmt | let/const 声明（或解构） | deepCopy=true ⇒ 包 P.deepcopy（§5） |
| HirAssign | 赋值（变量/字段/下标位） | 同上 note 触发 |
| HirExprStmt | 表达式语句 | |
| HirReturn | return（含值） | |
| HirLocalFun | 局部函数提升 | |
| HirIf / HirWhen | if 语句 / 级联分支 | when subject 无副作用可直翻 |
| HirBlock | 块作用域 | |
| HirAxiom / HirByStmt / HirBecause / HirWhenProp / HirIfProp / HirExistsIntro / HirExistsElim / HirStrategy | **擦除**（命题面/证明策略面） | 用户倾向：命题槽=编译期;ok=true 前提下运行时零码 |
| HirUnchecked | 擦除（信任闸编译期已闭） | 产物已过闸 ⇒ 无运行时检查 |
| HirErrorStmt | **拒收** | |

### 2.4 表达式族（26 kind）

| kind | 映射 | 备注 |
| --- | --- | --- |
| HirIntLit / HirFloatLit / HirStrLit | JS 字面量 | 数值塔见 §3.1 |
| HirTop / HirBot | true / false | 布尔双射 |
| HirNameRef | 词法绑定或符号解析（§2.7） | |
| HirBinExpr | 按 op 表映射（算术/比较/逻辑） | 除法恒 Rat:JS / 同形态 |
| HirUniExpr | 按 op 表（负/非） | |
| HirCall | 符号解析分派（§2.7） | methodSugar:args[0]=接收者 |
| HirField | 字段访问或方法表键 | |
| HirFunRef | 函数引用 | |
| HirLambda / HirAnonFun | 箭头函数 + env 捕获 | **创建时深拷快照**（§5.2） |
| HirTuple | JS 数组 | |
| HirStructCtor | `__ctors.Name(f1, …)` | |
| HirByExpr | 擦除（证明计量） | |
| HirVarExpr | 变量值通道（脚本 var 表达式） | |
| HirTypeTest | 运行时 ctor 标签判定 | |
| HirTask / HirFlow / HirInst | **V1 报错桩**（遇即抛「异步面未实现」) | 不静默放行（R4 §2.5 同源） |
| HirBorrow | **透传**（x 本身;@ref=类型层事实，OQ-4 倾向） | 零包装 |
| HirErrorExpr | **拒收** | |

### 2.5 模式族（8 kind,when/解构臂内）

| kind | 映射 |
| --- | --- |
| HirPatLit | 常量相等比较 |
| HirPatCtor | ctor 标签判定 + 子模式递归 |
| HirPatTuple | 数组长度 + 逐项 |
| HirPatIs | 类型判定（typeof / 标签） |
| HirPatBind / HirPatElse | 绑定 / 兜底 |
| HirPatCond | 模式 + 守卫表达式 |
| HirErrorPattern | **拒收** |

### 2.6 类型族 9 + 命题族 11 + 命题项 13 = 全擦除

- **类型族**(HirNamedType/FunType/TupleType/MutType/AtomicType/RefType/UnionType/QualifiedType/ErrorType):
  编译期事实 ⇒ **零运行时代码**;唯一保留用途=MutType/RefType 的**受体类型名**作深拷/方法分派表键（§3.4/§5）。
- **命题族 + 命题项**(24 kind)：posts/proofContext/verdict 与 axiom/by/when-prop/exists/iff 全部编译期——
  **用户倾向=擦除**（命题槽不进运行时;trust 面已闭于 ok=true 门）。
- 依据同 R4 §2 与效应分析提案——命题是编译期契约，运行时无义务。

### 2.7 符号引用 13 族 → 解析键（codegen 核心表）

HIR 符号 `{id, kind, name}`(id 权威、kind/name 冗余可读，schema v3 §3):

| symbol kind | 出现位 | JS 解析 |
| --- | --- | --- |
| builtin | HirNameRef(callee) | `prelude.<name>`——71 内建按 kernel 内建面规格 §2 六组（§3.2） |
| fun | HirNameRef / HirCall | 词法域 → 当前 module export → 挂载 module import(§4) |
| ctor | 结构/枚举 ctor | `__ctors[name]` / union ctor 工厂 |
| field | HirField | 直接属性访问（字段名保序，§3.1) |
| local / param | HirNameRef | 词法绑定（闭包捕获=JS 天然） |
| struct / enum / alias / propdef / theorem / trait / trait_const | 类型/命题/分派面 | 擦除或分派表键 |

**方法糖分派键**:`methodSugar=true` 的 HirCall(symbol.kind=fun)+ 接收者运行时标签 ⇒ prelude 分派表
(§3.4);限定调用 `HirField(target=HirNameRef{symbol:null})` ⇒ 模块别名解析（§4）。

### 2.8 Error 族（5 kind)=拒收门

HirErrorDecl/Stmt/Expr/Type/Pattern 为错误恢复产物——**ok=true 的件不应含**;codegen 遇 Error 节点即
整体拒收该 unit（诊断报告定位），与「只收 ok=true」双保险。

## 3. JS 运行时 prelude 边界

### 3.1 值模型（JS 表示——承接 R4 §2.3 表，带 JS 宿主形态）

| 值族 | JS 表示 | 深拷行为 |
| --- | --- | --- |
| 标量 Nat/Int/Rat/Real | number（Nat/Int 整数域内精确;Rat/Real=double，见 §8 精度边界） | 原值（不可变，零成本） |
| Str | string | 原值 |
| Bool/⊤⊥ | boolean | 原值 |
| Null/Nothing | null | 原值 |
| Tuple | Array | 浅壳新数组 + 递归元素 |
| struct / enum / union 值 | tagged object：`{ __tag: ctor名， …字段（保声明序） }` | 递归拷字段 |
| List | Array（Nil=[]、Cons(h,t)=[h,…t]） | 递归拷元素 |
| Optional/Result | tagged object（Some/None、Ok/Err） | 递归拷载荷 |
| Set / Map | JS Set / Map | 递归拷 |
| Fn | JS 函数（闭包 env 天然） | env 可变槽快照（§5.2） |
| @ref 视图 | **透传同一对象**（HirBorrow 零包装;OQ-4 倾向） | 零操作 |

**渲染契约**:`print/toStr` 的输出格式以 **golden 快照锁定**（§7）——golden 即渲染规格;
已知跨端陷阱（样本注释自标）：`/` 恒 Rat 的渲染、`⊤⊥` 的 null 双射等，逐条登入 §8。

### 3.2 体空债清单（prelude 承接面）

stdlib 八单元（t160 stdlib 产品实测）共 98 个 HirFunDecl;**体空 9 件**（`body == null` ⇒ 前端无 HIR
体、实现必经 prelude）：

| 单元 | 体实（codegen 直翻） | 体空（prelude 债） |
| --- | --- | --- |
| list | 27（map/fold/range/size/sortBy/…） | **3: add、mutableListOf、listOf**（构造族——@mut 视图/不可变构造的运行时原语） |
| traits | 0 | **6: map/pure/ap/flatMap/fold/traverse**（trait 方法=分派面，体必空） |
| map / set / optional / result / math / tool | 全体实 | — |
| axioms | 0（16 公理=命题面，擦除） | — |

**内建面 71 名**(kernel 内建面规格 §2，Kotlin 硬编码 ⇒ prelude 同名承接）:

| 组 | 数 |名字 |
| --- | --- | --- |
| IO/系统 | 11 | print/readLine/readFile/writeFile/appendFile/fileExists/readDir/getArgs/timeNow/sleep/delay |
| 异步/并发 | 14 | runBoth/runSeq/withMutex/bumpCounter/bumpPair/collect*/channel*/sendAll/channelDrain/channelPipe/sublEval/sublRun——**V1 报错桩**（§2.4） |
| 字符串 | 13 | concat/length/charAt/substring/strCmp/strUpper/strLower/strTrim/startsWith/endsWith/strReplace/strSplit/strIndexOf |
| 数学 | 12 | abs/floor/ceil/pow/mathMin/mathMax/mathClamp/mathRound/mathSin/mathCos/mathTan/rand |
| JSON | 11 | jsonParse/jsonGet/jsonAt/jsonLen/jsonStr/jsonNum/jsonBool/jsonIsNull/jsonIsArr/jsonToStr/jsonToList |
| 转换/控制流 | 10 | toStr/toRat/toReal/toNat/parseNat/parseRat/panic/in/emptyTuple/singleTuple |

（纯度位 26 名：与上表同源同步;**纯度=编译期已闭，codegen 运行时不重判**——@impure 标注零代码。）

### 3.3 边界判据（体空 vs 纯翻译——codegen 决策规则）

| 判据 | 处置 |
| --- | --- |
| HirFunDecl.body != null | **翻译**：该函数的 JS 体由 HIR 直生（体实 89 件，含 list 的 27、map/set/… 全量） |
| HirFunDecl.body == null | **prelude 接管**：生成 import + 符号重导出（体空 9 件） |
| symbol.kind == builtin | prelude 内建面（71 名） |
| symbol.kind == fun 且接收者标签∈分派表 | prelude 方法分派（§3.4） |
| 命题/类型/证明面 kind | 擦除（§2.6） |

注：体实件也**必须**吃 prelude 的值模型（List=Array、union=tagged object、深拷原语、渲染）——
prelude 边界=「债面 + 值模型 + 原语面」三层，非仅体空 9 件。

### 3.4 trait 方法分派（分派表 = prelude 第三层）

traits 六方法体空（声明面）⇒ 分派按**接收者运行时标签**：`Functor.map(x, f)` 走 prelude 分派
表，键=接收者 ctor 族（List/Optional/Result/Set/Map…）。V1 直接按 HIR 实证面收窄（functor 方法 ×
五容器）;未登记组合=运行时 panic（不静默，同 OQ-5 桩纪律）。

## 4. 模块拼接默认案（unit → ES module）

**默认案（静态 import 图）**:
1. 每 unit 产一个 `out/<unit>.mjs`;export 表=该 unit HirFunDecl/HirStructDecl 名。
2. unit 间引用（限定调用 HirField(target.symbol==null)→模块别名）解析为 import：
   - **stdlib 挂载别名**（.settings mount;R-22/R-23)→ import 自 `./stdlib/<alias>.mjs`（该 module
     =「体实直翻 + 体空重导 prelude」的合成件，§3.3）。
   - **用户模块别名** → 用户 unit 产物（codegen 全包模式：一个目录读全部 unit JSON + .settings 图）。
3. 裸名链（t137:retire 三项 range/sum/summation 经允许表）→ 解析回其源 module import;
   collection 限定引用面同（optional.getOrElse 形）。
4. **bundle 后置**:V1 = node ESM 直跑（import 图=依赖清单）;单文件打包（esbuild）为可选后步，
   不进 V1 验收。

挂载态来源（OQ-6:manifest 侧文件倾向）：codegen 的**消侧文件**——输入=既有 `.settings`(R-22 既有
面）+ unit 产物目录;alias→unit 表由消侧文件单一真源，不要求 CLI 新输出。

## 5. 深拷引擎在 codegen 侧的落点

### 5.1 发射规则（R4-A0 notes → 实际拷贝调用）

R4-A0(t160 已落）通道：节点 notes(code=D-DEEP-COPY, from, to[, deepElements]) + HirVarStmt.deepCopy
布尔。codegen **单源信任**（不重跑 copyDispatch——D-12 单源：判定真源在 TypeInfer):

| 宿主节点 | 发射形态 |
| --- | --- |
| HirVarStmt（deepCopy=true / note） | `let b = P.deepcopy(<init>)` |
| HirAssign + note | `u = P.deepcopy(<rhs>)` |
| HirCall + note（实参位） | 参数表达式包 deepcopy——**参数定位=note.pos 对 args[i].pos 匹配** |
| HirStructCtor + note（字段位） | 同上：assigns 值按 pos 定位 |
| HirReturn + note | `return P.deepcopy(<value>)` |
| HirParam（默认值 note） | 默认值表达式包 deepcopy |
| HirFunDecl / HirAnonFun（体位 note） | 体（表达式/末尾 return 值位）包 deepcopy |

- **共享格零码**:无 note ⇒ 无 deepcopy（@ref 接收=透传，R-28 共享七格零发射）。
- **deepElements**:审计/预算槽（R-14 计数提示，R4 §2.3:运行时全图 DFS 不做部分拷）⇒ codegen
  仅记录不计入分片;V3 CoW 时作预算输入。
- 深拷原语 = prelude.deepcopy(v)：DFS + visited 复用（保共享保环，R4 §2.3)。

### 5.2 闭包捕获快照（HirLambda/HirAnonFun)

closure 创建时对 env 中**可变**槽深拷快照（特性巡礼实证语义：snapshot() 返回创建时值，外层后续
写不映入）——发射为函数工厂前置 `P.snapshotEnv`（按捕获源 @mut 标记选拷）;@ref 捕获=零拷共享。
可变位标记通道=R4-A0 同路 notes（R-06 (b) 形态下 @mut 衣由声明位携带，见 R4 §2.4）。

### 5.3 CoW 留 V3

V1/V2 = eager deepcopy（同 R4 路线）;CoW（MUT→PLAIN 写屏障 + 元素层预算）= V3,**依赖 R-06 裁决**
（R4 §3 同构条件）;届时同一 notes 通道切原语即可（codegen 冻结点：`P.deepcopy` 一名）。

## 6. R-06 依赖（改准口径）

R-06 提升槽位的 HIR 表示未决（用户 pending）。按 R4 §7 改准后的口径——(a) 提升写回豁免 / (b) 提升
改走**声明位通道**（与 @mut var 同记账：槽位标记可变、**类型保持 T**,不造 @mut T 视图）/ (c) 维持
R-28 字面——三态对 **JS codegen** 的影响：

| 形态 | codegen 影响 |
| --- | --- |
| (a) 写回豁免 | 写回位=同变量自赋值 ⇒ 同帧槽位**普通赋值**，深拷不触发 ⇒ 零影响 |
| (b) 声明位通道 | 同上：槽位普通赋值;deepCopy 布尔/notes 由 R4-A0 通道照常携带（与 @mut 形态无关） |
| (c) 维持字面 | 纯前端测试面 fallout ⇒ 运行时零影响 |

**不硬阻塞的机制**：脚本提升当前为红（E 级）⇒ 其 HIR 为 ok=false 件 ⇒ **codegen 输入面只收
ok=true**（§1.1）⇒ 提升-红样本根本不进 codegen。R-06 裁决的实际约束点在 **V3 CoW**（@mut 表示定
案才定写屏障），V1/V2 不等（与 R4 §7 结论同）。

## 7. 「能直接跑」的验收形态

### 7.1 golden 语料（现成！）

e2e 样本 38 件中 **31 件自带「预期 stdout（compile + node，逐行）」注释**——即 golden 期望按
// compile + node 口径写就（本提案的验收形态与样本作者预设一致）：值与字面量、变量与绑定、函数与
调用形式、模式匹配、字符串与插值、标准库集合、空安全、结构体与枚举、特质、错误处理、日常-×8、
JSON内建、lambda与闭包、高阶函数与lambda、类型系统、可变性控制、副作用与unchecked、命题基础、
自动证明、手动证明、公理化、上下文弥散、异步与并发。

### 7.2 验收管线（同 jsoncmp 纪律）

1. **编译**:frontend CLI 产出 unit JSON（既有面，零前端改动）;只需保证 ok=true。
2. **codegen**:`backend-js` 读 JSON → 产 `out/*.mjs` + `prelude.mjs`。
3. **跑**:`node out/<main>.mjs` 捕获 stdout（进程级快照，rc=0）。
4. **对拍**:golden 文件（`snapshots/<样本>.stdout`)+ 差分台账（同 t160 五段口径：预期同/预期差
   分类 + 未登记差即 FAIL）;每期独立台账。
5. **守门负例**:schemas v4/残缺/非对象/ok=false/Error 节点件拒收（复用 T158 形态）。

### 7.3 渲染规格=golden 锁定

print/toStr 的输出格式不在本提案用文字定义——**以样本 golden 逐字锁定**（含 List/Optional/元组
的渲染、Rat 小数位、中文等）;改渲染=改 golden=显式 diff（契约漂移监测同 S-5 纪律）。

## 8. 分期（每期独立验收，先小后大）

| 期 | codegen 覆盖子集 | 验收 | 依赖门 |
| --- | --- | --- | --- |
| **V1 纯算术+容器+print** | 数值塔/Str/Bool 字面量、var/赋值、fun 声明与调用、when/模式、Tuple、print、List 容器族（list 27 体实 + 构造 3 prelude)、限定调用与挂载 import | 纯算术+容器样本（数值/绑定/调用形式/模式匹配/字符串插值/容器范型/温度BMI/数列区间）golden 逐例过 | 无（R4-A0 已落） |
| **V2 深拷+闭包+可变** | §5 全发射规则 + 深拷原语 + 闭包快照 + @ref 透传 + 借用面 | 可变性控制/特性巡礼三行（深拷隔离 0 / 别名同变 1 / 闭包快照 7)+ W-DEEP-COPY-LOOP 样本可跑 | R4-A0 ✓（t160 已落） |
| **V3 CoW + 异步面裁决** | CoW 写屏障 + Task/Flow 按裁决落 | 同输入与 V2 输出等价 + 拷贝次数仪表 | **R-06 裁决**、异步面裁 |

扩面顺序（V1 内部）：先 print+算术（4 样本）→ 容器/list 限定调用 → 模式/when → 字符串插值 →
高阶函数/lambda（Fn 值）→ struct/枚举 → trait 分派收口;
命题/异步/JSON 样本在 V1 **按桩纪律处理**（命题=擦除后照跑——样本含命题但 stdout 可跑;异步=报错桩）。

## 9. 风险与诚实边界

1. **数值精度**:Nat/Int→JS 安全整数域内精确;Rat/Real→double——极端值精度发散（样本注释已自标
   `/` 渲染跨端陷阱）。V1 golden 不覆盖极端域;若之后要精确 Rat=换 BigInt 分数表示（V2+ 提案级变更，
   现不承诺）。
2. **深拷 trust 边界**:codegen 不重判复制格（单源 TypeInfer)——若前端 copyDispatch 有 bug,JS 端
   忠实复制该 bug。隔离验证=特性巡礼三行（V2 验收），非代码层防御。
3. **异步面 V1 桩**:Task/Flow/Channel 遇即抛错（不静默）;异步样本（日常-异步任务/异步与并发）V1
   不可跑——验收账面如实标注，不预实现。
4. **体实件也经 prelude 值模型**——所谓「纯翻译」不是零运行时依赖（§3.3 注）;prelude 版本=代码gen
   产物的依赖（产物自带 prelude.mjs,单版本锁定）。
5. **零代码机械证（本件）**:`git status` 前后差=仅 `documents/JS后端-设计提案.md` 新增;前端/stdlib/
   套件/脚本门零改动（既有 M 项为 t123check 产物历史漂移，非本件）。
6. **本提案是设计件**——未跑任何产物的机械证（无可运行的代码面）;所有数字（59 kind 实发、9 体空、
   31 golden 样本）出自 t160 既有产品的静态读数。

## 10. 开放问题清单（不代裁）

| OQ | 问题 | 倾向（供裁） |
| --- | --- | --- |
| OQ-1 | P-11 单态化 | 本路线无义（§1.2）——两种裁决同产物 |
| OQ-3 | CoW 兑现期 | V3（R4 同） |
| OQ-4 | @ref 视图表示 | 透传 |
| OQ-5 | 异步面 V1 桩 | 报错桩 |
| OQ-6 | 挂载态消费：消侧文件（.settings） | 消侧文件（不新增 CLI 输出） |
| OQ-7 | 验收语料 | golden + 差分台账（§7） |
| OQ-9（新） | List 表示：Array（Nil=[]/Cons=[h,…t]） vs 链式 Cons 胞 | Array（V1 简单;cons O(n) 可接受——golden 锁语义、极端深 cons 性能留 V3 剖析） |
| OQ-10（新） | bundle 单文件是否进验收 | 不进（后置;node ESM 直跑即验收） |

## 11. 与既有决议/文档的对账

| 依据 | 本提案承接位 |
| --- | --- |
| 用户裁决 α 路线 + OQ-1 C 路 | §0/§1（TS 包、JSON v3 唯一输入) |
| schema v3 契约（t158) | §1.1 守门、§2 全表、§2.7 符号 13 族 |
| R4 后端提案 | §3.1 值模型、§5 深拷落点、§6 R-06 三态、§8 分期、§1.2 单态化论证 |
| R4-A0(t160 notes 通道） | §5.1 发射规则单源信任 |
| kernel 内建面规格 | §3.2 内建 71 名承接表 |
| t137/R-22/R-23 挂载面 | §4 模块拼接 + 裸名允许表 |
| e2e 样本 31 golden 注释 | §7 验收语料 |
| jsoncmp 纪律 | §7.2 差分台账 + 守门负例 |

