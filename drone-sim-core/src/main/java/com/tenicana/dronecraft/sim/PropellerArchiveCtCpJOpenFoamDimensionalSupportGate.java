package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJOpenFoamDimensionalSupportGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-OpenFOAM-Dimensional-Support-Gate-Packet";
	public static final String CAVEAT =
			"OpenFOAM dimensional support opens only when handoff-aware CT/CP/J lookup execution, coefficient-level CFD lookup support including solver-quality QA, and SI rotor-response residuals all pass; it cannot export references, patch runtime physics, or tune gameplay automatically.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 10;
	public static final int SCENARIO_SAMPLE_COUNT = 6;
	public static final int SCENARIO_METRIC_ROW_COUNT = 26;
	public static final int SUMMARY_ROW_COUNT = 14;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveCtCpJOpenFoamDimensionalSupportGate() {
	}

	public record OpenFoamDimensionalSupportSummary(
			boolean lookupExecutionContractReady,
			boolean lookupSupportReady,
			boolean dimensionalResidualReady,
			boolean dimensionalResponseReferenceReady,
			boolean openFoamCoefficientResultReady,
			boolean openFoamSolverQualityContractReady,
			boolean externalDimensionalExtractionReady,
			int expectedLookupTargetCount,
			int cfdSupportedLookupTargetCount,
			int cfdGeometryUnsupportedLookupTargetCount,
			int expectedDimensionalReferenceCount,
			int readyDimensionalReferenceCount,
			int expectedOpenFoamDimensionalResultCaseCount,
			int dimensionalObservedResultCount,
			int dimensionalMissingResultCount,
			int dimensionalFailedResultCount,
			int supportedDimensionalTargetCount,
			double maxThrustResidualToReference,
			double maxInducedVelocityResidualToReference,
			boolean cfdDimensionalSupportReady,
			boolean referenceExportAuthorityAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record OpenFoamDimensionalSupportScenario(
			String scenarioName,
			OpenFoamDimensionalSupportSummary summary
	) {
	}

	public record OpenFoamDimensionalSupportExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int lookupExecutionBlockedScenarioCount,
			int maxSupportedDimensionalTargetCount,
			int maxCfdSupportedLookupTargetCount,
			int maxCfdGeometryUnsupportedLookupTargetCount,
			int maxDimensionalMissingResultCount,
			int maxDimensionalFailedResultCount,
			double maxThrustResidualToReference,
			double maxInducedVelocityResidualToReference,
			int referenceExportAuthorityAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJOpenFoamDimensionalSupportGateAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<OpenFoamDimensionalSupportScenario> scenarios,
			OpenFoamDimensionalSupportExtrema extrema
	) {
		public CtCpJOpenFoamDimensionalSupportGateAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJOpenFoamDimensionalSupportGateAudit audit() {
		PropellerArchiveCtCpJOpenFoamLookupSupportGate.CtCpJOpenFoamLookupSupportGateAudit lookupAudit =
				PropellerArchiveCtCpJOpenFoamLookupSupportGate.audit();
		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.CtCpJOpenFoamDimensionalResidualContractAudit
				dimensionalAudit = PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.audit();
		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary currentLookup =
				lookupSupport(lookupAudit, "current_lookup_and_cfd_blocked");
		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary executionBlockedLookup =
				lookupSupport(lookupAudit, "lookup_execution_blocked_cfd_ready");
		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary lookupFailed =
				lookupSupport(lookupAudit, "cfd_ready_lookup_acceptance_failed");
		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary lookupReady =
				lookupSupport(lookupAudit, "lookup_and_cfd_ready");
		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary currentDimensional =
				dimensionalResidual(dimensionalAudit, "current_dimensional_and_openfoam_blocked");
		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary missingDimensional =
				dimensionalResidual(dimensionalAudit, "dimensional_reference_ready_openfoam_si_missing");
		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary failedDimensional =
				dimensionalResidual(dimensionalAudit, "dimensional_and_openfoam_si_residual_failed");
		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary readyDimensional =
				dimensionalResidual(dimensionalAudit, "dimensional_and_openfoam_si_ready");
		List<OpenFoamDimensionalSupportScenario> scenarios = List.of(
				new OpenFoamDimensionalSupportScenario(
						"current_lookup_and_dimensional_blocked",
						support(currentLookup, currentDimensional,
								"current-openfoam-dimensional-support-blocked")),
				new OpenFoamDimensionalSupportScenario(
						"lookup_execution_blocked_si_ready",
						support(executionBlockedLookup, readyDimensional,
								"synthetic-lookup-execution-blocked-si-ready")),
				new OpenFoamDimensionalSupportScenario(
						"lookup_support_ready_si_results_missing",
						support(lookupReady, missingDimensional,
								"synthetic-lookup-support-ready-si-results-missing")),
				new OpenFoamDimensionalSupportScenario(
						"si_residual_ready_lookup_support_failed",
						support(lookupFailed, readyDimensional,
								"synthetic-si-residual-ready-lookup-support-failed")),
				new OpenFoamDimensionalSupportScenario(
						"lookup_support_ready_si_residual_failed",
						support(lookupReady, failedDimensional,
								"synthetic-lookup-support-ready-si-residual-failed")),
				new OpenFoamDimensionalSupportScenario(
						"lookup_and_dimensional_openfoam_support_ready",
						support(lookupReady, readyDimensional,
								"synthetic-lookup-and-dimensional-openfoam-support-ready"))
		);
		return new CtCpJOpenFoamDimensionalSupportGateAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				scenarios,
				extrema(scenarios)
		);
	}

	public static OpenFoamDimensionalSupportSummary support(
			PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary lookupSupport,
			PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary dimensional,
			String sourceRuntimeInfo
	) {
		if (lookupSupport == null) {
			throw new IllegalArgumentException("OpenFOAM lookup support summary must not be null.");
		}
		if (dimensional == null) {
			throw new IllegalArgumentException("OpenFOAM dimensional residual summary must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		boolean ready = lookupSupport.cfdLookupSupportReady()
				&& dimensional.openFoamDimensionalResidualReady();
		int supportedTargets = ready
				? Math.min(lookupSupport.cfdSupportedLookupTargetCount(),
						dimensional.expectedOpenFoamDimensionalResultCaseCount())
				: 0;
		return new OpenFoamDimensionalSupportSummary(
				lookupSupport.lookupExecutionContractReady(),
				lookupSupport.cfdLookupSupportReady(),
				dimensional.openFoamDimensionalResidualReady(),
				dimensional.dimensionalResponseReferenceReady(),
				dimensional.openFoamCoefficientResultReady(),
				lookupSupport.openFoamSolverQualityContractReady(),
				dimensional.externalDimensionalExtractionReady(),
				lookupSupport.expectedLookupTargetCount(),
				lookupSupport.cfdSupportedLookupTargetCount(),
				lookupSupport.cfdGeometryUnsupportedLookupTargetCount(),
				dimensional.expectedDimensionalReferenceCount(),
				dimensional.readyDimensionalReferenceCount(),
				dimensional.expectedOpenFoamDimensionalResultCaseCount(),
				dimensional.observedResultCount(),
				dimensional.missingResultCount(),
				dimensional.failedResultCount(),
				supportedTargets,
				dimensional.maxThrustResidualToReference(),
				dimensional.maxInducedVelocityResidualToReference(),
				ready,
				false,
				false,
				false,
				ready ? "READY" : "BLOCKED",
				messageFor(lookupSupport, dimensional),
				sourceRuntimeInfo
		);
	}

	private static PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary lookupSupport(
			PropellerArchiveCtCpJOpenFoamLookupSupportGate.CtCpJOpenFoamLookupSupportGateAudit audit,
			String scenarioName
	) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary
			dimensionalResidual(
					PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.CtCpJOpenFoamDimensionalResidualContractAudit
							audit,
					String scenarioName
			) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static OpenFoamDimensionalSupportExtrema extrema(List<OpenFoamDimensionalSupportScenario> scenarios) {
		int ready = 0;
		int executionBlocked = 0;
		int maxSupported = 0;
		int maxLookupSupported = 0;
		int maxGeometryUnsupported = 0;
		int maxMissing = 0;
		int maxFailed = 0;
		double maxThrust = 0.0;
		double maxInduced = 0.0;
		int referenceAuthority = 0;
		int runtime = 0;
		int gameplay = 0;
		for (OpenFoamDimensionalSupportScenario scenario : scenarios) {
			OpenFoamDimensionalSupportSummary summary = scenario.summary();
			if (summary.cfdDimensionalSupportReady()) {
				ready++;
			}
			if (!summary.lookupExecutionContractReady()
					&& "lookup-execution-contract-not-ready".equals(summary.message())) {
				executionBlocked++;
			}
			maxSupported = Math.max(maxSupported, summary.supportedDimensionalTargetCount());
			maxLookupSupported = Math.max(maxLookupSupported, summary.cfdSupportedLookupTargetCount());
			maxGeometryUnsupported = Math.max(maxGeometryUnsupported,
					summary.cfdGeometryUnsupportedLookupTargetCount());
			maxMissing = Math.max(maxMissing, summary.dimensionalMissingResultCount());
			maxFailed = Math.max(maxFailed, summary.dimensionalFailedResultCount());
			maxThrust = Math.max(maxThrust, summary.maxThrustResidualToReference());
			maxInduced = Math.max(maxInduced, summary.maxInducedVelocityResidualToReference());
			if (summary.referenceExportAuthorityAllowed()) {
				referenceAuthority++;
			}
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new OpenFoamDimensionalSupportExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				executionBlocked,
				maxSupported,
				maxLookupSupported,
				maxGeometryUnsupported,
				maxMissing,
				maxFailed,
				maxThrust,
				maxInduced,
				referenceAuthority,
				runtime,
				gameplay
		);
	}

	private static String messageFor(
			PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary lookupSupport,
			PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary dimensional
	) {
		if (!lookupSupport.cfdLookupSupportReady()) {
			if ("lookup-execution-contract-not-ready".equals(lookupSupport.message())) {
				return "lookup-execution-contract-not-ready";
			}
			if (!lookupSupport.lookupAcceptanceReady()) {
				return "lookup-support-not-ready";
			}
			if (!lookupSupport.openFoamResultContractReady()) {
				return "openfoam-coefficient-support-not-ready";
			}
			if (!lookupSupport.openFoamSolverQualityContractReady()) {
				return "openfoam-solver-quality-not-ready";
			}
			return "openfoam-lookup-support-not-ready";
		}
		if (!dimensional.openFoamDimensionalResidualReady()) {
			if (!dimensional.dimensionalResponseReferenceReady()) {
				return "dimensional-response-reference-not-ready";
			}
			if (!dimensional.openFoamCoefficientResultReady()) {
				return "openfoam-coefficient-result-contract-not-ready";
			}
			if (!dimensional.externalDimensionalExtractionReady()) {
				return "openfoam-dimensional-extraction-not-ready";
			}
			if (dimensional.missingResultCount() > 0) {
				return "openfoam-dimensional-results-missing";
			}
			if (dimensional.failedResultCount() > 0) {
				return "openfoam-dimensional-residual-gate-failed";
			}
			return "openfoam-dimensional-residual-contract-not-ready";
		}
		return "openfoam-dimensional-support-ready";
	}
}
