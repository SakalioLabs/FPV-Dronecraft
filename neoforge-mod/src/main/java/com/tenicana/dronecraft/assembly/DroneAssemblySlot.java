package com.tenicana.dronecraft.assembly;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A drone component slot whose interaction is gated by its owning menu. */
public final class DroneAssemblySlot extends Slot {
	private final BooleanSupplier interactionAllowed;

	public DroneAssemblySlot(Container container, int slot, int x, int y, BooleanSupplier interactionAllowed) {
		super(container, slot, x, y);
		this.interactionAllowed = Objects.requireNonNull(interactionAllowed, "interactionAllowed");
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return interactionAllowed.getAsBoolean()
				&& stack != null
				&& !stack.isEmpty()
				&& stack.getCount() > 0
				&& container.canPlaceItem(index, stack);
	}

	@Override
	public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
		return interactionAllowed.getAsBoolean();
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return 1;
	}
}
