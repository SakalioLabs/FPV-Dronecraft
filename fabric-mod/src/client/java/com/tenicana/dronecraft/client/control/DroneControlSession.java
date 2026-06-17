package com.tenicana.dronecraft.client.control;

import java.util.Objects;
import java.util.UUID;

final class DroneControlSession {
	private UUID controlledDroneId;

	boolean updateControlledDrone(UUID nextControlledDroneId) {
		if (Objects.equals(controlledDroneId, nextControlledDroneId)) {
			return false;
		}
		controlledDroneId = nextControlledDroneId;
		return true;
	}

	void clear() {
		controlledDroneId = null;
	}
}
