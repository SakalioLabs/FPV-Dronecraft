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

class PropellerArchiveRotorSpecFitBridgeTest {
	@Test
	void auditBuildsBlockedCurrentRotorSpecBridgeRows() {
		PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeAudit audit =
				PropellerArchiveRotorSpecFitBridge.audit();

		assertEquals("User-Propeller-Archive-RotorSpec-Fit-Bridge-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("DroneConfig patch is emitted"));
		assertEquals(105, audit.packetRowCount());
		assertEquals(6, audit.sourceReferenceRowCount());
		assertEquals(4, audit.bridgeSampleCount());
		assertEquals(22, audit.bridgeMetricRowCount());
		assertEquals(10, audit.summaryRowCount());
		assertEquals(1, audit.methodRowCount());
		assertEquals(4, audit.rows().size());

		for (PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeRow row : audit.rows()) {
			assertFalse(row.performanceReferenceAvailable());
			assertFalse(row.geometryReferenceAvailable());
			assertFalse(row.fullRotorSpecFitReady());
			assertFalse(row.rotorSpecPatchAllowed());
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			assertEquals("BLOCKED", row.status());
			assertEquals("reference-table-handoff-blocked", row.message());
			assertEquals("audit-only-prop-archive-reference-handoff", row.sourceRuntimeInfo());
		}

		PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeRow racing =
				PropellerArchiveRotorSpecFitBridge.row("racingQuad");
		assertEquals("da4052 5.0x3.75 - 3", racing.performanceMatchId());
		assertEquals("da4052 5.0x3.75", racing.geometryMatchId());
		assertEquals(0.127, racing.targetDiameterMeters(), 1.0e-12);
		assertEquals(0.0635, racing.targetRadiusMeters(), 1.0e-12);
		assertEquals(0.10795, racing.targetPitchMeters(), 1.0e-12);
		assertEquals(3, racing.bladeCountCandidate());
		assertEquals(1.1970258229679278e-6, racing.thrustCoefficientCandidate(), 1.0e-18);
		assertEquals(0.014063300257624938, racing.yawTorquePerThrustCandidateMeters(), 1.0e-15);
		assertEquals(0.2068, racing.chordToRadiusCandidate(), 1.0e-12);
		assertEquals(0.36498325317705416, racing.betaRadiansCandidate(), 1.0e-15);

		PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeRow heavy =
				PropellerArchiveRotorSpecFitBridge.row("heavyLift");
		assertEquals("ancf 10.0x5.0 - 2", heavy.performanceMatchId());
		assertEquals("missing-reviewed-geometry-match", heavy.geometryMatchId());
		assertEquals(1.1636357963656028e-5, heavy.thrustCoefficientCandidate(), 1.0e-17);
		assertEquals(0.013822491322424616, heavy.yawTorquePerThrustCandidateMeters(), 1.0e-15);
		assertEquals(0.0, heavy.chordToRadiusCandidate(), 1.0e-12);
		assertEquals(0.0, heavy.betaRadiansCandidate(), 1.0e-12);

		assertEquals(4, audit.extrema().rowCount());
		assertEquals(0, audit.extrema().performanceReferenceAvailableCount());
		assertEquals(0, audit.extrema().geometryReferenceAvailableCount());
		assertEquals(0, audit.extrema().fullRotorSpecFitReadyCount());
		assertEquals(0, audit.extrema().rotorSpecPatchAllowedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(1.1636357963656028e-5, audit.extrema().maxThrustCoefficientCandidate(), 1.0e-17);
		assertEquals(0.014344566262777437, audit.extrema().maxYawTorquePerThrustCandidateMeters(), 1.0e-15);
		assertEquals(0.43720497762457955, audit.extrema().maxBetaRadiansCandidate(), 1.0e-15);

		assertThrows(IllegalArgumentException.class,
				() -> PropellerArchiveRotorSpecFitBridge.row("missing"));
	}

	@Test
	void readyReferenceTableEnablesCandidateRowsWithoutPatchingDroneConfig() {
		PropellerArchiveCompactReferenceHandoff.CompactReferenceHandoffSummary handoff =
				findHandoff("acceptance_ready_reference_reviewed");
		PropellerArchiveCompactReferenceTable.CompactReferenceTableAudit table =
				PropellerArchiveCompactReferenceTable.audit(handoff);
		PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeAudit audit =
				PropellerArchiveRotorSpecFitBridge.audit(table);

		assertEquals(4, audit.extrema().performanceReferenceAvailableCount());
		assertEquals(3, audit.extrema().geometryReferenceAvailableCount());
		assertEquals(3, audit.extrema().fullRotorSpecFitReadyCount());
		assertEquals(0, audit.extrema().rotorSpecPatchAllowedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());

		for (PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeRow row : audit.rows()) {
			assertTrue(row.performanceReferenceAvailable());
			assertFalse(row.rotorSpecPatchAllowed());
			assertFalse(row.runtimeCouplingAllowed());
			assertFalse(row.gameplayAutoApplyAllowed());
			if ("heavyLift".equals(row.presetName())) {
				assertFalse(row.geometryReferenceAvailable());
				assertFalse(row.fullRotorSpecFitReady());
				assertEquals("BLOCKED", row.status());
				assertEquals("rotor-spec-geometry-reference-missing", row.message());
			} else {
				assertTrue(row.geometryReferenceAvailable());
				assertTrue(row.fullRotorSpecFitReady());
				assertEquals("READY", row.status());
				assertEquals("rotor-spec-fit-candidate-ready", row.message());
			}
		}
	}

	@Test
	void candidateFormulasRejectInvalidInputs() {
		assertEquals(0.0, PropellerArchiveRotorSpecFitBridge.thrustCoefficientCandidate(0.0, 0.127), 1.0e-12);
		assertEquals(0.0, PropellerArchiveRotorSpecFitBridge.thrustCoefficientCandidate(0.1, 0.0), 1.0e-12);
		assertEquals(0.0, PropellerArchiveRotorSpecFitBridge.thrustCoefficientCandidate(-0.1, 0.127), 1.0e-12);
		assertEquals(0.0, PropellerArchiveRotorSpecFitBridge.thrustCoefficientCandidate(
				Double.NaN, 0.127), 1.0e-12);
		assertEquals(0.0, PropellerArchiveRotorSpecFitBridge.yawTorquePerThrustCandidate(
				0.0, 0.1, 0.127), 1.0e-12);
		assertEquals(0.0, PropellerArchiveRotorSpecFitBridge.yawTorquePerThrustCandidate(
				0.1, 0.0, 0.127), 1.0e-12);
		assertEquals(0.0, PropellerArchiveRotorSpecFitBridge.yawTorquePerThrustCandidate(
				0.1, 0.1, Double.POSITIVE_INFINITY), 1.0e-12);

		assertEquals(1.1970258229679278e-6,
				PropellerArchiveRotorSpecFitBridge.thrustCoefficientCandidate(
						0.148290142857143, 0.127), 1.0e-18);
		assertEquals(0.014063300257624938,
				PropellerArchiveRotorSpecFitBridge.yawTorquePerThrustCandidate(
						0.148290142857143, 0.103175285714286, 0.127), 1.0e-15);
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeAudit audit =
				PropellerArchiveRotorSpecFitBridge.audit();
		Path packet = findRepoRoot().resolve("docs/data/propeller_archive_rotor_spec_fit_bridge_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_fit_bridge_summary,all_rows,full_rotor_spec_fit_ready_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_fit_bridge_summary,all_rows,runtime_coupling_allowed_count,0,count,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_fit_bridge,racingQuad,thrust_coefficient_candidate,1.1970258229679278E-6,N_per_rad2,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_fit_bridge,heavyLift,geometry_match_id,missing-reviewed-geometry-match,text,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("propeller_archive_rotor_spec_fit_bridge,heavyLift,geometry_reference_available,false,boolean,")));
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
							"docs/data/propeller_archive_rotor_spec_fit_bridge_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
