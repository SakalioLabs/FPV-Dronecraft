package com.tenicana.dronecraft.performance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

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
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Opt-in physical-pilot and remote-region checks on an ordinary disposable-world client. */
public final class RemotePilotSmoke {
	public static final boolean ENABLED = Boolean.getBoolean("fpvdrone.performance.remotePilotSmoke");
	public static final boolean PHYSICAL_CONTROLLER = Boolean.getBoolean("fpvdrone.performance.remotePilotPhysicalController");
	private static final String[] STAGES = {
		"setup", "enable_control", "enter_fpv", "stationary_pilot", "move_640_meters", "far_region_loaded",
		"far_entity_visible", "first_chunk_crossing", "second_chunk_crossing", "far_third_person", "far_los",
		"reattach_same_far_drone", "real_husk_attack", "attack_screenshot", "remove_pilot_floor", "physical_fall",
		"return_to_fallen_pilot", "enter_fpv_for_death", "actual_mob_death", "death_screenshot", "respawn", "restore_fixture", "complete"
	};
	private final JsonObject report = new JsonObject();
	private final JsonObject checks = new JsonObject();
	private final JsonArray screenshots = new JsonArray();
	private final JsonArray crossings = new JsonArray();
	private final List<BlockPos> platform = new ArrayList<>();
	private volatile Snapshot snapshot;
	private volatile boolean started, complete, stoppedClean;
	private volatile UUID droneId, chickenId, huskId;
	private volatile BlockPos marker;
	private volatile BlockPos losMarker;
	private volatile Vec3 routeDestination;
	private volatile int routeCompletedTick;
	private int losRouteStage;
	private UUID ownerId;
	private Vec3 droneHome, playerHome, bodyHome, farHome, fallStart;
	private Path runDirectory;
	private CameraType previousCameraType;
	private boolean restored, disconnected, reopened, screenshotRecorded;
	private int stage, stageServerTick, previousDroneTick, previousClientDroneTick, firstChickenTick, disconnectStage;
	private long stageStarted;
	private CompletableFuture<Boolean> operation;
	private CompletableFuture<JsonObject> screenshot;
	private double maximumStationaryDisplacement, maximumKnockback;
	private volatile boolean fixtureMobDamagedPilot;
	private volatile Vec3 attackOrigin;
	private volatile ChunkPos lastDroneChunk, reloadChunk;
	private volatile boolean temporaryForceAdded, reloadRequested, reloadAbsentAtStart, sameUuidRecovered, reloadForceCleared;
	private volatile int reloadStartTick, reloadFinishTick;
	private volatile CompletableFuture<Boolean> reloadFuture;
	private boolean respawnRestorationStarted;
	private boolean physicalControllerChecked;
	private long physicalBindingChecks;
	private int previousSelectedSlot;
	private ItemStack previousMainHand, previousOffHand;

