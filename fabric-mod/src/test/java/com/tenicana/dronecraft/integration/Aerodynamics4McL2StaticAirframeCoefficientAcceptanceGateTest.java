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

class Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGateTest {
	@Test
	void auditBuildsStableAcceptanceScenarios() {
		Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceAudit audit =
				Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.audit();

		assertEquals("A4MC-L2-Static-Airframe-Coefficient-Acceptance-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Gate remains closed"));
		assertEquals(79, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(16, audit.scenarioMetricCount());
		assertEquals(9, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceSummary unavailable =
				find(audit.scenarios(), "current_runtime_unavailable").summary();
		assertEquals(4, unavailable.expectedSeedCount());
		assertEquals(4, unavailable.observedSeedCount());
		assertEquals(0, unavailable.fitReadyCount());
		assertEquals(0, unavailable.liveRuntimeCount());
		assertFalse(unavailable.gameplayCoefficientFitAllowed());

		Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceSummary testRuntime =
				find(audit.scenarios(), "test_runtime_fit_ready_blocked").summary();
		assertEquals(4, testRuntime.fitReadyCount());
		assertEquals(0, testRuntime.liveRuntimeCount());
		assertTrue(testRuntime.allSeedsPresent());
		assertTrue(testRuntime.allSeedsFitReady());
		assertFalse(testRuntime.allSeedsLiveRuntime());
		assertFalse(testRuntime.gameplayCoefficientFitAllowed());

		Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceSummary live =
				find(audit.scenarios(), "live_runtime_all_bounded").summary();
		assertTrue(live.allSeedsPresent());
		assertTrue(live.allSeedsFitReady());
		assertTrue(live.allSeedsLiveRuntime());
		assertTrue(live.allCoefficientsBounded());
		assertTrue(live.gameplayCoefficientFitAllowed());

		Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceSummary outOfBounds =
				find(audit.scenarios(), "live_runtime_one_out_of_bounds").summary();
		assertTrue(outOfBounds.allSeedsLiveRuntime());
		assertFalse(outOfBounds.allCoefficientsBounded());
		assertFalse(outOfBounds.gameplayCoefficientFitAllowed());
		assertEquals(1, audit.extrema().allowedScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(4, audit.extrema().maxExpectedSeedCount());
	}

	@Test
	void gateRequiresLiveRuntimeAndBoundedCoefficients() {
		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> testSeeds =
				Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(getClass().getClassLoader()).seeds();
		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> liveSeeds =
				testSeeds.stream().map(seed -> withRuntime(seed, "a4mc-live-calibration")).toList();

		Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceSummary testSummary =
				Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.gate(testSeeds);
		Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceSummary liveSummary =
				Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.gate(liveSeeds);

		assertFalse(testSummary.gameplayCoefficientFitAllowed());
		assertTrue(testSummary.allSeedsFitReady());
		assertFalse(testSummary.allSeedsLiveRuntime());
		assertTrue(liveSummary.gameplayCoefficientFitAllowed());
	}

	@Test
	void gateRejectsMissingUnexpectedDuplicateAndOutOfBoundsSeeds() {
		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> liveSeeds =
				Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(getClass().getClassLoader()).seeds()
						.stream().map(seed -> withRuntime(seed, "a4mc-live-calibration")).toList();

		Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceSummary missing =
				Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.gate(liveSeeds.subList(0, 3));
		assertEquals(1, missing.missingSeedCount());
		assertFalse(missing.gameplayCoefficientFitAllowed());

		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> unexpected = new ArrayList<>(liveSeeds);
		unexpected.remove(0);
		unexpected.add(withPresetName(liveSeeds.get(0), "unexpected"));
		Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceSummary unexpectedSummary =
				Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.gate(unexpected);
		assertEquals(1, unexpectedSummary.missingSeedCount());
		assertEquals(1, unexpectedSummary.unexpectedSeedCount());
		assertFalse(unexpectedSummary.gameplayCoefficientFitAllowed());

		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> duplicate = new ArrayList<>(liveSeeds);
		duplicate.set(1, liveSeeds.get(0));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.gate(duplicate));

		List<Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed> outOfBounds = new ArrayList<>(liveSeeds);
		outOfBounds.set(0, withDrag(outOfBounds.get(0), 35.1));
		assertFalse(Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.gate(outOfBounds)
				.gameplayCoefficientFitAllowed());
	}

	@Test
	void gateRejectsNullInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.gate(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceAudit audit =
				Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_static_airframe_coefficient_acceptance_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_coefficient_acceptance_gate_summary,all_scenarios,allowed_scenario_count,1,")));
	}

	private static Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceScenario find(
			List<Aerodynamics4McL2StaticAirframeCoefficientAcceptanceGate.CoefficientAcceptanceScenario> scenarios,
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

	private static Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed withPresetName(
			Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed,
			String presetName
	) {
		return copy(seed, presetName, seed.dragCoefficient(), seed.sourceRuntimeInfo());
	}

	private static Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed withDrag(
			Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed,
			double dragCoefficient
	) {
		return copy(seed, seed.presetName(), dragCoefficient, seed.sourceRuntimeInfo());
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
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_static_airframe_coefficient_acceptance_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
