# kernel 内建面规格（V1 不迁 · 独立登记件）

> 抽源：t132（stdlib 设计提案附录 A）→ t137（B0-0 收口）独立成件。
> 登记对象：`TypeInfer.builtinSig`（TypeInfer.kt:790）71 名（含 in/emptyTuple/singleTuple 算符/元组投影）+ `builtinImpureNames`（:779）26 名纯度位。
> **口径**：kernel 层 = 语言定义层内建（随编译器版本锁定，规划稿 §2 内核层）；stdlib 层 = 源码可审计（本件外，见 stdlib-设计-提案）。

## 1 分工线（历史病_stamp）

| 层 | 内容 | 可审计性 | retire 状态 |
|---|---|---|---|
| kernel 内建面 | 本表 71 名：IO/异步/字符串/数学/JSON/控制流/转换 | ❌ Kotlin 表硬编码（签名投影在 builtinSig，纯度在 builtinImpureNames，**改须两处同步**） | V1 不迁（B3 后另件） |
| prelude 类型面 | BUILTIN_TYPES 标量/单例/异步泛类型（Nat/Int/Rat/Real/Str/Bool/Null/Nothing/Any/EmptyTuple/SingleTuple…）+ PreludeDecls 虚拟 struct(11)/alias(5) | ❌ Kotlin 声明 | B2 后 retire（R-22 形态 C 路线） |
| retire 替身三项 | range/sum/summation | ✅ stdlib 源（B0-0 已迁，裸名链经 RETIRED_BARE_NAMES 允许表） | **已完成 B0-0** |
| collection 五件 | list/optional/result/set/map 自由函数全家 | ✅ stdlib 源（B0-1 已迁，**限定引用通道**——裸名链不注入） | **已完成 B0-1** |

## 2 内建函数签名面（builtinSig 逐名登记）

`(参数类型) -> 返回类型`；`Any` = 真通配位（isAnyExpect）；`(内建T)` = 具名类型变量（instCall 解出）；`Result[ok, err]` 携错误信息（P5 决策 81）。

### 2.1 IO/系统（11 名，impure×11）

| 名字 | 签名 |
|---|---|
| print | (Any) -> Null |
| readLine | () -> Result[Str, Str] |
| readFile | (Str) -> Result[Str, Str] |
| writeFile | (Str, Str) -> Result[Null, Str] |
| appendFile | (Str, Str) -> Result[Null, Str] |
| fileExists | (Str) -> Bool |
| readDir | (Str) -> Result[List[Str], Str] |
| getArgs | () -> List[Str] |
| timeNow | () -> Nat |
| sleep | (Nat) -> Null |
| delay | (Nat) -> Null |

### 2.2 异步/并发（14 名，全 impure）

| 名字 | 签名 | 备注 |
|---|---|---|
| runBoth | ((Null) -> Null, (Null) -> Null) -> Null | |
| runSeq | ((Null) -> Null, (Null) -> Null) -> Null | impure |
| withMutex | (Mutex, (Null) -> Null) -> Null | impure |
| bumpCounter | (Mutex, @mut (内建T)) -> Null | HirMutType 视图 |
| bumpPair | (Mutex, Mutex, @mut (内建T), @mut (内建T)) -> Null | |
| collectToList | (Flow[(内建T)]) -> List[(内建T)] | |
| collectCount | (Flow[(内建T)]) -> Nat | |
| collectAll | (Flow[(内建T)], ((内建T)) -> Bool) -> Bool | |
| channelFromList | (List[(内建T)]) -> Channel[(内建T)] | |
| sendAll | (Channel[(内建T)], List[(内建T)]) -> Null | |
| channelDrain | (Channel[(内建T)]) -> List[(内建T)] | |
| channelPipe | (Channel[(内建T)], Channel[(内建T)]) -> Null | |
| sublEval | (Str) -> Result[(内建T), Str] | 反射求值 |
| sublRun | (Str) -> Result[Null, Str] | 反射运行 |

### 2.3 字符串（13 名）

| 名字 | 签名 |
|---|---|
| concat | (Str, Str) -> Str |
| length | (Str) -> Nat |
| charAt | (Str, Nat) -> Str |
| substring | (Str, Nat, Nat) -> Str |
| strCmp | (Str, Str) -> Int |
| strUpper | (Str) -> Str |
| strLower | (Str) -> Str |
| strTrim | (Str) -> Str |
| startsWith | (Str, Str) -> Bool |
| endsWith | (Str, Str) -> Bool |
| strReplace | (Str, Str, Str) -> Str |
| strSplit | (Str, Str) -> List[Str] |
| strIndexOf | (Str, Str) -> Optional[Nat] |

