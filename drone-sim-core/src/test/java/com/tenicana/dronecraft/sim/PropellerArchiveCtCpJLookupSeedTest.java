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

class PropellerArchiveCtCpJLookupSeedTest {
	@Test
	void auditDefinesOfflineLookupSeedWithoutRuntimeCoupling() {
		PropellerArchiveCtCpJLookupSeed.CtCpJLookupSeedAudit audit =
				PropellerArchiveCtCpJLookupSeed.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Seed-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("offline lookup payload"));
		assertEquals(46, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(13, audit.lookupFieldRowCount());
		assertEquals(4, audit.presetSeedRowCount());
		assertEquals(7, audit.lookupStageRowCount());
		assertEquals(14, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());

		PropellerArchiveCtCpJLookupSeed.LookupSeedSummary summary = audit.summary();
		assertEquals(13, summary.lookupFieldCount());
		assertEquals(4, summary.presetSeedCount());
		assertEquals(0, summary.currentLookupSeedAllowedCount());
		assertEquals(3, summary.postReviewCtCpJLookupSeedAllowedCount());
		assertEquals(2, summary.postReviewFullSimulationLookupSeedAllowedCount());
		assertEquals(0, summary.compactReferenceExportAllowedAfterReviewCount());
		assertEquals(0, summary.runtimeCouplingAllowedPresetCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedPresetCount());
		assertFalse(summary.currentRawImportAllowed());
		assertTrue(summary.reviewedRawImportContractReady());
		assertFalse(summary.fullLookupRuntimeReady());
		assertFalse(summary.playableReferenceAllowed());
		assertEquals(PropellerArchiveSourceFingerprint.ARCHIVE_SHA256, summary.sourceArchiveSha256());
		assertEquals("review-source-license-then-import-reviewed-ct-cp-j-rows-into-offline-lookup",
				summary.nextRequiredAction());
	}

	@Test
	void lookupFieldsAndStagesCaptureBoundaries() {
		PropellerArchiveCtCpJLookupSeed.LookupField ct =
				PropellerArchiveCtCpJLookupSeed.field("ct_lookup_curve");
		assertEquals("coefficient", ct.unit());
		assertEquals("accepted CT(J,rpm) fit", ct.source());
		assertTrue(ct.downstreamUse().contains("thrust coefficient"));
		assertFalse(ct.runtimeCouplingAllowed());
		assertFalse(ct.gameplayAutoApplyAllowed());

		PropellerArchiveCtCpJLookupSeed.LookupField mu =
				PropellerArchiveCtCpJLookupSeed.field("equivalent_project_mu");
		assertEquals("ratio", mu.unit());
		assertTrue(mu.source().contains("J/pi"));

		PropellerArchiveCtCpJLookupSeed.LookupStage seed =
				PropellerArchiveCtCpJLookupSeed.stage("lookup_seed_generation");
		assertFalse(seed.currentSatisfied());
		assertTrue(seed.reviewedImportSatisfied());
		assertTrue(seed.syntheticTargetSatisfied());

		PropellerArchiveCtCpJLookupSeed.LookupStage guard =
				PropellerArchiveCtCpJLookupSeed.stage("runtime_leak_guard");
		assertTrue(guard.currentSatisfied());
		assertTrue(guard.reviewedImportSatisfied());
		assertTrue(guard.syntheticTargetSatisfied());
		assertEquals("keep-runtime-coupling-and-gameplay-auto-apply-closed", guard.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupSeed.field("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupSeed.stage("missing"));
	}

	@Test
	void presetSeedsExposeCurrentSourceBlockerAndPostReviewCoverage() {
		PropellerArchiveCtCpJLookupSeed.PresetLookupSeed racing =
				PropellerArchiveCtCpJLookupSeed.preset("racingQuad");
		assertEquals("da4052 5.0x3.75 - 3", racing.performanceMatchId());
		assertEquals("da4052 5.0x3.75", racing.geometryMatchId());
		assertEquals(0.0, racing.advanceRatioMin());
		assertEquals(0.8128, racing.advanceRatioMax());
		assertEquals(1_477.8, racing.rpmMin());
		assertEquals(7_946.7, racing.rpmMax());
		assertEquals(0.148290142857143, racing.staticCtMean());
		assertEquals(0.103175285714286, racing.staticCpMean());
		assertFalse(racing.currentLookupSeedAllowed());
		assertTrue(racing.postReviewCtCpJLookupSeedAllowed());
		assertTrue(racing.postReviewFullSimulationLookupSeedAllowed());
		assertEquals("source-license-review-required", racing.currentBlocker());
		assertEquals("none", racing.postReviewBlocker());

		PropellerArchiveCtCpJLookupSeed.PresetLookupSeed cine =
				PropellerArchiveCtCpJLookupSeed.preset("cinewhoop");
		assertFalse(cine.postReviewCtCpJLookupSeedAllowed());
		assertFalse(cine.postReviewFullSimulationLookupSeedAllowed());
		assertEquals("blade-count-coverage-missing", cine.postReviewBlocker());
		assertEquals("resolve-cinewhoop-three-blade-coverage-or-correction",
				cine.postReviewNextRequiredAction());

		PropellerArchiveCtCpJLookupSeed.PresetLookupSeed heavy =
				PropellerArchiveCtCpJLookupSeed.preset("heavyLift");
		assertTrue(heavy.postReviewCtCpJLookupSeedAllowed());
		assertFalse(heavy.postReviewFullSimulationLookupSeedAllowed());
		assertEquals("missing-reviewed-geometry-match", heavy.geometryMatchId());
		assertEquals("geometry-fit-input-missing", heavy.postReviewBlocker());
		assertEquals("add-heavy-lift-geometry-match-or-reviewed-surrogate",
				heavy.postReviewNextRequiredAction());
		assertFalse(heavy.runtimeCouplingAllowed());
		assertFalse(heavy.gameplayAutoApplyAllowed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupSeed.preset("unknown"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupSeed.CtCpJLookupSeedAudit audit =
				PropellerArchiveCtCpJLookupSeed.audit();
		Path packet = findRepoRoot().resolve("docs/data/propeller_archive_ct_cp_j_lookup_seed_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_field,ct_lookup_curve,unit,coefficient,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_preset,racingQuad,da4052 5.0x3.75 - 3,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_preset,cinewhoop,gwsdd 3.0x3.0 - 2,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_summary,all,post_review_ct_cp_j_lookup_seed_allowed_count,3,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_summary,all,full_lookup_runtime_ready,false,boolean,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_seed_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
