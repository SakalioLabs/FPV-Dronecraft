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

class PropellerArchiveFitBlockerReportTest {
	@Test
	void auditDecomposesCurrentSourceBoundaryAndCoverageBlockers() {
		PropellerArchiveFitBlockerReport.FitBlockerAudit audit =
				PropellerArchiveFitBlockerReport.audit();

		assertEquals("User-Propeller-Archive-Fit-Blocker-Report-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("source, coverage, fitting"));
		assertEquals(34, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(3, audit.scenarioRowCount());
		assertEquals(12, audit.presetRowCount());
		assertEquals(12, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());

		PropellerArchiveFitBlockerReport.FitBlockerScenario current =
				find(audit.scenarios(), "current_triage_blocked");
		PropellerArchiveFitBlockerReport.FitBlockerScenarioSummary summary = current.summary();
		assertEquals(8, summary.blockerCount());
		assertTrue(summary.sourceLicenseReviewBlocker());
		assertTrue(summary.rawRowImportBlocker());
		assertTrue(summary.datasetFitReviewBlocker());
		assertTrue(summary.bladeCountCoverageBlocker());
		assertTrue(summary.geometryCoverageBlocker());
		assertTrue(summary.ctCpFitBlocker());
		assertTrue(summary.geometryFitBlocker());
		assertTrue(summary.simulationFitBlocker());
		assertFalse(summary.playableReferenceReviewBlocker());
		assertEquals(4, summary.blockedPresetCount());
		assertEquals(0, summary.simulationFitReadyPresetCount());
		assertEquals("review-source-license-before-importing-raw-rows", summary.nextRequiredAction());
		assertEquals("BLOCKED", summary.status());
	}

	@Test
	void reviewedImportedScenarioSeparatesCinewhoopAndHeavyLiftCoverageGaps() {
		PropellerArchiveFitBlockerReport.FitBlockerScenario reviewed =
				find(PropellerArchiveFitBlockerReport.audit().scenarios(),
						"reviewed_imported_with_coverage_gaps");

		PropellerArchiveFitBlockerReport.FitBlockerScenarioSummary summary = reviewed.summary();
		assertEquals(5, summary.blockerCount());
		assertFalse(summary.sourceLicenseReviewBlocker());
		assertFalse(summary.rawRowImportBlocker());
		assertFalse(summary.datasetFitReviewBlocker());
		assertTrue(summary.bladeCountCoverageBlocker());
		assertTrue(summary.geometryCoverageBlocker());
		assertTrue(summary.ctCpFitBlocker());
		assertTrue(summary.geometryFitBlocker());
		assertTrue(summary.simulationFitBlocker());
		assertEquals(2, summary.simulationFitReadyPresetCount());
		assertEquals(0, summary.playableReferenceExportAllowedPresetCount());
		assertEquals("resolve-cinewhoop-three-blade-coverage-or-correction",
				summary.nextRequiredAction());

		PropellerArchiveFitBlockerReport.FitBlockerPreset racing =
				findPreset(reviewed.presets(), "racingQuad");
		assertEquals(1, racing.blockerCount());
		assertTrue(racing.simulationFitReady());
		assertTrue(racing.playableReferenceReviewBlocker());
		assertEquals("review-compact-reference-before-playable-handoff",
				racing.nextRequiredAction());

		PropellerArchiveFitBlockerReport.FitBlockerPreset cine =
				findPreset(reviewed.presets(), "cinewhoop");
		assertEquals(3, cine.blockerCount());
		assertTrue(cine.bladeCountCoverageBlocker());
		assertFalse(cine.geometryCoverageBlocker());
		assertTrue(cine.ctCpFitBlocker());
		assertFalse(cine.geometryFitBlocker());
		assertTrue(cine.simulationFitBlocker());
		assertEquals("resolve-cinewhoop-three-blade-coverage-or-correction",
				cine.nextRequiredAction());

		PropellerArchiveFitBlockerReport.FitBlockerPreset heavy =
				findPreset(reviewed.presets(), "heavyLift");
		assertEquals(3, heavy.blockerCount());
		assertFalse(heavy.bladeCountCoverageBlocker());
		assertTrue(heavy.geometryCoverageBlocker());
		assertFalse(heavy.ctCpFitBlocker());
		assertTrue(heavy.geometryFitBlocker());
		assertTrue(heavy.simulationFitBlocker());
		assertEquals("add-heavy-lift-geometry-match-or-reviewed-surrogate",
				heavy.nextRequiredAction());
	}

	@Test
	void syntheticFullReviewedTargetClearsFitBlockersWithoutRuntimeLeaks() {
		PropellerArchiveFitBlockerReport.FitBlockerAudit audit =
				PropellerArchiveFitBlockerReport.audit();
		PropellerArchiveFitBlockerReport.FitBlockerScenario synthetic =
				find(audit.scenarios(), "synthetic_full_reviewed_fit_target");

		assertEquals(0, synthetic.summary().blockerCount());
		assertEquals("READY", synthetic.summary().status());
		assertEquals("fit-readiness-blockers-clear", synthetic.summary().nextRequiredAction());
		assertEquals(4, synthetic.summary().simulationFitReadyPresetCount());
		assertEquals(4, synthetic.summary().playableReferenceExportAllowedPresetCount());
		assertTrue(synthetic.presets().stream()
				.allMatch(preset -> preset.blockerCount() == 0));
		assertTrue(synthetic.presets().stream()
				.noneMatch(PropellerArchiveFitBlockerReport.FitBlockerPreset::runtimeCouplingLeakBlocker));
		assertTrue(synthetic.presets().stream()
				.noneMatch(PropellerArchiveFitBlockerReport.FitBlockerPreset::gameplayAutoApplyLeakBlocker));

		assertEquals(3, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(2, audit.extrema().blockedScenarioCount());
		assertEquals(8, audit.extrema().maxScenarioBlockerCount());
		assertEquals(1, audit.extrema().sourceLicenseReviewBlockerScenarioCount());
		assertEquals(1, audit.extrema().rawRowImportBlockerScenarioCount());
		assertEquals(1, audit.extrema().datasetFitReviewBlockerScenarioCount());
		assertEquals(2, audit.extrema().bladeCoverageBlockerScenarioCount());
		assertEquals(2, audit.extrema().geometryCoverageBlockerScenarioCount());
		assertEquals(2, audit.extrema().playableReferenceReviewBlockerPresetCount());
		assertEquals(0, audit.extrema().runtimeCouplingLeakScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyLeakScenarioCount());
	}

	@Test
	void reportRejectsMalformedInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveFitBlockerReport.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveFitBlockerReport.scenario(null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveFitBlockerReport.preset(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveFitBlockerReport.FitBlockerAudit audit =
				PropellerArchiveFitBlockerReport.audit();
		Path packet = findRepoRoot().resolve("docs/data/propeller_archive_fit_blocker_report_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_fit_blocker_scenario,current_triage_blocked,all,8,true,true,true,true,true,true,true,true,false,false,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_fit_blocker_preset,reviewed_imported_with_coverage_gaps,cinewhoop,3,false,false,false,true,false,true,false,true,false,false,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_fit_blocker_preset,reviewed_imported_with_coverage_gaps,heavyLift,3,false,false,false,false,true,false,true,true,false,false,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_fit_blocker_summary,all,max_scenario_blocker_count,8,")));
	}

	private static PropellerArchiveFitBlockerReport.FitBlockerScenario find(
			List<PropellerArchiveFitBlockerReport.FitBlockerScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveFitBlockerReport.FitBlockerPreset findPreset(
			List<PropellerArchiveFitBlockerReport.FitBlockerPreset> presets,
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
							"docs/data/propeller_archive_fit_blocker_report_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
