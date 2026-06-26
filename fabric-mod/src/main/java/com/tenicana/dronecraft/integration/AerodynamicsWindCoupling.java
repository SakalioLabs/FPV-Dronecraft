package com.tenicana.dronecraft.integration;

import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.Vec3;

public final class AerodynamicsWindCoupling {
	private static final double LOCAL_VOXEL_BASE_COVERAGE = 0.32;
	private static final double LOCAL_VOXEL_SHELTER_COVERAGE = 0.48;
	private static final double LOCAL_VOXEL_MIN_OBSTRUCTION_RESIDUAL = 0.28;
	private static final double LOCAL_VOXEL_SHELTER_GRADIENT_OBSTRUCTION_GAIN = 0.42;
	private static final double LOCAL_VOXEL_SHELTER_GRADIENT_MAX_OBSTRUCTION = 0.22;
	private static final double LOCAL_VOXEL_PRECIPITATION_SHELTER_RELIEF = 0.72;
	private static final double LOCAL_VOXEL_PRECIPITATION_MIN_EXPOSURE = 0.24;
	private static final double LOCAL_VOXEL_PRESSURE_TURBULENCE_FULL_SCALE_PASCALS = 1800.0;
	private static final double MAX_LOCAL_VOXEL_PRESSURE_TURBULENCE_BOOST = 0.16;
	private static final double LOCAL_VOXEL_PRESSURE_GRADIENT_FULL_SCALE_PASCALS = 1600.0;
	private static final double LOCAL_VOXEL_PRESSURE_GRADIENT_MAX_WIND_EQUIVALENT_MPS = 2.4;
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

	public static double localVoxelObstacleResidualFactorOrFallback(
			Aerodynamics4McWindBridge.WindSample sample,
			double fallbackResidual
	) {
		double fallback = Double.isFinite(fallbackResidual)
				? MathUtil.clamp(fallbackResidual, LOCAL_VOXEL_MIN_OBSTRUCTION_RESIDUAL, 1.0)
				: 1.0;
		if (sample == null
				|| !sample.hasFlow()
				|| !sample.localVoxelFlow()
				|| sourceQualityFactor(sample) <= 1.0e-9) {
			return fallback;
		}
		return localVoxelObstacleResidualFactor(sample);
	}

	public static double localVoxelShelterFactorOrFallback(
			Aerodynamics4McWindBridge.WindSample sample,
			double fallbackShelterFactor
	) {
		double fallback = Double.isFinite(fallbackShelterFactor)
				? MathUtil.clamp(fallbackShelterFactor, 0.0, 1.0)
				: 0.0;
		if (sample == null || !sample.hasFlow() || !sample.localVoxelFlow()) {
			return fallback;
		}
		double sourceQuality = sourceQualityFactor(sample);
		if (sourceQuality <= 1.0e-9) {
			return fallback;
		}
		double shelter = Double.isFinite(sample.shelterFactor()) ? MathUtil.clamp(sample.shelterFactor(), 0.0, 1.0) : 0.0;
		return fallback * (1.0 - sourceQuality) + shelter * sourceQuality;
	}

	public static double localVoxelPrecipitationExposureFactor(Aerodynamics4McWindBridge.WindSample sample) {
		if (sample == null) {
			return 1.0;
		}
		return localVoxelPrecipitationExposureFactor(
				sample.hasFlow(),
				sample.trustedForGameplay(),
				sample.localVoxelFlow(),
				sample.confidence(),
				sample.shelterFactor(),
				sample.freshnessAgeTicks()
		);
	}

	public static double localVoxelPrecipitationExposureFactor(RotorDiskShelterBlend blend) {
		if (blend == null) {
			return 1.0;
		}
		return precipitationExposureFactorForAdoptedShelter(blend.meanShelterFactor());
	}

