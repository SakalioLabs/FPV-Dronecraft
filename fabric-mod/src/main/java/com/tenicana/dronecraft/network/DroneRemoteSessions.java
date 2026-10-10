package com.tenicana.dronecraft.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.item.DroneControllerItem;
import com.tenicana.dronecraft.remote.RemoteChunkMapAccess;

/** A leased observation region, independent of the player's physical body and native camera. */
public final class DroneRemoteSessions {
	// Manual input still fails safe after 8 ticks; a short rendering stall must not discard the remote target.
	private static final int HEARTBEAT_TIMEOUT_TICKS = 100;
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static final Map<ServerLevel, Set<TicketKey>> TICKETS = new HashMap<>();
	private static TicketType loadingTicket;
	private static TicketType simulationTicket;

	private DroneRemoteSessions() {
	}

	public static void initialize() {
		// Match PLAYER_LOADING and PLAYER_SIMULATION flags, without persistent tickets.
		loadingTicket = Registry.register(BuiltInRegistries.TICKET_TYPE,
				Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "remote_loading"), new TicketType(0L, 2));
		simulationTicket = Registry.register(BuiltInRegistries.TICKET_TYPE,
				Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "remote_simulation"), new TicketType(0L, 12));
		ServerTickEvents.END_SERVER_TICK.register(DroneRemoteSessions::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(DroneRemoteSessions::stop);
	}

	public static void controlHeartbeat(ServerPlayer player, DroneEntity drone) {
		if (!validTarget(player, drone)) {
			end(player);
			return;
		}
		Session previous = SESSIONS.get(player.getUUID());
		boolean sameTarget = previous != null && previous.player == player && previous.drone == drone;
		SESSIONS.put(player.getUUID(), new Session(player, drone, player.level().getServer().getTickCount(),
				sameTarget && previous.remoteView));
	}

	public static void setRemoteView(ServerPlayer player, boolean enabled) {
		Session session = session(player);
		if (session != null) {
			session.remoteView = enabled;
		} else if (enabled) {
			notifyEnded(player);
		}
	}

	public static DroneEntity trackedDrone(ServerPlayer player) {
		Session session = session(player);
		return session == null ? null : session.drone;
	}

	public static boolean hasSession(ServerPlayer player) {
		return session(player) != null;
	}

	public static boolean isRemoteView(ServerPlayer player) {
		Session session = session(player);
		return session != null && session.remoteView;
	}

	public static ChunkPos viewCenter(ServerPlayer player) {
		Session session = session(player);
		return session != null && session.remoteView ? session.drone.chunkPosition() : player.chunkPosition();
	}

	public static Vec3 trackingPosition(ServerPlayer player, Entity tracked) {
		Session session = session(player);
		Vec3 body = player.position();
		if (session == null) {
			return body;
		}
		Vec3 drone = session.drone.position();
		return horizontalDistanceSquared(drone, tracked.position()) < horizontalDistanceSquared(body, tracked.position())
				? drone : body;
	}

	public static void end(ServerPlayer player) {
		if (player == null) {
			return;
		}
		Session previous = SESSIONS.get(player.getUUID());
		if (previous == null) {
			DroneControlManager.clearInput(player.getUUID());
			return;
		}
		if (previous.player != player) {
			return;
		}
		SESSIONS.remove(player.getUUID());
		DroneControlManager.clearInput(player.getUUID());
		notifyEnded(player);
	}

	public static void entityUnloaded(Entity entity) {
		for (Session session : SESSIONS.values().toArray(Session[]::new)) {
			if (session.drone == entity) {
				end(session.player);
			}
		}
	}

	public static int sessionCount(MinecraftServer server) {
		return (int) SESSIONS.values().stream().filter(session -> session.player.level().getServer() == server).count();
	}

	public static int ticketCount(ServerLevel level) {
		return TICKETS.getOrDefault(level, Set.of()).size();
	}

	private static Session session(ServerPlayer player) {
		if (player == null) {
			return null;
		}
		Session session = SESSIONS.get(player.getUUID());
		return validSession(session, player) ? session : null;
	}

	private static boolean validSession(Session session, ServerPlayer player) {
		return session != null && session.player == player && validTarget(player, session.drone)
				&& player.level().getServer().getTickCount() - session.lastHeartbeat <= HEARTBEAT_TIMEOUT_TICKS;
	}

	private static boolean validTarget(ServerPlayer player, DroneEntity drone) {
		return player != null && player.isAlive() && !player.isRemoved()
				&& drone != null && drone.isAlive() && !drone.isRemoved()
				&& drone.level() == player.level() && drone.isOwnedBy(player.getUUID())
				&& (!DroneControllerItem.hasControllerItem(player)
						|| drone.getUUID().equals(DroneControllerItem.boundDroneId(player)));
	}

	private static double horizontalDistanceSquared(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	private static void tick(MinecraftServer server) {
		for (Session session : SESSIONS.values().toArray(Session[]::new)) {
			if (session.player.level().getServer() == server && (!validSession(session, session.player)
					|| server.getPlayerList().getPlayer(session.player.getUUID()) != session.player)) {
				end(session.player);
			}
		}
		Map<ServerLevel, Set<TicketKey>> desired = new HashMap<>();
		for (Session session : SESSIONS.values()) {
			if (session.player.level().getServer() != server) {
				continue;
			}
			ServerLevel level = (ServerLevel) session.drone.level();
			int viewDistance = ((RemoteChunkMapAccess) level.getChunkSource().chunkMap)
					.fpvdrone$playerViewDistance(session.player);
			int simulationDistance = server.getPlayerList().getSimulationDistance();
			Set<TicketKey> region = desired.computeIfAbsent(level, ignored -> new HashSet<>());
			region.add(new TicketKey(loadingTicket, session.drone.chunkPosition(), Math.min(33, viewDistance + 1)));
			// addTicketWithRadius uses FULL (33) minus radius; ENTITY_TICKING needs level 31.
			region.add(new TicketKey(simulationTicket, session.drone.chunkPosition(), Math.min(33, simulationDistance + 2)));
		}
		for (ServerLevel level : server.getAllLevels()) {
			Set<TicketKey> next = desired.getOrDefault(level, Set.of());
			Set<TicketKey> previous = TICKETS.getOrDefault(level, Set.of());
			// Shared regions are sets: one controller leaving must not release another controller's tickets.
			for (TicketKey key : next) {
				if (!previous.contains(key)) {
					level.getChunkSource().addTicketWithRadius(key.type, key.center, key.radius);
				}
			}
			for (TicketKey key : previous) {
				if (!next.contains(key)) {
					level.getChunkSource().removeTicketWithRadius(key.type, key.center, key.radius);
				}
			}
			if (next.isEmpty()) {
				TICKETS.remove(level);
			} else {
				TICKETS.put(level, next);
			}
		}
		for (Session session : SESSIONS.values()) {
			if (session.player.level().getServer() == server) {
				// Refresh subscriptions and entity pairs after remote pending chunks have been delivered.
				((ServerLevel) session.player.level()).getChunkSource().move(session.player);
			}
		}
	}

	private static void notifyEnded(ServerPlayer player) {
		if (player != null && player.connection != null && ServerPlayNetworking.canSend(player, DroneSessionEndedPayload.TYPE)) {
			ServerPlayNetworking.send(player, DroneSessionEndedPayload.INSTANCE);
		}
	}

	private static void stop(MinecraftServer server) {
		SESSIONS.values().stream().filter(session -> session.player.level().getServer() == server)
				.forEach(session -> DroneControlManager.clearInput(session.player.getUUID()));
		SESSIONS.entrySet().removeIf(entry -> entry.getValue().player.level().getServer() == server);
		for (ServerLevel level : server.getAllLevels()) {
			for (TicketKey key : TICKETS.getOrDefault(level, Set.of())) {
				level.getChunkSource().removeTicketWithRadius(key.type, key.center, key.radius);
			}
			TICKETS.remove(level);
		}
	}

	private record TicketKey(TicketType type, ChunkPos center, int radius) {
	}

	private static final class Session {
		private final ServerPlayer player;
		private final DroneEntity drone;
		private final int lastHeartbeat;
		private boolean remoteView;

		private Session(ServerPlayer player, DroneEntity drone, int lastHeartbeat, boolean remoteView) {
			this.player = player;
			this.drone = drone;
			this.lastHeartbeat = lastHeartbeat;
			this.remoteView = remoteView;
		}
	}
}
