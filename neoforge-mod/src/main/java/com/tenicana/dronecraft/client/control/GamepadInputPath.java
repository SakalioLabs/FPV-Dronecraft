package com.tenicana.dronecraft.client.control;

import java.util.List;

import com.tenicana.dronecraft.client.DroneClientState.InputSource;
import com.tenicana.dronecraft.client.config.DroneClientConfig;

public final class GamepadInputPath {
	private GamepadInputPath() {
	}

	public static Decision evaluate(
			DroneClientConfig config,
			JoystickProvider provider,
			boolean hasController,
			boolean virtualControllerEnabled,
			boolean hasLinkedDrone,
			float hoverThrottle,
			boolean allowLegacyMigration
	) {
		DroneClientConfig safeConfig = config == null ? DroneClientConfig.defaults() : config;
		boolean controlAuthorized = DroneControlAuthority.hasControlAuthority(hasController, virtualControllerEnabled, hasLinkedDrone);
		boolean shouldUseGamepadInput = DroneControlAuthority.shouldUseGamepadInput(
				safeConfig.gamepadEnabled(),
				hasController,
				virtualControllerEnabled,
				hasLinkedDrone
		);
		List<JoystickSnapshot> connected = provider == null ? List.of() : List.copyOf(provider.snapshots());
		boolean controllerActivity = connected.stream().anyMatch(snapshot -> snapshot.hasAnyAxisActivity(safeConfig.axisDetectionThreshold()));
		if (!shouldUseGamepadInput) {
			GamepadDeviceResolver.Resolution resolution = GamepadDeviceResolver.resolve(
					safeConfig,
					() -> connected,
					false
			);
			return new Decision(
					controlAuthorized,
					shouldUseGamepadInput,
					controllerActivity && !safeConfig.gamepadEnabled(),
					GamepadInputFrame.unavailable(resolution),
					InputSource.KEYBOARD
			);
		}

		GamepadInputFrame frame = GamepadInputFrame.sample(safeConfig, () -> connected, hoverThrottle, allowLegacyMigration);
		InputSource source = frame.usable() ? InputSource.GAMEPAD : InputSource.KEYBOARD;
		return new Decision(controlAuthorized, shouldUseGamepadInput, false, frame, source);
	}

	public record Decision(
			boolean controlAuthorized,
			boolean shouldUseGamepadInput,
			boolean disabledControllerActivity,
			GamepadInputFrame frame,
			InputSource inputSource
	) {
		public boolean hasUsableGamepadInput() {
			return frame != null && frame.usable() && inputSource == InputSource.GAMEPAD;
		}
	}
}
