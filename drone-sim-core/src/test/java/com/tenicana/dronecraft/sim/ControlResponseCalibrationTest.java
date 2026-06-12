package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ControlResponseCalibrationTest {
	@Test
	void apDroneControlResponseAuditMatchesBlackboxSetpointToGyroReference() {
		ControlResponseCalibration.ControlResponseAudit audit =
				ControlResponseCalibration.apDroneControlResponseAudit(DroneConfig.apDrone());

		assertEquals("APDrone-Mendeley-Blackbox", audit.sourceId());
		assertEquals("setpoint_to_gyro_correlation", audit.selection());
		assertEquals(500.0, audit.analysisRateHertz(), 1.0e-12);
		assertEquals(0.35, audit.reliableCorrelationThreshold(), 1.0e-12);
		assertEquals(0.08, audit.maxLagSeconds(), 1.0e-12);
		assertEquals(30.0, audit.activityThresholdDegreesPerSecond(), 1.0e-12);
		assertEquals(250, audit.minDynamicSamples());

		assertAxis(
				audit.roll(),
				"roll",
				16,
				19.950000000079626,
				23.952000000235785,
				37.93600000000197,
				0.928063325272031,
				0.9952843137610449,
				1.043613999029235,
				6.245374193351072,
				2.3952000000235784,
				1.1976000000117892
		);
		assertAxis(
				audit.pitch(),
				"pitch",
				15,
				10.778400000106103,
				13.972000000038065,
				16.35640000003491,
				0.9433201487164501,
				0.9971638242811274,
				1.0447216907807626,
				5.868046571798189,
				1.3972000000038065,
				0.6986000000019032
		);
		assertAxis(
				audit.yaw(),
				"yaw",
				6,
				5.988000000058946,
				15.968000000086136,
				45.90800000037376,
				0.9894612568199653,
				0.9974587572330679,
				1.006794210722791,
				4.247886914794264,
				1.5968000000086136,
				0.7984000000043068
		);
	}

	private static void assertAxis(
			ControlResponseCalibration.AxisLagAudit axis,
			String name,
			int reliableRows,
			double lagP10Milliseconds,
			double lagP50Milliseconds,
			double lagP90Milliseconds,
			double absCorrelationP50,
			double absCorrelationP90,
			double gainP50,
			double maeDegreesPerSecondP50,
			double p50OverControlLatency,
			double p50OverControlPlusRcLatency
	) {
		assertEquals(name, axis.axis());
		assertEquals(21, axis.fileAxisRowCount());
		assertEquals(reliableRows, axis.reliableRowCount());
		assertEquals(lagP10Milliseconds, axis.lagP10Milliseconds(), 1.0e-12);
		assertEquals(lagP50Milliseconds, axis.lagP50Milliseconds(), 1.0e-12);
		assertEquals(lagP90Milliseconds, axis.lagP90Milliseconds(), 1.0e-12);
		assertEquals(absCorrelationP50, axis.absCorrelationP50(), 1.0e-15);
		assertEquals(absCorrelationP90, axis.absCorrelationP90(), 1.0e-15);
		assertEquals(gainP50, axis.gainP50(), 1.0e-15);
		assertEquals(maeDegreesPerSecondP50, axis.maeDegreesPerSecondP50(), 1.0e-12);
		assertEquals(10.0, axis.configuredControlLatencyMilliseconds(), 1.0e-12);
		assertEquals(20.0, axis.configuredControlPlusRcLatencyMilliseconds(), 1.0e-12);
		assertEquals(12.0, axis.configuredRcSmoothingTauMilliseconds(), 1.0e-12);
		assertEquals(1000.0 / 150.0, axis.rcFrameIntervalMilliseconds(), 1.0e-12);
		assertEquals(1000.0 / 480.0, axis.escFrameIntervalMilliseconds(), 1.0e-12);
		assertEquals(1998.0, axis.maxRateDegreesPerSecond(), 1.0e-9);
		assertEquals(p50OverControlLatency, axis.p50LagOverControlLatency(), 1.0e-15);
		assertEquals(p50OverControlPlusRcLatency, axis.p50LagOverControlPlusRcLatency(), 1.0e-15);
	}
}
