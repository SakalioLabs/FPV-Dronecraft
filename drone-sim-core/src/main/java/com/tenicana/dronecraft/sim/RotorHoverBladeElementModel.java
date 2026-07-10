package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

import com.tenicana.dronecraft.sim.RotorHoverBladeProfilePowerModel.BladeGeometry;

/**
 * Hover blade-element/momentum model with annular induced-flow solves.
 * Each annulus closes blade-element thrust against hover momentum theory and
 * applies the Prandtl tip-loss factor. Section lift and drag come from the
 * explicitly bounded SDA1075 polar; no coefficient is tuned to static data.
 */
public final class RotorHoverBladeElementModel {
	public static final int DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL = 8;
	private static final int MAX_ANNULI_PER_GEOMETRY_INTERVAL = 64;
	private static final int MAX_ROOT_BRACKET_EXPANSIONS = 20;
	private static final int MAX_ROOT_ITERATIONS = 80;
	private static final double EPSILON = 1.0e-12;

	private RotorHoverBladeElementModel() {
	}

	public enum Status {
		SOLVED,
		BLOCKED_SECTION_POLAR_ENVELOPE,
		BLOCKED_ANNULAR_MOMENTUM_ROOT
	}

	public static HoverSample solve(HoverQuery query) {
		if (query == null) {
			throw new IllegalArgumentException("query must not be null.");
		}
		BladeGeometry geometry = query.geometry();
		List<AnnulusSample> annuli = new ArrayList<>();
		for (int stationIndex = 1; stationIndex < geometry.stations().size(); stationIndex++) {
			double geometryInnerFraction = geometry.stations().get(stationIndex - 1).radialFraction();
			double geometryOuterFraction = geometry.stations().get(stationIndex).radialFraction();
			for (int subdivision = 0; subdivision < query.annuliPerGeometryInterval(); subdivision++) {
				double innerFraction = lerp(
						geometryInnerFraction,
						geometryOuterFraction,
						(double) subdivision / query.annuliPerGeometryInterval()
				);
				double outerFraction = lerp(
						geometryInnerFraction,
						geometryOuterFraction,
						(double) (subdivision + 1) / query.annuliPerGeometryInterval()
				);
				double radialFraction = 0.5 * (innerFraction + outerFraction);
				double radius = radialFraction * query.rotorRadiusMeters();
				double radialWidth = (outerFraction - innerFraction) * query.rotorRadiusMeters();
				double chord = geometry.chordToRadiusAt(radialFraction)
						* query.rotorRadiusMeters();
				double pitchAngle = geometry.pitchAngleRadiansAt(radialFraction);

				RootSolve root = solveAnnularInducedVelocity(
						query,
						radialFraction,
						radius,
						chord,
						pitchAngle
				);
				if (!root.solved()) {
					return blocked(
							query,
							Status.BLOCKED_ANNULAR_MOMENTUM_ROOT,
							annuli,
							"no-positive-annular-momentum-root-at-r-over-r=" + radialFraction
					);
				}

				SectionEvaluation section = evaluateSection(
						query,
						radialFraction,
						radius,
						chord,
						pitchAngle,
						root.inducedVelocityMetersPerSecond(),
						query.polarEnvelopePolicy()
				);
				AnnulusSample annulus = annulusSample(
						innerFraction,
						outerFraction,
						radialFraction,
						radius,
						radialWidth,
						chord,
						pitchAngle,
						root.inducedVelocityMetersPerSecond(),
						section,
						root.iterations()
				);
				annuli.add(annulus);
				if (section.polar().blocked()) {
					return blocked(
							query,
							Status.BLOCKED_SECTION_POLAR_ENVELOPE,
							annuli,
							"section-polar-envelope-blocked-at-r-over-r=" + radialFraction
					);
				}
			}
		}
		return aggregate(query, annuli);
	}

