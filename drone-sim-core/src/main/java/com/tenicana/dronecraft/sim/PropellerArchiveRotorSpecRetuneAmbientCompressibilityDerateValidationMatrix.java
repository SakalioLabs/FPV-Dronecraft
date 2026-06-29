package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Validation-Matrix-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate validation matrix turns cold-air derate targets into offline validation runs only; it never invokes validation, applies DroneConfig changes, opens runtime coupling, exports playable references, or enables gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.SCENARIO_SAMPLE_COUNT;
	public static final int VALIDATION_CASE_COUNT = 4;
	public static final int VALIDATION_ROW_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.DERATE_ROW_COUNT
					* VALIDATION_CASE_COUNT;
	public static final int SCENARIO_METRIC_ROW_COUNT = 15;
	public static final int SUMMARY_ROW_COUNT = 13;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ VALIDATION_ROW_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final List<DerateValidationCaseDefinition> CASES = List.of(
			new DerateValidationCaseDefinition(
					"cold_air_max_rpm_scale_closure",
					"rotor_speed_control",
					"target_max_rpm_scale",
					6,
					0.0025,
					0.0010
			),
			new DerateValidationCaseDefinition(
					"cold_air_tip_mach_replay",
					"environment_speed_of_sound",
					"target_max_tip_mach",
					3,
					0.0030,
					0.0010
			),
			new DerateValidationCaseDefinition(
					"reaction_torque_limit_closure",
					"runtime_compressibility_curve",
					"post_derate_reaction_torque_scale",
					4,
					0.0050,
					0.0020
			),
			new DerateValidationCaseDefinition(
					"compressibility_impact_budget_closure",
					"runtime_compressibility_curve",
					"post_derate_thrust_loss_load_vibration",
					4,
					0.0100,
					0.0050
			)
	);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix() {
	}

	public record DerateValidationCaseDefinition(
			String validationCaseName,
			String validationDomain,
			String targetMetric,
			int minSampleCount,
			double maxPrimaryErrorRatio,
			double maxSecondaryErrorRatio
	) {
	}

	public record RetuneAmbientCompressibilityDerateValidationRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			String validationCaseName,
			String validationDomain,
			String targetMetric,
			int minSampleCount,
			double maxPrimaryErrorRatio,
			double maxSecondaryErrorRatio,
			double targetMaxTipMach,
			double targetMaxRpmScale,
			double requiredMaxRpmDeratePercent,
			String limitingBudget,
			double postDerateThrustLossPercent,
			double postDerateReactionTorqueScale,
			double postDerateVibrationProxy,
			boolean derateTargetWithinBudget,
			boolean validationRunPlanned,
			boolean validationInvoked,
			boolean validationPassed,
			boolean validationResultRequired,
			boolean playableReferenceAllowed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record RetuneAmbientCompressibilityDerateValidationScenarioSummary(
			boolean validationAcceptanceReady,
			boolean deratePlanAvailable,
			boolean validationMatrixAvailable,
			int validationRowCount,
			int plannedValidationCaseCount,
			int validationInvokedCount,
			int validationPassedCount,
			int derateTargetWithinBudgetCount,
			double maxRequiredMaxRpmDeratePercent,
			int playableReferenceAllowedCount,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String status,
			String message
	) {
	}

	public record RetuneAmbientCompressibilityDerateValidationScenario(
			String scenarioName,
			RetuneAmbientCompressibilityDerateValidationScenarioSummary summary
	) {
	}

	public record RetuneAmbientCompressibilityDerateValidationExtrema(
			int scenarioCount,
			int validationMatrixAvailableScenarioCount,
			int totalValidationRowCount,
			int maxValidationRowCount,
			int maxPlannedValidationCaseCount,
			int maxValidationInvokedCount,
			int maxValidationPassedCount,
			int maxDerateTargetWithinBudgetCount,
			double maxRequiredMaxRpmDeratePercent,
			int playableReferenceAllowedScenarioCount,
			int configPatchAllowedScenarioCount,
			int runtimeCouplingAllowedScenarioCount,
			int gameplayAutoApplyAllowedScenarioCount
	) {
	}

	public record RetuneAmbientCompressibilityDerateValidationAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int scenarioSampleCount,
			int validationCaseCount,
			int validationRowCount,
			int scenarioMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<DerateValidationCaseDefinition> cases,
			List<RetuneAmbientCompressibilityDerateValidationRow> rows,
			List<RetuneAmbientCompressibilityDerateValidationScenario> scenarios,
			RetuneAmbientCompressibilityDerateValidationExtrema extrema
	) {
		public RetuneAmbientCompressibilityDerateValidationAudit {
			cases = List.copyOf(cases);
			rows = List.copyOf(rows);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static RetuneAmbientCompressibilityDerateValidationAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateAudit derate =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.audit();
		List<RetuneAmbientCompressibilityDerateValidationScenario> scenarios = derate.scenarios().stream()
				.map(scenario -> scenario(derate, scenario))
				.toList();
		List<RetuneAmbientCompressibilityDerateValidationRow> rows = scenarios.stream()
				.flatMap(scenario -> rowsForScenario(derate, scenario.scenarioName()).stream())
				.toList();
		return new RetuneAmbientCompressibilityDerateValidationAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SCENARIO_SAMPLE_COUNT,
				VALIDATION_CASE_COUNT,
				VALIDATION_ROW_COUNT,
				SCENARIO_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				CASES,
				rows,
				scenarios,
				extrema(rows, scenarios)
		);
	}

	public static DerateValidationCaseDefinition caseDefinition(String validationCaseName) {
		return CASES.stream()
				.filter(caseDefinition -> caseDefinition.validationCaseName().equals(validationCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate validation case: " + validationCaseName));
	}

	public static RetuneAmbientCompressibilityDerateValidationScenario scenario(String scenarioName) {
		return audit().scenarios().stream()
				.filter(scenario -> scenario.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate validation scenario: " + scenarioName));
	}

	public static RetuneAmbientCompressibilityDerateValidationRow row(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			String validationCaseName
	) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName)
						&& row.ambientCaseName().equals(ambientCaseName)
						&& row.validationCaseName().equals(validationCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate validation row: "
								+ scenarioName + "/" + presetName + "/" + ambientCaseName
								+ "/" + validationCaseName));
	}

	private static RetuneAmbientCompressibilityDerateValidationScenario scenario(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateAudit derate,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateScenario scenario
	) {
		List<RetuneAmbientCompressibilityDerateValidationRow> rows =
				rowsForScenario(derate, scenario.scenarioName());
		return new RetuneAmbientCompressibilityDerateValidationScenario(
				scenario.scenarioName(),
				summary(scenario.summary(), rows)
		);
	}

	private static RetuneAmbientCompressibilityDerateValidationScenarioSummary summary(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateScenarioSummary derateSummary,
			List<RetuneAmbientCompressibilityDerateValidationRow> rows
	) {
		int planned = 0;
		int invoked = 0;
		int passed = 0;
		int targetWithin = 0;
		double maxDerate = 0.0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneAmbientCompressibilityDerateValidationRow row : rows) {
			if (row.validationRunPlanned()) {
				planned++;
			}
			if (row.validationInvoked()) {
				invoked++;
			}
			if (row.validationPassed()) {
				passed++;
			}
			if (row.derateTargetWithinBudget()) {
				targetWithin++;
			}
			maxDerate = Math.max(maxDerate, row.requiredMaxRpmDeratePercent());
			if (row.playableReferenceAllowed()) {
				playable++;
			}
			if (row.configPatchAllowed()) {
				config++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		boolean available = derateSummary.deratePlanAvailable() && !rows.isEmpty();
		return new RetuneAmbientCompressibilityDerateValidationScenarioSummary(
				derateSummary.validationAcceptanceReady(),
				derateSummary.deratePlanAvailable(),
				available,
				rows.size(),
				planned,
				invoked,
				passed,
				targetWithin,
				maxDerate,
				playable,
				config,
				runtime,
				gameplay,
				available ? "PENDING_VALIDATION" : "BLOCKED",
				available ? "cold-air-derate-validation-planned" : derateSummary.message()
		);
	}

	private static List<RetuneAmbientCompressibilityDerateValidationRow> rowsForScenario(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateAudit derate,
			String scenarioName
	) {
		return derate.rows().stream()
				.filter(row -> scenarioName.equals(row.scenarioName()))
				.filter(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateRow::derateTargetWithinBudget)
				.flatMap(row -> CASES.stream().map(caseDefinition -> row(row, caseDefinition)))
				.toList();
	}

	private static RetuneAmbientCompressibilityDerateValidationRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateRow derate,
			DerateValidationCaseDefinition caseDefinition
	) {
		return new RetuneAmbientCompressibilityDerateValidationRow(
				derate.scenarioName(),
				derate.presetName(),
				derate.ambientCaseName(),
				caseDefinition.validationCaseName(),
				caseDefinition.validationDomain(),
				caseDefinition.targetMetric(),
				caseDefinition.minSampleCount(),
				caseDefinition.maxPrimaryErrorRatio(),
				caseDefinition.maxSecondaryErrorRatio(),
				derate.targetMaxTipMach(),
				derate.targetMaxRpmScale(),
				derate.requiredMaxRpmDeratePercent(),
				derate.limitingBudget(),
				derate.postDerateThrustLossPercent(),
				derate.postDerateReactionTorqueScale(),
				derate.postDerateVibrationProxy(),
				derate.derateTargetWithinBudget(),
				true,
				false,
				false,
				true,
				false,
				false,
				false,
				false,
				"PENDING_VALIDATION",
				"cold-air-derate-validation-planned"
		);
	}

	private static RetuneAmbientCompressibilityDerateValidationExtrema extrema(
			List<RetuneAmbientCompressibilityDerateValidationRow> rows,
			List<RetuneAmbientCompressibilityDerateValidationScenario> scenarios
	) {
		int available = 0;
		int maxRows = 0;
		int maxPlanned = 0;
		int maxInvoked = 0;
		int maxPassed = 0;
		int maxTargetWithin = 0;
		double maxDerate = 0.0;
		int playable = 0;
		int config = 0;
		int runtime = 0;
		int gameplay = 0;
		for (RetuneAmbientCompressibilityDerateValidationScenario scenario : scenarios) {
			RetuneAmbientCompressibilityDerateValidationScenarioSummary summary = scenario.summary();
			if (summary.validationMatrixAvailable()) {
				available++;
			}
			maxRows = Math.max(maxRows, summary.validationRowCount());
			maxPlanned = Math.max(maxPlanned, summary.plannedValidationCaseCount());
			maxInvoked = Math.max(maxInvoked, summary.validationInvokedCount());
			maxPassed = Math.max(maxPassed, summary.validationPassedCount());
			maxTargetWithin = Math.max(maxTargetWithin, summary.derateTargetWithinBudgetCount());
			maxDerate = Math.max(maxDerate, summary.maxRequiredMaxRpmDeratePercent());
			if (summary.playableReferenceAllowedCount() > 0) {
				playable++;
			}
			if (summary.configPatchAllowedCount() > 0) {
				config++;
			}
			if (summary.runtimeCouplingAllowedCount() > 0) {
				runtime++;
			}
			if (summary.gameplayAutoApplyAllowedCount() > 0) {
				gameplay++;
			}
		}
		return new RetuneAmbientCompressibilityDerateValidationExtrema(
				scenarios.size(),
				available,
				rows.size(),
				maxRows,
				maxPlanned,
				maxInvoked,
				maxPassed,
				maxTargetWithin,
				maxDerate,
				playable,
				config,
				runtime,
				gameplay
		);
	}
}
