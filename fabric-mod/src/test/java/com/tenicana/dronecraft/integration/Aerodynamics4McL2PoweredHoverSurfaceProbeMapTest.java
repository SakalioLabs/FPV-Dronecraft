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
import com.tenicana.dronecraft.sim.SurfaceNearfieldCalibration;
import com.tenicana.dronecraft.sim.Vec3;

class Aerodynamics4McL2PoweredHoverSurfaceProbeMapTest {
	@Test
	void auditBuildsStableRotorResolvedSurfaceProbeMap() {
		Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbeAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceProbeMap.audit();

		assertEquals("A4MC-L2-Powered-Hover-Surface-Probe-Map-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Rotor-resolved"));
		assertEquals(4627, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(4, audit.rotorSampleCount());
		assertEquals(4, audit.clearanceSampleCount());
		assertEquals(2, audit.surfaceSampleCount());
		assertEquals(128, audit.probeSampleCount());
		assertEquals(36, audit.probeMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(128, audit.probes().size());

		for (Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe probe : audit.probes()) {
			assertEquals("hover", probe.spinState());
			assertTrue("ground".equals(probe.surfaceType()) || "ceiling".equals(probe.surfaceType()));
			assertTrue(probe.clearanceOverRadius() == 0.5
					|| probe.clearanceOverRadius() == 1.0
					|| probe.clearanceOverRadius() == 2.0
					|| probe.clearanceOverRadius() == 4.0);
			assertTrue(probe.rotorIndex() >= 0);
			assertTrue(probe.rotorIndex() < 4);
			assertEquals(1.0, axisLength(probe.thrustAxisXBody(), probe.thrustAxisYBody(), probe.thrustAxisZBody()),
					1.0e-12);
			assertEquals(1.0, axisLength(probe.surfaceNormalXBody(), probe.surfaceNormalYBody(),
					probe.surfaceNormalZBody()), 1.0e-12);
			assertEquals(-1.0, dot(
					probe.thrustAxisXBody(), probe.thrustAxisYBody(), probe.thrustAxisZBody(),
					probe.surfaceNormalXBody(), probe.surfaceNormalYBody(), probe.surfaceNormalZBody()
			) * probe.surfaceOffsetAxisSign(), 1.0e-12);
			assertEquals(probe.rotorDiskRadiusMeters() * probe.clearanceOverRadius(),
					probe.clearanceMeters(), 1.0e-12);
			assertEquals(0.5 * probe.rotorOpenAreaSquareMeters(), probe.perRotorFarWakeAreaSquareMeters(), 1.0e-12);
			assertEquals(Math.sqrt(probe.perRotorFarWakeAreaSquareMeters() / Math.PI),
					probe.perRotorFarWakeEquivalentRadiusMeters(), 1.0e-12);
			assertEquals(probe.rotorThrustNewtons() / probe.perRotorFarWakeAreaSquareMeters(),
					probe.perRotorImpingementPressurePascals(), 1.0e-12);
			assertEquals(probe.surfaceCurveMultiplier() - 1.0,
					probe.surfaceExtraLiftFraction(), 1.0e-12);
			assertEquals(probe.rotorThrustNewtons() * probe.surfaceExtraLiftFraction(),
					probe.perRotorSurfaceCushionForceNewtons(), 1.0e-12);
			assertEquals(probe.surfaceExtraLiftFraction(), probe.surfaceCushionForceOverRotorThrust(), 1.0e-12);
			assertEquals(probe.perRotorSurfaceCushionForceNewtons() / probe.perRotorFarWakeAreaSquareMeters(),
					probe.perRotorSurfaceReactionPressurePascals(), 1.0e-12);
			assertTrue(probe.expectedWakeSpeedMetersPerSecond() > 0.0);
			assertFalse(probe.localProbeApiAvailable());
			assertFalse(probe.runtimeCouplingAllowed());
			assertTrue(probe.validationBeforeRuntimeRequired());
		}
		assertEquals(128, audit.extrema().probeCount());
		assertEquals(64, audit.extrema().groundProbeCount());
		assertEquals(64, audit.extrema().ceilingProbeCount());
		assertEquals(128, audit.extrema().rotorProbeCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().localProbeApiAvailableCount());
		assertEquals(audit.probes().stream()
						.mapToDouble(Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe::perRotorImpingementPressurePascals)
						.max().orElseThrow(),
				audit.extrema().maxPerRotorImpingementPressurePascals(), 1.0e-12);
	}

	@Test
	void probePointsFollowRotorAxisForGroundAndCeiling() {
		List<Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe> probes =
				Aerodynamics4McL2PoweredHoverSurfaceProbeMap.audit().probes();
		Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe ground =
				find(probes, "racingQuad", "ground", 1.0, 0);
		Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe ceiling =
				find(probes, "racingQuad", "ceiling", 1.0, 0);

		assertEquals(0.12727922061357855, ground.rotorCenterXBodyMeters(), 1.0e-12);
		assertEquals(0.0, ground.rotorCenterYBodyMeters(), 1.0e-12);
		assertEquals(0.12727922061357855, ground.rotorCenterZBodyMeters(), 1.0e-12);
		assertEquals(0.0635, ground.clearanceMeters(), 1.0e-12);
		assertEquals(-1.0, ground.surfaceOffsetAxisSign(), 1.0e-12);
		assertEquals(ground.rotorCenterXBodyMeters(), ground.probeXBodyMeters(), 1.0e-12);
		assertEquals(-0.0635, ground.probeYBodyMeters(), 1.0e-12);
		assertEquals(ground.rotorCenterZBodyMeters(), ground.probeZBodyMeters(), 1.0e-12);
		assertEquals(0.0, ground.surfaceNormalXBody(), 1.0e-12);
		assertEquals(1.0, ground.surfaceNormalYBody(), 1.0e-12);
		assertEquals(0.0, ground.surfaceNormalZBody(), 1.0e-12);

		assertEquals(1.0, ceiling.surfaceOffsetAxisSign(), 1.0e-12);
		assertEquals(ceiling.rotorCenterXBodyMeters(), ceiling.probeXBodyMeters(), 1.0e-12);
		assertEquals(0.0635, ceiling.probeYBodyMeters(), 1.0e-12);
		assertEquals(ceiling.rotorCenterZBodyMeters(), ceiling.probeZBodyMeters(), 1.0e-12);
		assertEquals(0.0, ceiling.surfaceNormalXBody(), 1.0e-12);
		assertEquals(-1.0, ceiling.surfaceNormalYBody(), 1.0e-12);
		assertEquals(0.0, ceiling.surfaceNormalZBody(), 1.0e-12);
	}

	@Test
	void probeMatchesSourceTermAndSurfaceCurve() {
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap =
				Aerodynamics4McL2PoweredHoverSourceMap.sourceMap(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm sourceTerm =
				sourceMap.sourceTerms().get(0);
		Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe probe =
				Aerodynamics4McL2PoweredHoverSurfaceProbeMap.probe(sourceMap, sourceTerm, "ground", 1.0);

		assertEquals(sourceTerm.thrustNewtons(), probe.rotorThrustNewtons(), 1.0e-12);
		assertEquals(sourceTerm.openAreaSquareMeters(), probe.rotorOpenAreaSquareMeters(), 1.0e-12);
		assertEquals(sourceTerm.pressureJumpPascals(), probe.rotorPressureJumpPascals(), 1.0e-12);
		assertEquals(2.0 * sourceTerm.pressureJumpPascals(),
				probe.perRotorImpingementPressurePascals(), 1.0e-12);
		assertEquals(sourceMap.farWakeVelocityMetersPerSecond(), probe.expectedWakeSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(SurfaceNearfieldCalibration.jirsGroundCurveFitMultiplier(1.0),
				probe.surfaceCurveMultiplier(), 1.0e-15);
		assertEquals(1.0856401115902108, probe.surfaceCurveMultiplier(), 1.0e-15);
	}

	@Test
	void probeRequiresHoverSourceMapAndSupportedSurface() {
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap =
				Aerodynamics4McL2PoweredHoverSourceMap.sourceMap(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm sourceTerm =
				sourceMap.sourceTerms().get(0);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceProbeMap.probes(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceProbeMap.probe(sourceMap, sourceTerm, "wall", 1.0));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceProbeMap.probe(null, sourceTerm, "ground", 1.0));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbeAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceProbeMap.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_hover_surface_probe_map_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_probe_summary,all_probes,probe_count,128,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_probe,racingQuad:ground:hR1.0:rotor0,probe_y_m,-0.063500000000000001,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_probe,racingQuad:ceiling:hR1.0:rotor0,surface_normal_y,-1,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_probe,racingQuad:ground:hR1.0:rotor0,runtime_coupling_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceProbeMap.PoweredHoverSurfaceProbe find(
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

	private static double axisLength(double x, double y, double z) {
		return Math.sqrt(x * x + y * y + z * z);
	}

	private static double dot(double ax, double ay, double az, double bx, double by, double bz) {
		return ax * bx + ay * by + az * bz;
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_hover_surface_probe_map_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
