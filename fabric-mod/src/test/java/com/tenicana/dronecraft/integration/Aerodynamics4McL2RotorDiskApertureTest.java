package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

class Aerodynamics4McL2RotorDiskApertureTest {
	@Test
	void auditBuildsStableRotorDiskAperturePacket() {
		Aerodynamics4McL2RotorDiskAperture.RotorDiskApertureAudit audit =
				Aerodynamics4McL2RotorDiskAperture.audit();

		assertEquals("A4MC-L2-Rotor-Disk-Aperture-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("actuator-disk aperture"));
		assertEquals(89, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(19, audit.presetMetricCount());
		assertEquals(7, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.radialSampleCount());
		assertEquals(16, audit.azimuthSampleCount());
		assertEquals(64, audit.rotorSampleCount());
		assertIterableEquals(
				List.of("racingQuad", "apDrone", "cinewhoop", "heavyLift"),
				audit.presets().stream().map(Aerodynamics4McL2RotorDiskAperture.PresetApertureSummary::presetName).toList()
		);

		for (Aerodynamics4McL2RotorDiskAperture.PresetApertureSummary summary : audit.presets()) {
			assertEquals(4, summary.rotorCount());
			assertEquals(4, summary.radialSampleCount());
			assertEquals(16, summary.azimuthSampleCount());
			assertEquals(summary.rotorCount() * 64, summary.sampleCount());
			assertTrue(summary.openSampleCount() > 0);
			assertTrue(summary.openFraction() > 0.60 && summary.openFraction() < 1.0);
			assertTrue(summary.totalDiskAreaSquareMeters() > 0.0);
			assertEquals(summary.openDiskAreaSquareMeters() + summary.blockedDiskAreaSquareMeters(),
					summary.totalDiskAreaSquareMeters(), 1.0e-12);
			assertTrue(summary.minRotorOpenFraction() <= summary.maxRotorOpenFraction());
			assertTrue(summary.meanRotorRadiusMeters() > 0.0);
			assertTrue(summary.meanRepresentativeBladeChordMeters() > 0.0);
			assertTrue(summary.maxRotorDiskRadiusCells() > 1.5);
		}
		assertEquals(0.0, audit.extrema().maxAxialInletSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(18.0, audit.extrema().maxInPlaneInletSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(audit.apDrone().blockedDiskAreaSquareMeters(), audit.extrema().maxBlockedDiskAreaSquareMeters(), 1.0e-12);
	}

	@Test
	void apertureAxesStayOrthonormalAndRespectRotorCant() {
		DroneConfig canted = DroneConfig.racingQuad().withRotorOutwardCantDegrees(12.0);
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(canted, new Vec3(0.0, 0.0, -18.0), 72);
		List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures =
				Aerodynamics4McL2RotorDiskAperture.apertures(canted, probe);

		assertEquals(canted.rotors().size(), apertures.size());
		assertTrue(apertures.stream().anyMatch(aperture -> Math.abs(aperture.thrustAxisBody().z()) > 0.01));
		for (Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture aperture : apertures) {
			assertEquals(1.0, aperture.thrustAxisBody().length(), 1.0e-12);
			assertEquals(1.0, aperture.radialAxisBody().length(), 1.0e-12);
			assertEquals(1.0, aperture.tangentialAxisBody().length(), 1.0e-12);
			assertEquals(0.0, aperture.thrustAxisBody().dot(aperture.radialAxisBody()), 1.0e-12);
			assertEquals(0.0, aperture.thrustAxisBody().dot(aperture.tangentialAxisBody()), 1.0e-12);
			assertEquals(0.0, aperture.radialAxisBody().dot(aperture.tangentialAxisBody()), 1.0e-12);
			assertEquals(64, aperture.sampleCount());
			assertTrue(aperture.openSampleCount() > 0 && aperture.openSampleCount() < aperture.sampleCount());
			assertEquals(aperture.diskAreaSquareMeters() * aperture.openFraction(),
					aperture.openAreaSquareMeters(), 1.0e-12);
			assertTrue(aperture.axialInletSpeedMetersPerSecond() > 0.0);
			assertTrue(aperture.inPlaneInletSpeedMetersPerSecond() > 0.0);
		}
	}

	@Test
	void summaryRequiresRotorConfigurationAndProbe() {
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2RotorDiskAperture.summary("missing", null, null, 24));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2RotorDiskAperture.apertures(null, probe));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2RotorDiskAperture.apertures(DroneConfig.apDrone(), null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2RotorDiskAperture.RotorDiskApertureAudit audit =
				Aerodynamics4McL2RotorDiskAperture.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_rotor_disk_aperture_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertFalse(lines.isEmpty());
		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_rotor_disk_aperture_summary,all_presets,max_preset_sample_count,256,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
