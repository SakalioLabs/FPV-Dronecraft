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

class PropellerArchiveCtCpJLookupInterpolationPolicyTest {
	@Test
	void auditDefinesInterpolationPolicyWithoutCoefficientInterpolation() {
		PropellerArchiveCtCpJLookupInterpolationPolicy.CtCpJLookupInterpolationPolicyAudit audit =
				PropellerArchiveCtCpJLookupInterpolationPolicy.audit();

		assertEquals("User-Propeller-Archive-CT-CP-J-Lookup-Interpolation-Policy-Packet",
				audit.sourceId());
		assertTrue(audit.caveat().contains("reviewed-neighbor requirements"));
		assertEquals(50, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(9, audit.policyRuleRowCount());
		assertEquals(16, audit.queryContractRowCount());
		assertEquals(18, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());

		PropellerArchiveCtCpJLookupInterpolationPolicy.InterpolationPolicySummary summary =
				audit.summary();
		assertEquals(9, summary.policyRuleCount());
		assertEquals(16, summary.queryContractCount());
		assertEquals(12, summary.insideLookupDomainContractCount());
		assertEquals(4, summary.extrapolationRejectedContractCount());
		assertEquals(0, summary.currentInterpolationAllowedCount());
		assertEquals(9, summary.postReviewCtCpJInterpolationAllowedCount());
		assertEquals(6, summary.postReviewFullSimulationInterpolationAllowedCount());
		assertEquals(3, summary.postReviewPerformanceOnlyInterpolationCount());
		assertEquals(3, summary.bladeCoverageBlockedContractCount());
		assertEquals(3, summary.geometryCoverageBlockedContractCount());
		assertEquals(3, summary.staticAnchorContractCount());
		assertEquals(9, summary.etaConsistencyRequiredContractCount());
		assertEquals(9, summary.cpPositiveRequiredContractCount());
		assertEquals(21, summary.minimumPostReviewPerformanceNeighborRows());
		assertEquals(14, summary.minimumPostReviewFullSimulationNeighborRows());
		assertEquals(0, summary.runtimeCouplingAllowedCount());
		assertEquals(0, summary.gameplayAutoApplyAllowedCount());
		assertEquals("review-source-license-then-bind-reviewed-ct-cp-j-rows-to-interpolation-policy",
				summary.nextRequiredAction());
	}

	@Test
	void policyRulesKeepRawImportAndRuntimeBoundariesClosed() {
		PropellerArchiveCtCpJLookupInterpolationPolicy.InterpolationPolicyRule review =
				PropellerArchiveCtCpJLookupInterpolationPolicy.rule("source_license_review");
		assertTrue(review.required());
		assertFalse(review.currentSatisfied());
		assertTrue(review.reviewedImportSatisfied());
		assertTrue(review.syntheticTargetSatisfied());
		assertEquals("review-source-license-before-importing-raw-rows", review.nextRequiredAction());

		PropellerArchiveCtCpJLookupInterpolationPolicy.InterpolationPolicyRule extrapolation =
				PropellerArchiveCtCpJLookupInterpolationPolicy.rule("reject_extrapolation");
		assertTrue(extrapolation.currentSatisfied());
		assertTrue(extrapolation.requirement().contains("rejected"));

		PropellerArchiveCtCpJLookupInterpolationPolicy.InterpolationPolicyRule guard =
				PropellerArchiveCtCpJLookupInterpolationPolicy.rule("runtime_leak_guard");
		assertTrue(guard.currentSatisfied());
		assertTrue(guard.reviewedImportSatisfied());
		assertEquals("keep-runtime-coupling-and-gameplay-auto-apply-closed", guard.nextRequiredAction());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupInterpolationPolicy.rule("missing"));
	}

