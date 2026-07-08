package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class PropellerArchiveDatasetTriageTest {
	@Test
	void auditClassifiesUserArchiveAsUsefulRotorDatasetWithoutRuntimeCoupling() {
		PropellerArchiveDatasetTriage.PropellerArchiveDatasetTriageAudit audit =
				PropellerArchiveDatasetTriage.audit();

		assertEquals("User-Propeller-Archive-Dataset-Triage-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("raw rows are not vendored"));
		assertEquals(51, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(22, audit.aggregateRowCount());
		assertEquals(6, audit.volumeSummaryRowCount());
		assertEquals(3, audit.bladeCountDistributionRowCount());
		assertEquals(4, audit.presetMatchRowCount());
		assertEquals(3, audit.geometryMatchRowCount());
		assertEquals(7, audit.boundaryRowCount());
		assertEquals(6, audit.archiveEntries().size());

		PropellerArchiveDatasetTriage.DatasetSummary summary = audit.datasetSummary();
		assertEquals(27_495, summary.experimentRowCount());
		assertEquals(2_316, summary.geometryRowCount());
		assertEquals(240, summary.uniquePropellerCount());
		assertEquals(226, summary.experimentBladeCount());
		assertEquals(120, summary.geometryBladeCount());
		assertEquals(32, summary.experimentFamilyCount());
		assertEquals(26, summary.geometryFamilyCount());
		assertEquals(1.2, summary.experimentDiameterMinInches(), 1.0e-12);
		assertEquals(19.0, summary.experimentDiameterMaxInches(), 1.0e-12);
		assertEquals(1.552, summary.experimentAdvanceRatioMax(), 1.0e-12);
		assertEquals(27_050.0, summary.experimentRpmMax(), 1.0e-12);
		assertEquals(-0.12614, summary.experimentCtMin(), 1.0e-12);
		assertEquals(0.253789, summary.experimentCtMax(), 1.0e-12);
		assertEquals(0.192791, summary.experimentCpMax(), 1.0e-12);
		assertEquals(0.840262, summary.experimentEfficiencyMax(), 1.0e-12);
		assertEquals(55.845, summary.geometryBetaMaxDegrees(), 1.0e-12);

		PropellerArchiveDatasetTriage.TriageBoundary boundary = audit.boundary();
		assertFalse(boundary.rawRowsVendored());
		assertTrue(boundary.sourceLicenseReviewRequired());
		assertFalse(boundary.directA4mcL2Evidence());
		assertTrue(boundary.rotorCtCpCurveFitCandidate());
		assertTrue(boundary.propGeometryFitCandidate());
		assertFalse(boundary.gameplayAutoApplyAllowed());
		assertEquals("review-source-license-then-fit-ct-cp-j-re-and-geometry-curves",
				boundary.nextRequiredAction());
	}

	@Test
	void presetMatchesCaptureCurrentRotorCalibrationAnchors() {
		PropellerArchiveDatasetTriage.PresetPerformanceMatch racing =
				PropellerArchiveDatasetTriage.presetMatch("racingQuad");
		assertEquals("da4052 5.0x3.75 - 3", racing.matchedPropName());
		assertEquals(3, racing.targetBladeCount());
		assertEquals(3, racing.matchedBladeCount());
		assertTrue(racing.bladeCountMatches());
		assertTrue(racing.geometryAvailable());
		assertEquals(63, racing.sampleRowCount());
		assertEquals(14, racing.staticRowCount());
		assertEquals(0.148290142857143, racing.staticCtMean(), 1.0e-15);
		assertEquals(0.103175285714286, racing.staticCpMean(), 1.0e-15);
		assertEquals(0.576493, racing.maxEfficiency(), 1.0e-12);

		PropellerArchiveDatasetTriage.PresetPerformanceMatch apDrone =
				PropellerArchiveDatasetTriage.presetMatch("apDrone");
		assertEquals(5.1, apDrone.targetDiameterInches(), 1.0e-12);
		assertEquals(4.5, apDrone.targetPitchInches(), 1.0e-12);
		assertEquals("same-five-inch-three-blade-anchor-but-under-pitched-for-current-target",
				apDrone.evidenceRole());
		assertTrue(apDrone.bladeCountMatches());

		PropellerArchiveDatasetTriage.PresetPerformanceMatch cine =
				PropellerArchiveDatasetTriage.presetMatch("cinewhoop");
		assertEquals("gwsdd 3.0x3.0 - 2", cine.matchedPropName());
		assertEquals(3, cine.targetBladeCount());
		assertEquals(2, cine.matchedBladeCount());
		assertFalse(cine.bladeCountMatches());
		assertTrue(cine.geometryAvailable());
		assertEquals(0.192041923076923, cine.staticCtMean(), 1.0e-15);
		assertEquals(0.680984, cine.maxEfficiency(), 1.0e-12);

		PropellerArchiveDatasetTriage.PresetPerformanceMatch lift =
				PropellerArchiveDatasetTriage.presetMatch("heavyLift");
		assertEquals("ancf 10.0x5.0 - 2", lift.matchedPropName());
		assertTrue(lift.bladeCountMatches());
		assertFalse(lift.geometryAvailable());
		assertEquals(0.0900960714285714, lift.staticCtMean(), 1.0e-15);
		assertEquals(0.0308062142857143, lift.staticCpMean(), 1.0e-15);
		assertEquals(0.679833, lift.maxEfficiency(), 1.0e-12);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveDatasetTriage.presetMatch("unknown"));
	}

	@Test
	void geometryMatchesExposeBladeShapeWhereArchiveHasRows() {
		PropellerArchiveDatasetTriage.GeometryStationMatch racing =
				PropellerArchiveDatasetTriage.geometryMatch("racingQuad");
		assertEquals("da4052 5.0x3.75", racing.matchedBladeName());
		assertEquals(18, racing.stationCount());
		assertEquals(0.7, racing.stationRadiusRatio(), 1.0e-12);
		assertEquals(0.2068, racing.chordToRadius(), 1.0e-12);
		assertEquals(20.912, racing.betaDegrees(), 1.0e-12);

		PropellerArchiveDatasetTriage.GeometryStationMatch cine =
				PropellerArchiveDatasetTriage.geometryMatch("cinewhoop");
		assertEquals("gwsdd 3.0x3.0", cine.matchedBladeName());
		assertEquals(0.2599, cine.chordToRadius(), 1.0e-12);
		assertEquals(25.05, cine.betaDegrees(), 1.0e-12);
		assertEquals("gwsdd 3.0x3.0",
				PropellerArchiveDatasetTriage.geometryMatchOrNull("cinewhoop").matchedBladeName());
		assertNull(PropellerArchiveDatasetTriage.geometryMatchOrNull("heavyLift"));

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveDatasetTriage.geometryMatch("heavyLift"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveDatasetTriage.PropellerArchiveDatasetTriageAudit audit =
				PropellerArchiveDatasetTriage.audit();
		Path packet = findRepoRoot().resolve("docs/data/propeller_archive_dataset_triage_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_dataset_aggregate,all,experiment_row_count,27495,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_dataset_preset_match,racingQuad,da4052 5.0x3.75 - 3,3,5.0,4.25,3,5.0,3.75,63,0.0,0.8128,1477.8,7946.7,14,0.148290142857143,0.103175285714286,0.576493,true,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_dataset_preset_match,cinewhoop,gwsdd 3.0x3.0 - 2,3,2.9921,2.5433,2,3.0,3.0,103,0.0,1.0674,2963.3,15063.0,13,0.192041923076923,0.143023846153846,0.680984,false,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_dataset_boundary,all,direct_a4mc_l2_evidence,false,boolean,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_dataset_triage_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
