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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGateTest {
	@Test
	void auditAcceptsOnlyValidatedPostDeratePhysicalBudgets() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
				.DeratePhysicalBudgetAcceptanceAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Physical-Budget-Acceptance-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("post-derate thrust"));
		assertEquals(107, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(2, audit.budgetRowCount());
		assertEquals(20, audit.scenarioMetricRowCount());
		assertEquals(18, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(2, audit.rows().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
				.DeratePhysicalBudgetAcceptanceScenarioSummary current =
						find(audit.scenarios(), "current_derate_validation_blocked").summary();
		assertFalse(current.deratePhysicalBudgetAvailable());
		assertEquals(0, current.budgetRowCount());
		assertEquals("cold-air-derate-validation-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
				.DeratePhysicalBudgetAcceptanceScenarioSummary missing =
						find(audit.scenarios(), "synthetic_derate_validation_results_missing").summary();
		assertFalse(missing.deratePhysicalBudgetAvailable());
		assertEquals("cold-air-derate-validation-result-set-incomplete", missing.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
				.DeratePhysicalBudgetAcceptanceScenarioSummary allPass =
						find(audit.scenarios(), "synthetic_derate_validation_all_pass").summary();
		assertTrue(allPass.derateValidationAcceptanceReady());
		assertTrue(allPass.manualDerateReviewAllowed());
		assertTrue(allPass.deratePhysicalBudgetAvailable());
		assertEquals(2, allPass.budgetRowCount());
		assertEquals(2, allPass.postDeratePhysicalBudgetAcceptedCount());
		assertEquals(2, allPass.manualDerateReviewReadyCount());
		assertEquals(2, allPass.rpmControlReviewCompleteCount());
		assertEquals(2, allPass.physicsBudgetReviewCompleteCount());
		assertEquals(2.840173019822779, allPass.maxRequiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals(0.9715982698017723, allPass.minTargetMaxRpmScale(), 1.0e-12);
		assertEquals(9.761226035642207, allPass.maxPostDerateThrustLossPercent(), 1.0e-12);
		assertEquals(0.20498574674848635, allPass.maxPostDerateLoadFactor(), 1.0e-12);
		assertEquals(1.18, allPass.maxPostDerateReactionTorqueScale(), 1.0e-12);
		assertEquals(0.10737348639206429, allPass.maxPostDerateVibrationProxy(), 1.0e-12);
		assertEquals(0, allPass.playableReferenceAllowedCount());
		assertEquals(0, allPass.configPatchAllowedCount());
		assertEquals(0, allPass.runtimeCouplingAllowedCount());
		assertEquals(0, allPass.gameplayAutoApplyAllowedCount());
		assertEquals("READY", allPass.status());
		assertEquals("post-derate-physical-budget-accepted-for-manual-review", allPass.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
				.DeratePhysicalBudgetAcceptanceScenarioSummary failed =
						find(audit.scenarios(), "synthetic_derate_validation_one_failed").summary();
		assertFalse(failed.deratePhysicalBudgetAvailable());
		assertEquals("cold-air-derate-validation-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().deratePhysicalBudgetAvailableScenarioCount());
		assertEquals(2, audit.extrema().totalBudgetRowCount());
		assertEquals(2, audit.extrema().maxBudgetRowCount());
		assertEquals(2, audit.extrema().maxPostDeratePhysicalBudgetAcceptedCount());
		assertEquals(2, audit.extrema().maxManualDerateReviewReadyCount());
		assertEquals(2, audit.extrema().maxRpmControlReviewCompleteCount());
		assertEquals(2, audit.extrema().maxPhysicsBudgetReviewCompleteCount());
		assertEquals(2.840173019822779, audit.extrema().maxRequiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals(0.9715982698017723, audit.extrema().minTargetMaxRpmScale(), 1.0e-12);
		assertEquals(9.761226035642207, audit.extrema().maxPostDerateThrustLossPercent(), 1.0e-12);
		assertEquals(0.20498574674848635, audit.extrema().maxPostDerateLoadFactor(), 1.0e-12);
		assertEquals(1.18, audit.extrema().maxPostDerateReactionTorqueScale(), 1.0e-12);
		assertEquals(0.10737348639206429, audit.extrema().maxPostDerateVibrationProxy(), 1.0e-12);
		assertEquals(0, audit.extrema().playableReferenceAllowedScenarioCount());
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void rowsCarryPostDerateLimitsButKeepMutationClosed() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
				.DeratePhysicalBudgetAcceptanceRow racing =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate.row(
								"synthetic_derate_validation_all_pass",
								"racingQuad",
								"cold_sea_level_minus10c");

		assertTrue(racing.derateValidationAcceptanceReady());
		assertTrue(racing.manualDerateReviewAllowed());
		assertTrue(racing.derateManualReviewMaterialAvailable());
		assertTrue(racing.rpmControlReviewComplete());
		assertTrue(racing.physicsBudgetReviewComplete());
		assertEquals(0.6371344703535363, racing.targetMaxTipMach(), 1.0e-12);
		assertEquals(0.9715982698017723, racing.targetMaxRpmScale(), 1.0e-12);
		assertEquals(2.840173019822779, racing.requiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals("reaction_torque_scale_limit", racing.limitingBudget());
		assertEquals(0.7, racing.maxTipMachLimit(), 1.0e-12);
		assertEquals(9.761226035642207, racing.postDerateThrustLossPercent(), 1.0e-12);
		assertEquals(10.0, racing.maxThrustLossPercentLimit(), 1.0e-12);
		assertEquals(0.20498574674848635, racing.postDerateLoadFactor(), 1.0e-12);
		assertEquals(0.22, racing.maxLoadFactorLimit(), 1.0e-12);
		assertEquals(1.18, racing.postDerateReactionTorqueScale(), 1.0e-12);
		assertEquals(1.18, racing.maxReactionTorqueScaleLimit(), 1.0e-12);
		assertEquals(0.10737348639206429, racing.postDerateVibrationProxy(), 1.0e-12);
		assertEquals(0.12, racing.maxVibrationProxyLimit(), 1.0e-12);
		assertTrue(racing.derateTargetWithinBudget());
		assertTrue(racing.postDeratePhysicalBudgetAccepted());
		assertTrue(racing.manualDerateReviewReady());
		assertFalse(racing.playableReferenceAllowed());
		assertFalse(racing.configPatchAllowed());
		assertFalse(racing.runtimeCouplingAllowed());
		assertFalse(racing.gameplayAutoApplyAllowed());
		assertEquals("READY", racing.status());
		assertEquals("post-derate-physical-budget-accepted-for-manual-review", racing.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
				.DeratePhysicalBudgetAcceptanceRow apDrone =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate.row(
								"synthetic_derate_validation_all_pass",
								"apDrone",
								"cold_sea_level_minus10c");
		assertTrue(apDrone.postDeratePhysicalBudgetAccepted());
		assertTrue(apDrone.manualDerateReviewReady());
		assertTrue(apDrone.targetMaxRpmScale() > racing.targetMaxRpmScale());
		assertTrue(apDrone.requiredMaxRpmDeratePercent() < racing.requiredMaxRpmDeratePercent());
		assertFalse(apDrone.playableReferenceAllowed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate.row(
						"synthetic_derate_validation_all_pass", "cinewhoop", "cold_sea_level_minus10c"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate.scenario(
						"missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
				.DeratePhysicalBudgetAcceptanceAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_physical_budget_acceptance_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_physical_budget_acceptance_summary,all_scenarios,total_budget_row_count,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_physical_budget_acceptance_scenario,synthetic_derate_validation_all_pass,post_derate_physical_budget_accepted_count,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_physical_budget_acceptance,synthetic_derate_validation_all_pass,racingQuad,cold_sea_level_minus10c,true,true,true,true,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_physical_budget_acceptance,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,true,true,true,true,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_physical_budget_acceptance_summary,all_scenarios,runtime_coupling_allowed_scenario_count,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
			.DeratePhysicalBudgetAcceptanceScenario find(
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePhysicalBudgetAcceptanceGate
					.DeratePhysicalBudgetAcceptanceScenario> scenarios,
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
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_physical_budget_acceptance_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
