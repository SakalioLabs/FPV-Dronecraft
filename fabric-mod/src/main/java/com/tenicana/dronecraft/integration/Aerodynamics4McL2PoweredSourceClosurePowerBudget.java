package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceClosurePowerBudget {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Closure-Power-Budget-Packet";
	public static final String CAVEAT =
			"Powered-source closure power budget converts finite closure targets into SI shaft, axial-wake, and swirl-wake power budgets; it is audit-only and never changes runtime config or gameplay feel.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int PRESET_STATE_ROW_COUNT = 8;
	public static final int SUMMARY_ROW_COUNT = 13;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ PRESET_STATE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final double RESIDUAL_TOLERANCE_WATTS = 1.0e-9;

	private Aerodynamics4McL2PoweredSourceClosurePowerBudget() {
	}

	public record PoweredSourceClosurePowerBudgetRow(
			String spinState,
			String presetName,
			double currentShaftPowerWatts,
			double currentAxialWakePowerWatts,
			double currentSwirlWakePowerWatts,
			double currentWakeDemandWatts,
			double targetTorqueScale,
			double targetAxialWakePowerScale,
			double targetSwirlWakePowerScale,
			double targetShaftPowerWatts,
			double targetAxialWakePowerWatts,
			double targetSwirlWakePowerWatts,
			double targetWakeDemandWatts,
			double targetResidualMarginWatts,
			double targetDemandToShaftPowerRatio,
			double shaftPowerChangeWatts,
			double axialWakePowerChangeWatts,
			double swirlWakePowerChangeWatts,
			double wakeDemandChangeWatts,
			boolean finiteCalibrationTargetRequired,
			boolean currentWakeTargetInsideClosure,
			boolean targetResidualNonNegative,
			boolean targetResidualWithinTolerance,
			boolean shaftPowerReductionRequired,
			boolean axialWakePowerReductionRequired,
			boolean swirlWakePowerReductionRequired,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredSourceClosurePowerBudgetExtrema(
			int rowCount,
			int currentWakeTargetInsideClosureCount,
			int finiteCalibrationTargetRequiredCount,
			int targetResidualNonNegativeCount,
			int targetResidualWithinToleranceCount,
			int shaftPowerReductionRequiredCount,
			int axialWakePowerReductionRequiredCount,
			int swirlWakePowerReductionRequiredCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			double maxTargetWakeDemandWatts,
			double minTargetDemandToShaftPowerRatio,
			double maxWakeDemandReductionWatts
	) {
	}

	public record PoweredSourceClosurePowerBudgetAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int presetStateRowCount,
			int summaryRowCount,
			int methodRowCount,
			double residualToleranceWatts,
			List<PoweredSourceClosurePowerBudgetRow> rows,
			PoweredSourceClosurePowerBudgetExtrema extrema
	) {
		public PoweredSourceClosurePowerBudgetAudit {
			rows = List.copyOf(rows);
		}
	}

	public static PoweredSourceClosurePowerBudgetAudit audit() {
		List<PoweredSourceClosurePowerBudgetRow> rows =
				Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.audit()
						.rows()
						.stream()
						.map(Aerodynamics4McL2PoweredSourceClosurePowerBudget::row)
						.toList();
		return new PoweredSourceClosurePowerBudgetAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				PRESET_STATE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				RESIDUAL_TOLERANCE_WATTS,
				rows,
				extrema(rows)
		);
	}

	public static PoweredSourceClosurePowerBudgetRow row(String spinState, String presetName) {
		return row(Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.row(spinState, presetName));
	}

	private static PoweredSourceClosurePowerBudgetRow row(
			Aerodynamics4McL2PoweredSourceClosureCalibrationTarget.PoweredSourceClosureCalibrationTargetRow target
	) {
		if (target == null) {
			throw new IllegalArgumentException("target must not be null.");
		}
		Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.PoweredSourceJointCalibrationEnvelopeRow envelope =
				Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.row(
						target.spinState(), target.presetName());
		double currentShaft = envelope.currentShaftPowerWatts();
		double currentAxial = envelope.axialMomentumPowerWatts();
		double currentSwirl = envelope.currentSwirlPowerWatts();
		double currentDemand = envelope.currentAeroPowerDemandWatts();
		double targetShaft = target.targetTorqueScale() * currentShaft;
		double targetAxial = target.targetAxialWakePowerScale() * currentAxial;
		double targetSwirl = target.targetSwirlWakePowerScale()
				* target.targetTorqueScale()
				* target.targetTorqueScale()
				* currentSwirl;
		double targetDemand = targetAxial + targetSwirl;
		double residual = targetShaft - targetDemand;
		double demandRatio = ratio(targetDemand, targetShaft);
		double shaftDelta = targetShaft - currentShaft;
		double axialDelta = targetAxial - currentAxial;
		double swirlDelta = targetSwirl - currentSwirl;
		double demandDelta = targetDemand - currentDemand;
		boolean residualNonNegative = residual >= -RESIDUAL_TOLERANCE_WATTS;
		boolean residualWithinTolerance = Math.abs(residual) <= RESIDUAL_TOLERANCE_WATTS;
		String status = target.finiteCalibrationTargetRequired()
				? "FINITE_TARGET_POWER_BUDGET_READY"
				: "CURRENT_TARGET_POWER_BUDGET_READY";
		String nextRequiredAction = target.finiteCalibrationTargetRequired()
				? "run-live-powered-source-at-target-q-and-axial-wake-power"
				: "capture-live-current-wake-power-budget-telemetry";
		String message = target.finiteCalibrationTargetRequired()
				? "finite-target-power-budget-closes-energy-residual-for-live-cfd-comparison"
				: "current-target-power-budget-has-positive-margin-but-still-needs-live-evidence";
		return new PoweredSourceClosurePowerBudgetRow(
				target.spinState(),
				target.presetName(),
				currentShaft,
				currentAxial,
				currentSwirl,
				currentDemand,
				target.targetTorqueScale(),
				target.targetAxialWakePowerScale(),
				target.targetSwirlWakePowerScale(),
				targetShaft,
				targetAxial,
				targetSwirl,
				targetDemand,
				residual,
				demandRatio,
				shaftDelta,
				axialDelta,
				swirlDelta,
				demandDelta,
				target.finiteCalibrationTargetRequired(),
				target.currentWakeTargetInsideClosure(),
				residualNonNegative,
				residualWithinTolerance,
				shaftDelta < -RESIDUAL_TOLERANCE_WATTS,
				axialDelta < -RESIDUAL_TOLERANCE_WATTS,
				swirlDelta < -RESIDUAL_TOLERANCE_WATTS,
				false,
				false,
				nextRequiredAction,
				status,
				message
		);
	}

	private static PoweredSourceClosurePowerBudgetExtrema extrema(
			List<PoweredSourceClosurePowerBudgetRow> rows
	) {
		int inside = 0;
		int finite = 0;
		int nonNegative = 0;
		int within = 0;
		int shaftReduction = 0;
		int axialReduction = 0;
		int swirlReduction = 0;
		int runtime = 0;
		int gameplay = 0;
		double maxDemand = 0.0;
		double minDemandRatio = Double.POSITIVE_INFINITY;
		double maxDemandReduction = 0.0;
		for (PoweredSourceClosurePowerBudgetRow row : rows) {
			if (row.currentWakeTargetInsideClosure()) {
				inside++;
			}
			if (row.finiteCalibrationTargetRequired()) {
				finite++;
			}
			if (row.targetResidualNonNegative()) {
				nonNegative++;
			}
			if (row.targetResidualWithinTolerance()) {
				within++;
			}
			if (row.shaftPowerReductionRequired()) {
				shaftReduction++;
			}
			if (row.axialWakePowerReductionRequired()) {
				axialReduction++;
			}
			if (row.swirlWakePowerReductionRequired()) {
				swirlReduction++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxDemand = Math.max(maxDemand, row.targetWakeDemandWatts());
			minDemandRatio = Math.min(minDemandRatio, row.targetDemandToShaftPowerRatio());
			maxDemandReduction = Math.max(maxDemandReduction, -row.wakeDemandChangeWatts());
		}
		return new PoweredSourceClosurePowerBudgetExtrema(
				rows.size(),
				inside,
				finite,
				nonNegative,
				within,
				shaftReduction,
				axialReduction,
				swirlReduction,
				runtime,
				gameplay,
				maxDemand,
				Double.isInfinite(minDemandRatio) ? 0.0 : minDemandRatio,
				maxDemandReduction
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
