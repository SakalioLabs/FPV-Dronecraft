package com.tenicana.dronecraft.sim;

public record DroneEnvironment(
		Vec3 windVelocityWorldMetersPerSecond,
		double airDensityRatio,
		double groundClearanceMeters,
		double turbulenceIntensity,
		double obstacleProximity,
		double droneWakeIntensity,
		double ceilingClearanceMeters,
		double[] rotorThrustMultipliers,
		double[] rotorFlowObstructions,
		Vec3[] rotorFlowObstructionDirectionsBody,
		double[] rotorWaterImmersions,
		double waterImmersionIntensity,
		double[] rotorPrecipitationWetnesses,
		double precipitationWetnessIntensity,
		double ambientTemperatureCelsius,
		Vec3[] rotorWindVelocityWorldMetersPerSecond,
		Vec3[] rotorDiskWindGradientBodyMetersPerSecond,
		double[] rotorA4mcShelterObstructions,
		String windSourceId,
		boolean windSourceTrustedForGameplay,
		double windSourceConfidence,
		double windSourceTurbulenceIntensity,
		double windSourcePressureAnomalyPascals,
		double windShearMagnitudePerBlock,
		double windShelterFactor,
		double windUpdraftMetersPerSecond,
		boolean windSourceLocalVoxelFlow,
		String windSourceLevel,
		String windSourceAuthority,
		long windSourceFreshnessAgeTicks,
		double windSourceMeanSpeedMetersPerSecond,
		double windSourceEffectiveSpeedMetersPerSecond,
		double windSourceGustSpeedMetersPerSecond,
		boolean windSourceHasTemperature,
		double windSourceTemperatureCelsius,
		boolean windSourceHasHumidity,
		double windSourceHumidity,
		double windSourceAblStability,
		double windSourceAblMixingStrength,
		Vec3 windSourceGustVelocityWorldMetersPerSecond,
		double[] rotorGroundSurfaceCoverages,
		double[] rotorCeilingSurfaceCoverages,
		double[] rotorGroundSurfaceGates,
		double[] rotorCeilingSurfaceGates,
		double[] rotorLocalVoxelObstacleResiduals,
		Vec3[] rotorA4mcPressureGradientWindBodyMetersPerSecond
) {
	public static final String WIND_SOURCE_INTERNAL = "internal";
	public static final String WIND_SOURCE_CALM = "calm";
	public static final String WIND_SOURCE_MINECRAFT_WEATHER = "minecraft_weather";
	public static final String WIND_SOURCE_AERODYNAMICS4MC = "aerodynamics4mc";
	public static final String WIND_SOURCE_ENVIRONMENT_OVERRIDE = "environment_override";
	public static final String WIND_SOURCE_LEVEL_NONE = "none";
	public static final String WIND_SOURCE_AUTHORITY_NONE = "none";

	private static final double SEA_LEVEL_PRESSURE_HECTOPASCALS = 1013.25;
	private static final double SEA_LEVEL_PRESSURE_PASCALS = SEA_LEVEL_PRESSURE_HECTOPASCALS * 100.0;
	private static final double MAX_WIND_SOURCE_PRESSURE_ANOMALY_PASCALS = 5000.0;
	private static final long WIND_SOURCE_FULL_TRUST_AGE_TICKS = 40L;
	private static final long WIND_SOURCE_ZERO_TRUST_AGE_TICKS = 160L;
	private static final double STANDARD_SEA_LEVEL_TEMPERATURE_KELVIN = 288.15;
	private static final double STANDARD_LAPSE_RATE_KELVIN_PER_METER = 0.0065;
	private static final double STANDARD_PRESSURE_EXPONENT = 5.255;
	private static final double WATER_VAPOR_DRY_AIR_DENSITY_RELIEF = 0.378;
	private static final double ZJU_GROUND_EFFECT_G1_METERS_SQUARED = 0.01804;
	private static final double ZJU_GROUND_EFFECT_G3_METERS = -0.3365;
	private static final double ZJU_GROUND_EFFECT_G4_METERS_SQUARED = 0.04126;
	private static final double ZJU_GROUND_EFFECT_G5 = 0.06494;
	private static final double ZJU_GROUND_EFFECT_LEVELING_TORQUE_PEAK_HEIGHT_METERS = 0.186;
	private static final double RACING_QUAD_REFERENCE_GROUND_EFFECT_BOOST = 0.18;
	private static final double MAX_GROUND_EFFECT_EXTRA_THRUST_FRACTION = 0.60;
	private static final double MAX_CEILING_EFFECT_EXTRA_THRUST_FRACTION = 0.38;
	private static final double PARTIAL_SURFACE_NEGLIGIBLE_DIAMETER_OVER_PROP_DIAMETER = 0.5;
	private static final double PARTIAL_SURFACE_FULL_LIKE_DIAMETER_OVER_PROP_DIAMETER = 1.0;

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, 0.0);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, 0.0);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, 0.0);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, Double.POSITIVE_INFINITY);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, null);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, null);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers, double[] rotorFlowObstructions) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, rotorFlowObstructions, null);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers, double[] rotorFlowObstructions, Vec3[] rotorFlowObstructionDirectionsBody) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, rotorFlowObstructions, rotorFlowObstructionDirectionsBody, null, 0.0);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers, double[] rotorFlowObstructions, Vec3[] rotorFlowObstructionDirectionsBody, double waterImmersionIntensity) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, rotorFlowObstructions, rotorFlowObstructionDirectionsBody, null, waterImmersionIntensity);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers, double[] rotorFlowObstructions, Vec3[] rotorFlowObstructionDirectionsBody, double[] rotorWaterImmersions, double waterImmersionIntensity) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, rotorFlowObstructions, rotorFlowObstructionDirectionsBody, rotorWaterImmersions, waterImmersionIntensity, 0.0);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers, double[] rotorFlowObstructions, Vec3[] rotorFlowObstructionDirectionsBody, double[] rotorWaterImmersions, double waterImmersionIntensity, double precipitationWetnessIntensity) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, rotorFlowObstructions, rotorFlowObstructionDirectionsBody, rotorWaterImmersions, waterImmersionIntensity, null, precipitationWetnessIntensity, 25.0);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers, double[] rotorFlowObstructions, Vec3[] rotorFlowObstructionDirectionsBody, double[] rotorWaterImmersions, double waterImmersionIntensity, double[] rotorPrecipitationWetnesses, double precipitationWetnessIntensity) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, rotorFlowObstructions, rotorFlowObstructionDirectionsBody, rotorWaterImmersions, waterImmersionIntensity, rotorPrecipitationWetnesses, precipitationWetnessIntensity, 25.0);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers, double[] rotorFlowObstructions, Vec3[] rotorFlowObstructionDirectionsBody, double[] rotorWaterImmersions, double waterImmersionIntensity, double precipitationWetnessIntensity, double ambientTemperatureCelsius) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, rotorFlowObstructions, rotorFlowObstructionDirectionsBody, rotorWaterImmersions, waterImmersionIntensity, null, precipitationWetnessIntensity, ambientTemperatureCelsius);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers, double[] rotorFlowObstructions, Vec3[] rotorFlowObstructionDirectionsBody, double[] rotorWaterImmersions, double waterImmersionIntensity, double[] rotorPrecipitationWetnesses, double precipitationWetnessIntensity, double ambientTemperatureCelsius) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, rotorFlowObstructions, rotorFlowObstructionDirectionsBody, rotorWaterImmersions, waterImmersionIntensity, rotorPrecipitationWetnesses, precipitationWetnessIntensity, ambientTemperatureCelsius, null);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers, double[] rotorFlowObstructions, Vec3[] rotorFlowObstructionDirectionsBody, double[] rotorWaterImmersions, double waterImmersionIntensity, double[] rotorPrecipitationWetnesses, double precipitationWetnessIntensity, double ambientTemperatureCelsius, Vec3[] rotorWindVelocityWorldMetersPerSecond) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, rotorFlowObstructions, rotorFlowObstructionDirectionsBody, rotorWaterImmersions, waterImmersionIntensity, rotorPrecipitationWetnesses, precipitationWetnessIntensity, ambientTemperatureCelsius, rotorWindVelocityWorldMetersPerSecond, null);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers, double[] rotorFlowObstructions, Vec3[] rotorFlowObstructionDirectionsBody, double[] rotorWaterImmersions, double waterImmersionIntensity, double[] rotorPrecipitationWetnesses, double precipitationWetnessIntensity, double ambientTemperatureCelsius, Vec3[] rotorWindVelocityWorldMetersPerSecond, Vec3[] rotorDiskWindGradientBodyMetersPerSecond) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, rotorFlowObstructions, rotorFlowObstructionDirectionsBody, rotorWaterImmersions, waterImmersionIntensity, rotorPrecipitationWetnesses, precipitationWetnessIntensity, ambientTemperatureCelsius, rotorWindVelocityWorldMetersPerSecond, rotorDiskWindGradientBodyMetersPerSecond, null, WIND_SOURCE_INTERNAL, false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, WIND_SOURCE_LEVEL_NONE, WIND_SOURCE_AUTHORITY_NONE, -1L, 0.0, 0.0, 0.0, false, 0.0, false, 0.0, 0.0, 0.0);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers, double[] rotorFlowObstructions, Vec3[] rotorFlowObstructionDirectionsBody, double[] rotorWaterImmersions, double waterImmersionIntensity, double[] rotorPrecipitationWetnesses, double precipitationWetnessIntensity, double ambientTemperatureCelsius, Vec3[] rotorWindVelocityWorldMetersPerSecond, Vec3[] rotorDiskWindGradientBodyMetersPerSecond, double[] rotorA4mcShelterObstructions) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, rotorFlowObstructions, rotorFlowObstructionDirectionsBody, rotorWaterImmersions, waterImmersionIntensity, rotorPrecipitationWetnesses, precipitationWetnessIntensity, ambientTemperatureCelsius, rotorWindVelocityWorldMetersPerSecond, rotorDiskWindGradientBodyMetersPerSecond, rotorA4mcShelterObstructions, WIND_SOURCE_INTERNAL, false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, WIND_SOURCE_LEVEL_NONE, WIND_SOURCE_AUTHORITY_NONE, -1L, 0.0, 0.0, 0.0, false, 0.0, false, 0.0, 0.0, 0.0);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers, double[] rotorFlowObstructions, Vec3[] rotorFlowObstructionDirectionsBody, double[] rotorWaterImmersions, double waterImmersionIntensity, double[] rotorPrecipitationWetnesses, double precipitationWetnessIntensity, double ambientTemperatureCelsius, Vec3[] rotorWindVelocityWorldMetersPerSecond, Vec3[] rotorDiskWindGradientBodyMetersPerSecond, double[] rotorA4mcShelterObstructions, double[] rotorLocalVoxelObstacleResiduals) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, rotorFlowObstructions, rotorFlowObstructionDirectionsBody, rotorWaterImmersions, waterImmersionIntensity, rotorPrecipitationWetnesses, precipitationWetnessIntensity, ambientTemperatureCelsius, rotorWindVelocityWorldMetersPerSecond, rotorDiskWindGradientBodyMetersPerSecond, rotorA4mcShelterObstructions, rotorLocalVoxelObstacleResiduals, null);
	}

	public DroneEnvironment(Vec3 windVelocityWorldMetersPerSecond, double airDensityRatio, double groundClearanceMeters, double turbulenceIntensity, double obstacleProximity, double droneWakeIntensity, double ceilingClearanceMeters, double[] rotorThrustMultipliers, double[] rotorFlowObstructions, Vec3[] rotorFlowObstructionDirectionsBody, double[] rotorWaterImmersions, double waterImmersionIntensity, double[] rotorPrecipitationWetnesses, double precipitationWetnessIntensity, double ambientTemperatureCelsius, Vec3[] rotorWindVelocityWorldMetersPerSecond, Vec3[] rotorDiskWindGradientBodyMetersPerSecond, double[] rotorA4mcShelterObstructions, double[] rotorLocalVoxelObstacleResiduals, Vec3[] rotorA4mcPressureGradientWindBodyMetersPerSecond) {
		this(windVelocityWorldMetersPerSecond, airDensityRatio, groundClearanceMeters, turbulenceIntensity, obstacleProximity, droneWakeIntensity, ceilingClearanceMeters, rotorThrustMultipliers, rotorFlowObstructions, rotorFlowObstructionDirectionsBody, rotorWaterImmersions, waterImmersionIntensity, rotorPrecipitationWetnesses, precipitationWetnessIntensity, ambientTemperatureCelsius, rotorWindVelocityWorldMetersPerSecond, rotorDiskWindGradientBodyMetersPerSecond, rotorA4mcShelterObstructions, WIND_SOURCE_INTERNAL, false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, WIND_SOURCE_LEVEL_NONE, WIND_SOURCE_AUTHORITY_NONE, -1L, 0.0, 0.0, 0.0, false, 0.0, false, 0.0, 0.0, 0.0, Vec3.ZERO, null, null, null, null, rotorLocalVoxelObstacleResiduals, rotorA4mcPressureGradientWindBodyMetersPerSecond);
	}

	public DroneEnvironment(
			Vec3 windVelocityWorldMetersPerSecond,
			double airDensityRatio,
			double groundClearanceMeters,
			double turbulenceIntensity,
			double obstacleProximity,
			double droneWakeIntensity,
			double ceilingClearanceMeters,
			double[] rotorThrustMultipliers,
			double[] rotorFlowObstructions,
			Vec3[] rotorFlowObstructionDirectionsBody,
			double[] rotorWaterImmersions,
			double waterImmersionIntensity,
			double[] rotorPrecipitationWetnesses,
			double precipitationWetnessIntensity,
			double ambientTemperatureCelsius,
			Vec3[] rotorWindVelocityWorldMetersPerSecond,
			Vec3[] rotorDiskWindGradientBodyMetersPerSecond,
			String windSourceId,
			boolean windSourceTrustedForGameplay,
			double windSourceConfidence,
			double windSourceTurbulenceIntensity,
			double windSourcePressureAnomalyPascals,
			double windShearMagnitudePerBlock,
			double windShelterFactor,
			double windUpdraftMetersPerSecond,
			boolean windSourceLocalVoxelFlow,
			String windSourceLevel,
			String windSourceAuthority,
			long windSourceFreshnessAgeTicks,
			double windSourceMeanSpeedMetersPerSecond,
			double windSourceEffectiveSpeedMetersPerSecond,
			double windSourceGustSpeedMetersPerSecond,
			boolean windSourceHasTemperature,
			double windSourceTemperatureCelsius,
			boolean windSourceHasHumidity,
			double windSourceHumidity,
			double windSourceAblStability,
			double windSourceAblMixingStrength
	) {
		this(
				windVelocityWorldMetersPerSecond,
				airDensityRatio,
				groundClearanceMeters,
				turbulenceIntensity,
				obstacleProximity,
				droneWakeIntensity,
				ceilingClearanceMeters,
				rotorThrustMultipliers,
				rotorFlowObstructions,
				rotorFlowObstructionDirectionsBody,
				rotorWaterImmersions,
				waterImmersionIntensity,
				rotorPrecipitationWetnesses,
				precipitationWetnessIntensity,
				ambientTemperatureCelsius,
				rotorWindVelocityWorldMetersPerSecond,
				rotorDiskWindGradientBodyMetersPerSecond,
				null,
				windSourceId,
				windSourceTrustedForGameplay,
				windSourceConfidence,
				windSourceTurbulenceIntensity,
				windSourcePressureAnomalyPascals,
				windShearMagnitudePerBlock,
				windShelterFactor,
				windUpdraftMetersPerSecond,
				windSourceLocalVoxelFlow,
				windSourceLevel,
				windSourceAuthority,
				windSourceFreshnessAgeTicks,
				windSourceMeanSpeedMetersPerSecond,
				windSourceEffectiveSpeedMetersPerSecond,
				windSourceGustSpeedMetersPerSecond,
				windSourceHasTemperature,
				windSourceTemperatureCelsius,
				windSourceHasHumidity,
				windSourceHumidity,
				windSourceAblStability,
				windSourceAblMixingStrength,
				Vec3.ZERO,
				null,
				null,
				null,
				null,
				null,
				null
		);
	}

	public DroneEnvironment(
			Vec3 windVelocityWorldMetersPerSecond,
			double airDensityRatio,
			double groundClearanceMeters,
			double turbulenceIntensity,
			double obstacleProximity,
			double droneWakeIntensity,
			double ceilingClearanceMeters,
			double[] rotorThrustMultipliers,
			double[] rotorFlowObstructions,
			Vec3[] rotorFlowObstructionDirectionsBody,
			double[] rotorWaterImmersions,
			double waterImmersionIntensity,
			double[] rotorPrecipitationWetnesses,
			double precipitationWetnessIntensity,
			double ambientTemperatureCelsius,
			Vec3[] rotorWindVelocityWorldMetersPerSecond,
			Vec3[] rotorDiskWindGradientBodyMetersPerSecond,
			String windSourceId,
			boolean windSourceTrustedForGameplay,
			double windSourceConfidence,
			double windSourceTurbulenceIntensity,
			double windSourcePressureAnomalyPascals,
			double windShearMagnitudePerBlock,
			double windShelterFactor,
			double windUpdraftMetersPerSecond,
			boolean windSourceLocalVoxelFlow,
			String windSourceLevel,
			String windSourceAuthority,
			long windSourceFreshnessAgeTicks,
			double windSourceMeanSpeedMetersPerSecond,
			double windSourceEffectiveSpeedMetersPerSecond,
			double windSourceGustSpeedMetersPerSecond,
			boolean windSourceHasTemperature,
			double windSourceTemperatureCelsius,
			boolean windSourceHasHumidity,
			double windSourceHumidity,
			double windSourceAblStability,
			double windSourceAblMixingStrength,
			Vec3 windSourceGustVelocityWorldMetersPerSecond
	) {
		this(
				windVelocityWorldMetersPerSecond,
				airDensityRatio,
				groundClearanceMeters,
				turbulenceIntensity,
				obstacleProximity,
				droneWakeIntensity,
				ceilingClearanceMeters,
				rotorThrustMultipliers,
				rotorFlowObstructions,
				rotorFlowObstructionDirectionsBody,
				rotorWaterImmersions,
				waterImmersionIntensity,
				rotorPrecipitationWetnesses,
				precipitationWetnessIntensity,
				ambientTemperatureCelsius,
				rotorWindVelocityWorldMetersPerSecond,
				rotorDiskWindGradientBodyMetersPerSecond,
				null,
				windSourceId,
				windSourceTrustedForGameplay,
				windSourceConfidence,
				windSourceTurbulenceIntensity,
				windSourcePressureAnomalyPascals,
				windShearMagnitudePerBlock,
				windShelterFactor,
				windUpdraftMetersPerSecond,
				windSourceLocalVoxelFlow,
				windSourceLevel,
				windSourceAuthority,
				windSourceFreshnessAgeTicks,
				windSourceMeanSpeedMetersPerSecond,
				windSourceEffectiveSpeedMetersPerSecond,
				windSourceGustSpeedMetersPerSecond,
				windSourceHasTemperature,
				windSourceTemperatureCelsius,
				windSourceHasHumidity,
				windSourceHumidity,
				windSourceAblStability,
				windSourceAblMixingStrength,
				windSourceGustVelocityWorldMetersPerSecond,
				null,
				null,
				null,
				null,
				null,
				null
		);
	}

	public DroneEnvironment(
			Vec3 windVelocityWorldMetersPerSecond,
			double airDensityRatio,
			double groundClearanceMeters,
			double turbulenceIntensity,
			double obstacleProximity,
			double droneWakeIntensity,
			double ceilingClearanceMeters,
			double[] rotorThrustMultipliers,
			double[] rotorFlowObstructions,
			Vec3[] rotorFlowObstructionDirectionsBody,
			double[] rotorWaterImmersions,
			double waterImmersionIntensity,
			double[] rotorPrecipitationWetnesses,
			double precipitationWetnessIntensity,
			double ambientTemperatureCelsius,
			Vec3[] rotorWindVelocityWorldMetersPerSecond,
			Vec3[] rotorDiskWindGradientBodyMetersPerSecond,
			double[] rotorA4mcShelterObstructions,
			String windSourceId,
			boolean windSourceTrustedForGameplay,
			double windSourceConfidence,
			double windSourceTurbulenceIntensity,
			double windSourcePressureAnomalyPascals,
			double windShearMagnitudePerBlock,
			double windShelterFactor,
			double windUpdraftMetersPerSecond,
			boolean windSourceLocalVoxelFlow,
			String windSourceLevel,
			String windSourceAuthority,
			long windSourceFreshnessAgeTicks,
			double windSourceMeanSpeedMetersPerSecond,
			double windSourceEffectiveSpeedMetersPerSecond,
			double windSourceGustSpeedMetersPerSecond,
			boolean windSourceHasTemperature,
			double windSourceTemperatureCelsius,
			boolean windSourceHasHumidity,
			double windSourceHumidity,
			double windSourceAblStability,
			double windSourceAblMixingStrength
	) {
		this(
				windVelocityWorldMetersPerSecond,
				airDensityRatio,
				groundClearanceMeters,
				turbulenceIntensity,
				obstacleProximity,
				droneWakeIntensity,
				ceilingClearanceMeters,
				rotorThrustMultipliers,
				rotorFlowObstructions,
				rotorFlowObstructionDirectionsBody,
				rotorWaterImmersions,
				waterImmersionIntensity,
				rotorPrecipitationWetnesses,
				precipitationWetnessIntensity,
				ambientTemperatureCelsius,
				rotorWindVelocityWorldMetersPerSecond,
				rotorDiskWindGradientBodyMetersPerSecond,
				rotorA4mcShelterObstructions,
				windSourceId,
				windSourceTrustedForGameplay,
				windSourceConfidence,
				windSourceTurbulenceIntensity,
				windSourcePressureAnomalyPascals,
				windShearMagnitudePerBlock,
				windShelterFactor,
				windUpdraftMetersPerSecond,
				windSourceLocalVoxelFlow,
				windSourceLevel,
				windSourceAuthority,
				windSourceFreshnessAgeTicks,
				windSourceMeanSpeedMetersPerSecond,
				windSourceEffectiveSpeedMetersPerSecond,
				windSourceGustSpeedMetersPerSecond,
				windSourceHasTemperature,
				windSourceTemperatureCelsius,
				windSourceHasHumidity,
				windSourceHumidity,
				windSourceAblStability,
				windSourceAblMixingStrength,
				Vec3.ZERO,
				null,
				null,
				null,
				null,
				null,
				null
		);
	}

	public DroneEnvironment(
			Vec3 windVelocityWorldMetersPerSecond,
			double airDensityRatio,
			double groundClearanceMeters,
			double turbulenceIntensity,
			double obstacleProximity,
			double droneWakeIntensity,
			double ceilingClearanceMeters,
			double[] rotorThrustMultipliers,
			double[] rotorFlowObstructions,
			Vec3[] rotorFlowObstructionDirectionsBody,
			double[] rotorWaterImmersions,
			double waterImmersionIntensity,
			double[] rotorPrecipitationWetnesses,
			double precipitationWetnessIntensity,
			double ambientTemperatureCelsius,
			Vec3[] rotorWindVelocityWorldMetersPerSecond,
			Vec3[] rotorDiskWindGradientBodyMetersPerSecond,
			double[] rotorA4mcShelterObstructions,
			String windSourceId,
			boolean windSourceTrustedForGameplay,
			double windSourceConfidence,
			double windSourceTurbulenceIntensity,
			double windSourcePressureAnomalyPascals,
			double windShearMagnitudePerBlock,
			double windShelterFactor,
			double windUpdraftMetersPerSecond,
			boolean windSourceLocalVoxelFlow,
			String windSourceLevel,
			String windSourceAuthority,
			long windSourceFreshnessAgeTicks,
			double windSourceMeanSpeedMetersPerSecond,
			double windSourceEffectiveSpeedMetersPerSecond,
			double windSourceGustSpeedMetersPerSecond,
			boolean windSourceHasTemperature,
			double windSourceTemperatureCelsius,
			boolean windSourceHasHumidity,
			double windSourceHumidity,
			double windSourceAblStability,
			double windSourceAblMixingStrength,
			Vec3 windSourceGustVelocityWorldMetersPerSecond
	) {
		this(
				windVelocityWorldMetersPerSecond,
				airDensityRatio,
				groundClearanceMeters,
				turbulenceIntensity,
				obstacleProximity,
				droneWakeIntensity,
				ceilingClearanceMeters,
				rotorThrustMultipliers,
				rotorFlowObstructions,
				rotorFlowObstructionDirectionsBody,
				rotorWaterImmersions,
				waterImmersionIntensity,
				rotorPrecipitationWetnesses,
				precipitationWetnessIntensity,
				ambientTemperatureCelsius,
				rotorWindVelocityWorldMetersPerSecond,
				rotorDiskWindGradientBodyMetersPerSecond,
				rotorA4mcShelterObstructions,
				windSourceId,
				windSourceTrustedForGameplay,
				windSourceConfidence,
				windSourceTurbulenceIntensity,
				windSourcePressureAnomalyPascals,
				windShearMagnitudePerBlock,
				windShelterFactor,
				windUpdraftMetersPerSecond,
				windSourceLocalVoxelFlow,
				windSourceLevel,
				windSourceAuthority,
				windSourceFreshnessAgeTicks,
				windSourceMeanSpeedMetersPerSecond,
				windSourceEffectiveSpeedMetersPerSecond,
				windSourceGustSpeedMetersPerSecond,
				windSourceHasTemperature,
				windSourceTemperatureCelsius,
				windSourceHasHumidity,
				windSourceHumidity,
				windSourceAblStability,
				windSourceAblMixingStrength,
				windSourceGustVelocityWorldMetersPerSecond,
				null,
				null,
				null,
				null,
				null,
				null
		);
	}

	public DroneEnvironment {
		if (windVelocityWorldMetersPerSecond == null) {
			windVelocityWorldMetersPerSecond = Vec3.ZERO;
		}
		if (!Double.isFinite(airDensityRatio)) {
			airDensityRatio = 1.0;
		}
		airDensityRatio = MathUtil.clamp(airDensityRatio, 0.35, 1.35);
		if (!Double.isFinite(groundClearanceMeters) || groundClearanceMeters < 0.0) {
			groundClearanceMeters = Double.POSITIVE_INFINITY;
		}
		if (!Double.isFinite(turbulenceIntensity)) {
			turbulenceIntensity = 0.0;
		}
		turbulenceIntensity = MathUtil.clamp(turbulenceIntensity, 0.0, 1.5);
		if (!Double.isFinite(obstacleProximity)) {
			obstacleProximity = 0.0;
		}
		obstacleProximity = MathUtil.clamp(obstacleProximity, 0.0, 1.0);
		if (!Double.isFinite(droneWakeIntensity)) {
			droneWakeIntensity = 0.0;
		}
		droneWakeIntensity = MathUtil.clamp(droneWakeIntensity, 0.0, 1.5);
		if (!Double.isFinite(ceilingClearanceMeters) || ceilingClearanceMeters < 0.0) {
			ceilingClearanceMeters = Double.POSITIVE_INFINITY;
		}
		rotorThrustMultipliers = sanitizeRotorThrustMultipliers(rotorThrustMultipliers);
		rotorFlowObstructions = sanitizeUnitArray(rotorFlowObstructions);
		rotorFlowObstructionDirectionsBody = sanitizeDirectionArray(rotorFlowObstructionDirectionsBody);
		rotorWaterImmersions = sanitizeUnitArray(rotorWaterImmersions);
		rotorPrecipitationWetnesses = sanitizeUnitArray(rotorPrecipitationWetnesses);
		rotorWindVelocityWorldMetersPerSecond = sanitizeWindArray(rotorWindVelocityWorldMetersPerSecond);
		rotorDiskWindGradientBodyMetersPerSecond = sanitizeDiskWindGradientArray(rotorDiskWindGradientBodyMetersPerSecond);
		rotorA4mcShelterObstructions = sanitizeUnitArray(rotorA4mcShelterObstructions);
		rotorGroundSurfaceCoverages = sanitizeUnitArray(rotorGroundSurfaceCoverages);
		rotorCeilingSurfaceCoverages = sanitizeUnitArray(rotorCeilingSurfaceCoverages);
		rotorGroundSurfaceGates = sanitizeUnitArray(rotorGroundSurfaceGates);
		rotorCeilingSurfaceGates = sanitizeUnitArray(rotorCeilingSurfaceGates);
		rotorLocalVoxelObstacleResiduals = sanitizeUnitArrayOrOne(rotorLocalVoxelObstacleResiduals);
		rotorA4mcPressureGradientWindBodyMetersPerSecond = sanitizeDiskWindGradientArray(rotorA4mcPressureGradientWindBodyMetersPerSecond);
		windSourceId = sanitizeWindSourceId(windSourceId);
		if (!Double.isFinite(windSourceConfidence)) {
			windSourceConfidence = 0.0;
		}
		windSourceConfidence = MathUtil.clamp(windSourceConfidence, 0.0, 1.0);
		if (!Double.isFinite(windSourceTurbulenceIntensity)) {
			windSourceTurbulenceIntensity = 0.0;
		}
		windSourceTurbulenceIntensity = MathUtil.clamp(windSourceTurbulenceIntensity, 0.0, 1.5);
		windSourcePressureAnomalyPascals = sanitizePressureAnomalyPascals(windSourcePressureAnomalyPascals);
		if (!Double.isFinite(windShearMagnitudePerBlock)) {
			windShearMagnitudePerBlock = 0.0;
		}
		windShearMagnitudePerBlock = MathUtil.clamp(windShearMagnitudePerBlock, 0.0, 5.0);
		if (!Double.isFinite(windShelterFactor)) {
			windShelterFactor = 0.0;
		}
		windShelterFactor = MathUtil.clamp(windShelterFactor, 0.0, 1.0);
		if (!Double.isFinite(windUpdraftMetersPerSecond)) {
			windUpdraftMetersPerSecond = 0.0;
		}
		windUpdraftMetersPerSecond = MathUtil.clamp(windUpdraftMetersPerSecond, -12.0, 12.0);
		windSourceLevel = sanitizeWindSourceIdWithFallback(windSourceLevel, WIND_SOURCE_LEVEL_NONE);
		windSourceAuthority = sanitizeWindSourceIdWithFallback(windSourceAuthority, WIND_SOURCE_AUTHORITY_NONE);
		windSourceFreshnessAgeTicks = windSourceFreshnessAgeTicks < 0L
				? -1L
				: Math.min(windSourceFreshnessAgeTicks, 1_000_000L);
		windSourceMeanSpeedMetersPerSecond = sanitizeWindSourceSpeed(windSourceMeanSpeedMetersPerSecond);
		windSourceEffectiveSpeedMetersPerSecond = sanitizeWindSourceSpeed(windSourceEffectiveSpeedMetersPerSecond);
		windSourceGustSpeedMetersPerSecond = sanitizeWindSourceSpeed(windSourceGustSpeedMetersPerSecond);
		windSourceGustVelocityWorldMetersPerSecond = sanitizeWindSourceVelocity(windSourceGustVelocityWorldMetersPerSecond);
		if (!windSourceHasTemperature || !Double.isFinite(windSourceTemperatureCelsius)) {
			windSourceHasTemperature = false;
			windSourceTemperatureCelsius = 0.0;
		} else {
			windSourceTemperatureCelsius = MathUtil.clamp(windSourceTemperatureCelsius, -40.0, 65.0);
		}
		if (!windSourceHasHumidity || !Double.isFinite(windSourceHumidity)) {
			windSourceHasHumidity = false;
			windSourceHumidity = 0.0;
		} else {
			windSourceHumidity = MathUtil.clamp(windSourceHumidity, 0.0, 1.0);
		}
		if (!Double.isFinite(windSourceAblStability)) {
			windSourceAblStability = 0.0;
		}
		windSourceAblStability = MathUtil.clamp(windSourceAblStability, -1.0, 1.0);
		if (!Double.isFinite(windSourceAblMixingStrength)) {
			windSourceAblMixingStrength = 0.0;
		}
		windSourceAblMixingStrength = MathUtil.clamp(windSourceAblMixingStrength, 0.0, 1.0);
		if (!Double.isFinite(waterImmersionIntensity)) {
			waterImmersionIntensity = 0.0;
		}
		waterImmersionIntensity = MathUtil.clamp(waterImmersionIntensity, 0.0, 1.0);
		if (!Double.isFinite(precipitationWetnessIntensity)) {
			precipitationWetnessIntensity = 0.0;
		}
		precipitationWetnessIntensity = MathUtil.clamp(precipitationWetnessIntensity, 0.0, 1.0);
		if (!Double.isFinite(ambientTemperatureCelsius)) {
			ambientTemperatureCelsius = 25.0;
		}
		ambientTemperatureCelsius = MathUtil.clamp(ambientTemperatureCelsius, -40.0, 65.0);
	}

	public static DroneEnvironment calm() {
		return new DroneEnvironment(Vec3.ZERO, 1.0, Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0, Double.POSITIVE_INFINITY, null, null, null, null, 0.0, null, 0.0, 25.0, null, null, WIND_SOURCE_CALM, true, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, WIND_SOURCE_LEVEL_NONE, WIND_SOURCE_AUTHORITY_NONE, -1L, 0.0, 0.0, 0.0, false, 0.0, false, 0.0, 0.0, 0.0);
	}

	public static double standardAtmospherePressureRatio(double altitudeMeters) {
		double altitude = MathUtil.clamp(altitudeMeters, -1000.0, 18000.0);
		double base = 1.0 - STANDARD_LAPSE_RATE_KELVIN_PER_METER * altitude / STANDARD_SEA_LEVEL_TEMPERATURE_KELVIN;
		return Math.pow(MathUtil.clamp(base, 0.15, 1.25), STANDARD_PRESSURE_EXPONENT);
	}

	public static double standardAtmosphereAirDensityRatio(double altitudeMeters, double ambientTemperatureCelsius) {
		return standardAtmosphereAirDensityRatio(altitudeMeters, ambientTemperatureCelsius, 0.0);
	}

	public static double standardAtmosphereAirDensityRatio(
			double altitudeMeters,
			double ambientTemperatureCelsius,
			double pressureAnomalyPascals
	) {
		if (!Double.isFinite(ambientTemperatureCelsius)) {
			ambientTemperatureCelsius = 25.0;
		}
		double ambientKelvin = MathUtil.clamp(ambientTemperatureCelsius + 273.15, 233.15, 338.15);
		double densityRatio = standardAtmospherePressureRatio(altitudeMeters)
				* STANDARD_SEA_LEVEL_TEMPERATURE_KELVIN
				/ ambientKelvin;
		return MathUtil.clamp(densityRatio * pressureAnomalyAirDensityMultiplier(pressureAnomalyPascals), 0.35, 1.35);
	}

	public static double pressureAnomalyAirDensityMultiplier(double pressureAnomalyPascals) {
		return MathUtil.clamp(
				(SEA_LEVEL_PRESSURE_PASCALS + sanitizePressureAnomalyPascals(pressureAnomalyPascals))
						/ SEA_LEVEL_PRESSURE_PASCALS,
				0.90,
				1.10
		);
	}

	public double windSourceQualityFactor() {
		return windSourceQualityFactor(
				windSourceTrustedForGameplay,
				windSourceConfidence,
				windSourceFreshnessAgeTicks
		);
	}

	public static double windSourceQualityFactor(
			boolean trustedForGameplay,
			double confidence,
			long freshnessAgeTicks
	) {
		if (!trustedForGameplay) {
			return 0.0;
		}
		double trust = Double.isFinite(confidence) ? MathUtil.clamp(confidence, 0.0, 1.0) : 0.0;
		return trust * windSourceFreshnessFactor(freshnessAgeTicks);
	}

	public static double windSourceFreshnessFactor(long freshnessAgeTicks) {
		if (freshnessAgeTicks < 0L) {
			return 1.0;
		}
		if (freshnessAgeTicks <= WIND_SOURCE_FULL_TRUST_AGE_TICKS) {
			return 1.0;
		}
		if (freshnessAgeTicks >= WIND_SOURCE_ZERO_TRUST_AGE_TICKS) {
			return 0.0;
		}
		double t = (freshnessAgeTicks - WIND_SOURCE_FULL_TRUST_AGE_TICKS)
				/ (double) (WIND_SOURCE_ZERO_TRUST_AGE_TICKS - WIND_SOURCE_FULL_TRUST_AGE_TICKS);
		double smooth = t * t * (3.0 - 2.0 * t);
		return 1.0 - smooth;
	}

	public static double speedOfSoundMetersPerSecond(double ambientTemperatureCelsius) {
		if (!Double.isFinite(ambientTemperatureCelsius)) {
			ambientTemperatureCelsius = 25.0;
		}
		double temperatureKelvin = MathUtil.clamp(ambientTemperatureCelsius + 273.15, 233.15, 338.15);
		return Math.sqrt(1.4 * 287.05 * temperatureKelvin);
	}

	public double effectiveAirDensityRatio() {
		return MathUtil.clamp(
				airDensityRatio * moistAirDensityMultiplier(ambientTemperatureCelsius, ambientHumidity()),
				0.35,
				1.35
		);
	}

	public double ambientHumidity() {
		double ambientHumidity = precipitationWetnessIntensity;
		if (windSourceHasHumidity) {
			ambientHumidity = Math.max(ambientHumidity, windSourceHumidity);
		}
		return MathUtil.clamp(ambientHumidity, 0.0, 1.0);
	}

	public static double moistAirDensityMultiplier(double ambientTemperatureCelsius, double precipitationWetnessIntensity) {
		if (!Double.isFinite(ambientTemperatureCelsius)) {
			ambientTemperatureCelsius = 25.0;
		}
		double wetness = MathUtil.clamp(precipitationWetnessIntensity, 0.0, 1.0);
		if (wetness <= 1.0e-9) {
			return 1.0;
		}

		double temperatureCelsius = MathUtil.clamp(ambientTemperatureCelsius, -40.0, 65.0);
		double saturationVaporPressureHectopascals = saturationVaporPressureHectopascals(temperatureCelsius);
		double vaporPressureFraction = saturationVaporPressureHectopascals / SEA_LEVEL_PRESSURE_HECTOPASCALS;
		double densityRelief = WATER_VAPOR_DRY_AIR_DENSITY_RELIEF * vaporPressureFraction * wetness;
		return MathUtil.clamp(1.0 - densityRelief, 0.94, 1.0);
	}

	public double moistAirCoolingMultiplier() {
		return moistAirCoolingMultiplier(ambientTemperatureCelsius, ambientHumidity());
	}

	public static double moistAirCoolingMultiplier(double ambientTemperatureCelsius, double humidity) {
		if (!Double.isFinite(ambientTemperatureCelsius)) {
			ambientTemperatureCelsius = 25.0;
		}
		double wetness = MathUtil.clamp(humidity, 0.0, 1.0);
		if (wetness <= 1.0e-9) {
			return 1.0;
		}

		double temperatureCelsius = MathUtil.clamp(ambientTemperatureCelsius, -40.0, 65.0);
		double saturationVaporPressureHectopascals = saturationVaporPressureHectopascals(temperatureCelsius);
		double vaporPressureFraction = saturationVaporPressureHectopascals / SEA_LEVEL_PRESSURE_HECTOPASCALS;
		double coolingRelief = wetness * (0.030 + 0.45 * vaporPressureFraction);
		return MathUtil.clamp(1.0 - coolingRelief, 0.90, 1.0);
	}

	private static double saturationVaporPressureHectopascals(double ambientTemperatureCelsius) {
		double celsius = MathUtil.clamp(ambientTemperatureCelsius, -40.0, 65.0);
		return 6.112 * Math.exp(17.67 * celsius / (celsius + 243.5));
	}

	public static double barometricPressureHectopascals(
			double altitudeMeters,
			double airDensityRatio,
			double ambientTemperatureCelsius
	) {
		return barometricPressureHectopascals(altitudeMeters, airDensityRatio, ambientTemperatureCelsius, 0.0);
	}

	public static double barometricPressureHectopascals(
			double altitudeMeters,
			double airDensityRatio,
			double ambientTemperatureCelsius,
			double pressureAnomalyPascals
	) {
		if (!Double.isFinite(airDensityRatio)) {
			airDensityRatio = standardAtmosphereAirDensityRatio(altitudeMeters, ambientTemperatureCelsius);
		}
		double densityPressureScale = MathUtil.clamp(0.97 + 0.03 * airDensityRatio, 0.94, 1.05);
		double pressure = SEA_LEVEL_PRESSURE_HECTOPASCALS
				* standardAtmospherePressureRatio(altitudeMeters)
				* densityPressureScale
				+ sanitizePressureAnomalyPascals(pressureAnomalyPascals) / 100.0;
		return MathUtil.clamp(pressure, 50.0, 1100.0);
	}

	public double groundEffectThrustMultiplier(DroneConfig config) {
		return groundEffectThrustMultiplier(config, groundClearanceMeters);
	}

	public static double groundEffectThrustMultiplier(DroneConfig config, double groundClearanceMeters) {
		if (config == null
				|| groundClearanceMeters >= config.groundEffectHeightMeters()
				|| config.groundEffectHeightMeters() <= 1.0e-6) {
			return 1.0;
		}
		double clearanceOverRadius = surfaceEffectClearanceOverRadius(config, groundClearanceMeters);
		double extraLift = SurfaceNearfieldCalibration.jirsGroundCurveFitExtraLift(clearanceOverRadius)
				* surfaceLiftBoostScale(config);
		return 1.0 + MathUtil.clamp(extraLift, 0.0, MAX_GROUND_EFFECT_EXTRA_THRUST_FRACTION);
	}

	public double groundEffectIntensity(DroneConfig config) {
		return groundEffectIntensity(config, groundClearanceMeters);
	}

	public static double groundEffectIntensity(DroneConfig config, double groundClearanceMeters) {
		if (groundClearanceMeters >= config.groundEffectHeightMeters() || config.groundEffectHeightMeters() <= 1.0e-6) {
			return 0.0;
		}

		double clearance = Math.max(0.0, groundClearanceMeters);
		double zjuHeightShape = ZJU_GROUND_EFFECT_G1_METERS_SQUARED
				/ (clearance * clearance + ZJU_GROUND_EFFECT_G1_METERS_SQUARED);
		double cutoffFade = 1.0 - smoothStep(
				config.groundEffectHeightMeters() * 0.80,
				config.groundEffectHeightMeters(),
				clearance
		);
		return MathUtil.clamp(zjuHeightShape * cutoffFade, 0.0, 1.0);
	}

	public double groundEffectLevelingTorqueIntensity(DroneConfig config) {
		return groundEffectLevelingTorqueIntensity(config, groundClearanceMeters);
	}

	public static double groundEffectLevelingTorqueIntensity(DroneConfig config, double groundClearanceMeters) {
		if (groundClearanceMeters >= config.groundEffectHeightMeters() || config.groundEffectHeightMeters() <= 1.0e-6) {
			return 0.0;
		}

		double clearance = Math.max(0.0, groundClearanceMeters);
		double peakShape = zjuGroundEffectLevelingTorqueShape(ZJU_GROUND_EFFECT_LEVELING_TORQUE_PEAK_HEIGHT_METERS);
		if (peakShape <= 1.0e-9) {
			return 0.0;
		}
		double normalizedShape = zjuGroundEffectLevelingTorqueShape(clearance) / peakShape;
		double cutoffFade = 1.0 - smoothStep(
				config.groundEffectHeightMeters() * 0.80,
				config.groundEffectHeightMeters(),
				clearance
		);
		return MathUtil.clamp(normalizedShape * cutoffFade, 0.0, 1.0);
	}

	private static double zjuGroundEffectLevelingTorqueShape(double groundClearanceMeters) {
		double clearance = Math.max(0.0, groundClearanceMeters);
		double denominator = clearance * clearance
				+ ZJU_GROUND_EFFECT_G3_METERS * clearance
				+ ZJU_GROUND_EFFECT_G4_METERS_SQUARED;
		if (denominator <= 1.0e-9) {
			return 0.0;
		}
		return ZJU_GROUND_EFFECT_G5 * clearance / (denominator * denominator);
	}

	private static double surfaceLiftBoostScale(DroneConfig config) {
		if (config == null) {
			return 0.0;
		}
		return MathUtil.clamp(
				config.groundEffectMaxThrustBoost() / RACING_QUAD_REFERENCE_GROUND_EFFECT_BOOST,
				0.0,
				MAX_GROUND_EFFECT_EXTRA_THRUST_FRACTION / RACING_QUAD_REFERENCE_GROUND_EFFECT_BOOST
		);
	}

	private static double surfaceEffectClearanceOverRadius(DroneConfig config, double clearanceMeters) {
		if (config == null || config.rotors().isEmpty()) {
			return Double.POSITIVE_INFINITY;
		}
		RotorSpec rotor = config.rotors().get(0);
		if (rotor.radiusMeters() <= 1.0e-9 || !Double.isFinite(clearanceMeters)) {
			return Double.POSITIVE_INFINITY;
		}
		return Math.max(0.0, clearanceMeters) / rotor.radiusMeters();
	}

	public static double weightedGroundEffectThrustMultiplier(
			DroneConfig config,
			double[] groundClearancesMeters,
			double[] weights
	) {
		return weightedSurfaceEffectThrustMultiplier(config, groundClearancesMeters, weights, false);
	}

	public double ceilingEffectIntensity(DroneConfig config) {
		return ceilingEffectIntensity(config, ceilingClearanceMeters);
	}

	public static double ceilingEffectIntensity(DroneConfig config, double ceilingClearanceMeters) {
		double effectHeight = ceilingEffectHeightMeters(config);
		if (ceilingClearanceMeters >= effectHeight || effectHeight <= 1.0e-6) {
			return 0.0;
		}

		double proximity = 1.0 - ceilingClearanceMeters / effectHeight;
		return MathUtil.clamp(proximity * proximity, 0.0, 1.0);
	}

	public double ceilingEffectThrustMultiplier(DroneConfig config) {
		return ceilingEffectThrustMultiplier(config, ceilingClearanceMeters);
	}

	public static double ceilingEffectThrustMultiplier(DroneConfig config, double ceilingClearanceMeters) {
		if (config == null) {
			return 1.0;
		}
		double effectHeight = ceilingEffectHeightMeters(config);
		if (ceilingClearanceMeters >= effectHeight || effectHeight <= 1.0e-6) {
			return 1.0;
		}
		double clearanceOverRadius = surfaceEffectClearanceOverRadius(config, ceilingClearanceMeters);
		double extraLift = SurfaceNearfieldCalibration.jirsCeilingCurveFitExtraLift(clearanceOverRadius)
				* surfaceLiftBoostScale(config);
		return 1.0 + MathUtil.clamp(extraLift, 0.0, MAX_CEILING_EFFECT_EXTRA_THRUST_FRACTION);
	}

	public static double weightedCeilingEffectThrustMultiplier(
			DroneConfig config,
			double[] ceilingClearancesMeters,
			double[] weights
	) {
		return weightedSurfaceEffectThrustMultiplier(config, ceilingClearancesMeters, weights, true);
	}

	public static double partialSurfaceDiameterOverPropDiameter(DroneConfig config, double patchDiameterMeters) {
		if (config == null || config.rotors().isEmpty() || patchDiameterMeters <= 0.0) {
			return 0.0;
		}
		if (patchDiameterMeters == Double.POSITIVE_INFINITY) {
			return Double.POSITIVE_INFINITY;
		}
		if (!Double.isFinite(patchDiameterMeters)) {
			return 0.0;
		}

		RotorSpec rotor = config.rotors().get(0);
		double propellerDiameterMeters = rotor.radiusMeters() * 2.0;
		if (propellerDiameterMeters <= 1.0e-9) {
			return 0.0;
		}
		return patchDiameterMeters / propellerDiameterMeters;
	}

	public static double partialSurfaceEffectGate(DroneConfig config, double patchDiameterMeters) {
		double diameterOverPropDiameter = partialSurfaceDiameterOverPropDiameter(config, patchDiameterMeters);
		if (diameterOverPropDiameter == Double.POSITIVE_INFINITY) {
			return 1.0;
		}
		if (!Double.isFinite(diameterOverPropDiameter)) {
			return 0.0;
		}
		return smoothStep(
				PARTIAL_SURFACE_NEGLIGIBLE_DIAMETER_OVER_PROP_DIAMETER,
				PARTIAL_SURFACE_FULL_LIKE_DIAMETER_OVER_PROP_DIAMETER,
				diameterOverPropDiameter
		);
	}

	public static double partialGroundEffectThrustMultiplier(
			DroneConfig config,
			double groundClearanceMeters,
			double patchDiameterMeters
	) {
		if (config == null) {
			return 1.0;
		}
		double fullSurfaceMultiplier = groundEffectThrustMultiplier(config, groundClearanceMeters);
		double gate = partialSurfaceEffectGate(config, patchDiameterMeters);
		return 1.0 + (fullSurfaceMultiplier - 1.0) * gate;
	}

	public static double partialCeilingEffectThrustMultiplier(
			DroneConfig config,
			double ceilingClearanceMeters,
			double patchDiameterMeters
	) {
		if (config == null) {
			return 1.0;
		}
		double fullSurfaceMultiplier = ceilingEffectThrustMultiplier(config, ceilingClearanceMeters);
		double gate = partialSurfaceEffectGate(config, patchDiameterMeters);
		return 1.0 + (fullSurfaceMultiplier - 1.0) * gate;
	}

	private static double weightedSurfaceEffectThrustMultiplier(
			DroneConfig config,
			double[] clearancesMeters,
			double[] weights,
			boolean ceiling
	) {
		if (config == null || clearancesMeters == null || clearancesMeters.length == 0) {
			return 1.0;
		}

		double supportedWeightedMultiplier = 0.0;
		double supportedWeight = 0.0;
		double totalWeight = 0.0;
		for (int i = 0; i < clearancesMeters.length; i++) {
			double weight = weights != null && i < weights.length ? weights[i] : 1.0;
			if (!Double.isFinite(weight) || weight <= 0.0) {
				continue;
			}
			totalWeight += weight;
			double clearance = clearancesMeters[i];
			if (!Double.isFinite(clearance)) {
				continue;
			}
			double multiplier = ceiling
					? ceilingEffectThrustMultiplier(config, clearance)
					: groundEffectThrustMultiplier(config, clearance);
			supportedWeightedMultiplier += multiplier * weight;
			supportedWeight += weight;
		}
		if (totalWeight <= 1.0e-9 || supportedWeight <= 1.0e-9) {
			return 1.0;
		}
		double partialSurfaceGate = partialSurfaceCoverageGate(config, supportedWeight / totalWeight);
		double supportedAverageMultiplier = supportedWeightedMultiplier / supportedWeight;
		return MathUtil.clamp(1.0 + (supportedAverageMultiplier - 1.0) * partialSurfaceGate, 0.35, 2.0);
	}

	public static double weightedSurfaceEffectSupportCoverage(
			double[] clearancesMeters,
			double[] weights
	) {
		if (clearancesMeters == null || clearancesMeters.length == 0) {
			return 0.0;
		}
		double supportedWeight = 0.0;
		double totalWeight = 0.0;
		for (int i = 0; i < clearancesMeters.length; i++) {
			double weight = weights != null && i < weights.length ? weights[i] : 1.0;
			if (!Double.isFinite(weight) || weight <= 0.0) {
				continue;
			}
			totalWeight += weight;
			if (Double.isFinite(clearancesMeters[i])) {
				supportedWeight += weight;
			}
		}
		return totalWeight <= 1.0e-9
				? 0.0
				: MathUtil.clamp(supportedWeight / totalWeight, 0.0, 1.0);
	}

	public static double partialSurfaceCoverageGate(
			DroneConfig config,
			double supportedCoverageFraction
	) {
		return partialSurfaceEffectGate(
				config,
				partialSurfaceCoveragePatchDiameterMeters(config, supportedCoverageFraction)
		);
	}

	public static double partialSurfaceCoveragePatchDiameterMeters(
			DroneConfig config,
			double supportedCoverageFraction
	) {
		if (config == null || config.rotors().isEmpty() || !Double.isFinite(supportedCoverageFraction)) {
			return 0.0;
		}
		double coverage = MathUtil.clamp(supportedCoverageFraction, 0.0, 1.0);
		if (coverage <= 0.0) {
			return 0.0;
		}
		RotorSpec rotor = config.rotors().get(0);
		double propellerDiameterMeters = rotor.radiusMeters() * 2.0;
		if (propellerDiameterMeters <= 1.0e-9) {
			return 0.0;
		}
		return propellerDiameterMeters * Math.sqrt(coverage);
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	private static double sanitizePressureAnomalyPascals(double value) {
		return Double.isFinite(value)
				? MathUtil.clamp(value, -MAX_WIND_SOURCE_PRESSURE_ANOMALY_PASCALS, MAX_WIND_SOURCE_PRESSURE_ANOMALY_PASCALS)
				: 0.0;
	}

	private static double sanitizeWindSourceSpeed(double value) {
		return Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 30.0) : 0.0;
	}

	private static Vec3 sanitizeWindSourceVelocity(Vec3 value) {
		return value == null || !value.isFinite() ? Vec3.ZERO : value.clamp(-30.0, 30.0);
	}

	public double rotorThrustMultiplier(int rotorIndex, DroneConfig config) {
		if (rotorIndex >= 0 && rotorIndex < rotorThrustMultipliers.length) {
			return rotorThrustMultipliers[rotorIndex];
		}
		return groundEffectThrustMultiplier(config) * ceilingEffectThrustMultiplier(config);
	}

	public double rotorThrustAsymmetry(DroneConfig config) {
		int rotorCount = rotorThrustMultipliers.length > 1
				? rotorThrustMultipliers.length
				: Math.max(0, config.rotors().size());
		if (rotorCount <= 1) {
			return 0.0;
		}

		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < rotorCount; i++) {
			double multiplier = rotorThrustMultiplier(i, config);
			min = Math.min(min, multiplier);
			max = Math.max(max, multiplier);
		}
		return Double.isFinite(min) && Double.isFinite(max) ? max - min : 0.0;
	}

	public double[] rotorThrustMultipliers() {
		return rotorThrustMultipliers.clone();
	}

	public double rotorGroundSurfaceCoverage(int rotorIndex) {
		if (rotorIndex >= 0 && rotorIndex < rotorGroundSurfaceCoverages.length) {
			return rotorGroundSurfaceCoverages[rotorIndex];
		}
		return Double.isFinite(groundClearanceMeters) ? 1.0 : 0.0;
	}

	public double rotorCeilingSurfaceCoverage(int rotorIndex) {
		if (rotorIndex >= 0 && rotorIndex < rotorCeilingSurfaceCoverages.length) {
			return rotorCeilingSurfaceCoverages[rotorIndex];
		}
		return Double.isFinite(ceilingClearanceMeters) ? 1.0 : 0.0;
	}

	public double rotorGroundSurfaceGate(int rotorIndex) {
		if (rotorIndex >= 0 && rotorIndex < rotorGroundSurfaceGates.length) {
			return rotorGroundSurfaceGates[rotorIndex];
		}
		return rotorGroundSurfaceCoverage(rotorIndex) > 0.0 ? 1.0 : 0.0;
	}

	public double rotorCeilingSurfaceGate(int rotorIndex) {
		if (rotorIndex >= 0 && rotorIndex < rotorCeilingSurfaceGates.length) {
			return rotorCeilingSurfaceGates[rotorIndex];
		}
		return rotorCeilingSurfaceCoverage(rotorIndex) > 0.0 ? 1.0 : 0.0;
	}

	public double[] rotorGroundSurfaceCoverages() {
		return rotorGroundSurfaceCoverages.clone();
	}

	public double[] rotorCeilingSurfaceCoverages() {
		return rotorCeilingSurfaceCoverages.clone();
	}

	public double[] rotorGroundSurfaceGates() {
		return rotorGroundSurfaceGates.clone();
	}

	public double[] rotorCeilingSurfaceGates() {
		return rotorCeilingSurfaceGates.clone();
	}

	public double rotorFlowObstruction(int rotorIndex) {
		if (rotorIndex >= 0 && rotorIndex < rotorFlowObstructions.length) {
			return rotorFlowObstructions[rotorIndex];
		}
		return 0.0;
	}

	public double maxRotorFlowObstruction() {
		double max = 0.0;
		for (double obstruction : rotorFlowObstructions) {
			max = Math.max(max, obstruction);
		}
		return max;
	}

	public double[] rotorFlowObstructions() {
		return rotorFlowObstructions.clone();
	}

	public Vec3 rotorFlowObstructionDirectionBody(int rotorIndex) {
		if (rotorIndex >= 0 && rotorIndex < rotorFlowObstructionDirectionsBody.length) {
			return rotorFlowObstructionDirectionsBody[rotorIndex];
		}
		return Vec3.ZERO;
	}

	public Vec3[] rotorFlowObstructionDirectionsBody() {
		return rotorFlowObstructionDirectionsBody.clone();
	}

	public double rotorWaterImmersion(int rotorIndex) {
		if (rotorIndex >= 0 && rotorIndex < rotorWaterImmersions.length) {
			return rotorWaterImmersions[rotorIndex];
		}
		return waterImmersionIntensity;
	}

	public double maxRotorWaterImmersion() {
		double max = waterImmersionIntensity;
		for (double immersion : rotorWaterImmersions) {
			max = Math.max(max, immersion);
		}
		return max;
	}

	public double[] rotorWaterImmersions() {
		return rotorWaterImmersions.clone();
	}

	public double rotorPrecipitationWetness(int rotorIndex) {
		if (rotorIndex >= 0 && rotorIndex < rotorPrecipitationWetnesses.length) {
			return rotorPrecipitationWetnesses[rotorIndex];
		}
		return precipitationWetnessIntensity;
	}

	public double maxRotorPrecipitationWetness() {
		double max = precipitationWetnessIntensity;
		for (double wetness : rotorPrecipitationWetnesses) {
			max = Math.max(max, wetness);
		}
		return max;
	}

	public double[] rotorPrecipitationWetnesses() {
		return rotorPrecipitationWetnesses.clone();
	}

	public Vec3 rotorWindVelocityWorldMetersPerSecond(int rotorIndex) {
		if (rotorIndex >= 0 && rotorIndex < rotorWindVelocityWorldMetersPerSecond.length) {
			return rotorWindVelocityWorldMetersPerSecond[rotorIndex];
		}
		return windVelocityWorldMetersPerSecond;
	}

	public Vec3[] rotorWindVelocityWorldMetersPerSecond() {
		return rotorWindVelocityWorldMetersPerSecond.clone();
	}

	public Vec3 rotorDiskWindGradientBodyMetersPerSecond(int rotorIndex) {
		if (rotorIndex >= 0 && rotorIndex < rotorDiskWindGradientBodyMetersPerSecond.length) {
			return rotorDiskWindGradientBodyMetersPerSecond[rotorIndex];
		}
		return Vec3.ZERO;
	}

	public double rotorDiskWindGradientMagnitudeMetersPerSecond(int rotorIndex) {
		return rotorDiskWindGradientBodyMetersPerSecond(rotorIndex).length();
	}

	public double maxRotorDiskWindGradientMetersPerSecond() {
		double max = 0.0;
		for (Vec3 gradient : rotorDiskWindGradientBodyMetersPerSecond) {
			max = Math.max(max, gradient.length());
		}
		return max;
	}

	public Vec3[] rotorDiskWindGradientBodyMetersPerSecond() {
		return rotorDiskWindGradientBodyMetersPerSecond.clone();
	}

	public double rotorA4mcShelterObstruction(int rotorIndex) {
		if (rotorIndex >= 0 && rotorIndex < rotorA4mcShelterObstructions.length) {
			return rotorA4mcShelterObstructions[rotorIndex];
		}
		return 0.0;
	}

	public double maxRotorA4mcShelterObstruction() {
		double max = 0.0;
		for (double obstruction : rotorA4mcShelterObstructions) {
			max = Math.max(max, obstruction);
		}
		return max;
	}

	public double[] rotorA4mcShelterObstructions() {
		return rotorA4mcShelterObstructions.clone();
	}

	public double rotorLocalVoxelObstacleResidual(int rotorIndex) {
		if (rotorIndex >= 0 && rotorIndex < rotorLocalVoxelObstacleResiduals.length) {
			return rotorLocalVoxelObstacleResiduals[rotorIndex];
		}
		return 1.0;
	}

	public double minRotorLocalVoxelObstacleResidual() {
		double min = 1.0;
		for (double residual : rotorLocalVoxelObstacleResiduals) {
			min = Math.min(min, residual);
		}
		return min;
	}

	public double[] rotorLocalVoxelObstacleResiduals() {
		return rotorLocalVoxelObstacleResiduals.clone();
	}

	public Vec3 rotorA4mcPressureGradientWindBodyMetersPerSecond(int rotorIndex) {
		if (rotorIndex >= 0 && rotorIndex < rotorA4mcPressureGradientWindBodyMetersPerSecond.length) {
			return rotorA4mcPressureGradientWindBodyMetersPerSecond[rotorIndex];
		}
		return Vec3.ZERO;
	}

	public double rotorA4mcPressureGradientWindMagnitudeMetersPerSecond(int rotorIndex) {
		return rotorA4mcPressureGradientWindBodyMetersPerSecond(rotorIndex).length();
	}

	public double maxRotorA4mcPressureGradientWindMetersPerSecond() {
		double max = 0.0;
		for (Vec3 wind : rotorA4mcPressureGradientWindBodyMetersPerSecond) {
			max = Math.max(max, wind.length());
		}
		return max;
	}

	public Vec3[] rotorA4mcPressureGradientWindBodyMetersPerSecond() {
		return rotorA4mcPressureGradientWindBodyMetersPerSecond.clone();
	}

	private static double ceilingEffectHeightMeters(DroneConfig config) {
		if (config.groundEffectHeightMeters() <= 1.0e-6) {
			return 0.0;
		}
		return Math.max(0.35, config.groundEffectHeightMeters() * 1.25);
	}

	private static double[] sanitizeRotorThrustMultipliers(double[] multipliers) {
		if (multipliers == null || multipliers.length == 0) {
			return new double[0];
		}

		double[] sanitized = multipliers.clone();
		for (int i = 0; i < sanitized.length; i++) {
			if (!Double.isFinite(sanitized[i])) {
				sanitized[i] = 1.0;
			}
			sanitized[i] = MathUtil.clamp(sanitized[i], 0.35, 2.0);
		}
		return sanitized;
	}

	private static double[] sanitizeUnitArray(double[] values) {
		if (values == null || values.length == 0) {
			return new double[0];
		}

		double[] sanitized = values.clone();
		for (int i = 0; i < sanitized.length; i++) {
			if (!Double.isFinite(sanitized[i])) {
				sanitized[i] = 0.0;
			}
			sanitized[i] = MathUtil.clamp(sanitized[i], 0.0, 1.0);
		}
		return sanitized;
	}

	private static double[] sanitizeUnitArrayOrOne(double[] values) {
		if (values == null || values.length == 0) {
			return new double[0];
		}

		double[] sanitized = values.clone();
		for (int i = 0; i < sanitized.length; i++) {
			if (!Double.isFinite(sanitized[i])) {
				sanitized[i] = 1.0;
			}
			sanitized[i] = MathUtil.clamp(sanitized[i], 0.0, 1.0);
		}
		return sanitized;
	}

	private static Vec3[] sanitizeDirectionArray(Vec3[] values) {
		if (values == null || values.length == 0) {
			return new Vec3[0];
		}

		Vec3[] sanitized = values.clone();
		for (int i = 0; i < sanitized.length; i++) {
			Vec3 value = sanitized[i];
			if (value == null || !value.isFinite()) {
				sanitized[i] = Vec3.ZERO;
			} else {
				sanitized[i] = value.normalized();
			}
		}
		return sanitized;
	}

	private static Vec3[] sanitizeWindArray(Vec3[] values) {
		if (values == null || values.length == 0) {
			return new Vec3[0];
		}

		Vec3[] sanitized = values.clone();
		for (int i = 0; i < sanitized.length; i++) {
			Vec3 value = sanitized[i];
			sanitized[i] = value == null || !value.isFinite() ? Vec3.ZERO : value.clamp(-30.0, 30.0);
		}
		return sanitized;
	}

	private static Vec3[] sanitizeDiskWindGradientArray(Vec3[] values) {
		if (values == null || values.length == 0) {
			return new Vec3[0];
		}

		Vec3[] sanitized = values.clone();
		for (int i = 0; i < sanitized.length; i++) {
			Vec3 value = sanitized[i];
			sanitized[i] = value == null || !value.isFinite() ? Vec3.ZERO : value.clamp(-12.0, 12.0);
		}
		return sanitized;
	}

	private static String sanitizeWindSourceId(String value) {
		return sanitizeWindSourceIdWithFallback(value, WIND_SOURCE_INTERNAL);
	}

	private static String sanitizeWindSourceIdWithFallback(String value, String fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		String trimmed = value.trim();
		StringBuilder builder = new StringBuilder(trimmed.length());
		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);
			if (c >= 'A' && c <= 'Z') {
				builder.append((char) (c + 32));
			} else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
				builder.append(c);
			} else {
				builder.append('_');
			}
		}
		return builder.length() == 0 ? fallback : builder.toString();
	}
}
