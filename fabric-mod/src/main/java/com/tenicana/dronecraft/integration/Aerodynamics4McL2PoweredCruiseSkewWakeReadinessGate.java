package com.tenicana.dronecraft.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate {
	private static final double MINECRAFT_TICK_PERIOD_SECONDS = 0.05;
	private static final double EPSILON = 1.0e-12;
	private static final String[] EXPECTED_PRESETS = { "racingQuad", "apDrone", "cinewhoop", "heavyLift" };
	private static final int EXPECTED_ROTOR_COUNT = 4;
	private static final int[] EXPECTED_AXIAL_PLANES = { 1, 2, 3, 4 };
	private static final int[] EXPECTED_SWEEP_COLUMNS = { -1, 0, 1 };

	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Skew-Wake-Readiness-Gate-Packet";
	public static final String CAVEAT =
			"Gate remains closed until powered-source support, local skew-wake probe support, transient probe support, complete rotor-resolved cruise targets, and sufficient temporal substepping are present for live A4MC validation; runtime coupling remains closed.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int SCENARIO_SAMPLE_COUNT = 6;
	public static final int SCENARIO_METRIC_COUNT = 23;
	public static final int SUMMARY_METRIC_ROW_COUNT = 10;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseSkewWakeReadinessGate() {
	}

	public record PoweredCruiseSkewWakeReadinessSummary(
			boolean poweredSourceApiAvailable,
			boolean skewWakeProbeApiAvailable,
			boolean transientProbeApiAvailable,
			double appliedMaxSamplePeriodSeconds,
			int expectedTargetCount,
			int observedTargetCount,
			int sourceTermTargetCount,
			int centerlineSweepTargetCount,
			int lateralSweepTargetCount,
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

	public record PoweredCruiseSkewWakeReadinessScenario(
			String scenarioName,
			PoweredCruiseSkewWakeReadinessSummary summary
	) {
	}

	public record PoweredCruiseSkewWakeReadinessExtrema(
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

	public record PoweredCruiseSkewWakeReadinessAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredCruiseSkewWakeReadinessScenario> scenarios,
			PoweredCruiseSkewWakeReadinessExtrema extrema
	) {
		public PoweredCruiseSkewWakeReadinessAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredCruiseSkewWakeReadinessAudit audit() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> current =
				Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.audit().targets();
		List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> probeReady =
				current.stream()
						.map(target -> copy(target, true, false))
						.toList();
		double requiredMaxSamplePeriod = requiredMaxSamplePeriodSeconds(probeReady);
		List<PoweredCruiseSkewWakeReadinessScenario> scenarios = List.of(
				scenario("current_api_unavailable_tick_rate_only", false, false, false,
						MINECRAFT_TICK_PERIOD_SECONDS, current),
				scenario("powered_and_skew_probe_api_available_substepped", true, true, true,
						requiredMaxSamplePeriod, probeReady),
				scenario("api_available_one_target_missing", true, true, true,
						requiredMaxSamplePeriod, probeReady.subList(0, probeReady.size() - 1)),
				scenario("api_available_tick_rate_underresolved", true, true, true,
						MINECRAFT_TICK_PERIOD_SECONDS, probeReady),
				scenario("powered_api_available_skew_probe_unavailable", true, false, true,
						requiredMaxSamplePeriod, probeReady),
				scenario("powered_api_available_probe_target_unavailable", true, true, true,
						requiredMaxSamplePeriod, current)
		);
		return new PoweredCruiseSkewWakeReadinessAudit(
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

	public static PoweredCruiseSkewWakeReadinessScenario scenario(
			String scenarioName,
			boolean poweredSourceApiAvailable,
			boolean skewWakeProbeApiAvailable,
			boolean transientProbeApiAvailable,
			double appliedMaxSamplePeriodSeconds,
			List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> targets
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		return new PoweredCruiseSkewWakeReadinessScenario(
				scenarioName,
				gate(poweredSourceApiAvailable, skewWakeProbeApiAvailable, transientProbeApiAvailable,
						appliedMaxSamplePeriodSeconds, targets)
		);
	}

	public static PoweredCruiseSkewWakeReadinessSummary gate(
			boolean poweredSourceApiAvailable,
			boolean skewWakeProbeApiAvailable,
			boolean transientProbeApiAvailable,
			double appliedMaxSamplePeriodSeconds,
			List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> targets
	) {
		if (targets == null) {
			throw new IllegalArgumentException("targets must not be null.");
		}
		if (!Double.isFinite(appliedMaxSamplePeriodSeconds) || appliedMaxSamplePeriodSeconds <= 0.0) {
			throw new IllegalArgumentException("appliedMaxSamplePeriodSeconds must be positive and finite.");
		}
		Set<String> expected = expectedTargetNames();
		Set<String> observed = new HashSet<>();
		Set<String> sourceTerms = new HashSet<>();
		int centerline = 0;
		int lateral = 0;
		int transientTargetApiAvailable = 0;
		int runtimeAllowed = 0;
		int invalid = 0;
		int unexpected = 0;
		double requiredMaxSamplePeriod = Double.POSITIVE_INFINITY;
		double maxRequiredSampleRate = 0.0;
		for (Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget target
				: targets) {
			if (target == null || target.presetName() == null || target.presetName().isBlank()) {
				throw new IllegalArgumentException("targets must include stable preset names.");
			}
			String name = targetName(target);
			if (!observed.add(name)) {
				throw new IllegalArgumentException("duplicate cruise skew-wake target: " + name);
			}
			if (!expected.contains(name)) {
				unexpected++;
			}
			sourceTerms.add(target.presetName() + ":rotor" + target.rotorIndex());
			if (target.sweepColumnIndex() == 0) {
				centerline++;
			} else if (expectedSweepColumn(target.sweepColumnIndex())) {
				lateral++;
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
				&& skewWakeProbeApiAvailable
				&& transientProbeApiAvailable
				&& allTargetProbeApisAvailable;
		boolean runtimeBlocked = runtimeAllowed == 0;
		boolean allowed = poweredAndProbeApisAvailable
				&& allPresent
				&& invalid == 0
				&& temporalResolutionSufficient
				&& runtimeBlocked;
		return new PoweredCruiseSkewWakeReadinessSummary(
				poweredSourceApiAvailable,
				skewWakeProbeApiAvailable,
				transientProbeApiAvailable,
				appliedMaxSamplePeriodSeconds,
				expected.size(),
				targets.size(),
				sourceTerms.size(),
				centerline,
				lateral,
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
				allowed ? "READY_FOR_LIVE_CRUISE_SKEW_WAKE_VALIDATION" : "BLOCKED"
		);
	}

	private static boolean validTarget(
			Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget target
	) {
		if (!"cruise".equals(target.spinState())) {
			return false;
		}
		if (target.rotorIndex() < 0 || target.rotorIndex() >= EXPECTED_ROTOR_COUNT) {
			return false;
		}
		if (!expectedAxialPlane(target.axialPlaneIndex()) || !expectedSweepColumn(target.sweepColumnIndex())) {
			return false;
		}
		if (target.centerlineResultantTransitSeconds() <= 0.0
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
				&& "target-only-cruise-skew-wake-temporal-resolution-unverified".equals(target.status());
	}

	private static Set<String> expectedTargetNames() {
		Set<String> names = new HashSet<>();
		for (String preset : EXPECTED_PRESETS) {
			for (int plane : EXPECTED_AXIAL_PLANES) {
				for (int sweep : EXPECTED_SWEEP_COLUMNS) {
					for (int rotor = 0; rotor < EXPECTED_ROTOR_COUNT; rotor++) {
						names.add(preset + ":plane" + plane + ":sweep" + sweep + ":rotor" + rotor);
					}
				}
			}
		}
		return names;
	}

	private static String targetName(
			Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget target
	) {
		return target.presetName()
				+ ":plane" + target.axialPlaneIndex()
				+ ":sweep" + target.sweepColumnIndex()
				+ ":rotor" + target.rotorIndex();
	}

	private static boolean expectedAxialPlane(int axialPlaneIndex) {
		for (int expected : EXPECTED_AXIAL_PLANES) {
			if (expected == axialPlaneIndex) {
				return true;
			}
		}
		return false;
	}

	private static boolean expectedSweepColumn(int sweepColumnIndex) {
		for (int expected : EXPECTED_SWEEP_COLUMNS) {
			if (expected == sweepColumnIndex) {
				return true;
			}
		}
		return false;
	}

	private static double requiredMaxSamplePeriodSeconds(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> targets
	) {
		return targets.stream()
				.mapToDouble(Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget::recommendedMaxSamplePeriodSeconds)
				.min()
				.orElse(0.0);
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget copy(
			Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget target,
			boolean transientProbeApiAvailable,
			boolean runtimeCouplingAllowed
	) {
		return new Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget(
				target.presetName(),
				target.spinState(),
				target.rotorIndex(),
				target.axialPlaneIndex(),
				target.sweepColumnIndex(),
				target.axialPlaneFraction(),
				target.centerlineResultantTransitSeconds(),
				target.lateralAdjustedTransitSeconds(),
				target.transitBandSeconds(),
				target.configuredDynamicInflowTauSeconds(),
				target.tauOverCenterlineTransit(),
				target.tauOverLateralTransit(),
				target.minimumSamplesPerFastTransit(),
				target.recommendedMaxSamplePeriodSeconds(),
				target.recommendedMinSampleRateHertz(),
				target.minecraftTickRateHertz(),
				target.minecraftTickSamplesPerCenterlineTransit(),
				target.minecraftTickResolvesCenterlineTransit(),
				target.requiredSubstepsPerMinecraftTick(),
				target.sixtyHertzSamplesPerCenterlineTransit(),
				target.sixtyHertzResolvesCenterlineTransit(),
				target.requiredSubstepsPerSixtyHertzFrame(),
				target.oneTwentyHertzSamplesPerCenterlineTransit(),
				target.oneTwentyHertzResolvesCenterlineTransit(),
				target.requiredSubstepsPerOneTwentyHertzFrame(),
				target.twoFortyHertzSamplesPerCenterlineTransit(),
				target.twoFortyHertzResolvesCenterlineTransit(),
				target.requiredSubstepsPerTwoFortyHertzFrame(),
				target.oneKilohertzSamplesPerCenterlineTransit(),
				target.oneKilohertzResolvesCenterlineTransit(),
				target.requiredSubstepsPerOneKilohertzFrame(),
				target.oneKilohertzSamplesPerLateralAdjustedTransit(),
				target.oneKilohertzResolvesLateralAdjustedTransit(),
				transientProbeApiAvailable,
				runtimeCouplingAllowed,
				target.validationBeforeRuntimeRequired(),
				target.status(),
				target.runtimeInfo()
		);
	}

	private static PoweredCruiseSkewWakeReadinessExtrema extrema(
			List<PoweredCruiseSkewWakeReadinessScenario> scenarios
	) {
		int allowed = 0;
		int maxExpected = 0;
		int maxMissing = 0;
		int maxInvalid = 0;
		int maxUnexpected = 0;
		double minRequiredPeriod = Double.POSITIVE_INFINITY;
		double maxAppliedPeriod = 0.0;
		double maxRequiredRate = 0.0;
		for (PoweredCruiseSkewWakeReadinessScenario scenario : scenarios) {
			PoweredCruiseSkewWakeReadinessSummary summary = scenario.summary();
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
		return new PoweredCruiseSkewWakeReadinessExtrema(
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
