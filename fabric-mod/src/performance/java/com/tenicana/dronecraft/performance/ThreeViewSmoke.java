package com.tenicana.dronecraft.performance;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.lwjgl.glfw.GLFW;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.tenicana.dronecraft.camera.DroneViewMode;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.client.control.DroneClientControls;
import com.tenicana.dronecraft.client.render.DroneEntityRenderState;
import com.tenicana.dronecraft.client.render.DroneEntityRenderer;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.network.DroneRemoteSessions;
import com.tenicana.dronecraft.performance.mixin.GameRendererFovAccess;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Real key mappings, renderer submissions and block collision on the normal client loop. */
public final class ThreeViewSmoke {
	public static final boolean ENABLED = Boolean.getBoolean("fpvdrone.performance.threeViewSmoke")
			|| Boolean.getBoolean("fpvdrone.performance.renderStress");
	private static final EntityDataAccessor<Float> PITCH = attitudeAccessor("PITCH");
	private static final EntityDataAccessor<Float> YAW = attitudeAccessor("YAW");
	private static final EntityDataAccessor<Float> ROLL = attitudeAccessor("ROLL");
	private static final float TILT_PITCH = (float) Math.toRadians(38);
	private static final float TILT_ROLL = (float) Math.toRadians(-47);
	private static ThreeViewSmoke active;
	private final JsonObject report = new JsonObject();
	private final JsonArray screenshots = new JsonArray();
	private final List<BlockPos> wall = new ArrayList<>();
	private UUID droneId;
	private Vec3 droneHome, playerHome, observerHome, uprightCamera, uprightForward, wallCenter;
	private Path runDirectory;
	private int stage, stageTick, submittedFrames, disconnectStage;
	private long stageStarted;
	private volatile boolean started, complete, serverPlayerCamera, serverRemoteView, serverSessionDrone;
	private volatile Vec3 serverPlayerPosition;
	private boolean restored, disconnectVerified, reopenVerified, screenshotRecorded;
	private CameraType previousCameraType;
	private boolean previousBobView;
	private CompletableFuture<Boolean> operation;
	private CompletableFuture<JsonObject> screenshot;
	private DroneEntityRenderState observed;

