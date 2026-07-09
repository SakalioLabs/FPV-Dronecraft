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
		assertEquals(234, staticRow.peakSampleIndex());
		assertEquals(1.175, staticRow.peakTimeSeconds(), 1.0e-12);
		assertEquals("settled_after_900ms", staticRow.peakWindow());
		assertEquals(49.25013391822248, staticRow.peakObservedThrustLossPercent(), 1.0e-12);
		assertEquals(0.47464226625844674, staticRow.peakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(1.0520384567061167, staticRow.peakAverageTargetOmegaRatio(), 1.0e-12);
		assertEquals(1.0925970720246028, staticRow.peakAverageMotorOmegaRatio(), 1.0e-12);
		assertEquals(0.0, staticRow.earlyWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.47464226625844674, staticRow.settledWindowMaxThrustLossOverContractRatio(), 1.0e-12);
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
		assertEquals(353, blackboxSpeed.peakSampleIndex());
		assertEquals(1.77, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(21.397476807663228, blackboxSpeed.peakObservedThrustLossPercent(), 1.0e-12);
		assertEquals(0.1961156951528542,
				blackboxSpeed.peakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.9752093559974072, blackboxSpeed.peakAverageTargetOmegaRatio(), 1.0e-12);
		assertEquals(0.9639301361410095, blackboxSpeed.peakAverageMotorOmegaRatio(), 1.0e-12);
		assertEquals(0.028979069814177638, blackboxSpeed.peakTargetScaleErrorRatio(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.peakMotorOmegaAboveNeutralRatio(), 1.0e-12);
		assertEquals(0.007474447303139012,
				blackboxSpeed.earlyWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.007184564915970953,
				blackboxSpeed.midWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.004534821076632709,
				blackboxSpeed.lateWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.1961156951528542,
				blackboxSpeed.settledWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertTrue(blackboxSpeed.peakInSettledWindow());
		assertFalse(blackboxSpeed.thrustMarginPassed());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
				.ForwardPunchoutPeakTimingDiagnosticRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
								.row(28.0);
		assertEquals(359, fastRow.peakSampleIndex());
		assertEquals(1.8, fastRow.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.12037830926456211, fastRow.peakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(1.0219729159929059, fastRow.peakAverageTargetOmegaRatio(), 1.0e-12);
		assertEquals(1.0254969204873257, fastRow.peakAverageMotorOmegaRatio(), 1.0e-12);
		assertEquals(0.027941815269727565,
				fastRow.midWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertTrue(fastRow.peakAverageTargetOmegaRatio() > 1.0);
		assertTrue(fastRow.peakAverageMotorOmegaRatio() > 1.0);

		assertEquals(5, audit.summary().rowCount());
		assertEquals(5, audit.summary().failedRowCount());
		assertEquals(5, audit.summary().settledPeakRowCount());
		assertEquals(0, audit.summary().earlyWindowFailureRowCount());
		assertEquals(5, audit.summary().settledWindowFailureRowCount());
		assertEquals(2, audit.summary().deratedTargetAboveNeutralPeakRowCount());
		assertEquals(2, audit.summary().deratedMotorAboveNeutralPeakRowCount());
		assertEquals(1.07, audit.summary().minPeakTimeSeconds(), 1.0e-12);
		assertEquals(1.8, audit.summary().maxPeakTimeSeconds(), 1.0e-12);
		assertEquals(0.47464226625844674, audit.summary().maxPeakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.04818344060840724,
				audit.summary().minPeakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.015476235886279666,
				audit.summary().maxEarlyWindowThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.027941815269727565,
				audit.summary().maxMidWindowThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.004534821076632709,
				audit.summary().maxLateWindowThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.47464226625844674,
				audit.summary().maxSettledWindowThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(1.0520384567061167, audit.summary().maxPeakAverageTargetOmegaRatio(), 1.0e-12);
		assertEquals(1.0925970720246028, audit.summary().maxPeakAverageMotorOmegaRatio(), 1.0e-12);
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
