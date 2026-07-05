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
		assertEquals(46, blackboxSpeed.peakSampleIndex());
		assertEquals(0.23500000000000001, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.009642732412038528, blackboxSpeed.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(0.07031344250625146,
				blackboxSpeed.stateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.007320403101864318, blackboxSpeed.attitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(0.14242029417317925,
				blackboxSpeed.angularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.propellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.propellerScaleDropMax(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.propellerScaleDropRange(), 1.0e-12);
		assertEquals(1.0, blackboxSpeed.deratedAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(1.0, blackboxSpeed.deratedMinPropellerThrustScale(), 1.0e-12);
		assertEquals(1.0, blackboxSpeed.deratedMaxPropellerThrustScale(), 1.0e-12);
		assertEquals(0.0, blackboxSpeed.deratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.031347025623796536, blackboxSpeed.deratedAverageAdvanceRatio(), 1.0e-12);
		assertEquals(0.013612475121139264, blackboxSpeed.deratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(1.744370158949999, blackboxSpeed.deratedRotorThrustRangeNewtons(), 1.0e-12);
		assertEquals(0.3421652802625781, blackboxSpeed.deratedAverageRotorStallIntensity(), 1.0e-12);
		assertEquals(9.711504930912702e-4, blackboxSpeed.deratedAverageBladeDissymmetryIntensity(), 1.0e-12);
		assertEquals(0.5556748575532443, blackboxSpeed.deratedAverageBladeElementStallIntensity(), 1.0e-12);
		assertEquals(0.33111171689124785, blackboxSpeed.deratedAverageConingIntensity(), 1.0e-12);
		assertEquals(0.9789912693318444, blackboxSpeed.residualAfterCompressibilityRatio(), 1.0e-12);
		assertEquals(0.020869392084478244, blackboxSpeed.residualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.0,
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
		assertEquals(0.8606654502327913, fastRow.stateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.13606002608911932, fastRow.propellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.15643014557193613, fastRow.propellerScaleDropMax(), 1.0e-12);
		assertEquals(0.10484747015062723, fastRow.deratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.013892306264261259, fastRow.deratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(2.5260589711039043, fastRow.deratedRotorThrustRangeNewtons(), 1.0e-12);
		assertEquals(1.207908288304092, fastRow.propellerScaleDropOverFormulaArchiveGap(), 1.0e-12);
		assertTrue(fastRow.freeFlightStateDivergence());
		assertFalse(fastRow.propellerScaleSpreadLarge());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
				.RotorStateDispersionSummary summary = audit.summary();
		assertEquals(5, summary.rowCount());
		assertEquals(5, summary.failedRowCount());
		assertEquals(2, summary.freeFlightStateDivergenceRowCount());
		assertEquals(2, summary.propellerScaleSpreadLargeRowCount());
		assertEquals(0, summary.propellerScaleDropExceedsArchiveGapRowCount());
		assertEquals(2, summary.residualDeficitAboveThresholdRowCount());
		assertEquals(3.354058931284269, summary.maxStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(5.424387428975164, summary.maxAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(19.357695908640878, summary.maxAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.31177722940737174, summary.maxPropellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.4561816155794527, summary.maxPropellerScaleDropMax(), 1.0e-12);
		assertEquals(0.3096179981346193, summary.maxPropellerScaleDropRange(), 1.0e-12);
		assertEquals(0.21968193918808165,
				summary.maxDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.042035572947572625, summary.maxDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(3.3725071845480556, summary.maxDeratedRotorThrustRangeNewtons(), 1.0e-12);
		assertEquals(0.8915324357349034,
				summary.maxDeratedAverageBladeElementStallIntensity(), 1.0e-12);
		assertEquals(0.5960938389179319, summary.maxDeratedAverageRotorStallIntensity(), 1.0e-12);
		assertEquals(0.06285299649224374, summary.maxResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.07031344250625146,
				summary.blackboxSpeedStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.0,
				summary.blackboxSpeedPropellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.020869392084478244,
				summary.blackboxSpeedResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(0.8606654502327913,
				summary.fastSpeedStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.13606002608911932,
				summary.fastSpeedPropellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.10484747015062723,
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
