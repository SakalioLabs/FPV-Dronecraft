package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Skew-Wake-Acceptance-Gate-Packet";
	public static final String CAVEAT =
			"Gate accepts transient cruise skew-wake validation only when every expected rotor/plane/sweep seed is present, ready, passing, and every error-budget group is a candidate; accepted evidence is lab validation, not gameplay coupling.";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int SCENARIO_SAMPLE_COUNT = 5;
	public static final int SCENARIO_METRIC_COUNT = 33;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate() {
	}

	public record PoweredCruiseSkewWakeAcceptanceSummary(
			boolean skewWakeProbeApiAvailable,
			boolean transientProbeApiAvailable,
			int expectedSeedCount,
			int observedSeedCount,
			int readySeedCount,
			int passedSeedCount,
			int failedSeedCount,
			int unavailableSeedCount,
			int missingSeedCount,
			int unexpectedSeedCount,
			int centerlineSweepSeedCount,
			int lateralSweepSeedCount,
			int readyCenterlineSweepSeedCount,
			int readyLateralSweepSeedCount,
			int passedCenterlineSweepSeedCount,
			int passedLateralSweepSeedCount,
			int expectedGroupCount,
			int observedGroupCount,
			int readyGroupCount,
			int passedGroupCount,
			int candidateGroupCount,
			int blockedGroupCount,
			double maxPressureErrorRatio,
			double maxWakeVelocityErrorRatio,
			double maxArrivalWindowErrorRatio,
			double maxMomentumErrorRatio,
			boolean allExpectedSeedsPresent,
			boolean allSeedsReady,
			boolean allExpectedSeedsPassed,
			boolean allErrorBudgetGroupsCandidate,
			boolean labValidationAccepted,
			String status,
			String message
	) {
	}

	public record PoweredCruiseSkewWakeAcceptanceScenario(
			String scenarioName,
			PoweredCruiseSkewWakeAcceptanceSummary summary
	) {
	}

	public record PoweredCruiseSkewWakeAcceptanceExtrema(
			int scenarioCount,
			int acceptedScenarioCount,
			int blockedScenarioCount,
			int maxExpectedSeedCount,
			int maxMissingSeedCount,
			int maxUnexpectedSeedCount,
			int maxUnavailableSeedCount,
			int maxBlockedGroupCount,
			double maxPressureErrorRatio,
			double maxWakeVelocityErrorRatio,
			double maxArrivalWindowErrorRatio,
			double maxMomentumErrorRatio
	) {
	}

	public record PoweredCruiseSkewWakeAcceptanceAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredCruiseSkewWakeAcceptanceScenario> scenarios,
			PoweredCruiseSkewWakeAcceptanceExtrema extrema
	) {
		public PoweredCruiseSkewWakeAcceptanceAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static PoweredCruiseSkewWakeAcceptanceAudit audit() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> currentSeeds =
				Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.audit().seeds();
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> passingSeeds =
				passingSeeds(currentSeeds);
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> missingOne =
				passingSeeds.subList(0, passingSeeds.size() - 1);
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> failedOne =
				new ArrayList<>(passingSeeds);
		failedOne.set(failedOne.size() - 1, failingSeed(passingSeeds.get(passingSeeds.size() - 1)));

		List<PoweredCruiseSkewWakeAcceptanceScenario> scenarios = List.of(
				new PoweredCruiseSkewWakeAcceptanceScenario(
						"current_probe_apis_unavailable",
						gate(false, false, currentSeeds)),
				new PoweredCruiseSkewWakeAcceptanceScenario(
						"skew_and_transient_probe_all_targets_pass",
						gate(true, true, passingSeeds)),
				new PoweredCruiseSkewWakeAcceptanceScenario(
						"skew_probe_unavailable_all_targets_pass",
						gate(false, true, passingSeeds)),
				new PoweredCruiseSkewWakeAcceptanceScenario(
						"skew_and_transient_probe_one_seed_missing",
						gate(true, true, missingOne)),
				new PoweredCruiseSkewWakeAcceptanceScenario(
						"skew_and_transient_probe_one_seed_failed",
						gate(true, true, failedOne))
		);
		return new PoweredCruiseSkewWakeAcceptanceAudit(
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

	public static PoweredCruiseSkewWakeAcceptanceSummary gate(
			boolean skewWakeProbeApiAvailable,
			boolean transientProbeApiAvailable,
			List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> seeds
	) {
		if (seeds == null) {
			throw new IllegalArgumentException("seeds must not be null.");
		}
		Set<String> expectedNames = expectedSeedNames();
		Set<String> expectedGroupNames = expectedGroupNames();
		Set<String> observedNames = new HashSet<>();
		Set<String> observedGroupNames = new HashSet<>();
		Map<String, List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed>> byGroup =
				new LinkedHashMap<>();
		int ready = 0;
		int passed = 0;
		int failed = 0;
		int unavailable = 0;
		int unexpected = 0;
		int centerline = 0;
		int lateral = 0;
		int readyCenterline = 0;
		int readyLateral = 0;
		int passedCenterline = 0;
		int passedLateral = 0;
		double maxPressureRatio = 0.0;
		double maxVelocityRatio = 0.0;
		double maxArrivalRatio = 0.0;
		double maxMomentumRatio = 0.0;
		for (Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed : seeds) {
			if (seed == null) {
				throw new IllegalArgumentException("seeds must not contain null entries.");
			}
			String name = seedName(seed);
			if (!observedNames.add(name)) {
				throw new IllegalArgumentException("duplicate cruise skew-wake validation seed: " + name);
			}
			if (!expectedNames.contains(name)) {
				unexpected++;
			}
			String groupName = groupName(seed);
			observedGroupNames.add(groupName);
			byGroup.merge(groupName, List.of(seed), Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate::concat);
			if (seed.sweepColumnIndex() == 0) {
				centerline++;
				if (seed.validationSeedReady()) {
					readyCenterline++;
				}
				if (seed.validationPassed()) {
					passedCenterline++;
				}
			} else if (seed.sweepColumnIndex() == -1 || seed.sweepColumnIndex() == 1) {
				lateral++;
				if (seed.validationSeedReady()) {
					readyLateral++;
				}
				if (seed.validationPassed()) {
					passedLateral++;
				}
			} else {
				throw new IllegalArgumentException("sweepColumnIndex must be -1, 0, or 1.");
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
			maxArrivalRatio = Math.max(maxArrivalRatio, seed.arrivalWindowErrorRatio());
			maxMomentumRatio = Math.max(maxMomentumRatio, seed.momentumErrorRatio());
		}
		int missing = 0;
		for (String expectedName : expectedNames) {
			if (!observedNames.contains(expectedName)) {
				missing++;
			}
		}
		int expected = expectedNames.size();
		int expectedGroups = expectedGroupNames.size();
		int readyGroups = 0;
		int passedGroups = 0;
		int candidateGroups = 0;
		for (Map.Entry<String, List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed>> entry
				: byGroup.entrySet()) {
			if (!expectedGroupNames.contains(entry.getKey())) {
				continue;
			}
			Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup group =
					Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.group(
							entry.getValue().get(0).presetName(),
							entry.getValue().get(0).spinState(),
							entry.getValue().get(0).axialPlaneIndex(),
							entry.getValue().get(0).sweepColumnIndex(),
							entry.getValue()
					);
			if (group.allRotorSeedsReady()) {
				readyGroups++;
			}
			if (group.allRotorSeedsPassed()) {
				passedGroups++;
			}
			if (group.validationCandidate()) {
				candidateGroups++;
			}
		}
		boolean allPresent = missing == 0 && unexpected == 0 && seeds.size() == expected;
		boolean allReady = ready == expected && unavailable == 0;
		boolean allPassed = passed == expected && failed == 0;
		boolean allGroupsCandidate = observedGroupNames.size() == expectedGroups && candidateGroups == expectedGroups;
		boolean accepted = skewWakeProbeApiAvailable
				&& transientProbeApiAvailable
				&& allPresent
				&& allReady
				&& allPassed
				&& allGroupsCandidate;
		return new PoweredCruiseSkewWakeAcceptanceSummary(
				skewWakeProbeApiAvailable,
				transientProbeApiAvailable,
				expected,
				seeds.size(),
				ready,
				passed,
				failed,
				unavailable,
				missing,
				unexpected,
				centerline,
				lateral,
				readyCenterline,
				readyLateral,
				passedCenterline,
				passedLateral,
				expectedGroups,
				observedGroupNames.size(),
				readyGroups,
				passedGroups,
				candidateGroups,
				expectedGroups - candidateGroups,
				maxPressureRatio,
				maxVelocityRatio,
				maxArrivalRatio,
				maxMomentumRatio,
				allPresent,
				allReady,
				allPassed,
				allGroupsCandidate,
				accepted,
				accepted ? "ACCEPTED" : "BLOCKED",
				messageFor(skewWakeProbeApiAvailable, transientProbeApiAvailable,
						allPresent, allReady, allPassed, allGroupsCandidate)
		);
	}

	private static String messageFor(
			boolean skewWakeProbeApiAvailable,
			boolean transientProbeApiAvailable,
			boolean allPresent,
			boolean allReady,
			boolean allPassed,
			boolean allGroupsCandidate
	) {
		if (!skewWakeProbeApiAvailable) {
			return "skew-wake-probe-api-unavailable";
		}
		if (!transientProbeApiAvailable) {
			return "transient-probe-api-unavailable";
		}
		if (!allPresent) {
			return "cruise-skew-wake-seed-set-incomplete";
		}
		if (!allReady) {
			return "cruise-skew-wake-seeds-not-ready";
		}
		if (!allPassed) {
			return "cruise-skew-wake-seeds-failed";
		}
		if (!allGroupsCandidate) {
			return "cruise-skew-wake-error-budget-blocked";
		}
		return "cruise-skew-wake-validation-accepted";
	}

	private static Set<String> expectedSeedNames() {
		Set<String> names = new HashSet<>();
		for (Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed
				: Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.audit().seeds()) {
			if (!names.add(seedName(seed))) {
				throw new IllegalStateException("duplicate expected cruise skew-wake validation seed: " + seedName(seed));
			}
		}
		return names;
	}

	private static Set<String> expectedGroupNames() {
		Set<String> names = new HashSet<>();
		for (Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup group
				: Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.audit().groups()) {
			names.add(group.presetName()
					+ ":plane" + group.axialPlaneIndex()
					+ ":sweep" + group.sweepColumnIndex());
		}
		return names;
	}

	private static String seedName(
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed
	) {
		if (seed.presetName() == null || seed.presetName().isBlank()) {
			throw new IllegalArgumentException("seed presetName must not be blank.");
		}
		if (!"cruise".equals(seed.spinState())) {
			throw new IllegalArgumentException("seed spinState must be cruise.");
		}
		return seed.presetName()
				+ ":plane" + seed.axialPlaneIndex()
				+ ":sweep" + seed.sweepColumnIndex()
				+ ":rotor" + seed.rotorIndex();
	}

	private static String groupName(
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed
	) {
		return seed.presetName()
				+ ":plane" + seed.axialPlaneIndex()
				+ ":sweep" + seed.sweepColumnIndex();
	}

	private static List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> concat(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> left,
			List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> right
	) {
		return java.util.stream.Stream.concat(left.stream(), right.stream()).toList();
	}

	private static List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> passingSeeds(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> seeds
	) {
		return seeds.stream()
				.map(Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate::passingSeed)
				.toList();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed passingSeed(
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed
	) {
		return new Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed(
				seed.presetName(),
				seed.spinState(),
				seed.rotorIndex(),
				seed.axialPlaneIndex(),
				seed.sweepColumnIndex(),
				seed.axialPlaneFraction(),
				true,
				true,
				true,
				true,
				true,
				true,
				seed.targetAxialWakePressurePascals(),
				seed.targetResultantDynamicPressurePascals(),
				seed.targetResultantDynamicPressurePascals(),
				0.0,
				0.0,
				seed.targetResultantWakeVelocityMetersPerSecond(),
				seed.targetResultantWakeVelocityMetersPerSecond(),
				0.0,
				0.0,
				seed.targetCenterlineArrivalSeconds(),
				seed.targetLateralArrivalSeconds(),
				seed.targetArrivalBandSeconds(),
				seed.targetArrivalToleranceSeconds(),
				seed.targetCenterlineArrivalSeconds(),
				0.0,
				0.0,
				seed.targetAxialMomentumFluxNewtons(),
				seed.targetAxialMomentumFluxNewtons(),
				0.0,
				0.0,
				seed.pressureToleranceRatio(),
				seed.velocityToleranceRatio(),
				seed.arrivalTimeToleranceFraction(),
				seed.momentumToleranceRatio(),
				true,
				true,
				true,
				true,
				true,
				"READY_PASS",
				"cruise-skew-wake-validation-seed-passed",
				"OK",
				"live-cruise-skew-wake-sample",
				"live-runtime",
				"synthetic-live-cruise-skew-wake-acceptance-scenario"
		);
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed failingSeed(
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed
	) {
		double momentumErrorRatio = 0.5;
		double momentumError = seed.targetAxialMomentumFluxNewtons() * momentumErrorRatio;
		return new Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed(
				seed.presetName(),
				seed.spinState(),
				seed.rotorIndex(),
				seed.axialPlaneIndex(),
				seed.sweepColumnIndex(),
				seed.axialPlaneFraction(),
				true,
				true,
				true,
				true,
				true,
				true,
				seed.targetAxialWakePressurePascals(),
				seed.targetResultantDynamicPressurePascals(),
				seed.targetResultantDynamicPressurePascals(),
				0.0,
				0.0,
				seed.targetResultantWakeVelocityMetersPerSecond(),
				seed.targetResultantWakeVelocityMetersPerSecond(),
				0.0,
				0.0,
				seed.targetCenterlineArrivalSeconds(),
				seed.targetLateralArrivalSeconds(),
				seed.targetArrivalBandSeconds(),
				seed.targetArrivalToleranceSeconds(),
				seed.targetCenterlineArrivalSeconds(),
				0.0,
				0.0,
				seed.targetAxialMomentumFluxNewtons(),
				seed.targetAxialMomentumFluxNewtons() - momentumError,
				momentumError,
				momentumErrorRatio,
				seed.pressureToleranceRatio(),
				seed.velocityToleranceRatio(),
				seed.arrivalTimeToleranceFraction(),
				seed.momentumToleranceRatio(),
				true,
				true,
				true,
				false,
				false,
				"READY_FAIL",
				"cruise-skew-wake-validation-seed-failed",
				"OK",
				"live-cruise-skew-wake-sample",
				"live-runtime",
				"synthetic-live-cruise-skew-wake-acceptance-scenario"
		);
	}

	private static PoweredCruiseSkewWakeAcceptanceExtrema extrema(
			List<PoweredCruiseSkewWakeAcceptanceScenario> scenarios
	) {
		int accepted = 0;
		int maxExpected = 0;
		int maxMissing = 0;
		int maxUnexpected = 0;
		int maxUnavailable = 0;
		int maxBlockedGroups = 0;
		double maxPressureRatio = 0.0;
		double maxVelocityRatio = 0.0;
		double maxArrivalRatio = 0.0;
		double maxMomentumRatio = 0.0;
		for (PoweredCruiseSkewWakeAcceptanceScenario scenario : scenarios) {
			PoweredCruiseSkewWakeAcceptanceSummary summary = scenario.summary();
			if (summary.labValidationAccepted()) {
				accepted++;
			}
			maxExpected = Math.max(maxExpected, summary.expectedSeedCount());
			maxMissing = Math.max(maxMissing, summary.missingSeedCount());
			maxUnexpected = Math.max(maxUnexpected, summary.unexpectedSeedCount());
			maxUnavailable = Math.max(maxUnavailable, summary.unavailableSeedCount());
			maxBlockedGroups = Math.max(maxBlockedGroups, summary.blockedGroupCount());
			maxPressureRatio = Math.max(maxPressureRatio, summary.maxPressureErrorRatio());
			maxVelocityRatio = Math.max(maxVelocityRatio, summary.maxWakeVelocityErrorRatio());
			maxArrivalRatio = Math.max(maxArrivalRatio, summary.maxArrivalWindowErrorRatio());
			maxMomentumRatio = Math.max(maxMomentumRatio, summary.maxMomentumErrorRatio());
		}
		return new PoweredCruiseSkewWakeAcceptanceExtrema(
				scenarios.size(),
				accepted,
				scenarios.size() - accepted,
				maxExpected,
				maxMissing,
				maxUnexpected,
				maxUnavailable,
				maxBlockedGroups,
				maxPressureRatio,
				maxVelocityRatio,
				maxArrivalRatio,
				maxMomentumRatio
		);
	}
}
