package com.tenicana.dronecraft.network;

import java.util.Comparator;
import java.util.UUID;
import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
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

	/** Client callbacks are installed from the physical client entry point without loading client classes here. */
	private static volatile ClientHandlers clientHandlers = new ClientHandlers(payload -> { }, payload -> { });

	public static void initializeClientHandlers(
			Consumer<DroneSessionEndedPayload> sessionEnded,
			Consumer<DroneChunkCentersPayload> chunkCenters
	) {
		clientHandlers = new ClientHandlers(Objects.requireNonNull(sessionEnded), Objects.requireNonNull(chunkCenters));
	}

	private record ClientHandlers(Consumer<DroneSessionEndedPayload> sessionEnded,
			Consumer<DroneChunkCentersPayload> chunkCenters) { }

	public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
		var registrar = event.registrar("1").optional();
		registrar.playToServer(DroneControlPayload.TYPE, DroneControlPayload.CODEC, DroneNetworking::handleControl);
		registrar.playToServer(DroneViewPayload.TYPE, DroneViewPayload.CODEC, DroneNetworking::handleView);
		registrar.playToClient(DroneSessionEndedPayload.TYPE, DroneSessionEndedPayload.CODEC,
				(payload, context) -> context.enqueueWork(() -> clientHandlers.sessionEnded().accept(payload)));
		registrar.playToClient(DroneChunkCentersPayload.TYPE, DroneChunkCentersPayload.CODEC,
				(payload, context) -> context.enqueueWork(() -> clientHandlers.chunkCenters().accept(payload)));
	}

	public static void initialize() {
		DroneRemoteSessions.initialize();
		NeoForge.EVENT_BUS.addListener(DroneNetworking::onPlayerLoggedIn);
		NeoForge.EVENT_BUS.addListener(DroneNetworking::onPlayerLoggedOut);
		NeoForge.EVENT_BUS.addListener(DroneNetworking::onPlayerClone);
		NeoForge.EVENT_BUS.addListener(DroneNetworking::onPlayerChangedDimension);
		NeoForge.EVENT_BUS.addListener(DroneNetworking::onEntityLeaveLevel);
		NeoForge.EVENT_BUS.addListener(DroneNetworking::onServerTick);
	}

	private static void handleControl(DroneControlPayload payload, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player)) {
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

		context.enqueueWork(() -> {
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
	}

	private static void handleView(DroneViewPayload payload, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player) {
			context.enqueueWork(() -> updateFpvCamera(player, payload.fpvView()));
		}
	}

	private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			restorePlayerCamera(player);
		}
	}

	private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			restorePlayerCamera(player);
		}
	}

	private static void onPlayerClone(PlayerEvent.Clone event) {
		if (event.getOriginal() instanceof ServerPlayer originalPlayer) {
			restorePlayerCamera(originalPlayer);
		}
		if (event.getEntity() instanceof ServerPlayer newPlayer) {
			restorePlayerCamera(newPlayer);
		}
	}

	private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			restorePlayerCamera(player);
		}
	}

	private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
		if (!(event.getEntity() instanceof DroneEntity)) {
			return;
		}
		MinecraftServer server = event.getLevel().getServer();
		if (server != null) {
			server.getPlayerList().getPlayers().stream()
					.filter(player -> player.getCamera() == event.getEntity())
					.forEach(DroneNetworking::restorePlayerCamera);
			DroneRemoteSessions.entityUnloaded(event.getEntity());
		}
	}

	private static void onServerTick(ServerTickEvent.Post event) {
		event.getServer().getPlayerList().getPlayers().forEach(DroneNetworking::restoreInvalidDroneCamera);
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
