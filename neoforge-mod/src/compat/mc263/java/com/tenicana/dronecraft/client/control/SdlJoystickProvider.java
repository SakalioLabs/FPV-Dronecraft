package com.tenicana.dronecraft.client.control;

import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_JOYSTICK;
import static org.lwjgl.sdl.SDLInit.SDL_InitSubSystem;
import static org.lwjgl.sdl.SDLInit.SDL_IsMainThread;
import static org.lwjgl.sdl.SDLInit.SDL_QuitSubSystem;
import static org.lwjgl.sdl.SDLInit.SDL_WasInit;
import static org.lwjgl.sdl.SDLJoystick.SDL_CloseJoystick;
import static org.lwjgl.sdl.SDLJoystick.SDL_GetJoysticks;
import static org.lwjgl.sdl.SDLJoystick.SDL_GetJoystickAxis;
import static org.lwjgl.sdl.SDLJoystick.SDL_GetJoystickButton;
import static org.lwjgl.sdl.SDLJoystick.SDL_GetJoystickGUID;
import static org.lwjgl.sdl.SDLJoystick.SDL_GetJoystickName;
import static org.lwjgl.sdl.SDLJoystick.SDL_GetJoystickNameForID;
import static org.lwjgl.sdl.SDLJoystick.SDL_GetJoystickPathForID;
import static org.lwjgl.sdl.SDLJoystick.SDL_GetJoystickSerial;
import static org.lwjgl.sdl.SDLJoystick.SDL_GetNumJoystickAxes;
import static org.lwjgl.sdl.SDLJoystick.SDL_GetNumJoystickButtons;
import static org.lwjgl.sdl.SDLJoystick.SDL_JoystickConnected;
import static org.lwjgl.sdl.SDLJoystick.SDL_OpenJoystick;
import static org.lwjgl.sdl.SDLJoystick.SDL_UpdateJoysticks;
import static org.lwjgl.sdl.SDLStdinc.SDL_free;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.lwjgl.sdl.SDL_GUID;
import org.lwjgl.system.MemoryStack;

/** SDL3 joystick backend for the Minecraft 26.3 SDL input stack. */
public final class SdlJoystickProvider implements JoystickProvider, AutoCloseable {
	public static final SdlJoystickProvider INSTANCE = new SdlJoystickProvider();

	private static final Logger LOGGER = Logger.getLogger(SdlJoystickProvider.class.getName());
	private static final int MAX_LOGICAL_SLOTS = 16;
	private static final int MAX_AXES = 64;
	private static final int MAX_BUTTONS = 256;
	private static final String NO_ERROR = "SDL did not provide an error description";

	private final Map<Integer, OpenDevice> openDevices = new LinkedHashMap<>();
	private final SdlJoystickSlots slots = new SdlJoystickSlots(MAX_LOGICAL_SLOTS);
	private volatile String diagnosticReason = "Joystick input has not been initialized";
	private boolean initialized;
	private boolean ownsJoystickSubsystem;
	private boolean closed;

	private SdlJoystickProvider() {
	}

	@Override
	public synchronized List<JoystickSnapshot> snapshots() {
		if (!onMainThread("enumerate controllers")) {
			return List.of();
		}

		try {
			if (!initialize()) {
				return List.of();
			}
			SDL_UpdateJoysticks();
			IntBuffer deviceIds = SDL_GetJoysticks();
			Set<Integer> connectedIds = new HashSet<>();
			List<Integer> orderedIds = new ArrayList<>();
			if (deviceIds != null) {
				try {
					for (int i = 0; i < deviceIds.limit(); i++) {
						int deviceId = deviceIds.get(i);
						if (connectedIds.add(deviceId)) {
							orderedIds.add(deviceId);
						}
					}
				} finally {
					SDL_free(deviceIds);
				}
			}

			closeDisconnected(connectedIds);
			diagnosticReason = "";
			for (int deviceId : orderedIds) {
				if (!openDevices.containsKey(deviceId)) {
					openDevice(deviceId);
				}
			}

			List<JoystickSnapshot> snapshots = new ArrayList<>(openDevices.size());
			for (OpenDevice device : openDevices.values()) {
				if (!SDL_JoystickConnected(device.handle)) {
					continue;
				}
				snapshots.add(readSnapshot(device));
			}
			if (snapshots.isEmpty() && diagnosticReason.isBlank()) {
				diagnosticReason = "No SDL joystick devices are connected";
			}
			return List.copyOf(snapshots);
		} catch (RuntimeException | LinkageError exception) {
			warn("SDL joystick query failed: " + exceptionMessage(exception));
			return List.of();
		}
	}

	/** Human-readable reason for an empty provider result; empty when snapshots are available. */
	public String diagnosticReason() {
		return diagnosticReason;
	}

	@Override
	public synchronized void close() {
		if (!onMainThread("close controller input")) {
			return;
		}
		closeAllDevices();
		if (ownsJoystickSubsystem) {
			SDL_QuitSubSystem(SDL_INIT_JOYSTICK);
			ownsJoystickSubsystem = false;
		}
		initialized = false;
		closed = true;
		diagnosticReason = "Joystick input is closed";
	}

