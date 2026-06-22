package com.tenicana.dronecraft.client;

import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneItems;
import com.tenicana.dronecraft.client.control.ControllerInputDiagnostics;
import com.tenicana.dronecraft.sim.FlightMode;

public final class DroneClientState {
	public static final FlightMode DEFAULT_FLIGHT_MODE = FlightMode.DEFAULT_FIRST_FLIGHT;

	private static DroneEntity controlledDrone;
	private static float throttle;
	private static float pitch;
	private static float roll;
	private static float yaw;
	private static boolean armed;
	private static boolean controlActive;
	private static boolean physicalControllerPresent;
	private static boolean virtualControllerEnabled;
	private static boolean fpvViewEnabled;
	private static HudMode hudMode = HudMode.MINIMAL;
	private static boolean throttleCalibrated = true;
	private static boolean throttleCalibrationActive;
	private static FlightMode flightMode = DEFAULT_FLIGHT_MODE;
	private static InputSource inputSource = InputSource.KEYBOARD;
	private static ControllerInputDiagnostics.Snapshot controllerDiagnostics = ControllerInputDiagnostics.Snapshot.empty();

	private DroneClientState() {
	}

	public static void updateControls(
			float throttle,
			float pitch,
			float roll,
			float yaw,
			boolean armed,
			boolean controlActive,
			boolean physicalControllerPresent,
			boolean virtualControllerEnabled,
			FlightMode flightMode,
			InputSource inputSource,
			boolean throttleCalibrated,
			boolean throttleCalibrationActive
	) {
		DroneClientState.throttle = throttle;
		DroneClientState.pitch = pitch;
		DroneClientState.roll = roll;
		DroneClientState.yaw = yaw;
		DroneClientState.armed = armed;
		DroneClientState.controlActive = controlActive;
		DroneClientState.physicalControllerPresent = physicalControllerPresent;
		DroneClientState.virtualControllerEnabled = virtualControllerEnabled;
		DroneClientState.flightMode = flightMode == null ? DEFAULT_FLIGHT_MODE : flightMode;
		DroneClientState.inputSource = inputSource;
		DroneClientState.throttleCalibrated = throttleCalibrated;
		DroneClientState.throttleCalibrationActive = throttleCalibrationActive;
	}

	public static void refreshControlledDrone(Minecraft client) {
		if (client.player == null || client.level == null) {
			controlledDrone = null;
			controlActive = false;
			physicalControllerPresent = false;
			fpvViewEnabled = false;
			return;
		}

		physicalControllerPresent = client.player.getMainHandItem().is(DroneItems.DRONE_CONTROLLER)
				|| client.player.getOffhandItem().is(DroneItems.DRONE_CONTROLLER);

		if (controlledDrone != null && controlledDrone.isAlive() && controlledDrone.isOwnedBy(client.player.getUUID())) {
			return;
		}

		AABB search = client.player.getBoundingBox().inflate(256.0);
		List<DroneEntity> ownedDrones = client.level.getEntitiesOfClass(
				DroneEntity.class,
				search,
				drone -> drone.isAlive() && drone.isOwnedBy(client.player.getUUID())
		);
		controlledDrone = ownedDrones.stream()
				.min(Comparator.comparingDouble(drone -> drone.distanceToSqr(client.player)))
				.orElse(null);
		if (controlledDrone == null) {
			fpvViewEnabled = false;
		}
	}

	public static DroneEntity controlledDrone() {
		return controlledDrone;
	}

	public static boolean hasController() {
		return controlActive;
	}

	public static boolean hasPhysicalController() {
		return physicalControllerPresent;
	}

	public static boolean isVirtualControllerEnabled() {
		return virtualControllerEnabled;
	}

	public static boolean isFpvViewEnabled() {
		return fpvViewEnabled;
	}

	public static void setFpvViewEnabled(boolean enabled) {
		fpvViewEnabled = enabled;
	}

	public static boolean isHudEnabled() {
		return hudMode != HudMode.OFF;
	}

	public static void setHudEnabled(boolean enabled) {
		hudMode = enabled ? HudMode.MINIMAL : HudMode.OFF;
	}

	public static HudMode hudMode() {
		return hudMode;
	}

	public static void setHudMode(HudMode mode) {
		hudMode = mode == null ? HudMode.MINIMAL : mode;
	}

	public static HudMode cycleHudMode() {
		hudMode = hudMode.next();
		return hudMode;
	}

	public static boolean throttleCalibrated() {
		return throttleCalibrated;
	}

	public static boolean throttleCalibrationActive() {
		return throttleCalibrationActive;
	}

	public static boolean isFpvActive() {
		return fpvViewEnabled && controlledDrone != null && controlledDrone.isAlive();
	}

	public static float throttle() {
		return throttle;
	}

	public static float pitch() {
		return pitch;
	}

	public static float roll() {
		return roll;
	}

	public static float yaw() {
		return yaw;
	}

	public static boolean armed() {
		return armed;
	}

	public static FlightMode flightMode() {
		return flightMode;
	}

	public static InputSource inputSource() {
		return inputSource;
	}

	public static ControllerInputDiagnostics.Snapshot controllerDiagnostics() {
		return controllerDiagnostics;
	}

	public static void setControllerDiagnostics(ControllerInputDiagnostics.Snapshot diagnostics) {
		controllerDiagnostics = diagnostics == null ? ControllerInputDiagnostics.Snapshot.empty() : diagnostics;
	}

	public enum InputSource {
		KEYBOARD("hud.fpvdrone.source_keyboard"),
		GAMEPAD("hud.fpvdrone.source_gamepad");

		private final String translationKey;

		InputSource(String translationKey) {
			this.translationKey = translationKey;
		}

		public String translationKey() {
			return translationKey;
		}
	}

	public enum HudMode {
		MINIMAL("message.fpvdrone.hud_minimal"),
		FULL("message.fpvdrone.hud_full"),
		OFF("message.fpvdrone.hud_disabled");

		private final String translationKey;

		HudMode(String translationKey) {
			this.translationKey = translationKey;
		}

		public HudMode next() {
			return switch (this) {
				case MINIMAL -> OFF;
				case OFF -> FULL;
				case FULL -> MINIMAL;
			};
		}

		public String translationKey() {
			return translationKey;
		}
	}
}
