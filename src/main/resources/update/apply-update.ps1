param(
    [Parameter(Mandatory = $true)][int]$ProcessId,
    [Parameter(Mandatory = $true)][string]$InstallDir,
    [Parameter(Mandatory = $true)][string]$StagedDir,
    [Parameter(Mandatory = $true)][string]$LogFile,
    [switch]$NoLaunch
)

$ErrorActionPreference = 'Stop'
$previousDir = "$InstallDir.old"

function Write-Log([string]$Message) {
    Add-Content -LiteralPath $LogFile -Value ("{0:s} {1}" -f (Get-Date), $Message)
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
    if (Test-Path -LiteralPath $previousDir) {
        Remove-Item -LiteralPath $previousDir -Recurse -Force
    }
    $moved = $false
    for ($attempt = 0; $attempt -lt 40 -and -not $moved; $attempt++) {
        try {
            Move-Item -LiteralPath $InstallDir -Destination $previousDir
            $moved = $true
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    if (-not $moved) {
        throw "the current version's files are still in use"
    }
    Move-Item -LiteralPath $StagedDir -Destination $InstallDir
    Write-Log "installed the new version in $InstallDir"
} catch {
    Write-Log "update failed: $_"
    if (-not (Test-Path -LiteralPath $InstallDir) -and (Test-Path -LiteralPath $previousDir)) {
        Move-Item -LiteralPath $previousDir -Destination $InstallDir
        Write-Log "restored the previous version"
    }
}

if (-not $NoLaunch) {
    Start-Process -FilePath (Join-Path $InstallDir 'EVEFarm.exe') -WorkingDirectory $InstallDir
}
