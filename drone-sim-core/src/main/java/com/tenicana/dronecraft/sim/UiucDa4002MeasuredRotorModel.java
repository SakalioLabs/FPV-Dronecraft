package com.tenicana.dronecraft.sim;

import java.util.Comparator;
import java.util.List;

import com.tenicana.dronecraft.sim.RotorHoverBladeProfilePowerModel.BladeGeometry;

/**
 * Propeller-specific measured CT/CP surface for the UIUC DA4002 propellers.
 * Static measurements anchor J=0. Advancing-flow runs are assembled at their
 * nominal test RPM, with overlapping low/high tunnel-speed runs blended over
 * their measured overlap, then interpolated only when nominal tracks bracket
 * the query RPM.
 * This is not a generic Reynolds correction or a substitute geometry model.
 */
public final class UiucDa4002MeasuredRotorModel {
	public static final String DATA_SOURCE_ID =
			"uiuc-propdb-volume-2-da4002-static-and-advance-surface";
	private static final double EPSILON = 1.0e-12;

	private UiucDa4002MeasuredRotorModel() {
	}

	public enum Propeller {
		DA4002_5X3_75(
				"da4002-5x3.75",
				UiucDa4002PropellerGeometry.fiveByThreePointSevenFiveTwoBlade(),
				UiucDa4002PropellerGeometry.FIVE_INCH_DIAMETER_METERS
		),
		DA4002_9X6_75(
				"da4002-9x6.75",
				UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade(),
				UiucDa4002PropellerGeometry.NINE_INCH_DIAMETER_METERS
		);

		private final String id;
		private final BladeGeometry geometry;
		private final double diameterMeters;

		Propeller(String id, BladeGeometry geometry, double diameterMeters) {
			this.id = id;
			this.geometry = geometry;
			this.diameterMeters = diameterMeters;
		}

		public String id() {
			return id;
		}

		public BladeGeometry geometry() {
			return geometry;
		}

		public double diameterMeters() {
			return diameterMeters;
		}

		public static Propeller fromId(String value) {
			String candidate = value == null ? "" : value.trim();
			for (Propeller propeller : values()) {
				if (propeller.name().equalsIgnoreCase(candidate)
						|| propeller.id.equalsIgnoreCase(candidate)) {
					return propeller;
				}
			}
			throw new IllegalArgumentException("Unknown UIUC DA4002 propeller: " + candidate);
		}
	}

	public enum TrackInterpolationStatus {
		STATIC,
		LINEAR_STATIC_TO_FIRST_ADVANCE,
		EXACT_ADVANCE_ROW,
		LINEAR_ADVANCE_RATIO,
		LINEAR_OVERLAPPING_RUNS,
		BLOCKED
	}

	public enum InterpolationStatus {
		STATIC_EXACT,
		LINEAR_STATIC_RPM,
		ADVANCE_EXACT,
		LINEAR_STATIC_TO_FIRST_ADVANCE,
		LINEAR_ADVANCE_RATIO,
		LINEAR_OVERLAPPING_RUNS,
		LINEAR_RPM_BETWEEN_TRACKS,
		BLOCKED
	}

	public enum PropulsiveRegime {
		STATIC_POSITIVE_THRUST,
		AXIAL_POSITIVE_THRUST,
		MEASURED_NON_POSITIVE_THRUST,
		BLOCKED
	}

