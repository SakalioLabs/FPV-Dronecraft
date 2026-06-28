package com.tenicana.dronecraft.integration;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Skew-Wake-Error-Budget-Packet";
	public static final String CAVEAT =
			"Aggregate error budget groups transient cruise skew-wake validation seeds by preset, axial plane, and sweep column; it diagnoses lab evidence quality and does not provide gameplay tuning parameters.";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int EXPECTED_ROTOR_SEED_COUNT = 4;
	public static final int GROUP_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.SEED_SAMPLE_COUNT / EXPECTED_ROTOR_SEED_COUNT;
	public static final int GROUP_METRIC_COUNT = 31;
	public static final int SUMMARY_METRIC_ROW_COUNT = 18;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ GROUP_SAMPLE_COUNT * GROUP_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget() {
	}

	public record PoweredCruiseSkewWakeErrorBudgetGroup(
			String presetName,
			String spinState,
			int axialPlaneIndex,
			int sweepColumnIndex,
			double axialPlaneFraction,
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
			double meanTargetResultantDynamicPressurePascals,
			double maxPressureErrorRatio,
			double meanPressureErrorRatio,
			double meanTargetResultantWakeVelocityMetersPerSecond,
			double maxWakeVelocityErrorRatio,
			double meanWakeVelocityErrorRatio,
			double meanArrivalBandSeconds,
			double maxArrivalWindowErrorRatio,
			double meanArrivalWindowErrorRatio,
			double meanTargetAxialMomentumFluxNewtons,
			double maxMomentumErrorRatio,
			double meanMomentumErrorRatio,
			boolean validationCandidate,
			String status,
			String message
	) {
	}

	public record PoweredCruiseSkewWakeErrorBudgetExtrema(
			int groupCount,
			int centerlineSweepGroupCount,
			int lateralSweepGroupCount,
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
			double maxArrivalWindowErrorRatio,
			double maxMomentumErrorRatio,
			double maxMeanTargetResultantDynamicPressurePascals,
			double maxMeanTargetResultantWakeVelocityMetersPerSecond,
			double maxMeanTargetAxialMomentumFluxNewtons
	) {
	}

	public record PoweredCruiseSkewWakeErrorBudgetAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int groupSampleCount,
			int expectedRotorSeedCount,
			int groupMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredCruiseSkewWakeErrorBudgetGroup> groups,
			PoweredCruiseSkewWakeErrorBudgetExtrema extrema
	) {
		public PoweredCruiseSkewWakeErrorBudgetAudit {
			groups = List.copyOf(groups);
		}
	}

	public static PoweredCruiseSkewWakeErrorBudgetAudit audit() {
		return audit(Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.audit());
	}

	public static PoweredCruiseSkewWakeErrorBudgetAudit audit(
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeResultSeedAudit seedAudit
	) {
		if (seedAudit == null) {
			throw new IllegalArgumentException("seedAudit must not be null.");
		}
		Map<String, List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed>> byGroup =
				new LinkedHashMap<>();
		for (Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed
				: seedAudit.seeds()) {
			if (seed == null) {
				throw new IllegalArgumentException("seedAudit must not contain null seeds.");
			}
			byGroup.merge(groupKey(seed), List.of(seed), Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget::concat);
		}
		List<PoweredCruiseSkewWakeErrorBudgetGroup> groups = byGroup.values().stream()
				.map(seeds -> group(
						seeds.get(0).presetName(),
						seeds.get(0).spinState(),
						seeds.get(0).axialPlaneIndex(),
						seeds.get(0).sweepColumnIndex(),
						seeds
				))
				.toList();
		return new PoweredCruiseSkewWakeErrorBudgetAudit(
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

	public static PoweredCruiseSkewWakeErrorBudgetGroup group(
			String presetName,
			String spinState,
			int axialPlaneIndex,
			int sweepColumnIndex,
			List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> seeds
	) {
		if (presetName == null || presetName.isBlank()) {
			throw new IllegalArgumentException("presetName must not be blank.");
		}
		if (!"cruise".equals(spinState)) {
			throw new IllegalArgumentException("spinState must be cruise.");
		}
		if (axialPlaneIndex < 1 || axialPlaneIndex > 4) {
			throw new IllegalArgumentException("axialPlaneIndex must be between 1 and 4.");
		}
		if (sweepColumnIndex < -1 || sweepColumnIndex > 1) {
			throw new IllegalArgumentException("sweepColumnIndex must be -1, 0, or 1.");
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
		double axialPlaneFractionSum = 0.0;
		double targetPressureSum = 0.0;
		double pressureErrorRatioSum = 0.0;
		double maxPressureErrorRatio = 0.0;
		double targetWakeVelocitySum = 0.0;
		double wakeVelocityErrorRatioSum = 0.0;
		double maxWakeVelocityErrorRatio = 0.0;
		double arrivalBandSum = 0.0;
		double arrivalErrorRatioSum = 0.0;
		double maxArrivalErrorRatio = 0.0;
		double targetMomentumSum = 0.0;
		double momentumErrorRatioSum = 0.0;
		double maxMomentumErrorRatio = 0.0;
		for (Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed : seeds) {
			if (seed == null) {
				throw new IllegalArgumentException("seeds must not contain null entries.");
			}
			if (!presetName.equals(seed.presetName())
					|| !spinState.equals(seed.spinState())
					|| axialPlaneIndex != seed.axialPlaneIndex()
					|| sweepColumnIndex != seed.sweepColumnIndex()) {
				throw new IllegalArgumentException("seeds must belong to the requested preset/plane/sweep group.");
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
			axialPlaneFractionSum += seed.axialPlaneFraction();
			targetPressureSum += seed.targetResultantDynamicPressurePascals();
			pressureErrorRatioSum += seed.pressureErrorRatio();
			maxPressureErrorRatio = Math.max(maxPressureErrorRatio, seed.pressureErrorRatio());
			targetWakeVelocitySum += seed.targetResultantWakeVelocityMetersPerSecond();
			wakeVelocityErrorRatioSum += seed.wakeVelocityErrorRatio();
			maxWakeVelocityErrorRatio = Math.max(maxWakeVelocityErrorRatio, seed.wakeVelocityErrorRatio());
			arrivalBandSum += seed.targetArrivalBandSeconds();
			arrivalErrorRatioSum += seed.arrivalWindowErrorRatio();
			maxArrivalErrorRatio = Math.max(maxArrivalErrorRatio, seed.arrivalWindowErrorRatio());
			targetMomentumSum += seed.targetAxialMomentumFluxNewtons();
			momentumErrorRatioSum += seed.momentumErrorRatio();
			maxMomentumErrorRatio = Math.max(maxMomentumErrorRatio, seed.momentumErrorRatio());
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
		return new PoweredCruiseSkewWakeErrorBudgetGroup(
				presetName,
				spinState,
				axialPlaneIndex,
				sweepColumnIndex,
				average(axialPlaneFractionSum, observed),
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
				maxArrivalErrorRatio,
				average(arrivalErrorRatioSum, observed),
				average(targetMomentumSum, observed),
				maxMomentumErrorRatio,
				average(momentumErrorRatioSum, observed),
				candidate,
				candidate ? "READY" : "BLOCKED",
				messageFor(allPresent, allReady, allPassed)
		);
	}

	private static String groupKey(
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed
	) {
		return seed.presetName() + ":" + seed.spinState()
				+ ":plane" + seed.axialPlaneIndex()
				+ ":sweep" + seed.sweepColumnIndex();
	}

	private static List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> concat(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> left,
			List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> right
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
		return "cruise-skew-wake-error-budget-ready";
	}

	private static PoweredCruiseSkewWakeErrorBudgetExtrema extrema(
			List<PoweredCruiseSkewWakeErrorBudgetGroup> groups
	) {
		int centerline = 0;
		int lateral = 0;
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
		double maxMomentumRatio = 0.0;
		double maxMeanPressure = 0.0;
		double maxMeanVelocity = 0.0;
		double maxMeanMomentum = 0.0;
		for (PoweredCruiseSkewWakeErrorBudgetGroup group : groups) {
			if (group.sweepColumnIndex() == 0) {
				centerline++;
			} else {
				lateral++;
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
			maxArrivalRatio = Math.max(maxArrivalRatio, group.maxArrivalWindowErrorRatio());
			maxMomentumRatio = Math.max(maxMomentumRatio, group.maxMomentumErrorRatio());
			maxMeanPressure = Math.max(maxMeanPressure, group.meanTargetResultantDynamicPressurePascals());
			maxMeanVelocity = Math.max(maxMeanVelocity, group.meanTargetResultantWakeVelocityMetersPerSecond());
			maxMeanMomentum = Math.max(maxMeanMomentum, group.meanTargetAxialMomentumFluxNewtons());
		}
		return new PoweredCruiseSkewWakeErrorBudgetExtrema(
				groups.size(),
				centerline,
				lateral,
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
				maxMomentumRatio,
				maxMeanPressure,
				maxMeanVelocity,
				maxMeanMomentum
		);
	}

	private static double average(double sum, int count) {
		return count > 0 ? sum / count : 0.0;
	}
}
