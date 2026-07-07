package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PropellerArchiveCtCpJLookupEvaluator {
	public static final String DEFAULT_PRESET_NAME = "apDrone";
	public static final String DATA_SOURCE_ID = "accepted-reference-payload-bridge";
	public static final String STATIC_ANCHORED_DATA_SOURCE_ID =
			"accepted-reference-advance-shape+rotor-spec-static-ct-cp";
	private static final double EPSILON = 1.0e-9;
	private static final double ADVANCE_SHAPE_RPM = 1.0;
	private static final Map<ReferenceWindow, ReferenceWindowGrid> REFERENCE_WINDOW_GRIDS =
			new ConcurrentHashMap<>();

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

	public enum LookupStatusCode {
		UNKNOWN,
		INTERPOLATED,
		CLAMPED,
		REFERENCE_WINDOW_UNAVAILABLE,
		OUT_OF_ENVELOPE_BLOCKED,
		REFERENCE_WINDOW_INCOMPLETE,
		REFERENCE_NEIGHBOR_ROWS_MISSING
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

		public LookupStatusCode lookupStatusCode() {
			return PropellerArchiveCtCpJLookupEvaluator.lookupStatusCode(status);
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
			double torqueCoefficientCq,
			double diskAreaSquareMeters,
			double diskLoadingNewtonsPerSquareMeter,
			double idealInducedVelocityMetersPerSecond,
			double idealMomentumPowerWatts,
			double usefulAxialThrustPowerWatts,
			double idealInducedPowerWatts,
			double axialPropulsiveEfficiency,
			double idealMomentumPowerOverShaftPower,
			double diskMassFlowKilogramsPerSecond,
			double farWakeAxialVelocityMetersPerSecond,
			double farWakeContractedAreaSquareMeters,
			double farWakeEquivalentRadiusMeters,
			double farWakeContractedAreaOverDiskArea,
			double farWakeEquivalentRadiusOverRotorRadius,
			double angularMomentumSwirlRadiusMeters,
			double wakeTangentialVelocityMetersPerSecond,
			double wakeSwirlKineticPowerWatts,
			double totalWakeKineticPowerWatts,
			double totalWakeKineticPowerOverShaftPower,
			double wakeSwirlKineticPowerOverShaftPower,
			double totalWakeKineticPowerResidualWatts,
			double totalWakeKineticPowerResidualFraction,
			double shaftPowerResidualWatts,
			double shaftPowerResidualFraction
	) {
		public boolean blocked() {
			return lookup.blocked();
		}

		public boolean clamped() {
			return lookup.clamped();
		}

		public double wakeAngularMomentumTorqueNewtonMeters() {
			return diskMassFlowKilogramsPerSecond
					* angularMomentumSwirlRadiusMeters
					* wakeTangentialVelocityMetersPerSecond;
		}

		public double wakeAngularMomentumTorqueResidualNewtonMeters() {
			return wakeAngularMomentumTorqueNewtonMeters() - Math.abs(shaftTorqueNewtonMeters);
		}

		public double wakeAngularMomentumTorqueResidualFraction() {
			double denominator = Math.abs(shaftTorqueNewtonMeters);
			if (!Double.isFinite(denominator) || denominator <= EPSILON) {
				return 0.0;
			}
			return wakeAngularMomentumTorqueResidualNewtonMeters() / denominator;
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
		Coefficients coefficients = interpolateCoefficients(j0r0, j1r0, j0r1, j1r1, bracket);
		double ct = coefficients.ctCoefficient();
		double cp = coefficients.cpCoefficient();
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

	public static LookupResult evaluateStaticAnchored(
			LookupQuery query,
			double staticThrustCoefficientCt,
			double staticPowerCoefficientCp
	) {
		if (query == null) {
			throw new IllegalArgumentException("lookup query must not be null.");
		}
		if (!Double.isFinite(staticThrustCoefficientCt) || staticThrustCoefficientCt <= 0.0) {
			throw new IllegalArgumentException("staticThrustCoefficientCt must be finite and positive.");
		}
		if (!Double.isFinite(staticPowerCoefficientCp) || staticPowerCoefficientCp <= 0.0) {
			throw new IllegalArgumentException("staticPowerCoefficientCp must be finite and positive.");
		}
		ReferenceWindow shapeWindow = acceptedAdvanceShapeWindow(query.presetName());
		if (shapeWindow == null) {
			return staticAnchoredBlocked(query, "REFERENCE_WINDOW_UNAVAILABLE",
					"no-accepted-reference-advance-shape-for-preset");
		}
		Coefficients staticAnchor = acceptedStaticAnchorCoefficients(query.presetName());
		if (staticAnchor == null || staticAnchor.ctCoefficient() <= EPSILON
				|| staticAnchor.cpCoefficient() <= EPSILON) {
			return staticAnchoredBlocked(query, "REFERENCE_WINDOW_INCOMPLETE",
					"accepted-static-anchor-coefficients-missing");
		}
		boolean clamp = query.envelopePolicy() == EnvelopePolicy.CLAMP_TO_ENVELOPE;
		double effectiveJ = query.advanceRatioJ();
		boolean clamped = false;
		if (!shapeWindow.insideAdvanceRatio(query.advanceRatioJ())) {
			if (!clamp) {
				return staticAnchoredBlocked(query, "OUT_OF_ENVELOPE_BLOCKED",
						"query-outside-accepted-advance-shape-window");
			}
			effectiveJ = MathUtil.clamp(
					query.advanceRatioJ(),
					shapeWindow.minAdvanceRatioJ(),
					shapeWindow.maxAdvanceRatioJ()
			);
			clamped = Math.abs(effectiveJ - query.advanceRatioJ()) > EPSILON;
		}
		Bracket bracket = shapeWindow.bracket(effectiveJ, ADVANCE_SHAPE_RPM);
		if (!bracket.inside()) {
			return staticAnchoredBlocked(query, "REFERENCE_WINDOW_INCOMPLETE",
					"accepted-advance-shape-does-not-bracket-query");
		}
		List<CoefficientGridRow> neighbors = shapeWindow.neighborRows(bracket);
		int expectedNeighbors = expectedNeighborRows(bracket);
		if (neighbors.size() < expectedNeighbors) {
			return staticAnchoredBlocked(query, "REFERENCE_NEIGHBOR_ROWS_MISSING",
					"accepted-advance-shape-neighbor-row-missing");
		}
		Coefficients shape = interpolateCoefficients(shapeWindow, bracket);
		double ct = staticThrustCoefficientCt * shape.ctCoefficient() / staticAnchor.ctCoefficient();
		double cp = staticPowerCoefficientCp * shape.cpCoefficient() / staticAnchor.cpCoefficient();
		double eta = eta(effectiveJ, ct, cp);
		return new LookupResult(
				shapeWindow.presetName(),
				query.caseName().isBlank() ? shapeWindow.caseName() : query.caseName(),
				STATIC_ANCHORED_DATA_SOURCE_ID,
				query.advanceRatioJ(),
				query.rpm(),
				effectiveJ,
				query.rpm(),
				bracket.lowerAdvanceRatioJ(),
				bracket.upperAdvanceRatioJ(),
				query.rpm(),
				query.rpm(),
				bracket.advanceFraction(),
				0.0,
				neighbors.size(),
				expectedNeighbors,
				ct,
				cp,
				eta,
				statusFor(bracket, clamped),
				clamped,
				false,
				clamped ? "CLAMPED" : "INTERPOLATED",
				clamped
						? "query-clamped-to-accepted-advance-shape-window"
						: "static-anchored-ct-cp-j-lookup-interpolated"
		);
	}

	public static RotorDimensionalSample sampleStaticAnchoredRotor(
			LookupQuery query,
			double staticThrustCoefficientCt,
			double staticPowerCoefficientCp
	) {
		LookupResult lookup = evaluateStaticAnchored(
				query,
				staticThrustCoefficientCt,
				staticPowerCoefficientCp
		);
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
		double revolutionsPerSecond = Math.max(0.0, lookup.queryRpm()) / 60.0;
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
					0.0,
					diskArea,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					0.0
			);
		}
		revolutionsPerSecond = Math.max(0.0, lookup.effectiveRpm()) / 60.0;
		omega = revolutionsPerSecond * 2.0 * Math.PI;
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
		double torqueCoefficientCq = torqueCoefficientCq(lookup.powerCoefficientCp());
		double torque = torqueCoefficientCq
				* airDensityKgPerCubicMeter
				* revolutionsPerSecond
				* revolutionsPerSecond
				* Math.pow(propellerDiameterMeters, 5.0);
		double diskLoading = diskArea > EPSILON ? thrust / diskArea : 0.0;
		double inducedVelocity = axialMomentumInducedVelocity(
				thrust, airDensityKgPerCubicMeter, diskArea, advanceSpeed);
		double nonnegativeAxialSpeed = Math.max(0.0, advanceSpeed);
		double usefulAxialThrustPower = thrust > EPSILON
				? thrust * nonnegativeAxialSpeed
				: 0.0;
		double idealInducedPower = thrust > EPSILON
				? thrust * inducedVelocity
				: 0.0;
		double idealMomentumPower = thrust > EPSILON
				? usefulAxialThrustPower + idealInducedPower
				: 0.0;
		double axialPropulsiveEfficiency = ratio(usefulAxialThrustPower, shaftPower);
		double momentumOverShaft = ratio(idealMomentumPower, shaftPower);
		double diskMassFlow = airDensityKgPerCubicMeter
				* diskArea
				* (nonnegativeAxialSpeed + inducedVelocity);
		double farWakeAxialVelocity = nonnegativeAxialSpeed + 2.0 * inducedVelocity;
		double farWakeArea = farWakeAxialVelocity > EPSILON
				? diskMassFlow / (airDensityKgPerCubicMeter * farWakeAxialVelocity)
				: 0.0;
		double farWakeRadius = farWakeArea > EPSILON
				? Math.sqrt(farWakeArea / Math.PI)
				: 0.0;
		double farWakeAreaRatio = ratio(farWakeArea, diskArea);
		double farWakeRadiusRatio = ratio(farWakeRadius, radius);
		double swirlRadius = farWakeRadius * RotorSpec.BLADE_GEOMETRY_REFERENCE_STATION_FRACTION;
		double tangentialWakeVelocity = diskMassFlow > EPSILON && swirlRadius > EPSILON
				? Math.abs(torque) / (diskMassFlow * swirlRadius)
				: 0.0;
		double swirlKineticPower = 0.5
				* diskMassFlow
				* tangentialWakeVelocity
				* tangentialWakeVelocity;
		double totalWakePower = idealMomentumPower + swirlKineticPower;
		double totalWakeOverShaft = ratio(totalWakePower, shaftPower);
		double swirlOverShaft = ratio(swirlKineticPower, shaftPower);
		double totalWakeResidual = shaftPower - totalWakePower;
		double totalWakeResidualFraction = ratio(totalWakeResidual, shaftPower);
		double shaftPowerResidual = shaftPower - idealMomentumPower;
		double shaftPowerResidualFraction = ratio(shaftPowerResidual, shaftPower);
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
				torqueCoefficientCq,
				diskArea,
				diskLoading,
				inducedVelocity,
				idealMomentumPower,
				usefulAxialThrustPower,
				idealInducedPower,
				axialPropulsiveEfficiency,
				momentumOverShaft,
				diskMassFlow,
				farWakeAxialVelocity,
				farWakeArea,
				farWakeRadius,
				farWakeAreaRatio,
				farWakeRadiusRatio,
				swirlRadius,
				tangentialWakeVelocity,
				swirlKineticPower,
				totalWakePower,
				totalWakeOverShaft,
				swirlOverShaft,
				totalWakeResidual,
				totalWakeResidualFraction,
				shaftPowerResidual,
				shaftPowerResidualFraction
		);
	}

	public static List<String> acceptedReferenceCaseNames(String presetName) {
		String normalizedPreset = normalizePreset(presetName);
		return ACCEPTED_REFERENCE_WINDOWS.stream()
				.filter(window -> window.presetName().equals(normalizedPreset))
				.map(ReferenceWindow::caseName)
				.toList();
	}

	public static List<LookupQuery> acceptedReferenceCurveQueries(
			String presetName,
			double propellerDiameterMeters,
			double airDensityKgPerCubicMeter
	) {
		String normalizedPreset = normalizePreset(presetName);
		List<LookupQuery> queries = new ArrayList<>();
		for (ReferenceWindow window : ACCEPTED_REFERENCE_WINDOWS) {
			if (!window.presetName().equals(normalizedPreset)) {
				continue;
			}
			List<Double> advanceRatios = window.sampleAdvanceRatios();
			List<Double> rpms = window.sampleRpms();
			for (double rpm : rpms) {
				for (double advanceRatioJ : advanceRatios) {
					queries.add(new LookupQuery(
							window.presetName(),
							window.caseName(),
							advanceRatioJ,
							rpm,
							propellerDiameterMeters,
							airDensityKgPerCubicMeter,
							EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
					));
				}
			}
		}
		if (queries.isEmpty()) {
			throw new IllegalArgumentException("no accepted CT/CP/J reference windows for preset: " + normalizedPreset);
		}
		return List.copyOf(queries);
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
				.min(Comparator
						.comparingDouble((ReferenceWindow window) ->
								window.outsideAdvanceDistanceSquared(query.advanceRatioJ()))
						.thenComparingDouble(window -> window.outsideRpmDistanceSquared(query.rpm()))
						.thenComparingDouble(window -> Math.abs(
								window.queryAdvanceRatioJ() - query.advanceRatioJ())))
				.orElse(null);
	}

	private static ReferenceWindow acceptedAdvanceShapeWindow(String presetName) {
		String normalizedPreset = normalizePreset(presetName);
		List<CoefficientGridRow> shapeRows = new ArrayList<>();
		for (ReferenceWindow window : ACCEPTED_REFERENCE_WINDOWS) {
			if (!window.presetName().equals(normalizedPreset)) {
				continue;
			}
			for (double advanceRatioJ : window.sampleAdvanceRatios()) {
				Bracket bracket = window.bracket(advanceRatioJ, window.queryRpm());
				if (!bracket.inside()) {
					continue;
				}
				List<CoefficientGridRow> neighbors = window.neighborRows(bracket);
				if (neighbors.size() < expectedNeighborRows(bracket)) {
					continue;
				}
				Coefficients coefficients = interpolateCoefficients(window, bracket);
				addShapeRow(shapeRows, row(
						window.caseName() + "-j" + shapeRows.size(),
						advanceRatioJ,
						ADVANCE_SHAPE_RPM,
						coefficients.ctCoefficient(),
						coefficients.cpCoefficient()
				));
			}
		}
		if (shapeRows.isEmpty()) {
			return null;
		}
		shapeRows.sort(Comparator.comparingDouble(CoefficientGridRow::advanceRatioJ));
		return new ReferenceWindow(
				normalizedPreset,
				"static_anchored_forward_shape",
				0.0,
				ADVANCE_SHAPE_RPM,
				shapeRows
		);
	}

	private static Coefficients acceptedStaticAnchorCoefficients(String presetName) {
		String normalizedPreset = normalizePreset(presetName);
		for (ReferenceWindow window : ACCEPTED_REFERENCE_WINDOWS) {
			if (!window.presetName().equals(normalizedPreset)
					|| !"static_anchor_low_rpm".equals(window.caseName())) {
				continue;
			}
			CoefficientGridRow row = window.row(0.0, window.queryRpm());
			if (row == null) {
				return null;
			}
			return new Coefficients(row.ctCoefficient(), row.cpCoefficient());
		}
		return null;
	}

	private static double globalAdvanceRatioScale(String presetName) {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (ReferenceWindow window : ACCEPTED_REFERENCE_WINDOWS) {
			if (!window.presetName().equals(presetName)) {
				continue;
			}
			min = Math.min(min, window.minAdvanceRatioJ());
			max = Math.max(max, window.maxAdvanceRatioJ());
		}
		if (!Double.isFinite(min) || !Double.isFinite(max)) {
			return 1.0;
		}
		return Math.max(EPSILON, max - min);
	}

	private static double globalRpmScale(String presetName) {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (ReferenceWindow window : ACCEPTED_REFERENCE_WINDOWS) {
			if (!window.presetName().equals(presetName)) {
				continue;
			}
			min = Math.min(min, window.minRpm());
			max = Math.max(max, window.maxRpm());
		}
		if (!Double.isFinite(min) || !Double.isFinite(max)) {
			return 1.0;
		}
		return Math.max(1.0, max - min);
	}

	private static LookupResult blocked(LookupQuery query, String status, String message) {
		return blocked(query, DATA_SOURCE_ID, status, message);
	}

	private static LookupResult staticAnchoredBlocked(LookupQuery query, String status, String message) {
		return blocked(query, STATIC_ANCHORED_DATA_SOURCE_ID, status, message);
	}

	private static LookupResult blocked(
			LookupQuery query,
			String dataSourceId,
			String status,
			String message
	) {
		return new LookupResult(
				query.presetName(),
				query.caseName(),
				dataSourceId,
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

	private static Coefficients interpolateCoefficients(ReferenceWindow window, Bracket bracket) {
		CoefficientGridRow j0r0 = window.row(bracket.lowerAdvanceRatioJ(), bracket.lowerRpm());
		CoefficientGridRow j1r0 = window.row(bracket.upperAdvanceRatioJ(), bracket.lowerRpm());
		CoefficientGridRow j0r1 = window.row(bracket.lowerAdvanceRatioJ(), bracket.upperRpm());
		CoefficientGridRow j1r1 = window.row(bracket.upperAdvanceRatioJ(), bracket.upperRpm());
		return interpolateCoefficients(j0r0, j1r0, j0r1, j1r1, bracket);
	}

	private static Coefficients interpolateCoefficients(
			CoefficientGridRow j0r0,
			CoefficientGridRow j1r0,
			CoefficientGridRow j0r1,
			CoefficientGridRow j1r1,
			Bracket bracket
	) {
		double ct = interpolate(bracket, j0r0.ctCoefficient(), j1r0.ctCoefficient(),
				j0r1.ctCoefficient(), j1r1.ctCoefficient());
		double cp = interpolate(bracket, j0r0.cpCoefficient(), j1r0.cpCoefficient(),
				j0r1.cpCoefficient(), j1r1.cpCoefficient());
		return new Coefficients(ct, cp);
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

	public static double torqueCoefficientCq(double powerCoefficientCp) {
		if (!Double.isFinite(powerCoefficientCp) || powerCoefficientCp <= 0.0) {
			return 0.0;
		}
		return powerCoefficientCp / (2.0 * Math.PI);
	}

	private static double axialMomentumInducedVelocity(
			double thrustNewtons,
			double airDensityKgPerCubicMeter,
			double diskAreaSquareMeters,
			double axialAdvanceSpeedMetersPerSecond
	) {
		if (thrustNewtons <= EPSILON
				|| airDensityKgPerCubicMeter <= EPSILON
				|| diskAreaSquareMeters <= EPSILON) {
			return 0.0;
		}
		double axialAdvanceSpeed = Double.isFinite(axialAdvanceSpeedMetersPerSecond)
				? Math.max(0.0, axialAdvanceSpeedMetersPerSecond)
				: 0.0;
		double diskTerm = 2.0 * thrustNewtons / (airDensityKgPerCubicMeter * diskAreaSquareMeters);
		return 0.5 * (Math.sqrt(axialAdvanceSpeed * axialAdvanceSpeed + diskTerm) - axialAdvanceSpeed);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private static LookupStatusCode lookupStatusCode(String status) {
		if (status == null || status.isBlank()) {
			return LookupStatusCode.UNKNOWN;
		}
		try {
			return LookupStatusCode.valueOf(status);
		} catch (IllegalArgumentException ignored) {
			return LookupStatusCode.UNKNOWN;
		}
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

	private record Coefficients(
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

		private boolean insideAdvanceRatio(double advanceRatioJ) {
			return advanceRatioJ >= minAdvanceRatioJ() - EPSILON
					&& advanceRatioJ <= maxAdvanceRatioJ() + EPSILON;
		}

		private double outsideAdvanceDistanceSquared(double advanceRatioJ) {
			double clampedJ = MathUtil.clamp(advanceRatioJ, minAdvanceRatioJ(), maxAdvanceRatioJ());
			double jScale = globalAdvanceRatioScale(presetName);
			double jDistance = (advanceRatioJ - clampedJ) / jScale;
			return jDistance * jDistance;
		}

		private double outsideRpmDistanceSquared(double rpm) {
			double clampedRpm = MathUtil.clamp(rpm, minRpm(), maxRpm());
			double rpmScale = globalRpmScale(presetName);
			double rpmDistance = (rpm - clampedRpm) / rpmScale;
			return rpmDistance * rpmDistance;
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
			return grid().advanceRatios();
		}

		private List<Double> rpms() {
			return grid().rpms();
		}

		private ReferenceWindowGrid grid() {
			return REFERENCE_WINDOW_GRIDS.computeIfAbsent(this, ReferenceWindowGrid::from);
		}

		private List<Double> sampleAdvanceRatios() {
			List<Double> values = new ArrayList<>(advanceRatios());
			addDistinct(values, queryAdvanceRatioJ);
			values.sort(Comparator.naturalOrder());
			return values;
		}

		private List<Double> sampleRpms() {
			List<Double> values = new ArrayList<>(rpms());
			addDistinct(values, queryRpm);
			values.sort(Comparator.naturalOrder());
			return values;
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

	private record ReferenceWindowGrid(
			List<Double> advanceRatios,
			List<Double> rpms
	) {
		private static ReferenceWindowGrid from(ReferenceWindow window) {
			return new ReferenceWindowGrid(
					distinctSorted(window.rows().stream().map(CoefficientGridRow::advanceRatioJ).toList()),
					distinctSorted(window.rows().stream().map(CoefficientGridRow::rpm).toList())
			);
		}
	}

	private static List<Double> distinctSorted(List<Double> rawValues) {
		List<Double> values = new ArrayList<>();
		for (double candidate : rawValues) {
			addDistinct(values, candidate);
		}
		values.sort(Comparator.naturalOrder());
		return values;
	}

	private static void addDistinct(List<Double> values, double candidate) {
		for (double value : values) {
			if (same(value, candidate)) {
				return;
			}
		}
		values.add(candidate);
	}

	private static void addIfPresent(List<CoefficientGridRow> rows, CoefficientGridRow row) {
		if (row != null && rows.stream().noneMatch(existing -> existing == row)) {
			rows.add(row);
		}
	}

	private static void addShapeRow(List<CoefficientGridRow> rows, CoefficientGridRow row) {
		for (CoefficientGridRow existing : rows) {
			if (same(existing.advanceRatioJ(), row.advanceRatioJ())) {
				return;
			}
		}
		rows.add(row);
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
