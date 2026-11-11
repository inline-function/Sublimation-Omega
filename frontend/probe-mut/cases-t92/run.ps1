$cp = 'F:/Sublimation/frontend/build/classes/kotlin/main;' + 'C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
$mods = @('p6-prop-or', 'p7-user-iff', 'p9-prop-iff', 'p10-prop-top', 'p11-prop-amp-retired')
foreach ($f in (Get-ChildItem -Path 'F:/Sublimation/frontend/probe-mut/cases-t92/' -Filter *.subl | Sort-Object Name)) {
  $args = @($f.FullName)
  if ($mods -notcontains $f.BaseName) { $args += '--script' }
  & java -cp $cp sublimation.frontend.cli.MainKt check @args > ($f.FullName + '.log') 2>&1
  $err = (Get-Content ($f.FullName + '.log') | Where-Object { $_ -match '^[\u9519\u8bef]' }) -join ' | '

  Write-Host ($f.BaseName.PadRight(22) + ' code=' + $LASTEXITCODE + '  ' + $err)
}