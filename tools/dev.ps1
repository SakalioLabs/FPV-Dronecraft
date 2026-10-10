[CmdletBinding()]
param(
	[Parameter(Position = 0)]
	[ValidateSet('build', 'test', 'client')]
	[string]$Task = 'build',
	[string]$JdkHome,
	[switch]$Offline
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Test-Jdk21([string]$Candidate) {
	if ([string]::IsNullOrWhiteSpace($Candidate)) { return $false }
	$javaExecutable = Join-Path $Candidate 'bin/java.exe'
	$javacExecutable = Join-Path $Candidate 'bin/javac.exe'
	if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) { return $false }
	if (-not (Test-Path -LiteralPath $javacExecutable -PathType Leaf)) { return $false }
	$previousPreference = $ErrorActionPreference
	try {
		# Windows PowerShell treats the normal java -version stderr as errors.
		$ErrorActionPreference = 'Continue'
		$versionText = (& $javaExecutable -version 2>&1 | Out-String)
		$versionExitCode = $LASTEXITCODE
	} finally {
		$ErrorActionPreference = $previousPreference
	}
	return $versionExitCode -eq 0 -and $versionText -match 'version "21(?:\.|\")'
}

if ($JdkHome) {
	if (-not (Test-Jdk21 $JdkHome)) {
		throw "JdkHome must point to a JDK 21 installation: $JdkHome"
	}
	$selectedJdk = $JdkHome
} else {
	$candidates = @($env:JAVA_HOME)
	$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
	if ($javaCommand) {
		$candidates += Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
	}
	$gradleCache = if ($env:GRADLE_USER_HOME) {
		$env:GRADLE_USER_HOME
	} else {
		Join-Path $env:USERPROFILE '.gradle'
	}
	$cachedJdks = Join-Path $gradleCache 'jdks'
	if (Test-Path -LiteralPath $cachedJdks -PathType Container) {
		$candidates += Get-ChildItem -LiteralPath $cachedJdks -Directory |
			Select-Object -ExpandProperty FullName
	}
	$selectedJdk = $candidates | Select-Object -Unique | Where-Object { Test-Jdk21 $_ } |
		Select-Object -First 1
	if (-not $selectedJdk) {
		throw 'JDK 21 was not found. Set JAVA_HOME or pass -JdkHome <path-to-jdk-21>.'
	}
}

$selectedJdk = (Resolve-Path -LiteralPath $selectedJdk).ProviderPath
$gradleArguments = @('--console=plain')
if ($Offline) { $gradleArguments += '--offline' }
$gradleArguments += switch ($Task) {
	'build' { '--no-daemon'; 'build' }
	'test' { '--no-daemon'; ':drone-sim-core:test'; ':fabric-mod:test' }
	'client' { ':fabric-mod:runClient' }
}

$previousJavaHome = $env:JAVA_HOME
Push-Location $projectRoot
try {
	$env:JAVA_HOME = $selectedJdk
	Write-Host "Using JDK 21: $selectedJdk"
	& (Join-Path $projectRoot 'gradlew.bat') @gradleArguments
	$gradleExitCode = $LASTEXITCODE
} finally {
	$env:JAVA_HOME = $previousJavaHome
	Pop-Location
}
exit $gradleExitCode
