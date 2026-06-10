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
	public DroneControllerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			DroneEntity drone = new DroneEntity(DroneEntityTypes.DRONE, level);
			drone.setOwner(player.getUUID());
			drone.setPos(player.getX(), player.getY() + 1.25, player.getZ());
			drone.setYRot(player.getYRot());
			level.addFreshEntity(drone);
			player.displayClientMessage(Component.translatable("message.fpvdrone.spawned"), true);
		}

		return InteractionResult.SUCCESS;
	}
}
