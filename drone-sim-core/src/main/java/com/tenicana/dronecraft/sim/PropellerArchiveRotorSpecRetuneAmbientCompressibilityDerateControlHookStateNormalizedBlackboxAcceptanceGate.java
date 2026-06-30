package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-State-Normalized-Blackbox-Acceptance-Gate-Packet";
	public static final String CAVEAT =
			"State-normalized blackbox acceptance gate applies direct held-kinematics forward-punchout regression results to the cold-air forward-punchout lab rows; free-flight blackbox acceptance, manual control-hook review, runtime coupling, playable export, and gameplay auto-apply remain closed.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 9;
	public static final int RESULT_ROW_COUNT =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.CONTRACT_ROW_COUNT
					* PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
							.REGRESSION_CASE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 22;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ RESULT_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double RESIDUAL_DEFICIT_THRESHOLD =
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
					.RESIDUAL_DEFICIT_THRESHOLD;
	private static final double STATE_DELTA_EPSILON = 1.0e-12;

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate() {
	}

	public record StateNormalizedBlackboxAcceptanceRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			String regressionCaseName,
			String flightPhase,
			String targetMetric,
			int sampleCount,
			int minSampleCount,
			double freeFlightPrimaryErrorRatio,
			double freeFlightSecondaryErrorRatio,
			int freeFlightPhysicalConstraintViolationCount,
			boolean freeFlightRegressionPassed,
			boolean stateNormalizedEvidenceApplied,
			double stateNormalizedPrimaryErrorRatio,
			double stateNormalizedSecondaryErrorRatio,
			int stateNormalizedPhysicalConstraintViolationCount,
			double maxAllowedPrimaryErrorRatio,
			double maxAllowedSecondaryErrorRatio,
			double contractThrustRatio,
			double heldObservedThrustRatio,
			double heldResidualThrustDeficitRatio,
			double heldResidualReductionRatio,
			double heldStateVelocityDeltaMetersPerSecond,
			boolean stateNormalizedRegressionPassed,
			boolean freeFlightAcceptanceStillBlocked,
			boolean stateNormalizedLabAcceptanceAllowed,
			boolean manualControlHookReviewAllowed,
			boolean runtimeImplementationAllowed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record StateNormalizedBlackboxAcceptanceSummary(
			int rowCount,
			int freeFlightPassedRowCount,
			int freeFlightFailedRowCount,
			int stateNormalizedEvidenceAppliedRowCount,
			int stateNormalizedPassedRowCount,
			int stateNormalizedFailedRowCount,
			int stateNormalizedPhysicalConstraintViolationCount,
			double maxFreeFlightSecondaryErrorRatio,
			double maxStateNormalizedSecondaryErrorRatio,
			double maxHeldResidualThrustDeficitRatio,
			double blackboxSpeedHeldObservedThrustRatio,
			double blackboxSpeedHeldResidualThrustDeficitRatio,
			double blackboxSpeedResidualReductionRatio,
			boolean currentFreeFlightBlackboxAcceptanceReady,
			boolean stateNormalizedLabAcceptanceReady,
			boolean manualControlHookReviewAllowed,
			boolean runtimeImplementationAllowed,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String nextRequiredAction
	) {
	}

	public record StateNormalizedBlackboxAcceptanceAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int resultRowCount,
			int summaryRowCount,
			int methodRowCount,
			double residualDeficitThreshold,
			double stateDeltaEpsilon,
			List<StateNormalizedBlackboxAcceptanceRow> rows,
			StateNormalizedBlackboxAcceptanceSummary summary
	) {
		public StateNormalizedBlackboxAcceptanceAudit {
			rows = List.copyOf(rows);
		}
	}

	public static StateNormalizedBlackboxAcceptanceAudit audit() {
		List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
				.DerateControlHookBlackboxResultReviewRow> resultRows =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
								.audit()
								.rows();
		boolean currentFreeFlightBlackboxAcceptanceReady = !resultRows.isEmpty()
				&& resultRows.stream().allMatch(
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
								.DerateControlHookBlackboxResultReviewRow::blackboxRegressionPassed);
		List<StateNormalizedBlackboxAcceptanceRow> rows = resultRows.stream()
				.map(result -> row(result, currentFreeFlightBlackboxAcceptanceReady))
				.toList();
		return new StateNormalizedBlackboxAcceptanceAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				RESULT_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				RESIDUAL_DEFICIT_THRESHOLD,
				STATE_DELTA_EPSILON,
				rows,
				summary(rows, currentFreeFlightBlackboxAcceptanceReady)
		);
	}

	public static StateNormalizedBlackboxAcceptanceRow row(
			String presetName,
			String regressionCaseName
	) {
		return audit().rows().stream()
				.filter(row -> row.presetName().equals(presetName)
						&& row.regressionCaseName().equals(regressionCaseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate state-normalized blackbox acceptance row: "
								+ presetName + "/" + regressionCaseName));
	}

	private static StateNormalizedBlackboxAcceptanceRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
					.DerateControlHookBlackboxResultReviewRow result,
			boolean currentFreeFlightBlackboxAcceptanceReady
	) {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow contract =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.row(
								result.scenarioName(),
								result.presetName(),
								result.ambientCaseName());
		double contractThrustRatio = 1.0 - contract.equivalentMaxThrustLossPercent() / 100.0;
		boolean evidenceApplied = "apDrone".equals(result.presetName())
				&& "cold_air_forward_punchout_margin".equals(result.regressionCaseName());
		boolean forwardPunchout = PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
				.REGRESSION_CASE_NAME
				.equals(result.regressionCaseName());
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
				.StateNormalizedBlackboxResultReviewRow normalized = forwardPunchout
						? PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
								.row(result.presetName(), result.regressionCaseName())
						: null;
		double normalizedPrimary = result.primaryErrorRatio();
		double normalizedSecondary = result.secondaryErrorRatio();
		int normalizedViolations = result.physicalConstraintViolationCount();
		double heldObserved = 0.0;
		double heldResidual = 0.0;
		double heldReduction = 0.0;
		double heldStateDelta = 0.0;
		if (normalized != null) {
			heldObserved = normalized.heldObservedThrustRatio();
			heldResidual = normalized.heldResidualThrustDeficitRatio();
			heldReduction = normalized.freeFlightSecondaryErrorRatio() > 0.0
					? (normalized.freeFlightSecondaryErrorRatio() - normalized.stateNormalizedSecondaryErrorRatio())
							/ normalized.freeFlightSecondaryErrorRatio()
					: 0.0;
			heldStateDelta = normalized.heldStateVelocityDeltaMetersPerSecond();
			normalizedPrimary = normalized.stateNormalizedPrimaryErrorRatio();
			normalizedSecondary = normalized.stateNormalizedSecondaryErrorRatio();
			normalizedViolations = normalized.stateNormalizedPhysicalConstraintViolationCount();
		}
		boolean normalizedPassed = result.sampleCount() >= result.minSampleCount()
				&& normalizedPrimary <= result.maxAllowedPrimaryErrorRatio()
				&& normalizedSecondary <= result.maxAllowedSecondaryErrorRatio()
				&& normalizedViolations == 0;
		boolean freeFlightAcceptanceBlocked = !currentFreeFlightBlackboxAcceptanceReady;
		return new StateNormalizedBlackboxAcceptanceRow(
				result.scenarioName(),
				result.presetName(),
				result.ambientCaseName(),
				result.regressionCaseName(),
				result.flightPhase(),
				result.targetMetric(),
				result.sampleCount(),
				result.minSampleCount(),
				result.primaryErrorRatio(),
				result.secondaryErrorRatio(),
				result.physicalConstraintViolationCount(),
				result.blackboxRegressionPassed(),
				normalized != null,
				normalizedPrimary,
				normalizedSecondary,
				normalizedViolations,
				result.maxAllowedPrimaryErrorRatio(),
				result.maxAllowedSecondaryErrorRatio(),
				contractThrustRatio,
				heldObserved,
				heldResidual,
				heldReduction,
				heldStateDelta,
				normalizedPassed,
				freeFlightAcceptanceBlocked,
				normalizedPassed,
				false,
				false,
				false,
				false,
				false,
				normalizedPassed ? "STATE_NORMALIZED_PASS" : "BLOCKED",
				message(evidenceApplied, result.blackboxRegressionPassed(), normalizedPassed)
		);
	}

	private static int normalizedViolationCount(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
					.DerateControlHookBlackboxResultReviewRow result,
			double normalizedSecondary,
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
					.StateNormalizedPunchoutRow normalized
	) {
		int violations = 0;
		if (result.primaryErrorRatio() > result.maxAllowedPrimaryErrorRatio()) {
			violations++;
		}
		if (normalizedSecondary > result.maxAllowedSecondaryErrorRatio()) {
			violations++;
		}
		if (normalized.heldResidualThrustDeficitRatio() > RESIDUAL_DEFICIT_THRESHOLD) {
			violations++;
		}
		if (normalized.heldStateVelocityDeltaMetersPerSecond() > STATE_DELTA_EPSILON) {
			violations++;
		}
		if (normalized.heldObservedThrustRatio() < normalized.contractThrustRatio()) {
			violations++;
		}
		return violations;
	}

	private static StateNormalizedBlackboxAcceptanceSummary summary(
			List<StateNormalizedBlackboxAcceptanceRow> rows,
			boolean currentFreeFlightReady
	) {
		int freePassed = 0;
		int evidence = 0;
		int normalizedPassed = 0;
		int violations = 0;
		double maxFreeSecondary = 0.0;
		double maxNormalizedSecondary = 0.0;
		double maxHeldResidual = 0.0;
		double blackboxHeldObserved = 0.0;
		double blackboxHeldResidual = 0.0;
		double blackboxReduction = 0.0;
		for (StateNormalizedBlackboxAcceptanceRow row : rows) {
			if (row.freeFlightRegressionPassed()) {
				freePassed++;
			}
			if (row.stateNormalizedEvidenceApplied()) {
				evidence++;
				blackboxHeldObserved = row.heldObservedThrustRatio();
				blackboxHeldResidual = row.heldResidualThrustDeficitRatio();
				blackboxReduction = row.heldResidualReductionRatio();
			}
			if (row.stateNormalizedRegressionPassed()) {
				normalizedPassed++;
			}
			violations += row.stateNormalizedPhysicalConstraintViolationCount();
			maxFreeSecondary = Math.max(maxFreeSecondary, row.freeFlightSecondaryErrorRatio());
			maxNormalizedSecondary = Math.max(maxNormalizedSecondary, row.stateNormalizedSecondaryErrorRatio());
			maxHeldResidual = Math.max(maxHeldResidual, row.heldResidualThrustDeficitRatio());
		}
		boolean labReady = !rows.isEmpty()
				&& normalizedPassed == rows.size()
				&& evidence == PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
						.RESULT_ROW_COUNT
				&& violations == 0
				&& !currentFreeFlightReady;
		return new StateNormalizedBlackboxAcceptanceSummary(
				rows.size(),
				freePassed,
				rows.size() - freePassed,
				evidence,
				normalizedPassed,
				rows.size() - normalizedPassed,
				violations,
				maxFreeSecondary,
				maxNormalizedSecondary,
				maxHeldResidual,
				blackboxHeldObserved,
				blackboxHeldResidual,
				blackboxReduction,
				currentFreeFlightReady,
				labReady,
				false,
				false,
				false,
				false,
				false,
				labReady ? "STATE_NORMALIZED_READY" : "BLOCKED",
				labReady
						? "feed-state-normalized-forward-punchout-into-separate-blackbox-harness-before-free-flight-acceptance"
						: "inspect-state-normalized-blackbox-failures-before-acceptance"
		);
	}

	private static String message(
			boolean evidenceApplied,
			boolean freeFlightPassed,
			boolean normalizedPassed
	) {
		if (freeFlightPassed && normalizedPassed) {
			return "free-flight-blackbox-result-already-passed";
		}
		if (evidenceApplied && normalizedPassed) {
			return "state-normalized-forward-punchout-clears-lab-margin-but-free-flight-acceptance-stays-blocked";
		}
		return "state-normalized-blackbox-result-blocked";
	}
}
