package com.tenicana.dronecraft.client.control;

import java.util.Arrays;

import com.tenicana.dronecraft.client.config.DroneClientConfig;

public record JoystickSnapshot(int glfwId, String name, String guid, float[] axes, byte[] buttons) {
	private static final byte BUTTON_PRESSED = 1;

	public JoystickSnapshot {
		name = safeText(name, "Controller " + glfwId);
		guid = safeText(guid, "");
		axes = axes == null ? new float[0] : Arrays.copyOf(axes, axes.length);
		buttons = buttons == null ? new byte[0] : Arrays.copyOf(buttons, buttons.length);
	}

	@Override
	public float[] axes() {
		return Arrays.copyOf(axes, axes.length);
	}

	@Override
	public byte[] buttons() {
		return Arrays.copyOf(buttons, buttons.length);
	}

	public float axis(int axis) {
		if (axis < 0 || axis >= axes.length) {
			return 0.0f;
		}
		float value = axes[axis];
		return Float.isFinite(value) ? value : 0.0f;
	}

	public boolean buttonPressed(int button) {
		return button >= 0 && button < buttons.length && buttons[button] == BUTTON_PRESSED;
	}

	public boolean hasRequiredAxes(DroneClientConfig config) {
		DroneClientConfig safeConfig = config == null ? DroneClientConfig.defaults() : config;
		return safeConfig.hasRequiredAxes(axes.length) && hasFiniteAxes();
	}

	public boolean hasAnyAxisActivity(float threshold) {
		float safeThreshold = Float.isFinite(threshold) ? Math.max(0.0f, threshold) : 0.05f;
		for (float axis : axes) {
			if (Float.isFinite(axis) && Math.abs(axis) > safeThreshold) {
				return true;
			}
		}
		return false;
	}

	private boolean hasFiniteAxes() {
		for (float axis : axes) {
			if (!Float.isFinite(axis)) {
				return false;
			}
		}
		return true;
	}

	private static String safeText(String text, String fallback) {
		if (text == null || text.isBlank()) {
			return fallback;
		}
		return text.trim();
	}
}
