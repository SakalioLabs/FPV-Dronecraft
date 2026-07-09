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
		assertEquals(353, blackbox.peakSampleIndex());
		assertEquals(1.77, blackbox.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.9910302351978076, blackbox.targetMaxRpmScale(), 1.0e-12);
		assertEquals(0.9821409270762219, blackbox.contractThrustRatio(), 1.0e-12);
		assertEquals(0.1961156951528542, blackbox.releaseThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.7860252319233677, blackbox.releaseObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9948737365881243, blackbox.heldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.20884850466475657, blackbox.observedRecoveryRatio(), 1.0e-12);
		assertEquals(0.051824729389994606,
				blackbox.releaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0,
				blackbox.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0, blackbox.residualReductionRatio(), 1.0e-12);
		assertEquals(0.6704653270613072,
				blackbox.releaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.5463830537769551, blackbox.releaseAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(2.5975089859340508,
				blackbox.releaseAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.0, blackbox.heldStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.18745657847021202,
				blackbox.releaseDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.06211431664460698,
				blackbox.heldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.02491052456241008, blackbox.releaseDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(0.00792580066897168, blackbox.heldDeratedAdvanceRatioRange(), 1.0e-12);
		assertTrue(blackbox.releaseThrustMarginFailure());
		assertFalse(blackbox.releaseStateEnvelopeExceeded());
		assertTrue(blackbox.heldResidualEnvelopeCleared());
		assertTrue(blackbox.heldStateEnvelopeCleared());
		assertTrue(blackbox.freeFlightTrajectoryReleaseBlocked());
		assertTrue(blackbox.freeFlightTrajectoryReviewAllowed());
		assertFalse(blackbox.runtimeCouplingAllowed());
		assertFalse(blackbox.playableReferenceAllowed());
		assertFalse(blackbox.gameplayAutoApplyAllowed());
		assertEquals("released-thrust-margin-failure-held-local-clearance", blackbox.envelopeBucket());
		assertEquals("TRAJECTORY_RELEASE_BLOCKED", blackbox.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
				.StateNormalizedFreeFlightTrajectoryEnvelopeRow fast =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
								.row(28.0);
		assertEquals(1.3485566501916006, fast.releaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, fast.releaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0, fast.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0, fast.residualReductionRatio(), 1.0e-12);
		assertTrue(fast.freeFlightTrajectoryReleaseBlocked());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
				.StateNormalizedFreeFlightTrajectoryEnvelopeSummary summary = audit.summary();
		assertEquals(5, summary.rowCount());
		assertEquals(5, summary.releaseThrustMarginFailureRowCount());
		assertEquals(3, summary.releaseStateEnvelopeExceededRowCount());
		assertEquals(5, summary.heldResidualEnvelopeClearedRowCount());
		assertEquals(5, summary.heldStateEnvelopeClearedRowCount());
		assertEquals(5, summary.freeFlightTrajectoryReleaseBlockedRowCount());
		assertEquals(0.47464226625844674, summary.maxReleaseThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.06686369268551773,
				summary.maxReleaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0014941766843524773,
				summary.maxHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0, summary.minResidualReductionRatio(), 1.0e-12);
		assertEquals(0.49631492201439165, summary.maxObservedRecoveryRatio(), 1.0e-12);
		assertEquals(2.9892244211484305,
				summary.maxReleaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(5.914021977804991, summary.maxReleaseAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(4.711665364071266,
				summary.maxReleaseAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.3969134902024324,
				summary.maxReleaseDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.06211431664460698,
				summary.maxHeldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.6704653270613072,
				summary.blackboxSpeedReleaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.051824729389994606,
				summary.blackboxSpeedReleaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0,
				summary.blackboxSpeedHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0,
				summary.blackboxSpeedResidualReductionRatio(), 1.0e-12);
		assertEquals(1.3485566501916006,
				summary.fastSpeedReleaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.0,
				summary.fastSpeedReleaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0,
				summary.fastSpeedHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0, summary.fastSpeedResidualReductionRatio(), 1.0e-12);
		assertFalse(summary.currentFreeFlightBlackboxAcceptanceReady());
		assertTrue(summary.stateNormalizedLabAcceptanceReady());
		assertTrue(summary.freeFlightTrajectoryReviewAllowed());
		assertFalse(summary.runtimeCouplingAllowed());
		assertFalse(summary.playableReferenceAllowed());
		assertFalse(summary.gameplayAutoApplyAllowed());
		assertEquals("TRAJECTORY_ENVELOPE_READY", summary.status());
		assertEquals("released-trajectory-state-divergence-with-held-residual-clearance",
				summary.dominantBucket());
		assertEquals(
				"fit-free-flight-trajectory-release-damping-or-state-coupling-before-runtime-review",
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
