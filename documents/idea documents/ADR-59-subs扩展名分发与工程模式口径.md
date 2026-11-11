# ADR-59 S0 任务：.subs 扩展名分发与工程模式口径（CLI 层）

- 状态：已落地（t100）
- 日期：2026-11-10
- 范围：t100「S0：.subs 扩展名分发 + 工程模式 & 拒绝的 CLI 端」——前端 CLI（Main.kt）按输入文件扩展名决定解析模式，不改 Parser / 语义层。

## 一、决策

1. .subs 等价于 --script：runCli 计算脚本模式为 opts.script（--script 标志）或输入文件扩展名为 .subs，统一用于 parse/check/compile 三个子命令的分发位（原 opts.script 三处引用全部替换）。
2. .subl 与其他扩展名保持默认工程模式：顶层只允许声明与 axiom 语句——脚本专属形态经既有解析层通道拒绝（E-SYNTAX 通用文案「顶层只能写声明，执行代码请放进 fun main()」；废除关键字 val/enum/def/impl 走既有专属码），本 ADR 不改消息、不改 Parser。
3. 双开合法：--script 与扩展名只共同决定同一布尔（OR），--script 配 .subl 与 .subs（加或不加 --script）同产脚本模式。

## 二、为什么不进解析层

扩展名是编译单元的 IO 属性（与 --script 同源的单位决策），Parser 的 scriptMode 参数由 CLI 注入即可；把扩展名判定放进 Parser 会让解析层依赖文件名约定，破坏 S-2 解析层零 IO 的分层（ADR-11 §一：CLI 负责装配）。

## 三、验收口径（与夜间基线红线对齐）

- .subl 语义逐字不变（本任务零 Parser / 语义层改动）；
- 全量只增不减（874 基线 + 本任务新增 3 例 CLI 测试）；
- e2e 冻红集 65/68 逐字不变；68 e2e 样本 JSON 零差异（规范导出与 out/t99 比对——导出走 .subl 默认工程模式通道，不受本任务影响）。

## 四、测试

CliTest 追加 3 例（包内复用 run/source 装配）：

- subsExtensionEqualsScriptFlag：.subs 源（顶层 var + fun）parse/check/compile 三命令全通，JSON envelope schema 与 ok 旗标对账；
- sublEngineeringModeRejectsScriptFormsUnchanged：.subl 无 --script 退出码 1，拒绝消息含既有「顶层只能写声明」文案（未新增或改动任何拒绝消息）；
- scriptFlagWithSublStillLegal：--script 配 .subl 与 --script 配 .subs 双开均合法。

## 五、边界

- 待裁项 C-1..C-8 不涉及（本任务不写推导、不碰 schema）。
- 用法串（USAGE）同步一行扩展名说明（CLI 层展示文案，非规程性改动）。
