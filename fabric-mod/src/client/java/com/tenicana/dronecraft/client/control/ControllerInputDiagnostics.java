package com.tenicana.dronecraft.client.control;

import java.util.Locale;
import java.util.stream.Collectors;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.DroneClientState.InputSource;

public final class ControllerInputDiagnostics {
	private static final boolean LOG_ENABLED = Boolean.getBoolean("fpvdrone.controllerDiagnostics");
	private static final int LOG_INTERVAL_TICKS = 20;
	private static volatile Snapshot latest = Snapshot.empty();
	private static volatile String calibrationDevice = "none";
	private static int lastLogTick = Integer.MIN_VALUE;

	private ControllerInputDiagnostics() {
	}

	public static Snapshot latest() {
		return latest;
	}

	public static void updateCalibrationDevice(JoystickSnapshot snapshot) {
		calibrationDevice = snapshot == null ? "none" : label(snapshot);
	}

	static void record(Snapshot snapshot, int tick) {
		latest = snapshot == null ? Snapshot.empty() : snapshot;
		if (!LOG_ENABLED || tick - lastLogTick < LOG_INTERVAL_TICKS) {
			return;
		}
		lastLogTick = tick;
		FpvDronecraftMod.LOGGER.info("[FPV-Controller] {}", latest.compactLine());
	}

	static Snapshot fromRuntime(
			int tick,
			GamepadInputPath.Decision decision,
			ClientControlInput smoothedInput,
			boolean gamepadEnabled,
			boolean hasController,
			boolean hasLinkedDrone,
			boolean controlAuthorized,
			boolean armed,
			ArmSafetyCheck.Result armCheck,
			ControllerPayloadTrace payload
	) {
		GamepadInputFrame frame = decision == null ? null : decision.frame();
		GamepadDeviceResolver.Resolution resolution = frame == null ? null : frame.resolution();
		JoystickSnapshot selected = resolution == null ? null : resolution.selected();
		ClientControlInput safeSmoothed = smoothedInput == null
				? new ClientControlInput(0.0f, 0.0f, 0.0f, 0.0f, InputSource.KEYBOARD)
				: smoothedInput;
		ArmSafetyCheck.Result safeArmCheck = armCheck == null ? ArmSafetyCheck.Result.blocked(ArmSafetyCheck.Reason.NO_CONTROL_AUTHORITY) : armCheck;
		ControllerPayloadTrace safePayload = payload == null ? ControllerPayloadTrace.empty() : payload;
		return new Snapshot(
				tick,
				joystickList(resolution),
				selected == null ? "none" : label(selected),
				calibrationDevice,
				gamepadEnabled,
				hasController,
				hasLinkedDrone,
				controlAuthorized,
				decision != null && decision.shouldUseGamepadInput(),
				resolution == null ? GamepadDeviceResolver.Status.NO_DEVICES : resolution.status(),
				selected == null ? "[]" : axesText(selected.axes()),
				mappingText(frame),
				calibrationText(frame),
				frame == null ? "T0.000 P0.000 R0.000 Y0.000" : controlsText(frame.throttle(), frame.pitch(), frame.roll(), frame.yaw()),
				frame == null ? "T0.000 P0.000 R0.000 Y0.000" : controlsText(frame.throttleCommand(), frame.pitchCommand(), frame.rollCommand(), frame.yawCommand()),
				controlsText(safeSmoothed.throttle(), safeSmoothed.pitch(), safeSmoothed.roll(), safeSmoothed.yaw()),
				safeSmoothed.source(),
				armed,
				safeArmCheck.reason().diagnosticText(),
				safePayload.compactLine()
		);
	}

	private static String joystickList(GamepadDeviceResolver.Resolution resolution) {
		if (resolution == null || resolution.connected().isEmpty()) {
			return "[]";
		}
		return resolution.connected().stream().map(ControllerInputDiagnostics::label).collect(Collectors.joining("; "));
	}

