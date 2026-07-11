package com.tenicana.dronecraft.client.control;

import com.tenicana.dronecraft.sim.ControlStickProfile;

final class GamepadStickShaper {
	private GamepadStickShaper() {
	}

	static float conditionedAxis(float calibratedAxis, float configuredDeadband) {
		return (float) ControlStickProfile.applyDeadband(calibratedAxis, configuredDeadband);
	}

	static float commandFromConditionedAxis(float conditionedAxis, float configuredDeadband, float configuredExpo, float rateScale) {
		return (float) ControlStickProfile.gamepadCommand(conditionedAxis, configuredDeadband, configuredExpo, rateScale);
	}

	static float commandFromCalibratedAxis(float calibratedAxis, float configuredDeadband, float configuredExpo, float rateScale) {
		return commandFromConditionedAxis(
				conditionedAxis(calibratedAxis, configuredDeadband),
				configuredDeadband,
				configuredExpo,
				rateScale
		);
	}
}
