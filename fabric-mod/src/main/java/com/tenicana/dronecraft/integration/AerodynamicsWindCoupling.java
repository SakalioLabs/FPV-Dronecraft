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

	public static double sourceWeightedPressureAnomalyPascals(Aerodynamics4McWindBridge.WindSample sample) {
		if (sample == null || !sample.hasFlow()) {
			return 0.0;
		}
		return sample.pressureAnomalyPascals() * sourceQualityFactor(sample);
	}

	public static double sourceWeightedTemperatureCelsius(
			double fallbackTemperatureCelsius,
			Aerodynamics4McWindBridge.WindSample sample
	) {
		double fallback = Double.isFinite(fallbackTemperatureCelsius)
				? MathUtil.clamp(fallbackTemperatureCelsius, -40.0, 65.0)
				: 25.0;
		if (sample == null || !sample.hasFlow() || !sample.hasAmbientTemperature()) {
			return fallback;
		}
		double sourceQuality = sourceQualityFactor(sample);
		if (sourceQuality <= 1.0e-9) {
			return fallback;
		}
		double sourceTemperature = MathUtil.clamp(sample.ambientTemperatureCelsius(), -40.0, 65.0);
		return fallback * (1.0 - sourceQuality) + sourceTemperature * sourceQuality;
	}

	public static double sourceWeightedHumidity(Aerodynamics4McWindBridge.WindSample sample) {
		if (sample == null || !sample.hasFlow() || !sample.hasHumidity()) {
			return 0.0;
		}
		return MathUtil.clamp(sample.humidity(), 0.0, 1.0) * sourceQualityFactor(sample);
	}

	public static RotorDiskWindBlend rotorDiskWindBlend(
			Vec3 centerWindWorldMetersPerSecond,
			Vec3 rotorAxisWorld,
			Vec3[] sampleWindVelocitiesWorldMetersPerSecond,
			Vec3[] sampleDirectionsBody,
			double[] sampleWeights,
			double centerWeight
	) {
		Vec3 centerWind = finiteVec(centerWindWorldMetersPerSecond);
		Vec3 axisWorld = finiteVec(rotorAxisWorld).normalized();
		double safeCenterWeight = positiveWeight(centerWeight);
		Vec3 weightedWind = centerWind.multiply(safeCenterWeight);
		double totalWeight = safeCenterWeight;
		Vec3 gradientBody = Vec3.ZERO;
		double gradientWeight = 0.0;
		double centerAxialWind = centerWind.dot(axisWorld);
		int sampleCount = Math.min(
				lengthOf(sampleWindVelocitiesWorldMetersPerSecond),
				Math.min(lengthOf(sampleDirectionsBody), lengthOf(sampleWeights))
		);
		for (int i = 0; i < sampleCount; i++) {
			double weight = positiveWeight(sampleWeights[i]);
			if (weight <= 0.0) {
				continue;
			}
			Vec3 sampleWind = finiteVecOrFallback(sampleWindVelocitiesWorldMetersPerSecond[i], centerWind);
			weightedWind = weightedWind.add(sampleWind.multiply(weight));
			totalWeight += weight;
			Vec3 directionBody = finiteVec(sampleDirectionsBody[i]);
			if (directionBody.lengthSquared() <= 1.0e-12 || axisWorld.lengthSquared() <= 1.0e-12) {
				continue;
			}
			double axialDelta = sampleWind.dot(axisWorld) - centerAxialWind;
			gradientBody = gradientBody.add(directionBody.multiply(axialDelta * weight));
			gradientWeight += weight;
		}
		Vec3 meanWind = totalWeight <= 1.0e-9
				? centerWind
				: weightedWind.multiply(1.0 / totalWeight);
		Vec3 gradient = gradientWeight <= 1.0e-9
				? Vec3.ZERO
				: gradientBody.multiply(1.0 / gradientWeight);
		return new RotorDiskWindBlend(meanWind, gradient);
	}

	public static RotorDiskWindBlend rotorDiskWindBlend(
			Vec3 centerWindWorldMetersPerSecond,
			Vec3 rotorAxisWorld,
			Aerodynamics4McWindBridge.WindSample[] sampleWindSamples,
			Vec3[] sampleDirectionsBody,
			double[] sampleWeights,
			double centerWeight
	) {
		Vec3 centerWind = finiteVec(centerWindWorldMetersPerSecond);
		int sampleCount = lengthOf(sampleWindSamples);
		Vec3[] sampleWindVelocities = new Vec3[sampleCount];
		for (int i = 0; i < sampleCount; i++) {
			sampleWindVelocities[i] = sourceWeightedWind(centerWind, sampleWindSamples[i]);
		}
		return rotorDiskWindBlend(
				centerWind,
				rotorAxisWorld,
				sampleWindVelocities,
				sampleDirectionsBody,
				sampleWeights,
				centerWeight
		);
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

	private static int lengthOf(Object[] values) {
		return values == null ? 0 : values.length;
	}

	private static int lengthOf(double[] values) {
		return values == null ? 0 : values.length;
	}

	private static Vec3 finiteVec(Vec3 value) {
		return finiteVecOrFallback(value, Vec3.ZERO);
	}

	private static Vec3 finiteVecOrFallback(Vec3 value, Vec3 fallback) {
		return value == null || !value.isFinite() ? fallback : value;
	}

	private static double positiveWeight(double weight) {
		return Double.isFinite(weight) && weight > 0.0 ? weight : 0.0;
	}

	public record RotorDiskWindBlend(Vec3 meanWindWorldMetersPerSecond, Vec3 gradientBodyMetersPerSecond) {
		public RotorDiskWindBlend {
			meanWindWorldMetersPerSecond = finiteVec(meanWindWorldMetersPerSecond);
			gradientBodyMetersPerSecond = finiteVec(gradientBodyMetersPerSecond);
		}
	}
}
