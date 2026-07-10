package com.tenicana.dronecraft.sim;

/**
 * Semi-empirical rotational augmentation for a propeller blade section.
 * The model augments the section coefficient normal to the chord using
 * McCrink's propeller form of the Snel correction, then preserves the 2-D
 * chordwise coefficient while rotating the corrected pair back to lift/drag.
 */
public final class SnelMcCrinkRotationalAugmentation {
	public static final String DATA_SOURCE_ID =
			"snel-mccrink-normal-force-aiaa-2015-3296-equation-9";
	public static final String CONFERENCE_PAPER_URL = "https://doi.org/10.2514/6.2015-3296";
	public static final String PUBLIC_DISSERTATION_URL =
			"https://etd.ohiolink.edu/acprod/odb_etd/etd/r/1501/10"
					+ "?clear=10&p10_accession_num=osu1449142886";
	public static final double MAX_APPLIED_RADIAL_FRACTION = 0.85;
	public static final double CORRECTION_SCALE = 1.5;
	public static final double THIN_AIRFOIL_LIFT_SLOPE_PER_RADIAN = 2.0 * Math.PI;
	private static final double EPSILON = 1.0e-12;

	private SnelMcCrinkRotationalAugmentation() {
	}

	public enum Policy {
		NONE,
		SNEL_MCCRINK_NORMAL_FORCE
	}

	public enum Status {
		POLICY_DISABLED,
		OUTBOARD_OF_SOURCE_SPAN_LIMIT,
		APPLIED
	}

	public static Sample evaluate(Query query, Policy policy) {
		if (query == null) {
			throw new IllegalArgumentException("query must not be null.");
		}
		if (policy == null) {
			policy = Policy.NONE;
		}

		double angle = query.angleOfAttackRadians();
		double cosine = Math.cos(angle);
		double sine = Math.sin(angle);
		double normal2d = query.liftCoefficientCl2d() * cosine
				+ query.dragCoefficientCd2d() * sine;
		double chordwise2d = query.dragCoefficientCd2d() * cosine
				- query.liftCoefficientCl2d() * sine;
		double potentialLift = THIN_AIRFOIL_LIFT_SLOPE_PER_RADIAN * angle;
		double rotationToRelativeSpeed = query.bladeTangentialSpeedMetersPerSecond()
				/ query.localRelativeSpeedMetersPerSecond();

		Status status;
		double correctionFactor;
		if (policy == Policy.NONE) {
			status = Status.POLICY_DISABLED;
			correctionFactor = 0.0;
		} else if (query.radialFraction() > MAX_APPLIED_RADIAL_FRACTION) {
			status = Status.OUTBOARD_OF_SOURCE_SPAN_LIMIT;
			correctionFactor = 0.0;
		} else {
			status = Status.APPLIED;
			correctionFactor = CORRECTION_SCALE
					* query.chordOverLocalRadius()
					* query.chordOverLocalRadius()
					* rotationToRelativeSpeed
					* rotationToRelativeSpeed;
		}

		double normalDelta = correctionFactor
				* (potentialLift - query.liftCoefficientCl2d());
		double normal3d = normal2d + normalDelta;
		double correctedLift = status == Status.APPLIED
				? normal3d * cosine - chordwise2d * sine
				: query.liftCoefficientCl2d();
		double correctedDrag = status == Status.APPLIED
				? normal3d * sine + chordwise2d * cosine
				: query.dragCoefficientCd2d();
		if (!Double.isFinite(correctedLift) || !Double.isFinite(correctedDrag)) {
			throw new IllegalArgumentException("rotational augmentation produced non-finite output.");
		}
		if (correctedDrag < -EPSILON) {
			throw new IllegalArgumentException(
					"rotational augmentation produced a negative drag coefficient."
			);
		}
		correctedDrag = Math.max(0.0, correctedDrag);
		return new Sample(
				policy,
				status,
				query,
				potentialLift,
				normal2d,
				chordwise2d,
				rotationToRelativeSpeed,
				correctionFactor,
				normalDelta,
				normal3d,
				correctedLift,
				correctedDrag,
				correctedLift - query.liftCoefficientCl2d(),
				correctedDrag - query.dragCoefficientCd2d()
		);
	}

	public record Query(
			double liftCoefficientCl2d,
			double dragCoefficientCd2d,
			double angleOfAttackRadians,
			double radialFraction,
			double chordOverLocalRadius,
			double bladeTangentialSpeedMetersPerSecond,
			double localRelativeSpeedMetersPerSecond
	) {
		public Query {
			if (!Double.isFinite(liftCoefficientCl2d)) {
				throw new IllegalArgumentException("liftCoefficientCl2d must be finite.");
			}
			if (!Double.isFinite(dragCoefficientCd2d) || dragCoefficientCd2d < 0.0) {
				throw new IllegalArgumentException(
						"dragCoefficientCd2d must be finite and non-negative."
				);
			}
			if (!Double.isFinite(angleOfAttackRadians)
					|| Math.abs(angleOfAttackRadians) > 0.5 * Math.PI) {
				throw new IllegalArgumentException(
						"angleOfAttackRadians must be finite and within +/- pi/2."
				);
			}
			if (!Double.isFinite(radialFraction)
					|| radialFraction <= 0.0
					|| radialFraction > 1.0) {
				throw new IllegalArgumentException("radialFraction must be in (0, 1].");
			}
			if (!Double.isFinite(chordOverLocalRadius)
					|| chordOverLocalRadius <= 0.0
					|| chordOverLocalRadius > 1.0) {
				throw new IllegalArgumentException(
						"chordOverLocalRadius must be in (0, 1]."
				);
			}
			if (!Double.isFinite(bladeTangentialSpeedMetersPerSecond)
					|| bladeTangentialSpeedMetersPerSecond <= 0.0) {
				throw new IllegalArgumentException(
						"bladeTangentialSpeedMetersPerSecond must be finite and positive."
				);
			}
			if (!Double.isFinite(localRelativeSpeedMetersPerSecond)
					|| localRelativeSpeedMetersPerSecond <= 0.0) {
				throw new IllegalArgumentException(
						"localRelativeSpeedMetersPerSecond must be finite and positive."
				);
			}
		}
	}

	public record Sample(
			Policy policy,
			Status status,
			Query query,
			double potentialFlowLiftCoefficientCl,
			double normalForceCoefficientCn2d,
			double chordwiseForceCoefficientCa2d,
			double rotationToRelativeSpeedRatio,
			double correctionFactor,
			double normalForceCoefficientDelta,
			double normalForceCoefficientCn3d,
			double correctedLiftCoefficientCl,
			double correctedDragCoefficientCd,
			double liftCoefficientDelta,
			double dragCoefficientDelta
	) {
		public boolean applied() {
			return status == Status.APPLIED;
		}

		public boolean sourceSpanLimited() {
			return status == Status.OUTBOARD_OF_SOURCE_SPAN_LIMIT;
		}

		public boolean requiresPropellerSpecificValidation() {
			return policy == Policy.SNEL_MCCRINK_NORMAL_FORCE;
		}
	}
}
