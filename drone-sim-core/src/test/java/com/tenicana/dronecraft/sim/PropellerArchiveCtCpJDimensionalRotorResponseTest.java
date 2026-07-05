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

class PropellerArchiveCtCpJDimensionalRotorResponseTest {
	@Test
	void auditConvertsAcceptedLookupExecutionRowsToDimensionalReferences() {
		PropellerArchiveCtCpJDimensionalRotorResponse.CtCpJDimensionalRotorResponseAudit audit =
				PropellerArchiveCtCpJDimensionalRotorResponse.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Dimensional-Rotor-Response-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("SI thrust"));
		assertTrue(audit.caveat().contains("handoff-blocked"));
		assertTrue(audit.caveat().contains("archive curve-shape"));
		assertEquals(41, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(8, audit.dimensionalRuleRowCount());
		assertEquals(8, audit.scenarioRowCount());
		assertEquals(16, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(8, audit.rules().size());
		assertEquals(8, audit.scenarios().size());

		PropellerArchiveCtCpJDimensionalRotorResponse.DimensionalResponseSummary summary =
				audit.summary();
		assertEquals(8, summary.scenarioCount());
		assertEquals(2, summary.readyScenarioCount());
		assertEquals(6, summary.blockedScenarioCount());
		assertEquals(0.19840592872073343, summary.maxThrustNewtons(), 1.0e-15);
		assertEquals(1.0985575597709705, summary.maxShaftPowerWatts(), 1.0e-15);
		assertEquals(0.0022262087016841664, summary.maxShaftTorqueNewtonMeters(), 1.0e-18);
		assertEquals(15.05417563905381, summary.maxDiskLoadingNewtonsPerSquareMeter(), 1.0e-14);
		assertEquals(scenario("synthetic_mid_bilinear_pass").idealInducedVelocityMetersPerSecond(),
				summary.maxIdealInducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(4.1346110856, summary.maxAxialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(0.1287827767456875, summary.maxTotalThrustToWeightRatio(), 1.0e-15);
		assertEquals(2, summary.archiveCurveShapeGuardInheritedReadyCount());
		assertEquals(9, summary.maxNegativeThrustTailExecutionInputRowCount());
		assertTrue(summary.maxArchiveCurveEtaFormulaResidual()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
		assertTrue(summary.maxArchiveCurveCtIncrease()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("bind-reviewed-dimensional-rotor-response-to-openfoam-and-runtime-fit-review",
				summary.nextRequiredAction());
	}

	@Test
	void rulesKeepDimensionalResponseSeparateFromRuntimeMutation() {
		PropellerArchiveCtCpJDimensionalRotorResponse.DimensionalResponseRule accepted =
				PropellerArchiveCtCpJDimensionalRotorResponse.rule("accepted_lookup_execution_required");
		assertTrue(accepted.required());
		assertFalse(accepted.currentSatisfied());
		assertTrue(accepted.syntheticTargetSatisfied());

		PropellerArchiveCtCpJDimensionalRotorResponse.DimensionalResponseRule inheritedShape =
				PropellerArchiveCtCpJDimensionalRotorResponse.rule("archive_curve_shape_guard_inherited");
		assertTrue(inheritedShape.required());
		assertFalse(inheritedShape.currentSatisfied());
		assertTrue(inheritedShape.syntheticTargetSatisfied());
		assertEquals("carry-archive-curve-shape-guard-into-dimensional-response",
				inheritedShape.nextRequiredAction());

		PropellerArchiveCtCpJDimensionalRotorResponse.DimensionalResponseRule formula =
				PropellerArchiveCtCpJDimensionalRotorResponse.rule("propeller_coefficient_equations");
		assertTrue(formula.requirement().contains("T=CT"));
		assertEquals("verify-dimensioned-propeller-coefficient-closure", formula.nextRequiredAction());

		PropellerArchiveCtCpJDimensionalRotorResponse.DimensionalResponseRule momentum =
				PropellerArchiveCtCpJDimensionalRotorResponse.rule("momentum_induced_velocity_reference");
		assertTrue(momentum.requirement().contains("axial actuator-disk"));
		assertTrue(momentum.requirement().contains("static hover"));

		PropellerArchiveCtCpJDimensionalRotorResponse.DimensionalResponseRule runtime =
				PropellerArchiveCtCpJDimensionalRotorResponse.rule("runtime_leak_guard");
		assertTrue(runtime.currentSatisfied());
		assertEquals("keep-runtime-coupling-closed", runtime.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJDimensionalRotorResponse.rule("missing"));
	}

	@Test
	void staticAnchorAndMidDomainScenariosCloseDimensionalFormulas() {
		PropellerArchiveCtCpJDimensionalRotorResponse.RotorDimensionalResponse staticAnchor =
				scenario("synthetic_static_anchor_exact");
		assertTrue(staticAnchor.lookupExecutionAccepted());
		assertTrue(staticAnchor.dimensionalResponseReady());
		assertEquals("DIMENSIONAL_RESPONSE_READY", staticAnchor.status());
		assertEquals(0.0, staticAnchor.axialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(0.12954, staticAnchor.propellerDiameterMeters(), 1.0e-12);
		assertEquals(1_477.8, staticAnchor.queryRpm(), 1.0e-12);
		assertEquals(24.63, staticAnchor.revolutionsPerSecond(), 1.0e-12);
		assertEquals(0.025110868242593, staticAnchor.thrustNewtons(), 1.0e-15);
		assertEquals(0.026705995970315, staticAnchor.shaftPowerWatts(), 1.0e-15);
		assertEquals(0.000172569682049040, staticAnchor.shaftTorqueNewtonMeters(), 1.0e-18);
		assertEquals(0.881858670062071, staticAnchor.idealInducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(0.022144236872517, staticAnchor.idealMomentumPowerWatts(), 1.0e-15);
		assertEquals(0.016299146702053, staticAnchor.totalThrustToWeightRatio(), 1.0e-15);
		assertTrue(staticAnchor.archiveCurveShapeGuardInherited());
		assertEquals(9, staticAnchor.negativeThrustTailExecutionInputRowCount());
		assertFalse(staticAnchor.runtimeCouplingAllowed());
		assertFalse(staticAnchor.gameplayAutoApplyAllowed());

		PropellerArchiveCtCpJDimensionalRotorResponse.RotorDimensionalResponse mid =
				scenario("synthetic_mid_bilinear_pass");
		assertTrue(mid.dimensionalResponseReady());
		assertEquals(0.4064, mid.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(4_712.25, mid.queryRpm(), 1.0e-9);
		assertEquals(78.5375, mid.revolutionsPerSecond(), 1.0e-12);
		assertEquals(493.4656660626167, mid.angularVelocityRadiansPerSecond(), 1.0e-12);
		assertEquals(4.1346110856, mid.axialAdvanceSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(0.09325, mid.thrustCoefficientCt(), 1.0e-12);
		assertEquals(0.05075, mid.powerCoefficientCp(), 1.0e-12);
		assertEquals(0.7467349753694581, mid.propulsiveEfficiencyEta(), 1.0e-15);
		assertEquals(0.19840592872073343, mid.thrustNewtons(), 1.0e-15);
		assertEquals(1.0985575597709705, mid.shaftPowerWatts(), 1.0e-15);
		assertEquals(0.0022262087016841664, mid.shaftTorqueNewtonMeters(), 1.0e-18);
		assertEquals(0.013179461531325914, mid.diskAreaSquareMeters(), 1.0e-18);
		assertEquals(15.05417563905381, mid.diskLoadingNewtonsPerSquareMeter(), 1.0e-14);
		assertEquals(expectedAxialMomentumInducedVelocity(mid),
				mid.idealInducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(expectedIdealMomentumPower(mid), mid.idealMomentumPowerWatts(), 1.0e-15);
		assertEquals(mid.idealMomentumPowerWatts() / mid.shaftPowerWatts(),
				mid.idealMomentumPowerOverShaftPower(), 1.0e-15);
		assertTrue(mid.idealMomentumPowerOverShaftPower() > 0.90);
		assertTrue(mid.archiveCurveShapeGuardInherited());
		assertEquals(9, mid.negativeThrustTailExecutionInputRowCount());
		assertTrue(mid.archiveCurveEtaFormulaResidual()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_ETA_FORMULA_RESIDUAL);
		assertTrue(mid.archiveCurveCtIncrease()
				<= PropellerArchiveCtCpJArchiveCurveShapeReview.MAX_CT_INCREASE_TOLERANCE);
	}

	@Test
	void blockedLookupRowsRemainDimensionalDiagnosticsOnly() {
		for (String name : List.of(
				"current_handoff_blocked_no_execution",
				"current_no_reviewed_rows",
				"synthetic_handoff_curve_shape_guard_blocked",
				"synthetic_missing_neighbor_blocked",
				"synthetic_high_j_extrapolation_rejected",
				"synthetic_cp_guard_failed")) {
			PropellerArchiveCtCpJDimensionalRotorResponse.RotorDimensionalResponse response =
					scenario(name);
			assertFalse(response.dimensionalResponseReady());
			assertEquals("LOOKUP_EXECUTION_BLOCKED", response.status());
			assertEquals(0.0, response.thrustNewtons(), 1.0e-12);
			assertEquals(0.0, response.shaftPowerWatts(), 1.0e-12);
			assertEquals(0.0, response.totalThrustToWeightRatio(), 1.0e-12);
			assertFalse(response.runtimeCouplingAllowed());
			assertFalse(response.gameplayAutoApplyAllowed());
		}
		assertEquals("source-license-review-required",
				scenario("current_handoff_blocked_no_execution").message());
		assertEquals("reviewed-ct-cp-j-rows-missing", scenario("current_no_reviewed_rows").message());
		assertEquals("archive-curve-shape-guard-not-ready",
				scenario("synthetic_handoff_curve_shape_guard_blocked").message());
		assertEquals("cp-positive-guard-failed", scenario("synthetic_cp_guard_failed").message());
	}

	@Test
	void customDensityScalesThrustAndPowerButKeepsIdealInducedVelocityForCoefficientRow() {
		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult mid =
				PropellerArchiveCtCpJLookupExecutionContract.audit()
						.scenarios()
						.stream()
						.filter(scenario -> "synthetic_mid_bilinear_pass".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.result();
		PropellerArchiveCtCpJDimensionalRotorResponse.RotorDimensionalResponse standard =
				PropellerArchiveCtCpJDimensionalRotorResponse.response(mid);
		PropellerArchiveCtCpJDimensionalRotorResponse.RotorDimensionalResponse dense =
				PropellerArchiveCtCpJDimensionalRotorResponse.response(mid, DroneConfig.apDrone(), 2.45);

		assertEquals(standard.thrustNewtons() * 2.0, dense.thrustNewtons(), 1.0e-15);
		assertEquals(standard.shaftPowerWatts() * 2.0, dense.shaftPowerWatts(), 1.0e-15);
		assertEquals(standard.idealInducedVelocityMetersPerSecond(),
				dense.idealInducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(standard.idealMomentumPowerWatts() * 2.0,
				dense.idealMomentumPowerWatts(), 1.0e-15);
	}

	@Test
	void responseRejectsInvalidInputs() {
		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult mid =
				PropellerArchiveCtCpJLookupExecutionContract.audit()
						.scenarios()
						.stream()
						.filter(scenario -> "synthetic_mid_bilinear_pass".equals(scenario.scenarioName()))
						.findFirst()
						.orElseThrow()
						.result();

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJDimensionalRotorResponse.response(null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJDimensionalRotorResponse.response(mid, null, 1.225));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJDimensionalRotorResponse.response(mid, DroneConfig.apDrone(), 0.0));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJDimensionalRotorResponse.CtCpJDimensionalRotorResponseAudit audit =
				PropellerArchiveCtCpJDimensionalRotorResponse.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_dimensional_rotor_response_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_dimensional_rotor_scenario,current_handoff_blocked_no_execution,LOOKUP_EXECUTION_BLOCKED,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_dimensional_rotor_rule,archive_curve_shape_guard_inherited,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_dimensional_rotor_rule,propeller_coefficient_equations,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_dimensional_rotor_scenario,synthetic_handoff_curve_shape_guard_blocked,LOOKUP_EXECUTION_BLOCKED,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_dimensional_rotor_scenario,synthetic_mid_bilinear_pass,DIMENSIONAL_RESPONSE_READY,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_dimensional_rotor_scenario,synthetic_cp_guard_failed,LOOKUP_EXECUTION_BLOCKED,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_dimensional_rotor_summary,all_scenarios,ready_scenario_count,,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_dimensional_rotor_summary,all_scenarios,archive_curve_shape_guard_inherited_ready_count,,2,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_dimensional_rotor_summary,all_scenarios,runtime_coupling_allowed_count,,0,count,")));
	}

	private static PropellerArchiveCtCpJDimensionalRotorResponse.RotorDimensionalResponse scenario(String name) {
		return PropellerArchiveCtCpJDimensionalRotorResponse.audit()
				.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.response();
	}

	private static double expectedAxialMomentumInducedVelocity(
			PropellerArchiveCtCpJDimensionalRotorResponse.RotorDimensionalResponse response
	) {
		double axialAdvanceSpeed = Math.max(0.0, response.axialAdvanceSpeedMetersPerSecond());
		double diskTerm = 2.0 * response.thrustNewtons()
				/ (response.airDensityKgPerCubicMeter() * response.diskAreaSquareMeters());
		return 0.5 * (Math.sqrt(axialAdvanceSpeed * axialAdvanceSpeed + diskTerm) - axialAdvanceSpeed);
	}

	private static double expectedIdealMomentumPower(
			PropellerArchiveCtCpJDimensionalRotorResponse.RotorDimensionalResponse response
	) {
		return response.thrustNewtons()
				* (Math.max(0.0, response.axialAdvanceSpeedMetersPerSecond())
				+ expectedAxialMomentumInducedVelocity(response));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_dimensional_rotor_response_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
