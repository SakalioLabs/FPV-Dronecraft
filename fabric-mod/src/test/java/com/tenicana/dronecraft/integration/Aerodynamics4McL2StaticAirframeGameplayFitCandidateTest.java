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

class Aerodynamics4McL2StaticAirframeGameplayFitCandidateTest {
	@Test
	void auditBuildsStableGameplayFitCandidateScenarios() {
		Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitAudit audit =
				Aerodynamics4McL2StaticAirframeGameplayFitCandidate.audit();

		assertEquals("A4MC-L2-Static-Airframe-Gameplay-Fit-Candidate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Single-axis forward-drag"));
		assertEquals(209, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(3, audit.scenarioSampleCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(16, audit.candidateMetricCount());
		assertEquals(11, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(3, audit.scenarios().size());

		Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitScenario unavailable =
				find(audit.scenarios(), "current_runtime_unavailable");
		assertEquals(4, unavailable.candidates().size());
		assertTrue(unavailable.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitCandidate::forwardDragCandidateReady));

		Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitScenario testRuntime =
				find(audit.scenarios(), "test_runtime_fit_ready_blocked");
		assertTrue(testRuntime.candidates().stream()
				.allMatch(candidate -> candidate.coefficientFitReady() && !candidate.coefficientGateAllowed()));
		assertTrue(testRuntime.candidates().stream()
				.anyMatch(candidate -> candidate.targetBodyDragZCoefficient() > 0.0));
		assertTrue(testRuntime.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitCandidate::forwardDragCandidateReady));

		Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitScenario live =
				find(audit.scenarios(), "live_runtime_forward_drag_candidate");
		assertTrue(live.candidates().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitCandidate::coefficientGateAllowed));
		assertTrue(live.candidates().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitCandidate::forwardDragCandidateReady));
		assertTrue(live.candidates().stream()
				.allMatch(Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitCandidate::multiAxisSweepRequired));
		assertTrue(live.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitCandidate::fullAirframeGameplayFitAllowed));

		assertEquals(3, audit.extrema().scenarioCount());
		assertEquals(12, audit.extrema().candidateCount());
		assertEquals(4, audit.extrema().forwardDragCandidateReadyCount());
		assertEquals(0, audit.extrema().fullAirframeGameplayFitAllowedCount());
		assertEquals(12, audit.extrema().multiAxisSweepRequiredCount());
	}

	@Test
	void candidateMapsDragCoefficientOntoCurrentBodyDragScale() {
		Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed =
				Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(getClass().getClassLoader()).seeds().get(0);
		Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed liveSeed =
				withRuntime(seed, "a4mc-live-calibration");
		Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceSummary gate =
				Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.gate(List.of(
						liveSeed,
						withRuntime(Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(getClass().getClassLoader()).seeds().get(1), "a4mc-live-calibration"),
						withRuntime(Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(getClass().getClassLoader()).seeds().get(2), "a4mc-live-calibration"),
						withRuntime(Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(getClass().getClassLoader()).seeds().get(3), "a4mc-live-calibration")
				));

		Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitCandidate candidate =
				Aerodynamics4McL2StaticAirframeGameplayFitCandidate.candidate("live", liveSeed, gate);

		double expectedCdA = liveSeed.referenceAreaSquareMeters() * liveSeed.dragCoefficient();
		double expectedBodyDragZ = 0.5 * liveSeed.airDensityKgM3() * expectedCdA;
		double currentBodyDragZ = DroneConfig.racingQuad().bodyDragCoefficients().z();
		assertEquals(currentBodyDragZ, candidate.currentBodyDragZCoefficient(), 1.0e-15);
		assertEquals(expectedBodyDragZ, candidate.targetBodyDragZCoefficient(), 1.0e-15);
		assertEquals(expectedCdA, candidate.targetEquivalentCdAMetersSquared(), 1.0e-15);
		assertEquals(2.0 * currentBodyDragZ / liveSeed.airDensityKgM3(),
				candidate.currentEquivalentCdAMetersSquared(), 1.0e-15);
		assertEquals(expectedBodyDragZ / currentBodyDragZ, candidate.targetToCurrentBodyDragZRatio(), 1.0e-12);
		assertTrue(candidate.forwardDragCandidateReady());
		assertFalse(candidate.fullAirframeGameplayFitAllowed());
	}

	@Test
	void scenarioRequiresGateAndRejectsUnsafeInputs() {
		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> testSeeds =
				Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(getClass().getClassLoader()).seeds();
		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> liveSeeds =
				testSeeds.stream().map(seed -> withRuntime(seed, "a4mc-live-calibration")).toList();

		Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitScenario blocked =
				Aerodynamics4McL2StaticAirframeGameplayFitCandidate.scenario("blocked", testSeeds);
		assertTrue(blocked.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitCandidate::forwardDragCandidateReady));

		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> outOfBounds =
				new ArrayList<>(liveSeeds);
		outOfBounds.set(0, withDrag(outOfBounds.get(0), 35.1));
		Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitScenario unsafe =
				Aerodynamics4McL2StaticAirframeGameplayFitCandidate.scenario("unsafe", outOfBounds);
		assertTrue(unsafe.candidates().stream()
				.noneMatch(Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitCandidate::forwardDragCandidateReady));

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeGameplayFitCandidate.scenario("", liveSeeds));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeGameplayFitCandidate.scenario("null", null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeGameplayFitCandidate.candidate("bad",
						withPresetName(liveSeeds.get(0), "unexpected"),
						Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.gate(liveSeeds)));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitAudit audit =
				Aerodynamics4McL2StaticAirframeGameplayFitCandidate.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_static_airframe_gameplay_fit_candidate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_gameplay_fit_candidate_summary,all_scenarios,forward_drag_candidate_ready_count,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_gameplay_fit_candidate_summary,all_scenarios,full_airframe_gameplay_fit_allowed_count,0,")));
	}

	private static Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitScenario find(
			List<Aerodynamics4McL2StaticAirframeGameplayFitCandidate.GameplayFitScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed withRuntime(
			Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed,
			String runtimeInfo
	) {
		return copy(seed, seed.presetName(), seed.dragCoefficient(), runtimeInfo);
	}

	private static Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed withDrag(
			Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed,
			double dragCoefficient
	) {
		return copy(seed, seed.presetName(), dragCoefficient, seed.sourceRuntimeInfo());
	}

	private static Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed withPresetName(
			Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed,
			String presetName
	) {
		return copy(seed, presetName, seed.dragCoefficient(), seed.sourceRuntimeInfo());
	}

	private static Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed copy(
			Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed,
			String presetName,
			double dragCoefficient,
			String runtimeInfo
	) {
		return new Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed(
				presetName,
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
				runtimeInfo
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_static_airframe_gameplay_fit_candidate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
