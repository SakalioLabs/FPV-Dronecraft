package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2PoweredHoverSurfaceWakeTransit {
	private static final int MINIMUM_SAMPLES_PER_FAST_TRANSIT = 4;
	private static final double EPSILON = 1.0e-12;

	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Surface-Wake-Transit-Packet";
	public static final String CAVEAT =
			"Audit-only powered-hover surface wake transit packet brackets per-rotor probe arrival time between disk-plane induced velocity and far-wake velocity; validate live A4MC transient probes before using any surface-coupled downwash timing.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int ROTOR_SAMPLE_COUNT = 4;
	public static final int CLEARANCE_SAMPLE_COUNT = 4;
	public static final int SURFACE_SAMPLE_COUNT = 2;
	public static final int TRANSIT_SAMPLE_COUNT =
			PRESET_SAMPLE_COUNT * ROTOR_SAMPLE_COUNT * CLEARANCE_SAMPLE_COUNT * SURFACE_SAMPLE_COUNT;
	public static final int TRANSIT_METRIC_COUNT = 33;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ TRANSIT_SAMPLE_COUNT * TRANSIT_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverSurfaceWakeTransit() {
	}

	public record PoweredHoverSurfaceWakeTransitTarget(
			String presetName,
			String spinState,
			String surfaceType,
			int rotorIndex,
			double clearanceOverRadius,
			double clearanceMeters,
			double clearanceOverFarWakeRadius,
			double rotorDiskRadiusMeters,
			double idealInducedVelocityMetersPerSecond,
			double farWakeVelocityMetersPerSecond,
			double meanWakeVelocityMetersPerSecond,
			double slowDiskPlaneTransitSeconds,
			double fastFarWakeTransitSeconds,
			double meanWakeTransitSeconds,
			double transitBandSeconds,
			double slowDiskPlaneTransitMilliseconds,
			double fastFarWakeTransitMilliseconds,
			double configuredDynamicInflowTauSeconds,
			double tauOverFastTransit,
			double tauOverMeanTransit,
			double fastTransitOverConfiguredTau,
			double perRotorImpingementPressurePascals,
			double surfaceCurveMultiplier,
			double perRotorSurfaceCushionForceNewtons,
			int minimumSamplesPerFastTransit,
			double recommendedMaxSamplePeriodSeconds,
			double recommendedMinSampleRateHertz,
			boolean localProbeApiAvailable,
			boolean transientProbeApiAvailable,
			boolean runtimeCouplingAllowed,
			boolean validationBeforeRuntimeRequired,
			String status,
			String runtimeInfo
	) {
	}

	public record PoweredHoverSurfaceWakeTransitExtrema(
			int transitTargetCount,
			int groundTargetCount,
			int ceilingTargetCount,
			double maxSlowDiskPlaneTransitSeconds,
			double maxFastFarWakeTransitSeconds,
			double minFastFarWakeTransitSeconds,
			double maxConfiguredDynamicInflowTauSeconds,
			double maxTauOverFastTransit,
			double maxRecommendedMinSampleRateHertz,
			double maxTransitBandSeconds,
			int localProbeApiAvailableCount,
			int runtimeCouplingAllowedCount
	) {
	}

	public record PoweredHoverSurfaceWakeTransitAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int rotorSampleCount,
			int clearanceSampleCount,
			int surfaceSampleCount,
			int transitSampleCount,
			int transitMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverSurfaceWakeTransitTarget> targets,
			PoweredHoverSurfaceWakeTransitExtrema extrema
	) {
		public PoweredHoverSurfaceWakeTransitAudit {
			targets = List.copyOf(targets);
		}
	}

	public static PoweredHoverSurfaceWakeTransitAudit audit() {
		List<PoweredHoverSurfaceWakeTransitTarget> targets = new ArrayList<>(TRANSIT_SAMPLE_COUNT);
		targets.addAll(targets("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72));
		targets.addAll(targets("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64));
		targets.addAll(targets("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48));
		targets.addAll(targets("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80));
		return new PoweredHoverSurfaceWakeTransitAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				ROTOR_SAMPLE_COUNT,
				CLEARANCE_SAMPLE_COUNT,
				SURFACE_SAMPLE_COUNT,
				TRANSIT_SAMPLE_COUNT,
				TRANSIT_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				targets,
				extrema(targets)
		);
	}

	public static List<PoweredHoverSurfaceWakeTransitTarget> targets(
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
		List<Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe> probes =
				Aerodynamics4McL2PoweredHoverSurfaceProbeMap.probes(sourceMap);
		List<PoweredHoverSurfaceWakeTransitTarget> targets = new ArrayList<>(probes.size());
		for (Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe probe : probes) {
			RotorSpec rotor = config.rotors().get(Math.min(probe.rotorIndex(), config.rotors().size() - 1));
			targets.add(target(sourceMap, probe, rotor));
		}
		return List.copyOf(targets);
	}

	public static PoweredHoverSurfaceWakeTransitTarget target(
			Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap,
			Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe probe,
			RotorSpec rotor
	) {
		if (sourceMap == null || probe == null || rotor == null) {
			throw new IllegalArgumentException("source map, probe, and rotor are required.");
		}
		if (!"hover".equals(sourceMap.spinState()) || !"hover".equals(probe.spinState())) {
			throw new IllegalArgumentException("surface wake transit requires hover inputs.");
		}
		double idealVelocity = sourceMap.idealInducedVelocityMetersPerSecond();
		double farVelocity = sourceMap.farWakeVelocityMetersPerSecond();
		double meanVelocity = 0.5 * (idealVelocity + farVelocity);
		double slowTransit = ratio(probe.clearanceMeters(), idealVelocity);
		double fastTransit = ratio(probe.clearanceMeters(), farVelocity);
		double meanTransit = ratio(probe.clearanceMeters(), meanVelocity);
		double tau = rotor.inducedInflowTimeConstantSeconds();
		return new PoweredHoverSurfaceWakeTransitTarget(
				probe.presetName(),
				probe.spinState(),
				probe.surfaceType(),
				probe.rotorIndex(),
				probe.clearanceOverRadius(),
				probe.clearanceMeters(),
				ratio(probe.clearanceMeters(), probe.perRotorFarWakeEquivalentRadiusMeters()),
				probe.rotorDiskRadiusMeters(),
				idealVelocity,
				farVelocity,
				meanVelocity,
				slowTransit,
				fastTransit,
				meanTransit,
				Math.max(0.0, slowTransit - fastTransit),
				slowTransit * 1000.0,
				fastTransit * 1000.0,
				tau,
				ratio(tau, fastTransit),
				ratio(tau, meanTransit),
				ratio(fastTransit, tau),
				probe.perRotorImpingementPressurePascals(),
				probe.surfaceCurveMultiplier(),
				probe.perRotorSurfaceCushionForceNewtons(),
				MINIMUM_SAMPLES_PER_FAST_TRANSIT,
				ratio(fastTransit, MINIMUM_SAMPLES_PER_FAST_TRANSIT),
				ratio(MINIMUM_SAMPLES_PER_FAST_TRANSIT, fastTransit),
				probe.localProbeApiAvailable(),
				false,
				false,
				true,
				"target-only-transient-wake-probe-unavailable",
				"audit-only-unvalidated-surface-wake-transit"
		);
	}

	private static PoweredHoverSurfaceWakeTransitExtrema extrema(
			List<PoweredHoverSurfaceWakeTransitTarget> targets
	) {
		int ground = 0;
		int ceiling = 0;
		double maxSlowTransit = 0.0;
		double maxFastTransit = 0.0;
		double minFastTransit = Double.POSITIVE_INFINITY;
		double maxTau = 0.0;
		double maxTauOverFast = 0.0;
		double maxSampleRate = 0.0;
		double maxBand = 0.0;
		int localApiAvailable = 0;
		int runtimeAllowed = 0;
		for (PoweredHoverSurfaceWakeTransitTarget target : targets) {
			if ("ground".equals(target.surfaceType())) {
				ground++;
			}
			if ("ceiling".equals(target.surfaceType())) {
				ceiling++;
			}
			maxSlowTransit = Math.max(maxSlowTransit, target.slowDiskPlaneTransitSeconds());
			maxFastTransit = Math.max(maxFastTransit, target.fastFarWakeTransitSeconds());
			minFastTransit = Math.min(minFastTransit, target.fastFarWakeTransitSeconds());
			maxTau = Math.max(maxTau, target.configuredDynamicInflowTauSeconds());
			maxTauOverFast = Math.max(maxTauOverFast, target.tauOverFastTransit());
			maxSampleRate = Math.max(maxSampleRate, target.recommendedMinSampleRateHertz());
			maxBand = Math.max(maxBand, target.transitBandSeconds());
			if (target.localProbeApiAvailable()) {
				localApiAvailable++;
			}
			if (target.runtimeCouplingAllowed()) {
				runtimeAllowed++;
			}
		}
		return new PoweredHoverSurfaceWakeTransitExtrema(
				targets.size(),
				ground,
				ceiling,
				maxSlowTransit,
				maxFastTransit,
				targets.isEmpty() ? 0.0 : minFastTransit,
				maxTau,
				maxTauOverFast,
				maxSampleRate,
				maxBand,
				localApiAvailable,
				runtimeAllowed
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
