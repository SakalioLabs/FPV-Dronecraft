package com.tenicana.dronecraft.network;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DroneNetworkingCameraSafetyTest {
	@Test
	void fpvViewPacketsAlwaysRestorePlayerWhenFpvIsDisabledOrNoDroneIsValid() throws IOException {
		String source = Files.readString(droneNetworkingSource(), StandardCharsets.UTF_8);
		String updateFpvCamera = source.substring(
				source.indexOf("private static void updateFpvCamera"),
				source.indexOf("private static DroneEntity fpvCameraDrone")
		);

		assertTrue(updateFpvCamera.contains("if (!fpvView)"), "FPV-off packets must have an explicit restore branch");
		assertTrue(updateFpvCamera.contains("restorePlayerCamera(player);"), "FPV-off packets must restore the player camera");
		assertTrue(updateFpvCamera.contains("if (drone == null)"), "FPV-on packets with no valid drone must be handled");
		assertTrue(updateFpvCamera.contains("restorePlayerCamera(player);"), "missing valid FPV drones must restore the player camera");
	}

	@Test
	void serverRestoresDroneCameraOnDisconnectAndInvalidCameraTicks() throws IOException {
		String source = Files.readString(droneNetworkingSource(), StandardCharsets.UTF_8);

		assertTrue(
				source.contains("ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> restorePlayerCamera(handler.player));"),
				"server disconnects must restore the player's camera even when the client cannot send an FPV-off packet"
		);
		assertTrue(
				source.contains("ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(DroneNetworking::restoreInvalidDroneCamera));"),
				"server ticks must sweep invalid DroneEntity camera targets"
		);

		String restoreInvalidDroneCamera = source.substring(
				source.indexOf("private static void restoreInvalidDroneCamera"),
				source.indexOf("private static boolean isValidFpvCameraDrone")
		);
		assertTrue(
				restoreInvalidDroneCamera.contains("camera instanceof DroneEntity drone && !isValidFpvCameraDrone(player, drone)"),
				"invalid-camera sweep must only target DroneEntity cameras that fail the FPV validity check"
		);
		assertTrue(
				restoreInvalidDroneCamera.contains("restorePlayerCamera(player);"),
				"invalid DroneEntity cameras must be reset to the player"
		);
	}

	@Test
	void fpvCameraValidityRejectsStaleRemovedCrossWorldOrUnownedDrones() throws IOException {
		String source = Files.readString(droneNetworkingSource(), StandardCharsets.UTF_8);
		String validity = source.substring(
				source.indexOf("private static boolean isValidFpvCameraDrone"),
				source.indexOf("private static boolean bindNearestDroneIfNeeded")
		);

		assertTrue(validity.contains("drone.isAlive()"), "dead drones must not remain FPV camera targets");
		assertTrue(validity.contains("!drone.isRemoved()"), "removed drones must not remain FPV camera targets");
		assertTrue(validity.contains("drone.level() == player.level()"), "cross-world drones must not remain FPV camera targets");
		assertTrue(validity.contains("drone.isOwnedBy(player.getUUID())"), "unowned or other-player drones must not remain FPV camera targets");
	}

	private static Path droneNetworkingSource() {
		Path current = Path.of("").toAbsolutePath();
		for (int i = 0; i < 8 && current != null; i++) {
			Path direct = current.resolve("src/main/java/com/tenicana/dronecraft/network/DroneNetworking.java");
			if (Files.exists(direct)) {
				return direct;
			}
			Path child = current.resolve("fabric-mod/src/main/java/com/tenicana/dronecraft/network/DroneNetworking.java");
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		fail("Cannot locate DroneNetworking.java");
		return Path.of("DroneNetworking.java");
	}
}