	private static RootSolve solveAnnularInducedVelocity(
			HoverQuery query,
			double radialFraction,
			double radiusMeters,
			double chordMeters,
			double pitchAngleRadians
	) {
		SectionEvaluation lowerEvaluation = evaluateSection(
				query,
				radialFraction,
				radiusMeters,
				chordMeters,
				pitchAngleRadians,
				0.0,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
		);
		if (lowerEvaluation.momentumClosureResidualNewtonsPerMeter() <= 0.0) {
			return new RootSolve(false, 0.0, 0);
		}

		double lowerVelocity = 0.0;
		double upperVelocity = Math.max(
				1.0,
				query.angularVelocityRadiansPerSecond() * query.rotorRadiusMeters()
		);
		SectionEvaluation upperEvaluation = evaluateSection(
				query,
				radialFraction,
				radiusMeters,
				chordMeters,
				pitchAngleRadians,
				upperVelocity,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
		);
		int bracketExpansions = 0;
		while (upperEvaluation.momentumClosureResidualNewtonsPerMeter() > 0.0
				&& bracketExpansions < MAX_ROOT_BRACKET_EXPANSIONS) {
			upperVelocity *= 2.0;
			upperEvaluation = evaluateSection(
					query,
					radialFraction,
					radiusMeters,
					chordMeters,
					pitchAngleRadians,
					upperVelocity,
					Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
			);
			bracketExpansions++;
		}
		if (upperEvaluation.momentumClosureResidualNewtonsPerMeter() > 0.0) {
			return new RootSolve(false, 0.0, 0);
		}

		int iterations = 0;
		for (; iterations < MAX_ROOT_ITERATIONS; iterations++) {
			double midpointVelocity = 0.5 * (lowerVelocity + upperVelocity);
			if (midpointVelocity == lowerVelocity || midpointVelocity == upperVelocity) {
				break;
			}
			SectionEvaluation midpointEvaluation = evaluateSection(
					query,
					radialFraction,
					radiusMeters,
					chordMeters,
					pitchAngleRadians,
					midpointVelocity,
					Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
			);
			if (midpointEvaluation.momentumClosureResidualNewtonsPerMeter() > 0.0) {
				lowerVelocity = midpointVelocity;
			} else {
				upperVelocity = midpointVelocity;
			}
		}
		return new RootSolve(true, 0.5 * (lowerVelocity + upperVelocity), iterations);
	}

	private static SectionEvaluation evaluateSection(
			HoverQuery query,
			double radialFraction,
			double radiusMeters,
			double chordMeters,
			double pitchAngleRadians,
			double inducedVelocityMetersPerSecond,
			Sda1075XfoilSectionPolar.EnvelopePolicy envelopePolicy
	) {
		double tangentialSpeed = query.angularVelocityRadiansPerSecond() * radiusMeters;
		double relativeSpeed = Math.hypot(tangentialSpeed, inducedVelocityMetersPerSecond);
		double inflowAngle = Math.atan2(inducedVelocityMetersPerSecond, tangentialSpeed);
		double angleOfAttack = pitchAngleRadians - inflowAngle;
		double reynoldsNumber = query.airDensityKgPerCubicMeter()
				* relativeSpeed
				* chordMeters
				/ query.dynamicViscosityPascalSeconds();
		Sda1075XfoilSectionPolar.PolarSample polar =
				Sda1075XfoilSectionPolar.evaluateRadians(
						reynoldsNumber,
						angleOfAttack,
						envelopePolicy
				);
		double tipLossFactor = prandtlTipLossFactor(
				query.geometry().bladeCount(),
				radialFraction,
				inflowAngle
		);
		double dynamicPressure = 0.5
				* query.airDensityKgPerCubicMeter()
				* relativeSpeed
				* relativeSpeed;
		double bladeCountDynamicPressureChord = query.geometry().bladeCount()
				* dynamicPressure
				* chordMeters;
		double differentialLift = bladeCountDynamicPressureChord * polar.liftCoefficientCl();
		double differentialDrag = bladeCountDynamicPressureChord * polar.dragCoefficientCd();
		double differentialThrust = differentialLift * Math.cos(inflowAngle)
				- differentialDrag * Math.sin(inflowAngle);
		double differentialLiftTorque = radiusMeters
				* differentialLift
				* Math.sin(inflowAngle);
		double differentialProfileTorque = radiusMeters
				* differentialDrag
				* Math.cos(inflowAngle);
		double differentialMomentumThrust = 4.0
				* Math.PI
				* query.airDensityKgPerCubicMeter()
				* tipLossFactor
				* radiusMeters
				* inducedVelocityMetersPerSecond
				* inducedVelocityMetersPerSecond;
		return new SectionEvaluation(
				tangentialSpeed,
				relativeSpeed,
				inflowAngle,
				angleOfAttack,
				reynoldsNumber,
				polar,
				tipLossFactor,
				differentialThrust,
				differentialMomentumThrust,
				differentialThrust - differentialMomentumThrust,
				differentialLiftTorque,
				differentialProfileTorque
		);
	}

