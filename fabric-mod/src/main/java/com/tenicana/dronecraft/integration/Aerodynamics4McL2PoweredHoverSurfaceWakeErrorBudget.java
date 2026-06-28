package com.tenicana.dronecraft.integration;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Surface-Wake-Error-Budget-Packet";
	public static final String CAVEAT =
			"Aggregate error budget groups transient surface-wake validation seeds by preset, surface, and clearance; it diagnoses lab evidence quality and does not provide gameplay tuning parameters.";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int EXPECTED_ROTOR_SEED_COUNT = 4;
	public static final int GROUP_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.SEED_SAMPLE_COUNT / EXPECTED_ROTOR_SEED_COUNT;
	public static final int GROUP_METRIC_COUNT = 28;
	public static final int SUMMARY_METRIC_ROW_COUNT = 16;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ GROUP_SAMPLE_COUNT * GROUP_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget() {
	}

	public record PoweredHoverSurfaceWakeErrorBudgetGroup(
			String presetName,
			String spinState,
			String surfaceType,
			double clearanceOverRadius,
			double clearanceMeters,
			int expectedRotorSeedCount,
			int observedRotorSeedCount,
			int missingRotorSeedCount,
			int unexpectedRotorSeedCount,
			int readyRotorSeedCount,
			int passedRotorSeedCount,
			int failedRotorSeedCount,
			int unavailableRotorSeedCount,
			boolean allRotorSeedsPresent,
			boolean allRotorSeedsReady,
			boolean allRotorSeedsPassed,
			double meanTargetPressurePascals,
			double maxPressureErrorRatio,
			double meanPressureErrorRatio,
			double meanTargetWakeVelocityMetersPerSecond,
			double maxWakeVelocityErrorRatio,
			double meanWakeVelocityErrorRatio,
			double meanArrivalBandSeconds,
			double maxArrivalBandErrorRatio,
			double meanArrivalBandErrorRatio,
			boolean validationCandidate,
			String status,
			String message
	) {
	}

	public record PoweredHoverSurfaceWakeErrorBudgetExtrema(
			int groupCount,
			int groundGroupCount,
			int ceilingGroupCount,
			int completeGroupCount,
			int readyGroupCount,
			int passedGroupCount,
			int validationCandidateCount,
			int blockedGroupCount,
			int maxMissingRotorSeedCount,
			int maxUnexpectedRotorSeedCount,
			int maxUnavailableRotorSeedCount,
			double maxPressureErrorRatio,
			double maxWakeVelocityErrorRatio,
			double maxArrivalBandErrorRatio,
			double maxMeanTargetPressurePascals,
			double maxMeanTargetWakeVelocityMetersPerSecond
	) {
	}

	public record PoweredHoverSurfaceWakeErrorBudgetAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int groupSampleCount,
			int expectedRotorSeedCount,
			int groupMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverSurfaceWakeErrorBudgetGroup> groups,
			PoweredHoverSurfaceWakeErrorBudgetExtrema extrema
	) {
		public PoweredHoverSurfaceWakeErrorBudgetAudit {
			groups = List.copyOf(groups);
		}
	}

	public static PoweredHoverSurfaceWakeErrorBudgetAudit audit() {
		return audit(Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.audit());
	}

	public static PoweredHoverSurfaceWakeErrorBudgetAudit audit(
			Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeResultSeedAudit seedAudit
	) {
		if (seedAudit == null) {
			throw new IllegalArgumentException("seedAudit must not be null.");
		}
		Map<String, List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed>> byGroup =
				new LinkedHashMap<>();
		for (Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed
				: seedAudit.seeds()) {
			if (seed == null) {
				throw new IllegalArgumentException("seedAudit must not contain null seeds.");
			}
			byGroup.merge(groupKey(seed), List.of(seed), Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget::concat);
		}
		List<PoweredHoverSurfaceWakeErrorBudgetGroup> groups = byGroup.values().stream()
				.map(seeds -> group(
						seeds.get(0).presetName(),
						seeds.get(0).spinState(),
						seeds.get(0).surfaceType(),
						seeds.get(0).clearanceOverRadius(),
						seeds
				))
				.toList();
		return new PoweredHoverSurfaceWakeErrorBudgetAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				GROUP_SAMPLE_COUNT,
				EXPECTED_ROTOR_SEED_COUNT,
				GROUP_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				groups,
				extrema(groups)
		);
	}

	public static PoweredHoverSurfaceWakeErrorBudgetGroup group(
			String presetName,
			String spinState,
			String surfaceType,
			double clearanceOverRadius,
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> seeds
	) {
		if (presetName == null || presetName.isBlank()) {
			throw new IllegalArgumentException("presetName must not be blank.");
		}
		if (!"hover".equals(spinState)) {
			throw new IllegalArgumentException("spinState must be hover.");
		}
		if (!"ground".equals(surfaceType) && !"ceiling".equals(surfaceType)) {
			throw new IllegalArgumentException("surfaceType must be ground or ceiling.");
		}
		if (!Double.isFinite(clearanceOverRadius) || clearanceOverRadius <= 0.0) {
			throw new IllegalArgumentException("clearanceOverRadius must be positive and finite.");
		}
		if (seeds == null) {
			throw new IllegalArgumentException("seeds must not be null.");
		}
		Set<Integer> observedRotors = new HashSet<>();
		int unexpected = 0;
		int ready = 0;
		int passed = 0;
		int failed = 0;
		int unavailable = 0;
		double clearanceMetersSum = 0.0;
		double targetPressureSum = 0.0;
		double pressureErrorRatioSum = 0.0;
		double maxPressureErrorRatio = 0.0;
		double targetWakeVelocitySum = 0.0;
		double wakeVelocityErrorRatioSum = 0.0;
		double maxWakeVelocityErrorRatio = 0.0;
		double arrivalBandSum = 0.0;
		double arrivalBandErrorRatioSum = 0.0;
		double maxArrivalBandErrorRatio = 0.0;
		for (Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed : seeds) {
			if (seed == null) {
				throw new IllegalArgumentException("seeds must not contain null entries.");
			}
			if (!presetName.equals(seed.presetName())
					|| !spinState.equals(seed.spinState())
					|| !surfaceType.equals(seed.surfaceType())
					|| Math.abs(clearanceOverRadius - seed.clearanceOverRadius()) > 1.0e-12) {
				throw new IllegalArgumentException("seeds must belong to the requested preset/surface/clearance group.");
			}
			if (seed.rotorIndex() >= 0 && seed.rotorIndex() < EXPECTED_ROTOR_SEED_COUNT) {
				if (!observedRotors.add(seed.rotorIndex())) {
					throw new IllegalArgumentException("duplicate rotor seed in group: " + seed.rotorIndex());
				}
			} else {
				unexpected++;
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
			clearanceMetersSum += seed.clearanceMeters();
			targetPressureSum += seed.targetPressurePascals();
			pressureErrorRatioSum += seed.pressureErrorRatio();
			maxPressureErrorRatio = Math.max(maxPressureErrorRatio, seed.pressureErrorRatio());
			targetWakeVelocitySum += seed.targetWakeVelocityMetersPerSecond();
			wakeVelocityErrorRatioSum += seed.wakeVelocityErrorRatio();
			maxWakeVelocityErrorRatio = Math.max(maxWakeVelocityErrorRatio, seed.wakeVelocityErrorRatio());
			arrivalBandSum += seed.targetArrivalBandSeconds();
			arrivalBandErrorRatioSum += seed.arrivalBandErrorRatio();
			maxArrivalBandErrorRatio = Math.max(maxArrivalBandErrorRatio, seed.arrivalBandErrorRatio());
		}
		int observed = seeds.size();
		int missing = 0;
		for (int rotorIndex = 0; rotorIndex < EXPECTED_ROTOR_SEED_COUNT; rotorIndex++) {
			if (!observedRotors.contains(rotorIndex)) {
				missing++;
			}
		}
		boolean allPresent = missing == 0
				&& unexpected == 0
				&& observed == EXPECTED_ROTOR_SEED_COUNT;
		boolean allReady = ready == EXPECTED_ROTOR_SEED_COUNT
				&& unavailable == 0
				&& allPresent;
		boolean allPassed = passed == EXPECTED_ROTOR_SEED_COUNT
				&& failed == 0
				&& allReady;
		boolean candidate = allPresent && allReady && allPassed;
		return new PoweredHoverSurfaceWakeErrorBudgetGroup(
				presetName,
				spinState,
				surfaceType,
				clearanceOverRadius,
				average(clearanceMetersSum, observed),
				EXPECTED_ROTOR_SEED_COUNT,
				observed,
				missing,
				unexpected,
				ready,
				passed,
				failed,
				unavailable,
				allPresent,
				allReady,
				allPassed,
				average(targetPressureSum, observed),
				maxPressureErrorRatio,
				average(pressureErrorRatioSum, observed),
				average(targetWakeVelocitySum, observed),
				maxWakeVelocityErrorRatio,
				average(wakeVelocityErrorRatioSum, observed),
				average(arrivalBandSum, observed),
				maxArrivalBandErrorRatio,
				average(arrivalBandErrorRatioSum, observed),
				candidate,
				candidate ? "READY" : "BLOCKED",
				messageFor(allPresent, allReady, allPassed)
		);
	}

	private static String groupKey(
			Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed
	) {
		return seed.presetName() + ":" + seed.spinState() + ":" + seed.surfaceType() + ":hR"
				+ Double.toString(seed.clearanceOverRadius());
	}

	private static List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> concat(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> left,
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> right
	) {
		return java.util.stream.Stream.concat(left.stream(), right.stream()).toList();
	}

	private static String messageFor(boolean allPresent, boolean allReady, boolean allPassed) {
		if (!allPresent) {
			return "rotor-seed-set-incomplete";
		}
		if (!allReady) {
			return "rotor-seeds-not-ready";
		}
		if (!allPassed) {
			return "rotor-seeds-failed";
		}
		return "surface-wake-error-budget-ready";
	}

	private static PoweredHoverSurfaceWakeErrorBudgetExtrema extrema(
			List<PoweredHoverSurfaceWakeErrorBudgetGroup> groups
	) {
		int ground = 0;
		int ceiling = 0;
		int complete = 0;
		int ready = 0;
		int passed = 0;
		int candidates = 0;
		int maxMissing = 0;
		int maxUnexpected = 0;
		int maxUnavailable = 0;
		double maxPressureRatio = 0.0;
		double maxVelocityRatio = 0.0;
		double maxArrivalRatio = 0.0;
		double maxMeanPressure = 0.0;
		double maxMeanVelocity = 0.0;
		for (PoweredHoverSurfaceWakeErrorBudgetGroup group : groups) {
			if ("ground".equals(group.surfaceType())) {
				ground++;
			}
			if ("ceiling".equals(group.surfaceType())) {
				ceiling++;
			}
			if (group.allRotorSeedsPresent()) {
				complete++;
			}
			if (group.allRotorSeedsReady()) {
				ready++;
			}
			if (group.allRotorSeedsPassed()) {
				passed++;
			}
			if (group.validationCandidate()) {
				candidates++;
			}
			maxMissing = Math.max(maxMissing, group.missingRotorSeedCount());
			maxUnexpected = Math.max(maxUnexpected, group.unexpectedRotorSeedCount());
			maxUnavailable = Math.max(maxUnavailable, group.unavailableRotorSeedCount());
			maxPressureRatio = Math.max(maxPressureRatio, group.maxPressureErrorRatio());
			maxVelocityRatio = Math.max(maxVelocityRatio, group.maxWakeVelocityErrorRatio());
			maxArrivalRatio = Math.max(maxArrivalRatio, group.maxArrivalBandErrorRatio());
			maxMeanPressure = Math.max(maxMeanPressure, group.meanTargetPressurePascals());
			maxMeanVelocity = Math.max(maxMeanVelocity, group.meanTargetWakeVelocityMetersPerSecond());
		}
		return new PoweredHoverSurfaceWakeErrorBudgetExtrema(
				groups.size(),
				ground,
				ceiling,
				complete,
				ready,
				passed,
				candidates,
				groups.size() - candidates,
				maxMissing,
				maxUnexpected,
				maxUnavailable,
				maxPressureRatio,
				maxVelocityRatio,
				maxArrivalRatio,
				maxMeanPressure,
				maxMeanVelocity
		);
	}

	private static double average(double sum, int count) {
		return count > 0 ? sum / count : 0.0;
	}
}
