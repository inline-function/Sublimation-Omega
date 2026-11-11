$cp = 'F:/Sublimation/frontend/build/classes/kotlin/main;C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
$base = "F:/Sublimation/frontend/probe-mut/cases-t99"
$rows = Get-Content ($base + '/matrix.csv')
$bad = 0
foreach ($row in $rows) {
  if ($row.Trim() -eq "") { continue }
  $p = $row -split ";"
  $src = $base + "/" + $p[0] + ".subl"
  $out = $base + "/" + $p[0] + ".json"
  $logf = $base + "/" + $p[0] + ".log"
  & java -cp $cp sublimation.frontend.cli.MainKt compile $src --script -o $out > $logf 2>&1
  $j = if (Test-Path $out) { Get-Content -Raw $out } else { "" }
  $m = [regex]::Matches($j, "(?s)D-REF-DISPATCH")
  $last = ""
  if ($m.Count -gt 0) {
    $start = $m[$m.Count - 1].Index
    $last = $j.Substring($start, [Math]::Min(400, $j.Length - $start))
  }
  $share = if ($last -match "共享同一对象") { 1 } else { 0 }
  $copyx = if ($last -match "深拷隔离") { 1 } else { 0 }
  $mismatch = ([regex]::Matches($j, "E-TYPE-MISMATCH")).Count
  $judg = if ($mismatch -gt 0) { "reject" } elseif ($share -gt 0) { "share" } elseif ($copyx -gt 0) { "copy" } else { "n/a" }
  if ($judg -ne $p[1]) { $bad++ }
  Write-Host ($p[0].PadRight(22) + "expect=" + $p[1].PadRight(7) + " got=" + $judg.PadRight(7) + " notes=" + $m.Count)
}
if ($bad -eq 0) { Write-Host "MATRIX 16/16 OK" } else { Write-Host ("MATRIX " + $bad + " MISMATCH") }