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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivityTest {
	@Test
	void auditReplaysAcceptedRowsAcrossAmbientSpeedOfSoundCases() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityAudit audit =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Sensitivity-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("cold, reference, and hot-air"));
		assertEquals(101, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(3, audit.ambientCaseCount());
		assertEquals(6, audit.sensitivityRowCount());
		assertEquals(18, audit.scenarioMetricRowCount());
		assertEquals(16, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(15.0, audit.referenceAmbientTemperatureCelsius(), 1.0e-12);
		assertEquals(340.29228686527705, audit.referenceSpeedOfSoundMetersPerSecond(), 1.0e-9);
		assertEquals(0.70, audit.maxTipMachLimit(), 1.0e-12);
		assertEquals(0.46, audit.compressibilityOnsetMach(), 1.0e-12);
		assertEquals(0.91, audit.seriousLossMach(), 1.0e-12);
		assertEquals(3, audit.cases().size());
		assertEquals(6, audit.rows().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityScenarioSummary current =
				find(audit.scenarios(), "current_retune_validation_blocked").summary();
		assertFalse(current.physicalBudgetAvailable());
		assertFalse(current.ambientSensitivityAvailable());
		assertEquals(0, current.sensitivityRowCount());
		assertEquals("BLOCKED", current.status());
		assertEquals("validation-run-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityScenarioSummary allPass =
				find(audit.scenarios(), "synthetic_ready_validation_all_pass").summary();
		assertTrue(allPass.validationAcceptanceReady());
		assertTrue(allPass.physicalBudgetAvailable());
		assertTrue(allPass.ambientSensitivityAvailable());
		assertEquals(6, allPass.sensitivityRowCount());
		assertEquals(3, allPass.ambientCaseCount());
		assertEquals(4, allPass.ambientSensitivityAcceptedCount());
		assertEquals(4, allPass.impactWithinBudgetCount());
		assertEquals(6, allPass.compressibilityReviewRequiredCount());
		assertEquals(0.6557591652396883, allPass.maxCandidateMaxTipMach(), 1.0e-12);
		assertEquals(0.04424083476031165, allPass.minCandidateMaxMachMargin(), 1.0e-12);
		assertEquals(11.309908309581662, allPass.maxCandidateMaxThrustLossPercent(), 1.0e-12);
		assertEquals(0.12440899140539828, allPass.maxCandidateMaxVibrationProxy(), 1.0e-12);
		assertEquals(0, allPass.playableReferenceAllowedCount());
		assertEquals(0, allPass.configPatchAllowedCount());
		assertEquals(0, allPass.runtimeCouplingAllowedCount());
		assertEquals(0, allPass.gameplayAutoApplyAllowedCount());
		assertEquals("PENDING_REVIEW", allPass.status());
		assertEquals("cold-air-compressibility-impact-exceeds-budget", allPass.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().ambientSensitivityAvailableScenarioCount());
		assertEquals(6, audit.extrema().totalSensitivityRowCount());
		assertEquals(6, audit.extrema().maxSensitivityRowCount());
		assertEquals(3, audit.extrema().maxAmbientCaseCount());
		assertEquals(4, audit.extrema().maxAmbientSensitivityAcceptedCount());
		assertEquals(4, audit.extrema().maxImpactWithinBudgetCount());
		assertEquals(6, audit.extrema().maxCompressibilityReviewRequiredCount());
		assertEquals(0, audit.extrema().playableReferenceAllowedScenarioCount());
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void coldAirRowsExposeCompressibilityImpactBudgetRegression() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.AmbientCaseDefinition cold =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.caseDefinition(
						"cold_sea_level_minus10c");
		assertEquals(-10.0, cold.ambientTemperatureCelsius(), 1.0e-12);
		assertTrue(cold.note().contains("lowers sound speed"));

		PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityRow racingCold =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.row(
						"synthetic_ready_validation_all_pass",
						"racingQuad",
						"cold_sea_level_minus10c");

		assertEquals(325.1954650667809, racingCold.speedOfSoundMetersPerSecond(), 1.0e-9);
		assertEquals(1.0464238386454636, racingCold.speedOfSoundScaleVsReference(), 1.0e-12);
		assertEquals(0.2930921385100393, racingCold.candidateHoverTipMach(), 1.0e-12);
		assertEquals(0.6557591652396883, racingCold.candidateMaxTipMach(), 1.0e-12);
		assertEquals(0.04424083476031165, racingCold.candidateMaxMachMargin(), 1.0e-12);
		assertEquals(0.2542408347603117, racingCold.seriousLossMachMargin(), 1.0e-12);
		assertEquals(0.5654954154790831, racingCold.candidateMaxCompressibilityIntensity(), 1.0e-12);
		assertEquals(11.309908309581662, racingCold.candidateMaxThrustLossPercent(), 1.0e-12);
		assertEquals(0.2375080745012149, racingCold.candidateMaxLoadFactor(), 1.0e-12);
		assertEquals(1.2129370394460944, racingCold.candidateMaxReactionTorqueScale(), 1.0e-12);
		assertEquals(0.12440899140539828, racingCold.candidateMaxVibrationProxy(), 1.0e-12);
		assertTrue(racingCold.physicalBudgetAccepted());
		assertTrue(racingCold.maxTipMachWithinBudget());
		assertTrue(racingCold.belowSeriousLossMach());
		assertFalse(racingCold.candidateImpactWithinBudget());
		assertTrue(racingCold.compressibilityReviewRequired());
		assertFalse(racingCold.ambientSensitivityAccepted());
		assertFalse(racingCold.playableReferenceAllowed());
		assertFalse(racingCold.configPatchAllowed());
		assertFalse(racingCold.runtimeCouplingAllowed());
		assertFalse(racingCold.gameplayAutoApplyAllowed());
		assertEquals("PENDING_REVIEW", racingCold.status());
		assertEquals("cold-air-compressibility-impact-exceeds-budget", racingCold.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityRow racingReference =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.row(
						"synthetic_ready_validation_all_pass",
						"racingQuad",
						"reference_lab_15c");
		assertTrue(racingReference.candidateImpactWithinBudget());
		assertTrue(racingReference.ambientSensitivityAccepted());
		assertEquals(0.6266668829797807, racingReference.candidateMaxTipMach(), 1.0e-12);
		assertEquals(8.890939026611733, racingReference.candidateMaxThrustLossPercent(), 1.0e-12);

		PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityRow apHot =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.row(
						"synthetic_ready_validation_all_pass",
						"apDrone",
						"hot_air_30c");
		assertTrue(apHot.ambientSensitivityAccepted());
		assertEquals(0.5989865883048388, apHot.candidateMaxTipMach(), 1.0e-12);
		assertEquals(6.641362346138494, apHot.candidateMaxThrustLossPercent(), 1.0e-12);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.caseDefinition("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.scenario("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.row(
						"synthetic_ready_validation_all_pass", "racingQuad", "missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.row(
						"synthetic_ready_validation_all_pass", "cinewhoop", "cold_sea_level_minus10c"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityAudit audit =
				PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_sensitivity_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_summary,all_scenarios,,,,total_sensitivity_row_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_scenario,synthetic_ready_validation_all_pass,,,,ambient_sensitivity_accepted_count,4,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility,synthetic_ready_validation_all_pass,racingQuad,cold_sea_level_minus10c,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility,synthetic_ready_validation_all_pass,apDrone,hot_air_30c,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_summary,all_scenarios,,,,runtime_coupling_allowed_scenario_count,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityScenario find(
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilitySensitivity.RetuneAmbientCompressibilitySensitivityScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_sensitivity_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
