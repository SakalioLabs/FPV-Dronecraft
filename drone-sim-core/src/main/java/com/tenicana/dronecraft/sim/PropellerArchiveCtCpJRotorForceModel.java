package com.tenicana.dronecraft.sim;

public final class PropellerArchiveCtCpJRotorForceModel {
	private static final double EPSILON = 1.0e-9;

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
			Vec3 thrustForceBodyNewtons,
			Vec3 reactionTorqueBodyNewtonMeters,
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
			thrustForceBodyNewtons = finiteVecOrZero(thrustForceBodyNewtons);
			reactionTorqueBodyNewtonMeters = finiteVecOrZero(reactionTorqueBodyNewtonMeters);
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
			return !blocked() && !clamped();
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
		return forceSample(query, lookup, dimensional);
	}

	public static RotorForceSample sampleStaticAnchored(RotorForceQuery query) {
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
		return forceSample(query, lookup, dimensional);
	}

	private static RotorForceSample forceSample(
			RotorForceQuery query,
			PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup,
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample dimensional
	) {
		double axialAdvanceSpeed = lookup.blocked()
				? query.advanceRatioJ() * query.rpm() / 60.0 * query.propellerDiameterMeters()
				: dimensional.axialAdvanceSpeedMetersPerSecond();
		Vec3 axis = rotorAxisBody(query.rotor());
		Vec3 thrustForce = lookup.blocked()
				? Vec3.ZERO
				: axis.multiply(dimensional.thrustNewtons());
		Vec3 reactionTorque = lookup.blocked()
				? Vec3.ZERO
				: axis.multiply(query.rotor().spinDirection() * dimensional.shaftTorqueNewtonMeters());
		double yawTorquePerThrust = dimensional.thrustNewtons() > EPSILON
				? dimensional.shaftTorqueNewtonMeters() / dimensional.thrustNewtons()
				: 0.0;
		return new RotorForceSample(
				query,
				lookup,
				dimensional,
				axialAdvanceSpeed,
				thrustForce,
				reactionTorque,
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
		return sample(queryFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				axialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				envelopePolicy
		));
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
		return sampleStaticAnchored(queryFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				axialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				envelopePolicy
		));
	}

	private static Vec3 rotorAxisBody(RotorSpec rotor) {
		Vec3 axis = rotor.thrustAxisBody();
		if (axis == null || !axis.isFinite() || axis.lengthSquared() <= EPSILON) {
			return new Vec3(0.0, 1.0, 0.0);
		}
		return axis.normalized();
	}

	private static Vec3 finiteVecOrZero(Vec3 value) {
		return value == null || !value.isFinite() ? Vec3.ZERO : value;
	}
}