	@Test
	void acceptedRowsDeclareMinimumNeighborRequirements() {
		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract staticRow =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract("apDrone", "static_anchor_low_rpm");
		assertEquals(0.0, staticRow.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(1, staticRow.minimumAdvanceNeighbors());
		assertEquals(1, staticRow.minimumRpmNeighbors());
		assertEquals(1, staticRow.minimumPerformanceNeighborRows());
		assertTrue(staticRow.staticAnchorPreserved());
		assertTrue(staticRow.etaConsistencyRequired());
		assertTrue(staticRow.cpPositiveRequired());
		assertTrue(staticRow.postReviewCtCpJInterpolationAllowed());
		assertTrue(staticRow.postReviewFullSimulationInterpolationAllowed());
		assertFalse(staticRow.currentInterpolationAllowed());
		assertFalse(staticRow.runtimeCouplingAllowed());
		assertFalse(staticRow.gameplayAutoApplyAllowed());

		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract mid =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract("apDrone", "mid_domain_mid_rpm");
		assertEquals(0.4064, mid.queryAdvanceRatioJ(), 1.0e-12);
		assertEquals(4_712.25, mid.queryRpm(), 1.0e-9);
		assertEquals(2, mid.minimumAdvanceNeighbors());
		assertEquals(2, mid.minimumRpmNeighbors());
		assertEquals(4, mid.minimumPerformanceNeighborRows());
		assertFalse(mid.staticAnchorPreserved());
		assertEquals("INTERPOLATION_READY_AFTER_REVIEW", mid.status());

		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract high =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract("apDrone", "high_domain_max_rpm");
		assertEquals(2, high.minimumAdvanceNeighbors());
		assertEquals(1, high.minimumRpmNeighbors());
		assertEquals(2, high.minimumPerformanceNeighborRows());
		assertTrue(high.insideLookupDomain());
	}

	@Test
	void rejectedAndCoverageBlockedRowsStayClosed() {
		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract probe =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract("apDrone", "high_j_extrapolation_probe");
		assertTrue(probe.extrapolationRequired());
		assertFalse(probe.insideLookupDomain());
		assertEquals(0, probe.minimumAdvanceNeighbors());
		assertEquals(0, probe.minimumRpmNeighbors());
		assertEquals(0, probe.minimumPerformanceNeighborRows());
		assertFalse(probe.postReviewCtCpJInterpolationAllowed());
		assertFalse(probe.postReviewFullSimulationInterpolationAllowed());
		assertEquals("EXTRAPOLATION_REJECTED", probe.status());
		assertEquals("query-requires-extrapolation-and-is-rejected", probe.message());

		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract cine =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract("cinewhoop", "mid_domain_mid_rpm");
		assertTrue(cine.insideLookupDomain());
		assertFalse(cine.postReviewCtCpJInterpolationAllowed());
		assertFalse(cine.etaConsistencyRequired());
		assertFalse(cine.cpPositiveRequired());
		assertEquals("blade-count-coverage-missing", cine.postReviewBlocker());
		assertEquals("COVERAGE_BLOCKED", cine.status());

		PropellerArchiveCtCpJLookupInterpolationPolicy.QueryInterpolationContract heavy =
				PropellerArchiveCtCpJLookupInterpolationPolicy.contract("heavyLift", "mid_domain_mid_rpm");
		assertTrue(heavy.postReviewCtCpJInterpolationAllowed());
		assertFalse(heavy.postReviewFullSimulationInterpolationAllowed());
		assertTrue(heavy.etaConsistencyRequired());
		assertTrue(heavy.cpPositiveRequired());
		assertEquals("geometry-fit-input-missing", heavy.postReviewBlocker());
		assertEquals("PERFORMANCE_INTERPOLATION_READY_AFTER_REVIEW_FULL_SIM_BLOCKED", heavy.status());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupInterpolationPolicy.contract("missing", "mid_domain_mid_rpm"));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveCtCpJLookupInterpolationPolicy.contract("apDrone", "missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveCtCpJLookupInterpolationPolicy.CtCpJLookupInterpolationPolicyAudit audit =
				PropellerArchiveCtCpJLookupInterpolationPolicy.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_ct_cp_j_lookup_interpolation_policy_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_interpolation_rule,reject_extrapolation,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_interpolation_contract,apDrone,mid_domain_mid_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_interpolation_contract,cinewhoop,mid_domain_mid_rpm,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_interpolation_summary,all,minimum_post_review_performance_neighbor_rows,21,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_ct_cp_j_lookup_interpolation_summary,all,current_interpolation_allowed_count,0,count,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/propeller_archive_ct_cp_j_lookup_interpolation_policy_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
