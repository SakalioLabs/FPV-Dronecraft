package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AirframeDragCalibrationTest {
	@Test
	void cdaGuardAuditSeparatesRuntimeLinearDampingFromQuadraticProjection() {
		AirframeDragCalibration.AirframeCdaGuardAudit audit =
				AirframeDragCalibration.cdaGuardAudit(DroneConfig.racingQuad());

		assertEquals("Airframe-CdA-Guard-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("linear damping"));
		assertEquals(0.2007, audit.imavMassFitLinearDragCoefficient(), 1.0e-12);
		assertEquals(0.020903184, audit.nasaBareAirframeMedianCdAMetersSquared(), 1.0e-12);
		assertEquals(0.06967728000048874, audit.nasaPoweredFullAirframeMedianCdAMetersSquared(), 1.0e-15);
		assertEquals(0.005, audit.rotorPyHummingbirdXQuadraticDrag(), 1.0e-12);
		assertEquals(0.005, audit.rotorPyHummingbirdYQuadraticDrag(), 1.0e-12);
		assertEquals(0.010, audit.rotorPyHummingbirdZQuadraticDrag(), 1.0e-12);
		assertEquals(6.0, audit.manchesterFlightDragVsWindTunnelAccuracyPercent(), 1.0e-12);
		assertEquals(20.0, audit.manchesterDragBuildUpModelCiPercent(), 1.0e-12);

		AirframeDragCalibration.CdaGuardSample lateral10 = audit.lateral10MetersPerSecond();
		assertEquals(AirframeDragCalibration.Axis.X, lateral10.axis());
		assertEquals(10.0, lateral10.speedMetersPerSecond(), 1.0e-12);
		assertEquals(0.18, lateral10.linearDampingCoefficient(), 1.0e-12);
		assertEquals(0.0025, lateral10.bodyQuadraticCoefficient(), 1.0e-12);
		assertEquals(1.8, lateral10.runtimeLinearDampingForceNewtons(), 1.0e-12);
		assertEquals(0.25, lateral10.runtimeBodyDragForceNewtons(), 1.0e-12);
		assertEquals(2.05, lateral10.runtimeTotalDragForceNewtons(), 1.0e-12);
		assertEquals(2.007, lateral10.imavReferenceDragForceNewtons(), 1.0e-12);
		assertEquals(1.0214250124564025, lateral10.runtimeOverImavReference(), 1.0e-15);
		assertEquals(0.02007, lateral10.imavEquivalentQuadraticCoefficient(), 1.0e-12);
		assertEquals(0.205, lateral10.runtimeEquivalentLinearCoefficient(), 1.0e-12);
		assertEquals(0.03346938775510204, lateral10.runtimeEquivalentCdAMetersSquared(), 1.0e-15);
		assertEquals(18.25, lateral10.linearAsQuadraticProjectionForceNewtons(), 1.0e-12);
		assertEquals(8.902439024390244, lateral10.linearAsQuadraticProjectionOverRuntime(), 1.0e-15);

		AirframeDragCalibration.CdaGuardSample forward10 = audit.forward10MetersPerSecond();
		assertEquals(AirframeDragCalibration.Axis.Z, forward10.axis());
		assertEquals(0.0045, forward10.bodyQuadraticCoefficient(), 1.0e-12);
		assertEquals(2.25, forward10.runtimeTotalDragForceNewtons(), 1.0e-12);
		assertEquals(1.1210762331838565, forward10.runtimeOverImavReference(), 1.0e-15);
		assertEquals(0.036734693877551024, forward10.runtimeEquivalentCdAMetersSquared(), 1.0e-15);
		assertEquals(18.45, forward10.linearAsQuadraticProjectionForceNewtons(), 1.0e-12);
		assertEquals(8.2, forward10.linearAsQuadraticProjectionOverRuntime(), 1.0e-12);

		AirframeDragCalibration.CdaGuardSample forward20 = audit.forward20MetersPerSecond();
		assertEquals(20.0, forward20.speedMetersPerSecond(), 1.0e-12);
		assertEquals(3.6, forward20.runtimeLinearDampingForceNewtons(), 1.0e-12);
		assertEquals(1.8, forward20.runtimeBodyDragForceNewtons(), 1.0e-12);
		assertEquals(5.4, forward20.runtimeTotalDragForceNewtons(), 1.0e-12);
		assertEquals(4.014, forward20.imavReferenceDragForceNewtons(), 1.0e-12);
		assertEquals(1.345291479820628, forward20.runtimeOverImavReference(), 1.0e-15);
		assertEquals(0.02204081632653061, forward20.runtimeEquivalentCdAMetersSquared(), 1.0e-15);
		assertEquals(73.8, forward20.linearAsQuadraticProjectionForceNewtons(), 1.0e-12);
		assertEquals(13.666666666666668, forward20.linearAsQuadraticProjectionOverRuntime(), 1.0e-15);
	}

	@Test
	void ratmHighSpeedEnvelopeAuditKeepsRawEnvelopeSeparateFromCurrentDragModel() {
		AirframeDragCalibration.RatmHighSpeedEnvelopeAudit audit =
				AirframeDragCalibration.ratmHighSpeedEnvelopeAudit(DroneConfig.racingQuad());

		assertEquals("RATM-500Hz-Speed-Envelope", audit.sourceId());
		assertEquals(36, audit.totalFlightCount());
		assertEquals(18, audit.autonomousFlightCount());
		assertEquals(18, audit.pilotedFlightCount());
		assertEquals(996_104, audit.totalSampleRowCount());
		assertEquals(500.0, audit.sampleRateHertz(), 1.0e-12);
		assertEquals(21.0, audit.readmeSpeedFloorMetersPerSecond(), 1.0e-12);
		assertEquals(21.8533357096, audit.fastestSpeedMetersPerSecond(), 1.0e-10);
		assertEquals(21.2591123637, audit.fastestP99SpeedMetersPerSecond(), 1.0e-10);
		assertEquals(13, audit.flightCountAtOrAboveReadmeFloor());
		assertEquals(0.361111111111, audit.flightFractionAtOrAboveReadmeFloor(), 1.0e-12);
		assertEquals(
				"autonomous/flight-04a-ellipse/flight-04a-ellipse_500hz_freq_sync.csv",
				audit.fastestMemberPath()
		);
		assertEquals(21.8533357096, audit.autonomousFastestSpeedMetersPerSecond(), 1.0e-10);
		assertEquals(21.2937177831, audit.pilotedFastestSpeedMetersPerSecond(), 1.0e-10);
		assertEquals(20.5, audit.minimumBatteryVoltageAcrossGroup(), 1.0e-12);
		assertEquals(2020.0, audit.motorKvRpmPerVolt(), 1.0e-12);
		assertEquals(55.0, audit.escCurrentAmps(), 1.0e-12);
		assertEquals(22.2, audit.batteryNominalVoltage(), 1.0e-12);
		assertEquals(1.4, audit.batteryCapacityAmpHours(), 1.0e-12);
		assertEquals(210.0, audit.batteryListedPackCurrentAmps(), 1.0e-12);
		assertEquals(0.0648, audit.propRadiusMeters(), 1.0e-12);

		assertEquals(AirframeDragCalibration.Axis.Z, audit.fastestRequirement().axis());
		assertTrue(audit.readmeFloorRequirement().reachable());
		assertTrue(audit.fastestRequirement().reachable());
		assertTrue(audit.p99Requirement().reachable());
		assertTrue(audit.fastestRequirement().baseDragForceNewtons() > 6.0);
		assertTrue(audit.fastestRequirement().baseDragForceNewtons() < 8.5);
		assertTrue(audit.fastestRequirement().requiredMaxThrustFraction() < 0.25);
		assertTrue(audit.fastestRequirement().dragToHorizontalMarginRatio() < 0.16);
		assertTrue(audit.slowestHorizontalDragLimitedLevelSpeedMetersPerSecond() > 85.0);
		assertTrue(audit.slowestHorizontalDragLimitedLevelSpeedMetersPerSecond() < 95.0);
		assertTrue(audit.fastestSpeedOverDragLimitedLevelSpeed() > 0.22);
		assertTrue(audit.fastestSpeedOverDragLimitedLevelSpeed() < 0.26);
		assertTrue(audit.p99SpeedOverDragLimitedLevelSpeed() > 0.22);
		assertTrue(audit.p99SpeedOverDragLimitedLevelSpeed() < 0.25);

		assertTrue(audit.configuredAverageMaxRotorRpm() > 28_000.0);
		assertTrue(audit.configuredAverageMaxRotorRpm() < 30_000.0);
		assertEquals(22.5, audit.configuredPerMotorPackCurrentAmps(), 1.0e-12);
		assertEquals(0.0635, audit.configuredAverageRotorRadiusMeters(), 1.0e-12);
		assertTrue(audit.configuredMaxRpmOverRatmKvAtNominalVoltage() > 0.62);
		assertTrue(audit.configuredMaxRpmOverRatmKvAtNominalVoltage() < 0.68);
		assertEquals(22.5 / 55.0, audit.configuredPerMotorCurrentOverRatmEscCurrent(), 1.0e-12);
		assertEquals(0.0635 / 0.0648, audit.configuredRotorRadiusOverRatmPropRadius(), 1.0e-12);
	}
}
