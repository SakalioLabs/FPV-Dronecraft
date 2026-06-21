package com.tenicana.dronecraft.sim.flight;

import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.Vec3;

public record FlightStateSnapshot(
		Vec3 positionWorldMeters,
		Vec3 velocityWorldMetersPerSecond,
		Quaternion attitude,
		Vec3 angularVelocityBodyRadiansPerSecond,
		FlightMode flightMode,
		boolean armed
) {
	public FlightStateSnapshot {
		positionWorldMeters = positionWorldMeters == null ? Vec3.ZERO : positionWorldMeters;
		velocityWorldMetersPerSecond = velocityWorldMetersPerSecond == null ? Vec3.ZERO : velocityWorldMetersPerSecond;
		attitude = attitude == null ? Quaternion.IDENTITY : attitude.normalized();
		angularVelocityBodyRadiansPerSecond = angularVelocityBodyRadiansPerSecond == null ? Vec3.ZERO : angularVelocityBodyRadiansPerSecond;
		flightMode = flightMode == null ? FlightMode.DEFAULT_FIRST_FLIGHT : flightMode;
	}

	public static FlightStateSnapshot zero() {
		return zero(FlightMode.DEFAULT_FIRST_FLIGHT);
	}

	public static FlightStateSnapshot zero(FlightMode mode) {
		return new FlightStateSnapshot(Vec3.ZERO, Vec3.ZERO, Quaternion.IDENTITY, Vec3.ZERO, mode, false);
	}

	public Vec3 velocityBodyMetersPerSecond() {
		return worldVectorToBody(velocityWorldMetersPerSecond);
	}

	public Vec3 worldVectorToBody(Vec3 worldVector) {
		Vec3 safeVector = worldVector == null ? Vec3.ZERO : worldVector;
		return attitude.conjugate().rotate(safeVector);
	}

	public Vec3 bodyVectorToWorld(Vec3 bodyVector) {
		Vec3 safeVector = bodyVector == null ? Vec3.ZERO : bodyVector;
		return attitude.rotate(safeVector);
	}

	public boolean isFinite() {
		return positionWorldMeters.isFinite()
				&& velocityWorldMetersPerSecond.isFinite()
				&& angularVelocityBodyRadiansPerSecond.isFinite()
				&& Double.isFinite(attitude.w())
				&& Double.isFinite(attitude.x())
				&& Double.isFinite(attitude.y())
				&& Double.isFinite(attitude.z());
	}
}
