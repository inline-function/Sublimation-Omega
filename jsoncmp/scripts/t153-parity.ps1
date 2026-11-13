# t153-parity 对拍脚本（诊断扩批二 / H 静态 ×4 + D 自动活 ×3）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t153-parity.ps1
#       `01 判别性探针（七码绿面 ×7 + 保守豁免面 ×9 含 panic 占位面/表达式体签名面）
#       `02 68 门差分台账（vs t122n 死冻锚：SAME 38 + 剥差同 11 + 纯W批一 5 + 纯批二 3 + 混合 6 + 预期差 4 + 转绿 1 = 68 闭合零未登记差）
#       `03 精确差分（vs t152gate：58 逐字同 + 10 滤 12 码同 + 零未登记差）
#       `04 stdlib 侧基线（vs t143stdlib：4 同 + 3 纯W批一 + 1 纯批二 + list 件构造族迁源面登记差）
#       `05 确定性 9/9 + 脚本门（run-gate）。
# 纪律：零分配 nid（诊断检查一律禁 etype/sigOf——批一教训;HIR 体 walker 只读既有节点字段）。
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

# ---- `01 判别性探针：七码七面绿（编译过 + 落新码）+ 保守豁免八面（零误报防护） ----
$h1 = Run-Probe "h1" "subs" "var xs = Cons(1, Cons(2, Nil))`nprint(xs)`n"
if ($h1 -notmatch 'ok": true' -or $h1 -notmatch 'H-CONS-BUILD') { Write-Output '[parity] FAIL: h1 Cons 手拼两层未绿'; exit 1 }
$h1n = Run-Probe "h1n" "subs" "var xs = Cons(1, Nil)`nprint(xs)`n"
if ($h1n -notmatch 'ok": true' -or $h1n -match 'H-CONS-BUILD') { Write-Output '[parity] FAIL: h1n 单层 Cons 误报'; exit 1 }
$h2 = Run-Probe "h2" "subs" "fun f() : Nat {`n  var dead = 3`n  return 1`n}`nprint(f())`n"
if ($h2 -notmatch 'ok": true' -or $h2 -notmatch 'H-UNUSED') { Write-Output '[parity] FAIL: h2 未用局部未绿'; exit 1 }
$h2n = Run-Probe "h2n" "subs" "fun f(_x : Nat) : Nat {`n  var _dead = 3`n  return 1`n}`nprint(f(1))`n"
$h2e = Run-Probe "h2e" "subs" "fun g(x : Nat) : Nat = 0`nprint(g(1))`n"
if ($h2e -notmatch 'ok": true' -or $h2e -match 'H-UNUSED') { Write-Output '[parity] FAIL: h2e 表达式体未用形参误报（占位/签名面）'; exit 1 }
if ($h2n -notmatch 'ok": true' -or $h2n -match 'H-UNUSED') { Write-Output '[parity] FAIL: h2n 弃用名面误报'; exit 1 }
$h3 = Run-Probe "h3" "subs" "struct Box[T](v : T)`nvar b = Box[Nat](3)`nprint(b)`n"
if ($h3 -notmatch 'ok": true' -or $h3 -notmatch 'H-EXPLICIT-TYPE-ARG') { Write-Output '[parity] FAIL: h3 冗余角表未绿'; exit 1 }
$h3n = Run-Probe "h3n" "subs" "fun wrap[T]() : Optional[T] = None`nvar v = wrap[Nat]()`nprint(v)`n"
if ($h3n -notmatch 'ok": true' -or $h3n -match 'H-EXPLICIT-TYPE-ARG') { Write-Output '[parity] FAIL: h3n 返回位理论参数误报'; exit 1 }
$h4 = Run-Probe "h4" "subs" "@impure fun pureAdd(a : Nat, b : Nat) : Nat = a + b`nprint(pureAdd(1, 2))`n"
if ($h4 -notmatch 'ok": true' -or $h4 -notmatch 'H-IMPURE-REDUNDANT') { Write-Output '[parity] FAIL: h4 冗余 @impure 未绿'; exit 1 }
$h4n = Run-Probe "h4n" "subs" "@impure fun f() : Null { print(1) }`nf()`n"
if ($h4n -notmatch 'ok": true' -or $h4n -match 'H-IMPURE-REDUNDANT') { Write-Output '[parity] FAIL: h4n 体不纯误报'; exit 1 }
$h4p = Run-Probe "h4p" "subs" "@impure fun httpGet(url : Nat) : Nat = panic()`nprint(httpGet(1))`n"
if ($h4p -notmatch 'ok": true' -or $h4p -match 'H-IMPURE-REDUNDANT') { Write-Output '[parity] FAIL: h4p panic 占位误报'; exit 1 }
$d1 = Run-Probe "d1" "subs" "fun f(x : Nat) : Nat {`n  var x = x + 1`n  return x`n}`nprint(f(1))`n"
if ($d1 -notmatch 'ok": true' -or $d1 -notmatch 'D-SHADOW') { Write-Output '[parity] FAIL: d1 遮蔽形参未绿'; exit 1 }
$d1n = Run-Probe "d1n" "subs" "fun f(x : Nat) : Nat {`n  var y = x + 1`n  return y`n}`nprint(f(1))`n"
if ($d1n -notmatch 'ok": true' -or $d1n -match 'D-SHADOW') { Write-Output '[parity] FAIL: d1n 无遮蔽误报'; exit 1 }
$d2 = Run-Probe "d2" "subs" "trait Sink[T] {`n  fun Sink[T].take(v : T) : Null`n}`nprint(1)`n"
if ($d2 -notmatch 'ok": true' -or $d2 -notmatch 'D-VARIANCE') { Write-Output '[parity] FAIL: d2 逆变参数未绿'; exit 1 }
$d2n = Run-Probe "d2n" "subs" "trait Box[T] {`n  fun Box[T].get() : T`n}`nprint(1)`n"
if ($d2n -notmatch 'ok": true' -or $d2n -match 'D-VARIANCE') { Write-Output '[parity] FAIL: d2n 纯协变参数误报'; exit 1 }
$d3 = Run-Probe "d3" "subs" "trait T { fun test() }`nfun takesT(x : T) : Nat = 1`n@impure fun f() : Null {`n  fun Nat.test() = print(self)`n  1.test()`n  print(takesT(1))`n}`nf()`n"
if ($d3 -notmatch 'ok": true' -or $d3 -notmatch 'D-TRAIT-LOCAL') { Write-Output '[parity] FAIL: d3 局部扩展满足未绿'; exit 1 }
$d3n = Run-Probe "d3n" "subs" "trait T { fun test() }`nfun Nat.test() = print(self)`nfun takesT(x : T) : Nat = 1`nprint(takesT(1))`n"
if ($d3n -notmatch 'ok": true' -or $d3n -match 'D-TRAIT-LOCAL') { Write-Output '[parity] FAIL: d3n 全局扩展满足误报'; exit 1 }
Write-Output "[parity] OK: 七码七面绿 + 保守豁免九面（含 panic 占位面/表达式体签名面）零误报"

