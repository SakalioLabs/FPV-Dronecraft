package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MotorBenchCurrentModelTest {
	@Test
	void mqtbHq5x4x3PowerLawMatchesFitRows() {
		assertEquals(
				2.0466825,
				MotorBenchCurrentModel.mqtbHq5x4x3CurrentAmpsForThrustNewtons(1.36312435),
				1.0e-7
		);
		assertEquals(
				24.5217693,
				MotorBenchCurrentModel.mqtbHq5x4x3CurrentAmpsForThrustNewtons(11.64049355),
				1.0e-7
		);
		assertEquals(
				115.9858820,
				MotorBenchCurrentModel.mqtbHq5x4x3ElectricalPowerWattsForThrustNewtons(4.0795664),
				1.0e-7
		);
		assertEquals(0.0, MotorBenchCurrentModel.mqtbHq5x4x3CurrentAmpsForThrustNewtons(Double.NaN), 1.0e-12);
		assertEquals(0.0, MotorBenchCurrentModel.mqtbHq5x4x3ElectricalPowerWattsForThrustNewtons(-1.0), 1.0e-12);
	}

	@Test
	void stateAuditReportsTotalCurrentRatioAndResidual() {
		DroneState state = new DroneState(4);
		for (int i = 0; i < 4; i++) {
			state.setRotorThrustNewtons(i, 2.69682875);
			state.setMotorCurrentAmps(i, 4.75);
		}

		double referenceCurrent = MotorBenchCurrentModel.mqtbHq5x4x3TotalCurrentAmps(state);
		assertEquals(18.0390770, referenceCurrent, 1.0e-6);
		assertEquals(288.1544128, MotorBenchCurrentModel.mqtbHq5x4x3TotalElectricalPowerWatts(state), 1.0e-6);
		assertEquals(19.0 / referenceCurrent, MotorBenchCurrentModel.mqtbHq5x4x3CurrentRatio(state), 1.0e-12);
		assertEquals(19.0 - referenceCurrent, MotorBenchCurrentModel.mqtbHq5x4x3CurrentResidualAmps(state), 1.0e-12);
	}

	@Test
	void tytoX3nmStaticPowertrainAuditMatchesCachedReference() {
		MotorBenchCurrentModel.StaticPowertrainAudit audit =
				MotorBenchCurrentModel.tytoX3nmStaticPowertrainAudit(DroneConfig.apDrone());

		assertEquals("x3nm", audit.referenceId());
		assertEquals(13.5, audit.configuredMaxRotorThrustNewtons(), 1.0e-12);
		assertEquals(1.45e-6, audit.configuredThrustCoefficient(), 1.0e-18);
		assertEquals(29137.63274949454, audit.configuredMaxRpm(), 1.0e-9);
		assertEquals(12.547278947987, audit.referenceMaxThrustNewtons(), 1.0e-12);
		assertEquals(22.185563411713, audit.referenceMaxCurrentAmps(), 1.0e-12);
		assertEquals(24.213822555542, audit.referenceVoltageAtMaxThrust(), 1.0e-12);
		assertEquals(1.7996539842396274e-6, audit.referenceThrustCoefficient(), 1.0e-18);
		assertEquals(0.9988870109198886, audit.referenceFitR2(), 1.0e-15);
		assertEquals(7, audit.referenceFitPointCount());
		assertEquals(25214.575008524822, audit.referenceRpmAtMaxThrust(), 1.0e-9);
		assertEquals(26154.33969893502, audit.referenceEquivalentRpmForConfiguredMaxThrust(), 1.0e-9);
		assertEquals(1.0759304910620362, audit.configuredMaxThrustOverReference(), 1.0e-15);
		assertEquals(0.8057104380610366, audit.configuredThrustCoefficientOverReference(), 1.0e-15);
	}

	@Test
	void aiioRotorSpeedTelemetryAuditMatchesResolvedMechanicalRpmScale() {
		MotorBenchCurrentModel.RotorSpeedTelemetryAudit racingAudit =
				MotorBenchCurrentModel.aiioRotorSpeedTelemetryAudit(DroneConfig.racingQuad());
		MotorBenchCurrentModel.RotorSpeedTelemetryAudit apDroneAudit =
				MotorBenchCurrentModel.aiioRotorSpeedTelemetryAudit(DroneConfig.apDrone());

		assertEquals("AI-IO", racingAudit.referenceId());
		assertEquals(22, racingAudit.referenceSampleFileCount());
		assertEquals(100.00009536752259, racingAudit.referenceSampleRateHertz(), 1.0e-12);
		assertEquals(50.000047683761295, racingAudit.referenceTelemetryNyquistHertz(), 1.0e-12);
		assertEquals(13.597415180566555, racingAudit.referenceFastestSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(24245.16376198689, racingAudit.referenceRotorRpmP95OfFilePeaks(), 1.0e-9);
		assertEquals(29146.829122720956, racingAudit.referenceMaxRotorRpm(), 1.0e-9);
		assertEquals(13023.090711279678, racingAudit.configuredHoverRotorRpm(), 1.0e-9);
		assertEquals(29137.63274949454, racingAudit.configuredMaxRotorRpm(), 1.0e-9);
		assertEquals(1.0003156184068034, racingAudit.referenceMaxRotorRpmOverConfiguredMax(), 1.0e-15);
		assertEquals(0.999684481176745, racingAudit.configuredMaxRotorRpmOverReferenceMax(), 1.0e-15);
		assertEquals(3.0, racingAudit.configuredBladeCount(), 1.0e-12);
		assertEquals(1457.3414561360478, racingAudit.referenceBladePassHertzForConfiguredBladeCount(), 1.0e-9);
		assertEquals(1456.881637474727, racingAudit.configuredMaxBladePassHertz(), 1.0e-9);
		assertEquals(1457.3414561360478, racingAudit.referenceThreeBladeBladePassHertz(), 1.0e-9);
		assertEquals(29.14680132613862, racingAudit.referenceBladePassOverTelemetryNyquist(), 1.0e-12);
		assertEquals(9843.188707660616, apDroneAudit.configuredHoverRotorRpm(), 1.0e-9);
		assertEquals(racingAudit.configuredMaxRotorRpm(), apDroneAudit.configuredMaxRotorRpm(), 1.0e-9);
	}

	@Test
	void mqtbHq5x4x3RotorSimilaritySelectsFiveInchTriBladeProps() {
		assertEquals(
				1.0,
				MotorBenchCurrentModel.mqtbHq5x4x3RotorSimilarity(DroneConfig.racingQuad().rotors().get(0)),
				1.0e-12
		);
		assertEquals(
				0.0,
				MotorBenchCurrentModel.mqtbHq5x4x3RotorSimilarity(
						DroneConfig.racingQuad().withRotorBladeCount(2).rotors().get(0)
				),
				1.0e-12
		);
		assertEquals(
				0.0,
				MotorBenchCurrentModel.mqtbHq5x4x3RotorSimilarity(DroneConfig.cinewhoop().rotors().get(0)),
				1.0e-12
		);
		assertEquals(
				0.0,
				MotorBenchCurrentModel.mqtbHq5x4x3RotorSimilarity(DroneConfig.heavyLift().rotors().get(0)),
				1.0e-12
		);
	}
}
