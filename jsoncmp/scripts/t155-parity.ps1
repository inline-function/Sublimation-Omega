# t155-parity 对拍脚本（S7 限面收边：跨模块不纯记账 + 三段调用链 + 多文件诊断行号）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t153-parity.ps1
#       `01 判别性模块探针（限面三面绿 + 两段限定零回归对照）
#       `02 68 门差分台账（vs t122n 死冻锚：SAME 38 + 剥差同 11 + 纯W批一 5 + 纯批二 3 + 混合 6 + 预期差 4 + 转绿 1 = 68 闭合零未登记差——批三新码全落 expOld 件内，分类零波动）
#       `03 精确差分（vs t154gate：68 逐字同零增量——限面改动仅模块态，SAMPLE 面零波）
#       `04 stdlib 侧基线（vs t143stdlib：4 同 + 3 纯W批一 + 1 纯批二 + list 件构造族迁源面登记差——批三零命中）
#       `05 模块双导出确定性 9/9 + 脚本门（run-gate）。
# 纪律：负例先红（三面实现前实测红/缺）;零分配 nid（快照纯度表按名回收）;单文件/单件模块零侵入（SAME 68 面逐字不变的承诺）。
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$javaExe = "F:\jdk-17.0.13.11-hotspot\bin\java.exe"
$cp = "$($root)/frontend/build/install/frontend/lib/*"
$probeDir = "$root/jsoncmp/out-subs"

function Run-Probe([string]$tag, [string]$ext, [string]$src) {
    $out = "$probeDir/t155-$tag.json"; $tmp = "$probeDir/t155-$tag.$ext"
    Set-Content -Path $tmp -Value $src -Encoding UTF8
    Remove-Item $out -Force -ErrorAction SilentlyContinue
    try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $tmp, "-o", $out) -Wait -NoNewWindow } catch { }
    if (-not (Test-Path $out)) { return "MISSING" }
    return Get-Content $out -Raw
}


function Run-ModProbe([string]$tag, [string]$mainSrc, [string]$childRel, [string]$childSrc, [string]$childSettings, [string]$rootSettings, [string]$midRel, [string]$midSrc) {
    $d = "$probeDir/t155mp-$tag"
    if (Test-Path $d) { Remove-Item $d -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $d | Out-Null
    if ($rootSettings) { [System.IO.File]::WriteAllText("$d/.settings", $rootSettings) }   # 无 BOM（.settings 行扫描器不剥 BOM）
    [System.IO.File]::WriteAllText("$d/main.subl", $mainSrc)
    if ($childRel) {
        $cd = "$d/$childRel"; New-Item -ItemType Directory -Force -Path $cd | Out-Null
        if ($childSettings) { [System.IO.File]::WriteAllText("$cd/.settings", $childSettings) }
        [System.IO.File]::WriteAllText("$cd/c.subl", $childSrc)
        if ($midRel) {
            $md = "$cd/$midRel"; New-Item -ItemType Directory -Force -Path $md | Out-Null
            [System.IO.File]::WriteAllText("$md/b.subl", $midSrc)
        }
    }
    $out = "$d/out.json"
    Remove-Item "$d/*.json" -Force -ErrorAction SilentlyContinue
    $p = Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $d, "-o", $out) -Wait -PassThru -NoNewWindow
    $res = @{ rc = $p.ExitCode; main = "MISSING" }
    if (Test-Path $out) { $res.main = Get-Content $out -Raw }
    return $res
}

