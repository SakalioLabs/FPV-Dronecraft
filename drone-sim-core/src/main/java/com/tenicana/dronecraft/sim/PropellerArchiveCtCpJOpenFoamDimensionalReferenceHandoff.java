package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-OpenFOAM-Dimensional-Reference-Handoff-Packet";
	public static final String CAVEAT =
			"OpenFOAM dimensional reference handoff defines the reviewed payload shape after handoff-aware CT/CP/J lookup execution, coefficient-level CFD lookup support, solver-quality QA, SI residual support, and inherited archive curve-shape diagnostics; it exports no current references and never enables runtime coupling or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 11;
	public static final int REFERENCE_FIELD_ROW_COUNT = 17;
	public static final int SCENARIO_SAMPLE_COUNT = 5;
	public static final int SCENARIO_METRIC_ROW_COUNT = 27;
	public static final int SUMMARY_ROW_COUNT = 17;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REFERENCE_FIELD_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final String REFERENCE_PAYLOAD_KIND =
			"compact-openfoam-dimensional-rotor-response-reference";

	private static final List<OpenFoamDimensionalReferenceField> REFERENCE_FIELDS = List.of(
			new OpenFoamDimensionalReferenceField("preset_name", "text", true,
					"DroneConfig preset key", "join SI CFD reference to preset profile", false, false),
			new OpenFoamDimensionalReferenceField("case_name", "text", true,
					"OpenFOAM validation case key", "separate static mid and high-domain rotor points", false, false),
			new OpenFoamDimensionalReferenceField("source_archive_sha256", "sha256", true,
					"PropellerArchiveSourceFingerprint archive hash", "prove wind-tunnel source identity", false, false),
			new OpenFoamDimensionalReferenceField("solver_family", "text", true,
					"OpenFOAM dimensional residual contract", "trace external solver setup", false, false),
			new OpenFoamDimensionalReferenceField("mesh_geometry_id", "text", true,
					"reviewed blade geometry", "trace mesh geometry provenance", false, false),
			new OpenFoamDimensionalReferenceField("query_advance_ratio_j", "J", true,
					"accepted CT/CP/J lookup query", "bound advance-ratio reference point", false, false),
			new OpenFoamDimensionalReferenceField("query_rpm", "rpm", true,
					"accepted CT/CP/J lookup query", "bound RPM reference point", false, false),
			new OpenFoamDimensionalReferenceField("reference_thrust_newtons", "N", true,
					"CT/CP/J dimensional rotor response", "SI thrust target for later review", false, false),
			new OpenFoamDimensionalReferenceField("reference_shaft_power_watts", "W", true,
					"CT/CP/J dimensional rotor response", "SI shaft-power target for later review", false, false),
			new OpenFoamDimensionalReferenceField("reference_shaft_torque_newton_meters", "N*m", true,
					"CT/CP/J dimensional rotor response", "reaction-torque target for later review", false, false),
			new OpenFoamDimensionalReferenceField("reference_induced_velocity_mps", "m/s", true,
					"CT/CP/J dimensional rotor response", "wake-speed target for later review", false, false),
			new OpenFoamDimensionalReferenceField("reference_momentum_power_watts", "W", true,
					"momentum-theory dimensional response", "wake power closure target", false, false),
			new OpenFoamDimensionalReferenceField("thrust_residual_to_reference", "ratio", true,
					"OpenFOAM dimensional residual contract", "gate CFD thrust agreement", false, false),
			new OpenFoamDimensionalReferenceField("shaft_power_residual_to_reference", "ratio", true,
					"OpenFOAM dimensional residual contract", "gate CFD power agreement", false, false),
			new OpenFoamDimensionalReferenceField("shaft_torque_residual_to_reference", "ratio", true,
					"OpenFOAM dimensional residual contract", "gate CFD torque agreement", false, false),
			new OpenFoamDimensionalReferenceField("induced_velocity_residual_to_reference", "ratio", true,
					"OpenFOAM dimensional residual contract", "gate wake velocity agreement", false, false),
			new OpenFoamDimensionalReferenceField("momentum_power_residual_to_reference", "ratio", true,
					"OpenFOAM dimensional residual contract", "gate wake energy closure", false, false)
	);

	private PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff() {
	}

	public record OpenFoamDimensionalReferenceField(
			String fieldName,
			String unit,
			boolean required,
			String source,
			String downstreamUse,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed
	) {
	}

	public record OpenFoamDimensionalReferenceHandoffSummary(
			boolean lookupExecutionContractReady,
			boolean dimensionalSupportReady,
			boolean dimensionalReferenceReviewed,
			boolean lookupSupportReady,
			boolean dimensionalResidualReady,
			boolean openFoamSolverQualityContractReady,
			int openFoamSolverQualityBlockerCount,
			String openFoamSolverQualityNextRequiredAction,
			int expectedReferenceRowCount,
			int expectedReferenceFieldCount,
			int observedReferenceFieldCount,
			int supportedDimensionalTargetCount,
			int blockedDimensionalTargetCount,
			int archiveCurveShapeGuardInheritedReferenceCount,
			int negativeThrustTailReferenceCount,
			double maxArchiveCurveEtaFormulaResidual,
			double maxArchiveCurveCtIncrease,
			boolean allReferenceFieldsPresent,
			boolean referenceMaterialExportAllowed,
			int openFoamDimensionalReferenceRowAvailableCount,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String referencePayloadKind,
			String status,
			String message,
			String supportStatus,
			String sourceRuntimeInfo
	) {
	}

	public record OpenFoamDimensionalReferenceHandoffScenario(
			String scenarioName,
			OpenFoamDimensionalReferenceHandoffSummary summary
	) {
	}

	public record OpenFoamDimensionalReferenceHandoffExtrema(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			int lookupExecutionBlockedScenarioCount,
			int referenceMaterialExportAllowedCount,
			int maxSupportedDimensionalTargetCount,
			int maxBlockedDimensionalTargetCount,
			int maxObservedReferenceFieldCount,
			int maxReferenceRowAvailableCount,
			int maxArchiveCurveShapeGuardInheritedReferenceCount,
			int maxNegativeThrustTailReferenceCount,
			double maxArchiveCurveEtaFormulaResidual,
			double maxArchiveCurveCtIncrease,
			int maxOpenFoamSolverQualityBlockerCount,
			int openFoamSolverQualityBlockerScenarioCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount
	) {
	}

	public record CtCpJOpenFoamDimensionalReferenceHandoffAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int referenceFieldRowCount,
			int scenarioSampleCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<OpenFoamDimensionalReferenceField> fields,
			List<OpenFoamDimensionalReferenceHandoffScenario> scenarios,
			OpenFoamDimensionalReferenceHandoffExtrema extrema
	) {
		public CtCpJOpenFoamDimensionalReferenceHandoffAudit {
			fields = List.copyOf(fields);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJOpenFoamDimensionalReferenceHandoffAudit audit() {
		PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.CtCpJOpenFoamDimensionalSupportGateAudit supportAudit =
				PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.audit();
		PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportSummary current =
				support(supportAudit, "current_lookup_and_dimensional_blocked");
		PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportSummary executionBlocked =
				support(supportAudit, "lookup_execution_blocked_si_ready");
		PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportSummary supportReady =
				support(supportAudit, "lookup_and_dimensional_openfoam_support_ready");
		PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportSummary residualFailed =
				support(supportAudit, "lookup_support_ready_si_residual_failed");
		List<OpenFoamDimensionalReferenceHandoffScenario> scenarios = List.of(
				new OpenFoamDimensionalReferenceHandoffScenario(
						"current_dimensional_support_blocked",
						handoff(current, false, REFERENCE_FIELDS,
								"audit-only-openfoam-dimensional-reference-handoff")),
				new OpenFoamDimensionalReferenceHandoffScenario(
						"lookup_execution_blocked_reference_reviewed",
						handoff(executionBlocked, true, REFERENCE_FIELDS,
								"synthetic-openfoam-dimensional-reference-execution-blocked")),
				new OpenFoamDimensionalReferenceHandoffScenario(
						"dimensional_support_ready_reference_review_missing",
						handoff(supportReady, false, REFERENCE_FIELDS,
								"synthetic-openfoam-dimensional-support-ready-review-missing")),
				new OpenFoamDimensionalReferenceHandoffScenario(
						"dimensional_support_ready_reference_reviewed",
						handoff(supportReady, true, REFERENCE_FIELDS,
								"synthetic-openfoam-dimensional-reference-handoff-ready")),
				new OpenFoamDimensionalReferenceHandoffScenario(
						"reference_reviewed_dimensional_support_failed",
						handoff(residualFailed, true, REFERENCE_FIELDS,
								"synthetic-openfoam-dimensional-reference-reviewed-support-failed"))
		);
		return new CtCpJOpenFoamDimensionalReferenceHandoffAudit(
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

	public static OpenFoamDimensionalReferenceField field(String fieldName) {
		return REFERENCE_FIELDS.stream()
				.filter(field -> field.fieldName().equals(fieldName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown OpenFOAM dimensional reference field: " + fieldName));
	}

	public static OpenFoamDimensionalReferenceHandoffSummary handoff(
			PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportSummary support,
			boolean dimensionalReferenceReviewed,
			List<OpenFoamDimensionalReferenceField> fields,
			String sourceRuntimeInfo
	) {
		if (support == null) {
			throw new IllegalArgumentException("dimensional support summary must not be null.");
		}
		if (fields == null) {
			throw new IllegalArgumentException("fields must not be null.");
		}
		if (sourceRuntimeInfo == null || sourceRuntimeInfo.isBlank()) {
			throw new IllegalArgumentException("sourceRuntimeInfo must not be blank.");
		}
		int observedFields = 0;
		for (OpenFoamDimensionalReferenceField field : fields) {
			if (field == null || field.fieldName() == null || field.fieldName().isBlank()) {
				throw new IllegalArgumentException("fields must include stable field names.");
			}
			observedFields++;
		}
		boolean allFields = observedFields == REFERENCE_FIELD_ROW_COUNT;
		int expectedRows = support.expectedOpenFoamDimensionalResultCaseCount();
		int supportedRows = support.supportedDimensionalTargetCount();
		int blockedRows = expectedRows - supportedRows;
		boolean archiveCurveShapeGuardComplete =
				support.archiveCurveShapeGuardInheritedReferenceCount()
						>= support.expectedDimensionalReferenceCount()
				&& support.maxArchiveCurveEtaFormulaResidual()
						<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL
				&& support.maxArchiveCurveCtIncrease()
						<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE;
		boolean exportAllowed = support.cfdDimensionalSupportReady()
				&& dimensionalReferenceReviewed
				&& allFields
				&& archiveCurveShapeGuardComplete;
		return new OpenFoamDimensionalReferenceHandoffSummary(
				support.lookupExecutionContractReady(),
				support.cfdDimensionalSupportReady(),
				dimensionalReferenceReviewed,
				support.lookupSupportReady(),
				support.dimensionalResidualReady(),
				support.openFoamSolverQualityContractReady(),
				support.openFoamSolverQualityBlockerCount(),
				support.openFoamSolverQualityNextRequiredAction(),
				expectedRows,
				REFERENCE_FIELD_ROW_COUNT,
				observedFields,
				supportedRows,
				blockedRows,
				support.archiveCurveShapeGuardInheritedReferenceCount(),
				support.negativeThrustTailReferenceCount(),
				support.maxArchiveCurveEtaFormulaResidual(),
				support.maxArchiveCurveCtIncrease(),
				allFields,
				exportAllowed,
				exportAllowed ? supportedRows : 0,
				false,
				false,
				REFERENCE_PAYLOAD_KIND,
				exportAllowed ? "READY" : "BLOCKED",
				messageFor(support, dimensionalReferenceReviewed, allFields,
						archiveCurveShapeGuardComplete),
				support.status(),
				sourceRuntimeInfo
		);
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportSummary support(
			PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.CtCpJOpenFoamDimensionalSupportGateAudit audit,
			String scenarioName
	) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static OpenFoamDimensionalReferenceHandoffExtrema extrema(
			List<OpenFoamDimensionalReferenceHandoffScenario> scenarios
	) {
		int ready = 0;
		int exportAllowed = 0;
		int executionBlocked = 0;
		int maxSupported = 0;
		int maxBlocked = 0;
		int maxFields = 0;
		int maxRowsAvailable = 0;
		int maxShapeInherited = 0;
		int maxNegativeTail = 0;
		double maxArchiveEta = 0.0;
		double maxArchiveCt = 0.0;
		int maxQualityBlockers = 0;
		int qualityBlocked = 0;
		int runtime = 0;
		int gameplay = 0;
		for (OpenFoamDimensionalReferenceHandoffScenario scenario : scenarios) {
			OpenFoamDimensionalReferenceHandoffSummary summary = scenario.summary();
			if (summary.referenceMaterialExportAllowed()) {
				ready++;
				exportAllowed++;
			}
			if (!summary.lookupExecutionContractReady()
					&& "lookup-execution-contract-not-ready".equals(summary.message())) {
				executionBlocked++;
			}
			maxSupported = Math.max(maxSupported, summary.supportedDimensionalTargetCount());
			maxBlocked = Math.max(maxBlocked, summary.blockedDimensionalTargetCount());
			maxFields = Math.max(maxFields, summary.observedReferenceFieldCount());
			maxRowsAvailable = Math.max(maxRowsAvailable,
					summary.openFoamDimensionalReferenceRowAvailableCount());
			maxShapeInherited = Math.max(maxShapeInherited,
					summary.archiveCurveShapeGuardInheritedReferenceCount());
			maxNegativeTail = Math.max(maxNegativeTail, summary.negativeThrustTailReferenceCount());
			maxArchiveEta = Math.max(maxArchiveEta, summary.maxArchiveCurveEtaFormulaResidual());
			maxArchiveCt = Math.max(maxArchiveCt, summary.maxArchiveCurveCtIncrease());
			maxQualityBlockers = Math.max(maxQualityBlockers,
					summary.openFoamSolverQualityBlockerCount());
			if (summary.openFoamSolverQualityBlockerCount() > 0) {
				qualityBlocked++;
			}
			if (summary.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new OpenFoamDimensionalReferenceHandoffExtrema(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				executionBlocked,
				exportAllowed,
				maxSupported,
				maxBlocked,
				maxFields,
				maxRowsAvailable,
				maxShapeInherited,
				maxNegativeTail,
				maxArchiveEta,
				maxArchiveCt,
				maxQualityBlockers,
				qualityBlocked,
				runtime,
				gameplay
		);
	}

	private static String messageFor(
			PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportSummary support,
			boolean dimensionalReferenceReviewed,
			boolean allFields,
			boolean archiveCurveShapeGuardComplete
	) {
		if ("lookup-execution-contract-not-ready".equals(support.message())) {
			return "lookup-execution-contract-not-ready";
		}
		if (!support.cfdDimensionalSupportReady()) {
			if ("openfoam-solver-quality-not-ready".equals(support.message())) {
				return "openfoam-solver-quality-not-ready";
			}
			if (!support.lookupSupportReady()) {
				return "openfoam-dimensional-lookup-support-not-ready";
			}
			if (!support.dimensionalResidualReady()) {
				return "openfoam-dimensional-residual-not-ready";
			}
			return "openfoam-dimensional-support-not-ready";
		}
		if (!dimensionalReferenceReviewed) {
			return "openfoam-dimensional-reference-review-missing";
		}
		if (!archiveCurveShapeGuardComplete) {
			return "archive-curve-shape-guard-not-inherited";
		}
		if (!allFields) {
			return "openfoam-dimensional-reference-schema-incomplete";
		}
		return "openfoam-dimensional-reference-material-ready";
	}
}
