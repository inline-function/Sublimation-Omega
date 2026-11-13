# t154-parity 对拍脚本（诊断扩批三 / D 级效应告示 ×2）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t153-parity.ps1
#       `01 判别性探针（二码四面绿 + 纯方法豁免面）
#       `02 68 门差分台账（vs t122n 死冻锚：SAME 38 + 剥差同 11 + 纯W批一 5 + 纯批二 3 + 混合 6 + 预期差 4 + 转绿 1 = 68 闭合零未登记差——批三新码全落 expOld 件内，分类零波动）
#       `03 精确差分（vs t153gate：65 逐字同 + 3 滤 14 码同 + 零未登记差）
#       `04 stdlib 侧基线（vs t143stdlib：4 同 + 3 纯W批一 + 1 纯批二 + list 件构造族迁源面登记差——批三零命中）
#       `05 确定性 9/9 + 脚本门（run-gate）。
# 纪律：零分配 nid（复用 t151 既有 receiverOriginIsLocal 判定——无新类型求值）;口径跟 t151 现行判定——只告示不变判定。
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$javaExe = "F:\jdk-17.0.13.11-hotspot\bin\java.exe"
$cp = "$($root)/frontend/build/install/frontend/lib/*"
$probeDir = "$root/jsoncmp/out-subs"

function Run-Probe([string]$tag, [string]$ext, [string]$src) {
    $out = "$probeDir/t153-$tag.json"; $tmp = "$probeDir/t153-$tag.$ext"
    Set-Content -Path $tmp -Value $src -Encoding UTF8
    Remove-Item $out -Force -ErrorAction SilentlyContinue
    try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $tmp, "-o", $out) -Wait -NoNewWindow } catch { }
    if (-not (Test-Path $out)) { return "MISSING" }
    return Get-Content $out -Raw
}

# ---- `01 判别性探针：二码四面绿（编译过 + 落 supplement）+ 豁免面（纯方法零误报） ----
$e1 = Run-Probe "e1" "subs" "var xs = mutableListOf(1)`nxs.add(2)`nprint(xs)`n"
if ($e1 -notmatch 'ok": true' -or $e1 -notmatch 'D-EFFECT-LOCAL') { Write-Output '[parity] FAIL: e1 局部来源接收者写未绿（判别性样本面）'; exit 1 }
if ($e1 -match 'D-EFFECT-ESCAPE') { Write-Output '[parity] FAIL: e1 局部来源误落逃逸告示（互斥）'; exit 1 }
$e1n = Run-Probe "e1n" "subs" "var xs = listOf(1)`nprint(xs.map { x => x + 1 })`n"
if ($e1n -notmatch 'ok": true' -or $e1n -match 'D-EFFECT-LOCAL' -or $e1n -match 'D-EFFECT-ESCAPE') { Write-Output '[parity] FAIL: e1n 纯方法（无 @mut 写）误报'; exit 1 }
$e2 = Run-Probe "e2" "subs" "@impure fun f(xs : @mut List[Nat]) : Null { xs.add(2) }`n@impure fun main() : Null { f(mutableListOf(1)) }`n"
if ($e2 -notmatch 'ok": true' -or $e2 -notmatch 'D-EFFECT-ESCAPE') { Write-Output '[parity] FAIL: e2 外域形参逃逸未绿'; exit 1 }
if ($e2 -notmatch '外域形参') { Write-Output '[parity] FAIL: e2 逃逸路径未分型为外域形参'; exit 1 }
$e3 = Run-Probe "e3" "subs" "@impure fun outer() : Null {`n  var xs = mutableListOf(1)`n  @impure fun inner() : Null { xs.add(2) }`n  inner()`n}`nouter()`n"
if ($e3 -notmatch 'ok": true' -or $e3 -notmatch 'D-EFFECT-ESCAPE') { Write-Output '[parity] FAIL: e3 祖先帧捕获逃逸未绿'; exit 1 }
if ($e3 -notmatch '祖先帧捕获') { Write-Output '[parity] FAIL: e3 逃逸路径未分型为祖先帧捕获'; exit 1 }
$e5 = Run-Probe "e5" "subs" "@mut struct Box(@mut fld : @mut List[Nat])`n@impure fun main() : Null {`n  var b = Box(mutableListOf(1))`n  b.fld.add(2)`n  print(1)`n}`n"
if ($e5 -notmatch 'ok": true' -or $e5 -notmatch 'D-EFFECT-LOCAL') { Write-Output '[parity] FAIL: e5 字段随属主局部面未绿'; exit 1 }
$e6 = Run-Probe "e6" "subs" "fun g() : Nat {`n  var xs = mutableListOf(1)`n  xs.add(2)`n  return 1`n}`nprint(g())`n"
if ($e6 -notmatch 'ok": true' -or $e6 -notmatch 'D-EFFECT-LOCAL') { Write-Output '[parity] FAIL: e6 纯函数局部写（R-26.4 放行留痕）未绿'; exit 1 }
if ($e6 -match 'E-IMPURE-REQUIRED') { Write-Output '[parity] FAIL: e6 纯函数局部写被 @mut 方法闸误拦（判定变更!）'; exit 1 }
Write-Output "[parity] OK: 二码四面绿（局部顶层/纯函数/字段属主 + 形参逃逸/捕获逃逸）+ 纯方法豁免零误报;纯函数局部写零拦——只告示不变判定"


