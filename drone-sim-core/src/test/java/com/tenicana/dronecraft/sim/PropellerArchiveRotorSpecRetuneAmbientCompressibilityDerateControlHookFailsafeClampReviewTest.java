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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReviewTest {
	@Test
	void auditReviewsFailsafeClampAndNoLoadRelease() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
				.DerateControlHookFailsafeClampAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Failsafe-Clamp-Review-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("link-loss failsafe"));
		assertEquals(31, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(2, audit.contractRowCount());
		assertEquals(3, audit.reviewCaseCount());
		assertEquals(6, audit.reviewRowCount());
		assertEquals(18, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(
				"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response",
				audit.controlBoundary());
		assertEquals(3, audit.reviewCases().size());
		assertEquals(6, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
				.ReviewCaseDefinition linkLoss =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
								.reviewCase("link_loss_failsafe_target_zero");
		assertEquals("failsafe_disarm", linkLoss.flightPhase());
		assertEquals(120, linkLoss.spoolSampleCount());
		assertEquals(120, linkLoss.releaseSampleCount());
		assertEquals(0.005, linkLoss.dtSeconds(), 1.0e-12);
		assertTrue(linkLoss.requiresFailsafeActivation());
		assertEquals(1.0e-12, linkLoss.maxAllowedTargetOmegaAfterReleaseRatio(), 1.0e-18);
		assertEquals(0.020, linkLoss.maxAllowedDeratedOmegaAboveNeutralMaxOmegaRatio(), 1.0e-12);

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
				.DerateControlHookFailsafeClampReviewRow racingDirect =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
								.row("racingQuad", "direct_disarm_target_zero");
		assertEquals("synthetic_derate_validation_all_pass", racingDirect.scenarioName());
		assertEquals("cold_sea_level_minus10c", racingDirect.ambientCaseName());
		assertEquals(0.9715982698017723, racingDirect.targetMaxRpmScale(), 1.0e-12);
		assertEquals(28310.07356552835, racingDirect.contractedMaxRpm(), 1.0e-12);
		assertFalse(racingDirect.failsafeActivated());
		assertTrue(racingDirect.processedDisarmed());
		assertEquals(0.0, racingDirect.maxTargetOmegaAfterReleaseRatio(), 1.0e-12);
		assertEquals(0.0, racingDirect.maxEscElectricalOutputAfterRelease(), 1.0e-12);
		assertEquals(0.0, racingDirect.maxMotorOmegaOverspeedRatio(), 1.0e-12);
		assertEquals(0.0, racingDirect.maxMotorOmegaRiseAfterReleaseRatio(), 1.0e-12);
		assertEquals(0.0, racingDirect.maxDeratedOmegaAboveNeutralMaxOmegaRatio(), 1.0e-12);
		assertTrue(racingDirect.failsafeClampReviewed());
		assertFalse(racingDirect.configMutationAllowed());
		assertFalse(racingDirect.runtimeCouplingAllowed());
		assertFalse(racingDirect.playableReferenceAllowed());
		assertFalse(racingDirect.gameplayAutoApplyAllowed());
		assertEquals("REVIEWED", racingDirect.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
				.DerateControlHookFailsafeClampReviewRow apLinkLoss =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
								.row("apDrone", "link_loss_failsafe_target_zero");
		assertTrue(apLinkLoss.failsafeActivated());
		assertTrue(apLinkLoss.processedDisarmed());
		assertEquals(0.0, apLinkLoss.maxTargetOmegaAfterReleaseRatio(), 1.0e-12);
		assertEquals(0.0, apLinkLoss.maxEscElectricalOutputAfterRelease(), 1.0e-12);
		assertEquals(0.0, apLinkLoss.maxMotorOmegaOverspeedRatio(), 1.0e-12);
		assertEquals(0.0, apLinkLoss.maxMotorOmegaRiseAfterReleaseRatio(), 1.0e-12);
		assertTrue(apLinkLoss.maxDeratedOmegaAboveNeutralMaxOmegaRatio() <= 0.020,
				() -> "above neutral=" + apLinkLoss.maxDeratedOmegaAboveNeutralMaxOmegaRatio());
		assertTrue(apLinkLoss.failsafeClampReviewed());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
				.DerateControlHookFailsafeClampReviewRow apHigh =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
								.row("apDrone", "high_rpm_no_load_disarm_release");
		assertFalse(apHigh.failsafeActivated());
		assertTrue(apHigh.processedDisarmed());
		assertEquals(0.0, apHigh.maxMotorOmegaOverspeedRatio(), 1.0e-12);
		assertEquals(0.0, apHigh.maxMotorOmegaRiseAfterReleaseRatio(), 1.0e-12);
		assertTrue(apHigh.maxDeratedOmegaAboveNeutralMaxOmegaRatio() > 0.010);
		assertTrue(apHigh.maxDeratedOmegaAboveNeutralMaxOmegaRatio() <= 0.020);
		assertTrue(apHigh.failsafeClampReviewed());

		assertEquals(6, audit.summary().rowCount());
		assertEquals(6, audit.summary().reviewedRowCount());
		assertEquals(2, audit.summary().presetCount());
		assertEquals(3, audit.summary().reviewCaseCount());
		assertEquals(2, audit.summary().failsafeActivationRowCount());
		assertEquals(6, audit.summary().disarmedReleaseRowCount());
		assertEquals(0.0, audit.summary().maxTargetOmegaAfterReleaseRatio(), 1.0e-12);
		assertEquals(0.0, audit.summary().maxEscElectricalOutputAfterRelease(), 1.0e-12);
		assertEquals(0.0, audit.summary().maxMotorOmegaOverspeedRatio(), 1.0e-12);
		assertEquals(0.0, audit.summary().maxMotorOmegaRiseAfterReleaseRatio(), 1.0e-12);
		assertTrue(audit.summary().maxDeratedOmegaAboveNeutralMaxOmegaRatio() <= 0.020,
				() -> "max above neutral=" + audit.summary().maxDeratedOmegaAboveNeutralMaxOmegaRatio());
		assertEquals(0.9715982698017723, audit.summary().minTargetMaxRpmScale(), 1.0e-12);
		assertEquals(5.599680211820246, audit.summary().maxEquivalentMaxThrustLossPercent(), 1.0e-12);
		assertEquals(0, audit.summary().configMutationAllowedCount());
		assertEquals(0, audit.summary().runtimeCouplingAllowedCount());
		assertEquals(0, audit.summary().playableReferenceAllowedCount());
		assertEquals(0, audit.summary().gameplayAutoApplyAllowedCount());
		assertTrue(audit.summary().failsafeClampReviewed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
						.reviewCase("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
						.row("racingQuad", "missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
				.DerateControlHookFailsafeClampAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookFailsafeClampReview
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_failsafe_clamp_review_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_failsafe_clamp_review,synthetic_derate_validation_all_pass,racingQuad,cold_sea_level_minus10c,direct_disarm_target_zero,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_failsafe_clamp_review,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,link_loss_failsafe_target_zero,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_failsafe_clamp_summary,all,,,,,reviewed_row_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_failsafe_clamp_summary,all,,,,,runtime_coupling_allowed_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_failsafe_clamp_method,all,,,,,method,target-omega-derate-failsafe-clamp-review,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_failsafe_clamp_review_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
