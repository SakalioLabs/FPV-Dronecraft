package com.tenicana.dronecraft.machine;

import com.tenicana.dronecraft.registry.DroneItems;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** One atomic four-plate job. Materials stay in the input until completion. */
final class CncWorkstationInventory implements Container {
    static final int INPUT = 0;
    static final int OUTPUT = 1;
    static final int PROCESSING_TICKS = 120;
    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private final Runnable changed;
    private ItemStack observedInput = ItemStack.EMPTY;
    private int progress;

    CncWorkstationInventory(Runnable changed) { this.changed = changed; }

    NonNullList<ItemStack> storage() { return items; }
    int progress() { return progress; }

    boolean canProcess() {
        return items.get(INPUT).is(DroneItems.carbonPlate()) && items.get(INPUT).getCount() >= 4
                && items.get(OUTPUT).isEmpty();
    }

    void restoreProgress(int saved) {
        observedInput = items.get(INPUT).copy();
        progress = canProcess() ? Math.max(0, Math.min(PROCESSING_TICKS - 1, saved)) : 0;
    }

    void tick() {
        // Also detect automation that mutates a live stack before calling setChanged.
        if (!ItemStack.matches(observedInput, items.get(INPUT))) setChanged();
        if (!canProcess()) {
            if (progress != 0) { progress = 0; changed.run(); }
            return;
        }
        progress++;
        if (progress == PROCESSING_TICKS) {
            items.get(INPUT).shrink(4);
            if (items.get(INPUT).isEmpty()) items.set(INPUT, ItemStack.EMPTY);
            items.set(OUTPUT, new ItemStack(DroneItems.carbonFrame()));
            observedInput = items.get(INPUT).copy();
            progress = 0;
        }
        changed.run();
    }

    @Override public int getContainerSize() { return 2; }
    @Override public boolean isEmpty() { return items.get(INPUT).isEmpty() && items.get(OUTPUT).isEmpty(); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override public void setChanged() {
        if (!ItemStack.matches(observedInput, items.get(INPUT))) {
            progress = 0;
            observedInput = items.get(INPUT).copy();
        }
        if (!canProcess()) progress = 0;
        changed.run();
    }

    @Override public boolean stillValid(Player player) { return true; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && stack.is(DroneItems.carbonPlate());
    }
    @Override public void clearContent() {
        items.clear();
        progress = 0;
        observedInput = ItemStack.EMPTY;
        changed.run();
    }
}
