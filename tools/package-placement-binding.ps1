param(
    [string]$PackageDirectory = 'F:/Codex-Project/MCFPV/deliverables/placement-binding-20261010',
    [string]$ZipPath,
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$ProductionRunId = 'placement-binding-production-20261010-verified5',
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$ClientRunId = 'placement-binding-20261010-final2'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$packageRoot = [IO.Path]::GetFullPath($PackageDirectory)
if (Test-Path -LiteralPath $packageRoot) { throw 'Package directory already exists' }
$productionRoot = Join-Path $projectRoot ('build/performance/' + $ProductionRunId)
$report = Get-Content -LiteralPath (Join-Path $productionRoot 'report.json') -Raw | ConvertFrom-Json
$receipt = Get-Content -LiteralPath (Join-Path $productionRoot 'launch-receipt.json') -Raw | ConvertFrom-Json
if (!$report.passed -or !$report.runtime_monitor_smoke.passed -or $receipt.exitCode -ne 0) { throw 'Production functional smoke did not pass' }
if (!$report.three_view_smoke.enabled -or !$report.three_view_smoke.passed) { throw 'Three-view camera smoke did not pass' }
$placementBindingSmoke = $report.placement_binding_smoke
$placementBindingSmokePassed = $placementBindingSmoke.enabled -eq $true -and $placementBindingSmoke.passed -eq $true
if (!$placementBindingSmokePassed) { throw 'Placement binding smoke did not pass' }
$placementBindingSmokeReportPath = Join-Path $productionRoot 'placement-binding-smoke-report.json'
if (!(Test-Path -LiteralPath $placementBindingSmokeReportPath -PathType Leaf)) { throw 'Placement binding smoke report is missing' }
$placementBindingSmokeReport = Get-Content -LiteralPath $placementBindingSmokeReportPath -Raw | ConvertFrom-Json
if ($placementBindingSmokeReport.passed -ne $true) { throw 'Placement binding smoke report did not pass' }
$remoteProductionRoot = Join-Path $projectRoot 'build/performance/placement-binding-remote-20261010-verified'
$remoteReport = Get-Content -LiteralPath (Join-Path $remoteProductionRoot 'report.json') -Raw | ConvertFrom-Json
$remoteReceiptPath = Join-Path $remoteProductionRoot 'launch-receipt.json'
$remoteReceipt = Get-Content -LiteralPath $remoteReceiptPath -Raw | ConvertFrom-Json
$remotePilotSmoke = $remoteReport.remote_pilot_smoke
if (!$remoteReport.passed -or !$remoteReport.runtime_monitor_smoke.passed -or !$remotePilotSmoke.enabled -or !$remotePilotSmoke.passed -or $remotePilotSmoke.physical_controller_binding -ne $true -or $remoteReceipt.exitCode -ne 0 -or $remoteReceipt.modSha256.ToLowerInvariant() -ne $receipt.modSha256.ToLowerInvariant()) { throw 'Remote pilot production smoke did not pass for the primary production artifact' }
$remotePilotSmokeReportPath = Join-Path $remoteProductionRoot 'remote-pilot-smoke-report.json'
if (!(Test-Path -LiteralPath $remotePilotSmokeReportPath -PathType Leaf)) { throw 'Remote pilot smoke report is missing' }
$remotePilotSmokeReport = Get-Content -LiteralPath $remotePilotSmokeReportPath -Raw | ConvertFrom-Json
if ($remotePilotSmokeReport.passed -ne $true) { throw 'Remote pilot smoke report did not pass' }
$visualReviewPath = Join-Path $projectRoot 'build/placement-binding-release-20261010/placement-binding-visual-review.json'
$visualReview = Get-Content -LiteralPath $visualReviewPath -Raw | ConvertFrom-Json
if (!$visualReview.passed -or $visualReview.mod_sha256 -ne $receipt.modSha256) { throw 'Placement binding screenshots need review for this production artifact' }
$importReportPath = Join-Path $projectRoot 'build/controller-model-release-20261010/controller-import-report.json'
$importReport = Get-Content -LiteralPath $importReportPath -Raw | ConvertFrom-Json
if (!$importReport.passed -or $importReport.model_sha256 -ne '80e5d74b8d53468a5e279afcef31f30c0b75bcac3662f01b4504f9ac2203b4bd' -or $importReport.texture_sha256 -ne '7071448ea7554b08515baa23ccffbee4fb85abf5c90f33f4eec8212b85c80abb' -or $importReport.item_wrapper_sha256 -ne '725c38c0950e5a3ca2071accea8fa63f8943a34c9f1fd6bb18a7819d372e40e0' -or !$importReport.model_bytes_equal_to_source -or !$importReport.texture_bytes_equal_to_source -or $importReport.item_wrapper.parent -ne 'fpvdrone:item/fpv_controller_v1' -or $importReport.item_wrapper.textures.particle -ne 'fpvdrone:item/fpv_controller_v1' -or $importReport.item_wrapper.PSObject.Properties['elements']) { throw 'Controller import report does not prove the exact source model, texture, and item wrapper' }
$clientReportPath = Join-Path $projectRoot ('fabric-mod/build/run/playableClientGameTest/' + $ClientRunId + '/playtest-client-report.json')
$clientReport = Get-Content -LiteralPath $clientReportPath -Raw | ConvertFrom-Json
if (!$clientReport.passed -or !$clientReport.runtimePerformanceFunctionalSmoke.passed) { throw 'Client functional smoke did not pass' }
$serverLogPath = Join-Path $projectRoot 'build/placement-binding-release-20261010/server-gametest.log'
$serverLog = Get-Content -LiteralPath $serverLogPath -Raw
$runningMatch = [regex]::Match($serverLog, '(?m)(?<count>\d+) tests are now running')
$completeMatch = [regex]::Match($serverLog, '(?m)(?<count>\d+) GAME TESTS COMPLETE')
$passedMatch = [regex]::Match($serverLog, '(?m)All (?<count>\d+) required tests passed')
$serverTestCount = if ($runningMatch.Success) { [int]$runningMatch.Groups['count'].Value } else { 0 }
if ($serverLog -notmatch 'fpvdrone 0\.1\.1-playtest\.11' -or !$runningMatch.Success -or !$completeMatch.Success -or !$passedMatch.Success -or $serverTestCount -ne 16 -or [int]$completeMatch.Groups['count'].Value -ne $serverTestCount -or [int]$passedMatch.Groups['count'].Value -ne $serverTestCount) { throw 'Server GameTests did not pass with the expected consistent test count' }
$jarName = 'fpv-dronecraft-fabric-0.1.1-playtest.11.jar'
$sodiumName = 'sodium-fabric-0.8.14+mc1.21.11.jar'
$jar = Join-Path $projectRoot ('fabric-mod/build/libs/' + $jarName)
$sodium = Join-Path $projectRoot ('build/render-optimization-20261010/' + $sodiumName)
$jarSha = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
$sodiumSha = (Get-FileHash -LiteralPath $sodium -Algorithm SHA256).Hash.ToLowerInvariant()
if ($jarSha -ne $receipt.modSha256) { throw 'Production mod differs from tested artifact' }
$jarInventoryPath = Join-Path $projectRoot 'build/placement-binding-release-20261010/jar-change-inventory.json'
$jarInventory = Get-Content -LiteralPath $jarInventoryPath -Raw | ConvertFrom-Json
if ($jarInventory.status -ne 'final' -or $jarInventory.all_pass -ne $true -or $jarInventory.new_jar.sha256 -ne $jarSha) { throw 'Final JAR change review does not match this artifact' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$productionJar = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    if (@($productionJar.Entries | Where-Object {$_.FullName -match '^com/tenicana/dronecraft/(performance|gametest|playtest)/|^fpvdrone-(gametest|performance|playtest)\.'}).Count) { throw 'Diagnostic helper entered production JAR' }
    if (!$productionJar.GetEntry('com/tenicana/dronecraft/client/render/RotorLayoutRenderCache.class')) { throw 'Renderer cache missing from production JAR' }
    if (!$productionJar.GetEntry('com/tenicana/dronecraft/client/render/ImportedDroneDesign.class')) { throw 'Imported model missing from production JAR' }
    if (!$productionJar.GetEntry('com/tenicana/dronecraft/client/mixin/RemotePilotBodyRenderMixin.class')) { throw 'Remote pilot body render fix missing from production JAR' }
    if (!$productionJar.GetEntry('com/tenicana/dronecraft/item/DronePlacementItem.class')) { throw 'Drone placement item missing from production JAR' }
    if (!$productionJar.GetEntry('com/tenicana/dronecraft/camera/DroneViewMode.class') -or !$productionJar.GetEntry('com/tenicana/dronecraft/camera/DroneThirdPersonCamera.class')) { throw 'Three-view camera classes missing from production JAR' }
    foreach ($entryName in @(
        'com/tenicana/dronecraft/network/DroneRemoteSessions.class',
        'com/tenicana/dronecraft/network/DroneSessionEndedPayload.class',
        'com/tenicana/dronecraft/network/DroneChunkCentersPayload.class',
        'com/tenicana/dronecraft/server/mixin/RemoteChunkMapMixin.class',
        'com/tenicana/dronecraft/server/mixin/RemoteTrackedEntityMixin.class',
        'com/tenicana/dronecraft/server/mixin/RemotePlayerChunkSenderMixin.class',
        'com/tenicana/dronecraft/remote/RemoteChunkMapAccess.class',
        'com/tenicana/dronecraft/remote/DroneChunkTrackingView.class',
        'com/tenicana/dronecraft/client/mixin/RemoteClientChunkCacheMixin.class',
        'com/tenicana/dronecraft/client/mixin/RemoteLocalPlayerBodyMixin.class',
        'com/tenicana/dronecraft/client/mixin/RemoteControlledDroneTickMixin.class',
        'com/tenicana/dronecraft/client/chunk/RemoteClientChunkStorage.class',
        'com/tenicana/dronecraft/client/chunk/RemoteClientChunkCacheAccess.class',
        'fpvdrone.server.mixins.json',
        'fpvdrone.client.mixins.json',
        'fpvdrone-remote.accesswidener'
    )) {
        if (!$productionJar.GetEntry($entryName)) { throw "Controller model production entry missing: $entryName" }
    }
    $texture = $productionJar.GetEntry('assets/fpvdrone/textures/entity/drone.png')
    if (!$texture) { throw 'Imported texture missing from production JAR' }
    $textureStream = $texture.Open()
    $textureDigest = [Security.Cryptography.SHA256]::Create()
    try { $textureSha = ([BitConverter]::ToString($textureDigest.ComputeHash($textureStream))).Replace('-','').ToLowerInvariant() }
    finally { $textureStream.Dispose(); $textureDigest.Dispose() }
    if ($textureSha -ne 'f9c732723164c4e25687cc9a70b0c66300b43326baf0322fb0e78cd7a3fcbe57') { throw 'Production texture differs from supplied design' }
    $controllerModel = $productionJar.GetEntry('assets/fpvdrone/models/item/fpv_controller_v1.json')
    $controllerWrapper = $productionJar.GetEntry('assets/fpvdrone/models/item/drone_controller.json')
    $controllerTexture = $productionJar.GetEntry('assets/fpvdrone/textures/item/fpv_controller_v1.png')
    $itemDefinition = $productionJar.GetEntry('assets/fpvdrone/items/drone_controller.json')
    $droneItemDefinition = $productionJar.GetEntry('assets/fpvdrone/items/drone.json')
    $droneItemModel = $productionJar.GetEntry('assets/fpvdrone/models/item/drone.json')
    if (!$controllerModel -or !$controllerWrapper -or !$controllerTexture -or !$itemDefinition) { throw 'Controller source model, wrapper, texture, or item definition is missing from production JAR' }
    if (!$droneItemDefinition -or !$droneItemModel) { throw 'Drone placement item definition or model is missing from production JAR' }
    foreach ($asset in @(@{entry=$controllerModel;expected='80e5d74b8d53468a5e279afcef31f30c0b75bcac3662f01b4504f9ac2203b4bd';label='controller model'},@{entry=$controllerWrapper;expected='725c38c0950e5a3ca2071accea8fa63f8943a34c9f1fd6bb18a7819d372e40e0';label='controller item wrapper'},@{entry=$controllerTexture;expected='7071448ea7554b08515baa23ccffbee4fb85abf5c90f33f4eec8212b85c80abb';label='controller texture'})) {
        $stream = $asset.entry.Open(); $digest = [Security.Cryptography.SHA256]::Create()
        try { $actual = ([BitConverter]::ToString($digest.ComputeHash($stream))).Replace('-','').ToLowerInvariant() }
        finally { $stream.Dispose(); $digest.Dispose() }
        if ($actual -ne $asset.expected) { throw "Production $($asset.label) differs from imported source asset" }
    }
    $wrapperReader = [IO.StreamReader]::new($controllerWrapper.Open())
    try { $wrapper = $wrapperReader.ReadToEnd() | ConvertFrom-Json } finally { $wrapperReader.Dispose() }
    if ($wrapper.parent -ne 'fpvdrone:item/fpv_controller_v1' -or $wrapper.textures.particle -ne 'fpvdrone:item/fpv_controller_v1' -or $wrapper.PSObject.Properties['elements']) { throw 'Controller wrapper parent, particle texture, or empty geometry check failed' }
    $definitionReader = [IO.StreamReader]::new($itemDefinition.Open())
    try { $definition = $definitionReader.ReadToEnd() | ConvertFrom-Json } finally { $definitionReader.Dispose() }
    if ($definition.model.type -ne 'minecraft:model' -or $definition.model.model -ne 'fpvdrone:item/drone_controller') { throw 'Controller item definition does not point to the exact production model' }
    $droneDefinitionReader = [IO.StreamReader]::new($droneItemDefinition.Open())
    try { $droneDefinition = $droneDefinitionReader.ReadToEnd() | ConvertFrom-Json } finally { $droneDefinitionReader.Dispose() }
    if ($droneDefinition.model.type -ne 'minecraft:model' -or $droneDefinition.model.model -ne 'fpvdrone:item/drone') { throw 'Drone item definition does not point to the production drone model' }
} finally {$productionJar.Dispose()}
if ($sodiumSha -ne 'fd2619eff5da6b9ba6304b8b72ac3fe132c30e0bd5d675e4581eda417a272f5d' -or @($receipt.extraMods | Where-Object {$_.filename -eq $sodiumName -and $_.sha256 -eq $sodiumSha}).Count -ne 1) { throw 'Sodium differs from tested official artifact' }
$installerCheck = Join-Path $projectRoot 'build/placement-binding-installer-check/installer-verification.json'
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
$unitResults = Join-Path $verification 'unit-test-results'
New-Item -ItemType Directory -Path $unitResults | Out-Null
foreach ($module in @('fabric-mod','drone-sim-core','computational-acoustics-core')) {
    $destination = Join-Path $unitResults $module
    New-Item -ItemType Directory -Path $destination | Out-Null
    $moduleXml = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot "$module/build/test-results/test") -Filter 'TEST-*.xml' -File)
    if (!$moduleXml.Count) { throw "Missing JUnit result files for $module" }
    Copy-Item -LiteralPath $moduleXml.FullName -Destination $destination
}
$utf8 = [Text.UTF8Encoding]::new($false)
$bom = [Text.UTF8Encoding]::new($true)
foreach ($script in @('install-placement-binding.ps1','rollback-placement-binding.ps1')) {
    [IO.File]::WriteAllText((Join-Path $packageRoot $script),[IO.File]::ReadAllText((Join-Path $PSScriptRoot $script)),$bom)
}
$manifest = [ordered]@{
    version='0.1.1-playtest.11';old_filename='fpv-dronecraft-fabric-0.1.1-playtest.10.jar'
    old_sha256='c03b061879353ea3792118bd8d734e882c72bd4618466f94847e777c8f3f3f7f'
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
Copy-Item -LiteralPath $jarInventoryPath,$importReportPath -Destination $verification
Copy-Item -LiteralPath (Join-Path $projectRoot 'build/placement-binding-release-20261010/proof-audit.json') -Destination $verification
Copy-Item -LiteralPath $placementBindingSmokeReportPath -Destination $verification
$remoteVerification = Join-Path $verification 'remote-pilot'
New-Item -ItemType Directory -Path $remoteVerification | Out-Null
Copy-Item -LiteralPath (Join-Path $remoteProductionRoot 'report.json') -Destination (Join-Path $remoteVerification 'production-report.json')
Copy-Item -LiteralPath $remotePilotSmokeReportPath -Destination $remoteVerification
Copy-Item -LiteralPath $remoteReceiptPath -Destination $remoteVerification
Copy-Item -LiteralPath (Join-Path $remoteProductionRoot 'screenshots') -Destination $remoteVerification -Recurse
Copy-Item -LiteralPath (Join-Path $productionRoot 'screenshots') -Destination $verification -Recurse
Copy-Item -LiteralPath (Join-Path $productionRoot 'launch-receipt.json'),$installerCheck -Destination $verification
Copy-Item -LiteralPath $clientReportPath -Destination (Join-Path $verification 'client-report.json')
Copy-Item -LiteralPath $serverLogPath -Destination (Join-Path $verification 'server-gametest.log')
Copy-Item -LiteralPath $visualReviewPath -Destination $verification
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/development/placement-binding-20261010.md') -Destination (Join-Path $packageRoot 'README-放置绑定与玩家模型.md')
$validation = [ordered]@{
    version=$manifest.version;mod_sha256=$jarSha;sodium_sha256=$sodiumSha;junit=$junit
    server_gametest_required=$serverTestCount;server_gametest_passed=$serverTestCount;client_passed=$clientReport.passed
    production_passed=$report.passed;process_exit_code=$receipt.exitCode
    save_reopen=$report.verification;runtime_monitor=$report.runtime_monitor_smoke
    render_stress=$report.render_stress;installer_all_pass=$installer.all_pass
    model_visual_review=$visualReview;drone_texture_sha256=$textureSha
    controller_model_sha256='80e5d74b8d53468a5e279afcef31f30c0b75bcac3662f01b4504f9ac2203b4bd'
    controller_wrapper_sha256='725c38c0950e5a3ca2071accea8fa63f8943a34c9f1fd6bb18a7819d372e40e0'
    controller_texture_sha256='7071448ea7554b08515baa23ccffbee4fb85abf5c90f33f4eec8212b85c80abb'
    three_view_smoke=$report.three_view_smoke;placement_binding_smoke_passed=$placementBindingSmokePassed
    remote_pilot_smoke=$remotePilotSmoke
    view_cycle=@('LINE_OF_SIGHT','FPV','THIRD_PERSON','LINE_OF_SIGHT')
    scope='Normal production runtime in a disposable fixed-seed world; no user-world/display performance guarantee'
}
[IO.File]::WriteAllText((Join-Path $verification 'validation.json'),($validation | ConvertTo-Json -Depth 12),$utf8)
$inventory = @(Get-ChildItem -LiteralPath $packageRoot -File -Recurse | ForEach-Object {
    [ordered]@{path=$_.FullName.Substring($packageRoot.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant();bytes=$_.Length}
})
[IO.File]::WriteAllText((Join-Path $packageRoot 'SHA256SUMS.json'),($inventory | ConvertTo-Json),$utf8)
if (!$ZipPath) { $ZipPath = Join-Path (Split-Path -Parent $packageRoot) 'FPV-Dronecraft-0.1.1-playtest.11.zip' }
$zipFullPath = [IO.Path]::GetFullPath($ZipPath)
if (Test-Path -LiteralPath $zipFullPath) { throw 'Package ZIP already exists' }
Compress-Archive -Path (Join-Path $packageRoot '*') -DestinationPath $zipFullPath -CompressionLevel Optimal
[ordered]@{package_directory=$packageRoot;zip_path=$zipFullPath;files=$inventory.Count;mod_sha256=$jarSha;sodium_sha256=$sodiumSha} | ConvertTo-Json
