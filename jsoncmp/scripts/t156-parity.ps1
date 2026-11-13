# t156-parity 对拍脚本（B3 axioms 公理库：A1–A3 + L1–L4;归纳法按 t91 暂缓）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t153-parity.ps1
#       `01 判别性探针（axioms 件并入默认挂载 + 8 条公理零红 + 用户限定调用绿）
#       `02 68 门差分台账（vs t122n 死冻锚：SAME 38 + 剥差同 11 + 纯W批一 5 + 纯批二 3 + 混合 6 + 预期差 4 + 转绿 1 = 68 闭合零未登记差——批三新码全落 expOld 件内，分类零波动）
#       `03 精确差分（vs t155gate：既有件逐字同 + axioms 新增件一枚零未登记差）
#       `04 stdlib 侧基线（vs t143stdlib：原 9 件闭合 + axioms 新增件 = 10）
#       `05 模块双导出确定性 10/10 + 脚本门（run-gate）。
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


# ---- `01 判别性模块探针：B3 axioms 公理库（默认挂载并入 + 快照面 + 隔离） ----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$pd = "$probeDir/t156probe"
if (Test-Path $pd) { Remove-Item $pd -Recurse -Force }
New-Item -ItemType Directory -Force -Path $pd | Out-Null
$p = Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$pd/examples__模块系统.json") -Wait -PassThru -NoNewWindow
if ($p.ExitCode -ne 0) { Write-Output "[parity] FAIL: 模块系统样本（含 axioms 默认挂载）须绿实得 rc=$($p.ExitCode)"; exit 1 }
$envs = Get-ChildItem $pd -Filter "*.json"
$n = ($envs | Measure-Object).Count
if ($n -ne 10) { Write-Output "[parity] FAIL: stdlib 件数须 10（原 9 + axioms）实得 $n"; exit 1 }
$ax = Get-Content "$pd/examples__模块系统__axioms.json" -Raw
if ($ax -notmatch '"ok": true') { Write-Output "[parity] FAIL: axioms envelope 须 ok=true（公理件零红）"; exit 1 }
$axn = ([regex]::Matches($ax, [regex]::Escape('"annotations": [') + '[^]]*axiom')).Count
if ($axn -ne 8) { Write-Output "[parity] FAIL: axioms envelope 须恰 8 条 @axiom 声明实得 $axn"; exit 1 }
# 隔离对照：用户目录挂 axioms 件——限定引用调用绿（B3 见面机制）+ 裸名仍 E-UNBOUND（零污染）
$ud = "$probeDir/t156user"
if (Test-Path $ud) { Remove-Item $ud -Recurse -Force }
New-Item -ItemType Directory -Force -Path $ud | Out-Null
[System.IO.File]::WriteAllText("$ud/.settings", "mount axioms`n")
New-Item -ItemType Directory -Force -Path "$ud/axioms" | Out-Null
[System.IO.File]::WriteAllText("$ud/axioms/.settings", '# 无依赖（命题位静默）' + "`n")
[System.IO.File]::WriteAllText("$ud/axioms/a.subl", '@axiom' + "`n" + 'fun `nat non-negative`[n]: Null <n >= 0>' + "`n")
[System.IO.File]::WriteAllText("$ud/主.subl", 'fun main() : Null {' + "`n" + '  axioms.`nat non-negative`() ' + "`n" + '  return null' + "`n" + '}' + "`n")
$up = Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $ud, "-o", "$ud/out.json") -Wait -PassThru -NoNewWindow
$upRc = $up.ExitCode
if ($upRc -ne 0) { Write-Output '[parity] FAIL: 用户限定调用 axioms 须绿实得 rc=' + $upRc; exit 1 }
Write-Output "[parity] OK: B3 axioms 落地（默认挂载并入 10 件 + 8 条公理零红 + 用户限定调用绿）"

# ---- `02 `03 `04 台账三段（t156-gatecmp.js：闭合分类 + 零未登记差 + 新码归因台账）----
$nodeExe = "C:\Program Files\nodejs\node.exe"
$cmpLog = "$probeDir/t154-gatecmp.out"; $cmpErr = "$probeDir/t154-gatecmp.err"
Remove-Item $cmpLog -Force -ErrorAction SilentlyContinue; Remove-Item $cmpErr -Force -ErrorAction SilentlyContinue
$p = Start-Process -FilePath $nodeExe -ArgumentList @("$root/jsoncmp/scripts/t156-gatecmp.js") -Wait -PassThru -NoNewWindow -RedirectStandardOutput $cmpLog -RedirectStandardError $cmpErr
$cmpRc = $p.ExitCode
$cmpOut = @()
if (Test-Path $cmpLog) { $cmpOut += (Get-Content $cmpLog -Raw) }
if (Test-Path $cmpErr) { $cmpOut += (Get-Content $cmpErr -Raw) }
$cmpOut | Set-Content "$root/jsoncmp/out-subs/t156gate-ledger.txt" -Encoding UTF8
if ($cmpRc -ne 0) { Write-Output "[parity] FAIL: 台账三段未闭合（rc=$cmpRc）"; Write-Output $cmpOut; exit $cmpRc }
Write-Output $cmpOut
$blockOk = ($cmpOut -join "`n")
if ($blockOk -notmatch '\[2\].*=68') { Write-Output '[parity] FAIL: 68 门未闭合'; exit 1 }
if ($blockOk -notmatch '\[3\].*逐字同=76 滤14码同=0') { Write-Output '[parity] FAIL: 03 前后门计数波动'; exit 1 }
Write-Output "[parity] OK: 台账三段闭合（LEDGER: out-subs/t156gate-ledger.txt）"

# ---- `05 确定性（模块双导出 9/9）+ 脚本门 ----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$da = "$root/jsoncmp/out-subs/t156det-a"; $db = "$root/jsoncmp/out-subs/t156det-b"
Remove-Item $da -Recurse -Force -ErrorAction SilentlyContinue; Remove-Item $db -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $da | Out-Null; New-Item -ItemType Directory -Force -Path $db | Out-Null
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$da/examples__模块系统.json") -Wait -NoNewWindow } catch { }
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$db/examples__模块系统.json") -Wait -NoNewWindow } catch { }
$ja = Get-ChildItem $da -Filter "*.json"
$det = 0; $n = 0
foreach ($f in $ja) { $n++; if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$db/$($f.Name)" -Raw)) { $det++ } }
if ($det -ne $n -or $n -ne 10) { Write-Output "[parity] FAIL: 模块双导出确定性 $det/$n（应为 10——原 9 + axioms）"; exit 1 }
Write-Output "[parity] OK: 模块双导出确定性 $det/$n（B3 前为 9）"
& "$root/jsoncmp/out-subs/run-gate.ps1"
$gateRc = $LASTEXITCODE
if ($gateRc -ne 0) { Write-Output "[parity] FAIL: 脚本门未过（run-gate，exit=$gateRc）"; exit $gateRc }
Write-Output "[parity] PASSED: t156 B3 axioms 公理库 机械证据五段全绿"
