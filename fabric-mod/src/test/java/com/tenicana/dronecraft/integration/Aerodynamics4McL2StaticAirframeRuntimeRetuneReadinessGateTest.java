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

class Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGateTest {
	@Test
	void auditBuildsStableRuntimeRetuneReadinessScenarios() {
		Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.RuntimeRetuneReadinessAudit audit =
				Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.audit();

		assertEquals("A4MC-L2-Static-Airframe-Runtime-Retune-Readiness-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Runtime retune readiness remains closed"));
		assertEquals(111, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(24, audit.rotationalSweepCaseCount());
		assertEquals(24, audit.scenarioMetricCount());
		assertEquals(9, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.RuntimeRetuneReadinessSummary current =
				findScenario(audit.scenarios(), "current_runtime_unavailable").summary();
		assertEquals(4, current.presetCount());
		assertEquals(4, current.configBlendCandidateCount());
		assertEquals(24, current.rotationalSweepCaseCount());
		assertEquals(0, current.suggestedBodyDragCandidateCount());
		assertEquals(0, current.runtimeConfigAutoApplyAllowedCount());
		assertFalse(current.staticAirframeRuntimeRetuneAllowed());

		Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.RuntimeRetuneReadinessSummary liveUnreviewed =
				findScenario(audit.scenarios(), "live_runtime_full_multi_axis_candidate_unreviewed").summary();
		assertEquals(4, liveUnreviewed.suggestedBodyDragCandidateCount());
		assertEquals(4, liveUnreviewed.suggestedPressureCenterCandidateCount());
		assertEquals(4, liveUnreviewed.angularDragReviewRequiredCount());
		assertEquals(0, liveUnreviewed.configBlendPolicyReviewedCount());
		assertEquals(0, liveUnreviewed.runtimeConfigAutoApplyAllowedCount());
		assertEquals(0, liveUnreviewed.rotationalFlowApiAvailableCount());
		assertEquals(0, liveUnreviewed.angularDragCalibrationAllowedCount());
		assertFalse(liveUnreviewed.bodyDragRetuneReady());
		assertFalse(liveUnreviewed.pressureCenterRetuneReady());
		assertFalse(liveUnreviewed.angularDragRetuneReady());
		assertFalse(liveUnreviewed.staticAirframeRuntimeRetuneAllowed());

		Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.RuntimeRetuneReadinessSummary synthetic =
				findScenario(audit.scenarios(), "reviewed_live_multi_axis_with_rotational_evidence").summary();
		assertEquals(4, synthetic.configBlendPolicyReviewedCount());
		assertEquals(4, synthetic.runtimeConfigAutoApplyAllowedCount());
		assertEquals(24, synthetic.rotationalFlowApiAvailableCount());
		assertEquals(24, synthetic.angularDragCalibrationAllowedCount());
		assertTrue(synthetic.bodyDragRetuneReady());
		assertTrue(synthetic.pressureCenterRetuneReady());
		assertTrue(synthetic.angularDragRetuneReady());
		assertTrue(synthetic.runtimePresetMutationExplicitlyEnabled());
		assertTrue(synthetic.staticAirframeRuntimeRetuneAllowed());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().allowedScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(1, audit.extrema().syntheticTargetAllowedCount());
		assertEquals(4, audit.extrema().maxConfigBlendCandidateCount());
		assertEquals(24, audit.extrema().maxRotationalSweepCaseCount());
	}

	@Test
	void scenarioRequiresEveryRetuneAxisBeforeOpening() {
		List<Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate> liveCandidates =
				findBlendScenario(Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.audit().scenarios(),
						"live_runtime_full_multi_axis_candidate").candidates();
		List<Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase> rotationalCases =
				Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.audit().sweepCases();

		Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.RuntimeRetuneReadinessSummary missingRuntimeEnable =
				Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.scenario(
						"reviewed_without_runtime_enable",
						liveCandidates,
						rotationalCases,
						true,
						false,
						true,
						true,
						"reviewed_without_runtime_enable"
				).summary();
		assertTrue(missingRuntimeEnable.angularDragRetuneReady());
		assertFalse(missingRuntimeEnable.bodyDragRetuneReady());
		assertFalse(missingRuntimeEnable.staticAirframeRuntimeRetuneAllowed());

		Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.RuntimeRetuneReadinessSummary missingRotational =
				Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.scenario(
						"reviewed_without_rotational",
						liveCandidates,
						rotationalCases,
						true,
						true,
						false,
						false,
						"reviewed_without_rotational"
				).summary();
		assertTrue(missingRotational.bodyDragRetuneReady());
		assertTrue(missingRotational.pressureCenterRetuneReady());
		assertFalse(missingRotational.angularDragRetuneReady());
		assertFalse(missingRotational.staticAirframeRuntimeRetuneAllowed());
	}

	@Test
	void scenarioRejectsInvalidInputs() {
		List<Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendCandidate> liveCandidates =
				findBlendScenario(Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.audit().scenarios(),
						"live_runtime_full_multi_axis_candidate").candidates();
		List<Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.RotationalDampingSweepCase> rotationalCases =
				Aerodynamics4McL2StaticAirframeRotationalDampingSweepPlan.audit().sweepCases();

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.scenario(
						"", liveCandidates, rotationalCases, false, false, false, false, "bad"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.scenario(
						"bad", null, rotationalCases, false, false, false, false, "bad"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.scenario(
						"bad", liveCandidates, null, false, false, false, false, "bad"));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.scenario(
						"bad", liveCandidates, rotationalCases, false, false, false, false, ""));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.RuntimeRetuneReadinessAudit audit =
				Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_static_airframe_runtime_retune_readiness_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_runtime_retune_readiness_gate_summary,all_scenarios,allowed_scenario_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_runtime_retune_readiness_gate_summary,all_scenarios,synthetic_target_allowed_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_runtime_retune_readiness_gate_scenario,live_runtime_full_multi_axis_candidate_unreviewed,static_airframe_runtime_retune_allowed,false,")));
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendScenario findBlendScenario(
			List<Aerodynamics4McL2StaticAirframeMultiAxisConfigBlendPolicy.MultiAxisConfigBlendScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.RuntimeRetuneReadinessScenario findScenario(
			List<Aerodynamics4McL2StaticAirframeRuntimeRetuneReadinessGate.RuntimeRetuneReadinessScenario> scenarios,
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
							"docs/data/a4mc_l2_static_airframe_runtime_retune_readiness_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
