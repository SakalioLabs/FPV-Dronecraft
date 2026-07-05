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
		assertEquals(46, blackboxSpeed.peakSampleIndex());
		assertEquals(0.23500000000000001, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.991030235197808, blackboxSpeed.targetMaxRpmScale(), 1.0e-12);
		assertEquals(0.982140927076222, blackboxSpeed.contractThrustRatio(), 1.0e-12);
		assertEquals(0.9724981946641834,
				blackboxSpeed.freeFlightObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9933675867486617,
				blackboxSpeed.freeFlightCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.020869392084478244,
				blackboxSpeed.freeFlightResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.07031344250625146,
				blackboxSpeed.freeFlightStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(1.0010975905065054, blackboxSpeed.heldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9880711664662691,
				blackboxSpeed.heldCompressibilityProxyRatio(), 1.0e-12);
		assertEquals(0.0,
				blackboxSpeed.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0, blackboxSpeed.heldResidualReductionRatio(), 1.0e-12);
		assertEquals(0.028599395842321962,
				blackboxSpeed.heldObservedMinusFreeFlightRatio(), 1.0e-12);
		assertEquals(28.839846313536064, blackboxSpeed.heldNeutralThrustNewtons(), 1.0e-12);
		assertEquals(28.871500655058878, blackboxSpeed.heldDeratedThrustNewtons(), 1.0e-12);
		assertEquals(0.6592966824220778, blackboxSpeed.heldNeutralAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.6555903126357419, blackboxSpeed.heldDeratedAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.050758156939549215,
				blackboxSpeed.heldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.00651280080498487, blackboxSpeed.heldDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.heldStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.heldAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.heldAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertFalse(blackboxSpeed.freeFlightStateDivergence());
		assertTrue(blackboxSpeed.stateNormalizationReducedResidual());
		assertFalse(blackboxSpeed.heldResidualAboveThreshold());
		assertFalse(blackboxSpeed.runtimeCouplingAllowed());
		assertFalse(blackboxSpeed.playableReferenceAllowed());
		assertFalse(blackboxSpeed.gameplayAutoApplyAllowed());
		assertEquals("state-normalization-removes-local-residual", blackboxSpeed.dominantBucket());
		assertEquals("STATE_NORMALIZED", blackboxSpeed.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
				.StateNormalizedPunchoutRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
								.row(28.0);
		assertEquals(1.0011481944053933, fastRow.heldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.0,
				fastRow.heldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0, fastRow.heldResidualReductionRatio(), 1.0e-12);
		assertEquals(0.19349805909343265,
				fastRow.heldObservedMinusFreeFlightRatio(), 1.0e-12);
		assertEquals(0.037164080219876605, fastRow.heldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.016269811513010213, fastRow.heldDeratedAdvanceRatioRange(), 1.0e-12);
		assertFalse(fastRow.heldResidualAboveThreshold());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookStateNormalizedPunchoutDiagnostic
				.StateNormalizedPunchoutSummary summary = audit.summary();
		assertEquals(5, summary.rowCount());
		assertEquals(0, summary.blockedRowCount());
		assertEquals(2, summary.freeFlightStateDivergenceRowCount());
		assertEquals(4, summary.stateNormalizationReducedResidualRowCount());
		assertEquals(0, summary.heldResidualAboveThresholdRowCount());
		assertEquals(0.8076501353119606, summary.minFreeFlightObservedThrustRatio(), 1.0e-12);
		assertEquals(0.9860954896095497, summary.minHeldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.06285299649224374,
				summary.maxFreeFlightResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.009954666154520009,
				summary.maxHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0, summary.maxResidualReductionRatio(), 1.0e-12);
		assertEquals(-9954666.154520009, summary.minResidualReductionRatio(), 1.0e-12);
		assertEquals(0.19349805909343265,
				summary.maxHeldObservedMinusFreeFlightRatio(), 1.0e-12);
		assertEquals(0.050758156939549215,
				summary.maxHeldDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.016269811513010213,
				summary.maxHeldDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(0.0, summary.maxHeldStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(1.0010975905065054,
				summary.blackboxSpeedHeldObservedThrustRatio(), 1.0e-12);
		assertEquals(0.0,
				summary.blackboxSpeedHeldResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.0,
				summary.blackboxSpeedResidualReductionRatio(), 1.0e-12);
		assertEquals(1.0011481944053933, summary.fastSpeedHeldObservedThrustRatio(), 1.0e-12);
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