# ---- `02 `03 `04 台账三段（t154-gatecmp.js：闭合分类 + 零未登记差 + 新码归因台账）----
$nodeExe = "C:\Program Files\nodejs\node.exe"
$cmpLog = "$probeDir/t154-gatecmp.out"; $cmpErr = "$probeDir/t154-gatecmp.err"
Remove-Item $cmpLog -Force -ErrorAction SilentlyContinue; Remove-Item $cmpErr -Force -ErrorAction SilentlyContinue
$p = Start-Process -FilePath $nodeExe -ArgumentList @("$root/jsoncmp/scripts/t154-gatecmp.js") -Wait -PassThru -NoNewWindow -RedirectStandardOutput $cmpLog -RedirectStandardError $cmpErr
$cmpRc = $p.ExitCode
$cmpOut = @()
if (Test-Path $cmpLog) { $cmpOut += (Get-Content $cmpLog -Raw) }
if (Test-Path $cmpErr) { $cmpOut += (Get-Content $cmpErr -Raw) }
$cmpOut | Set-Content "$root/jsoncmp/out-subs/t154gate-ledger.txt" -Encoding UTF8
if ($cmpRc -ne 0) { Write-Output "[parity] FAIL: 台账三段未闭合（rc=$cmpRc）"; Write-Output $cmpOut; exit $cmpRc }
Write-Output $cmpOut
$blockOk = ($cmpOut -join "`n")
if ($blockOk -notmatch '\[2\].*=68') { Write-Output '[parity] FAIL: 68 门未闭合'; exit 1 }
if ($blockOk -notmatch '\[3\].*逐字同=65 滤14码同=3') { Write-Output '[parity] FAIL: 03 前后门计数波动'; exit 1 }
Write-Output "[parity] OK: 台账三段闭合（LEDGER: out-subs/t154gate-ledger.txt）"

# ---- `05 确定性（模块双导出 9/9）+ 脚本门 ----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$da = "$root/jsoncmp/out-subs/t154det-a"; $db = "$root/jsoncmp/out-subs/t154det-b"
Remove-Item $da -Recurse -Force -ErrorAction SilentlyContinue; Remove-Item $db -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $da | Out-Null; New-Item -ItemType Directory -Force -Path $db | Out-Null
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$da/examples__模块系统.json") -Wait -NoNewWindow } catch { }
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$db/examples__模块系统.json") -Wait -NoNewWindow } catch { }
$ja = Get-ChildItem $da -Filter "*.json"
$det = 0; $n = 0
foreach ($f in $ja) { $n++; if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$db/$($f.Name)" -Raw)) { $det++ } }
if ($det -ne $n -or $n -ne 9) { Write-Output "[parity] FAIL: 模块双导出确定性 $det/$n（应为 9）"; exit 1 }
Write-Output "[parity] OK: 模块双导出确定性 $det/$n"
& "$root/jsoncmp/out-subs/run-gate.ps1"
$gateRc = $LASTEXITCODE
if ($gateRc -ne 0) { Write-Output "[parity] FAIL: 脚本门未过（run-gate，exit=$gateRc）"; exit $gateRc }
Write-Output "[parity] PASSED: t154 诊断扩批三 机械证据五段全绿"
