# ADR-14 NodeId 单一分配源与重解析作用域裁决（t13）

- 状态：已落地（实现 + 测试通过）
- 日期：2026-11-05
- 范围：t13「修复 E-UNBOUND-NAME 主因族」——四修复项：nid 单一分配源（F1 blocker）、
  签名在调用点重解析的作用域、量词绑定变量、类型参数在命题项位
- 对齐依据：P-7 旧实现 PropLogic/Checker 的量词作用域与类型参数口径（字典点查）

## 一、背景

t12 e2e 13/68 通过，队长以最小样例二分定位主因族（探针 a/c/d/f/g/h 实录于
log/2026-11-05.md）：check core/collection/list.subl 的 8:10/8:18「未知类型 T」虽报在
enum List 构造子字段行，实则由 14:26 的 Cons(...) 调用触发**签名重解析**（声明位已压入
theory 作用域、调用位没有）；探针 f 的 ∀x 与函数类型参数 a/b 均无作用域。

## 二、裁决

### 2.1 NodeId 单一分配源（修复项 1，F1 blocker）

- 引入 internal class NodeIdSource(start = 1)（alloc(): Int 自增）作为**全编译期
  唯一的 HIR 节点 id 源**；compile() 创建一实例，同时注入 SymbolTable(hirNid) 与
  Elaborator(nids)（freshNid = nids.alloc()）。
- 旧缺陷：SymbolTable.builtinValueTypeOf() 用**符号表计数器** nextId 造 HirNamedType 的 nid，
  与 Elaborator.nextNid 是两套计数器——同一 nid 对应不同节点，破坏 D-9 / ADR-1 §3.7 的
  「诊断↔节点身份」绑定（内建值字面量的合成类型经 lookupValue().type 可流入 HirCall.type
  进树，nid 与构造期节点相撞）。
- **禁止第二个节点计数器**：SymbolEntry.id 的符号 id 空间保留（符号引用分表口径，非节点身份），
  但任何 HirXxx 节点的 nid 只能取自 NodeIdSource。
- 证据：e2e 68 样本「nid 全文件唯一」判据（t12 的 E2ERegressionTest 判据 2）全绿。

### 2.2 签名在调用点重解析的作用域（修复项 2）

- Elaborator.sigOf(d)：重解析形参/返回类型前 enterTypeParams(d.theory)，try/finally
  出栈——泛型签名里的 T/A/B 按词法作用域解析（symbol=null 静默，t7 既有口径），不再落
  E-UNBOUND-NAME。
- 构造子调用位（callExpr 的 SymbolKind.CTOR 分支）：形参表解析前压入该 enum 的
  theory，完成后出栈。
- **impl 自身类型参数**（裁决补充：impl F for List[a] 的 a 不在成员 FunDecl 的 theory 里，
  而在 self 类型位的实参里）：decl() 的 ImplDecl 分支把 self（Optional[a]）的裸名类型实参
  压入 typeParams 栈（TypeParam(name, null, 0, pos)），成员签名与 self 类型解析期间有效，
  出栈即弃。判据：list/optional 的「未知类型 a」全消；probe C 无伪报。
- 未修的同类项（见 §四 发现项）：泛型构造子调用的实参核对把形式类型参数当**名义类型**逐位比较
  → E-TYPE-MISMATCH（list 14:26「实参 List[B] 与字段 List[T] 不符」）——属 t8 callCtor 的
  类型参数代入缺口，非 E-UNBOUND-NAME 族，留待下一轮。

### 2.3 量词绑定变量入帧（修复项 3）

- NameResolver.quantified(names, origin, block)：**成对 push/pop** 的 BLOCK 帧内以
  SymbolKind.PARAM 登记量词变量（∀/∃/∃! 的 names），归一完体即出栈。
- PropNormalizer.prop() 的 QuantExpr 分支整体包入该作用域——体内对 x 的引用按帧链解析，
  不再落 E-UNBOUND-NAME；嵌套量词天然叠加。
