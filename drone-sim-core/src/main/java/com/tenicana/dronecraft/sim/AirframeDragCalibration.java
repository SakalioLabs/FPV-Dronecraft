package com.tenicana.dronecraft.sim;

public final class AirframeDragCalibration {
	private static final double EPSILON = 1.0e-12;
	public static final double SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER = 1.225;
	private static final double IMAV_2022_BASE_DRAG_NEWTON_SECONDS_PER_METER = 0.105;
	private static final double IMAV_2022_MASS_DRAG_NEWTON_SECONDS_PER_METER_PER_KG = 0.087;

	private AirframeDragCalibration() {
	}

	public enum Axis {
		X,
		Y,
		Z
	}

	public record Coastdown(
			Axis axis,
			double startSpeedMetersPerSecond,
			double endSpeedMetersPerSecond,
			double linearDampingCoefficient,
			double bodyQuadraticCoefficient,
			double timeSeconds,
			double distanceMeters,
			double initialDragForceNewtons,
			double finalDragForceNewtons,
			double referenceLinearDragCoefficient,
			double referenceTimeSeconds,
			double referenceDistanceMeters
	) {
		public double timeRatioToReference() {
			return timeSeconds / referenceTimeSeconds;
		}

		public double distanceRatioToReference() {
			return distanceMeters / referenceDistanceMeters;
		}
	}

	public record BodyDragFit(
			Axis axis,
			double startSpeedMetersPerSecond,
			double endSpeedMetersPerSecond,
			double targetTimeSeconds,
			double linearDampingCoefficient,
			double bodyQuadraticCoefficient,
			double achievedTimeSeconds,
			double achievedDistanceMeters,
			boolean targetReachable
	) {
		public double timeResidualSeconds() {
			return achievedTimeSeconds - targetTimeSeconds;
		}
	}

	public record LevelFlightRequirement(
			Axis axis,
			double speedMetersPerSecond,
			double airDensityRatio,
			double linearDampingCoefficient,
			double bodyQuadraticCoefficient,
			double baseDragForceNewtons,
			double vehicleWeightNewtons,
			double maxTotalThrustNewtons,
			double horizontalThrustMarginNewtons,
			double requiredTotalThrustNewtons,
			double requiredMaxThrustFraction,
			double requiredTiltDegrees,
			boolean reachable
	) {
		public double dragToHorizontalMarginRatio() {
			return horizontalThrustMarginNewtons <= EPSILON
					? Double.POSITIVE_INFINITY
					: baseDragForceNewtons / horizontalThrustMarginNewtons;
		}
	}

	public static Coastdown coastdown(DroneConfig config, Axis axis, double startSpeedMetersPerSecond, double endSpeedMetersPerSecond) {
		requireValidSpeedRange(startSpeedMetersPerSecond, endSpeedMetersPerSecond);
		double massKg = config.massKg();
		double linearCoefficient = config.linearDragCoefficient();
		double quadraticCoefficient = bodyQuadraticCoefficient(config, axis);
		double referenceCoefficient = imav2022MassFitLinearDragCoefficient(config);
		return new Coastdown(
				axis,
				startSpeedMetersPerSecond,
				endSpeedMetersPerSecond,
				linearCoefficient,
				quadraticCoefficient,
				coastdownTimeSeconds(massKg, linearCoefficient, quadraticCoefficient, startSpeedMetersPerSecond, endSpeedMetersPerSecond),
				coastdownDistanceMeters(massKg, linearCoefficient, quadraticCoefficient, startSpeedMetersPerSecond, endSpeedMetersPerSecond),
				dragForceNewtons(linearCoefficient, quadraticCoefficient, startSpeedMetersPerSecond),
				dragForceNewtons(linearCoefficient, quadraticCoefficient, endSpeedMetersPerSecond),
				referenceCoefficient,
				coastdownTimeSeconds(massKg, referenceCoefficient, 0.0, startSpeedMetersPerSecond, endSpeedMetersPerSecond),
				coastdownDistanceMeters(massKg, referenceCoefficient, 0.0, startSpeedMetersPerSecond, endSpeedMetersPerSecond)
		);
	}

