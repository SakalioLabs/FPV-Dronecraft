package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-State-Normalized-Free-Flight-Handoff-Gate-Packet";
	public static final String CAVEAT =
			"State-normalized free-flight handoff gate separates held-kinematics lab acceptance from the still-blocked APDrone free-flight trajectory; it opens only trajectory review material, while manual control-hook review, runtime implementation, runtime coupling, playable export, and gameplay auto-apply remain closed.";
	public static final String NEXT_REQUIRED_ACTION =
			"build-free-flight-trajectory-hold-vs-release-envelope-before-runtime-review";
	public static final String DOMINANT_BLOCKER =
			"apDrone-cold-air-forward-punchout-free-flight-trajectory-release";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int HANDOFF_ROW_COUNT = 5;
	public static final int SUMMARY_ROW_COUNT = 24;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ HANDOFF_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final String FORWARD_PUNCHOUT_CASE = "cold_air_forward_punchout_margin";

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate() {
	}

	public record StateNormalizedFreeFlightHandoffRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			String regressionCaseName,
			String handoffStage,
			String metricName,
			String metricValue,
			String unit,
			boolean currentFreeFlightBlackboxAcceptanceReady,
			boolean stateNormalizedLabAcceptanceReady,
			boolean freeFlightRegressionPassed,
			boolean stateNormalizedRegressionPassed,
			boolean trajectoryBlocker,
			boolean freeFlightTrajectoryReviewRequired,
			boolean freeFlightTrajectoryReviewAllowed,
			boolean manualControlHookReviewAllowed,
			boolean runtimeImplementationAllowed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			double freeFlightSecondaryErrorRatio,
			double stateNormalizedSecondaryErrorRatio,
			double heldResidualThrustDeficitRatio,
			String status,
			String nextRequiredAction,
			String message
	) {
	}

	public record StateNormalizedFreeFlightHandoffSummary(
			int rowCount,
			int freeFlightPassedRowCount,
			int freeFlightFailedRowCount,
			int stateNormalizedEvidenceAppliedRowCount,
			int stateNormalizedPassedRowCount,
			int stateNormalizedFailedRowCount,
			int trajectoryBlockerRowCount,
			double maxFreeFlightSecondaryErrorRatio,
			double maxStateNormalizedSecondaryErrorRatio,
			double apDroneFreeFlightSecondaryErrorRatio,
			double apDroneStateNormalizedSecondaryErrorRatio,
			double apDroneHeldResidualThrustDeficitRatio,
			boolean currentFreeFlightBlackboxAcceptanceReady,
			boolean stateNormalizedLabAcceptanceReady,
			boolean freeFlightTrajectoryReviewRequired,
			boolean freeFlightTrajectoryReviewAllowed,
			boolean manualControlHookReviewAllowed,
			boolean runtimeImplementationAllowed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String dominantBlocker,
			String nextRequiredAction
	) {
	}

	public record StateNormalizedFreeFlightHandoffAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int handoffRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<StateNormalizedFreeFlightHandoffRow> rows,
			StateNormalizedFreeFlightHandoffSummary summary
	) {
		public StateNormalizedFreeFlightHandoffAudit {
			rows = List.copyOf(rows);
		}
	}

	public static StateNormalizedFreeFlightHandoffAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
				.StateNormalizedBlackboxAcceptanceAudit upstream =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
								.audit();
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
				.StateNormalizedBlackboxAcceptanceSummary upstreamSummary = upstream.summary();
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
				.StateNormalizedBlackboxAcceptanceRow racingForward =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
								.row("racingQuad", FORWARD_PUNCHOUT_CASE);
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
				.StateNormalizedBlackboxAcceptanceRow apDroneForward =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
								.row("apDrone", FORWARD_PUNCHOUT_CASE);
		boolean trajectoryReviewRequired = trajectoryReviewRequired(upstreamSummary, apDroneForward);
		boolean trajectoryReviewAllowed = trajectoryReviewAllowed(upstreamSummary, apDroneForward);
		List<StateNormalizedFreeFlightHandoffRow> rows = List.of(
				freeFlightRow(upstreamSummary, trajectoryReviewRequired, trajectoryReviewAllowed),
				stateNormalizedLabRow(upstreamSummary),
				presetForwardRow(racingForward, upstreamSummary, trajectoryReviewRequired, trajectoryReviewAllowed),
				presetForwardRow(apDroneForward, upstreamSummary, trajectoryReviewRequired, trajectoryReviewAllowed),
				handoffGuardRow(upstreamSummary, apDroneForward, trajectoryReviewRequired, trajectoryReviewAllowed)
		);
		return new StateNormalizedFreeFlightHandoffAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				HANDOFF_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				summary(upstreamSummary, apDroneForward, rows, trajectoryReviewRequired, trajectoryReviewAllowed)
		);
	}

	public static StateNormalizedFreeFlightHandoffRow row(String scenarioName) {
		return audit().rows().stream()
				.filter(row -> row.scenarioName().equals(scenarioName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune state-normalized free-flight handoff row: " + scenarioName));
	}

	private static StateNormalizedFreeFlightHandoffRow freeFlightRow(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
					.StateNormalizedBlackboxAcceptanceSummary upstream,
			boolean trajectoryReviewRequired,
			boolean trajectoryReviewAllowed
	) {
		return new StateNormalizedFreeFlightHandoffRow(
				"current_free_flight_blackbox",
				"all",
				"all",
				"all",
				"free_flight_acceptance",
				"free_flight_failed_row_count",
				Integer.toString(upstream.freeFlightFailedRowCount()),
				"count",
				upstream.currentFreeFlightBlackboxAcceptanceReady(),
				upstream.stateNormalizedLabAcceptanceReady(),
				upstream.freeFlightFailedRowCount() == 0,
				upstream.stateNormalizedFailedRowCount() == 0,
				trajectoryReviewRequired,
				trajectoryReviewRequired,
				trajectoryReviewAllowed,
				false,
				false,
				false,
				false,
				false,
				upstream.maxFreeFlightSecondaryErrorRatio(),
				upstream.maxStateNormalizedSecondaryErrorRatio(),
				upstream.maxHeldResidualThrustDeficitRatio(),
				upstream.currentFreeFlightBlackboxAcceptanceReady() ? "FREE_FLIGHT_READY" : "FREE_FLIGHT_BLOCKED",
				trajectoryReviewRequired ? NEXT_REQUIRED_ACTION : "no-free-flight-trajectory-review-required",
				upstream.currentFreeFlightBlackboxAcceptanceReady()
						? "current-free-flight-blackbox-acceptance-ready"
						: "current-free-flight-blackbox-acceptance-blocked-by-apDrone-forward-punchout"
		);
	}

	private static StateNormalizedFreeFlightHandoffRow stateNormalizedLabRow(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
					.StateNormalizedBlackboxAcceptanceSummary upstream
	) {
		return new StateNormalizedFreeFlightHandoffRow(
				"state_normalized_lab_blackbox",
				"all",
				"all",
				"all",
				"state_normalized_lab_acceptance",
				"state_normalized_passed_row_count",
				Integer.toString(upstream.stateNormalizedPassedRowCount()),
				"count",
				upstream.currentFreeFlightBlackboxAcceptanceReady(),
				upstream.stateNormalizedLabAcceptanceReady(),
				upstream.freeFlightFailedRowCount() == 0,
				upstream.stateNormalizedFailedRowCount() == 0,
				false,
				false,
				false,
				false,
				false,
				false,
				false,
				false,
				upstream.maxFreeFlightSecondaryErrorRatio(),
				upstream.maxStateNormalizedSecondaryErrorRatio(),
				upstream.maxHeldResidualThrustDeficitRatio(),
				upstream.stateNormalizedLabAcceptanceReady() ? "STATE_NORMALIZED_READY" : "BLOCKED",
				upstream.stateNormalizedLabAcceptanceReady()
						? "inspect-free-flight-trajectory-before-runtime-review"
						: "inspect-state-normalized-blackbox-failures-before-handoff",
				upstream.stateNormalizedLabAcceptanceReady()
						? "state-normalized-lab-acceptance-ready-for-trajectory-handoff"
						: "state-normalized-lab-acceptance-not-ready"
		);
	}

	private static StateNormalizedFreeFlightHandoffRow presetForwardRow(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
					.StateNormalizedBlackboxAcceptanceRow upstreamRow,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
					.StateNormalizedBlackboxAcceptanceSummary upstreamSummary,
			boolean trajectoryReviewRequired,
			boolean trajectoryReviewAllowed
	) {
		boolean blocker = !upstreamRow.freeFlightRegressionPassed()
				&& upstreamRow.stateNormalizedRegressionPassed();
		return new StateNormalizedFreeFlightHandoffRow(
				upstreamRow.presetName() + "_forward_punchout",
				upstreamRow.presetName(),
				upstreamRow.ambientCaseName(),
				upstreamRow.regressionCaseName(),
				"preset_forward_punchout",
				"state_normalized_secondary_error_ratio",
				Double.toString(upstreamRow.stateNormalizedSecondaryErrorRatio()),
				"ratio",
				upstreamSummary.currentFreeFlightBlackboxAcceptanceReady(),
				upstreamSummary.stateNormalizedLabAcceptanceReady(),
				upstreamRow.freeFlightRegressionPassed(),
				upstreamRow.stateNormalizedRegressionPassed(),
				blocker,
				blocker && trajectoryReviewRequired,
				blocker && trajectoryReviewAllowed,
				false,
				false,
				false,
				false,
				false,
				upstreamRow.freeFlightSecondaryErrorRatio(),
				upstreamRow.stateNormalizedSecondaryErrorRatio(),
				upstreamRow.heldResidualThrustDeficitRatio(),
				blocker ? "TRAJECTORY_BLOCKED" : "FREE_FLIGHT_PASS",
				blocker ? NEXT_REQUIRED_ACTION : "no-preset-trajectory-blocker",
				blocker
						? "state-normalized-pass-exposes-free-flight-trajectory-release-blocker"
						: "forward-punchout-free-flight-and-state-normalized-rows-both-pass"
		);
	}

	private static StateNormalizedFreeFlightHandoffRow handoffGuardRow(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
					.StateNormalizedBlackboxAcceptanceSummary upstream,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
					.StateNormalizedBlackboxAcceptanceRow apDroneForward,
			boolean trajectoryReviewRequired,
			boolean trajectoryReviewAllowed
	) {
		return new StateNormalizedFreeFlightHandoffRow(
				"handoff_guard",
				"all",
				apDroneForward.ambientCaseName(),
				apDroneForward.regressionCaseName(),
				"state_normalized_to_free_flight_guard",
				"free_flight_trajectory_review_allowed",
				Boolean.toString(trajectoryReviewAllowed),
				"bool",
				upstream.currentFreeFlightBlackboxAcceptanceReady(),
				upstream.stateNormalizedLabAcceptanceReady(),
				upstream.freeFlightFailedRowCount() == 0,
				upstream.stateNormalizedFailedRowCount() == 0,
				trajectoryReviewRequired,
				trajectoryReviewRequired,
				trajectoryReviewAllowed,
				false,
				false,
				false,
				false,
				false,
				upstream.maxFreeFlightSecondaryErrorRatio(),
				apDroneForward.stateNormalizedSecondaryErrorRatio(),
				apDroneForward.heldResidualThrustDeficitRatio(),
				trajectoryReviewAllowed ? "TRAJECTORY_REVIEW_READY" : "BLOCKED",
				trajectoryReviewAllowed ? NEXT_REQUIRED_ACTION : "inspect-state-normalized-handoff-blockers",
				trajectoryReviewAllowed
						? "lab-harness-passes-but-free-flight-trajectory-needs-hold-vs-release-envelope"
						: "trajectory-review-not-ready"
		);
	}

	private static StateNormalizedFreeFlightHandoffSummary summary(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
					.StateNormalizedBlackboxAcceptanceSummary upstream,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
					.StateNormalizedBlackboxAcceptanceRow apDroneForward,
			List<StateNormalizedFreeFlightHandoffRow> rows,
			boolean trajectoryReviewRequired,
			boolean trajectoryReviewAllowed
	) {
		int blockers = 0;
		for (StateNormalizedFreeFlightHandoffRow row : rows) {
			if (row.trajectoryBlocker() && "preset_forward_punchout".equals(row.handoffStage())) {
				blockers++;
			}
		}
		return new StateNormalizedFreeFlightHandoffSummary(
				rows.size(),
				upstream.freeFlightPassedRowCount(),
				upstream.freeFlightFailedRowCount(),
				upstream.stateNormalizedEvidenceAppliedRowCount(),
				upstream.stateNormalizedPassedRowCount(),
				upstream.stateNormalizedFailedRowCount(),
				blockers,
				upstream.maxFreeFlightSecondaryErrorRatio(),
				upstream.maxStateNormalizedSecondaryErrorRatio(),
				apDroneForward.freeFlightSecondaryErrorRatio(),
				apDroneForward.stateNormalizedSecondaryErrorRatio(),
				apDroneForward.heldResidualThrustDeficitRatio(),
				upstream.currentFreeFlightBlackboxAcceptanceReady(),
				upstream.stateNormalizedLabAcceptanceReady(),
				trajectoryReviewRequired,
				trajectoryReviewAllowed,
				false,
				false,
				false,
				false,
				false,
				trajectoryReviewAllowed ? "TRAJECTORY_REVIEW_READY" : "BLOCKED",
				trajectoryReviewRequired ? DOMINANT_BLOCKER : "none",
				trajectoryReviewAllowed ? NEXT_REQUIRED_ACTION : "inspect-state-normalized-handoff-blockers"
		);
	}

	private static boolean trajectoryReviewRequired(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
					.StateNormalizedBlackboxAcceptanceSummary upstream,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
					.StateNormalizedBlackboxAcceptanceRow apDroneForward
	) {
		return upstream.stateNormalizedLabAcceptanceReady()
				&& !upstream.currentFreeFlightBlackboxAcceptanceReady()
				&& !apDroneForward.freeFlightRegressionPassed()
				&& apDroneForward.stateNormalizedRegressionPassed();
	}

	private static boolean trajectoryReviewAllowed(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
					.StateNormalizedBlackboxAcceptanceSummary upstream,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
					.StateNormalizedBlackboxAcceptanceRow apDroneForward
	) {
		return trajectoryReviewRequired(upstream, apDroneForward)
				&& upstream.stateNormalizedFailedRowCount() == 0
				&& upstream.stateNormalizedPhysicalConstraintViolationCount() == 0;
	}
}
