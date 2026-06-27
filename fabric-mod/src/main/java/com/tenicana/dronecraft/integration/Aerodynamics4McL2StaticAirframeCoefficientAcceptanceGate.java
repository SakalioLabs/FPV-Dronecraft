package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate {
	public static final String SOURCE_ID = "A4MC-L2-Static-Airframe-Coefficient-Acceptance-Gate-Packet";
	public static final String CAVEAT =
			"Gate remains closed until every static-airframe coefficient seed is present, bounded, and produced by a live A4MC runtime rather than the test fixture.";
	public static final double MAX_DRAG_COEFFICIENT = 30.0;
	public static final double MAX_FORCE_COEFFICIENT_MAGNITUDE = 35.0;
	public static final double MAX_MOMENT_COEFFICIENT_MAGNITUDE = 20.0;
	public static final double MAX_PRESSURE_CENTER_OFFSET_RATIO = 2.0;
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int SCENARIO_SAMPLE_COUNT = 4;
	public static final int SCENARIO_METRIC_COUNT = 16;
	public static final int SUMMARY_METRIC_ROW_COUNT = 9;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ SCENARIO_SAMPLE_COUNT * SCENARIO_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate() {
	}

	public record CoefficientAcceptanceSummary(
			int expectedSeedCount,
			int observedSeedCount,
			int fitReadyCount,
			int liveRuntimeCount,
			int boundedCoefficientCount,
			int missingSeedCount,
			int unexpectedSeedCount,
			double maxDragCoefficient,
			double maxForceCoefficientMagnitude,
			double maxMomentCoefficientMagnitude,
			double maxPressureCenterOffsetRatio,
			boolean allSeedsPresent,
			boolean allSeedsFitReady,
			boolean allSeedsLiveRuntime,
			boolean allCoefficientsBounded,
			boolean gameplayCoefficientFitAllowed
	) {
	}

	public record CoefficientAcceptanceScenario(
			String scenarioName,
			CoefficientAcceptanceSummary summary
	) {
	}

	public record CoefficientAcceptanceExtrema(
			int scenarioCount,
			int allowedScenarioCount,
			int blockedScenarioCount,
			int maxExpectedSeedCount,
			int maxMissingSeedCount,
			int maxUnexpectedSeedCount,
			double maxDragCoefficient,
			double maxForceCoefficientMagnitude,
			double maxMomentCoefficientMagnitude
	) {
	}

	public record CoefficientAcceptanceAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int scenarioSampleCount,
			int scenarioMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<CoefficientAcceptanceScenario> scenarios,
			CoefficientAcceptanceExtrema extrema
	) {
		public CoefficientAcceptanceAudit {
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CoefficientAcceptanceAudit audit() {
		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> productionSeeds =
				Aerodynamics4McL2StaticAirframeCoefficientSeed.audit().seeds();
		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> testSeeds =
				Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(
						Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.class.getClassLoader()).seeds();
		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> liveSeeds =
				liveRuntimeSeeds(testSeeds);
		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> outOfBounds =
				new ArrayList<>(liveSeeds);
		outOfBounds.set(0, withDrag(outOfBounds.get(0), MAX_DRAG_COEFFICIENT + 5.0));
		List<CoefficientAcceptanceScenario> scenarios = List.of(
				new CoefficientAcceptanceScenario("current_runtime_unavailable", gate(productionSeeds)),
				new CoefficientAcceptanceScenario("test_runtime_fit_ready_blocked", gate(testSeeds)),
				new CoefficientAcceptanceScenario("live_runtime_all_bounded", gate(liveSeeds)),
				new CoefficientAcceptanceScenario("live_runtime_one_out_of_bounds", gate(outOfBounds))
		);
		return new CoefficientAcceptanceAudit(
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

	public static CoefficientAcceptanceSummary gate(
			List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> seeds
	) {
		if (seeds == null) {
			throw new IllegalArgumentException("seeds must not be null.");
		}
		Set<String> expectedNames = Set.of("racingQuad", "apDrone", "cinewhoop", "heavyLift");
		Set<String> observedNames = new HashSet<>();
		int fitReady = 0;
		int liveRuntime = 0;
		int bounded = 0;
		int unexpected = 0;
		double maxDrag = 0.0;
		double maxForce = 0.0;
		double maxMoment = 0.0;
		double maxPressureCenter = 0.0;
		for (Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed : seeds) {
			if (seed == null || seed.presetName() == null || seed.presetName().isBlank()) {
				throw new IllegalArgumentException("seeds must include stable preset names.");
			}
			if (!observedNames.add(seed.presetName())) {
				throw new IllegalArgumentException("duplicate coefficient seed preset: " + seed.presetName());
			}
			if (!expectedNames.contains(seed.presetName())) {
				unexpected++;
			}
			if (seed.coefficientFitReady()) {
				fitReady++;
			}
			if (isLiveRuntime(seed)) {
				liveRuntime++;
			}
			double forceMagnitude = magnitude(
					seed.forceCoefficientX(),
					seed.forceCoefficientY(),
					seed.forceCoefficientZ()
			);
			maxDrag = Math.max(maxDrag, seed.dragCoefficient());
			maxForce = Math.max(maxForce, forceMagnitude);
			maxMoment = Math.max(maxMoment, seed.momentCoefficientMagnitude());
			maxPressureCenter = Math.max(maxPressureCenter, seed.pressureCenterOffsetRatio());
			if (isBounded(seed, forceMagnitude)) {
				bounded++;
			}
		}
		int missing = 0;
		for (String expectedName : expectedNames) {
			if (!observedNames.contains(expectedName)) {
				missing++;
			}
		}
		boolean allPresent = missing == 0 && unexpected == 0 && observedNames.size() == expectedNames.size();
		boolean allFitReady = fitReady == expectedNames.size();
		boolean allLiveRuntime = liveRuntime == expectedNames.size();
		boolean allBounded = bounded == expectedNames.size();
		boolean allowed = allPresent && allFitReady && allLiveRuntime && allBounded;
		return new CoefficientAcceptanceSummary(
				expectedNames.size(),
				seeds.size(),
				fitReady,
				liveRuntime,
				bounded,
				missing,
				unexpected,
				maxDrag,
				maxForce,
				maxMoment,
				maxPressureCenter,
				allPresent,
				allFitReady,
				allLiveRuntime,
				allBounded,
				allowed
		);
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

	private static boolean isBounded(
			Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed,
			double forceMagnitude
	) {
		return finiteNonNegative(seed.dragCoefficient(), MAX_DRAG_COEFFICIENT)
				&& finiteWithinAbs(forceMagnitude, MAX_FORCE_COEFFICIENT_MAGNITUDE)
				&& finiteWithinAbs(seed.momentCoefficientMagnitude(), MAX_MOMENT_COEFFICIENT_MAGNITUDE)
				&& finiteNonNegative(seed.pressureCenterOffsetRatio(), MAX_PRESSURE_CENTER_OFFSET_RATIO);
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

	private static Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed withDrag(
			Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed,
			double dragCoefficient
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
				dragCoefficient,
				seed.sideForceCoefficient(),
				seed.liftCoefficient(),
				seed.momentCoefficientX(),
				seed.momentCoefficientY(),
				seed.momentCoefficientZ(),
				seed.momentCoefficientMagnitude(),
				seed.pressureCenterOffsetRatio(),
				seed.sourceRunStatus(),
				seed.sourceRuntimeInfo()
		);
	}

	private static CoefficientAcceptanceExtrema extrema(List<CoefficientAcceptanceScenario> scenarios) {
		int allowed = 0;
		int maxExpected = 0;
		int maxMissing = 0;
		int maxUnexpected = 0;
		double maxDrag = 0.0;
		double maxForce = 0.0;
		double maxMoment = 0.0;
		for (CoefficientAcceptanceScenario scenario : scenarios) {
			CoefficientAcceptanceSummary summary = scenario.summary();
			if (summary.gameplayCoefficientFitAllowed()) {
				allowed++;
			}
			maxExpected = Math.max(maxExpected, summary.expectedSeedCount());
			maxMissing = Math.max(maxMissing, summary.missingSeedCount());
			maxUnexpected = Math.max(maxUnexpected, summary.unexpectedSeedCount());
			maxDrag = Math.max(maxDrag, summary.maxDragCoefficient());
			maxForce = Math.max(maxForce, summary.maxForceCoefficientMagnitude());
			maxMoment = Math.max(maxMoment, summary.maxMomentCoefficientMagnitude());
		}
		return new CoefficientAcceptanceExtrema(
				scenarios.size(),
				allowed,
				scenarios.size() - allowed,
				maxExpected,
				maxMissing,
				maxUnexpected,
				maxDrag,
				maxForce,
				maxMoment
		);
	}

	private static boolean finiteNonNegative(double value, double max) {
		return Double.isFinite(value) && value >= 0.0 && value <= max;
	}

	private static boolean finiteWithinAbs(double value, double maxAbs) {
		return Double.isFinite(value) && Math.abs(value) <= maxAbs;
	}

	private static double magnitude(double x, double y, double z) {
		return Math.sqrt(x * x + y * y + z * z);
	}
}
