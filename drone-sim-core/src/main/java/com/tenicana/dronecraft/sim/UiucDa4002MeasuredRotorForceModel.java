package com.tenicana.dronecraft.sim;

import java.util.Optional;

/**
 * Resolves the measured DA4002 axial CT/CP surface into body-frame force and
 * torque vectors. The UIUC data does not identify an oblique- or reverse-flow
 * model, so those queries remain reference-only or blocked instead of silently
 * projecting them into a runtime force.
 */
public final class UiucDa4002MeasuredRotorForceModel {
	private static final double EPSILON = 1.0e-9;
	private static final double ROTOR_DIAMETER_TOLERANCE_METERS = 1.0e-6;

	private UiucDa4002MeasuredRotorForceModel() {
	}

	public enum ForceApplicationStatus {
		APPLIED_AXIAL_MEASURED,
		REFERENCE_ONLY_TRANSVERSE_INFLOW,
		REFERENCE_ONLY_NON_POSITIVE_THRUST,
		BLOCKED_REVERSE_AXIAL_FLOW,
		BLOCKED_COEFFICIENT_SURFACE,
		BLOCKED_ROTOR_DIAMETER_MISMATCH
	}

	public record Query(
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			RotorSpec rotor,
			double rpm,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			Vec3 momentReferenceBodyMeters,
			Vec3 relativeAirVelocityBodyMetersPerSecond
	) {
		public Query {
			if (propeller == null) {
				throw new IllegalArgumentException("propeller must not be null.");
			}
			if (rotor == null) {
				throw new IllegalArgumentException("rotor must not be null.");
			}
			if (!Double.isFinite(rpm) || rpm <= 0.0) {
				throw new IllegalArgumentException("rpm must be finite and positive.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter)
					|| airDensityKgPerCubicMeter <= 0.0) {
				throw new IllegalArgumentException(
						"airDensityKgPerCubicMeter must be finite and positive."
				);
			}
			if (!Double.isFinite(dynamicViscosityPascalSeconds)
					|| dynamicViscosityPascalSeconds <= 0.0) {
				throw new IllegalArgumentException(
						"dynamicViscosityPascalSeconds must be finite and positive."
				);
			}
			if (momentReferenceBodyMeters == null) {
				momentReferenceBodyMeters = Vec3.ZERO;
			} else if (!momentReferenceBodyMeters.isFinite()) {
				throw new IllegalArgumentException("momentReferenceBodyMeters must be finite.");
			}
			if (relativeAirVelocityBodyMetersPerSecond == null
					|| !relativeAirVelocityBodyMetersPerSecond.isFinite()) {
				throw new IllegalArgumentException(
						"relativeAirVelocityBodyMetersPerSecond must be finite."
				);
			}
		}
	}

	public record ForceSample(
			Query query,
			ForceApplicationStatus forceApplicationStatus,
			Optional<UiucDa4002MeasuredRotorModel.DimensionalSample> dimensionalSample,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double signedAdvanceRatioJ,
			Vec3 transverseAirVelocityBodyMetersPerSecond,
			double transverseAirSpeedMetersPerSecond,
			double inflowAngleRadians,
			Vec3 referenceThrustForceBodyNewtons,
			Vec3 referenceReactionTorqueBodyNewtonMeters,
			Vec3 momentArmBodyMeters,
			Vec3 referenceThrustMomentBodyNewtonMeters,
			Vec3 referenceTotalTorqueBodyNewtonMeters,
			double yawTorquePerThrustMeterEquivalent,
			String message
	) {
		public ForceSample {
			if (query == null) {
				throw new IllegalArgumentException("query must not be null.");
			}
			if (forceApplicationStatus == null) {
				throw new IllegalArgumentException("forceApplicationStatus must not be null.");
			}
			dimensionalSample = dimensionalSample == null
					? Optional.empty()
					: dimensionalSample;
			if (!Double.isFinite(signedAxialAdvanceSpeedMetersPerSecond)) {
				throw new IllegalArgumentException(
						"signedAxialAdvanceSpeedMetersPerSecond must be finite."
				);
			}
			if (!Double.isFinite(signedAdvanceRatioJ)) {
				throw new IllegalArgumentException("signedAdvanceRatioJ must be finite.");
			}
			validateVector(transverseAirVelocityBodyMetersPerSecond,
					"transverseAirVelocityBodyMetersPerSecond");
			if (!Double.isFinite(transverseAirSpeedMetersPerSecond)
					|| transverseAirSpeedMetersPerSecond < 0.0) {
				throw new IllegalArgumentException(
						"transverseAirSpeedMetersPerSecond must be finite and non-negative."
				);
			}
			if (!Double.isFinite(inflowAngleRadians)
					|| inflowAngleRadians < 0.0
					|| inflowAngleRadians > Math.PI) {
				throw new IllegalArgumentException(
						"inflowAngleRadians must be finite and within [0, pi]."
				);
			}
			validateVector(referenceThrustForceBodyNewtons,
					"referenceThrustForceBodyNewtons");
			validateVector(referenceReactionTorqueBodyNewtonMeters,
					"referenceReactionTorqueBodyNewtonMeters");
			validateVector(momentArmBodyMeters, "momentArmBodyMeters");
			validateVector(referenceThrustMomentBodyNewtonMeters,
					"referenceThrustMomentBodyNewtonMeters");
			validateVector(referenceTotalTorqueBodyNewtonMeters,
					"referenceTotalTorqueBodyNewtonMeters");
			if (!Double.isFinite(yawTorquePerThrustMeterEquivalent)) {
				throw new IllegalArgumentException(
						"yawTorquePerThrustMeterEquivalent must be finite."
				);
			}
			message = message == null ? "" : message;
		}

		public boolean blocked() {
			return switch (forceApplicationStatus) {
				case BLOCKED_REVERSE_AXIAL_FLOW,
						BLOCKED_COEFFICIENT_SURFACE,
						BLOCKED_ROTOR_DIAMETER_MISMATCH -> true;
				default -> false;
			};
		}

		public boolean referenceOnly() {
			return forceApplicationStatus
					== ForceApplicationStatus.REFERENCE_ONLY_TRANSVERSE_INFLOW
					|| forceApplicationStatus
					== ForceApplicationStatus.REFERENCE_ONLY_NON_POSITIVE_THRUST;
		}

		public boolean runtimeForceEligible() {
			return forceApplicationStatus == ForceApplicationStatus.APPLIED_AXIAL_MEASURED;
		}

		public boolean coefficientSampleAvailable() {
			return dimensionalSample.isPresent();
		}

		public Optional<UiucDa4002MeasuredRotorModel.LookupResult> lookup() {
			return dimensionalSample.map(UiucDa4002MeasuredRotorModel.DimensionalSample::lookup);
		}

		public boolean clamped() {
			return lookup().map(UiucDa4002MeasuredRotorModel.LookupResult::clamped)
					.orElse(false);
		}

		public Vec3 appliedThrustForceBodyNewtons() {
			return runtimeForceEligible() ? referenceThrustForceBodyNewtons : Vec3.ZERO;
		}

		public Vec3 appliedReactionTorqueBodyNewtonMeters() {
			return runtimeForceEligible() ? referenceReactionTorqueBodyNewtonMeters : Vec3.ZERO;
		}

		public Vec3 appliedThrustMomentBodyNewtonMeters() {
			return runtimeForceEligible() ? referenceThrustMomentBodyNewtonMeters : Vec3.ZERO;
		}

		public Vec3 appliedTotalTorqueBodyNewtonMeters() {
			return runtimeForceEligible() ? referenceTotalTorqueBodyNewtonMeters : Vec3.ZERO;
		}

		private static void validateVector(Vec3 value, String name) {
			if (value == null || !value.isFinite()) {
				throw new IllegalArgumentException(name + " must be finite.");
			}
		}
	}

	public static ForceSample sample(
			UiucDa4002MeasuredRotorModel.Propeller propeller,
			RotorSpec rotor,
			double rpm,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			Vec3 relativeAirVelocityBodyMetersPerSecond
	) {
		return sample(new Query(
				propeller,
				rotor,
				rpm,
				airDensityKgPerCubicMeter,
				dynamicViscosityPascalSeconds,
				Vec3.ZERO,
				relativeAirVelocityBodyMetersPerSecond
		));
	}

	public static ForceSample sample(Query query) {
		if (query == null) {
			throw new IllegalArgumentException("query must not be null.");
		}
		Vec3 axis = query.rotor().thrustAxisBody().normalized();
		Vec3 relativeAir = query.relativeAirVelocityBodyMetersPerSecond();
		double projectedAxialAdvanceSpeed = relativeAir.dot(axis);
		double signedAxialAdvanceSpeed = Math.abs(projectedAxialAdvanceSpeed) <= EPSILON
				? 0.0
				: projectedAxialAdvanceSpeed;
		Vec3 transverseAir = relativeAir.subtract(axis.multiply(projectedAxialAdvanceSpeed));
		double transverseAirSpeed = transverseAir.length();
		double inflowAngle = inflowAngleRadians(
				signedAxialAdvanceSpeed,
				transverseAirSpeed
		);
		double revolutionsPerSecond = query.rpm() / 60.0;
		double signedAdvanceRatio = stableAdvanceRatio(signedAxialAdvanceSpeed
				/ (revolutionsPerSecond * query.propeller().diameterMeters()));
		Vec3 momentArm = query.rotor().positionBodyMeters()
				.subtract(query.momentReferenceBodyMeters());

		if (!rotorDiameterMatchesMeasuredPropeller(query)) {
			return unavailableSample(
					query,
					ForceApplicationStatus.BLOCKED_ROTOR_DIAMETER_MISMATCH,
					signedAxialAdvanceSpeed,
					signedAdvanceRatio,
					transverseAir,
					transverseAirSpeed,
					inflowAngle,
					momentArm,
					"rotor-diameter-does-not-match-selected-uiuc-da4002-propeller"
			);
		}
		if (signedAxialAdvanceSpeed < 0.0) {
			return unavailableSample(
					query,
					ForceApplicationStatus.BLOCKED_REVERSE_AXIAL_FLOW,
					signedAxialAdvanceSpeed,
					signedAdvanceRatio,
					transverseAir,
					transverseAirSpeed,
					inflowAngle,
					momentArm,
					"reverse-axial-flow-is-outside-uiuc-da4002-measured-envelope"
			);
		}

		UiucDa4002MeasuredRotorModel.DimensionalSample dimensional =
				UiucDa4002AxialSurfaceV1.sample(
						query.propeller(),
						signedAdvanceRatio,
						query.rpm(),
						query.airDensityKgPerCubicMeter(),
						query.dynamicViscosityPascalSeconds()
				);
		if (dimensional.blocked()) {
			return resolvedSample(
					query,
					ForceApplicationStatus.BLOCKED_COEFFICIENT_SURFACE,
					dimensional,
					signedAxialAdvanceSpeed,
					signedAdvanceRatio,
					transverseAir,
					transverseAirSpeed,
					inflowAngle,
					momentArm,
					Vec3.ZERO,
					Vec3.ZERO,
					dimensional.lookup().message()
			);
		}

		Vec3 thrustForce = axis.multiply(dimensional.thrustNewtons());
		Vec3 reactionTorque = axis.multiply(
				query.rotor().spinDirection() * dimensional.shaftTorqueNewtonMeters()
		);
		ForceApplicationStatus status;
		String message;
		if (!dimensional.positiveThrustRuntimeEligible()) {
			status = ForceApplicationStatus.REFERENCE_ONLY_NON_POSITIVE_THRUST;
			message = "measured-non-positive-thrust-is-reference-only";
		} else if (transverseAirSpeed > EPSILON) {
			status = ForceApplicationStatus.REFERENCE_ONLY_TRANSVERSE_INFLOW;
			message = "transverse-inflow-lacks-uiuc-da4002-measured-force-direction-data";
		} else {
			status = ForceApplicationStatus.APPLIED_AXIAL_MEASURED;
			message = "uiuc-da4002-measured-axial-force-applied";
		}
		return resolvedSample(
				query,
				status,
				dimensional,
				signedAxialAdvanceSpeed,
				signedAdvanceRatio,
				transverseAir,
				transverseAirSpeed,
				inflowAngle,
				momentArm,
				thrustForce,
				reactionTorque,
				message
		);
	}

	private static ForceSample resolvedSample(
			Query query,
			ForceApplicationStatus status,
			UiucDa4002MeasuredRotorModel.DimensionalSample dimensional,
			double signedAxialAdvanceSpeed,
			double signedAdvanceRatio,
			Vec3 transverseAir,
			double transverseAirSpeed,
			double inflowAngle,
			Vec3 momentArm,
			Vec3 thrustForce,
			Vec3 reactionTorque,
			String message
	) {
		Vec3 thrustMoment = momentArm.cross(thrustForce);
		Vec3 totalTorque = thrustMoment.add(reactionTorque);
		double yawTorquePerThrust = dimensional.thrustNewtons() > EPSILON
				? dimensional.shaftTorqueNewtonMeters() / dimensional.thrustNewtons()
				: 0.0;
		return new ForceSample(
				query,
				status,
				Optional.of(dimensional),
				signedAxialAdvanceSpeed,
				signedAdvanceRatio,
				transverseAir,
				transverseAirSpeed,
				inflowAngle,
				thrustForce,
				reactionTorque,
				momentArm,
				thrustMoment,
				totalTorque,
				yawTorquePerThrust,
				message
		);
	}

	private static ForceSample unavailableSample(
			Query query,
			ForceApplicationStatus status,
			double signedAxialAdvanceSpeed,
			double signedAdvanceRatio,
			Vec3 transverseAir,
			double transverseAirSpeed,
			double inflowAngle,
			Vec3 momentArm,
			String message
	) {
		return new ForceSample(
				query,
				status,
				Optional.empty(),
				signedAxialAdvanceSpeed,
				signedAdvanceRatio,
				transverseAir,
				transverseAirSpeed,
				inflowAngle,
				Vec3.ZERO,
				Vec3.ZERO,
				momentArm,
				Vec3.ZERO,
				Vec3.ZERO,
				0.0,
				message
		);
	}

	private static boolean rotorDiameterMatchesMeasuredPropeller(Query query) {
		double rotorDiameter = query.rotor().radiusMeters() * 2.0;
		return Math.abs(rotorDiameter - query.propeller().diameterMeters())
				<= ROTOR_DIAMETER_TOLERANCE_METERS;
	}

	private static double inflowAngleRadians(
			double signedAxialAdvanceSpeed,
			double transverseAirSpeed
	) {
		if (transverseAirSpeed <= EPSILON && signedAxialAdvanceSpeed >= 0.0) {
			return 0.0;
		}
		if (transverseAirSpeed <= EPSILON) {
			return Math.PI;
		}
		return Math.atan2(transverseAirSpeed, signedAxialAdvanceSpeed);
	}

	private static double stableAdvanceRatio(double value) {
		double rounded = Math.rint(value * 1.0e12) / 1.0e12;
		return Math.abs(value - rounded) <= 8.0 * Math.ulp(value) ? rounded : value;
	}
}
