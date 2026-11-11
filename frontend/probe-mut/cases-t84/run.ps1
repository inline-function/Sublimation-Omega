$lib = 'C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
$cp = 'F:/Sublimation/frontend/build/classes/kotlin/main;C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
foreach ($f in (Get-ChildItem -Path 'F:/Sublimation/frontend/probe-mut/cases-t84/' -Filter *.subl | Sort-Object Name)) {
  $log = $f.FullName + '.log'
  & java -cp $cp sublimation.frontend.cli.MainKt check $f.FullName --script > $log 2>&1
  $err = (Get-Content $log | Where-Object { $_ -match '^\[错误\]' }) -join ' | '

  Write-Host ($f.BaseName.PadRight(20) + ' code=' + $LASTEXITCODE + '  ' + $err)
}