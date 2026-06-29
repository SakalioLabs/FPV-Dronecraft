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
		assertEquals(255, staticRow.peakSampleIndex());
		assertEquals(1.28, staticRow.peakTimeSeconds(), 1.0e-12);
		assertEquals("settled_after_900ms", staticRow.peakWindow());
		assertEquals(38.30018462290361, staticRow.peakObservedThrustLossPercent(), 1.0e-12);
		assertEquals(0.365142773305258, staticRow.peakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.9708059398404551, staticRow.peakAverageTargetOmegaRatio(), 1.0e-12);
		assertEquals(0.957727413243542, staticRow.peakAverageMotorOmegaRatio(), 1.0e-12);
		assertEquals(0.0, staticRow.earlyWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.365142773305258, staticRow.settledWindowMaxThrustLossOverContractRatio(), 1.0e-12);
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
		assertEquals(345, blackboxSpeed.peakSampleIndex());
		assertEquals(1.73, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(13.87036428776079, blackboxSpeed.peakObservedThrustLossPercent(), 1.0e-12);
		assertEquals(0.1208445699538298,
				blackboxSpeed.peakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.9943289815249627, blackboxSpeed.peakAverageTargetOmegaRatio(), 1.0e-12);
		assertEquals(0.9967368021843248, blackboxSpeed.peakAverageMotorOmegaRatio(), 1.0e-12);
		assertEquals(0.011979825181119456, blackboxSpeed.peakTargetScaleErrorRatio(), 1.0e-12);
		assertEquals(0.010046653840002547, blackboxSpeed.peakMotorOmegaAboveNeutralRatio(), 1.0e-12);
		assertEquals(0.007466499320681051,
				blackboxSpeed.earlyWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.006868185871988352,
				blackboxSpeed.midWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.006384179795763525,
				blackboxSpeed.lateWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.1208445699538298,
				blackboxSpeed.settledWindowMaxThrustLossOverContractRatio(), 1.0e-12);

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
				.ForwardPunchoutPeakTimingDiagnosticRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookForwardPunchoutPeakTimingDiagnostic
								.row(28.0);
		assertEquals(277, fastRow.peakSampleIndex());
		assertEquals(1.39, fastRow.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.23146597254194, fastRow.peakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(1.0189384289795356, fastRow.peakAverageTargetOmegaRatio(), 1.0e-12);
		assertEquals(1.0327557518121044, fastRow.peakAverageMotorOmegaRatio(), 1.0e-12);
		assertEquals(0.027694826581310346,
				fastRow.midWindowMaxThrustLossOverContractRatio(), 1.0e-12);
		assertTrue(fastRow.peakAverageTargetOmegaRatio() > 1.0);
		assertTrue(fastRow.peakAverageMotorOmegaRatio() > 1.0);

		assertEquals(5, audit.summary().rowCount());
		assertEquals(5, audit.summary().failedRowCount());
		assertEquals(5, audit.summary().settledPeakRowCount());
		assertEquals(0, audit.summary().earlyWindowFailureRowCount());
		assertEquals(5, audit.summary().settledWindowFailureRowCount());
		assertEquals(1, audit.summary().deratedTargetAboveNeutralPeakRowCount());
		assertEquals(1, audit.summary().deratedMotorAboveNeutralPeakRowCount());
		assertEquals(1.06, audit.summary().minPeakTimeSeconds(), 1.0e-12);
		assertEquals(1.73, audit.summary().maxPeakTimeSeconds(), 1.0e-12);
		assertEquals(0.365142773305258, audit.summary().maxPeakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.059948790116623255,
				audit.summary().minPeakThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.015472804809058016,
				audit.summary().maxEarlyWindowThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.027694826581310346,
				audit.summary().maxMidWindowThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.006384179795763525,
				audit.summary().maxLateWindowThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.365142773305258,
				audit.summary().maxSettledWindowThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(1.0189384289795356, audit.summary().maxPeakAverageTargetOmegaRatio(), 1.0e-12);
		assertEquals(1.0327557518121044, audit.summary().maxPeakAverageMotorOmegaRatio(), 1.0e-12);
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
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_forward_punchout_peak_timing_diagnostic_summary,all,,,,failed_row_count,5,count,")));
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
