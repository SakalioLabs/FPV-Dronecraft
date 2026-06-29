package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceEnergyClosureManifold {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Energy-Closure-Manifold-Packet";
	public static final String CAVEAT =
			"Powered-source energy-closure manifold samples joint motor torque, axial wake, and swirl wake closure bounds; it is audit-only and never changes runtime config or gameplay feel.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int PRESET_STATE_SAMPLE_COUNT = 8;
	public static final int TORQUE_SAMPLE_COUNT_PER_PRESET = 2;
	public static final int MANIFOLD_SAMPLE_ROW_COUNT =
			PRESET_STATE_SAMPLE_COUNT * TORQUE_SAMPLE_COUNT_PER_PRESET;
	public static final int SUMMARY_ROW_COUNT = 10;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ MANIFOLD_SAMPLE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceEnergyClosureManifold() {
	}

	public record PoweredSourceEnergyClosureSample(
			String spinState,
			String presetName,
			String sampleName,
			double torqueScale,
			double swirlPowerScale,
			double currentShaftPowerWatts,
			double axialMomentumPowerWatts,
			double currentSwirlPowerWatts,
			double availableShaftPowerWatts,
			double retainedSwirlPowerWatts,
			double maxAxialPowerWatts,
			double maxAxialPowerScale,
			double requiredAxialReductionFraction,
			double energyMarginWithCurrentAxialWatts,
			boolean currentAxialRetained,
			boolean zeroAxialBudgetAtThisTorque,
			boolean jointWakeOrSourceCalibrationRequired,
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

	public record PoweredSourceEnergyClosureExtrema(
			int sampleRowCount,
			int currentWakeInsideManifoldCount,
			int axialReductionRequiredCount,
			int zeroAxialBudgetCount,
			int jointWakeOrSourceCalibrationRequiredCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			double minMaxAxialPowerScale,
			double maxRequiredAxialReductionFraction,
			double minEnergyMarginWithCurrentAxialWatts,
			double maxEnergyMarginWithCurrentAxialWatts
	) {
	}

	public record PoweredSourceEnergyClosureAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int presetStateSampleCount,
			int torqueSampleCountPerPreset,
			int manifoldSampleRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<PoweredSourceEnergyClosureSample> samples,
			PoweredSourceEnergyClosureExtrema extrema
	) {
		public PoweredSourceEnergyClosureAudit {
			samples = List.copyOf(samples);
		}
	}

	public static PoweredSourceEnergyClosureAudit audit() {
		List<PoweredSourceEnergyClosureSample> samples =
				Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.audit()
						.rows()
						.stream()
						.flatMap(row -> List.of(
								sample(row, "current_torque", row.currentTorqueScale()),
								sample(row, "peak_torque", row.peakTorqueScaleForExistingWake())
						).stream())
						.toList();
		return new PoweredSourceEnergyClosureAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				PRESET_STATE_SAMPLE_COUNT,
				TORQUE_SAMPLE_COUNT_PER_PRESET,
				MANIFOLD_SAMPLE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				samples,
				extrema(samples)
		);
	}

	public static PoweredSourceEnergyClosureSample sample(
			String spinState,
			String presetName,
			String sampleName
	) {
		Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.PoweredSourceJointCalibrationEnvelopeRow row =
				Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.row(spinState, presetName);
		double torqueScale = switch (sampleName) {
			case "current_torque" -> row.currentTorqueScale();
			case "peak_torque" -> row.peakTorqueScaleForExistingWake();
			default -> throw new IllegalArgumentException("unsupported sampleName: " + sampleName);
		};
		return sample(row, sampleName, torqueScale);
	}

	private static PoweredSourceEnergyClosureSample sample(
			Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.PoweredSourceJointCalibrationEnvelopeRow row,
			String sampleName,
			double torqueScale
	) {
		if (row == null) {
			throw new IllegalArgumentException("row must not be null.");
		}
		if (!Double.isFinite(torqueScale) || torqueScale < 0.0) {
			throw new IllegalArgumentException("torqueScale must be finite and non-negative.");
		}
		double shaftPower = row.currentShaftPowerWatts();
		double axialPower = row.axialMomentumPowerWatts();
		double swirlPower = row.currentSwirlPowerWatts();
		double swirlPowerScale = 1.0;
		double availableShaftPower = torqueScale * shaftPower;
		double retainedSwirlPower = swirlPowerScale * torqueScale * torqueScale * swirlPower;
		double maxAxialPower = Math.max(0.0, availableShaftPower - retainedSwirlPower);
		double maxAxialPowerScale = ratio(maxAxialPower, axialPower);
		double requiredAxialReduction = Math.max(0.0, 1.0 - maxAxialPowerScale);
		double energyMargin = availableShaftPower - retainedSwirlPower - axialPower;
		boolean currentAxialRetained = energyMargin >= -1.0e-9;
		boolean zeroAxialBudget = maxAxialPower <= 1.0e-9;
		String status;
		String nextRequiredAction;
		String message;
		if (currentAxialRetained) {
			status = "CURRENT_WAKE_INSIDE_MANIFOLD";
			nextRequiredAction = "capture-live-motor-torque-curve-and-powered-wake-power-telemetry";
			message = "current-axial-and-swirl-wake-fit-this-sampled-energy-closure-point";
		} else if (zeroAxialBudget) {
			status = "ZERO_AXIAL_BUDGET_AT_SAMPLED_TORQUE";
			nextRequiredAction = "reduce-swirl-power-or-increase-motor-torque-before-axial-source-fit";
			message = "sampled-torque-shaft-power-is-consumed-by-retained-swirl-wake";
		} else {
			status = "AXIAL_SOURCE_REDUCTION_REQUIRED";
			nextRequiredAction = "calibrate-axial-source-map-wake-footprint-and-motor-torque-together";
			message = "sampled-torque-can-pay-only-a-reduced-axial-wake-at-current-swirl";
		}
		return new PoweredSourceEnergyClosureSample(
				row.spinState(),
				row.presetName(),
				sampleName,
				torqueScale,
				swirlPowerScale,
				shaftPower,
				axialPower,
				swirlPower,
				availableShaftPower,
				retainedSwirlPower,
				maxAxialPower,
				maxAxialPowerScale,
				requiredAxialReduction,
				energyMargin,
				currentAxialRetained,
				zeroAxialBudget,
				row.jointWakeOrSourceCalibrationRequired(),
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

	private static PoweredSourceEnergyClosureExtrema extrema(
			List<PoweredSourceEnergyClosureSample> samples
	) {
		int inside = 0;
		int reduction = 0;
		int zeroAxial = 0;
		int joint = 0;
		int runtime = 0;
		int gameplay = 0;
		double minAxialScale = Double.POSITIVE_INFINITY;
		double maxRequiredReduction = 0.0;
		double minMargin = Double.POSITIVE_INFINITY;
		double maxMargin = Double.NEGATIVE_INFINITY;
		for (PoweredSourceEnergyClosureSample sample : samples) {
			if (sample.currentAxialRetained()) {
				inside++;
			}
			if (!sample.currentAxialRetained() && !sample.zeroAxialBudgetAtThisTorque()) {
				reduction++;
			}
			if (sample.zeroAxialBudgetAtThisTorque()) {
				zeroAxial++;
			}
			if (sample.jointWakeOrSourceCalibrationRequired()) {
				joint++;
			}
			if (sample.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (sample.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			minAxialScale = Math.min(minAxialScale, sample.maxAxialPowerScale());
			maxRequiredReduction = Math.max(maxRequiredReduction, sample.requiredAxialReductionFraction());
			minMargin = Math.min(minMargin, sample.energyMarginWithCurrentAxialWatts());
			maxMargin = Math.max(maxMargin, sample.energyMarginWithCurrentAxialWatts());
		}
		return new PoweredSourceEnergyClosureExtrema(
				samples.size(),
				inside,
				reduction,
				zeroAxial,
				joint,
				runtime,
				gameplay,
				Double.isInfinite(minAxialScale) ? 0.0 : minAxialScale,
				maxRequiredReduction,
				Double.isInfinite(minMargin) ? 0.0 : minMargin,
				Double.isInfinite(maxMargin) ? 0.0 : maxMargin
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
