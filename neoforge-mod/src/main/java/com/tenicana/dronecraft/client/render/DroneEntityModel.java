package com.tenicana.dronecraft.client.render;

import java.util.ArrayList;
import java.util.List;

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
	public static final String MODEL_ID = "drone_assembly_v2";
	public static final int SOURCE_CUBE_COUNT = ImportedDroneDesign.SOURCE_CUBE_COUNT;
	private static final String BODY = "body";
	private static final float RPM_TO_RADIANS_PER_TICK = (float) (Math.PI * 2.0 / 1200.0);
	private static final float BODY_FORWARD_Z = 6.0f;

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart pitch;
	private final ModelPart roll;
	private final ModelPart[] allParts;
	private final ModelPart[][] installedParts = new ModelPart[13][];
	private final ModelPart[] loadedStraps;
	private final ModelPart[] emptyStraps;
	private final ModelPart[] arms = new ModelPart[RotorLayoutCodec.MAX_RENDER_ROTORS];
	private final ModelPart[] armBeams = new ModelPart[RotorLayoutCodec.MAX_RENDER_ROTORS];
	private final ModelPart[] motors = new ModelPart[RotorLayoutCodec.MAX_RENDER_ROTORS];
	private final ModelPart[] rotors = new ModelPart[RotorLayoutCodec.MAX_RENDER_ROTORS];
	private final ModelPart[][] positiveBlades = new ModelPart[RotorLayoutCodec.MAX_RENDER_ROTORS][];
	private final ModelPart[][] negativeBlades = new ModelPart[RotorLayoutCodec.MAX_RENDER_ROTORS][];

	public DroneEntityModel(ModelPart root) {
		super(RenderType::entityCutoutNoCull);
		this.root = root;
		this.body = root.getChild(BODY);
		this.pitch = body.getChild("pitch");
		this.roll = pitch.getChild("roll");
		this.allParts = parts(root);
		ModelPart design = roll.getChild("design");
		ModelPart frame = design.getChild("frame");
		this.loadedStraps = parts(frame.getChild("battery_straps_loaded"));
		this.emptyStraps = parts(frame.getChild("battery_straps_empty"));
		for (int i = 0; i < RotorLayoutCodec.MAX_RENDER_ROTORS; i++) {
			int template = ImportedDroneDesign.SOURCE_TEMPLATE[i & 3];
			arms[i] = frame.getChild("assembly_" + i);
			armBeams[i] = arms[i].getChild("arm_" + template + "_carbon_beam");
			motors[i] = design.getChild("motor_" + i);
			rotors[i] = design.getChild("rotor_" + i);
			positiveBlades[i] = parts(rotors[i].getChild("positive"));
			negativeBlades[i] = parts(rotors[i].getChild("negative"));
		}
		for (int i = 0; i < 4; i++) {
			installedParts[i] = parts(motors[i], motors[i + 4]);
			installedParts[i + 4] = parts(rotors[i], rotors[i + 4]);
		}
		String[] electronics = {"esc", "flight_controller", "receiver", "battery", "camera"};
		for (int i = 0; i < electronics.length; i++) {
			installedParts[8 + i] = parts(design.getChild(electronics[i]));
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild(BODY, CubeListBuilder.create(), PartPose.offset(0.0f, 20.0f, 0.0f));
		// Keep the flight model's Y-X-Z attitude order and the previous render rotation center.
		PartDefinition pitch = body.addOrReplaceChild("pitch", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition roll = pitch.addOrReplaceChild("roll", CubeListBuilder.create(), PartPose.ZERO);
		ImportedDroneDesign.addTo(roll);
		return LayerDefinition.create(mesh, ImportedDroneDesign.TEXTURE_WIDTH, ImportedDroneDesign.TEXTURE_HEIGHT);
	}


	public void setupAnim(DroneEntityRenderState state) {
		for (ModelPart part : allParts) {
			part.resetPose();
			part.visible = true;
		}
		body.yRot = state.yawRadians;
		pitch.xRot = bodyPitchRotationRadians(state.pitchRadians);
		roll.zRot = state.rollRadians;
		// Model instances render many entities; restore both component roots and descendants.
		for (int bit = 0; bit < installedParts.length; bit++) {
			setVisible(installedParts[bit], (state.assemblyMask & (1 << bit)) != 0);
		}
		boolean batteryInstalled = (state.assemblyMask & (1 << 11)) != 0;
		setVisible(loadedStraps, batteryInstalled);
		setVisible(emptyStraps, !batteryInstalled);

		int rotorCount = Math.max(1, Math.min(RotorLayoutCodec.MAX_RENDER_ROTORS, state.rotorCount));
		for (int i = 0; i < RotorLayoutCodec.MAX_RENDER_ROTORS; i++) {
			boolean inLayout = i < rotorCount;
			int slot = i & 3;
			boolean motorInstalled = (state.assemblyMask & (1 << slot)) != 0;
			boolean propellerInstalled = (state.assemblyMask & (1 << (slot + 4))) != 0;
			// A missing motor or propeller never removes the frame's arm and guard.
			arms[i].visible = inLayout;
			motors[i].visible = inLayout && motorInstalled;
			rotors[i].visible = inLayout && propellerInstalled;
			setVisible(positiveBlades[i], inLayout && propellerInstalled && state.rotorSpinDirection[i] >= 0);
			setVisible(negativeBlades[i], inLayout && propellerInstalled && state.rotorSpinDirection[i] < 0);
			if (!inLayout) {
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
			armBeams[i].x = -ImportedDroneDesign.ARM_X[template] * armScale;
			armBeams[i].z = -ImportedDroneDesign.ARM_Z[template] * armScale;
			armBeams[i].zScale = armScale;
			motors[i].setPos(nativeX, ImportedDroneDesign.MOTOR_ORIGIN_Y - y, nativeZ);
			motors[i].setRotation(0.0f, yaw, 0.0f);
			rotors[i].setPos(nativeX, ImportedDroneDesign.ROTOR_ORIGIN_Y - y, nativeZ);
			rotors[i].setRotation(0.0f, rotorAngle(state.ageInTicks, state.rotorRpm[i], state.rotorSpinDirection[i]), 0.0f);
		}
	}

	@Override
	public void renderToBuffer(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
		root.render(poses, vertices, light, overlay, color);
	}

	private static ModelPart[] parts(ModelPart... roots) {
		List<ModelPart> result = new ArrayList<>();
		for (ModelPart root : roots) root.getAllParts().iterator().forEachRemaining(result::add);
		return result.toArray(ModelPart[]::new);
	}

	private static void setVisible(ModelPart[] parts, boolean visible) {
		for (ModelPart part : parts) {
			part.visible = visible;
		}
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
