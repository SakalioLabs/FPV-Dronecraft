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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGateTest {
	@Test
	void auditSeparatesTrajectoryReviewFromRuntimeHandoff() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
				.StateNormalizedFreeFlightHandoffAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-State-Normalized-Free-Flight-Handoff-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("held-kinematics lab acceptance"));
		assertEquals(38, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(5, audit.handoffRowCount());
		assertEquals(24, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(5, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
				.StateNormalizedFreeFlightHandoffRow apDrone =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
								.row("apDrone_forward_punchout");
		assertEquals("apDrone", apDrone.presetName());
		assertEquals("cold_sea_level_minus10c", apDrone.ambientCaseName());
		assertEquals("cold_air_forward_punchout_margin", apDrone.regressionCaseName());
		assertEquals("preset_forward_punchout", apDrone.handoffStage());
		assertEquals("state_normalized_secondary_error_ratio", apDrone.metricName());
		assertTrue(apDrone.currentFreeFlightBlackboxAcceptanceReady());
		assertFalse(apDrone.stateNormalizedLabAcceptanceReady());
		assertTrue(apDrone.freeFlightRegressionPassed());
		assertTrue(apDrone.stateNormalizedRegressionPassed());
		assertFalse(apDrone.trajectoryBlocker());
		assertFalse(apDrone.freeFlightTrajectoryReviewRequired());
		assertFalse(apDrone.freeFlightTrajectoryReviewAllowed());
		assertFalse(apDrone.manualControlHookReviewAllowed());
		assertFalse(apDrone.runtimeImplementationAllowed());
		assertFalse(apDrone.runtimeCouplingAllowed());
		assertFalse(apDrone.playableReferenceAllowed());
		assertFalse(apDrone.gameplayAutoApplyAllowed());
		assertEquals(0.009642732412038528, apDrone.freeFlightSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.013942762381785191, apDrone.stateNormalizedSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.014429734182337728, apDrone.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals("FREE_FLIGHT_PASS", apDrone.status());
		assertEquals("no-preset-trajectory-blocker", apDrone.nextRequiredAction());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
				.StateNormalizedFreeFlightHandoffRow racing =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
								.row("racingQuad_forward_punchout");
		assertTrue(racing.freeFlightRegressionPassed());
		assertTrue(racing.stateNormalizedRegressionPassed());
		assertFalse(racing.trajectoryBlocker());
		assertFalse(racing.freeFlightTrajectoryReviewRequired());
		assertEquals("FREE_FLIGHT_PASS", racing.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
				.StateNormalizedFreeFlightHandoffSummary summary = audit.summary();
		assertEquals(5, summary.rowCount());
		assertEquals(8, summary.freeFlightPassedRowCount());
		assertEquals(0, summary.freeFlightFailedRowCount());
		assertEquals(2, summary.stateNormalizedEvidenceAppliedRowCount());
		assertEquals(8, summary.stateNormalizedPassedRowCount());
		assertEquals(0, summary.stateNormalizedFailedRowCount());
		assertEquals(0, summary.trajectoryBlockerRowCount());
		assertEquals(0.020144608176940988, summary.maxFreeFlightSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.021957819779317794,
				summary.maxStateNormalizedSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.009642732412038528,
				summary.apDroneFreeFlightSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.013942762381785191,
				summary.apDroneStateNormalizedSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.014429734182337728,
				summary.apDroneHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertTrue(summary.currentFreeFlightBlackboxAcceptanceReady());
		assertFalse(summary.stateNormalizedLabAcceptanceReady());
		assertFalse(summary.freeFlightTrajectoryReviewRequired());
		assertFalse(summary.freeFlightTrajectoryReviewAllowed());
		assertFalse(summary.manualControlHookReviewAllowed());
		assertFalse(summary.runtimeImplementationAllowed());
		assertFalse(summary.runtimeCouplingAllowed());
		assertFalse(summary.playableReferenceAllowed());
		assertFalse(summary.gameplayAutoApplyAllowed());
		assertEquals("BLOCKED", summary.status());
		assertEquals("none", summary.dominantBlocker());
		assertEquals("inspect-state-normalized-handoff-blockers", summary.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
						.row("missing"));
	}

	@Test
	void handoffGuardDoesNotOpenManualRuntimeOrPlayablePaths() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
				.StateNormalizedFreeFlightHandoffRow guard =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
								.row("handoff_guard");

		assertEquals("state_normalized_to_free_flight_guard", guard.handoffStage());
		assertEquals("free_flight_trajectory_review_allowed", guard.metricName());
		assertEquals("false", guard.metricValue());
		assertFalse(guard.freeFlightTrajectoryReviewRequired());
		assertFalse(guard.freeFlightTrajectoryReviewAllowed());
		assertFalse(guard.manualControlHookReviewAllowed());
		assertFalse(guard.runtimeImplementationAllowed());
		assertFalse(guard.runtimeCouplingAllowed());
		assertFalse(guard.playableReferenceAllowed());
		assertFalse(guard.gameplayAutoApplyAllowed());
		assertEquals("BLOCKED", guard.status());
		assertEquals("trajectory-review-not-ready", guard.message());
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
				.StateNormalizedFreeFlightHandoffAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedFreeFlightHandoffGate
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_free_flight_handoff_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_free_flight_handoff_gate,apDrone_forward_punchout,apDrone,cold_sea_level_minus10c,cold_air_forward_punchout_margin,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_free_flight_handoff_summary,all,all,all,all,free_flight_trajectory_review_allowed,false,bool,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_free_flight_handoff_summary,all,all,all,all,runtime_coupling_allowed,false,bool,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_free_flight_handoff_method,all,all,all,all,method,state-normalized-lab-pass-feeds-free-flight-trajectory-review-only,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_free_flight_handoff_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
