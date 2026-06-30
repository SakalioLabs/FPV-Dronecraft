package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-OpenFOAM-Solver-Quality-Blocker-Report-Packet";
	public static final String CAVEAT =
			"OpenFOAM solver quality blocker report decomposes numerical budget, external case provenance, compact QA extraction, mesh/timestep, Reynolds, and runtime-leak blockers before CFD result support can open; it remains audit-only and never enables gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 5;
	public static final int SCENARIO_ROW_COUNT =
			PropellerArchiveCtCpJOpenFoamSolverQualityContract.SCENARIO_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 15;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SCENARIO_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport() {
	}

	public record OpenFoamSolverQualityBlockerScenario(
			String scenarioName,
			int blockerCount,
			boolean numericalBudgetBlocker,
			boolean externalCaseHashBlocker,
			boolean solverQualityExtractionBlocker,
			boolean missingQualityCaseBlocker,
			boolean failedQualityCaseBlocker,
			boolean incompleteQualityPayloadBlocker,
			boolean bladeCourantBlocker,
			boolean freestreamCourantBlocker,
			boolean azimuthStepBlocker,
			boolean tipMachBlocker,
			boolean reynoldsReferenceBlocker,
			boolean lowReynoldsReviewBlocker,
			boolean gridIndependenceBlocker,
			boolean meshNonOrthogonalityBlocker,
			boolean runtimeCouplingLeakBlocker,
			boolean gameplayAutoApplyLeakBlocker,
			int expectedQualityCaseCount,
			int observedQualityCaseCount,
			int missingQualityCaseCount,
			int failedQualityCaseCount,
			int lowReynoldsReviewMissingCount,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record OpenFoamSolverQualityBlockerExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxBlockerCount,
			int numericalBudgetBlockerScenarioCount,
			int externalCaseHashBlockerScenarioCount,
			int solverQualityExtractionBlockerScenarioCount,
			int missingQualityCaseBlockerScenarioCount,
			int failedQualityCaseBlockerScenarioCount,
			int lowReynoldsReviewBlockerScenarioCount,
			int bladeCourantBlockerScenarioCount,
			int gridIndependenceBlockerScenarioCount,
			int meshNonOrthogonalityBlockerScenarioCount,
			int runtimeCouplingLeakScenarioCount,
			int gameplayAutoApplyLeakScenarioCount
	) {
	}

	public record CtCpJOpenFoamSolverQualityBlockerReportAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<OpenFoamSolverQualityBlockerScenario> scenarios,
			OpenFoamSolverQualityBlockerExtrema extrema
	) {
		public CtCpJOpenFoamSolverQualityBlockerReportAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJOpenFoamSolverQualityBlockerReportAudit audit() {
		return audit(PropellerArchiveCtCpJOpenFoamSolverQualityContract.audit());
	}

	public static CtCpJOpenFoamSolverQualityBlockerReportAudit audit(
			PropellerArchiveCtCpJOpenFoamSolverQualityContract.CtCpJOpenFoamSolverQualityContractAudit qualityAudit
	) {
		if (qualityAudit == null) {
			throw new IllegalArgumentException("qualityAudit must not be null.");
		}
		List<OpenFoamSolverQualityBlockerScenario> scenarios = qualityAudit.scenarios()
				.stream()
				.map(PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport::scenario)
				.toList();
		return new CtCpJOpenFoamSolverQualityBlockerReportAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				scenarios,
				extrema(scenarios)
		);
	}

	public static OpenFoamSolverQualityBlockerScenario scenario(
			PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualityScenario scenario
	) {
		if (scenario == null) {
			throw new IllegalArgumentException("solver quality scenario must not be null.");
		}
		return scenario(scenario.scenarioName(), scenario.summary());
	}

	public static OpenFoamSolverQualityBlockerScenario scenario(
			String scenarioName,
			PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary summary
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (summary == null) {
			throw new IllegalArgumentException("solver quality summary must not be null.");
		}
		boolean numericalBudgetBlocker = !summary.numericalBudgetReady();
		boolean externalCaseHashBlocker = !summary.externalCaseHashReady();
		boolean solverQualityExtractionBlocker = !summary.solverQualityExtractionReady();
		boolean missingQualityCaseBlocker = summary.missingQualityCaseCount() > 0;
		boolean failedQualityCaseBlocker = summary.failedQualityCaseCount() > 0;
		boolean incompleteQualityPayloadBlocker = summary.observedQualityCaseCount() > 0
				&& summary.minObservedQualityChannelCount()
						< PropellerArchiveCtCpJOpenFoamSolverQualityContract.REQUIRED_QUALITY_CHANNEL_COUNT;
		boolean bladeCourantBlocker = summary.maxBladeCellCourant()
				> PropellerArchiveCtCpJOpenFoamNumericalBudget.MAX_BLADE_COURANT;
		boolean freestreamCourantBlocker = summary.maxFreestreamCourant()
				> PropellerArchiveCtCpJOpenFoamNumericalBudget.MAX_FREESTREAM_COURANT;
		boolean azimuthStepBlocker = summary.maxAzimuthDegreesPerStep()
				> PropellerArchiveCtCpJOpenFoamNumericalBudget.MAX_AZIMUTH_DEGREES_PER_STEP;
		boolean tipMachBlocker = summary.maxHelicalTipMach()
				> PropellerArchiveCtCpJOpenFoamNumericalBudget.INCOMPRESSIBLE_TIP_MACH_LIMIT;
		boolean reynoldsReferenceBlocker = summary.maxReynoldsReferenceResidualRatio()
				> PropellerArchiveCtCpJOpenFoamSolverQualityContract.MAX_REYNOLDS_REFERENCE_RESIDUAL_RATIO;
		boolean lowReynoldsReviewBlocker = summary.lowReynoldsReviewMissingCount() > 0;
		boolean gridIndependenceBlocker = summary.maxGridIndependenceResidualRatio()
				> PropellerArchiveCtCpJOpenFoamSolverQualityContract.MAX_GRID_INDEPENDENCE_RESIDUAL_RATIO;
		boolean meshNonOrthogonalityBlocker = summary.maxMeshNonOrthogonalityDegrees()
				> PropellerArchiveCtCpJOpenFoamSolverQualityContract.MAX_MESH_NON_ORTHOGONALITY_DEGREES;
		boolean runtimeLeak = summary.runtimeCouplingAllowed();
		boolean gameplayLeak = summary.gameplayAutoApplyAllowed();
		int blockerCount = countTrue(
				numericalBudgetBlocker,
				externalCaseHashBlocker,
				solverQualityExtractionBlocker,
				missingQualityCaseBlocker,
				failedQualityCaseBlocker,
				incompleteQualityPayloadBlocker,
				bladeCourantBlocker,
				freestreamCourantBlocker,
				azimuthStepBlocker,
				tipMachBlocker,
				reynoldsReferenceBlocker,
				lowReynoldsReviewBlocker,
				gridIndependenceBlocker,
				meshNonOrthogonalityBlocker,
				runtimeLeak,
				gameplayLeak);
		boolean ready = summary.openFoamSolverQualityContractReady() && blockerCount == 0;
		return new OpenFoamSolverQualityBlockerScenario(
				scenarioName,
				blockerCount,
				numericalBudgetBlocker,
				externalCaseHashBlocker,
				solverQualityExtractionBlocker,
				missingQualityCaseBlocker,
				failedQualityCaseBlocker,
				incompleteQualityPayloadBlocker,
				bladeCourantBlocker,
				freestreamCourantBlocker,
				azimuthStepBlocker,
				tipMachBlocker,
				reynoldsReferenceBlocker,
				lowReynoldsReviewBlocker,
				gridIndependenceBlocker,
				meshNonOrthogonalityBlocker,
				runtimeLeak,
				gameplayLeak,
				summary.expectedQualityCaseCount(),
				summary.observedQualityCaseCount(),
				summary.missingQualityCaseCount(),
				summary.failedQualityCaseCount(),
				summary.lowReynoldsReviewMissingCount(),
				nextRequiredAction(
						numericalBudgetBlocker,
						externalCaseHashBlocker,
						solverQualityExtractionBlocker,
						missingQualityCaseBlocker,
						failedQualityCaseBlocker,
						incompleteQualityPayloadBlocker,
						bladeCourantBlocker,
						freestreamCourantBlocker,
						azimuthStepBlocker,
						tipMachBlocker,
						reynoldsReferenceBlocker,
						lowReynoldsReviewBlocker,
						gridIndependenceBlocker,
						meshNonOrthogonalityBlocker,
						runtimeLeak,
						gameplayLeak),
				ready ? "READY" : "BLOCKED",
				ready ? "openfoam-solver-quality-blockers-clear"
						: "openfoam-solver-quality-blockers-open"
		);
	}

	private static OpenFoamSolverQualityBlockerExtrema extrema(
			List<OpenFoamSolverQualityBlockerScenario> scenarios
	) {
		int ready = 0;
		int maxBlockers = 0;
		int numerical = 0;
		int hash = 0;
		int extraction = 0;
		int missing = 0;
		int failed = 0;
		int lowRe = 0;
		int blade = 0;
		int grid = 0;
		int mesh = 0;
		int runtime = 0;
		int gameplay = 0;
		for (OpenFoamSolverQualityBlockerScenario scenario : scenarios) {
			if ("READY".equals(scenario.status())) {
				ready++;
			}
			maxBlockers = Math.max(maxBlockers, scenario.blockerCount());
			if (scenario.numericalBudgetBlocker()) {
				numerical++;
			}
			if (scenario.externalCaseHashBlocker()) {
				hash++;
			}
			if (scenario.solverQualityExtractionBlocker()) {
				extraction++;
			}
			if (scenario.missingQualityCaseBlocker()) {
				missing++;
			}
			if (scenario.failedQualityCaseBlocker()) {
				failed++;
			}
			if (scenario.lowReynoldsReviewBlocker()) {
				lowRe++;
			}
			if (scenario.bladeCourantBlocker()) {
				blade++;
			}
			if (scenario.gridIndependenceBlocker()) {
				grid++;
			}
			if (scenario.meshNonOrthogonalityBlocker()) {
				mesh++;
			}
			if (scenario.runtimeCouplingLeakBlocker()) {
				runtime++;
			}
			if (scenario.gameplayAutoApplyLeakBlocker()) {
				gameplay++;
			}
		}
		return new OpenFoamSolverQualityBlockerExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxBlockers,
				numerical,
				hash,
				extraction,
				missing,
				failed,
				lowRe,
				blade,
				grid,
				mesh,
				runtime,
				gameplay
		);
	}

	private static String nextRequiredAction(
			boolean numericalBudgetBlocker,
			boolean externalCaseHashBlocker,
			boolean solverQualityExtractionBlocker,
			boolean missingQualityCaseBlocker,
			boolean failedQualityCaseBlocker,
			boolean incompleteQualityPayloadBlocker,
			boolean bladeCourantBlocker,
			boolean freestreamCourantBlocker,
			boolean azimuthStepBlocker,
			boolean tipMachBlocker,
			boolean reynoldsReferenceBlocker,
			boolean lowReynoldsReviewBlocker,
			boolean gridIndependenceBlocker,
			boolean meshNonOrthogonalityBlocker,
			boolean runtimeLeak,
			boolean gameplayLeak
	) {
		if (numericalBudgetBlocker) {
			return "review-openfoam-mesh-yplus-and-time-step-against-run-setup";
		}
		if (externalCaseHashBlocker) {
			return "author-external-openfoam-case-template-and-record-case-sha256";
		}
		if (solverQualityExtractionBlocker || missingQualityCaseBlocker) {
			return "extract-compact-openfoam-solver-quality-summary";
		}
		if (lowReynoldsReviewBlocker) {
			return "review-low-reynolds-static-anchor-openfoam-model";
		}
		if (bladeCourantBlocker || freestreamCourantBlocker || azimuthStepBlocker) {
			return "bind-openfoam-deltaT-to-run-setup-budget";
		}
		if (tipMachBlocker) {
			return "review-openfoam-compressibility-assumption";
		}
		if (reynoldsReferenceBlocker) {
			return "match-openfoam-reynolds-reference-to-run-setup";
		}
		if (incompleteQualityPayloadBlocker) {
			return "complete-openfoam-solver-quality-payload";
		}
		if (gridIndependenceBlocker) {
			return "run-openfoam-grid-independence-study";
		}
		if (meshNonOrthogonalityBlocker) {
			return "repair-openfoam-mesh-quality-before-result-support";
		}
		if (failedQualityCaseBlocker) {
			return "repair-failed-openfoam-solver-quality-cases";
		}
		if (runtimeLeak) {
			return "close-runtime-coupling-before-openfoam-quality-report";
		}
		if (gameplayLeak) {
			return "close-gameplay-auto-apply-before-openfoam-quality-report";
		}
		return "openfoam-solver-quality-blockers-clear";
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
