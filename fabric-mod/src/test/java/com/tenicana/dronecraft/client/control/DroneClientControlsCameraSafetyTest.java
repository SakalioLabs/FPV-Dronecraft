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
	void lifecycleBoundariesResetFpvStateAndControlledDroneReferences() throws IOException {
		String source = Files.readString(droneClientControlsSource(), StandardCharsets.UTF_8);
		String initialize = source.substring(
				source.indexOf("public static void initialize()"),
				source.indexOf("private static void sendFpvViewState")
		);

		assertTrue(
				source.contains("import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;"),
				"client world-change events must be available as an explicit cross-world camera reset boundary"
		);
		assertTrue(
				source.contains("import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;"),
				"client stopping must be available as a last-chance camera reset boundary"
		);
		assertTrue(
				source.contains("import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;"),
				"controlled-drone unloads must be handled before stale entity references can survive"
		);
		assertTrue(
				initialize.contains("ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> resetClientRuntimeState(client));"),
				"changing to a new client world must clear transient FPV state before the new world starts ticking"
		);
		assertTrue(
				initialize.contains("ClientLifecycleEvents.CLIENT_STOPPING.register(DroneClientControls::resetClientRuntimeState);"),
				"closing the client while in FPV must restore the local camera state"
		);
		assertTrue(
				initialize.contains("ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {"),
				"the controlled drone unloading from the client world must be observed directly"
		);
		assertTrue(
				initialize.contains("if (entity == DroneClientState.controlledDrone() || client.getCameraEntity() == entity)"),
				"controlled-drone unload or direct camera-entity unload must trigger the reset"
		);
		assertTrue(
				initialize.contains("disableFpvView(client, true, true);"),
				"controlled-drone unload must force an FPV-off packet when the play connection is still sendable"
		);
		assertTrue(
				initialize.contains("resetClientRuntimeState(client);"),
				"controlled-drone unload must clear stale controlled-drone and input state"
		);
	}

	@Test
	void fpvViewPacketsAreOnlySentWhenPlayNetworkingCanSend() throws IOException {
		String source = Files.readString(droneClientControlsSource(), StandardCharsets.UTF_8);
		String sendControlInput = source.substring(
				source.indexOf("private static void sendControlInput"),
				source.indexOf("private static void sendFpvViewState")
		);
		String sendFpvViewState = source.substring(
				source.indexOf("private static void sendFpvViewState"),
				source.indexOf("private static void disableFpvView")
		);

		assertTrue(
				sendControlInput.contains("ClientPlayNetworking.canSend(DroneControlPayload.TYPE)"),
				"control payload sends must also be skipped after the play connection is no longer sendable"
		);
		assertTrue(
				sendFpvViewState.contains("ClientPlayNetworking.canSend(DroneViewPayload.TYPE)"),
				"FPV-off recovery must not throw if a lifecycle reset runs after the play connection is gone"
		);
		assertTrue(
				sendFpvViewState.contains("return;"),
				"non-sendable lifecycle windows must skip the packet and still complete local recovery"
		);
		assertTrue(
				sendFpvViewState.contains("ClientPlayNetworking.send(new DroneViewPayload(fpvView));"),
				"sendable play connections must still receive the explicit FPV state packet"
		);
	}

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
	void lostDroneAfterRefreshStillSendsFpvOffPacket() throws IOException {
		String source = Files.readString(droneClientControlsSource(), StandardCharsets.UTF_8);
		String lostDroneBranch = source.substring(
				source.indexOf("boolean fpvRequestedBeforeRefresh"),
				source.indexOf("while (VIRTUAL_CONTROLLER.consumeClick())")
		);
		String forcedDisable = source.substring(
				source.indexOf("private static void disableFpvView(Minecraft client, boolean notifyServer, boolean forceNotifyServer)"),
				source.indexOf("public static DroneClientConfig config()")
		);

		assertTrue(
				lostDroneBranch.contains("disableFpvView(client, true, true);"),
				"if refreshControlledDrone clears the FPV flag after losing a drone, the client must still force an FPV-off packet"
		);
		assertTrue(
				forcedDisable.contains("if (notifyServer && (wasEnabled || forceNotifyServer))"),
				"forced FPV disable must notify the server even when local state was already cleared"
		);
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
				source.indexOf("private static void clearStaleHitTargets")
		);
		String clearStaleHitTargets = source.substring(
				source.indexOf("private static void clearStaleHitTargets"),
				source.indexOf("private static void restoreInvalidDroneCamera")
		);

		assertTrue(restoreVanillaCamera.contains("clearStaleHitTargets(client);"), "vanilla camera restore must clear stale hit targets before player-dependent camera restore");
		assertTrue(restoreVanillaCamera.indexOf("clearStaleHitTargets(client);") < restoreVanillaCamera.indexOf("if (client.player == null)"), "hit targets must still be cleared when client.player is null during disconnect");
		assertTrue(restoreVanillaCamera.contains("client.setCameraEntity(client.player)"), "vanilla camera restore must point cameraEntity back at the player");
		assertTrue(clearStaleHitTargets.contains("fpvdrone$setCrosshairPickEntity(null)"), "vanilla camera restore must clear stale crosshair entity picks");
		assertTrue(clearStaleHitTargets.contains("fpvdrone$setHitResult(null)"), "vanilla camera restore must clear stale hit results");
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
