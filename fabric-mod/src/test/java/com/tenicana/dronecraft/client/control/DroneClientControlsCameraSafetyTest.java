package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DroneClientControlsCameraSafetyTest {
	@Test
	void fpvToggleOffUsesUnifiedCameraRestorePath() throws IOException {
		String source = Files.readString(droneClientControlsSource(), StandardCharsets.UTF_8);
		String fpvToggle = source.substring(
				source.indexOf("while (FPV_VIEW.consumeClick())"),
				source.indexOf("while (HUD_TOGGLE.consumeClick())")
		);

		assertTrue(fpvToggle.contains("disableFpvView(client, true);"), "FPV toggle-off must restore the local camera immediately");
		assertTrue(!fpvToggle.contains("DroneClientState.setFpvViewEnabled(fpvEnabled);"), "FPV toggle must not bypass the unified restore path");
	}

	@Test
	void clientTickSweepsDroneCameraWhenFpvIsNoLongerActive() throws IOException {
		String source = Files.readString(droneClientControlsSource(), StandardCharsets.UTF_8);
		String restoreInvalidDroneCamera = source.substring(
				source.indexOf("private static void restoreInvalidDroneCamera"),
				source.indexOf("private static UUID controlledDroneId")
		);

		assertTrue(
				source.contains("restoreInvalidDroneCamera(client);"),
				"client tick must sweep stale DroneEntity camera targets"
		);
		assertTrue(
				restoreInvalidDroneCamera.contains("client.getCameraEntity() instanceof DroneEntity && !DroneClientState.isFpvActive(client.level)"),
				"client camera sweep must only restore DroneEntity camera targets when FPV is no longer active"
		);
		assertTrue(
				restoreInvalidDroneCamera.contains("restoreVanillaCamera(client);"),
				"stale DroneEntity cameras must restore the player camera and clear hit state"
		);
	}

	@Test
	void restoreVanillaCameraClearsCameraAndHitTargets() throws IOException {
		String source = Files.readString(droneClientControlsSource(), StandardCharsets.UTF_8);
		String restoreVanillaCamera = source.substring(
				source.indexOf("private static void restoreVanillaCamera"),
				source.indexOf("private static void restoreInvalidDroneCamera")
		);

		assertTrue(restoreVanillaCamera.contains("client.setCameraEntity(client.player)"), "vanilla camera restore must point cameraEntity back at the player");
		assertTrue(restoreVanillaCamera.contains("fpvdrone$setCrosshairPickEntity(null)"), "vanilla camera restore must clear stale crosshair entity picks");
		assertTrue(restoreVanillaCamera.contains("fpvdrone$setHitResult(null)"), "vanilla camera restore must clear stale hit results");
	}

	private static Path droneClientControlsSource() {
		Path current = Path.of("").toAbsolutePath();
		for (int i = 0; i < 8 && current != null; i++) {
			Path direct = current.resolve("src/client/java/com/tenicana/dronecraft/client/control/DroneClientControls.java");
			if (Files.exists(direct)) {
				return direct;
			}
			Path child = current.resolve("fabric-mod/src/client/java/com/tenicana/dronecraft/client/control/DroneClientControls.java");
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		fail("Cannot locate DroneClientControls.java");
		return Path.of("DroneClientControls.java");
	}
}
