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

class Aerodynamics4McL2PoweredHoverSurfaceWakeTransitTest {
	@Test
	void auditBuildsStableSurfaceWakeTransitTargets() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.audit();

		assertEquals("A4MC-L2-Powered-Hover-Surface-Wake-Transit-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("transient probes"));
		assertEquals(4243, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(4, audit.rotorSampleCount());
		assertEquals(4, audit.clearanceSampleCount());
		assertEquals(2, audit.surfaceSampleCount());
		assertEquals(128, audit.transitSampleCount());
		assertEquals(33, audit.transitMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(128, audit.targets().size());

		for (Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget target
				: audit.targets()) {
			assertTrue(target.presetName().length() > 0);
			assertEquals("hover", target.spinState());
			assertTrue("ground".equals(target.surfaceType()) || "ceiling".equals(target.surfaceType()));
			assertTrue(target.rotorIndex() >= 0);
			assertTrue(target.rotorIndex() < 4);
			assertTrue(target.clearanceOverRadius() == 0.5
					|| target.clearanceOverRadius() == 1.0
					|| target.clearanceOverRadius() == 2.0
					|| target.clearanceOverRadius() == 4.0);
			assertEquals(target.rotorDiskRadiusMeters() * target.clearanceOverRadius(),
					target.clearanceMeters(), 1.0e-12);
			assertTrue(target.clearanceOverFarWakeRadius() > 0.0);
			assertEquals(2.0 * target.idealInducedVelocityMetersPerSecond(),
					target.farWakeVelocityMetersPerSecond(), 1.0e-12);
			assertEquals(0.5 * (
							target.idealInducedVelocityMetersPerSecond()
									+ target.farWakeVelocityMetersPerSecond()),
					target.meanWakeVelocityMetersPerSecond(), 1.0e-12);
			assertEquals(target.clearanceMeters() / target.idealInducedVelocityMetersPerSecond(),
					target.slowDiskPlaneTransitSeconds(), 1.0e-12);
			assertEquals(target.clearanceMeters() / target.farWakeVelocityMetersPerSecond(),
					target.fastFarWakeTransitSeconds(), 1.0e-12);
			assertEquals(target.clearanceMeters() / target.meanWakeVelocityMetersPerSecond(),
					target.meanWakeTransitSeconds(), 1.0e-12);
			assertTrue(target.slowDiskPlaneTransitSeconds() > target.meanWakeTransitSeconds());
			assertTrue(target.meanWakeTransitSeconds() > target.fastFarWakeTransitSeconds());
			assertEquals(target.slowDiskPlaneTransitSeconds() - target.fastFarWakeTransitSeconds(),
					target.transitBandSeconds(), 1.0e-12);
			assertEquals(target.slowDiskPlaneTransitSeconds() * 1000.0,
					target.slowDiskPlaneTransitMilliseconds(), 1.0e-12);
			assertEquals(target.fastFarWakeTransitSeconds() * 1000.0,
					target.fastFarWakeTransitMilliseconds(), 1.0e-12);
			assertTrue(target.configuredDynamicInflowTauSeconds() > 0.0);
			assertEquals(target.configuredDynamicInflowTauSeconds() / target.fastFarWakeTransitSeconds(),
					target.tauOverFastTransit(), 1.0e-12);
			assertEquals(target.configuredDynamicInflowTauSeconds() / target.meanWakeTransitSeconds(),
					target.tauOverMeanTransit(), 1.0e-12);
			assertEquals(target.fastFarWakeTransitSeconds() / target.configuredDynamicInflowTauSeconds(),
					target.fastTransitOverConfiguredTau(), 1.0e-12);
			assertTrue(target.perRotorImpingementPressurePascals() > 0.0);
			assertTrue(target.surfaceCurveMultiplier() >= 1.0);
			assertTrue(target.perRotorSurfaceCushionForceNewtons() >= 0.0);
			assertEquals(4, target.minimumSamplesPerFastTransit());
			assertEquals(target.fastFarWakeTransitSeconds() / 4.0,
					target.recommendedMaxSamplePeriodSeconds(), 1.0e-12);
			assertEquals(4.0 / target.fastFarWakeTransitSeconds(),
					target.recommendedMinSampleRateHertz(), 1.0e-12);
			assertFalse(target.localProbeApiAvailable());
			assertFalse(target.transientProbeApiAvailable());
			assertFalse(target.runtimeCouplingAllowed());
			assertTrue(target.validationBeforeRuntimeRequired());
			assertEquals("target-only-transient-wake-probe-unavailable", target.status());
			assertEquals("audit-only-unvalidated-surface-wake-transit", target.runtimeInfo());
		}
		assertEquals(128, audit.extrema().transitTargetCount());
		assertEquals(64, audit.extrema().groundTargetCount());
		assertEquals(64, audit.extrema().ceilingTargetCount());
		assertEquals(0, audit.extrema().localProbeApiAvailableCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(audit.targets().stream()
						.mapToDouble(Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget::slowDiskPlaneTransitSeconds)
						.max().orElseThrow(),
				audit.extrema().maxSlowDiskPlaneTransitSeconds(), 1.0e-12);
	}

	@Test
	void transitTargetMatchesProbeMapAndRotorTau() {
		DroneConfig config = DroneConfig.racingQuad();
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap =
				Aerodynamics4McL2PoweredHoverSourceMap.sourceMap(
						"racingQuad",
						config,
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe probe =
				findProbe(Aerodynamics4McL2PoweredHoverSurfaceProbeMap.probes(sourceMap),
						"racingQuad", "ground", 1.0, 0);
		RotorSpec rotor = config.rotors().get(0);
		Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget target =
				Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.target(sourceMap, probe, rotor);

		assertEquals(sourceMap.idealInducedVelocityMetersPerSecond(),
				target.idealInducedVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(sourceMap.farWakeVelocityMetersPerSecond(),
				target.farWakeVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(probe.perRotorImpingementPressurePascals(),
				target.perRotorImpingementPressurePascals(), 1.0e-12);
		assertEquals(probe.surfaceCurveMultiplier(), target.surfaceCurveMultiplier(), 1.0e-12);
		assertEquals(probe.perRotorSurfaceCushionForceNewtons(),
				target.perRotorSurfaceCushionForceNewtons(), 1.0e-12);
		assertEquals(rotor.inducedInflowTimeConstantSeconds(),
				target.configuredDynamicInflowTauSeconds(), 1.0e-12);
		assertEquals(0.035, target.configuredDynamicInflowTauSeconds(), 1.0e-12);
		assertEquals(0.063500000000000001 / 19.583980442431486,
				target.fastFarWakeTransitSeconds(), 1.0e-15);
	}

	@Test
	void targetRequiresHoverSourceProbeAndRotor() {
		DroneConfig config = DroneConfig.racingQuad();
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap =
				Aerodynamics4McL2PoweredHoverSourceMap.sourceMap(
						"racingQuad",
						config,
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe probe =
				Aerodynamics4McL2PoweredHoverSurfaceProbeMap.probes(sourceMap).get(0);
		RotorSpec rotor = config.rotors().get(0);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.targets(
						"missing",
						null,
						Vec3.ZERO,
						24
				));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.target(null, probe, rotor));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.target(sourceMap, probe, null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_hover_surface_wake_transit_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_transit_summary,all_targets,transit_target_count,128,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_transit,racingQuad:ground:hR1.0:rotor0,minimum_samples_per_fast_transit,4,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_transit,racingQuad:ground:hR1.0:rotor0,transient_probe_api_available,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_transit,racingQuad:ground:hR1.0:rotor0,runtime_coupling_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe findProbe(
			List<Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe> probes,
			String presetName,
			String surfaceType,
			double clearanceOverRadius,
			int rotorIndex
	) {
		return probes.stream()
				.filter(probe -> presetName.equals(probe.presetName())
						&& surfaceType.equals(probe.surfaceType())
						&& Math.abs(clearanceOverRadius - probe.clearanceOverRadius()) <= 1.0e-12
						&& rotorIndex == probe.rotorIndex())
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_hover_surface_wake_transit_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
