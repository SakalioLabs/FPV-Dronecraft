package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap {
	private static final double AIR_DENSITY_KG_M3 = 1.225;
	private static final int AXIAL_SAMPLE_PLANE_COUNT = 4;
	private static final int SWEEP_SAMPLE_COLUMN_COUNT = 3;
	private static final int[] SWEEP_COLUMN_INDEXES = { -1, 0, 1 };
	private static final double EPSILON = 1.0e-12;

	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Skew-Wake-Probe-Map-Packet";
	public static final String CAVEAT =
			"Rotor-resolved forward-flight skew-wake probe map for future A4MC local wake validation; keep audit-only until live powered-source and skew-wake probes recover the expected per-rotor wake geometry, pressure, and momentum targets.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int ROTOR_SAMPLE_COUNT = 4;
	public static final int PROBE_SAMPLE_COUNT =
			PRESET_SAMPLE_COUNT * ROTOR_SAMPLE_COUNT * AXIAL_SAMPLE_PLANE_COUNT * SWEEP_SAMPLE_COLUMN_COUNT;
	public static final int PROBE_METRIC_COUNT = 48;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PROBE_SAMPLE_COUNT * PROBE_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap() {
	}

	public record PoweredCruiseSkewWakeProbe(
			String presetName,
			String spinState,
			int rotorIndex,
			int axialPlaneIndex,
			int sweepColumnIndex,
			double axialPlaneFraction,
			double rotorCenterXBodyMeters,
			double rotorCenterYBodyMeters,
			double rotorCenterZBodyMeters,
			double thrustAxisXBody,
			double thrustAxisYBody,
			double thrustAxisZBody,
			double axialWakeAxisXBody,
			double axialWakeAxisYBody,
			double axialWakeAxisZBody,
			double freestreamAxisXBody,
			double freestreamAxisYBody,
			double freestreamAxisZBody,
			double lateralSweepAxisXBody,
			double lateralSweepAxisYBody,
			double lateralSweepAxisZBody,
			double probeXBodyMeters,
			double probeYBodyMeters,
			double probeZBodyMeters,
			double axialDistanceMeters,
			double freestreamSweepDistanceMeters,
			double lateralOffsetMeters,
			double centerlineDistanceMeters,
			double distanceFromRotorMeters,
			double perRotorFarWakeAreaSquareMeters,
			double perRotorFarWakeEquivalentRadiusMeters,
			double expectedAxialWakeVelocityMetersPerSecond,
			double expectedFreestreamVelocityMetersPerSecond,
			double expectedResultantWakeVelocityMetersPerSecond,
			double wakeSkewAngleDegrees,
			double expectedAxialWakePressurePascals,
			double expectedResultantDynamicPressurePascals,
			double perRotorMassFlowKilogramsPerSecond,
			double perRotorAxialMomentumFluxNewtons,
			double axialMomentumFluxClosureErrorNewtons,
			double requestCellSizeMeters,
			double axialDistanceCells,
			double freestreamSweepDistanceCells,
			double lateralOffsetCells,
			boolean skewWakeProbeApiAvailable,
			boolean runtimeCouplingAllowed,
			boolean validationBeforeRuntimeRequired,
			String status,
			String runtimeInfo
	) {
	}

	public record PoweredCruiseSkewWakeProbeExtrema(
			int probeCount,
			int sourceTermCount,
			int axialPlaneCount,
			int sweepColumnCount,
			double maxWakeSkewAngleDegrees,
			double maxProbeDistanceMeters,
			double maxCenterlineDistanceMeters,
			double maxResultantDynamicPressurePascals,
			double maxAxialDistanceCells,
			double maxFreestreamSweepDistanceCells,
			int runtimeCouplingAllowedCount,
			int validationBeforeRuntimeRequiredCount
	) {
	}

	public record PoweredCruiseSkewWakeProbeAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int rotorSampleCount,
			int axialSamplePlaneCount,
			int sweepSampleColumnCount,
			int probeSampleCount,
			int probeMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			double airDensityKilogramsPerCubicMeter,
			List<PoweredCruiseSkewWakeProbe> probes,
			PoweredCruiseSkewWakeProbeExtrema extrema
	) {
		public PoweredCruiseSkewWakeProbeAudit {
			probes = List.copyOf(probes);
		}
	}

	public static PoweredCruiseSkewWakeProbeAudit audit() {
		List<PoweredCruiseSkewWakeProbe> probes = new ArrayList<>(PROBE_SAMPLE_COUNT);
		probes.addAll(probes("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72));
		probes.addAll(probes("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64));
		probes.addAll(probes("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48));
		probes.addAll(probes("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80));
		return new PoweredCruiseSkewWakeProbeAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				ROTOR_SAMPLE_COUNT,
				AXIAL_SAMPLE_PLANE_COUNT,
				SWEEP_SAMPLE_COLUMN_COUNT,
				PROBE_SAMPLE_COUNT,
				PROBE_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				AIR_DENSITY_KG_M3,
				probes,
				extrema(probes)
		);
	}

	public static List<PoweredCruiseSkewWakeProbe> probes(
			String presetName,
			DroneConfig config,
			Vec3 inletVelocity,
			int steps
	) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap sourceMap =
				Aerodynamics4McL2PoweredCruiseSourceMap.sourceMap(presetName, config, inletVelocity, steps);
		Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request =
				Aerodynamics4McL2PoweredSourceRequestPlan.request(presetName, config, inletVelocity, steps, "cruise");
		Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint footprint =
				Aerodynamics4McL2PoweredCruiseWakeFootprint.footprint(sourceMap, request);
		return probes(sourceMap, footprint, request);
	}

	public static List<PoweredCruiseSkewWakeProbe> probes(
			Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap sourceMap,
			Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint footprint,
			Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request
	) {
		requireCruiseRows(sourceMap, footprint, request);
		List<PoweredCruiseSkewWakeProbe> probes = new ArrayList<>(
				sourceMap.sourceTerms().size() * AXIAL_SAMPLE_PLANE_COUNT * SWEEP_SAMPLE_COLUMN_COUNT
		);
		for (int axialPlaneIndex = 1; axialPlaneIndex <= AXIAL_SAMPLE_PLANE_COUNT; axialPlaneIndex++) {
			for (int sweepColumnIndex : SWEEP_COLUMN_INDEXES) {
				for (Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm sourceTerm : sourceMap.sourceTerms()) {
					probes.add(probe(sourceMap, footprint, request, sourceTerm, axialPlaneIndex, sweepColumnIndex));
				}
			}
		}
		return List.copyOf(probes);
	}

	public static PoweredCruiseSkewWakeProbe probe(
			Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap sourceMap,
			Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint footprint,
			Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request,
			Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm sourceTerm,
			int axialPlaneIndex,
			int sweepColumnIndex
	) {
		requireCruiseRows(sourceMap, footprint, request);
		if (sourceTerm == null) {
			throw new IllegalArgumentException("source term is required.");
		}
		if (axialPlaneIndex < 1 || axialPlaneIndex > AXIAL_SAMPLE_PLANE_COUNT) {
			throw new IllegalArgumentException("axialPlaneIndex must be between 1 and 4.");
		}
		if (!isSupportedSweepColumn(sweepColumnIndex)) {
			throw new IllegalArgumentException("sweepColumnIndex must be -1, 0, or 1.");
		}
		Vec3 thrustAxis = sourceTerm.thrustAxisBody().normalized();
		Vec3 axialWakeAxis = thrustAxis.multiply(-1.0);
		Vec3 freestreamAxis = new Vec3(
				request.inletVxMetersPerSecond(),
				request.inletVyMetersPerSecond(),
				request.inletVzMetersPerSecond()
		).normalized();
		if (freestreamAxis.length() <= EPSILON) {
			freestreamAxis = new Vec3(0.0, 0.0, -1.0);
		}
		Vec3 lateralSweepAxis = freestreamAxis.cross(axialWakeAxis).normalized();
		if (lateralSweepAxis.length() <= EPSILON) {
			lateralSweepAxis = fallbackLateralAxis(axialWakeAxis);
		}
		double axialFraction = (double) axialPlaneIndex / AXIAL_SAMPLE_PLANE_COUNT;
		double axialDistance = footprint.downstreamSampleDistanceMeters() * axialFraction;
		double freestreamSweepDistance = footprint.freestreamSweepDistanceMeters() * axialFraction;
		double farWakeArea = 0.5 * sourceTerm.openAreaSquareMeters();
		double farWakeRadius = equivalentRadius(farWakeArea);
		double lateralOffset = farWakeRadius * sweepColumnIndex;
		Vec3 probePoint = sourceTerm.centerBodyMeters()
				.add(axialWakeAxis.multiply(axialDistance))
				.add(freestreamAxis.multiply(freestreamSweepDistance))
				.add(lateralSweepAxis.multiply(lateralOffset));
		double centerlineDistance = Math.hypot(axialDistance, freestreamSweepDistance);
		double distanceFromRotor = Math.hypot(centerlineDistance, lateralOffset);
		double massFlow = AIR_DENSITY_KG_M3
				* sourceTerm.openAreaSquareMeters()
				* footprint.idealInducedVelocityMetersPerSecond();
		double momentumFlux = massFlow * footprint.axialWakeVelocityMetersPerSecond();
		return new PoweredCruiseSkewWakeProbe(
				sourceMap.presetName(),
				sourceMap.spinState(),
				sourceTerm.rotorIndex(),
				axialPlaneIndex,
				sweepColumnIndex,
				axialFraction,
				sourceTerm.centerBodyMeters().x(),
				sourceTerm.centerBodyMeters().y(),
				sourceTerm.centerBodyMeters().z(),
				thrustAxis.x(),
				thrustAxis.y(),
				thrustAxis.z(),
				axialWakeAxis.x(),
				axialWakeAxis.y(),
				axialWakeAxis.z(),
				freestreamAxis.x(),
				freestreamAxis.y(),
				freestreamAxis.z(),
				lateralSweepAxis.x(),
				lateralSweepAxis.y(),
				lateralSweepAxis.z(),
				probePoint.x(),
				probePoint.y(),
				probePoint.z(),
				axialDistance,
				freestreamSweepDistance,
				lateralOffset,
				centerlineDistance,
				distanceFromRotor,
				farWakeArea,
				farWakeRadius,
				footprint.axialWakeVelocityMetersPerSecond(),
				footprint.freestreamVelocityMetersPerSecond(),
				footprint.resultantWakeVelocityMetersPerSecond(),
				footprint.wakeSkewAngleDegrees(),
				ratio(sourceTerm.thrustNewtons(), farWakeArea),
				0.5 * AIR_DENSITY_KG_M3
						* footprint.resultantWakeVelocityMetersPerSecond()
						* footprint.resultantWakeVelocityMetersPerSecond(),
				massFlow,
				momentumFlux,
				Math.abs(sourceTerm.thrustNewtons() - momentumFlux),
				request.cellSizeMeters(),
				ratio(axialDistance, request.cellSizeMeters()),
				ratio(freestreamSweepDistance, request.cellSizeMeters()),
				ratio(lateralOffset, request.cellSizeMeters()),
				false,
				false,
				true,
				"target-only-cruise-skew-wake-probe-unavailable",
				"audit-only-unvalidated-cruise-skew-wake-probe"
		);
	}

	private static PoweredCruiseSkewWakeProbeExtrema extrema(List<PoweredCruiseSkewWakeProbe> probes) {
		double maxSkewAngle = 0.0;
		double maxProbeDistance = 0.0;
		double maxCenterlineDistance = 0.0;
		double maxDynamicPressure = 0.0;
		double maxAxialCells = 0.0;
		double maxSweepCells = 0.0;
		int runtimeAllowed = 0;
		int validationRequired = 0;
		for (PoweredCruiseSkewWakeProbe probe : probes) {
			maxSkewAngle = Math.max(maxSkewAngle, probe.wakeSkewAngleDegrees());
			maxProbeDistance = Math.max(maxProbeDistance, probe.distanceFromRotorMeters());
			maxCenterlineDistance = Math.max(maxCenterlineDistance, probe.centerlineDistanceMeters());
			maxDynamicPressure = Math.max(maxDynamicPressure, probe.expectedResultantDynamicPressurePascals());
			maxAxialCells = Math.max(maxAxialCells, probe.axialDistanceCells());
			maxSweepCells = Math.max(maxSweepCells, probe.freestreamSweepDistanceCells());
			if (probe.runtimeCouplingAllowed()) {
				runtimeAllowed++;
			}
			if (probe.validationBeforeRuntimeRequired()) {
				validationRequired++;
			}
		}
		return new PoweredCruiseSkewWakeProbeExtrema(
				probes.size(),
				PRESET_SAMPLE_COUNT * ROTOR_SAMPLE_COUNT,
				AXIAL_SAMPLE_PLANE_COUNT,
				SWEEP_SAMPLE_COLUMN_COUNT,
				maxSkewAngle,
				maxProbeDistance,
				maxCenterlineDistance,
				maxDynamicPressure,
				maxAxialCells,
				maxSweepCells,
				runtimeAllowed,
				validationRequired
		);
	}

	private static void requireCruiseRows(
			Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap sourceMap,
			Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint footprint,
			Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request
	) {
		if (sourceMap == null || footprint == null || request == null) {
			throw new IllegalArgumentException("source map, wake footprint, and request rows are required.");
		}
		if (!"cruise".equals(sourceMap.spinState())
				|| !"cruise".equals(footprint.spinState())
				|| !"cruise".equals(request.spinState())) {
			throw new IllegalArgumentException("skew-wake probe map requires cruise source, footprint, and request rows.");
		}
		if (!sourceMap.presetName().equals(footprint.presetName())
				|| !sourceMap.presetName().equals(request.presetName())) {
			throw new IllegalArgumentException("source, footprint, and request rows must use the same preset.");
		}
	}

	private static boolean isSupportedSweepColumn(int sweepColumnIndex) {
		for (int supported : SWEEP_COLUMN_INDEXES) {
			if (supported == sweepColumnIndex) {
				return true;
			}
		}
		return false;
	}

	private static Vec3 fallbackLateralAxis(Vec3 axialWakeAxis) {
		Vec3 xAxis = new Vec3(1.0, 0.0, 0.0);
		Vec3 candidate = xAxis.cross(axialWakeAxis).normalized();
		if (candidate.length() > EPSILON) {
			return candidate;
		}
		return new Vec3(0.0, 0.0, 1.0).cross(axialWakeAxis).normalized();
	}

	private static double equivalentRadius(double areaSquareMeters) {
		return areaSquareMeters <= EPSILON ? 0.0 : Math.sqrt(areaSquareMeters / Math.PI);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
