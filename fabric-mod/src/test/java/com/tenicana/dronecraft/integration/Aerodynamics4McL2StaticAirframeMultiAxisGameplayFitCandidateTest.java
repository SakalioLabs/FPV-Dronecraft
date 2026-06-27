package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;

class Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidateTest {
	@Test
	void auditBuildsStableMultiAxisGameplayFitCandidateScenarios() {
		Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.audit();

		assertEquals("A4MC-L2-Static-Airframe-Multi-Axis-Gameplay-Fit-Candidate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("audit-only"));
		assertEquals(501, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(30, audit.candidateMetricCount());
		assertEquals(15, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitScenario unavailable =
				findScenario(audit.scenarios(), "current_runtime_unavailable");
		assertEquals(4, unavailable.candidates().size());
		assertTrue(unavailable.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate::multiAxisCandidateReady));

		Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitScenario testRuntime =
				findScenario(audit.scenarios(), "test_runtime_fit_ready_blocked");
		assertTrue(testRuntime.candidates().stream()
				.allMatch(candidate -> candidate.fitReadySweepCount() == 6 && !candidate.coefficientGateAllowed()));
		assertTrue(testRuntime.candidates().stream()
				.anyMatch(candidate -> candidate.targetBodyDragZCoefficient() > 0.0));
		assertTrue(testRuntime.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate::multiAxisCandidateReady));

		Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitScenario live =
				findScenario(audit.scenarios(), "live_runtime_full_multi_axis_candidate");
		assertTrue(live.candidates().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate::coefficientGateAllowed));
		assertTrue(live.candidates().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate::multiAxisCandidateReady));
		assertTrue(live.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate::runtimeConfigAutoApplyAllowed));
		assertTrue(live.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate::pressureCenterVectorResolved));

		Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitScenario signBroken =
				findScenario(audit.scenarios(), "live_runtime_forward_reverse_sign_broken");
		assertTrue(signBroken.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate::coefficientGateAllowed));
		assertTrue(signBroken.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate::multiAxisCandidateReady));

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(16, audit.extrema().candidateCount());
		assertEquals(4, audit.extrema().multiAxisCandidateReadyCount());
		assertEquals(0, audit.extrema().runtimeConfigAutoApplyAllowedCount());
		assertEquals(0, audit.extrema().pressureCenterVectorResolvedCount());
	}

	@Test
	void candidateMapsMultiAxisCoefficientsOntoCurrentGameplayScales() {
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> liveSeeds =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.audit(getClass().getClassLoader()).seeds()
						.stream().map(seed -> withRuntime(seed, "a4mc-live-calibration")).toList();
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> racingQuad =
				liveSeeds.stream().filter(seed -> "racingQuad".equals(seed.presetName())).toList();
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.MultiAxisCoefficientAcceptanceSummary gate =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.gate(liveSeeds);

		Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate candidate =
				Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.candidate(
						"live",
						"racingQuad",
						racingQuad,
						gate
				);

		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed forward =
				findSeed(racingQuad, "forward_drag");
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed reverse =
				findSeed(racingQuad, "reverse_drag_symmetry");
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed right =
				findSeed(racingQuad, "right_sideslip_12deg");
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed left =
				findSeed(racingQuad, "left_sideslip_12deg");
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed positiveAoa =
				findSeed(racingQuad, "positive_aoa_12deg");
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed negativeAoa =
				findSeed(racingQuad, "negative_aoa_12deg");

		double expectedDragZ = 0.5 * 1.225 * (
				forward.projectedReferenceAreaSquareMeters() * Math.abs(forward.signedAxialForceCoefficient())
						+ reverse.projectedReferenceAreaSquareMeters() * Math.abs(reverse.signedAxialForceCoefficient())
		) * 0.5;
		double expectedDragX = 0.5 * 1.225 * (
				right.projectedReferenceAreaSquareMeters() * Math.abs(right.signedAxialForceCoefficient())
						+ left.projectedReferenceAreaSquareMeters() * Math.abs(left.signedAxialForceCoefficient())
		) * 0.5;
		double expectedDragY = 0.5 * 1.225 * (
				positiveAoa.projectedReferenceAreaSquareMeters() * Math.abs(positiveAoa.signedAxialForceCoefficient())
						+ negativeAoa.projectedReferenceAreaSquareMeters() * Math.abs(negativeAoa.signedAxialForceCoefficient())
		) * 0.5;
		double sweepResponse = Math.sin(2.0 * Math.toRadians(12.0));
		double expectedSideforceGain = 0.5 * 1.225 * (
				right.projectedReferenceAreaSquareMeters() * Math.abs(right.sideForceCoefficient())
						+ left.projectedReferenceAreaSquareMeters() * Math.abs(left.sideForceCoefficient())
		) * 0.5 / sweepResponse;
		double expectedPitchLiftGain = 0.5 * 1.225 * (
				positiveAoa.projectedReferenceAreaSquareMeters() * Math.abs(positiveAoa.liftCoefficient())
						+ negativeAoa.projectedReferenceAreaSquareMeters() * Math.abs(negativeAoa.liftCoefficient())
		) * 0.5 / sweepResponse;
		DroneConfig config = DroneConfig.racingQuad();

		assertEquals(config.bodyDragCoefficients().x(), candidate.currentBodyDragXCoefficient(), 1.0e-15);
		assertEquals(config.bodyDragCoefficients().y(), candidate.currentBodyDragYCoefficient(), 1.0e-15);
		assertEquals(config.bodyDragCoefficients().z(), candidate.currentBodyDragZCoefficient(), 1.0e-15);
		assertEquals(expectedDragX, candidate.targetBodyDragXCoefficient(), 1.0e-15);
		assertEquals(expectedDragY, candidate.targetBodyDragYCoefficient(), 1.0e-15);
		assertEquals(expectedDragZ, candidate.targetBodyDragZCoefficient(), 1.0e-15);
		assertEquals(expectedSideforceGain, candidate.targetSideforceGain(), 1.0e-15);
		assertEquals(expectedPitchLiftGain, candidate.targetPitchLiftGain(), 1.0e-15);
		assertEquals(0.065 * Math.sqrt(config.bodyDragCoefficients().x() * config.bodyDragCoefficients().z()),
				candidate.currentSideforceGain(), 1.0e-15);
		assertEquals(0.085 * Math.sqrt(config.bodyDragCoefficients().y() * config.bodyDragCoefficients().z()),
				candidate.currentPitchLiftGain(), 1.0e-15);
		assertTrue(candidate.multiAxisCandidateReady());
		assertFalse(candidate.runtimeConfigAutoApplyAllowed());
		assertFalse(candidate.pressureCenterVectorResolved());
	}

	@Test
	void scenarioRequiresGateAndRejectsUnsafeInputs() {
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> testSeeds =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.audit(getClass().getClassLoader()).seeds();
		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> liveSeeds =
				testSeeds.stream().map(seed -> withRuntime(seed, "a4mc-live-calibration")).toList();

		Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitScenario blocked =
				Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.scenario("blocked", testSeeds);
		assertTrue(blocked.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate::multiAxisCandidateReady));

		List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> signBroken =
				new ArrayList<>(liveSeeds);
		int reverseIndex = findIndex(signBroken, "racingQuad", "reverse_drag_symmetry");
		signBroken.set(reverseIndex, withSignedAxial(signBroken.get(reverseIndex), Math.abs(signBroken.get(reverseIndex)
				.signedAxialForceCoefficient())));
		Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitScenario unsafe =
				Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.scenario("unsafe", signBroken);
		assertTrue(unsafe.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate::multiAxisCandidateReady));

		Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitScenario missing =
				Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.scenario("missing", liveSeeds.subList(0, 23));
		assertTrue(missing.candidates().stream().anyMatch(candidate -> candidate.observedSweepCount() < 6));
		assertTrue(missing.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitCandidate::multiAxisCandidateReady));

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.scenario("", liveSeeds));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.scenario("null", null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.candidate("bad", "unknown",
						liveSeeds, Aerodynamics4McL2StaticAirframeMultiAxisCoefficientAcceptanceGate.gate(liveSeeds)));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_static_airframe_multi_axis_gameplay_fit_candidate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_multi_axis_gameplay_fit_candidate_summary,all_scenarios,multi_axis_candidate_ready_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_multi_axis_gameplay_fit_candidate_summary,all_scenarios,runtime_config_auto_apply_allowed_count,0,")));
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitScenario findScenario(
			List<Aerodynamics4McL2StaticAirframeMultiAxisGameplayFitCandidate.MultiAxisGameplayFitScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed findSeed(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds,
			String sweepName
	) {
		return seeds.stream()
				.filter(seed -> sweepName.equals(seed.sweepName()))
				.findFirst()
				.orElseThrow();
	}

	private static int findIndex(
			List<Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed> seeds,
			String presetName,
			String sweepName
	) {
		for (int index = 0; index < seeds.size(); index++) {
			Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed =
					seeds.get(index);
			if (presetName.equals(seed.presetName()) && sweepName.equals(seed.sweepName())) {
				return index;
			}
		}
		throw new IllegalArgumentException("Seed not found: " + presetName + ":" + sweepName);
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

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_static_airframe_multi_axis_gameplay_fit_candidate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
