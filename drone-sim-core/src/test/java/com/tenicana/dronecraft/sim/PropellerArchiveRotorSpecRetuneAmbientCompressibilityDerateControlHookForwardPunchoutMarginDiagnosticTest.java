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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnosticTest {
	@Test
	void auditDiagnosesApDroneFullThrottleForwardPunchoutMarginFailure() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic
				.ForwardPunchoutMarginDiagnosticAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Forward-Punchout-Margin-Diagnostic-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("APDrone blackbox result failure"));
		assertEquals(37, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(2, audit.presetCount());
		assertEquals(5, audit.speedSampleCount());
		assertEquals(10, audit.diagnosticRowCount());
		assertEquals(20, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals("DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response",
				audit.controlBoundary());
		assertEquals(10, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic
				.ForwardPunchoutMarginDiagnosticRow racingBlackboxSpeed =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic
								.row("racingQuad", 22.0);
		assertEquals("cold_sea_level_minus10c", racingBlackboxSpeed.ambientCaseName());
		assertEquals(1.0, racingBlackboxSpeed.throttleCommand(), 1.0e-12);
		assertEquals(360, racingBlackboxSpeed.sampleCount());
		assertEquals(0.005, racingBlackboxSpeed.dtSeconds(), 1.0e-12);
		assertEquals(7.614315453781521, racingBlackboxSpeed.observedThrustLossPercent(), 1.0e-12);
		assertEquals(0.02014635241961253,
				racingBlackboxSpeed.thrustLossOverContractRatio(), 1.0e-12);
		assertTrue(racingBlackboxSpeed.thrustMarginPassed());
		assertEquals("PASS", racingBlackboxSpeed.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic
				.ForwardPunchoutMarginDiagnosticRow apStatic =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic
								.row("apDrone", 0.0);
		assertEquals(49.25013391822248, apStatic.observedThrustLossPercent(), 1.0e-12);
		assertEquals(0.47464226625844674, apStatic.thrustLossOverContractRatio(), 1.0e-12);
		assertFalse(apStatic.thrustMarginPassed());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic
				.ForwardPunchoutMarginDiagnosticRow apEight =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic
								.row("apDrone", 8.0);
		assertEquals(0.04818344060840724, apEight.thrustLossOverContractRatio(), 1.0e-12);
		assertFalse(apEight.thrustMarginPassed());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic
				.ForwardPunchoutMarginDiagnosticRow apBlackboxSpeed =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic
								.row("apDrone", 22.0);
		assertEquals(0.9910302351978076, apBlackboxSpeed.targetMaxRpmScale(), 1.0e-12);
		assertEquals(1.7859072923778085, apBlackboxSpeed.equivalentMaxThrustLossPercent(), 1.0e-12);
		assertEquals(21.397476807663228, apBlackboxSpeed.observedThrustLossPercent(), 1.0e-12);
		assertEquals(0.1961156951528542, apBlackboxSpeed.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.04, apBlackboxSpeed.maxAllowedThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.363690937609333, apBlackboxSpeed.maxRotorAdvanceRatio(), 1.0e-12);
		assertEquals(0.5688188955364236, apBlackboxSpeed.maxRotorTipMach(), 1.0e-12);
		assertEquals(9.284276111928687, apBlackboxSpeed.neutralThrustNewtonsAtMaxMargin(), 1.0e-12);
		assertEquals(7.297675284119329, apBlackboxSpeed.deratedThrustNewtonsAtMaxMargin(), 1.0e-12);
		assertFalse(apBlackboxSpeed.thrustMarginPassed());
		assertEquals("FAIL", apBlackboxSpeed.status());
		assertEquals("forward-punchout-derate-margin-exceeds-contract", apBlackboxSpeed.message());
		assertFalse(apBlackboxSpeed.runtimeCouplingAllowed());
		assertFalse(apBlackboxSpeed.playableReferenceAllowed());
		assertFalse(apBlackboxSpeed.gameplayAutoApplyAllowed());

		assertEquals(10, audit.summary().rowCount());
		assertEquals(5, audit.summary().passedRowCount());
		assertEquals(5, audit.summary().failedRowCount());
		assertEquals(5, audit.summary().apDroneFailedRowCount());
		assertEquals(0, audit.summary().racingQuadFailedRowCount());
		assertEquals(49.25013391822248, audit.summary().maxObservedThrustLossPercent(), 1.0e-12);
		assertEquals(0.47464226625844674, audit.summary().maxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.04818344060840724,
				audit.summary().minFailingThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.1961156951528542,
				audit.summary().blackboxSpeedApDroneThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.02014635241961253,
				audit.summary().blackboxSpeedRacingQuadThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.08135095812641524, audit.summary().maxTargetScaleErrorRatio(), 1.0e-12);
		assertEquals(0.009550913074549803, audit.summary().maxTargetOmegaOvershootRatio(), 1.0e-12);
		assertEquals(0.1604219790768001, audit.summary().maxMotorOmegaAboveNeutralRatio(), 1.0e-12);
		assertEquals(0.6224189932941847, audit.summary().maxRotorAdvanceRatio(), 1.0e-12);
		assertEquals(0.5753590539454095, audit.summary().maxRotorTipMach(), 1.0e-12);
		assertEquals("apDrone", audit.summary().dominantFailurePreset());
		assertEquals("investigate-apDrone-full-throttle-derate-transient-thrust-loss-before-runtime-coupling",
				audit.summary().nextRequiredAction());
		assertFalse(audit.summary().marginDiagnosticPassed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic
						.row("racingQuad", 12.0));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic
				.ForwardPunchoutMarginDiagnosticAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutMarginDiagnostic
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_margin_diagnostic_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_margin_diagnostic,synthetic_derate_validation_all_pass,racingQuad,cold_sea_level_minus10c,22.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_margin_diagnostic,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,22.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_margin_diagnostic_summary,all,,,,failed_row_count,5,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_margin_diagnostic_summary,all,,,,next_required_action,investigate-apDrone-full-throttle-derate-transient-thrust-loss-before-runtime-coupling,text,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_margin_diagnostic_method,all,,,,method,full-throttle-forward-punchout-margin-speed-sweep,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_margin_diagnostic_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