	public static LookupResult evaluate(Propeller propeller, double advanceRatio, double rpm) {
		validateQuery(propeller, advanceRatio, rpm);
		Query query = new Query(propeller, advanceRatio, rpm);
		if (advanceRatio <= EPSILON) {
			return evaluateStatic(query);
		}

		List<TrackGroup> groups = advanceTrackGroups(propeller);
		for (TrackGroup group : groups) {
			if (Double.compare(rpm, group.nominalRpm()) == 0) {
				TrackSample sample = groupTrackSample(group, advanceRatio);
				return sample == null
						? blocked(query, "query-outside-uiuc-da4002-nominal-track-j-envelope")
						: result(query, sample, sample, 0.0,
								statusForExactTrack(sample.status()));
			}
		}

		TrackGroup lowerGroup = null;
		TrackGroup upperGroup = null;
		for (TrackGroup group : groups) {
			if (group.nominalRpm() < rpm) {
				lowerGroup = group;
				continue;
			}
			if (group.nominalRpm() > rpm) {
				upperGroup = group;
				break;
			}
		}
		if (lowerGroup == null || upperGroup == null) {
			return blocked(query, "query-outside-uiuc-da4002-forward-rpm-envelope");
		}
		TrackSample lower = groupTrackSample(lowerGroup, advanceRatio);
		TrackSample upper = groupTrackSample(upperGroup, advanceRatio);
		if (lower == null || upper == null) {
			return blocked(query,
					"query-not-supported-by-both-adjacent-uiuc-da4002-rpm-tracks");
		}
		double fraction = (rpm - lower.rpm()) / (upper.rpm() - lower.rpm());
		return result(
				query,
				lower,
				upper,
				fraction,
				InterpolationStatus.LINEAR_RPM_BETWEEN_TRACKS
		);
	}

