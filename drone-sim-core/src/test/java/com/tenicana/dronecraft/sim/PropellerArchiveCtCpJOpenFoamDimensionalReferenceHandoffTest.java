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

class PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoffTest {
	private static final PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
			.CtCpJOpenFoamDimensionalReferenceHandoffAudit AUDIT =
					PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.audit();

	@Test
	void auditBuildsDimensionalReferenceHandoffScenarios() {
		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Dimensional-Reference-Handoff-Packet",
				AUDIT.sourceId());
		assertTrue(AUDIT.caveat().contains("reviewed payload shape"));
		assertTrue(AUDIT.caveat().contains("handoff-aware CT/CP/J lookup execution"));
		assertEquals(138, AUDIT.packetRowCount());
		assertEquals(9, AUDIT.sourceReferenceRowCount());
		assertEquals(17, AUDIT.referenceFieldRowCount());
		assertEquals(5, AUDIT.scenarioSampleCount());
		assertEquals(20, AUDIT.scenarioMetricRowCount());
		assertEquals(11, AUDIT.summaryRowCount());
		assertEquals(1, AUDIT.methodRowCount());
		assertEquals(17, AUDIT.fields().size());
		assertEquals(5, AUDIT.scenarios().size());

		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
				.OpenFoamDimensionalReferenceHandoffSummary current =
						scenario("current_dimensional_support_blocked").summary();
		assertFalse(current.lookupExecutionContractReady());
		assertFalse(current.dimensionalSupportReady());
		assertFalse(current.dimensionalReferenceReviewed());
		assertFalse(current.referenceMaterialExportAllowed());
		assertEquals(6, current.expectedReferenceRowCount());
		assertEquals(17, current.observedReferenceFieldCount());
		assertEquals(0, current.supportedDimensionalTargetCount());
		assertEquals(6, current.blockedDimensionalTargetCount());
		assertEquals("openfoam-dimensional-lookup-support-not-ready", current.message());

		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
				.OpenFoamDimensionalReferenceHandoffSummary executionBlocked =
						scenario("lookup_execution_blocked_reference_reviewed").summary();
		assertFalse(executionBlocked.lookupExecutionContractReady());
		assertFalse(executionBlocked.dimensionalSupportReady());
		assertTrue(executionBlocked.dimensionalReferenceReviewed());
		assertEquals("lookup-execution-contract-not-ready", executionBlocked.message());

		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
				.OpenFoamDimensionalReferenceHandoffSummary reviewMissing =
						scenario("dimensional_support_ready_reference_review_missing").summary();
		assertTrue(reviewMissing.lookupExecutionContractReady());
		assertTrue(reviewMissing.dimensionalSupportReady());
		assertFalse(reviewMissing.dimensionalReferenceReviewed());
		assertFalse(reviewMissing.referenceMaterialExportAllowed());
		assertEquals("openfoam-dimensional-reference-review-missing", reviewMissing.message());

		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
				.OpenFoamDimensionalReferenceHandoffSummary ready =
						scenario("dimensional_support_ready_reference_reviewed").summary();
		assertTrue(ready.lookupExecutionContractReady());
		assertTrue(ready.dimensionalSupportReady());
		assertTrue(ready.dimensionalReferenceReviewed());
		assertTrue(ready.referenceMaterialExportAllowed());
		assertEquals(6, ready.supportedDimensionalTargetCount());
		assertEquals(0, ready.blockedDimensionalTargetCount());
		assertEquals(6, ready.openFoamDimensionalReferenceRowAvailableCount());
		assertEquals("compact-openfoam-dimensional-rotor-response-reference",
				ready.referencePayloadKind());
		assertEquals("openfoam-dimensional-reference-material-ready", ready.message());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());

		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
				.OpenFoamDimensionalReferenceHandoffSummary residualFailed =
						scenario("reference_reviewed_dimensional_support_failed").summary();
		assertTrue(residualFailed.lookupExecutionContractReady());
		assertFalse(residualFailed.dimensionalSupportReady());
		assertTrue(residualFailed.dimensionalReferenceReviewed());
		assertEquals("openfoam-dimensional-residual-not-ready", residualFailed.message());

		assertEquals(5, AUDIT.extrema().scenarioCount());
		assertEquals(1, AUDIT.extrema().readyScenarioCount());
		assertEquals(4, AUDIT.extrema().blockedScenarioCount());
		assertEquals(1, AUDIT.extrema().lookupExecutionBlockedScenarioCount());
		assertEquals(1, AUDIT.extrema().referenceMaterialExportAllowedCount());
		assertEquals(6, AUDIT.extrema().maxSupportedDimensionalTargetCount());
		assertEquals(6, AUDIT.extrema().maxBlockedDimensionalTargetCount());
		assertEquals(17, AUDIT.extrema().maxObservedReferenceFieldCount());
		assertEquals(6, AUDIT.extrema().maxReferenceRowAvailableCount());
		assertEquals(0, AUDIT.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, AUDIT.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void fieldsDefineStableDimensionalReferencePayload() {
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceField thrust =
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.field("reference_thrust_newtons");
		assertEquals("N", thrust.unit());
		assertTrue(thrust.required());
		assertTrue(thrust.source().contains("dimensional rotor response"));
		assertFalse(thrust.runtimeCouplingAllowed());
		assertFalse(thrust.gameplayAutoApplyAllowed());

		PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceField induced =
				PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.field(
						"induced_velocity_residual_to_reference");
		assertEquals("ratio", induced.unit());
		assertTrue(induced.downstreamUse().contains("wake velocity"));

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.field("missing"));
	}

	@Test
	void handoffRejectsInvalidInputs() {
		PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportSummary support =
				findSupportScenario("lookup_and_dimensional_openfoam_support_ready").summary();
		List<PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff.OpenFoamDimensionalReferenceField> fields =
				AUDIT.fields();

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
						.handoff(null, true, fields, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
						.handoff(support, true, null, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
						.handoff(support, true, fields, ""));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_dimensional_reference_handoff_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(AUDIT.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_handoff_field,reference_thrust_newtons,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_handoff_scenario,lookup_execution_blocked_reference_reviewed,lookup_execution_contract_ready,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_handoff_scenario,dimensional_support_ready_reference_reviewed,reference_material_export_allowed,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_handoff_summary,all_scenarios,lookup_execution_blocked_scenario_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_handoff_summary,all_scenarios,max_reference_row_available_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_reference_handoff_summary,all_scenarios,runtime_coupling_allowed_count,0,count,")));
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalReferenceHandoff
			.OpenFoamDimensionalReferenceHandoffScenario scenario(String name) {
		return AUDIT.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalSupportGate
			.OpenFoamDimensionalSupportScenario findSupportScenario(String name) {
		return PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.audit()
				.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_openfoam_dimensional_reference_handoff_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
