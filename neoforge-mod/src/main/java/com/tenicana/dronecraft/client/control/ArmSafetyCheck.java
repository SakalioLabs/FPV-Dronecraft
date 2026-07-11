package com.tenicana.dronecraft.client.control;

import com.tenicana.dronecraft.control.DroneArmSafetyRules;

public final class ArmSafetyCheck {
	private ArmSafetyCheck() {
	}

	public static Result canArmFromMomentaryControl(float throttle, float pitch, float roll, float yaw) {
		if (!Float.isFinite(throttle) || !Float.isFinite(pitch) || !Float.isFinite(roll) || !Float.isFinite(yaw)) {
			return Result.blocked(Reason.NON_FINITE);
		}
		if (throttle > DroneArmSafetyRules.MOMENTARY_ARM_THROTTLE_MAX) {
			return Result.blocked(Reason.THROTTLE_NOT_LOW);
		}
		if (Math.abs(pitch) > DroneArmSafetyRules.MOMENTARY_ARM_AXIS_MAX) {
			return Result.blocked(Reason.PITCH_NOT_CENTERED);
		}
		if (Math.abs(roll) > DroneArmSafetyRules.MOMENTARY_ARM_AXIS_MAX) {
			return Result.blocked(Reason.ROLL_NOT_CENTERED);
		}
		if (Math.abs(yaw) > DroneArmSafetyRules.MOMENTARY_ARM_AXIS_MAX) {
			return Result.blocked(Reason.YAW_NOT_CENTERED);
		}
		return Result.ok();
	}

	public enum Reason {
		OK("message.fpvdrone.arm_ready", "ready"),
		NO_CONTROL_AUTHORITY("message.fpvdrone.arm_blocked_no_authority", "no control authority"),
		GAMEPAD_DISABLED("message.fpvdrone.arm_blocked_gamepad_disabled", "gamepad input disabled"),
		NO_GAMEPAD_DEVICE("message.fpvdrone.arm_blocked_no_gamepad", "no selected controller"),
		ARM_BUTTON_UNCONFIGURED("message.fpvdrone.arm_blocked_arm_button_unset", "arm button not configured"),
		THROTTLE_NOT_LOW("message.fpvdrone.arm_blocked_throttle", "throttle not low"),
		PITCH_NOT_CENTERED("message.fpvdrone.arm_blocked_pitch", "pitch not centered"),
		ROLL_NOT_CENTERED("message.fpvdrone.arm_blocked_roll", "roll not centered"),
		YAW_NOT_CENTERED("message.fpvdrone.arm_blocked_yaw", "yaw not centered"),
		NON_FINITE("message.fpvdrone.arm_blocked_non_finite", "non-finite controller input");

		private final String translationKey;
		private final String diagnosticText;

		Reason(String translationKey, String diagnosticText) {
			this.translationKey = translationKey;
			this.diagnosticText = diagnosticText;
		}

		public String translationKey() {
			return translationKey;
		}

		public String diagnosticText() {
			return diagnosticText;
		}
	}

	public record Result(boolean allowed, Reason reason) {
		public static Result ok() {
			return new Result(true, Reason.OK);
		}

		public static Result blocked(Reason reason) {
			return new Result(false, reason == null ? Reason.NO_CONTROL_AUTHORITY : reason);
		}
	}
}
