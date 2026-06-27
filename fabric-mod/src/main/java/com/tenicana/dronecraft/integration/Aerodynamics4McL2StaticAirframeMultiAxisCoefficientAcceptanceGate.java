package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate {
	public static final String SOURCE_ID = "A4MC-L2-Static-Airframe-Multi-Axis-Coefficient-Acceptance-Gate-Packet";
	public static final String CAVEAT =
			"Gate remains closed until every multi-axis static-airframe coefficient seed is present, bounded, sign-consistent, and produced by a live A4MC runtime rather than the test fixture.";
	public static final double MAX_ABS_FORCE_COEFFICIENT = 35.0;
	public static final double MAX_ABS_SIGNED_AXIAL_FORCE_COEFFICIENT = 35.0;
	public static final double MAX_MOMENT_COEFFICIENT_MAGNITUDE = 20.0;
	public static final double MAX_PRESSURE_CENTER_OFFSET_RATIO = 2.0;
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_COUNT = 22;
	public static final int SUMMARY_METRIC_ROW_COUNT = 10;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
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
	private static final Set<String> EXPECTED_KEYS = expectedKeys();
	private static final int EXPECTED_SEED_COUNT = EXPECTED_PRESET_NAMES.size() * EXPECTED_SWEEP_NAMES.size();
	private static final int EXPECTED_FORWARD_DRAG_SEED_COUNT = 8;
	private static final int EXPECTED_SIDEFORCE_SEED_COUNT = 8;
	private static final int EXPECTED_LIFT_SEED_COUNT = 8;
	private static final int EXPECTED_MOMENT_PRESSURE_CENTER_SEED_COUNT = EXPECTED_SEED_COUNT;

	private Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate() {
	}

	public record MultiAxisCoefficientAcceptanceSummary(
			int expectedSeedCount,
			int observedSeedCount,
			int fitReadyCount,
			int liveRuntimeCount,
			int boundedCoefficientCount,
			int forwardDragSeedCount,
			int sideforceSeedCount,
			int liftSeedCount,
			int momentPressureCenterSeedCount,
			int forwardReverseSignConsistentPresetCount,
			int missingSeedCount,
			int unexpectedSeedCount,
			double maxAbsForceCoefficient,
			double maxAbsSignedAxialForceCoefficient,
			double maxMomentCoefficientMagnitude,
			double maxPressureCenterOffsetRatio,
			boolean allSeedsPresent,
			boolean allSeedsFitReady,
			boolean allSeedsLiveRuntime,
			boolean allCoefficientsBounded,
			boolean allForwardReverseSignConsistent,
			boolean multiAxisGameplayFitAllowed
	) {
	}

	public record MultiAxisCoefficientAcceptanceScenario(
			String scenarioName,
			MultiAxisCoefficientAcceptanceSummary summary
	) {
	}

	public record MultiAxisCoefficientAcceptanceExtrema(
			int scenarioCount,
			int allowedScenarioCount,
			int blockedScenarioCount,
			int maxExpectedSeedCount,
			int maxMissingSeedCount,
			int maxUnexpectedSeedCount,
			double maxAbsForceCoefficient,
			double maxAbsSignedAxialForceCoefficient,
			double maxMomentCoefficientMagnitude,
			double maxPressureCenterOffsetRatio
	) {
	}

	public record MultiAxisCoefficientAcceptanceAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<MultiAxisCoefficientAcceptanceScenario> scenarios,
			MultiAxisCoefficientAcceptanceExtrema extrema
	) {
		public MultiAxisCoefficientAcceptanceAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static MultiAxisCoefficientAcceptanceAudit audit() {
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> productionSeeds =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.audit().seeds();
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> testSeeds =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.audit(
						Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.class.getClassLoader()).seeds();
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> liveSeeds =
				liveRuntimeSeeds(testSeeds);
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> signBrokenSeeds =
				forwardReverseSignBrokenSeeds(liveSeeds);
		List<MultiAxisCoefficientAcceptanceScenario> scenarios = List.of(
				new MultiAxisCoefficientAcceptanceScenario("current_runtime_unavailable", gate(productionSeeds)),
				new MultiAxisCoefficientAcceptanceScenario("test_runtime_fit_ready_blocked", gate(testSeeds)),
				new MultiAxisCoefficientAcceptanceScenario("live_runtime_all_bounded_sign_consistent", gate(liveSeeds)),
				new MultiAxisCoefficientAcceptanceScenario("live_runtime_forward_reverse_sign_broken", gate(signBrokenSeeds))
		);
		return new MultiAxisCoefficientAcceptanceAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				SCENARIO_SAMPLE_COUNT,
				SCENARIO_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				scenarios,
				extrema(scenarios)
		);
	}

	public static MultiAxisCoefficientAcceptanceSummary gate(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds
	) {
		if (seeds == null) {
			throw new IllegalArgumentException("seeds must not be null.");
		}
		Set<String> observedKeys = new HashSet<>();
		Map<String, Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seedsByKey =
				new HashMap<>();
		int fitReady = 0;
		int liveRuntime = 0;
		int bounded = 0;
		int forwardDrag = 0;
		int sideforce = 0;
		int lift = 0;
		int momentPressureCenter = 0;
		int unexpected = 0;
		double maxAbsForce = 0.0;
		double maxAbsAxial = 0.0;
		double maxMoment = 0.0;
		double maxPressureCenter = 0.0;
		for (Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed : seeds) {
			if (seed == null || seed.presetName() == null || seed.presetName().isBlank()
					|| seed.sweepName() == null || seed.sweepName().isBlank()) {
				throw new IllegalArgumentException("seeds must include stable preset and sweep names.");
			}
			String key = key(seed.presetName(), seed.sweepName());
			if (!observedKeys.add(key)) {
				throw new IllegalArgumentException("duplicate multi-axis coefficient seed: " + key);
			}
			seedsByKey.put(key, seed);
			if (!EXPECTED_KEYS.contains(key)) {
				unexpected++;
			}
			if (seed.coefficientFitReady()) {
				fitReady++;
			}
			if (isLiveRuntime(seed)) {
				liveRuntime++;
			}
			if (seed.forwardDragFitSample()) {
				forwardDrag++;
			}
			if (seed.sideforceFitSample()) {
				sideforce++;
			}
			if (seed.liftFitSample()) {
				lift++;
			}
			if (seed.momentPressureCenterFitSample()) {
				momentPressureCenter++;
			}
			maxAbsForce = Math.max(maxAbsForce, maxAbs(
					seed.forceCoefficientX(),
					seed.forceCoefficientY(),
					seed.forceCoefficientZ()
			));
			maxAbsAxial = Math.max(maxAbsAxial, Math.abs(seed.signedAxialForceCoefficient()));
			maxMoment = Math.max(maxMoment, seed.momentCoefficientMagnitude());
			maxPressureCenter = Math.max(maxPressureCenter, seed.pressureCenterOffsetRatio());
			if (isBounded(seed)) {
				bounded++;
			}
		}
		int missing = 0;
		for (String expectedKey : EXPECTED_KEYS) {
			if (!observedKeys.contains(expectedKey)) {
				missing++;
			}
		}
		int signConsistentPresets = forwardReverseSignConsistentPresetCount(seedsByKey);
		boolean allPresent = missing == 0 && unexpected == 0 && observedKeys.size() == EXPECTED_SEED_COUNT;
		boolean allFitReady = fitReady == EXPECTED_SEED_COUNT;
		boolean allLiveRuntime = liveRuntime == EXPECTED_SEED_COUNT;
		boolean allBounded = bounded == EXPECTED_SEED_COUNT;
		boolean allSignConsistent = signConsistentPresets == EXPECTED_PRESET_NAMES.size();
		boolean allRolesPresent = forwardDrag == EXPECTED_FORWARD_DRAG_SEED_COUNT
				&& sideforce == EXPECTED_SIDEFORCE_SEED_COUNT
				&& lift == EXPECTED_LIFT_SEED_COUNT
				&& momentPressureCenter == EXPECTED_MOMENT_PRESSURE_CENTER_SEED_COUNT;
		boolean allowed = allPresent
				&& allFitReady
				&& allLiveRuntime
				&& allBounded
				&& allSignConsistent
				&& allRolesPresent;
		return new MultiAxisCoefficientAcceptanceSummary(
				EXPECTED_SEED_COUNT,
				seeds.size(),
				fitReady,
				liveRuntime,
				bounded,
				forwardDrag,
				sideforce,
				lift,
				momentPressureCenter,
				signConsistentPresets,
				missing,
				unexpected,
				maxAbsForce,
				maxAbsAxial,
				maxMoment,
				maxPressureCenter,
				allPresent,
				allFitReady,
				allLiveRuntime,
				allBounded,
				allSignConsistent,
				allowed
		);
	}

	private static Set<String> expectedKeys() {
		Set<String> keys = new HashSet<>();
		for (String presetName : EXPECTED_PRESET_NAMES) {
			for (String sweepName : EXPECTED_SWEEP_NAMES) {
				keys.add(key(presetName, sweepName));
			}
		}
		return Set.copyOf(keys);
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
		return finiteWithinAbs(seed.forceCoefficientX(), MAX_ABS_FORCE_COEFFICIENT)
				&& finiteWithinAbs(seed.forceCoefficientY(), MAX_ABS_FORCE_COEFFICIENT)
				&& finiteWithinAbs(seed.forceCoefficientZ(), MAX_ABS_FORCE_COEFFICIENT)
				&& finiteWithinAbs(seed.signedAxialForceCoefficient(), MAX_ABS_SIGNED_AXIAL_FORCE_COEFFICIENT)
				&& finiteWithinAbs(seed.sideForceCoefficient(), MAX_ABS_FORCE_COEFFICIENT)
				&& finiteWithinAbs(seed.liftCoefficient(), MAX_ABS_FORCE_COEFFICIENT)
				&& finiteNonNegative(seed.momentCoefficientMagnitude(), MAX_MOMENT_COEFFICIENT_MAGNITUDE)
				&& finiteNonNegative(seed.pressureCenterOffsetRatio(), MAX_PRESSURE_CENTER_OFFSET_RATIO);
	}

	private static int forwardReverseSignConsistentPresetCount(
			Map<String, Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seedsByKey
	) {
		int consistent = 0;
		for (String presetName : EXPECTED_PRESET_NAMES) {
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed forward =
					seedsByKey.get(key(presetName, "forward_drag"));
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed reverse =
					seedsByKey.get(key(presetName, "reverse_drag_symmetry"));
			if (forward != null
					&& reverse != null
					&& Double.isFinite(forward.signedAxialForceCoefficient())
					&& Double.isFinite(reverse.signedAxialForceCoefficient())
					&& forward.signedAxialForceCoefficient() * reverse.signedAxialForceCoefficient() < 0.0) {
				consistent++;
			}
		}
		return consistent;
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
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> broken =
				new ArrayList<>(seeds);
		int forwardIndex = -1;
		int reverseIndex = -1;
		for (int index = 0; index < broken.size(); index++) {
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed =
					broken.get(index);
			if ("racingQuad".equals(seed.presetName()) && "forward_drag".equals(seed.sweepName())) {
				forwardIndex = index;
			}
			if ("racingQuad".equals(seed.presetName()) && "reverse_drag_symmetry".equals(seed.sweepName())) {
				reverseIndex = index;
			}
		}
		if (forwardIndex >= 0 && reverseIndex >= 0) {
			double signedAxial = Math.copySign(
					Math.abs(broken.get(reverseIndex).signedAxialForceCoefficient()),
					broken.get(forwardIndex).signedAxialForceCoefficient()
			);
			broken.set(reverseIndex, withSignedAxial(broken.get(reverseIndex), signedAxial));
		}
		return broken;
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed withRuntime(
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed,
			String runtimeInfo
	) {
		return copy(seed, seed.presetName(), seed.sweepName(), seed.forceCoefficientX(),
				seed.signedAxialForceCoefficient(), runtimeInfo);
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed withSignedAxial(
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed,
			double signedAxialForceCoefficient
	) {
		return copy(seed, seed.presetName(), seed.sweepName(), seed.forceCoefficientX(),
				signedAxialForceCoefficient, seed.sourceRuntimeInfo());
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed copy(
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed,
			String presetName,
			String sweepName,
			double forceCoefficientX,
			double signedAxialForceCoefficient,
			String runtimeInfo
	) {
		return new Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed(
				presetName,
				sweepName,
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
				forceCoefficientX,
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

	private static MultiAxisCoefficientAcceptanceExtrema extrema(
			List<MultiAxisCoefficientAcceptanceScenario> scenarios
	) {
		int allowed = 0;
		int maxExpected = 0;
		int maxMissing = 0;
		int maxUnexpected = 0;
		double maxAbsForce = 0.0;
		double maxAbsAxial = 0.0;
		double maxMoment = 0.0;
		double maxPressureCenter = 0.0;
		for (MultiAxisCoefficientAcceptanceScenario scenario : scenarios) {
			MultiAxisCoefficientAcceptanceSummary summary = scenario.summary();
			if (summary.multiAxisGameplayFitAllowed()) {
				allowed++;
			}
			maxExpected = Math.max(maxExpected, summary.expectedSeedCount());
			maxMissing = Math.max(maxMissing, summary.missingSeedCount());
			maxUnexpected = Math.max(maxUnexpected, summary.unexpectedSeedCount());
			maxAbsForce = Math.max(maxAbsForce, summary.maxAbsForceCoefficient());
			maxAbsAxial = Math.max(maxAbsAxial, summary.maxAbsSignedAxialForceCoefficient());
			maxMoment = Math.max(maxMoment, summary.maxMomentCoefficientMagnitude());
			maxPressureCenter = Math.max(maxPressureCenter, summary.maxPressureCenterOffsetRatio());
		}
		return new MultiAxisCoefficientAcceptanceExtrema(
				scenarios.size(),
				allowed,
				scenarios.size() - allowed,
				maxExpected,
				maxMissing,
				maxUnexpected,
				maxAbsForce,
				maxAbsAxial,
				maxMoment,
				maxPressureCenter
		);
	}

	private static boolean finiteNonNegative(double value, double max) {
		return Double.isFinite(value) && value >= 0.0 && value <= max;
	}

	private static boolean finiteWithinAbs(double value, double maxAbs) {
		return Double.isFinite(value) && Math.abs(value) <= maxAbs;
	}

	private static String key(String presetName, String sweepName) {
		return presetName + ":" + sweepName;
	}

	private static double maxAbs(double x, double y, double z) {
		return Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z));
	}
}
