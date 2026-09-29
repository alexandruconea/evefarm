param(
    [Parameter(Mandatory = $true)][int]$ProcessId,
    [Parameter(Mandatory = $true)][string]$InstallDir,
    [Parameter(Mandatory = $true)][string]$StagedDir,
    [Parameter(Mandatory = $true)][string]$LogFile,
    [int]$MoveAttempts = 40,
    [switch]$NoLaunch
)

$ErrorActionPreference = 'Stop'
$previousDir = "$InstallDir.old"
$failureFile = Join-Path (Split-Path -Parent $LogFile) 'update-failed.txt'
Set-Location -LiteralPath $PSScriptRoot
[System.IO.Directory]::SetCurrentDirectory($PSScriptRoot)

function Write-Log([string]$Message) {
    Add-Content -LiteralPath $LogFile -Value ("{0:s} {1}" -f (Get-Date), $Message)
}

function Move-Patiently([string]$From, [string]$To, [int]$Attempts) {
    for ($attempt = 1; $true; $attempt++) {
        try {
            Move-Item -LiteralPath $From -Destination $To
            return
        } catch {
            if ($attempt -ge $Attempts) {
                throw
            }
            Start-Sleep -Milliseconds 500
        }
    }
}

function Move-Children([string]$From, [string]$To, [string[]]$Names) {
    $done = @()
    try {
        foreach ($name in $Names) {
            Move-Item -LiteralPath (Join-Path $From $name) -Destination (Join-Path $To $name)
            $done += $name
        }
    } catch {
        foreach ($name in $done) {
            Move-Item -LiteralPath (Join-Path $To $name) -Destination (Join-Path $From $name) -ErrorAction SilentlyContinue
        }
        throw
    }
}

try {
    Write-Log "waiting for EVE Farm (pid $ProcessId) to exit"
    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($process -and -not $process.WaitForExit(120000)) {
        throw "EVE Farm did not exit"
    }
    $appPrefix = $InstallDir.TrimEnd('\') + '\'
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        $running = Get-Process -ErrorAction SilentlyContinue |
            Where-Object { $_.Path -and $_.Path.StartsWith($appPrefix, [System.StringComparison]::OrdinalIgnoreCase) }
        if (-not $running) { break }
        Start-Sleep -Milliseconds 500
    }
    $newNames = @(Get-ChildItem -LiteralPath $StagedDir -Force | ForEach-Object { $_.Name })
    if (Test-Path -LiteralPath $previousDir) {
        Remove-Item -LiteralPath $previousDir -Recurse -Force
    }
    $moved = $false
    $lastError = ''
    try {
        Move-Patiently $InstallDir $previousDir $MoveAttempts
        $moved = $true
    } catch {
        $lastError = $_.Exception.Message
    }
    if ($moved) {
        Move-Item -LiteralPath $StagedDir -Destination $InstallDir
        foreach ($own in Get-ChildItem -LiteralPath $previousDir -Force) {
            $target = Join-Path $InstallDir $own.Name
            if (Test-Path -LiteralPath $target) {
                continue
            }
            try {
                Move-Patiently $own.FullName $target 10
            } catch {
                Write-Log "couldn't move $($own.Name) back into the app folder: $($_.Exception.Message)"
            }
        }
    } else {
        Write-Log "the app folder is open in another program ($lastError); replacing the files inside it instead"
        New-Item -ItemType Directory -Path $previousDir -Force | Out-Null
        $oldNames = @(Get-ChildItem -LiteralPath $InstallDir -Force |
            Where-Object { $newNames -contains $_.Name } | ForEach-Object { $_.Name })
        try {
            Move-Children $InstallDir $previousDir $oldNames
        } catch {
            throw "the current version's files are still in use ($($_.Exception.Message))"
        }
        try {
            Move-Children $StagedDir $InstallDir $newNames
        } catch {
            $reason = $_.Exception.Message
            Move-Children $previousDir $InstallDir $oldNames
            throw "the new version couldn't be put in place ($reason)"
        }
        Remove-Item -LiteralPath $StagedDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    Write-Log "installed the new version in $InstallDir"
    Remove-Item -LiteralPath $failureFile -ErrorAction SilentlyContinue
} catch {
    $reason = $_.Exception.Message
    Write-Log "update failed: $reason"
    try {
        Set-Content -LiteralPath $failureFile -Value $reason -Encoding UTF8
    } catch {
        Write-Log "could not record the failure for EVE Farm"
    }
    if (-not (Test-Path -LiteralPath $InstallDir) -and (Test-Path -LiteralPath $previousDir)) {
        Move-Item -LiteralPath $previousDir -Destination $InstallDir
        Write-Log "restored the previous version"
    }
}

if (-not $NoLaunch) {
    Start-Process -FilePath (Join-Path $InstallDir 'EVEFarm.exe') -WorkingDirectory $InstallDir
}
