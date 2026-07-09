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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReviewTest {
	@Test
	void auditBuildsReviewRowsOnlyAfterBlackboxAcceptance() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
				.DerateControlHookImplementationReviewAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Implementation-Review-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("target-omega insertion"));
		assertEquals(75, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(10, audit.reviewRowCount());
		assertEquals(12, audit.scenarioMetricRowCount());
		assertEquals(10, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(5, audit.reviewItems().size());
		assertEquals(10, audit.rows().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
				.DerateControlHookImplementationReviewScenarioSummary current =
						find(audit.scenarios(), "current_control_hook_blackbox_blocked").summary();
		assertFalse(current.blackboxRegressionAcceptanceReady());
		assertFalse(current.manualControlHookReviewAllowed());
		assertEquals(0, current.reviewRowCount());
		assertEquals("blackbox-regression-run-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
				.DerateControlHookImplementationReviewScenarioSummary missing =
						find(audit.scenarios(), "synthetic_control_hook_blackbox_results_missing").summary();
		assertFalse(missing.manualControlHookReviewAllowed());
		assertEquals(0, missing.reviewRowCount());
		assertEquals("blackbox-regression-result-set-incomplete", missing.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
				.DerateControlHookImplementationReviewScenarioSummary accepted =
						find(audit.scenarios(), "synthetic_control_hook_blackbox_all_pass").summary();
		assertTrue(accepted.blackboxRegressionAcceptanceReady());
		assertTrue(accepted.manualControlHookReviewAllowed());
		assertEquals(2, accepted.manualControlHookCandidatePresetCount());
		assertEquals(10, accepted.reviewRowCount());
		assertEquals(6, accepted.numericReviewRowCount());
		assertEquals(8, accepted.telemetryRequiredRowCount());
		assertEquals(2, accepted.leakGuardRowCount());
		assertEquals(0, accepted.runtimeImplementationAllowedCount());
		assertEquals(0, accepted.runtimeCouplingAllowedCount());
		assertEquals(0, accepted.playableReferenceAllowedCount());
		assertEquals(0, accepted.gameplayAutoApplyAllowedCount());
		assertEquals("REVIEW_READY", accepted.status());
		assertEquals("manual-control-hook-implementation-review-material-ready", accepted.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
				.DerateControlHookImplementationReviewScenarioSummary failed =
						find(audit.scenarios(), "synthetic_control_hook_blackbox_one_failed").summary();
		assertFalse(failed.manualControlHookReviewAllowed());
		assertEquals(0, failed.reviewRowCount());
		assertEquals("blackbox-regression-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().manualControlHookReviewAllowedScenarioCount());
		assertEquals(10, audit.extrema().totalReviewRowCount());
		assertEquals(10, audit.extrema().maxReviewRowCount());
		assertEquals(2, audit.extrema().maxManualControlHookCandidatePresetCount());
		assertEquals(6, audit.extrema().maxNumericReviewRowCount());
		assertEquals(8, audit.extrema().maxTelemetryRequiredRowCount());
		assertEquals(2, audit.extrema().maxLeakGuardRowCount());
		assertEquals(0, audit.extrema().runtimeImplementationAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().playableReferenceAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void reviewRowsDescribeTargetOmegaInsertionAndLeakGuards() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
				.DerateControlHookImplementationReviewRow scale =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
								.row(
										"synthetic_control_hook_blackbox_all_pass",
										"racingQuad",
										"target_max_rpm_scale");
		assertEquals("racingQuad", scale.presetName());
		assertEquals("cold_sea_level_minus10c", scale.ambientCaseName());
		assertEquals("0.9715982698017722", scale.reviewValue());
		assertEquals(0.9715982698017722, scale.numericValue(), 1.0e-12);
		assertTrue(scale.numericItem());
		assertEquals("ratio", scale.unit());
		assertEquals("DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response",
				scale.controlBoundary());
		assertEquals("target_omega_scale_closure", scale.blackboxMetric());
		assertTrue(scale.telemetryRequired());
		assertFalse(scale.leakGuardItem());
		assertTrue(scale.blackboxRegressionAcceptanceReady());
		assertTrue(scale.manualControlHookReviewAllowed());
		assertFalse(scale.runtimeImplementationAllowed());
		assertFalse(scale.runtimeCouplingAllowed());
		assertFalse(scale.playableReferenceAllowed());
		assertFalse(scale.gameplayAutoApplyAllowed());
		assertEquals("REVIEW_READY", scale.status());
		assertEquals("manual-control-hook-review-candidate", scale.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
				.DerateControlHookImplementationReviewRow rpm =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
								.row(
										"synthetic_control_hook_blackbox_all_pass",
										"apDrone",
										"contracted_max_rpm");
		assertEquals("29472.808857731186", rpm.reviewValue());
		assertEquals(29472.808857731186, rpm.numericValue(), 1.0e-12);
		assertEquals("target_omega_clamp_and_slew", rpm.blackboxMetric());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
				.DerateControlHookImplementationReviewRow boundary =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
								.row(
										"synthetic_control_hook_blackbox_all_pass",
										"racingQuad",
										"hook_insertion_boundary");
		assertFalse(boundary.numericItem());
		assertTrue(Double.isNaN(boundary.numericValue()));
		assertTrue(boundary.reviewValue().contains("state.setMotorTargetOmega"));
		assertTrue(boundary.telemetryRequired());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
				.DerateControlHookImplementationReviewRow leak =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
								.row(
										"synthetic_control_hook_blackbox_all_pass",
										"apDrone",
										"runtime_leak_guard");
		assertTrue(leak.leakGuardItem());
		assertFalse(leak.telemetryRequired());
		assertEquals("runtime-leak-guard-review-required", leak.message());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
						.row("synthetic_control_hook_blackbox_all_pass", "cinewhoop",
								"target_max_rpm_scale"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
						.row("missing", "racingQuad", "target_max_rpm_scale"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
						.scenario("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
				.DerateControlHookImplementationReviewAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_implementation_review_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_implementation_review_summary,all_scenarios,total_review_row_count,10,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_implementation_review_scenario,synthetic_control_hook_blackbox_all_pass,review_row_count,10,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_implementation_review,synthetic_control_hook_blackbox_all_pass,racingQuad,cold_sea_level_minus10c,target_max_rpm_scale,0.9715982698017722,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_implementation_review,synthetic_control_hook_blackbox_all_pass,apDrone,cold_sea_level_minus10c,runtime_leak_guard,candidate-derate-runtime-coupling-playable-export-gameplay-auto-apply-remain-closed,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_implementation_review_summary,all_scenarios,runtime_implementation_allowed_scenario_count,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
			.DerateControlHookImplementationReviewScenario find(
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookImplementationReview
					.DerateControlHookImplementationReviewScenario> scenarios,
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
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_implementation_review_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
