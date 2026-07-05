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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelopeTest {
	@Test
	void auditBuildsHoldVsReleaseTrajectoryEnvelope() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
				.StateNormalizedFreeFlightTrajectoryEnvelopeAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-State-Normalized-Free-Flight-Trajectory-Envelope-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("released APDrone punchout states"));
		assertEquals(47, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(5, audit.envelopeRowCount());
		assertEquals(33, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(0.04, audit.thrustMarginFailureThreshold(), 1.0e-12);
		assertEquals(0.75, audit.stateVelocityEnvelopeThresholdMetersPerSecond(), 1.0e-12);
		assertEquals(1.0e-12, audit.heldStateDeltaThreshold(), 1.0e-18);
		assertEquals(5, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
				.StateNormalizedFreeFlightTrajectoryEnvelopeRow blackbox =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
								.row(22.0);
		assertEquals("synthetic_derate_validation_all_pass", blackbox.scenarioName());
		assertEquals("apDrone", blackbox.presetName());
		assertEquals("cold_sea_level_minus10c", blackbox.ambientCaseName());
		assertEquals(14, blackbox.peakSampleIndex());
		assertEquals(0.075, blackbox.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.9910302351978076, blackbox.targetMaxRpmScale(), 1.0e-12);
		assertEquals(0.9821409270762219, blackbox.contractThrustRatio(), 1.0e-12);
		assertEquals(0.012099179305115717, blackbox.releaseThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.9700417477711062, blackbox.releaseObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9707209799261323, blackbox.heldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.0006792321550260771, blackbox.observedRecoveryRatio(), 1.0e-12);
		assertEquals(0.01323831079448079,
				blackbox.releaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.013687698563681794,
				blackbox.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(-0.033946005361073645, blackbox.residualReductionRatio(), 1.0e-12);
		assertEquals(0.04283390005570867,
				blackbox.releaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.001990762073396154, blackbox.releaseAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(0.04591377889097348,
				blackbox.releaseAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.0, blackbox.heldStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.11523638809238457,
				blackbox.releaseDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.021835120766790728,
				blackbox.heldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.014139620384393065, blackbox.releaseDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(0.0027909323802535746, blackbox.heldDeratedAdvanceRatioRange(), 1.0e-12);
		assertFalse(blackbox.releaseThrustMarginFailure());
		assertFalse(blackbox.releaseStateEnvelopeExceeded());
		assertTrue(blackbox.heldResidualEnvelopeCleared());
		assertTrue(blackbox.heldStateEnvelopeCleared());
		assertFalse(blackbox.freeFlightTrajectoryReleaseBlocked());
		assertFalse(blackbox.freeFlightTrajectoryReviewAllowed());
		assertFalse(blackbox.runtimeCouplingAllowed());
		assertFalse(blackbox.playableReferenceAllowed());
		assertFalse(blackbox.gameplayAutoApplyAllowed());
		assertEquals("trajectory-envelope-review-only", blackbox.envelopeBucket());
		assertEquals("TRAJECTORY_RELEASE_REVIEW", blackbox.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
				.StateNormalizedFreeFlightTrajectoryEnvelopeRow fast =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
								.row(28.0);
		assertEquals(2.2027601492399342, fast.releaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.06290864573533295, fast.releaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0, fast.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0, fast.residualReductionRatio(), 1.0e-12);
		assertTrue(fast.freeFlightTrajectoryReleaseBlocked());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
				.StateNormalizedFreeFlightTrajectoryEnvelopeSummary summary = audit.summary();
		assertEquals(5, summary.rowCount());
		assertEquals(4, summary.releaseThrustMarginFailureRowCount());
		assertEquals(3, summary.releaseStateEnvelopeExceededRowCount());
		assertEquals(5, summary.heldResidualEnvelopeClearedRowCount());
		assertEquals(5, summary.heldStateEnvelopeClearedRowCount());
		assertEquals(4, summary.freeFlightTrajectoryReleaseBlockedRowCount());
		assertEquals(0.35038721631721637, summary.maxReleaseThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.06290864573533295,
				summary.maxReleaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.013687698563681794,
				summary.maxHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(-0.033946005361073645, summary.minResidualReductionRatio(), 1.0e-12);
		assertEquals(0.3736390567296842, summary.maxObservedRecoveryRatio(), 1.0e-12);
		assertEquals(4.9855010127678385,
				summary.maxReleaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(5.57766181007588, summary.maxReleaseAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(23.25489617548909,
				summary.maxReleaseAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.3031595636397926,
				summary.maxReleaseDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.043745089415982874,
				summary.maxHeldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.04283390005570867,
				summary.blackboxSpeedReleaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.01323831079448079,
				summary.blackboxSpeedReleaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.013687698563681794,
				summary.blackboxSpeedHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(-0.033946005361073645,
				summary.blackboxSpeedResidualReductionRatio(), 1.0e-12);
		assertEquals(2.2027601492399342,
				summary.fastSpeedReleaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.06290864573533295,
				summary.fastSpeedReleaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0,
				summary.fastSpeedHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0, summary.fastSpeedResidualReductionRatio(), 1.0e-12);
		assertTrue(summary.currentFreeFlightBlackboxAcceptanceReady());
		assertFalse(summary.stateNormalizedLabAcceptanceReady());
		assertFalse(summary.freeFlightTrajectoryReviewAllowed());
		assertFalse(summary.runtimeCouplingAllowed());
		assertFalse(summary.playableReferenceAllowed());
		assertFalse(summary.gameplayAutoApplyAllowed());
		assertEquals("BLOCKED", summary.status());
		assertEquals("released-trajectory-state-divergence-with-held-residual-clearance",
				summary.dominantBucket());
		assertEquals(
				"inspect-free-flight-trajectory-envelope-blockers",
				summary.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
						.row(12.0));
	}

	@Test
	void residualAmplificationRatioUsesHeldResidualFloor() {
		assertEquals(7.7907728544618084,
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
						.residualAmplificationRatio(0.042638060481761775, 0.005472892263486129),
				1.0e-12);
		assertEquals(4.0e7,
				PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
						.residualAmplificationRatio(0.04, 0.0),
				1.0e-6);
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
				.StateNormalizedFreeFlightTrajectoryEnvelopeAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_free_flight_trajectory_envelope_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_free_flight_trajectory_envelope,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,22.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_free_flight_trajectory_envelope_summary,all,all,all,all,free_flight_trajectory_release_blocked_row_count,5,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_free_flight_trajectory_envelope_summary,all,all,all,all,runtime_coupling_allowed,false,bool,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_free_flight_trajectory_envelope_method,all,all,all,all,method,hold-vs-release-apdrone-trajectory-envelope,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_free_flight_trajectory_envelope_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
