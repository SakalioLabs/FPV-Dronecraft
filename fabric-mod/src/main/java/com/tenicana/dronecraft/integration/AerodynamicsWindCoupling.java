package com.tenicana.dronecraft.integration;

import com.tenicana.dronecraft.sim.MathUtil;

public final class AerodynamicsWindCoupling {
	private static final double LOCAL_VOXEL_BASE_COVERAGE = 0.32;
	private static final double LOCAL_VOXEL_SHELTER_COVERAGE = 0.48;
	private static final double LOCAL_VOXEL_MIN_OBSTRUCTION_RESIDUAL = 0.28;

	private AerodynamicsWindCoupling() {
	}

	public static double localVoxelObstacleResidualFactor(Aerodynamics4McWindBridge.WindSample sample) {
		if (sample == null) {
			return 1.0;
		}
		return localVoxelObstacleResidualFactor(
				sample.hasFlow(),
				sample.trustedForGameplay(),
				sample.localVoxelFlow(),
				sample.confidence(),
				sample.shelterFactor()
		);
	}

	static double localVoxelObstacleResidualFactor(
			boolean hasFlow,
			boolean trustedForGameplay,
			boolean localVoxelFlow,
			double confidence,
			double shelterFactor
	) {
		if (!hasFlow || !trustedForGameplay || !localVoxelFlow) {
			return 1.0;
		}
		double trust = Double.isFinite(confidence) ? MathUtil.clamp(confidence, 0.0, 1.0) : 0.0;
		double shelter = Double.isFinite(shelterFactor) ? MathUtil.clamp(shelterFactor, 0.0, 1.0) : 0.0;
		double coverage = trust * (LOCAL_VOXEL_BASE_COVERAGE + LOCAL_VOXEL_SHELTER_COVERAGE * shelter);
		return MathUtil.clamp(1.0 - coverage, LOCAL_VOXEL_MIN_OBSTRUCTION_RESIDUAL, 1.0);
	}
}
