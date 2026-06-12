package com.tenicana.dronecraft.sim;

public final class WindGustCalibration {
	private static final double EPSILON = 1.0e-12;
	private static final double TWO_PI = 2.0 * Math.PI;
	private static final double DEFAULT_ALTITUDE_METERS = 6.0;
	private static final double CURRENT_BURBLE_SCALE = 0.32;

	public static final String SOURCE_ID = "Wind-Gust-Dryden-Calibration-Packet";
	public static final String CAVEAT = "Keep atmospheric Dryden turbulence, deterministic dirty-air burble, and direct rotor gust CT response separate.";
	public static final int PACKET_METRIC_ROW_COUNT = 632;
	public static final int SOURCE_REFERENCE_COUNT = 4;
	public static final int CURRENT_WIND_SCAN_COUNT = 12;
	public static final int SPECTRAL_SHAPE_METRIC_ROW_COUNT = 240;
	public static final int ICAS_HOVER_GUST_ROW_COUNT = 12;

	private static final IcasHoverGustCtReference ICAS_4319_NEGATIVE_90 = new IcasHoverGustCtReference(
			"ICAS_2020_hover_gust_4319rpm_-90deg",
			"https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf",
			4319.0,
			10.0,
			-90.0,
			"-90deg",
			0.008221,
			-0.002579,
			-0.31370879455054135,
			-131.37087945505414
	);
	private static final IcasHoverGustCtReference ICAS_4319_POSITIVE_90 = new IcasHoverGustCtReference(
			"ICAS_2020_hover_gust_4319rpm_90deg",
			"https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf",
			4319.0,
			10.0,
			90.0,
			"90deg",
			0.008221,
			0.01810,
			2.2016786279041485,
			120.16786279041484
	);
	private static final IcasHoverGustCtReference ICAS_6528_NEGATIVE_90 = new IcasHoverGustCtReference(
			"ICAS_2020_hover_gust_6528rpm_-90deg",
			"https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf",
			6528.0,
			10.0,
			-90.0,
			"-90deg",
			0.008306,
			0.002636,
			0.31736094389597885,
			-68.26390561040212
	);
	private static final IcasHoverGustCtReference ICAS_6528_POSITIVE_90 = new IcasHoverGustCtReference(
			"ICAS_2020_hover_gust_6528rpm_90deg",
			"https://www.icas.org/icas_archive/ICAS2020/data/papers/ICAS2020_0482_paper.pdf",
			6528.0,
			10.0,
			90.0,
			"90deg",
			0.008306,
			0.01181,
			1.4218637129785698,
			42.18637129785699
	);

	private WindGustCalibration() {
	}

	public record WindScenarioAudit(
			String scenarioId,
			double dirtyAir,
			double windSpeedMetersPerSecond,
			double altitudeMeters,
			double currentGustScale,
			double currentBurbleScale,
			double drydenIntensityScale,
			double currentGustRmsXMetersPerSecond,
			double currentGustRmsYMetersPerSecond,
			double currentGustRmsZMetersPerSecond,
			double currentBurbleRmsXMetersPerSecond,
			double currentBurbleRmsYMetersPerSecond,
			double currentBurbleRmsZMetersPerSecond,
			double drydenTargetRmsXMetersPerSecond,
			double drydenTargetRmsYMetersPerSecond,
			double drydenTargetRmsZMetersPerSecond,
			double currentGustPeakXMetersPerSecond,
			double currentGustPeakYMetersPerSecond,
			double currentGustPeakZMetersPerSecond,
			double drydenSigmaUMetersPerSecond,
			double drydenSigmaWMetersPerSecond,
			double currentXRmsOverDrydenU,
			double currentYRmsOverDrydenW
	) {
	}

	public record SpectralShapeAudit(
			double phaseAPeriodSeconds,
			double phaseBPeriodSeconds,
			double phaseCPeriodSeconds,
			double gustTimeConstantSeconds,
			double meanWindTimeConstantSeconds,
			double drydenLongitudinalScaleMeters,
			double drydenVerticalScaleMeters,
			double drydenLongitudinalTimeSeconds,
			double drydenVerticalTimeSeconds,
			double currentGustCornerHertz,
			double currentMeanWindCornerHertz,
			double drydenLongitudinalPoleHertz,
			double drydenVerticalPoleHertz,
			double drydenVerticalZeroHertz,
			double currentCornerOverDrydenLongitudinalPole,
			double currentCornerOverDrydenVerticalPole,
			double currentShapeOverDrydenLongitudinalAtHalfHertz,
			double currentShapeOverDrydenVerticalAtHalfHertz,
			double currentShapeOverDrydenLongitudinalAtOneHertz,
			double currentShapeOverDrydenVerticalAtOneHertz
	) {
	}

