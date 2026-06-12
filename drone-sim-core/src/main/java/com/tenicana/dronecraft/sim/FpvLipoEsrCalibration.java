package com.tenicana.dronecraft.sim;

public final class FpvLipoEsrCalibration {
	private static final double EPSILON = 1.0e-12;
	private static final double AMBIENT_REFERENCE_CELSIUS = 25.0;
	private static final double TWENTY_PERCENT_SAG_FRACTION = 0.20;
	private static final double IR_FORMULA_CONSTANT = 2500.0;
	private static final double FRESH_FULL_SOC_RESISTANCE_SCALE = 1.002684224007052;
	private static final double WORN_TEN_PERCENT_SOC_RESISTANCE_SCALE = 1.1902959848598708;

	public static final String SOURCE_ID = "FPV-LiPo-ESR-Calibration-Packet";
	public static final String CAVEAT =
			"FPV charger-IR anchors set absolute high-C pack scale; Mendeley/NASA/Figshare rows are SOC, SOH, C-rate, and temperature shape priors.";
	public static final int PACKET_METRIC_ROW_COUNT = 774;
	public static final int SOURCE_INVENTORY_ROW_COUNT = 6;
	public static final int MEASURED_IR_ANCHOR_ROW_COUNT = 60;
	public static final int CURRENT_PRESET_ABSOLUTE_CHECK_ROW_COUNT = 54;
	public static final int CURRENT_VS_MEASURED_IR_ROW_COUNT = 80;
	public static final int CURRENT_FORMULA_GUARDRAIL_ROW_COUNT = 70;
	public static final int SOC_SOH_RUNTIME_PROJECTION_ROW_COUNT = 396;
	public static final int TEMPERATURE_MODEL_CHECK_ROW_COUNT = 30;
	public static final int TEMPERATURE_REFERENCE_CHECK_ROW_COUNT = 18;
	public static final int C_RATE_TEMPERATURE_SHAPE_ROW_COUNT = 12;
	public static final int METHOD_GUARDRAIL_ROW_COUNT = 8;
	public static final int SUMMARY_ROW_COUNT = 39;
	public static final double MEASURED_PER_CELL_IR_MIN_MILLIOHMS = 4.975;
	public static final double MEASURED_PER_CELL_IR_MEDIAN_MILLIOHMS = 6.525;
	public static final double MEASURED_PER_CELL_IR_MAX_MILLIOHMS = 6.85;
	public static final double MEASURED_PACK_IR_MIN_MILLIOHMS = 19.9;
	public static final double MEASURED_PACK_IR_MAX_MILLIOHMS = 27.4;
	public static final double MEASURED_SAG_AT_90A_MIN_VOLTS = 1.791;
	public static final double MEASURED_SAG_AT_90A_MAX_VOLTS = 2.466;
	public static final double JEFFCO_COLD_OVER_WARM_IR_MIN = 1.67693661972;
	public static final double JEFFCO_COLD_OVER_WARM_IR_MAX = 2.03448275862;
	public static final double CURRENT_MODEL_OVER_JEFFCO_MIN = 0.747690178085;
	public static final double CURRENT_MODEL_OVER_JEFFCO_MAX = 0.907108091157;

	private FpvLipoEsrCalibration() {
	}

	public record MeasuredIrAnchor(
			String packName,
			int cells,
			double capacityAmpHours,
			double listedC,
			double listedCurrentAmps,
			double perCellIrMeanMilliohms,
			double perCellIrMinMilliohms,
			double perCellIrMaxMilliohms,
			double cellIrSpreadMilliohms,
			double cellIrCoefficientOfVariationPercent,
			double packIrMilliohms,
			double packIrOhms,
			double sagAt90AmpsVolts
	) {
	}

	public record MeasuredIrRange(
			int measuredPackCount,
			double measuredPerCellIrMinMilliohms,
			double measuredPerCellIrMedianMilliohms,
			double measuredPerCellIrMaxMilliohms,
			double measuredPackIrMinMilliohms,
			double measuredPackIrMaxMilliohms,
			double measuredSagAt90AmpsMinVolts,
			double measuredSagAt90AmpsMaxVolts
	) {
	}