	public void start(Minecraft client, Path directory, UUID id, Vec3 home, Vec3 playerPosition) {
		require(!started, "Three-view smoke was started twice");
		started = true;
		active = this;
		droneId = id;
		droneHome = home;
		playerHome = playerPosition;
		observerHome = home.add(24, 1, 18);
		runDirectory = directory;
		previousCameraType = client.options.getCameraType();
		previousBobView = client.options.bobView().get();
		client.options.setCameraType(CameraType.FIRST_PERSON);
		client.options.bobView().set(false);
		report.addProperty("enabled", true);
		report.addProperty("input", "queued production B/V/G/F5 key mappings on the ordinary client loop");
		report.addProperty("cycle", "LINE_OF_SIGHT -> FPV -> THIRD_PERSON -> LINE_OF_SIGHT");
		report.addProperty("attitude_fixture", "real client metadata handler on the disposable controlled drone; normal render interpolation; not flight physics");
		operation = serverOperation(client, server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.getAbilities().flying = true;
			player.setNoGravity(true);
			player.onUpdateAbilities();
			player.teleportTo(observerHome.x(), observerHome.y(), observerHome.z());
		});
		advance(client);
	}

	public boolean tick(Minecraft client) {
		if (complete) return true;
		require(started, "Three-view smoke was not initialized");
		if (System.nanoTime() - stageStarted > 25_000_000_000L) throw new IllegalStateException("Three-view smoke stalled at stage " + stage);
		DroneEntity drone = DroneClientState.controlledDrone();
		if (client.player == null || drone == null || !droneId.equals(drone.getUUID())) return false;
		// The observer is positioned once in setup. Repositioning it here would conceal body-follow regressions.
		if (stage >= 5 && stage <= 10) sendAttitude(client, drone, TILT_PITCH, TILT_ROLL);
		else sendAttitude(client, drone, 0, 0);
		switch (stage) {
			case 1 -> {
				if (!doneOperation() || drone.position().distanceTo(droneHome) > 0.2) break;
				require(DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT, "Fresh world did not start in line of sight");
				report.addProperty("fresh_world_default_los", true);
				if (DroneClientControls.config().gamepadEnabled()) click(GLFW.GLFW_KEY_G);
				if (!DroneClientState.isVirtualControllerEnabled()) click(GLFW.GLFW_KEY_V);
				advance(client);
			}
			case 2 -> {
				if (!DroneClientState.isVirtualControllerEnabled() || DroneClientControls.config().gamepadEnabled()
						|| DroneClientState.inputSource() != DroneClientState.InputSource.KEYBOARD) break;
				report.addProperty("keyboard_virtual_control", true);
				click(GLFW.GLFW_KEY_B);
				advance(client);
			}
			case 3 -> {
				if (!remoteReady(client, DroneViewMode.FPV) || !settled(client)) break;
				require(camera(client).distanceTo(drone.position()) < 3, "FPV camera did not follow the controlled drone");
				require(camera(client).distanceTo(client.player.position()) > 12, "FPV camera followed the observer player");
				require(extract(client, drone).hiddenInFpv, "FPV render state did not hide the controlled mesh");
				require(!client.gameRenderer.getMainCamera().isDetached(), "FPV camera incorrectly retained a detached perspective");
				require(serverPlayerPosition.distanceTo(observerHome) < 0.25,
						"FPV moved the physical server player away from the observation position");
				report.addProperty("first_b_fpv_with_server_player_camera_and_remote_session", true);
				click(GLFW.GLFW_KEY_B);
				advance(client);
			}
			case 4 -> {
				if (!thirdReady(client) || !settled(client) || submittedFrames < 4 || !client.levelRenderer.hasRenderedAllSections()) break;
				Vec3 center = drone.position().add(0, drone.getPhysicsCenterYOffsetMeters(), 0);
				require(client.gameRenderer.getMainCamera().isDetached(), "Third-person camera did not use a detached perspective");
				float fov = ((GameRendererFovAccess) client.gameRenderer).fpvPerformance$readFov(client.gameRenderer.getMainCamera(), 1, true);
				require(Math.abs(fov - 70) < 0.001, "Third-person production FOV is not 70 degrees");
				report.addProperty("third_person_render_fov_degrees", fov);
				uprightCamera = camera(client);
				uprightForward = forward(client);
				Vec3 offset = uprightCamera.subtract(center);
				require(Math.abs(Math.hypot(offset.x(), offset.z()) - 5) < 0.25 && Math.abs(offset.y() - 2) < 0.25,
						"Third-person camera did not use its five-meter rear boom and two-meter world-up height");
				require(uprightCamera.distanceTo(client.player.position()) > 12, "Third-person camera followed the observer player");
				require(serverPlayerPosition.distanceTo(observerHome) < 0.25,
						"Third-person moved the physical server player away from the observation position");
				report.addProperty("second_b_third_person_with_server_player_camera_and_remote_session", true);
				report.addProperty("third_person_production_mesh_submitted", true);
				report.addProperty("third_person_hidden_in_fpv", observed.hiddenInFpv);
				capture(client, "three-view-third-person", "upright");
				advance(client);
			}
			case 5 -> {
				if (!doneScreenshot() || !settled(client) || !thirdReady(client) || submittedFrames < 4) break;
				if (Math.abs(drone.getInterpolatedRenderPitchRadians(1) - TILT_PITCH) > 0.02
						|| Math.abs(drone.getInterpolatedRenderRollRadians(1) - TILT_ROLL) > 0.02) break;
				require(camera(client).distanceTo(uprightCamera) < 0.15, "Body pitch/roll moved the third-person boom");
				require(forward(client).dot(uprightForward) > 0.9999, "Body pitch/roll moved the third-person horizon");
				Vec3 left = new Vec3(client.gameRenderer.getMainCamera().leftVector());
				require(Math.abs(left.y()) < 0.0001 && client.gameRenderer.getMainCamera().upVector().y() > 0.9,
						"Third-person camera is not upright during combined pitch/roll");
				report.addProperty("combined_38_degree_pitch_minus_47_degree_roll_keeps_upright_camera", true);
				capture(client, "three-view-third-person-tilted", "combined_pitch_roll");
				advance(client);
			}
			case 6 -> {
				if (!doneScreenshot()) break;
				Vec3 center = drone.position().add(0, drone.getPhysicsCenterYOffsetMeters(), 0);
				wallCenter = center.lerp(uprightCamera, 0.55);
				BlockPos middle = BlockPos.containing(wallCenter);
				for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) wall.add(middle.offset(x, y, 0));
				operation = serverOperation(client, server -> wall.forEach(pos -> server.overworld().setBlock(pos, Blocks.STONE.defaultBlockState(), 3)));
				advance(client);
			}
			case 7 -> {
				if (!doneOperation() || !client.level.getBlockState(BlockPos.containing(wallCenter)).is(Blocks.STONE) || !settled(client)) break;
				Vec3 center = drone.position().add(0, drone.getPhysicsCenterYOffsetMeters(), 0);
				if (camera(client).distanceTo(center) >= uprightCamera.distanceTo(center) - 1) break;
				HitResult desired = client.level.clip(new ClipContext(center, uprightCamera, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, drone));
				HitResult actual = client.level.clip(new ClipContext(center, camera(client), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, drone));
				require(desired.getType() == HitResult.Type.BLOCK && actual.getType() == HitResult.Type.MISS,
						"Third-person camera did not retract before the actual client wall");
				report.addProperty("client_block_wall_retracts_camera_without_crossing_surface", true);
				report.addProperty("unobstructed_boom_meters", uprightCamera.distanceTo(center));
				report.addProperty("wall_retracted_boom_meters", camera(client).distanceTo(center));
				capture(client, "three-view-third-person-wall", "wall_retracted");
				advance(client);
			}
			case 8 -> {
				if (!doneScreenshot()) break;
				operation = serverOperation(client, server -> wall.forEach(pos -> server.overworld().setBlock(pos, Blocks.AIR.defaultBlockState(), 3)));
				advance(client);
			}
			case 9 -> {
				if (!doneOperation() || !client.level.getBlockState(BlockPos.containing(wallCenter)).isAir() || !settled(client)) break;
				if (camera(client).distanceTo(uprightCamera) > 0.15) break;
				report.addProperty("removing_wall_restores_follow_boom", true);
				click(GLFW.GLFW_KEY_F5);
				advance(client);
			}
			case 10 -> {
				if (client.options.getCameraType() != CameraType.THIRD_PERSON_BACK || !thirdReady(client) || !settled(client)) break;
				require(camera(client).distanceTo(uprightCamera) < 0.15, "F5 displaced the mounted third-person camera");
				report.addProperty("f5_retains_remote_mode_and_follow_camera", true);
				click(GLFW.GLFW_KEY_B);
				advance(client);
			}
			case 11 -> {
				if (!losReady(client) || !settled(client)) break;
				require(client.options.getCameraType() == CameraType.THIRD_PERSON_BACK, "B reset the player's F5 perspective");
				require(camera(client).distanceTo(client.player.position()) < 6 && camera(client).distanceTo(drone.position()) > 12,
						"Line of sight did not return to the distant player's vanilla camera");
				report.addProperty("third_b_los_and_server_player_camera", true);
				report.addProperty("los_restores_current_f5_player_perspective", true);
				click(GLFW.GLFW_KEY_F5);
				click(GLFW.GLFW_KEY_F5);
				click(GLFW.GLFW_KEY_B);
				advance(client);
			}
			case 12 -> {
				if (client.options.getCameraType() != CameraType.FIRST_PERSON || !remoteReady(client, DroneViewMode.FPV) || !settled(client)) break;
				click(GLFW.GLFW_KEY_B);
				advance(client);
			}
			case 13 -> {
				if (!thirdReady(client) || !settled(client)) break;
				click(GLFW.GLFW_KEY_V);
				advance(client);
			}
			case 14 -> {
				if (DroneClientState.isVirtualControllerEnabled() || !losReady(client) || !settled(client)) break;
				report.addProperty("virtual_controller_loss_resets_mode_and_both_cameras", true);
				restore(client);
				advance(client);
			}
			case 15 -> {
				if (!doneOperation()) break;
				complete = true;
				report.addProperty("camera_flow_passed", true);
			}
			default -> throw new IllegalStateException("Unknown three-view stage " + stage);
		}
		return complete;
	}

	public static void onExtracted(DroneEntity drone, DroneEntityRenderState state) {
		ThreeViewSmoke smoke = active;
		if (smoke != null && smoke.droneId.equals(drone.getUUID())) smoke.observed = state;
	}

	public static void onSubmitted(DroneEntityRenderState state) {
		ThreeViewSmoke smoke = active;
		if (smoke != null && state == smoke.observed && !state.hiddenInFpv) smoke.submittedFrames++;
	}

	public void serverTick(MinecraftServer server) {
		if (!started || server.getPlayerList().getPlayers().isEmpty()) return;
		require(server.isSameThread(), "Three-view server fixture used the wrong thread");
		if (!(server.overworld().getEntity(droneId) instanceof DroneEntity drone)) return;
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		serverPlayerCamera = player.getCamera() == player;
		serverRemoteView = DroneRemoteSessions.isRemoteView(player);
		serverSessionDrone = DroneRemoteSessions.trackedDrone(player) == drone;
		serverPlayerPosition = player.position();
		if (!complete) { drone.setPos(droneHome); drone.setDeltaMovement(Vec3.ZERO); }
	}

	/** Enter a real mounted view before vanilla closes the connection. */
	public boolean prepareDisconnect(Minecraft client) {
		if (!complete) return false;
		if (disconnectStage == 0) {
			if (!DroneClientState.isVirtualControllerEnabled()) click(GLFW.GLFW_KEY_V);
			disconnectStage++;
			return false;
		}
		if (!DroneClientState.isVirtualControllerEnabled()) return false;
		if (DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT) { click(GLFW.GLFW_KEY_B); return false; }
		if (DroneClientState.viewMode() == DroneViewMode.FPV) { click(GLFW.GLFW_KEY_B); return false; }
		if (!remoteReady(client, DroneViewMode.THIRD_PERSON)) return false;
		report.addProperty("disconnect_begins_in_mounted_third_person", true);
		return true;
	}

	public void verifyDisconnected(Minecraft client) {
		require(client.level == null && client.player == null && DroneClientState.controlledDrone() == null
				&& DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT && !DroneClientState.armed(),
				"Vanilla disconnect left stale drone camera/control state");
		disconnectVerified = true;
		report.addProperty("disconnect_resets_remote_mode_and_controlled_drone", true);
	}

	public void verifyReopened(Minecraft client) {
		require(DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT && client.getCameraEntity() == client.player,
				"Reopening the disposable world restored a stale mounted view");
		reopenVerified = true;
		report.addProperty("world_reopen_starts_with_player_los_camera", true);
	}

	public boolean passed() { return complete && restored && disconnectVerified && reopenVerified && screenshots.size() == 3; }
	public JsonObject summary() {
		report.addProperty("passed", passed());
		report.add("screenshots", screenshots);
		return report;
	}

	public void restore(Minecraft client) {
		if (!started || restored) return;
		active = null;
		client.options.setCameraType(previousCameraType);
		client.options.bobView().set(previousBobView);
		if (client.player != null) {
			client.player.getAbilities().flying = false;
			client.player.setNoGravity(false);
		}
		if (client.getSingleplayerServer() != null) operation = serverOperation(client, server -> {
			wall.forEach(pos -> server.overworld().setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.getAbilities().flying = false;
			player.setNoGravity(false);
			player.onUpdateAbilities();
			player.teleportTo(playerHome.x(), playerHome.y(), playerHome.z());
		});
		restored = true;
	}

	private boolean remoteReady(Minecraft client, DroneViewMode mode) {
		DroneEntity drone = DroneClientState.controlledDrone();
		return drone != null && droneId.equals(drone.getUUID()) && DroneClientState.viewMode() == mode
				&& DroneClientState.isDroneCameraActive(client.level) && client.getCameraEntity() == drone
				&& serverPlayerCamera && serverRemoteView && serverSessionDrone;
	}
	private boolean thirdReady(Minecraft client) {
		return remoteReady(client, DroneViewMode.THIRD_PERSON) && DroneClientState.isThirdPersonActive(client.level)
				&& !DroneClientState.isFpvActive(client.level) && observed != null && !observed.hiddenInFpv;
	}
	private boolean losReady(Minecraft client) {
		return DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT && !DroneClientState.isDroneCameraActive(client.level)
				&& client.getCameraEntity() == client.player && serverPlayerCamera && !serverRemoteView;
	}
	private boolean settled(Minecraft client) { return client.player.tickCount - stageTick >= 8; }
	private void advance(Minecraft client) {
		stage++;
		stageTick = client.player.tickCount;
		stageStarted = System.nanoTime();
		submittedFrames = 0;
	}
	private boolean doneOperation() { if (operation == null || !operation.isDone()) return false; operation.join(); return true; }
	private boolean doneScreenshot() {
		if (screenshot == null || !screenshot.isDone()) return false;
		if (!screenshotRecorded) { screenshots.add(screenshot.join()); screenshotRecorded = true; }
		return true;
	}
	private void capture(Minecraft client, String name, String view) {
		JsonObject capture = new JsonObject();
		capture.addProperty("view", view);
		capture.addProperty("production_renderer_submitted", submittedFrames >= 4);
		capture.addProperty("rendered_frames", submittedFrames);
		capture.addProperty("hidden_in_fpv", observed.hiddenInFpv);
		capture.addProperty("pitch_radians", observed.pitchRadians);
		capture.addProperty("roll_radians", observed.rollRadians);
		capture.addProperty("camera_entity_is_drone", client.getCameraEntity() == DroneClientState.controlledDrone());
		capture.addProperty("server_camera_entity_is_player", serverPlayerCamera);
		capture.addProperty("server_remote_view_active", serverRemoteView);
		capture.addProperty("server_session_tracks_this_drone", serverSessionDrone);
		capture.addProperty("terrain_render_complete", client.levelRenderer.hasRenderedAllSections());
		capture.add("camera_position", vector(camera(client)));
		capture.add("camera_forward", vector(forward(client)));
		Path path = runDirectory.resolve("screenshots/" + name + ".png");
		capture.addProperty("screenshot", path.toString());
		if (screenshots.isEmpty()) report.addProperty("screenshot", path.toString());
		CompletableFuture<String> saved = new CompletableFuture<>();
		screenshotRecorded = false;
		int width = client.getWindow().getWidth(), height = client.getWindow().getHeight();
		screenshot = saved.orTimeout(15, TimeUnit.SECONDS).thenApplyAsync(message -> verifyPng(path, capture, width, height));
		Screenshot.grab(runDirectory.toFile(), path.getFileName().toString(), client.getMainRenderTarget(), 1, message -> saved.complete(message.getString()));
	}
	private static DroneEntityRenderState extract(Minecraft client, DroneEntity drone) {
		Object renderer = client.getEntityRenderDispatcher().getRenderer(drone);
		require(renderer instanceof DroneEntityRenderer, "Three-view smoke did not use the production drone renderer");
		DroneEntityRenderState state = new DroneEntityRenderState();
		((DroneEntityRenderer) renderer).extractRenderState(drone, state, 1);
		return state;
	}
	private static void sendAttitude(Minecraft client, DroneEntity drone, float pitch, float roll) {
		client.getConnection().handleSetEntityData(new ClientboundSetEntityDataPacket(drone.getId(), List.of(
				SynchedEntityData.DataValue.create(PITCH, pitch), SynchedEntityData.DataValue.create(YAW, 0f), SynchedEntityData.DataValue.create(ROLL, roll))));
	}
	@SuppressWarnings("unchecked")
	private static EntityDataAccessor<Float> attitudeAccessor(String name) {
		try {
			Field field = DroneEntity.class.getDeclaredField(name);
			field.setAccessible(true);
			return (EntityDataAccessor<Float>) field.get(null);
		} catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not get diagnostic attitude accessor " + name, error); }
	}
	private static CompletableFuture<Boolean> serverOperation(Minecraft client, java.util.function.Consumer<MinecraftServer> task) {
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		MinecraftServer server = client.getSingleplayerServer();
		server.execute(() -> { try { require(server.isSameThread(), "Three-view world mutation used the wrong thread"); task.accept(server); result.complete(true); }
			catch (Throwable error) { result.completeExceptionally(error); } });
		return result;
	}
	private static JsonObject verifyPng(Path path, JsonObject capture, int width, int height) {
		try {
			byte[] bytes = Files.readAllBytes(path);
			require(bytes.length >= 24 && ByteBuffer.wrap(bytes).getLong() == 0x89504e470d0a1a0aL, "Three-view screenshot is not a PNG");
			require(ByteBuffer.wrap(bytes, 16, 4).getInt() == width && ByteBuffer.wrap(bytes, 20, 4).getInt() == height, "Three-view PNG differs from the captured framebuffer");
			capture.addProperty("png_verified", true);
			capture.addProperty("screenshot_width", width);
			capture.addProperty("screenshot_height", height);
			return capture;
		} catch (IOException error) { throw new IllegalStateException("Three-view screenshot did not save", error); }
	}
	private static Vec3 camera(Minecraft client) { return client.gameRenderer.getMainCamera().position(); }
	private static Vec3 forward(Minecraft client) { return new Vec3(client.gameRenderer.getMainCamera().forwardVector()); }
	private static JsonArray vector(Vec3 value) { JsonArray result = new JsonArray(); result.add(value.x()); result.add(value.y()); result.add(value.z()); return result; }
	private static void click(int key) { KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(key)); }
	private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
