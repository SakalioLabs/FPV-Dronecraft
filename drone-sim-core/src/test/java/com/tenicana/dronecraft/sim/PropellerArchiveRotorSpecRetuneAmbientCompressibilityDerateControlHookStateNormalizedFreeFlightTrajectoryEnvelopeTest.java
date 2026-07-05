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
		assertEquals(46, blackbox.peakSampleIndex());
		assertEquals(0.23500000000000001, blackbox.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.9910302351978076, blackbox.targetMaxRpmScale(), 1.0e-12);
		assertEquals(0.9821409270762219, blackbox.contractThrustRatio(), 1.0e-12);
		assertEquals(0.009642732412038528, blackbox.releaseThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.9724981946641834, blackbox.releaseObservedThrustRatio(), 1.0e-12);
		assertEquals(1.0010975905065054, blackbox.heldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.028599395842321962, blackbox.observedRecoveryRatio(), 1.0e-12);
		assertEquals(0.020869392084478244,
				blackbox.releaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0,
				blackbox.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0, blackbox.residualReductionRatio(), 1.0e-12);
		assertEquals(0.07031344250625146,
				blackbox.releaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.007320403101864318, blackbox.releaseAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(0.14242029417317925,
				blackbox.releaseAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.0, blackbox.heldStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.0,
				blackbox.releaseDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.050758156939549215,
				blackbox.heldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.013612475121139264, blackbox.releaseDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(0.00651280080498487, blackbox.heldDeratedAdvanceRatioRange(), 1.0e-12);
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
		assertEquals(0.8606654502327913, fast.releaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.05743181996128399, fast.releaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0, fast.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0, fast.residualReductionRatio(), 1.0e-12);
		assertTrue(fast.freeFlightTrajectoryReleaseBlocked());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightTrajectoryEnvelope
				.StateNormalizedFreeFlightTrajectoryEnvelopeSummary summary = audit.summary();
		assertEquals(5, summary.rowCount());
		assertEquals(4, summary.releaseThrustMarginFailureRowCount());
		assertEquals(2, summary.releaseStateEnvelopeExceededRowCount());
		assertEquals(5, summary.heldResidualEnvelopeClearedRowCount());
		assertEquals(5, summary.heldStateEnvelopeClearedRowCount());
		assertEquals(4, summary.freeFlightTrajectoryReleaseBlockedRowCount());
		assertEquals(0.17449079176426135, summary.maxReleaseThrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.06285299649224374,
				summary.maxReleaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.009954666154520009,
				summary.maxHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(-9954666.154520009, summary.minResidualReductionRatio(), 1.0e-12);
		assertEquals(0.19349805909343265, summary.maxObservedRecoveryRatio(), 1.0e-12);
		assertEquals(3.354058931284269,
				summary.maxReleaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(5.424387428975164, summary.maxReleaseAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(19.357695908640878,
				summary.maxReleaseAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.21968193918808165,
				summary.maxReleaseDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.050758156939549215,
				summary.maxHeldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.07031344250625146,
				summary.blackboxSpeedReleaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.020869392084478244,
				summary.blackboxSpeedReleaseResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0,
				summary.blackboxSpeedHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0,
				summary.blackboxSpeedResidualReductionRatio(), 1.0e-12);
		assertEquals(0.8606654502327913,
				summary.fastSpeedReleaseStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.05743181996128399,
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
		assertEquals("released-thrust-margin-failure-with-held-local-clearance",
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
