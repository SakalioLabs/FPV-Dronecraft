package com.tenicana.dronecraft.client.control;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

public final class GlfwJoystickProvider implements JoystickProvider {
	public static final GlfwJoystickProvider INSTANCE = new GlfwJoystickProvider();

	private GlfwJoystickProvider() {
	}

	@Override
	public List<JoystickSnapshot> snapshots() {
		List<JoystickSnapshot> snapshots = new ArrayList<>();
		for (int joystick = GLFW.GLFW_JOYSTICK_1; joystick <= GLFW.GLFW_JOYSTICK_LAST; joystick++) {
			if (!GLFW.glfwJoystickPresent(joystick)) {
				continue;
			}
			snapshots.add(new JoystickSnapshot(
					joystick,
					GLFW.glfwGetJoystickName(joystick),
					GLFW.glfwGetJoystickGUID(joystick),
					copyAxes(GLFW.glfwGetJoystickAxes(joystick)),
					copyButtons(GLFW.glfwGetJoystickButtons(joystick))
			));
		}
		return snapshots;
	}

	private static float[] copyAxes(FloatBuffer axes) {
		if (axes == null) {
			return new float[0];
		}
		float[] copy = new float[axes.limit()];
		for (int i = 0; i < axes.limit(); i++) {
			copy[i] = axes.get(i);
		}
		return copy;
	}

	private static byte[] copyButtons(ByteBuffer buttons) {
		if (buttons == null) {
			return new byte[0];
		}
		byte[] copy = new byte[buttons.limit()];
		for (int i = 0; i < buttons.limit(); i++) {
			copy[i] = buttons.get(i);
		}
		return copy;
	}
}
