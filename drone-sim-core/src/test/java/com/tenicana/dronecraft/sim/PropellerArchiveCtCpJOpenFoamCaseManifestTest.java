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

class PropellerArchiveCtCpJOpenFoamCaseManifestTest {
	@Test
	void auditBuildsGeometryBackedExternalCaseManifest() {
		PropellerArchiveCtCpJOpenFoamCaseManifest.CtCpJOpenFoamCaseManifestAudit audit =
				PropellerArchiveCtCpJOpenFoamCaseManifest.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Case-Manifest-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("external case keys"));
		assertTrue(audit.caveat().contains("dimensional reference materialization readiness"));
		assertEquals(57, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(26, audit.requiredChannelRowCount());
		assertEquals(6, audit.manifestCaseRowCount());
		assertEquals(16, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(26, audit.channels().size());
		assertEquals(6, audit.rows().size());

		assertEquals(6, audit.summary().manifestCaseCount());
		assertEquals(2, audit.summary().staticAnchorCaseCount());
		assertEquals(4, audit.summary().nonStaticCaseCount());
		assertEquals(6, audit.summary().postReviewCaseBuildableCount());
		assertEquals(0, audit.summary().currentCaseRunnableCount());
		assertEquals(6, audit.summary().sourceCaseSha256RequiredCount());
		assertEquals(0, audit.summary().currentSourceCaseSha256AvailableCount());
		assertEquals(0, audit.summary().referenceMaterializationReadyCaseCount());
		assertEquals(36, audit.summary().blockedOpenFoamReferenceRowTotal());
		assertEquals(66, audit.summary().requiredCoefficientChannelTotal());
		assertEquals(102, audit.summary().requiredDimensionalChannelTotal());
		assertEquals(156, audit.summary().requiredManifestChannelTotal());
		assertEquals(0, audit.summary().runtimeCouplingAllowedCount());
		assertEquals(0, audit.summary().gameplayAutoApplyAllowedCount());
		assertEquals("current_lookup_and_openfoam_blocked",
				audit.summary().currentReferenceMaterializationScenarioName());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				audit.summary().currentReferenceMaterializationNextRequiredAction());
	}

