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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnosticTest {
	@Test
	void auditShowsStateNormalizationRemovesApDroneSettledResidual() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
				.StateNormalizedPunchoutAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-State-Normalized-Punchout-Diagnostic-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("kinematics held"));
		assertEquals(37, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(5, audit.speedSampleCount());
		assertEquals(5, audit.diagnosticRowCount());
		assertEquals(25, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(0.04, audit.residualDeficitThreshold(), 1.0e-12);
		assertEquals(0.75,
				audit.stateDivergenceVelocityDeltaThresholdMetersPerSecond(), 1.0e-12);
		assertEquals(0.50, audit.stateNormalizedResidualReductionThreshold(), 1.0e-12);
		assertEquals(5, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
				.StateNormalizedPunchoutRow blackboxSpeed =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
								.row(22.0);
		assertEquals("synthetic_derate_validation_all_pass", blackboxSpeed.scenarioName());
		assertEquals("apDrone", blackboxSpeed.presetName());
		assertEquals("cold_sea_level_minus10c", blackboxSpeed.ambientCaseName());
		assertEquals(345, blackboxSpeed.peakSampleIndex());
		assertEquals(1.73, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.991030235197808, blackboxSpeed.targetMaxRpmScale(), 1.0e-12);
		assertEquals(0.982140927076222, blackboxSpeed.contractThrustRatio(), 1.0e-12);
		assertEquals(0.8612963571223922,
				blackboxSpeed.freeFlightObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9039344176041539,
				blackboxSpeed.freeFlightCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.042638060481761775,
				blackboxSpeed.freeFlightResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.6408442519881623,
				blackboxSpeed.freeFlightStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.9852557943006115, blackboxSpeed.heldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9907286865640976,
				blackboxSpeed.heldCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.005472892263486129,
				blackboxSpeed.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.8716430296864199, blackboxSpeed.heldResidualReductionRatio(), 1.0e-12);
		assertEquals(0.12395943717821927,
				blackboxSpeed.heldObservedMinusFreeFlightRatio(), 1.0e-12);
		assertEquals(29.322583877320017, blackboxSpeed.heldNeutralThrustNewtons(), 1.0e-12);
		assertEquals(28.890245668995238, blackboxSpeed.heldDeratedThrustNewtons(), 1.0e-12);
		assertEquals(0.662033647169015, blackboxSpeed.heldNeutralAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.659147381397314, blackboxSpeed.heldDeratedAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.06293041608762284,
				blackboxSpeed.heldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.008037108496122, blackboxSpeed.heldDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.heldStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.heldAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.heldAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertTrue(blackboxSpeed.freeFlightStateDivergence());
		assertTrue(blackboxSpeed.stateNormalizationReducedResidual());
		assertFalse(blackboxSpeed.heldResidualAboveThreshold());
		assertFalse(blackboxSpeed.runtimeCouplingAllowed());
		assertFalse(blackboxSpeed.playableReferenceAllowed());
		assertFalse(blackboxSpeed.gameplayAutoApplyAllowed());
		assertEquals("free-flight-state-drift-dominates-residual", blackboxSpeed.dominantBucket());
		assertEquals("STATE_NORMALIZED", blackboxSpeed.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
				.StateNormalizedPunchoutRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
								.row(28.0);
		assertEquals(0.9854633585071544, fastRow.heldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.0077138566022426636,
				fastRow.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.843495002938369, fastRow.heldResidualReductionRatio(), 1.0e-12);
		assertEquals(0.23478840397287225,
				fastRow.heldObservedMinusFreeFlightRatio(), 1.0e-12);
		assertEquals(0.035081954747968, fastRow.heldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.016271182725127176, fastRow.heldDeratedAdvanceRatioRange(), 1.0e-12);
		assertFalse(fastRow.heldResidualAboveThreshold());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
				.StateNormalizedPunchoutSummary summary = audit.summary();
		assertEquals(5, summary.rowCount());
		assertEquals(0, summary.blockedRowCount());
		assertEquals(3, summary.freeFlightStateDivergenceRowCount());
		assertEquals(5, summary.stateNormalizationReducedResidualRowCount());
		assertEquals(0, summary.heldResidualAboveThresholdRowCount());
		assertEquals(0.6169981537709639, summary.minFreeFlightObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9852557943006115, summary.minHeldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.09981456177414405,
				summary.maxFreeFlightResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0077138566022426636,
				summary.maxHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0, summary.maxResidualReductionRatio(), 1.0e-12);
		assertEquals(0.843495002938369, summary.minResidualReductionRatio(), 1.0e-12);
		assertEquals(0.3880898352676898,
				summary.maxHeldObservedMinusFreeFlightRatio(), 1.0e-12);
		assertEquals(0.06293041608762284,
				summary.maxHeldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.016271182725127176,
				summary.maxHeldDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(0.0, summary.maxHeldStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.9852557943006115,
				summary.blackboxSpeedHeldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.005472892263486129,
				summary.blackboxSpeedHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.8716430296864199,
				summary.blackboxSpeedResidualReductionRatio(), 1.0e-12);
		assertEquals(0.9854633585071544, summary.fastSpeedHeldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.0077138566022426636,
				summary.fastSpeedHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.843495002938369, summary.fastSpeedResidualReductionRatio(), 1.0e-12);
		assertEquals("free-flight-state-drift-dominates-residual", summary.dominantBucket());
		assertEquals("use-state-normalized-punchout-harness-before-residual-thrust-tuning",
				summary.nextRequiredAction());
		assertTrue(summary.stateNormalizedPunchoutDiagnosticPassed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
						.row(12.0));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
				.StateNormalizedPunchoutAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_punchout_diagnostic_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_punchout_diagnostic,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,22.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_punchout_diagnostic,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,28.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_punchout_diagnostic_summary,all,all,all,all,held_residual_above_threshold_row_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_punchout_diagnostic_summary,all,all,all,all,next_required_action,use-state-normalized-punchout-harness-before-residual-thrust-tuning,text,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_punchout_diagnostic_method,all,all,all,all,method,neutral-vs-derated-held-kinematics-peak-punchout,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_state_normalized_punchout_diagnostic_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
