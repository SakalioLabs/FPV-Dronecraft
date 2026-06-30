package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJOpenFoamLookupSupportGate {
	public static final String SOURCE_ID = "User-Propeller-Archive-CT-CP-J-OpenFOAM-Lookup-Support-Gate-Packet";
	public static final String CAVEAT =
			"OpenFOAM lookup support opens only when handoff-aware CT/CP/J lookup execution with inherited archive curve-shape diagnostics, reviewed lookup acceptance, compact solver-quality QA, and compact OpenFOAM residual results all pass; CFD evidence cannot replace wind-tunnel acceptance and cannot mutate runtime or gameplay tuning.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 10;
	public static final int SCENARIO_SAMPLE_COUNT = 6;
	public static final int SCENARIO_METRIC_ROW_COUNT = 29;
	public static final int SUMMARY_ROW_COUNT = 20;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveCtCpJOpenFoamLookupSupportGate() {
	}

	public record OpenFoamLookupSupportSummary(
			boolean lookupAcceptanceReady,
			boolean lookupExecutionContractReady,
			boolean openFoamResultContractReady,
			boolean openFoamSolverQualityContractReady,
			int openFoamSolverQualityBlockerCount,
			String openFoamSolverQualityNextRequiredAction,
			boolean lookupExecutionArchiveCurveShapeGuardReady,
			int lookupExecutionArchiveCurveShapeGuardInheritedScenarioCount,
			int lookupExecutionArchiveCurveShapeGuardBlockedScenarioCount,
			int maxNegativeThrustTailExecutionInputRowCount,
			double maxArchiveCurveEtaFormulaResidual,
			double maxArchiveCurveCtIncrease,
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
			int lookupExecutionBlockedScenarioCount,
			int maxAcceptedLookupTargetCount,
			int maxCfdSupportedLookupTargetCount,
			int maxCfdGeometryUnsupportedLookupTargetCount,
			int maxCfdMissingResultCount,
			int maxCfdFailedResultCount,
			int maxOpenFoamSolverQualityBlockerCount,
			int openFoamSolverQualityBlockerScenarioCount,
			int lookupExecutionArchiveCurveShapeGuardReadyScenarioCount,
			int maxLookupExecutionArchiveCurveShapeGuardInheritedScenarioCount,
			int maxLookupExecutionArchiveCurveShapeGuardBlockedScenarioCount,
			int maxNegativeThrustTailExecutionInputRowCount,
			double maxArchiveCurveEtaFormulaResidual,
			double maxArchiveCurveCtIncrease,
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
		PropellerArchiveCtCpJOpenFoamSolverQualityContract.CtCpJOpenFoamSolverQualityContractAudit qualityAudit =
				PropellerArchiveCtCpJOpenFoamSolverQualityContract.audit();
		PropellerArchiveCtCpJLookupExecutionContract.CtCpJLookupExecutionContractAudit executionAudit =
				PropellerArchiveCtCpJLookupExecutionContract.audit();
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary currentLookup =
				lookupAcceptance(lookupAudit, "current_no_reviewed_import_no_results");
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary executionBlockedLookup =
				lookupAcceptance(lookupAudit, "reviewed_import_policy_ready_execution_blocked");
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
		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary currentQuality =
				solverQualityContract(qualityAudit, "current_no_case_hash_no_solver_quality");
		PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary readyQuality =
				solverQualityContract(qualityAudit, "synthetic_solver_quality_all_pass");
		List<OpenFoamLookupSupportScenario> scenarios = List.of(
				new OpenFoamLookupSupportScenario(
						"current_lookup_and_cfd_blocked",
						support(currentLookup, currentCfd, currentQuality, executionAudit.summary(),
								"current-ct-cp-j-openfoam-lookup-support-blocked")),
				new OpenFoamLookupSupportScenario(
						"lookup_execution_blocked_cfd_ready",
						support(executionBlockedLookup, readyCfd, readyQuality, executionAudit.summary(),
								"synthetic-lookup-execution-blocked-cfd-ready")),
				new OpenFoamLookupSupportScenario(
						"lookup_ready_cfd_results_missing",
						support(acceptedLookup, missingCfd, readyQuality, executionAudit.summary(),
								"synthetic-lookup-ready-cfd-results-missing")),
				new OpenFoamLookupSupportScenario(
						"cfd_ready_lookup_acceptance_failed",
						support(failedLookup, readyCfd, readyQuality, executionAudit.summary(),
								"synthetic-cfd-ready-lookup-acceptance-failed")),
				new OpenFoamLookupSupportScenario(
						"lookup_ready_cfd_residual_failed",
						support(acceptedLookup, failedCfd, readyQuality, executionAudit.summary(),
								"synthetic-lookup-ready-cfd-residual-failed")),
				new OpenFoamLookupSupportScenario(
						"lookup_and_cfd_ready",
						support(acceptedLookup, readyCfd, readyQuality, executionAudit.summary(),
								"synthetic-lookup-and-cfd-support-ready"))
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
			PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary solverQuality,
			String sourceRuntimeInfo
	) {
		return support(lookup, cfd, solverQuality,
				PropellerArchiveCtCpJLookupExecutionContract.audit().summary(), sourceRuntimeInfo);
	}

	public static OpenFoamLookupSupportSummary support(
			PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary lookup,
			PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary cfd,
			PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary solverQuality,
			PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionSummary lookupExecution,
			String sourceRuntimeInfo
	) {
		if (lookup == null) {
			throw new IllegalArgumentException("lookup acceptance summary must not be null.");
		}
		if (cfd == null) {
			throw new IllegalArgumentException("OpenFOAM result contract summary must not be null.");
		}
		if (solverQuality == null) {
			throw new IllegalArgumentException("OpenFOAM solver quality summary must not be null.");
		}
		if (lookupExecution == null) {
			throw new IllegalArgumentException("lookup execution summary must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport.OpenFoamSolverQualityBlockerScenario
				qualityBlocker =
						PropellerArchiveCtCpJOpenFoamSolverQualityBlockerReport.scenario(
								"lookup_support_solver_quality", solverQuality);
		boolean shapeGuardReady = lookup.lookupExecutionContractReady()
				&& lookupExecution.archiveCurveShapeGuardInheritedScenarioCount() > 0
				&& lookupExecution.maxArchiveCurveEtaFormulaResidual()
						<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL
				&& lookupExecution.maxArchiveCurveCtIncrease()
						<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE;
		boolean ready = lookup.lookupExecutionContractReady()
				&& lookup.lookupAcceptanceReady()
				&& shapeGuardReady
				&& solverQuality.openFoamSolverQualityContractReady()
				&& qualityBlocker.blockerCount() == 0
				&& cfd.openFoamResultContractReady();
		int geometryUnsupported = lookup.expectedTargetCount() - cfd.expectedOpenFoamResultCaseCount();
		int supportedTargets = ready ? cfd.expectedOpenFoamResultCaseCount() : 0;
		return new OpenFoamLookupSupportSummary(
				lookup.lookupAcceptanceReady(),
				lookup.lookupExecutionContractReady(),
				cfd.openFoamResultContractReady(),
				solverQuality.openFoamSolverQualityContractReady(),
				qualityBlocker.blockerCount(),
				qualityBlocker.nextRequiredAction(),
				shapeGuardReady,
				lookupExecution.archiveCurveShapeGuardInheritedScenarioCount(),
				lookupExecution.archiveCurveShapeGuardBlockedScenarioCount(),
				lookupExecution.maxNegativeThrustTailExecutionInputRowCount(),
				lookupExecution.maxArchiveCurveEtaFormulaResidual(),
				lookupExecution.maxArchiveCurveCtIncrease(),
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
				messageFor(lookup, cfd, solverQuality, shapeGuardReady),
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

	private static PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary
			solverQualityContract(
					PropellerArchiveCtCpJOpenFoamSolverQualityContract.CtCpJOpenFoamSolverQualityContractAudit audit,
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
		int executionBlocked = 0;
		int maxAccepted = 0;
		int maxSupported = 0;
		int maxUnsupported = 0;
		int maxMissing = 0;
		int maxFailed = 0;
		int maxQualityBlockers = 0;
		int qualityBlocked = 0;
		int shapeReady = 0;
		int maxShapeInherited = 0;
		int maxShapeBlocked = 0;
		int maxNegativeTail = 0;
		double maxShapeEta = 0.0;
		double maxShapeCt = 0.0;
		int referenceAuthority = 0;
		int runtime = 0;
		int gameplay = 0;
		for (OpenFoamLookupSupportScenario scenario : scenarios) {
			OpenFoamLookupSupportSummary summary = scenario.summary();
			if (summary.cfdLookupSupportReady()) {
				ready++;
			}
			if (!summary.lookupExecutionContractReady()
					&& "lookup-execution-contract-not-ready".equals(summary.message())) {
				executionBlocked++;
			}
			maxAccepted = Math.max(maxAccepted, summary.acceptedLookupTargetCount());
			maxSupported = Math.max(maxSupported, summary.cfdSupportedLookupTargetCount());
			maxUnsupported = Math.max(maxUnsupported, summary.cfdGeometryUnsupportedLookupTargetCount());
			maxMissing = Math.max(maxMissing, summary.cfdMissingResultCount());
			maxFailed = Math.max(maxFailed, summary.cfdFailedResultCount());
			maxQualityBlockers = Math.max(maxQualityBlockers,
					summary.openFoamSolverQualityBlockerCount());
			if (summary.openFoamSolverQualityBlockerCount() > 0) {
				qualityBlocked++;
			}
			if (summary.lookupExecutionArchiveCurveShapeGuardReady()) {
				shapeReady++;
			}
			maxShapeInherited = Math.max(maxShapeInherited,
					summary.lookupExecutionArchiveCurveShapeGuardInheritedScenarioCount());
			maxShapeBlocked = Math.max(maxShapeBlocked,
					summary.lookupExecutionArchiveCurveShapeGuardBlockedScenarioCount());
			maxNegativeTail = Math.max(maxNegativeTail,
					summary.maxNegativeThrustTailExecutionInputRowCount());
			maxShapeEta = Math.max(maxShapeEta, summary.maxArchiveCurveEtaFormulaResidual());
			maxShapeCt = Math.max(maxShapeCt, summary.maxArchiveCurveCtIncrease());
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
				executionBlocked,
				maxAccepted,
				maxSupported,
				maxUnsupported,
				maxMissing,
				maxFailed,
				maxQualityBlockers,
				qualityBlocked,
				shapeReady,
				maxShapeInherited,
				maxShapeBlocked,
				maxNegativeTail,
				maxShapeEta,
				maxShapeCt,
				referenceAuthority,
				runtime,
				gameplay
		);
	}

	private static String messageFor(
			PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary lookup,
			PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary cfd,
			PropellerArchiveCtCpJOpenFoamSolverQualityContract.OpenFoamSolverQualitySummary solverQuality,
			boolean shapeGuardReady
	) {
		if ("lookup-execution-contract-blocked".equals(lookup.message())) {
			return "lookup-execution-contract-not-ready";
		}
		if (!lookup.lookupAcceptanceReady()) {
			return "lookup-acceptance-not-ready";
		}
		if (!shapeGuardReady) {
			return "lookup-execution-archive-curve-shape-guard-not-ready";
		}
		if (!solverQuality.openFoamSolverQualityContractReady()) {
			return "openfoam-solver-quality-not-ready";
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
