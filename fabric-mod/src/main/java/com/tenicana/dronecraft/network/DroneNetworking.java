package com.tenicana.dronecraft.network;

import java.util.Comparator;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import com.tenicana.dronecraft.debug.DroneDebugSettings;
import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;

public final class DroneNetworking {
	private DroneNetworking() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(DroneControlPayload.TYPE, DroneControlPayload.CODEC);
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
				boolean autoBound = bindNearestDroneIfNeeded(player, input);
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
				DroneControlManager.update(player.getUUID(), input, player.tickCount);
			});
		});
	}

	private static boolean bindNearestDroneIfNeeded(ServerPlayer player, DroneInput input) {
		if (player == null || input == null || !hasControlIntent(input)) {
			return false;
		}

		AABB search = player.getBoundingBox().inflate(24.0);
		List<DroneEntity> nearby = player.level().getEntitiesOfClass(
				DroneEntity.class,
				search,
				drone -> drone.isAlive() && player.level() == drone.level()
		);
		if (nearby.isEmpty()) {
			return false;
		}

		DroneEntity owned = nearby.stream()
				.filter(drone -> drone.isOwnedBy(player.getUUID()))
				.min(Comparator.comparingDouble(drone -> drone.distanceToSqr(player)))
				.orElse(null);
		if (owned != null) {
			return false;
		}

		DroneEntity unowned = nearby.stream()
				.filter(drone -> drone.getOwner() == null)
				.min(Comparator.comparingDouble(drone -> drone.distanceToSqr(player)))
				.orElse(null);
		if (unowned != null) {
			unowned.setOwner(player.getUUID());
			return true;
		}

		DroneDebugSettings.logControlReason(
				player,
				player.tickCount,
				null,
				"no_drone_nearby",
				nearby.size(),
				true,
				input.linkActive()
		);
		return false;
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
