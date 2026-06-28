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

class Aerodynamics4McL2PoweredSourceCouplingBlockerReportTest {
	@Test
	void auditBuildsStableCouplingBlockerScenarios() {
		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerAudit audit =
				Aerodynamics4McL2PoweredSourceCouplingBlockerReport.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Powered-Source-Coupling-Blocker-Report-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("does not enable runtime coupling"));
		assertEquals(208, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(37, audit.scenarioMetricCount());
		assertEquals(15, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(5, audit.scenarios().size());

		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerSummary current =
				find(audit.scenarios(), "current_handoff_and_policy_blocked").summary();
		assertFalse(current.runtimePoweredSourceCouplingAllowed());
		assertEquals(7, current.blockerCount());
		assertTrue(current.acceptanceHandoffBlocker());
		assertTrue(current.validationBudgetBlocker());
		assertTrue(current.policyRuntimeMutationBlocker());
		assertTrue(current.poweredSourceApiSurfaceBlocker());
		assertTrue(current.poweredSourcePhysicalContractBlocker());
		assertTrue(current.poweredSourceApiBlocker());
		assertTrue(current.gameplayCouplingBlocker());
		assertFalse(current.solidDiskMaskBlocker());
		assertFalse(current.rotorDiskMaskPolicyBlocker());
		assertFalse(current.hoverAcceptanceHandoffReady());
		assertFalse(current.cruiseAcceptanceHandoffReady());
		assertFalse(current.acceptanceBudgetGateReady());
		assertFalse(current.allValidationBudgetsReady());
		assertFalse(current.hoverValidationBudgetCandidate());
		assertFalse(current.cruiseValidationBudgetCandidate());
		assertEquals(0, current.readyHandoffCount());
		assertEquals(2, current.expectedHandoffCount());
		assertEquals(0, current.validationBudgetCandidateCount());
		assertEquals(2, current.expectedValidationBudgetGroupCount());
		assertEquals(4, current.policyCount());
		assertEquals(0, current.runtimeMutationAllowedPolicyCount());
		assertEquals(0, current.poweredSourceApiSurfaceCount());
		assertEquals(5, current.requiredPoweredSourceApiSurfaceCount());
		assertTrue(current.missingPoweredSourceApiList().contains("body_force_source_api"));
		assertEquals(0, current.poweredSourcePhysicalContractCount());
		assertEquals(5, current.requiredPoweredSourcePhysicalContractCount());
		assertTrue(current.missingPoweredSourcePhysicalContractList().contains("source_term_si_units"));
		assertEquals(0, current.poweredSourceApiAvailablePolicyCount());
		assertEquals(4, current.keepRotorDiskOpenPolicyCount());
		assertEquals("complete-hover-and-cruise-powered-source-acceptance-handoffs",
				current.nextRequiredAction());
		assertEquals("BLOCKED", current.status());
		assertEquals("powered-source-coupling-blocked", current.message());

		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerSummary handoffsOnly =
				find(audit.scenarios(), "handoffs_ready_policy_blocked").summary();
		assertEquals(5, handoffsOnly.blockerCount());
		assertFalse(handoffsOnly.acceptanceHandoffBlocker());
		assertFalse(handoffsOnly.validationBudgetBlocker());
		assertTrue(handoffsOnly.acceptanceBudgetGateReady());
		assertTrue(handoffsOnly.poweredSourceApiSurfaceBlocker());
		assertTrue(handoffsOnly.poweredSourcePhysicalContractBlocker());
		assertTrue(handoffsOnly.poweredSourceApiBlocker());
		assertEquals("wait-for-public-a4mc-powered-source-api-surface",
				handoffsOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerSummary policyOnly =
				find(audit.scenarios(), "policy_ready_handoffs_blocked").summary();
		assertEquals(4, policyOnly.blockerCount());
		assertTrue(policyOnly.acceptanceHandoffBlocker());
		assertTrue(policyOnly.validationBudgetBlocker());
		assertTrue(policyOnly.poweredSourceApiSurfaceBlocker());
		assertTrue(policyOnly.poweredSourcePhysicalContractBlocker());
		assertFalse(policyOnly.poweredSourceApiBlocker());

		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerSummary surfaceBlocked =
				find(audit.scenarios(), "handoffs_and_policy_ready").summary();
		assertFalse(surfaceBlocked.runtimePoweredSourceCouplingAllowed());
		assertEquals(2, surfaceBlocked.blockerCount());
		assertTrue(surfaceBlocked.poweredSourceApiSurfaceBlocker());
		assertTrue(surfaceBlocked.poweredSourcePhysicalContractBlocker());
		assertFalse(surfaceBlocked.poweredSourceApiBlocker());
		assertEquals("wait-for-public-a4mc-powered-source-api-surface",
				surfaceBlocked.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerSummary ready =
				find(audit.scenarios(), "handoffs_policy_and_api_surface_ready").summary();
		assertTrue(ready.runtimePoweredSourceCouplingAllowed());
		assertEquals(0, ready.blockerCount());
		assertFalse(ready.poweredSourceApiSurfaceBlocker());
		assertFalse(ready.poweredSourcePhysicalContractBlocker());
		assertEquals(5, ready.poweredSourceApiSurfaceCount());
		assertEquals("none", ready.missingPoweredSourceApiList());
		assertEquals(5, ready.poweredSourcePhysicalContractCount());
		assertEquals("none", ready.missingPoweredSourcePhysicalContractList());
		assertEquals("READY", ready.status());
		assertEquals("powered-source-coupling-clear", ready.message());
		assertEquals("runtime-powered-source-coupling-ready-for-reviewed-activation",
				ready.nextRequiredAction());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(7, audit.extrema().maxBlockerCount());
		assertEquals(2, audit.extrema().acceptanceHandoffBlockerScenarioCount());
		assertEquals(2, audit.extrema().validationBudgetBlockerScenarioCount());
		assertEquals(2, audit.extrema().policyRuntimeMutationBlockerScenarioCount());
		assertEquals(4, audit.extrema().poweredSourceApiSurfaceBlockerScenarioCount());
		assertEquals(4, audit.extrema().poweredSourcePhysicalContractBlockerScenarioCount());
		assertEquals(2, audit.extrema().poweredSourceApiBlockerScenarioCount());
		assertEquals(2, audit.extrema().gameplayCouplingBlockerScenarioCount());
		assertEquals(0, audit.extrema().solidDiskMaskBlockerScenarioCount());
		assertEquals(0, audit.extrema().rotorDiskMaskPolicyBlockerScenarioCount());
		assertEquals(5, audit.extrema().maxPoweredSourceApiSurfaceCount());
		assertEquals(5, audit.extrema().maxPoweredSourcePhysicalContractCount());
	}

	@Test
	void reportSeparatesPhysicalContractFromNamedSourceExtensionPoints() {
		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessSummary missingContract =
				Aerodynamics4McL2PoweredSourceCouplingReadinessGate.gate(
						apiSurfaceWithoutPhysicalContract(),
						readyBudget(),
						readyPolicies());

		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerSummary report =
				Aerodynamics4McL2PoweredSourceCouplingBlockerReport.report(missingContract);

		assertFalse(report.runtimePoweredSourceCouplingAllowed());
		assertFalse(report.poweredSourceApiSurfaceBlocker());
		assertTrue(report.poweredSourcePhysicalContractBlocker());
		assertFalse(report.poweredSourceApiBlocker());
		assertEquals(1, report.blockerCount());
		assertEquals("wait-for-public-a4mc-powered-source-physical-contract",
				report.nextRequiredAction());
		assertEquals("source_term_si_units;source_term_body_frame;source_term_time_step_or_substep;"
						+ "runtime_force_moment_delta_result;runtime_momentum_conservation_residual",
				report.missingPoweredSourcePhysicalContractList());
	}

	@Test
	void reportSeparatesSolidDiskMaskFromAcceptanceAndSourceApiBlockers() {
		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessSummary solidDisk =
				Aerodynamics4McL2PoweredSourceCouplingReadinessGate.gate(
						Aerodynamics4McL2PoweredSourceApiSurfaceAudit.syntheticReadySummary(),
						readyBudget(),
						List.of(solidDiskPolicy(readyPolicies().get(0))));

		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerSummary report =
				Aerodynamics4McL2PoweredSourceCouplingBlockerReport.report(solidDisk);

		assertFalse(report.runtimePoweredSourceCouplingAllowed());
		assertFalse(report.acceptanceHandoffBlocker());
		assertFalse(report.poweredSourceApiSurfaceBlocker());
		assertFalse(report.poweredSourcePhysicalContractBlocker());
		assertFalse(report.poweredSourceApiBlocker());
		assertFalse(report.policyRuntimeMutationBlocker());
		assertFalse(report.gameplayCouplingBlocker());
		assertTrue(report.solidDiskMaskBlocker());
		assertTrue(report.rotorDiskMaskPolicyBlocker());
		assertEquals(2, report.blockerCount());
		assertEquals("keep-rotor-disks-open-and-reject-solid-disk-masks",
				report.nextRequiredAction());
	}

	@Test
	void rejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingBlockerReport.audit((ClassLoader) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingBlockerReport.audit(
						(Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessAudit) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingBlockerReport.report(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerAudit audit =
				Aerodynamics4McL2PoweredSourceCouplingBlockerReport.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_source_coupling_blocker_report_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_blocker_report_summary,all_scenarios,max_blocker_count,7,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_blocker_report_scenario,current_handoff_and_policy_blocked,blocker_count,7,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_blocker_report_scenario,handoffs_and_policy_ready,powered_source_api_surface_blocker,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_blocker_report_scenario,handoffs_and_policy_ready,powered_source_physical_contract_blocker,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_blocker_report_scenario,handoffs_policy_and_api_surface_ready,runtime_powered_source_coupling_allowed,true,")));
	}

	private static Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerScenario find(
			List<Aerodynamics4McL2PoweredSourceCouplingBlockerReport.PoweredSourceCouplingBlockerScenario> scenarios,
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

	private static Aerodynamics4McL2PoweredSourceApiSurfaceAudit.PoweredSourceApiSurfaceSummary apiSurfaceWithoutPhysicalContract() {
		return Aerodynamics4McL2PoweredSourceApiSurfaceAudit.summary(
				new Aerodynamics4McL2Bridge.L2Capabilities(
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						true,
						"synthetic source extension points without physical contract"),
				List.of(
						"bodyForceSource",
						"porousSource",
						"rotorSourceTerm",
						"sourceEnvelope"),
				List.of("sourceRuntimeDelta"),
				"synthetic-source-extension-points-without-physical-contract");
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
							"docs/data/a4mc_l2_powered_source_coupling_blocker_report_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
