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

class Aerodynamics4McL2PoweredSourceCouplingConservationGuardTest {
	@Test
	void auditKeepsMechanicalCouplingBlockedUntilConservationEvidenceIsAccepted() {
		Aerodynamics4McL2PoweredSourceCouplingConservationGuard.PoweredSourceCouplingConservationGuardAudit audit =
				Aerodynamics4McL2PoweredSourceCouplingConservationGuard.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Powered-Source-Coupling-Conservation-Guard-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("swirl angular-momentum conservation evidence"));
		assertEquals(201, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(35, audit.scenarioMetricCount());
		assertEquals(18, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(5, audit.scenarios().size());

		Aerodynamics4McL2PoweredSourceCouplingConservationGuard.PoweredSourceCouplingConservationGuardSummary current =
				find(audit.scenarios(), "current_coupling_and_conservation_blocked").summary();
		assertEquals("current_handoff_and_policy_blocked", current.couplingReadinessScenarioName());
		assertFalse(current.runtimeCouplingReadinessAllowed());
		assertFalse(current.poweredSourceApiSurfaceReady());
		assertFalse(current.acceptanceBudgetGateReady());
		assertFalse(current.allPoliciesRuntimeAllowed());
		assertFalse(current.hoverAndCruiseCouplingAllowed());
		assertEquals(2, current.conservationRowCount());
		assertEquals(2, current.conservationTargetSelfConsistentCount());
		assertEquals(0, current.liveConservationAcceptedCount());
		assertEquals(2, current.sourceForceDeltaRequiredCount());
		assertEquals(2, current.sourceMomentDeltaRequiredCount());
		assertEquals(2, current.wakeResidualRequiredCount());
		assertEquals(2, current.swirlConservationRowCount());
		assertEquals(2, current.swirlTargetSelfConsistentCount());
		assertEquals(0, current.liveSwirlConservationAcceptedCount());
		assertEquals(2, current.wakeTangentialVelocityRequiredCount());
		assertEquals(2, current.wakeAngularMomentumResidualRequiredCount());
		assertFalse(current.hoverLiveConservationAccepted());
		assertFalse(current.cruiseLiveConservationAccepted());
		assertFalse(current.hoverLiveSwirlConservationAccepted());
		assertFalse(current.cruiseLiveSwirlConservationAccepted());
		assertFalse(current.liveConservationEvidenceAccepted());
		assertFalse(current.liveSwirlConservationEvidenceAccepted());
		assertFalse(current.runtimePoweredSourceCouplingAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertEquals("hover-and-cruise-powered-source-force-moment-wake-and-swirl-conservation-evidence",
				current.conservationPayloadKind());
		assertEquals("audit-only-conservation-guard", current.runtimeInfo());
		assertEquals("complete-powered-source-coupling-readiness-gate", current.nextRequiredAction());
		assertEquals("BLOCKED", current.status());

		Aerodynamics4McL2PoweredSourceCouplingConservationGuard.PoweredSourceCouplingConservationGuardSummary
		conservationOnly = find(audit.scenarios(), "conservation_ready_coupling_blocked").summary();
		assertFalse(conservationOnly.runtimeCouplingReadinessAllowed());
		assertEquals(2, conservationOnly.liveConservationAcceptedCount());
		assertEquals(2, conservationOnly.liveSwirlConservationAcceptedCount());
		assertTrue(conservationOnly.liveConservationEvidenceAccepted());
		assertTrue(conservationOnly.liveSwirlConservationEvidenceAccepted());
		assertFalse(conservationOnly.runtimePoweredSourceCouplingAllowed());
		assertEquals("complete-powered-source-coupling-readiness-gate",
				conservationOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceCouplingConservationGuard.PoweredSourceCouplingConservationGuardSummary
				couplingOnly = find(audit.scenarios(), "coupling_ready_conservation_blocked").summary();
		assertEquals("handoffs_policy_and_api_surface_ready", couplingOnly.couplingReadinessScenarioName());
		assertTrue(couplingOnly.runtimeCouplingReadinessAllowed());
		assertTrue(couplingOnly.poweredSourceApiSurfaceReady());
		assertTrue(couplingOnly.acceptanceBudgetGateReady());
		assertTrue(couplingOnly.allPoliciesRuntimeAllowed());
		assertTrue(couplingOnly.hoverAndCruiseCouplingAllowed());
		assertEquals(0, couplingOnly.liveConservationAcceptedCount());
		assertEquals(0, couplingOnly.liveSwirlConservationAcceptedCount());
		assertFalse(couplingOnly.liveConservationEvidenceAccepted());
		assertFalse(couplingOnly.liveSwirlConservationEvidenceAccepted());
		assertFalse(couplingOnly.runtimePoweredSourceCouplingAllowed());
		assertEquals("capture-live-a4mc-powered-source-force-moment-wake-and-swirl-residuals",
				couplingOnly.nextRequiredAction());
		assertEquals("BLOCKED", couplingOnly.status());

		Aerodynamics4McL2PoweredSourceCouplingConservationGuard.PoweredSourceCouplingConservationGuardSummary
				swirlOnly = find(audit.scenarios(),
						"coupling_ready_force_moment_wake_ready_swirl_blocked").summary();
		assertTrue(swirlOnly.runtimeCouplingReadinessAllowed());
		assertEquals(2, swirlOnly.liveConservationAcceptedCount());
		assertEquals(0, swirlOnly.liveSwirlConservationAcceptedCount());
		assertTrue(swirlOnly.hoverLiveConservationAccepted());
		assertTrue(swirlOnly.cruiseLiveConservationAccepted());
		assertFalse(swirlOnly.hoverLiveSwirlConservationAccepted());
		assertFalse(swirlOnly.cruiseLiveSwirlConservationAccepted());
		assertFalse(swirlOnly.liveConservationEvidenceAccepted());
		assertFalse(swirlOnly.liveSwirlConservationEvidenceAccepted());
		assertFalse(swirlOnly.runtimePoweredSourceCouplingAllowed());
		assertEquals("capture-live-a4mc-powered-source-swirl-angular-momentum-residuals",
				swirlOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceCouplingConservationGuard.PoweredSourceCouplingConservationGuardSummary ready =
				find(audit.scenarios(), "coupling_and_conservation_ready").summary();
		assertTrue(ready.runtimeCouplingReadinessAllowed());
		assertEquals(2, ready.liveConservationAcceptedCount());
		assertEquals(2, ready.liveSwirlConservationAcceptedCount());
		assertTrue(ready.hoverLiveConservationAccepted());
		assertTrue(ready.cruiseLiveConservationAccepted());
		assertTrue(ready.hoverLiveSwirlConservationAccepted());
		assertTrue(ready.cruiseLiveSwirlConservationAccepted());
		assertTrue(ready.liveConservationEvidenceAccepted());
		assertTrue(ready.liveSwirlConservationEvidenceAccepted());
		assertTrue(ready.runtimePoweredSourceCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("synthetic-reviewed-conservation-ready", ready.runtimeInfo());
		assertEquals("runtime-powered-source-coupling-ready-after-conservation-review",
				ready.nextRequiredAction());
		assertEquals("READY", ready.status());
		assertEquals("powered-source-coupling-conservation-clear", ready.message());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(3, audit.extrema().mechanicalCouplingReadyScenarioCount());
		assertEquals(2, audit.extrema().conservationEvidenceAcceptedScenarioCount());
		assertEquals(1, audit.extrema().runtimePoweredSourceCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(2, audit.extrema().maxConservationRowCount());
		assertEquals(2, audit.extrema().maxConservationTargetSelfConsistentCount());
		assertEquals(2, audit.extrema().maxLiveConservationAcceptedCount());
		assertEquals(2, audit.extrema().maxSwirlConservationRowCount());
		assertEquals(2, audit.extrema().maxSwirlTargetSelfConsistentCount());
		assertEquals(2, audit.extrema().maxLiveSwirlConservationAcceptedCount());
		assertTrue(audit.extrema().maxMomentumClosureErrorRatio() < 1.0e-12);
		assertTrue(audit.extrema().maxKineticPowerClosureErrorRatio() < 1.0e-12);
		assertTrue(audit.extrema().maxTargetTangentialWakeVelocityMetersPerSecond() > 20.0);
		assertTrue(audit.extrema().maxSwirlPowerFractionOfMomentumPower() > 0.5);
		assertEquals(0.0, audit.extrema().maxNetTorqueCancellationErrorRatio(), 1.0e-12);
	}

	@Test
	void directGuardRequiresReadinessAndConservationRows() {
		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessSummary readyReadiness =
				Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit(getClass().getClassLoader())
						.scenarios()
						.stream()
						.filter(scenario -> "handoffs_policy_and_api_surface_ready".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();
		List<Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow> currentRows =
				Aerodynamics4McL2PoweredSourceConservationContract.audit().rows();

		Aerodynamics4McL2PoweredSourceCouplingConservationGuard.PoweredSourceCouplingConservationGuardSummary guarded =
				Aerodynamics4McL2PoweredSourceCouplingConservationGuard.guard(
						"ready-readiness-current-conservation",
						readyReadiness,
						currentRows);

		assertTrue(guarded.runtimeCouplingReadinessAllowed());
		assertFalse(guarded.liveConservationEvidenceAccepted());
		assertFalse(guarded.runtimePoweredSourceCouplingAllowed());
		assertEquals("capture-live-a4mc-powered-source-force-moment-wake-and-swirl-residuals",
				guarded.nextRequiredAction());
	}

	@Test
	void rejectsInvalidInputs() {
		Aerodynamics4McL2PoweredSourceCouplingReadinessGate.PoweredSourceCouplingReadinessSummary readiness =
				Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit(getClass().getClassLoader())
						.scenarios()
						.get(0)
						.summary();
		List<Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow> rows =
				Aerodynamics4McL2PoweredSourceConservationContract.audit().rows();

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingConservationGuard.audit((ClassLoader) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingConservationGuard.audit(null,
						Aerodynamics4McL2PoweredSourceConservationContract.audit()));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingConservationGuard.audit(
						Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit(), null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingConservationGuard.audit(
						Aerodynamics4McL2PoweredSourceCouplingReadinessGate.audit(),
						Aerodynamics4McL2PoweredSourceConservationContract.audit(), null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingConservationGuard.guard("", readiness, rows));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingConservationGuard.guard("scenario", null, rows));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingConservationGuard.guard("scenario", readiness, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingConservationGuard.guard(
						"scenario", readiness, rows, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingConservationGuard.guard(
						"scenario", readiness, java.util.Arrays.asList(rows.get(0), null)));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingConservationGuard.guard(
						"scenario", readiness, rows,
						java.util.Arrays.asList(
								Aerodynamics4McL2PoweredSourceSwirlConservationContract.audit().rows().get(0),
								null)));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceCouplingConservationGuard.PoweredSourceCouplingConservationGuardAudit audit =
				Aerodynamics4McL2PoweredSourceCouplingConservationGuard.audit(getClass().getClassLoader());
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_source_coupling_conservation_guard_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_conservation_guard_summary,all_scenarios,ready_scenario_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_conservation_guard_scenario,coupling_ready_conservation_blocked,runtime_coupling_readiness_allowed,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_conservation_guard_scenario,coupling_ready_force_moment_wake_ready_swirl_blocked,live_swirl_conservation_evidence_accepted,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_conservation_guard_scenario,coupling_ready_force_moment_wake_ready_swirl_blocked,next_required_action,capture-live-a4mc-powered-source-swirl-angular-momentum-residuals,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_conservation_guard_scenario,coupling_and_conservation_ready,runtime_powered_source_coupling_allowed,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_conservation_guard_scenario,coupling_and_conservation_ready,gameplay_auto_apply_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredSourceCouplingConservationGuard.PoweredSourceCouplingConservationGuardScenario find(
			List<Aerodynamics4McL2PoweredSourceCouplingConservationGuard.PoweredSourceCouplingConservationGuardScenario>
					scenarios,
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
							"docs/data/a4mc_l2_powered_source_coupling_conservation_guard_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
