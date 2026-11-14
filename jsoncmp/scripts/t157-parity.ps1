# t157-parity 对拍脚本（B3 后续件：跨模块命题实例化通道收口 + 注入一致性 + M/S/T/G 扩容）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t157-parity.ps1
#       `01 判别性探针（限定 callee 角表通道：[]+<> 双绿 + 反例精确 E 级 + axioms 16 条零红 + 裸名隔离）
#       `02 68 门台账（vs t122n：38/11/5/3/6/4/1 = 68 闭合零波动）
#       `03 精确差分（vs t156gate：既有件逐字同 76 + axioms 件 8→16 扩容 authorized）
#       `04 stdlib 基线（vs t143stdlib 10 闭合）+ ④b vs t156stdlib（九件逐字同 + axioms 8→16）
#       `05 模块双导出确定性 10/10 + 脚本门（run-gate）。
# 纪律：负例先红（角表通道修前 CLI 探针两路实测红）;零分配 nid;既有件逐字不动（76 逐字同承诺）。
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$javaExe = "F:\jdk-17.0.13.11-hotspot\bin\java.exe"
$cp = "$($root)/frontend/build/install/frontend/lib/*"
$probeDir = "$root/jsoncmp/out-subs"

# ---- `01 判别性探针：跨模块 callee 角表通道 + axioms 扩容面 + 裸名隔离 ----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$pd = "$probeDir/t157probe"
if (Test-Path $pd) { Remove-Item $pd -Recurse -Force }
New-Item -ItemType Directory -Force -Path $pd | Out-Null
$p = Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$pd/examples__模块系统.json") -Wait -PassThru -NoNewWindow
if ($p.ExitCode -ne 0) { Write-Output "[parity] FAIL: 模块系统样本（含 axioms 默认挂载）须绿实得 rc=$($p.ExitCode)"; exit 1 }
$envs = Get-ChildItem $pd -Filter "*.json"
$n = ($envs | Measure-Object).Count
if ($n -ne 10) { Write-Output "[parity] FAIL: stdlib 件数须 10 实得 $n"; exit 1 }
$ax = Get-Content "$pd/examples__模块系统__axioms.json" -Raw
if ($ax -notmatch '"ok": true') { Write-Output '[parity] FAIL: axioms envelope 须 ok=true'; exit 1 }
$axn = ([regex]::Matches($ax, [regex]::Escape('"annotations": [') + '[^]]*axiom')).Count
if ($axn -ne 16) { Write-Output "[parity] FAIL: axioms envelope 须恰 16 条 @axiom 声明实得 $axn"; exit 1 }
foreach ($nm in @('map get put', 'set insert contains', 'text concat empty', 'range membership')) {
    if ($ax -notlike ("*" + $nm + "*")) { Write-Output ("[parity] FAIL: 扩容公理 " + $nm + " 须在 axioms 件"); exit 1 }
}
# 跨模块 callee 角表通道（t157 收口）：mod.gid[Nat] + mod.witness<谓词> 绿;反例 mod.np<0>() E-CALL-ANGLE
$ud = "$probeDir/t157mod"
if (Test-Path $ud) { Remove-Item $ud -Recurse -Force }
New-Item -ItemType Directory -Force -Path $ud | Out-Null
[System.IO.File]::WriteAllText("$ud/.settings", "mount mod" + "`n")
$umd = "$ud/mod"; New-Item -ItemType Directory -Force -Path $umd | Out-Null
[System.IO.File]::WriteAllText("$umd/w.subl", 'fun witness[@q]<q>: Null {}' + "`n" + 'fun gid[T](x: T): T { return x }' + "`n" + 'fun np(x: Nat): Nat { return x }' + "`n")
[System.IO.File]::WriteAllText("$ud/main.subl", 'fun main() : Null {' + "`n" + '  var v = mod.gid[Nat]((0))' + "`n" + '  mod.witness<forall n. even<n>>()' + "`n" + '  mod.np<0>()' + "`n" + '  return null' + "`n" + '}' + "`n")
$up = Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $ud, "-o", "$ud/out.json") -Wait -PassThru -NoNewWindow
$upOut = ''
if (Test-Path "$ud/out.json") { $upOut = Get-Content "$ud/out.json" -Raw }
if ($up.ExitCode -eq 0) { Write-Output "[parity] FAIL: mod.np<0>() 反例须红（np 无 PropParam）;rc=0"; exit 1 }
if ($upOut -notmatch '(?s)E-CALL-ANGLE.*`np`') { Write-Output '[parity] FAIL: E-CALL-ANGLE 须精确指向函数名 np'; exit 1 }
[System.IO.File]::WriteAllText("$ud/main.subl", 'fun main() : Null {' + "`n" + '  var v = mod.gid[Nat]((0))' + "`n" + '  mod.witness<forall n. even<n>>()' + "`n" + '  return null' + "`n" + '}' + "`n")
$up2 = Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $ud, "-o", "$ud/out.json") -Wait -PassThru -NoNewWindow
if ($up2.ExitCode -ne 0) { Write-Output "[parity] FAIL: 限定 callee 角表通道（[] 类型实参 + <> 命题供给）须绿 rc=$($up2.ExitCode)"; exit 1 }
# 裸名链隔离维持（t156 门 4 口径逐字延续）
$bd = "$probeDir/t157bare"
if (Test-Path $bd) { Remove-Item $bd -Recurse -Force }
New-Item -ItemType Directory -Force -Path $bd | Out-Null
[System.IO.File]::WriteAllText("$bd/.settings", "mount list" + "`n")
[System.IO.File]::WriteAllText("$bd/main.subl", 'fun main() : Null { `nat non-negative`() return null }' + "`n")
$bp = Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $bd, "-o", "$bd/out.json") -Wait -PassThru -NoNewWindow
$bpOut = ''
if (Test-Path "$bd/out.json") { $bpOut = Get-Content "$bd/out.json" -Raw }
if ($bpOut -notmatch 'E-UNBOUND-NAME') { Write-Output '[parity] FAIL: 公理名裸用须 E-UNBOUND-NAME（裸名隔离维持）'; exit 1 }
Write-Output "[parity] OK: 跨模块角表通道收口（[]+<> 双绿 + 反例精确 E 级）+ axioms 16 条零红 + 裸名隔离维持"

