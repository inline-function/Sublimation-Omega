# t117 R 轮重跑——R 池观测表（附录 B H3 / OQ-2 分布口径）

机器口径：compileWithRounds（Semantic.kt）逐轮落表；每轮一全新 FUEL_DEFAULT=237500 燃料池
（每轮 new Elaborator ⇒ 每轮新 TypeInfer，setFuelLabel 按轮标记）。fuelMaxConsumed=该轮池峰值消耗。
采集：T117Debug.dump（frontend 测试树，2026-11-11 实跑）。

## 观测（脚本模式样本）

| 样本 | roundsUsed | notConverged | degraded | 轮序（round|label|metaCount|fuelMax|converged） |
|---|---|---|---|---|
| conv3（fun main() = double(5); fun double(x) = x） | 3 | false | false | 1|infer-r1|3|1|false；2|infer-r2|3|1|false；3|infer-r3|3|1|**true** |
| blkret（块体 return 推导 fun f(n){return n+1}） | 3 | false | false | 1|infer-r1|2|1|false；2|infer-r2|2|1|false；3|infer-r3|2|1|**true** |
| nonconv（互递归元组生长 fun a(x: Nat)=(b(x),0); fun b(x: Nat)=(a(x),1)） | 8+1 | true | true | 1..8|infer-r1..r8|2|1|false ×8；9|**infer-degraded**|2|1|false |
| zero（零元变量工程单元 fun add(a: Nat, b: Nat): Nat = a + b） | 1 | false | false | 1|infer-r1|**0**|1|**true**（单趟直通门） |

## 结论（供 OQ-2 终裁）

1. **零元变量 ⇒ 单趟直通**成立：68 工程样本 SAME 门逐字节不破（build/e2e-export-t117 68/68 vs jsoncmp/out/t113n）。
2. 收敛样本轮数 = 3（先使用后定义链）/ 3（块体 return），均在 R=8 预算内、燃料远未耗尽（peak=1/237500）。
3. 真不收敛样本（互递归元组嵌套生长）8 轮放满：**脚本 ⇒ infer-degraded 退化触发**（impure 化再跑一轮），
   **工程 ⇒ E-INFER-NOT-CONVERGED 硬拒**——预算形态=轮数上限触发而非燃料耗尽（OQ-2 裁 R 或降预算均可）。
4. 燃料池按轮独立（label 化），未观测到跨轮累积——H3 方案 1（R 池）落地形态机器可检。
