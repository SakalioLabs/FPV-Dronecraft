package com.tenicana.dronecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.machine.CncWorkstationBlockEntity;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

public final class CncWorkstationRenderer implements BlockEntityRenderer<CncWorkstationBlockEntity, CncWorkstationRenderState> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FpvDronecraftMod.MOD_ID, "textures/block/cnc_workstation_v2.png");
    private final CncMachineModel model;
    private final ItemModelResolver items;

    public CncWorkstationRenderer(BlockEntityRendererProvider.Context context) {
        model = new CncMachineModel(context.bakeLayer(CncModelLayers.CNC_WORKSTATION));
        items = context.itemModelResolver();
    }

    @Override
    public CncWorkstationRenderState createRenderState() { return new CncWorkstationRenderState(); }

    @Override
    public void extractRenderState(CncWorkstationBlockEntity machine, CncWorkstationRenderState state,
                                   float partialTick, Vec3 cameraPosition,
                                   ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(machine, state, partialTick, cameraPosition, breakProgress);
        state.visual = CncVisualState.capture(machine, partialTick);
        state.facingDegrees = CncRenderSupport.facingDegrees(machine.getBlockState());
        items.updateForTopItem(state.workpiece, CncRenderSupport.workpiece(machine, state.visual),
                ItemDisplayContext.FIXED, machine.getLevel(), null, machine.getBlockPos().hashCode());
    }

    @Override
    public void submit(CncWorkstationRenderState state, PoseStack poses, SubmitNodeCollector submitter,
                       CameraRenderState cameraState) {
        poses.pushPose();
        CncRenderSupport.machinePose(poses, state.facingDegrees);
        submitter.submitModel(model, state.visual, poses, RenderTypes.entityCutout(TEXTURE),
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0, state.breakProgress);
        int lampLight = state.visual.processing() || state.visual.progress() > 0.0f ? 15728880 : state.lightCoords;
        submitter.submitModel(model, state.visual.withLayer(CncVisualState.Layer.LAMP), poses,
                RenderTypes.entityCutout(TEXTURE), lampLight, OverlayTexture.NO_OVERLAY, 0, state.breakProgress);
        poses.pushPose();
        model.geometry().workpiecePose(state.visual, poses);
        state.workpiece.submit(poses, submitter, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poses.popPose();
        submitter.submitModel(model, state.visual.withLayer(CncVisualState.Layer.GLASS), poses,
                RenderTypes.entityTranslucent(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, 0, state.breakProgress);
        poses.popPose();
    }
}
