package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan {
	public static final String SOURCE_ID = "A4MC-L2-Static-Airframe-Rotational-Damping-Sweep-Plan-Packet";
	public static final String CAVEAT =
			"Rotational damping sweep plan records pitch, yaw, and roll body-rate evidence targets for future A4MC L2 calibration; current static force/moment probes only supply baseline geometry, so angular-drag calibration remains disabled until a rotational velocity-field or moving-boundary API exists.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int SWEEP_CASES_PER_PRESET = 6;
	public static final int SWEEP_CASE_METRIC_COUNT = 28;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * SWEEP_CASES_PER_PRESET * SWEEP_CASE_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private static final double YAW_TANGENTIAL_SPEED_SCALE = 0.68;

	private Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan() {
	}

	public record RotationalDampingSweepCase(
			String presetName,
			String sweepName,
			String fitRole,
			String bodyAxisName,
			double bodyRateXRadPerSecond,
			double bodyRateYRadPerSecond,
			double bodyRateZRadPerSecond,
			double bodyRateMagnitudeRadPerSecond,
			double rateSign,
			double axisTangentialSpeedScale,
			double referenceRadiusMeters,
			double maxTangentialSpeedMetersPerSecond,
			double currentAngularDragCoefficient,
			double currentDampingTorqueMagnitudeNewtonMeters,
			int steps,
			int gridCellCount,
			double cellSizeMeters,
			double solidFraction,
			double referenceLengthMeters,
			boolean outputFlowAtlas,
			boolean computeForceMoment,
			boolean baselineStaticRequestReady,
			boolean rotationalFlowApiRequired,
			boolean rotationalFlowApiAvailable,
			boolean movingBoundaryOrVelocityFieldRequired,
			boolean expectedOpposingMomentSign,
			boolean angularDragCalibrationAllowed,
			String sourceRuntimeInfo
	) {
	}

	public record RotationalDampingSweepExtrema(
			int presetCount,
			int sweepCaseCount,
			int baselineStaticRequestReadyCount,
			int rotationalFlowApiAvailableCount,
			int angularDragCalibrationAllowedCount,
			int expectedOpposingMomentSignCount,
			int movingBoundaryOrVelocityFieldRequiredCount,
			double maxBodyRateMagnitudeRadPerSecond,
			double maxTangentialSpeedMetersPerSecond,
			double maxCurrentDampingTorqueMagnitudeNewtonMeters,
			int maxGridCellCount,
			double maxReferenceRadiusMeters
	) {
	}

	public record RotationalDampingSweepAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int sweepCasesPerPreset,
			int sweepCaseMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<RotationalDampingSweepCase> sweepCases,
			RotationalDampingSweepExtrema extrema
	) {
		public RotationalDampingSweepAudit {
			sweepCases = List.copyOf(sweepCases);
		}
	}

	public static RotationalDampingSweepAudit audit() {
		List<RotationalDampingSweepCase> sweepCases = new ArrayList<>();
		sweepCases.addAll(casesForPreset("racingQuad", DroneConfig.racingQuad(), 4.8, 72));
		sweepCases.addAll(casesForPreset("apDrone", DroneConfig.apDrone(), 4.2, 64));
		sweepCases.addAll(casesForPreset("cinewhoop", DroneConfig.cinewhoop(), 3.6, 48));
		sweepCases.addAll(casesForPreset("heavyLift", DroneConfig.heavyLift(), 2.4, 80));
		return new RotationalDampingSweepAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				SWEEP_CASES_PER_PRESET,
				SWEEP_CASE_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				sweepCases,
				extrema(sweepCases)
		);
	}

	public static List<RotationalDampingSweepCase> casesForPreset(
			String presetName,
			DroneConfig config,
			double bodyRateMagnitudeRadPerSecond,
			int steps
	) {
		if (presetName == null || presetName.isBlank()) {
			throw new IllegalArgumentException("presetName must not be blank.");
		}
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		if (!Double.isFinite(bodyRateMagnitudeRadPerSecond) || bodyRateMagnitudeRadPerSecond <= 0.0) {
			throw new IllegalArgumentException("bodyRateMagnitudeRadPerSecond must be positive.");
		}
		if (steps <= 0) {
			throw new IllegalArgumentException("steps must be positive.");
		}
		return List.of(
				sweepCase(presetName, "positive_pitch_rate", "pitch_angular_drag_positive", "pitch_x", config,
						new Vec3(bodyRateMagnitudeRadPerSecond, 0.0, 0.0), steps, 1.0),
				sweepCase(presetName, "negative_pitch_rate", "pitch_angular_drag_negative", "pitch_x", config,
						new Vec3(-bodyRateMagnitudeRadPerSecond, 0.0, 0.0), steps, 1.0),
				sweepCase(presetName, "positive_yaw_rate", "yaw_angular_drag_positive", "yaw_y", config,
						new Vec3(0.0, bodyRateMagnitudeRadPerSecond, 0.0), steps, YAW_TANGENTIAL_SPEED_SCALE),
				sweepCase(presetName, "negative_yaw_rate", "yaw_angular_drag_negative", "yaw_y", config,
						new Vec3(0.0, -bodyRateMagnitudeRadPerSecond, 0.0), steps, YAW_TANGENTIAL_SPEED_SCALE),
				sweepCase(presetName, "positive_roll_rate", "roll_angular_drag_positive", "roll_z", config,
						new Vec3(0.0, 0.0, bodyRateMagnitudeRadPerSecond), steps, 1.0),
				sweepCase(presetName, "negative_roll_rate", "roll_angular_drag_negative", "roll_z", config,
						new Vec3(0.0, 0.0, -bodyRateMagnitudeRadPerSecond), steps, 1.0)
		);
	}

	private static RotationalDampingSweepCase sweepCase(
			String presetName,
			String sweepName,
			String fitRole,
			String bodyAxisName,
			DroneConfig config,
			Vec3 bodyRate,
			int steps,
			double axisTangentialSpeedScale
	) {
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, Vec3.ZERO, steps);
		Aerodynamics4McL2Bridge.L2RequestSpec request = probe.requestSpec();
		int gridCells = probe.nx() * probe.ny() * probe.nz();
		double referenceLength = probe.rotorSpanMeters();
		double referenceRadius = 0.5 * referenceLength;
		double rateMagnitude = bodyRate.length();
		double maxTangentialSpeed = rateMagnitude * referenceRadius * axisTangentialSpeedScale;
		boolean baselineReady = request.computeForceMoment()
				&& !request.outputFlowAtlas()
				&& gridCells > 0
				&& referenceRadius > 1.0e-12;
		boolean rotationalFlowApiRequired = true;
		boolean rotationalFlowApiAvailable = false;
		boolean movingBoundaryOrVelocityFieldRequired = true;
		boolean expectedOpposingMomentSign = true;
		boolean angularDragCalibrationAllowed = false;
		return new RotationalDampingSweepCase(
				presetName,
				sweepName,
				fitRole,
				bodyAxisName,
				bodyRate.x(),
				bodyRate.y(),
				bodyRate.z(),
				rateMagnitude,
				Math.signum(bodyRate.x() + bodyRate.y() + bodyRate.z()),
				axisTangentialSpeedScale,
				referenceRadius,
				maxTangentialSpeed,
				config.angularDragCoefficient(),
				rateMagnitude * config.angularDragCoefficient(),
				request.steps(),
				gridCells,
				probe.cellSizeMeters(),
				probe.solidCellCount() / (double) Math.max(1, gridCells),
				referenceLength,
				request.outputFlowAtlas(),
				request.computeForceMoment(),
				baselineReady,
				rotationalFlowApiRequired,
				rotationalFlowApiAvailable,
				movingBoundaryOrVelocityFieldRequired,
				expectedOpposingMomentSign,
				angularDragCalibrationAllowed,
				"plan-only-rotational-flow-api-unavailable"
		);
	}

	private static RotationalDampingSweepExtrema extrema(List<RotationalDampingSweepCase> sweepCases) {
		Set<String> presets = new HashSet<>();
		int baselineReady = 0;
		int apiAvailable = 0;
		int calibrationAllowed = 0;
		int opposing = 0;
		int movingBoundary = 0;
		int maxGrid = 0;
		double maxRate = 0.0;
		double maxTangentialSpeed = 0.0;
		double maxDampingTorque = 0.0;
		double maxReferenceRadius = 0.0;
		for (RotationalDampingSweepCase sweepCase : sweepCases) {
			presets.add(sweepCase.presetName());
			if (sweepCase.baselineStaticRequestReady()) {
				baselineReady++;
			}
			if (sweepCase.rotationalFlowApiAvailable()) {
				apiAvailable++;
			}
			if (sweepCase.angularDragCalibrationAllowed()) {
				calibrationAllowed++;
			}
			if (sweepCase.expectedOpposingMomentSign()) {
				opposing++;
			}
			if (sweepCase.movingBoundaryOrVelocityFieldRequired()) {
				movingBoundary++;
			}
			maxGrid = Math.max(maxGrid, sweepCase.gridCellCount());
			maxRate = Math.max(maxRate, sweepCase.bodyRateMagnitudeRadPerSecond());
			maxTangentialSpeed = Math.max(maxTangentialSpeed, sweepCase.maxTangentialSpeedMetersPerSecond());
			maxDampingTorque = Math.max(maxDampingTorque, sweepCase.currentDampingTorqueMagnitudeNewtonMeters());
			maxReferenceRadius = Math.max(maxReferenceRadius, sweepCase.referenceRadiusMeters());
		}
		return new RotationalDampingSweepExtrema(
				presets.size(),
				sweepCases.size(),
				baselineReady,
				apiAvailable,
				calibrationAllowed,
				opposing,
				movingBoundary,
				maxRate,
				maxTangentialSpeed,
				maxDampingTorque,
				maxGrid,
				maxReferenceRadius
		);
	}
}
