package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceShaftPowerBudgetTest {
	@Test
	void auditFlagsRotorTorquePowerBudgetDeficitsBeforeRuntimeCoupling() {
		Aerodynamics4McL2PoweredSourceShaftPowerBudget.PoweredSourceShaftPowerBudgetAudit audit =
				Aerodynamics4McL2PoweredSourceShaftPowerBudget.audit();

		assertEquals("A4MC-L2-Powered-Source-Shaft-Power-Budget-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("reaction-torque shaft power"));
		assertEquals(73, audit.packetMetricRowCount());
		assertEquals(8, audit.sourceReferenceCount());
		assertEquals(2, audit.spinStateSampleCount());
		assertEquals(26, audit.spinStateMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(2, audit.rows().size());

		Aerodynamics4McL2PoweredSourceShaftPowerBudget.PoweredSourceShaftPowerBudgetRow hover =
				find(audit.rows(), "hover");
		assertEquals(4, hover.presetBudgetCount());
		assertEquals(16, hover.rotorBudgetCount());
		assertTrue(hover.totalShaftPowerWatts() > 1000.0);
		assertTrue(hover.totalMomentumPowerWatts() > 700.0);
		assertTrue(hover.totalSwirlPowerWatts() > 200.0);
		assertTrue(hover.totalAeroPowerDemandWatts() > 900.0);
		assertTrue(hover.totalShaftPowerMarginWatts() > 0.0);
		assertTrue(hover.minPresetShaftPowerMarginRatio() < -0.7);
		assertTrue(hover.maxPresetShaftPowerDeficitWatts() > 150.0);
		assertTrue(hover.maxPerRotorReactionTorqueNewtonMeters() > 0.3);
		assertTrue(hover.maxPerRotorAngularVelocityRadiansPerSecond() > 1000.0);
		assertTrue(hover.maxPerRotorReactionPowerWatts() > 160.0);
		assertRequiredAndBlocked(hover);

		Aerodynamics4McL2PoweredSourceShaftPowerBudget.PoweredSourceShaftPowerBudgetRow cruise =
				find(audit.rows(), "cruise");
		assertEquals(4, cruise.presetBudgetCount());
		assertEquals(16, cruise.rotorBudgetCount());
		assertTrue(cruise.totalShaftPowerWatts() > hover.totalShaftPowerWatts());
		assertTrue(cruise.totalAeroPowerDemandWatts() > hover.totalAeroPowerDemandWatts());
		assertTrue(cruise.maxPresetShaftPowerDeficitWatts() > hover.maxPresetShaftPowerDeficitWatts());
		assertTrue(cruise.maxPerRotorAngularVelocityRadiansPerSecond()
				> hover.maxPerRotorAngularVelocityRadiansPerSecond());
		assertTrue(cruise.maxShaftPowerFractionOfBatteryLimit() > hover.maxShaftPowerFractionOfBatteryLimit());
		assertRequiredAndBlocked(cruise);

		assertEquals(2, audit.extrema().rowCount());
		assertEquals(0, audit.extrema().budgetSelfConsistentCount());
		assertEquals(2, audit.extrema().budgetInconsistentCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(32, audit.extrema().totalRotorBudgetCount());
		assertTrue(audit.extrema().maxTotalShaftPowerWatts() >= cruise.totalShaftPowerWatts());
		assertTrue(audit.extrema().maxTotalAeroPowerDemandWatts() >= cruise.totalAeroPowerDemandWatts());
		assertTrue(audit.extrema().maxPresetShaftPowerDeficitWatts()
				>= cruise.maxPresetShaftPowerDeficitWatts());
		assertTrue(audit.extrema().minPresetShaftPowerMarginRatio() < -0.7);
		assertTrue(audit.extrema().maxShaftPowerFractionOfBatteryLimit() > 0.4);
		assertTrue(audit.extrema().maxPerRotorReactionPowerWatts() > 280.0);
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceShaftPowerBudget.PoweredSourceShaftPowerBudgetAudit audit =
				Aerodynamics4McL2PoweredSourceShaftPowerBudget.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_source_shaft_power_budget_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_shaft_power_budget_summary,all_spin_states,budget_inconsistent_count,2,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_shaft_power_budget,hover,min_preset_shaft_power_margin_ratio,-0.7407756976240387,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_shaft_power_budget,cruise,max_preset_shaft_power_deficit_w,272.29551346442753,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_shaft_power_budget,cruise,budget_self_consistent,false,")));
	}

	private static void assertRequiredAndBlocked(
			Aerodynamics4McL2PoweredSourceShaftPowerBudget.PoweredSourceShaftPowerBudgetRow row
	) {
		assertTrue(row.shaftTelemetryRequired());
		assertTrue(row.motorTorqueCalibrationRequired());
		assertTrue(row.wakePowerClosureRequired());
		assertTrue(row.swirlPowerClosureRequired());
		assertFalse(row.budgetSelfConsistent());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("calibrate-powered-source-shaft-power-and-rotor-torque-coefficients",
				row.nextRequiredAction());
		assertEquals("TARGET_INCONSISTENT", row.status());
		assertEquals("powered-source-shaft-power-budget-deficit-detected", row.message());
	}

	private static Aerodynamics4McL2PoweredSourceShaftPowerBudget.PoweredSourceShaftPowerBudgetRow find(
			List<Aerodynamics4McL2PoweredSourceShaftPowerBudget.PoweredSourceShaftPowerBudgetRow> rows,
			String spinState
	) {
		return rows.stream()
				.filter(row -> spinState.equals(row.spinState()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_shaft_power_budget_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
