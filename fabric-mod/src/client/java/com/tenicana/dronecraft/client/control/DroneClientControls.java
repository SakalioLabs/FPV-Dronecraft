package com.tenicana.dronecraft.client.control;

import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.client.DroneClientState.InputSource;
import com.tenicana.dronecraft.client.config.DroneClientConfig;
import com.tenicana.dronecraft.client.config.DroneControllerSettingsScreen;
import com.tenicana.dronecraft.client.mixin.MinecraftCameraStateAccessor;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.network.DroneControlPayload;
import com.tenicana.dronecraft.network.DroneViewPayload;
import com.tenicana.dronecraft.registry.DroneItems;
import com.tenicana.dronecraft.sim.DroneConfig;
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
	private static final float GAMEPAD_THROTTLE_RISE_PER_TICK = 0.08f;
	private static final float GAMEPAD_THROTTLE_FALL_PER_TICK = 0.14f;

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
	private static boolean disabledControllerActivityWarned;
	private static int payloadSequence;
	private static final int ARM_GESTURE_HOLD_TICKS = 7;
	private static final int MODE_SWITCH_RAMP_TICKS = 8;
	private static final StickArmGestureLatch STICK_ARM_GESTURE = new StickArmGestureLatch(ARM_GESTURE_HOLD_TICKS);
	private static final FlightModeInputRamp MODE_SWITCH_RAMP = new FlightModeInputRamp(MODE_SWITCH_RAMP_TICKS);
	private static final ControlInputSmoother INPUT_SMOOTHER = new ControlInputSmoother();
	private static final DroneControlSession CONTROL_SESSION = new DroneControlSession();
	private static final ControllerButtonEdgeTracker BUTTON_EDGE_TRACKER = new ControllerButtonEdgeTracker();
	private static final ControllerActivityKeepalive CONTROLLER_ACTIVITY_KEEPALIVE = new ControllerActivityKeepalive();
	private static final JoystickProvider JOYSTICKS = GlfwJoystickProvider.INSTANCE;
	private static final UserActivityNotifier USER_ACTIVITY_NOTIFIER = MinecraftUserActivityNotifier.INSTANCE;
	private static DroneClientConfig config = DroneClientConfig.defaults();

	private DroneClientControls() {
	}

	public static void initialize() {
		config = DroneClientConfig.load();
		gamepadEnabled = config.gamepadEnabled();
		DroneClientState.setHudMode(config.hudMode());
		throttleCalibrationActive = false;

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetClientRuntimeState(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetClientRuntimeState(client));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				resetClientRuntimeState(client);
				return;
			}

			boolean hasController = client.player.getMainHandItem().is(DroneItems.DRONE_CONTROLLER)
					|| client.player.getOffhandItem().is(DroneItems.DRONE_CONTROLLER);
			boolean fpvRequestedBeforeRefresh = DroneClientState.isFpvViewEnabled();
			DroneClientState.refreshControlledDrone(client);
			if (CONTROL_SESSION.updateControlledDrone(controlledDroneId())) {
				resetControlSessionState();
			}
			boolean hasLinkedDrone = DroneClientState.hasControlledDrone(client);
			if (fpvRequestedBeforeRefresh && !hasLinkedDrone) {
				disableFpvView(client, true, true);
			}

			while (VIRTUAL_CONTROLLER.consumeClick()) {
				virtualControllerEnabled = !virtualControllerEnabled;
				client.player.displayClientMessage(
						Component.translatable(virtualControllerEnabled ? "message.fpvdrone.virtual_controller_enabled" : "message.fpvdrone.virtual_controller_disabled"),
						true
				);
			}

			while (FPV_VIEW.consumeClick()) {
				boolean fpvEnabled = !DroneClientState.isFpvViewEnabled();
				if (fpvEnabled) {
					DroneClientState.setFpvViewEnabled(true);
					sendFpvViewState(true);
				} else {
					disableFpvView(client, true);
				}
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

			gamepadEnabled = config.gamepadEnabled();
			boolean controlAuthorized = DroneControlAuthority.hasControlAuthority(hasController, virtualControllerEnabled, hasLinkedDrone);
			GamepadInputPath.Decision gamepadDecision = GamepadInputPath.evaluate(
					config,
					JOYSTICKS,
					hasController,
					virtualControllerEnabled,
					hasLinkedDrone,
					currentHoverThrottle(),
					true
			);
			if (gamepadDecision.frame().resolution().migrated()) {
				config.save();
			}
			if (gamepadDecision.disabledControllerActivity() && !disabledControllerActivityWarned) {
				disabledControllerActivityWarned = true;
				client.player.displayClientMessage(Component.translatable("message.fpvdrone.gamepad_input_detected_disabled"), true);
			}
			if (gamepadEnabled) {
				disabledControllerActivityWarned = false;
			}
			GamepadInputFrame gamepadInput = gamepadDecision.hasUsableGamepadInput()
					? gamepadDecision.frame()
					: null;
			if (!controlAuthorized) {
				while (ARM.consumeClick()) {
					displayArmBlocked(client, ArmSafetyCheck.Result.blocked(ArmSafetyCheck.Reason.NO_CONTROL_AUTHORITY));
				}
				STICK_ARM_GESTURE.reset();
				MODE_SWITCH_RAMP.reset();
				resetTransientControlState();
				ControllerInputDiagnostics.Snapshot diagnostics = ControllerInputDiagnostics.fromRuntime(
						client.player.tickCount,
						gamepadDecision,
						new ClientControlInput(throttle, 0.0f, 0.0f, 0.0f, InputSource.KEYBOARD),
						gamepadEnabled,
						hasController,
						hasLinkedDrone,
						false,
						armed,
						ArmSafetyCheck.Result.blocked(ArmSafetyCheck.Reason.NO_CONTROL_AUTHORITY),
						ControllerInputDiagnostics.ControllerPayloadTrace.empty(),
						ControllerActivityKeepalive.Result.idle(USER_ACTIVITY_NOTIFIER)
				);
				ControllerInputDiagnostics.record(diagnostics, client.player.tickCount);
				DroneClientState.setControllerDiagnostics(diagnostics);
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
				disableFpvView(client, true);
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
				BUTTON_EDGE_TRACKER.reset();
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

			ControllerButtonEdgeTracker.ButtonEdges buttonEdges = ControllerButtonEdgeTracker.ButtonEdges.none();
			if (gamepadInput != null) {
				buttonEdges = BUTTON_EDGE_TRACKER.sample(gamepadInput);
				handleGamepadButtons(client, gamepadInput, buttonEdges);
				handleStickArmGesture(client, gamepadInput);
			} else {
				BUTTON_EDGE_TRACKER.reset();
			}

			ClientControlInput input = gamepadInput != null ? gamepadInputAsControl(gamepadInput) : keyboardInput(client);
			if (gamepadInput != null && isStickArmGesture(gamepadInput)) {
				INPUT_SMOOTHER.reset();
				input = new ClientControlInput(0.0f, 0.0f, 0.0f, 0.0f, InputSource.GAMEPAD);
			} else if (input.source() == InputSource.GAMEPAD) {
				input = smoothGamepadInput(input);
			}
			input = applyModeSwitchRamp(input);
			throttle = input.throttle();
			ControllerActivityKeepalive.Result activityResult = CONTROLLER_ACTIVITY_KEEPALIVE.sample(
					client.player.tickCount,
					gamepadDecision,
					input,
					hasLinkedDrone,
					controlAuthorized,
					armed,
					buttonEdges,
					USER_ACTIVITY_NOTIFIER
			);
			ControllerInputDiagnostics.ControllerPayloadTrace payloadTrace = new ControllerInputDiagnostics.ControllerPayloadTrace(
					++payloadSequence,
					input.throttle(),
					input.pitch(),
					input.roll(),
					input.yaw(),
					armed
			);
			ControllerInputDiagnostics.Snapshot diagnostics = ControllerInputDiagnostics.fromRuntime(
					client.player.tickCount,
					gamepadDecision,
					input,
					gamepadEnabled,
					hasController,
					hasLinkedDrone,
					controlAuthorized,
					armed,
					armCheckForDiagnostics(input),
					payloadTrace,
					activityResult
			);
			ControllerInputDiagnostics.record(diagnostics, client.player.tickCount);
			DroneClientState.setControllerDiagnostics(diagnostics);
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
			restoreInvalidDroneCamera(client);
			ClientPlayNetworking.send(new DroneControlPayload(input.throttle(), input.pitch(), input.roll(), input.yaw(), armed, flightMode.id()));
			sendFpvViewState(DroneClientState.isFpvViewEnabled());
		});
	}

	private static void sendFpvViewState(boolean fpvView) {
		ClientPlayNetworking.send(new DroneViewPayload(fpvView));
	}

	private static void disableFpvView(Minecraft client, boolean notifyServer) {
		disableFpvView(client, notifyServer, false);
	}

	private static void disableFpvView(Minecraft client, boolean notifyServer, boolean forceNotifyServer) {
		boolean wasEnabled = DroneClientState.isFpvViewEnabled();
		DroneClientState.setFpvViewEnabled(false);
		restoreVanillaCamera(client);
		if (notifyServer && (wasEnabled || forceNotifyServer)) {
			sendFpvViewState(false);
		}
	}

	public static DroneClientConfig config() {
		return config;
	}

	private static void handleGamepadButtons(Minecraft client, GamepadInputFrame input, ControllerButtonEdgeTracker.ButtonEdges edges) {
		if (edges.armPressed() && !input.disarmButtonPressed()) {
			requestArmed(client, true, canArmWithGamepadButton(input));
		}
		if (edges.disarmPressed() && !input.armButtonPressed()) {
			requestArmed(client, false, ArmSafetyCheck.Result.ok());
		}
		if (edges.calibratePressed()) {
			toggleThrottleCalibration(client, input.rawThrottle());
		}

		if (throttleCalibrationActive && Float.isFinite(input.rawThrottle())) {
			sampledThrottleMin = Math.min(sampledThrottleMin, input.rawThrottle());
			sampledThrottleMax = Math.max(sampledThrottleMax, input.rawThrottle());
		}
	}

	private static void handleStickArmGesture(Minecraft client, GamepadInputFrame input) {
		if (!STICK_ARM_GESTURE.update(isStickArmGesture(input))) {
			return;
		}
		resetTransientControlState();
		armed = !armed;
		client.player.displayClientMessage(Component.translatable(armed ? "message.fpvdrone.armed" : "message.fpvdrone.disarmed"), true);
	}

	private static boolean isStickArmGesture(GamepadInputFrame input) {
		return input != null
				&& DroneArmSafety.isStickArmGesture(input.throttle(), input.pitch(), input.roll(), input.yaw());
	}

	private static ArmSafetyCheck.Result canArmWithGamepadButton(GamepadInputFrame input) {
		if (config.armButton() < 0) {
			return ArmSafetyCheck.Result.blocked(ArmSafetyCheck.Reason.ARM_BUTTON_UNCONFIGURED);
		}
		if (input == null || !input.usable()) {
			return ArmSafetyCheck.Result.blocked(ArmSafetyCheck.Reason.NO_GAMEPAD_DEVICE);
		}
		return DroneArmSafety.checkMomentaryArm(input.throttle(), input.pitch(), input.roll(), input.yaw());
	}

	private static ArmSafetyCheck.Result canArmWithKeyboard(Minecraft client) {
		float pitch = largerMagnitude(keyboardPitchAxis, axis(PITCH_BACK.isDown(), PITCH_FORWARD.isDown()));
		float roll = largerMagnitude(keyboardRollAxis, axis(ROLL_LEFT.isDown(), ROLL_RIGHT.isDown()));
		float yaw = largerMagnitude(keyboardYawAxis, axis(YAW_LEFT.isDown(), YAW_RIGHT.isDown()));
		return DroneArmSafety.checkMomentaryArm(throttle, pitch, roll, yaw);
	}

	private static float largerMagnitude(float current, float target) {
		return Math.abs(current) >= Math.abs(target) ? current : target;
	}

	private static void requestArmed(Minecraft client, boolean targetArmed, ArmSafetyCheck.Result armCheck) {
		if (targetArmed) {
			if (armed) {
				return;
			}
			ArmSafetyCheck.Result safeArmCheck = armCheck == null
					? ArmSafetyCheck.Result.blocked(ArmSafetyCheck.Reason.NO_CONTROL_AUTHORITY)
					: armCheck;
			if (!safeArmCheck.allowed()) {
				displayArmBlocked(client, safeArmCheck);
				return;
			}
			resetTransientControlState();
			armed = true;
			client.player.displayClientMessage(Component.translatable("message.fpvdrone.armed"), true);
			return;
		}

		if (!armed) {
			return;
		}
		resetTransientControlState();
		armed = false;
		client.player.displayClientMessage(Component.translatable("message.fpvdrone.disarmed"), true);
	}

	private static void displayArmBlocked(Minecraft client, ArmSafetyCheck.Result armCheck) {
		ArmSafetyCheck.Result safeArmCheck = armCheck == null
				? ArmSafetyCheck.Result.blocked(ArmSafetyCheck.Reason.NO_CONTROL_AUTHORITY)
				: armCheck;
		client.player.displayClientMessage(Component.translatable(safeArmCheck.reason().translationKey()), true);
	}

	private static void resetTransientControlState() {
		throttle = 0.0f;
		INPUT_SMOOTHER.reset();
		resetKeyboardAxes();
	}

	private static void resetControlSessionState() {
		resetTransientControlState();
		STICK_ARM_GESTURE.reset();
		MODE_SWITCH_RAMP.reset();
		armed = false;
		CONTROLLER_ACTIVITY_KEEPALIVE.reset();
	}

	private static void resetClientRuntimeState(Minecraft client) {
		BUTTON_EDGE_TRACKER.reset();
		STICK_ARM_GESTURE.reset();
		MODE_SWITCH_RAMP.reset();
		INPUT_SMOOTHER.reset();
		CONTROL_SESSION.clear();
		CONTROLLER_ACTIVITY_KEEPALIVE.reset();
		resetTransientControlState();
		armed = false;
		virtualControllerEnabled = false;
		throttleCalibrationActive = false;
		disabledControllerActivityWarned = false;
		DroneClientState.resetTransientFlightState();
		restoreVanillaCamera(client);
	}

	private static void restoreVanillaCamera(Minecraft client) {
		if (client == null) {
			return;
		}
		clearStaleHitTargets(client);
		if (client.player == null) {
			return;
		}
		if (client.getCameraEntity() != client.player) {
			client.setCameraEntity(client.player);
		}
	}

	private static void clearStaleHitTargets(Minecraft client) {
		if (client == null) {
			return;
		}
		MinecraftCameraStateAccessor cameraState = (MinecraftCameraStateAccessor) client;
		cameraState.fpvdrone$setCrosshairPickEntity(null);
		cameraState.fpvdrone$setHitResult(null);
	}

	private static void restoreInvalidDroneCamera(Minecraft client) {
		if (client == null || client.player == null || client.level == null) {
			return;
		}
		if (client.getCameraEntity() instanceof DroneEntity && !DroneClientState.isFpvActive(client.level)) {
			restoreVanillaCamera(client);
		}
	}

	private static UUID controlledDroneId() {
		DroneEntity drone = DroneClientState.controlledDrone();
		return drone == null ? null : drone.getUUID();
	}

	private static ClientControlInput gamepadInputAsControl(GamepadInputFrame input) {
		return input.controlInput();
	}

	private static ClientControlInput smoothGamepadInput(ClientControlInput input) {
		float throttle = INPUT_SMOOTHER.sampleThrottle(
				input.throttle(),
				GAMEPAD_THROTTLE_RISE_PER_TICK,
				GAMEPAD_THROTTLE_FALL_PER_TICK
		);
		ControlInputSmoother.Axes axes = INPUT_SMOOTHER.sample(
				input.pitch(),
				input.roll(),
				input.yaw(),
				config.gamepadAxisRisePerTick(),
				config.gamepadAxisFallPerTick()
		);
		return new ClientControlInput(throttle, axes.pitch(), axes.roll(), axes.yaw(), input.source());
	}

	private static ClientControlInput applyModeSwitchRamp(ClientControlInput input) {
		float scale = MODE_SWITCH_RAMP.sampleAndAdvance();
		if (scale >= 0.999f) {
			return input;
		}
		return new ClientControlInput(
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

	private static ClientControlInput keyboardInput(Minecraft client) {
		throttle = KeyboardControlShaper.adjustThrottle(throttle, keyboardThrottleDirection(), currentHoverThrottle());

		keyboardPitchAxis = approachKeyboardAxis(keyboardPitchAxis, axis(PITCH_BACK.isDown(), PITCH_FORWARD.isDown()));
		keyboardRollAxis = approachKeyboardAxis(keyboardRollAxis, axis(ROLL_LEFT.isDown(), ROLL_RIGHT.isDown()));
		keyboardYawAxis = approachKeyboardAxis(keyboardYawAxis, axis(YAW_LEFT.isDown(), YAW_RIGHT.isDown()));
		return new ClientControlInput(
				throttle,
				keyboardCommandAxis(keyboardPitchAxis),
				keyboardCommandAxis(keyboardRollAxis),
				keyboardCommandAxis(keyboardYawAxis),
				InputSource.KEYBOARD
		);
	}

	private static int keyboardThrottleDirection() {
		if (THROTTLE_UP.isDown() == THROTTLE_DOWN.isDown()) {
			return 0;
		}
		return THROTTLE_UP.isDown() ? 1 : -1;
	}

	private static float currentHoverThrottle() {
		DroneEntity drone = DroneClientState.controlledDrone();
		double hoverThrottle = drone == null ? DroneConfig.racingQuad().hoverThrottle() : drone.config().hoverThrottle();
		return (float) Mth.clamp(hoverThrottle, 0.05, 0.75);
	}

	private static void resetKeyboardAxes() {
		keyboardPitchAxis = 0.0f;
		keyboardRollAxis = 0.0f;
		keyboardYawAxis = 0.0f;
	}

	private static float approachKeyboardAxis(float current, float target) {
		return KeyboardControlShaper.approachAxis(current, target);
	}

	private static float keyboardCommandAxis(float value) {
		return KeyboardControlShaper.commandAxis(value);
	}

	private static ArmSafetyCheck.Result armCheckForDiagnostics(ClientControlInput input) {
		if (armed) {
			return ArmSafetyCheck.Result.ok();
		}
		if (input == null) {
			return ArmSafetyCheck.Result.blocked(ArmSafetyCheck.Reason.NO_CONTROL_AUTHORITY);
		}
		if (input.source() == InputSource.GAMEPAD && config.armButton() < 0) {
			return ArmSafetyCheck.Result.blocked(ArmSafetyCheck.Reason.ARM_BUTTON_UNCONFIGURED);
		}
		return DroneArmSafety.checkMomentaryArm(input.throttle(), input.pitch(), input.roll(), input.yaw());
	}
}
