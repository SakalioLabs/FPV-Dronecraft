package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class PropellerArchiveRotorSpecRetuneReynoldsBudgetGateTest {
	@Test
	void auditBuildsReynoldsBudgetOnlyAfterTipMachAcceptance() {
		PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetAudit audit =
				PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Reynolds-Budget-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("runtime low-Reynolds proxy"));
		assertEquals(82, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(2, audit.budgetRowCount());
		assertEquals(15, audit.scenarioMetricRowCount());
		assertEquals(13, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(15.0, audit.ambientTemperatureCelsius(), 1.0e-12);
		assertEquals(0.0, audit.ambientHumidity(), 1.0e-12);
		assertEquals(1.0, audit.airDensityRatio(), 1.0e-12);
		assertEquals(0.9739532625301786, audit.dynamicViscosityRatio(), 1.0e-12);
		assertEquals(30_000.0, audit.hoverReynoldsNumberFloor(), 1.0e-12);
		assertEquals(1.05, audit.lowReynoldsIndexFloor(), 1.0e-12);
		assertEquals(0.02, audit.lowReynoldsLossLimit(), 1.0e-12);
		assertEquals(2, audit.rows().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetScenarioSummary current =
				find(audit.scenarios(), "current_retune_validation_blocked").summary();
		assertFalse(current.reynoldsBudgetAvailable());
		assertEquals(0, current.budgetRowCount());
		assertEquals("validation-run-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetScenarioSummary allPass =
				find(audit.scenarios(), "synthetic_ready_validation_all_pass").summary();
		assertTrue(allPass.validationAcceptanceReady());
		assertTrue(allPass.tipMachBudgetAvailable());
		assertTrue(allPass.reynoldsBudgetAvailable());
		assertEquals(2, allPass.budgetRowCount());
		assertEquals(2, allPass.reynoldsBudgetAcceptedCount());
		assertEquals(2, allPass.tipMachCompressibilityReviewRequiredCount());
		assertTrue(allPass.minCandidateHoverReynoldsNumber() > audit.hoverReynoldsNumberFloor());
		assertTrue(allPass.minCandidateHoverLowReynoldsIndex() > audit.lowReynoldsIndexFloor());
		assertTrue(allPass.minCandidateMaxLowReynoldsIndex() > audit.lowReynoldsIndexFloor());
		assertEquals(0.0, allPass.maxCandidateLowReynoldsLoss(), 1.0e-12);
		assertEquals(0, allPass.configPatchAllowedCount());
		assertEquals(0, allPass.runtimeCouplingAllowedCount());
		assertEquals(0, allPass.gameplayAutoApplyAllowedCount());
		assertEquals("READY", allPass.status());
		assertEquals("reynolds-budget-ready-tip-mach-compressibility-review-carried", allPass.message());

		PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetScenarioSummary failed =
				find(audit.scenarios(), "synthetic_ready_validation_one_failed").summary();
		assertFalse(failed.reynoldsBudgetAvailable());
		assertEquals("validation-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().reynoldsBudgetAvailableScenarioCount());
		assertEquals(2, audit.extrema().totalBudgetRowCount());
		assertEquals(2, audit.extrema().maxBudgetRowCount());
		assertEquals(2, audit.extrema().maxReynoldsBudgetAcceptedCount());
		assertEquals(2, audit.extrema().maxTipMachCompressibilityReviewRequiredCount());
		assertTrue(audit.extrema().minCandidateHoverReynoldsNumber() > audit.hoverReynoldsNumberFloor());
		assertTrue(audit.extrema().minCandidateHoverLowReynoldsIndex() > audit.lowReynoldsIndexFloor());
		assertTrue(audit.extrema().minCandidateMaxLowReynoldsIndex() > audit.lowReynoldsIndexFloor());
		assertEquals(0.0, audit.extrema().maxCandidateLowReynoldsLoss(), 1.0e-12);
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void budgetRowsMatchRuntimeReynoldsFormula() throws ReflectiveOperationException {
		PropellerArchiveRotorSpecRetunePhysicalImpactPreview.RetunePhysicalImpactRow impact =
				PropellerArchiveRotorSpecRetunePhysicalImpactPreview.row(
						"synthetic_ready_validation_all_pass", "racingQuad");
		PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetRow budget =
				PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.row(
						"synthetic_ready_validation_all_pass", "racingQuad");
		RotorSpec candidate = candidateRotor("racingQuad");
		double candidateHoverOmega = omegaFromRpm(impact.candidateHoverRpm());
		double candidateMaxOmega = omegaFromRpm(impact.candidateMaxRpm());
		Method reynoldsNumber = privateDronePhysicsMethod("rotorReynoldsNumber");
		Method reynoldsIndex = privateDronePhysicsMethod("rotorLowReynoldsIndex");
		Method lowReynoldsLoss = privateDronePhysicsMethod("rotorLowReynoldsLoss");

		assertEquals(candidate.representativeBladeChordMeters(),
				budget.candidateRepresentativeBladeChordMeters(), 1.0e-12);
		assertEquals((double) reynoldsNumber.invoke(
						null,
						candidate,
						candidateHoverOmega,
						budget.airDensityRatio(),
						budget.ambientTemperatureCelsius(),
						budget.ambientHumidity()
				),
				budget.candidateHoverReynoldsNumber(), 1.0e-9);
		assertEquals((double) reynoldsNumber.invoke(
						null,
						candidate,
						candidateMaxOmega,
						budget.airDensityRatio(),
						budget.ambientTemperatureCelsius(),
						budget.ambientHumidity()
				),
				budget.candidateMaxReynoldsNumber(), 1.0e-9);
		assertEquals((double) reynoldsIndex.invoke(
						null,
						candidate,
						candidateHoverOmega,
						budget.airDensityRatio(),
						budget.ambientTemperatureCelsius(),
						budget.ambientHumidity()
				),
				budget.candidateHoverLowReynoldsIndex(), 1.0e-12);
		assertEquals((double) reynoldsIndex.invoke(
						null,
						candidate,
						candidateMaxOmega,
						budget.airDensityRatio(),
						budget.ambientTemperatureCelsius(),
						budget.ambientHumidity()
				),
				budget.candidateMaxLowReynoldsIndex(), 1.0e-12);
		assertEquals((double) lowReynoldsLoss.invoke(
						null,
						candidate,
						candidateHoverOmega,
						budget.airDensityRatio(),
						budget.ambientTemperatureCelsius(),
						budget.ambientHumidity()
				),
				budget.candidateHoverLowReynoldsLoss(), 1.0e-12);
		assertEquals((double) lowReynoldsLoss.invoke(
						null,
						candidate,
						candidateMaxOmega,
						budget.airDensityRatio(),
						budget.ambientTemperatureCelsius(),
						budget.ambientHumidity()
				),
				budget.candidateMaxLowReynoldsLoss(), 1.0e-12);
		assertTrue(budget.candidateHoverReynoldsWithinBudget());
		assertTrue(budget.candidateHoverLowReynoldsIndexWithinBudget());
		assertTrue(budget.candidateMaxLowReynoldsIndexWithinBudget());
		assertTrue(budget.candidateLowReynoldsLossWithinBudget());
		assertTrue(budget.reynoldsBudgetAccepted());
		assertTrue(budget.tipMachCompressibilityReviewRequired());
		assertTrue(budget.manualPatchReviewAllowed());
		assertFalse(budget.configPatchAllowed());
		assertFalse(budget.runtimeCouplingAllowed());
		assertFalse(budget.gameplayAutoApplyAllowed());
		assertEquals("READY", budget.status());
		assertEquals("reynolds-budget-ready-tip-mach-compressibility-review-carried", budget.message());

		PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetRow apDrone =
				PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.row(
						"synthetic_ready_validation_all_pass", "apDrone");
		assertTrue(apDrone.candidateHoverReynoldsNumber() < budget.candidateHoverReynoldsNumber());
		assertEquals(0.0, apDrone.candidateMaxLowReynoldsLoss(), 1.0e-12);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.row(
						"synthetic_ready_validation_all_pass", "cinewhoop"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.scenario("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetAudit audit =
				PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_reynolds_budget_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_reynolds_budget_summary,all_scenarios,total_budget_row_count,,,,,,,,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_reynolds_budget_scenario,synthetic_ready_validation_all_pass,reynolds_budget_accepted_count,,,,,,,,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_reynolds_budget,synthetic_ready_validation_all_pass,racingQuad,15.0,0.0,1.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_reynolds_budget,synthetic_ready_validation_all_pass,apDrone,15.0,0.0,1.0,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_reynolds_budget_summary,all_scenarios,runtime_coupling_allowed_scenario_count,,,,,,,,0,count,")));
	}

	private static Method privateDronePhysicsMethod(String name) throws NoSuchMethodException {
		Method method = DronePhysics.class.getDeclaredMethod(
				name,
				RotorSpec.class,
				double.class,
				double.class,
				double.class,
				double.class
		);
		method.setAccessible(true);
		return method;
	}

	private static RotorSpec candidateRotor(String presetName) {
		DroneConfig config = PropellerArchiveRotorSpecConfigDeltaReport.configFor(presetName);
		RotorSpec current = config.rotors().get(0);
		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow delta =
				PropellerArchiveRotorSpecConfigDeltaReport.row(presetName);
		return current
				.withRadiusMeters(delta.candidateRadiusMeters())
				.withBladePitchMeters(delta.candidateBladePitchMeters())
				.withBladeCount(delta.candidateBladeCount())
				.withThrustCoefficient(delta.candidateThrustCoefficient())
				.withYawTorquePerThrustMeter(delta.candidateYawTorquePerThrustMeters());
	}

	private static double omegaFromRpm(double rpm) {
		return rpm * 2.0 * Math.PI / 60.0;
	}

	private static PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetScenario find(
			List<PropellerArchiveRotorSpecRetuneReynoldsBudgetGate.RetuneReynoldsBudgetScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_reynolds_budget_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
