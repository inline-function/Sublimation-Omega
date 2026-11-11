$cp = 'F:/Sublimation/frontend/build/classes/kotlin/main;C:/Users/ZhuanZ/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar'
$names = @("p1-root-legal","p2-root-illegal","p3-ref-readonly","p4-refmut-writable","p5-mut-to-refview","p6-ref-to-plain","p7-plain-to-refmut","p8-plain-to-ref","p9-prop-view","p10-regress-mut-sigma","p11-prop-plain-ok","p12-refmut-to-refview","p13-ref-param-readonly","p14-deepcopy-note")
foreach ($n in $names) {
  $src = "F:/Sublimation/frontend/probe-mut/cases-t98/$n.subl"
  $out = "F:/Sublimation/frontend/probe-mut/cases-t98/$n.json"
  & java -cp $cp sublimation.frontend.cli.MainKt compile $src --script -o $out > "F:/Sublimation/frontend/probe-mut/cases-t98/$n.log" 2>&1
  $j = if (Test-Path $out) { Get-Content -Raw $out } else { "" }
  $vw = ([regex]::Matches($j, "E-PROP-VIEW")).Count
  $ia = ([regex]::Matches($j, "E-IMMUT-ASSIGN")).Count
  $dd = ([regex]::Matches($j, "D-REF-DISPATCH")).Count
  Write-Host ($n.PadRight(26) + ': immut=' + $ia + ' propview=' + $vw + ' dispatch=' + $dd + ' exit=' + $LASTEXITCODE)
}