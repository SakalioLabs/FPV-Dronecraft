package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RateEnvelopeCalibrationTest {
	@Test
	void projectCurveMatchesBetaflightActualUrbanRateEnvelope() {
		double maxRate = RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_DEGREES_PER_SECOND;
		double center = RateEnvelopeCalibration.APDRONE_BETAFLIGHT_ACTUAL_CENTER_SENSITIVITY_DEGREES_PER_SECOND;
		double expo = RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_EXPO_FRACTION;
		double superRate = RateEnvelopeCalibration.projectSuperForActualProfile(maxRate, center, expo);

		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_PROJECT_EQUIVALENT_SUPER_RATE,
				superRate,
				1.0e-15
		);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_25_DEGREES_PER_SECOND,
				RateEnvelopeCalibration.projectRateDegreesPerSecond(0.25, maxRate, expo, superRate),
				1.0e-12
		);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_50_DEGREES_PER_SECOND,
				RateEnvelopeCalibration.projectRateDegreesPerSecond(0.50, maxRate, expo, superRate),
				1.0e-12
		);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_75_DEGREES_PER_SECOND,
				RateEnvelopeCalibration.projectRateDegreesPerSecond(0.75, maxRate, expo, superRate),
				1.0e-12
		);
		assertEquals(maxRate, RateEnvelopeCalibration.projectRateDegreesPerSecond(1.0, maxRate, expo, superRate), 1.0e-12);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_25_DEGREES_PER_SECOND,
				RateEnvelopeCalibration.betaflightActualRateDegreesPerSecond(0.25, maxRate, center, expo),
				1.0e-12
		);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_50_DEGREES_PER_SECOND,
				RateEnvelopeCalibration.betaflightActualRateDegreesPerSecond(0.50, maxRate, center, expo),
				1.0e-12
		);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_75_DEGREES_PER_SECOND,
				RateEnvelopeCalibration.betaflightActualRateDegreesPerSecond(0.75, maxRate, center, expo),
				1.0e-12
		);
		assertEquals(maxRate, RateEnvelopeCalibration.betaflightActualRateDegreesPerSecond(1.0, maxRate, center, expo), 1.0e-12);
	}

	@Test
	void apDronePresetRateEnvelopeAuditMatchesSelectedBlackboxActualRates() {
		RateEnvelopeCalibration.RateEnvelopeAudit audit =
				RateEnvelopeCalibration.apDroneRateEnvelopeAudit(DroneConfig.apDrone());

		assertEquals("APDrone-Mendeley-Blackbox", audit.sourceId());
		assertEquals("Betaflight-Actual-urban-670", audit.selection());
		assertEquals(3, audit.betaflightRatesType());
		assertEquals("ACTUAL", audit.betaflightRatesTypeName());
		assertEquals(7.0, audit.betaflightActualRcRate(), 1.0e-12);
		assertEquals(70.0, audit.referenceCenterSensitivityDegreesPerSecond(), 1.0e-12);
		assertEquals(670.0, audit.selectedReferenceMaxRateDegreesPerSecond(), 1.0e-12);
		assertEquals(300.0, audit.dumpOpenFieldReferenceMaxRateDegreesPerSecond(), 1.0e-12);
		assertEquals(1998.0, audit.betaflightRateLimitDegreesPerSecond(), 1.0e-12);
		assertEquals(0.5, audit.selectedReferenceExpoFraction(), 1.0e-12);
		assertEquals(
				0.791044776119403,
				audit.selectedProjectEquivalentSuperRate(),
				1.0e-15
		);
		assertAxis(audit.roll(), "roll");
		assertAxis(audit.pitch(), "pitch");
		assertAxis(audit.yaw(), "yaw");
	}

	private static void assertAxis(RateEnvelopeCalibration.AxisRateAudit axis, String name) {
		assertEquals(name, axis.axis());
		assertEquals(670.0, axis.configuredMaxRateDegreesPerSecond(), 1.0e-12);
		assertEquals(0.5, axis.configuredRateExpo(), 1.0e-12);
		assertEquals(0.791044776119403, axis.configuredRateSuper(), 1.0e-15);
		assertEquals(70.0, axis.configuredCenterSensitivityDegreesPerSecond(), 1.0e-12);
		assertEquals(1.0, axis.configuredMaxOverSelectedReferenceMax(), 1.0e-12);
		assertEquals(2.2333333333333334, axis.configuredMaxOverDumpReferenceMax(), 1.0e-15);
		assertEquals(0.3353353353353353, axis.configuredMaxOverBetaflightRateLimit(), 1.0e-15);
		assertEquals(1.0, axis.configuredCenterOverReferenceCenter(), 1.0e-12);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_25_DEGREES_PER_SECOND,
				axis.configuredRateAtStick25DegreesPerSecond(),
				1.0e-12
		);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_50_DEGREES_PER_SECOND,
				axis.configuredRateAtStick50DegreesPerSecond(),
				1.0e-12
		);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_75_DEGREES_PER_SECOND,
				axis.configuredRateAtStick75DegreesPerSecond(),
				1.0e-12
		);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_25_DEGREES_PER_SECOND,
				axis.selectedReferenceRateAtStick25DegreesPerSecond(),
				1.0e-12
		);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_50_DEGREES_PER_SECOND,
				axis.selectedReferenceRateAtStick50DegreesPerSecond(),
				1.0e-12
		);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_AT_STICK_75_DEGREES_PER_SECOND,
				axis.selectedReferenceRateAtStick75DegreesPerSecond(),
				1.0e-12
		);
	}
}
