# t165-parity 对拍脚本（P-L1：compileFrontendWithStdlib 公共挂载入口——CLI 通道统一）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t165-parity.ps1
# 五段：
#  ① 导出 t165gate 77 件 + t165stdlib 10 件（node t165-gate-run.js，用 installDist 的 t165 构建）
#  ② 68 门台账（vs t122n 死冻锚：38/11/5/3/6/4/1 零漂移——P-L1 零产品差）
#  ③ 精确差分（vs t160gate：77 件逐字同——P-L1 核心断言=CLI 行为逐字零变化）
#  ④ stdlib 基线（vs t160stdlib 十件逐字同——stdlib 编译面零触及）
#  ⑤ 模块双导出确定性 10/10 + 脚本门（run-gate）
# 纪律：负例先红（T165 套件实现前 compileFrontendWithStdlib 未解析=compile 红）;零分配 nid;
#  既有件逐字不动（77/77 + stdlib 10 + run-gate 14）
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$nodeExe = "node"   # PATH 解析（Start-Process 全路径在沙箱下偶发 not-found，裸名稳定）

# ---- ① 导出（t165-gate-run.js）----
$p0 = Start-Process -FilePath $nodeExe -ArgumentList @("$root/jsoncmp/scripts/t165-gate-run.js") -Wait -PassThru -NoNewWindow
if ($p0.ExitCode -ne 0) { Write-Output "[parity] FAIL: gate-run 导出失败（exit=$($p0.ExitCode)）"; exit 1 }
$gateN = (Get-ChildItem "$root/jsoncmp/out-subs/t165gate" -Recurse -Filter *.json).Count
if ($gateN -ne 77) { Write-Output "[parity] FAIL: t165gate 件数须 77 实得 $gateN"; exit 1 }
Write-Output "[parity] OK: t165gate 导出 $gateN 件"

# ---- ② ③ ④ 台账（t165-gatecmp.js）----
$cmpLog = "$root/jsoncmp/out-subs/t165-gatecmp.out"; $cmpErr = "$root/jsoncmp/out-subs/t165-gatecmp.err"
Remove-Item $cmpLog -Force -ErrorAction SilentlyContinue; Remove-Item $cmpErr -Force -ErrorAction SilentlyContinue
$p = Start-Process -FilePath $nodeExe -ArgumentList @("$root/jsoncmp/scripts/t165-gatecmp.js") -Wait -PassThru -NoNewWindow -RedirectStandardOutput $cmpLog -RedirectStandardError $cmpErr
$cmpRc = $p.ExitCode
$cmpOut = @()
if (Test-Path $cmpLog) { $cmpOut += (Get-Content $cmpLog -Raw) }
if (Test-Path $cmpErr) { $cmpOut += (Get-Content $cmpErr -Raw) }
$cmpOut | Set-Content "$root/jsoncmp/out-subs/t165gate-ledger.txt" -Encoding UTF8
if ($cmpRc -ne 0) { Write-Output "[parity] FAIL: 台账未闭合（rc=$cmpRc）"; Write-Output $cmpOut; exit $cmpRc }
Write-Output $cmpOut
$blockOk = ($cmpOut -join "`n")
if ($blockOk -notmatch '[2].*=68') { Write-Output '[parity] FAIL: ② 68 门未闭合'; exit 1 }
if ($blockOk -notmatch '[3].*逐字同=77/77') { Write-Output '[parity] FAIL: ③ 77 件逐字同未闭合'; exit 1 }
if ($blockOk -notmatch '[4].*十件逐字同') { Write-Output '[parity] FAIL: ④ stdlib 十件同未闭合'; exit 1 }
Write-Output "[parity] OK: 台账闭合（LEDGER: out-subs/t165gate-ledger.txt）"

# ---- ⑤ 确定性（模块双导出 10/10）+ 脚本门 ----
$cp = "$($root)/frontend/build/install/frontend/lib/*"   # java 经 PATH 解析（Start-Process 绝对路径在沙箱下 not-found，& 调用稳定）
$probeDir = "$root/jsoncmp/out-subs"
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$da = "$probeDir/t165det-a"; $db = "$probeDir/t165det-b"
Remove-Item $da -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $db -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $da | Out-Null
New-Item -ItemType Directory -Force -Path $db | Out-Null
& java -cp $cp sublimation.frontend.cli.MainKt compile $mod -o "$da/examples__模块系统.json" | Out-Null
$rcA = $LASTEXITCODE
& java -cp $cp sublimation.frontend.cli.MainKt compile $mod -o "$db/examples__模块系统.json" | Out-Null
$rcB = $LASTEXITCODE
if ($rcA -ne 0 -or $rcB -ne 0) { Write-Output "[parity] FAIL: 模块导出退出码 a=$rcA b=$rcB"; exit 1 }
$ja = Get-ChildItem $da -Filter "*.json"
$det = 0; $n = 0
foreach ($f in $ja) { $n++; if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$db/$($f.Name)" -Raw)) { $det++ } }
if ($det -ne $n -or $n -ne 10) { Write-Output "[parity] FAIL: 模块双导出确定性 $det/$n（应为 10）"; exit 1 }
Write-Output "[parity] OK: 模块双导出确定性 $det/$n"
& "$root/jsoncmp/out-subs/run-gate.ps1"
$gateRc = $LASTEXITCODE
if ($gateRc -ne 0) { Write-Output "[parity] FAIL: 脚本门未过（run-gate，exit=$gateRc）"; exit $gateRc }
Write-Output "[parity] PASSED: t165 P-L1（公共挂载入口——CLI 通道统一） 机械证据五段全绿"
