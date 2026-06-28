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

class Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolutionTest {
	@Test
	void auditBuildsStableCruiseSkewWakeTemporalResolutionTargets() {
		Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.audit();

		assertEquals("A4MC-L2-Powered-Cruise-Skew-Wake-Temporal-Resolution-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("low-rate Minecraft"));
		assertEquals(7317, audit.packetMetricRowCount());
		assertEquals(6, audit.sourceReferenceCount());
		assertEquals(192, audit.temporalTargetCount());
		assertEquals(38, audit.temporalMetricCount());
		assertEquals(14, audit.summaryMetricRowCount());
		assertEquals(1, audit.methodMetricRowCount());
		assertEquals(192, audit.targets().size());

		for (Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget target
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
			assertTrue(target.centerlineResultantTransitSeconds() > 0.0);
			assertTrue(target.lateralAdjustedTransitSeconds() >= target.centerlineResultantTransitSeconds());
			assertEquals(target.lateralAdjustedTransitSeconds() - target.centerlineResultantTransitSeconds(),
					target.transitBandSeconds(), 1.0e-12);
			assertTrue(target.configuredDynamicInflowTauSeconds() > 0.0);
			assertEquals(target.configuredDynamicInflowTauSeconds() / target.centerlineResultantTransitSeconds(),
					target.tauOverCenterlineTransit(), 1.0e-12);
			assertEquals(target.configuredDynamicInflowTauSeconds() / target.lateralAdjustedTransitSeconds(),
					target.tauOverLateralTransit(), 1.0e-12);
			assertEquals(target.minimumSamplesPerFastTransit() / target.centerlineResultantTransitSeconds(),
					target.recommendedMinSampleRateHertz(), 1.0e-12);
			assertEquals(target.centerlineResultantTransitSeconds() / target.minimumSamplesPerFastTransit(),
					target.recommendedMaxSamplePeriodSeconds(), 1.0e-12);
			assertEquals(4, target.minimumSamplesPerFastTransit());
			assertEquals(20.0, target.minecraftTickRateHertz(), 1.0e-12);
			assertEquals(target.centerlineResultantTransitSeconds() * 20.0,
					target.minecraftTickSamplesPerCenterlineTransit(), 1.0e-12);
			assertEquals(target.minecraftTickSamplesPerCenterlineTransit() >= 4.0,
					target.minecraftTickResolvesCenterlineTransit());
			assertEquals(requiredSubsteps(20.0, target.recommendedMaxSamplePeriodSeconds()),
					target.requiredSubstepsPerMinecraftTick());
			assertEquals(target.centerlineResultantTransitSeconds() * 60.0,
					target.sixtyHertzSamplesPerCenterlineTransit(), 1.0e-12);
			assertEquals(target.sixtyHertzSamplesPerCenterlineTransit() >= 4.0,
					target.sixtyHertzResolvesCenterlineTransit());
			assertEquals(requiredSubsteps(60.0, target.recommendedMaxSamplePeriodSeconds()),
					target.requiredSubstepsPerSixtyHertzFrame());
			assertEquals(target.centerlineResultantTransitSeconds() * 120.0,
					target.oneTwentyHertzSamplesPerCenterlineTransit(), 1.0e-12);
			assertEquals(target.oneTwentyHertzSamplesPerCenterlineTransit() >= 4.0,
					target.oneTwentyHertzResolvesCenterlineTransit());
			assertEquals(requiredSubsteps(120.0, target.recommendedMaxSamplePeriodSeconds()),
					target.requiredSubstepsPerOneTwentyHertzFrame());
			assertEquals(target.centerlineResultantTransitSeconds() * 240.0,
					target.twoFortyHertzSamplesPerCenterlineTransit(), 1.0e-12);
			assertEquals(target.twoFortyHertzSamplesPerCenterlineTransit() >= 4.0,
					target.twoFortyHertzResolvesCenterlineTransit());
			assertEquals(requiredSubsteps(240.0, target.recommendedMaxSamplePeriodSeconds()),
					target.requiredSubstepsPerTwoFortyHertzFrame());
			assertEquals(target.centerlineResultantTransitSeconds() * 1000.0,
					target.oneKilohertzSamplesPerCenterlineTransit(), 1.0e-12);
			assertEquals(target.oneKilohertzSamplesPerCenterlineTransit() >= 4.0,
					target.oneKilohertzResolvesCenterlineTransit());
			assertEquals(requiredSubsteps(1000.0, target.recommendedMaxSamplePeriodSeconds()),
					target.requiredSubstepsPerOneKilohertzFrame());
			assertEquals(target.lateralAdjustedTransitSeconds() * 1000.0,
					target.oneKilohertzSamplesPerLateralAdjustedTransit(), 1.0e-12);
			assertEquals(target.oneKilohertzSamplesPerLateralAdjustedTransit() >= 4.0,
					target.oneKilohertzResolvesLateralAdjustedTransit());
			assertFalse(target.transientProbeApiAvailable());
			assertFalse(target.runtimeCouplingAllowed());
			assertTrue(target.validationBeforeRuntimeRequired());
			assertEquals("target-only-cruise-skew-wake-temporal-resolution-unverified", target.status());
			assertEquals("audit-only-unvalidated-cruise-skew-wake-temporal-resolution", target.runtimeInfo());
		}

		assertEquals(192, audit.extrema().temporalTargetCount());
		assertEquals(16, audit.extrema().sourceTermCount());
		assertEquals(4, audit.extrema().axialPlaneCount());
		assertEquals(3, audit.extrema().sweepColumnCount());
		assertEquals(countResolved(audit.targets(), "minecraft"), audit.extrema().minecraftTickResolvedCount());
		assertEquals(countResolved(audit.targets(), "sixty"), audit.extrema().sixtyHertzResolvedCount());
		assertEquals(countResolved(audit.targets(), "oneTwenty"), audit.extrema().oneTwentyHertzResolvedCount());
		assertEquals(countResolved(audit.targets(), "twoForty"), audit.extrema().twoFortyHertzResolvedCount());
		assertEquals(countResolved(audit.targets(), "oneKilohertz"), audit.extrema().oneKilohertzResolvedCount());
		assertEquals(0, audit.extrema().minecraftTickResolvedCount());
		assertEquals(0, audit.extrema().sixtyHertzResolvedCount());
		assertEquals(0, audit.extrema().runtimeCouplingAllowedCount());
		assertEquals(audit.targets().stream()
						.mapToDouble(Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget::recommendedMinSampleRateHertz)
						.max().orElseThrow(),
				audit.extrema().maxRecommendedMinSampleRateHertz(), 1.0e-12);
		assertEquals(audit.targets().stream()
						.mapToInt(Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget::requiredSubstepsPerMinecraftTick)
						.max().orElseThrow(),
				audit.extrema().maxRequiredSubstepsPerMinecraftTick());
	}

	@Test
	void temporalResolutionTargetMatchesCruiseSkewWakeTransitTarget() {
		Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget transit =
				findTransit(Aerodynamics4McL2PoweredCruiseSkewWakeTransit.audit().targets(),
						"racingQuad", 4, 0, 0);
		Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget target =
				Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.target(transit);

		assertEquals(transit.centerlineResultantTransitSeconds(),
				target.centerlineResultantTransitSeconds(), 1.0e-12);
		assertEquals(transit.lateralAdjustedTransitSeconds(), target.lateralAdjustedTransitSeconds(), 1.0e-12);
		assertEquals(transit.recommendedMaxSamplePeriodSeconds(),
				target.recommendedMaxSamplePeriodSeconds(), 1.0e-12);
		assertEquals(transit.recommendedMinSampleRateHertz(),
				target.recommendedMinSampleRateHertz(), 1.0e-12);
		assertEquals(transit.tauOverCenterlineTransit(), target.tauOverCenterlineTransit(), 1.0e-12);
		assertEquals(requiredSubsteps(20.0, target.recommendedMaxSamplePeriodSeconds()),
				target.requiredSubstepsPerMinecraftTick());
		assertEquals(requiredSubsteps(1000.0, target.recommendedMaxSamplePeriodSeconds()),
				target.requiredSubstepsPerOneKilohertzFrame());
		assertFalse(target.minecraftTickResolvesCenterlineTransit());
		assertFalse(target.sixtyHertzResolvesCenterlineTransit());
		assertFalse(target.oneTwentyHertzResolvesCenterlineTransit());
		assertEquals(target.oneKilohertzSamplesPerCenterlineTransit() >= 4.0,
				target.oneKilohertzResolvesCenterlineTransit());
		assertEquals(0.0, target.transitBandSeconds(), 1.0e-12);
		assertEquals(target.oneKilohertzSamplesPerCenterlineTransit(),
				target.oneKilohertzSamplesPerLateralAdjustedTransit(), 1.0e-12);
	}

	@Test
	void temporalResolutionRejectsInvalidInputs() {
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.targets(null));
		assertThrows(IllegalArgumentException.class,
				() -> Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.target(null));
	}

