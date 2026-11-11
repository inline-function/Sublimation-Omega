$cp = 'F:/Sublimation/frontend/build/classes/kotlin/main;C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
foreach ($n in @("p8-funref-impure", "p9-funref-pure")) {
  & java -cp $cp sublimation.frontend.cli.MainKt compile "F:/Sublimation/frontend/probe-mut/cases-t94/$n.subl" --script -o "F:/Sublimation/frontend/probe-mut/cases-t94/$n.json" > "F:/Sublimation/frontend/probe-mut/cases-t94/$n.log" 2>&1
  $t = Get-Content -Raw "F:/Sublimation/frontend/probe-mut/cases-t94/$n.json"
  $p = [regex]::Matches($t, '"purity":\s*"([a-z]+)"') | ForEach-Object { $_.Groups[1].Value }
  Write-Host ($n.PadRight(22) + ': purity=[' + ($p -join ',') + '] exit=' + $LASTEXITCODE)
}