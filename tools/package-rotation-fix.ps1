param(
    [string]$PackageDirectory = 'F:/Codex-Project/MCFPV/deliverables/rotation-fix-20261010',
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$ProductionRunId = 'rotation-production-32-20261010',
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$ClientRunId = 'rotation-fix-20261010'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$packageRoot = [IO.Path]::GetFullPath($PackageDirectory)
if (Test-Path -LiteralPath $packageRoot) { throw 'Package directory already exists' }
$productionRoot = Join-Path $projectRoot ('build/performance/' + $ProductionRunId)
$report = Get-Content -LiteralPath (Join-Path $productionRoot 'report.json') -Raw | ConvertFrom-Json
$receipt = Get-Content -LiteralPath (Join-Path $productionRoot 'launch-receipt.json') -Raw | ConvertFrom-Json
if (!$report.passed -or !$report.runtime_monitor_smoke.passed -or $receipt.exitCode -ne 0) { throw 'Production functional smoke did not pass' }
$clientReportPath = Join-Path $projectRoot ('fabric-mod/build/run/playableClientGameTest/' + $ClientRunId + '/playtest-client-report.json')
$clientReport = Get-Content -LiteralPath $clientReportPath -Raw | ConvertFrom-Json
if (!$clientReport.passed -or !$clientReport.runtimePerformanceFunctionalSmoke.passed) { throw 'Client functional smoke did not pass' }
$serverLogPath = Join-Path $projectRoot 'build/rotation-fix-release-20261010/server-gametest.log'
$serverLog = Get-Content -LiteralPath $serverLogPath -Raw
if ($serverLog -notmatch 'fpvdrone 0\.1\.1-playtest\.6' -or $serverLog -notmatch '10 tests are now running' -or $serverLog -notmatch '10 GAME TESTS COMPLETE' -or $serverLog -notmatch 'All 10 required tests passed') { throw 'Rotation server GameTests did not pass' }
$jarName = 'fpv-dronecraft-fabric-0.1.1-playtest.6.jar'
$sodiumName = 'sodium-fabric-0.8.14+mc1.21.11.jar'
$jar = Join-Path $projectRoot ('fabric-mod/build/libs/' + $jarName)
$sodium = Join-Path $projectRoot ('build/render-optimization-20261010/' + $sodiumName)
$jarSha = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
$sodiumSha = (Get-FileHash -LiteralPath $sodium -Algorithm SHA256).Hash.ToLowerInvariant()
if ($jarSha -ne $receipt.modSha256) { throw 'Production mod differs from tested artifact' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$productionJar = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    if (@($productionJar.Entries | Where-Object {$_.FullName -match '^com/tenicana/dronecraft/(performance|gametest|playtest)/|^fpvdrone-(gametest|performance|playtest)\.'}).Count) { throw 'Diagnostic helper entered production JAR' }
    if (!$productionJar.GetEntry('com/tenicana/dronecraft/client/render/RotorLayoutRenderCache.class')) { throw 'Renderer cache missing from production JAR' }
} finally {$productionJar.Dispose()}
if ($sodiumSha -ne 'fd2619eff5da6b9ba6304b8b72ac3fe132c30e0bd5d675e4581eda417a272f5d' -or @($receipt.extraMods | Where-Object {$_.filename -eq $sodiumName -and $_.sha256 -eq $sodiumSha}).Count -ne 1) { throw 'Sodium differs from tested official artifact' }
$installerCheck = Join-Path $projectRoot 'build/rotation-fix-installer-check/installer-verification.json'
$installer = Get-Content -LiteralPath $installerCheck -Raw | ConvertFrom-Json
if (!$installer.all_pass) { throw 'Installer verification did not pass' }
$junit = @()
foreach ($module in @('fabric-mod','drone-sim-core','computational-acoustics-core')) {
    $files = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot "$module/build/test-results/test") -Filter 'TEST-*.xml')
    if (!$files.Count) { throw "Missing JUnit results: $module" }
    $counts = [ordered]@{module=$module;tests=0;failures=0;errors=0;skipped=0}
    foreach ($file in $files) {
        $suite = ([xml](Get-Content -LiteralPath $file.FullName -Raw)).testsuite
        foreach ($key in @('tests','failures','errors','skipped')) { $counts[$key] += [int]$suite.GetAttribute($key) }
    }
    if ($counts.failures -or $counts.errors -or $counts.skipped) { throw "JUnit result not clean: $module" }
    $junit += $counts
}
$mods = Join-Path $packageRoot 'mods'
$verification = Join-Path $packageRoot 'verification'
New-Item -ItemType Directory -Path $mods,$verification | Out-Null
Copy-Item -LiteralPath $jar,$sodium -Destination $mods
$utf8 = [Text.UTF8Encoding]::new($false)
$bom = [Text.UTF8Encoding]::new($true)
foreach ($script in @('install-rotation-fix.ps1','rollback-rotation-fix.ps1')) {
    [IO.File]::WriteAllText((Join-Path $packageRoot $script),[IO.File]::ReadAllText((Join-Path $PSScriptRoot $script)),$bom)
}
$manifest = [ordered]@{
    version='0.1.1-playtest.6';old_filename='fpv-dronecraft-fabric-0.1.1-playtest.5.jar'
    old_sha256='b5afdaa814d2d038228678c8887d9bfd528ad01e05601a77fe93eed3bba5c304'
    new_filename=$jarName;new_sha256=$jarSha;sodium_filename=$sodiumName;sodium_sha256=$sodiumSha
    api_filename='fabric-api-0.141.6+1.21.11.jar';api_sha256='bdff7fd7e220085cfad2ff9b1f40dde6534ae0b96cf378f97a374bc54cb9ed0f'
}
[IO.File]::WriteAllText((Join-Path $packageRoot 'install-manifest.json'),($manifest | ConvertTo-Json),$utf8)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($sodium)
try {
    $entry = $archive.GetEntry('LICENSE.md')
    if (!$entry) { throw 'Sodium license is missing' }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { [IO.File]::WriteAllText((Join-Path $packageRoot 'Sodium-LICENSE.md'),$reader.ReadToEnd(),$utf8) } finally { $reader.Dispose() }
} finally { $archive.Dispose() }
Copy-Item -LiteralPath (Join-Path $productionRoot 'report.json') -Destination (Join-Path $verification 'production-report.json')
Copy-Item -LiteralPath (Join-Path $productionRoot 'launch-receipt.json'),$installerCheck -Destination $verification
Copy-Item -LiteralPath $clientReportPath -Destination (Join-Path $verification 'client-report.json')
Copy-Item -LiteralPath $serverLogPath -Destination (Join-Path $verification 'server-gametest.log')
Copy-Item -LiteralPath (Join-Path $projectRoot 'build/rotation-fix-release-20261010/golden-revision-comparison.json') -Destination $verification
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/development/rotation-continuity-20261010.md') -Destination (Join-Path $packageRoot 'README-翻滚修复.md')
$validation = [ordered]@{
    version=$manifest.version;mod_sha256=$jarSha;sodium_sha256=$sodiumSha;junit=$junit
    server_gametest_required=10;server_gametest_passed=10;client_passed=$clientReport.passed
    production_passed=$report.passed;process_exit_code=$receipt.exitCode
    save_reopen=$report.verification;runtime_monitor=$report.runtime_monitor_smoke
    render_stress=$report.render_stress;installer_all_pass=$installer.all_pass
    scope='Normal production runtime in a disposable fixed-seed world; no user-world/display performance guarantee'
}
[IO.File]::WriteAllText((Join-Path $verification 'validation.json'),($validation | ConvertTo-Json -Depth 12),$utf8)
$inventory = @(Get-ChildItem -LiteralPath $packageRoot -File -Recurse | ForEach-Object {
    [ordered]@{path=$_.FullName.Substring($packageRoot.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant();bytes=$_.Length}
})
[IO.File]::WriteAllText((Join-Path $packageRoot 'SHA256SUMS.json'),($inventory | ConvertTo-Json),$utf8)
[ordered]@{package_directory=$packageRoot;files=$inventory.Count;mod_sha256=$jarSha;sodium_sha256=$sodiumSha} | ConvertTo-Json
