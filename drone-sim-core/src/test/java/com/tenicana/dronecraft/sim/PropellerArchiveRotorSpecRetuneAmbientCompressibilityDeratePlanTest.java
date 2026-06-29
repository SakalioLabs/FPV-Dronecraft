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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlanTest {
	@Test
	void auditBuildsColdAirDerateTargetsWithoutOpeningRuntimeOutputs() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateAudit audit =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Plan-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("offline max-tip-Mach and max-RPM scale targets"));
		assertEquals(97, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(2, audit.derateRowCount());
		assertEquals(18, audit.scenarioMetricRowCount());
		assertEquals(16, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(2, audit.rows().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateScenarioSummary current =
				find(audit.scenarios(), "current_retune_validation_blocked").summary();
		assertFalse(current.ambientSensitivityAvailable());
		assertFalse(current.deratePlanAvailable());
		assertEquals(0, current.derateRowCount());
		assertEquals("BLOCKED", current.status());
		assertEquals("validation-run-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateScenarioSummary allPass =
				find(audit.scenarios(), "synthetic_ready_validation_all_pass").summary();
		assertTrue(allPass.validationAcceptanceReady());
		assertTrue(allPass.ambientSensitivityAvailable());
		assertTrue(allPass.deratePlanAvailable());
		assertEquals(2, allPass.derateRowCount());
		assertEquals(2, allPass.coldAirFailedRowCount());
		assertEquals(2, allPass.derateTargetWithinBudgetCount());
		assertEquals(2.840173019822779, allPass.maxRequiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals(0.9715982698017723, allPass.minTargetMaxRpmScale(), 1.0e-12);
		assertEquals(0.6371344703535363, allPass.minTargetMaxTipMach(), 1.0e-12);
		assertEquals(9.761226035642207, allPass.maxPostDerateThrustLossPercent(), 1.0e-12);
		assertEquals(1.18, allPass.maxPostDerateReactionTorqueScale(), 1.0e-12);
		assertEquals(0.10737348639206429, allPass.maxPostDerateVibrationProxy(), 1.0e-12);
		assertEquals(0, allPass.playableReferenceAllowedCount());
		assertEquals(0, allPass.configPatchAllowedCount());
		assertEquals(0, allPass.runtimeCouplingAllowedCount());
		assertEquals(0, allPass.gameplayAutoApplyAllowedCount());
		assertEquals("PENDING_REVIEW", allPass.status());
		assertEquals("cold-air-derate-target-planned", allPass.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().deratePlanAvailableScenarioCount());
		assertEquals(2, audit.extrema().totalDerateRowCount());
		assertEquals(2, audit.extrema().maxDerateRowCount());
		assertEquals(2, audit.extrema().maxColdAirFailedRowCount());
		assertEquals(2, audit.extrema().maxDerateTargetWithinBudgetCount());
		assertEquals(2.840173019822779, audit.extrema().maxRequiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals(0.9715982698017723, audit.extrema().minTargetMaxRpmScale(), 1.0e-12);
		assertEquals(0, audit.extrema().playableReferenceAllowedScenarioCount());
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void rowsCarryReactionTorqueLimitedDerateTargets() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateRow racing =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.row(
						"synthetic_ready_validation_all_pass",
						"racingQuad",
						"cold_sea_level_minus10c");

		assertEquals(-10.0, racing.ambientTemperatureCelsius(), 1.0e-12);
		assertEquals(0.6557591652396883, racing.candidateMaxTipMach(), 1.0e-12);
		assertEquals(11.309908309581662, racing.candidateMaxThrustLossPercent(), 1.0e-12);
		assertEquals(1.2129370394460944, racing.candidateMaxReactionTorqueScale(), 1.0e-12);
		assertEquals(0.6371344703535363, racing.targetMaxTipMach(), 1.0e-12);
		assertEquals(0.06286552964646365, racing.targetMaxTipMachMargin(), 1.0e-12);
		assertEquals(0.9715982698017723, racing.targetMaxRpmScale(), 1.0e-12);
		assertEquals(2.840173019822779, racing.requiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals("reaction_torque_scale_limit", racing.limitingBudget());
		assertEquals(9.761226035642207, racing.postDerateThrustLossPercent(), 1.0e-12);
		assertEquals(0.20498574674848635, racing.postDerateLoadFactor(), 1.0e-12);
		assertEquals(1.18, racing.postDerateReactionTorqueScale(), 1.0e-12);
		assertEquals(0.10737348639206429, racing.postDerateVibrationProxy(), 1.0e-12);
		assertFalse(racing.ambientSensitivityAccepted());
		assertTrue(racing.derateTargetWithinBudget());
		assertFalse(racing.playableReferenceAllowed());
		assertFalse(racing.configPatchAllowed());
		assertFalse(racing.runtimeCouplingAllowed());
		assertFalse(racing.gameplayAutoApplyAllowed());
		assertEquals("PENDING_REVIEW", racing.status());
		assertEquals("cold-air-derate-target-planned", racing.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateRow apDrone =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.row(
						"synthetic_ready_validation_all_pass",
						"apDrone",
						"cold_sea_level_minus10c");
		assertEquals(0.6429011423918513, apDrone.candidateMaxTipMach(), 1.0e-12);
		assertEquals(0.9910302351978083, apDrone.targetMaxRpmScale(), 1.0e-12);
		assertEquals(0.8969764802191723, apDrone.requiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals("reaction_torque_scale_limit", apDrone.limitingBudget());
		assertTrue(apDrone.derateTargetWithinBudget());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.scenario("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.row(
						"synthetic_ready_validation_all_pass", "racingQuad", "reference_lab_15c"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.row(
						"synthetic_ready_validation_all_pass", "cinewhoop", "cold_sea_level_minus10c"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateAudit audit =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_plan_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_summary,all_scenarios,,,,total_derate_row_count,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_scenario,synthetic_ready_validation_all_pass,,,,derate_target_within_budget_count,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate,synthetic_ready_validation_all_pass,racingQuad,cold_sea_level_minus10c,0.6371344703535363,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate,synthetic_ready_validation_all_pass,apDrone,cold_sea_level_minus10c,0.6371344703535363,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_summary,all_scenarios,,,,runtime_coupling_allowed_scenario_count,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateScenario find(
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDeratePlan.RetuneAmbientCompressibilityDerateScenario> scenarios,
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
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_plan_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
