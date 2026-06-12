package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PropellerDamageCalibrationTest {
	@Test
	void propDamageAuditMatchesFaultDatasetPacketAndRuntimeCurve() {
		PropellerDamageCalibration.PropellerDamageAudit audit =
				PropellerDamageCalibration.audit(DroneConfig.racingQuad());
		PropellerDamageCalibration.FaultDatasetAudit dataset = audit.dataset();
		PropellerDamageCalibration.RuntimeModelAudit runtime = audit.runtimeModel();

		assertEquals("Prop-Damage-Vibration-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("dimensionless runtime intensities"));
		assertEquals(470, dataset.packetRowCount());
		assertEquals(8, dataset.sourceInventoryRowCount());
		assertEquals(5, dataset.uavRealisticFaultClassCount());
		assertEquals(216, dataset.uavRealisticDataFileCount());
		assertEquals(99, dataset.uavRealisticSensorcombinedFileCount());
		assertEquals(99, dataset.uavRealisticAudiobufferFileCount());
		assertEquals(9, dataset.uavRealisticImuFileCount());
		assertEquals(3475.290487, dataset.uavRealisticTotalSizeMb(), 1.0e-12);
		assertEquals(1.566803460010257, dataset.singleBrokenGyroRmsRatio(), 1.0e-15);
		assertEquals(3.65539974853721, dataset.singleBrokenAccelerometerRmsRatio(), 1.0e-15);
		assertEquals(2.34459385954, dataset.strongestSensorcombinedGyroRmsRatio(), 1.0e-12);
		assertEquals(7.41126253241, dataset.strongestSensorcombinedAccelerometerRmsRatio(), 1.0e-12);
		assertEquals(1.06409952825, dataset.strongestRawImuGyroRmsRatio(), 1.0e-12);
		assertEquals(1.51580878648, dataset.strongestRawImuAccelerometerRmsRatio(), 1.0e-12);
		assertEquals(3.000977801285221, dataset.padreSingleRotorAccelerometerFeatureRmsRatio(), 1.0e-15);
		assertEquals(3.125830267410634, dataset.padreTwoPositionAccelerometerFeatureRmsRatio(), 1.0e-15);
		assertEquals(1023.54145345, dataset.djiMini2SampleRateHertz(), 1.0e-12);
		assertEquals(511.770726726, dataset.djiMini2NyquistHertz(), 1.0e-12);
		assertEquals(159.678464052, dataset.djiMini2HealthyDominantFrequencyHertz(), 1.0e-12);
		assertEquals(0.961371945529, dataset.djiMini2VectorRmsRatioMin(), 1.0e-12);
		assertEquals(1.15481410476, dataset.djiMini2VectorRmsRatioMax(), 1.0e-12);

		assertEquals(0.012, runtime.configuredHealthyImbalance(), 1.0e-12);
		assertEquals(0.75, runtime.referenceDamage(), 1.0e-12);
		assertEquals(0.18825, runtime.referenceEffectiveImbalance(), 1.0e-12);
		assertEquals(0.7205427227311096, runtime.referenceDamageVibrationAtMaxSpin(), 1.0e-15);
		assertEquals(0.18825, runtime.referenceImbalanceVibrationAtMaxSpin(), 1.0e-12);
		assertEquals(0.217637640824031, runtime.referenceThrustScale(), 1.0e-15);
		assertEquals(0.003255172013208225, runtime.referenceProfileDragTorqueAtMaxSpin(), 1.0e-15);
		assertEquals(0.955, runtime.fullDamageVibrationAtMaxSpin(), 1.0e-15);
		assertEquals(0.0, runtime.fullDamageThrustScale(), 1.0e-15);
		assertEquals(651.154535564, runtime.racingHoverBladePassFrequencyHertz(), 1.0e-12);
		assertEquals(1456.88163747, runtime.racingMaxBladePassFrequencyHertz(), 1.0e-12);
		assertEquals(372.386917888, runtime.racingHoverBladePassAlias1024Hertz(), 1.0e-12);
		assertEquals(433.340184022, runtime.racingMaxBladePassAlias1024Hertz(), 1.0e-12);
		assertEquals(1.2723559624632956, runtime.hoverBladePassOverDjiNyquist(), 1.0e-12);
		assertEquals(2.846746719552032, runtime.maxBladePassOverDjiNyquist(), 1.0e-12);
	}

	@Test
	void damageCurvesKeepLightFaultsVisibleButModest() {
		RotorSpec rotor = DroneConfig.racingQuad().rotors().get(0);
		double maxOmega = rotor.maxOmegaRadiansPerSecond();

		double mildFault = PropellerDamageCalibration.damageVibrationIntensity(rotor, maxOmega, 0.90);
		double moderateFault = PropellerDamageCalibration.damageVibrationIntensity(rotor, maxOmega, 0.75);
		double heavyFault = PropellerDamageCalibration.damageVibrationIntensity(rotor, maxOmega, 0.50);
		double severeFault = PropellerDamageCalibration.damageVibrationIntensity(rotor, maxOmega, 0.25);
		double halfSpeedMildFault = PropellerDamageCalibration.damageVibrationIntensity(rotor, maxOmega * 0.5, 0.90);

		assertEquals(0.031360000000000006, mildFault, 1.0e-15);
		assertEquals(0.06035270664964894, moderateFault, 1.0e-15);
		assertEquals(0.3411923065912822, heavyFault, 1.0e-15);
		assertEquals(0.7205427227311096, severeFault, 1.0e-15);
		assertTrue(halfSpeedMildFault > mildFault * 0.55);
		assertTrue(halfSpeedMildFault < mildFault);
		assertEquals(0.18825, PropellerDamageCalibration.damageImbalanceIntensity(0.75)
				+ rotor.imbalanceIntensity(), 1.0e-12);
		assertEquals(0.217637640824031, PropellerDamageCalibration.thrustScale(0.25), 1.0e-15);
	}
}
