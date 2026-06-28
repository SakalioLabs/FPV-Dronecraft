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

class Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGateTest {
	@Test
	void auditBuildsStableSurfaceWakeAcceptanceScenarios() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.audit();

		assertEquals("A4MC-L2-Powered-Hover-Surface-Wake-Acceptance-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("lab validation"));
		assertEquals(114, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(24, audit.scenarioMetricCount());
		assertEquals(10, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary current =
				find(audit.scenarios(), "current_transient_probe_unavailable").summary();
		assertFalse(current.transientProbeApiAvailable());
		assertEquals(128, current.expectedSeedCount());
		assertEquals(128, current.observedSeedCount());
		assertEquals(0, current.readySeedCount());
		assertEquals(128, current.unavailableSeedCount());
		assertEquals(0, current.missingSeedCount());
		assertEquals(0, current.unexpectedSeedCount());
		assertTrue(current.allExpectedSeedsPresent());
		assertFalse(current.allSeedsReady());
		assertFalse(current.labValidationAccepted());
		assertEquals("BLOCKED", current.status());
		assertEquals("transient-probe-api-unavailable", current.message());

		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary allPass =
				find(audit.scenarios(), "transient_probe_all_targets_pass").summary();
		assertTrue(allPass.transientProbeApiAvailable());
		assertEquals(128, allPass.readySeedCount());
		assertEquals(128, allPass.passedSeedCount());
		assertEquals(0, allPass.failedSeedCount());
		assertEquals(64, allPass.groundSeedCount());
		assertEquals(64, allPass.ceilingSeedCount());
		assertEquals(64, allPass.readyGroundSeedCount());
		assertEquals(64, allPass.readyCeilingSeedCount());
		assertEquals(64, allPass.passedGroundSeedCount());
		assertEquals(64, allPass.passedCeilingSeedCount());
		assertTrue(allPass.allExpectedSeedsPresent());
		assertTrue(allPass.allSeedsReady());
		assertTrue(allPass.allExpectedSeedsPassed());
		assertTrue(allPass.labValidationAccepted());
		assertEquals("ACCEPTED", allPass.status());
		assertEquals("surface-wake-validation-accepted", allPass.message());

		assertFalse(find(audit.scenarios(), "transient_probe_one_seed_missing").summary().labValidationAccepted());
		assertFalse(find(audit.scenarios(), "transient_probe_one_seed_failed").summary().labValidationAccepted());
		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().acceptedScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(128, audit.extrema().maxExpectedSeedCount());
		assertEquals(1, audit.extrema().maxMissingSeedCount());
		assertEquals(0, audit.extrema().maxUnexpectedSeedCount());
		assertEquals(128, audit.extrema().maxUnavailableSeedCount());
		assertTrue(audit.extrema().maxPressureErrorRatio() > 0.0);
	}

	@Test
	void gateAcceptsOnlyCompleteLivePassingSeeds() {
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> seeds =
				passingSeeds(Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.audit().seeds());

		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary open =
				Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.gate(true, seeds);
		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary noProbe =
				Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.gate(false, seeds);

		assertTrue(open.allExpectedSeedsPresent());
		assertTrue(open.allSeedsReady());
		assertTrue(open.allExpectedSeedsPassed());
		assertTrue(open.labValidationAccepted());
		assertFalse(noProbe.labValidationAccepted());
		assertTrue(noProbe.allExpectedSeedsPresent());
		assertTrue(noProbe.allSeedsReady());
		assertTrue(noProbe.allExpectedSeedsPassed());
	}

	@Test
	void gateRejectsUnavailableMissingFailedAndUnexpectedSeeds() {
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> current =
				Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.audit().seeds();
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> passing =
				passingSeeds(current);

		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary unavailable =
				Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.gate(true, current);
		assertFalse(unavailable.labValidationAccepted());
		assertEquals(128, unavailable.unavailableSeedCount());
		assertEquals("surface-wake-seeds-not-ready", unavailable.message());

		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary missing =
				Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.gate(true, passing.subList(0, 127));
		assertFalse(missing.labValidationAccepted());
		assertEquals(1, missing.missingSeedCount());
		assertEquals("surface-wake-seed-set-incomplete", missing.message());

		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> failed =
				new ArrayList<>(passing);
		failed.set(0, failingSeed(passing.get(0)));
		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary failedSummary =
				Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.gate(true, failed);
		assertFalse(failedSummary.labValidationAccepted());
		assertEquals(1, failedSummary.failedSeedCount());
		assertEquals("surface-wake-seeds-failed", failedSummary.message());
		assertTrue(failedSummary.maxPressureErrorRatio()
				> Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PRESSURE_RELATIVE_TOLERANCE);

		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> unexpected =
				new ArrayList<>(passing);
		unexpected.set(0, renamedPreset(passing.get(0), "unexpected"));
		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceSummary unexpectedSummary =
				Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.gate(true, unexpected);
		assertFalse(unexpectedSummary.labValidationAccepted());
		assertEquals(1, unexpectedSummary.missingSeedCount());
		assertEquals(1, unexpectedSummary.unexpectedSeedCount());
	}

	@Test
	void gateRejectsInvalidInputsAndDuplicateSeedNames() {
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> passing =
				passingSeeds(Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.audit().seeds());
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> duplicate =
				new ArrayList<>(passing);
		duplicate.set(1, passing.get(0));
		List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> withNull =
				new ArrayList<>(passing);
		withNull.set(1, null);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.gate(true, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.gate(true, withNull));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.gate(true, duplicate));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_hover_surface_wake_acceptance_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_acceptance_gate_summary,all_scenarios,accepted_scenario_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_acceptance_gate_scenario,current_transient_probe_unavailable,lab_validation_accepted,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_acceptance_gate_scenario,transient_probe_all_targets_pass,lab_validation_accepted,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_acceptance_gate_scenario,transient_probe_one_seed_failed,max_pressure_error_ratio,0.5,")));
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceScenario find(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGate.PoweredHoverSurfaceWakeAcceptanceScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> passingSeeds(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed> seeds
	) {
		return seeds.stream()
				.map(Aerodynamics4McL2PoweredHoverSurfaceWakeAcceptanceGateTest::passingSeed)
				.toList();
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed passingSeed(
			Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed
	) {
		return copy(seed,
				seed.presetName(),
				true,
				seed.targetPressurePascals(),
				seed.targetWakeVelocityMetersPerSecond(),
				seed.targetFastArrivalSeconds() + seed.targetArrivalBandSeconds() * 0.5,
				0.0,
				0.0,
				0.0,
				true,
				true,
				true,
				true,
				"READY_PASS",
				"surface-wake-validation-seed-passed");
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed failingSeed(
			Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed
	) {
		return copy(seed,
				seed.presetName(),
				true,
				seed.targetPressurePascals() * 0.5,
				seed.targetWakeVelocityMetersPerSecond(),
				seed.targetFastArrivalSeconds() + seed.targetArrivalBandSeconds() * 0.5,
				seed.targetPressurePascals() * 0.5,
				0.5,
				0.0,
				false,
				true,
				true,
				false,
				"READY_FAIL",
				"surface-wake-validation-seed-failed");
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed renamedPreset(
			Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed,
			String presetName
	) {
		return copy(seed,
				presetName,
				seed.validationSeedReady(),
				seed.observedPressurePascals(),
				seed.observedWakeVelocityMetersPerSecond(),
				seed.observedArrivalTimeSeconds(),
				seed.pressureErrorPascals(),
				seed.pressureErrorRatio(),
				seed.wakeVelocityErrorRatio(),
				seed.pressureValidationPassed(),
				seed.velocityValidationPassed(),
				seed.arrivalTimeValidationPassed(),
				seed.validationPassed(),
				seed.status(),
				seed.message());
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed copy(
			Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed seed,
			String presetName,
			boolean ready,
			double observedPressure,
			double observedVelocity,
			double observedArrival,
			double pressureError,
			double pressureErrorRatio,
			double wakeVelocityErrorRatio,
			boolean pressurePassed,
			boolean velocityPassed,
			boolean arrivalPassed,
			boolean validationPassed,
			String status,
			String message
	) {
		return new Aerodynamics4McL2PoweredHoverSurfaceWakeResultSeed.PoweredHoverSurfaceWakeValidationSeed(
				presetName,
				seed.spinState(),
				seed.surfaceType(),
				seed.rotorIndex(),
				seed.clearanceOverRadius(),
				seed.clearanceMeters(),
				ready,
				ready,
				ready,
				ready,
				ready,
				seed.targetPressurePascals(),
				observedPressure,
				pressureError,
				pressureErrorRatio,
				seed.targetWakeVelocityMetersPerSecond(),
				observedVelocity,
				Math.abs(observedVelocity - seed.targetWakeVelocityMetersPerSecond()),
				wakeVelocityErrorRatio,
				seed.targetFastArrivalSeconds(),
				seed.targetSlowArrivalSeconds(),
				seed.targetArrivalBandSeconds(),
				observedArrival,
				0.0,
				0.0,
				seed.pressureToleranceRatio(),
				seed.velocityToleranceRatio(),
				seed.arrivalBandToleranceFraction(),
				pressurePassed,
				velocityPassed,
				arrivalPassed,
				validationPassed,
				status,
				message,
				ready ? "OK" : seed.runStatus(),
				ready ? "live-surface-wake-sample" : seed.runMessage(),
				ready ? "live-runtime" : seed.runRuntimeInfo(),
				ready ? "synthetic-live-surface-wake-acceptance-scenario" : seed.resultRuntimeInfo()
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_hover_surface_wake_acceptance_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
