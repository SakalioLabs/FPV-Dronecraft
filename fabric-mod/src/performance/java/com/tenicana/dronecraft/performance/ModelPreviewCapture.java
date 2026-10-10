package com.tenicana.dronecraft.performance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.client.render.DroneEntityModel;
import com.tenicana.dronecraft.client.render.DroneEntityRenderState;
import com.tenicana.dronecraft.client.render.DroneEntityRenderer;
import com.tenicana.dronecraft.client.render.DroneModelLayers;
import com.tenicana.dronecraft.entity.DroneEntity;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded render fixture for the production model, isolated from flight physics. */
public final class ModelPreviewCapture {
	public static final boolean ENABLED = Boolean.getBoolean("fpvdrone.performance.modelPreview");
	private static final View[] VIEWS = {
			new View("front-perspective", new Vec3(2.5, 2.4, 4.5), 0, 0, 0, 0),
			new View("rear-perspective", new Vec3(-2.5, 2.4, -4.5), 0, 0, 0, 0),
			new View("top", new Vec3(0, 5.5, 0.01), 0, 0, 0, 0),
			new View("spinning-combined-pitch-roll-a", new Vec3(2.5, 2.4, 4.5), 24, 28, -18, 7231),
			new View("spinning-combined-pitch-roll-b", new Vec3(2.5, 2.4, 4.5), 24, 28, -18, 7231)
	};
	private static ModelPreviewCapture active;
	private final JsonObject report = new JsonObject();
	private final JsonArray screenshots = new JsonArray();
	private UUID droneId;
	private Vec3 droneHome, playerHome;
	private Path runDirectory, reportPath;
	private int stage, stageTick, renderedFrames;
	private long stageStarted;
	private float renderedAge;
	private DroneEntityRenderState observedState;
	private CompletableFuture<Boolean> placement;
	private CompletableFuture<JsonObject> screenshot;
	private volatile boolean started, complete;
	private boolean restored, previousHideGui, previousBobView;
	private int previousFov;
	private double previousGamma, previousFovEffectScale;
	private CameraType previousCameraType;

	/** Server-thread platform edits only, inside the already disposable flat world. */
	public static void preparePlatform(ServerLevel level, int surface) {
		level.setWeatherParameters(12000, 0, false, false);
		for (int x = 1; x <= 15; x++) {
			for (int z = 5; z <= 19; z++) {
				level.setBlock(new BlockPos(x, surface - 1, z),
						((x + z) % 4 == 0 ? Blocks.SEA_LANTERN : Blocks.SMOOTH_QUARTZ).defaultBlockState(), 3);
			}
		}
	}

	public void start(Minecraft client, Path output, Path directory, UUID id, Vec3 dronePosition, Vec3 playerPosition, String flightModelId) {
		if (started) throw new IllegalStateException("Model preview was started twice");
		active = this;
		droneId = id;
		droneHome = dronePosition;
		playerHome = playerPosition;
		runDirectory = directory;
		reportPath = output.resolveSibling("model-preview-report.json");
		previousHideGui = client.options.hideGui;
		previousFov = client.options.fov().get();
		previousGamma = client.options.gamma().get();
		previousBobView = client.options.bobView().get();
		previousFovEffectScale = client.options.fovEffectScale().get();
		previousCameraType = client.options.getCameraType();
		started = true;
		client.options.hideGui = true;
		client.options.fov().set(40);
		client.options.gamma().set(1.0);
		client.options.bobView().set(false);
		client.options.fovEffectScale().set(0.0);
		client.options.setCameraType(CameraType.FIRST_PERSON);
		report.addProperty("schema", "fpv-normal-client-model-preview-v1");
		report.addProperty("scope", "Production mesh/texture/animation render fixture; explicit diagnostic attitude and RPM, not physical flight validation");
		report.addProperty("model_layer", DroneModelLayers.DRONE.toString());
		report.addProperty("texture", "fpvdrone:textures/entity/drone.png");
		report.addProperty("flight_model_id", flightModelId);
		report.addProperty("drone_uuid", id.toString());
		report.addProperty("world_day_time", 6000);
		report.addProperty("clear_weather", true);
		report.addProperty("safe_illuminated_platform", true);
		report.addProperty("fov_degrees", 40);
		try {
			// Read the loaded production class, rather than an inlined helper constant.
			report.addProperty("actual_model_id", (String) DroneEntityModel.class.getField("MODEL_ID").get(null));
			report.addProperty("source_cube_count", DroneEntityModel.class.getField("SOURCE_CUBE_COUNT").getInt(null));
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Loaded production model has no visual identity", exception);
		}
		beginView(client);
	}

