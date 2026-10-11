package com.tenicana.dronecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.entity.RotorLayoutCodec;

public class DroneEntityRenderer extends EntityRenderer<DroneEntity, DroneEntityRenderState> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "textures/entity/drone.png");
	private final DroneEntityModel model;
	private final RotorLayoutRenderCache rotorLayouts = new RotorLayoutRenderCache();

	public DroneEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new DroneEntityModel(context.bakeLayer(DroneModelLayers.DRONE));
		this.shadowRadius = 0.28f;
	}

	@Override
	public DroneEntityRenderState createRenderState() {
		return new DroneEntityRenderState();
	}

	@Override
	public void extractRenderState(DroneEntity entity, DroneEntityRenderState state, float tickProgress) {
		super.extractRenderState(entity, state, tickProgress);
		state.pitchRadians = entity.getInterpolatedRenderPitchRadians(tickProgress);
		state.yawRadians = entity.getInterpolatedRenderYawRadians(tickProgress);
		state.rollRadians = entity.getInterpolatedRenderRollRadians(tickProgress);
		state.motorPower = entity.getMotorPower();
		state.assemblyMask = entity.getAssemblyMask();
		RotorLayoutRenderCache.Snapshot layout = rotorLayouts.get(entity.getRotorLayout());
		state.rotorCount = layout.rotorCount();
		for (int i = 0; i < RotorLayoutCodec.MAX_RENDER_ROTORS; i++) {
			state.rotorRpm[i] = i < entity.getRotorCount() ? entity.getMotorRpm(i) : 0.0f;
			state.rotorXModelUnits[i] = layout.xModelUnits(i);
			state.rotorYModelUnits[i] = layout.yModelUnits(i);
			state.rotorZModelUnits[i] = layout.zModelUnits(i);
			state.rotorSpinDirection[i] = layout.spinDirection(i);
		}
		state.armed = entity.isArmed();
		state.hiddenInFpv = DroneClientState.isFpvActive(entity.level()) && DroneClientState.controlledDrone() == entity;
	}

    @Override
    public void render(DroneEntityRenderState state, PoseStack poses, MultiBufferSource buffers, int light) {
        if (state.hiddenInFpv) return;
        model.setupAnim(state);
        poses.pushPose();
        poses.scale(-1.0f, -1.0f, 1.0f); poses.translate(0.0f, -1.501f, 0.0f);
        model.renderToBuffer(poses, buffers.getBuffer(model.renderType(TEXTURE)), light, OverlayTexture.NO_OVERLAY, -1);
        poses.popPose();
        super.render(state, poses, buffers, light);
    }
}
