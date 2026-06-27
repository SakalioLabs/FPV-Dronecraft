package com.tenicana.dronecraft.sim;

public final class RotorFlowObstructionModel {
	public static final Result CLEAR = new Result(0.0, Vec3.ZERO, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0);

	private RotorFlowObstructionModel() {
	}

	public static Result fromDirectionalDistances(double[] distancesMeters, Vec3[] bodyDirections, double maxDistanceMeters) {
		return fromDirectionalDistances(distancesMeters, bodyDirections, maxDistanceMeters, maxDistanceMeters * 0.50);
	}

	public static Result fromDirectionalDistances(
			double[] distancesMeters,
			Vec3[] bodyDirections,
			double maxDistanceMeters,
			double rotorRadiusMeters
	) {
		if (distancesMeters == null || bodyDirections == null || distancesMeters.length == 0 || maxDistanceMeters <= 1.0e-6) {
			return CLEAR;
		}

		int count = Math.min(distancesMeters.length, bodyDirections.length);
		double peakProximity = 0.0;
		double closestDistanceMeters = Double.POSITIVE_INFINITY;
		double weightedProximity = 0.0;
		double totalWeight = 0.0;
		Vec3 directionSum = Vec3.ZERO;
		double directionAuthority = 0.0;

		for (int i = 0; i < count; i++) {
			Vec3 bodyDirection = bodyDirections[i];
			if (bodyDirection == null || bodyDirection.lengthSquared() <= 1.0e-9) {
				continue;
			}

			Vec3 normalizedDirection = bodyDirection.normalized();
			double sampleWeight = sampleWeight(normalizedDirection);
			double proximity = proximityFromDistance(distancesMeters[i], maxDistanceMeters);
			double shapedProximity = Math.pow(proximity, 1.12);
			double directionalProximity = Math.pow(proximity, 1.45);
			peakProximity = Math.max(peakProximity, proximity);
			if (Double.isFinite(distancesMeters[i])) {
				closestDistanceMeters = Math.min(closestDistanceMeters, Math.max(0.0, distancesMeters[i]));
			}
			weightedProximity += sampleWeight * shapedProximity;
			totalWeight += sampleWeight;
			directionSum = directionSum.add(normalizedDirection.multiply(sampleWeight * directionalProximity));
			directionAuthority += sampleWeight * directionalProximity;
		}

		if (totalWeight <= 1.0e-9 || peakProximity <= 1.0e-6) {
			return new Result(
					0.0,
					Vec3.ZERO,
					closestDistanceMeters,
					closestDistanceOverRadius(closestDistanceMeters, rotorRadiusMeters),
					0.0,
					0.0,
					normalizedFlatWallDiskCoverage(closestDistanceMeters, rotorRadiusMeters)
			);
		}

		double diskCoverage = weightedProximity / totalWeight;
		double segmentCoverage = normalizedFlatWallDiskCoverage(closestDistanceMeters, rotorRadiusMeters);
		double closestDistanceOverRadius = closestDistanceOverRadius(closestDistanceMeters, rotorRadiusMeters);
		double intensity = MathUtil.clamp(
				0.50 * peakProximity + 0.27 * segmentCoverage + 0.23 * diskCoverage,
				0.0,
				1.0
		);
		Vec3 direction = directionAuthority <= 1.0e-9 || directionSum.lengthSquared() <= 1.0e-9
				? Vec3.ZERO
				: directionSum.normalized();
		return new Result(intensity, direction, closestDistanceMeters, closestDistanceOverRadius, peakProximity, diskCoverage, segmentCoverage);
	}

	public static double proximityFromDistance(double distanceMeters, double maxDistanceMeters) {
		if (!Double.isFinite(distanceMeters)) {
			return 0.0;
		}
		return 1.0 - MathUtil.clamp(distanceMeters / maxDistanceMeters, 0.0, 1.0);
	}

	public static double flatWallDiskBlockedFraction(double clearanceOverRadius) {
		if (!Double.isFinite(clearanceOverRadius)) {
			return 0.0;
		}
		double x = MathUtil.clamp(clearanceOverRadius, -1.0, 1.0);
		double segment = Math.acos(x) - x * Math.sqrt(Math.max(0.0, 1.0 - x * x));
		return MathUtil.clamp(segment / Math.PI, 0.0, 1.0);
	}

	public static double thrustMultiplier(double obstructionIntensity) {
		double obstruction = MathUtil.clamp(obstructionIntensity, 0.0, 1.0);
		return MathUtil.clamp(1.0 - 0.10 * obstruction * obstruction * obstruction, 0.90, 1.0);
	}

	public static double wallForceGeometryFactor(Result result) {
		if (result == null || result.intensity() <= 1.0e-6) {
			return 0.0;
		}
		return wallForceGeometryFactor(result.closestDistanceOverRadius(), result.flatWallDiskCoverage());
	}

	public static double wallForceGeometryFactor(double closestDistanceOverRadius, double flatWallDiskCoverage) {
		if (!Double.isFinite(closestDistanceOverRadius)) {
			return 0.0;
		}
		double clearanceOverRadius = Math.max(0.0, closestDistanceOverRadius);
		double distanceLobe = Math.exp(-0.45 * clearanceOverRadius);
		double diskOverlapLobe = Math.sqrt(MathUtil.clamp(flatWallDiskCoverage, 0.0, 1.0));
		return MathUtil.clamp(Math.max(distanceLobe, diskOverlapLobe), 0.0, 1.0);
	}

	private static double closestDistanceOverRadius(double closestDistanceMeters, double rotorRadiusMeters) {
		if (!Double.isFinite(closestDistanceMeters) || !Double.isFinite(rotorRadiusMeters) || rotorRadiusMeters <= 1.0e-6) {
			return Double.POSITIVE_INFINITY;
		}
		return Math.max(0.0, closestDistanceMeters) / rotorRadiusMeters;
	}

	private static double normalizedFlatWallDiskCoverage(double clearanceMeters, double rotorRadiusMeters) {
		if (!Double.isFinite(clearanceMeters) || !Double.isFinite(rotorRadiusMeters) || rotorRadiusMeters <= 1.0e-6) {
			return 0.0;
		}
		return MathUtil.clamp(2.0 * flatWallDiskBlockedFraction(clearanceMeters / rotorRadiusMeters), 0.0, 1.0);
	}

	private static double sampleWeight(Vec3 normalizedDirection) {
		double horizontal = Math.hypot(normalizedDirection.x(), normalizedDirection.z());
		if (horizontal <= 1.0e-9) {
			return 0.70;
		}

		double majorAxis = Math.max(Math.abs(normalizedDirection.x()), Math.abs(normalizedDirection.z()));
		double minorAxis = Math.min(Math.abs(normalizedDirection.x()), Math.abs(normalizedDirection.z()));
		double diagonalMix = majorAxis <= 1.0e-9 ? 0.0 : minorAxis / majorAxis;
		return 1.0 - 0.30 * MathUtil.clamp(diagonalMix, 0.0, 1.0);
	}

	public record Result(
			double intensity,
			Vec3 directionBody,
			double closestDistanceMeters,
			double closestDistanceOverRadius,
			double peakProximity,
			double diskCoverage,
			double flatWallDiskCoverage
	) {
	}
}