	public boolean tick(Minecraft client) {
		if (complete) return true;
		if (!started) throw new IllegalStateException("Model preview was not initialized");
		if (System.nanoTime() - stageStarted > 20_000_000_000L) throw new IllegalStateException("Model preview stalled at " + VIEWS[stage].name);
		if (!placement.isDone()) return false;
		placement.join();
		placeObserver(client);
		if (screenshot == null || !screenshot.isDone()) return false;
		screenshots.add(screenshot.join());
		screenshot = null;
		if (++stage < VIEWS.length) {
			beginView(client);
			return false;
		}
		complete = true;
		active = null;
		restore(client);
		return true;
	}

	/** Called at the normal runTick HEAD, so readback sees the previous completed frame. */
	public void frameBoundary(Minecraft client) {
		if (!started || complete || screenshot != null || placement == null || !placement.isDone() || observedState == null) return;
		if (renderedFrames < 4 || client.player.tickCount - stageTick < 8) return;
		if (!client.levelRenderer.hasRenderedAllSections()) return;
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		Vec3 eye = eyePosition();
		Vec3 target = droneHome.add(0, 0.42, 0);
		boolean observer = client.getCameraEntity() == client.player && client.options.getCameraType() == CameraType.FIRST_PERSON
				&& !DroneClientState.isFpvViewEnabled() && !observedState.hiddenInFpv;
		boolean unobstructed = client.level.clip(new ClipContext(camera, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player)).getType() == HitResult.Type.MISS;
		if (!observer || !unobstructed || camera.distanceTo(eye) > 0.15 || cameraForwardDot(client, target.subtract(camera).normalize()) < 0.999) return;
		DroneEntity drone = clientDrone(client);
		if (drone == null || drone.position().distanceTo(droneHome) > 0.15) return;
		Object renderer = client.getEntityRenderDispatcher().getRenderer(drone);
		if (!(renderer instanceof DroneEntityRenderer)) throw new IllegalStateException("Model preview did not use the production drone renderer");
		report.addProperty("renderer_class", renderer.getClass().getName());
		View view = VIEWS[stage];
		JsonObject capture = new JsonObject();
		capture.addProperty("view", view.name);
		capture.addProperty("observer_camera_without_fpv", observer);
		capture.addProperty("unobstructed_block_line_of_sight", unobstructed);
		capture.addProperty("terrain_render_complete", client.levelRenderer.hasRenderedAllSections());
		capture.addProperty("production_renderer_submitted", true);
		capture.addProperty("rendered_frames_at_pose", renderedFrames);
		capture.addProperty("render_age_ticks", renderedAge);
		capture.addProperty("pitch_degrees", view.pitch);
		capture.addProperty("yaw_degrees", view.yaw);
		capture.addProperty("roll_degrees", view.roll);
		capture.addProperty("rotor_rpm", view.rpm);
		capture.addProperty("rotor_count", observedState.rotorCount);
		capture.addProperty("camera_distance_meters", camera.distanceTo(target));
		capture.add("camera_position", vector(camera));
		capture.add("drone_position", vector(drone.position()));
		capture.addProperty("camera_forward_dot_target", cameraForwardDot(client, target.subtract(camera).normalize()));
		Path path = runDirectory.resolve("screenshots/model-preview-" + view.name + ".png");
		capture.addProperty("screenshot", path.toString());
		CompletableFuture<String> saved = new CompletableFuture<>();
		int expectedWidth = client.getWindow().getWidth(), expectedHeight = client.getWindow().getHeight();
		screenshot = saved.orTimeout(15, TimeUnit.SECONDS).thenApplyAsync(message -> verifyPng(path, capture, expectedWidth, expectedHeight));
		Screenshot.grab(runDirectory.toFile(), path.getFileName().toString(), client.getMainRenderTarget(), 1, message -> saved.complete(message.getString()));
	}

