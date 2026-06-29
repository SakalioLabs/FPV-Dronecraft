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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGateTest {
	@Test
	void auditKeepsCurrentControlHookImplementationBlocked() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
				.DerateControlHookReadinessAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Readiness-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("target-omega hook"));
		assertEquals(67, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(10, audit.scenarioMetricRowCount());
		assertEquals(10, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(2, audit.requiredContractRowCount());
		assertEquals(
				"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response",
				audit.controlBoundary());
		assertEquals(5, audit.rows().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
				.DerateControlHookReadinessRow current =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate.row(
								"current_derate_validation_blocked");
		assertFalse(current.controlContractAvailable());
		assertFalse(current.implementationReady());
		assertEquals("cold-air-derate-validation-not-planned", current.dominantBlocker());
		assertEquals("complete-cold-air-derate-validation-before-control-hook", current.nextRequiredAction());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
				.DerateControlHookReadinessRow allPass =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate.row(
								"synthetic_derate_validation_all_pass");
		assertTrue(allPass.controlContractAvailable());
		assertTrue(allPass.contractCoverageComplete());
		assertTrue(allPass.targetOmegaBoundaryMatched());
		assertTrue(allPass.controlLayerClampRequired());
		assertFalse(allPass.runtimeHookImplemented());
		assertFalse(allPass.motorResponseCouplingReviewed());
		assertFalse(allPass.failsafeClampReviewed());
		assertFalse(allPass.blackboxRegressionAvailable());
		assertFalse(allPass.implementationReady());
		assertEquals(2, allPass.contractRowCount());
		assertEquals(2, allPass.requiredContractRowCount());
		assertEquals(0.9715982698017723, allPass.minTargetMaxRpmScale(), 1.0e-12);
		assertEquals(5.599680211820246, allPass.maxEquivalentMaxThrustLossPercent(), 1.0e-12);
		assertEquals(28310.07356552835, allPass.minContractedMaxRpm(), 1.0e-12);
		assertEquals(29472.808857731186, allPass.maxContractedMaxRpm(), 1.0e-12);
		assertFalse(allPass.configMutationAllowed());
		assertFalse(allPass.runtimeCouplingAllowed());
		assertFalse(allPass.playableReferenceAllowed());
		assertFalse(allPass.gameplayAutoApplyAllowed());
		assertEquals("target-omega-derate-hook-not-implemented", allPass.dominantBlocker());
		assertEquals("implement-target-omega-scale-hook-before-motor-response", allPass.nextRequiredAction());
		assertEquals("BLOCKED", allPass.status());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
				.DerateControlHookReadinessRow syntheticReady =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate.row(
								"synthetic_control_hook_ready_reviewed");
		assertTrue(syntheticReady.runtimeHookImplemented());
		assertTrue(syntheticReady.motorResponseCouplingReviewed());
		assertTrue(syntheticReady.failsafeClampReviewed());
		assertTrue(syntheticReady.blackboxRegressionAvailable());
		assertTrue(syntheticReady.implementationReady());
		assertFalse(syntheticReady.runtimeCouplingAllowed());
		assertEquals("VALIDATION_READY", syntheticReady.status());
		assertEquals("target-omega-control-hook-ready-for-offline-validation", syntheticReady.message());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().implementationReadyScenarioCount());
		assertEquals(2, audit.extrema().controlContractAvailableScenarioCount());
		assertEquals(2, audit.extrema().contractCoverageCompleteScenarioCount());
		assertEquals(2, audit.extrema().targetOmegaBoundaryMatchedScenarioCount());
		assertEquals(2, audit.extrema().controlLayerClampRequiredScenarioCount());
		assertEquals(1, audit.extrema().runtimeHookImplementedScenarioCount());
		assertEquals(1, audit.extrema().motorResponseCouplingReviewedScenarioCount());
		assertEquals(1, audit.extrema().failsafeClampReviewedScenarioCount());
		assertEquals(1, audit.extrema().blackboxRegressionAvailableScenarioCount());
		assertEquals(2, audit.extrema().maxContractRowCount());
		assertEquals(0.9715982698017723, audit.extrema().minTargetMaxRpmScale(), 1.0e-12);
		assertEquals(5.599680211820246, audit.extrema().maxEquivalentMaxThrustLossPercent(), 1.0e-12);
		assertEquals(28310.07356552835, audit.extrema().minContractedMaxRpm(), 1.0e-12);
		assertEquals(29472.808857731186, audit.extrema().maxContractedMaxRpm(), 1.0e-12);
		assertEquals(0, audit.extrema().configMutationAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().playableReferenceAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate.row(
						"missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate
				.DerateControlHookReadinessAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookReadinessGate.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_readiness_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_readiness_summary,all_scenarios,implementation_ready_scenario_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_readiness_scenario,synthetic_derate_validation_all_pass,runtime_hook_implemented,false,boolean,true,true,true,true,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_readiness_scenario,synthetic_control_hook_ready_reviewed,implementation_ready,true,boolean,true,true,true,true,true,true,true,true,true,false,false,false,false,VALIDATION_READY,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_readiness_summary,all_scenarios,runtime_coupling_allowed_scenario_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_readiness_method,all_scenarios,method,target-omega-control-hook-readiness-gate,text,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_hook_readiness_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
