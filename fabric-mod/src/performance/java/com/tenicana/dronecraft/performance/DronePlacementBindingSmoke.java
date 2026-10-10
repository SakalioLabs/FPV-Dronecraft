package com.tenicana.dronecraft.performance;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.tenicana.dronecraft.camera.DroneViewMode;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.client.control.DroneClientControls;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.item.DroneControllerItem;
import com.tenicana.dronecraft.network.DroneRemoteSessions;
import com.tenicana.dronecraft.registry.DroneItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Real native placement and entity-interaction packets, followed by ordinary body rendering. */
public final class DronePlacementBindingSmoke {
	public static final boolean ENABLED = Boolean.getBoolean("fpvdrone.performance.placementBindingSmoke");
	private static final int PERSIST_SLOT = 35;
	private static final EntityDataAccessor<Float> PITCH = attitudeAccessor("PITCH");
	private static final EntityDataAccessor<Float> YAW = attitudeAccessor("YAW");
	private static final EntityDataAccessor<Float> ROLL = attitudeAccessor("ROLL");
	private static DronePlacementBindingSmoke active;
	private final JsonObject report = new JsonObject();
	private final JsonArray screenshots = new JsonArray();
	private Path directory;
	private Vec3 playerHome, observerHome, cameraHome, bodyBaseline;
	private BlockPos nearFloor, farFloor;
	private UUID firstId, targetId, ownerId;
	private String modelId;
	private volatile Snapshot snapshot;
	private volatile boolean started, poseFixture, complete;
	private boolean restored, setupRestored, screenshotRecorded, reopened, renderWindowStarted;
	private int stage, stageTick, previousSelected;
	private long stageStarted, bodyExtractions, bodySubmissions, handPasses, vanillaHands;
	private CameraType previousCameraType;
	private boolean previousBobView, previousHideGui, previousShift;
	private GameType previousGameType;
	private ItemStack previousMain, previousOff, previousPersist;
	private CompletableFuture<Boolean> operation;
	private CompletableFuture<JsonObject> screenshot;
	private AvatarRenderState observedBody;

