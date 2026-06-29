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

class PropellerArchiveCompactReferenceHandoffTest {
	@Test
	void auditBuildsStableCompactReferenceHandoffScenarios() {
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffAudit audit =
				PropellerArchiveCompactReferenceHandoff.audit();

		assertEquals("User-Propeller-Archive-Compact-Reference-Handoff-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("playable reference material"));
		assertEquals(96, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(12, audit.referenceFieldRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(17, audit.scenarioMetricRowCount());
		assertEquals(9, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(12, audit.fields().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary current =
				find(audit.scenarios(), "current_acceptance_blocked").summary();
		assertFalse(current.curveFitAcceptanceReady());
		assertFalse(current.compactReferenceReviewed());
		assertEquals(4, current.expectedPresetCount());
		assertEquals(6, current.expectedCurveFamilyCount());
		assertEquals(12, current.observedReferenceFieldCount());
		assertEquals(0, current.acceptedCurveTargetCount());
		assertEquals(24, current.blockedCurveTargetCount());
		assertFalse(current.referenceMaterialExportAllowed());
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals("curve-fit-acceptance-not-ready", current.message());

		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary reviewMissing =
				find(audit.scenarios(), "acceptance_ready_reference_review_missing").summary();
		assertTrue(reviewMissing.curveFitAcceptanceReady());
		assertFalse(reviewMissing.compactReferenceReviewed());
		assertEquals(24, reviewMissing.acceptedCurveTargetCount());
		assertEquals(0, reviewMissing.blockedCurveTargetCount());
		assertFalse(reviewMissing.referenceMaterialExportAllowed());
		assertEquals("compact-reference-review-missing", reviewMissing.message());

		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary ready =
				find(audit.scenarios(), "acceptance_ready_reference_reviewed").summary();
		assertTrue(ready.curveFitAcceptanceReady());
		assertTrue(ready.compactReferenceReviewed());
		assertTrue(ready.allReferenceFieldsPresent());
		assertTrue(ready.referenceMaterialExportAllowed());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("compact-propeller-ct-cp-eta-geometry-reference", ready.referencePayloadKind());
		assertEquals("READY", ready.status());
		assertEquals("compact-reference-material-ready", ready.message());

		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary failed =
				find(audit.scenarios(), "reference_reviewed_acceptance_failed").summary();
		assertFalse(failed.curveFitAcceptanceReady());
		assertEquals(23, failed.acceptedCurveTargetCount());
		assertEquals(1, failed.blockedCurveTargetCount());
		assertFalse(failed.referenceMaterialExportAllowed());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(1, audit.extrema().referenceMaterialExportAllowedCount());
		assertEquals(24, audit.extrema().maxAcceptedCurveTargetCount());
		assertEquals(24, audit.extrema().maxBlockedCurveTargetCount());
		assertEquals(12, audit.extrema().maxObservedReferenceFieldCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void fieldsDefinePlayableReferencePayloadWithoutAutoApply() {
		PropellerArchiveCompactReferenceHandoff.CompactReferenceField source =
				PropellerArchiveCompactReferenceHandoff.field("source_archive_sha256");
		assertEquals("sha256", source.unit());
		assertTrue(source.required());
		assertTrue(source.source().contains("PropellerArchiveSourceFingerprint"));
		assertTrue(source.playableUse().contains("source data identity"));
		assertFalse(source.runtimeCouplingAllowed());
		assertFalse(source.gameplayAutoApplyAllowed());

		PropellerArchiveCompactReferenceHandoff.CompactReferenceField ct =
				PropellerArchiveCompactReferenceHandoff.field("ct_curve_reference");
		assertEquals("coefficient", ct.unit());
		assertTrue(ct.playableUse().contains("thrust feel reference"));

		PropellerArchiveCompactReferenceHandoff.CompactReferenceField beta =
				PropellerArchiveCompactReferenceHandoff.field("beta_distribution_reference");
		assertEquals("deg", beta.unit());
		assertTrue(beta.source().contains("blade twist"));

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCompactReferenceHandoff.field("missing"));
	}

	@Test
	void handoffRequiresAcceptedFitReviewAndCompleteSchema() {
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary acceptedReviewed =
				findAcceptance("synthetic_all_curve_targets_pass");
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary ready =
				PropellerArchiveCompactReferenceHandoff.handoff(
						acceptedReviewed,
						PropellerArchiveCompactReferenceHandoff.audit().fields(),
						"ready-reference");
		assertTrue(ready.referenceMaterialExportAllowed());

		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary acceptedReviewMissing =
				acceptedWithoutReferenceReview();
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary reviewMissing =
				PropellerArchiveCompactReferenceHandoff.handoff(
						acceptedReviewMissing,
						PropellerArchiveCompactReferenceHandoff.audit().fields(),
						"review-missing");
		assertFalse(reviewMissing.referenceMaterialExportAllowed());
		assertEquals("compact-reference-review-missing", reviewMissing.message());

		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary schemaMissing =
				PropellerArchiveCompactReferenceHandoff.handoff(
						acceptedReviewed,
						PropellerArchiveCompactReferenceHandoff.audit().fields().subList(0, 11),
						"schema-missing");
		assertFalse(schemaMissing.referenceMaterialExportAllowed());
		assertEquals("compact-reference-schema-incomplete", schemaMissing.message());

		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary current =
				findAcceptance("current_no_reviewed_import_no_results");
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary blocked =
				PropellerArchiveCompactReferenceHandoff.handoff(
						current,
						PropellerArchiveCompactReferenceHandoff.audit().fields(),
						"blocked");
		assertFalse(blocked.referenceMaterialExportAllowed());
		assertEquals("curve-fit-acceptance-not-ready", blocked.message());
	}

	@Test
	void handoffRejectsInvalidInputs() {
		PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary accepted =
				findAcceptance("synthetic_all_curve_targets_pass");

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCompactReferenceHandoff.handoff(
						null, PropellerArchiveCompactReferenceHandoff.audit().fields(), "runtime"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCompactReferenceHandoff.handoff(accepted, null, "runtime"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCompactReferenceHandoff.handoff(
						accepted, PropellerArchiveCompactReferenceHandoff.audit().fields(), ""));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCompactReferenceHandoff.handoff(
						accepted,
						List.of(new PropellerArchiveCompactReferenceHandoff.CompactReferenceField(
								"", "text", true, "source", "use", false, false)),
						"runtime"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffAudit audit =
				PropellerArchiveCompactReferenceHandoff.audit();
		Path packet = findRepoRoot().resolve("docs/data/propeller_archive_compact_reference_handoff_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_compact_reference_handoff_summary,all_scenarios,reference_material_export_allowed_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_compact_reference_handoff_field,ct_curve_reference,unit,coefficient,text,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_compact_reference_handoff_scenario,current_acceptance_blocked,reference_material_export_allowed,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_compact_reference_handoff_scenario,acceptance_ready_reference_reviewed,reference_material_export_allowed,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_compact_reference_handoff_summary,all_scenarios,runtime_coupling_allowed_count,0,count,")));
	}

	private static PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffScenario find(
			List<PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary findAcceptance(String name) {
		return PropellerArchiveCurveFitAcceptanceGate.audit().scenarios().stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceSummary acceptedWithoutReferenceReview() {
		List<PropellerArchiveCurveFitAcceptanceGate.CurveFitAcceptanceTarget> targets =
				PropellerArchiveCurveFitAcceptanceGate.targets();
		return PropellerArchiveCurveFitAcceptanceGate.gate(
				true,
				true,
				true,
				false,
				targets,
				targets.stream()
						.map(target -> PropellerArchiveCurveFitAcceptanceGate.result(
								target.presetName(),
								target.curveName(),
								target.maxWeightedRmse() * 0.5,
								target.maxAnchorError() * 0.5,
								target.minValidationRowCount() + 2,
								0
						))
						.toList(),
				"accepted-reference-review-missing"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_compact_reference_handoff_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
