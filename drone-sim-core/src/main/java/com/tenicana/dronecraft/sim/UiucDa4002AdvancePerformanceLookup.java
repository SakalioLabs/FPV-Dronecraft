package com.tenicana.dronecraft.sim;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.tenicana.dronecraft.sim.RotorHoverBladeProfilePowerModel.BladeGeometry;

/** Measured UIUC Volume 2 DA4002 advancing-flow CT/CP/J reference lookup. */
public final class UiucDa4002AdvancePerformanceLookup {
	public static final String DATA_SOURCE_ID = "uiuc-propdb-volume-2-da4002-advance";
	public static final String SOURCE_BASE_URL =
			"https://m-selig.ae.illinois.edu/props/volume-2/data/";
	private static final String RESOURCE_ROOT =
			"/com/tenicana/dronecraft/sim/aero/uiuc-da4002-advance/";
	private static final double EPSILON = 1.0e-12;
	// Negative-thrust rows amplify the source table's six-decimal CT/CP rounding.
	private static final double SOURCE_ETA_CLOSURE_TOLERANCE = 2.0e-4;

	private static final List<AdvanceCurve> FIVE_INCH_CURVES = List.of(
			curve("da4002-5x3.75-rpm4031", "da4002_5x3.75_1123md_4031.txt",
					UiucDa4002PropellerGeometry.fiveByThreePointSevenFiveTwoBlade(),
					UiucDa4002PropellerGeometry.FIVE_INCH_DIAMETER_METERS, 4_031.0),
			curve("da4002-5x3.75-rpm5039", "da4002_5x3.75_1124md_5039.txt",
					UiucDa4002PropellerGeometry.fiveByThreePointSevenFiveTwoBlade(),
					UiucDa4002PropellerGeometry.FIVE_INCH_DIAMETER_METERS, 5_039.0),
			curve("da4002-5x3.75-rpm6045", "da4002_5x3.75_1125md_6045.txt",
					UiucDa4002PropellerGeometry.fiveByThreePointSevenFiveTwoBlade(),
					UiucDa4002PropellerGeometry.FIVE_INCH_DIAMETER_METERS, 6_045.0)
	);
	private static final List<AdvanceCurve> NINE_INCH_CURVES = List.of(
			curve("da4002-9x6.75-rpm2013", "da4002_9x6.75_1100md_2013.txt",
					UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
					UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS, 2_013.0),
			curve("da4002-9x6.75-rpm3030", "da4002_9x6.75_1101md_3030.txt",
					UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
					UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS, 3_030.0),
			curve("da4002-9x6.75-rpm4054", "da4002_9x6.75_1102rd_4054.txt",
					UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
					UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS, 4_054.0),
			curve("da4002-9x6.75-rpm4049", "da4002_9x6.75_1103rd_4049.txt",
					UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
					UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS, 4_049.0),
			curve("da4002-9x6.75-rpm5028", "da4002_9x6.75_1104rd_5028.txt",
					UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
					UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS, 5_028.0),
			curve("da4002-9x6.75-rpm5064", "da4002_9x6.75_1105rd_5064.txt",
					UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
					UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS, 5_064.0)
	);
	private static final List<AdvanceCurve> CURVES;

	static {
		List<AdvanceCurve> curves = new ArrayList<>(FIVE_INCH_CURVES.size()
				+ NINE_INCH_CURVES.size());
		curves.addAll(FIVE_INCH_CURVES);
		curves.addAll(NINE_INCH_CURVES);
		CURVES = List.copyOf(curves);
	}

	private UiucDa4002AdvancePerformanceLookup() {
	}

	public enum EnvelopePolicy {
		BLOCK_OUT_OF_ENVELOPE,
		CLAMP_TO_ENVELOPE
	}

	public enum InterpolationStatus {
		EXACT,
		LINEAR_ADVANCE_RATIO,
		BLOCKED
	}

	public static List<AdvanceCurve> fiveInchCurves() {
		return FIVE_INCH_CURVES;
	}

	public static List<AdvanceCurve> nineInchCurves() {
		return NINE_INCH_CURVES;
	}

	public static List<AdvanceCurve> curves() {
		return CURVES;
	}

