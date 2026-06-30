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

class PropellerArchiveCtCpJLookupExecutionContractTest {
	@Test
	void auditDefinesExecutionContractWithoutRawImportOrRuntimeCoupling() {
		PropellerArchiveCtCpJLookupExecutionContract.CtCpJLookupExecutionContractAudit audit =
				PropellerArchiveCtCpJLookupExecutionContract.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Execution-Contract-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("scattered-fit execution handoff"));
		assertEquals(38, audit.packetRowCount());
		assertEquals(8, audit.sourceReferenceRowCount());
		assertEquals(9, audit.executionRuleRowCount());
		assertEquals(7, audit.scenarioRowCount());
		assertEquals(13, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(9, audit.rules().size());
		assertEquals(7, audit.scenarios().size());

		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionSummary summary = audit.summary();
		assertEquals(7, summary.scenarioCount());
		assertEquals(2, summary.acceptedScenarioCount());
		assertEquals(5, summary.blockedScenarioCount());
		assertEquals(1, summary.handoffBlockedScenarioCount());
		assertEquals(1, summary.noReviewedRowsScenarioCount());
		assertEquals(1, summary.outOfDomainScenarioCount());
		assertEquals(1, summary.missingNeighborScenarioCount());
		assertEquals(1, summary.acceptanceGuardFailedScenarioCount());
		assertEquals(4, summary.maxObservedNeighborRows());
		assertEquals(0.0, summary.maxCtShapeOvershoot(), 1.0e-12);
		assertEquals(0.040, summary.minAcceptedCpCoefficient(), 1.0e-12);
		assertEquals(0.0, summary.maxEtaResidual(), 1.0e-12);
		assertEquals(0.0, summary.maxStaticAnchorError(), 1.0e-12);
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("complete-scattered-surface-fit-execution-handoff-before-lookup-run",
				summary.nextRequiredAction());
	}

	@Test
	void executionRulesKeepSourceAndRuntimeBoundariesClosed() {
		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionRule source =
				PropellerArchiveCtCpJLookupExecutionContract.rule("source_license_review");
		assertTrue(source.required());
		assertFalse(source.currentSatisfied());
		assertFalse(source.callerSuppliedReviewedRowsSatisfied());
		assertTrue(source.syntheticTargetSatisfied());

		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionRule handoff =
				PropellerArchiveCtCpJLookupExecutionContract.rule("scattered_fit_execution_handoff_ready");
		assertTrue(handoff.required());
		assertFalse(handoff.currentSatisfied());
		assertFalse(handoff.callerSuppliedReviewedRowsSatisfied());
		assertTrue(handoff.syntheticTargetSatisfied());
		assertEquals("complete-scattered-surface-fit-execution-handoff-before-lookup-run",
				handoff.nextRequiredAction());

		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionRule extrapolation =
				PropellerArchiveCtCpJLookupExecutionContract.rule("reject_extrapolation");
		assertTrue(extrapolation.currentSatisfied());
		assertTrue(extrapolation.requirement().contains("rejected"));

		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionRule guard =
				PropellerArchiveCtCpJLookupExecutionContract.rule("runtime_leak_guard");
		assertTrue(guard.currentSatisfied());
		assertTrue(guard.callerSuppliedReviewedRowsSatisfied());
		assertEquals("keep-runtime-coupling-and-gameplay-auto-apply-closed", guard.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionContract.rule("missing"));
	}

