package com.tenicana.dronecraft.client.render;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Imported from the supplied 337-cube Blockbench assembly design, retaining its box UV and pivots. */
final class ImportedDroneDesign {
	static final String SOURCE_SHA256 = "9d54f70d0655f06d3350bc95102000a63b6acc4343952c47f0c6d173fd2b37cf";
	static final String TEXTURE_SHA256 = "ed7162c4a601d80336536ba510c14de8dc82488c3cb385466f348805e823b541";
	static final int SOURCE_CUBE_COUNT = 337;
	static final int SOURCE_GROUP_COUNT = 31;
	static final int TEXTURE_WIDTH = 512;
	static final int TEXTURE_HEIGHT = 512;
	static final float ARM_ORIGIN_Y = -2.6f;
	static final float MOTOR_ORIGIN_Y = -3.7f;
	static final float ROTOR_ORIGIN_Y = -4.9f;
	static final float ARM_RADIUS = (float) Math.sqrt(12.0 * 12.0 * 2.0);
	// Default physical rotor order differs from the source rear-left/rear-right order.
	static final int[] SOURCE_TEMPLATE = {0, 1, 3, 2};
	static final float[] ARM_X = {12.0f, -12.0f, -12.0f, 12.0f};
	static final float[] ARM_Z = {-12.0f, -12.0f, 12.0f, 12.0f};

	private ImportedDroneDesign() {}

	static void addTo(PartDefinition parent) {
		PartDefinition design = parent.addOrReplaceChild("design", CubeListBuilder.create(),
				PartPose.offsetAndRotation(0.0f, 4.0f, 0.0f, 0.0f, (float) Math.PI, 0.0f));
		PartDefinition frame = addFixedParts(design);
		for (int index = 0; index < 8; index++) {
			switch (SOURCE_TEMPLATE[index & 3]) {
				case 0 -> { addArm0(frame, index); addMotor0(design, index); addRotor0(design, index); }
				case 1 -> { addArm1(frame, index); addMotor1(design, index); addRotor1(design, index); }
				case 2 -> { addArm2(frame, index); addMotor2(design, index); addRotor2(design, index); }
				case 3 -> { addArm3(frame, index); addMotor3(design, index); addRotor3(design, index); }
			}
		}
	}

