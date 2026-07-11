package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.integration.Aerodynamics4McAtmosphereBridge;
import com.tenicana.dronecraft.integration.AerodynamicsAtmosphereCoupling;
import com.tenicana.dronecraft.sim.Vec3;

class DroneEntityAtmosphereCouplingTest {
	@Test
	void unavailableOrZeroQualitySourcePreservesFallbackIdentityAndNeutralPrimitives() {
		Vec3 fallback = new Vec3(-0.0, 2.0, 4.0);
		Aerodynamics4McAtmosphereBridge.AtmosphereSample flow = sample(
				true,
				10.0,
				3.0,
				-4.0,
				0.8,
				0.4,
				0.5,
				2.0,
				1.0,
				0.0,
				true,
				1200.0
		);

		assertSame(
				fallback,
				AerodynamicsAtmosphereCoupling.adoptedAtmosphereWind(
						fallback,
						Aerodynamics4McAtmosphereBridge.AtmosphereSample.unavailable(),
						1.0
				)
		);
		assertSame(fallback, AerodynamicsAtmosphereCoupling.adoptedAtmosphereWind(fallback, flow, 0.0));
		assertEquals(0.37, AerodynamicsAtmosphereCoupling.adoptedAtmosphereTurbulence(0.37, flow, 0.0), 0.0);
		assertEquals(0.0, AerodynamicsAtmosphereCoupling.adoptedAtmospherePressureAnomalyPascals(flow, 0.0), 0.0);
		assertEquals(1.0, AerodynamicsAtmosphereCoupling.motorEscVentilationFactor(flow, 0.0), 0.0);
		assertEquals(1.0, AerodynamicsAtmosphereCoupling.batteryVentilationFactor(flow, 0.0), 0.0);
		assertSame(Vec3.ZERO, AerodynamicsAtmosphereCoupling.adoptedAtmosphereGustVelocity(flow, 0.0));
		assertEquals(0.0, AerodynamicsAtmosphereCoupling.adoptedAblStability(flow, 0.0), 0.0);
		assertEquals(0.0, AerodynamicsAtmosphereCoupling.adoptedAblMixingStrength(flow, 0.0), 0.0);
		assertSame(
				Vec3.ZERO,
				AerodynamicsAtmosphereCoupling.adoptedAtmosphereGustVelocity(
						Aerodynamics4McAtmosphereBridge.AtmosphereSample.unavailable(),
						1.0
				)
		);
	}

	@Test
	void ablPrimitivesConsumeQualityExactlyOnce() {
		Aerodynamics4McAtmosphereBridge.AtmosphereSample flow = sample(
				false,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				0.0,
				0.0,
				-0.8,
				0.6
		);

		assertEquals(-0.4, AerodynamicsAtmosphereCoupling.adoptedAblStability(flow, 0.5), 0.0);
		assertEquals(0.3, AerodynamicsAtmosphereCoupling.adoptedAblMixingStrength(flow, 0.5), 0.0);
		assertEquals(-0.8, AerodynamicsAtmosphereCoupling.adoptedAblStability(flow, 2.0), 0.0);
		assertEquals(0.6, AerodynamicsAtmosphereCoupling.adoptedAblMixingStrength(flow, 2.0), 0.0);
		assertEquals(
				0.0,
				AerodynamicsAtmosphereCoupling.adoptedAblStability(
						Aerodynamics4McAtmosphereBridge.AtmosphereSample.unavailable(),
						1.0
				),
				0.0
		);
		assertEquals(
				0.0,
				AerodynamicsAtmosphereCoupling.adoptedAblMixingStrength(
						Aerodynamics4McAtmosphereBridge.AtmosphereSample.unavailable(),
						1.0
				),
				0.0
		);
	}

