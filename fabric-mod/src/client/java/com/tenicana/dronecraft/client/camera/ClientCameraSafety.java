package com.tenicana.dronecraft.client.camera;

public final class ClientCameraSafety {
	private ClientCameraSafety() {
	}

	public static boolean isInvalidEntityTarget(
			boolean targetPresent,
			boolean clientLevelPresent,
			boolean targetInClientLevel,
			boolean targetRemoved,
			boolean targetAlive
	) {
		return targetPresent && (!clientLevelPresent || !targetInClientLevel || targetRemoved || !targetAlive);
	}

	public static boolean isUsableFpvDroneReference(
			boolean fpvActive,
			boolean dronePresent,
			boolean droneInClientLevel,
			boolean droneRemoved,
			boolean droneAlive
	) {
		return fpvActive && dronePresent && droneInClientLevel && !droneRemoved && droneAlive;
	}

	public static boolean shouldResetFpvPoseDelay(int droneId, int delayedDroneId, Object level, Object delayedLevel) {
		return droneId != delayedDroneId || level != delayedLevel;
	}
}
