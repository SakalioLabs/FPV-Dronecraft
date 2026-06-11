package com.tenicana.dronecraft.entity;

import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.Vec3;

final class RotorFlowObstructionModel {
	static final Result CLEAR = new Result(0.0, Vec3.ZERO);

	private RotorFlowObstructionModel() {
	}

	static Result fromDirectionalDistances(double[] distancesMeters, Vec3[] bodyDirections, double maxDistanceMeters) {
		if (distancesMeters == null || bodyDirections == null || distancesMeters.length == 0 || maxDistanceMeters <= 1.0e-6) {
			return CLEAR;
		}

		int count = Math.min(distancesMeters.length, bodyDirections.length);
		double peakProximity = 0.0;
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
			weightedProximity += sampleWeight * shapedProximity;
			totalWeight += sampleWeight;
			directionSum = directionSum.add(normalizedDirection.multiply(sampleWeight * directionalProximity));
			directionAuthority += sampleWeight * directionalProximity;
		}

		if (totalWeight <= 1.0e-9 || peakProximity <= 1.0e-6) {
			return CLEAR;
		}

		double diskCoverage = weightedProximity / totalWeight;
		double intensity = MathUtil.clamp(0.70 * peakProximity + 0.30 * diskCoverage, 0.0, 1.0);
		Vec3 direction = directionAuthority <= 1.0e-9 || directionSum.lengthSquared() <= 1.0e-9
				? Vec3.ZERO
				: directionSum.normalized();
		return new Result(intensity, direction);
	}

	static double proximityFromDistance(double distanceMeters, double maxDistanceMeters) {
		if (!Double.isFinite(distanceMeters)) {
			return 0.0;
		}
		return 1.0 - MathUtil.clamp(distanceMeters / maxDistanceMeters, 0.0, 1.0);
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

	record Result(double intensity, Vec3 directionBody) {
	}
}
