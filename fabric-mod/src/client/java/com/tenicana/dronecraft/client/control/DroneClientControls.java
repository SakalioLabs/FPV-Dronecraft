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
import com.tenicana.dronecraft.sim.FlightMode;

public final class DroneClientControls {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "controls")
	);
	private static final KeyMapping ARM = register("key.fpvdrone.arm", GLFW.GLFW_KEY_R);
	private static final KeyMapping FLIGHT_MODE = register("key.fpvdrone.flight_mode", GLFW.GLFW_KEY_M);
	private static final KeyMapping VIRTUAL_CONTROLLER = register("key.fpvdrone.virtual_controller", GLFW.GLFW_KEY_V);
	private static final KeyMapping GAMEPAD_TOGGLE = register("key.fpvdrone.gamepad_toggle", GLFW.GLFW_KEY_G);
	private static final KeyMapping CONFIG_RELOAD = register("key.fpvdrone.reload_config", GLFW.GLFW_KEY_H);
	private static final KeyMapping CONTROLLER_SETTINGS = register("key.fpvdrone.open_controller_settings", GLFW.GLFW_KEY_I);
	private static final KeyMapping THROTTLE_UP = register("key.fpvdrone.throttle_up", GLFW.GLFW_KEY_SPACE);
	private static final KeyMapping THROTTLE_DOWN = register("key.fpvdrone.throttle_down", GLFW.GLFW_KEY_LEFT_SHIFT);
	private static final KeyMapping YAW_LEFT = register("key.fpvdrone.yaw_left", GLFW.GLFW_KEY_Q);
	private static final KeyMapping YAW_RIGHT = register("key.fpvdrone.yaw_right", GLFW.GLFW_KEY_E);
	private static final KeyMapping THROTTLE_CALIBRATE = register("key.fpvdrone.calibrate_throttle", GLFW.GLFW_KEY_C);
	private static final float THROTTLE_CALIBRATION_MIN_SPAN = 0.05f;

	private static float throttle;
	private static boolean armed;
	private static FlightMode flightMode = FlightMode.ACRO;
	private static boolean virtualControllerEnabled;
	private static boolean gamepadEnabled = true;
	private static boolean throttleCalibrationActive;
	private static float sampledThrottleMin = 0.0f;
	private static float sampledThrottleMax = 1.0f;
	private static boolean gamepadArmButtonDown;
	private static boolean gamepadDisarmButtonDown;
	private static boolean gamepadCalibrateButtonDown;
	private static boolean autoArmSampleInitialized;
	private static float autoArmSampleThrottle;
	private static float autoArmSamplePitch;
	private static float autoArmSampleRoll;
	private static float autoArmSampleYaw;
	private static final float AUTO_ARM_MOVEMENT_DELTA = 0.11f;
	private static DroneClientConfig config = DroneClientConfig.defaults();

	private DroneClientControls() {
	}

	public static void initialize() {
		config = DroneClientConfig.load();
		gamepadEnabled = config.gamepadEnabled();
		throttleCalibrationActive = false;

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				gamepadArmButtonDown = false;
				gamepadDisarmButtonDown = false;
				gamepadCalibrateButtonDown = false;
				throttleCalibrationActive = false;
				return;
			}

			boolean hasController = client.player.getMainHandItem().is(DroneItems.DRONE_CONTROLLER)
					|| client.player.getOffhandItem().is(DroneItems.DRONE_CONTROLLER);
			DroneClientState.refreshControlledDrone(client);
			boolean hasLinkedDrone = DroneClientState.controlledDrone() != null && DroneClientState.controlledDrone().isAlive();
			GamepadInput gamepadInput = gamepadEnabled ? gamepadInput() : null;

			while (VIRTUAL_CONTROLLER.consumeClick()) {
				virtualControllerEnabled = !virtualControllerEnabled;
				client.player.displayClientMessage(
						Component.translatable(virtualControllerEnabled ? "message.fpvdrone.virtual_controller_enabled" : "message.fpvdrone.virtual_controller_disabled"),
						true
				);
			}

			boolean gamepadActive = gamepadEnabled && gamepadInput != null;
			boolean controlActive = hasController || (virtualControllerEnabled && hasLinkedDrone) || gamepadActive;
			if (!controlActive) {
				autoArmSampleInitialized = false;
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
				armed = !armed;
				client.player.displayClientMessage(Component.translatable(armed ? "message.fpvdrone.armed" : "message.fpvdrone.disarmed"), true);
			}

			while (FLIGHT_MODE.consumeClick()) {
				flightMode = flightMode.next();
				client.player.displayClientMessage(Component.literal("Drone mode: " + flightMode.name()), true);
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
				autoArmFromJoystickMovement(gamepadInput);
			}

			ControlInput input = gamepadInput != null ? gamepadInputAsControl(gamepadInput) : keyboardInput(client);
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
			armed = true;
			client.player.displayClientMessage(Component.translatable("message.fpvdrone.armed"), true);
		}
		if (disarmPressedEdge && !input.armButtonPressed()) {
			armed = false;
			client.player.displayClientMessage(Component.translatable("message.fpvdrone.disarmed"), true);
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

	private static void autoArmFromJoystickMovement(GamepadInput input) {
		if (armed) {
			autoArmSampleInitialized = false;
			return;
		}

		if (!Float.isFinite(input.throttle()) || !Float.isFinite(input.pitch()) || !Float.isFinite(input.roll()) || !Float.isFinite(input.yaw())) {
			autoArmSampleInitialized = false;
			return;
		}

		if (!autoArmSampleInitialized) {
			autoArmSampleInitialized = true;
			autoArmSampleThrottle = input.throttle();
			autoArmSamplePitch = input.pitch();
			autoArmSampleRoll = input.roll();
			autoArmSampleYaw = input.yaw();
			return;
		}

		float throttleDelta = Math.abs(input.throttle() - autoArmSampleThrottle);
		float pitchDelta = Math.abs(input.pitch() - autoArmSamplePitch);
		float rollDelta = Math.abs(input.roll() - autoArmSampleRoll);
		float yawDelta = Math.abs(input.yaw() - autoArmSampleYaw);
		if (throttleDelta > AUTO_ARM_MOVEMENT_DELTA
				|| pitchDelta > AUTO_ARM_MOVEMENT_DELTA
				|| rollDelta > AUTO_ARM_MOVEMENT_DELTA
				|| yawDelta > AUTO_ARM_MOVEMENT_DELTA) {
			armed = true;
			clientMessageAutoArmed();
		}

		autoArmSampleThrottle = input.throttle();
		autoArmSamplePitch = input.pitch();
		autoArmSampleRoll = input.roll();
		autoArmSampleYaw = input.yaw();
	}

	private static void clientMessageAutoArmed() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(Component.translatable("message.fpvdrone.auto_armed_hint"), true);
		}
	}

	private static ControlInput gamepadInputAsControl(GamepadInput input) {
		return new ControlInput(input.throttle(), input.pitch(), input.roll(), input.yaw(), InputSource.GAMEPAD);
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

		float pitch = axis(client.options.keyDown.isDown(), client.options.keyUp.isDown());
		float roll = axis(client.options.keyLeft.isDown(), client.options.keyRight.isDown());
		float yaw = axis(YAW_LEFT.isDown(), YAW_RIGHT.isDown());
		return new ControlInput(throttle, pitch, roll, yaw, InputSource.KEYBOARD);
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
			float roll = stickAxis(axes, config.rollAxis(), config.rollInverted());
			float pitch = stickAxis(axes, config.pitchAxis(), config.pitchInverted());
			float yaw = stickAxis(axes, config.yawAxis(), config.yawInverted());

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

	private static float stickAxis(FloatBuffer axes, int axis, boolean inverted) {
		if (axes == null || axis < 0 || axis >= axes.limit()) {
			return 0.0f;
		}
		float value = axes.get(axis);
		if (inverted) {
			value = -value;
		}
		return deadband(value, config.gamepadDeadband());
	}

	private static boolean isGamepadButtonPressed(ByteBuffer buttons, int button) {
		if (buttons == null || button < 0 || button >= buttons.limit()) {
			return false;
		}
		return buttons.get(button) == GLFW.GLFW_PRESS;
	}

	private static float deadband(float value, float deadband) {
		float magnitude = Math.abs(value);
		if (magnitude <= deadband) {
			return 0.0f;
		}
		return Math.copySign((magnitude - deadband) / (1.0f - deadband), value);
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
}