	public static LookupResult evaluate(
			AdvanceCurve curve,
			double advanceRatio,
			EnvelopePolicy envelopePolicy
	) {
		if (curve == null) {
			throw new IllegalArgumentException("curve must not be null.");
		}
		if (!Double.isFinite(advanceRatio) || advanceRatio < 0.0) {
			throw new IllegalArgumentException("advanceRatio must be finite and non-negative.");
		}
		if (envelopePolicy == null) {
			envelopePolicy = EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE;
		}
		boolean outOfEnvelope = advanceRatio < curve.minimumAdvanceRatio()
				|| advanceRatio > curve.maximumAdvanceRatio();
		if (outOfEnvelope && envelopePolicy == EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE) {
			return blocked(curve, advanceRatio);
		}

		double effectiveAdvanceRatio = MathUtil.clamp(
				advanceRatio,
				curve.minimumAdvanceRatio(),
				curve.maximumAdvanceRatio()
		);
		for (AdvanceRow row : curve.rows()) {
			if (Double.compare(effectiveAdvanceRatio, row.advanceRatioJ()) == 0) {
				return result(
						curve,
						advanceRatio,
						effectiveAdvanceRatio,
						row,
						row,
						0.0,
						row.thrustCoefficientCt(),
						row.powerCoefficientCp(),
						row.propulsiveEfficiencyEta(),
						InterpolationStatus.EXACT,
						outOfEnvelope,
						outOfEnvelope
								? "query-clamped-to-uiuc-advance-envelope"
								: "uiuc-advance-row-exact"
				);
			}
		}

		for (int rowIndex = 1; rowIndex < curve.rows().size(); rowIndex++) {
			AdvanceRow upper = curve.rows().get(rowIndex);
			if (effectiveAdvanceRatio >= upper.advanceRatioJ()) {
				continue;
			}
			AdvanceRow lower = curve.rows().get(rowIndex - 1);
			double fraction = (effectiveAdvanceRatio - lower.advanceRatioJ())
					/ (upper.advanceRatioJ() - lower.advanceRatioJ());
			double thrustCoefficient = lerp(
					lower.thrustCoefficientCt(),
					upper.thrustCoefficientCt(),
					fraction
			);
			double powerCoefficient = lerp(
					lower.powerCoefficientCp(),
					upper.powerCoefficientCp(),
					fraction
			);
			return result(
					curve,
					advanceRatio,
					effectiveAdvanceRatio,
					lower,
					upper,
					fraction,
					thrustCoefficient,
					powerCoefficient,
					ratio(thrustCoefficient * effectiveAdvanceRatio, powerCoefficient),
					InterpolationStatus.LINEAR_ADVANCE_RATIO,
					false,
					"uiuc-advance-row-linearly-interpolated"
			);
		}
		throw new IllegalStateException("validated UIUC advance curve does not bracket query.");
	}

