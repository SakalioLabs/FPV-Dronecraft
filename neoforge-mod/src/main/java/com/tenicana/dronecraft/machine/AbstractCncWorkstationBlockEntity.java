package com.tenicana.dronecraft.machine;

import com.tenicana.dronecraft.menu.CncWorkstationMenu;
import com.tenicana.dronecraft.registry.DroneBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Runtime shared across native save-format adapters. */
public abstract class AbstractCncWorkstationBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    private static final int[] INPUT_SLOTS = {INPUT};
    private static final int[] OUTPUT_SLOTS = {OUTPUT};
    private final CncWorkstationInventory inventory = new CncWorkstationInventory(this::inventoryChanged);
    private long lastClientUpdateTick;
    private int lastSyncedStage = Integer.MIN_VALUE;
    private int lastSyncedInputCount = -1;
    private int lastSyncedOutputCount = -1;
    private boolean lastSyncedProcessing;
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return index == 0 ? getProgress() : 0; }
        @Override public void set(int index, int value) { if (index == 0) AbstractCncWorkstationBlockEntity.this.restoreProgress(value); }
        @Override public int getCount() { return 1; }
    };

    protected AbstractCncWorkstationBlockEntity(BlockPos position, BlockState state) {
        super(DroneBlocks.CNC_WORKSTATION_ENTITY.get(), position, state);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, CncWorkstationBlockEntity machine) {
        if (!level.isClientSide() && !machine.isRemoved()) ((AbstractCncWorkstationBlockEntity) machine).inventory.tick();
    }

    public int getProgress() { return inventory.progress(); }
    public int getProcessingTicks() { return CncWorkstationInventory.PROCESSING_TICKS; }
    public boolean isProcessing() { return inventory.canProcess() && getProgress() > 0; }
    public int getProgressAgeTicks() {
        return level == null || !level.isClientSide() ? 0 : (int) Math.max(0, Math.min(10, level.getGameTime() - lastClientUpdateTick));
    }
    public int getWorkpieceStage() {
        if (getItem(OUTPUT).is(com.tenicana.dronecraft.registry.DroneItems.carbonFrame())) return 4;
        if (!getItem(INPUT).is(com.tenicana.dronecraft.registry.DroneItems.carbonPlate()) || getItem(INPUT).getCount() < 4) return -1;
        double fraction = (double) getProgress() / getProcessingTicks();
        return fraction < 0.2 ? 0 : fraction < 0.4 ? 1 : fraction < 0.65 ? 2 : fraction < 0.9 ? 3 : 4;
    }
    public Container getInventory() { return this; }
    public ContainerData menuData() { return data; }
    protected NonNullList<ItemStack> storage() { return inventory.storage(); }
    protected void restoreProgress(int value) {
        inventory.restoreProgress(value);
        if (level != null && level.isClientSide()) lastClientUpdateTick = level.getGameTime();
    }

    @Override public void setLevel(Level level) {
        super.setLevel(level);
        if (level.isClientSide()) lastClientUpdateTick = level.getGameTime();
    }

    private void inventoryChanged() {
        super.setChanged();
        if (level == null || level.isClientSide() || isRemoved()) return;
        int stage = getWorkpieceStage(), inputCount = getItem(INPUT).getCount(), outputCount = getItem(OUTPUT).getCount();
        boolean processing = isProcessing();
        if (getProgress() % 10 == 0 || processing != lastSyncedProcessing || stage != lastSyncedStage
                || inputCount != lastSyncedInputCount || outputCount != lastSyncedOutputCount) {
            lastSyncedStage = stage;
            lastSyncedInputCount = inputCount;
            lastSyncedOutputCount = outputCount;
            lastSyncedProcessing = processing;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Drain before spawning any drops so a second removal callback cannot duplicate contents. */
    public void dropContentsOnce() {
        if (level == null || level.isClientSide() || inventory.isEmpty()) return;
        ItemStack input = getItem(INPUT).copy(), output = getItem(OUTPUT).copy();
        inventory.clearContent();
        Containers.dropContents(level, worldPosition, new SimpleContainer(input, output));
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public Component getDisplayName() { return Component.translatable("container.fpvdrone.cnc_workstation"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CncWorkstationMenu(id, inventory, (CncWorkstationBlockEntity) this);
    }
    @Override public int getContainerSize() { return inventory.getContainerSize(); }
    @Override public boolean isEmpty() { return inventory.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return inventory.getItem(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { return inventory.removeItem(slot, amount); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return inventory.removeItemNoUpdate(slot); }
    @Override public void setItem(int slot, ItemStack stack) { inventory.setItem(slot, stack); }
    @Override public void setChanged() { inventory.setChanged(); }
    @Override public void clearContent() { inventory.clearContent(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return inventory.canPlaceItem(slot, stack); }
    @Override public boolean stillValid(Player player) {
        return level != null && !isRemoved() && player != null && player.isAlive() && !player.isSpectator()
                && player.level() == level && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }
    @Override public int[] getSlotsForFace(Direction face) { return (face == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS).clone(); }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction face) {
        return face != Direction.DOWN && canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) { return slot == OUTPUT; }
}
