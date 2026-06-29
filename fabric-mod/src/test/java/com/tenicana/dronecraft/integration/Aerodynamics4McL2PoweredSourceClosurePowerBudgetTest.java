package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceClosurePowerBudgetTest {
	@Test
	void auditConvertsFiniteTargetsIntoSiPowerBudgets() {
		Aerodynamics4McL2PoweredSourceClosurePowerBudget.PoweredSourceClosurePowerBudgetAudit audit =
				Aerodynamics4McL2PoweredSourceClosurePowerBudget.audit();

		assertEquals("A4MC-L2-Powered-Source-Closure-Power-Budget-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("SI shaft, axial-wake, and swirl-wake power budgets"));
		assertEquals(29, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(8, audit.presetStateRowCount());
		assertEquals(13, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(1.0e-9, audit.residualToleranceWatts(), 0.0);
		assertEquals(8, audit.rows().size());

		Aerodynamics4McL2PoweredSourceClosurePowerBudget.PoweredSourceClosurePowerBudgetRow hoverRacing =
				find(audit.rows(), "hover", "racingQuad");
		assertEquals(205.9605675929757, hoverRacing.targetShaftPowerWatts(), 1.0e-12);
		assertEquals(128.75412896241968, hoverRacing.targetWakeDemandWatts(), 1.0e-12);
		assertTrue(hoverRacing.targetResidualMarginWatts() > 70.0);
		assertCurrentPowerBudgetAndBlocked(hoverRacing);

		Aerodynamics4McL2PoweredSourceClosurePowerBudget.PoweredSourceClosurePowerBudgetRow hoverCine =
				find(audit.rows(), "hover", "cinewhoop");
		assertEquals(20.928608644908824, hoverCine.targetShaftPowerWatts(), 1.0e-12);
		assertEquals(10.46430432245441, hoverCine.targetAxialWakePowerWatts(), 1.0e-12);
		assertEquals(10.464304322454412, hoverCine.targetSwirlWakePowerWatts(), 1.0e-12);
		assertEquals(20.92860864490882, hoverCine.targetWakeDemandWatts(), 1.0e-12);
		assertTrue(Math.abs(hoverCine.targetResidualMarginWatts()) <= 1.0e-9);
		assertTrue(hoverCine.wakeDemandChangeWatts() < -180.0);
		assertFinitePowerBudgetAndBlocked(hoverCine);

		Aerodynamics4McL2PoweredSourceClosurePowerBudget.PoweredSourceClosurePowerBudgetRow cruiseCine =
				find(audit.rows(), "cruise", "cinewhoop");
		assertEquals(36.58802549801357, cruiseCine.targetShaftPowerWatts(), 1.0e-12);
		assertEquals(36.58802549801357, cruiseCine.targetWakeDemandWatts(), 1.0e-12);
		assertEquals(1.0, cruiseCine.targetDemandToShaftPowerRatio(), 1.0e-12);
		assertTrue(cruiseCine.wakeDemandChangeWatts() < -330.0);
		assertFinitePowerBudgetAndBlocked(cruiseCine);

		assertEquals(8, audit.extrema().rowCount());
		assertEquals(6, audit.extrema().currentWakeTargetInsideClosureCount());
		assertEquals(2, audit.extrema().finiteCalibrationTargetRequiredCount());
		assertEquals(8, audit.extrema().targetResidualNonNegativeCount());
		assertEquals(2, audit.extrema().targetResidualWithinToleranceCount());
		assertEquals(2, audit.extrema().shaftPowerReductionRequiredCount());
		assertEquals(2, audit.extrema().axialWakePowerReductionRequiredCount());
		assertEquals(2, audit.extrema().swirlWakePowerReductionRequiredCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertTrue(audit.extrema().maxTargetWakeDemandWatts() > 900.0);
		assertTrue(audit.extrema().minTargetDemandToShaftPowerRatio() < 0.63);
		assertTrue(audit.extrema().maxWakeDemandReductionWatts() > 330.0);
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceClosurePowerBudget.PoweredSourceClosurePowerBudgetAudit audit =
				Aerodynamics4McL2PoweredSourceClosurePowerBudget.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_source_closure_power_budget_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_closure_power_budget,hover:cinewhoop,54.5043114712853,139.2865598928443,70.9727058153374,210.25926570818172,0.3839807912431793,0.07512788262201889,1.0,20.928608644908824,10.46430432245441,10.464304322454412,20.92860864490882,3.552713678800501E-15,0.9999999999999998,true,true,true,FINITE_TARGET_POWER_BUDGET_READY,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_closure_power_budget,cruise:cinewhoop,95.28608287814579,243.50496926749648,124.07662707507687,367.58159634257333,0.38398079124317924,0.07512788262201885,1.0,36.58802549801357,18.294012749006782,18.29401274900679,36.58802549801357,0.0,1.0,true,true,true,FINITE_TARGET_POWER_BUDGET_READY,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_closure_power_budget_summary,all_rows,target_residual_within_tolerance_count,,,,,,,,,,,,,,,2,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_closure_power_budget_method,si_power_budget,target_power_equations,,,,,,,,,,,,,,,method,")));
	}

	private static void assertCurrentPowerBudgetAndBlocked(
			Aerodynamics4McL2PoweredSourceClosurePowerBudget.PoweredSourceClosurePowerBudgetRow row
	) {
		assertTrue(row.currentWakeTargetInsideClosure());
		assertFalse(row.finiteCalibrationTargetRequired());
		assertTrue(row.targetResidualNonNegative());
		assertFalse(row.targetResidualWithinTolerance());
		assertFalse(row.shaftPowerReductionRequired());
		assertFalse(row.axialWakePowerReductionRequired());
		assertFalse(row.swirlWakePowerReductionRequired());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("capture-live-current-wake-power-budget-telemetry", row.nextRequiredAction());
		assertEquals("CURRENT_TARGET_POWER_BUDGET_READY", row.status());
	}

	private static void assertFinitePowerBudgetAndBlocked(
			Aerodynamics4McL2PoweredSourceClosurePowerBudget.PoweredSourceClosurePowerBudgetRow row
	) {
		assertFalse(row.currentWakeTargetInsideClosure());
		assertTrue(row.finiteCalibrationTargetRequired());
		assertTrue(row.targetResidualNonNegative());
		assertTrue(row.targetResidualWithinTolerance());
		assertTrue(row.shaftPowerReductionRequired());
		assertTrue(row.axialWakePowerReductionRequired());
		assertTrue(row.swirlWakePowerReductionRequired());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("run-live-powered-source-at-target-q-and-axial-wake-power",
				row.nextRequiredAction());
		assertEquals("FINITE_TARGET_POWER_BUDGET_READY", row.status());
	}

	private static Aerodynamics4McL2PoweredSourceClosurePowerBudget.PoweredSourceClosurePowerBudgetRow find(
			List<Aerodynamics4McL2PoweredSourceClosurePowerBudget.PoweredSourceClosurePowerBudgetRow> rows,
			String spinState,
			String presetName
	) {
		return rows.stream()
				.filter(row -> spinState.equals(row.spinState())
						&& presetName.equals(row.presetName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_closure_power_budget_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
