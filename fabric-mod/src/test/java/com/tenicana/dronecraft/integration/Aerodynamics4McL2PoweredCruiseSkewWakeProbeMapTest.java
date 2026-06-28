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
import com.tenicana.dronecraft.sim.Vec3;

class Aerodynamics4McL2PoweredCruiseSkewWakeProbeMapTest {
	@Test
	void auditBuildsStableRotorResolvedSkewWakeProbeMap() {
		Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbeAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Skew-Wake-Probe-Map-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Rotor-resolved"));
		assertEquals(9235, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(4, audit.rotorSampleCount());
		assertEquals(4, audit.axialSamplePlaneCount());
		assertEquals(3, audit.sweepSampleColumnCount());
		assertEquals(192, audit.probeSampleCount());
		assertEquals(48, audit.probeMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(1.225, audit.airDensityKilogramsPerCubicMeter(), 1.0e-12);
		assertEquals(192, audit.probes().size());

		for (Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe probe : audit.probes()) {
			assertEquals("cruise", probe.spinState());
			assertTrue(probe.rotorIndex() >= 0);
			assertTrue(probe.rotorIndex() < 4);
			assertTrue(probe.axialPlaneIndex() >= 1);
			assertTrue(probe.axialPlaneIndex() <= 4);
			assertTrue(probe.sweepColumnIndex() == -1 || probe.sweepColumnIndex() == 0
					|| probe.sweepColumnIndex() == 1);
			assertEquals(probe.axialPlaneIndex() / 4.0, probe.axialPlaneFraction(), 1.0e-12);
			assertEquals(1.0, axisLength(probe.thrustAxisXBody(), probe.thrustAxisYBody(),
					probe.thrustAxisZBody()), 1.0e-12);
			assertEquals(1.0, axisLength(probe.axialWakeAxisXBody(), probe.axialWakeAxisYBody(),
					probe.axialWakeAxisZBody()), 1.0e-12);
			assertEquals(-1.0, dot(
					probe.thrustAxisXBody(), probe.thrustAxisYBody(), probe.thrustAxisZBody(),
					probe.axialWakeAxisXBody(), probe.axialWakeAxisYBody(), probe.axialWakeAxisZBody()
			), 1.0e-12);
			assertEquals(1.0, axisLength(probe.freestreamAxisXBody(), probe.freestreamAxisYBody(),
					probe.freestreamAxisZBody()), 1.0e-12);
			assertEquals(1.0, axisLength(probe.lateralSweepAxisXBody(), probe.lateralSweepAxisYBody(),
					probe.lateralSweepAxisZBody()), 1.0e-12);
			assertEquals(0.0, dot(
					probe.lateralSweepAxisXBody(), probe.lateralSweepAxisYBody(), probe.lateralSweepAxisZBody(),
					probe.axialWakeAxisXBody(), probe.axialWakeAxisYBody(), probe.axialWakeAxisZBody()
			), 1.0e-12);
			assertEquals(0.0, dot(
					probe.lateralSweepAxisXBody(), probe.lateralSweepAxisYBody(), probe.lateralSweepAxisZBody(),
					probe.freestreamAxisXBody(), probe.freestreamAxisYBody(), probe.freestreamAxisZBody()
			), 1.0e-12);
			assertEquals(probe.sweepColumnIndex() * probe.perRotorFarWakeEquivalentRadiusMeters(),
					probe.lateralOffsetMeters(), 1.0e-12);
			assertEquals(Math.hypot(probe.axialDistanceMeters(), probe.freestreamSweepDistanceMeters()),
					probe.centerlineDistanceMeters(), 1.0e-12);
			assertEquals(Math.hypot(probe.centerlineDistanceMeters(), probe.lateralOffsetMeters()),
					probe.distanceFromRotorMeters(), 1.0e-12);
			assertEquals(Math.sqrt(probe.perRotorFarWakeAreaSquareMeters() / Math.PI),
					probe.perRotorFarWakeEquivalentRadiusMeters(), 1.0e-12);
			assertEquals(probe.expectedAxialWakePressurePascals() * probe.perRotorFarWakeAreaSquareMeters(),
					probe.perRotorAxialMomentumFluxNewtons(), 1.0e-10);
			assertEquals(0.5 * 1.225
							* probe.expectedResultantWakeVelocityMetersPerSecond()
							* probe.expectedResultantWakeVelocityMetersPerSecond(),
					probe.expectedResultantDynamicPressurePascals(), 1.0e-12);
			assertEquals(probe.axialDistanceMeters() / probe.requestCellSizeMeters(),
					probe.axialDistanceCells(), 1.0e-12);
			assertEquals(probe.freestreamSweepDistanceMeters() / probe.requestCellSizeMeters(),
					probe.freestreamSweepDistanceCells(), 1.0e-12);
			assertEquals(probe.lateralOffsetMeters() / probe.requestCellSizeMeters(),
					probe.lateralOffsetCells(), 1.0e-12);
			assertFalse(probe.skewWakeProbeApiAvailable());
			assertFalse(probe.runtimeCouplingAllowed());
			assertTrue(probe.validationBeforeRuntimeRequired());
			assertEquals("target-only-cruise-skew-wake-probe-unavailable", probe.status());
			assertEquals("audit-only-unvalidated-cruise-skew-wake-probe", probe.runtimeInfo());
		}
		assertEquals(192, audit.extrema().probeCount());
		assertEquals(16, audit.extrema().sourceTermCount());
		assertEquals(4, audit.extrema().axialPlaneCount());
		assertEquals(3, audit.extrema().sweepColumnCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(192, audit.extrema().validationBeforeRuntimeRequiredCount());
		assertEquals(audit.probes().stream()
						.mapToDouble(Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe::expectedResultantDynamicPressurePascals)
						.max().orElseThrow(),
				audit.extrema().maxResultantDynamicPressurePascals(), 1.0e-12);
	}

	@Test
	void probeCoordinatesFollowSkewWakeBasis() {
		List<Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe> probes =
				Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.audit().probes();
		Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe centerline =
				find(probes, "racingQuad", 4, 0, 0);
		Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe left =
				find(probes, "racingQuad", 4, -1, 0);
		Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe right =
				find(probes, "racingQuad", 4, 1, 0);

		assertEquals(0.0, centerline.lateralOffsetMeters(), 1.0e-12);
		assertEquals(centerline.rotorCenterXBodyMeters()
						+ centerline.axialWakeAxisXBody() * centerline.axialDistanceMeters()
						+ centerline.freestreamAxisXBody() * centerline.freestreamSweepDistanceMeters(),
				centerline.probeXBodyMeters(), 1.0e-12);
		assertEquals(centerline.rotorCenterYBodyMeters()
						+ centerline.axialWakeAxisYBody() * centerline.axialDistanceMeters()
						+ centerline.freestreamAxisYBody() * centerline.freestreamSweepDistanceMeters(),
				centerline.probeYBodyMeters(), 1.0e-12);
		assertEquals(centerline.rotorCenterZBodyMeters()
						+ centerline.axialWakeAxisZBody() * centerline.axialDistanceMeters()
						+ centerline.freestreamAxisZBody() * centerline.freestreamSweepDistanceMeters(),
				centerline.probeZBodyMeters(), 1.0e-12);
		assertEquals(-centerline.perRotorFarWakeEquivalentRadiusMeters(), left.lateralOffsetMeters(), 1.0e-12);
		assertEquals(centerline.perRotorFarWakeEquivalentRadiusMeters(), right.lateralOffsetMeters(), 1.0e-12);
		assertEquals(centerline.probeXBodyMeters()
						+ centerline.lateralSweepAxisXBody() * left.lateralOffsetMeters(),
				left.probeXBodyMeters(), 1.0e-12);
		assertEquals(centerline.probeYBodyMeters()
						+ centerline.lateralSweepAxisYBody() * left.lateralOffsetMeters(),
				left.probeYBodyMeters(), 1.0e-12);
		assertEquals(centerline.probeZBodyMeters()
						+ centerline.lateralSweepAxisZBody() * left.lateralOffsetMeters(),
				left.probeZBodyMeters(), 1.0e-12);
		assertEquals(centerline.probeXBodyMeters()
						+ centerline.lateralSweepAxisXBody() * right.lateralOffsetMeters(),
				right.probeXBodyMeters(), 1.0e-12);
		assertEquals(centerline.probeYBodyMeters()
						+ centerline.lateralSweepAxisYBody() * right.lateralOffsetMeters(),
				right.probeYBodyMeters(), 1.0e-12);
		assertEquals(centerline.probeZBodyMeters()
						+ centerline.lateralSweepAxisZBody() * right.lateralOffsetMeters(),
				right.probeZBodyMeters(), 1.0e-12);
	}

	@Test
	void probeMatchesSourceFootprintAndRequestRows() {
		Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap sourceMap =
				Aerodynamics4McL2PoweredCruiseSourceMap.sourceMap(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest request =
				Aerodynamics4McL2PoweredSourceRequestPlan.request(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72,
						"cruise"
				);
		Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint footprint =
				Aerodynamics4McL2PoweredCruiseWakeFootprint.footprint(sourceMap, request);
		Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm sourceTerm =
				sourceMap.sourceTerms().get(0);
		Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe probe =
				Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.probe(sourceMap, footprint, request,
						sourceTerm, 2, 1);

		assertEquals(sourceMap.presetName(), probe.presetName());
		assertEquals(sourceTerm.rotorIndex(), probe.rotorIndex());
		assertEquals(sourceTerm.centerBodyMeters().x(), probe.rotorCenterXBodyMeters(), 1.0e-12);
		assertEquals(footprint.downstreamSampleDistanceMeters() * 0.5,
				probe.axialDistanceMeters(), 1.0e-12);
		assertEquals(footprint.freestreamSweepDistanceMeters() * 0.5,
				probe.freestreamSweepDistanceMeters(), 1.0e-12);
		assertEquals(footprint.axialWakeVelocityMetersPerSecond(),
				probe.expectedAxialWakeVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(footprint.freestreamVelocityMetersPerSecond(),
				probe.expectedFreestreamVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(footprint.resultantWakeVelocityMetersPerSecond(),
				probe.expectedResultantWakeVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(footprint.wakeSkewAngleDegrees(), probe.wakeSkewAngleDegrees(), 1.0e-12);
		assertEquals(sourceTerm.thrustNewtons() / probe.perRotorFarWakeAreaSquareMeters(),
				probe.expectedAxialWakePressurePascals(), 1.0e-12);
		assertEquals(sourceTerm.thrustNewtons(), probe.perRotorAxialMomentumFluxNewtons(), 1.0e-10);
		assertEquals(0.0, probe.axialMomentumFluxClosureErrorNewtons(), 1.0e-10);
		assertEquals(request.cellSizeMeters(), probe.requestCellSizeMeters(), 1.0e-12);
	}

	@Test
	void probeMapRequiresMatchingCruiseRowsAndSupportedSamples() {
		Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap sourceMap =
				Aerodynamics4McL2PoweredCruiseSourceMap.sourceMap(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest cruiseRequest =
				Aerodynamics4McL2PoweredSourceRequestPlan.request(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72,
						"cruise"
				);
		Aerodynamics4McL2PoweredSourceRequestPlan.PoweredSourceRequest hoverRequest =
				Aerodynamics4McL2PoweredSourceRequestPlan.request(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72,
						"hover"
				);
		Aerodynamics4McL2PoweredCruiseWakeFootprint.PoweredCruiseWakeFootprint footprint =
				Aerodynamics4McL2PoweredCruiseWakeFootprint.footprint(sourceMap, cruiseRequest);
		Aerodynamics4McL2ActuatorDiskSourceMap.RotorDiskSourceTerm sourceTerm =
				sourceMap.sourceTerms().get(0);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.probes("missing", null, Vec3.ZERO, 24));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.probes(sourceMap, footprint, hoverRequest));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.probe(sourceMap, footprint, cruiseRequest,
						sourceTerm, 0, 0));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.probe(sourceMap, footprint, cruiseRequest,
						sourceTerm, 1, 2));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.probe(sourceMap, footprint, cruiseRequest,
						null, 1, 0));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbeAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_cruise_skew_wake_probe_map_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_probe_summary,all_probes,probe_count,192,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_probe,racingQuad:plane4:sweep0:rotor0,spin_state,cruise,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_probe,racingQuad:plane4:sweep0:rotor0,runtime_coupling_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeProbeMap.PoweredCruiseSkewWakeProbe find(
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
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_cruise_skew_wake_probe_map_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
