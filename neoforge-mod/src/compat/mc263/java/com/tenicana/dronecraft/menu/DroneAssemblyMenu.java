package com.tenicana.dronecraft.menu;

import com.tenicana.dronecraft.assembly.DroneAssemblyInventory;
import com.tenicana.dronecraft.assembly.DroneAssemblySlot;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.registry.DroneMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Native, server-authoritative component transfer for one grounded aircraft. */
public final class DroneAssemblyMenu extends AbstractContainerMenu {
	public static final int IMAGE_WIDTH = 212;
	public static final int IMAGE_HEIGHT = 222;
	private static final int PLAYER_INVENTORY_END = DroneAssemblyInventory.SLOT_COUNT + 36;
	private final DroneEntity drone;
	private final Player viewer;
	private final DroneAssemblyInventory assembly;

	public DroneAssemblyMenu(int containerId, Inventory inventory, DroneEntity drone) {
		this(containerId, inventory, drone, drone.assemblyInventory());
	}

	public DroneAssemblyMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
		this(containerId, inventory, resolveDrone(inventory, data.readVarInt()), new DroneAssemblyInventory(() -> { }));
	}

	private DroneAssemblyMenu(int containerId, Inventory inventory, DroneEntity drone, DroneAssemblyInventory assembly) {
		super(DroneMenus.ASSEMBLY.get(), containerId);
		this.drone = drone;
		this.viewer = inventory.player;
		this.assembly = assembly;
		for (int rotor = 0; rotor < 4; rotor++) addAssemblySlot(rotor, 32 + rotor * 42, 31);
		for (int rotor = 0; rotor < 4; rotor++) addAssemblySlot(rotor + 4, 32 + rotor * 42, 63);
		for (int component = 8; component < 13; component++) addAssemblySlot(component, 20 + (component - 8) * 38, 100);
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column + row * 9 + 9, 25 + column * 18, 140 + row * 18));
		}
		for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 25 + column * 18, 198));
	}

	private static DroneEntity resolveDrone(Inventory inventory, int entityId) {
		Entity entity = inventory.player.level().getEntity(entityId);
		return entity instanceof DroneEntity drone ? drone : null;
	}

	private void addAssemblySlot(int index, int x, int y) {
		addSlot(new DroneAssemblySlot(assembly, index, x, y, this::assemblyInteractionAllowed));
	}

	private boolean assemblyInteractionAllowed() {
		if (!stillValid(viewer)) return false;
		// Native shift-transfer mutates live stacks before Slot.setChanged; save battery charge first.
		if (!viewer.level().isClientSide()) drone.prepareAssemblyTransfer();
		return true;
	}

	public DroneEntity drone() { return drone; }
	public DroneAssemblyInventory assemblyInventory() { return assembly; }

	@Override
	public boolean stillValid(Player player) {
		return drone != null && drone.canPlayerModifyAssembly(player);
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (stillValid(player)) super.clicked(slotId, button, clickType, player);
	}

	@Override
	public boolean canDragTo(Slot slot) {
		return stillValid(viewer) && super.canDragTo(slot);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		if (!stillValid(player) || slotIndex < 0 || slotIndex >= slots.size()) return ItemStack.EMPTY;
		Slot sourceSlot = slots.get(slotIndex);
		if (!sourceSlot.hasItem() || !sourceSlot.mayPickup(player)) return ItemStack.EMPTY;
		if (!player.level().isClientSide()) drone.prepareAssemblyTransfer();
		ItemStack source = sourceSlot.getItem();
		ItemStack original = source.copy();
		boolean moved = slotIndex < DroneAssemblyInventory.SLOT_COUNT
				? moveItemStackTo(source, DroneAssemblyInventory.SLOT_COUNT, PLAYER_INVENTORY_END, true)
				: moveItemStackTo(source, 0, DroneAssemblyInventory.SLOT_COUNT, false);
		if (!moved || source.getCount() == original.getCount()) return ItemStack.EMPTY;
		if (source.isEmpty()) sourceSlot.set(ItemStack.EMPTY);
		else sourceSlot.setChanged();
		sourceSlot.onTake(player, source);
		return original;
	}
}
