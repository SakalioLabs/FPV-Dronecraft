package com.tenicana.dronecraft.sim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PropellerArchiveRotorSpecRetuneValidationRunMatrix {
	public static final String SOURCE_ID = "User-Propeller-Archive-RotorSpec-Retune-Validation-Run-Matrix-Packet";
	public static final String CAVEAT =
			"RotorSpec retune validation run matrix defines offline validation cases required after retune readiness and before any DroneConfig patch; current rows never invoke validation, runtime coupling, or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int PRESET_SAMPLE_COUNT = PropellerArchiveRotorSpecRetuneReadinessGate.RETUNE_SAMPLE_COUNT;
	public static final int VALIDATION_CASE_COUNT = 4;
	public static final int VALIDATION_RUN_ROW_COUNT = PRESET_SAMPLE_COUNT * VALIDATION_CASE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 10;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ VALIDATION_RUN_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final List<ValidationCaseDefinition> CASES = List.of(
			new ValidationCaseDefinition(
					"static_hover_thrust_closure",
					"hover_static",
					"thrust_coefficient",
					32,
					0.08,
					0.05
			),
			new ValidationCaseDefinition(
					"static_yaw_torque_closure",
					"hover_static",
					"yaw_torque_per_thrust",
					32,
					0.12,
					0.05
			),
			new ValidationCaseDefinition(
					"forward_advance_ratio_sweep",
					"forward_flight",
					"ct_cp_vs_advance_ratio",
					48,
					0.10,
					0.03
			),
			new ValidationCaseDefinition(
					"geometry_chord_beta_review",
					"blade_geometry",
					"chord_beta_0p7r",
					12,
					0.10,
					0.05
			)
	);

	private PropellerArchiveRotorSpecRetuneValidationRunMatrix() {
	}

	public record ValidationCaseDefinition(
			String validationCaseName,
			String validationDomain,
			String targetMetric,
			int minSampleCount,
			double maxPrimaryErrorRatio,
			double maxSecondaryErrorRatio
	) {
	}

	public record RetuneValidationRunRow(
			String presetName,
			String validationCaseName,
			String validationDomain,
			String targetMetric,
			int minSampleCount,
			double maxPrimaryErrorRatio,
			double maxSecondaryErrorRatio,
			boolean retuneReviewReady,
			boolean validationRunPlanned,
			boolean validationInvoked,
			boolean validationPassed,
			boolean configPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String blocker,
			String nextRequiredAction
	) {
	}

	public record RetuneValidationRunSummary(
			int rowCount,
			int validationCaseCount,
			int retuneReviewReadyPresetCount,
			int validationRunPlannedCount,
			int validationInvokedCount,
			int validationPassedCount,
			int configPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			int maxPlannedRunsPerPreset
	) {
	}

	public record RetuneValidationRunAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int presetSampleCount,
			int validationCaseCount,
			int validationRunRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<ValidationCaseDefinition> cases,
			List<RetuneValidationRunRow> rows,
			RetuneValidationRunSummary summary
	) {
		public RetuneValidationRunAudit {
			cases = List.copyOf(cases);
			rows = List.copyOf(rows);
		}
	}

	public static RetuneValidationRunAudit audit() {
		return audit(PropellerArchiveRotorSpecRetuneReadinessGate.audit());
	}

	public static RetuneValidationRunAudit audit(
			PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessAudit readinessAudit
	) {
		if (readinessAudit == null) {
			throw new IllegalArgumentException("readinessAudit must not be null.");
		}
		List<RetuneValidationRunRow> rows = readinessAudit.rows()
				.stream()
				.flatMap(readiness -> CASES.stream().map(caseDefinition -> row(readiness, caseDefinition)))
				.toList();
		return new RetuneValidationRunAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				PRESET_SAMPLE_COUNT,
				VALIDATION_CASE_COUNT,
				VALIDATION_RUN_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				CASES,
				rows,
				summary(rows)
		);
	}

	public static ValidationCaseDefinition caseDefinition(String validationCaseName) {
		return CASES.stream()
				.filter(caseDefinition -> caseDefinition.validationCaseName().equals(validationCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune validation case: " + validationCaseName));
	}

	public static RetuneValidationRunRow row(String presetName, String validationCaseName) {
		return audit().rows().stream()
				.filter(row -> row.presetName().equals(presetName)
						&& row.validationCaseName().equals(validationCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune validation row: " + presetName + "/" + validationCaseName));
	}

	public static RetuneValidationRunRow row(
			PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessRow readiness,
			ValidationCaseDefinition caseDefinition
	) {
		if (readiness == null || caseDefinition == null) {
			throw new IllegalArgumentException("readiness and caseDefinition are required.");
		}
		boolean planned = readiness.directRetuneReviewReady();
		return new RetuneValidationRunRow(
				readiness.presetName(),
				caseDefinition.validationCaseName(),
				caseDefinition.validationDomain(),
				caseDefinition.targetMetric(),
				caseDefinition.minSampleCount(),
				caseDefinition.maxPrimaryErrorRatio(),
				caseDefinition.maxSecondaryErrorRatio(),
				readiness.directRetuneReviewReady(),
				planned,
				false,
				false,
				false,
				false,
				false,
				planned ? "PENDING_VALIDATION" : "BLOCKED",
				planned ? "validation-not-run" : readiness.dominantBlocker(),
				planned ? "execute-retune-validation-before-config-patch" : readiness.nextRequiredAction()
		);
	}

	private static RetuneValidationRunSummary summary(List<RetuneValidationRunRow> rows) {
		int planned = 0;
		int invoked = 0;
		int passed = 0;
		int patch = 0;
		int runtime = 0;
		int gameplay = 0;
		Map<String, Integer> plannedByPreset = new HashMap<>();
		for (RetuneValidationRunRow row : rows) {
			if (row.validationRunPlanned()) {
				planned++;
				plannedByPreset.merge(row.presetName(), 1, Integer::sum);
			}
			if (row.validationInvoked()) {
				invoked++;
			}
			if (row.validationPassed()) {
				passed++;
			}
			if (row.configPatchAllowed()) {
				patch++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		int maxPlanned = plannedByPreset.values().stream()
				.mapToInt(Integer::intValue)
				.max()
				.orElse(0);
		return new RetuneValidationRunSummary(
				rows.size(),
				VALIDATION_CASE_COUNT,
				plannedByPreset.size(),
				planned,
				invoked,
				passed,
				patch,
				runtime,
				gameplay,
				maxPlanned
		);
	}
}
