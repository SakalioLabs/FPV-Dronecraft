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

class Aerodynamics4McL2PoweredSourceCouplingReviewHandoffTest {
	@Test
	void auditBuildsBlockedCurrentAndReadyReviewTarget() {
		Aerodynamics4McL2PoweredSourceCouplingReviewHandoff.PoweredSourceCouplingReviewHandoffAudit audit =
				Aerodynamics4McL2PoweredSourceCouplingReviewHandoff.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Powered-Source-Coupling-Review-Handoff-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("final conservation guard"));
		assertEquals(83, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(2, audit.scenarioSampleCount());
		assertEquals(32, audit.scenarioMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(2, audit.scenarios().size());

		Aerodynamics4McL2PoweredSourceCouplingReviewHandoff.PoweredSourceCouplingReviewHandoffSummary current =
				find(audit.scenarios(), "current_powered_source_coupling_review_blocked").summary();
		assertFalse(current.poweredSourceCouplingReviewAllowed());
		assertFalse(current.simulationReferenceMaterialExportAllowed());
		assertEquals(8, current.blockerCount());
		assertFalse(current.finalGuardRuntimePoweredSourceCouplingAllowed());
		assertFalse(current.upstreamCouplingReadinessAllowed());
		assertFalse(current.poweredSourceApiSurfaceReady());
		assertFalse(current.acceptanceBudgetGateReady());
		assertFalse(current.policyRuntimeAllowed());
		assertFalse(current.hoverAndCruiseCouplingAllowed());
		assertFalse(current.liveConservationEvidenceAccepted());
		assertFalse(current.hoverLiveConservationAccepted());
		assertFalse(current.cruiseLiveConservationAccepted());
		assertTrue(current.conservationTargetSelfConsistent());
		assertEquals(2, current.conservationRowCount());
		assertEquals(0, current.liveConservationAcceptedCount());
		assertEquals(2, current.sourceForceDeltaRequiredCount());
		assertEquals(2, current.sourceMomentDeltaRequiredCount());
		assertEquals(2, current.wakeResidualRequiredCount());
		assertTrue(current.couplingReadinessBlocker());
		assertTrue(current.conservationEvidenceBlocker());
		assertTrue(current.hoverConservationBlocker());
		assertTrue(current.cruiseConservationBlocker());
		assertFalse(current.targetModelSelfConsistencyBlocker());
		assertFalse(current.gameplayAutoApplyLeakBlocker());
		assertFalse(current.gameplayAutoApplyAllowed());
		assertTrue(current.playableReviewRequiredBeforeUse());
		assertEquals("powered-source-force-moment-wake-conservation-review-package",
				current.reviewPayloadKind());
		assertEquals("wait-for-public-a4mc-powered-source-api-surface", current.nextRequiredAction());
		assertEquals("BLOCKED", current.status());
		assertEquals("powered-source-coupling-review-handoff-blocked", current.message());

		Aerodynamics4McL2PoweredSourceCouplingReviewHandoff.PoweredSourceCouplingReviewHandoffSummary ready =
				find(audit.scenarios(), "synthetic_powered_source_coupling_review_ready").summary();
		assertTrue(ready.poweredSourceCouplingReviewAllowed());
		assertTrue(ready.simulationReferenceMaterialExportAllowed());
		assertEquals(0, ready.blockerCount());
		assertTrue(ready.finalGuardRuntimePoweredSourceCouplingAllowed());
		assertTrue(ready.upstreamCouplingReadinessAllowed());
		assertTrue(ready.poweredSourceApiSurfaceReady());
		assertTrue(ready.acceptanceBudgetGateReady());
		assertTrue(ready.policyRuntimeAllowed());
		assertTrue(ready.hoverAndCruiseCouplingAllowed());
		assertTrue(ready.liveConservationEvidenceAccepted());
		assertTrue(ready.hoverLiveConservationAccepted());
		assertTrue(ready.cruiseLiveConservationAccepted());
		assertTrue(ready.conservationTargetSelfConsistent());
		assertEquals(2, ready.conservationRowCount());
		assertEquals(2, ready.liveConservationAcceptedCount());
		assertFalse(ready.couplingReadinessBlocker());
		assertFalse(ready.conservationEvidenceBlocker());
		assertFalse(ready.gameplayAutoApplyLeakBlocker());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertTrue(ready.playableReviewRequiredBeforeUse());
		assertEquals("powered-source-coupling-evidence-ready-for-reviewed-reference-handoff",
				ready.nextRequiredAction());
		assertEquals("READY", ready.status());
		assertEquals("powered-source-coupling-review-handoff-ready", ready.message());

		assertEquals(2, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(1, audit.extrema().blockedScenarioCount());
		assertEquals(1, audit.extrema().reviewAllowedCount());
		assertEquals(1, audit.extrema().referenceMaterialExportAllowedCount());
		assertEquals(8, audit.extrema().maxBlockerCount());
		assertEquals(1, audit.extrema().couplingReadinessBlockerScenarioCount());
		assertEquals(1, audit.extrema().conservationEvidenceBlockerScenarioCount());
		assertEquals(0, audit.extrema().targetModelSelfConsistencyBlockerScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyLeakBlockerScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(2, audit.extrema().playableReviewRequiredBeforeUseCount());
	}

	@Test
	void rejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingReviewHandoff.audit((ClassLoader) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingReviewHandoff.audit(
						(Aerodynamics4McL2PoweredSourceCouplingConservationBlockerReport
								.PoweredSourceCouplingConservationBlockerAudit) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceCouplingReviewHandoff.handoff(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceCouplingReviewHandoff.PoweredSourceCouplingReviewHandoffAudit audit =
				Aerodynamics4McL2PoweredSourceCouplingReviewHandoff.audit(getClass().getClassLoader());
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_source_coupling_review_handoff_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_review_handoff_summary,all_scenarios,review_allowed_count,1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_review_handoff_scenario,current_powered_source_coupling_review_blocked,blocker_count,8,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_review_handoff_scenario,synthetic_powered_source_coupling_review_ready,simulation_reference_material_export_allowed,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_coupling_review_handoff_scenario,synthetic_powered_source_coupling_review_ready,gameplay_auto_apply_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredSourceCouplingReviewHandoff.PoweredSourceCouplingReviewHandoffScenario find(
			List<Aerodynamics4McL2PoweredSourceCouplingReviewHandoff.PoweredSourceCouplingReviewHandoffScenario>
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
							"docs/data/a4mc_l2_powered_source_coupling_review_handoff_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
