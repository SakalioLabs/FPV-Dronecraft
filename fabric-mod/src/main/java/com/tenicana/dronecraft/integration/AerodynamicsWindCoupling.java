package com.tenicana.dronecraft.integration;

import com.tenicana.dronecraft.sim.MathUtil;

public final class AerodynamicsWindCoupling {
	private static final double LOCAL_VOXEL_BASE_COVERAGE = 0.32;
	private static final double LOCAL_VOXEL_SHELTER_COVERAGE = 0.48;
	private static final double LOCAL_VOXEL_MIN_OBSTRUCTION_RESIDUAL = 0.28;
	private static final double SOURCE_GUST_TURBULENCE_GAIN_PER_MPS = 0.065;
	private static final double MAX_SOURCE_GUST_TURBULENCE_BOOST = 0.26;

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

	public static double naturalTurbulenceIntensity(
			double weatherTurbulence,
			Aerodynamics4McWindBridge.WindSample sample
	) {
		double turbulence = Double.isFinite(weatherTurbulence) ? MathUtil.clamp(weatherTurbulence, 0.0, 1.5) : 0.0;
		if (sample == null || !sample.hasFlow()) {
			return turbulence;
		}
		turbulence = Math.max(turbulence, sample.turbulenceIntensity());
		turbulence += MathUtil.clamp(sample.windShearMagnitudePerBlock() * 0.45, 0.0, 0.35);
		turbulence += MathUtil.clamp(sample.shelterFactor() * 0.20, 0.0, 0.20);
		turbulence += MathUtil.clamp(Math.abs(sample.updraftMetersPerSecond()) * 0.025, 0.0, 0.18);
		turbulence += MathUtil.clamp(
				sample.gustSpeedMetersPerSecond() * SOURCE_GUST_TURBULENCE_GAIN_PER_MPS,
				0.0,
				MAX_SOURCE_GUST_TURBULENCE_BOOST
		);
		return MathUtil.clamp(turbulence, 0.0, 1.5);
	}
}
