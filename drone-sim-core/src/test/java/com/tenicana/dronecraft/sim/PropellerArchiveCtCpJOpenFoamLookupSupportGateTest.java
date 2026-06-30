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

class PropellerArchiveCtCpJOpenFoamLookupSupportGateTest {
	@Test
	void auditCombinesLookupAcceptanceAndOpenFoamResults() {
		PropellerArchiveCtCpJOpenFoamLookupSupportGate.CtCpJOpenFoamLookupSupportGateAudit audit =
				PropellerArchiveCtCpJOpenFoamLookupSupportGate.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Lookup-Support-Gate-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("cannot replace wind-tunnel acceptance"));
		assertEquals(114, audit.packetRowCount());
		assertEquals(7, audit.sourceReferenceRowCount());
		assertEquals(5, audit.scenarioSampleCount());
		assertEquals(19, audit.scenarioMetricRowCount());
		assertEquals(11, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(5, audit.scenarios().size());

		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary current =
				scenario(audit, "current_lookup_and_cfd_blocked").summary();
		assertFalse(current.lookupAcceptanceReady());
		assertFalse(current.openFoamResultContractReady());
		assertEquals(9, current.expectedLookupTargetCount());
		assertEquals(6, current.expectedOpenFoamResultCaseCount());
		assertEquals(0, current.acceptedLookupTargetCount());
		assertEquals(6, current.cfdMissingResultCount());
		assertEquals(0, current.cfdSupportedLookupTargetCount());
		assertEquals(3, current.cfdGeometryUnsupportedLookupTargetCount());
		assertEquals("lookup-acceptance-not-ready", current.message());

		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary missing =
				scenario(audit, "lookup_ready_cfd_results_missing").summary();
		assertTrue(missing.lookupAcceptanceReady());
		assertFalse(missing.openFoamResultContractReady());
		assertEquals(9, missing.acceptedLookupTargetCount());
		assertEquals(6, missing.cfdMissingResultCount());
		assertEquals("openfoam-results-missing", missing.message());

		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary lookupFailed =
				scenario(audit, "cfd_ready_lookup_acceptance_failed").summary();
		assertFalse(lookupFailed.lookupAcceptanceReady());
		assertTrue(lookupFailed.openFoamResultContractReady());
		assertEquals(8, lookupFailed.acceptedLookupTargetCount());
		assertEquals(1, lookupFailed.failedLookupTargetCount());
		assertEquals("lookup-acceptance-not-ready", lookupFailed.message());

		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary cfdFailed =
				scenario(audit, "lookup_ready_cfd_residual_failed").summary();
		assertTrue(cfdFailed.lookupAcceptanceReady());
		assertFalse(cfdFailed.openFoamResultContractReady());
		assertEquals(1, cfdFailed.cfdFailedResultCount());
		assertEquals("openfoam-residual-gate-failed", cfdFailed.message());

		PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportSummary ready =
				scenario(audit, "lookup_and_cfd_ready").summary();
		assertTrue(ready.lookupAcceptanceReady());
		assertTrue(ready.openFoamResultContractReady());
		assertTrue(ready.cfdLookupSupportReady());
		assertEquals(6, ready.cfdSupportedLookupTargetCount());
		assertEquals(3, ready.cfdGeometryUnsupportedLookupTargetCount());
		assertFalse(ready.referenceExportAuthorityAllowed());
		assertFalse(ready.runtimeCouplingAllowed());
		assertFalse(ready.gameplayAutoApplyAllowed());
		assertEquals("openfoam-lookup-support-ready", ready.message());

		assertEquals(5, audit.extrema().scenarioCount());
		assertEquals(1, audit.extrema().readyScenarioCount());
		assertEquals(4, audit.extrema().blockedScenarioCount());
		assertEquals(9, audit.extrema().maxAcceptedLookupTargetCount());
		assertEquals(6, audit.extrema().maxCfdSupportedLookupTargetCount());
		assertEquals(3, audit.extrema().maxCfdGeometryUnsupportedLookupTargetCount());
		assertEquals(6, audit.extrema().maxCfdMissingResultCount());
		assertEquals(1, audit.extrema().maxCfdFailedResultCount());
		assertEquals(0, audit.extrema().referenceExportAuthorityAllowedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
	}

	@Test
	void supportRejectsInvalidInputs() {
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceSummary lookup =
				PropellerArchiveCtCpJLookupAcceptanceGate.audit().scenarios().stream()
						.filter(scenario -> "synthetic_all_lookup_targets_pass".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();
		PropellerArchiveCtCpJOpenFoamResultContract.OpenFoamResultContractSummary cfd =
				PropellerArchiveCtCpJOpenFoamResultContract.audit().scenarios().stream()
						.filter(scenario -> "synthetic_openfoam_results_all_pass".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.summary();

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamLookupSupportGate.support(null, cfd, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamLookupSupportGate.support(lookup, null, "source"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamLookupSupportGate.support(lookup, cfd, ""));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJOpenFoamLookupSupportGate.CtCpJOpenFoamLookupSupportGateAudit audit =
				PropellerArchiveCtCpJOpenFoamLookupSupportGate.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_lookup_support_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_scenario,current_lookup_and_cfd_blocked,cfd_missing_result_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_scenario,lookup_and_cfd_ready,cfd_lookup_support_ready,true,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_scenario,lookup_and_cfd_ready,reference_export_authority_allowed,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_summary,all_scenarios,max_cfd_supported_lookup_target_count,6,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_lookup_support_summary,all_scenarios,runtime_coupling_allowed_count,0,count,")));
	}

	private static PropellerArchiveCtCpJOpenFoamLookupSupportGate.OpenFoamLookupSupportScenario scenario(
			PropellerArchiveCtCpJOpenFoamLookupSupportGate.CtCpJOpenFoamLookupSupportGateAudit audit,
			String name
	) {
		return audit.scenarios()
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
							"docs/data/propeller_archive_ct_cp_j_openfoam_lookup_support_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