	private static double prandtlTipLossFactor(
			int bladeCount,
			double radialFraction,
			double inflowAngleRadians
	) {
		double sineInflow = Math.sin(inflowAngleRadians);
		if (sineInflow <= EPSILON) {
			return 1.0;
		}
		double exponentMagnitude = 0.5
				* bladeCount
				* (1.0 - radialFraction)
				/ (radialFraction * sineInflow);
		double exponential = Math.exp(-Math.max(0.0, exponentMagnitude));
		return 2.0 / Math.PI * Math.acos(MathUtil.clamp(exponential, 0.0, 1.0));
	}

	private static AnnulusSample annulusSample(
			double innerFraction,
			double outerFraction,
			double radialFraction,
			double radiusMeters,
			double radialWidthMeters,
			double chordMeters,
			double pitchAngleRadians,
			double inducedVelocityMetersPerSecond,
			SectionEvaluation section,
			int rootIterations
	) {
		return new AnnulusSample(
				innerFraction,
				outerFraction,
				radialFraction,
				radiusMeters,
				radialWidthMeters,
				chordMeters,
				pitchAngleRadians,
				inducedVelocityMetersPerSecond,
				section.tangentialSpeedMetersPerSecond(),
				section.relativeSpeedMetersPerSecond(),
				section.inflowAngleRadians(),
				section.angleOfAttackRadians(),
				section.reynoldsNumber(),
				section.polar(),
				section.prandtlTipLossFactor(),
				section.differentialThrustNewtonsPerMeter(),
				section.differentialMomentumThrustNewtonsPerMeter(),
				section.momentumClosureResidualNewtonsPerMeter(),
				section.differentialLiftTorqueNewtonMetersPerMeter(),
				section.differentialProfileTorqueNewtonMetersPerMeter(),
				section.differentialThrustNewtonsPerMeter() * radialWidthMeters,
				section.differentialMomentumThrustNewtonsPerMeter() * radialWidthMeters,
				section.differentialLiftTorqueNewtonMetersPerMeter() * radialWidthMeters,
				section.differentialProfileTorqueNewtonMetersPerMeter() * radialWidthMeters,
				rootIterations
		);
	}

