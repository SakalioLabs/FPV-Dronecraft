package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution {
	private static final double MINECRAFT_TICK_RATE_HERTZ = 20.0;
	private static final double SIXTY_HERTZ = 60.0;
	private static final double ONE_TWENTY_HERTZ = 120.0;
	private static final double TWO_FORTY_HERTZ = 240.0;
	private static final double ONE_KILOHERTZ = 1000.0;
	private static final double EPSILON = 1.0e-12;

	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Skew-Wake-Temporal-Resolution-Packet";
	public static final String CAVEAT =
			"Audit-only temporal resolution packet for powered-cruise skew-wake probes; low-rate Minecraft or controller frames are diagnostic references only and must not be treated as validated transient CFD evidence.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int TEMPORAL_TARGET_COUNT =
			Aerodynamics4McL2PoweredCruiseSkewWakeTransit.TRANSIT_SAMPLE_COUNT;
	public static final int TEMPORAL_METRIC_COUNT = 38;
	public static final int SUMMARY_METRIC_ROW_COUNT = 14;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ TEMPORAL_TARGET_COUNT * TEMPORAL_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution() {
	}

	public record PoweredCruiseSkewWakeTemporalResolutionTarget(
			String presetName,
			String spinState,
			int rotorIndex,
			int axialPlaneIndex,
			int sweepColumnIndex,
			double axialPlaneFraction,
			double centerlineResultantTransitSeconds,
			double lateralAdjustedTransitSeconds,
			double transitBandSeconds,
			double configuredDynamicInflowTauSeconds,
			double tauOverCenterlineTransit,
			double tauOverLateralTransit,
			int minimumSamplesPerFastTransit,
			double recommendedMaxSamplePeriodSeconds,
			double recommendedMinSampleRateHertz,
			double minecraftTickRateHertz,
			double minecraftTickSamplesPerCenterlineTransit,
			boolean minecraftTickResolvesCenterlineTransit,
			int requiredSubstepsPerMinecraftTick,
			double sixtyHertzSamplesPerCenterlineTransit,
			boolean sixtyHertzResolvesCenterlineTransit,
			int requiredSubstepsPerSixtyHertzFrame,
			double oneTwentyHertzSamplesPerCenterlineTransit,
			boolean oneTwentyHertzResolvesCenterlineTransit,
			int requiredSubstepsPerOneTwentyHertzFrame,
			double twoFortyHertzSamplesPerCenterlineTransit,
			boolean twoFortyHertzResolvesCenterlineTransit,
			int requiredSubstepsPerTwoFortyHertzFrame,
			double oneKilohertzSamplesPerCenterlineTransit,
			boolean oneKilohertzResolvesCenterlineTransit,
			int requiredSubstepsPerOneKilohertzFrame,
			double oneKilohertzSamplesPerLateralAdjustedTransit,
			boolean oneKilohertzResolvesLateralAdjustedTransit,
			boolean transientProbeApiAvailable,
			boolean runtimeCouplingAllowed,
			boolean validationBeforeRuntimeRequired,
			String status,
			String runtimeInfo
	) {
	}

	public record PoweredCruiseSkewWakeTemporalResolutionExtrema(
			int temporalTargetCount,
			int sourceTermCount,
			int axialPlaneCount,
			int sweepColumnCount,
			int minecraftTickResolvedCount,
			int sixtyHertzResolvedCount,
			int oneTwentyHertzResolvedCount,
			int twoFortyHertzResolvedCount,
			int oneKilohertzResolvedCount,
			double maxRecommendedMinSampleRateHertz,
			int maxRequiredSubstepsPerMinecraftTick,
			int maxRequiredSubstepsPerOneKilohertzFrame,
			double minCenterlineResultantTransitSeconds,
			int runtimeCouplingAllowedCount
	) {
	}

	public record PoweredCruiseSkewWakeTemporalResolutionAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int temporalTargetCount,
			int temporalMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredCruiseSkewWakeTemporalResolutionTarget> targets,
			PoweredCruiseSkewWakeTemporalResolutionExtrema extrema
	) {
		public PoweredCruiseSkewWakeTemporalResolutionAudit {
			targets = List.copyOf(targets);
		}
	}

	public static PoweredCruiseSkewWakeTemporalResolutionAudit audit() {
		List<PoweredCruiseSkewWakeTemporalResolutionTarget> targets =
				targets(Aerodynamics4McL2PoweredCruiseSkewWakeTransit.audit().targets());
		return new PoweredCruiseSkewWakeTemporalResolutionAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				TEMPORAL_TARGET_COUNT,
				TEMPORAL_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				targets,
				extrema(targets)
		);
	}

	public static List<PoweredCruiseSkewWakeTemporalResolutionTarget> targets(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget> transitTargets
	) {
		if (transitTargets == null) {
			throw new IllegalArgumentException("transit targets must not be null.");
		}
		return transitTargets.stream()
				.map(Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution::target)
				.toList();
	}

	public static PoweredCruiseSkewWakeTemporalResolutionTarget target(
			Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget transit
	) {
		if (transit == null) {
			throw new IllegalArgumentException("transit target must not be null.");
		}
		return new PoweredCruiseSkewWakeTemporalResolutionTarget(
				transit.presetName(),
				transit.spinState(),
				transit.rotorIndex(),
				transit.axialPlaneIndex(),
				transit.sweepColumnIndex(),
				transit.axialPlaneFraction(),
				transit.centerlineResultantTransitSeconds(),
				transit.lateralAdjustedTransitSeconds(),
				transit.transitBandSeconds(),
				transit.configuredDynamicInflowTauSeconds(),
				transit.tauOverCenterlineTransit(),
				transit.tauOverLateralTransit(),
				transit.minimumSamplesPerFastTransit(),
				transit.recommendedMaxSamplePeriodSeconds(),
				transit.recommendedMinSampleRateHertz(),
				MINECRAFT_TICK_RATE_HERTZ,
				samplesPerCenterlineTransit(transit, MINECRAFT_TICK_RATE_HERTZ),
				resolvesCenterlineTransit(transit, MINECRAFT_TICK_RATE_HERTZ),
				requiredSubsteps(MINECRAFT_TICK_RATE_HERTZ, transit.recommendedMaxSamplePeriodSeconds()),
				samplesPerCenterlineTransit(transit, SIXTY_HERTZ),
				resolvesCenterlineTransit(transit, SIXTY_HERTZ),
				requiredSubsteps(SIXTY_HERTZ, transit.recommendedMaxSamplePeriodSeconds()),
				samplesPerCenterlineTransit(transit, ONE_TWENTY_HERTZ),
				resolvesCenterlineTransit(transit, ONE_TWENTY_HERTZ),
				requiredSubsteps(ONE_TWENTY_HERTZ, transit.recommendedMaxSamplePeriodSeconds()),
				samplesPerCenterlineTransit(transit, TWO_FORTY_HERTZ),
				resolvesCenterlineTransit(transit, TWO_FORTY_HERTZ),
				requiredSubsteps(TWO_FORTY_HERTZ, transit.recommendedMaxSamplePeriodSeconds()),
				samplesPerCenterlineTransit(transit, ONE_KILOHERTZ),
				resolvesCenterlineTransit(transit, ONE_KILOHERTZ),
				requiredSubsteps(ONE_KILOHERTZ, transit.recommendedMaxSamplePeriodSeconds()),
				samplesPerLateralAdjustedTransit(transit, ONE_KILOHERTZ),
				resolvesLateralAdjustedTransit(transit, ONE_KILOHERTZ),
				transit.transientProbeApiAvailable(),
				false,
				true,
				"target-only-cruise-skew-wake-temporal-resolution-unverified",
				"audit-only-unvalidated-cruise-skew-wake-temporal-resolution"
		);
	}

	private static double samplesPerCenterlineTransit(
			Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget transit,
			double sampleRateHertz
	) {
		return transit.centerlineResultantTransitSeconds() * sampleRateHertz;
	}

	private static boolean resolvesCenterlineTransit(
			Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget transit,
			double sampleRateHertz
	) {
		return samplesPerCenterlineTransit(transit, sampleRateHertz) + EPSILON
				>= transit.minimumSamplesPerFastTransit();
	}

	private static double samplesPerLateralAdjustedTransit(
			Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget transit,
			double sampleRateHertz
	) {
		return transit.lateralAdjustedTransitSeconds() * sampleRateHertz;
	}

	private static boolean resolvesLateralAdjustedTransit(
			Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget transit,
			double sampleRateHertz
	) {
		return samplesPerLateralAdjustedTransit(transit, sampleRateHertz) + EPSILON
				>= transit.minimumSamplesPerFastTransit();
	}

	private static int requiredSubsteps(double frameRateHertz, double maximumSamplePeriodSeconds) {
		if (!Double.isFinite(frameRateHertz) || frameRateHertz <= EPSILON
				|| !Double.isFinite(maximumSamplePeriodSeconds) || maximumSamplePeriodSeconds <= EPSILON) {
			return 0;
		}
		double framePeriodSeconds = 1.0 / frameRateHertz;
		return Math.max(1, (int) Math.ceil(framePeriodSeconds / maximumSamplePeriodSeconds));
	}

	private static PoweredCruiseSkewWakeTemporalResolutionExtrema extrema(
			List<PoweredCruiseSkewWakeTemporalResolutionTarget> targets
	) {
		int minecraftResolved = 0;
		int sixtyResolved = 0;
		int oneTwentyResolved = 0;
		int twoFortyResolved = 0;
		int oneKilohertzResolved = 0;
		double maxSampleRate = 0.0;
		int maxMinecraftSubsteps = 0;
		int maxOneKilohertzSubsteps = 0;
		double minCenterlineTransit = Double.POSITIVE_INFINITY;
		int runtimeAllowed = 0;
		for (PoweredCruiseSkewWakeTemporalResolutionTarget target : targets) {
			if (target.minecraftTickResolvesCenterlineTransit()) {
				minecraftResolved++;
			}
			if (target.sixtyHertzResolvesCenterlineTransit()) {
				sixtyResolved++;
			}
			if (target.oneTwentyHertzResolvesCenterlineTransit()) {
				oneTwentyResolved++;
			}
			if (target.twoFortyHertzResolvesCenterlineTransit()) {
				twoFortyResolved++;
			}
			if (target.oneKilohertzResolvesCenterlineTransit()) {
				oneKilohertzResolved++;
			}
			maxSampleRate = Math.max(maxSampleRate, target.recommendedMinSampleRateHertz());
			maxMinecraftSubsteps = Math.max(maxMinecraftSubsteps, target.requiredSubstepsPerMinecraftTick());
			maxOneKilohertzSubsteps = Math.max(maxOneKilohertzSubsteps,
					target.requiredSubstepsPerOneKilohertzFrame());
			minCenterlineTransit = Math.min(minCenterlineTransit, target.centerlineResultantTransitSeconds());
			if (target.runtimeCouplingAllowed()) {
				runtimeAllowed++;
			}
		}
		return new PoweredCruiseSkewWakeTemporalResolutionExtrema(
				targets.size(),
				Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PRESET_SAMPLE_COUNT
						* Aerodynamics4McL2PoweredCruiseSkewWakeTransit.ROTOR_SAMPLE_COUNT,
				Aerodynamics4McL2PoweredCruiseSkewWakeTransit.AXIAL_SAMPLE_PLANE_COUNT,
				Aerodynamics4McL2PoweredCruiseSkewWakeTransit.SWEEP_SAMPLE_COLUMN_COUNT,
				minecraftResolved,
				sixtyResolved,
				oneTwentyResolved,
				twoFortyResolved,
				oneKilohertzResolved,
				maxSampleRate,
				maxMinecraftSubsteps,
				maxOneKilohertzSubsteps,
				targets.isEmpty() ? 0.0 : minCenterlineTransit,
				runtimeAllowed
		);
	}
}
