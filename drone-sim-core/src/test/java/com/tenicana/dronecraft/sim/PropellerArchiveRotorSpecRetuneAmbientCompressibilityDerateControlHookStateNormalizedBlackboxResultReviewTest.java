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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReviewTest {
	@Test
	void auditRunsHeldKinematicsForwardPunchoutRegression() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
				.StateNormalizedBlackboxResultReviewAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-State-Normalized-Blackbox-Result-Review-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("held-kinematics cold-air forward-punchout regressions"));
		assertEquals(30, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(2, audit.presetSampleCount());
		assertEquals(2, audit.resultRowCount());
		assertEquals(20, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(0.04, audit.residualDeficitThreshold(), 1.0e-12);
		assertEquals(1.0e-12, audit.stateDeltaEpsilon(), 1.0e-18);
		assertEquals(2, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
				.StateNormalizedBlackboxResultReviewRow racingQuad =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
								.row("racingQuad");
		assertEquals("synthetic_derate_validation_all_pass", racingQuad.scenarioName());
		assertEquals("racingQuad", racingQuad.presetName());
		assertEquals("cold_sea_level_minus10c", racingQuad.ambientCaseName());
		assertEquals("cold_air_forward_punchout_margin", racingQuad.regressionCaseName());
		assertEquals("forward_punchout", racingQuad.flightPhase());
		assertEquals("tip_mach_and_thrust_loss_margin", racingQuad.targetMetric());
		assertEquals(360, racingQuad.sampleCount());
		assertEquals(360, racingQuad.minSampleCount());
		assertEquals(0.005, racingQuad.dtSeconds(), 1.0e-12);
		assertEquals(22.0, racingQuad.forwardSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(0.9715982698017722, racingQuad.targetMaxRpmScale(), 1.0e-12);
		assertEquals(0.9440031978817973, racingQuad.contractThrustRatio(), 1.0e-12);
		assertEquals(0.02014635241961253, racingQuad.freeFlightSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.0, racingQuad.stateNormalizedPrimaryErrorRatio(), 1.0e-12);
		assertEquals(0.021945700352512906,
				racingQuad.stateNormalizedSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0, racingQuad.stateNormalizedPhysicalConstraintViolationCount());
		assertEquals(18, racingQuad.maxMarginSampleIndex());
		assertEquals(0.095, racingQuad.maxMarginTimeSeconds(), 1.0e-12);
		assertEquals(0.9220574975292845, racingQuad.heldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9258615872198296, racingQuad.heldCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.0038040896905451316,
				racingQuad.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(31.101991637704177, racingQuad.heldNeutralThrustNewtons(), 1.0e-12);
		assertEquals(28.677824577638244, racingQuad.heldDeratedThrustNewtons(), 1.0e-12);
		assertEquals(0.6254725282911741, racingQuad.heldNeutralAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.6036652662020685, racingQuad.heldDeratedAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(8.371755205663911e-4, racingQuad.heldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(1.25724959343404e-4, racingQuad.heldDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(0.0, racingQuad.heldStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, racingQuad.heldAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(0.0, racingQuad.heldAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertTrue(racingQuad.stateNormalizedRegressionPassed());
		assertFalse(racingQuad.runtimeCouplingAllowed());
		assertFalse(racingQuad.playableReferenceAllowed());
		assertFalse(racingQuad.gameplayAutoApplyAllowed());
		assertEquals("PASS", racingQuad.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
				.StateNormalizedBlackboxResultReviewRow apDrone =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
								.row("apDrone");
		assertEquals(0.9910302351978076, apDrone.targetMaxRpmScale(), 1.0e-12);
		assertEquals(0.9821409270762219, apDrone.contractThrustRatio(), 1.0e-12);
		assertEquals(0.1961156951528542, apDrone.freeFlightSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.013821464890990382,
				apDrone.stateNormalizedSecondaryErrorRatio(), 1.0e-12);
		assertEquals(43, apDrone.maxMarginSampleIndex());
		assertEquals(0.22, apDrone.maxMarginTimeSeconds(), 1.0e-12);
		assertEquals(0.9683194621852316, apDrone.heldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9852353522302884, apDrone.heldCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.01691589004505678, apDrone.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(29.41062331987714, apDrone.heldNeutralThrustNewtons(), 1.0e-12);
		assertEquals(28.47887895563586, apDrone.heldDeratedThrustNewtons(), 1.0e-12);
		assertEquals(0.6606886772068915, apDrone.heldNeutralAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.6560937872851146, apDrone.heldDeratedAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.050517191947048956, apDrone.heldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.006474327040358316, apDrone.heldDeratedAdvanceRatioRange(), 1.0e-12);
		assertTrue(apDrone.stateNormalizedRegressionPassed());
		assertEquals("PASS", apDrone.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
				.StateNormalizedBlackboxResultReviewSummary summary = audit.summary();
		assertEquals(2, summary.rowCount());
		assertEquals(2, summary.passedRowCount());
		assertEquals(0, summary.failedRowCount());
		assertEquals(1, summary.freeFlightFailedRowCount());
		assertEquals(0, summary.stateNormalizedPhysicalConstraintViolationCount());
		assertEquals(0.1961156951528542, summary.maxFreeFlightSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.021945700352512906,
				summary.maxStateNormalizedSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.01691589004505678,
				summary.maxHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0, summary.maxHeldStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.021945700352512906, summary.racingQuadHeldSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.0038040896905451316,
				summary.racingQuadHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.013821464890990382, summary.apDroneHeldSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.9683194621852316, summary.apDroneHeldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.01691589004505678,
				summary.apDroneHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(43, summary.apDroneMaxMarginSampleIndex());
		assertEquals(0.22, summary.apDroneMaxMarginTimeSeconds(), 1.0e-12);
		assertTrue(summary.stateNormalizedBlackboxResultReviewPassed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
						.row("apDrone", "missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
						.row("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
				.StateNormalizedBlackboxResultReviewAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxResultReview
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_blackbox_result_review_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_blackbox_result_review,synthetic_derate_validation_all_pass,racingQuad,cold_sea_level_minus10c,cold_air_forward_punchout_margin,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_blackbox_result_review,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,cold_air_forward_punchout_margin,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_blackbox_result_review_summary,all,all,all,all,state_normalized_blackbox_result_review_passed,true,bool,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_blackbox_result_review_method,all,all,all,all,method,held-kinematics-forward-punchout-regression,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_blackbox_result_review_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
