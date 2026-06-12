package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NeuroBemAirframeResidualCalibrationTest {
	@Test
	void auditMatchesNeuroBemResidualPacketAnchors() {
		NeuroBemAirframeResidualCalibration.NeuroBemAirframeResidualAudit audit =
				NeuroBemAirframeResidualCalibration.audit(DroneConfig.racingQuad());

		assertEquals("NeuroBEM-Drag-Residual-Packet", audit.sourceId());
		assertTrue(audit.caveat().contains("model residuals"));
		assertEquals(17_293, audit.packetMetricRowCount());
		assertEquals(8, audit.sourceInventoryRowCount());
		assertEquals(14_056, audit.fileSummaryRowCount());
		assertEquals(60, audit.globalSummaryMetricRowCount());
		assertEquals(171, audit.speedBinMetricRowCount());
		assertEquals(253, audit.trajectoryFamilySummaryRowCount());
		assertEquals(483, audit.targetVelocitySummaryRowCount());

		NeuroBemAirframeResidualCalibration.GlobalResidualEnvelope global = audit.globalEnvelope();
		assertEquals(251, global.predictionCsvFileCount());
		assertEquals(1_816_329L, global.rawSampleRowCount());
		assertEquals(0, global.invalidSampleRowCount());
		assertEquals(247, global.metadataMatchedFileCount());
		assertEquals(13, global.testsetMatchedFileCount());
		assertEquals(4563.064695, global.totalDurationSeconds(), 1.0e-12);
		assertEquals(76.05107825, global.totalDurationMinutes(), 1.0e-12);
		assertEquals(0.772, global.vehicleMassKg(), 1.0e-12);
		assertEquals(3.09969215197, global.bodySpeedSampleP50MetersPerSecond(), 1.0e-12);
		assertEquals(11.7354143183, global.bodySpeedSampleP95MetersPerSecond(), 1.0e-12);
		assertEquals(17.7241565773, global.bodySpeedMaxMetersPerSecond(), 1.0e-12);
		assertEquals(0.24956356196, global.residualForceSampleP50Newtons(), 1.0e-12);
		assertEquals(0.914653292187, global.residualForceSampleP95Newtons(), 1.0e-12);
		assertEquals(6.86236711785, global.residualForceMaxNewtons(), 1.0e-12);
		assertEquals(0.120814351204, global.residualForceSampleP95OverWeight(), 1.0e-12);
		assertEquals(-0.0136422721443, global.dragLikeForceSampleP50Newtons(), 1.0e-12);
		assertEquals(0.376788401479, global.dragLikeForceSampleP95Newtons(), 1.0e-12);
		assertEquals(0.231254075478, global.equivalentQuadCoeffSampleP95(), 1.0e-12);
		assertEquals(11096.3300637, global.motorRpmSampleP50(), 1.0e-10);
		assertEquals(19085.8175825, global.motorRpmSampleP95(), 1.0e-10);
		assertEquals(15.3337401, global.batteryVoltageSampleP50(), 1.0e-12);
		assertEquals(14.296264, global.batteryVoltageSampleP05(), 1.0e-12);
	}

	@Test
	void torqueEnvelopeExposesResidualTorqueAndAngularDampingScale() {
		NeuroBemAirframeResidualCalibration.ResidualTorqueEnvelope torque =
				NeuroBemAirframeResidualCalibration.audit(DroneConfig.racingQuad()).torqueEnvelope();

		assertEquals(0.588461352766, torque.angularSpeedSampleP50RadiansPerSecond(), 1.0e-12);
		assertEquals(3.81664941329, torque.angularSpeedSampleP95RadiansPerSecond(), 1.0e-12);
		assertEquals(0.00718893907333, torque.predictedTorqueSampleP50NewtonMeters(), 1.0e-15);
		assertEquals(0.0635806567204, torque.predictedTorqueSampleP95NewtonMeters(), 1.0e-15);
		assertEquals(0.00419321106075, torque.residualTorqueSampleP50NewtonMeters(), 1.0e-15);
		assertEquals(0.0227576957313, torque.residualTorqueSampleP95NewtonMeters(), 1.0e-15);
		assertEquals(0.175744943512, torque.residualTorqueMaxNewtonMeters(), 1.0e-15);
		assertEquals(0.035, torque.currentPropwashMaxTorqueNewtonMeters(), 1.0e-15);
		assertEquals(0.650219878037143, torque.residualTorqueSampleP95OverCurrentPropwashMaxTorque(), 1.0e-15);
		assertEquals(0.014519, torque.residualTorqueAbsXSampleP95NewtonMeters(), 1.0e-15);
		assertEquals(0.015987, torque.residualTorqueAbsYSampleP95NewtonMeters(), 1.0e-15);
		assertEquals(0.007825, torque.residualTorqueAbsZSampleP95NewtonMeters(), 1.0e-15);
		assertEquals(5.8076,
				torque.residualTorqueAbsXEquivalentAngularAccelSampleP95RadiansPerSecondSquared(), 1.0e-15);
		assertEquals(7.61285714286,
				torque.residualTorqueAbsYEquivalentAngularAccelSampleP95RadiansPerSecondSquared(), 1.0e-12);
		assertEquals(1.81976744186,
				torque.residualTorqueAbsZEquivalentAngularAccelSampleP95RadiansPerSecondSquared(), 1.0e-12);
		assertEquals(0.000615487067973, torque.torqueDampingLikeSampleP50NewtonMeters(), 1.0e-15);
		assertEquals(0.0133403058651, torque.torqueDampingLikeSampleP95NewtonMeters(), 1.0e-15);
		assertEquals(0.000719563050699,
				torque.equivalentAngularDampingSampleP50NewtonMetersPerRadianPerSecond(), 1.0e-15);
		assertEquals(0.012315040507,
				torque.equivalentAngularDampingSampleP95NewtonMetersPerRadianPerSecond(), 1.0e-15);
		assertEquals(0.018, torque.currentAngularDragCoefficient(), 1.0e-15);
		assertEquals(0.684168917055556,
				torque.equivalentAngularDampingSampleP95OverCurrentAngularDragCoefficient(), 1.0e-15);
	}

	@Test
	void runtimeAngularDampingGuardScalesNeuroBemTorqueResidualsByCurrentInertia() {
		NeuroBemAirframeResidualCalibration.RuntimeAngularDampingGuard guard =
				NeuroBemAirframeResidualCalibration.audit(DroneConfig.racingQuad()).runtimeAngularDampingGuard();

		assertEquals(3.09969215197, guard.speedGateStartMetersPerSecond(), 1.0e-12);
		assertEquals(11.7354143183, guard.speedGateFullMetersPerSecond(), 1.0e-12);
		assertEquals(0.588461352766, guard.angularRateGateStartRadiansPerSecond(), 1.0e-12);
		assertEquals(3.81664941329, guard.angularRateGateFullRadiansPerSecond(), 1.0e-12);
		assertEquals(1.20, guard.headroom(), 1.0e-15);
		assertEquals(5.8076, guard.residualTorqueEquivalentAngularAccelP95RadiansPerSecondSquared().x(), 1.0e-15);
		assertEquals(7.61285714286, guard.residualTorqueEquivalentAngularAccelP95RadiansPerSecondSquared().y(), 1.0e-12);
		assertEquals(1.81976744186, guard.residualTorqueEquivalentAngularAccelP95RadiansPerSecondSquared().z(), 1.0e-12);
		assertEquals(0.08362944, guard.residualTorqueP95AxisLimitNewtonMeters().x(), 1.0e-12);
		assertEquals(0.191844000000072, guard.residualTorqueP95AxisLimitNewtonMeters().y(), 1.0e-12);
		assertEquals(0.030572093023248002, guard.residualTorqueP95AxisLimitNewtonMeters().z(), 1.0e-12);
		assertEquals(0.06869968943922,
				guard.currentBaseAngularDragTorqueAtNeuroBemP95RatesNewtonMeters().x(), 1.0e-14);
		assertEquals(guard.currentBaseAngularDragTorqueAtNeuroBemP95RatesNewtonMeters().x(),
				guard.currentBaseAngularDragTorqueAtNeuroBemP95RatesNewtonMeters().y(), 1.0e-15);
		assertEquals(guard.currentBaseAngularDragTorqueAtNeuroBemP95RatesNewtonMeters().x(),
				guard.currentBaseAngularDragTorqueAtNeuroBemP95RatesNewtonMeters().z(), 1.0e-15);

		Vec3 heavierInertiaLimit = NeuroBemAirframeResidualCalibration
				.runtimeResidualTorqueP95AxisLimitNewtonMeters(
						DroneConfig.racingQuad().withInertiaKgMetersSquared(new Vec3(0.024, 0.042, 0.028)));
		assertEquals(guard.residualTorqueP95AxisLimitNewtonMeters().x() * 2.0,
				heavierInertiaLimit.x(), 1.0e-12);
		assertEquals(guard.residualTorqueP95AxisLimitNewtonMeters().y() * 2.0,
				heavierInertiaLimit.y(), 1.0e-12);
		assertEquals(guard.residualTorqueP95AxisLimitNewtonMeters().z() * 2.0,
				heavierInertiaLimit.z(), 1.0e-12);
	}

	@Test
	void residualFitKeepsAxisFitsSeparateFromCurrentPresetDrag() {
		NeuroBemAirframeResidualCalibration.ResidualFitEnvelope fit =
				NeuroBemAirframeResidualCalibration.audit(DroneConfig.racingQuad()).residualFitEnvelope();

		assertEquals(-0.00367264074515, fit.dragLikeLinearFitK(), 1.0e-15);
		assertEquals(-0.000325802979245, fit.dragLikeQuadraticFitC(), 1.0e-15);
		assertEquals(-0.00571729734066, fit.dragLikeLinearPlusQuadFitK(), 1.0e-15);
		assertEquals(0.000213229716386, fit.dragLikeLinearPlusQuadFitC(), 1.0e-15);
		assertEquals(-0.000705537358404, fit.axisXQuadraticResidualCoeff(), 1.0e-15);
		assertEquals(-6.77568646841e-05, fit.axisYQuadraticResidualCoeff(), 1.0e-15);
		assertEquals(-0.000717337486942, fit.axisZQuadraticResidualCoeff(), 1.0e-15);
	}

	@Test
	void currentPresetComparisonUsesLiveDroneConfigRatherThanPacketCurrentRows() {
		NeuroBemAirframeResidualCalibration.NeuroBemAirframeResidualAudit audit =
				NeuroBemAirframeResidualCalibration.audit(DroneConfig.racingQuad());

		NeuroBemAirframeResidualCalibration.CurrentAxisDragComparison lateral =
				audit.lateralAxisComparison();
		NeuroBemAirframeResidualCalibration.CurrentAxisDragComparison forward =
				audit.forwardAxisComparison();

		assertEquals(AirframeDragCalibration.Axis.X, lateral.axis());
		assertEquals(AirframeDragCalibration.Axis.Z, forward.axis());
		assertEquals(0.18, lateral.linearDampingCoefficient(), 1.0e-15);
		assertEquals(0.18, forward.linearDampingCoefficient(), 1.0e-15);
		assertEquals(0.0025, lateral.bodyQuadraticCoefficient(), 1.0e-15);
		assertEquals(0.0045, forward.bodyQuadraticCoefficient(), 1.0e-15);
		assertEquals(2.05, lateral.dragAtTenMetersPerSecondNewtons(), 1.0e-15);
		assertEquals(2.25, forward.dragAtTenMetersPerSecondNewtons(), 1.0e-15);
		assertEquals(0.0205, lateral.equivalentQuadraticAtTenMetersPerSecond(), 1.0e-15);
		assertEquals(0.0225, forward.equivalentQuadraticAtTenMetersPerSecond(), 1.0e-15);
		assertEquals(2.4566744503494, lateral.dragAtNeuroBemP95SpeedNewtons(), 1.0e-13);
		assertEquals(2.73211434879372, forward.dragAtNeuroBemP95SpeedNewtons(), 1.0e-13);
		assertEquals(2.68590784216752, lateral.dragAtNeuroBemP95SpeedOverNeuroBemResidualP95(), 1.0e-13);
		assertEquals(2.98704916073832, forward.dragAtNeuroBemP95SpeedOverNeuroBemResidualP95(), 1.0e-13);
		assertEquals(6.52003734909638, lateral.dragAtNeuroBemP95SpeedOverNeuroBemDragLikeP95(), 1.0e-13);
		assertEquals(7.25105745842868, forward.dragAtNeuroBemP95SpeedOverNeuroBemDragLikeP95(), 1.0e-13);
	}

	@Test
	void speedBinsExposeLowSpeedAndFastPacketComparisons() {
		NeuroBemAirframeResidualCalibration.NeuroBemAirframeResidualAudit audit =
				NeuroBemAirframeResidualCalibration.audit(DroneConfig.racingQuad());

		NeuroBemAirframeResidualCalibration.SpeedBinResidualComparison crawl = audit.crawlSpeedBin();
		assertEquals("0.5_1_m_s", crawl.binId());
		assertEquals(161_312, crawl.rowCount());
		assertEquals(0.767070923619, crawl.speedSampleP50MetersPerSecond(), 1.0e-15);
		assertEquals(0.383154430811, crawl.residualForceSampleP95Newtons(), 1.0e-15);
		assertEquals(0.279695151046, crawl.dragLikeForceSampleP95Newtons(), 1.0e-15);
		assertEquals(0.526183760193, crawl.equivalentQuadCoeffSampleP95(), 1.0e-15);
		assertEquals(0.139543760756074, crawl.currentXDragAtP50SpeedNewtons(), 1.0e-15);
		assertEquals(0.140720556359798, crawl.currentZDragAtP50SpeedNewtons(), 1.0e-15);

		NeuroBemAirframeResidualCalibration.SpeedBinResidualComparison fast = audit.fastPacketSpeedBin();
		assertEquals("6_inf_m_s", fast.binId());
		assertEquals(579_133, fast.rowCount());
		assertEquals(9.11826016831, fast.speedSampleP50MetersPerSecond(), 1.0e-14);
		assertEquals(1.12536541476, fast.residualForceSampleP95Newtons(), 1.0e-14);
		assertEquals(0.482333181678, fast.dragLikeForceSampleP95Newtons(), 1.0e-14);
		assertEquals(0.00591862401076, fast.equivalentQuadCoeffSampleP95(), 1.0e-15);
		assertEquals(1.84914350153827, fast.currentXDragAtP50SpeedNewtons(), 1.0e-14);
		assertEquals(2.01542883853225, fast.currentZDragAtP50SpeedNewtons(), 1.0e-14);
	}

	@Test
	void trajectoryFamiliesExposeMotionDependentTorqueResiduals() {
		NeuroBemAirframeResidualCalibration.NeuroBemAirframeResidualAudit audit =
				NeuroBemAirframeResidualCalibration.audit(DroneConfig.racingQuad());

		NeuroBemAirframeResidualCalibration.TrajectoryFamilyResidualSummary lemniscate =
				audit.lemniscateFamily();
		assertEquals("lemniscate", lemniscate.familyId());
		assertEquals(48, lemniscate.segmentCount());
		assertEquals(4, lemniscate.testsetSegmentCount());
		assertEquals(355_242, lemniscate.rowCount());
		assertEquals(46, lemniscate.targetVelocitySegmentCount());
		assertEquals(1.5, lemniscate.targetVelocitySampleP50MetersPerSecond(), 1.0e-15);
		assertEquals(2.0, lemniscate.targetVelocitySampleP95MetersPerSecond(), 1.0e-15);
		assertEquals(11.8527395202, lemniscate.bodySpeedSampleP95MetersPerSecond(), 1.0e-12);
		assertEquals(0.95004882295, lemniscate.residualForceSampleP95Newtons(), 1.0e-12);
		assertEquals(0.0037683808194, lemniscate.residualTorqueSampleP50NewtonMeters(), 1.0e-15);
		assertEquals(0.0256703819411, lemniscate.residualTorqueSampleP95NewtonMeters(), 1.0e-15);
		assertEquals(1.12798686845057, lemniscate.residualTorqueSampleP95OverGlobalP95(), 1.0e-14);

		NeuroBemAirframeResidualCalibration.TrajectoryFamilyResidualSummary linear =
				audit.linearOscillationFamily();
		assertEquals("linear_oscillation", linear.familyId());
		assertEquals(29, linear.segmentCount());
		assertEquals(2.25, linear.targetVelocitySampleP50MetersPerSecond(), 1.0e-15);
		assertEquals(12.8388823236, linear.bodySpeedSampleP95MetersPerSecond(), 1.0e-12);
		assertEquals(0.0296943336346, linear.residualTorqueSampleP95NewtonMeters(), 1.0e-15);
		assertEquals(0.0196407346741,
				linear.equivalentAngularDampingSampleP95NewtonMetersPerRadianPerSecond(), 1.0e-15);
		assertEquals(1.59485749664697, linear.equivalentAngularDampingSampleP95OverGlobalP95(), 1.0e-14);

		NeuroBemAirframeResidualCalibration.TrajectoryFamilyResidualSummary ellipse =
				audit.ellipseFamily();
		assertEquals("ellipse", ellipse.familyId());
		assertEquals(1, ellipse.segmentCount());
		assertEquals(1, ellipse.testsetSegmentCount());
		assertEquals(16_909, ellipse.rowCount());
		assertEquals(16.2219816887, ellipse.bodySpeedSampleP95MetersPerSecond(), 1.0e-12);
		assertEquals(0.0449853350549, ellipse.residualTorqueSampleP95NewtonMeters(), 1.0e-15);
		assertEquals(1.97670869608424, ellipse.residualTorqueSampleP95OverGlobalP95(), 1.0e-14);

		NeuroBemAirframeResidualCalibration.TrajectoryFamilyResidualSummary vertical =
				audit.verticalOscillationFamily();
		assertEquals("vertical_oscillation", vertical.familyId());
		assertEquals(6, vertical.segmentCount());
		assertEquals(0, vertical.testsetSegmentCount());
		assertEquals(3.24464821287, vertical.bodySpeedSampleP95MetersPerSecond(), 1.0e-12);
		assertEquals(0.00818470610346, vertical.residualTorqueSampleP95NewtonMeters(), 1.0e-15);
		assertEquals(0.0312104000645,
				vertical.equivalentAngularDampingSampleP95NewtonMetersPerRadianPerSecond(), 1.0e-15);
		assertEquals(2.53433190469489, vertical.equivalentAngularDampingSampleP95OverGlobalP95(), 1.0e-14);
	}

	@Test
	void neuroBemResidualAuditRequiresConfig() {
		assertThrows(IllegalArgumentException.class, () -> NeuroBemAirframeResidualCalibration.audit(null));
	}
}
