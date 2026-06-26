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
				Vec3.ZERO,
				Vec3.ZERO,
				0.0,
				0.0,
				0.75,
				0.0,
				false,
				0.0,
				false,
				0.0,
				0.80,
				true,
				true
		);

		assertEquals(0.456, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(sample), 1.0e-9);
	}
}
