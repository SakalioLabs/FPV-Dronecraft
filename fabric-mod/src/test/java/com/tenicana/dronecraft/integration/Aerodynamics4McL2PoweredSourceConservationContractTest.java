package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceConservationContractTest {
	@Test
	void auditBuildsStableHoverAndCruiseConservationContracts() {
		Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractAudit audit =
				Aerodynamics4McL2PoweredSourceConservationContract.audit();

		assertEquals("A4MC-L2-Powered-Source-Conservation-Contract-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("force, momentum, and kinetic-power closure"));
		assertEquals(0.015, audit.maxMomentumClosureErrorRatioAllowed(), 1.0e-12);
		assertEquals(0.02, audit.maxKineticPowerClosureErrorRatioAllowed(), 1.0e-12);
		assertEquals(72, audit.packetMetricRowCount());
		assertEquals(7, audit.sourceReferenceCount());
		assertEquals(2, audit.spinStateSampleCount());
		assertEquals(26, audit.contractMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(2, audit.rows().size());

		Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow hover =
				find(audit.rows(), "hover");
		assertEquals(4, hover.footprintCount());
		assertEquals(16, hover.sourceTermCount());
		assertTrue(hover.maxTotalThrustNewtons() > 0.0);
		assertTrue(hover.maxMassFlowKilogramsPerSecond() > 0.0);
		assertTrue(hover.maxMomentumPowerWatts() > 0.0);
		assertTrue(hover.maxMomentumClosureErrorRatio() < 1.0e-12);
		assertTrue(hover.maxKineticPowerClosureErrorRatio() < 1.0e-12);
		assertTrue(hover.maxWakeSpeedMetersPerSecond() > 0.0);
		assertEquals(0.0, hover.maxWakeTransitTimeSeconds(), 1.0e-12);
		assertEquals(12, hover.recommendedWakeSampleCount());
		assertContractTargetOnly(hover);

		Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow cruise =
				find(audit.rows(), "cruise");
		assertEquals(4, cruise.footprintCount());
		assertEquals(16, cruise.sourceTermCount());
		assertTrue(cruise.maxTotalThrustNewtons() > hover.maxTotalThrustNewtons());
		assertTrue(cruise.maxMassFlowKilogramsPerSecond() > hover.maxMassFlowKilogramsPerSecond());
		assertTrue(cruise.maxMomentumPowerWatts() > hover.maxMomentumPowerWatts());
		assertTrue(cruise.maxWakeSpeedMetersPerSecond() > hover.maxWakeSpeedMetersPerSecond());
		assertTrue(cruise.maxWakeTransitTimeSeconds() > 0.0);
		assertEquals(48, cruise.recommendedWakeSampleCount());
		assertContractTargetOnly(cruise);

		assertEquals(2, audit.extrema().rowCount());
		assertEquals(2, audit.extrema().targetModelSelfConsistentCount());
		assertEquals(0, audit.extrema().liveConservationAcceptedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(2, audit.extrema().sourceForceDeltaRequiredCount());
		assertEquals(2, audit.extrema().sourceMomentDeltaRequiredCount());
		assertEquals(2, audit.extrema().wakeResidualRequiredCount());
		assertEquals(8, audit.extrema().totalFootprintCount());
		assertEquals(32, audit.extrema().totalSourceTermCount());
		assertTrue(audit.extrema().maxMomentumClosureErrorRatio() < 1.0e-12);
		assertTrue(audit.extrema().maxKineticPowerClosureErrorRatio() < 1.0e-12);
	}

	@Test
	void rowsMatchWakeFootprintInputs() {
		Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprintAudit hoverFootprints =
				Aerodynamics4McL2PoweredHoverWakeFootprint.audit();
		Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprintAudit cruiseFootprints =
				Aerodynamics4McL2PoweredCruiseWakeFootprint.audit();

		Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow hover =
				Aerodynamics4McL2PoweredSourceConservationContract.hoverRow(hoverFootprints);
		Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow cruise =
				Aerodynamics4McL2PoweredSourceConservationContract.cruiseRow(cruiseFootprints);

		assertEquals(hoverFootprints.extrema().sourceTermCount(), hover.sourceTermCount());
		assertEquals(hoverFootprints.extrema().maxTotalThrustNewtons(), hover.maxTotalThrustNewtons(), 1.0e-12);
		assertEquals(hoverFootprints.extrema().maxMassFlowKilogramsPerSecond(),
				hover.maxMassFlowKilogramsPerSecond(), 1.0e-12);
		assertEquals(hoverFootprints.extrema().maxTotalMomentumPowerWatts(), hover.maxMomentumPowerWatts(),
				1.0e-12);
		assertEquals(hoverFootprints.extrema().maxFarWakeVelocityMetersPerSecond(),
				hover.maxWakeSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(hoverFootprints.extrema().maxRecommendedRotorWakeSampleCount(),
				hover.recommendedWakeSampleCount());

		assertEquals(cruiseFootprints.extrema().sourceTermCount(), cruise.sourceTermCount());
		assertEquals(cruiseFootprints.extrema().maxResultantWakeVelocityMetersPerSecond(),
				cruise.maxWakeSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(cruiseFootprints.extrema().maxAxialTransitTimeSeconds(),
				cruise.maxWakeTransitTimeSeconds(), 1.0e-12);
		assertEquals(cruiseFootprints.extrema().maxRecommendedSkewWakeSampleCount(),
				cruise.recommendedWakeSampleCount());
	}

	@Test
	void rejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceConservationContract.hoverRow(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredSourceConservationContract.cruiseRow(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractAudit audit =
				Aerodynamics4McL2PoweredSourceConservationContract.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_source_conservation_contract_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_conservation_contract,hover,target_model_self_consistent,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_conservation_contract,cruise,live_conservation_accepted,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_conservation_contract_summary,all_spin_states,total_source_term_count,32,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_conservation_contract_summary,all_spin_states,runtime_coupling_allowed_count,0,")));
	}

	private static void assertContractTargetOnly(
			Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow row
	) {
		assertTrue(row.targetModelSelfConsistent());
		assertTrue(row.sourceForceDeltaRequired());
		assertTrue(row.sourceMomentDeltaRequired());
		assertTrue(row.wakeMomentumResidualRequired());
		assertTrue(row.wakeKineticPowerResidualRequired());
		assertFalse(row.livePoweredSourceEvidencePresent());
		assertFalse(row.liveWakeProbeEvidencePresent());
		assertFalse(row.liveConservationAccepted());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("capture-live-a4mc-powered-source-force-moment-and-wake-residuals",
				row.nextRequiredAction());
		assertEquals("TARGET_ONLY", row.status());
		assertEquals("powered-source-conservation-target-self-consistent-live-evidence-missing",
				row.message());
	}

	private static Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow find(
			List<Aerodynamics4McL2PoweredSourceConservationContract.PoweredSourceConservationContractRow> rows,
			String spinState
	) {
		return rows.stream()
				.filter(row -> spinState.equals(row.spinState()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_source_conservation_contract_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