### 2.4 数学（12 名）

| 名字 | 签名 |
|---|---|
| abs | (Rat) -> Rat |
| floor | (Rat) -> Int |
| ceil | (Rat) -> Int |
| pow | (Rat, Rat) -> Rat |
| mathMin | (Rat, Rat) -> Rat |
| mathMax | (Rat, Rat) -> Rat |
| mathClamp | (Rat, Rat, Rat) -> Rat |
| mathRound | (Rat) -> Rat |
| mathSin | (Rat) -> Rat |
| mathCos | (Rat) -> Rat |
| mathTan | (Rat) -> Rat |
| rand | () -> Rat | impure（系统源，列数学域） |

### 2.5 JSON（11 名）

| 名字 | 签名 |
|---|---|
| jsonParse | (Str) -> Optional[Json] |
| jsonGet | (Json, Str) -> Optional[Json] |
| jsonAt | (Json, Nat) -> Optional[Json] |
| jsonLen | (Json) -> Optional[Nat] |
| jsonStr | (Json) -> Optional[Str] |
| jsonNum | (Json) -> Optional[Rat] |
| jsonBool | (Json) -> Optional[Bool] |
| jsonIsNull | (Json) -> Bool |
| jsonIsArr | (Json) -> Bool |
| jsonToStr | (Json) -> Str |
| jsonToList | (Json) -> Optional[List[Json]] |

### 2.6 转换与控制流（10 名）

| 名字 | 签名 | 备注 |
|---|---|---|
| toStr | ((内建T)) -> Str | 通配转换 |
| toRat | ((内建T)) -> Rat | |
| toReal | ((内建T)) -> Real | |
| toNat | ((内建T)) -> Optional[Nat] | 失败语义=Optional |
| parseNat | (Str) -> Optional[Nat] | |
| parseRat | (Str) -> Optional[Rat] | |
| panic | () -> Nothing | 控制流；refute/不可达兜底 |
| in | (Any, Range[(内建T)]) -> Bool | 算符投影（`x in r`） |
| emptyTuple | () -> EmptyTuple | 元组构造 |
| singleTuple | ((内建T)) -> SingleTuple[(内建T)] | 元组构造 |

## 3 纯度位（builtinImpureNames，26 名——与上表同源须同步）

```
print, readLine, readFile, writeFile, getArgs, sleep, appendFile, fileExists, readDir,
timeNow, rand, delay, runBoth, runSeq, collectToList, collectCount, collectAll,
withMutex, bumpCounter, bumpPair, channelFromList, sendAll, channelDrain, channelPipe,
sublEval, sublRun
```

注：builtinSig 是旧 builtinimpure / builtinPure 两表合并的类型层投影——**纯度信息在投影中丢失**，故本表单列；两表同源（旧 builtinimpure），改须同步。

## 4 落点闸（kernel 面的既有核验路径）

- 裸名解析链（SymbolTable lookupValue）: `lookupBuiltinFun(name) → preludeFun(name) → lookupBuiltinValue(name)`——kernel 优先于 retire 替身（同名时 kernel 胜，P-7 口径继承）;
- 签名核对经 callFun（严格 adaptOk，t58 内建泛型位为具名类型变量 `(内建T)`——instCall 解出，旧「一律放行」已删）;
- e2e 冻红样本的未解析名（getOrElse/add/put 等）保持未解析——kernel 面不含任何 collection 名（B0-1 裸名隔离的口径前提）。

## 5 审计缺口与后件（诚实边界）

1. 本表为**文档化补丁**（提案风险 4 承诺的独立件）：kernel 面的可审计需 B3（kernel 函数面 retire → stdlib 源）+ 命题面配套，V1 不做;
2. `rand` 纯度位在数学域表中（签名同域），实为系统源——本表显式标注;
3. `in` 为算符投影（非可调用函数形态），签名登记仅类型层口径;
4. 同步约束内置在注释（builtinSig 上方法名 builtinImpureNames），无编译期交叉校验——改一处忘另一处为已知缺陷（登记在 结构债台账）。
