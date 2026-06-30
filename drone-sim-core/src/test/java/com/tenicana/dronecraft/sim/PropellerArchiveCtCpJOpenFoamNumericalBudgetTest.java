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

class PropellerArchiveCtCpJOpenFoamNumericalBudgetTest {
	@Test
	void auditBuildsOpenFoamNumericalBudgetsFromRunSetup() {
		PropellerArchiveCtCpJOpenFoamNumericalBudget.CtCpJOpenFoamNumericalBudgetAudit audit =
				PropellerArchiveCtCpJOpenFoamNumericalBudget.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-OpenFOAM-Numerical-Budget-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("mesh"));
		assertTrue(audit.caveat().contains("timestep"));
		assertEquals(39, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(8, audit.numericalRuleRowCount());
		assertEquals(6, audit.numericalBudgetRowCount());
		assertEquals(16, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(8, audit.rules().size());
		assertEquals(6, audit.rows().size());

		PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetSummary summary =
				audit.summary();
		assertEquals(6, summary.numericalBudgetRowCount());
		assertEquals(6, summary.incompressibleAssumptionReadyCount());
		assertEquals(6, summary.timeStepBudgetReadyCount());
		assertEquals(6, summary.numericalBudgetReadyForExternalAuthoringCount());
		assertEquals(2, summary.lowReynoldsTransitionReviewRequiredCount());
		assertEquals(0, summary.currentCaseRunnableCount());
		assertEquals(0.16263075459130397, summary.maxHelicalTipMach(), 1.0e-15);
		assertEquals(4_793.703209742471, summary.minReynoldsStationChordNumber(), 1.0e-12);
		assertEquals(28_145.342361325085, summary.maxReynoldsStationChordNumber(), 1.0e-12);
		assertEquals(9.921875e-4, summary.minNearBladeCellSizeMeters(), 1.0e-15);
		assertEquals(9.143430011271411e-6, summary.minSuggestedTimeStepSeconds(), 1.0e-18);
		assertEquals(5.048306913948163e-5, summary.maxSuggestedTimeStepSeconds(), 1.0e-18);
		assertEquals(825.7627488179439,
				summary.maxStepsPerRevolutionAtSuggestedTimeStep(), 1.0e-12);
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("review-openfoam-mesh-yplus-and-time-step-against-run-setup",
				summary.nextRequiredAction());
	}

	@Test
	void rulesCaptureMachCourantDomainAndLeakBoundaries() {
		PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRule mach =
				PropellerArchiveCtCpJOpenFoamNumericalBudget.rule(
						"incompressible_tip_mach_budget");
		assertTrue(mach.currentSatisfied());
		assertTrue(mach.requirement().contains("0.30"));

		PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRule courant =
				PropellerArchiveCtCpJOpenFoamNumericalBudget.rule("courant_timestep_budget");
		assertTrue(courant.requirement().contains("Courant"));
		assertEquals("bind-openfoam-deltaT-to-run-setup-budget", courant.nextRequiredAction());

		PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRule reynolds =
				PropellerArchiveCtCpJOpenFoamNumericalBudget.rule("low_reynolds_model_review");
		assertFalse(reynolds.currentSatisfied());
		assertTrue(reynolds.postReviewSatisfied());

		PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRule leak =
				PropellerArchiveCtCpJOpenFoamNumericalBudget.rule(
						"no_runtime_or_gameplay_auto_apply");
		assertTrue(leak.currentSatisfied());
		assertEquals("keep-runtime-coupling-and-gameplay-auto-apply-closed",
				leak.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamNumericalBudget.rule("missing"));
	}

	@Test
	void highDomainBudgetIsTipCourantLimitedAndIncompressible() {
		PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow high =
				PropellerArchiveCtCpJOpenFoamNumericalBudget.row("apDrone", "high_domain_max_rpm");

		assertEquals("apDrone/high_domain_max_rpm", high.caseKey());
		assertEquals(0.73152, high.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(7_946.7, high.queryRpm(), 1.0e-9);
		assertEquals(0.12954, high.propellerDiameterMeters(), 1.0e-12);
		assertEquals(0.16263075459130397, high.helicalTipMach(), 1.0e-15);
		assertTrue(high.incompressibleAssumptionReady());
		assertFalse(high.lowReynoldsTransitionReviewRequired());
		assertEquals(0.00101203125, high.nearBladeCellSizeMeters(), 1.0e-15);
		assertEquals(0.00269875, high.wakeCoreCellSizeMeters(), 1.0e-15);
		assertEquals(1.03632, high.farFieldRadiusMeters(), 1.0e-12);
		assertEquals(0.51816, high.upstreamLengthMeters(), 1.0e-12);
		assertEquals(1.55448, high.downstreamLengthMeters(), 1.0e-12);
		assertEquals(4.194613277628869e-5, high.azimuthLimitedTimeStepSeconds(), 1.0e-18);
		assertEquals(9.143430011271413e-6, high.bladeCourantLimitedTimeStepSeconds(), 1.0e-18);
		assertEquals(2.1502897789682114e-4,
				high.freestreamCourantLimitedTimeStepSeconds(), 1.0e-18);
		assertEquals(high.bladeCourantLimitedTimeStepSeconds(),
				high.suggestedTimeStepSeconds(), 1.0e-18);
		assertEquals(0.4359605716234232, high.azimuthDegreesPerStep(), 1.0e-15);
		assertEquals(0.5, high.bladeCellCourantAtSuggestedTimeStep(), 1.0e-12);
		assertEquals(0.04252185031386221,
				high.freestreamCourantAtSuggestedTimeStep(), 1.0e-15);
		assertEquals(825.7627488179437,
				high.stepsPerRevolutionAtSuggestedTimeStep(), 1.0e-12);
		assertTrue(high.timeStepBudgetReady());
		assertTrue(high.numericalBudgetReadyForExternalAuthoring());
		assertFalse(high.currentCaseRunnable());
		assertFalse(high.runtimeCouplingAllowed());
		assertFalse(high.gameplayAutoApplyAllowed());
		assertEquals("BLOCKED", high.status());
	}

	@Test
	void staticAnchorsFlagLowReynoldsReviewButKeepTimestepReady() {
		PropellerArchiveCtCpJOpenFoamNumericalBudget.OpenFoamNumericalBudgetRow staticAnchor =
				PropellerArchiveCtCpJOpenFoamNumericalBudget.row(
						"racingQuad", "static_anchor_low_rpm");

		assertEquals(0.0, staticAnchor.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(4_793.703209742471,
				staticAnchor.reynoldsStationChordNumber(), 1.0e-12);
		assertTrue(staticAnchor.lowReynoldsTransitionReviewRequired());
		assertTrue(staticAnchor.incompressibleAssumptionReady());
		assertEquals(0.0, staticAnchor.freestreamCourantAtSuggestedTimeStep(), 1.0e-12);
		assertEquals(0.5, staticAnchor.bladeCellCourantAtSuggestedTimeStep(), 1.0e-12);
		assertEquals(804.247719318987,
				staticAnchor.stepsPerRevolutionAtSuggestedTimeStep(), 1.0e-12);
		assertTrue(staticAnchor.timeStepBudgetReady());
		assertTrue(staticAnchor.numericalBudgetReadyForExternalAuthoring());
		assertEquals("review-low-reynolds-static-anchor-openfoam-model",
				staticAnchor.nextRequiredAction());
	}

	@Test
	void rowFactoryRejectsMissingInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamNumericalBudget.row(null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJOpenFoamNumericalBudget.row("heavyLift", "mid_domain_mid_rpm"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJOpenFoamNumericalBudget.CtCpJOpenFoamNumericalBudgetAudit audit =
				PropellerArchiveCtCpJOpenFoamNumericalBudget.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_openfoam_numerical_budget_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_numerical_budget_rule,courant_timestep_budget,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_numerical_budget,racingQuad,static_anchor_low_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_numerical_budget,apDrone,high_domain_max_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_numerical_budget_summary,all_rows,low_reynolds_transition_review_required_count,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_openfoam_numerical_budget_method,external_mesh_timestep_rule,method,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_openfoam_numerical_budget_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
