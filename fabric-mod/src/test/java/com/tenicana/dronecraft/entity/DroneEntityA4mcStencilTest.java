package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.integration.Aerodynamics4McAtmosphereBridge;
import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.Vec3;

class DroneEntityA4mcStencilTest {
	@Test
	void affineFourPointStencilRecoversWindDerivativesAndPressureGradient() {
		double radius = 0.18;
		Vec3 centerWind = new Vec3(3.0, -1.0, 2.0);
		Vec3 derivativeX = new Vec3(2.0, -3.0, 4.0);
		Vec3 derivativeZ = new Vec3(-1.0, 0.5, 5.0);
		double centerPressure = 120.0;
		double pressureGradientX = 640.0;
		double pressureGradientZ = -275.0;
		Aerodynamics4McAtmosphereBridge.AtmosphereSample center = sample(
				centerWind,
				centerPressure,
				1.0,
				true,
				true
		);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample positiveX = sample(
				centerWind.add(derivativeX.multiply(radius)),
				centerPressure + pressureGradientX * radius,
				1.0,
				true,
				true
		);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample negativeX = sample(
				centerWind.subtract(derivativeX.multiply(radius)),
				centerPressure - pressureGradientX * radius,
				1.0,
				true,
				true
		);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample positiveZ = sample(
				centerWind.add(derivativeZ.multiply(radius)),
				centerPressure + pressureGradientZ * radius,
				1.0,
				true,
				true
		);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample negativeZ = sample(
				centerWind.subtract(derivativeZ.multiply(radius)),
				centerPressure - pressureGradientZ * radius,
				1.0,
				true,
				true
		);

		Vec3 recoveredX = SimulationFlightRuntime.centralDifference(
				SimulationFlightRuntime.adoptedA4mcStencilWind(centerWind, positiveX),
				SimulationFlightRuntime.adoptedA4mcStencilWind(centerWind, negativeX),
				radius
		);
		Vec3 recoveredZ = SimulationFlightRuntime.centralDifference(
				SimulationFlightRuntime.adoptedA4mcStencilWind(centerWind, positiveZ),
				SimulationFlightRuntime.adoptedA4mcStencilWind(centerWind, negativeZ),
				radius
		);
		Vec3 recoveredPressure = SimulationFlightRuntime.adoptedA4mcStencilPressureGradient(
				center,
				1.0,
				positiveX,
				negativeX,
				positiveZ,
				negativeZ,
				radius
		);

		assertVecEquals(derivativeX, recoveredX);
		assertVecEquals(derivativeZ, recoveredZ);
		assertVecEquals(new Vec3(pressureGradientX, 0.0, pressureGradientZ), recoveredPressure);
	}

	@Test
	void edgeSamplesUseTheirOwnQualityAndFallBackToCenter() {
		Vec3 centerWind = new Vec3(2.0, 0.0, -1.0);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample halfQuality = sample(
				new Vec3(10.0, 4.0, 3.0),
				500.0,
				0.5,
				true,
				true
		);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample unavailable =
				Aerodynamics4McAtmosphereBridge.AtmosphereSample.unavailable();

		assertVecEquals(new Vec3(6.0, 2.0, 1.0), SimulationFlightRuntime.adoptedA4mcStencilWind(centerWind, halfQuality));
		assertEquals(300.0, SimulationFlightRuntime.adoptedA4mcStencilPressure(100.0, halfQuality), 1.0e-12);
		assertEquals(centerWind, SimulationFlightRuntime.adoptedA4mcStencilWind(centerWind, unavailable));
		assertEquals(100.0, SimulationFlightRuntime.adoptedA4mcStencilPressure(100.0, unavailable), 0.0);
	}

