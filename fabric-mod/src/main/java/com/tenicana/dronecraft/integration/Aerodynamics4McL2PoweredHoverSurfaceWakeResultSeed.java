package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Surface-Wake-Result-Seed-Packet";
	public static final String CAVEAT =
			"Result seed pairs surface-wake validation run rows with pressure, velocity, and arrival-time targets; validation remains unavailable until live A4MC transient probe evidence exists for all three channels.";
	public static final double PRESSURE_RELATIVE_TOLERANCE = 0.20;
	public static final double WAKE_VELOCITY_RELATIVE_TOLERANCE = 0.15;
	public static final double ARRIVAL_BAND_TOLERANCE_FRACTION = 0.10;
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int SEED_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.RUN_SAMPLE_COUNT;
	public static final int SEED_METRIC_COUNT = 38;
	public static final int SUMMARY_METRIC_ROW_COUNT = 14;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SEED_SAMPLE_COUNT * SEED_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed() {
	}

	public record PoweredHoverSurfaceWakeValidationSeed(
			String presetName,
			String spinState,
			String surfaceType,
			int rotorIndex,
			double clearanceOverRadius,
			double clearanceMeters,
			boolean validationRunAvailable,
			boolean pressureEvidenceAvailable,
			boolean velocityEvidenceAvailable,
			boolean transitEvidenceAvailable,
			boolean validationSeedReady,
			double targetPressurePascals,
			double observedPressurePascals,
			double pressureErrorPascals,
			double pressureErrorRatio,
			double targetWakeVelocityMetersPerSecond,
			double observedWakeVelocityMetersPerSecond,
			double wakeVelocityErrorMetersPerSecond,
			double wakeVelocityErrorRatio,
			double targetFastArrivalSeconds,
			double targetSlowArrivalSeconds,
			double targetArrivalBandSeconds,
			double observedArrivalTimeSeconds,
			double arrivalBandErrorSeconds,
			double arrivalBandErrorRatio,
			double pressureToleranceRatio,
			double velocityToleranceRatio,
			double arrivalBandToleranceFraction,
			boolean pressureValidationPassed,
			boolean velocityValidationPassed,
			boolean arrivalTimeValidationPassed,
			boolean validationPassed,
			String status,
			String message,
			String runStatus,
			String runMessage,
			String runRuntimeInfo,
			String resultRuntimeInfo
	) {
	}

	public record PoweredHoverSurfaceWakeResultSeedExtrema(
			int validationSeedCount,
			int groundSeedCount,
			int ceilingSeedCount,
			int validationRunAvailableCount,
			int pressureEvidenceCount,
			int velocityEvidenceCount,
			int transitEvidenceCount,
			int validationSeedReadyCount,
			int validationPassedCount,
			double maxPressureErrorRatio,
			double maxWakeVelocityErrorRatio,
			double maxArrivalBandErrorRatio,
			double maxTargetPressurePascals,
			double maxTargetWakeVelocityMetersPerSecond
	) {
	}

	public record PoweredHoverSurfaceWakeResultSeedAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int seedSampleCount,
			int seedMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverSurfaceWakeValidationSeed> seeds,
			PoweredHoverSurfaceWakeResultSeedExtrema extrema
	) {
		public PoweredHoverSurfaceWakeResultSeedAudit {
			seeds = List.copyOf(seeds);
		}
	}

	public static PoweredHoverSurfaceWakeResultSeedAudit audit() {
		return audit(Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.audit());
	}

	public static PoweredHoverSurfaceWakeResultSeedAudit audit(
			Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRunMatrixAudit runMatrix
	) {
		if (runMatrix == null) {
			throw new IllegalArgumentException("run matrix is required.");
		}
		List<PoweredHoverSurfaceWakeValidationSeed> seeds = runMatrix.runs().stream()
				.map(Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed::seed)
				.toList();
		return new PoweredHoverSurfaceWakeResultSeedAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SEED_SAMPLE_COUNT,
				SEED_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				seeds,
				extrema(seeds)
		);
	}

	public static PoweredHoverSurfaceWakeValidationSeed seed(
			Aerodynamics4McL2PoweredHoverSurfaceWakeValidationRunMatrix.PoweredHoverSurfaceWakeValidationRun run
	) {
		if (run == null) {
			throw new IllegalArgumentException("run must not be null.");
		}
		boolean pressureEvidence = run.available() && run.hasPressureEvidence();
		boolean velocityEvidence = run.available() && run.hasVelocityEvidence();
		boolean transitEvidence = run.available() && run.hasTransitEvidence();
		boolean seedReady = pressureEvidence && velocityEvidence && transitEvidence;
		double pressureError = seedReady
				? Math.abs(run.observedPressurePascals() - run.perRotorImpingementPressurePascals())
				: 0.0;
		double pressureErrorRatio = seedReady
				? ratio(pressureError, run.perRotorImpingementPressurePascals())
				: 0.0;
		double velocityError = seedReady
				? Math.abs(run.observedWakeVelocityMetersPerSecond() - run.farWakeVelocityMetersPerSecond())
				: 0.0;
		double velocityErrorRatio = seedReady
				? ratio(velocityError, run.farWakeVelocityMetersPerSecond())
				: 0.0;
		double arrivalBand = Math.max(0.0, run.slowDiskPlaneTransitSeconds() - run.fastFarWakeTransitSeconds());
		double arrivalError = seedReady
				? arrivalBandError(run.observedArrivalTimeSeconds(),
						run.fastFarWakeTransitSeconds(),
						run.slowDiskPlaneTransitSeconds())
				: 0.0;
		double arrivalErrorRatio = seedReady ? ratio(arrivalError, arrivalBand) : 0.0;
		boolean pressurePassed = seedReady && pressureErrorRatio <= PRESSURE_RELATIVE_TOLERANCE;
		boolean velocityPassed = seedReady && velocityErrorRatio <= WAKE_VELOCITY_RELATIVE_TOLERANCE;
		boolean arrivalPassed = seedReady && arrivalErrorRatio <= ARRIVAL_BAND_TOLERANCE_FRACTION;
		boolean passed = pressurePassed && velocityPassed && arrivalPassed;
		return new PoweredHoverSurfaceWakeValidationSeed(
				run.presetName(),
				run.spinState(),
				run.surfaceType(),
				run.rotorIndex(),
				run.clearanceOverRadius(),
				run.clearanceMeters(),
				run.available(),
				pressureEvidence,
				velocityEvidence,
				transitEvidence,
				seedReady,
				run.perRotorImpingementPressurePascals(),
				seedReady ? run.observedPressurePascals() : 0.0,
				pressureError,
				pressureErrorRatio,
				run.farWakeVelocityMetersPerSecond(),
				seedReady ? run.observedWakeVelocityMetersPerSecond() : 0.0,
				velocityError,
				velocityErrorRatio,
				run.fastFarWakeTransitSeconds(),
				run.slowDiskPlaneTransitSeconds(),
				arrivalBand,
				seedReady ? run.observedArrivalTimeSeconds() : 0.0,
				arrivalError,
				arrivalErrorRatio,
				PRESSURE_RELATIVE_TOLERANCE,
				WAKE_VELOCITY_RELATIVE_TOLERANCE,
				ARRIVAL_BAND_TOLERANCE_FRACTION,
				pressurePassed,
				velocityPassed,
				arrivalPassed,
				passed,
				seedReady ? (passed ? "READY_PASS" : "READY_FAIL") : "UNAVAILABLE",
				messageFor(run.available(), pressureEvidence, velocityEvidence, transitEvidence, passed),
				run.status(),
				run.message(),
				run.runtimeInfo(),
				"audit-only-unvalidated-surface-wake-result-seed"
		);
	}

	private static String messageFor(
			boolean runAvailable,
			boolean pressureEvidence,
			boolean velocityEvidence,
			boolean transitEvidence,
			boolean passed
	) {
		if (!runAvailable) {
			return "surface-wake-run-unavailable";
		}
		if (!pressureEvidence) {
			return "pressure-evidence-missing";
		}
		if (!velocityEvidence) {
			return "velocity-evidence-missing";
		}
		if (!transitEvidence) {
			return "arrival-time-evidence-missing";
		}
		return passed ? "surface-wake-validation-seed-passed" : "surface-wake-validation-seed-failed";
	}

	private static double arrivalBandError(double observed, double fast, double slow) {
		if (observed < fast) {
			return fast - observed;
		}
		if (observed > slow) {
			return observed - slow;
		}
		return 0.0;
	}

	private static PoweredHoverSurfaceWakeResultSeedExtrema extrema(
			List<PoweredHoverSurfaceWakeValidationSeed> seeds
	) {
		int ground = 0;
		int ceiling = 0;
		int runAvailable = 0;
		int pressureEvidence = 0;
		int velocityEvidence = 0;
		int transitEvidence = 0;
		int seedReady = 0;
		int passed = 0;
		double maxPressureErrorRatio = 0.0;
		double maxVelocityErrorRatio = 0.0;
		double maxArrivalErrorRatio = 0.0;
		double maxTargetPressure = 0.0;
		double maxTargetVelocity = 0.0;
		for (PoweredHoverSurfaceWakeValidationSeed seed : seeds) {
			if ("ground".equals(seed.surfaceType())) {
				ground++;
			}
			if ("ceiling".equals(seed.surfaceType())) {
				ceiling++;
			}
			if (seed.validationRunAvailable()) {
				runAvailable++;
			}
			if (seed.pressureEvidenceAvailable()) {
				pressureEvidence++;
			}
			if (seed.velocityEvidenceAvailable()) {
				velocityEvidence++;
			}
			if (seed.transitEvidenceAvailable()) {
				transitEvidence++;
			}
			if (seed.validationSeedReady()) {
				seedReady++;
			}
			if (seed.validationPassed()) {
				passed++;
			}
			maxPressureErrorRatio = Math.max(maxPressureErrorRatio, seed.pressureErrorRatio());
			maxVelocityErrorRatio = Math.max(maxVelocityErrorRatio, seed.wakeVelocityErrorRatio());
			maxArrivalErrorRatio = Math.max(maxArrivalErrorRatio, seed.arrivalBandErrorRatio());
			maxTargetPressure = Math.max(maxTargetPressure, seed.targetPressurePascals());
			maxTargetVelocity = Math.max(maxTargetVelocity, seed.targetWakeVelocityMetersPerSecond());
		}
		return new PoweredHoverSurfaceWakeResultSeedExtrema(
				seeds.size(),
				ground,
				ceiling,
				runAvailable,
				pressureEvidence,
				velocityEvidence,
				transitEvidence,
				seedReady,
				passed,
				maxPressureErrorRatio,
				maxVelocityErrorRatio,
				maxArrivalErrorRatio,
				maxTargetPressure,
				maxTargetVelocity
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
