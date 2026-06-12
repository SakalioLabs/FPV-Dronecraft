package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PidTuningCalibrationTest {
	@Test
	void apDronePidTuningAuditCapturesSweepBestRowsAndDumpDMismatch() {
		PidTuningCalibration.ApDronePidTuningAudit audit = PidTuningCalibration.apDronePidTuningAudit();

		assertEquals("APDrone-Mendeley-PID-Sweeps", audit.sourceId());
		assertTrue(audit.note().contains("not project torque PID units"));
		assertAxis(
				audit.pitch(),
				"pitch",
				135.0,
				31.29778899264627,
				135.0,
				155.0,
				1.8382176024439676,
				24.0,
				5.080034776512117,
				0.058733145746361674,
				0.9412668542536383,
				2.7635655157246086,
				-1.7635655157246086,
				90.0,
				24.0,
				3.75
		);
		assertAxis(
				audit.roll(),
				"roll",
				65.0,
				49.77701311740217,
				65.0,
				85.0,
				2.5708982130254863,
				40.0,
				13.015482284971233,
				0.051648302138215155,
				0.9483516978617849,
				5.062620612137866,
				-4.062620612137866,
				60.0,
				40.0,
				1.5
		);
		assertAxis(
				audit.yaw(),
				"yaw",
				135.0,
				111.2293700243075,
				140.0,
				100.0,
				2.3691877538406065,
				50.0,
				7.007424499776081,
				0.021300019530119216,
				0.9786999804698808,
				2.957732872127417,
				-1.957732872127417,
				90.0,
				50.0,
				1.8
		);
	}

	private static void assertAxis(
			PidTuningCalibration.AxisPidTuningAudit axis,
			String name,
			double pOnlyKp,
			double pOnlyMae,
			double piKp,
			double piKi,
			double piMae,
			double pidKd,
			double pidMae,
			double piMaeOverPOnly,
			double piMaeReduction,
			double pidMaeOverPi,
			double pidMaeReduction,
			double configKd,
			double configDMin,
			double configKdOverBestKd
	) {
		assertEquals(name, axis.axis());
		assertEquals(pOnlyKp, axis.bestPOnlyKp(), 1.0e-12);
		assertEquals(pOnlyMae, axis.bestPOnlyMae(), 1.0e-12);
		assertEquals(piKp, axis.bestPiKp(), 1.0e-12);
		assertEquals(piKi, axis.bestPiKi(), 1.0e-12);
		assertEquals(piMae, axis.bestPiMae(), 1.0e-12);
		assertEquals(piKp, axis.bestPidKp(), 1.0e-12);
		assertEquals(piKi, axis.bestPidKi(), 1.0e-12);
		assertEquals(pidKd, axis.bestPidKd(), 1.0e-12);
		assertEquals(pidMae, axis.bestPidMae(), 1.0e-12);
		assertEquals(piMaeOverPOnly, axis.piMaeOverPOnlyMae(), 1.0e-15);
		assertEquals(piMaeReduction, axis.piMaeReductionVsPOnly(), 1.0e-15);
		assertEquals(pidMaeOverPi, axis.pidMaeOverPiMae(), 1.0e-15);
		assertEquals(pidMaeReduction, axis.pidMaeReductionVsPi(), 1.0e-15);
		assertEquals(piKp, axis.betaflightConfigKp(), 1.0e-12);
		assertEquals(piKi, axis.betaflightConfigKi(), 1.0e-12);
		assertEquals(configKd, axis.betaflightConfigKd(), 1.0e-12);
		assertEquals(configDMin, axis.betaflightConfigDMin(), 1.0e-12);
		assertTrue(axis.betaflightConfigMatchesBestKp());
		assertTrue(axis.betaflightConfigMatchesBestKi());
		assertFalse(axis.betaflightConfigMatchesBestKd());
		assertTrue(axis.betaflightConfigDMinMatchesBestKd());
		assertEquals(configKdOverBestKd, axis.betaflightConfigKdOverBestKd(), 1.0e-15);
		assertEquals(1.0, axis.betaflightConfigDMinOverBestKd(), 1.0e-12);
	}
}
