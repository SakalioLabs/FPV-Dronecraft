package com.tenicana.dronecraft.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Skew-Wake-Result-Seed-Packet";
	public static final String CAVEAT =
			"Result seed pairs cruise skew-wake validation run rows with pressure, velocity, arrival-time, and momentum targets; validation remains unavailable until live A4MC transient skew-wake probe evidence exists for all four channels.";
	public static final double PRESSURE_RELATIVE_TOLERANCE = 0.20;
	public static final double WAKE_VELOCITY_RELATIVE_TOLERANCE = 0.15;
	public static final double ARRIVAL_TIME_TOLERANCE_FRACTION = 0.10;
	public static final double MOMENTUM_RELATIVE_TOLERANCE = 0.15;
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int SEED_SAMPLE_COUNT =
			Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.RUN_SAMPLE_COUNT;
	public static final int SEED_METRIC_COUNT = 47;
	public static final int SUMMARY_METRIC_ROW_COUNT = 18;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SEED_SAMPLE_COUNT * SEED_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed() {
	}

	public record PoweredCruiseSkewWakeValidationSeed(
			String presetName,
			String spinState,
			int rotorIndex,
			int axialPlaneIndex,
			int sweepColumnIndex,
			double axialPlaneFraction,
			boolean validationRunAvailable,
			boolean pressureEvidenceAvailable,
			boolean velocityEvidenceAvailable,
			boolean transitEvidenceAvailable,
			boolean momentumEvidenceAvailable,
			boolean validationSeedReady,
			double targetAxialWakePressurePascals,
			double targetResultantDynamicPressurePascals,
			double observedPressurePascals,
			double pressureErrorPascals,
			double pressureErrorRatio,
			double targetResultantWakeVelocityMetersPerSecond,
			double observedWakeVelocityMetersPerSecond,
			double wakeVelocityErrorMetersPerSecond,
			double wakeVelocityErrorRatio,
			double targetCenterlineArrivalSeconds,
			double targetLateralArrivalSeconds,
			double targetArrivalBandSeconds,
			double targetArrivalToleranceSeconds,
			double observedArrivalTimeSeconds,
			double arrivalWindowErrorSeconds,
			double arrivalWindowErrorRatio,
			double targetAxialMomentumFluxNewtons,
			double observedMomentumFluxNewtons,
			double momentumErrorNewtons,
			double momentumErrorRatio,
			double pressureToleranceRatio,
			double velocityToleranceRatio,
			double arrivalTimeToleranceFraction,
			double momentumToleranceRatio,
			boolean pressureValidationPassed,
			boolean velocityValidationPassed,
			boolean arrivalTimeValidationPassed,
			boolean momentumValidationPassed,
			boolean validationPassed,
			String status,
			String message,
			String runStatus,
			String runMessage,
			String runRuntimeInfo,
			String resultRuntimeInfo
	) {
	}

	public record PoweredCruiseSkewWakeResultSeedExtrema(
			int validationSeedCount,
			int sourceTermCount,
			int centerlineSweepSeedCount,
			int lateralSweepSeedCount,
			int validationRunAvailableCount,
			int pressureEvidenceCount,
			int velocityEvidenceCount,
			int transitEvidenceCount,
			int momentumEvidenceCount,
			int validationSeedReadyCount,
			int validationPassedCount,
			double maxPressureErrorRatio,
			double maxWakeVelocityErrorRatio,
			double maxArrivalWindowErrorRatio,
			double maxMomentumErrorRatio,
			double maxTargetResultantDynamicPressurePascals,
			double maxTargetResultantWakeVelocityMetersPerSecond,
			double maxTargetAxialMomentumFluxNewtons
	) {
	}

	public record PoweredCruiseSkewWakeResultSeedAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int seedSampleCount,
			int seedMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredCruiseSkewWakeValidationSeed> seeds,
			PoweredCruiseSkewWakeResultSeedExtrema extrema
	) {
		public PoweredCruiseSkewWakeResultSeedAudit {
			seeds = List.copyOf(seeds);
		}
	}

	public static PoweredCruiseSkewWakeResultSeedAudit audit() {
		return audit(Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.audit());
	}

	public static PoweredCruiseSkewWakeResultSeedAudit audit(
			Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRunMatrixAudit runMatrix
	) {
		if (runMatrix == null) {
			throw new IllegalArgumentException("run matrix is required.");
		}
		List<PoweredCruiseSkewWakeValidationSeed> seeds = runMatrix.runs().stream()
				.map(Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed::seed)
				.toList();
		return new PoweredCruiseSkewWakeResultSeedAudit(
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

	public static PoweredCruiseSkewWakeValidationSeed seed(
			Aerodynamics4McL2PoweredCruiseSkewWakeValidationRunMatrix.PoweredCruiseSkewWakeValidationRun run
	) {
		if (run == null) {
			throw new IllegalArgumentException("run must not be null.");
		}
		boolean pressureEvidence = run.available() && run.hasPressureEvidence();
		boolean velocityEvidence = run.available() && run.hasVelocityEvidence();
		boolean transitEvidence = run.available() && run.hasTransitEvidence();
		boolean momentumEvidence = run.available() && run.hasMomentumEvidence();
		boolean seedReady = pressureEvidence && velocityEvidence && transitEvidence && momentumEvidence;
		double pressureError = seedReady
				? Math.abs(run.observedPressurePascals() - run.expectedResultantDynamicPressurePascals())
				: 0.0;
		double pressureErrorRatio = seedReady
				? ratio(pressureError, run.expectedResultantDynamicPressurePascals())
				: 0.0;
		double velocityError = seedReady
				? Math.abs(run.observedWakeVelocityMetersPerSecond()
						- run.expectedResultantWakeVelocityMetersPerSecond())
				: 0.0;
		double velocityErrorRatio = seedReady
				? ratio(velocityError, run.expectedResultantWakeVelocityMetersPerSecond())
				: 0.0;
		double arrivalBand = Math.max(0.0,
				run.lateralAdjustedTransitSeconds() - run.centerlineResultantTransitSeconds());
		double arrivalTolerance = arrivalToleranceSeconds(run.centerlineResultantTransitSeconds(), arrivalBand);
		double arrivalError = seedReady
				? arrivalWindowError(run.observedArrivalTimeSeconds(),
						run.centerlineResultantTransitSeconds(),
						run.lateralAdjustedTransitSeconds())
				: 0.0;
		double arrivalErrorRatio = seedReady
				? ratio(arrivalError, Math.max(arrivalBand, run.centerlineResultantTransitSeconds()))
				: 0.0;
		double momentumError = seedReady
				? Math.abs(run.observedMomentumFluxNewtons() - run.perRotorAxialMomentumFluxNewtons())
				: 0.0;
		double momentumErrorRatio = seedReady
				? ratio(momentumError, run.perRotorAxialMomentumFluxNewtons())
				: 0.0;
		boolean pressurePassed = seedReady && pressureErrorRatio <= PRESSURE_RELATIVE_TOLERANCE;
		boolean velocityPassed = seedReady && velocityErrorRatio <= WAKE_VELOCITY_RELATIVE_TOLERANCE;
		boolean arrivalPassed = seedReady && arrivalError <= arrivalTolerance + 1.0e-12;
		boolean momentumPassed = seedReady && momentumErrorRatio <= MOMENTUM_RELATIVE_TOLERANCE;
		boolean passed = pressurePassed && velocityPassed && arrivalPassed && momentumPassed;
		return new PoweredCruiseSkewWakeValidationSeed(
				run.presetName(),
				run.spinState(),
				run.rotorIndex(),
				run.axialPlaneIndex(),
				run.sweepColumnIndex(),
				run.axialPlaneFraction(),
				run.available(),
				pressureEvidence,
				velocityEvidence,
				transitEvidence,
				momentumEvidence,
				seedReady,
				run.expectedAxialWakePressurePascals(),
				run.expectedResultantDynamicPressurePascals(),
				seedReady ? run.observedPressurePascals() : 0.0,
				pressureError,
				pressureErrorRatio,
				run.expectedResultantWakeVelocityMetersPerSecond(),
				seedReady ? run.observedWakeVelocityMetersPerSecond() : 0.0,
				velocityError,
				velocityErrorRatio,
				run.centerlineResultantTransitSeconds(),
				run.lateralAdjustedTransitSeconds(),
				arrivalBand,
				arrivalTolerance,
				seedReady ? run.observedArrivalTimeSeconds() : 0.0,
				arrivalError,
				arrivalErrorRatio,
				run.perRotorAxialMomentumFluxNewtons(),
				seedReady ? run.observedMomentumFluxNewtons() : 0.0,
				momentumError,
				momentumErrorRatio,
				PRESSURE_RELATIVE_TOLERANCE,
				WAKE_VELOCITY_RELATIVE_TOLERANCE,
				ARRIVAL_TIME_TOLERANCE_FRACTION,
				MOMENTUM_RELATIVE_TOLERANCE,
				pressurePassed,
				velocityPassed,
				arrivalPassed,
				momentumPassed,
				passed,
				seedReady ? (passed ? "READY_PASS" : "READY_FAIL") : "UNAVAILABLE",
				messageFor(run.available(), pressureEvidence, velocityEvidence, transitEvidence,
						momentumEvidence, passed),
				run.status(),
				run.message(),
				run.runtimeInfo(),
				"audit-only-unvalidated-cruise-skew-wake-result-seed"
		);
	}

	private static String messageFor(
			boolean runAvailable,
			boolean pressureEvidence,
			boolean velocityEvidence,
			boolean transitEvidence,
			boolean momentumEvidence,
			boolean passed
	) {
		if (!runAvailable) {
			return "cruise-skew-wake-run-unavailable";
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
		if (!momentumEvidence) {
			return "momentum-evidence-missing";
		}
		return passed ? "cruise-skew-wake-validation-seed-passed"
				: "cruise-skew-wake-validation-seed-failed";
	}

	private static double arrivalToleranceSeconds(double centerlineTransitSeconds, double arrivalBandSeconds) {
		double reference = Math.max(arrivalBandSeconds, centerlineTransitSeconds);
		return reference * ARRIVAL_TIME_TOLERANCE_FRACTION;
	}

	private static double arrivalWindowError(double observed, double centerline, double lateral) {
		double lower = Math.min(centerline, lateral);
		double upper = Math.max(centerline, lateral);
		if (observed < lower) {
			return lower - observed;
		}
		if (observed > upper) {
			return observed - upper;
		}
		return 0.0;
	}

	private static PoweredCruiseSkewWakeResultSeedExtrema extrema(
			List<PoweredCruiseSkewWakeValidationSeed> seeds
	) {
		Set<String> sourceTerms = new HashSet<>();
		int centerline = 0;
		int lateral = 0;
		int runAvailable = 0;
		int pressureEvidence = 0;
		int velocityEvidence = 0;
		int transitEvidence = 0;
		int momentumEvidence = 0;
		int seedReady = 0;
		int passed = 0;
		double maxPressureErrorRatio = 0.0;
		double maxVelocityErrorRatio = 0.0;
		double maxArrivalErrorRatio = 0.0;
		double maxMomentumErrorRatio = 0.0;
		double maxTargetPressure = 0.0;
		double maxTargetVelocity = 0.0;
		double maxTargetMomentum = 0.0;
		for (PoweredCruiseSkewWakeValidationSeed seed : seeds) {
			sourceTerms.add(seed.presetName() + ":rotor" + seed.rotorIndex());
			if (seed.sweepColumnIndex() == 0) {
				centerline++;
			} else {
				lateral++;
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
			if (seed.momentumEvidenceAvailable()) {
				momentumEvidence++;
			}
			if (seed.validationSeedReady()) {
				seedReady++;
			}
			if (seed.validationPassed()) {
				passed++;
			}
			maxPressureErrorRatio = Math.max(maxPressureErrorRatio, seed.pressureErrorRatio());
			maxVelocityErrorRatio = Math.max(maxVelocityErrorRatio, seed.wakeVelocityErrorRatio());
			maxArrivalErrorRatio = Math.max(maxArrivalErrorRatio, seed.arrivalWindowErrorRatio());
			maxMomentumErrorRatio = Math.max(maxMomentumErrorRatio, seed.momentumErrorRatio());
			maxTargetPressure = Math.max(maxTargetPressure, seed.targetResultantDynamicPressurePascals());
			maxTargetVelocity = Math.max(maxTargetVelocity, seed.targetResultantWakeVelocityMetersPerSecond());
			maxTargetMomentum = Math.max(maxTargetMomentum, seed.targetAxialMomentumFluxNewtons());
		}
		return new PoweredCruiseSkewWakeResultSeedExtrema(
				seeds.size(),
				sourceTerms.size(),
				centerline,
				lateral,
				runAvailable,
				pressureEvidence,
				velocityEvidence,
				transitEvidence,
				momentumEvidence,
				seedReady,
				passed,
				maxPressureErrorRatio,
				maxVelocityErrorRatio,
				maxArrivalErrorRatio,
				maxMomentumErrorRatio,
				maxTargetPressure,
				maxTargetVelocity,
				maxTargetMomentum
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