	public record IcasHoverGustCtReference(
			String referenceId,
			String sourceUrl,
			double rpm,
			double gustSpeedMetersPerSecond,
			double gustDirectionDegrees,
			String gustDirectionLabel,
			double ctNoWind,
			double ctGust,
			double ctRatioGustOverNoWind,
			double ctChangePercent
	) {
	}

	public record WindGustAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int currentWindScanCount,
			int spectralShapeMetricRowCount,
			int icasHoverGustRowCount,
			WindScenarioAudit lightDirtyAir,
			SpectralShapeAudit lightDirtyAirSpectralShape,
			WindScenarioAudit representativeDirtyAir,
			SpectralShapeAudit representativeSpectralShape,
			WindScenarioAudit saturatedDirtyAir,
			SpectralShapeAudit saturatedSpectralShape,
			double currentXRmsOverDrydenUMin,
			double currentXRmsOverDrydenUMax,
			double currentYRmsOverDrydenWMin,
			double currentYRmsOverDrydenWMax,
			double currentCornerOverDrydenLongitudinalPoleMin,
			double currentCornerOverDrydenLongitudinalPoleMax,
			double currentShapeOverDrydenLongitudinalAtOneHertzMax,
			double currentShapeOverDrydenVerticalAtOneHertzMax,
			IcasHoverGustCtReference strongest4319Downdraft,
			IcasHoverGustCtReference strongest4319Updraft,
			IcasHoverGustCtReference strongest6528Downdraft,
			IcasHoverGustCtReference strongest6528Updraft
	) {
	}

	public static WindGustAudit audit() {
		WindScenarioAudit light = scenario(5.0, 0.25, DEFAULT_ALTITUDE_METERS);
		WindScenarioAudit representative = scenario(10.0, 1.50, DEFAULT_ALTITUDE_METERS);
		WindScenarioAudit saturated = scenario(15.0, 1.80, DEFAULT_ALTITUDE_METERS);
		Extrema extrema = scanExtrema();
		return new WindGustAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				CURRENT_WIND_SCAN_COUNT,
				SPECTRAL_SHAPE_METRIC_ROW_COUNT,
				ICAS_HOVER_GUST_ROW_COUNT,
				light,
				spectralShape(5.0, 0.25, DEFAULT_ALTITUDE_METERS),
				representative,
				spectralShape(10.0, 1.50, DEFAULT_ALTITUDE_METERS),
				saturated,
				spectralShape(15.0, 1.80, DEFAULT_ALTITUDE_METERS),
				extrema.minXRmsOverDrydenU(),
				extrema.maxXRmsOverDrydenU(),
				extrema.minYRmsOverDrydenW(),
				extrema.maxYRmsOverDrydenW(),
				extrema.minCornerOverDrydenLongitudinalPole(),
				extrema.maxCornerOverDrydenLongitudinalPole(),
				extrema.maxShapeOverDrydenLongitudinalAtOneHertz(),
				extrema.maxShapeOverDrydenVerticalAtOneHertz(),
				ICAS_4319_NEGATIVE_90,
				ICAS_4319_POSITIVE_90,
				ICAS_6528_NEGATIVE_90,
				ICAS_6528_POSITIVE_90
		);
	}

	private static WindScenarioAudit scenario(double windSpeedMetersPerSecond, double dirtyAir, double altitudeMeters) {
		double windSpeed = Math.max(0.0, windSpeedMetersPerSecond);
		double dirty = MathUtil.clamp(dirtyAir, 0.0, 1.8);
		DrydenTurbulenceModel.Parameters dryden =
				DrydenTurbulenceModel.lowAltitude(altitudeMeters, Math.max(0.1, windSpeed));
		double gustScale = MathUtil.clamp(dirty * (0.32 + 0.070 * windSpeed), 0.0, 4.5);
		double burbleXPeak = 1.60 * gustScale * CURRENT_BURBLE_SCALE;
		double burbleZPeak = 1.57 * gustScale * CURRENT_BURBLE_SCALE;
		double burbleYPeak = 0.50 * gustScale * CURRENT_BURBLE_SCALE;
		double burbleXRms = Math.sqrt((1.0 + 0.42 * 0.42 + 0.18 * 0.18) / 2.0)
				* gustScale
				* CURRENT_BURBLE_SCALE;
		double burbleZRms = Math.sqrt((1.0 + 0.35 * 0.35 + 0.22 * 0.22) / 2.0)
				* gustScale
				* CURRENT_BURBLE_SCALE;
		double burbleYRms = Math.sqrt((0.34 * 0.34 + 0.16 * 0.16) / 2.0)
				* gustScale
				* CURRENT_BURBLE_SCALE;
		double drydenIntensityScale = MathUtil.clamp(dirty / 1.8, 0.0, 1.0);
		double drydenXRms = dryden.longitudinalSigmaMetersPerSecond() * drydenIntensityScale;
		double drydenYRms = dryden.verticalSigmaMetersPerSecond() * drydenIntensityScale;
		double drydenZRms = dryden.lateralSigmaMetersPerSecond() * drydenIntensityScale;
		double xRms = Math.hypot(burbleXRms, drydenXRms);
		double yRms = Math.hypot(burbleYRms, drydenYRms);
		double zRms = Math.hypot(burbleZRms, drydenZRms);
		return new WindScenarioAudit(
				scenarioId(windSpeed, dirty, altitudeMeters),
				dirty,
				windSpeed,
				altitudeMeters,
				gustScale,
				CURRENT_BURBLE_SCALE,
				drydenIntensityScale,
				xRms,
				yRms,
				zRms,
				burbleXRms,
				burbleYRms,
				burbleZRms,
				drydenXRms,
				drydenYRms,
				drydenZRms,
				burbleXPeak + 2.0 * drydenXRms,
				burbleYPeak + 2.0 * drydenYRms,
				burbleZPeak + 2.0 * drydenZRms,
				dryden.longitudinalSigmaMetersPerSecond(),
				dryden.verticalSigmaMetersPerSecond(),
				ratio(xRms, dryden.longitudinalSigmaMetersPerSecond()),
				ratio(yRms, dryden.verticalSigmaMetersPerSecond())
		);
	}

	private static SpectralShapeAudit spectralShape(double windSpeedMetersPerSecond, double dirtyAir, double altitudeMeters) {
		double windSpeed = Math.max(0.0, windSpeedMetersPerSecond);
		double dirty = MathUtil.clamp(dirtyAir, 0.0, 1.8);
		DrydenTurbulenceModel.Parameters dryden =
				DrydenTurbulenceModel.lowAltitude(altitudeMeters, Math.max(0.1, windSpeed));
		double phaseA = 1.35 + 0.16 * windSpeed + 1.25 * dirty;
		double phaseB = 1.95 + 0.11 * windSpeed + 0.95 * dirty;
		double phaseC = 0.85 + 0.09 * windSpeed + 1.55 * dirty;
		double gustTau = MathUtil.clamp(0.070 + 0.085 / (0.35 + dirty), 0.055, 0.260);
		double meanTau = MathUtil.clamp(0.055 + 0.018 * windSpeed + 0.140 * dirty, 0.045, 0.620);
		double longitudinalTime = dryden.longitudinalScaleMeters() / Math.max(0.1, windSpeed);
		double verticalTime = dryden.verticalScaleMeters() / Math.max(0.1, windSpeed);
		double gustCorner = poleHertz(gustTau);
		double meanCorner = poleHertz(meanTau);
		double drydenLongitudinalPole = poleHertz(longitudinalTime);
		double drydenVerticalPole = poleHertz(verticalTime);
		double drydenVerticalZero = poleHertz(Math.sqrt(3.0) * verticalTime);
		double currentShapeHalfHertz = firstOrderShapeMagnitude(gustTau, 0.5);
		double currentShapeOneHertz = firstOrderShapeMagnitude(gustTau, 1.0);
		double drydenLongitudinalHalfHertz = firstOrderShapeMagnitude(longitudinalTime, 0.5);
		double drydenLongitudinalOneHertz = firstOrderShapeMagnitude(longitudinalTime, 1.0);
		double drydenVerticalHalfHertz = pyflyTransverseShapeMagnitude(verticalTime, 0.5);
		double drydenVerticalOneHertz = pyflyTransverseShapeMagnitude(verticalTime, 1.0);
		return new SpectralShapeAudit(
				TWO_PI / phaseA,
				TWO_PI / phaseB,
				TWO_PI / phaseC,
				gustTau,
				meanTau,
				dryden.longitudinalScaleMeters(),
				dryden.verticalScaleMeters(),
				longitudinalTime,
				verticalTime,
				gustCorner,
				meanCorner,
				drydenLongitudinalPole,
				drydenVerticalPole,
				drydenVerticalZero,
				ratio(gustCorner, drydenLongitudinalPole),
				ratio(gustCorner, drydenVerticalPole),
				ratio(currentShapeHalfHertz, drydenLongitudinalHalfHertz),
				ratio(currentShapeHalfHertz, drydenVerticalHalfHertz),
				ratio(currentShapeOneHertz, drydenLongitudinalOneHertz),
				ratio(currentShapeOneHertz, drydenVerticalOneHertz)
		);
	}

	private static Extrema scanExtrema() {
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double minCorner = Double.POSITIVE_INFINITY;
		double maxCorner = Double.NEGATIVE_INFINITY;
		double maxShapeU = Double.NEGATIVE_INFINITY;
		double maxShapeW = Double.NEGATIVE_INFINITY;
		for (double windSpeed : new double[] { 5.0, 10.0, 15.0 }) {
			for (double dirtyAir : new double[] { 0.25, 0.75, 1.50, 1.80 }) {
				WindScenarioAudit scenario = scenario(windSpeed, dirtyAir, DEFAULT_ALTITUDE_METERS);
				SpectralShapeAudit spectral = spectralShape(windSpeed, dirtyAir, DEFAULT_ALTITUDE_METERS);
				minX = Math.min(minX, scenario.currentXRmsOverDrydenU());
				maxX = Math.max(maxX, scenario.currentXRmsOverDrydenU());
				minY = Math.min(minY, scenario.currentYRmsOverDrydenW());
				maxY = Math.max(maxY, scenario.currentYRmsOverDrydenW());
				minCorner = Math.min(minCorner, spectral.currentCornerOverDrydenLongitudinalPole());
				maxCorner = Math.max(maxCorner, spectral.currentCornerOverDrydenLongitudinalPole());
				maxShapeU = Math.max(maxShapeU, spectral.currentShapeOverDrydenLongitudinalAtOneHertz());
				maxShapeW = Math.max(maxShapeW, spectral.currentShapeOverDrydenVerticalAtOneHertz());
			}
		}
		return new Extrema(minX, maxX, minY, maxY, minCorner, maxCorner, maxShapeU, maxShapeW);
	}

	private static String scenarioId(double windSpeedMetersPerSecond, double dirtyAir, double altitudeMeters) {
		return "wind_" + formatToken(windSpeedMetersPerSecond)
				+ "m_s_dirty_" + formatToken(dirtyAir)
				+ "_alt_" + formatToken(altitudeMeters)
				+ "m";
	}

	private static String formatToken(double value) {
		String text = String.format(java.util.Locale.ROOT, "%.3f", value);
		while (text.contains(".") && text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		if (text.endsWith(".")) {
			text = text.substring(0, text.length() - 1);
		}
		return text.replace('.', 'p');
	}

	private static double poleHertz(double timeConstantSeconds) {
		if (!Double.isFinite(timeConstantSeconds) || timeConstantSeconds <= 0.0) {
			return 0.0;
		}
		return 1.0 / (TWO_PI * timeConstantSeconds);
	}

	private static double firstOrderShapeMagnitude(double timeConstantSeconds, double frequencyHertz) {
		double omegaTau = TWO_PI * Math.max(0.0, frequencyHertz) * Math.max(EPSILON, timeConstantSeconds);
		return 1.0 / Math.sqrt(1.0 + omegaTau * omegaTau);
	}

	private static double pyflyTransverseShapeMagnitude(double timeConstantSeconds, double frequencyHertz) {
		double omega = TWO_PI * Math.max(0.0, frequencyHertz);
		double timeConstant = Math.max(EPSILON, timeConstantSeconds);
		double zeroTime = Math.sqrt(3.0) * timeConstant;
		double numerator = Math.sqrt(1.0 + Math.pow(omega * zeroTime, 2.0));
		double denominator = 1.0 + Math.pow(omega * timeConstant, 2.0);
		return numerator / denominator;
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private record Extrema(
			double minXRmsOverDrydenU,
			double maxXRmsOverDrydenU,
			double minYRmsOverDrydenW,
			double maxYRmsOverDrydenW,
			double minCornerOverDrydenLongitudinalPole,
			double maxCornerOverDrydenLongitudinalPole,
			double maxShapeOverDrydenLongitudinalAtOneHertz,
			double maxShapeOverDrydenVerticalAtOneHertz
	) {
	}
}
