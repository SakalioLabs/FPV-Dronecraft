package com.tenicana.dronecraft.playtest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;

/** Runs real client packet handlers on an isolated fixture, without opening a network socket. */
public final class DronePosePacketSmoke {
	private static final double EPSILON = 1.0e-5;
	private static final EntityDataAccessor<Float> PITCH = attitudeAccessor("PITCH");
	private static final EntityDataAccessor<Float> YAW = attitudeAccessor("YAW");
	private static final EntityDataAccessor<Float> ROLL = attitudeAccessor("ROLL");

	private DronePosePacketSmoke() {
	}

	/** Call through ClientGameTestContext.runOnClient after the synthetic world's chunks download. */
	public static void verify(Minecraft client) {
		verifyAndReturnFixtureId(client);
	}

	/** Returns the same isolated fixture's ID for functional monitor event checks. */
	public static int verifyAndReturnFixtureId(Minecraft client) {
		ClientLevel level = client.level;
		ClientPacketListener listener = client.getConnection();
		check(level != null && listener != null && client.player != null, "pose packet smoke needs an active client world");
		DroneEntity controlledBefore = DroneClientState.controlledDrone();
		Entity cameraBefore = client.getCameraEntity();
		DroneEntity fixture = new DroneEntity(DroneEntityTypes.DRONE, level);
		int id = 0x7fff0000;
		while (level.getEntity(id) != null) {
			id--;
		}
		fixture.setId(id);
		fixture.setSilent(true);
		fixture.setInvisible(true);
		Vec3 start = new Vec3(Math.floor(client.player.getX()) + 1.0,
				Math.floor(client.player.getY()) + 8.0, Math.floor(client.player.getZ()) + 1.0);
		fixture.snapTo(start, 0.0f, 0.0f);
		fixture.syncPacketPositionCodec(start.x, start.y, start.z);
		boolean registered = false;
		try {
			level.addEntity(fixture);
			registered = true;
			check(level.getEntity(id) == fixture, "pose fixture must be registered for packet lookup");
			check(level.isTickingEntity(fixture), "pose fixture must occupy a ticking client chunk");
			check(fixture.getOwner() == null && !fixture.isArmed() && fixture.getMotorPower() == 0.0f,
					"pose fixture must remain unowned and have no active motors");
			level.tickNonPassenger(fixture);

			verifyMetadata(level, listener, fixture);
			verifySignedFullTurnMetadata(level, listener, fixture);
			verifyQueuedMovement(level, listener, fixture, start);
			verifyTeleportReset(level, listener, fixture);

			check(DroneClientState.controlledDrone() == controlledBefore && client.getCameraEntity() == cameraBefore,
					"pose fixture must not change the real controlled drone or camera");
			FpvDronecraftMod.LOGGER.info("Playtest pose packet smoke passed: metadata, signed pitch/roll 720-degree turns, relative movement, position sync, teleport reset");
		} finally {
			if (registered) {
				level.removeEntity(id, Entity.RemovalReason.DISCARDED);
			}
		}
		return id;
	}

	private static void verifyMetadata(ClientLevel level, ClientPacketListener listener, DroneEntity fixture) {
		sendAttitude(listener, fixture, 0.2f, 0.4f, 0.6f);
		assertAngles(fixture, 0.6f, 0.0f, 0.0f, 0.0f, "mid-tick metadata must leave the rendered attitude unchanged");
		level.tickNonPassenger(fixture);
		assertAngles(fixture, 0.0f, 0.0f, 0.0f, 0.0f, "attitude interval must begin at its previous endpoint");
		assertAngles(fixture, 0.5f, 0.1f, 0.2f, 0.3f, "all metadata angles must interpolate in the new interval");
		assertAngles(fixture, 1.0f, 0.2f, 0.4f, 0.6f, "all metadata angles must commit on the next tick");

		sendAttitude(listener, fixture, 0.4f, 0.8f, 1.2f);
		sendAttitude(listener, fixture, 0.6f, 1.2f, 1.8f);
		assertAngles(fixture, 0.6f, 0.12f, 0.24f, 0.36f, "multiple metadata packets must not retarget the active interval");
		level.tickNonPassenger(fixture);
		assertAngles(fixture, 0.0f, 0.2f, 0.4f, 0.6f, "coalesced attitude must retain the previous endpoint");
		assertAngles(fixture, 0.5f, 0.4f, 0.8f, 1.2f, "coalesced attitude must use the latest complete packet");
		assertAngles(fixture, 1.0f, 0.6f, 1.2f, 1.8f, "latest metadata target must become the new endpoint");

		float nearPi = (float) Math.toRadians(179.0);
		sendAttitude(listener, fixture, nearPi, -nearPi, nearPi);
		level.tickNonPassenger(fixture);
		level.tickNonPassenger(fixture);
		sendAttitude(listener, fixture, -nearPi, nearPi, -nearPi);
		level.tickNonPassenger(fixture);
		assertAngles(fixture, 0.5f, (float) Math.PI, (float) -Math.PI, (float) Math.PI,
				"packet attitude must cross the pi boundary along the short arc");
	}

