package com.tenicana.dronecraft.sim.flight;

import com.tenicana.dronecraft.sim.Vec3;

public record ForceTorqueDiagnostics(
		Vec3 forceWorldNewtons,
		Vec3 forceBodyNewtons,
		Vec3 torqueBodyNewtonMeters,
		Vec3 linearAccelerationWorldMetersPerSecondSquared
) {
	public ForceTorqueDiagnostics {
		forceWorldNewtons = forceWorldNewtons == null ? Vec3.ZERO : forceWorldNewtons;
		forceBodyNewtons = forceBodyNewtons == null ? Vec3.ZERO : forceBodyNewtons;
		torqueBodyNewtonMeters = torqueBodyNewtonMeters == null ? Vec3.ZERO : torqueBodyNewtonMeters;
		linearAccelerationWorldMetersPerSecondSquared = linearAccelerationWorldMetersPerSecondSquared == null
				? Vec3.ZERO
				: linearAccelerationWorldMetersPerSecondSquared;
	}

	public static ForceTorqueDiagnostics zero() {
		return new ForceTorqueDiagnostics(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
	}
}