	@Test
	void csvPacketRowCountMatchesAuditSummary() throws IOException {
		Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionAudit audit =
				Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.audit();
		Path packet = findRepoRoot()
				.resolve("docs/data/a4mc_l2_powered_cruise_skew_wake_temporal_resolution_packet.csv");
		List<String> lines = Files.readAllLines(packet);

		assertEquals(audit.packetMetricRowCount() + 1, lines.size());
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_temporal_resolution_summary,all_targets,temporal_target_count,192,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_temporal_resolution,racingQuad:plane4:sweep0:rotor0,required_substeps_per_minecraft_tick,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_temporal_resolution,racingQuad:plane4:sweep0:rotor0,minecraft_tick_resolves_centerline_transit,false,")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("a4mc_l2_powered_cruise_skew_wake_temporal_resolution,racingQuad:plane4:sweep0:rotor0,runtime_coupling_allowed,false,")));
	}

	private static Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget findTransit(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeTransit.PoweredCruiseSkewWakeTransitTarget> targets,
			String presetName,
			int axialPlaneIndex,
			int sweepColumnIndex,
			int rotorIndex
	) {
		return targets.stream()
				.filter(target -> presetName.equals(target.presetName())
						&& axialPlaneIndex == target.axialPlaneIndex()
						&& sweepColumnIndex == target.sweepColumnIndex()
						&& rotorIndex == target.rotorIndex())
				.findFirst()
				.orElseThrow();
	}

	private static int countResolved(
			List<Aerodynamics4McL2PoweredCruiseSkewWakeTemporalResolution.PoweredCruiseSkewWakeTemporalResolutionTarget> targets,
			String rate
	) {
		return (int) targets.stream()
				.filter(target -> switch (rate) {
					case "minecraft" -> target.minecraftTickResolvesCenterlineTransit();
					case "sixty" -> target.sixtyHertzResolvesCenterlineTransit();
					case "oneTwenty" -> target.oneTwentyHertzResolvesCenterlineTransit();
					case "twoForty" -> target.twoFortyHertzResolvesCenterlineTransit();
					case "oneKilohertz" -> target.oneKilohertzResolvesCenterlineTransit();
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
							"docs/data/a4mc_l2_powered_cruise_skew_wake_temporal_resolution_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}
}
