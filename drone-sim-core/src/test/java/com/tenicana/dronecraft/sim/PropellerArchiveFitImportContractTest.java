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

class PropellerArchiveFitImportContractTest {
	@Test
	void auditDefinesReviewedOfflineImportSchemaWithoutRuntimeCoupling() {
		PropellerArchiveFitImportContract.FitImportContractAudit audit =
				PropellerArchiveFitImportContract.audit();

		assertEquals("User-Propeller-Archive-Fit-Import-Contract-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("reviewed offline schema"));
		assertEquals(55, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(26, audit.fieldContractRowCount());
		assertEquals(4, audit.presetInputRowCount());
		assertEquals(8, audit.importStageRowCount());
		assertEquals(10, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(26, audit.fields().size());
		assertEquals(4, audit.presets().size());
		assertEquals(8, audit.stages().size());

		PropellerArchiveFitImportContract.ImportContractSummary summary = audit.summary();
		assertEquals(26, summary.fieldContractCount());
		assertEquals(4, summary.presetInputContractCount());
		assertEquals(8, summary.importStageCount());
		assertFalse(summary.currentRawImportAllowed());
		assertTrue(summary.reviewedRawImportContractReady());
		assertFalse(summary.reviewedFitInputReady());
		assertTrue(summary.syntheticFitInputReady());
		assertEquals(2, summary.postReviewCoverageBlockerPresetCount());
		assertEquals(0, summary.runtimeCouplingAllowedPresetCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedPresetCount());
		assertEquals("review-source-license-before-importing-raw-rows", summary.nextRequiredAction());
	}

	@Test
	void fieldContractsPinHeadersUnitsAndFitUses() {
		PropellerArchiveFitImportContract.ImportFieldContract ct =
				PropellerArchiveFitImportContract.field("performance", "thrust_coefficient");
		assertEquals("CT", ct.sourceFieldName());
		assertEquals("coefficient", ct.unit());
		assertTrue(ct.required());
		assertEquals("rotor thrust coefficient fit", ct.fitUse());

		PropellerArchiveFitImportContract.ImportFieldContract beta =
				PropellerArchiveFitImportContract.field("geometry", "beta_degrees");
		assertEquals("beta", beta.sourceFieldName());
		assertEquals("deg", beta.unit());
		assertEquals("blade pitch-angle fit", beta.fitUse());

		PropellerArchiveFitImportContract.ImportFieldContract target =
				PropellerArchiveFitImportContract.field("fit_target", "coverage_blocker");
		assertEquals("coverageBlocker", target.sourceFieldName());
		assertEquals("known-blocker-or-none", target.validationRule());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveFitImportContract.field("performance", "missing"));
	}

	@Test
	void presetContractsExposePostReviewCoverageGapsBeforeFitInputIsReady() {
		PropellerArchiveFitImportContract.PresetFitInputContract racing =
				PropellerArchiveFitImportContract.preset("racingQuad");
		assertEquals("da4052 5.0x3.75 - 3", racing.performanceMatchId());
		assertEquals("da4052 5.0x3.75", racing.geometryMatchId());
		assertEquals(63, racing.performanceSampleRowCount());
		assertEquals(18, racing.geometryStationCount());
		assertFalse(racing.currentImportAllowed());
		assertTrue(racing.performanceSchemaReadyAfterReview());
		assertTrue(racing.bladeCountCoverageReadyAfterReview());
		assertTrue(racing.geometryCoverageReadyAfterReview());
		assertTrue(racing.fitInputReadyAfterReview());
		assertEquals("ready-for-reviewed-fit-import", racing.postReviewNextRequiredAction());

		PropellerArchiveFitImportContract.PresetFitInputContract cine =
				PropellerArchiveFitImportContract.preset("cinewhoop");
		assertEquals(3, cine.targetBladeCount());
		assertEquals(2, cine.matchedBladeCount());
		assertTrue(cine.performanceSchemaReadyAfterReview());
		assertFalse(cine.bladeCountCoverageReadyAfterReview());
		assertTrue(cine.geometryCoverageReadyAfterReview());
		assertFalse(cine.fitInputReadyAfterReview());
		assertTrue(cine.fitInputReadyInSyntheticTarget());
		assertEquals("resolve-cinewhoop-three-blade-coverage-or-correction",
				cine.postReviewNextRequiredAction());

		PropellerArchiveFitImportContract.PresetFitInputContract heavy =
				PropellerArchiveFitImportContract.preset("heavyLift");
		assertEquals("missing-reviewed-geometry-match", heavy.geometryMatchId());
		assertEquals(0, heavy.geometryStationCount());
		assertTrue(heavy.performanceSchemaReadyAfterReview());
		assertTrue(heavy.bladeCountCoverageReadyAfterReview());
		assertFalse(heavy.geometryCoverageReadyAfterReview());
		assertFalse(heavy.fitInputReadyAfterReview());
		assertEquals("add-heavy-lift-geometry-match-or-reviewed-surrogate",
				heavy.postReviewNextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveFitImportContract.preset("unknown"));
	}

	@Test
	void stageContractsKeepCurrentImportBlockedButDefineReviewedAndSyntheticTargets() {
		PropellerArchiveFitImportContract.ImportStageContract source =
				PropellerArchiveFitImportContract.stage("source_license_review");
		assertTrue(source.required());
		assertFalse(source.currentSatisfied());
		assertTrue(source.reviewedImportSatisfied());
		assertTrue(source.syntheticTargetSatisfied());
		assertEquals("review-source-license-before-importing-raw-rows", source.nextRequiredAction());

		PropellerArchiveFitImportContract.ImportStageContract coverage =
				PropellerArchiveFitImportContract.stage("preset_coverage_resolution");
		assertFalse(coverage.currentSatisfied());
		assertFalse(coverage.reviewedImportSatisfied());
		assertTrue(coverage.syntheticTargetSatisfied());

		PropellerArchiveFitImportContract.ImportStageContract guard =
				PropellerArchiveFitImportContract.stage("runtime_leak_guard");
		assertTrue(guard.currentSatisfied());
		assertTrue(guard.reviewedImportSatisfied());
		assertTrue(guard.syntheticTargetSatisfied());
		assertEquals("keep-runtime-coupling-and-gameplay-auto-apply-closed", guard.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveFitImportContract.stage("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveFitImportContract.FitImportContractAudit audit =
				PropellerArchiveFitImportContract.audit();
		Path packet = findRepoRoot().resolve("docs/data/propeller_archive_fit_import_contract_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_fit_import_field,performance,thrust_coefficient,CT,coefficient,CT,thrust_coefficient,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_fit_import_preset,cinewhoop,fit_input_ready_after_review,false,boolean,gwsdd 3.0x3.0 - 2,gwsdd 3.0x3.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_fit_import_preset,heavyLift,geometry_station_count,0,count,ancf 10.0x5.0 - 2,missing-reviewed-geometry-match,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_fit_import_summary,all,post_review_coverage_blocker_preset_count,2,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_fit_import_contract_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
