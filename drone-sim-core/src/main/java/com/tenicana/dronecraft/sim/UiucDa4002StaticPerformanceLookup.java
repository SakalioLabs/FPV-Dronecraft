package com.tenicana.dronecraft.sim;

import java.util.List;

import com.tenicana.dronecraft.sim.RotorHoverBladeProfilePowerModel.BladeGeometry;

/**
 * Static CT/CP lookup for the measured UIUC DA4002 Volume 2 propellers.
 * The table is propeller-specific evidence at J=0; it is not a generic
 * Reynolds correction or a forward-flight polar.
 */
public final class UiucDa4002StaticPerformanceLookup {
	public static final String DATA_SOURCE_ID = "uiuc-propdb-volume-2-da4002-static";
	private static final double EPSILON = 1.0e-12;

	private static final StaticCurve FIVE_BY_THREE_POINT_SEVEN_FIVE = new StaticCurve(
			"uiuc-da4002-5x3.75-static",
			"UIUC DA4002 5x3.75 static",
			UiucDa4002PropellerGeometry.FIVE_INCH_STATIC_SOURCE_URL,
			UiucDa4002PropellerGeometry.fiveByThreePointSevenFiveTwoBlade(),
			UiucDa4002PropellerGeometry.FIVE_INCH_DIAMETER_METERS,
			List.of(
					row(1_410.000, 0.117996, 0.097429),
					row(1_976.667, 0.124035, 0.095690),
					row(2_446.667, 0.123977, 0.095134),
					row(3_060.000, 0.122526, 0.093445),
					row(3_550.000, 0.123508, 0.091795),
					row(3_986.667, 0.125492, 0.091880),
					row(4_513.333, 0.125092, 0.089659),
					row(4_966.667, 0.124781, 0.090452),
					row(5_533.333, 0.122616, 0.087334),
					row(5_930.000, 0.125383, 0.088644),
					row(6_483.333, 0.128062, 0.091539),
					row(6_913.333, 0.129589, 0.089348),
					row(7_440.000, 0.129020, 0.092551)
			)
	);

	private static final StaticCurve NINE_BY_SIX_POINT_SEVEN_FIVE = new StaticCurve(
			"uiuc-da4002-9x6.75-static",
			"UIUC DA4002 9x6.75 static",
			UiucDa4002PropellerGeometry.NINE_INCH_STATIC_SOURCE_URL,
			UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
			UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS,
			List.of(
					row(1_546.667, 0.125931, 0.088432),
					row(1_700.000, 0.127314, 0.088785),
					row(2_053.333, 0.129033, 0.087836),
					row(2_253.333, 0.127408, 0.085943),
					row(2_503.333, 0.131518, 0.087917),
					row(2_746.667, 0.130653, 0.086259),
					row(2_946.667, 0.132682, 0.086557),
					row(3_280.000, 0.130863, 0.083443),
					row(3_526.667, 0.132955, 0.083443),
					row(3_743.333, 0.134311, 0.083174),
					row(3_940.000, 0.134936, 0.082488),
					row(4_213.333, 0.136319, 0.079168),
					row(4_443.333, 0.135364, 0.078000),
					row(4_676.667, 0.138118, 0.079092),
					row(4_930.000, 0.138094, 0.078738),
					row(5_186.667, 0.138747, 0.079026),
					row(5_430.000, 0.139023, 0.079163),
					row(5_710.000, 0.139617, 0.079595),
					row(5_943.333, 0.140450, 0.080081)
			)
	);

	private static final List<StaticCurve> CURVES = List.of(
			FIVE_BY_THREE_POINT_SEVEN_FIVE,
			NINE_BY_SIX_POINT_SEVEN_FIVE
	);

	private UiucDa4002StaticPerformanceLookup() {
	}

	public enum EnvelopePolicy {
		BLOCK_OUT_OF_ENVELOPE,
		CLAMP_TO_ENVELOPE
	}

	public enum InterpolationStatus {
		EXACT,
		LINEAR_RPM,
		BLOCKED
	}

	public static StaticCurve fiveByThreePointSevenFive() {
		return FIVE_BY_THREE_POINT_SEVEN_FIVE;
	}

	public static StaticCurve nineBySixPointSevenFive() {
		return NINE_BY_SIX_POINT_SEVEN_FIVE;
	}

	public static List<StaticCurve> curves() {
		return CURVES;
	}

