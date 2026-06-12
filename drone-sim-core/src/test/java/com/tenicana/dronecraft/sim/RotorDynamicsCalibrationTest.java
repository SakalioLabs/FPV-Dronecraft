package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RotorDynamicsCalibrationTest {
	@Test
	void rotorDynamicsAuditMatchesRacingQuadInertiaAndReferenceRows() {
		RotorDynamicsCalibration.RotorDynamicsAudit audit =
				RotorDynamicsCalibration.audit(DroneConfig.racingQuad());

		assertEquals("Rotor-Dynamics-Inertia-Inflow-Coning-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("runtime values reuse DronePhysics"));
		assertEquals(132, audit.packetMetricRowCount());
		assertEquals(31, audit.rotorInertiaRowCount());
		assertEquals(7, audit.rotorInflowRowCount());
		assertEquals(94, audit.armFlexConingRowCount());
		assertEquals(8, audit.physicalPropReferenceCount());
		assertEquals(6, audit.currentPresetInertiaRowCount());
		assertEquals(12, audit.currentVsPhysicalPropRowCount());
		assertEquals(4, audit.openSourceRotorInertiaReferenceCount());
		assertEquals(1, audit.paperReportedRotorInertiaReferenceCount());
		assertEquals(6, audit.currentPresetInflowRowCount());
		assertEquals(18, audit.currentPresetArmFlexConingRowCount());
		assertEquals(60, audit.beamTheoryArmSensitivityRowCount());
		assertEquals(8, audit.multicopterConingMeasurementRowCount());

		RotorDynamicsCalibration.RotorInertiaAudit inertia = audit.inertia();
		assertEquals("HQProp Durable 5x4.3x3 V1S", inertia.nearestPhysicalReference().propellerId());
		assertEquals(4, inertia.rotorCount());
		assertEquals(5.0, inertia.configuredDiameterInches(), 1.0e-12);
		assertEquals(0.85, inertia.configuredPitchToDiameterRatio(), 1.0e-12);
		assertEquals(3, inertia.configuredBladeCount());
		assertEquals(5.376333333333333e-06, inertia.configuredRotorInertiaKgMetersSquared(), 1.0e-18);
		assertEquals(1.3998250218722659, inertia.configuredOverReferenceHubBiasedInertia(), 1.0e-15);
		assertEquals(1.0498687664041995, inertia.configuredOverReferenceUniformBladeInertia(), 1.0e-15);
		assertEquals(0.6999125109361329, inertia.configuredOverReferenceTipBiasedInertia(), 1.0e-15);
		assertEquals(3.999999999999999, inertia.configuredEquivalentUniformBladeMassGrams(), 1.0e-15);
		assertEquals(1.0498687664041992, inertia.configuredEquivalentUniformBladeMassOverReferenceMass(), 1.0e-15);
		assertEquals(0.00010556, inertia.zjuReportedRotorInertiaKgMetersSquared(), 1.0e-12);
		assertEquals(0.05093153972464317, inertia.configuredOverZjuReportedInertia(), 1.0e-15);
		assertEquals(13023.090711279678, inertia.hoverRpm(), 1.0e-9);
		assertEquals(29137.63274949454, inertia.maxRpm(), 1.0e-9);
		assertEquals(0.007332108293745071, inertia.hoverAngularMomentumNewtonMeterSeconds(), 1.0e-15);
		assertEquals(0.016404729374850074, inertia.maxAngularMomentumNewtonMeterSeconds(), 1.0e-15);
		assertEquals(720.0, inertia.bodyRateReferenceDegreesPerSecond(), 1.0e-12);
		assertEquals(0.09213799020381723, inertia.hoverGyroTorquePerRotorNewtonMeters(), 1.0e-15);
		assertEquals(0.2061479091526307, inertia.maxGyroTorquePerRotorNewtonMeters(), 1.0e-15);
		assertEquals(0.8245916366105228, inertia.maxGyroTorqueAllRotorsAbsoluteNewtonMeters(), 1.0e-15);
		assertEquals(0.364549541663335, inertia.motorTauSpinupReactionTorqueNewtonMeters(), 1.0e-15);
		assertEquals(0.3280945874970015, inertia.fiftyMillisecondSpinupReactionTorqueNewtonMeters(), 1.0e-15);
	}

	@Test
	void rotorDynamicsAuditTracksDynamicInflowAgainstWakeTransitScale() {
		RotorDynamicsCalibration.DynamicInflowAudit inflow =
				RotorDynamicsCalibration.audit(DroneConfig.racingQuad()).dynamicInflow();

		assertEquals(0.035, inflow.configuredInflowTimeConstantSeconds(), 1.0e-12);
		assertEquals(0.16, inflow.configuredInflowLagCoefficient(), 1.0e-12);
		assertEquals(0.0125, inflow.rotorSPx4ReferenceTauUpSeconds(), 1.0e-12);
		assertEquals(0.025, inflow.rotorSPx4ReferenceTauDownSeconds(), 1.0e-12);
		assertEquals(2.8000000000000003, inflow.configuredTauOverReferenceUp(), 1.0e-15);
		assertEquals(1.4000000000000001, inflow.configuredTauOverReferenceDown(), 1.0e-15);
		assertEquals(0.0635, inflow.rotorRadiusMeters(), 1.0e-12);
		assertEquals(9.321696972452157, inflow.hoverInducedVelocityMetersPerSecond(), 1.0e-15);
		assertEquals(20.856199884266704, inflow.maxInducedVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(0.006812064389955786, inflow.wakeTransitOneRadiusHoverSeconds(), 1.0e-15);
		assertEquals(0.013624128779911572, inflow.wakeTransitTwoRadiusHoverSeconds(), 1.0e-15);
		assertEquals(0.003044658200073279, inflow.wakeTransitOneRadiusMaxSeconds(), 1.0e-15);
		assertEquals(5.137943213162607, inflow.configuredTauOverOneRadiusHoverTransit(), 1.0e-15);
		assertEquals(2.5689716065813033, inflow.configuredTauOverTwoRadiusHoverTransit(), 1.0e-15);
		assertEquals(11.495543243296611, inflow.configuredTauOverOneRadiusMaxTransit(), 1.0e-12);
		assertEquals(0.03277497363257264, inflow.runtimeHoverDynamicTauSeconds(), 1.0e-15);
		assertEquals(0.0133056, inflow.runtimeHighThrustDynamicTauSeconds(), 1.0e-15);
		assertEquals(0.026062691508235026, inflow.runtimeFastCrossflowDynamicTauSeconds(), 1.0e-15);
		assertEquals(0.04176081903563105, inflow.runtimeFastDescentDynamicTauSeconds(), 1.0e-15);
	}

	@Test
	void rotorDynamicsAuditTracksConingAndArmFlexRuntimeHelpers() {
		RotorDynamicsCalibration.RotorDynamicsAudit audit =
				RotorDynamicsCalibration.audit(DroneConfig.racingQuad());

		RotorDynamicsCalibration.ConingAudit coning = audit.coning();
		assertEquals(0.0, coning.hoverTargetIntensity(), 1.0e-12);
		assertEquals(0.6389577297149291, coning.maxTargetIntensity(), 1.0e-15);
		assertEquals(1.3098633459156046, coning.maxConingAngleDegrees(), 1.0e-15);
		assertEquals(0.9757196062708327, coning.maxConingThrustScale(), 1.0e-15);
		assertEquals(0.0351426751343211, coning.maxConingLoadFactor(), 1.0e-15);
		assertEquals(0.012779154594298583, coning.maxConingVibration(), 1.0e-15);
		assertEquals(42.599999999999994, coning.maxConingNaturalFrequencyHertz(), 1.0e-12);
		assertEquals(0.64, coning.maxConingDampingRatio(), 1.0e-12);
		assertEquals(1.95, coning.djiPhantom8500RpmConingDegrees(), 1.0e-12);
		assertEquals(4.08, coning.djiPhantom8500RpmDeflectionMillimeters(), 1.0e-12);
		assertEquals(0.89, coning.tmotor15x5_5000RpmConingDegrees(), 1.0e-12);
		assertEquals(1.87, coning.tmotor15x5_5000RpmDeflectionMillimeters(), 1.0e-12);
		assertEquals(0.6717247927772332, coning.maxConingAngleOverDjiPhantomReference(), 1.0e-15);
		assertEquals(1.4717565684444995, coning.maxConingAngleOverTmotorReference(), 1.0e-15);

		RotorDynamicsCalibration.ArmFlexAudit flex = audit.armFlex();
		assertEquals(0.0, flex.hoverTargetIntensity(), 1.0e-12);
		assertEquals(0.16, flex.maxSteadyTargetIntensity(), 1.0e-12);
		assertEquals(0.6000000000000001, flex.maxSnapTargetIntensity(), 1.0e-15);
		assertEquals(9.899999999999999, flex.fullFlexVerticalDeflectionMillimeters(), 1.0e-15);
		assertEquals(1.5839999999999999, flex.maxSteadyVerticalDeflectionMillimeters(), 1.0e-15);
		assertEquals(5.94, flex.maxSnapVerticalDeflectionMillimeters(), 1.0e-12);
		assertEquals(3.4641016151377544, flex.fullFlexTiltDegrees(), 1.0e-15);
		assertEquals(0.5542562584220407, flex.maxSteadyTiltDegrees(), 1.0e-15);
		assertEquals(2.078460969082653, flex.maxSnapTiltDegrees(), 1.0e-15);
		assertEquals(35.47240053901061, flex.maxSpinNaturalFrequencyHertz(), 1.0e-12);
		assertEquals(0.52, flex.maxSpinDampingRatio(), 1.0e-12);
		assertEquals(0.14400000000000002, flex.maxSnapVibration(), 1.0e-15);

		RotorDynamicsCalibration.BeamSensitivityAudit beam = flex.representativeBeamSensitivity();
		assertEquals("5in_solid_10x5mm_E70GPa", beam.geometryId());
		assertEquals(70.0, beam.youngsModulusGpa(), 1.0e-12);
		assertEquals(0.18, beam.armLengthMeters(), 1.0e-12);
		assertEquals(13.5, beam.loadForceNewtons(), 1.0e-12);
		assertEquals(1.0416666666666668e-10, beam.secondMomentAreaMeters4(), 1.0e-24);
		assertEquals(3.599177142857142, beam.cantileverTipDeflectionMillimeters(), 1.0e-15);
		assertEquals(3750.8573388203026, beam.cantileverTipStiffnessNewtonsPerMeter(), 1.0e-9);
		assertEquals(49.74264111174961, beam.cantileverFirstBendingFrequencyHertz(), 1.0e-12);
		assertEquals(0.6059220779220778, beam.beamDeflectionOverRuntimeMaxSnap(), 1.0e-15);
		assertEquals(1.402291368948808, beam.beamFrequencyOverRuntimeMaxSpin(), 1.0e-15);
	}

	@Test
	void rotorDynamicsAuditSelectsPresetSpecificReferenceScales() {
		RotorDynamicsCalibration.RotorDynamicsAudit cinewhoop =
				RotorDynamicsCalibration.audit(DroneConfig.cinewhoop());
		RotorDynamicsCalibration.RotorDynamicsAudit heavy =
				RotorDynamicsCalibration.audit(DroneConfig.heavyLift());

		assertEquals("HQProp Durable T3x3x3", cinewhoop.inertia().nearestPhysicalReference().propellerId());
		assertEquals("3in_solid_8x3mm_E135GPa", cinewhoop.armFlex().representativeBeamSensitivity().geometryId());
		assertEquals("Gemfan 1050 Cinelifter glass-fiber nylon 3-blade", heavy.inertia().nearestPhysicalReference().propellerId());
		assertEquals("10in_solid_16x6mm_E70GPa", heavy.armFlex().representativeBeamSensitivity().geometryId());
		assertTrue(heavy.dynamicInflow().configuredTauOverOneRadiusHoverTransit()
				< heavy.dynamicInflow().configuredTauOverOneRadiusMaxTransit());
		assertTrue(heavy.coning().maxConingAngleDegrees() > 1.0);
	}

	@Test
	void rotorDynamicsAuditRequiresRotorConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> RotorDynamicsCalibration.audit(null));
	}
}
