package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution {
	private static final double MINECRAFT_TICK_RATE_HERTZ = 20.0;
	private static final double SIXTY_HERTZ = 60.0;
	private static final double ONE_TWENTY_HERTZ = 120.0;
	private static final double TWO_FORTY_HERTZ = 240.0;
	private static final double ONE_KILOHERTZ = 1000.0;
	private static final double EPSILON = 1.0e-12;

	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Surface-Wake-Temporal-Resolution-Packet";
	public static final String CAVEAT =
			"Audit-only temporal resolution packet for powered-hover surface wake probes; low-rate Minecraft or controller frames are diagnostic references only and must not be treated as validated transient CFD evidence.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int TEMPORAL_TARGET_COUNT =
			Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.TRANSIT_SAMPLE_COUNT;
	public static final int TEMPORAL_METRIC_COUNT = 36;
	public static final int SUMMARY_METRIC_ROW_COUNT = 14;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ TEMPORAL_TARGET_COUNT * TEMPORAL_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution() {
	}

	public record PoweredHoverSurfaceWakeTemporalResolutionTarget(
			String presetName,
			String spinState,
			String surfaceType,
			int rotorIndex,
			double clearanceOverRadius,
			double clearanceMeters,
			double fastFarWakeTransitSeconds,
			double meanWakeTransitSeconds,
			double slowDiskPlaneTransitSeconds,
			double transitBandSeconds,
			double configuredDynamicInflowTauSeconds,
			double tauOverFastTransit,
			int minimumSamplesPerFastTransit,
			double recommendedMaxSamplePeriodSeconds,
			double recommendedMinSampleRateHertz,
			double minecraftTickRateHertz,
			double minecraftTickSamplesPerFastTransit,
			boolean minecraftTickResolvesFastTransit,
			int requiredSubstepsPerMinecraftTick,
			double sixtyHertzSamplesPerFastTransit,
			boolean sixtyHertzResolvesFastTransit,
			int requiredSubstepsPerSixtyHertzFrame,
			double oneTwentyHertzSamplesPerFastTransit,
			boolean oneTwentyHertzResolvesFastTransit,
			int requiredSubstepsPerOneTwentyHertzFrame,
			double twoFortyHertzSamplesPerFastTransit,
			boolean twoFortyHertzResolvesFastTransit,
			int requiredSubstepsPerTwoFortyHertzFrame,
			double oneKilohertzSamplesPerFastTransit,
			boolean oneKilohertzResolvesFastTransit,
			int requiredSubstepsPerOneKilohertzFrame,
			boolean transientProbeApiAvailable,
			boolean runtimeCouplingAllowed,
			boolean validationBeforeRuntimeRequired,
			String status,
			String runtimeInfo
	) {
	}

	public record PoweredHoverSurfaceWakeTemporalResolutionExtrema(
			int temporalTargetCount,
			int groundTargetCount,
			int ceilingTargetCount,
			int minecraftTickResolvedCount,
			int sixtyHertzResolvedCount,
			int oneTwentyHertzResolvedCount,
			int twoFortyHertzResolvedCount,
			int oneKilohertzResolvedCount,
			double maxRecommendedMinSampleRateHertz,
			int maxRequiredSubstepsPerMinecraftTick,
			int maxRequiredSubstepsPerOneKilohertzFrame,
			double minFastFarWakeTransitSeconds,
			double maxFastFarWakeTransitSeconds,
			int runtimeCouplingAllowedCount
	) {
	}

	public record PoweredHoverSurfaceWakeTemporalResolutionAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int temporalTargetCount,
			int temporalMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverSurfaceWakeTemporalResolutionTarget> targets,
			PoweredHoverSurfaceWakeTemporalResolutionExtrema extrema
	) {
		public PoweredHoverSurfaceWakeTemporalResolutionAudit {
			targets = List.copyOf(targets);
		}
	}

	public static PoweredHoverSurfaceWakeTemporalResolutionAudit audit() {
		List<PoweredHoverSurfaceWakeTemporalResolutionTarget> targets =
				targets(Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.audit().targets());
		return new PoweredHoverSurfaceWakeTemporalResolutionAudit(
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

	public static List<PoweredHoverSurfaceWakeTemporalResolutionTarget> targets(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget> transitTargets
	) {
		if (transitTargets == null) {
			throw new IllegalArgumentException("transit targets must not be null.");
		}
		return transitTargets.stream()
				.map(Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution::target)
				.toList();
	}

	public static PoweredHoverSurfaceWakeTemporalResolutionTarget target(
			Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget transit
	) {
		if (transit == null) {
			throw new IllegalArgumentException("transit target must not be null.");
		}
		return new PoweredHoverSurfaceWakeTemporalResolutionTarget(
				transit.presetName(),
				transit.spinState(),
				transit.surfaceType(),
				transit.rotorIndex(),
				transit.clearanceOverRadius(),
				transit.clearanceMeters(),
				transit.fastFarWakeTransitSeconds(),
				transit.meanWakeTransitSeconds(),
				transit.slowDiskPlaneTransitSeconds(),
				transit.transitBandSeconds(),
				transit.configuredDynamicInflowTauSeconds(),
				transit.tauOverFastTransit(),
				transit.minimumSamplesPerFastTransit(),
				transit.recommendedMaxSamplePeriodSeconds(),
				transit.recommendedMinSampleRateHertz(),
				MINECRAFT_TICK_RATE_HERTZ,
				samplesPerFastTransit(transit, MINECRAFT_TICK_RATE_HERTZ),
				resolvesFastTransit(transit, MINECRAFT_TICK_RATE_HERTZ),
				requiredSubsteps(MINECRAFT_TICK_RATE_HERTZ, transit.recommendedMaxSamplePeriodSeconds()),
				samplesPerFastTransit(transit, SIXTY_HERTZ),
				resolvesFastTransit(transit, SIXTY_HERTZ),
				requiredSubsteps(SIXTY_HERTZ, transit.recommendedMaxSamplePeriodSeconds()),
				samplesPerFastTransit(transit, ONE_TWENTY_HERTZ),
				resolvesFastTransit(transit, ONE_TWENTY_HERTZ),
				requiredSubsteps(ONE_TWENTY_HERTZ, transit.recommendedMaxSamplePeriodSeconds()),
				samplesPerFastTransit(transit, TWO_FORTY_HERTZ),
				resolvesFastTransit(transit, TWO_FORTY_HERTZ),
				requiredSubsteps(TWO_FORTY_HERTZ, transit.recommendedMaxSamplePeriodSeconds()),
				samplesPerFastTransit(transit, ONE_KILOHERTZ),
				resolvesFastTransit(transit, ONE_KILOHERTZ),
				requiredSubsteps(ONE_KILOHERTZ, transit.recommendedMaxSamplePeriodSeconds()),
				transit.transientProbeApiAvailable(),
				false,
				true,
				"target-only-temporal-resolution-unverified",
				"audit-only-unvalidated-surface-wake-temporal-resolution"
		);
	}

	private static double samplesPerFastTransit(
			Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget transit,
			double sampleRateHertz
	) {
		return transit.fastFarWakeTransitSeconds() * sampleRateHertz;
	}

	private static boolean resolvesFastTransit(
			Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget transit,
			double sampleRateHertz
	) {
		return samplesPerFastTransit(transit, sampleRateHertz) + EPSILON
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

	private static PoweredHoverSurfaceWakeTemporalResolutionExtrema extrema(
			List<PoweredHoverSurfaceWakeTemporalResolutionTarget> targets
	) {
		int ground = 0;
		int ceiling = 0;
		int minecraftResolved = 0;
		int sixtyResolved = 0;
		int oneTwentyResolved = 0;
		int twoFortyResolved = 0;
		int oneKilohertzResolved = 0;
		double maxSampleRate = 0.0;
		int maxMinecraftSubsteps = 0;
		int maxOneKilohertzSubsteps = 0;
		double minFastTransit = Double.POSITIVE_INFINITY;
		double maxFastTransit = 0.0;
		int runtimeAllowed = 0;
		for (PoweredHoverSurfaceWakeTemporalResolutionTarget target : targets) {
			if ("ground".equals(target.surfaceType())) {
				ground++;
			}
			if ("ceiling".equals(target.surfaceType())) {
				ceiling++;
			}
			if (target.minecraftTickResolvesFastTransit()) {
				minecraftResolved++;
			}
			if (target.sixtyHertzResolvesFastTransit()) {
				sixtyResolved++;
			}
			if (target.oneTwentyHertzResolvesFastTransit()) {
				oneTwentyResolved++;
			}
			if (target.twoFortyHertzResolvesFastTransit()) {
				twoFortyResolved++;
			}
			if (target.oneKilohertzResolvesFastTransit()) {
				oneKilohertzResolved++;
			}
			maxSampleRate = Math.max(maxSampleRate, target.recommendedMinSampleRateHertz());
			maxMinecraftSubsteps = Math.max(maxMinecraftSubsteps, target.requiredSubstepsPerMinecraftTick());
			maxOneKilohertzSubsteps = Math.max(maxOneKilohertzSubsteps,
					target.requiredSubstepsPerOneKilohertzFrame());
			minFastTransit = Math.min(minFastTransit, target.fastFarWakeTransitSeconds());
			maxFastTransit = Math.max(maxFastTransit, target.fastFarWakeTransitSeconds());
			if (target.runtimeCouplingAllowed()) {
				runtimeAllowed++;
			}
		}
		return new PoweredHoverSurfaceWakeTemporalResolutionExtrema(
				targets.size(),
				ground,
				ceiling,
				minecraftResolved,
				sixtyResolved,
				oneTwentyResolved,
				twoFortyResolved,
				oneKilohertzResolved,
				maxSampleRate,
				maxMinecraftSubsteps,
				maxOneKilohertzSubsteps,
				targets.isEmpty() ? 0.0 : minFastTransit,
				maxFastTransit,
				runtimeAllowed
		);
	}
}
