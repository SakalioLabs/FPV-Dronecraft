package com.tenicana.dronecraft.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Aerodynamics4McL2PoweredSourceValidationErrorBudget {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Validation-Error-Budget-Packet";
	public static final String CAVEAT =
			"Validation error budget aggregates powered-source hover and cruise validation runs by spin state; it diagnoses lab evidence quality and does not provide gameplay tuning parameters.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int GROUP_SAMPLE_COUNT = 2;
	public static final int EXPECTED_PRESET_COUNT = 4;
	public static final int GROUP_METRIC_COUNT = 26;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ GROUP_SAMPLE_COUNT * GROUP_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceValidationErrorBudget() {
	}

	public record PoweredSourceValidationErrorBudgetGroup(
			String spinState,
			String validationPacketId,
			String acceptanceGatePacketId,
			int expectedPresetCount,
			int observedValidationRunCount,
			int validationSeedReadyCount,
			int validationInvokedCount,
			int validationPassedCount,
			int skippedValidationRunCount,
			int failedValidationRunCount,
			int acceptanceCandidateCount,
			int missingPresetCount,
			int unexpectedPresetCount,
			boolean allExpectedRunsPresent,
			boolean allValidationSeedsReady,
			boolean allValidationPassed,
			boolean allAcceptanceCandidates,
			double maxForceErrorRatio,
			double maxForceErrorNewtons,
			double maxMomentErrorNewtonMeters,
			double maxCenterOfForceErrorMeters,
			double meanTargetForceMagnitudeNewtons,
			double meanTargetMomentMagnitudeNewtonMeters,
			boolean validationBudgetCandidate,
			String status,
			String message
	) {
	}

	public record PoweredSourceValidationErrorBudgetExtrema(
			int groupCount,
			int validationBudgetCandidateCount,
			int blockedGroupCount,
			int maxMissingPresetCount,
			int maxUnexpectedPresetCount,
			int maxSkippedValidationRunCount,
			int maxFailedValidationRunCount,
			double maxForceErrorRatio,
			double maxForceErrorNewtons,
			double maxMomentErrorNewtonMeters,
			double maxCenterOfForceErrorMeters,
			double maxMeanTargetForceMagnitudeNewtons
	) {
	}

	public record PoweredSourceValidationErrorBudgetAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int groupSampleCount,
			int expectedPresetCount,
			int groupMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceValidationErrorBudgetGroup> groups,
			PoweredSourceValidationErrorBudgetExtrema extrema
	) {
		public PoweredSourceValidationErrorBudgetAudit {
			groups = List.copyOf(groups);
		}
	}

	public static PoweredSourceValidationErrorBudgetAudit audit() {
		return audit(Aerodynamics4McL2PoweredSourceValidationRunMatrix.audit());
	}

	public static PoweredSourceValidationErrorBudgetAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(Aerodynamics4McL2PoweredSourceValidationRunMatrix.audit(loader));
	}

	public static PoweredSourceValidationErrorBudgetAudit audit(
			Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunMatrixAudit validationRunMatrix
	) {
		if (validationRunMatrix == null) {
			throw new IllegalArgumentException("validationRunMatrix must not be null.");
		}
		List<PoweredSourceValidationErrorBudgetGroup> groups = List.of(
				group("hover", validationRunMatrix.runs()),
				group("cruise", validationRunMatrix.runs())
		);
		return new PoweredSourceValidationErrorBudgetAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				GROUP_SAMPLE_COUNT,
				EXPECTED_PRESET_COUNT,
				GROUP_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				groups,
				extrema(groups)
		);
	}

	public static PoweredSourceValidationErrorBudgetGroup group(
			String spinState,
			List<Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary> runs
	) {
		if (!"hover".equals(spinState) && !"cruise".equals(spinState)) {
			throw new IllegalArgumentException("spinState must be hover or cruise.");
		}
		if (runs == null) {
			throw new IllegalArgumentException("runs must not be null.");
		}
		Set<String> expected = expectedPresetNames();
		Set<String> observed = new HashSet<>();
		int observedCount = 0;
		int ready = 0;
		int invoked = 0;
		int passed = 0;
		int skipped = 0;
		int failed = 0;
		int candidates = 0;
		int unexpected = 0;
		double forceRatio = 0.0;
		double forceError = 0.0;
		double momentError = 0.0;
		double centerError = 0.0;
		double targetForceSum = 0.0;
		double targetMomentSum = 0.0;
		String validationPacketId = "hover".equals(spinState)
				? Aerodynamics4McL2PoweredHoverValidation.SOURCE_ID
				: Aerodynamics4McL2PoweredCruiseValidation.SOURCE_ID;
		String acceptanceGatePacketId = "hover".equals(spinState)
				? Aerodynamics4McL2PoweredHoverAcceptanceGate.SOURCE_ID
				: Aerodynamics4McL2PoweredCruiseAcceptanceGate.SOURCE_ID;
		for (Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunSummary run : runs) {
			if (run == null) {
				throw new IllegalArgumentException("runs must not contain null entries.");
			}
			if (!spinState.equals(run.spinState())) {
				continue;
			}
			if (run.presetName() == null || run.presetName().isBlank()) {
				throw new IllegalArgumentException("validation runs must include stable preset names.");
			}
			if (!observed.add(run.presetName())) {
				throw new IllegalArgumentException("duplicate validation run preset for " + spinState + ": "
						+ run.presetName());
			}
			observedCount++;
			if (!expected.contains(run.presetName())) {
				unexpected++;
			}
			if (!validationPacketId.equals(run.validationPacketId())
					|| !acceptanceGatePacketId.equals(run.acceptanceGatePacketId())) {
				unexpected++;
			}
			if (run.validationSeedReady()) {
				ready++;
			}
			if (run.validationInvoked()) {
				invoked++;
			}
			if (run.validationPassed()) {
				passed++;
			}
			if ("SKIPPED".equals(run.status())) {
				skipped++;
			}
			if ("FAILED".equals(run.status())) {
				failed++;
			}
			if (run.acceptanceResultCandidate()) {
				candidates++;
			}
			forceRatio = Math.max(forceRatio, run.forceErrorRatio());
			forceError = Math.max(forceError, run.forceErrorNewtons());
			momentError = Math.max(momentError, run.momentErrorNewtonMeters());
			centerError = Math.max(centerError, run.centerOfForceErrorMeters());
			targetForceSum += run.targetForceMagnitudeNewtons();
			targetMomentSum += run.targetMomentMagnitudeNewtonMeters();
		}
		int missing = 0;
		for (String presetName : expected) {
			if (!observed.contains(presetName)) {
				missing++;
			}
		}
		boolean allPresent = missing == 0 && unexpected == 0 && observedCount == expected.size();
		boolean allReady = allPresent && ready == expected.size();
		boolean allPassed = allReady && passed == expected.size() && failed == 0 && skipped == 0;
		boolean allCandidates = allPassed && candidates == expected.size();
		boolean candidate = allPresent && allReady && allPassed && allCandidates;
		return new PoweredSourceValidationErrorBudgetGroup(
				spinState,
				validationPacketId,
				acceptanceGatePacketId,
				expected.size(),
				observedCount,
				ready,
				invoked,
				passed,
				skipped,
				failed,
				candidates,
				missing,
				unexpected,
				allPresent,
				allReady,
				allPassed,
				allCandidates,
				forceRatio,
				forceError,
				momentError,
				centerError,
				average(targetForceSum, observedCount),
				average(targetMomentSum, observedCount),
				candidate,
				candidate ? "READY" : "BLOCKED",
				messageFor(allPresent, allReady, allPassed, allCandidates)
		);
	}

	private static String messageFor(
			boolean allPresent,
			boolean allReady,
			boolean allPassed,
			boolean allCandidates
	) {
		if (!allPresent) {
			return "validation-run-set-incomplete";
		}
		if (!allReady) {
			return "validation-seeds-not-ready";
		}
		if (!allPassed) {
			return "validation-runs-not-passing";
		}
		if (!allCandidates) {
			return "acceptance-candidates-incomplete";
		}
		return "powered-source-validation-error-budget-ready";
	}

	private static PoweredSourceValidationErrorBudgetExtrema extrema(
			List<PoweredSourceValidationErrorBudgetGroup> groups
	) {
		int candidates = 0;
		int maxMissing = 0;
		int maxUnexpected = 0;
		int maxSkipped = 0;
		int maxFailed = 0;
		double maxForceRatio = 0.0;
		double maxForceError = 0.0;
		double maxMomentError = 0.0;
		double maxCenterError = 0.0;
		double maxTargetForce = 0.0;
		for (PoweredSourceValidationErrorBudgetGroup group : groups) {
			if (group.validationBudgetCandidate()) {
				candidates++;
			}
			maxMissing = Math.max(maxMissing, group.missingPresetCount());
			maxUnexpected = Math.max(maxUnexpected, group.unexpectedPresetCount());
			maxSkipped = Math.max(maxSkipped, group.skippedValidationRunCount());
			maxFailed = Math.max(maxFailed, group.failedValidationRunCount());
			maxForceRatio = Math.max(maxForceRatio, group.maxForceErrorRatio());
			maxForceError = Math.max(maxForceError, group.maxForceErrorNewtons());
			maxMomentError = Math.max(maxMomentError, group.maxMomentErrorNewtonMeters());
			maxCenterError = Math.max(maxCenterError, group.maxCenterOfForceErrorMeters());
			maxTargetForce = Math.max(maxTargetForce, group.meanTargetForceMagnitudeNewtons());
		}
		return new PoweredSourceValidationErrorBudgetExtrema(
				groups.size(),
				candidates,
				groups.size() - candidates,
				maxMissing,
				maxUnexpected,
				maxSkipped,
				maxFailed,
				maxForceRatio,
				maxForceError,
				maxMomentError,
				maxCenterError,
				maxTargetForce
		);
	}

	private static Set<String> expectedPresetNames() {
		return Set.of("racingQuad", "apDrone", "cinewhoop", "heavyLift");
	}

	private static double average(double sum, int count) {
		return count > 0 ? sum / count : 0.0;
	}
}
