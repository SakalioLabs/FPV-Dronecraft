package com.tenicana.dronecraft.client.sound;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftNativeEventTraceEvidenceContractTest {
	@Test
	void alcSnapshotIsReadOnlyAndRunsOnSoundThread()
			throws IOException {
		String source = read(
				"src/client/java/com/tenicana/dronecraft/client/sound/"
						+ "MinecraftNativeEventTraceEvidence.java",
				"fabric-mod/src/client/java/com/tenicana/dronecraft/"
						+ "client/sound/"
						+ "MinecraftNativeEventTraceEvidence.java"
		);
		assertTrue(source.contains("fpvdrone$getExecutor()"));
		assertTrue(source.contains("executor.schedule(() ->"));
		assertTrue(source.contains("ALC10.alcGetCurrentContext()"));
		assertTrue(source.contains("ALC10.alcGetContextsDevice(context)"));
		assertTrue(source.contains("ALC10.ALC_DEVICE_SPECIFIER"));
		assertTrue(source.contains("ALC11.ALC_CAPTURE_DEVICE_SPECIFIER"));
		assertTrue(source.contains("thenApplyAsync(alc ->"));
		assertTrue(source.contains("NativeEventTraceEvidenceExporter.write("));
		assertFalse(source.contains("alcOpenDevice"));
		assertFalse(source.contains("alcCaptureOpenDevice"));
		assertFalse(source.contains("DroneSoundManager"));
	}

	@Test
	void onlyArmedBranchRegistersDedicatedCommand()
			throws IOException {
		String initializer = read(
				"src/client/java/com/tenicana/dronecraft/client/"
						+ "FpvDronecraftClient.java",
				"fabric-mod/src/client/java/com/tenicana/dronecraft/"
						+ "client/FpvDronecraftClient.java"
		);
		String command = read(
				"src/client/java/com/tenicana/dronecraft/client/command/"
						+ "NativeEventTraceDiagnosticClientCommands.java",
				"fabric-mod/src/client/java/com/tenicana/dronecraft/"
						+ "client/command/"
						+ "NativeEventTraceDiagnosticClientCommands.java"
		);
		int armed = initializer.indexOf("if (traceMode.armed())");
		int dedicated = initializer.indexOf(
				"NativeEventTraceDiagnosticClientCommands.initialize()"
		);
		int fallback = initializer.indexOf("} else {", armed);
		assertTrue(armed >= 0 && dedicated > armed && dedicated < fallback);
		assertTrue(command.contains(
				"MinecraftNativeEventTraceEvidence.export("
		));
		assertFalse(command.contains("DroneSoundManager"));
	}

	private static String read(String local, String root)
			throws IOException {
		Path localPath = Path.of(local);
		return Files.readString(
				Files.isRegularFile(localPath)
						? localPath
						: Path.of(root),
				StandardCharsets.UTF_8
		);
	}
}
