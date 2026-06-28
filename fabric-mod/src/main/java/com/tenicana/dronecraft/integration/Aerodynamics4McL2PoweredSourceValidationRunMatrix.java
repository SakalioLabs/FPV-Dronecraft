package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceValidationRunMatrix {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Validation-Run-Matrix-Packet";
	public static final String CAVEAT =
			"Validation run matrix consumes powered-source result seeds only when baseline-subtracted force/moment evidence is ready; unavailable seeds keep their powered-run blocker message, stay skipped, and cannot feed acceptance gates.";
	public static final int SOURCE_REFERENCE_COUNT = 8;
	public static final int VALIDATION_SAMPLE_COUNT = 8;
	public static final int VALIDATION_METRIC_COUNT = 25;
	public static final int SUMMARY_METRIC_ROW_COUNT = 11;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ VALIDATION_SAMPLE_COUNT * VALIDATION_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceValidationRunMatrix() {
	}

	public record PoweredSourceValidationRunSummary(
			String presetName,
			String spinState,
			String validationPacketId,
			String acceptanceGatePacketId,
			boolean validationSeedReady,
			boolean baselineSubtractedDeltaReady,
			boolean validationInvoked,
			boolean validationPassed,
			boolean hoverValidationTarget,
			boolean cruiseValidationTarget,
			double forceDeltaMagnitudeNewtons,
			double targetForceMagnitudeNewtons,
			double forceErrorNewtons,
			double forceErrorRatio,
			double momentDeltaMagnitudeNewtonMeters,
			double targetMomentMagnitudeNewtonMeters,
			double momentErrorNewtonMeters,
			double centerOfForceOffsetMeters,
			double targetCenterOfForceOffsetMeters,
			double centerOfForceErrorMeters,
			boolean acceptanceResultCandidate,
			String status,
			String message,
			String staticRuntimeInfo,
			String poweredRuntimeInfo
	) {
	}

	public record PoweredSourceValidationRunExtrema(
			int validationRunCount,
			int hoverValidationRunCount,
			int cruiseValidationRunCount,
			int validationSeedReadyCount,
			int validationInvokedCount,
			int validationPassedCount,
			int validationSkippedCount,
			int validationFailedCount,
			int acceptanceResultCandidateCount,
			double maxForceErrorRatio,
			double maxCenterOfForceErrorMeters
	) {
	}

	public record PoweredSourceValidationRunMatrixAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int validationSampleCount,
			int validationMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceValidationRunSummary> runs,
			PoweredSourceValidationRunExtrema extrema
	) {
		public PoweredSourceValidationRunMatrixAudit {
			runs = List.copyOf(runs);
		}
	}

	public static PoweredSourceValidationRunMatrixAudit audit() {
		return audit(Aerodynamics4McL2PoweredSourceResultSeed.audit());
	}

	public static PoweredSourceValidationRunMatrixAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(Aerodynamics4McL2PoweredSourceResultSeed.audit(loader));
	}

	public static PoweredSourceValidationRunMatrixAudit audit(
			Aerodynamics4McL2PoweredSourceResultSeed.PoweredSourceResultSeedAudit seedAudit
	) {
		if (seedAudit == null) {
			throw new IllegalArgumentException("seedAudit must not be null.");
		}
		List<PoweredSourceValidationRunSummary> runs = seedAudit.seeds().stream()
				.map(Aerodynamics4McL2PoweredSourceValidationRunMatrix::summary)
				.toList();
		return new PoweredSourceValidationRunMatrixAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				VALIDATION_SAMPLE_COUNT,
				VALIDATION_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				runs,
				extrema(runs)
		);
	}

	public static PoweredSourceValidationRunSummary summary(
			Aerodynamics4McL2PoweredSourceResultSeed.PoweredSourceValidationSeed seed
	) {
		if (seed == null) {
			throw new IllegalArgumentException("seed must not be null.");
		}
		boolean validationInvoked = seed.validationSeedReady();
		boolean accepted = validationInvoked && seed.validationPassed();
		String status = !validationInvoked ? "SKIPPED" : (seed.validationPassed() ? "PASSED" : "FAILED");
		String message = !validationInvoked
				? seed.message()
				: (seed.validationPassed() ? "validation-result-ready" : "validation-result-failed");
		return new PoweredSourceValidationRunSummary(
				seed.presetName(),
				seed.spinState(),
				seed.validationPacketId(),
				seed.acceptanceGatePacketId(),
				seed.validationSeedReady(),
				seed.baselineSubtractedDeltaReady(),
				validationInvoked,
				seed.validationPassed(),
				"hover".equals(seed.spinState()),
				"cruise".equals(seed.spinState()),
				seed.poweredForceDeltaMagnitudeNewtons(),
				seed.targetForceMagnitudeNewtons(),
				seed.forceErrorNewtons(),
				seed.forceErrorRatio(),
				seed.poweredMomentDeltaMagnitudeNewtonMeters(),
				seed.targetMomentMagnitudeNewtonMeters(),
				seed.momentErrorNewtonMeters(),
				seed.poweredCenterOfForceOffsetMeters(),
				seed.targetCenterOfForceOffsetMeters(),
				seed.centerOfForceErrorMeters(),
				accepted,
				status,
				message,
				seed.staticRuntimeInfo(),
				seed.poweredRuntimeInfo()
		);
	}

	private static PoweredSourceValidationRunExtrema extrema(List<PoweredSourceValidationRunSummary> runs) {
		int hover = 0;
		int cruise = 0;
		int ready = 0;
		int invoked = 0;
		int passed = 0;
		int skipped = 0;
		int failed = 0;
		int candidates = 0;
		double maxForceRatio = 0.0;
		double maxCenterError = 0.0;
		for (PoweredSourceValidationRunSummary run : runs) {
			if (run.hoverValidationTarget()) {
				hover++;
			}
			if (run.cruiseValidationTarget()) {
				cruise++;
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
			maxForceRatio = Math.max(maxForceRatio, run.forceErrorRatio());
			maxCenterError = Math.max(maxCenterError, run.centerOfForceErrorMeters());
		}
		return new PoweredSourceValidationRunExtrema(
				runs.size(),
				hover,
				cruise,
				ready,
				invoked,
				passed,
				skipped,
				failed,
				candidates,
				maxForceRatio,
				maxCenterError
		);
	}
}
