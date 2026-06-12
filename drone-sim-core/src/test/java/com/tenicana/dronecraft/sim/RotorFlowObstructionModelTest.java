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
