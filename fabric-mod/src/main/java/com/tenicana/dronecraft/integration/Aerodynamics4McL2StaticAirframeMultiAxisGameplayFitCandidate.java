package com.tenicana.dronecraft.integration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate {
	public static final String SOURCE_ID = "A4MC-L2-Static-Airframe-Multi-Axis-Gameplay-Fit-Candidate-Packet";
	public static final String CAVEAT =
			"Multi-axis gameplay-fit candidates are audit-only: they map accepted live A4MC coefficient sweeps into body-drag, sideforce, lift, moment, and pressure-center targets without automatically changing DroneConfig.";
	public static final double SIDEFORCE_SWEEP_ANGLE_RADIANS = Math.toRadians(12.0);
	public static final double LIFT_SWEEP_ANGLE_RADIANS = Math.toRadians(12.0);
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int CANDIDATE_METRIC_COUNT = 30;
	public static final int SUMMARY_METRIC_ROW_COUNT = 15;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * PRESET_SAMPLE_COUNT * CANDIDATE_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private static final List<String> EXPECTED_PRESET_NAMES = List.of(
			"racingQuad",
			"apDrone",
			"cinewhoop",
			"heavyLift"
	);
	private static final List<String> EXPECTED_SWEEP_NAMES = List.of(
			"forward_drag",
			"reverse_drag_symmetry",
			"right_sideslip_12deg",
			"left_sideslip_12deg",
			"positive_aoa_12deg",
			"negative_aoa_12deg"
	);
	private static final List<String> FORWARD_SWEEP_NAMES = List.of("forward_drag", "reverse_drag_symmetry");
	private static final List<String> SIDEFORCE_SWEEP_NAMES = List.of("right_sideslip_12deg", "left_sideslip_12deg");
	private static final List<String> LIFT_SWEEP_NAMES = List.of("positive_aoa_12deg", "negative_aoa_12deg");

	private Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate() {
	}

	public record MultiAxisGameplayFitCandidate(
			String scenarioName,
			String presetName,
			boolean coefficientGateAllowed,
			int expectedSweepCount,
			int observedSweepCount,
			int fitReadySweepCount,
			int liveRuntimeSweepCount,
			int boundedSweepCount,
			boolean multiAxisCandidateReady,
			boolean runtimeConfigAutoApplyAllowed,
			double currentBodyDragXCoefficient,
			double targetBodyDragXCoefficient,
			double targetToCurrentBodyDragXRatio,
			double currentBodyDragYCoefficient,
			double targetBodyDragYCoefficient,
			double targetToCurrentBodyDragYRatio,
			double currentBodyDragZCoefficient,
			double targetBodyDragZCoefficient,
			double targetToCurrentBodyDragZRatio,
			double currentSideforceGain,
			double targetSideforceGain,
			double targetToCurrentSideforceGainRatio,
			double currentPitchLiftGain,
			double targetPitchLiftGain,
			double targetToCurrentPitchLiftGainRatio,
			double currentAngularDragCoefficient,
			double targetMomentCoefficientMagnitude,
			double maxPressureCenterOffsetMeters,
			double currentPressureCenterOffsetMeters,
			double maxPressureCenterOffsetRatio,
			boolean pressureCenterVectorResolved,
			String sourceRuntimeInfo
	) {
	}

	public record MultiAxisGameplayFitScenario(
			String scenarioName,
			List<MultiAxisGameplayFitCandidate> candidates
	) {
		public MultiAxisGameplayFitScenario {
			candidates = List.copyOf(candidates);
		}
	}

	public record MultiAxisGameplayFitExtrema(
			int scenarioCount,
			int candidateCount,
			int multiAxisCandidateReadyCount,
			int runtimeConfigAutoApplyAllowedCount,
			int pressureCenterVectorResolvedCount,
			double maxTargetBodyDragXCoefficient,
			double maxTargetBodyDragYCoefficient,
			double maxTargetBodyDragZCoefficient,
			double maxTargetToCurrentBodyDragRatio,
			double maxTargetSideforceGain,
			double maxTargetPitchLiftGain,
			double maxTargetToCurrentSideforceGainRatio,
			double maxTargetToCurrentPitchLiftGainRatio,
			double maxTargetMomentCoefficientMagnitude,
			double maxPressureCenterOffsetMeters
	) {
	}

	public record MultiAxisGameplayFitAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int presetSampleCount,
			int candidateMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<MultiAxisGameplayFitScenario> scenarios,
			MultiAxisGameplayFitExtrema extrema
	) {
		public MultiAxisGameplayFitAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static MultiAxisGameplayFitAudit audit() {
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> productionSeeds =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.audit().seeds();
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> testSeeds =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.audit(
						Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.class.getClassLoader()).seeds();
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> liveSeeds =
				liveRuntimeSeeds(testSeeds);
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> signBrokenSeeds =
				forwardReverseSignBrokenSeeds(liveSeeds);
		List<MultiAxisGameplayFitScenario> scenarios = List.of(
				scenario("current_runtime_unavailable", productionSeeds),
				scenario("test_runtime_fit_ready_blocked", testSeeds),
				scenario("live_runtime_full_multi_axis_candidate", liveSeeds),
				scenario("live_runtime_forward_reverse_sign_broken", signBrokenSeeds)
		);
		return new MultiAxisGameplayFitAudit(
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

	public static MultiAxisGameplayFitScenario scenario(
			String scenarioName,
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (seeds == null) {
			throw new IllegalArgumentException("seeds must not be null.");
		}
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceSummary gate =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.gate(seeds);
		return new MultiAxisGameplayFitScenario(
				scenarioName,
				EXPECTED_PRESET_NAMES.stream()
						.map(presetName -> candidate(scenarioName, presetName, seedsForPreset(seeds, presetName), gate))
						.toList()
		);
	}

	public static MultiAxisGameplayFitCandidate candidate(
			String scenarioName,
			String presetName,
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds,
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceSummary gate
	) {
		if (scenarioName == null || scenarioName.isBlank()) {
			throw new IllegalArgumentException("scenarioName must not be blank.");
		}
		if (presetName == null || presetName.isBlank() || seeds == null || gate == null) {
			throw new IllegalArgumentException("presetName, seeds, and gate summary are required.");
		}
		DroneConfig config = configForPreset(presetName);
		int fitReady = 0;
		int liveRuntime = 0;
		int bounded = 0;
		Set<String> observedSweeps = new HashSet<>();
		for (Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed : seeds) {
			if (seed == null || seed.sweepName() == null || seed.sweepName().isBlank()) {
				throw new IllegalArgumentException("seeds must include stable sweep names.");
			}
			if (seed.coefficientFitReady()) {
				fitReady++;
			}
			if (isLiveRuntime(seed)) {
				liveRuntime++;
			}
			if (isBounded(seed)) {
				bounded++;
			}
			observedSweeps.add(seed.sweepName());
		}
		Vec3 currentDrag = config.bodyDragCoefficients();
		double targetBodyDragX = targetBodyDragCoefficient(seeds, SIDEFORCE_SWEEP_NAMES);
		double targetBodyDragY = targetBodyDragCoefficient(seeds, LIFT_SWEEP_NAMES);
		double targetBodyDragZ = targetBodyDragCoefficient(seeds, FORWARD_SWEEP_NAMES);
		double currentSideforceGain = 0.065 * Math.sqrt(currentDrag.x() * currentDrag.z());
		double targetSideforceGain = targetForceGain(
				seeds,
				SIDEFORCE_SWEEP_NAMES,
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed::sideForceCoefficient,
				SIDEFORCE_SWEEP_ANGLE_RADIANS
		);
		double currentPitchLiftGain = 0.085 * Math.sqrt(currentDrag.y() * currentDrag.z());
		double targetPitchLiftGain = targetForceGain(
				seeds,
				LIFT_SWEEP_NAMES,
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed::liftCoefficient,
				LIFT_SWEEP_ANGLE_RADIANS
		);
		double targetMoment = max(
				seeds,
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed::momentCoefficientMagnitude
		);
		double maxPressureCenterRatio = max(
				seeds,
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed::pressureCenterOffsetRatio
		);
		double maxPressureCenterMeters = maxPressureCenterOffsetMeters(seeds);
		boolean pressureCenterVectorResolved = false;
		boolean ready = gate.multiAxisGameplayFitAllowed()
				&& observedSweeps.size() == EXPECTED_SWEEP_NAMES.size()
				&& fitReady == EXPECTED_SWEEP_NAMES.size()
				&& liveRuntime == EXPECTED_SWEEP_NAMES.size()
				&& bounded == EXPECTED_SWEEP_NAMES.size()
				&& finiteNonNegative(targetBodyDragX)
				&& finiteNonNegative(targetBodyDragY)
				&& finiteNonNegative(targetBodyDragZ)
				&& finiteNonNegative(targetSideforceGain)
				&& finiteNonNegative(targetPitchLiftGain)
				&& finiteNonNegative(targetMoment)
				&& finiteNonNegative(maxPressureCenterMeters);
		boolean autoApply = ready && pressureCenterVectorResolved;
		return new MultiAxisGameplayFitCandidate(
				scenarioName,
				presetName,
				gate.multiAxisGameplayFitAllowed(),
				EXPECTED_SWEEP_NAMES.size(),
				observedSweeps.size(),
				fitReady,
				liveRuntime,
				bounded,
				ready,
				autoApply,
				currentDrag.x(),
				targetBodyDragX,
				safeRatio(targetBodyDragX, currentDrag.x()),
				currentDrag.y(),
				targetBodyDragY,
				safeRatio(targetBodyDragY, currentDrag.y()),
				currentDrag.z(),
				targetBodyDragZ,
				safeRatio(targetBodyDragZ, currentDrag.z()),
				currentSideforceGain,
				targetSideforceGain,
				safeRatio(targetSideforceGain, currentSideforceGain),
				currentPitchLiftGain,
				targetPitchLiftGain,
				safeRatio(targetPitchLiftGain, currentPitchLiftGain),
				config.angularDragCoefficient(),
				targetMoment,
				maxPressureCenterMeters,
				config.centerOfPressureOffsetBodyMeters().length(),
				maxPressureCenterRatio,
				pressureCenterVectorResolved,
				sourceRuntimeInfo(seeds)
		);
	}

	private static DroneConfig configForPreset(String presetName) {
		return switch (presetName) {
			case "racingQuad" -> DroneConfig.racingQuad();
			case "apDrone" -> DroneConfig.apDrone();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavyLift" -> DroneConfig.heavyLift();
			default -> throw new IllegalArgumentException("unsupported static-airframe multi-axis gameplay-fit preset: " + presetName);
		};
	}

	private static List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seedsForPreset(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds,
			String presetName
	) {
		return seeds.stream()
				.filter(seed -> seed != null && presetName.equals(seed.presetName()))
				.toList();
	}

	private static boolean isLiveRuntime(
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed
	) {
		String runtime = seed.sourceRuntimeInfo();
		return seed.sourceRunAvailable()
				&& runtime != null
				&& !runtime.isBlank()
				&& !"none".equals(runtime)
				&& !"test-runtime".equals(runtime);
	}

	private static boolean isBounded(
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed
	) {
		return finiteWithinAbs(seed.forceCoefficientX(),
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MAX_ABS_FORCE_COEFFICIENT)
				&& finiteWithinAbs(seed.forceCoefficientY(),
						Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MAX_ABS_FORCE_COEFFICIENT)
				&& finiteWithinAbs(seed.forceCoefficientZ(),
						Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MAX_ABS_FORCE_COEFFICIENT)
				&& finiteWithinAbs(seed.signedAxialForceCoefficient(),
						Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MAX_ABS_SIGNED_AXIAL_FORCE_COEFFICIENT)
				&& finiteWithinAbs(seed.sideForceCoefficient(),
						Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MAX_ABS_FORCE_COEFFICIENT)
				&& finiteWithinAbs(seed.liftCoefficient(),
						Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MAX_ABS_FORCE_COEFFICIENT)
				&& finiteNonNegative(seed.momentCoefficientMagnitude())
				&& seed.momentCoefficientMagnitude()
						<= Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MAX_MOMENT_COEFFICIENT_MAGNITUDE
				&& finiteNonNegative(seed.pressureCenterOffsetRatio())
				&& seed.pressureCenterOffsetRatio()
						<= Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MAX_PRESSURE_CENTER_OFFSET_RATIO;
	}

	private static double targetBodyDragCoefficient(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds,
			List<String> sweepNames
	) {
		return meanTarget(seeds, sweepNames, seed -> Math.abs(seed.signedAxialForceCoefficient()), 1.0);
	}

	private static double targetForceGain(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds,
			List<String> sweepNames,
			ToDoubleFunction<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> coefficient,
			double sweepAngleRadians
	) {
		double responseScale = Math.max(1.0e-12, Math.abs(Math.sin(2.0 * sweepAngleRadians)));
		return meanTarget(seeds, sweepNames, coefficient, responseScale);
	}

	private static double meanTarget(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds,
			List<String> sweepNames,
			ToDoubleFunction<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> coefficient,
			double responseScale
	) {
		double sum = 0.0;
		int count = 0;
		for (String sweepName : sweepNames) {
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed =
					findSeed(seeds, sweepName);
			if (seed == null) {
				continue;
			}
			double target = 0.5
					* seed.airDensityKgM3()
					* seed.projectedReferenceAreaSquareMeters()
					* Math.abs(coefficient.applyAsDouble(seed))
					/ responseScale;
			if (Double.isFinite(target)) {
				sum += target;
				count++;
			}
		}
		return count == 0 ? 0.0 : sum / count;
	}

	private static double max(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds,
			ToDoubleFunction<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> value
	) {
		double max = 0.0;
		for (Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed : seeds) {
			double next = value.applyAsDouble(seed);
			if (Double.isFinite(next)) {
				max = Math.max(max, next);
			}
		}
		return max;
	}

	private static double maxPressureCenterOffsetMeters(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds
	) {
		double max = 0.0;
		for (Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed : seeds) {
			double offset = seed.pressureCenterOffsetRatio() * seed.referenceLengthMeters();
			if (Double.isFinite(offset)) {
				max = Math.max(max, offset);
			}
		}
		return max;
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed findSeed(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds,
			String sweepName
	) {
		for (Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed : seeds) {
			if (sweepName.equals(seed.sweepName())) {
				return seed;
			}
		}
		return null;
	}

	private static String sourceRuntimeInfo(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds
	) {
		if (seeds.isEmpty()) {
			return "none";
		}
		Set<String> runtimes = new HashSet<>();
		for (Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed : seeds) {
			String runtime = seed.sourceRuntimeInfo();
			runtimes.add(runtime == null || runtime.isBlank() ? "none" : runtime);
		}
		return runtimes.size() == 1 ? runtimes.iterator().next() : "mixed";
	}

	private static List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> liveRuntimeSeeds(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds
	) {
		return seeds.stream()
				.map(seed -> withRuntime(seed, "a4mc-live-calibration"))
				.toList();
	}

	private static List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> forwardReverseSignBrokenSeeds(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds
	) {
		return seeds.stream()
				.map(seed -> "racingQuad".equals(seed.presetName()) && "reverse_drag_symmetry".equals(seed.sweepName())
						? withSignedAxial(seed, Math.abs(seed.signedAxialForceCoefficient()))
						: seed)
				.toList();
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed withRuntime(
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed,
			String runtimeInfo
	) {
		return copy(seed, seed.signedAxialForceCoefficient(), runtimeInfo);
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed withSignedAxial(
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed,
			double signedAxialForceCoefficient
	) {
		return copy(seed, signedAxialForceCoefficient, seed.sourceRuntimeInfo());
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed copy(
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed,
			double signedAxialForceCoefficient,
			String runtimeInfo
	) {
		return new Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed(
				seed.presetName(),
				seed.sweepName(),
				seed.fitRole(),
				seed.coefficientFitReady(),
				seed.sourceRunAvailable(),
				seed.forwardDragFitSample(),
				seed.sideforceFitSample(),
				seed.liftFitSample(),
				seed.momentPressureCenterFitSample(),
				seed.airDensityKgM3(),
				seed.dynamicPressurePascals(),
				seed.projectedReferenceAreaSquareMeters(),
				seed.referenceLengthMeters(),
				seed.forceCoefficientX(),
				seed.forceCoefficientY(),
				seed.forceCoefficientZ(),
				signedAxialForceCoefficient,
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

	private static MultiAxisGameplayFitExtrema extrema(List<MultiAxisGameplayFitScenario> scenarios) {
		int candidates = 0;
		int ready = 0;
		int autoApply = 0;
		int pressureCenterResolved = 0;
		double maxTargetDragX = 0.0;
		double maxTargetDragY = 0.0;
		double maxTargetDragZ = 0.0;
		double maxTargetToCurrentDrag = 0.0;
		double maxTargetSideforce = 0.0;
		double maxTargetLift = 0.0;
		double maxTargetToCurrentSideforce = 0.0;
		double maxTargetToCurrentLift = 0.0;
		double maxTargetMoment = 0.0;
		double maxPressureCenter = 0.0;
		for (MultiAxisGameplayFitScenario scenario : scenarios) {
			for (MultiAxisGameplayFitCandidate candidate : scenario.candidates()) {
				candidates++;
				if (candidate.multiAxisCandidateReady()) {
					ready++;
				}
				if (candidate.runtimeConfigAutoApplyAllowed()) {
					autoApply++;
				}
				if (candidate.pressureCenterVectorResolved()) {
					pressureCenterResolved++;
				}
				maxTargetDragX = Math.max(maxTargetDragX, candidate.targetBodyDragXCoefficient());
				maxTargetDragY = Math.max(maxTargetDragY, candidate.targetBodyDragYCoefficient());
				maxTargetDragZ = Math.max(maxTargetDragZ, candidate.targetBodyDragZCoefficient());
				maxTargetToCurrentDrag = Math.max(maxTargetToCurrentDrag, Math.max(
						Math.max(candidate.targetToCurrentBodyDragXRatio(), candidate.targetToCurrentBodyDragYRatio()),
						candidate.targetToCurrentBodyDragZRatio()
				));
				maxTargetSideforce = Math.max(maxTargetSideforce, candidate.targetSideforceGain());
				maxTargetLift = Math.max(maxTargetLift, candidate.targetPitchLiftGain());
				maxTargetToCurrentSideforce = Math.max(maxTargetToCurrentSideforce,
						candidate.targetToCurrentSideforceGainRatio());
				maxTargetToCurrentLift = Math.max(maxTargetToCurrentLift,
						candidate.targetToCurrentPitchLiftGainRatio());
				maxTargetMoment = Math.max(maxTargetMoment, candidate.targetMomentCoefficientMagnitude());
				maxPressureCenter = Math.max(maxPressureCenter, candidate.maxPressureCenterOffsetMeters());
			}
		}
		return new MultiAxisGameplayFitExtrema(
				scenarios.size(),
				candidates,
				ready,
				autoApply,
				pressureCenterResolved,
				maxTargetDragX,
				maxTargetDragY,
				maxTargetDragZ,
				maxTargetToCurrentDrag,
				maxTargetSideforce,
				maxTargetLift,
				maxTargetToCurrentSideforce,
				maxTargetToCurrentLift,
				maxTargetMoment,
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

	private static boolean finiteWithinAbs(double value, double maxAbs) {
		return Double.isFinite(value) && Math.abs(value) <= maxAbs;
	}
}
