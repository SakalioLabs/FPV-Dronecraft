package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredSourceResultSeed {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Result-Seed-Packet";
	public static final String CAVEAT =
			"Result seed pairs static baseline force/moment summaries with powered-source run matrix rows; validation remains unavailable until both baseline and powered run force/moment evidence exist.";
	public static final int SOURCE_REFERENCE_COUNT = 7;
	public static final int SEED_SAMPLE_COUNT = 8;
	public static final int SEED_METRIC_COUNT = 39;
	public static final int SUMMARY_METRIC_ROW_COUNT = 12;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SEED_SAMPLE_COUNT * SEED_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceResultSeed() {
	}

	public record PoweredSourceValidationSeed(
			String presetName,
			String spinState,
			String validationPacketId,
			String acceptanceGatePacketId,
			boolean staticBaselineAvailable,
			boolean staticBaselineHasForceMoment,
			boolean poweredRunAvailable,
			boolean poweredRunHasForceMoment,
			boolean baselineSubtractedDeltaReady,
			boolean validationSeedReady,
			double staticForceXNewtons,
			double staticForceYNewtons,
			double staticForceZNewtons,
			double staticForceMagnitudeNewtons,
			double staticMomentXNewtonMeters,
			double staticMomentYNewtonMeters,
			double staticMomentZNewtonMeters,
			double staticMomentMagnitudeNewtonMeters,
			double poweredForceDeltaXNewtons,
			double poweredForceDeltaYNewtons,
			double poweredForceDeltaZNewtons,
			double poweredForceDeltaMagnitudeNewtons,
			double poweredMomentDeltaXNewtonMeters,
			double poweredMomentDeltaYNewtonMeters,
			double poweredMomentDeltaZNewtonMeters,
			double poweredMomentDeltaMagnitudeNewtonMeters,
			double poweredCenterOfForceOffsetMeters,
			double targetForceMagnitudeNewtons,
			double targetMomentMagnitudeNewtonMeters,
			double targetCenterOfForceOffsetMeters,
			double forceErrorNewtons,
			double forceErrorRatio,
			double momentErrorNewtonMeters,
			double centerOfForceErrorMeters,
			boolean validationPassed,
			String status,
			String message,
			String staticRuntimeInfo,
			String poweredRuntimeInfo
	) {
	}

	public record PoweredSourceResultSeedExtrema(
			int validationSeedCount,
			int hoverSeedCount,
			int cruiseSeedCount,
			int staticBaselineAvailableCount,
			int staticBaselineForceMomentCount,
			int poweredRunAvailableCount,
			int poweredRunForceMomentCount,
			int baselineSubtractedDeltaReadyCount,
			int validationSeedReadyCount,
			int validationPassedCount,
			double maxTargetForceMagnitudeNewtons,
			double maxForceErrorRatio
	) {
	}

	public record PoweredSourceResultSeedAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int seedSampleCount,
			int seedMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceValidationSeed> seeds,
			PoweredSourceResultSeedExtrema extrema
	) {
		public PoweredSourceResultSeedAudit {
			seeds = List.copyOf(seeds);
		}
	}

	public static PoweredSourceResultSeedAudit audit() {
		return audit(
				Aerodynamics4McL2StaticAirframeRunMatrix.audit(),
				Aerodynamics4McL2PoweredSourceRunMatrix.audit()
		);
	}

	public static PoweredSourceResultSeedAudit audit(ClassLoader loader) {
		if (loader == null) {
			throw new IllegalArgumentException("loader must not be null.");
		}
		return audit(
				Aerodynamics4McL2StaticAirframeRunMatrix.audit(loader),
				Aerodynamics4McL2PoweredSourceRunMatrix.audit()
		);
	}

	public static PoweredSourceResultSeedAudit audit(
			Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunMatrixAudit staticRunMatrix,
			Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunMatrixAudit poweredRunMatrix
	) {
		if (staticRunMatrix == null || poweredRunMatrix == null) {
			throw new IllegalArgumentException("static and powered run matrices are required.");
		}
		List<ValidationTargetSpec> targets = validationTargets();
		List<PoweredSourceValidationSeed> seeds = poweredRunMatrix.runs().stream()
				.map(run -> seed(run, findStaticRun(staticRunMatrix.runs(), run.presetName()), targets))
				.toList();
		return new PoweredSourceResultSeedAudit(
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

	public static PoweredSourceValidationSeed seed(
			Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunSummary poweredRun,
			Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunSummary staticBaseline
	) {
		return seed(poweredRun, staticBaseline, validationTargets());
	}

	private static PoweredSourceValidationSeed seed(
			Aerodynamics4McL2PoweredSourceRunMatrix.PoweredSourceRunSummary poweredRun,
			Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunSummary staticBaseline,
			List<ValidationTargetSpec> targets
	) {
		if (poweredRun == null || staticBaseline == null) {
			throw new IllegalArgumentException("powered run and static baseline are required.");
		}
		if (!poweredRun.presetName().equals(staticBaseline.presetName())) {
			throw new IllegalArgumentException("powered run and static baseline preset names must match.");
		}
		ValidationTargetSpec target = findTarget(targets, poweredRun.presetName(), poweredRun.spinState());
		boolean staticAvailable = staticBaseline.available();
		boolean staticForceMoment = staticBaseline.hasForceMoment();
		boolean poweredAvailable = poweredRun.available();
		boolean poweredForceMoment = poweredRun.hasForceMoment();
		boolean deltaReady = staticAvailable && staticForceMoment && poweredAvailable && poweredForceMoment;
		double forceError = deltaReady
				? magnitude(
						poweredRun.forceDeltaXNewtons() - target.forceXNewtons(),
						poweredRun.forceDeltaYNewtons() - target.forceYNewtons(),
						poweredRun.forceDeltaZNewtons() - target.forceZNewtons())
				: 0.0;
		double forceErrorRatio = deltaReady && target.forceMagnitudeNewtons() > 1.0e-12
				? forceError / target.forceMagnitudeNewtons()
				: 0.0;
		double momentError = deltaReady
				? magnitude(
						poweredRun.momentDeltaXNewtonMeters() - target.momentXNewtonMeters(),
						poweredRun.momentDeltaYNewtonMeters() - target.momentYNewtonMeters(),
						poweredRun.momentDeltaZNewtonMeters() - target.momentZNewtonMeters())
				: 0.0;
		double centerError = deltaReady
				? Math.abs(poweredRun.centerOfForceOffsetMeters() - target.centerOfForceOffsetMeters())
				: 0.0;
		boolean passed = deltaReady
				&& forceError <= target.forceToleranceNewtons()
				&& momentError <= target.momentToleranceNewtonMeters()
				&& centerError <= target.centerOfForceToleranceMeters();
		return new PoweredSourceValidationSeed(
				poweredRun.presetName(),
				poweredRun.spinState(),
				poweredRun.validationPacketId(),
				poweredRun.acceptanceGatePacketId(),
				staticAvailable,
				staticForceMoment,
				poweredAvailable,
				poweredForceMoment,
				deltaReady,
				deltaReady,
				staticBaseline.forceXNewtons(),
				staticBaseline.forceYNewtons(),
				staticBaseline.forceZNewtons(),
				staticBaseline.forceMagnitudeNewtons(),
				staticBaseline.momentXNewtonMeters(),
				staticBaseline.momentYNewtonMeters(),
				staticBaseline.momentZNewtonMeters(),
				staticBaseline.momentMagnitudeNewtonMeters(),
				poweredRun.forceDeltaXNewtons(),
				poweredRun.forceDeltaYNewtons(),
				poweredRun.forceDeltaZNewtons(),
				poweredRun.forceDeltaMagnitudeNewtons(),
				poweredRun.momentDeltaXNewtonMeters(),
				poweredRun.momentDeltaYNewtonMeters(),
				poweredRun.momentDeltaZNewtonMeters(),
				poweredRun.momentDeltaMagnitudeNewtonMeters(),
				poweredRun.centerOfForceOffsetMeters(),
				target.forceMagnitudeNewtons(),
				target.momentMagnitudeNewtonMeters(),
				target.centerOfForceOffsetMeters(),
				forceError,
				forceErrorRatio,
				momentError,
				centerError,
				passed,
				deltaReady ? (passed ? "READY_PASS" : "READY_FAIL") : "UNAVAILABLE",
				messageFor(staticAvailable, staticForceMoment, poweredAvailable, poweredForceMoment, passed),
				staticBaseline.runtimeInfo(),
				poweredRun.runtimeInfo()
		);
	}

	private static String messageFor(
			boolean staticAvailable,
			boolean staticForceMoment,
			boolean poweredAvailable,
			boolean poweredForceMoment,
			boolean passed
	) {
		if (!staticAvailable) {
			return "static-baseline-unavailable";
		}
		if (!staticForceMoment) {
			return "static-baseline-force-moment-missing";
		}
		if (!poweredAvailable) {
			return "powered-source-run-unavailable";
		}
		if (!poweredForceMoment) {
			return "powered-source-force-moment-missing";
		}
		return passed ? "validation-seed-passed" : "validation-seed-failed";
	}

	private static Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunSummary findStaticRun(
			List<Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunSummary> runs,
			String presetName
	) {
		for (Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunSummary run : runs) {
			if (presetName.equals(run.presetName())) {
				return run;
			}
		}
		throw new IllegalArgumentException("missing static baseline for preset: " + presetName);
	}

	private static ValidationTargetSpec findTarget(
			List<ValidationTargetSpec> targets,
			String presetName,
			String spinState
	) {
		for (ValidationTargetSpec target : targets) {
			if (target.presetName().equals(presetName) && target.spinState().equals(spinState)) {
				return target;
			}
		}
		throw new IllegalArgumentException("missing validation target for " + presetName + ":" + spinState);
	}

	private static List<ValidationTargetSpec> validationTargets() {
		List<ValidationTargetSpec> hover = Aerodynamics4McL2PoweredHoverValidation.audit().targets().stream()
				.map(target -> new ValidationTargetSpec(
						target.presetName(),
						"hover",
						target.targetForceXNewtons(),
						target.targetForceYNewtons(),
						target.targetForceZNewtons(),
						target.targetForceMagnitudeNewtons(),
						target.targetMomentXNewtonMeters(),
						target.targetMomentYNewtonMeters(),
						target.targetMomentZNewtonMeters(),
						target.targetMomentMagnitudeNewtonMeters(),
						target.targetCenterOfThrustOffsetMeters(),
						target.forceToleranceNewtons(),
						target.momentToleranceNewtonMeters(),
						target.centerOfForceToleranceMeters()
				))
				.toList();
		List<ValidationTargetSpec> cruise = Aerodynamics4McL2PoweredCruiseValidation.audit().targets().stream()
				.map(target -> new ValidationTargetSpec(
						target.presetName(),
						"cruise",
						target.targetForceXNewtons(),
						target.targetForceYNewtons(),
						target.targetForceZNewtons(),
						target.targetForceMagnitudeNewtons(),
						target.targetMomentXNewtonMeters(),
						target.targetMomentYNewtonMeters(),
						target.targetMomentZNewtonMeters(),
						target.targetMomentMagnitudeNewtonMeters(),
						target.targetCenterOfThrustOffsetMeters(),
						target.forceToleranceNewtons(),
						target.momentToleranceNewtonMeters(),
						target.centerOfForceToleranceMeters()
				))
				.toList();
		return java.util.stream.Stream.concat(hover.stream(), cruise.stream()).toList();
	}

	private static PoweredSourceResultSeedExtrema extrema(List<PoweredSourceValidationSeed> seeds) {
		int hover = 0;
		int cruise = 0;
		int staticAvailable = 0;
		int staticForceMoment = 0;
		int poweredAvailable = 0;
		int poweredForceMoment = 0;
		int deltaReady = 0;
		int validationReady = 0;
		int validationPassed = 0;
		double maxTargetForce = 0.0;
		double maxForceErrorRatio = 0.0;
		for (PoweredSourceValidationSeed seed : seeds) {
			if ("hover".equals(seed.spinState())) {
				hover++;
			}
			if ("cruise".equals(seed.spinState())) {
				cruise++;
			}
			if (seed.staticBaselineAvailable()) {
				staticAvailable++;
			}
			if (seed.staticBaselineHasForceMoment()) {
				staticForceMoment++;
			}
			if (seed.poweredRunAvailable()) {
				poweredAvailable++;
			}
			if (seed.poweredRunHasForceMoment()) {
				poweredForceMoment++;
			}
			if (seed.baselineSubtractedDeltaReady()) {
				deltaReady++;
			}
			if (seed.validationSeedReady()) {
				validationReady++;
			}
			if (seed.validationPassed()) {
				validationPassed++;
			}
			maxTargetForce = Math.max(maxTargetForce, seed.targetForceMagnitudeNewtons());
			maxForceErrorRatio = Math.max(maxForceErrorRatio, seed.forceErrorRatio());
		}
		return new PoweredSourceResultSeedExtrema(
				seeds.size(),
				hover,
				cruise,
				staticAvailable,
				staticForceMoment,
				poweredAvailable,
				poweredForceMoment,
				deltaReady,
				validationReady,
				validationPassed,
				maxTargetForce,
				maxForceErrorRatio
		);
	}

	private static double magnitude(double x, double y, double z) {
		return Math.sqrt(x * x + y * y + z * z);
	}

	private record ValidationTargetSpec(
			String presetName,
			String spinState,
			double forceXNewtons,
			double forceYNewtons,
			double forceZNewtons,
			double forceMagnitudeNewtons,
			double momentXNewtonMeters,
			double momentYNewtonMeters,
			double momentZNewtonMeters,
			double momentMagnitudeNewtonMeters,
			double centerOfForceOffsetMeters,
			double forceToleranceNewtons,
			double momentToleranceNewtonMeters,
			double centerOfForceToleranceMeters
	) {
	}
}
