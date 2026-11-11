$cp = 'F:/Sublimation/frontend/build/classes/kotlin/main;C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
foreach ($n in @("q1-pass-dstructure", "q2-pass-minus", "q3-fire-passthrough", "q4-fire-constant", "q5-escape-impure", "q6-escape-unchecked", "q7-pass-method-sugar", "q8-fire-method-sugar", "q9-pass-field", "q10-pass-nested-when", "q11-fire-local-fun", "q12-mutual-limitation")) {
  $src = "F:/Sublimation/frontend/probe-mut/cases-t97/$n.subl"
  $out = "F:/Sublimation/frontend/probe-mut/cases-t97/$n.json"
  & java -cp $cp sublimation.frontend.cli.MainKt compile $src --script -o $out > "F:/Sublimation/frontend/probe-mut/cases-t97/$n.log" 2>&1
  $j = if (Test-Path $out) { Get-Content -Raw $out } else { "" }
  $gate = [regex]::Matches($j, 'E-NON-STRUCTURAL-REC') | ForEach-Object { 1 }
  Write-Host ($n.PadRight(24) + ': fires=' + $gate.Count + ' exit=' + $LASTEXITCODE)
}