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

class PropellerArchiveRotorSpecRetunePhysicalImpactPreviewTest {
	@Test
	void auditBuildsImpactRowsOnlyAfterManualPatchReviewOpens() {
		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactAudit audit =
				PropellerArchiveRotorSpecRetunePhysicalImpactPreview.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Physical-Impact-Preview-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("hover RPM"));
		assertEquals(78, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(2, audit.impactRowCount());
		assertEquals(12, audit.scenarioMetricRowCount());
		assertEquals(11, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(2, audit.rows().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactScenarioSummary current =
				find(audit.scenarios(), "current_retune_validation_blocked").summary();
		assertFalse(current.impactPreviewAvailable());
		assertEquals(0, current.impactRowCount());
		assertEquals("validation-run-not-planned", current.message());

		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactScenarioSummary missing =
				find(audit.scenarios(), "synthetic_ready_validation_results_missing").summary();
		assertFalse(missing.impactPreviewAvailable());
		assertEquals("validation-result-set-incomplete", missing.message());

		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactScenarioSummary allPass =
				find(audit.scenarios(), "synthetic_ready_validation_all_pass").summary();
		assertTrue(allPass.validationAcceptanceReady());
		assertTrue(allPass.manualPatchReviewAllowed());
		assertTrue(allPass.impactPreviewAvailable());
		assertEquals(2, allPass.impactRowCount());
		assertTrue(allPass.maxHoverRpmDeltaAbs() > 0.0);
		assertTrue(allPass.maxHoverRpmRatioError() > 0.0);
		assertEquals(0.0, allPass.maxDiskLoadingRatioError(), 1.0e-12);
		assertTrue(allPass.maxHoverYawTorqueRatioError() > 0.0);
		assertEquals(0, allPass.configPatchAllowedCount());
		assertEquals(0, allPass.runtimeCouplingAllowedCount());
		assertEquals(0, allPass.gameplayAutoApplyAllowedCount());
		assertEquals("READY", allPass.status());
		assertEquals("retune-physical-impact-preview-ready", allPass.message());

		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactScenarioSummary failed =
				find(audit.scenarios(), "synthetic_ready_validation_one_failed").summary();
		assertFalse(failed.impactPreviewAvailable());
		assertEquals("validation-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().impactPreviewAvailableScenarioCount());
		assertEquals(2, audit.extrema().totalImpactRowCount());
		assertEquals(2, audit.extrema().maxImpactRowCount());
		assertTrue(audit.extrema().maxHoverRpmDeltaAbs() > 0.0);
		assertTrue(audit.extrema().maxHoverRpmRatioError() > 0.0);
		assertEquals(0.0, audit.extrema().maxDiskLoadingRatioError(), 1.0e-12);
		assertTrue(audit.extrema().maxHoverYawTorqueRatioError() > 0.0);
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void impactRowsMatchRotorPhysicsFormulas() {
		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactRow racing =
				PropellerArchiveRotorSpecRetunePhysicalImpactPreview.row(
						"synthetic_ready_validation_all_pass", "racingQuad");
		DroneConfig config = DroneConfig.racingQuad();
		RotorSpec rotor = config.rotors().get(0);
		double hoverThrust = config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size();
		double currentHoverOmega = Math.sqrt(hoverThrust / rotor.thrustCoefficient());
		double currentHoverRpm = currentHoverOmega * 60.0 / (2.0 * Math.PI);

		assertEquals(4, racing.rotorCount());
		assertEquals(hoverThrust, racing.hoverThrustPerRotorNewtons(), 1.0e-12);
		assertEquals(hoverThrust / (Math.PI * rotor.radiusMeters() * rotor.radiusMeters()),
				racing.currentDiskLoadingNewtonsPerSquareMeter(), 1.0e-12);
		assertEquals(1.0, racing.diskLoadingRatioCandidateOverCurrent(), 1.0e-12);
		assertEquals(currentHoverRpm, racing.currentHoverRpm(), 1.0e-9);
		assertTrue(racing.candidateHoverRpm() > racing.currentHoverRpm());
		assertEquals(racing.candidateHoverRpm() - racing.currentHoverRpm(), racing.hoverRpmDelta(), 1.0e-12);
		assertEquals(racing.candidateHoverRpm() / racing.currentHoverRpm(),
				racing.hoverRpmRatioCandidateOverCurrent(), 1.0e-12);
		assertEquals(currentHoverOmega * rotor.radiusMeters(),
				racing.currentHoverTipSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(hoverThrust * rotor.yawTorquePerThrustMeter(),
				racing.currentHoverYawTorqueNewtonMeters(), 1.0e-12);
		assertTrue(racing.hoverYawTorqueRatioCandidateOverCurrent() > 1.0);
		assertTrue(racing.manualPatchReviewAllowed());
		assertFalse(racing.configPatchAllowed());
		assertFalse(racing.runtimeCouplingAllowed());
		assertFalse(racing.gameplayAutoApplyAllowed());

		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactRow apDrone =
				PropellerArchiveRotorSpecRetunePhysicalImpactPreview.row(
						"synthetic_ready_validation_all_pass", "apDrone");
		assertEquals(1.0, apDrone.diskLoadingRatioCandidateOverCurrent(), 1.0e-12);
		assertTrue(apDrone.candidateHoverRpm() > apDrone.currentHoverRpm());
		assertTrue(apDrone.hoverYawTorqueRatioCandidateOverCurrent() > 1.0);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetunePhysicalImpactPreview.row(
						"synthetic_ready_validation_all_pass", "cinewhoop"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetunePhysicalImpactPreview.scenario("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactAudit audit =
				PropellerArchiveRotorSpecRetunePhysicalImpactPreview.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_physical_impact_preview_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_physical_impact_summary,all_scenarios,total_impact_row_count,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_physical_impact_scenario,synthetic_ready_validation_all_pass,,0,impact_row_count,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_physical_impact,synthetic_ready_validation_all_pass,racingQuad,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_physical_impact,synthetic_ready_validation_all_pass,apDrone,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_physical_impact_summary,all_scenarios,runtime_coupling_allowed_scenario_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_physical_impact_summary,all_scenarios,config_patch_allowed_scenario_count,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactScenario find(
			List<PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactScenario> scenarios,
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
							"docs/data/propeller_archive_rotor_spec_retune_physical_impact_preview_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
