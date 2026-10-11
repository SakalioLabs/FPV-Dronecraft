package com.tenicana.dronecraft.menu;

import com.tenicana.dronecraft.machine.CncWorkstationBlockEntity;
import com.tenicana.dronecraft.registry.DroneItems;
import com.tenicana.dronecraft.registry.DroneMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Native inventory packets remain authoritative for both the machine and backpack. */
public final class CncWorkstationMenu extends AbstractContainerMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 166;
    private final CncWorkstationBlockEntity machine;
    private final Player viewer;
    private final ContainerData data;

    public CncWorkstationMenu(int id, Inventory inventory, CncWorkstationBlockEntity machine) {
        this(id, inventory, machine, machine, machine.menuData());
    }

    public CncWorkstationMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, resolve(inventory, buffer.readBlockPos()), new SimpleContainer(2), new SimpleContainerData(1));
    }

    private CncWorkstationMenu(int id, Inventory inventory, CncWorkstationBlockEntity machine, Container storage, ContainerData data) {
        super(DroneMenus.CNC_WORKSTATION.get(), id);
        this.machine = machine;
        this.viewer = inventory.player;
        this.data = data;
        addSlot(new Slot(storage, 0, 44, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return stillValid(viewer) && stack.is(DroneItems.carbonPlate()); }
            @Override public boolean mayPickup(Player player) { return stillValid(player); }
        });
        addSlot(new Slot(storage, 1, 116, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return stillValid(player); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        addDataSlots(data);
    }

    private static CncWorkstationBlockEntity resolve(Inventory inventory, BlockPos position) {
        return inventory.player.level().getBlockEntity(position) instanceof CncWorkstationBlockEntity machine ? machine : null;
    }
    public CncWorkstationBlockEntity machine() { return machine; }
    public int getProgress() { return data.get(0); }
    public int getProcessingTicks() { return 120; }
    @Override public boolean stillValid(Player player) { return machine != null && machine.stillValid(player); }
    @Override public void clicked(int slot, int button, ContainerInput type, Player player) {
        if (stillValid(player)) super.clicked(slot, button, type, player);
    }
    @Override public boolean canDragTo(Slot slot) { return stillValid(viewer) && super.canDragTo(slot); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack source = slot.getItem(), original = source.copy();
        boolean moved;
        if (index < 2) moved = moveItemStackTo(source, 2, 38, true);
        else if (source.is(DroneItems.carbonPlate())) moved = moveItemStackTo(source, 0, 1, false);
        else moved = index < 29 ? moveItemStackTo(source, 29, 38, false) : moveItemStackTo(source, 2, 29, false);
        if (!moved || source.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, source);
        return original;
    }
}
