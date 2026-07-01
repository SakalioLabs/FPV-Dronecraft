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

class PropellerArchiveCtCpJLookupReviewedGridInputTest {
	@Test
	void auditExpandsHandoffTargetsIntoReviewedGridSlotsWithoutPayloadImport() {
		PropellerArchiveCtCpJLookupReviewedGridInput.CtCpJLookupReviewedGridInputAudit audit =
				PropellerArchiveCtCpJLookupReviewedGridInput.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Reviewed-Grid-Input-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("exact J/RPM slots"));
		assertEquals(68, audit.packetRowCount());
		assertEquals(9, audit.sourceReferenceRowCount());
		assertEquals(18, audit.gridInputFieldRowCount());
		assertEquals(21, audit.gridInputSlotRowCount());
		assertEquals(19, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(18, audit.fields().size());
		assertEquals(21, audit.slots().size());

		PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSummary summary = audit.summary();
		assertEquals(9, summary.targetRowCount());
		assertEquals(21, summary.gridSlotRowCount());
		assertEquals(3, summary.staticAnchorSlotCount());
		assertEquals(12, summary.bilinearCornerSlotCount());
		assertEquals(6, summary.highDomainEdgeSlotCount());
		assertEquals(3, summary.directStaticAnchorSlotCount());
		assertEquals(18, summary.scatteredSurfaceFitSlotCount());
		assertEquals(14, summary.fullSimulationGridSlotCount());
		assertEquals(7, summary.performanceOnlyGridSlotCount());
		assertEquals(21, summary.archiveCurveShapeGuardReadySlotCount());
		assertEquals(0, summary.coefficientPayloadReviewedSlotCount());
		assertEquals(0, summary.reviewedGridSlotReadyCount());
		assertEquals(0.0, summary.minSlotAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.8128, summary.maxSlotAdvanceRatioJ(), 1.0e-12);
		assertEquals(1060.0, summary.minSlotRpm(), 1.0e-12);
		assertEquals(7946.7, summary.maxSlotRpm(), 1.0e-9);
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("fill-reviewed-grid-input-slots-with-reviewed-ct-cp-payloads-before-lookup-run",
				summary.nextRequiredAction());
	}

