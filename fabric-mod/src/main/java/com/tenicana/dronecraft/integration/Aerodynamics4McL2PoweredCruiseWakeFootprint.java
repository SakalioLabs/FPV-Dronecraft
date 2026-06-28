package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2PoweredCruiseWakeFootprint {
	private static final double AIR_DENSITY_KG_M3 = 1.225;
	private static final double RECOMMENDED_DOWNSTREAM_SAMPLE_DISTANCE_ROTORS = 3.0;
	private static final int RECOMMENDED_AXIAL_SAMPLE_PLANE_COUNT = 4;
	private static final int RECOMMENDED_SWEEP_SAMPLE_COLUMN_COUNT = 3;

	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Wake-Footprint-Packet";
	public static final String CAVEAT =
			"Forward-flight skew-wake footprint packet derives cruise slipstream contraction, wake skew angle, sweep distance, mass-flow, and local skew-wake probe targets from powered cruise source-map rows; keep audit-only until live A4MC powered-source and local skew-wake probes validate the near-field wake.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int FOOTPRINT_METRIC_COUNT = 45;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * FOOTPRINT_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseWakeFootprint() {
	}

	public record PoweredCruiseWakeFootprint(
			String presetName,
			String spinState,
			int rotorCount,
			int sourceTermCount,
			double inletSpeedMetersPerSecond,
			double edgewiseAdvanceRatio,
			double totalThrustNewtons,
			double thrustToWeight,
			double totalOpenAreaSquareMeters,
			double diskPlaneWakeAreaSquareMeters,
			double farWakeContractedAreaSquareMeters,
			double diskEquivalentRadiusMeters,
			double farWakeEquivalentRadiusMeters,
			double contractionAreaRatio,
			double contractionRadiusRatio,
			double meanPressureJumpPascals,
			double maxPressureJumpPascals,
			double idealInducedVelocityMetersPerSecond,
			double axialWakeVelocityMetersPerSecond,
			double freestreamVelocityMetersPerSecond,
			double resultantWakeVelocityMetersPerSecond,
			double wakeSkewAngleDegrees,
			double recommendedDownstreamSampleDistanceRotors,
			double downstreamSampleDistanceMeters,
			double freestreamSweepDistanceMeters,
			double skewedWakeCenterlineDistanceMeters,
			double axialTransitTimeSeconds,
			double massFlowKilogramsPerSecond,
			double axialMomentumFluxNewtons,
			double momentumFluxClosureErrorNewtons,
			double totalMomentumPowerWatts,
			double axialWakeKineticPowerWatts,
			double kineticPowerClosureErrorWatts,
			int requestGridCellCount,
			double requestCellSizeMeters,
			double downstreamSampleDistanceCells,
			double freestreamSweepDistanceCells,
			int recommendedAxialSamplePlaneCount,
			int recommendedSweepSampleColumnCount,
			int recommendedSkewWakeSampleCount,
			boolean poweredSourceApiAvailable,
			boolean skewWakeProbeApiAvailable,
			boolean runtimeCouplingAllowed,
			boolean validationBeforeRuntimeRequired,
			String status,
			String runtimeInfo
	) {
	}

	public record PoweredCruiseWakeFootprintExtrema(
			int footprintCount,
			int sourceTermCount,
			double maxInletSpeedMetersPerSecond,
			double maxEdgewiseAdvanceRatio,
			double maxWakeSkewAngleDegrees,
			double maxFreestreamSweepDistanceMeters,
			double maxFreestreamSweepDistanceCells,
			double maxResultantWakeVelocityMetersPerSecond,
			double maxAxialTransitTimeSeconds,
			int maxRecommendedSkewWakeSampleCount,
			int runtimeCouplingAllowedCount,
			int validationBeforeRuntimeRequiredCount
	) {
	}

	public record PoweredCruiseWakeFootprintAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int footprintMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			double airDensityKilogramsPerCubicMeter,
			double recommendedDownstreamSampleDistanceRotors,
			List<PoweredCruiseWakeFootprint> footprints,
			PoweredCruiseWakeFootprintExtrema extrema
	) {
		public PoweredCruiseWakeFootprintAudit {
			footprints = List.copyOf(footprints);
		}
	}

	public static PoweredCruiseWakeFootprintAudit audit() {
		List<PoweredCruiseWakeFootprint> footprints = List.of(
				footprint("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72),
				footprint("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64),
				footprint("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48),
				footprint("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80)
		);
		return new PoweredCruiseWakeFootprintAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				FOOTPRINT_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				AIR_DENSITY_KG_M3,
				RECOMMENDED_DOWNSTREAM_SAMPLE_DISTANCE_ROTORS,
				footprints,
				extrema(footprints)
		);
	}

	public static PoweredCruiseWakeFootprint footprint(
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
		return footprint(sourceMap, request);
	}

	public static PoweredCruiseWakeFootprint footprint(
			Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap sourceMap,
			Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request
	) {
		if (sourceMap == null || request == null) {
			throw new IllegalArgumentException("source map and request plan rows are required.");
		}
		if (!"cruise".equals(sourceMap.spinState()) || !"cruise".equals(request.spinState())) {
			throw new IllegalArgumentException("cruise wake footprint requires cruise source-map and request rows.");
		}
		double diskPlaneArea = sourceMap.totalOpenAreaSquareMeters();
		double farWakeArea = 0.5 * diskPlaneArea;
		double diskRadius = equivalentRadius(diskPlaneArea, sourceMap.rotorCount());
		double farWakeRadius = equivalentRadius(farWakeArea, sourceMap.rotorCount());
		double axialWakeVelocity = sourceMap.farWakeVelocityMetersPerSecond();
		double freestreamVelocity = sourceMap.inletSpeedMetersPerSecond();
		double resultantWakeVelocity = Math.hypot(axialWakeVelocity, freestreamVelocity);
		double skewAngleDegrees = Math.toDegrees(Math.atan2(freestreamVelocity, axialWakeVelocity));
		double downstreamDistance = RECOMMENDED_DOWNSTREAM_SAMPLE_DISTANCE_ROTORS * diskRadius;
		double axialTransitTime = ratio(downstreamDistance, axialWakeVelocity);
		double sweepDistance = freestreamVelocity * axialTransitTime;
		double centerlineDistance = Math.hypot(downstreamDistance, sweepDistance);
		double massFlow = AIR_DENSITY_KG_M3 * diskPlaneArea * sourceMap.idealInducedVelocityMetersPerSecond();
		double axialMomentumFlux = massFlow * axialWakeVelocity;
		double axialWakePower = 0.5 * massFlow * axialWakeVelocity * axialWakeVelocity;
		return new PoweredCruiseWakeFootprint(
				sourceMap.presetName(),
				sourceMap.spinState(),
				sourceMap.rotorCount(),
				sourceMap.sourceTermCount(),
				sourceMap.inletSpeedMetersPerSecond(),
				sourceMap.edgewiseAdvanceRatio(),
				sourceMap.totalThrustNewtons(),
				sourceMap.thrustToWeight(),
				sourceMap.totalOpenAreaSquareMeters(),
				diskPlaneArea,
				farWakeArea,
				diskRadius,
				farWakeRadius,
				ratio(farWakeArea, diskPlaneArea),
				ratio(farWakeRadius, diskRadius),
				sourceMap.meanPressureJumpPascals(),
				sourceMap.maxPressureJumpPascals(),
				sourceMap.idealInducedVelocityMetersPerSecond(),
				axialWakeVelocity,
				freestreamVelocity,
				resultantWakeVelocity,
				skewAngleDegrees,
				RECOMMENDED_DOWNSTREAM_SAMPLE_DISTANCE_ROTORS,
				downstreamDistance,
				sweepDistance,
				centerlineDistance,
				axialTransitTime,
				massFlow,
				axialMomentumFlux,
				Math.abs(sourceMap.totalThrustNewtons() - axialMomentumFlux),
				sourceMap.totalMomentumPowerWatts(),
				axialWakePower,
				Math.abs(sourceMap.totalMomentumPowerWatts() - axialWakePower),
				request.gridCellCount(),
				request.cellSizeMeters(),
				ratio(downstreamDistance, request.cellSizeMeters()),
				ratio(sweepDistance, request.cellSizeMeters()),
				RECOMMENDED_AXIAL_SAMPLE_PLANE_COUNT,
				RECOMMENDED_SWEEP_SAMPLE_COLUMN_COUNT,
				sourceMap.rotorCount() * RECOMMENDED_AXIAL_SAMPLE_PLANE_COUNT
						* RECOMMENDED_SWEEP_SAMPLE_COLUMN_COUNT,
				sourceMap.poweredSourceApiAvailable(),
				false,
				false,
				true,
				"target-only-skew-wake-probe-unavailable",
				"audit-only-unvalidated-cruise-skew-wake"
		);
	}

	private static PoweredCruiseWakeFootprintExtrema extrema(List<PoweredCruiseWakeFootprint> footprints) {
		int sourceTerms = 0;
		double maxInletSpeed = 0.0;
		double maxEdgewiseAdvance = 0.0;
		double maxWakeSkewAngle = 0.0;
		double maxSweepDistance = 0.0;
		double maxSweepDistanceCells = 0.0;
		double maxResultantWakeVelocity = 0.0;
		double maxAxialTransitTime = 0.0;
		int maxWakeSamples = 0;
		int runtimeAllowed = 0;
		int validationRequired = 0;
		for (PoweredCruiseWakeFootprint footprint : footprints) {
			sourceTerms += footprint.sourceTermCount();
			maxInletSpeed = Math.max(maxInletSpeed, footprint.inletSpeedMetersPerSecond());
			maxEdgewiseAdvance = Math.max(maxEdgewiseAdvance, footprint.edgewiseAdvanceRatio());
			maxWakeSkewAngle = Math.max(maxWakeSkewAngle, footprint.wakeSkewAngleDegrees());
			maxSweepDistance = Math.max(maxSweepDistance, footprint.freestreamSweepDistanceMeters());
			maxSweepDistanceCells = Math.max(maxSweepDistanceCells, footprint.freestreamSweepDistanceCells());
			maxResultantWakeVelocity = Math.max(maxResultantWakeVelocity,
					footprint.resultantWakeVelocityMetersPerSecond());
			maxAxialTransitTime = Math.max(maxAxialTransitTime, footprint.axialTransitTimeSeconds());
			maxWakeSamples = Math.max(maxWakeSamples, footprint.recommendedSkewWakeSampleCount());
			if (footprint.runtimeCouplingAllowed()) {
				runtimeAllowed++;
			}
			if (footprint.validationBeforeRuntimeRequired()) {
				validationRequired++;
			}
		}
		return new PoweredCruiseWakeFootprintExtrema(
				footprints.size(),
				sourceTerms,
				maxInletSpeed,
				maxEdgewiseAdvance,
				maxWakeSkewAngle,
				maxSweepDistance,
				maxSweepDistanceCells,
				maxResultantWakeVelocity,
				maxAxialTransitTime,
				maxWakeSamples,
				runtimeAllowed,
				validationRequired
		);
	}

	private static double equivalentRadius(double totalAreaSquareMeters, int rotorCount) {
		if (!Double.isFinite(totalAreaSquareMeters) || totalAreaSquareMeters <= 0.0 || rotorCount <= 0) {
			return 0.0;
		}
		return Math.sqrt(totalAreaSquareMeters / rotorCount / Math.PI);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
