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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReviewTest {
	@Test
	void auditCapturesCurrentBlackboxResultFailure() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
				.DerateControlHookBlackboxResultReviewAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Blackbox-Result-Review-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("explicit DronePhysics target-omega derate regressions"));
		assertEquals(34, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(2, audit.contractRowCount());
		assertEquals(4, audit.regressionCaseCount());
		assertEquals(8, audit.resultRowCount());
		assertEquals(18, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals("DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response",
				audit.controlBoundary());
		assertEquals(8, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
				.DerateControlHookBlackboxResultReviewRow racingHover =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
								.row("racingQuad", "hover_hold_target_omega_scale");
		assertEquals("synthetic_derate_validation_all_pass", racingHover.scenarioName());
		assertEquals("cold_sea_level_minus10c", racingHover.ambientCaseName());
		assertEquals("hover_hold", racingHover.flightPhase());
		assertEquals("target_omega_scale_closure", racingHover.targetMetric());
		assertEquals(240, racingHover.sampleCount());
		assertEquals(240, racingHover.minSampleCount());
		assertEquals(0.005, racingHover.dtSeconds(), 1.0e-12);
		assertEquals(0.004075367197358903, racingHover.primaryErrorRatio(), 1.0e-12);
		assertEquals(2.6409514646356123e-9, racingHover.secondaryErrorRatio(), 1.0e-18);
		assertEquals(0, racingHover.physicalConstraintViolationCount());
		assertTrue(racingHover.blackboxRegressionPassed());
		assertFalse(racingHover.runtimeCouplingAllowed());
		assertFalse(racingHover.playableReferenceAllowed());
		assertFalse(racingHover.gameplayAutoApplyAllowed());
		assertEquals("PASS", racingHover.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
				.DerateControlHookBlackboxResultReviewRow racingForward =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
								.row("racingQuad", "cold_air_forward_punchout_margin");
		assertEquals(0.0, racingForward.primaryErrorRatio(), 1.0e-12);
		assertEquals(0.020144608176940988, racingForward.secondaryErrorRatio(), 1.0e-12);
		assertTrue(racingForward.blackboxRegressionPassed());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
				.DerateControlHookBlackboxResultReviewRow apForward =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
								.row("apDrone", "cold_air_forward_punchout_margin");
		assertEquals("tip_mach_and_thrust_loss_margin", apForward.targetMetric());
		assertEquals(360, apForward.sampleCount());
		assertEquals(0.0, apForward.primaryErrorRatio(), 1.0e-12);
		assertEquals(0.1208445699538298, apForward.secondaryErrorRatio(), 1.0e-12);
		assertEquals(0.1208445699538298, apForward.maxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.009013488645329533, apForward.maxTargetOmegaOvershootRatio(), 1.0e-12);
		assertEquals(1, apForward.physicalConstraintViolationCount());
		assertFalse(apForward.blackboxRegressionPassed());
		assertEquals("FAIL", apForward.status());
		assertEquals("target-omega-blackbox-regression-result-failed", apForward.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
				.DerateControlHookBlackboxResultReviewRow apFailsafe =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
								.row("apDrone", "failsafe_disarm_no_overspeed");
		assertTrue(apFailsafe.failsafeActivated());
		assertTrue(apFailsafe.processedDisarmed());
		assertEquals(280, apFailsafe.sampleCount());
		assertEquals(0.0, apFailsafe.primaryErrorRatio(), 1.0e-12);
		assertEquals(0.0, apFailsafe.secondaryErrorRatio(), 1.0e-12);
		assertTrue(apFailsafe.blackboxRegressionPassed());

		assertEquals(8, audit.summary().rowCount());
		assertEquals(7, audit.summary().passedRowCount());
		assertEquals(2, audit.summary().presetCount());
		assertEquals(4, audit.summary().regressionCaseCount());
		assertEquals(2, audit.summary().failsafeActivatedRowCount());
		assertEquals(2, audit.summary().processedDisarmedRowCount());
		assertEquals(0.004075367197358903, audit.summary().maxPrimaryErrorRatio(), 1.0e-12);
		assertEquals(0.1208445699538298, audit.summary().maxSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.040898197245874734, audit.summary().maxTargetScaleErrorRatio(), 1.0e-12);
		assertEquals(0.009013488645329533, audit.summary().maxTargetOmegaOvershootRatio(), 1.0e-12);
		assertEquals(1.1575880155545448e-6, audit.summary().maxEscElectricalOutputDelta(), 1.0e-18);
		assertEquals(0.03449687718230223, audit.summary().maxMotorOmegaAboveNeutralRatio(), 1.0e-12);
		assertEquals(0.0, audit.summary().maxTipMachMarginViolationRatio(), 1.0e-12);
		assertEquals(0.1208445699538298, audit.summary().maxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.0, audit.summary().maxTargetOmegaAfterReleaseRatio(), 1.0e-12);
		assertEquals(0.0, audit.summary().maxMotorOmegaOverspeedRatio(), 1.0e-12);
		assertEquals(1, audit.summary().physicalConstraintViolationCount());
		assertFalse(audit.summary().blackboxRegressionPassed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
						.row("racingQuad", "missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
				.DerateControlHookBlackboxResultReviewAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxResultReview
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_result_review_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_result,synthetic_derate_validation_all_pass,racingQuad,cold_sea_level_minus10c,hover_hold_target_omega_scale,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_result,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,cold_air_forward_punchout_margin,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_result_summary,all,,,,,,passed_row_count,7,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_result_summary,all,,,,,,physical_constraint_violation_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_result_method,all,,,,,,method,target-omega-derate-blackbox-result-review,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_result_review_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
