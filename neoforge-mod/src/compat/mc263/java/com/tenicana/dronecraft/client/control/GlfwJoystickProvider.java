package com.tenicana.dronecraft.client.control;

import java.util.List;

/** Compatibility facade for call sites that have not adopted the SDL provider name. */
public final class GlfwJoystickProvider implements JoystickProvider, AutoCloseable {
	public static final GlfwJoystickProvider INSTANCE = new GlfwJoystickProvider();

	private GlfwJoystickProvider() {
	}

	@Override
	public List<JoystickSnapshot> snapshots() {
		return SdlJoystickProvider.INSTANCE.snapshots();
	}

	public String diagnosticReason() {
		return SdlJoystickProvider.INSTANCE.diagnosticReason();
	}

	@Override
	public void close() {
		SdlJoystickProvider.INSTANCE.close();
	}
}
