package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJOpenFoamLookupSupportGate {
	public static final String SOURCE_ID = "User-Propeller-Archive-CT-CP-J-OpenFOAM-Lookup-Support-Gate-Packet";
	public static final String CAVEAT =
			"OpenFOAM lookup support opens only when reviewed CT/CP/J lookup acceptance and compact OpenFOAM residual results both pass; CFD evidence cannot replace wind-tunnel acceptance and cannot mutate runtime or gameplay tuning.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int SCENARIO_SAMPLE_COUNT = 5;
	public static final int SCENARIO_METRIC_ROW_COUNT = 19;
	public static final int SUMMARY_ROW_COUNT = 11;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveCtCpJOpenFoamLookupSupportGate() {
	}

	public record OpenFoamLookupSupportSummary(
			boolean lookupAcceptanceReady,
			boolean openFoamResultContractReady,
			int expectedLookupTargetCount,
			int expectedOpenFoamResultCaseCount,
			int acceptedLookupTargetCount,
			int failedLookupTargetCount,
			int missingLookupResultCount,
			int cfdPassedResultCount,
			int cfdMissingResultCount,
			int cfdFailedResultCount,
			int cfdSupportedLookupTargetCount,
			int cfdGeometryUnsupportedLookupTargetCount,
			boolean cfdLookupSupportReady,
			boolean referenceExportAuthorityAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record OpenFoamLookupSupportScenario(
			String scenarioName,
			OpenFoamLookupSupportSummary summary
	) {
	}

	public record OpenFoamLookupSupportExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxAcceptedLookupTargetCount,
			int maxCfdSupportedLookupTargetCount,
			int maxCfdGeometryUnsupportedLookupTargetCount,
			int maxCfdMissingResultCount,
			int maxCfdFailedResultCount,
			int referenceExportAuthorityAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJOpenFoamLookupSupportGateAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<OpenFoamLookupSupportScenario> scenarios,
			OpenFoamLookupSupportExtrema extrema
	) {
		public CtCpJOpenFoamLookupSupportGateAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJOpenFoamLookupSupportGateAudit audit() {
		PropellerArchiveCtCpJLookupAcceptanceGate.CtCpJLookupAcceptanceAudit lookupAudit =
				PropellerArchiveCtCpJLookupAcceptanceGate.audit();
		PropellerArchiveCtCpJOpenFoamResultContract.CtCpJOpenFoamResultContractAudit cfdAudit =
				PropellerArchiveCtCpJOpenFoamResultContract.audit();
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary currentLookup =
				lookupAcceptance(lookupAudit, "current_no_reviewed_import_no_results");
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary acceptedLookup =
				lookupAcceptance(lookupAudit, "synthetic_all_lookup_targets_pass");
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary failedLookup =
				lookupAcceptance(lookupAudit, "synthetic_one_lookup_result_failed");
		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary currentCfd =
				cfdContract(cfdAudit, "current_no_reviewed_import_no_openfoam_results");
		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary missingCfd =
				cfdContract(cfdAudit, "reviewed_import_openfoam_ready_results_missing");
		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary readyCfd =
				cfdContract(cfdAudit, "synthetic_openfoam_results_all_pass");
		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary failedCfd =
				cfdContract(cfdAudit, "synthetic_openfoam_result_failed");
		List<OpenFoamLookupSupportScenario> scenarios = List.of(
				new OpenFoamLookupSupportScenario(
						"current_lookup_and_cfd_blocked",
						support(currentLookup, currentCfd, "current-ct-cp-j-openfoam-lookup-support-blocked")),
				new OpenFoamLookupSupportScenario(
						"lookup_ready_cfd_results_missing",
						support(acceptedLookup, missingCfd, "synthetic-lookup-ready-cfd-results-missing")),
				new OpenFoamLookupSupportScenario(
						"cfd_ready_lookup_acceptance_failed",
						support(failedLookup, readyCfd, "synthetic-cfd-ready-lookup-acceptance-failed")),
				new OpenFoamLookupSupportScenario(
						"lookup_ready_cfd_residual_failed",
						support(acceptedLookup, failedCfd, "synthetic-lookup-ready-cfd-residual-failed")),
				new OpenFoamLookupSupportScenario(
						"lookup_and_cfd_ready",
						support(acceptedLookup, readyCfd, "synthetic-lookup-and-cfd-support-ready"))
		);
		return new CtCpJOpenFoamLookupSupportGateAudit(
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

	public static OpenFoamLookupSupportSummary support(
			PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary lookup,
			PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary cfd,
			String sourceRuntimeInfo
	) {
		if (lookup == null) {
			throw new IllegalArgumentException("lookup acceptance summary must not be null.");
		}
		if (cfd == null) {
			throw new IllegalArgumentException("OpenFOAM result contract summary must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		boolean ready = lookup.lookupAcceptanceReady() && cfd.openFoamResultContractReady();
		int geometryUnsupported = lookup.expectedTargetCount() - cfd.expectedOpenFoamResultCaseCount();
		int supportedTargets = ready ? cfd.expectedOpenFoamResultCaseCount() : 0;
		return new OpenFoamLookupSupportSummary(
				lookup.lookupAcceptanceReady(),
				cfd.openFoamResultContractReady(),
				lookup.expectedTargetCount(),
				cfd.expectedOpenFoamResultCaseCount(),
				lookup.passedResultCount(),
				lookup.failedResultCount(),
				lookup.missingResultCount(),
				cfd.passedResultCount(),
				cfd.missingResultCount(),
				cfd.failedResultCount(),
				supportedTargets,
				geometryUnsupported,
				ready,
				false,
				false,
				false,
				ready ? "READY" : "BLOCKED",
				messageFor(lookup, cfd),
				sourceRuntimeInfo
		);
	}

	private static PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary lookupAcceptance(
			PropellerArchiveCtCpJLookupAcceptanceGate.CtCpJLookupAcceptanceAudit audit,
			String scenarioName
	) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary cfdContract(
			PropellerArchiveCtCpJOpenFoamResultContract.CtCpJOpenFoamResultContractAudit audit,
			String scenarioName
	) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static OpenFoamLookupSupportExtrema extrema(List<OpenFoamLookupSupportScenario> scenarios) {
		int ready = 0;
		int maxAccepted = 0;
		int maxSupported = 0;
		int maxUnsupported = 0;
		int maxMissing = 0;
		int maxFailed = 0;
		int referenceAuthority = 0;
		int runtime = 0;
		int gameplay = 0;
		for (OpenFoamLookupSupportScenario scenario : scenarios) {
			OpenFoamLookupSupportSummary summary = scenario.summary();
			if (summary.cfdLookupSupportReady()) {
				ready++;
			}
			maxAccepted = Math.max(maxAccepted, summary.acceptedLookupTargetCount());
			maxSupported = Math.max(maxSupported, summary.cfdSupportedLookupTargetCount());
			maxUnsupported = Math.max(maxUnsupported, summary.cfdGeometryUnsupportedLookupTargetCount());
			maxMissing = Math.max(maxMissing, summary.cfdMissingResultCount());
			maxFailed = Math.max(maxFailed, summary.cfdFailedResultCount());
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
		return new OpenFoamLookupSupportExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxAccepted,
				maxSupported,
				maxUnsupported,
				maxMissing,
				maxFailed,
				referenceAuthority,
				runtime,
				gameplay
		);
	}

	private static String messageFor(
			PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary lookup,
			PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary cfd
	) {
		if (!lookup.lookupAcceptanceReady()) {
			return "lookup-acceptance-not-ready";
		}
		if (!cfd.openFoamResultContractReady()) {
			if (cfd.missingResultCount() > 0) {
				return "openfoam-results-missing";
			}
			if (cfd.failedResultCount() > 0) {
				return "openfoam-residual-gate-failed";
			}
			return "openfoam-result-contract-not-ready";
		}
		return "openfoam-lookup-support-ready";
	}
}