	/** Only the preview entity's render state changes; production simulation stays idle. */
	public static void applyRenderPose(DroneEntity drone, DroneEntityRenderState state) {
		ModelPreviewCapture preview = active;
		if (preview == null || !drone.getUUID().equals(preview.droneId)) return;
		View view = VIEWS[preview.stage];
		state.pitchRadians = (float) Math.toRadians(view.pitch);
		state.yawRadians = (float) Math.toRadians(view.yaw);
		state.rollRadians = (float) Math.toRadians(view.roll);
		state.armed = view.rpm > 0;
		state.motorPower = view.rpm > 0 ? 0.35f : 0;
		for (int i = 0; i < state.rotorRpm.length; i++) state.rotorRpm[i] = i < state.rotorCount ? view.rpm : 0;
		preview.observedState = state;
	}

	public static void onSubmitted(DroneEntityRenderState state) {
		ModelPreviewCapture preview = active;
		if (preview != null && state == preview.observedState && !state.hiddenInFpv) {
			preview.renderedFrames++;
			preview.renderedAge = state.ageInTicks;
		}
	}

	public void holdDrone(MinecraftServer server) {
		if (!started || complete || !server.isSameThread()) return;
		if (server.overworld().getEntity(droneId) instanceof DroneEntity drone) {
			drone.setPos(droneHome);
			drone.setDeltaMovement(Vec3.ZERO);
		}
	}

	public JsonObject summary() {
		JsonObject summary = new JsonObject();
		summary.addProperty("enabled", true);
		summary.addProperty("passed", complete && screenshots.size() == VIEWS.length && restored);
		summary.addProperty("report", reportPath == null ? "uninitialized" : reportPath.toString());
		return summary;
	}

