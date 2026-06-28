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

class Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTableTest {
	@Test
	void auditBuildsBlockedCurrentReferenceTable() {
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.PoweredCruiseSkewWakeReferenceTableAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Skew-Wake-Reference-Table-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("export-disabled"));
		assertEquals(1317, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(48, audit.referenceSampleCount());
		assertEquals(27, audit.referenceMetricCount());
		assertEquals(14, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(48, audit.rows().size());

		for (Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.PoweredCruiseSkewWakeReferenceRow row
				: audit.rows()) {
			assertEquals("cruise", row.spinState());
			assertTrue(row.axialPlaneIndex() >= 1);
			assertTrue(row.axialPlaneIndex() <= 4);
			assertTrue(row.sweepColumnIndex() == -1 || row.sweepColumnIndex() == 0 || row.sweepColumnIndex() == 1);
			assertTrue(row.axialPlaneFraction() > 0.0);
			assertFalse(row.labValidationAccepted());
			assertFalse(row.errorBudgetGroupReady());
			assertFalse(row.referenceMaterialExportAllowed());
			assertFalse(row.referenceRowAvailable());
			assertTrue(row.meanTargetResultantDynamicPressurePascals() > 0.0);
			assertEquals(0.0, row.maxPressureErrorRatio(), 1.0e-12);
			assertTrue(row.meanTargetResultantWakeVelocityMetersPerSecond() > 0.0);
			assertEquals(0.0, row.maxWakeVelocityErrorRatio(), 1.0e-12);
			assertTrue(row.meanArrivalBandSeconds() >= 0.0);
			assertEquals(0.0, row.maxArrivalWindowErrorRatio(), 1.0e-12);
			assertTrue(row.meanTargetAxialMomentumFluxNewtons() > 0.0);
			assertEquals(0.0, row.maxMomentumErrorRatio(), 1.0e-12);
			assertEquals(0.0, row.pressureReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.wakeVelocityReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.arrivalReferenceWeight(), 1.0e-12);
			assertEquals(0.0, row.momentumReferenceWeight(), 1.0e-12);
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("BLOCKED", row.status());
			assertEquals("reference-handoff-blocked", row.message());
			assertEquals("aggregate-cruise-skew-wake-reference-table", row.referencePayloadKind());
		}

		assertEquals(48, audit.extrema().rowCount());
		assertEquals(0, audit.extrema().referenceRowAvailableCount());
		assertEquals(48, audit.extrema().blockedRowCount());
		assertEquals(16, audit.extrema().centerlineSweepRowCount());
		assertEquals(32, audit.extrema().lateralSweepRowCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(0.0, audit.extrema().maxPressureReferenceWeight(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxWakeVelocityReferenceWeight(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxArrivalReferenceWeight(), 1.0e-12);
		assertEquals(0.0, audit.extrema().maxMomentumReferenceWeight(), 1.0e-12);
		assertTrue(audit.extrema().maxMeanTargetResultantDynamicPressurePascals() > 0.0);
		assertTrue(audit.extrema().maxMeanTargetResultantWakeVelocityMetersPerSecond() > 0.0);
		assertTrue(audit.extrema().maxMeanTargetAxialMomentumFluxNewtons() > 0.0);
	}

	@Test
	void readyHandoffEnablesAvailableReferenceRowsWithoutRuntimeCoupling() {
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary handoff =
				findHandoff("lab_accepted_error_budget_ready");
		Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetAudit readyBudget =
				new Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetAudit(
						Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.SOURCE_ID,
						Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.CAVEAT,
						Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PACKET_METRIC_ROW_COUNT,
						Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.SOURCE_REFERENCE_COUNT,
						Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.GROUP_SAMPLE_COUNT,
						Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.EXPECTED_ROTOR_SEED_COUNT,
						Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.GROUP_METRIC_COUNT,
						Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.SUMMARY_METRIC_ROW_COUNT,
						Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.METHOD_METRIC_ROW_COUNT,
						Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.audit().groups().stream()
								.map(Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTableTest::readyGroup)
								.toList(),
						Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.audit().extrema()
				);

		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.PoweredCruiseSkewWakeReferenceTableAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.audit(handoff, readyBudget);

		assertEquals(48, audit.extrema().referenceRowAvailableCount());
		assertEquals(0, audit.extrema().blockedRowCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		for (Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.PoweredCruiseSkewWakeReferenceRow row
				: audit.rows()) {
			assertTrue(row.referenceRowAvailable());
			assertEquals(1.0, row.pressureReferenceWeight(), 1.0e-12);
			assertEquals(1.0, row.wakeVelocityReferenceWeight(), 1.0e-12);
			assertEquals(1.0, row.arrivalReferenceWeight(), 1.0e-12);
			assertEquals(1.0, row.momentumReferenceWeight(), 1.0e-12);
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("AVAILABLE", row.status());
			assertEquals("cruise-skew-wake-reference-row-available", row.message());
		}
	}

	@Test
	void rejectsInvalidInputs() {
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary handoff =
				findHandoff("current_lab_validation_blocked");
		Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetAudit budget =
				Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.audit();

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.audit(null, budget));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.audit(handoff, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.row(null, budget.groups().get(0)));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.row(handoff, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.PoweredCruiseSkewWakeReferenceTableAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeReferenceTable.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_cruise_skew_wake_reference_table_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_reference_table_summary,all_rows,reference_row_available_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_reference_table_summary,all_rows,runtime_coupling_allowed_count,0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_reference_table,apDrone:plane1:sweep0,reference_row_available,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_reference_table,apDrone:plane1:sweep0,momentum_reference_weight,0,")));
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.PoweredCruiseSkewWakeReferenceHandoffSummary findHandoff(
			String scenarioName
	) {
		return Aerodynamics4McL2PoweredCruiseSkewWakeReferenceHandoff.audit().scenarios().stream()
				.filter(scenario -> scenarioName.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup readyGroup(
			Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup group
	) {
		return new Aerodynamics4McL2PoweredCruiseSkewWakeErrorBudget.PoweredCruiseSkewWakeErrorBudgetGroup(
				group.presetName(),
				group.spinState(),
				group.axialPlaneIndex(),
				group.sweepColumnIndex(),
				group.axialPlaneFraction(),
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
				group.meanTargetResultantDynamicPressurePascals(),
				0.0,
				0.0,
				group.meanTargetResultantWakeVelocityMetersPerSecond(),
				0.0,
				0.0,
				group.meanArrivalBandSeconds(),
				0.0,
				0.0,
				group.meanTargetAxialMomentumFluxNewtons(),
				0.0,
				0.0,
				true,
				"READY",
				"cruise-skew-wake-error-budget-ready"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_cruise_skew_wake_reference_table_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