	@Test
	void sharedFourPointStencilRefreshesAtTenHertz() {
		SimulationFlightRuntime.A4mcSharedStencilCache cache = new SimulationFlightRuntime.A4mcSharedStencilCache();
		Aerodynamics4McAtmosphereBridge.AtmosphereSample center = sample(Vec3.ZERO, 0.0, 1.0, true, true);
		assertTrue(cache.acceptsCenter(center, 1.0, false));
		int refreshCount = 0;
		int edgeSampleBudget = 0;
		int centerSampleBudget = 0;
		for (long tick = 0L; tick < 20L; tick++) {
			centerSampleBudget++;
			if (cache.shouldRefresh(tick)) {
				refreshCount++;
				edgeSampleBudget += SimulationFlightRuntime.a4mcStencilEdgeSampleCount();
				cache.update(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Quaternion.IDENTITY, tick);
			}
		}

		assertEquals(10, refreshCount);
		assertEquals(20, centerSampleBudget);
		assertEquals(40, edgeSampleBudget);
		assertEquals(60, centerSampleBudget + edgeSampleBudget);
		double labToSharedSampleRatio = 37.0 * 20.0 / (centerSampleBudget + edgeSampleBudget);
		assertEquals(740.0 / 60.0, labToSharedSampleRatio, 1.0e-12);
		assertTrue(labToSharedSampleRatio > 12.3,
				"the shared stencil must cut the sim/lab 37-samples-per-tick budget by at least 12.3x");
		assertFalse(cache.shouldRefresh(19L));
		assertTrue(cache.shouldRefresh(20L));
	}

	@Test
	void overrideUnavailableAndZeroQualityCentersImmediatelyClearTheCache() {
		SimulationFlightRuntime runtime = new SimulationFlightRuntime(DroneConfig.racingQuad());
		SimulationFlightRuntime.A4mcSharedStencilCache cache = new SimulationFlightRuntime.A4mcSharedStencilCache();
		Aerodynamics4McAtmosphereBridge.AtmosphereSample valid = sample(Vec3.ZERO, 0.0, 1.0, true, true);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample zeroQuality = sample(Vec3.ZERO, 0.0, 0.0, true, true);
		Vec3 nonzero = new Vec3(1.0, 2.0, 3.0);

		cache.update(nonzero, nonzero, nonzero, Quaternion.IDENTITY, 12L);
		assertFalse(cache.acceptsCenter(valid, 1.0, true));
		assertCacheClear(cache, runtime);

		cache.update(nonzero, nonzero, nonzero, Quaternion.IDENTITY, 12L);
		assertFalse(cache.acceptsCenter(Aerodynamics4McAtmosphereBridge.AtmosphereSample.unavailable(), 0.0, false));
		assertCacheClear(cache, runtime);

		cache.update(nonzero, nonzero, nonzero, Quaternion.IDENTITY, 12L);
		assertFalse(cache.acceptsCenter(zeroQuality, 0.0, false));
		assertCacheClear(cache, runtime);
	}

	@Test
	void cachedWorldDerivativesReprojectIntoTheCurrentBodyFrameBetweenRefreshes() {
		SimulationFlightRuntime runtime = new SimulationFlightRuntime(DroneConfig.racingQuad());
		SimulationFlightRuntime.A4mcSharedStencilCache cache = new SimulationFlightRuntime.A4mcSharedStencilCache();
		Vec3 derivativeAlongStencilXWorld = new Vec3(1.0, 2.0, 3.0);
		Vec3 derivativeAlongStencilZWorld = new Vec3(-4.0, 5.0, 6.0);
		Vec3 pressureGradientWorld = new Vec3(70.0, 80.0, 90.0);
		cache.update(
				derivativeAlongStencilXWorld,
				derivativeAlongStencilZWorld,
				pressureGradientWorld,
				Quaternion.IDENTITY,
				0L
		);
		Quaternion turned = new Quaternion(Math.cos(Math.PI / 4.0), 0.0, Math.sin(Math.PI / 4.0), 0.0);
		runtime.state().setOrientation(turned);
		Vec3 currentXWorld = turned.rotate(new Vec3(1.0, 0.0, 0.0));
		Vec3 currentZWorld = turned.rotate(new Vec3(0.0, 0.0, 1.0));
		Vec3 expectedDerivativeXWorld = derivativeAlongStencilXWorld.multiply(currentXWorld.x())
				.add(derivativeAlongStencilZWorld.multiply(currentXWorld.z()));
		Vec3 expectedDerivativeZWorld = derivativeAlongStencilXWorld.multiply(currentZWorld.x())
				.add(derivativeAlongStencilZWorld.multiply(currentZWorld.z()));
		Vec3 expectedPressureBody = turned.conjugate().rotate(pressureGradientWorld);

		assertVecEquals(
				turned.conjugate().rotate(expectedDerivativeXWorld),
				cache.adoptedWindDerivativeAlongBodyXPerMeter(runtime)
		);
		assertVecEquals(
				turned.conjugate().rotate(expectedDerivativeZWorld),
				cache.adoptedWindDerivativeAlongBodyZPerMeter(runtime)
		);
		assertVecEquals(
				new Vec3(expectedPressureBody.x(), 0.0, expectedPressureBody.z()),
				cache.adoptedPressureGradientBodyPascalsPerMeter(runtime)
		);
		assertFalse(cache.shouldRefresh(1L), "odd ticks must reproject the cached world tensor without resampling");
	}

