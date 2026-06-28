package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Surface-Wake-Acceptance-Gate-Packet";
	public static final String CAVEAT =
			"Gate accepts transient surface-wake validation only when every expected rotor/surface/clearance seed is present, ready, and passing; accepted evidence is lab validation, not gameplay coupling.";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_COUNT = 24;
	public static final int SUMMARY_METRIC_ROW_COUNT = 10;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate() {
	}

	public record PoweredHoverSurfaceWakeAcceptanceSummary(
			boolean transientProbeApiAvailable,
			int expectedSeedCount,
			int observedSeedCount,
			int readySeedCount,
			int passedSeedCount,
			int failedSeedCount,
			int unavailableSeedCount,
			int missingSeedCount,
			int unexpectedSeedCount,
			int groundSeedCount,
			int ceilingSeedCount,
			int readyGroundSeedCount,
			int readyCeilingSeedCount,
			int passedGroundSeedCount,
			int passedCeilingSeedCount,
			double maxPressureErrorRatio,
			double maxWakeVelocityErrorRatio,
			double maxArrivalBandErrorRatio,
			boolean allExpectedSeedsPresent,
			boolean allSeedsReady,
			boolean allExpectedSeedsPassed,
			boolean labValidationAccepted,
			String status,
			String message
	) {
	}

	public record PoweredHoverSurfaceWakeAcceptanceScenario(
			String scenarioName,
			PoweredHoverSurfaceWakeAcceptanceSummary summary
	) {
	}

	public record PoweredHoverSurfaceWakeAcceptanceExtrema(
			int scenarioCount,
			int acceptedScenarioCount,
			int blockedScenarioCount,
			int maxExpectedSeedCount,
			int maxMissingSeedCount,
			int maxUnexpectedSeedCount,
			int maxUnavailableSeedCount,
			double maxPressureErrorRatio,
			double maxWakeVelocityErrorRatio,
			double maxArrivalBandErrorRatio
	) {
	}

	public record PoweredHoverSurfaceWakeAcceptanceAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverSurfaceWakeAcceptanceScenario> scenarios,
			PoweredHoverSurfaceWakeAcceptanceExtrema extrema
	) {
		public PoweredHoverSurfaceWakeAcceptanceAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredHoverSurfaceWakeAcceptanceAudit audit() {
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> currentSeeds =
				Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.audit().seeds();
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> passingSeeds =
				passingSeeds(currentSeeds);
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> missingOne =
				passingSeeds.subList(0, passingSeeds.size() - 1);
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> failedOne =
				new ArrayList<>(passingSeeds);
		failedOne.set(failedOne.size() - 1, failingSeed(passingSeeds.get(passingSeeds.size() - 1)));

		List<PoweredHoverSurfaceWakeAcceptanceScenario> scenarios = List.of(
				new PoweredHoverSurfaceWakeAcceptanceScenario(
						"current_transient_probe_unavailable",
						gate(false, currentSeeds)),
				new PoweredHoverSurfaceWakeAcceptanceScenario(
						"transient_probe_all_targets_pass",
						gate(true, passingSeeds)),
				new PoweredHoverSurfaceWakeAcceptanceScenario(
						"transient_probe_one_seed_missing",
						gate(true, missingOne)),
				new PoweredHoverSurfaceWakeAcceptanceScenario(
						"transient_probe_one_seed_failed",
						gate(true, failedOne))
		);
		return new PoweredHoverSurfaceWakeAcceptanceAudit(
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

	public static PoweredHoverSurfaceWakeAcceptanceSummary gate(
			boolean transientProbeApiAvailable,
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> seeds
	) {
		if (seeds == null) {
			throw new IllegalArgumentException("seeds must not be null.");
		}
		Set<String> expectedNames = expectedSeedNames();
		Set<String> observedNames = new HashSet<>();
		int ready = 0;
		int passed = 0;
		int failed = 0;
		int unavailable = 0;
		int unexpected = 0;
		int ground = 0;
		int ceiling = 0;
		int readyGround = 0;
		int readyCeiling = 0;
		int passedGround = 0;
		int passedCeiling = 0;
		double maxPressureRatio = 0.0;
		double maxVelocityRatio = 0.0;
		double maxArrivalRatio = 0.0;
		for (Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed : seeds) {
			if (seed == null) {
				throw new IllegalArgumentException("seeds must not contain null entries.");
			}
			String name = seedName(seed);
			if (!observedNames.add(name)) {
				throw new IllegalArgumentException("duplicate surface wake validation seed: " + name);
			}
			if (!expectedNames.contains(name)) {
				unexpected++;
			}
			if ("ground".equals(seed.surfaceType())) {
				ground++;
				if (seed.validationSeedReady()) {
					readyGround++;
				}
				if (seed.validationPassed()) {
					passedGround++;
				}
			} else if ("ceiling".equals(seed.surfaceType())) {
				ceiling++;
				if (seed.validationSeedReady()) {
					readyCeiling++;
				}
				if (seed.validationPassed()) {
					passedCeiling++;
				}
			} else {
				throw new IllegalArgumentException("surfaceType must be ground or ceiling.");
			}
			if (seed.validationSeedReady()) {
				ready++;
			} else {
				unavailable++;
			}
			if (seed.validationPassed()) {
				passed++;
			} else if (seed.validationSeedReady()) {
				failed++;
			}
			maxPressureRatio = Math.max(maxPressureRatio, seed.pressureErrorRatio());
			maxVelocityRatio = Math.max(maxVelocityRatio, seed.wakeVelocityErrorRatio());
			maxArrivalRatio = Math.max(maxArrivalRatio, seed.arrivalBandErrorRatio());
		}
		int missing = 0;
		for (String expectedName : expectedNames) {
			if (!observedNames.contains(expectedName)) {
				missing++;
			}
		}
		int expected = expectedNames.size();
		boolean allPresent = missing == 0 && unexpected == 0 && seeds.size() == expected;
		boolean allReady = ready == expected && unavailable == 0;
		boolean allPassed = passed == expected && failed == 0;
		boolean accepted = transientProbeApiAvailable && allPresent && allReady && allPassed;
		return new PoweredHoverSurfaceWakeAcceptanceSummary(
				transientProbeApiAvailable,
				expected,
				seeds.size(),
				ready,
				passed,
				failed,
				unavailable,
				missing,
				unexpected,
				ground,
				ceiling,
				readyGround,
				readyCeiling,
				passedGround,
				passedCeiling,
				maxPressureRatio,
				maxVelocityRatio,
				maxArrivalRatio,
				allPresent,
				allReady,
				allPassed,
				accepted,
				accepted ? "ACCEPTED" : "BLOCKED",
				messageFor(transientProbeApiAvailable, allPresent, allReady, allPassed)
		);
	}

	private static String messageFor(
			boolean transientProbeApiAvailable,
			boolean allPresent,
			boolean allReady,
			boolean allPassed
	) {
		if (!transientProbeApiAvailable) {
			return "transient-probe-api-unavailable";
		}
		if (!allPresent) {
			return "surface-wake-seed-set-incomplete";
		}
		if (!allReady) {
			return "surface-wake-seeds-not-ready";
		}
		if (!allPassed) {
			return "surface-wake-seeds-failed";
		}
		return "surface-wake-validation-accepted";
	}

	private static Set<String> expectedSeedNames() {
		Set<String> names = new HashSet<>();
		for (Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed
				: Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.audit().seeds()) {
			if (!names.add(seedName(seed))) {
				throw new IllegalStateException("duplicate expected surface wake validation seed: " + seedName(seed));
			}
		}
		return names;
	}

	private static String seedName(
			Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed
	) {
		if (seed.presetName() == null || seed.presetName().isBlank()) {
			throw new IllegalArgumentException("seed presetName must not be blank.");
		}
		return seed.presetName() + ":" + seed.surfaceType() + ":hR"
				+ Double.toString(seed.clearanceOverRadius()) + ":rotor" + seed.rotorIndex();
	}

	private static List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> passingSeeds(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> seeds
	) {
		return seeds.stream()
				.map(Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate::passingSeed)
				.toList();
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed passingSeed(
			Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed
	) {
		return new Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed(
				seed.presetName(),
				seed.spinState(),
				seed.surfaceType(),
				seed.rotorIndex(),
				seed.clearanceOverRadius(),
				seed.clearanceMeters(),
				true,
				true,
				true,
				true,
				true,
				seed.targetPressurePascals(),
				seed.targetPressurePascals(),
				0.0,
				0.0,
				seed.targetWakeVelocityMetersPerSecond(),
				seed.targetWakeVelocityMetersPerSecond(),
				0.0,
				0.0,
				seed.targetFastArrivalSeconds(),
				seed.targetSlowArrivalSeconds(),
				seed.targetArrivalBandSeconds(),
				seed.targetFastArrivalSeconds() + seed.targetArrivalBandSeconds() * 0.5,
				0.0,
				0.0,
				seed.pressureToleranceRatio(),
				seed.velocityToleranceRatio(),
				seed.arrivalBandToleranceFraction(),
				true,
				true,
				true,
				true,
				"READY_PASS",
				"surface-wake-validation-seed-passed",
				"OK",
				"live-surface-wake-sample",
				"live-runtime",
				"synthetic-live-surface-wake-acceptance-scenario"
		);
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed failingSeed(
			Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed
	) {
		double pressureErrorRatio = 0.5;
		double pressureError = seed.targetPressurePascals() * pressureErrorRatio;
		return new Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed(
				seed.presetName(),
				seed.spinState(),
				seed.surfaceType(),
				seed.rotorIndex(),
				seed.clearanceOverRadius(),
				seed.clearanceMeters(),
				true,
				true,
				true,
				true,
				true,
				seed.targetPressurePascals(),
				seed.targetPressurePascals() - pressureError,
				pressureError,
				pressureErrorRatio,
				seed.targetWakeVelocityMetersPerSecond(),
				seed.targetWakeVelocityMetersPerSecond(),
				0.0,
				0.0,
				seed.targetFastArrivalSeconds(),
				seed.targetSlowArrivalSeconds(),
				seed.targetArrivalBandSeconds(),
				seed.targetFastArrivalSeconds() + seed.targetArrivalBandSeconds() * 0.5,
				0.0,
				0.0,
				seed.pressureToleranceRatio(),
				seed.velocityToleranceRatio(),
				seed.arrivalBandToleranceFraction(),
				false,
				true,
				true,
				false,
				"READY_FAIL",
				"surface-wake-validation-seed-failed",
				"OK",
				"live-surface-wake-sample",
				"live-runtime",
				"synthetic-live-surface-wake-acceptance-scenario"
		);
	}

	private static PoweredHoverSurfaceWakeAcceptanceExtrema extrema(
			List<PoweredHoverSurfaceWakeAcceptanceScenario> scenarios
	) {
		int accepted = 0;
		int maxExpected = 0;
		int maxMissing = 0;
		int maxUnexpected = 0;
		int maxUnavailable = 0;
		double maxPressureRatio = 0.0;
		double maxVelocityRatio = 0.0;
		double maxArrivalRatio = 0.0;
		for (PoweredHoverSurfaceWakeAcceptanceScenario scenario : scenarios) {
			PoweredHoverSurfaceWakeAcceptanceSummary summary = scenario.summary();
			if (summary.labValidationAccepted()) {
				accepted++;
			}
			maxExpected = Math.max(maxExpected, summary.expectedSeedCount());
			maxMissing = Math.max(maxMissing, summary.missingSeedCount());
			maxUnexpected = Math.max(maxUnexpected, summary.unexpectedSeedCount());
			maxUnavailable = Math.max(maxUnavailable, summary.unavailableSeedCount());
			maxPressureRatio = Math.max(maxPressureRatio, summary.maxPressureErrorRatio());
			maxVelocityRatio = Math.max(maxVelocityRatio, summary.maxWakeVelocityErrorRatio());
			maxArrivalRatio = Math.max(maxArrivalRatio, summary.maxArrivalBandErrorRatio());
		}
		return new PoweredHoverSurfaceWakeAcceptanceExtrema(
				scenarios.size(),
				accepted,
				scenarios.size() - accepted,
				maxExpected,
				maxMissing,
				maxUnexpected,
				maxUnavailable,
				maxPressureRatio,
				maxVelocityRatio,
				maxArrivalRatio
		);
	}
}
