# ADR-15 内建容器登记口径与默认值 arity 规则裁决（t14）

- 状态：已落地（实现 + 测试通过）
- 日期：2026-11-05
- 范围：t14「补齐内建容器类型/构造子与参数默认值 arity 核对」——修复项 1（BUILTIN_TYPES
  容器类型 + prelude 构造子名字层登记 + 队长补充的 PRELUDE_ENUMS 单文件注册）、
  修复项 2（决策 69：尾部默认参数参与调用点 arity/命名实参重排）
- 对齐依据：P-7 旧 compiler/checker（Symbols.kt 决策 45 注入机制、Types.kt BASE_TYPES、
  CallCheck.kt 决策 69）；core 模块实际形态字典点查

## 一、背景

t12 归因 F3/F5：旧 BASE_TYPES 的容器与 Range/Json 未带过来；prelude 构造子（Some/None/Nil/Cons/
Ok/Err/SetNil/SetCons/MapNil/MapCons）在单文件期无 prelude 挂载时完全不可解析（旧多模块管线
里由 prelude 可见模块兜底，新单文件模式没有该通道）；调用点 arity 核对未把「有默认值的尾参可
省略」（旧决策 69）纳入。队长补充指令（t13 发现项①）：Collector 的 PRELUDE_ENUMS 豁免名单
（Bool/Null/Optional）在单文件期跳过用户声明的同名 enum 注册——optional.subl 自己声明的
enum Optional 不入表，44 条 E-UNBOUND-NAME 几乎全源于此级联。

## 二、裁决

### 2.1 BUILTIN_TYPES 补齐（修复项 1 之前半）

- 旧 BASE_TYPES 差集补入：Range（惰性区间）、Json（JSON 值）——旧表已有、新表漏带。
- 容器类型裸名兜底：List / Optional / Result / Map / Set——单文件期无 prelude 挂载时类型位
  可解析（旧「可见模块」口径的单文件等价物）。用户自声明的同名 enum/struct 优先
  （lookupType 查表序：structs→enums→classes→aliases→内建）。

### 2.2 prelude 构造子名字层登记（修复项 1 之后半）

- SymbolTable 新增 PRELUDE_CTORS 表（bare name → enum 名 / theory 元数 / 字段规格），形态取自
  core 模块实际（Optional[T] 的 Some(T)/None()、List[T] 的 Cons(T, List[T])/Nil()、
  Result[T,E] 的 Ok(T)/Err(E)、Set[T] 的 SetCons(T, Set[T])/SetNil()、Map[K,V] 的
  MapCons(K, V, Map[K,V])/MapNil()）。
- lookupCtor/lookupValue 增加兜底：ctors[name] ?: preludeCtor(name)——用户自声明 ctor
  优先，prelude 构造子次之（旧实现「同名重复内建保留名静默」的等价口径）。
- 字段规格 PreludeField(root, params)：形参形态由 Elaborator 的 preludeFormals() 合成为
  类型参数位（T/K/V 与容器应用），theory 元数经 preludeEnumTheoryOf 兜底；实参类型核对属
  callCtor 域——泛型构造子把形式类型参数当名义类型比较的缺陷由 t18 专门处理。
- 缓存实例级化（关键修正）：preludeCtorCache 必须是 SymbolTable 实例字段而非文件级
  top-level——否则跨编译复用携带旧实例符号 id 的条目，破坏「相同输入恒产相同输出」
  （S-1 层间总契约 3；e2e 可复现判据捕获：两次编译 symbol id 序列不一致）。本裁决把它列为
  prelude 兜底的硬约束：任何携带实例 id 的懒登记缓存不得跨 compile 实例共享。

### 2.3 PRELUDE_ENUMS 豁免废除（队长补充项）

- Collector 不再因 {Bool, Null, Optional} 名族跳过 registerEnum——单文件期无 prelude 挂载，
  用户声明的 enum 即生效；旧豁免名单只适用于「prelude 实际挂载」的旧多管线形态。
- 判据达成：optional.subl 的「未知类型 Optional / 未定义 Some/None」族从 47 条降至 3 条
  （余量为跨文件的 Functor/Foldable/Monad 类型类引用——单文件边界，ADR-13 §四 ④ 同类）。

### 2.4 默认值参与调用点核对（修复项 2）