	private static String label(JoystickSnapshot snapshot) {
		String guid = snapshot.guid().isBlank() ? "no-guid" : snapshot.guid();
		return String.format(Locale.ROOT, "%d:%s:%s", snapshot.glfwId(), snapshot.name(), guid);
	}

	private static String axesText(float[] axes) {
		if (axes == null || axes.length == 0) {
			return "[]";
		}
		StringBuilder builder = new StringBuilder("[");
		for (int i = 0; i < axes.length; i++) {
			if (i > 0) {
				builder.append(' ');
			}
			builder.append(i).append('=').append(String.format(Locale.ROOT, "%.3f", axes[i]));
		}
		return builder.append(']').toString();
	}

	private static String mappingText(GamepadInputFrame frame) {
		if (frame == null || frame.resolution() == null || frame.resolution().selected() == null) {
			return "unmapped";
		}
		return String.format(
				Locale.ROOT,
				"raw T%.3f P%.3f R%.3f Y%.3f",
				frame.rawThrottle(),
				frame.rawPitch(),
				frame.rawRoll(),
				frame.rawYaw()
		);
	}

	private static String calibrationText(GamepadInputFrame frame) {
		if (frame == null) {
			return "cal none";
		}
		return controlsText(frame.throttle(), frame.pitch(), frame.roll(), frame.yaw());
	}

	private static String controlsText(float throttle, float pitch, float roll, float yaw) {
		return String.format(Locale.ROOT, "T%.3f P%.3f R%.3f Y%.3f", throttle, pitch, roll, yaw);
	}

	public record Snapshot(
			int tick,
			String connectedJoysticks,
			String selectedRuntimeDevice,
			String calibrationSelectedDevice,
			boolean gamepadEnabled,
			boolean hasController,
			boolean hasLinkedDrone,
			boolean controlAuthorized,
			boolean shouldUseGamepadInput,
			GamepadDeviceResolver.Status resolverStatus,
			String rawAxes,
			String mappedAxes,
			String calibratedAxes,
			String calibratedControls,
			String shapedControls,
			String smoothedControls,
			InputSource inputSource,
			boolean armed,
			String armBlockReason,
			String payload
	) {
		public static Snapshot empty() {
			return new Snapshot(
					0,
					"[]",
					"none",
					"none",
					true,
					false,
					false,
					false,
					false,
					GamepadDeviceResolver.Status.NO_DEVICES,
					"[]",
					"unmapped",
					"cal none",
					"T0.000 P0.000 R0.000 Y0.000",
					"T0.000 P0.000 R0.000 Y0.000",
					"T0.000 P0.000 R0.000 Y0.000",
					InputSource.KEYBOARD,
					false,
					"none",
					"seq=0 T0.000 P0.000 R0.000 Y0.000 armed=false"
			);
		}

		public String compactLine() {
			return String.format(
					Locale.ROOT,
					"tick=%d selected=%s cal=%s enabled=%s authority=%s shouldGamepad=%s status=%s raw=%s mapped=%s shaped=%s smooth=%s source=%s armed=%s arm=%s payload=%s",
					tick,
					selectedRuntimeDevice,
					calibrationSelectedDevice,
					gamepadEnabled,
					controlAuthorized,
					shouldUseGamepadInput,
					resolverStatus,
					rawAxes,
					mappedAxes,
					shapedControls,
					smoothedControls,
					inputSource,
					armed,
					armBlockReason,
					payload
			);
		}
	}

	public record ControllerPayloadTrace(int sequence, float throttle, float pitch, float roll, float yaw, boolean armed) {
		public static ControllerPayloadTrace empty() {
			return new ControllerPayloadTrace(0, 0.0f, 0.0f, 0.0f, 0.0f, false);
		}

		public String compactLine() {
			return String.format(Locale.ROOT, "seq=%d T%.3f P%.3f R%.3f Y%.3f armed=%s", sequence, throttle, pitch, roll, yaw, armed);
		}
	}
}
