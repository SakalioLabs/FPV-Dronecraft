package com.tenicana.dronecraft.sim;

public final class NeuroBemAirframeResidualCalibration {
	private static final double EPSILON = 1.0e-12;
	private static final double COMPARISON_SPEED_METERS_PER_SECOND = 10.0;
	private static final double NEUROBEM_VEHICLE_MASS_KG = 0.772;

	public static final String SOURCE_ID = "NeuroBEM-Drag-Residual-Packet";
	public static final String CAVEAT =
			"NeuroBEM residual forces are 0.772 kg quadrotor model residuals, not isolated wind-tunnel drag or racing-speed envelope.";
	public static final int PACKET_METRIC_ROW_COUNT = 17_293;
	public static final int SOURCE_INVENTORY_ROW_COUNT = 8;
	public static final int FILE_SUMMARY_ROW_COUNT = 14_056;
	public static final int GLOBAL_SUMMARY_METRIC_ROW_COUNT = 60;
	public static final int SPEED_BIN_METRIC_ROW_COUNT = 171;
	public static final int TRAJECTORY_FAMILY_SUMMARY_ROW_COUNT = 253;
	public static final int TARGET_VELOCITY_SUMMARY_ROW_COUNT = 483;
	public static final int PREDICTION_CSV_FILE_COUNT = 251;
	public static final long RAW_SAMPLE_ROW_COUNT = 1_816_329L;
	public static final int METADATA_MATCHED_FILE_COUNT = 247;
	public static final int TESTSET_MATCHED_FILE_COUNT = 13;
	public static final int INVALID_SAMPLE_ROW_COUNT = 0;
	public static final double TOTAL_DURATION_SECONDS = 4563.064695;
	public static final double TOTAL_DURATION_MINUTES = 76.05107825;
	public static final double BODY_SPEED_SAMPLE_P50_METERS_PER_SECOND = 3.09969215197;
	public static final double BODY_SPEED_SAMPLE_P95_METERS_PER_SECOND = 11.7354143183;
	public static final double BODY_SPEED_MAX_METERS_PER_SECOND = 17.7241565773;
	public static final double RESIDUAL_FORCE_SAMPLE_P50_NEWTONS = 0.24956356196;
	public static final double RESIDUAL_FORCE_SAMPLE_P95_NEWTONS = 0.914653292187;
	public static final double RESIDUAL_FORCE_MAX_NEWTONS = 6.86236711785;
	public static final double RESIDUAL_FORCE_SAMPLE_P95_OVER_WEIGHT = 0.120814351204;
	public static final double ANGULAR_SPEED_SAMPLE_P50_RADIANS_PER_SECOND = 0.588461352766;
	public static final double ANGULAR_SPEED_SAMPLE_P95_RADIANS_PER_SECOND = 3.81664941329;
	public static final double PREDICTED_TORQUE_SAMPLE_P50_NEWTON_METERS = 0.00718893907333;
	public static final double PREDICTED_TORQUE_SAMPLE_P95_NEWTON_METERS = 0.0635806567204;
	public static final double RESIDUAL_TORQUE_SAMPLE_P50_NEWTON_METERS = 0.00419321106075;
	public static final double RESIDUAL_TORQUE_SAMPLE_P95_NEWTON_METERS = 0.0227576957313;
	public static final double RESIDUAL_TORQUE_MAX_NEWTON_METERS = 0.175744943512;
	public static final double RESIDUAL_TORQUE_ABS_X_SAMPLE_P95_NEWTON_METERS = 0.014519;
	public static final double RESIDUAL_TORQUE_ABS_Y_SAMPLE_P95_NEWTON_METERS = 0.015987;
	public static final double RESIDUAL_TORQUE_ABS_Z_SAMPLE_P95_NEWTON_METERS = 0.007825;
	public static final double RESIDUAL_TORQUE_ABS_X_EQUIV_ANGULAR_ACCEL_SAMPLE_P95_RADIANS_PER_SECOND_SQUARED = 5.8076;
	public static final double RESIDUAL_TORQUE_ABS_Y_EQUIV_ANGULAR_ACCEL_SAMPLE_P95_RADIANS_PER_SECOND_SQUARED = 7.61285714286;
	public static final double RESIDUAL_TORQUE_ABS_Z_EQUIV_ANGULAR_ACCEL_SAMPLE_P95_RADIANS_PER_SECOND_SQUARED = 1.81976744186;
	public static final double TORQUE_DAMPING_LIKE_SAMPLE_P50_NEWTON_METERS = 0.000615487067973;
	public static final double TORQUE_DAMPING_LIKE_SAMPLE_P95_NEWTON_METERS = 0.0133403058651;
	public static final double EQUIVALENT_ANGULAR_DAMPING_SAMPLE_P50_NEWTON_METERS_PER_RADIAN_PER_SECOND =
			0.000719563050699;
	public static final double EQUIVALENT_ANGULAR_DAMPING_SAMPLE_P95_NEWTON_METERS_PER_RADIAN_PER_SECOND =
			0.012315040507;
	public static final double DRAG_LIKE_FORCE_SAMPLE_P50_NEWTONS = -0.0136422721443;
	public static final double DRAG_LIKE_FORCE_SAMPLE_P95_NEWTONS = 0.376788401479;
	public static final double EQUIVALENT_QUAD_COEFF_SAMPLE_P50 = -0.00068123767585;
	public static final double EQUIVALENT_QUAD_COEFF_SAMPLE_P95 = 0.231254075478;
	public static final double DRAG_LIKE_LINEAR_FIT_K = -0.00367264074515;
	public static final double DRAG_LIKE_QUADRATIC_FIT_C = -0.000325802979245;
	public static final double DRAG_LIKE_LINEAR_PLUS_QUAD_FIT_K = -0.00571729734066;
	public static final double DRAG_LIKE_LINEAR_PLUS_QUAD_FIT_C = 0.000213229716386;
	public static final double AXIS_X_QUADRATIC_RESIDUAL_COEFF = -0.000705537358404;
	public static final double AXIS_Y_QUADRATIC_RESIDUAL_COEFF = -6.77568646841e-05;
	public static final double AXIS_Z_QUADRATIC_RESIDUAL_COEFF = -0.000717337486942;
	public static final double MOTOR_RPM_SAMPLE_P50 = 11096.3300637;
	public static final double MOTOR_RPM_SAMPLE_P95 = 19085.8175825;
	public static final double BATTERY_VOLTAGE_SAMPLE_P50 = 15.3337401;
	public static final double BATTERY_VOLTAGE_SAMPLE_P05 = 14.296264;