	@Test
	void staticMidAndHighDomainSlotsDeclareExpectedCoordinateWindows() {
		PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot staticAnchor =
				PropellerArchiveCtCpJLookupReviewedGridInput.slot(
						"apDrone", "static_anchor_low_rpm", "static-anchor");
		assertEquals("DIRECT_STATIC_ANCHOR_SLOT", staticAnchor.slotKind());
		assertEquals("query_j_query_rpm", staticAnchor.slotCoordinateRole());
		assertEquals(0.0, staticAnchor.slotAdvanceRatioJ(), 1.0e-12);
		assertEquals(1477.8, staticAnchor.slotRpm(), 1.0e-12);
		assertEquals(1, staticAnchor.minimumPerformanceNeighborRows());
		assertEquals("DIRECT_STATIC_ANCHOR", staticAnchor.executionInputSourceKind());
		assertTrue(staticAnchor.directNeighborBindingReady());
		assertFalse(staticAnchor.scatteredFitRequired());
		assertTrue(staticAnchor.postReviewFullSimulationLookupAllowed());
		assertTrue(staticAnchor.archiveCurveShapeGuardPassed());
		assertFalse(staticAnchor.coefficientPayloadReviewed());
		assertFalse(staticAnchor.reviewedGridSlotReady());
		assertEquals("AWAIT_REVIEWED_COEFFICIENT_PAYLOAD", staticAnchor.status());

		List<PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot> midSlots =
				PropellerArchiveCtCpJLookupReviewedGridInput.slots("apDrone", "mid_domain_mid_rpm");
		assertEquals(4, midSlots.size());
		PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot lower =
				PropellerArchiveCtCpJLookupReviewedGridInput.slot("apDrone", "mid_domain_mid_rpm", "j0-r0");
		PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot upper =
				PropellerArchiveCtCpJLookupReviewedGridInput.slot("apDrone", "mid_domain_mid_rpm", "j1-r1");
		assertEquals("BILINEAR_CORNER_SLOT", lower.slotKind());
		assertEquals("lower_j_lower_rpm", lower.slotCoordinateRole());
		assertEquals(0.32512, lower.slotAdvanceRatioJ(), 1.0e-12);
		assertEquals(4065.36, lower.slotRpm(), 1.0e-9);
		assertEquals("upper_j_upper_rpm", upper.slotCoordinateRole());
		assertEquals(0.48768, upper.slotAdvanceRatioJ(), 1.0e-12);
		assertEquals(5359.14, upper.slotRpm(), 1.0e-9);
		assertEquals(4, lower.minimumPerformanceNeighborRows());
		assertEquals("SCATTERED_SURFACE_FIT", lower.executionInputSourceKind());
		assertTrue(lower.scatteredFitRequired());
		assertEquals("await-reviewed-bilinear-corner-ct-cp-payload", lower.message());

		List<PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot> highSlots =
				PropellerArchiveCtCpJLookupReviewedGridInput.slots("heavyLift", "high_domain_max_rpm");
		assertEquals(2, highSlots.size());
		PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot highLower =
				PropellerArchiveCtCpJLookupReviewedGridInput.slot("heavyLift", "high_domain_max_rpm", "j0-rmax");
		PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputSlot highUpper =
				PropellerArchiveCtCpJLookupReviewedGridInput.slot("heavyLift", "high_domain_max_rpm", "j1-rmax");
		assertEquals("HIGH_RPM_EDGE_SLOT", highLower.slotKind());
		assertEquals("lower_j_query_rpm", highLower.slotCoordinateRole());
		assertEquals(0.5336, highLower.slotAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.667, highUpper.slotAdvanceRatioJ(), 1.0e-12);
		assertEquals(7440.0, highLower.slotRpm(), 1.0e-12);
		assertEquals(7440.0, highUpper.slotRpm(), 1.0e-12);
		assertEquals(2, highLower.minimumPerformanceNeighborRows());
		assertFalse(highLower.postReviewFullSimulationLookupAllowed());
		assertEquals("await-reviewed-high-rpm-edge-ct-cp-payload", highLower.message());
	}

	@Test
	void fieldsAndLookupRejectUnknownRequests() {
		PropellerArchiveCtCpJLookupReviewedGridInput.ReviewedGridInputField slotKind =
				PropellerArchiveCtCpJLookupReviewedGridInput.field("slot_kind");
		assertEquals("text", slotKind.unit());
		assertTrue(slotKind.downstreamUse().contains("static anchor"));
		assertFalse(slotKind.runtimeCouplingAllowed());
		assertFalse(slotKind.gameplayAutoApplyAllowed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReviewedGridInput.field("missing"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupReviewedGridInput.slot(
						"apDrone", "mid_domain_mid_rpm", "missing"));
		assertTrue(PropellerArchiveCtCpJLookupReviewedGridInput.slots(
				"cinewhoop", "mid_domain_mid_rpm").isEmpty());
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupReviewedGridInput.CtCpJLookupReviewedGridInputAudit audit =
				PropellerArchiveCtCpJLookupReviewedGridInput.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_reviewed_grid_input_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reviewed_grid_input_source,scattered_fit_execution_handoff,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reviewed_grid_input_slot,apDrone,mid_domain_mid_rpm,j0-r0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reviewed_grid_input_slot,heavyLift,high_domain_max_rpm,j1-rmax,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reviewed_grid_input_summary,all,summary,,BLOCKED,grid_slot_row_count,21,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reviewed_grid_input_summary,all,summary,,BLOCKED,reviewed_grid_slot_ready_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_reviewed_grid_input_summary,all,summary,,BLOCKED,runtime_coupling_allowed_count,0,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_reviewed_grid_input_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
