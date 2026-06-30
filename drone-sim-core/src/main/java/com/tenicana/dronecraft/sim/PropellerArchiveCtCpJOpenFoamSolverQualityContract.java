package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PropellerArchiveCtCpJOpenFoamSolverQualityContract {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-OpenFOAM-Solver-Quality-Contract-Packet";
	public static final String CAVEAT =
			"OpenFOAM solver quality contract accepts only compact external mesh, timestep, Courant, Mach, Reynolds, and grid-independence QA summaries before CFD result rows can be trusted; it vendors no solver output and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int QUALITY_FIELD_ROW_COUNT = 16;
	public static final int SCENARIO_ROW_COUNT = 5;
	public static final int SUMMARY_ROW_COUNT = 13;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ QUALITY_FIELD_ROW_COUNT
			+ SCENARIO_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final int REQUIRED_QUALITY_CHANNEL_COUNT = QUALITY_FIELD_ROW_COUNT;
	public static final double MAX_GRID_INDEPENDENCE_RESIDUAL_RATIO = 0.03;
	public static final double MAX_MESH_NON_ORTHOGONALITY_DEGREES = 65.0;
	public static final double MAX_REYNOLDS_REFERENCE_RESIDUAL_RATIO = 0.02;

	private static final String REVIEWED_CASE_SHA =
			"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
	private static final double EPSILON = 1.0e-12;

	private static final List<OpenFoamSolverQualityField> FIELDS = List.of(
			new OpenFoamSolverQualityField("case_key", "text", true,
					"join quality evidence to the manifest case", false, false),
			new OpenFoamSolverQualityField("source_case_sha256", "sha256", true,
					"prove exact external case archive identity", false, false),
			new OpenFoamSolverQualityField("quality_channel_count", "count", true,
					"prove the compact QA payload is complete", false, false),
			new OpenFoamSolverQualityField("max_blade_cell_courant", "ratio", true,
					"gate blade-cell timestep stability", false, false),
			new OpenFoamSolverQualityField("max_freestream_courant", "ratio", true,
					"gate wake and inflow timestep stability", false, false),
			new OpenFoamSolverQualityField("max_azimuth_degrees_per_step", "deg", true,
					"gate rotor azimuth advance per sample", false, false),
			new OpenFoamSolverQualityField("max_helical_tip_mach", "mach", true,
					"gate incompressible steady-rotor assumption", false, false),
			new OpenFoamSolverQualityField("station_reynolds_chord_number", "Re", true,
					"confirm the 0.75R chord Reynolds setup reference", false, false),
			new OpenFoamSolverQualityField("low_reynolds_model_reviewed", "boolean", true,
					"prove static-anchor low-Reynolds model review when required", false, false),
			new OpenFoamSolverQualityField("near_blade_cell_size_meters", "m", true,
					"prove near-blade cells are no coarser than the budget", false, false),
			new OpenFoamSolverQualityField("wake_core_cell_size_meters", "m", true,
					"prove wake-core cells are no coarser than the budget", false, false),
			new OpenFoamSolverQualityField("radial_domain_diameters", "D", true,
					"prove radial far-field padding", false, false),
			new OpenFoamSolverQualityField("upstream_domain_diameters", "D", true,
					"prove upstream inlet padding", false, false),
			new OpenFoamSolverQualityField("downstream_domain_diameters", "D", true,
					"prove downstream wake padding", false, false),
			new OpenFoamSolverQualityField("grid_independence_residual_ratio", "ratio", true,
					"gate coarse versus refined force and torque stability", false, false),
			new OpenFoamSolverQualityField("mesh_non_orthogonality_max_degrees", "deg", true,
					"gate mesh quality before solver evidence can support results", false, false)
	);

	private PropellerArchiveCtCpJOpenFoamSolverQualityContract() {
	}

	public record OpenFoamSolverQualityField(
			String fieldName,
			String unit,
			boolean required,
			String downstreamUse,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed
	) {
	}

	public record OpenFoamSolverQualitySample(
			String presetName,
			String caseName,
			String caseKey,
			String sourceCaseSha256,
			int qualityChannelCount,
			double maxBladeCellCourant,
			double maxFreestreamCourant,
			double maxAzimuthDegreesPerStep,
			double maxHelicalTipMach,
			double stationReynoldsChordNumber,
			double reynoldsReferenceResidualRatio,
			boolean lowReynoldsModelReviewed,
			double nearBladeCellSizeMeters,
			double wakeCoreCellSizeMeters,
			double radialDomainDiameters,
			double upstreamDomainDiameters,
			double downstreamDomainDiameters,
			double gridIndependenceResidualRatio,
			double meshNonOrthogonalityMaxDegrees,
			boolean passed,
			String status
	) {
	}

	public record OpenFoamSolverQualitySummary(
			boolean numericalBudgetReady,
			boolean externalCaseHashReady,
			boolean solverQualityExtractionReady,
			int expectedQualityCaseCount,
			int observedQualityCaseCount,
			int missingQualityCaseCount,
			int passedQualityCaseCount,
			int failedQualityCaseCount,
			int minObservedQualityChannelCount,
			double maxBladeCellCourant,
			double maxFreestreamCourant,
			double maxAzimuthDegreesPerStep,
			double maxHelicalTipMach,
			double maxReynoldsReferenceResidualRatio,
			double maxGridIndependenceResidualRatio,
			double maxMeshNonOrthogonalityDegrees,
			int lowReynoldsReviewRequiredCount,
			int lowReynoldsReviewMissingCount,
			boolean allExpectedQualityPresent,
			boolean allObservedQualityPassed,
			boolean openFoamSolverQualityContractReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record OpenFoamSolverQualityScenario(
			String scenarioName,
			OpenFoamSolverQualitySummary summary
	) {
	}

	public record OpenFoamSolverQualityExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxExpectedQualityCaseCount,
			int maxMissingQualityCaseCount,
			int maxFailedQualityCaseCount,
			double maxBladeCellCourant,
			double maxGridIndependenceResidualRatio,
			int maxLowReynoldsReviewMissingCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJOpenFoamSolverQualityContractAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int qualityFieldRowCount,
			int scenarioRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<OpenFoamSolverQualityField> fields,
			List<OpenFoamSolverQualityScenario> scenarios,
			OpenFoamSolverQualityExtrema extrema
	) {
		public CtCpJOpenFoamSolverQualityContractAudit {
			fields = List.copyOf(fields);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJOpenFoamSolverQualityContractAudit audit() {
		List<PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow> targets =
				numericalBudgetTargets();
		List<OpenFoamSolverQualitySample> passing = passingQualitySamples(targets);
		List<OpenFoamSolverQualitySample> lowReynoldsMissing = new ArrayList<>(passing);
		lowReynoldsMissing.set(0, sampleWithLowReynoldsReview(targets.get(0), false));
		List<OpenFoamSolverQualitySample> courantFailed = new ArrayList<>(passing);
		courantFailed.set(0, sampleWithBladeCourant(targets.get(0),
				PropellerArchiveCtCpJOpenFoamNumericalBudget.MAX_BLADE_COURANT + 0.10));
		List<OpenFoamSolverQualityScenario> scenarios = List.of(
				new OpenFoamSolverQualityScenario(
						"current_no_case_hash_no_solver_quality",
						review(false, false, false, List.of(), "current-solver-quality-blocked")),
				new OpenFoamSolverQualityScenario(
						"numerical_budget_ready_quality_missing",
						review(true, true, true, List.of(), "solver-quality-results-missing")),
				new OpenFoamSolverQualityScenario(
						"synthetic_solver_quality_all_pass",
						review(true, true, true, passing, "synthetic-solver-quality-all-pass")),
				new OpenFoamSolverQualityScenario(
						"synthetic_low_reynolds_review_missing",
						review(true, true, true, lowReynoldsMissing,
								"synthetic-low-reynolds-review-missing")),
				new OpenFoamSolverQualityScenario(
						"synthetic_blade_courant_failed",
						review(true, true, true, courantFailed, "synthetic-blade-courant-failed"))
		);
		return new CtCpJOpenFoamSolverQualityContractAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				QUALITY_FIELD_ROW_COUNT,
				SCENARIO_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				FIELDS,
				scenarios,
				extrema(scenarios)
		);
	}

	public static OpenFoamSolverQualityField field(String fieldName) {
		return FIELDS.stream()
				.filter(field -> field.fieldName().equals(fieldName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown OpenFOAM solver quality field: " + fieldName));
	}

	public static OpenFoamSolverQualitySample sample(
			PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow target,
			String sourceCaseSha256,
			int qualityChannelCount,
			double maxBladeCellCourant,
			double maxFreestreamCourant,
			double maxAzimuthDegreesPerStep,
			double maxHelicalTipMach,
			double stationReynoldsChordNumber,
			boolean lowReynoldsModelReviewed,
			double nearBladeCellSizeMeters,
			double wakeCoreCellSizeMeters,
			double radialDomainDiameters,
			double upstreamDomainDiameters,
			double downstreamDomainDiameters,
			double gridIndependenceResidualRatio,
			double meshNonOrthogonalityMaxDegrees
	) {
		if (target == null) {
			throw new IllegalArgumentException("target numerical budget row must not be null.");
		}
		if (!isSha256(sourceCaseSha256)) {
			throw new IllegalArgumentException("sourceCaseSha256 must be a reviewed 64-character SHA-256.");
		}
		requireFinite("maxBladeCellCourant", maxBladeCellCourant);
		requireFinite("maxFreestreamCourant", maxFreestreamCourant);
		requireFinite("maxAzimuthDegreesPerStep", maxAzimuthDegreesPerStep);
		requireFinite("maxHelicalTipMach", maxHelicalTipMach);
		requireFinite("stationReynoldsChordNumber", stationReynoldsChordNumber);
		requireFinite("nearBladeCellSizeMeters", nearBladeCellSizeMeters);
		requireFinite("wakeCoreCellSizeMeters", wakeCoreCellSizeMeters);
		requireFinite("radialDomainDiameters", radialDomainDiameters);
		requireFinite("upstreamDomainDiameters", upstreamDomainDiameters);
		requireFinite("downstreamDomainDiameters", downstreamDomainDiameters);
		requireFinite("gridIndependenceResidualRatio", gridIndependenceResidualRatio);
		requireFinite("meshNonOrthogonalityMaxDegrees", meshNonOrthogonalityMaxDegrees);

		double reynoldsResidual = residualRatio(stationReynoldsChordNumber,
				target.reynoldsStationChordNumber());
		boolean lowReynoldsReady = !target.lowReynoldsTransitionReviewRequired()
				|| lowReynoldsModelReviewed;
		boolean passed = qualityChannelCount >= REQUIRED_QUALITY_CHANNEL_COUNT
				&& maxBladeCellCourant <= PropellerArchiveCtCpJOpenFoamNumericalBudget.MAX_BLADE_COURANT
				&& maxFreestreamCourant <= PropellerArchiveCtCpJOpenFoamNumericalBudget.MAX_FREESTREAM_COURANT
				&& maxAzimuthDegreesPerStep <= PropellerArchiveCtCpJOpenFoamNumericalBudget
						.MAX_AZIMUTH_DEGREES_PER_STEP
				&& maxHelicalTipMach <= PropellerArchiveCtCpJOpenFoamNumericalBudget
						.INCOMPRESSIBLE_TIP_MACH_LIMIT
				&& reynoldsResidual <= MAX_REYNOLDS_REFERENCE_RESIDUAL_RATIO
				&& lowReynoldsReady
				&& nearBladeCellSizeMeters <= target.nearBladeCellSizeMeters()
				&& wakeCoreCellSizeMeters <= target.wakeCoreCellSizeMeters()
				&& radialDomainDiameters >= PropellerArchiveCtCpJOpenFoamNumericalBudget
						.FAR_FIELD_RADIUS_DIAMETERS
				&& upstreamDomainDiameters >= PropellerArchiveCtCpJOpenFoamNumericalBudget
						.UPSTREAM_LENGTH_DIAMETERS
				&& downstreamDomainDiameters >= PropellerArchiveCtCpJOpenFoamNumericalBudget
						.DOWNSTREAM_LENGTH_DIAMETERS
				&& gridIndependenceResidualRatio <= MAX_GRID_INDEPENDENCE_RESIDUAL_RATIO
				&& meshNonOrthogonalityMaxDegrees <= MAX_MESH_NON_ORTHOGONALITY_DEGREES;
		return new OpenFoamSolverQualitySample(
				target.presetName(),
				target.caseName(),
				target.caseKey(),
				sourceCaseSha256,
				qualityChannelCount,
				maxBladeCellCourant,
				maxFreestreamCourant,
				maxAzimuthDegreesPerStep,
				maxHelicalTipMach,
				stationReynoldsChordNumber,
				reynoldsResidual,
				lowReynoldsModelReviewed,
				nearBladeCellSizeMeters,
				wakeCoreCellSizeMeters,
				radialDomainDiameters,
				upstreamDomainDiameters,
				downstreamDomainDiameters,
				gridIndependenceResidualRatio,
				meshNonOrthogonalityMaxDegrees,
				passed,
				passed ? "PASS" : "FAIL"
		);
	}

	public static OpenFoamSolverQualitySummary review(
			boolean numericalBudgetReady,
			boolean externalCaseHashReady,
			boolean solverQualityExtractionReady,
			List<OpenFoamSolverQualitySample> samples,
			String sourceRuntimeInfo
	) {
		if (samples == null) {
			throw new IllegalArgumentException("samples must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		List<PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow> targets =
				numericalBudgetTargets();
		validateUniqueSamples(samples);
		int observed = samples.size();
		int passed = 0;
		int failed = 0;
		int minChannels = observed > 0 ? Integer.MAX_VALUE : 0;
		int lowReRequired = 0;
		int lowReMissing = 0;
		double bladeCourant = 0.0;
		double freestreamCourant = 0.0;
		double azimuth = 0.0;
		double mach = 0.0;
		double reynoldsResidual = 0.0;
		double gridResidual = 0.0;
		double nonOrthogonality = 0.0;
		for (OpenFoamSolverQualitySample sample : samples) {
			PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow target =
					target(targets, sample.presetName(), sample.caseName());
			if (target.lowReynoldsTransitionReviewRequired()) {
				lowReRequired++;
				if (!sample.lowReynoldsModelReviewed()) {
					lowReMissing++;
				}
			}
			if (sample.passed()) {
				passed++;
			} else {
				failed++;
			}
			minChannels = Math.min(minChannels, sample.qualityChannelCount());
			bladeCourant = Math.max(bladeCourant, sample.maxBladeCellCourant());
			freestreamCourant = Math.max(freestreamCourant, sample.maxFreestreamCourant());
			azimuth = Math.max(azimuth, sample.maxAzimuthDegreesPerStep());
			mach = Math.max(mach, sample.maxHelicalTipMach());
			reynoldsResidual = Math.max(reynoldsResidual, sample.reynoldsReferenceResidualRatio());
			gridResidual = Math.max(gridResidual, sample.gridIndependenceResidualRatio());
			nonOrthogonality = Math.max(nonOrthogonality, sample.meshNonOrthogonalityMaxDegrees());
		}
		int expected = targets.size();
		int missing = Math.max(0, expected - observed);
		boolean allPresent = observed == expected;
		boolean allPassed = observed > 0 && failed == 0 && passed == observed;
		boolean ready = numericalBudgetReady
				&& externalCaseHashReady
				&& solverQualityExtractionReady
				&& allPresent
				&& allPassed;
		return new OpenFoamSolverQualitySummary(
				numericalBudgetReady,
				externalCaseHashReady,
				solverQualityExtractionReady,
				expected,
				observed,
				missing,
				passed,
				failed,
				minChannels,
				bladeCourant,
				freestreamCourant,
				azimuth,
				mach,
				reynoldsResidual,
				gridResidual,
				nonOrthogonality,
				lowReRequired,
				lowReMissing,
				allPresent,
				allPassed,
				ready,
				false,
				false,
				ready ? "READY" : "BLOCKED",
				messageFor(numericalBudgetReady, externalCaseHashReady, solverQualityExtractionReady,
						allPresent, allPassed, lowReMissing),
				sourceRuntimeInfo
		);
	}

	private static List<PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow>
			numericalBudgetTargets() {
		return PropellerArchiveCtCpJOpenFoamNumericalBudget.audit().rows();
	}

	private static List<OpenFoamSolverQualitySample> passingQualitySamples(
			List<PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow> targets
	) {
		return targets.stream()
				.map(target -> sample(target,
						REVIEWED_CASE_SHA,
						REQUIRED_QUALITY_CHANNEL_COUNT,
						target.bladeCellCourantAtSuggestedTimeStep(),
						target.freestreamCourantAtSuggestedTimeStep(),
						target.azimuthDegreesPerStep(),
						target.helicalTipMach(),
						target.reynoldsStationChordNumber(),
						true,
						target.nearBladeCellSizeMeters(),
						target.wakeCoreCellSizeMeters(),
						PropellerArchiveCtCpJOpenFoamNumericalBudget.FAR_FIELD_RADIUS_DIAMETERS,
						PropellerArchiveCtCpJOpenFoamNumericalBudget.UPSTREAM_LENGTH_DIAMETERS,
						PropellerArchiveCtCpJOpenFoamNumericalBudget.DOWNSTREAM_LENGTH_DIAMETERS,
						0.015,
						48.0))
				.toList();
	}

	private static OpenFoamSolverQualitySample sampleWithLowReynoldsReview(
			PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow target,
			boolean reviewed
	) {
		return sample(target,
				REVIEWED_CASE_SHA,
				REQUIRED_QUALITY_CHANNEL_COUNT,
				target.bladeCellCourantAtSuggestedTimeStep(),
				target.freestreamCourantAtSuggestedTimeStep(),
				target.azimuthDegreesPerStep(),
				target.helicalTipMach(),
				target.reynoldsStationChordNumber(),
				reviewed,
				target.nearBladeCellSizeMeters(),
				target.wakeCoreCellSizeMeters(),
				PropellerArchiveCtCpJOpenFoamNumericalBudget.FAR_FIELD_RADIUS_DIAMETERS,
				PropellerArchiveCtCpJOpenFoamNumericalBudget.UPSTREAM_LENGTH_DIAMETERS,
				PropellerArchiveCtCpJOpenFoamNumericalBudget.DOWNSTREAM_LENGTH_DIAMETERS,
				0.015,
				48.0);
	}

	private static OpenFoamSolverQualitySample sampleWithBladeCourant(
			PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow target,
			double bladeCourant
	) {
		return sample(target,
				REVIEWED_CASE_SHA,
				REQUIRED_QUALITY_CHANNEL_COUNT,
				bladeCourant,
				target.freestreamCourantAtSuggestedTimeStep(),
				target.azimuthDegreesPerStep(),
				target.helicalTipMach(),
				target.reynoldsStationChordNumber(),
				true,
				target.nearBladeCellSizeMeters(),
				target.wakeCoreCellSizeMeters(),
				PropellerArchiveCtCpJOpenFoamNumericalBudget.FAR_FIELD_RADIUS_DIAMETERS,
				PropellerArchiveCtCpJOpenFoamNumericalBudget.UPSTREAM_LENGTH_DIAMETERS,
				PropellerArchiveCtCpJOpenFoamNumericalBudget.DOWNSTREAM_LENGTH_DIAMETERS,
				0.015,
				48.0);
	}

	private static OpenFoamSolverQualityExtrema extrema(List<OpenFoamSolverQualityScenario> scenarios) {
		int ready = 0;
		int runtime = 0;
		int gameplay = 0;
		int maxExpected = 0;
		int maxMissing = 0;
		int maxFailed = 0;
		int maxLowReMissing = 0;
		double maxBladeCourant = 0.0;
		double maxGridResidual = 0.0;
		for (OpenFoamSolverQualityScenario scenario : scenarios) {
			OpenFoamSolverQualitySummary summary = scenario.summary();
			if (summary.openFoamSolverQualityContractReady()) {
				ready++;
			}
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxExpected = Math.max(maxExpected, summary.expectedQualityCaseCount());
			maxMissing = Math.max(maxMissing, summary.missingQualityCaseCount());
			maxFailed = Math.max(maxFailed, summary.failedQualityCaseCount());
			maxLowReMissing = Math.max(maxLowReMissing, summary.lowReynoldsReviewMissingCount());
			maxBladeCourant = Math.max(maxBladeCourant, summary.maxBladeCellCourant());
			maxGridResidual = Math.max(maxGridResidual, summary.maxGridIndependenceResidualRatio());
		}
		return new OpenFoamSolverQualityExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxExpected,
				maxMissing,
				maxFailed,
				maxBladeCourant,
				maxGridResidual,
				maxLowReMissing,
				runtime,
				gameplay
		);
	}

	private static void validateUniqueSamples(List<OpenFoamSolverQualitySample> samples) {
		Set<String> seen = new HashSet<>();
		for (OpenFoamSolverQualitySample sample : samples) {
			if (sample == null) {
				throw new IllegalArgumentException("quality samples must not include null rows.");
			}
			String key = sample.presetName() + "/" + sample.caseName();
			if (!seen.add(key)) {
				throw new IllegalArgumentException("duplicate OpenFOAM solver quality sample: " + key);
			}
		}
	}

	private static PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow target(
			List<PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow> targets,
			String presetName,
			String caseName
	) {
		return targets.stream()
				.filter(target -> target.presetName().equals(presetName)
						&& target.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unexpected OpenFOAM solver quality sample: " + presetName + "/" + caseName));
	}

	private static String messageFor(
			boolean numericalBudgetReady,
			boolean externalCaseHashReady,
			boolean solverQualityExtractionReady,
			boolean allPresent,
			boolean allPassed,
			int lowReynoldsMissing
	) {
		if (!numericalBudgetReady) {
			return "openfoam-numerical-budget-not-ready";
		}
		if (!externalCaseHashReady) {
			return "openfoam-case-hash-missing";
		}
		if (!solverQualityExtractionReady) {
			return "openfoam-solver-quality-extraction-not-ready";
		}
		if (!allPresent) {
			return "openfoam-solver-quality-results-missing";
		}
		if (lowReynoldsMissing > 0) {
			return "openfoam-low-reynolds-review-missing";
		}
		if (!allPassed) {
			return "openfoam-solver-quality-gate-failed";
		}
		return "openfoam-solver-quality-contract-ready";
	}

	private static void requireFinite(String name, double value) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(name + " must be finite and non-negative.");
		}
	}

	private static boolean isSha256(String value) {
		return value != null && value.matches("[0-9a-fA-F]{64}");
	}

	private static double residualRatio(double observed, double expected) {
		return Math.abs(observed - expected) / Math.max(EPSILON, Math.abs(expected));
	}
}
