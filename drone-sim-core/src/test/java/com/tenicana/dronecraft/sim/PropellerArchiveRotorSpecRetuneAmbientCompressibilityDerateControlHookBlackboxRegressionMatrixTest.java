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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrixTest {
	@Test
	void auditBuildsSkippedCurrentRowsAndPendingSyntheticReadyRows() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Blackbox-Regression-Matrix-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("offline target-omega derate cases"));
		assertEquals(37, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(2, audit.contractScenarioCount());
		assertEquals(2, audit.presetSampleCount());
		assertEquals(4, audit.regressionCaseCount());
		assertEquals(16, audit.regressionRunRowCount());
		assertEquals(13, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(4, audit.cases().size());
		assertEquals(16, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.BlackboxRegressionCaseDefinition hover =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
								.caseDefinition("hover_hold_target_omega_scale");
		assertEquals("hover_hold", hover.flightPhase());
		assertEquals("target_omega_scale_closure", hover.targetMetric());
		assertEquals(240, hover.minSampleCount());
		assertEquals(0.005, hover.maxPrimaryErrorRatio(), 1.0e-12);
		assertEquals(0.02, hover.maxSecondaryErrorRatio(), 1.0e-12);
		assertTrue(hover.requiresMotorResponseReview());
		assertFalse(hover.requiresFailsafeReview());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionRunRow current =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
								.row(
										"synthetic_derate_validation_all_pass",
										"racingQuad",
										"hover_hold_target_omega_scale");
		assertFalse(current.controlHookImplementationReady());
		assertFalse(current.regressionRunPlanned());
		assertFalse(current.regressionInvoked());
		assertFalse(current.regressionPassed());
		assertEquals(0.9715982698017723, current.targetMaxRpmScale(), 1.0e-12);
		assertEquals(28310.07356552835, current.contractedMaxRpm(), 1.0e-12);
		assertEquals(5.599680211820246, current.equivalentMaxThrustLossPercent(), 1.0e-12);
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.playableReferenceAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals("SKIPPED", current.status());
		assertEquals("derate-hook-blackbox-regression-missing", current.blocker());
		assertEquals("add-blackbox-regression-for-cold-air-derate-hook",
				current.nextRequiredAction());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionRunRow ready =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
								.row(
										"synthetic_control_hook_ready_reviewed",
										"apDrone",
										"full_throttle_ramp_derate_clamp");
		assertTrue(ready.controlHookImplementationReady());
		assertTrue(ready.regressionRunPlanned());
		assertFalse(ready.regressionInvoked());
		assertFalse(ready.regressionPassed());
		assertEquals("throttle_ramp", ready.flightPhase());
		assertEquals("target_omega_clamp_and_slew", ready.targetMetric());
		assertEquals(180, ready.minSampleCount());
		assertEquals(0.006, ready.maxPrimaryErrorRatio(), 1.0e-12);
		assertEquals(0.03, ready.maxSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.9910302351978083, ready.targetMaxRpmScale(), 1.0e-12);
		assertEquals(29472.808857731186, ready.contractedMaxRpm(), 1.0e-12);
		assertEquals(1.7859072923776753, ready.equivalentMaxThrustLossPercent(), 1.0e-12);
		assertFalse(ready.runtimeCouplingAllowed());
		assertEquals("PENDING_REGRESSION", ready.status());
		assertEquals("blackbox-regression-not-run", ready.blocker());
		assertEquals("execute-cold-air-target-omega-blackbox-regression-before-runtime-candidate-derate",
				ready.nextRequiredAction());

		assertEquals(16, audit.summary().rowCount());
		assertEquals(4, audit.summary().regressionCaseCount());
		assertEquals(2, audit.summary().contractScenarioCount());
		assertEquals(1, audit.summary().controlHookImplementationReadyScenarioCount());
		assertEquals(8, audit.summary().regressionRunPlannedCount());
		assertEquals(0, audit.summary().regressionInvokedCount());
		assertEquals(0, audit.summary().regressionPassedCount());
		assertEquals(0, audit.summary().runtimeCouplingAllowedCount());
		assertEquals(0, audit.summary().playableReferenceAllowedCount());
		assertEquals(0, audit.summary().gameplayAutoApplyAllowedCount());
		assertEquals(8, audit.summary().maxRowsPerScenario());
		assertEquals(0.9715982698017723, audit.summary().minTargetMaxRpmScale(), 1.0e-12);
		assertEquals(5.599680211820246, audit.summary().maxEquivalentMaxThrustLossPercent(), 1.0e-12);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
						.caseDefinition("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
						.row("synthetic_control_hook_ready_reviewed", "racingQuad", "missing"));
	}

	@Test
	void matrixRejectsInvalidInputs() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
				.DerateControlHookReadinessAudit readiness =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate.audit();
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractAudit contract =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.audit();
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
				.DerateControlHookReadinessRow ready =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate.row(
								"synthetic_control_hook_ready_reviewed");
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow contractRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.row(
								"synthetic_derate_validation_all_pass",
								"racingQuad",
								"cold_sea_level_minus10c");
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.BlackboxRegressionCaseDefinition caseDefinition =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
								.caseDefinition("hover_hold_target_omega_scale");

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
						.audit(null, contract));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
						.audit(readiness, null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
						.row(null, contractRow, caseDefinition));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
						.row(ready, null, caseDefinition));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
						.row(ready, contractRow, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_regression_matrix_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_regression_run,synthetic_derate_validation_all_pass,racingQuad,cold_sea_level_minus10c,hover_hold_target_omega_scale,hover_hold,target_omega_scale_closure,240,0.005,0.02,false,false,false,false,0.9715982698017723,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_regression_run,synthetic_control_hook_ready_reviewed,apDrone,cold_sea_level_minus10c,full_throttle_ramp_derate_clamp,throttle_ramp,target_omega_clamp_and_slew,180,0.006,0.03,true,true,false,false,0.9910302351978083,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_regression_summary,all_scenarios,regression_run_planned_count,8,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_regression_summary,all_scenarios,runtime_coupling_allowed_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_regression_method,all_scenarios,method,cold-air-target-omega-hook-blackbox-regression-matrix,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_regression_matrix_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