# ---- `02 `03 `04 台账三段（t153-gatecmp.js：闭合分类 + 零未登记差 + 新码归因台账）----
$nodeExe = "C:\Program Files\nodejs\node.exe"
$cmpLog = "$probeDir/t153-gatecmp.out"; $cmpErr = "$probeDir/t153-gatecmp.err"
Remove-Item $cmpLog -Force -ErrorAction SilentlyContinue; Remove-Item $cmpErr -Force -ErrorAction SilentlyContinue
$p = Start-Process -FilePath $nodeExe -ArgumentList @("$root/jsoncmp/scripts/t153-gatecmp.js") -Wait -PassThru -NoNewWindow -RedirectStandardOutput $cmpLog -RedirectStandardError $cmpErr
$cmpRc = $p.ExitCode
$cmpOut = @()
if (Test-Path $cmpLog) { $cmpOut += (Get-Content $cmpLog -Raw) }
if (Test-Path $cmpErr) { $cmpOut += (Get-Content $cmpErr -Raw) }
$cmpOut | Set-Content "$root/jsoncmp/out-subs/t153gate-ledger.txt" -Encoding UTF8
if ($cmpRc -ne 0) { Write-Output "[parity] FAIL: 台账三段未闭合（rc=$cmpRc）"; Write-Output $cmpOut; exit $cmpRc }
Write-Output $cmpOut
$blockOk = ($cmpOut -join "`n")
if ($blockOk -notmatch '\[2\].*=68') { Write-Output '[parity] FAIL: 68 门未闭合'; exit 1 }
if ($blockOk -notmatch '\[3\].*逐字同=58 滤12码同=10') { Write-Output '[parity] FAIL: 03 前后门计数波动'; exit 1 }
Write-Output "[parity] OK: 台账三段闭合（LEDGER: out-subs/t153gate-ledger.txt）"

# ---- `05 确定性（模块双导出 9/9）+ 脚本门 ----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$da = "$root/jsoncmp/out-subs/t153det-a"; $db = "$root/jsoncmp/out-subs/t153det-b"
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
Write-Output "[parity] PASSED: t153 诊断扩批二 机械证据五段全绿"
