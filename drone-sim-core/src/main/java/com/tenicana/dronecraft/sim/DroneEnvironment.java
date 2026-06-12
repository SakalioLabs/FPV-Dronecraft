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
		double ambientTemperatureCelsius
) {
	private static final double SEA_LEVEL_PRESSURE_HECTOPASCALS = 1013.25;
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
		return new DroneEnvironment(Vec3.ZERO, 1.0, Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0, Double.POSITIVE_INFINITY, null, null, null, null, 0.0, null, 0.0, 25.0);
	}

	public static double standardAtmospherePressureRatio(double altitudeMeters) {
		double altitude = MathUtil.clamp(altitudeMeters, -1000.0, 18000.0);
		double base = 1.0 - STANDARD_LAPSE_RATE_KELVIN_PER_METER * altitude / STANDARD_SEA_LEVEL_TEMPERATURE_KELVIN;
		return Math.pow(MathUtil.clamp(base, 0.15, 1.25), STANDARD_PRESSURE_EXPONENT);
	}

	public static double standardAtmosphereAirDensityRatio(double altitudeMeters, double ambientTemperatureCelsius) {
		if (!Double.isFinite(ambientTemperatureCelsius)) {
			ambientTemperatureCelsius = 25.0;
		}
		double ambientKelvin = MathUtil.clamp(ambientTemperatureCelsius + 273.15, 233.15, 338.15);
		double densityRatio = standardAtmospherePressureRatio(altitudeMeters)
				* STANDARD_SEA_LEVEL_TEMPERATURE_KELVIN
				/ ambientKelvin;
		return MathUtil.clamp(densityRatio, 0.35, 1.35);
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
				airDensityRatio * moistAirDensityMultiplier(ambientTemperatureCelsius, precipitationWetnessIntensity),
				0.35,
				1.35
		);
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

	private static double saturationVaporPressureHectopascals(double ambientTemperatureCelsius) {
		double celsius = MathUtil.clamp(ambientTemperatureCelsius, -40.0, 65.0);
		return 6.112 * Math.exp(17.67 * celsius / (celsius + 243.5));
	}

	public static double barometricPressureHectopascals(
			double altitudeMeters,
			double airDensityRatio,
			double ambientTemperatureCelsius
	) {
		if (!Double.isFinite(airDensityRatio)) {
			airDensityRatio = standardAtmosphereAirDensityRatio(altitudeMeters, ambientTemperatureCelsius);
		}
		double densityPressureScale = MathUtil.clamp(0.97 + 0.03 * airDensityRatio, 0.94, 1.05);
		return SEA_LEVEL_PRESSURE_HECTOPASCALS
				* standardAtmospherePressureRatio(altitudeMeters)
				* densityPressureScale;
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

		double weightedMultiplier = 0.0;
		double totalWeight = 0.0;
		for (int i = 0; i < clearancesMeters.length; i++) {
			double weight = weights != null && i < weights.length ? weights[i] : 1.0;
			if (!Double.isFinite(weight) || weight <= 0.0) {
				continue;
			}
			double clearance = clearancesMeters[i];
			double multiplier = ceiling
					? ceilingEffectThrustMultiplier(config, clearance)
					: groundEffectThrustMultiplier(config, clearance);
			weightedMultiplier += multiplier * weight;
			totalWeight += weight;
		}
		if (totalWeight <= 1.0e-9) {
			return 1.0;
		}
		return MathUtil.clamp(weightedMultiplier / totalWeight, 0.35, 2.0);
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
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
}