	public record ConfiguredPackAudit(
			int cells,
			double nominalVoltage,
			double capacityAmpHours,
			double maxCurrentAmps,
			double currentLimitC,
			double packResistanceOhms,
			double perCellIrMilliohms,
			double sagAtCurrentLimitVolts,
			double sagAtCurrentLimitPercentNominal,
			double currentForTwentyPercentNominalSagAmps,
			double configuredCurrentOverTwentyPercentSagCurrent,
			double configuredPerCellIrOverLowestMeasuredFpvAnchor,
			double configuredPerCellIrOverMeasuredMedian,
			double configuredPerCellIrOverHighestMeasuredFpvAnchor
	) {
	}

	public record IrFormulaGuardrail(
			double configuredPerCellIrMilliohms,
			double configuredCurrentLimitC,
			double irFormulaTrueC,
			double irFormulaTrueCurrentAmps,
			double configuredCurrentOverIrFormulaCurrent,
			double sagAtIrFormulaCurrentVolts,
			double sagAtConfiguredCurrentVolts,
			double sagAtConfiguredCurrentPercentNominal,
			double coldTenCdropIrDoubledTrueCurrentAmps,
			double configuredCurrentOverColdTenCdropCurrent
	) {
	}

	public record SocSohProjection(
			String projectionId,
			double resistanceScale,
			double projectedPackResistanceOhms,
			double projectedPerCellResistanceMilliohms,
			double configCurrentSagVolts,
			double configCurrentForTwentyPercentNominalSagAmps,
			double configCurrentOverTwentyPercentSagCurrent
	) {
	}

	public record TemperatureModelPoint(
			double batteryTemperatureCelsius,
			double ambientTemperatureCelsius,
			double resistanceScale,
			double currentScale,
			double thermalPowerLimit,
			double effectiveResistanceOhms,
			double effectiveCurrentLimitAmps,
			double sagAtTemperatureScaledLimitVolts
	) {
	}

	public record TemperatureReferenceCheck(
			double jeffcoReferenceColdOverWarmIrMin,
			double jeffcoReferenceColdOverWarmIrMax,
			double currentModelOverJeffcoMin,
			double currentModelOverJeffcoMax
	) {
	}

