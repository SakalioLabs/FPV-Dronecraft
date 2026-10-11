package com.tenicana.dronecraft.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.entity.RotorLayoutCodec;
import com.tenicana.dronecraft.sim.DroneConfig;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

class DroneAssemblyModelTest {
	@Test
	void bareFrameKeepsItsArmsAndMountsWithoutInstalledComponents() {
		Fixture fixture = new Fixture();
		fixture.state.assemblyMask = 0;
		fixture.model.setupAnim(fixture.state);

		ModelPart frame = fixture.design.getChild("frame");
		assertTrue(frame.visible);
		assertTrue(frame.getChild("camera_mounts").visible);
		for (int i = 0; i < 4; i++) {
			assertTrue(frame.getChild("assembly_" + i).visible);
			assertVisibility(fixture.design.getChild("motor_" + i), false);
			assertVisibility(fixture.design.getChild("rotor_" + i), false);
		}
		for (String name : new String[]{"esc", "flight_controller", "receiver", "battery", "camera"}) {
			assertVisibility(fixture.design.getChild(name), false);
		}
		assertVisibility(frame.getChild("battery_straps_loaded"), false);
		assertVisibility(frame.getChild("battery_straps_empty"), true);
	}

	@Test
	void reusedModelRestoresDescendantsAndBatteryStrapsAfterBareFrame() {
		Fixture fixture = new Fixture();
		fixture.state.assemblyMask = 0;
		fixture.model.setupAnim(fixture.state);
		fixture.state.assemblyMask = 0x1FFF;
		fixture.model.setupAnim(fixture.state);

		for (int i = 0; i < 4; i++) {
			assertVisibility(fixture.design.getChild("motor_" + i), true);
			assertTrue(fixture.design.getChild("rotor_" + i).visible);
		}
		for (String name : new String[]{"esc", "flight_controller", "receiver", "battery", "camera"}) {
			assertVisibility(fixture.design.getChild(name), true);
		}
		ModelPart frame = fixture.design.getChild("frame");
		assertVisibility(frame.getChild("battery_straps_loaded"), true);
		assertVisibility(frame.getChild("battery_straps_empty"), false);
		assertTrue(fixture.design.getChild("camera").getChild("video_tx").visible);
		assertTrue(fixture.design.getChild("camera").getChild("rear_antenna").visible);
		assertFalse(frame.getChild("assembly_4").visible);
		assertFalse(fixture.design.getChild("motor_4").visible);
	}

	@Test
	void removingOneMotorAndOnePropellerLeavesOtherInstancesAndFramePresent() {
		Fixture fixture = new Fixture();
		fixture.state.assemblyMask &= ~(1 << 1);
		fixture.state.assemblyMask &= ~(1 << 6);
		fixture.model.setupAnim(fixture.state);

		assertVisibility(fixture.design.getChild("motor_1"), false);
		assertVisibility(fixture.design.getChild("rotor_2"), false);
		for (int i = 0; i < 4; i++) {
			assertTrue(fixture.design.getChild("frame").getChild("assembly_" + i).visible);
			assertEquals(i != 1, fixture.design.getChild("motor_" + i).visible);
			assertEquals(i != 2, fixture.design.getChild("rotor_" + i).visible);
		}
		// The rear physical indices retain the supplied rear-right/rear-left geometry.
		assertTrue(fixture.design.getChild("motor_2").hasChild("motor_3_base_0"));
		assertTrue(fixture.design.getChild("motor_3").hasChild("motor_2_base_0"));
	}

	@Test
	void coaxialRotorsKeepTheirNativePivotsAnimationAndOppositeBladeHandedness() {
		Fixture fixture = new Fixture();
		fixture.layout(DroneConfig.coaxialX8());
		fixture.state.ageInTicks = 12.0f;
		for (int i = 0; i < fixture.state.rotorCount; i++) {
			fixture.state.rotorRpm[i] = 600.0f;
		}
		fixture.model.setupAnim(fixture.state);

		for (int i = 0; i < fixture.state.rotorCount; i++) {
			ModelPart rotor = fixture.design.getChild("rotor_" + i);
			int spin = fixture.state.rotorSpinDirection[i];
			assertTrue(rotor.visible);
			assertVisibility(rotor.getChild("positive"), spin >= 0);
			assertVisibility(rotor.getChild("negative"), spin < 0);
			assertEquals(fixture.state.rotorXModelUnits[i], rotor.x, 1.0e-6f);
			assertEquals(-4.9f - fixture.state.rotorYModelUnits[i], rotor.y, 1.0e-6f);
			assertEquals(-fixture.state.rotorZModelUnits[i], rotor.z, 1.0e-6f);
			assertEquals(spin * 12.0f * (float) Math.PI, rotor.yRot, 1.0e-4f);
		}
	}

	private static void assertVisibility(ModelPart root, boolean expected) {
		assertTrue(root.getAllParts().allMatch(part -> part.visible == expected));
	}

	private static final class Fixture {
		final ModelPart root = DroneEntityModel.createBodyLayer().bakeRoot();
		final ModelPart design = root.getChild("body").getChild("pitch").getChild("roll").getChild("design");
		final DroneEntityModel model = new DroneEntityModel(root);
		final DroneEntityRenderState state = new DroneEntityRenderState();

		Fixture() {
			layout(DroneConfig.racingQuad());
		}

		void layout(DroneConfig config) {
			RotorLayoutCodec.Layout layout = RotorLayoutCodec.decode(RotorLayoutCodec.encode(config));
			state.rotorCount = layout.rotorCount();
			for (int i = 0; i < state.rotorCount; i++) {
				state.rotorXModelUnits[i] = layout.xModelUnits(i);
				state.rotorYModelUnits[i] = layout.yModelUnits(i);
				state.rotorZModelUnits[i] = layout.zModelUnits(i);
				state.rotorSpinDirection[i] = layout.spinDirection(i);
			}
		}
	}
}
