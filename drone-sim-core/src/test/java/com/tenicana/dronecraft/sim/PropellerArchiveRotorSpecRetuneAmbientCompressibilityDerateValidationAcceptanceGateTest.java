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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGateTest {
	@Test
	void auditBuildsColdAirDerateValidationAcceptanceScenarios() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationAcceptanceAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Validation-Acceptance-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("manual derate review"));
		assertEquals(90, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(18, audit.scenarioMetricRowCount());
		assertEquals(11, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationAcceptanceSummary current =
						find(audit.scenarios(), "current_derate_validation_blocked").summary();
		assertEquals(0, current.plannedValidationRunCount());
		assertEquals(0, current.observedResultCount());
		assertEquals(0, current.missingResultCount());
		assertFalse(current.derateValidationAcceptanceReady());
		assertFalse(current.manualDerateReviewAllowed());
		assertFalse(current.playableReferenceAllowed());
		assertFalse(current.configPatchAllowed());
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals("BLOCKED", current.status());
		assertEquals("cold-air-derate-validation-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationAcceptanceSummary missing =
						find(audit.scenarios(), "synthetic_derate_validation_results_missing").summary();
		assertEquals(8, missing.plannedValidationRunCount());
		assertEquals(0, missing.observedResultCount());
		assertEquals(8, missing.missingResultCount());
		assertFalse(missing.derateValidationAcceptanceReady());
		assertEquals("cold-air-derate-validation-result-set-incomplete", missing.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationAcceptanceSummary allPass =
						find(audit.scenarios(), "synthetic_derate_validation_all_pass").summary();
		assertEquals(8, allPass.plannedValidationRunCount());
		assertEquals(8, allPass.observedResultCount());
		assertEquals(8, allPass.passedResultCount());
		assertEquals(0, allPass.failedResultCount());
		assertEquals(8, allPass.validationAcceptedRunCount());
		assertEquals(2, allPass.manualDerateReviewCandidateCount());
		assertTrue(allPass.allPlannedRunsHaveResults());
		assertTrue(allPass.allObservedResultsPassed());
		assertTrue(allPass.derateValidationAcceptanceReady());
		assertTrue(allPass.manualDerateReviewAllowed());
		assertFalse(allPass.playableReferenceAllowed());
		assertFalse(allPass.configPatchAllowed());
		assertFalse(allPass.runtimeCouplingAllowed());
		assertFalse(allPass.gameplayAutoApplyAllowed());
		assertEquals("READY", allPass.status());
		assertEquals("cold-air-derate-validation-accepted-for-manual-review", allPass.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationAcceptanceSummary failed =
						find(audit.scenarios(), "synthetic_derate_validation_one_failed").summary();
		assertEquals(8, failed.plannedValidationRunCount());
		assertEquals(8, failed.observedResultCount());
		assertEquals(1, failed.failedResultCount());
		assertEquals(7, failed.passedResultCount());
		assertFalse(failed.derateValidationAcceptanceReady());
		assertEquals("cold-air-derate-validation-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().acceptedScenarioCount());
		assertEquals(1, audit.extrema().manualDerateReviewAllowedScenarioCount());
		assertEquals(8, audit.extrema().maxPlannedValidationRunCount());
		assertEquals(8, audit.extrema().maxObservedResultCount());
		assertEquals(8, audit.extrema().maxMissingResultCount());
		assertEquals(1, audit.extrema().maxFailedResultCount());
		assertEquals(0, audit.extrema().playableReferenceAllowedScenarioCount());
		assertEquals(0, audit.extrema().configPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void resultAppliesColdAirDerateThresholdsSamplesAndViolations() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationRow target =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.row(
								"synthetic_ready_validation_all_pass",
								"racingQuad",
								"cold_sea_level_minus10c",
								"reaction_torque_limit_closure");

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationResult passing =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.result(
								target,
								target.maxPrimaryErrorRatio() * 0.50,
								target.maxSecondaryErrorRatio() * 0.50,
								target.minSampleCount() + 2,
								0);
		assertTrue(passing.passed());
		assertEquals("PASS", passing.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationResult highPrimary =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.result(
								target,
								target.maxPrimaryErrorRatio() * 1.10,
								target.maxSecondaryErrorRatio() * 0.50,
								target.minSampleCount() + 2,
								0);
		assertFalse(highPrimary.passed());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationResult highSecondary =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.result(
								target,
								target.maxPrimaryErrorRatio() * 0.50,
								target.maxSecondaryErrorRatio() * 1.10,
								target.minSampleCount() + 2,
								0);
		assertFalse(highSecondary.passed());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationResult tooFewSamples =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.result(
								target,
								target.maxPrimaryErrorRatio() * 0.50,
								target.maxSecondaryErrorRatio() * 0.50,
								target.minSampleCount() - 1,
								0);
		assertFalse(tooFewSamples.passed());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationResult violation =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.result(
								target,
								target.maxPrimaryErrorRatio() * 0.50,
								target.maxSecondaryErrorRatio() * 0.50,
								target.minSampleCount() + 2,
								1);
		assertFalse(violation.passed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.result(
						target, -1.0, 0.0, target.minSampleCount(), 0));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.result(
						target, 0.0, 0.0, -1, 0));
	}

	@Test
	void gateRejectsMalformedAndUnexpectedResults() {
		List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationRow> plannedRows =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.audit().rows()
								.stream()
								.filter(row -> "synthetic_ready_validation_all_pass".equals(row.scenarioName()))
								.toList();
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix
				.RetuneAmbientCompressibilityDerateValidationRow target =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationMatrix.row(
								"synthetic_ready_validation_all_pass",
								"racingQuad",
								"cold_sea_level_minus10c",
								"reaction_torque_limit_closure");
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationResult passing =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.result(
								target,
								target.maxPrimaryErrorRatio() * 0.50,
								target.maxSecondaryErrorRatio() * 0.50,
								target.minSampleCount() + 2,
								0);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.gate(
						"", plannedRows, List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.gate(
						"missing-plans", null, List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.gate(
						"missing-results", plannedRows, null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.gate(
						"null-planned-row",
						java.util.Collections.singletonList(null),
						List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.gate(
						"duplicate-result", plannedRows, List.of(passing, passing)));

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationResult unexpected =
						new PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
								.DerateValidationResult(
										"cinewhoop",
										"cold_sea_level_minus10c",
										"reaction_torque_limit_closure",
										0.0,
										0.0,
										99,
										0,
										true,
										"PASS");
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationAcceptanceSummary summary =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.gate(
								"unexpected-result", plannedRows, List.of(unexpected));
		assertEquals(8, summary.plannedValidationRunCount());
		assertEquals(1, summary.observedResultCount());
		assertEquals(8, summary.missingResultCount());
		assertEquals(1, summary.unexpectedResultCount());
		assertFalse(summary.derateValidationAcceptanceReady());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.scenario(
						"missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
				.DerateValidationAcceptanceAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation_acceptance_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation_acceptance_summary,all_scenarios,accepted_scenario_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation_acceptance_scenario,synthetic_derate_validation_results_missing,planned_validation_run_count,8,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation_acceptance_scenario,synthetic_derate_validation_all_pass,derate_validation_acceptance_ready,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation_acceptance_scenario,synthetic_derate_validation_one_failed,failed_result_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation_acceptance_summary,all_scenarios,runtime_coupling_allowed_scenario_count,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
			.DerateValidationAcceptanceScenario find(
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateValidationAcceptanceGate
					.DerateValidationAcceptanceScenario> scenarios,
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
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_validation_acceptance_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
