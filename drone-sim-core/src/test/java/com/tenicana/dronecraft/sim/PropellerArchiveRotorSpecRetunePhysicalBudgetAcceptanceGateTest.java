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

class PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGateTest {
	@Test
	void auditAggregatesPhysicalBudgetEvidenceWithoutOpeningRuntime() {
		PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceAudit audit =
				PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Physical-Budget-Acceptance-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("validation, physical impact, tip-Mach, Reynolds"));
		assertEquals(106, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(2, audit.budgetRowCount());
		assertEquals(20, audit.scenarioMetricRowCount());
		assertEquals(17, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(2, audit.rows().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceScenarioSummary current =
				find(audit.scenarios(), "current_retune_validation_blocked").summary();
		assertFalse(current.physicalBudgetAvailable());
		assertEquals(0, current.budgetRowCount());
		assertEquals("validation-run-not-planned", current.message());

		PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceScenarioSummary allPass =
				find(audit.scenarios(), "synthetic_ready_validation_all_pass").summary();
		assertTrue(allPass.validationAcceptanceReady());
		assertTrue(allPass.reynoldsBudgetAvailable());
		assertTrue(allPass.compressibilityImpactAvailable());
		assertTrue(allPass.physicalBudgetAvailable());
		assertEquals(2, allPass.budgetRowCount());
		assertEquals(2, allPass.physicalBudgetAcceptedCount());
		assertEquals(2, allPass.compressibilityReviewRequiredCount());
		assertEquals(2, allPass.manualPatchReviewReadyCount());
		assertEquals(0, allPass.playableReferenceAllowedCount());
		assertEquals(36163.200423383874, allPass.minCandidateHoverReynoldsNumber(), 1.0e-9);
		assertEquals(0.6266668829797807, allPass.maxCandidateMaxTipMach(), 1.0e-12);
		assertEquals(8.890939026611733, allPass.maxCandidateMaxThrustLossPercent(), 1.0e-12);
		assertEquals(0.1867097195588465, allPass.maxCandidateMaxLoadFactor(), 1.0e-12);
		assertEquals(1.1620172236195199, allPass.maxCandidateMaxReactionTorqueScale(), 1.0e-12);
		assertEquals(0.09780032929272911, allPass.maxCandidateMaxVibrationProxy(), 1.0e-12);
		assertEquals(0, allPass.configPatchAllowedCount());
		assertEquals(0, allPass.runtimeCouplingAllowedCount());
		assertEquals(0, allPass.gameplayAutoApplyAllowedCount());
		assertEquals("READY", allPass.status());
		assertEquals("physical-budget-accepted-compressibility-review-required", allPass.message());

		PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceScenarioSummary failed =
				find(audit.scenarios(), "synthetic_ready_validation_one_failed").summary();
		assertFalse(failed.physicalBudgetAvailable());
		assertEquals("validation-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().physicalBudgetAvailableScenarioCount());
		assertEquals(2, audit.extrema().totalBudgetRowCount());
		assertEquals(2, audit.extrema().maxBudgetRowCount());
		assertEquals(2, audit.extrema().maxPhysicalBudgetAcceptedCount());
		assertEquals(2, audit.extrema().maxCompressibilityReviewRequiredCount());
		assertEquals(2, audit.extrema().maxManualPatchReviewReadyCount());
		assertEquals(0, audit.extrema().playableReferenceAllowedScenarioCount());
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void rowsCarryAcceptedBudgetButKeepPlayableReferenceClosed() {
		PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceRow racing =
				PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.row(
						"synthetic_ready_validation_all_pass", "racingQuad");

		assertTrue(racing.validationAcceptanceReady());
		assertTrue(racing.manualPatchReviewAllowed());
		assertTrue(racing.physicalImpactPreviewAvailable());
		assertTrue(racing.tipMachBudgetAccepted());
		assertTrue(racing.reynoldsBudgetAccepted());
		assertTrue(racing.compressibilityImpactAccepted());
		assertTrue(racing.compressibilityReviewRequired());
		assertEquals(0.2800893172401641, racing.candidateHoverTipMach(), 1.0e-12);
		assertEquals(0.6266668829797807, racing.candidateMaxTipMach(), 1.0e-12);
		assertEquals(47737.93956854094, racing.candidateHoverReynoldsNumber(), 1.0e-9);
		assertEquals(8.890939026611733, racing.candidateMaxThrustLossPercent(), 1.0e-12);
		assertTrue(racing.physicalBudgetAccepted());
		assertTrue(racing.manualPatchReviewReady());
		assertFalse(racing.playableReferenceAllowed());
		assertFalse(racing.configPatchAllowed());
		assertFalse(racing.runtimeCouplingAllowed());
		assertFalse(racing.gameplayAutoApplyAllowed());
		assertEquals("READY", racing.status());
		assertEquals("physical-budget-accepted-compressibility-review-required", racing.message());

		PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceRow apDrone =
				PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.row(
						"synthetic_ready_validation_all_pass", "apDrone");
		assertTrue(apDrone.physicalBudgetAccepted());
		assertTrue(apDrone.manualPatchReviewReady());
		assertFalse(apDrone.playableReferenceAllowed());
		assertTrue(apDrone.candidateHoverReynoldsNumber() < racing.candidateHoverReynoldsNumber());
		assertTrue(apDrone.candidateMaxThrustLossPercent() < racing.candidateMaxThrustLossPercent());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.row(
						"synthetic_ready_validation_all_pass", "cinewhoop"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.scenario("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceAudit audit =
				PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_physical_budget_acceptance_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_physical_budget_acceptance_summary,all_scenarios,total_budget_row_count,,,,,,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_physical_budget_acceptance_scenario,synthetic_ready_validation_all_pass,physical_budget_accepted_count,,,,,,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_physical_budget_acceptance,synthetic_ready_validation_all_pass,racingQuad,true,true,true,true,true,true,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_physical_budget_acceptance,synthetic_ready_validation_all_pass,apDrone,true,true,true,true,true,true,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_physical_budget_acceptance_summary,all_scenarios,playable_reference_allowed_scenario_count,,,,,,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceScenario find(
			List<PropellerArchiveRotorSpecRetunePhysicalBudgetAcceptanceGate.RetunePhysicalBudgetAcceptanceScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_physical_budget_acceptance_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
