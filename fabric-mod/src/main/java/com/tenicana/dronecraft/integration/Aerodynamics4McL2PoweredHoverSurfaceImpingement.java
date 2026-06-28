package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.SurfaceNearfieldCalibration;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2PoweredHoverSurfaceImpingement {
	private static final double[] CLEARANCE_OVER_RADIUS_SAMPLES = { 0.5, 1.0, 2.0, 4.0 };
	private static final String[] SURFACE_SAMPLES = { "ground", "ceiling" };

	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Surface-Impingement-Packet";
	public static final String CAVEAT =
			"Audit-only powered-hover wake impingement targets couple hover downwash momentum flux to JIRS ground/ceiling surface-effect curves; validate live A4MC powered-source and local wake probes before any runtime gameplay coupling.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int CLEARANCE_SAMPLE_COUNT = 4;
	public static final int SURFACE_SAMPLE_COUNT = 2;
	public static final int TARGET_SAMPLE_COUNT = PRESET_SAMPLE_COUNT * CLEARANCE_SAMPLE_COUNT * SURFACE_SAMPLE_COUNT;
	public static final int TARGET_METRIC_COUNT = 25;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ TARGET_SAMPLE_COUNT * TARGET_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverSurfaceImpingement() {
	}

	public record PoweredHoverSurfaceImpingementTarget(
			String presetName,
			String spinState,
			String surfaceType,
			double clearanceOverRadius,
			double clearanceMeters,
			double clearanceOverFarWakeRadius,
			int rotorCount,
			int sourceTermCount,
			double rotorRadiusMeters,
			double diskEquivalentRadiusMeters,
			double farWakeEquivalentRadiusMeters,
			double totalThrustNewtons,
			double farWakeMomentumFluxNewtons,
			double farWakeContractedAreaSquareMeters,
			double farWakeImpingementPressurePascals,
			double surfaceCurveMultiplier,
			double surfaceExtraLiftFraction,
			double totalSurfaceCushionForceNewtons,
			double perRotorSurfaceCushionForceNewtons,
			double surfaceCushionForceOverWeight,
			double surfaceReactionPressurePascals,
			boolean localWakeProbeApiAvailable,
			boolean runtimeCouplingAllowed,
			boolean validationBeforeRuntimeRequired,
			String status,
			String runtimeInfo
	) {
	}

	public record PoweredHoverSurfaceImpingementExtrema(
			int targetCount,
			int groundTargetCount,
			int ceilingTargetCount,
			double maxSurfaceCurveMultiplier,
			double maxSurfaceExtraLiftFraction,
			double maxTotalSurfaceCushionForceNewtons,
			double maxSurfaceReactionPressurePascals,
			double maxFarWakeImpingementPressurePascals,
			double maxClearanceOverFarWakeRadius,
			double minClearanceOverFarWakeRadius,
			int runtimeCouplingAllowedCount,
			int localWakeProbeApiAvailableCount
	) {
	}

	public record PoweredHoverSurfaceImpingementAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int clearanceSampleCount,
			int surfaceSampleCount,
			int targetSampleCount,
			int targetMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverSurfaceImpingementTarget> targets,
			PoweredHoverSurfaceImpingementExtrema extrema
	) {
		public PoweredHoverSurfaceImpingementAudit {
			targets = List.copyOf(targets);
		}
	}

	public static PoweredHoverSurfaceImpingementAudit audit() {
		List<PoweredHoverSurfaceImpingementTarget> targets = new ArrayList<>(TARGET_SAMPLE_COUNT);
		targets.addAll(targets("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72));
		targets.addAll(targets("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64));
		targets.addAll(targets("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48));
		targets.addAll(targets("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80));
		return new PoweredHoverSurfaceImpingementAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				CLEARANCE_SAMPLE_COUNT,
				SURFACE_SAMPLE_COUNT,
				TARGET_SAMPLE_COUNT,
				TARGET_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				targets,
				extrema(targets)
		);
	}

	public static List<PoweredHoverSurfaceImpingementTarget> targets(
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
		Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint footprint =
				Aerodynamics4McL2PoweredHoverWakeFootprint.footprint(
						sourceMap,
						Aerodynamics4McL2PoweredSourceRequestPlan.request(presetName, config, inletVelocity, steps, "hover")
				);
		List<PoweredHoverSurfaceImpingementTarget> targets = new ArrayList<>(
				CLEARANCE_SAMPLE_COUNT * SURFACE_SAMPLE_COUNT
		);
		for (String surfaceType : SURFACE_SAMPLES) {
			for (double clearanceOverRadius : CLEARANCE_OVER_RADIUS_SAMPLES) {
				targets.add(target(sourceMap, footprint, surfaceType, clearanceOverRadius));
			}
		}
		return List.copyOf(targets);
	}

	public static PoweredHoverSurfaceImpingementTarget target(
			Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap,
			Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint footprint,
			String surfaceType,
			double clearanceOverRadius
	) {
		if (sourceMap == null || footprint == null) {
			throw new IllegalArgumentException("source map and wake footprint rows are required.");
		}
		if (!"hover".equals(sourceMap.spinState()) || !"hover".equals(footprint.spinState())) {
			throw new IllegalArgumentException("powered hover surface impingement requires hover inputs.");
		}
		String surface = surfaceType == null ? "" : surfaceType.trim();
		if (!"ground".equals(surface) && !"ceiling".equals(surface)) {
			throw new IllegalArgumentException("surfaceType must be ground or ceiling.");
		}
		double normalizedClearance = Math.max(0.0, finiteOrZero(clearanceOverRadius));
		double rotorRadius = meanRotorRadius(sourceMap.sourceTerms());
		double clearanceMeters = rotorRadius * normalizedClearance;
		double surfaceMultiplier = "ceiling".equals(surface)
				? SurfaceNearfieldCalibration.jirsCeilingCurveFitMultiplier(normalizedClearance)
				: SurfaceNearfieldCalibration.jirsGroundCurveFitMultiplier(normalizedClearance);
		double surfaceExtra = Math.max(0.0, surfaceMultiplier - 1.0);
		double totalCushionForce = footprint.totalThrustNewtons() * surfaceExtra;
		double impingementPressure = ratio(
				footprint.farWakeMomentumFluxNewtons(),
				footprint.farWakeContractedAreaSquareMeters()
		);
		double reactionPressure = ratio(totalCushionForce, footprint.farWakeContractedAreaSquareMeters());
		return new PoweredHoverSurfaceImpingementTarget(
				sourceMap.presetName(),
				sourceMap.spinState(),
				surface,
				normalizedClearance,
				clearanceMeters,
				ratio(clearanceMeters, footprint.farWakeEquivalentRadiusMeters()),
				sourceMap.rotorCount(),
				sourceMap.sourceTermCount(),
				rotorRadius,
				footprint.diskEquivalentRadiusMeters(),
				footprint.farWakeEquivalentRadiusMeters(),
				footprint.totalThrustNewtons(),
				footprint.farWakeMomentumFluxNewtons(),
				footprint.farWakeContractedAreaSquareMeters(),
				impingementPressure,
				surfaceMultiplier,
				surfaceExtra,
				totalCushionForce,
				ratio(totalCushionForce, sourceMap.rotorCount()),
				ratio(totalCushionForce, footprint.totalThrustNewtons()),
				reactionPressure,
				false,
				false,
				true,
				"target-only-surface-wake-probe-unavailable",
				"audit-only-unvalidated-surface-impingement"
		);
	}

	private static PoweredHoverSurfaceImpingementExtrema extrema(
			List<PoweredHoverSurfaceImpingementTarget> targets
	) {
		int ground = 0;
		int ceiling = 0;
		double maxMultiplier = 0.0;
		double maxExtra = 0.0;
		double maxCushionForce = 0.0;
		double maxReactionPressure = 0.0;
		double maxImpingementPressure = 0.0;
		double maxClearanceOverWake = 0.0;
		double minClearanceOverWake = Double.POSITIVE_INFINITY;
		int runtimeAllowed = 0;
		int localWakeAvailable = 0;
		for (PoweredHoverSurfaceImpingementTarget target : targets) {
			if ("ground".equals(target.surfaceType())) {
				ground++;
			}
			if ("ceiling".equals(target.surfaceType())) {
				ceiling++;
			}
			maxMultiplier = Math.max(maxMultiplier, target.surfaceCurveMultiplier());
			maxExtra = Math.max(maxExtra, target.surfaceExtraLiftFraction());
			maxCushionForce = Math.max(maxCushionForce, target.totalSurfaceCushionForceNewtons());
			maxReactionPressure = Math.max(maxReactionPressure, target.surfaceReactionPressurePascals());
			maxImpingementPressure = Math.max(maxImpingementPressure, target.farWakeImpingementPressurePascals());
			maxClearanceOverWake = Math.max(maxClearanceOverWake, target.clearanceOverFarWakeRadius());
			minClearanceOverWake = Math.min(minClearanceOverWake, target.clearanceOverFarWakeRadius());
			if (target.runtimeCouplingAllowed()) {
				runtimeAllowed++;
			}
			if (target.localWakeProbeApiAvailable()) {
				localWakeAvailable++;
			}
		}
		return new PoweredHoverSurfaceImpingementExtrema(
				targets.size(),
				ground,
				ceiling,
				maxMultiplier,
				maxExtra,
				maxCushionForce,
				maxReactionPressure,
				maxImpingementPressure,
				maxClearanceOverWake,
				targets.isEmpty() ? 0.0 : minClearanceOverWake,
				runtimeAllowed,
				localWakeAvailable
		);
	}

	private static double meanRotorRadius(
			List<Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm> sourceTerms
	) {
		if (sourceTerms == null || sourceTerms.isEmpty()) {
			return 0.0;
		}
		double sum = 0.0;
		for (Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm term : sourceTerms) {
			sum += term.diskRadiusMeters();
		}
		return sum / sourceTerms.size();
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
