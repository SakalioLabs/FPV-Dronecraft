package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class Aerodynamics4McL2PoweredSourceSwirlConservationContractTest {
	@Test
	void auditBuildsStableHoverAndCruiseSwirlConservationTargets() {
		Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractAudit audit =
				Aerodynamics4McL2PoweredSourceSwirlConservationContract.audit();

		assertEquals("A4MC-L2-Powered-Source-Swirl-Conservation-Contract-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("wake angular-momentum targets"));
		assertEquals(70, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(2, audit.spinStateSampleCount());
		assertEquals(25, audit.spinStateMetricCount());
		assertEquals(13, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(3, audit.swirlProbeAzimuthSampleCount());
		assertEquals(2, audit.rows().size());

		Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractRow hover =
				find(audit.rows(), "hover");
		assertEquals(4, hover.sourceMapCount());
		assertEquals(16, hover.rotorSwirlTargetCount());
		assertEquals(48, hover.recommendedSwirlProbeCount());
		assertTrue(hover.maxPerRotorReactionTorqueNewtonMeters() > 0.1);
		assertEquals(hover.maxPerRotorReactionTorqueNewtonMeters(),
				hover.maxSignedWakeAngularMomentumFluxNewtonMeters(), 1.0e-12);
		assertTrue(hover.maxSwirlRadiusMeters() > 0.02);
		assertTrue(hover.maxTargetTangentialWakeVelocityMetersPerSecond() > 0.1);
		assertTrue(hover.maxSwirlKineticPowerWatts() > 0.0);
		assertTrue(hover.maxSwirlPowerFractionOfMomentumPower() > 0.0);
		assertEquals(0.0, hover.maxNetSignedReactionTorqueNewtonMeters(), 1.0e-12);
		assertEquals(0.0, hover.maxNetTorqueCancellationErrorRatio(), 1.0e-12);
		assertTrue(hover.angularMomentumTargetSelfConsistent());
		assertRequiredAndBlocked(hover);

		Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractRow cruise =
				find(audit.rows(), "cruise");
		assertEquals(4, cruise.sourceMapCount());
		assertEquals(16, cruise.rotorSwirlTargetCount());
		assertEquals(48, cruise.recommendedSwirlProbeCount());
		assertTrue(cruise.maxPerRotorReactionTorqueNewtonMeters() >= hover.maxPerRotorReactionTorqueNewtonMeters());
		assertTrue(cruise.maxTargetTangentialWakeVelocityMetersPerSecond()
				>= hover.maxTargetTangentialWakeVelocityMetersPerSecond());
		assertEquals(0.0, cruise.maxNetSignedReactionTorqueNewtonMeters(), 1.0e-12);
		assertEquals(0.0, cruise.maxNetTorqueCancellationErrorRatio(), 1.0e-12);
		assertTrue(cruise.angularMomentumTargetSelfConsistent());
		assertRequiredAndBlocked(cruise);

		assertEquals(2, audit.extrema().rowCount());
		assertEquals(2, audit.extrema().targetSelfConsistentCount());
		assertEquals(0, audit.extrema().liveSwirlConservationAcceptedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().gameplayAutoApplyAllowedCount());
		assertEquals(2, audit.extrema().sourceMomentDeltaRequiredCount());
		assertEquals(2, audit.extrema().wakeTangentialVelocityRequiredCount());
		assertEquals(2, audit.extrema().wakeAngularMomentumResidualRequiredCount());
		assertEquals(32, audit.extrema().totalRotorSwirlTargetCount());
		assertEquals(48, audit.extrema().maxRecommendedSwirlProbeCount());
		assertTrue(audit.extrema().maxTargetTangentialWakeVelocityMetersPerSecond()
				>= cruise.maxTargetTangentialWakeVelocityMetersPerSecond());
		assertTrue(audit.extrema().maxSwirlPowerFractionOfMomentumPower()
				>= cruise.maxSwirlPowerFractionOfMomentumPower());
		assertEquals(0.0, audit.extrema().maxNetTorqueCancellationErrorRatio(), 1.0e-12);
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractAudit audit =
				Aerodynamics4McL2PoweredSourceSwirlConservationContract.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_source_swirl_conservation_contract_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_swirl_conservation_contract_summary,all_spin_states,total_rotor_swirl_target_count,32,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_swirl_conservation_contract,hover,wake_tangential_velocity_required,true,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_swirl_conservation_contract,cruise,recommended_swirl_probe_count,48,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_source_swirl_conservation_contract,cruise,live_swirl_conservation_accepted,false,")));
	}

	private static void assertRequiredAndBlocked(
			Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractRow row
	) {
		assertTrue(row.sourceMomentDeltaRequired());
		assertTrue(row.wakeTangentialVelocityRequired());
		assertTrue(row.wakeAngularMomentumResidualRequired());
		assertFalse(row.livePoweredSourceEvidencePresent());
		assertFalse(row.liveSwirlProbeEvidencePresent());
		assertFalse(row.liveSwirlConservationAccepted());
		assertFalse(row.runtimeCouplingAllowed());
		assertFalse(row.gameplayAutoApplyAllowed());
		assertEquals("per-rotor-powered-source-wake-swirl-angular-momentum-evidence", row.targetPayloadKind());
		assertEquals("capture-live-a4mc-powered-source-swirl-angular-momentum-residuals",
				row.nextRequiredAction());
		assertEquals("TARGET_ONLY", row.status());
		assertEquals("powered-source-swirl-target-self-consistent-live-evidence-missing", row.message());
	}

	private static Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractRow
	find(
			List<Aerodynamics4McL2PoweredSourceSwirlConservationContract.PoweredSourceSwirlConservationContractRow>
					rows,
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
							"docs/data/a4mc_l2_powered_source_swirl_conservation_contract_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
