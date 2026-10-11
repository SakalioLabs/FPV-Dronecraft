package com.tenicana.dronecraft.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class CncWorkstationBlockEntity extends AbstractCncWorkstationBlockEntity {
    public CncWorkstationBlockEntity(BlockPos position, BlockState state) { super(position, state); }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, storage());
        output.putInt("CncProgress", getProgress());
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        storage().clear();
        ContainerHelper.loadAllItems(input, storage());
        restoreProgress(input.getIntOr("CncProgress", 0));
    }

    @Override public void preRemoveSideEffects(BlockPos position, BlockState state) {
        dropContentsOnce();
        super.preRemoveSideEffects(position, state);
    }
}
