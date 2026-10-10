[CmdletBinding()]
param(
    [ValidateSet('build', 'client', 'smoke')]
    [string]$Action = 'build',
    [string]$Version = '0.1.1-playtest.11',
    [string]$RunId = (Get-Date -Format 'yyyyMMdd-HHmmss'),
    [string]$JdkHome,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$candidates = @()
if ($JdkHome) {
    $candidates += $JdkHome
} else {
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    if ($env:APPDATA) {
        $candidates += Join-Path $env:APPDATA '.minecraft/runtime/java-runtime-delta'
    }
    $gradleCache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $jdkCache = Join-Path $gradleCache 'jdks'
    if (Test-Path -LiteralPath $jdkCache) {
        $candidates += Get-ChildItem -LiteralPath $jdkCache -Directory | ForEach-Object { $_.FullName }
    }
}

$selectedJdk = $null
foreach ($candidate in $candidates) {
    $javaExe = Join-Path $candidate 'bin/java.exe'
    $javacExe = Join-Path $candidate 'bin/javac.exe'
    if (!(Test-Path -LiteralPath $javaExe) -or !(Test-Path -LiteralPath $javacExe)) { continue }
    $releaseFile = Join-Path $candidate 'release'
    $javaVersion = if (Test-Path -LiteralPath $releaseFile) { Get-Content -LiteralPath $releaseFile -Raw } else { '' }
    if ($javaVersion -match 'JAVA_VERSION="21[.\"]') {
        $selectedJdk = (Resolve-Path -LiteralPath $candidate).Path
        break
    }
}
if (!$selectedJdk) {
    throw 'Java 21 JDK not found. Supply -JdkHome with a JDK 21 directory containing bin/java.exe and bin/javac.exe.'
}

$tasks = switch ($Action) {
    'build' { @(':drone-sim-core:build', ':computational-acoustics-core:build', ':fabric-mod:build') }
    'client' { @(':fabric-mod:runClient') }
    'smoke' { @(':fabric-mod:runPlayableClientGameTest') }
}
$gradleArgs = @('--no-daemon', '--console=plain', "-Pmod_version=$Version")
if ($Offline) { $gradleArgs += '--offline' }
if ($Action -eq 'smoke') { $gradleArgs += "-PplaytestRunId=$RunId" }
$gradleArgs += $tasks
$previousJavaHome = $env:JAVA_HOME
Push-Location -LiteralPath $projectRoot
try {
    $env:JAVA_HOME = $selectedJdk
    Write-Host "Java 21: $selectedJdk"
    & (Join-Path $projectRoot 'gradlew.bat') @gradleArgs
    $result = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
exit $result
