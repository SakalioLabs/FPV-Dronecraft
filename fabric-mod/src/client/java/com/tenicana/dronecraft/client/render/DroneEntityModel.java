package com.tenicana.dronecraft.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import com.tenicana.dronecraft.entity.RotorLayoutCodec;

public class DroneEntityModel extends EntityModel<DroneEntityRenderState> {
	private static final String BODY = "body";
	private static final String ARM_PREFIX = "arm_";
	private static final String ROTOR_PREFIX = "rotor_";
	private static final float RPM_TO_RADIANS_PER_TICK = (float) (Math.PI * 2.0 / 1200.0);
	private static final float ARM_BASE_LENGTH = 24.0f;
	private static final float ARM_Y = -0.5f;
	private static final float ROTOR_Y = -0.6f;
	private static final float BODY_FORWARD_Z = 6.0f;

	private final ModelPart body;
	private final ModelPart[] arms = new ModelPart[RotorLayoutCodec.MAX_RENDER_ROTORS];
	private final ModelPart[] rotors = new ModelPart[RotorLayoutCodec.MAX_RENDER_ROTORS];

	public DroneEntityModel(ModelPart root) {
		super(root);
		this.body = root.getChild(BODY);
		for (int i = 0; i < RotorLayoutCodec.MAX_RENDER_ROTORS; i++) {
			this.arms[i] = body.getChild(ARM_PREFIX + i);
			this.rotors[i] = body.getChild(ROTOR_PREFIX + i);
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild(
				BODY,
				CubeListBuilder.create()
						.texOffs(0, 0).addBox(-4.0f, -2.0f, -6.0f, 8.0f, 4.0f, 12.0f)
						.texOffs(32, 0).addBox(-2.0f, -2.8f, -3.0f, 4.0f, 1.0f, 6.0f)
						.texOffs(32, 8).addBox(-1.5f, -2.7f, 4.4f, 3.0f, 1.4f, 2.8f),
				PartPose.offset(0.0f, 20.0f, 0.0f)
		);
		for (int i = 0; i < RotorLayoutCodec.MAX_RENDER_ROTORS; i++) {
			body.addOrReplaceChild(ARM_PREFIX + i, arm(), PartPose.offset(0.0f, ARM_Y, 0.0f));
			body.addOrReplaceChild(ROTOR_PREFIX + i, rotor(), PartPose.offset(0.0f, ROTOR_Y, 0.0f));
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	private static CubeListBuilder arm() {
		return CubeListBuilder.create()
				.texOffs(0, 16).addBox(-0.35f, -0.35f, 0.0f, 0.7f, 0.7f, ARM_BASE_LENGTH);
	}

	private static CubeListBuilder rotor() {
		return CubeListBuilder.create()
				.texOffs(0, 21).addBox(-5.0f, -0.25f, -0.75f, 10.0f, 0.5f, 1.5f)
				.texOffs(0, 24).addBox(-0.75f, -0.25f, -5.0f, 1.5f, 0.5f, 10.0f);
	}

	@Override
	public void setupAnim(DroneEntityRenderState state) {
		super.setupAnim(state);
		body.xRot = bodyPitchRotationRadians(state.pitchRadians);
		body.yRot = state.yawRadians;
		body.zRot = state.rollRadians;

		int rotorCount = Math.max(1, Math.min(RotorLayoutCodec.MAX_RENDER_ROTORS, state.rotorCount));
		for (int i = 0; i < RotorLayoutCodec.MAX_RENDER_ROTORS; i++) {
			boolean visible = i < rotorCount;
			arms[i].visible = visible;
			rotors[i].visible = visible;
			if (!visible) {
				continue;
			}

			float x = state.rotorXModelUnits[i];
			float y = state.rotorYModelUnits[i];
			float z = state.rotorZModelUnits[i];
			float radius = (float) Math.sqrt(x * x + z * z);
			float yaw = (float) Math.atan2(x, z);
			arms[i].setPos(0.0f, ARM_Y + y, 0.0f);
			arms[i].setRotation(0.0f, yaw, 0.0f);
			arms[i].xScale = 1.0f;
			arms[i].yScale = 1.0f;
			arms[i].zScale = Math.max(0.10f, radius / ARM_BASE_LENGTH);

			rotors[i].setPos(x, ROTOR_Y + y, z);
			rotors[i].xScale = 1.0f;
			rotors[i].yScale = 1.0f;
			rotors[i].zScale = 1.0f;
			rotors[i].setRotation(0.0f, rotorAngle(state.ageInTicks, state.rotorRpm[i], state.rotorSpinDirection[i], phaseOffset(i)), 0.0f);
		}
	}

	private static float rotorAngle(float ageInTicks, float rpm, int spinDirection, float phaseOffset) {
		return spinDirection * ageInTicks * Math.max(0.0f, rpm) * RPM_TO_RADIANS_PER_TICK + phaseOffset;
	}

	static float bodyPitchRotationRadians(float pitchRadians) {
		return pitchRadians;
	}

	static float renderedBodyForwardYOffset(float pitchRadians) {
		return BODY_FORWARD_Z * (float) Math.sin(bodyPitchRotationRadians(pitchRadians));
	}

	static float renderedBodyForwardYOffsetAfterRendererTransform(float pitchRadians) {
		return -renderedBodyForwardYOffset(pitchRadians);
	}

	private static float phaseOffset(int index) {
		return (index & 1) == 0 ? 0.0f : Mth.HALF_PI;
	}
}
