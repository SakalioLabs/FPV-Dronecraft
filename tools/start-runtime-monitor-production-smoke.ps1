param(
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$RunId = 'production-monitor-20261010',
    [string]$ClasspathManifest,
    [ValidatePattern('^[A-Za-z0-9_.+-]+$')][string]$ModVersion = '0.1.1-playtest.11',
    [string[]]$ExtraModJars = @(),
    [string[]]$DiagnosticProperties = @(),
    [switch]$WaitForExit
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$diagnosticRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'build/performance'))
$runRoot = [IO.Path]::GetFullPath((Join-Path $diagnosticRoot $RunId))
if (!$runRoot.StartsWith($diagnosticRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Diagnostic directory escapes build/performance' }
if (Test-Path -LiteralPath $runRoot) { throw "Run directory exists: $runRoot" }
if (!$ClasspathManifest) { $ClasspathManifest = Join-Path $projectRoot 'build/playtest-20261008/fpv-windows-x64-classpath.json' }
$manifest = Get-Content -LiteralPath $ClasspathManifest -Raw | ConvertFrom-Json
if ($manifest.classpathEntries.Count -lt 50) { throw 'Incomplete production classpath' }
$modJar = Join-Path $projectRoot "fabric-mod/build/libs/fpv-dronecraft-fabric-$ModVersion.jar"
$helperJar = Join-Path $projectRoot 'fabric-mod/build/performance-smoke/fpvdrone-performance-smoke.jar'
$apiJar = 'F:/Desktop/.minecraft/versions/FPV/mods/fabric-api-0.141.6+1.21.11.jar'
foreach ($artifact in @($modJar,$helperJar,$apiJar) + $ExtraModJars) { if (!(Test-Path -LiteralPath $artifact -PathType Leaf)) { throw "Missing artifact: $artifact" } }
foreach ($property in $DiagnosticProperties) {
    if ($property -notmatch '^-Dfpvdrone\.performance\.[A-Za-z][A-Za-z0-9]*=[A-Za-z0-9_.+-]+$') { throw "Invalid diagnostic property: $property" }
}
$modsDir = Join-Path $runRoot 'mods'
$nativeDir = Join-Path $runRoot 'natives'
New-Item -ItemType Directory -Path $modsDir,$nativeDir | Out-Null
Copy-Item -LiteralPath $modJar,$helperJar,$apiJar -Destination $modsDir
foreach ($artifact in $ExtraModJars) {
    $destination = Join-Path $modsDir ([IO.Path]::GetFileName($artifact))
    if (Test-Path -LiteralPath $destination) { throw "Duplicate mod filename: $destination" }
    Copy-Item -LiteralPath $artifact -Destination $destination
}
if ($manifest.lwjglVersions -contains '3.3.3') { Copy-Item -LiteralPath 'F:/Desktop/.minecraft/versions/FPV/FPV-natives/lwjgl.dll' -Destination $nativeDir }
[IO.File]::WriteAllText((Join-Path $runRoot 'options.txt'), "onboardAccessibility:false`npauseOnLostFocus:false`nmaxFps:260`nenableVsync:false`nrenderDistance:8`nsimulationDistance:8`n", [Text.UTF8Encoding]::new($false))
$classpath = ($manifest.classpathEntries | ForEach-Object { $_.path }) -join ';'
$launchArguments = @(
    '-Xmx4G','-XX:+UseG1GC','--enable-native-access=ALL-UNNAMED',
    "-Djava.library.path=$nativeDir", "-Djna.tmpdir=$nativeDir",
    "-Dorg.lwjgl.system.SharedLibraryExtractPath=$nativeDir", "-Dio.netty.native.workdir=$nativeDir",
    '-Dminecraft.launcher.brand=RuntimeMonitorSmoke','-Dminecraft.launcher.version=1',
    '-Dfpvdrone.performance=true','-Dfpvdrone.performance.runtimeMonitorSmoke=true',
    '-Dfpvdrone.performance.hidden=true','-Dfpvdrone.performance.phaseSeconds=3',
    '-Dfpvdrone.performance.warmupSeconds=3','-Dfpvdrone.performance.dirtyRadius=1',
    "-Dfpvdrone.performance.workspace=$projectRoot",
    "-Dfpvdrone.performance.output=$runRoot/report.json"
) + $DiagnosticProperties + @(
    '-cp',$classpath,$manifest.mainClass,
    '--username','MonitorSmoke','--uuid','00000000000000000000000000000001','--accessToken','0',
    '--version','FPV','--versionType','release','--gameDir',$runRoot,
    '--assetsDir','F:/Desktop/.minecraft/assets','--assetIndex','29',
    '--userType','legacy','--width','1280','--height','720'
)
$argumentFile = Join-Path $runRoot 'launch.args'
$escapedArguments = $launchArguments | ForEach-Object { '"' + $_.Replace('\','\\').Replace('"','\"') + '"' }
[IO.File]::WriteAllLines($argumentFile,$escapedArguments,[Text.UTF8Encoding]::new($false))
$javaPath = 'C:/Users/Teni/AppData/Roaming/.minecraft/runtime/java-runtime-delta/bin/java.exe'
$process = Start-Process -FilePath $javaPath -ArgumentList ('@' + $argumentFile) -WorkingDirectory $runRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runRoot 'stdout.log') -RedirectStandardError (Join-Path $runRoot 'stderr.log') -PassThru
$receipt = [ordered]@{
    runId=$RunId;processId=$process.Id;gameDirectory=$runRoot;startTime=(Get-Date).ToString('o')
    classpathEntries=$manifest.classpathEntries.Count;lwjglVersions=$manifest.lwjglVersions
    modSha256=(Get-FileHash -LiteralPath $modJar -Algorithm SHA256).Hash.ToLowerInvariant()
    helperSha256=(Get-FileHash -LiteralPath $helperJar -Algorithm SHA256).Hash.ToLowerInvariant()
    extraMods=@($ExtraModJars | ForEach-Object { [ordered]@{filename=[IO.Path]::GetFileName($_);sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()} })
    diagnosticProperties=$DiagnosticProperties
    scope='Disposable normal production-runtime functional smoke; no user worlds or accounts'
}
$receipt | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runRoot 'launch-receipt.json') -Encoding UTF8
$receipt | ConvertTo-Json
if ($WaitForExit) {
    $process.WaitForExit()
    $receipt['exitCode'] = $process.ExitCode
    $receipt['endTime'] = (Get-Date).ToString('o')
    $receipt | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runRoot 'launch-receipt.json') -Encoding UTF8
    Write-Output "Production smoke exit code: $($process.ExitCode)"
    exit $process.ExitCode
}
