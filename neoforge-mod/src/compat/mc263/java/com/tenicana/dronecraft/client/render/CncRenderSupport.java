package com.tenicana.dronecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tenicana.dronecraft.machine.CncWorkstationBlock;
import com.tenicana.dronecraft.machine.CncWorkstationBlockEntity;
import com.tenicana.dronecraft.registry.DroneItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

final class CncRenderSupport {
    private CncRenderSupport() {}

    static float facingDegrees(BlockState state) {
        return 180.0f - state.getValue(CncWorkstationBlock.FACING).toYRot();
    }

    static void machinePose(PoseStack poses, float yaw) {
        poses.translate(0.5, 0.0, 0.5);
        poses.rotateDegrees(Axis.YP, yaw);
        poses.translate(-0.5, 0.0, -0.5);
        // Converts ModelPart's basis back to the source's +Y-up coordinates; no design reflection.
        poses.scale(-1.0f, -1.0f, 1.0f);
    }

    static ItemStack workpiece(CncWorkstationBlockEntity machine, CncVisualState visual) {
        if (!machine.getItem(1).isEmpty()) return machine.getItem(1).copyWithCount(1);
        if (machine.getItem(0).isEmpty()) return ItemStack.EMPTY;
        return visual.stage() == 4 ? new ItemStack(DroneItems.carbonFrame()) : machine.getItem(0).copyWithCount(1);
    }
}
