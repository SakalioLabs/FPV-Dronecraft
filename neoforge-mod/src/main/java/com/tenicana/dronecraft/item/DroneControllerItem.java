package com.tenicana.dronecraft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;

public class DroneControllerItem extends Item {
	private static final double SPAWN_FORWARD_METERS = 1.65;
	private static final double SPAWN_GROUND_OFFSET_METERS = 0.04;
	private static final double OWNED_DRONE_REUSE_RADIUS_METERS = 96.0;

	public DroneControllerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			DroneEntity existing = DroneControllerSelection.nearestReusableOwnedDrone(
					level,
					player.getUUID(),
					player.position(),
					OWNED_DRONE_REUSE_RADIUS_METERS
			);
			if (existing != null) {
				player.displayClientMessage(Component.translatable("message.fpvdrone.bound"), true);
				return InteractionResult.SUCCESS;
			}

			DroneEntity drone = new DroneEntity(DroneEntityTypes.drone(), level);
			drone.setOwner(player.getUUID());
			net.minecraft.world.phys.Vec3 look = player.getLookAngle();
			double horizontal = Math.hypot(look.x, look.z);
			double forwardX = horizontal <= 1.0e-6 ? -Math.sin(Math.toRadians(player.getYRot())) : look.x / horizontal;
			double forwardZ = horizontal <= 1.0e-6 ? Math.cos(Math.toRadians(player.getYRot())) : look.z / horizontal;
			drone.setPos(
					player.getX() + forwardX * SPAWN_FORWARD_METERS,
					player.getY() + SPAWN_GROUND_OFFSET_METERS,
					player.getZ() + forwardZ * SPAWN_FORWARD_METERS
			);
			drone.setYRot(player.getYRot());
			level.addFreshEntity(drone);
			player.displayClientMessage(Component.translatable("message.fpvdrone.spawned"), true);
		}

		return InteractionResult.SUCCESS;
	}
}
