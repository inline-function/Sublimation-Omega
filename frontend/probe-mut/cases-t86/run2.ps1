$cp = 'F:/Sublimation/frontend/build/classes/kotlin/main;' + 'C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
foreach ($n in @('f6d-lambda-param', 'f6h-pattern-bind', 'f6i-when-subj-bind', 'f6e2-neg-bare')) {
  $f = 'F:/Sublimation/frontend/probe-mut/cases-t86/' + $n + '.subl'
  if (Test-Path $f) { & java -cp $cp sublimation.frontend.cli.MainKt check $f > ($f + '.log') 2>&1
    Write-Host ($n.PadRight(22) + ' code=' + $LASTEXITCODE + '  ' + ((Get-Content ($f + '.log') | Where-Object { $_ -match '^[^s]' }) -join ' | ').Substring(0, [Math]::Min(230, ((Get-Content ($f + '.log') | Where-Object { $_ -match '^[^s]' }) -join ' | ').Length))) }
}