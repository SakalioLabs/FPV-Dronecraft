package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCompactReferenceHandoff {
	public static final String SOURCE_ID = "User-Propeller-Archive-Compact-Reference-Handoff-Packet";
	public static final String CAVEAT =
			"Compact reference handoff defines the reviewed payload shape for downstream playable reference material after curve-fit acceptance; it exports no raw rows or coefficients in the current state and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int REFERENCE_FIELD_ROW_COUNT = 12;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_ROW_COUNT = 17;
	public static final int SUMMARY_ROW_COUNT = 9;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REFERENCE_FIELD_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String REFERENCE_PAYLOAD_KIND =
			"compact-propeller-ct-cp-eta-geometry-reference";

	private static final List<CompactReferenceField> REFERENCE_FIELDS = List.of(
			new CompactReferenceField("preset_name", "text", true,
					"DroneConfig preset key", "join playable profile to reviewed simulation fit", false, false),
			new CompactReferenceField("source_archive_sha256", "sha256", true,
					"PropellerArchiveSourceFingerprint archive hash", "prove source data identity", false, false),
			new CompactReferenceField("performance_match_id", "text", true,
					"reviewed performance curve source", "trace CT/CP fit provenance", false, false),
			new CompactReferenceField("geometry_match_id", "text", true,
					"reviewed geometry curve source", "trace chord/beta fit provenance", false, false),
			new CompactReferenceField("advance_ratio_domain", "J", true,
					"validated advance-ratio interval", "bound forward-flight curve lookup", false, false),
			new CompactReferenceField("rpm_domain", "rpm", true,
					"validated RPM interval", "bound speed-dependent fit lookup", false, false),
			new CompactReferenceField("ct_curve_reference", "coefficient", true,
					"accepted thrust coefficient curve family", "simulation-derived thrust feel reference", false, false),
			new CompactReferenceField("cp_curve_reference", "coefficient", true,
					"accepted power coefficient curve family", "simulation-derived power draw reference", false, false),
			new CompactReferenceField("eta_consistency_residual", "ratio", true,
					"accepted efficiency consistency residual", "reject inconsistent thrust/power tuning", false, false),
			new CompactReferenceField("static_ct_cp_anchor", "coefficient", true,
					"accepted hover/static coefficient anchor", "hover thrust and power reference", false, false),
			new CompactReferenceField("chord_distribution_reference", "ratio", true,
					"accepted blade chord station fit", "geometry-driven solidity reference", false, false),
			new CompactReferenceField("beta_distribution_reference", "deg", true,
					"accepted blade twist station fit", "geometry-driven pitch/twist reference", false, false)
	);

	private PropellerArchiveCompactReferenceHandoff() {
	}

	public record CompactReferenceField(
			String fieldName,
			String unit,
			boolean required,
			String source,
			String playableUse,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed
	) {
	}

	public record CompactReferenceHandoffSummary(
			boolean curveFitAcceptanceReady,
			boolean compactReferenceReviewed,
			int expectedPresetCount,
			int expectedCurveFamilyCount,
			int expectedReferenceFieldCount,
			int observedReferenceFieldCount,
			int acceptedCurveTargetCount,
			int blockedCurveTargetCount,
			boolean allReferenceFieldsPresent,
			boolean referenceMaterialExportAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String referencePayloadKind,
			String status,
			String message,
			String acceptanceStatus,
			String sourceRuntimeInfo
	) {
	}

	public record CompactReferenceHandoffScenario(
			String scenarioName,
			CompactReferenceHandoffSummary summary
	) {
	}

	public record CompactReferenceHandoffExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int referenceMaterialExportAllowedCount,
			int maxAcceptedCurveTargetCount,
			int maxBlockedCurveTargetCount,
			int maxObservedReferenceFieldCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CompactReferenceHandoffAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int referenceFieldRowCount,
			int scenarioSampleCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<CompactReferenceField> fields,
			List<CompactReferenceHandoffScenario> scenarios,
			CompactReferenceHandoffExtrema extrema
	) {
		public CompactReferenceHandoffAudit {
			fields = List.copyOf(fields);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CompactReferenceHandoffAudit audit() {
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceAudit acceptanceAudit =
				PropellerArchiveCurveFitAcceptanceGate.audit();
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary current =
				acceptance(acceptanceAudit, "current_no_reviewed_import_no_results");
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary acceptedWithoutReferenceReview =
				acceptedWithoutReferenceReview();
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary acceptedAndReviewed =
				acceptance(acceptanceAudit, "synthetic_all_curve_targets_pass");
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary failed =
				acceptance(acceptanceAudit, "synthetic_one_curve_result_failed");
		List<CompactReferenceHandoffScenario> scenarios = List.of(
				new CompactReferenceHandoffScenario(
						"current_acceptance_blocked",
						handoff(current, REFERENCE_FIELDS, "audit-only-prop-archive-reference-handoff")),
				new CompactReferenceHandoffScenario(
						"acceptance_ready_reference_review_missing",
						handoff(acceptedWithoutReferenceReview, REFERENCE_FIELDS,
								"synthetic-accepted-reference-review-missing")),
				new CompactReferenceHandoffScenario(
						"acceptance_ready_reference_reviewed",
						handoff(acceptedAndReviewed, REFERENCE_FIELDS,
								"synthetic-accepted-reference-handoff-ready")),
				new CompactReferenceHandoffScenario(
						"reference_reviewed_acceptance_failed",
						handoff(failed, REFERENCE_FIELDS, "synthetic-reference-reviewed-fit-failed"))
		);
		return new CompactReferenceHandoffAudit(
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

	public static CompactReferenceField field(String fieldName) {
		return REFERENCE_FIELDS.stream()
				.filter(field -> field.fieldName().equals(fieldName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown compact reference field: " + fieldName));
	}

	public static CompactReferenceHandoffSummary handoff(
			PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary acceptance,
			List<CompactReferenceField> fields,
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
		for (CompactReferenceField field : fields) {
			if (field == null || field.fieldName() == null || field.fieldName().isBlank()) {
				throw new IllegalArgumentException("fields must include stable field names.");
			}
			observedFields++;
		}
		boolean allFields = observedFields == REFERENCE_FIELD_ROW_COUNT;
		int blockedTargets = acceptance.failedResultCount()
				+ acceptance.missingResultCount()
				+ acceptance.unexpectedResultCount();
		boolean exportAllowed = acceptance.curveFitAcceptanceReady()
				&& acceptance.compactReferenceReviewed()
				&& allFields;
		return new CompactReferenceHandoffSummary(
				acceptance.curveFitAcceptanceReady(),
				acceptance.compactReferenceReviewed(),
				PropellerArchiveCurveFitPlan.PRESET_PLAN_ROW_COUNT,
				PropellerArchiveCurveFitPlan.CURVE_CONTRACT_ROW_COUNT,
				REFERENCE_FIELD_ROW_COUNT,
				observedFields,
				acceptance.passedResultCount(),
				blockedTargets,
				allFields,
				exportAllowed,
				false,
				false,
				REFERENCE_PAYLOAD_KIND,
				exportAllowed ? "READY" : "BLOCKED",
				messageFor(acceptance, allFields),
				acceptance.status(),
				sourceRuntimeInfo
		);
	}

	private static PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary acceptedWithoutReferenceReview() {
		List<PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceTarget> targets =
				PropellerArchiveCurveFitAcceptanceGate.targets();
		return PropellerArchiveCurveFitAcceptanceGate.gate(
				true,
				true,
				true,
				false,
				targets,
				passingResults(targets),
				"synthetic-accepted-reference-review-missing"
		);
	}

	private static List<PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceResult> passingResults(
			List<PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceTarget> targets
	) {
		return targets.stream()
				.map(target -> PropellerArchiveCurveFitAcceptanceGate.result(
						target.presetName(),
						target.curveName(),
						target.maxWeightedRmse() * 0.5,
						target.maxAnchorError() * 0.5,
						target.minValidationRowCount() + 2,
						0
				))
				.toList();
	}

	private static PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary acceptance(
			PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceAudit audit,
			String scenarioName
	) {
		return audit.scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static CompactReferenceHandoffExtrema extrema(List<CompactReferenceHandoffScenario> scenarios) {
		int ready = 0;
		int exportAllowed = 0;
		int maxAccepted = 0;
		int maxBlocked = 0;
		int maxFields = 0;
		int runtime = 0;
		int gameplay = 0;
		for (CompactReferenceHandoffScenario scenario : scenarios) {
			CompactReferenceHandoffSummary summary = scenario.summary();
			if (summary.referenceMaterialExportAllowed()) {
				ready++;
				exportAllowed++;
			}
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxAccepted = Math.max(maxAccepted, summary.acceptedCurveTargetCount());
			maxBlocked = Math.max(maxBlocked, summary.blockedCurveTargetCount());
			maxFields = Math.max(maxFields, summary.observedReferenceFieldCount());
		}
		return new CompactReferenceHandoffExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				exportAllowed,
				maxAccepted,
				maxBlocked,
				maxFields,
				runtime,
				gameplay
		);
	}

	private static String messageFor(
			PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary acceptance,
			boolean allFields
	) {
		if (!acceptance.curveFitAcceptanceReady()) {
			return "curve-fit-acceptance-not-ready";
		}
		if (!acceptance.compactReferenceReviewed()) {
			return "compact-reference-review-missing";
		}
		if (!allFields) {
			return "compact-reference-schema-incomplete";
		}
		return "compact-reference-material-ready";
	}
}
