[CmdletBinding()]
param(
    [string[]]$MinecraftVersions = @(),
    [ValidateSet('build', 'unit', 'server', 'client')]
    [string[]]$Stages = @('build'),
    [switch]$IncludePlanned,
    [switch]$ListOnly,
    [switch]$Quiet,
    [string]$NeoForgeVersionOverride,
    [string]$ClientVerificationTask,
    [string[]]$AdditionalGradleArguments = @()
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$matrixPath = Join-Path $workspaceRoot 'gradle/minecraft-targets.json'
$inventory = Get-Content -LiteralPath $matrixPath -Raw | ConvertFrom-Json
if ($inventory.schemaVersion -ne 1) { throw 'Unsupported Minecraft target inventory schema; expected schemaVersion 1.' }

function Get-RowField($Row, [string[]]$Names) {
    foreach ($name in $Names) {
        $property = $Row.PSObject.Properties[$name]
        if ($null -ne $property -and $null -ne $property.Value) { return $property.Value }
    }
    return $null
}

function Get-BoundedPath([string]$Relative) {
    $absolute = [IO.Path]::GetFullPath((Join-Path $workspaceRoot $Relative))
    $prefix = $workspaceRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $absolute.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Output path escapes the workspace: $absolute"
    }
    return $absolute
}

$allRows = @($inventory.targets)
if ($allRows.Count -eq 0) { throw 'Minecraft target inventory is empty.' }
$knownVersions = @($allRows | ForEach-Object { [string](Get-RowField $_ @('minecraft', 'minecraft_version')) })
if (@($knownVersions | Sort-Object -Unique).Count -ne $knownVersions.Count) { throw 'Duplicate Minecraft target rows.' }
foreach ($requested in $MinecraftVersions) {
    if ($requested -notin $knownVersions) { throw "Unknown Minecraft version: $requested" }
}
$selected = @($allRows | Where-Object {
    $version = [string](Get-RowField $_ @('minecraft', 'minecraft_version'))
    if ($MinecraftVersions.Count -gt 0) { return $version -in $MinecraftVersions }
    return $IncludePlanned -or (Get-RowField $_ @('implemented')) -eq $true
})
if ($selected.Count -eq 0) { throw 'No implemented target selected. Use -ListOnly -IncludePlanned to inspect the inventory.' }
if ($NeoForgeVersionOverride -and $selected.Count -ne 1) { throw 'A NeoForge override requires exactly one selected Minecraft version.' }
$listing = @($selected | ForEach-Object {
    [pscustomobject]@{
        Minecraft = Get-RowField $_ @('minecraft', 'minecraft_version')
        NeoForge = Get-RowField $_ @('neoforge', 'neoforge_version')
        Java = Get-RowField $_ @('java', 'javaVersion', 'javaMajor')
        Adapter = Get-RowField $_ @('adapterFamily')
        Status = Get-RowField $_ @('status')
        Implemented = (Get-RowField $_ @('implemented')) -eq $true
    }
})
$listing | Format-Table -AutoSize | Out-Host
if ($ListOnly) { return }
if ($AdditionalGradleArguments | Where-Object {
    $_ -match '^-P(?:targetMinecraft|targetNeoForge|minecraft_version|neoforge_version)=' -or
    $_ -match '^(?:-p|--project-dir|--build-file|-b|--settings-file|-c)(?:=|$)'
}) { throw 'AdditionalGradleArguments cannot replace the selected target or workspace.' }
if ($ClientVerificationTask -and $ClientVerificationTask -notin @(':neoforge-mod:runClientSmoke', ':neoforge-mod:runClientWorldValidation')) {
    throw 'ClientVerificationTask must be runClientSmoke or runClientWorldValidation; only these tasks validate fresh native client evidence.'
}
if ($ClientVerificationTask -eq ':neoforge-mod:runClientWorldValidation' -and @($selected | Where-Object { (Get-RowField $_ @('minecraft', 'minecraft_version')) -ne '1.21.1' }).Count -gt 0) {
    throw 'runClientWorldValidation currently supports Minecraft 1.21.1 only.'
}

