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

import com.tenicana.dronecraft.sim.Vec3;

class Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeedTest {
	@Test
	void auditBuildsStableMultiAxisCoefficientSeedPacket() {
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.audit(getClass().getClassLoader());

		assertEquals("A4MC-L2-Static-Airframe-Multi-Axis-Coefficient-Seed-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("Multi-axis coefficient seed"));
		assertEquals(593, audit.packetMetricRowCount());
		assertEquals(5, audit.sourceReferenceCount());
		assertEquals(24, audit.sweepCaseSampleCount());
		assertEquals(24, audit.coefficientMetricCount());
		assertEquals(11, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(24, audit.seeds().size());

		for (Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed : audit.seeds()) {
			assertTrue(seed.coefficientFitReady());
			assertTrue(seed.sourceRunAvailable());
			assertEquals(1.225, seed.airDensityKgM3(), 1.0e-12);
			assertTrue(seed.dynamicPressurePascals() > 0.0);
			assertTrue(seed.projectedReferenceAreaSquareMeters() > 0.0);
			assertTrue(seed.referenceLengthMeters() > 0.0);
			assertEquals(seed.forceCoefficientX(), seed.sideForceCoefficient(), 1.0e-12);
			assertEquals(seed.forceCoefficientY(), seed.liftCoefficient(), 1.0e-12);
			assertEquals("OK", seed.sourceRunStatus());
			assertEquals("test-runtime", seed.sourceRuntimeInfo());
		}
		assertEquals(24, audit.extrema().coefficientSeedCount());
		assertEquals(24, audit.extrema().fitReadyCount());
		assertEquals(8, audit.extrema().forwardDragSeedCount());
		assertEquals(8, audit.extrema().sideforceSeedCount());
		assertEquals(8, audit.extrema().liftSeedCount());
		assertEquals(24, audit.extrema().momentPressureCenterSeedCount());
	}

	@Test
	void coefficientSeedMatchesRunScalingAndInletAxisSign() {
		Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunMatrixAudit runMatrix =
				Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.audit(getClass().getClassLoader());
		Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunSummary forward =
				find(runMatrix.runs(), "racingQuad", "forward_drag");
		Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunSummary reverse =
				find(runMatrix.runs(), "racingQuad", "reverse_drag_symmetry");
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed forwardSeed =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.seed(forward);
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed reverseSeed =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.seed(reverse);

		double dynamicPressure = 0.5 * 1.225 * forward.inletSpeedMetersPerSecond() * forward.inletSpeedMetersPerSecond();
		double forceScale = dynamicPressure * forward.projectedReferenceAreaSquareMeters();
		double momentScale = forceScale * forward.referenceLengthMeters();
		assertEquals(dynamicPressure, forwardSeed.dynamicPressurePascals(), 1.0e-12);
		assertEquals(forward.forceZNewtons() / forceScale, forwardSeed.forceCoefficientZ(), 1.0e-12);
		assertEquals(forward.momentMagnitudeNewtonMeters() / momentScale,
				forwardSeed.momentCoefficientMagnitude(), 1.0e-12);
		assertEquals(forward.pressureCenterOffsetMeters() / forward.referenceLengthMeters(),
				forwardSeed.pressureCenterOffsetRatio(), 1.0e-12);
		assertEquals(forwardSeed.forceCoefficientZ(), forwardSeed.signedAxialForceCoefficient(), 1.0e-12);
		assertEquals(-forwardSeed.signedAxialForceCoefficient(),
				reverseSeed.signedAxialForceCoefficient(), 1.0e-12);
	}

	@Test
	void sideslipCoefficientUsesSignedInletAxisProjection() {
		Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunMatrixAudit runMatrix =
				Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.audit(getClass().getClassLoader());
		Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunSummary rightSideslip =
				find(runMatrix.runs(), "racingQuad", "right_sideslip_12deg");
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.seed(rightSideslip);

		double forceScale = seed.dynamicPressurePascals() * seed.projectedReferenceAreaSquareMeters();
		Vec3 force = new Vec3(
				rightSideslip.forceXNewtons(),
				rightSideslip.forceYNewtons(),
				rightSideslip.forceZNewtons()
		);
		Vec3 inlet = new Vec3(
				rightSideslip.inletVxMetersPerSecond(),
				rightSideslip.inletVyMetersPerSecond(),
				rightSideslip.inletVzMetersPerSecond()
		);
		double expectedAxial = force.dot(inlet.multiply(-1.0 / rightSideslip.inletSpeedMetersPerSecond())) / forceScale;
		assertTrue(seed.sideforceFitSample());
		assertFalse(seed.liftFitSample());
		assertEquals(expectedAxial, seed.signedAxialForceCoefficient(), 1.0e-12);
		assertTrue(seed.projectedReferenceAreaSquareMeters() > find(runMatrix.runs(), "racingQuad", "forward_drag")
				.projectedReferenceAreaSquareMeters());
	}

	@Test
	void unavailableProductionRunDoesNotBecomeFitReady() {
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.audit();

		assertEquals(24, audit.seeds().size());
		for (Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientSeed seed : audit.seeds()) {
			assertFalse(seed.coefficientFitReady());
			assertFalse(seed.sourceRunAvailable());
		}
		assertEquals(0, audit.extrema().fitReadyCount());
	}

	@Test
	void coefficientSeedRejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.audit((ClassLoader) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.audit(
						(Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunMatrixAudit) null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.seed(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.StaticAirframeMultiAxisCoefficientAudit audit =
				Aerodynamics4McL2StaticAirframeMultiAxisCoefficientSeed.audit(getClass().getClassLoader());
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_static_airframe_multi_axis_coefficient_seed_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_multi_axis_coefficient_seed_summary,all_sweeps,fit_ready_count,24,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_static_airframe_multi_axis_coefficient_seed_case,racingQuad:right_sideslip_12deg,fit_role,sideforce_yaw_positive,")));
	}

	private static Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunSummary find(
			List<Aerodynamics4McL2StaticAirframeMultiAxisRunMatrix.StaticAirframeMultiAxisRunSummary> runs,
			String presetName,
			String sweepName
	) {
		return runs.stream()
				.filter(run -> presetName.equals(run.presetName()) && sweepName.equals(run.sweepName()))
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_static_airframe_multi_axis_coefficient_seed_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
