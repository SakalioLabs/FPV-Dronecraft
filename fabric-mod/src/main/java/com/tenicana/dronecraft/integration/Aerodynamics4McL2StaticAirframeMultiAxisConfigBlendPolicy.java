package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy {
	public static final String SOURCE_ID = "A4MC-L2-Static-Airframe-Multi-Axis-Config-Blend-Policy-Packet";
	public static final String CAVEAT =
			"Multi-axis config blend policy is audit-only: it caps suggested body-drag and center-of-pressure moves from accepted A4MC candidates while keeping runtime preset mutation disabled until live calibration review.";
	public static final double MAX_BODY_DRAG_BLEND_FRACTION = 0.18;
	public static final double MAX_PRESSURE_CENTER_BLEND_FRACTION = 0.20;
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int BLEND_METRIC_COUNT = 31;
	public static final int SUMMARY_METRIC_ROW_COUNT = 13;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * PRESET_SAMPLE_COUNT * BLEND_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy() {
	}

	public record MultiAxisConfigBlendCandidate(
			String scenarioName,
			String presetName,
			boolean multiAxisCandidateReady,
			boolean pressureCenterVectorResolved,
			boolean blendPolicyReviewed,
			boolean runtimeConfigAutoApplyAllowed,
			double currentBodyDragXCoefficient,
			double targetBodyDragXCoefficient,
			double suggestedBodyDragXCoefficient,
			double currentBodyDragYCoefficient,
			double targetBodyDragYCoefficient,
			double suggestedBodyDragYCoefficient,
			double currentBodyDragZCoefficient,
			double targetBodyDragZCoefficient,
			double suggestedBodyDragZCoefficient,
			double bodyDragBlendFraction,
			double currentPressureCenterOffsetXBodyMeters,
			double targetPressureCenterOffsetXBodyMeters,
			double suggestedPressureCenterOffsetXBodyMeters,
			double currentPressureCenterOffsetYBodyMeters,
			double targetPressureCenterOffsetYBodyMeters,
			double suggestedPressureCenterOffsetYBodyMeters,
			double currentPressureCenterOffsetZBodyMeters,
			double targetPressureCenterOffsetZBodyMeters,
			double suggestedPressureCenterOffsetZBodyMeters,
			double pressureCenterBlendFraction,
			double targetMomentCoefficientMagnitude,
			boolean angularDragReviewRequired,
			double maxBodyDragRelativeDelta,
			double maxPressureCenterDeltaMeters,
			String sourceRuntimeInfo
	) {
	}

	public record MultiAxisConfigBlendScenario(
			String scenarioName,
			List<MultiAxisConfigBlendCandidate> candidates
	) {
		public MultiAxisConfigBlendScenario {
			candidates = List.copyOf(candidates);
		}
	}

	public record MultiAxisConfigBlendExtrema(
			int scenarioCount,
			int blendCandidateCount,
			int suggestedBodyDragCandidateCount,
			int suggestedPressureCenterCandidateCount,
			int runtimeConfigAutoApplyAllowedCount,
			int angularDragReviewRequiredCount,
			double maxSuggestedBodyDragXCoefficient,
			double maxSuggestedBodyDragYCoefficient,
			double maxSuggestedBodyDragZCoefficient,
			double maxBodyDragRelativeDelta,
			double maxSuggestedPressureCenterOffsetMeters,
			double maxPressureCenterDeltaMeters,
			double maxTargetMomentCoefficientMagnitude
	) {
	}

	public record MultiAxisConfigBlendAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int presetSampleCount,
			int blendMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<MultiAxisConfigBlendScenario> scenarios,
			MultiAxisConfigBlendExtrema extrema
	) {
		public MultiAxisConfigBlendAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static MultiAxisConfigBlendAudit audit() {
		List<MultiAxisConfigBlendScenario> scenarios =
				Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.audit().scenarios().stream()
						.map(sourceScenario -> scenario(sourceScenario.scenarioName(), sourceScenario.candidates()))
						.toList();
		return new MultiAxisConfigBlendAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SCENARIO_SAMPLE_COUNT,
				PRESET_SAMPLE_COUNT,
				BLEND_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				scenarios,
				extrema(scenarios)
		);
	}

	public static MultiAxisConfigBlendScenario scenario(
			String scenarioName,
			List<Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate> candidates
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (candidates == null) {
			throw new IllegalArgumentException("candidates must not be null.");
		}
		return new MultiAxisConfigBlendScenario(
				scenarioName,
				candidates.stream()
						.map(Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy::candidate)
						.toList()
		);
	}

	public static MultiAxisConfigBlendCandidate candidate(
			Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate candidate
	) {
		if (candidate == null) {
			throw new IllegalArgumentException("candidate must not be null.");
		}
		DroneConfig config = configForPreset(candidate.presetName());
		Vec3 currentBodyDrag = config.bodyDragCoefficients();
		Vec3 targetBodyDrag = new Vec3(
				finiteOrZero(candidate.targetBodyDragXCoefficient()),
				finiteOrZero(candidate.targetBodyDragYCoefficient()),
				finiteOrZero(candidate.targetBodyDragZCoefficient())
		);
		Vec3 currentPressureCenter = config.centerOfPressureOffsetBodyMeters();
		Vec3 targetPressureCenter = new Vec3(
				finiteOrZero(candidate.targetPressureCenterOffsetXBodyMeters()),
				finiteOrZero(candidate.targetPressureCenterOffsetYBodyMeters()),
				finiteOrZero(candidate.targetPressureCenterOffsetZBodyMeters())
		);
		boolean canSuggest = candidate.multiAxisCandidateReady() && candidate.pressureCenterVectorResolved();
		boolean blendPolicyReviewed = false;
		boolean runtimeConfigAutoApplyAllowed = false;
		double bodyDragBlendFraction = canSuggest ? MAX_BODY_DRAG_BLEND_FRACTION : 0.0;
		double pressureCenterBlendFraction = canSuggest ? MAX_PRESSURE_CENTER_BLEND_FRACTION : 0.0;
		Vec3 suggestedBodyDrag = canSuggest
				? blend(currentBodyDrag, targetBodyDrag, bodyDragBlendFraction)
				: currentBodyDrag;
		Vec3 suggestedPressureCenter = canSuggest
				? blend(currentPressureCenter, targetPressureCenter, pressureCenterBlendFraction)
				: currentPressureCenter;
		boolean angularDragReviewRequired = canSuggest
				&& finiteOrZero(candidate.targetMomentCoefficientMagnitude()) > 1.0e-12;
		double maxBodyDragRelativeDelta = max(
				relativeDelta(suggestedBodyDrag.x(), currentBodyDrag.x()),
				relativeDelta(suggestedBodyDrag.y(), currentBodyDrag.y()),
				relativeDelta(suggestedBodyDrag.z(), currentBodyDrag.z())
		);
		double maxPressureCenterDeltaMeters = suggestedPressureCenter.subtract(currentPressureCenter).length();
		return new MultiAxisConfigBlendCandidate(
				candidate.scenarioName(),
				candidate.presetName(),
				candidate.multiAxisCandidateReady(),
				candidate.pressureCenterVectorResolved(),
				blendPolicyReviewed,
				runtimeConfigAutoApplyAllowed,
				currentBodyDrag.x(),
				targetBodyDrag.x(),
				suggestedBodyDrag.x(),
				currentBodyDrag.y(),
				targetBodyDrag.y(),
				suggestedBodyDrag.y(),
				currentBodyDrag.z(),
				targetBodyDrag.z(),
				suggestedBodyDrag.z(),
				bodyDragBlendFraction,
				currentPressureCenter.x(),
				targetPressureCenter.x(),
				suggestedPressureCenter.x(),
				currentPressureCenter.y(),
				targetPressureCenter.y(),
				suggestedPressureCenter.y(),
				currentPressureCenter.z(),
				targetPressureCenter.z(),
				suggestedPressureCenter.z(),
				pressureCenterBlendFraction,
				finiteOrZero(candidate.targetMomentCoefficientMagnitude()),
				angularDragReviewRequired,
				maxBodyDragRelativeDelta,
				maxPressureCenterDeltaMeters,
				candidate.sourceRuntimeInfo()
		);
	}

	private static DroneConfig configForPreset(String presetName) {
		return switch (presetName) {
			case "racingQuad" -> DroneConfig.racingQuad();
			case "apDrone" -> DroneConfig.apDrone();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavyLift" -> DroneConfig.heavyLift();
			default -> throw new IllegalArgumentException("unsupported static-airframe multi-axis config blend preset: " + presetName);
		};
	}

	private static MultiAxisConfigBlendExtrema extrema(List<MultiAxisConfigBlendScenario> scenarios) {
		int candidates = 0;
		int suggestedBodyDrag = 0;
		int suggestedPressureCenter = 0;
		int autoApply = 0;
		int angularReview = 0;
		double maxSuggestedBodyDragX = 0.0;
		double maxSuggestedBodyDragY = 0.0;
		double maxSuggestedBodyDragZ = 0.0;
		double maxBodyDragRelativeDelta = 0.0;
		double maxSuggestedPressureCenter = 0.0;
		double maxPressureCenterDelta = 0.0;
		double maxTargetMoment = 0.0;
		for (MultiAxisConfigBlendScenario scenario : scenarios) {
			for (MultiAxisConfigBlendCandidate candidate : scenario.candidates()) {
				candidates++;
				if (candidate.bodyDragBlendFraction() > 0.0) {
					suggestedBodyDrag++;
				}
				if (candidate.pressureCenterBlendFraction() > 0.0) {
					suggestedPressureCenter++;
				}
				if (candidate.runtimeConfigAutoApplyAllowed()) {
					autoApply++;
				}
				if (candidate.angularDragReviewRequired()) {
					angularReview++;
				}
				maxSuggestedBodyDragX = Math.max(maxSuggestedBodyDragX, candidate.suggestedBodyDragXCoefficient());
				maxSuggestedBodyDragY = Math.max(maxSuggestedBodyDragY, candidate.suggestedBodyDragYCoefficient());
				maxSuggestedBodyDragZ = Math.max(maxSuggestedBodyDragZ, candidate.suggestedBodyDragZCoefficient());
				maxBodyDragRelativeDelta = Math.max(maxBodyDragRelativeDelta, candidate.maxBodyDragRelativeDelta());
				maxSuggestedPressureCenter = Math.max(maxSuggestedPressureCenter, new Vec3(
						candidate.suggestedPressureCenterOffsetXBodyMeters(),
						candidate.suggestedPressureCenterOffsetYBodyMeters(),
						candidate.suggestedPressureCenterOffsetZBodyMeters()
				).length());
				maxPressureCenterDelta = Math.max(maxPressureCenterDelta, candidate.maxPressureCenterDeltaMeters());
				maxTargetMoment = Math.max(maxTargetMoment, candidate.targetMomentCoefficientMagnitude());
			}
		}
		return new MultiAxisConfigBlendExtrema(
				scenarios.size(),
				candidates,
				suggestedBodyDrag,
				suggestedPressureCenter,
				autoApply,
				angularReview,
				maxSuggestedBodyDragX,
				maxSuggestedBodyDragY,
				maxSuggestedBodyDragZ,
				maxBodyDragRelativeDelta,
				maxSuggestedPressureCenter,
				maxPressureCenterDelta,
				maxTargetMoment
		);
	}

	private static Vec3 blend(Vec3 current, Vec3 target, double fraction) {
		return current.add(target.subtract(current).multiply(fraction));
	}

	private static double relativeDelta(double suggested, double current) {
		if (!Double.isFinite(suggested) || !Double.isFinite(current) || current <= 1.0e-12) {
			return 0.0;
		}
		return Math.abs(suggested - current) / current;
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}

	private static double max(double x, double y, double z) {
		return Math.max(Math.max(x, y), z);
	}
}
