package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Scattered-Surface-Fit-Execution-Handoff-Packet";
	public static final String CAVEAT =
			"Scattered surface fit execution handoff exposes lookup-execution input shape only after the scattered CT/CP/J surface fit contract is complete; it exports no raw archive rows and cannot mutate runtime physics or gameplay tuning.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int EXECUTION_INPUT_FIELD_ROW_COUNT = 14;
	public static final int EXECUTION_INPUT_ROW_COUNT = PropellerArchiveCtCpJScatteredSurfaceFitContract.TARGET_ROW_COUNT;
	public static final int SCENARIO_SAMPLE_COUNT = 5;
	public static final int SUMMARY_ROW_COUNT = 12;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ EXECUTION_INPUT_FIELD_ROW_COUNT
			+ EXECUTION_INPUT_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final List<ExecutionInputField> FIELDS = List.of(
			new ExecutionInputField("preset_name", "text", true,
					"scattered surface fit target", "join input row to DroneConfig preset", false, false),
			new ExecutionInputField("case_name", "text", true,
					"scattered surface fit target", "join input row to lookup query", false, false),
			new ExecutionInputField("source_archive_sha256", "sha256", true,
					"PropellerArchiveSourceFingerprint archive hash", "prove source identity", false, false),
			new ExecutionInputField("performance_match_id", "text", true,
					"reviewed propeller archive match", "trace CT/CP/J source", false, false),
			new ExecutionInputField("query_advance_ratio_j", "J", true,
					"lookup query envelope", "evaluate execution input at query J", false, false),
			new ExecutionInputField("query_rpm", "rpm", true,
					"lookup query envelope", "evaluate execution input at query RPM", false, false),
			new ExecutionInputField("execution_input_source_kind", "text", true,
					"surface fit contract", "separate static-anchor rows from fitted rows", false, false),
			new ExecutionInputField("minimum_performance_neighbor_rows", "count", true,
					"interpolation policy", "carry reviewed-neighbor requirement into execution", false, false),
			new ExecutionInputField("available_rectangular_neighbor_rows", "count", true,
					"archive grid coverage", "document why fitted rows were needed", false, false),
			new ExecutionInputField("fit_static_anchor_residual", "ratio", true,
					"surface fit validation", "preserve static anchor quality", false, false),
			new ExecutionInputField("fit_ct_holdout_residual", "ratio", true,
					"surface fit validation", "preserve CT fit quality", false, false),
			new ExecutionInputField("fit_cp_holdout_residual", "ratio", true,
					"surface fit validation", "preserve CP fit quality", false, false),
			new ExecutionInputField("fit_eta_consistency_residual", "ratio", true,
					"surface fit validation", "preserve eta closure quality", false, false),
			new ExecutionInputField("coefficient_payload_reviewed", "boolean", true,
					"manual fit review", "prevent synthetic rows from becoming runtime input", false, false)
	);

	private PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff() {
	}

	public record ExecutionInputField(
			String fieldName,
			String unit,
			boolean required,
			String source,
			String downstreamUse,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed
	) {
	}

	public record ExecutionInputHandoffRow(
			String presetName,
			String caseName,
			String performanceMatchId,
			double queryAdvanceRatioJ,
			double queryRpm,
			String executionInputSourceKind,
			int minimumPerformanceNeighborRows,
			int availableRectangularNeighborRows,
			boolean directNeighborBindingReady,
			boolean scatteredFitRequired,
			boolean postReviewFullSimulationLookupAllowed,
			boolean coefficientPayloadReviewed,
			boolean executionInputReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record ExecutionInputHandoffSummary(
			boolean sourceRowsReviewed,
			boolean scatteredSurfaceFitContractReady,
			boolean scatteredSurfaceFitRun,
			int expectedExecutionInputRowCount,
			int candidateExecutionInputRowCount,
			int exportedExecutionInputRowCount,
			int directNeighborCandidateRowCount,
			int scatteredSurfaceCandidateRowCount,
			int fullSimulationExecutionInputRowCount,
			int performanceOnlyExecutionInputRowCount,
			boolean executionInputHandoffReady,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record ExecutionInputHandoffScenario(
			String scenarioName,
			ExecutionInputHandoffSummary summary
	) {
	}

	public record ExecutionInputHandoffExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int maxCandidateExecutionInputRowCount,
			int maxExportedExecutionInputRowCount,
			int maxDirectNeighborCandidateRowCount,
			int maxScatteredSurfaceCandidateRowCount,
			int maxFullSimulationExecutionInputRowCount,
			int maxPerformanceOnlyExecutionInputRowCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJScatteredSurfaceFitExecutionHandoffAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int executionInputFieldRowCount,
			int executionInputRowCount,
			int scenarioSampleCount,
			int summaryRowCount,
			int methodRowCount,
			List<ExecutionInputField> fields,
			List<ExecutionInputHandoffRow> rows,
			List<ExecutionInputHandoffScenario> scenarios,
			ExecutionInputHandoffExtrema extrema
	) {
		public CtCpJScatteredSurfaceFitExecutionHandoffAudit {
			fields = List.copyOf(fields);
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJScatteredSurfaceFitExecutionHandoffAudit audit() {
		PropellerArchiveCtCpJScatteredSurfaceFitContract.CtCpJScatteredSurfaceFitContractAudit fitAudit =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.audit();
		List<ExecutionInputHandoffRow> rows = fitAudit.targets()
				.stream()
				.map(PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff::row)
				.toList();
		List<ExecutionInputHandoffScenario> scenarios = List.of(
				new ExecutionInputHandoffScenario(
						"current_source_review_blocked_no_execution_input",
						handoff(scenario(fitAudit, "current_source_review_blocked_no_surface_fit"),
								"current-scattered-fit-execution-handoff-blocked")),
				new ExecutionInputHandoffScenario(
						"static_neighbor_rows_only_no_complete_execution_input",
						handoff(scenario(fitAudit, "reviewed_static_neighbors_only_nonstatic_missing"),
								"synthetic-static-neighbor-input-only")),
				new ExecutionInputHandoffScenario(
						"surface_fit_results_missing_no_execution_input",
						handoff(scenario(fitAudit, "reviewed_surface_fit_results_missing"),
								"synthetic-surface-fit-results-missing")),
				new ExecutionInputHandoffScenario(
						"surface_fit_failed_no_execution_input",
						handoff(scenario(fitAudit, "reviewed_surface_fit_one_failed"),
								"synthetic-surface-fit-failed")),
				new ExecutionInputHandoffScenario(
						"surface_fit_ready_execution_input_handoff",
						handoff(scenario(fitAudit, "reviewed_surface_fit_all_pass"),
								"synthetic-surface-fit-ready-execution-input-handoff"))
		);
		return new CtCpJScatteredSurfaceFitExecutionHandoffAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				EXECUTION_INPUT_FIELD_ROW_COUNT,
				EXECUTION_INPUT_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				FIELDS,
				rows,
				scenarios,
				extrema(scenarios)
		);
	}

	public static ExecutionInputField field(String fieldName) {
		return FIELDS.stream()
				.filter(field -> field.fieldName().equals(fieldName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown scattered fit execution input field: " + fieldName));
	}

	public static ExecutionInputHandoffRow row(String presetName, String caseName) {
		return audit().rows()
				.stream()
				.filter(row -> row.presetName().equals(presetName) && row.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown scattered fit execution input row: " + presetName + " / " + caseName));
	}

	public static ExecutionInputHandoffSummary handoff(
			PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitSummary fit,
			String sourceRuntimeInfo
	) {
		if (fit == null) {
			throw new IllegalArgumentException("scattered surface fit summary must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		boolean ready = fit.scatteredSurfaceFitContractReady();
		int candidateRows = fit.passedResultCount();
		int exportedRows = ready ? fit.expectedTargetCount() : 0;
		int directCandidateRows = Math.min(candidateRows, fit.directNeighborTargetCount());
		int scatteredCandidateRows = Math.max(0, candidateRows - directCandidateRows);
		return new ExecutionInputHandoffSummary(
				fit.sourceRowsReviewed(),
				fit.scatteredSurfaceFitContractReady(),
				fit.scatteredSurfaceFitRun(),
				fit.expectedTargetCount(),
				candidateRows,
				exportedRows,
				directCandidateRows,
				scatteredCandidateRows,
				ready ? fit.readyFullSimulationTargetCount() : 0,
				ready ? fit.readyPerformanceOnlyTargetCount() : 0,
				ready,
				false,
				false,
				ready ? "READY" : "BLOCKED",
				messageFor(fit),
				sourceRuntimeInfo
		);
	}

	private static ExecutionInputHandoffRow row(
			PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitTarget target
	) {
		boolean direct = target.directNeighborBindingReady();
		String sourceKind = direct ? "DIRECT_STATIC_ANCHOR" : "SCATTERED_SURFACE_FIT";
		return new ExecutionInputHandoffRow(
				target.presetName(),
				target.caseName(),
				target.performanceMatchId(),
				target.queryAdvanceRatioJ(),
				target.queryRpm(),
				sourceKind,
				target.minimumPerformanceNeighborRows(),
				target.availableRectangularNeighborRows(),
				direct,
				target.scatteredFitRequired(),
				target.postReviewFullSimulationLookupAllowed(),
				false,
				false,
				false,
				false,
				direct ? "DIRECT_BINDING_TARGET" : "SURFACE_FIT_TARGET",
				direct ? "await-reviewed-static-anchor-payload" : "await-reviewed-scattered-surface-fit-payload"
		);
	}

	private static PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitSummary scenario(
			PropellerArchiveCtCpJScatteredSurfaceFitContract.CtCpJScatteredSurfaceFitContractAudit audit,
			String scenarioName
	) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static ExecutionInputHandoffExtrema extrema(List<ExecutionInputHandoffScenario> scenarios) {
		int ready = 0;
		int maxCandidate = 0;
		int maxExported = 0;
		int maxDirect = 0;
		int maxScattered = 0;
		int maxFull = 0;
		int maxPerformanceOnly = 0;
		int runtime = 0;
		int gameplay = 0;
		for (ExecutionInputHandoffScenario scenario : scenarios) {
			ExecutionInputHandoffSummary summary = scenario.summary();
			if (summary.executionInputHandoffReady()) {
				ready++;
			}
			maxCandidate = Math.max(maxCandidate, summary.candidateExecutionInputRowCount());
			maxExported = Math.max(maxExported, summary.exportedExecutionInputRowCount());
			maxDirect = Math.max(maxDirect, summary.directNeighborCandidateRowCount());
			maxScattered = Math.max(maxScattered, summary.scatteredSurfaceCandidateRowCount());
			maxFull = Math.max(maxFull, summary.fullSimulationExecutionInputRowCount());
			maxPerformanceOnly = Math.max(maxPerformanceOnly, summary.performanceOnlyExecutionInputRowCount());
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new ExecutionInputHandoffExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxCandidate,
				maxExported,
				maxDirect,
				maxScattered,
				maxFull,
				maxPerformanceOnly,
				runtime,
				gameplay
		);
	}

	private static String messageFor(
			PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitSummary fit
	) {
		if (!fit.sourceRowsReviewed()) {
			return "source-license-review-required";
		}
		if (!fit.scatteredSurfaceFitRun()) {
			return "scattered-surface-fit-not-run";
		}
		if (!fit.scatteredSurfaceFitContractReady()) {
			return fit.message();
		}
		return "scattered-surface-fit-execution-input-handoff-ready";
	}
}
