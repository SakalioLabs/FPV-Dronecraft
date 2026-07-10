package com.tenicana.dronecraft.sim;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative SDA1075 section-polar lookup generated offline with XFOIL 6.99.
 * The airfoil coordinates are Appendix A of Deters, Ananda, and Selig,
 * AIAA 2014-2151. XFOIL was run at Mach 0, Ncrit 9, with 250 iterations.
 * The reviewed 40k-100k anchors are retained unchanged. Path-independent
 * ascending/descending sweep anchors extend the positive-alpha envelope down
 * to Re 10k; unstable 25k/30k sweep branches are intentionally not anchors.
 * This table is a computational section reference, not wind-tunnel evidence.
 */
public final class Sda1075XfoilSectionPolar {
	public static final String DATA_SOURCE_ID =
			"sda1075-xfoil-6.99-ncrit9-reviewed-and-path-independent-low-re";
	public static final String AIRFOIL_SOURCE_URL =
			"https://m-selig.ae.illinois.edu/pubs/DetersAnandaSelig-2014-AIAA-2014-2151.pdf";
	public static final String XFOIL_SOURCE_URL = "https://web.mit.edu/drela/Public/web/xfoil/";
	public static final double MIN_REYNOLDS_NUMBER = 10_000.0;
	public static final double MAX_REYNOLDS_NUMBER = 100_000.0;
	public static final double MIN_ANGLE_OF_ATTACK_DEGREES = -5.0;
	public static final double LOW_RE_MIN_ANGLE_OF_ATTACK_DEGREES = 0.0;
	public static final double MAX_ANGLE_OF_ATTACK_DEGREES = 12.0;
	public static final double REVIEWED_MIN_REYNOLDS_NUMBER = 40_000.0;

	private static final String LOW_RE_RESOURCE_ROOT =
			"/com/tenicana/dronecraft/sim/aero/xfoil-6.99-ncrit9/";
	private static final double SWEEP_AGREEMENT_TOLERANCE = 1.0e-12;
	private static final Pattern XFOIL_SETTINGS_PATTERN = Pattern.compile(
			"Mach\\s*=\\s*([0-9.+\\-Ee]+)\\s+Re\\s*=\\s*([0-9.+\\-Ee]+)"
					+ "\\s+e\\s*6\\s+Ncrit\\s*=\\s*([0-9.+\\-Ee]+)"
	);

	private static final double[] REYNOLDS_AXIS = { 40_000.0, 60_000.0, 100_000.0 };
	private static final double[] LOW_REYNOLDS_AXIS = {
			10_000.0, 15_000.0, 20_000.0, 40_000.0
	};
	private static final double[] ANGLE_AXIS_DEGREES = {
			-5.0, -4.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0,
			4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0
	};
	private static final double[] LOW_RE_ANGLE_AXIS_DEGREES = {
			0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0,
			7.0, 8.0, 9.0, 10.0, 11.0, 12.0
	};
	private static final double[][] LIFT_COEFFICIENT = {
			{
					-0.4285, -0.3593, -0.3126, -0.1261, -0.0932, -0.0322,
					0.1939, 0.3678, 0.5075, 0.6030, 0.6896, 0.7579,
					0.8081, 0.8167, 0.8121, 0.7456, 0.7224, 0.7412
			},
			{
					-0.4188, -0.3492, -0.2859, -0.1449, 0.0091, 0.2240,
					0.3628, 0.4627, 0.5595, 0.6559, 0.7543, 0.8536,
					0.9537, 1.0451, 1.1342, 1.2080, 1.2568, 1.2558
			},
			{
					-0.4170, -0.3502, -0.2309, -0.0451, 0.1625, 0.2470,
					0.3389, 0.4383, 0.5419, 0.6475, 0.7535, 0.8587,
					0.9619, 1.0594, 1.1483, 1.2169, 1.2474, 1.2452
			}
	};
	private static final double[][] DRAG_COEFFICIENT = {
			{
					0.03065, 0.02556, 0.02324, 0.02237, 0.02382, 0.02661,
					0.03071, 0.03390, 0.03688, 0.04094, 0.04565, 0.05209,
					0.06073, 0.07395, 0.08975, 0.11619, 0.13739, 0.15082
			},
			{
					0.02487, 0.02080, 0.01931, 0.01962, 0.02084, 0.02139,
					0.02208, 0.02329, 0.02452, 0.02613, 0.02782, 0.02964,
					0.03152, 0.03436, 0.03692, 0.04036, 0.04547, 0.05602
			},
			{
					0.01994, 0.01702, 0.01570, 0.01477, 0.01447, 0.01466,
					0.01517, 0.01581, 0.01655, 0.01745, 0.01857, 0.01994,
					0.02157, 0.02355, 0.02588, 0.02907, 0.03491, 0.04361
			}
	};
	private static final double[][] PITCHING_MOMENT_COEFFICIENT = {
			{
					-0.0276, -0.0175, 0.0011, -0.0191, -0.0141, -0.0137,
					-0.0419, -0.0568, -0.0622, -0.0603, -0.0569, -0.0529,
					-0.0491, -0.0463, -0.0457, -0.0554, -0.0637, -0.0639
			},
			{
					-0.0276, -0.0206, -0.0110, -0.0171, -0.0335, -0.0557,
					-0.0598, -0.0557, -0.0507, -0.0464, -0.0425, -0.0388,
					-0.0351, -0.0312, -0.0265, -0.0206, -0.0130, -0.0033
			},
			{
					-0.0256, -0.0200, -0.0239, -0.0345, -0.0541, -0.0481,
					-0.0431, -0.0397, -0.0373, -0.0355, -0.0341, -0.0327,
					-0.0312, -0.0292, -0.0263, -0.0213, -0.0128, -0.0028
			}
	};
	private static final XfoilPolar[] PATH_INDEPENDENT_LOW_RE_POLARS = {
			loadPathIndependentLowRePolar(10_000),
			loadPathIndependentLowRePolar(15_000),
			loadPathIndependentLowRePolar(20_000)
	};
	private static final double[][] LOW_RE_LIFT_COEFFICIENT =
			extendedLowReTable(Coefficient.LIFT);
	private static final double[][] LOW_RE_DRAG_COEFFICIENT =
			extendedLowReTable(Coefficient.DRAG);
	private static final double[][] LOW_RE_PITCHING_MOMENT_COEFFICIENT =
			extendedLowReTable(Coefficient.PITCHING_MOMENT);

