package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Energy-Closure-Sensitivity-Packet";
	public static final String CAVEAT =
			"Powered-source energy-closure sensitivity differentiates local torque, axial-wake, and swirl-wake residual directions; it is audit-only and never changes runtime config or gameplay feel.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 7;
	public static final int MANIFOLD_SAMPLE_ROW_COUNT = 16;
	public static final int SUMMARY_ROW_COUNT = 12;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ MANIFOLD_SAMPLE_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double STATIONARY_GRADIENT_EPSILON = 1.0e-9;

	private Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity() {
	}

	public record PoweredSourceEnergyClosureSensitivityRow(
			String spinState,
			String presetName,
			String sampleName,
			double torqueScale,
			double energyMarginWithCurrentAxialWatts,
			double torqueGradientWattsPerTorqueScale,
			double axialScaleGradientWattsPerScale,
			double swirlScaleGradientWattsPerScale,
			boolean torqueIncreaseImprovesMargin,
			boolean torqueIncreaseWorsensMargin,
			boolean torqueStationaryAtSample,
			double requiredTorqueScaleIncreaseForLinearClosure,
			double requiredAxialScaleReductionForLinearClosure,
			double requiredSwirlScaleReductionForLinearClosure,
			boolean torqueOnlyLinearClosureAvailable,
			boolean axialOnlyLinearClosureAvailable,
			boolean swirlOnlyLinearClosureAvailable,
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

	public record PoweredSourceEnergyClosureSensitivityExtrema(
			int rowCount,
			int positiveMarginCount,
			int negativeMarginCount,
			int torqueIncreaseImprovesCount,
			int torqueIncreaseWorsensCount,
			int torqueStationaryCount,
			int torqueOnlyLinearClosureAvailableCount,
			int axialOnlyLinearClosureAvailableCount,
			int swirlOnlyLinearClosureAvailableCount,
			int jointWakeOrSourceCalibrationRequiredCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			double minTorqueGradientWattsPerTorqueScale,
			double maxTorqueGradientWattsPerTorqueScale,
			double maxRequiredAxialScaleReductionForLinearClosure
	) {
	}

	public record PoweredSourceEnergyClosureSensitivityAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int manifoldSampleRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<PoweredSourceEnergyClosureSensitivityRow> rows,
			PoweredSourceEnergyClosureSensitivityExtrema extrema
	) {
		public PoweredSourceEnergyClosureSensitivityAudit {
			rows = List.copyOf(rows);
		}
	}

	public static PoweredSourceEnergyClosureSensitivityAudit audit() {
		List<PoweredSourceEnergyClosureSensitivityRow> rows =
				Aerodynamics4McL2PoweredSourceEnergyClosureManifold.audit()
						.samples()
						.stream()
						.map(Aerodynamics4McL2PoweredSourceEnergyClosureSensitivity::row)
						.toList();
		return new PoweredSourceEnergyClosureSensitivityAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				MANIFOLD_SAMPLE_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				extrema(rows)
		);
	}

	public static PoweredSourceEnergyClosureSensitivityRow row(
			String spinState,
			String presetName,
			String sampleName
	) {
		return row(Aerodynamics4McL2PoweredSourceEnergyClosureManifold.sample(spinState, presetName, sampleName));
	}

	private static PoweredSourceEnergyClosureSensitivityRow row(
			Aerodynamics4McL2PoweredSourceEnergyClosureManifold.PoweredSourceEnergyClosureSample sample
	) {
		if (sample == null) {
			throw new IllegalArgumentException("sample must not be null.");
		}
		double torqueScale = sample.torqueScale();
		double margin = sample.energyMarginWithCurrentAxialWatts();
		double torqueGradient = sample.currentShaftPowerWatts()
				- 2.0 * torqueScale * sample.swirlPowerScale() * sample.currentSwirlPowerWatts();
		double axialGradient = -sample.axialMomentumPowerWatts();
		double swirlGradient = -torqueScale * torqueScale * sample.currentSwirlPowerWatts();
		boolean torqueImproves = torqueGradient > STATIONARY_GRADIENT_EPSILON;
		boolean torqueWorsens = torqueGradient < -STATIONARY_GRADIENT_EPSILON;
		boolean torqueStationary = !torqueImproves && !torqueWorsens;
		double requiredTorqueIncrease = margin < 0.0 && torqueImproves
				? -margin / torqueGradient
				: 0.0;
		double requiredAxialReduction = margin < 0.0
				? clamp01(-margin / sample.axialMomentumPowerWatts())
				: 0.0;
		double requiredSwirlReduction = margin < 0.0
				? clamp01(-margin / Math.abs(swirlGradient))
				: 0.0;
		boolean torqueOnlyAvailable = margin < 0.0 && torqueImproves && requiredTorqueIncrease <= 1.0;
		boolean axialOnlyAvailable = margin < 0.0
				&& requiredAxialReduction < 1.0
				&& !sample.zeroAxialBudgetAtThisTorque();
		boolean swirlOnlyAvailable = margin < 0.0 && requiredSwirlReduction < 1.0;
		String status;
		String nextRequiredAction;
		String message;
		if (margin >= 0.0) {
			status = "POSITIVE_RESIDUAL_MARGIN";
			nextRequiredAction = "capture-live-powered-source-residual-sensitivity-telemetry";
			message = "sampled-energy-closure-has-positive-margin-but-still-needs-live-evidence";
		} else if (torqueWorsens) {
			status = "TORQUE_INCREASE_WORSENS_DEFICIT";
			nextRequiredAction = "reduce-torque-scale-toward-peak-before-joint-wake-calibration";
			message = "sample-is-right-of-the-torque-only-peak-so-more-torque-increases-swirl-loss";
		} else if (torqueStationary) {
			status = "TORQUE_GRADIENT_STATIONARY_DEFICIT";
			nextRequiredAction = "calibrate-axial-wake-or-swirl-wake-because-torque-gradient-is-zero";
			message = "sample-sits-at-the-local-torque-energy-peak-but-still-has-negative-margin";
		} else {
			status = "TORQUE_INCREASE_CAN_REDUCE_DEFICIT";
			nextRequiredAction = "capture-motor-torque-curve-before-adjusting-wake-calibration";
			message = "local-torque-increase-reduces-the-energy-deficit-but-live-evidence-is-required";
		}
		return new PoweredSourceEnergyClosureSensitivityRow(
				sample.spinState(),
				sample.presetName(),
				sample.sampleName(),
				torqueScale,
				margin,
				torqueGradient,
				axialGradient,
				swirlGradient,
				torqueImproves,
				torqueWorsens,
				torqueStationary,
				requiredTorqueIncrease,
				requiredAxialReduction,
				requiredSwirlReduction,
				torqueOnlyAvailable,
				axialOnlyAvailable,
				swirlOnlyAvailable,
				sample.jointWakeOrSourceCalibrationRequired(),
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

	private static PoweredSourceEnergyClosureSensitivityExtrema extrema(
			List<PoweredSourceEnergyClosureSensitivityRow> rows
	) {
		int positive = 0;
		int negative = 0;
		int improves = 0;
		int worsens = 0;
		int stationary = 0;
		int torqueOnly = 0;
		int axialOnly = 0;
		int swirlOnly = 0;
		int joint = 0;
		int runtime = 0;
		int gameplay = 0;
		double minTorqueGradient = Double.POSITIVE_INFINITY;
		double maxTorqueGradient = Double.NEGATIVE_INFINITY;
		double maxAxialReduction = 0.0;
		for (PoweredSourceEnergyClosureSensitivityRow row : rows) {
			if (row.energyMarginWithCurrentAxialWatts() >= 0.0) {
				positive++;
			} else {
				negative++;
			}
			if (row.torqueIncreaseImprovesMargin()) {
				improves++;
			}
			if (row.torqueIncreaseWorsensMargin()) {
				worsens++;
			}
			if (row.torqueStationaryAtSample()) {
				stationary++;
			}
			if (row.torqueOnlyLinearClosureAvailable()) {
				torqueOnly++;
			}
			if (row.axialOnlyLinearClosureAvailable()) {
				axialOnly++;
			}
			if (row.swirlOnlyLinearClosureAvailable()) {
				swirlOnly++;
			}
			if (row.jointWakeOrSourceCalibrationRequired()) {
				joint++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			minTorqueGradient = Math.min(minTorqueGradient, row.torqueGradientWattsPerTorqueScale());
			maxTorqueGradient = Math.max(maxTorqueGradient, row.torqueGradientWattsPerTorqueScale());
			maxAxialReduction = Math.max(maxAxialReduction,
					row.requiredAxialScaleReductionForLinearClosure());
		}
		return new PoweredSourceEnergyClosureSensitivityExtrema(
				rows.size(),
				positive,
				negative,
				improves,
				worsens,
				stationary,
				torqueOnly,
				axialOnly,
				swirlOnly,
				joint,
				runtime,
				gameplay,
				Double.isInfinite(minTorqueGradient) ? 0.0 : minTorqueGradient,
				Double.isInfinite(maxTorqueGradient) ? 0.0 : maxTorqueGradient,
				maxAxialReduction
		);
	}

	private static double clamp01(double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}
}
