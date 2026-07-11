package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.client.config.DroneClientConfig;

class GamepadControlPreviewTest {
	@Test
	void previewMatchesRuntimeGamepadShaping() {
		DroneClientConfig config = DroneClientConfig.defaults();
		float[] axes = {
				0.40f,
				0.00f,
				0.70f,
				-0.70f
		};

		GamepadControlPreview.Preview preview = GamepadControlPreview.fromAxes(config, axes, 0.20f);

		assertTrue(preview.allAxesPresent());
		assertEquals(0.20f, preview.throttleCommand(), 1.0e-6f);
		assertEquals(
				GamepadStickShaper.commandFromConditionedAxis(
						preview.pitchStick(),
						config.gamepadDeadband(),
						config.gamepadExpo(),
						config.gamepadRollPitchRateScale()
				),
				preview.pitchCommand(),
				1.0e-6f
		);
		assertEquals(
				GamepadStickShaper.commandFromConditionedAxis(
						preview.rollStick(),
						config.gamepadDeadband(),
						config.gamepadExpo(),
						config.gamepadRollPitchRateScale()
				),
				preview.rollCommand(),
				1.0e-6f
		);
		assertEquals(
				GamepadStickShaper.commandFromConditionedAxis(
						preview.yawStick(),
						config.gamepadDeadband(),
						config.gamepadExpo(),
						config.gamepadYawRateScale()
				),
				preview.yawCommand(),
				1.0e-6f
		);
	}

	@Test
	void missingBoundAxesAreVisibleAndCommandNeutral() {
		DroneClientConfig config = DroneClientConfig.defaults();
		float[] axes = {
				0.30f,
				0.00f
		};

		GamepadControlPreview.Preview preview = GamepadControlPreview.fromAxes(config, axes, 0.20f);

		assertFalse(preview.allAxesPresent());
		assertTrue(preview.throttleAxisPresent());
		assertTrue(preview.yawAxisPresent());
		assertFalse(preview.rollAxisPresent());
		assertFalse(preview.pitchAxisPresent());
		assertEquals(0.0f, preview.rollCommand(), 1.0e-6f);
		assertEquals(0.0f, preview.pitchCommand(), 1.0e-6f);
	}

	@Test
	void previewReflectsStickCenterCalibration() {
		DroneClientConfig config = DroneClientConfig.defaults();
		config.setStickCenters(0.18f, -0.12f, 0.09f);
		float[] axes = {
				0.09f,
				0.00f,
				0.18f,
				0.12f
		};

		GamepadControlPreview.Preview preview = GamepadControlPreview.fromAxes(config, axes, 0.20f);

		assertEquals(0.0f, preview.pitchStick(), 1.0e-6f);
		assertEquals(0.0f, preview.rollStick(), 1.0e-6f);
		assertEquals(0.0f, preview.yawStick(), 1.0e-6f);
		assertEquals(0.0f, preview.pitchCommand(), 1.0e-6f);
		assertEquals(0.0f, preview.rollCommand(), 1.0e-6f);
		assertEquals(0.0f, preview.yawCommand(), 1.0e-6f);
	}
}
