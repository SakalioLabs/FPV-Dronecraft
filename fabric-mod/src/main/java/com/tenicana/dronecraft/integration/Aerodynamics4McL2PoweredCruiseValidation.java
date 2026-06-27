package com.tenicana.dronecraft.integration;

import java.util.List;

public final class Aerodynamics4McL2PoweredCruiseValidation {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Cruise-Validation-Packet";
	public static final String CAVEAT =
			"Validation target compares forward-flight powered L2 force/moment output against the static-airframe baseline plus cruise actuator-disk delta; use only after a real A4MC powered source-term request can represent edgewise rotor loading.";
	public static final double FORCE_MATCH_RELATIVE_TOLERANCE = 0.08;
	public static final double MIN_FORCE_MATCH_TOLERANCE_NEWTONS = 0.20;
	public static final double MOMENT_MATCH_TOLERANCE_NEWTON_METERS = 0.05;
	public static final double CENTER_OF_FORCE_TOLERANCE_METERS = 0.05;
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int TARGET_METRIC_COUNT = 26;
	public static final int SUMMARY_METRIC_ROW_COUNT = 11;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ PRESET_SAMPLE_COUNT * TARGET_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredCruiseValidation() {
	}

	public record PoweredCruiseValidationTarget(
			String presetName,
			boolean staticBaselineRequired,
			boolean poweredRunRequired,
			boolean staticBaselineSubtractRequired,
			boolean poweredSourceApiRequired,
			boolean edgewiseValidationRequired,
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
			double targetMeanPressureJumpPascals,
			double targetEdgewiseAdvanceRatio,
			double targetIdealInducedVelocityMetersPerSecond,
			double targetFarWakeVelocityMetersPerSecond,
			double targetTipSpeedMetersPerSecond,
			double targetRepresentativeBladeReynoldsNumber,
			double targetInletSpeedMetersPerSecond,
			double targetSpinRatio,
			String runtimeInfo
	) {
	}

	public record PoweredCruiseValidationResult(
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

	public record PoweredCruiseValidationExtrema(
			double maxTargetForceMagnitudeNewtons,
			double maxForceToleranceNewtons,
			double forceRelativeTolerance,
			double momentToleranceNewtonMeters,
			double centerOfForceToleranceMeters,
			double maxTargetEdgewiseAdvanceRatio,
			double maxTargetRepresentativeBladeReynoldsNumber,
			int targetCount,
			int poweredSourceApiRequiredCount,
			int staticBaselineSubtractRequiredCount,
			int edgewiseValidationRequiredCount
	) {
	}

	public record PoweredCruiseValidationAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int targetMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredCruiseValidationTarget> targets,
			PoweredCruiseValidationExtrema extrema
	) {
		public PoweredCruiseValidationAudit {
			targets = List.copyOf(targets);
		}
	}

	public static PoweredCruiseValidationAudit audit() {
		List<PoweredCruiseValidationTarget> targets = Aerodynamics4McL2PoweredCruiseExperimentPlan.audit()
				.experiments()
				.stream()
				.map(Aerodynamics4McL2PoweredCruiseValidation::target)
				.toList();
		return new PoweredCruiseValidationAudit(
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

	public static PoweredCruiseValidationTarget target(
			Aerodynamics4McL2PoweredCruiseExperimentPlan.PoweredCruiseExperiment experiment
	) {
		if (experiment == null) {
			throw new IllegalArgumentException("experiment must not be null.");
		}
		double forceTolerance = Math.max(
				MIN_FORCE_MATCH_TOLERANCE_NEWTONS,
				experiment.targetForceMagnitudeNewtons() * FORCE_MATCH_RELATIVE_TOLERANCE
		);
		return new PoweredCruiseValidationTarget(
				experiment.presetName(),
				true,
				true,
				true,
				experiment.poweredSourceApiRequired(),
				true,
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
				experiment.meanPressureJumpPascals(),
				experiment.edgewiseAdvanceRatio(),
				experiment.idealInducedVelocityMetersPerSecond(),
				experiment.farWakeVelocityMetersPerSecond(),
				experiment.tipSpeedMetersPerSecond(),
				experiment.representativeBladeReynoldsNumber(),
				experiment.inletSpeedMetersPerSecond(),
				experiment.spinRatio(),
				experiment.runtimeInfo()
		);
	}

	public static PoweredCruiseValidationResult evaluate(
			PoweredCruiseValidationTarget target,
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
		double centerError = Math.abs(centerOffset);
		boolean forceMatched = forceError <= target.forceToleranceNewtons();
		boolean momentMatched = momentError <= target.momentToleranceNewtonMeters();
		boolean centerMatched = centerError <= target.centerOfForceToleranceMeters();
		return new PoweredCruiseValidationResult(
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

	public static PoweredCruiseValidationResult evaluate(
			Aerodynamics4McL2PoweredCruiseExperimentPlan.PoweredCruiseExperiment experiment,
			Aerodynamics4McL2Bridge.L2ForceMomentSample staticBaseline,
			Aerodynamics4McL2Bridge.L2ForceMomentSample poweredRun
	) {
		return evaluate(target(experiment), staticBaseline, poweredRun);
	}

	private static PoweredCruiseValidationExtrema extrema(List<PoweredCruiseValidationTarget> targets) {
		double maxForce = 0.0;
		double maxForceTolerance = 0.0;
		double maxEdgewise = 0.0;
		double maxReynolds = 0.0;
		int sourceApiRequired = 0;
		int baselineSubtractRequired = 0;
		int edgewiseRequired = 0;
		for (PoweredCruiseValidationTarget target : targets) {
			maxForce = Math.max(maxForce, target.targetForceMagnitudeNewtons());
			maxForceTolerance = Math.max(maxForceTolerance, target.forceToleranceNewtons());
			maxEdgewise = Math.max(maxEdgewise, target.targetEdgewiseAdvanceRatio());
			maxReynolds = Math.max(maxReynolds, target.targetRepresentativeBladeReynoldsNumber());
			if (target.poweredSourceApiRequired()) {
				sourceApiRequired++;
			}
			if (target.staticBaselineSubtractRequired()) {
				baselineSubtractRequired++;
			}
			if (target.edgewiseValidationRequired()) {
				edgewiseRequired++;
			}
		}
		return new PoweredCruiseValidationExtrema(
				maxForce,
				maxForceTolerance,
				FORCE_MATCH_RELATIVE_TOLERANCE,
				MOMENT_MATCH_TOLERANCE_NEWTON_METERS,
				CENTER_OF_FORCE_TOLERANCE_METERS,
				maxEdgewise,
				maxReynolds,
				targets.size(),
				sourceApiRequired,
				baselineSubtractRequired,
				edgewiseRequired
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
