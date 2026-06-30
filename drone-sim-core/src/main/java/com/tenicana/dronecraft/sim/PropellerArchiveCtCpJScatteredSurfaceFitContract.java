package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PropellerArchiveCtCpJScatteredSurfaceFitContract {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Scattered-Surface-Fit-Contract-Packet";
	public static final String CAVEAT =
			"Scattered CT/CP/J surface fit contract converts archive sparse RPM-track topology into reviewed lookup-ready surface rows; it vendors no raw archive rows and cannot mutate runtime physics or gameplay tuning.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int FIT_FIELD_ROW_COUNT = 15;
	public static final int TARGET_ROW_COUNT = 9;
	public static final int SCENARIO_SAMPLE_COUNT = 5;
	public static final int SUMMARY_ROW_COUNT = 14;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ FIT_FIELD_ROW_COUNT
			+ TARGET_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final double MAX_STATIC_ANCHOR_RESIDUAL = 0.005;
	public static final double MAX_CT_HOLDOUT_RESIDUAL = 0.06;
	public static final double MAX_CP_HOLDOUT_RESIDUAL = 0.08;
	public static final double MAX_ETA_CONSISTENCY_RESIDUAL = 0.04;
	public static final String NEXT_REQUIRED_ACTION =
			"fit-scattered-ct-cp-j-surface-before-direct-lookup-binding";

	private static final List<ScatteredSurfaceFitField> FIELDS = List.of(
			new ScatteredSurfaceFitField("preset_name", "text", true,
					"lookup target key", "join fit row to DroneConfig preset", false, false),
			new ScatteredSurfaceFitField("case_name", "text", true,
					"lookup query case", "join fit row to query envelope", false, false),
			new ScatteredSurfaceFitField("performance_match_id", "text", true,
					"reviewed propeller archive match", "trace sparse CT/CP/J source", false, false),
			new ScatteredSurfaceFitField("source_archive_sha256", "sha256", true,
					"PropellerArchiveSourceFingerprint archive hash", "prove source identity", false, false),
			new ScatteredSurfaceFitField("query_advance_ratio_j", "J", true,
					"lookup query coordinate", "evaluate fitted surface at query J", false, false),
			new ScatteredSurfaceFitField("query_rpm", "rpm", true,
					"lookup query coordinate", "evaluate fitted surface at query RPM", false, false),
			new ScatteredSurfaceFitField("direct_neighbor_binding_ready", "boolean", true,
					"archive grid coverage", "allow reviewed static-anchor rows without scattered fit", false, false),
			new ScatteredSurfaceFitField("scattered_fit_required", "boolean", true,
					"archive grid coverage", "route sparse nonstatic rows through surface fit", false, false),
			new ScatteredSurfaceFitField("available_rectangular_neighbor_rows", "count", true,
					"archive grid coverage", "prove direct rectangular lookup is insufficient", false, false),
			new ScatteredSurfaceFitField("available_nonstatic_neighbor_rows", "count", true,
					"archive grid coverage", "prove nonstatic support around the query", false, false),
			new ScatteredSurfaceFitField("static_anchor_residual", "ratio", true,
					"reviewed J-zero fit replay", "preserve hover/static CT/CP anchor", false, false),
			new ScatteredSurfaceFitField("ct_holdout_residual", "ratio", true,
					"reviewed sparse surface validation", "bound thrust-coefficient fit error", false, false),
			new ScatteredSurfaceFitField("cp_holdout_residual", "ratio", true,
					"reviewed sparse surface validation", "bound power-coefficient fit error", false, false),
			new ScatteredSurfaceFitField("eta_consistency_residual", "ratio", true,
					"reviewed sparse surface validation", "reject CT/CP/eta inconsistency", false, false),
			new ScatteredSurfaceFitField("ct_shape_preserved", "boolean", true,
					"reviewed sparse surface validation", "reject unphysical CT overshoot", false, false)
	);

	private PropellerArchiveCtCpJScatteredSurfaceFitContract() {
	}

	public record ScatteredSurfaceFitField(
			String fieldName,
			String unit,
			boolean required,
			String source,
			String downstreamUse,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed
	) {
	}

	public record ScatteredSurfaceFitTarget(
			String presetName,
			String caseName,
			String performanceMatchId,
			double queryAdvanceRatioJ,
			double queryRpm,
			int minimumPerformanceNeighborRows,
			int availableRectangularNeighborRows,
			int availableNonstaticNeighborRows,
			boolean directNeighborBindingReady,
			boolean scatteredFitRequired,
			boolean postReviewFullSimulationLookupAllowed,
			String nextRequiredAction
	) {
	}

	public record ScatteredSurfaceFitResult(
			String presetName,
			String caseName,
			boolean sourceRowsReviewed,
			boolean scatteredSurfaceFitExecuted,
			boolean surfaceEvidenceReady,
			double staticAnchorResidual,
			double ctHoldoutResidual,
			double cpHoldoutResidual,
			double etaConsistencyResidual,
			boolean positiveCpPreserved,
			boolean ctShapePreserved,
			boolean passed,
			String status
	) {
	}

	public record ScatteredSurfaceFitSummary(
			boolean sourceRowsReviewed,
			boolean archiveGridCoverageReady,
			boolean scatteredSurfaceFitRun,
			int expectedTargetCount,
			int directNeighborTargetCount,
			int scatteredFitRequiredTargetCount,
			int observedResultCount,
			int missingResultCount,
			int passedResultCount,
			int failedResultCount,
			int readyFullSimulationTargetCount,
			int readyPerformanceOnlyTargetCount,
			double maxStaticAnchorResidual,
			double maxCtHoldoutResidual,
			double maxCpHoldoutResidual,
			double maxEtaConsistencyResidual,
			boolean scatteredSurfaceFitContractReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record ScatteredSurfaceFitScenario(
			String scenarioName,
			ScatteredSurfaceFitSummary summary
	) {
	}

	public record ScatteredSurfaceFitExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxObservedResultCount,
			int maxMissingResultCount,
			int maxFailedResultCount,
			int maxReadyFullSimulationTargetCount,
			int maxReadyPerformanceOnlyTargetCount,
			double maxStaticAnchorResidual,
			double maxCtHoldoutResidual,
			double maxCpHoldoutResidual,
			double maxEtaConsistencyResidual,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJScatteredSurfaceFitContractAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int fitFieldRowCount,
			int targetRowCount,
			int scenarioSampleCount,
			int summaryRowCount,
			int methodRowCount,
			List<ScatteredSurfaceFitField> fields,
			List<ScatteredSurfaceFitTarget> targets,
			List<ScatteredSurfaceFitScenario> scenarios,
			ScatteredSurfaceFitExtrema extrema
	) {
		public CtCpJScatteredSurfaceFitContractAudit {
			fields = List.copyOf(fields);
			targets = List.copyOf(targets);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJScatteredSurfaceFitContractAudit audit() {
		List<ScatteredSurfaceFitTarget> targets = targets();
		List<ScatteredSurfaceFitResult> staticOnly = targets.stream()
				.filter(ScatteredSurfaceFitTarget::directNeighborBindingReady)
				.map(PropellerArchiveCtCpJScatteredSurfaceFitContract::passingDirectResult)
				.toList();
		List<ScatteredSurfaceFitResult> passing = targets.stream()
				.map(PropellerArchiveCtCpJScatteredSurfaceFitContract::passingResult)
				.toList();
		List<ScatteredSurfaceFitResult> failed = new ArrayList<>(passing);
		failed.set(1, failingResult(targets.get(1)));
		List<ScatteredSurfaceFitScenario> scenarios = List.of(
				new ScatteredSurfaceFitScenario(
						"current_source_review_blocked_no_surface_fit",
						review(false, true, false, targets, List.of(),
								"current-scattered-surface-fit-source-blocked")),
				new ScatteredSurfaceFitScenario(
						"reviewed_static_neighbors_only_nonstatic_missing",
						review(true, true, false, targets, staticOnly,
								"synthetic-static-neighbor-binding-nonstatic-missing")),
				new ScatteredSurfaceFitScenario(
						"reviewed_surface_fit_results_missing",
						review(true, true, true, targets, List.of(),
								"synthetic-scattered-surface-fit-results-missing")),
				new ScatteredSurfaceFitScenario(
						"reviewed_surface_fit_one_failed",
						review(true, true, true, targets, failed,
								"synthetic-scattered-surface-fit-one-failed")),
				new ScatteredSurfaceFitScenario(
						"reviewed_surface_fit_all_pass",
						review(true, true, true, targets, passing,
								"synthetic-scattered-surface-fit-all-pass"))
		);
		return new CtCpJScatteredSurfaceFitContractAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				FIT_FIELD_ROW_COUNT,
				TARGET_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				FIELDS,
				targets,
				scenarios,
				extrema(scenarios)
		);
	}

	public static List<ScatteredSurfaceFitTarget> targets() {
		return PropellerArchiveCtCpJArchiveLookupGridCoverage.audit()
				.rows()
				.stream()
				.filter(row -> row.insideLookupDomain() && row.postReviewCtCpJLookupAllowed())
				.map(PropellerArchiveCtCpJScatteredSurfaceFitContract::target)
				.toList();
	}

	public static ScatteredSurfaceFitField field(String fieldName) {
		return FIELDS.stream()
				.filter(field -> field.fieldName().equals(fieldName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown scattered CT/CP/J surface fit field: " + fieldName));
	}

	public static ScatteredSurfaceFitTarget target(String presetName, String caseName) {
		return targets().stream()
				.filter(target -> target.presetName().equals(presetName) && target.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown scattered CT/CP/J surface fit target: " + presetName + " / " + caseName));
	}

	public static ScatteredSurfaceFitResult result(
			ScatteredSurfaceFitTarget target,
			boolean sourceRowsReviewed,
			boolean scatteredSurfaceFitExecuted,
			double staticAnchorResidual,
			double ctHoldoutResidual,
			double cpHoldoutResidual,
			double etaConsistencyResidual,
			boolean positiveCpPreserved,
			boolean ctShapePreserved
	) {
		if (target == null) {
			throw new IllegalArgumentException("scattered surface fit target must not be null.");
		}
		validateResidual("staticAnchorResidual", staticAnchorResidual);
		validateResidual("ctHoldoutResidual", ctHoldoutResidual);
		validateResidual("cpHoldoutResidual", cpHoldoutResidual);
		validateResidual("etaConsistencyResidual", etaConsistencyResidual);
		boolean evidenceReady = target.directNeighborBindingReady()
				|| (target.scatteredFitRequired() && scatteredSurfaceFitExecuted);
		boolean passed = sourceRowsReviewed
				&& evidenceReady
				&& staticAnchorResidual <= MAX_STATIC_ANCHOR_RESIDUAL
				&& ctHoldoutResidual <= MAX_CT_HOLDOUT_RESIDUAL
				&& cpHoldoutResidual <= MAX_CP_HOLDOUT_RESIDUAL
				&& etaConsistencyResidual <= MAX_ETA_CONSISTENCY_RESIDUAL
				&& positiveCpPreserved
				&& ctShapePreserved;
		return new ScatteredSurfaceFitResult(
				target.presetName(),
				target.caseName(),
				sourceRowsReviewed,
				scatteredSurfaceFitExecuted,
				evidenceReady,
				staticAnchorResidual,
				ctHoldoutResidual,
				cpHoldoutResidual,
				etaConsistencyResidual,
				positiveCpPreserved,
				ctShapePreserved,
				passed,
				passed ? "PASS" : "FAIL"
		);
	}

	public static ScatteredSurfaceFitSummary review(
			boolean sourceRowsReviewed,
			boolean archiveGridCoverageReady,
			boolean scatteredSurfaceFitRun,
			List<ScatteredSurfaceFitTarget> targets,
			List<ScatteredSurfaceFitResult> results,
			String sourceRuntimeInfo
	) {
		if (targets == null) {
			throw new IllegalArgumentException("targets must not be null.");
		}
		if (results == null) {
			throw new IllegalArgumentException("results must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		Set<String> expected = new HashSet<>();
		int direct = 0;
		int scattered = 0;
		for (ScatteredSurfaceFitTarget target : targets) {
			expected.add(key(target.presetName(), target.caseName()));
			if (target.directNeighborBindingReady()) {
				direct++;
			}
			if (target.scatteredFitRequired()) {
				scattered++;
			}
		}
		Set<String> observed = new HashSet<>();
		for (ScatteredSurfaceFitResult result : results) {
			String resultKey = key(result.presetName(), result.caseName());
			if (!expected.contains(resultKey)) {
				throw new IllegalArgumentException("unexpected scattered surface fit result: "
						+ result.presetName() + " / " + result.caseName());
			}
			if (!observed.add(resultKey)) {
				throw new IllegalArgumentException("duplicate scattered surface fit result: "
						+ result.presetName() + " / " + result.caseName());
			}
		}
		int missing = 0;
		int passed = 0;
		int failed = 0;
		int fullReady = 0;
		int performanceOnlyReady = 0;
		double maxStatic = 0.0;
		double maxCt = 0.0;
		double maxCp = 0.0;
		double maxEta = 0.0;
		for (ScatteredSurfaceFitTarget target : targets) {
			ScatteredSurfaceFitResult result = findResult(results, target.presetName(), target.caseName());
			if (result == null) {
				missing++;
				continue;
			}
			maxStatic = Math.max(maxStatic, result.staticAnchorResidual());
			maxCt = Math.max(maxCt, result.ctHoldoutResidual());
			maxCp = Math.max(maxCp, result.cpHoldoutResidual());
			maxEta = Math.max(maxEta, result.etaConsistencyResidual());
			if (result.passed()) {
				passed++;
				if (target.postReviewFullSimulationLookupAllowed()) {
					fullReady++;
				} else {
					performanceOnlyReady++;
				}
			} else {
				failed++;
			}
		}
		boolean ready = sourceRowsReviewed
				&& archiveGridCoverageReady
				&& scatteredSurfaceFitRun
				&& missing == 0
				&& failed == 0
				&& passed == targets.size();
		return new ScatteredSurfaceFitSummary(
				sourceRowsReviewed,
				archiveGridCoverageReady,
				scatteredSurfaceFitRun,
				targets.size(),
				direct,
				scattered,
				results.size(),
				missing,
				passed,
				failed,
				fullReady,
				performanceOnlyReady,
				maxStatic,
				maxCt,
				maxCp,
				maxEta,
				ready,
				false,
				false,
				ready ? "READY" : "BLOCKED",
				messageFor(sourceRowsReviewed, archiveGridCoverageReady, scatteredSurfaceFitRun, missing, failed),
				sourceRuntimeInfo
		);
	}

	private static ScatteredSurfaceFitTarget target(
			PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridCoverageRow row
	) {
		return new ScatteredSurfaceFitTarget(
				row.presetName(),
				row.caseName(),
				row.performanceMatchId(),
				row.queryAdvanceRatioJ(),
				row.queryRpm(),
				row.minimumPerformanceNeighborRows(),
				row.availableRectangularNeighborRows(),
				row.availableNonstaticNeighborRows(),
				row.reviewedNeighborBindingReady(),
				row.scatteredFitRequired(),
				row.postReviewFullSimulationLookupAllowed(),
				row.reviewedNeighborBindingReady()
						? "ready-for-reviewed-neighbor-binding"
						: NEXT_REQUIRED_ACTION
		);
	}

	private static ScatteredSurfaceFitResult passingDirectResult(ScatteredSurfaceFitTarget target) {
		return result(target, true, false, 0.001, 0.0, 0.0, 0.0, true, true);
	}

	private static ScatteredSurfaceFitResult passingResult(ScatteredSurfaceFitTarget target) {
		if (target.directNeighborBindingReady()) {
			return passingDirectResult(target);
		}
		return result(target, true, true, 0.002, 0.03, 0.04, 0.02, true, true);
	}

	private static ScatteredSurfaceFitResult failingResult(ScatteredSurfaceFitTarget target) {
		return result(target, true, true, 0.002, MAX_CT_HOLDOUT_RESIDUAL * 1.25,
				0.04, 0.02, true, true);
	}

	private static ScatteredSurfaceFitResult findResult(
			List<ScatteredSurfaceFitResult> results,
			String presetName,
			String caseName
	) {
		for (ScatteredSurfaceFitResult result : results) {
			if (result.presetName().equals(presetName) && result.caseName().equals(caseName)) {
				return result;
			}
		}
		return null;
	}

	private static ScatteredSurfaceFitExtrema extrema(List<ScatteredSurfaceFitScenario> scenarios) {
		int ready = 0;
		int maxObserved = 0;
		int maxMissing = 0;
		int maxFailed = 0;
		int maxFull = 0;
		int maxPerformanceOnly = 0;
		double maxStatic = 0.0;
		double maxCt = 0.0;
		double maxCp = 0.0;
		double maxEta = 0.0;
		int runtime = 0;
		int gameplay = 0;
		for (ScatteredSurfaceFitScenario scenario : scenarios) {
			ScatteredSurfaceFitSummary summary = scenario.summary();
			if (summary.scatteredSurfaceFitContractReady()) {
				ready++;
			}
			maxObserved = Math.max(maxObserved, summary.observedResultCount());
			maxMissing = Math.max(maxMissing, summary.missingResultCount());
			maxFailed = Math.max(maxFailed, summary.failedResultCount());
			maxFull = Math.max(maxFull, summary.readyFullSimulationTargetCount());
			maxPerformanceOnly = Math.max(maxPerformanceOnly, summary.readyPerformanceOnlyTargetCount());
			maxStatic = Math.max(maxStatic, summary.maxStaticAnchorResidual());
			maxCt = Math.max(maxCt, summary.maxCtHoldoutResidual());
			maxCp = Math.max(maxCp, summary.maxCpHoldoutResidual());
			maxEta = Math.max(maxEta, summary.maxEtaConsistencyResidual());
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new ScatteredSurfaceFitExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxObserved,
				maxMissing,
				maxFailed,
				maxFull,
				maxPerformanceOnly,
				maxStatic,
				maxCt,
				maxCp,
				maxEta,
				runtime,
				gameplay
		);
	}

	private static String messageFor(
			boolean sourceRowsReviewed,
			boolean archiveGridCoverageReady,
			boolean scatteredSurfaceFitRun,
			int missing,
			int failed
	) {
		if (!sourceRowsReviewed) {
			return "source-license-review-required";
		}
		if (!archiveGridCoverageReady) {
			return "archive-grid-coverage-not-ready";
		}
		if (!scatteredSurfaceFitRun) {
			return "scattered-surface-fit-not-run";
		}
		if (missing > 0) {
			return "scattered-surface-fit-results-missing";
		}
		if (failed > 0) {
			return "scattered-surface-fit-residual-gate-failed";
		}
		return "scattered-surface-fit-contract-ready";
	}

	private static void validateResidual(String fieldName, double value) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(fieldName + " must be finite and nonnegative.");
		}
	}

	private static String key(String presetName, String caseName) {
		return presetName + "/" + caseName;
	}
}
