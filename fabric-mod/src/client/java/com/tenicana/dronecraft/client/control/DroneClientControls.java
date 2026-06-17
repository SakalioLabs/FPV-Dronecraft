package com.tenicana.dronecraft.client.control;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.client.DroneClientState.InputSource;
import com.tenicana.dronecraft.client.config.DroneClientConfig;
import com.tenicana.dronecraft.client.config.DroneControllerSettingsScreen;
import com.tenicana.dronecraft.network.DroneControlPayload;
import com.tenicana.dronecraft.registry.DroneItems;
import com.tenicana.dronecraft.sim.ControlStickProfile;
import com.tenicana.dronecraft.sim.FlightMode;

public final class DroneClientControls {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "controls")
	);
	private static final KeyMapping ARM = register("key.fpvdrone.arm", GLFW.GLFW_KEY_R);
	private static final KeyMapping FLIGHT_MODE = register("key.fpvdrone.flight_mode", GLFW.GLFW_KEY_M);
	private static final KeyMapping VIRTUAL_CONTROLLER = register("key.fpvdrone.virtual_controller", GLFW.GLFW_KEY_V);
	private static final KeyMapping FPV_VIEW = register("key.fpvdrone.fpv_view", GLFW.GLFW_KEY_B);
	private static final KeyMapping HUD_TOGGLE = register("key.fpvdrone.hud_toggle", GLFW.GLFW_KEY_N);
	private static final KeyMapping GAMEPAD_TOGGLE = register("key.fpvdrone.gamepad_toggle", GLFW.GLFW_KEY_G);
	private static final KeyMapping CONFIG_RELOAD = register("key.fpvdrone.reload_config", GLFW.GLFW_KEY_H);
	private static final KeyMapping CONTROLLER_SETTINGS = register("key.fpvdrone.open_controller_settings", GLFW.GLFW_KEY_I);
	private static final KeyMapping PITCH_FORWARD = register("key.fpvdrone.pitch_forward", GLFW.GLFW_KEY_UP);
	private static final KeyMapping PITCH_BACK = register("key.fpvdrone.pitch_back", GLFW.GLFW_KEY_DOWN);
	private static final KeyMapping ROLL_LEFT = register("key.fpvdrone.roll_left", GLFW.GLFW_KEY_LEFT);
	private static final KeyMapping ROLL_RIGHT = register("key.fpvdrone.roll_right", GLFW.GLFW_KEY_RIGHT);
	private static final KeyMapping THROTTLE_UP = register("key.fpvdrone.throttle_up", GLFW.GLFW_KEY_PAGE_UP);
	private static final KeyMapping THROTTLE_DOWN = register("key.fpvdrone.throttle_down", GLFW.GLFW_KEY_PAGE_DOWN);
	private static final KeyMapping YAW_LEFT = register("key.fpvdrone.yaw_left", GLFW.GLFW_KEY_Z);
	private static final KeyMapping YAW_RIGHT = register("key.fpvdrone.yaw_right", GLFW.GLFW_KEY_X);
	private static final KeyMapping THROTTLE_CALIBRATE = register("key.fpvdrone.calibrate_throttle", GLFW.GLFW_KEY_C);
	private static final float THROTTLE_CALIBRATION_MIN_SPAN = 0.05f;
	private static final float KEYBOARD_AXIS_RISE_PER_TICK = 0.075f;
	private static final float KEYBOARD_AXIS_FALL_PER_TICK = 0.18f;

	private static float throttle;
	private static float keyboardPitchAxis;
	private static float keyboardRollAxis;
	private static float keyboardYawAxis;
	private static boolean armed;
	private static FlightMode flightMode = DroneClientState.DEFAULT_FLIGHT_MODE;
	private static boolean virtualControllerEnabled;
	private static boolean gamepadEnabled = true;
	private static boolean throttleCalibrationActive;
	private static float sampledThrottleMin = 0.0f;
	private static float sampledThrottleMax = 1.0f;
	private static boolean gamepadArmButtonDown;
	private static boolean gamepadDisarmButtonDown;
	private static boolean gamepadCalibrateButtonDown;
	private static final int ARM_GESTURE_HOLD_TICKS = 7;
	private static final int MODE_SWITCH_RAMP_TICKS = 8;
	private static final StickArmGestureLatch STICK_ARM_GESTURE = new StickArmGestureLatch(ARM_GESTURE_HOLD_TICKS);
	private static final FlightModeInputRamp MODE_SWITCH_RAMP = new FlightModeInputRamp(MODE_SWITCH_RAMP_TICKS);
	private static final ControlInputSmoother INPUT_SMOOTHER = new ControlInputSmoother();
	private static DroneClientConfig config = DroneClientConfig.defaults();

	private DroneClientControls() {
	}

	public static void initialize() {
		config = DroneClientConfig.load();
		gamepadEnabled = config.gamepadEnabled();
		DroneClientState.setHudMode(config.hudMode());
		throttleCalibrationActive = false;

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				gamepadArmButtonDown = false;
				gamepadDisarmButtonDown = false;
				gamepadCalibrateButtonDown = false;
				STICK_ARM_GESTURE.reset();
				MODE_SWITCH_RAMP.reset();
				INPUT_SMOOTHER.reset();
				throttleCalibrationActive = false;
				return;
			}

			boolean hasController = client.player.getMainHandItem().is(DroneItems.DRONE_CONTROLLER)
					|| client.player.getOffhandItem().is(DroneItems.DRONE_CONTROLLER);
			DroneClientState.refreshControlledDrone(client);
			boolean hasLinkedDrone = DroneClientState.controlledDrone() != null && DroneClientState.controlledDrone().isAlive();

			while (VIRTUAL_CONTROLLER.consumeClick()) {
				virtualControllerEnabled = !virtualControllerEnabled;
				client.player.displayClientMessage(
						Component.translatable(virtualControllerEnabled ? "message.fpvdrone.virtual_controller_enabled" : "message.fpvdrone.virtual_controller_disabled"),
						true
				);
			}

			while (FPV_VIEW.consumeClick()) {
				boolean fpvEnabled = !DroneClientState.isFpvViewEnabled();
				DroneClientState.setFpvViewEnabled(fpvEnabled);
				client.player.displayClientMessage(
						Component.translatable(fpvEnabled ? "message.fpvdrone.fpv_view_enabled" : "message.fpvdrone.fpv_view_disabled"),
						true
				);
			}

			while (HUD_TOGGLE.consumeClick()) {
				DroneClientState.HudMode hudMode = DroneClientState.cycleHudMode();
				config.setHudMode(hudMode);
				config.save();
				client.player.displayClientMessage(
						Component.translatable(hudMode.translationKey()),
						true
				);
			}

			boolean controlAuthorized = DroneControlAuthority.hasControlAuthority(hasController, virtualControllerEnabled, hasLinkedDrone);
			GamepadInput gamepadInput = DroneControlAuthority.shouldUseGamepadInput(gamepadEnabled, hasController, virtualControllerEnabled, hasLinkedDrone)
					? gamepadInput()
					: null;
			if (!controlAuthorized) {
				STICK_ARM_GESTURE.reset();
				MODE_SWITCH_RAMP.reset();
				INPUT_SMOOTHER.reset();
				resetKeyboardAxes();
				DroneClientState.updateControls(
						throttle,
						0.0f,
						0.0f,
						0.0f,
						armed,
						false,
						hasController,
						virtualControllerEnabled,
						flightMode,
						InputSource.KEYBOARD,
						config.throttleCalibrated(),
						throttleCalibrationActive
				);
				return;
			}

			while (ARM.consumeClick()) {
				requestArmed(client, !armed, canArmWithKeyboard(client));
			}

			while (FLIGHT_MODE.consumeClick()) {
				flightMode = flightMode.next();
				MODE_SWITCH_RAMP.trigger();
				client.player.displayClientMessage(Component.translatable("message.fpvdrone.flight_mode", flightMode.name()), true);
			}

			while (GAMEPAD_TOGGLE.consumeClick()) {
				gamepadEnabled = !gamepadEnabled;
				config.setGamepadEnabled(gamepadEnabled);
				config.save();
				client.player.displayClientMessage(Component.translatable(gamepadEnabled ? "message.fpvdrone.gamepad_enabled" : "message.fpvdrone.gamepad_disabled"), true);
			}

			while (CONFIG_RELOAD.consumeClick()) {
				config = DroneClientConfig.load();
				gamepadEnabled = config.gamepadEnabled();
				DroneClientState.setHudMode(config.hudMode());
				gamepadArmButtonDown = false;
				gamepadDisarmButtonDown = false;
				gamepadCalibrateButtonDown = false;
				client.player.displayClientMessage(Component.translatable("message.fpvdrone.config_reloaded", DroneClientConfig.path().toAbsolutePath().toString()), true);
			}

			while (CONTROLLER_SETTINGS.consumeClick()) {
				client.setScreen(DroneControllerSettingsScreen.open(config));
			}

			while (THROTTLE_CALIBRATE.consumeClick()) {
				if (gamepadInput == null) {
					client.player.displayClientMessage(Component.translatable("message.fpvdrone.throttle_calibration_unavailable"), true);
					continue;
				}
				toggleThrottleCalibration(client, gamepadInput.rawThrottle());
			}

			if (gamepadInput != null) {
				handleGamepadButtons(client, gamepadInput);
				handleStickArmGesture(client, gamepadInput);
			}

			ControlInput input = gamepadInput != null ? gamepadInputAsControl(gamepadInput) : keyboardInput(client);
			if (gamepadInput != null && isStickArmGesture(gamepadInput)) {
				INPUT_SMOOTHER.reset();
				input = new ControlInput(0.0f, 0.0f, 0.0f, 0.0f, InputSource.GAMEPAD);
			} else if (input.source() == InputSource.GAMEPAD) {
				input = smoothGamepadInput(input);
			}
			input = applyModeSwitchRamp(input);
			throttle = input.throttle();
			DroneClientState.updateControls(
					input.throttle(),
					input.pitch(),
					input.roll(),
					input.yaw(),
					armed,
					true,
					hasController,
					virtualControllerEnabled,
					flightMode,
					input.source(),
					config.throttleCalibrated(),
					throttleCalibrationActive
			);
			ClientPlayNetworking.send(new DroneControlPayload(input.throttle(), input.pitch(), input.roll(), input.yaw(), armed, flightMode.id()));
		});
	}

	public static DroneClientConfig config() {
		return config;
	}

	private static void handleGamepadButtons(Minecraft client, GamepadInput input) {
		boolean armPressedEdge = input.armButtonPressed() && !gamepadArmButtonDown;
		boolean disarmPressedEdge = input.disarmButtonPressed() && !gamepadDisarmButtonDown;
		boolean calibratePressedEdge = input.calibrateButtonPressed() && !gamepadCalibrateButtonDown;

		if (armPressedEdge && !input.disarmButtonPressed()) {
			requestArmed(client, true, canArmWithGamepadButton(input));
		}
		if (disarmPressedEdge && !input.armButtonPressed()) {
			requestArmed(client, false, true);
		}
		if (calibratePressedEdge) {
			toggleThrottleCalibration(client, input.rawThrottle());
		}

		gamepadArmButtonDown = input.armButtonPressed();
		gamepadDisarmButtonDown = input.disarmButtonPressed();
		gamepadCalibrateButtonDown = input.calibrateButtonPressed();

		if (throttleCalibrationActive && Float.isFinite(input.rawThrottle())) {
			sampledThrottleMin = Math.min(sampledThrottleMin, input.rawThrottle());
			sampledThrottleMax = Math.max(sampledThrottleMax, input.rawThrottle());
		}
	}

	private static void handleStickArmGesture(Minecraft client, GamepadInput input) {
		if (!STICK_ARM_GESTURE.update(isStickArmGesture(input))) {
			return;
		}
		armed = !armed;
		client.player.displayClientMessage(Component.translatable(armed ? "message.fpvdrone.armed" : "message.fpvdrone.disarmed"), true);
	}

	private static boolean isStickArmGesture(GamepadInput input) {
		return input != null
				&& DroneArmSafety.isStickArmGesture(input.throttle(), input.pitch(), input.roll(), input.yaw());
	}

	private static boolean canArmWithGamepadButton(GamepadInput input) {
		return input != null
				&& DroneArmSafety.canArmFromMomentaryControl(input.throttle(), input.pitch(), input.roll(), input.yaw());
	}

	private static boolean canArmWithKeyboard(Minecraft client) {
		float pitch = largerMagnitude(keyboardPitchAxis, axis(PITCH_BACK.isDown(), PITCH_FORWARD.isDown()));
		float roll = largerMagnitude(keyboardRollAxis, axis(ROLL_LEFT.isDown(), ROLL_RIGHT.isDown()));
		float yaw = largerMagnitude(keyboardYawAxis, axis(YAW_LEFT.isDown(), YAW_RIGHT.isDown()));
		return DroneArmSafety.canArmFromMomentaryControl(throttle, pitch, roll, yaw);
	}

	private static float largerMagnitude(float current, float target) {
		return Math.abs(current) >= Math.abs(target) ? current : target;
	}

	private static void requestArmed(Minecraft client, boolean targetArmed, boolean canArm) {
		if (targetArmed) {
			if (armed) {
				return;
			}
			if (!canArm) {
				client.player.displayClientMessage(Component.translatable("message.fpvdrone.arm_blocked"), true);
				return;
			}
			armed = true;
			client.player.displayClientMessage(Component.translatable("message.fpvdrone.armed"), true);
			return;
		}

		if (!armed) {
			return;
		}
		armed = false;
		client.player.displayClientMessage(Component.translatable("message.fpvdrone.disarmed"), true);
	}

	private static ControlInput gamepadInputAsControl(GamepadInput input) {
		return new ControlInput(
				(float) ControlStickProfile.gamepadThrottle(input.throttle()),
				commandAxis(input.pitch(), config.gamepadRollPitchRateScale()),
				commandAxis(input.roll(), config.gamepadRollPitchRateScale()),
				commandAxis(input.yaw(), config.gamepadYawRateScale()),
				InputSource.GAMEPAD
		);
	}

	private static ControlInput smoothGamepadInput(ControlInput input) {
		ControlInputSmoother.Axes axes = INPUT_SMOOTHER.sample(
				input.pitch(),
				input.roll(),
				input.yaw(),
				config.gamepadAxisRisePerTick(),
				config.gamepadAxisFallPerTick()
		);
		return new ControlInput(input.throttle(), axes.pitch(), axes.roll(), axes.yaw(), input.source());
	}

	private static ControlInput applyModeSwitchRamp(ControlInput input) {
		float scale = MODE_SWITCH_RAMP.sampleAndAdvance();
		if (scale >= 0.999f) {
			return input;
		}
		return new ControlInput(
				input.throttle(),
				input.pitch() * scale,
				input.roll() * scale,
				input.yaw() * scale,
				input.source()
		);
	}

	private static void toggleThrottleCalibration(Minecraft client, float currentRawThrottle) {
		if (!throttleCalibrationActive) {
			throttleCalibrationActive = true;
			sampledThrottleMin = currentRawThrottle;
			sampledThrottleMax = currentRawThrottle;
			client.player.displayClientMessage(Component.translatable("message.fpvdrone.throttle_calibration_started"), true);
		} else {
			finishThrottleCalibration(client);
		}
	}

	private static void finishThrottleCalibration(Minecraft client) {
		throttleCalibrationActive = false;
		if (sampledThrottleMax - sampledThrottleMin < THROTTLE_CALIBRATION_MIN_SPAN) {
			client.player.displayClientMessage(Component.translatable("message.fpvdrone.throttle_calibration_invalid"), true);
			return;
		}
		config.setThrottleCalibration(sampledThrottleMin, sampledThrottleMax);
		config.save();
		client.player.displayClientMessage(Component.translatable("message.fpvdrone.throttle_calibration_saved"), true);
	}

	private static KeyMapping register(String translationKey, int key) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping(translationKey, InputConstants.Type.KEYSYM, key, CATEGORY));
	}

	private static float axis(boolean negative, boolean positive) {
		if (negative == positive) {
			return 0.0f;
		}
		return positive ? 1.0f : -1.0f;
	}

	private static ControlInput keyboardInput(Minecraft client) {
		double throttleDelta = 0.0;
		if (THROTTLE_UP.isDown()) {
			throttleDelta += 0.014;
		}
		if (THROTTLE_DOWN.isDown()) {
			throttleDelta -= 0.014;
		}
		throttle = (float) Mth.clamp(throttle + throttleDelta, 0.0, 1.0);

		keyboardPitchAxis = approachKeyboardAxis(keyboardPitchAxis, axis(PITCH_BACK.isDown(), PITCH_FORWARD.isDown()));
		keyboardRollAxis = approachKeyboardAxis(keyboardRollAxis, axis(ROLL_LEFT.isDown(), ROLL_RIGHT.isDown()));
		keyboardYawAxis = approachKeyboardAxis(keyboardYawAxis, axis(YAW_LEFT.isDown(), YAW_RIGHT.isDown()));
		return new ControlInput(
				throttle,
				keyboardCommandAxis(keyboardPitchAxis),
				keyboardCommandAxis(keyboardRollAxis),
				keyboardCommandAxis(keyboardYawAxis),
				InputSource.KEYBOARD
		);
	}

	private static void resetKeyboardAxes() {
		keyboardPitchAxis = 0.0f;
		keyboardRollAxis = 0.0f;
		keyboardYawAxis = 0.0f;
	}

	private static float approachKeyboardAxis(float current, float target) {
		float step = Math.abs(target) > Math.abs(current) ? KEYBOARD_AXIS_RISE_PER_TICK : KEYBOARD_AXIS_FALL_PER_TICK;
		if (current < target) {
			return Math.min(target, current + step);
		}
		if (current > target) {
			return Math.max(target, current - step);
		}
		return current;
	}

	private static float keyboardCommandAxis(float value) {
		return (float) ControlStickProfile.keyboardCommand(value);
	}

	private static GamepadInput gamepadInput() {
		for (int joystick = GLFW.GLFW_JOYSTICK_1; joystick <= GLFW.GLFW_JOYSTICK_LAST; joystick++) {
			if (!GLFW.glfwJoystickPresent(joystick)) {
				continue;
			}

			FloatBuffer axes = GLFW.glfwGetJoystickAxes(joystick);
			if (axes == null || axes.limit() <= 0) {
				continue;
			}

			ByteBuffer buttons = GLFW.glfwGetJoystickButtons(joystick);
			float rawThrottle = throttleAxisRaw(axes, config.throttleAxis(), config.throttleInverted());
			float mappedThrottle = config.calibrateThrottle(rawThrottle);
			float roll = stickAxis(axes, config.rollAxis(), config::calibrateRollAxis);
			float pitch = stickAxis(axes, config.pitchAxis(), config::calibratePitchAxis);
			float yaw = stickAxis(axes, config.yawAxis(), config::calibrateYawAxis);

			return new GamepadInput(
					mappedThrottle,
					pitch,
					roll,
					yaw,
					rawThrottle,
					isGamepadButtonPressed(buttons, config.armButton()),
					isGamepadButtonPressed(buttons, config.disarmButton()),
					isGamepadButtonPressed(buttons, config.throttleCalibrateButton())
			);
		}

		return null;
	}

	private static float throttleAxisRaw(FloatBuffer axes, int axis, boolean inverted) {
		if (axes == null || axis < 0 || axis >= axes.limit()) {
			return 0.0f;
		}
		float value = axes.get(axis);
		if (inverted) {
			value = -value;
		}
		return (float) Mth.clamp((value + 1.0f) * 0.5f, 0.0f, 1.0f);
	}

	private static float stickAxis(FloatBuffer axes, int axis, AxisCalibrator calibrator) {
		if (axes == null || axis < 0 || axis >= axes.limit()) {
			return 0.0f;
		}
		float value = axes.get(axis);
		float calibrated = calibrator == null ? value : calibrator.calibrate(value);
		return GamepadStickShaper.conditionedAxis(calibrated, config.gamepadDeadband());
	}

	private static boolean isGamepadButtonPressed(ByteBuffer buttons, int button) {
		if (buttons == null || button < 0 || button >= buttons.limit()) {
			return false;
		}
		return buttons.get(button) == GLFW.GLFW_PRESS;
	}

	private static float commandAxis(float value) {
		return commandAxis(value, 1.0f);
	}

	private static float commandAxis(float value, float rateScale) {
		return GamepadStickShaper.commandFromConditionedAxis(value, config.gamepadDeadband(), config.gamepadExpo(), rateScale);
	}

	private record ControlInput(float throttle, float pitch, float roll, float yaw, InputSource source) {
	}

	private record GamepadInput(float throttle,
							   float pitch,
							   float roll,
							   float yaw,
							   float rawThrottle,
							   boolean armButtonPressed,
							   boolean disarmButtonPressed,
							   boolean calibrateButtonPressed) {
	}

	@FunctionalInterface
	private interface AxisCalibrator {
		float calibrate(float rawAxis);
	}
}
