param(
    [string]$ModsDirectory = 'F:/Desktop/.minecraft/versions/FPV/mods',
    [Parameter(Mandatory=$true)][string]$ReceiptPath
)
$ErrorActionPreference = 'Stop'

function Get-Sha256([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Get-FabricModId([string]$JarPath) {
    $archive = [IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entry = $archive.GetEntry('fabric.mod.json')
        if (!$entry) { return $null }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { return (($reader.ReadToEnd() | ConvertFrom-Json).id) } finally { $reader.Dispose() }
    } finally { $archive.Dispose() }
}
function Assert-GameExited([string]$VersionRoot) {
    $active = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" | Where-Object {
        $_.CommandLine -and $_.CommandLine.Replace('/','\').IndexOf($VersionRoot,[StringComparison]::OrdinalIgnoreCase) -ge 0
    })
    if ($active.Count -gt 0) { throw 'Minecraft instance is running. Exit this version before restoring its mods.' }
}

$modsRoot = [IO.Path]::GetFullPath($ModsDirectory).TrimEnd([IO.Path]::DirectorySeparatorChar)
$receiptFullPath = [IO.Path]::GetFullPath($ReceiptPath)
if (!(Test-Path -LiteralPath $modsRoot -PathType Container)) { throw "Mods directory missing: $modsRoot" }
if (!(Test-Path -LiteralPath $receiptFullPath -PathType Leaf)) { throw "Install receipt missing: $receiptFullPath" }
$receipt = Get-Content -LiteralPath $receiptFullPath -Raw | ConvertFrom-Json
if ($receipt.schema -ne 'mcfpv-render-optimization-install-v1') { throw 'Unsupported render optimization receipt.' }
if (!$receipt.PSObject.Properties['sodium_added'] -or $receipt.sodium_added -isnot [bool]) { throw 'Receipt must contain a Boolean sodium_added field.' }
if ([IO.Path]::GetFullPath([string]$receipt.mods_directory) -ne $modsRoot) { throw 'Receipt belongs to a different Mods directory.' }
$versionRoot = [IO.Directory]::GetParent($modsRoot).FullName
$backupRoot = [IO.Path]::GetFullPath([string]$receipt.backup_directory)
$backupParent = [IO.Path]::GetFullPath((Join-Path $versionRoot 'fpvdrone-render-optimization-backups'))
if (!$backupRoot.StartsWith($backupParent + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Receipt backup path is outside the expected backup directory.' }
if ([IO.Path]::GetFullPath((Split-Path -Parent $receiptFullPath)) -ne $backupRoot) { throw 'Receipt is not located in its recorded backup directory.' }
foreach ($field in @('old_filename','new_filename','sodium_filename','api_filename')) {
    $name = [string]$receipt.$field
    if ([IO.Path]::GetFileName($name) -ne $name -or !$name.EndsWith('.jar',[StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe filename in receipt: $field" }
}
foreach ($field in @('old_sha256','new_sha256','sodium_sha256','api_sha256')) {
    if ([string]$receipt.$field -notmatch '^[0-9a-fA-F]{64}$') { throw "Invalid checksum in receipt: $field" }
}
if ($receipt.old_filename -ne 'fpv-dronecraft-fabric-0.1.1-playtest.4.jar' -or $receipt.new_filename -ne 'fpv-dronecraft-fabric-0.1.1-playtest.5.jar') { throw 'Receipt does not describe the expected .4/.5 versions.' }
if ($receipt.sodium_filename -ne 'sodium-fabric-0.8.14+mc1.21.11.jar' -or $receipt.api_filename -ne 'fabric-api-0.141.6+1.21.11.jar') { throw 'Receipt artifact names differ from the verified bundle.' }

Assert-GameExited $versionRoot
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jars = @(Get-ChildItem -LiteralPath $modsRoot -File -Filter '*.jar')
$droneJars = @($jars | Where-Object { (Get-FabricModId $_.FullName) -eq 'fpvdrone' })
if ($droneJars.Count -ne 1 -or $droneJars[0].Name -ne $receipt.new_filename -or (Get-Sha256 $droneJars[0].FullName) -ne $receipt.new_sha256.ToLowerInvariant()) { throw 'Installed .5 mod is missing, duplicated, or modified; rollback stopped.' }
$apiTarget = Join-Path $modsRoot $receipt.api_filename
$apiJars = @($jars | Where-Object { $_.Name -eq $receipt.api_filename -or (Get-FabricModId $_.FullName) -eq 'fabric-api' })
if ($apiJars.Count -ne 1 -or (Get-Sha256 $apiTarget) -ne $receipt.api_sha256.ToLowerInvariant()) { throw 'Fabric API is missing, duplicated, or changed since installation.' }
$sodiumJars = @($jars | Where-Object { $_.Name -like 'sodium*.jar' -or (Get-FabricModId $_.FullName) -eq 'sodium' })
if ($sodiumJars.Count -gt 1) { throw 'Multiple Sodium JARs are present; rollback stopped.' }
$sodiumTarget = Join-Path $modsRoot $receipt.sodium_filename
if ($receipt.sodium_added) {
    if ($sodiumJars.Count -ne 1 -or $sodiumJars[0].Name -ne $receipt.sodium_filename -or (Get-Sha256 $sodiumTarget) -ne $receipt.sodium_sha256.ToLowerInvariant()) { throw 'Added Sodium is missing or modified; rollback stopped.' }
} elseif ($sodiumJars.Count -gt 0 -and ($sodiumJars[0].Name -ne $receipt.sodium_filename -or (Get-Sha256 $sodiumTarget) -ne $receipt.sodium_sha256.ToLowerInvariant())) {
    throw 'Existing Sodium differs from the verified bundle; rollback stopped.'
}
$oldBackup = [IO.Path]::GetFullPath((Join-Path $backupRoot $receipt.old_filename))
if (!$oldBackup.StartsWith($backupRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or !(Test-Path -LiteralPath $oldBackup -PathType Leaf) -or (Get-Sha256 $oldBackup) -ne $receipt.old_sha256.ToLowerInvariant()) { throw 'Backed up .4 mod is missing or modified.' }
$oldTarget = Join-Path $modsRoot $receipt.old_filename
if (Test-Path -LiteralPath $oldTarget) { throw 'The .4 target already exists; rollback will not overwrite it.' }
$newTarget = Join-Path $modsRoot $receipt.new_filename
$removedNew = Join-Path $backupRoot ('rolled-back-' + $receipt.new_filename)
$removedSodium = Join-Path $backupRoot ('rolled-back-' + $receipt.sodium_filename)
if ((Test-Path -LiteralPath $removedNew) -or (Test-Path -LiteralPath $oldTarget)) { throw 'Rollback destination already exists.' }
if ($receipt.sodium_added -and (Test-Path -LiteralPath $removedSodium)) { throw 'Rollback Sodium destination already exists.' }

Assert-GameExited $versionRoot
$movedSodium = $false
$movedNew = $false
$oldRestoreAttempted = $false
$rollbackReceiptPath = Join-Path $backupRoot 'rollback-receipt.json'
try {
    if ($receipt.sodium_added) { $movedSodium = $true; Move-Item -LiteralPath $sodiumTarget -Destination $removedSodium }
    $movedNew = $true
    Move-Item -LiteralPath $newTarget -Destination $removedNew
    $oldRestoreAttempted = $true
    Copy-Item -LiteralPath $oldBackup -Destination $oldTarget
    if ((Get-Sha256 $oldTarget) -ne $receipt.old_sha256.ToLowerInvariant()) { throw 'Restored .4 mod checksum mismatch.' }
    if ((Get-Sha256 $apiTarget) -ne $receipt.api_sha256.ToLowerInvariant()) { throw 'Fabric API changed during rollback.' }
    $rollbackReceipt = [ordered]@{
        schema='mcfpv-render-optimization-rollback-v1';rolled_back_at=(Get-Date).ToString('o')
        mods_directory=$modsRoot;source_install_receipt=$receiptFullPath
        restored_filename=$receipt.old_filename;restored_sha256=$receipt.old_sha256.ToLowerInvariant()
        removed_filename=$receipt.new_filename;removed_sha256=$receipt.new_sha256.ToLowerInvariant();removed_artifact=$removedNew
        sodium_removed=[bool]$receipt.sodium_added
        sodium_filename=$receipt.sodium_filename;sodium_sha256=$receipt.sodium_sha256.ToLowerInvariant()
        api_filename=$receipt.api_filename;api_sha256=$receipt.api_sha256.ToLowerInvariant()
    }
    $rollbackReceipt | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $rollbackReceiptPath -Encoding UTF8
    $rollbackReceipt | ConvertTo-Json -Depth 5
} catch {
    if ($oldRestoreAttempted -and (Test-Path -LiteralPath $oldTarget)) { Remove-Item -LiteralPath $oldTarget -Force }
    if ($movedNew -and (Test-Path -LiteralPath $removedNew)) { Move-Item -LiteralPath $removedNew -Destination $newTarget }
    if ($movedSodium -and (Test-Path -LiteralPath $removedSodium)) { Move-Item -LiteralPath $removedSodium -Destination $sodiumTarget }
    if (Test-Path -LiteralPath $rollbackReceiptPath) { Remove-Item -LiteralPath $rollbackReceiptPath -Force }
    throw
}
