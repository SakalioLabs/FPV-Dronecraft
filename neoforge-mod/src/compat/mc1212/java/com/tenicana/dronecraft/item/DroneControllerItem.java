package com.tenicana.dronecraft.item;

import java.util.UUID;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.network.DroneRemoteSessions;
import com.tenicana.dronecraft.registry.DroneItems;

/** A physical controller stores one explicit drone target; it never creates or repairs a drone. */
public class DroneControllerItem extends Item {
	private static final String BOUND_DRONE_KEY = "fpvdrone_bound_drone";

	public DroneControllerItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context,
			List<Component> tooltip, TooltipFlag flag) {
		UUID target = boundDroneId(stack);
		tooltip.add((target == null ? Component.translatable("tooltip.fpvdrone.controller_unbound")
				: Component.translatable("tooltip.fpvdrone.controller_target", target.toString().substring(0, 8)))
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.fpvdrone.controller_bind").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			player.displayClientMessage(Component.translatable("message.fpvdrone.binding_hint"), true);
		}
		return InteractionResult.SUCCESS;
	}

	public static boolean hasControllerItem(Player player) {
		return player != null && (player.getMainHandItem().is(DroneItems.droneController())
				|| player.getOffhandItem().is(DroneItems.droneController()));
	}

	/** Main hand takes precedence, even if that controller has not been bound yet. */
	public static UUID boundDroneId(Player player) {
		if (player == null) {
			return null;
		}
		ItemStack main = player.getMainHandItem();
		return boundDroneId(main.is(DroneItems.droneController()) ? main : player.getOffhandItem());
	}

	public static UUID boundDroneId(ItemStack controller) {
		if (controller == null || !controller.is(DroneItems.droneController())) {
			return null;
		}
		CustomData customData = controller.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return null;
		}
		try {
			return UUID.fromString(customData.copyTag().getString(BOUND_DRONE_KEY));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	/** Invoked by the drone's normal entity interaction, with the actual controller stack in that hand. */
	public static boolean bindToDrone(Player player, InteractionHand hand, DroneEntity drone) {
		if (player == null || drone == null || !player.isShiftKeyDown()
				|| !player.getItemInHand(hand).is(DroneItems.droneController())) {
			return false;
		}
		if (player.isSpectator() || !player.isAlive() || player.isRemoved() || !drone.isAlive()
				|| drone.isRemoved() || player.level() != drone.level()
				|| !player.canInteractWithEntity(drone, 0.25)) {
			return false;
		}
		if (drone.hasOwner() && !drone.isOwnedBy(player.getUUID())) {
			message(player, "message.fpvdrone.binding_other_owner");
			return false;
		}
		if (drone.isArmed()) {
			message(player, "message.fpvdrone.binding_armed");
			return false;
		}
		if (!player.level().isClientSide()) {
			// Reset the old lease and input before switching a controller's UUID. Nothing inherits its throttle.
			DroneControlManager.stopDiagnostic(player.getUUID());
			if (player instanceof ServerPlayer serverPlayer) {
				DroneRemoteSessions.end(serverPlayer);
			} else {
				DroneControlManager.clearInput(player.getUUID());
			}
			drone.setOwner(player.getUUID());
			CustomData.update(DataComponents.CUSTOM_DATA, player.getItemInHand(hand),
					data -> data.putString(BOUND_DRONE_KEY, drone.getUUID().toString()));
			player.inventoryMenu.broadcastChanges();
			message(player, "message.fpvdrone.bound");
		}
		return true;
	}

	private static void message(Player player, String key) {
		if (!player.level().isClientSide()) {
			player.displayClientMessage(Component.translatable(key), true);
		}
	}
}
