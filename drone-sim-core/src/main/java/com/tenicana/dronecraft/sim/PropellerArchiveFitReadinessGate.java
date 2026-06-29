package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveFitReadinessGate {
	public static final String SOURCE_ID = "User-Propeller-Archive-Fit-Readiness-Gate-Packet";
	public static final String CAVEAT =
			"Propeller archive fitting stays blocked until source/license review, reviewed raw-row import, preset coverage gaps, and downstream reference review are explicit; it never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 3;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int PRESET_ROW_COUNT = SCENARIO_SAMPLE_COUNT * PRESET_SAMPLE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 9;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT
			+ PRESET_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final int MIN_STATIC_ROW_COUNT_FOR_FIT = 10;
	public static final double MIN_ADVANCE_RATIO_RANGE_FOR_FIT = 0.6;
	public static final double MIN_EFFICIENCY_EVIDENCE_FOR_FIT = 0.5;

	private PropellerArchiveFitReadinessGate() {
	}

	public record PresetFitReadiness(
			String scenarioName,
			String presetName,
			boolean sourceLicenseReviewed,
			boolean rawRowsImported,
			boolean datasetFitReviewed,
			boolean coverageGapResolved,
			boolean performanceMatchPresent,
			boolean bladeCountReady,
			boolean geometryMatchReady,
			boolean staticCoverageReady,
			boolean advanceCoverageReady,
			boolean efficiencyEvidenceReady,
			boolean ctCpFitReady,
			boolean geometryFitReady,
			boolean simulationFitReady,
			boolean playableReferenceExportAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String dominantBlocker,
			String nextRequiredAction
	) {
	}

	public record FitReadinessSummary(
			String scenarioName,
			int presetCount,
			int performanceMatchCount,
			int bladeCountReadyCount,
			int geometryMatchReadyCount,
			int staticCoverageReadyCount,
			int advanceCoverageReadyCount,
			int efficiencyEvidenceReadyCount,
			int ctCpFitReadyCount,
			int geometryFitReadyCount,
			int simulationFitReadyCount,
			int playableReferenceExportAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			boolean sourceBoundaryOpen,
			boolean allExpectedPresetsPresent,
			boolean coefficientFitReady,
			boolean geometryFitReady,
			boolean simulationFitReady,
			boolean playableReferenceHandoffReady,
			String dominantBlocker,
			String nextRequiredAction,
			String reviewMode
	) {
	}

	public record FitReadinessScenario(
			String scenarioName,
			List<PresetFitReadiness> presets,
			FitReadinessSummary summary
	) {
		public FitReadinessScenario {
			presets = List.copyOf(presets);
		}
	}

	public record FitReadinessExtrema(
			int scenarioCount,
			int presetScenarioRowCount,
			int simulationFitReadyScenarioCount,
			int playableReferenceHandoffReadyScenarioCount,
			int maxCtCpFitReadyCount,
			int maxGeometryFitReadyCount,
			int maxSimulationFitReadyCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record FitReadinessAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int presetSampleCount,
			int presetRowCount,
			int summaryRowCount,
			int methodRowCount,
			int minStaticRowCountForFit,
			double minAdvanceRatioRangeForFit,
			double minEfficiencyEvidenceForFit,
			List<FitReadinessScenario> scenarios,
			FitReadinessExtrema extrema
	) {
		public FitReadinessAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static FitReadinessAudit audit() {
		List<FitReadinessScenario> scenarios = List.of(
				scenario(
						"current_triage_blocked",
						false,
						false,
						false,
						false,
						false,
						"current-non-vendored-triage"
				),
				scenario(
						"reviewed_imported_with_coverage_gaps",
						true,
						true,
						true,
						false,
						false,
						"reviewed-raw-rows-but-preset-gaps-remain"
				),
				scenario(
						"synthetic_full_reviewed_fit_target",
						true,
						true,
						true,
						true,
						true,
						"synthetic-reviewed-coverage-complete"
				)
		);
		return new FitReadinessAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				PRESET_SAMPLE_COUNT,
				PRESET_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				MIN_STATIC_ROW_COUNT_FOR_FIT,
				MIN_ADVANCE_RATIO_RANGE_FOR_FIT,
				MIN_EFFICIENCY_EVIDENCE_FOR_FIT,
				scenarios,
				extrema(scenarios)
		);
	}

	public static FitReadinessScenario scenario(
			String scenarioName,
			boolean sourceLicenseReviewed,
			boolean rawRowsImported,
			boolean datasetFitReviewed,
			boolean coverageGapResolved,
			boolean playableReferenceReviewed,
			String reviewMode
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (reviewMode == null || reviewMode.isBlank()) {
			throw new IllegalArgumentException("reviewMode must not be blank.");
		}
		List<PresetFitReadiness> presets = PropellerArchiveDatasetTriage.audit()
				.presetMatches()
				.stream()
				.map(match -> preset(
						scenarioName,
						match,
						sourceLicenseReviewed,
						rawRowsImported,
						datasetFitReviewed,
						coverageGapResolved,
						playableReferenceReviewed
				))
				.toList();
		return new FitReadinessScenario(
				scenarioName,
				presets,
				summary(
						scenarioName,
						presets,
						sourceLicenseReviewed,
						rawRowsImported,
						datasetFitReviewed,
						reviewMode
				)
		);
	}

	public static PresetFitReadiness preset(
			String scenarioName,
			PropellerArchiveDatasetTriage.PresetPerformanceMatch match,
			boolean sourceLicenseReviewed,
			boolean rawRowsImported,
			boolean datasetFitReviewed,
			boolean coverageGapResolved,
			boolean playableReferenceReviewed
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (match == null) {
			throw new IllegalArgumentException("performance match is required.");
		}
		boolean sourceReady = sourceLicenseReviewed && rawRowsImported && datasetFitReviewed;
		boolean performancePresent = match.sampleRowCount() > 0;
		boolean bladeReady = match.bladeCountMatches() || coverageGapResolved;
		boolean geometryReady = geometryAvailable(match.presetName()) || coverageGapResolved;
		boolean staticReady = match.staticRowCount() >= MIN_STATIC_ROW_COUNT_FOR_FIT;
		boolean advanceReady = match.advanceRatioMax() >= MIN_ADVANCE_RATIO_RANGE_FOR_FIT;
		boolean efficiencyReady = match.maxEfficiency() >= MIN_EFFICIENCY_EVIDENCE_FOR_FIT;
		boolean ctCpReady = sourceReady
				&& performancePresent
				&& bladeReady
				&& staticReady
				&& advanceReady
				&& efficiencyReady;
		boolean geometryFitReady = sourceReady && geometryReady;
		boolean simulationFitReady = ctCpReady && geometryFitReady;
		boolean playableReferenceAllowed = simulationFitReady && playableReferenceReviewed;
		String blocker = blocker(
				sourceLicenseReviewed,
				rawRowsImported,
				datasetFitReviewed,
				performancePresent,
				bladeReady,
				staticReady,
				advanceReady,
				efficiencyReady,
				geometryReady,
				playableReferenceAllowed,
				playableReferenceReviewed,
				simulationFitReady
		);
		return new PresetFitReadiness(
				scenarioName,
				match.presetName(),
				sourceLicenseReviewed,
				rawRowsImported,
				datasetFitReviewed,
				coverageGapResolved,
				performancePresent,
				bladeReady,
				geometryReady,
				staticReady,
				advanceReady,
				efficiencyReady,
				ctCpReady,
				geometryFitReady,
				simulationFitReady,
				playableReferenceAllowed,
				false,
				false,
				blocker,
				nextRequiredAction(blocker)
		);
	}

	private static FitReadinessSummary summary(
			String scenarioName,
			List<PresetFitReadiness> presets,
			boolean sourceLicenseReviewed,
			boolean rawRowsImported,
			boolean datasetFitReviewed,
			String reviewMode
	) {
		int performance = 0;
		int blade = 0;
		int geometryMatch = 0;
		int staticCoverage = 0;
		int advance = 0;
		int efficiency = 0;
		int ctCp = 0;
		int geometryFit = 0;
		int simulation = 0;
		int playable = 0;
		int runtime = 0;
		int gameplay = 0;
		for (PresetFitReadiness preset : presets) {
			if (preset.performanceMatchPresent()) {
				performance++;
			}
			if (preset.bladeCountReady()) {
				blade++;
			}
			if (preset.geometryMatchReady()) {
				geometryMatch++;
			}
			if (preset.staticCoverageReady()) {
				staticCoverage++;
			}
			if (preset.advanceCoverageReady()) {
				advance++;
			}
			if (preset.efficiencyEvidenceReady()) {
				efficiency++;
			}
			if (preset.ctCpFitReady()) {
				ctCp++;
			}
			if (preset.geometryFitReady()) {
				geometryFit++;
			}
			if (preset.simulationFitReady()) {
				simulation++;
			}
			if (preset.playableReferenceExportAllowed()) {
				playable++;
			}
			if (preset.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (preset.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		boolean sourceOpen = sourceLicenseReviewed && rawRowsImported && datasetFitReviewed;
		boolean allPresets = presets.size() == PRESET_SAMPLE_COUNT;
		boolean coefficientReady = allPresets && ctCp == PRESET_SAMPLE_COUNT;
		boolean geometryReady = allPresets && geometryFit == PRESET_SAMPLE_COUNT;
		boolean simulationReady = allPresets && simulation == PRESET_SAMPLE_COUNT;
		boolean playableReady = allPresets && playable == PRESET_SAMPLE_COUNT;
		String blocker = summaryBlocker(
				sourceLicenseReviewed,
				rawRowsImported,
				datasetFitReviewed,
				simulationReady,
				playableReady
		);
		return new FitReadinessSummary(
				scenarioName,
				presets.size(),
				performance,
				blade,
				geometryMatch,
				staticCoverage,
				advance,
				efficiency,
				ctCp,
				geometryFit,
				simulation,
				playable,
				runtime,
				gameplay,
				sourceOpen,
				allPresets,
				coefficientReady,
				geometryReady,
				simulationReady,
				playableReady,
				blocker,
				nextRequiredAction(blocker),
				reviewMode
		);
	}

	private static FitReadinessExtrema extrema(List<FitReadinessScenario> scenarios) {
		int simulationReadyScenarios = 0;
		int playableReadyScenarios = 0;
		int maxCtCp = 0;
		int maxGeometry = 0;
		int maxSimulation = 0;
		int runtimeAllowedScenarios = 0;
		int gameplayAllowedScenarios = 0;
		for (FitReadinessScenario scenario : scenarios) {
			FitReadinessSummary summary = scenario.summary();
			if (summary.simulationFitReady()) {
				simulationReadyScenarios++;
			}
			if (summary.playableReferenceHandoffReady()) {
				playableReadyScenarios++;
			}
			maxCtCp = Math.max(maxCtCp, summary.ctCpFitReadyCount());
			maxGeometry = Math.max(maxGeometry, summary.geometryFitReadyCount());
			maxSimulation = Math.max(maxSimulation, summary.simulationFitReadyCount());
			if (summary.runtimeCouplingAllowedCount() > 0) {
				runtimeAllowedScenarios++;
			}
			if (summary.gameplayAutoApplyAllowedCount() > 0) {
				gameplayAllowedScenarios++;
			}
		}
		return new FitReadinessExtrema(
				scenarios.size(),
				scenarios.size() * PRESET_SAMPLE_COUNT,
				simulationReadyScenarios,
				playableReadyScenarios,
				maxCtCp,
				maxGeometry,
				maxSimulation,
				runtimeAllowedScenarios,
				gameplayAllowedScenarios
		);
	}

	private static boolean geometryAvailable(String presetName) {
		try {
			PropellerArchiveDatasetTriage.geometryMatch(presetName);
			return true;
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private static String summaryBlocker(
			boolean sourceLicenseReviewed,
			boolean rawRowsImported,
			boolean datasetFitReviewed,
			boolean simulationReady,
			boolean playableReady
	) {
		if (!sourceLicenseReviewed) {
			return "source-license-review-required";
		}
		if (!rawRowsImported) {
			return "reviewed-raw-row-import-required";
		}
		if (!datasetFitReviewed) {
			return "dataset-fit-review-required";
		}
		if (!simulationReady) {
			return "preset-coverage-gaps";
		}
		if (!playableReady) {
			return "playable-reference-review-required";
		}
		return "ready-for-reviewed-sim-fit-packet";
	}

	private static String blocker(
			boolean sourceLicenseReviewed,
			boolean rawRowsImported,
			boolean datasetFitReviewed,
			boolean performancePresent,
			boolean bladeReady,
			boolean staticReady,
			boolean advanceReady,
			boolean efficiencyReady,
			boolean geometryReady,
			boolean playableReferenceAllowed,
			boolean playableReferenceReviewed,
			boolean simulationFitReady
	) {
		if (!sourceLicenseReviewed) {
			return "source-license-review-required";
		}
		if (!rawRowsImported) {
			return "reviewed-raw-row-import-required";
		}
		if (!datasetFitReviewed) {
			return "dataset-fit-review-required";
		}
		if (!performancePresent) {
			return "performance-match-missing";
		}
		if (!bladeReady) {
			return "blade-count-mismatch-review-required";
		}
		if (!staticReady) {
			return "static-coverage-insufficient";
		}
		if (!advanceReady) {
			return "advance-coverage-insufficient";
		}
		if (!efficiencyReady) {
			return "efficiency-evidence-insufficient";
		}
		if (!geometryReady) {
			return "geometry-match-missing";
		}
		if (simulationFitReady && !playableReferenceReviewed && !playableReferenceAllowed) {
			return "playable-reference-review-required";
		}
		return "none";
	}

	private static String nextRequiredAction(String blocker) {
		return switch (blocker) {
			case "source-license-review-required" -> "review-source-license-before-importing-raw-rows";
			case "reviewed-raw-row-import-required" -> "import-reviewed-raw-rows-into-offline-fitting-pipeline";
			case "dataset-fit-review-required" -> "review-archive-schema-and-fitting-method-before-curve-fit";
			case "performance-match-missing" -> "add-reviewed-performance-match-for-preset";
			case "blade-count-mismatch-review-required" -> "resolve-cinewhoop-three-blade-coverage-or-correction";
			case "static-coverage-insufficient" -> "add-static-low-j-performance-coverage";
			case "advance-coverage-insufficient" -> "add-advance-ratio-coverage-before-ct-cp-fit";
			case "efficiency-evidence-insufficient" -> "add-efficiency-evidence-before-power-fit";
			case "geometry-match-missing" -> "add-heavy-lift-geometry-match-or-reviewed-surrogate";
			case "preset-coverage-gaps" -> "resolve-cinewhoop-blade-count-and-heavy-lift-geometry-coverage";
			case "playable-reference-review-required" -> "review-compact-reference-before-playable-handoff";
			case "ready-for-reviewed-sim-fit-packet" -> "fit-ct-cp-j-re-and-blade-geometry-curves";
			case "none" -> "no-preset-blocker";
			default -> "inspect-fit-readiness-blocker";
		};
	}
}
