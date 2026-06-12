package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AirframeDragCalibrationTest {
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