	static double localVoxelPrecipitationExposureFactor(
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
		double sourceQuality = sourceQualityFactor(hasFlow, trustedForGameplay, confidence, freshnessAgeTicks);
		double shelter = Double.isFinite(shelterFactor) ? MathUtil.clamp(shelterFactor, 0.0, 1.0) : 0.0;
		return precipitationExposureFactorForAdoptedShelter(shelter * sourceQuality);
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
		turbulence += sourceQuality * MathUtil.clamp(Math.abs(sourceSeparatedUpdraftMetersPerSecond(sample)) * 0.025, 0.0, 0.18);
		if (sample.localVoxelFlow()) {
			double localPressureSignal = Math.abs(sample.pressureAnomalyPascals())
					/ LOCAL_VOXEL_PRESSURE_TURBULENCE_FULL_SCALE_PASCALS;
			turbulence += sourceQuality * MathUtil.clamp(
					localPressureSignal * MAX_LOCAL_VOXEL_PRESSURE_TURBULENCE_BOOST,
					0.0,
					MAX_LOCAL_VOXEL_PRESSURE_TURBULENCE_BOOST
			);
		}
		turbulence += sourceQuality * MathUtil.clamp(
				sample.gustSpeedMetersPerSecond() * SOURCE_GUST_TURBULENCE_GAIN_PER_MPS,
				0.0,
				MAX_SOURCE_GUST_TURBULENCE_BOOST
		);
		return MathUtil.clamp(turbulence, 0.0, 1.5);
	}

	private static double sourceSeparatedUpdraftMetersPerSecond(Aerodynamics4McWindBridge.WindSample sample) {
		if (sample == null || !sample.hasFlow()) {
			return 0.0;
		}
		double sourceUpdraft = MathUtil.clamp(sample.updraftMetersPerSecond(), -12.0, 12.0);
		if (Math.abs(sourceUpdraft) <= 1.0e-6) {
			return 0.0;
		}
		Vec3 sourceGustVelocity = sample.gustVelocityWorldMetersPerSecond();
		double explicitVerticalGust = sourceGustVelocity == null || !sourceGustVelocity.isFinite()
				? 0.0
				: sourceGustVelocity.y();
		sourceUpdraft = removeOverlappingVerticalFlow(sourceUpdraft, explicitVerticalGust);
		Vec3 meanVelocity = sample.meanVelocityWorldMetersPerSecond();
		double representedMeanVerticalFlow = meanVelocity == null || !meanVelocity.isFinite()
				? 0.0
				: meanVelocity.y();
		sourceUpdraft = removeOverlappingVerticalFlow(sourceUpdraft, representedMeanVerticalFlow);
		return MathUtil.clamp(sourceUpdraft, -12.0, 12.0);
	}

	private static double removeOverlappingVerticalFlow(double verticalSignal, double alreadyRepresentedVerticalFlow) {
		if (Math.abs(verticalSignal) <= 1.0e-6 || Math.abs(alreadyRepresentedVerticalFlow) <= 1.0e-6) {
			return verticalSignal;
		}
		if (Math.signum(verticalSignal) != Math.signum(alreadyRepresentedVerticalFlow)) {
			return verticalSignal;
		}
		if (Math.abs(verticalSignal) <= Math.abs(alreadyRepresentedVerticalFlow)) {
			return 0.0;
		}
		return verticalSignal - alreadyRepresentedVerticalFlow;
	}

