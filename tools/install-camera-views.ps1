param(
    [string]$ModsDirectory = 'F:/Desktop/.minecraft/versions/FPV/mods',
    [string]$PackageDirectory = $PSScriptRoot
)
$ErrorActionPreference = 'Stop'

function Get-Sha256([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Assert-Hash([string]$Value, [string]$Label) {
    if ($Value -notmatch '^[0-9a-fA-F]{64}$') { throw "Invalid SHA-256 in manifest: $Label" }
}
function Assert-GameExited([string]$VersionRoot) {
    $active = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" | Where-Object {
        $_.CommandLine -and $_.CommandLine.Replace('/','\').IndexOf($VersionRoot,[StringComparison]::OrdinalIgnoreCase) -ge 0
    })
    if ($active.Count -gt 0) { throw 'Minecraft instance is running. Exit this version before updating its mods.' }
}
function Get-FabricModId([string]$JarPath) {
    $archive = [IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entry = $archive.GetEntry('fabric.mod.json')
        if (!$entry) { return $null }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { return (($reader.ReadToEnd() | ConvertFrom-Json).id) } finally { $reader.Dispose() }
    } finally { $archive.Dispose() }
}

$modsRoot = [IO.Path]::GetFullPath($ModsDirectory).TrimEnd([IO.Path]::DirectorySeparatorChar)
$packageRoot = [IO.Path]::GetFullPath($PackageDirectory)
if (!(Test-Path -LiteralPath $modsRoot -PathType Container)) { throw "Mods directory missing: $modsRoot" }
$versionRoot = [IO.Directory]::GetParent($modsRoot).FullName
$manifestPath = Join-Path $packageRoot 'install-manifest.json'
if (!(Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Package is missing install-manifest.json' }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$required = @('old_filename','old_sha256','new_filename','new_sha256','sodium_filename','sodium_sha256','api_filename','api_sha256')
foreach ($field in $required) { if (!$manifest.PSObject.Properties[$field]) { throw "Manifest is missing $field" } }
foreach ($field in @('old_filename','new_filename','sodium_filename','api_filename')) {
    $name = [string]$manifest.$field
    if ([IO.Path]::GetFileName($name) -ne $name -or !$name.EndsWith('.jar',[StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe JAR name in manifest: $field" }
}
foreach ($field in @('old_sha256','new_sha256','sodium_sha256','api_sha256')) { Assert-Hash ([string]$manifest.$field) $field }
if ($manifest.old_filename -ne 'fpv-dronecraft-fabric-0.1.1-playtest.7.jar' -or $manifest.new_filename -ne 'fpv-dronecraft-fabric-0.1.1-playtest.8.jar') { throw 'Manifest must describe the exact .7 to .8 update.' }
if ($manifest.old_sha256.ToLowerInvariant() -ne '7c86f6ace4fe676eb86b6f26e81f40351992368192c3e28172f453d0c4e90a52') { throw 'Manifest does not contain the verified .7 source checksum.' }
if ($manifest.sodium_filename -ne 'sodium-fabric-0.8.14+mc1.21.11.jar' -or $manifest.sodium_sha256.ToLowerInvariant() -ne 'fd2619eff5da6b9ba6304b8b72ac3fe132c30e0bd5d675e4581eda417a272f5d' -or $manifest.api_filename -ne 'fabric-api-0.141.6+1.21.11.jar' -or $manifest.api_sha256.ToLowerInvariant() -ne 'bdff7fd7e220085cfad2ff9b1f40dde6534ae0b96cf378f97a374bc54cb9ed0f') { throw 'Manifest contains an unexpected Sodium or Fabric API artifact.' }

$oldTarget = Join-Path $modsRoot $manifest.old_filename
$newTarget = Join-Path $modsRoot $manifest.new_filename
$sodiumTarget = Join-Path $modsRoot $manifest.sodium_filename
$apiTarget = Join-Path $modsRoot $manifest.api_filename
$newSource = Join-Path (Join-Path $packageRoot 'mods') $manifest.new_filename
$sodiumSource = Join-Path (Join-Path $packageRoot 'mods') $manifest.sodium_filename
foreach ($source in @($newSource,$sodiumSource)) { if (!(Test-Path -LiteralPath $source -PathType Leaf)) { throw "Package artifact missing: $source" } }
if ((Get-Sha256 $newSource) -ne $manifest.new_sha256.ToLowerInvariant()) { throw 'New .8 mod checksum mismatch.' }
if ((Get-Sha256 $sodiumSource) -ne $manifest.sodium_sha256.ToLowerInvariant()) { throw 'Sodium package checksum mismatch.' }

Assert-GameExited $versionRoot
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jars = @(Get-ChildItem -LiteralPath $modsRoot -File -Filter '*.jar')
$droneJars = @($jars | Where-Object { (Get-FabricModId $_.FullName) -eq 'fpvdrone' })
if ($droneJars.Count -ne 1) { throw 'Expected exactly one FPV Dronecraft mod. Resolve duplicates before updating.' }
$sodiumJars = @($jars | Where-Object { (Get-FabricModId $_.FullName) -eq 'sodium' -or $_.Name -like 'sodium*.jar' })
if ($sodiumJars.Count -ne 1 -or $sodiumJars[0].Name -ne $manifest.sodium_filename -or (Get-Sha256 $sodiumJars[0].FullName) -ne $manifest.sodium_sha256.ToLowerInvariant()) { throw 'Expected the exact verified Sodium JAR already installed; installation stopped.' }
$apiJars = @($jars | Where-Object { $_.Name -eq $manifest.api_filename -or (Get-FabricModId $_.FullName) -eq 'fabric-api' })
if ($apiJars.Count -ne 1 -or (Get-Sha256 $apiTarget) -ne $manifest.api_sha256.ToLowerInvariant()) { throw 'Fabric API filename or checksum does not match the package manifest.' }

$alreadyInstalled = $droneJars[0].Name -eq $manifest.new_filename -and (Get-Sha256 $droneJars[0].FullName) -eq $manifest.new_sha256.ToLowerInvariant()
if ($alreadyInstalled) { Write-Output 'The verified .8 mod is already installed; Sodium and Fabric API checks passed.'; return }
if ($droneJars[0].Name -ne $manifest.old_filename -or (Get-Sha256 $droneJars[0].FullName) -ne $manifest.old_sha256.ToLowerInvariant()) { throw 'Existing FPV Dronecraft mod differs from the verified .7 artifact.' }
if (Test-Path -LiteralPath $newTarget) { throw 'The .8 target filename is already occupied.' }

$backupParent = [IO.Path]::GetFullPath((Join-Path $versionRoot 'fpvdrone-camera-view-backups'))
$backupRoot = [IO.Path]::GetFullPath((Join-Path $backupParent ((Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))))
$stageRoot = [IO.Path]::GetFullPath((Join-Path $versionRoot ('.fpvdrone-camera-view-stage-' + [Guid]::NewGuid().ToString('N'))))
foreach ($path in @($backupRoot,$stageRoot)) {
    if (!$path.StartsWith($versionRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or $path.StartsWith($modsRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe backup or staging path.' }
}
if (Test-Path -LiteralPath $stageRoot) { throw 'Camera views staging directory already exists; inspect it before retrying.' }
New-Item -ItemType Directory -Path $backupRoot | Out-Null
New-Item -ItemType Directory -Path $stageRoot | Out-Null
$stagedNew = Join-Path $stageRoot $manifest.new_filename
$backupOld = Join-Path $backupRoot $manifest.old_filename
$oldMoved = $false
$newInstallAttempted = $false
try {
    Copy-Item -LiteralPath $newSource -Destination $stagedNew
    if ((Get-Sha256 $stagedNew) -ne $manifest.new_sha256.ToLowerInvariant()) { throw 'Staged .8 artifact checksum mismatch.' }
    Assert-GameExited $versionRoot
    Move-Item -LiteralPath $oldTarget -Destination $backupOld
    $oldMoved = $true
    if ((Get-Sha256 $backupOld) -ne $manifest.old_sha256.ToLowerInvariant()) { throw 'Backed up .7 mod checksum mismatch.' }
    $newInstallAttempted = $true
    Move-Item -LiteralPath $stagedNew -Destination $newTarget
    if ((Get-Sha256 $newTarget) -ne $manifest.new_sha256.ToLowerInvariant()) { throw 'Installed .8 mod checksum mismatch.' }
    if ((Get-Sha256 $sodiumTarget) -ne $manifest.sodium_sha256.ToLowerInvariant() -or (Get-Sha256 $apiTarget) -ne $manifest.api_sha256.ToLowerInvariant()) { throw 'Sodium or Fabric API changed during installation.' }
    $receipt = [ordered]@{
        schema='mcfpv-camera-view-install-v1';installed_at=(Get-Date).ToString('o')
        mods_directory=$modsRoot;backup_directory=$backupRoot
        old_filename=$manifest.old_filename;old_sha256=$manifest.old_sha256.ToLowerInvariant()
        new_filename=$manifest.new_filename;new_sha256=$manifest.new_sha256.ToLowerInvariant()
        sodium_filename=$manifest.sodium_filename;sodium_sha256=$manifest.sodium_sha256.ToLowerInvariant();sodium_added=$false
        api_filename=$manifest.api_filename;api_sha256=$manifest.api_sha256.ToLowerInvariant()
        artifacts=@(
            [ordered]@{filename=$manifest.new_filename;sha256=$manifest.new_sha256.ToLowerInvariant();source=$newSource}
            [ordered]@{filename=$manifest.sodium_filename;sha256=$manifest.sodium_sha256.ToLowerInvariant();verified_path=$sodiumTarget}
            [ordered]@{filename=$manifest.old_filename;sha256=$manifest.old_sha256.ToLowerInvariant();backup=$backupOld}
            [ordered]@{filename=$manifest.api_filename;sha256=$manifest.api_sha256.ToLowerInvariant();verified_path=$apiTarget}
        )
    }
    $receipt | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $backupRoot 'install-receipt.json') -Encoding UTF8
    $receipt | ConvertTo-Json -Depth 6
} catch {
    if ($newInstallAttempted -and (Test-Path -LiteralPath $newTarget) -and (Get-Sha256 $newTarget) -eq $manifest.new_sha256.ToLowerInvariant()) { Remove-Item -LiteralPath $newTarget -Force }
    if ($oldMoved -and !(Test-Path -LiteralPath $oldTarget) -and (Test-Path -LiteralPath $backupOld) -and (Get-Sha256 $backupOld) -eq $manifest.old_sha256.ToLowerInvariant()) { Copy-Item -LiteralPath $backupOld -Destination $oldTarget }
    $receiptFile = Join-Path $backupRoot 'install-receipt.json'
    if (Test-Path -LiteralPath $receiptFile) { Remove-Item -LiteralPath $receiptFile -Force }
    if (!$oldMoved -and (Test-Path -LiteralPath $backupRoot)) { Remove-Item -LiteralPath $backupRoot -Recurse -Force }
    throw
} finally {
    if (Test-Path -LiteralPath $stageRoot) { Remove-Item -LiteralPath $stageRoot -Recurse -Force }
}