	@Test
	void nonLocalOrUntrustedCenterDisablesPressureGradientEvenWithTrustedEdges() {
		double radius = 0.20;
		Aerodynamics4McAtmosphereBridge.AtmosphereSample coarseCenter = sample(
				Vec3.ZERO,
				100.0,
				1.0,
				true,
				false
		);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample untrustedCenter = sample(
				Vec3.ZERO,
				100.0,
				1.0,
				false,
				true
		);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample positive = sample(
				Vec3.ZERO,
				900.0,
				1.0,
				true,
				true
		);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample negative = sample(
				Vec3.ZERO,
				-300.0,
				1.0,
				true,
				true
		);

		assertEquals(
				Vec3.ZERO,
				SimulationFlightRuntime.adoptedA4mcStencilPressureGradient(
						coarseCenter,
						1.0,
						positive,
						negative,
						positive,
						negative,
						radius
				)
		);
		assertEquals(
				Vec3.ZERO,
				SimulationFlightRuntime.adoptedA4mcStencilPressureGradient(
						untrustedCenter,
						0.0,
						positive,
						negative,
						positive,
						negative,
						radius
				)
		);

		SimulationFlightRuntime runtime = new SimulationFlightRuntime(DroneConfig.racingQuad());
		SimulationFlightRuntime.A4mcSharedStencilCache cache = new SimulationFlightRuntime.A4mcSharedStencilCache();
		cache.update(Vec3.ZERO, Vec3.ZERO, new Vec3(600.0, 0.0, -300.0), Quaternion.IDENTITY, 0L);
		assertTrue(cache.acceptsCenter(coarseCenter, 1.0, false),
				"coarse center wind remains usable even though pressure is not");
		assertEquals(Vec3.ZERO, cache.adoptedPressureGradientBodyPascalsPerMeter(runtime),
				"a coarse/non-local center must clear pressure immediately on the odd tick");
		assertFalse(cache.shouldRefresh(1L));
	}

	private static Aerodynamics4McAtmosphereBridge.AtmosphereSample sample(
			Vec3 meanWind,
			double pressureAnomalyPascals,
			double confidence,
			boolean trustedForGameplay,
			boolean localVoxelFlow
	) {
		return new Aerodynamics4McAtmosphereBridge.AtmosphereSample(
				true,
				trustedForGameplay,
				confidence,
				0L,
				true,
				meanWind.x(),
				meanWind.y(),
				meanWind.z(),
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				localVoxelFlow,
				pressureAnomalyPascals,
				false,
				0.0,
				false,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0
		);
	}

	private static void assertCacheClear(
			SimulationFlightRuntime.A4mcSharedStencilCache cache,
			SimulationFlightRuntime runtime
	) {
		assertEquals(Vec3.ZERO, cache.adoptedWindDerivativeAlongBodyXPerMeter(runtime));
		assertEquals(Vec3.ZERO, cache.adoptedWindDerivativeAlongBodyZPerMeter(runtime));
		assertEquals(Vec3.ZERO, cache.adoptedPressureGradientBodyPascalsPerMeter(runtime));
		assertEquals(Long.MIN_VALUE, cache.lastStencilTick());
	}

	private static void assertVecEquals(Vec3 expected, Vec3 actual) {
		assertEquals(expected.x(), actual.x(), 1.0e-12);
		assertEquals(expected.y(), actual.y(), 1.0e-12);
		assertEquals(expected.z(), actual.z(), 1.0e-12);
	}
}
