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
		assertEquals(14, blackboxSpeed.peakSampleIndex());
		assertEquals(0.075, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.991030235197808, blackboxSpeed.targetMaxRpmScale(), 1.0e-12);
		assertEquals(0.982140927076222, blackboxSpeed.contractThrustRatio(), 1.0e-12);
		assertEquals(0.9700417477711062,
				blackboxSpeed.freeFlightObservedThrustRatio(), 1.0e-12);
		assertEquals(0.983280058565587,
				blackboxSpeed.freeFlightCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.01323831079448079,
				blackboxSpeed.freeFlightResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.04283390005570867,
				blackboxSpeed.freeFlightStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.9707209799261323, blackboxSpeed.heldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9844086784898141,
				blackboxSpeed.heldCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.013687698563681794,
				blackboxSpeed.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(-0.033946005361073645, blackboxSpeed.heldResidualReductionRatio(), 1.0e-12);
		assertEquals(6.792321550260771e-4,
				blackboxSpeed.heldObservedMinusFreeFlightRatio(), 1.0e-12);
		assertEquals(30.354530400536206, blackboxSpeed.heldNeutralThrustNewtons(), 1.0e-12);
		assertEquals(29.46577949560608, blackboxSpeed.heldDeratedThrustNewtons(), 1.0e-12);
		assertEquals(0.6603297678408826, blackboxSpeed.heldNeutralAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.6554697433501953, blackboxSpeed.heldDeratedAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.021835120766790728,
				blackboxSpeed.heldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.0027909323802535746, blackboxSpeed.heldDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.heldStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.heldAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.heldAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertFalse(blackboxSpeed.freeFlightStateDivergence());
		assertFalse(blackboxSpeed.stateNormalizationReducedResidual());
		assertFalse(blackboxSpeed.heldResidualAboveThreshold());
		assertFalse(blackboxSpeed.runtimeCouplingAllowed());
		assertFalse(blackboxSpeed.playableReferenceAllowed());
		assertFalse(blackboxSpeed.gameplayAutoApplyAllowed());
		assertEquals("state-normalized-harness-required-before-acceptance", blackboxSpeed.dominantBucket());
		assertEquals("STATE_NORMALIZED", blackboxSpeed.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
				.StateNormalizedPunchoutRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
								.row(28.0);
		assertEquals(0.9996442161258576, fastRow.heldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.0,
				fastRow.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0, fastRow.heldResidualReductionRatio(), 1.0e-12);
		assertEquals(0.25930206501877473,
				fastRow.heldObservedMinusFreeFlightRatio(), 1.0e-12);
		assertEquals(0.043745089415982874, fastRow.heldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.015107175779373566, fastRow.heldDeratedAdvanceRatioRange(), 1.0e-12);
		assertFalse(fastRow.heldResidualAboveThreshold());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
				.StateNormalizedPunchoutSummary summary = audit.summary();
		assertEquals(5, summary.rowCount());
		assertEquals(0, summary.blockedRowCount());
		assertEquals(3, summary.freeFlightStateDivergenceRowCount());
		assertEquals(4, summary.stateNormalizationReducedResidualRowCount());
		assertEquals(0, summary.heldResidualAboveThresholdRowCount());
		assertEquals(0.6317537107590055, summary.minFreeFlightObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9707209799261323, summary.minHeldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.06290864573533295,
				summary.maxFreeFlightResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.013687698563681794,
				summary.maxHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0, summary.maxResidualReductionRatio(), 1.0e-12);
		assertEquals(-0.033946005361073645, summary.minResidualReductionRatio(), 1.0e-12);
		assertEquals(0.3736390567296842,
				summary.maxHeldObservedMinusFreeFlightRatio(), 1.0e-12);
		assertEquals(0.043745089415982874,
				summary.maxHeldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.015107175779373566,
				summary.maxHeldDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(0.0, summary.maxHeldStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.9707209799261323,
				summary.blackboxSpeedHeldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.013687698563681794,
				summary.blackboxSpeedHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(-0.033946005361073645,
				summary.blackboxSpeedResidualReductionRatio(), 1.0e-12);
		assertEquals(0.9996442161258576, summary.fastSpeedHeldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.0,
				summary.fastSpeedHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0, summary.fastSpeedResidualReductionRatio(), 1.0e-12);
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
