param(
    [string]$DesignDirectory = 'F:/Codex-Project/MCFPV/deliverables/controller-design-20261010/blockbench'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$designRoot = (Resolve-Path -LiteralPath $DesignDirectory).Path
$sourceModel = Join-Path $designRoot 'fpv_controller_v1.json'
$sourceTexture = Join-Path $designRoot 'fpv_controller_v1.png'
$model = Get-Content -LiteralPath $sourceModel -Raw | ConvertFrom-Json
if ($model.format_version -ne '1.21.11' -or @($model.elements).Count -ne 283) { throw 'Unexpected controller model format or geometry' }
if ($model.textures.'0' -ne 'fpvdrone:item/fpv_controller_v1') { throw 'Unexpected controller texture reference' }
foreach ($context in @('firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand','gui','ground','fixed')) {
    if (!$model.display.$context) { throw "Missing display context: $context" }
}
$resourceRoot = Join-Path $projectRoot 'fabric-mod/src/main/resources/assets/fpvdrone'
$targetModel = Join-Path $resourceRoot 'models/item/fpv_controller_v1.json'
$targetWrapper = Join-Path $resourceRoot 'models/item/drone_controller.json'
$textureDirectory = Join-Path $resourceRoot 'textures/item'
New-Item -ItemType Directory -Path $textureDirectory -Force | Out-Null
$targetTexture = Join-Path $textureDirectory 'fpv_controller_v1.png'
Copy-Item -LiteralPath $sourceModel -Destination $targetModel
Copy-Item -LiteralPath $sourceTexture -Destination $targetTexture
$wrapper = [ordered]@{parent='fpvdrone:item/fpv_controller_v1';textures=[ordered]@{particle='fpvdrone:item/fpv_controller_v1'}}
[IO.File]::WriteAllText($targetWrapper,($wrapper | ConvertTo-Json -Depth 4) + [Environment]::NewLine,[Text.UTF8Encoding]::new($false))
$itemDefinition = Get-Content -LiteralPath (Join-Path $resourceRoot 'items/drone_controller.json') -Raw | ConvertFrom-Json
if ($itemDefinition.model.type -ne 'minecraft:model' -or $itemDefinition.model.model -ne 'fpvdrone:item/drone_controller') { throw 'Controller item definition is not linked to the imported model' }
$report = [ordered]@{
    passed = $true
    source_directory = $designRoot
    elements = @($model.elements).Count
    format_version = $model.format_version
    model_sha256 = (Get-FileHash -LiteralPath $targetModel -Algorithm SHA256).Hash.ToLowerInvariant()
    texture_sha256 = (Get-FileHash -LiteralPath $targetTexture -Algorithm SHA256).Hash.ToLowerInvariant()
    item_wrapper_sha256 = (Get-FileHash -LiteralPath $targetWrapper -Algorithm SHA256).Hash.ToLowerInvariant()
    item_wrapper = $wrapper
    model_bytes_equal_to_source = (Get-FileHash -LiteralPath $sourceModel -Algorithm SHA256).Hash -eq (Get-FileHash -LiteralPath $targetModel -Algorithm SHA256).Hash
    texture_bytes_equal_to_source = (Get-FileHash -LiteralPath $sourceTexture -Algorithm SHA256).Hash -eq (Get-FileHash -LiteralPath $targetTexture -Algorithm SHA256).Hash
    item_definition = $itemDefinition
    display = $model.display
}
$reportDirectory = Join-Path $projectRoot 'build/controller-model-release-20261010'
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $reportDirectory 'controller-import-report.json'),($report | ConvertTo-Json -Depth 12),[Text.UTF8Encoding]::new($false))
$report | ConvertTo-Json -Depth 12
