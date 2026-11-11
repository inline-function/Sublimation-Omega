# ADR-21 显式类型实参调用与解析恢复点修复（t23）

- 状态：已落地（实现 + 测试通过）
- 日期：2026-11-05
- 范围：B8「显式类型实参构造与函数调用」（Channel<Nat>(1)、id<Nat>(…) 两样本族）与
  B7「解析恢复点」（函数与调用形式.subl:46-47 的 +/* 前缀调用）
- 对齐依据：P-7 旧 compiler/checker 的 CallCheck.kt:158-168（instTerms + typeOfTerm）、201-215
  （Channel 特判）、970-975（checkFnCall 显式绑定）段；形式化语义01 §2.4/§4（T-APP）；字典点查未通读

## 一、背景

### B8：解析干净、语义把角表当值

    var ch: Channel[Nat] = Channel<Nat>(1)
    → [错误] E-UNBOUND-NAME: 未定义的名称 `Channel`×
      [错误] E-UNBOUND-NAME: 未定义的名称 `Nat`×

解析产物正确（CallExpr(callee=InstExpr(target=NameRef(Channel), terms=[NameRef(Nat)]),
args=[IntLit(1)])——D-1 紧贴 `<` 即后缀实例化不误判），断点在 Elaborator.callExpr 的 else 分支：
callee 无差别走 `expr(e.callee)` 值域消解——InstExpr 的 target/terms 被当**值**读取（resolveValue），
Channel 是类型位名字、Nat 是类型字面，双双落 E-UNBOUND-NAME。

### B7：恢复点处的令牌多消费

    unchecked print(+(4, 5))
    → [错误] E-SYNTAX: 期望)，实际'<eof>'，并级联 E-UNBOUND-NAME

parsePrimary 的 opPrefixKinds 分支（决策 88：+(4,5) 当普通函数名调用）先调 parseArgs()（其
next() 消费的是算符 `+`，留下的 `(` 被收成 TupleExpr 的起点），返回后才 `NameRef(next().text,…)
再消费一个令牌——正好吞掉 print 的闭括号，调用括号永不闭合，错误流浪到 `<eof>`。属恢复点产物
缺陷（一个可救形态被吃成不可救），不是文法口径问题。

## 二、裁决

### 2.1 B8 调用位角表按类型域读取（主条款）

callExpr 的 callee 消解按形态分流：

1. **NameRef**：照旧 resolveValue 值域消解（无角表，explicitTps=∅）。
2. **InstExpr**：target 取名（`(c.target as? NameRef)?.name`），先**静默**查 symbols.lookupValue
   命中才走本路径（未命中回落原 expr 值域路径，保留裸 InstExpr 作值的旧出口）；命中则
   resolveValue 得 SymbolRef、callee 造 HirNameRef（名字不变，符号正确），terms 经 termType
   读**类型域**（NameRef→具名类型；InstExpr(target=NameRef)→namedT(应用)；其余 synthetic 兜底）。
3. 形参位与结果类型的实例化复用 t18/ADR-17 的 instCall 通道：callFun/callCtor 新增
   explicitTps 形参，instCall 在换名后、合一圈前**预播种** sub[renaming[formalTps[i]]]=explicit[i]
   （i<formalTps.size 才绑）。

### 2.2 冲突裁定：显式优先、一次绑定不覆盖（形式化语义01 §2.4/§4，T-APP）

- 显式实参先占位（预播种），推导圈的 unifyInto 对**已绑定**变量走 looseEq 不回写——显式优先
  且不被实参反推覆盖；
- 冲突不在实参位报，挪到形参位核对处落 E-TYPE-MISMATCH（id<Nat>("s") → 实参 1 Str 与形参
  Nat 不符；callCtor 同构：Channel<Nat>("s") → 实参 Str 与字段 Nat 不符）。
- 旧 CallCheck 同口径（显式 bindFirst + 一次绑定不覆盖 + 参数位 mismatch 兜底），本裁决即对齐。

### 2.3 Channel 登记为 prelude 泛型构造子（取代旧特判）

旧 CallCheck:201-215 对 Channel 有专门分支（callee=InstExpr、容量≤1 且须 Nat、缺类型参数报
E-TYPE-MISSING）。新前端不设特判，把 Channel 登记进既有 prelude 表（与 t13/t14 的 Some/Cons 同源）：

- PRELUDE_CTORS += Channel（theory 名 T；字段 [Nat]——容量位；fieldDefaults=[true] 可省）；
- PRELUDE_ENUM_THEORY/…_NAMES += Channel（theory=1、名 [T]）；
- preludeCtorDefaults(name) 新增导出字段默认值旗标（供 ctorCall 的 B5 尾部缺省通道复用）。

prelude ctor 条目的符号 kind 为 BUILTIN（builtinEntry 登记，既有架构），故 BUILTIN 分支前置
explicitCtorCall 路由：仅在 explicitTps 非空且 lookupCtor 命中时进入 ctorCall（三源形参表的
prelude 源），否则回落 builtinSig/preludeFunSig 旧链——**不改动无角表 prelude ctor 调用
（Some(5)）的既有出口，零回归风险**。函数路径的 ctorCall 为原 CTOR 分支体抽出的公共辅助
（t18 实例化 + t20 B5 默认值 + t23 显式实参三参数线汇合点）。

### 2.4 函数路径不做显式实参个数核对（对齐旧 checkFnCall）

角表三义（§2.10）：命题供给 witness<tagged<7>>() 的角表项是**命题**不是类型。旧 checkFnCall
只按 `i < count` 绑定前 N 个、不做个数核对（仅旧 FunType 值调用路径有 instArity）。故 FUN/
THEOREM 分支**不**落 instArity——多出的角表项静默忽略（id<Nat, Int>(1) 同旧沉默），命题供给
（上下文弥散.subl:37）不被误伤。曾在实现中加过该核对并实测构成回归，已撤回。

### 2.5 B7 恢复点修法（Parser）

opPrefixKinds 分支对齐旧 Parser.kt:1415-1419 的消费序：**先 next() 消费算符**（与
symbolCallStart 分支同理：parseArgs 的 next() 消费开括号），再 parseArgs() 收位置实参，
CallExpr(NameRef(算符名), args)——+(4,5) 是两个位置实参的函数式调用，不是元组。文法未放宽、
决策 88 口径不变；不可救形态（缺括号、残算符）仍落唯一 E-SYNTAX。

## 三、验收与证据

- e2e 对账：异步与并发.subl、日常-异步任务.subl（B8）、函数与调用形式.subl（B7）三样本由
  待办（2+2+4 诊）转**零诊断**；D-1 后缀实例化族与全量 68 样本无新增失败（core/list、tuple
  等既有不受影响）。
- 单测：ParserTest「运算符前缀调用按位置实参收集/不吞闭括号」×2；ExplicitTypeArgsTest ×7
  （Channel[Nat] 结果类型、容量可省、容量须 Nat、容量≤1、id<Nat>(1)→Nat、id<Nat>("s") 
  E-TYPE-MISMATCH、命题供给角表不落个数核对）。

## 四、边界与残留

1. **消息口径差异**：prelude ctor 路由走 ctorCall，诊断措辞为通用构造子口径（「构造子 Channel
   期望 1 实参，实际 2」「实参 Str 与字段 Nat 不符」），不复刻旧 Channel 专句（E-TYPE-MISSING
   「Channel 创建必须写明类型参数…」/E-ARITY「最多一个容量实参」）。任务判据为「类型核对正确」，
   通用口径满足；E-TYPE-MISSING 语义被 prelude 默认值机制覆盖（容量可省，缺角表时回落 BUILTIN
   出口——见残留 2）。
2. **无角表 Channel 调用**（Channel(1) 不带 <>）不进 B8 通道：符号 kind=BUILTIN 走旧出口
   （syntheticT「内建Channel」不核对）——等同既有 Some(5)/Cons(…) 无角表形态的边界，不在本
   任务判据内（两 B8 样本均带角表）。
3. 显式实参的**类型域读法**是 V1 口径（具名类型 + 实例化应用 + 兜底）；(T)=>U 等函数类型实参、
   命题供给的**语义级**识别（旧 propFromSupply 绑定 PropParam）未移植——命题供给角表目前按
   2.4 维持旧沉默，正面实现留 t26+。
4. HirInst 节点与「InstExpr 作值」路径（f<…> 值位、diagnostics 用 InstExpr callee 的
   symbol 提取）保持 t18/t22 现状，未随 B8 改动。
