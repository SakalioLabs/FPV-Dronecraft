param(
    [string]$ModsDirectory = 'F:/Desktop/.minecraft/versions/FPV/mods',
    [string]$PackageDirectory = $PSScriptRoot
)
$ErrorActionPreference = 'Stop'
$modsRoot = [IO.Path]::GetFullPath($ModsDirectory).TrimEnd([IO.Path]::DirectorySeparatorChar)
if (!(Test-Path -LiteralPath $modsRoot -PathType Container)) { throw "Mods directory missing: $modsRoot" }
$versionRoot = [IO.Directory]::GetParent($modsRoot).FullName
$manifest = Get-Content -LiteralPath (Join-Path $PackageDirectory 'install-manifest.json') -Raw | ConvertFrom-Json
foreach ($name in @($manifest.old_filename,$manifest.new_filename)) {
    if ([IO.Path]::GetFileName($name) -ne $name -or !$name.EndsWith('.jar')) { throw 'Unsafe JAR name in manifest' }
}
$newSource = Join-Path $PackageDirectory ('mods/' + $manifest.new_filename)
$oldTarget = [IO.Path]::GetFullPath((Join-Path $modsRoot $manifest.old_filename))
$newTarget = [IO.Path]::GetFullPath((Join-Path $modsRoot $manifest.new_filename))
foreach ($target in @($oldTarget,$newTarget)) {
    if (!$target.StartsWith($modsRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'JAR target escapes mods' }
}
if ((Get-FileHash -LiteralPath $newSource -Algorithm SHA256).Hash -ne $manifest.new_sha256) { throw 'New mod checksum mismatch' }
function Assert-GameExited {
    $active = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" | Where-Object {
        $_.CommandLine -and $_.CommandLine.Replace('/','\').IndexOf($versionRoot,[StringComparison]::OrdinalIgnoreCase) -ge 0
    })
    if ($active.Count -gt 0) { throw 'Minecraft instance is running. Exit this version before updating its mods.' }
}
Assert-GameExited
Add-Type -AssemblyName System.IO.Compression.FileSystem
$droneJars = @(Get-ChildItem -LiteralPath $modsRoot -File -Filter '*.jar' | Where-Object {
    $archive = [IO.Compression.ZipFile]::OpenRead($_.FullName)
    try {
        $entry = $archive.GetEntry('fabric.mod.json')
        if (!$entry) { return $false }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $modInfo = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        return $modInfo.id -eq 'fpvdrone'
    } finally { $archive.Dispose() }
})
if ($droneJars.Count -ne 1) { throw 'Expected exactly one FPV Dronecraft mod. Resolve duplicates before updating.' }
if ((Test-Path -LiteralPath $newTarget) -and $droneJars[0].FullName -eq $newTarget) {
    if ((Get-FileHash -LiteralPath $newTarget -Algorithm SHA256).Hash -eq $manifest.new_sha256) { Write-Output 'The verified monitor version is already installed.'; return }
    throw 'A different JAR already occupies the new version filename.'
}
if ($droneJars[0].FullName -ne $oldTarget -or (Get-FileHash -LiteralPath $oldTarget -Algorithm SHA256).Hash -ne $manifest.old_sha256) { throw 'Existing mod differs from the tested .3 version; installation stopped.' }
if (Test-Path -LiteralPath $newTarget) { throw 'New target already exists' }
$backupParent = [IO.Path]::GetFullPath((Join-Path $versionRoot 'fpvdrone-mod-backups'))
$backupRoot = [IO.Path]::GetFullPath((Join-Path $backupParent ((Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))))
if (!$backupRoot.StartsWith($versionRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or $backupRoot.StartsWith($modsRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid backup directory' }
New-Item -ItemType Directory -Path $backupRoot | Out-Null
$pending = Join-Path $backupRoot ($manifest.new_filename + '.pending')
$backupJar = Join-Path $backupRoot $manifest.old_filename
Copy-Item -LiteralPath $newSource -Destination $pending
if ((Get-FileHash -LiteralPath $pending -Algorithm SHA256).Hash -ne $manifest.new_sha256) { throw 'Staged mod checksum mismatch' }
Assert-GameExited
Move-Item -LiteralPath $oldTarget -Destination $backupJar
try {
    if ((Get-FileHash -LiteralPath $backupJar -Algorithm SHA256).Hash -ne $manifest.old_sha256) { throw 'Backup checksum mismatch' }
    Move-Item -LiteralPath $pending -Destination $newTarget
    if ((Get-FileHash -LiteralPath $newTarget -Algorithm SHA256).Hash -ne $manifest.new_sha256) { throw 'Installed mod checksum mismatch' }
} catch {
    if (Test-Path -LiteralPath $newTarget) { Move-Item -LiteralPath $newTarget -Destination (Join-Path $backupRoot 'failed-install.jar') }
    if (!(Test-Path -LiteralPath $oldTarget)) { Copy-Item -LiteralPath $backupJar -Destination $oldTarget }
    throw
}
$receipt = [ordered]@{
    installed_at=(Get-Date).ToString('o');mods_directory=$modsRoot;backup_directory=$backupRoot
    old_filename=$manifest.old_filename;old_sha256=$manifest.old_sha256
    new_filename=$manifest.new_filename;new_sha256=$manifest.new_sha256
    scope='Mod-only update; options, controller config, Fabric API and saves untouched'
}
$receipt | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backupRoot 'install-receipt.json') -Encoding UTF8
$receipt | ConvertTo-Json
