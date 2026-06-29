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
		assertEquals(345, blackboxSpeed.peakSampleIndex());
		assertEquals(1.73, blackboxSpeed.peakTimeSeconds(), 1.0e-12);
		assertEquals(0.1208445699538298, blackboxSpeed.thrustLossOverContractRatio(), 1.0e-12);
		assertEquals(1.6408442519881623,
				blackboxSpeed.stateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.331090324189972, blackboxSpeed.attitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(2.2872598852152373,
				blackboxSpeed.angularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.06658985181073551, blackboxSpeed.propellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.248375306143679, blackboxSpeed.propellerScaleDropMax(), 1.0e-12);
		assertEquals(0.33167463321215496, blackboxSpeed.propellerScaleDropRange(), 1.0e-12);
		assertEquals(0.649160172139802, blackboxSpeed.deratedAveragePropellerThrustScale(), 1.0e-12);
		assertEquals(0.576408656732182, blackboxSpeed.deratedMinPropellerThrustScale(), 1.0e-12);
		assertEquals(0.698226639178999, blackboxSpeed.deratedMaxPropellerThrustScale(), 1.0e-12);
		assertEquals(0.121817982446817, blackboxSpeed.deratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.122705056737043, blackboxSpeed.deratedAverageAdvanceRatio(), 1.0e-12);
		assertEquals(0.017032309856185, blackboxSpeed.deratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(1.275470322368358, blackboxSpeed.deratedRotorThrustRangeNewtons(), 1.0e-12);
		assertEquals(0.6728705997273056, blackboxSpeed.deratedAverageRotorStallIntensity(), 1.0e-12);
		assertEquals(0.039616729875873, blackboxSpeed.deratedAverageBladeDissymmetryIntensity(), 1.0e-12);
		assertEquals(0.9901066724066678, blackboxSpeed.deratedAverageBladeElementStallIntensity(), 1.0e-12);
		assertEquals(0.156127340741183, blackboxSpeed.deratedAverageConingIntensity(), 1.0e-12);
		assertEquals(0.9528305818968898, blackboxSpeed.residualAfterCompressibilityRatio(), 1.0e-12);
		assertEquals(0.042638060481761775, blackboxSpeed.residualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.860721012895054,
				blackboxSpeed.propellerScaleDropOverFormulaArchiveGap(), 1.0e-12);
		assertTrue(blackboxSpeed.freeFlightStateDivergence());
		assertTrue(blackboxSpeed.propellerScaleSpreadLarge());
		assertTrue(blackboxSpeed.propellerScaleDropExceedsArchiveGap());
		assertTrue(blackboxSpeed.residualDeficitAboveThreshold());
		assertFalse(blackboxSpeed.runtimeCouplingAllowed());
		assertFalse(blackboxSpeed.playableReferenceAllowed());
		assertFalse(blackboxSpeed.gameplayAutoApplyAllowed());
		assertEquals("free-flight-state-divergence-with-per-rotor-propeller-scale-spread",
				blackboxSpeed.dominantDispersionBucket());
		assertEquals("BLOCKED", blackboxSpeed.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
				.RotorStateDispersionRow fastRow =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
								.row(28.0);
		assertEquals(2.4762712346945404, fastRow.stateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.19639631397321564, fastRow.propellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.27303570708373837, fastRow.propellerScaleDropMax(), 1.0e-12);
		assertEquals(0.27751071957620477, fastRow.deratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.05344126847947031, fastRow.deratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(1.8987789578399976, fastRow.deratedRotorThrustRangeNewtons(), 1.0e-12);
		assertEquals(6.062104191116544, fastRow.propellerScaleDropOverFormulaArchiveGap(), 1.0e-12);
		assertTrue(fastRow.freeFlightStateDivergence());
		assertTrue(fastRow.propellerScaleSpreadLarge());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookRotorStateDispersionDiagnostic
				.RotorStateDispersionSummary summary = audit.summary();
		assertEquals(5, summary.rowCount());
		assertEquals(5, summary.failedRowCount());
		assertEquals(3, summary.freeFlightStateDivergenceRowCount());
		assertEquals(4, summary.propellerScaleSpreadLargeRowCount());
		assertEquals(4, summary.propellerScaleDropExceedsArchiveGapRowCount());
		assertEquals(3, summary.residualDeficitAboveThresholdRowCount());
		assertEquals(2.4762712346945404, summary.maxStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.40862814603348924, summary.maxAttitudeEulerDeltaRadians(), 1.0e-12);
		assertEquals(2.2872598852152373, summary.maxAngularVelocityDeltaRadiansPerSecond(), 1.0e-12);
		assertEquals(0.19639631397321564, summary.maxPropellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.27303570708373837, summary.maxPropellerScaleDropMax(), 1.0e-12);
		assertEquals(0.33167463321215496, summary.maxPropellerScaleDropRange(), 1.0e-12);
		assertEquals(0.27751071957620477,
				summary.maxDeratedPropellerThrustScaleRange(), 1.0e-12);
		assertEquals(0.05344126847947031, summary.maxDeratedAdvanceRatioRange(), 1.0e-12);
		assertEquals(1.8987789578399976, summary.maxDeratedRotorThrustRangeNewtons(), 1.0e-12);
		assertEquals(0.9901066724066678,
				summary.maxDeratedAverageBladeElementStallIntensity(), 1.0e-12);
		assertEquals(0.6728705997273056, summary.maxDeratedAverageRotorStallIntensity(), 1.0e-12);
		assertEquals(0.09981456177414405, summary.maxResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(1.6408442519881623,
				summary.blackboxSpeedStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.06658985181073551,
				summary.blackboxSpeedPropellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.042638060481761775,
				summary.blackboxSpeedResidualThrustDeficitRatio(), 1.0e-12);
		assertEquals(2.4762712346945404,
				summary.fastSpeedStateVelocityDeltaMetersPerSecond(), 1.0e-12);
		assertEquals(0.19639631397321564,
				summary.fastSpeedPropellerScaleDropAverage(), 1.0e-12);
		assertEquals(0.27751071957620477,
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