	public static BodyDragFit fitBodyQuadraticCoefficientToImav2022Reference(
			DroneConfig config,
			Axis axis,
			double startSpeedMetersPerSecond,
			double endSpeedMetersPerSecond
	) {
		requireValidSpeedRange(startSpeedMetersPerSecond, endSpeedMetersPerSecond);
		double referenceCoefficient = imav2022MassFitLinearDragCoefficient(config);
		double targetTimeSeconds = coastdownTimeSeconds(
				config.massKg(),
				referenceCoefficient,
				0.0,
				startSpeedMetersPerSecond,
				endSpeedMetersPerSecond
		);
		return fitBodyQuadraticCoefficientForCoastdownTime(
				config,
				axis,
				startSpeedMetersPerSecond,
				endSpeedMetersPerSecond,
				targetTimeSeconds
		);
	}

	public static BodyDragFit fitBodyQuadraticCoefficientForCoastdownTime(
			DroneConfig config,
			Axis axis,
			double startSpeedMetersPerSecond,
			double endSpeedMetersPerSecond,
			double targetTimeSeconds
	) {
		requireValidSpeedRange(startSpeedMetersPerSecond, endSpeedMetersPerSecond);
		if (!Double.isFinite(targetTimeSeconds) || targetTimeSeconds <= 0.0) {
			throw new IllegalArgumentException("targetTimeSeconds must be finite and positive.");
		}

		double massKg = config.massKg();
		double linearCoefficient = config.linearDragCoefficient();
		double zeroQuadraticTimeSeconds = coastdownTimeSeconds(
				massKg,
				linearCoefficient,
				0.0,
				startSpeedMetersPerSecond,
				endSpeedMetersPerSecond
		);
		double toleranceSeconds = Math.max(1.0e-9, targetTimeSeconds * 1.0e-9);
		if (targetTimeSeconds >= zeroQuadraticTimeSeconds - toleranceSeconds) {
			boolean targetReachable = targetTimeSeconds <= zeroQuadraticTimeSeconds + toleranceSeconds;
			return bodyDragFit(
					axis,
					startSpeedMetersPerSecond,
					endSpeedMetersPerSecond,
					targetTimeSeconds,
					massKg,
					linearCoefficient,
					0.0,
					targetReachable
			);
		}

		double low = 0.0;
		double high = Math.max(
				Math.max(bodyQuadraticCoefficient(config, axis), EPSILON),
				massKg * (1.0 / endSpeedMetersPerSecond - 1.0 / startSpeedMetersPerSecond) / targetTimeSeconds
		);
		for (int i = 0; i < 128
				&& coastdownTimeSeconds(massKg, linearCoefficient, high, startSpeedMetersPerSecond, endSpeedMetersPerSecond) > targetTimeSeconds;
				i++) {
			high *= 2.0;
			if (!Double.isFinite(high)) {
				throw new IllegalArgumentException("targetTimeSeconds requires a non-finite drag coefficient.");
			}
		}

		for (int i = 0; i < 96; i++) {
			double mid = 0.5 * (low + high);
			double midTime = coastdownTimeSeconds(
					massKg,
					linearCoefficient,
					mid,
					startSpeedMetersPerSecond,
					endSpeedMetersPerSecond
			);
			if (midTime > targetTimeSeconds) {
				low = mid;
			} else {
				high = mid;
			}
		}

		return bodyDragFit(
				axis,
				startSpeedMetersPerSecond,
				endSpeedMetersPerSecond,
				targetTimeSeconds,
				massKg,
				linearCoefficient,
				high,
				true
		);
	}

