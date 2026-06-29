package com.tenicana.dronecraft.sim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Blackbox-Regression-Matrix-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate control-hook blackbox regression matrix defines offline target-omega derate cases after hook readiness and before any runtime coupling, playable export, or gameplay auto-apply.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int CONTRACT_SCENARIO_COUNT = 2;
	public static final int PRESET_SAMPLE_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.CONTRACT_ROW_COUNT;
	public static final int REGRESSION_CASE_COUNT = 4;
	public static final int REGRESSION_RUN_ROW_COUNT =
			CONTRACT_SCENARIO_COUNT * PRESET_SAMPLE_COUNT * REGRESSION_CASE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 13;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ REGRESSION_RUN_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final List<BlackboxRegressionCaseDefinition> CASES = List.of(
			new BlackboxRegressionCaseDefinition(
					"hover_hold_target_omega_scale",
					"hover_hold",
					"target_omega_scale_closure",
					240,
					0.005,
					0.02,
					true,
					false
			),
			new BlackboxRegressionCaseDefinition(
					"full_throttle_ramp_derate_clamp",
					"throttle_ramp",
					"target_omega_clamp_and_slew",
					180,
					0.006,
					0.03,
					true,
					true
			),
			new BlackboxRegressionCaseDefinition(
					"cold_air_forward_punchout_margin",
					"forward_punchout",
					"tip_mach_and_thrust_loss_margin",
					360,
					0.02,
					0.04,
					true,
					false
			),
			new BlackboxRegressionCaseDefinition(
					"failsafe_disarm_no_overspeed",
					"failsafe_disarm",
					"no_load_overspeed_and_clamp_release",
					160,
					0.01,
					0.05,
					false,
					true
			)
	);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix() {
	}

	public record BlackboxRegressionCaseDefinition(
			String regressionCaseName,
			String flightPhase,
			String targetMetric,
			int minSampleCount,
			double maxPrimaryErrorRatio,
			double maxSecondaryErrorRatio,
			boolean requiresMotorResponseReview,
			boolean requiresFailsafeReview
	) {
	}

	public record DerateControlHookBlackboxRegressionRunRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			String regressionCaseName,
			String flightPhase,
			String targetMetric,
			int minSampleCount,
			double maxPrimaryErrorRatio,
			double maxSecondaryErrorRatio,
			boolean controlHookImplementationReady,
			boolean regressionRunPlanned,
			boolean regressionInvoked,
			boolean regressionPassed,
			double targetMaxRpmScale,
			double contractedMaxRpm,
			double equivalentMaxThrustLossPercent,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String blocker,
			String nextRequiredAction
	) {
	}

	public record DerateControlHookBlackboxRegressionSummary(
			int rowCount,
			int regressionCaseCount,
			int contractScenarioCount,
			int controlHookImplementationReadyScenarioCount,
			int regressionRunPlannedCount,
			int regressionInvokedCount,
			int regressionPassedCount,
			int runtimeCouplingAllowedCount,
			int playableReferenceAllowedCount,
			int gameplayAutoApplyAllowedCount,
			int maxRowsPerScenario,
			double minTargetMaxRpmScale,
			double maxEquivalentMaxThrustLossPercent
	) {
	}

	public record DerateControlHookBlackboxRegressionAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int contractScenarioCount,
			int presetSampleCount,
			int regressionCaseCount,
			int regressionRunRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<BlackboxRegressionCaseDefinition> cases,
			List<DerateControlHookBlackboxRegressionRunRow> rows,
			DerateControlHookBlackboxRegressionSummary summary
	) {
		public DerateControlHookBlackboxRegressionAudit {
			cases = List.copyOf(cases);
			rows = List.copyOf(rows);
		}
	}

	public static DerateControlHookBlackboxRegressionAudit audit() {
		return audit(
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate.audit(),
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.audit()
		);
	}

	public static DerateControlHookBlackboxRegressionAudit audit(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
					.DerateControlHookReadinessAudit readinessAudit,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractAudit contractAudit
	) {
		if (readinessAudit == null || contractAudit == null) {
			throw new IllegalArgumentException("readinessAudit and contractAudit are required.");
		}
		List<DerateControlHookBlackboxRegressionRunRow> rows = readinessAudit.rows().stream()
				.filter(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
						.DerateControlHookReadinessRow::controlContractAvailable)
				.flatMap(readiness -> contractRowsForScenario(readiness, contractAudit).stream()
						.flatMap(contract -> CASES.stream().map(caseDefinition -> row(readiness, contract,
								caseDefinition))))
				.toList();
		return new DerateControlHookBlackboxRegressionAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				CONTRACT_SCENARIO_COUNT,
				PRESET_SAMPLE_COUNT,
				REGRESSION_CASE_COUNT,
				REGRESSION_RUN_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				CASES,
				rows,
				summary(rows)
		);
	}

	public static BlackboxRegressionCaseDefinition caseDefinition(String regressionCaseName) {
		return CASES.stream()
				.filter(caseDefinition -> caseDefinition.regressionCaseName().equals(regressionCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate blackbox regression case: " + regressionCaseName));
	}

	public static DerateControlHookBlackboxRegressionRunRow row(
			String scenarioName,
			String presetName,
			String regressionCaseName
	) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName)
						&& row.presetName().equals(presetName)
						&& row.regressionCaseName().equals(regressionCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate blackbox regression row: "
								+ scenarioName + "/" + presetName + "/" + regressionCaseName));
	}

	public static DerateControlHookBlackboxRegressionRunRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
					.DerateControlHookReadinessRow readiness,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contract,
			BlackboxRegressionCaseDefinition caseDefinition
	) {
		if (readiness == null || contract == null || caseDefinition == null) {
			throw new IllegalArgumentException("readiness, contract, and caseDefinition are required.");
		}
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
				.DerateControlHookBlackboxResultReviewRow result = resultFor(readiness, contract, caseDefinition);
		boolean readyForBlackbox = readiness.runtimeHookImplemented()
				&& readiness.motorResponseCouplingReviewed()
				&& readiness.failsafeClampReviewed();
		boolean planned = readyForBlackbox || readiness.implementationReady();
		boolean invoked = result != null;
		boolean passed = invoked && result.blackboxRegressionPassed();
		return new DerateControlHookBlackboxRegressionRunRow(
				readiness.scenarioName(),
				contract.presetName(),
				contract.ambientCaseName(),
				caseDefinition.regressionCaseName(),
				caseDefinition.flightPhase(),
				caseDefinition.targetMetric(),
				caseDefinition.minSampleCount(),
				caseDefinition.maxPrimaryErrorRatio(),
				caseDefinition.maxSecondaryErrorRatio(),
				readiness.implementationReady(),
				planned,
				invoked,
				passed,
				contract.targetMaxRpmScale(),
				contract.contractedMaxRpm(),
				contract.equivalentMaxThrustLossPercent(),
				false,
				false,
				false,
				status(planned, invoked, passed),
				blocker(readiness, planned, invoked, passed),
				nextRequiredAction(readiness, planned, invoked, passed)
		);
	}

	private static PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
			.DerateControlHookBlackboxResultReviewRow resultFor(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
					.DerateControlHookReadinessRow readiness,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contract,
			BlackboxRegressionCaseDefinition caseDefinition
	) {
		if (!"synthetic_derate_validation_all_pass".equals(readiness.scenarioName())) {
			return null;
		}
		return PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview.audit()
				.rows()
				.stream()
				.filter(row -> row.presetName().equals(contract.presetName())
						&& row.regressionCaseName().equals(caseDefinition.regressionCaseName()))
				.findFirst()
				.orElse(null);
	}

	private static String status(boolean planned, boolean invoked, boolean passed) {
		if (invoked) {
			return passed ? "PASS" : "FAIL";
		}
		return planned ? "PENDING_REGRESSION" : "SKIPPED";
	}

	private static String blocker(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
					.DerateControlHookReadinessRow readiness,
			boolean planned,
			boolean invoked,
			boolean passed
	) {
		if (invoked) {
			return passed
					? "target-omega-blackbox-regression-result-passed"
					: "target-omega-blackbox-regression-result-failed";
		}
		return planned ? "blackbox-regression-not-run" : readiness.dominantBlocker();
	}

	private static String nextRequiredAction(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
					.DerateControlHookReadinessRow readiness,
			boolean planned,
			boolean invoked,
			boolean passed
	) {
		if (invoked) {
			return passed
					? "feed-blackbox-acceptance-gate-for-manual-control-hook-review"
					: "investigate-apDrone-cold-forward-punchout-derate-margin";
		}
		return planned
				? "execute-cold-air-target-omega-blackbox-regression-before-runtime-candidate-derate"
				: readiness.nextRequiredAction();
	}

	private static List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
			.DerateControlContractRow> contractRowsForScenario(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
					.DerateControlHookReadinessRow readiness,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractAudit contractAudit
	) {
		String sourceScenario = "synthetic_control_hook_ready_reviewed".equals(readiness.scenarioName())
				? "synthetic_derate_validation_all_pass"
				: readiness.scenarioName();
		return contractAudit.rows().stream()
				.filter(row -> sourceScenario.equals(row.scenarioName()))
				.toList();
	}

	private static DerateControlHookBlackboxRegressionSummary summary(
			List<DerateControlHookBlackboxRegressionRunRow> rows
	) {
		int planned = 0;
		int invoked = 0;
		int passed = 0;
		int runtime = 0;
		int playable = 0;
		int gameplay = 0;
		double minScale = rows.isEmpty() ? 0.0 : Double.POSITIVE_INFINITY;
		double maxThrustLoss = 0.0;
		Map<String, Integer> rowsByScenario = new HashMap<>();
		Map<String, Boolean> readyByScenario = new HashMap<>();
		for (DerateControlHookBlackboxRegressionRunRow row : rows) {
			rowsByScenario.merge(row.scenarioName(), 1, Integer::sum);
			readyByScenario.merge(row.scenarioName(), row.controlHookImplementationReady(), Boolean::logicalOr);
			if (row.regressionRunPlanned()) {
				planned++;
			}
			if (row.regressionInvoked()) {
				invoked++;
			}
			if (row.regressionPassed()) {
				passed++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.playableReferenceAllowed()) {
				playable++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			minScale = Math.min(minScale, row.targetMaxRpmScale());
			maxThrustLoss = Math.max(maxThrustLoss, row.equivalentMaxThrustLossPercent());
		}
		int maxRows = rowsByScenario.values().stream()
				.mapToInt(Integer::intValue)
				.max()
				.orElse(0);
		int readyScenarios = (int) readyByScenario.values().stream()
				.filter(Boolean::booleanValue)
				.count();
		return new DerateControlHookBlackboxRegressionSummary(
				rows.size(),
				REGRESSION_CASE_COUNT,
				rowsByScenario.size(),
				readyScenarios,
				planned,
				invoked,
				passed,
				runtime,
				playable,
				gameplay,
				maxRows,
				rows.isEmpty() ? 0.0 : minScale,
				maxThrustLoss
		);
	}
}
