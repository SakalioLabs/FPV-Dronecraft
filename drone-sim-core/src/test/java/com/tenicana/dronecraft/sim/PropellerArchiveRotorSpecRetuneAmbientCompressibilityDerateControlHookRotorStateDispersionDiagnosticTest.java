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
		assertEquals(353, blackboxSpeed.peakSampleIndex());
		assertEquals(1.77, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.1961156951528542, blackboxSpeed.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.6704653270613072,
				blackboxSpeed.stateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.5463830537769551, blackboxSpeed.attitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(2.5975089859340508,
				blackboxSpeed.angularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.04103540992942442, blackboxSpeed.propellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.14445936611474752, blackboxSpeed.propellerScaleDropMax(), 1.0e-12);
		assertEquals(0.22278609979262776, blackboxSpeed.propellerScaleDropRange(), 1.0e-12);
		assertEquals(0.34664069690933275, blackboxSpeed.deratedAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.2585842816202404, blackboxSpeed.deratedMinPropellerThrustScale(), 1.0e-12);
		assertEquals(0.4460408600904524, blackboxSpeed.deratedMaxPropellerThrustScale(), 1.0e-12);
		assertEquals(0.18745657847021202, blackboxSpeed.deratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.18479613476818135, blackboxSpeed.deratedAverageAdvanceRatio(), 1.0e-12);
		assertEquals(0.02491052456241008, blackboxSpeed.deratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(1.5361903260843972, blackboxSpeed.deratedRotorThrustRangeNewtons(), 1.0e-12);
		assertEquals(0.670093195894906, blackboxSpeed.deratedAverageRotorStallIntensity(), 1.0e-12);
		assertEquals(0.3143564932934507, blackboxSpeed.deratedAverageBladeDissymmetryIntensity(), 1.0e-12);
		assertEquals(0.8548625875892806, blackboxSpeed.deratedAverageBladeElementStallIntensity(), 1.0e-12);
		assertEquals(0.23058913265226144, blackboxSpeed.deratedAverageConingIntensity(), 1.0e-12);
		assertEquals(0.9381455728556013, blackboxSpeed.residualAfterCompressibilityRatio(), 1.0e-12);
		assertEquals(0.051824729389994606, blackboxSpeed.residualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.11604659391786115,
				blackboxSpeed.propellerScaleDropOverFormulaArchiveGap(), 1.0e-12);
		assertFalse(blackboxSpeed.freeFlightStateDivergence());
		assertTrue(blackboxSpeed.propellerScaleSpreadLarge());
		assertFalse(blackboxSpeed.propellerScaleDropExceedsArchiveGap());
		assertTrue(blackboxSpeed.residualDeficitAboveThreshold());
		assertFalse(blackboxSpeed.runtimeCouplingAllowed());
		assertFalse(blackboxSpeed.playableReferenceAllowed());
		assertFalse(blackboxSpeed.gameplayAutoApplyAllowed());
		assertEquals("residual-thrust-scale-before-state-dispersion",
				blackboxSpeed.dominantDispersionBucket());
		assertEquals("BLOCKED", blackboxSpeed.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
				.RotorStateDispersionRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
								.row(28.0);
		assertEquals(1.3485566501916006, fastRow.stateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.14444171718757012, fastRow.propellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.16806661327154948, fastRow.propellerScaleDropMax(), 1.0e-12);
		assertEquals(0.14726664929560818, fastRow.deratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.031410890798159924, fastRow.deratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(2.7523201841958485, fastRow.deratedRotorThrustRangeNewtons(), 1.0e-12);
		assertEquals(2.7052694036593445, fastRow.propellerScaleDropOverFormulaArchiveGap(), 1.0e-12);
		assertTrue(fastRow.freeFlightStateDivergence());
		assertTrue(fastRow.propellerScaleSpreadLarge());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
				.RotorStateDispersionSummary summary = audit.summary();
		assertEquals(5, summary.rowCount());
		assertEquals(5, summary.failedRowCount());
		assertEquals(3, summary.freeFlightStateDivergenceRowCount());
		assertEquals(4, summary.propellerScaleSpreadLargeRowCount());
		assertEquals(2, summary.propellerScaleDropExceedsArchiveGapRowCount());
		assertEquals(3, summary.residualDeficitAboveThresholdRowCount());
		assertEquals(2.9892244211484305, summary.maxStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(5.914021977804991, summary.maxAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(4.711665364071266, summary.maxAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.5054526604279256, summary.maxPropellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.7434815847910942, summary.maxPropellerScaleDropMax(), 1.0e-12);
		assertEquals(0.3416905851736429, summary.maxPropellerScaleDropRange(), 1.0e-12);
		assertEquals(0.3969134902024324,
				summary.maxDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.0714654008623731, summary.maxDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(3.9128538244672075, summary.maxDeratedRotorThrustRangeNewtons(), 1.0e-12);
		assertEquals(0.8548625875892806,
				summary.maxDeratedAverageBladeElementStallIntensity(), 1.0e-12);
		assertEquals(0.670093195894906, summary.maxDeratedAverageRotorStallIntensity(), 1.0e-12);
		assertEquals(0.06686369268551773, summary.maxResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.6704653270613072,
				summary.blackboxSpeedStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.04103540992942442,
				summary.blackboxSpeedPropellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.051824729389994606,
				summary.blackboxSpeedResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.3485566501916006,
				summary.fastSpeedStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.14444171718757012,
				summary.fastSpeedPropellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.14726664929560818,
				summary.fastSpeedDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals("free-flight-state-divergence-with-per-rotor-propeller-scale-spread",
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
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_rotor_state_dispersion_diagnostic_summary,all,all,all,all,free_flight_state_divergence_row_count,2,count,")));
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