	private NeuroBemAirframeResidualCalibration() {
	}

	public record GlobalResidualEnvelope(
			int predictionCsvFileCount,
			long rawSampleRowCount,
			int invalidSampleRowCount,
			int metadataMatchedFileCount,
			int testsetMatchedFileCount,
			double totalDurationSeconds,
			double totalDurationMinutes,
			double vehicleMassKg,
			double bodySpeedSampleP50MetersPerSecond,
			double bodySpeedSampleP95MetersPerSecond,
			double bodySpeedMaxMetersPerSecond,
			double residualForceSampleP50Newtons,
			double residualForceSampleP95Newtons,
			double residualForceMaxNewtons,
			double residualForceSampleP95OverWeight,
			double dragLikeForceSampleP50Newtons,
			double dragLikeForceSampleP95Newtons,
			double equivalentQuadCoeffSampleP50,
			double equivalentQuadCoeffSampleP95,
			double motorRpmSampleP50,
			double motorRpmSampleP95,
			double batteryVoltageSampleP50,
			double batteryVoltageSampleP05
	) {
	}

	public record ResidualTorqueEnvelope(
			double angularSpeedSampleP50RadiansPerSecond,
			double angularSpeedSampleP95RadiansPerSecond,
			double predictedTorqueSampleP50NewtonMeters,
			double predictedTorqueSampleP95NewtonMeters,
			double residualTorqueSampleP50NewtonMeters,
			double residualTorqueSampleP95NewtonMeters,
			double residualTorqueMaxNewtonMeters,
			double currentPropwashMaxTorqueNewtonMeters,
			double residualTorqueSampleP95OverCurrentPropwashMaxTorque,
			double residualTorqueAbsXSampleP95NewtonMeters,
			double residualTorqueAbsYSampleP95NewtonMeters,
			double residualTorqueAbsZSampleP95NewtonMeters,
			double residualTorqueAbsXEquivalentAngularAccelSampleP95RadiansPerSecondSquared,
			double residualTorqueAbsYEquivalentAngularAccelSampleP95RadiansPerSecondSquared,
			double residualTorqueAbsZEquivalentAngularAccelSampleP95RadiansPerSecondSquared,
			double torqueDampingLikeSampleP50NewtonMeters,
			double torqueDampingLikeSampleP95NewtonMeters,
			double equivalentAngularDampingSampleP50NewtonMetersPerRadianPerSecond,
			double equivalentAngularDampingSampleP95NewtonMetersPerRadianPerSecond,
			double currentAngularDragCoefficient,
			double equivalentAngularDampingSampleP95OverCurrentAngularDragCoefficient
	) {
	}

