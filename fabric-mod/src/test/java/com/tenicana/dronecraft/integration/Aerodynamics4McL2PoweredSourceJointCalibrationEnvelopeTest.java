package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceJointCalibrationEnvelopeTest {
	@Test
	void auditQuantifiesJointCalibrationBoundsAfterTorqueOnlyFailure() {
		Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.PoweredSourceJointCalibrationEnvelopeAudit audit =
				Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.audit();

		assertEquals("A4MC-L2-Powered-Source-Joint-Calibration-Envelope-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("axial, swirl, and motor-torque calibration bounds"));
		assertEquals(164, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(8, audit.presetStateSampleCount());
		assertEquals(18, audit.envelopeMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(8, audit.rows().size());

		Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.PoweredSourceJointCalibrationEnvelopeRow hoverRacing =
				find(audit.rows(), "hover", "racingQuad");
		assertEquals(1.0, hoverRacing.currentTorqueScale(), 1.0e-12);
		assertTrue(hoverRacing.equalWakePowerScaleForCurrentTorqueClosure() > 1.5);
		assertEquals(0.0, hoverRacing.requiredUniformWakePowerReductionFraction(), 1.0e-12);
		assertTrue(hoverRacing.maxAxialPowerScaleWithCurrentSwirlAtCurrentTorque() > 1.7);
		assertTrue(hoverRacing.maxSwirlPowerScaleWithCurrentAxialAtCurrentTorque() > 4.0);
		assertTrue(hoverRacing.axialOnlyReductionCanCloseAtCurrentTorque());
		assertTrue(hoverRacing.swirlOnlyReductionCanCloseAtCurrentTorque());
		assertFalse(hoverRacing.bothSingleChannelReductionsBlockedAtCurrentTorque());
		assertTargetOnlyAndBlocked(hoverRacing);

		Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.PoweredSourceJointCalibrationEnvelopeRow hoverCine =
				find(audit.rows(), "hover", "cinewhoop");
		assertTrue(hoverCine.equalWakePowerScaleForCurrentTorqueClosure() < 0.27);
		assertTrue(hoverCine.requiredUniformWakePowerReductionFraction() > 0.73);
		assertEquals(0.0, hoverCine.maxAxialPowerWithCurrentSwirlAtCurrentTorqueWatts(), 1.0e-12);
		assertEquals(0.0, hoverCine.maxSwirlPowerWithCurrentAxialAtCurrentTorqueWatts(), 1.0e-12);
		assertFalse(hoverCine.axialOnlyReductionCanCloseAtCurrentTorque());
		assertFalse(hoverCine.swirlOnlyReductionCanCloseAtCurrentTorque());
		assertTrue(hoverCine.bothSingleChannelReductionsBlockedAtCurrentTorque());
		assertTrue(hoverCine.peakTorqueScaleForExistingWake() < 0.4);
		assertTrue(hoverCine.maxAxialPowerAtPeakTorqueScaleWatts() > 10.0);
		assertTrue(hoverCine.maxAxialPowerScaleAtPeakTorqueScale() < 0.08);
		assertTrue(hoverCine.requiredAxialReductionAtPeakTorqueScaleFraction() > 0.92);
		assertJointCalibrationAndBlocked(hoverCine);

		Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.PoweredSourceJointCalibrationEnvelopeRow cruiseCine =
				find(audit.rows(), "cruise", "cinewhoop");
		assertEquals(hoverCine.equalWakePowerScaleForCurrentTorqueClosure(),
				cruiseCine.equalWakePowerScaleForCurrentTorqueClosure(), 1.0e-12);
		assertTrue(cruiseCine.maxAxialPowerAtPeakTorqueScaleWatts()
				> hoverCine.maxAxialPowerAtPeakTorqueScaleWatts());
		assertEquals(hoverCine.maxAxialPowerScaleAtPeakTorqueScale(),
				cruiseCine.maxAxialPowerScaleAtPeakTorqueScale(), 1.0e-12);
		assertJointCalibrationAndBlocked(cruiseCine);

		assertEquals(8, audit.extrema().rowCount());
		assertEquals(2, audit.extrema().jointWakeOrSourceCalibrationRequiredCount());
		assertEquals(6, audit.extrema().targetOnlyTelemetryCount());
		assertEquals(6, audit.extrema().axialOnlyReductionCanCloseCount());
		assertEquals(6, audit.extrema().swirlOnlyReductionCanCloseCount());
		assertEquals(2, audit.extrema().bothSingleChannelReductionsBlockedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertTrue(audit.extrema().minEqualWakePowerScaleForCurrentTorqueClosure() < 0.27);
		assertTrue(audit.extrema().maxRequiredUniformWakePowerReductionFraction() > 0.73);
		assertTrue(audit.extrema().minMaxAxialPowerScaleAtPeakTorqueScale() < 0.08);
		assertTrue(audit.extrema().maxRequiredAxialReductionAtPeakTorqueScaleFraction() > 0.92);
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.PoweredSourceJointCalibrationEnvelopeAudit audit =
				Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_source_joint_calibration_envelope_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_joint_calibration_envelope_summary,all_rows,both_single_channel_reductions_blocked_count,2,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_joint_calibration_envelope,hover:cinewhoop,equal_wake_power_scale_for_current_torque_closure,0.25922430237596134,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_joint_calibration_envelope,cruise:cinewhoop,max_axial_power_scale_at_peak_torque_scale,0.07512788262201885,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_joint_calibration_envelope,cruise:heavyLift,swirl_only_reduction_can_close_at_current_torque,true,")));
	}

	private static void assertTargetOnlyAndBlocked(
			Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.PoweredSourceJointCalibrationEnvelopeRow row
	) {
		assertFalse(row.jointWakeOrSourceCalibrationRequired());
		assertTrue(row.liveMotorTorqueCurveRequired());
		assertTrue(row.liveWakePowerCalibrationRequired());
		assertTrue(row.liveSwirlProbeRequired());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("capture-live-motor-torque-curve-and-powered-wake-power-telemetry",
				row.nextRequiredAction());
		assertEquals("TARGET_ONLY_TELEMETRY_REQUIRED", row.status());
		assertEquals("joint-calibration-envelope-open-but-live-evidence-missing", row.message());
	}

	private static void assertJointCalibrationAndBlocked(
			Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.PoweredSourceJointCalibrationEnvelopeRow row
	) {
		assertTrue(row.jointWakeOrSourceCalibrationRequired());
		assertTrue(row.liveMotorTorqueCurveRequired());
		assertTrue(row.liveWakePowerCalibrationRequired());
		assertTrue(row.liveSwirlProbeRequired());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("capture-cinewhoop-source-map-wake-footprint-and-motor-torque-calibration",
				row.nextRequiredAction());
		assertEquals("JOINT_CALIBRATION_REQUIRED", row.status());
		assertEquals("single-channel-powered-source-calibration-cannot-close-current-wake-demand",
				row.message());
	}

	private static Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.PoweredSourceJointCalibrationEnvelopeRow
	find(
			List<Aerodynamics4McL2PoweredSourceJointCalibrationEnvelope.PoweredSourceJointCalibrationEnvelopeRow>
					rows,
			String spinState,
			String presetName
	) {
		return rows.stream()
				.filter(row -> spinState.equals(row.spinState()) && presetName.equals(row.presetName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_joint_calibration_envelope_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
