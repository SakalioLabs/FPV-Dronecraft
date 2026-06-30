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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGateTest {
	@Test
	void auditSeparatesStateNormalizedLabPassFromFreeFlightAcceptance() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
				.StateNormalizedBlackboxAcceptanceAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-State-Normalized-Blackbox-Acceptance-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("free-flight blackbox acceptance"));
		assertEquals(39, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(8, audit.resultRowCount());
		assertEquals(22, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(0.04, audit.residualDeficitThreshold(), 1.0e-12);
		assertEquals(1.0e-12, audit.stateDeltaEpsilon(), 1.0e-18);
		assertEquals(8, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
				.StateNormalizedBlackboxAcceptanceRow blackboxSpeed =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
								.row("apDrone", "cold_air_forward_punchout_margin");
		assertEquals("synthetic_derate_validation_all_pass", blackboxSpeed.scenarioName());
		assertEquals("apDrone", blackboxSpeed.presetName());
		assertEquals("cold_sea_level_minus10c", blackboxSpeed.ambientCaseName());
		assertEquals("cold_air_forward_punchout_margin", blackboxSpeed.regressionCaseName());
		assertEquals("forward_punchout", blackboxSpeed.flightPhase());
		assertEquals("tip_mach_and_thrust_loss_margin", blackboxSpeed.targetMetric());
		assertEquals(360, blackboxSpeed.sampleCount());
		assertEquals(360, blackboxSpeed.minSampleCount());
		assertEquals(0.0, blackboxSpeed.freeFlightPrimaryErrorRatio(), 1.0e-12);
		assertEquals(0.1208445699538298, blackboxSpeed.freeFlightSecondaryErrorRatio(), 1.0e-12);
		assertEquals(1, blackboxSpeed.freeFlightPhysicalConstraintViolationCount());
		assertFalse(blackboxSpeed.freeFlightRegressionPassed());
		assertTrue(blackboxSpeed.stateNormalizedEvidenceApplied());
		assertEquals(0.0, blackboxSpeed.stateNormalizedPrimaryErrorRatio(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.stateNormalizedSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0, blackboxSpeed.stateNormalizedPhysicalConstraintViolationCount());
		assertEquals(0.02, blackboxSpeed.maxAllowedPrimaryErrorRatio(), 1.0e-12);
		assertEquals(0.04, blackboxSpeed.maxAllowedSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.9821409270762219, blackboxSpeed.contractThrustRatio(), 1.0e-12);
		assertEquals(0.9852557943006115, blackboxSpeed.heldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.005472892263486129,
				blackboxSpeed.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.8716430296864199, blackboxSpeed.heldResidualReductionRatio(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.heldStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertTrue(blackboxSpeed.stateNormalizedRegressionPassed());
		assertTrue(blackboxSpeed.freeFlightAcceptanceStillBlocked());
		assertTrue(blackboxSpeed.stateNormalizedLabAcceptanceAllowed());
		assertFalse(blackboxSpeed.manualControlHookReviewAllowed());
		assertFalse(blackboxSpeed.runtimeImplementationAllowed());
		assertFalse(blackboxSpeed.runtimeCouplingAllowed());
		assertFalse(blackboxSpeed.playableReferenceAllowed());
		assertFalse(blackboxSpeed.gameplayAutoApplyAllowed());
		assertEquals("STATE_NORMALIZED_PASS", blackboxSpeed.status());
		assertEquals(
				"state-normalized-forward-punchout-clears-lab-margin-but-free-flight-acceptance-stays-blocked",
				blackboxSpeed.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
				.StateNormalizedBlackboxAcceptanceRow racingForward =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
								.row("racingQuad", "cold_air_forward_punchout_margin");
		assertTrue(racingForward.freeFlightRegressionPassed());
		assertFalse(racingForward.stateNormalizedEvidenceApplied());
		assertEquals(0.020144608176940988,
				racingForward.stateNormalizedSecondaryErrorRatio(), 1.0e-12);
		assertTrue(racingForward.stateNormalizedRegressionPassed());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
				.StateNormalizedBlackboxAcceptanceSummary summary = audit.summary();
		assertEquals(8, summary.rowCount());
		assertEquals(7, summary.freeFlightPassedRowCount());
		assertEquals(1, summary.freeFlightFailedRowCount());
		assertEquals(1, summary.stateNormalizedEvidenceAppliedRowCount());
		assertEquals(8, summary.stateNormalizedPassedRowCount());
		assertEquals(0, summary.stateNormalizedFailedRowCount());
		assertEquals(0, summary.stateNormalizedPhysicalConstraintViolationCount());
		assertEquals(0.1208445699538298, summary.maxFreeFlightSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.020144608176940988,
				summary.maxStateNormalizedSecondaryErrorRatio(), 1.0e-12);
		assertEquals(0.005472892263486129,
				summary.maxHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.9852557943006115,
				summary.blackboxSpeedHeldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.005472892263486129,
				summary.blackboxSpeedHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.8716430296864199,
				summary.blackboxSpeedResidualReductionRatio(), 1.0e-12);
		assertFalse(summary.currentFreeFlightBlackboxAcceptanceReady());
		assertTrue(summary.stateNormalizedLabAcceptanceReady());
		assertFalse(summary.manualControlHookReviewAllowed());
		assertFalse(summary.runtimeImplementationAllowed());
		assertFalse(summary.runtimeCouplingAllowed());
		assertFalse(summary.playableReferenceAllowed());
		assertFalse(summary.gameplayAutoApplyAllowed());
		assertEquals("STATE_NORMALIZED_READY", summary.status());
		assertEquals(
				"feed-state-normalized-forward-punchout-into-separate-blackbox-harness-before-free-flight-acceptance",
				summary.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
						.row("apDrone", "missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
				.StateNormalizedBlackboxAcceptanceAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedBlackboxAcceptanceGate
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_blackbox_acceptance_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_blackbox_acceptance_gate,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,cold_air_forward_punchout_margin,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_blackbox_acceptance_summary,all,all,all,all,state_normalized_lab_acceptance_ready,true,bool,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_blackbox_acceptance_summary,all,all,all,all,current_free_flight_blackbox_acceptance_ready,false,bool,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_blackbox_acceptance_summary,all,all,all,all,manual_control_hook_review_allowed,false,bool,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_blackbox_acceptance_method,all,all,all,all,method,state-normalized-forward-punchout-substitutes-only-failed-lab-case,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_blackbox_acceptance_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