- 旧 PropLogic.fromExpr 同构（字典点查确证）：量词变量在命题归一期间就是作用域内绑定。

### 2.4 类型参数在命题项位静默（修复项 4）

- PropNormalizer 新增构造参数 isTypeParam: (String) -> Boolean（Elaborator 的
  typeParams 词法栈投影）；term()/termQuiet() 的 NameRef 分支：**类型参数名 → symbol=null
  静默**（与 type() 的 NamedType 分支口径一致），非类型参数才走 resolveValue。
- 判据：ZFC 风格（x ∈ a）中 a 作为函数类型参数不报 E-UNBOUND-NAME；探针 f（set
  extensionality 定理）check **exit 0 通过**。

## 三、逐条对账（E-UNBOUND-NAME 计数，修复前 → 后）

| 样本 | 修复前 | 修复后 | 消除项归因 |
| --- | --- | --- | --- |
| core/collection/list.subl | 58 | 44 | 项2：8:10/8:18 的 T×2、19 行 A/R×6、127 行 T×3；项2 补充：impl self a×3 |
| core/collection/optional.subl | 47 | 44 | 项2 补充：impl self a×3（其余 44 为 §四 发现项 1 的级联） |
| core/zfc/zfc.subl | 74 | **0** | 项3 量词绑定 + 项4 类型参数静默（check 全过 exit 0） |
| core/zfc/derived.subl | — | **0** | 同上 |
| 探针 f（定理 ∀x） | 3（x/a/b 未定义） | **0** | exit 0 |
| 探针 C（enum+impl+泛型成员） | 伪报多 | **0** | 仅剩 t8 族 E-TYPE-MISMATCH（非本族） |

全量 e2e E-UNBOUND-NAME 总数 978 → 本族覆盖项全部消除（zfc 两样本清零；list/optional 各消项
如上）；余量的主成分为跨文件引用与 §四 发现项，非 t13 范围。

## 四、t13 范围外发现项（建议后续任务）

1. **PRELUDE_ENUMS 跳过用户声明的 Optional**（Collector L39/L112：enum 名在
   {Bool, Null, Optional} 时不注册）：单文件期无 prelude → optional.subl 自己声明的
   enum Optional 不入符号表，级联 ctor Some/None 未登记，44 条 E-UNBOUND-NAME 几乎全源于此。
   建议：单文件模式下 prelude 名族与用户声明冲突时按用户声明注册（或 prelude 兜底改为类型层
   内建而非登记跳过）。
2. **impl 成员以名直接调用落 E-UNBOUND-NAME**（list 的 fold/flatMap 递归调用）：impl 成员只
   入 methods 表（方法糖用），未按自由函数形态取用——与旧实现的「方法 = 自由函数糖」口径差。
3. **泛型构造子调用 E-TYPE-MISMATCH**（§2.2 末）：callCtor 须把形式类型参数按实参代入或
   通配，而非名义比较。
4. 跨文件引用（Functor/Monad/Optional 于 list.subl 等）：单文件模式的既定边界，需模块挂载
   （ADR-1 §3.2 已留档）。

## 五、验收

- :frontend:compileKotlin 绿（零错误零警告）；:frontend:jar 绿。
- :frontend:test --rerun-tasks **294 tests 0 failures**（含 qa t12 的 E2ERegressionTest 2：
  68 样本结构/可复现/nid 唯一判据，与 ScratchT15 6；t9 ProofCheckerTest 49 与 t8/7 回归全绿）。
- CLI check 三样本 + 探针 f/C 复核如 §三表。
- 交付物：SymbolTable.kt（NodeIdSource + 注入）、Semantic.kt、Elaborator.kt
  （freshNid 单源 + sigOf/ctor/impl 作用域 + propNorm isTypeParam 投影）、NameResolver.kt
  （quantified）、PropNormalizer.kt（QuantExpr 绑定 + 项位类型参数静默）、本裁决。
