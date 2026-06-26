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
}
