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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnosticTest {
	@Test
	void auditShowsFreeFlightStateDispersionAmplifiesApDronePropellerScaleDrop() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
				.RotorStateDispersionAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Rotor-State-Dispersion-Diagnostic-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("free-flight settled punchout states"));
		assertEquals(39, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(5, audit.speedSampleCount());
		assertEquals(5, audit.dispersionRowCount());
		assertEquals(27, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(0.75, audit.stateDivergenceVelocityDeltaThresholdMetersPerSecond(), 1.0e-12);
		assertEquals(0.12, audit.propellerScaleRangeThreshold(), 1.0e-12);
		assertEquals(1.50, audit.dropOverArchiveGapThreshold(), 1.0e-12);
		assertEquals(0.04, audit.residualDeficitThreshold(), 1.0e-12);
		assertEquals(5, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
				.RotorStateDispersionRow blackboxSpeed =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
								.row(22.0);
		assertEquals("synthetic_derate_validation_all_pass", blackboxSpeed.scenarioName());
		assertEquals("apDrone", blackboxSpeed.presetName());
		assertEquals("cold_sea_level_minus10c", blackboxSpeed.ambientCaseName());
		assertEquals(14, blackboxSpeed.peakSampleIndex());
		assertEquals(0.075, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.012099179305115717, blackboxSpeed.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.04283390005570867,
				blackboxSpeed.stateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.001990762073396154, blackboxSpeed.attitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(0.04591377889097348,
				blackboxSpeed.angularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.005419964833438928, blackboxSpeed.propellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.006903972205448139, blackboxSpeed.propellerScaleDropMax(), 1.0e-12);
		assertEquals(0.0026400994089893493, blackboxSpeed.propellerScaleDropRange(), 1.0e-12);
		assertEquals(0.7103332486403016, blackboxSpeed.deratedAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.6438119922118046, blackboxSpeed.deratedMinPropellerThrustScale(), 1.0e-12);
		assertEquals(0.7590483803041892, blackboxSpeed.deratedMaxPropellerThrustScale(), 1.0e-12);
		assertEquals(0.11523638809238457, blackboxSpeed.deratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.11472780610497432, blackboxSpeed.deratedAverageAdvanceRatio(), 1.0e-12);
		assertEquals(0.014139620384393065, blackboxSpeed.deratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(1.382911946392019, blackboxSpeed.deratedRotorThrustRangeNewtons(), 1.0e-12);
		assertEquals(0.017073096235844805, blackboxSpeed.deratedAverageRotorStallIntensity(), 1.0e-12);
		assertEquals(0.05964201579829364, blackboxSpeed.deratedAverageBladeDissymmetryIntensity(), 1.0e-12);
		assertEquals(0.019773399978873136, blackboxSpeed.deratedAverageBladeElementStallIntensity(), 1.0e-12);
		assertEquals(0.5704789412610216, blackboxSpeed.deratedAverageConingIntensity(), 1.0e-12);
		assertEquals(0.9865365816390166, blackboxSpeed.residualAfterCompressibilityRatio(), 1.0e-12);
		assertEquals(0.01323831079448079, blackboxSpeed.residualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.17142992197019252,
				blackboxSpeed.propellerScaleDropOverFormulaArchiveGap(), 1.0e-12);
		assertFalse(blackboxSpeed.freeFlightStateDivergence());
		assertFalse(blackboxSpeed.propellerScaleSpreadLarge());
		assertFalse(blackboxSpeed.propellerScaleDropExceedsArchiveGap());
		assertFalse(blackboxSpeed.residualDeficitAboveThreshold());
		assertFalse(blackboxSpeed.runtimeCouplingAllowed());
		assertFalse(blackboxSpeed.playableReferenceAllowed());
		assertFalse(blackboxSpeed.gameplayAutoApplyAllowed());
		assertEquals("local-propeller-scale-spread-without-large-state-divergence",
				blackboxSpeed.dominantDispersionBucket());
		assertEquals("BLOCKED", blackboxSpeed.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
				.RotorStateDispersionRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
								.row(28.0);
		assertEquals(2.2027601492399342, fastRow.stateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.20251176813300015, fastRow.propellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.2542397982801451, fastRow.propellerScaleDropMax(), 1.0e-12);
		assertEquals(0.036574782374596326, fastRow.deratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.005145688744203805, fastRow.deratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(1.5208097482934004, fastRow.deratedRotorThrustRangeNewtons(), 1.0e-12);
		assertEquals(1.6889642820041064, fastRow.propellerScaleDropOverFormulaArchiveGap(), 1.0e-12);
		assertTrue(fastRow.freeFlightStateDivergence());
		assertFalse(fastRow.propellerScaleSpreadLarge());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
				.RotorStateDispersionSummary summary = audit.summary();
		assertEquals(5, summary.rowCount());
		assertEquals(5, summary.failedRowCount());
		assertEquals(3, summary.freeFlightStateDivergenceRowCount());
		assertEquals(3, summary.propellerScaleSpreadLargeRowCount());
		assertEquals(3, summary.propellerScaleDropExceedsArchiveGapRowCount());
		assertEquals(2, summary.residualDeficitAboveThresholdRowCount());
		assertEquals(4.9855010127678385, summary.maxStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(5.57766181007588, summary.maxAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(23.25489617548909, summary.maxAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.3481504747303751, summary.maxPropellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.5887286110593163, summary.maxPropellerScaleDropMax(), 1.0e-12);
		assertEquals(0.476372078017142, summary.maxPropellerScaleDropRange(), 1.0e-12);
		assertEquals(0.3031595636397926,
				summary.maxDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.06289363764670589, summary.maxDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(3.555181954113693, summary.maxDeratedRotorThrustRangeNewtons(), 1.0e-12);
		assertEquals(0.5974165855587633,
				summary.maxDeratedAverageBladeElementStallIntensity(), 1.0e-12);
		assertEquals(0.5413006888219459, summary.maxDeratedAverageRotorStallIntensity(), 1.0e-12);
		assertEquals(0.06290864573533295, summary.maxResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.04283390005570867,
				summary.blackboxSpeedStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.005419964833438928,
				summary.blackboxSpeedPropellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.01323831079448079,
				summary.blackboxSpeedResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(2.2027601492399342,
				summary.fastSpeedStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.20251176813300015,
				summary.fastSpeedPropellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.036574782374596326,
				summary.fastSpeedDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals("residual-thrust-scale-before-state-dispersion",
				summary.dominantDispersionBucket());
		assertEquals("inspect-free-flight-state-hold-or-normalize-blackbox-punchout-before-residual-thrust-tuning",
				summary.nextRequiredAction());
		assertFalse(summary.rotorStateDispersionDiagnosticPassed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
						.row(12.0));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
				.RotorStateDispersionAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_rotor_state_dispersion_diagnostic_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_rotor_state_dispersion_diagnostic,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,22.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_rotor_state_dispersion_diagnostic,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,28.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_rotor_state_dispersion_diagnostic_summary,all,all,all,all,free_flight_state_divergence_row_count,3,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_rotor_state_dispersion_diagnostic_summary,all,all,all,all,next_required_action,inspect-free-flight-state-hold-or-normalize-blackbox-punchout-before-residual-thrust-tuning,text,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_rotor_state_dispersion_diagnostic_method,all,all,all,all,method,neutral-vs-derated-free-flight-peak-state-dispersion,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_rotor_state_dispersion_diagnostic_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
