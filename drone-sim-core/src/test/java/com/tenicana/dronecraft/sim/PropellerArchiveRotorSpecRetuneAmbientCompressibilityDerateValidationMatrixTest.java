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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrixTest {
	@Test
	void auditBuildsValidationRowsForColdAirDerateTargetsOnly() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Validation-Matrix-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("offline validation runs only"));
		assertEquals(88, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(4, audit.validationCaseCount());
		assertEquals(8, audit.validationRowCount());
		assertEquals(15, audit.scenarioMetricRowCount());
		assertEquals(13, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(4, audit.cases().size());
		assertEquals(8, audit.rows().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationScenarioSummary current =
						find(audit.scenarios(), "current_retune_validation_blocked").summary();
		assertFalse(current.deratePlanAvailable());
		assertFalse(current.validationMatrixAvailable());
		assertEquals(0, current.validationRowCount());
		assertEquals("BLOCKED", current.status());
		assertEquals("validation-run-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationScenarioSummary allPass =
						find(audit.scenarios(), "synthetic_ready_validation_all_pass").summary();
		assertTrue(allPass.validationAcceptanceReady());
		assertTrue(allPass.deratePlanAvailable());
		assertTrue(allPass.validationMatrixAvailable());
		assertEquals(8, allPass.validationRowCount());
		assertEquals(8, allPass.plannedValidationCaseCount());
		assertEquals(0, allPass.validationInvokedCount());
		assertEquals(0, allPass.validationPassedCount());
		assertEquals(8, allPass.derateTargetWithinBudgetCount());
		assertEquals(2.840173019822779, allPass.maxRequiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals(0, allPass.playableReferenceAllowedCount());
		assertEquals(0, allPass.configPatchAllowedCount());
		assertEquals(0, allPass.runtimeCouplingAllowedCount());
		assertEquals(0, allPass.gameplayAutoApplyAllowedCount());
		assertEquals("PENDING_VALIDATION", allPass.status());
		assertEquals("cold-air-derate-validation-planned", allPass.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().validationMatrixAvailableScenarioCount());
		assertEquals(8, audit.extrema().totalValidationRowCount());
		assertEquals(8, audit.extrema().maxValidationRowCount());
		assertEquals(8, audit.extrema().maxPlannedValidationCaseCount());
		assertEquals(0, audit.extrema().maxValidationInvokedCount());
		assertEquals(0, audit.extrema().maxValidationPassedCount());
		assertEquals(8, audit.extrema().maxDerateTargetWithinBudgetCount());
		assertEquals(2.840173019822779, audit.extrema().maxRequiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals(0, audit.extrema().playableReferenceAllowedScenarioCount());
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void rowsCarryValidationTargetsWithoutInvokingValidation() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.DerateValidationCaseDefinition rpm =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.caseDefinition(
						"cold_air_max_rpm_scale_closure");
		assertEquals("rotor_speed_control", rpm.validationDomain());
		assertEquals("target_max_rpm_scale", rpm.targetMetric());
		assertEquals(6, rpm.minSampleCount());
		assertEquals(0.0025, rpm.maxPrimaryErrorRatio(), 1.0e-12);

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationRow racing =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.row(
								"synthetic_ready_validation_all_pass",
								"racingQuad",
								"cold_sea_level_minus10c",
								"reaction_torque_limit_closure");
		assertEquals("runtime_compressibility_curve", racing.validationDomain());
		assertEquals("post_derate_reaction_torque_scale", racing.targetMetric());
		assertEquals(4, racing.minSampleCount());
		assertEquals(0.6371344703535363, racing.targetMaxTipMach(), 1.0e-12);
		assertEquals(0.9715982698017723, racing.targetMaxRpmScale(), 1.0e-12);
		assertEquals(2.840173019822779, racing.requiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals("reaction_torque_scale_limit", racing.limitingBudget());
		assertEquals(9.761226035642207, racing.postDerateThrustLossPercent(), 1.0e-12);
		assertEquals(1.18, racing.postDerateReactionTorqueScale(), 1.0e-12);
		assertEquals(0.10737348639206429, racing.postDerateVibrationProxy(), 1.0e-12);
		assertTrue(racing.derateTargetWithinBudget());
		assertTrue(racing.validationRunPlanned());
		assertFalse(racing.validationInvoked());
		assertFalse(racing.validationPassed());
		assertTrue(racing.validationResultRequired());
		assertFalse(racing.playableReferenceAllowed());
		assertFalse(racing.configPatchAllowed());
		assertFalse(racing.runtimeCouplingAllowed());
		assertFalse(racing.gameplayAutoApplyAllowed());
		assertEquals("PENDING_VALIDATION", racing.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationRow apDrone =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.row(
								"synthetic_ready_validation_all_pass",
								"apDrone",
								"cold_sea_level_minus10c",
								"compressibility_impact_budget_closure");
		assertEquals(0.9910302351978083, apDrone.targetMaxRpmScale(), 1.0e-12);
		assertEquals(0.8969764802191723, apDrone.requiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals("post_derate_thrust_loss_load_vibration", apDrone.targetMetric());
		assertTrue(apDrone.derateTargetWithinBudget());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.caseDefinition("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.scenario("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.row(
						"synthetic_ready_validation_all_pass", "racingQuad", "reference_lab_15c",
						"cold_air_max_rpm_scale_closure"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.row(
						"synthetic_ready_validation_all_pass", "racingQuad", "cold_sea_level_minus10c",
						"missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation_matrix_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation_summary,all_scenarios,,,,total_validation_row_count,8,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation_scenario,synthetic_ready_validation_all_pass,,,,planned_validation_case_count,8,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation,synthetic_ready_validation_all_pass,racingQuad,cold_sea_level_minus10c,reaction_torque_limit_closure,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation,synthetic_ready_validation_all_pass,apDrone,cold_sea_level_minus10c,compressibility_impact_budget_closure,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation_summary,all_scenarios,,,,runtime_coupling_allowed_scenario_count,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
			.RetuneAmbientCompressibilityDerateValidationScenario find(
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
					.RetuneAmbientCompressibilityDerateValidationScenario> scenarios,
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
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation_matrix_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