	private boolean initialize() {
		if (initialized) {
			if (closed) {
				diagnosticReason = "Joystick input is closed";
				return false;
			}
			return true;
		}
		if (closed) {
			diagnosticReason = "Joystick input is closed";
			return false;
		}

		int activeSubsystems = SDL_WasInit(SDL_INIT_JOYSTICK);
		if ((activeSubsystems & SDL_INIT_JOYSTICK) == 0) {
			if (!SDL_InitSubSystem(SDL_INIT_JOYSTICK)) {
				warn("SDL joystick subsystem initialization failed: " + sdlError());
				return false;
			}
			ownsJoystickSubsystem = true;
		}
		initialized = true;
		diagnosticReason = "No SDL joystick devices are connected";
		return true;
	}

	private void openDevice(int deviceId) {
		long handle = SDL_OpenJoystick(deviceId);
		if (handle == 0L) {
			warn("Could not open SDL joystick " + deviceId + ": " + sdlError());
			return;
		}

		int slot = -1;
		boolean retained = false;
		try {
			String name = safeText(SDL_GetJoystickName(handle), SDL_GetJoystickNameForID(deviceId));
			String guid = guidString(handle);
			String serial = safeText(SDL_GetJoystickSerial(handle), "");
			String path = safeText(SDL_GetJoystickPathForID(deviceId), "");
			String identity = deviceIdentity(guid, serial, path, name);
			slot = slots.acquire(identity);
			if (slot < 0) {
				warn("SDL joystick " + deviceId + " exceeds the " + MAX_LOGICAL_SLOTS + " simultaneously connected controller slots");
				return;
			}
			openDevices.put(deviceId, new OpenDevice(deviceId, slot, handle, name, guid));
			retained = true;
		} catch (RuntimeException | LinkageError exception) {
			warn("SDL could not inspect joystick " + deviceId + ": " + exceptionMessage(exception));
		} finally {
			if (!retained) {
				if (slot >= 0) slots.release(slot);
				SDL_CloseJoystick(handle);
			}
		}
	}

	private JoystickSnapshot readSnapshot(OpenDevice device) {
		int axisCount = boundedCount(SDL_GetNumJoystickAxes(device.handle), MAX_AXES, "axis", device.deviceId);
		int buttonCount = boundedCount(SDL_GetNumJoystickButtons(device.handle), MAX_BUTTONS, "button", device.deviceId);
		float[] axes = new float[axisCount];
		byte[] buttons = new byte[buttonCount];
		for (int axis = 0; axis < axisCount; axis++) {
			axes[axis] = normalize(SDL_GetJoystickAxis(device.handle, axis));
		}
		for (int button = 0; button < buttonCount; button++) {
			buttons[button] = (byte) (SDL_GetJoystickButton(device.handle, button) ? 1 : 0);
		}
		return new JoystickSnapshot(device.logicalSlot, device.name, device.guid, axes, buttons);
	}

	private int boundedCount(int count, int maximum, String kind, int deviceId) {
		if (count < 0 || count > maximum) {
			String reason = "SDL joystick " + deviceId + " reported invalid " + kind + " count " + count + " (limit " + maximum + ")";
			warn(reason);
			return Math.max(0, Math.min(count, maximum));
		}
		return count;
	}

	private static float normalize(short value) {
		return value < 0 ? value / 32768.0f : value / 32767.0f;
	}

	private static String guidString(long handle) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			SDL_GUID guid = SDL_GUID.malloc(stack);
			SDL_GetJoystickGUID(handle, guid);
			ByteBuffer bytes = guid.data();
			StringBuilder result = new StringBuilder(32);
			for (int i = 0; i < 16; i++) {
				result.append(String.format(java.util.Locale.ROOT, "%02x", bytes.get(i) & 0xff));
			}
			return result.toString();
		}
	}

	private static String deviceIdentity(String guid, String serial, String path, String name) {
		String unique = !serial.isBlank() ? "serial:" + serial : !path.isBlank() ? "path:" + path : "name:" + name;
		return guid + "|" + unique;
	}

	private void closeDisconnected(Set<Integer> connectedIds) {
		for (Integer deviceId : new ArrayList<>(openDevices.keySet())) {
			if (!connectedIds.contains(deviceId)) {
				OpenDevice removed = openDevices.remove(deviceId);
				if (removed != null) {
					closeDevice(removed);
				}
			}
		}
	}

	private void closeAllDevices() {
		for (OpenDevice device : openDevices.values()) {
			closeDevice(device);
		}
		openDevices.clear();
	}

	private void closeDevice(OpenDevice device) {
		try {
			SDL_CloseJoystick(device.handle);
		} finally {
			slots.release(device.logicalSlot);
		}
	}

	private boolean onMainThread(String operation) {
		try {
			if (SDL_IsMainThread()) {
				return true;
			}
		} catch (RuntimeException | LinkageError exception) {
			warn("Cannot verify SDL main-thread ownership for " + operation + ": " + exceptionMessage(exception));
			return false;
		}
		warn("Cannot " + operation + " off SDL's main thread; keyboard flight input remains available");
		return false;
	}

	private void warn(String message) {
		if (!message.equals(diagnosticReason)) {
			diagnosticReason = message;
			LOGGER.warning(message);
		}
	}

	private static String sdlError() {
		return safeText(SDL_GetError(), NO_ERROR);
	}

	private static String exceptionMessage(Throwable exception) {
		String message = exception.getMessage();
		return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
	}

	private static String safeText(String preferred, String fallback) {
		if (preferred == null || preferred.isBlank()) {
			return fallback == null ? "" : fallback;
		}
		return preferred.trim();
	}

	private record OpenDevice(int deviceId, int logicalSlot, long handle, String name, String guid) {
	}
}
