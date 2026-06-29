package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class PropellerArchiveRotorSpecRetuneValidationAcceptanceGateTest {
	@Test
	void auditBuildsBlockedAndSyntheticAcceptanceScenarios() {
		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceAudit audit =
				PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Validation-Acceptance-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("manual DroneConfig patch review"));
		assertEquals(81, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(16, audit.scenarioMetricRowCount());
		assertEquals(10, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary current =
				find(audit.scenarios(), "current_retune_validation_blocked").summary();
		assertEquals(0, current.plannedValidationRunCount());
		assertEquals(0, current.observedResultCount());
		assertEquals(0, current.missingResultCount());
		assertEquals(0, current.validationAcceptedRunCount());
		assertFalse(current.validationAcceptanceReady());
		assertFalse(current.manualConfigPatchReviewAllowed());
		assertFalse(current.configPatchAllowed());
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals("BLOCKED", current.status());
		assertEquals("validation-run-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary missing =
				find(audit.scenarios(), "synthetic_ready_validation_results_missing").summary();
		assertEquals(8, missing.plannedValidationRunCount());
		assertEquals(0, missing.observedResultCount());
		assertEquals(8, missing.missingResultCount());
		assertFalse(missing.validationAcceptanceReady());
		assertEquals("validation-result-set-incomplete", missing.message());

		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary allPass =
				find(audit.scenarios(), "synthetic_ready_validation_all_pass").summary();
		assertEquals(8, allPass.plannedValidationRunCount());
		assertEquals(8, allPass.observedResultCount());
		assertEquals(8, allPass.passedResultCount());
		assertEquals(0, allPass.failedResultCount());
		assertEquals(8, allPass.validationAcceptedRunCount());
		assertEquals(2, allPass.manualConfigPatchReviewCandidateCount());
		assertTrue(allPass.allPlannedRunsHaveResults());
		assertTrue(allPass.allObservedResultsPassed());
		assertTrue(allPass.validationAcceptanceReady());
		assertTrue(allPass.manualConfigPatchReviewAllowed());
		assertFalse(allPass.configPatchAllowed());
		assertFalse(allPass.runtimeCouplingAllowed());
		assertFalse(allPass.gameplayAutoApplyAllowed());
		assertEquals("READY", allPass.status());
		assertEquals("retune-validation-accepted-for-manual-config-review", allPass.message());

		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary failed =
				find(audit.scenarios(), "synthetic_ready_validation_one_failed").summary();
		assertEquals(8, failed.plannedValidationRunCount());
		assertEquals(8, failed.observedResultCount());
		assertEquals(1, failed.failedResultCount());
		assertEquals(7, failed.passedResultCount());
		assertFalse(failed.validationAcceptanceReady());
		assertEquals("validation-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().acceptedScenarioCount());
		assertEquals(1, audit.extrema().manualConfigPatchReviewAllowedScenarioCount());
		assertEquals(8, audit.extrema().maxPlannedValidationRunCount());
		assertEquals(8, audit.extrema().maxObservedResultCount());
		assertEquals(8, audit.extrema().maxMissingResultCount());
		assertEquals(1, audit.extrema().maxFailedResultCount());
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void resultAppliesCaseThresholdsSamplesAndViolations() {
		PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow target =
				plannedRow("racingQuad", "static_hover_thrust_closure");

		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationResult passing =
				PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.result(
						target,
						target.maxPrimaryErrorRatio() * 0.50,
						target.maxSecondaryErrorRatio() * 0.50,
						target.minSampleCount() + 2,
						0);
		assertTrue(passing.passed());
		assertEquals("PASS", passing.status());

		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationResult highPrimary =
				PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.result(
						target,
						target.maxPrimaryErrorRatio() * 1.10,
						target.maxSecondaryErrorRatio() * 0.50,
						target.minSampleCount() + 2,
						0);
		assertFalse(highPrimary.passed());

		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationResult highSecondary =
				PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.result(
						target,
						target.maxPrimaryErrorRatio() * 0.50,
						target.maxSecondaryErrorRatio() * 1.10,
						target.minSampleCount() + 2,
						0);
		assertFalse(highSecondary.passed());

		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationResult tooFewSamples =
				PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.result(
						target,
						target.maxPrimaryErrorRatio() * 0.50,
						target.maxSecondaryErrorRatio() * 0.50,
						target.minSampleCount() - 1,
						0);
		assertFalse(tooFewSamples.passed());

		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationResult violation =
				PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.result(
						target,
						target.maxPrimaryErrorRatio() * 0.50,
						target.maxSecondaryErrorRatio() * 0.50,
						target.minSampleCount() + 2,
						1);
		assertFalse(violation.passed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.result(
						target, -1.0, 0.0, target.minSampleCount(), 0));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.result(
						target, 0.0, 0.0, -1, 0));
		PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow currentBlocked =
				PropellerArchiveRotorSpecRetuneValidationRunMatrix.row(
						"racingQuad", "static_hover_thrust_closure");
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.result(
						currentBlocked, 0.0, 0.0, currentBlocked.minSampleCount(), 0));
	}

	@Test
	void gateRejectsMalformedAndUnexpectedResults() {
		PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit ready = readyValidationRunAudit();
		PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow target =
				plannedRow("racingQuad", "static_hover_thrust_closure");
		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationResult passing =
				PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.result(
						target,
						target.maxPrimaryErrorRatio() * 0.50,
						target.maxSecondaryErrorRatio() * 0.50,
						target.minSampleCount() + 2,
						0);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.gate(
						"", ready, List.of(), "blank-scenario"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.gate(
						"missing-audit", null, List.of(), "missing-audit"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.gate(
						"missing-results", ready, null, "missing-results"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.gate(
						"missing-runtime-info", ready, List.of(), ""));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.gate(
						"duplicate-result", ready, List.of(passing, passing), "duplicate-result"));

		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationResult unexpected =
				new PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationResult(
						"cinewhoop", "static_hover_thrust_closure", 0.0, 0.0, 99, 0, true, "PASS");
		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceSummary summary =
				PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.gate(
						"unexpected-result", ready, List.of(unexpected), "unexpected-result");
		assertEquals(8, summary.plannedValidationRunCount());
		assertEquals(1, summary.observedResultCount());
		assertEquals(8, summary.missingResultCount());
		assertEquals(1, summary.unexpectedResultCount());
		assertFalse(summary.validationAcceptanceReady());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.scenario("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceAudit audit =
				PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_validation_acceptance_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_validation_acceptance_summary,all_scenarios,accepted_scenario_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_validation_acceptance_scenario,synthetic_ready_validation_results_missing,planned_validation_run_count,8,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_validation_acceptance_scenario,synthetic_ready_validation_all_pass,validation_acceptance_ready,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_validation_acceptance_scenario,synthetic_ready_validation_one_failed,failed_result_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_validation_acceptance_summary,all_scenarios,config_patch_allowed_scenario_count,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceScenario find(
			List<PropellerArchiveRotorSpecRetuneValidationAcceptanceGate.RetuneValidationAcceptanceScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow plannedRow(
			String presetName,
			String validationCaseName
	) {
		return readyValidationRunAudit().rows().stream()
				.filter(row -> presetName.equals(row.presetName())
						&& validationCaseName.equals(row.validationCaseName()))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit readyValidationRunAudit() {
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary handoff =
				PropellerArchiveCompactReferenceHandoff.audit().scenarios().stream()
						.filter(scenario -> "acceptance_ready_reference_reviewed".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();
		PropellerArchiveCompactReferenceTable.CompactReferenceTableAudit table =
				PropellerArchiveCompactReferenceTable.audit(handoff);
		PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeAudit bridge =
				PropellerArchiveRotorSpecFitBridge.audit(table);
		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaAudit delta =
				PropellerArchiveRotorSpecConfigDeltaReport.audit(bridge);
		PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessAudit readiness =
				PropellerArchiveRotorSpecRetuneReadinessGate.audit(delta);
		return PropellerArchiveRotorSpecRetuneValidationRunMatrix.audit(readiness);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_validation_acceptance_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
