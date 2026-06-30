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

class PropellerArchiveCtCpJOpenFoamRunSetupTest {
	@Test
	void auditBuildsExternalOpenFoamRunSetupRows() {
		PropellerArchiveCtCpJOpenFoamRunSetup.CtCpJOpenFoamRunSetupAudit audit =
				PropellerArchiveCtCpJOpenFoamRunSetup.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Run-Setup-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("external CFD case inputs"));
		assertTrue(audit.caveat().contains("audit-only"));
		assertEquals(38, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(8, audit.runSetupRuleRowCount());
		assertEquals(6, audit.runSetupRowCount());
		assertEquals(15, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(8, audit.rules().size());
		assertEquals(6, audit.rows().size());

		PropellerArchiveCtCpJOpenFoamRunSetup.OpenFoamRunSetupSummary summary = audit.summary();
		assertEquals(6, summary.runSetupRowCount());
		assertEquals(0, summary.currentRunnableCount());
		assertEquals(6, summary.postReviewCaseBuildableCount());
		assertEquals(6, summary.sourceCaseSha256RequiredCount());
		assertEquals(0, summary.currentSourceCaseSha256AvailableCount());
		assertEquals(2, summary.staticAnchorCaseCount());
		assertEquals(0.73152, summary.maxQueryAdvanceRatioJ(), 1.0e-12);
		assertEquals(7_946.7, summary.maxQueryRpm(), 1.0e-9);
		assertEquals(12.550633995456, summary.maxAxialFreestreamSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(0.162630754591304, summary.maxHelicalTipMach(), 1.0e-15);
		assertTrue(summary.minReynoldsStationChordNumber() > 4_500.0);
		assertTrue(summary.maxReynoldsStationChordNumber() > summary.minReynoldsStationChordNumber());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("author-external-openfoam-case-template-and-record-case-sha256",
				summary.nextRequiredAction());
	}

	@Test
	void rulesExposeFreestreamDerivationAndLeakBoundary() {
		PropellerArchiveCtCpJOpenFoamRunSetup.OpenFoamRunSetupRule freestream =
				PropellerArchiveCtCpJOpenFoamRunSetup.rule("freestream_from_advance_ratio");
		assertTrue(freestream.required());
		assertTrue(freestream.currentSatisfied());
		assertTrue(freestream.requirement().contains("V=J*n*D"));

		PropellerArchiveCtCpJOpenFoamRunSetup.OpenFoamRunSetupRule hash =
				PropellerArchiveCtCpJOpenFoamRunSetup.rule("source_case_hash_required");
		assertFalse(hash.currentSatisfied());
		assertTrue(hash.postReviewSatisfied());
		assertEquals("author-external-openfoam-case-template-and-record-case-sha256",
				hash.nextRequiredAction());

		PropellerArchiveCtCpJOpenFoamRunSetup.OpenFoamRunSetupRule leak =
				PropellerArchiveCtCpJOpenFoamRunSetup.rule("no_runtime_or_gameplay_auto_apply");
		assertTrue(leak.currentSatisfied());
		assertEquals("keep-runtime-coupling-and-gameplay-auto-apply-closed",
				leak.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamRunSetup.rule("missing"));
	}

	@Test
	void setupRowsDeriveDimensionalOpenFoamConditions() {
		PropellerArchiveCtCpJOpenFoamRunSetup.OpenFoamRunSetupRow apMid =
				PropellerArchiveCtCpJOpenFoamRunSetup.row("apDrone", "mid_domain_mid_rpm");

		assertEquals("apDrone/mid_domain_mid_rpm", apMid.caseKey());
		assertEquals(PropellerArchiveCtCpJOpenFoamValidationPlan.SOLVER_FAMILY,
				apMid.solverFamily());
		assertEquals("external-openfoam-steady-rotor-case-manifest", apMid.caseTemplateFamily());
		assertEquals("da4052 5.0x3.75", apMid.meshGeometryId());
		assertEquals(0.4064, apMid.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(4_712.25, apMid.queryRpm(), 1.0e-9);
		assertEquals(0.4064 / Math.PI, apMid.equivalentProjectMu(), 1.0e-12);
		assertFalse(apMid.staticAnchorCase());
		assertTrue(apMid.sourceCaseSha256Required());
		assertFalse(apMid.currentSourceCaseSha256Available());
		assertFalse(apMid.currentCaseRunnable());
		assertTrue(apMid.postReviewCaseBuildable());
		assertTrue(apMid.runSetupReadyForExternalAuthoring());

		assertEquals(1.225, apMid.airDensityKgPerCubicMeter(), 1.0e-12);
		assertEquals(15.0, apMid.ambientTemperatureCelsius(), 1.0e-12);
		assertEquals(DroneEnvironment.speedOfSoundMetersPerSecond(15.0),
				apMid.speedOfSoundMetersPerSecond(), 1.0e-12);
		assertEquals(PropellerArchiveRotorSpecRetuneReynoldsBudgetGate
						.REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				apMid.dynamicViscosityPascalSeconds(), 1.0e-18);
		assertEquals(0.06477, apMid.rotorRadiusMeters(), 1.0e-12);
		assertEquals(0.12954, apMid.propellerDiameterMeters(), 1.0e-12);
		assertEquals(0.013179461531325914, apMid.diskAreaSquareMeters(), 1.0e-18);
		assertEquals(78.5375, apMid.revolutionsPerSecond(), 1.0e-12);
		assertEquals(493.4656660626167, apMid.angularVelocityRadiansPerSecond(), 1.0e-12);
		assertEquals(4.1346110856, apMid.axialFreestreamSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(31.96177119087569, apMid.tipSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(32.22809064290117, apMid.helicalTipSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(0.09470708531122375, apMid.helicalTipMach(), 1.0e-15);
		assertEquals(0.75, apMid.reynoldsStationFraction(), 1.0e-12);
		assertEquals(apMid.queryAdvanceRatioJ()
						* apMid.revolutionsPerSecond()
						* apMid.propellerDiameterMeters(),
				apMid.axialFreestreamSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(apMid.helicalTipSpeedMetersPerSecond() / apMid.speedOfSoundMetersPerSecond(),
				apMid.helicalTipMach(), 1.0e-15);
		assertEquals(apMid.airDensityKgPerCubicMeter()
						* apMid.reynoldsStationSpeedMetersPerSecond()
						* apMid.representativeBladeChordMeters()
						/ apMid.dynamicViscosityPascalSeconds(),
				apMid.reynoldsStationChordNumber(), 1.0e-9);
		assertFalse(apMid.runtimeCouplingAllowed());
		assertFalse(apMid.gameplayAutoApplyAllowed());
		assertEquals("BLOCKED", apMid.status());
	}

	@Test
	void staticAnchorKeepsZeroFreestreamButNonzeroRotorDiagnostics() {
		PropellerArchiveCtCpJOpenFoamRunSetup.OpenFoamRunSetupRow staticAnchor =
				PropellerArchiveCtCpJOpenFoamRunSetup.row("racingQuad", "static_anchor_low_rpm");

		assertTrue(staticAnchor.staticAnchorCase());
		assertEquals(0.0, staticAnchor.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(0.0, staticAnchor.axialFreestreamSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(staticAnchor.tipSpeedMetersPerSecond(),
				staticAnchor.helicalTipSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(0.02887791941122053, staticAnchor.helicalTipMach(), 1.0e-15);
		assertTrue(staticAnchor.reynoldsStationChordNumber() > 4_500.0);
	}

	@Test
	void rowFactoryRejectsMissingInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamRunSetup.row(null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamRunSetup.row("heavyLift", "mid_domain_mid_rpm"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJOpenFoamRunSetup.CtCpJOpenFoamRunSetupAudit audit =
				PropellerArchiveCtCpJOpenFoamRunSetup.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_run_setup_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_run_setup_rule,freestream_from_advance_ratio,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_run_setup,racingQuad,static_anchor_low_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_run_setup,apDrone,mid_domain_mid_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_run_setup_summary,all_rows,max_helical_tip_mach,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_run_setup_method,external_case_authoring_rule,method,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_openfoam_run_setup_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
