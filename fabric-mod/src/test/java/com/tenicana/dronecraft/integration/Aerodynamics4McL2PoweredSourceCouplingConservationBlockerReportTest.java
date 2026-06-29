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

class Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReportTest {
	@Test
	void auditBuildsStableConservationBlockerScenarios() {
		Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.PoweredSourceCouplingConservationBlockerAudit audit =
				Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Powered-Source-Coupling-Conservation-Blocker-Report-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("live swirl angular-momentum evidence"));
		assertEquals(277, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(49, audit.scenarioMetricCount());
		assertEquals(24, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(5, audit.scenarios().size());

		Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.PoweredSourceCouplingConservationBlockerSummary current =
				find(audit.scenarios(), "current_coupling_and_conservation_blocked").summary();
		assertFalse(current.runtimePoweredSourceCouplingAllowed());
		assertEquals(11, current.blockerCount());
		assertEquals("current_handoff_and_policy_blocked", current.couplingReadinessScenarioName());
		assertTrue(current.couplingReadinessBlocker());
		assertTrue(current.poweredSourceApiSurfaceBlocker());
		assertTrue(current.acceptanceBudgetBlocker());
		assertTrue(current.policyRuntimeBlocker());
		assertTrue(current.hoverAndCruiseCouplingReadinessBlocker());
		assertFalse(current.targetModelSelfConsistencyBlocker());
		assertFalse(current.swirlTargetSelfConsistencyBlocker());
		assertTrue(current.conservationEvidenceBlocker());
		assertTrue(current.hoverConservationBlocker());
		assertTrue(current.cruiseConservationBlocker());
		assertTrue(current.swirlConservationEvidenceBlocker());
		assertTrue(current.hoverSwirlConservationBlocker());
		assertTrue(current.cruiseSwirlConservationBlocker());
		assertFalse(current.gameplayAutoApplyLeakBlocker());
		assertFalse(current.liveSwirlConservationEvidenceAccepted());
		assertEquals(2, current.swirlConservationRowCount());
		assertEquals(2, current.swirlTargetSelfConsistentCount());
		assertEquals(0, current.liveSwirlConservationAcceptedCount());
		assertEquals(2, current.wakeTangentialVelocityRequiredCount());
		assertEquals(2, current.wakeAngularMomentumResidualRequiredCount());
		assertEquals("wait-for-public-a4mc-powered-source-api-surface", current.nextRequiredAction());
		assertEquals("BLOCKED", current.status());

		Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.PoweredSourceCouplingConservationBlockerSummary
				conservationOnly = find(audit.scenarios(), "conservation_ready_coupling_blocked").summary();
		assertEquals(5, conservationOnly.blockerCount());
		assertTrue(conservationOnly.couplingReadinessBlocker());
		assertTrue(conservationOnly.poweredSourceApiSurfaceBlocker());
		assertFalse(conservationOnly.conservationEvidenceBlocker());
		assertFalse(conservationOnly.swirlConservationEvidenceBlocker());
		assertFalse(conservationOnly.hoverConservationBlocker());
		assertFalse(conservationOnly.cruiseConservationBlocker());
		assertFalse(conservationOnly.hoverSwirlConservationBlocker());
		assertFalse(conservationOnly.cruiseSwirlConservationBlocker());
		assertEquals(2, conservationOnly.liveConservationAcceptedCount());
		assertEquals(2, conservationOnly.liveSwirlConservationAcceptedCount());
		assertEquals("wait-for-public-a4mc-powered-source-api-surface",
				conservationOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.PoweredSourceCouplingConservationBlockerSummary
				couplingOnly = find(audit.scenarios(), "coupling_ready_conservation_blocked").summary();
		assertEquals(6, couplingOnly.blockerCount());
		assertFalse(couplingOnly.couplingReadinessBlocker());
		assertFalse(couplingOnly.poweredSourceApiSurfaceBlocker());
		assertFalse(couplingOnly.acceptanceBudgetBlocker());
		assertFalse(couplingOnly.policyRuntimeBlocker());
		assertTrue(couplingOnly.runtimeCouplingReadinessAllowed());
		assertTrue(couplingOnly.poweredSourceApiSurfaceReady());
		assertTrue(couplingOnly.acceptanceBudgetGateReady());
		assertTrue(couplingOnly.allPoliciesRuntimeAllowed());
		assertTrue(couplingOnly.hoverAndCruiseCouplingAllowed());
		assertTrue(couplingOnly.conservationEvidenceBlocker());
		assertTrue(couplingOnly.hoverConservationBlocker());
		assertTrue(couplingOnly.cruiseConservationBlocker());
		assertTrue(couplingOnly.swirlConservationEvidenceBlocker());
		assertTrue(couplingOnly.hoverSwirlConservationBlocker());
		assertTrue(couplingOnly.cruiseSwirlConservationBlocker());
		assertEquals("capture-live-hover-and-cruise-powered-source-conservation-and-swirl-evidence",
				couplingOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.PoweredSourceCouplingConservationBlockerSummary
				swirlOnly = find(audit.scenarios(),
						"coupling_ready_force_moment_wake_ready_swirl_blocked").summary();
		assertEquals(4, swirlOnly.blockerCount());
		assertFalse(swirlOnly.couplingReadinessBlocker());
		assertFalse(swirlOnly.poweredSourceApiSurfaceBlocker());
		assertFalse(swirlOnly.acceptanceBudgetBlocker());
		assertFalse(swirlOnly.policyRuntimeBlocker());
		assertTrue(swirlOnly.conservationEvidenceBlocker());
		assertFalse(swirlOnly.hoverConservationBlocker());
		assertFalse(swirlOnly.cruiseConservationBlocker());
		assertTrue(swirlOnly.swirlConservationEvidenceBlocker());
		assertTrue(swirlOnly.hoverSwirlConservationBlocker());
		assertTrue(swirlOnly.cruiseSwirlConservationBlocker());
		assertEquals(2, swirlOnly.liveConservationAcceptedCount());
		assertEquals(0, swirlOnly.liveSwirlConservationAcceptedCount());
		assertEquals("capture-live-hover-and-cruise-powered-source-swirl-angular-momentum-evidence",
				swirlOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.PoweredSourceCouplingConservationBlockerSummary ready =
				find(audit.scenarios(), "coupling_and_conservation_ready").summary();
		assertTrue(ready.runtimePoweredSourceCouplingAllowed());
		assertEquals(0, ready.blockerCount());
		assertFalse(ready.couplingReadinessBlocker());
		assertFalse(ready.conservationEvidenceBlocker());
		assertFalse(ready.swirlConservationEvidenceBlocker());
		assertEquals(2, ready.liveConservationAcceptedCount());
		assertEquals(2, ready.liveSwirlConservationAcceptedCount());
		assertTrue(ready.liveConservationEvidenceAccepted());
		assertTrue(ready.liveSwirlConservationEvidenceAccepted());
		assertFalse(ready.gameplayAutoApplyLeakBlocker());
		assertEquals("runtime-powered-source-coupling-ready-after-conservation-review",
				ready.nextRequiredAction());
		assertEquals("READY", ready.status());
		assertEquals("powered-source-coupling-conservation-clear", ready.message());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(11, audit.extrema().maxBlockerCount());
		assertEquals(2, audit.extrema().couplingReadinessBlockerScenarioCount());
		assertEquals(3, audit.extrema().conservationEvidenceBlockerScenarioCount());
		assertEquals(2, audit.extrema().hoverConservationBlockerScenarioCount());
		assertEquals(2, audit.extrema().cruiseConservationBlockerScenarioCount());
		assertEquals(3, audit.extrema().swirlConservationEvidenceBlockerScenarioCount());
		assertEquals(3, audit.extrema().hoverSwirlConservationBlockerScenarioCount());
		assertEquals(3, audit.extrema().cruiseSwirlConservationBlockerScenarioCount());
		assertEquals(0, audit.extrema().targetModelSelfConsistencyBlockerScenarioCount());
		assertEquals(0, audit.extrema().swirlTargetSelfConsistencyBlockerScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyLeakBlockerScenarioCount());
		assertEquals(1, audit.extrema().runtimePoweredSourceCouplingAllowedCount());
		assertEquals(2, audit.extrema().maxLiveConservationAcceptedCount());
		assertEquals(2, audit.extrema().maxConservationTargetSelfConsistentCount());
		assertTrue(audit.extrema().maxMomentumClosureErrorRatio() < 1.0e-12);
		assertTrue(audit.extrema().maxKineticPowerClosureErrorRatio() < 1.0e-12);
		assertEquals(2, audit.extrema().maxLiveSwirlConservationAcceptedCount());
		assertEquals(2, audit.extrema().maxSwirlConservationTargetSelfConsistentCount());
		assertTrue(audit.extrema().maxTargetTangentialWakeVelocityMetersPerSecond() > 20.0);
		assertTrue(audit.extrema().maxSwirlPowerFractionOfMomentumPower() > 0.5);
		assertEquals(0.0, audit.extrema().maxNetTorqueCancellationErrorRatio(), 1.0e-12);
	}

	@Test
	void rejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.audit((ClassLoader) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.audit(
						(Aerodynamics4McL2PoweredSourceCouplingConservationGuard.PoweredSourceCouplingConservationGuardAudit)
								null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.report(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.PoweredSourceCouplingConservationBlockerAudit audit =
				Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.audit(getClass().getClassLoader());
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_source_coupling_conservation_blocker_report_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_conservation_blocker_report_summary,all_scenarios,max_blocker_count,11,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_conservation_blocker_report_scenario,current_coupling_and_conservation_blocked,blocker_count,11,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_conservation_blocker_report_scenario,coupling_ready_force_moment_wake_ready_swirl_blocked,next_required_action,capture-live-hover-and-cruise-powered-source-swirl-angular-momentum-evidence,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_conservation_blocker_report_scenario,coupling_and_conservation_ready,runtime_powered_source_coupling_allowed,true,")));
	}

	private static Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.PoweredSourceCouplingConservationBlockerScenario find(
			List<Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.PoweredSourceCouplingConservationBlockerScenario>
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
							"docs/data/a4mc_l2_powered_source_coupling_conservation_blocker_report_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