$reportRoot = Get-BoundedPath 'build/reports/neoforge-matrix'
New-Item -ItemType Directory -Path $reportRoot -Force | Out-Null
$lockPath = Join-Path $reportRoot 'runner.lock'
$runnerLock = $null
try {
    $runnerLock = [IO.File]::Open($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
} catch { throw 'Another NeoForge matrix runner is already active.' }
$runStamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
$runDirectory = Get-BoundedPath "build/reports/neoforge-matrix/$runStamp"
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$reportPath = Join-Path $runDirectory 'results.json'
$report = [ordered]@{
    schemaVersion = 1
    startedAt = [DateTime]::UtcNow.ToString('o')
    inventory = 'gradle/minecraft-targets.json'
    requestedStages = @($Stages)
    rows = [Collections.Generic.List[object]]::new()
    passed = $false
}
$hasFailures = $false
$gradleWrapper = Get-BoundedPath 'gradlew.bat'

function Save-MatrixReport {
    $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding utf8
}

try {
    Push-Location -LiteralPath $workspaceRoot
    try {
        foreach ($profile in $selected) {
            $minecraft = [string](Get-RowField $profile @('minecraft', 'minecraft_version'))
            if ($minecraft -notmatch '^(?:1\.21\.[1-9][0-9]*|26\.[1-9][0-9]*(?:\.[1-9][0-9]*)?)$') { throw "Invalid target path key: $minecraft" }
            $pinnedNeo = [string](Get-RowField $profile @('neoforge', 'neoforge_version'))
            $neoVersion = if ($NeoForgeVersionOverride) { $NeoForgeVersionOverride } else { $pinnedNeo }
            $row = [ordered]@{
                minecraft = $minecraft
                neoforge = $neoVersion
                java = Get-RowField $profile @('java', 'javaVersion', 'javaMajor')
                adapterFamily = Get-RowField $profile @('adapterFamily')
                inventoryStatus = Get-RowField $profile @('status')
                verificationStatus = if ($neoVersion -ne $pinnedNeo) { 'unverified_override' } else { Get-RowField $profile @('status') }
                implemented = (Get-RowField $profile @('implemented')) -eq $true
                result = 'pending'
                buildDirectory = "build/targets/$minecraft/neoforge-mod"
                stages = [ordered]@{ build = @{ status = 'not_run' }; unit = @{ status = 'not_run' }; server = @{ status = 'not_run' }; client = @{ status = 'not_run' } }
            }
            $report.rows.Add($row)
            if (-not $row.implemented -or $row.inventoryStatus -eq 'planned') {
                $row.result = 'skipped_unimplemented'
                foreach ($stage in $Stages) { $row.stages[$stage] = @{ status = 'skipped_unimplemented' } }
                $hasFailures = $true
                Save-MatrixReport
                continue
            }
            $targetFailed = $false
            foreach ($stage in @('build', 'unit', 'server', 'client')) {
                if ($stage -notin $Stages) { continue }
                if ($targetFailed) {
                    $row.stages[$stage] = @{ status = 'skipped_after_failure' }
                    continue
                }
                if ($stage -eq 'unit' -and $minecraft -ne '1.21.1') {
                    $row.stages[$stage] = @{ status = 'unavailable'; reason = 'Native mod JUnit fixtures currently target Minecraft 1.21.1 only; shared core tests are run with that profile.' }
                    $hasFailures = $true
                    $targetFailed = $true
                    continue
                }
                $tasks = @(switch ($stage) {
                    'build' { @(':neoforge-mod:assemble', ':neoforge-mod:verifyNeoForgePackaging') }
                    'unit' { @(':drone-sim-core:test', ':computational-acoustics-core:test', ':neoforge-mod:test') }
                    'server' { @(':neoforge-mod:runServerSelfTest', ':neoforge-mod:runPlayableServerSelfTest', ':neoforge-mod:runPlayableHorizonServerSelfTest', ':neoforge-mod:runPlayableAcroServerSelfTest') }
                    'client' { if ($ClientVerificationTask) { @($ClientVerificationTask) } else { @() } }
                })
                if ($tasks.Count -eq 0) {
                    $row.stages[$stage] = @{ status = 'unavailable'; reason = 'A client validation task is required.' }
                    $hasFailures = $true
                    $targetFailed = $true
                    continue
                }
                $logPath = Join-Path $runDirectory "$minecraft-$stage.log"
                $entry = [ordered]@{ status = 'running'; tasks = @($tasks); startedAt = [DateTime]::UtcNow.ToString('o'); log = $logPath }
                $row.stages[$stage] = $entry
                Save-MatrixReport
                $arguments = @('--console=plain', '--no-daemon', '--no-parallel', '--max-workers=1', "-PtargetMinecraft=$minecraft") + @($tasks) + $AdditionalGradleArguments
                if ($NeoForgeVersionOverride) { $arguments += "-PtargetNeoForge=$NeoForgeVersionOverride" }
                Write-Host "[$minecraft] $stage on NeoForge $neoVersion"
                $exitCode = 1
                try {
                    $PSNativeCommandUseErrorActionPreference = $false
                    if ($Quiet) {
                        & $gradleWrapper @arguments 2>&1 | Tee-Object -FilePath $logPath | Out-Null
                    } else {
                        & $gradleWrapper @arguments 2>&1 | Tee-Object -FilePath $logPath | Out-Host
                    }
                    $exitCode = $LASTEXITCODE
                } catch {
                    $_ | Out-String | Add-Content -LiteralPath $logPath
                }
                $entry.finishedAt = [DateTime]::UtcNow.ToString('o')
                $entry.exitCode = $exitCode
                $entry.status = if ($exitCode -eq 0) { 'passed' } else { 'failed' }
                if ($exitCode -ne 0) { $hasFailures = $true; $targetFailed = $true }
                Save-MatrixReport
            }
            $row.result = if ($targetFailed) { 'failed' } else { 'requested_stages_passed' }
            Save-MatrixReport
        }
    } finally { Pop-Location }
    $report.finishedAt = [DateTime]::UtcNow.ToString('o')
    $report.passed = -not $hasFailures
    Save-MatrixReport
    Write-Host "Matrix report: $reportPath"
    if ($hasFailures) { exit 1 }
} finally {
    if ($null -ne $runnerLock) { $runnerLock.Dispose() }
}
