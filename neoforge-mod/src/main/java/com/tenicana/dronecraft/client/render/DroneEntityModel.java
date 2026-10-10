package com.tenicana.dronecraft.client.render;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import com.tenicana.dronecraft.entity.RotorLayoutCodec;

public class DroneEntityModel extends Model {
	public static final String MODEL_ID = "drone_design_v1";
	public static final int SOURCE_CUBE_COUNT = ImportedDroneDesign.SOURCE_CUBE_COUNT;
	private static final String BODY = "body";
	private static final String ARM_PREFIX = "assembly_";
	private static final String ROTOR_PREFIX = "rotor_";
	private static final float RPM_TO_RADIANS_PER_TICK = (float) (Math.PI * 2.0 / 1200.0);
	private static final float BODY_FORWARD_Z = 6.0f;

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart pitch;
	private final ModelPart roll;
	private final ModelPart[] arms = new ModelPart[RotorLayoutCodec.MAX_RENDER_ROTORS];
	private final ModelPart[] armBeams = new ModelPart[RotorLayoutCodec.MAX_RENDER_ROTORS];
	private final ModelPart[] rotors = new ModelPart[RotorLayoutCodec.MAX_RENDER_ROTORS];

	public DroneEntityModel(ModelPart root) {
		super(RenderType::entityCutoutNoCull);
		this.root = root;
		this.body = root.getChild(BODY);
		this.pitch = body.getChild("pitch");
		this.roll = pitch.getChild("roll");
		ModelPart design = roll.getChild("design");
		for (int i = 0; i < RotorLayoutCodec.MAX_RENDER_ROTORS; i++) {
			this.arms[i] = design.getChild(ARM_PREFIX + i);
			this.armBeams[i] = arms[i].getChild("arm_" + (i & 3) + "_carbon_beam");
			this.rotors[i] = design.getChild(ROTOR_PREFIX + i);
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// The imported design adds four model units below this original flight rotation center.
		PartDefinition body = root.addOrReplaceChild(BODY, CubeListBuilder.create(), PartPose.offset(0.0f, 20.0f, 0.0f));
		// Separate nodes preserve the same Y-X-Z attitude order as the flight model and FPV camera.
		PartDefinition pitch = body.addOrReplaceChild("pitch", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition roll = pitch.addOrReplaceChild("roll", CubeListBuilder.create(), PartPose.ZERO);
		ImportedDroneDesign.addTo(roll);
		return LayerDefinition.create(mesh, ImportedDroneDesign.TEXTURE_WIDTH, ImportedDroneDesign.TEXTURE_HEIGHT);
	}

	public void setupAnim(DroneEntityRenderState state) {
		root.getAllParts().forEach(ModelPart::resetPose);
		body.yRot = state.yawRadians;
		pitch.xRot = bodyPitchRotationRadians(state.pitchRadians);
		roll.zRot = state.rollRadians;

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
			int template = i & 3;
			float nativeX = x;
			float nativeZ = -z;
			float yaw = (float) (Math.atan2(nativeX, nativeZ)
					- Math.atan2(ImportedDroneDesign.ARM_X[template], ImportedDroneDesign.ARM_Z[template]));
			float armScale = Math.max(0.10f, radius / ImportedDroneDesign.ARM_RADIUS);
			arms[i].setPos(nativeX, ImportedDroneDesign.ARM_ORIGIN_Y - y, nativeZ);
			arms[i].setRotation(0.0f, yaw, 0.0f);
			// Stretch only the carbon beam; the supplied motor, landing pad and guard keep their shape.
			armBeams[i].x = -ImportedDroneDesign.ARM_X[template] * armScale;
			armBeams[i].z = -ImportedDroneDesign.ARM_Z[template] * armScale;
			armBeams[i].zScale = armScale;
			rotors[i].setPos(nativeX, ImportedDroneDesign.ROTOR_ORIGIN_Y - y, nativeZ);
			rotors[i].setRotation(0.0f, rotorAngle(state.ageInTicks, state.rotorRpm[i], state.rotorSpinDirection[i]), 0.0f);
		}
	}

	@Override
	public void renderToBuffer(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
		root.render(poses, vertices, light, overlay, color);
	}

	private static float rotorAngle(float ageInTicks, float rpm, int spinDirection) {
		return spinDirection * ageInTicks * Math.max(0.0f, rpm) * RPM_TO_RADIANS_PER_TICK;
	}

	static float bodyPitchRotationRadians(float pitchRadians) {
		return -pitchRadians;
	}

	static float renderedBodyForwardYOffset(float pitchRadians) {
		return -BODY_FORWARD_Z * (float) Math.sin(bodyPitchRotationRadians(pitchRadians));
	}

	static float renderedBodyForwardYOffsetAfterRendererTransform(float pitchRadians) {
		return -renderedBodyForwardYOffset(pitchRadians);
	}
}
