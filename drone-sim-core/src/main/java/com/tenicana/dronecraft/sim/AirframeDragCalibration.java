package com.tenicana.dronecraft.sim;

public final class AirframeDragCalibration {
	private static final double EPSILON = 1.0e-12;
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

	private static void requireValidSpeedRange(double startSpeedMetersPerSecond, double endSpeedMetersPerSecond) {
		if (!Double.isFinite(startSpeedMetersPerSecond)
				|| !Double.isFinite(endSpeedMetersPerSecond)
				|| startSpeedMetersPerSecond <= endSpeedMetersPerSecond
				|| endSpeedMetersPerSecond <= 0.0) {
			throw new IllegalArgumentException("Coastdown speeds must be finite and satisfy start > end > 0.");
		}
	}
}
