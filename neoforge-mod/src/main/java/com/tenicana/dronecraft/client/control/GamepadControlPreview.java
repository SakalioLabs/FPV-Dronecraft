package com.tenicana.dronecraft.client.control;

import com.tenicana.dronecraft.client.config.DroneClientConfig;
import com.tenicana.dronecraft.sim.ControlStickProfile;

public final class GamepadControlPreview {
	private GamepadControlPreview() {
	}

	public static Preview fromAxes(DroneClientConfig config, float[] axes, float hoverThrottle) {
		DroneClientConfig safeConfig = config == null ? DroneClientConfig.defaults() : config;
		float[] safeAxes = axes == null ? new float[0] : axes;
		float rawThrottle = rawThrottle(safeAxes, safeConfig.throttleAxis(), safeConfig.throttleInverted());
		float calibratedThrottle = safeConfig.calibrateThrottle(rawThrottle);
		float throttleCommand = (float) ControlStickProfile.gamepadThrottle(calibratedThrottle, hoverThrottle);
		float pitchStick = conditionedStick(safeConfig.calibratePitchAxis(rawAxis(safeAxes, safeConfig.pitchAxis())), safeConfig);
		float rollStick = conditionedStick(safeConfig.calibrateRollAxis(rawAxis(safeAxes, safeConfig.rollAxis())), safeConfig);
		float yawStick = conditionedStick(safeConfig.calibrateYawAxis(rawAxis(safeAxes, safeConfig.yawAxis())), safeConfig);

		return new Preview(
				axisPresent(safeAxes, safeConfig.throttleAxis()),
				axisPresent(safeAxes, safeConfig.pitchAxis()),
				axisPresent(safeAxes, safeConfig.rollAxis()),
				axisPresent(safeAxes, safeConfig.yawAxis()),
				calibratedThrottle,
				pitchStick,
				rollStick,
				yawStick,
				throttleCommand,
				GamepadStickShaper.commandFromConditionedAxis(
						pitchStick,
						safeConfig.gamepadDeadband(),
						safeConfig.gamepadExpo(),
						safeConfig.gamepadRollPitchRateScale()
				),
				GamepadStickShaper.commandFromConditionedAxis(
						rollStick,
						safeConfig.gamepadDeadband(),
						safeConfig.gamepadExpo(),
						safeConfig.gamepadRollPitchRateScale()
				),
				GamepadStickShaper.commandFromConditionedAxis(
						yawStick,
						safeConfig.gamepadDeadband(),
						safeConfig.gamepadExpo(),
						safeConfig.gamepadYawRateScale()
				)
		);
	}

	private static float conditionedStick(float calibratedAxis, DroneClientConfig config) {
		return GamepadStickShaper.conditionedAxis(calibratedAxis, config.gamepadDeadband());
	}

	private static float rawThrottle(float[] axes, int axis, boolean inverted) {
		float value = rawAxis(axes, axis);
		if (inverted) {
			value = -value;
		}
		return clamp01((value + 1.0f) * 0.5f);
	}

	private static float rawAxis(float[] axes, int axis) {
		return axisPresent(axes, axis) ? finiteOrZero(axes[axis]) : 0.0f;
	}

	private static boolean axisPresent(float[] axes, int axis) {
		return axes != null && axis >= 0 && axis < axes.length;
	}

	private static float finiteOrZero(float value) {
		return Float.isFinite(value) ? value : 0.0f;
	}

	private static float clamp01(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	public record Preview(
			boolean throttleAxisPresent,
			boolean pitchAxisPresent,
			boolean rollAxisPresent,
			boolean yawAxisPresent,
			float calibratedThrottle,
			float pitchStick,
			float rollStick,
			float yawStick,
			float throttleCommand,
			float pitchCommand,
			float rollCommand,
			float yawCommand
	) {
		public boolean allAxesPresent() {
			return throttleAxisPresent && pitchAxisPresent && rollAxisPresent && yawAxisPresent;
		}
	}
}
