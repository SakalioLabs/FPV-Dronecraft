package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2PoweredCruiseSkewWakeTransit {
	private static final int MINIMUM_SAMPLES_PER_FAST_TRANSIT = 4;
	private static final double EPSILON = 1.0e-12;

	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Skew-Wake-Transit-Packet";
	public static final String CAVEAT =
			"Audit-only powered-cruise skew-wake transit packet brackets rotor-resolved probe arrival time along axial, freestream-swept, and resultant wake paths; validate live A4MC transient skew-wake probes before using any forward-flight wake timing.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int ROTOR_SAMPLE_COUNT = 4;
	public static final int AXIAL_SAMPLE_PLANE_COUNT = 4;
	public static final int SWEEP_SAMPLE_COLUMN_COUNT = 3;
	public static final int TRANSIT_SAMPLE_COUNT =
			PRESET_SAMPLE_COUNT * ROTOR_SAMPLE_COUNT * AXIAL_SAMPLE_PLANE_COUNT * SWEEP_SAMPLE_COLUMN_COUNT;
	public static final int TRANSIT_METRIC_COUNT = 38;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ TRANSIT_SAMPLE_COUNT * TRANSIT_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseSkewWakeTransit() {
	}

	public record PoweredCruiseSkewWakeTransitTarget(
			String presetName,
			String spinState,
			int rotorIndex,
			int axialPlaneIndex,
			int sweepColumnIndex,
			double axialPlaneFraction,
			double axialDistanceMeters,
			double freestreamSweepDistanceMeters,
			double lateralOffsetMeters,
			double centerlineDistanceMeters,
			double distanceFromRotorMeters,
			double expectedAxialWakeVelocityMetersPerSecond,
			double expectedFreestreamVelocityMetersPerSecond,
			double expectedResultantWakeVelocityMetersPerSecond,
			double axialOnlyTransitSeconds,
			double freestreamSweepTransitSeconds,
			double centerlineResultantTransitSeconds,
			double lateralAdjustedTransitSeconds,
			double transitBandSeconds,
			double axialTransitMilliseconds,
			double centerlineTransitMilliseconds,
			double lateralTransitMilliseconds,
			double configuredDynamicInflowTauSeconds,
			double tauOverCenterlineTransit,
			double tauOverLateralTransit,
			double centerlineTransitOverConfiguredTau,
			double expectedAxialWakePressurePascals,
			double expectedResultantDynamicPressurePascals,
			double perRotorAxialMomentumFluxNewtons,
			int minimumSamplesPerFastTransit,
			double recommendedMaxSamplePeriodSeconds,
			double recommendedMinSampleRateHertz,
			boolean skewWakeProbeApiAvailable,
			boolean transientProbeApiAvailable,
			boolean runtimeCouplingAllowed,
			boolean validationBeforeRuntimeRequired,
			String status,
			String runtimeInfo
	) {
	}

	public record PoweredCruiseSkewWakeTransitExtrema(
			int transitTargetCount,
			int sourceTermCount,
			int axialPlaneCount,
			int sweepColumnCount,
			double maxCenterlineTransitSeconds,
			double maxLateralAdjustedTransitSeconds,
			double minCenterlineTransitSeconds,
			double maxConfiguredDynamicInflowTauSeconds,
			double maxTauOverCenterlineTransit,
			double maxRecommendedMinSampleRateHertz,
			int transientProbeApiAvailableCount,
			int runtimeCouplingAllowedCount
	) {
	}

	public record PoweredCruiseSkewWakeTransitAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int rotorSampleCount,
			int axialSamplePlaneCount,
			int sweepSampleColumnCount,
			int transitSampleCount,
			int transitMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredCruiseSkewWakeTransitTarget> targets,
			PoweredCruiseSkewWakeTransitExtrema extrema
	) {
		public PoweredCruiseSkewWakeTransitAudit {
			targets = List.copyOf(targets);
		}
	}

	public static PoweredCruiseSkewWakeTransitAudit audit() {
		List<PoweredCruiseSkewWakeTransitTarget> targets = new ArrayList<>(TRANSIT_SAMPLE_COUNT);
		targets.addAll(targets("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72));
		targets.addAll(targets("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64));
		targets.addAll(targets("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48));
		targets.addAll(targets("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80));
		return new PoweredCruiseSkewWakeTransitAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				ROTOR_SAMPLE_COUNT,
				AXIAL_SAMPLE_PLANE_COUNT,
				SWEEP_SAMPLE_COLUMN_COUNT,
				TRANSIT_SAMPLE_COUNT,
				TRANSIT_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				targets,
				extrema(targets)
		);
	}

	public static List<PoweredCruiseSkewWakeTransitTarget> targets(
			String presetName,
			DroneConfig config,
			Vec3 inletVelocity,
			int steps
	) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		List<Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe> probes =
				Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.probes(presetName, config, inletVelocity, steps);
		List<PoweredCruiseSkewWakeTransitTarget> targets = new ArrayList<>(probes.size());
		for (Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe probe : probes) {
			RotorSpec rotor = config.rotors().get(Math.min(probe.rotorIndex(), config.rotors().size() - 1));
			targets.add(target(probe, rotor));
		}
		return List.copyOf(targets);
	}

	public static PoweredCruiseSkewWakeTransitTarget target(
			Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe probe,
			RotorSpec rotor
	) {
		if (probe == null || rotor == null) {
			throw new IllegalArgumentException("probe and rotor are required.");
		}
		if (!"cruise".equals(probe.spinState())) {
			throw new IllegalArgumentException("skew-wake transit requires a cruise probe row.");
		}
		double axialTransit = ratio(probe.axialDistanceMeters(), probe.expectedAxialWakeVelocityMetersPerSecond());
		double freestreamTransit = ratio(
				Math.abs(probe.freestreamSweepDistanceMeters()),
				probe.expectedFreestreamVelocityMetersPerSecond()
		);
		double centerlineTransit = ratio(
				probe.centerlineDistanceMeters(),
				probe.expectedResultantWakeVelocityMetersPerSecond()
		);
		double lateralTransit = ratio(
				probe.distanceFromRotorMeters(),
				probe.expectedResultantWakeVelocityMetersPerSecond()
		);
		double tau = rotor.inducedInflowTimeConstantSeconds();
		return new PoweredCruiseSkewWakeTransitTarget(
				probe.presetName(),
				probe.spinState(),
				probe.rotorIndex(),
				probe.axialPlaneIndex(),
				probe.sweepColumnIndex(),
				probe.axialPlaneFraction(),
				probe.axialDistanceMeters(),
				probe.freestreamSweepDistanceMeters(),
				probe.lateralOffsetMeters(),
				probe.centerlineDistanceMeters(),
				probe.distanceFromRotorMeters(),
				probe.expectedAxialWakeVelocityMetersPerSecond(),
				probe.expectedFreestreamVelocityMetersPerSecond(),
				probe.expectedResultantWakeVelocityMetersPerSecond(),
				axialTransit,
				freestreamTransit,
				centerlineTransit,
				lateralTransit,
				Math.max(0.0, lateralTransit - centerlineTransit),
				axialTransit * 1000.0,
				centerlineTransit * 1000.0,
				lateralTransit * 1000.0,
				tau,
				ratio(tau, centerlineTransit),
				ratio(tau, lateralTransit),
				ratio(centerlineTransit, tau),
				probe.expectedAxialWakePressurePascals(),
				probe.expectedResultantDynamicPressurePascals(),
				probe.perRotorAxialMomentumFluxNewtons(),
				MINIMUM_SAMPLES_PER_FAST_TRANSIT,
				ratio(centerlineTransit, MINIMUM_SAMPLES_PER_FAST_TRANSIT),
				ratio(MINIMUM_SAMPLES_PER_FAST_TRANSIT, centerlineTransit),
				probe.skewWakeProbeApiAvailable(),
				false,
				false,
				true,
				"target-only-transient-cruise-skew-wake-probe-unavailable",
				"audit-only-unvalidated-cruise-skew-wake-transit"
		);
	}

	private static PoweredCruiseSkewWakeTransitExtrema extrema(
			List<PoweredCruiseSkewWakeTransitTarget> targets
	) {
		double maxCenterlineTransit = 0.0;
		double maxLateralTransit = 0.0;
		double minCenterlineTransit = Double.POSITIVE_INFINITY;
		double maxTau = 0.0;
		double maxTauOverCenterline = 0.0;
		double maxSampleRate = 0.0;
		int transientApiAvailable = 0;
		int runtimeAllowed = 0;
		for (PoweredCruiseSkewWakeTransitTarget target : targets) {
			maxCenterlineTransit = Math.max(maxCenterlineTransit, target.centerlineResultantTransitSeconds());
			maxLateralTransit = Math.max(maxLateralTransit, target.lateralAdjustedTransitSeconds());
			minCenterlineTransit = Math.min(minCenterlineTransit, target.centerlineResultantTransitSeconds());
			maxTau = Math.max(maxTau, target.configuredDynamicInflowTauSeconds());
			maxTauOverCenterline = Math.max(maxTauOverCenterline, target.tauOverCenterlineTransit());
			maxSampleRate = Math.max(maxSampleRate, target.recommendedMinSampleRateHertz());
			if (target.transientProbeApiAvailable()) {
				transientApiAvailable++;
			}
			if (target.runtimeCouplingAllowed()) {
				runtimeAllowed++;
			}
		}
		return new PoweredCruiseSkewWakeTransitExtrema(
				targets.size(),
				PRESET_SAMPLE_COUNT * ROTOR_SAMPLE_COUNT,
				AXIAL_SAMPLE_PLANE_COUNT,
				SWEEP_SAMPLE_COLUMN_COUNT,
				maxCenterlineTransit,
				maxLateralTransit,
				targets.isEmpty() ? 0.0 : minCenterlineTransit,
				maxTau,
				maxTauOverCenterline,
				maxSampleRate,
				transientApiAvailable,
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
