$cp = 'F:/Sublimation/frontend/build/classes/kotlin/main;C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
foreach ($n in @("q1-fire-declared", "q2-fire-builtin", "q3-release-context", "q4-release-axiom", "q5-release-unchecked", "q6-release-impure-caller", "q7-release-borrowed", "q8-lambda-absorb", "q9-fire-direct-lambda", "q10-nested-pure-lambda")) {
  $src = "F:/Sublimation/frontend/probe-mut/cases-t96/$n.subl"
  $out = "F:/Sublimation/frontend/probe-mut/cases-t96/$n.json"
  & java -cp $cp sublimation.frontend.cli.MainKt compile $src --script -o $out > "F:/Sublimation/frontend/probe-mut/cases-t96/$n.log" 2>&1
  $j = if (Test-Path $out) { Get-Content -Raw $out } else { "" }
  $gate = [regex]::Matches($j, '"code":\s*"E-IMPURE-CALL",\s*"severity":\s*"([a-z]+)"') | ForEach-Object { $_.Groups[1].Value }
  if ($gate.Count -eq 0) { $gate = [regex]::Matches($j, '"severity":\s*"?([a-z]+)"?,\s*"code":\s*"?E-IMPURE-CALL"?') | ForEach-Object { $_.Groups[1].Value } }
  Write-Host ($n.PadRight(26) + ': gate=[' + ($gate -join ',') + '] exit=' + $LASTEXITCODE)
}