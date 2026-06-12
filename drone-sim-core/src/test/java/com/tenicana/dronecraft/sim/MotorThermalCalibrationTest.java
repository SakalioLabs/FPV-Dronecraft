package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MotorThermalCalibrationTest {
	@Test
	void motorThermalAuditKeepsPacketInventoryAndReferenceAnchors() {
		MotorThermalCalibration.MotorThermalAudit audit =
				MotorThermalCalibration.audit(DroneConfig.racingQuad());

		assertEquals("Motor-Thermal-Calibration-Packet", audit.sourceId());
		assertEquals(240, audit.rowTypeCounts().totalRowCount());
		assertEquals(6, audit.rowTypeCounts().sourceInventoryRowCount());
		assertEquals(32, audit.rowTypeCounts().u8DynoSummaryRowCount());
		assertEquals(10, audit.rowTypeCounts().mqtbMetadataRowCount());
		assertEquals(108, audit.rowTypeCounts().currentPresetRowCount());
		assertEquals(48, audit.rowTypeCounts().electricalStressRowCount());
		assertEquals(17, audit.rowTypeCounts().racingQuadCrosscheckRowCount());
		assertEquals(18, audit.rowTypeCounts().copperResistanceScaleRowCount());
		assertEquals(1, audit.rowTypeCounts().methodRowCount());

		MotorThermalCalibration.U8DynoSummary u8_24v = audit.u8Dyno24v();
		assertEquals("U8-Kv100-Dyno-Data", u8_24v.referenceId());
		assertEquals(24.0, u8_24v.voltageVolts(), 1.0e-12);
		assertEquals(208.0, u8_24v.temperatureSampleCount(), 1.0e-12);
		assertEquals(31.092133653846155, u8_24v.temperatureMeanCelsius(), 1.0e-15);
		assertEquals(48.623, u8_24v.temperatureMaxCelsius(), 1.0e-12);
		assertEquals(406.677, u8_24v.lossMaxWatts(), 1.0e-12);
		assertEquals(0.6587, u8_24v.motorEfficiencyMax(), 1.0e-12);
		assertEquals(0.9744, u8_24v.driverEfficiencyMax(), 1.0e-12);
		assertEquals(1504.7, u8_24v.rpmMax(), 1.0e-12);
		assertEquals(3.1727, u8_24v.torqueMaxNewtonMeters(), 1.0e-12);

		MotorThermalCalibration.U8DynoSummary u8_36v = audit.u8Dyno36v();
		assertEquals(36.0, u8_36v.voltageVolts(), 1.0e-12);
		assertEquals(232.0, u8_36v.temperatureSampleCount(), 1.0e-12);
		assertEquals(32.330050862068966, u8_36v.temperatureMeanCelsius(), 1.0e-15);
		assertEquals(57.6919, u8_36v.temperatureMaxCelsius(), 1.0e-12);
		assertEquals(484.1668, u8_36v.lossMaxWatts(), 1.0e-12);
		assertEquals(0.6824, u8_36v.motorEfficiencyMax(), 1.0e-12);
		assertEquals(0.9795, u8_36v.driverEfficiencyMax(), 1.0e-12);
		assertEquals(1703.8609, u8_36v.rpmMax(), 1.0e-12);
		assertEquals(3.3789, u8_36v.torqueMaxNewtonMeters(), 1.0e-12);

		MotorThermalCalibration.MqtbFpvMotorMetadata mqtb = audit.mqtbMetadata();
		assertEquals("Emax Eco 2306 2400kv", mqtb.referenceMotor());
		assertEquals(2300.0, mqtb.testedKvRpmPerVolt(), 1.0e-12);
		assertEquals(29.7, mqtb.motorWeightGrams(), 1.0e-12);
		assertEquals(23.0, mqtb.statorDiameterMillimeters(), 1.0e-12);
		assertEquals(6.0, mqtb.statorHeightMillimeters(), 1.0e-12);
		assertEquals(2492.8537706235006, mqtb.statorVolumeProxyCubicMillimeters(), 1.0e-12);
		assertEquals("XRotor 40A ESC", mqtb.esc());
		assertEquals(40.0, mqtb.escCurrentRatingAmps(), 1.0e-12);
		assertEquals(21.11111111111111, mqtb.ambientTempMinCelsius(), 1.0e-14);
		assertEquals(23.333333333333332, mqtb.ambientTempMaxCelsius(), 1.0e-14);
		assertEquals(4.0, mqtb.loggerRateMilliseconds(), 1.0e-12);
	}

	@Test
	void racingQuadThermalProxyMatchesMotorThermalPacket() {
		MotorThermalCalibration.MotorThermalAudit audit =
				MotorThermalCalibration.audit(DroneConfig.racingQuad());

		MotorThermalCalibration.PresetThermalAudit preset = audit.preset();
		assertEquals(12.0, preset.thermalRiseCelsiusPerSecond(), 1.0e-12);
		assertEquals(0.035, preset.coolingRatePerSecond(), 1.0e-12);
		assertEquals(95.0, preset.motorLimitCelsius(), 1.0e-12);
		assertEquals(125.0, preset.motorCutoffCelsius(), 1.0e-12);
		assertEquals(90.0, preset.escLimitCelsius(), 1.0e-12);
		assertEquals(120.0, preset.escCutoffCelsius(), 1.0e-12);
		assertEquals(0.45, preset.minThermalThrustLimit(), 1.0e-12);
		assertEquals(0.1997650925925926, preset.hoverPowerProxy(), 1.0e-13);
		assertEquals(1.1028952309959133, preset.hoverMotorCoolingFactorProxy(), 1.0e-13);
		assertEquals(1.92, preset.fullMotorCoolingFactorProxy(), 1.0e-15);
		assertEquals(2.4755555555555553, preset.fullTenMetersPerSecondMotorCoolingFactorProxy(), 1.0e-15);
		assertEquals(1.0863515310755316, preset.hoverEscCoolingFactorProxy(), 1.0e-13);
		assertEquals(1.8364, preset.fullEscCoolingFactorProxy(), 1.0e-15);
		assertEquals(28.57142857142857, preset.motorBaseTimeConstantSeconds(), 1.0e-14);
		assertEquals(14.88095238095238, preset.motorFullWashTimeConstantSeconds(), 1.0e-14);
		assertEquals(11.541420877147985, preset.motorFullTenMetersPerSecondTimeConstantSeconds(), 1.0e-14);
		assertEquals(178.57142857142856, preset.motorFullSteadyRiseCelsius(), 1.0e-12);
		assertEquals(12.40561059302343, preset.motorHoverSteadyRiseProxyCelsius(), 1.0e-14);
		assertEquals(92.60354108970968, preset.escFullCurrentSteadyRiseProxyCelsius(), 1.0e-13);
		assertEquals(0.23333333333333334, preset.inferredWindingResistance25cOhms(), 1.0e-16);
		assertEquals(1.273, preset.windingResistanceScaleAtLimit(), 1.0e-15);
		assertEquals(1.39, preset.windingResistanceScaleAtCutoff(), 1.0e-15);
		assertEquals(0.725, preset.motorLimitScaleAtMidpoint(), 1.0e-15);
		assertEquals(0.725, preset.escLimitScaleAtMidpoint(), 1.0e-15);
	}

	@Test
	void racingQuadElectricalStressMatchesThermalPacketInputs() {
		MotorThermalCalibration.MotorThermalAudit audit =
				MotorThermalCalibration.audit(DroneConfig.racingQuad());

		MotorThermalCalibration.ElectricalStressAudit electrical = audit.electricalStress();
		assertEquals(2341.416917370097, electrical.inferredKvRpmPerVolt(), 1.0e-12);
		assertEquals(0.00407842640696369, electrical.torqueConstantNewtonMetersPerAmp(), 1.0e-17);
		assertEquals(22.5, electrical.perMotorCurrentLimitAmps(), 1.0e-12);
		assertEquals(4.0865191982733275, electrical.hoverPowerCurrentAmps(), 1.0e-15);
		assertEquals(45.769286494404696, electrical.maxPowerCurrentAmps(), 1.0e-14);
		assertEquals(2.0341905108624307, electrical.maxPowerCurrentOverLimit(), 1.0e-15);
		assertEquals(9.2573945763823, electrical.hoverPhaseCurrentProxyAmps(), 1.0e-13);
		assertEquals(46.341402575584766, electrical.maxPhaseCurrentProxyAmps(), 1.0e-14);
		assertEquals(0.668925271207061, electrical.hoverVoltageHeadroom(), 1.0e-15);
		assertEquals(0.2592592592592594, electrical.maxVoltageHeadroom(), 1.0e-15);
		assertEquals(0.2951954662153137, electrical.maxDesyncHeadroomStress(), 1.0e-15);
	}

	@Test
	void racingQuadCrosscheckHighlightsFullPowerThermalLimitRisk() {
		MotorThermalCalibration.MotorThermalAudit audit =
				MotorThermalCalibration.audit(DroneConfig.racingQuad());

		MotorThermalCalibration.RacingQuadCrosscheck crosscheck = audit.racingQuadCrosscheck();
		assertEquals(25.0, crosscheck.referenceAmbientCelsius(), 1.0e-12);
		assertEquals(57.6919, crosscheck.u8OpenDynoMaxTemperatureCelsius(), 1.0e-12);
		assertEquals(484.1668, crosscheck.u8OpenDynoMaxLossWatts(), 1.0e-12);
		assertEquals(32.6919, crosscheck.u8MaxTemperatureRiseVs25c(), 1.0e-12);
		assertEquals(203.57142857142856, crosscheck.currentFullPowerMotorAbsoluteTempAt25c(), 1.0e-12);
		assertEquals(37.40561059302343, crosscheck.currentHoverMotorAbsoluteTempAt25c(), 1.0e-14);
		assertEquals(117.60354108970968, crosscheck.currentFullEscAbsoluteTempAt25c(), 1.0e-13);
		assertEquals(2.5510204081632653, crosscheck.currentFullMotorRiseOverMotorLimitMargin(), 1.0e-15);
		assertEquals(1.7857142857142856, crosscheck.currentFullMotorRiseOverMotorCutoffMargin(), 1.0e-15);
		assertEquals(1.4246698629186105, crosscheck.currentFullEscRiseOverEscLimitMargin(), 1.0e-15);
		assertEquals(0.9747741167337861, crosscheck.currentFullEscRiseOverEscCutoffMargin(), 1.0e-15);
		assertEquals(5.4622529914574705, crosscheck.currentFullMotorRiseOverU8MaxRise(), 1.0e-13);
		assertEquals(0.3794704680065529, crosscheck.currentHoverMotorRiseOverU8MaxRise(), 1.0e-15);
		assertEquals(1.018007355378303, crosscheck.currentInferredKvOverMqtbTestedKv(), 1.0e-15);
		assertEquals(0.5625, crosscheck.currentPerMotorLimitOverMqtbEscRating(), 1.0e-15);
		assertEquals(2.0341905108624307, crosscheck.currentMaxPowerCurrentOverLimit(), 1.0e-15);
		assertTrue(crosscheck.currentFullMotorRiseOverMotorLimitMargin() > 1.0);
		assertTrue(crosscheck.currentFullEscRiseOverEscLimitMargin() > 1.0);
	}

	@Test
	void copperAndLimitHelpersMirrorRuntimeShape() {
		MotorThermalCalibration.CopperResistanceReference copper =
				MotorThermalCalibration.audit(DroneConfig.racingQuad()).copperResistance();

		assertEquals(0.9025, copper.scaleAt0C(), 1.0e-15);
		assertEquals(1.0, copper.scaleAt25C(), 1.0e-15);
		assertEquals(1.1365, copper.scaleAt60C(), 1.0e-15);
		assertEquals(1.273, copper.scaleAt95C(), 1.0e-15);
		assertEquals(1.39, copper.scaleAt125C(), 1.0e-15);
		assertEquals(1.4875, copper.scaleAt150C(), 1.0e-15);
		assertEquals(1.6044999999999998, copper.scaleAt180C(), 1.0e-15);
		assertEquals(1.7605, copper.scaleAt220C(), 1.0e-15);
		assertEquals(1.9, copper.scaleAt260C(), 1.0e-15);

		assertEquals(1.0, MotorThermalCalibration.thermalLimitScale(95.0, 95.0, 125.0), 1.0e-15);
		assertEquals(0.725, MotorThermalCalibration.thermalLimitScale(110.0, 95.0, 125.0), 1.0e-15);
		assertEquals(0.45, MotorThermalCalibration.thermalLimitScale(125.0, 95.0, 125.0), 1.0e-15);
	}

	@Test
	void motorThermalAuditRequiresRotorConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> MotorThermalCalibration.audit(null));
	}
}