	public void writeReport(boolean smokePassed, String failure) throws IOException {
		if (reportPath == null) return;
		report.addProperty("passed", complete && screenshots.size() == VIEWS.length && restored && smokePassed);
		report.addProperty("failure", failure);
		report.addProperty("all_expected_views_captured", screenshots.size() == VIEWS.length);
		report.addProperty("preview_options_restored", restored);
		report.addProperty("save_flush_reopen_and_optional_monitor_smoke_passed", smokePassed);
		report.addProperty("spinning_samples_have_distinct_render_age", screenshots.size() == VIEWS.length
				&& screenshots.get(3).getAsJsonObject().get("render_age_ticks").getAsFloat() != screenshots.get(4).getAsJsonObject().get("render_age_ticks").getAsFloat());
		report.add("screenshots", screenshots);
		Files.writeString(reportPath, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
	}

	public void restore(Minecraft client) {
		if (!started || restored) return;
		active = null;
		client.options.hideGui = previousHideGui;
		client.options.fov().set(previousFov);
		client.options.gamma().set(previousGamma);
		client.options.bobView().set(previousBobView);
		client.options.fovEffectScale().set(previousFovEffectScale);
		client.options.setCameraType(previousCameraType);
		if (client.player != null) {
			client.player.getAbilities().flying = false;
			client.player.setNoGravity(false);
			client.player.setPos(playerHome);
			client.player.setOldPosAndRot();
		}
		MinecraftServer server = client.getSingleplayerServer();
		if (server != null) server.execute(() -> {
			if (server.getPlayerList().getPlayers().isEmpty()) return;
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.getAbilities().flying = false;
			player.setNoGravity(false);
			player.onUpdateAbilities();
			player.teleportTo(playerHome.x(), playerHome.y(), playerHome.z());
		});
		restored = true;
	}

	private void beginView(Minecraft client) {
		stageStarted = System.nanoTime();
		stageTick = client.player.tickCount;
		renderedFrames = 0;
		observedState = null;
		placement = new CompletableFuture<>();
		MinecraftServer server = client.getSingleplayerServer();
		Vec3 eye = eyePosition();
		server.execute(() -> {
			try {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setCamera(player);
				player.getAbilities().flying = true;
				player.setNoGravity(true);
				player.onUpdateAbilities();
				player.teleportTo(eye.x(), eye.y() - player.getEyeHeight(), eye.z());
				placement.complete(true);
			} catch (Throwable exception) { placement.completeExceptionally(exception); }
		});
		placeObserver(client);
	}

	private void placeObserver(Minecraft client) {
		if (DroneClientState.isFpvViewEnabled()) throw new IllegalStateException("Model preview requires the observer player camera with FPV off");
		KeyMapping.releaseAll();
		client.setScreen(null);
		client.setCameraEntity(client.player);
		client.player.getAbilities().flying = true;
		client.player.setNoGravity(true);
		Vec3 eye = eyePosition();
		Vec3 target = droneHome.add(0, 0.42, 0);
		Vec3 look = target.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-look.x(), look.z()));
		float pitch = (float) -Math.toDegrees(Math.atan2(look.y(), Math.hypot(look.x(), look.z())));
		client.player.snapTo(eye.x(), eye.y() - client.player.getEyeHeight(), eye.z(), yaw, pitch);
		client.player.setOldPosAndRot();
		client.player.setDeltaMovement(Vec3.ZERO);
		DroneEntity drone = clientDrone(client);
		if (drone != null) { drone.setPos(droneHome); drone.setOldPosAndRot(); drone.setDeltaMovement(Vec3.ZERO); }
	}

	private Vec3 eyePosition() { return droneHome.add(VIEWS[stage].eyeOffset); }
	private DroneEntity clientDrone(Minecraft client) {
		return client.level.getEntitiesOfClass(DroneEntity.class, client.player.getBoundingBox().inflate(16), drone -> drone.getUUID().equals(droneId)).stream().findFirst().orElse(null);
	}
	private static JsonObject verifyPng(Path path, JsonObject capture, int width, int height) {
		try {
			byte[] bytes = Files.readAllBytes(path);
			if (bytes.length < 24 || ByteBuffer.wrap(bytes).getLong() != 0x89504e470d0a1a0aL) throw new IOException("Screenshot is not a PNG: " + path);
			int actualWidth = ByteBuffer.wrap(bytes, 16, 8).getInt();
			int actualHeight = ByteBuffer.wrap(bytes, 20, 4).getInt();
			if (actualWidth != width || actualHeight != height) throw new IOException("Screenshot dimensions differ from the captured framebuffer");
			capture.addProperty("screenshot_bytes", bytes.length);
			capture.addProperty("screenshot_width", actualWidth);
			capture.addProperty("screenshot_height", actualHeight);
			capture.addProperty("png_verified", true);
			return capture;
		} catch (IOException exception) { throw new IllegalStateException("Model preview screenshot did not save", exception); }
	}
	private static JsonArray vector(Vec3 value) { JsonArray result = new JsonArray(); result.add(value.x()); result.add(value.y()); result.add(value.z()); return result; }
	private static double cameraForwardDot(Minecraft client, Vec3 direction) {
		var forward = client.gameRenderer.getMainCamera().forwardVector();
		return forward.x() * direction.x() + forward.y() * direction.y() + forward.z() * direction.z();
	}
	private record View(String name, Vec3 eyeOffset, float pitch, float yaw, float roll, float rpm) { }
}
