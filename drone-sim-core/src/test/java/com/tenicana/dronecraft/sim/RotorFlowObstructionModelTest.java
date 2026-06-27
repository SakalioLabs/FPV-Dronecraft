package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RotorFlowObstructionModelTest {
	private static final double MAX_DISTANCE = 0.40;
	private static final double ROTOR_RADIUS = 0.20;
	private static final Vec3[] ROTOR_PLANE_DIRECTIONS = {
			new Vec3(1.0, 0.0, 0.0),
			new Vec3(-1.0, 0.0, 0.0),
			new Vec3(0.0, 0.0, 1.0),
			new Vec3(0.0, 0.0, -1.0),
			new Vec3(1.0, 0.0, 1.0).normalized(),
			new Vec3(1.0, 0.0, -1.0).normalized(),
			new Vec3(-1.0, 0.0, 1.0).normalized(),
			new Vec3(-1.0, 0.0, -1.0).normalized()
	};

	@Test
	void flatWallDiskBlockedFractionMatchesCircleSegmentGeometry() {
		assertEquals(0.5, RotorFlowObstructionModel.flatWallDiskBlockedFraction(0.0), 1.0e-12);
		assertEquals(0.4364442857847691, RotorFlowObstructionModel.flatWallDiskBlockedFraction(0.1), 1.0e-12);
		assertEquals(0.3735300390523310, RotorFlowObstructionModel.flatWallDiskBlockedFraction(0.2), 1.0e-12);
		assertEquals(0.3425188212371463, RotorFlowObstructionModel.flatWallDiskBlockedFraction(0.25), 1.0e-12);
		assertEquals(0.3119188323905365, RotorFlowObstructionModel.flatWallDiskBlockedFraction(0.3), 1.0e-12);
		assertEquals(0.1955011094778854, RotorFlowObstructionModel.flatWallDiskBlockedFraction(0.5), 1.0e-12);
		assertEquals(0.0721468064071937, RotorFlowObstructionModel.flatWallDiskBlockedFraction(0.75), 1.0e-12);
		assertEquals(0.0, RotorFlowObstructionModel.flatWallDiskBlockedFraction(1.0), 1.0e-12);
		assertEquals(0.0, RotorFlowObstructionModel.flatWallDiskBlockedFraction(1.5), 1.0e-12);
	}

	@Test
	void thrustMultiplierKeepsSidewallLiftLossSmall() {
		assertEquals(1.0, RotorFlowObstructionModel.thrustMultiplier(0.0), 1.0e-12);
		assertEquals(0.90, RotorFlowObstructionModel.thrustMultiplier(1.0), 1.0e-12);

		double tangentWall = RotorFlowObstructionModel.thrustMultiplier(0.66);
		double closeWall = RotorFlowObstructionModel.thrustMultiplier(0.80);

		assertTrue(tangentWall > 0.970 && tangentWall < 0.980, () -> "tangentWall=" + tangentWall);
		assertTrue(closeWall < tangentWall, () -> "closeWall=" + closeWall + " tangentWall=" + tangentWall);
		assertTrue(closeWall > 0.945, () -> "closeWall=" + closeWall);
	}

	@Test
	void tangentSidewallSeparatesDirtyAirLossFromAttractionForce() {
		DroneConfig config = DroneConfig.racingQuad();
		RotorSpec rotor = config.rotors().get(0);
		double maxDistance = Math.max(0.32, Math.min(0.70, rotor.radiusMeters() * 6.5));
		RotorFlowObstructionModel.Result result = RotorFlowObstructionModel.fromDirectionalDistances(
				distancesToWalls(rotor.radiusMeters(), new Vec3(1.0, 0.0, 0.0)),
				ROTOR_PLANE_DIRECTIONS,
				maxDistance,
				rotor.radiusMeters()
		);

		assertEquals(0.0, RotorFlowObstructionModel.flatWallDiskBlockedFraction(1.0), 1.0e-12);
		assertTrue(result.intensity() > 0.45 && result.intensity() < 0.52,
				() -> "intensity=" + result.intensity());
		double thrustMultiplier = RotorFlowObstructionModel.thrustMultiplier(result.intensity());
		assertTrue(thrustMultiplier > 0.985, () -> "thrustMultiplier=" + thrustMultiplier);

		double hoverThrust = config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size();
		double hoverSpinRatio = Math.sqrt(hoverThrust / rotor.maxThrustNewtons());
		double hoverOmega = rotor.maxOmegaRadiansPerSecond() * hoverSpinRatio;
		Vec3 hoverWallForce = DronePhysics.calculateSteadyRotorWallEffectForce(
				rotor,
				Vec3.ZERO,
				hoverOmega,
				hoverThrust,
				result.intensity(),
				result.directionBody()
		);
		Vec3 fastWallForce = DronePhysics.calculateSteadyRotorWallEffectForce(
				rotor,
				new Vec3(12.0, 0.0, 0.0),
				hoverOmega,
				hoverThrust,
				result.intensity(),
				result.directionBody()
		);

		double dirtyAirLossNewtons = hoverThrust * (1.0 - thrustMultiplier);
		assertTrue(hoverWallForce.length() > dirtyAirLossNewtons * 3.0,
				() -> "wallForce=" + hoverWallForce.length() + " dirtyAirLoss=" + dirtyAirLossNewtons);
		assertTrue(fastWallForce.length() < hoverWallForce.length() * 0.25,
				() -> "hoverWallForce=" + hoverWallForce + " fastWallForce=" + fastWallForce);
	}

	@Test
	void clearDistancesReturnNoRotorFlowObstruction() {
		double[] distances = new double[ROTOR_PLANE_DIRECTIONS.length];
		for (int i = 0; i < distances.length; i++) {
			distances[i] = Double.POSITIVE_INFINITY;
		}

		RotorFlowObstructionModel.Result result = RotorFlowObstructionModel.fromDirectionalDistances(
				distances,
				ROTOR_PLANE_DIRECTIONS,
				MAX_DISTANCE
		);

		assertEquals(0.0, result.intensity(), 1.0e-9);
		assertEquals(0.0, result.directionBody().length(), 1.0e-9);
		assertTrue(Double.isInfinite(result.closestDistanceMeters()));
		assertTrue(Double.isInfinite(result.closestDistanceOverRadius()));
		assertEquals(0.0, result.peakProximity(), 1.0e-9);
		assertEquals(0.0, result.diskCoverage(), 1.0e-9);
		assertEquals(0.0, result.flatWallDiskCoverage(), 1.0e-9);
		assertEquals(0.0, RotorFlowObstructionModel.wallForceGeometryFactor(result), 1.0e-9);
	}

	@Test
	void singleWallUsesDiskCoverageInsteadOfNearestRayOnly() {
		double clearance = 0.04;
		RotorFlowObstructionModel.Result result = RotorFlowObstructionModel.fromDirectionalDistances(
				distancesToWalls(clearance, new Vec3(1.0, 0.0, 0.0)),
				ROTOR_PLANE_DIRECTIONS,
				MAX_DISTANCE,
				ROTOR_RADIUS
		);

		double nearestRayOnly = RotorFlowObstructionModel.proximityFromDistance(clearance, MAX_DISTANCE);
		assertEquals(clearance, result.closestDistanceMeters(), 1.0e-12);
		assertEquals(clearance / ROTOR_RADIUS, result.closestDistanceOverRadius(), 1.0e-12);
		assertEquals(nearestRayOnly, result.peakProximity(), 1.0e-12);
		assertEquals(0.747060078104662, result.flatWallDiskCoverage(), 1.0e-12);
		assertTrue(result.diskCoverage() > 0.30 && result.diskCoverage() < 0.31,
				() -> "diskCoverage=" + result.diskCoverage());
		assertTrue(RotorFlowObstructionModel.wallForceGeometryFactor(result) > 0.91
						&& RotorFlowObstructionModel.wallForceGeometryFactor(result) < 0.92,
				() -> "wallForceGeometryFactor=" + RotorFlowObstructionModel.wallForceGeometryFactor(result));
		assertTrue(result.intensity() > 0.65, () -> "intensity=" + result.intensity());
		assertTrue(result.intensity() < nearestRayOnly * 0.86,
				() -> "intensity=" + result.intensity() + " nearestRayOnly=" + nearestRayOnly);
		assertTrue(result.directionBody().x() > 0.99, () -> "direction=" + result.directionBody());
		assertEquals(0.0, result.directionBody().z(), 1.0e-9);
	}

	@Test
	void cornerObstructionIsStrongerAndPointsBetweenWalls() {
		RotorFlowObstructionModel.Result singleWall = RotorFlowObstructionModel.fromDirectionalDistances(
				distancesToWalls(0.04, new Vec3(1.0, 0.0, 0.0)),
				ROTOR_PLANE_DIRECTIONS,
				MAX_DISTANCE,
				ROTOR_RADIUS
		);
		RotorFlowObstructionModel.Result corner = RotorFlowObstructionModel.fromDirectionalDistances(
				distancesToWalls(0.04, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0)),
				ROTOR_PLANE_DIRECTIONS,
				MAX_DISTANCE,
				ROTOR_RADIUS
		);

		assertTrue(corner.intensity() > singleWall.intensity() + 0.04,
				() -> "singleWall=" + singleWall.intensity() + " corner=" + corner.intensity());
		assertTrue(corner.directionBody().x() > 0.68, () -> "direction=" + corner.directionBody());
		assertTrue(corner.directionBody().z() > 0.68, () -> "direction=" + corner.directionBody());
		assertEquals(corner.directionBody().x(), corner.directionBody().z(), 0.02);
	}

	@Test
	void fartherWallFadesContinuously() {
		RotorFlowObstructionModel.Result close = RotorFlowObstructionModel.fromDirectionalDistances(
				distancesToWalls(0.04, new Vec3(1.0, 0.0, 0.0)),
				ROTOR_PLANE_DIRECTIONS,
				MAX_DISTANCE,
				ROTOR_RADIUS
		);
		RotorFlowObstructionModel.Result far = RotorFlowObstructionModel.fromDirectionalDistances(
				distancesToWalls(0.24, new Vec3(1.0, 0.0, 0.0)),
				ROTOR_PLANE_DIRECTIONS,
				MAX_DISTANCE,
				ROTOR_RADIUS
		);

		assertTrue(far.intensity() < close.intensity() * 0.55,
				() -> "close=" + close.intensity() + " far=" + far.intensity());
		assertTrue(far.intensity() > 0.20, () -> "far=" + far.intensity());
		assertEquals(0.24, far.closestDistanceMeters(), 1.0e-12);
		assertEquals(1.20, far.closestDistanceOverRadius(), 1.0e-12);
		assertEquals(0.0, far.flatWallDiskCoverage(), 1.0e-12);
		assertTrue(RotorFlowObstructionModel.wallForceGeometryFactor(far) < 0.59,
				() -> "wallForceGeometryFactor=" + RotorFlowObstructionModel.wallForceGeometryFactor(far));
	}

	@Test
	void beyondModeledRangeStillReportsClosestGeometry() {
		double clearance = MAX_DISTANCE * 1.5;
		RotorFlowObstructionModel.Result result = RotorFlowObstructionModel.fromDirectionalDistances(
				distancesToWalls(clearance, new Vec3(1.0, 0.0, 0.0)),
				ROTOR_PLANE_DIRECTIONS,
				MAX_DISTANCE,
				ROTOR_RADIUS
		);

		assertEquals(0.0, result.intensity(), 1.0e-9);
		assertEquals(0.0, result.directionBody().length(), 1.0e-9);
		assertEquals(clearance, result.closestDistanceMeters(), 1.0e-12);
		assertEquals(clearance / ROTOR_RADIUS, result.closestDistanceOverRadius(), 1.0e-12);
		assertEquals(0.0, result.peakProximity(), 1.0e-9);
		assertEquals(0.0, result.diskCoverage(), 1.0e-9);
		assertEquals(0.0, result.flatWallDiskCoverage(), 1.0e-9);
		assertEquals(0.0, RotorFlowObstructionModel.wallForceGeometryFactor(result), 1.0e-9);
	}

	@Test
	void allSidesBlockedApproachesFullObstructionWithoutArtificialDirection() {
		RotorFlowObstructionModel.Result result = RotorFlowObstructionModel.fromDirectionalDistances(
				distancesToWalls(
						0.0,
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(-1.0, 0.0, 0.0),
						new Vec3(0.0, 0.0, 1.0),
						new Vec3(0.0, 0.0, -1.0)
				),
				ROTOR_PLANE_DIRECTIONS,
				MAX_DISTANCE,
				ROTOR_RADIUS
		);

		assertTrue(result.intensity() > 0.99, () -> "intensity=" + result.intensity());
		assertEquals(0.0, result.directionBody().length(), 1.0e-9);
	}

	private static double[] distancesToWalls(double clearanceMeters, Vec3... wallDirections) {
		double[] distances = new double[ROTOR_PLANE_DIRECTIONS.length];
		for (int i = 0; i < ROTOR_PLANE_DIRECTIONS.length; i++) {
			Vec3 sampleDirection = ROTOR_PLANE_DIRECTIONS[i].normalized();
			double distance = Double.POSITIVE_INFINITY;
			for (Vec3 wallDirection : wallDirections) {
				double projection = sampleDirection.dot(wallDirection.normalized());
				if (projection > 1.0e-9) {
					distance = Math.min(distance, clearanceMeters / projection);
				}
			}
			distances[i] = distance;
		}
		return distances;
	}
}
