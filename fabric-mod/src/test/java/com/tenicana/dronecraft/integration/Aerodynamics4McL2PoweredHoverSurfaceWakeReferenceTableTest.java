package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTableTest {
	@Test
	void auditBuildsBlockedCurrentReferenceTable() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.PoweredHoverSurfaceWakeReferenceTableAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.audit();

		assertEquals("A4MC-L2-Powered-Hover-Surface-Wake-Reference-Table-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("export-disabled"));
		assertEquals(788, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(32, audit.referenceSampleCount());
		assertEquals(24, audit.referenceMetricCount());
		assertEquals(13, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(32, audit.rows().size());

		for (Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.PoweredHoverSurfaceWakeReferenceRow row
				: audit.rows()) {
			assertEquals("hover", row.spinState());
			assertTrue("ground".equals(row.surfaceType()) || "ceiling".equals(row.surfaceType()));
			assertTrue(row.clearanceOverRadius() == 0.5
					|| row.clearanceOverRadius() == 1.0
					|| row.clearanceOverRadius() == 2.0
					|| row.clearanceOverRadius() == 4.0);
			assertTrue(row.clearanceMeters() > 0.0);
			assertFalse(row.labValidationAccepted());
			assertFalse(row.errorBudgetGroupReady());
			assertFalse(row.referenceMaterialExportAllowed());
			assertFalse(row.referenceRowAvailable());
			assertTrue(row.meanTargetPressurePascals() > 0.0);
			assertEquals(0.0, row.maxPressureErrorRatio(), 1.0e-12);
			assertTrue(row.meanTargetWakeVelocityMetersPerSecond() > 0.0);
			assertEquals(0.0, row.maxWakeVelocityErrorRatio(), 1.0e-12);
			assertTrue(row.meanArrivalBandSeconds() > 0.0);
			assertEquals(0.0, row.maxArrivalBandErrorRatio(), 1.0e-12);
			assertEquals(0.0, row.pressureReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.wakeVelocityReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.arrivalReferenceWeight(), 1.0e-12);
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("BLOCKED", row.status());
			assertEquals("reference-handoff-blocked", row.message());
			assertEquals("aggregate-surface-wake-reference-table", row.referencePayloadKind());
		}

		assertEquals(32, audit.extrema().rowCount());
		assertEquals(0, audit.extrema().referenceRowAvailableCount());
		assertEquals(32, audit.extrema().blockedRowCount());
		assertEquals(16, audit.extrema().groundRowCount());
		assertEquals(16, audit.extrema().ceilingRowCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(0.0, audit.extrema().maxPressureReferenceWeight(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxWakeVelocityReferenceWeight(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxArrivalReferenceWeight(), 1.0e-12);
		assertTrue(audit.extrema().maxMeanTargetPressurePascals() > 0.0);
		assertTrue(audit.extrema().maxMeanTargetWakeVelocityMetersPerSecond() > 0.0);
		assertTrue(audit.extrema().maxMeanArrivalBandSeconds() > 0.0);
	}

	@Test
	void readyHandoffEnablesAvailableReferenceRowsWithoutRuntimeCoupling() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary handoff =
				findHandoff("lab_accepted_error_budget_ready");
		Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetAudit readyBudget =
				new Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetAudit(
						Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.SOURCE_ID,
						Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.CAVEAT,
						Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PACKET_METRIC_ROW_COUNT,
						Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.SOURCE_REFERENCE_COUNT,
						Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.GROUP_SAMPLE_COUNT,
						Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.EXPECTED_ROTOR_SEED_COUNT,
						Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.GROUP_METRIC_COUNT,
						Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.SUMMARY_METRIC_ROW_COUNT,
						Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.METHOD_METRIC_ROW_COUNT,
						Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.audit().groups().stream()
								.map(Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTableTest::readyGroup)
								.toList(),
						Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.audit().extrema()
				);

		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.PoweredHoverSurfaceWakeReferenceTableAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.audit(handoff, readyBudget);

		assertEquals(32, audit.extrema().referenceRowAvailableCount());
		assertEquals(0, audit.extrema().blockedRowCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		for (Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.PoweredHoverSurfaceWakeReferenceRow row
				: audit.rows()) {
			assertTrue(row.referenceRowAvailable());
			assertEquals(1.0, row.pressureReferenceWeight(), 1.0e-12);
			assertEquals(1.0, row.wakeVelocityReferenceWeight(), 1.0e-12);
			assertEquals(1.0, row.arrivalReferenceWeight(), 1.0e-12);
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("AVAILABLE", row.status());
			assertEquals("surface-wake-reference-row-available", row.message());
		}
	}

	@Test
	void rejectsInvalidInputs() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary handoff =
				findHandoff("current_lab_validation_blocked");
		Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetAudit budget =
				Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.audit();

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.audit(null, budget));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.audit(handoff, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.row(null, budget.groups().get(0)));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.row(handoff, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.PoweredHoverSurfaceWakeReferenceTableAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceTable.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_hover_surface_wake_reference_table_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_reference_table_summary,all_rows,reference_row_available_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_reference_table_summary,all_rows,runtime_coupling_allowed_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_reference_table,racingQuad:ground:hR1.0,reference_row_available,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_reference_table,racingQuad:ground:hR1.0,pressure_reference_weight,0,")));
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.PoweredHoverSurfaceWakeReferenceHandoffSummary findHandoff(
			String scenarioName
	) {
		return Aerodynamics4McL2PoweredHoverSurfaceWakeReferenceHandoff.audit().scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup readyGroup(
			Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup group
	) {
		return new Aerodynamics4McL2PoweredHoverSurfaceWakeErrorBudget.PoweredHoverSurfaceWakeErrorBudgetGroup(
				group.presetName(),
				group.spinState(),
				group.surfaceType(),
				group.clearanceOverRadius(),
				group.clearanceMeters(),
				group.expectedRotorSeedCount(),
				group.expectedRotorSeedCount(),
				0,
				0,
				group.expectedRotorSeedCount(),
				group.expectedRotorSeedCount(),
				0,
				0,
				true,
				true,
				true,
				group.meanTargetPressurePascals(),
				0.0,
				0.0,
				group.meanTargetWakeVelocityMetersPerSecond(),
				0.0,
				0.0,
				group.meanArrivalBandSeconds(),
				0.0,
				0.0,
				true,
				"READY",
				"surface-wake-error-budget-ready"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_hover_surface_wake_reference_table_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
