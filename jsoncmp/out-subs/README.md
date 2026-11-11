# out-subs —— .subs 脚本推导基线（OQ-10，t119）

S2 推导链（t113-t118）导出门。68 工程样本全零元变量（直通），SAME 门从未覆盖推导面；
本基线以独立 .subs 脚本样本覆盖五类形态，**与 jsoncmp/out 的工程样本门分立、互不污染**。

## 目录

| 路径 | 职责 |
|---|---|
| samples/ | 样本源（12 个：10 × .subs + 2 × .subl 双模式对照） |
| t01/ | 基线产物（14 份 JSON + 14 份 log；当前树实跑落盘，**非手写**） |
| run2/ | gate 第二趟编译产物（双编译逐字比对证据） |
| run-gate.ps1 | 门脚本：baseline（落盘）/ gate（复跑+比对） |

## 门协议

```
# 1. 落基线（仅首次/树升级后）
./run-gate.ps1 -Mode baseline     # → t01/
# 2. 跑门（任意时候）
./run-gate.ps1 -Mode gate         # → run2/，逐字节比对 t01，输出 SAME/DIFF
```

gate 判据三件：
1. **双编译逐字节同**：14 份 JSON 与 t01 逐字相同（轮数确定性：退化样本的诊断消息携带 R=8 轮数，两趟一致）；
2. **退出码符合清单**：nosol×2 与 --no-degrade 门样本退出 1，其余退出 0；
3. **语义抽查**：码在/不在断言（E-INFER-NO-SOLUTION、E-INFER-DEGRADE-FORBIDDEN、D-INFER-DEGRADED、E-OMITTED-TYPE、E-TYPE-MISMATCH；退化产物禁出现 E- 码）+ ret-infer.subl 落 project / ret-infer.subs 落 script。

## 五类形态 × 产物（t01 实测）

| 形态 | 样本 | 产物语义（实测落点） |
|---|---|---|
| **收敛推导** | conv-forward.subs | ok=true；param x + funDecl 返回位 inferred+evidence（use:2:14 调用点、use:1:17 体位）共 3 载体 |
| | conv-use-first.subs | 先使用后定义（后到约束 R 轮消化） |
| | conv-negative-polarity.subs | 负位极性 glb 求解（上界 Nat） |
| | conv-field.subs | struct P(x) 字段从构造位推导（载体 2） |
| | conv-multi-use.subs | 多使用点约束汇聚；supplement D-TYPE-INFERRED ×2 |
| **返回推导（R-13 双模式共享面）** | ret-infer.subs / ret-infer.subl | 双模式均落 inferred（载体 1）；.subl 落 mode=project |
| | ret-infer-arith.subs | 算术体 x + 1 返回推导 |
| **不收敛退化** | degrade-tuple-grow.subs | ok=true；D-INFER-DEGRADED ×2（a/b 各一，四元组：函数+空位+use: 清单+原因分类「R 轮耗尽·类型随轮生长/振荡」，R=8）；空位填 Any、载体零发射、相关函数记 impure；fun ok 局部退化不牵连 |
| **无解硬拒（双模式）** | nosol.subs | ok=false；E-INFER-NO-SOLUTION + E-TYPE-MISMATCH（不可退化、不填 Any） |
| | nosol.subl | ok=false；工程模式硬拒：E-OMITTED-TYPE + E-TYPE-MISMATCH + E-SYNTAX |
| **--no-degrade 门** | degrade-tuple-grow.subs.nd | ok=false；E-INFER-DEGRADE-FORBIDDEN，且退化诊断不落袋（门触发⇒不退化） |
| | conv-forward.subs.nd | 收敛样本 + 门 ⇒ 静默放行（ok=true，退出 0） |
| 对照 | control-fully-annotated.subs | 全标注⇒零元变量直通：inferred/evidence 零发射（同 68 工程样本面） |

**负例红态产物归档**：t01/nosol.subs(.subl).json 与 *.nd.json 为红态产物（envelope ok=false，D-9 半成品落盘 + 退出码 1）——供「负例先红」复认：树升级后复跑 gate 时，红态形态改变即捕获回归。

## 与工程样本门的关系

- 本门只读写 `jsoncmp/out-subs/`；`jsoncmp/out/` 工程基线目录零接触。
- 工程样本若需同款覆盖，须另建样本并入 out/<tag>/ 目录（不得复用本目录产物）。

## 取证

- 当前树：frontend/build/install/frontend（jar 2026-11-11 16:13 落盘），java 17。
- 双编译：t01（第一趟）vs run2（第二趟）SAME=14/14 逐字节；退出码清单全符；语义抽查全过（gate exit 0）。
