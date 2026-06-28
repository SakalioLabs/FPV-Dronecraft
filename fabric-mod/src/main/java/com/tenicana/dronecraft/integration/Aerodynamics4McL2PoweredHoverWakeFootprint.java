package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2PoweredHoverWakeFootprint {
	private static final double AIR_DENSITY_KG_M3 = 1.225;
	private static final int RECOMMENDED_AXIAL_SAMPLE_PLANE_COUNT = 3;

	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Wake-Footprint-Packet";
	public static final String CAVEAT =
			"Momentum-theory hover wake footprint packet derives slipstream contraction, downwash, mass-flow, and local wake-probe targets from powered hover source-map rows; keep audit-only until live A4MC powered-source and local wake probes validate the near-field downwash.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int FOOTPRINT_METRIC_COUNT = 34;
	public static final int SUMMARY_METRIC_ROW_COUNT = 10;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * FOOTPRINT_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverWakeFootprint() {
	}

	public record PoweredHoverWakeFootprint(
			String presetName,
			String spinState,
			int rotorCount,
			int sourceTermCount,
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
			double farWakeVelocityMetersPerSecond,
			double massFlowKilogramsPerSecond,
			double farWakeMomentumFluxNewtons,
			double momentumFluxClosureErrorNewtons,
			double totalMomentumPowerWatts,
			double farWakeKineticPowerWatts,
			double kineticPowerClosureErrorWatts,
			int requestGridCellCount,
			double requestCellSizeMeters,
			double diskEquivalentRadiusCells,
			double farWakeEquivalentRadiusCells,
			int recommendedAxialSamplePlaneCount,
			int recommendedRotorWakeSampleCount,
			boolean poweredSourceApiAvailable,
			boolean localWakeProbeApiAvailable,
			boolean runtimeCouplingAllowed,
			boolean validationBeforeRuntimeRequired,
			String status,
			String runtimeInfo
	) {
	}

	public record PoweredHoverWakeFootprintExtrema(
			int footprintCount,
			int sourceTermCount,
			double maxTotalThrustNewtons,
			double maxMeanPressureJumpPascals,
			double maxFarWakeVelocityMetersPerSecond,
			double maxMassFlowKilogramsPerSecond,
			double maxTotalMomentumPowerWatts,
			double maxDiskEquivalentRadiusMeters,
			int maxRecommendedRotorWakeSampleCount,
			int runtimeCouplingAllowedCount
	) {
	}

	public record PoweredHoverWakeFootprintAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int footprintMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			double airDensityKilogramsPerCubicMeter,
			List<PoweredHoverWakeFootprint> footprints,
			PoweredHoverWakeFootprintExtrema extrema
	) {
		public PoweredHoverWakeFootprintAudit {
			footprints = List.copyOf(footprints);
		}
	}

	public static PoweredHoverWakeFootprintAudit audit() {
		List<PoweredHoverWakeFootprint> footprints = List.of(
				footprint("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72),
				footprint("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64),
				footprint("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48),
				footprint("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80)
		);
		return new PoweredHoverWakeFootprintAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				FOOTPRINT_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				AIR_DENSITY_KG_M3,
				footprints,
				extrema(footprints)
		);
	}

	public static PoweredHoverWakeFootprint footprint(
			String presetName,
			DroneConfig config,
			Vec3 inletVelocity,
			int steps
	) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap =
				Aerodynamics4McL2PoweredHoverSourceMap.sourceMap(presetName, config, inletVelocity, steps);
		Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request =
				Aerodynamics4McL2PoweredSourceRequestPlan.request(presetName, config, inletVelocity, steps, "hover");
		return footprint(sourceMap, request);
	}

	public static PoweredHoverWakeFootprint footprint(
			Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap,
			Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request
	) {
		if (sourceMap == null || request == null) {
			throw new IllegalArgumentException("source map and request plan rows are required.");
		}
		if (!"hover".equals(sourceMap.spinState()) || !"hover".equals(request.spinState())) {
			throw new IllegalArgumentException("hover wake footprint requires hover source-map and request rows.");
		}
		double diskPlaneArea = sourceMap.totalOpenAreaSquareMeters();
		double farWakeArea = 0.5 * diskPlaneArea;
		double diskRadius = equivalentRadius(diskPlaneArea, sourceMap.rotorCount());
		double farWakeRadius = equivalentRadius(farWakeArea, sourceMap.rotorCount());
		double massFlow = AIR_DENSITY_KG_M3 * diskPlaneArea * sourceMap.idealInducedVelocityMetersPerSecond();
		double momentumFlux = massFlow * sourceMap.farWakeVelocityMetersPerSecond();
		double wakePower = 0.5 * massFlow
				* sourceMap.farWakeVelocityMetersPerSecond()
				* sourceMap.farWakeVelocityMetersPerSecond();
		return new PoweredHoverWakeFootprint(
				sourceMap.presetName(),
				sourceMap.spinState(),
				sourceMap.rotorCount(),
				sourceMap.sourceTermCount(),
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
				sourceMap.farWakeVelocityMetersPerSecond(),
				massFlow,
				momentumFlux,
				Math.abs(sourceMap.totalThrustNewtons() - momentumFlux),
				sourceMap.totalMomentumPowerWatts(),
				wakePower,
				Math.abs(sourceMap.totalMomentumPowerWatts() - wakePower),
				request.gridCellCount(),
				request.cellSizeMeters(),
				ratio(diskRadius, request.cellSizeMeters()),
				ratio(farWakeRadius, request.cellSizeMeters()),
				RECOMMENDED_AXIAL_SAMPLE_PLANE_COUNT,
				sourceMap.rotorCount() * RECOMMENDED_AXIAL_SAMPLE_PLANE_COUNT,
				sourceMap.poweredSourceApiAvailable(),
				false,
				false,
				true,
				"target-only-local-wake-probe-unavailable",
				"audit-only-unvalidated-hover-downwash"
		);
	}

	private static PoweredHoverWakeFootprintExtrema extrema(List<PoweredHoverWakeFootprint> footprints) {
		int sourceTerms = 0;
		double maxTotalThrust = 0.0;
		double maxMeanPressureJump = 0.0;
		double maxFarWakeVelocity = 0.0;
		double maxMassFlow = 0.0;
		double maxPower = 0.0;
		double maxDiskRadius = 0.0;
		int maxWakeSamples = 0;
		int runtimeAllowed = 0;
		for (PoweredHoverWakeFootprint footprint : footprints) {
			sourceTerms += footprint.sourceTermCount();
			maxTotalThrust = Math.max(maxTotalThrust, footprint.totalThrustNewtons());
			maxMeanPressureJump = Math.max(maxMeanPressureJump, footprint.meanPressureJumpPascals());
			maxFarWakeVelocity = Math.max(maxFarWakeVelocity, footprint.farWakeVelocityMetersPerSecond());
			maxMassFlow = Math.max(maxMassFlow, footprint.massFlowKilogramsPerSecond());
			maxPower = Math.max(maxPower, footprint.totalMomentumPowerWatts());
			maxDiskRadius = Math.max(maxDiskRadius, footprint.diskEquivalentRadiusMeters());
			maxWakeSamples = Math.max(maxWakeSamples, footprint.recommendedRotorWakeSampleCount());
			if (footprint.runtimeCouplingAllowed()) {
				runtimeAllowed++;
			}
		}
		return new PoweredHoverWakeFootprintExtrema(
				footprints.size(),
				sourceTerms,
				maxTotalThrust,
				maxMeanPressureJump,
				maxFarWakeVelocity,
				maxMassFlow,
				maxPower,
				maxDiskRadius,
				maxWakeSamples,
				runtimeAllowed
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
