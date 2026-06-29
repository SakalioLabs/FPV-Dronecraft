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

class PropellerArchiveRotorSpecRetuneReadinessGateTest {
	@Test
	void auditBuildsCurrentRowsBlockedByBridgeGate() {
		PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessAudit audit =
				PropellerArchiveRotorSpecRetuneReadinessGate.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Retune-Readiness-Gate-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("before any DroneConfig patch"));
		assertEquals(109, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.retuneSampleCount());
		assertEquals(23, audit.retuneMetricRowCount());
		assertEquals(10, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(0.02, audit.maxRadiusRatioErrorForReview(), 1.0e-12);
		assertEquals(0.05, audit.maxBladePitchRatioErrorForReview(), 1.0e-12);
		assertEquals(0, audit.maxBladeCountDeltaForReview());
		assertEquals(0.25, audit.maxThrustCoefficientRatioErrorForReview(), 1.0e-12);
		assertEquals(0.15, audit.maxYawTorqueRatioErrorForReview(), 1.0e-12);
		assertEquals(0.40, audit.maxChordRatioErrorForReview(), 1.0e-12);
		assertEquals(0.08, audit.maxBetaDeltaRadiansForReview(), 1.0e-12);
		assertEquals(4, audit.rows().size());

		for (PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessRow row : audit.rows()) {
			assertFalse(row.bridgeFullRotorSpecFitReady());
			assertFalse(row.directRetuneReviewReady());
			assertFalse(row.calibrationValidationRequired());
			assertFalse(row.configPatchAllowed());
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("BLOCKED", row.status());
			assertEquals("rotor-spec-full-fit-gate-blocked", row.dominantBlocker());
			assertEquals("complete-bridge-full-fit-before-retune-review", row.nextRequiredAction());
		}

		PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessRow racing =
				PropellerArchiveRotorSpecRetuneReadinessGate.row("racingQuad");
		assertEquals(7, racing.passedToleranceCount());
		assertEquals(0, racing.failedToleranceCount());
		assertEquals(0.346354166666667, racing.maxRatioError(), 1.0e-15);
		assertEquals(0.0038479545759917966, racing.betaDeltaRadiansAbs(), 1.0e-15);

		PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessRow cine =
				PropellerArchiveRotorSpecRetuneReadinessGate.row("cinewhoop");
		assertTrue(cine.radiusWithinTolerance());
		assertTrue(cine.bladePitchWithinTolerance());
		assertTrue(cine.bladeCountMatches());
		assertFalse(cine.thrustCoefficientWithinTolerance());
		assertFalse(cine.yawTorqueWithinTolerance());
		assertFalse(cine.chordWithinTolerance());
		assertTrue(cine.betaWithinTolerance());
		assertEquals(4, cine.passedToleranceCount());
		assertEquals(3, cine.failedToleranceCount());
		assertEquals(0.9827132305100931, cine.maxRatioError(), 1.0e-15);

		PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessRow heavy =
				PropellerArchiveRotorSpecRetuneReadinessGate.row("heavyLift");
		assertEquals(3, heavy.passedToleranceCount());
		assertEquals(4, heavy.failedToleranceCount());
		assertEquals(1.0, heavy.maxRatioError(), 1.0e-12);
		assertEquals(0.22356358358160267, heavy.betaDeltaRadiansAbs(), 1.0e-15);

		assertEquals(4, audit.extrema().rowCount());
		assertEquals(0, audit.extrema().bridgeFullRotorSpecFitReadyCount());
		assertEquals(0, audit.extrema().directRetuneReviewReadyCount());
		assertEquals(0, audit.extrema().calibrationValidationRequiredCount());
		assertEquals(0, audit.extrema().configPatchAllowedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(4, audit.extrema().maxFailedToleranceCount());
		assertEquals(1.0, audit.extrema().maxRatioError(), 1.0e-12);
		assertEquals(0.22356358358160267, audit.extrema().maxBetaDeltaRadians(), 1.0e-15);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneReadinessGate.row("missing"));
	}

	@Test
	void readyDeltaGateAllowsOnlyLowDeltaRowsForValidation() {
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary handoff =
				findHandoff("acceptance_ready_reference_reviewed");
		PropellerArchiveCompactReferenceTable.CompactReferenceTableAudit table =
				PropellerArchiveCompactReferenceTable.audit(handoff);
		PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeAudit bridge =
				PropellerArchiveRotorSpecFitBridge.audit(table);
		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaAudit delta =
				PropellerArchiveRotorSpecConfigDeltaReport.audit(bridge);
		PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessAudit audit =
				PropellerArchiveRotorSpecRetuneReadinessGate.audit(delta);

		assertEquals(3, audit.extrema().bridgeFullRotorSpecFitReadyCount());
		assertEquals(2, audit.extrema().directRetuneReviewReadyCount());
		assertEquals(2, audit.extrema().calibrationValidationRequiredCount());
		assertEquals(0, audit.extrema().configPatchAllowedCount());

		for (PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessRow row : audit.rows()) {
			assertFalse(row.configPatchAllowed());
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			if ("racingQuad".equals(row.presetName()) || "apDrone".equals(row.presetName())) {
				assertTrue(row.directRetuneReviewReady());
				assertTrue(row.calibrationValidationRequired());
				assertEquals("VALIDATION_REQUIRED", row.status());
				assertEquals("retune-candidate-ready-for-validation", row.dominantBlocker());
				assertEquals("run-offline-hover-forward-flight-validation-before-config-patch",
						row.nextRequiredAction());
			} else if ("cinewhoop".equals(row.presetName())) {
				assertTrue(row.bridgeFullRotorSpecFitReady());
				assertFalse(row.directRetuneReviewReady());
				assertEquals("thrust-coefficient-large-delta-review-required", row.dominantBlocker());
				assertEquals("review-static-ct-anchor-or-current-thrust-coefficient-before-retune",
						row.nextRequiredAction());
			} else {
				assertEquals("heavyLift", row.presetName());
				assertFalse(row.bridgeFullRotorSpecFitReady());
				assertEquals("rotor-spec-geometry-reference-missing", row.dominantBlocker());
				assertEquals("add-heavy-lift-geometry-source-or-surrogate", row.nextRequiredAction());
			}
		}
	}

	@Test
	void gateRejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneReadinessGate.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecRetuneReadinessGate.row(
						(PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow) null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecRetuneReadinessGate.RotorSpecRetuneReadinessAudit audit =
				PropellerArchiveRotorSpecRetuneReadinessGate.audit();
		Path packet = findRepoRoot().resolve(
				"docs/data/propeller_archive_rotor_spec_retune_readiness_gate_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_readiness_summary,all_rows,direct_retune_review_ready_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_readiness,racingQuad,passed_tolerance_count,7,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_readiness,cinewhoop,thrust_coefficient_within_tolerance,false,boolean,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_readiness,heavyLift,failed_tolerance_count,4,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_retune_readiness_method,retune_gate_rule,method,bridge-full-fit-and-delta-tolerance-gate,audit,")));
	}

	private static PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary findHandoff(String name) {
		return PropellerArchiveCompactReferenceHandoff.audit().scenarios().stream()
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
							"docs/data/propeller_archive_rotor_spec_retune_readiness_gate_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
