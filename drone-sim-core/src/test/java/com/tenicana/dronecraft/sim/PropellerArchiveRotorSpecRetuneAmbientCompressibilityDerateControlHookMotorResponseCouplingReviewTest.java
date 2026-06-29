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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReviewTest {
	@Test
	void auditReviewsTargetOmegaDerateThroughMotorResponseBoundary() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview
				.DerateControlHookMotorResponseCouplingAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Motor-Response-Coupling-Review-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("ESC-output to motor-response boundary"));
		assertEquals(27, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(2, audit.contractRowCount());
		assertEquals(3, audit.reviewCaseCount());
		assertEquals(6, audit.reviewRowCount());
		assertEquals(14, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(
				"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response",
				audit.controlBoundary());
		assertEquals(3, audit.reviewCases().size());
		assertEquals(6, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview
				.ReviewCaseDefinition ramp =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview
								.reviewCase("full_throttle_ramp_motor_response");
		assertEquals("throttle_ramp", ramp.flightPhase());
		assertEquals(180, ramp.sampleCount());
		assertEquals(0.006, ramp.dtSeconds(), 1.0e-12);
		assertEquals(2.0e-6, ramp.maxAllowedEscElectricalOutputDelta(), 1.0e-18);
		assertEquals(0.012, ramp.maxAllowedTargetScaleErrorRatio(), 1.0e-12);

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview
				.DerateControlHookMotorResponseCouplingReviewRow racingFirstFrame =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview
								.row("racingQuad", "first_frame_target_scale_closure");
		assertEquals("synthetic_derate_validation_all_pass", racingFirstFrame.scenarioName());
		assertEquals("cold_sea_level_minus10c", racingFirstFrame.ambientCaseName());
		assertEquals("single_frame_full_throttle", racingFirstFrame.flightPhase());
		assertEquals(1, racingFirstFrame.sampleCount());
		assertEquals(0.9715982698017723, racingFirstFrame.targetMaxRpmScale(), 1.0e-12);
		assertEquals(28310.07356552835, racingFirstFrame.contractedMaxRpm(), 1.0e-12);
		assertEquals(0.0, racingFirstFrame.maxEscElectricalOutputDelta(), 1.0e-12);
		assertEquals(0.0, racingFirstFrame.maxTargetScaleErrorRatio(), 1.0e-12);
		assertEquals(0.0, racingFirstFrame.maxTargetOvershootRatio(), 1.0e-12);
		assertEquals(0.0, racingFirstFrame.maxMotorOmegaAboveNeutralRatio(), 1.0e-12);
		assertTrue(racingFirstFrame.defaultPresetTargetScaleNeutral());
		assertTrue(racingFirstFrame.motorResponseCouplingReviewed());
		assertFalse(racingFirstFrame.configMutationAllowed());
		assertFalse(racingFirstFrame.runtimeCouplingAllowed());
		assertFalse(racingFirstFrame.playableReferenceAllowed());
		assertFalse(racingFirstFrame.gameplayAutoApplyAllowed());
		assertEquals("REVIEWED", racingFirstFrame.status());
		assertEquals("target-omega-derate-couples-before-motor-response-with-esc-output-invariant",
				racingFirstFrame.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview
				.DerateControlHookMotorResponseCouplingReviewRow apHover =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview
								.row("apDrone", "hover_hold_tracking_response");
		assertEquals(0.9910302351978083, apHover.targetMaxRpmScale(), 1.0e-12);
		assertEquals(29472.808857731186, apHover.contractedMaxRpm(), 1.0e-12);
		assertTrue(apHover.maxEscElectricalOutputDelta() <= 2.0e-6);
		assertTrue(apHover.maxTargetScaleErrorRatio() <= 0.012,
				() -> "target error=" + apHover.maxTargetScaleErrorRatio());
		assertTrue(apHover.maxTargetOvershootRatio() <= 0.006,
				() -> "target overshoot=" + apHover.maxTargetOvershootRatio());
		assertTrue(apHover.maxMotorOmegaAboveNeutralRatio() <= 1.0e-6,
				() -> "omega above neutral=" + apHover.maxMotorOmegaAboveNeutralRatio());
		assertTrue(apHover.motorResponseCouplingReviewed());

		assertEquals(6, audit.summary().rowCount());
		assertEquals(6, audit.summary().reviewedRowCount());
		assertEquals(2, audit.summary().presetCount());
		assertEquals(3, audit.summary().reviewCaseCount());
		assertEquals(2, audit.summary().neutralDefaultPresetCount());
		assertTrue(audit.summary().maxEscElectricalOutputDelta() <= 2.0e-6);
		assertTrue(audit.summary().maxTargetScaleErrorRatio() <= 0.012,
				() -> "max target error=" + audit.summary().maxTargetScaleErrorRatio());
		assertTrue(audit.summary().maxTargetOvershootRatio() <= 0.006,
				() -> "max target overshoot=" + audit.summary().maxTargetOvershootRatio());
		assertTrue(audit.summary().maxMotorOmegaAboveNeutralRatio() <= 1.0e-6,
				() -> "max omega above neutral=" + audit.summary().maxMotorOmegaAboveNeutralRatio());
		assertEquals(0.9715982698017723, audit.summary().minTargetMaxRpmScale(), 1.0e-12);
		assertEquals(5.599680211820246, audit.summary().maxEquivalentMaxThrustLossPercent(), 1.0e-12);
		assertEquals(0, audit.summary().configMutationAllowedCount());
		assertEquals(0, audit.summary().runtimeCouplingAllowedCount());
		assertEquals(0, audit.summary().playableReferenceAllowedCount());
		assertEquals(0, audit.summary().gameplayAutoApplyAllowedCount());
		assertTrue(audit.summary().motorResponseCouplingReviewed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview
						.reviewCase("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview
						.row("racingQuad", "missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview
				.DerateControlHookMotorResponseCouplingAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookMotorResponseCouplingReview
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_motor_response_coupling_review_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_motor_response_coupling_review,synthetic_derate_validation_all_pass,racingQuad,cold_sea_level_minus10c,first_frame_target_scale_closure,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_motor_response_coupling_review,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,hover_hold_tracking_response,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_motor_response_coupling_summary,all,,,,,reviewed_row_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_motor_response_coupling_summary,all,,,,,runtime_coupling_allowed_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_motor_response_coupling_method,all,,,,,method,target-omega-derate-motor-response-coupling-review,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_motor_response_coupling_review_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