	private static PartDefinition addFixedParts(PartDefinition design) {
		PartDefinition frame = design.addOrReplaceChild("frame", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition part0 = frame.addOrReplaceChild("chassis", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("bottom_protection_plate",
				CubeListBuilder.create().texOffs(2, 2)
						.addBox(-3.58f, -0.505f, -13.68f, 7.16f, 1.01f, 27.36f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, -1.8f, 0.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("lower_carbon_plate",
				CubeListBuilder.create().texOffs(74, 2)
						.addBox(-3.905f, -0.505f, -11.705f, 7.81f, 1.01f, 23.41f, new CubeDeformation(-0.205f)), PartPose.offsetAndRotation(0.0f, -2.4f, 0.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("upper_carbon_plate_front",
				CubeListBuilder.create().texOffs(139, 2)
						.addBox(-3.48f, -0.505f, -4.68f, 6.96f, 1.01f, 9.36f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, -4.15f, -7.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("upper_carbon_plate_rear",
				CubeListBuilder.create().texOffs(174, 2)
						.addBox(-3.48f, -0.505f, -5.18f, 6.96f, 1.01f, 10.36f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, -4.15f, 7.5f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("upper_carbon_service_rail_-2.9",
				CubeListBuilder.create().texOffs(211, 2)
						.addBox(-0.58f, -0.505f, -2.68f, 1.16f, 1.01f, 5.36f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(2.9f, -4.15f, 0.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("upper_carbon_service_rail_2.9",
				CubeListBuilder.create().texOffs(227, 2)
						.addBox(-0.58f, -0.505f, -2.68f, 1.16f, 1.01f, 5.36f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(-2.9f, -4.15f, 0.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("front_vent_cover",
				CubeListBuilder.create().texOffs(243, 2)
						.addBox(-3.355f, -0.505f, -2.905f, 6.71f, 1.01f, 5.81f, new CubeDeformation(-0.305f)), PartPose.offsetAndRotation(0.0f, -4.55f, -9.3f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("rear_vent_cover",
				CubeListBuilder.create().texOffs(271, 2)
						.addBox(-2.9f, -1.05f, -0.8f, 5.8f, 2.1f, 1.6f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, -3.6f, 12.4f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("underside_service_panel",
				CubeListBuilder.create().texOffs(288, 2)
						.addBox(-2.805f, -0.505f, -6.555f, 5.61f, 1.01f, 13.11f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -1.38f, 1.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("stack_standoff_-2.8_-9.8",
				CubeListBuilder.create().texOffs(328, 2)
						.addBox(-0.505f, -1.005f, -0.505f, 1.01f, 2.01f, 1.01f, new CubeDeformation(-0.205f)), PartPose.offsetAndRotation(2.8f, -3.15f, -9.8f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("plate_screw_-2.8_-9.8",
				CubeListBuilder.create().texOffs(335, 2)
						.addBox(-0.78f, -0.505f, -0.78f, 1.56f, 1.01f, 1.56f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(2.8f, -4.57f, -9.8f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("bottom_screw_-2.8_-9.8",
				CubeListBuilder.create().texOffs(344, 2)
						.addBox(-0.805f, -0.505f, -0.805f, 1.61f, 1.01f, 1.61f, new CubeDeformation(-0.43f)), PartPose.offsetAndRotation(2.8f, -1.34f, -9.8f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("stack_standoff_-2.8_9.8",
				CubeListBuilder.create().texOffs(353, 2)
						.addBox(-0.505f, -1.005f, -0.505f, 1.01f, 2.01f, 1.01f, new CubeDeformation(-0.205f)), PartPose.offsetAndRotation(2.8f, -3.15f, 9.8f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("plate_screw_-2.8_9.8",
				CubeListBuilder.create().texOffs(360, 2)
						.addBox(-0.78f, -0.505f, -0.78f, 1.56f, 1.01f, 1.56f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(2.8f, -4.57f, 9.8f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("bottom_screw_-2.8_9.8",
				CubeListBuilder.create().texOffs(369, 2)
						.addBox(-0.805f, -0.505f, -0.805f, 1.61f, 1.01f, 1.61f, new CubeDeformation(-0.43f)), PartPose.offsetAndRotation(2.8f, -1.34f, 9.8f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("stack_standoff_2.8_-9.8",
				CubeListBuilder.create().texOffs(378, 2)
						.addBox(-0.505f, -1.005f, -0.505f, 1.01f, 2.01f, 1.01f, new CubeDeformation(-0.205f)), PartPose.offsetAndRotation(-2.8f, -3.15f, -9.8f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("plate_screw_2.8_-9.8",
				CubeListBuilder.create().texOffs(385, 2)
						.addBox(-0.78f, -0.505f, -0.78f, 1.56f, 1.01f, 1.56f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(-2.8f, -4.57f, -9.8f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("bottom_screw_2.8_-9.8",
				CubeListBuilder.create().texOffs(394, 2)
						.addBox(-0.805f, -0.505f, -0.805f, 1.61f, 1.01f, 1.61f, new CubeDeformation(-0.43f)), PartPose.offsetAndRotation(-2.8f, -1.34f, -9.8f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("stack_standoff_2.8_9.8",
				CubeListBuilder.create().texOffs(403, 2)
						.addBox(-0.505f, -1.005f, -0.505f, 1.01f, 2.01f, 1.01f, new CubeDeformation(-0.205f)), PartPose.offsetAndRotation(-2.8f, -3.15f, 9.8f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("plate_screw_2.8_9.8",
				CubeListBuilder.create().texOffs(410, 2)
						.addBox(-0.78f, -0.505f, -0.78f, 1.56f, 1.01f, 1.56f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(-2.8f, -4.57f, 9.8f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("bottom_screw_2.8_9.8",
				CubeListBuilder.create().texOffs(419, 2)
						.addBox(-0.805f, -0.505f, -0.805f, 1.61f, 1.01f, 1.61f, new CubeDeformation(-0.43f)), PartPose.offsetAndRotation(-2.8f, -1.34f, 9.8f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("side_rail_-3.35",
				CubeListBuilder.create().texOffs(428, 2)
						.addBox(-0.505f, -0.73f, -10.83f, 1.01f, 1.46f, 21.66f, new CubeDeformation(-0.33f)), PartPose.offsetAndRotation(3.35f, -3.1f, 0.3f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("side_rail_3.35",
				CubeListBuilder.create().texOffs(2, 33)
						.addBox(-0.505f, -0.73f, -10.83f, 1.01f, 1.46f, 21.66f, new CubeDeformation(-0.33f)), PartPose.offsetAndRotation(-3.35f, -3.1f, 0.3f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("pcb_stack_post_-2.15_-2.15",
				CubeListBuilder.create().texOffs(50, 33)
						.addBox(-0.505f, -0.915f, -0.505f, 1.01f, 1.83f, 1.01f, new CubeDeformation(-0.365f)), PartPose.offsetAndRotation(2.15f, -3.25f, -2.15f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("pcb_stack_screw_-2.15_-2.15",
				CubeListBuilder.create().texOffs(57, 33)
						.addBox(-0.645f, -0.505f, -0.645f, 1.29f, 1.01f, 1.29f, new CubeDeformation(-0.465f)), PartPose.offsetAndRotation(2.15f, -3.76f, -2.15f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("pcb_stack_post_-2.15_2.15",
				CubeListBuilder.create().texOffs(65, 33)
						.addBox(-0.505f, -0.915f, -0.505f, 1.01f, 1.83f, 1.01f, new CubeDeformation(-0.365f)), PartPose.offsetAndRotation(2.15f, -3.25f, 2.15f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("pcb_stack_screw_-2.15_2.15",
				CubeListBuilder.create().texOffs(72, 33)
						.addBox(-0.645f, -0.505f, -0.645f, 1.29f, 1.01f, 1.29f, new CubeDeformation(-0.465f)), PartPose.offsetAndRotation(2.15f, -3.76f, 2.15f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("pcb_stack_post_2.15_-2.15",
				CubeListBuilder.create().texOffs(80, 33)
						.addBox(-0.505f, -0.915f, -0.505f, 1.01f, 1.83f, 1.01f, new CubeDeformation(-0.365f)), PartPose.offsetAndRotation(-2.15f, -3.25f, -2.15f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("pcb_stack_screw_2.15_-2.15",
				CubeListBuilder.create().texOffs(87, 33)
						.addBox(-0.645f, -0.505f, -0.645f, 1.29f, 1.01f, 1.29f, new CubeDeformation(-0.465f)), PartPose.offsetAndRotation(-2.15f, -3.76f, -2.15f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("pcb_stack_post_2.15_2.15",
				CubeListBuilder.create().texOffs(95, 33)
						.addBox(-0.505f, -0.915f, -0.505f, 1.01f, 1.83f, 1.01f, new CubeDeformation(-0.365f)), PartPose.offsetAndRotation(-2.15f, -3.25f, 2.15f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("pcb_stack_screw_2.15_2.15",
				CubeListBuilder.create().texOffs(102, 33)
						.addBox(-0.645f, -0.505f, -0.645f, 1.29f, 1.01f, 1.29f, new CubeDeformation(-0.465f)), PartPose.offsetAndRotation(-2.15f, -3.76f, 2.15f, 0.0f, 0.0f, 0.0f));
		PartDefinition part1 = frame.addOrReplaceChild("battery_straps_loaded", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
		PartDefinition part2 = part1.addOrReplaceChild("battery_strap_0", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
		part2.addOrReplaceChild("strap_0_top",
				CubeListBuilder.create().texOffs(2, 59)
						.addBox(-3.53f, -0.505f, -1.005f, 7.06f, 1.01f, 2.01f, new CubeDeformation(-0.38f)), PartPose.offsetAndRotation(0.0f, -8.29f, -3.1f, 0.0f, 0.0f, 0.0f));
		part2.addOrReplaceChild("strap_0_side_-3.1",
				CubeListBuilder.create().texOffs(23, 59)
						.addBox(-0.505f, -2.23f, -1.005f, 1.01f, 4.46f, 2.01f, new CubeDeformation(-0.38f)), PartPose.offsetAndRotation(3.1f, -6.4f, -3.1f, 0.0f, 0.0f, 0.0f));
		part2.addOrReplaceChild("strap_0_side_3.1",
				CubeListBuilder.create().texOffs(32, 59)
						.addBox(-0.505f, -2.23f, -1.005f, 1.01f, 4.46f, 2.01f, new CubeDeformation(-0.38f)), PartPose.offsetAndRotation(-3.1f, -6.4f, -3.1f, 0.0f, 0.0f, 0.0f));
		part2.addOrReplaceChild("strap_0_buckle",
				CubeListBuilder.create().texOffs(41, 59)
						.addBox(-0.505f, -1.105f, -1.18f, 1.01f, 2.21f, 2.36f, new CubeDeformation(-0.38f)), PartPose.offsetAndRotation(-3.26f, -6.7f, -3.1f, 0.0f, 0.0f, 0.0f));
		PartDefinition part3 = part1.addOrReplaceChild("battery_strap_1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
		part3.addOrReplaceChild("strap_1_top",
				CubeListBuilder.create().texOffs(80, 59)
						.addBox(-3.53f, -0.505f, -1.005f, 7.06f, 1.01f, 2.01f, new CubeDeformation(-0.38f)), PartPose.offsetAndRotation(0.0f, -8.29f, 6.4f, 0.0f, 0.0f, 0.0f));
		part3.addOrReplaceChild("strap_1_side_-3.1",
				CubeListBuilder.create().texOffs(101, 59)
						.addBox(-0.505f, -2.23f, -1.005f, 1.01f, 4.46f, 2.01f, new CubeDeformation(-0.38f)), PartPose.offsetAndRotation(3.1f, -6.4f, 6.4f, 0.0f, 0.0f, 0.0f));
		part3.addOrReplaceChild("strap_1_side_3.1",
				CubeListBuilder.create().texOffs(110, 59)
						.addBox(-0.505f, -2.23f, -1.005f, 1.01f, 4.46f, 2.01f, new CubeDeformation(-0.38f)), PartPose.offsetAndRotation(-3.1f, -6.4f, 6.4f, 0.0f, 0.0f, 0.0f));
		part3.addOrReplaceChild("strap_1_buckle",
				CubeListBuilder.create().texOffs(119, 59)
						.addBox(-0.505f, -1.105f, -1.18f, 1.01f, 2.21f, 2.36f, new CubeDeformation(-0.38f)), PartPose.offsetAndRotation(-3.26f, -6.7f, 6.4f, 0.0f, 0.0f, 0.0f));
		PartDefinition part4 = frame.addOrReplaceChild("battery_straps_empty", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
		part4.addOrReplaceChild("empty_strap_0",
				CubeListBuilder.create().texOffs(50, 59)
						.addBox(-3.565f, -0.505f, -1.04f, 7.13f, 1.01f, 2.08f, new CubeDeformation(-0.415f)), PartPose.offsetAndRotation(0.0f, -4.64f, -3.1f, 0.0f, 0.0f, 0.0f));
		part4.addOrReplaceChild("empty_buckle_0",
				CubeListBuilder.create().texOffs(71, 59)
						.addBox(-0.505f, -0.51f, -1.135f, 1.01f, 1.02f, 2.27f, new CubeDeformation(-0.335f)), PartPose.offsetAndRotation(-3.17f, -4.72f, -3.1f, 0.0f, 0.0f, 0.0f));
		part4.addOrReplaceChild("empty_strap_1",
				CubeListBuilder.create().texOffs(128, 59)
						.addBox(-3.565f, -0.505f, -1.04f, 7.13f, 1.01f, 2.08f, new CubeDeformation(-0.415f)), PartPose.offsetAndRotation(0.0f, -4.64f, 6.4f, 0.0f, 0.0f, 0.0f));
		part4.addOrReplaceChild("empty_buckle_1",
				CubeListBuilder.create().texOffs(149, 59)
						.addBox(-0.505f, -0.51f, -1.135f, 1.01f, 1.02f, 2.27f, new CubeDeformation(-0.335f)), PartPose.offsetAndRotation(-3.17f, -4.72f, 6.4f, 0.0f, 0.0f, 0.0f));
		PartDefinition part5 = frame.addOrReplaceChild("camera_mounts", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, -3.05f, -14.7f, 0.0f, 0.0f, 0.0f));
		part5.addOrReplaceChild("camera_bracket_top",
				CubeListBuilder.create().texOffs(227, 59)
						.addBox(-3.23f, -0.505f, -2.08f, 6.46f, 1.01f, 4.16f, new CubeDeformation(-0.28f)), PartPose.offsetAndRotation(0.0f, -1.81f, 0.05f, 0.0f, 0.0f, 0.0f));
		part5.addOrReplaceChild("camera_bracket_bottom",
				CubeListBuilder.create().texOffs(251, 59)
						.addBox(-3.23f, -0.505f, -2.08f, 6.46f, 1.01f, 4.16f, new CubeDeformation(-0.28f)), PartPose.offsetAndRotation(0.0f, 2.09f, 0.05f, 0.0f, 0.0f, 0.0f));
		part5.addOrReplaceChild("camera_bracket_side_-2.73",
				CubeListBuilder.create().texOffs(275, 59)
						.addBox(-0.505f, -2.08f, -2.08f, 1.01f, 4.16f, 4.16f, new CubeDeformation(-0.28f)), PartPose.offsetAndRotation(2.73f, 0.14f, 0.1f, 0.0f, 0.0f, 0.0f));
		part5.addOrReplaceChild("camera_bracket_screw_-2.73",
				CubeListBuilder.create().texOffs(288, 59)
						.addBox(-0.505f, -0.755f, -0.755f, 1.01f, 1.51f, 1.51f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(2.96205f, 0.14f, 0.1f, 0.0f, 0.0f, 0.0f));
		part5.addOrReplaceChild("camera_bracket_side_2.73",
				CubeListBuilder.create().texOffs(296, 59)
						.addBox(-0.505f, -2.08f, -2.08f, 1.01f, 4.16f, 4.16f, new CubeDeformation(-0.28f)), PartPose.offsetAndRotation(-2.73f, 0.14f, 0.1f, 0.0f, 0.0f, 0.0f));
		part5.addOrReplaceChild("camera_bracket_screw_2.73",
				CubeListBuilder.create().texOffs(309, 59)
						.addBox(-0.505f, -0.755f, -0.755f, 1.01f, 1.51f, 1.51f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(-2.96205f, 0.14f, 0.1f, 0.0f, 0.0f, 0.0f));
		PartDefinition part6 = design.addOrReplaceChild("esc", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, -2.94f, 0.0f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_pcb",
				CubeListBuilder.create().texOffs(110, 33)
						.addBox(-3.015f, -0.505f, -3.015f, 6.03f, 1.01f, 6.03f, new CubeDeformation(-0.415f)), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_mosfet_-1.25_-1.45",
				CubeListBuilder.create().texOffs(137, 33)
						.addBox(-0.86f, -0.505f, -0.755f, 1.72f, 1.01f, 1.51f, new CubeDeformation(-0.43f)), PartPose.offsetAndRotation(1.25f, -0.165f, -1.45f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_mosfet_1.25_-1.45",
				CubeListBuilder.create().texOffs(146, 33)
						.addBox(-0.86f, -0.505f, -0.755f, 1.72f, 1.01f, 1.51f, new CubeDeformation(-0.43f)), PartPose.offsetAndRotation(-1.25f, -0.165f, -1.45f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_mosfet_-1.25_1.45",
				CubeListBuilder.create().texOffs(155, 33)
						.addBox(-0.86f, -0.505f, -0.755f, 1.72f, 1.01f, 1.51f, new CubeDeformation(-0.43f)), PartPose.offsetAndRotation(1.25f, -0.165f, 1.45f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_mosfet_1.25_1.45",
				CubeListBuilder.create().texOffs(164, 33)
						.addBox(-0.86f, -0.505f, -0.755f, 1.72f, 1.01f, 1.51f, new CubeDeformation(-0.43f)), PartPose.offsetAndRotation(-1.25f, -0.165f, 1.45f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_motor_pad_-2.42_-1.6",
				CubeListBuilder.create().texOffs(173, 33)
						.addBox(-0.6125f, -0.505f, -0.6925f, 1.225f, 1.01f, 1.385f, new CubeDeformation(-0.4825f)), PartPose.offsetAndRotation(2.42f, -0.115f, -1.6f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_motor_pad_-2.42_-0.55",
				CubeListBuilder.create().texOffs(181, 33)
						.addBox(-0.6125f, -0.505f, -0.6925f, 1.225f, 1.01f, 1.385f, new CubeDeformation(-0.4825f)), PartPose.offsetAndRotation(2.42f, -0.115f, -0.55f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_motor_pad_-2.42_0.55",
				CubeListBuilder.create().texOffs(189, 33)
						.addBox(-0.6125f, -0.505f, -0.6925f, 1.225f, 1.01f, 1.385f, new CubeDeformation(-0.4825f)), PartPose.offsetAndRotation(2.42f, -0.115f, 0.55f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_motor_pad_-2.42_1.6",
				CubeListBuilder.create().texOffs(197, 33)
						.addBox(-0.6125f, -0.505f, -0.6925f, 1.225f, 1.01f, 1.385f, new CubeDeformation(-0.4825f)), PartPose.offsetAndRotation(2.42f, -0.115f, 1.6f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_motor_pad_2.42_-1.6",
				CubeListBuilder.create().texOffs(205, 33)
						.addBox(-0.6125f, -0.505f, -0.6925f, 1.225f, 1.01f, 1.385f, new CubeDeformation(-0.4825f)), PartPose.offsetAndRotation(-2.42f, -0.115f, -1.6f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_motor_pad_2.42_-0.55",
				CubeListBuilder.create().texOffs(213, 33)
						.addBox(-0.6125f, -0.505f, -0.6925f, 1.225f, 1.01f, 1.385f, new CubeDeformation(-0.4825f)), PartPose.offsetAndRotation(-2.42f, -0.115f, -0.55f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_motor_pad_2.42_0.55",
				CubeListBuilder.create().texOffs(221, 33)
						.addBox(-0.6125f, -0.505f, -0.6925f, 1.225f, 1.01f, 1.385f, new CubeDeformation(-0.4825f)), PartPose.offsetAndRotation(-2.42f, -0.115f, 0.55f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_motor_pad_2.42_1.6",
				CubeListBuilder.create().texOffs(229, 33)
						.addBox(-0.6125f, -0.505f, -0.6925f, 1.225f, 1.01f, 1.385f, new CubeDeformation(-0.4825f)), PartPose.offsetAndRotation(-2.42f, -0.115f, 1.6f, 0.0f, 0.0f, 0.0f));
		part6.addOrReplaceChild("esc_power_socket",
				CubeListBuilder.create().texOffs(237, 33)
						.addBox(-1.0f, -0.505f, -0.575f, 2.0f, 1.01f, 1.15f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.2f, 2.5f, 0.0f, 0.0f, 0.0f));
		PartDefinition part7 = design.addOrReplaceChild("flight_controller", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, -3.59f, 0.0f, 0.0f, 0.0f, 0.0f));
		part7.addOrReplaceChild("fc_pcb",
				CubeListBuilder.create().texOffs(246, 33)
						.addBox(-2.915f, -0.505f, -2.915f, 5.83f, 1.01f, 5.83f, new CubeDeformation(-0.415f)), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
		part7.addOrReplaceChild("fc_processor",
				CubeListBuilder.create().texOffs(272, 33)
						.addBox(-1.09f, -0.505f, -1.09f, 2.18f, 1.01f, 2.18f, new CubeDeformation(-0.44f)), PartPose.offsetAndRotation(0.0f, -0.155f, 0.15f, 0.0f, 0.0f, 0.0f));
		part7.addOrReplaceChild("fc_gyro",
				CubeListBuilder.create().texOffs(283, 33)
						.addBox(-0.775f, -0.505f, -0.775f, 1.55f, 1.01f, 1.55f, new CubeDeformation(-0.45f)), PartPose.offsetAndRotation(-1.35f, -0.145f, -0.8f, 0.0f, 0.0f, 0.0f));
		part7.addOrReplaceChild("fc_usb_socket",
				CubeListBuilder.create().texOffs(292, 33)
						.addBox(-0.615f, -0.505f, -0.835f, 1.23f, 1.01f, 1.67f, new CubeDeformation(-0.385f)), PartPose.offsetAndRotation(2.4f, -0.1f, -0.4f, 0.0f, 0.0f, 0.0f));
		part7.addOrReplaceChild("fc_pin_-1.6",
				CubeListBuilder.create().texOffs(300, 33)
						.addBox(-0.585f, -0.505f, -0.645f, 1.17f, 1.01f, 1.29f, new CubeDeformation(-0.47f)), PartPose.offsetAndRotation(1.6f, -0.12f, 2.27f, 0.0f, 0.0f, 0.0f));
		part7.addOrReplaceChild("fc_pin_-0.8",
				CubeListBuilder.create().texOffs(307, 33)
						.addBox(-0.585f, -0.505f, -0.645f, 1.17f, 1.01f, 1.29f, new CubeDeformation(-0.47f)), PartPose.offsetAndRotation(0.8f, -0.12f, 2.27f, 0.0f, 0.0f, 0.0f));
		part7.addOrReplaceChild("fc_pin_0",
				CubeListBuilder.create().texOffs(314, 33)
						.addBox(-0.585f, -0.505f, -0.645f, 1.17f, 1.01f, 1.29f, new CubeDeformation(-0.47f)), PartPose.offsetAndRotation(0.0f, -0.12f, 2.27f, 0.0f, 0.0f, 0.0f));
		part7.addOrReplaceChild("fc_pin_0.8",
				CubeListBuilder.create().texOffs(321, 33)
						.addBox(-0.585f, -0.505f, -0.645f, 1.17f, 1.01f, 1.29f, new CubeDeformation(-0.47f)), PartPose.offsetAndRotation(-0.8f, -0.12f, 2.27f, 0.0f, 0.0f, 0.0f));
		part7.addOrReplaceChild("fc_pin_1.6",
				CubeListBuilder.create().texOffs(328, 33)
						.addBox(-0.585f, -0.505f, -0.645f, 1.17f, 1.01f, 1.29f, new CubeDeformation(-0.47f)), PartPose.offsetAndRotation(-1.6f, -0.12f, 2.27f, 0.0f, 0.0f, 0.0f));
		PartDefinition part8 = design.addOrReplaceChild("receiver", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, -3.08f, 8.4f, 0.0f, 0.0f, 0.0f));
		part8.addOrReplaceChild("receiver_pcb",
				CubeListBuilder.create().texOffs(335, 33)
						.addBox(-1.765f, -0.505f, -2.015f, 3.53f, 1.01f, 4.03f, new CubeDeformation(-0.415f)), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
		part8.addOrReplaceChild("receiver_rf_chip",
				CubeListBuilder.create().texOffs(353, 33)
						.addBox(-0.97f, -0.505f, -1.045f, 1.94f, 1.01f, 2.09f, new CubeDeformation(-0.42f)), PartPose.offsetAndRotation(0.0f, -0.175f, 0.2f, 0.0f, 0.0f, 0.0f));
		part8.addOrReplaceChild("receiver_pin_-0.85",
				CubeListBuilder.create().texOffs(364, 33)
						.addBox(-0.6f, -0.505f, -0.625f, 1.2f, 1.01f, 1.25f, new CubeDeformation(-0.475f)), PartPose.offsetAndRotation(0.85f, -0.12f, -1.45f, 0.0f, 0.0f, 0.0f));
		part8.addOrReplaceChild("receiver_pin_0",
				CubeListBuilder.create().texOffs(371, 33)
						.addBox(-0.6f, -0.505f, -0.625f, 1.2f, 1.01f, 1.25f, new CubeDeformation(-0.475f)), PartPose.offsetAndRotation(0.0f, -0.12f, -1.45f, 0.0f, 0.0f, 0.0f));
		part8.addOrReplaceChild("receiver_pin_0.85",
				CubeListBuilder.create().texOffs(378, 33)
						.addBox(-0.6f, -0.505f, -0.625f, 1.2f, 1.01f, 1.25f, new CubeDeformation(-0.475f)), PartPose.offsetAndRotation(-0.85f, -0.12f, -1.45f, 0.0f, 0.0f, 0.0f));
		part8.addOrReplaceChild("receiver_coax",
				CubeListBuilder.create().texOffs(385, 33)
						.addBox(-0.505f, -0.505f, -1.265f, 1.01f, 1.01f, 2.53f, new CubeDeformation(-0.415f)), PartPose.offsetAndRotation(1.15f, -0.72f, 2.25f, 0.0f, 0.0f, 0.0f));
		part8.addOrReplaceChild("receiver_coax_board_riser",
				CubeListBuilder.create().texOffs(395, 33)
						.addBox(-0.505f, -0.775f, -0.505f, 1.01f, 1.55f, 1.01f, new CubeDeformation(-0.415f)), PartPose.offsetAndRotation(1.15f, -0.37f, 1.5f, 0.0f, 0.0f, 0.0f));
		part8.addOrReplaceChild("receiver_coax_antenna_link",
				CubeListBuilder.create().texOffs(402, 33)
						.addBox(-0.73f, -0.505f, -0.505f, 1.46f, 1.01f, 1.01f, new CubeDeformation(-0.415f)), PartPose.offsetAndRotation(1.375f, -0.72f, 2.9f, 0.0f, 0.0f, 0.0f));
		part8.addOrReplaceChild("receiver_antenna_stem",
				CubeListBuilder.create().texOffs(409, 33)
						.addBox(-0.505f, -1.545f, -0.505f, 1.01f, 3.09f, 1.01f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(1.6f, -1.82f, 2.9f, 0.0f, 0.0f, 0.0f));
		part8.addOrReplaceChild("receiver_antenna_t",
				CubeListBuilder.create().texOffs(416, 33)
						.addBox(-1.895f, -0.505f, -0.505f, 3.79f, 1.01f, 1.01f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(1.6f, -3.04f, 2.9f, 0.0f, 0.0f, 0.0f));
		PartDefinition part9 = design.addOrReplaceChild("battery", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
		part9.addOrReplaceChild("battery_pack",
				CubeListBuilder.create().texOffs(428, 33)
						.addBox(-2.95f, -1.8f, -8.1f, 5.9f, 3.6f, 16.2f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, -6.35f, 2.0f, 0.0f, 0.0f, 0.0f));
		part9.addOrReplaceChild("battery_end_front",
				CubeListBuilder.create().texOffs(475, 33)
						.addBox(-3.33f, -2.13f, -0.505f, 6.66f, 4.26f, 1.01f, new CubeDeformation(-0.355f)), PartPose.offsetAndRotation(0.0f, -6.35f, -6.15f, 0.0f, 0.0f, 0.0f));
		part9.addOrReplaceChild("battery_end_rear",
				CubeListBuilder.create().texOffs(493, 33)
						.addBox(-3.33f, -2.13f, -0.505f, 6.66f, 4.26f, 1.01f, new CubeDeformation(-0.355f)), PartPose.offsetAndRotation(0.0f, -6.35f, 10.15f, 0.0f, 0.0f, 0.0f));
		part9.addOrReplaceChild("battery_connector",
				CubeListBuilder.create().texOffs(158, 59)
						.addBox(-0.705f, -0.505f, -0.655f, 1.41f, 1.01f, 1.31f, new CubeDeformation(-0.105f)), PartPose.offsetAndRotation(-1.6f, -4.95f, 11.15f, 0.0f, 0.0f, 0.0f));
		part9.addOrReplaceChild("battery_cable",
				CubeListBuilder.create().texOffs(166, 59)
						.addBox(-0.505f, -0.505f, -0.905f, 1.01f, 1.01f, 1.81f, new CubeDeformation(-0.305f)), PartPose.offsetAndRotation(-1.6f, -4.65f, 12.0f, 0.0f, 0.0f, 0.0f));
		part9.addOrReplaceChild("battery_lead_pack",
				CubeListBuilder.create().texOffs(174, 59)
						.addBox(-0.505f, -0.505f, -0.81f, 1.01f, 1.01f, 1.62f, new CubeDeformation(-0.335f)), PartPose.offsetAndRotation(-1.6f, -5.2f, 10.58f, 0.0f, 0.0f, 0.0f));
		PartDefinition part10 = design.addOrReplaceChild("camera", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, -3.05f, -14.7f, 0.0f, 0.0f, 0.0f));
		part10.addOrReplaceChild("camera_housing",
				CubeListBuilder.create().texOffs(182, 59)
						.addBox(-2.4f, -1.65f, -1.6f, 4.8f, 3.3f, 3.2f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.15f, 0.1f, 0.0f, 0.0f, 0.0f));
		part10.addOrReplaceChild("camera_front_bezel",
				CubeListBuilder.create().texOffs(200, 59)
						.addBox(-2.505f, -1.855f, -0.505f, 5.01f, 3.71f, 1.01f, new CubeDeformation(-0.355f)), PartPose.offsetAndRotation(0.0f, 0.15f, -1.58f, 0.0f, 0.0f, 0.0f));
		part10.addOrReplaceChild("camera_lens_glass",
				CubeListBuilder.create().texOffs(215, 59)
						.addBox(-1.755f, -1.755f, -0.505f, 3.51f, 3.51f, 1.01f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, 0.15f, -1.81f, 0.0f, 0.0f, 0.0f));
		PartDefinition part11 = part10.addOrReplaceChild("video_tx", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, -0.13f, 8.0f, 0.0f, 0.0f, 0.0f));
		part11.addOrReplaceChild("vtx_pcb",
				CubeListBuilder.create().texOffs(317, 59)
						.addBox(-2.165f, -0.505f, -2.115f, 4.33f, 1.01f, 4.23f, new CubeDeformation(-0.415f)), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
		part11.addOrReplaceChild("vtx_rf_shield",
				CubeListBuilder.create().texOffs(337, 59)
						.addBox(-1.465f, -0.505f, -1.365f, 2.93f, 1.01f, 2.73f, new CubeDeformation(-0.39f)), PartPose.offsetAndRotation(0.0f, -0.21f, 0.0f, 0.0f, 0.0f, 0.0f));
		part11.addOrReplaceChild("vtx_camera_connector",
				CubeListBuilder.create().texOffs(351, 59)
						.addBox(-1.04f, -0.505f, -0.615f, 2.08f, 1.01f, 1.23f, new CubeDeformation(-0.415f)), PartPose.offsetAndRotation(0.0f, -0.14f, -1.55f, 0.0f, 0.0f, 0.0f));
		part11.addOrReplaceChild("camera_video_cable",
				CubeListBuilder.create().texOffs(360, 59)
						.addBox(-0.525f, -0.505f, -2.75f, 1.05f, 1.01f, 5.5f, new CubeDeformation(-0.425f)), PartPose.offsetAndRotation(0.0f, -0.14f, -4.025f, 0.0f, 0.0f, 0.0f));
		part11.addOrReplaceChild("vtx_coax_connector",
				CubeListBuilder.create().texOffs(376, 59)
						.addBox(-0.505f, -0.525f, -0.555f, 1.01f, 1.05f, 1.11f, new CubeDeformation(-0.365f)), PartPose.offsetAndRotation(1.55f, -0.15f, 0.0f, 0.0f, 0.0f, 0.0f));
		part11.addOrReplaceChild("vtx_coax_front_link",
				CubeListBuilder.create().texOffs(383, 59)
						.addBox(-1.05f, -0.505f, -0.505f, 2.1f, 1.01f, 1.01f, new CubeDeformation(-0.425f)), PartPose.offsetAndRotation(2.05f, -0.22f, 0.0f, 0.0f, 0.0f, 0.0f));
		part11.addOrReplaceChild("vtx_coax_body",
				CubeListBuilder.create().texOffs(392, 59)
						.addBox(-0.505f, -0.505f, -10.525f, 1.01f, 1.01f, 21.05f, new CubeDeformation(-0.425f)), PartPose.offsetAndRotation(2.65f, -0.22f, 9.9f, 0.0f, 0.0f, 0.0f));
		part11.addOrReplaceChild("vtx_coax_rear_link",
				CubeListBuilder.create().texOffs(439, 59)
						.addBox(-1.83f, -0.505f, -0.505f, 3.66f, 1.01f, 1.01f, new CubeDeformation(-0.425f)), PartPose.offsetAndRotation(1.325f, -0.22f, 19.95f, 0.0f, 0.0f, 0.0f));
		part11.addOrReplaceChild("vtx_coax_antenna_riser",
				CubeListBuilder.create().texOffs(451, 59)
						.addBox(-0.505f, -1.0f, -0.505f, 1.01f, 2.0f, 1.01f, new CubeDeformation(-0.425f)), PartPose.offsetAndRotation(0.0f, -0.795f, 19.95f, 0.0f, 0.0f, 0.0f));
		PartDefinition part12 = part10.addOrReplaceChild("rear_antenna", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, -1.4f, 27.95f, 0.0f, 0.0f, 0.0f));
		part12.addOrReplaceChild("antenna_socket_0",
				CubeListBuilder.create().texOffs(458, 59)
						.addBox(-1.18f, -0.505f, -0.623503f, 2.36f, 1.01f, 1.247006f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.27f, 0.0f, 0.0f, 0.0f, 0.0f));
		part12.addOrReplaceChild("antenna_socket_1",
				CubeListBuilder.create().texOffs(468, 59)
						.addBox(-1.18f, -0.505f, -0.623503f, 2.36f, 1.01f, 1.247006f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.27f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		part12.addOrReplaceChild("antenna_socket_2",
				CubeListBuilder.create().texOffs(478, 59)
						.addBox(-1.18f, -0.505f, -0.623503f, 2.36f, 1.01f, 1.247006f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.27f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		part12.addOrReplaceChild("antenna_socket_3",
				CubeListBuilder.create().texOffs(488, 59)
						.addBox(-1.18f, -0.505f, -0.623503f, 2.36f, 1.01f, 1.247006f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.27f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		part12.addOrReplaceChild("antenna_mast",
				CubeListBuilder.create().texOffs(498, 59)
						.addBox(-0.505f, -4.765f, -0.505f, 1.01f, 9.53f, 1.01f, new CubeDeformation(-0.265f)), PartPose.offsetAndRotation(0.0f, -5.0f, 0.0f, 0.0f, 0.0f, 0.0f));
		part12.addOrReplaceChild("antenna_faceted_cap_0",
				CubeListBuilder.create().texOffs(2, 84)
						.addBox(-1.061497f, -0.936497f, -0.505f, 2.122994f, 1.872994f, 1.01f, new CubeDeformation(-0.111497f)), PartPose.offsetAndRotation(0.0f, -9.6f, 0.0f, 0.0f, 0.0f, 0.0f));
		part12.addOrReplaceChild("antenna_faceted_cap_1",
				CubeListBuilder.create().texOffs(11, 84)
						.addBox(-1.061497f, -0.936497f, -0.505f, 2.122994f, 1.872994f, 1.01f, new CubeDeformation(-0.111497f)), PartPose.offsetAndRotation(0.0f, -9.6f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		part12.addOrReplaceChild("antenna_faceted_cap_2",
				CubeListBuilder.create().texOffs(20, 84)
						.addBox(-1.061497f, -0.936497f, -0.505f, 2.122994f, 1.872994f, 1.01f, new CubeDeformation(-0.111497f)), PartPose.offsetAndRotation(0.0f, -9.6f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		part12.addOrReplaceChild("antenna_faceted_cap_3",
				CubeListBuilder.create().texOffs(29, 84)
						.addBox(-1.061497f, -0.936497f, -0.505f, 2.122994f, 1.872994f, 1.01f, new CubeDeformation(-0.111497f)), PartPose.offsetAndRotation(0.0f, -9.6f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		part12.addOrReplaceChild("antenna_cap_top_0",
				CubeListBuilder.create().texOffs(38, 84)
						.addBox(-1.13f, -0.505f, -0.719949f, 2.26f, 1.01f, 1.439898f, new CubeDeformation(-0.43f)), PartPose.offsetAndRotation(0.0f, -10.48f, 0.0f, 0.0f, 0.0f, 0.0f));
		part12.addOrReplaceChild("antenna_cap_top_1",
				CubeListBuilder.create().texOffs(48, 84)
						.addBox(-1.13f, -0.505f, -0.719949f, 2.26f, 1.01f, 1.439898f, new CubeDeformation(-0.43f)), PartPose.offsetAndRotation(0.0f, -10.48f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		part12.addOrReplaceChild("antenna_cap_top_2",
				CubeListBuilder.create().texOffs(58, 84)
						.addBox(-1.13f, -0.505f, -0.719949f, 2.26f, 1.01f, 1.439898f, new CubeDeformation(-0.43f)), PartPose.offsetAndRotation(0.0f, -10.48f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		part12.addOrReplaceChild("antenna_cap_top_3",
				CubeListBuilder.create().texOffs(68, 84)
						.addBox(-1.13f, -0.505f, -0.719949f, 2.26f, 1.01f, 1.439898f, new CubeDeformation(-0.43f)), PartPose.offsetAndRotation(0.0f, -10.48f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		return frame;
	}

	private static void addArm0(PartDefinition frame, int index) {
		PartDefinition component = frame.addOrReplaceChild("assembly_" + index, CubeListBuilder.create(), PartPose.offsetAndRotation(12.0f, -2.6f, -12.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("arm_0_carbon_beam",
				CubeListBuilder.create().texOffs(78, 84)
						.addBox(-1.355f, -0.505f, 1.42f, 2.71f, 1.01f, 15.730563f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(-12.0f, -0.05f, 12.0f, 0.0f, 2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_0_mount_0",
				CubeListBuilder.create().texOffs(117, 84)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_0_mount_1",
				CubeListBuilder.create().texOffs(133, 84)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_0_mount_2",
				CubeListBuilder.create().texOffs(149, 84)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_0_mount_3",
				CubeListBuilder.create().texOffs(165, 84)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_0_foot_0",
				CubeListBuilder.create().texOffs(337, 84)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_0_foot_1",
				CubeListBuilder.create().texOffs(346, 84)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_0_foot_2",
				CubeListBuilder.create().texOffs(355, 84)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_0_foot_3",
				CubeListBuilder.create().texOffs(364, 84)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		PartDefinition part0 = component.addOrReplaceChild("guard_0", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, -2.3f, 0.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("guard_0_segment_0",
				CubeListBuilder.create().texOffs(373, 84)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(0.0f, 0.05f, 8.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("guard_0_top_trim_0",
				CubeListBuilder.create().texOffs(392, 84)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(0.0f, -0.45f, 8.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("guard_0_segment_1",
				CubeListBuilder.create().texOffs(413, 84)
						.addBox(-3.59903875051f, -0.58f, -0.505000249492f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(-5.65685424949f, 0.05f, 5.65685424949f, 0.0f, -0.785398163397f, 0.0f));
		part0.addOrReplaceChild("guard_0_top_trim_1",
				CubeListBuilder.create().texOffs(432, 84)
						.addBox(-3.91403875051f, -0.505f, -0.820000249492f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(-5.65685424949f, -0.45f, 5.65685424949f, 0.0f, -0.785398163397f, 0.0f));
		part0.addOrReplaceChild("guard_0_segment_2",
				CubeListBuilder.create().texOffs(453, 84)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(-8.0f, 0.05f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		part0.addOrReplaceChild("guard_0_top_trim_2",
				CubeListBuilder.create().texOffs(472, 84)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(-8.0f, -0.45f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		part0.addOrReplaceChild("guard_0_segment_3",
				CubeListBuilder.create().texOffs(493, 84)
						.addBox(-3.59903875051f, -0.58f, -0.504999750508f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(-5.65685424949f, 0.05f, -5.65685424949f, 0.0f, -2.35619449019f, 0.0f));
		part0.addOrReplaceChild("guard_0_top_trim_3",
				CubeListBuilder.create().texOffs(2, 103)
						.addBox(-3.91403875051f, -0.505f, -0.819999750508f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(-5.65685424949f, -0.45f, -5.65685424949f, 0.0f, -2.35619449019f, 0.0f));
		part0.addOrReplaceChild("guard_0_segment_4",
				CubeListBuilder.create().texOffs(23, 103)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(0.0f, 0.05f, -8.0f, 0.0f, -3.14159265359f, 0.0f));
		part0.addOrReplaceChild("guard_0_top_trim_4",
				CubeListBuilder.create().texOffs(42, 103)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(0.0f, -0.45f, -8.0f, 0.0f, -3.14159265359f, 0.0f));
		part0.addOrReplaceChild("guard_0_segment_5",
				CubeListBuilder.create().texOffs(63, 103)
						.addBox(-3.59903824949f, -0.58f, -0.504999750508f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(5.65685424949f, 0.05f, -5.65685424949f, 0.0f, -3.92699081699f, 0.0f));
		part0.addOrReplaceChild("guard_0_top_trim_5",
				CubeListBuilder.create().texOffs(82, 103)
						.addBox(-3.91403824949f, -0.505f, -0.819999750508f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(5.65685424949f, -0.45f, -5.65685424949f, 0.0f, -3.92699081699f, 0.0f));
		part0.addOrReplaceChild("guard_0_segment_6",
				CubeListBuilder.create().texOffs(103, 103)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(8.0f, 0.05f, 0.0f, 0.0f, -4.71238898038f, 0.0f));
		part0.addOrReplaceChild("guard_0_top_trim_6",
				CubeListBuilder.create().texOffs(122, 103)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(8.0f, -0.45f, 0.0f, 0.0f, -4.71238898038f, 0.0f));
		part0.addOrReplaceChild("guard_0_segment_7",
				CubeListBuilder.create().texOffs(143, 103)
						.addBox(-3.59903824949f, -0.58f, -0.505000249492f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(5.65685424949f, 0.05f, 5.65685424949f, 0.0f, -5.49778714378f, 0.0f));
		part0.addOrReplaceChild("guard_0_top_trim_7",
				CubeListBuilder.create().texOffs(162, 103)
						.addBox(-3.91403824949f, -0.505f, -0.820000249492f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(5.65685424949f, -0.45f, 5.65685424949f, 0.0f, -5.49778714378f, 0.0f));
		part0.addOrReplaceChild("guard_0_support_0",
				CubeListBuilder.create().texOffs(183, 103)
						.addBox(-0.529999553372f, -0.505f, -4.17999955337f, 1.06f, 1.01f, 8.36f, new CubeDeformation(-0.28f)), PartPose.offsetAndRotation(-2.75771644663f, 1.68f, -2.75771644663f, 0.0f, 3.92699081699f, 0.0f));
		part0.addOrReplaceChild("guard_0_support_1",
				CubeListBuilder.create().texOffs(204, 103)
						.addBox(-0.530000446628f, -0.505f, -4.18000044663f, 1.06f, 1.01f, 8.36f, new CubeDeformation(-0.28f)), PartPose.offsetAndRotation(2.75771644663f, 1.68f, 2.75771644663f, 0.0f, 0.785398163397f, 0.0f));
	}

	private static void addMotor0(PartDefinition design, int index) {
		PartDefinition component = design.addOrReplaceChild("motor_" + index, CubeListBuilder.create(), PartPose.offsetAndRotation(12.0f, -3.7f, -12.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_0_base_0",
				CubeListBuilder.create().texOffs(181, 84)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_0_base_1",
				CubeListBuilder.create().texOffs(194, 84)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_0_base_2",
				CubeListBuilder.create().texOffs(207, 84)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_0_base_3",
				CubeListBuilder.create().texOffs(220, 84)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_0_can_0",
				CubeListBuilder.create().texOffs(233, 84)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_0_can_1",
				CubeListBuilder.create().texOffs(245, 84)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_0_can_2",
				CubeListBuilder.create().texOffs(257, 84)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_0_can_3",
				CubeListBuilder.create().texOffs(269, 84)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_0_cap_0",
				CubeListBuilder.create().texOffs(281, 84)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_0_cap_1",
				CubeListBuilder.create().texOffs(295, 84)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_0_cap_2",
				CubeListBuilder.create().texOffs(309, 84)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_0_cap_3",
				CubeListBuilder.create().texOffs(323, 84)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
	}

	private static void addRotor0(PartDefinition design, int index) {
		PartDefinition rotor = design.addOrReplaceChild("rotor_" + index, CubeListBuilder.create(), PartPose.offsetAndRotation(12.0f, -4.9f, -12.0f, 0.0f, 0.0f, 0.0f));
		PartDefinition positive = rotor.addOrReplaceChild("positive", CubeListBuilder.create(), PartPose.ZERO);
		positive.addOrReplaceChild("rotor_0_hub_0",
				CubeListBuilder.create().texOffs(225, 103)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, 0.0f, 0.0f));
		positive.addOrReplaceChild("rotor_0_hub_1",
				CubeListBuilder.create().texOffs(237, 103)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		positive.addOrReplaceChild("rotor_0_hub_2",
				CubeListBuilder.create().texOffs(249, 103)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		positive.addOrReplaceChild("rotor_0_hub_3",
				CubeListBuilder.create().texOffs(261, 103)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		positive.addOrReplaceChild("rotor_0_nut_0",
				CubeListBuilder.create().texOffs(273, 103)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, 0.0f, 0.0f));
		positive.addOrReplaceChild("rotor_0_nut_1",
				CubeListBuilder.create().texOffs(281, 103)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		positive.addOrReplaceChild("rotor_0_nut_2",
				CubeListBuilder.create().texOffs(289, 103)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		positive.addOrReplaceChild("rotor_0_nut_3",
				CubeListBuilder.create().texOffs(297, 103)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_0_0",
				CubeListBuilder.create().texOffs(305, 103)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -0.349065850399f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_0_1",
				CubeListBuilder.create().texOffs(317, 103)
						.addBox(-1.46f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -0.349065850399f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_0_2",
				CubeListBuilder.create().texOffs(332, 103)
						.addBox(-1.295f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -0.349065850399f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_1_0",
				CubeListBuilder.create().texOffs(342, 103)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -2.44346095279f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_1_1",
				CubeListBuilder.create().texOffs(354, 103)
						.addBox(-1.46f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -2.44346095279f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_1_2",
				CubeListBuilder.create().texOffs(369, 103)
						.addBox(-1.295f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -2.44346095279f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_2_0",
				CubeListBuilder.create().texOffs(379, 103)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -4.53785605519f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_2_1",
				CubeListBuilder.create().texOffs(391, 103)
						.addBox(-1.46f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -4.53785605519f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_2_2",
				CubeListBuilder.create().texOffs(406, 103)
						.addBox(-1.295f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -4.53785605519f, 0.0f));
		PartDefinition negative = rotor.addOrReplaceChild("negative", CubeListBuilder.create(), PartPose.ZERO);
		negative.addOrReplaceChild("rotor_1_hub_0",
				CubeListBuilder.create().texOffs(65, 128)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, 0.0f, 0.0f));
		negative.addOrReplaceChild("rotor_1_hub_1",
				CubeListBuilder.create().texOffs(77, 128)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		negative.addOrReplaceChild("rotor_1_hub_2",
				CubeListBuilder.create().texOffs(89, 128)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		negative.addOrReplaceChild("rotor_1_hub_3",
				CubeListBuilder.create().texOffs(101, 128)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		negative.addOrReplaceChild("rotor_1_nut_0",
				CubeListBuilder.create().texOffs(113, 128)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, 0.0f, 0.0f));
		negative.addOrReplaceChild("rotor_1_nut_1",
				CubeListBuilder.create().texOffs(121, 128)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		negative.addOrReplaceChild("rotor_1_nut_2",
				CubeListBuilder.create().texOffs(129, 128)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		negative.addOrReplaceChild("rotor_1_nut_3",
				CubeListBuilder.create().texOffs(137, 128)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_0_0",
				CubeListBuilder.create().texOffs(145, 128)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 0.349065850399f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_0_1",
				CubeListBuilder.create().texOffs(157, 128)
						.addBox(-0.98f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 0.349065850399f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_0_2",
				CubeListBuilder.create().texOffs(172, 128)
						.addBox(-0.595f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 0.349065850399f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_1_0",
				CubeListBuilder.create().texOffs(182, 128)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 2.44346095279f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_1_1",
				CubeListBuilder.create().texOffs(194, 128)
						.addBox(-0.98f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 2.44346095279f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_1_2",
				CubeListBuilder.create().texOffs(209, 128)
						.addBox(-0.595f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 2.44346095279f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_2_0",
				CubeListBuilder.create().texOffs(219, 128)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 4.53785605519f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_2_1",
				CubeListBuilder.create().texOffs(231, 128)
						.addBox(-0.98f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 4.53785605519f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_2_2",
				CubeListBuilder.create().texOffs(246, 128)
						.addBox(-0.595f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 4.53785605519f, 0.0f));
	}

	private static void addArm1(PartDefinition frame, int index) {
		PartDefinition component = frame.addOrReplaceChild("assembly_" + index, CubeListBuilder.create(), PartPose.offsetAndRotation(-12.0f, -2.6f, -12.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("arm_1_carbon_beam",
				CubeListBuilder.create().texOffs(416, 103)
						.addBox(-1.355f, -0.505f, 1.42f, 2.71f, 1.01f, 15.730563f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(12.0f, -0.05f, 12.0f, 0.0f, -2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_1_mount_0",
				CubeListBuilder.create().texOffs(455, 103)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_1_mount_1",
				CubeListBuilder.create().texOffs(471, 103)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_1_mount_2",
				CubeListBuilder.create().texOffs(487, 103)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_1_mount_3",
				CubeListBuilder.create().texOffs(2, 122)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_1_foot_0",
				CubeListBuilder.create().texOffs(174, 122)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_1_foot_1",
				CubeListBuilder.create().texOffs(183, 122)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_1_foot_2",
				CubeListBuilder.create().texOffs(192, 122)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_1_foot_3",
				CubeListBuilder.create().texOffs(201, 122)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		PartDefinition part0 = component.addOrReplaceChild("guard_1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, -2.3f, 0.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("guard_1_segment_0",
				CubeListBuilder.create().texOffs(210, 122)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(0.0f, 0.05f, 8.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("guard_1_top_trim_0",
				CubeListBuilder.create().texOffs(229, 122)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(0.0f, -0.45f, 8.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("guard_1_segment_1",
				CubeListBuilder.create().texOffs(250, 122)
						.addBox(-3.59903875051f, -0.58f, -0.505000249492f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(-5.65685424949f, 0.05f, 5.65685424949f, 0.0f, -0.785398163397f, 0.0f));
		part0.addOrReplaceChild("guard_1_top_trim_1",
				CubeListBuilder.create().texOffs(269, 122)
						.addBox(-3.91403875051f, -0.505f, -0.820000249492f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(-5.65685424949f, -0.45f, 5.65685424949f, 0.0f, -0.785398163397f, 0.0f));
		part0.addOrReplaceChild("guard_1_segment_2",
				CubeListBuilder.create().texOffs(290, 122)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(-8.0f, 0.05f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		part0.addOrReplaceChild("guard_1_top_trim_2",
				CubeListBuilder.create().texOffs(309, 122)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(-8.0f, -0.45f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		part0.addOrReplaceChild("guard_1_segment_3",
				CubeListBuilder.create().texOffs(330, 122)
						.addBox(-3.59903875051f, -0.58f, -0.504999750508f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(-5.65685424949f, 0.05f, -5.65685424949f, 0.0f, -2.35619449019f, 0.0f));
		part0.addOrReplaceChild("guard_1_top_trim_3",
				CubeListBuilder.create().texOffs(349, 122)
						.addBox(-3.91403875051f, -0.505f, -0.819999750508f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(-5.65685424949f, -0.45f, -5.65685424949f, 0.0f, -2.35619449019f, 0.0f));
		part0.addOrReplaceChild("guard_1_segment_4",
				CubeListBuilder.create().texOffs(370, 122)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(0.0f, 0.05f, -8.0f, 0.0f, -3.14159265359f, 0.0f));
		part0.addOrReplaceChild("guard_1_top_trim_4",
				CubeListBuilder.create().texOffs(389, 122)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(0.0f, -0.45f, -8.0f, 0.0f, -3.14159265359f, 0.0f));
		part0.addOrReplaceChild("guard_1_segment_5",
				CubeListBuilder.create().texOffs(410, 122)
						.addBox(-3.59903824949f, -0.58f, -0.504999750508f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(5.65685424949f, 0.05f, -5.65685424949f, 0.0f, -3.92699081699f, 0.0f));
		part0.addOrReplaceChild("guard_1_top_trim_5",
				CubeListBuilder.create().texOffs(429, 122)
						.addBox(-3.91403824949f, -0.505f, -0.819999750508f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(5.65685424949f, -0.45f, -5.65685424949f, 0.0f, -3.92699081699f, 0.0f));
		part0.addOrReplaceChild("guard_1_segment_6",
				CubeListBuilder.create().texOffs(450, 122)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(8.0f, 0.05f, 0.0f, 0.0f, -4.71238898038f, 0.0f));
		part0.addOrReplaceChild("guard_1_top_trim_6",
				CubeListBuilder.create().texOffs(469, 122)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(8.0f, -0.45f, 0.0f, 0.0f, -4.71238898038f, 0.0f));
		part0.addOrReplaceChild("guard_1_segment_7",
				CubeListBuilder.create().texOffs(490, 122)
						.addBox(-3.59903824949f, -0.58f, -0.505000249492f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(5.65685424949f, 0.05f, 5.65685424949f, 0.0f, -5.49778714378f, 0.0f));
		part0.addOrReplaceChild("guard_1_top_trim_7",
				CubeListBuilder.create().texOffs(2, 128)
						.addBox(-3.91403824949f, -0.505f, -0.820000249492f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(5.65685424949f, -0.45f, 5.65685424949f, 0.0f, -5.49778714378f, 0.0f));
		part0.addOrReplaceChild("guard_1_support_0",
				CubeListBuilder.create().texOffs(23, 128)
						.addBox(-0.529999553372f, -0.505f, -4.18000044663f, 1.06f, 1.01f, 8.36f, new CubeDeformation(-0.28f)), PartPose.offsetAndRotation(-2.75771644663f, 1.68f, 2.75771644663f, 0.0f, -0.785398163397f, 0.0f));
		part0.addOrReplaceChild("guard_1_support_1",
				CubeListBuilder.create().texOffs(44, 128)
						.addBox(-0.530000446628f, -0.505f, -4.17999955337f, 1.06f, 1.01f, 8.36f, new CubeDeformation(-0.28f)), PartPose.offsetAndRotation(2.75771644663f, 1.68f, -2.75771644663f, 0.0f, -3.92699081699f, 0.0f));
	}

	private static void addMotor1(PartDefinition design, int index) {
		PartDefinition component = design.addOrReplaceChild("motor_" + index, CubeListBuilder.create(), PartPose.offsetAndRotation(-12.0f, -3.7f, -12.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_1_base_0",
				CubeListBuilder.create().texOffs(18, 122)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_1_base_1",
				CubeListBuilder.create().texOffs(31, 122)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_1_base_2",
				CubeListBuilder.create().texOffs(44, 122)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_1_base_3",
				CubeListBuilder.create().texOffs(57, 122)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_1_can_0",
				CubeListBuilder.create().texOffs(70, 122)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_1_can_1",
				CubeListBuilder.create().texOffs(82, 122)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_1_can_2",
				CubeListBuilder.create().texOffs(94, 122)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_1_can_3",
				CubeListBuilder.create().texOffs(106, 122)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_1_cap_0",
				CubeListBuilder.create().texOffs(118, 122)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_1_cap_1",
				CubeListBuilder.create().texOffs(132, 122)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_1_cap_2",
				CubeListBuilder.create().texOffs(146, 122)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_1_cap_3",
				CubeListBuilder.create().texOffs(160, 122)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
	}

	private static void addRotor1(PartDefinition design, int index) {
		PartDefinition rotor = design.addOrReplaceChild("rotor_" + index, CubeListBuilder.create(), PartPose.offsetAndRotation(-12.0f, -4.9f, -12.0f, 0.0f, 0.0f, 0.0f));
		PartDefinition positive = rotor.addOrReplaceChild("positive", CubeListBuilder.create(), PartPose.ZERO);
		positive.addOrReplaceChild("rotor_0_hub_0",
				CubeListBuilder.create().texOffs(225, 103)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, 0.0f, 0.0f));
		positive.addOrReplaceChild("rotor_0_hub_1",
				CubeListBuilder.create().texOffs(237, 103)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		positive.addOrReplaceChild("rotor_0_hub_2",
				CubeListBuilder.create().texOffs(249, 103)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		positive.addOrReplaceChild("rotor_0_hub_3",
				CubeListBuilder.create().texOffs(261, 103)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		positive.addOrReplaceChild("rotor_0_nut_0",
				CubeListBuilder.create().texOffs(273, 103)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, 0.0f, 0.0f));
		positive.addOrReplaceChild("rotor_0_nut_1",
				CubeListBuilder.create().texOffs(281, 103)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		positive.addOrReplaceChild("rotor_0_nut_2",
				CubeListBuilder.create().texOffs(289, 103)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		positive.addOrReplaceChild("rotor_0_nut_3",
				CubeListBuilder.create().texOffs(297, 103)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_0_0",
				CubeListBuilder.create().texOffs(305, 103)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -0.349065850399f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_0_1",
				CubeListBuilder.create().texOffs(317, 103)
						.addBox(-1.46f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -0.349065850399f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_0_2",
				CubeListBuilder.create().texOffs(332, 103)
						.addBox(-1.295f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -0.349065850399f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_1_0",
				CubeListBuilder.create().texOffs(342, 103)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -2.44346095279f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_1_1",
				CubeListBuilder.create().texOffs(354, 103)
						.addBox(-1.46f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -2.44346095279f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_1_2",
				CubeListBuilder.create().texOffs(369, 103)
						.addBox(-1.295f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -2.44346095279f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_2_0",
				CubeListBuilder.create().texOffs(379, 103)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -4.53785605519f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_2_1",
				CubeListBuilder.create().texOffs(391, 103)
						.addBox(-1.46f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -4.53785605519f, 0.0f));
		positive.addOrReplaceChild("rotor_0_blade_2_2",
				CubeListBuilder.create().texOffs(406, 103)
						.addBox(-1.295f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -4.53785605519f, 0.0f));
		PartDefinition negative = rotor.addOrReplaceChild("negative", CubeListBuilder.create(), PartPose.ZERO);
		negative.addOrReplaceChild("rotor_1_hub_0",
				CubeListBuilder.create().texOffs(65, 128)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, 0.0f, 0.0f));
		negative.addOrReplaceChild("rotor_1_hub_1",
				CubeListBuilder.create().texOffs(77, 128)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		negative.addOrReplaceChild("rotor_1_hub_2",
				CubeListBuilder.create().texOffs(89, 128)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		negative.addOrReplaceChild("rotor_1_hub_3",
				CubeListBuilder.create().texOffs(101, 128)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		negative.addOrReplaceChild("rotor_1_nut_0",
				CubeListBuilder.create().texOffs(113, 128)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, 0.0f, 0.0f));
		negative.addOrReplaceChild("rotor_1_nut_1",
				CubeListBuilder.create().texOffs(121, 128)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		negative.addOrReplaceChild("rotor_1_nut_2",
				CubeListBuilder.create().texOffs(129, 128)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		negative.addOrReplaceChild("rotor_1_nut_3",
				CubeListBuilder.create().texOffs(137, 128)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_0_0",
				CubeListBuilder.create().texOffs(145, 128)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 0.349065850399f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_0_1",
				CubeListBuilder.create().texOffs(157, 128)
						.addBox(-0.98f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 0.349065850399f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_0_2",
				CubeListBuilder.create().texOffs(172, 128)
						.addBox(-0.595f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 0.349065850399f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_1_0",
				CubeListBuilder.create().texOffs(182, 128)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 2.44346095279f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_1_1",
				CubeListBuilder.create().texOffs(194, 128)
						.addBox(-0.98f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 2.44346095279f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_1_2",
				CubeListBuilder.create().texOffs(209, 128)
						.addBox(-0.595f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 2.44346095279f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_2_0",
				CubeListBuilder.create().texOffs(219, 128)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 4.53785605519f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_2_1",
				CubeListBuilder.create().texOffs(231, 128)
						.addBox(-0.98f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 4.53785605519f, 0.0f));
		negative.addOrReplaceChild("rotor_1_blade_2_2",
				CubeListBuilder.create().texOffs(246, 128)
						.addBox(-0.595f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 4.53785605519f, 0.0f));
	}

	private static void addArm2(PartDefinition frame, int index) {
		PartDefinition component = frame.addOrReplaceChild("assembly_" + index, CubeListBuilder.create(), PartPose.offsetAndRotation(12.0f, -2.6f, 12.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("arm_2_carbon_beam",
				CubeListBuilder.create().texOffs(256, 128)
						.addBox(-1.355f, -0.505f, 1.42f, 2.71f, 1.01f, 15.730563f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(-12.0f, -0.05f, -12.0f, 0.0f, 0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_2_mount_0",
				CubeListBuilder.create().texOffs(295, 128)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_2_mount_1",
				CubeListBuilder.create().texOffs(311, 128)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_2_mount_2",
				CubeListBuilder.create().texOffs(327, 128)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_2_mount_3",
				CubeListBuilder.create().texOffs(343, 128)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_2_foot_0",
				CubeListBuilder.create().texOffs(16, 147)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_2_foot_1",
				CubeListBuilder.create().texOffs(25, 147)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_2_foot_2",
				CubeListBuilder.create().texOffs(34, 147)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_2_foot_3",
				CubeListBuilder.create().texOffs(43, 147)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		PartDefinition part0 = component.addOrReplaceChild("guard_2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, -2.3f, 0.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("guard_2_segment_0",
				CubeListBuilder.create().texOffs(52, 147)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(0.0f, 0.05f, 8.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("guard_2_top_trim_0",
				CubeListBuilder.create().texOffs(71, 147)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(0.0f, -0.45f, 8.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("guard_2_segment_1",
				CubeListBuilder.create().texOffs(92, 147)
						.addBox(-3.59903875051f, -0.58f, -0.505000249492f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(-5.65685424949f, 0.05f, 5.65685424949f, 0.0f, -0.785398163397f, 0.0f));
		part0.addOrReplaceChild("guard_2_top_trim_1",
				CubeListBuilder.create().texOffs(111, 147)
						.addBox(-3.91403875051f, -0.505f, -0.820000249492f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(-5.65685424949f, -0.45f, 5.65685424949f, 0.0f, -0.785398163397f, 0.0f));
		part0.addOrReplaceChild("guard_2_segment_2",
				CubeListBuilder.create().texOffs(132, 147)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(-8.0f, 0.05f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		part0.addOrReplaceChild("guard_2_top_trim_2",
				CubeListBuilder.create().texOffs(151, 147)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(-8.0f, -0.45f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		part0.addOrReplaceChild("guard_2_segment_3",
				CubeListBuilder.create().texOffs(172, 147)
						.addBox(-3.59903875051f, -0.58f, -0.504999750508f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(-5.65685424949f, 0.05f, -5.65685424949f, 0.0f, -2.35619449019f, 0.0f));
		part0.addOrReplaceChild("guard_2_top_trim_3",
				CubeListBuilder.create().texOffs(191, 147)
						.addBox(-3.91403875051f, -0.505f, -0.819999750508f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(-5.65685424949f, -0.45f, -5.65685424949f, 0.0f, -2.35619449019f, 0.0f));
		part0.addOrReplaceChild("guard_2_segment_4",
				CubeListBuilder.create().texOffs(212, 147)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(0.0f, 0.05f, -8.0f, 0.0f, -3.14159265359f, 0.0f));
		part0.addOrReplaceChild("guard_2_top_trim_4",
				CubeListBuilder.create().texOffs(231, 147)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(0.0f, -0.45f, -8.0f, 0.0f, -3.14159265359f, 0.0f));
		part0.addOrReplaceChild("guard_2_segment_5",
				CubeListBuilder.create().texOffs(252, 147)
						.addBox(-3.59903824949f, -0.58f, -0.504999750508f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(5.65685424949f, 0.05f, -5.65685424949f, 0.0f, -3.92699081699f, 0.0f));
		part0.addOrReplaceChild("guard_2_top_trim_5",
				CubeListBuilder.create().texOffs(271, 147)
						.addBox(-3.91403824949f, -0.505f, -0.819999750508f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(5.65685424949f, -0.45f, -5.65685424949f, 0.0f, -3.92699081699f, 0.0f));
		part0.addOrReplaceChild("guard_2_segment_6",
				CubeListBuilder.create().texOffs(292, 147)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(8.0f, 0.05f, 0.0f, 0.0f, -4.71238898038f, 0.0f));
		part0.addOrReplaceChild("guard_2_top_trim_6",
				CubeListBuilder.create().texOffs(311, 147)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(8.0f, -0.45f, 0.0f, 0.0f, -4.71238898038f, 0.0f));
		part0.addOrReplaceChild("guard_2_segment_7",
				CubeListBuilder.create().texOffs(332, 147)
						.addBox(-3.59903824949f, -0.58f, -0.505000249492f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(5.65685424949f, 0.05f, 5.65685424949f, 0.0f, -5.49778714378f, 0.0f));
		part0.addOrReplaceChild("guard_2_top_trim_7",
				CubeListBuilder.create().texOffs(351, 147)
						.addBox(-3.91403824949f, -0.505f, -0.820000249492f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(5.65685424949f, -0.45f, 5.65685424949f, 0.0f, -5.49778714378f, 0.0f));
		part0.addOrReplaceChild("guard_2_support_0",
				CubeListBuilder.create().texOffs(372, 147)
						.addBox(-0.530000446628f, -0.505f, -4.17999955337f, 1.06f, 1.01f, 8.36f, new CubeDeformation(-0.28f)), PartPose.offsetAndRotation(2.75771644663f, 1.68f, -2.75771644663f, 0.0f, 2.35619449019f, 0.0f));
		part0.addOrReplaceChild("guard_2_support_1",
				CubeListBuilder.create().texOffs(393, 147)
						.addBox(-0.529999553372f, -0.505f, -4.18000044663f, 1.06f, 1.01f, 8.36f, new CubeDeformation(-0.28f)), PartPose.offsetAndRotation(-2.75771644663f, 1.68f, 2.75771644663f, 0.0f, -0.785398163397f, 0.0f));
	}

	private static void addMotor2(PartDefinition design, int index) {
		PartDefinition component = design.addOrReplaceChild("motor_" + index, CubeListBuilder.create(), PartPose.offsetAndRotation(12.0f, -3.7f, 12.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_2_base_0",
				CubeListBuilder.create().texOffs(359, 128)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_2_base_1",
				CubeListBuilder.create().texOffs(372, 128)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_2_base_2",
				CubeListBuilder.create().texOffs(385, 128)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_2_base_3",
				CubeListBuilder.create().texOffs(398, 128)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_2_can_0",
				CubeListBuilder.create().texOffs(411, 128)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_2_can_1",
				CubeListBuilder.create().texOffs(423, 128)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_2_can_2",
				CubeListBuilder.create().texOffs(435, 128)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_2_can_3",
				CubeListBuilder.create().texOffs(447, 128)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_2_cap_0",
				CubeListBuilder.create().texOffs(459, 128)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_2_cap_1",
				CubeListBuilder.create().texOffs(473, 128)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_2_cap_2",
				CubeListBuilder.create().texOffs(487, 128)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_2_cap_3",
				CubeListBuilder.create().texOffs(2, 147)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
	}

	private static void addRotor2(PartDefinition design, int index) {
		PartDefinition rotor = design.addOrReplaceChild("rotor_" + index, CubeListBuilder.create(), PartPose.offsetAndRotation(12.0f, -4.9f, 12.0f, 0.0f, 0.0f, 0.0f));
		PartDefinition positive = rotor.addOrReplaceChild("positive", CubeListBuilder.create(), PartPose.ZERO);
		positive.addOrReplaceChild("rotor_3_hub_0",
				CubeListBuilder.create().texOffs(265, 178)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, 0.0f, 0.0f));
		positive.addOrReplaceChild("rotor_3_hub_1",
				CubeListBuilder.create().texOffs(277, 178)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		positive.addOrReplaceChild("rotor_3_hub_2",
				CubeListBuilder.create().texOffs(289, 178)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		positive.addOrReplaceChild("rotor_3_hub_3",
				CubeListBuilder.create().texOffs(301, 178)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		positive.addOrReplaceChild("rotor_3_nut_0",
				CubeListBuilder.create().texOffs(313, 178)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, 0.0f, 0.0f));
		positive.addOrReplaceChild("rotor_3_nut_1",
				CubeListBuilder.create().texOffs(321, 178)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		positive.addOrReplaceChild("rotor_3_nut_2",
				CubeListBuilder.create().texOffs(329, 178)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		positive.addOrReplaceChild("rotor_3_nut_3",
				CubeListBuilder.create().texOffs(337, 178)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_0_0",
				CubeListBuilder.create().texOffs(345, 178)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -0.349065850399f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_0_1",
				CubeListBuilder.create().texOffs(357, 178)
						.addBox(-1.46f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -0.349065850399f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_0_2",
				CubeListBuilder.create().texOffs(372, 178)
						.addBox(-1.295f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -0.349065850399f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_1_0",
				CubeListBuilder.create().texOffs(382, 178)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -2.44346095279f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_1_1",
				CubeListBuilder.create().texOffs(394, 178)
						.addBox(-1.46f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -2.44346095279f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_1_2",
				CubeListBuilder.create().texOffs(409, 178)
						.addBox(-1.295f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -2.44346095279f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_2_0",
				CubeListBuilder.create().texOffs(419, 178)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -4.53785605519f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_2_1",
				CubeListBuilder.create().texOffs(431, 178)
						.addBox(-1.46f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -4.53785605519f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_2_2",
				CubeListBuilder.create().texOffs(446, 178)
						.addBox(-1.295f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -4.53785605519f, 0.0f));
		PartDefinition negative = rotor.addOrReplaceChild("negative", CubeListBuilder.create(), PartPose.ZERO);
		negative.addOrReplaceChild("rotor_2_hub_0",
				CubeListBuilder.create().texOffs(414, 147)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, 0.0f, 0.0f));
		negative.addOrReplaceChild("rotor_2_hub_1",
				CubeListBuilder.create().texOffs(426, 147)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		negative.addOrReplaceChild("rotor_2_hub_2",
				CubeListBuilder.create().texOffs(438, 147)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		negative.addOrReplaceChild("rotor_2_hub_3",
				CubeListBuilder.create().texOffs(450, 147)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		negative.addOrReplaceChild("rotor_2_nut_0",
				CubeListBuilder.create().texOffs(462, 147)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, 0.0f, 0.0f));
		negative.addOrReplaceChild("rotor_2_nut_1",
				CubeListBuilder.create().texOffs(470, 147)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		negative.addOrReplaceChild("rotor_2_nut_2",
				CubeListBuilder.create().texOffs(478, 147)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		negative.addOrReplaceChild("rotor_2_nut_3",
				CubeListBuilder.create().texOffs(486, 147)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_0_0",
				CubeListBuilder.create().texOffs(494, 147)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 0.349065850399f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_0_1",
				CubeListBuilder.create().texOffs(2, 159)
						.addBox(-0.98f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 0.349065850399f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_0_2",
				CubeListBuilder.create().texOffs(17, 159)
						.addBox(-0.595f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 0.349065850399f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_1_0",
				CubeListBuilder.create().texOffs(27, 159)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 2.44346095279f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_1_1",
				CubeListBuilder.create().texOffs(39, 159)
						.addBox(-0.98f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 2.44346095279f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_1_2",
				CubeListBuilder.create().texOffs(54, 159)
						.addBox(-0.595f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 2.44346095279f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_2_0",
				CubeListBuilder.create().texOffs(64, 159)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 4.53785605519f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_2_1",
				CubeListBuilder.create().texOffs(76, 159)
						.addBox(-0.98f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 4.53785605519f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_2_2",
				CubeListBuilder.create().texOffs(91, 159)
						.addBox(-0.595f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 4.53785605519f, 0.0f));
	}

	private static void addArm3(PartDefinition frame, int index) {
		PartDefinition component = frame.addOrReplaceChild("assembly_" + index, CubeListBuilder.create(), PartPose.offsetAndRotation(-12.0f, -2.6f, 12.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("arm_3_carbon_beam",
				CubeListBuilder.create().texOffs(101, 159)
						.addBox(-1.355f, -0.505f, 1.42f, 2.71f, 1.01f, 15.730563f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(12.0f, -0.05f, -12.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_3_mount_0",
				CubeListBuilder.create().texOffs(140, 159)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_3_mount_1",
				CubeListBuilder.create().texOffs(156, 159)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_3_mount_2",
				CubeListBuilder.create().texOffs(172, 159)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_3_mount_3",
				CubeListBuilder.create().texOffs(188, 159)
						.addBox(-2.23f, -0.505f, -1.058427f, 4.46f, 1.01f, 2.116854f, new CubeDeformation(-0.23f)), PartPose.offsetAndRotation(0.0f, -0.12f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_3_foot_0",
				CubeListBuilder.create().texOffs(360, 159)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_3_foot_1",
				CubeListBuilder.create().texOffs(369, 159)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_3_foot_2",
				CubeListBuilder.create().texOffs(378, 159)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_3_foot_3",
				CubeListBuilder.create().texOffs(387, 159)
						.addBox(-1.032208f, -0.857208f, -0.505f, 2.064416f, 1.714416f, 1.01f, new CubeDeformation(-0.132208f)), PartPose.offsetAndRotation(0.0f, 1.8f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		PartDefinition part0 = component.addOrReplaceChild("guard_3", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, -2.3f, 0.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("guard_3_segment_0",
				CubeListBuilder.create().texOffs(396, 159)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(0.0f, 0.05f, 8.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("guard_3_top_trim_0",
				CubeListBuilder.create().texOffs(415, 159)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(0.0f, -0.45f, 8.0f, 0.0f, 0.0f, 0.0f));
		part0.addOrReplaceChild("guard_3_segment_1",
				CubeListBuilder.create().texOffs(436, 159)
						.addBox(-3.59903875051f, -0.58f, -0.505000249492f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(-5.65685424949f, 0.05f, 5.65685424949f, 0.0f, -0.785398163397f, 0.0f));
		part0.addOrReplaceChild("guard_3_top_trim_1",
				CubeListBuilder.create().texOffs(455, 159)
						.addBox(-3.91403875051f, -0.505f, -0.820000249492f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(-5.65685424949f, -0.45f, 5.65685424949f, 0.0f, -0.785398163397f, 0.0f));
		part0.addOrReplaceChild("guard_3_segment_2",
				CubeListBuilder.create().texOffs(476, 159)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(-8.0f, 0.05f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		part0.addOrReplaceChild("guard_3_top_trim_2",
				CubeListBuilder.create().texOffs(2, 178)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(-8.0f, -0.45f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		part0.addOrReplaceChild("guard_3_segment_3",
				CubeListBuilder.create().texOffs(23, 178)
						.addBox(-3.59903875051f, -0.58f, -0.504999750508f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(-5.65685424949f, 0.05f, -5.65685424949f, 0.0f, -2.35619449019f, 0.0f));
		part0.addOrReplaceChild("guard_3_top_trim_3",
				CubeListBuilder.create().texOffs(42, 178)
						.addBox(-3.91403875051f, -0.505f, -0.819999750508f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(-5.65685424949f, -0.45f, -5.65685424949f, 0.0f, -2.35619449019f, 0.0f));
		part0.addOrReplaceChild("guard_3_segment_4",
				CubeListBuilder.create().texOffs(63, 178)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(0.0f, 0.05f, -8.0f, 0.0f, -3.14159265359f, 0.0f));
		part0.addOrReplaceChild("guard_3_top_trim_4",
				CubeListBuilder.create().texOffs(82, 178)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(0.0f, -0.45f, -8.0f, 0.0f, -3.14159265359f, 0.0f));
		part0.addOrReplaceChild("guard_3_segment_5",
				CubeListBuilder.create().texOffs(103, 178)
						.addBox(-3.59903824949f, -0.58f, -0.504999750508f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(5.65685424949f, 0.05f, -5.65685424949f, 0.0f, -3.92699081699f, 0.0f));
		part0.addOrReplaceChild("guard_3_top_trim_5",
				CubeListBuilder.create().texOffs(122, 178)
						.addBox(-3.91403824949f, -0.505f, -0.819999750508f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(5.65685424949f, -0.45f, -5.65685424949f, 0.0f, -3.92699081699f, 0.0f));
		part0.addOrReplaceChild("guard_3_segment_6",
				CubeListBuilder.create().texOffs(143, 178)
						.addBox(-3.599039f, -0.58f, -0.505f, 7.198078f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(8.0f, 0.05f, 0.0f, 0.0f, -4.71238898038f, 0.0f));
		part0.addOrReplaceChild("guard_3_top_trim_6",
				CubeListBuilder.create().texOffs(162, 178)
						.addBox(-3.914039f, -0.505f, -0.82f, 7.828078f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(8.0f, -0.45f, 0.0f, 0.0f, -4.71238898038f, 0.0f));
		part0.addOrReplaceChild("guard_3_segment_7",
				CubeListBuilder.create().texOffs(183, 178)
						.addBox(-3.59903824949f, -0.58f, -0.505000249492f, 7.198077f, 1.16f, 1.01f, new CubeDeformation(-0.13f)), PartPose.offsetAndRotation(5.65685424949f, 0.05f, 5.65685424949f, 0.0f, -5.49778714378f, 0.0f));
		part0.addOrReplaceChild("guard_3_top_trim_7",
				CubeListBuilder.create().texOffs(202, 178)
						.addBox(-3.91403824949f, -0.505f, -0.820000249492f, 7.828077f, 1.01f, 1.64f, new CubeDeformation(-0.445f)), PartPose.offsetAndRotation(5.65685424949f, -0.45f, 5.65685424949f, 0.0f, -5.49778714378f, 0.0f));
		part0.addOrReplaceChild("guard_3_support_0",
				CubeListBuilder.create().texOffs(223, 178)
						.addBox(-0.530000446628f, -0.505f, -4.18000044663f, 1.06f, 1.01f, 8.36f, new CubeDeformation(-0.28f)), PartPose.offsetAndRotation(2.75771644663f, 1.68f, 2.75771644663f, 0.0f, 0.785398163397f, 0.0f));
		part0.addOrReplaceChild("guard_3_support_1",
				CubeListBuilder.create().texOffs(244, 178)
						.addBox(-0.529999553372f, -0.505f, -4.17999955337f, 1.06f, 1.01f, 8.36f, new CubeDeformation(-0.28f)), PartPose.offsetAndRotation(-2.75771644663f, 1.68f, -2.75771644663f, 0.0f, -2.35619449019f, 0.0f));
	}

	private static void addMotor3(PartDefinition design, int index) {
		PartDefinition component = design.addOrReplaceChild("motor_" + index, CubeListBuilder.create(), PartPose.offsetAndRotation(-12.0f, -3.7f, 12.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_3_base_0",
				CubeListBuilder.create().texOffs(204, 159)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_3_base_1",
				CubeListBuilder.create().texOffs(217, 159)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_3_base_2",
				CubeListBuilder.create().texOffs(230, 159)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_3_base_3",
				CubeListBuilder.create().texOffs(243, 159)
						.addBox(-1.83f, -0.505f, -0.863452f, 3.66f, 1.01f, 1.726904f, new CubeDeformation(-0.18f)), PartPose.offsetAndRotation(0.0f, 1.55f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_3_can_0",
				CubeListBuilder.create().texOffs(256, 159)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_3_can_1",
				CubeListBuilder.create().texOffs(268, 159)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_3_can_2",
				CubeListBuilder.create().texOffs(280, 159)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_3_can_3",
				CubeListBuilder.create().texOffs(292, 159)
						.addBox(-1.65f, -1.0f, -0.683452f, 3.3f, 2.0f, 1.366904f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.22f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		component.addOrReplaceChild("motor_3_cap_0",
				CubeListBuilder.create().texOffs(304, 159)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, 0.0f, 0.0f));
		component.addOrReplaceChild("motor_3_cap_1",
				CubeListBuilder.create().texOffs(318, 159)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		component.addOrReplaceChild("motor_3_cap_2",
				CubeListBuilder.create().texOffs(332, 159)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		component.addOrReplaceChild("motor_3_cap_3",
				CubeListBuilder.create().texOffs(346, 159)
						.addBox(-1.855f, -0.505f, -1.00561f, 3.71f, 1.01f, 2.01122f, new CubeDeformation(-0.405f)), PartPose.offsetAndRotation(0.0f, -0.81f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
	}

	private static void addRotor3(PartDefinition design, int index) {
		PartDefinition rotor = design.addOrReplaceChild("rotor_" + index, CubeListBuilder.create(), PartPose.offsetAndRotation(-12.0f, -4.9f, 12.0f, 0.0f, 0.0f, 0.0f));
		PartDefinition positive = rotor.addOrReplaceChild("positive", CubeListBuilder.create(), PartPose.ZERO);
		positive.addOrReplaceChild("rotor_3_hub_0",
				CubeListBuilder.create().texOffs(265, 178)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, 0.0f, 0.0f));
		positive.addOrReplaceChild("rotor_3_hub_1",
				CubeListBuilder.create().texOffs(277, 178)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		positive.addOrReplaceChild("rotor_3_hub_2",
				CubeListBuilder.create().texOffs(289, 178)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		positive.addOrReplaceChild("rotor_3_hub_3",
				CubeListBuilder.create().texOffs(301, 178)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		positive.addOrReplaceChild("rotor_3_nut_0",
				CubeListBuilder.create().texOffs(313, 178)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, 0.0f, 0.0f));
		positive.addOrReplaceChild("rotor_3_nut_1",
				CubeListBuilder.create().texOffs(321, 178)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		positive.addOrReplaceChild("rotor_3_nut_2",
				CubeListBuilder.create().texOffs(329, 178)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		positive.addOrReplaceChild("rotor_3_nut_3",
				CubeListBuilder.create().texOffs(337, 178)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_0_0",
				CubeListBuilder.create().texOffs(345, 178)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -0.349065850399f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_0_1",
				CubeListBuilder.create().texOffs(357, 178)
						.addBox(-1.46f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -0.349065850399f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_0_2",
				CubeListBuilder.create().texOffs(372, 178)
						.addBox(-1.295f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -0.349065850399f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_1_0",
				CubeListBuilder.create().texOffs(382, 178)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -2.44346095279f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_1_1",
				CubeListBuilder.create().texOffs(394, 178)
						.addBox(-1.46f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -2.44346095279f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_1_2",
				CubeListBuilder.create().texOffs(409, 178)
						.addBox(-1.295f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -2.44346095279f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_2_0",
				CubeListBuilder.create().texOffs(419, 178)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -4.53785605519f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_2_1",
				CubeListBuilder.create().texOffs(431, 178)
						.addBox(-1.46f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -4.53785605519f, 0.0f));
		positive.addOrReplaceChild("rotor_3_blade_2_2",
				CubeListBuilder.create().texOffs(446, 178)
						.addBox(-1.295f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, -4.53785605519f, 0.0f));
		PartDefinition negative = rotor.addOrReplaceChild("negative", CubeListBuilder.create(), PartPose.ZERO);
		negative.addOrReplaceChild("rotor_2_hub_0",
				CubeListBuilder.create().texOffs(414, 147)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, 0.0f, 0.0f));
		negative.addOrReplaceChild("rotor_2_hub_1",
				CubeListBuilder.create().texOffs(426, 147)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		negative.addOrReplaceChild("rotor_2_hub_2",
				CubeListBuilder.create().texOffs(438, 147)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		negative.addOrReplaceChild("rotor_2_hub_3",
				CubeListBuilder.create().texOffs(450, 147)
						.addBox(-1.495f, -0.505f, -0.762767f, 2.99f, 1.01f, 1.525534f, new CubeDeformation(-0.245f)), PartPose.offsetAndRotation(0.0f, -0.14f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		negative.addOrReplaceChild("rotor_2_nut_0",
				CubeListBuilder.create().texOffs(462, 147)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, 0.0f, 0.0f));
		negative.addOrReplaceChild("rotor_2_nut_1",
				CubeListBuilder.create().texOffs(470, 147)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -0.785398163397f, 0.0f));
		negative.addOrReplaceChild("rotor_2_nut_2",
				CubeListBuilder.create().texOffs(478, 147)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -1.57079632679f, 0.0f));
		negative.addOrReplaceChild("rotor_2_nut_3",
				CubeListBuilder.create().texOffs(486, 147)
						.addBox(-0.875f, -0.505f, -0.582107f, 1.75f, 1.01f, 1.164214f, new CubeDeformation(-0.375f)), PartPose.offsetAndRotation(0.0f, -0.52f, 0.0f, 0.0f, -2.35619449019f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_0_0",
				CubeListBuilder.create().texOffs(494, 147)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 0.349065850399f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_0_1",
				CubeListBuilder.create().texOffs(2, 159)
						.addBox(-0.98f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 0.349065850399f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_0_2",
				CubeListBuilder.create().texOffs(17, 159)
						.addBox(-0.595f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 0.349065850399f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_1_0",
				CubeListBuilder.create().texOffs(27, 159)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 2.44346095279f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_1_1",
				CubeListBuilder.create().texOffs(39, 159)
						.addBox(-0.98f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 2.44346095279f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_1_2",
				CubeListBuilder.create().texOffs(54, 159)
						.addBox(-0.595f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 2.44346095279f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_2_0",
				CubeListBuilder.create().texOffs(64, 159)
						.addBox(-0.92f, -0.505f, 0.355f, 1.84f, 1.01f, 2.74f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 4.53785605519f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_2_1",
				CubeListBuilder.create().texOffs(76, 159)
						.addBox(-0.98f, -0.505f, 2.305f, 2.44f, 1.01f, 4.04f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 4.53785605519f, 0.0f));
		negative.addOrReplaceChild("rotor_2_blade_2_2",
				CubeListBuilder.create().texOffs(91, 159)
						.addBox(-0.595f, -0.505f, 5.555f, 1.89f, 1.01f, 1.89f, new CubeDeformation(-0.395f)), PartPose.offsetAndRotation(0.0f, -0.08f, 0.0f, 0.0f, 4.53785605519f, 0.0f));
	}
}
