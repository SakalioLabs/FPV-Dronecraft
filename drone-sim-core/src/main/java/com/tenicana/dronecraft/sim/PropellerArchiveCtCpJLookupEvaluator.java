package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PropellerArchiveCtCpJLookupEvaluator {
	public static final String DEFAULT_PRESET_NAME = "apDrone";
	public static final String DATA_SOURCE_ID = "accepted-reference-payload-bridge";
	private static final double EPSILON = 1.0e-9;

	// Runtime lookup window materialized from the accepted PropellerArchive CT/CP/J reference bridge.
	private static final List<ReferenceWindow> ACCEPTED_REFERENCE_WINDOWS = List.of(
			new ReferenceWindow(DEFAULT_PRESET_NAME, "static_anchor_low_rpm", 0.0, 1_477.8,
					List.of(row("static-anchor", 0.0, 1_477.8, 0.120, 0.040))),
			new ReferenceWindow(DEFAULT_PRESET_NAME, "mid_domain_mid_rpm", 0.4064, 4_712.25,
					List.of(
							row("j0-r0", 0.32512, 4_065.36, 0.100, 0.046),
							row("j1-r0", 0.48768, 4_065.36, 0.092, 0.051),
							row("j0-r1", 0.32512, 5_359.14, 0.095, 0.049),
							row("j1-r1", 0.48768, 5_359.14, 0.086, 0.057))),
			new ReferenceWindow(DEFAULT_PRESET_NAME, "high_domain_max_rpm", 0.73152, 7_946.7,
					List.of(
							row("j0-rmax", 0.65024, 7_946.7, 0.088, 0.054),
							row("j1-rmax", 0.8128, 7_946.7, 0.080, 0.060)))
	);

	private PropellerArchiveCtCpJLookupEvaluator() {
	}

	public enum EnvelopePolicy {
		BLOCK_OUT_OF_ENVELOPE,
		CLAMP_TO_ENVELOPE
	}

	public enum InterpolationStatus {
		EXACT,
		LINEAR_ADVANCE,
		LINEAR_RPM,
		BILINEAR,
		CLAMPED_EXACT,
		CLAMPED_LINEAR_ADVANCE,
		CLAMPED_LINEAR_RPM,
		CLAMPED_BILINEAR,
		BLOCKED
	}

	public record LookupQuery(
			String presetName,
			String caseName,
			double advanceRatioJ,
			double rpm,
			double propellerDiameterMeters,
			double airDensityKgPerCubicMeter,
			EnvelopePolicy envelopePolicy
	) {
		public LookupQuery {
			presetName = normalizePreset(presetName);
			caseName = normalizeCase(caseName);
			if (!Double.isFinite(advanceRatioJ) || advanceRatioJ < 0.0) {
				throw new IllegalArgumentException("advanceRatioJ must be finite and nonnegative.");
			}
			if (!Double.isFinite(rpm) || rpm <= 0.0) {
				throw new IllegalArgumentException("rpm must be finite and positive.");
			}
			if (!Double.isFinite(propellerDiameterMeters) || propellerDiameterMeters <= 0.0) {
				throw new IllegalArgumentException("propellerDiameterMeters must be finite and positive.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (envelopePolicy == null) {
				envelopePolicy = EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE;
			}
		}

		public static LookupQuery fromRotorRadius(
				String presetName,
				String caseName,
				double advanceRatioJ,
				double rpm,
				double rotorRadiusMeters,
				double airDensityKgPerCubicMeter,
				EnvelopePolicy envelopePolicy
		) {
			if (!Double.isFinite(rotorRadiusMeters) || rotorRadiusMeters <= 0.0) {
				throw new IllegalArgumentException("rotorRadiusMeters must be finite and positive.");
			}
			return new LookupQuery(presetName, caseName, advanceRatioJ, rpm,
					rotorRadiusMeters * 2.0, airDensityKgPerCubicMeter, envelopePolicy);
		}
	}

	public record LookupResult(
			String presetName,
			String caseName,
			String dataSourceId,
			double queryAdvanceRatioJ,
			double queryRpm,
			double effectiveAdvanceRatioJ,
			double effectiveRpm,
			double lowerAdvanceRatioJ,
			double upperAdvanceRatioJ,
			double lowerRpm,
			double upperRpm,
			double advanceInterpolationFraction,
			double rpmInterpolationFraction,
			int observedNeighborRows,
			int minimumNeighborRowsRequired,
			double thrustCoefficientCt,
			double powerCoefficientCp,
			double propulsiveEfficiencyEta,
			InterpolationStatus interpolationStatus,
			boolean clamped,
			boolean blocked,
			String status,
			String message
	) {
		public boolean accepted() {
			return !blocked;
		}
	}

	public record RotorDimensionalSample(
			LookupResult lookup,
			double airDensityKgPerCubicMeter,
			double rotorRadiusMeters,
			double propellerDiameterMeters,
			double revolutionsPerSecond,
			double angularVelocityRadiansPerSecond,
			double axialAdvanceSpeedMetersPerSecond,
			double thrustNewtons,
			double shaftPowerWatts,
			double shaftTorqueNewtonMeters,
			double diskAreaSquareMeters,
			double diskLoadingNewtonsPerSquareMeter,
			double idealInducedVelocityMetersPerSecond,
			double idealMomentumPowerWatts,
			double idealMomentumPowerOverShaftPower
	) {
		public boolean blocked() {
			return lookup.blocked();
		}

		public boolean clamped() {
			return lookup.clamped();
		}
	}

	public static LookupQuery queryForReferenceCase(
			String presetName,
			String caseName,
			double propellerDiameterMeters,
			double airDensityKgPerCubicMeter
	) {
		ReferenceWindow window = ACCEPTED_REFERENCE_WINDOWS.stream()
				.filter(candidate -> candidate.presetName().equals(normalizePreset(presetName))
						&& candidate.caseName().equals(caseName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown accepted CT/CP/J reference case: " + normalizePreset(presetName) + " / " + caseName));
		return new LookupQuery(
				window.presetName(),
				window.caseName(),
				window.queryAdvanceRatioJ(),
				window.queryRpm(),
				propellerDiameterMeters,
				airDensityKgPerCubicMeter,
				EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
		);
	}

	public static LookupResult evaluate(LookupQuery query) {
		if (query == null) {
			throw new IllegalArgumentException("lookup query must not be null.");
		}
		ReferenceWindow window = selectWindow(query);
		if (window == null) {
			return blocked(query, "REFERENCE_WINDOW_UNAVAILABLE", "no-accepted-reference-window-brackets-query");
		}
		boolean clamp = query.envelopePolicy() == EnvelopePolicy.CLAMP_TO_ENVELOPE;
		double effectiveJ = query.advanceRatioJ();
		double effectiveRpm = query.rpm();
		boolean clamped = false;
		if (!window.inside(query.advanceRatioJ(), query.rpm())) {
			if (!clamp) {
				return blocked(query, "OUT_OF_ENVELOPE_BLOCKED", "query-outside-accepted-reference-window");
			}
			effectiveJ = MathUtil.clamp(query.advanceRatioJ(), window.minAdvanceRatioJ(), window.maxAdvanceRatioJ());
			effectiveRpm = MathUtil.clamp(query.rpm(), window.minRpm(), window.maxRpm());
			clamped = Math.abs(effectiveJ - query.advanceRatioJ()) > EPSILON
					|| Math.abs(effectiveRpm - query.rpm()) > EPSILON;
		}
		Bracket bracket = window.bracket(effectiveJ, effectiveRpm);
		if (!bracket.inside()) {
			return blocked(query, "REFERENCE_WINDOW_INCOMPLETE", "accepted-reference-window-does-not-bracket-query");
		}
		List<CoefficientGridRow> neighbors = window.neighborRows(bracket);
		int expectedNeighbors = expectedNeighborRows(bracket);
		if (neighbors.size() < expectedNeighbors) {
			return blocked(query, "REFERENCE_NEIGHBOR_ROWS_MISSING", "accepted-reference-neighbor-row-missing");
		}
		CoefficientGridRow j0r0 = window.row(bracket.lowerAdvanceRatioJ(), bracket.lowerRpm());
		CoefficientGridRow j1r0 = window.row(bracket.upperAdvanceRatioJ(), bracket.lowerRpm());
		CoefficientGridRow j0r1 = window.row(bracket.lowerAdvanceRatioJ(), bracket.upperRpm());
		CoefficientGridRow j1r1 = window.row(bracket.upperAdvanceRatioJ(), bracket.upperRpm());
		double ct = interpolate(bracket, j0r0.ctCoefficient(), j1r0.ctCoefficient(),
				j0r1.ctCoefficient(), j1r1.ctCoefficient());
		double cp = interpolate(bracket, j0r0.cpCoefficient(), j1r0.cpCoefficient(),
				j0r1.cpCoefficient(), j1r1.cpCoefficient());
		double eta = eta(effectiveJ, ct, cp);
		return new LookupResult(
				window.presetName(),
				window.caseName(),
				DATA_SOURCE_ID,
				query.advanceRatioJ(),
				query.rpm(),
				effectiveJ,
				effectiveRpm,
				bracket.lowerAdvanceRatioJ(),
				bracket.upperAdvanceRatioJ(),
				bracket.lowerRpm(),
				bracket.upperRpm(),
				bracket.advanceFraction(),
				bracket.rpmFraction(),
				neighbors.size(),
				expectedNeighbors,
				ct,
				cp,
				eta,
				statusFor(bracket, clamped),
				clamped,
				false,
				clamped ? "CLAMPED" : "INTERPOLATED",
				clamped ? "query-clamped-to-accepted-reference-window" : "ct-cp-j-lookup-interpolated"
		);
	}

	public static RotorDimensionalSample sampleRotor(LookupQuery query) {
		LookupResult lookup = evaluate(query);
		return sampleRotor(lookup, query.propellerDiameterMeters(), query.airDensityKgPerCubicMeter());
	}

	public static RotorDimensionalSample sampleRotor(
			LookupResult lookup,
			double propellerDiameterMeters,
			double airDensityKgPerCubicMeter
	) {
		if (lookup == null) {
			throw new IllegalArgumentException("lookup result must not be null.");
		}
		if (!Double.isFinite(propellerDiameterMeters) || propellerDiameterMeters <= 0.0) {
			throw new IllegalArgumentException("propellerDiameterMeters must be finite and positive.");
		}
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		double radius = propellerDiameterMeters * 0.5;
		double diskArea = Math.PI * radius * radius;
		double revolutionsPerSecond = Math.max(0.0, lookup.effectiveRpm()) / 60.0;
		double omega = revolutionsPerSecond * 2.0 * Math.PI;
		if (lookup.blocked()) {
			return new RotorDimensionalSample(
					lookup,
					airDensityKgPerCubicMeter,
					radius,
					propellerDiameterMeters,
					revolutionsPerSecond,
					omega,
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
		double advanceSpeed = lookup.effectiveAdvanceRatioJ() * revolutionsPerSecond * propellerDiameterMeters;
		double thrust = lookup.thrustCoefficientCt()
				* airDensityKgPerCubicMeter
				* revolutionsPerSecond
				* revolutionsPerSecond
				* Math.pow(propellerDiameterMeters, 4.0);
		double shaftPower = lookup.powerCoefficientCp()
				* airDensityKgPerCubicMeter
				* Math.pow(revolutionsPerSecond, 3.0)
				* Math.pow(propellerDiameterMeters, 5.0);
		double torque = omega > EPSILON ? shaftPower / omega : 0.0;
		double diskLoading = diskArea > EPSILON ? thrust / diskArea : 0.0;
		double inducedVelocity = thrust > EPSILON && diskArea > EPSILON
				? Math.sqrt(thrust / (2.0 * airDensityKgPerCubicMeter * diskArea))
				: 0.0;
		double idealMomentumPower = thrust * inducedVelocity;
		double momentumOverShaft = ratio(idealMomentumPower, shaftPower);
		return new RotorDimensionalSample(
				lookup,
				airDensityKgPerCubicMeter,
				radius,
				propellerDiameterMeters,
				revolutionsPerSecond,
				omega,
				advanceSpeed,
				thrust,
				shaftPower,
				torque,
				diskArea,
				diskLoading,
				inducedVelocity,
				idealMomentumPower,
				momentumOverShaft
		);
	}

	public static List<String> acceptedReferenceCaseNames(String presetName) {
		String normalizedPreset = normalizePreset(presetName);
		return ACCEPTED_REFERENCE_WINDOWS.stream()
				.filter(window -> window.presetName().equals(normalizedPreset))
				.map(ReferenceWindow::caseName)
				.toList();
	}

	private static ReferenceWindow selectWindow(LookupQuery query) {
		if (!query.caseName().isBlank()) {
			return ACCEPTED_REFERENCE_WINDOWS.stream()
					.filter(window -> window.presetName().equals(query.presetName())
							&& window.caseName().equals(query.caseName()))
					.findFirst()
					.orElse(null);
		}
		return ACCEPTED_REFERENCE_WINDOWS.stream()
				.filter(window -> window.presetName().equals(query.presetName()))
				.filter(window -> window.inside(query.advanceRatioJ(), query.rpm()))
				.findFirst()
				.orElseGet(() -> query.envelopePolicy() == EnvelopePolicy.CLAMP_TO_ENVELOPE
						? nearestWindow(query)
						: null);
	}

	private static ReferenceWindow nearestWindow(LookupQuery query) {
		return ACCEPTED_REFERENCE_WINDOWS.stream()
				.filter(window -> window.presetName().equals(query.presetName()))
				.min(Comparator.comparingDouble(window -> window.outsideDistance(query.advanceRatioJ(), query.rpm())))
				.orElse(null);
	}

	private static LookupResult blocked(LookupQuery query, String status, String message) {
		return new LookupResult(
				query.presetName(),
				query.caseName(),
				DATA_SOURCE_ID,
				query.advanceRatioJ(),
				query.rpm(),
				query.advanceRatioJ(),
				query.rpm(),
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0,
				0,
				0.0,
				0.0,
				0.0,
				InterpolationStatus.BLOCKED,
				false,
				true,
				status,
				message
		);
	}

	private static CoefficientGridRow row(
			String rowId,
			double advanceRatioJ,
			double rpm,
			double ctCoefficient,
			double cpCoefficient
	) {
		return new CoefficientGridRow(rowId, advanceRatioJ, rpm, ctCoefficient, cpCoefficient);
	}

	private static InterpolationStatus statusFor(Bracket bracket, boolean clamped) {
		boolean advanceExact = same(bracket.lowerAdvanceRatioJ(), bracket.upperAdvanceRatioJ());
		boolean rpmExact = same(bracket.lowerRpm(), bracket.upperRpm());
		if (advanceExact && rpmExact) {
			return clamped ? InterpolationStatus.CLAMPED_EXACT : InterpolationStatus.EXACT;
		}
		if (rpmExact) {
			return clamped ? InterpolationStatus.CLAMPED_LINEAR_ADVANCE : InterpolationStatus.LINEAR_ADVANCE;
		}
		if (advanceExact) {
			return clamped ? InterpolationStatus.CLAMPED_LINEAR_RPM : InterpolationStatus.LINEAR_RPM;
		}
		return clamped ? InterpolationStatus.CLAMPED_BILINEAR : InterpolationStatus.BILINEAR;
	}

	private static double interpolate(
			Bracket bracket,
			double j0r0,
			double j1r0,
			double j0r1,
			double j1r1
	) {
		double lowRpm = MathUtil.lerp(j0r0, j1r0, bracket.advanceFraction());
		double highRpm = MathUtil.lerp(j0r1, j1r1, bracket.advanceFraction());
		return MathUtil.lerp(lowRpm, highRpm, bracket.rpmFraction());
	}

	private static int expectedNeighborRows(Bracket bracket) {
		int jCount = same(bracket.lowerAdvanceRatioJ(), bracket.upperAdvanceRatioJ()) ? 1 : 2;
		int rpmCount = same(bracket.lowerRpm(), bracket.upperRpm()) ? 1 : 2;
		return jCount * rpmCount;
	}

	private static double eta(double j, double ct, double cp) {
		if (j <= EPSILON || cp <= EPSILON) {
			return 0.0;
		}
		return j * ct / cp;
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private static String normalizePreset(String presetName) {
		if (presetName == null || presetName.isBlank()) {
			return DEFAULT_PRESET_NAME;
		}
		return presetName;
	}

	private static String normalizeCase(String caseName) {
		return caseName == null ? "" : caseName;
	}

	private static boolean same(double left, double right) {
		return Math.abs(left - right) <= EPSILON;
	}

	private record CoefficientGridRow(
			String rowId,
			double advanceRatioJ,
			double rpm,
			double ctCoefficient,
			double cpCoefficient
	) {
	}

	private record ReferenceWindow(
			String presetName,
			String caseName,
			double queryAdvanceRatioJ,
			double queryRpm,
			List<CoefficientGridRow> rows
	) {
		private ReferenceWindow {
			rows = List.copyOf(rows);
		}

		private boolean inside(double advanceRatioJ, double rpm) {
			return advanceRatioJ >= minAdvanceRatioJ() - EPSILON
					&& advanceRatioJ <= maxAdvanceRatioJ() + EPSILON
					&& rpm >= minRpm() - EPSILON
					&& rpm <= maxRpm() + EPSILON;
		}

		private double outsideDistance(double advanceRatioJ, double rpm) {
			double clampedJ = MathUtil.clamp(advanceRatioJ, minAdvanceRatioJ(), maxAdvanceRatioJ());
			double clampedRpm = MathUtil.clamp(rpm, minRpm(), maxRpm());
			double jScale = Math.max(EPSILON, maxAdvanceRatioJ() - minAdvanceRatioJ());
			double rpmScale = Math.max(1.0, maxRpm() - minRpm());
			double jDistance = (advanceRatioJ - clampedJ) / jScale;
			double rpmDistance = (rpm - clampedRpm) / rpmScale;
			return jDistance * jDistance + rpmDistance * rpmDistance;
		}

		private Bracket bracket(double queryJ, double queryRpm) {
			Double lowerJ = lowerOrEqual(advanceRatios(), queryJ);
			Double upperJ = upperOrEqual(advanceRatios(), queryJ);
			Double lowerRpm = lowerOrEqual(rpms(), queryRpm);
			Double upperRpm = upperOrEqual(rpms(), queryRpm);
			if (lowerJ == null || upperJ == null || lowerRpm == null || upperRpm == null) {
				return new Bracket(false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
			}
			return new Bracket(true, lowerJ, upperJ, lowerRpm, upperRpm,
					fraction(queryJ, lowerJ, upperJ), fraction(queryRpm, lowerRpm, upperRpm));
		}

		private List<CoefficientGridRow> neighborRows(Bracket bracket) {
			List<CoefficientGridRow> neighbors = new ArrayList<>();
			addIfPresent(neighbors, row(bracket.lowerAdvanceRatioJ(), bracket.lowerRpm()));
			addIfPresent(neighbors, row(bracket.upperAdvanceRatioJ(), bracket.lowerRpm()));
			addIfPresent(neighbors, row(bracket.lowerAdvanceRatioJ(), bracket.upperRpm()));
			addIfPresent(neighbors, row(bracket.upperAdvanceRatioJ(), bracket.upperRpm()));
			return neighbors;
		}

		private CoefficientGridRow row(double advanceRatioJ, double rpm) {
			for (CoefficientGridRow row : rows) {
				if (same(row.advanceRatioJ(), advanceRatioJ) && same(row.rpm(), rpm)) {
					return row;
				}
			}
			return null;
		}

		private List<Double> advanceRatios() {
			return distinctSorted(rows.stream().map(CoefficientGridRow::advanceRatioJ).toList());
		}

		private List<Double> rpms() {
			return distinctSorted(rows.stream().map(CoefficientGridRow::rpm).toList());
		}

		private double minAdvanceRatioJ() {
			return advanceRatios().get(0);
		}

		private double maxAdvanceRatioJ() {
			List<Double> values = advanceRatios();
			return values.get(values.size() - 1);
		}

		private double minRpm() {
			return rpms().get(0);
		}

		private double maxRpm() {
			List<Double> values = rpms();
			return values.get(values.size() - 1);
		}
	}

	private record Bracket(
			boolean inside,
			double lowerAdvanceRatioJ,
			double upperAdvanceRatioJ,
			double lowerRpm,
			double upperRpm,
			double advanceFraction,
			double rpmFraction
	) {
	}

	private static List<Double> distinctSorted(List<Double> rawValues) {
		List<Double> values = new ArrayList<>();
		for (double candidate : rawValues) {
			boolean present = false;
			for (double value : values) {
				if (same(value, candidate)) {
					present = true;
					break;
				}
			}
			if (!present) {
				values.add(candidate);
			}
		}
		values.sort(Comparator.naturalOrder());
		return values;
	}

	private static void addIfPresent(List<CoefficientGridRow> rows, CoefficientGridRow row) {
		if (row != null && rows.stream().noneMatch(existing -> existing == row)) {
			rows.add(row);
		}
	}

	private static Double lowerOrEqual(List<Double> values, double query) {
		Double selected = null;
		for (double value : values) {
			if (value <= query + EPSILON) {
				selected = value;
			}
		}
		return selected;
	}

	private static Double upperOrEqual(List<Double> values, double query) {
		for (double value : values) {
			if (value >= query - EPSILON) {
				return value;
			}
		}
		return null;
	}

	private static double fraction(double query, double lower, double upper) {
		if (same(lower, upper)) {
			return 0.0;
		}
		return (query - lower) / (upper - lower);
	}
}
