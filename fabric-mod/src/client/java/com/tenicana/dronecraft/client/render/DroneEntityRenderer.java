package com.tenicana.dronecraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.entity.RotorLayoutCodec;

public class DroneEntityRenderer extends MobRenderer<DroneEntity, DroneEntityRenderState, DroneEntityModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "textures/entity/drone.png");

	public DroneEntityRenderer(EntityRendererProvider.Context context) {
		super(context, new DroneEntityModel(context.bakeLayer(DroneModelLayers.DRONE)), 0.35f);
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
	}

	@Override
	public Identifier getTextureLocation(DroneEntityRenderState state) {
		return TEXTURE;
	}
}