	public void start(Minecraft client, Path runDirectory, Vec3 home, Vec3 originalPlayerHome) {
		require(!started, "Placement/binding smoke started twice");
		started = true;
		active = this;
		directory = runDirectory;
		playerHome = originalPlayerHome;
		previousCameraType = client.options.getCameraType();
		previousBobView = client.options.bobView().get();
		previousHideGui = client.options.hideGui;
		previousShift = client.options.keyShift.isDown();
		client.options.setCameraType(CameraType.FIRST_PERSON);
		client.options.bobView().set(false);
		client.options.hideGui = false;
		client.options.keyShift.setDown(false);
		if (DroneClientControls.config().gamepadEnabled()) click(GLFW.GLFW_KEY_G);
		if (DroneClientState.isVirtualControllerEnabled()) click(GLFW.GLFW_KEY_V);
		report.addProperty("enabled", true);
		report.addProperty("interaction_path", "ordinary MultiPlayerGameMode.useItem/useItemOn/interactAt/interact network packets; real keyboard shift state and player raycast");
		report.addProperty("body_fixture", "survival pilot on native heightmap ground; one absolute initial teleport with zero velocity, no per-tick body writes");
		report.addProperty("render_observation", "native LevelRenderer.extractEntity(actual LocalPlayer) and LivingEntityRenderer.submit(AvatarRenderState.id); diagnostic hooks never bypass filters or submit extra models");
		report.addProperty("camera_fixture", "after real binding/arming checks, only the target drone receives a static server metadata pose facing the pilot; production CameraMixin provides both views");
		operation = serverOperation(client, server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ownerId = player.getUUID();
			previousGameType = player.gameMode();
			previousSelected = player.getInventory().getSelectedSlot();
			previousMain = player.getMainHandItem().copy();
			previousOff = player.getOffhandItem().copy();
			previousPersist = player.getInventory().getItem(PERSIST_SLOT).copy();
			int x = (int) Math.floor(home.x()) + 24, z = (int) Math.floor(home.z()) + 24;
			int surface = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			observerHome = new Vec3(x + 0.5, surface, z + 0.5);
			nearFloor = BlockPos.containing(observerHome.add(1, -1, 0));
			// Keep the farther target inside native Survival's 3 m eye-to-entity reach.
			farFloor = BlockPos.containing(observerHome.add(2, -1, 1));
			cameraHome = observerHome.add(-2, 1.2, -7);
			require(!server.overworld().getBlockState(BlockPos.containing(observerHome).below()).getCollisionShape(server.overworld(), BlockPos.containing(observerHome).below()).isEmpty(), "Placement pilot has no native solid floor");
			player.setGameMode(GameType.SURVIVAL);
			player.setNoGravity(false);
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
			player.setDeltaMovement(Vec3.ZERO);
			player.connection.teleport(new PositionMoveRotation(observerHome, Vec3.ZERO, 0, 0), Set.of());
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneItems.DRONE_CONTROLLER));
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			player.inventoryMenu.broadcastChanges();
		});
		advance(client);
	}

	public boolean tick(Minecraft client) {
		if (complete) return true;
		require(started, "Placement/binding smoke was not initialized");
		if (System.nanoTime() - stageStarted > 25_000_000_000L) throw new IllegalStateException("Placement/binding smoke stalled at stage " + stage + "; server=" + snapshot);
		if (client.player == null || client.level == null || client.gameMode == null || snapshot == null) return false;
		Snapshot state = snapshot;
		switch (stage) {
			case 1 -> {
				if (!doneOperation() || !held(client, DroneItems.DRONE_CONTROLLER, 1) || !losReady(client) || !settled(client)
						|| DroneClientControls.config().gamepadEnabled() || DroneClientState.isVirtualControllerEnabled()) break;
				if (!state.grounded() || !client.player.onGround() || state.body().distanceTo(observerHome) > 0.05 || client.player.position().distanceTo(observerHome) > 0.05) break;
				bodyBaseline = state.body();
				require(state.drones().isEmpty(), "Placement area unexpectedly already contains a drone");
				require(DroneControllerItem.boundDroneId(client.player.getMainHandItem()) == null, "Fresh controller unexpectedly contains a binding");
				client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
				advance(client);
			}
			case 2 -> {
				if (!settled(client)) break;
				require(state.drones().isEmpty() && state.binding() == null, "Right-clicking an unbound controller spawned or bound a drone");
				report.addProperty("controller_right_click_air_does_not_spawn_or_bind", true);
				operation = serverOperation(client, server -> { ServerPlayer player = server.getPlayerList().getPlayers().getFirst(); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneItems.DRONE, 2)); player.inventoryMenu.broadcastChanges(); });
				advance(client);
			}
			case 3 -> {
				if (!doneOperation() || !held(client, DroneItems.DRONE, 2)) break;
				place(client, nearFloor);
				advance(client);
			}
			case 4 -> {
				if (state.drones().size() != 1 || state.mainCount() != 1 || !held(client, DroneItems.DRONE, 1) || !settled(client)) break;
				DroneRecord first = state.drones().getFirst();
				require(first.owner() == null, "Placed drone was automatically assigned to the pilot");
				firstId = first.id();
				place(client, farFloor);
				advance(client);
			}
			case 5 -> {
				if (state.drones().size() != 2 || state.mainCount() != 0 || !client.player.getMainHandItem().isEmpty() || !settled(client)) break;
				DroneRecord second = state.drones().stream().filter(drone -> !drone.id().equals(firstId)).findFirst().orElseThrow();
				require(second.owner() == null && state.drones().stream().allMatch(drone -> drone.owner() == null), "Independent placements acquired an owner without binding");
				targetId = second.id();
				report.addProperty("two_native_item_placements_create_two_unbound_drones", true);
				report.addProperty("survival_placement_consumes_exactly_two_items", true);
				requireLosBodyHidden();
				lookAt(client, clientDrone(client, targetId).getBoundingBox().getCenter());
				capture(client, "placement-two-independent-drones", "los_independent_placements");
				advance(client);
			}
			case 6 -> {
				if (!doneScreenshot()) break;
				operation = serverOperation(client, server -> { ServerPlayer player = server.getPlayerList().getPlayers().getFirst(); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneItems.DRONE_CONTROLLER)); player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); player.inventoryMenu.broadcastChanges(); });
				advance(client);
			}
			case 7 -> {
				if (!doneOperation() || !held(client, DroneItems.DRONE_CONTROLLER, 1) || !settled(client)) break;
				interact(client, targetId, false);
				advance(client);
			}
			case 8 -> {
				if (!settled(client)) break;
				require(state.binding() == null && DroneControllerItem.boundDroneId(client.player.getMainHandItem()) == null
						&& state.drones().stream().allMatch(drone -> drone.owner() == null), "Plain controller entity-click bound a drone without shift");
				report.addProperty("plain_controller_entity_right_click_does_not_bind", true);
				client.options.keyShift.setDown(true);
				advance(client);
			}
			case 9 -> {
				if (!client.player.isShiftKeyDown() || !settled(client)) break;
				interact(client, firstId, true);
				advance(client);
			}
			case 10 -> {
				if (!bound(client, state, firstId) || !settled(client)) break;
				report.addProperty("shift_entity_interaction_binds_first_drone", true);
				interact(client, targetId, true);
				advance(client);
			}
			case 11 -> {
				if (!bound(client, state, targetId) || !settled(client)) break;
				client.options.keyShift.setDown(false);
				DroneRecord target = record(state, targetId), peer = record(state, firstId);
				require(ownerId.equals(target.owner()) && ownerId.equals(peer.owner()), "Bound drones did not receive the real server owner");
				require(peer.position().distanceTo(state.body()) + 1 < target.position().distanceTo(state.body()), "Exact binding fixture did not select the farther of two owned drones");
				report.addProperty("second_shift_entity_interaction_rebinds_to_exact_farther_owned_drone", true);
				report.addProperty("binding_uuid_synchronizes_to_client_controller_and_selected_target", true);
				report.addProperty("bound_drone_uuid", targetId.toString());
				report.addProperty("nearer_owned_drone_uuid", firstId.toString());
				modelId = target.model();
				capture(client, "binding-exact-farther-drone", "los_exact_binding");
				advance(client);
			}
			case 12 -> {
				if (!doneScreenshot() || client.player.isShiftKeyDown() || !settled(client)) break;
				click(GLFW.GLFW_KEY_R);
				advance(client);
			}
			case 13 -> {
				if (!record(state, targetId).armed() || !DroneClientState.armed() || !settled(client)) break;
				require(!record(state, firstId).armed(), "Arming the bound target armed another owned drone");
				KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Z), true);
				advance(client);
			}
			case 14 -> {
				if (!settled(client) || Math.abs(record(state, targetId).yawInput()) < 0.05) break;
				require(!record(state, firstId).armed() && Math.abs(record(state, firstId).yawInput()) < 0.001, "Bound controller flight commands reached the other owned drone");
				report.addProperty("production_arm_and_keyboard_yaw_only_reach_bound_drone", true);
				KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Z), false);
				click(GLFW.GLFW_KEY_R);
				advance(client);
			}
			case 15 -> {
				if (record(state, targetId).armed() || DroneClientState.armed() || !settled(client)) break;
				poseFixture = true;
				click(GLFW.GLFW_KEY_B);
				advance(client);
			}
			case 16 -> {
				if (!remoteReady(client, state, DroneViewMode.FPV) || !settled(client) || !stableRenderWindow()
						|| bodyExtractions < 4 || bodySubmissions < 4) break;
				requireRemoteBodyVisible(client);
				report.addProperty("fpv_extracts_and_submits_real_pilot_body_with_native_player_model", true);
				capture(client, "pilot-body-visible-fpv", "fpv_actual_pilot_body");
				advance(client);
			}
			case 17 -> { if (doneScreenshot()) { click(GLFW.GLFW_KEY_B); advance(client); } }
			case 18 -> {
				if (!remoteReady(client, state, DroneViewMode.THIRD_PERSON) || !settled(client) || !stableRenderWindow()
						|| bodyExtractions < 4 || bodySubmissions < 4) break;
				requireRemoteBodyVisible(client);
				report.addProperty("drone_third_person_extracts_and_submits_real_pilot_body", true);
				capture(client, "pilot-body-visible-drone-third-person", "drone_third_person_actual_pilot_body");
				advance(client);
			}
			case 19 -> { if (doneScreenshot()) { click(GLFW.GLFW_KEY_B); advance(client); } }
			case 20 -> {
				if (!losReady(client) || !settled(client) || !stableRenderWindow()) break;
				requireLosBodyHidden();
				require(vanillaHands >= 4, "LOS did not restore normal first-person hands");
				report.addProperty("los_first_person_restores_hands_without_rendering_own_body", true);
				capture(client, "pilot-los-restored-first-person", "los_first_person_restored");
				advance(client);
			}
			case 21 -> {
				if (!doneScreenshot()) break;
				poseFixture = false;
				preparePersistence(client);
				advance(client);
			}
			case 22 -> {
				if (!doneOperation() || !targetId.equals(DroneControllerItem.boundDroneId(client.player.getInventory().getItem(PERSIST_SLOT)))
						|| client.player.position().distanceTo(playerHome) > 1.2 || !settled(client)) break;
				// Virtual control deliberately retains a valid prior target. This diagnostic
				// fixture must release its selection before the previous ThreeView fixture
				// enters its own original drone for the native disconnect/save checks.
				DroneClientState.resetTransientFlightState();
				complete = true;
				active = null;
				report.addProperty("diagnostic_remote_session_and_cached_selection_released_before_prior_fixture_resume", true);
				report.addProperty("fixture_restored_and_bound_controller_preserved_for_native_save", true);
			}
			default -> throw new IllegalStateException("Unknown placement/binding stage " + stage);
		}
		return complete;
	}

	public void serverTick(MinecraftServer server) {
		if (!started || complete || server.getPlayerList().getPlayers().isEmpty()) return;
		require(server.isSameThread(), "Placement/binding world read ran off the server thread");
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		if (observerHome == null) return;
		List<DroneEntity> drones = server.overworld().getEntitiesOfClass(DroneEntity.class, new AABB(observerHome, observerHome).inflate(12), drone -> !drone.isRemoved());
		if (poseFixture && server.overworld().getEntity(targetId) instanceof DroneEntity drone) {
			drone.setPos(cameraHome);
			drone.setDeltaMovement(Vec3.ZERO);
			Vec3 direction = observerHome.add(0, 0.95, 0).subtract(cameraHome);
			float yaw = (float) Math.atan2(-direction.x(), direction.z());
			float pitch = (float) Math.toRadians(DroneClientControls.config().cameraTiltDegrees() + Math.toDegrees(Math.atan2(-direction.y(), Math.hypot(direction.x(), direction.z()))));
			drone.getEntityData().set(PITCH, pitch);
			drone.getEntityData().set(YAW, yaw);
			drone.getEntityData().set(ROLL, 0f);
		}
		snapshot = new Snapshot(player.position(), player.onGround(), player.getCamera() == player, DroneRemoteSessions.isRemoteView(player),
				DroneRemoteSessions.trackedDrone(player) == null ? null : DroneRemoteSessions.trackedDrone(player).getUUID(),
				DroneControllerItem.boundDroneId(player.getMainHandItem()), player.getMainHandItem().getCount(),
				drones.stream().map(drone -> new DroneRecord(drone.getUUID(), drone.getOwner(), drone.position(), drone.isArmed(), drone.getControlYaw(), drone.fixedFlightModelIdForDiagnostics())).toList());
	}

	public static void onPilotExtracted(Entity entity, EntityRenderState state) {
		DronePlacementBindingSmoke smoke = active;
		Minecraft client = Minecraft.getInstance();
		if (smoke != null && entity == client.player && state instanceof AvatarRenderState avatar && avatar.id == entity.getId()) {
			smoke.bodyExtractions++;
			smoke.observedBody = avatar;
		}
	}
	public static void onPilotSubmitted(LivingEntityRenderState state) {
		DronePlacementBindingSmoke smoke = active;
		Minecraft client = Minecraft.getInstance();
		if (smoke != null && client.player != null && state instanceof AvatarRenderState avatar && avatar.id == client.player.getId()) smoke.bodySubmissions++;
	}
	public static void onHandPass() { if (active != null) active.handPasses++; }
	public static void onVanillaHands() { if (active != null) active.vanillaHands++; }

	/** Executed only on the normal server thread after native shutdown and world reopening. */
	public boolean verifyReopened(MinecraftServer server) {
		if (!complete || targetId == null) return false;
		server.overworld().getChunk(BlockPos.containing(cameraHome));
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		boolean binding = targetId.equals(DroneControllerItem.boundDroneId(player.getInventory().getItem(PERSIST_SLOT)));
		boolean target = server.overworld().getEntity(targetId) instanceof DroneEntity drone && ownerId.equals(drone.getOwner()) && modelId.equals(drone.fixedFlightModelIdForDiagnostics());
		boolean peer = server.overworld().getEntity(firstId) instanceof DroneEntity drone && ownerId.equals(drone.getOwner());
		report.addProperty("native_saved_controller_binding_uuid_persisted", binding);
		report.addProperty("native_saved_bound_drone_uuid_owner_and_model_persisted", target);
		report.addProperty("native_saved_other_owned_drone_uuid_persisted", peer);
		return binding && target && peer;
	}
	public boolean verifyClientReopened(Minecraft client) {
		boolean synchronizedBinding = client.player != null && targetId.equals(DroneControllerItem.boundDroneId(client.player.getInventory().getItem(PERSIST_SLOT)));
		if (!synchronizedBinding) return false;
		require(DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT && client.getCameraEntity() == client.player, "World reopen restored a stale drone camera");
		reopened = true;
		report.addProperty("reopened_client_receives_same_bound_controller_uuid_and_player_los_camera", true);
		return true;
	}
	public boolean passed() { return complete && setupRestored && reopened && screenshots.size() == 5; }
	public JsonObject summary() { report.addProperty("passed", passed()); report.addProperty("final_stage", stage); report.add("screenshots", screenshots); return report; }
	public void writeReport(boolean enclosingPassed, String failure) throws IOException {
		if (directory == null) return;
		JsonObject result = summary();
		result.addProperty("enclosing_probe_passed", enclosingPassed);
		result.addProperty("failure", failure);
		Files.writeString(directory.resolve("placement-binding-smoke-report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n", StandardCharsets.UTF_8);
	}
	public void restore(Minecraft client) {
		if (!started || restored) return;
		active = null;
		poseFixture = false;
		client.options.keyShift.setDown(previousShift);
		KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Z), false);
		client.options.setCameraType(previousCameraType);
		client.options.bobView().set(previousBobView);
		client.options.hideGui = previousHideGui;
		if (client.getSingleplayerServer() != null) serverOperation(client, server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			if (!setupRestored) restorePlayer(player);
			if (previousPersist != null) player.getInventory().setItem(PERSIST_SLOT, previousPersist.copy());
			player.inventoryMenu.broadcastChanges();
		});
		restored = true;
	}
	private void preparePersistence(Minecraft client) {
		client.options.keyShift.setDown(previousShift);
		client.options.setCameraType(previousCameraType);
		client.options.bobView().set(previousBobView);
		client.options.hideGui = previousHideGui;
		operation = serverOperation(client, server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			require(targetId.equals(DroneControllerItem.boundDroneId(player.getMainHandItem())), "Server main-hand controller lost the exact binding before native save");
			player.getInventory().setItem(PERSIST_SLOT, player.getMainHandItem().copy());
			DroneRemoteSessions.end(player);
			restorePlayer(player);
			player.inventoryMenu.broadcastChanges();
		});
		setupRestored = true;
	}
	private void restorePlayer(ServerPlayer player) {
		player.getInventory().setSelectedSlot(previousSelected);
		if (previousMain != null) player.setItemInHand(InteractionHand.MAIN_HAND, previousMain.copy());
		if (previousOff != null) player.setItemInHand(InteractionHand.OFF_HAND, previousOff.copy());
		if (previousGameType != null) player.setGameMode(previousGameType);
		player.setNoGravity(false);
		player.getAbilities().flying = false;
		player.onUpdateAbilities();
		player.setDeltaMovement(Vec3.ZERO);
		player.connection.teleport(new PositionMoveRotation(playerHome, Vec3.ZERO, 0, 0), Set.of());
	}
	private static void place(Minecraft client, BlockPos floor) {
		Vec3 point = Vec3.atCenterOf(floor).add(0, 0.5, 0);
		lookAt(client, point);
		client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, new BlockHitResult(point, Direction.UP, floor, false));
	}
	private static void interact(Minecraft client, UUID id, boolean shifted) {
		DroneEntity drone = clientDrone(client, id);
		require(client.player.isShiftKeyDown() == shifted, "Entity interaction did not have the actual requested keyboard shift state");
		lookAt(client, drone.getBoundingBox().getCenter());
		var ray = client.player.raycastHitResult(1, client.player);
		require(ray instanceof EntityHitResult hit && hit.getEntity() == drone, "Native player raycast did not target the intended drone: result=" + ray.getType()
				+ ", eye=" + client.player.getEyePosition() + ", targetCenter=" + drone.getBoundingBox().getCenter()
				+ ", centerDistance=" + client.player.getEyePosition().distanceTo(drone.getBoundingBox().getCenter()) + ", hit=" + ray.getLocation());
		EntityHitResult hit = (EntityHitResult) ray;
		InteractionResult result = client.gameMode.interactAt(client.player, drone, hit, InteractionHand.MAIN_HAND);
		if (!result.consumesAction()) client.gameMode.interact(client.player, drone, InteractionHand.MAIN_HAND);
	}
	private static DroneEntity clientDrone(Minecraft client, UUID id) {
		return client.level.getEntitiesOfClass(DroneEntity.class, client.player.getBoundingBox().inflate(40), drone -> id.equals(drone.getUUID())).stream().findFirst().orElseThrow(() -> new IllegalStateException("Client did not receive placed drone " + id));
	}
	private boolean bound(Minecraft client, Snapshot state, UUID id) {
		return id.equals(state.binding()) && id.equals(DroneControllerItem.boundDroneId(client.player.getMainHandItem()))
				&& DroneClientState.controlledDrone() != null && id.equals(DroneClientState.controlledDrone().getUUID());
	}
	private boolean remoteReady(Minecraft client, Snapshot state, DroneViewMode mode) {
		return bound(client, state, targetId) && DroneClientState.viewMode() == mode && DroneClientState.isDroneCameraActive(client.level)
				&& client.getCameraEntity() == DroneClientState.controlledDrone() && state.nativeCameraSelf() && state.remoteView() && targetId.equals(state.sessionDrone());
	}
	private boolean losReady(Minecraft client) { return DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT && client.getCameraEntity() == client.player && !DroneClientState.isDroneCameraActive(client.level); }
	private static boolean held(Minecraft client, net.minecraft.world.item.Item item, int count) { return client.player.getMainHandItem().is(item) && client.player.getMainHandItem().getCount() == count; }
	private static DroneRecord record(Snapshot state, UUID id) { return state.drones().stream().filter(drone -> drone.id().equals(id)).findFirst().orElseThrow(() -> new IllegalStateException("Server lost placed drone " + id)); }
	private void requireLosBodyHidden() { require(bodyExtractions == 0 && bodySubmissions == 0, "Normal player first-person view rendered the real pilot body"); }
	private void requireRemoteBodyVisible(Minecraft client) {
		require(bodyExtractions >= 4 && bodySubmissions >= 4 && observedBody != null && !observedBody.isInvisible, "Remote view did not render the visible real player model");
		require(handPasses >= 4 && vanillaHands == 0, "Remote view reached the first-person hands/held-items renderer: view=" + DroneClientState.viewMode()
				+ ", nativeHandPasses=" + handPasses + ", vanillaHands=" + vanillaHands
				+ ", bodyExtractions=" + bodyExtractions + ", bodySubmissions=" + bodySubmissions);
		require(snapshot.body().distanceTo(bodyBaseline) < 0.25 && client.player.position().distanceTo(bodyBaseline) < 0.25, "Remote camera moved the physical pilot body");
		require(client.player.getMainHandItem().is(DroneItems.DRONE_CONTROLLER) && client.player.getOffhandItem().is(Items.SHIELD), "Pilot body lost its synchronized real held items");
		Vec3 toBody = client.player.position().add(0, 0.9, 0).subtract(client.gameRenderer.getMainCamera().position()).normalize();
		require(toBody.dot(new Vec3(client.gameRenderer.getMainCamera().forwardVector())) > 0.75, "Remote camera was not facing the original pilot body");
	}
	private static void lookAt(Minecraft client, Vec3 point) { Vec3 delta = point.subtract(client.player.getEyePosition()); client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x(), delta.z()))); client.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y(), Math.hypot(delta.x(), delta.z())))); }
	private boolean settled(Minecraft client) { return client.player.tickCount - stageTick >= 12; }
	private void advance(Minecraft client) { stage++; stageTick = client.player.tickCount; stageStarted = System.nanoTime(); renderWindowStarted = false; resetRenderCounters(); }
	private void resetRenderCounters() { bodyExtractions = bodySubmissions = handPasses = vanillaHands = 0; observedBody = null; }
	private boolean stableRenderWindow() {
		if (!renderWindowStarted) {
			// A queued B press is consumed on the next control tick. Start observations
			// only after the requested native camera has settled, excluding transition frames.
			resetRenderCounters();
			renderWindowStarted = true;
			return false;
		}
		return handPasses >= 4;
	}
	private boolean doneOperation() { if (operation == null || !operation.isDone()) return false; operation.join(); return true; }
	private boolean doneScreenshot() { if (screenshot == null || !screenshot.isDone()) return false; if (!screenshotRecorded) { screenshots.add(screenshot.join()); screenshotRecorded = true; } return true; }
	private void capture(Minecraft client, String name, String view) {
		JsonObject image = new JsonObject();
		image.addProperty("view", view);
		image.addProperty("real_player_body_extractions", bodyExtractions);
		image.addProperty("real_player_body_submissions", bodySubmissions);
		image.addProperty("native_hand_passes", handPasses);
		image.addProperty("vanilla_first_person_hands_calls", vanillaHands);
		image.addProperty("camera_entity_is_bound_drone", client.getCameraEntity() == DroneClientState.controlledDrone() && DroneClientState.controlledDrone() != null);
		image.addProperty("server_camera_entity_is_player", snapshot.nativeCameraSelf());
		image.addProperty("body_displacement_meters", snapshot.body().distanceTo(bodyBaseline));
		image.addProperty("terrain_render_complete", client.levelRenderer.hasRenderedAllSections());
		Path path = directory.resolve("screenshots/" + name + ".png");
		image.addProperty("screenshot", path.toString());
		CompletableFuture<String> saved = new CompletableFuture<>();
		screenshotRecorded = false;
		int width = client.getWindow().getWidth(), height = client.getWindow().getHeight();
		screenshot = saved.orTimeout(15, TimeUnit.SECONDS).thenApplyAsync(message -> {
			try { byte[] bytes = Files.readAllBytes(path); require(bytes.length >= 24 && ByteBuffer.wrap(bytes).getLong() == 0x89504e470d0a1a0aL, "Placement screenshot is not PNG"); require(ByteBuffer.wrap(bytes, 16, 4).getInt() == width && ByteBuffer.wrap(bytes, 20, 4).getInt() == height, "Placement screenshot dimensions mismatch"); image.addProperty("png_verified", true); return image; }
			catch (IOException error) { throw new IllegalStateException("Could not read placement/binding screenshot", error); }
		});
		Screenshot.grab(directory.toFile(), path.getFileName().toString(), client.getMainRenderTarget(), 1, message -> saved.complete(message.getString()));
	}
	private static CompletableFuture<Boolean> serverOperation(Minecraft client, java.util.function.Consumer<MinecraftServer> task) { CompletableFuture<Boolean> result = new CompletableFuture<>(); MinecraftServer server = client.getSingleplayerServer(); server.execute(() -> { try { require(server.isSameThread(), "Placement/binding world operation ran off server thread"); task.accept(server); result.complete(true); } catch (Throwable error) { result.completeExceptionally(error); } }); return result; }
	@SuppressWarnings("unchecked")
	private static EntityDataAccessor<Float> attitudeAccessor(String name) { try { Field field = DroneEntity.class.getDeclaredField(name); field.setAccessible(true); return (EntityDataAccessor<Float>) field.get(null); } catch (ReflectiveOperationException error) { throw new IllegalStateException("Missing diagnostic drone attitude accessor", error); } }
	private static void click(int key) { KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(key)); }
	private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
	private record DroneRecord(UUID id, UUID owner, Vec3 position, boolean armed, float yawInput, String model) { }
	private record Snapshot(Vec3 body, boolean grounded, boolean nativeCameraSelf, boolean remoteView, UUID sessionDrone, UUID binding, int mainCount, List<DroneRecord> drones) { }
}
