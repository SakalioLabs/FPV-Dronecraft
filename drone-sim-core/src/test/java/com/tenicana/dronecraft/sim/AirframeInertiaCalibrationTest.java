package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AirframeInertiaCalibrationTest {
	@Test
	void apDroneInertiaAuditMatchesMendeleyPdfAxisMapping() {
		AirframeInertiaCalibration.ApDroneInertiaAudit audit =
				AirframeInertiaCalibration.apDroneInertiaAudit(DroneConfig.apDrone());

		assertEquals("APDrone-Mendeley-Inertia-PDF", audit.referenceId());
		assertEquals(0.6284, audit.referenceMassKg(), 1.0e-12);
		assertEquals(0.6284, audit.currentMassKg(), 1.0e-12);
		assertEquals(1.0, audit.currentMassOverReference(), 1.0e-12);
		assertEquals(0.001346, audit.referenceSourceInertiaXKgMetersSquared(), 1.0e-15);
		assertEquals(0.001410, audit.referenceSourceInertiaYKgMetersSquared(), 1.0e-15);
		assertEquals(0.002480, audit.referenceSourceYawInertiaZKgMetersSquared(), 1.0e-15);
		assertEquals(0.001346, audit.currentProjectInertiaXKgMetersSquared(), 1.0e-15);
		assertEquals(0.002480, audit.currentProjectYawInertiaYKgMetersSquared(), 1.0e-15);
		assertEquals(0.001410, audit.currentProjectInertiaZKgMetersSquared(), 1.0e-15);
		assertEquals(1.0, audit.currentProjectXOverReferenceSourceX(), 1.0e-12);
		assertEquals(1.0, audit.currentProjectZOverReferenceSourceY(), 1.0e-12);
		assertEquals(1.0, audit.currentProjectYawYOverReferenceSourceYawZ(), 1.0e-12);
		assertEquals(0.095, audit.referenceMotorCenterRadiusMeters(), 1.0e-12);
		assertEquals(0.095, audit.currentMotorCenterRadiusMeters(), 1.0e-12);
		assertEquals(1.0, audit.currentMotorCenterRadiusOverReference(), 1.0e-12);
		assertEquals(0.04628118196358571, audit.referenceRadiusOfGyrationSourceXMeters(), 1.0e-15);
		assertEquals(0.04736870023480772, audit.referenceRadiusOfGyrationSourceYMeters(), 1.0e-15);
		assertEquals(0.06282142048741031, audit.referenceRadiusOfGyrationYawZMeters(), 1.0e-15);
		assertEquals(0.04628118196358571, audit.currentRadiusOfGyrationXMeters(), 1.0e-15);
		assertEquals(0.06282142048741031, audit.currentRadiusOfGyrationYawYMeters(), 1.0e-15);
		assertEquals(0.04736870023480772, audit.currentRadiusOfGyrationZMeters(), 1.0e-15);
		assertEquals(1.0, audit.currentRadiusOfGyrationXOverReferenceSourceX(), 1.0e-12);
		assertEquals(1.0, audit.currentRadiusOfGyrationZOverReferenceSourceY(), 1.0e-12);
		assertEquals(1.0, audit.currentRadiusOfGyrationYawYOverReferenceYawZ(), 1.0e-12);
		assertEquals(1.7997097242380262, audit.referenceYawToRollPitchMeanInertiaRatio(), 1.0e-15);
		assertEquals(1.7997097242380262, audit.currentYawToRollPitchMeanInertiaRatio(), 1.0e-15);
		assertEquals(1.0, audit.currentYawRatioOverReference(), 1.0e-12);
	}

	@Test
	void inertiaAuditShowsRacingQuadIsLargerThanApDroneReference() {
		AirframeInertiaCalibration.ApDroneInertiaAudit audit =
				AirframeInertiaCalibration.apDroneInertiaAudit(DroneConfig.racingQuad());

		assertTrue(audit.currentMassOverReference() > 1.7);
		assertTrue(audit.currentMotorCenterRadiusOverReference() > 1.8);
		assertTrue(audit.currentProjectXOverReferenceSourceX() > 8.0);
		assertTrue(audit.currentProjectYawYOverReferenceSourceYawZ() > 8.0);
		assertTrue(audit.currentYawRatioOverReference() < 1.0);
	}
}