- TypeInfer.callFun 新增 defaults（形参是否有默认值的 Boolean 表）：
  required = 首个带默认值形参的下标（无默认值则 = 形参数）；
  实参 < required → E-ARITY 旧口径「函数 X 想要 R~N 个实参，但你传了 M 个（尾部 K 个参数
  有默认值，可省略）」；实参 > 形参数 → E-ARITY「多了」（既有口径不变）；
  无默认值时的缺实参保持既有 E-TYPE-MISMATCH「缺实参：名」口径（旧 CallCheck 同码同消息）。
- 命名实参重排交互：reorderNamedArgs 的返回改为按形参位对齐的可空表——未占位先以声明
  默认值填补（AST Expr，在调用方语境重走 expr），无默认值的空位落旧口径 E-TYPE-MISMATCH
  「缺实参：名」+ 调用点 ErrorExpr 哨兵承接（etype synthetic，不污染其余形参核对）。
  旧行为 mapNotNull 会把 f(1, c = 3) 压成 [1,3] 造成 b/c 位错配——修复后位置恒对齐。
- 声明位规则（尾部连续默认值、默认值类型核对）旧实现另由 E-DEFAULT-PARAM 承接——
  本期未见调用层需求样本，声明位核对留待后续轮次（本裁决未覆盖）。

## 三、逐条对账（E-UNBOUND-NAME 计数，修复前 → 后；前 = t12 基线，后 = e2e 重跑实测）

| 样本 | 修复前 | 修复后 | 归因 |
| --- | --- | --- | --- |
| core/collection/list.subl | 58 | 8 | t13 重解析作用域 + 本轮 prelude ctor 登记与 Optional 注册；余 8 = 跨文件 Functor/Foldable/Monad + impl 成员直接调用（P-8 废除口径） |
| core/collection/optional.subl | 47 | 3 | 队长补充项（用户声明 Optional 注册 + Some/None 登记）；余 3 = 跨文件类型类 |
| core/collection/result.subl | （t12 明细被覆盖、未单计） | 0（check exit 0） | Result/Ok/Err 登记后全清——qa F3 点名族 |
| core/collection/map.subl | （同） | 0（仅剩 t8 族 2 条） | Map[K,V] prelude 登记齐备 |
| core/collection/set.subl | （同） | 3 | Set/SetNil/SetCons 登记；余 3 = 跨文件类型类 + impl 成员 |
| core/zfc/zfc.subl | 74（t13） | 0 | t13 量词/类型参数修复 |
| 全量 68 样本 | 978（t12） | 153 | 剩余主成为跨文件引用与 impl 成员直接调用（P-8 单文件边界，ADR-13 §四 ②④ 已留档） |

e2e ok=true 通过率：13/68（t12）→ 28/68（本轮）。默认值探针：正例 f(2)（b 默认 1）exit 0；
反例 f() → E-ARITY「想要 1~2 个实参…尾部 1 个参数有默认值，可省略」、f(1,2,3) → E-ARITY「多了」；
命名实参 f(1, c = 3) 与 f(c = 3) 均位置对齐 exit 0；形参留空无默认值 f(a=1, c=3)（b: Str）
→ E-TYPE-MISMATCH 缺实参：b（旧口径）。

## 四、已知边界

1. prelude 构造子的调用走 callCtor 形式类型参数比较——Some 的 Nat 实参 vs T 形参落
   E-TYPE-MISMATCH（t18 域，队长已实证为普遍多态缺陷并建单）。
2. 命名实参语法为 f(x = 1)（本前端既有口径）；旧消息文本里的 f(x: 1) 是消息文案而非语法。
3. 声明位的默认值规则（尾部连续 / 默认值类型核对 / 类型类成员禁默认值）未落地——无 e2e 样本
   驱动，留待后续。

## 五、验收

- :frontend:compileKotlin 绿（零错误零警告）。
- :frontend:test --rerun-tasks 293 tests 0 failures（含 E2ERegressionTest 2 全绿：
  68 样本 nid 唯一 / 可复现 / 结构完整判据——本轮可复现性回归（top-level preludeCtorCache
  跨编译泄漏）已由实例级缓存修复；ParserTest 63 含 syntax t15 的 5 例；ProofCheckerTest 49、
  SemanticTest 22、TypeInferTest 36 回归全绿）。
- CLI check 三样本复核如 §三表；默认值正/反例如上。
- 交付物：SymbolTable.kt（容器类型 + PRELUDE_CTORS + 实例级缓存）、Collector.kt（豁免废除）、
  Elaborator.kt（preludeFormals + theory 兜底 + callFun defaults 传递 + 重排默认值填补）、
  TypeInfer.kt（callFun 默认值 arity）、本裁决。