	public static LevelFlightRequirement levelFlightRequirement(
			DroneConfig config,
			Axis axis,
			double speedMetersPerSecond,
			double airDensityRatio
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (!Double.isFinite(speedMetersPerSecond) || speedMetersPerSecond < 0.0) {
			throw new IllegalArgumentException("speedMetersPerSecond must be finite and non-negative.");
		}

		double densityScale = Double.isFinite(airDensityRatio) ? MathUtil.clamp(airDensityRatio, 0.0, 2.0) : 1.0;
		double linearCoefficient = config.linearDragCoefficient();
		double quadraticCoefficient = bodyQuadraticCoefficient(config, axis);
		double baseDrag = dragForceNewtons(linearCoefficient, quadraticCoefficient, speedMetersPerSecond)
				* densityScale;
		double weight = config.massKg() * config.gravityMetersPerSecondSquared();
		double maxThrust = maxTotalRotorThrustNewtons(config);
		double horizontalMargin = maxThrust <= weight
				? 0.0
				: Math.sqrt(Math.max(0.0, maxThrust * maxThrust - weight * weight));
		double requiredTotalThrust = Math.hypot(weight, baseDrag);
		double requiredMaxThrustFraction = maxThrust <= EPSILON
				? Double.POSITIVE_INFINITY
				: requiredTotalThrust / maxThrust;
		double requiredTiltDegrees = Math.toDegrees(Math.atan2(baseDrag, weight));
		boolean reachable = maxThrust + EPSILON >= requiredTotalThrust;

		return new LevelFlightRequirement(
				axis,
				speedMetersPerSecond,
				densityScale,
				linearCoefficient,
				quadraticCoefficient,
				baseDrag,
				weight,
				maxThrust,
				horizontalMargin,
				requiredTotalThrust,
				requiredMaxThrustFraction,
				requiredTiltDegrees,
				reachable
		);
	}

	public static LevelFlightRequirement worstHorizontalLevelFlightRequirement(
			DroneConfig config,
			double speedMetersPerSecond,
			double airDensityRatio
	) {
		LevelFlightRequirement lateral = levelFlightRequirement(
				config,
				Axis.X,
				speedMetersPerSecond,
				airDensityRatio
		);
		LevelFlightRequirement forward = levelFlightRequirement(
				config,
				Axis.Z,
				speedMetersPerSecond,
				airDensityRatio
		);
		return forward.requiredMaxThrustFraction() >= lateral.requiredMaxThrustFraction()
				? forward
				: lateral;
	}

