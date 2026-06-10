package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneEnvironmentOverrideTest {
	@Test
	void naturalOverrideLeavesEnvironmentInputsAlone() {
		DroneEnvironmentOverride override = DroneEnvironmentOverride.natural();

		assertFalse(override.active());
		assertEquals(new Vec3(2.0, 0.5, -1.0), override.windOr(new Vec3(2.0, 0.5, -1.0)));
		assertEquals(0.42, override.turbulenceOr(0.42), 1.0e-9);
		assertEquals(0.94, override.airDensityOr(0.94), 1.0e-9);
	}

	@Test
	void enabledChannelsReplaceOnlyTheirEnvironmentInput() {
		DroneEnvironmentOverride override = DroneEnvironmentOverride.natural()
				.withWind(new Vec3(6.0, 0.0, -2.0))
				.withAirDensity(0.72);

		assertTrue(override.active());
		assertEquals(new Vec3(6.0, 0.0, -2.0), override.windOr(new Vec3(1.0, 0.0, 1.0)));
		assertEquals(0.31, override.turbulenceOr(0.31), 1.0e-9);
		assertEquals(0.72, override.airDensityOr(1.0), 1.0e-9);
	}

	@Test
	void valuesAreClampedAndCanBeClearedIndividually() {
		DroneEnvironmentOverride override = new DroneEnvironmentOverride(
				true,
				null,
				true,
				5.0,
				true,
				5.0
		);

		assertEquals(Vec3.ZERO, override.windVelocityWorldMetersPerSecond());
		assertEquals(1.5, override.turbulenceIntensity(), 1.0e-9);
		assertEquals(1.35, override.airDensityRatio(), 1.0e-9);

		DroneEnvironmentOverride cleared = override.withoutWind().withoutTurbulence().withoutAirDensity();
		assertFalse(cleared.active());
		assertEquals(new Vec3(-1.0, 0.0, 3.0), cleared.windOr(new Vec3(-1.0, 0.0, 3.0)));
		assertEquals(0.22, cleared.turbulenceOr(0.22), 1.0e-9);
		assertEquals(0.88, cleared.airDensityOr(0.88), 1.0e-9);
	}
}
