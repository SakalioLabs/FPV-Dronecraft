package com.tenicana.dronecraft.sim;

public final class NeuroBemAirframeResidualCalibration {
	private static final double EPSILON = 1.0e-12;
	private static final double COMPARISON_SPEED_METERS_PER_SECOND = 10.0;
	private static final double NEUROBEM_VEHICLE_MASS_KG = 0.772;

	public static final String SOURCE_ID = "NeuroBEM-Drag-Residual-Packet";
	public static final String CAVEAT =
			"NeuroBEM residual forces are 0.772 kg quadrotor model residuals, not isolated wind-tunnel drag or racing-speed envelope.";
	public static final int PACKET_METRIC_ROW_COUNT = 8176;
	public static final int SOURCE_INVENTORY_ROW_COUNT = 5;
	public static final int FILE_SUMMARY_ROW_COUNT = 8032;
	public static final int GLOBAL_SUMMARY_METRIC_ROW_COUNT = 39;
	public static final int SPEED_BIN_METRIC_ROW_COUNT = 99;
	public static final int PREDICTION_CSV_FILE_COUNT = 251;
	public static final long RAW_SAMPLE_ROW_COUNT = 1_816_329L;
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

	public record NeuroBemAirframeResidualAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceInventoryRowCount,
			int fileSummaryRowCount,
			int globalSummaryMetricRowCount,
			int speedBinMetricRowCount,
			GlobalResidualEnvelope globalEnvelope,
			ResidualFitEnvelope residualFitEnvelope,
			CurrentAxisDragComparison lateralAxisComparison,
			CurrentAxisDragComparison forwardAxisComparison,
			SpeedBinResidualComparison crawlSpeedBin,
			SpeedBinResidualComparison trimSpeedBin,
			SpeedBinResidualComparison maneuverSpeedBin,
			SpeedBinResidualComparison fastPacketSpeedBin
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
				new GlobalResidualEnvelope(
						PREDICTION_CSV_FILE_COUNT,
						RAW_SAMPLE_ROW_COUNT,
						INVALID_SAMPLE_ROW_COUNT,
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
				speedBinComparison(config, "6_inf_m_s", 579_133, 9.11826016831, 1.12536541476, 0.482333181678, 0.00591862401076)
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