# ---- `01 判别性模块探针：S7 限面收边三面绿 ----
# 面 1（跨模块不纯记账）：纯域调模块推断 impure → E-IMPURE-CALL（模块态纯度门）+ calleeImpure 真化
$r = Run-ModProbe "face1" "fun main() : Nat {`n  imp.count()`n  return 1`n}`n" "imp" "fun count() : Nat {`n  print(9)`n  return 1`n}`n" "" "mount imp`n"
if ($r.rc -ne 1) { Write-Output "[parity] FAIL: face1 纯域调模块 impure 须 rc=1（E-IMPURE-CALL）实得 rc=$($r.rc)"; exit 1 }
if ($r.main -notmatch 'E-IMPURE-CALL') { Write-Output "[parity] FAIL: face1 主 envelope 须落 E-IMPURE-CALL"; exit 1 }
if ($r.main -notmatch '"calleeImpure": true') { Write-Output "[parity] FAIL: face1 calleeImpure 须真化=true"; exit 1 }
# 面 1b：@impure 调用方——模块 impure 调用计证据（H-IMPURE-REDUNDANT 免误报）+ 零 E-IMPURE-CALL
$r = Run-ModProbe "face1b" "@impure fun main() : Null {`n  imp.hello()`n}`n" "imp" "@impure fun hello() : Null { print(1) }`n" "" "mount imp`n"
if ($r.rc -ne 0) { Write-Output "[parity] FAIL: face1b @impure 调用方调模块 @impure 函数须绿实得 rc=$($r.rc)"; exit 1 }
if ($r.main -match 'E-IMPURE-CALL') { Write-Output "[parity] FAIL: face1b impure 域内模块 impure 调用免闸（零 E-IMPURE-CALL 于主 envelope）"; exit 1 }
if ($r.main -match 'H-IMPURE-REDUNDANT') { Write-Output "[parity] FAIL: face1b 模块 impure 调用计证据——H-IMPURE-REDUNDANT 误报"; exit 1 }
# 面 2（三段调用链）：外段别名 → 中段子挂载 → 末段符号
$r = Run-ModProbe "face2" "fun main() : Nat {`n  return outer.inner.f(1)`n}`n" "outer" "fun pad() : Nat = 0`n" "mount inner`n" "mount outer`n" "inner" "fun f(n : Nat) : Nat = n + 1`n"
if ($r.rc -ne 0) { Write-Output "[parity] FAIL: face2 三段链 outer.inner.f(1) 须绿实得 rc=$($r.rc)"; exit 1 }
# 面 2b：中段未命中单错落位（免双报）
$r = Run-ModProbe "face2b" "fun main() : Nat {`n  return outer.nope.f(1)`n}`n" "outer" "fun pad() : Nat = 0`n" "" "mount outer`n"
$unb = ([regex]::Matches($r.main, 'E-UNBOUND-NAME')).Count
if ($unb -ne 1) { Write-Output "[parity] FAIL: face2b 中段未命中须恰好一错（免双报）实得 $unb"; exit 1 }
if ($r.main -notmatch '无子模块') { Write-Output "[parity] FAIL: face2b 错误文案须指名中段子模块缺失"; exit 1 }
# 面 3（多文件诊断行号）：file 归属 + 累计行号
$mfd = "$probeDir/t155mp-face3"
if (Test-Path $mfd) { Remove-Item $mfd -Recurse -Force }
New-Item -ItemType Directory -Force -Path $mfd | Out-Null
[System.IO.File]::WriteAllText("$mfd/a.subl", "fun one() : Nat = 1`nfun two() : Nat = 2`nfun three() : Nat = 3`nfun four() : Nat = 4`nfun five() : Nat = 5`n")
[System.IO.File]::WriteAllText("$mfd/b.subl", "fun six() : Nat = 6`nfun bad() : Nat = nope`n")
$mf = Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mfd, "-o", "$mfd/out.json") -Wait -PassThru -NoNewWindow
if ($mf.ExitCode -ne 1) { Write-Output "[parity] FAIL: face3 须 rc=1（E-UNBOUND-NAME）实得 $($mf.ExitCode)"; exit 1 }
$mfj = Get-Content "$mfd/out.json" -Raw
if ($mfj -notmatch '"file": "b.subl"') { Write-Output "[parity] FAIL: face3 须源件归属 file=b.subl"; exit 1 }
if ($mfj -notmatch '"pos": "8:19-8:22"') { Write-Output "[parity] FAIL: face3 须累计行号 8:19（a 五行 + 分隔 + b 二行）"; exit 1 }
# 对照面：两段限定 + 纯调模块纯函数（既有 t131 面零回归）
$r = Run-ModProbe "ctrl" "fun main() : Nat {`n  return tool.triple(4)`n}`n" "tool" "fun triple(n : Nat) : Nat = n * 3`n" "" "mount tool`n"
if ($r.rc -ne 0) { Write-Output "[parity] FAIL: 对照面 两段限定纯调用须绿实得 rc=$($r.rc)"; exit 1 }
if ($r.main -match '"calleeImpure": true') { Write-Output "[parity] FAIL: 对照面 纯模块调用 calleeImpure 须 false"; exit 1 }
Write-Output "[parity] OK: S7 限面三面绿（不纯记账/三段链/多文件行号）+ 既有两段限定面零回归"

# ---- `02 `03 `04 台账三段（t155-gatecmp.js：闭合分类 + 零未登记差 + 新码归因台账）----
$nodeExe = "C:\Program Files\nodejs\node.exe"
$cmpLog = "$probeDir/t154-gatecmp.out"; $cmpErr = "$probeDir/t154-gatecmp.err"
Remove-Item $cmpLog -Force -ErrorAction SilentlyContinue; Remove-Item $cmpErr -Force -ErrorAction SilentlyContinue
$p = Start-Process -FilePath $nodeExe -ArgumentList @("$root/jsoncmp/scripts/t155-gatecmp.js") -Wait -PassThru -NoNewWindow -RedirectStandardOutput $cmpLog -RedirectStandardError $cmpErr
$cmpRc = $p.ExitCode
$cmpOut = @()
if (Test-Path $cmpLog) { $cmpOut += (Get-Content $cmpLog -Raw) }
if (Test-Path $cmpErr) { $cmpOut += (Get-Content $cmpErr -Raw) }
$cmpOut | Set-Content "$root/jsoncmp/out-subs/t155gate-ledger.txt" -Encoding UTF8
if ($cmpRc -ne 0) { Write-Output "[parity] FAIL: 台账三段未闭合（rc=$cmpRc）"; Write-Output $cmpOut; exit $cmpRc }
Write-Output $cmpOut
$blockOk = ($cmpOut -join "`n")
if ($blockOk -notmatch '\[2\].*=68') { Write-Output '[parity] FAIL: 68 门未闭合'; exit 1 }
if ($blockOk -notmatch '\[3\].*逐字同=68 滤14码同=0') { Write-Output '[parity] FAIL: 03 前后门计数波动'; exit 1 }
Write-Output "[parity] OK: 台账三段闭合（LEDGER: out-subs/t155gate-ledger.txt）"

# ---- `05 确定性（模块双导出 9/9）+ 脚本门 ----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$da = "$root/jsoncmp/out-subs/t155det-a"; $db = "$root/jsoncmp/out-subs/t155det-b"
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
Write-Output "[parity] PASSED: t155 S7 限面收边 机械证据五段全绿"