	private static HoverSample aggregate(HoverQuery query, List<AnnulusSample> annuli) {
		double thrust = 0.0;
		double momentumThrust = 0.0;
		double liftTorque = 0.0;
		double profileTorque = 0.0;
		int clampedCount = 0;
		int reynoldsClampedCount = 0;
		int angleClampedCount = 0;
		double minimumReynolds = Double.POSITIVE_INFINITY;
		double maximumReynolds = 0.0;
		double minimumAngle = Double.POSITIVE_INFINITY;
		double maximumAngle = Double.NEGATIVE_INFINITY;
		for (AnnulusSample annulus : annuli) {
			thrust += annulus.thrustNewtons();
			momentumThrust += annulus.momentumThrustNewtons();
			liftTorque += annulus.liftInducedTorqueNewtonMeters();
			profileTorque += annulus.profileTorqueNewtonMeters();
			if (annulus.polar().clamped()) {
				clampedCount++;
			}
			if (annulus.polar().reynoldsClamped()) {
				reynoldsClampedCount++;
			}
			if (annulus.polar().angleOfAttackClamped()) {
				angleClampedCount++;
			}
			minimumReynolds = Math.min(minimumReynolds, annulus.reynoldsNumber());
			maximumReynolds = Math.max(maximumReynolds, annulus.reynoldsNumber());
			double angleDegrees = Math.toDegrees(annulus.angleOfAttackRadians());
			minimumAngle = Math.min(minimumAngle, angleDegrees);
			maximumAngle = Math.max(maximumAngle, angleDegrees);
		}

		double shaftTorque = liftTorque + profileTorque;
		double liftPower = liftTorque * query.angularVelocityRadiansPerSecond();
		double profilePower = profileTorque * query.angularVelocityRadiansPerSecond();
		double shaftPower = shaftTorque * query.angularVelocityRadiansPerSecond();
		double diameter = query.rotorRadiusMeters() * 2.0;
		double revolutionsPerSecond = query.angularVelocityRadiansPerSecond() / (2.0 * Math.PI);
		double thrustDenominator = query.airDensityKgPerCubicMeter()
				* revolutionsPerSecond
				* revolutionsPerSecond
				* Math.pow(diameter, 4.0);
		double powerDenominator = query.airDensityKgPerCubicMeter()
				* Math.pow(revolutionsPerSecond, 3.0)
				* Math.pow(diameter, 5.0);
		double torqueDenominator = query.airDensityKgPerCubicMeter()
				* revolutionsPerSecond
				* revolutionsPerSecond
				* Math.pow(diameter, 5.0);
		double diskArea = Math.PI * query.rotorRadiusMeters() * query.rotorRadiusMeters();
		double idealInducedVelocity = thrust > EPSILON
				? Math.sqrt(thrust / (2.0 * query.airDensityKgPerCubicMeter() * diskArea))
				: 0.0;
		double idealInducedPower = thrust * idealInducedVelocity;
		return new HoverSample(
				query,
				Status.SOLVED,
				annuli,
				thrust,
				momentumThrust,
				thrust - momentumThrust,
				shaftTorque,
				liftTorque,
				profileTorque,
				shaftTorque - liftTorque - profileTorque,
				shaftPower,
				liftPower,
				profilePower,
				shaftPower - liftPower - profilePower,
				ratio(thrust, thrustDenominator),
				ratio(shaftPower, powerDenominator),
				ratio(shaftTorque, torqueDenominator),
				diskArea,
				ratio(thrust, diskArea),
				idealInducedVelocity,
				idealInducedPower,
				ratio(idealInducedPower, shaftPower),
				ratio(liftPower, idealInducedPower),
				clampedCount,
				reynoldsClampedCount,
				angleClampedCount,
				minimumReynolds,
				maximumReynolds,
				minimumAngle,
				maximumAngle,
				"annular-hover-bemt-solved"
		);
	}

