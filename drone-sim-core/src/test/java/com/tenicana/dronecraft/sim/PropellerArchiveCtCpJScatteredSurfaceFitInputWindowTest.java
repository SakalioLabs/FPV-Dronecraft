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

class PropellerArchiveCtCpJScatteredSurfaceFitInputWindowTest {
	@Test
	void auditBuildsCompactFitInputWindowsFromArchiveTopology() {
		PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.CtCpJScatteredSurfaceFitInputWindowAudit audit =
				PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Scattered-Surface-Fit-Input-Window-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("compact archive topology and curve-shape evidence"));
		assertEquals(32, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(9, audit.windowRowCount());
		assertEquals(15, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(9, audit.rows().size());

		PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.ScatteredSurfaceFitInputWindowRow racingStatic =
				PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.window(
						"racingQuad", "static_anchor_low_rpm");
		assertEquals("DIRECT_STATIC_ANCHOR", racingStatic.fitInputWindowKind());
		assertEquals(63, racingStatic.archiveSampleRowCount());
		assertEquals(14, racingStatic.archiveStaticSampleRowCount());
		assertEquals(49, racingStatic.archiveNonstaticSampleRowCount());
		assertEquals(18, racingStatic.archiveDistinctRpmBinCount());
		assertTrue(racingStatic.directStaticAnchorBinding());
		assertFalse(racingStatic.scatteredSurfaceFitRequired());
		assertTrue(racingStatic.archiveCurveShapeGuardPassed());
		assertEquals(5, racingStatic.negativeThrustTailRowCount());
		assertFalse(racingStatic.sourceRowsReviewed());
		assertFalse(racingStatic.fitInputWindowReady());
		assertEquals("source-review-blocks-static-anchor-input-window", racingStatic.message());

		PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.ScatteredSurfaceFitInputWindowRow racingMid =
				PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.window(
						"racingQuad", "mid_domain_mid_rpm");
		assertEquals("SCATTERED_SURFACE_FIT", racingMid.fitInputWindowKind());
		assertEquals(4, racingMid.minimumPerformanceNeighborRows());
		assertEquals(2, racingMid.availableRectangularNeighborRows());
		assertEquals(0, racingMid.availableNonstaticNeighborRows());
		assertFalse(racingMid.directStaticAnchorBinding());
		assertTrue(racingMid.scatteredSurfaceFitRequired());
		assertEquals("source-review-blocks-scattered-fit-input-window", racingMid.message());

		PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.ScatteredSurfaceFitInputWindowRow heavy =
				PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.window(
						"heavyLift", "high_domain_max_rpm");
		assertEquals(128, heavy.archiveSampleRowCount());
		assertEquals(114, heavy.archiveNonstaticSampleRowCount());
		assertEquals(20, heavy.archiveDistinctRpmBinCount());
		assertFalse(heavy.postReviewFullSimulationLookupAllowed());
		assertEquals(44, heavy.negativeThrustTailRowCount());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.window("cinewhoop",
						"static_anchor_low_rpm"));
	}

	@Test
	void summaryKeepsInputWindowsReviewBlockedAndRuntimeClosed() {
		PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.ScatteredSurfaceFitInputWindowSummary summary =
				PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.audit().summary();

		assertEquals(9, summary.windowRowCount());
		assertEquals(3, summary.directStaticAnchorWindowCount());
		assertEquals(6, summary.scatteredSurfaceFitRequiredWindowCount());
		assertEquals(6, summary.postReviewFullSimulationWindowCount());
		assertEquals(3, summary.postReviewPerformanceOnlyWindowCount());
		assertEquals(9, summary.archiveCurveShapeGuardReadyWindowCount());
		assertEquals(9, summary.negativeThrustTailWindowCount());
		assertEquals(0, summary.sourceRowsReviewedWindowCount());
		assertEquals(0, summary.fitInputWindowReadyCount());
		assertEquals(128, summary.maxArchiveSampleRowCount());
		assertEquals(14, summary.maxArchiveStaticSampleRowCount());
		assertEquals(114, summary.maxArchiveNonstaticSampleRowCount());
		assertEquals(20, summary.maxArchiveDistinctRpmBinCount());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
	}

	@Test
	void scatteredSurfaceFitContractConsumesInputWindowTargets() {
		PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.ScatteredSurfaceFitInputWindowRow window =
				PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.window(
						"apDrone", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitTarget target =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.target(
						"apDrone", "mid_domain_mid_rpm");

		assertEquals(window.performanceMatchId(), target.performanceMatchId());
		assertEquals(window.queryAdvanceRatioJ(), target.queryAdvanceRatioJ());
		assertEquals(window.queryRpm(), target.queryRpm());
		assertEquals(window.minimumPerformanceNeighborRows(), target.minimumPerformanceNeighborRows());
		assertEquals(window.availableRectangularNeighborRows(), target.availableRectangularNeighborRows());
		assertEquals(window.availableNonstaticNeighborRows(), target.availableNonstaticNeighborRows());
		assertEquals(window.scatteredSurfaceFitRequired(), target.scatteredFitRequired());
		assertEquals(window.archiveMaxEtaFormulaResidual(), target.archiveMaxEtaFormulaResidual());
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.CtCpJScatteredSurfaceFitInputWindowAudit audit =
				PropellerArchiveCtCpJScatteredSurfaceFitInputWindow.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_scattered_surface_fit_input_window_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_fit_input_window,racingQuad,static_anchor_low_rpm,BLOCKED,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_fit_input_window,racingQuad,mid_domain_mid_rpm,BLOCKED,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_fit_input_window_summary,all,scattered_surface_fit_required_window_count,BLOCKED,scattered_surface_fit_required_window_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_fit_input_window_summary,all,max_archive_sample_row_count,BLOCKED,max_archive_sample_row_count,128,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_fit_input_window_summary,all,fit_input_window_ready_count,BLOCKED,fit_input_window_ready_count,0,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_scattered_surface_fit_input_window_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
