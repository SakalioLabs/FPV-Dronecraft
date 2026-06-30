package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJLookupReferenceHandoff {
	public static final String SOURCE_ID = "User-Propeller-Archive-CT-CP-J-Lookup-Reference-Handoff-Packet";
	public static final String CAVEAT =
			"CT/CP/J lookup reference handoff defines the reviewed payload shape after handoff-aware lookup execution and lookup acceptance; it exports no coefficients in the current state and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int REFERENCE_FIELD_ROW_COUNT = 12;
	public static final int SCENARIO_SAMPLE_COUNT = 5;
	public static final int SCENARIO_METRIC_ROW_COUNT = 16;
	public static final int SUMMARY_ROW_COUNT = 11;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REFERENCE_FIELD_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String REFERENCE_PAYLOAD_KIND =
			"compact-propeller-ct-cp-j-lookup-reference";

	private static final List<LookupReferenceField> REFERENCE_FIELDS = List.of(
			new LookupReferenceField("preset_name", "text", true,
					"DroneConfig preset key", "join lookup reference to preset profile", false, false),
			new LookupReferenceField("case_name", "text", true,
					"lookup query case key", "separate static mid and high-domain reference points", false, false),
			new LookupReferenceField("source_archive_sha256", "sha256", true,
					"PropellerArchiveSourceFingerprint archive hash", "prove source identity", false, false),
			new LookupReferenceField("performance_match_id", "text", true,
					"reviewed propeller performance source", "trace CT/CP/J provenance", false, false),
			new LookupReferenceField("geometry_match_id", "text", true,
					"reviewed geometry source or blocker", "separate full-simulation coverage", false, false),
			new LookupReferenceField("query_advance_ratio_j", "J", true,
					"accepted lookup query envelope", "bound lookup reference point", false, false),
			new LookupReferenceField("query_rpm", "rpm", true,
					"accepted lookup query envelope", "bound RPM bin reference point", false, false),
			new LookupReferenceField("equivalent_project_mu", "ratio", true,
					"J/pi audit conversion", "compare axial propeller lookup with advance-scale diagnostics", false, false),
			new LookupReferenceField("minimum_neighbor_rows", "count", true,
					"interpolation policy", "prove reviewed neighbor coverage", false, false),
			new LookupReferenceField("ct_cp_eta_reference", "coefficient", true,
					"accepted lookup result", "performance reference after quality acceptance", false, false),
			new LookupReferenceField("static_anchor_reference", "coefficient", true,
					"accepted static lookup result", "hover/static anchor reference after review", false, false),
			new LookupReferenceField("full_simulation_reference_weight", "weight", true,
					"coverage and handoff gate", "zero unless full simulation coverage is accepted", false, false)
	);

	private PropellerArchiveCtCpJLookupReferenceHandoff() {
	}

	public record LookupReferenceField(
			String fieldName,
			String unit,
			boolean required,
			String source,
			String downstreamUse,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed
	) {
	}

	public record LookupReferenceHandoffSummary(
			boolean lookupAcceptanceReady,
			boolean lookupExecutionContractReady,
			boolean compactReferenceReviewed,
			int expectedReferenceRowCount,
			int expectedReferenceFieldCount,
			int observedReferenceFieldCount,
			int acceptedLookupTargetCount,
			int blockedLookupTargetCount,
			boolean allReferenceFieldsPresent,
			boolean referenceMaterialExportAllowed,
			int performanceReferenceRowAvailableCount,
			int fullSimulationReferenceRowAvailableCount,
			int performanceOnlyReferenceRowAvailableCount,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String referencePayloadKind,
			String status,
			String message,
			String acceptanceStatus,
			String sourceRuntimeInfo
	) {
	}

	public record LookupReferenceHandoffScenario(
			String scenarioName,
			LookupReferenceHandoffSummary summary
	) {
	}

	public record LookupReferenceHandoffExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int lookupExecutionBlockedScenarioCount,
			int referenceMaterialExportAllowedCount,
			int maxAcceptedLookupTargetCount,
			int maxBlockedLookupTargetCount,
			int maxPerformanceReferenceRowAvailableCount,
			int maxFullSimulationReferenceRowAvailableCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJLookupReferenceHandoffAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int referenceFieldRowCount,
			int scenarioSampleCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<LookupReferenceField> fields,
			List<LookupReferenceHandoffScenario> scenarios,
			LookupReferenceHandoffExtrema extrema
	) {
		public CtCpJLookupReferenceHandoffAudit {
			fields = List.copyOf(fields);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJLookupReferenceHandoffAudit audit() {
		PropellerArchiveCtCpJLookupAcceptanceGate.CtCpJLookupAcceptanceAudit acceptanceAudit =
				PropellerArchiveCtCpJLookupAcceptanceGate.audit();
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary current =
				acceptance(acceptanceAudit, "current_no_reviewed_import_no_results");
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary executionBlocked =
				acceptance(acceptanceAudit, "reviewed_import_policy_ready_execution_blocked");
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary acceptedWithoutReview =
				acceptedWithoutReferenceReview();
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary acceptedAndReviewed =
				acceptance(acceptanceAudit, "synthetic_all_lookup_targets_pass");
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary failed =
				acceptance(acceptanceAudit, "synthetic_one_lookup_result_failed");
		List<LookupReferenceHandoffScenario> scenarios = List.of(
				new LookupReferenceHandoffScenario(
						"current_acceptance_blocked",
						handoff(current, REFERENCE_FIELDS, "audit-only-ct-cp-j-lookup-reference-handoff")),
				new LookupReferenceHandoffScenario(
						"acceptance_execution_blocked",
						handoff(executionBlocked, REFERENCE_FIELDS,
								"synthetic-lookup-reference-execution-contract-blocked")),
				new LookupReferenceHandoffScenario(
						"acceptance_ready_reference_review_missing",
						handoff(acceptedWithoutReview, REFERENCE_FIELDS,
								"synthetic-lookup-accepted-reference-review-missing")),
				new LookupReferenceHandoffScenario(
						"acceptance_ready_reference_reviewed",
						handoff(acceptedAndReviewed, REFERENCE_FIELDS,
								"synthetic-lookup-accepted-reference-handoff-ready")),
				new LookupReferenceHandoffScenario(
						"reference_reviewed_acceptance_failed",
						handoff(failed, REFERENCE_FIELDS, "synthetic-reference-reviewed-lookup-failed"))
		);
		return new CtCpJLookupReferenceHandoffAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				REFERENCE_FIELD_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				REFERENCE_FIELDS,
				scenarios,
				extrema(scenarios)
		);
	}

	public static LookupReferenceField field(String fieldName) {
		return REFERENCE_FIELDS.stream()
				.filter(field -> field.fieldName().equals(fieldName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown CT/CP/J lookup reference field: "
						+ fieldName));
	}

	public static LookupReferenceHandoffSummary handoff(
			PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary acceptance,
			List<LookupReferenceField> fields,
			String sourceRuntimeInfo
	) {
		if (acceptance == null) {
			throw new IllegalArgumentException("acceptance summary must not be null.");
		}
		if (fields == null) {
			throw new IllegalArgumentException("fields must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		int observedFields = 0;
		for (LookupReferenceField field : fields) {
			if (field == null || field.fieldName() == null || field.fieldName().isBlank()) {
				throw new IllegalArgumentException("fields must include stable field names.");
			}
			observedFields++;
		}
		boolean allFields = observedFields == REFERENCE_FIELD_ROW_COUNT;
		boolean exportAllowed = acceptance.lookupAcceptanceReady()
				&& acceptance.compactReferenceReviewed()
				&& allFields;
		int performanceRows = exportAllowed ? PropellerArchiveCtCpJLookupAcceptanceGate.TARGET_ROW_COUNT : 0;
		int fullSimulationRows = exportAllowed ? fullSimulationTargetCount() : 0;
		int performanceOnlyRows = performanceRows - fullSimulationRows;
		int blockedTargets = acceptance.failedResultCount()
				+ acceptance.missingResultCount()
				+ acceptance.unexpectedResultCount();
		return new LookupReferenceHandoffSummary(
				acceptance.lookupAcceptanceReady(),
				acceptance.lookupExecutionContractReady(),
				acceptance.compactReferenceReviewed(),
				PropellerArchiveCtCpJLookupAcceptanceGate.TARGET_ROW_COUNT,
				REFERENCE_FIELD_ROW_COUNT,
				observedFields,
				acceptance.passedResultCount(),
				blockedTargets,
				allFields,
				exportAllowed,
				performanceRows,
				fullSimulationRows,
				performanceOnlyRows,
				false,
				false,
				REFERENCE_PAYLOAD_KIND,
				exportAllowed ? "READY" : "BLOCKED",
				messageFor(acceptance, allFields),
				acceptance.status(),
				sourceRuntimeInfo
		);
	}

	private static PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary acceptedWithoutReferenceReview() {
		List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget> targets =
				PropellerArchiveCtCpJLookupAcceptanceGate.targets();
		return PropellerArchiveCtCpJLookupAcceptanceGate.gate(
				true,
				true,
				true,
				true,
				false,
				targets,
				passingResults(targets),
				"synthetic-lookup-accepted-reference-review-missing"
		);
	}

	private static List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult> passingResults(
			List<PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget> targets
	) {
		return targets.stream()
				.map(target -> PropellerArchiveCtCpJLookupAcceptanceGate.result(
						target.presetName(),
						target.caseName(),
						target.minNeighborRows() + 1,
						0.0,
						0.010,
						target.maxEtaResidual() * 0.50,
						target.requiresStaticAnchorPreservation()
								? target.maxStaticAnchorError() * 0.50
								: 0.0
				))
				.toList();
	}

	private static PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary acceptance(
			PropellerArchiveCtCpJLookupAcceptanceGate.CtCpJLookupAcceptanceAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static LookupReferenceHandoffExtrema extrema(List<LookupReferenceHandoffScenario> scenarios) {
		int ready = 0;
		int executionBlocked = 0;
		int exportAllowed = 0;
		int maxAccepted = 0;
		int maxBlocked = 0;
		int maxPerformanceRows = 0;
		int maxFullRows = 0;
		int runtime = 0;
		int gameplay = 0;
		for (LookupReferenceHandoffScenario scenario : scenarios) {
			LookupReferenceHandoffSummary summary = scenario.summary();
			if (summary.referenceMaterialExportAllowed()) {
				ready++;
				exportAllowed++;
			}
			if (!summary.lookupExecutionContractReady()
					&& "lookup-execution-contract-not-ready".equals(summary.message())) {
				executionBlocked++;
			}
			maxAccepted = Math.max(maxAccepted, summary.acceptedLookupTargetCount());
			maxBlocked = Math.max(maxBlocked, summary.blockedLookupTargetCount());
			maxPerformanceRows = Math.max(maxPerformanceRows, summary.performanceReferenceRowAvailableCount());
			maxFullRows = Math.max(maxFullRows, summary.fullSimulationReferenceRowAvailableCount());
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new LookupReferenceHandoffExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				executionBlocked,
				exportAllowed,
				maxAccepted,
				maxBlocked,
				maxPerformanceRows,
				maxFullRows,
				runtime,
				gameplay
		);
	}

	private static int fullSimulationTargetCount() {
		int count = 0;
		for (PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceTarget target
				: PropellerArchiveCtCpJLookupAcceptanceGate.targets()) {
			if (target.downstreamUse().startsWith("full-simulation")) {
				count++;
			}
		}
		return count;
	}

	private static String messageFor(
			PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary acceptance,
			boolean allFields
	) {
		if ("lookup-execution-contract-blocked".equals(acceptance.message())) {
			return "lookup-execution-contract-not-ready";
		}
		if (!acceptance.lookupAcceptanceReady()) {
			return "lookup-acceptance-not-ready";
		}
		if (!acceptance.compactReferenceReviewed()) {
			return "lookup-reference-review-missing";
		}
		if (!allFields) {
			return "lookup-reference-schema-incomplete";
		}
		return "lookup-reference-material-ready";
	}
}
