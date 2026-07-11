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
		String clientSource = Files.readString(fpvDronecraftClientSource(), StandardCharsets.UTF_8);

		assertTrue(
				clientSource.contains("ClientPlayerNetworkEvent.LoggingIn")
						&& clientSource.contains("DroneClientControls.onLoggingIn("),
				"NeoForge client login events must delegate runtime-state reset to DroneClientControls"
		);
		assertTrue(
				clientSource.contains("ClientPlayerNetworkEvent.LoggingOut")
						&& clientSource.contains("DroneClientControls.onLoggingOut("),
				"NeoForge client logout events must delegate runtime-state reset to DroneClientControls"
		);
		assertTrue(
				clientSource.contains("LevelEvent.Unload")
						&& clientSource.contains("DroneClientControls.onLevelUnload("),
				"NeoForge client level unload events must delegate cross-world camera reset"
		);
		assertTrue(
				clientSource.contains("EntityLeaveLevelEvent")
						&& clientSource.contains("DroneClientControls.onEntityLeaveLevel("),
				"NeoForge entity unload events must delegate controlled-drone cleanup"
		);
		assertTrue(
				clientSource.contains("ClientStoppingEvent")
						&& clientSource.contains("DroneClientControls.onClientStopping("),
				"NeoForge client stopping events must provide a last-chance camera reset boundary"
		);
		assertTrue(
				countOccurrences(clientSource, "event.getLevel() == client.level") == 2,
				"level and entity unload handlers must ignore events from levels other than the active client level"
		);
		assertTrue(
				source.contains("if (client.player == null || client.level == null)"),
				"client tick must reset and stop before dereferencing an absent client player or level"
		);
		assertTrue(
				source.contains("if (client == null || entity == null)"),
				"entity unload delegation must tolerate unavailable client or entity state"
		);
		assertTrue(
				source.contains("entity == DroneClientState.controlledDrone() || client.getCameraEntity() == entity"),
				"controlled-drone unload or direct camera-entity unload must trigger the reset"
		);
		assertTrue(
				source.contains("disableFpvView(client, true, true);")
						&& source.contains("resetClientRuntimeState(client);"),
				"controlled-drone unload must notify the server when possible and clear stale client state"
		);
	}

	@Test
	void fpvViewPacketsAreOnlySentWhenNeoForgeConnectionHasThePayloadChannel() throws IOException {
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
				sendControlInput.contains("ClientPacketListener connection = Minecraft.getInstance().getConnection();")
						&& sendControlInput.contains("connection.hasChannel(DroneControlPayload.TYPE)"),
				"control payload sends must require an active NeoForge connection with the negotiated payload channel"
		);
		assertTrue(
				sendFpvViewState.contains("ClientPacketListener connection = Minecraft.getInstance().getConnection();")
						&& sendFpvViewState.contains("connection.hasChannel(DroneViewPayload.TYPE)"),
				"FPV recovery packets must require an active NeoForge connection with the negotiated payload channel"
		);
		assertTrue(
				sendControlInput.contains("return;") && sendFpvViewState.contains("return;"),
				"missing connections or channels must skip packets while allowing local recovery to complete"
		);
		assertTrue(
				sendControlInput.contains("ClientPacketDistributor.sendToServer("),
				"control input must use NeoForge's client packet distributor"
		);
		assertTrue(
				sendFpvViewState.contains("ClientPacketDistributor.sendToServer(new DroneViewPayload(fpvView));"),
				"sendable play connections must receive the explicit FPV state through NeoForge"
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

	private static int countOccurrences(String source, String needle) {
		int count = 0;
		int offset = 0;
		while ((offset = source.indexOf(needle, offset)) >= 0) {
			count++;
			offset += needle.length();
		}
		return count;
	}

	private static Path droneClientControlsSource() {
		return locateSource("com/tenicana/dronecraft/client/control/DroneClientControls.java");
	}

	private static Path fpvDronecraftClientSource() {
		return locateSource("com/tenicana/dronecraft/client/FpvDronecraftClient.java");
	}

	private static Path locateSource(String relativePath) {
		Path current = Path.of("").toAbsolutePath();
		for (int i = 0; i < 8 && current != null; i++) {
			Path direct = current.resolve("src/main/java").resolve(relativePath);
			if (Files.exists(direct)) {
				return direct;
			}
			Path child = current.resolve("neoforge-mod/src/main/java").resolve(relativePath);
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		fail("Cannot locate " + relativePath);
		return Path.of(relativePath);
	}
}
