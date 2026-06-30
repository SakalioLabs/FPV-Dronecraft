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

class PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoffTest {
	@Test
	void auditBuildsExecutionInputHandoffScenarios() {
		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.CtCpJScatteredSurfaceFitExecutionHandoffAudit audit =
				PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Scattered-Surface-Fit-Execution-Handoff-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("lookup-execution input shape"));
		assertEquals(48, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(14, audit.executionInputFieldRowCount());
		assertEquals(9, audit.executionInputRowCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(12, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(14, audit.fields().size());
		assertEquals(9, audit.rows().size());
		assertEquals(5, audit.scenarios().size());

		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary current =
				scenario(audit, "current_source_review_blocked_no_execution_input").summary();
		assertFalse(current.sourceRowsReviewed());
		assertFalse(current.scatteredSurfaceFitContractReady());
		assertEquals(9, current.expectedExecutionInputRowCount());
		assertEquals(0, current.candidateExecutionInputRowCount());
		assertEquals(0, current.exportedExecutionInputRowCount());
		assertEquals("source-license-review-required", current.message());

		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary staticOnly =
				scenario(audit, "static_neighbor_rows_only_no_complete_execution_input").summary();
		assertTrue(staticOnly.sourceRowsReviewed());
		assertFalse(staticOnly.scatteredSurfaceFitRun());
		assertEquals(3, staticOnly.candidateExecutionInputRowCount());
		assertEquals(3, staticOnly.directNeighborCandidateRowCount());
		assertEquals(0, staticOnly.scatteredSurfaceCandidateRowCount());
		assertEquals(0, staticOnly.exportedExecutionInputRowCount());
		assertEquals("scattered-surface-fit-not-run", staticOnly.message());

		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary missing =
				scenario(audit, "surface_fit_results_missing_no_execution_input").summary();
		assertTrue(missing.scatteredSurfaceFitRun());
		assertEquals(0, missing.candidateExecutionInputRowCount());
		assertEquals("scattered-surface-fit-results-missing", missing.message());

		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary failed =
				scenario(audit, "surface_fit_failed_no_execution_input").summary();
		assertEquals(8, failed.candidateExecutionInputRowCount());
		assertEquals(3, failed.directNeighborCandidateRowCount());
		assertEquals(5, failed.scatteredSurfaceCandidateRowCount());
		assertEquals(0, failed.exportedExecutionInputRowCount());
		assertEquals("scattered-surface-fit-residual-gate-failed", failed.message());

		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary ready =
				scenario(audit, "surface_fit_ready_execution_input_handoff").summary();
		assertTrue(ready.scatteredSurfaceFitContractReady());
		assertTrue(ready.executionInputHandoffReady());
		assertEquals(9, ready.candidateExecutionInputRowCount());
		assertEquals(9, ready.exportedExecutionInputRowCount());
		assertEquals(6, ready.fullSimulationExecutionInputRowCount());
		assertEquals(3, ready.performanceOnlyExecutionInputRowCount());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("scattered-surface-fit-execution-input-handoff-ready", ready.message());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(9, audit.extrema().maxCandidateExecutionInputRowCount());
		assertEquals(9, audit.extrema().maxExportedExecutionInputRowCount());
		assertEquals(3, audit.extrema().maxDirectNeighborCandidateRowCount());
		assertEquals(6, audit.extrema().maxScatteredSurfaceCandidateRowCount());
		assertEquals(6, audit.extrema().maxFullSimulationExecutionInputRowCount());
		assertEquals(3, audit.extrema().maxPerformanceOnlyExecutionInputRowCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void rowsExposeStaticDirectAndSurfaceFitInputKindsWithoutReviewPayload() {
		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffRow staticRow =
				PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.row(
						"racingQuad", "static_anchor_low_rpm");
		assertEquals("DIRECT_STATIC_ANCHOR", staticRow.executionInputSourceKind());
		assertTrue(staticRow.directNeighborBindingReady());
		assertFalse(staticRow.scatteredFitRequired());
		assertEquals(1, staticRow.minimumPerformanceNeighborRows());
		assertEquals(2, staticRow.availableRectangularNeighborRows());
		assertFalse(staticRow.coefficientPayloadReviewed());
		assertFalse(staticRow.executionInputReady());
		assertFalse(staticRow.runtimeCouplingAllowed());
		assertFalse(staticRow.gameplayAutoApplyAllowed());
		assertEquals("DIRECT_BINDING_TARGET", staticRow.status());

		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffRow midRow =
				PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.row(
						"apDrone", "mid_domain_mid_rpm");
		assertEquals("SCATTERED_SURFACE_FIT", midRow.executionInputSourceKind());
		assertFalse(midRow.directNeighborBindingReady());
		assertTrue(midRow.scatteredFitRequired());
		assertEquals(4, midRow.minimumPerformanceNeighborRows());
		assertEquals(2, midRow.availableRectangularNeighborRows());
		assertTrue(midRow.postReviewFullSimulationLookupAllowed());
		assertEquals("SURFACE_FIT_TARGET", midRow.status());

		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffRow heavy =
				PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.row(
						"heavyLift", "mid_domain_mid_rpm");
		assertFalse(heavy.postReviewFullSimulationLookupAllowed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.row("cinewhoop", "mid_domain_mid_rpm"));
	}

	@Test
	void fieldsAndHandoffRejectInvalidInputs() {
		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputField sourceKind =
				PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.field("execution_input_source_kind");
		assertEquals("text", sourceKind.unit());
		assertTrue(sourceKind.downstreamUse().contains("static-anchor"));
		assertFalse(sourceKind.runtimeCouplingAllowed());
		assertFalse(sourceKind.gameplayAutoApplyAllowed());
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.field("missing"));

		PropellerArchiveCtCpJScatteredSurfaceFitContract.ScatteredSurfaceFitSummary readySummary =
				PropellerArchiveCtCpJScatteredSurfaceFitContract.audit().scenarios().stream()
						.filter(scenario -> "reviewed_surface_fit_all_pass".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();
		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary handoff =
				PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.handoff(readySummary, "source");
		assertTrue(handoff.executionInputHandoffReady());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.handoff(null, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.handoff(readySummary, ""));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.CtCpJScatteredSurfaceFitExecutionHandoffAudit audit =
				PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_scattered_surface_fit_execution_handoff_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_fit_execution_input,racingQuad,static_anchor_low_rpm,DIRECT_BINDING_TARGET,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_fit_execution_input,apDrone,mid_domain_mid_rpm,SURFACE_FIT_TARGET,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_fit_execution_handoff_scenario,surface_fit_ready_execution_input_handoff,READY,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_fit_execution_handoff_summary,all,max_exported_execution_input_row_count,9,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_scattered_fit_execution_handoff_summary,all,runtime_coupling_allowed_count,0,count,")));
	}

	private static PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffScenario scenario(
			PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.CtCpJScatteredSurfaceFitExecutionHandoffAudit audit,
			String name
	) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_scattered_surface_fit_execution_handoff_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
