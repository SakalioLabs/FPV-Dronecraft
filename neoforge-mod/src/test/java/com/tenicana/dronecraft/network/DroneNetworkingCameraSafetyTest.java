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
	void payloadsRegisterThroughNeoForgePayloadEvent() throws IOException {
		Path sourcePath = droneNetworkingSource();
		String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
		String modSource = Files.readString(sourcePath.getParent().getParent().resolve("FpvDronecraftMod.java"), StandardCharsets.UTF_8);
		String registration = source.substring(
				source.indexOf("public static void registerPayloadHandlers"),
				source.indexOf("public static void initialize")
		);

		assertTrue(
				source.contains("import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;"),
				"payload registration must use NeoForge's mod-bus registration event"
		);
		assertTrue(
				registration.contains("registerPayloadHandlers(RegisterPayloadHandlersEvent event)"),
				"the payload registration entry point must accept RegisterPayloadHandlersEvent"
		);
		assertTrue(
				registration.contains("event.registrar(\"1\").optional()"),
				"the NeoForge registrar must use the versioned optional channel"
		);
		assertTrue(
				registration.contains("registrar.playToServer(DroneControlPayload.TYPE, DroneControlPayload.CODEC, DroneNetworking::handleControl);"),
				"control payloads must be decoded and handled on the server"
		);
		assertTrue(
				registration.contains("registrar.playToServer(DroneViewPayload.TYPE, DroneViewPayload.CODEC, DroneNetworking::handleView);"),
				"FPV view payloads must be decoded and handled on the server"
		);
		assertTrue(
				modSource.contains("modEventBus.addListener(DroneNetworking::registerPayloadHandlers);"),
				"the mod entry point must subscribe payload registration to the mod event bus"
		);
		assertTrue(
				modSource.contains("DroneNetworking.initialize();"),
				"the mod entry point must install server lifecycle listeners"
		);
	}

	@Test
	void serverRestoresPlayerCameraOnPlayerLifecycleBoundaries() throws IOException {
		String source = Files.readString(droneNetworkingSource(), StandardCharsets.UTF_8);

		assertTrue(
				source.contains("import net.neoforged.neoforge.common.NeoForge;"),
				"server lifecycle listeners must be registered on NeoForge's event bus"
		);
		assertTrue(
				source.contains("import net.neoforged.neoforge.event.entity.player.PlayerEvent;"),
				"NeoForge player events must provide login, logout, clone, and dimension reset boundaries"
		);
		assertTrue(
				source.contains("import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;"),
				"NeoForge entity-leave events must provide an immediate stale-camera boundary"
		);
		assertTrue(
				source.contains("NeoForge.EVENT_BUS.addListener(DroneNetworking::onPlayerLoggedIn);"),
				"joining a world must force the server camera back to the player"
		);
		assertTrue(
				source.contains("NeoForge.EVENT_BUS.addListener(DroneNetworking::onPlayerLoggedOut);"),
				"leaving a world must restore the server camera before player data is saved"
		);
		assertTrue(
				source.contains("NeoForge.EVENT_BUS.addListener(DroneNetworking::onPlayerClone);"),
				"respawn must be an explicit camera reset boundary"
		);
		assertTrue(
				source.contains("restorePlayerCamera(originalPlayer);") && source.contains("restorePlayerCamera(newPlayer);"),
				"respawn must restore both old and new player camera references"
		);
		assertTrue(
				source.contains("NeoForge.EVENT_BUS.addListener(DroneNetworking::onPlayerChangedDimension);"),
				"changing server worlds must restore the player camera immediately instead of waiting for a later tick sweep"
		);
		assertTrue(
				source.contains("NeoForge.EVENT_BUS.addListener(DroneNetworking::onEntityLeaveLevel);"),
				"unloading a drone entity must immediately restore any player camera still pointing at it"
		);
		assertTrue(
				source.contains("player.getCamera() == event.getEntity()"),
				"server drone unload recovery must target players whose camera still references that exact entity"
		);

		String login = source.substring(source.indexOf("private static void onPlayerLoggedIn"), source.indexOf("private static void onPlayerLoggedOut"));
		String logout = source.substring(source.indexOf("private static void onPlayerLoggedOut"), source.indexOf("private static void onPlayerClone"));
		String dimensionChange = source.substring(source.indexOf("private static void onPlayerChangedDimension"), source.indexOf("private static void onEntityLeaveLevel"));
		assertTrue(login.contains("restorePlayerCamera(player);"), "login events must restore the player's camera");
		assertTrue(logout.contains("restorePlayerCamera(player);"), "logout events must restore the player's camera");
		assertTrue(dimensionChange.contains("restorePlayerCamera(player);"), "dimension changes must restore the player's camera");
	}

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
				source.contains("NeoForge.EVENT_BUS.addListener(DroneNetworking::onPlayerLoggedOut);"),
				"server disconnects must restore the player's camera even when the client cannot send an FPV-off packet"
		);
		assertTrue(
				source.contains("NeoForge.EVENT_BUS.addListener(DroneNetworking::onServerTick);"),
				"server ticks must sweep invalid DroneEntity camera targets"
		);
		assertTrue(
				source.contains("private static void onServerTick(ServerTickEvent.Post event)"),
				"the invalid-camera sweep must run after the server tick"
		);
		assertTrue(
				source.contains("event.getServer().getPlayerList().getPlayers().forEach(DroneNetworking::restoreInvalidDroneCamera);"),
				"the post-tick handler must validate every connected player's camera"
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
			Path child = current.resolve("neoforge-mod/src/main/java/com/tenicana/dronecraft/network/DroneNetworking.java");
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		fail("Cannot locate DroneNetworking.java");
		return Path.of("DroneNetworking.java");
	}
}
