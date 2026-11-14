# t160-parity 对拍脚本（R4-A0：D-DEEP-COPY 深拷指令 notes 化——v3 加性扩展）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t160-parity.ps1
#       `02 68 门台账（vs t122n：38/11/5/3/6/4/1 = 68 闭合，分类数与 t157 零漂移——深拷三件落预期差）
#       `03 精确差分（vs t157gate：74 逐字同 + 3 件 authorized 深拷 notes 加性 + deepCopy 真化，结构校验 + 单源 + 站点全覆盖）
#       `04 stdlib 基线（vs t143stdlib 口径延续）+ ④b vs t157stdlib 十件逐字同（零深拷站点）
#       `05 模块双导出确定性 10/10 + 脚本门（run-gate）
# 纪律：负例先红（T160 套件实现前 7 站点全红）;零分配 nid;既有件逐字不动（74/77 + stdlib 10 + run-gate 14）
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$javaExe = "F:\jdk-17.0.13.11-hotspot\bin\java.exe"
$cp = "$($root)/frontend/build/install/frontend/lib/*"
$probeDir = "$root/jsoncmp/out-subs"

# ---- `02 `03 `04 `04b 台账（t160-gatecmp.js）----
$nodeExe = "C:\Program Files\nodejs\node.exe"
$cmpLog = "$probeDir/t160-gatecmp.out"; $cmpErr = "$probeDir/t160-gatecmp.err"
Remove-Item $cmpLog -Force -ErrorAction SilentlyContinue; Remove-Item $cmpErr -Force -ErrorAction SilentlyContinue
$p = Start-Process -FilePath $nodeExe -ArgumentList @("$root/jsoncmp/scripts/t160-gatecmp.js") -Wait -PassThru -NoNewWindow -RedirectStandardOutput $cmpLog -RedirectStandardError $cmpErr
$cmpRc = $p.ExitCode
$cmpOut = @()
if (Test-Path $cmpLog) { $cmpOut += (Get-Content $cmpLog -Raw) }
if (Test-Path $cmpErr) { $cmpOut += (Get-Content $cmpErr -Raw) }
$cmpOut | Set-Content "$root/jsoncmp/out-subs/t160gate-ledger.txt" -Encoding UTF8
if ($cmpRc -ne 0) { Write-Output "[parity] FAIL: 台账未闭合（rc=$cmpRc）"; Write-Output $cmpOut; exit $cmpRc }
Write-Output $cmpOut
$blockOk = ($cmpOut -join "`n")
if ($blockOk -notmatch '\[2\].*=68') { Write-Output '[parity] FAIL: 68 门未闭合'; exit 1 }
if ($blockOk -notmatch '\[3\].*逐字同=74 authorized') { Write-Output '[parity] FAIL: 03 74 逐字同 + authorized 三件未闭合'; exit 1 }
if ($blockOk -notmatch '\[4\].*总=10') { Write-Output '[parity] FAIL: 04 stdlib 基线未闭合'; exit 1 }
if ($blockOk -notmatch '\[4b\].*十件逐字同') { Write-Output '[parity] FAIL: ④b 十件逐字同未闭合'; exit 1 }
Write-Output "[parity] OK: 台账闭合（LEDGER: out-subs/t160gate-ledger.txt）"

# ---- `05 确定性（模块双导出 10/10）+ 脚本门 ----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$da = "$probeDir/t160det-a"; $db = "$probeDir/t160det-b"
Remove-Item $da -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $db -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $da | Out-Null
New-Item -ItemType Directory -Force -Path $db | Out-Null
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$da/examples__模块系统.json") -Wait -NoNewWindow | Out-Null } catch {}
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$db/examples__模块系统.json") -Wait -NoNewWindow | Out-Null } catch {}
$ja = Get-ChildItem $da -Filter "*.json"
$det = 0; $n = 0
foreach ($f in $ja) { $n++; if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$db/$($f.Name)" -Raw)) { $det++ } }
if ($det -ne $n -or $n -ne 10) { Write-Output "[parity] FAIL: 模块双导出确定性 $det/$n（应为 10）"; exit 1 }
Write-Output "[parity] OK: 模块双导出确定性 $det/$n"
& "$root/jsoncmp/out-subs/run-gate.ps1"
$gateRc = $LASTEXITCODE
if ($gateRc -ne 0) { Write-Output "[parity] FAIL: 脚本门未过（run-gate，exit=$gateRc）"; exit $gateRc }
Write-Output "[parity] PASSED: t160 R4-A0（D-DEEP-COPY 深拷指令 notes 化——v3 加性扩展） 机械证据五段全绿"
