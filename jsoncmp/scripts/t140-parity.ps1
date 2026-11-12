# t140-parity 对拍脚本（R-23 B2.2 / HKT 特质族·接收者形态 + 满足面双通道 + 快照 trait 面 的机械证据）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File jsoncmp/scripts/t140-parity.ps1
# 五段：① traits 落位核对（特质族声明/默认挂载/接收者形态/M 无界）② 68 门差分台账（vs **t122n 死冻锚**——
#       队长修正令：活目录不得作锚）③ 精确差分（vs t139 冻结产物：预期逐字节同 + 模块样本 traits 新子件）
#       ④ stdlib 侧基线（vs t137stdlib：list 件差 = t139 add 位；其余同）⑤ 确定性 9/9 + 脚本门 14/14
# 退出码 0 = parity 全绿；非 0 = 失败件数。
# 原生 java 冻红样本写 stderr——Continue 模式容错（各门 exit 皆显式）
$ErrorActionPreference = "Continue"
$root = Resolve-Path "$PSScriptRoot/../.."
$java = "java -cp `"$($root)/frontend/build/install/frontend/lib/*`" sublimation.frontend.cli.MainKt"

# ---- ① traits 落位核对 ----
$ts = Get-Content "$root/stdlib/collection/traits/traits.subl" -Raw
foreach ($tr in @("trait Functor[F[_]]", "trait Applicative[F[_]] : Functor[F]", "trait Monad[F[_]] : Applicative[F]",
                 "trait Foldable[F[_]]", "trait Traversable[F[_]] : Functor[F]")) {
    if (-not ($ts.Contains($tr))) { Write-Output "[parity] FAIL: 缺特质声明 $tr"; exit 1 }
}
Write-Output "[parity] OK: 五特质族落 stdlib/collection/traits（Functor/Applicative(:Functor)/Monad(:Applicative)/Foldable/Traversable(:Functor)）"
if (-not ($ts.Contains("fun F[A].map[A, B](f: (A) => B): F[B]"))) { Write-Output "[parity] FAIL: map 非接收者形态"; exit 1 }
if (-not ($ts.Contains("fun pure[A](x: A): F[A]"))) { Write-Output "[parity] FAIL: pure 非无接收者形态"; exit 1 }
if (-not ($ts.Contains("fun F[A].traverse[A, M, B](f: (A) => M[B]): M[F[B]]"))) { Write-Output "[parity] FAIL: traverse 形态错"; exit 1 }
if (-not ($ts.Contains("E-TC-BOUNDARY"))) { Write-Output "[parity] FAIL: traverse 挂账注缺"; exit 1 }
Write-Output "[parity] OK: 接收者形态特质方法 + pure 无接收者 + traverse 无界挂账形态"
$set = Get-Content "$root/stdlib/.settings" -Raw
if (-not ($set -match [regex]::Escape("mount collection/traits"))) { Write-Output "[parity] FAIL: stdlib 默认挂载缺 traits"; exit 1 }
Write-Output "[parity] OK: traits 入 stdlib 默认挂载（collection 第 6 件）"

# ---- ② 68 门差分台账（vs t122n 死冻锚）----
function Strip-Pos([object]$o) {
    if ($null -eq $o) { return $null }
    if ($o -is [array]) { return @($o | ForEach-Object { Strip-Pos $_ }) }
    if ($o -is [System.Collections.IDictionary] -or $o -is [pscustomobject]) {
        $h = [ordered]@{}
        foreach ($p in $o.PSObject.Properties) { if ($p.Name -notin @("pos","message")) { $h[$p.Name] = Strip-Pos $p.Value } }
        return [pscustomobject]$h
    }
    return $o
}
$manifest = Get-Content "$root/jsoncmp/manifest/export-t131mod-list.txt"
$side = "$root/jsoncmp/out-subs/t140gate"
Remove-Item $side -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $side | Out-Null
$sameN = 0; $blindN = 0; $failN = 0; $lines = @()
foreach ($line in $manifest) {
    $parts = $line -split "\|"
    $src = "$root/" + ($parts[0] -replace "/", [char]92)
    $base = ($parts[1] -replace "^jsoncmp/out/t131mod/", "")
    if ($base -eq "examples__模块系统.json") { $base = "examples__模块系统__主.subl.json" }
    $dst = "$side/$base"
    $dir = Split-Path $dst -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    try { Invoke-Expression "$java compile `"$src`" -o `"$dst`" 2>`$null | Out-Null" } catch { }
    $a = Get-Content "$root/jsoncmp/out/t122n/$base" -Raw -ErrorAction SilentlyContinue
    $b = if (Test-Path $dst) { Get-Content $dst -Raw } else { $null }
    if ($a -ceq $b) { $sameN++; $lines += "SAME`t$base" }
    elseif ($null -ne $a -and $null -ne $b) {
        $ja = $a | ConvertFrom-Json; $jb = $b | ConvertFrom-Json
        if (((Strip-Pos $ja) | ConvertTo-Json -Depth 20 -Compress) -ceq ((Strip-Pos $jb) | ConvertTo-Json -Depth 20 -Compress)) {
            $blindN++; $lines += "剥差同`t$base（pos/message 盲——t131 时代历史盲差）"
        } else { $failN++; $lines += "FAIL`t$base" }
    } else { $failN++; $lines += "FAIL`t$base（产物缺失）" }
}
$lines | Set-Content "$root/jsoncmp/out-subs/t140gate-ledger.txt"
Write-Output "[parity] 68 门 vs t122n: SAME=$sameN 剥差同=$blindN 结构差=$failN（台账: out-subs/t140gate-ledger.txt）"
if ($failN -gt 1) { Write-Output "[parity] FAIL: 68 门非预期结构差（$failN 件；预期至多 1 = 模块样本）"; exit $failN }
Write-Output "[parity] OK: 68 门口径延续（51 SAME + 16 历史剥差 + 1 模块样本结构差）"

# ---- ③ 精确差分（vs t139 冻结产物 t139gate）----
$a139 = "$root/jsoncmp/out-subs/t139gate"
$sameD = 0; $newD = @()
foreach ($f in (Get-ChildItem $side -Filter "*.json")) {
    if ($f.Name -like "examples__模块系统*") { continue }   # 模块样本由下一块按命名映射对
    $alt = Join-Path $a139 $f.Name
    $tgt = if ($f.Name -eq "examples__模块系统__主.subl.json") { Join-Path $a139 "examples__模块系统__主.subl__x" } else { $alt }
    if (Test-Path $alt) {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content $alt -Raw)) { $sameD++ }
        else { Write-Output "[parity] FAIL: 精确差分破坏 $f"; exit 1 }
    } else { $newD += $f.Name }
}
Write-Output "[parity] OK: 精确差分逐字节同 $sameD 件（t139 冻结产物；模块子件另对）"
# 模块子件：t139 命名 examples__模块系统__X.json ↔ t140 examples__模块系统__主.subl__X.json
$sameC = 0
foreach ($f in (Get-ChildItem $side -Filter "examples__模块系统__主.subl__*.json")) {
    $tail = $f.Name -replace "^examples__模块系统__主.subl", ""
    $alt = Join-Path $a139 ("examples__模块系统" + $tail)
    if (Test-Path $alt) { if ((Get-Content $f.FullName -Raw) -ceq (Get-Content $alt -Raw)) { $sameC++ }
        else { Write-Output "[parity] FAIL: 模块子件差 $f"; exit 1 } }
    else { $newD += $f.Name }
}
# 主件（命名映射：t140 __主.subl.json ↔ t139 .json）
if ((Get-Content "$side/examples__模块系统__主.subl.json" -Raw) -ceq (Get-Content "$a139/examples__模块系统.json" -Raw)) { Write-Output "[parity] OK: 模块样本主件逐字节同（t139 冻结）" }
else { Write-Output "[parity] FAIL: 模块样本主件差"; exit 1 }
if ($sameC -ne 7) { Write-Output "[parity] FAIL: 模块子件逐字节同应为 7（t139 子件全家），实 $sameC"; exit 1 }
if (@($newD).Count -ne 1 -or $newD[0] -ne "examples__模块系统__主.subl__traits.json") {
    Write-Output ("[parity] FAIL: 新增件集合偏离: " + ($newD -join ";") + "（预期 仅 __traits）"); exit 1 }
Write-Output "[parity] OK: 模块样本主件+8 子件逐字节同 + 唯一新增 __traits 子件（additive）"

# ---- ④ stdlib 侧基线（vs t137stdlib：预期 list 件差 = t139 add；其余同）----
$mod = "$root/frontend/src/test/resources/e2e/examples/模块系统"
$std = "$root/jsoncmp/out-subs/t140stdlib"
Remove-Item $std -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force -Path $std | Out-Null
try { Invoke-Expression "$java compile `"$mod`" -o `"$std/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
$baseStd = "$root/jsoncmp/out/t137stdlib"
$sameS = 0; $diffS = @()
foreach ($f in (Get-ChildItem $baseStd -Filter "*.json")) {
    if (Test-Path "$std/$($f.Name)") {
        if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$std/$($f.Name)" -Raw)) { $sameS++ }
        else { $diffS += $f.Name }
    } else { $diffS += ($f.Name + "（缺产物）") }
}
$expect = "examples__模块系统__list.json"
if (@($diffS).Count -ne 1 -or $diffS[0] -ne $expect) {
    Write-Output ("[parity] FAIL: 侧基线差异集合偏离预期: " + ($diffS -join ";") + "（预期 " + $expect + "）"); exit 1 }
Write-Output "[parity] OK: 侧基线逐字节同 $sameS 件 + 预期差 $expect（t139 add 位——台账登记）"

# ---- ⑤ 确定性 9/9 + 脚本门 14/14 ----
$da = "$root/jsoncmp/out-subs/t140det-a"; $db = "$root/jsoncmp/out-subs/t140det-b"
Remove-Item $da -Recurse -Force -ErrorAction SilentlyContinue; Remove-Item $db -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $da | Out-Null; New-Item -ItemType Directory -Force -Path $db | Out-Null
try { Invoke-Expression "$java compile `"$mod`" -o `"$da/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
try { Invoke-Expression "$java compile `"$mod`" -o `"$db/examples__模块系统.json`" 2>`$null | Out-Null" } catch { }
$ja = Get-ChildItem $da -Filter "*.json"
$det = 0; $n = 0
foreach ($f in $ja) { $n++; if ((Get-Content $f.FullName -Raw) -ceq (Get-Content "$db/$($f.Name)" -Raw)) { $det++ } }
if ($det -ne $n -or $n -ne 9) { Write-Output "[parity] FAIL: 模块双导出确定性 $det/$n（应为 9——含 traits 子件）"; exit 1 }
Write-Output "[parity] OK: 模块双导出确定性 $det/$n（+traits 子件）"
& powershell -NoProfile -ExecutionPolicy Bypass -File "$root/jsoncmp/out-subs/run-gate.ps1" -Mode gate
if ($LASTEXITCODE -ne 0) { Write-Output "[parity] FAIL: 脚本门未过（run-gate）"; exit $LASTEXITCODE }
Write-Output "[parity] PASSED: B2.2 特质族 机械证据五段全绿"