	public static LookupResult evaluate(
			StaticCurve curve,
			double rpm,
			EnvelopePolicy envelopePolicy
	) {
		validateCurveAndRpm(curve, rpm);
		if (envelopePolicy == null) {
			envelopePolicy = EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE;
		}
		boolean outOfEnvelope = rpm < curve.minimumRpm() || rpm > curve.maximumRpm();
		if (outOfEnvelope && envelopePolicy == EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE) {
			return new LookupResult(
					curve,
					rpm,
					rpm,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					InterpolationStatus.BLOCKED,
					true,
					false,
					true,
					"query-outside-uiuc-static-rpm-envelope"
			);
		}

		double effectiveRpm = MathUtil.clamp(rpm, curve.minimumRpm(), curve.maximumRpm());
		for (StaticRow candidate : curve.rows()) {
			if (Double.compare(effectiveRpm, candidate.rpm()) == 0) {
				return new LookupResult(
						curve,
						rpm,
						effectiveRpm,
						candidate.rpm(),
						candidate.rpm(),
						0.0,
						candidate.thrustCoefficientCt(),
						candidate.powerCoefficientCp(),
						InterpolationStatus.EXACT,
						outOfEnvelope,
						outOfEnvelope,
						false,
						outOfEnvelope
								? "query-clamped-to-uiuc-static-rpm-envelope"
								: "uiuc-static-row-exact"
				);
			}
		}

		for (int rowIndex = 1; rowIndex < curve.rows().size(); rowIndex++) {
			StaticRow upper = curve.rows().get(rowIndex);
			if (effectiveRpm >= upper.rpm()) {
				continue;
			}
			StaticRow lower = curve.rows().get(rowIndex - 1);
			double fraction = (effectiveRpm - lower.rpm()) / (upper.rpm() - lower.rpm());
			return new LookupResult(
					curve,
					rpm,
					effectiveRpm,
					lower.rpm(),
					upper.rpm(),
					fraction,
					lerp(lower.thrustCoefficientCt(), upper.thrustCoefficientCt(), fraction),
					lerp(lower.powerCoefficientCp(), upper.powerCoefficientCp(), fraction),
					InterpolationStatus.LINEAR_RPM,
					false,
					false,
					false,
					"uiuc-static-row-linearly-interpolated"
			);
		}
		throw new IllegalStateException("validated UIUC static curve does not bracket RPM query.");
	}

