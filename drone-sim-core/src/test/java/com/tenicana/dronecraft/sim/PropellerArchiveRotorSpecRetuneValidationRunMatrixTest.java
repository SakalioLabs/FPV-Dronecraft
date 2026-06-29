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

class PropellerArchiveRotorSpecRetuneValidationRunMatrixTest {
	@Test
	void auditBuildsCurrentBlockedValidationRows() {
		PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit audit =
				PropellerArchiveRotorSpecRetuneValidationRunMatrix.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Validation-Run-Matrix-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("before any DroneConfig patch"));
		assertEquals(33, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(4, audit.validationCaseCount());
		assertEquals(16, audit.validationRunRowCount());
		assertEquals(10, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(4, audit.cases().size());
		assertEquals(16, audit.rows().size());

		for (PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow row : audit.rows()) {
			assertFalse(row.retuneReviewReady());
			assertFalse(row.validationRunPlanned());
			assertFalse(row.validationInvoked());
			assertFalse(row.validationPassed());
			assertFalse(row.configPatchAllowed());
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("BLOCKED", row.status());
			assertEquals("rotor-spec-full-fit-gate-blocked", row.blocker());
			assertEquals("complete-bridge-full-fit-before-retune-review", row.nextRequiredAction());
		}

		PropellerArchiveRotorSpecRetuneValidationRunMatrix.ValidationCaseDefinition thrust =
				PropellerArchiveRotorSpecRetuneValidationRunMatrix.caseDefinition("static_hover_thrust_closure");
		assertEquals("hover_static", thrust.validationDomain());
		assertEquals("thrust_coefficient", thrust.targetMetric());
		assertEquals(32, thrust.minSampleCount());
		assertEquals(0.08, thrust.maxPrimaryErrorRatio(), 1.0e-12);
		assertEquals(0.05, thrust.maxSecondaryErrorRatio(), 1.0e-12);

		PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow racingHover =
				PropellerArchiveRotorSpecRetuneValidationRunMatrix.row(
						"racingQuad", "static_hover_thrust_closure");
		assertEquals("racingQuad", racingHover.presetName());
		assertEquals("static_hover_thrust_closure", racingHover.validationCaseName());
		assertEquals("hover_static", racingHover.validationDomain());
		assertEquals("thrust_coefficient", racingHover.targetMetric());
		assertEquals(32, racingHover.minSampleCount());

		assertEquals(16, audit.summary().rowCount());
		assertEquals(4, audit.summary().validationCaseCount());
		assertEquals(0, audit.summary().retuneReviewReadyPresetCount());
		assertEquals(0, audit.summary().validationRunPlannedCount());
		assertEquals(0, audit.summary().validationInvokedCount());
		assertEquals(0, audit.summary().validationPassedCount());
		assertEquals(0, audit.summary().configPatchAllowedCount());
		assertEquals(0, audit.summary().runtimeCouplingAllowedCount());
		assertEquals(0, audit.summary().gameplayAutoApplyAllowedCount());
		assertEquals(0, audit.summary().maxPlannedRunsPerPreset());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationRunMatrix.caseDefinition("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationRunMatrix.row("racingQuad", "missing"));
	}

	@Test
	void readyRetuneRowsPlanValidationButDoNotPatchConfig() {
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary handoff =
				findHandoff("acceptance_ready_reference_reviewed");
		PropellerArchiveCompactReferenceTable.CompactReferenceTableAudit table =
				PropellerArchiveCompactReferenceTable.audit(handoff);
		PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeAudit bridge =
				PropellerArchiveRotorSpecFitBridge.audit(table);
		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaAudit delta =
				PropellerArchiveRotorSpecConfigDeltaReport.audit(bridge);
		PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessAudit readiness =
				PropellerArchiveRotorSpecRetuneReadinessGate.audit(delta);
		PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit audit =
				PropellerArchiveRotorSpecRetuneValidationRunMatrix.audit(readiness);

		assertEquals(2, audit.summary().retuneReviewReadyPresetCount());
		assertEquals(8, audit.summary().validationRunPlannedCount());
		assertEquals(0, audit.summary().validationInvokedCount());
		assertEquals(0, audit.summary().validationPassedCount());
		assertEquals(0, audit.summary().configPatchAllowedCount());
		assertEquals(0, audit.summary().runtimeCouplingAllowedCount());
		assertEquals(0, audit.summary().gameplayAutoApplyAllowedCount());
		assertEquals(4, audit.summary().maxPlannedRunsPerPreset());

		for (PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunRow row : audit.rows()) {
			assertFalse(row.configPatchAllowed());
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			if ("racingQuad".equals(row.presetName()) || "apDrone".equals(row.presetName())) {
				assertTrue(row.retuneReviewReady());
				assertTrue(row.validationRunPlanned());
				assertFalse(row.validationInvoked());
				assertFalse(row.validationPassed());
				assertEquals("PENDING_VALIDATION", row.status());
				assertEquals("validation-not-run", row.blocker());
				assertEquals("execute-retune-validation-before-config-patch", row.nextRequiredAction());
			} else if ("cinewhoop".equals(row.presetName())) {
				assertFalse(row.validationRunPlanned());
				assertEquals("thrust-coefficient-large-delta-review-required", row.blocker());
			} else {
				assertEquals("heavyLift", row.presetName());
				assertFalse(row.validationRunPlanned());
				assertEquals("rotor-spec-geometry-reference-missing", row.blocker());
			}
		}
	}

	@Test
	void matrixRejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationRunMatrix.audit(null));
		PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessRow readiness =
				PropellerArchiveRotorSpecRetuneReadinessGate.row("racingQuad");
		PropellerArchiveRotorSpecRetuneValidationRunMatrix.ValidationCaseDefinition caseDefinition =
				PropellerArchiveRotorSpecRetuneValidationRunMatrix.caseDefinition("static_hover_thrust_closure");
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationRunMatrix.row(null, caseDefinition));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneValidationRunMatrix.row(readiness, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneValidationRunMatrix.RetuneValidationRunAudit audit =
				PropellerArchiveRotorSpecRetuneValidationRunMatrix.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_validation_run_matrix_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_validation_run,racingQuad,static_hover_thrust_closure,racingQuad,hover_static,thrust_coefficient,32,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_validation_run,cinewhoop,forward_advance_ratio_sweep,cinewhoop,forward_flight,ct_cp_vs_advance_ratio,48,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_validation_run_summary,all_rows,validation_run_planned_count,,,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_validation_run_method,validation_run_matrix_rule,method,,,,,")));
	}

	private static PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary findHandoff(String name) {
		return PropellerArchiveCompactReferenceHandoff.audit().scenarios().stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_validation_run_matrix_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
