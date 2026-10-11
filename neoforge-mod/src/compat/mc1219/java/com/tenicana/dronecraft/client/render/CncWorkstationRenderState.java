package com.tenicana.dronecraft.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public final class CncWorkstationRenderState extends BlockEntityRenderState {
    public CncVisualState visual;
    public float facingDegrees;
    public final ItemStackRenderState workpiece = new ItemStackRenderState();
}
