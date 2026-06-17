package com.tenicana.dronecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.entity.RotorLayoutCodec;

public class DroneEntityRenderer extends EntityRenderer<DroneEntity, DroneEntityRenderState> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "textures/entity/drone.png");
	private final DroneEntityModel model;

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
		state.pitchRadians = entity.getRenderPitchRadians();
		state.yawRadians = entity.getRenderYawRadians();
		state.rollRadians = entity.getRenderRollRadians();
		state.motorPower = entity.getMotorPower();
		RotorLayoutCodec.Layout layout = RotorLayoutCodec.decode(entity.getRotorLayout());
		state.rotorCount = layout.rotorCount();
		for (int i = 0; i < RotorLayoutCodec.MAX_RENDER_ROTORS; i++) {
			state.rotorRpm[i] = i < entity.getRotorCount() ? entity.getMotorRpm(i) : 0.0f;
			state.rotorXModelUnits[i] = layout.xModelUnits(i);
			state.rotorYModelUnits[i] = layout.yModelUnits(i);
			state.rotorZModelUnits[i] = layout.zModelUnits(i);
			state.rotorSpinDirection[i] = layout.spinDirection(i);
		}
		state.armed = entity.isArmed();
		state.hiddenInFpv = DroneClientState.isFpvActive() && DroneClientState.controlledDrone() == entity;
	}

	@Override
	public void submit(DroneEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitter, CameraRenderState cameraState) {
		if (state.hiddenInFpv) {
			return;
		}
		super.submit(state, poseStack, submitter, cameraState);
		poseStack.pushPose();
		poseStack.scale(-1.0f, -1.0f, 1.0f);
		poseStack.translate(0.0f, -1.501f, 0.0f);
		submitter.submitModel(
				model,
				state,
				poseStack,
				model.renderType(TEXTURE),
				state.lightCoords,
				OverlayTexture.NO_OVERLAY,
				-1,
				null
		);
		poseStack.popPose();
	}
}
