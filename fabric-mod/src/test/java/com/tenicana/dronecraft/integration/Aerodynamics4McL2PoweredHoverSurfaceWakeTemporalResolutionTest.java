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

class Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolutionTest {
	@Test
	void auditBuildsStableSurfaceWakeTemporalResolutionTargets() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.audit();

		assertEquals("A4MC-L2-Powered-Hover-Surface-Wake-Temporal-Resolution-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("low-rate Minecraft"));
		assertEquals(4629, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(128, audit.temporalTargetCount());
		assertEquals(36, audit.temporalMetricCount());
		assertEquals(14, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(128, audit.targets().size());

		for (Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget target
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
			assertEquals(target.minimumSamplesPerFastTransit() / target.fastFarWakeTransitSeconds(),
					target.recommendedMinSampleRateHertz(), 1.0e-12);
			assertEquals(target.fastFarWakeTransitSeconds() / target.minimumSamplesPerFastTransit(),
					target.recommendedMaxSamplePeriodSeconds(), 1.0e-12);
			assertEquals(4, target.minimumSamplesPerFastTransit());
			assertEquals(20.0, target.minecraftTickRateHertz(), 1.0e-12);
			assertEquals(target.fastFarWakeTransitSeconds() * 20.0,
					target.minecraftTickSamplesPerFastTransit(), 1.0e-12);
			assertEquals(target.minecraftTickSamplesPerFastTransit() >= 4.0,
					target.minecraftTickResolvesFastTransit());
			assertEquals(requiredSubsteps(20.0, target.recommendedMaxSamplePeriodSeconds()),
					target.requiredSubstepsPerMinecraftTick());
			assertEquals(target.fastFarWakeTransitSeconds() * 60.0,
					target.sixtyHertzSamplesPerFastTransit(), 1.0e-12);
			assertEquals(target.sixtyHertzSamplesPerFastTransit() >= 4.0,
					target.sixtyHertzResolvesFastTransit());
			assertEquals(requiredSubsteps(60.0, target.recommendedMaxSamplePeriodSeconds()),
					target.requiredSubstepsPerSixtyHertzFrame());
			assertEquals(target.fastFarWakeTransitSeconds() * 120.0,
					target.oneTwentyHertzSamplesPerFastTransit(), 1.0e-12);
			assertEquals(target.oneTwentyHertzSamplesPerFastTransit() >= 4.0,
					target.oneTwentyHertzResolvesFastTransit());
			assertEquals(requiredSubsteps(120.0, target.recommendedMaxSamplePeriodSeconds()),
					target.requiredSubstepsPerOneTwentyHertzFrame());
			assertEquals(target.fastFarWakeTransitSeconds() * 240.0,
					target.twoFortyHertzSamplesPerFastTransit(), 1.0e-12);
			assertEquals(target.twoFortyHertzSamplesPerFastTransit() >= 4.0,
					target.twoFortyHertzResolvesFastTransit());
			assertEquals(requiredSubsteps(240.0, target.recommendedMaxSamplePeriodSeconds()),
					target.requiredSubstepsPerTwoFortyHertzFrame());
			assertEquals(target.fastFarWakeTransitSeconds() * 1000.0,
					target.oneKilohertzSamplesPerFastTransit(), 1.0e-12);
			assertEquals(target.oneKilohertzSamplesPerFastTransit() >= 4.0,
					target.oneKilohertzResolvesFastTransit());
			assertEquals(requiredSubsteps(1000.0, target.recommendedMaxSamplePeriodSeconds()),
					target.requiredSubstepsPerOneKilohertzFrame());
			assertFalse(target.transientProbeApiAvailable());
			assertFalse(target.runtimeCouplingAllowed());
			assertTrue(target.validationBeforeRuntimeRequired());
			assertEquals("target-only-temporal-resolution-unverified", target.status());
			assertEquals("audit-only-unvalidated-surface-wake-temporal-resolution", target.runtimeInfo());
		}

		assertEquals(128, audit.extrema().temporalTargetCount());
		assertEquals(64, audit.extrema().groundTargetCount());
		assertEquals(64, audit.extrema().ceilingTargetCount());
		assertEquals(countResolved(audit.targets(), "minecraft"), audit.extrema().minecraftTickResolvedCount());
		assertEquals(countResolved(audit.targets(), "sixty"), audit.extrema().sixtyHertzResolvedCount());
		assertEquals(countResolved(audit.targets(), "oneTwenty"), audit.extrema().oneTwentyHertzResolvedCount());
		assertEquals(countResolved(audit.targets(), "twoForty"), audit.extrema().twoFortyHertzResolvedCount());
		assertEquals(countResolved(audit.targets(), "oneKilohertz"), audit.extrema().oneKilohertzResolvedCount());
		assertEquals(0, audit.extrema().minecraftTickResolvedCount());
		assertEquals(0, audit.extrema().sixtyHertzResolvedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(audit.targets().stream()
						.mapToDouble(Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget::recommendedMinSampleRateHertz)
						.max().orElseThrow(),
				audit.extrema().maxRecommendedMinSampleRateHertz(), 1.0e-12);
		assertEquals(audit.targets().stream()
						.mapToInt(Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget::requiredSubstepsPerMinecraftTick)
						.max().orElseThrow(),
				audit.extrema().maxRequiredSubstepsPerMinecraftTick());
	}

	@Test
	void temporalResolutionTargetMatchesWakeTransitTarget() {
		Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget transit =
				findTransit(Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.audit().targets(),
						"racingQuad", "ground", 1.0, 0);
		Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget target =
				Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.target(transit);

		assertEquals(transit.fastFarWakeTransitSeconds(), target.fastFarWakeTransitSeconds(), 1.0e-12);
		assertEquals(transit.recommendedMaxSamplePeriodSeconds(),
				target.recommendedMaxSamplePeriodSeconds(), 1.0e-12);
		assertEquals(transit.recommendedMinSampleRateHertz(),
				target.recommendedMinSampleRateHertz(), 1.0e-12);
		assertEquals(transit.tauOverFastTransit(), target.tauOverFastTransit(), 1.0e-12);
		assertEquals(62, target.requiredSubstepsPerMinecraftTick());
		assertEquals(21, target.requiredSubstepsPerSixtyHertzFrame());
		assertEquals(11, target.requiredSubstepsPerOneTwentyHertzFrame());
		assertEquals(6, target.requiredSubstepsPerTwoFortyHertzFrame());
		assertEquals(2, target.requiredSubstepsPerOneKilohertzFrame());
		assertFalse(target.minecraftTickResolvesFastTransit());
		assertFalse(target.sixtyHertzResolvesFastTransit());
		assertFalse(target.oneTwentyHertzResolvesFastTransit());
		assertFalse(target.twoFortyHertzResolvesFastTransit());
		assertFalse(target.oneKilohertzResolvesFastTransit());
		assertEquals(3.2424460485274076, target.oneKilohertzSamplesPerFastTransit(), 1.0e-12);
	}

	@Test
	void temporalResolutionRejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.targets(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.target(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionAudit audit =
				Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_hover_surface_wake_temporal_resolution_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_temporal_resolution_summary,all_targets,temporal_target_count,128,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_temporal_resolution,racingQuad:ground:hR1.0:rotor0,required_substeps_per_minecraft_tick,62,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_temporal_resolution,racingQuad:ground:hR1.0:rotor0,minecraft_tick_resolves_fast_transit,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_hover_surface_wake_temporal_resolution,racingQuad:ground:hR1.0:rotor0,runtime_coupling_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget findTransit(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeTransit.PoweredHoverSurfaceWakeTransitTarget> targets,
			String presetName,
			String surfaceType,
			double clearanceOverRadius,
			int rotorIndex
	) {
		return targets.stream()
				.filter(target -> presetName.equals(target.presetName())
						&& surfaceType.equals(target.surfaceType())
						&& Math.abs(clearanceOverRadius - target.clearanceOverRadius()) <= 1.0e-12
						&& rotorIndex == target.rotorIndex())
				.findFirst()
				.orElseThrow();
	}

	private static int countResolved(
			List<Aerodynamics4McL2PoweredHoverSurfaceWakeTemporalResolution.PoweredHoverSurfaceWakeTemporalResolutionTarget> targets,
			String rate
	) {
		return (int) targets.stream()
				.filter(target -> switch (rate) {
					case "minecraft" -> target.minecraftTickResolvesFastTransit();
					case "sixty" -> target.sixtyHertzResolvesFastTransit();
					case "oneTwenty" -> target.oneTwentyHertzResolvesFastTransit();
					case "twoForty" -> target.twoFortyHertzResolvesFastTransit();
					case "oneKilohertz" -> target.oneKilohertzResolvesFastTransit();
					default -> false;
				})
				.count();
	}

	private static int requiredSubsteps(double frameRateHertz, double maximumSamplePeriodSeconds) {
		return Math.max(1, (int) Math.ceil((1.0 / frameRateHertz) / maximumSamplePeriodSeconds));
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve(
							"docs/data/a4mc_l2_powered_hover_surface_wake_temporal_resolution_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