	@Test
	void reviewedRowsExecuteStaticAnchorAndMidDomainInterpolation() {
		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult blockedHandoff =
				scenario("current_handoff_blocked_no_execution");
		assertFalse(blockedHandoff.acceptedByLookupGate());
		assertEquals("HANDOFF_BLOCKED", blockedHandoff.status());
		assertEquals("source-license-review-required", blockedHandoff.message());
		assertEquals(0, blockedHandoff.observedNeighborRows());
		assertEquals(4, blockedHandoff.minimumNeighborRowsRequired());

		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult current =
				scenario("current_no_reviewed_rows");
		assertFalse(current.acceptedByLookupGate());
		assertEquals("NO_REVIEWED_ROWS", current.status());
		assertEquals("reviewed-ct-cp-j-rows-missing", current.message());
		assertEquals(0, current.observedNeighborRows());
		assertEquals(4, current.minimumNeighborRowsRequired());
		assertFalse(current.runtimeCouplingAllowed());
		assertFalse(current.gameplayAutoApplyAllowed());

		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult staticAnchor =
				scenario("synthetic_static_anchor_exact");
		assertTrue(staticAnchor.acceptedByLookupGate());
		assertEquals("ACCEPTED", staticAnchor.status());
		assertEquals(1, staticAnchor.observedNeighborRows());
		assertEquals(1, staticAnchor.minimumNeighborRowsRequired());
		assertEquals(0.120, staticAnchor.ctCoefficient(), 1.0e-12);
		assertEquals(0.040, staticAnchor.cpCoefficient(), 1.0e-12);
		assertEquals(0.0, staticAnchor.eta(), 1.0e-12);
		assertEquals(0.0, staticAnchor.staticAnchorError(), 1.0e-12);

		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult mid =
				scenario("synthetic_mid_bilinear_pass");
		assertTrue(mid.acceptedByLookupGate());
		assertEquals("lookup-execution-accepted", mid.message());
		assertEquals(4, mid.observedNeighborRows());
		assertEquals(4, mid.minimumNeighborRowsRequired());
		assertEquals(0.5, mid.advanceInterpolationFraction(), 1.0e-12);
		assertEquals(0.5, mid.rpmInterpolationFraction(), 1.0e-12);
		assertEquals(0.09325, mid.ctCoefficient(), 1.0e-12);
		assertEquals(0.05075, mid.cpCoefficient(), 1.0e-12);
		assertEquals(mid.queryAdvanceRatioJ() * mid.ctCoefficient() / mid.cpCoefficient(),
				mid.eta(), 1.0e-12);
		assertEquals(0.0, mid.maxCtShapeOvershoot(), 1.0e-12);
		assertEquals(0.046, mid.minCpCoefficient(), 1.0e-12);
	}

	@Test
	void executionRejectsMissingNeighborsExtrapolationAndCpGuardFailure() {
		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult missing =
				scenario("synthetic_missing_neighbor_blocked");
		assertFalse(missing.acceptedByLookupGate());
		assertEquals("NEIGHBOR_ROWS_MISSING", missing.status());
		assertEquals("reviewed-neighbor-row-missing", missing.message());
		assertEquals(3, missing.observedNeighborRows());

		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult outOfDomain =
				scenario("synthetic_high_j_extrapolation_rejected");
		assertFalse(outOfDomain.acceptedByLookupGate());
		assertEquals("OUT_OF_DOMAIN", outOfDomain.status());
		assertEquals("query-requires-extrapolation-and-is-rejected", outOfDomain.message());
		assertFalse(outOfDomain.insideLookupDomain());

		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult cpFailed =
				scenario("synthetic_cp_guard_failed");
		assertFalse(cpFailed.acceptedByLookupGate());
		assertEquals("ACCEPTANCE_GUARD_FAILED", cpFailed.status());
		assertEquals("cp-positive-guard-failed", cpFailed.message());
		assertTrue(cpFailed.minCpCoefficient()
				< PropellerArchiveCtCpJLookupAcceptanceGate.MIN_CP_COEFFICIENT);
	}

	@Test
	void executionResultFeedsLookupAcceptanceGateMetrics() {
		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary readyHandoff =
				readyHandoff();
		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult accepted =
				PropellerArchiveCtCpJLookupExecutionContract.acceptanceResultFromHandoff(
						readyHandoff,
						midRows(1.0), "apDrone", "mid_domain_mid_rpm");
		assertTrue(accepted.passed());
		assertEquals(4, accepted.observedNeighborRows());
		assertEquals(0.046, accepted.minCpCoefficient(), 1.0e-12);

		PropellerArchiveCtCpJLookupAcceptanceGate.LookupAcceptanceResult failed =
				PropellerArchiveCtCpJLookupExecutionContract.acceptanceResult(
						midRows(1.0e-6), "apDrone", "mid_domain_mid_rpm");
		assertFalse(failed.passed());
		assertEquals("FAIL", failed.status());
	}

