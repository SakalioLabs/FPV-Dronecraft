package com.tenicana.dronecraft.integration;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2DroneProbe {
	private static final double MIN_CELL_SIZE_METERS = 0.02;
	private static final double MAX_CELL_SIZE_METERS = 0.08;
	private static final int MIN_HORIZONTAL_CELLS = 16;
	private static final int MIN_VERTICAL_CELLS = 12;
	private static final int MAX_AXIS_CELLS = 96;
	private static final double DEFAULT_AIRFRAME_BODY_LENGTH_SCALE = 1.60;
	private static final double DEFAULT_AIRFRAME_BODY_WIDTH_SCALE = 1.10;
	private static final double DEFAULT_AIRFRAME_BODY_HEIGHT_SCALE = 0.44;
	private static final double DEFAULT_ARM_RADIUS_SCALE = 0.08;
	private static final double DEFAULT_HUB_RADIUS_SCALE = 0.16;
	private static final double DEFAULT_VERTICAL_CLEARANCE_SCALE = 8.0;
	private static final double DEFAULT_HORIZONTAL_CLEARANCE_SCALE = 6.0;
	private static final double DEFAULT_DOWNSTREAM_CLEARANCE_SCALE = 12.0;
	private static final double DEFAULT_UPSTREAM_CLEARANCE_SCALE = 8.0;

	private Aerodynamics4McL2DroneProbe() {
	}

	public static DroneWindTunnelProbe forceMomentProbe(
			DroneConfig config,
			Vec3 inletVelocityBodyMetersPerSecond,
			int steps
	) {
		DroneConfig safeConfig = config == null ? DroneConfig.racingQuad() : config;
		Vec3 inlet = sanitizeInlet(inletVelocityBodyMetersPerSecond);
		double maxRotorRadius = maxRotorRadius(safeConfig);
		double maxHorizontalReach = maxHorizontalReach(safeConfig, maxRotorRadius);
		double cellSize = cellSizeFor(maxRotorRadius, maxHorizontalReach);
		double bodyHalfX = Math.max(0.035, maxRotorRadius * DEFAULT_AIRFRAME_BODY_WIDTH_SCALE);
		double bodyHalfY = Math.max(0.018, maxRotorRadius * DEFAULT_AIRFRAME_BODY_HEIGHT_SCALE);
		double bodyHalfZ = Math.max(0.045, maxRotorRadius * DEFAULT_AIRFRAME_BODY_LENGTH_SCALE);
		double armRadius = Math.max(cellSize * 0.65, maxRotorRadius * DEFAULT_ARM_RADIUS_SCALE);
		double hubRadius = Math.max(cellSize * 0.80, maxRotorRadius * DEFAULT_HUB_RADIUS_SCALE);
		int nx = boundedCellCount(2.0 * maxHorizontalReach + DEFAULT_HORIZONTAL_CLEARANCE_SCALE * cellSize, cellSize, MIN_HORIZONTAL_CELLS);
		int ny = boundedCellCount(2.0 * (bodyHalfY + hubRadius) + DEFAULT_VERTICAL_CLEARANCE_SCALE * cellSize, cellSize, MIN_VERTICAL_CELLS);
		int nz = boundedCellCount(2.0 * maxHorizontalReach
				+ (DEFAULT_UPSTREAM_CLEARANCE_SCALE + DEFAULT_DOWNSTREAM_CLEARANCE_SCALE) * cellSize, cellSize, MIN_HORIZONTAL_CELLS);
		byte[] solidMask = new byte[nx * ny * nz];
		int solidCells = fillSolidMask(solidMask, nx, ny, nz, cellSize, safeConfig, bodyHalfX, bodyHalfY, bodyHalfZ, armRadius, hubRadius);
		Aerodynamics4McL2Bridge.L2RequestSpec requestSpec = Aerodynamics4McL2Bridge.L2RequestSpec.forceMomentProbe(
				nx,
				ny,
				nz,
				cellSize,
				steps,
				inlet.x(),
				inlet.y(),
				inlet.z(),
				solidMask
		);
		return new DroneWindTunnelProbe(
				requestSpec,
				nx,
				ny,
				nz,
				cellSize,
				solidCells,
				maxRotorRadius,
				maxHorizontalReach * 2.0,
				bodyHalfX,
				bodyHalfY,
				bodyHalfZ,
				armRadius,
				hubRadius
		);
	}

	private static Vec3 sanitizeInlet(Vec3 inletVelocityBodyMetersPerSecond) {
		if (inletVelocityBodyMetersPerSecond == null || !inletVelocityBodyMetersPerSecond.isFinite()) {
			return new Vec3(0.0, 0.0, -12.0);
		}
		return inletVelocityBodyMetersPerSecond.clamp(-80.0, 80.0);
	}

	private static double maxRotorRadius(DroneConfig config) {
		double max = 0.05;
		for (RotorSpec rotor : config.rotors()) {
			max = Math.max(max, rotor.radiusMeters());
		}
		return max;
	}

	private static double maxHorizontalReach(DroneConfig config, double maxRotorRadius) {
		double max = maxRotorRadius;
		for (RotorSpec rotor : config.rotors()) {
			Vec3 position = rotor.positionBodyMeters();
			max = Math.max(max, Math.abs(position.x()) + rotor.radiusMeters());
			max = Math.max(max, Math.abs(position.z()) + rotor.radiusMeters());
		}
		return max;
	}

	private static double cellSizeFor(double maxRotorRadius, double maxHorizontalReach) {
		double rotorResolution = maxRotorRadius / 4.0;
		double frameResolution = maxHorizontalReach / 10.0;
		return MathUtil.clamp(Math.max(Math.max(rotorResolution, frameResolution), MIN_CELL_SIZE_METERS),
				MIN_CELL_SIZE_METERS, MAX_CELL_SIZE_METERS);
	}

	private static int boundedCellCount(double meters, double cellSize, int minimum) {
		int count = (int) Math.ceil(meters / Math.max(MIN_CELL_SIZE_METERS, cellSize));
		count = Math.max(minimum, count);
		count = Math.min(MAX_AXIS_CELLS, count);
		return count + (count & 1);
	}

	private static int fillSolidMask(
			byte[] solidMask,
			int nx,
			int ny,
			int nz,
			double cellSize,
			DroneConfig config,
			double bodyHalfX,
			double bodyHalfY,
			double bodyHalfZ,
			double armRadius,
			double hubRadius
	) {
		int solidCells = 0;
		for (int x = 0; x < nx; x++) {
			for (int y = 0; y < ny; y++) {
				for (int z = 0; z < nz; z++) {
					Vec3 body = bodyPositionForCell(nx, ny, nz, cellSize, x, y, z);
					if (isSolidBodyCell(body, config, bodyHalfX, bodyHalfY, bodyHalfZ, armRadius, hubRadius)) {
						solidMask[cellIndex(nx, ny, nz, x, y, z)] = 1;
						solidCells++;
					}
				}
			}
		}
		return solidCells;
	}

	private static boolean isSolidBodyCell(
			Vec3 body,
			DroneConfig config,
			double bodyHalfX,
			double bodyHalfY,
			double bodyHalfZ,
			double armRadius,
			double hubRadius
	) {
		if (ellipsoid(body, bodyHalfX, bodyHalfY, bodyHalfZ) <= 1.0) {
			return true;
		}
		Vec3 origin = Vec3.ZERO;
		for (RotorSpec rotor : config.rotors()) {
			Vec3 rotorPosition = rotor.positionBodyMeters();
			if (distanceToSegment(body, origin, new Vec3(rotorPosition.x(), 0.0, rotorPosition.z())) <= armRadius) {
				return true;
			}
			Vec3 hubCenter = new Vec3(rotorPosition.x(), 0.0, rotorPosition.z());
			if (body.subtract(hubCenter).length() <= hubRadius) {
				return true;
			}
		}
		return false;
	}

	private static double ellipsoid(Vec3 point, double halfX, double halfY, double halfZ) {
		double x = point.x() / Math.max(1.0e-6, halfX);
		double y = point.y() / Math.max(1.0e-6, halfY);
		double z = point.z() / Math.max(1.0e-6, halfZ);
		return x * x + y * y + z * z;
	}

	private static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 segment = end.subtract(start);
		double lengthSquared = segment.lengthSquared();
		if (lengthSquared <= 1.0e-12) {
			return point.subtract(start).length();
		}
		double t = MathUtil.clamp(point.subtract(start).dot(segment) / lengthSquared, 0.0, 1.0);
		Vec3 closest = start.add(segment.multiply(t));
		return point.subtract(closest).length();
	}

	private static Vec3 bodyPositionForCell(int nx, int ny, int nz, double cellSize, int x, int y, int z) {
		return new Vec3(
				(x + 0.5 - 0.5 * nx) * cellSize,
				(y + 0.5 - 0.5 * ny) * cellSize,
				(z + 0.5 - 0.5 * nz) * cellSize
		);
	}

	private static int cellIndex(int nx, int ny, int nz, int x, int y, int z) {
		return (x * ny + y) * nz + z;
	}

	public record DroneWindTunnelProbe(
			Aerodynamics4McL2Bridge.L2RequestSpec requestSpec,
			int nx,
			int ny,
			int nz,
			double cellSizeMeters,
			int solidCellCount,
			double maxRotorRadiusMeters,
			double rotorSpanMeters,
			double bodyHalfX,
			double bodyHalfY,
			double bodyHalfZ,
			double armRadiusMeters,
			double hubRadiusMeters
	) {
		public boolean solidAtBodyPosition(Vec3 bodyPositionMeters) {
			if (bodyPositionMeters == null || !bodyPositionMeters.isFinite()) {
				return false;
			}
			int x = bodyToCell(bodyPositionMeters.x(), nx);
			int y = bodyToCell(bodyPositionMeters.y(), ny);
			int z = bodyToCell(bodyPositionMeters.z(), nz);
			if (x < 0 || y < 0 || z < 0) {
				return false;
			}
			byte[] mask = requestSpec.solidMask();
			return mask != null && mask[cellIndex(nx, ny, nz, x, y, z)] != 0;
		}

		public Vec3 cellCenterBodyMeters(int x, int y, int z) {
			if (x < 0 || x >= nx || y < 0 || y >= ny || z < 0 || z >= nz) {
				return Vec3.ZERO;
			}
			return bodyPositionForCell(nx, ny, nz, cellSizeMeters, x, y, z);
		}

		private int bodyToCell(double coordinateMeters, int cells) {
			double index = coordinateMeters / cellSizeMeters + 0.5 * cells - 0.5;
			int rounded = (int) Math.round(index);
			return rounded < 0 || rounded >= cells ? -1 : rounded;
		}
	}
}
