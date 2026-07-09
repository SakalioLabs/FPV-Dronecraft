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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGateTest {
	@Test
	void auditBuildsBlockedAndSyntheticAcceptanceScenarios() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxAcceptanceAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
								.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Blackbox-Acceptance-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("manual control-hook review"));
		assertEquals(48, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(8, audit.scenarioMetricRowCount());
		assertEquals(8, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxAcceptanceSummary current =
						find(audit.scenarios(), "current_control_hook_blackbox_blocked").summary();
		assertEquals(0, current.plannedRegressionRunCount());
		assertEquals(0, current.observedResultCount());
		assertEquals(0, current.missingResultCount());
		assertEquals(0, current.failedResultCount());
		assertEquals(0, current.passedResultCount());
		assertFalse(current.blackboxRegressionAcceptanceReady());
		assertFalse(current.manualControlHookReviewAllowed());
		assertFalse(current.runtimeImplementationAllowed());
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.playableReferenceAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals("BLOCKED", current.status());
		assertEquals("blackbox-regression-run-not-planned",
				current.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxAcceptanceSummary missing =
						find(audit.scenarios(), "synthetic_control_hook_blackbox_results_missing").summary();
		assertEquals(8, missing.plannedRegressionRunCount());
		assertEquals(0, missing.observedResultCount());
		assertEquals(8, missing.missingResultCount());
		assertFalse(missing.blackboxRegressionAcceptanceReady());
		assertEquals("blackbox-regression-result-set-incomplete", missing.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxAcceptanceSummary allPass =
						find(audit.scenarios(), "synthetic_control_hook_blackbox_all_pass").summary();
		assertEquals(8, allPass.plannedRegressionRunCount());
		assertEquals(8, allPass.observedResultCount());
		assertEquals(8, allPass.passedResultCount());
		assertEquals(0, allPass.failedResultCount());
		assertEquals(8, allPass.acceptedRegressionRunCount());
		assertEquals(2, allPass.manualControlHookReviewCandidateCount());
		assertTrue(allPass.allPlannedRunsHaveResults());
		assertTrue(allPass.allObservedResultsPassed());
		assertTrue(allPass.blackboxRegressionAcceptanceReady());
		assertTrue(allPass.manualControlHookReviewAllowed());
		assertFalse(allPass.runtimeImplementationAllowed());
		assertFalse(allPass.runtimeCouplingAllowed());
		assertFalse(allPass.playableReferenceAllowed());
		assertFalse(allPass.gameplayAutoApplyAllowed());
		assertEquals("READY", allPass.status());
		assertEquals("target-omega-blackbox-regression-accepted-for-manual-control-hook-review",
				allPass.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxAcceptanceSummary failed =
						find(audit.scenarios(), "synthetic_control_hook_blackbox_one_failed").summary();
		assertEquals(8, failed.plannedRegressionRunCount());
		assertEquals(8, failed.observedResultCount());
		assertEquals(1, failed.failedResultCount());
		assertEquals(7, failed.passedResultCount());
		assertFalse(failed.blackboxRegressionAcceptanceReady());
		assertEquals("blackbox-regression-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().acceptedScenarioCount());
		assertEquals(1, audit.extrema().manualControlHookReviewAllowedScenarioCount());
		assertEquals(8, audit.extrema().maxPlannedRegressionRunCount());
		assertEquals(8, audit.extrema().maxObservedResultCount());
		assertEquals(8, audit.extrema().maxMissingResultCount());
		assertEquals(1, audit.extrema().maxFailedResultCount());
		assertEquals(0, audit.extrema().runtimeImplementationAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().playableReferenceAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
						.scenario("missing"));
	}

	@Test
	void resultAppliesCaseThresholdsSamplesAndViolations() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionRunRow target =
						plannedRow("racingQuad", "hover_hold_target_omega_scale");

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxRegressionResult passing =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
								.result(
										target,
										target.maxPrimaryErrorRatio() * 0.50,
										target.maxSecondaryErrorRatio() * 0.50,
										target.minSampleCount() + 2,
										0);
		assertTrue(passing.passed());
		assertEquals("PASS", passing.status());

		assertFalse(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.result(target, target.maxPrimaryErrorRatio() * 1.10,
						target.maxSecondaryErrorRatio() * 0.50, target.minSampleCount() + 2, 0)
				.passed());
		assertFalse(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.result(target, target.maxPrimaryErrorRatio() * 0.50,
						target.maxSecondaryErrorRatio() * 1.10, target.minSampleCount() + 2, 0)
				.passed());
		assertFalse(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.result(target, target.maxPrimaryErrorRatio() * 0.50,
						target.maxSecondaryErrorRatio() * 0.50, target.minSampleCount() - 1, 0)
				.passed());
		assertFalse(PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.result(target, target.maxPrimaryErrorRatio() * 0.50,
						target.maxSecondaryErrorRatio() * 0.50, target.minSampleCount() + 2, 1)
				.passed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
						.result(target, -1.0, 0.0, target.minSampleCount(), 0));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
						.result(target, 0.0, 0.0, -1, 0));
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionRunRow skipped =
						new PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
								.DerateControlHookBlackboxRegressionRunRow(
										target.scenarioName(),
										target.presetName(),
										target.ambientCaseName(),
										target.regressionCaseName(),
										target.flightPhase(),
										target.targetMetric(),
										target.minSampleCount(),
										target.maxPrimaryErrorRatio(),
										target.maxSecondaryErrorRatio(),
										false,
										false,
										false,
										false,
										target.targetMaxRpmScale(),
										target.contractedMaxRpm(),
										target.equivalentMaxThrustLossPercent(),
										false,
										false,
										false,
										"SKIPPED",
										"derate-hook-blackbox-regression-missing",
										"execute-cold-air-target-omega-blackbox-regression-before-runtime-candidate-derate");
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
						.result(skipped, 0.0, 0.0, skipped.minSampleCount(), 0));
	}

	@Test
	void gateRejectsMalformedAndUnexpectedResults() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionAudit matrix =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
								.audit();
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
				.DerateControlHookBlackboxRegressionRunRow target =
						plannedRow("racingQuad", "hover_hold_target_omega_scale");
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxRegressionResult passing =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
								.result(
										target,
										target.maxPrimaryErrorRatio() * 0.50,
										target.maxSecondaryErrorRatio() * 0.50,
										target.minSampleCount() + 2,
										0);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
						.gate("", "synthetic_control_hook_ready_reviewed", matrix, List.of(), "blank-scenario"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
						.gate("missing-matrix-scenario", "", matrix, List.of(), "missing-matrix-scenario"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
						.gate("missing-matrix", "synthetic_control_hook_ready_reviewed", null, List.of(),
								"missing-matrix"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
						.gate("missing-results", "synthetic_control_hook_ready_reviewed", matrix, null,
								"missing-results"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
						.gate("missing-runtime-info", "synthetic_control_hook_ready_reviewed", matrix, List.of(),
								""));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
						.gate("duplicate-result", "synthetic_control_hook_ready_reviewed", matrix,
								List.of(passing, passing), "duplicate-result"));

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxRegressionResult unexpected =
						new PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
								.DerateControlHookBlackboxRegressionResult(
										"synthetic_derate_validation_all_pass",
										"racingQuad",
										"hover_hold_target_omega_scale",
										0.0,
										0.0,
										999,
										0,
										true,
										"PASS");
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxAcceptanceSummary summary =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
								.gate("unexpected-result", "synthetic_control_hook_ready_reviewed", matrix,
										List.of(unexpected), "unexpected-result");
		assertEquals(8, summary.plannedRegressionRunCount());
		assertEquals(1, summary.observedResultCount());
		assertEquals(8, summary.missingResultCount());
		assertEquals(1, summary.unexpectedResultCount());
		assertFalse(summary.blackboxRegressionAcceptanceReady());
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
				.DerateControlHookBlackboxAcceptanceAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
								.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_acceptance_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_acceptance_summary,all_scenarios,accepted_scenario_count,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_acceptance_scenario,current_control_hook_blackbox_blocked,failed_result_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_acceptance_scenario,synthetic_control_hook_blackbox_results_missing,planned_regression_run_count,8,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_acceptance_scenario,synthetic_control_hook_blackbox_all_pass,blackbox_regression_acceptance_ready,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_acceptance_scenario,synthetic_control_hook_blackbox_one_failed,failed_result_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_acceptance_summary,all_scenarios,runtime_coupling_allowed_scenario_count,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
			.DerateControlHookBlackboxAcceptanceScenario find(
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxAcceptanceGate
					.DerateControlHookBlackboxAcceptanceScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix
			.DerateControlHookBlackboxRegressionRunRow plannedRow(
			String presetName,
			String regressionCaseName
	) {
		return PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookBlackboxRegressionMatrix.audit()
				.rows()
				.stream()
				.filter(row -> "synthetic_control_hook_ready_reviewed".equals(row.scenarioName()))
				.filter(row -> presetName.equals(row.presetName())
						&& regressionCaseName.equals(row.regressionCaseName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_blackbox_acceptance_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
