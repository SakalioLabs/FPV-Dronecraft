package com.tenicana.dronecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.machine.CncWorkstationBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class CncWorkstationRenderer implements BlockEntityRenderer<CncWorkstationBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FpvDronecraftMod.MOD_ID, "textures/block/cnc_workstation_v2.png");
    private final CncMachineGeometry geometry;
    private final ItemRenderer itemRenderer;

    public CncWorkstationRenderer(BlockEntityRendererProvider.Context context) {
        geometry = new CncMachineGeometry(context.bakeLayer(CncModelLayers.CNC_WORKSTATION));
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(CncWorkstationBlockEntity machine, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        CncVisualState visual = CncVisualState.capture(machine, partialTick);
        poses.pushPose();
        CncRenderSupport.machinePose(poses, CncRenderSupport.facingDegrees(machine.getBlockState()));
        geometry.setup(visual);
        geometry.root().render(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay);
        geometry.setup(visual.withLayer(CncVisualState.Layer.LAMP));
        int lampLight = visual.processing() || visual.progress() > 0.0f ? 15728880 : light;
        geometry.root().render(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), lampLight, overlay);
        ItemStack workpiece = CncRenderSupport.workpiece(machine, visual);
        if (!workpiece.isEmpty()) {
            poses.pushPose();
            geometry.workpiecePose(visual, poses);
            itemRenderer.renderStatic(workpiece, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    poses, buffers, machine.getLevel(), machine.getBlockPos().hashCode());
            poses.popPose();
        }
        geometry.setup(visual.withLayer(CncVisualState.Layer.GLASS));
        geometry.root().render(poses, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, overlay);
        poses.popPose();
    }
}
