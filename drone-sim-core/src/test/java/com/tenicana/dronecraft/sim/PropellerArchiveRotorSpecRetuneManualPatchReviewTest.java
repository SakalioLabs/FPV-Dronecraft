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

class PropellerArchiveRotorSpecRetuneManualPatchReviewTest {
	@Test
	void auditBuildsManualPatchReviewRowsOnlyAfterValidationAcceptance() {
		PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewAudit audit =
				PropellerArchiveRotorSpecRetuneManualPatchReview.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Manual-Patch-Review-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("never emits a DroneConfig patch"));
		assertEquals(79, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(14, audit.reviewRowCount());
		assertEquals(12, audit.scenarioMetricRowCount());
		assertEquals(10, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(14, audit.rows().size());
		assertEquals(4, audit.scenarios().size());
		assertEquals(7, PropellerArchiveRotorSpecRetuneManualPatchReview.fields().size());

		PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewScenarioSummary current =
				find(audit.scenarios(), "current_retune_validation_blocked").summary();
		assertFalse(current.validationAcceptanceReady());
		assertFalse(current.manualPatchReviewAllowed());
		assertEquals(0, current.reviewRowCount());
		assertEquals("validation-run-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewScenarioSummary missing =
				find(audit.scenarios(), "synthetic_ready_validation_results_missing").summary();
		assertFalse(missing.manualPatchReviewAllowed());
		assertEquals(0, missing.reviewRowCount());
		assertEquals("validation-result-set-incomplete", missing.message());

		PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewScenarioSummary accepted =
				find(audit.scenarios(), "synthetic_ready_validation_all_pass").summary();
		assertTrue(accepted.validationAcceptanceReady());
		assertTrue(accepted.manualPatchReviewAllowed());
		assertEquals(2, accepted.manualPatchCandidatePresetCount());
		assertEquals(14, accepted.reviewRowCount());
		assertEquals(10, accepted.rotorSpecPatchFieldRowCount());
		assertEquals(4, accepted.geometryReferenceFieldRowCount());
		assertEquals(0, accepted.configPatchAllowedCount());
		assertEquals(0, accepted.runtimeCouplingAllowedCount());
		assertEquals(0, accepted.gameplayAutoApplyAllowedCount());
		assertEquals("REVIEW_READY", accepted.status());
		assertEquals("manual-config-patch-review-material-ready", accepted.message());

		PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewScenarioSummary failed =
				find(audit.scenarios(), "synthetic_ready_validation_one_failed").summary();
		assertFalse(failed.manualPatchReviewAllowed());
		assertEquals(0, failed.reviewRowCount());
		assertEquals("validation-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().manualPatchReviewAllowedScenarioCount());
		assertEquals(14, audit.extrema().totalReviewRowCount());
		assertEquals(14, audit.extrema().maxReviewRowCount());
		assertEquals(2, audit.extrema().maxManualPatchCandidatePresetCount());
		assertEquals(10, audit.extrema().maxRotorSpecPatchFieldRowCount());
		assertEquals(4, audit.extrema().maxGeometryReferenceFieldRowCount());
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void reviewRowsSeparateRotorSpecPatchFieldsFromGeometryReferences() {
		PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewRow radius =
				PropellerArchiveRotorSpecRetuneManualPatchReview.row(
						"synthetic_ready_validation_all_pass",
						"racingQuad",
						"rotor_radius_meters");
		assertEquals("racingQuad", radius.presetName());
		assertEquals(0.0635, radius.currentValue(), 1.0e-12);
		assertEquals(0.0635, radius.candidateValue(), 1.0e-12);
		assertEquals(0.0, radius.absoluteDelta(), 1.0e-12);
		assertEquals(1.0, radius.ratioCandidateOverCurrent(), 1.0e-12);
		assertEquals("m", radius.unit());
		assertTrue(radius.rotorSpecPatchField());
		assertFalse(radius.geometryReferenceField());
		assertTrue(radius.manualPatchReviewAllowed());
		assertFalse(radius.configPatchAllowed());
		assertFalse(radius.runtimeCouplingAllowed());
		assertFalse(radius.gameplayAutoApplyAllowed());
		assertEquals("REVIEW_READY", radius.status());
		assertEquals("manual-patch-review-candidate", radius.message());

		PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewRow beta =
				PropellerArchiveRotorSpecRetuneManualPatchReview.row(
						"synthetic_ready_validation_all_pass",
						"apDrone",
						"blade_beta_radians");
		assertEquals("rad", beta.unit());
		assertFalse(beta.rotorSpecPatchField());
		assertTrue(beta.geometryReferenceField());
		assertTrue(beta.candidateValue() > 0.0);
		assertEquals("geometry-reference-review-required", beta.message());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneManualPatchReview.row(
						"synthetic_ready_validation_all_pass", "cinewhoop", "rotor_radius_meters"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneManualPatchReview.row(
						"missing", "racingQuad", "rotor_radius_meters"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneManualPatchReview.scenario("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewAudit audit =
				PropellerArchiveRotorSpecRetuneManualPatchReview.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_manual_patch_review_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_manual_patch_review_summary,all_scenarios,total_review_row_count,14,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_manual_patch_review_scenario,synthetic_ready_validation_all_pass,review_row_count,14,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_manual_patch_review,synthetic_ready_validation_all_pass,racingQuad,rotor_radius_meters,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_manual_patch_review,synthetic_ready_validation_all_pass,apDrone,blade_beta_radians,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_manual_patch_review_summary,all_scenarios,config_patch_allowed_scenario_count,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewScenario find(
			List<PropellerArchiveRotorSpecRetuneManualPatchReview.RetuneManualPatchReviewScenario> scenarios,
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
							"docs/data/propeller_archive_rotor_spec_retune_manual_patch_review_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
