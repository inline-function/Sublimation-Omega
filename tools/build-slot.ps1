# build-slot.ps1 — Gradle 构建槽位互斥（S-3 §8.3 的机械落实）
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools/build-slot.ps1 status
#   pwsh tools/build-slot.ps1 acquire -Note 't51'
#   pwsh tools/build-slot.ps1 release
#   pwsh tools/build-slot.ps1 run -Note 't51' -- :frontend:test --rerun-tasks
# 规则：同一时刻只允许一人运行 Gradle；槽位文件 = <repo>/.build-slot
param(
    [Parameter(Position=0)][ValidateSet('status','acquire','release','run')][string]$Action = 'status',
    [string]$Note = '',
    [int]$StaleMinutes = 30,
    [string]$GradleArgs = '',
    [Parameter(ValueFromRemainingArguments=$true)][string[]]$Rest
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$slot = Join-Path $root '.build-slot'

function Get-Holder {
    if (-not (Test-Path $slot)) { return $null }
    $raw = Get-Content $slot -Raw -ErrorAction SilentlyContinue
    if (-not $raw) { return $null }
    $parts = $raw.Trim() -split '\|'
    $holder = [pscustomobject]@{ Pid = 0; Started = $null; Note = ''; Raw = $raw.Trim() }
    if ($parts.Count -ge 3) {
        $holder.Pid = [int]$parts[0]
        $holder.Started = $parts[1]
        $holder.Note = $parts[2]
    }
    return $holder
}

function Test-Stale($h) {
    if (-not $h) { return $false }
    if ($h.Started) {
        try {
            $t = [datetime]::Parse($h.Started)
            if (((Get-Date) - $t).TotalMinutes -gt $StaleMinutes) { return $true }
        } catch { }
    }
    # pid 判活的坑：acquire 常在短命进程里执行（执行完就退出），若只看 pid 会把合法持有误判为失效。
    # 故：仅在「pid 已死 **且** 已过 grace 期」时才判失效；否则一律以时间门槛为准。
    $graceMin = 2
    if ($h.Pid -gt 0 -and $h.Started) {
        try {
            $ageOk = (((Get-Date) - [datetime]::Parse($h.Started)).TotalMinutes -gt $graceMin)
            if ($ageOk -and -not (Get-Process -Id $h.Pid -ErrorAction SilentlyContinue)) { return $true }
        } catch { }
    }
    return $false
}

switch ($Action) {
    'status' {
        $h = Get-Holder
        if (-not $h) { Write-Output 'SLOT=FREE' }
        else {
            $stale = Test-Stale $h
            Write-Output ('SLOT=BUSY pid=' + $h.Pid + ' started=' + $h.Started + ' note=' + $h.Note + ' stale=' + $stale)
        }
        exit 0
    }
    'acquire' {
        $h = Get-Holder
        if ($h) {
            if (Test-Stale $h) {
                Write-Output ('SLOT=STALE_STOLEN prev_pid=' + $h.Pid + ' prev_note=' + $h.Note)
                Remove-Item $slot -Force -ErrorAction SilentlyContinue
            } else {
                Write-Output ('SLOT=BUSY pid=' + $h.Pid + ' note=' + $h.Note)
                exit 3
            }
        }
        $body = ('' + $PID + '|' + (Get-Date).ToString('o') + '|' + $Note)
        try {
            $fsx = [System.IO.File]::Open($slot, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
            $sw = New-Object System.IO.StreamWriter($fsx)
            $sw.Write($body); $sw.Close(); $fsx.Close()
        } catch {
            $h2 = Get-Holder
            Write-Output ('SLOT=RACE_LOST holder_pid=' + $(if ($h2) { $h2.Pid } else { 'unknown' }))
            exit 3
        }
        Write-Output ('SLOT=ACQUIRED pid=' + $PID + ' note=' + $Note)
        exit 0
    }
    'release' {
        $h = Get-Holder
        if ($h) { Remove-Item $slot -Force -ErrorAction SilentlyContinue; Write-Output 'SLOT=RELEASED' }
        else { Write-Output 'SLOT=ALREADY_FREE' }
        exit 0
    }
    'run' {
        # 为什么用 -GradleArgs 字符串而不是位置参数：PowerShell 会把 `--` 和 `--rerun-tasks` 当成参数名解析（ambiguous），
        # 逐词传参在 5.1 下不可靠。这里收一个整体字符串再自行切分，最稳。
        $raw = $GradleArgs
        if (-not $raw -and $Rest) { $raw = ($Rest -join ' ') }
        if (-not $raw) { Write-Output 'ERR: run 需要 Gradle 任务参数，例：run -Note t51 -GradleArgs '':frontend:test --rerun-tasks'''; exit 2 }
        $cmdArgs = @($raw -split '\s+' | Where-Object { $_ -ne '' })
        $acq = & (Join-Path $PSScriptRoot 'build-slot.ps1') acquire -Note $Note -StaleMinutes $StaleMinutes
        Write-Output $acq
        if ($acq -notmatch 'SLOT=ACQUIRED|SLOT=STALE_STOLEN') {
            Write-Output 'ERR: 槽位被占用，未执行 Gradle。用 status 查看持有者。'
            exit 3
        }
        try {
            Push-Location $root
            & (Join-Path $root 'gradlew.bat') @cmdArgs
            $code = $LASTEXITCODE
            Pop-Location
        } finally {
            & (Join-Path $PSScriptRoot 'build-slot.ps1') release | Out-Null
        }
        exit $code
    }
}
