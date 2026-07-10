package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

import com.tenicana.dronecraft.sim.RotorHoverBladeProfilePowerModel.BladeGeometry;

/**
 * Hover blade-element/momentum model with annular induced-flow solves.
 * Each annulus closes blade-element thrust against hover momentum theory and
 * applies the Prandtl tip-loss factor. The optional wake-rotation solve also
 * closes lift-induced torque against angular momentum; profile drag remains a
 * dissipative torque instead of being forced into coherent swirl. Section lift
 * and drag come from the explicitly bounded SDA1075 polar, with no coefficient
 * tuned to static data.
 */
public final class RotorHoverBladeElementModel {
	public static final String BLADE_ELEMENT_MOMENTUM_REFERENCE_URL =
			"https://www.nrel.gov/wind/nwtc/assets/pdfs/ad-theory.pdf";
	public static final int DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL = 8;
	private static final int MAX_ANNULI_PER_GEOMETRY_INTERVAL = 64;
	private static final int MAX_ROOT_BRACKET_EXPANSIONS = 20;
	private static final int MAX_ROOT_ITERATIONS = 80;
	private static final int MAX_COUPLED_INDUCTION_ITERATIONS = 1_000;
	private static final double COUPLED_INDUCTION_RELAXATION = 0.20;
	private static final double COUPLED_VELOCITY_CONVERGENCE_FRACTION = 1.0e-13;
	private static final double COUPLED_MOMENTUM_RESIDUAL_TOLERANCE = 1.0e-8;
	private static final double MAX_TANGENTIAL_INDUCTION_TO_BLADE_SPEED = 0.95;
	private static final double EPSILON = 1.0e-12;

	private RotorHoverBladeElementModel() {
	}

	public enum Status {
		SOLVED,
		BLOCKED_SECTION_POLAR_ENVELOPE,
		BLOCKED_ANNULAR_MOMENTUM_ROOT
	}

	public enum WakeRotationPolicy {
		AXIAL_MOMENTUM_ONLY,
		COUPLED_LIFT_TORQUE_ANGULAR_MOMENTUM
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

