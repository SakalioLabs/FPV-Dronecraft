package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredHoverValidation {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Hover-Validation-Packet";
	public static final String CAVEAT =
			"Validation target compares powered L2 force/moment output against the static-airframe baseline plus hover actuator-disk delta; use only after a real A4MC powered source-term request is available.";
	public static final double FORCE_MATCH_RELATIVE_TOLERANCE = 0.08;
	public static final double MIN_FORCE_MATCH_TOLERANCE_NEWTONS = 0.20;
	public static final double MOMENT_MATCH_TOLERANCE_NEWTON_METERS = 0.05;
	public static final double CENTER_OF_FORCE_TOLERANCE_METERS = 0.05;
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int TARGET_METRIC_COUNT = 18;
	public static final int SUMMARY_METRIC_ROW_COUNT = 8;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * TARGET_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredHoverValidation() {
	}

	public record PoweredHoverValidationTarget(
			String presetName,
			boolean staticBaselineRequired,
			boolean poweredRunRequired,
			boolean staticBaselineSubtractRequired,
			boolean poweredSourceApiRequired,
			double targetForceXNewtons,
			double targetForceYNewtons,
			double targetForceZNewtons,
			double targetForceMagnitudeNewtons,
			double targetMomentXNewtonMeters,
			double targetMomentYNewtonMeters,
			double targetMomentZNewtonMeters,
			double targetMomentMagnitudeNewtonMeters,
			double forceToleranceNewtons,
			double forceRelativeTolerance,
			double momentToleranceNewtonMeters,
			double centerOfForceToleranceMeters,
			double targetCenterOfThrustOffsetMeters,
			double targetMeanPressureJumpPascals
	) {
	}

	public record PoweredHoverValidationResult(
			String presetName,
			double observedForceDeltaXNewtons,
			double observedForceDeltaYNewtons,
			double observedForceDeltaZNewtons,
			double observedForceDeltaMagnitudeNewtons,
			double observedMomentDeltaXNewtonMeters,
			double observedMomentDeltaYNewtonMeters,
			double observedMomentDeltaZNewtonMeters,
			double observedMomentDeltaMagnitudeNewtonMeters,
			double observedCenterOfForceOffsetMeters,
			double forceErrorNewtons,
			double forceErrorRatio,
			double momentErrorNewtonMeters,
			double centerOfForceErrorMeters,
			boolean forceMatched,
			boolean momentMatched,
			boolean centerOfForceMatched,
			boolean passed
	) {
	}

	public record PoweredHoverValidationExtrema(
			double maxTargetForceMagnitudeNewtons,
			double maxForceToleranceNewtons,
			double forceRelativeTolerance,
			double momentToleranceNewtonMeters,
			double centerOfForceToleranceMeters,
			int targetCount,
			int poweredSourceApiRequiredCount,
			int staticBaselineSubtractRequiredCount
	) {
	}

	public record PoweredHoverValidationAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int targetMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredHoverValidationTarget> targets,
			PoweredHoverValidationExtrema extrema
	) {
		public PoweredHoverValidationAudit {
			targets = List.copyOf(targets);
		}
	}

	public static PoweredHoverValidationAudit audit() {
		List<PoweredHoverValidationTarget> targets = Aerodynamics4McL2PoweredHoverExperimentPlan.audit().experiments()
				.stream()
				.map(Aerodynamics4McL2PoweredHoverValidation::target)
				.toList();
		return new PoweredHoverValidationAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				TARGET_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				targets,
				extrema(targets)
		);
	}

	public static PoweredHoverValidationTarget target(
			Aerodynamics4McL2PoweredHoverExperimentPlan.PoweredHoverExperiment experiment
	) {
		if (experiment == null) {
			throw new IllegalArgumentException("experiment must not be null.");
		}
		double forceTolerance = Math.max(
				MIN_FORCE_MATCH_TOLERANCE_NEWTONS,
				experiment.targetForceMagnitudeNewtons() * FORCE_MATCH_RELATIVE_TOLERANCE
		);
		return new PoweredHoverValidationTarget(
				experiment.presetName(),
				true,
				true,
				true,
				experiment.poweredSourceApiRequired(),
				experiment.targetForceXNewtons(),
				experiment.targetForceYNewtons(),
				experiment.targetForceZNewtons(),
				experiment.targetForceMagnitudeNewtons(),
				experiment.targetMomentXNewtonMeters(),
				experiment.targetMomentYNewtonMeters(),
				experiment.targetMomentZNewtonMeters(),
				experiment.targetMomentMagnitudeNewtonMeters(),
				forceTolerance,
				FORCE_MATCH_RELATIVE_TOLERANCE,
				MOMENT_MATCH_TOLERANCE_NEWTON_METERS,
				CENTER_OF_FORCE_TOLERANCE_METERS,
				experiment.centerOfThrustOffsetMeters(),
				experiment.meanPressureJumpPascals()
		);
	}

	public static PoweredHoverValidationResult evaluate(
			PoweredHoverValidationTarget target,
			Aerodynamics4McL2Bridge.L2ForceMomentSample staticBaseline,
			Aerodynamics4McL2Bridge.L2ForceMomentSample poweredRun
	) {
		if (target == null) {
			throw new IllegalArgumentException("target must not be null.");
		}
		if (staticBaseline == null || poweredRun == null) {
			throw new IllegalArgumentException("static baseline and powered run samples are required.");
		}
		double forceX = poweredRun.forceX() - staticBaseline.forceX();
		double forceY = poweredRun.forceY() - staticBaseline.forceY();
		double forceZ = poweredRun.forceZ() - staticBaseline.forceZ();
		double momentX = poweredRun.momentX() - staticBaseline.momentX();
		double momentY = poweredRun.momentY() - staticBaseline.momentY();
		double momentZ = poweredRun.momentZ() - staticBaseline.momentZ();
		double forceMagnitude = magnitude(forceX, forceY, forceZ);
		double momentMagnitude = magnitude(momentX, momentY, momentZ);
		double forceError = magnitude(
				forceX - target.targetForceXNewtons(),
				forceY - target.targetForceYNewtons(),
				forceZ - target.targetForceZNewtons()
		);
		double forceErrorRatio = target.targetForceMagnitudeNewtons() <= 1.0e-12
				? forceError
				: forceError / target.targetForceMagnitudeNewtons();
		double momentError = magnitude(
				momentX - target.targetMomentXNewtonMeters(),
				momentY - target.targetMomentYNewtonMeters(),
				momentZ - target.targetMomentZNewtonMeters()
		);
		double centerOffset = centerOfForceOffset(forceX, forceY, forceZ, momentX, momentY, momentZ);
		double centerError = Math.abs(centerOffset - target.targetCenterOfThrustOffsetMeters());
		boolean forceMatched = forceError <= target.forceToleranceNewtons();
		boolean momentMatched = momentError <= target.momentToleranceNewtonMeters();
		boolean centerMatched = centerError <= target.centerOfForceToleranceMeters();
		return new PoweredHoverValidationResult(
				target.presetName(),
				forceX,
				forceY,
				forceZ,
				forceMagnitude,
				momentX,
				momentY,
				momentZ,
				momentMagnitude,
				centerOffset,
				forceError,
				forceErrorRatio,
				momentError,
				centerError,
				forceMatched,
				momentMatched,
				centerMatched,
				forceMatched && momentMatched && centerMatched
		);
	}

	public static PoweredHoverValidationResult evaluate(
			Aerodynamics4McL2PoweredHoverExperimentPlan.PoweredHoverExperiment experiment,
			Aerodynamics4McL2Bridge.L2ForceMomentSample staticBaseline,
			Aerodynamics4McL2Bridge.L2ForceMomentSample poweredRun
	) {
		return evaluate(target(experiment), staticBaseline, poweredRun);
	}

	private static PoweredHoverValidationExtrema extrema(List<PoweredHoverValidationTarget> targets) {
		double maxForce = 0.0;
		double maxForceTolerance = 0.0;
		int sourceApiRequired = 0;
		int baselineSubtractRequired = 0;
		for (PoweredHoverValidationTarget target : targets) {
			maxForce = Math.max(maxForce, target.targetForceMagnitudeNewtons());
			maxForceTolerance = Math.max(maxForceTolerance, target.forceToleranceNewtons());
			if (target.poweredSourceApiRequired()) {
				sourceApiRequired++;
			}
			if (target.staticBaselineSubtractRequired()) {
				baselineSubtractRequired++;
			}
		}
		return new PoweredHoverValidationExtrema(
				maxForce,
				maxForceTolerance,
				FORCE_MATCH_RELATIVE_TOLERANCE,
				MOMENT_MATCH_TOLERANCE_NEWTON_METERS,
				CENTER_OF_FORCE_TOLERANCE_METERS,
				targets.size(),
				sourceApiRequired,
				baselineSubtractRequired
		);
	}

	private static double centerOfForceOffset(double forceX, double forceY, double forceZ,
			double momentX, double momentY, double momentZ) {
		double forceSquared = forceX * forceX + forceY * forceY + forceZ * forceZ;
		if (forceSquared <= 1.0e-12) {
			return 0.0;
		}
		double cx = forceY * momentZ - forceZ * momentY;
		double cy = forceZ * momentX - forceX * momentZ;
		double cz = forceX * momentY - forceY * momentX;
		return magnitude(cx, cy, cz) / forceSquared;
	}

	private static double magnitude(double x, double y, double z) {
		return Math.sqrt(x * x + y * y + z * z);
	}
}
