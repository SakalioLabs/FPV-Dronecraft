[CmdletBinding()]
param(
	[Parameter(Mandatory = $true)]
	[ValidateNotNullOrEmpty()]
	[string] $ReportPath,

	[Parameter(Mandatory = $true)]
	[ValidateNotNullOrEmpty()]
	[string] $ExpectedRunId
)

$ErrorActionPreference = 'Stop'

$requiredGates = @(
	'mods_loaded'
	'mekanism_ingredient_ids_resolved'
	'mekanism_conditional_recipes_loaded'
	'loaded_recipes_craft_modules'
	'assembled_loadout_item_persistence'
	'placement_preserves_loadout_tiers'
	'placed_drone_empty_battery_persistence'
	'quantum_public_frequency_configured'
	'quantum_real_sided_fe_supply'
	'quantum_insufficient_supply_fallback'
	'quantum_source_removed_fallback'
	'a4mc_native_gameplay_sample_available'
	'a4mc_bridge_matches_native_trust_and_fields'
)

if ($ExpectedRunId -cnotmatch '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$') {
	throw "ExpectedRunId must be a nonempty UUID: '$ExpectedRunId'."
}

if (-not (Test-Path -LiteralPath $ReportPath -PathType Leaf)) {
	throw "Integration report does not exist: '$ReportPath'."
}

try {
	$report = Get-Content -LiteralPath $ReportPath -Raw | ConvertFrom-Json
} catch {
	throw "Integration report is not valid JSON: '$ReportPath'. $($_.Exception.Message)"
}

if ($null -eq $report -or $report -isnot [pscustomobject]) {
	throw 'Integration report root must be a JSON object.'
}
if ($report.runId -isnot [string] -or [string]::IsNullOrWhiteSpace($report.runId)) {
	throw 'Integration report runId must be a nonempty UUID string.'
}
if ($report.runId -cnotmatch '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$') {
	throw "Integration report runId is not a UUID: '$($report.runId)'."
}
if ($report.runId -cne $ExpectedRunId) {
	throw "Integration report runId '$($report.runId)' does not exactly match ExpectedRunId '$ExpectedRunId'."
}
if ($report.minecraft -cne '1.21.1') {
	throw "Integration report minecraft must be exactly '1.21.1'; got '$($report.minecraft)'."
}
if ($report.complete -isnot [bool] -or -not $report.complete) {
	throw 'Integration report must have complete=true.'
}
if ($report.passed -isnot [bool] -or -not $report.passed) {
	throw 'Integration report must have passed=true.'
}

if ($null -eq $report.requiredGates -or $report.requiredGates -isnot [System.Array]) {
	throw 'Integration report requiredGates must be an array.'
}
$reportedRequiredGates = @($report.requiredGates)
if ($reportedRequiredGates.Count -ne $requiredGates.Count) {
	throw "Integration report requiredGates must contain exactly $($requiredGates.Count) names."
}
for ($index = 0; $index -lt $requiredGates.Count; $index++) {
	if ($reportedRequiredGates[$index] -isnot [string] -or $reportedRequiredGates[$index] -cne $requiredGates[$index]) {
		throw "Integration report requiredGates differs from the validator schema at index $index."
	}
}

if ($null -eq $report.gates -or $report.gates -isnot [pscustomobject]) {
	throw 'Integration report gates must be an object.'
}
$reportedGateNames = @($report.gates.PSObject.Properties | ForEach-Object { $_.Name })
if ($reportedGateNames.Count -ne $requiredGates.Count) {
	throw "Integration report gates must contain exactly $($requiredGates.Count) names."
}
foreach ($gateName in $requiredGates) {
	$gateProperty = $report.gates.PSObject.Properties[$gateName]
	if ($null -eq $gateProperty) {
		throw "Integration report is missing required gate '$gateName'."
	}
	if ($null -eq $gateProperty.Value -or $gateProperty.Value -isnot [pscustomobject]) {
		throw "Integration report gate '$gateName' must be an object."
	}
	if ($gateProperty.Value.status -isnot [string] -or $gateProperty.Value.status -cne 'PASS') {
		$status = $gateProperty.Value.status
		throw "Integration report gate '$gateName' did not PASS (status='$status')."
	}
}
foreach ($gateName in $reportedGateNames) {
	if ($requiredGates -cnotcontains $gateName) {
		throw "Integration report contains unknown gate '$gateName'."
	}
}

Write-Output "NeoForge integration report passed: runId=$ExpectedRunId, gates=$($requiredGates.Count), minecraft=1.21.1"