				RootSolve root = solveAnnularInducedFlow(
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
						root.axialInducedVelocityMetersPerSecond(),
						root.tangentialInducedVelocityMetersPerSecond(),
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

	private static RootSolve solveAnnularInducedFlow(
			HoverQuery query,
			double radialFraction,
			double radiusMeters,
			double chordMeters,
			double pitchAngleRadians
	) {
		RootSolve axialRoot = solveAxialInducedVelocity(
				query,
				radialFraction,
				radiusMeters,
				chordMeters,
				pitchAngleRadians
		);
		if (!axialRoot.solved()
				|| query.wakeRotationPolicy() == WakeRotationPolicy.AXIAL_MOMENTUM_ONLY) {
			return axialRoot;
		}
		return solveCoupledAxialAndTangentialInduction(
				query,
				radialFraction,
				radiusMeters,
				chordMeters,
				pitchAngleRadians,
				axialRoot
		);
	}

	private static RootSolve solveAxialInducedVelocity(
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
				0.0,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
		);
		if (lowerEvaluation.momentumClosureResidualNewtonsPerMeter() <= 0.0) {
			return RootSolve.blocked();
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
				0.0,
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
					0.0,
					Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
			);
			bracketExpansions++;
		}
		if (upperEvaluation.momentumClosureResidualNewtonsPerMeter() > 0.0) {
			return RootSolve.blocked();
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
					0.0,
					Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
			);
			if (midpointEvaluation.momentumClosureResidualNewtonsPerMeter() > 0.0) {
				lowerVelocity = midpointVelocity;
			} else {
				upperVelocity = midpointVelocity;
			}
		}
		return new RootSolve(
				true,
				0.5 * (lowerVelocity + upperVelocity),
				0.0,
				iterations
		);
	}

	private static RootSolve solveCoupledAxialAndTangentialInduction(
			HoverQuery query,
			double radialFraction,
			double radiusMeters,
			double chordMeters,
			double pitchAngleRadians,
			RootSolve axialRoot
	) {
		double bladeTangentialSpeed = query.angularVelocityRadiansPerSecond() * radiusMeters;
		SectionEvaluation initial = evaluateSection(
				query,
				radialFraction,
				radiusMeters,
				chordMeters,
				pitchAngleRadians,
				axialRoot.axialInducedVelocityMetersPerSecond(),
				0.0,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
		);
		double axialInducedVelocity = axialRoot.axialInducedVelocityMetersPerSecond();
		double tangentialInducedVelocity = tangentialInductionTarget(
				query,
				radiusMeters,
				axialInducedVelocity,
				initial
		);
		tangentialInducedVelocity = Math.min(
				tangentialInducedVelocity,
				MAX_TANGENTIAL_INDUCTION_TO_BLADE_SPEED * bladeTangentialSpeed
		);
		double convergenceTolerance = COUPLED_VELOCITY_CONVERGENCE_FRACTION
				* bladeTangentialSpeed;
		boolean converged = false;
		int coupledIterations = 0;
		for (; coupledIterations < MAX_COUPLED_INDUCTION_ITERATIONS; coupledIterations++) {
			SectionEvaluation section = evaluateSection(
					query,
					radialFraction,
					radiusMeters,
					chordMeters,
					pitchAngleRadians,
					axialInducedVelocity,
					tangentialInducedVelocity,
					Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
			);
			if (section.differentialThrustNewtonsPerMeter() <= EPSILON
					|| section.prandtlTipLossFactor() <= EPSILON) {
				return RootSolve.blocked();
			}
			double axialTarget = Math.sqrt(
					section.differentialThrustNewtonsPerMeter()
							/ (4.0
							* Math.PI
							* query.airDensityKgPerCubicMeter()
							* section.prandtlTipLossFactor()
							* radiusMeters)
			);
			double tangentialTarget = tangentialInductionTarget(
					query,
					radiusMeters,
					axialInducedVelocity,
					section
			);
			tangentialTarget = Math.min(
					tangentialTarget,
					MAX_TANGENTIAL_INDUCTION_TO_BLADE_SPEED * bladeTangentialSpeed
			);
			double newAxialVelocity = lerp(
					axialInducedVelocity,
					axialTarget,
					COUPLED_INDUCTION_RELAXATION
			);
			double newTangentialVelocity = lerp(
					tangentialInducedVelocity,
					tangentialTarget,
					COUPLED_INDUCTION_RELAXATION
			);
			if (Math.abs(newAxialVelocity - axialInducedVelocity) <= convergenceTolerance
					&& Math.abs(newTangentialVelocity - tangentialInducedVelocity)
							<= convergenceTolerance) {
				axialInducedVelocity = newAxialVelocity;
				tangentialInducedVelocity = newTangentialVelocity;
				converged = true;
				break;
			}
			axialInducedVelocity = newAxialVelocity;
			tangentialInducedVelocity = newTangentialVelocity;
		}
		if (!converged) {
			return RootSolve.blocked();
		}

		SectionEvaluation solvedSection = evaluateSection(
				query,
				radialFraction,
				radiusMeters,
				chordMeters,
				pitchAngleRadians,
				axialInducedVelocity,
				tangentialInducedVelocity,
				Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE
		);
		double scaledThrustResidual = Math.abs(
				solvedSection.momentumClosureResidualNewtonsPerMeter()
		) / Math.max(EPSILON, Math.abs(solvedSection.differentialThrustNewtonsPerMeter()));
		double scaledAngularMomentumResidual = Math.abs(
				solvedSection.angularMomentumClosureResidualNewtonMetersPerMeter()
		) / Math.max(
				EPSILON,
				Math.abs(solvedSection.differentialLiftTorqueNewtonMetersPerMeter())
		);
		if (scaledThrustResidual > COUPLED_MOMENTUM_RESIDUAL_TOLERANCE
				|| scaledAngularMomentumResidual > COUPLED_MOMENTUM_RESIDUAL_TOLERANCE) {
			return RootSolve.blocked();
		}
		return new RootSolve(
				true,
				axialInducedVelocity,
				tangentialInducedVelocity,
				axialRoot.iterations() + coupledIterations + 1
		);
	}

	private static double tangentialInductionTarget(
			HoverQuery query,
			double radiusMeters,
			double axialInducedVelocityMetersPerSecond,
			SectionEvaluation section
	) {
		double denominator = 4.0
				* Math.PI
				* query.airDensityKgPerCubicMeter()
				* section.prandtlTipLossFactor()
				* radiusMeters
				* radiusMeters
				* Math.max(EPSILON, axialInducedVelocityMetersPerSecond);
		return Math.max(0.0, section.differentialLiftTorqueNewtonMetersPerMeter())
				/ Math.max(EPSILON, denominator);
	}

	private static SectionEvaluation evaluateSection(
			HoverQuery query,
			double radialFraction,
			double radiusMeters,
			double chordMeters,
			double pitchAngleRadians,
			double axialInducedVelocityMetersPerSecond,
			double tangentialInducedVelocityMetersPerSecond,
			Sda1075XfoilSectionPolar.EnvelopePolicy envelopePolicy
	) {
		double bladeTangentialSpeed = query.angularVelocityRadiansPerSecond() * radiusMeters;
		double relativeTangentialSpeed = bladeTangentialSpeed
				- tangentialInducedVelocityMetersPerSecond;
		if (relativeTangentialSpeed <= EPSILON) {
			throw new IllegalArgumentException(
					"tangential induction must remain below the local blade speed."
			);
		}
		double relativeSpeed = Math.hypot(
				relativeTangentialSpeed,
				axialInducedVelocityMetersPerSecond
		);
		double inflowAngle = Math.atan2(
				axialInducedVelocityMetersPerSecond,
				relativeTangentialSpeed
		);
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
				* axialInducedVelocityMetersPerSecond
				* axialInducedVelocityMetersPerSecond;
		double differentialMomentumTorque = 4.0
				* Math.PI
				* query.airDensityKgPerCubicMeter()
				* tipLossFactor
				* radiusMeters
				* radiusMeters
				* axialInducedVelocityMetersPerSecond
				* tangentialInducedVelocityMetersPerSecond;
		// Actuator-disk swirl is half the far-wake tangential velocity jump.
		double differentialWakeSwirlKineticPower = radiusMeters > EPSILON
				? differentialMomentumTorque
						* tangentialInducedVelocityMetersPerSecond
						/ radiusMeters
				: 0.0;
		return new SectionEvaluation(
				bladeTangentialSpeed,
				axialInducedVelocityMetersPerSecond,
				tangentialInducedVelocityMetersPerSecond,
				relativeTangentialSpeed,
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
				differentialProfileTorque,
				differentialMomentumTorque,
				differentialLiftTorque - differentialMomentumTorque,
				differentialWakeSwirlKineticPower
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
				section.axialInducedVelocityMetersPerSecond(),
				section.tangentialInducedVelocityMetersPerSecond(),
				2.0 * section.tangentialInducedVelocityMetersPerSecond(),
				section.bladeTangentialSpeedMetersPerSecond(),
				section.relativeTangentialSpeedMetersPerSecond(),
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
				section.differentialMomentumTorqueNewtonMetersPerMeter(),
				section.angularMomentumClosureResidualNewtonMetersPerMeter(),
				section.differentialWakeSwirlKineticPowerWattsPerMeter(),
				section.differentialThrustNewtonsPerMeter() * radialWidthMeters,
				section.differentialMomentumThrustNewtonsPerMeter() * radialWidthMeters,
				section.differentialLiftTorqueNewtonMetersPerMeter() * radialWidthMeters,
				section.differentialProfileTorqueNewtonMetersPerMeter() * radialWidthMeters,
				section.differentialMomentumTorqueNewtonMetersPerMeter() * radialWidthMeters,
				section.angularMomentumClosureResidualNewtonMetersPerMeter() * radialWidthMeters,
				section.differentialWakeSwirlKineticPowerWattsPerMeter() * radialWidthMeters,
				rootIterations
		);
	}

	private static HoverSample aggregate(HoverQuery query, List<AnnulusSample> annuli) {
		double thrust = 0.0;
		double momentumThrust = 0.0;
		double liftTorque = 0.0;
		double profileTorque = 0.0;
		double momentumWakeTorque = 0.0;
		double wakeSwirlKineticPower = 0.0;
		double maximumTangentialInducedVelocity = 0.0;
		double maximumTangentialInductionFraction = 0.0;
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
			momentumWakeTorque += annulus.momentumWakeTorqueNewtonMeters();
			wakeSwirlKineticPower += annulus.wakeSwirlKineticPowerWatts();
			maximumTangentialInducedVelocity = Math.max(
					maximumTangentialInducedVelocity,
					annulus.tangentialInducedVelocityMetersPerSecond()
			);
			maximumTangentialInductionFraction = Math.max(
					maximumTangentialInductionFraction,
					ratio(
							annulus.tangentialInducedVelocityMetersPerSecond(),
							annulus.bladeTangentialSpeedMetersPerSecond()
					)
			);
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
				momentumWakeTorque,
				liftTorque - momentumWakeTorque,
				shaftPower,
				liftPower,
				profilePower,
				shaftPower - liftPower - profilePower,
				wakeSwirlKineticPower,
				ratio(wakeSwirlKineticPower, shaftPower),
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
				maximumTangentialInducedVelocity,
				maximumTangentialInductionFraction,
				query.wakeRotationPolicy() == WakeRotationPolicy.AXIAL_MOMENTUM_ONLY
						? "annular-hover-bemt-axial-momentum-solved"
						: "annular-hover-bemt-axial-angular-momentum-solved"
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
			double axialInducedVelocityMetersPerSecond,
			double tangentialInducedVelocityMetersPerSecond,
			int iterations
	) {
		private static RootSolve blocked() {
			return new RootSolve(false, 0.0, 0.0, 0);
		}
	}

	private record SectionEvaluation(
			double bladeTangentialSpeedMetersPerSecond,
			double axialInducedVelocityMetersPerSecond,
			double tangentialInducedVelocityMetersPerSecond,
			double relativeTangentialSpeedMetersPerSecond,
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
			double differentialProfileTorqueNewtonMetersPerMeter,
			double differentialMomentumTorqueNewtonMetersPerMeter,
			double angularMomentumClosureResidualNewtonMetersPerMeter,
			double differentialWakeSwirlKineticPowerWattsPerMeter
	) {
	}

	public record HoverQuery(
			BladeGeometry geometry,
			double rotorRadiusMeters,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			double angularVelocityRadiansPerSecond,
			int annuliPerGeometryInterval,
			Sda1075XfoilSectionPolar.EnvelopePolicy polarEnvelopePolicy,
			WakeRotationPolicy wakeRotationPolicy
	) {
		public HoverQuery(
				BladeGeometry geometry,
				double rotorRadiusMeters,
				double airDensityKgPerCubicMeter,
				double dynamicViscosityPascalSeconds,
				double angularVelocityRadiansPerSecond,
				int annuliPerGeometryInterval,
				Sda1075XfoilSectionPolar.EnvelopePolicy polarEnvelopePolicy
		) {
			this(
					geometry,
					rotorRadiusMeters,
					airDensityKgPerCubicMeter,
					dynamicViscosityPascalSeconds,
					angularVelocityRadiansPerSecond,
					annuliPerGeometryInterval,
					polarEnvelopePolicy,
					WakeRotationPolicy.AXIAL_MOMENTUM_ONLY
			);
		}

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
			if (wakeRotationPolicy == null) {
				wakeRotationPolicy = WakeRotationPolicy.AXIAL_MOMENTUM_ONLY;
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
					polarEnvelopePolicy,
					WakeRotationPolicy.AXIAL_MOMENTUM_ONLY
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
			double axialInducedVelocityMetersPerSecond,
			double tangentialInducedVelocityMetersPerSecond,
			double farWakeTangentialVelocityMetersPerSecond,
			double bladeTangentialSpeedMetersPerSecond,
			double relativeTangentialSpeedMetersPerSecond,
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
			double differentialMomentumTorqueNewtonMetersPerMeter,
			double differentialAngularMomentumClosureResidualNewtonMetersPerMeter,
			double differentialWakeSwirlKineticPowerWattsPerMeter,
			double thrustNewtons,
			double momentumThrustNewtons,
			double liftInducedTorqueNewtonMeters,
			double profileTorqueNewtonMeters,
			double momentumWakeTorqueNewtonMeters,
			double angularMomentumClosureResidualNewtonMeters,
			double wakeSwirlKineticPowerWatts,
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
			double momentumWakeTorqueNewtonMeters,
			double angularMomentumClosureResidualNewtonMeters,
			double shaftPowerWatts,
			double liftInducedPowerWatts,
			double profilePowerWatts,
			double powerClosureResidualWatts,
			double wakeSwirlKineticPowerWatts,
			double wakeSwirlKineticPowerOverShaftPower,
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
			double maximumTangentialInducedVelocityMetersPerSecond,
			double maximumTangentialInductionToBladeSpeed,
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
