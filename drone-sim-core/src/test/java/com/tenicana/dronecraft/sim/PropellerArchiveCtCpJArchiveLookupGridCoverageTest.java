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

class PropellerArchiveCtCpJArchiveLookupGridCoverageTest {
	@Test
	void auditRecordsArchiveTopologyAsScatteredRatherThanRectangularGrid() {
		PropellerArchiveCtCpJArchiveLookupGridCoverage.CtCpJArchiveLookupGridCoverageAudit audit =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Archive-Lookup-Grid-Coverage-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("direct rectangular CT/CP/J lookup binding is not enough"));
		assertEquals(42, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(4, audit.presetTopologyRowCount());
		assertEquals(16, audit.queryCoverageRowCount());
		assertEquals(14, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());

		PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridTopology racing =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.topology("racingQuad");
		assertEquals("da4052 5.0x3.75 - 3", racing.performanceMatchId());
		assertEquals(63, racing.performanceSampleRowCount());
		assertEquals(14, racing.staticSampleRowCount());
		assertEquals(49, racing.nonstaticSampleRowCount());
		assertEquals(18, racing.distinctRpmBinCount());
		assertEquals(4, racing.denseNonstaticRpmBinCount());
		assertEquals(14, racing.staticOnlyRpmBinCount());
		assertFalse(racing.directRectangularGridReadyAfterReview());
		assertTrue(racing.postReviewCtCpJLookupSeedAllowed());
		assertTrue(racing.postReviewFullSimulationLookupSeedAllowed());
		assertEquals("SCATTERED_FIT_REQUIRED", racing.status());

		PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridTopology cine =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.topology("cinewhoop");
		assertEquals(103, cine.performanceSampleRowCount());
		assertEquals(5, cine.denseNonstaticRpmBinCount());
		assertFalse(cine.postReviewCtCpJLookupSeedAllowed());
		assertEquals("COVERAGE_BLOCKED", cine.status());

		PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridTopology heavy =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.topology("heavyLift");
		assertEquals(128, heavy.performanceSampleRowCount());
		assertTrue(heavy.postReviewCtCpJLookupSeedAllowed());
		assertFalse(heavy.postReviewFullSimulationLookupSeedAllowed());
		assertEquals("PERFORMANCE_ONLY_SCATTERED_FIT_REQUIRED", heavy.status());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJArchiveLookupGridCoverage.topology("missing"));
	}

	@Test
	void queryRowsSeparateStaticAnchorsFromNonstaticScatteredFitRequirements() {
		PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridCoverageRow racingStatic =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.coverage("racingQuad", "static_anchor_low_rpm");
		assertTrue(racingStatic.insideLookupDomain());
		assertEquals(1, racingStatic.minimumPerformanceNeighborRows());
		assertEquals(2, racingStatic.availableRectangularNeighborRows());
		assertEquals(0, racingStatic.availableNonstaticNeighborRows());
		assertTrue(racingStatic.reviewedNeighborBindingReady());
		assertFalse(racingStatic.scatteredFitRequired());
		assertEquals("NEIGHBOR_BINDING_READY_AFTER_REVIEW", racingStatic.status());

		PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridCoverageRow racingMid =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.coverage("racingQuad", "mid_domain_mid_rpm");
		assertTrue(racingMid.insideLookupDomain());
		assertEquals(4, racingMid.minimumPerformanceNeighborRows());
		assertEquals(2, racingMid.availableRectangularNeighborRows());
		assertEquals(0, racingMid.availableNonstaticNeighborRows());
		assertFalse(racingMid.reviewedNeighborBindingReady());
		assertTrue(racingMid.scatteredFitRequired());
		assertEquals("RECTANGULAR_GRID_MISSING", racingMid.status());
		assertEquals("scattered-ct-cp-j-fit-required-before-lookup-binding", racingMid.message());

		PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridCoverageRow cineMid =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.coverage("cinewhoop", "mid_domain_mid_rpm");
		assertEquals(3, cineMid.availableRectangularNeighborRows());
		assertEquals(2, cineMid.availableNonstaticNeighborRows());
		assertFalse(cineMid.postReviewCtCpJLookupAllowed());
		assertFalse(cineMid.reviewedNeighborBindingReady());
		assertEquals("COVERAGE_BLOCKED", cineMid.status());

		PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridCoverageRow heavyStatic =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.coverage("heavyLift", "static_anchor_low_rpm");
		assertTrue(heavyStatic.reviewedNeighborBindingReady());
		assertTrue(heavyStatic.postReviewCtCpJLookupAllowed());
		assertFalse(heavyStatic.postReviewFullSimulationLookupAllowed());

		PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridCoverageRow extrapolated =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.coverage("apDrone", "high_j_extrapolation_probe");
		assertFalse(extrapolated.insideLookupDomain());
		assertEquals("EXTRAPOLATION_REJECTED", extrapolated.status());
		assertEquals("query-requires-extrapolation-and-is-rejected", extrapolated.message());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJArchiveLookupGridCoverage.coverage("racingQuad", "missing"));
	}

	@Test
	void summaryKeepsRuntimeClosedAndRequiresScatteredFitForNonstaticLookupBinding() {
		PropellerArchiveCtCpJArchiveLookupGridCoverage.ArchiveLookupGridCoverageSummary summary =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.audit().summary();

		assertEquals(4, summary.topologyPresetCount());
		assertEquals(16, summary.queryCoverageRowCount());
		assertEquals(12, summary.insideLookupDomainQueryCount());
		assertEquals(9, summary.postReviewCtCpJAllowedQueryCount());
		assertEquals(3, summary.reviewedNeighborBindingReadyQueryCount());
		assertEquals(2, summary.postReviewFullSimulationNeighborReadyQueryCount());
		assertEquals(6, summary.scatteredFitRequiredQueryCount());
		assertEquals(6, summary.rectangularNeighborMissingQueryCount());
		assertEquals(6, summary.nonstaticNeighborMissingQueryCount());
		assertEquals(4, summary.extrapolationRejectedQueryCount());
		assertEquals(3, summary.coverageBlockedQueryCount());
		assertEquals(3, summary.maxAvailableRectangularNeighborRows());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJArchiveLookupGridCoverage.CtCpJArchiveLookupGridCoverageAudit audit =
				PropellerArchiveCtCpJArchiveLookupGridCoverage.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_archive_lookup_grid_coverage_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_archive_lookup_grid_topology,racingQuad,topology,SCATTERED_FIT_REQUIRED,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_archive_lookup_grid_query,racingQuad,mid_domain_mid_rpm,RECTANGULAR_GRID_MISSING,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_archive_lookup_grid_query,heavyLift,static_anchor_low_rpm,NEIGHBOR_BINDING_READY_AFTER_REVIEW,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_archive_lookup_grid_summary,all,reviewed_neighbor_binding_ready_query_count,3,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_archive_lookup_grid_summary,all,runtime_coupling_allowed_count,0,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_archive_lookup_grid_coverage_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