	public record FpvLipoEsrAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceInventoryRowCount,
			int measuredIrAnchorRowCount,
			int currentPresetAbsoluteCheckRowCount,
			int currentVsMeasuredIrRowCount,
			int currentFormulaGuardrailRowCount,
			int socSohRuntimeProjectionRowCount,
			int temperatureModelCheckRowCount,
			int temperatureReferenceCheckRowCount,
			int cRateTemperatureShapeRowCount,
			int methodGuardrailRowCount,
			int summaryRowCount,
			MeasuredIrRange measuredRange,
			MeasuredIrAnchor lowestMeasuredAnchor,
			MeasuredIrAnchor medianMeasuredAnchor,
			MeasuredIrAnchor highestMeasuredAnchor,
			ConfiguredPackAudit configuredPack,
			IrFormulaGuardrail formulaGuardrail,
			SocSohProjection freshFullProjection,
			SocSohProjection wornTenPercentProjection,
			TemperatureModelPoint coldZeroCelsius,
			TemperatureModelPoint roomTwentyFiveCelsius,
			TemperatureModelPoint hotSeventyCelsius,
			TemperatureReferenceCheck temperatureReference
	) {
	}

	public static FpvLipoEsrAudit audit(DroneConfig config) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}

		ConfiguredPackAudit configured = configuredPackAudit(config);
		return new FpvLipoEsrAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_INVENTORY_ROW_COUNT,
				MEASURED_IR_ANCHOR_ROW_COUNT,
				CURRENT_PRESET_ABSOLUTE_CHECK_ROW_COUNT,
				CURRENT_VS_MEASURED_IR_ROW_COUNT,
				CURRENT_FORMULA_GUARDRAIL_ROW_COUNT,
				SOC_SOH_RUNTIME_PROJECTION_ROW_COUNT,
				TEMPERATURE_MODEL_CHECK_ROW_COUNT,
				TEMPERATURE_REFERENCE_CHECK_ROW_COUNT,
				C_RATE_TEMPERATURE_SHAPE_ROW_COUNT,
				METHOD_GUARDRAIL_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				new MeasuredIrRange(
						MEASURED_IR_ANCHOR_COUNT,
						MEASURED_PER_CELL_IR_MIN_MILLIOHMS,
						MEASURED_PER_CELL_IR_MEDIAN_MILLIOHMS,
						MEASURED_PER_CELL_IR_MAX_MILLIOHMS,
						MEASURED_PACK_IR_MIN_MILLIOHMS,
						MEASURED_PACK_IR_MAX_MILLIOHMS,
						MEASURED_SAG_AT_90A_MIN_VOLTS,
						MEASURED_SAG_AT_90A_MAX_VOLTS
				),
				ACEHE_4S_95C_1500,
				TATTU_4S_45C_1550,
				DRONELAB_CHAOS_4S_75C_1300,
				configured,
				formulaGuardrail(config, configured),
				socSohProjection(config, configured, "fresh_full", FRESH_FULL_SOC_RESISTANCE_SCALE),
				socSohProjection(config, configured, "worn_10pct", WORN_TEN_PERCENT_SOC_RESISTANCE_SCALE),
				temperatureModelPoint(config, configured, 0.0),
				temperatureModelPoint(config, configured, 25.0),
				temperatureModelPoint(config, configured, 70.0),
				new TemperatureReferenceCheck(
						JEFFCO_COLD_OVER_WARM_IR_MIN,
						JEFFCO_COLD_OVER_WARM_IR_MAX,
						CURRENT_MODEL_OVER_JEFFCO_MIN,
						CURRENT_MODEL_OVER_JEFFCO_MAX
				)
		);
	}

	private static final int MEASURED_IR_ANCHOR_COUNT = 5;
	private static final MeasuredIrAnchor DRONELAB_4S_50C_1500 = new MeasuredIrAnchor(
			"DroneLab 4S 50C 1500mAh",
			4,
			1.50,
			50.0,
			75.0,
			6.75,
			6.30,
			7.00,
			0.7000000000000002,
			3.9890109682477823,
			27.0,
			0.027,
			2.43
	);
	private static final MeasuredIrAnchor TATTU_4S_45C_1550 = new MeasuredIrAnchor(
			"Tattu 4S 45C 1550mAh",
			4,
			1.55,
			45.0,
			69.75,
			6.525,
			6.30,
			6.70,
			0.40000000000000036,
			2.2666972349040684,
			26.1,
			0.0261,
			2.349
	);
	private static final MeasuredIrAnchor ACEHE_4S_95C_1500 = new MeasuredIrAnchor(
			"Acehe Formula 4S 95C 1500mAh",
			4,
			1.50,
			95.0,
			142.5,
			4.975,
			4.80,
			5.10,
			0.2999999999999998,
			2.6111318707068474,
			19.9,
			0.019899999999999998,
			1.7909999999999997
	);
	private static final MeasuredIrAnchor DRONELAB_CHAOS_4S_75C_1300 = new MeasuredIrAnchor(
			"DroneLab Chaos 4S 75C 1300mAh",
			4,
			1.30,
			75.0,
			97.5,
			6.85,
			6.60,
			7.40,
			0.8000000000000007,
			4.786451477592705,
			27.4,
			0.027399999999999997,
			2.4659999999999997
	);

	@SuppressWarnings("unused")
	private static final MeasuredIrAnchor ACEHE_4S_95C_1300 = new MeasuredIrAnchor(
			"Acehe Formula 4S 95C 1300mAh",
			4,
			1.30,
			95.0,
			123.5,
			5.575,
			4.50,
			6.10,
			1.5999999999999996,
			11.300092527671884,
			22.3,
			0.0223,
			2.007
	);

	private static ConfiguredPackAudit configuredPackAudit(DroneConfig config) {
		int cells = inferCellCount(config);
		double currentLimitC = config.maxBatteryCurrentAmps() / Math.max(EPSILON, config.batteryCapacityAmpHours());
		double perCellIrMilliohms = config.batteryInternalResistanceOhms() * 1000.0 / Math.max(1, cells);
		double sagAtLimit = config.batteryInternalResistanceOhms() * config.maxBatteryCurrentAmps();
		double twentyPercentSagCurrent = currentForSagFraction(config, config.batteryInternalResistanceOhms(), TWENTY_PERCENT_SAG_FRACTION);
		return new ConfiguredPackAudit(
				cells,
				config.nominalBatteryVoltage(),
				config.batteryCapacityAmpHours(),
				config.maxBatteryCurrentAmps(),
				currentLimitC,
				config.batteryInternalResistanceOhms(),
				perCellIrMilliohms,
				sagAtLimit,
				ratio(sagAtLimit, config.nominalBatteryVoltage()) * 100.0,
				twentyPercentSagCurrent,
				ratio(config.maxBatteryCurrentAmps(), twentyPercentSagCurrent),
				ratio(perCellIrMilliohms, MEASURED_PER_CELL_IR_MIN_MILLIOHMS),
				ratio(perCellIrMilliohms, MEASURED_PER_CELL_IR_MEDIAN_MILLIOHMS),
				ratio(perCellIrMilliohms, MEASURED_PER_CELL_IR_MAX_MILLIOHMS)
		);
	}

	private static IrFormulaGuardrail formulaGuardrail(DroneConfig config, ConfiguredPackAudit configured) {
		double trueC = IR_FORMULA_CONSTANT / Math.sqrt(
				Math.max(EPSILON, config.batteryCapacityAmpHours() * 1000.0 * configured.perCellIrMilliohms())
		);
		double trueCurrent = trueC * config.batteryCapacityAmpHours();
		double coldTrueCurrent = trueCurrent / Math.sqrt(2.0);
		double sagAtTrueCurrent = trueCurrent * config.batteryInternalResistanceOhms();
		return new IrFormulaGuardrail(
				configured.perCellIrMilliohms(),
				configured.currentLimitC(),
				trueC,
				trueCurrent,
				ratio(config.maxBatteryCurrentAmps(), trueCurrent),
				sagAtTrueCurrent,
				configured.sagAtCurrentLimitVolts(),
				configured.sagAtCurrentLimitPercentNominal(),
				coldTrueCurrent,
				ratio(config.maxBatteryCurrentAmps(), coldTrueCurrent)
		);
	}

	private static SocSohProjection socSohProjection(
			DroneConfig config,
			ConfiguredPackAudit configured,
			String projectionId,
			double resistanceScale
	) {
		double resistance = config.batteryInternalResistanceOhms() * resistanceScale;
		double sagCurrent = currentForSagFraction(config, resistance, TWENTY_PERCENT_SAG_FRACTION);
		return new SocSohProjection(
				projectionId,
				resistanceScale,
				resistance,
				resistance * 1000.0 / Math.max(1, configured.cells()),
				resistance * config.maxBatteryCurrentAmps(),
				sagCurrent,
				ratio(config.maxBatteryCurrentAmps(), sagCurrent)
		);
	}

	private static TemperatureModelPoint temperatureModelPoint(
			DroneConfig config,
			ConfiguredPackAudit configured,
			double batteryTemperatureCelsius
	) {
		double resistanceScale = DronePhysics.batteryTemperatureResistanceScale(
				batteryTemperatureCelsius,
				AMBIENT_REFERENCE_CELSIUS
		);
		double currentScale = DronePhysics.temperatureAdjustedBatteryCurrentScale(batteryTemperatureCelsius);
		double thermalLimit = DronePhysics.batteryThermalLimit(batteryTemperatureCelsius);
		double effectiveResistance = config.batteryInternalResistanceOhms() * resistanceScale;
		double effectiveCurrentLimit = config.maxBatteryCurrentAmps() * currentScale;
		return new TemperatureModelPoint(
				batteryTemperatureCelsius,
				AMBIENT_REFERENCE_CELSIUS,
				resistanceScale,
				currentScale,
				thermalLimit,
				effectiveResistance,
				effectiveCurrentLimit,
				effectiveResistance * effectiveCurrentLimit
		);
	}

	private static int inferCellCount(DroneConfig config) {
		double fullCellEstimate = config.nominalBatteryVoltage() / 4.2;
		double nominalCellEstimate = config.nominalBatteryVoltage() / 3.7;
		int fullCells = Math.max(1, (int) Math.round(fullCellEstimate));
		int nominalCells = Math.max(1, (int) Math.round(nominalCellEstimate));
		double fullResidual = Math.abs(fullCellEstimate - fullCells);
		double nominalResidual = Math.abs(nominalCellEstimate - nominalCells);
		return fullResidual <= nominalResidual ? fullCells : nominalCells;
	}

	private static double currentForSagFraction(DroneConfig config, double resistanceOhms, double sagFraction) {
		double resistance = Math.max(EPSILON, resistanceOhms);
		return Math.max(0.0, config.nominalBatteryVoltage()) * Math.max(0.0, sagFraction) / resistance;
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