	@Test
	void handoffAwareExecutionBlocksBeforeRowsReachLookupRunner() {
		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.CtCpJScatteredSurfaceFitExecutionHandoffAudit audit =
				PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.audit();
		PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary blocked =
				handoffScenario(audit, "current_source_review_blocked_no_execution_input");

		PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult result =
				PropellerArchiveCtCpJLookupExecutionContract.executeFromHandoff(
						blocked, midRows(1.0), "apDrone", "mid_domain_mid_rpm");

		assertFalse(result.acceptedByLookupGate());
		assertEquals("HANDOFF_BLOCKED", result.status());
		assertEquals("source-license-review-required", result.message());
		assertEquals(0, result.observedNeighborRows());
		assertFalse(result.runtimeCouplingAllowed());
		assertFalse(result.gameplayAutoApplyAllowed());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionContract.executeFromHandoff(
						null, List.of(), "apDrone", "mid_domain_mid_rpm"));
	}

	@Test
	void executionRejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionContract.execute(null,
						"apDrone", "mid_domain_mid_rpm"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionContract.execute(List.of(),
						"missing", "mid_domain_mid_rpm"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionContract.execute(
						List.of(new PropellerArchiveCtCpJLookupExecutionContract.LookupGridRow(
								"bad-j", -0.1, 4_000.0, 0.10, 0.04)),
						"apDrone", "mid_domain_mid_rpm"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupExecutionContract.execute(
						List.of(
								new PropellerArchiveCtCpJLookupExecutionContract.LookupGridRow(
										"a", 0.2, 4_000.0, 0.10, 0.04),
								new PropellerArchiveCtCpJLookupExecutionContract.LookupGridRow(
										"b", 0.2, 4_000.0, 0.09, 0.05)),
						"apDrone", "mid_domain_mid_rpm"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupExecutionContract.CtCpJLookupExecutionContractAudit audit =
				PropellerArchiveCtCpJLookupExecutionContract.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_execution_contract_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_scenario,current_handoff_blocked_no_execution,HANDOFF_BLOCKED,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_rule,reject_extrapolation,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_scenario,synthetic_mid_bilinear_pass,ACCEPTED,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_scenario,synthetic_cp_guard_failed,ACCEPTANCE_GUARD_FAILED,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_summary,all_scenarios,handoff_blocked_scenario_count,1,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_execution_summary,all_scenarios,runtime_coupling_allowed_count,0,count,")));
	}

	private static PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult scenario(String name) {
		return PropellerArchiveCtCpJLookupExecutionContract.audit()
				.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.result();
	}

	private static List<PropellerArchiveCtCpJLookupExecutionContract.LookupGridRow> midRows(double cpScale) {
		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract contract =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract("apDrone", "mid_domain_mid_rpm");
		double jStep = 0.100;
		double rpmStep = 750.0;
		double j0 = contract.queryAdvanceRatioJ() - jStep;
		double j1 = contract.queryAdvanceRatioJ() + jStep;
		double rpm0 = contract.queryRpm() - rpmStep;
		double rpm1 = contract.queryRpm() + rpmStep;
		return List.of(
				new PropellerArchiveCtCpJLookupExecutionContract.LookupGridRow(
						"j0-r0", j0, rpm0, 0.100, 0.046 * cpScale),
				new PropellerArchiveCtCpJLookupExecutionContract.LookupGridRow(
						"j1-r0", j1, rpm0, 0.092, 0.051 * cpScale),
				new PropellerArchiveCtCpJLookupExecutionContract.LookupGridRow(
						"j0-r1", j0, rpm1, 0.095, 0.049 * cpScale),
				new PropellerArchiveCtCpJLookupExecutionContract.LookupGridRow(
						"j1-r1", j1, rpm1, 0.086, 0.057 * cpScale)
		);
	}

	private static PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary readyHandoff() {
		return handoffScenario(
				PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.audit(),
				"surface_fit_ready_execution_input_handoff");
	}

	private static PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.ExecutionInputHandoffSummary handoffScenario(
			PropellerArchiveCtCpJScatteredSurfaceFitExecutionHandoff.CtCpJScatteredSurfaceFitExecutionHandoffAudit audit,
			String name
	) {
		return audit.scenarios()
				.stream()
				.filter(scenario -> name.equals(scenario.scenarioName()))
				.findFirst()
				.orElseThrow()
				.summary();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_execution_contract_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