	public void start(Minecraft client, Path directory, UUID id, Vec3 home, Vec3 pilotHome) {
		require(!started, "Remote pilot smoke started twice");
		started = true;
		droneId = id;
		ownerId = client.player.getUUID();
		droneHome = home;
		playerHome = pilotHome;
		bodyHome = pilotHome.add(0, 4, 0);
		farHome = home.add(640, 4, 0);
		runDirectory = directory;
		previousCameraType = client.options.getCameraType();
		client.options.setCameraType(CameraType.FIRST_PERSON);
		report.addProperty("enabled", true);
		report.addProperty("physical_controller_requested", PHYSICAL_CONTROLLER);
		report.addProperty("physical_controller_target_uuid", PHYSICAL_CONTROLLER ? id.toString() : null);
		report.addProperty("body_repositioning", "one initial platform placement, then no body position writes until cleanup after death and respawn");
		report.addProperty("remote_route", "continuous diagnostic pose advance of 8m per server tick: 640m in FPV, two further chunk crossings, then another five chunks in LOS; no destination getChunk preload");
		report.addProperty("damage", "real Husk target AI and native melee damage; no direct damage calls");
		operation = submit(client, server -> {
			ServerPlayer player = player(server);
			ServerLevel level = server.overworld();
			server.setDifficulty(Difficulty.NORMAL, true);
			player.setGameMode(GameType.SURVIVAL);
			player.setInvulnerable(false);
			player.setNoGravity(false);
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
			player.setHealth(20);
			player.getFoodData().setFoodLevel(20);
			if (PHYSICAL_CONTROLLER) {
				previousSelectedSlot = player.getInventory().getSelectedSlot();
				previousMainHand = player.getMainHandItem().copy();
				previousOffHand = player.getOffhandItem().copy();
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneItems.DRONE_CONTROLLER));
				player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
				DroneEntity target = drone(server);
				// Placement/binding smoke proves native interaction. This separate long-route
				// fixture starts with the same validated API and then keeps the real stack.
				target.setPos(player.position().add(0, 0, 2));
				player.setShiftKeyDown(true);
				try { require(DroneControllerItem.bindToDrone(player, InteractionHand.MAIN_HAND, target), "Could not bind the physical long-route fixture controller"); }
				finally { player.setShiftKeyDown(false); target.setPos(droneHome); target.setDeltaMovement(Vec3.ZERO); }
				player.inventoryMenu.broadcastChanges();
			}
			BlockPos floor = BlockPos.containing(bodyHome).below();
			for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
				BlockPos pos = floor.offset(x, 0, z);
				platform.add(pos);
				level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
				level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
				level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 3);
			}
			player.setDeltaMovement(Vec3.ZERO);
			player.connection.teleport(new PositionMoveRotation(bodyHome, Vec3.ZERO, player.getYRot(), player.getXRot()), Set.of());
		});
		stageStarted = System.nanoTime();
	}

	public boolean tick(Minecraft client) {
		if (complete) return true;
		require(started, "Remote pilot smoke was not initialized");
		Snapshot state = snapshot;
		if (System.nanoTime() - stageStarted > 40_000_000_000L)
			throw new IllegalStateException("Remote pilot smoke timed out at " + STAGES[stage] + "; last server snapshot=" + state);
		if (state == null || client.player == null || client.level == null) return false;
		require(state.nativeCameraSelf(), "Server camera left the physical player at " + STAGES[stage]);
		if (PHYSICAL_CONTROLLER && physicalControllerChecked && stage <= 18 && state.alive() && client.player.isAlive()) {
			require(physicalControllerReady(client, state), "Physical controller binding changed during " + STAGES[stage]);
			require(!DroneClientState.isVirtualControllerEnabled(), "Physical-controller route unexpectedly relied on virtual control");
			physicalBindingChecks++;
		}
		if (stage >= 3 && stage <= 11) {
			maximumStationaryDisplacement = Math.max(maximumStationaryDisplacement, state.body().distanceTo(bodyHome));
			require(maximumStationaryDisplacement < 0.35, "Physical pilot followed the drone before any damage: " + state.body());
		}
		switch (stage) {
			case 0 -> {
				if (!doneOperation() || state.body().distanceTo(bodyHome) > 0.15 || client.player.position().distanceTo(bodyHome) > 0.3) break;
				if (PHYSICAL_CONTROLLER && !physicalControllerReady(client, state)) break;
				if (DroneClientControls.config().gamepadEnabled()) click(GLFW.GLFW_KEY_G);
				if (PHYSICAL_CONTROLLER ? DroneClientState.isVirtualControllerEnabled() : !DroneClientState.isVirtualControllerEnabled()) click(GLFW.GLFW_KEY_V);
				advance();
			}
			case 1 -> {
				if ((PHYSICAL_CONTROLLER ? !physicalControllerReady(client, state) || DroneClientState.isVirtualControllerEnabled() : !DroneClientState.isVirtualControllerEnabled())
						|| DroneClientControls.config().gamepadEnabled() || !sameSession(state)) break;
				if (PHYSICAL_CONTROLLER) {
					physicalControllerChecked = true;
					report.addProperty("physical_controller_binding", true);
					check("real_bound_controller_uuid_syncs_before_remote_route_with_virtual_control_disabled");
				}
				require(!state.remote(), "Fresh pilot fixture unexpectedly started remotely");
				click(GLFW.GLFW_KEY_B);
				advance();
			}
			case 2 -> {
				if (!remoteReady(client, state, DroneViewMode.FPV) || !settled(12)) break;
				check("fpv_keeps_survival_pilot_at_original_position");
				capture(client, "remote-pilot-fpv-body-separate");
				advance();
			}
			case 3 -> {
				if (!doneScreenshot()) break;
				previousDroneTick = state.droneTick();
				previousClientDroneTick = DroneClientState.controlledDrone().tickCount;
				operation = submit(client, server -> routeDestination = farHome);
				advance();
			}
			case 4 -> {
				if (!doneOperation() || !farReady(client, state, farHome) || state.droneTick() - previousDroneTick < 8) break;
				require(DroneClientState.controlledDrone().tickCount > previousClientDroneTick, "Remote client drone tick stopped during the continuous 640m route");
				check("unprepared_region_640m_away_loaded_and_drone_ticks_continue");
				operation = submit(client, server -> {
					ServerLevel level = server.overworld();
					int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) farHome.x(), (int) farHome.z());
					marker = new BlockPos((int) farHome.x(), surface, (int) farHome.z() + 6);
					level.setBlock(marker, Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
					Chicken chicken = EntityType.CHICKEN.create(level, EntitySpawnReason.COMMAND);
					require(chicken != null, "Could not create remote region chicken");
					chicken.setPersistenceRequired();
					chicken.setPos(farHome.x() + 2, surface, farHome.z() + 3);
					require(level.addFreshEntity(chicken), "Could not spawn remote region chicken");
					chickenId = chicken.getUUID();
				});
				advance();
			}
			case 5 -> {
				if (!doneOperation() || marker == null || !client.level.getBlockState(marker).is(Blocks.EMERALD_BLOCK) || clientChicken(client) == null || state.chickenTick() < 8) break;
				firstChickenTick = state.chickenTick();
				check("remote_block_update_and_entity_pairing_reach_client");
				capture(client, "remote-pilot-far-fpv");
				advance();
			}
			case 6 -> {
				if (!doneScreenshot()) break;
				previousDroneTick = state.droneTick();
				operation = submit(client, server -> {
					server.overworld().setBlock(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
					routeDestination = farHome.add(16, 0, 0);
				});
				advance();
			}
			case 7 -> {
				if (!doneOperation() || !farReady(client, state, farHome.add(16, 0, 0)) || state.droneTick() - previousDroneTick < 8) break;
				crossings.add(checkpoint(state));
				previousDroneTick = state.droneTick();
				operation = submit(client, server -> routeDestination = farHome.add(32, 0, 0));
				advance();
			}
			case 8 -> {
				if (!doneOperation() || !farReady(client, state, farHome.add(32, 0, 0)) || state.droneTick() - previousDroneTick < 8
						|| !client.level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK) || clientChicken(client) == null) break;
				require(state.chickenTick() > firstChickenTick + 8, "Entities in the far simulation region stopped ticking");
				crossings.add(checkpoint(state));
				check("two_further_chunk_crossings_keep_drone_and_remote_entities_updating");
				click(GLFW.GLFW_KEY_B);
				advance();
			}
			case 9 -> {
				if (!remoteReady(client, state, DroneViewMode.THIRD_PERSON) || !settled(8)) break;
				require(client.gameRenderer.getMainCamera().isDetached(), "Remote third-person camera was not detached");
				check("third_person_keeps_pilot_in_its_original_chunks");
				capture(client, "remote-pilot-far-third-person");
				advance();
			}
			case 10 -> {
				if (!doneScreenshot()) break;
				click(GLFW.GLFW_KEY_B);
				advance();
			}
			case 11 -> {
				if (!losReady(client, state) || !sameSession(state) || !settled(8)) break;
				require(client.player.position().distanceTo(state.body()) < 0.3, "LOS returned to an incorrect physical player position");
				Vec3 destination = farHome.add(112, 0, 0);
				if (losRouteStage == 0) {
					previousDroneTick = state.droneTick();
					previousClientDroneTick = DroneClientState.controlledDrone().tickCount;
					operation = submit(client, server -> routeDestination = destination);
					losRouteStage = 1;
					break;
				}
				if (losRouteStage == 1) {
					if (!doneOperation() || !regionReady(client, state, destination, DroneViewMode.LINE_OF_SIGHT)) break;
					operation = submit(client, server -> {
						ServerLevel level = server.overworld();
						int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) destination.x(), (int) destination.z());
						losMarker = new BlockPos((int) destination.x(), surface, (int) destination.z() + 6);
						level.setBlock(losMarker, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
					});
					losRouteStage = 2;
					break;
				}
				if (!doneOperation() || losMarker == null || !client.level.getBlockState(losMarker).is(Blocks.REDSTONE_BLOCK)
						|| !regionReady(client, state, destination, DroneViewMode.LINE_OF_SIGHT)) break;
				require(state.droneTick() > previousDroneTick + 8 && DroneClientState.controlledDrone().tickCount > previousClientDroneTick,
						"Remote target stopped updating while the LOS camera stayed at the pilot");
				crossings.add(checkpoint(state));
				check("los_five_more_chunk_crossings_stream_secondary_region_and_tick_same_drone");
				check("los_retains_same_remote_drone_session_beyond_512m");
				if (PHYSICAL_CONTROLLER) check("physical_controller_uuid_retains_exact_target_through_640m_and_seven_further_chunk_crossings");
				click(GLFW.GLFW_KEY_B);
				advance();
			}
			case 12 -> {
				if (!remoteReady(client, state, DroneViewMode.FPV) || !settled(8)) break;
				check("los_fpv_third_los_fpv_reattaches_same_far_drone");
				operation = submit(client, server -> { attackOrigin = player(server).position(); spawnHusk(server); });
				advance();
			}
			case 13 -> {
				if (!doneOperation()) break;
				maximumKnockback = Math.max(maximumKnockback, horizontalDistance(state.body(), attackOrigin));
				if (!fixtureMobDamagedPilot || state.health() >= 20 || state.huskTick() < 8 || maximumKnockback < 0.08) break;
				require(state.alive() && remoteReady(client, state, DroneViewMode.FPV), "Mob attack unexpectedly ended a living pilot's remote view");
				check("real_husk_ai_damages_and_knocks_back_remote_survival_pilot");
				report.addProperty("health_after_real_melee", state.health());
				report.addProperty("measured_knockback_horizontal_meters", maximumKnockback);
				capture(client, "remote-pilot-fpv-real-mob-damage");
				advance();
			}
			case 14 -> {
				if (!doneScreenshot()) break;
				fallStart = state.body();
				operation = submit(client, server -> {
					discard(server, huskId);
					huskId = null;
					platform.forEach(pos -> server.overworld().setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
				});
				advance();
			}
			case 15 -> {
				if (!doneOperation() || state.body().y() >= fallStart.y() - 2.5 || !settled(12)) break;
				require(state.alive() && remoteReady(client, state, DroneViewMode.FPV), "Removing the pilot floor broke remote control or killed the fixture");
				check("physical_pilot_falls_normally_when_original_floor_is_removed");
				report.addProperty("physical_fall_meters", fallStart.y() - state.body().y());
				click(GLFW.GLFW_KEY_B);
				click(GLFW.GLFW_KEY_B);
				advance();
			}
			case 16 -> {
				if (!losReady(client, state) || !settled(8) || client.player.position().distanceTo(state.body()) > 0.35) break;
				check("los_returns_to_real_body_after_knockback_and_fall");
				capture(client, "remote-pilot-los-after-physical-fall");
				advance();
			}
			case 17 -> {
				if (!doneScreenshot()) break;
				click(GLFW.GLFW_KEY_B);
				advance();
			}
			case 18 -> {
				if (!remoteReady(client, state, DroneViewMode.FPV) || !settled(8)) break;
				operation = submit(client, server -> { player(server).setHealth(1); spawnHusk(server); });
				advance();
			}
			case 19 -> {
				if (!doneOperation() || state.alive() || state.sessions() != 0 || state.tickets() != 0 || !losReady(client, state)) break;
				require(fixtureMobDamagedPilot, "Death had no damage evidence from the fixture mob");
				check("actual_mob_death_resets_client_los_and_releases_session_and_tickets");
				capture(client, "remote-pilot-death-player-camera");
				advance();
			}
			case 20 -> {
				if (!doneScreenshot() || !doneOperation()) break;
				operation = submit(client, server -> { discard(server, huskId); huskId = null; });
				client.player.respawn();
				advance();
			}
			case 21 -> {
				if (!doneOperation() || !state.alive() || !client.player.isAlive()) break;
				if (!respawnRestorationStarted) {
					client.setScreen(null);
					if (DroneClientState.isVirtualControllerEnabled()) click(GLFW.GLFW_KEY_V);
					operation = reloadOriginalFixture(client);
					respawnRestorationStarted = true;
					break;
				}
				require(sameUuidRecovered && reloadForceCleared && !temporaryForceAdded, "Respawn fixture reload did not recover the same UUID or retained its diagnostic forced chunk");
				check("same_far_drone_uuid_reloads_from_native_storage_after_death");
				check("diagnostic_cleanup_forced_chunk_is_released_before_fixture_restore");
				restore(client);
				advance();
			}
			case 22 -> {
				if (!doneOperation() || state.sessions() != 0 || state.tickets() != 0 || !losReady(client, state)) break;
				complete = true;
				check("respawn_and_fixture_cleanup_leave_no_remote_resources");
				if (PHYSICAL_CONTROLLER) {
					report.addProperty("physical_controller_binding_checks", physicalBindingChecks);
					check("physical_controller_fixture_inventory_and_native_drops_are_cleaned_after_death");
				}
				report.addProperty("maximum_unprovoked_body_displacement_meters", maximumStationaryDisplacement);
				report.addProperty("physical_pilot_checks_passed", true);
			}
			default -> throw new IllegalStateException("Unknown remote pilot stage " + stage);
		}
		return complete;
	}

	public void serverTick(MinecraftServer server) {
		if (!started || server.getPlayerList().getPlayers().isEmpty()) return;
		ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
		if (player == null) return;
		ServerLevel level = server.overworld();
		finishFixtureReload(server, level);
		Entity entity = level.getEntity(droneId), chicken = chickenId == null ? null : level.getEntity(chickenId), husk = huskId == null ? null : level.getEntity(huskId);
		DroneEntity drone = entity instanceof DroneEntity current ? current : null;
		Vec3 destination = routeDestination;
		if (destination != null && drone != null) {
			Vec3 current = drone.position();
			double distance = horizontalDistance(current, destination);
			if (distance <= 8) {
				drone.setPos(destination);
				routeDestination = null;
				routeCompletedTick = server.getTickCount();
			} else {
				drone.setPos(current.x() + (destination.x() - current.x()) * 8 / distance, destination.y(), current.z() + (destination.z() - current.z()) * 8 / distance);
			}
			drone.setDeltaMovement(Vec3.ZERO);
		}
		if (drone != null) lastDroneChunk = drone.chunkPosition();
		if (husk != null && player.getLastDamageSource() != null && player.getLastDamageSource().getEntity() == husk) fixtureMobDamagedPilot = true;
		boolean loaded = drone != null && level.getChunkSource().hasChunk(drone.chunkPosition().x, drone.chunkPosition().z);
		boolean ticking = drone != null && level.getChunkSource().chunkMap.getDistanceManager().inEntityTickingRange(drone.chunkPosition().toLong());
		DroneEntity tracked = DroneRemoteSessions.trackedDrone(player);
		snapshot = new Snapshot(server.getTickCount(), player.position(), player.getHealth(), player.isAlive(), player.getCamera() == player,
				tracked == null ? null : tracked.getUUID(), DroneRemoteSessions.isRemoteView(player), DroneRemoteSessions.sessionCount(server),
				DroneRemoteSessions.ticketCount(level), drone == null ? null : drone.position(), drone == null ? -1 : drone.tickCount,
				loaded, ticking, chicken == null ? -1 : chicken.tickCount, husk == null ? -1 : husk.tickCount,
				DroneControllerItem.boundDroneId(player));
	}

	public void serverStopped(MinecraftServer server) {
		if (!started) return;
		stoppedClean = DroneRemoteSessions.sessionCount(server) == 0;
		for (ServerLevel level : server.getAllLevels()) stoppedClean &= DroneRemoteSessions.ticketCount(level) == 0;
	}

	/** Always remove a diagnostic force before vanilla persists the disposable world's forced-chunk list. */
	public void serverStopping(MinecraftServer server) {
		if (!started) return;
		reloadRequested = false;
		releaseTemporaryForce(server.overworld());
		if (reloadFuture != null && !reloadFuture.isDone()) reloadFuture.completeExceptionally(new IllegalStateException("Server stopped before original drone UUID reloaded"));
	}

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
		return snapshot != null && remoteReady(client, snapshot, DroneViewMode.THIRD_PERSON);
	}

	public void verifyDisconnected(Minecraft client) {
		require(client.level == null && client.player == null && DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT
				&& DroneClientState.controlledDrone() == null && stoppedClean, "Disconnect retained remote camera/session/tickets");
		disconnected = true;
		check("disconnect_and_server_stop_release_all_remote_resources");
	}

	public void verifyReopened(Minecraft client) {
		require(DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT && client.getCameraEntity() == client.player,
				"Save/reopen restored a stale remote camera");
		reopened = true;
		check("saved_world_reopens_in_player_los_view");
	}

	public boolean passed() { return complete && restored && disconnected && reopened && screenshots.size() == 6 && sameUuidRecovered && reloadForceCleared && !temporaryForceAdded
			&& (!PHYSICAL_CONTROLLER || physicalControllerChecked && physicalBindingChecks >= 8); }
	public JsonObject summary() {
		report.addProperty("passed", passed());
		report.addProperty("last_stage", STAGES[stage]);
		report.add("checks", checks);
		report.add("screenshots", screenshots);
		report.add("chunk_crossing_checkpoints", crossings);
		JsonObject cleanup = new JsonObject();
		cleanup.addProperty("scope", "disposable-world cleanup only, after core death/session/ticket checks; native setChunkForced and UUID entity lookup, no replacement entity");
		cleanup.addProperty("original_drone_uuid", droneId == null ? null : droneId.toString());
		cleanup.addProperty("native_entity_absent_when_reload_started", reloadAbsentAtStart);
		cleanup.addProperty("same_uuid_recovered", sameUuidRecovered);
		cleanup.addProperty("temporary_force_still_present", temporaryForceAdded);
		cleanup.addProperty("temporary_force_released", reloadForceCleared);
		cleanup.addProperty("reload_start_server_tick", reloadStartTick);
		cleanup.addProperty("reload_finish_server_tick", reloadFinishTick);
		if (reloadChunk != null) { cleanup.addProperty("chunk_x", reloadChunk.x); cleanup.addProperty("chunk_z", reloadChunk.z); }
		report.add("native_death_fixture_reload", cleanup);
		return report;
	}
	public void writeReport(boolean enclosingPassed, String failure) throws IOException {
		if (runDirectory == null) return;
		JsonObject result = summary();
		result.addProperty("enclosing_probe_passed", enclosingPassed);
		result.addProperty("failure", failure);
		Files.writeString(runDirectory.resolve("remote-pilot-smoke-report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n", StandardCharsets.UTF_8);
	}

	public void restore(Minecraft client) {
		if (!started || restored) return;
		client.options.setCameraType(previousCameraType);
		if (client.getSingleplayerServer() != null) operation = submit(client, server -> {
			ServerLevel level = server.overworld();
			try {
			routeDestination = null;
			discard(server, huskId);
			discard(server, chickenId);
			huskId = null;
			chickenId = null;
			if (marker != null) level.setBlock(marker, Blocks.AIR.defaultBlockState(), 3);
			if (losMarker != null) level.setBlock(losMarker, Blocks.AIR.defaultBlockState(), 3);
			platform.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
			if (level.getEntity(droneId) instanceof DroneEntity drone) { drone.setPos(droneHome); drone.setDeltaMovement(Vec3.ZERO); }
			ServerPlayer player = player(server);
			if (PHYSICAL_CONTROLLER) {
				for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
					if (droneId.equals(DroneControllerItem.boundDroneId(player.getInventory().getItem(slot)))) player.getInventory().setItem(slot, ItemStack.EMPTY);
				}
				for (ItemEntity dropped : level.getEntitiesOfClass(ItemEntity.class, new AABB(bodyHome, bodyHome).inflate(16),
						item -> droneId.equals(DroneControllerItem.boundDroneId(item.getItem())))) dropped.discard();
				player.getInventory().setSelectedSlot(previousSelectedSlot);
				if (previousMainHand != null) player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand.copy());
				if (previousOffHand != null) player.setItemInHand(InteractionHand.OFF_HAND, previousOffHand.copy());
				player.inventoryMenu.broadcastChanges();
			}
			player.setGameMode(GameType.CREATIVE);
			player.setHealth(20);
			player.setNoGravity(false);
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
			player.setDeltaMovement(Vec3.ZERO);
			player.connection.teleport(new PositionMoveRotation(playerHome, Vec3.ZERO, 0, 0), Set.of());
			server.setDifficulty(Difficulty.PEACEFUL, true);
			} finally {
				reloadRequested = false;
				releaseTemporaryForce(level);
			}
		});
		restored = true;
	}

	private CompletableFuture<Boolean> reloadOriginalFixture(Minecraft client) {
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		reloadFuture = result;
		MinecraftServer server = client.getSingleplayerServer();
		require(server != null, "Fixture reload has no integrated server");
		server.execute(() -> {
			ServerLevel level = server.overworld();
			try {
				require(server.isSameThread(), "Fixture reload ran off the server thread");
				require(lastDroneChunk != null, "No final remote chunk was recorded for the original drone");
				reloadChunk = lastDroneChunk;
				reloadAbsentAtStart = level.getEntity(droneId) == null;
				reloadStartTick = server.getTickCount();
				// This diagnostic load starts only after death and respawn, never during the remote-flight checks.
				if (!level.getForceLoadedChunks().contains(reloadChunk.toLong())) {
					temporaryForceAdded = true;
					require(level.setChunkForced(reloadChunk.x, reloadChunk.z, true), "Could not temporarily force the original drone's saved chunk");
				}
				reloadRequested = true;
			} catch (Throwable failure) {
				releaseTemporaryForce(level);
				result.completeExceptionally(failure);
			}
		});
		return result;
	}

	private void finishFixtureReload(MinecraftServer server, ServerLevel level) {
		if (!reloadRequested) return;
		try {
			require(server.getTickCount() - reloadStartTick < 500, "Native entity storage did not reload original drone UUID " + droneId + " from chunk " + reloadChunk);
			// Yield at least two complete native ticks for entity-storage loading and tracking changes.
			if (server.getTickCount() - reloadStartTick < 2 || !(level.getEntity(droneId) instanceof DroneEntity drone)) return;
			require(droneId.equals(drone.getUUID()), "Native fixture reload returned a different drone UUID");
			drone.setPos(droneHome);
			drone.setDeltaMovement(Vec3.ZERO);
			sameUuidRecovered = true;
			reloadFinishTick = server.getTickCount();
			reloadRequested = false;
			releaseTemporaryForce(level);
			reloadFuture.complete(true);
		} catch (Throwable failure) {
			reloadRequested = false;
			releaseTemporaryForce(level);
			reloadFuture.completeExceptionally(failure);
		}
	}

	private void releaseTemporaryForce(ServerLevel level) {
		if (temporaryForceAdded && reloadChunk != null) {
			level.setChunkForced(reloadChunk.x, reloadChunk.z, false);
			temporaryForceAdded = false;
		}
		if (reloadChunk != null) reloadForceCleared = !level.getForceLoadedChunks().contains(reloadChunk.toLong());
	}

	private void spawnHusk(MinecraftServer server) {
		ServerPlayer player = player(server);
		Husk husk = EntityType.HUSK.create(server.overworld(), EntitySpawnReason.COMMAND);
		require(husk != null, "Could not create real damage fixture Husk");
		husk.setPersistenceRequired();
		husk.setPos(player.getX() + 1.3, player.getY(), player.getZ());
		husk.setTarget(player);
		require(server.overworld().addFreshEntity(husk), "Could not spawn real damage fixture Husk");
		huskId = husk.getUUID();
		fixtureMobDamagedPilot = false;
	}
	private boolean farReady(Minecraft client, Snapshot state, Vec3 expected) {
		return regionReady(client, state, expected, DroneViewMode.FPV);
	}
	private boolean regionReady(Minecraft client, Snapshot state, Vec3 expected, DroneViewMode view) {
		DroneEntity drone = DroneClientState.controlledDrone();
		return state.drone() != null && state.loaded() && state.ticking() && horizontalDistance(state.drone(), expected) < 0.5
				&& drone != null && droneId.equals(drone.getUUID()) && horizontalDistance(drone.position(), expected) < 0.7
				&& client.level.hasChunk(drone.chunkPosition().x, drone.chunkPosition().z)
				&& routeDestination == null && state.serverTick() - routeCompletedTick >= 8
				&& (view == DroneViewMode.LINE_OF_SIGHT ? losReady(client, state) && sameSession(state) : remoteReady(client, state, view));
	}
	private boolean remoteReady(Minecraft client, Snapshot state, DroneViewMode mode) {
		DroneEntity drone = DroneClientState.controlledDrone();
		return sameSession(state) && state.remote() && DroneClientState.viewMode() == mode && drone != null
				&& droneId.equals(drone.getUUID()) && client.getCameraEntity() == drone && DroneClientState.isDroneCameraActive(client.level);
	}
	private boolean losReady(Minecraft client, Snapshot state) {
		return !state.remote() && DroneClientState.viewMode() == DroneViewMode.LINE_OF_SIGHT && client.getCameraEntity() == client.player;
	}
	private boolean sameSession(Snapshot state) { return droneId.equals(state.trackedDrone()); }
	private boolean physicalControllerReady(Minecraft client, Snapshot state) {
		return client.player.getMainHandItem().is(DroneItems.DRONE_CONTROLLER) && DroneClientState.hasPhysicalController()
				&& droneId.equals(DroneControllerItem.boundDroneId(client.player.getMainHandItem())) && droneId.equals(state.controllerBinding());
	}
	private boolean settled(int ticks) { return snapshot.serverTick() - stageServerTick >= ticks; }
	private void advance() { stage++; stageServerTick = snapshot.serverTick(); stageStarted = System.nanoTime(); }
	private void check(String name) { checks.addProperty(name, true); }
	private boolean doneOperation() { if (operation == null || !operation.isDone()) return false; operation.join(); return true; }
	private Chicken clientChicken(Minecraft client) {
		if (chickenId == null) return null;
		return client.level.getEntitiesOfClass(Chicken.class, new AABB(farHome, farHome).inflate(64), entity -> chickenId.equals(entity.getUUID())).stream().findFirst().orElse(null);
	}
	private ServerPlayer player(MinecraftServer server) { return server.getPlayerList().getPlayer(ownerId); }
	private DroneEntity drone(MinecraftServer server) {
		Entity entity = server.overworld().getEntity(droneId);
		require(entity instanceof DroneEntity, "Original persistence fixture drone disappeared");
		return (DroneEntity) entity;
	}
	private static void discard(MinecraftServer server, UUID id) { if (id != null) { Entity entity = server.overworld().getEntity(id); if (entity != null) entity.discard(); } }
	private static double horizontalDistance(Vec3 first, Vec3 second) { return Math.hypot(first.x() - second.x(), first.z() - second.z()); }
	private static void click(int key) { KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(key)); }
	private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
	private static CompletableFuture<Boolean> submit(Minecraft client, Consumer<MinecraftServer> action) {
		MinecraftServer server = client.getSingleplayerServer();
		require(server != null, "Remote pilot fixture has no integrated server");
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		server.execute(() -> { try { require(server.isSameThread(), "Remote pilot world mutation ran off the server thread"); action.accept(server); result.complete(true); }
			catch (Throwable failure) { result.completeExceptionally(failure); } });
		return result;
	}
	private JsonObject checkpoint(Snapshot state) {
		JsonObject point = new JsonObject();
		point.addProperty("server_tick", state.serverTick());
		point.addProperty("drone_tick", state.droneTick());
		point.addProperty("remote_entity_tick", state.chickenTick());
		if (state.drone() != null) {
			point.addProperty("chunk_x", BlockPos.containing(state.drone()).getX() >> 4);
			point.addProperty("chunk_z", BlockPos.containing(state.drone()).getZ() >> 4);
		}
		point.addProperty("loading_and_entity_ticking", state.loaded() && state.ticking());
		if (PHYSICAL_CONTROLLER) point.addProperty("physical_controller_target_uuid", state.controllerBinding() == null ? null : state.controllerBinding().toString());
		return point;
	}
	private void capture(Minecraft client, String name) {
		Path path = runDirectory.resolve("screenshots/" + name + ".png");
		JsonObject capture = checkpoint(snapshot);
		capture.addProperty("checkpoint", name);
		capture.addProperty("screenshot", path.toString());
		capture.addProperty("view", DroneClientState.viewMode().name());
		capture.addProperty("pilot_health", snapshot.health());
		capture.addProperty("server_camera_is_player", snapshot.nativeCameraSelf());
		capture.addProperty("session_count", snapshot.sessions());
		capture.addProperty("ticket_count", snapshot.tickets());
		int width = client.getWindow().getWidth(), height = client.getWindow().getHeight();
		CompletableFuture<String> saved = new CompletableFuture<>();
		screenshotRecorded = false;
		screenshot = saved.orTimeout(15, TimeUnit.SECONDS).thenApplyAsync(message -> {
			try {
				byte[] bytes = Files.readAllBytes(path);
				require(bytes.length > 24 && ByteBuffer.wrap(bytes).getLong() == 0x89504e470d0a1a0aL, "Remote pilot screenshot is not a PNG");
				require(ByteBuffer.wrap(bytes, 16, 4).getInt() == width && ByteBuffer.wrap(bytes, 20, 4).getInt() == height, "Remote pilot PNG dimensions differ from captured framebuffer");
				capture.addProperty("png_verified", true);
				return capture;
			} catch (IOException failure) { throw new IllegalStateException("Remote pilot screenshot did not save", failure); }
		});
		Screenshot.grab(runDirectory.toFile(), path.getFileName().toString(), client.getMainRenderTarget(), 1, message -> saved.complete(message.getString()));
	}
	private boolean doneScreenshot() {
		if (screenshot == null || !screenshot.isDone()) return false;
		if (!screenshotRecorded) { screenshots.add(screenshot.join()); screenshotRecorded = true; }
		return true;
	}
	private record Snapshot(int serverTick, Vec3 body, float health, boolean alive, boolean nativeCameraSelf, UUID trackedDrone,
			boolean remote, int sessions, int tickets, Vec3 drone, int droneTick, boolean loaded, boolean ticking, int chickenTick, int huskTick, UUID controllerBinding) { }
}
