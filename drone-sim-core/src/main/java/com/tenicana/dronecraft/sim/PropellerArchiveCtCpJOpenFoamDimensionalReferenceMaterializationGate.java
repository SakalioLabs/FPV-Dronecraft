package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-OpenFOAM-Dimensional-Reference-Materialization-Gate-Packet";
	public static final String CAVEAT =
			"OpenFOAM dimensional reference materialization requires both reviewed CT/CP/J lookup reference rows and reviewed OpenFOAM dimensional reference handoff rows before any SI CFD reference table can materialize; current rows stay blocked and runtime coupling/gameplay auto-apply stay closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int MATERIALIZATION_SCENARIO_ROW_COUNT = 5;
	public static final int SUMMARY_ROW_COUNT = 20;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ MATERIALIZATION_SCENARIO_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String NEXT_REQUIRED_ACTION =
			"complete-ct-cp-j-lookup-reference-review-before-openfoam-materialization";
	public static final String MATERIALIZE_REFERENCE_ACTION =
			"materialize-openfoam-dimensional-reference-table-after-review";

	private PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate() {
	}

	public record OpenFoamDimensionalReferenceMaterializationScenario(
			String scenarioName,
			String lookupReferenceReviewScenarioName,
			String openFoamDimensionalHandoffScenarioName,
			boolean lookupReferenceReviewReady,
			boolean lookupReferenceMaterialExportAllowed,
			boolean openFoamDimensionalHandoffReady,
			boolean openFoamDimensionalSupportReady,
			boolean openFoamSolverQualityContractReady,
			boolean openFoamCoefficientLookupShapeGuardReady,
			int lookupPerformanceReferenceRowAvailableCount,
			int lookupFullSimulationReferenceRowAvailableCount,
			int expectedOpenFoamReferenceRowCount,
			int openFoamReferenceRowAvailableCount,
			int blockedOpenFoamReferenceRowCount,
			int openFoamSolverQualityBlockerCount,
			boolean referenceMaterializationReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String nextRequiredAction,
			String message
	) {
	}

	public record OpenFoamDimensionalReferenceMaterializationSummary(
			int scenarioCount,
			int materializationReadyScenarioCount,
			int blockedScenarioCount,
			int maxLookupPerformanceReferenceRowAvailableCount,
			int maxLookupFullSimulationReferenceRowAvailableCount,
			int maxExpectedOpenFoamReferenceRowCount,
			int maxOpenFoamReferenceRowAvailableCount,
			int maxBlockedOpenFoamReferenceRowCount,
			int currentLookupPerformanceReferenceRowAvailableCount,
			int currentOpenFoamReferenceRowAvailableCount,
			int currentBlockedOpenFoamReferenceRowCount,
			int openFoamDimensionalHandoffReadyScenarioCount,
			int lookupReferenceReviewReadyScenarioCount,
			int maxOpenFoamSolverQualityBlockerCount,
			int openFoamSolverQualityBlockedScenarioCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String firstMaterializationReadyScenario,
			String currentStatus,
			String nextRequiredAction
	) {
	}

	public record CtCpJOpenFoamDimensionalReferenceMaterializationGateAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int materializationScenarioRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<OpenFoamDimensionalReferenceMaterializationScenario> scenarios,
			OpenFoamDimensionalReferenceMaterializationSummary summary
	) {
		public CtCpJOpenFoamDimensionalReferenceMaterializationGateAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJOpenFoamDimensionalReferenceMaterializationGateAudit audit() {
		List<OpenFoamDimensionalReferenceMaterializationScenario> scenarios = List.of(
				scenario("current_lookup_and_openfoam_blocked",
						lookup("current_result_seeds_unavailable_reference_blocked"),
						openFoam("current_dimensional_support_blocked")),
				scenario("lookup_reference_pending_results",
						lookup("authorized_runs_pending_results_reference_blocked"),
						openFoam("current_dimensional_support_blocked")),
				scenario("openfoam_ready_lookup_reference_review_missing",
						lookup("acceptance_handoff_ready_reference_review_missing"),
						openFoam("dimensional_support_ready_reference_reviewed")),
				scenario("lookup_reference_ready_openfoam_review_missing",
						lookup("acceptance_handoff_ready_reference_reviewed"),
						openFoam("dimensional_support_ready_reference_review_missing")),
				scenario("lookup_reference_and_openfoam_ready",
						lookup("acceptance_handoff_ready_reference_reviewed"),
						openFoam("dimensional_support_ready_reference_reviewed"))
		);
		return new CtCpJOpenFoamDimensionalReferenceMaterializationGateAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				MATERIALIZATION_SCENARIO_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				scenarios,
				summary(scenarios)
		);
	}

	public static OpenFoamDimensionalReferenceMaterializationScenario scenario(String scenarioName) {
		return audit().scenarios()
				.stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J OpenFOAM dimensional reference materialization scenario: " + scenarioName));
	}

	public static OpenFoamDimensionalReferenceMaterializationScenario scenario(
			String scenarioName,
			PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario lookup,
			PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary openFoam
	) {
		return scenario(scenarioName, lookup, openFoam == null ? "" : openFoam.sourceRuntimeInfo(), openFoam);
	}

	private static OpenFoamDimensionalReferenceMaterializationScenario scenario(
			String scenarioName,
			PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario lookup,
			PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffScenario openFoam
	) {
		return scenario(scenarioName, lookup, openFoam == null ? "" : openFoam.scenarioName(),
				openFoam == null ? null : openFoam.summary());
	}

	private static OpenFoamDimensionalReferenceMaterializationScenario scenario(
			String scenarioName,
			PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario lookup,
			String openFoamScenarioName,
			PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary openFoam
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (openFoamScenarioName == null || openFoamScenarioName.isBlank()) {
			throw new IllegalArgumentException("OpenFOAM dimensional handoff scenario name must not be blank.");
		}
		if (lookup == null || openFoam == null) {
			throw new IllegalArgumentException("lookup reference readiness and OpenFOAM handoff are required.");
		}
		boolean ready = lookup.referenceMaterialExportAllowed() && openFoam.referenceMaterialExportAllowed();
		int availableRows = ready ? openFoam.openFoamDimensionalReferenceRowAvailableCount() : 0;
		int blockedRows = Math.max(0, openFoam.expectedReferenceRowCount() - availableRows);
		return new OpenFoamDimensionalReferenceMaterializationScenario(
				scenarioName,
				lookup.scenarioName(),
				openFoamScenarioName,
				lookup.referenceReviewReady(),
				lookup.referenceMaterialExportAllowed(),
				openFoam.referenceMaterialExportAllowed(),
				openFoam.dimensionalSupportReady(),
				openFoam.openFoamSolverQualityContractReady(),
				openFoam.openFoamCoefficientLookupShapeGuardReady(),
				lookup.performanceReferenceRowAvailableCount(),
				lookup.fullSimulationReferenceRowAvailableCount(),
				openFoam.expectedReferenceRowCount(),
				availableRows,
				blockedRows,
				openFoam.openFoamSolverQualityBlockerCount(),
				ready,
				false,
				false,
				ready ? "MATERIALIZATION_READY" : "BLOCKED",
				nextAction(lookup, openFoam),
				message(lookup, openFoam)
		);
	}

	private static OpenFoamDimensionalReferenceMaterializationSummary summary(
			List<OpenFoamDimensionalReferenceMaterializationScenario> scenarios
	) {
		int ready = 0;
		int handoffReady = 0;
		int lookupReady = 0;
		int qualityBlocked = 0;
		int runtime = 0;
		int gameplay = 0;
		int maxLookupPerformance = 0;
		int maxLookupFull = 0;
		int maxExpected = 0;
		int maxAvailable = 0;
		int maxBlocked = 0;
		int maxQualityBlockers = 0;
		String firstReady = "";
		for (OpenFoamDimensionalReferenceMaterializationScenario scenario : scenarios) {
			if (scenario.referenceMaterializationReady()) {
				ready++;
				if (firstReady.isBlank()) {
					firstReady = scenario.scenarioName();
				}
			}
			if (scenario.openFoamDimensionalHandoffReady()) {
				handoffReady++;
			}
			if (scenario.lookupReferenceReviewReady()) {
				lookupReady++;
			}
			if (scenario.openFoamSolverQualityBlockerCount() > 0) {
				qualityBlocked++;
			}
			if (scenario.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (scenario.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxLookupPerformance = Math.max(maxLookupPerformance,
					scenario.lookupPerformanceReferenceRowAvailableCount());
			maxLookupFull = Math.max(maxLookupFull, scenario.lookupFullSimulationReferenceRowAvailableCount());
			maxExpected = Math.max(maxExpected, scenario.expectedOpenFoamReferenceRowCount());
			maxAvailable = Math.max(maxAvailable, scenario.openFoamReferenceRowAvailableCount());
			maxBlocked = Math.max(maxBlocked, scenario.blockedOpenFoamReferenceRowCount());
			maxQualityBlockers = Math.max(maxQualityBlockers, scenario.openFoamSolverQualityBlockerCount());
		}
		OpenFoamDimensionalReferenceMaterializationScenario current = scenarios.get(0);
		return new OpenFoamDimensionalReferenceMaterializationSummary(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxLookupPerformance,
				maxLookupFull,
				maxExpected,
				maxAvailable,
				maxBlocked,
				current.lookupPerformanceReferenceRowAvailableCount(),
				current.openFoamReferenceRowAvailableCount(),
				current.blockedOpenFoamReferenceRowCount(),
				handoffReady,
				lookupReady,
				maxQualityBlockers,
				qualityBlocked,
				runtime,
				gameplay,
				firstReady,
				current.status(),
				current.nextRequiredAction()
		);
	}

	private static String nextAction(
			PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario lookup,
			PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary openFoam
	) {
		if (!lookup.referenceMaterialExportAllowed()) {
			return lookup.nextRequiredAction();
		}
		if (!openFoam.referenceMaterialExportAllowed()) {
			return openFoam.openFoamSolverQualityBlockerCount() > 0
					? openFoam.openFoamSolverQualityNextRequiredAction()
					: "review-openfoam-dimensional-reference-before-materialization";
		}
		return MATERIALIZE_REFERENCE_ACTION;
	}

	private static String message(
			PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario lookup,
			PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary openFoam
	) {
		if (!lookup.referenceMaterialExportAllowed()) {
			return "OpenFOAM materialization blocked by CT/CP/J lookup reference readiness: " + lookup.message();
		}
		if (!openFoam.referenceMaterialExportAllowed()) {
			return "OpenFOAM materialization blocked by dimensional reference handoff: " + openFoam.message();
		}
		return "OpenFOAM dimensional reference table materialization is ready";
	}

	private static PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario
			lookup(String scenarioName) {
		return switch (scenarioName) {
			case "current_result_seeds_unavailable_reference_blocked" -> lookupScenario(
					scenarioName,
					"current_result_seeds_unavailable",
					false,
					false,
					false,
					false,
					false,
					0,
					0,
					9,
					0,
					9,
					false,
					false,
					0,
					0,
					0,
					"BLOCKED",
					"execute-clearance-evidence-ledger-before-reviewed-payload-output",
					"reference review blocked by lookup execution acceptance handoff: lookup execution acceptance is blocked");
			case "authorized_runs_pending_results_reference_blocked" -> lookupScenario(
					scenarioName,
					"authorized_runs_pending_results",
					false,
					false,
					false,
					true,
					true,
					0,
					9,
					0,
					0,
					9,
					false,
					false,
					0,
					0,
					0,
					"BLOCKED",
					"record-lookup-execution-result-evidence-before-acceptance",
					"reference review blocked by lookup execution acceptance handoff: waiting for CT/CP/J result evidence");
			case "acceptance_handoff_ready_reference_review_missing" -> lookupScenario(
					scenarioName,
					"synthetic_all_result_seeds_ready",
					true,
					false,
					true,
					true,
					true,
					9,
					0,
					0,
					9,
					0,
					false,
					false,
					0,
					0,
					0,
					"BLOCKED",
					"review-accepted-ct-cp-j-results-before-reference-export",
					"accepted CT/CP/J lookup results require compact reference review before export");
			case "acceptance_handoff_ready_reference_reviewed" -> lookupScenario(
					scenarioName,
					"synthetic_all_result_seeds_ready",
					true,
					true,
					true,
					true,
					true,
					9,
					0,
					0,
					9,
					0,
					true,
					true,
					9,
					6,
					3,
					"REVIEW_READY",
					"materialize-ct-cp-j-lookup-reference-table-after-review",
					"CT/CP/J lookup reference review is ready for table materialization");
			default -> throw new IllegalArgumentException(
					"unknown CT/CP/J lookup reference review readiness scenario: " + scenarioName);
		};
	}

	private static PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario
			lookupScenario(
					String scenarioName,
					String acceptanceHandoffScenarioName,
					boolean acceptanceHandoffReady,
					boolean compactReferenceReviewed,
					boolean lookupAcceptanceReady,
					boolean lookupExecutionContractReady,
					boolean lookupInterpolationExecuted,
					int readySeedCount,
					int pendingSeedCount,
					int unavailableSeedCount,
					int acceptedLookupTargetCount,
					int blockedLookupTargetCount,
					boolean referenceReviewReady,
					boolean referenceMaterialExportAllowed,
					int performanceReferenceRowAvailableCount,
					int fullSimulationReferenceRowAvailableCount,
					int performanceOnlyReferenceRowAvailableCount,
					String status,
					String nextRequiredAction,
					String message
			) {
		return new PropellerArchiveCtCpJLookupReferenceReviewReadinessGate.LookupReferenceReviewReadinessScenario(
				scenarioName,
				acceptanceHandoffScenarioName,
				acceptanceHandoffReady,
				compactReferenceReviewed,
				lookupAcceptanceReady,
				lookupExecutionContractReady,
				lookupInterpolationExecuted,
				9,
				PropellerArchiveCtCpJLookupReferenceHandoff.REFERENCE_FIELD_ROW_COUNT,
				PropellerArchiveCtCpJLookupReferenceHandoff.REFERENCE_FIELD_ROW_COUNT,
				readySeedCount,
				pendingSeedCount,
				unavailableSeedCount,
				acceptedLookupTargetCount,
				blockedLookupTargetCount,
				true,
				referenceReviewReady,
				referenceMaterialExportAllowed,
				performanceReferenceRowAvailableCount,
				fullSimulationReferenceRowAvailableCount,
				performanceOnlyReferenceRowAvailableCount,
				false,
				false,
				status,
				nextRequiredAction,
				message
		);
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
			.OpenFoamDimensionalReferenceHandoffScenario openFoam(String scenarioName) {
		return switch (scenarioName) {
			case "current_dimensional_support_blocked" -> new PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
					.OpenFoamDimensionalReferenceHandoffScenario(
							scenarioName,
							openFoamSummary(false, false, false, false, false, 4, 0, 6, false, 2, 9,
									0.00027500814692071884, 0.000071, false,
									"BLOCKED", "openfoam-dimensional-lookup-support-not-ready",
									"audit-only-openfoam-dimensional-reference-handoff"));
			case "dimensional_support_ready_reference_review_missing" -> new PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
					.OpenFoamDimensionalReferenceHandoffScenario(
							scenarioName,
							openFoamSummary(true, true, false, true, true, 0, 6, 0, true, 6, 0,
									0.0, 0.0, false,
									"BLOCKED", "openfoam-dimensional-reference-review-missing",
									"synthetic-openfoam-dimensional-support-ready-review-missing"));
			case "dimensional_support_ready_reference_reviewed" -> new PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
					.OpenFoamDimensionalReferenceHandoffScenario(
							scenarioName,
							openFoamSummary(true, true, true, true, true, 0, 6, 0, true, 6, 0,
									0.0, 0.0, true,
									"READY", "openfoam-dimensional-reference-material-ready",
									"synthetic-openfoam-dimensional-reference-handoff-ready"));
			default -> throw new IllegalArgumentException(
					"unknown OpenFOAM dimensional reference handoff scenario: " + scenarioName);
		};
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary
			openFoamSummary(
					boolean lookupExecutionContractReady,
					boolean dimensionalSupportReady,
					boolean dimensionalReferenceReviewed,
					boolean lookupSupportReady,
					boolean dimensionalResidualReady,
					int openFoamSolverQualityBlockerCount,
					int supportedRows,
					int blockedRows,
					boolean coefficientShapeGuardReady,
					int archiveCurveShapeGuardInheritedReferenceCount,
					int negativeThrustTailReferenceCount,
					double maxArchiveCurveEtaFormulaResidual,
					double maxArchiveCurveCtIncrease,
					boolean referenceMaterialExportAllowed,
					String status,
					String message,
					String sourceRuntimeInfo
			) {
		return new PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceHandoffSummary(
				lookupExecutionContractReady,
				dimensionalSupportReady,
				dimensionalReferenceReviewed,
				lookupSupportReady,
				dimensionalResidualReady,
				openFoamSolverQualityBlockerCount == 0,
				openFoamSolverQualityBlockerCount,
				openFoamSolverQualityBlockerCount == 0
						? "openfoam-solver-quality-blockers-clear"
						: "review-openfoam-mesh-yplus-and-time-step-against-run-setup",
				6,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.REFERENCE_FIELD_ROW_COUNT,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.REFERENCE_FIELD_ROW_COUNT,
				supportedRows,
				blockedRows,
				coefficientShapeGuardReady,
				5,
				1,
				9,
				0.00027500814692071884,
				0.000071,
				archiveCurveShapeGuardInheritedReferenceCount,
				negativeThrustTailReferenceCount,
				maxArchiveCurveEtaFormulaResidual,
				maxArchiveCurveCtIncrease,
				true,
				referenceMaterialExportAllowed,
				referenceMaterialExportAllowed ? 6 : 0,
				false,
				false,
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.REFERENCE_PAYLOAD_KIND,
				status,
				message,
				dimensionalSupportReady ? "READY" : "BLOCKED",
				sourceRuntimeInfo
		);
	}
}
