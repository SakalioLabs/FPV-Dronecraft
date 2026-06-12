package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PropGeometryCalibrationTest {
	@Test
	void racingQuadGeometryAuditMatchesFiveInchOfficialAndUiucReferences() {
		PropGeometryCalibration.PropGeometryAudit audit =
				PropGeometryCalibration.audit(DroneConfig.racingQuad());

		assertEquals("Prop-Pitch-Geometry-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("geometric pitch"));
		assertEquals(294, audit.packetRowCount());
		assertEquals(8, audit.officialPropReferenceCount());
		assertEquals(6, audit.uiucGeometryReferenceCount());

		PropGeometryCalibration.CurrentPropGeometry current = audit.current();
		assertEquals(5.0, current.diameterInches(), 1.0e-12);
		assertEquals(4.25, current.pitchInches(), 1.0e-12);
		assertEquals(0.85, current.pitchToDiameterRatio(), 1.0e-12);
		assertEquals(3, current.bladeCount());
		assertEquals(13_023.090711279678, current.hoverRotorRpm(), 1.0e-9);
		assertEquals(29_137.63274949454, current.maxRotorRpm(), 1.0e-9);
		assertEquals(23.430710704710688, current.hoverPitchSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(52.42345758846559, current.maxPitchSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(21.132471556962376, current.geometricPitchAngle70rDegrees(), 1.0e-12);
		assertEquals(0.1536, current.representativeChordToRadius70r(), 1.0e-12);
		assertEquals(0.0097536, current.representativeChordMeters(), 1.0e-12);

		PropGeometryCalibration.OfficialPropReference hq43 = audit.hq5x43x3();
		assertEquals("HQProp Durable 5x4.3x3 V1S", hq43.propellerId());
		assertEquals(4.3, hq43.pitchInches(), 1.0e-12);
		assertEquals(0.86, hq43.pitchToDiameterRatio(), 1.0e-12);
		assertEquals(21.35879981637402, hq43.geometricPitchAngle70rDegrees(), 1.0e-12);
		assertEquals(3.81, hq43.massGrams(), 1.0e-12);

		PropGeometryCalibration.OfficialPropComparison hq43Comparison = audit.hq5x43Comparison();
		assertEquals(0.0, hq43Comparison.diameterDeltaPercent(), 1.0e-12);
		assertEquals(0.9883720930232559, hq43Comparison.currentPitchOverReference(), 1.0e-15);
		assertEquals(0.9883720930232558, hq43Comparison.currentPitchToDiameterOverReference(), 1.0e-15);
		assertEquals(0.9883720930232558, hq43Comparison.currentPitchSpeedOverReference(), 1.0e-15);
		assertEquals(0.9894035123060548, hq43Comparison.currentGeometricPitchAngleOverReference(), 1.0e-15);

		assertEquals(0.9444444444444444, audit.hq5x45Comparison().currentPitchOverReference(), 1.0e-15);
		assertEquals(1.1805555555555556, audit.gemfan51466Comparison().currentPitchOverReference(), 1.0e-15);
		assertEquals(1.2251749781277341, audit.gemfan51466Comparison().currentPitchToDiameterOverReference(), 1.0e-15);

		PropGeometryCalibration.UiucGeometryReference da4052 = audit.da4052Geometry();
		assertEquals("DA4052 5x3.75 tested geometry", da4052.geometryId());
		assertEquals(18, da4052.stationCount());
		assertEquals(0.2068, da4052.chordToRadius70r(), 1.0e-12);
		assertEquals(20.912, da4052.beta70rDegrees(), 1.0e-12);
		assertEquals(0.8402880906845006, da4052.localPitchToDiameter70r(), 1.0e-15);
		assertEquals(0.2162578642621722, da4052.planformSolidityProxy(), 1.0e-15);

		PropGeometryCalibration.UiucGeometryComparison daComparison = audit.da4052Comparison();
		assertEquals(0.7427466150870405, daComparison.currentChordOverReference70r(), 1.0e-15);
		assertEquals(1.0105428250268926, daComparison.currentGeometricPitchAngleOverReference70r(), 1.0e-15);
		assertEquals(1.0115578328708528, daComparison.currentPitchToDiameterOverReferenceLocal70r(), 1.0e-15);
		assertEquals(1.1437081161578555, audit.nr640Comparison().currentChordOverReference70r(), 1.0e-15);
		assertEquals(1.2219539468580072, audit.nr640Comparison().currentGeometricPitchAngleOverReference70r(), 1.0e-15);
	}

	@Test
	void apDroneGeometryAuditTracksFivePointOneByFourPointFiveProp() {
		PropGeometryCalibration.PropGeometryAudit audit =
				PropGeometryCalibration.audit(DroneConfig.apDrone());
		PropGeometryCalibration.CurrentPropGeometry current = audit.current();

		assertEquals(5.1, current.diameterInches(), 1.0e-12);
		assertEquals(4.5, current.pitchInches(), 1.0e-12);
		assertEquals(4.5 / 5.1, current.pitchToDiameterRatio(), 1.0e-12);
		assertEquals(3, current.bladeCount());
		assertEquals(10_046.531935346788, current.hoverRotorRpm(), 1.0e-9);
		assertEquals(29_739.565767989377, current.maxRotorRpm(), 1.0e-9);
		assertEquals(19.13864333683563, current.hoverPitchSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(56.65387278801976, current.maxPitchSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(21.862183822860644, current.geometricPitchAngle70rDegrees(), 1.0e-12);
		assertEquals(0.1539475061677115, current.representativeChordToRadius70r(), 1.0e-15);

		assertEquals(1.0, audit.hq5x45Comparison().currentPitchOverReference(), 1.0e-12);
		assertEquals(0.9803921568627452, audit.hq5x45Comparison().currentPitchToDiameterOverReference(), 1.0e-15);
		assertEquals(0.9822564028386329, audit.hq5x45Comparison().currentGeometricPitchAngleOverReference(), 1.0e-15);
		assertEquals(1.25, audit.gemfan51466Comparison().currentPitchOverReference(), 1.0e-12);
		assertEquals(1.2718079357727345, audit.gemfan51466Comparison().currentPitchToDiameterOverReference(), 1.0e-15);
		assertEquals(0.7444270124163999, audit.da4052Comparison().currentChordOverReference70r(), 1.0e-15);
		assertEquals(1.0454372524321272, audit.da4052Comparison().currentGeometricPitchAngleOverReference70r(), 1.0e-15);
		assertEquals(1.0500600341219926, audit.da4052Comparison().currentPitchToDiameterOverReferenceLocal70r(), 1.0e-15);
	}

	@Test
	void liftPropGeometryAuditUsesCurrentLowPitchUtilityPreset() {
		PropGeometryCalibration.PropGeometryAudit audit =
				PropGeometryCalibration.audit(DroneConfig.heavyLift());
		PropGeometryCalibration.CurrentPropGeometry current = audit.current();

		assertEquals(10.0, current.diameterInches(), 1.0e-12);
		assertEquals(5.0, current.pitchInches(), 1.0e-12);
		assertEquals(DroneConfig.LARGE_LIFT_PROP_PITCH_TO_DIAMETER_RATIO, current.pitchToDiameterRatio(), 1.0e-12);
		assertEquals(2, current.bladeCount());
		assertEquals(12.809249792046057, current.geometricPitchAngle70rDegrees(), 1.0e-12);
		assertEquals(0.16096912825837492, current.representativeChordToRadius70r(), 1.0e-15);

		assertEquals("Gemfan 1050 Cinelifter glass-fiber nylon 3-blade", audit.gemfan1050().propellerId());
		assertEquals(1.0, audit.gemfan1050Comparison().currentPitchOverReference(), 1.0e-12);
		assertEquals(1.0, audit.gemfan1050Comparison().currentPitchToDiameterOverReference(), 1.0e-12);
		assertEquals(1.0, audit.gemfan1050Comparison().currentGeometricPitchAngleOverReference(), 1.0e-12);
		assertEquals(1.1101319190232755, audit.apcThin10x5Comparison().currentChordOverReference70r(), 1.0e-15);
		assertEquals(0.9091021853829707, audit.apcThin10x5Comparison().currentGeometricPitchAngleOverReference70r(), 1.0e-15);
		assertEquals(0.9058439426973787, audit.apcThin10x5Comparison().currentPitchToDiameterOverReferenceLocal70r(), 1.0e-15);
	}

	@Test
	void propGeometryAuditRequiresRotorConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> PropGeometryCalibration.audit(null));
	}
}