	private static HoverSample blocked(
			HoverQuery query,
			Status status,
			List<AnnulusSample> annuli,
			String message
	) {
		double diskArea = Math.PI * query.rotorRadiusMeters() * query.rotorRadiusMeters();
		return new HoverSample(
				query,
				status,
				annuli,
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
				diskArea,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0,
				0,
				0,
				0.0,
				0.0,
				0.0,
				0.0,
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
		double result = numerator / denominator;
		return Double.isFinite(result) ? result : 0.0;
	}

	private record RootSolve(
			boolean solved,
			double inducedVelocityMetersPerSecond,
			int iterations
	) {
	}

	private record SectionEvaluation(
			double tangentialSpeedMetersPerSecond,
			double relativeSpeedMetersPerSecond,
			double inflowAngleRadians,
			double angleOfAttackRadians,
			double reynoldsNumber,
			Sda1075XfoilSectionPolar.PolarSample polar,
			double prandtlTipLossFactor,
			double differentialThrustNewtonsPerMeter,
			double differentialMomentumThrustNewtonsPerMeter,
			double momentumClosureResidualNewtonsPerMeter,
			double differentialLiftTorqueNewtonMetersPerMeter,
			double differentialProfileTorqueNewtonMetersPerMeter
	) {
	}

	public record HoverQuery(
			BladeGeometry geometry,
			double rotorRadiusMeters,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			double angularVelocityRadiansPerSecond,
			int annuliPerGeometryInterval,
			Sda1075XfoilSectionPolar.EnvelopePolicy polarEnvelopePolicy
	) {
		public HoverQuery {
			if (geometry == null) {
				throw new IllegalArgumentException("geometry must not be null.");
			}
			if (!Double.isFinite(rotorRadiusMeters) || rotorRadiusMeters <= 0.0) {
				throw new IllegalArgumentException("rotorRadiusMeters must be finite and positive.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (!Double.isFinite(dynamicViscosityPascalSeconds)
					|| dynamicViscosityPascalSeconds <= 0.0) {
				throw new IllegalArgumentException(
						"dynamicViscosityPascalSeconds must be finite and positive."
				);
			}
			if (!Double.isFinite(angularVelocityRadiansPerSecond)
					|| angularVelocityRadiansPerSecond <= 0.0) {
				throw new IllegalArgumentException(
						"angularVelocityRadiansPerSecond must be finite and positive."
				);
			}
			if (annuliPerGeometryInterval <= 0
					|| annuliPerGeometryInterval > MAX_ANNULI_PER_GEOMETRY_INTERVAL) {
				throw new IllegalArgumentException(
						"annuliPerGeometryInterval must be in [1, "
								+ MAX_ANNULI_PER_GEOMETRY_INTERVAL + "]."
				);
			}
			if (polarEnvelopePolicy == null) {
				polarEnvelopePolicy = Sda1075XfoilSectionPolar.EnvelopePolicy
						.BLOCK_OUT_OF_ENVELOPE;
			}
		}

		public static HoverQuery standardResolution(
				BladeGeometry geometry,
				double rotorRadiusMeters,
				double airDensityKgPerCubicMeter,
				double dynamicViscosityPascalSeconds,
				double angularVelocityRadiansPerSecond,
				Sda1075XfoilSectionPolar.EnvelopePolicy polarEnvelopePolicy
		) {
			return new HoverQuery(
					geometry,
					rotorRadiusMeters,
					airDensityKgPerCubicMeter,
					dynamicViscosityPascalSeconds,
					angularVelocityRadiansPerSecond,
					DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL,
					polarEnvelopePolicy
			);
		}
	}

	public record AnnulusSample(
			double innerRadialFraction,
			double outerRadialFraction,
			double radialFraction,
			double radiusMeters,
			double radialWidthMeters,
			double chordMeters,
			double pitchAngleRadians,
			double inducedVelocityMetersPerSecond,
			double tangentialSpeedMetersPerSecond,
			double relativeSpeedMetersPerSecond,
			double inflowAngleRadians,
			double angleOfAttackRadians,
			double reynoldsNumber,
			Sda1075XfoilSectionPolar.PolarSample polar,
			double prandtlTipLossFactor,
			double differentialThrustNewtonsPerMeter,
			double differentialMomentumThrustNewtonsPerMeter,
			double differentialMomentumClosureResidualNewtonsPerMeter,
			double differentialLiftTorqueNewtonMetersPerMeter,
			double differentialProfileTorqueNewtonMetersPerMeter,
			double thrustNewtons,
			double momentumThrustNewtons,
			double liftInducedTorqueNewtonMeters,
			double profileTorqueNewtonMeters,
			int rootIterations
	) {
	}

	public record HoverSample(
			HoverQuery query,
			Status status,
			List<AnnulusSample> annuli,
			double thrustNewtons,
			double momentumThrustNewtons,
			double thrustClosureResidualNewtons,
			double shaftTorqueNewtonMeters,
			double liftInducedTorqueNewtonMeters,
			double profileTorqueNewtonMeters,
			double torqueClosureResidualNewtonMeters,
			double shaftPowerWatts,
			double liftInducedPowerWatts,
			double profilePowerWatts,
			double powerClosureResidualWatts,
			double thrustCoefficientCt,
			double powerCoefficientCp,
			double torqueCoefficientCq,
			double diskAreaSquareMeters,
			double diskLoadingNewtonsPerSquareMeter,
			double idealInducedVelocityMetersPerSecond,
			double idealInducedPowerWatts,
			double hoverFigureOfMerit,
			double liftInducedPowerOverUniformIdeal,
			int clampedAnnulusCount,
			int reynoldsClampedAnnulusCount,
			int angleOfAttackClampedAnnulusCount,
			double minimumReynoldsNumber,
			double maximumReynoldsNumber,
			double minimumAngleOfAttackDegrees,
			double maximumAngleOfAttackDegrees,
			String message
	) {
		public HoverSample {
			annuli = List.copyOf(annuli == null ? List.of() : annuli);
		}

		public boolean solved() {
			return status == Status.SOLVED;
		}

		public boolean blocked() {
			return !solved();
		}

		public int annulusCount() {
			return annuli.size();
		}

		public double unclampedPolarCoverageFraction() {
			return annuli.isEmpty()
					? 0.0
					: (double) (annuli.size() - clampedAnnulusCount) / annuli.size();
		}
	}
}