	public static DimensionalSample sample(
			Propeller propeller,
			double advanceRatio,
			double rpm,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds
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
		LookupResult lookup = evaluate(propeller, advanceRatio, rpm);
		double diameter = propeller.diameterMeters();
		double radius = diameter * 0.5;
		double diskArea = Math.PI * radius * radius;
		double revolutionsPerSecond = rpm / 60.0;
		double angularVelocity = 2.0 * Math.PI * revolutionsPerSecond;
		double axialFreestreamVelocity = advanceRatio * revolutionsPerSecond * diameter;
		double chord75 = propeller.geometry().chordToRadiusAt(0.75) * radius;
		double rotationalSpeed75 = 0.75 * angularVelocity * radius;
		double relativeSpeed75 = Math.hypot(
				rotationalSpeed75,
				axialFreestreamVelocity
		);
		double sourceRotationalReynolds75 = airDensityKgPerCubicMeter
				* rotationalSpeed75
				* chord75
				/ dynamicViscosityPascalSeconds;
		double resultantSectionReynolds75 = airDensityKgPerCubicMeter
				* relativeSpeed75
				* chord75
				/ dynamicViscosityPascalSeconds;
		if (lookup.blocked()) {
			return new DimensionalSample(
					lookup,
					PropulsiveRegime.BLOCKED,
					airDensityKgPerCubicMeter,
					dynamicViscosityPascalSeconds,
					diameter,
					radius,
					diskArea,
					revolutionsPerSecond,
					angularVelocity,
					axialFreestreamVelocity,
					chord75,
					rotationalSpeed75,
					relativeSpeed75,
					sourceRotationalReynolds75,
					resultantSectionReynolds75,
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

		double thrust = lookup.thrustCoefficientCt()
				* airDensityKgPerCubicMeter
				* Math.pow(revolutionsPerSecond, 2.0)
				* Math.pow(diameter, 4.0);
		double shaftPower = lookup.powerCoefficientCp()
				* airDensityKgPerCubicMeter
				* Math.pow(revolutionsPerSecond, 3.0)
				* Math.pow(diameter, 5.0);
		double shaftTorque = shaftPower / angularVelocity;
		double usefulPower = thrust * axialFreestreamVelocity;
		double diskLoading = thrust / diskArea;
		boolean positiveThrust = thrust > EPSILON;
		double idealInducedVelocity = positiveThrust
				? axialInductionForPositiveThrust(
						axialFreestreamVelocity,
						thrust,
						2.0 * airDensityKgPerCubicMeter * diskArea
				)
				: 0.0;
		double idealInducedPower = positiveThrust ? thrust * idealInducedVelocity : 0.0;
		double idealMomentumPower = positiveThrust
				? usefulPower + idealInducedPower
				: 0.0;
		PropulsiveRegime regime = positiveThrust
				? advanceRatio <= EPSILON
						? PropulsiveRegime.STATIC_POSITIVE_THRUST
						: PropulsiveRegime.AXIAL_POSITIVE_THRUST
				: PropulsiveRegime.MEASURED_NON_POSITIVE_THRUST;
		return new DimensionalSample(
				lookup,
				regime,
				airDensityKgPerCubicMeter,
				dynamicViscosityPascalSeconds,
				diameter,
				radius,
				diskArea,
				revolutionsPerSecond,
				angularVelocity,
				axialFreestreamVelocity,
				chord75,
				rotationalSpeed75,
				relativeSpeed75,
				sourceRotationalReynolds75,
				resultantSectionReynolds75,
				thrust,
				shaftPower,
				shaftTorque,
				lookup.powerCoefficientCp() / (2.0 * Math.PI),
				usefulPower,
				diskLoading,
				idealInducedVelocity,
				idealInducedPower,
				idealMomentumPower,
				ratio(idealMomentumPower, shaftPower)
		);
	}

	private static LookupResult evaluateStatic(Query query) {
		UiucDa4002StaticPerformanceLookup.LookupResult staticLookup =
				UiucDa4002StaticPerformanceLookup.evaluate(
						staticCurve(query.propeller()),
						query.rpm(),
						UiucDa4002StaticPerformanceLookup.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		if (staticLookup.blocked()) {
			return blocked(query, "query-outside-uiuc-da4002-static-rpm-envelope");
		}
		TrackSample lower = new TrackSample(
				staticLookup.curve().id(),
				staticLookup.lowerRpm(),
				TrackInterpolationStatus.STATIC,
				0.0,
				0.0,
				0.0,
				staticLookup.thrustCoefficientCt(),
				staticLookup.powerCoefficientCp()
		);
		TrackSample upper = new TrackSample(
				staticLookup.curve().id(),
				staticLookup.upperRpm(),
				TrackInterpolationStatus.STATIC,
				0.0,
				0.0,
				0.0,
				staticLookup.thrustCoefficientCt(),
				staticLookup.powerCoefficientCp()
		);
		return new LookupResult(
				query,
				lower.rpm(),
				upper.rpm(),
				staticLookup.interpolationFraction(),
				lower.sourceCurveId(),
				upper.sourceCurveId(),
				lower.status(),
				upper.status(),
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				staticLookup.thrustCoefficientCt(),
				staticLookup.powerCoefficientCp(),
				0.0,
				staticLookup.interpolationStatus()
						== UiucDa4002StaticPerformanceLookup.InterpolationStatus.EXACT
								? InterpolationStatus.STATIC_EXACT
								: InterpolationStatus.LINEAR_STATIC_RPM,
				false,
				false,
				false,
				"uiuc-da4002-static-surface-solved"
		);
	}

	private static TrackSample directTrackSample(
			UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve,
			double advanceRatio,
			double nominalRpm
	) {
		UiucDa4002AdvancePerformanceLookup.LookupResult lookup =
				UiucDa4002AdvancePerformanceLookup.evaluate(
						curve,
						advanceRatio,
						UiucDa4002AdvancePerformanceLookup.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		if (lookup.blocked()) {
			throw new IllegalStateException("eligible UIUC advance track unexpectedly blocked.");
		}
		TrackInterpolationStatus status = lookup.interpolationStatus()
				== UiucDa4002AdvancePerformanceLookup.InterpolationStatus.EXACT
						? TrackInterpolationStatus.EXACT_ADVANCE_ROW
						: TrackInterpolationStatus.LINEAR_ADVANCE_RATIO;
		return new TrackSample(
				curve.id(),
				nominalRpm,
				status,
				lookup.lowerAdvanceRatioJ(),
				lookup.upperAdvanceRatioJ(),
				lookup.interpolationFraction(),
				lookup.thrustCoefficientCt(),
				lookup.powerCoefficientCp()
		);
	}

	private static TrackSample staticBridgeSample(
			UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve,
			double advanceRatio,
			double nominalRpm
	) {
		UiucDa4002StaticPerformanceLookup.LookupResult staticLookup =
				UiucDa4002StaticPerformanceLookup.evaluate(
						staticCurveForGeometry(curve.geometry()),
						nominalRpm,
						UiucDa4002StaticPerformanceLookup.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		if (staticLookup.blocked()) {
			throw new IllegalStateException("advance track RPM is outside its static curve envelope.");
		}
		UiucDa4002AdvancePerformanceLookup.AdvanceRow first = curve.rows().get(0);
		double fraction = advanceRatio / first.advanceRatioJ();
		return new TrackSample(
				curve.id(),
				nominalRpm,
				TrackInterpolationStatus.LINEAR_STATIC_TO_FIRST_ADVANCE,
				0.0,
				first.advanceRatioJ(),
				fraction,
				lerp(staticLookup.thrustCoefficientCt(), first.thrustCoefficientCt(), fraction),
				lerp(staticLookup.powerCoefficientCp(), first.powerCoefficientCp(), fraction)
		);
	}

	private static TrackSample groupTrackSample(TrackGroup group, double advanceRatio) {
		List<UiucDa4002AdvancePerformanceLookup.AdvanceCurve> curves = group.curves();
		if (curves.size() == 1) {
			UiucDa4002AdvancePerformanceLookup.AdvanceCurve curve = curves.get(0);
			if (advanceRatio < curve.minimumAdvanceRatio()) {
				return staticBridgeSample(curve, advanceRatio, group.nominalRpm());
			}
			return advanceRatio <= curve.maximumAdvanceRatio()
					? directTrackSample(curve, advanceRatio, group.nominalRpm())
					: null;
		}

		List<UiucDa4002AdvancePerformanceLookup.AdvanceCurve> ordered = curves.stream()
				.sorted(Comparator.comparingDouble(
						UiucDa4002AdvancePerformanceLookup.AdvanceCurve::minimumAdvanceRatio
				))
				.toList();
		UiucDa4002AdvancePerformanceLookup.AdvanceCurve lowSpeedRun = ordered.get(0);
		UiucDa4002AdvancePerformanceLookup.AdvanceCurve highSpeedRun = ordered.get(1);
		double overlapLower = highSpeedRun.minimumAdvanceRatio();
		double overlapUpper = lowSpeedRun.maximumAdvanceRatio();
		if (curves.size() != 2 || overlapLower >= overlapUpper) {
			throw new IllegalStateException(
					"UIUC nominal RPM track requires two overlapping tunnel-speed runs."
			);
		}
		if (advanceRatio < lowSpeedRun.minimumAdvanceRatio()) {
			return staticBridgeSample(lowSpeedRun, advanceRatio, group.nominalRpm());
		}
		if (advanceRatio < overlapLower) {
			return directTrackSample(lowSpeedRun, advanceRatio, group.nominalRpm());
		}
		if (advanceRatio <= overlapUpper) {
			TrackSample lower = directTrackSample(
					lowSpeedRun,
					advanceRatio,
					group.nominalRpm()
			);
			TrackSample upper = directTrackSample(
					highSpeedRun,
					advanceRatio,
					group.nominalRpm()
			);
			double fraction = (advanceRatio - overlapLower) / (overlapUpper - overlapLower);
			return new TrackSample(
					lowSpeedRun.id() + "+" + highSpeedRun.id(),
					group.nominalRpm(),
					TrackInterpolationStatus.LINEAR_OVERLAPPING_RUNS,
					overlapLower,
					overlapUpper,
					fraction,
					lerp(lower.thrustCoefficientCt(), upper.thrustCoefficientCt(), fraction),
					lerp(lower.powerCoefficientCp(), upper.powerCoefficientCp(), fraction)
			);
		}
		return advanceRatio <= highSpeedRun.maximumAdvanceRatio()
				? directTrackSample(highSpeedRun, advanceRatio, group.nominalRpm())
				: null;
	}

	private static LookupResult result(
			Query query,
			TrackSample lower,
			TrackSample upper,
			double rpmFraction,
			InterpolationStatus interpolationStatus
	) {
		double thrustCoefficient = lerp(
				lower.thrustCoefficientCt(),
				upper.thrustCoefficientCt(),
				rpmFraction
		);
		double powerCoefficient = lerp(
				lower.powerCoefficientCp(),
				upper.powerCoefficientCp(),
				rpmFraction
		);
		return new LookupResult(
				query,
				lower.rpm(),
				upper.rpm(),
				rpmFraction,
				lower.sourceCurveId(),
				upper.sourceCurveId(),
				lower.status(),
				upper.status(),
				lower.lowerAdvanceRatioJ(),
				lower.upperAdvanceRatioJ(),
				lower.advanceInterpolationFraction(),
				upper.lowerAdvanceRatioJ(),
				upper.upperAdvanceRatioJ(),
				upper.advanceInterpolationFraction(),
				thrustCoefficient,
				powerCoefficient,
				ratio(thrustCoefficient * query.advanceRatioJ(), powerCoefficient),
				interpolationStatus,
				false,
				false,
				false,
				"uiuc-da4002-forward-measured-surface-solved"
		);
	}

	private static LookupResult blocked(Query query, String message) {
		return new LookupResult(
				query,
				0.0,
				0.0,
				0.0,
				"",
				"",
				TrackInterpolationStatus.BLOCKED,
				TrackInterpolationStatus.BLOCKED,
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
				true,
				false,
				true,
				message
		);
	}

	private static InterpolationStatus statusForExactTrack(
			TrackInterpolationStatus trackStatus
	) {
		return switch (trackStatus) {
			case EXACT_ADVANCE_ROW -> InterpolationStatus.ADVANCE_EXACT;
			case LINEAR_ADVANCE_RATIO -> InterpolationStatus.LINEAR_ADVANCE_RATIO;
			case LINEAR_STATIC_TO_FIRST_ADVANCE ->
					InterpolationStatus.LINEAR_STATIC_TO_FIRST_ADVANCE;
			case LINEAR_OVERLAPPING_RUNS -> InterpolationStatus.LINEAR_OVERLAPPING_RUNS;
			case STATIC -> InterpolationStatus.STATIC_EXACT;
			case BLOCKED -> InterpolationStatus.BLOCKED;
		};
	}

	private static UiucDa4002StaticPerformanceLookup.StaticCurve staticCurve(
			Propeller propeller
	) {
		return switch (propeller) {
			case DA4002_5X3_75 ->
					UiucDa4002StaticPerformanceLookup.fiveByThreePointSevenFive();
			case DA4002_9X6_75 ->
					UiucDa4002StaticPerformanceLookup.nineBySixPointSevenFive();
		};
	}

	private static UiucDa4002StaticPerformanceLookup.StaticCurve staticCurveForGeometry(
			BladeGeometry geometry
	) {
		if (geometry.id().equals(
				UiucDa4002PropellerGeometry.fiveByThreePointSevenFiveTwoBlade().id())) {
			return UiucDa4002StaticPerformanceLookup.fiveByThreePointSevenFive();
		}
		if (geometry.id().equals(
				UiucDa4002PropellerGeometry.nineBySixPointSevenFiveTwoBlade().id())) {
			return UiucDa4002StaticPerformanceLookup.nineBySixPointSevenFive();
		}
		throw new IllegalArgumentException("geometry is not a supported UIUC DA4002 propeller.");
	}

	private static List<TrackGroup>
			advanceTrackGroups(Propeller propeller) {
		if (propeller == Propeller.DA4002_5X3_75) {
			List<UiucDa4002AdvancePerformanceLookup.AdvanceCurve> curves =
					UiucDa4002AdvancePerformanceLookup.fiveInchCurves();
			return List.of(
					new TrackGroup(4_000.0, List.of(curves.get(0))),
					new TrackGroup(5_000.0, List.of(curves.get(1))),
					new TrackGroup(6_000.0, List.of(curves.get(2)))
			);
		}
		List<UiucDa4002AdvancePerformanceLookup.AdvanceCurve> curves =
				UiucDa4002AdvancePerformanceLookup.nineInchCurves();
		return List.of(
				new TrackGroup(2_000.0, List.of(curves.get(0))),
				new TrackGroup(3_000.0, List.of(curves.get(1))),
				new TrackGroup(4_000.0, List.of(curves.get(2), curves.get(3))),
				new TrackGroup(5_000.0, List.of(curves.get(4), curves.get(5)))
		);
	}

	private static void validateQuery(Propeller propeller, double advanceRatio, double rpm) {
		if (propeller == null) {
			throw new IllegalArgumentException("propeller must not be null.");
		}
		if (!Double.isFinite(advanceRatio) || advanceRatio < 0.0) {
			throw new IllegalArgumentException("advanceRatio must be finite and non-negative.");
		}
		if (!Double.isFinite(rpm) || rpm <= 0.0) {
			throw new IllegalArgumentException("rpm must be finite and positive.");
		}
	}

	private static double axialInductionForPositiveThrust(
			double axialFreestreamVelocityMetersPerSecond,
			double thrust,
			double momentumDenominator
	) {
		double momentumLoad = thrust / momentumDenominator;
		double radical = Math.sqrt(
				axialFreestreamVelocityMetersPerSecond
						* axialFreestreamVelocityMetersPerSecond
						+ 4.0 * momentumLoad
		);
		return 2.0 * momentumLoad
				/ Math.max(EPSILON, radical + axialFreestreamVelocityMetersPerSecond);
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

	private record TrackSample(
			String sourceCurveId,
			double rpm,
			TrackInterpolationStatus status,
			double lowerAdvanceRatioJ,
			double upperAdvanceRatioJ,
			double advanceInterpolationFraction,
			double thrustCoefficientCt,
			double powerCoefficientCp
	) {
	}

	private record TrackGroup(
			double nominalRpm,
			List<UiucDa4002AdvancePerformanceLookup.AdvanceCurve> curves
	) {
		private TrackGroup {
			curves = List.copyOf(curves);
		}
	}

	public record Query(Propeller propeller, double advanceRatioJ, double rpm) {
	}

	public record LookupResult(
			Query query,
			double lowerRpm,
			double upperRpm,
			double rpmInterpolationFraction,
			String lowerSourceCurveId,
			String upperSourceCurveId,
			TrackInterpolationStatus lowerTrackInterpolationStatus,
			TrackInterpolationStatus upperTrackInterpolationStatus,
			double lowerTrackLowerAdvanceRatioJ,
			double lowerTrackUpperAdvanceRatioJ,
			double lowerTrackAdvanceInterpolationFraction,
			double upperTrackLowerAdvanceRatioJ,
			double upperTrackUpperAdvanceRatioJ,
			double upperTrackAdvanceInterpolationFraction,
			double thrustCoefficientCt,
			double powerCoefficientCp,
			double propulsiveEfficiencyEta,
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
			PropulsiveRegime propulsiveRegime,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			double propellerDiameterMeters,
			double rotorRadiusMeters,
			double diskAreaSquareMeters,
			double revolutionsPerSecond,
			double angularVelocityRadiansPerSecond,
			double axialFreestreamVelocityMetersPerSecond,
			double chordAtSeventyFivePercentRadiusMeters,
			double rotationalSpeedAtSeventyFivePercentRadiusMetersPerSecond,
			double relativeSpeedAtSeventyFivePercentRadiusMetersPerSecond,
			double sourceRotationalReynoldsNumberAtSeventyFivePercentRadius,
			double resultantSectionReynoldsNumberAtSeventyFivePercentRadius,
			double thrustNewtons,
			double shaftPowerWatts,
			double shaftTorqueNewtonMeters,
			double torqueCoefficientCq,
			double usefulPropulsivePowerWatts,
			double signedDiskLoadingNewtonsPerSquareMeter,
			double idealInducedVelocityMetersPerSecond,
			double idealInducedPowerWatts,
			double idealMomentumPowerWatts,
			double idealMomentumPowerOverShaftPower
	) {
		public boolean blocked() {
			return lookup.blocked();
		}

		public boolean positiveThrustRuntimeEligible() {
			return propulsiveRegime == PropulsiveRegime.STATIC_POSITIVE_THRUST
					|| propulsiveRegime == PropulsiveRegime.AXIAL_POSITIVE_THRUST;
		}
	}
}
