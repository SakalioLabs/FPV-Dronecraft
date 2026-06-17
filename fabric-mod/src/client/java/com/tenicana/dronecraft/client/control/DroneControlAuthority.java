package com.tenicana.dronecraft.client.control;

final class DroneControlAuthority {
	private DroneControlAuthority() {
	}

	static boolean hasControlAuthority(boolean hasController, boolean virtualControllerEnabled, boolean hasLinkedDrone) {
		return hasController || (virtualControllerEnabled && hasLinkedDrone);
	}

	static boolean shouldUseGamepadInput(boolean gamepadEnabled, boolean hasController, boolean virtualControllerEnabled, boolean hasLinkedDrone) {
		return gamepadEnabled && hasControlAuthority(hasController, virtualControllerEnabled, hasLinkedDrone);
	}
}