	@Test
	void channelsJoinCoefficientAndDimensionalContractsWithoutRuntimeLeak() {
		PropellerArchiveCtCpJOpenFoamCaseManifest.OpenFoamCaseManifestChannel ct =
				PropellerArchiveCtCpJOpenFoamCaseManifest.channel("cfd_thrust_coefficient_ct");
		assertEquals("coefficient", ct.unit());
		assertEquals("coefficient_result", ct.evidenceRole());
		assertTrue(ct.required());
		assertTrue(ct.coefficientResultContractChannel());
		assertFalse(ct.dimensionalResidualContractChannel());
		assertEquals(0.08, ct.maxResidualRatio(), 1.0e-12);

		PropellerArchiveCtCpJOpenFoamCaseManifest.OpenFoamCaseManifestChannel ctResidual =
				PropellerArchiveCtCpJOpenFoamCaseManifest.channel("ct_residual_to_wind_tunnel");
		assertEquals("ratio", ctResidual.unit());
		assertEquals("coefficient_residual", ctResidual.evidenceRole());
		assertTrue(ctResidual.coefficientResultContractChannel());

		PropellerArchiveCtCpJOpenFoamCaseManifest.OpenFoamCaseManifestChannel induced =
				PropellerArchiveCtCpJOpenFoamCaseManifest.channel(
						"cfd_induced_velocity_meters_per_second");
		assertEquals("m/s", induced.unit());
		assertFalse(induced.coefficientResultContractChannel());
		assertTrue(induced.dimensionalResidualContractChannel());
		assertEquals(0.06, induced.maxResidualRatio(), 1.0e-12);

		PropellerArchiveCtCpJOpenFoamCaseManifest.OpenFoamCaseManifestChannel convergence =
				PropellerArchiveCtCpJOpenFoamCaseManifest.channel("solver_convergence_residual");
		assertTrue(convergence.coefficientResultContractChannel());
		assertTrue(convergence.dimensionalResidualContractChannel());
		assertEquals(1.0e-4, convergence.maxResidualRatio(), 1.0e-15);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamCaseManifest.channel("missing"));
	}

	@Test
	void manifestRowsCarryStableCaseKeysAndBlockCurrentExecution() {
		PropellerArchiveCtCpJOpenFoamCaseManifest.OpenFoamCaseManifestRow apMid =
				PropellerArchiveCtCpJOpenFoamCaseManifest.row("apDrone", "mid_domain_mid_rpm");

		assertEquals("apDrone/mid_domain_mid_rpm", apMid.caseKey());
		assertEquals("external-openfoam-steady-rotor-cfd", apMid.solverFamily());
		assertEquals("external-openfoam-steady-rotor-case-manifest", apMid.caseTemplateFamily());
		assertEquals("da4052 5.0x3.75 - 3", apMid.performanceMatchId());
		assertEquals("da4052 5.0x3.75", apMid.meshGeometryId());
		assertEquals(0.4064, apMid.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(4_712.25, apMid.queryRpm(), 1.0e-9);
		assertEquals(0.4064 / Math.PI, apMid.equivalentProjectMu(), 1.0e-12);
		assertEquals(4, apMid.minimumNeighborRows());
		assertFalse(apMid.staticAnchorCase());
		assertEquals(11, apMid.requiredCoefficientChannelCount());
		assertEquals(17, apMid.requiredDimensionalChannelCount());
		assertEquals(26, apMid.requiredManifestChannelCount());
		assertTrue(apMid.sourceCaseSha256Required());
		assertFalse(apMid.currentSourceCaseSha256Available());
		assertFalse(apMid.currentCaseRunnable());
		assertTrue(apMid.postReviewCaseBuildable());
		assertEquals("current_lookup_and_openfoam_blocked",
				apMid.referenceMaterializationScenarioName());
		assertFalse(apMid.referenceMaterializationReady());
		assertEquals(6, apMid.blockedOpenFoamReferenceRowCount());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				apMid.referenceMaterializationNextRequiredAction());
		assertFalse(apMid.runtimeCouplingAllowed());
		assertFalse(apMid.gameplayAutoApplyAllowed());
		assertEquals("BLOCKED", apMid.status());
		assertEquals("execute-clearance-evidence-ledger-before-reviewed-payload-output",
				apMid.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamCaseManifest.row("heavyLift", "mid_domain_mid_rpm"));
	}

	@Test
	void readyReferenceMaterializationAdvancesRowsToExternalCaseHashAction() {
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase apMid =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("apDrone", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
				.OpenFoamDimensionalReferenceMaterializationScenario ready =
						PropellerArchiveCtCpJOpenFoamDimensionalReferenceMaterializationGate
								.scenario("lookup_reference_and_openfoam_ready");

		PropellerArchiveCtCpJOpenFoamCaseManifest.OpenFoamCaseManifestRow row =
				PropellerArchiveCtCpJOpenFoamCaseManifest.row(apMid, ready);

		assertEquals("lookup_reference_and_openfoam_ready", row.referenceMaterializationScenarioName());
		assertTrue(row.referenceMaterializationReady());
		assertEquals(0, row.blockedOpenFoamReferenceRowCount());
		assertEquals("materialize-openfoam-dimensional-reference-table-after-review",
				row.referenceMaterializationNextRequiredAction());
		assertEquals("author-external-openfoam-case-template-and-record-case-sha256",
				row.nextRequiredAction());
		assertTrue(row.note().contains("current case hash is absent"));
		assertFalse(row.currentCaseRunnable());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
	}

	@Test
	void rowFactoryRejectsMissingUnsupportedOrMalformedInputs() {
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase heavy =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("heavyLift", "mid_domain_mid_rpm");
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase apMid =
				PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow("apDrone", "mid_domain_mid_rpm");

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamCaseManifest.row(null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamCaseManifest.row(apMid, null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamCaseManifest.row(heavy));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJOpenFoamCaseManifest.CtCpJOpenFoamCaseManifestAudit audit =
				PropellerArchiveCtCpJOpenFoamCaseManifest.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_case_manifest_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_case_manifest_source,openfoam_reference_materialization_gate,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_case_manifest_channel,cfd_thrust_coefficient_ct,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_case_manifest_case,apDrone,mid_domain_mid_rpm,")
						&& line.contains("materialization=current_lookup_and_openfoam_blocked")
						&& line.contains("blocked_reference_rows=6")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_case_manifest_case,racingQuad,static_anchor_low_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_case_manifest_summary,all_cases,reference_materialization_ready_case_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_case_manifest_summary,all_cases,blocked_openfoam_reference_row_total,36,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_case_manifest_summary,all_cases,required_dimensional_channel_total,102,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_case_manifest_method,external_case_manifest_rule,method,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_openfoam_case_manifest_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
