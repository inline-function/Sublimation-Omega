$cp = 'F:/Sublimation/frontend/build/classes/kotlin/main;C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
foreach ($n in @("impure-body", "pure-body")) {
  & java -cp $cp sublimation.frontend.cli.MainKt compile "F:/Sublimation/frontend/probe-mut/cases-t94/$n.subl" --script -o "F:/Sublimation/frontend/probe-mut/cases-t94/$n.json" > $null 2>&1
  $j = Get-Content -Raw "F:/Sublimation/frontend/probe-mut/cases-t94/$n.json" | ConvertFrom-Json
  $lam = $j.hir | ConvertTo-Json -Depth 20 | Select-String -Pattern '"purity":"([A-Z]+)"' -AllMatches | ForEach-Object { $_.Matches.Groups[1].Value } | Select-Object -First 3
  Write-Host ($n + ": purity=" + ($lam -join ","))
}