	public static double maxTotalRotorThrustNewtons(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			return 0.0;
		}
		double maxThrust = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			maxThrust += Math.max(0.0, rotor.maxThrustNewtons());
		}
		return maxThrust;
	}

	public static double imav2022MassFitLinearDragCoefficient(DroneConfig config) {
		return IMAV_2022_BASE_DRAG_NEWTON_SECONDS_PER_METER
				+ IMAV_2022_MASS_DRAG_NEWTON_SECONDS_PER_METER_PER_KG * config.massKg();
	}

	public static double bodyQuadraticCoefficient(DroneConfig config, Axis axis) {
		Vec3 drag = config.bodyDragCoefficients();
		return switch (axis) {
			case X -> drag.x();
			case Y -> drag.y();
			case Z -> drag.z();
		};
	}

	public static double dragForceNewtons(double linearCoefficient, double quadraticCoefficient, double speedMetersPerSecond) {
		double speed = Math.max(0.0, speedMetersPerSecond);
		return linearCoefficient * speed + quadraticCoefficient * speed * speed;
	}

	public static double imav2022ReferenceDragForceNewtons(
			DroneConfig config,
			double speedMetersPerSecond,
			double airDensityRatio
	) {
		double densityScale = Math.max(0.0, airDensityRatio);
		return imav2022MassFitLinearDragCoefficient(config)
				* Math.max(0.0, speedMetersPerSecond)
				* densityScale;
	}

	public static double equivalentLinearCoefficient(double dragForceNewtons, double speedMetersPerSecond) {
		double speed = Math.max(0.0, speedMetersPerSecond);
		if (speed <= EPSILON) {
			return 0.0;
		}
		return Math.max(0.0, dragForceNewtons) / speed;
	}

	public static double equivalentCdAMetersSquared(
			double dragForceNewtons,
			double speedMetersPerSecond,
			double airDensityRatio
	) {
		double speedSquared = Math.max(0.0, speedMetersPerSecond) * Math.max(0.0, speedMetersPerSecond);
		double density = SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * Math.max(0.0, airDensityRatio);
		double dynamicPressure = 0.5 * density * speedSquared;
		if (dynamicPressure <= EPSILON) {
			return 0.0;
		}
		return Math.max(0.0, dragForceNewtons) / dynamicPressure;
	}

	private static double coastdownTimeSeconds(
			double massKg,
			double linearCoefficient,
			double quadraticCoefficient,
			double startSpeedMetersPerSecond,
			double endSpeedMetersPerSecond
	) {
		if (linearCoefficient <= EPSILON && quadraticCoefficient <= EPSILON) {
			return Double.POSITIVE_INFINITY;
		}
		if (linearCoefficient <= EPSILON) {
			return massKg / quadraticCoefficient
					* (1.0 / endSpeedMetersPerSecond - 1.0 / startSpeedMetersPerSecond);
		}
		if (quadraticCoefficient <= EPSILON) {
			return massKg / linearCoefficient
					* Math.log(startSpeedMetersPerSecond / endSpeedMetersPerSecond);
		}
		return massKg / linearCoefficient
				* Math.log(
						startSpeedMetersPerSecond * (linearCoefficient + quadraticCoefficient * endSpeedMetersPerSecond)
								/ (endSpeedMetersPerSecond * (linearCoefficient + quadraticCoefficient * startSpeedMetersPerSecond))
				);
	}

	private static double coastdownDistanceMeters(
			double massKg,
			double linearCoefficient,
			double quadraticCoefficient,
			double startSpeedMetersPerSecond,
			double endSpeedMetersPerSecond
	) {
		if (linearCoefficient <= EPSILON && quadraticCoefficient <= EPSILON) {
			return Double.POSITIVE_INFINITY;
		}
		if (linearCoefficient <= EPSILON) {
			return massKg / quadraticCoefficient
					* Math.log(startSpeedMetersPerSecond / endSpeedMetersPerSecond);
		}
		if (quadraticCoefficient <= EPSILON) {
			return massKg / linearCoefficient
					* (startSpeedMetersPerSecond - endSpeedMetersPerSecond);
		}
		return massKg / quadraticCoefficient
				* Math.log(
						(linearCoefficient + quadraticCoefficient * startSpeedMetersPerSecond)
								/ (linearCoefficient + quadraticCoefficient * endSpeedMetersPerSecond)
				);
	}

	private static BodyDragFit bodyDragFit(
			Axis axis,
			double startSpeedMetersPerSecond,
			double endSpeedMetersPerSecond,
			double targetTimeSeconds,
			double massKg,
			double linearCoefficient,
			double quadraticCoefficient,
			boolean targetReachable
	) {
		double achievedTimeSeconds = coastdownTimeSeconds(
				massKg,
				linearCoefficient,
				quadraticCoefficient,
				startSpeedMetersPerSecond,
				endSpeedMetersPerSecond
		);
		double achievedDistanceMeters = coastdownDistanceMeters(
				massKg,
				linearCoefficient,
				quadraticCoefficient,
				startSpeedMetersPerSecond,
				endSpeedMetersPerSecond
		);
		return new BodyDragFit(
				axis,
				startSpeedMetersPerSecond,
				endSpeedMetersPerSecond,
				targetTimeSeconds,
				linearCoefficient,
				quadraticCoefficient,
				achievedTimeSeconds,
				achievedDistanceMeters,
				targetReachable
		);
	}

	private static void requireValidSpeedRange(double startSpeedMetersPerSecond, double endSpeedMetersPerSecond) {
		if (!Double.isFinite(startSpeedMetersPerSecond)
				|| !Double.isFinite(endSpeedMetersPerSecond)
				|| startSpeedMetersPerSecond <= endSpeedMetersPerSecond
				|| endSpeedMetersPerSecond <= 0.0) {
			throw new IllegalArgumentException("Coastdown speeds must be finite and satisfy start > end > 0.");
		}
	}
}