	@Test
	void meanWindIsQualityBlendedAndExplicitGustStaysOutOfTheBaseWind() {
		Aerodynamics4McAtmosphereBridge.AtmosphereSample flow = sample(
				true,
				10.0,
				2.0,
				-4.0,
				0.0,
				0.0,
				0.0,
				0.0,
				13.0,
				4.0,
				false,
				0.0,
				3.0,
				12.0
		);

		Vec3 adopted = AerodynamicsAtmosphereCoupling.adoptedAtmosphereWind(new Vec3(2.0, 0.0, 0.0), flow, 0.5);
		assertEquals(6.0, adopted.x(), 0.0);
		assertEquals(1.0, adopted.y(), 0.0);
		assertEquals(-2.0, adopted.z(), 0.0);
	}

	@Test
	void coherentGustPreservesAllComponentsAndConsumesQualityExactlyOnce() {
		Aerodynamics4McAtmosphereBridge.AtmosphereSample flow = sample(
				true,
				10.0,
				2.0,
				-4.0,
				0.0,
				0.0,
				0.0,
				0.0,
				13.0,
				4.0,
				false,
				0.0,
				3.0,
				12.0
		);

		Vec3 fullQuality = AerodynamicsAtmosphereCoupling.adoptedAtmosphereGustVelocity(flow, 1.0);
		assertEquals(3.0, fullQuality.x(), 0.0);
		assertEquals(4.0, fullQuality.y(), 0.0);
		assertEquals(12.0, fullQuality.z(), 0.0);

		Vec3 halfQuality = AerodynamicsAtmosphereCoupling.adoptedAtmosphereGustVelocity(flow, 0.5);
		assertEquals(1.5, halfQuality.x(), 0.0);
		assertEquals(2.0, halfQuality.y(), 0.0);
		assertEquals(6.0, halfQuality.z(), 0.0);
		assertSame(Vec3.ZERO, AerodynamicsAtmosphereCoupling.adoptedAtmosphereGustVelocity(flow, 1.0e-12));
	}

	@Test
	void updraftRemovesMeanAndGustOverlapBeforeJoiningTheMeanAirMassFilter() {
		Aerodynamics4McAtmosphereBridge.AtmosphereSample flow = sample(
				true,
				0.0,
				3.0,
				0.0,
				0.0,
				0.0,
				0.0,
				8.0,
				2.0,
				2.0,
				false,
				0.0
		);

		assertEquals(3.0, AerodynamicsAtmosphereCoupling.separatedAtmosphereUpdraftMetersPerSecond(flow), 0.0);
		assertEquals(6.0, AerodynamicsAtmosphereCoupling.adoptedAtmosphereWind(Vec3.ZERO, flow, 1.0).y(), 0.0);
		assertEquals(-5.0, AerodynamicsAtmosphereCoupling.removeOverlappingVerticalFlow(-5.0, 2.0), 0.0);
		assertEquals(0.0, AerodynamicsAtmosphereCoupling.removeOverlappingVerticalFlow(2.0, 3.0), 0.0);
	}

	@Test
	void turbulencePressureAndVentilationConsumeQualityExactlyOnce() {
		Aerodynamics4McAtmosphereBridge.AtmosphereSample flow = sample(
				true,
				4.0,
				0.0,
				0.0,
				0.8,
				0.4,
				0.5,
				0.0,
				2.0,
				0.0,
				true,
				900.0
		);

		assertEquals(0.645, AerodynamicsAtmosphereCoupling.adoptedAtmosphereTurbulence(0.1, flow, 0.5), 1.0e-12);
		assertEquals(450.0, AerodynamicsAtmosphereCoupling.adoptedAtmospherePressureAnomalyPascals(flow, 0.5), 0.0);

		Aerodynamics4McAtmosphereBridge.AtmosphereSample sheltered = sample(
				true,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.8,
				0.0,
				0.0,
				0.0,
				true,
				4500.0
		);
		assertEquals(2250.0, AerodynamicsAtmosphereCoupling.adoptedAtmospherePressureAnomalyPascals(sheltered, 0.5), 0.0);
		assertEquals(0.92, AerodynamicsAtmosphereCoupling.motorEscVentilationFactor(sheltered, 0.5), 1.0e-12);
		assertEquals(0.944, AerodynamicsAtmosphereCoupling.batteryVentilationFactor(sheltered, 0.5), 1.0e-12);

		Aerodynamics4McAtmosphereBridge.AtmosphereSample coarse = sample(
				true,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.8,
				0.0,
				0.0,
				0.0,
				false,
				0.0
		);
		assertEquals(1.0, AerodynamicsAtmosphereCoupling.motorEscVentilationFactor(coarse, 1.0), 0.0);
		assertEquals(1.0, AerodynamicsAtmosphereCoupling.batteryVentilationFactor(coarse, 1.0), 0.0);
	}

