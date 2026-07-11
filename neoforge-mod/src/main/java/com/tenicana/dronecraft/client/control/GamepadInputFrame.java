package com.tenicana.dronecraft.client.control;

import com.tenicana.dronecraft.client.DroneClientState.InputSource;
import com.tenicana.dronecraft.client.config.DroneClientConfig;
import com.tenicana.dronecraft.sim.ControlStickProfile;

public record GamepadInputFrame(
		GamepadDeviceResolver.Resolution resolution,
		float rawThrottle,
		float rawPitch,
		float rawRoll,
		float rawYaw,
		float throttle,
		float pitch,
		float roll,
		float yaw,
		float throttleCommand,
		float pitchCommand,
		float rollCommand,
		float yawCommand,
		boolean armButtonPressed,
		boolean disarmButtonPressed,
		boolean calibrateButtonPressed
) {
	public static GamepadInputFrame sample(
			DroneClientConfig config,
			JoystickProvider provider,
			float hoverThrottle,
			boolean allowLegacyMigration
	) {
		DroneClientConfig safeConfig = config == null ? DroneClientConfig.defaults() : config;
		GamepadDeviceResolver.Resolution resolution = GamepadDeviceResolver.resolve(safeConfig, provider, allowLegacyMigration);
		if (!resolution.ready(safeConfig)) {
			return unavailable(resolution);
		}

		JoystickSnapshot snapshot = resolution.selected();
		float rawThrottle = rawThrottle(snapshot.axis(safeConfig.throttleAxis()), safeConfig.throttleInverted());
		float throttle = safeConfig.calibrateThrottle(rawThrottle);
		float rawPitch = snapshot.axis(safeConfig.pitchAxis());
		float rawRoll = snapshot.axis(safeConfig.rollAxis());
		float rawYaw = snapshot.axis(safeConfig.yawAxis());
		float pitch = GamepadStickShaper.conditionedAxis(safeConfig.calibratePitchAxis(rawPitch), safeConfig.gamepadDeadband());
		float roll = GamepadStickShaper.conditionedAxis(safeConfig.calibrateRollAxis(rawRoll), safeConfig.gamepadDeadband());
		float yaw = GamepadStickShaper.conditionedAxis(safeConfig.calibrateYawAxis(rawYaw), safeConfig.gamepadDeadband());

		return new GamepadInputFrame(
				resolution,
				rawThrottle,
				rawPitch,
				rawRoll,
				rawYaw,
				throttle,
				pitch,
				roll,
				yaw,
				(float) ControlStickProfile.gamepadThrottle(throttle, hoverThrottle),
				GamepadStickShaper.commandFromConditionedAxis(
						pitch,
						safeConfig.gamepadDeadband(),
						safeConfig.gamepadExpo(),
						safeConfig.gamepadRollPitchRateScale()
				),
				GamepadStickShaper.commandFromConditionedAxis(
						roll,
						safeConfig.gamepadDeadband(),
						safeConfig.gamepadExpo(),
						safeConfig.gamepadRollPitchRateScale()
				),
				GamepadStickShaper.commandFromConditionedAxis(
						yaw,
						safeConfig.gamepadDeadband(),
						safeConfig.gamepadExpo(),
						safeConfig.gamepadYawRateScale()
				),
				snapshot.buttonPressed(safeConfig.armButton()),
				snapshot.buttonPressed(safeConfig.disarmButton()),
				snapshot.buttonPressed(safeConfig.throttleCalibrateButton())
		);
	}

	public static GamepadInputFrame unavailable(GamepadDeviceResolver.Resolution resolution) {
		return new GamepadInputFrame(
				resolution,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				false,
				false,
				false
		);
	}

	public boolean usable() {
		return resolution != null
				&& resolution.selected() != null
				&& (resolution.status() == GamepadDeviceResolver.Status.SELECTED
						|| resolution.status() == GamepadDeviceResolver.Status.MIGRATED_LEGACY_DEVICE);
	}

	ClientControlInput controlInput() {
		return new ClientControlInput(throttleCommand, pitchCommand, rollCommand, yawCommand, InputSource.GAMEPAD);
	}

	private static float rawThrottle(float axisValue, boolean inverted) {
		float value = Float.isFinite(axisValue) ? axisValue : 0.0f;
		if (inverted) {
			value = -value;
		}
		return clamp01((value + 1.0f) * 0.5f);
	}

	private static float clamp01(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}
}