	public static DimensionalSample sample(
			StaticCurve curve,
			double rpm,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			EnvelopePolicy envelopePolicy
	) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(dynamicViscosityPascalSeconds)
				|| dynamicViscosityPascalSeconds <= 0.0) {
			throw new IllegalArgumentException(
					"dynamicViscosityPascalSeconds must be finite and positive."
			);
		}
		LookupResult lookup = evaluate(curve, rpm, envelopePolicy);
		double diameter = curve.referenceDiameterMeters();
		double radius = diameter * 0.5;
		double diskArea = Math.PI * radius * radius;
		double kinematicRpm = lookup.blocked() ? rpm : lookup.effectiveRpm();
		double revolutionsPerSecond = kinematicRpm / 60.0;
		double angularVelocity = revolutionsPerSecond * 2.0 * Math.PI;
		double chordAtSeventyFivePercentRadius = curve.geometry().chordToRadiusAt(0.75) * radius;
		double speedAtSeventyFivePercentRadius = 0.75 * angularVelocity * radius;
		double reynoldsAtSeventyFivePercentRadius = airDensityKgPerCubicMeter
				* speedAtSeventyFivePercentRadius
				* chordAtSeventyFivePercentRadius
				/ dynamicViscosityPascalSeconds;
		if (lookup.blocked()) {
			return new DimensionalSample(
					lookup,
					airDensityKgPerCubicMeter,
					dynamicViscosityPascalSeconds,
					diameter,
					radius,
					revolutionsPerSecond,
					angularVelocity,
					chordAtSeventyFivePercentRadius,
					speedAtSeventyFivePercentRadius,
					reynoldsAtSeventyFivePercentRadius,
					0.0,
					0.0,
					0.0,
					0.0,
					diskArea,
					0.0,
					0.0,
					0.0,
					0.0
			);
		}

		double thrust = lookup.thrustCoefficientCt()
				* airDensityKgPerCubicMeter
				* revolutionsPerSecond
				* revolutionsPerSecond
				* Math.pow(diameter, 4.0);
		double shaftPower = lookup.powerCoefficientCp()
				* airDensityKgPerCubicMeter
				* Math.pow(revolutionsPerSecond, 3.0)
				* Math.pow(diameter, 5.0);
		double shaftTorque = shaftPower / angularVelocity;
		double torqueCoefficient = lookup.powerCoefficientCp() / (2.0 * Math.PI);
		double diskLoading = thrust / diskArea;
		double idealInducedVelocity = Math.sqrt(
				thrust / (2.0 * airDensityKgPerCubicMeter * diskArea)
		);
		double idealInducedPower = thrust * idealInducedVelocity;
		return new DimensionalSample(
				lookup,
				airDensityKgPerCubicMeter,
				dynamicViscosityPascalSeconds,
				diameter,
				radius,
				revolutionsPerSecond,
				angularVelocity,
				chordAtSeventyFivePercentRadius,
				speedAtSeventyFivePercentRadius,
				reynoldsAtSeventyFivePercentRadius,
				thrust,
				shaftPower,
				shaftTorque,
				torqueCoefficient,
				diskArea,
				diskLoading,
				idealInducedVelocity,
				idealInducedPower,
				ratio(idealInducedPower, shaftPower)
		);
	}

	private static void validateCurveAndRpm(StaticCurve curve, double rpm) {
		if (curve == null) {
			throw new IllegalArgumentException("curve must not be null.");
		}
		if (!Double.isFinite(rpm) || rpm <= 0.0) {
			throw new IllegalArgumentException("rpm must be finite and positive.");
		}
	}

	private static StaticRow row(double rpm, double thrustCoefficientCt, double powerCoefficientCp) {
		return new StaticRow(rpm, thrustCoefficientCt, powerCoefficientCp);
	}

	private static double lerp(double lower, double upper, double fraction) {
		return lower + (upper - lower) * fraction;
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator)
				|| !Double.isFinite(denominator)
				|| Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		double value = numerator / denominator;
		return Double.isFinite(value) ? value : 0.0;
	}

	public record StaticRow(
			double rpm,
			double thrustCoefficientCt,
			double powerCoefficientCp
	) {
		public StaticRow {
			if (!Double.isFinite(rpm) || rpm <= 0.0) {
				throw new IllegalArgumentException("static row RPM must be finite and positive.");
			}
			if (!Double.isFinite(thrustCoefficientCt) || thrustCoefficientCt <= 0.0) {
				throw new IllegalArgumentException("static row CT must be finite and positive.");
			}
			if (!Double.isFinite(powerCoefficientCp) || powerCoefficientCp <= 0.0) {
				throw new IllegalArgumentException("static row CP must be finite and positive.");
			}
		}
	}

	public record StaticCurve(
			String id,
			String displayName,
			String sourceUrl,
			BladeGeometry geometry,
			double referenceDiameterMeters,
			List<StaticRow> rows
	) {
		public StaticCurve {
			id = id == null ? "" : id.trim();
			displayName = displayName == null ? "" : displayName.trim();
			sourceUrl = sourceUrl == null ? "" : sourceUrl.trim();
			if (id.isEmpty() || displayName.isEmpty() || sourceUrl.isEmpty()) {
				throw new IllegalArgumentException("curve id, displayName, and sourceUrl must not be blank.");
			}
			if (geometry == null) {
				throw new IllegalArgumentException("curve geometry must not be null.");
			}
			if (!Double.isFinite(referenceDiameterMeters) || referenceDiameterMeters <= 0.0) {
				throw new IllegalArgumentException("referenceDiameterMeters must be finite and positive.");
			}
			rows = List.copyOf(rows == null ? List.of() : rows);
			if (rows.size() < 2) {
				throw new IllegalArgumentException("static curve requires at least two rows.");
			}
			double previousRpm = 0.0;
			for (StaticRow row : rows) {
				if (row == null || row.rpm() <= previousRpm) {
					throw new IllegalArgumentException(
							"static curve rows must be non-null and strictly increasing in RPM."
					);
				}
				previousRpm = row.rpm();
			}
		}

		public double minimumRpm() {
			return rows.get(0).rpm();
		}

		public double maximumRpm() {
			return rows.get(rows.size() - 1).rpm();
		}
	}

	public record LookupResult(
			StaticCurve curve,
			double queryRpm,
			double effectiveRpm,
			double lowerRpm,
			double upperRpm,
			double interpolationFraction,
			double thrustCoefficientCt,
			double powerCoefficientCp,
			InterpolationStatus interpolationStatus,
			boolean outOfEnvelope,
			boolean clamped,
			boolean blocked,
			String message
	) {
		public boolean accepted() {
			return !blocked;
		}
	}

	public record DimensionalSample(
			LookupResult lookup,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			double propellerDiameterMeters,
			double rotorRadiusMeters,
			double revolutionsPerSecond,
			double angularVelocityRadiansPerSecond,
			double chordAtSeventyFivePercentRadiusMeters,
			double speedAtSeventyFivePercentRadiusMetersPerSecond,
			double reynoldsNumberAtSeventyFivePercentRadius,
			double thrustNewtons,
			double shaftPowerWatts,
			double shaftTorqueNewtonMeters,
			double torqueCoefficientCq,
			double diskAreaSquareMeters,
			double diskLoadingNewtonsPerSquareMeter,
			double idealInducedVelocityMetersPerSecond,
			double idealInducedPowerWatts,
			double hoverFigureOfMerit
	) {
		public boolean blocked() {
			return lookup.blocked();
		}

		public boolean clamped() {
			return lookup.clamped();
		}
	}
}
