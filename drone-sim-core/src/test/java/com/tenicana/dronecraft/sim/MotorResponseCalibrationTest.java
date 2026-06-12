package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MotorResponseCalibrationTest {
	@Test
	void motorResponseAuditMatchesBetaflightRpmSlewPacketForRacingQuad() {
		MotorResponseCalibration.MotorResponseAudit audit =
				MotorResponseCalibration.audit(DroneConfig.racingQuad());

		assertEquals("Motor-Response-Dynamics-Packet", audit.sourceId());
		assertEquals(210, audit.packetRowCount());

		MotorResponseCalibration.RotorSPx4ActuatorLagReference rotorS =
				audit.rotorSPx4Reference();
		assertEquals("RotorS-PX4-Actuator-Lag", rotorS.referenceId());
		assertEquals(0.0125, rotorS.motorSpinupReferenceTauSeconds(), 1.0e-12);
		assertEquals(0.025, rotorS.motorSpindownReferenceTauSeconds(), 1.0e-12);
		assertEquals(0.0125, rotorS.rotorInflowReferenceTauSeconds(), 1.0e-12);
		assertEquals(3.5999999999999996, rotorS.racingQuadMotorTauOverSpinupReference(), 1.0e-15);
		assertEquals(1.7999999999999998, rotorS.racingQuadMotorTauOverSpindownReference(), 1.0e-15);
		assertEquals(2.8000000000000003, rotorS.racingQuadInflowTauOverReference(), 1.0e-15);
		assertEquals(0.006812064389955786, rotorS.racingQuadWakeTransitOneRadiusSeconds(), 1.0e-15);
		assertEquals(5.137943213162607, rotorS.racingQuadInflowTauOverOneRadiusWakeTransit(), 1.0e-15);

		MotorResponseCalibration.BetaflightRpmSlewReference betaflight =
				audit.betaflightRpmSlewReference();
		assertEquals("Betaflight-PR12562-RPM-Slew", betaflight.referenceId());
		assertEquals(16, betaflight.decodedMotorRowCount());
		assertEquals(4, betaflight.decodedLogCountWithValidRpm());
		assertEquals(0.045, betaflight.currentRacingMotorTauSeconds(), 1.0e-12);
		assertEquals(0.0140625, betaflight.currentRacingBrakingTauProxySeconds(), 1.0e-12);
		assertEquals(647_502.949989, betaflight.currentRacingNominalSpinupSlewRpmPerSecond(), 1.0e-6);
		assertEquals(2_072_009.43996, betaflight.currentRacingBrakingSlewProxyRpmPerSecond(), 1.0e-5);
		assertEquals(503_271.096249, betaflight.observedMaxPositive50msSlewRpmPerSecond(), 1.0e-6);
		assertEquals(525_477.707006, betaflight.observedMaxNegative50msSlewRpmPerSecond(), 1.0e-6);
		assertEquals(0.777249117177, betaflight.observedPositiveSlewOverCurrentSpinupProxy(), 1.0e-12);
		assertEquals(0.253607776524, betaflight.observedNegativeSlewOverCurrentBrakingProxy(), 1.0e-12);
		assertEquals(0.0578964954807, betaflight.observedPositiveTauEquivalentSeconds(), 1.0e-13);
		assertEquals(0.0554497980809, betaflight.observedNegativeTauEquivalentSeconds(), 1.0e-13);
		assertEquals(42_916.6666667, betaflight.decodedRpmMaxAcrossMotors(), 1.0e-7);

		MotorResponseCalibration.PresetMotorResponseAudit preset = audit.preset();
		assertEquals(0.045, preset.motorTimeConstantSeconds(), 1.0e-12);
		assertEquals(400.0, preset.escFrameRateHertz(), 1.0e-12);
		assertEquals(2.5, preset.escCommandFrameIntervalMilliseconds(), 1.0e-12);
		assertEquals(0.55, preset.activeBrakingStrength(), 1.0e-12);
		assertEquals(13_023.090711279678, preset.averageHoverRotorRpm(), 1.0e-9);
		assertEquals(29_137.63274949454, preset.averageMaxRotorRpm(), 1.0e-9);
		assertEquals(647_502.9499887676, preset.nominalSpinupSlewRpmPerSecond(), 1.0e-6);
		assertEquals(0.0140625, preset.activeBrakingTauProxySeconds(), 1.0e-12);
		assertEquals(2_072_009.4399640565, preset.activeBrakingSlewProxyRpmPerSecond(), 1.0e-6);
		assertEquals(3.5999999999999996, preset.motorTauOverRotorSSpinupReference(), 1.0e-15);
		assertEquals(1.7999999999999998, preset.motorTauOverRotorSSpindownReference(), 1.0e-15);
		assertEquals(0.7772491171781231, preset.observedPositiveSlewOverNominalSpinupProxy(), 1.0e-12);
		assertEquals(0.253607776523989, preset.observedNegativeSlewOverActiveBrakingProxy(), 1.0e-12);
		assertEquals(1.28658878846, preset.observedPositiveTauOverMotorTau(), 1.0e-12);
		assertEquals(3.9430967524195557, preset.observedNegativeTauOverActiveBrakingTauProxy(), 1.0e-12);
		assertEquals(0.6789351320265299, preset.configuredMaxRpmOverBetaflightDecodedMaxRpm(), 1.0e-12);
		assertEquals(11.403027150576492, preset.motorTauOverApDroneUrbanFirstOrderTauP50(), 1.0e-12);
		assertEquals(0.9393787575130691, preset.motorTauOverApDroneUrbanLevelLagP50(), 1.0e-12);
		assertEquals(0.06262525050084512, preset.escFrameIntervalOverApDroneUrbanDeltaLagP50(), 1.0e-15);
	}

	@Test
	void apDronePresetResponseAuditKeepsUrbanErpmLagAnchors() {
		MotorResponseCalibration.MotorResponseAudit audit =
				MotorResponseCalibration.audit(DroneConfig.apDrone());

		MotorResponseCalibration.ApDroneUrbanRpmLagReference urban =
				audit.apDroneUrbanRpmLagReference();
		assertEquals("APDrone-Urban-eRPM-Lag", urban.referenceId());
		assertEquals(5, urban.sourceFileCount());
		assertEquals(0.015, urban.currentApDroneMotorTauSeconds(), 1.0e-12);
		assertEquals(480.0, urban.currentApDroneEscFrameRateHertz(), 1.0e-12);
		assertEquals(0.62, urban.currentApDroneActiveBrakingStrength(), 1.0e-12);
		assertEquals(10_046.5319353, urban.currentApDroneHoverRpm(), 1.0e-7);
		assertEquals(29_739.565768, urban.currentApDroneMaxRpm(), 1.0e-6);
		assertEquals(1_982_637.71787, urban.currentApDroneNominalSpinupSlewRpmPerSecond(), 1.0e-5);
		assertEquals(0.898207694325, urban.validErpmFractionMin(), 1.0e-15);
		assertEquals(13_957.1428571, urban.mechanicalRpmP95Median(), 1.0e-7);
		assertEquals(19_000.0, urban.mechanicalRpmMaxAcrossFiles(), 1.0e-12);
		assertEquals(1.38924983735, urban.rpmOverCurrentHoverP95Median(), 1.0e-12);
		assertEquals(0.886081159956, urban.linearFitR2Median(), 1.0e-15);
		assertEquals(0.891071027341, urban.powerFitR2Median(), 1.0e-15);
		assertEquals(43.1136000001, urban.commandRpmLevelLagP10Milliseconds(), 1.0e-10);
		assertEquals(47.9040000001, urban.commandRpmLevelLagP50Milliseconds(), 1.0e-10);
		assertEquals(73.3912, urban.commandRpmLevelLagP90Milliseconds(), 1.0e-12);
		assertEquals(39.9200000001, urban.commandRpmDeltaLagP50Milliseconds(), 1.0e-10);
		assertEquals(3.94632051698, urban.firstOrderTauP50AcrossFilesP50Milliseconds(), 1.0e-12);
		assertEquals(34.0431254323, urban.firstOrderTauP90AcrossFilesMaxMilliseconds(), 1.0e-10);
		assertEquals(3.80100905019, urban.currentMotorTauOverFirstOrderTauP50(), 1.0e-12);
		assertEquals(0.313126252504, urban.currentMotorTauOverLevelLagP50(), 1.0e-12);

		MotorResponseCalibration.PresetMotorResponseAudit preset = audit.preset();
		assertEquals(0.015, preset.motorTimeConstantSeconds(), 1.0e-12);
		assertEquals(480.0, preset.escFrameRateHertz(), 1.0e-12);
		assertEquals(2.0833333333333335, preset.escCommandFrameIntervalMilliseconds(), 1.0e-15);
		assertEquals(0.62, preset.activeBrakingStrength(), 1.0e-12);
		assertEquals(10_046.531935346788, preset.averageHoverRotorRpm(), 1.0e-9);
		assertEquals(29_739.565767989377, preset.averageMaxRotorRpm(), 1.0e-9);
		assertEquals(1_982_637.7178659586, preset.nominalSpinupSlewRpmPerSecond(), 1.0e-6);
		assertEquals(0.005, preset.activeBrakingTauProxySeconds(), 1.0e-12);
		assertEquals(5_947_913.1535978755, preset.activeBrakingSlewProxyRpmPerSecond(), 1.0e-6);
		assertEquals(0.253839161695513, preset.observedPositiveSlewOverNominalSpinupProxy(), 1.0e-12);
		assertEquals(0.08834656684389214, preset.observedNegativeSlewOverActiveBrakingProxy(), 1.0e-14);
		assertEquals(3.85976636538, preset.observedPositiveTauOverMotorTau(), 1.0e-12);
		assertEquals(11.08995961618, preset.observedNegativeTauOverActiveBrakingTauProxy(), 1.0e-11);
		assertEquals(0.6929607557584375, preset.configuredMaxRpmOverBetaflightDecodedMaxRpm(), 1.0e-12);
		assertEquals(3.801009050192164, preset.motorTauOverApDroneUrbanFirstOrderTauP50(), 1.0e-12);
		assertEquals(0.3131262525043564, preset.motorTauOverApDroneUrbanLevelLagP50(), 1.0e-12);
		assertEquals(0.05218770875070427, preset.escFrameIntervalOverApDroneUrbanDeltaLagP50(), 1.0e-15);
	}

	@Test
	void motorResponseAuditRequiresRotorConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> MotorResponseCalibration.audit(null));
	}
}