# ---- `02 `03 `04 台账（t157-gatecmp.js：闭合分类 + 零未登记差 + ④b 扩容校验）----
$nodeExe = "C:\Program Files\nodejs\node.exe"
$cmpLog = "$probeDir/t157-gatecmp.out"; $cmpErr = "$probeDir/t157-gatecmp.err"
Remove-Item $cmpLog -Force -ErrorAction SilentlyContinue; Remove-Item $cmpErr -Force -ErrorAction SilentlyContinue
$p = Start-Process -FilePath $nodeExe -ArgumentList @("$root/jsoncmp/scripts/t157-gatecmp.js") -Wait -PassThru -NoNewWindow -RedirectStandardOutput $cmpLog -RedirectStandardError $cmpErr
$cmpRc = $p.ExitCode
$cmpOut = @()
if (Test-Path $cmpLog) { $cmpOut += (Get-Content $cmpLog -Raw) }
if (Test-Path $cmpErr) { $cmpOut += (Get-Content $cmpErr -Raw) }
$cmpOut | Set-Content "$root/jsoncmp/out-subs/t157gate-ledger.txt" -Encoding UTF8
if ($cmpRc -ne 0) { Write-Output "[parity] FAIL: 台账未闭合（rc=$cmpRc）"; Write-Output $cmpOut; exit $cmpRc }
Write-Output $cmpOut
$blockOk = ($cmpOut -join "`n")
if ($blockOk -notmatch '\[2\].*=68') { Write-Output '[parity] FAIL: 68 门未闭合'; exit 1 }
if ($blockOk -notmatch '\[3\].*逐字同=76 滤14码同=0.*axioms扩容件=1') { Write-Output '[parity] FAIL: 03 既有件逐字同 + axioms 扩容件=1 波动'; exit 1 }
if ($blockOk -notmatch '\[4b\].*九件逐字同 \+ axioms 件 8→16') { Write-Output '[parity] FAIL: ④b 九件逐字同 + axioms 8→16 未闭合'; exit 1 }
Write-Output "[parity] OK: 台账闭合（LEDGER: out-subs/t157gate-ledger.txt）"

# ---- `05 确定性（模块双导出 10/10）+ 脚本门 ----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$da = "$root/jsoncmp/out-subs/t157det-a"; $db = "$root/jsoncmp/out-subs/t157det-b"
Remove-Item $da -Recurse -Force -ErrorAction SilentlyContinue; Remove-Item $db -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $da | Out-Null; New-Item -ItemType Directory -Force -Path $db | Out-Null
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$da/examples__模块系统.json") -Wait -NoNewWindow } catch { }
try { Start-Process -FilePath $javaExe -ArgumentList @("-cp", $cp, "sublimation.frontend.cli.MainKt", "compile", $mod, "-o", "$db/examples__模块系统.json") -Wait -NoNewWindow } catch { }
$ja = Get-ChildItem $da -Filter "*.json"
$det = 0; $n = 0
foreach ($f in $ja) { $n++; if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$db/$($f.Name)" -Raw)) { $det++ } }
if ($det -ne $n -or $n -ne 10) { Write-Output "[parity] FAIL: 模块双导出确定性 $det/$n（应为 10）"; exit 1 }
Write-Output "[parity] OK: 模块双导出确定性 $det/$n（t156 起为 10）"
& "$root/jsoncmp/out-subs/run-gate.ps1"
$gateRc = $LASTEXITCODE
if ($gateRc -ne 0) { Write-Output "[parity] FAIL: 脚本门未过（run-gate，exit=$gateRc）"; exit $gateRc }
Write-Output "[parity] PASSED: t157 B3 后续件（跨模块命题实例化收口 + 注入一致性 + M/S/T/G 扩容） 机械证据五段全绿"