	/** Exercise the real metadata handler at both signed turn boundaries. */
	private static void verifySignedFullTurnMetadata(ClientLevel level, ClientPacketListener listener, DroneEntity fixture) {
		for (boolean pitchAxis : new boolean[]{true, false}) {
			for (int sign : new int[]{-1, 1}) {
				sendAttitude(listener, fixture, 0, 0, 0);
				level.tickNonPassenger(fixture);
				level.tickNonPassenger(fixture);
				float previous = 0;
				for (int degrees = 5; degrees <= 720; degrees += 5) {
					float next = (float) Math.toRadians(sign * degrees);
					float before = pitchAxis ? fixture.getInterpolatedRenderPitchRadians(1) : fixture.getInterpolatedRenderRollRadians(1);
					sendAttitude(listener, fixture, pitchAxis ? next : 0, 0, pitchAxis ? 0 : next);
					float afterPacket = pitchAxis ? fixture.getInterpolatedRenderPitchRadians(1) : fixture.getInterpolatedRenderRollRadians(1);
					check(Math.abs(before - afterPacket) < EPSILON, "multi-turn packet retargeted an active interval");
					level.tickNonPassenger(fixture);
					float midpoint = pitchAxis ? fixture.getInterpolatedRenderPitchRadians(0.5f) : fixture.getInterpolatedRenderRollRadians(0.5f);
					check(Math.abs(midpoint - (previous + next) / 2) < EPSILON, "signed full-turn metadata rewound or reversed interpolation");
					check(Math.abs(fixture.getInterpolatedRenderYawRadians(1)) < EPSILON, "pitch/roll revolution changed metadata yaw");
					previous = next;
				}
				for (int tick = 0; tick < 5; tick++) level.tickNonPassenger(fixture);
				float held = pitchAxis ? fixture.getInterpolatedRenderPitchRadians(1) : fixture.getInterpolatedRenderRollRadians(1);
				check(Math.abs(held - previous) < EPSILON, "completed-turn metadata was recentered without an update");
			}
		}
	}

	private static void verifyQueuedMovement(ClientLevel level, ClientPacketListener listener, DroneEntity fixture, Vec3 start) {
		Vec3 renderedBefore = fixture.getPosition(0.6f);
		listener.handleMoveEntity(new ClientboundMoveEntityPacket.Pos(fixture.getId(), (short) 4096, (short) 0, (short) 0, false));
		listener.handleMoveEntity(new ClientboundMoveEntityPacket.Pos(fixture.getId(), (short) 8192, (short) 0, (short) 0, false));
		assertPosition(fixture.getPosition(0.6f), renderedBefore, "relative movement packets must not change the active interval");
		check(fixture.isInterpolating(), "relative movement packets must queue interpolation");
		Vec3 latest = start.add(3.0, 0.0, 0.0);
		level.tickNonPassenger(fixture);
		assertPosition(fixture.getPosition(0.0f), start, "coalesced movement must begin at the previous endpoint");
		assertPosition(fixture.getPosition(0.5f), start.lerp(latest, 0.5), "coalesced movement must render its midpoint");
		assertPosition(fixture.getPosition(1.0f), latest, "coalesced movement must use the latest decoded relative target");

		Vec3 synced = latest.add(2.0, 1.0, 0.0);
		Vec3 intervalBeforeSync = fixture.getPosition(0.6f);
		listener.handleEntityPositionSync(new ClientboundEntityPositionSyncPacket(fixture.getId(), pose(synced), false));
		assertPosition(fixture.getPosition(0.6f), intervalBeforeSync, "nearby position sync must not change the active interval");
		check(fixture.isInterpolating(), "nearby position sync must queue interpolation");
		level.tickNonPassenger(fixture);
		assertPosition(fixture.getPosition(0.5f), latest.lerp(synced, 0.5), "position sync must render a new tick midpoint");
		assertPosition(fixture.getPosition(1.0f), synced, "position sync must reach its target in one interval");
	}