	private Sda1075XfoilSectionPolar() {
	}

	public enum EnvelopePolicy {
		BLOCK_OUT_OF_ENVELOPE,
		CLAMP_TO_ENVELOPE
	}

	public enum InterpolationStatus {
		EXACT,
		LINEAR_ANGLE_OF_ATTACK,
		LINEAR_REYNOLDS,
		BILINEAR,
		BLOCKED
	}

	public static PolarSample evaluateRadians(
			double reynoldsNumber,
			double angleOfAttackRadians,
			EnvelopePolicy envelopePolicy
	) {
		if (!Double.isFinite(angleOfAttackRadians)) {
			throw new IllegalArgumentException("angleOfAttackRadians must be finite.");
		}
		return evaluateDegrees(reynoldsNumber, Math.toDegrees(angleOfAttackRadians), envelopePolicy);
	}

	public static PolarSample evaluateDegrees(
			double reynoldsNumber,
			double angleOfAttackDegrees,
			EnvelopePolicy envelopePolicy
	) {
		if (!Double.isFinite(reynoldsNumber) || reynoldsNumber <= 0.0) {
			throw new IllegalArgumentException("reynoldsNumber must be finite and positive.");
		}
		if (!Double.isFinite(angleOfAttackDegrees)) {
			throw new IllegalArgumentException("angleOfAttackDegrees must be finite.");
		}
		if (envelopePolicy == null) {
			envelopePolicy = EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE;
		}

		boolean reynoldsOutOfEnvelope = reynoldsNumber < MIN_REYNOLDS_NUMBER
				|| reynoldsNumber > MAX_REYNOLDS_NUMBER;
		double effectiveReynolds = MathUtil.clamp(
				reynoldsNumber,
				MIN_REYNOLDS_NUMBER,
				MAX_REYNOLDS_NUMBER
		);
		double minimumSupportedAngle = effectiveReynolds < REVIEWED_MIN_REYNOLDS_NUMBER
				? LOW_RE_MIN_ANGLE_OF_ATTACK_DEGREES
				: MIN_ANGLE_OF_ATTACK_DEGREES;
		boolean angleOutOfEnvelope = angleOfAttackDegrees < minimumSupportedAngle
				|| angleOfAttackDegrees > MAX_ANGLE_OF_ATTACK_DEGREES;
		if ((reynoldsOutOfEnvelope || angleOutOfEnvelope)
				&& envelopePolicy == EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE) {
			return new PolarSample(
					DATA_SOURCE_ID,
					reynoldsNumber,
					angleOfAttackDegrees,
					reynoldsNumber,
					angleOfAttackDegrees,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					InterpolationStatus.BLOCKED,
					reynoldsOutOfEnvelope,
					angleOutOfEnvelope,
					false,
					false,
					true,
					false,
					false,
					"query-outside-supported-xfoil-envelope"
			);
		}

		double effectiveAngle = MathUtil.clamp(
				angleOfAttackDegrees,
				minimumSupportedAngle,
				MAX_ANGLE_OF_ATTACK_DEGREES
		);
		boolean lowReynoldsExtensionUsed = effectiveReynolds < REVIEWED_MIN_REYNOLDS_NUMBER;
		double[] reynoldsAxis = lowReynoldsExtensionUsed ? LOW_REYNOLDS_AXIS : REYNOLDS_AXIS;
		double[] angleAxis = lowReynoldsExtensionUsed
				? LOW_RE_ANGLE_AXIS_DEGREES
				: ANGLE_AXIS_DEGREES;
		double[][] liftTable = lowReynoldsExtensionUsed
				? LOW_RE_LIFT_COEFFICIENT
				: LIFT_COEFFICIENT;
		double[][] dragTable = lowReynoldsExtensionUsed
				? LOW_RE_DRAG_COEFFICIENT
				: DRAG_COEFFICIENT;
		double[][] pitchingMomentTable = lowReynoldsExtensionUsed
				? LOW_RE_PITCHING_MOMENT_COEFFICIENT
				: PITCHING_MOMENT_COEFFICIENT;
		AxisBracket reynoldsBracket = bracket(reynoldsAxis, effectiveReynolds);
		AxisBracket angleBracket = bracket(angleAxis, effectiveAngle);
		double liftCoefficient = bilinear(
				liftTable,
				reynoldsBracket,
				angleBracket
		);
		double dragCoefficient = bilinear(
				dragTable,
				reynoldsBracket,
				angleBracket
		);
		double pitchingMomentCoefficient = bilinear(
				pitchingMomentTable,
				reynoldsBracket,
				angleBracket
		);
		boolean reynoldsClamped = reynoldsOutOfEnvelope;
		boolean angleClamped = angleOutOfEnvelope;
		return new PolarSample(
				DATA_SOURCE_ID,
				reynoldsNumber,
				angleOfAttackDegrees,
				effectiveReynolds,
				effectiveAngle,
				reynoldsBracket.lowerValue(),
				reynoldsBracket.upperValue(),
				angleBracket.lowerValue(),
				angleBracket.upperValue(),
				reynoldsBracket.fraction(),
				angleBracket.fraction(),
				liftCoefficient,
				dragCoefficient,
				pitchingMomentCoefficient,
				interpolationStatus(reynoldsBracket, angleBracket),
				reynoldsOutOfEnvelope,
				angleOutOfEnvelope,
				reynoldsClamped,
				angleClamped,
				false,
				lowReynoldsExtensionUsed,
				lowReynoldsExtensionUsed,
				reynoldsClamped || angleClamped
						? "query-clamped-to-supported-xfoil-envelope"
						: lowReynoldsExtensionUsed
								? "sda1075-path-independent-low-re-polar-interpolated"
								: "sda1075-reviewed-polar-interpolated"
		);
	}

