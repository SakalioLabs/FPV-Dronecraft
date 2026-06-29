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

class PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrixTest {
	@Test
	void auditPlansCompressibilityReviewCasesWithoutOpeningRuntimeOutputs() {
		PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.RetuneCompressibilityReviewAudit audit =
				PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Compressibility-Review-Matrix-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("offline review runs only"));
		assertEquals(83, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(2, audit.reviewCaseCount());
		assertEquals(4, audit.reviewRowCount());
		assertEquals(15, audit.scenarioMetricRowCount());
		assertEquals(12, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(0.50, audit.nacaTakeoffClimbEffectLowMach(), 1.0e-12);
		assertEquals(0.70, audit.nacaTakeoffClimbEffectHighMach(), 1.0e-12);
		assertEquals(0.91, audit.nacaSeriousLossMach(), 1.0e-12);
		assertEquals(2, audit.cases().size());
		assertEquals(4, audit.rows().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.RetuneCompressibilityReviewScenarioSummary current =
				find(audit.scenarios(), "current_retune_validation_blocked").summary();
		assertFalse(current.physicalBudgetAvailable());
		assertFalse(current.reviewMatrixAvailable());
		assertEquals(0, current.matrixRowCount());
		assertEquals("BLOCKED", current.status());
		assertEquals("validation-run-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.RetuneCompressibilityReviewScenarioSummary allPass =
				find(audit.scenarios(), "synthetic_ready_validation_all_pass").summary();
		assertTrue(allPass.validationAcceptanceReady());
		assertTrue(allPass.physicalBudgetAvailable());
		assertTrue(allPass.reviewMatrixAvailable());
		assertEquals(4, allPass.matrixRowCount());
		assertEquals(4, allPass.plannedReviewCaseCount());
		assertEquals(4, allPass.compressibilityReviewRequiredCount());
		assertEquals(2, allPass.nacaBandCaseCount());
		assertEquals(2, allPass.runtimeCurveCaseCount());
		assertEquals(0, allPass.playableReferenceAllowedCount());
		assertEquals(0, allPass.configPatchAllowedCount());
		assertEquals(0, allPass.runtimeCouplingAllowedCount());
		assertEquals(0, allPass.gameplayAutoApplyAllowedCount());
		assertEquals("PENDING_VALIDATION", allPass.status());
		assertEquals("compressibility-review-matrix-planned", allPass.message());

		PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.RetuneCompressibilityReviewScenarioSummary failed =
				find(audit.scenarios(), "synthetic_ready_validation_one_failed").summary();
		assertFalse(failed.reviewMatrixAvailable());
		assertEquals("validation-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().reviewMatrixAvailableScenarioCount());
		assertEquals(4, audit.extrema().totalMatrixRowCount());
		assertEquals(4, audit.extrema().maxMatrixRowCount());
		assertEquals(4, audit.extrema().maxPlannedReviewCaseCount());
		assertEquals(4, audit.extrema().maxCompressibilityReviewRequiredCount());
		assertEquals(2, audit.extrema().maxNacaBandCaseCount());
		assertEquals(2, audit.extrema().maxRuntimeCurveCaseCount());
		assertEquals(0, audit.extrema().playableReferenceAllowedScenarioCount());
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void rowsCarryCandidateImpactAndReferenceBandChecks() {
		PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.CompressibilityReviewCaseDefinition runtime =
				PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.caseDefinition(
						"runtime_curve_bounded_impact");
		assertEquals("runtime_curve", runtime.validationDomain());
		assertEquals("thrust_loss_load_torque_vibration", runtime.targetMetric());
		assertEquals(4, runtime.minSampleCount());

		PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.RetuneCompressibilityReviewRow racing =
				PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.row(
						"synthetic_ready_validation_all_pass",
						"racingQuad",
						"runtime_curve_bounded_impact");

		assertEquals("runtime_curve", racing.validationDomain());
		assertEquals("thrust_loss_load_torque_vibration", racing.targetMetric());
		assertEquals(4, racing.minSampleCount());
		assertEquals(0.6266668829797807, racing.candidateMaxTipMach(), 1.0e-12);
		assertEquals(8.890939026611733, racing.candidateMaxThrustLossPercent(), 1.0e-12);
		assertEquals(0.1867097195588465, racing.candidateMaxLoadFactor(), 1.0e-12);
		assertEquals(1.1620172236195199, racing.candidateMaxReactionTorqueScale(), 1.0e-12);
		assertEquals(0.09780032929272911, racing.candidateMaxVibrationProxy(), 1.0e-12);
		assertEquals(0.50, racing.referenceEffectBandLowMach(), 1.0e-12);
		assertEquals(0.70, racing.referenceEffectBandHighMach(), 1.0e-12);
		assertEquals(0.91, racing.referenceSeriousLossMach(), 1.0e-12);
		assertTrue(racing.inNacaTakeoffClimbEffectBand());
		assertTrue(racing.belowSeriousLossMach());
		assertTrue(racing.physicalBudgetAccepted());
		assertTrue(racing.compressibilityReviewRequired());
		assertTrue(racing.validationRunPlanned());
		assertTrue(racing.validationResultRequired());
		assertFalse(racing.playableReferenceAllowed());
		assertFalse(racing.configPatchAllowed());
		assertFalse(racing.runtimeCouplingAllowed());
		assertFalse(racing.gameplayAutoApplyAllowed());
		assertEquals("PENDING_VALIDATION", racing.status());
		assertEquals("runtime-curve-impact-review-planned", racing.message());

		PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.RetuneCompressibilityReviewRow apDrone =
				PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.row(
						"synthetic_ready_validation_all_pass",
						"apDrone",
						"naca_takeoff_climb_band_position");
		assertEquals("historical_reference", apDrone.validationDomain());
		assertEquals("tip_mach_loss_band_position", apDrone.targetMetric());
		assertEquals(2, apDrone.minSampleCount());
		assertEquals(0.6143792970390007, apDrone.candidateMaxTipMach(), 1.0e-12);
		assertTrue(apDrone.inNacaTakeoffClimbEffectBand());
		assertTrue(apDrone.belowSeriousLossMach());
		assertTrue(apDrone.candidateMaxThrustLossPercent() < racing.candidateMaxThrustLossPercent());
		assertEquals("naca-band-position-review-planned", apDrone.message());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.caseDefinition("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.scenario("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.row(
						"synthetic_ready_validation_all_pass", "racingQuad", "missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.row(
						"synthetic_ready_validation_all_pass", "cinewhoop", "runtime_curve_bounded_impact"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.RetuneCompressibilityReviewAudit audit =
				PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_compressibility_review_matrix_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_compressibility_review_summary,all_scenarios,,,total_matrix_row_count,4,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_compressibility_review_scenario,synthetic_ready_validation_all_pass,,,planned_review_case_count,4,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_compressibility_review,synthetic_ready_validation_all_pass,racingQuad,runtime_curve_bounded_impact,review_case_planned,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_compressibility_review,synthetic_ready_validation_all_pass,apDrone,naca_takeoff_climb_band_position,review_case_planned,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_compressibility_review_summary,all_scenarios,,,runtime_coupling_allowed_scenario_count,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.RetuneCompressibilityReviewScenario find(
			List<PropellerArchiveRotorSpecRetuneCompressibilityReviewMatrix.RetuneCompressibilityReviewScenario> scenarios,
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
							"docs/data/propeller_archive_rotor_spec_retune_compressibility_review_matrix_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
