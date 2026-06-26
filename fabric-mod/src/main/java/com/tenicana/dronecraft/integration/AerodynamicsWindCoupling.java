package com.tenicana.dronecraft.integration;

import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.Vec3;

public final class AerodynamicsWindCoupling {
	private static final double LOCAL_VOXEL_BASE_COVERAGE = 0.32;
	private static final double LOCAL_VOXEL_SHELTER_COVERAGE = 0.48;
	private static final double LOCAL_VOXEL_MIN_OBSTRUCTION_RESIDUAL = 0.28;
	private static final double SOURCE_GUST_TURBULENCE_GAIN_PER_MPS = 0.065;
	private static final double MAX_SOURCE_GUST_TURBULENCE_BOOST = 0.26;
	private static final long SOURCE_FULL_TRUST_AGE_TICKS = 40L;
	private static final long SOURCE_ZERO_TRUST_AGE_TICKS = 160L;

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
				sample.shelterFactor(),
				sample.freshnessAgeTicks()
		);
	}

	static double localVoxelObstacleResidualFactor(
			boolean hasFlow,
			boolean trustedForGameplay,
			boolean localVoxelFlow,
			double confidence,
			double shelterFactor
	) {
		return localVoxelObstacleResidualFactor(
				hasFlow,
				trustedForGameplay,
				localVoxelFlow,
				confidence,
				shelterFactor,
				-1L
		);
	}

	static double localVoxelObstacleResidualFactor(
			boolean hasFlow,
			boolean trustedForGameplay,
			boolean localVoxelFlow,
			double confidence,
			double shelterFactor,
			long freshnessAgeTicks
	) {
		if (!hasFlow || !trustedForGameplay || !localVoxelFlow) {
			return 1.0;
		}
		double trust = sourceQualityFactor(hasFlow, trustedForGameplay, confidence, freshnessAgeTicks);
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
		double sourceQuality = sourceQualityFactor(sample);
		turbulence = Math.max(turbulence, sample.turbulenceIntensity() * sourceQuality);
		turbulence += sourceQuality * MathUtil.clamp(sample.windShearMagnitudePerBlock() * 0.45, 0.0, 0.35);
		turbulence += sourceQuality * MathUtil.clamp(sample.shelterFactor() * 0.20, 0.0, 0.20);
		turbulence += sourceQuality * MathUtil.clamp(Math.abs(sample.updraftMetersPerSecond()) * 0.025, 0.0, 0.18);
		turbulence += sourceQuality * MathUtil.clamp(
				sample.gustSpeedMetersPerSecond() * SOURCE_GUST_TURBULENCE_GAIN_PER_MPS,
				0.0,
				MAX_SOURCE_GUST_TURBULENCE_BOOST
		);
		return MathUtil.clamp(turbulence, 0.0, 1.5);
	}

	public static Vec3 sourceWeightedWind(Vec3 fallbackWindWorldMetersPerSecond, Aerodynamics4McWindBridge.WindSample sample) {
		Vec3 fallback = fallbackWindWorldMetersPerSecond == null || !fallbackWindWorldMetersPerSecond.isFinite()
				? Vec3.ZERO
				: fallbackWindWorldMetersPerSecond;
		if (sample == null || !sample.hasFlow()) {
			return fallback;
		}
		double sourceQuality = sourceQualityFactor(sample);
		if (sourceQuality <= 1.0e-9) {
			return fallback;
		}
		Vec3 sourceWind = sample.effectiveVelocityWorldMetersPerSecond();
		if (sourceQuality >= 1.0 - 1.0e-9) {
			return sourceWind;
		}
		return fallback.multiply(1.0 - sourceQuality).add(sourceWind.multiply(sourceQuality));
	}

	public static double sourceQualityFactor(Aerodynamics4McWindBridge.WindSample sample) {
		if (sample == null) {
			return 0.0;
		}
		return sourceQualityFactor(
				sample.hasFlow(),
				sample.trustedForGameplay(),
				sample.confidence(),
				sample.freshnessAgeTicks()
		);
	}

	static double sourceQualityFactor(
			boolean hasFlow,
			boolean trustedForGameplay,
			double confidence,
			long freshnessAgeTicks
	) {
		if (!hasFlow || !trustedForGameplay) {
			return 0.0;
		}
		double trust = Double.isFinite(confidence) ? MathUtil.clamp(confidence, 0.0, 1.0) : 0.0;
		return trust * freshnessFactor(freshnessAgeTicks);
	}

	private static double freshnessFactor(long freshnessAgeTicks) {
		if (freshnessAgeTicks < 0L) {
			return 1.0;
		}
		if (freshnessAgeTicks <= SOURCE_FULL_TRUST_AGE_TICKS) {
			return 1.0;
		}
		if (freshnessAgeTicks >= SOURCE_ZERO_TRUST_AGE_TICKS) {
			return 0.0;
		}
		double t = (freshnessAgeTicks - SOURCE_FULL_TRUST_AGE_TICKS)
				/ (double) (SOURCE_ZERO_TRUST_AGE_TICKS - SOURCE_FULL_TRUST_AGE_TICKS);
		double smooth = t * t * (3.0 - 2.0 * t);
		return 1.0 - smooth;
	}
}