	private static double[][] extendedLowReTable(Coefficient coefficient) {
		double[][] table = new double[LOW_REYNOLDS_AXIS.length][LOW_RE_ANGLE_AXIS_DEGREES.length];
		for (int reynoldsIndex = 0;
				reynoldsIndex < PATH_INDEPENDENT_LOW_RE_POLARS.length;
				reynoldsIndex++) {
			double[] source = coefficient.values(PATH_INDEPENDENT_LOW_RE_POLARS[reynoldsIndex]);
			System.arraycopy(source, 0, table[reynoldsIndex], 0, source.length);
		}
		double[][] reviewedTable = coefficient.reviewedTable();
		int zeroAngleIndex = 5;
		System.arraycopy(
				reviewedTable[0],
				zeroAngleIndex,
				table[table.length - 1],
				0,
				LOW_RE_ANGLE_AXIS_DEGREES.length
		);
		return table;
	}

	private static XfoilPolar loadPathIndependentLowRePolar(int reynoldsNumber) {
		String stem = "sda1075-re" + reynoldsNumber;
		XfoilPolar ascending = loadXfoilPolar(stem + "-alpha-up.pol", reynoldsNumber);
		XfoilPolar descending = loadXfoilPolar(stem + "-alpha-down.pol", reynoldsNumber);
		verifySweepAgreement(reynoldsNumber, ascending, descending);
		return ascending;
	}

