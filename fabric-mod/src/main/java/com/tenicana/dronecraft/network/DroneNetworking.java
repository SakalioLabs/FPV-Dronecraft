package com.tenicana.dronecraft.network;

import java.util.Comparator;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import com.tenicana.dronecraft.debug.DroneDebugSettings;
import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.item.DroneControllerItem;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;

public final class DroneNetworking {
	private static final double FPV_CAMERA_SEARCH_RADIUS = 512.0;

	private DroneNetworking() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(DroneControlPayload.TYPE, DroneControlPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DroneViewPayload.TYPE, DroneViewPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DroneSessionEndedPayload.TYPE, DroneSessionEndedPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DroneChunkCentersPayload.TYPE, DroneChunkCentersPayload.CODEC);
		DroneRemoteSessions.initialize();
		ServerPlayNetworking.registerGlobalReceiver(DroneControlPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player == null) {
				return;
			}
			DroneInput input = new DroneInput(
					payload.throttle(),
					payload.pitch(),
					payload.roll(),
					payload.yaw(),
					payload.armed(),
					true,
					FlightMode.byId(payload.flightMode())
			);

			context.server().execute(() -> {
				boolean hasIntent = hasControlIntent(input);
				boolean autoBound = false;
				int nearbyCount = nearbyDroneCount(player, input);
				boolean hasOwned = hasOwnedDrone(player, input);
				boolean hasUnowned = hasUnownedDrone(player, input);
				DroneDebugSettings.logBinding(
						player,
						player.tickCount,
						nearbyCount,
						hasOwned,
						hasUnowned,
						autoBound,
						hasOwned,
						input.linkActive(),
						hasIntent
				);
				DroneDebugSettings.logControlPacket(
						player,
						player.tickCount,
						input.throttle(),
						input.pitch(),
						input.roll(),
						input.yaw(),
						input.armed(),
						input.flightMode().id(),
						input.linkActive(),
						autoBound
				);
				if (!hasIntent) {
					DroneDebugSettings.logControlReason(
							player,
							player.tickCount,
							player.getUUID(),
							"no_control_intent",
							nearbyCount,
							false,
							input.linkActive()
					);
				}
				DroneEntity drone = fpvCameraDrone(player);
				DroneRemoteSessions.controlHeartbeat(player, drone);
				if (drone != null && player.isAlive()) {
					DroneControlManager.update(player.getUUID(), drone.getUUID(), input, drone.tickCount);
				}
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(DroneViewPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player == null) {
				return;
			}
			context.server().execute(() -> updateFpvCamera(player, payload.fpvView()));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> restorePlayerCamera(handler.player));
		ServerPlayerEvents.JOIN.register(DroneNetworking::restorePlayerCamera);
		ServerPlayerEvents.LEAVE.register(DroneNetworking::restorePlayerCamera);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			restorePlayerCamera(oldPlayer);
			restorePlayerCamera(newPlayer);
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> restorePlayerCamera(player));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof DroneEntity) {
				world.getServer().getPlayerList().getPlayers().stream()
						.filter(player -> player.getCamera() == entity)
						.forEach(DroneNetworking::restorePlayerCamera);
				DroneRemoteSessions.entityUnloaded(entity);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(DroneNetworking::restoreInvalidDroneCamera));
	}

	private static void updateFpvCamera(ServerPlayer player, boolean fpvView) {
		if (player == null) {
			return;
		}
		if (!fpvView) {
			DroneRemoteSessions.setRemoteView(player, false);
			return;
		}
		DroneEntity drone = fpvCameraDrone(player);
		if (drone == null) {
			restorePlayerCamera(player);
			DroneRemoteSessions.setRemoteView(player, true);
		} else {
			DroneRemoteSessions.controlHeartbeat(player, drone);
			DroneRemoteSessions.setRemoteView(player, true);
		}
	}

	private static DroneEntity fpvCameraDrone(ServerPlayer player) {
		if (DroneControllerItem.hasControllerItem(player)) {
			UUID boundId = DroneControllerItem.boundDroneId(player);
			Entity bound = boundId == null ? null : player.level().getEntity(boundId);
			return bound instanceof DroneEntity drone && isValidFpvCameraDrone(player, drone) ? drone : null;
		}
		DroneEntity tracked = DroneRemoteSessions.trackedDrone(player);
		if (tracked != null) {
			return tracked;
		}
		Entity camera = player.getCamera();
		if (camera instanceof DroneEntity drone && isValidFpvCameraDrone(player, drone)) {
			return drone;
		}

		AABB search = player.getBoundingBox().inflate(FPV_CAMERA_SEARCH_RADIUS);
		return player.level().getEntitiesOfClass(
				DroneEntity.class,
				search,
				drone -> isValidFpvCameraDrone(player, drone)
		).stream()
				.min(Comparator.comparingDouble(drone -> drone.distanceToSqr(player)))
				.orElse(null);
	}

	public static DroneEntity selectedDrone(ServerPlayer player) {
		return player == null ? null : fpvCameraDrone(player);
	}

	private static void restorePlayerCamera(ServerPlayer player) {
		DroneRemoteSessions.end(player);
		if (player != null && player.getCamera() != player) {
			player.setCamera(player);
		}
	}

	private static void restoreInvalidDroneCamera(ServerPlayer player) {
		if (player == null) {
			return;
		}
		Entity camera = player.getCamera();
		if (camera instanceof DroneEntity drone && !isValidFpvCameraDrone(player, drone)) {
			restorePlayerCamera(player);
		}
	}

	private static boolean isValidFpvCameraDrone(ServerPlayer player, DroneEntity drone) {
		return player != null
				&& drone != null
				&& drone.isAlive()
				&& !drone.isRemoved()
				&& drone.level() == player.level()
				&& drone.isOwnedBy(player.getUUID());
	}

	private static int nearbyDroneCount(ServerPlayer player, DroneInput input) {
		if (player == null || input == null || !hasControlIntent(input)) {
			return 0;
		}
		AABB search = player.getBoundingBox().inflate(24.0);
		return player.level().getEntitiesOfClass(
				DroneEntity.class,
				search,
				drone -> drone.isAlive() && player.level() == drone.level()
		).size();
	}

	private static boolean hasOwnedDrone(ServerPlayer player, DroneInput input) {
		if (player == null || input == null || !hasControlIntent(input)) {
			return false;
		}
		AABB search = player.getBoundingBox().inflate(24.0);
		return player.level().getEntitiesOfClass(
				DroneEntity.class,
				search,
				drone -> drone.isAlive() && player.level() == drone.level() && drone.isOwnedBy(player.getUUID())
		).size() > 0;
	}

	private static boolean hasUnownedDrone(ServerPlayer player, DroneInput input) {
		if (player == null || input == null || !hasControlIntent(input)) {
			return false;
		}
		AABB search = player.getBoundingBox().inflate(24.0);
		return player.level().getEntitiesOfClass(
				DroneEntity.class,
				search,
				drone -> drone.isAlive() && player.level() == drone.level() && drone.getOwner() == null
		).size() > 0;
	}

	private static boolean hasControlIntent(DroneInput input) {
		if (input == null || !input.linkActive()) {
			return false;
		}
		return input.armed()
				|| input.throttle() > 0.001
				|| Math.abs(input.pitch()) > 0.001
				|| Math.abs(input.roll()) > 0.001
				|| Math.abs(input.yaw()) > 0.001;
	}
}
