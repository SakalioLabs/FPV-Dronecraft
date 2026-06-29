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

class PropellerArchiveFitReadinessGateTest {
	@Test
	void currentTriageScenarioStaysBlockedBySourceBoundary() {
		PropellerArchiveFitReadinessGate.FitReadinessAudit audit =
				PropellerArchiveFitReadinessGate.audit();

		assertEquals("User-Propeller-Archive-Fit-Readiness-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("source/license review"));
		assertEquals(31, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(3, audit.scenarioSampleCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(12, audit.presetRowCount());
		assertEquals(9, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(10, audit.minStaticRowCountForFit());
		assertEquals(0.6, audit.minAdvanceRatioRangeForFit(), 1.0e-12);
		assertEquals(0.5, audit.minEfficiencyEvidenceForFit(), 1.0e-12);

		PropellerArchiveFitReadinessGate.FitReadinessScenario current =
				find(audit.scenarios(), "current_triage_blocked");
		PropellerArchiveFitReadinessGate.FitReadinessSummary summary = current.summary();
		assertFalse(summary.sourceBoundaryOpen());
		assertTrue(summary.allExpectedPresetsPresent());
		assertEquals(4, summary.performanceMatchCount());
		assertEquals(3, summary.bladeCountReadyCount());
		assertEquals(3, summary.geometryMatchReadyCount());
		assertEquals(0, summary.ctCpFitReadyCount());
		assertEquals(0, summary.geometryFitReadyCount());
		assertEquals(0, summary.simulationFitReadyCount());
		assertEquals("source-license-review-required", summary.dominantBlocker());
		assertEquals("review-source-license-before-importing-raw-rows", summary.nextRequiredAction());
		assertFalse(summary.simulationFitReady());
		assertFalse(summary.playableReferenceHandoffReady());
		assertTrue(current.presets().stream()
				.noneMatch(PropellerArchiveFitReadinessGate.PresetFitReadiness::runtimeCouplingAllowed));
		assertTrue(current.presets().stream()
				.noneMatch(PropellerArchiveFitReadinessGate.PresetFitReadiness::gameplayAutoApplyAllowed));
	}

	@Test
	void reviewedImportedScenarioExposesPresetCoverageGapsBeforeFitting() {
		PropellerArchiveFitReadinessGate.FitReadinessScenario scenario =
				find(PropellerArchiveFitReadinessGate.audit().scenarios(),
						"reviewed_imported_with_coverage_gaps");

		PropellerArchiveFitReadinessGate.FitReadinessSummary summary = scenario.summary();
		assertTrue(summary.sourceBoundaryOpen());
		assertEquals(4, summary.performanceMatchCount());
		assertEquals(3, summary.bladeCountReadyCount());
		assertEquals(3, summary.geometryMatchReadyCount());
		assertEquals(4, summary.staticCoverageReadyCount());
		assertEquals(4, summary.advanceCoverageReadyCount());
		assertEquals(4, summary.efficiencyEvidenceReadyCount());
		assertEquals(3, summary.ctCpFitReadyCount());
		assertEquals(3, summary.geometryFitReadyCount());
		assertEquals(2, summary.simulationFitReadyCount());
		assertFalse(summary.coefficientFitReady());
		assertFalse(summary.geometryFitReady());
		assertFalse(summary.simulationFitReady());
		assertEquals("preset-coverage-gaps", summary.dominantBlocker());
		assertEquals("resolve-cinewhoop-blade-count-and-heavy-lift-geometry-coverage",
				summary.nextRequiredAction());

		PropellerArchiveFitReadinessGate.PresetFitReadiness racing =
				findPreset(scenario.presets(), "racingQuad");
		assertTrue(racing.ctCpFitReady());
		assertTrue(racing.geometryFitReady());
		assertTrue(racing.simulationFitReady());
		assertEquals("playable-reference-review-required", racing.dominantBlocker());

		PropellerArchiveFitReadinessGate.PresetFitReadiness cine =
				findPreset(scenario.presets(), "cinewhoop");
		assertFalse(cine.bladeCountReady());
		assertTrue(cine.geometryMatchReady());
		assertFalse(cine.ctCpFitReady());
		assertEquals("blade-count-mismatch-review-required", cine.dominantBlocker());

		PropellerArchiveFitReadinessGate.PresetFitReadiness heavy =
				findPreset(scenario.presets(), "heavyLift");
		assertTrue(heavy.bladeCountReady());
		assertFalse(heavy.geometryMatchReady());
		assertTrue(heavy.ctCpFitReady());
		assertFalse(heavy.simulationFitReady());
		assertEquals("geometry-match-missing", heavy.dominantBlocker());
	}

	@Test
	void syntheticReviewedTargetOpensSimFitButKeepsRuntimeAndAutoApplyClosed() {
		PropellerArchiveFitReadinessGate.FitReadinessAudit audit =
				PropellerArchiveFitReadinessGate.audit();
		PropellerArchiveFitReadinessGate.FitReadinessScenario synthetic =
				find(audit.scenarios(), "synthetic_full_reviewed_fit_target");

		PropellerArchiveFitReadinessGate.FitReadinessSummary summary = synthetic.summary();
		assertTrue(summary.sourceBoundaryOpen());
		assertTrue(summary.coefficientFitReady());
		assertTrue(summary.geometryFitReady());
		assertTrue(summary.simulationFitReady());
		assertTrue(summary.playableReferenceHandoffReady());
		assertEquals(4, summary.ctCpFitReadyCount());
		assertEquals(4, summary.geometryFitReadyCount());
		assertEquals(4, summary.simulationFitReadyCount());
		assertEquals(4, summary.playableReferenceExportAllowedCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("ready-for-reviewed-sim-fit-packet", summary.dominantBlocker());

		assertTrue(synthetic.presets().stream()
				.allMatch(PropellerArchiveFitReadinessGate.PresetFitReadiness::simulationFitReady));
		assertTrue(synthetic.presets().stream()
				.noneMatch(PropellerArchiveFitReadinessGate.PresetFitReadiness::runtimeCouplingAllowed));
		assertTrue(synthetic.presets().stream()
				.noneMatch(PropellerArchiveFitReadinessGate.PresetFitReadiness::gameplayAutoApplyAllowed));

		assertEquals(3, audit.extrema().scenarioCount());
		assertEquals(12, audit.extrema().presetScenarioRowCount());
		assertEquals(1, audit.extrema().simulationFitReadyScenarioCount());
		assertEquals(1, audit.extrema().playableReferenceHandoffReadyScenarioCount());
		assertEquals(4, audit.extrema().maxCtCpFitReadyCount());
		assertEquals(4, audit.extrema().maxGeometryFitReadyCount());
		assertEquals(4, audit.extrema().maxSimulationFitReadyCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void scenarioRejectsMalformedInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveFitReadinessGate.scenario(
						"",
						true,
						true,
						true,
						true,
						true,
						"reviewed"
				));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveFitReadinessGate.scenario(
						"bad",
						true,
						true,
						true,
						true,
						true,
						""
				));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveFitReadinessGate.preset(
						"bad",
						null,
						true,
						true,
						true,
						true,
						true
				));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveFitReadinessGate.FitReadinessAudit audit =
				PropellerArchiveFitReadinessGate.audit();
		Path packet = findRepoRoot().resolve("docs/data/propeller_archive_fit_readiness_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_fit_readiness_scenario,current_triage_blocked,all,false,false,false,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_fit_readiness_preset,reviewed_imported_with_coverage_gaps,cinewhoop,true,true,true,false,true,false,true,true,true,true,false,true,false,false,false,false,blade-count-mismatch-review-required,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_fit_readiness_preset,reviewed_imported_with_coverage_gaps,heavyLift,true,true,true,false,true,true,false,true,true,true,true,false,false,false,false,false,geometry-match-missing,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_fit_readiness_summary,all,simulation_fit_ready_scenario_count,,,,,,,,,,,,,,,,,1,")));
	}

	private static PropellerArchiveFitReadinessGate.FitReadinessScenario find(
			List<PropellerArchiveFitReadinessGate.FitReadinessScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveFitReadinessGate.PresetFitReadiness findPreset(
			List<PropellerArchiveFitReadinessGate.PresetFitReadiness> presets,
			String name
	) {
		return presets.stream()
				.filter(preset -> name.equals(preset.presetName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_fit_readiness_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
