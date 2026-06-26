package com.tenicana.dronecraft.blackbox;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;

public record DroneBlackboxSummary(
		int sampleCount,
		double durationSeconds,
		int maxPhysicsSubsteps,
		double maxPhysicsRateHertz,
		double maxSpeedMetersPerSecond,
		double maxAirspeedMetersPerSecond,
		double maxBatteryCurrentAmps,
		double maxBatteryRegenerativeCurrentAmps,
		double maxMotorRegenerativeCurrentAmps,
		double maxBatterySagVoltage,
		double maxBatteryEffectiveResistanceOhms,
		double maxBatteryStateOfChargeResistanceScale,
		double maxBatteryTemperatureResistanceScale,
		double maxBatteryPolarizationResistanceScale,
		double maxBatteryVoltageSpike,
		double maxBatteryBusRippleVoltage,
		double maxImuSupplyNoiseIntensity,
		double minBatteryVoltage,
		double minBatteryStateOfCharge,
		double minBatteryCurrentLimit,
		double maxBatteryTemperatureCelsius,
		double minBatteryThermalLimit,
		double maxPropwashIntensity,
		double maxVortexRingState,
		double maxVortexRingThrustBuffetAmplitude,
		double maxVortexRingBuffetForceNewtons,
		double maxRotorInducedVelocityMetersPerSecond,
		double minRotorInducedLagThrustScale,
		double maxRotorTranslationalLiftIntensity,
		double maxRotorAdvanceRatio,
		double maxRotorPropellerAdvanceRatioJ,
		double minRotorPropellerThrustScale,
		double minRotorPropellerPowerScale,
		AxialGustStats axialGustStats,
		double maxRotorReverseFlowInboardFraction,
		double maxRotorTipMach,
		double minRotorCompressibilityThrustScale,
		double maxRotorLowReynoldsLoss,
		double maxRotorBladePassRippleIntensity,
		double maxRotorAerodynamicLoadFactor,
		double maxRotorInPlaneDragForceNewtons,
		double maxMotorMechanicalLossTorqueNewtonMeters,
		double maxMotorTrackingError,
		double minMotorActuatorAuthority,
		double maxRotorInflowSkewIntensity,
		double maxRotorBladeDissymmetryTorqueNewtonMeters,
		double maxRotorWakeInterferenceIntensity,
		double maxRotorCoaxialLoadBias,
		double maxRotorCoaxialLoadBiasTarget,
		double maxRotorCoaxialLoadBiasClipping,
		double maxRotorCoaxialAllocationLoadFraction,
		double maxRotorCoaxialAllocationCommandRatio,
		double maxRotorCoaxialAllocationMechanicalGainPercent,
		double maxRotorCoaxialAllocationElectricalGainPercent,
		double maxRotorCoaxialAllocationUncertaintyPercent,
		double minRotorWetThrustScale,
		double maxRotorWakeSwirlVelocityMetersPerSecond,
		double maxRotorWindmillingIntensity,
		double maxRotorWakeSwirlTorqueNewtonMeters,
		double maxRotorActiveBrakingTorqueNewtonMeters,
		double maxRotorAccelerationReactionTorqueNewtonMeters,
		double maxRotorGyroscopicTorqueNewtonMeters,
		double maxRotorFlappingTorqueNewtonMeters,
		double maxRotorAngularDragTorqueNewtonMeters,
		double maxAirframeAngularDragTorqueNewtonMeters,
		double maxAirframeSeparatedFlowIntensity,
		double maxAirframeLiftForceNewtons,
		double maxAirframeBodyDragForceNewtons,
		double maxLinearDampingDragForceNewtons,
		double maxGroundEffectDragForceNewtons,
		double maxGroundEffectLevelingTorqueNewtonMeters,
		double maxRotorWashDragForceNewtons,
		double maxRotorWallEffectForceNewtons,
		double maxContactImpactSpeedMetersPerSecond,
		double maxContactSlipSpeedMetersPerSecond,
		double maxContactBounceSpeedMetersPerSecond,
		double minContactSurfaceFrictionMultiplier,
		double maxContactSurfaceFrictionMultiplier,
		double minContactSurfaceRestitutionMultiplier,
		double maxContactSurfaceRestitutionMultiplier,
		double minContactSurfaceScrapeMultiplier,
		double maxContactSurfaceScrapeMultiplier,
		double maxContactAngularImpulseDegreesPerSecond,
		double maxBarometerErrorMeters,
		double maxBarometerPropwashErrorMeters,
		double minBarometerPressureHectopascals,
		double maxRotorStallIntensity,
		double maxRotorVibration,
		double maxRotorDamageVibration,
		double maxRotorConingIntensity,
		double maxRotorConingAngleDegrees,
		double maxRotorFlappingTiltDegrees,
		double maxRotorArmFlexIntensity,
		double maxRotorArmFlexDeflectionMillimeters,
		double maxRotorArmFlexTiltDegrees,
		double maxRotorSurfaceScrapeIntensity,
		double maxMixerSaturation,
		double maxMixerLowSaturation,
		double maxMixerHighSaturation,
		double minMixerLowHeadroom,
		double minMixerHighHeadroom,
		double minMixerAxisAuthority,
		double maxMotorTemperatureCelsius,
		double minMotorElectricalEfficiency,
		double minMotorVoltageHeadroom,
		double maxMotorWindingResistanceScale,
		double maxEscTemperatureCelsius,
		double minEscThermalLimit,
		double maxEscDesyncIntensity,
		double maxDroneWakeIntensity,
		double maxWaterImmersionIntensity,
		double maxPrecipitationWetnessIntensity,
		double minAmbientTemperatureCelsius,
		double maxAmbientTemperatureCelsius,
		double maxWindGustSpeedMetersPerSecond,
		WindSplit windSplit,
		double maxWindShearAccelerationMetersPerSecondSquared,
		double maxCeilingEffectMultiplier,
		double maxEnvironmentThrustAsymmetry,
		double maxRotorFlowObstruction,
		double minCeilingClearanceMeters,
		double maxAltitudeMeters,
		double maxControlLinkLossSeconds,
		double maxControlFrameAgeSeconds,
		double maxControlFrameError,
		double minRotorHealth,
		double maxPropStrikeSeverity,
		int propStrikeSamples,
		int propStrikeCount,
		int failsafeSamples,
		int collisionSamples
) {
	private static final double TICKS_PER_SECOND = 20.0;
	private static final Map<String, Integer> COLUMNS = columnIndex();
	private static final WindSplit EMPTY_WIND_SPLIT = new WindSplit(0.0, 0.0, 0.0, 0.0, 0.0);
	private static final AxialGustStats EMPTY_AXIAL_GUST_STATS = new AxialGustStats(1.0, 1.0);
	private static final IcingStats EMPTY_ICING_STATS = new IcingStats(0.0, 1.0, 1.0);
	private static final BarometerStats EMPTY_BAROMETER_STATS = new BarometerStats(0.0);
	private static final FlightModelStats EMPTY_FLIGHT_MODEL_STATS = new FlightModelStats(0, 0);
	private static final LowAltitudeStats EMPTY_LOW_ALTITUDE_STATS = new LowAltitudeStats(1.0);
	private static final PlayableVisualStats EMPTY_PLAYABLE_VISUAL_STATS = new PlayableVisualStats(0.0, 0.0, 0.0, 0.0);
	private static final PlayableNeutralStats EMPTY_PLAYABLE_NEUTRAL_STATS = new PlayableNeutralStats(0, 0.0, 0.0, 0.0);
	private static final WindSourceStats EMPTY_WIND_SOURCE_STATS = new WindSourceStats(0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0);
	private static final DiskGradientStats EMPTY_DISK_GRADIENT_STATS = new DiskGradientStats(0.0, 0.0, 0.0, 0.0);
	private static final double WIND_SOURCE_STALE_AGE_TICKS = 80.0;
	private static final Map<DroneBlackboxSummary, IcingStats> ICING_STATS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<DroneBlackboxSummary, BarometerStats> BAROMETER_STATS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<DroneBlackboxSummary, FlightModelStats> FLIGHT_MODEL_STATS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<DroneBlackboxSummary, LowAltitudeStats> LOW_ALTITUDE_STATS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<DroneBlackboxSummary, PlayableVisualStats> PLAYABLE_VISUAL_STATS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<DroneBlackboxSummary, PlayableNeutralStats> PLAYABLE_NEUTRAL_STATS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<DroneBlackboxSummary, WindSourceStats> WIND_SOURCE_STATS =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<DroneBlackboxSummary, DiskGradientStats> DISK_GRADIENT_STATS =
			Collections.synchronizedMap(new WeakHashMap<>());

	public record WindSplit(
			double maxDrydenSpeedMetersPerSecond,
			double maxBurbleSpeedMetersPerSecond,
			double maxA4mcSourceGustSpeedMetersPerSecond,
			double maxAbsA4mcUpdraftMetersPerSecond,
			double maxA4mcTerrainShearSpeedMetersPerSecond
	) {
		public WindSplit {
			maxDrydenSpeedMetersPerSecond = finiteNonNegativeOrZero(maxDrydenSpeedMetersPerSecond);
			maxBurbleSpeedMetersPerSecond = finiteNonNegativeOrZero(maxBurbleSpeedMetersPerSecond);
			maxA4mcSourceGustSpeedMetersPerSecond = finiteNonNegativeOrZero(maxA4mcSourceGustSpeedMetersPerSecond);
			maxAbsA4mcUpdraftMetersPerSecond = finiteNonNegativeOrZero(maxAbsA4mcUpdraftMetersPerSecond);
			maxA4mcTerrainShearSpeedMetersPerSecond = finiteNonNegativeOrZero(maxA4mcTerrainShearSpeedMetersPerSecond);
		}
	}

	public record AxialGustStats(
			double minThrustScale,
			double maxThrustScale
	) {
		public AxialGustStats {
			minThrustScale = finiteScaleOrOne(minThrustScale);
			maxThrustScale = finiteScaleOrOne(maxThrustScale);
		}
	}

	private record IcingStats(
			double maxRotorIcingSeverity,
			double minRotorIcingThrustScale,
			double maxRotorIcingPowerScale
	) {
		public IcingStats {
			maxRotorIcingSeverity = finiteNonNegativeOrZero(maxRotorIcingSeverity);
			minRotorIcingThrustScale = finiteScaleOrOne(minRotorIcingThrustScale);
			maxRotorIcingPowerScale = Math.max(1.0, finiteScaleOrOne(maxRotorIcingPowerScale));
		}
	}

	private record DiskGradientStats(
			double maxThrustLossFraction,
			double maxLoadFactor,
			double maxVibration,
			double maxStallIntensity
	) {
		public DiskGradientStats {
			maxThrustLossFraction = unitOrZero(maxThrustLossFraction);
			maxLoadFactor = finiteNonNegativeOrZero(maxLoadFactor);
			maxVibration = finiteNonNegativeOrZero(maxVibration);
			maxStallIntensity = finiteNonNegativeOrZero(maxStallIntensity);
		}
	}

	private record BarometerStats(double maxPressurePortErrorMeters) {
		public BarometerStats {
			maxPressurePortErrorMeters = finiteNonNegativeOrZero(maxPressurePortErrorMeters);
		}
	}

	private record FlightModelStats(
			int playableSamples,
			int simulationSamples
	) {
		public FlightModelStats {
			playableSamples = Math.max(0, playableSamples);
			simulationSamples = Math.max(0, simulationSamples);
		}
	}

	private record LowAltitudeStats(double minPlayableLowAltitudeAuthority) {
		public LowAltitudeStats {
			minPlayableLowAltitudeAuthority = unitOrOne(minPlayableLowAltitudeAuthority);
		}
	}

	public record PlayableVisualStats(
			double maxPitchDegrees,
			double maxRollDegrees,
			double maxYawRateDegreesPerSecond,
			double finalYawDriftDegrees
	) {
		public PlayableVisualStats {
			maxPitchDegrees = finiteNonNegativeOrZero(maxPitchDegrees);
			maxRollDegrees = finiteNonNegativeOrZero(maxRollDegrees);
			maxYawRateDegreesPerSecond = finiteNonNegativeOrZero(maxYawRateDegreesPerSecond);
			finalYawDriftDegrees = finiteNonNegativeOrZero(finalYawDriftDegrees);
		}
	}

	public record PlayableNeutralStats(
			int sampleCount,
			double maxPitchDegrees,
			double maxRollDegrees,
			double maxYawRateDegreesPerSecond
	) {
		public PlayableNeutralStats {
			sampleCount = Math.max(0, sampleCount);
			maxPitchDegrees = finiteNonNegativeOrZero(maxPitchDegrees);
			maxRollDegrees = finiteNonNegativeOrZero(maxRollDegrees);
			maxYawRateDegreesPerSecond = finiteNonNegativeOrZero(maxYawRateDegreesPerSecond);
		}
	}

	public record WindSourceStats(
			int aerodynamics4McSamples,
			int trustedSourceSamples,
			int untrustedAerodynamics4McSamples,
			int localVoxelFlowSamples,
			int l0SourceSamples,
			int l1SourceSamples,
			int l2SourceSamples,
			int staleSourceSamples,
			double maxFreshnessAgeTicks,
			double maxMeanSpeedMetersPerSecond,
			double maxEffectiveSpeedMetersPerSecond,
			double maxGustSpeedMetersPerSecond,
			double maxConfidence,
			double maxTurbulenceIntensity,
			double maxQualityFactor,
			double maxAbsPressureAnomalyPascals,
			double maxShelterFactor,
			double maxShearMagnitudePerBlock,
			double maxAbsUpdraftMetersPerSecond,
			double maxAbsAblStability,
			double maxAblMixingStrength,
			double maxRotorDiskWindGradientMetersPerSecond,
			double maxRotorA4mcPressureGradientWindMetersPerSecond,
			double maxRotorA4mcShelterObstruction,
			double minRotorLocalVoxelObstacleResidual
	) {
		public WindSourceStats {
			aerodynamics4McSamples = Math.max(0, aerodynamics4McSamples);
			trustedSourceSamples = Math.max(0, trustedSourceSamples);
			untrustedAerodynamics4McSamples = Math.max(0, untrustedAerodynamics4McSamples);
			localVoxelFlowSamples = Math.max(0, localVoxelFlowSamples);
			l0SourceSamples = Math.max(0, l0SourceSamples);
			l1SourceSamples = Math.max(0, l1SourceSamples);
			l2SourceSamples = Math.max(0, l2SourceSamples);
			staleSourceSamples = Math.max(0, staleSourceSamples);
			maxFreshnessAgeTicks = finiteNonNegativeOrZero(maxFreshnessAgeTicks);
			maxMeanSpeedMetersPerSecond = finiteNonNegativeOrZero(maxMeanSpeedMetersPerSecond);
			maxEffectiveSpeedMetersPerSecond = finiteNonNegativeOrZero(maxEffectiveSpeedMetersPerSecond);
			maxGustSpeedMetersPerSecond = finiteNonNegativeOrZero(maxGustSpeedMetersPerSecond);
			maxConfidence = unitOrZero(maxConfidence);
			maxTurbulenceIntensity = finiteNonNegativeOrZero(maxTurbulenceIntensity);
			maxQualityFactor = unitOrZero(maxQualityFactor);
			maxAbsPressureAnomalyPascals = finiteNonNegativeOrZero(maxAbsPressureAnomalyPascals);
			maxShelterFactor = unitOrZero(maxShelterFactor);
			maxShearMagnitudePerBlock = finiteNonNegativeOrZero(maxShearMagnitudePerBlock);
			maxAbsUpdraftMetersPerSecond = finiteNonNegativeOrZero(maxAbsUpdraftMetersPerSecond);
			maxAbsAblStability = unitOrZero(maxAbsAblStability);
			maxAblMixingStrength = unitOrZero(maxAblMixingStrength);
			maxRotorDiskWindGradientMetersPerSecond = finiteNonNegativeOrZero(maxRotorDiskWindGradientMetersPerSecond);
			maxRotorA4mcPressureGradientWindMetersPerSecond = finiteNonNegativeOrZero(maxRotorA4mcPressureGradientWindMetersPerSecond);
			maxRotorA4mcShelterObstruction = unitOrZero(maxRotorA4mcShelterObstruction);
			minRotorLocalVoxelObstacleResidual = unitOrOne(minRotorLocalVoxelObstacleResidual);
		}
	}

	public static DroneBlackboxSummary from(DroneBlackboxRecorder recorder) {
		List<DroneBlackboxSample> samples = recorder.snapshot();
		if (samples.isEmpty()) {
			return empty();
		}

		double maxSpeed = 0.0;
		double maxAirspeed = 0.0;
		double maxCurrent = 0.0;
		double maxRegenCurrent = 0.0;
		double maxMotorRegenCurrent = 0.0;
		double maxSag = 0.0;
		double maxEffectiveResistance = 0.0;
		double maxSocResistanceScale = 1.0;
		double maxTemperatureResistanceScale = 1.0;
		double maxPolarizationResistanceScale = 1.0;
		double maxVoltageSpike = 0.0;
		double maxBusRipple = 0.0;
		double maxImuSupplyNoise = 0.0;
		double minVoltage = Double.POSITIVE_INFINITY;
		double minSoc = Double.POSITIVE_INFINITY;
		double minCurrentLimit = Double.POSITIVE_INFINITY;
		double maxBatteryTemperature = 25.0;
		double minBatteryThermalLimit = 1.0;
		double maxPropwash = 0.0;
		double maxVrs = 0.0;
		double maxVrsThrustBuffet = 0.0;
		double maxVrsBuffetForce = 0.0;
		double maxRotorInducedVelocity = 0.0;
		double minRotorInducedLagThrustScale = 1.0;
		double maxRotorTranslationalLift = 0.0;
		double maxRotorAdvanceRatio = 0.0;
		double maxRotorPropellerAdvanceRatioJ = 0.0;
		double minRotorPropellerThrustScale = 1.0;
		double minRotorPropellerPowerScale = 1.0;
		double minRotorAxialGustThrustScale = 1.0;
		double maxRotorAxialGustThrustScale = 1.0;
		double maxRotorReverseFlow = 0.0;
		double maxRotorTipMach = 0.0;
		double minRotorCompressibilityThrustScale = 1.0;
		double maxRotorLowReynoldsLoss = 0.0;
		double maxRotorBladePassRipple = 0.0;
		double maxRotorAerodynamicLoad = 0.0;
		double maxRotorDiskGradientThrustLoss = 0.0;
		double maxRotorDiskGradientLoad = 0.0;
		double maxRotorDiskGradientVibration = 0.0;
		double maxRotorDiskGradientStall = 0.0;
		double maxRotorInPlaneDragForce = 0.0;
		double maxMotorMechanicalLoss = 0.0;
		double maxMotorTrackingError = 0.0;
		double minMotorActuatorAuthority = 1.0;
		double maxRotorInflowSkew = 0.0;
		double maxRotorBladeDissymmetryTorque = 0.0;
		double maxRotorWakeInterference = 0.0;
		double maxRotorCoaxialLoadBias = 0.0;
		double maxRotorCoaxialLoadBiasTarget = 0.0;
		double maxRotorCoaxialLoadBiasClipping = 0.0;
		double maxRotorCoaxialAllocationLoadFraction = 0.0;
		double maxRotorCoaxialAllocationCommandRatio = 1.0;
		double maxRotorCoaxialAllocationMechanicalGain = 0.0;
		double maxRotorCoaxialAllocationElectricalGain = 0.0;
		double maxRotorCoaxialAllocationUncertainty = 0.0;
		double minRotorWetThrustScale = 1.0;
		double maxRotorIcingSeverity = 0.0;
		double minRotorIcingThrustScale = 1.0;
		double maxRotorIcingPowerScale = 1.0;
		double maxRotorWakeSwirlVelocity = 0.0;
		double maxRotorWindmilling = 0.0;
		double maxRotorWakeSwirlTorque = 0.0;
		double maxRotorActiveBrakingTorque = 0.0;
		double maxRotorAccelerationReactionTorque = 0.0;
		double maxRotorGyroscopicTorque = 0.0;
		double maxRotorFlappingTorque = 0.0;
		double maxRotorAngularDrag = 0.0;
		double maxAirframeAngularDrag = 0.0;
		double maxAirframeSeparation = 0.0;
		double maxAirframeLift = 0.0;
		double maxAirframeBodyDrag = 0.0;
		double maxLinearDampingDrag = 0.0;
		double maxGroundEffectDrag = 0.0;
		double maxGroundEffectLevelingTorque = 0.0;
		double maxRotorWashDrag = 0.0;
		double maxRotorWallEffect = 0.0;
		double maxContactImpact = 0.0;
		double maxContactSlip = 0.0;
		double maxContactBounce = 0.0;
		double minContactSurfaceFriction = 1.0;
		double maxContactSurfaceFriction = 1.0;
		double minContactSurfaceRestitution = 1.0;
		double maxContactSurfaceRestitution = 1.0;
		double minContactSurfaceScrape = 1.0;
		double maxContactSurfaceScrape = 1.0;
		double maxContactAngularImpulse = 0.0;
		double maxBarometerError = 0.0;
		double maxBarometerPressurePortError = 0.0;
		double maxBarometerPropwashError = 0.0;
		double minBarometerPressure = Double.POSITIVE_INFINITY;
		double maxRotorStall = 0.0;
		double maxRotorVibration = 0.0;
		double maxRotorDamageVibration = 0.0;
		double maxRotorConing = 0.0;
		double maxRotorConingAngle = 0.0;
		double maxRotorFlappingTilt = 0.0;
		double maxRotorArmFlex = 0.0;
		double maxRotorArmFlexDeflection = 0.0;
		double maxRotorArmFlexTilt = 0.0;
		double maxRotorSurfaceScrape = 0.0;
		double maxMixer = 0.0;
		double maxMixerLowSaturation = 0.0;
		double maxMixerHighSaturation = 0.0;
		double minMixerLowHeadroom = 1.0;
		double minMixerHighHeadroom = 1.0;
		double minMixerAxisAuthority = 1.0;
		double maxMotorTemp = 0.0;
		double minMotorElectricalEfficiency = Double.POSITIVE_INFINITY;
		double minMotorVoltageHeadroom = Double.POSITIVE_INFINITY;
		double maxMotorWindingResistanceScale = 1.0;
		double maxEscTemp = 0.0;
		double minEscThermalLimit = 1.0;
		double maxEscDesync = 0.0;
		double maxWake = 0.0;
		double maxWaterImmersion = 0.0;
		double maxPrecipitationWetness = 0.0;
		double minAmbientTemperature = Double.POSITIVE_INFINITY;
		double maxAmbientTemperature = Double.NEGATIVE_INFINITY;
		double maxWindGust = 0.0;
		double maxWindDryden = 0.0;
		double maxWindBurble = 0.0;
		double maxWindA4mcSourceGust = 0.0;
		double maxAbsWindA4mcUpdraft = 0.0;
		double maxWindA4mcTerrainShear = 0.0;
		double maxWindShear = 0.0;
		int aerodynamics4McSamples = 0;
		int trustedSourceSamples = 0;
		int untrustedAerodynamics4McSamples = 0;
		int localVoxelFlowSamples = 0;
		int l0SourceSamples = 0;
		int l1SourceSamples = 0;
		int l2SourceSamples = 0;
		int staleSourceSamples = 0;
		double maxWindSourceFreshnessAgeTicks = 0.0;
		double maxWindSourceMeanSpeed = 0.0;
		double maxWindSourceEffectiveSpeed = 0.0;
		double maxWindSourceGustSpeed = 0.0;
		double maxWindSourceConfidence = 0.0;
		double maxWindSourceTurbulence = 0.0;
		double maxWindSourceQuality = 0.0;
		double maxAbsWindSourcePressureAnomaly = 0.0;
		double maxWindSourceShelter = 0.0;
		double maxWindSourceShear = 0.0;
		double maxAbsWindSourceUpdraft = 0.0;
		double maxAbsWindSourceAblStability = 0.0;
		double maxWindSourceAblMixingStrength = 0.0;
		double maxRotorDiskWindGradient = 0.0;
		double maxRotorA4mcPressureGradientWind = 0.0;
		double maxRotorA4mcShelterObstruction = 0.0;
		double minRotorLocalVoxelObstacleResidual = 1.0;
		double maxCeilingEffect = 1.0;
		double maxEnvironmentAsymmetry = 0.0;
		double maxRotorFlowObstruction = 0.0;
		double minCeilingClearance = Double.POSITIVE_INFINITY;
		double maxAltitude = Double.NEGATIVE_INFINITY;
		double maxLinkLoss = 0.0;
		double maxControlFrameAge = 0.0;
		double maxControlFrameError = 0.0;
		double minRotorHealth = Double.POSITIVE_INFINITY;
		double maxPropStrikeSeverity = 0.0;
		int maxPhysicsSubsteps = 0;
		double maxPhysicsRateHertz = 0.0;
		int propStrikeSamples = 0;
		int propStrikeCount = 0;
		int failsafeSamples = 0;
		int collisionSamples = 0;
		int playableFlightModelSamples = 0;
		int simulationFlightModelSamples = 0;
		double minPlayableLowAltitudeAuthority = 1.0;
		double maxPlayableVisualPitchDegrees = 0.0;
		double maxPlayableVisualRollDegrees = 0.0;
		double maxPlayableVisualYawRateDegreesPerSecond = 0.0;
		int playableNeutralSamples = 0;
		double maxPlayableNeutralVisualPitchDegrees = 0.0;
		double maxPlayableNeutralVisualRollDegrees = 0.0;
		double maxPlayableNeutralVisualYawRateDegreesPerSecond = 0.0;
		double firstPlayableVisualYawDegrees = 0.0;
		double lastPlayableVisualYawDegrees = 0.0;
		boolean hasPlayableVisualYaw = false;

		for (DroneBlackboxSample sample : samples) {
			String[] row = sample.toCsvLine().split(",", -1);
			String flightModel = textValue(row, "flight_model");
			boolean playableFlightModel = "playable".equalsIgnoreCase(flightModel) || "direct".equalsIgnoreCase(flightModel);
			if (playableFlightModel) {
				playableFlightModelSamples++;
			} else if ("simulation".equalsIgnoreCase(flightModel) || "sim".equalsIgnoreCase(flightModel)) {
				simulationFlightModelSamples++;
			}
			maxPhysicsSubsteps = Math.max(maxPhysicsSubsteps, intValue(row, "physics_substeps"));
			maxPhysicsRateHertz = Math.max(maxPhysicsRateHertz, value(row, "physics_rate_hz"));
			minPlayableLowAltitudeAuthority = Math.min(
					minPlayableLowAltitudeAuthority,
					unitOrOne(valueOrDefault(row, "playable_low_altitude_authority", 1.0))
			);
			if (playableFlightModel) {
				maxPlayableVisualPitchDegrees = Math.max(maxPlayableVisualPitchDegrees, Math.abs(value(row, "playable_visual_pitch_deg")));
				maxPlayableVisualRollDegrees = Math.max(maxPlayableVisualRollDegrees, Math.abs(value(row, "playable_visual_roll_deg")));
				maxPlayableVisualYawRateDegreesPerSecond = Math.max(maxPlayableVisualYawRateDegreesPerSecond, Math.abs(value(row, "playable_visual_yaw_rate_dps")));
				if (isPlayableNeutralControlSample(row)) {
					playableNeutralSamples++;
					maxPlayableNeutralVisualPitchDegrees = Math.max(maxPlayableNeutralVisualPitchDegrees, Math.abs(value(row, "playable_visual_pitch_deg")));
					maxPlayableNeutralVisualRollDegrees = Math.max(maxPlayableNeutralVisualRollDegrees, Math.abs(value(row, "playable_visual_roll_deg")));
					maxPlayableNeutralVisualYawRateDegreesPerSecond = Math.max(maxPlayableNeutralVisualYawRateDegreesPerSecond, Math.abs(value(row, "playable_visual_yaw_rate_dps")));
				}
				double playableVisualYawDegrees = value(row, "playable_visual_yaw_deg");
				if (!hasPlayableVisualYaw) {
					firstPlayableVisualYawDegrees = playableVisualYawDegrees;
					hasPlayableVisualYaw = true;
				}
				lastPlayableVisualYawDegrees = playableVisualYawDegrees;
			}
			maxSpeed = Math.max(maxSpeed, value(row, "speed_mps"));
			maxAirspeed = Math.max(maxAirspeed, value(row, "airspeed_mps"));
			maxCurrent = Math.max(maxCurrent, value(row, "battery_current_a"));
			maxRegenCurrent = Math.max(maxRegenCurrent, value(row, "battery_regen_current_a"));
			maxMotorRegenCurrent = Math.max(
					maxMotorRegenCurrent,
					Math.max(value(row, "motor_regen_current_a"), maxIndexedValue(row, "motor_", "_regen_current_a"))
			);
			maxSag = Math.max(maxSag, value(row, "battery_ohmic_sag_v")
					+ value(row, "battery_transient_sag_v")
					+ valueOrDefault(row, "battery_slow_polarization_v", 0.0));
			maxEffectiveResistance = Math.max(maxEffectiveResistance, value(row, "battery_effective_resistance_ohm"));
			maxSocResistanceScale = Math.max(maxSocResistanceScale, valueOrDefault(row, "battery_soc_resistance_scale", 1.0));
			maxTemperatureResistanceScale = Math.max(maxTemperatureResistanceScale, valueOrDefault(row, "battery_temp_resistance_scale", 1.0));
			maxPolarizationResistanceScale = Math.max(maxPolarizationResistanceScale, valueOrDefault(row, "battery_polarization_resistance_scale", 1.0));
			maxVoltageSpike = Math.max(maxVoltageSpike, value(row, "battery_voltage_spike_v"));
			maxBusRipple = Math.max(maxBusRipple, value(row, "battery_bus_ripple_v"));
			maxImuSupplyNoise = Math.max(maxImuSupplyNoise, value(row, "imu_supply_noise"));
			minVoltage = Math.min(minVoltage, value(row, "battery_voltage"));
			minSoc = Math.min(minSoc, value(row, "battery_soc"));
			minCurrentLimit = Math.min(minCurrentLimit, value(row, "battery_current_limit"));
			maxBatteryTemperature = Math.max(maxBatteryTemperature, valueOrDefault(row, "battery_temp_c", 25.0));
			minBatteryThermalLimit = Math.min(minBatteryThermalLimit, valueOrDefault(row, "battery_thermal_limit", 1.0));
			maxPropwash = Math.max(maxPropwash, value(row, "propwash_intensity"));
			maxVrs = Math.max(maxVrs, value(row, "vortex_ring_state"));
			maxVrsThrustBuffet = Math.max(
					maxVrsThrustBuffet,
					Math.max(
							valueOrDefault(row, "vortex_ring_thrust_buffet", 0.0),
							valueOrDefault(row, "vortex_ring_max_thrust_buffet", 0.0)
					)
			);
			maxVrsBuffetForce = Math.max(maxVrsBuffetForce, valueOrDefault(row, "vortex_ring_buffet_force_n", 0.0));
			maxRotorInducedVelocity = Math.max(maxRotorInducedVelocity, value(row, "rotor_induced_velocity_mps"));
			minRotorInducedLagThrustScale = Math.min(
					minRotorInducedLagThrustScale,
					valueOrDefault(row, "rotor_induced_lag_thrust_scale", 1.0)
			);
			maxRotorTranslationalLift = Math.max(maxRotorTranslationalLift, value(row, "rotor_translational_lift"));
			maxRotorAdvanceRatio = Math.max(maxRotorAdvanceRatio, value(row, "rotor_advance_ratio"));
			maxRotorPropellerAdvanceRatioJ = Math.max(
					maxRotorPropellerAdvanceRatioJ,
					Math.max(value(row, "rotor_prop_advance_ratio_j"), maxIndexedValue(row, "rotor_", "_prop_advance_ratio_j"))
			);
			minRotorPropellerThrustScale = Math.min(
					minRotorPropellerThrustScale,
					Math.min(
							valueOrDefault(row, "rotor_prop_thrust_scale", 1.0),
							minIndexedValue(row, "rotor_", "_prop_thrust_scale", 1.0)
					)
			);
			minRotorPropellerPowerScale = Math.min(
					minRotorPropellerPowerScale,
					Math.min(
							valueOrDefault(row, "rotor_prop_power_scale", 1.0),
							minIndexedValue(row, "rotor_", "_prop_power_scale", 1.0)
					)
			);
			minRotorAxialGustThrustScale = Math.min(
					minRotorAxialGustThrustScale,
					Math.min(
							valueOrDefault(row, "rotor_axial_gust_thrust_scale", 1.0),
							minIndexedValue(row, "rotor_", "_axial_gust_thrust_scale", 1.0)
					)
			);
			maxRotorAxialGustThrustScale = Math.max(
					maxRotorAxialGustThrustScale,
					Math.max(
							valueOrDefault(row, "rotor_axial_gust_thrust_scale", 1.0),
							maxIndexedValue(row, "rotor_", "_axial_gust_thrust_scale")
					)
			);
			maxRotorReverseFlow = Math.max(
					maxRotorReverseFlow,
					Math.max(value(row, "rotor_reverse_flow_fraction"), maxIndexedValue(row, "rotor_", "_reverse_flow_fraction"))
			);
			maxRotorTipMach = Math.max(maxRotorTipMach, value(row, "rotor_tip_mach"));
			minRotorCompressibilityThrustScale = Math.min(
					minRotorCompressibilityThrustScale,
					Math.min(
							valueOrDefault(row, "rotor_compressibility_thrust_scale", 1.0),
							minIndexedValue(row, "rotor_", "_compressibility_thrust_scale", 1.0)
					)
			);
			maxRotorLowReynoldsLoss = Math.max(
					maxRotorLowReynoldsLoss,
					Math.max(value(row, "rotor_low_reynolds_loss"), maxIndexedValue(row, "rotor_", "_low_reynolds_loss"))
			);
			maxRotorBladePassRipple = Math.max(
					maxRotorBladePassRipple,
					Math.max(value(row, "rotor_blade_pass_ripple"), maxIndexedValue(row, "rotor_", "_blade_pass_ripple"))
			);
			maxRotorAerodynamicLoad = Math.max(maxRotorAerodynamicLoad, value(row, "rotor_aerodynamic_load"));
			maxRotorDiskGradientThrustLoss = Math.max(
					maxRotorDiskGradientThrustLoss,
					Math.max(
							valueOrDefault(row, "rotor_disk_gradient_thrust_loss", 0.0),
							maxIndexedValue(row, "rotor_", "_disk_gradient_thrust_loss")
					)
			);
			maxRotorDiskGradientLoad = Math.max(
					maxRotorDiskGradientLoad,
					Math.max(
							valueOrDefault(row, "rotor_disk_gradient_load", 0.0),
							maxIndexedValue(row, "rotor_", "_disk_gradient_load")
					)
			);
			maxRotorDiskGradientVibration = Math.max(
					maxRotorDiskGradientVibration,
					Math.max(
							valueOrDefault(row, "rotor_disk_gradient_vibration", 0.0),
							maxIndexedValue(row, "rotor_", "_disk_gradient_vibration")
					)
			);
			maxRotorDiskGradientStall = Math.max(
					maxRotorDiskGradientStall,
					Math.max(
							valueOrDefault(row, "rotor_disk_gradient_stall", 0.0),
							maxIndexedValue(row, "rotor_", "_disk_gradient_stall")
					)
			);
			maxRotorInPlaneDragForce = Math.max(
					maxRotorInPlaneDragForce,
					Math.max(value(row, "rotor_in_plane_drag_force_n"), maxIndexedValue(row, "rotor_", "_in_plane_drag_force_n"))
			);
			maxMotorMechanicalLoss = Math.max(
					maxMotorMechanicalLoss,
					Math.max(value(row, "avg_motor_mechanical_loss_torque_nm"), maxIndexedValue(row, "motor_", "_mechanical_loss_torque_nm"))
			);
			maxMotorTrackingError = Math.max(
					maxMotorTrackingError,
					Math.max(value(row, "avg_motor_tracking_error"), maxIndexedValue(row, "motor_", "_tracking_error"))
			);
			minMotorActuatorAuthority = Math.min(
					minMotorActuatorAuthority,
					Math.min(valueOrDefault(row, "avg_motor_actuator_authority", 1.0), minIndexedValue(row, "motor_", "_actuator_authority", 1.0))
			);
			maxRotorInflowSkew = Math.max(maxRotorInflowSkew, value(row, "rotor_inflow_skew"));
			double bladeDissymmetryPitch = value(row, "rotor_blade_dissymmetry_pitch_torque_nm");
			double bladeDissymmetryYaw = value(row, "rotor_blade_dissymmetry_yaw_torque_nm");
			double bladeDissymmetryRoll = value(row, "rotor_blade_dissymmetry_roll_torque_nm");
			maxRotorBladeDissymmetryTorque = Math.max(
					maxRotorBladeDissymmetryTorque,
					Math.sqrt(bladeDissymmetryPitch * bladeDissymmetryPitch
							+ bladeDissymmetryYaw * bladeDissymmetryYaw
							+ bladeDissymmetryRoll * bladeDissymmetryRoll)
			);
			maxRotorWakeInterference = Math.max(maxRotorWakeInterference, value(row, "rotor_wake_interference"));
			maxRotorCoaxialLoadBias = Math.max(maxRotorCoaxialLoadBias, value(row, "rotor_coaxial_load_bias"));
			maxRotorCoaxialLoadBiasTarget = Math.max(
					maxRotorCoaxialLoadBiasTarget,
					valueOrDefault(row, "rotor_coaxial_load_bias_target", 0.0)
			);
			maxRotorCoaxialLoadBiasClipping = Math.max(
					maxRotorCoaxialLoadBiasClipping,
					valueOrDefault(row, "rotor_coaxial_load_bias_clipping", 0.0)
			);
			maxRotorCoaxialAllocationLoadFraction = Math.max(
					maxRotorCoaxialAllocationLoadFraction,
					valueOrDefault(row, "rotor_coaxial_allocation_load", 0.0)
			);
			maxRotorCoaxialAllocationCommandRatio = Math.max(
					maxRotorCoaxialAllocationCommandRatio,
					valueOrDefault(row, "rotor_coaxial_allocation_ratio", 1.0)
			);
			maxRotorCoaxialAllocationMechanicalGain = Math.max(
					maxRotorCoaxialAllocationMechanicalGain,
					valueOrDefault(row, "rotor_coaxial_allocation_mech_gain_pct", 0.0)
			);
			maxRotorCoaxialAllocationElectricalGain = Math.max(
					maxRotorCoaxialAllocationElectricalGain,
					valueOrDefault(row, "rotor_coaxial_allocation_elec_gain_pct", 0.0)
			);
			maxRotorCoaxialAllocationUncertainty = Math.max(
					maxRotorCoaxialAllocationUncertainty,
					valueOrDefault(row, "rotor_coaxial_allocation_uncertainty_pct", 0.0)
			);
			minRotorWetThrustScale = Math.min(
					minRotorWetThrustScale,
					Math.min(
							valueOrDefault(row, "rotor_wet_thrust_scale", 1.0),
							minIndexedValue(row, "rotor_", "_wet_thrust_scale", 1.0)
					)
			);
			maxRotorIcingSeverity = Math.max(
					maxRotorIcingSeverity,
					Math.max(
							valueOrDefault(row, "rotor_icing_severity", 0.0),
							maxIndexedValue(row, "rotor_", "_icing_severity")
					)
			);
			minRotorIcingThrustScale = Math.min(
					minRotorIcingThrustScale,
					Math.min(
							valueOrDefault(row, "rotor_icing_thrust_scale", 1.0),
							minIndexedValue(row, "rotor_", "_icing_thrust_scale", 1.0)
					)
			);
			maxRotorIcingPowerScale = Math.max(
					maxRotorIcingPowerScale,
					Math.max(
							valueOrDefault(row, "rotor_icing_power_scale", 1.0),
							maxIndexedValue(row, "rotor_", "_icing_power_scale")
					)
			);
			maxRotorWakeSwirlVelocity = Math.max(
					maxRotorWakeSwirlVelocity,
					Math.max(value(row, "rotor_wake_swirl_mps"), maxIndexedValue(row, "rotor_", "_wake_swirl_mps"))
			);
			maxRotorWindmilling = Math.max(
					maxRotorWindmilling,
					Math.max(value(row, "rotor_windmilling"), maxIndexedValue(row, "rotor_", "_windmilling"))
			);
			double wakeSwirlTorquePitch = value(row, "rotor_wake_swirl_pitch_torque_nm");
			double wakeSwirlTorqueYaw = value(row, "rotor_wake_swirl_yaw_torque_nm");
			double wakeSwirlTorqueRoll = value(row, "rotor_wake_swirl_roll_torque_nm");
			maxRotorWakeSwirlTorque = Math.max(
					maxRotorWakeSwirlTorque,
					Math.sqrt(wakeSwirlTorquePitch * wakeSwirlTorquePitch
							+ wakeSwirlTorqueYaw * wakeSwirlTorqueYaw
							+ wakeSwirlTorqueRoll * wakeSwirlTorqueRoll)
			);
			double activeBrakingTorquePitch = value(row, "rotor_active_braking_pitch_torque_nm");
			double activeBrakingTorqueYaw = value(row, "rotor_active_braking_yaw_torque_nm");
			double activeBrakingTorqueRoll = value(row, "rotor_active_braking_roll_torque_nm");
			maxRotorActiveBrakingTorque = Math.max(
					maxRotorActiveBrakingTorque,
					Math.sqrt(activeBrakingTorquePitch * activeBrakingTorquePitch
							+ activeBrakingTorqueYaw * activeBrakingTorqueYaw
							+ activeBrakingTorqueRoll * activeBrakingTorqueRoll)
			);
			double accelerationReactionTorquePitch = value(row, "rotor_acceleration_reaction_pitch_torque_nm");
			double accelerationReactionTorqueYaw = value(row, "rotor_acceleration_reaction_yaw_torque_nm");
			double accelerationReactionTorqueRoll = value(row, "rotor_acceleration_reaction_roll_torque_nm");
			maxRotorAccelerationReactionTorque = Math.max(
					maxRotorAccelerationReactionTorque,
					Math.sqrt(accelerationReactionTorquePitch * accelerationReactionTorquePitch
							+ accelerationReactionTorqueYaw * accelerationReactionTorqueYaw
							+ accelerationReactionTorqueRoll * accelerationReactionTorqueRoll)
			);
			double gyroscopicTorquePitch = value(row, "rotor_gyroscopic_pitch_torque_nm");
			double gyroscopicTorqueYaw = value(row, "rotor_gyroscopic_yaw_torque_nm");
			double gyroscopicTorqueRoll = value(row, "rotor_gyroscopic_roll_torque_nm");
			maxRotorGyroscopicTorque = Math.max(
					maxRotorGyroscopicTorque,
					Math.sqrt(gyroscopicTorquePitch * gyroscopicTorquePitch
							+ gyroscopicTorqueYaw * gyroscopicTorqueYaw
							+ gyroscopicTorqueRoll * gyroscopicTorqueRoll)
			);
			double flappingTorquePitch = value(row, "rotor_flapping_pitch_torque_nm");
			double flappingTorqueYaw = value(row, "rotor_flapping_yaw_torque_nm");
			double flappingTorqueRoll = value(row, "rotor_flapping_roll_torque_nm");
			maxRotorFlappingTorque = Math.max(
					maxRotorFlappingTorque,
					Math.sqrt(flappingTorquePitch * flappingTorquePitch
							+ flappingTorqueYaw * flappingTorqueYaw
							+ flappingTorqueRoll * flappingTorqueRoll)
			);
			double rotorAngularDragPitch = value(row, "rotor_angular_drag_pitch_torque_nm");
			double rotorAngularDragYaw = value(row, "rotor_angular_drag_yaw_torque_nm");
			double rotorAngularDragRoll = value(row, "rotor_angular_drag_roll_torque_nm");
			maxRotorAngularDrag = Math.max(
					maxRotorAngularDrag,
					Math.sqrt(rotorAngularDragPitch * rotorAngularDragPitch + rotorAngularDragYaw * rotorAngularDragYaw + rotorAngularDragRoll * rotorAngularDragRoll)
			);
			double angularDragPitch = value(row, "airframe_angular_drag_pitch_torque_nm");
			double angularDragYaw = value(row, "airframe_angular_drag_yaw_torque_nm");
			double angularDragRoll = value(row, "airframe_angular_drag_roll_torque_nm");
			maxAirframeAngularDrag = Math.max(
					maxAirframeAngularDrag,
					Math.sqrt(angularDragPitch * angularDragPitch + angularDragYaw * angularDragYaw + angularDragRoll * angularDragRoll)
			);
			maxAirframeSeparation = Math.max(maxAirframeSeparation, value(row, "airframe_separation"));
			maxAirframeLift = Math.max(maxAirframeLift, value(row, "airframe_lift_n"));
			maxAirframeBodyDrag = Math.max(maxAirframeBodyDrag, valueOrDefault(row, "airframe_body_drag_n", 0.0));
			maxLinearDampingDrag = Math.max(maxLinearDampingDrag, valueOrDefault(row, "linear_damping_drag_n", 0.0));
			maxGroundEffectDrag = Math.max(maxGroundEffectDrag, value(row, "ground_effect_drag_n"));
			maxGroundEffectLevelingTorque = Math.max(
					maxGroundEffectLevelingTorque,
					valueOrDefault(row, "ground_effect_leveling_torque_nm", 0.0)
			);
			maxRotorWashDrag = Math.max(maxRotorWashDrag, value(row, "rotor_wash_drag_n"));
			maxRotorWallEffect = Math.max(maxRotorWallEffect, value(row, "rotor_wall_effect_n"));
			maxContactImpact = Math.max(maxContactImpact, value(row, "contact_impact_mps"));
			maxContactSlip = Math.max(maxContactSlip, value(row, "contact_slip_mps"));
			maxContactBounce = Math.max(maxContactBounce, value(row, "contact_bounce_mps"));
			double contactSurfaceFriction = valueOrDefault(row, "contact_surface_friction", 1.0);
			double contactSurfaceRestitution = valueOrDefault(row, "contact_surface_restitution", 1.0);
			double contactSurfaceScrape = valueOrDefault(row, "contact_surface_scrape", 1.0);
			minContactSurfaceFriction = Math.min(minContactSurfaceFriction, contactSurfaceFriction);
			maxContactSurfaceFriction = Math.max(maxContactSurfaceFriction, contactSurfaceFriction);
			minContactSurfaceRestitution = Math.min(minContactSurfaceRestitution, contactSurfaceRestitution);
			maxContactSurfaceRestitution = Math.max(maxContactSurfaceRestitution, contactSurfaceRestitution);
			minContactSurfaceScrape = Math.min(minContactSurfaceScrape, contactSurfaceScrape);
			maxContactSurfaceScrape = Math.max(maxContactSurfaceScrape, contactSurfaceScrape);
			maxContactAngularImpulse = Math.max(maxContactAngularImpulse, value(row, "contact_angular_impulse_dps"));
			maxBarometerError = Math.max(maxBarometerError, Math.abs(value(row, "barometer_error_m")));
			maxBarometerPressurePortError = Math.max(maxBarometerPressurePortError, Math.abs(value(row, "barometer_pressure_port_error_m")));
			maxBarometerPropwashError = Math.max(maxBarometerPropwashError, Math.abs(value(row, "barometer_propwash_error_m")));
			minBarometerPressure = Math.min(minBarometerPressure, value(row, "barometer_pressure_hpa"));
			maxRotorStall = Math.max(maxRotorStall, value(row, "rotor_stall_intensity"));
			maxRotorVibration = Math.max(maxRotorVibration, value(row, "rotor_vibration"));
			maxRotorDamageVibration = Math.max(
					maxRotorDamageVibration,
					Math.max(
							valueOrDefault(row, "rotor_damage_vibration", 0.0),
							maxIndexedValue(row, "rotor_", "_damage_vibration")
					)
			);
			maxRotorConing = Math.max(maxRotorConing, value(row, "rotor_coning"));
			maxRotorConingAngle = Math.max(
					maxRotorConingAngle,
					Math.max(value(row, "rotor_coning_angle_deg"), maxIndexedValue(row, "rotor_", "_coning_angle_deg"))
			);
			maxRotorFlappingTilt = Math.max(
					maxRotorFlappingTilt,
					Math.max(value(row, "rotor_flapping_tilt_deg"), maxIndexedValue(row, "rotor_", "_flapping_tilt_deg"))
			);
			maxRotorArmFlex = Math.max(maxRotorArmFlex, value(row, "rotor_arm_flex"));
			maxRotorArmFlexDeflection = Math.max(
					maxRotorArmFlexDeflection,
					Math.max(value(row, "rotor_arm_flex_deflection_mm"), maxIndexedValue(row, "rotor_", "_arm_flex_deflection_mm"))
			);
			maxRotorArmFlexTilt = Math.max(
					maxRotorArmFlexTilt,
					Math.max(value(row, "rotor_arm_flex_tilt_deg"), maxIndexedValue(row, "rotor_", "_arm_flex_tilt_deg"))
			);
			maxRotorSurfaceScrape = Math.max(maxRotorSurfaceScrape, value(row, "rotor_surface_scrape"));
			maxMixer = Math.max(maxMixer, value(row, "mixer_saturation"));
			maxMixerLowSaturation = Math.max(maxMixerLowSaturation, valueOrDefault(row, "mixer_low_saturation", 0.0));
			maxMixerHighSaturation = Math.max(maxMixerHighSaturation, valueOrDefault(row, "mixer_high_saturation", 0.0));
			minMixerLowHeadroom = Math.min(minMixerLowHeadroom, valueOrDefault(row, "mixer_low_headroom", 1.0));
			minMixerHighHeadroom = Math.min(minMixerHighHeadroom, valueOrDefault(row, "mixer_high_headroom", 1.0));
			minMixerAxisAuthority = Math.min(
					minMixerAxisAuthority,
					valueOrDefault(row, "mixer_min_axis_authority", 1.0)
			);
			maxMotorTemp = Math.max(maxMotorTemp, value(row, "motor_temp_c"));
			minMotorElectricalEfficiency = Math.min(
					minMotorElectricalEfficiency,
					minPositiveIndexedValue(row, "motor_", "_electrical_efficiency", Double.POSITIVE_INFINITY)
			);
			minMotorVoltageHeadroom = Math.min(
					minMotorVoltageHeadroom,
					minIndexedValue(row, "motor_", "_voltage_headroom", Double.POSITIVE_INFINITY)
			);
			maxMotorWindingResistanceScale = Math.max(
					maxMotorWindingResistanceScale,
					Math.max(
							valueOrDefault(row, "motor_winding_resistance_scale", 1.0),
							maxIndexedValue(row, "motor_", "_winding_resistance_scale")
					)
			);
			maxEscTemp = Math.max(maxEscTemp, value(row, "max_esc_temp_c"));
			minEscThermalLimit = Math.min(minEscThermalLimit, value(row, "esc_thermal_limit"));
			maxEscDesync = Math.max(maxEscDesync, value(row, "esc_desync"));
			maxWake = Math.max(maxWake, value(row, "drone_wake_intensity"));
			maxWaterImmersion = Math.max(
					maxWaterImmersion,
					Math.max(value(row, "water_immersion"), maxIndexedValue(row, "rotor_", "_water_immersion"))
			);
			maxPrecipitationWetness = Math.max(
					maxPrecipitationWetness,
					Math.max(value(row, "precipitation_wetness"), maxIndexedValue(row, "rotor_", "_precipitation_wetness"))
			);
			double ambientTemperature = value(row, "ambient_temperature_c");
			minAmbientTemperature = Math.min(minAmbientTemperature, ambientTemperature);
			maxAmbientTemperature = Math.max(maxAmbientTemperature, ambientTemperature);
			maxWindGust = Math.max(maxWindGust, value(row, "wind_gust_speed_mps"));
			maxWindDryden = Math.max(maxWindDryden, valueOrDefault(row, "wind_dryden_speed_mps", 0.0));
			maxWindBurble = Math.max(maxWindBurble, valueOrDefault(row, "wind_burble_speed_mps", 0.0));
			maxWindA4mcSourceGust = Math.max(maxWindA4mcSourceGust, valueOrDefault(row, "wind_a4mc_source_gust_speed_mps", 0.0));
			maxAbsWindA4mcUpdraft = Math.max(maxAbsWindA4mcUpdraft, Math.abs(valueOrDefault(row, "wind_a4mc_updraft_mps", 0.0)));
			maxWindA4mcTerrainShear = Math.max(maxWindA4mcTerrainShear, valueOrDefault(row, "wind_a4mc_terrain_shear_speed_mps", 0.0));
			maxWindShear = Math.max(maxWindShear, value(row, "wind_shear_accel_mps2"));
			boolean aerodynamics4McSource = "aerodynamics4mc".equalsIgnoreCase(textValue(row, "wind_source"));
			boolean trustedSource = boolValue(row, "wind_source_trusted");
			if (aerodynamics4McSource) {
				aerodynamics4McSamples++;
			}
			if (trustedSource) {
				trustedSourceSamples++;
			}
			if (aerodynamics4McSource && !trustedSource) {
				untrustedAerodynamics4McSamples++;
			}
			if (boolValue(row, "wind_source_local_voxel_flow")) {
				localVoxelFlowSamples++;
			}
			String sourceLevel = textValue(row, "wind_source_level").toLowerCase(Locale.ROOT);
			if ("l0".equals(sourceLevel)) {
				l0SourceSamples++;
			} else if ("l1".equals(sourceLevel)) {
				l1SourceSamples++;
			} else if ("l2".equals(sourceLevel)) {
				l2SourceSamples++;
			}
			double sourceAgeTicks = valueOrDefault(row, "wind_source_freshness_age_ticks", -1.0);
			if (sourceAgeTicks >= 0.0) {
				maxWindSourceFreshnessAgeTicks = Math.max(maxWindSourceFreshnessAgeTicks, sourceAgeTicks);
				if (sourceAgeTicks > WIND_SOURCE_STALE_AGE_TICKS) {
					staleSourceSamples++;
				}
			}
			maxWindSourceMeanSpeed = Math.max(maxWindSourceMeanSpeed, valueOrDefault(row, "wind_source_mean_speed_mps", 0.0));
			maxWindSourceEffectiveSpeed = Math.max(maxWindSourceEffectiveSpeed, valueOrDefault(row, "wind_source_effective_speed_mps", 0.0));
			maxWindSourceGustSpeed = Math.max(maxWindSourceGustSpeed, valueOrDefault(row, "wind_source_gust_speed_mps", 0.0));
			maxWindSourceConfidence = Math.max(maxWindSourceConfidence, valueOrDefault(row, "wind_source_confidence", 0.0));
			maxWindSourceTurbulence = Math.max(maxWindSourceTurbulence, valueOrDefault(row, "wind_source_turbulence", 0.0));
			maxWindSourceQuality = Math.max(maxWindSourceQuality, valueOrDefault(row, "wind_source_quality", 0.0));
			maxAbsWindSourcePressureAnomaly = Math.max(maxAbsWindSourcePressureAnomaly, Math.abs(valueOrDefault(row, "wind_source_pressure_anomaly_pa", 0.0)));
			maxWindSourceShelter = Math.max(maxWindSourceShelter, valueOrDefault(row, "wind_source_shelter_factor", 0.0));
			maxWindSourceShear = Math.max(maxWindSourceShear, valueOrDefault(row, "wind_source_shear_mag_per_block", 0.0));
			maxAbsWindSourceUpdraft = Math.max(maxAbsWindSourceUpdraft, Math.abs(valueOrDefault(row, "wind_source_updraft_mps", 0.0)));
			maxAbsWindSourceAblStability = Math.max(maxAbsWindSourceAblStability, Math.abs(valueOrDefault(row, "wind_source_abl_stability", 0.0)));
			maxWindSourceAblMixingStrength = Math.max(maxWindSourceAblMixingStrength, valueOrDefault(row, "wind_source_abl_mixing_strength", 0.0));
			maxRotorDiskWindGradient = Math.max(
					maxRotorDiskWindGradient,
					Math.max(
							valueOrDefault(row, "rotor_disk_wind_gradient_mps", 0.0),
							maxIndexedValue(row, "rotor_", "_disk_wind_gradient_mps")
					)
			);
			maxRotorA4mcPressureGradientWind = Math.max(
					maxRotorA4mcPressureGradientWind,
					Math.max(
							valueOrDefault(row, "rotor_a4mc_pressure_gradient_wind_mps", 0.0),
							maxIndexedValue(row, "rotor_", "_a4mc_pressure_gradient_wind_mps")
					)
			);
			maxRotorA4mcShelterObstruction = Math.max(
					maxRotorA4mcShelterObstruction,
					Math.max(
							valueOrDefault(row, "rotor_a4mc_shelter_obstruction", 0.0),
							maxIndexedValue(row, "rotor_", "_a4mc_shelter_obstruction")
					)
			);
			minRotorLocalVoxelObstacleResidual = Math.min(
					minRotorLocalVoxelObstacleResidual,
					Math.min(
							valueOrDefault(row, "rotor_local_voxel_obstacle_residual", 1.0),
							minIndexedValue(row, "rotor_", "_local_voxel_obstacle_residual", 1.0)
					)
			);
			maxCeilingEffect = Math.max(maxCeilingEffect, value(row, "ceiling_effect_multiplier"));
			maxEnvironmentAsymmetry = Math.max(maxEnvironmentAsymmetry, value(row, "env_thrust_asymmetry"));
			maxRotorFlowObstruction = Math.max(maxRotorFlowObstruction, value(row, "rotor_flow_obstruction"));
			double ceilingClearance = value(row, "ceiling_clearance_m");
			if (ceilingClearance >= 0.0) {
				minCeilingClearance = Math.min(minCeilingClearance, ceilingClearance);
			}
			maxAltitude = Math.max(maxAltitude, value(row, "y"));
			maxLinkLoss = Math.max(maxLinkLoss, value(row, "control_link_loss_s"));
			maxControlFrameAge = Math.max(maxControlFrameAge, value(row, "control_frame_age_s"));
			maxControlFrameError = Math.max(maxControlFrameError, value(row, "control_frame_error"));
			minRotorHealth = Math.min(minRotorHealth, minIndexedValue(row, "rotor_", "_health", 1.0));
			maxPropStrikeSeverity = Math.max(maxPropStrikeSeverity, value(row, "prop_strike_severity"));
			maxPropStrikeSeverity = Math.max(maxPropStrikeSeverity, maxIndexedValue(row, "prop_strike_", "_severity"));
			propStrikeCount = Math.max(propStrikeCount, intValue(row, "prop_strike_count"));
			if (boolValue(row, "prop_strike")) {
				propStrikeSamples++;
			}
			if (boolValue(row, "control_failsafe")) {
				failsafeSamples++;
			}
			if (value(row, "collision_severity") > 0.001 || value(row, "contact_impact_mps") > 0.001) {
				collisionSamples++;
			}
		}

		DroneBlackboxSummary summary = new DroneBlackboxSummary(
				samples.size(),
				samples.size() / TICKS_PER_SECOND,
				maxPhysicsSubsteps,
				maxPhysicsRateHertz,
				maxSpeed,
				maxAirspeed,
				maxCurrent,
				maxRegenCurrent,
				maxMotorRegenCurrent,
				maxSag,
				maxEffectiveResistance,
				maxSocResistanceScale,
				maxTemperatureResistanceScale,
				maxPolarizationResistanceScale,
				maxVoltageSpike,
				maxBusRipple,
				maxImuSupplyNoise,
				finiteOrZero(minVoltage),
				finiteOrZero(minSoc),
				finiteOrZero(minCurrentLimit),
				maxBatteryTemperature,
				finiteOrZero(minBatteryThermalLimit),
				maxPropwash,
				maxVrs,
				maxVrsThrustBuffet,
				maxVrsBuffetForce,
				maxRotorInducedVelocity,
				finiteOrOne(minRotorInducedLagThrustScale),
				maxRotorTranslationalLift,
				maxRotorAdvanceRatio,
				maxRotorPropellerAdvanceRatioJ,
				finiteOrOne(minRotorPropellerThrustScale),
				finiteOrOne(minRotorPropellerPowerScale),
				new AxialGustStats(minRotorAxialGustThrustScale, maxRotorAxialGustThrustScale),
				maxRotorReverseFlow,
				maxRotorTipMach,
				finiteOrOne(minRotorCompressibilityThrustScale),
				maxRotorLowReynoldsLoss,
				maxRotorBladePassRipple,
				maxRotorAerodynamicLoad,
				maxRotorInPlaneDragForce,
				maxMotorMechanicalLoss,
				maxMotorTrackingError,
				finiteOrOne(minMotorActuatorAuthority),
				maxRotorInflowSkew,
				maxRotorBladeDissymmetryTorque,
				maxRotorWakeInterference,
				maxRotorCoaxialLoadBias,
				maxRotorCoaxialLoadBiasTarget,
				maxRotorCoaxialLoadBiasClipping,
				maxRotorCoaxialAllocationLoadFraction,
				maxRotorCoaxialAllocationCommandRatio,
				maxRotorCoaxialAllocationMechanicalGain,
				maxRotorCoaxialAllocationElectricalGain,
				maxRotorCoaxialAllocationUncertainty,
				finiteOrOne(minRotorWetThrustScale),
				maxRotorWakeSwirlVelocity,
				maxRotorWindmilling,
				maxRotorWakeSwirlTorque,
				maxRotorActiveBrakingTorque,
				maxRotorAccelerationReactionTorque,
				maxRotorGyroscopicTorque,
				maxRotorFlappingTorque,
				maxRotorAngularDrag,
				maxAirframeAngularDrag,
				maxAirframeSeparation,
				maxAirframeLift,
				maxAirframeBodyDrag,
				maxLinearDampingDrag,
				maxGroundEffectDrag,
				maxGroundEffectLevelingTorque,
				maxRotorWashDrag,
				maxRotorWallEffect,
				maxContactImpact,
				maxContactSlip,
				maxContactBounce,
				finiteOrOne(minContactSurfaceFriction),
				finiteOrOne(maxContactSurfaceFriction),
				finiteOrOne(minContactSurfaceRestitution),
				finiteOrOne(maxContactSurfaceRestitution),
				finiteOrOne(minContactSurfaceScrape),
				finiteOrOne(maxContactSurfaceScrape),
				maxContactAngularImpulse,
				maxBarometerError,
				maxBarometerPropwashError,
				finiteOrZero(minBarometerPressure),
				maxRotorStall,
				maxRotorVibration,
				maxRotorDamageVibration,
				maxRotorConing,
				maxRotorConingAngle,
				maxRotorFlappingTilt,
				maxRotorArmFlex,
				maxRotorArmFlexDeflection,
				maxRotorArmFlexTilt,
				maxRotorSurfaceScrape,
				maxMixer,
				maxMixerLowSaturation,
				maxMixerHighSaturation,
				finiteOrOne(minMixerLowHeadroom),
				finiteOrOne(minMixerHighHeadroom),
				finiteOrOne(minMixerAxisAuthority),
				maxMotorTemp,
				finiteOrZero(minMotorElectricalEfficiency),
				finiteOrZero(minMotorVoltageHeadroom),
				maxMotorWindingResistanceScale,
				maxEscTemp,
				finiteOrZero(minEscThermalLimit),
				maxEscDesync,
				maxWake,
				maxWaterImmersion,
				maxPrecipitationWetness,
				finiteOrZero(minAmbientTemperature),
				finiteOrZero(maxAmbientTemperature),
				maxWindGust,
				new WindSplit(maxWindDryden, maxWindBurble, maxWindA4mcSourceGust, maxAbsWindA4mcUpdraft, maxWindA4mcTerrainShear),
				maxWindShear,
				maxCeilingEffect,
				maxEnvironmentAsymmetry,
				maxRotorFlowObstruction,
				finiteOrZero(minCeilingClearance),
				finiteOrZero(maxAltitude),
				maxLinkLoss,
				maxControlFrameAge,
				maxControlFrameError,
				finiteOrZero(minRotorHealth),
				maxPropStrikeSeverity,
				propStrikeSamples,
				propStrikeCount,
				failsafeSamples,
				collisionSamples
		);
		ICING_STATS.put(summary, new IcingStats(
				maxRotorIcingSeverity,
				finiteOrOne(minRotorIcingThrustScale),
				maxRotorIcingPowerScale
		));
		DISK_GRADIENT_STATS.put(summary, new DiskGradientStats(
				maxRotorDiskGradientThrustLoss,
				maxRotorDiskGradientLoad,
				maxRotorDiskGradientVibration,
				maxRotorDiskGradientStall
		));
		BAROMETER_STATS.put(summary, new BarometerStats(maxBarometerPressurePortError));
		FLIGHT_MODEL_STATS.put(summary, new FlightModelStats(
				playableFlightModelSamples,
				simulationFlightModelSamples
		));
		LOW_ALTITUDE_STATS.put(summary, new LowAltitudeStats(minPlayableLowAltitudeAuthority));
		PLAYABLE_VISUAL_STATS.put(summary, new PlayableVisualStats(
				maxPlayableVisualPitchDegrees,
				maxPlayableVisualRollDegrees,
				maxPlayableVisualYawRateDegreesPerSecond,
				hasPlayableVisualYaw ? angleDifferenceDegrees(lastPlayableVisualYawDegrees, firstPlayableVisualYawDegrees) : 0.0
		));
		PLAYABLE_NEUTRAL_STATS.put(summary, new PlayableNeutralStats(
				playableNeutralSamples,
				maxPlayableNeutralVisualPitchDegrees,
				maxPlayableNeutralVisualRollDegrees,
				maxPlayableNeutralVisualYawRateDegreesPerSecond
		));
		WIND_SOURCE_STATS.put(summary, new WindSourceStats(
				aerodynamics4McSamples,
				trustedSourceSamples,
				untrustedAerodynamics4McSamples,
				localVoxelFlowSamples,
				l0SourceSamples,
				l1SourceSamples,
				l2SourceSamples,
				staleSourceSamples,
				maxWindSourceFreshnessAgeTicks,
				maxWindSourceMeanSpeed,
				maxWindSourceEffectiveSpeed,
				maxWindSourceGustSpeed,
				maxWindSourceConfidence,
				maxWindSourceTurbulence,
				maxWindSourceQuality,
				maxAbsWindSourcePressureAnomaly,
				maxWindSourceShelter,
				maxWindSourceShear,
				maxAbsWindSourceUpdraft,
				maxAbsWindSourceAblStability,
				maxWindSourceAblMixingStrength,
				maxRotorDiskWindGradient,
				maxRotorA4mcPressureGradientWind,
				maxRotorA4mcShelterObstruction,
				minRotorLocalVoxelObstacleResidual
		));
		return summary;
	}

	public boolean hasSamples() {
		return sampleCount > 0;
	}

	public int playableFlightModelSamples() {
		return flightModelStats().playableSamples();
	}

	public int simulationFlightModelSamples() {
		return flightModelStats().simulationSamples();
	}

	private FlightModelStats flightModelStats() {
		return FLIGHT_MODEL_STATS.getOrDefault(this, EMPTY_FLIGHT_MODEL_STATS);
	}

	public double minPlayableLowAltitudeAuthority() {
		return lowAltitudeStats().minPlayableLowAltitudeAuthority();
	}

	public double maxPlayableLowAltitudeSuppressionPercent() {
		return (1.0 - minPlayableLowAltitudeAuthority()) * 100.0;
	}

	private LowAltitudeStats lowAltitudeStats() {
		return LOW_ALTITUDE_STATS.getOrDefault(this, EMPTY_LOW_ALTITUDE_STATS);
	}

	public PlayableVisualStats playableVisualStats() {
		return PLAYABLE_VISUAL_STATS.getOrDefault(this, EMPTY_PLAYABLE_VISUAL_STATS);
	}

	public PlayableNeutralStats playableNeutralStats() {
		return PLAYABLE_NEUTRAL_STATS.getOrDefault(this, EMPTY_PLAYABLE_NEUTRAL_STATS);
	}

	public WindSourceStats windSourceStats() {
		return WIND_SOURCE_STATS.getOrDefault(this, EMPTY_WIND_SOURCE_STATS);
	}

	public double maxWindDrydenSpeedMetersPerSecond() {
		return windSplit == null ? 0.0 : windSplit.maxDrydenSpeedMetersPerSecond();
	}

	public double maxWindBurbleSpeedMetersPerSecond() {
		return windSplit == null ? 0.0 : windSplit.maxBurbleSpeedMetersPerSecond();
	}

	public double maxWindA4mcSourceGustSpeedMetersPerSecond() {
		return windSplit == null ? 0.0 : windSplit.maxA4mcSourceGustSpeedMetersPerSecond();
	}

	public double maxAbsWindA4mcUpdraftMetersPerSecond() {
		return windSplit == null ? 0.0 : windSplit.maxAbsA4mcUpdraftMetersPerSecond();
	}

	public double maxWindA4mcTerrainShearSpeedMetersPerSecond() {
		return windSplit == null ? 0.0 : windSplit.maxA4mcTerrainShearSpeedMetersPerSecond();
	}

	public double minRotorAxialGustThrustScale() {
		return axialGustStats == null ? 1.0 : axialGustStats.minThrustScale();
	}

	public double maxRotorAxialGustThrustScale() {
		return axialGustStats == null ? 1.0 : axialGustStats.maxThrustScale();
	}

	public double maxRotorIcingSeverity() {
		return icingStats().maxRotorIcingSeverity();
	}

	public double minRotorIcingThrustScale() {
		return icingStats().minRotorIcingThrustScale();
	}

	public double maxRotorIcingPowerScale() {
		return icingStats().maxRotorIcingPowerScale();
	}

	private IcingStats icingStats() {
		return ICING_STATS.getOrDefault(this, EMPTY_ICING_STATS);
	}

	public double maxBarometerPressurePortErrorMeters() {
		return barometerStats().maxPressurePortErrorMeters();
	}

	private BarometerStats barometerStats() {
		return BAROMETER_STATS.getOrDefault(this, EMPTY_BAROMETER_STATS);
	}

	public double maxRotorDiskGradientThrustLossFraction() {
		return diskGradientStats().maxThrustLossFraction();
	}

	public double maxRotorDiskGradientLoadFactor() {
		return diskGradientStats().maxLoadFactor();
	}

	public double maxRotorDiskGradientVibration() {
		return diskGradientStats().maxVibration();
	}

	public double maxRotorDiskGradientStallIntensity() {
		return diskGradientStats().maxStallIntensity();
	}

	private DiskGradientStats diskGradientStats() {
		return DISK_GRADIENT_STATS.getOrDefault(this, EMPTY_DISK_GRADIENT_STATS);
	}

	public String formatForChat() {
		if (!hasSamples()) {
			return "Blackbox summary: no samples.";
		}
		IcingStats icingStats = icingStats();
		DiskGradientStats diskGradientStats = diskGradientStats();
		FlightModelStats flightModelStats = flightModelStats();
		PlayableVisualStats playableVisualStats = playableVisualStats();
		WindSourceStats windSourceStats = windSourceStats();
		return String.format(
				Locale.ROOT,
				"Blackbox %.1fs/%d samples | flight playable %d sim %d lowAlt %.0f%% vis %.1f/%.1fdeg yaw %.1fdps drift %.1fdeg | loop %d@%.0fHz | max speed %.2fm/s air %.2fm/s contact %.2f/%.2f/%.2fm/s %.0fd/s surface %.2f..%.2f/%.2f..%.2f/%.2f..%.2f | battery min %.2fV sag %.2fV ir %.1fmOhm irx %.2f/%.2f/%.2f spike %.2fV ripple %.3fV imuP %.2f current %.1fA regen %.1fA motor-regen %.3fA soc %.1f%% current-limit %.2f temp %.1fC batt-limit %.2f | propwash %.2f VRS %.2f vrsbuf %.0f%% vrsF %.2fN ind %.2fm/s iloss %.0f%% ETL %.2f adv %.2f J %.2f pthr %.2f ppwr %.2f agust %.2f..%.2f rev %.2f tipmach %.2f machloss %.0f%% lowre %.2f bpass %.3f load %.2f dg %.1f%%/%.2f/%.2f/%.2f hforce %.2fN mech-loss %.4fNm track %.3f auth %.2f skew %.2f bdiss %.3fNm rwake %.2f coax %.3f target %.3f clip %.3f cload %.2f cratio %.2f cgain %.1f/%.1f%% cunc %.1f%% swirl %.2fm/s wmill %.2f swirlT %.3fNm brakeT %.3fNm accelT %.3fNm gyroT %.3fNm flapT %.3fNm rdamp %.3f ang-drag %.3f sep %.2f lift %.2fN bodyD %.2fN linD %.2fN cushion %.2fN glev %.3fNm wash %.2fN wall %.2fN baro err %.2fm port %.2fm wash %.2fm min %.1fhPa wake %.2f water %.2f rain %.2f wetloss %.0f%% ice %.2f iceloss %.0f%% icepwr %.2f temp %.1f..%.1fC gust %.2fm/s dryden %.2f burble %.2f a4mcsrc %.2f a4mcup %.2f a4mcshr %.2f shear %.2fm/s2 a4mc %d/%d trusted %d untrusted %d l2 %d src %d/%d/%d age %.0ft stale %d srcwind %.2f/%.2f/%.2f conf %.2f srcturb %.2f q %.2f p %.0fPa shelter %.2f srcshear %.2f/m updraft %.2fm/s abl %.2f mix %.2f diskgrad %.2fm/s pgrad %.2fm/s lvoxres %.2f a4mcsh %.2f ceil %.2f/%s asym %.2f block %.2f stall %.2f vib %.2f dvib %.2f coning %.2f/%.1fdeg flap %.1fdeg flex %.2f %.2fmm %.1fdeg scrape %.2f mixer %.2f mix-auth %.2f mix-edge %.2f/%.2f mix-head %.2f/%.2f desync %.2f | motor %.1fC eff %.2f headroom %.2f mR %.2f esc %.1fC limit %.2f rotor min %.1f%% prop-strike %d samples max %.2f count %d | alt %.1fm link-loss %.2fs rc-frame %.3fs err %.4f failsafe %d collision %d",
				durationSeconds,
				sampleCount,
				flightModelStats.playableSamples(),
				flightModelStats.simulationSamples(),
				maxPlayableLowAltitudeSuppressionPercent(),
				playableVisualStats.maxPitchDegrees(),
				playableVisualStats.maxRollDegrees(),
				playableVisualStats.maxYawRateDegreesPerSecond(),
				playableVisualStats.finalYawDriftDegrees(),
				maxPhysicsSubsteps,
				maxPhysicsRateHertz,
				maxSpeedMetersPerSecond,
				maxAirspeedMetersPerSecond,
				maxContactImpactSpeedMetersPerSecond,
				maxContactSlipSpeedMetersPerSecond,
				maxContactBounceSpeedMetersPerSecond,
				maxContactAngularImpulseDegreesPerSecond,
				minContactSurfaceFrictionMultiplier,
				maxContactSurfaceFrictionMultiplier,
				minContactSurfaceRestitutionMultiplier,
				maxContactSurfaceRestitutionMultiplier,
				minContactSurfaceScrapeMultiplier,
				maxContactSurfaceScrapeMultiplier,
				minBatteryVoltage,
				maxBatterySagVoltage,
				maxBatteryEffectiveResistanceOhms * 1000.0,
				maxBatteryStateOfChargeResistanceScale,
				maxBatteryTemperatureResistanceScale,
				maxBatteryPolarizationResistanceScale,
				maxBatteryVoltageSpike,
				maxBatteryBusRippleVoltage,
				maxImuSupplyNoiseIntensity,
				maxBatteryCurrentAmps,
				maxBatteryRegenerativeCurrentAmps,
				maxMotorRegenerativeCurrentAmps,
				minBatteryStateOfCharge * 100.0,
				minBatteryCurrentLimit,
				maxBatteryTemperatureCelsius,
				minBatteryThermalLimit,
				maxPropwashIntensity,
				maxVortexRingState,
				maxVortexRingThrustBuffetAmplitude * 100.0,
				maxVortexRingBuffetForceNewtons,
				maxRotorInducedVelocityMetersPerSecond,
				(1.0 - minRotorInducedLagThrustScale) * 100.0,
				maxRotorTranslationalLiftIntensity,
				maxRotorAdvanceRatio,
				maxRotorPropellerAdvanceRatioJ,
				minRotorPropellerThrustScale,
				minRotorPropellerPowerScale,
				minRotorAxialGustThrustScale(),
				maxRotorAxialGustThrustScale(),
				maxRotorReverseFlowInboardFraction,
				maxRotorTipMach,
				(1.0 - minRotorCompressibilityThrustScale) * 100.0,
				maxRotorLowReynoldsLoss,
				maxRotorBladePassRippleIntensity,
				maxRotorAerodynamicLoadFactor,
				diskGradientStats.maxThrustLossFraction() * 100.0,
				diskGradientStats.maxLoadFactor(),
				diskGradientStats.maxVibration(),
				diskGradientStats.maxStallIntensity(),
				maxRotorInPlaneDragForceNewtons,
				maxMotorMechanicalLossTorqueNewtonMeters,
				maxMotorTrackingError,
				minMotorActuatorAuthority,
				maxRotorInflowSkewIntensity,
				maxRotorBladeDissymmetryTorqueNewtonMeters,
				maxRotorWakeInterferenceIntensity,
				maxRotorCoaxialLoadBias,
				maxRotorCoaxialLoadBiasTarget,
				maxRotorCoaxialLoadBiasClipping,
				maxRotorCoaxialAllocationLoadFraction,
				maxRotorCoaxialAllocationCommandRatio,
				maxRotorCoaxialAllocationMechanicalGainPercent,
				maxRotorCoaxialAllocationElectricalGainPercent,
				maxRotorCoaxialAllocationUncertaintyPercent,
				maxRotorWakeSwirlVelocityMetersPerSecond,
				maxRotorWindmillingIntensity,
				maxRotorWakeSwirlTorqueNewtonMeters,
				maxRotorActiveBrakingTorqueNewtonMeters,
				maxRotorAccelerationReactionTorqueNewtonMeters,
				maxRotorGyroscopicTorqueNewtonMeters,
				maxRotorFlappingTorqueNewtonMeters,
				maxRotorAngularDragTorqueNewtonMeters,
				maxAirframeAngularDragTorqueNewtonMeters,
				maxAirframeSeparatedFlowIntensity,
				maxAirframeLiftForceNewtons,
				maxAirframeBodyDragForceNewtons,
				maxLinearDampingDragForceNewtons,
				maxGroundEffectDragForceNewtons,
				maxGroundEffectLevelingTorqueNewtonMeters,
				maxRotorWashDragForceNewtons,
				maxRotorWallEffectForceNewtons,
				maxBarometerErrorMeters,
				maxBarometerPressurePortErrorMeters(),
				maxBarometerPropwashErrorMeters,
				minBarometerPressureHectopascals,
				maxDroneWakeIntensity,
				maxWaterImmersionIntensity,
				maxPrecipitationWetnessIntensity,
				(1.0 - minRotorWetThrustScale) * 100.0,
				icingStats.maxRotorIcingSeverity(),
				(1.0 - icingStats.minRotorIcingThrustScale()) * 100.0,
				icingStats.maxRotorIcingPowerScale(),
				minAmbientTemperatureCelsius,
				maxAmbientTemperatureCelsius,
				maxWindGustSpeedMetersPerSecond,
				maxWindDrydenSpeedMetersPerSecond(),
				maxWindBurbleSpeedMetersPerSecond(),
				maxWindA4mcSourceGustSpeedMetersPerSecond(),
				maxAbsWindA4mcUpdraftMetersPerSecond(),
				maxWindA4mcTerrainShearSpeedMetersPerSecond(),
				maxWindShearAccelerationMetersPerSecondSquared,
				windSourceStats.aerodynamics4McSamples(),
				sampleCount,
				windSourceStats.trustedSourceSamples(),
				windSourceStats.untrustedAerodynamics4McSamples(),
				windSourceStats.localVoxelFlowSamples(),
				windSourceStats.l0SourceSamples(),
				windSourceStats.l1SourceSamples(),
				windSourceStats.l2SourceSamples(),
				windSourceStats.maxFreshnessAgeTicks(),
				windSourceStats.staleSourceSamples(),
				windSourceStats.maxMeanSpeedMetersPerSecond(),
				windSourceStats.maxEffectiveSpeedMetersPerSecond(),
				windSourceStats.maxGustSpeedMetersPerSecond(),
				windSourceStats.maxConfidence(),
				windSourceStats.maxTurbulenceIntensity(),
				windSourceStats.maxQualityFactor(),
				windSourceStats.maxAbsPressureAnomalyPascals(),
				windSourceStats.maxShelterFactor(),
				windSourceStats.maxShearMagnitudePerBlock(),
				windSourceStats.maxAbsUpdraftMetersPerSecond(),
				windSourceStats.maxAbsAblStability(),
				windSourceStats.maxAblMixingStrength(),
				windSourceStats.maxRotorDiskWindGradientMetersPerSecond(),
				windSourceStats.maxRotorA4mcPressureGradientWindMetersPerSecond(),
				windSourceStats.minRotorLocalVoxelObstacleResidual(),
				windSourceStats.maxRotorA4mcShelterObstruction(),
				maxCeilingEffectMultiplier,
				formatCeilingClearance(minCeilingClearanceMeters),
				maxEnvironmentThrustAsymmetry,
				maxRotorFlowObstruction,
				maxRotorStallIntensity,
				maxRotorVibration,
				maxRotorDamageVibration,
				maxRotorConingIntensity,
				maxRotorConingAngleDegrees,
				maxRotorFlappingTiltDegrees,
				maxRotorArmFlexIntensity,
				maxRotorArmFlexDeflectionMillimeters,
				maxRotorArmFlexTiltDegrees,
				maxRotorSurfaceScrapeIntensity,
				maxMixerSaturation,
				minMixerAxisAuthority,
				maxMixerLowSaturation,
				maxMixerHighSaturation,
				minMixerLowHeadroom,
				minMixerHighHeadroom,
				maxEscDesyncIntensity,
				maxMotorTemperatureCelsius,
				minMotorElectricalEfficiency,
				minMotorVoltageHeadroom,
				maxMotorWindingResistanceScale,
				maxEscTemperatureCelsius,
				minEscThermalLimit,
				minRotorHealth * 100.0,
				propStrikeSamples,
				maxPropStrikeSeverity,
				propStrikeCount,
				maxAltitudeMeters,
				maxControlLinkLossSeconds,
				maxControlFrameAgeSeconds,
				maxControlFrameError,
				failsafeSamples,
				collisionSamples
		);
	}

	private static DroneBlackboxSummary empty() {
		return new DroneBlackboxSummary(
				0, // sampleCount
				0.0, // durationSeconds
				0, // maxPhysicsSubsteps
				0.0, // maxPhysicsRateHertz
				0.0, // maxSpeedMetersPerSecond
				0.0, // maxAirspeedMetersPerSecond
				0.0, // maxBatteryCurrentAmps
				0.0, // maxBatteryRegenerativeCurrentAmps
				0.0, // maxMotorRegenerativeCurrentAmps
				0.0, // maxBatterySagVoltage
				0.0, // maxBatteryEffectiveResistanceOhms
				1.0, // maxBatteryStateOfChargeResistanceScale
				1.0, // maxBatteryTemperatureResistanceScale
				1.0, // maxBatteryPolarizationResistanceScale
				0.0, // maxBatteryVoltageSpike
				0.0, // maxBatteryBusRippleVoltage
				0.0, // maxImuSupplyNoiseIntensity
				0.0, // minBatteryVoltage
				0.0, // minBatteryStateOfCharge
				1.0, // minBatteryCurrentLimit
				25.0, // maxBatteryTemperatureCelsius
				1.0, // minBatteryThermalLimit
				0.0, // maxPropwashIntensity
				0.0, // maxVortexRingState
				0.0, // maxVortexRingThrustBuffetAmplitude
				0.0, // maxVortexRingBuffetForceNewtons
				0.0, // maxRotorInducedVelocityMetersPerSecond
				1.0, // minRotorInducedLagThrustScale
				0.0, // maxRotorTranslationalLiftIntensity
				0.0, // maxRotorAdvanceRatio
				0.0, // maxRotorPropellerAdvanceRatioJ
				1.0, // minRotorPropellerThrustScale
				1.0, // minRotorPropellerPowerScale
				EMPTY_AXIAL_GUST_STATS,
				0.0, // maxRotorReverseFlowInboardFraction
				0.0, // maxRotorTipMach
				1.0, // minRotorCompressibilityThrustScale
				0.0, // maxRotorLowReynoldsLoss
				0.0, // maxRotorBladePassRippleIntensity
				0.0, // maxRotorAerodynamicLoadFactor
				0.0, // maxRotorInPlaneDragForceNewtons
				0.0, // maxMotorMechanicalLossTorqueNewtonMeters
				0.0, // maxMotorTrackingError
				1.0, // minMotorActuatorAuthority
				0.0, // maxRotorInflowSkewIntensity
				0.0, // maxRotorBladeDissymmetryTorqueNewtonMeters
				0.0, // maxRotorWakeInterferenceIntensity
				0.0, // maxRotorCoaxialLoadBias
				0.0, // maxRotorCoaxialLoadBiasTarget
				0.0, // maxRotorCoaxialLoadBiasClipping
				0.0, // maxRotorCoaxialAllocationLoadFraction
				1.0, // maxRotorCoaxialAllocationCommandRatio
				0.0, // maxRotorCoaxialAllocationMechanicalGainPercent
				0.0, // maxRotorCoaxialAllocationElectricalGainPercent
				0.0, // maxRotorCoaxialAllocationUncertaintyPercent
				1.0, // minRotorWetThrustScale
				0.0, // maxRotorWakeSwirlVelocityMetersPerSecond
				0.0, // maxRotorWindmillingIntensity
				0.0, // maxRotorWakeSwirlTorqueNewtonMeters
				0.0, // maxRotorActiveBrakingTorqueNewtonMeters
				0.0, // maxRotorAccelerationReactionTorqueNewtonMeters
				0.0, // maxRotorGyroscopicTorqueNewtonMeters
				0.0, // maxRotorFlappingTorqueNewtonMeters
				0.0, // maxRotorAngularDragTorqueNewtonMeters
				0.0, // maxAirframeAngularDragTorqueNewtonMeters
				0.0, // maxAirframeSeparatedFlowIntensity
				0.0, // maxAirframeLiftForceNewtons
				0.0, // maxAirframeBodyDragForceNewtons
				0.0, // maxLinearDampingDragForceNewtons
				0.0, // maxGroundEffectDragForceNewtons
				0.0, // maxGroundEffectLevelingTorqueNewtonMeters
				0.0, // maxRotorWashDragForceNewtons
				0.0, // maxRotorWallEffectForceNewtons
				0.0, // maxContactImpactSpeedMetersPerSecond
				0.0, // maxContactSlipSpeedMetersPerSecond
				0.0, // maxContactBounceSpeedMetersPerSecond
				1.0, // minContactSurfaceFrictionMultiplier
				1.0, // maxContactSurfaceFrictionMultiplier
				1.0, // minContactSurfaceRestitutionMultiplier
				1.0, // maxContactSurfaceRestitutionMultiplier
				1.0, // minContactSurfaceScrapeMultiplier
				1.0, // maxContactSurfaceScrapeMultiplier
				0.0, // maxContactAngularImpulseDegreesPerSecond
				0.0, // maxBarometerErrorMeters
				0.0, // maxBarometerPropwashErrorMeters
				1013.25, // minBarometerPressureHectopascals
				0.0, // maxRotorStallIntensity
				0.0, // maxRotorVibration
				0.0, // maxRotorDamageVibration
				0.0, // maxRotorConingIntensity
				0.0, // maxRotorConingAngleDegrees
				0.0, // maxRotorFlappingTiltDegrees
				0.0, // maxRotorArmFlexIntensity
				0.0, // maxRotorArmFlexDeflectionMillimeters
				0.0, // maxRotorArmFlexTiltDegrees
				0.0, // maxRotorSurfaceScrapeIntensity
				0.0, // maxMixerSaturation
				0.0, // maxMixerLowSaturation
				0.0, // maxMixerHighSaturation
				1.0, // minMixerLowHeadroom
				1.0, // minMixerHighHeadroom
				1.0, // minMixerAxisAuthority
				0.0, // maxMotorTemperatureCelsius
				0.0, // minMotorElectricalEfficiency
				1.0, // minMotorVoltageHeadroom
				1.0, // maxMotorWindingResistanceScale
				0.0, // maxEscTemperatureCelsius
				1.0, // minEscThermalLimit
				0.0, // maxEscDesyncIntensity
				0.0, // maxDroneWakeIntensity
				0.0, // maxWaterImmersionIntensity
				0.0, // maxPrecipitationWetnessIntensity
				0.0, // minAmbientTemperatureCelsius
				0.0, // maxAmbientTemperatureCelsius
				0.0, // maxWindGustSpeedMetersPerSecond
				EMPTY_WIND_SPLIT,
				0.0, // maxWindShearAccelerationMetersPerSecondSquared
				1.0, // maxCeilingEffectMultiplier
				0.0, // maxEnvironmentThrustAsymmetry
				0.0, // maxRotorFlowObstruction
				0.0, // minCeilingClearanceMeters
				0.0, // maxAltitudeMeters
				0.0, // maxControlLinkLossSeconds
				0.0, // maxControlFrameAgeSeconds
				0.0, // maxControlFrameError
				0.0, // minRotorHealth
				0.0, // maxPropStrikeSeverity
				0, // propStrikeSamples
				0, // propStrikeCount
				0, // failsafeSamples
				0 // collisionSamples
		);
	}

	private static double value(String[] row, String column) {
		return valueOrDefault(row, column, 0.0);
	}

	private static double valueOrDefault(String[] row, String column, double fallback) {
		int index = index(column);
		if (index >= row.length) {
			return fallback;
		}
		try {
			return Double.parseDouble(row[index]);
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static String textValue(String[] row, String column) {
		int index = index(column);
		return index < row.length ? row[index] : "";
	}

	private static boolean boolValue(String[] row, String column) {
		int index = index(column);
		return index < row.length && Boolean.parseBoolean(row[index]);
	}

	private static boolean isPlayableNeutralControlSample(String[] row) {
		return (boolValue(row, "armed") || boolValue(row, "control_armed") || value(row, "motor_power") > 0.08)
				&& Math.abs(value(row, "input_pitch")) <= 0.006
				&& Math.abs(value(row, "input_roll")) <= 0.006
				&& Math.abs(value(row, "input_yaw")) <= 0.006;
	}

	private static int intValue(String[] row, String column) {
		int index = index(column);
		if (index >= row.length) {
			return 0;
		}
		try {
			return Integer.parseInt(row[index]);
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static double minIndexedValue(String[] row, String prefix, String suffix, double fallback) {
		double min = Double.POSITIVE_INFINITY;
		for (String column : COLUMNS.keySet()) {
			if (isIndexedColumn(column, prefix, suffix)) {
				min = Math.min(min, value(row, column));
			}
		}
		return Double.isFinite(min) ? min : fallback;
	}

	private static double minPositiveIndexedValue(String[] row, String prefix, String suffix, double fallback) {
		double min = Double.POSITIVE_INFINITY;
		for (String column : COLUMNS.keySet()) {
			if (isIndexedColumn(column, prefix, suffix)) {
				double value = value(row, column);
				if (value > 1.0e-9) {
					min = Math.min(min, value);
				}
			}
		}
		return Double.isFinite(min) ? min : fallback;
	}

	private static double maxIndexedValue(String[] row, String prefix, String suffix) {
		double max = 0.0;
		for (String column : COLUMNS.keySet()) {
			if (isIndexedColumn(column, prefix, suffix)) {
				max = Math.max(max, value(row, column));
			}
		}
		return max;
	}

	private static boolean isIndexedColumn(String column, String prefix, String suffix) {
		if (!column.startsWith(prefix)
				|| !column.endsWith(suffix)
				|| column.length() <= prefix.length() + suffix.length()) {
			return false;
		}
		String index = column.substring(prefix.length(), column.length() - suffix.length());
		if (index.isEmpty()) {
			return false;
		}
		for (int i = 0; i < index.length(); i++) {
			if (!Character.isDigit(index.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static int index(String column) {
		Integer index = COLUMNS.get(column);
		if (index == null) {
			throw new IllegalArgumentException("Missing blackbox column: " + column);
		}
		return index;
	}

	private static Map<String, Integer> columnIndex() {
		String[] columns = DroneBlackboxSample.CSV_HEADER.split(",", -1);
		Map<String, Integer> indices = new HashMap<>();
		for (int i = 0; i < columns.length; i++) {
			indices.put(columns[i], i);
		}
		return Map.copyOf(indices);
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}

	private static double unitOrOne(double value) {
		return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 1.0;
	}

	private static double unitOrZero(double value) {
		return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
	}

	private static double angleDifferenceDegrees(double a, double b) {
		if (!Double.isFinite(a) || !Double.isFinite(b)) {
			return 0.0;
		}
		double difference = (a - b) % 360.0;
		if (difference > 180.0) {
			difference -= 360.0;
		} else if (difference < -180.0) {
			difference += 360.0;
		}
		return Math.abs(difference);
	}

	private static double finiteNonNegativeOrZero(double value) {
		return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	private static double finiteScaleOrOne(double value) {
		return Double.isFinite(value) ? Math.max(0.0, Math.min(2.5, value)) : 1.0;
	}

	private static double finiteOrOne(double value) {
		return Double.isFinite(value) ? value : 1.0;
	}

	private static String formatCeilingClearance(double value) {
		return value > 0.0 ? String.format(Locale.ROOT, "%.2fm", value) : "open";
	}
}
