package com.tenicana.dronecraft.client.sound;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftNativeEventTraceDiagnosticModeContractTest {
	@Test
	void launchGateRequiresExclusiveNullBackend() throws IOException {
		String source = Files.readString(
				locate(
						"src/client/java/com/tenicana/dronecraft/client/"
								+ "sound/"
								+ "MinecraftNativeEventTraceDiagnosticMode.java",
						"fabric-mod/src/client/java/com/tenicana/dronecraft/"
								+ "client/sound/"
								+ "MinecraftNativeEventTraceDiagnosticMode.java"
				),
				StandardCharsets.UTF_8
		);
		assertTrue(source.contains("System.getenv(\"ALSOFT_DRIVERS\")"));
		assertTrue(source.contains(
				"MinecraftLocalPlaneSceneScheduler.ENABLE_PROPERTY"
		));
		assertTrue(source.contains(
				"NativeEventTraceDiagnosticGate.evaluate("
		));
	}

	@Test
	void armedModeSuppressesSoundManagerAndGeneralCommands()
			throws IOException {
		String source = Files.readString(
				locate(
						"src/client/java/com/tenicana/dronecraft/client/"
								+ "FpvDronecraftClient.java",
						"fabric-mod/src/client/java/com/tenicana/dronecraft/"
								+ "client/FpvDronecraftClient.java"
				),
				StandardCharsets.UTF_8
		);
		int armed = source.indexOf("if (traceMode.armed())");
		int fallback = source.indexOf("} else {", armed);
		int commands = source.indexOf(
				"AcousticDiagnosticClientCommands.initialize()"
		);
		int manager = source.indexOf("DroneSoundManager.initialize()");
		assertTrue(source.contains(
				"NativeEventTraceDiagnosticGate.State.REJECTED"
		));
		assertTrue(armed >= 0);
		assertTrue(fallback > armed);
		assertTrue(commands > fallback);
		assertTrue(manager > fallback);
	}

	private static Path locate(String local, String root) {
		Path localPath = Path.of(local);
		return Files.isRegularFile(localPath) ? localPath : Path.of(root);
	}
}
