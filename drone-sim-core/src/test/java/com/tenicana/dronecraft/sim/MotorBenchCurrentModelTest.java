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
	void apDroneMotorSpecAuditMatchesCachedMotorPdfReference() {
		MotorBenchCurrentModel.ApDroneMotorSpecAudit audit =
				MotorBenchCurrentModel.apDroneMotorSpecAudit(DroneConfig.apDrone());

		assertEquals("YSIDO-2507-1800KV", audit.referenceId());
		assertEquals(13.5, audit.configuredMaxRotorThrustNewtons(), 1.0e-12);
		assertEquals(29137.63274949454, audit.configuredMaxRpm(), 1.0e-9);
		assertEquals(0.0586, audit.configuredMotorWindingResistanceOhms(), 1.0e-12);
		assertEquals(37.5, audit.configuredPerMotorPackCurrentAmps(), 1.0e-12);
		assertEquals(1800.0, audit.referenceKvRpmPerVolt(), 1.0e-12);
		assertEquals(1960.0, audit.betaflightKvRpmPerVolt(), 1.0e-12);
		assertEquals(0.0586, audit.referenceMotorWindingResistanceOhms(), 1.0e-12);
		assertEquals(42.0, audit.referenceContinuousCurrentAmps(), 1.0e-12);
		assertEquals(14.5922952, audit.referenceHeadlineMaxThrustNewtons(), 1.0e-12);
		assertEquals(14.1608026, audit.referenceBestVisibleMaxThrustNewtons(), 1.0e-12);
		assertEquals(32.16, audit.referenceBestVisibleMaxCurrentAmps(), 1.0e-12);
		assertEquals(24.39, audit.referenceBestVisibleMaxVoltageVolts(), 1.0e-12);
		assertEquals(0.925145757742072, audit.configuredMaxThrustOverReferenceHeadline(), 1.0e-15);
		assertEquals(0.9533357946815811, audit.configuredMaxThrustOverReferenceBestVisible(), 1.0e-15);
		assertEquals(1.0, audit.configuredMotorWindingResistanceOverReference(), 1.0e-15);
		assertEquals(0.8928571428571429, audit.configuredPerMotorPackCurrentOverReferenceContinuous(), 1.0e-15);
		assertEquals(0.9635460565309041, audit.configuredMaxRpmOverReferenceKvFullCharge(), 1.0e-15);
		assertEquals(1.0937549830891344, audit.configuredMaxRpmOverReferenceKvNominal(), 1.0e-15);
		assertEquals(0.8848892355896057, audit.configuredMaxRpmOverBetaflightKvFullCharge(), 1.0e-15);
		assertEquals(1.0044688620206337, audit.configuredMaxRpmOverBetaflightKvNominal(), 1.0e-15);
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
		assertEquals(37089, racingAudit.lowDynamicSampleCount());
		assertEquals(20, racingAudit.lowDynamicSampleFileCount());
		assertEquals(370.8896462917328, racingAudit.lowDynamicDurationSeconds(), 1.0e-9);
		assertEquals(1.0, racingAudit.lowDynamicMaxSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(1.5, racingAudit.lowDynamicMaxGroundAccelerationMetersPerSecondSquared(), 1.0e-12);
		assertEquals(0.5, racingAudit.lowDynamicMaxGyroNormRadiansPerSecond(), 1.0e-12);
		assertEquals(0.5199172711044272, racingAudit.lowDynamicSpeedMeanMetersPerSecond(), 1.0e-15);
		assertEquals(0.9754606713859196, racingAudit.lowDynamicSpeedP95MetersPerSecond(), 1.0e-15);
		assertEquals(1.2580811125853344, racingAudit.lowDynamicGroundAccelerationP95MetersPerSecondSquared(), 1.0e-15);
		assertEquals(0.4236720246096596, racingAudit.lowDynamicGyroNormP95RadiansPerSecond(), 1.0e-15);
		assertEquals(0.2870552036315011, racingAudit.lowDynamicThrottleMean(), 1.0e-15);
		assertEquals(0.30747738541591185, racingAudit.lowDynamicThrottleP95(), 1.0e-15);
		assertEquals(13642.489652165377, racingAudit.lowDynamicRotorRpmMean(), 1.0e-9);
		assertEquals(13791.514254986312, racingAudit.lowDynamicRotorRpmP50(), 1.0e-9);
		assertEquals(14119.280967371637, racingAudit.lowDynamicRotorRpmP95(), 1.0e-9);
		assertEquals(15192.087569316081, racingAudit.lowDynamicRotorRpmMax(), 1.0e-9);
		assertEquals(1.0475615930670912, racingAudit.lowDynamicRotorRpmMeanOverConfiguredHover(), 1.0e-15);
		assertEquals(1.0590046987111195, racingAudit.lowDynamicRotorRpmP50OverConfiguredHover(), 1.0e-15);
		assertEquals(0.4845719996802612, racingAudit.lowDynamicRotorRpmP95OverConfiguredMax(), 1.0e-15);
		assertEquals(682.1244826082689, racingAudit.lowDynamicMeanBladePassHertzForConfiguredBladeCount(), 1.0e-9);
		assertEquals(682.1244826082689, racingAudit.lowDynamicThreeBladeBladePassHertzAtMean(), 1.0e-9);
		assertEquals(3.0, racingAudit.configuredBladeCount(), 1.0e-12);
		assertEquals(1457.3414561360478, racingAudit.referenceBladePassHertzForConfiguredBladeCount(), 1.0e-9);
		assertEquals(1456.881637474727, racingAudit.configuredMaxBladePassHertz(), 1.0e-9);
		assertEquals(1457.3414561360478, racingAudit.referenceThreeBladeBladePassHertz(), 1.0e-9);
		assertEquals(29.14680132613862, racingAudit.referenceBladePassOverTelemetryNyquist(), 1.0e-12);
		assertEquals(9843.188707660616, apDroneAudit.configuredHoverRotorRpm(), 1.0e-9);
		assertEquals(1.3859827396733637, apDroneAudit.lowDynamicRotorRpmMeanOverConfiguredHover(), 1.0e-15);
		assertEquals(3.0, apDroneAudit.configuredBladeCount(), 1.0e-12);
		assertEquals(682.1244826082689, apDroneAudit.lowDynamicMeanBladePassHertzForConfiguredBladeCount(), 1.0e-9);
		assertEquals(racingAudit.configuredMaxRotorRpm(), apDroneAudit.configuredMaxRotorRpm(), 1.0e-9);
	}

	@Test
	void tytoStaticYawTorqueAuditKeepsFiveInchReactionTorqueWithinReferenceWindow() {
		MotorBenchCurrentModel.StaticYawTorqueAudit racingAudit =
				MotorBenchCurrentModel.tytoStaticYawTorqueAudit(DroneConfig.racingQuad());
		MotorBenchCurrentModel.StaticYawTorqueAudit apDroneAudit =
				MotorBenchCurrentModel.tytoStaticYawTorqueAudit(DroneConfig.apDrone());

		assertEquals(0.014, racingAudit.configuredYawTorquePerThrustMeter(), 1.0e-15);
		assertEquals("x3nm", racingAudit.lowTorqueReferenceId());
		assertEquals(0.0113854299885362, racingAudit.lowTorqueReferenceFitQOverTMeters(), 1.0e-15);
		assertEquals(0.99991803288615, racingAudit.lowTorqueReferenceFitR2(), 1.0e-15);
		assertEquals(7, racingAudit.lowTorqueReferenceFitPointCount());
		assertEquals(0.011369775044516606, racingAudit.lowTorqueReferenceHighThrustMeanQOverTMeters(), 1.0e-15);
		assertEquals(0.011385548298033902, racingAudit.lowTorqueReferenceQOverTAtMaxThrustMeters(), 1.0e-15);
		assertEquals(1.229641745116027, racingAudit.configuredOverLowTorqueReferenceFit(), 1.0e-15);
		assertEquals(0.8132449991811571, racingAudit.lowTorqueReferenceFitOverConfigured(), 1.0e-15);
		assertEquals(0.8132534498595644, racingAudit.lowTorqueReferenceAtMaxThrustOverConfigured(), 1.0e-15);
		assertEquals("dnq", racingAudit.highTorqueReferenceId());
		assertEquals(0.014586521016335946, racingAudit.highTorqueReferenceFitQOverTMeters(), 1.0e-15);
		assertEquals(0.9996491957508717, racingAudit.highTorqueReferenceFitR2(), 1.0e-15);
		assertEquals(14, racingAudit.highTorqueReferenceFitPointCount());
		assertEquals(0.0145697672667851, racingAudit.highTorqueReferenceHighThrustMeanQOverTMeters(), 1.0e-15);
		assertEquals(0.014512651125968636, racingAudit.highTorqueReferenceQOverTAtMaxThrustMeters(), 1.0e-15);
		assertEquals(0.959790205239544, racingAudit.configuredOverHighTorqueReferenceFit(), 1.0e-15);
		assertEquals(1.0418943583097104, racingAudit.highTorqueReferenceFitOverConfigured(), 1.0e-15);
		assertEquals(1.0366179375691882, racingAudit.highTorqueReferenceAtMaxThrustOverConfigured(), 1.0e-15);
		assertEquals(0.0113854299885362, racingAudit.referenceFitWindowMinMeters(), 1.0e-15);
		assertEquals(0.014586521016335946, racingAudit.referenceFitWindowMaxMeters(), 1.0e-15);
		assertEquals(0.8167746523787272, racingAudit.configuredPositionWithinReferenceFitWindow(), 1.0e-15);
		assertEquals(0.0145, apDroneAudit.configuredYawTorquePerThrustMeter(), 1.0e-15);
		assertEquals(1.2735575217273138, apDroneAudit.configuredOverLowTorqueReferenceFit(), 1.0e-15);
		assertEquals(0.994068426855242, apDroneAudit.configuredOverHighTorqueReferenceFit(), 1.0e-15);
		assertEquals(0.9729713976939244, apDroneAudit.configuredPositionWithinReferenceFitWindow(), 1.0e-15);
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
