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
		assertTrue(audit.caveat().contains("live hover/cruise conservation evidence"));
		assertEquals(158, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(34, audit.scenarioMetricCount());
		assertEquals(15, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.scenarios().size());

		Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.PoweredSourceCouplingConservationBlockerSummary current =
				find(audit.scenarios(), "current_coupling_and_conservation_blocked").summary();
		assertFalse(current.runtimePoweredSourceCouplingAllowed());
		assertEquals(8, current.blockerCount());
		assertEquals("current_handoff_and_policy_blocked", current.couplingReadinessScenarioName());
		assertTrue(current.couplingReadinessBlocker());
		assertTrue(current.poweredSourceApiSurfaceBlocker());
		assertTrue(current.acceptanceBudgetBlocker());
		assertTrue(current.policyRuntimeBlocker());
		assertTrue(current.hoverAndCruiseCouplingReadinessBlocker());
		assertFalse(current.targetModelSelfConsistencyBlocker());
		assertTrue(current.conservationEvidenceBlocker());
		assertTrue(current.hoverConservationBlocker());
		assertTrue(current.cruiseConservationBlocker());
		assertFalse(current.gameplayAutoApplyLeakBlocker());
		assertEquals("wait-for-public-a4mc-powered-source-api-surface", current.nextRequiredAction());
		assertEquals("BLOCKED", current.status());

		Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.PoweredSourceCouplingConservationBlockerSummary
				conservationOnly = find(audit.scenarios(), "conservation_ready_coupling_blocked").summary();
		assertEquals(5, conservationOnly.blockerCount());
		assertTrue(conservationOnly.couplingReadinessBlocker());
		assertTrue(conservationOnly.poweredSourceApiSurfaceBlocker());
		assertFalse(conservationOnly.conservationEvidenceBlocker());
		assertFalse(conservationOnly.hoverConservationBlocker());
		assertFalse(conservationOnly.cruiseConservationBlocker());
		assertEquals(2, conservationOnly.liveConservationAcceptedCount());
		assertEquals("wait-for-public-a4mc-powered-source-api-surface",
				conservationOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.PoweredSourceCouplingConservationBlockerSummary
				couplingOnly = find(audit.scenarios(), "coupling_ready_conservation_blocked").summary();
		assertEquals(3, couplingOnly.blockerCount());
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
		assertEquals("capture-live-hover-and-cruise-powered-source-conservation-evidence",
				couplingOnly.nextRequiredAction());

		Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport.PoweredSourceCouplingConservationBlockerSummary ready =
				find(audit.scenarios(), "coupling_and_conservation_ready").summary();
		assertTrue(ready.runtimePoweredSourceCouplingAllowed());
		assertEquals(0, ready.blockerCount());
		assertFalse(ready.couplingReadinessBlocker());
		assertFalse(ready.conservationEvidenceBlocker());
		assertEquals(2, ready.liveConservationAcceptedCount());
		assertTrue(ready.liveConservationEvidenceAccepted());
		assertFalse(ready.gameplayAutoApplyLeakBlocker());
		assertEquals("runtime-powered-source-coupling-ready-after-conservation-review",
				ready.nextRequiredAction());
		assertEquals("READY", ready.status());
		assertEquals("powered-source-coupling-conservation-clear", ready.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(3, audit.extrema().blockedScenarioCount());
		assertEquals(8, audit.extrema().maxBlockerCount());
		assertEquals(2, audit.extrema().couplingReadinessBlockerScenarioCount());
		assertEquals(2, audit.extrema().conservationEvidenceBlockerScenarioCount());
		assertEquals(2, audit.extrema().hoverConservationBlockerScenarioCount());
		assertEquals(2, audit.extrema().cruiseConservationBlockerScenarioCount());
		assertEquals(0, audit.extrema().targetModelSelfConsistencyBlockerScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyLeakBlockerScenarioCount());
		assertEquals(1, audit.extrema().runtimePoweredSourceCouplingAllowedCount());
		assertEquals(2, audit.extrema().maxLiveConservationAcceptedCount());
		assertEquals(2, audit.extrema().maxConservationTargetSelfConsistentCount());
		assertTrue(audit.extrema().maxMomentumClosureErrorRatio() < 1.0e-12);
		assertTrue(audit.extrema().maxKineticPowerClosureErrorRatio() < 1.0e-12);
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
				line.startsWith("a4mc_l2_powered_source_coupling_conservation_blocker_report_summary,all_scenarios,max_blocker_count,8,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_conservation_blocker_report_scenario,current_coupling_and_conservation_blocked,blocker_count,8,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_conservation_blocker_report_scenario,coupling_ready_conservation_blocked,next_required_action,capture-live-hover-and-cruise-powered-source-conservation-evidence,")));
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
