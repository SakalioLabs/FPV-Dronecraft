package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceDiagnosticGate;

/** Pre-launch mapping for the default-off native callback trace mode. */
public final class MinecraftNativeEventTraceDiagnosticMode {
	private MinecraftNativeEventTraceDiagnosticMode() {
	}

	public static NativeEventTraceDiagnosticGate.Decision evaluate() {
		boolean requested =
				MinecraftAcousticWorldDirtyTracker.nativeEventTraceEnabled();
		return NativeEventTraceDiagnosticGate.evaluate(
				requested,
				requested,
				System.getenv("ALSOFT_DRIVERS"),
				Boolean.getBoolean(
						MinecraftLocalPlaneSceneScheduler.ENABLE_PROPERTY
				),
				true,
				true
		);
	}
}
