package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveFitBlockerReport {
	public static final String SOURCE_ID = "User-Propeller-Archive-Fit-Blocker-Report-Packet";
	public static final String CAVEAT =
			"Propeller archive fit blocker report decomposes source, coverage, fitting, handoff, and runtime-leak blockers; it is audit-only and does not fit coefficients or enable gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_ROW_COUNT = PropellerArchiveFitReadinessGate.SCENARIO_SAMPLE_COUNT;
	public static final int PRESET_ROW_COUNT =
			PropellerArchiveFitReadinessGate.SCENARIO_SAMPLE_COUNT
					* PropellerArchiveFitReadinessGate.PRESET_SAMPLE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 12;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SCENARIO_ROW_COUNT
			+ PRESET_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveFitBlockerReport() {
	}

	public record FitBlockerPreset(
			String scenarioName,
			String presetName,
			int blockerCount,
			boolean sourceLicenseReviewBlocker,
			boolean rawRowImportBlocker,
			boolean datasetFitReviewBlocker,
			boolean bladeCountCoverageBlocker,
			boolean geometryCoverageBlocker,
			boolean ctCpFitBlocker,
			boolean geometryFitBlocker,
			boolean simulationFitBlocker,
			boolean playableReferenceReviewBlocker,
			boolean runtimeCouplingLeakBlocker,
			boolean gameplayAutoApplyLeakBlocker,
			boolean simulationFitReady,
			boolean playableReferenceExportAllowed,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record FitBlockerScenarioSummary(
			String scenarioName,
			int blockerCount,
			boolean sourceLicenseReviewBlocker,
			boolean rawRowImportBlocker,
			boolean datasetFitReviewBlocker,
			boolean bladeCountCoverageBlocker,
			boolean geometryCoverageBlocker,
			boolean ctCpFitBlocker,
			boolean geometryFitBlocker,
			boolean simulationFitBlocker,
			boolean playableReferenceReviewBlocker,
			boolean runtimeCouplingLeakBlocker,
			boolean gameplayAutoApplyLeakBlocker,
			int presetCount,
			int blockedPresetCount,
			int simulationFitReadyPresetCount,
			int playableReferenceExportAllowedPresetCount,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record FitBlockerScenario(
			String scenarioName,
			List<FitBlockerPreset> presets,
			FitBlockerScenarioSummary summary
	) {
		public FitBlockerScenario {
			presets = List.copyOf(presets);
		}
	}

	public record FitBlockerExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxScenarioBlockerCount,
			int sourceLicenseReviewBlockerScenarioCount,
			int rawRowImportBlockerScenarioCount,
			int datasetFitReviewBlockerScenarioCount,
			int bladeCoverageBlockerScenarioCount,
			int geometryCoverageBlockerScenarioCount,
			int playableReferenceReviewBlockerPresetCount,
			int runtimeCouplingLeakScenarioCount,
			int gameplayAutoApplyLeakScenarioCount
	) {
	}

	public record FitBlockerAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioRowCount,
			int presetRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<FitBlockerScenario> scenarios,
			FitBlockerExtrema extrema
	) {
		public FitBlockerAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static FitBlockerAudit audit() {
		return audit(PropellerArchiveFitReadinessGate.audit());
	}

	public static FitBlockerAudit audit(PropellerArchiveFitReadinessGate.FitReadinessAudit readinessAudit) {
		if (readinessAudit == null) {
			throw new IllegalArgumentException("readinessAudit must not be null.");
		}
		List<FitBlockerScenario> scenarios = readinessAudit.scenarios().stream()
				.map(PropellerArchiveFitBlockerReport::scenario)
				.toList();
		return new FitBlockerAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_ROW_COUNT,
				PRESET_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				scenarios,
				extrema(scenarios)
		);
	}

	public static FitBlockerScenario scenario(
			PropellerArchiveFitReadinessGate.FitReadinessScenario readinessScenario
	) {
		if (readinessScenario == null) {
			throw new IllegalArgumentException("readiness scenario must not be null.");
		}
		List<FitBlockerPreset> presets = readinessScenario.presets().stream()
				.map(PropellerArchiveFitBlockerReport::preset)
				.toList();
		return new FitBlockerScenario(
				readinessScenario.scenarioName(),
				presets,
				summary(readinessScenario.summary(), presets)
		);
	}

	public static FitBlockerPreset preset(PropellerArchiveFitReadinessGate.PresetFitReadiness readiness) {
		if (readiness == null) {
			throw new IllegalArgumentException("readiness preset must not be null.");
		}
		boolean sourceBlocker = !readiness.sourceLicenseReviewed();
		boolean rawImportBlocker = !readiness.rawRowsImported();
		boolean datasetReviewBlocker = !readiness.datasetFitReviewed();
		boolean bladeBlocker = !readiness.bladeCountReady();
		boolean geometryBlocker = !readiness.geometryMatchReady();
		boolean ctCpBlocker = !readiness.ctCpFitReady();
		boolean geometryFitBlocker = !readiness.geometryFitReady();
		boolean simulationBlocker = !readiness.simulationFitReady();
		boolean playableBlocker = readiness.simulationFitReady()
				&& !readiness.playableReferenceExportAllowed();
		boolean runtimeLeak = readiness.runtimeCouplingAllowed();
		boolean gameplayLeak = readiness.gameplayAutoApplyAllowed();
		int blockerCount = countTrue(
				sourceBlocker,
				rawImportBlocker,
				datasetReviewBlocker,
				bladeBlocker,
				geometryBlocker,
				ctCpBlocker,
				geometryFitBlocker,
				simulationBlocker,
				playableBlocker,
				runtimeLeak,
				gameplayLeak);
		boolean ready = blockerCount == 0;
		return new FitBlockerPreset(
				readiness.scenarioName(),
				readiness.presetName(),
				blockerCount,
				sourceBlocker,
				rawImportBlocker,
				datasetReviewBlocker,
				bladeBlocker,
				geometryBlocker,
				ctCpBlocker,
				geometryFitBlocker,
				simulationBlocker,
				playableBlocker,
				runtimeLeak,
				gameplayLeak,
				readiness.simulationFitReady(),
				readiness.playableReferenceExportAllowed(),
				nextRequiredAction(
						sourceBlocker,
						rawImportBlocker,
						datasetReviewBlocker,
						bladeBlocker,
						geometryBlocker,
						ctCpBlocker,
						geometryFitBlocker,
						simulationBlocker,
						playableBlocker,
						runtimeLeak,
						gameplayLeak),
				ready ? "READY" : "BLOCKED",
				ready ? "preset-fit-blockers-clear" : "preset-fit-blockers-open"
		);
	}

	private static FitBlockerScenarioSummary summary(
			PropellerArchiveFitReadinessGate.FitReadinessSummary readiness,
			List<FitBlockerPreset> presets
	) {
		if (readiness == null) {
			throw new IllegalArgumentException("readiness summary must not be null.");
		}
		boolean sourceBlocker = !readiness.sourceBoundaryOpen()
				&& presets.stream().anyMatch(FitBlockerPreset::sourceLicenseReviewBlocker);
		boolean rawBlocker = !readiness.sourceBoundaryOpen()
				&& presets.stream().anyMatch(FitBlockerPreset::rawRowImportBlocker);
		boolean datasetBlocker = !readiness.sourceBoundaryOpen()
				&& presets.stream().anyMatch(FitBlockerPreset::datasetFitReviewBlocker);
		boolean bladeBlocker = presets.stream().anyMatch(FitBlockerPreset::bladeCountCoverageBlocker);
		boolean geometryBlocker = presets.stream().anyMatch(FitBlockerPreset::geometryCoverageBlocker);
		boolean ctCpBlocker = !readiness.coefficientFitReady();
		boolean geometryFitBlocker = !readiness.geometryFitReady();
		boolean simulationBlocker = !readiness.simulationFitReady();
		boolean playableBlocker = readiness.simulationFitReady()
				&& !readiness.playableReferenceHandoffReady();
		boolean runtimeLeak = readiness.runtimeCouplingAllowedCount() > 0;
		boolean gameplayLeak = readiness.gameplayAutoApplyAllowedCount() > 0;
		int blockerCount = countTrue(
				sourceBlocker,
				rawBlocker,
				datasetBlocker,
				bladeBlocker,
				geometryBlocker,
				ctCpBlocker,
				geometryFitBlocker,
				simulationBlocker,
				playableBlocker,
				runtimeLeak,
				gameplayLeak);
		int blockedPresets = 0;
		int simulationReady = 0;
		int playableAllowed = 0;
		for (FitBlockerPreset preset : presets) {
			if (preset.blockerCount() > 0) {
				blockedPresets++;
			}
			if (preset.simulationFitReady()) {
				simulationReady++;
			}
			if (preset.playableReferenceExportAllowed()) {
				playableAllowed++;
			}
		}
		boolean ready = blockerCount == 0;
		return new FitBlockerScenarioSummary(
				readiness.scenarioName(),
				blockerCount,
				sourceBlocker,
				rawBlocker,
				datasetBlocker,
				bladeBlocker,
				geometryBlocker,
				ctCpBlocker,
				geometryFitBlocker,
				simulationBlocker,
				playableBlocker,
				runtimeLeak,
				gameplayLeak,
				presets.size(),
				blockedPresets,
				simulationReady,
				playableAllowed,
				nextRequiredAction(
						sourceBlocker,
						rawBlocker,
						datasetBlocker,
						bladeBlocker,
						geometryBlocker,
						ctCpBlocker,
						geometryFitBlocker,
						simulationBlocker,
						playableBlocker,
						runtimeLeak,
						gameplayLeak),
				ready ? "READY" : "BLOCKED",
				ready ? "fit-readiness-blockers-clear" : "fit-readiness-blockers-open"
		);
	}

	private static FitBlockerExtrema extrema(List<FitBlockerScenario> scenarios) {
		int ready = 0;
		int maxBlockers = 0;
		int source = 0;
		int raw = 0;
		int dataset = 0;
		int blade = 0;
		int geometry = 0;
		int playablePreset = 0;
		int runtimeLeak = 0;
		int gameplayLeak = 0;
		for (FitBlockerScenario scenario : scenarios) {
			FitBlockerScenarioSummary summary = scenario.summary();
			if ("READY".equals(summary.status())) {
				ready++;
			}
			maxBlockers = Math.max(maxBlockers, summary.blockerCount());
			if (summary.sourceLicenseReviewBlocker()) {
				source++;
			}
			if (summary.rawRowImportBlocker()) {
				raw++;
			}
			if (summary.datasetFitReviewBlocker()) {
				dataset++;
			}
			if (summary.bladeCountCoverageBlocker()) {
				blade++;
			}
			if (summary.geometryCoverageBlocker()) {
				geometry++;
			}
			if (summary.runtimeCouplingLeakBlocker()) {
				runtimeLeak++;
			}
			if (summary.gameplayAutoApplyLeakBlocker()) {
				gameplayLeak++;
			}
			for (FitBlockerPreset preset : scenario.presets()) {
				if (preset.playableReferenceReviewBlocker()) {
					playablePreset++;
				}
			}
		}
		return new FitBlockerExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxBlockers,
				source,
				raw,
				dataset,
				blade,
				geometry,
				playablePreset,
				runtimeLeak,
				gameplayLeak
		);
	}

	private static String nextRequiredAction(
			boolean sourceBlocker,
			boolean rawImportBlocker,
			boolean datasetReviewBlocker,
			boolean bladeBlocker,
			boolean geometryBlocker,
			boolean ctCpBlocker,
			boolean geometryFitBlocker,
			boolean simulationBlocker,
			boolean playableBlocker,
			boolean runtimeLeak,
			boolean gameplayLeak
	) {
		if (sourceBlocker) {
			return "review-source-license-before-importing-raw-rows";
		}
		if (rawImportBlocker) {
			return "import-reviewed-raw-rows-into-offline-fitting-pipeline";
		}
		if (datasetReviewBlocker) {
			return "review-archive-schema-and-fitting-method-before-curve-fit";
		}
		if (bladeBlocker) {
			return "resolve-cinewhoop-three-blade-coverage-or-correction";
		}
		if (geometryBlocker) {
			return "add-heavy-lift-geometry-match-or-reviewed-surrogate";
		}
		if (ctCpBlocker) {
			return "repair-ct-cp-fit-readiness";
		}
		if (geometryFitBlocker) {
			return "repair-geometry-fit-readiness";
		}
		if (simulationBlocker) {
			return "repair-simulation-fit-readiness";
		}
		if (playableBlocker) {
			return "review-compact-reference-before-playable-handoff";
		}
		if (runtimeLeak) {
			return "close-runtime-coupling-before-fit-report";
		}
		if (gameplayLeak) {
			return "close-gameplay-auto-apply-before-fit-report";
		}
		return "fit-readiness-blockers-clear";
	}

	private static int countTrue(boolean... values) {
		int count = 0;
		for (boolean value : values) {
			if (value) {
				count++;
			}
		}
		return count;
	}
}