	public record ResidualFitEnvelope(
			double dragLikeLinearFitK,
			double dragLikeQuadraticFitC,
			double dragLikeLinearPlusQuadFitK,
			double dragLikeLinearPlusQuadFitC,
			double axisXQuadraticResidualCoeff,
			double axisYQuadraticResidualCoeff,
			double axisZQuadraticResidualCoeff
	) {
	}

	public record CurrentAxisDragComparison(
			AirframeDragCalibration.Axis axis,
			double linearDampingCoefficient,
			double bodyQuadraticCoefficient,
			double dragAtTenMetersPerSecondNewtons,
			double equivalentQuadraticAtTenMetersPerSecond,
			double dragAtNeuroBemP95SpeedNewtons,
			double equivalentQuadraticAtNeuroBemP95Speed,
			double dragAtTenMetersPerSecondOverNeuroBemResidualP95,
			double dragAtTenMetersPerSecondOverNeuroBemDragLikeP95,
			double dragAtNeuroBemP95SpeedOverNeuroBemResidualP95,
			double dragAtNeuroBemP95SpeedOverNeuroBemDragLikeP95,
			double bodyQuadraticOverNeuroBemEquivalentQuadP95
	) {
	}

	public record SpeedBinResidualComparison(
			String binId,
			int rowCount,
			double speedSampleP50MetersPerSecond,
			double residualForceSampleP95Newtons,
			double dragLikeForceSampleP95Newtons,
			double equivalentQuadCoeffSampleP95,
			double currentXDragAtP50SpeedNewtons,
			double currentZDragAtP50SpeedNewtons,
			double currentXDragAtP50SpeedOverDragLikeP95,
			double currentZDragAtP50SpeedOverDragLikeP95
	) {
	}

	public record TrajectoryFamilyResidualSummary(
			String familyId,
			int segmentCount,
			int testsetSegmentCount,
			int rowCount,
			int targetVelocitySegmentCount,
			double targetVelocitySampleP50MetersPerSecond,
			double targetVelocitySampleP95MetersPerSecond,
			double bodySpeedSampleP50MetersPerSecond,
			double bodySpeedSampleP95MetersPerSecond,
			double residualForceSampleP95Newtons,
			double residualTorqueSampleP50NewtonMeters,
			double residualTorqueSampleP95NewtonMeters,
			double torqueDampingLikeSampleP50NewtonMeters,
			double torqueDampingLikeSampleP95NewtonMeters,
			double equivalentAngularDampingSampleP50NewtonMetersPerRadianPerSecond,
			double equivalentAngularDampingSampleP95NewtonMetersPerRadianPerSecond,
			double residualTorqueSampleP95OverGlobalP95,
			double equivalentAngularDampingSampleP95OverGlobalP95
	) {
	}

	public record NeuroBemAirframeResidualAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceInventoryRowCount,
			int fileSummaryRowCount,
			int globalSummaryMetricRowCount,
			int speedBinMetricRowCount,
			int trajectoryFamilySummaryRowCount,
			int targetVelocitySummaryRowCount,
			GlobalResidualEnvelope globalEnvelope,
			ResidualTorqueEnvelope torqueEnvelope,
			ResidualFitEnvelope residualFitEnvelope,
			CurrentAxisDragComparison lateralAxisComparison,
			CurrentAxisDragComparison forwardAxisComparison,
			SpeedBinResidualComparison crawlSpeedBin,
			SpeedBinResidualComparison trimSpeedBin,
			SpeedBinResidualComparison maneuverSpeedBin,
			SpeedBinResidualComparison fastPacketSpeedBin,
			TrajectoryFamilyResidualSummary lemniscateFamily,
			TrajectoryFamilyResidualSummary linearOscillationFamily,
			TrajectoryFamilyResidualSummary ellipseFamily,
			TrajectoryFamilyResidualSummary verticalOscillationFamily
	) {
	}

	public static NeuroBemAirframeResidualAudit audit(DroneConfig config) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}

		return new NeuroBemAirframeResidualAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_INVENTORY_ROW_COUNT,
				FILE_SUMMARY_ROW_COUNT,
				GLOBAL_SUMMARY_METRIC_ROW_COUNT,
				SPEED_BIN_METRIC_ROW_COUNT,
				TRAJECTORY_FAMILY_SUMMARY_ROW_COUNT,
				TARGET_VELOCITY_SUMMARY_ROW_COUNT,
				new GlobalResidualEnvelope(
						PREDICTION_CSV_FILE_COUNT,
						RAW_SAMPLE_ROW_COUNT,
						INVALID_SAMPLE_ROW_COUNT,
						METADATA_MATCHED_FILE_COUNT,
						TESTSET_MATCHED_FILE_COUNT,
						TOTAL_DURATION_SECONDS,
						TOTAL_DURATION_MINUTES,
						NEUROBEM_VEHICLE_MASS_KG,
						BODY_SPEED_SAMPLE_P50_METERS_PER_SECOND,
						BODY_SPEED_SAMPLE_P95_METERS_PER_SECOND,
						BODY_SPEED_MAX_METERS_PER_SECOND,
						RESIDUAL_FORCE_SAMPLE_P50_NEWTONS,
						RESIDUAL_FORCE_SAMPLE_P95_NEWTONS,
						RESIDUAL_FORCE_MAX_NEWTONS,
						RESIDUAL_FORCE_SAMPLE_P95_OVER_WEIGHT,
						DRAG_LIKE_FORCE_SAMPLE_P50_NEWTONS,
						DRAG_LIKE_FORCE_SAMPLE_P95_NEWTONS,
						EQUIVALENT_QUAD_COEFF_SAMPLE_P50,
						EQUIVALENT_QUAD_COEFF_SAMPLE_P95,
						MOTOR_RPM_SAMPLE_P50,
						MOTOR_RPM_SAMPLE_P95,
						BATTERY_VOLTAGE_SAMPLE_P50,
						BATTERY_VOLTAGE_SAMPLE_P05
				),
				residualTorqueEnvelope(config),
				new ResidualFitEnvelope(
						DRAG_LIKE_LINEAR_FIT_K,
						DRAG_LIKE_QUADRATIC_FIT_C,
						DRAG_LIKE_LINEAR_PLUS_QUAD_FIT_K,
						DRAG_LIKE_LINEAR_PLUS_QUAD_FIT_C,
						AXIS_X_QUADRATIC_RESIDUAL_COEFF,
						AXIS_Y_QUADRATIC_RESIDUAL_COEFF,
						AXIS_Z_QUADRATIC_RESIDUAL_COEFF
				),
				currentAxisComparison(config, AirframeDragCalibration.Axis.X),
				currentAxisComparison(config, AirframeDragCalibration.Axis.Z),
				speedBinComparison(config, "0.5_1_m_s", 161_312, 0.767070923619, 0.383154430811, 0.279695151046, 0.526183760193),
				speedBinComparison(config, "1_1.5_m_s", 139_239, 1.15517914043, 0.461757616852, 0.264411169336, 0.219673290107),
				speedBinComparison(config, "2.5_3_m_s", 89_520, 2.74919044185, 0.746158830247, 0.244448346754, 0.0329423693021),
				speedBinComparison(config, "6_inf_m_s", 579_133, 9.11826016831, 1.12536541476, 0.482333181678, 0.00591862401076),
				trajectoryFamilySummary(
						"lemniscate",
						48,
						4,
						355_242,
						46,
						1.5,
						2.0,
						3.14348190163,
						11.8527395202,
						0.95004882295,
						0.0037683808194,
						0.0256703819411,
						0.000488171282824,
						0.0138312536393,
						0.000619831374504,
						0.0137543792969
				),
				trajectoryFamilySummary(
						"linear_oscillation",
						29,
						1,
						160_927,
						29,
						2.25,
						2.6,
						3.05540662315,
						12.8388823236,
						0.814105480701,
						0.00509266717939,
						0.0296943336346,
						0.0012766741647,
						0.0192212830102,
						0.00205848936421,
						0.0196407346741
				),
				trajectoryFamilySummary(
						"ellipse",
						1,
						1,
						16_909,
						1,
						2.4,
						2.4,
						12.5136570164,
						16.2219816887,
						1.3736296898,
						0.0141914773368,
						0.0449853350549,
						-0.00361185946738,
						0.0113207227346,
						-0.00258760976613,
						0.00922561190983
				),
				trajectoryFamilySummary(
						"vertical_oscillation",
						6,
						0,
						30_798,
						6,
						1.0,
						1.7,
						1.04452690348,
						3.24464821287,
						0.376874366455,
						0.0026201935043,
						0.00818470610346,
						0.000231135898947,
						0.00522571279876,
						0.000688390892377,
						0.0312104000645
				)
		);
	}

	private static ResidualTorqueEnvelope residualTorqueEnvelope(DroneConfig config) {
		return new ResidualTorqueEnvelope(
				ANGULAR_SPEED_SAMPLE_P50_RADIANS_PER_SECOND,
				ANGULAR_SPEED_SAMPLE_P95_RADIANS_PER_SECOND,
				PREDICTED_TORQUE_SAMPLE_P50_NEWTON_METERS,
				PREDICTED_TORQUE_SAMPLE_P95_NEWTON_METERS,
				RESIDUAL_TORQUE_SAMPLE_P50_NEWTON_METERS,
				RESIDUAL_TORQUE_SAMPLE_P95_NEWTON_METERS,
				RESIDUAL_TORQUE_MAX_NEWTON_METERS,
				config.propwashMaxTorqueNewtonMeters(),
				ratio(RESIDUAL_TORQUE_SAMPLE_P95_NEWTON_METERS, config.propwashMaxTorqueNewtonMeters()),
				RESIDUAL_TORQUE_ABS_X_SAMPLE_P95_NEWTON_METERS,
				RESIDUAL_TORQUE_ABS_Y_SAMPLE_P95_NEWTON_METERS,
				RESIDUAL_TORQUE_ABS_Z_SAMPLE_P95_NEWTON_METERS,
				RESIDUAL_TORQUE_ABS_X_EQUIV_ANGULAR_ACCEL_SAMPLE_P95_RADIANS_PER_SECOND_SQUARED,
				RESIDUAL_TORQUE_ABS_Y_EQUIV_ANGULAR_ACCEL_SAMPLE_P95_RADIANS_PER_SECOND_SQUARED,
				RESIDUAL_TORQUE_ABS_Z_EQUIV_ANGULAR_ACCEL_SAMPLE_P95_RADIANS_PER_SECOND_SQUARED,
				TORQUE_DAMPING_LIKE_SAMPLE_P50_NEWTON_METERS,
				TORQUE_DAMPING_LIKE_SAMPLE_P95_NEWTON_METERS,
				EQUIVALENT_ANGULAR_DAMPING_SAMPLE_P50_NEWTON_METERS_PER_RADIAN_PER_SECOND,
				EQUIVALENT_ANGULAR_DAMPING_SAMPLE_P95_NEWTON_METERS_PER_RADIAN_PER_SECOND,
				config.angularDragCoefficient(),
				ratio(EQUIVALENT_ANGULAR_DAMPING_SAMPLE_P95_NEWTON_METERS_PER_RADIAN_PER_SECOND,
						config.angularDragCoefficient())
		);
	}

	private static CurrentAxisDragComparison currentAxisComparison(
			DroneConfig config,
			AirframeDragCalibration.Axis axis
	) {
		double linearCoefficient = config.linearDragCoefficient();
		double bodyQuadraticCoefficient = AirframeDragCalibration.bodyQuadraticCoefficient(config, axis);
		double tenMeterDrag = AirframeDragCalibration.dragForceNewtons(
				linearCoefficient,
				bodyQuadraticCoefficient,
				COMPARISON_SPEED_METERS_PER_SECOND
		);
		double p95SpeedDrag = AirframeDragCalibration.dragForceNewtons(
				linearCoefficient,
				bodyQuadraticCoefficient,
				BODY_SPEED_SAMPLE_P95_METERS_PER_SECOND
		);
		return new CurrentAxisDragComparison(
				axis,
				linearCoefficient,
				bodyQuadraticCoefficient,
				tenMeterDrag,
				equivalentQuadraticCoefficient(tenMeterDrag, COMPARISON_SPEED_METERS_PER_SECOND),
				p95SpeedDrag,
				equivalentQuadraticCoefficient(p95SpeedDrag, BODY_SPEED_SAMPLE_P95_METERS_PER_SECOND),
				ratio(tenMeterDrag, RESIDUAL_FORCE_SAMPLE_P95_NEWTONS),
				ratio(tenMeterDrag, DRAG_LIKE_FORCE_SAMPLE_P95_NEWTONS),
				ratio(p95SpeedDrag, RESIDUAL_FORCE_SAMPLE_P95_NEWTONS),
				ratio(p95SpeedDrag, DRAG_LIKE_FORCE_SAMPLE_P95_NEWTONS),
				ratio(bodyQuadraticCoefficient, EQUIVALENT_QUAD_COEFF_SAMPLE_P95)
		);
	}

	private static SpeedBinResidualComparison speedBinComparison(
			DroneConfig config,
			String binId,
			int rowCount,
			double speedSampleP50MetersPerSecond,
			double residualForceSampleP95Newtons,
			double dragLikeForceSampleP95Newtons,
			double equivalentQuadCoeffSampleP95
	) {
		double currentXDrag = AirframeDragCalibration.dragForceNewtons(
				config.linearDragCoefficient(),
				AirframeDragCalibration.bodyQuadraticCoefficient(config, AirframeDragCalibration.Axis.X),
				speedSampleP50MetersPerSecond
		);
		double currentZDrag = AirframeDragCalibration.dragForceNewtons(
				config.linearDragCoefficient(),
				AirframeDragCalibration.bodyQuadraticCoefficient(config, AirframeDragCalibration.Axis.Z),
				speedSampleP50MetersPerSecond
		);
		return new SpeedBinResidualComparison(
				binId,
				rowCount,
				speedSampleP50MetersPerSecond,
				residualForceSampleP95Newtons,
				dragLikeForceSampleP95Newtons,
				equivalentQuadCoeffSampleP95,
				currentXDrag,
				currentZDrag,
				ratio(currentXDrag, dragLikeForceSampleP95Newtons),
				ratio(currentZDrag, dragLikeForceSampleP95Newtons)
		);
	}

	private static TrajectoryFamilyResidualSummary trajectoryFamilySummary(
			String familyId,
			int segmentCount,
			int testsetSegmentCount,
			int rowCount,
			int targetVelocitySegmentCount,
			double targetVelocitySampleP50MetersPerSecond,
			double targetVelocitySampleP95MetersPerSecond,
			double bodySpeedSampleP50MetersPerSecond,
			double bodySpeedSampleP95MetersPerSecond,
			double residualForceSampleP95Newtons,
			double residualTorqueSampleP50NewtonMeters,
			double residualTorqueSampleP95NewtonMeters,
			double torqueDampingLikeSampleP50NewtonMeters,
			double torqueDampingLikeSampleP95NewtonMeters,
			double equivalentAngularDampingSampleP50NewtonMetersPerRadianPerSecond,
			double equivalentAngularDampingSampleP95NewtonMetersPerRadianPerSecond
	) {
		return new TrajectoryFamilyResidualSummary(
				familyId,
				segmentCount,
				testsetSegmentCount,
				rowCount,
				targetVelocitySegmentCount,
				targetVelocitySampleP50MetersPerSecond,
				targetVelocitySampleP95MetersPerSecond,
				bodySpeedSampleP50MetersPerSecond,
				bodySpeedSampleP95MetersPerSecond,
				residualForceSampleP95Newtons,
				residualTorqueSampleP50NewtonMeters,
				residualTorqueSampleP95NewtonMeters,
				torqueDampingLikeSampleP50NewtonMeters,
				torqueDampingLikeSampleP95NewtonMeters,
				equivalentAngularDampingSampleP50NewtonMetersPerRadianPerSecond,
				equivalentAngularDampingSampleP95NewtonMetersPerRadianPerSecond,
				ratio(residualTorqueSampleP95NewtonMeters, RESIDUAL_TORQUE_SAMPLE_P95_NEWTON_METERS),
				ratio(equivalentAngularDampingSampleP95NewtonMetersPerRadianPerSecond,
						EQUIVALENT_ANGULAR_DAMPING_SAMPLE_P95_NEWTON_METERS_PER_RADIAN_PER_SECOND)
		);
	}

	private static double equivalentQuadraticCoefficient(double dragForceNewtons, double speedMetersPerSecond) {
		double speed = Math.max(0.0, speedMetersPerSecond);
		if (speed <= EPSILON) {
			return 0.0;
		}
		return Math.max(0.0, dragForceNewtons) / (speed * speed);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
