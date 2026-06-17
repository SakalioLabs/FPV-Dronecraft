package com.tenicana.dronecraft.debug;

import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.Locale;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.sim.DroneInput;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class DroneDebugSettings {
	private static final UUID OWNERLESS_LOG_ID = new UUID(0L, 1L);
	private static final ConcurrentHashMap<UUID, Integer> LAST_CONTROL_LOG_TICK = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<UUID, Integer> LAST_TICK_LOG_TICK = new ConcurrentHashMap<>();
	private static volatile boolean controlLoggingEnabled;
	private static volatile boolean tickLoggingEnabled;
	private static volatile boolean ownerlessControlEnabled;
	private static volatile boolean bypassPhysicsEnabled = true;

	private DroneDebugSettings() {
	}

	public static boolean controlLoggingEnabled() {
		return controlLoggingEnabled;
	}

	public static boolean tickLoggingEnabled() {
		return tickLoggingEnabled;
	}

	public static boolean ownerlessControlEnabled() {
		return ownerlessControlEnabled;
	}

	public static boolean bypassPhysicsEnabled() {
		return bypassPhysicsEnabled;
	}

	public static void setControlLoggingEnabled(boolean enabled) {
		controlLoggingEnabled = enabled;
	}

	public static void setTickLoggingEnabled(boolean enabled) {
		tickLoggingEnabled = enabled;
	}

	public static void setOwnerlessControlEnabled(boolean enabled) {
		ownerlessControlEnabled = enabled;
	}

	public static void setBypassPhysicsEnabled(boolean enabled) {
		bypassPhysicsEnabled = enabled;
	}

	public static String statusLine() {
		return String.format(
				"debug[pkt=%s,tick=%s,physics=%s,ownerless=%s]",
				controlLoggingEnabled ? "on" : "off",
				tickLoggingEnabled ? "on" : "off",
				bypassPhysicsEnabled ? "direct" : "sim",
				ownerlessControlEnabled ? "on" : "off"
		);
	}

	public static void logControlPacket(
			ServerPlayer player,
			int tick,
			double throttle,
			double pitch,
			double roll,
			double yaw,
			boolean armed,
			int flightMode,
			boolean linkActive,
			boolean autoBound
	) {
		if (!controlLoggingEnabled || player == null) {
			return;
		}

		java.util.UUID playerId = player.getUUID();
		Integer lastTick = LAST_CONTROL_LOG_TICK.get(playerId);
		if (lastTick != null && tick - lastTick < 20) {
			return;
		}
		LAST_CONTROL_LOG_TICK.put(playerId, tick);

		FpvDronecraftMod.LOGGER.info(
				"[FPV-Debug] input player={} tick={} input={} armed={} mode={} link={} autoBound={}",
				player.getName().getString(),
				tick,
				String.format("%.3f %.3f %.3f %.3f", throttle, pitch, roll, yaw),
				armed,
				flightMode,
				linkActive,
				autoBound
		);
	}

	public static void logBinding(
			ServerPlayer player,
			int tick,
			int nearby,
			boolean hasOwned,
			boolean hasUnowned,
			boolean autoBound,
			boolean active,
			boolean linkActive,
			boolean hasControlIntent
	) {
		if (!controlLoggingEnabled || player == null) {
			return;
		}

		java.util.UUID playerId = player.getUUID();
		Integer lastTick = LAST_CONTROL_LOG_TICK.get(playerId);
		if (lastTick != null && tick - lastTick < 20) {
			return;
		}
		LAST_CONTROL_LOG_TICK.put(playerId, tick);

		FpvDronecraftMod.LOGGER.info(
				"[FPV-Debug] bind player={} tick={} nearby={} owned={} unowned={} autoBound={} activeOwner={} linkActive={} intent={}",
				player.getName().getString(),
				tick,
				nearby,
				hasOwned,
				hasUnowned,
				autoBound,
				active,
				linkActive,
				hasControlIntent
		);
	}

	public static void logControlReason(ServerPlayer player, int tick, UUID rawOwner, String reason, int nearby, boolean hasIntent, boolean linkActive) {
		if (!controlLoggingEnabled || player == null) {
			return;
		}
		java.util.UUID playerId = player.getUUID();
		Integer lastTick = LAST_CONTROL_LOG_TICK.get(playerId);
		if (lastTick != null && tick - lastTick < 20) {
			return;
		}
		LAST_CONTROL_LOG_TICK.put(playerId, tick);

		FpvDronecraftMod.LOGGER.info(
				"[FPV-Debug] bind-fail player={} tick={} reason={} nearby={} owner={} intent={} linkActive={}",
				player.getName().getString(),
				tick,
				reason,
				nearby,
				rawOwner == null ? "none" : rawOwner.toString(),
				hasIntent,
				linkActive
		);
	}

	public static void logEntityTick(
			Entity drone,
			int tick,
			DroneInput rawInput,
			DroneInput effectiveInput,
			String controlReason,
			UUID controlOwner,
			float targetVx,
			float targetVy,
			float targetVz,
			float targetYawRate,
			boolean airworthy,
			boolean hasOwner,
			boolean autoBypassed,
			Vec3 velocity
	) {
		if (!tickLoggingEnabled || drone == null) {
			return;
		}
		java.util.UUID droneId = drone.getUUID();
		Integer lastTick = LAST_TICK_LOG_TICK.get(droneId);
		if (lastTick != null && tick - lastTick < 20) {
			return;
		}
		LAST_TICK_LOG_TICK.put(droneId, tick);
		DroneInput raw = rawInput == null ? DroneInput.idle() : rawInput.normalized();
		DroneInput effective = effectiveInput == null ? raw : effectiveInput.normalized();
		Vec3 movement = velocity == null ? Vec3.ZERO : velocity;
		double vx = movement.x();
		double vy = movement.y();
		double vz = movement.z();
		double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
		double altitude = drone.getY();
		String owner = controlOwner == null ? "none" : controlOwner.toString();
		FpvDronecraftMod.LOGGER.info(
				"[FPV-Debug] tick={} reason={} owner={} raw_input={} eff_input={} airworthy={} ownerSync={} bypass={} alt={} vel={} speed={} tgt=({} {} {}) yawRate={}",
				tick,
				controlReason,
				owner,
				String.format(Locale.ROOT, "%.3f %.3f %.3f %.3f", raw.throttle(), raw.pitch(), raw.roll(), raw.yaw()),
				String.format(Locale.ROOT, "%.3f %.3f %.3f %.3f armed=%s", effective.throttle(), effective.pitch(), effective.roll(), effective.yaw(), effective.armed()),
				airworthy,
				hasOwner,
				autoBypassed,
				String.format(Locale.ROOT, "%.2f", altitude),
				String.format(Locale.ROOT, "%.3f %.3f %.3f", vx, vy, vz),
				String.format(Locale.ROOT, "%.3f", speed),
				String.format(Locale.ROOT, "%.3f", targetVx),
				String.format(Locale.ROOT, "%.3f", targetVy),
				String.format(Locale.ROOT, "%.3f", targetVz),
				String.format(Locale.ROOT, "%.3f", targetYawRate)
		);
	}

	public static void logNoOwnerInput(int tick, int nearby, boolean ownerlessMode, UUID lastPacketOwner) {
		if (!controlLoggingEnabled) {
			return;
		}
		Integer lastTick = LAST_CONTROL_LOG_TICK.get(OWNERLESS_LOG_ID);
		if (lastTick != null && tick - lastTick < 60) {
			return;
		}
		LAST_CONTROL_LOG_TICK.put(OWNERLESS_LOG_ID, tick);
		FpvDronecraftMod.LOGGER.info(
				"[FPV-Debug] ownerless-control tick={} nearby={} ownerlessMode={} lastOwner={}",
				tick,
				nearby,
				ownerlessMode,
				lastPacketOwner == null ? "none" : lastPacketOwner.toString()
		);
	}
}
