package com.tenicana.dronecraft.client.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FpvCameraMixinSafetyTest {
	@Test
	void cameraPoseDelayIsResetAcrossWorldsAndInvalidDroneReferences() throws IOException {
		String source = Files.readString(cameraMixinSource(), StandardCharsets.UTF_8);
		String setup = source.substring(
				source.indexOf("private void fpvdrone$setupFpvCamera"),
				source.indexOf("private static void resetCameraDelay")
		);
		String reset = source.substring(
				source.indexOf("private static void resetCameraDelay"),
				source.indexOf("private static CameraShake cameraShake")
		);

		assertTrue(
				source.contains("private static Level delayedLevel;"),
				"FPV pose delay must track the world as well as the entity id"
		);
		assertTrue(
				setup.contains("ClientCameraSafety.isUsableFpvDroneReference(")
						&& setup.contains("DroneClientState.isFpvActive(level)")
						&& setup.contains("drone != null && drone.level() == level")
						&& setup.contains("drone != null && drone.isRemoved()")
						&& setup.contains("drone != null && drone.isAlive()"),
				"Camera setup must reject stale, removed, dead or cross-world controlled-drone references"
		);
		assertTrue(
				setup.contains("ClientCameraSafety.shouldResetFpvPoseDelay(drone.getId(), delayedDroneId, level, delayedLevel)"),
				"entity ids can be reused in a new world, so the camera delay cache must reset on level changes"
		);
		assertTrue(
				setup.contains("delayedLevel = level;"),
				"the active FPV level must be recorded after resetting the pose cache"
		);
		assertTrue(
				reset.contains("delayedLevel = null;"),
				"leaving FPV or seeing an invalid drone must clear the cached level"
		);
	}

	@Test
	void fovOverrideRequiresCurrentWorldDrone() throws IOException {
		String source = Files.readString(gameRendererMixinSource(), StandardCharsets.UTF_8);
		String override = source.substring(
				source.indexOf("private void fpvdrone$overrideFpvFov"),
				source.lastIndexOf("}")
		);

		assertTrue(
				override.contains("Minecraft client = Minecraft.getInstance();"),
				"FOV override should use one client snapshot for the active level check"
		);
		assertTrue(
				override.contains("DroneClientState.isFpvActive(client.level)"),
				"FOV override must be tied to active FPV in the current client level"
		);
		assertTrue(
				override.contains("ClientCameraSafety.isUsableFpvDroneReference(")
						&& override.contains("DroneClientState.isFpvActive(client.level)")
						&& override.contains("drone != null && drone.level() == client.level")
						&& override.contains("drone != null && drone.isRemoved()")
						&& override.contains("drone != null && drone.isAlive()"),
				"FOV override must not run for stale, removed, dead or cross-world drone references"
		);
	}

	private static Path cameraMixinSource() {
		return locate("src/client/java/com/tenicana/dronecraft/client/mixin/CameraMixin.java",
				"fabric-mod/src/client/java/com/tenicana/dronecraft/client/mixin/CameraMixin.java");
	}

	private static Path gameRendererMixinSource() {
		return locate("src/client/java/com/tenicana/dronecraft/client/mixin/GameRendererMixin.java",
				"fabric-mod/src/client/java/com/tenicana/dronecraft/client/mixin/GameRendererMixin.java");
	}

	private static Path locate(String directPath, String childPath) {
		Path current = Path.of("").toAbsolutePath();
		for (int i = 0; i < 8 && current != null; i++) {
			Path direct = current.resolve(directPath);
			if (Files.exists(direct)) {
				return direct;
			}
			Path child = current.resolve(childPath);
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		fail("Cannot locate " + childPath);
		return Path.of(childPath);
	}
}
