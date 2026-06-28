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

class Aerodynamics4McL2PoweredSourceCouplingReadinessGateTest {
	@Test
	void auditBuildsStableCouplingReadinessScenarios() {
		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessAudit audit =
				Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Powered-Source-Coupling-Readiness-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Runtime powered-source coupling remains closed"));
		assertEquals(244, audit.packetMetricRowCount());
		assertEquals(8, audit.sourceReferenceCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(44, audit.scenarioMetricCount());
		assertEquals(15, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(5, audit.scenarios().size());

		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessSummary current =
				find(audit.scenarios(), "current_handoff_and_policy_blocked").summary();
		assertFalse(current.hoverAcceptanceHandoffReady());
		assertFalse(current.cruiseAcceptanceHandoffReady());
		assertEquals(2, current.handoffCount());
		assertEquals(0, current.readyHandoffCount());
		assertEquals(2, current.expectedHandoffCount());
		assertFalse(current.acceptanceBudgetGateReady());
		assertFalse(current.allValidationBudgetsReady());
		assertFalse(current.hoverValidationBudgetCandidate());
		assertFalse(current.cruiseValidationBudgetCandidate());
		assertEquals(2, current.validationBudgetGroupCount());
		assertEquals(0, current.validationBudgetCandidateCount());
		assertEquals(2, current.expectedValidationBudgetGroupCount());
		assertEquals(2, current.acceptanceHandoffBlockerMessageCount());
		assertEquals("powered-source-api-surface-missing", current.dominantAcceptanceHandoffMessage());
		assertEquals(2, current.validationBudgetBlockerMessageCount());
		assertEquals("powered-source-api-surface-missing", current.dominantValidationBudgetMessage());
		assertFalse(current.poweredSourceApiSurfaceReady());
		assertFalse(current.poweredSourceExecutorWiringAllowed());
		assertEquals(0, current.poweredSourceApiSurfaceCount());
		assertEquals(5, current.requiredPoweredSourceApiSurfaceCount());
		assertTrue(current.missingPoweredSourceApiList().contains("body_force_source_api"));
		assertFalse(current.poweredSourcePhysicalContractReady());
		assertEquals(0, current.poweredSourcePhysicalContractCount());
		assertEquals(5, current.requiredPoweredSourcePhysicalContractCount());
		assertTrue(current.missingPoweredSourcePhysicalContractList().contains("source_term_si_units"));
		assertEquals(4, current.policyCount());
		assertEquals(0, current.runtimeMutationAllowedPolicyCount());
		assertEquals(0, current.solidDiskMaskAllowedPolicyCount());
		assertEquals(4, current.actuatorDiskRepresentationRequiredPolicyCount());
		assertEquals(4, current.porousOrBodyForceSourceRequiredPolicyCount());
		assertEquals(0, current.poweredSourceApiAvailablePolicyCount());
		assertEquals(0, current.poweredHoverGameplayCouplingAllowedPolicyCount());
		assertEquals(0, current.poweredCruiseGameplayCouplingAllowedPolicyCount());
		assertEquals(4, current.validationBeforeRuntimeRequiredPolicyCount());
		assertEquals(4, current.keepRotorDiskOpenPolicyCount());
		assertEquals(4, current.overBlockageRiskIfSolidFilledPolicyCount());
		assertFalse(current.allHandoffsReady());
		assertFalse(current.allPoliciesRuntimeAllowed());
		assertTrue(current.allPoliciesKeepRotorDisksOpen());
		assertTrue(current.allPoliciesRequirePoweredSourceApi());
		assertFalse(current.hoverAndCruiseCouplingAllowed());
		assertFalse(current.runtimePoweredSourceCouplingAllowed());
		assertEquals("BLOCKED", current.status());

		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessSummary handoffsOnly =
				find(audit.scenarios(), "handoffs_ready_policy_blocked").summary();
		assertTrue(handoffsOnly.allHandoffsReady());
		assertTrue(handoffsOnly.acceptanceBudgetGateReady());
		assertTrue(handoffsOnly.allValidationBudgetsReady());
		assertEquals(2, handoffsOnly.validationBudgetCandidateCount());
		assertEquals(0, handoffsOnly.acceptanceHandoffBlockerMessageCount());
		assertEquals("none", handoffsOnly.dominantAcceptanceHandoffMessage());
		assertEquals(0, handoffsOnly.validationBudgetBlockerMessageCount());
		assertEquals("none", handoffsOnly.dominantValidationBudgetMessage());
		assertFalse(handoffsOnly.poweredSourceApiSurfaceReady());
		assertFalse(handoffsOnly.allPoliciesRuntimeAllowed());
		assertFalse(handoffsOnly.runtimePoweredSourceCouplingAllowed());

		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessSummary policyOnly =
				find(audit.scenarios(), "policy_ready_handoffs_blocked").summary();
		assertFalse(policyOnly.allHandoffsReady());
		assertFalse(policyOnly.acceptanceBudgetGateReady());
		assertFalse(policyOnly.allValidationBudgetsReady());
		assertEquals(2, policyOnly.acceptanceHandoffBlockerMessageCount());
		assertEquals("powered-source-api-surface-missing", policyOnly.dominantAcceptanceHandoffMessage());
		assertEquals(2, policyOnly.validationBudgetBlockerMessageCount());
		assertEquals("powered-source-api-surface-missing", policyOnly.dominantValidationBudgetMessage());
		assertFalse(policyOnly.poweredSourceApiSurfaceReady());
		assertTrue(policyOnly.allPoliciesRuntimeAllowed());
		assertTrue(policyOnly.hoverAndCruiseCouplingAllowed());
		assertFalse(policyOnly.runtimePoweredSourceCouplingAllowed());

		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessSummary surfaceBlocked =
				find(audit.scenarios(), "handoffs_and_policy_ready").summary();
		assertTrue(surfaceBlocked.allHandoffsReady());
		assertTrue(surfaceBlocked.acceptanceBudgetGateReady());
		assertEquals(0, surfaceBlocked.acceptanceHandoffBlockerMessageCount());
		assertEquals("none", surfaceBlocked.dominantAcceptanceHandoffMessage());
		assertEquals(0, surfaceBlocked.validationBudgetBlockerMessageCount());
		assertEquals("none", surfaceBlocked.dominantValidationBudgetMessage());
		assertTrue(surfaceBlocked.allPoliciesRuntimeAllowed());
		assertFalse(surfaceBlocked.poweredSourceApiSurfaceReady());
		assertFalse(surfaceBlocked.runtimePoweredSourceCouplingAllowed());

		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessSummary ready =
				find(audit.scenarios(), "handoffs_policy_and_api_surface_ready").summary();
		assertTrue(ready.allHandoffsReady());
		assertTrue(ready.acceptanceBudgetGateReady());
		assertTrue(ready.allValidationBudgetsReady());
		assertEquals(0, ready.acceptanceHandoffBlockerMessageCount());
		assertEquals("none", ready.dominantAcceptanceHandoffMessage());
		assertEquals(0, ready.validationBudgetBlockerMessageCount());
		assertEquals("none", ready.dominantValidationBudgetMessage());
		assertTrue(ready.poweredSourceApiSurfaceReady());
		assertTrue(ready.poweredSourceExecutorWiringAllowed());
		assertEquals(5, ready.poweredSourceApiSurfaceCount());
		assertEquals("none", ready.missingPoweredSourceApiList());
		assertTrue(ready.poweredSourcePhysicalContractReady());
		assertEquals(5, ready.poweredSourcePhysicalContractCount());
		assertEquals("none", ready.missingPoweredSourcePhysicalContractList());
		assertTrue(ready.allPoliciesRuntimeAllowed());
		assertTrue(ready.allPoliciesKeepRotorDisksOpen());
		assertTrue(ready.allPoliciesRequirePoweredSourceApi());
		assertTrue(ready.hoverAndCruiseCouplingAllowed());
		assertTrue(ready.runtimePoweredSourceCouplingAllowed());
		assertEquals("READY_FOR_POWERED_SOURCE_RUNTIME_COUPLING", ready.status());
		assertEquals("runtime-coupling-ready", ready.message());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().allowedScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(2, audit.extrema().maxReadyHandoffCount());
		assertEquals(2, audit.extrema().maxValidationBudgetCandidateCount());
		assertEquals(2, audit.extrema().maxAcceptanceHandoffBlockerMessageCount());
		assertEquals(2, audit.extrema().maxValidationBudgetBlockerMessageCount());
		assertEquals(4, audit.extrema().maxRuntimeMutationAllowedPolicyCount());
		assertEquals(4, audit.extrema().maxPolicyCount());
		assertEquals(0, audit.extrema().maxSolidDiskMaskAllowedPolicyCount());
		assertEquals(1, audit.extrema().poweredSourceApiSurfaceReadyScenarioCount());
		assertEquals(5, audit.extrema().maxPoweredSourceApiSurfaceCount());
		assertEquals(1, audit.extrema().poweredSourcePhysicalContractReadyScenarioCount());
		assertEquals(5, audit.extrema().maxPoweredSourcePhysicalContractCount());
		assertEquals(4, audit.extrema().maxPoweredSourceApiAvailablePolicyCount());
	}

	@Test
	void gateRequiresBothHandoffsAndRuntimeReadyPolicies() {
		List<Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary> handoffs =
				List.of(readyHandoff("hover"), readyHandoff("cruise"));
		Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary readyBudget =
				readyBudget();
		List<Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy> readyPolicies =
				readyPolicies();

		assertFalse(Aerodynamics4McL2PoweredSourceCouplingReadinessGate
				.gate(handoffs, readyPolicies)
				.runtimePoweredSourceCouplingAllowed());
		assertFalse(Aerodynamics4McL2PoweredSourceCouplingReadinessGate
				.gate(readyBudget, readyPolicies)
				.runtimePoweredSourceCouplingAllowed());
		assertTrue(Aerodynamics4McL2PoweredSourceCouplingReadinessGate
				.gate(
						Aerodynamics4McL2PoweredSourceApiSurfaceAudit.syntheticReadySummary(),
						readyBudget,
						readyPolicies)
				.runtimePoweredSourceCouplingAllowed());
		assertTrue(Aerodynamics4McL2PoweredSourceCouplingReadinessGate
				.gate(
						Aerodynamics4McL2PoweredSourceApiSurfaceAudit.syntheticReadySummary(),
						readyBudget,
						readyPolicies)
				.poweredSourcePhysicalContractReady());
		assertFalse(Aerodynamics4McL2PoweredSourceCouplingReadinessGate
				.gate(List.of(readyHandoff("hover")), readyPolicies)
				.runtimePoweredSourceCouplingAllowed());
		assertFalse(Aerodynamics4McL2PoweredSourceCouplingReadinessGate
				.gate(readyBudget, Aerodynamics4McL2ActuatorDiskRepresentationPolicy.audit().policies())
				.runtimePoweredSourceCouplingAllowed());
		assertFalse(Aerodynamics4McL2PoweredSourceCouplingReadinessGate
				.gate(readyBudget, List.of(solidDiskPolicy(readyPolicies.get(0))))
				.allPoliciesKeepRotorDisksOpen());
	}

	@Test
	void gateRejectsInvalidInputsAndDuplicateHandoffs() {
		List<Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary> duplicate =
				List.of(readyHandoff("hover"), readyHandoff("hover"));

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit((ClassLoader) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit(
						(Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffAudit) null,
						Aerodynamics4McL2ActuatorDiskRepresentationPolicy.audit()));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit(
						Aerodynamics4McL2PoweredSourceAcceptanceHandoff.audit(getClass().getClassLoader()),
						null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit(
						(Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetAudit) null,
						Aerodynamics4McL2ActuatorDiskRepresentationPolicy.audit()));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingReadinessGate.gate(
						(List<Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary>) null,
						readyPolicies()));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingReadinessGate.gate(
						(Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary) null,
						readyPolicies()));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingReadinessGate.gate(
						null,
						readyBudget(),
						readyPolicies()));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingReadinessGate.gate(duplicate, readyPolicies()));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessAudit audit =
				Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_source_coupling_readiness_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_readiness_summary,all_scenarios,allowed_scenario_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_readiness_scenario,current_handoff_and_policy_blocked,runtime_powered_source_coupling_allowed,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_readiness_scenario,current_handoff_and_policy_blocked,dominant_acceptance_handoff_message,powered-source-api-surface-missing,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_readiness_scenario,policy_ready_handoffs_blocked,dominant_validation_budget_message,powered-source-api-surface-missing,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_readiness_scenario,handoffs_and_policy_ready,powered_source_api_surface_ready,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_readiness_scenario,handoffs_and_policy_ready,missing_powered_source_physical_contract_list,source_term_si_units;")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_readiness_scenario,handoffs_policy_and_api_surface_ready,runtime_powered_source_coupling_allowed,true,")));
	}

	private static Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessScenario find(
			List<Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessScenario> scenarios,
			String name
	) {
		return scenarios.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary readyHandoff(
			String spinState
	) {
		boolean hover = "hover".equals(spinState);
		return new Aerodynamics4McL2PoweredSourceAcceptanceHandoff.PoweredSourceAcceptanceHandoffSummary(
				spinState,
				hover ? Aerodynamics4McL2PoweredHoverValidation.SOURCE_ID : Aerodynamics4McL2PoweredCruiseValidation.SOURCE_ID,
				hover ? Aerodynamics4McL2PoweredHoverAcceptanceGate.SOURCE_ID : Aerodynamics4McL2PoweredCruiseAcceptanceGate.SOURCE_ID,
				4,
				4,
				4,
				4,
				4,
				4,
				0,
				0,
				"none",
				0,
				0,
				0,
				0.0,
				0.0,
				true,
				true,
				true,
				"READY",
				"acceptance-handoff-ready"
		);
	}

	private static List<Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy> readyPolicies() {
		return Aerodynamics4McL2RotorDiskAperture.audit().presets().stream()
				.map(aperture -> Aerodynamics4McL2ActuatorDiskRepresentationPolicy.policy(aperture, true, true))
				.toList();
	}

	private static Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.PoweredSourceAcceptanceBudgetSummary readyBudget() {
		return Aerodynamics4McL2PoweredSourceAcceptanceBudgetGate.audit().scenarios().stream()
				.filter(scenario -> "handoff_ready_budget_ready".equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy solidDiskPolicy(
			Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy policy
	) {
		return new Aerodynamics4McL2ActuatorDiskRepresentationPolicy.ActuatorDiskRepresentationPolicy(
				policy.presetName(),
				policy.rotorCount(),
				policy.openFraction(),
				policy.minRotorOpenFraction(),
				policy.maxRotorOpenFraction(),
				policy.openDiskAreaSquareMeters(),
				policy.blockedDiskAreaSquareMeters(),
				policy.maxAxialInletSpeedMetersPerSecond(),
				policy.maxInPlaneInletSpeedMetersPerSecond(),
				"solid_disk_filled",
				true,
				policy.actuatorDiskRepresentationRequired(),
				policy.porousOrBodyForceSourceRequired(),
				policy.poweredSourceApiAvailable(),
				policy.actuatorDiskSourceMapReady(),
				policy.poweredHoverExperimentPlanReady(),
				policy.poweredCruiseExperimentPlanReady(),
				policy.poweredHoverAcceptanceGateRequired(),
				policy.poweredCruiseAcceptanceGateRequired(),
				policy.poweredHoverGameplayCouplingAllowed(),
				policy.poweredCruiseGameplayCouplingAllowed(),
				policy.overBlockageRiskIfSolidFilled(),
				policy.validationBeforeRuntimeRequired(),
				policy.recommendedApiSurface(),
				policy.runtimeMutationAllowed(),
				policy.status()
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_coupling_readiness_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
