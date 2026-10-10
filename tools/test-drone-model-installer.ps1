$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../build/drone-model-installer-check'))
$repo = [IO.Path]::GetFullPath((Join-Path $root '../..'))
$installer = Join-Path $repo 'tools/install-drone-model.ps1'
$rollback = Join-Path $repo 'tools/rollback-drone-model.ps1'
$package = Join-Path $root 'package'
$modsPackage = Join-Path $package 'mods'
$oldSource = Join-Path $repo 'fabric-mod/build/libs/fpv-dronecraft-fabric-0.1.1-playtest.6.jar'
$sodiumSource = Join-Path $repo 'build/render-optimization-20261010/sodium-fabric-0.8.14+mc1.21.11.jar'
$apiSource = 'F:/Desktop/.minecraft/versions/FPV/mods/fabric-api-0.141.6+1.21.11.jar'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

function Get-Sha256([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function New-MockFabricJar([string]$Path, [string]$Id, [string]$Version, [string]$Marker) {
    if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Force }
    $archive = [IO.Compression.ZipFile]::Open($Path, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $entry = $archive.CreateEntry('fabric.mod.json')
        $writer = [IO.StreamWriter]::new($entry.Open())
        try { $writer.Write((@{schemaVersion=1;id=$Id;version=$Version;name=$Id} | ConvertTo-Json -Compress)) } finally { $writer.Dispose() }
        $data = $archive.CreateEntry('fixture-marker.txt')
        $writer = [IO.StreamWriter]::new($data.Open())
        try { $writer.Write($Marker) } finally { $writer.Dispose() }
    } finally { $archive.Dispose() }
}
function New-Case([string]$Name, [switch]$WrongOld, [switch]$DuplicateDrone, [switch]$WrongApi, [switch]$WrongSodium) {
    $case = Join-Path $root $Name
    if (Test-Path -LiteralPath $case) {
        $resolved = [IO.Path]::GetFullPath($case)
        if (!$resolved.StartsWith($root + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Refusing to clear a fixture outside the test root.' }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
    $mods = Join-Path $case 'versions/FPV/mods'
    New-Item -ItemType Directory -Force -Path $mods,(Join-Path $case 'config'),(Join-Path $case 'saves/world') | Out-Null
    Set-Content -LiteralPath (Join-Path $case 'options.txt') -Value 'synthetic-options'
    Set-Content -LiteralPath (Join-Path $case 'config/fpvdrone-client.json') -Value '{"fixture":"synthetic-config"}'
    Set-Content -LiteralPath (Join-Path $case 'saves/world/marker.txt') -Value 'synthetic-save-marker'
    $oldPath = Join-Path $mods 'fpv-dronecraft-fabric-0.1.1-playtest.6.jar'
    if ($WrongOld) { New-MockFabricJar $oldPath 'fpvdrone' '0.1.1-playtest.6' 'wrong-old-bytes' } else { Copy-Item -LiteralPath $oldSource -Destination $oldPath }
    if ($DuplicateDrone) { Copy-Item -LiteralPath $oldPath -Destination (Join-Path $mods 'extra-fpv-dronecraft.jar') }
    if ($WrongSodium) { New-MockFabricJar (Join-Path $mods 'sodium-fabric-0.8.14+mc1.21.11.jar') 'sodium' 'tampered' 'wrong-sodium' } else { Copy-Item -LiteralPath $sodiumSource -Destination $mods }
    if ($WrongApi) { New-MockFabricJar (Join-Path $mods 'fabric-api-0.141.6+1.21.11.jar') 'fabric-api' 'tampered' 'wrong-api' } else { Copy-Item -LiteralPath $apiSource -Destination $mods }
    return $mods
}
function Invoke-Script([string]$Path, [hashtable]$Arguments) {
    $output = @()
    try { $output = @(& $Path @Arguments 2>&1); return [pscustomobject]@{exit=0;output=($output -join "`n")} }
    catch { $output += $_.Exception.Message; return [pscustomobject]@{exit=1;output=($output -join "`n")} }
}
function Get-Snapshot([string]$Mods) {
    return ((Get-ChildItem -LiteralPath $Mods -File | Sort-Object Name | ForEach-Object { $_.Name + ':' + (Get-Sha256 $_.FullName) }) -join '|')
}
function New-Manifest {
    $newPath = Join-Path $modsPackage 'fpv-dronecraft-fabric-0.1.1-playtest.7.jar'
    New-MockFabricJar $newPath 'fpvdrone' '0.1.1-playtest.7' 'new-drone-model'
    return [ordered]@{
        old_filename='fpv-dronecraft-fabric-0.1.1-playtest.6.jar';old_sha256=(Get-Sha256 $oldSource)
        new_filename='fpv-dronecraft-fabric-0.1.1-playtest.7.jar';new_sha256=(Get-Sha256 $newPath)
        sodium_filename='sodium-fabric-0.8.14+mc1.21.11.jar';sodium_sha256=(Get-Sha256 $sodiumSource)
        api_filename='fabric-api-0.141.6+1.21.11.jar';api_sha256=(Get-Sha256 $apiSource)
    }
}

if ((Get-Sha256 $oldSource) -ne '4852066f615464e04b367dcdc8c4d67234a33775a784912dc538ebd0dbdeffb6') { throw 'The fixture .6 source JAR does not have the expected publisher checksum.' }
if ((Get-Sha256 $sodiumSource) -ne 'fd2619eff5da6b9ba6304b8b72ac3fe132c30e0bd5d675e4581eda417a272f5d') { throw 'Fixture Sodium source checksum mismatch.' }
if ((Get-Sha256 $apiSource) -ne 'bdff7fd7e220085cfad2ff9b1f40dde6534ae0b96cf378f97a374bc54cb9ed0f') { throw 'Fixture Fabric API checksum mismatch.' }
New-Item -ItemType Directory -Force -Path $modsPackage | Out-Null
Copy-Item -LiteralPath $sodiumSource -Destination $modsPackage
$manifest = New-Manifest
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $package 'install-manifest.json') -Encoding UTF8
$results = @()

$mods = New-Case 'case-success'
$install = Invoke-Script $installer @{ModsDirectory=$mods;PackageDirectory=$package}
$versionRoot = Split-Path -Parent $mods
$backupRoot = Join-Path $versionRoot 'fpvdrone-model-backups'
$backup = if (Test-Path -LiteralPath $backupRoot) { Get-ChildItem -LiteralPath $backupRoot -Directory | Select-Object -First 1 } else { $null }
$receiptPath = if ($backup) { Join-Path $backup.FullName 'install-receipt.json' } else { '' }
$newPath = Join-Path $mods $manifest.new_filename
$oldPath = Join-Path $mods $manifest.old_filename
$sodiumPath = Join-Path $mods $manifest.sodium_filename
$apiPath = Join-Path $mods $manifest.api_filename
$installPassed = $install.exit -eq 0 -and (Test-Path -LiteralPath $newPath) -and (Get-Sha256 $newPath) -eq $manifest.new_sha256 -and (Get-Sha256 (Join-Path $backup.FullName $manifest.old_filename)) -eq $manifest.old_sha256 -and (Get-Sha256 $sodiumPath) -eq $manifest.sodium_sha256 -and (Get-Sha256 $apiPath) -eq $manifest.api_sha256
$snapshotAfterInstall = Get-Snapshot $mods
$repeat = Invoke-Script $installer @{ModsDirectory=$mods;PackageDirectory=$package}
$idempotent = $repeat.exit -eq 0 -and (Get-Snapshot $mods) -eq $snapshotAfterInstall -and @(Get-ChildItem -LiteralPath $backupRoot -Directory).Count -eq 1
$rollbackRun = Invoke-Script $rollback @{ModsDirectory=$mods;ReceiptPath=$receiptPath}
$rolledBack = $rollbackRun.exit -eq 0 -and (Test-Path -LiteralPath $oldPath) -and (Get-Sha256 $oldPath) -eq $manifest.old_sha256 -and !(Test-Path -LiteralPath $newPath) -and (Test-Path -LiteralPath $sodiumPath) -and (Get-Sha256 $sodiumPath) -eq $manifest.sodium_sha256 -and (Test-Path -LiteralPath $apiPath) -and (Get-Sha256 $apiPath) -eq $manifest.api_sha256
$results += [pscustomobject]@{case='install_idempotence_rollback_preserves_sodium_api';pass=($installPassed -and $idempotent -and $rolledBack);install_exit=$install.exit;repeat_exit=$repeat.exit;rollback_exit=$rollbackRun.exit;sodium_preserved=$rolledBack;output=($install.output + "`n" + $repeat.output + "`n" + $rollbackRun.output)}

foreach ($caseSpec in @(
    @{name='case-old-hash-mismatch';switch='WrongOld';expected='verified .6'},
    @{name='case-duplicate-drone';switch='DuplicateDrone';expected='exactly one FPV'},
    @{name='case-api-mismatch';switch='WrongApi';expected='Fabric API'},
    @{name='case-sodium-mismatch';switch='WrongSodium';expected='exact verified Sodium'}
)) {
    $params = @{}
    if ($caseSpec.switch) { $params[$caseSpec.switch] = $true }
    $mods = New-Case $caseSpec.name @params
    $before = Get-Snapshot $mods
    $backupPath = Join-Path (Split-Path -Parent $mods) 'fpvdrone-model-backups'
    $attempt = Invoke-Script $installer @{ModsDirectory=$mods;PackageDirectory=$package}
    $unchanged = (Get-Snapshot $mods) -eq $before -and !(Test-Path -LiteralPath $backupPath)
    $results += [pscustomobject]@{case=($caseSpec.name + '_rejected_unchanged');pass=($attempt.exit -ne 0 -and $unchanged);exit=$attempt.exit;mods_unchanged=$unchanged;output=$attempt.output}
}

$report = [ordered]@{schema='mcfpv-drone-model-installer-check-v1';fixture_root=$root;cases=$results;all_pass=(@($results | Where-Object { !$_.pass }).Count -eq 0)}
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $root 'installer-verification.json') -Encoding UTF8
$report | ConvertTo-Json -Depth 6
if (!$report.all_pass) { exit 1 }
