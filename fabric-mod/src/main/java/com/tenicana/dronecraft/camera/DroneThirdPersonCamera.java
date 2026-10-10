package com.tenicana.dronecraft.camera;

/** A yaw-following camera boom in world space, independent of the drone's pitch and roll. */
public final class DroneThirdPersonCamera {
	public static final double FOLLOW_DISTANCE_METERS = 5.0;
	public static final double FOLLOW_HEIGHT_METERS = 2.0;
	public static final double MAX_BOOM_LENGTH_METERS = Math.hypot(FOLLOW_DISTANCE_METERS, FOLLOW_HEIGHT_METERS);
	public static final float FOV_DEGREES = 70.0f;
	public static final float PITCH_DEGREES = (float) Math.toDegrees(Math.atan2(FOLLOW_HEIGHT_METERS, FOLLOW_DISTANCE_METERS));
	// Native Camera.getMaxZoom traces a 0.1 m cube. Leave room beyond its corner-distance result.
	private static final double COLLISION_CLEARANCE_METERS = 0.20;

	private DroneThirdPersonCamera() {
	}

	/** Produces the default boom or a retracted boom after the native collision probe. */
	public static Pose fromDroneCenter(
			double centerX,
			double centerY,
			double centerZ,
			float yawRadians,
			double allowedBoomLengthMeters
	) {
		double safeYaw = Float.isFinite(yawRadians) ? Math.IEEEremainder(yawRadians, Math.PI * 2.0) : 0.0;
		double fraction = boundedBoomLength(allowedBoomLengthMeters) / MAX_BOOM_LENGTH_METERS;
		double horizontalDistance = FOLLOW_DISTANCE_METERS * fraction;
		return new Pose(
				centerX + Math.sin(safeYaw) * horizontalDistance,
				centerY + FOLLOW_HEIGHT_METERS * fraction,
				centerZ - Math.cos(safeYaw) * horizontalDistance,
				(float) Math.toDegrees(safeYaw),
				PITCH_DEGREES
		);
	}

	/** Preserves the full unobstructed boom and retracts before a collision surface. */
	public static double clearBoomLength(double tracedBoomLengthMeters) {
		double bounded = boundedBoomLength(tracedBoomLengthMeters);
		return bounded >= MAX_BOOM_LENGTH_METERS - 1.0e-6
				? MAX_BOOM_LENGTH_METERS
				: Math.max(0.0, bounded - COLLISION_CLEARANCE_METERS);
	}

	private static double boundedBoomLength(double distanceMeters) {
		return Double.isFinite(distanceMeters)
				? Math.max(0.0, Math.min(MAX_BOOM_LENGTH_METERS, distanceMeters))
				: 0.0;
	}

	public record Pose(double xMeters, double yMeters, double zMeters, float yawDegrees, float pitchDegrees) {
	}
}
