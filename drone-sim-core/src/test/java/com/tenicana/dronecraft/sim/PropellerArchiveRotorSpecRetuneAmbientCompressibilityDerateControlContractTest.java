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

class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContractTest {
	private static final String CONTROL_BOUNDARY =
			"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response";

	@Test
	void auditMapsAcceptedPhysicalBudgetsIntoControlLayerContractOnly() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.audit();

		assertEquals(
				"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Contract-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("control-layer target-omega boundary"));
		assertEquals(97, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.scenarioSampleCount());
		assertEquals(2, audit.contractRowCount());
		assertEquals(18, audit.scenarioMetricRowCount());
		assertEquals(16, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(2, audit.rows().size());
		assertEquals(4, audit.scenarios().size());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractScenarioSummary current =
						find(audit.scenarios(), "current_derate_validation_blocked").summary();
		assertFalse(current.derateControlContractAvailable());
		assertEquals(0, current.contractRowCount());
		assertEquals("cold-air-derate-validation-not-planned", current.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractScenarioSummary missing =
						find(audit.scenarios(), "synthetic_derate_validation_results_missing").summary();
		assertFalse(missing.derateControlContractAvailable());
		assertEquals("cold-air-derate-validation-result-set-incomplete", missing.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractScenarioSummary allPass =
						find(audit.scenarios(), "synthetic_derate_validation_all_pass").summary();
		assertTrue(allPass.postDeratePhysicalBudgetAccepted());
		assertTrue(allPass.derateControlContractAvailable());
		assertEquals(2, allPass.contractRowCount());
		assertEquals(2, allPass.controlLayerClampRequiredCount());
		assertEquals(0, allPass.runtimeImplementationReadyCount());
		assertEquals(2.840173019822779, allPass.maxRequiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals(0.9715982698017723, allPass.minTargetMaxRpmScale(), 1.0e-12);
		assertEquals(5.599680211820246, allPass.maxEquivalentMaxThrustLossPercent(), 1.0e-12);
		assertEquals(28310.07356552835, allPass.minContractedMaxRpm(), 1.0e-12);
		assertEquals(29472.808857731186, allPass.maxContractedMaxRpm(), 1.0e-12);
		assertEquals(0, allPass.rotorSpecPatchAllowedCount());
		assertEquals(0, allPass.droneConfigPatchAllowedCount());
		assertEquals(0, allPass.runtimeCouplingAllowedCount());
		assertEquals(0, allPass.playableReferenceAllowedCount());
		assertEquals(0, allPass.gameplayAutoApplyAllowedCount());
		assertEquals(CONTROL_BOUNDARY, allPass.controlBoundary());
		assertEquals("REVIEW_READY", allPass.status());
		assertEquals("control-contract-ready-candidate-derate-not-enabled", allPass.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractScenarioSummary failed =
						find(audit.scenarios(), "synthetic_derate_validation_one_failed").summary();
		assertFalse(failed.derateControlContractAvailable());
		assertEquals("cold-air-derate-validation-result-failed", failed.message());

		assertEquals(4, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().derateControlContractAvailableScenarioCount());
		assertEquals(2, audit.extrema().totalContractRowCount());
		assertEquals(2, audit.extrema().maxContractRowCount());
		assertEquals(2, audit.extrema().maxControlLayerClampRequiredCount());
		assertEquals(0, audit.extrema().maxRuntimeImplementationReadyCount());
		assertEquals(2.840173019822779, audit.extrema().maxRequiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals(0.9715982698017723, audit.extrema().minTargetMaxRpmScale(), 1.0e-12);
		assertEquals(5.599680211820246, audit.extrema().maxEquivalentMaxThrustLossPercent(), 1.0e-12);
		assertEquals(28310.07356552835, audit.extrema().minContractedMaxRpm(), 1.0e-12);
		assertEquals(29472.808857731186, audit.extrema().maxContractedMaxRpm(), 1.0e-12);
		assertEquals(0, audit.extrema().rotorSpecPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().droneConfigPatchAllowedScenarioCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedScenarioCount());
		assertEquals(0, audit.extrema().playableReferenceAllowedScenarioCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedScenarioCount());
	}

	@Test
	void rowsKeepRuntimeAndPlayableMutationClosed() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow racing =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.row(
								"synthetic_derate_validation_all_pass",
								"racingQuad",
								"cold_sea_level_minus10c");

		assertTrue(racing.postDeratePhysicalBudgetAccepted());
		assertTrue(racing.manualDerateReviewReady());
		assertEquals(3051.2857662936467, racing.rotorSpecMaxOmegaRadiansPerSecond(), 1.0e-12);
		assertEquals(29137.63274949454, racing.rotorSpecMaxRpm(), 1.0e-12);
		assertEquals(0.9715982698017723, racing.targetMaxRpmScale(), 1.0e-12);
		assertEquals(2964.6239712016823, racing.contractedMaxOmegaRadiansPerSecond(), 1.0e-12);
		assertEquals(28310.07356552835, racing.contractedMaxRpm(), 1.0e-12);
		assertEquals(2.840173019822779, racing.requiredMaxRpmDeratePercent(), 1.0e-12);
		assertEquals(0.9440031978817975, racing.equivalentMaxThrustScale(), 1.0e-12);
		assertEquals(5.599680211820246, racing.equivalentMaxThrustLossPercent(), 1.0e-12);
		assertEquals(0.6371344703535363, racing.targetMaxTipMach(), 1.0e-12);
		assertEquals(CONTROL_BOUNDARY, racing.controlBoundary());
		assertTrue(racing.controlLayerClampRequired());
		assertFalse(racing.rotorSpecPatchAllowed());
		assertFalse(racing.droneConfigPatchAllowed());
		assertFalse(racing.runtimeImplementationReady());
		assertFalse(racing.runtimeCouplingAllowed());
		assertFalse(racing.playableReferenceAllowed());
		assertFalse(racing.gameplayAutoApplyAllowed());
		assertEquals("REVIEW_READY", racing.status());
		assertEquals("control-contract-ready-candidate-derate-not-enabled", racing.message());

		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow apDrone =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.row(
								"synthetic_derate_validation_all_pass",
								"apDrone",
								"cold_sea_level_minus10c");
		assertEquals(3114.320044588864, apDrone.rotorSpecMaxOmegaRadiansPerSecond(), 1.0e-12);
		assertEquals(29739.56576798938, apDrone.rotorSpecMaxRpm(), 1.0e-12);
		assertEquals(0.9910302351978083, apDrone.targetMaxRpmScale(), 1.0e-12);
		assertEquals(3086.385326270149, apDrone.contractedMaxOmegaRadiansPerSecond(), 1.0e-12);
		assertEquals(29472.808857731186, apDrone.contractedMaxRpm(), 1.0e-12);
		assertEquals(0.9821409270762232, apDrone.equivalentMaxThrustScale(), 1.0e-12);
		assertEquals(1.7859072923776753, apDrone.equivalentMaxThrustLossPercent(), 1.0e-12);
		assertTrue(apDrone.targetMaxRpmScale() > racing.targetMaxRpmScale());
		assertTrue(apDrone.equivalentMaxThrustLossPercent() < racing.equivalentMaxThrustLossPercent());
		assertTrue(apDrone.contractedMaxRpm() > racing.contractedMaxRpm());
		assertFalse(apDrone.runtimeCouplingAllowed());
		assertFalse(apDrone.playableReferenceAllowed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.row(
						"synthetic_derate_validation_all_pass", "cinewhoop", "cold_sea_level_minus10c"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.scenario(
						"missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractAudit audit =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_contract_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_contract_summary,all_scenarios,total_contract_row_count,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_contract_scenario,synthetic_derate_validation_all_pass,contract_row_count,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_contract,synthetic_derate_validation_all_pass,racingQuad,cold_sea_level_minus10c,true,true,3051.2857662936467,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_contract,synthetic_derate_validation_all_pass,apDrone,cold_sea_level_minus10c,true,true,3114.320044588864,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_contract_summary,all_scenarios,runtime_coupling_allowed_scenario_count,0,count,")));
	}

	private static PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
			.DerateControlContractScenario find(
			List<PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractScenario> scenarios,
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
							"docs/data/propeller_archive_rotor_spec_retune_ambient_compressibility_derate_control_contract_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