	public static Vec3 sourceWeightedEffectiveWind(Vec3 fallbackWindWorldMetersPerSecond, Aerodynamics4McWindBridge.WindSample sample) {
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

	public static Vec3 sourceWeightedMeanWind(Vec3 fallbackWindWorldMetersPerSecond, Aerodynamics4McWindBridge.WindSample sample) {
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
		Vec3 sourceWind = sample.meanVelocityWorldMetersPerSecond();
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

	private static double localVoxelPressureAnomalyOrFallback(
			Aerodynamics4McWindBridge.WindSample sample,
			double fallbackPressureAnomalyPascals
	) {
		double fallback = Double.isFinite(fallbackPressureAnomalyPascals)
				? MathUtil.clamp(fallbackPressureAnomalyPascals, -5000.0, 5000.0)
				: 0.0;
		if (sample == null || !sample.hasFlow() || !sample.localVoxelFlow()) {
			return fallback;
		}
		double sourceQuality = sourceQualityFactor(sample);
		if (sourceQuality <= 1.0e-9) {
			return fallback;
		}
		double pressure = Double.isFinite(sample.pressureAnomalyPascals())
				? MathUtil.clamp(sample.pressureAnomalyPascals(), -5000.0, 5000.0)
				: 0.0;
		return fallback * (1.0 - sourceQuality) + pressure * sourceQuality;
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

	public static double sourceWeightedMeanSpeedMetersPerSecond(Aerodynamics4McWindBridge.WindSample sample) {
		if (sample == null || !sample.hasFlow()) {
			return 0.0;
		}
		return sample.meanVelocityWorldMetersPerSecond().length() * sourceQualityFactor(sample);
	}

	public static double sourceWeightedEffectiveSpeedMetersPerSecond(Aerodynamics4McWindBridge.WindSample sample) {
		if (sample == null || !sample.hasFlow()) {
			return 0.0;
		}
		return sample.effectiveVelocityWorldMetersPerSecond().length() * sourceQualityFactor(sample);
	}

	public static double sourceWeightedGustSpeedMetersPerSecond(Aerodynamics4McWindBridge.WindSample sample) {
		if (sample == null || !sample.hasFlow()) {
			return 0.0;
		}
		return sample.gustSpeedMetersPerSecond() * sourceQualityFactor(sample);
	}

	public static Vec3 sourceWeightedGustVelocityWorldMetersPerSecond(Aerodynamics4McWindBridge.WindSample sample) {
		if (sample == null || !sample.hasFlow()) {
			return Vec3.ZERO;
		}
		return sample.gustVelocityWorldMetersPerSecond().multiply(sourceQualityFactor(sample));
	}

	public static Vec3 rotorDiskSampleDirectionBody(Vec3 nominalBodyDirection, Vec3 rotorAxisBody) {
		Vec3 nominal = finiteVec(nominalBodyDirection);
		if (nominal.lengthSquared() <= 1.0e-12) {
			return Vec3.ZERO;
		}
		Vec3 axis = finiteVec(rotorAxisBody).normalized();
		if (axis.lengthSquared() <= 1.0e-12) {
			return nominal.normalized();
		}
		Vec3 projected = nominal.subtract(axis.multiply(nominal.dot(axis)));
		if (projected.lengthSquared() <= 1.0e-12) {
			Vec3 fallback = Math.abs(axis.y()) < 0.92
					? new Vec3(0.0, 1.0, 0.0)
					: new Vec3(1.0, 0.0, 0.0);
			projected = fallback.subtract(axis.multiply(fallback.dot(axis)));
		}
		return projected.lengthSquared() <= 1.0e-12 ? Vec3.ZERO : projected.normalized();
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
			sampleWindVelocities[i] = sourceWeightedMeanWind(centerWind, sampleWindSamples[i]);
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

	public static RotorDiskShelterBlend rotorDiskShelterBlend(
			Aerodynamics4McWindBridge.WindSample centerSample,
			Aerodynamics4McWindBridge.WindSample[] sampleWindSamples,
			Vec3[] sampleDirectionsBody,
			double[] sampleWeights,
			double centerWeight
	) {
		double centerShelter = localVoxelShelterFactorOrFallback(centerSample, 0.0);
		double safeCenterWeight = positiveWeight(centerWeight);
		double weightedShelter = centerShelter * safeCenterWeight;
		double totalWeight = safeCenterWeight;
		Vec3 gradientBody = Vec3.ZERO;
		double gradientWeight = 0.0;
		int sampleCount = Math.min(
				lengthOf(sampleWindSamples),
				Math.min(lengthOf(sampleDirectionsBody), lengthOf(sampleWeights))
		);
		for (int i = 0; i < sampleCount; i++) {
			double weight = positiveWeight(sampleWeights[i]);
			if (weight <= 0.0) {
				continue;
			}
			double sampleShelter = localVoxelShelterFactorOrFallback(sampleWindSamples[i], centerShelter);
			weightedShelter += sampleShelter * weight;
			totalWeight += weight;
			Vec3 directionBody = finiteVec(sampleDirectionsBody[i]);
			if (directionBody.lengthSquared() <= 1.0e-12) {
				continue;
			}
			gradientBody = gradientBody.add(directionBody.multiply((sampleShelter - centerShelter) * weight));
			gradientWeight += weight;
		}
		double meanShelter = totalWeight <= 1.0e-9 ? centerShelter : weightedShelter / totalWeight;
		Vec3 gradient = gradientWeight <= 1.0e-9 ? Vec3.ZERO : gradientBody.multiply(1.0 / gradientWeight);
		return new RotorDiskShelterBlend(meanShelter, gradient);
	}

	public static RotorDiskPressureBlend rotorDiskPressureBlend(
			Aerodynamics4McWindBridge.WindSample centerSample,
			Aerodynamics4McWindBridge.WindSample[] sampleWindSamples,
			Vec3[] sampleDirectionsBody,
			double[] sampleWeights,
			double centerWeight
	) {
		double centerPressure = localVoxelPressureAnomalyOrFallback(centerSample, 0.0);
		double safeCenterWeight = positiveWeight(centerWeight);
		double weightedPressure = centerPressure * safeCenterWeight;
		double totalWeight = safeCenterWeight;
		Vec3 gradientBody = Vec3.ZERO;
		double gradientWeight = 0.0;
		int sampleCount = Math.min(
				lengthOf(sampleWindSamples),
				Math.min(lengthOf(sampleDirectionsBody), lengthOf(sampleWeights))
		);
		for (int i = 0; i < sampleCount; i++) {
			double weight = positiveWeight(sampleWeights[i]);
			if (weight <= 0.0) {
				continue;
			}
			double samplePressure = localVoxelPressureAnomalyOrFallback(sampleWindSamples[i], centerPressure);
			weightedPressure += samplePressure * weight;
			totalWeight += weight;
			Vec3 directionBody = finiteVec(sampleDirectionsBody[i]);
			if (directionBody.lengthSquared() <= 1.0e-12) {
				continue;
			}
			gradientBody = gradientBody.add(directionBody.multiply((samplePressure - centerPressure) * weight));
			gradientWeight += weight;
		}
		double meanPressure = totalWeight <= 1.0e-9 ? centerPressure : weightedPressure / totalWeight;
		Vec3 gradient = gradientWeight <= 1.0e-9 ? Vec3.ZERO : gradientBody.multiply(1.0 / gradientWeight);
		return new RotorDiskPressureBlend(meanPressure, gradient);
	}

	public static Vec3 localVoxelPressureGradientWindEquivalent(RotorDiskPressureBlend blend) {
		if (blend == null) {
			return Vec3.ZERO;
		}
		Vec3 gradientBody = blend.gradientBodyPascals();
		double gradientMagnitude = gradientBody.length();
		if (gradientMagnitude <= 1.0e-6) {
			return Vec3.ZERO;
		}
		double windEquivalentMagnitude = MathUtil.clamp(
				gradientMagnitude / LOCAL_VOXEL_PRESSURE_GRADIENT_FULL_SCALE_PASCALS
						* LOCAL_VOXEL_PRESSURE_GRADIENT_MAX_WIND_EQUIVALENT_MPS,
				0.0,
				LOCAL_VOXEL_PRESSURE_GRADIENT_MAX_WIND_EQUIVALENT_MPS
		);
		return gradientBody.multiply(windEquivalentMagnitude / gradientMagnitude);
	}

	public static double localVoxelShelterGradientObstruction(RotorDiskShelterBlend blend) {
		if (blend == null) {
			return 0.0;
		}
		double gradient = blend.gradientBody().length();
		if (gradient <= 1.0e-6) {
			return 0.0;
		}
		double shelterGate = 0.35 + 0.65 * MathUtil.clamp(blend.meanShelterFactor(), 0.0, 1.0);
		return MathUtil.clamp(
				LOCAL_VOXEL_SHELTER_GRADIENT_OBSTRUCTION_GAIN * gradient * shelterGate,
				0.0,
				LOCAL_VOXEL_SHELTER_GRADIENT_MAX_OBSTRUCTION
		);
	}

	private static double precipitationExposureFactorForAdoptedShelter(double adoptedShelterFactor) {
		double shelter = Double.isFinite(adoptedShelterFactor) ? MathUtil.clamp(adoptedShelterFactor, 0.0, 1.0) : 0.0;
		return MathUtil.clamp(
				1.0 - shelter * LOCAL_VOXEL_PRECIPITATION_SHELTER_RELIEF,
				LOCAL_VOXEL_PRECIPITATION_MIN_EXPOSURE,
				1.0
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

	public record RotorDiskShelterBlend(double meanShelterFactor, Vec3 gradientBody) {
		public RotorDiskShelterBlend {
			meanShelterFactor = Double.isFinite(meanShelterFactor) ? MathUtil.clamp(meanShelterFactor, 0.0, 1.0) : 0.0;
			gradientBody = finiteVec(gradientBody).clamp(-1.0, 1.0);
		}
	}

	public record RotorDiskPressureBlend(double meanPressureAnomalyPascals, Vec3 gradientBodyPascals) {
		public RotorDiskPressureBlend {
			meanPressureAnomalyPascals = Double.isFinite(meanPressureAnomalyPascals)
					? MathUtil.clamp(meanPressureAnomalyPascals, -5000.0, 5000.0)
					: 0.0;
			gradientBodyPascals = finiteVec(gradientBodyPascals).clamp(-5000.0, 5000.0);
		}
	}
}
