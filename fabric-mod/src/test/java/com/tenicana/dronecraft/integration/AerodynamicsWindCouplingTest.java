package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.Vec3;

class AerodynamicsWindCouplingTest {
	@Test
	void unavailableOrCoarseWindKeepsLocalObstructionModel() {
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(null), 1.0e-9);
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				true,
				false,
				1.0,
				1.0
		), 1.0e-9);
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				false,
				true,
				1.0,
				1.0
		), 1.0e-9);
	}

	@Test
	void trustedLocalVoxelFlowLeavesBoundedResidualObstruction() {
		assertEquals(0.68, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				true,
				true,
				1.0,
				0.0
		), 1.0e-9);
		assertEquals(0.28, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				true,
				true,
				1.0,
				1.0
		), 1.0e-9);
		assertEquals(0.72, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				true,
				true,
				0.5,
				0.5
		), 1.0e-9);
	}

	@Test
	void sourceQualityUsesConfidenceAndFreshnessAge() {
		assertEquals(1.0, AerodynamicsWindCoupling.sourceQualityFactor(
				true,
				true,
				1.0,
				-1L
		), 1.0e-9);
		assertEquals(0.50, AerodynamicsWindCoupling.sourceQualityFactor(
				true,
				true,
				0.50,
				20L
		), 1.0e-9);
		assertEquals(0.50, AerodynamicsWindCoupling.sourceQualityFactor(
				true,
				true,
				1.0,
				100L
		), 1.0e-9);
		assertEquals(0.0, AerodynamicsWindCoupling.sourceQualityFactor(
				true,
				true,
				1.0,
				160L
		), 1.0e-9);
	}

	@Test
	void staleLocalVoxelFlowRestoresLocalObstacleModel() {
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				true,
				true,
				1.0,
				1.0,
				160L
		), 1.0e-9);
		assertEquals(0.84, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				true,
				true,
				1.0,
				0.0,
				100L
		), 1.0e-9);
	}

	@Test
	void windSampleAdapterUsesSanitizedA4mcTelemetry() {
		Aerodynamics4McWindBridge.WindSample sample = new Aerodynamics4McWindBridge.WindSample(
				true,
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(1.0, 2.0, 0.0),
				0.0,
				0.0,
				0.75,
				0.0,
				false,
				0.0,
				false,
				0.0,
				0.80,
				-1250.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				3L,
				0.0,
				0.0
		);

		assertEquals(0.456, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(sample), 1.0e-9);
		assertEquals(2.0, sample.gustSpeedMetersPerSecond(), 1.0e-9);
	}

	@Test
	void a4mcGustAddsBoundedNaturalTurbulenceEnergy() {
		Aerodynamics4McWindBridge.WindSample sample = new Aerodynamics4McWindBridge.WindSample(
				true,
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(1.0, 2.0, 0.0),
				0.0,
				0.0,
				0.75,
				0.0,
				false,
				0.0,
				false,
				0.0,
				1.0,
				0.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				3L,
				0.0,
				0.0
		);

		assertEquals(0.10, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, null), 1.0e-9);
		assertEquals(0.38, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, sample), 1.0e-9);
	}

	@Test
	void a4mcGustTurbulenceBoostIsCapped() {
		Aerodynamics4McWindBridge.WindSample sample = new Aerodynamics4McWindBridge.WindSample(
				true,
				Vec3.ZERO,
				new Vec3(30.0, 0.0, 0.0),
				0.0,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				false,
				0.0,
				1.0,
				0.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				3L,
				0.0,
				0.0
		);

		assertEquals(1.5, AerodynamicsWindCoupling.naturalTurbulenceIntensity(1.45, sample), 1.0e-9);
	}

	@Test
	void staleA4mcSourceDoesNotDriveNaturalTurbulence() {
		Aerodynamics4McWindBridge.WindSample sample = new Aerodynamics4McWindBridge.WindSample(
				true,
				Vec3.ZERO,
				new Vec3(4.0, 0.0, 0.0),
				1.2,
				2.0,
				1.0,
				6.0,
				false,
				0.0,
				false,
				0.0,
				1.0,
				0.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				200L,
				0.0,
				0.0
		);

		assertEquals(0.12, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.12, sample), 1.0e-9);
	}

	@Test
	void sourceWeightedWindFadesTowardFallbackAsQualityDrops() {
		Vec3 fallback = new Vec3(0.0, 0.0, 2.0);
		Aerodynamics4McWindBridge.WindSample fresh = windSampleWithEffectiveWind(new Vec3(4.0, 0.0, 0.0), 20L);
		Aerodynamics4McWindBridge.WindSample halfStale = windSampleWithEffectiveWind(new Vec3(4.0, 0.0, 0.0), 100L);
		Aerodynamics4McWindBridge.WindSample stale = windSampleWithEffectiveWind(new Vec3(4.0, 0.0, 0.0), 200L);

		assertEquals(new Vec3(4.0, 0.0, 0.0), AerodynamicsWindCoupling.sourceWeightedWind(fallback, fresh));
		assertEquals(new Vec3(2.0, 0.0, 1.0), AerodynamicsWindCoupling.sourceWeightedWind(fallback, halfStale));
		assertEquals(fallback, AerodynamicsWindCoupling.sourceWeightedWind(fallback, stale));
		assertEquals(fallback, AerodynamicsWindCoupling.sourceWeightedWind(fallback, null));
	}

	@Test
	void rotorDiskWindBlendKeepsMissingEdgeWeightsConservative() {
		AerodynamicsWindCoupling.RotorDiskWindBlend blend = AerodynamicsWindCoupling.rotorDiskWindBlend(
				Vec3.ZERO,
				new Vec3(0.0, 1.0, 0.0),
				new Vec3[] {
						new Vec3(0.0, 4.0, 0.0),
						null
				},
				new Vec3[] {
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(-1.0, 0.0, 0.0)
				},
				new double[] { 1.0, 1.0 },
				1.0
		);

		assertEquals(0.0, blend.meanWindWorldMetersPerSecond().x(), 1.0e-9);
		assertEquals(4.0 / 3.0, blend.meanWindWorldMetersPerSecond().y(), 1.0e-9);
		assertEquals(0.0, blend.meanWindWorldMetersPerSecond().z(), 1.0e-9);
		assertEquals(new Vec3(2.0, 0.0, 0.0), blend.gradientBodyMetersPerSecond());
	}

	@Test
	void rotorDiskWindBlendFallsBackToCenterWhenEdgesAreMissing() {
		Vec3 centerWind = new Vec3(2.0, 0.5, -1.0);
		AerodynamicsWindCoupling.RotorDiskWindBlend blend = AerodynamicsWindCoupling.rotorDiskWindBlend(
				centerWind,
				new Vec3(0.0, 3.0, 0.0),
				new Vec3[] { null, new Vec3(Double.NaN, 1.0, 0.0) },
				new Vec3[] {
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(-1.0, 0.0, 0.0)
				},
				new double[] { 0.25, 0.25 },
				0.5
		);

		assertEquals(centerWind, blend.meanWindWorldMetersPerSecond());
		assertEquals(Vec3.ZERO, blend.gradientBodyMetersPerSecond());
	}

	@Test
	void rotorDiskWindBlendFadesStaleEdgeSamplesTowardCenterWind() {
		Vec3 centerWind = Vec3.ZERO;
		Vec3 rotorAxis = new Vec3(0.0, 1.0, 0.0);
		Vec3[] directions = {
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(-1.0, 0.0, 0.0)
		};
		double[] weights = { 1.0, 1.0 };

		AerodynamicsWindCoupling.RotorDiskWindBlend freshEdges = AerodynamicsWindCoupling.rotorDiskWindBlend(
				centerWind,
				rotorAxis,
				new Aerodynamics4McWindBridge.WindSample[] {
						windSampleWithEffectiveWind(new Vec3(0.0, 4.0, 0.0), 20L),
						windSampleWithEffectiveWind(new Vec3(0.0, -4.0, 0.0), 20L)
				},
				directions,
				weights,
				1.0
		);
		AerodynamicsWindCoupling.RotorDiskWindBlend halfStaleEdge = AerodynamicsWindCoupling.rotorDiskWindBlend(
				centerWind,
				rotorAxis,
				new Aerodynamics4McWindBridge.WindSample[] {
						windSampleWithEffectiveWind(new Vec3(0.0, 4.0, 0.0), 20L),
						windSampleWithEffectiveWind(new Vec3(0.0, -4.0, 0.0), 100L)
				},
				directions,
				weights,
				1.0
		);
		AerodynamicsWindCoupling.RotorDiskWindBlend staleEdge = AerodynamicsWindCoupling.rotorDiskWindBlend(
				centerWind,
				rotorAxis,
				new Aerodynamics4McWindBridge.WindSample[] {
						windSampleWithEffectiveWind(new Vec3(0.0, 4.0, 0.0), 20L),
						windSampleWithEffectiveWind(new Vec3(0.0, -4.0, 0.0), 200L)
				},
				directions,
				weights,
				1.0
		);

		assertEquals(new Vec3(4.0, 0.0, 0.0), freshEdges.gradientBodyMetersPerSecond());
		assertEquals(new Vec3(3.0, 0.0, 0.0), halfStaleEdge.gradientBodyMetersPerSecond());
		assertEquals(new Vec3(2.0, 0.0, 0.0), staleEdge.gradientBodyMetersPerSecond());
	}

	@Test
	void sourceWeightedAtmosphereScalarsUseSourceQuality() {
		Aerodynamics4McWindBridge.WindSample sample = windSampleWithAtmosphere(100L);

		assertEquals(500.0, AerodynamicsWindCoupling.sourceWeightedPressureAnomalyPascals(sample), 1.0e-9);
		assertEquals(30.0, AerodynamicsWindCoupling.sourceWeightedTemperatureCelsius(20.0, sample), 1.0e-9);
		assertEquals(0.4, AerodynamicsWindCoupling.sourceWeightedHumidity(sample), 1.0e-9);
	}

	@Test
	void sourceWeightedWindSpeedsUseSourceQuality() {
		Aerodynamics4McWindBridge.WindSample fresh = windSampleWithMeanAndEffectiveWind(
				new Vec3(3.0, 0.0, 4.0),
				new Vec3(6.0, 0.0, 8.0),
				20L
		);
		Aerodynamics4McWindBridge.WindSample halfStale = windSampleWithMeanAndEffectiveWind(
				new Vec3(3.0, 0.0, 4.0),
				new Vec3(6.0, 0.0, 8.0),
				100L
		);
		Aerodynamics4McWindBridge.WindSample stale = windSampleWithMeanAndEffectiveWind(
				new Vec3(3.0, 0.0, 4.0),
				new Vec3(6.0, 0.0, 8.0),
				200L
		);

		assertEquals(5.0, AerodynamicsWindCoupling.sourceWeightedMeanSpeedMetersPerSecond(fresh), 1.0e-9);
		assertEquals(10.0, AerodynamicsWindCoupling.sourceWeightedEffectiveSpeedMetersPerSecond(fresh), 1.0e-9);
		assertEquals(5.0, AerodynamicsWindCoupling.sourceWeightedGustSpeedMetersPerSecond(fresh), 1.0e-9);
		assertEquals(2.5, AerodynamicsWindCoupling.sourceWeightedMeanSpeedMetersPerSecond(halfStale), 1.0e-9);
		assertEquals(5.0, AerodynamicsWindCoupling.sourceWeightedEffectiveSpeedMetersPerSecond(halfStale), 1.0e-9);
		assertEquals(2.5, AerodynamicsWindCoupling.sourceWeightedGustSpeedMetersPerSecond(halfStale), 1.0e-9);
		assertEquals(0.0, AerodynamicsWindCoupling.sourceWeightedMeanSpeedMetersPerSecond(stale), 1.0e-9);
		assertEquals(0.0, AerodynamicsWindCoupling.sourceWeightedEffectiveSpeedMetersPerSecond(stale), 1.0e-9);
		assertEquals(0.0, AerodynamicsWindCoupling.sourceWeightedGustSpeedMetersPerSecond(stale), 1.0e-9);
	}

	@Test
	void staleAtmosphereScalarsFallBack() {
		Aerodynamics4McWindBridge.WindSample sample = windSampleWithAtmosphere(200L);

		assertEquals(0.0, AerodynamicsWindCoupling.sourceWeightedPressureAnomalyPascals(sample), 1.0e-9);
		assertEquals(20.0, AerodynamicsWindCoupling.sourceWeightedTemperatureCelsius(20.0, sample), 1.0e-9);
		assertEquals(0.0, AerodynamicsWindCoupling.sourceWeightedHumidity(sample), 1.0e-9);
	}

	private static Aerodynamics4McWindBridge.WindSample windSampleWithEffectiveWind(Vec3 effectiveWind, long freshnessAgeTicks) {
		return windSampleWithMeanAndEffectiveWind(Vec3.ZERO, effectiveWind, freshnessAgeTicks);
	}

	private static Aerodynamics4McWindBridge.WindSample windSampleWithMeanAndEffectiveWind(
			Vec3 meanWind,
			Vec3 effectiveWind,
			long freshnessAgeTicks
	) {
		return new Aerodynamics4McWindBridge.WindSample(
				true,
				meanWind,
				effectiveWind,
				0.0,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				false,
				0.0,
				1.0,
				0.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				freshnessAgeTicks,
				0.0,
				0.0
		);
	}

	private static Aerodynamics4McWindBridge.WindSample windSampleWithAtmosphere(long freshnessAgeTicks) {
		return new Aerodynamics4McWindBridge.WindSample(
				true,
				Vec3.ZERO,
				new Vec3(4.0, 0.0, 0.0),
				0.0,
				0.0,
				0.0,
				0.0,
				true,
				40.0,
				true,
				0.8,
				1.0,
				1000.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				freshnessAgeTicks,
				0.0,
				0.0
		);
	}
}
