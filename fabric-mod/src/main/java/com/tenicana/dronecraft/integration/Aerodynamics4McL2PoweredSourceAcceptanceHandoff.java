package com.tenicana.dronecraft.integration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Aerodynamics4McL2PoweredSourceAcceptanceHandoff {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Acceptance-Handoff-Packet";
	public static final String CAVEAT =
			"Acceptance handoff forwards only complete passing hover and cruise validation-run candidates; current rows stay blocked while powered-source validation runs are skipped and keep their dominant blocker message.";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int HANDOFF_SAMPLE_COUNT = 2;
	public static final int HANDOFF_METRIC_COUNT = 22;
	public static final int SUMMARY_METRIC_ROW_COUNT = 9;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ HANDOFF_SAMPLE_COUNT * HANDOFF_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceAcceptanceHandoff() {
	}

	public record PoweredSourceAcceptanceHandoffSummary(
			String spinState,
			String validationPacketId,
			String acceptanceGatePacketId,
			int expectedPresetCount,
			int observedValidationRunCount,
			int validationSeedReadyCount,
			int validationInvokedCount,
			int validationPassedCount,
			int acceptanceCandidateCount,
			int skippedValidationRunCount,
			int skippedValidationBlockerCount,
			String dominantSkippedValidationMessage,
			int failedValidationRunCount,
			int missingPresetCount,
			int unexpectedPresetCount,
			double maxForceErrorRatio,
			double maxCenterOfForceErrorMeters,
			boolean allExpectedRunsPresent,
			boolean allCandidatesPassed,
			boolean acceptanceHandoffReady,
			String status,
			String message
	) {
	}

	public record PoweredSourceAcceptanceHandoffExtrema(
			int handoffCount,
			int readyHandoffCount,
			int blockedHandoffCount,
			int maxExpectedPresetCount,
			int maxMissingPresetCount,
			int maxAcceptanceCandidateCount,
			int maxSkippedValidationBlockerCount,
			double maxForceErrorRatio,
			double maxCenterOfForceErrorMeters
	) {
	}

	public record PoweredSourceAcceptanceHandoffAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int handoffSampleCount,
			int handoffMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceAcceptanceHandoffSummary> handoffs,
			PoweredSourceAcceptanceHandoffExtrema extrema
	) {
		public PoweredSourceAcceptanceHandoffAudit {
			handoffs = List.copyOf(handoffs);
		}
	}

	public static PoweredSourceAcceptanceHandoffAudit audit() {
		return audit(Aerodynamics4McL2PoweredSourceValidationRunMatrix.audit());
	}

	public static PoweredSourceAcceptanceHandoffAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(Aerodynamics4McL2PoweredSourceValidationRunMatrix.audit(loader));
	}

	public static PoweredSourceAcceptanceHandoffAudit audit(
			Aerodynamics4McL2PoweredSourceValidationRunMatrix.PoweredSourceValidationRunMatrixAudit validationRunMatrix
	) {
		if (validationRunMatrix == null) {
			throw new IllegalArgumentException("validationRunMatrix must not be null.");
		}
		List<PoweredSourceAcceptanceHandoffSummary> handoffs = List.of(
				handoff("hover", validationRunMatrix.runs()),
				handoff("cruise", validationRunMatrix.runs())
		);
		return new PoweredSourceAcceptanceHandoffAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				HANDOFF_SAMPLE_COUNT,
				HANDOFF_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				handoffs,
				extrema(handoffs)
		);
	}

	public static PoweredSourceAcceptanceHandoffSummary handoff(
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
		int seedReady = 0;
		int invoked = 0;
		int passed = 0;
		int candidates = 0;
		int skipped = 0;
		int skippedBlockers = 0;
		int failed = 0;
		int unexpected = 0;
		double maxForceRatio = 0.0;
		double maxCenterError = 0.0;
		Map<String, Integer> skippedMessages = new HashMap<>();
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
			if (run.validationSeedReady()) {
				seedReady++;
			}
			if (run.validationInvoked()) {
				invoked++;
			}
			if (run.validationPassed()) {
				passed++;
			}
			if (run.acceptanceResultCandidate()) {
				candidates++;
			}
			if ("SKIPPED".equals(run.status())) {
				skipped++;
				if (run.message() != null && !run.message().isBlank() && !"none".equals(run.message())) {
					skippedBlockers++;
					skippedMessages.merge(run.message(), 1, Integer::sum);
				}
			}
			if ("FAILED".equals(run.status())) {
				failed++;
			}
			maxForceRatio = Math.max(maxForceRatio, run.forceErrorRatio());
			maxCenterError = Math.max(maxCenterError, run.centerOfForceErrorMeters());
			if (!validationPacketId.equals(run.validationPacketId())
					|| !acceptanceGatePacketId.equals(run.acceptanceGatePacketId())) {
				unexpected++;
			}
		}
		int missing = 0;
		for (String presetName : expected) {
			if (!observed.contains(presetName)) {
				missing++;
			}
		}
		boolean allPresent = missing == 0 && unexpected == 0 && observedCount == expected.size();
		boolean allCandidatesPassed = candidates == expected.size()
				&& passed == expected.size()
				&& failed == 0
				&& skipped == 0;
		boolean ready = allPresent && allCandidatesPassed;
		String dominantSkippedMessage = dominantMessage(skippedMessages);
		String message = messageFor(ready, allPresent, dominantSkippedMessage);
		return new PoweredSourceAcceptanceHandoffSummary(
				spinState,
				validationPacketId,
				acceptanceGatePacketId,
				expected.size(),
				observedCount,
				seedReady,
				invoked,
				passed,
				candidates,
				skipped,
				skippedBlockers,
				dominantSkippedMessage,
				failed,
				missing,
				unexpected,
				maxForceRatio,
				maxCenterError,
				allPresent,
				allCandidatesPassed,
				ready,
				ready ? "READY" : "BLOCKED",
				message
		);
	}

	private static String dominantMessage(Map<String, Integer> messages) {
		String dominant = "none";
		int dominantCount = 0;
		for (Map.Entry<String, Integer> entry : messages.entrySet()) {
			String message = entry.getKey();
			int count = entry.getValue();
			if (count > dominantCount || (count == dominantCount && message.compareTo(dominant) < 0)) {
				dominant = message;
				dominantCount = count;
			}
		}
		return dominant;
	}

	private static String messageFor(boolean ready, boolean allPresent, String dominantSkippedMessage) {
		if (ready) {
			return "acceptance-handoff-ready";
		}
		if (!"none".equals(dominantSkippedMessage)) {
			return dominantSkippedMessage;
		}
		if (!allPresent) {
			return "validation-runs-not-ready";
		}
		return "acceptance-candidates-incomplete";
	}

	private static Set<String> expectedPresetNames() {
		return Set.of("racingQuad", "apDrone", "cinewhoop", "heavyLift");
	}

	private static PoweredSourceAcceptanceHandoffExtrema extrema(
			List<PoweredSourceAcceptanceHandoffSummary> handoffs
	) {
		int ready = 0;
		int maxExpected = 0;
		int maxMissing = 0;
		int maxCandidates = 0;
		int maxSkippedBlockers = 0;
		double maxForceRatio = 0.0;
		double maxCenterError = 0.0;
		for (PoweredSourceAcceptanceHandoffSummary handoff : handoffs) {
			if (handoff.acceptanceHandoffReady()) {
				ready++;
			}
			maxExpected = Math.max(maxExpected, handoff.expectedPresetCount());
			maxMissing = Math.max(maxMissing, handoff.missingPresetCount());
			maxCandidates = Math.max(maxCandidates, handoff.acceptanceCandidateCount());
			maxSkippedBlockers = Math.max(maxSkippedBlockers, handoff.skippedValidationBlockerCount());
			maxForceRatio = Math.max(maxForceRatio, handoff.maxForceErrorRatio());
			maxCenterError = Math.max(maxCenterError, handoff.maxCenterOfForceErrorMeters());
		}
		return new PoweredSourceAcceptanceHandoffExtrema(
				handoffs.size(),
				ready,
				handoffs.size() - ready,
				maxExpected,
				maxMissing,
				maxCandidates,
				maxSkippedBlockers,
				maxForceRatio,
				maxCenterError
		);
	}
}
