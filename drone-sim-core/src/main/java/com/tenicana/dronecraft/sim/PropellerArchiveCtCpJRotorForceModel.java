package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveCtCpJRotorForceModel {
	private static final double EPSILON = 1.0e-9;
	private static final double MOMENTUM_POWER_CLOSURE_TOLERANCE = 1.0e-6;

	private PropellerArchiveCtCpJRotorForceModel() {
	}

	public record RotorForceQuery(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double advanceRatioJ,
			double rpm,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		public RotorForceQuery {
			if (rotor == null) {
				throw new IllegalArgumentException("rotor must not be null.");
			}
			if (!Double.isFinite(advanceRatioJ) || advanceRatioJ < 0.0) {
				throw new IllegalArgumentException("advanceRatioJ must be finite and nonnegative.");
			}
			if (!Double.isFinite(rpm) || rpm <= 0.0) {
				throw new IllegalArgumentException("rpm must be finite and positive.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (envelopePolicy == null) {
				envelopePolicy = PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE;
			}
		}

		private double propellerDiameterMeters() {
			return rotor.radiusMeters() * 2.0;
		}
	}

	public record RotorForceSample(
			RotorForceQuery query,
			PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup,
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dimensionalSample,
			double axialAdvanceSpeedMetersPerSecond,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 transverseAirVelocityBodyMetersPerSecond,
			double transverseAirSpeedMetersPerSecond,
			double inflowAngleRadians,
			Vec3 thrustForceBodyNewtons,
			Vec3 reactionTorqueBodyNewtonMeters,
			Vec3 momentArmBodyMeters,
			Vec3 thrustMomentBodyNewtonMeters,
			Vec3 totalTorqueBodyNewtonMeters,
			double yawTorquePerThrustMeterEquivalent
	) {
		public RotorForceSample {
			if (query == null) {
				throw new IllegalArgumentException("query must not be null.");
			}
			if (lookup == null) {
				throw new IllegalArgumentException("lookup must not be null.");
			}
			if (dimensionalSample == null) {
				throw new IllegalArgumentException("dimensionalSample must not be null.");
			}
			if (!Double.isFinite(axialAdvanceSpeedMetersPerSecond)) {
				throw new IllegalArgumentException("axialAdvanceSpeedMetersPerSecond must be finite.");
			}
			relativeAirVelocityBodyMetersPerSecond = finiteVecOrZero(relativeAirVelocityBodyMetersPerSecond);
			transverseAirVelocityBodyMetersPerSecond = finiteVecOrZero(transverseAirVelocityBodyMetersPerSecond);
			if (!Double.isFinite(transverseAirSpeedMetersPerSecond) || transverseAirSpeedMetersPerSecond < 0.0) {
				transverseAirSpeedMetersPerSecond = transverseAirVelocityBodyMetersPerSecond.length();
			}
			if (!Double.isFinite(inflowAngleRadians) || inflowAngleRadians < 0.0) {
				inflowAngleRadians = 0.0;
			}
			thrustForceBodyNewtons = finiteVecOrZero(thrustForceBodyNewtons);
			reactionTorqueBodyNewtonMeters = finiteVecOrZero(reactionTorqueBodyNewtonMeters);
			momentArmBodyMeters = finiteVecOrZero(momentArmBodyMeters);
			thrustMomentBodyNewtonMeters = finiteVecOrZero(thrustMomentBodyNewtonMeters);
			totalTorqueBodyNewtonMeters = finiteVecOrZero(totalTorqueBodyNewtonMeters);
			if (!Double.isFinite(yawTorquePerThrustMeterEquivalent)) {
				yawTorquePerThrustMeterEquivalent = 0.0;
			}
		}

		public boolean blocked() {
			return lookup.blocked();
		}

		public boolean clamped() {
			return lookup.clamped();
		}

		public boolean runtimeForceReplacementAccepted() {
			return !blocked() && !clamped() && momentumPowerClosureSatisfied();
		}

		public boolean momentumPowerClosureSatisfied() {
			double ratio = dimensionalSample.idealMomentumPowerOverShaftPower();
			return Double.isFinite(ratio)
					&& ratio > 0.0
					&& ratio <= 1.0 + MOMENTUM_POWER_CLOSURE_TOLERANCE;
		}

		public double thrustNewtons() {
			return dimensionalSample.thrustNewtons();
		}

		public double shaftPowerWatts() {
			return dimensionalSample.shaftPowerWatts();
		}

		public double shaftTorqueNewtonMeters() {
			return dimensionalSample.shaftTorqueNewtonMeters();
		}
	}

	public record RotorForceAggregateSample(
			List<RotorForceSample> rotorSamples,
			Vec3 totalThrustForceBodyNewtons,
			Vec3 totalReactionTorqueBodyNewtonMeters,
			Vec3 totalThrustMomentBodyNewtonMeters,
			Vec3 totalBodyTorqueNewtonMeters,
			double totalThrustNewtons,
			double totalShaftPowerWatts,
			double totalShaftTorqueNewtonMeters,
			int acceptedRotorCount,
			int blockedRotorCount,
			int clampedRotorCount
	) {
		public RotorForceAggregateSample {
			rotorSamples = rotorSamples == null ? List.of() : List.copyOf(rotorSamples);
			totalThrustForceBodyNewtons = finiteVecOrZero(totalThrustForceBodyNewtons);
			totalReactionTorqueBodyNewtonMeters = finiteVecOrZero(totalReactionTorqueBodyNewtonMeters);
			totalThrustMomentBodyNewtonMeters = finiteVecOrZero(totalThrustMomentBodyNewtonMeters);
			totalBodyTorqueNewtonMeters = finiteVecOrZero(totalBodyTorqueNewtonMeters);
			totalThrustNewtons = finiteNonnegative(totalThrustNewtons);
			totalShaftPowerWatts = finiteNonnegative(totalShaftPowerWatts);
			totalShaftTorqueNewtonMeters = finiteNonnegative(totalShaftTorqueNewtonMeters);
			acceptedRotorCount = Math.max(0, acceptedRotorCount);
			blockedRotorCount = Math.max(0, blockedRotorCount);
			clampedRotorCount = Math.max(0, clampedRotorCount);
		}
	}

	public static RotorForceQuery queryFromAxialAdvanceSpeed(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double axialAdvanceSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		if (!Double.isFinite(axialAdvanceSpeedMetersPerSecond) || axialAdvanceSpeedMetersPerSecond < 0.0) {
			throw new IllegalArgumentException("axialAdvanceSpeedMetersPerSecond must be finite and nonnegative.");
		}
		if (!Double.isFinite(omegaRadiansPerSecond) || omegaRadiansPerSecond <= 0.0) {
			throw new IllegalArgumentException("omegaRadiansPerSecond must be finite and positive.");
		}
		double rpm = omegaRadiansPerSecond * 60.0 / (2.0 * Math.PI);
		double revolutionsPerSecond = rpm / 60.0;
		double diameter = rotor.radiusMeters() * 2.0;
		double advanceRatioJ = axialAdvanceSpeedMetersPerSecond / Math.max(EPSILON, revolutionsPerSecond * diameter);
		return new RotorForceQuery(
				presetName,
				caseName,
				rotor,
				advanceRatioJ,
				rpm,
				airDensityKgPerCubicMeter,
				envelopePolicy
		);
	}

	public static RotorForceSample sample(RotorForceQuery query) {
		return sample(query, Vec3.ZERO);
	}

	public static RotorForceSample sample(
			RotorForceQuery query,
			Vec3 momentReferenceBodyMeters
	) {
		if (query == null) {
			throw new IllegalArgumentException("query must not be null.");
		}
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery lookupQuery =
				new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						query.presetName(),
						query.caseName(),
						query.advanceRatioJ(),
						query.rpm(),
						query.propellerDiameterMeters(),
						query.airDensityKgPerCubicMeter(),
						query.envelopePolicy()
				);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup =
				PropellerArchiveCtCpJLookupEvaluator.evaluate(lookupQuery);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dimensional =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(
						lookup,
						query.propellerDiameterMeters(),
						query.airDensityKgPerCubicMeter()
				);
		return forceSample(query, lookup, dimensional, momentReferenceBodyMeters, null);
	}

	public static RotorForceSample sampleStaticAnchored(RotorForceQuery query) {
		return sampleStaticAnchored(query, Vec3.ZERO);
	}

	public static RotorForceSample sampleStaticAnchored(
			RotorForceQuery query,
			Vec3 momentReferenceBodyMeters
	) {
		return sampleStaticAnchored(query, momentReferenceBodyMeters, null);
	}

	private static RotorForceSample sampleStaticAnchored(
			RotorForceQuery query,
			Vec3 momentReferenceBodyMeters,
			Vec3 relativeAirVelocityBodyMetersPerSecond
	) {
		if (query == null) {
			throw new IllegalArgumentException("query must not be null.");
		}
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery lookupQuery =
				new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						query.presetName(),
						query.caseName(),
						query.advanceRatioJ(),
						query.rpm(),
						query.propellerDiameterMeters(),
						query.airDensityKgPerCubicMeter(),
						query.envelopePolicy()
				);
		RotorStaticCtCpModel.StaticRotorSample staticSample = RotorStaticCtCpModel.sample(
				query.presetName(),
				query.caseName().isBlank() ? "static_anchored_forward_shape" : query.caseName(),
				query.rotor(),
				query.rpm(),
				query.airDensityKgPerCubicMeter()
		);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup =
				PropellerArchiveCtCpJLookupEvaluator.evaluateStaticAnchored(
						lookupQuery,
						staticSample.thrustCoefficientCt(),
						staticSample.powerCoefficientCp()
				);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dimensional =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(
						lookup,
						query.propellerDiameterMeters(),
						query.airDensityKgPerCubicMeter()
				);
		return forceSample(query, lookup, dimensional, momentReferenceBodyMeters,
				relativeAirVelocityBodyMetersPerSecond);
	}

	public static RotorForceSample sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		return sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				signedAxialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				Vec3.ZERO,
				envelopePolicy
		);
	}

	public static RotorForceSample sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			Vec3 momentReferenceBodyMeters,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		Vec3 relativeAirVelocityBodyMetersPerSecond =
				rotorAxisBody(rotor).multiply(finiteOrZero(signedAxialAdvanceSpeedMetersPerSecond));
		return sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				signedAxialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				relativeAirVelocityBodyMetersPerSecond,
				momentReferenceBodyMeters,
				envelopePolicy
		);
	}

	public static RotorForceSample sampleStaticAnchoredFromRelativeAirVelocity(
			String presetName,
			String caseName,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		return sampleStaticAnchoredFromRelativeAirVelocity(
				presetName,
				caseName,
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				Vec3.ZERO,
				envelopePolicy
		);
	}

	public static RotorForceSample sampleStaticAnchoredFromRelativeAirVelocity(
			String presetName,
			String caseName,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			Vec3 momentReferenceBodyMeters,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		Vec3 relativeAirVelocity = finiteRelativeAirVelocity(relativeAirVelocityBodyMetersPerSecond);
		double signedAxialAdvanceSpeedMetersPerSecond = relativeAirVelocity.dot(rotorAxisBody(rotor));
		return sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				signedAxialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				relativeAirVelocity,
				momentReferenceBodyMeters,
				envelopePolicy
		);
	}

	private static RotorForceSample sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 momentReferenceBodyMeters,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		if (!Double.isFinite(signedAxialAdvanceSpeedMetersPerSecond)) {
			throw new IllegalArgumentException("signedAxialAdvanceSpeedMetersPerSecond must be finite.");
		}
		Vec3 relativeAirVelocity = finiteRelativeAirVelocity(relativeAirVelocityBodyMetersPerSecond);
		if (signedAxialAdvanceSpeedMetersPerSecond >= 0.0) {
			RotorForceQuery query = queryFromAxialAdvanceSpeed(
					presetName,
					caseName,
					rotor,
					signedAxialAdvanceSpeedMetersPerSecond,
					omegaRadiansPerSecond,
					airDensityKgPerCubicMeter,
					envelopePolicy
			);
			return sampleStaticAnchored(query, momentReferenceBodyMeters, relativeAirVelocity);
		}
		if (envelopePolicy != PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE) {
			return sampleStaticAnchoredReverseAxialBlocked(
					presetName,
					caseName == null || caseName.isBlank() ? "reverse_axial_static_anchor" : caseName,
					rotor,
					omegaRadiansPerSecond,
					airDensityKgPerCubicMeter,
					relativeAirVelocity,
					momentReferenceBodyMeters
			);
		}
		return sampleStaticAnchoredReverseAxialClamped(
				presetName,
				caseName == null || caseName.isBlank() ? "reverse_axial_static_anchor" : caseName,
				rotor,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				relativeAirVelocity,
				momentReferenceBodyMeters
		);
	}

	private static RotorForceSample sampleStaticAnchoredReverseAxialClamped(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 momentReferenceBodyMeters
	) {
		RotorForceQuery query = queryFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				0.0,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE
		);
		RotorForceSample staticAnchor = sampleStaticAnchored(query, momentReferenceBodyMeters,
				relativeAirVelocityBodyMetersPerSecond);
		if (staticAnchor.blocked()) {
			return staticAnchor;
		}
		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup = staticAnchor.lookup();
		PropellerArchiveCtCpJLookupEvaluator.LookupResult clampedLookup =
				new PropellerArchiveCtCpJLookupEvaluator.LookupResult(
						lookup.presetName(),
						lookup.caseName(),
						lookup.dataSourceId(),
						lookup.queryAdvanceRatioJ(),
						lookup.queryRpm(),
						lookup.effectiveAdvanceRatioJ(),
						lookup.effectiveRpm(),
						lookup.lowerAdvanceRatioJ(),
						lookup.upperAdvanceRatioJ(),
						lookup.lowerRpm(),
						lookup.upperRpm(),
						lookup.advanceInterpolationFraction(),
						lookup.rpmInterpolationFraction(),
						lookup.observedNeighborRows(),
						lookup.minimumNeighborRowsRequired(),
						lookup.thrustCoefficientCt(),
						lookup.powerCoefficientCp(),
						lookup.propulsiveEfficiencyEta(),
						PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.CLAMPED_EXACT,
						true,
						false,
						"CLAMPED",
						"reverse-axial-flow-clamped-to-static-anchor"
				);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dimensional =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(
						clampedLookup,
						query.propellerDiameterMeters(),
						query.airDensityKgPerCubicMeter()
				);
		return forceSample(query, clampedLookup, dimensional, momentReferenceBodyMeters,
				relativeAirVelocityBodyMetersPerSecond);
	}

	private static RotorForceSample sampleStaticAnchoredReverseAxialBlocked(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 momentReferenceBodyMeters
	) {
		RotorForceQuery query = queryFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				0.0,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
		);
		PropellerArchiveCtCpJLookupEvaluator.LookupResult blockedLookup =
				new PropellerArchiveCtCpJLookupEvaluator.LookupResult(
						query.presetName(),
						query.caseName(),
						PropellerArchiveCtCpJLookupEvaluator.STATIC_ANCHORED_DATA_SOURCE_ID,
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
						PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BLOCKED,
						false,
						true,
						"OUT_OF_ENVELOPE_BLOCKED",
						"reverse-axial-flow-outside-ct-cp-j-envelope"
				);
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dimensional =
				PropellerArchiveCtCpJLookupEvaluator.sampleRotor(
						blockedLookup,
						query.propellerDiameterMeters(),
						query.airDensityKgPerCubicMeter()
				);
		return forceSample(query, blockedLookup, dimensional, momentReferenceBodyMeters,
				relativeAirVelocityBodyMetersPerSecond);
	}

	private static RotorForceSample forceSample(
			RotorForceQuery query,
			PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup,
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dimensional,
			Vec3 momentReferenceBodyMeters,
			Vec3 relativeAirVelocityBodyMetersPerSecond
	) {
		double axialAdvanceSpeed = lookup.blocked()
				? query.advanceRatioJ() * query.rpm() / 60.0 * query.propellerDiameterMeters()
				: dimensional.axialAdvanceSpeedMetersPerSecond();
		Vec3 axis = rotorAxisBody(query.rotor());
		Vec3 relativeAirVelocity = relativeAirVelocityBodyMetersPerSecond == null
				? axis.multiply(axialAdvanceSpeed)
				: finiteRelativeAirVelocity(relativeAirVelocityBodyMetersPerSecond);
		double axialVelocity = relativeAirVelocity.dot(axis);
		Vec3 transverseAirVelocity = relativeAirVelocity.subtract(axis.multiply(axialVelocity));
		double transverseAirSpeed = finiteNonnegative(transverseAirVelocity.length());
		double inflowAngle = Math.atan2(transverseAirSpeed, Math.max(EPSILON, Math.abs(axialVelocity)));
		Vec3 thrustForce = lookup.blocked()
				? Vec3.ZERO
				: axis.multiply(dimensional.thrustNewtons());
		Vec3 reactionTorque = lookup.blocked()
				? Vec3.ZERO
				: axis.multiply(query.rotor().spinDirection() * dimensional.shaftTorqueNewtonMeters());
		Vec3 momentReference = finiteVecOrZero(momentReferenceBodyMeters);
		Vec3 momentArm = query.rotor().positionBodyMeters().subtract(momentReference);
		Vec3 thrustMoment = momentArm.cross(thrustForce);
		Vec3 totalTorque = thrustMoment.add(reactionTorque);
		double yawTorquePerThrust = dimensional.thrustNewtons() > EPSILON
				? dimensional.shaftTorqueNewtonMeters() / dimensional.thrustNewtons()
				: 0.0;
		return new RotorForceSample(
				query,
				lookup,
				dimensional,
				axialAdvanceSpeed,
				relativeAirVelocity,
				transverseAirVelocity,
				transverseAirSpeed,
				inflowAngle,
				thrustForce,
				reactionTorque,
				momentArm,
				thrustMoment,
				totalTorque,
				yawTorquePerThrust
		);
	}

	public static RotorForceSample sampleFromAxialAdvanceSpeed(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double axialAdvanceSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		return sampleFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				axialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				Vec3.ZERO,
				envelopePolicy
		);
	}

	public static RotorForceSample sampleFromAxialAdvanceSpeed(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double axialAdvanceSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			Vec3 momentReferenceBodyMeters,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		return sample(queryFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				axialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				envelopePolicy
		), momentReferenceBodyMeters);
	}

	public static RotorForceSample sampleStaticAnchoredFromAxialAdvanceSpeed(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double axialAdvanceSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		return sampleStaticAnchoredFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				axialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				Vec3.ZERO,
				envelopePolicy
		);
	}

	public static RotorForceSample sampleStaticAnchoredFromAxialAdvanceSpeed(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double axialAdvanceSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			Vec3 momentReferenceBodyMeters,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		return sampleStaticAnchored(queryFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				axialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				envelopePolicy
		), momentReferenceBodyMeters);
	}

	public static RotorForceAggregateSample aggregate(List<RotorForceSample> samples) {
		if (samples == null || samples.isEmpty()) {
			return new RotorForceAggregateSample(List.of(), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
					0.0, 0.0, 0.0, 0, 0, 0);
		}
		List<RotorForceSample> acceptedSamples = new ArrayList<>();
		Vec3 totalForce = Vec3.ZERO;
		Vec3 totalReactionTorque = Vec3.ZERO;
		Vec3 totalThrustMoment = Vec3.ZERO;
		Vec3 totalBodyTorque = Vec3.ZERO;
		double totalThrust = 0.0;
		double totalPower = 0.0;
		double totalShaftTorque = 0.0;
		int accepted = 0;
		int blocked = 0;
		int clamped = 0;
		for (RotorForceSample sample : samples) {
			if (sample == null) {
				continue;
			}
			acceptedSamples.add(sample);
			totalForce = totalForce.add(sample.thrustForceBodyNewtons());
			totalReactionTorque = totalReactionTorque.add(sample.reactionTorqueBodyNewtonMeters());
			totalThrustMoment = totalThrustMoment.add(sample.thrustMomentBodyNewtonMeters());
			totalBodyTorque = totalBodyTorque.add(sample.totalTorqueBodyNewtonMeters());
			totalThrust += sample.thrustNewtons();
			totalPower += sample.shaftPowerWatts();
			totalShaftTorque += sample.shaftTorqueNewtonMeters();
			if (sample.blocked()) {
				blocked++;
			} else {
				accepted++;
			}
			if (sample.clamped()) {
				clamped++;
			}
		}
		return new RotorForceAggregateSample(
				acceptedSamples,
				totalForce,
				totalReactionTorque,
				totalThrustMoment,
				totalBodyTorque,
				totalThrust,
				totalPower,
				totalShaftTorque,
				accepted,
				blocked,
				clamped
		);
	}

	public static RotorForceAggregateSample sampleStaticAnchoredConfigurationFromRelativeAirVelocities(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3[] relativeAirVelocitiesBody,
			double[] omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (relativeAirVelocitiesBody == null
				|| omegaRadiansPerSecond == null
				|| relativeAirVelocitiesBody.length < config.rotors().size()
				|| omegaRadiansPerSecond.length < config.rotors().size()) {
			throw new IllegalArgumentException("relative air velocity and rotor speed arrays must cover every rotor.");
		}
		List<RotorForceSample> samples = new ArrayList<>();
		Vec3 momentReference = config.centerOfMassOffsetBodyMeters();
		for (int i = 0; i < config.rotors().size(); i++) {
			Vec3 relativeAirVelocityBody = relativeAirVelocitiesBody[i];
			if (relativeAirVelocityBody == null || !relativeAirVelocityBody.isFinite()) {
				throw new IllegalArgumentException("relative air velocity must be finite for every rotor.");
			}
			samples.add(sampleStaticAnchoredFromRelativeAirVelocity(
					presetName,
					caseName,
					config.rotors().get(i),
					relativeAirVelocityBody,
					omegaRadiansPerSecond[i],
					airDensityKgPerCubicMeter,
					momentReference,
					envelopePolicy
			));
		}
		return aggregate(samples);
	}

	public static RotorForceAggregateSample sampleStaticAnchoredConfigurationFromSignedAxialAdvanceSpeeds(
			String presetName,
			String caseName,
			DroneConfig config,
			double[] signedAxialAdvanceSpeedsMetersPerSecond,
			double[] omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (signedAxialAdvanceSpeedsMetersPerSecond == null
				|| omegaRadiansPerSecond == null
				|| signedAxialAdvanceSpeedsMetersPerSecond.length < config.rotors().size()
				|| omegaRadiansPerSecond.length < config.rotors().size()) {
			throw new IllegalArgumentException("rotor speed arrays must cover every configured rotor.");
		}
		List<RotorForceSample> samples = new ArrayList<>();
		Vec3 momentReference = config.centerOfMassOffsetBodyMeters();
		for (int i = 0; i < config.rotors().size(); i++) {
			samples.add(sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
					presetName,
					caseName,
					config.rotors().get(i),
					signedAxialAdvanceSpeedsMetersPerSecond[i],
					omegaRadiansPerSecond[i],
					airDensityKgPerCubicMeter,
					momentReference,
					envelopePolicy
			));
		}
		return aggregate(samples);
	}

	private static Vec3 rotorAxisBody(RotorSpec rotor) {
		if (rotor == null) {
			return new Vec3(0.0, 1.0, 0.0);
		}
		Vec3 axis = rotor.thrustAxisBody();
		if (axis == null || !axis.isFinite() || axis.lengthSquared() <= EPSILON) {
			return new Vec3(0.0, 1.0, 0.0);
		}
		return axis.normalized();
	}

	private static Vec3 finiteRelativeAirVelocity(Vec3 value) {
		if (value == null || !value.isFinite()) {
			throw new IllegalArgumentException("relativeAirVelocityBodyMetersPerSecond must be finite.");
		}
		return value;
	}

	private static double finiteNonnegative(double value) {
		return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}

	private static Vec3 finiteVecOrZero(Vec3 value) {
		return value == null || !value.isFinite() ? Vec3.ZERO : value;
	}
}
