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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReviewTest {
	@Test
	void auditBuildsManualReviewRowsOnlyAfterColdAirDerateValidationAcceptance() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview
				.DerateManualReviewAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Manual-Review-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("max-RPM derate targets"));
		assertEquals(109, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(14, audit.reviewRowCount());
		assertEquals(18, audit.scenarioMetricRowCount());
		assertEquals(16, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(14, audit.rows().size());
		assertEquals(4, audit.scenarios().size());
		assertEquals(7, PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.fields().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview
				.DerateManualReviewScenarioSummary current =
						find(audit.scenarios(), "current_derate_validation_blocked").summary();
		assertFalse(current.derateValidationAcceptanceReady());
		assertFalse(current.manualDerateReviewAllowed());
		assertEquals(0, current.reviewRowCount());
		assertEquals("cold-air-derate-validation-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview
				.DerateManualReviewScenarioSummary missing =
						find(audit.scenarios(), "synthetic_derate_validation_results_missing").summary();
		assertFalse(missing.manualDerateReviewAllowed());
		assertEquals(0, missing.reviewRowCount());
		assertEquals("cold-air-derate-validation-result-set-incomplete", missing.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview
				.DerateManualReviewScenarioSummary accepted =
						find(audit.scenarios(), "synthetic_derate_validation_all_pass").summary();
		assertTrue(accepted.derateValidationAcceptanceReady());
		assertTrue(accepted.manualDerateReviewAllowed());
		assertEquals(2, accepted.manualDerateReviewCandidateCount());
		assertEquals(14, accepted.reviewRowCount());
		assertEquals(6, accepted.rpmControlReviewFieldRowCount());
		assertEquals(8, accepted.physicsBudgetReviewFieldRowCount());
		assertEquals(2, accepted.derateTargetWithinBudgetCount());
		assertEquals(2.840173019822779, accepted.maxRequiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals(0.9715982698017723, accepted.minTargetMaxRpmScale(), 1.0e-12);
		assertEquals(9.761226035642207, accepted.maxPostDerateThrustLossPercent(), 1.0e-12);
		assertEquals(1.18, accepted.maxPostDerateReactionTorqueScale(), 1.0e-12);
		assertEquals(0.10737348639206429, accepted.maxPostDerateVibrationProxy(), 1.0e-12);
		assertEquals(0, accepted.playableReferenceAllowedCount());
		assertEquals(0, accepted.configPatchAllowedCount());
		assertEquals(0, accepted.runtimeCouplingAllowedCount());
		assertEquals(0, accepted.gameplayAutoApplyAllowedCount());
		assertEquals("REVIEW_READY", accepted.status());
		assertEquals("manual-cold-air-derate-review-material-ready", accepted.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview
				.DerateManualReviewScenarioSummary failed =
						find(audit.scenarios(), "synthetic_derate_validation_one_failed").summary();
		assertFalse(failed.manualDerateReviewAllowed());
		assertEquals(0, failed.reviewRowCount());
		assertEquals("cold-air-derate-validation-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().manualDerateReviewAllowedScenarioCount());
		assertEquals(14, audit.extrema().totalReviewRowCount());
		assertEquals(14, audit.extrema().maxReviewRowCount());
		assertEquals(2, audit.extrema().maxManualDerateReviewCandidateCount());
		assertEquals(6, audit.extrema().maxRpmControlReviewFieldRowCount());
		assertEquals(8, audit.extrema().maxPhysicsBudgetReviewFieldRowCount());
		assertEquals(2.840173019822779, audit.extrema().maxRequiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals(0.9715982698017723, audit.extrema().minTargetMaxRpmScale(), 1.0e-12);
		assertEquals(9.761226035642207, audit.extrema().maxPostDerateThrustLossPercent(), 1.0e-12);
		assertEquals(1.18, audit.extrema().maxPostDerateReactionTorqueScale(), 1.0e-12);
		assertEquals(0.10737348639206429, audit.extrema().maxPostDerateVibrationProxy(), 1.0e-12);
		assertEquals(0, audit.extrema().playableReferenceAllowedScenarioCount());
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void reviewRowsSeparateRpmControlTargetsFromPostDerateBudgets() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview
				.DerateManualReviewRow rpmScale =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.row(
								"synthetic_derate_validation_all_pass",
								"racingQuad",
								"cold_sea_level_minus10c",
								"target_max_rpm_scale");
		assertEquals(1.0, rpmScale.beforeDerateValue(), 1.0e-12);
		assertEquals(0.9715982698017723, rpmScale.reviewValue(), 1.0e-12);
		assertEquals(0.028401730198227718, rpmScale.absoluteDelta(), 1.0e-12);
		assertEquals(0.9715982698017723, rpmScale.ratioReviewOverBefore(), 1.0e-12);
		assertEquals("ratio", rpmScale.unit());
		assertEquals("reaction_torque_scale_limit", rpmScale.limitingBudget());
		assertTrue(rpmScale.derateTargetWithinBudget());
		assertTrue(rpmScale.rpmControlReviewField());
		assertFalse(rpmScale.physicsBudgetReviewField());
		assertTrue(rpmScale.manualDerateReviewAllowed());
		assertFalse(rpmScale.playableReferenceAllowed());
		assertFalse(rpmScale.configPatchAllowed());
		assertFalse(rpmScale.runtimeCouplingAllowed());
		assertFalse(rpmScale.gameplayAutoApplyAllowed());
		assertEquals("REVIEW_READY", rpmScale.status());
		assertEquals("manual-max-rpm-derate-review-candidate", rpmScale.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview
				.DerateManualReviewRow reactionTorque =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.row(
								"synthetic_derate_validation_all_pass",
								"racingQuad",
								"cold_sea_level_minus10c",
								"post_derate_reaction_torque_scale");
		assertTrue(reactionTorque.beforeDerateValue() > reactionTorque.reviewValue());
		assertEquals(1.18, reactionTorque.reviewValue(), 1.0e-12);
		assertFalse(reactionTorque.rpmControlReviewField());
		assertTrue(reactionTorque.physicsBudgetReviewField());
		assertEquals("post-derate-budget-review-required", reactionTorque.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview
				.DerateManualReviewRow apDroneDerate =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.row(
								"synthetic_derate_validation_all_pass",
								"apDrone",
								"cold_sea_level_minus10c",
								"required_max_rpm_derate_percent");
		assertEquals(0.0, apDroneDerate.beforeDerateValue(), 1.0e-12);
		assertEquals(0.8969764802191723, apDroneDerate.reviewValue(), 1.0e-12);
		assertEquals(0.0, apDroneDerate.ratioReviewOverBefore(), 1.0e-12);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.row(
						"synthetic_derate_validation_all_pass",
						"cinewhoop",
						"cold_sea_level_minus10c",
						"target_max_rpm_scale"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.row(
						"missing",
						"racingQuad",
						"cold_sea_level_minus10c",
						"target_max_rpm_scale"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.scenario("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview
				.DerateManualReviewAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_manual_review_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_manual_review_summary,all_scenarios,total_review_row_count,14,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_manual_review_scenario,synthetic_derate_validation_all_pass,review_row_count,14,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_manual_review,synthetic_derate_validation_all_pass,racingQuad,cold_sea_level_minus10c,target_max_rpm_scale,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_manual_review,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,required_max_rpm_derate_percent,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_manual_review_summary,all_scenarios,runtime_coupling_allowed_scenario_count,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview
			.DerateManualReviewScenario find(
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateManualReview
					.DerateManualReviewScenario> scenarios,
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
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_manual_review_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
