package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NanodroneSysIdCalibrationTest {
	@Test
	void nanodroneSysIdAuditMatchesPacketInventoryAndReferenceModel() {
		NanodroneSysIdCalibration.NanodroneSysIdAudit audit =
				NanodroneSysIdCalibration.audit(DroneConfig.racingQuad());

		assertEquals("Nanodrone-System-Identification-Packet", audit.sourceId());
		assertEquals(1092, audit.rowTypeCounts().totalRowCount());
		assertEquals(20, audit.rowTypeCounts().sourceInventoryRowCount());
		assertEquals(21, audit.rowTypeCounts().columnSchemaRowCount());
		assertEquals(405, audit.rowTypeCounts().fileSummaryRowCount());
		assertEquals(343, audit.rowTypeCounts().distributionSummaryRowCount());
		assertEquals(13, audit.rowTypeCounts().modelConstantRowCount());
		assertEquals(60, audit.rowTypeCounts().fixedModelEvalRowCount());
		assertEquals(72, audit.rowTypeCounts().trainToTestEvalRowCount());
		assertEquals(132, audit.rowTypeCounts().coefficientFitRowCount());
		assertEquals(10, audit.rowTypeCounts().currentModelComparisonRowCount());
		assertEquals(14, audit.rowTypeCounts().groupSummaryRowCount());
		assertEquals(2, audit.rowTypeCounts().methodRowCount());

		NanodroneSysIdCalibration.SourceDataAudit source = audit.sourceData();
		assertEquals("Crazyflie 2.1 Brushless nano-quadrotor", source.platform());
		assertEquals("Nonlinear System Identification for a Nano-drone Benchmark", source.paperTitle());
		assertEquals(75_000, source.claimedSampleCount());
		assertEquals(75_096, source.actualLoadedSampleCount());
		assertEquals(750.81, source.actualLoadedDurationSeconds(), 1.0e-12);
		assertEquals(100.0, source.sampleRateHertz(), 1.0e-12);
		assertEquals(0.01, source.sampleDtSeconds(), 1.0e-12);
		assertEquals(50, source.benchmarkOpenLoopHorizonSteps());
		assertEquals(0.5, source.benchmarkOpenLoopHorizonSeconds(), 1.0e-12);
		assertEquals(15, source.actualCsvFileCount());
		assertEquals(12, source.trainCsvFileCount());
		assertEquals(3, source.testCsvFileCount());
		assertEquals(55_599, source.trainSampleCount());
		assertEquals(19_497, source.testSampleCount());
		assertEquals("chirp|melon|random|square", source.trajectoryNames());
		assertEquals(24_000, source.chirpSampleCount());
		assertEquals(23_999, source.randomSampleCount());
		assertEquals(19_497, source.melonSampleCount());
		assertEquals(7_600, source.squareSampleCount());

		NanodroneSysIdCalibration.ReferenceModelAudit model = audit.referenceModel();
		assertEquals(0.045, model.massKg(), 1.0e-15);
		assertEquals(9.81, model.gravityMetersPerSecondSquared(), 1.0e-15);
		assertEquals(0.0353, model.armLengthMeters(), 1.0e-15);
		assertEquals(3.72e-8, model.sourceKtNewtonsPerRadianPerSecondSquared(), 1.0e-20);
		assertEquals(7.74e-12, model.sourceKcNewtonMetersPerRadianPerSecondSquared(), 1.0e-24);
		assertEquals(2.0, model.sourceThrustToWeight(), 1.0e-15);
		assertEquals(0.8829, model.sourceTmaxNewtons(), 1.0e-15);
		assertEquals(2.3951e-5, model.sourceJxxKgMetersSquared(), 1.0e-18);
		assertEquals(2.3951e-5, model.sourceJyyKgMetersSquared(), 1.0e-18);
		assertEquals(3.2347e-6, model.sourceJzzKgMetersSquared(), 1.0e-19);
		assertEquals(0.01, model.sourceMaxTorqueRollNewtonMeters(), 1.0e-15);
		assertEquals(0.01, model.sourceMaxTorquePitchNewtonMeters(), 1.0e-15);
		assertEquals(0.003, model.sourceMaxTorqueYawNewtonMeters(), 1.0e-15);
	}

	@Test
	void thrustOmegaSquaredFitGeneralizesAcrossHeldOutTrajectory() {
		NanodroneSysIdCalibration.NanodroneSysIdAudit audit =
				NanodroneSysIdCalibration.audit(DroneConfig.racingQuad());

		NanodroneSysIdCalibration.FixedSourceModelFit sourceFit = audit.sourceThrustFit();
		assertEquals("thrust_body_z_specific_force_all_source_constant", sourceFit.signalId());
		assertEquals(3.72e-8, sourceFit.sourceCoefficient(), 1.0e-20);
		assertEquals(0.0146942006553, sourceFit.rmse(), 1.0e-15);
		assertEquals(0.958394627403, sourceFit.r2(), 1.0e-12);
		assertEquals(0.000494231513556, sourceFit.meanPredictionMinusMeasurement(), 1.0e-18);
		assertEquals(75_096, sourceFit.samples());

		NanodroneSysIdCalibration.TrainToTestFit thrust = audit.trainThrustFit();
		assertEquals("thrust_body_z_specific_force_train_fit", thrust.signalId());
		assertEquals(3.71776253321e-8, thrust.trainFitCoefficient(), 1.0e-20);
		assertEquals(0.999398530433, thrust.trainFitCoefficientOverSource(), 1.0e-12);
		assertEquals(0.0137138575143, thrust.trainRmse(), 1.0e-15);
		assertEquals(0.960951623312, thrust.trainR2(), 1.0e-12);
		assertEquals(55_599, thrust.trainSamples());
		assertEquals(0.0171630004128, thrust.testRmse(), 1.0e-15);
		assertEquals(0.952819569406, thrust.testR2(), 1.0e-12);
		assertEquals(0.00073724384236, thrust.testBias(), 1.0e-17);
		assertEquals(19_497, thrust.testSamples());
		assertEquals(0.0146874060585, thrust.allRmse(), 1.0e-15);
		assertEquals(0.958433095147, thrust.allR2(), 1.0e-12);
		assertEquals(75_096, thrust.allSamples());
		assertTrue(thrust.testR2() > 0.95);
	}

	@Test
	void torqueFitsRemainExplicitlyMarkedAsWeakScaleSignals() {
		NanodroneSysIdCalibration.NanodroneSysIdAudit audit =
				NanodroneSysIdCalibration.audit(DroneConfig.racingQuad());

		NanodroneSysIdCalibration.TrainToTestFit roll = audit.trainRollTorqueFit();
		assertEquals(3.66997690136e-9, roll.trainFitCoefficient(), 1.0e-21);
		assertEquals(0.0986552930472, roll.trainFitCoefficientOverSource(), 1.0e-13);
		assertEquals(0.147066706391, roll.trainR2(), 1.0e-12);
		assertEquals(-0.145056930777, roll.testR2(), 1.0e-12);

		NanodroneSysIdCalibration.TrainToTestFit pitch = audit.trainPitchTorqueFit();
		assertEquals(1.85015998527e-9, pitch.trainFitCoefficient(), 1.0e-21);
		assertEquals(0.049735483475, pitch.trainFitCoefficientOverSource(), 1.0e-13);
		assertEquals(0.0905159613103, pitch.trainR2(), 1.0e-13);
		assertEquals(-0.0937251267048, pitch.testR2(), 1.0e-13);

		NanodroneSysIdCalibration.TrainToTestFit yaw = audit.trainYawTorqueFit();
		assertEquals(-4.81878079796e-12, yaw.trainFitCoefficient(), 1.0e-24);
		assertEquals(-0.622581498444, yaw.trainFitCoefficientOverSource(), 1.0e-12);
		assertEquals(0.16279913094, yaw.trainR2(), 1.0e-12);
		assertEquals(0.140449460194, yaw.testR2(), 1.0e-12);
		assertTrue(roll.testR2() < 0.0);
		assertTrue(pitch.testR2() < 0.0);
		assertTrue(Math.abs(yaw.trainFitCoefficientOverSource()) < 1.0);
	}

	@Test
	void currentScaleComparesNanodroneOmegaSquaredDataAgainstConfiguredQuad() {
		NanodroneSysIdCalibration.NanodroneSysIdAudit audit =
				NanodroneSysIdCalibration.audit(DroneConfig.racingQuad());

		NanodroneSysIdCalibration.CurrentScaleAudit current = audit.currentScale();
		assertEquals(1.45e-6, current.configuredAverageRotorThrustCoefficient(), 1.0e-18);
		assertEquals(0.0256551724137931, current.sourceKtOverConfiguredRotorThrustCoefficient(), 1.0e-15);
		assertEquals(1363.77487018632, current.configuredHoverRotorRadiansPerSecond(), 1.0e-10);
		assertEquals(3051.28576629365, current.configuredMaxRotorRadiansPerSecond(), 1.0e-10);
		assertEquals(1994.10804384, current.nanodroneMotorRadiansPerSecondP95(), 1.0e-8);
		assertEquals(2530.66938885, current.nanodroneMotorRadiansPerSecondMax(), 1.0e-8);
		assertEquals(1.4621973812786, current.nanodroneMotorP95OverConfiguredHover(), 1.0e-13);
		assertEquals(0.82937803361629, current.nanodroneMotorMaxOverConfiguredMax(), 1.0e-14);
		assertEquals(1.01961284597, current.nanodroneSourceKtThrustToWeightP50(), 1.0e-12);
		assertEquals(1.3036074917, current.nanodroneSourceKtThrustToWeightP95(), 1.0e-12);
		assertEquals(2.03599811389, current.nanodroneSourceKtThrustToWeightMax(), 1.0e-12);
	}

	@Test
	void nanodroneAuditRequiresRotorConfiguration() {
		assertThrows(IllegalArgumentException.class, () -> NanodroneSysIdCalibration.audit(null));
	}
}
