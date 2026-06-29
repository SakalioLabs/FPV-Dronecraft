package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceClosureCalibrationTarget {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Closure-Calibration-Target-Packet";
	public static final String CAVEAT =
			"Powered-source closure calibration targets convert energy residual and sensitivity audits into finite torque, axial-wake, and swirl-wake targets; they are audit-only and never change runtime config or gameplay feel.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int PRESET_STATE_ROW_COUNT = 8;
	public static final int SUMMARY_ROW_COUNT = 13;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ PRESET_STATE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceClosureCalibrationTarget() {
	}

	public record PoweredSourceClosureCalibrationTargetRow(
			String spinState,
			String presetName,
			double currentTorqueScale,
			double targetTorqueScale,
			double torqueScaleShiftFromCurrent,
			double currentEnergyMarginWatts,
			double peakEnergyMarginWatts,
			double targetResidualMarginWatts,
			double targetAxialWakePowerScale,
			double requiredAxialWakePowerReductionFraction,
			double targetSwirlWakePowerScale,
			double requiredSwirlWakePowerReductionFraction,
			boolean torqueOnlyClosurePossibleAtCurrentWake,
			boolean currentWakeTargetInsideClosure,
			boolean finiteCalibrationTargetRequired,
			boolean torqueScaleDecreaseRequired,
			boolean axialWakePowerReductionRequired,
			boolean swirlWakePowerReductionRequired,
			boolean currentTorqueIncreaseWorsensDeficit,
			boolean peakTorqueStationaryDeficit,
			boolean liveMotorTorqueCurveRequired,
			boolean liveWakePowerCalibrationRequired,
			boolean liveSwirlProbeRequired,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String nextRequiredAction,
			String status,
			String message
	) {
	}

	public record PoweredSourceClosureCalibrationTargetExtrema(
			int rowCount,
			int currentWakeTargetInsideClosureCount,
			int finiteCalibrationTargetRequiredCount,
			int torqueOnlyClosurePossibleAtCurrentWakeCount,
			int torqueScaleDecreaseRequiredCount,
			int axialWakePowerReductionRequiredCount,
			int swirlWakePowerReductionRequiredCount,
			int currentTorqueIncreaseWorsensDeficitCount,
			int peakTorqueStationaryDeficitCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			double minTargetTorqueScale,
			double maxRequiredAxialWakePowerReductionFraction,
			double minCurrentEnergyMarginWatts
	) {
	}

	public record PoweredSourceClosureCalibrationTargetAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int presetStateRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<PoweredSourceClosureCalibrationTargetRow> rows,
			PoweredSourceClosureCalibrationTargetExtrema extrema
	) {
		public PoweredSourceClosureCalibrationTargetAudit {
			rows = List.copyOf(rows);
		}
	}

	public static PoweredSourceClosureCalibrationTargetAudit audit() {
		List<PoweredSourceClosureCalibrationTargetRow> rows =
				Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.audit()
						.rows()
						.stream()
						.map(Aerodynamics4McL2PoweredSourceClosureCalibrationTarget::row)
						.toList();
		return new PoweredSourceClosureCalibrationTargetAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				PRESET_STATE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				extrema(rows)
		);
	}

	public static PoweredSourceClosureCalibrationTargetRow row(String spinState, String presetName) {
		return row(Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.row(spinState, presetName));
	}

	private static PoweredSourceClosureCalibrationTargetRow row(
			Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.PoweredSourceJointCalibrationEnvelopeRow envelope
	) {
		if (envelope == null) {
			throw new IllegalArgumentException("envelope must not be null.");
		}
		Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.PoweredSourceTorqueClosureRow torque =
				Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.row(
						envelope.spinState(), envelope.presetName());
		Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.PoweredSourceEnergyClosureSensitivityRow current =
				Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.row(
						envelope.spinState(), envelope.presetName(), "current_torque");
		Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.PoweredSourceEnergyClosureSensitivityRow peak =
				Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity.row(
						envelope.spinState(), envelope.presetName(), "peak_torque");

		boolean targetInside = current.energyMarginWithCurrentAxialWatts() >= 0.0
				&& torque.torqueCoefficientOnlySufficient();
		boolean finiteTargetRequired = !targetInside;
		double targetTorqueScale = targetInside
				? envelope.currentTorqueScale()
				: envelope.peakTorqueScaleForExistingWake();
		double targetAxialScale = targetInside ? 1.0 : envelope.maxAxialPowerScaleAtPeakTorqueScale();
		double targetSwirlScale = 1.0;
		double targetResidual = closureResidual(
				targetTorqueScale,
				targetAxialScale,
				targetSwirlScale,
				envelope.currentShaftPowerWatts(),
				envelope.axialMomentumPowerWatts(),
				envelope.currentSwirlPowerWatts());
		double axialReduction = Math.max(0.0, 1.0 - targetAxialScale);
		double swirlReduction = Math.max(0.0, 1.0 - targetSwirlScale);
		boolean torqueDecreaseRequired = targetTorqueScale < envelope.currentTorqueScale();
		boolean axialReductionRequired = axialReduction > 1.0e-9;
		boolean swirlReductionRequired = swirlReduction > 1.0e-9;
		String status = finiteTargetRequired
				? "FINITE_WAKE_AND_TORQUE_CALIBRATION_TARGET_REQUIRED"
				: "CURRENT_WAKE_TARGET_INSIDE_CLOSURE";
		String nextRequiredAction = finiteTargetRequired
				? "capture-live-source-map-at-peak-torque-and-reduced-axial-wake"
				: "capture-live-motor-torque-curve-and-powered-wake-power-telemetry";
		String message = finiteTargetRequired
				? "torque-only-current-wake-target-is-impossible-use-finite-peak-torque-axial-wake-target"
				: "current-wake-target-has-positive-closure-margin-but-live-evidence-is-still-required";
		return new PoweredSourceClosureCalibrationTargetRow(
				envelope.spinState(),
				envelope.presetName(),
				envelope.currentTorqueScale(),
				targetTorqueScale,
				targetTorqueScale - envelope.currentTorqueScale(),
				current.energyMarginWithCurrentAxialWatts(),
				peak.energyMarginWithCurrentAxialWatts(),
				targetResidual,
				targetAxialScale,
				axialReduction,
				targetSwirlScale,
				swirlReduction,
				torque.torqueScaleClosurePossible(),
				targetInside,
				finiteTargetRequired,
				torqueDecreaseRequired,
				axialReductionRequired,
				swirlReductionRequired,
				current.torqueIncreaseWorsensMargin(),
				peak.torqueStationaryAtSample() && peak.energyMarginWithCurrentAxialWatts() < 0.0,
				true,
				true,
				true,
				false,
				false,
				nextRequiredAction,
				status,
				message
		);
	}

	private static PoweredSourceClosureCalibrationTargetExtrema extrema(
			List<PoweredSourceClosureCalibrationTargetRow> rows
	) {
		int inside = 0;
		int finite = 0;
		int torquePossible = 0;
		int torqueDecrease = 0;
		int axialReduction = 0;
		int swirlReduction = 0;
		int torqueWorsens = 0;
		int peakStationary = 0;
		int runtime = 0;
		int gameplay = 0;
		double minTargetTorque = Double.POSITIVE_INFINITY;
		double maxAxialReduction = 0.0;
		double minCurrentMargin = Double.POSITIVE_INFINITY;
		for (PoweredSourceClosureCalibrationTargetRow row : rows) {
			if (row.currentWakeTargetInsideClosure()) {
				inside++;
			}
			if (row.finiteCalibrationTargetRequired()) {
				finite++;
			}
			if (row.torqueOnlyClosurePossibleAtCurrentWake()) {
				torquePossible++;
			}
			if (row.torqueScaleDecreaseRequired()) {
				torqueDecrease++;
			}
			if (row.axialWakePowerReductionRequired()) {
				axialReduction++;
			}
			if (row.swirlWakePowerReductionRequired()) {
				swirlReduction++;
			}
			if (row.currentTorqueIncreaseWorsensDeficit()) {
				torqueWorsens++;
			}
			if (row.peakTorqueStationaryDeficit()) {
				peakStationary++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			minTargetTorque = Math.min(minTargetTorque, row.targetTorqueScale());
			maxAxialReduction = Math.max(maxAxialReduction,
					row.requiredAxialWakePowerReductionFraction());
			minCurrentMargin = Math.min(minCurrentMargin, row.currentEnergyMarginWatts());
		}
		return new PoweredSourceClosureCalibrationTargetExtrema(
				rows.size(),
				inside,
				finite,
				torquePossible,
				torqueDecrease,
				axialReduction,
				swirlReduction,
				torqueWorsens,
				peakStationary,
				runtime,
				gameplay,
				Double.isInfinite(minTargetTorque) ? 0.0 : minTargetTorque,
				maxAxialReduction,
				Double.isInfinite(minCurrentMargin) ? 0.0 : minCurrentMargin
		);
	}

	private static double closureResidual(
			double torqueScale,
			double axialScale,
			double swirlScale,
			double shaftPowerWatts,
			double axialMomentumPowerWatts,
			double swirlPowerWatts
	) {
		return torqueScale * shaftPowerWatts
				- axialScale * axialMomentumPowerWatts
				- swirlScale * torqueScale * torqueScale * swirlPowerWatts;
	}
}
