package com.tenicana.dronecraft.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate {
	private static final double MINECRAFT_TICK_PERIOD_SECONDS = 0.05;
	private static final double EPSILON = 1.0e-12;
	private static final String[] EXPECTED_PRESETS = { "racingQuad", "apDrone", "cinewhoop", "heavyLift" };
	private static final String[] EXPECTED_SURFACES = { "ground", "ceiling" };
	private static final double[] EXPECTED_CLEARANCES_OVER_RADIUS = { 0.5, 1.0, 2.0, 4.0 };
	private static final int EXPECTED_ROTOR_COUNT = 4;

	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Surface-Wake-Readiness-Gate-Packet";
	public static final String CAVEAT =
			"Gate remains closed until powered-source support, transient surface-wake probe support, all rotor-resolved targets, and sufficient temporal substepping are present for live A4MC validation; runtime coupling remains closed.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 5;
	public static final int SCENARIO_METRIC_COUNT = 21;
	public static final int SUMMARY_METRIC_ROW_COUNT = 10;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverSurfaceWakeReadinessGate() {
	}

	public record PoweredHoverSurfaceWakeReadinessSummary(
			boolean poweredSourceApiAvailable,
			boolean transientProbeApiAvailable,
			double appliedMaxSamplePeriodSeconds,
			int expectedTargetCount,
			int observedTargetCount,
			int groundTargetCount,
			int ceilingTargetCount,
			int transientProbeApiAvailableTargetCount,
			int runtimeCouplingAllowedTargetCount,
			int invalidTargetCount,
			int missingTargetCount,
			int unexpectedTargetCount,
			double requiredMaxSamplePeriodSeconds,
			double maxRequiredSampleRateHertz,
			boolean allExpectedTargetsPresent,
			boolean allTargetProbeApisAvailable,
			boolean temporalResolutionSufficient,
			boolean poweredSourceAndProbeApisAvailable,
			boolean runtimeCouplingBlockedForAllTargets,
			boolean liveValidationRunAllowed,
			String status
	) {
	}

	public record PoweredHoverSurfaceWakeReadinessScenario(
			String scenarioName,
			PoweredHoverSurfaceWakeReadinessSummary summary
	) {
	}

	public record PoweredHoverSurfaceWakeReadinessExtrema(
			int scenarioCount,
			int allowedScenarioCount,
			int blockedScenarioCount,
			int maxExpectedTargetCount,
			int maxMissingTargetCount,
			int maxInvalidTargetCount,
			int maxUnexpectedTargetCount,
			double minRequiredMaxSamplePeriodSeconds,
			double maxAppliedMaxSamplePeriodSeconds,
			double maxRequiredSampleRateHertz
	) {
	}

	public record PoweredHoverSurfaceWakeReadinessAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverSurfaceWakeReadinessScenario> scenarios,
			PoweredHoverSurfaceWakeReadinessExtrema extrema
	) {
		public PoweredHoverSurfaceWakeReadinessAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredHoverSurfaceWakeReadinessAudit audit() {
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget> current =
				Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.audit().targets();
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget> probeReady =
				current.stream()
						.map(target -> copy(target, true, false))
						.toList();
		double requiredMaxSamplePeriod = requiredMaxSamplePeriodSeconds(probeReady);
		List<PoweredHoverSurfaceWakeReadinessScenario> scenarios = List.of(
				scenario("current_api_unavailable_tick_rate_only", false, false,
						MINECRAFT_TICK_PERIOD_SECONDS, current),
				scenario("powered_and_probe_api_available_substepped", true, true,
						requiredMaxSamplePeriod, probeReady),
				scenario("api_available_one_target_missing", true, true,
						requiredMaxSamplePeriod, probeReady.subList(0, probeReady.size() - 1)),
				scenario("api_available_tick_rate_underresolved", true, true,
						MINECRAFT_TICK_PERIOD_SECONDS, probeReady),
				scenario("powered_api_available_probe_target_unavailable", true, true,
						requiredMaxSamplePeriod, current)
		);
		return new PoweredHoverSurfaceWakeReadinessAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SCENARIO_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				scenarios,
				extrema(scenarios)
		);
	}

	public static PoweredHoverSurfaceWakeReadinessScenario scenario(
			String scenarioName,
			boolean poweredSourceApiAvailable,
			boolean transientProbeApiAvailable,
			double appliedMaxSamplePeriodSeconds,
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget> targets
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		return new PoweredHoverSurfaceWakeReadinessScenario(
				scenarioName,
				gate(poweredSourceApiAvailable, transientProbeApiAvailable, appliedMaxSamplePeriodSeconds, targets)
		);
	}

	public static PoweredHoverSurfaceWakeReadinessSummary gate(
			boolean poweredSourceApiAvailable,
			boolean transientProbeApiAvailable,
			double appliedMaxSamplePeriodSeconds,
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget> targets
	) {
		if (targets == null) {
			throw new IllegalArgumentException("targets must not be null.");
		}
		if (!Double.isFinite(appliedMaxSamplePeriodSeconds) || appliedMaxSamplePeriodSeconds <= 0.0) {
			throw new IllegalArgumentException("appliedMaxSamplePeriodSeconds must be positive and finite.");
		}
		Set<String> expected = expectedTargetNames();
		Set<String> observed = new HashSet<>();
		int ground = 0;
		int ceiling = 0;
		int transientTargetApiAvailable = 0;
		int runtimeAllowed = 0;
		int invalid = 0;
		int unexpected = 0;
		double requiredMaxSamplePeriod = Double.POSITIVE_INFINITY;
		double maxRequiredSampleRate = 0.0;
		for (Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget target
				: targets) {
			if (target == null || target.presetName() == null || target.presetName().isBlank()
					|| target.surfaceType() == null || target.surfaceType().isBlank()) {
				throw new IllegalArgumentException("targets must include stable preset and surface names.");
			}
			String name = targetName(target);
			if (!observed.add(name)) {
				throw new IllegalArgumentException("duplicate surface wake target: " + name);
			}
			if (!expected.contains(name)) {
				unexpected++;
			}
			if ("ground".equals(target.surfaceType())) {
				ground++;
			} else if ("ceiling".equals(target.surfaceType())) {
				ceiling++;
			} else {
				invalid++;
			}
			if (target.transientProbeApiAvailable()) {
				transientTargetApiAvailable++;
			}
			if (target.runtimeCouplingAllowed()) {
				runtimeAllowed++;
			}
			if (!validTarget(target)) {
				invalid++;
			}
			requiredMaxSamplePeriod = Math.min(requiredMaxSamplePeriod, target.recommendedMaxSamplePeriodSeconds());
			maxRequiredSampleRate = Math.max(maxRequiredSampleRate, target.recommendedMinSampleRateHertz());
		}
		int missing = 0;
		for (String name : expected) {
			if (!observed.contains(name)) {
				missing++;
			}
		}
		boolean allPresent = missing == 0 && unexpected == 0 && observed.size() == expected.size();
		boolean allTargetProbeApisAvailable = transientTargetApiAvailable == expected.size();
		boolean temporalResolutionSufficient = appliedMaxSamplePeriodSeconds <= requiredMaxSamplePeriod + EPSILON;
		boolean poweredAndProbeApisAvailable = poweredSourceApiAvailable
				&& transientProbeApiAvailable
				&& allTargetProbeApisAvailable;
		boolean runtimeBlocked = runtimeAllowed == 0;
		boolean allowed = poweredAndProbeApisAvailable
				&& allPresent
				&& invalid == 0
				&& temporalResolutionSufficient
				&& runtimeBlocked;
		return new PoweredHoverSurfaceWakeReadinessSummary(
				poweredSourceApiAvailable,
				transientProbeApiAvailable,
				appliedMaxSamplePeriodSeconds,
				expected.size(),
				targets.size(),
				ground,
				ceiling,
				transientTargetApiAvailable,
				runtimeAllowed,
				invalid,
				missing,
				unexpected,
				targets.isEmpty() ? 0.0 : requiredMaxSamplePeriod,
				maxRequiredSampleRate,
				allPresent,
				allTargetProbeApisAvailable,
				temporalResolutionSufficient,
				poweredAndProbeApisAvailable,
				runtimeBlocked,
				allowed,
				allowed ? "READY_FOR_LIVE_SURFACE_WAKE_VALIDATION" : "BLOCKED"
		);
	}

	private static boolean validTarget(
			Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget target
	) {
		if (!"hover".equals(target.spinState())) {
			return false;
		}
		if (target.rotorIndex() < 0 || target.rotorIndex() >= EXPECTED_ROTOR_COUNT) {
			return false;
		}
		if (!expectedClearance(target.clearanceOverRadius())) {
			return false;
		}
		if (target.fastFarWakeTransitSeconds() <= 0.0
				|| target.recommendedMaxSamplePeriodSeconds() <= 0.0
				|| target.recommendedMinSampleRateHertz() <= 0.0) {
			return false;
		}
		if (target.minimumSamplesPerFastTransit() <= 0
				|| target.requiredSubstepsPerMinecraftTick() <= 0
				|| target.requiredSubstepsPerOneKilohertzFrame() <= 0) {
			return false;
		}
		return target.validationBeforeRuntimeRequired()
				&& "target-only-temporal-resolution-unverified".equals(target.status());
	}

	private static Set<String> expectedTargetNames() {
		Set<String> names = new HashSet<>();
		for (String preset : EXPECTED_PRESETS) {
			for (String surface : EXPECTED_SURFACES) {
				for (double clearance : EXPECTED_CLEARANCES_OVER_RADIUS) {
					for (int rotor = 0; rotor < EXPECTED_ROTOR_COUNT; rotor++) {
						names.add(preset + ":" + surface + ":hR" + Double.toString(clearance) + ":rotor" + rotor);
					}
				}
			}
		}
		return names;
	}

	private static String targetName(
			Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget target
	) {
		return target.presetName() + ":" + target.surfaceType() + ":hR"
				+ Double.toString(target.clearanceOverRadius()) + ":rotor" + target.rotorIndex();
	}

	private static boolean expectedClearance(double clearanceOverRadius) {
		for (double expected : EXPECTED_CLEARANCES_OVER_RADIUS) {
			if (Math.abs(expected - clearanceOverRadius) <= EPSILON) {
				return true;
			}
		}
		return false;
	}

	private static double requiredMaxSamplePeriodSeconds(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget> targets
	) {
		return targets.stream()
				.mapToDouble(Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget::recommendedMaxSamplePeriodSeconds)
				.min()
				.orElse(0.0);
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget copy(
			Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget target,
			boolean transientProbeApiAvailable,
			boolean runtimeCouplingAllowed
	) {
		return new Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget(
				target.presetName(),
				target.spinState(),
				target.surfaceType(),
				target.rotorIndex(),
				target.clearanceOverRadius(),
				target.clearanceMeters(),
				target.fastFarWakeTransitSeconds(),
				target.meanWakeTransitSeconds(),
				target.slowDiskPlaneTransitSeconds(),
				target.transitBandSeconds(),
				target.configuredDynamicInflowTauSeconds(),
				target.tauOverFastTransit(),
				target.minimumSamplesPerFastTransit(),
				target.recommendedMaxSamplePeriodSeconds(),
				target.recommendedMinSampleRateHertz(),
				target.minecraftTickRateHertz(),
				target.minecraftTickSamplesPerFastTransit(),
				target.minecraftTickResolvesFastTransit(),
				target.requiredSubstepsPerMinecraftTick(),
				target.sixtyHertzSamplesPerFastTransit(),
				target.sixtyHertzResolvesFastTransit(),
				target.requiredSubstepsPerSixtyHertzFrame(),
				target.oneTwentyHertzSamplesPerFastTransit(),
				target.oneTwentyHertzResolvesFastTransit(),
				target.requiredSubstepsPerOneTwentyHertzFrame(),
				target.twoFortyHertzSamplesPerFastTransit(),
				target.twoFortyHertzResolvesFastTransit(),
				target.requiredSubstepsPerTwoFortyHertzFrame(),
				target.oneKilohertzSamplesPerFastTransit(),
				target.oneKilohertzResolvesFastTransit(),
				target.requiredSubstepsPerOneKilohertzFrame(),
				transientProbeApiAvailable,
				runtimeCouplingAllowed,
				target.validationBeforeRuntimeRequired(),
				target.status(),
				target.runtimeInfo()
		);
	}

	private static PoweredHoverSurfaceWakeReadinessExtrema extrema(
			List<PoweredHoverSurfaceWakeReadinessScenario> scenarios
	) {
		int allowed = 0;
		int maxExpected = 0;
		int maxMissing = 0;
		int maxInvalid = 0;
		int maxUnexpected = 0;
		double minRequiredPeriod = Double.POSITIVE_INFINITY;
		double maxAppliedPeriod = 0.0;
		double maxRequiredRate = 0.0;
		for (PoweredHoverSurfaceWakeReadinessScenario scenario : scenarios) {
			PoweredHoverSurfaceWakeReadinessSummary summary = scenario.summary();
			if (summary.liveValidationRunAllowed()) {
				allowed++;
			}
			maxExpected = Math.max(maxExpected, summary.expectedTargetCount());
			maxMissing = Math.max(maxMissing, summary.missingTargetCount());
			maxInvalid = Math.max(maxInvalid, summary.invalidTargetCount());
			maxUnexpected = Math.max(maxUnexpected, summary.unexpectedTargetCount());
			if (summary.requiredMaxSamplePeriodSeconds() > 0.0) {
				minRequiredPeriod = Math.min(minRequiredPeriod, summary.requiredMaxSamplePeriodSeconds());
			}
			maxAppliedPeriod = Math.max(maxAppliedPeriod, summary.appliedMaxSamplePeriodSeconds());
			maxRequiredRate = Math.max(maxRequiredRate, summary.maxRequiredSampleRateHertz());
		}
		return new PoweredHoverSurfaceWakeReadinessExtrema(
				scenarios.size(),
				allowed,
				scenarios.size() - allowed,
				maxExpected,
				maxMissing,
				maxInvalid,
				maxUnexpected,
				scenarios.isEmpty() ? 0.0 : minRequiredPeriod,
				maxAppliedPeriod,
				maxRequiredRate
		);
	}
}
