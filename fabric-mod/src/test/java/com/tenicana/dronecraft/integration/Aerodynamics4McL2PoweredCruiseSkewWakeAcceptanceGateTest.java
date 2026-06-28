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

class Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGateTest {
	@Test
	void auditBuildsStableCruiseSkewWakeAcceptanceScenarios() {
		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Skew-Wake-Acceptance-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("lab validation"));
		assertEquals(185, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(33, audit.scenarioMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(5, audit.scenarios().size());

		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary current =
				find(audit.scenarios(), "current_probe_apis_unavailable").summary();
		assertFalse(current.skewWakeProbeApiAvailable());
		assertFalse(current.transientProbeApiAvailable());
		assertEquals(192, current.expectedSeedCount());
		assertEquals(192, current.observedSeedCount());
		assertEquals(0, current.readySeedCount());
		assertEquals(192, current.unavailableSeedCount());
		assertEquals(0, current.missingSeedCount());
		assertEquals(0, current.unexpectedSeedCount());
		assertEquals(64, current.centerlineSweepSeedCount());
		assertEquals(128, current.lateralSweepSeedCount());
		assertEquals(48, current.expectedGroupCount());
		assertEquals(48, current.observedGroupCount());
		assertEquals(0, current.candidateGroupCount());
		assertEquals(48, current.blockedGroupCount());
		assertTrue(current.allExpectedSeedsPresent());
		assertFalse(current.allSeedsReady());
		assertFalse(current.allErrorBudgetGroupsCandidate());
		assertFalse(current.labValidationAccepted());
		assertEquals("BLOCKED", current.status());
		assertEquals("skew-wake-probe-api-unavailable", current.message());

		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary allPass =
				find(audit.scenarios(), "skew_and_transient_probe_all_targets_pass").summary();
		assertTrue(allPass.skewWakeProbeApiAvailable());
		assertTrue(allPass.transientProbeApiAvailable());
		assertEquals(192, allPass.readySeedCount());
		assertEquals(192, allPass.passedSeedCount());
		assertEquals(0, allPass.failedSeedCount());
		assertEquals(64, allPass.readyCenterlineSweepSeedCount());
		assertEquals(128, allPass.readyLateralSweepSeedCount());
		assertEquals(64, allPass.passedCenterlineSweepSeedCount());
		assertEquals(128, allPass.passedLateralSweepSeedCount());
		assertEquals(48, allPass.readyGroupCount());
		assertEquals(48, allPass.passedGroupCount());
		assertEquals(48, allPass.candidateGroupCount());
		assertEquals(0, allPass.blockedGroupCount());
		assertTrue(allPass.allExpectedSeedsPresent());
		assertTrue(allPass.allSeedsReady());
		assertTrue(allPass.allExpectedSeedsPassed());
		assertTrue(allPass.allErrorBudgetGroupsCandidate());
		assertTrue(allPass.labValidationAccepted());
		assertEquals("ACCEPTED", allPass.status());
		assertEquals("cruise-skew-wake-validation-accepted", allPass.message());

		assertFalse(find(audit.scenarios(), "skew_probe_unavailable_all_targets_pass")
				.summary().labValidationAccepted());
		assertFalse(find(audit.scenarios(), "skew_and_transient_probe_one_seed_missing")
				.summary().labValidationAccepted());
		assertFalse(find(audit.scenarios(), "skew_and_transient_probe_one_seed_failed")
				.summary().labValidationAccepted());
		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().acceptedScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(192, audit.extrema().maxExpectedSeedCount());
		assertEquals(1, audit.extrema().maxMissingSeedCount());
		assertEquals(0, audit.extrema().maxUnexpectedSeedCount());
		assertEquals(192, audit.extrema().maxUnavailableSeedCount());
		assertEquals(48, audit.extrema().maxBlockedGroupCount());
		assertTrue(audit.extrema().maxMomentumErrorRatio() > 0.0);
	}

	@Test
	void gateAcceptsOnlyCompleteLivePassingSeedsWithProbeApis() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> seeds =
				passingSeeds(Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.audit().seeds());

		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary open =
				Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.gate(true, true, seeds);
		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary noSkewProbe =
				Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.gate(false, true, seeds);
		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary noTransientProbe =
				Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.gate(true, false, seeds);

		assertTrue(open.allExpectedSeedsPresent());
		assertTrue(open.allSeedsReady());
		assertTrue(open.allExpectedSeedsPassed());
		assertTrue(open.allErrorBudgetGroupsCandidate());
		assertTrue(open.labValidationAccepted());
		assertFalse(noSkewProbe.labValidationAccepted());
		assertEquals("skew-wake-probe-api-unavailable", noSkewProbe.message());
		assertFalse(noTransientProbe.labValidationAccepted());
		assertEquals("transient-probe-api-unavailable", noTransientProbe.message());
	}

