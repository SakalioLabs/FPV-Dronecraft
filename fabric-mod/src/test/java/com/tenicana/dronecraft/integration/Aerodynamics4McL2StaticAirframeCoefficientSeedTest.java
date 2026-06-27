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

class Aerodynamics4McL2StaticAirframeCoefficientSeedTest {
	@Test
	void auditBuildsStaticAirframeCoefficientSeedPacket() {
		Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientAudit audit =
				Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Static-Airframe-Coefficient-Seed-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Coefficient seed"));
		assertEquals(90, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(19, audit.coefficientMetricCount());
		assertEquals(8, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(4, audit.seeds().size());

		for (Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed : audit.seeds()) {
			assertTrue(seed.coefficientFitReady());
			assertTrue(seed.sourceRunAvailable());
			assertTrue(seed.referenceAreaSquareMeters() > 0.0);
			assertTrue(seed.referenceLengthMeters() > 0.0);
			assertEquals(1.225, seed.airDensityKgM3(), 1.0e-12);
			assertTrue(seed.dynamicPressurePascals() > 0.0);
			assertEquals(seed.forceCoefficientZ(), seed.dragCoefficient(), 1.0e-12);
			assertEquals(seed.forceCoefficientX(), seed.sideForceCoefficient(), 1.0e-12);
			assertEquals(seed.forceCoefficientY(), seed.liftCoefficient(), 1.0e-12);
			assertTrue(seed.momentCoefficientMagnitude() > 0.0);
			assertTrue(seed.pressureCenterOffsetRatio() > 0.0);
			assertEquals("OK", seed.sourceRunStatus());
			assertEquals("test-runtime", seed.sourceRuntimeInfo());
		}
		assertEquals(4, audit.extrema().coefficientSeedCount());
		assertEquals(4, audit.extrema().fitReadyCount());
		assertEquals(audit.seeds().stream()
						.mapToDouble(Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed::dynamicPressurePascals)
						.max().orElseThrow(),
				audit.extrema().maxDynamicPressurePascals(), 1.0e-12);
		assertEquals(audit.seeds().stream()
						.mapToDouble(Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed::dragCoefficient)
						.max().orElseThrow(),
				audit.extrema().maxDragCoefficient(), 1.0e-12);
	}

	@Test
	void coefficientSeedMatchesRunAndGeometryScaling() {
		Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunMatrixAudit runMatrix =
				Aerodynamics4McL2StaticAirframeRunMatrix.audit(getClass().getClassLoader());
		Aerodynamics4McL2DroneProbeAudit.DroneProbeGeometryAudit geometryAudit =
				Aerodynamics4McL2DroneProbeAudit.audit();
		Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunSummary run = runMatrix.runs().get(0);
		Aerodynamics4McL2DroneProbeAudit.PresetProbeSummary geometry = geometryAudit.presets().get(0);

		Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed =
				Aerodynamics4McL2StaticAirframeCoefficientSeed.seed(run, geometry);

		double referenceArea = Math.PI * geometry.bodyHalfX() * geometry.bodyHalfY();
		double dynamicPressure = 0.5 * 1.225 * run.inletSpeedMetersPerSecond() * run.inletSpeedMetersPerSecond();
		double forceScale = dynamicPressure * referenceArea;
		double momentScale = forceScale * geometry.rotorSpanMeters();
		assertEquals(referenceArea, seed.referenceAreaSquareMeters(), 1.0e-15);
		assertEquals(dynamicPressure, seed.dynamicPressurePascals(), 1.0e-12);
		assertEquals(run.forceZNewtons() / forceScale, seed.dragCoefficient(), 1.0e-12);
		assertEquals(run.momentMagnitudeNewtonMeters() / momentScale, seed.momentCoefficientMagnitude(), 1.0e-12);
		assertEquals(run.pressureCenterOffsetMeters() / geometry.rotorSpanMeters(),
				seed.pressureCenterOffsetRatio(), 1.0e-12);
	}

	@Test
	void unavailableProductionRunDoesNotBecomeFitReady() {
		Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientAudit audit =
				Aerodynamics4McL2StaticAirframeCoefficientSeed.audit();

		assertEquals(4, audit.seeds().size());
		for (Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientSeed seed : audit.seeds()) {
			assertFalse(seed.coefficientFitReady());
			assertFalse(seed.sourceRunAvailable());
		}
		assertEquals(0, audit.extrema().fitReadyCount());
	}

	@Test
	void coefficientSeedRejectsInvalidInputs() {
		Aerodynamics4McL2StaticAirframeRunMatrix.StaticAirframeRunMatrixAudit runMatrix =
				Aerodynamics4McL2StaticAirframeRunMatrix.audit(getClass().getClassLoader());
		Aerodynamics4McL2DroneProbeAudit.DroneProbeGeometryAudit geometryAudit =
				Aerodynamics4McL2DroneProbeAudit.audit();

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(null, geometryAudit));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(runMatrix, null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeCoefficientSeed.seed(null, geometryAudit.presets().get(0)));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeCoefficientSeed.seed(runMatrix.runs().get(0), geometryAudit.presets().get(1)));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2StaticAirframeCoefficientSeed.StaticAirframeCoefficientAudit audit =
				Aerodynamics4McL2StaticAirframeCoefficientSeed.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_static_airframe_coefficient_seed_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_coefficient_seed_summary,all_presets,fit_ready_count,4,")));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_static_airframe_coefficient_seed_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
