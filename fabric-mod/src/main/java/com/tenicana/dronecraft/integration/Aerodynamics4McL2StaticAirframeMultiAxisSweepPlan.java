package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan {
	public static final String SOURCE_ID = "A4MC-L2-Static-Airframe-Multi-Axis-Sweep-Plan-Packet";
	public static final String CAVEAT =
			"Multi-axis sweep plan records force/moment request geometry for forward drag, reverse symmetry, sideslip sideforce, angle-of-attack lift, moment, and pressure-center fitting; it is a live A4MC run target, not a gameplay coefficient table.";
	public static final double SWEEP_ANGLE_DEGREES = 12.0;
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int SWEEP_CASES_PER_PRESET = 6;
	public static final int SWEEP_CASE_METRIC_COUNT = 22;
	public static final int SUMMARY_METRIC_ROW_COUNT = 11;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * SWEEP_CASES_PER_PRESET * SWEEP_CASE_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2StaticAirframeMultiAxisSweepPlan() {
	}

	public record StaticAirframeSweepCase(
			String presetName,
			String sweepName,
			String fitRole,
			double inletVxMetersPerSecond,
			double inletVyMetersPerSecond,
			double inletVzMetersPerSecond,
			double inletSpeedMetersPerSecond,
			double normalizedInletX,
			double normalizedInletY,
			double normalizedInletZ,
			double sweepAngleDegrees,
			int steps,
			int gridCellCount,
			double cellSizeMeters,
			double solidFraction,
			double projectedReferenceAreaSquareMeters,
			double referenceLengthMeters,
			boolean outputFlowAtlas,
			boolean computeForceMoment,
			boolean coefficientFitReady,
			boolean forwardDragFitSample,
			boolean sideforceFitSample,
			boolean liftFitSample,
			boolean momentPressureCenterFitSample
	) {
	}

	public record MultiAxisSweepExtrema(
			int sweepCaseCount,
			int coefficientFitReadyCount,
			int rawFlowAtlasDisabledCount,
			int sideforceFitSampleCount,
			int liftFitSampleCount,
			int momentPressureCenterFitSampleCount,
			int maxGridCellCount,
			double maxInletSpeedMetersPerSecond,
			double maxSweepAngleDegrees,
			double minProjectedReferenceAreaSquareMeters,
			double maxProjectedReferenceAreaSquareMeters
	) {
	}

	public record MultiAxisSweepAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int sweepCasesPerPreset,
			int sweepCaseMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<StaticAirframeSweepCase> sweepCases,
			MultiAxisSweepExtrema extrema
	) {
		public MultiAxisSweepAudit {
			sweepCases = List.copyOf(sweepCases);
		}
	}

	public static MultiAxisSweepAudit audit() {
		List<StaticAirframeSweepCase> sweepCases = new ArrayList<>();
		sweepCases.addAll(casesForPreset("racingQuad", DroneConfig.racingQuad(), 18.0, 72));
		sweepCases.addAll(casesForPreset("apDrone", DroneConfig.apDrone(), 14.0, 64));
		sweepCases.addAll(casesForPreset("cinewhoop", DroneConfig.cinewhoop(), 8.0, 48));
		sweepCases.addAll(casesForPreset("heavyLift", DroneConfig.heavyLift(), 12.0, 80));
		return new MultiAxisSweepAudit(
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

	public static List<StaticAirframeSweepCase> casesForPreset(
			String presetName,
			DroneConfig config,
			double baseSpeedMetersPerSecond,
			int steps
	) {
		if (presetName == null || presetName.isBlank()) {
			throw new IllegalArgumentException("presetName must not be blank.");
		}
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		if (!Double.isFinite(baseSpeedMetersPerSecond) || baseSpeedMetersPerSecond <= 0.0) {
			throw new IllegalArgumentException("baseSpeedMetersPerSecond must be positive.");
		}
		if (steps <= 0) {
			throw new IllegalArgumentException("steps must be positive.");
		}
		double angleRadians = Math.toRadians(SWEEP_ANGLE_DEGREES);
		double lateral = baseSpeedMetersPerSecond * Math.sin(angleRadians);
		double forward = -baseSpeedMetersPerSecond * Math.cos(angleRadians);
		return List.of(
				sweepCase(presetName, "forward_drag", "drag_z", config,
						new Vec3(0.0, 0.0, -baseSpeedMetersPerSecond), 0.0, steps, true, false, false),
				sweepCase(presetName, "reverse_drag_symmetry", "drag_z_reverse_check", config,
						new Vec3(0.0, 0.0, baseSpeedMetersPerSecond), 0.0, steps, true, false, false),
				sweepCase(presetName, "right_sideslip_12deg", "sideforce_yaw_positive", config,
						new Vec3(lateral, 0.0, forward), SWEEP_ANGLE_DEGREES, steps, false, true, false),
				sweepCase(presetName, "left_sideslip_12deg", "sideforce_yaw_negative", config,
						new Vec3(-lateral, 0.0, forward), SWEEP_ANGLE_DEGREES, steps, false, true, false),
				sweepCase(presetName, "positive_aoa_12deg", "lift_pitch_positive", config,
						new Vec3(0.0, lateral, forward), SWEEP_ANGLE_DEGREES, steps, false, false, true),
				sweepCase(presetName, "negative_aoa_12deg", "lift_pitch_negative", config,
						new Vec3(0.0, -lateral, forward), SWEEP_ANGLE_DEGREES, steps, false, false, true)
		);
	}

	private static StaticAirframeSweepCase sweepCase(
			String presetName,
			String sweepName,
			String fitRole,
			DroneConfig config,
			Vec3 inletVelocity,
			double sweepAngleDegrees,
			int steps,
			boolean forwardDragFit,
			boolean sideforceFit,
			boolean liftFit
	) {
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, inletVelocity, steps);
		Aerodynamics4McL2Bridge.L2RequestSpec request = probe.requestSpec();
		Vec3 inlet = new Vec3(request.inletVx(), request.inletVy(), request.inletVz());
		double speed = inlet.length();
		Vec3 normalized = speed <= 1.0e-12 ? Vec3.ZERO : inlet.multiply(1.0 / speed);
		double referenceArea = projectedEllipsoidArea(
				Math.abs(normalized.x()),
				Math.abs(normalized.y()),
				Math.abs(normalized.z()),
				probe.bodyHalfX(),
				probe.bodyHalfY(),
				probe.bodyHalfZ()
		);
		double referenceLength = probe.rotorSpanMeters();
		boolean ready = request.computeForceMoment()
				&& !request.outputFlowAtlas()
				&& speed > 1.0e-12
				&& referenceArea > 1.0e-12
				&& referenceLength > 1.0e-12;
		int gridCells = probe.nx() * probe.ny() * probe.nz();
		return new StaticAirframeSweepCase(
				presetName,
				sweepName,
				fitRole,
				inlet.x(),
				inlet.y(),
				inlet.z(),
				speed,
				normalized.x(),
				normalized.y(),
				normalized.z(),
				sweepAngleDegrees,
				request.steps(),
				gridCells,
				probe.cellSizeMeters(),
				probe.solidCellCount() / (double) Math.max(1, gridCells),
				referenceArea,
				referenceLength,
				request.outputFlowAtlas(),
				request.computeForceMoment(),
				ready,
				forwardDragFit,
				sideforceFit,
				liftFit,
				true
		);
	}

	private static double projectedEllipsoidArea(double nx, double ny, double nz, double halfX, double halfY, double halfZ) {
		double a = Math.max(1.0e-12, halfX);
		double b = Math.max(1.0e-12, halfY);
		double c = Math.max(1.0e-12, halfZ);
		return Math.PI * a * b * c * Math.sqrt(
				(nx * nx) / (a * a)
						+ (ny * ny) / (b * b)
						+ (nz * nz) / (c * c)
		);
	}

	private static MultiAxisSweepExtrema extrema(List<StaticAirframeSweepCase> sweepCases) {
		int ready = 0;
		int rawDisabled = 0;
		int sideforce = 0;
		int lift = 0;
		int momentPressure = 0;
		int maxGrid = 0;
		double maxSpeed = 0.0;
		double maxAngle = 0.0;
		double minArea = Double.POSITIVE_INFINITY;
		double maxArea = 0.0;
		for (StaticAirframeSweepCase sweepCase : sweepCases) {
			if (sweepCase.coefficientFitReady()) {
				ready++;
			}
			if (!sweepCase.outputFlowAtlas()) {
				rawDisabled++;
			}
			if (sweepCase.sideforceFitSample()) {
				sideforce++;
			}
			if (sweepCase.liftFitSample()) {
				lift++;
			}
			if (sweepCase.momentPressureCenterFitSample()) {
				momentPressure++;
			}
			maxGrid = Math.max(maxGrid, sweepCase.gridCellCount());
			maxSpeed = Math.max(maxSpeed, sweepCase.inletSpeedMetersPerSecond());
			maxAngle = Math.max(maxAngle, sweepCase.sweepAngleDegrees());
			minArea = Math.min(minArea, sweepCase.projectedReferenceAreaSquareMeters());
			maxArea = Math.max(maxArea, sweepCase.projectedReferenceAreaSquareMeters());
		}
		return new MultiAxisSweepExtrema(
				sweepCases.size(),
				ready,
				rawDisabled,
				sideforce,
				lift,
				momentPressure,
				maxGrid,
				maxSpeed,
				maxAngle,
				Double.isFinite(minArea) ? minArea : 0.0,
				maxArea
		);
	}
}
