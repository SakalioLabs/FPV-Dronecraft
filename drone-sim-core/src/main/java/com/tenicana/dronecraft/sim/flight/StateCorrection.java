package com.tenicana.dronecraft.sim.flight;

import com.tenicana.dronecraft.sim.Vec3;

public record StateCorrection(
		StateCorrectionReason reason,
		String detail,
		Vec3 positionDeltaWorldMeters,
		Vec3 velocityDeltaWorldMetersPerSecond,
		Vec3 angularVelocityDeltaBodyRadiansPerSecond
) {
	public StateCorrection {
		reason = reason == null ? StateCorrectionReason.UNKNOWN_LEGACY : reason;
		detail = detail == null ? "" : detail;
		positionDeltaWorldMeters = positionDeltaWorldMeters == null ? Vec3.ZERO : positionDeltaWorldMeters;
		velocityDeltaWorldMetersPerSecond = velocityDeltaWorldMetersPerSecond == null ? Vec3.ZERO : velocityDeltaWorldMetersPerSecond;
		angularVelocityDeltaBodyRadiansPerSecond = angularVelocityDeltaBodyRadiansPerSecond == null ? Vec3.ZERO : angularVelocityDeltaBodyRadiansPerSecond;
	}

	public static StateCorrection none() {
		return new StateCorrection(StateCorrectionReason.NONE, "", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
	}
}
