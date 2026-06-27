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

class Aerodynamics4McL2PoweredHoverAcceptanceGateTest {
	@Test
	void auditBuildsStableAcceptanceScenarios() {
		Aerodynamics4McL2PoweredHoverAcceptanceGate.PoweredHoverAcceptanceAudit audit =
				Aerodynamics4McL2PoweredHoverAcceptanceGate.audit();

		assertEquals("A4MC-L2-Powered-Hover-Acceptance-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Gate remains closed"));
		assertEquals(70, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(14, audit.scenarioMetricCount());
		assertEquals(8, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2PoweredHoverAcceptanceGate.PoweredHoverAcceptanceSummary current =
				find(audit.scenarios(), "current_api_unavailable_no_results").summary();
		assertFalse(current.poweredSourceApiAvailable());
		assertEquals(4, current.expectedTargetCount());
		assertEquals(0, current.observedResultCount());
		assertEquals(4, current.missingResultCount());
		assertFalse(current.gameplayCouplingAllowed());

		Aerodynamics4McL2PoweredHoverAcceptanceGate.PoweredHoverAcceptanceSummary allPass =
				find(audit.scenarios(), "api_available_all_presets_pass").summary();
		assertTrue(allPass.poweredSourceApiAvailable());
		assertEquals(4, allPass.passedResultCount());
		assertEquals(0, allPass.failedResultCount());
		assertEquals(0, allPass.missingResultCount());
		assertTrue(allPass.allTargetsPresent());
		assertTrue(allPass.allExpectedResultsPassed());
		assertTrue(allPass.gameplayCouplingAllowed());

		assertFalse(find(audit.scenarios(), "api_available_one_preset_missing").summary().gameplayCouplingAllowed());
		assertFalse(find(audit.scenarios(), "api_available_one_preset_failed").summary().gameplayCouplingAllowed());
		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().allowedScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(4, audit.extrema().maxExpectedTargetCount());
		assertEquals(4, audit.extrema().maxMissingResultCount());
		assertTrue(audit.extrema().maxForceErrorRatio() > 0.0);
	}

	@Test
	void gateAllowsOnlyCompleteValidatedPoweredRuns() {
		List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget> targets =
				Aerodynamics4McL2PoweredHoverValidation.audit().targets();
		List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult> passing = passingResults(targets);

		Aerodynamics4McL2PoweredHoverAcceptanceGate.PoweredHoverAcceptanceSummary open =
				Aerodynamics4McL2PoweredHoverAcceptanceGate.gate(true, targets, passing);
		Aerodynamics4McL2PoweredHoverAcceptanceGate.PoweredHoverAcceptanceSummary noApi =
				Aerodynamics4McL2PoweredHoverAcceptanceGate.gate(false, targets, passing);

		assertTrue(open.gameplayCouplingAllowed());
		assertFalse(noApi.gameplayCouplingAllowed());
		assertTrue(noApi.allTargetsPresent());
		assertTrue(noApi.allExpectedResultsPassed());
	}

	@Test
	void gateRejectsMissingFailedAndUnexpectedResults() {
		List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget> targets =
				Aerodynamics4McL2PoweredHoverValidation.audit().targets();
		List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult> passing = passingResults(targets);

		Aerodynamics4McL2PoweredHoverAcceptanceGate.PoweredHoverAcceptanceSummary missing =
				Aerodynamics4McL2PoweredHoverAcceptanceGate.gate(true, targets, passing.subList(0, 3));
		assertFalse(missing.gameplayCouplingAllowed());
		assertEquals(1, missing.missingResultCount());

		List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult> failed = new ArrayList<>(passing);
		failed.set(0, failingResult(targets.get(0)));
		Aerodynamics4McL2PoweredHoverAcceptanceGate.PoweredHoverAcceptanceSummary failedSummary =
				Aerodynamics4McL2PoweredHoverAcceptanceGate.gate(true, targets, failed);
		assertFalse(failedSummary.gameplayCouplingAllowed());
		assertEquals(1, failedSummary.failedResultCount());
		assertTrue(failedSummary.maxForceErrorRatio() > 0.0);

		List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult> unexpected = new ArrayList<>(passing);
		unexpected.remove(0);
		unexpected.add(unexpectedPresetResult());
		Aerodynamics4McL2PoweredHoverAcceptanceGate.PoweredHoverAcceptanceSummary unexpectedSummary =
				Aerodynamics4McL2PoweredHoverAcceptanceGate.gate(true, targets, unexpected);
		assertFalse(unexpectedSummary.gameplayCouplingAllowed());
		assertEquals(1, unexpectedSummary.missingResultCount());
		assertEquals(1, unexpectedSummary.unexpectedResultCount());
	}

	@Test
	void gateRejectsInvalidInputsAndDuplicatePresetNames() {
		List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget> targets =
				Aerodynamics4McL2PoweredHoverValidation.audit().targets();
		List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult> passing = passingResults(targets);
		List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult> duplicate = new ArrayList<>(passing);
		duplicate.set(1, passing.get(0));

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverAcceptanceGate.gate(true, null, passing));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverAcceptanceGate.gate(true, List.of(), passing));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverAcceptanceGate.gate(true, targets, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverAcceptanceGate.gate(true, targets, duplicate));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverAcceptanceGate.PoweredHoverAcceptanceAudit audit =
				Aerodynamics4McL2PoweredHoverAcceptanceGate.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_hover_acceptance_gate_packet.csv");

		assertEquals(audit.packetMetricRowCount() + 1, Files.readAllLines(packet).size());
		assertTrue(Files.readAllLines(packet).stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_acceptance_gate_summary,all_scenarios,allowed_scenario_count,1,")));
	}

	private static Aerodynamics4McL2PoweredHoverAcceptanceGate.PoweredHoverAcceptanceScenario find(
			List<Aerodynamics4McL2PoweredHoverAcceptanceGate.PoweredHoverAcceptanceScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult> passingResults(
			List<Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget> targets
	) {
		return targets.stream()
				.map(Aerodynamics4McL2PoweredHoverAcceptanceGateTest::passingResult)
				.toList();
	}

	private static Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult passingResult(
			Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget target
	) {
		return Aerodynamics4McL2PoweredHoverValidation.evaluate(
				target,
				sample(1.25, -0.50, 3.0, 0.20, -0.10, 0.40),
				sample(
						1.25 + target.targetForceXNewtons(),
						-0.50 + target.targetForceYNewtons(),
						3.0 + target.targetForceZNewtons(),
						0.20 + target.targetMomentXNewtonMeters(),
						-0.10 + target.targetMomentYNewtonMeters(),
						0.40 + target.targetMomentZNewtonMeters()
				)
		);
	}

	private static Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult failingResult(
			Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationTarget target
	) {
		return Aerodynamics4McL2PoweredHoverValidation.evaluate(
				target,
				sample(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
				sample(0.0, target.targetForceYNewtons() * 0.50, 0.0, 0.0, 0.0, 0.0)
		);
	}

	private static Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult unexpectedPresetResult() {
		return new Aerodynamics4McL2PoweredHoverValidation.PoweredHoverValidationResult(
				"unexpected",
				0.0,
				1.0,
				0.0,
				1.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				true,
				true,
				true,
				true
		);
	}

	private static Aerodynamics4McL2Bridge.L2ForceMomentSample sample(
			double forceX,
			double forceY,
			double forceZ,
			double momentX,
			double momentY,
			double momentZ
	) {
		return new Aerodynamics4McL2Bridge.L2ForceMomentSample(
				forceX,
				forceY,
				forceZ,
				momentX,
				momentY,
				momentZ,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_hover_acceptance_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