	public static DimensionalSample sample(
			AdvanceCurve curve,
			double advanceRatio,
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
		LookupResult lookup = evaluate(curve, advanceRatio, envelopePolicy);
		double diameter = curve.referenceDiameterMeters();
		double radius = diameter * 0.5;
		double revolutionsPerSecond = curve.rpm() / 60.0;
		double angularVelocity = 2.0 * Math.PI * revolutionsPerSecond;
		double axialFreestreamVelocity = lookup.blocked()
				? advanceRatio * revolutionsPerSecond * diameter
				: lookup.effectiveAdvanceRatioJ() * revolutionsPerSecond * diameter;
		double chord75 = curve.geometry().chordToRadiusAt(0.75) * radius;
		double rotationalSpeed75 = 0.75 * angularVelocity * radius;
		double relativeSpeed75 = Math.hypot(rotationalSpeed75, axialFreestreamVelocity);
		double reynolds75 = airDensityKgPerCubicMeter
				* relativeSpeed75
				* chord75
				/ dynamicViscosityPascalSeconds;
		if (lookup.blocked()) {
			return new DimensionalSample(
					lookup,
					airDensityKgPerCubicMeter,
					dynamicViscosityPascalSeconds,
					diameter,
					revolutionsPerSecond,
					angularVelocity,
					axialFreestreamVelocity,
					reynolds75,
					0.0,
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
		double usefulPower = thrust * axialFreestreamVelocity;
		return new DimensionalSample(
				lookup,
				airDensityKgPerCubicMeter,
				dynamicViscosityPascalSeconds,
				diameter,
				revolutionsPerSecond,
				angularVelocity,
				axialFreestreamVelocity,
				reynolds75,
				thrust,
				shaftPower,
				shaftTorque,
				usefulPower,
				ratio(usefulPower, shaftPower)
		);
	}

	private static AdvanceCurve curve(
			String id,
			String resourceName,
			BladeGeometry geometry,
			double diameterMeters,
			double rpm
	) {
		List<AdvanceRow> rows = loadRows(resourceName);
		return new AdvanceCurve(
				id,
				"UIUC " + id,
				resourceName,
				SOURCE_BASE_URL + resourceName,
				geometry,
				diameterMeters,
				rpm,
				rows
		);
	}

	private static List<AdvanceRow> loadRows(String resourceName) {
		InputStream stream = UiucDa4002AdvancePerformanceLookup.class.getResourceAsStream(
				RESOURCE_ROOT + resourceName
		);
		if (stream == null) {
			throw new IllegalStateException("Missing UIUC advance resource: " + resourceName);
		}
		List<AdvanceRow> rows = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(stream, StandardCharsets.US_ASCII))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] fields = line.trim().split("\\s+");
				if (fields.length != 4) {
					continue;
				}
				try {
					rows.add(new AdvanceRow(
							Double.parseDouble(fields[0]),
							Double.parseDouble(fields[1]),
							Double.parseDouble(fields[2]),
							Double.parseDouble(fields[3])
					));
				} catch (NumberFormatException ignored) {
					// Header row.
				}
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Could not read UIUC advance resource: " + resourceName,
					exception);
		}
		if (rows.size() < 2) {
			throw new IllegalStateException("UIUC advance resource has too few rows: " + resourceName);
		}
		for (int index = 0; index < rows.size(); index++) {
			AdvanceRow row = rows.get(index);
			if (index > 0 && row.advanceRatioJ() <= rows.get(index - 1).advanceRatioJ()) {
				throw new IllegalStateException("UIUC advance rows are not strictly ordered: "
						+ resourceName);
			}
			if (row.powerCoefficientCp() <= 0.0
					|| Math.abs(row.etaClosureResidual()) > SOURCE_ETA_CLOSURE_TOLERANCE) {
				throw new IllegalStateException("UIUC advance row failed coefficient closure: "
						+ resourceName + " J=" + row.advanceRatioJ());
			}
		}
		return List.copyOf(rows);
	}

	private static LookupResult blocked(AdvanceCurve curve, double advanceRatio) {
		return new LookupResult(
				curve,
				advanceRatio,
				advanceRatio,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				InterpolationStatus.BLOCKED,
				true,
				false,
				true,
				"query-outside-uiuc-advance-envelope"
		);
	}

	private static LookupResult result(
			AdvanceCurve curve,
			double queryAdvanceRatio,
			double effectiveAdvanceRatio,
			AdvanceRow lower,
			AdvanceRow upper,
			double fraction,
			double thrustCoefficient,
			double powerCoefficient,
			double efficiency,
			InterpolationStatus interpolationStatus,
			boolean outOfEnvelope,
			String message
	) {
		return new LookupResult(
				curve,
				queryAdvanceRatio,
				effectiveAdvanceRatio,
				lower.advanceRatioJ(),
				upper.advanceRatioJ(),
				fraction,
				thrustCoefficient,
				powerCoefficient,
				efficiency,
				efficiency - ratio(thrustCoefficient * effectiveAdvanceRatio, powerCoefficient),
				interpolationStatus,
				outOfEnvelope,
				outOfEnvelope,
				false,
				message
		);
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

	public record AdvanceRow(
			double advanceRatioJ,
			double thrustCoefficientCt,
			double powerCoefficientCp,
			double propulsiveEfficiencyEta
	) {
		public double etaClosureResidual() {
			return propulsiveEfficiencyEta - ratio(
					thrustCoefficientCt * advanceRatioJ,
					powerCoefficientCp
			);
		}
	}

	public record AdvanceCurve(
			String id,
			String label,
			String resourceName,
			String sourceUrl,
			BladeGeometry geometry,
			double referenceDiameterMeters,
			double rpm,
			List<AdvanceRow> rows
	) {
		public AdvanceCurve {
			rows = List.copyOf(rows);
		}

		public double minimumAdvanceRatio() {
			return rows.get(0).advanceRatioJ();
		}

		public double maximumAdvanceRatio() {
			return rows.get(rows.size() - 1).advanceRatioJ();
		}
	}

	public record LookupResult(
			AdvanceCurve curve,
			double queryAdvanceRatioJ,
			double effectiveAdvanceRatioJ,
			double lowerAdvanceRatioJ,
			double upperAdvanceRatioJ,
			double interpolationFraction,
			double thrustCoefficientCt,
			double powerCoefficientCp,
			double propulsiveEfficiencyEta,
			double etaClosureResidual,
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
			double revolutionsPerSecond,
			double angularVelocityRadiansPerSecond,
			double axialFreestreamVelocityMetersPerSecond,
			double reynoldsNumberAtSeventyFivePercentRadius,
			double thrustNewtons,
			double shaftPowerWatts,
			double shaftTorqueNewtonMeters,
			double usefulPropulsivePowerWatts,
			double coefficientDerivedEfficiencyEta
	) {
		public boolean blocked() {
			return lookup.blocked();
		}
	}
}
