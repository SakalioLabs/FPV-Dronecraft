package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UiucDa4002FiveInchRuntimeLookupTest {
	private static final float TOLERANCE = 1.0e-6f;

	@Test
	void preservesFrozenSourceIdentityAndExactPositiveRows() {
		assertEquals("uiuc-da4002-5x3.75-positive-axial-runtime-v1",
				UiucDa4002FiveInchRuntimeLookup.VERSION_ID);
		assertEquals("abf67ed5ba706cba92f97fc18834e846ee7241a03a1179d09c381969421951ad",
				UiucDa4002FiveInchRuntimeLookup.SOURCE_DATA_SHA256);
		assertEquals("49f20e2f7ea42771ce07bc2b4b1f371b54e6966616921da09c8bbf82612043cf",
				UiucDa4002FiveInchRuntimeLookup.CURVE_BUNDLE_SHA256);

		long staticRow = UiucDa4002FiveInchRuntimeLookup.lookupPositiveThrust(0.0f, 3_986.667f);
		assertTrue(UiucDa4002FiveInchRuntimeLookup.available(staticRow));
		assertEquals(0.125492f,
				UiucDa4002FiveInchRuntimeLookup.thrustCoefficient(staticRow), TOLERANCE);
		assertEquals(0.091880f,
				UiucDa4002FiveInchRuntimeLookup.powerCoefficient(staticRow), TOLERANCE);

		long forwardRow = UiucDa4002FiveInchRuntimeLookup.lookupPositiveThrust(0.782846f, 4_000.0f);
		assertTrue(UiucDa4002FiveInchRuntimeLookup.available(forwardRow));
		assertEquals(0.013266f,
				UiucDa4002FiveInchRuntimeLookup.thrustCoefficient(forwardRow), TOLERANCE);
		assertEquals(0.030942f,
				UiucDa4002FiveInchRuntimeLookup.powerCoefficient(forwardRow), TOLERANCE);
	}

	@Test
	void performsStaticBridgeJAndRpmInterpolationWithoutObjects() {
		long bridge = UiucDa4002FiveInchRuntimeLookup.lookupPositiveThrust(
				0.227509f * 0.5f,
				5_000.0f
		);
		assertEquals(0.117890824f,
				UiucDa4002FiveInchRuntimeLookup.thrustCoefficient(bridge), TOLERANCE);
		assertEquals(0.088147295f,
				UiucDa4002FiveInchRuntimeLookup.powerCoefficient(bridge), TOLERANCE);
		long zero = UiucDa4002FiveInchRuntimeLookup.lookupPositiveThrust(0.0f, 5_000.0f);
		assertEquals(
				(UiucDa4002FiveInchRuntimeLookup.thrustCoefficient(zero) + 0.111128f) * 0.5f,
				UiucDa4002FiveInchRuntimeLookup.thrustCoefficient(bridge),
				TOLERANCE
		);

		long lower = UiucDa4002FiveInchRuntimeLookup.lookupPositiveThrust(0.4f, 4_000.0f);
		long upper = UiucDa4002FiveInchRuntimeLookup.lookupPositiveThrust(0.4f, 5_000.0f);
		long midpoint = UiucDa4002FiveInchRuntimeLookup.lookupPositiveThrust(0.4f, 4_500.0f);
		assertTrue(UiucDa4002FiveInchRuntimeLookup.available(lower));
		assertTrue(UiucDa4002FiveInchRuntimeLookup.available(upper));
		assertTrue(UiucDa4002FiveInchRuntimeLookup.available(midpoint));
		assertEquals(0.088994373f,
				UiucDa4002FiveInchRuntimeLookup.thrustCoefficient(midpoint), TOLERANCE);
		assertEquals(0.074666318f,
				UiucDa4002FiveInchRuntimeLookup.powerCoefficient(midpoint), TOLERANCE);
		assertEquals(
				(UiucDa4002FiveInchRuntimeLookup.thrustCoefficient(lower)
						+ UiucDa4002FiveInchRuntimeLookup.thrustCoefficient(upper)) * 0.5f,
				UiucDa4002FiveInchRuntimeLookup.thrustCoefficient(midpoint),
				TOLERANCE
		);
		assertEquals(
				(UiucDa4002FiveInchRuntimeLookup.powerCoefficient(lower)
						+ UiucDa4002FiveInchRuntimeLookup.powerCoefficient(upper)) * 0.5f,
				UiucDa4002FiveInchRuntimeLookup.powerCoefficient(midpoint),
				TOLERANCE
		);
	}

	@Test
	void blocksOutOfEnvelopeAndMeasuredNonPositiveThrust() {
		assertUnavailable(UiucDa4002FiveInchRuntimeLookup.lookupPositiveThrust(0.2f, 3_999.0f));
		assertUnavailable(UiucDa4002FiveInchRuntimeLookup.lookupPositiveThrust(0.2f, 6_001.0f));
		assertUnavailable(UiucDa4002FiveInchRuntimeLookup.lookupPositiveThrust(0.86f, 4_500.0f));
		assertUnavailable(UiucDa4002FiveInchRuntimeLookup.lookupPositiveThrust(0.857870f, 4_000.0f));
		assertUnavailable(UiucDa4002FiveInchRuntimeLookup.lookupPositiveThrust(-0.1f, 5_000.0f));
		assertUnavailable(UiucDa4002FiveInchRuntimeLookup.lookupPositiveThrust(Float.NaN, 5_000.0f));
	}

	@Test
	void exposesBoundedNormalizedCtAndCpTrends() {
		float lowJThrust = UiucDa4002FiveInchRuntimeLookup.normalizedThrustScale(0.2f, 5_000.0f);
		float midJThrust = UiucDa4002FiveInchRuntimeLookup.normalizedThrustScale(0.5f, 5_000.0f);
		float highJThrust = UiucDa4002FiveInchRuntimeLookup.normalizedThrustScale(0.8f, 5_000.0f);
		float midJPower = UiucDa4002FiveInchRuntimeLookup.normalizedPowerScale(0.5f, 5_000.0f);

		assertEquals(1.0f,
				UiucDa4002FiveInchRuntimeLookup.normalizedThrustScale(0.0f, 5_000.0f),
				TOLERANCE);
		assertTrue(lowJThrust > midJThrust);
		assertTrue(midJThrust > highJThrust);
		assertTrue(highJThrust > 0.0f);
		assertTrue(midJPower > 0.0f && midJPower < 1.0f);
		assertTrue(Float.isNaN(
				UiucDa4002FiveInchRuntimeLookup.normalizedThrustScale(0.9f, 5_000.0f)));
	}

	private static void assertUnavailable(long packedCoefficients) {
		assertFalse(UiucDa4002FiveInchRuntimeLookup.available(packedCoefficients));
		assertTrue(Float.isNaN(
				UiucDa4002FiveInchRuntimeLookup.thrustCoefficient(packedCoefficients)));
		assertTrue(Float.isNaN(
				UiucDa4002FiveInchRuntimeLookup.powerCoefficient(packedCoefficients)));
	}
}