	private static void verifyTeleportReset(ClientLevel level, ClientPacketListener listener, DroneEntity fixture) {
		listener.handleMoveEntity(new ClientboundMoveEntityPacket.Pos(fixture.getId(), (short) 4096, (short) 0, (short) 0, false));
		check(fixture.isInterpolating(), "teleport test must begin with a queued movement target");
		sendAttitude(listener, fixture, 0.3f, -0.4f, 0.5f);
		Vec3 teleported = fixture.position().add(0.0, 80.0, 0.0);
		listener.handleTeleportEntity(new ClientboundTeleportEntityPacket(fixture.getId(), pose(teleported), Set.of(), false));
		assertPosition(fixture.position(), teleported, "large teleport packet must immediately place the fixture");
		check(!fixture.isInterpolating(), "large teleport packet must cancel the stale movement target");
		assertAngles(fixture, 0.6f, 0.3f, -0.4f, 0.5f, "large teleport must reset the old attitude interval");
		level.tickNonPassenger(fixture);
		assertPosition(fixture.getPosition(0.5f), teleported, "tick after teleport must not return toward the stale target");

		listener.handleMoveEntity(new ClientboundMoveEntityPacket.Pos(fixture.getId(), (short) 4096, (short) 0, (short) 0, false));
		check(fixture.isInterpolating(), "large sync test must begin with a queued movement target");
		Vec3 farSync = teleported.add(0.0, 80.0, 0.0);
		listener.handleEntityPositionSync(new ClientboundEntityPositionSyncPacket(fixture.getId(), pose(farSync), false));
		assertPosition(fixture.position(), farSync, "large position sync must take the vanilla snap path");
		check(!fixture.isInterpolating(), "large position sync must cancel stale interpolation");
		level.tickNonPassenger(fixture);
		assertPosition(fixture.getPosition(0.5f), farSync, "tick after large position sync must stay at the snapped position");
	}

	private static PositionMoveRotation pose(Vec3 position) {
		return new PositionMoveRotation(position, Vec3.ZERO, 0.0f, 0.0f);
	}

	private static void sendAttitude(ClientPacketListener listener, DroneEntity fixture, float pitch, float yaw, float roll) {
		List<SynchedEntityData.DataValue<?>> values = List.of(
				SynchedEntityData.DataValue.create(ROLL, roll),
				SynchedEntityData.DataValue.create(YAW, yaw),
				SynchedEntityData.DataValue.create(PITCH, pitch));
		listener.handleSetEntityData(new ClientboundSetEntityDataPacket(fixture.getId(), values));
		check(Math.abs(fixture.getRenderPitchRadians() - pitch) < EPSILON
				&& Math.abs(fixture.getRenderYawRadians() - yaw) < EPSILON
				&& Math.abs(fixture.getRenderRollRadians() - roll) < EPSILON,
				"real metadata handler must assign all three telemetry values");
	}

	@SuppressWarnings("unchecked")
	private static EntityDataAccessor<Float> attitudeAccessor(String name) {
		try {
			Field field = DroneEntity.class.getDeclaredField(name);
			field.setAccessible(true);
			return (EntityDataAccessor<Float>) field.get(null);
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("could not obtain test metadata accessor " + name, error);
		}
	}

	private static void assertAngles(DroneEntity fixture, float partialTick, float pitch, float yaw, float roll, String message) {
		check(Math.abs(fixture.getInterpolatedRenderPitchRadians(partialTick) - pitch) < EPSILON
				&& Math.abs(fixture.getInterpolatedRenderYawRadians(partialTick) - yaw) < EPSILON
				&& Math.abs(fixture.getInterpolatedRenderRollRadians(partialTick) - roll) < EPSILON, message);
	}

	private static void assertPosition(Vec3 actual, Vec3 expected, String message) {
		check(actual.distanceTo(expected) < EPSILON, message + ": actual=" + actual + ", expected=" + expected);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
