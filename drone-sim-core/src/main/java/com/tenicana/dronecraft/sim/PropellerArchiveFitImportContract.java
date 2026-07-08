package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveFitImportContract {
	public static final String SOURCE_ID = "User-Propeller-Archive-Fit-Import-Contract-Packet";
	public static final String CAVEAT =
			"Fit import contract defines the reviewed offline schema for propeller CT/CP/eta and blade-geometry rows; it imports no raw rows, fits no coefficients, and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int FIELD_CONTRACT_ROW_COUNT = 26;
	public static final int PRESET_INPUT_ROW_COUNT = 4;
	public static final int IMPORT_STAGE_ROW_COUNT = 8;
	public static final int SUMMARY_ROW_COUNT = 10;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ FIELD_CONTRACT_ROW_COUNT
			+ PRESET_INPUT_ROW_COUNT
			+ IMPORT_STAGE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final List<ImportFieldContract> FIELD_CONTRACTS = List.of(
			new ImportFieldContract("performance", "source_file", "source_file", "path", true,
					"nonblank-reviewed-source-id", "provenance and volume split"),
			new ImportFieldContract("performance", "PropName", "propeller_id", "text", true,
					"nonblank", "performance curve grouping"),
			new ImportFieldContract("performance", "BladeName", "blade_id", "text", true,
					"nonblank", "geometry/performance join key"),
			new ImportFieldContract("performance", "Family", "family_id", "text", true,
					"nonblank", "manufacturer/family grouping"),
			new ImportFieldContract("performance", "B", "blade_count", "count", true,
					"positive-integer", "blade-count coverage gate"),
			new ImportFieldContract("performance", "D", "diameter_inches", "in", true,
					"finite-positive", "RotorSpec diameter matching"),
			new ImportFieldContract("performance", "P", "pitch_inches", "in", true,
					"finite-positive", "RotorSpec pitch matching"),
			new ImportFieldContract("performance", "J", "advance_ratio", "J", true,
					"finite-nonnegative", "CT/CP curve independent variable"),
			new ImportFieldContract("performance", "N", "rpm", "rpm", true,
					"finite-positive", "Reynolds/RPM binning"),
			new ImportFieldContract("performance", "CT", "thrust_coefficient", "coefficient", true,
					"finite", "rotor thrust coefficient fit"),
			new ImportFieldContract("performance", "CP", "power_coefficient", "coefficient", true,
					"finite-positive", "rotor power coefficient fit"),
			new ImportFieldContract("performance", "eta", "efficiency", "ratio", true,
					"finite", "power/efficiency consistency check"),
			new ImportFieldContract("geometry", "source_file", "source_file", "path", true,
					"nonblank-reviewed-source-id", "provenance and volume split"),
			new ImportFieldContract("geometry", "BladeName", "blade_id", "text", true,
					"nonblank", "geometry/performance join key"),
			new ImportFieldContract("geometry", "Family", "family_id", "text", true,
					"nonblank", "manufacturer/family grouping"),
			new ImportFieldContract("geometry", "D", "diameter_inches", "in", true,
					"finite-positive", "geometry diameter matching"),
			new ImportFieldContract("geometry", "P", "pitch_inches", "in", true,
					"finite-positive", "geometry pitch matching"),
			new ImportFieldContract("geometry", "c/R", "chord_to_radius", "ratio", true,
					"finite-positive", "representative blade chord fit"),
			new ImportFieldContract("geometry", "r/R", "station_radius_ratio", "ratio", true,
					"finite-in-0-to-1", "blade station independent variable"),
			new ImportFieldContract("geometry", "beta", "beta_degrees", "deg", true,
					"finite", "blade pitch-angle fit"),
			new ImportFieldContract("fit_target", "presetName", "preset_name", "text", true,
					"supported-preset", "DroneConfig fit target key"),
			new ImportFieldContract("fit_target", "matchedPropName", "performance_match_id", "text", true,
					"nonblank-reviewed-match", "selected performance curve source"),
			new ImportFieldContract("fit_target", "matchedBladeName", "geometry_match_id", "text", true,
					"reviewed-or-surrogate", "selected geometry curve source"),
			new ImportFieldContract("fit_target", "targetBladeCount", "target_blade_count", "count", true,
					"positive-integer", "RotorSpec blade-count target"),
			new ImportFieldContract("fit_target", "coverageBlocker", "coverage_blocker", "text", true,
					"known-blocker-or-none", "reviewed gap handoff"),
			new ImportFieldContract("fit_target", "nextRequiredAction", "next_required_action", "text", true,
					"known-action", "deterministic fitting queue")
	);

	private static final List<ImportStageContract> IMPORT_STAGES = List.of(
			new ImportStageContract(
					"source_license_review",
					true,
					false,
					true,
					true,
					"review-source-license-before-importing-raw-rows"
			),
			new ImportStageContract(
					"reviewed_raw_row_import",
					true,
					false,
					true,
					true,
					"import-reviewed-raw-rows-into-offline-fitting-pipeline"
			),
			new ImportStageContract(
					"schema_header_validation",
					true,
					false,
					true,
					true,
					"validate-performance-and-geometry-headers"
			),
			new ImportStageContract(
					"unit_normalization",
					true,
					false,
					true,
					true,
					"normalize-diameter-pitch-rpm-and-coefficient-units"
			),
			new ImportStageContract(
					"finite_numeric_validation",
					true,
					false,
					true,
					true,
					"reject-nonfinite-performance-and-geometry-rows"
			),
			new ImportStageContract(
					"preset_coverage_resolution",
					true,
					false,
					false,
					true,
					"resolve-cinewhoop-blade-count-and-heavy-lift-geometry-coverage"
			),
			new ImportStageContract(
					"compact_reference_review",
					true,
					false,
					false,
					true,
					"review-compact-reference-before-playable-handoff"
			),
			new ImportStageContract(
					"runtime_leak_guard",
					true,
					true,
					true,
					true,
					"keep-runtime-coupling-and-gameplay-auto-apply-closed"
			)
	);

	private PropellerArchiveFitImportContract() {
	}

	public record ImportFieldContract(
			String tableName,
			String sourceFieldName,
			String normalizedFieldName,
			String unit,
			boolean required,
			String validationRule,
			String fitUse
	) {
	}

	public record PresetFitInputContract(
			String presetName,
			String performanceMatchId,
			String geometryMatchId,
			int targetBladeCount,
			int matchedBladeCount,
			int performanceSampleRowCount,
			int staticSampleRowCount,
			double advanceRatioMax,
			int geometryStationCount,
			boolean currentImportAllowed,
			boolean performanceSchemaReadyAfterReview,
			boolean bladeCountCoverageReadyAfterReview,
			boolean geometryCoverageReadyAfterReview,
			boolean fitInputReadyAfterReview,
			boolean fitInputReadyInSyntheticTarget,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String postReviewNextRequiredAction
	) {
	}

	public record ImportStageContract(
			String stageName,
			boolean required,
			boolean currentSatisfied,
			boolean reviewedImportSatisfied,
			boolean syntheticTargetSatisfied,
			String nextRequiredAction
	) {
	}

	public record ImportContractSummary(
			int fieldContractCount,
			int presetInputContractCount,
			int importStageCount,
			boolean currentRawImportAllowed,
			boolean reviewedRawImportContractReady,
			boolean reviewedFitInputReady,
			boolean syntheticFitInputReady,
			int postReviewCoverageBlockerPresetCount,
			int runtimeCouplingAllowedPresetCount,
			int gameplayAutoApplyAllowedPresetCount,
			String nextRequiredAction
	) {
	}

	public record FitImportContractAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int fieldContractRowCount,
			int presetInputRowCount,
			int importStageRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<ImportFieldContract> fields,
			List<PresetFitInputContract> presets,
			List<ImportStageContract> stages,
			ImportContractSummary summary
	) {
		public FitImportContractAudit {
			fields = List.copyOf(fields);
			presets = List.copyOf(presets);
			stages = List.copyOf(stages);
		}
	}

	public static FitImportContractAudit audit() {
		List<PresetFitInputContract> presets = PropellerArchiveDatasetTriage.audit()
				.presetMatches()
				.stream()
				.map(PropellerArchiveFitImportContract::preset)
				.toList();
		return new FitImportContractAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				FIELD_CONTRACT_ROW_COUNT,
				PRESET_INPUT_ROW_COUNT,
				IMPORT_STAGE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				FIELD_CONTRACTS,
				presets,
				IMPORT_STAGES,
				summary(presets, IMPORT_STAGES)
		);
	}

	public static ImportFieldContract field(String tableName, String normalizedFieldName) {
		return FIELD_CONTRACTS.stream()
				.filter(field -> field.tableName().equals(tableName)
						&& field.normalizedFieldName().equals(normalizedFieldName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown import field: " + tableName + "." + normalizedFieldName));
	}

	public static PresetFitInputContract preset(String presetName) {
		return PropellerArchiveDatasetTriage.audit()
				.presetMatches()
				.stream()
				.filter(match -> match.presetName().equals(presetName))
				.findFirst()
				.map(PropellerArchiveFitImportContract::preset)
				.orElseThrow(() -> new IllegalArgumentException("unknown preset import contract: " + presetName));
	}

	public static ImportStageContract stage(String stageName) {
		return IMPORT_STAGES.stream()
				.filter(stage -> stage.stageName().equals(stageName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown import stage: " + stageName));
	}

	private static PresetFitInputContract preset(
			PropellerArchiveDatasetTriage.PresetPerformanceMatch match
	) {
		PropellerArchiveDatasetTriage.GeometryStationMatch geometry = geometryOrNull(match.presetName());
		boolean geometryReady = geometry != null;
		boolean bladeReady = match.bladeCountMatches();
		boolean performanceReady = match.sampleRowCount() > 0
				&& match.staticRowCount() >= PropellerArchiveFitReadinessGate.MIN_STATIC_ROW_COUNT_FOR_FIT
				&& match.advanceRatioMax() >= PropellerArchiveFitReadinessGate.MIN_ADVANCE_RATIO_RANGE_FOR_FIT
				&& match.maxEfficiency() >= PropellerArchiveFitReadinessGate.MIN_EFFICIENCY_EVIDENCE_FOR_FIT;
		boolean fitReadyAfterReview = performanceReady && bladeReady && geometryReady;
		String geometryId = geometry == null ? "missing-reviewed-geometry-match" : geometry.matchedBladeName();
		return new PresetFitInputContract(
				match.presetName(),
				match.matchedPropName(),
				geometryId,
				match.targetBladeCount(),
				match.matchedBladeCount(),
				match.sampleRowCount(),
				match.staticRowCount(),
				match.advanceRatioMax(),
				geometry == null ? 0 : geometry.stationCount(),
				false,
				performanceReady,
				bladeReady,
				geometryReady,
				fitReadyAfterReview,
				performanceReady,
				false,
				false,
				postReviewNextRequiredAction(performanceReady, bladeReady, geometryReady)
		);
	}

	private static ImportContractSummary summary(
			List<PresetFitInputContract> presets,
			List<ImportStageContract> stages
	) {
		List<PresetFitInputContract> presetRows = List.copyOf(presets);
		List<ImportStageContract> stageRows = List.copyOf(stages);
		boolean currentRawImport = stageRows.stream()
				.filter(stage -> !"preset_coverage_resolution".equals(stage.stageName())
						&& !"compact_reference_review".equals(stage.stageName()))
				.allMatch(ImportStageContract::currentSatisfied);
		boolean reviewedRawImport = stageRows.stream()
				.filter(stage -> !"preset_coverage_resolution".equals(stage.stageName())
						&& !"compact_reference_review".equals(stage.stageName()))
				.allMatch(ImportStageContract::reviewedImportSatisfied);
		boolean syntheticStagesReady = stageRows.stream()
				.allMatch(ImportStageContract::syntheticTargetSatisfied);
		boolean reviewedFitReady = reviewedRawImport
				&& presetRows.stream().allMatch(PresetFitInputContract::fitInputReadyAfterReview);
		boolean syntheticFitReady = syntheticStagesReady
				&& presetRows.stream().allMatch(PresetFitInputContract::fitInputReadyInSyntheticTarget);
		int coverageBlockers = 0;
		int runtime = 0;
		int gameplay = 0;
		for (PresetFitInputContract preset : presetRows) {
			if (!preset.fitInputReadyAfterReview()) {
				coverageBlockers++;
			}
			if (preset.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (preset.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		String nextAction;
		if (!currentRawImport) {
			nextAction = "review-source-license-before-importing-raw-rows";
		} else if (!reviewedFitReady) {
			nextAction = "resolve-cinewhoop-blade-count-and-heavy-lift-geometry-coverage";
		} else {
			nextAction = "fit-ct-cp-j-re-and-blade-geometry-curves";
		}
		return new ImportContractSummary(
				FIELD_CONTRACTS.size(),
				presetRows.size(),
				stageRows.size(),
				currentRawImport,
				reviewedRawImport,
				reviewedFitReady,
				syntheticFitReady,
				coverageBlockers,
				runtime,
				gameplay,
				nextAction
		);
	}

	private static PropellerArchiveDatasetTriage.GeometryStationMatch geometryOrNull(String presetName) {
		return PropellerArchiveDatasetTriage.geometryMatchOrNull(presetName);
	}

	private static String postReviewNextRequiredAction(
			boolean performanceReady,
			boolean bladeReady,
			boolean geometryReady
	) {
		if (!performanceReady) {
			return "add-reviewed-performance-match-for-preset";
		}
		if (!bladeReady) {
			return "resolve-cinewhoop-three-blade-coverage-or-correction";
		}
		if (!geometryReady) {
			return "add-heavy-lift-geometry-match-or-reviewed-surrogate";
		}
		return "ready-for-reviewed-fit-import";
	}
}
