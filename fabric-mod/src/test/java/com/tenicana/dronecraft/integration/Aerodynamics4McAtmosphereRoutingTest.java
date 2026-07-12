package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class Aerodynamics4McAtmosphereRoutingTest {
	@Test
	void entityRoutesOneBodyCenterAndOneSharedFourPointStencilToBothEnvironmentPaths() throws IOException {
		String source = Files.readString(droneEntitySource(), StandardCharsets.UTF_8);
		String runtimeSource = Files.readString(simulationFlightRuntimeSource(), StandardCharsets.UTF_8);
		String advanced = between(source, "private DroneEnvironment sampleEnvironment()", "private DroneEnvironment sampleActiveEnvironment");
		String stageOne = between(source, "private DroneEnvironment samplePlayableStageOneEnvironment()", "private Aerodynamics4McAtmosphereBridge.AtmosphereSample sampleAerodynamicsAtmosphere");
		String bridge = between(source, "private Aerodynamics4McAtmosphereBridge.AtmosphereSample sampleAerodynamicsAtmosphere", "private static double atmosphereSourceQuality");
		String centerSampling = between(
				source,
				"private Aerodynamics4McAtmosphereBridge.AtmosphereSample sampleAerodynamicsAtmosphere()",
				"private static Aerodynamics4McAtmosphereBridge.AtmosphereSample sampleAerodynamicsAtmosphereAt"
		);
		String stencil = between(source, "private void updateA4mcSharedStencil", "private static double atmosphereSourceQuality");
		String cacheAndMath = between(
				runtimeSource,
				"static final class A4mcSharedStencilCache",
				"SimulationFlightRuntime(DroneConfig config)"
		);
		String localVoxelReduction = between(
				runtimeSource,
				"static double compactLocalStaticPressureExposure",
				"static final class A4mcSharedStencilCache"
		);
		String pressureCenterReduction = between(
				runtimeSource,
				"Vec3 compactLocalPressureCenterOffsetBodyMeters",
				"Vec3 bodyXWorldDirection()"
		);
		String rotorSampling = between(source, "private PrecipitationWetness samplePrecipitationWetness", "private DroneWakeAirflow sampleDroneWakeAirflow");

		assertEquals(1, occurrences(advanced, "sampleAerodynamicsAtmosphere()"));
		assertEquals(1, occurrences(stageOne, "sampleAerodynamicsAtmosphere()"));
		assertEquals(1, occurrences(bridge, "Aerodynamics4McAtmosphereBridge.sampleGameplay"));
		assertEquals(1, occurrences(source, "Aerodynamics4McAtmosphereBridge.sampleGameplay"),
				"all center and stencil probes must share one cached bridge call site");
		assertEquals(6, occurrences(source, "sampleAerodynamicsAtmosphereAt("),
				"only one helper declaration, one center probe, and four shared edge probes are allowed");
		assertEquals(1, occurrences(centerSampling, "sampleAerodynamicsAtmosphereAt("),
				"the body center must still be sampled on every environment frame");
		assertEquals(4, occurrences(stencil, "sampleAerodynamicsAtmosphereAt("),
				"the compact stencil must remain exactly +/-body-X and +/-body-Z");
		int explicitOverride = bridge.indexOf("if (environmentOverride.windEnabled())");
		int unavailableReturn = bridge.indexOf("return unavailable;", explicitOverride);
		int gameplaySample = bridge.indexOf("Aerodynamics4McAtmosphereBridge.sampleGameplay");
		assertTrue(explicitOverride >= 0
					&& unavailableReturn > explicitOverride
					&& unavailableReturn < gameplaySample,
				"an explicit wind override must return an unavailable sample before any A4MC adoption");
		assertTrue(centerSampling.contains("a4mcSharedStencil.acceptsCenter(unavailable, 0.0, true)"),
				"an explicit wind override must synchronously clear the shared stencil cache");
		assertTrue(stencil.contains("a4mcSharedStencil.shouldRefresh(currentTick)"));
		assertTrue(stencil.contains("ROTOR_DISK_SURFACE_SAMPLE_RADIUS_SCALE"));
		assertTrue(stencil.contains("representativeRotorRadiusMeters()"));
		assertFalse(stencil.contains("for ("), "the shared stencil must not grow into a per-rotor sampling loop");
		assertFalse(stencil.contains("new Aerodynamics4McAtmosphereBridge.AtmosphereSample["));
		assertFalse(stencil.contains("new Vec3["));
		assertFalse(stencil.contains("record "));
		assertFalse(stencil.contains("Map<"));
		assertTrue(cacheAndMath.contains("windDerivativeAlongStencilXWorldPerMeter"));
		assertTrue(cacheAndMath.contains("windDerivativeAlongStencilZWorldPerMeter"));
		assertTrue(cacheAndMath.contains("stencilOrientation"));
		assertTrue(cacheAndMath.contains("pressureGradientWorldPascalsPerMeter = Vec3.ZERO"),
				"a newly coarse center must clear cached pressure without waiting for the next stencil tick");
		assertTrue(cacheAndMath.contains("runtime.worldVectorToBody"),
				"cached world derivatives must be reprojected through the current body attitude");
		assertFalse(cacheAndMath.contains("new Aerodynamics4McAtmosphereBridge.AtmosphereSample["));
		assertFalse(cacheAndMath.contains("new Vec3["));
		assertFalse(cacheAndMath.contains("record "));
		assertFalse(cacheAndMath.contains("Map<"));
		assertTrue(localVoxelReduction.contains("compactLocalVoxelMeanObstruction"));
		assertFalse(localVoxelReduction.contains("new double["));
		assertFalse(localVoxelReduction.contains("new Vec3["));
		assertFalse(localVoxelReduction.contains("record "));
		assertFalse(localVoxelReduction.contains("Map<"));
		assertFalse(localVoxelReduction.contains("Aerodynamics4McAtmosphereBridge.sampleGameplay"));
		assertTrue(pressureCenterReduction.contains("LOCAL_PRESSURE_CENTER_OBSTRUCTION_WEIGHT"));
		assertTrue(pressureCenterReduction.contains("LOCAL_PRESSURE_CENTER_PRESSURE_WEIGHT"));
		assertFalse(pressureCenterReduction.contains("new double["),
				"compact pressure-center reduction must not allocate per-rotor scratch arrays");
		assertFalse(pressureCenterReduction.contains("new Vec3["));
		assertFalse(pressureCenterReduction.contains("record "));
		assertFalse(pressureCenterReduction.contains("Map<"));
		assertFalse(pressureCenterReduction.contains("Aerodynamics4McAtmosphereBridge.sampleGameplay"));
		assertFalse(rotorSampling.contains("Aerodynamics4McAtmosphereBridge"),
				"the shared stencil must not expand into per-rotor probes");
		assertFalse(rotorSampling.contains("sampleAerodynamicsAtmosphereAt("),
				"a per-rotor environment loop must never call the shared A4MC sampling helper");
		assertEquals(0, occurrences(rotorSampling, "sampleAerodynamicsAtmosphere()"),
				"rotor environment sampling must reuse compact body primitives instead of sampling A4MC");

		assertTrue(advanced.contains("adoptedAtmosphereWind("));
		assertTrue(advanced.contains("weatherWindMetersPerSecond()"),
				"the advanced path must blend A4MC mean wind with Minecraft weather wind");
		assertTrue(stageOne.contains("adoptedAtmosphereWind("));
		assertTrue(stageOne.contains("Vec3.ZERO"),
				"the stage-one path must blend A4MC mean wind from the legacy zero-wind baseline");
		assertTrue(advanced.contains("adoptedAtmosphereTurbulence("));
		assertTrue(stageOne.contains("adoptedAtmosphereTurbulence("));
		assertTrue(advanced.contains("adoptedAtmospherePressureAnomalyPascals(externalAtmosphere, sourceQuality)"));
		assertTrue(stageOne.contains("adoptedAtmospherePressureAnomalyPascals(externalAtmosphere, sourceQuality)"));
		assertTrue(advanced.contains("motorEscVentilationFactor(externalAtmosphere, sourceQuality)"));
		assertTrue(stageOne.contains("motorEscVentilationFactor(externalAtmosphere, sourceQuality)"));
		assertTrue(advanced.contains("batteryVentilationFactor(externalAtmosphere, sourceQuality)"));
		assertTrue(stageOne.contains("batteryVentilationFactor(externalAtmosphere, sourceQuality)"));
		assertTrue(advanced.contains("adoptedAtmosphereGustVelocity(externalAtmosphere, sourceQuality)"));
		assertTrue(stageOne.contains("adoptedAtmosphereGustVelocity(externalAtmosphere, sourceQuality)"));
		assertTrue(advanced.contains("adoptedAblStability(externalAtmosphere, sourceQuality)"));
		assertTrue(stageOne.contains("adoptedAblStability(externalAtmosphere, sourceQuality)"));
		assertTrue(advanced.contains("adoptedAblMixingStrength(externalAtmosphere, sourceQuality)"));
		assertTrue(stageOne.contains("adoptedAblMixingStrength(externalAtmosphere, sourceQuality)"));
		assertTrue(advanced.contains("adoptedWindShearMagnitudePerBlock(externalAtmosphere, sourceQuality)"));
		assertTrue(stageOne.contains("adoptedWindShearMagnitudePerBlock(externalAtmosphere, sourceQuality)"));
		assertTrue(advanced.contains("adoptedShelterFactor(externalAtmosphere, sourceQuality)"));
		assertTrue(stageOne.contains("adoptedShelterFactor(externalAtmosphere, sourceQuality)"));
		assertTrue(advanced.contains("adoptedUpdraftMetersPerSecond(externalAtmosphere, sourceQuality)"));
		assertTrue(stageOne.contains("adoptedUpdraftMetersPerSecond(externalAtmosphere, sourceQuality)"));
		assertTrue(advanced.contains("adoptedUpdraftLocalVoxelGain(externalAtmosphere, sourceQuality)"));
		assertTrue(stageOne.contains("adoptedUpdraftLocalVoxelGain(externalAtmosphere, sourceQuality)"));
		assertFalse(advanced.contains("externalAtmosphere.ablStability()"));
		assertFalse(stageOne.contains("externalAtmosphere.ablStability()"));
		assertFalse(advanced.contains("externalAtmosphere.ablMixingStrength()"));
		assertFalse(stageOne.contains("externalAtmosphere.ablMixingStrength()"));
		assertTrue(stageOne.contains("effectiveAmbientTemperature"), "the default playable path must receive adopted source temperature");
		assertTrue(stageOne.contains("adoptedSourceHumidity"), "the default playable path must receive quality-gated source humidity");
		assertTrue(advanced.contains("a4mcSharedStencil.adoptedWindDerivativeAlongBodyXPerMeter(simulationRuntime)"));
		assertTrue(advanced.contains("a4mcSharedStencil.adoptedWindDerivativeAlongBodyZPerMeter(simulationRuntime)"));
		assertTrue(advanced.contains("a4mcSharedStencil.adoptedPressureGradientBodyPascalsPerMeter(simulationRuntime)"));
		assertTrue(stageOne.contains("a4mcSharedStencil.adoptedWindDerivativeAlongBodyXPerMeter(simulationRuntime)"));
		assertTrue(stageOne.contains("a4mcSharedStencil.adoptedWindDerivativeAlongBodyZPerMeter(simulationRuntime)"));
		assertTrue(stageOne.contains("a4mcSharedStencil.adoptedPressureGradientBodyPascalsPerMeter(simulationRuntime)"));
		assertTrue(advanced.contains("compactLocalPressureCenterOffsetBodyMeters("));
		assertTrue(advanced.contains("rotorEffects.flowObstructions()"));
		assertTrue(advanced.contains("rotorEffects.flowObstructionWallForceFactors()"));
		assertTrue(stageOne.contains("compactLocalPressureCenterOffsetBodyMeters("));
		assertTrue(stageOne.contains("externalAtmosphere.localVoxelFlow() ? sourceQuality : 0.0"));
		assertTrue(advanced.contains("localPressureCenterOffset"));
		assertTrue(stageOne.contains("localPressureCenterOffset"));
		assertTrue(advanced.contains("compactLocalStaticPressureExposure("));
		assertTrue(stageOne.contains("compactLocalStaticPressureExposure("));
		assertTrue(advanced.contains("compactLocalVoxelVentilationMultiplier("));
		assertTrue(stageOne.contains("compactLocalVoxelVentilationMultiplier("));
		assertTrue(advanced.contains("localStaticPressureExposure"));
		assertTrue(stageOne.contains("localStaticPressureExposure"));
		assertEquals(1, occurrences(advanced, "compactLocalVoxelMeanObstruction("),
				"advanced local-voxel coupling must reduce rotor obstruction only once per frame");
		assertEquals(0, occurrences(stageOne, "compactLocalVoxelMeanObstruction("),
				"stage one has no rotor obstruction arrays to reduce");
	}

	@Test
	void aerodynamics4McRemainsAnOptionalSuggestedMod() throws IOException {
		String manifest = Files.readString(fabricManifest(), StandardCharsets.UTF_8);

		assertTrue(manifest.contains("\"suggests\""));
		assertTrue(manifest.contains("\"aerodynamics4mc\": \"*\""));
		String depends = between(manifest, "\"depends\"", "\"suggests\"");
		assertFalse(depends.contains("aerodynamics4mc"), "A4MC must not become a hard dependency");
	}

	private static int occurrences(String text, String needle) {
		int count = 0;
		for (int index = 0; (index = text.indexOf(needle, index)) >= 0; index += needle.length()) {
			count++;
		}
		return count;
	}

	private static String between(String text, String start, String end) {
		int startIndex = text.indexOf(start);
		int endIndex = text.indexOf(end, startIndex + start.length());
		if (startIndex < 0 || endIndex < 0) {
			fail("Cannot locate source range " + start + " .. " + end);
		}
		return text.substring(startIndex, endIndex);
	}

	private static Path droneEntitySource() {
		return locate("fabric-mod/src/main/java/com/tenicana/dronecraft/entity/DroneEntity.java");
	}

	private static Path simulationFlightRuntimeSource() {
		return locate("fabric-mod/src/main/java/com/tenicana/dronecraft/entity/SimulationFlightRuntime.java");
	}

	private static Path fabricManifest() {
		return locate("fabric-mod/src/main/resources/fabric.mod.json");
	}

	private static Path locate(String relativePath) {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			Path candidate = current.resolve(relativePath);
			if (Files.exists(candidate)) {
				return candidate;
			}
			current = current.getParent();
		}
		fail("Cannot locate " + relativePath);
		return Path.of(".");
	}
}