	@Test
	void gateRejectsUnavailableMissingFailedAndUnexpectedSeeds() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> current =
				Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.audit().seeds();
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> passing =
				passingSeeds(current);

		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary unavailable =
				Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.gate(true, true, current);
		assertFalse(unavailable.labValidationAccepted());
		assertEquals(192, unavailable.unavailableSeedCount());
		assertEquals("cruise-skew-wake-seeds-not-ready", unavailable.message());

		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary missing =
				Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.gate(true, true,
						passing.subList(0, passing.size() - 1));
		assertFalse(missing.labValidationAccepted());
		assertEquals(1, missing.missingSeedCount());
		assertEquals(1, missing.blockedGroupCount());
		assertEquals("cruise-skew-wake-seed-set-incomplete", missing.message());

		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> failed =
				new ArrayList<>(passing);
		failed.set(0, failingSeed(passing.get(0)));
		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary failedSummary =
				Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.gate(true, true, failed);
		assertFalse(failedSummary.labValidationAccepted());
		assertEquals(1, failedSummary.failedSeedCount());
		assertEquals(1, failedSummary.blockedGroupCount());
		assertEquals("cruise-skew-wake-seeds-failed", failedSummary.message());
		assertTrue(failedSummary.maxMomentumErrorRatio()
				> Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.MOMENTUM_RELATIVE_TOLERANCE);

		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> unexpected =
				new ArrayList<>(passing);
		unexpected.set(0, renamedPreset(passing.get(0), "unexpected"));
		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceSummary unexpectedSummary =
				Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.gate(true, true, unexpected);
		assertFalse(unexpectedSummary.labValidationAccepted());
		assertEquals(1, unexpectedSummary.missingSeedCount());
		assertEquals(1, unexpectedSummary.unexpectedSeedCount());
	}

	@Test
	void gateRejectsInvalidInputsAndDuplicateSeedNames() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> passing =
				passingSeeds(Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.audit().seeds());
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> duplicate =
				new ArrayList<>(passing);
		duplicate.set(1, passing.get(0));
		List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> withNull =
				new ArrayList<>(passing);
		withNull.set(1, null);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.gate(true, true, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.gate(true, true, withNull));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.gate(true, true, duplicate));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_cruise_skew_wake_acceptance_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_acceptance_gate_summary,all_scenarios,accepted_scenario_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_acceptance_gate_scenario,current_probe_apis_unavailable,lab_validation_accepted,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_acceptance_gate_scenario,skew_and_transient_probe_all_targets_pass,lab_validation_accepted,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_acceptance_gate_scenario,skew_and_transient_probe_one_seed_failed,max_momentum_error_ratio,0.5,")));
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceScenario find(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGate.PoweredCruiseSkewWakeAcceptanceScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> passingSeeds(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed> seeds
	) {
		return seeds.stream()
				.map(Aerodynamics4McL2PoweredCruiseSkewWakeAcceptanceGateTest::passingSeed)
				.toList();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed passingSeed(
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed
	) {
		return copy(seed,
				seed.presetName(),
				true,
				seed.targetResultantDynamicPressurePascals(),
				seed.targetResultantWakeVelocityMetersPerSecond(),
				seed.targetCenterlineArrivalSeconds(),
				seed.targetAxialMomentumFluxNewtons(),
				0.0,
				0.0,
				0.0,
				0.0,
				true,
				true,
				true,
				true,
				true,
				"READY_PASS",
				"cruise-skew-wake-validation-seed-passed");
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed failingSeed(
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed
	) {
		return copy(seed,
				seed.presetName(),
				true,
				seed.targetResultantDynamicPressurePascals(),
				seed.targetResultantWakeVelocityMetersPerSecond(),
				seed.targetCenterlineArrivalSeconds(),
				seed.targetAxialMomentumFluxNewtons() * 0.5,
				0.0,
				0.0,
				0.0,
				0.5,
				true,
				true,
				true,
				false,
				false,
				"READY_FAIL",
				"cruise-skew-wake-validation-seed-failed");
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed renamedPreset(
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed,
			String presetName
	) {
		return copy(seed,
				presetName,
				seed.validationSeedReady(),
				seed.observedPressurePascals(),
				seed.observedWakeVelocityMetersPerSecond(),
				seed.observedArrivalTimeSeconds(),
				seed.observedMomentumFluxNewtons(),
				seed.pressureErrorRatio(),
				seed.wakeVelocityErrorRatio(),
				seed.arrivalWindowErrorRatio(),
				seed.momentumErrorRatio(),
				seed.pressureValidationPassed(),
				seed.velocityValidationPassed(),
				seed.arrivalTimeValidationPassed(),
				seed.momentumValidationPassed(),
				seed.validationPassed(),
				seed.status(),
				seed.message());
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed copy(
			Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed seed,
			String presetName,
			boolean ready,
			double observedPressure,
			double observedVelocity,
			double observedArrival,
			double observedMomentum,
			double pressureErrorRatio,
			double wakeVelocityErrorRatio,
			double arrivalErrorRatio,
			double momentumErrorRatio,
			boolean pressurePassed,
			boolean velocityPassed,
			boolean arrivalPassed,
			boolean momentumPassed,
			boolean validationPassed,
			String status,
			String message
	) {
		return new Aerodynamics4McL2PoweredCruiseSkewWakeResultSeed.PoweredCruiseSkewWakeValidationSeed(
				presetName,
				seed.spinState(),
				seed.rotorIndex(),
				seed.axialPlaneIndex(),
				seed.sweepColumnIndex(),
				seed.axialPlaneFraction(),
				ready,
				ready,
				ready,
				ready,
				ready,
				ready,
				seed.targetAxialWakePressurePascals(),
				seed.targetResultantDynamicPressurePascals(),
				observedPressure,
				Math.abs(observedPressure - seed.targetResultantDynamicPressurePascals()),
				pressureErrorRatio,
				seed.targetResultantWakeVelocityMetersPerSecond(),
				observedVelocity,
				Math.abs(observedVelocity - seed.targetResultantWakeVelocityMetersPerSecond()),
				wakeVelocityErrorRatio,
				seed.targetCenterlineArrivalSeconds(),
				seed.targetLateralArrivalSeconds(),
				seed.targetArrivalBandSeconds(),
				seed.targetArrivalToleranceSeconds(),
				observedArrival,
				0.0,
				arrivalErrorRatio,
				seed.targetAxialMomentumFluxNewtons(),
				observedMomentum,
				Math.abs(observedMomentum - seed.targetAxialMomentumFluxNewtons()),
				momentumErrorRatio,
				seed.pressureToleranceRatio(),
				seed.velocityToleranceRatio(),
				seed.arrivalTimeToleranceFraction(),
				seed.momentumToleranceRatio(),
				pressurePassed,
				velocityPassed,
				arrivalPassed,
				momentumPassed,
				validationPassed,
				status,
				message,
				ready ? "OK" : seed.runStatus(),
				ready ? "live-cruise-skew-wake-sample" : seed.runMessage(),
				ready ? "live-runtime" : seed.runRuntimeInfo(),
				ready ? "synthetic-live-cruise-skew-wake-acceptance-scenario" : seed.resultRuntimeInfo()
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_cruise_skew_wake_acceptance_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