	private static XfoilPolar loadXfoilPolar(String resourceName, int expectedReynoldsNumber) {
		InputStream stream = Sda1075XfoilSectionPolar.class.getResourceAsStream(
				LOW_RE_RESOURCE_ROOT + resourceName
		);
		if (stream == null) {
			throw new IllegalStateException("Missing XFOIL polar resource: " + resourceName);
		}
		double[] lift = new double[LOW_RE_ANGLE_AXIS_DEGREES.length];
		double[] drag = new double[LOW_RE_ANGLE_AXIS_DEGREES.length];
		double[] pitchingMoment = new double[LOW_RE_ANGLE_AXIS_DEGREES.length];
		boolean[] present = new boolean[LOW_RE_ANGLE_AXIS_DEGREES.length];
		boolean versionVerified = false;
		boolean settingsVerified = false;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(stream, StandardCharsets.US_ASCII))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.contains("XFOIL") && line.contains("Version 6.99")) {
					versionVerified = true;
				}
				Matcher settingsMatcher = XFOIL_SETTINGS_PATTERN.matcher(line);
				if (settingsMatcher.find()) {
					double machNumber = Double.parseDouble(settingsMatcher.group(1));
					double reynoldsNumber = Double.parseDouble(settingsMatcher.group(2)) * 1.0e6;
					double ncrit = Double.parseDouble(settingsMatcher.group(3));
					settingsVerified = Math.abs(machNumber) <= 1.0e-12
							&& Math.abs(reynoldsNumber - expectedReynoldsNumber) <= 0.5
							&& Math.abs(ncrit - 9.0) <= 1.0e-12;
				}
				String[] fields = line.trim().split("\\s+");
				if (fields.length < 5) {
					continue;
				}
				double alpha;
				double liftCoefficient;
				double dragCoefficient;
				double pitchingMomentCoefficient;
				try {
					alpha = Double.parseDouble(fields[0]);
					liftCoefficient = Double.parseDouble(fields[1]);
					dragCoefficient = Double.parseDouble(fields[2]);
					pitchingMomentCoefficient = Double.parseDouble(fields[4]);
				} catch (NumberFormatException ignored) {
					continue;
				}
				int angleIndex = (int) Math.round(alpha);
				if (angleIndex < 0
						|| angleIndex >= LOW_RE_ANGLE_AXIS_DEGREES.length
						|| Math.abs(alpha - angleIndex) > 1.0e-9) {
					continue;
				}
				lift[angleIndex] = liftCoefficient;
				drag[angleIndex] = dragCoefficient;
				pitchingMoment[angleIndex] = pitchingMomentCoefficient;
				present[angleIndex] = true;
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Could not read XFOIL polar resource: " + resourceName,
					exception);
		}
		if (!versionVerified || !settingsVerified) {
			throw new IllegalStateException(
					"XFOIL polar provenance/settings mismatch: " + resourceName
			);
		}
		for (int angleIndex = 0; angleIndex < present.length; angleIndex++) {
			if (!present[angleIndex]) {
				throw new IllegalStateException(
						"XFOIL polar resource is missing alpha=" + angleIndex + ": " + resourceName
				);
			}
		}
		return new XfoilPolar(lift, drag, pitchingMoment);
	}

	private static void verifySweepAgreement(
			int reynoldsNumber,
			XfoilPolar ascending,
			XfoilPolar descending
	) {
		for (Coefficient coefficient : Coefficient.values()) {
			double[] ascendingValues = coefficient.values(ascending);
			double[] descendingValues = coefficient.values(descending);
			for (int angleIndex = 0; angleIndex < ascendingValues.length; angleIndex++) {
				if (Math.abs(ascendingValues[angleIndex] - descendingValues[angleIndex])
						> SWEEP_AGREEMENT_TOLERANCE) {
					throw new IllegalStateException(
							"XFOIL sweep disagreement at Re=" + reynoldsNumber
									+ ", alpha=" + angleIndex
									+ ", coefficient=" + coefficient
					);
				}
			}
		}
	}

	private static AxisBracket bracket(double[] axis, double value) {
		for (int upperIndex = 0; upperIndex < axis.length; upperIndex++) {
			double upperValue = axis[upperIndex];
			if (Double.compare(value, upperValue) == 0) {
				return new AxisBracket(upperIndex, upperIndex, upperValue, upperValue, 0.0);
			}
			if (value < upperValue) {
				int lowerIndex = Math.max(0, upperIndex - 1);
				double lowerValue = axis[lowerIndex];
				double fraction = (value - lowerValue) / (upperValue - lowerValue);
				return new AxisBracket(
						lowerIndex,
						upperIndex,
						lowerValue,
						upperValue,
						fraction
				);
			}
		}
		int lastIndex = axis.length - 1;
		return new AxisBracket(lastIndex, lastIndex, axis[lastIndex], axis[lastIndex], 0.0);
	}

	private static double bilinear(
			double[][] values,
			AxisBracket reynoldsBracket,
			AxisBracket angleBracket
	) {
		double lowerReynoldsValue = lerp(
				values[reynoldsBracket.lowerIndex()][angleBracket.lowerIndex()],
				values[reynoldsBracket.lowerIndex()][angleBracket.upperIndex()],
				angleBracket.fraction()
		);
		double upperReynoldsValue = lerp(
				values[reynoldsBracket.upperIndex()][angleBracket.lowerIndex()],
				values[reynoldsBracket.upperIndex()][angleBracket.upperIndex()],
				angleBracket.fraction()
		);
		return lerp(lowerReynoldsValue, upperReynoldsValue, reynoldsBracket.fraction());
	}

	private static double lerp(double lower, double upper, double fraction) {
		return lower + (upper - lower) * fraction;
	}

	private static InterpolationStatus interpolationStatus(
			AxisBracket reynoldsBracket,
			AxisBracket angleBracket
	) {
		boolean reynoldsExact = reynoldsBracket.lowerIndex() == reynoldsBracket.upperIndex();
		boolean angleExact = angleBracket.lowerIndex() == angleBracket.upperIndex();
		if (reynoldsExact && angleExact) {
			return InterpolationStatus.EXACT;
		}
		if (reynoldsExact) {
			return InterpolationStatus.LINEAR_ANGLE_OF_ATTACK;
		}
		if (angleExact) {
			return InterpolationStatus.LINEAR_REYNOLDS;
		}
		return InterpolationStatus.BILINEAR;
	}

	private record AxisBracket(
			int lowerIndex,
			int upperIndex,
			double lowerValue,
			double upperValue,
			double fraction
	) {
	}

	private enum Coefficient {
		LIFT,
		DRAG,
		PITCHING_MOMENT;

		double[] values(XfoilPolar polar) {
			return switch (this) {
				case LIFT -> polar.liftCoefficient();
				case DRAG -> polar.dragCoefficient();
				case PITCHING_MOMENT -> polar.pitchingMomentCoefficient();
			};
		}

		double[][] reviewedTable() {
			return switch (this) {
				case LIFT -> LIFT_COEFFICIENT;
				case DRAG -> DRAG_COEFFICIENT;
				case PITCHING_MOMENT -> PITCHING_MOMENT_COEFFICIENT;
			};
		}
	}

	private record XfoilPolar(
			double[] liftCoefficient,
			double[] dragCoefficient,
			double[] pitchingMomentCoefficient
	) {
	}

	public record PolarSample(
			String dataSourceId,
			double queryReynoldsNumber,
			double queryAngleOfAttackDegrees,
			double effectiveReynoldsNumber,
			double effectiveAngleOfAttackDegrees,
			double lowerReynoldsNumber,
			double upperReynoldsNumber,
			double lowerAngleOfAttackDegrees,
			double upperAngleOfAttackDegrees,
			double reynoldsInterpolationFraction,
			double angleInterpolationFraction,
			double liftCoefficientCl,
			double dragCoefficientCd,
			double pitchingMomentCoefficientCm,
			InterpolationStatus interpolationStatus,
			boolean reynoldsOutOfEnvelope,
			boolean angleOfAttackOutOfEnvelope,
			boolean reynoldsClamped,
			boolean angleOfAttackClamped,
			boolean blocked,
			boolean lowReynoldsExtensionUsed,
			boolean sourceSweepAgreementVerified,
			String message
	) {
		public boolean accepted() {
			return !blocked;
		}

		public boolean clamped() {
			return reynoldsClamped || angleOfAttackClamped;
		}

		public double queryAngleOfAttackRadians() {
			return Math.toRadians(queryAngleOfAttackDegrees);
		}

		public double effectiveAngleOfAttackRadians() {
			return Math.toRadians(effectiveAngleOfAttackDegrees);
		}
	}
}
