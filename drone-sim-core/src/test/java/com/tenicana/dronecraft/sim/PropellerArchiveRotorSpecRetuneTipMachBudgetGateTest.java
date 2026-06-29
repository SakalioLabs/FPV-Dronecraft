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

class PropellerArchiveRotorSpecRetuneTipMachBudgetGateTest {
	@Test
	void auditBuildsTipMachBudgetOnlyAfterImpactPreview() {
		PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetAudit audit =
				PropellerArchiveRotorSpecRetuneTipMachBudgetGate.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Tip-Mach-Budget-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("hover and max tip-Mach limits"));
		assertEquals(76, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(2, audit.budgetRowCount());
		assertEquals(14, audit.scenarioMetricRowCount());
		assertEquals(11, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(15.0, audit.ambientTemperatureCelsius(), 1.0e-12);
		assertEquals(DroneEnvironment.speedOfSoundMetersPerSecond(15.0),
				audit.speedOfSoundMetersPerSecond(), 1.0e-12);
		assertEquals(0.35, audit.hoverTipMachLimit(), 1.0e-12);
		assertEquals(0.70, audit.maxTipMachLimit(), 1.0e-12);
		assertEquals(0.46, audit.compressibilityOnsetMach(), 1.0e-12);
		assertEquals(2, audit.rows().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetScenarioSummary current =
				find(audit.scenarios(), "current_retune_validation_blocked").summary();
		assertFalse(current.tipMachBudgetAvailable());
		assertEquals(0, current.budgetRowCount());
		assertEquals("validation-run-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetScenarioSummary allPass =
				find(audit.scenarios(), "synthetic_ready_validation_all_pass").summary();
		assertTrue(allPass.validationAcceptanceReady());
		assertTrue(allPass.impactPreviewAvailable());
		assertTrue(allPass.tipMachBudgetAvailable());
		assertEquals(2, allPass.budgetRowCount());
		assertEquals(2, allPass.tipMachBudgetAcceptedCount());
		assertEquals(2, allPass.compressibilityReviewRequiredCount());
		assertTrue(allPass.maxCandidateHoverTipMach() < audit.hoverTipMachLimit());
		assertTrue(allPass.maxCandidateMaxTipMach() > audit.compressibilityOnsetMach());
		assertTrue(allPass.maxCandidateMaxTipMach() < audit.maxTipMachLimit());
		assertTrue(allPass.minCandidateHoverMachMargin() > 0.0);
		assertTrue(allPass.minCandidateMaxMachMargin() > 0.0);
		assertEquals(0, allPass.configPatchAllowedCount());
		assertEquals(0, allPass.runtimeCouplingAllowedCount());
		assertEquals(0, allPass.gameplayAutoApplyAllowedCount());
		assertEquals("READY", allPass.status());
		assertEquals("tip-mach-budget-ready-compressibility-review-required", allPass.message());

		PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetScenarioSummary failed =
				find(audit.scenarios(), "synthetic_ready_validation_one_failed").summary();
		assertFalse(failed.tipMachBudgetAvailable());
		assertEquals("validation-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().tipMachBudgetAvailableScenarioCount());
		assertEquals(2, audit.extrema().totalBudgetRowCount());
		assertEquals(2, audit.extrema().maxBudgetRowCount());
		assertEquals(2, audit.extrema().maxTipMachBudgetAcceptedCount());
		assertEquals(2, audit.extrema().maxCompressibilityReviewRequiredCount());
		assertTrue(audit.extrema().maxCandidateHoverTipMach() < audit.hoverTipMachLimit());
		assertTrue(audit.extrema().maxCandidateMaxTipMach() < audit.maxTipMachLimit());
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void budgetRowsMatchImpactPreviewTipSpeedRatios() {
		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactRow impact =
				PropellerArchiveRotorSpecRetunePhysicalImpactPreview.row(
						"synthetic_ready_validation_all_pass", "racingQuad");
		PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetRow budget =
				PropellerArchiveRotorSpecRetuneTipMachBudgetGate.row(
						"synthetic_ready_validation_all_pass", "racingQuad");
		double speedOfSound = DroneEnvironment.speedOfSoundMetersPerSecond(15.0);
		double expectedCandidateHoverMach = impact.candidateHoverTipSpeedMetersPerSecond() / speedOfSound;
		double expectedCandidateMaxMach = expectedCandidateHoverMach
				* impact.candidateMaxRpm()
				/ impact.candidateHoverRpm();

		assertEquals(15.0, budget.ambientTemperatureCelsius(), 1.0e-12);
		assertEquals(speedOfSound, budget.speedOfSoundMetersPerSecond(), 1.0e-12);
		assertEquals(impact.currentHoverTipSpeedMetersPerSecond() / speedOfSound,
				budget.currentHoverTipMach(), 1.0e-12);
		assertEquals(expectedCandidateHoverMach, budget.candidateHoverTipMach(), 1.0e-12);
		assertEquals(expectedCandidateMaxMach, budget.candidateMaxTipMach(), 1.0e-12);
		assertTrue(budget.candidateHoverMachWithinBudget());
		assertTrue(budget.candidateMaxMachWithinBudget());
		assertTrue(budget.candidateMaxCompressibilityReviewRequired());
		assertTrue(budget.tipMachBudgetAccepted());
		assertTrue(budget.manualPatchReviewAllowed());
		assertFalse(budget.configPatchAllowed());
		assertFalse(budget.runtimeCouplingAllowed());
		assertFalse(budget.gameplayAutoApplyAllowed());
		assertEquals("READY", budget.status());
		assertEquals("tip-mach-budget-ready-compressibility-review-required", budget.message());

		PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetRow apDrone =
				PropellerArchiveRotorSpecRetuneTipMachBudgetGate.row(
						"synthetic_ready_validation_all_pass", "apDrone");
		assertTrue(apDrone.candidateHoverTipMach() < budget.candidateHoverTipMach());
		assertTrue(apDrone.candidateMaxCompressibilityReviewRequired());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneTipMachBudgetGate.row(
						"synthetic_ready_validation_all_pass", "cinewhoop"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneTipMachBudgetGate.scenario("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetAudit audit =
				PropellerArchiveRotorSpecRetuneTipMachBudgetGate.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_tip_mach_budget_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_tip_mach_budget_summary,all_scenarios,total_budget_row_count,,,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_tip_mach_budget_scenario,synthetic_ready_validation_all_pass,tip_mach_budget_accepted_count,,,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_tip_mach_budget,synthetic_ready_validation_all_pass,racingQuad,15.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_tip_mach_budget,synthetic_ready_validation_all_pass,apDrone,15.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_tip_mach_budget_summary,all_scenarios,runtime_coupling_allowed_scenario_count,,,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetScenario find(
			List<PropellerArchiveRotorSpecRetuneTipMachBudgetGate.RetuneTipMachBudgetScenario> scenarios,
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
							"docs/data/propeller_archive_rotor_spec_retune_tip_mach_budget_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
