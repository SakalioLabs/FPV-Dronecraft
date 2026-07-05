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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnosticTest {
	@Test
	void auditShowsApDronePunchoutFailuresPeakAfterSettlingWindow() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
				.ForwardPunchoutPeakTimingDiagnosticAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Forward-Punchout-Peak-Timing-Diagnostic-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("peak sample and time window"));
		assertEquals(32, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(5, audit.speedSampleCount());
		assertEquals(5, audit.peakRowCount());
		assertEquals(20, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals("DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response",
				audit.controlBoundary());
		assertEquals(5, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
				.ForwardPunchoutPeakTimingDiagnosticRow staticRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
								.row(0.0);
		assertEquals("apDrone", staticRow.presetName());
		assertEquals("cold_sea_level_minus10c", staticRow.ambientCaseName());
		assertEquals(266, staticRow.peakSampleIndex());
		assertEquals(1.335, staticRow.peakTimeSeconds(), 1.0e-12);
		assertEquals("settled_after_900ms", staticRow.peakWindow());
		assertEquals(36.82462892409944, staticRow.peakObservedThrustLossPercent(), 1.0e-12);
		assertEquals(0.35038721631721637, staticRow.peakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(1.0456794988990297, staticRow.peakAverageTargetOmegaRatio(), 1.0e-12);
		assertEquals(1.07520610453705, staticRow.peakAverageMotorOmegaRatio(), 1.0e-12);
		assertEquals(0.0, staticRow.earlyWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.35038721631721637, staticRow.settledWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertTrue(staticRow.peakInSettledWindow());
		assertFalse(staticRow.thrustMarginPassed());
		assertFalse(staticRow.runtimeCouplingAllowed());
		assertFalse(staticRow.playableReferenceAllowed());
		assertFalse(staticRow.gameplayAutoApplyAllowed());
		assertEquals("FAIL", staticRow.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
				.ForwardPunchoutPeakTimingDiagnosticRow blackboxSpeed =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
								.row(22.0);
		assertEquals(14, blackboxSpeed.peakSampleIndex());
		assertEquals(0.075, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(2.9958252228893802, blackboxSpeed.peakObservedThrustLossPercent(), 1.0e-12);
		assertEquals(0.012099179305115717,
				blackboxSpeed.peakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.9947757583232686, blackboxSpeed.peakAverageTargetOmegaRatio(), 1.0e-12);
		assertEquals(0.9942545470136717, blackboxSpeed.peakAverageMotorOmegaRatio(), 1.0e-12);
		assertEquals(0.003763961917141123, blackboxSpeed.peakTargetScaleErrorRatio(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.peakMotorOmegaAboveNeutralRatio(), 1.0e-12);
		assertEquals(0.012099179305115717,
				blackboxSpeed.earlyWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.0,
				blackboxSpeed.midWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.0,
				blackboxSpeed.lateWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.0,
				blackboxSpeed.settledWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertFalse(blackboxSpeed.peakInSettledWindow());
		assertTrue(blackboxSpeed.thrustMarginPassed());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
				.ForwardPunchoutPeakTimingDiagnosticRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
								.row(28.0);
		assertEquals(301, fastRow.peakSampleIndex());
		assertEquals(1.51, fastRow.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.24179877596913907, fastRow.peakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(1.0196452948992651, fastRow.peakAverageTargetOmegaRatio(), 1.0e-12);
		assertEquals(1.0344211978422269, fastRow.peakAverageMotorOmegaRatio(), 1.0e-12);
		assertEquals(0.033590722746980044,
				fastRow.midWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertTrue(fastRow.peakAverageTargetOmegaRatio() > 1.0);
		assertTrue(fastRow.peakAverageMotorOmegaRatio() > 1.0);

		assertEquals(5, audit.summary().rowCount());
		assertEquals(4, audit.summary().failedRowCount());
		assertEquals(4, audit.summary().settledPeakRowCount());
		assertEquals(0, audit.summary().earlyWindowFailureRowCount());
		assertEquals(4, audit.summary().settledWindowFailureRowCount());
		assertEquals(3, audit.summary().deratedTargetAboveNeutralPeakRowCount());
		assertEquals(3, audit.summary().deratedMotorAboveNeutralPeakRowCount());
		assertEquals(0.075, audit.summary().minPeakTimeSeconds(), 1.0e-12);
		assertEquals(1.51, audit.summary().maxPeakTimeSeconds(), 1.0e-12);
		assertEquals(0.35038721631721637, audit.summary().maxPeakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.012099179305115717,
				audit.summary().minPeakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.01596141960638188,
				audit.summary().maxEarlyWindowThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.033590722746980044,
				audit.summary().maxMidWindowThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.017076924291965515,
				audit.summary().maxLateWindowThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.35038721631721637,
				audit.summary().maxSettledWindowThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(1.0456794988990297, audit.summary().maxPeakAverageTargetOmegaRatio(), 1.0e-12);
		assertEquals(1.07520610453705, audit.summary().maxPeakAverageMotorOmegaRatio(), 1.0e-12);
		assertEquals("settled_after_900ms", audit.summary().dominantPeakWindow());
		assertEquals("investigate-apDrone-settled-full-throttle-thrust-model-before-runtime-coupling",
				audit.summary().nextRequiredAction());
		assertFalse(audit.summary().peakTimingDiagnosticPassed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
						.row(12.0));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
				.ForwardPunchoutPeakTimingDiagnosticAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_peak_timing_diagnostic_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_peak_timing_diagnostic,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,22.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_peak_timing_diagnostic_summary,all,,,,failed_row_count,4,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_peak_timing_diagnostic_summary,all,,,,dominant_peak_window,settled_after_900ms,text,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_peak_timing_diagnostic_method,all,,,,method,apDrone-full-throttle-forward-punchout-peak-timing,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_peak_timing_diagnostic_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