	private static Aerodynamics4McAtmosphereBridge.AtmosphereSample sample(
			boolean hasMeanVelocity,
			double meanVelocityX,
			double meanVelocityY,
			double meanVelocityZ,
			double turbulenceIntensity,
			double windShearMagnitudePerBlock,
			double shelterFactor,
			double updraftMetersPerSecond,
			double gustSpeedMetersPerSecond,
			double gustVerticalMetersPerSecond,
			boolean localVoxelFlow,
			double pressureAnomalyPascals
	) {
		return sample(
				hasMeanVelocity,
				meanVelocityX,
				meanVelocityY,
				meanVelocityZ,
				turbulenceIntensity,
				windShearMagnitudePerBlock,
				shelterFactor,
				updraftMetersPerSecond,
				gustSpeedMetersPerSecond,
				gustVerticalMetersPerSecond,
				localVoxelFlow,
				pressureAnomalyPascals,
				0.0,
				0.0
		);
	}

	private static Aerodynamics4McAtmosphereBridge.AtmosphereSample sample(
			boolean hasMeanVelocity,
			double meanVelocityX,
			double meanVelocityY,
			double meanVelocityZ,
			double turbulenceIntensity,
			double windShearMagnitudePerBlock,
			double shelterFactor,
			double updraftMetersPerSecond,
			double gustSpeedMetersPerSecond,
			double gustVerticalMetersPerSecond,
			boolean localVoxelFlow,
			double pressureAnomalyPascals,
			double gustVelocityXMetersPerSecond,
			double gustVelocityZMetersPerSecond
	) {
		return sample(
				hasMeanVelocity,
				meanVelocityX,
				meanVelocityY,
				meanVelocityZ,
				turbulenceIntensity,
				windShearMagnitudePerBlock,
				shelterFactor,
				updraftMetersPerSecond,
				gustSpeedMetersPerSecond,
				gustVerticalMetersPerSecond,
				localVoxelFlow,
				pressureAnomalyPascals,
				gustVelocityXMetersPerSecond,
				gustVelocityZMetersPerSecond,
				0.0,
				0.0
		);
	}

	private static Aerodynamics4McAtmosphereBridge.AtmosphereSample sample(
			boolean hasMeanVelocity,
			double meanVelocityX,
			double meanVelocityY,
			double meanVelocityZ,
			double turbulenceIntensity,
			double windShearMagnitudePerBlock,
			double shelterFactor,
			double updraftMetersPerSecond,
			double gustSpeedMetersPerSecond,
			double gustVerticalMetersPerSecond,
			boolean localVoxelFlow,
			double pressureAnomalyPascals,
			double gustVelocityXMetersPerSecond,
			double gustVelocityZMetersPerSecond,
			double ablStability,
			double ablMixingStrength
	) {
		return new Aerodynamics4McAtmosphereBridge.AtmosphereSample(
				true,
				true,
				1.0,
				-1L,
				hasMeanVelocity,
				meanVelocityX,
				meanVelocityY,
				meanVelocityZ,
				turbulenceIntensity,
				windShearMagnitudePerBlock,
				shelterFactor,
				updraftMetersPerSecond,
				gustSpeedMetersPerSecond,
				gustVerticalMetersPerSecond,
				localVoxelFlow,
				pressureAnomalyPascals,
				false,
				0.0,
				false,
				0.0,
				gustVelocityXMetersPerSecond,
				gustVelocityZMetersPerSecond,
				ablStability,
				ablMixingStrength
		);
	}
}
