package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;

public final class Aerodynamics4McL2StaticAirframeGameplayFitCandidate {
	public static final String SOURCE_ID = "A4MC-L2-Static-Airframe-Gameplay-Fit-Candidate-Packet";
	public static final String CAVEAT =
			"Single-axis forward-drag candidates are recorded only after the coefficient gate opens; full sideforce, lift, moment, and pressure-center gameplay fitting still requires multi-axis live A4MC L2 sweeps.";
	public static final double MAX_FORWARD_DRAG_TARGET_TO_CURRENT_RATIO = 80.0;
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int SCENARIO_SAMPLE_COUNT = 3;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int CANDIDATE_METRIC_COUNT = 16;
	public static final int SUMMARY_METRIC_ROW_COUNT = 11;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * PRESET_SAMPLE_COUNT * CANDIDATE_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2StaticAirframeGameplayFitCandidate() {
	}

	public record GameplayFitCandidate(
			String scenarioName,
			String presetName,
			boolean coefficientGateAllowed,
			boolean coefficientFitReady,
			boolean liveRuntime,
			boolean forwardDragCandidateReady,
			boolean fullAirframeGameplayFitAllowed,
			double currentBodyDragZCoefficient,
			double targetBodyDragZCoefficient,
			double currentEquivalentCdAMetersSquared,
			double targetEquivalentCdAMetersSquared,
			double targetToCurrentBodyDragZRatio,
			double sideForceResidualCoefficient,
			double liftResidualCoefficient,
			double momentCoefficientMagnitude,
			double pressureCenterOffsetRatio,
			boolean multiAxisSweepRequired,
			String sourceRuntimeInfo
	) {
	}

	public record GameplayFitScenario(
			String scenarioName,
			List<GameplayFitCandidate> candidates
	) {
		public GameplayFitScenario {
			candidates = List.copyOf(candidates);
		}
	}

	public record GameplayFitExtrema(
			int scenarioCount,
			int candidateCount,
			int forwardDragCandidateReadyCount,
			int fullAirframeGameplayFitAllowedCount,
			int multiAxisSweepRequiredCount,
			double maxTargetBodyDragZCoefficient,
			double maxTargetToCurrentBodyDragZRatio,
			double maxAbsSideForceResidualCoefficient,
			double maxAbsLiftResidualCoefficient,
			double maxMomentCoefficientMagnitude,
			double maxPressureCenterOffsetRatio
	) {
	}

