package com.tenicana.dronecraft.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.level.block.state.BlockState;

public final class CncWorkstationBlockEntity extends AbstractCncWorkstationBlockEntity {
    public CncWorkstationBlockEntity(BlockPos position, BlockState state) { super(position, state); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, storage(), registries);
        tag.putInt("CncProgress", getProgress());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storage().clear();
        ContainerHelper.loadAllItems(tag, storage(), registries);
        restoreProgress(tag.getIntOr("CncProgress", 0));
    }

    @Override public void preRemoveSideEffects(BlockPos position, BlockState state) {
        dropContentsOnce();
        super.preRemoveSideEffects(position, state);
    }
}
