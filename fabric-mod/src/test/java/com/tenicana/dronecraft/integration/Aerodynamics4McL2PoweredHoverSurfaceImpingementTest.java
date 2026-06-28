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

class Aerodynamics4McL2PoweredHoverSurfaceImpingementTest {
	@Test
	void auditBuildsStablePoweredHoverSurfaceImpingementTargets() {
		Aerodynamics4McL2PoweredHoverSurfaceImpingement.PoweredHoverSurfaceImpingementAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceImpingement.audit();

		assertEquals("A4MC-L2-Powered-Hover-Surface-Impingement-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("JIRS ground/ceiling"));
		assertEquals(819, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(4, audit.presetSampleCount());
		assertEquals(4, audit.clearanceSampleCount());
		assertEquals(2, audit.surfaceSampleCount());
		assertEquals(32, audit.targetSampleCount());
		assertEquals(25, audit.targetMetricCount());
		assertEquals(12, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(32, audit.targets().size());

		for (Aerodynamics4McL2PoweredHoverSurfaceImpingement.PoweredHoverSurfaceImpingementTarget target
				: audit.targets()) {
			assertEquals("hover", target.spinState());
			assertTrue("ground".equals(target.surfaceType()) || "ceiling".equals(target.surfaceType()));
			assertEquals(4, target.rotorCount());
			assertEquals(4, target.sourceTermCount());
			assertTrue(target.clearanceOverRadius() == 0.5
					|| target.clearanceOverRadius() == 1.0
					|| target.clearanceOverRadius() == 2.0
					|| target.clearanceOverRadius() == 4.0);
			assertEquals(target.rotorRadiusMeters() * target.clearanceOverRadius(),
					target.clearanceMeters(), 1.0e-12);
			assertEquals(target.clearanceMeters() / target.farWakeEquivalentRadiusMeters(),
					target.clearanceOverFarWakeRadius(), 1.0e-12);
			assertTrue(target.diskEquivalentRadiusMeters() > target.farWakeEquivalentRadiusMeters());
			assertTrue(target.totalThrustNewtons() > 0.0);
			assertEquals(target.totalThrustNewtons(), target.farWakeMomentumFluxNewtons(), 1.0e-12);
			assertEquals(target.farWakeMomentumFluxNewtons() / target.farWakeContractedAreaSquareMeters(),
					target.farWakeImpingementPressurePascals(), 1.0e-12);
			assertEquals(target.surfaceCurveMultiplier() - 1.0,
					target.surfaceExtraLiftFraction(), 1.0e-12);
			assertEquals(target.totalThrustNewtons() * target.surfaceExtraLiftFraction(),
					target.totalSurfaceCushionForceNewtons(), 1.0e-12);
			assertEquals(target.totalSurfaceCushionForceNewtons() / target.rotorCount(),
					target.perRotorSurfaceCushionForceNewtons(), 1.0e-12);
			assertEquals(target.surfaceExtraLiftFraction(), target.surfaceCushionForceOverWeight(), 1.0e-12);
			assertEquals(target.totalSurfaceCushionForceNewtons() / target.farWakeContractedAreaSquareMeters(),
					target.surfaceReactionPressurePascals(), 1.0e-12);
			assertFalse(target.localWakeProbeApiAvailable());
			assertFalse(target.runtimeCouplingAllowed());
			assertTrue(target.validationBeforeRuntimeRequired());
			assertEquals("target-only-surface-wake-probe-unavailable", target.status());
			assertEquals("audit-only-unvalidated-surface-impingement", target.runtimeInfo());
		}
		assertEquals(32, audit.extrema().targetCount());
		assertEquals(16, audit.extrema().groundTargetCount());
		assertEquals(16, audit.extrema().ceilingTargetCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(0, audit.extrema().localWakeProbeApiAvailableCount());
		assertEquals(audit.targets().stream()
						.mapToDouble(Aerodynamics4McL2PoweredHoverSurfaceImpingement.PoweredHoverSurfaceImpingementTarget::surfaceCurveMultiplier)
						.max().orElseThrow(),
				audit.extrema().maxSurfaceCurveMultiplier(), 1.0e-12);
	}

	@Test
	void groundAndCeilingTargetsReuseJirsCurveFits() {
		List<Aerodynamics4McL2PoweredHoverSurfaceImpingement.PoweredHoverSurfaceImpingementTarget> targets =
				Aerodynamics4McL2PoweredHoverSurfaceImpingement.audit().targets();
		Aerodynamics4McL2PoweredHoverSurfaceImpingement.PoweredHoverSurfaceImpingementTarget groundOneRadius =
				find(targets, "racingQuad", "ground", 1.0);
		Aerodynamics4McL2PoweredHoverSurfaceImpingement.PoweredHoverSurfaceImpingementTarget ceilingOneRadius =
				find(targets, "racingQuad", "ceiling", 1.0);

		assertEquals(0.0635, groundOneRadius.rotorRadiusMeters(), 1.0e-12);
		assertEquals(SurfaceNearfieldCalibration.jirsGroundCurveFitMultiplier(1.0),
				groundOneRadius.surfaceCurveMultiplier(), 1.0e-15);
		assertEquals(1.0856401115902108, groundOneRadius.surfaceCurveMultiplier(), 1.0e-15);
		assertEquals(SurfaceNearfieldCalibration.jirsCeilingCurveFitMultiplier(1.0),
				ceilingOneRadius.surfaceCurveMultiplier(), 1.0e-15);
		assertEquals(1.096081775587298, ceilingOneRadius.surfaceCurveMultiplier(), 1.0e-12);
		assertTrue(ceilingOneRadius.totalSurfaceCushionForceNewtons()
				> groundOneRadius.totalSurfaceCushionForceNewtons());
	}

	@Test
	void targetMatchesHoverWakeFootprintMomentumFlux() {
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap =
				Aerodynamics4McL2PoweredHoverSourceMap.sourceMap(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint footprint =
				Aerodynamics4McL2PoweredHoverWakeFootprint.footprint(
						sourceMap,
						Aerodynamics4McL2PoweredSourceRequestPlan.request(
								"racingQuad",
								DroneConfig.racingQuad(),
								new Vec3(0.0, 0.0, -18.0),
								72,
								"hover"
						)
				);
		Aerodynamics4McL2PoweredHoverSurfaceImpingement.PoweredHoverSurfaceImpingementTarget target =
				Aerodynamics4McL2PoweredHoverSurfaceImpingement.target(sourceMap, footprint, "ground", 0.5);

		assertEquals(footprint.totalThrustNewtons(), target.totalThrustNewtons(), 1.0e-12);
		assertEquals(footprint.farWakeMomentumFluxNewtons(), target.farWakeMomentumFluxNewtons(), 1.0e-12);
		assertEquals(footprint.farWakeContractedAreaSquareMeters(),
				target.farWakeContractedAreaSquareMeters(), 1.0e-12);
		assertEquals(footprint.farWakeEquivalentRadiusMeters(),
				target.farWakeEquivalentRadiusMeters(), 1.0e-12);
		assertEquals(SurfaceNearfieldCalibration.jirsGroundCurveFitMultiplier(0.5),
				target.surfaceCurveMultiplier(), 1.0e-15);
	}

	@Test
	void targetRequiresHoverInputsAndSupportedSurface() {
		Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap =
				Aerodynamics4McL2PoweredHoverSourceMap.sourceMap(
						"racingQuad",
						DroneConfig.racingQuad(),
						new Vec3(0.0, 0.0, -18.0),
						72
				);
		Aerodynamics4McL2PoweredHoverWakeFootprint.PoweredHoverWakeFootprint footprint =
				Aerodynamics4McL2PoweredHoverWakeFootprint.audit().footprints().get(0);

		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceImpingement.targets("missing", null, Vec3.ZERO, 24));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceImpingement.target(sourceMap, footprint, "wall", 1.0));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceImpingement.target(null, footprint, "ground", 1.0));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverSurfaceImpingement.PoweredHoverSurfaceImpingementAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceImpingement.audit();
		Path packet = findRepoRoot().resolve("docs/data/a4mc_l2_powered_hover_surface_impingement_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_impingement_summary,all_targets,target_count,32,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_impingement,racingQuad:ground:hR1.0,surface_curve_multiplier,1.0856401115902108,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_impingement,racingQuad:ceiling:hR1.0,runtime_coupling_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceImpingement.PoweredHoverSurfaceImpingementTarget find(
			List<Aerodynamics4McL2PoweredHoverSurfaceImpingement.PoweredHoverSurfaceImpingementTarget> targets,
			String presetName,
			String surfaceType,
			double clearanceOverRadius
	) {
		return targets.stream()
				.filter(target -> presetName.equals(target.presetName())
						&& surfaceType.equals(target.surfaceType())
						&& Math.abs(clearanceOverRadius - target.clearanceOverRadius()) <= 1.0e-12)
				.findFirst()
				.orElseThrow();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_l2_powered_hover_surface_impingement_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
