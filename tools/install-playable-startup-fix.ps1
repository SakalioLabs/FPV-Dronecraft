[CmdletBinding()]
param(
    [string] $SourceVersionDirectory = 'F:\Desktop\.minecraft\versions\FPV',
    [string] $RuntimeManifestPath = (Join-Path $PSScriptRoot '..\build\playtest-20261008\fpv-windows-x64-lwjgl336-classpath.json'),
    [string] $PackageDirectory,
    [ValidatePattern('^[A-Za-z0-9_.+-]+$')]
    [string] $ModVersion = '0.1.1-playtest.3',
    [string] $VersionId = 'FPV-Smooth-20261009'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-Sha1([string] $Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA1).Hash.ToLowerInvariant()
}
function Resolve-Full([string] $Path) { return [IO.Path]::GetFullPath($Path).TrimEnd('\') }
function Test-Under([string] $Path, [string] $Root) {
    $p = (Resolve-Full $Path) + '\'
    $r = (Resolve-Full $Root) + '\'
    return $p.StartsWith($r, [StringComparison]::OrdinalIgnoreCase)
}
function ConvertTo-MavenPath([string] $Coordinate) {
    $parts = $Coordinate.Split(':')
    if ($parts.Count -lt 3 -or $parts.Count -gt 4) { throw "无效 Maven 坐标: $Coordinate" }
    $groupPath = $parts[0].Replace('.', '\')
    $artifact = $parts[1]; $version = $parts[2]
    $classifier = if ($parts.Count -eq 4) { '-' + $parts[3] } else { '' }
    return Join-Path (Join-Path (Join-Path $groupPath $artifact) $version) "$artifact-$version$classifier.jar"
}
function Set-ArtifactFields($Lib, $Entry, [string] $Coordinate, [string] $SourceFile) {
    $rel = ConvertTo-MavenPath $Coordinate
    $sha = [string]$Entry.expectedSha1
    $size = (Get-Item -LiteralPath $SourceFile).Length
    $artifact = [ordered]@{ path = $rel.Replace('\','/'); sha1 = $sha; size = $size; url = 'https://repo.maven.apache.org/maven2/' + $rel.Replace('\','/') }
    # This Minecraft version lists native classifier JARs as ordinary artifacts.
    # Keep each full Maven coordinate separate so the native archives remain on the classpath.
    $Lib | Add-Member -Force -NotePropertyName downloads -NotePropertyValue ([pscustomobject]@{ artifact = [pscustomobject]$artifact })
}

if ($VersionId -notmatch '^[A-Za-z0-9_-]+$') { throw 'VersionId 仅允许字母、数字、下划线和连字符。' }
$source = Resolve-Full $SourceVersionDirectory
$manifestFile = Resolve-Full $RuntimeManifestPath
if (!(Test-Path -LiteralPath $source -PathType Container)) { throw "找不到源版本目录: $source" }
if (!(Test-Path -LiteralPath $manifestFile -PathType Leaf)) { throw "找不到运行时 manifest: $manifestFile" }
$versionsRoot = Split-Path -Parent $source
$minecraftRoot = Split-Path -Parent $versionsRoot
if ((Split-Path -Leaf $versionsRoot) -ne 'versions') { throw '源版本目录必须位于 .minecraft/versions 下。' }
$target = Join-Path $versionsRoot $VersionId
$targetJsonPath = Join-Path $target "$VersionId.json"
if (Test-Path -LiteralPath $target) { throw "目标版本目录已存在，拒绝覆盖: $target" }
$originalId = Split-Path -Leaf $source
$originalJsonPath = Join-Path $source "$originalId.json"
if (!(Test-Path -LiteralPath $originalJsonPath -PathType Leaf)) { throw "找不到源版本 JSON: $originalJsonPath" }

# Read only launcher/runtime metadata and software packages; never enumerate saves or user data.
$manifest = Get-Content -LiteralPath $manifestFile -Raw | ConvertFrom-Json
$original = Get-Content -LiteralPath $originalJsonPath -Raw | ConvertFrom-Json
if ($manifest.platform -ne 'windows-x86_64') { throw 'Manifest 必须是 windows-x86_64。' }
$clientEntry = @($manifest.classpathEntries | Where-Object kind -eq 'minecraftClient') | Select-Object -First 1
if (!$clientEntry) { throw 'Manifest 缺少 minecraftClient。' }
$clientSource = Join-Path $source "$originalId.jar"
if (!(Test-Path -LiteralPath $clientSource -PathType Leaf)) { throw '找不到原 Minecraft client JAR。' }
if ($clientEntry.expectedSha1 -and (Get-Sha1 $clientSource) -ne ([string]$clientEntry.expectedSha1).ToLowerInvariant()) { throw 'Minecraft client JAR SHA-1 与 manifest 不符。' }

$lwjglEntries = @($manifest.classpathEntries | Where-Object { $_.coordinate -like 'org.lwjgl:*' })
if ($lwjglEntries.Count -ne 16) { throw "预期 manifest 含 16 个 LWJGL 文件，实际为 $($lwjglEntries.Count)。" }
$entries = @($manifest.classpathEntries | Where-Object kind -ne 'minecraftClient')
$libraries = @(); $copyPlan = [System.Collections.Generic.List[object]]::new()
$origLibs = @($original.libraries)
foreach ($entry in $entries) {
    $coordinate = [string]$entry.coordinate
    if ($coordinate -notmatch '^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+-]+(:[A-Za-z0-9_.-]+)?$') { throw '无效的依赖坐标。' }
    $isLwjgl = $coordinate -like 'org.lwjgl:*'
    $sourcePath = if ($isLwjgl) { [string]$entry.path } else { Join-Path (Join-Path $minecraftRoot 'libraries') (ConvertTo-MavenPath $coordinate) }
    if ($PackageDirectory) {
        $packageRoot = Resolve-Full $PackageDirectory
        if ($isLwjgl) { $candidate = Join-Path (Join-Path $packageRoot 'libraries') (ConvertTo-MavenPath $coordinate) }
        else { $candidate = Join-Path $packageRoot (Join-Path 'libraries' (ConvertTo-MavenPath $coordinate)) }
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { $sourcePath = $candidate }
        elseif ($isLwjgl) { throw "PackageDirectory 缺少依赖: $candidate" }
    }
    $isLwjglPackage = $false
    if ($PackageDirectory) { $isLwjglPackage = Test-Under $sourcePath (Join-Path (Resolve-Full $PackageDirectory) 'libraries') }
    if ($isLwjgl -and !$isLwjglPackage) {
        $minecraftLibraries = Join-Path $minecraftRoot 'libraries'
        if (!(Test-Under $sourcePath $minecraftLibraries)) { throw "LWJGL 来源必须位于 .minecraft/libraries 或 PackageDirectory/libraries: $sourcePath" }
    }
    if (!(Test-Path -LiteralPath $sourcePath -PathType Leaf)) { throw "缺少依赖文件: $sourcePath" }
    $actual = Get-Sha1 $sourcePath
    $manifestHash = if ($entry.expectedSha1) { [string]$entry.expectedSha1 } else { [string]$entry.actualSha1 }
    if (!$manifestHash -or $actual -ne $manifestHash.ToLowerInvariant()) { throw "依赖 SHA-1 与 manifest 不符: $coordinate" }

    if ($isLwjgl) {
        $baseParts = $coordinate.Split(':'); $baseParts[2] = '3.3.3'; $oldCoordinate = $baseParts -join ':'
        $lib = $origLibs | Where-Object { $_.name -eq $oldCoordinate } | Select-Object -First 1
        if (!$lib) { throw "原版本 JSON 中找不到 LWJGL 对应项: $oldCoordinate" }
        $newLib = $lib | ConvertTo-Json -Depth 100 | ConvertFrom-Json
        $newLib.name = $coordinate
        Set-ArtifactFields $newLib $entry $coordinate $sourcePath
        $libraries += $newLib
        $rel = ConvertTo-MavenPath $coordinate
        $destFile = Join-Path (Join-Path $minecraftRoot 'libraries') $rel
        if (Test-Path -LiteralPath $destFile -PathType Leaf) {
            if ((Get-Sha1 $destFile) -ne $actual) { throw "目标 LWJGL 已存在但 SHA-1 冲突，拒绝覆盖: $destFile" }
        }
        $copyPlan.Add([pscustomobject]@{ Source=$sourcePath; Destination=$destFile; Sha1=$actual; Coordinate=$coordinate; Library=$true })
    } else {
        $lib = $origLibs | Where-Object { $_.name -eq $coordinate } | Select-Object -First 1
        if (!$lib) { throw "原版本 JSON 中找不到 manifest 依赖: $coordinate" }
        $libraries += $lib
    }
}

$modsRoot = if ($PackageDirectory) { Join-Path (Resolve-Full $PackageDirectory) 'mods' } else { Join-Path $source 'mods' }
$modCandidates = @(
    @{ Name="fpvdrone-$ModVersion.jar"; Pattern="*fpv*$ModVersion.jar" },
    @{ Name='fabric-api-0.141.6.jar'; Pattern='fabric-api-0.141.6+1.21.11.jar' }
)
$modPlan = [System.Collections.Generic.List[object]]::new()
foreach ($spec in $modCandidates) {
    $found = Get-ChildItem -LiteralPath $modsRoot -Filter $spec.Pattern -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if (!$found) { throw "找不到要求的 mod: $($spec.Pattern) (目录 $modsRoot)" }
    $modPlan.Add([pscustomobject]@{ Source=$found.FullName; Name=$spec.Name; Sha1=(Get-Sha1 $found.FullName) })
}

# Sanitize the inherited launch metadata: preserve standard placeholders, remove stored auth values.
$version = $original | ConvertTo-Json -Depth 100 | ConvertFrom-Json
$version.id = $VersionId
$version | Add-Member -Force -NotePropertyName jar -NotePropertyValue $VersionId
$version.libraries = $libraries
foreach ($property in @('clientToken','clientId','xuid','userType','username','uuid','accessToken')) {
    if ($version.PSObject.Properties.Match($property).Count -gt 0) { $version.PSObject.Properties.Remove($property) }
}
$authPlaceholders = @{
    '--username'='${auth_player_name}'; '--uuid'='${auth_uuid}'; '--accessToken'='${auth_access_token}'
    '--clientId'='${clientid}'; '--xuid'='${auth_xuid}'
}
for ($argumentIndex = 0; $argumentIndex -lt $version.arguments.game.Count - 1; $argumentIndex++) {
    $argument = $version.arguments.game[$argumentIndex]
    if ($argument -is [string] -and $authPlaceholders.ContainsKey($argument)) {
        $version.arguments.game[$argumentIndex + 1] = $authPlaceholders[$argument]
    }
}
$jsonText = $version | ConvertTo-Json -Depth 100
$utf8NoBom = [Text.UTF8Encoding]::new($false)

# Only the new version receives PCL isolation metadata; global launcher settings are not written.
$pclSetup = Join-Path $target 'PCL\Setup.ini'
foreach ($item in $copyPlan) {
    if (!(Test-Under $item.Destination (Join-Path $minecraftRoot 'libraries'))) { throw '目标依赖路径越界。' }
}

# All validation is complete. Writes are confined to the new sibling version and its required libraries/PCL setting.
New-Item -ItemType Directory -Path $target | Out-Null
try {
    $journal = [System.Collections.Generic.List[object]]::new()
    foreach ($item in $copyPlan) {
        if (!(Test-Path -LiteralPath $item.Destination -PathType Leaf)) {
            New-Item -ItemType Directory -Path (Split-Path -Parent $item.Destination) -Force | Out-Null
            Copy-Item -LiteralPath $item.Source -Destination $item.Destination
        }
        $journal.Add([pscustomobject]@{ path=$item.Destination; sha1=$item.Sha1 })
    }
    Copy-Item -LiteralPath $clientSource -Destination (Join-Path $target "$VersionId.jar")
    $journal.Add([pscustomobject]@{ path=(Join-Path $target "$VersionId.jar"); sha1=(Get-Sha1 (Join-Path $target "$VersionId.jar")) })
    New-Item -ItemType Directory -Path (Join-Path $target 'mods') | Out-Null
    foreach ($mod in $modPlan) {
        $destMod = Join-Path (Join-Path $target 'mods') $mod.Name
        Copy-Item -LiteralPath $mod.Source -Destination $destMod
        $journal.Add([pscustomobject]@{ path=$destMod; sha1=(Get-Sha1 $destMod) })
    }
    # Preserve the existing controller mapping in the new isolated instance.
    $controllerConfig = Join-Path $source 'config\fpvdrone-client.json'
    if (Test-Path -LiteralPath $controllerConfig -PathType Leaf) {
        $targetConfig = Join-Path $target 'config\fpvdrone-client.json'
        New-Item -ItemType Directory -Path (Split-Path -Parent $targetConfig) | Out-Null
        Copy-Item -LiteralPath $controllerConfig -Destination $targetConfig
        $journal.Add([pscustomobject]@{ path=$targetConfig; sha1=(Get-Sha1 $targetConfig) })
    }
    [IO.File]::WriteAllText($targetJsonPath, $jsonText, $utf8NoBom)
    $sourceOptions = Join-Path $source 'options.txt'
    if (Test-Path -LiteralPath $sourceOptions -PathType Leaf) {
        Copy-Item -LiteralPath $sourceOptions -Destination (Join-Path $target 'options.txt')
    } else {
        [IO.File]::WriteAllText((Join-Path $target 'options.txt'), "lang:zh_cn`n", $utf8NoBom)
    }
    $journal.Add([pscustomobject]@{ path=$targetJsonPath; sha1=(Get-Sha1 $targetJsonPath) })
    $journal.Add([pscustomobject]@{ path=(Join-Path $target 'options.txt'); sha1=(Get-Sha1 (Join-Path $target 'options.txt')) })
    [IO.File]::WriteAllText((Join-Path $target 'startup-fix-installation.json'), (@{ versionId=$VersionId; copies=@($journal) } | ConvertTo-Json -Depth 10), $utf8NoBom)
    New-Item -ItemType Directory -Path (Split-Path -Parent $pclSetup) | Out-Null
    [IO.File]::WriteAllText($pclSetup, "VersionArgumentIndieV2:True`r`n", $utf8NoBom)
} catch {
    # Deliberately leave the new directory for inspection; never delete user data recursively.
    throw
}
Write-Output "已创建可玩启动修复版本: $target"
