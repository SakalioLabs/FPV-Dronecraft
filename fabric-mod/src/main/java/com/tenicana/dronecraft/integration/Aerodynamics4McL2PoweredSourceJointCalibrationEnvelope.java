package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Joint-Calibration-Envelope-Packet";
	public static final String CAVEAT =
			"Powered-source joint calibration envelope decomposes torque-only closure blockers into axial, swirl, and motor-torque calibration bounds; it is audit-only and never changes runtime config or gameplay feel.";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int PRESET_STATE_SAMPLE_COUNT = 8;
	public static final int ENVELOPE_METRIC_COUNT = 18;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_STATE_SAMPLE_COUNT * ENVELOPE_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope() {
	}

	public record PoweredSourceJointCalibrationEnvelopeRow(
			String spinState,
			String presetName,
			double currentTorqueScale,
			double currentShaftPowerWatts,
			double axialMomentumPowerWatts,
			double currentSwirlPowerWatts,
			double currentAeroPowerDemandWatts,
			double equalWakePowerScaleForCurrentTorqueClosure,
			double requiredUniformWakePowerReductionFraction,
			double maxAxialPowerWithCurrentSwirlAtCurrentTorqueWatts,
			double maxAxialPowerScaleWithCurrentSwirlAtCurrentTorque,
			double maxSwirlPowerWithCurrentAxialAtCurrentTorqueWatts,
			double maxSwirlPowerScaleWithCurrentAxialAtCurrentTorque,
			boolean axialOnlyReductionCanCloseAtCurrentTorque,
			boolean swirlOnlyReductionCanCloseAtCurrentTorque,
			boolean bothSingleChannelReductionsBlockedAtCurrentTorque,
			double peakTorqueScaleForExistingWake,
			double maxAxialPowerAtPeakTorqueScaleWatts,
			double maxAxialPowerScaleAtPeakTorqueScale,
			double requiredAxialReductionAtPeakTorqueScaleFraction,
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

	public record PoweredSourceJointCalibrationEnvelopeExtrema(
			int rowCount,
			int jointWakeOrSourceCalibrationRequiredCount,
			int targetOnlyTelemetryCount,
			int axialOnlyReductionCanCloseCount,
			int swirlOnlyReductionCanCloseCount,
			int bothSingleChannelReductionsBlockedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			double minEqualWakePowerScaleForCurrentTorqueClosure,
			double maxRequiredUniformWakePowerReductionFraction,
			double minMaxAxialPowerScaleAtPeakTorqueScale,
			double maxRequiredAxialReductionAtPeakTorqueScaleFraction
	) {
	}

	public record PoweredSourceJointCalibrationEnvelopeAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetStateSampleCount,
			int envelopeMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceJointCalibrationEnvelopeRow> rows,
			PoweredSourceJointCalibrationEnvelopeExtrema extrema
	) {
		public PoweredSourceJointCalibrationEnvelopeAudit {
			rows = List.copyOf(rows);
		}
	}

	public static PoweredSourceJointCalibrationEnvelopeAudit audit() {
		List<PoweredSourceJointCalibrationEnvelopeRow> rows =
				Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.audit()
						.rows()
						.stream()
						.map(Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope::row)
						.toList();
		return new PoweredSourceJointCalibrationEnvelopeAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_STATE_SAMPLE_COUNT,
				ENVELOPE_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				rows,
				extrema(rows)
		);
	}

	public static PoweredSourceJointCalibrationEnvelopeRow row(String spinState, String presetName) {
		return row(Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.row(spinState, presetName));
	}

	private static PoweredSourceJointCalibrationEnvelopeRow row(
			Aerodynamics4McL2PoweredSourceTorqueClosureFeasibility.PoweredSourceTorqueClosureRow torqueRow
	) {
		if (torqueRow == null) {
			throw new IllegalArgumentException("torqueRow must not be null.");
		}
		double shaftPower = torqueRow.currentShaftPowerWatts();
		double axialPower = torqueRow.axialMomentumPowerWatts();
		double swirlPower = torqueRow.currentSwirlPowerWatts();
		double demand = torqueRow.currentAeroPowerDemandWatts();
		double equalScale = ratio(shaftPower, demand);
		double uniformReduction = Math.max(0.0, 1.0 - equalScale);
		double maxAxialWithCurrentSwirl = Math.max(0.0, shaftPower - swirlPower);
		double maxSwirlWithCurrentAxial = Math.max(0.0, shaftPower - axialPower);
		double maxAxialScaleCurrent = ratio(maxAxialWithCurrentSwirl, axialPower);
		double maxSwirlScaleCurrent = ratio(maxSwirlWithCurrentAxial, swirlPower);
		boolean axialOnlyCanClose = shaftPower >= swirlPower;
		boolean swirlOnlyCanClose = shaftPower >= axialPower;
		boolean bothSingleChannelBlocked = !axialOnlyCanClose && !swirlOnlyCanClose;
		double peakTorqueScale = torqueRow.peakFeasibleTorqueScale();
		double maxAxialPowerAtPeak = axialPower + torqueRow.maxFeasibleMarginWatts();
		double maxAxialScaleAtPeak = ratio(Math.max(0.0, maxAxialPowerAtPeak), axialPower);
		double requiredAxialReductionAtPeak = Math.max(0.0, 1.0 - maxAxialScaleAtPeak);
		boolean jointCalibrationRequired = torqueRow.jointWakeOrSourceCalibrationRequired();
		return new PoweredSourceJointCalibrationEnvelopeRow(
				torqueRow.spinState(),
				torqueRow.presetName(),
				1.0,
				shaftPower,
				axialPower,
				swirlPower,
				demand,
				equalScale,
				uniformReduction,
				maxAxialWithCurrentSwirl,
				maxAxialScaleCurrent,
				maxSwirlWithCurrentAxial,
				maxSwirlScaleCurrent,
				axialOnlyCanClose,
				swirlOnlyCanClose,
				bothSingleChannelBlocked,
				peakTorqueScale,
				maxAxialPowerAtPeak,
				maxAxialScaleAtPeak,
				requiredAxialReductionAtPeak,
				jointCalibrationRequired,
				true,
				true,
				true,
				false,
				false,
				jointCalibrationRequired
						? "capture-cinewhoop-source-map-wake-footprint-and-motor-torque-calibration"
						: "capture-live-motor-torque-curve-and-powered-wake-power-telemetry",
				jointCalibrationRequired ? "JOINT_CALIBRATION_REQUIRED" : "TARGET_ONLY_TELEMETRY_REQUIRED",
				jointCalibrationRequired
						? "single-channel-powered-source-calibration-cannot-close-current-wake-demand"
						: "joint-calibration-envelope-open-but-live-evidence-missing"
		);
	}

	private static PoweredSourceJointCalibrationEnvelopeExtrema extrema(
			List<PoweredSourceJointCalibrationEnvelopeRow> rows
	) {
		int joint = 0;
		int axial = 0;
		int swirl = 0;
		int blocked = 0;
		int runtime = 0;
		int gameplay = 0;
		double minEqualScale = Double.POSITIVE_INFINITY;
		double maxUniformReduction = 0.0;
		double minPeakAxialScale = Double.POSITIVE_INFINITY;
		double maxPeakAxialReduction = 0.0;
		for (PoweredSourceJointCalibrationEnvelopeRow row : rows) {
			if (row.jointWakeOrSourceCalibrationRequired()) {
				joint++;
			}
			if (row.axialOnlyReductionCanCloseAtCurrentTorque()) {
				axial++;
			}
			if (row.swirlOnlyReductionCanCloseAtCurrentTorque()) {
				swirl++;
			}
			if (row.bothSingleChannelReductionsBlockedAtCurrentTorque()) {
				blocked++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			minEqualScale = Math.min(minEqualScale, row.equalWakePowerScaleForCurrentTorqueClosure());
			maxUniformReduction = Math.max(maxUniformReduction, row.requiredUniformWakePowerReductionFraction());
			minPeakAxialScale = Math.min(minPeakAxialScale, row.maxAxialPowerScaleAtPeakTorqueScale());
			maxPeakAxialReduction = Math.max(maxPeakAxialReduction,
					row.requiredAxialReductionAtPeakTorqueScaleFraction());
		}
		return new PoweredSourceJointCalibrationEnvelopeExtrema(
				rows.size(),
				joint,
				rows.size() - joint,
				axial,
				swirl,
				blocked,
				runtime,
				gameplay,
				Double.isInfinite(minEqualScale) ? 0.0 : minEqualScale,
				maxUniformReduction,
				Double.isInfinite(minPeakAxialScale) ? 0.0 : minPeakAxialScale,
				maxPeakAxialReduction
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