	public record GameplayFitAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int presetSampleCount,
			int candidateMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<GameplayFitScenario> scenarios,
			GameplayFitExtrema extrema
	) {
		public GameplayFitAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static GameplayFitAudit audit() {
		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> productionSeeds =
				Aerodynamics4McL2StaticAirframeCoefficientSeed.audit().seeds();
		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> testSeeds =
				Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(
						Aerodynamics4McL2StaticAirframeGameplayFitCandidate.class.getClassLoader()).seeds();
		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> liveSeeds =
				liveRuntimeSeeds(testSeeds);
		List<GameplayFitScenario> scenarios = List.of(
				scenario("current_runtime_unavailable", productionSeeds),
				scenario("test_runtime_fit_ready_blocked", testSeeds),
				scenario("live_runtime_forward_drag_candidate", liveSeeds)
		);
		return new GameplayFitAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SCENARIO_SAMPLE_COUNT,
				PRESET_SAMPLE_COUNT,
				CANDIDATE_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				scenarios,
				extrema(scenarios)
		);
	}

	public static GameplayFitScenario scenario(
			String scenarioName,
			List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> seeds
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (seeds == null) {
			throw new IllegalArgumentException("seeds must not be null.");
		}
		Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceSummary gate =
				Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.gate(seeds);
		return new GameplayFitScenario(
				scenarioName,
				seeds.stream()
						.map(seed -> candidate(scenarioName, seed, gate))
						.toList()
		);
	}

	public static GameplayFitCandidate candidate(
			String scenarioName,
			Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed,
			Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceSummary gate
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (seed == null || gate == null) {
			throw new IllegalArgumentException("seed and gate summary are required.");
		}
		DroneConfig config = configForPreset(seed.presetName());
		double currentBodyDragZ = config.bodyDragCoefficients().z();
		double airDensity = Math.max(1.0e-12, seed.airDensityKgM3());
		double targetCdA = Math.max(0.0, seed.referenceAreaSquareMeters() * seed.dragCoefficient());
		double targetBodyDragZ = 0.5 * airDensity * targetCdA;
		double currentCdA = 2.0 * currentBodyDragZ / airDensity;
		double ratio = safeRatio(targetBodyDragZ, currentBodyDragZ);
		boolean liveRuntime = isLiveRuntime(seed);
		boolean forwardReady = gate.gameplayCoefficientFitAllowed()
				&& seed.coefficientFitReady()
				&& liveRuntime
				&& finiteNonNegative(targetBodyDragZ)
				&& finiteNonNegative(targetCdA)
				&& finiteNonNegative(ratio)
				&& ratio <= MAX_FORWARD_DRAG_TARGET_TO_CURRENT_RATIO;
		boolean multiAxisSweepRequired = true;
		return new GameplayFitCandidate(
				scenarioName,
				seed.presetName(),
				gate.gameplayCoefficientFitAllowed(),
				seed.coefficientFitReady(),
				liveRuntime,
				forwardReady,
				forwardReady && !multiAxisSweepRequired,
				currentBodyDragZ,
				targetBodyDragZ,
				currentCdA,
				targetCdA,
				ratio,
				seed.sideForceCoefficient(),
				seed.liftCoefficient(),
				seed.momentCoefficientMagnitude(),
				seed.pressureCenterOffsetRatio(),
				multiAxisSweepRequired,
				seed.sourceRuntimeInfo()
		);
	}

	private static DroneConfig configForPreset(String presetName) {
		return switch (presetName) {
			case "racingQuad" -> DroneConfig.racingQuad();
			case "apDrone" -> DroneConfig.apDrone();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavyLift" -> DroneConfig.heavyLift();
			default -> throw new IllegalArgumentException("unsupported static-airframe gameplay-fit preset: " + presetName);
		};
	}

	private static boolean isLiveRuntime(
			Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed
	) {
		String runtime = seed.sourceRuntimeInfo();
		return seed.sourceRunAvailable()
				&& runtime != null
				&& !runtime.isBlank()
				&& !"none".equals(runtime)
				&& !"test-runtime".equals(runtime);
	}

	private static List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> liveRuntimeSeeds(
			List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> seeds
	) {
		return seeds.stream()
				.map(seed -> withRuntime(seed, "a4mc-live-calibration"))
				.toList();
	}

	private static Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed withRuntime(
			Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed,
			String runtimeInfo
	) {
		return new Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed(
				seed.presetName(),
				seed.coefficientFitReady(),
				seed.sourceRunAvailable(),
				seed.referenceAreaSquareMeters(),
				seed.referenceLengthMeters(),
				seed.airDensityKgM3(),
				seed.dynamicPressurePascals(),
				seed.forceCoefficientX(),
				seed.forceCoefficientY(),
				seed.forceCoefficientZ(),
				seed.dragCoefficient(),
				seed.sideForceCoefficient(),
				seed.liftCoefficient(),
				seed.momentCoefficientX(),
				seed.momentCoefficientY(),
				seed.momentCoefficientZ(),
				seed.momentCoefficientMagnitude(),
				seed.pressureCenterOffsetRatio(),
				seed.sourceRunStatus(),
				runtimeInfo
		);
	}

	private static GameplayFitExtrema extrema(List<GameplayFitScenario> scenarios) {
		int candidates = 0;
		int forwardReady = 0;
		int fullAllowed = 0;
		int multiAxisRequired = 0;
		double maxTargetDrag = 0.0;
		double maxRatio = 0.0;
		double maxSide = 0.0;
		double maxLift = 0.0;
		double maxMoment = 0.0;
		double maxPressureCenter = 0.0;
		for (GameplayFitScenario scenario : scenarios) {
			for (GameplayFitCandidate candidate : scenario.candidates()) {
				candidates++;
				if (candidate.forwardDragCandidateReady()) {
					forwardReady++;
				}
				if (candidate.fullAirframeGameplayFitAllowed()) {
					fullAllowed++;
				}
				if (candidate.multiAxisSweepRequired()) {
					multiAxisRequired++;
				}
				maxTargetDrag = Math.max(maxTargetDrag, candidate.targetBodyDragZCoefficient());
				maxRatio = Math.max(maxRatio, candidate.targetToCurrentBodyDragZRatio());
				maxSide = Math.max(maxSide, Math.abs(candidate.sideForceResidualCoefficient()));
				maxLift = Math.max(maxLift, Math.abs(candidate.liftResidualCoefficient()));
				maxMoment = Math.max(maxMoment, candidate.momentCoefficientMagnitude());
				maxPressureCenter = Math.max(maxPressureCenter, candidate.pressureCenterOffsetRatio());
			}
		}
		return new GameplayFitExtrema(
				scenarios.size(),
				candidates,
				forwardReady,
				fullAllowed,
				multiAxisRequired,
				maxTargetDrag,
				maxRatio,
				maxSide,
				maxLift,
				maxMoment,
				maxPressureCenter
		);
	}

	private static double safeRatio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || denominator <= 1.0e-12) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private static boolean finiteNonNegative(double value) {
		return Double.isFinite(value) && value >= 0.0;
	}
}
