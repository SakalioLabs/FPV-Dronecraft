package com.tenicana.dronecraft.client.control;

import java.nio.FloatBuffer;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
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
	private static final KeyMapping THROTTLE_UP = register("key.fpvdrone.throttle_up", GLFW.GLFW_KEY_SPACE);
	private static final KeyMapping THROTTLE_DOWN = register("key.fpvdrone.throttle_down", GLFW.GLFW_KEY_LEFT_SHIFT);
	private static final KeyMapping YAW_LEFT = register("key.fpvdrone.yaw_left", GLFW.GLFW_KEY_Q);
	private static final KeyMapping YAW_RIGHT = register("key.fpvdrone.yaw_right", GLFW.GLFW_KEY_E);

	private static float throttle;
	private static boolean armed;
	private static FlightMode flightMode = FlightMode.ACRO;
	private static boolean virtualControllerEnabled;
	private static boolean gamepadEnabled = true;
	private static DroneClientConfig config = DroneClientConfig.defaults();

	private DroneClientControls() {
	}

	public static void initialize() {
		config = DroneClientConfig.load();
		gamepadEnabled = config.gamepadEnabled();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				return;
			}

			boolean hasController = client.player.getMainHandItem().is(DroneItems.DRONE_CONTROLLER)
					|| client.player.getOffhandItem().is(DroneItems.DRONE_CONTROLLER);
			DroneClientState.refreshControlledDrone(client);
			boolean hasLinkedDrone = DroneClientState.controlledDrone() != null && DroneClientState.controlledDrone().isAlive();

			while (VIRTUAL_CONTROLLER.consumeClick()) {
				virtualControllerEnabled = !virtualControllerEnabled;
				client.player.displayClientMessage(Component.translatable(virtualControllerEnabled ? "message.fpvdrone.virtual_controller_enabled" : "message.fpvdrone.virtual_controller_disabled"), true);
			}

			boolean controlActive = hasController || (virtualControllerEnabled && hasLinkedDrone);
			if (!controlActive) {
				DroneClientState.updateControls(throttle, 0.0f, 0.0f, 0.0f, armed, false, hasController, virtualControllerEnabled, flightMode, InputSource.KEYBOARD);
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
				client.player.displayClientMessage(Component.translatable("message.fpvdrone.config_reloaded", DroneClientConfig.path().toAbsolutePath().toString()), true);
			}

			ControlInput input = gamepadEnabled ? gamepadInput() : null;
			if (input == null) {
				input = keyboardInput(client);
			} else {
				throttle = input.throttle();
			}

			DroneClientState.updateControls(input.throttle(), input.pitch(), input.roll(), input.yaw(), armed, true, hasController, virtualControllerEnabled, flightMode, input.source());
			ClientPlayNetworking.send(new DroneControlPayload(input.throttle(), input.pitch(), input.roll(), input.yaw(), armed, flightMode.id()));
		});
	}

	public static DroneClientConfig config() {
		return config;
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

	private static ControlInput keyboardInput(net.minecraft.client.Minecraft client) {
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

	private static ControlInput gamepadInput() {
		for (int joystick = GLFW.GLFW_JOYSTICK_1; joystick <= GLFW.GLFW_JOYSTICK_LAST; joystick++) {
			if (!GLFW.glfwJoystickPresent(joystick)) {
				continue;
			}

			FloatBuffer axes = GLFW.glfwGetJoystickAxes(joystick);
			if (axes == null || !config.hasRequiredAxes(axes.limit())) {
				continue;
			}

			float roll = stickAxis(axes, config.rollAxis(), config.rollInverted());
			float pitch = stickAxis(axes, config.pitchAxis(), config.pitchInverted());
			float yaw = stickAxis(axes, config.yawAxis(), config.yawInverted());
			float gamepadThrottle = throttleAxis(axes, config.throttleAxis(), config.throttleInverted());
			return new ControlInput(gamepadThrottle, pitch, roll, yaw, InputSource.GAMEPAD);
		}

		return null;
	}

	private static float stickAxis(FloatBuffer axes, int axis, boolean inverted) {
		float value = axes.get(axis);
		if (inverted) {
			value = -value;
		}
		return deadband(value, config.gamepadDeadband());
	}

	private static float throttleAxis(FloatBuffer axes, int axis, boolean inverted) {
		float value = axes.get(axis);
		if (inverted) {
			value = -value;
		}

		float normalized = (float) Mth.clamp((value + 1.0f) * 0.5f, 0.0f, 1.0f);
		float endpointSnap = config.gamepadDeadband() * 0.5f;
		if (normalized <= endpointSnap) {
			return 0.0f;
		}
		if (normalized >= 1.0f - endpointSnap) {
			return 1.0f;
		}
		return normalized;
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
}
