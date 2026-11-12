# t137 stdlib 件侧基线（B0-0 + B1 落地态）

- 产物：`examples__模块系统.json` + 7 个挂载模块产物（tool + stdlib list/optional/set/map/result/math —— 钻石形备忘按首次路径记 unitPath：optional/set 见于 `__list__optional/__list__set`）。
- 口径：侧基线（stdlib 件为新面，不入 t74/t122n 判 diff）；主门 = e2e 68/68 SAME（jsoncmp/out-subs/t137gate*/ 落实）。
- 再生：`scripts/t137-parity.ps1` 第 ③ 段（编译 `frontend/src/test/resources/e2e/examples/模块系统` 至本目录）。
