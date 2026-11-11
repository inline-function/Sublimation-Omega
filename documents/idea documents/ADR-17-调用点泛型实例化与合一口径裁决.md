# ADR-17 调用点泛型实例化与合一口径（t18）

- 状态：已落地（实现 + 测试通过）
- 日期：2026-11-05
- 范围：t13 发现项③「泛型构造子把形式类型参数当名义类型比较」的普遍化修复——构造子调用与
  泛型函数调用的签名实例化（构造子/函数声明里的形式类型参数 T/A/B 不再与实参类型直接比对）
- 对齐依据：P-7 旧 compiler/checker 的 checkCtorCall/checkFnCall 实例化段（extractTpBinding +
  substT + adaptOk 三段式）；字典点查未通读

## 一、背景

旧新前端同病的普遍多态缺陷（队长探针 i 复现）：

    enum Box[T] { Box(T) }  +  fun make(): Box[Nat] = Box(1)
    → [错误] E-TYPE-MISMATCH: 构造子 Box 实参 Nat 与字段 T 不符

根因（确认）：调用点核对直接拿被调声明里的形式类型参数与实参类型做名义比较（adaptOk），
没有先把形式参数实例化成新鲜推导变量、经合一带入解集再回代字段/形参与结果类型。旧实现早有
等价三段（类型参数位由实参反推 + substT 回代 + 未解裸类型参数位跳过核对），新前端移植时丢掉了
前两段。

## 二、裁决

### 2.1 调用点实例化三段（ADR-17 主条款）

1. **换名**：被调声明（enum theory / fun theory）的形式类型参数名统一换成新鲜变量名
   （_iN.原名，点号杜绝与任何用户类型名碰撞；序号取自 TypeInfer 实例级计数器——跨编译不共享）
   字段/形参/返回类型按换名表结构递归重建（NamedType/FunType/TupleType/Mut/Atomic）。
2. **合一**：实参类型与换名后的形参逐位 unifyInto（vars=新鲜名集合，sub=替换表）。合一只为收集
   绑定：synthetic/数值塔等宽容口径全部保持不变、合一不因此收紧；真不一致仍由调用方 adaptOk
   兜诊断（与旧实现的消息谓词一致）。
3. **回代**：按解集（形式参数名 → 解，deref 防链）回代字段/形参/返回类型：
   - callCtor：字段核对用回代后的字段；未解的裸类型参数字段跳过（旧 isTP 口径）；
     结果类型的类型参数位携带解或 synthetic（旧 None : Optional[T] 对任意 T 口径不变）。
   - callFun：形参核对与返回类型回代同构；返回类型携带解集代入（id(1) 的调用结论为 Nat）。

### 2.2 覆盖边界

- 覆盖：用户 enum 构造子、prelude 构造子（Some/Cons/Ok/…：theory 名 T/K/V/E 与字段规格同名）、
  FUN/THEOREM 泛型函数。prelude theory 名由 PRELUDE_ENUM_THEORY_NAMES 提供（与 t14 的
  PRELUDE_CTORS 字段规格同名对齐）。
- 不覆盖（保持现状）：impl 成员经方法糖的直调（xs.fold(…)——P-8 将以扩展函数替代 impl，
  t13/t14 已留档不修）、显式类型实参绑定（f<Nat>(…) 先于实参反推的 B1 口径，v1 无样本驱动）、
  类型类约束字典分派（V1 未做）。
- 不破坏：synthetic 宽容、错误恢复半成品、数值塔方向性、when 穷尽性与 nid 单一分配源
  （新增类型节点一律走实例级 alloc）。

## 三、逐条对账

| 项目 | 前（t14 终态） | 后（t18） |
| --- | --- | --- |
| 探针 i（enum Box[T] + Box(1): Box[Nat]） | E-TYPE-MISMATCH「实参 Nat 与字段 T 不符」 | exit 0（check 通过） |
| list.subl 14:26「构造子 Cons 实参 List[B] 与字段 List[T]」 | 存在 | 消失 |
| list.subl E-TYPE-MISMATCH 计数 | 25 | 20（余量为 impl fold 方法族 + @list/zip 两处既有缺陷，均在 §2.2 不覆盖边界） |
| optional.subl E-TYPE-MISMATCH | 2 | 0 |
| prelude 构造子 nominal 族（Some/Cons/Ok 族） | 探针/样本多例 | 全消（prelude-cons/some/result 探针 exit 0） |
| e2e ok=true | 28/68 | 31/68 |

探针清单（CLI check）：Box(1): Box[Nat] exit 0；嵌套 Cons(1, Nil) exit 0 且结论 List[Nat]；
prelude Cons(1, Cons(2, Nil)) / Some(1) / Ok(1) exit 0；反例 Box("s") → 返回位
「Box[Str] 与 Box[Nat] 不符」；反例 Cons(1, Cons("x", Nil)) → 构造子位
「实参 Lst[Str] 与字段 Lst[Nat] 不符」；泛型函数 id(1): Nat exit 0、id(1): Str 返回位报错。

## 四、已知边界

1. 显式类型实参（Cons<Nat>(…)）不优先绑定——B1 口径未移植（v1 无 e2e 样本驱动）。
2. 方法学糖经 impl 成员的调用（fold/flatMap/getOrElse/add/put 族）不在本裁决覆盖范围（§2.2）。
3. 解集仅按位置合一收集；lambda 返回类型反推（旧 v2.1 的定点迭代环）未移植——R 形参在仅有
   lambda 实参时可能留 synthetic/原名（与 t14 终态同形态，不回退）。

## 五、验收

- :frontend:compileKotlin 绿。
- :frontend:test --rerun-tasks **294 tests 0 failures**（新增 TypeInferTest.genericCtorInferenceT18：
  正例 Box(1): Box[Nat] 与嵌套 Cons(1, Nil)→Lst[Nat] 结论上树、反例 Box("s") 与
  Cons(1, Cons("x", Nil)) 落 E-TYPE-MISMATCH、泛型函数 id(1) 同构；E2ERegressionTest 2 全绿，
  ParserTest 63 / ProofCheckerTest 49 / SemanticTest 22 / TypeInferTest 37 回归全绿）。
- CLI check 全部探针如 §三表。
- 交付物：TypeInfer.kt（instCall + renameTps/substTps + callCtor/callFun 实例化）、
  SymbolTable.kt（preludeEnumTheoryNames）、Elaborator.kt（调用点传 theory 名）、
  TypeInferTest.kt（新增用例）、本裁决。
