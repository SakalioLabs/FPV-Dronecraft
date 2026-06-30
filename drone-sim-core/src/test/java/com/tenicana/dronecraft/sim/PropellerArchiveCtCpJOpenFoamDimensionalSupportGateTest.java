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

class PropellerArchiveCtCpJOpenFoamDimensionalSupportGateTest {
	private static final PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.CtCpJOpenFoamDimensionalSupportGateAudit
			AUDIT = PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.audit();

	@Test
	void auditCombinesLookupSupportAndDimensionalResiduals() {
		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Dimensional-Support-Gate-Packet",
				AUDIT.sourceId());
		assertTrue(AUDIT.caveat().contains("coefficient-level CFD lookup support"));
		assertEquals(142, AUDIT.packetRowCount());
		assertEquals(8, AUDIT.sourceReferenceRowCount());
		assertEquals(5, AUDIT.scenarioSampleCount());
		assertEquals(24, AUDIT.scenarioMetricRowCount());
		assertEquals(13, AUDIT.summaryRowCount());
		assertEquals(1, AUDIT.methodRowCount());
		assertEquals(5, AUDIT.scenarios().size());

		PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportSummary current =
				scenario(AUDIT, "current_lookup_and_dimensional_blocked").summary();
		assertFalse(current.lookupSupportReady());
		assertFalse(current.dimensionalResidualReady());
		assertFalse(current.cfdDimensionalSupportReady());
		assertEquals(9, current.expectedLookupTargetCount());
		assertEquals(6, current.expectedOpenFoamDimensionalResultCaseCount());
		assertEquals(6, current.dimensionalMissingResultCount());
		assertEquals(0, current.supportedDimensionalTargetCount());
		assertEquals("lookup-support-not-ready", current.message());

		PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportSummary missing =
				scenario(AUDIT, "lookup_support_ready_si_results_missing").summary();
		assertTrue(missing.lookupSupportReady());
		assertFalse(missing.dimensionalResidualReady());
		assertTrue(missing.dimensionalResponseReferenceReady());
		assertEquals(6, missing.readyDimensionalReferenceCount());
		assertEquals(6, missing.dimensionalMissingResultCount());
		assertEquals("openfoam-dimensional-results-missing", missing.message());

		PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportSummary lookupFailed =
				scenario(AUDIT, "si_residual_ready_lookup_support_failed").summary();
		assertFalse(lookupFailed.lookupSupportReady());
		assertTrue(lookupFailed.dimensionalResidualReady());
		assertEquals(6, lookupFailed.dimensionalObservedResultCount());
		assertEquals(0, lookupFailed.supportedDimensionalTargetCount());
		assertEquals("lookup-support-not-ready", lookupFailed.message());

		PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportSummary residualFailed =
				scenario(AUDIT, "lookup_support_ready_si_residual_failed").summary();
		assertTrue(residualFailed.lookupSupportReady());
		assertFalse(residualFailed.dimensionalResidualReady());
		assertEquals(1, residualFailed.dimensionalFailedResultCount());
		assertEquals(0.075, residualFailed.maxInducedVelocityResidualToReference(), 1.0e-12);
		assertEquals("openfoam-dimensional-residual-gate-failed", residualFailed.message());

		PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportSummary ready =
				scenario(AUDIT, "lookup_and_dimensional_openfoam_support_ready").summary();
		assertTrue(ready.lookupSupportReady());
		assertTrue(ready.dimensionalResidualReady());
		assertTrue(ready.cfdDimensionalSupportReady());
		assertEquals(6, ready.supportedDimensionalTargetCount());
		assertEquals(6, ready.cfdSupportedLookupTargetCount());
		assertEquals(3, ready.cfdGeometryUnsupportedLookupTargetCount());
		assertEquals(0.04, ready.maxThrustResidualToReference(), 1.0e-12);
		assertEquals(0.03, ready.maxInducedVelocityResidualToReference(), 1.0e-12);
		assertFalse(ready.referenceExportAuthorityAllowed());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("openfoam-dimensional-support-ready", ready.message());

		assertEquals(5, AUDIT.extrema().scenarioCount());
		assertEquals(1, AUDIT.extrema().readyScenarioCount());
		assertEquals(4, AUDIT.extrema().blockedScenarioCount());
		assertEquals(6, AUDIT.extrema().maxSupportedDimensionalTargetCount());
		assertEquals(6, AUDIT.extrema().maxCfdSupportedLookupTargetCount());
		assertEquals(3, AUDIT.extrema().maxCfdGeometryUnsupportedLookupTargetCount());
		assertEquals(6, AUDIT.extrema().maxDimensionalMissingResultCount());
		assertEquals(1, AUDIT.extrema().maxDimensionalFailedResultCount());
		assertEquals(0.075, AUDIT.extrema().maxInducedVelocityResidualToReference(), 1.0e-12);
		assertEquals(0, AUDIT.extrema().referenceExportAuthorityAllowedCount());
		assertEquals(0, AUDIT.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, AUDIT.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void supportRejectsInvalidInputs() {
		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary lookup =
				sampleLookupSupport();
		PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary dimensional =
				sampleDimensionalResidual();

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.support(null, dimensional, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.support(lookup, null, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.support(lookup, dimensional, ""));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_dimensional_support_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(AUDIT.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_support_scenario,current_lookup_and_dimensional_blocked,dimensional_missing_result_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_support_scenario,lookup_and_dimensional_openfoam_support_ready,cfd_dimensional_support_ready,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_support_scenario,lookup_and_dimensional_openfoam_support_ready,reference_export_authority_allowed,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_support_summary,all_scenarios,max_supported_dimensional_target_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_dimensional_support_summary,all_scenarios,runtime_coupling_allowed_count,0,count,")));
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.OpenFoamDimensionalSupportScenario scenario(
			PropellerArchiveCtCpJOpenFoamDimensionalSupportGate.CtCpJOpenFoamDimensionalSupportGateAudit audit,
			String name
	) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow();
	}

	private static PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary sampleLookupSupport() {
		return new PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary(
				true,
				true,
				9,
				6,
				9,
				0,
				0,
				6,
				0,
				0,
				6,
				3,
				true,
				false,
				false,
				false,
				"READY",
				"openfoam-lookup-support-ready",
				"sample-lookup-support"
		);
	}

	private static PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary
			sampleDimensionalResidual() {
		return new PropellerArchiveCtCpJOpenFoamDimensionalResidualContract.OpenFoamDimensionalResidualSummary(
				true,
				true,
				true,
				6,
				6,
				6,
				6,
				0,
				0,
				6,
				0,
				5,
				0.04,
				0.05,
				0.05,
				0.03,
				0.06,
				5.0e-5,
				true,
				true,
				true,
				false,
				false,
				"READY",
				"openfoam-dimensional-residual-contract-ready",
				"sample-dimensional-residual"
		);
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_openfoam_dimensional_support_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
