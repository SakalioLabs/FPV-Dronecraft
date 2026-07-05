package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-State-Normalized-Free-Flight-Trajectory-Envelope-Packet";
	public static final String CAVEAT =
			"State-normalized free-flight trajectory envelope compares released APDrone punchout states against held-kinematics rows; it explains the trajectory-release blocker before runtime review and does not enable runtime coupling, playable export, or gameplay auto-apply.";
	public static final String NEXT_REQUIRED_ACTION =
			"fit-free-flight-trajectory-release-damping-or-state-coupling-before-runtime-review";
	public static final double THRUST_MARGIN_FAILURE_THRESHOLD =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
					.RESIDUAL_DEFICIT_THRESHOLD;
	public static final double STATE_VELOCITY_ENVELOPE_THRESHOLD_METERS_PER_SECOND =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
					.STATE_DIVERGENCE_VELOCITY_DELTA_THRESHOLD_METERS_PER_SECOND;
	public static final double HELD_STATE_DELTA_THRESHOLD = 1.0e-12;
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int ENVELOPE_ROW_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
					.SPEED_SAMPLE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 33;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ ENVELOPE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double EPSILON = 1.0e-9;
	private static volatile StateNormalizedFreeFlightTrajectoryEnvelopeAudit cachedAudit;

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope() {
	}

	public record StateNormalizedFreeFlightTrajectoryEnvelopeRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			double forwardSpeedMetersPerSecond,
			int peakSampleIndex,
			double peakTimeSeconds,
			double targetMaxRpmScale,
			double contractThrustRatio,
			double releaseThrustLossOverContractRatio,
			double releaseObservedThrustRatio,
			double heldObservedThrustRatio,
			double observedRecoveryRatio,
			double releaseResidualThrustDeficitRatio,
			double heldResidualThrustDeficitRatio,
			double residualReductionRatio,
			double releaseStateVelocityDeltaMetersPerSecond,
			double releaseAttitudeEulerDeltaRadians,
			double releaseAngularVelocityDeltaRadiansPerSecond,
			double heldStateVelocityDeltaMetersPerSecond,
			double releaseDeratedPropellerThrustScaleRange,
			double heldDeratedPropellerThrustScaleRange,
			double releaseDeratedAdvanceRatioRange,
			double heldDeratedAdvanceRatioRange,
			boolean releaseThrustMarginFailure,
			boolean releaseStateEnvelopeExceeded,
			boolean heldResidualEnvelopeCleared,
			boolean heldStateEnvelopeCleared,
			boolean freeFlightTrajectoryReleaseBlocked,
			boolean freeFlightTrajectoryReviewAllowed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String envelopeBucket,
			String status,
			String nextRequiredAction,
			String message
	) {
	}

	public record StateNormalizedFreeFlightTrajectoryEnvelopeSummary(
			int rowCount,
			int releaseThrustMarginFailureRowCount,
			int releaseStateEnvelopeExceededRowCount,
			int heldResidualEnvelopeClearedRowCount,
			int heldStateEnvelopeClearedRowCount,
			int freeFlightTrajectoryReleaseBlockedRowCount,
			double maxReleaseThrustLossOverContractRatio,
			double maxReleaseResidualThrustDeficitRatio,
			double maxHeldResidualThrustDeficitRatio,
			double minResidualReductionRatio,
			double maxObservedRecoveryRatio,
			double maxReleaseStateVelocityDeltaMetersPerSecond,
			double maxReleaseAttitudeEulerDeltaRadians,
			double maxReleaseAngularVelocityDeltaRadiansPerSecond,
			double maxReleaseDeratedPropellerThrustScaleRange,
			double maxHeldDeratedPropellerThrustScaleRange,
			double blackboxSpeedReleaseStateVelocityDeltaMetersPerSecond,
			double blackboxSpeedReleaseResidualThrustDeficitRatio,
			double blackboxSpeedHeldResidualThrustDeficitRatio,
			double blackboxSpeedResidualReductionRatio,
			double fastSpeedReleaseStateVelocityDeltaMetersPerSecond,
			double fastSpeedReleaseResidualThrustDeficitRatio,
			double fastSpeedHeldResidualThrustDeficitRatio,
			double fastSpeedResidualReductionRatio,
			boolean currentFreeFlightBlackboxAcceptanceReady,
			boolean stateNormalizedLabAcceptanceReady,
			boolean freeFlightTrajectoryReviewAllowed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String dominantBucket,
			String nextRequiredAction
	) {
	}

	public record StateNormalizedFreeFlightTrajectoryEnvelopeAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int envelopeRowCount,
			int summaryRowCount,
			int methodRowCount,
			double thrustMarginFailureThreshold,
			double stateVelocityEnvelopeThresholdMetersPerSecond,
			double heldStateDeltaThreshold,
			List<StateNormalizedFreeFlightTrajectoryEnvelopeRow> rows,
			StateNormalizedFreeFlightTrajectoryEnvelopeSummary summary
	) {
		public StateNormalizedFreeFlightTrajectoryEnvelopeAudit {
			rows = List.copyOf(rows);
		}
	}

	public static StateNormalizedFreeFlightTrajectoryEnvelopeAudit audit() {
		StateNormalizedFreeFlightTrajectoryEnvelopeAudit cached = cachedAudit;
		if (cached != null) {
			return cached;
		}
		cached = buildAudit();
		cachedAudit = cached;
		return cached;
	}

	private static StateNormalizedFreeFlightTrajectoryEnvelopeAudit buildAudit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
				.StateNormalizedFreeFlightHandoffSummary handoffSummary =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
								.audit()
								.summary();
		List<StateNormalizedFreeFlightTrajectoryEnvelopeRow> rows =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
						.audit()
						.rows()
						.stream()
						.map(row -> row(row, handoffSummary))
						.toList();
		return new StateNormalizedFreeFlightTrajectoryEnvelopeAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				ENVELOPE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				THRUST_MARGIN_FAILURE_THRESHOLD,
				STATE_VELOCITY_ENVELOPE_THRESHOLD_METERS_PER_SECOND,
				HELD_STATE_DELTA_THRESHOLD,
				rows,
				summary(rows, handoffSummary)
		);
	}

	public static StateNormalizedFreeFlightTrajectoryEnvelopeRow row(double forwardSpeedMetersPerSecond) {
		return audit().rows().stream()
				.filter(row -> Double.compare(row.forwardSpeedMetersPerSecond(), forwardSpeedMetersPerSecond) == 0)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown APDrone state-normalized free-flight trajectory envelope row: "
								+ forwardSpeedMetersPerSecond));
	}

	private static StateNormalizedFreeFlightTrajectoryEnvelopeRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
					.StateNormalizedPunchoutRow held,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
					.StateNormalizedFreeFlightHandoffSummary handoffSummary
	) {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
				.RotorStateDispersionRow release =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
								.row(held.forwardSpeedMetersPerSecond());
		boolean releaseMarginFailure = release.thrustLossOverContractRatio() > THRUST_MARGIN_FAILURE_THRESHOLD;
		boolean releaseStateExceeded =
				release.stateVelocityDeltaMetersPerSecond() > STATE_VELOCITY_ENVELOPE_THRESHOLD_METERS_PER_SECOND;
		boolean heldResidualCleared = held.heldResidualThrustDeficitRatio() <= THRUST_MARGIN_FAILURE_THRESHOLD;
		boolean heldStateCleared = held.heldStateVelocityDeltaMetersPerSecond() <= HELD_STATE_DELTA_THRESHOLD
				&& held.heldAttitudeEulerDeltaRadians() <= HELD_STATE_DELTA_THRESHOLD
				&& held.heldAngularVelocityDeltaRadiansPerSecond() <= HELD_STATE_DELTA_THRESHOLD;
		boolean releaseBlocked = releaseMarginFailure && heldResidualCleared && heldStateCleared;
		String bucket = bucketFor(releaseMarginFailure, releaseStateExceeded, heldResidualCleared, heldStateCleared);
		return new StateNormalizedFreeFlightTrajectoryEnvelopeRow(
				held.scenarioName(),
				held.presetName(),
				held.ambientCaseName(),
				held.forwardSpeedMetersPerSecond(),
				held.peakSampleIndex(),
				held.peakTimeSeconds(),
				held.targetMaxRpmScale(),
				held.contractThrustRatio(),
				release.thrustLossOverContractRatio(),
				held.freeFlightObservedThrustRatio(),
				held.heldObservedThrustRatio(),
				held.heldObservedMinusFreeFlightRatio(),
				held.freeFlightResidualThrustDeficitRatio(),
				held.heldResidualThrustDeficitRatio(),
				held.heldResidualReductionRatio(),
				release.stateVelocityDeltaMetersPerSecond(),
				release.attitudeEulerDeltaRadians(),
				release.angularVelocityDeltaRadiansPerSecond(),
				held.heldStateVelocityDeltaMetersPerSecond(),
				release.deratedPropellerThrustScaleRange(),
				held.heldDeratedPropellerThrustScaleRange(),
				release.deratedAdvanceRatioRange(),
				held.heldDeratedAdvanceRatioRange(),
				releaseMarginFailure,
				releaseStateExceeded,
				heldResidualCleared,
				heldStateCleared,
				releaseBlocked,
				handoffSummary.freeFlightTrajectoryReviewAllowed(),
				false,
				false,
				false,
				bucket,
				releaseBlocked ? "TRAJECTORY_RELEASE_BLOCKED" : "TRAJECTORY_RELEASE_REVIEW",
				releaseBlocked ? NEXT_REQUIRED_ACTION : "inspect-trajectory-envelope-row",
				messageFor(bucket)
		);
	}

	private static StateNormalizedFreeFlightTrajectoryEnvelopeSummary summary(
			List<StateNormalizedFreeFlightTrajectoryEnvelopeRow> rows,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
					.StateNormalizedFreeFlightHandoffSummary handoffSummary
	) {
		int releaseFailures = 0;
		int releaseStateExceeded = 0;
		int heldResidualCleared = 0;
		int heldStateCleared = 0;
		int releaseBlocked = 0;
		double maxReleaseThrustLoss = 0.0;
		double maxReleaseResidual = 0.0;
		double maxHeldResidual = 0.0;
		double minReduction = Double.POSITIVE_INFINITY;
		double maxRecovery = Double.NEGATIVE_INFINITY;
		double maxVelocity = 0.0;
		double maxAttitude = 0.0;
		double maxAngular = 0.0;
		double maxReleasePropRange = 0.0;
		double maxHeldPropRange = 0.0;
		double blackboxVelocity = 0.0;
		double blackboxReleaseResidual = 0.0;
		double blackboxHeldResidual = 0.0;
		double blackboxReduction = 0.0;
		double fastVelocity = 0.0;
		double fastReleaseResidual = 0.0;
		double fastHeldResidual = 0.0;
		double fastReduction = 0.0;
		for (StateNormalizedFreeFlightTrajectoryEnvelopeRow row : rows) {
			if (row.releaseThrustMarginFailure()) {
				releaseFailures++;
			}
			if (row.releaseStateEnvelopeExceeded()) {
				releaseStateExceeded++;
			}
			if (row.heldResidualEnvelopeCleared()) {
				heldResidualCleared++;
			}
			if (row.heldStateEnvelopeCleared()) {
				heldStateCleared++;
			}
			if (row.freeFlightTrajectoryReleaseBlocked()) {
				releaseBlocked++;
			}
			maxReleaseThrustLoss = Math.max(maxReleaseThrustLoss, row.releaseThrustLossOverContractRatio());
			maxReleaseResidual = Math.max(maxReleaseResidual, row.releaseResidualThrustDeficitRatio());
			maxHeldResidual = Math.max(maxHeldResidual, row.heldResidualThrustDeficitRatio());
			minReduction = Math.min(minReduction, row.residualReductionRatio());
			maxRecovery = Math.max(maxRecovery, row.observedRecoveryRatio());
			maxVelocity = Math.max(maxVelocity, row.releaseStateVelocityDeltaMetersPerSecond());
			maxAttitude = Math.max(maxAttitude, row.releaseAttitudeEulerDeltaRadians());
			maxAngular = Math.max(maxAngular, row.releaseAngularVelocityDeltaRadiansPerSecond());
			maxReleasePropRange = Math.max(maxReleasePropRange, row.releaseDeratedPropellerThrustScaleRange());
			maxHeldPropRange = Math.max(maxHeldPropRange, row.heldDeratedPropellerThrustScaleRange());
			if (Double.compare(row.forwardSpeedMetersPerSecond(), 22.0) == 0) {
				blackboxVelocity = row.releaseStateVelocityDeltaMetersPerSecond();
				blackboxReleaseResidual = row.releaseResidualThrustDeficitRatio();
				blackboxHeldResidual = row.heldResidualThrustDeficitRatio();
				blackboxReduction = row.residualReductionRatio();
			}
			if (Double.compare(row.forwardSpeedMetersPerSecond(), 28.0) == 0) {
				fastVelocity = row.releaseStateVelocityDeltaMetersPerSecond();
				fastReleaseResidual = row.releaseResidualThrustDeficitRatio();
				fastHeldResidual = row.heldResidualThrustDeficitRatio();
				fastReduction = row.residualReductionRatio();
			}
		}
		boolean reviewAllowed = handoffSummary.freeFlightTrajectoryReviewAllowed()
				&& releaseBlocked > 0
				&& heldResidualCleared == rows.size()
				&& heldStateCleared == rows.size();
		String dominantBucket = releaseStateExceeded >= 3
				? "released-trajectory-state-divergence-with-held-residual-clearance"
				: "released-thrust-margin-failure-with-held-local-clearance";
		return new StateNormalizedFreeFlightTrajectoryEnvelopeSummary(
				rows.size(),
				releaseFailures,
				releaseStateExceeded,
				heldResidualCleared,
				heldStateCleared,
				releaseBlocked,
				maxReleaseThrustLoss,
				maxReleaseResidual,
				maxHeldResidual,
				Double.isInfinite(minReduction) ? 0.0 : minReduction,
				Double.isInfinite(maxRecovery) ? 0.0 : maxRecovery,
				maxVelocity,
				maxAttitude,
				maxAngular,
				maxReleasePropRange,
				maxHeldPropRange,
				blackboxVelocity,
				blackboxReleaseResidual,
				blackboxHeldResidual,
				blackboxReduction,
				fastVelocity,
				fastReleaseResidual,
				fastHeldResidual,
				fastReduction,
				handoffSummary.currentFreeFlightBlackboxAcceptanceReady(),
				handoffSummary.stateNormalizedLabAcceptanceReady(),
				reviewAllowed,
				false,
				false,
				false,
				reviewAllowed ? "TRAJECTORY_ENVELOPE_READY" : "BLOCKED",
				dominantBucket,
				reviewAllowed ? NEXT_REQUIRED_ACTION : "inspect-free-flight-trajectory-envelope-blockers"
		);
	}

	private static String bucketFor(
			boolean releaseMarginFailure,
			boolean releaseStateExceeded,
			boolean heldResidualCleared,
			boolean heldStateCleared
	) {
		if (releaseMarginFailure && releaseStateExceeded && heldResidualCleared && heldStateCleared) {
			return "released-state-divergence-held-residual-cleared";
		}
		if (releaseMarginFailure && heldResidualCleared && heldStateCleared) {
			return "released-thrust-margin-failure-held-local-clearance";
		}
		if (!heldResidualCleared || !heldStateCleared) {
			return "held-envelope-still-blocked";
		}
		return "trajectory-envelope-review-only";
	}

	private static String messageFor(String bucket) {
		return switch (bucket) {
			case "released-state-divergence-held-residual-cleared" ->
					"released-free-flight-state-diverges-while-held-local-residual-clears";
			case "released-thrust-margin-failure-held-local-clearance" ->
					"released-free-flight-thrust-margin-fails-while-held-local-envelope-clears";
			case "held-envelope-still-blocked" ->
					"held-state-envelope-still-blocks-trajectory-review";
			default -> "trajectory-envelope-row-is-review-material-only";
		};
	}

	public static double residualAmplificationRatio(double releaseResidual, double heldResidual) {
		return releaseResidual / Math.max(EPSILON, heldResidual);
	}
}
