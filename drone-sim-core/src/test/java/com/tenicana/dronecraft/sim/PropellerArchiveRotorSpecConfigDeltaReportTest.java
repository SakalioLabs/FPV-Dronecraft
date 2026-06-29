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

class PropellerArchiveRotorSpecConfigDeltaReportTest {
	@Test
	void auditBuildsCurrentConfigDeltaRowsWithoutPatchPermission() {
		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaAudit audit =
				PropellerArchiveRotorSpecConfigDeltaReport.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Config-Delta-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("review material only"));
		assertEquals(131, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.deltaSampleCount());
		assertEquals(28, audit.deltaMetricRowCount());
		assertEquals(12, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(4, audit.rows().size());

		for (PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow row : audit.rows()) {
			assertFalse(row.bridgeFullRotorSpecFitReady());
			assertFalse(row.configPatchAllowed());
			assertFalse(row.playableReferenceAllowed());
			assertEquals("BLOCKED", row.status());
			assertEquals("reference-table-handoff-blocked", row.message());
		}

		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow racing =
				PropellerArchiveRotorSpecConfigDeltaReport.row("racingQuad");
		assertEquals(4, racing.currentRotorCount());
		assertEquals(0.0635, racing.currentRadiusMeters(), 1.0e-12);
		assertEquals(0.0635, racing.candidateRadiusMeters(), 1.0e-12);
		assertEquals(1.0, racing.radiusRatioCandidateOverCurrent(), 1.0e-12);
		assertEquals(0.10795, racing.currentBladePitchMeters(), 1.0e-12);
		assertEquals(0.10795, racing.candidateBladePitchMeters(), 1.0e-12);
		assertEquals(3, racing.currentBladeCount());
		assertEquals(3, racing.candidateBladeCount());
		assertEquals(1.45e-6, racing.currentThrustCoefficient(), 1.0e-18);
		assertEquals(1.1970258229679278e-6, racing.candidateThrustCoefficient(), 1.0e-18);
		assertEquals(0.8255350503227088, racing.thrustCoefficientRatioCandidateOverCurrent(), 1.0e-15);
		assertEquals(0.014, racing.currentYawTorquePerThrustMeters(), 1.0e-15);
		assertEquals(0.014063300257624938, racing.candidateYawTorquePerThrustMeters(), 1.0e-15);
		assertEquals(1.0045214469732098, racing.yawTorqueRatioCandidateOverCurrent(), 1.0e-15);
		assertEquals(0.1536, racing.currentChordToRadius(), 1.0e-12);
		assertEquals(0.2068, racing.candidateChordToRadius(), 1.0e-12);
		assertEquals(1.346354166666667, racing.chordRatioCandidateOverCurrent(), 1.0e-15);
		assertEquals(0.36883120775304595, racing.currentBetaRadians(), 1.0e-15);
		assertEquals(0.36498325317705416, racing.candidateBetaRadians(), 1.0e-15);
		assertEquals(-0.0038479545759917966, racing.betaDeltaRadians(), 1.0e-15);

		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow cine =
				PropellerArchiveRotorSpecConfigDeltaReport.row("cinewhoop");
		assertEquals(0.017286769489906886, cine.thrustCoefficientRatioCandidateOverCurrent(), 1.0e-15);
		assertEquals(1.692057291666667, cine.chordRatioCandidateOverCurrent(), 1.0e-15);

		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow heavy =
				PropellerArchiveRotorSpecConfigDeltaReport.row("heavyLift");
		assertEquals(2, heavy.currentBladeCount());
		assertEquals(2, heavy.candidateBladeCount());
		assertEquals(0, heavy.bladeCountDelta());
		assertEquals(0.2585857325256895, heavy.thrustCoefficientRatioCandidateOverCurrent(), 1.0e-15);
		assertEquals(0.4607497107474872, heavy.yawTorqueRatioCandidateOverCurrent(), 1.0e-15);
		assertEquals(0.0, heavy.chordRatioCandidateOverCurrent(), 1.0e-12);
		assertEquals(-0.22356358358160267, heavy.betaDeltaRadians(), 1.0e-15);

		assertEquals(4, audit.extrema().rowCount());
		assertEquals(0, audit.extrema().bridgeFullRotorSpecFitReadyCount());
		assertEquals(0, audit.extrema().configPatchAllowedCount());
		assertEquals(0, audit.extrema().playableReferenceAllowedCount());
		assertEquals(8.684210526355152e-6, audit.extrema().maxRadiusRatioError(), 1.0e-18);
		assertEquals(2.7863777090075814e-6, audit.extrema().maxBladePitchRatioError(), 1.0e-18);
		assertEquals(0, audit.extrema().maxBladeCountDeltaAbs());
		assertEquals(0.9827132305100931, audit.extrema().maxThrustCoefficientRatioError(), 1.0e-15);
		assertEquals(0.5392502892525128, audit.extrema().maxYawTorqueRatioError(), 1.0e-15);
		assertEquals(1.0, audit.extrema().maxChordRatioError(), 1.0e-12);
		assertEquals(0.22356358358160267, audit.extrema().maxBetaDeltaRadians(), 1.0e-15);
		assertEquals(4, audit.extrema().maxRotorCount());

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecConfigDeltaReport.row("missing"));
	}

	@Test
	void readyBridgeExposesPlayableReviewRowsWithoutConfigPatch() {
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary handoff =
				findHandoff("acceptance_ready_reference_reviewed");
		PropellerArchiveCompactReferenceTable.CompactReferenceTableAudit table =
				PropellerArchiveCompactReferenceTable.audit(handoff);
		PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeAudit bridge =
				PropellerArchiveRotorSpecFitBridge.audit(table);
		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaAudit audit =
				PropellerArchiveRotorSpecConfigDeltaReport.audit(bridge);

		assertEquals(3, audit.extrema().bridgeFullRotorSpecFitReadyCount());
		assertEquals(0, audit.extrema().configPatchAllowedCount());
		assertEquals(3, audit.extrema().playableReferenceAllowedCount());

		for (PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaRow row : audit.rows()) {
			assertFalse(row.configPatchAllowed());
			if ("heavyLift".equals(row.presetName())) {
				assertFalse(row.playableReferenceAllowed());
				assertEquals("BLOCKED", row.status());
				assertEquals("rotor-spec-geometry-reference-missing", row.message());
			} else {
				assertTrue(row.bridgeFullRotorSpecFitReady());
				assertTrue(row.playableReferenceAllowed());
				assertEquals("REVIEW_READY", row.status());
				assertEquals("rotor-spec-config-delta-ready-for-review", row.message());
			}
		}
	}

	@Test
	void reportRejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecConfigDeltaReport.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecConfigDeltaReport.row(
						(PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeRow) null));
		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecConfigDeltaReport.configFor("missing"));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecConfigDeltaReport.RotorSpecConfigDeltaAudit audit =
				PropellerArchiveRotorSpecConfigDeltaReport.audit();
		Path packet = findRepoRoot().resolve("docs/data/propeller_archive_rotor_spec_config_delta_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_config_delta_summary,all_rows,playable_reference_allowed_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_config_delta_summary,all_rows,max_thrust_coefficient_ratio_error,0.9827132305100931,ratio,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_config_delta,racingQuad,thrust_coefficient_ratio_candidate_over_current,0.8255350503227088,ratio,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_config_delta,cinewhoop,thrust_coefficient_ratio_candidate_over_current,0.017286769489906886,ratio,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_config_delta,heavyLift,chord_ratio_candidate_over_current,0.0,ratio,")));
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
							"docs/data/propeller_archive_rotor_spec_config_delta_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
