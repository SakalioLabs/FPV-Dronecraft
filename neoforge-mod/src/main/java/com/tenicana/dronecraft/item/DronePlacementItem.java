package com.tenicana.dronecraft.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.registry.DroneDataComponents;

/** Places a disarmed, unbound aircraft on a supported block surface. */
public final class DronePlacementItem extends Item {
	private static final double GROUND_CLEARANCE_METERS = 0.04;

	public DronePlacementItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		BlockPos support = context.getClickedPos();
		if (player == null || player.isSpectator() || context.getClickedFace() != Direction.UP
				|| context.getItemInHand().isEmpty() || !player.canInteractWithBlock(support, 0.25)
				|| !player.mayUseItemAt(support.above(), Direction.UP, context.getItemInHand())) {
			return failed(level, player);
		}
		VoxelShape supportShape = level.getBlockState(support).getCollisionShape(level, support);
		if (supportShape.isEmpty()) {
			return failed(level, player);
		}
		double top = support.getY() + supportShape.max(Direction.Axis.Y);
		DroneEntity drone = new DroneEntity(DroneEntityTypes.drone(), level);
		drone.setPos(support.getX() + 0.5, top + GROUND_CLEARANCE_METERS, support.getZ() + 0.5);
		drone.setYRot(player.getYRot());
		AABB bounds = drone.getBoundingBox();
		if (!level.getWorldBorder().isWithinBounds(bounds) || !level.noCollision(drone, bounds)
				|| !level.getBlockCollisions(drone, bounds.move(0.0, -GROUND_CLEARANCE_METERS - 0.02, 0.0))
						.iterator().hasNext()) {
			return failed(level, player);
		}
		if (!level.isClientSide()) {
			var loadout = context.getItemInHand().get(DroneDataComponents.DRONE_LOADOUT.get());
			if (loadout != null) {
				drone.configureMekanismLoadout(loadout);
			}
			if (!level.addFreshEntity(drone)) {
				return failed(level, player);
			}
			if (!player.getAbilities().instabuild) {
				context.getItemInHand().shrink(1);
			}
			player.inventoryMenu.broadcastChanges();
			player.displayClientMessage(Component.translatable("message.fpvdrone.placed_unbound"), true);
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult failed(Level level, Player player) {
		if (!level.isClientSide() && player != null) {
			player.displayClientMessage(Component.translatable("message.fpvdrone.placement_blocked"), true);
		}
		return InteractionResult.FAIL;
	}
}
