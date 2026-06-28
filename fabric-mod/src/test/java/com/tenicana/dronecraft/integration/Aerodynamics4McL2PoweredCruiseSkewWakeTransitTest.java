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

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

class Aerodynamics4McL2PoweredCruiseSkewWakeTransitTest {
	@Test
	void auditBuildsStableCruiseSkewWakeTransitTargets() {
		Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeTransit.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Skew-Wake-Transit-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("skew-wake transit"));
		assertEquals(7315, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(4, audit.rotorSampleCount());
		assertEquals(4, audit.axialSamplePlaneCount());
		assertEquals(3, audit.sweepSampleColumnCount());
		assertEquals(192, audit.transitSampleCount());
		assertEquals(38, audit.transitMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(192, audit.targets().size());

		for (Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget target
				: audit.targets()) {
			assertTrue(target.presetName().length() > 0);
			assertEquals("cruise", target.spinState());
			assertTrue(target.rotorIndex() >= 0);
			assertTrue(target.rotorIndex() < 4);
			assertTrue(target.axialPlaneIndex() >= 1);
			assertTrue(target.axialPlaneIndex() <= 4);
			assertTrue(target.sweepColumnIndex() == -1 || target.sweepColumnIndex() == 0
					|| target.sweepColumnIndex() == 1);
			assertEquals(target.axialPlaneIndex() / 4.0, target.axialPlaneFraction(), 1.0e-12);
			assertTrue(target.axialDistanceMeters() > 0.0);
			assertTrue(target.freestreamSweepDistanceMeters() > 0.0);
			assertEquals(Math.hypot(target.axialDistanceMeters(), target.freestreamSweepDistanceMeters()),
					target.centerlineDistanceMeters(), 1.0e-12);
			assertEquals(Math.hypot(target.centerlineDistanceMeters(), target.lateralOffsetMeters()),
					target.distanceFromRotorMeters(), 1.0e-12);
			assertTrue(target.expectedAxialWakeVelocityMetersPerSecond() > 0.0);
			assertTrue(target.expectedFreestreamVelocityMetersPerSecond() > 0.0);
			assertEquals(Math.hypot(target.expectedAxialWakeVelocityMetersPerSecond(),
							target.expectedFreestreamVelocityMetersPerSecond()),
					target.expectedResultantWakeVelocityMetersPerSecond(), 1.0e-12);
			assertEquals(target.axialDistanceMeters() / target.expectedAxialWakeVelocityMetersPerSecond(),
					target.axialOnlyTransitSeconds(), 1.0e-12);
			assertEquals(target.freestreamSweepDistanceMeters() / target.expectedFreestreamVelocityMetersPerSecond(),
					target.freestreamSweepTransitSeconds(), 1.0e-12);
			assertEquals(target.centerlineDistanceMeters() / target.expectedResultantWakeVelocityMetersPerSecond(),
					target.centerlineResultantTransitSeconds(), 1.0e-12);
			assertEquals(target.distanceFromRotorMeters() / target.expectedResultantWakeVelocityMetersPerSecond(),
					target.lateralAdjustedTransitSeconds(), 1.0e-12);
			assertEquals(target.axialOnlyTransitSeconds(), target.freestreamSweepTransitSeconds(), 1.0e-12);
			assertEquals(target.axialOnlyTransitSeconds(), target.centerlineResultantTransitSeconds(), 1.0e-12);
			assertTrue(target.lateralAdjustedTransitSeconds() >= target.centerlineResultantTransitSeconds());
			assertEquals(target.lateralAdjustedTransitSeconds() - target.centerlineResultantTransitSeconds(),
					target.transitBandSeconds(), 1.0e-12);
			assertEquals(target.axialOnlyTransitSeconds() * 1000.0, target.axialTransitMilliseconds(), 1.0e-12);
			assertEquals(target.centerlineResultantTransitSeconds() * 1000.0,
					target.centerlineTransitMilliseconds(), 1.0e-12);
			assertEquals(target.lateralAdjustedTransitSeconds() * 1000.0,
					target.lateralTransitMilliseconds(), 1.0e-12);
			assertTrue(target.configuredDynamicInflowTauSeconds() > 0.0);
			assertEquals(target.configuredDynamicInflowTauSeconds() / target.centerlineResultantTransitSeconds(),
					target.tauOverCenterlineTransit(), 1.0e-12);
			assertEquals(target.configuredDynamicInflowTauSeconds() / target.lateralAdjustedTransitSeconds(),
					target.tauOverLateralTransit(), 1.0e-12);
			assertEquals(target.centerlineResultantTransitSeconds() / target.configuredDynamicInflowTauSeconds(),
					target.centerlineTransitOverConfiguredTau(), 1.0e-12);
			assertTrue(target.expectedAxialWakePressurePascals() > 0.0);
			assertTrue(target.expectedResultantDynamicPressurePascals() > 0.0);
			assertTrue(target.perRotorAxialMomentumFluxNewtons() > 0.0);
			assertEquals(4, target.minimumSamplesPerFastTransit());
			assertEquals(target.centerlineResultantTransitSeconds() / 4.0,
					target.recommendedMaxSamplePeriodSeconds(), 1.0e-12);
			assertEquals(4.0 / target.centerlineResultantTransitSeconds(),
					target.recommendedMinSampleRateHertz(), 1.0e-12);
			assertFalse(target.skewWakeProbeApiAvailable());
			assertFalse(target.transientProbeApiAvailable());
			assertFalse(target.runtimeCouplingAllowed());
			assertTrue(target.validationBeforeRuntimeRequired());
			assertEquals("target-only-transient-cruise-skew-wake-probe-unavailable", target.status());
			assertEquals("audit-only-unvalidated-cruise-skew-wake-transit", target.runtimeInfo());
		}
		assertEquals(192, audit.extrema().transitTargetCount());
		assertEquals(16, audit.extrema().sourceTermCount());
		assertEquals(4, audit.extrema().axialPlaneCount());
		assertEquals(3, audit.extrema().sweepColumnCount());
		assertEquals(0, audit.extrema().transientProbeApiAvailableCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(audit.targets().stream()
						.mapToDouble(Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget::centerlineResultantTransitSeconds)
						.max().orElseThrow(),
				audit.extrema().maxCenterlineTransitSeconds(), 1.0e-12);
	}

	@Test
	void transitTargetMatchesProbeMapAndRotorTau() {
		DroneConfig config = DroneConfig.racingQuad();
		List<Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe> probes =
				Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.probes(
						"racingQuad",
						config,
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe centerline =
				findProbe(probes, "racingQuad", 4, 0, 0);
		Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe lateral =
				findProbe(probes, "racingQuad", 4, 1, 0);
		RotorSpec rotor = config.rotors().get(0);
		Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget centerTarget =
				Aerodynamics4McL2PoweredCruiseSkewWakeTransit.target(centerline, rotor);
		Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget lateralTarget =
				Aerodynamics4McL2PoweredCruiseSkewWakeTransit.target(lateral, rotor);

		assertEquals(centerline.centerlineDistanceMeters(), centerTarget.centerlineDistanceMeters(), 1.0e-12);
		assertEquals(centerline.expectedResultantWakeVelocityMetersPerSecond(),
				centerTarget.expectedResultantWakeVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(centerline.expectedAxialWakePressurePascals(),
				centerTarget.expectedAxialWakePressurePascals(), 1.0e-12);
		assertEquals(centerline.expectedResultantDynamicPressurePascals(),
				centerTarget.expectedResultantDynamicPressurePascals(), 1.0e-12);
		assertEquals(centerline.perRotorAxialMomentumFluxNewtons(),
				centerTarget.perRotorAxialMomentumFluxNewtons(), 1.0e-12);
		assertEquals(rotor.inducedInflowTimeConstantSeconds(),
				centerTarget.configuredDynamicInflowTauSeconds(), 1.0e-12);
		assertEquals(0.035, centerTarget.configuredDynamicInflowTauSeconds(), 1.0e-12);
		assertEquals(0.0, centerTarget.transitBandSeconds(), 1.0e-12);
		assertTrue(lateralTarget.transitBandSeconds() > 0.0);
		assertTrue(lateralTarget.lateralAdjustedTransitSeconds() > centerTarget.centerlineResultantTransitSeconds());
	}

	@Test
	void transitRequiresCruiseProbeAndRotor() {
		DroneConfig config = DroneConfig.racingQuad();
		Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe probe =
				Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.probes(
						"racingQuad",
						config,
						new Vec3(0.0, 0.0, -18.0),
						72
				).get(0);
		RotorSpec rotor = config.rotors().get(0);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeTransit.targets("missing", null, Vec3.ZERO, 24));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeTransit.target(null, rotor));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeTransit.target(probe, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeTransit.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_cruise_skew_wake_transit_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_transit_summary,all_targets,transit_target_count,192,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_transit,racingQuad:plane4:sweep0:rotor0,minimum_samples_per_fast_transit,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_transit,racingQuad:plane4:sweep0:rotor0,transient_probe_api_available,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_transit,racingQuad:plane4:sweep0:rotor0,runtime_coupling_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe findProbe(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe> probes,
			String presetName,
			int axialPlaneIndex,
			int sweepColumnIndex,
			int rotorIndex
	) {
		return probes.stream()
				.filter(probe -> presetName.equals(probe.presetName())
						&& axialPlaneIndex == probe.axialPlaneIndex()
						&& sweepColumnIndex == probe.sweepColumnIndex()
						&& rotorIndex == probe.rotorIndex())
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_cruise_skew_wake_transit_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
