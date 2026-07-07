package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveCtCpJRotorForceModel {
	private static final double EPSILON = 1.0e-9;
	private static final double MOMENTUM_POWER_CLOSURE_TOLERANCE = 1.0e-6;
	private static final double TARGET_THRUST_SOLVE_RELATIVE_TOLERANCE = 1.0e-6;
	private static final int TARGET_THRUST_SOLVE_MAX_ITERATIONS = 56;
	private static final double RUNTIME_REPLACEMENT_MAX_INFLOW_ANGLE_RADIANS = Math.toRadians(15.0);
	private static final double RUNTIME_REPLACEMENT_STATIC_TRANSVERSE_TOLERANCE_METERS_PER_SECOND = 0.35;
	private static final double RUNTIME_REPLACEMENT_MAX_TIP_MACH = 0.46;
	private static final double RUNTIME_REPLACEMENT_MIN_REYNOLDS_INDEX = 0.52;
	private static final double STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS = 25.0;
	private static final double SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER = 1.225;
	private static final double REFERENCE_AIR_TEMPERATURE_KELVIN = 298.15;
	private static final double REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.837e-5;
	private static final double AIR_SUTHERLAND_CONSTANT_KELVIN = 110.4;

	private PropellerArchiveCtCpJRotorForceModel() {
	}

	public record RotorForceQuery(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double advanceRatioJ,
			double rpm,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		public RotorForceQuery(
				String presetName,
				String caseName,
				RotorSpec rotor,
				double advanceRatioJ,
				double rpm,
				double airDensityKgPerCubicMeter,
				PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
		) {
			this(
					presetName,
					caseName,
					rotor,
					advanceRatioJ,
					rpm,
					airDensityKgPerCubicMeter,
					envelopePolicy,
					STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
					0.0
			);
		}

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
			ambientTemperatureCelsius = Double.isFinite(ambientTemperatureCelsius)
					? MathUtil.clamp(ambientTemperatureCelsius, -40.0, 65.0)
					: STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS;
			ambientHumidity = Double.isFinite(ambientHumidity)
					? MathUtil.clamp(ambientHumidity, 0.0, 1.0)
					: 0.0;
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
			return runtimeForceReplacementAccepted(query.ambientTemperatureCelsius(), query.ambientHumidity());
		}

		public boolean runtimeForceReplacementAccepted(double ambientTemperatureCelsius, double ambientHumidity) {
			return !blocked()
					&& !clamped()
					&& momentumPowerClosureSatisfied()
					&& wakePowerClosureSatisfied()
					&& runtimeInflowEnvelopeSatisfied()
					&& runtimeOperatingPointEnvelopeSatisfied(ambientTemperatureCelsius, ambientHumidity);
		}

		public boolean runtimeInflowEnvelopeSatisfied() {
			return (transverseAirSpeedMetersPerSecond <= RUNTIME_REPLACEMENT_STATIC_TRANSVERSE_TOLERANCE_METERS_PER_SECOND
					&& inflowAngleRadians <= Math.PI * 0.5)
					|| inflowAngleRadians <= RUNTIME_REPLACEMENT_MAX_INFLOW_ANGLE_RADIANS;
		}

		public boolean momentumPowerClosureSatisfied() {
			double ratio = dimensionalSample.idealMomentumPowerOverShaftPower();
			return Double.isFinite(ratio)
					&& ratio > 0.0
					&& ratio <= 1.0 + MOMENTUM_POWER_CLOSURE_TOLERANCE;
		}

		public boolean wakePowerClosureSatisfied() {
			double ratio = dimensionalSample.totalWakeKineticPowerOverShaftPower();
			return Double.isFinite(ratio)
					&& ratio > 0.0
					&& ratio <= 1.0 + MOMENTUM_POWER_CLOSURE_TOLERANCE;
		}

		public boolean runtimeOperatingPointEnvelopeSatisfied() {
			return runtimeOperatingPointEnvelopeSatisfied(
					query.ambientTemperatureCelsius(),
					query.ambientHumidity()
			);
		}

		public boolean runtimeOperatingPointEnvelopeSatisfied(double ambientTemperatureCelsius, double ambientHumidity) {
			RotorOperatingPoint operatingPoint = operatingPoint(ambientTemperatureCelsius, ambientHumidity);
			return operatingPoint.runtimeTipMachMargin() >= -EPSILON
					&& operatingPoint.runtimeReynoldsIndexMargin() >= -EPSILON;
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

		public Vec3 relativeAirVelocityWorldMetersPerSecond(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(relativeAirVelocityBodyMetersPerSecond, bodyToWorldOrientation);
		}

		public Vec3 transverseAirVelocityWorldMetersPerSecond(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(transverseAirVelocityBodyMetersPerSecond, bodyToWorldOrientation);
		}

		public Vec3 thrustForceWorldNewtons(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(thrustForceBodyNewtons, bodyToWorldOrientation);
		}

		public Vec3 reactionTorqueWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(reactionTorqueBodyNewtonMeters, bodyToWorldOrientation);
		}

		public Vec3 momentArmWorldMeters(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(momentArmBodyMeters, bodyToWorldOrientation);
		}

		public Vec3 forceApplicationPointWorldMeters(
				Vec3 momentReferenceWorldMeters,
				Quaternion bodyToWorldOrientation
		) {
			return finiteVecOrZero(momentReferenceWorldMeters).add(momentArmWorldMeters(bodyToWorldOrientation));
		}

		public Vec3 thrustMomentWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(thrustMomentBodyNewtonMeters, bodyToWorldOrientation);
		}

		public Vec3 totalTorqueWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(totalTorqueBodyNewtonMeters, bodyToWorldOrientation);
		}

		public RotorWorldForceApplicationSample worldForceApplication(
				int rotorIndex,
				Vec3 momentReferenceWorldMeters,
				Quaternion bodyToWorldOrientation
		) {
			return new RotorWorldForceApplicationSample(
					rotorIndex,
					forceApplicationPointWorldMeters(momentReferenceWorldMeters, bodyToWorldOrientation),
					thrustForceWorldNewtons(bodyToWorldOrientation),
					reactionTorqueWorldNewtonMeters(bodyToWorldOrientation),
					thrustMomentWorldNewtonMeters(bodyToWorldOrientation),
					totalTorqueWorldNewtonMeters(bodyToWorldOrientation),
					runtimeForceReplacementAccepted(),
					!blocked(),
					lookup.status()
			);
		}

		public RotorWorldForceApplicationSample runtimeForceReplacementWorldForceApplication(
				int rotorIndex,
				Vec3 momentReferenceWorldMeters,
				Quaternion bodyToWorldOrientation
		) {
			boolean accepted = runtimeForceReplacementAccepted();
			Vec3 thrustForceWorld = accepted ? thrustForceWorldNewtons(bodyToWorldOrientation) : Vec3.ZERO;
			Vec3 reactionTorqueWorld = accepted ? reactionTorqueWorldNewtonMeters(bodyToWorldOrientation) : Vec3.ZERO;
			Vec3 thrustMomentWorld = accepted ? thrustMomentWorldNewtonMeters(bodyToWorldOrientation) : Vec3.ZERO;
			Vec3 totalTorqueWorld = accepted ? totalTorqueWorldNewtonMeters(bodyToWorldOrientation) : Vec3.ZERO;
			return new RotorWorldForceApplicationSample(
					rotorIndex,
					forceApplicationPointWorldMeters(momentReferenceWorldMeters, bodyToWorldOrientation),
					thrustForceWorld,
					reactionTorqueWorld,
					thrustMomentWorld,
					totalTorqueWorld,
					accepted,
					accepted,
					lookup.status()
			);
		}

		public RotorOperatingPoint standardOperatingPoint() {
			return operatingPoint(STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS);
		}

		public RotorOperatingPoint operatingPoint(double ambientTemperatureCelsius) {
			return operatingPoint(ambientTemperatureCelsius, 0.0);
		}

		public RotorOperatingPoint operatingPoint(double ambientTemperatureCelsius, double ambientHumidity) {
			return PropellerArchiveCtCpJRotorForceModel.operatingPoint(
					query.rotor(),
					relativeAirVelocityBodyMetersPerSecond,
					dimensionalSample.angularVelocityRadiansPerSecond(),
					query.airDensityKgPerCubicMeter(),
					ambientTemperatureCelsius,
					ambientHumidity
			);
		}
	}

	public record RotorWorldForceApplicationSample(
			int rotorIndex,
			Vec3 forceApplicationPointWorldMeters,
			Vec3 thrustForceWorldNewtons,
			Vec3 reactionTorqueWorldNewtonMeters,
			Vec3 thrustMomentWorldNewtonMeters,
			Vec3 totalTorqueWorldNewtonMeters,
			boolean runtimeForceReplacementAccepted,
			boolean applied,
			String lookupStatus
	) {
		public RotorWorldForceApplicationSample {
			rotorIndex = Math.max(0, rotorIndex);
			forceApplicationPointWorldMeters = finiteVecOrZero(forceApplicationPointWorldMeters);
			thrustForceWorldNewtons = finiteVecOrZero(thrustForceWorldNewtons);
			reactionTorqueWorldNewtonMeters = finiteVecOrZero(reactionTorqueWorldNewtonMeters);
			thrustMomentWorldNewtonMeters = finiteVecOrZero(thrustMomentWorldNewtonMeters);
			totalTorqueWorldNewtonMeters = finiteVecOrZero(totalTorqueWorldNewtonMeters);
			lookupStatus = lookupStatus == null ? "" : lookupStatus;
		}
	}

	public record RotorOperatingPoint(
			double ambientTemperatureCelsius,
			double ambientHumidity,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			double speedOfSoundMetersPerSecond,
			double rotationalTipSpeedMetersPerSecond,
			double helicalTipSpeedMetersPerSecond,
			double tipMach,
			double representativeBladeStationSpeedMetersPerSecond,
			double representativeBladeChordMeters,
			double reynoldsNumber,
			double reynoldsIndex
	) {
		public RotorOperatingPoint {
			ambientTemperatureCelsius = Double.isFinite(ambientTemperatureCelsius)
					? MathUtil.clamp(ambientTemperatureCelsius, -40.0, 65.0)
					: STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS;
			ambientHumidity = Double.isFinite(ambientHumidity)
					? MathUtil.clamp(ambientHumidity, 0.0, 1.0)
					: 0.0;
			airDensityKgPerCubicMeter = finiteNonnegative(airDensityKgPerCubicMeter);
			dynamicViscosityPascalSeconds = finiteNonnegative(dynamicViscosityPascalSeconds);
			speedOfSoundMetersPerSecond = finiteNonnegative(speedOfSoundMetersPerSecond);
			rotationalTipSpeedMetersPerSecond = finiteNonnegative(rotationalTipSpeedMetersPerSecond);
			helicalTipSpeedMetersPerSecond = finiteNonnegative(helicalTipSpeedMetersPerSecond);
			tipMach = finiteNonnegative(tipMach);
			representativeBladeStationSpeedMetersPerSecond =
					finiteNonnegative(representativeBladeStationSpeedMetersPerSecond);
			representativeBladeChordMeters = finiteNonnegative(representativeBladeChordMeters);
			reynoldsNumber = finiteNonnegative(reynoldsNumber);
			reynoldsIndex = finiteNonnegative(reynoldsIndex);
		}

		public double runtimeTipMachMargin() {
			return RUNTIME_REPLACEMENT_MAX_TIP_MACH - tipMach;
		}

		public double runtimeReynoldsIndexMargin() {
			return reynoldsIndex - RUNTIME_REPLACEMENT_MIN_REYNOLDS_INDEX;
		}

		public double runtimeOperatingEnvelopeMarginFraction() {
			double machMargin = runtimeTipMachMargin() / RUNTIME_REPLACEMENT_MAX_TIP_MACH;
			double reynoldsMargin = runtimeReynoldsIndexMargin() / RUNTIME_REPLACEMENT_MIN_REYNOLDS_INDEX;
			return Math.min(machMargin, reynoldsMargin);
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
			double totalWakeAngularMomentumTorqueNewtonMeters,
			double totalWakeAngularMomentumTorqueResidualNewtonMeters,
			double totalWakeAngularMomentumTorqueResidualFraction,
			double totalDiskMassFlowKilogramsPerSecond,
			double totalUsefulAxialThrustPowerWatts,
			double totalIdealInducedPowerWatts,
			double totalIdealMomentumPowerWatts,
			double totalWakeSwirlKineticPowerWatts,
			double totalWakeKineticPowerWatts,
			double totalWakeKineticPowerResidualWatts,
			double totalWakeKineticPowerOverShaftPower,
			Vec3 runtimeForceReplacementThrustForceBodyNewtons,
			Vec3 runtimeForceReplacementReactionTorqueBodyNewtonMeters,
			Vec3 runtimeForceReplacementThrustMomentBodyNewtonMeters,
			Vec3 runtimeForceReplacementTotalBodyTorqueNewtonMeters,
			double runtimeForceReplacementThrustNewtons,
			double runtimeForceReplacementShaftPowerWatts,
			double runtimeForceReplacementShaftTorqueNewtonMeters,
			double runtimeForceReplacementWakeAngularMomentumTorqueNewtonMeters,
			double runtimeForceReplacementWakeAngularMomentumTorqueResidualNewtonMeters,
			double runtimeForceReplacementWakeAngularMomentumTorqueResidualFraction,
			double runtimeForceReplacementDiskMassFlowKilogramsPerSecond,
			double runtimeForceReplacementUsefulAxialThrustPowerWatts,
			double runtimeForceReplacementIdealInducedPowerWatts,
			double runtimeForceReplacementIdealMomentumPowerWatts,
			double runtimeForceReplacementWakeSwirlKineticPowerWatts,
			double runtimeForceReplacementWakeKineticPowerWatts,
			double runtimeForceReplacementWakeKineticPowerResidualWatts,
			double runtimeForceReplacementWakeKineticPowerOverShaftPower,
			int acceptedRotorCount,
			int runtimeForceReplacementAcceptedRotorCount,
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
			totalWakeAngularMomentumTorqueNewtonMeters =
					finiteNonnegative(totalWakeAngularMomentumTorqueNewtonMeters);
			totalWakeAngularMomentumTorqueResidualNewtonMeters =
					finiteOrZero(totalWakeAngularMomentumTorqueResidualNewtonMeters);
			totalWakeAngularMomentumTorqueResidualFraction =
					finiteOrZero(totalWakeAngularMomentumTorqueResidualFraction);
			totalDiskMassFlowKilogramsPerSecond = finiteNonnegative(totalDiskMassFlowKilogramsPerSecond);
			totalUsefulAxialThrustPowerWatts = finiteNonnegative(totalUsefulAxialThrustPowerWatts);
			totalIdealInducedPowerWatts = finiteNonnegative(totalIdealInducedPowerWatts);
			totalIdealMomentumPowerWatts = finiteNonnegative(totalIdealMomentumPowerWatts);
			totalWakeSwirlKineticPowerWatts = finiteNonnegative(totalWakeSwirlKineticPowerWatts);
			totalWakeKineticPowerWatts = finiteNonnegative(totalWakeKineticPowerWatts);
			totalWakeKineticPowerResidualWatts = finiteOrZero(totalWakeKineticPowerResidualWatts);
			totalWakeKineticPowerOverShaftPower =
					finiteNonnegative(totalWakeKineticPowerOverShaftPower);
			runtimeForceReplacementThrustForceBodyNewtons =
					finiteVecOrZero(runtimeForceReplacementThrustForceBodyNewtons);
			runtimeForceReplacementReactionTorqueBodyNewtonMeters =
					finiteVecOrZero(runtimeForceReplacementReactionTorqueBodyNewtonMeters);
			runtimeForceReplacementThrustMomentBodyNewtonMeters =
					finiteVecOrZero(runtimeForceReplacementThrustMomentBodyNewtonMeters);
			runtimeForceReplacementTotalBodyTorqueNewtonMeters =
					finiteVecOrZero(runtimeForceReplacementTotalBodyTorqueNewtonMeters);
			runtimeForceReplacementThrustNewtons = finiteNonnegative(runtimeForceReplacementThrustNewtons);
			runtimeForceReplacementShaftPowerWatts = finiteNonnegative(runtimeForceReplacementShaftPowerWatts);
			runtimeForceReplacementShaftTorqueNewtonMeters =
					finiteNonnegative(runtimeForceReplacementShaftTorqueNewtonMeters);
			runtimeForceReplacementWakeAngularMomentumTorqueNewtonMeters =
					finiteNonnegative(runtimeForceReplacementWakeAngularMomentumTorqueNewtonMeters);
			runtimeForceReplacementWakeAngularMomentumTorqueResidualNewtonMeters =
					finiteOrZero(runtimeForceReplacementWakeAngularMomentumTorqueResidualNewtonMeters);
			runtimeForceReplacementWakeAngularMomentumTorqueResidualFraction =
					finiteOrZero(runtimeForceReplacementWakeAngularMomentumTorqueResidualFraction);
			runtimeForceReplacementDiskMassFlowKilogramsPerSecond =
					finiteNonnegative(runtimeForceReplacementDiskMassFlowKilogramsPerSecond);
			runtimeForceReplacementUsefulAxialThrustPowerWatts =
					finiteNonnegative(runtimeForceReplacementUsefulAxialThrustPowerWatts);
			runtimeForceReplacementIdealInducedPowerWatts =
					finiteNonnegative(runtimeForceReplacementIdealInducedPowerWatts);
			runtimeForceReplacementIdealMomentumPowerWatts =
					finiteNonnegative(runtimeForceReplacementIdealMomentumPowerWatts);
			runtimeForceReplacementWakeSwirlKineticPowerWatts =
					finiteNonnegative(runtimeForceReplacementWakeSwirlKineticPowerWatts);
			runtimeForceReplacementWakeKineticPowerWatts =
					finiteNonnegative(runtimeForceReplacementWakeKineticPowerWatts);
			runtimeForceReplacementWakeKineticPowerResidualWatts =
					finiteOrZero(runtimeForceReplacementWakeKineticPowerResidualWatts);
			runtimeForceReplacementWakeKineticPowerOverShaftPower =
					finiteNonnegative(runtimeForceReplacementWakeKineticPowerOverShaftPower);
			acceptedRotorCount = Math.max(0, acceptedRotorCount);
			runtimeForceReplacementAcceptedRotorCount = Math.max(0, runtimeForceReplacementAcceptedRotorCount);
			blockedRotorCount = Math.max(0, blockedRotorCount);
			clampedRotorCount = Math.max(0, clampedRotorCount);
		}

		public Vec3 totalThrustForceWorldNewtons(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(totalThrustForceBodyNewtons, bodyToWorldOrientation);
		}

		public Vec3 totalReactionTorqueWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(totalReactionTorqueBodyNewtonMeters, bodyToWorldOrientation);
		}

		public Vec3 totalThrustMomentWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(totalThrustMomentBodyNewtonMeters, bodyToWorldOrientation);
		}

		public Vec3 totalBodyTorqueWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(totalBodyTorqueNewtonMeters, bodyToWorldOrientation);
		}

		public Vec3 runtimeForceReplacementThrustForceWorldNewtons(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(runtimeForceReplacementThrustForceBodyNewtons, bodyToWorldOrientation);
		}

		public Vec3 runtimeForceReplacementReactionTorqueWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(
					runtimeForceReplacementReactionTorqueBodyNewtonMeters,
					bodyToWorldOrientation
			);
		}

		public Vec3 runtimeForceReplacementThrustMomentWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(
					runtimeForceReplacementThrustMomentBodyNewtonMeters,
					bodyToWorldOrientation
			);
		}

		public Vec3 runtimeForceReplacementTotalBodyTorqueWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return rotateBodyVectorToWorld(
					runtimeForceReplacementTotalBodyTorqueNewtonMeters,
					bodyToWorldOrientation
			);
		}

		public List<RotorWorldForceApplicationSample> rotorWorldForceApplications(
				Vec3 momentReferenceWorldMeters,
				Quaternion bodyToWorldOrientation
		) {
			List<RotorWorldForceApplicationSample> applications = new ArrayList<>();
			for (int i = 0; i < rotorSamples.size(); i++) {
				applications.add(rotorSamples.get(i).worldForceApplication(
						i,
						momentReferenceWorldMeters,
						bodyToWorldOrientation
				));
			}
			return List.copyOf(applications);
		}

		public List<RotorWorldForceApplicationSample> runtimeForceReplacementRotorWorldForceApplications(
				Vec3 momentReferenceWorldMeters,
				Quaternion bodyToWorldOrientation
		) {
			List<RotorWorldForceApplicationSample> applications = new ArrayList<>();
			for (int i = 0; i < rotorSamples.size(); i++) {
				applications.add(rotorSamples.get(i).runtimeForceReplacementWorldForceApplication(
						i,
						momentReferenceWorldMeters,
						bodyToWorldOrientation
				));
			}
			return List.copyOf(applications);
		}
	}

	public enum RotorTargetThrustSolveStatus {
		SOLVED,
		LOWER_BOUND_EXCEEDS_TARGET,
		UPPER_BOUND_BLOCKED,
		UPPER_BOUND_BELOW_TARGET,
		NO_CONVERGENCE
	}

	public enum ConfigurationTrimSolveStatus {
		SOLVED,
		ALLOCATION_UNAVAILABLE,
		NEGATIVE_ALLOCATED_THRUST,
		ROTOR_SOLVE_BLOCKED
	}

	public record RotorTargetThrustSolution(
			RotorTargetThrustSolveStatus status,
			double targetThrustNewtons,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			int iterations,
			RotorForceSample lowerBoundSample,
			RotorForceSample upperBoundSample,
			RotorForceSample solutionSample
	) {
		public RotorTargetThrustSolution {
			if (status == null) {
				status = RotorTargetThrustSolveStatus.NO_CONVERGENCE;
			}
			targetThrustNewtons = finiteNonnegative(targetThrustNewtons);
			signedAxialAdvanceSpeedMetersPerSecond = finiteOrZero(signedAxialAdvanceSpeedMetersPerSecond);
			lowerOmegaRadiansPerSecond = finiteNonnegative(lowerOmegaRadiansPerSecond);
			upperOmegaRadiansPerSecond = finiteNonnegative(upperOmegaRadiansPerSecond);
			iterations = Math.max(0, iterations);
		}

		public boolean solved() {
			return status == RotorTargetThrustSolveStatus.SOLVED
					&& solutionSample != null
					&& !solutionSample.blocked();
		}

		public boolean blocked() {
			return !solved();
		}

		public boolean clamped() {
			return (solutionSample != null && solutionSample.clamped())
					|| (lowerBoundSample != null && lowerBoundSample.clamped())
					|| (upperBoundSample != null && upperBoundSample.clamped());
		}

		public double solutionOmegaRadiansPerSecond() {
			return solutionSample == null
					? 0.0
					: solutionSample.dimensionalSample().angularVelocityRadiansPerSecond();
		}

		public double solutionRpm() {
			return solutionSample == null ? 0.0 : solutionSample.query().rpm();
		}

		public double thrustResidualNewtons() {
			return solutionSample == null ? 0.0 : solutionSample.thrustNewtons() - targetThrustNewtons;
		}

		public double absoluteThrustResidualNewtons() {
			return Math.abs(thrustResidualNewtons());
		}

		public double thrustResidualFraction() {
			return ratio(thrustResidualNewtons(), targetThrustNewtons);
		}
	}

	public record ConfigurationTargetThrustSolution(
			RotorTargetThrustSolveStatus status,
			double targetThrustNewtons,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			int iterations,
			RotorForceAggregateSample lowerBoundSample,
			RotorForceAggregateSample upperBoundSample,
			RotorForceAggregateSample solutionSample
	) {
		public ConfigurationTargetThrustSolution {
			if (status == null) {
				status = RotorTargetThrustSolveStatus.NO_CONVERGENCE;
			}
			targetThrustNewtons = finiteNonnegative(targetThrustNewtons);
			signedAxialAdvanceSpeedMetersPerSecond = finiteOrZero(signedAxialAdvanceSpeedMetersPerSecond);
			lowerOmegaRadiansPerSecond = finiteNonnegative(lowerOmegaRadiansPerSecond);
			upperOmegaRadiansPerSecond = finiteNonnegative(upperOmegaRadiansPerSecond);
			iterations = Math.max(0, iterations);
		}

		public boolean solved() {
			return status == RotorTargetThrustSolveStatus.SOLVED
					&& solutionSample != null
					&& solutionSample.blockedRotorCount() == 0;
		}

		public boolean blocked() {
			return !solved();
		}

		public boolean clamped() {
			return (solutionSample != null && solutionSample.clampedRotorCount() > 0)
					|| (lowerBoundSample != null && lowerBoundSample.clampedRotorCount() > 0)
					|| (upperBoundSample != null && upperBoundSample.clampedRotorCount() > 0);
		}

		public double solutionOmegaRadiansPerSecond() {
			if (solutionSample == null || solutionSample.rotorSamples().isEmpty()) {
				return 0.0;
			}
			return solutionSample.rotorSamples().get(0)
					.dimensionalSample()
					.angularVelocityRadiansPerSecond();
		}

		public double solutionRpm() {
			if (solutionSample == null || solutionSample.rotorSamples().isEmpty()) {
				return 0.0;
			}
			return solutionSample.rotorSamples().get(0).query().rpm();
		}

		public double thrustResidualNewtons() {
			return solutionSample == null ? 0.0 : solutionSample.totalThrustNewtons() - targetThrustNewtons;
		}

		public double absoluteThrustResidualNewtons() {
			return Math.abs(thrustResidualNewtons());
		}

		public double thrustResidualFraction() {
			return ratio(thrustResidualNewtons(), targetThrustNewtons);
		}
	}

	public record ConfigurationTrimSolution(
			ConfigurationTrimSolveStatus status,
			double targetThrustNewtons,
			Vec3 targetBodyTorqueNewtonMeters,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double[] allocatedRotorThrustsNewtons,
			List<RotorTargetThrustSolution> rotorSolutions,
			RotorForceAggregateSample solutionSample
	) {
		public ConfigurationTrimSolution {
			if (status == null) {
				status = ConfigurationTrimSolveStatus.ALLOCATION_UNAVAILABLE;
			}
			targetThrustNewtons = finiteNonnegative(targetThrustNewtons);
			targetBodyTorqueNewtonMeters = finiteVecOrZero(targetBodyTorqueNewtonMeters);
			signedAxialAdvanceSpeedMetersPerSecond = finiteOrZero(signedAxialAdvanceSpeedMetersPerSecond);
			lowerOmegaRadiansPerSecond = finiteNonnegative(lowerOmegaRadiansPerSecond);
			upperOmegaRadiansPerSecond = finiteNonnegative(upperOmegaRadiansPerSecond);
			allocatedRotorThrustsNewtons = allocatedRotorThrustsNewtons == null
					? new double[0]
					: allocatedRotorThrustsNewtons.clone();
			for (int i = 0; i < allocatedRotorThrustsNewtons.length; i++) {
				allocatedRotorThrustsNewtons[i] = finiteOrZero(allocatedRotorThrustsNewtons[i]);
			}
			rotorSolutions = rotorSolutions == null ? List.of() : List.copyOf(rotorSolutions);
		}

		public double[] allocatedRotorThrustsNewtons() {
			return allocatedRotorThrustsNewtons.clone();
		}

		public boolean solved() {
			return status == ConfigurationTrimSolveStatus.SOLVED
					&& solutionSample != null
					&& solutionSample.blockedRotorCount() == 0;
		}

		public boolean blocked() {
			return !solved();
		}

		public double thrustResidualNewtons() {
			return solutionSample == null ? 0.0 : solutionSample.totalThrustNewtons() - targetThrustNewtons;
		}

		public Vec3 bodyTorqueResidualNewtonMeters() {
			return solutionSample == null
					? targetBodyTorqueNewtonMeters.multiply(-1.0)
					: solutionSample.totalBodyTorqueNewtonMeters().subtract(targetBodyTorqueNewtonMeters);
		}

		public double maxAllocatedRotorThrustNewtons() {
			double max = 0.0;
			for (double thrust : allocatedRotorThrustsNewtons) {
				max = Math.max(max, thrust);
			}
			return max;
		}

		public double minAllocatedRotorThrustNewtons() {
			if (allocatedRotorThrustsNewtons.length == 0) {
				return 0.0;
			}
			double min = Double.POSITIVE_INFINITY;
			for (double thrust : allocatedRotorThrustsNewtons) {
				min = Math.min(min, thrust);
			}
			return Double.isFinite(min) ? min : 0.0;
		}
	}

	public static RotorTargetThrustSolution solveStaticAnchoredRpmForTargetThrust(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double targetThrustNewtons,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter
	) {
		return solveStaticAnchoredRpmForTargetThrust(
				presetName,
				caseName,
				rotor,
				signedAxialAdvanceSpeedMetersPerSecond,
				targetThrustNewtons,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static RotorTargetThrustSolution solveStaticAnchoredRpmForTargetThrust(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double targetThrustNewtons,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		if (!Double.isFinite(signedAxialAdvanceSpeedMetersPerSecond)) {
			throw new IllegalArgumentException("signedAxialAdvanceSpeedMetersPerSecond must be finite.");
		}
		return solveStaticAnchoredRpmForTargetThrustFromRelativeAirVelocity(
				presetName,
				caseName,
				rotor,
				rotorAxisBody(rotor).multiply(signedAxialAdvanceSpeedMetersPerSecond),
				Vec3.ZERO,
				targetThrustNewtons,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
	}

	public static RotorTargetThrustSolution solveStaticAnchoredRpmForTargetThrustFromRelativeAirVelocity(
			String presetName,
			String caseName,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double targetThrustNewtons,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter
	) {
		return solveStaticAnchoredRpmForTargetThrustFromRelativeAirVelocity(
				presetName,
				caseName,
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				Vec3.ZERO,
				targetThrustNewtons,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static RotorTargetThrustSolution solveStaticAnchoredRpmForTargetThrustFromRelativeAirVelocity(
			String presetName,
			String caseName,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 momentReferenceBodyMeters,
			double targetThrustNewtons,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		Vec3 relativeAirVelocity = finiteRelativeAirVelocity(relativeAirVelocityBodyMetersPerSecond);
		if (!Double.isFinite(targetThrustNewtons) || targetThrustNewtons < 0.0) {
			throw new IllegalArgumentException("targetThrustNewtons must be finite and nonnegative.");
		}
		if (!Double.isFinite(lowerOmegaRadiansPerSecond) || lowerOmegaRadiansPerSecond <= 0.0) {
			throw new IllegalArgumentException("lowerOmegaRadiansPerSecond must be finite and positive.");
		}
		if (!Double.isFinite(upperOmegaRadiansPerSecond)
				|| upperOmegaRadiansPerSecond <= lowerOmegaRadiansPerSecond) {
			throw new IllegalArgumentException(
					"upperOmegaRadiansPerSecond must be finite and greater than lowerOmegaRadiansPerSecond.");
		}
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		double signedAxialAdvanceSpeedMetersPerSecond = relativeAirVelocity.dot(rotorAxisBody(rotor));
		Vec3 momentReference = finiteVecOrZero(momentReferenceBodyMeters);

		RotorForceSample lower = solveTargetSampleFromRelativeAirVelocity(
				presetName,
				caseName,
				rotor,
				relativeAirVelocity,
				momentReference,
				lowerOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
		RotorForceSample upper = solveTargetSampleFromRelativeAirVelocity(
				presetName,
				caseName,
				rotor,
				relativeAirVelocity,
				momentReference,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
		double tolerance = targetThrustTolerance(targetThrustNewtons);
		if (!lower.blocked() && Math.abs(lower.thrustNewtons() - targetThrustNewtons) <= tolerance) {
			return new RotorTargetThrustSolution(
					RotorTargetThrustSolveStatus.SOLVED,
					targetThrustNewtons,
					signedAxialAdvanceSpeedMetersPerSecond,
					lowerOmegaRadiansPerSecond,
					upperOmegaRadiansPerSecond,
					0,
					lower,
					upper,
					lower
			);
		}
		if (!lower.blocked() && lower.thrustNewtons() > targetThrustNewtons) {
			return new RotorTargetThrustSolution(
					RotorTargetThrustSolveStatus.LOWER_BOUND_EXCEEDS_TARGET,
					targetThrustNewtons,
					signedAxialAdvanceSpeedMetersPerSecond,
					lowerOmegaRadiansPerSecond,
					upperOmegaRadiansPerSecond,
					0,
					lower,
					upper,
					lower
			);
		}
		if (upper.blocked()) {
			return new RotorTargetThrustSolution(
					RotorTargetThrustSolveStatus.UPPER_BOUND_BLOCKED,
					targetThrustNewtons,
					signedAxialAdvanceSpeedMetersPerSecond,
					lowerOmegaRadiansPerSecond,
					upperOmegaRadiansPerSecond,
					0,
					lower,
					upper,
					upper
			);
		}
		if (Math.abs(upper.thrustNewtons() - targetThrustNewtons) <= tolerance) {
			return new RotorTargetThrustSolution(
					RotorTargetThrustSolveStatus.SOLVED,
					targetThrustNewtons,
					signedAxialAdvanceSpeedMetersPerSecond,
					lowerOmegaRadiansPerSecond,
					upperOmegaRadiansPerSecond,
					0,
					lower,
					upper,
					upper
			);
		}
		if (upper.thrustNewtons() < targetThrustNewtons) {
			return new RotorTargetThrustSolution(
					RotorTargetThrustSolveStatus.UPPER_BOUND_BELOW_TARGET,
					targetThrustNewtons,
					signedAxialAdvanceSpeedMetersPerSecond,
					lowerOmegaRadiansPerSecond,
					upperOmegaRadiansPerSecond,
					0,
					lower,
					upper,
					upper
			);
		}

		double lowOmega = lowerOmegaRadiansPerSecond;
		double highOmega = upperOmegaRadiansPerSecond;
		RotorForceSample best = upper;
		int iterations = 0;
		for (int i = 1; i <= TARGET_THRUST_SOLVE_MAX_ITERATIONS; i++) {
			iterations = i;
			double midOmega = 0.5 * (lowOmega + highOmega);
			RotorForceSample mid = solveTargetSampleFromRelativeAirVelocity(
					presetName,
					caseName,
					rotor,
					relativeAirVelocity,
					momentReference,
					midOmega,
					airDensityKgPerCubicMeter,
					ambientTemperatureCelsius,
					ambientHumidity
			);
			if (mid.blocked()) {
				lowOmega = midOmega;
				lower = mid;
				continue;
			}
			best = mid;
			double residual = mid.thrustNewtons() - targetThrustNewtons;
			if (Math.abs(residual) <= tolerance) {
				return new RotorTargetThrustSolution(
						RotorTargetThrustSolveStatus.SOLVED,
						targetThrustNewtons,
						signedAxialAdvanceSpeedMetersPerSecond,
						lowOmega,
						highOmega,
						iterations,
						lower,
						upper,
						mid
				);
			}
			if (residual > 0.0) {
				highOmega = midOmega;
				upper = mid;
			} else {
				lowOmega = midOmega;
				lower = mid;
			}
		}
		return new RotorTargetThrustSolution(
				RotorTargetThrustSolveStatus.NO_CONVERGENCE,
				targetThrustNewtons,
				signedAxialAdvanceSpeedMetersPerSecond,
				lowOmega,
				highOmega,
				iterations,
				lower,
				upper,
				best
		);
	}

	public static ConfigurationTargetThrustSolution solveStaticAnchoredConfigurationRpmForTargetThrust(
			String presetName,
			String caseName,
			DroneConfig config,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double targetThrustNewtons,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter
	) {
		return solveStaticAnchoredConfigurationRpmForTargetThrust(
				presetName,
				caseName,
				config,
				signedAxialAdvanceSpeedMetersPerSecond,
				targetThrustNewtons,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static ConfigurationTargetThrustSolution solveStaticAnchoredConfigurationRpmForTargetThrust(
			String presetName,
			String caseName,
			DroneConfig config,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double targetThrustNewtons,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		if (!Double.isFinite(signedAxialAdvanceSpeedMetersPerSecond)) {
			throw new IllegalArgumentException("signedAxialAdvanceSpeedMetersPerSecond must be finite.");
		}
		return solveStaticAnchoredConfigurationRpmForTargetThrustFromRelativeAirVelocities(
				presetName,
				caseName,
				config,
				uniformRotorRelativeAirVelocities(config.rotors(), signedAxialAdvanceSpeedMetersPerSecond),
				targetThrustNewtons,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
	}

	public static ConfigurationTargetThrustSolution solveStaticAnchoredConfigurationRpmForTargetThrustFromBodyKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3 bodyRelativeAirVelocityBodyMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			double targetThrustNewtons,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter
	) {
		return solveStaticAnchoredConfigurationRpmForTargetThrustFromBodyKinematics(
				presetName,
				caseName,
				config,
				bodyRelativeAirVelocityBodyMetersPerSecond,
				angularVelocityBodyRadiansPerSecond,
				targetThrustNewtons,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static ConfigurationTargetThrustSolution solveStaticAnchoredConfigurationRpmForTargetThrustFromBodyKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3 bodyRelativeAirVelocityBodyMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			double targetThrustNewtons,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		return solveStaticAnchoredConfigurationRpmForTargetThrustFromRelativeAirVelocities(
				presetName,
				caseName,
				config,
				rotorRelativeAirVelocitiesFromBodyKinematics(
						config,
						bodyRelativeAirVelocityBodyMetersPerSecond,
						angularVelocityBodyRadiansPerSecond
				),
				targetThrustNewtons,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
	}

	public static ConfigurationTargetThrustSolution solveStaticAnchoredConfigurationRpmForTargetThrustFromEnvironmentKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Quaternion bodyToWorldOrientation,
			Vec3 vehicleVelocityWorldMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			double targetThrustNewtons,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			DroneEnvironment environment
	) {
		if (environment == null) {
			environment = DroneEnvironment.calm();
		}
		return solveStaticAnchoredConfigurationRpmForTargetThrustFromWorldKinematics(
				presetName,
				caseName,
				config,
				bodyToWorldOrientation,
				vehicleVelocityWorldMetersPerSecond,
				angularVelocityBodyRadiansPerSecond,
				environment.windVelocityWorldMetersPerSecond(),
				environment.rotorWindVelocityWorldMetersPerSecond(),
				targetThrustNewtons,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * environment.effectiveAirDensityRatio(),
				environment.effectiveAmbientTemperatureCelsius(),
				environment.ambientHumidity()
		);
	}

	public static ConfigurationTargetThrustSolution solveStaticAnchoredConfigurationRpmForTargetThrustFromWorldKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Quaternion bodyToWorldOrientation,
			Vec3 vehicleVelocityWorldMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 windVelocityWorldMetersPerSecond,
			Vec3[] rotorWindVelocityWorldMetersPerSecond,
			double targetThrustNewtons,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter
	) {
		return solveStaticAnchoredConfigurationRpmForTargetThrustFromWorldKinematics(
				presetName,
				caseName,
				config,
				bodyToWorldOrientation,
				vehicleVelocityWorldMetersPerSecond,
				angularVelocityBodyRadiansPerSecond,
				windVelocityWorldMetersPerSecond,
				rotorWindVelocityWorldMetersPerSecond,
				targetThrustNewtons,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static ConfigurationTargetThrustSolution solveStaticAnchoredConfigurationRpmForTargetThrustFromWorldKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Quaternion bodyToWorldOrientation,
			Vec3 vehicleVelocityWorldMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 windVelocityWorldMetersPerSecond,
			Vec3[] rotorWindVelocityWorldMetersPerSecond,
			double targetThrustNewtons,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		return solveStaticAnchoredConfigurationRpmForTargetThrustFromRelativeAirVelocities(
				presetName,
				caseName,
				config,
				rotorRelativeAirVelocitiesFromWorldKinematics(
						config,
						bodyToWorldOrientation,
						vehicleVelocityWorldMetersPerSecond,
						angularVelocityBodyRadiansPerSecond,
						windVelocityWorldMetersPerSecond,
						rotorWindVelocityWorldMetersPerSecond
				),
				targetThrustNewtons,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
	}

	public static ConfigurationTargetThrustSolution solveStaticAnchoredConfigurationRpmForTargetThrustFromRelativeAirVelocities(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3[] relativeAirVelocitiesBodyMetersPerSecond,
			double targetThrustNewtons,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter
	) {
		return solveStaticAnchoredConfigurationRpmForTargetThrustFromRelativeAirVelocities(
				presetName,
				caseName,
				config,
				relativeAirVelocitiesBodyMetersPerSecond,
				targetThrustNewtons,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static ConfigurationTargetThrustSolution solveStaticAnchoredConfigurationRpmForTargetThrustFromRelativeAirVelocities(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3[] relativeAirVelocitiesBodyMetersPerSecond,
			double targetThrustNewtons,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		Vec3[] relativeAirVelocities = validateRotorRelativeAirVelocities(
				config,
				relativeAirVelocitiesBodyMetersPerSecond
		);
		double signedAxialAdvanceSpeedMetersPerSecond =
				meanSignedAxialAdvanceSpeed(config.rotors(), relativeAirVelocities);
		if (!Double.isFinite(targetThrustNewtons) || targetThrustNewtons < 0.0) {
			throw new IllegalArgumentException("targetThrustNewtons must be finite and nonnegative.");
		}
		if (!Double.isFinite(lowerOmegaRadiansPerSecond) || lowerOmegaRadiansPerSecond <= 0.0) {
			throw new IllegalArgumentException("lowerOmegaRadiansPerSecond must be finite and positive.");
		}
		if (!Double.isFinite(upperOmegaRadiansPerSecond)
				|| upperOmegaRadiansPerSecond <= lowerOmegaRadiansPerSecond) {
			throw new IllegalArgumentException(
					"upperOmegaRadiansPerSecond must be finite and greater than lowerOmegaRadiansPerSecond.");
		}
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}

		RotorForceAggregateSample lower = solveTargetConfigurationSampleFromRelativeAirVelocities(
				presetName,
				caseName,
				config,
				relativeAirVelocities,
				lowerOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
		RotorForceAggregateSample upper = solveTargetConfigurationSampleFromRelativeAirVelocities(
				presetName,
				caseName,
				config,
				relativeAirVelocities,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
		double tolerance = targetThrustTolerance(targetThrustNewtons);
		if (lower.blockedRotorCount() == 0
				&& Math.abs(lower.totalThrustNewtons() - targetThrustNewtons) <= tolerance) {
			return new ConfigurationTargetThrustSolution(
					RotorTargetThrustSolveStatus.SOLVED,
					targetThrustNewtons,
					signedAxialAdvanceSpeedMetersPerSecond,
					lowerOmegaRadiansPerSecond,
					upperOmegaRadiansPerSecond,
					0,
					lower,
					upper,
					lower
			);
		}
		if (lower.blockedRotorCount() == 0 && lower.totalThrustNewtons() > targetThrustNewtons) {
			return new ConfigurationTargetThrustSolution(
					RotorTargetThrustSolveStatus.LOWER_BOUND_EXCEEDS_TARGET,
					targetThrustNewtons,
					signedAxialAdvanceSpeedMetersPerSecond,
					lowerOmegaRadiansPerSecond,
					upperOmegaRadiansPerSecond,
					0,
					lower,
					upper,
					lower
			);
		}
		if (upper.blockedRotorCount() > 0) {
			return new ConfigurationTargetThrustSolution(
					RotorTargetThrustSolveStatus.UPPER_BOUND_BLOCKED,
					targetThrustNewtons,
					signedAxialAdvanceSpeedMetersPerSecond,
					lowerOmegaRadiansPerSecond,
					upperOmegaRadiansPerSecond,
					0,
					lower,
					upper,
					upper
			);
		}
		if (Math.abs(upper.totalThrustNewtons() - targetThrustNewtons) <= tolerance) {
			return new ConfigurationTargetThrustSolution(
					RotorTargetThrustSolveStatus.SOLVED,
					targetThrustNewtons,
					signedAxialAdvanceSpeedMetersPerSecond,
					lowerOmegaRadiansPerSecond,
					upperOmegaRadiansPerSecond,
					0,
					lower,
					upper,
					upper
			);
		}
		if (upper.totalThrustNewtons() < targetThrustNewtons) {
			return new ConfigurationTargetThrustSolution(
					RotorTargetThrustSolveStatus.UPPER_BOUND_BELOW_TARGET,
					targetThrustNewtons,
					signedAxialAdvanceSpeedMetersPerSecond,
					lowerOmegaRadiansPerSecond,
					upperOmegaRadiansPerSecond,
					0,
					lower,
					upper,
					upper
			);
		}

		double lowOmega = lowerOmegaRadiansPerSecond;
		double highOmega = upperOmegaRadiansPerSecond;
		RotorForceAggregateSample best = upper;
		int iterations = 0;
		for (int i = 1; i <= TARGET_THRUST_SOLVE_MAX_ITERATIONS; i++) {
			iterations = i;
			double midOmega = 0.5 * (lowOmega + highOmega);
			RotorForceAggregateSample mid = solveTargetConfigurationSampleFromRelativeAirVelocities(
					presetName,
					caseName,
					config,
					relativeAirVelocities,
					midOmega,
					airDensityKgPerCubicMeter,
					ambientTemperatureCelsius,
					ambientHumidity
			);
			if (mid.blockedRotorCount() > 0) {
				lowOmega = midOmega;
				lower = mid;
				continue;
			}
			best = mid;
			double residual = mid.totalThrustNewtons() - targetThrustNewtons;
			if (Math.abs(residual) <= tolerance) {
				return new ConfigurationTargetThrustSolution(
						RotorTargetThrustSolveStatus.SOLVED,
						targetThrustNewtons,
						signedAxialAdvanceSpeedMetersPerSecond,
						lowOmega,
						highOmega,
						iterations,
						lower,
						upper,
						mid
				);
			}
			if (residual > 0.0) {
				highOmega = midOmega;
				upper = mid;
			} else {
				lowOmega = midOmega;
				lower = mid;
			}
		}
		return new ConfigurationTargetThrustSolution(
				RotorTargetThrustSolveStatus.NO_CONVERGENCE,
				targetThrustNewtons,
				signedAxialAdvanceSpeedMetersPerSecond,
				lowOmega,
				highOmega,
				iterations,
				lower,
				upper,
				best
		);
	}

	public static ConfigurationTrimSolution solveStaticAnchoredConfigurationTrim(
			String presetName,
			String caseName,
			DroneConfig config,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double targetThrustNewtons,
			Vec3 targetBodyTorqueNewtonMeters,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter
	) {
		return solveStaticAnchoredConfigurationTrim(
				presetName,
				caseName,
				config,
				signedAxialAdvanceSpeedMetersPerSecond,
				targetThrustNewtons,
				targetBodyTorqueNewtonMeters,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static ConfigurationTrimSolution solveStaticAnchoredConfigurationTrim(
			String presetName,
			String caseName,
			DroneConfig config,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double targetThrustNewtons,
			Vec3 targetBodyTorqueNewtonMeters,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		if (!Double.isFinite(signedAxialAdvanceSpeedMetersPerSecond)) {
			throw new IllegalArgumentException("signedAxialAdvanceSpeedMetersPerSecond must be finite.");
		}
		return solveStaticAnchoredConfigurationTrimFromRelativeAirVelocities(
				presetName,
				caseName,
				config,
				uniformRotorRelativeAirVelocities(config.rotors(), signedAxialAdvanceSpeedMetersPerSecond),
				targetThrustNewtons,
				targetBodyTorqueNewtonMeters,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
	}

	public static ConfigurationTrimSolution solveStaticAnchoredConfigurationTrimFromBodyKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3 bodyRelativeAirVelocityBodyMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			double targetThrustNewtons,
			Vec3 targetBodyTorqueNewtonMeters,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter
	) {
		return solveStaticAnchoredConfigurationTrimFromBodyKinematics(
				presetName,
				caseName,
				config,
				bodyRelativeAirVelocityBodyMetersPerSecond,
				angularVelocityBodyRadiansPerSecond,
				targetThrustNewtons,
				targetBodyTorqueNewtonMeters,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static ConfigurationTrimSolution solveStaticAnchoredConfigurationTrimFromBodyKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3 bodyRelativeAirVelocityBodyMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			double targetThrustNewtons,
			Vec3 targetBodyTorqueNewtonMeters,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		return solveStaticAnchoredConfigurationTrimFromRelativeAirVelocities(
				presetName,
				caseName,
				config,
				rotorRelativeAirVelocitiesFromBodyKinematics(
						config,
						bodyRelativeAirVelocityBodyMetersPerSecond,
						angularVelocityBodyRadiansPerSecond
				),
				targetThrustNewtons,
				targetBodyTorqueNewtonMeters,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
	}

	public static ConfigurationTrimSolution solveStaticAnchoredConfigurationTrimFromEnvironmentKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Quaternion bodyToWorldOrientation,
			Vec3 vehicleVelocityWorldMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			double targetThrustNewtons,
			Vec3 targetBodyTorqueNewtonMeters,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			DroneEnvironment environment
	) {
		if (environment == null) {
			environment = DroneEnvironment.calm();
		}
		return solveStaticAnchoredConfigurationTrimFromWorldKinematics(
				presetName,
				caseName,
				config,
				bodyToWorldOrientation,
				vehicleVelocityWorldMetersPerSecond,
				angularVelocityBodyRadiansPerSecond,
				environment.windVelocityWorldMetersPerSecond(),
				environment.rotorWindVelocityWorldMetersPerSecond(),
				targetThrustNewtons,
				targetBodyTorqueNewtonMeters,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * environment.effectiveAirDensityRatio(),
				environment.effectiveAmbientTemperatureCelsius(),
				environment.ambientHumidity()
		);
	}

	public static ConfigurationTrimSolution solveStaticAnchoredConfigurationTrimFromWorldKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Quaternion bodyToWorldOrientation,
			Vec3 vehicleVelocityWorldMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 windVelocityWorldMetersPerSecond,
			Vec3[] rotorWindVelocityWorldMetersPerSecond,
			double targetThrustNewtons,
			Vec3 targetBodyTorqueNewtonMeters,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter
	) {
		return solveStaticAnchoredConfigurationTrimFromWorldKinematics(
				presetName,
				caseName,
				config,
				bodyToWorldOrientation,
				vehicleVelocityWorldMetersPerSecond,
				angularVelocityBodyRadiansPerSecond,
				windVelocityWorldMetersPerSecond,
				rotorWindVelocityWorldMetersPerSecond,
				targetThrustNewtons,
				targetBodyTorqueNewtonMeters,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static ConfigurationTrimSolution solveStaticAnchoredConfigurationTrimFromWorldKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Quaternion bodyToWorldOrientation,
			Vec3 vehicleVelocityWorldMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 windVelocityWorldMetersPerSecond,
			Vec3[] rotorWindVelocityWorldMetersPerSecond,
			double targetThrustNewtons,
			Vec3 targetBodyTorqueNewtonMeters,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		return solveStaticAnchoredConfigurationTrimFromRelativeAirVelocities(
				presetName,
				caseName,
				config,
				rotorRelativeAirVelocitiesFromWorldKinematics(
						config,
						bodyToWorldOrientation,
						vehicleVelocityWorldMetersPerSecond,
						angularVelocityBodyRadiansPerSecond,
						windVelocityWorldMetersPerSecond,
						rotorWindVelocityWorldMetersPerSecond
				),
				targetThrustNewtons,
				targetBodyTorqueNewtonMeters,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
	}

	public static ConfigurationTrimSolution solveStaticAnchoredConfigurationTrimFromRelativeAirVelocities(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3[] relativeAirVelocitiesBodyMetersPerSecond,
			double targetThrustNewtons,
			Vec3 targetBodyTorqueNewtonMeters,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter
	) {
		return solveStaticAnchoredConfigurationTrimFromRelativeAirVelocities(
				presetName,
				caseName,
				config,
				relativeAirVelocitiesBodyMetersPerSecond,
				targetThrustNewtons,
				targetBodyTorqueNewtonMeters,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static ConfigurationTrimSolution solveStaticAnchoredConfigurationTrimFromRelativeAirVelocities(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3[] relativeAirVelocitiesBodyMetersPerSecond,
			double targetThrustNewtons,
			Vec3 targetBodyTorqueNewtonMeters,
			double lowerOmegaRadiansPerSecond,
			double upperOmegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		Vec3[] relativeAirVelocities = validateRotorRelativeAirVelocities(
				config,
				relativeAirVelocitiesBodyMetersPerSecond
		);
		double signedAxialAdvanceSpeedMetersPerSecond =
				meanSignedAxialAdvanceSpeed(config.rotors(), relativeAirVelocities);
		if (!Double.isFinite(targetThrustNewtons) || targetThrustNewtons < 0.0) {
			throw new IllegalArgumentException("targetThrustNewtons must be finite and nonnegative.");
		}
		if (!Double.isFinite(lowerOmegaRadiansPerSecond) || lowerOmegaRadiansPerSecond <= 0.0) {
			throw new IllegalArgumentException("lowerOmegaRadiansPerSecond must be finite and positive.");
		}
		if (!Double.isFinite(upperOmegaRadiansPerSecond)
				|| upperOmegaRadiansPerSecond <= lowerOmegaRadiansPerSecond) {
			throw new IllegalArgumentException(
					"upperOmegaRadiansPerSecond must be finite and greater than lowerOmegaRadiansPerSecond.");
		}
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		Vec3 targetTorque = finiteVecOrZero(targetBodyTorqueNewtonMeters);
		double[] allocatedThrusts = allocateStaticTrimThrusts(
				config.rotors(),
				config.centerOfMassOffsetBodyMeters(),
				targetThrustNewtons,
				targetTorque
		);
		if (allocatedThrusts == null || allocatedThrusts.length < config.rotors().size()) {
			return new ConfigurationTrimSolution(
					ConfigurationTrimSolveStatus.ALLOCATION_UNAVAILABLE,
					targetThrustNewtons,
					targetTorque,
					signedAxialAdvanceSpeedMetersPerSecond,
					lowerOmegaRadiansPerSecond,
					upperOmegaRadiansPerSecond,
					allocatedThrusts,
					List.of(),
					null
			);
		}
		for (double thrust : allocatedThrusts) {
			if (thrust < -1.0e-8) {
				return new ConfigurationTrimSolution(
						ConfigurationTrimSolveStatus.NEGATIVE_ALLOCATED_THRUST,
						targetThrustNewtons,
						targetTorque,
						signedAxialAdvanceSpeedMetersPerSecond,
						lowerOmegaRadiansPerSecond,
						upperOmegaRadiansPerSecond,
						allocatedThrusts,
						List.of(),
						null
				);
			}
		}
		List<RotorTargetThrustSolution> rotorSolutions = new ArrayList<>();
		List<RotorForceSample> rotorSamples = new ArrayList<>();
		boolean solved = true;
		String normalizedCase = caseName == null || caseName.isBlank() ? "configuration_trim" : caseName;
		for (int i = 0; i < config.rotors().size(); i++) {
			RotorTargetThrustSolution rotorSolution = solveStaticAnchoredRpmForTargetThrustFromRelativeAirVelocity(
					presetName,
					normalizedCase,
					config.rotors().get(i),
					relativeAirVelocities[i],
					config.centerOfMassOffsetBodyMeters(),
					Math.max(0.0, allocatedThrusts[i]),
					lowerOmegaRadiansPerSecond,
					upperOmegaRadiansPerSecond,
					airDensityKgPerCubicMeter,
					ambientTemperatureCelsius,
					ambientHumidity
			);
			rotorSolutions.add(rotorSolution);
			if (!rotorSolution.solved()) {
				solved = false;
			}
			if (rotorSolution.solutionSample() != null) {
				rotorSamples.add(rotorSolution.solutionSample());
			}
		}
		RotorForceAggregateSample aggregate = rotorSamples.size() == config.rotors().size()
				? aggregate(rotorSamples)
				: null;
		return new ConfigurationTrimSolution(
				solved ? ConfigurationTrimSolveStatus.SOLVED : ConfigurationTrimSolveStatus.ROTOR_SOLVE_BLOCKED,
				targetThrustNewtons,
				targetTorque,
				signedAxialAdvanceSpeedMetersPerSecond,
				lowerOmegaRadiansPerSecond,
				upperOmegaRadiansPerSecond,
				allocatedThrusts,
				rotorSolutions,
				aggregate
		);
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
		return queryFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				axialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				envelopePolicy,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static RotorForceQuery queryFromAxialAdvanceSpeed(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double axialAdvanceSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
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
				envelopePolicy,
				ambientTemperatureCelsius,
				ambientHumidity
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
		return sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				signedAxialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				momentReferenceBodyMeters,
				envelopePolicy,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
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
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
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
				envelopePolicy,
				ambientTemperatureCelsius,
				ambientHumidity
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
		return sampleStaticAnchoredFromRelativeAirVelocity(
				presetName,
				caseName,
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				momentReferenceBodyMeters,
				envelopePolicy,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
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
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
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
				envelopePolicy,
				ambientTemperatureCelsius,
				ambientHumidity
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
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
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
					envelopePolicy,
					ambientTemperatureCelsius,
					ambientHumidity
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
					momentReferenceBodyMeters,
					ambientTemperatureCelsius,
					ambientHumidity
			);
		}
		return sampleStaticAnchoredReverseAxialClamped(
				presetName,
				caseName == null || caseName.isBlank() ? "reverse_axial_static_anchor" : caseName,
				rotor,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				relativeAirVelocity,
				momentReferenceBodyMeters,
				ambientTemperatureCelsius,
				ambientHumidity
		);
	}

	private static RotorForceSample sampleStaticAnchoredReverseAxialClamped(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 momentReferenceBodyMeters,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		RotorForceQuery query = queryFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				0.0,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.CLAMP_TO_ENVELOPE,
				ambientTemperatureCelsius,
				ambientHumidity
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
			Vec3 momentReferenceBodyMeters,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		RotorForceQuery query = queryFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				0.0,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
				ambientTemperatureCelsius,
				ambientHumidity
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
		double inflowAngle = inflowAngleRadians(axialVelocity, transverseAirSpeed);
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
				envelopePolicy,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static RotorForceSample sampleFromAxialAdvanceSpeed(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double axialAdvanceSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		return sampleFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				axialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				Vec3.ZERO,
				envelopePolicy,
				ambientTemperatureCelsius,
				ambientHumidity
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
		return sampleFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				axialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				momentReferenceBodyMeters,
				envelopePolicy,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
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
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		return sample(queryFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				axialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				envelopePolicy,
				ambientTemperatureCelsius,
				ambientHumidity
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
				envelopePolicy,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static RotorForceSample sampleStaticAnchoredFromAxialAdvanceSpeed(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double axialAdvanceSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		return sampleStaticAnchoredFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				axialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				Vec3.ZERO,
				envelopePolicy,
				ambientTemperatureCelsius,
				ambientHumidity
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
		return sampleStaticAnchoredFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				axialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				momentReferenceBodyMeters,
				envelopePolicy,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
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
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		return sampleStaticAnchored(queryFromAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				axialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				envelopePolicy,
				ambientTemperatureCelsius,
				ambientHumidity
		), momentReferenceBodyMeters);
	}

	public static RotorForceAggregateSample aggregate(List<RotorForceSample> samples) {
		if (samples == null || samples.isEmpty()) {
			return new RotorForceAggregateSample(List.of(), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
					0.0, 0.0, 0.0,
					0.0, 0.0, 0.0,
					0.0, 0.0, 0.0,
					0.0, 0.0, 0.0, 0.0, 0.0,
					Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
					0.0, 0.0, 0.0,
					0.0, 0.0, 0.0,
					0.0, 0.0, 0.0,
					0.0, 0.0, 0.0, 0.0, 0.0,
					0, 0, 0, 0);
		}
		List<RotorForceSample> acceptedSamples = new ArrayList<>();
		Vec3 totalForce = Vec3.ZERO;
		Vec3 totalReactionTorque = Vec3.ZERO;
		Vec3 totalThrustMoment = Vec3.ZERO;
		Vec3 totalBodyTorque = Vec3.ZERO;
		Vec3 runtimeForceReplacementForce = Vec3.ZERO;
		Vec3 runtimeForceReplacementReactionTorque = Vec3.ZERO;
		Vec3 runtimeForceReplacementThrustMoment = Vec3.ZERO;
		Vec3 runtimeForceReplacementBodyTorque = Vec3.ZERO;
		double totalThrust = 0.0;
		double totalPower = 0.0;
		double totalShaftTorque = 0.0;
		double totalWakeAngularMomentumTorque = 0.0;
		double totalDiskMassFlow = 0.0;
		double totalUsefulAxialPower = 0.0;
		double totalIdealInducedPower = 0.0;
		double totalIdealMomentumPower = 0.0;
		double totalWakeSwirlPower = 0.0;
		double totalWakePower = 0.0;
		double runtimeForceReplacementThrust = 0.0;
		double runtimeForceReplacementPower = 0.0;
		double runtimeForceReplacementShaftTorque = 0.0;
		double runtimeForceReplacementWakeAngularMomentumTorque = 0.0;
		double runtimeForceReplacementDiskMassFlow = 0.0;
		double runtimeForceReplacementUsefulAxialPower = 0.0;
		double runtimeForceReplacementIdealInducedPower = 0.0;
		double runtimeForceReplacementIdealMomentumPower = 0.0;
		double runtimeForceReplacementWakeSwirlPower = 0.0;
		double runtimeForceReplacementWakePower = 0.0;
		int accepted = 0;
		int runtimeForceReplacementAccepted = 0;
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
			totalWakeAngularMomentumTorque += sample.dimensionalSample().wakeAngularMomentumTorqueNewtonMeters();
			totalDiskMassFlow += sample.dimensionalSample().diskMassFlowKilogramsPerSecond();
			totalUsefulAxialPower += sample.dimensionalSample().usefulAxialThrustPowerWatts();
			totalIdealInducedPower += sample.dimensionalSample().idealInducedPowerWatts();
			totalIdealMomentumPower += sample.dimensionalSample().idealMomentumPowerWatts();
			totalWakeSwirlPower += sample.dimensionalSample().wakeSwirlKineticPowerWatts();
			totalWakePower += sample.dimensionalSample().totalWakeKineticPowerWatts();
			if (sample.blocked()) {
				blocked++;
			} else {
				accepted++;
			}
			if (sample.runtimeForceReplacementAccepted()) {
				runtimeForceReplacementAccepted++;
				runtimeForceReplacementForce =
						runtimeForceReplacementForce.add(sample.thrustForceBodyNewtons());
				runtimeForceReplacementReactionTorque =
						runtimeForceReplacementReactionTorque.add(sample.reactionTorqueBodyNewtonMeters());
				runtimeForceReplacementThrustMoment =
						runtimeForceReplacementThrustMoment.add(sample.thrustMomentBodyNewtonMeters());
				runtimeForceReplacementBodyTorque =
						runtimeForceReplacementBodyTorque.add(sample.totalTorqueBodyNewtonMeters());
				runtimeForceReplacementThrust += sample.thrustNewtons();
				runtimeForceReplacementPower += sample.shaftPowerWatts();
				runtimeForceReplacementShaftTorque += sample.shaftTorqueNewtonMeters();
				runtimeForceReplacementWakeAngularMomentumTorque +=
						sample.dimensionalSample().wakeAngularMomentumTorqueNewtonMeters();
				runtimeForceReplacementDiskMassFlow += sample.dimensionalSample().diskMassFlowKilogramsPerSecond();
				runtimeForceReplacementUsefulAxialPower +=
						sample.dimensionalSample().usefulAxialThrustPowerWatts();
				runtimeForceReplacementIdealInducedPower +=
						sample.dimensionalSample().idealInducedPowerWatts();
				runtimeForceReplacementIdealMomentumPower += sample.dimensionalSample().idealMomentumPowerWatts();
				runtimeForceReplacementWakeSwirlPower += sample.dimensionalSample().wakeSwirlKineticPowerWatts();
				runtimeForceReplacementWakePower += sample.dimensionalSample().totalWakeKineticPowerWatts();
			}
			if (sample.clamped()) {
				clamped++;
			}
		}
		double totalWakeAngularMomentumTorqueResidual =
				totalWakeAngularMomentumTorque - totalShaftTorque;
		double totalWakeResidual = totalPower - totalWakePower;
		double runtimeForceReplacementWakeAngularMomentumTorqueResidual =
				runtimeForceReplacementWakeAngularMomentumTorque - runtimeForceReplacementShaftTorque;
		double runtimeForceReplacementWakeResidual =
				runtimeForceReplacementPower - runtimeForceReplacementWakePower;
		return new RotorForceAggregateSample(
				acceptedSamples,
				totalForce,
				totalReactionTorque,
				totalThrustMoment,
				totalBodyTorque,
				totalThrust,
				totalPower,
				totalShaftTorque,
				totalWakeAngularMomentumTorque,
				totalWakeAngularMomentumTorqueResidual,
				ratio(totalWakeAngularMomentumTorqueResidual, totalShaftTorque),
				totalDiskMassFlow,
				totalUsefulAxialPower,
				totalIdealInducedPower,
				totalIdealMomentumPower,
				totalWakeSwirlPower,
				totalWakePower,
				totalWakeResidual,
				ratio(totalWakePower, totalPower),
				runtimeForceReplacementForce,
				runtimeForceReplacementReactionTorque,
				runtimeForceReplacementThrustMoment,
				runtimeForceReplacementBodyTorque,
				runtimeForceReplacementThrust,
				runtimeForceReplacementPower,
				runtimeForceReplacementShaftTorque,
				runtimeForceReplacementWakeAngularMomentumTorque,
				runtimeForceReplacementWakeAngularMomentumTorqueResidual,
				ratio(runtimeForceReplacementWakeAngularMomentumTorqueResidual,
						runtimeForceReplacementShaftTorque),
				runtimeForceReplacementDiskMassFlow,
				runtimeForceReplacementUsefulAxialPower,
				runtimeForceReplacementIdealInducedPower,
				runtimeForceReplacementIdealMomentumPower,
				runtimeForceReplacementWakeSwirlPower,
				runtimeForceReplacementWakePower,
				runtimeForceReplacementWakeResidual,
				ratio(runtimeForceReplacementWakePower, runtimeForceReplacementPower),
				accepted,
				runtimeForceReplacementAccepted,
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
		return sampleStaticAnchoredConfigurationFromRelativeAirVelocities(
				presetName,
				caseName,
				config,
				relativeAirVelocitiesBody,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				envelopePolicy,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static RotorForceAggregateSample sampleStaticAnchoredConfigurationFromRelativeAirVelocities(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3[] relativeAirVelocitiesBody,
			double[] omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
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
					envelopePolicy,
					ambientTemperatureCelsius,
					ambientHumidity
			));
		}
		return aggregate(samples);
	}

	public static RotorForceAggregateSample sampleStaticAnchoredConfigurationFromBodyKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3 bodyRelativeAirVelocityBodyMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			double[] omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		return sampleStaticAnchoredConfigurationFromBodyKinematics(
				presetName,
				caseName,
				config,
				bodyRelativeAirVelocityBodyMetersPerSecond,
				angularVelocityBodyRadiansPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				envelopePolicy,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static RotorForceAggregateSample sampleStaticAnchoredConfigurationFromBodyKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3 bodyRelativeAirVelocityBodyMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			double[] omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (omegaRadiansPerSecond == null || omegaRadiansPerSecond.length < config.rotors().size()) {
			throw new IllegalArgumentException("rotor speed array must cover every configured rotor.");
		}
		Vec3 bodyRelativeAirVelocity = finiteRelativeAirVelocity(bodyRelativeAirVelocityBodyMetersPerSecond);
		Vec3 angularVelocityBody = finiteVecOrZero(angularVelocityBodyRadiansPerSecond);
		Vec3 momentReference = config.centerOfMassOffsetBodyMeters();
		List<RotorForceSample> samples = new ArrayList<>();
		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			Vec3 rotorArmBody = rotor.positionBodyMeters().subtract(momentReference);
			Vec3 rotorRelativeAirVelocity = bodyRelativeAirVelocity.add(angularVelocityBody.cross(rotorArmBody));
			samples.add(sampleStaticAnchoredFromRelativeAirVelocity(
					presetName,
					caseName,
					rotor,
					rotorRelativeAirVelocity,
					omegaRadiansPerSecond[i],
					airDensityKgPerCubicMeter,
					momentReference,
					envelopePolicy,
					ambientTemperatureCelsius,
					ambientHumidity
			));
		}
		return aggregate(samples);
	}

	public static RotorForceAggregateSample sampleStaticAnchoredConfigurationFromEnvironmentKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Quaternion bodyToWorldOrientation,
			Vec3 vehicleVelocityWorldMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			double[] omegaRadiansPerSecond,
			DroneEnvironment environment,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		if (environment == null) {
			environment = DroneEnvironment.calm();
		}
		return sampleStaticAnchoredConfigurationFromWorldKinematics(
				presetName,
				caseName,
				config,
				bodyToWorldOrientation,
				vehicleVelocityWorldMetersPerSecond,
				angularVelocityBodyRadiansPerSecond,
				environment.windVelocityWorldMetersPerSecond(),
				environment.rotorWindVelocityWorldMetersPerSecond(),
				omegaRadiansPerSecond,
				SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * environment.effectiveAirDensityRatio(),
				envelopePolicy,
				environment.effectiveAmbientTemperatureCelsius(),
				environment.ambientHumidity()
		);
	}

	public static RotorForceAggregateSample sampleStaticAnchoredConfigurationFromWorldKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Quaternion bodyToWorldOrientation,
			Vec3 vehicleVelocityWorldMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 windVelocityWorldMetersPerSecond,
			Vec3[] rotorWindVelocityWorldMetersPerSecond,
			double[] omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy
	) {
		return sampleStaticAnchoredConfigurationFromWorldKinematics(
				presetName,
				caseName,
				config,
				bodyToWorldOrientation,
				vehicleVelocityWorldMetersPerSecond,
				angularVelocityBodyRadiansPerSecond,
				windVelocityWorldMetersPerSecond,
				rotorWindVelocityWorldMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				envelopePolicy,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static RotorForceAggregateSample sampleStaticAnchoredConfigurationFromWorldKinematics(
			String presetName,
			String caseName,
			DroneConfig config,
			Quaternion bodyToWorldOrientation,
			Vec3 vehicleVelocityWorldMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 windVelocityWorldMetersPerSecond,
			Vec3[] rotorWindVelocityWorldMetersPerSecond,
			double[] omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		return sampleStaticAnchoredConfigurationFromRelativeAirVelocities(
				presetName,
				caseName,
				config,
				rotorRelativeAirVelocitiesFromWorldKinematics(
						config,
						bodyToWorldOrientation,
						vehicleVelocityWorldMetersPerSecond,
						angularVelocityBodyRadiansPerSecond,
						windVelocityWorldMetersPerSecond,
						rotorWindVelocityWorldMetersPerSecond
				),
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				envelopePolicy,
				ambientTemperatureCelsius,
				ambientHumidity
		);
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
		return sampleStaticAnchoredConfigurationFromSignedAxialAdvanceSpeeds(
				presetName,
				caseName,
				config,
				signedAxialAdvanceSpeedsMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				envelopePolicy,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS,
				0.0
		);
	}

	public static RotorForceAggregateSample sampleStaticAnchoredConfigurationFromSignedAxialAdvanceSpeeds(
			String presetName,
			String caseName,
			DroneConfig config,
			double[] signedAxialAdvanceSpeedsMetersPerSecond,
			double[] omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy envelopePolicy,
			double ambientTemperatureCelsius,
			double ambientHumidity
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
					envelopePolicy,
					ambientTemperatureCelsius,
					ambientHumidity
			));
		}
		return aggregate(samples);
	}

	private static RotorForceSample solveTargetSampleFromRelativeAirVelocity(
			String presetName,
			String caseName,
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 momentReferenceBodyMeters,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		return sampleStaticAnchoredFromRelativeAirVelocity(
				presetName,
				caseName,
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				momentReferenceBodyMeters,
				PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
				ambientTemperatureCelsius,
				ambientHumidity
		);
	}

	private static RotorForceAggregateSample solveTargetConfigurationSampleFromRelativeAirVelocities(
			String presetName,
			String caseName,
			DroneConfig config,
			Vec3[] relativeAirVelocitiesBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		int rotorCount = config.rotors().size();
		return sampleStaticAnchoredConfigurationFromRelativeAirVelocities(
				presetName,
				caseName,
				config,
				relativeAirVelocitiesBodyMetersPerSecond,
				uniformArray(rotorCount, omegaRadiansPerSecond),
				airDensityKgPerCubicMeter,
				PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
				ambientTemperatureCelsius,
				ambientHumidity
		);
	}

	private static double[] uniformArray(int length, double value) {
		double[] values = new double[Math.max(0, length)];
		for (int i = 0; i < values.length; i++) {
			values[i] = value;
		}
		return values;
	}

	private static Vec3[] uniformRotorRelativeAirVelocities(
			List<RotorSpec> rotors,
			double signedAxialAdvanceSpeedMetersPerSecond
	) {
		if (rotors == null) {
			return new Vec3[0];
		}
		Vec3[] velocities = new Vec3[rotors.size()];
		for (int i = 0; i < rotors.size(); i++) {
			velocities[i] = rotorAxisBody(rotors.get(i)).multiply(signedAxialAdvanceSpeedMetersPerSecond);
		}
		return velocities;
	}

	private static Vec3[] rotorRelativeAirVelocitiesFromBodyKinematics(
			DroneConfig config,
			Vec3 bodyRelativeAirVelocityBodyMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond
	) {
		Vec3 bodyRelativeAirVelocity = finiteRelativeAirVelocity(bodyRelativeAirVelocityBodyMetersPerSecond);
		Vec3 angularVelocityBody = finiteVecOrZero(angularVelocityBodyRadiansPerSecond);
		Vec3 momentReference = config.centerOfMassOffsetBodyMeters();
		Vec3[] velocities = new Vec3[config.rotors().size()];
		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			Vec3 rotorArmBody = rotor.positionBodyMeters().subtract(momentReference);
			velocities[i] = bodyRelativeAirVelocity.add(angularVelocityBody.cross(rotorArmBody));
		}
		return velocities;
	}

	private static Vec3[] rotorRelativeAirVelocitiesFromWorldKinematics(
			DroneConfig config,
			Quaternion bodyToWorldOrientation,
			Vec3 vehicleVelocityWorldMetersPerSecond,
			Vec3 angularVelocityBodyRadiansPerSecond,
			Vec3 windVelocityWorldMetersPerSecond,
			Vec3[] rotorWindVelocityWorldMetersPerSecond
	) {
		Quaternion worldToBody = finiteQuaternionOrIdentity(bodyToWorldOrientation).normalized().conjugate();
		Vec3 vehicleVelocityWorld = finiteVecOrZero(vehicleVelocityWorldMetersPerSecond);
		Vec3 baselineWindWorld = finiteVecOrZero(windVelocityWorldMetersPerSecond);
		Vec3 angularVelocityBody = finiteVecOrZero(angularVelocityBodyRadiansPerSecond);
		Vec3 momentReference = config.centerOfMassOffsetBodyMeters();
		Vec3[] velocities = new Vec3[config.rotors().size()];
		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			Vec3 localWindWorld = rotorWindWorldOrBaseline(
					rotorWindVelocityWorldMetersPerSecond,
					i,
					baselineWindWorld
			);
			Vec3 bodyRelativeAirVelocity = worldToBody.rotate(vehicleVelocityWorld.subtract(localWindWorld));
			Vec3 rotorArmBody = rotor.positionBodyMeters().subtract(momentReference);
			velocities[i] = bodyRelativeAirVelocity.add(angularVelocityBody.cross(rotorArmBody));
		}
		return velocities;
	}

	private static Vec3 rotorWindWorldOrBaseline(Vec3[] rotorWindVelocityWorldMetersPerSecond, int index, Vec3 baseline) {
		if (rotorWindVelocityWorldMetersPerSecond == null
				|| index < 0
				|| index >= rotorWindVelocityWorldMetersPerSecond.length
				|| rotorWindVelocityWorldMetersPerSecond[index] == null
				|| !rotorWindVelocityWorldMetersPerSecond[index].isFinite()) {
			return baseline;
		}
		return rotorWindVelocityWorldMetersPerSecond[index];
	}

	private static Vec3[] validateRotorRelativeAirVelocities(
			DroneConfig config,
			Vec3[] relativeAirVelocitiesBodyMetersPerSecond
	) {
		if (relativeAirVelocitiesBodyMetersPerSecond == null
				|| relativeAirVelocitiesBodyMetersPerSecond.length < config.rotors().size()) {
			throw new IllegalArgumentException("relative air velocity array must cover every configured rotor.");
		}
		Vec3[] velocities = new Vec3[config.rotors().size()];
		for (int i = 0; i < config.rotors().size(); i++) {
			velocities[i] = finiteRelativeAirVelocity(relativeAirVelocitiesBodyMetersPerSecond[i]);
		}
		return velocities;
	}

	private static double meanSignedAxialAdvanceSpeed(
			List<RotorSpec> rotors,
			Vec3[] relativeAirVelocitiesBodyMetersPerSecond
	) {
		if (rotors == null || rotors.isEmpty() || relativeAirVelocitiesBodyMetersPerSecond == null) {
			return 0.0;
		}
		int count = Math.min(rotors.size(), relativeAirVelocitiesBodyMetersPerSecond.length);
		double sum = 0.0;
		for (int i = 0; i < count; i++) {
			sum += relativeAirVelocitiesBodyMetersPerSecond[i].dot(rotorAxisBody(rotors.get(i)));
		}
		return count == 0 ? 0.0 : sum / count;
	}

	private static double targetThrustTolerance(double targetThrustNewtons) {
		return Math.max(1.0e-8, Math.abs(targetThrustNewtons) * TARGET_THRUST_SOLVE_RELATIVE_TOLERANCE);
	}

	private static double[] allocateStaticTrimThrusts(
			List<RotorSpec> rotors,
			Vec3 centerOfMassOffsetBodyMeters,
			double targetThrustNewtons,
			Vec3 targetBodyTorqueNewtonMeters
	) {
		int rotorCount = rotors == null ? 0 : rotors.size();
		if (rotorCount == 0 || !Double.isFinite(targetThrustNewtons) || targetThrustNewtons < 0.0) {
			return null;
		}
		Vec3 targetTorque = finiteVecOrZero(targetBodyTorqueNewtonMeters);
		Vec3 centerOfMass = finiteVecOrZero(centerOfMassOffsetBodyMeters);
		double[][] rows = new double[4][rotorCount];
		for (int i = 0; i < rotorCount; i++) {
			RotorSpec rotor = rotors.get(i);
			Vec3 arm = rotor.positionBodyMeters().subtract(centerOfMass);
			Vec3 torqueCoefficient = rotorTorqueCoefficientPerThrust(rotor, arm);
			rows[0][i] = 1.0;
			rows[1][i] = torqueCoefficient.x();
			rows[2][i] = torqueCoefficient.y();
			rows[3][i] = torqueCoefficient.z();
		}
		double[][] gram = new double[4][4];
		for (int row = 0; row < 4; row++) {
			for (int column = 0; column < 4; column++) {
				double value = 0.0;
				for (int rotor = 0; rotor < rotorCount; rotor++) {
					value += rows[row][rotor] * rows[column][rotor];
				}
				gram[row][column] = value;
			}
		}
		double trace = gram[0][0] + gram[1][1] + gram[2][2] + gram[3][3];
		if (!Double.isFinite(trace) || trace <= EPSILON) {
			return null;
		}
		double damping = Math.max(1.0e-12, trace * 1.0e-10);
		for (int i = 0; i < 4; i++) {
			gram[i][i] += damping;
		}
		double[] lambda = solveLinearSystem(
				gram,
				new double[] { targetThrustNewtons, targetTorque.x(), targetTorque.y(), targetTorque.z() }
		);
		if (lambda == null) {
			return null;
		}
		double[] thrusts = new double[rotorCount];
		for (int rotor = 0; rotor < rotorCount; rotor++) {
			double thrust = 0.0;
			for (int row = 0; row < 4; row++) {
				thrust += rows[row][rotor] * lambda[row];
			}
			if (!Double.isFinite(thrust)) {
				return null;
			}
			thrusts[rotor] = Math.abs(thrust) <= 1.0e-10 ? 0.0 : thrust;
		}
		return thrusts;
	}

	private static Vec3 rotorTorqueCoefficientPerThrust(RotorSpec rotor, Vec3 rotorArmBody) {
		Vec3 axis = rotorAxisBody(rotor);
		Vec3 arm = finiteVecOrZero(rotorArmBody);
		return arm.cross(axis)
				.add(axis.multiply(rotor.spinDirection() * rotor.yawTorquePerThrustMeter()));
	}

	private static double[] solveLinearSystem(double[][] matrix, double[] rhs) {
		int size = rhs == null ? 0 : rhs.length;
		if (matrix == null || size == 0 || matrix.length < size) {
			return null;
		}
		double[][] augmented = new double[size][size + 1];
		for (int row = 0; row < size; row++) {
			if (matrix[row] == null || matrix[row].length < size || !Double.isFinite(rhs[row])) {
				return null;
			}
			for (int column = 0; column < size; column++) {
				double value = matrix[row][column];
				if (!Double.isFinite(value)) {
					return null;
				}
				augmented[row][column] = value;
			}
			augmented[row][size] = rhs[row];
		}
		for (int pivot = 0; pivot < size; pivot++) {
			int pivotRow = pivot;
			double pivotAbs = Math.abs(augmented[pivot][pivot]);
			for (int row = pivot + 1; row < size; row++) {
				double candidateAbs = Math.abs(augmented[row][pivot]);
				if (candidateAbs > pivotAbs) {
					pivotAbs = candidateAbs;
					pivotRow = row;
				}
			}
			if (pivotAbs <= 1.0e-12) {
				return null;
			}
			if (pivotRow != pivot) {
				double[] temp = augmented[pivot];
				augmented[pivot] = augmented[pivotRow];
				augmented[pivotRow] = temp;
			}
			double pivotValue = augmented[pivot][pivot];
			for (int column = pivot; column <= size; column++) {
				augmented[pivot][column] /= pivotValue;
			}
			for (int row = 0; row < size; row++) {
				if (row == pivot) {
					continue;
				}
				double factor = augmented[row][pivot];
				if (Math.abs(factor) <= 1.0e-15) {
					continue;
				}
				for (int column = pivot; column <= size; column++) {
					augmented[row][column] -= factor * augmented[pivot][column];
				}
			}
		}
		double[] solution = new double[size];
		for (int row = 0; row < size; row++) {
			solution[row] = augmented[row][size];
			if (!Double.isFinite(solution[row])) {
				return null;
			}
		}
		return solution;
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

	private static double inflowAngleRadians(double axialVelocity, double transverseAirSpeed) {
		double axial = finiteOrZero(axialVelocity);
		double transverse = finiteNonnegative(transverseAirSpeed);
		if (transverse <= EPSILON && axial >= 0.0) {
			return 0.0;
		}
		if (transverse <= EPSILON) {
			return Math.PI;
		}
		return Math.atan2(transverse, axial);
	}

	public static RotorOperatingPoint standardOperatingPoint(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter
	) {
		return operatingPoint(
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS
		);
	}

	public static RotorOperatingPoint operatingPoint(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius
	) {
		return operatingPoint(
				rotor,
				relativeAirVelocityBodyMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				0.0
		);
	}

	public static RotorOperatingPoint operatingPoint(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		Vec3 relativeAirVelocity = relativeAirVelocityBodyMetersPerSecond == null
				? Vec3.ZERO
				: finiteVecOrZero(relativeAirVelocityBodyMetersPerSecond);
		Vec3 axis = rotorAxisBody(rotor);
		double axialSpeed = Math.abs(relativeAirVelocity.dot(axis));
		double transverseSpeed = relativeAirVelocity.subtract(axis.multiply(relativeAirVelocity.dot(axis))).length();
		double rotationalTipSpeed = Math.abs(omegaRadiansPerSecond) * rotor.radiusMeters();
		double helicalTipSpeed = Math.sqrt(
				rotationalTipSpeed * rotationalTipSpeed
						+ 0.25 * transverseSpeed * transverseSpeed
						+ 0.16 * axialSpeed * axialSpeed
		);
		double temperature = Double.isFinite(ambientTemperatureCelsius)
				? MathUtil.clamp(ambientTemperatureCelsius, -40.0, 65.0)
				: STANDARD_OPERATING_POINT_TEMPERATURE_CELSIUS;
		double humidity = Double.isFinite(ambientHumidity)
				? MathUtil.clamp(ambientHumidity, 0.0, 1.0)
				: 0.0;
		double speedOfSound = DroneEnvironment.speedOfSoundMetersPerSecond(temperature, humidity);
		double tipMach = ratio(helicalTipSpeed, speedOfSound);
		double stationSpeed = Math.sqrt(
				0.75 * rotationalTipSpeed * 0.75 * rotationalTipSpeed
						+ 0.25 * transverseSpeed * transverseSpeed
						+ 0.16 * axialSpeed * axialSpeed
		);
		double chord = rotor.representativeBladeChordMeters();
		double dynamicViscosity = airDynamicViscosityPascalSeconds(temperature, humidity);
		double reynolds = dynamicViscosity > EPSILON
				? finiteNonnegative(airDensityKgPerCubicMeter) * stationSpeed * chord / dynamicViscosity
				: 0.0;
		double densityViscosityRatio = MathUtil.clamp(
				ratio(finiteNonnegative(airDensityKgPerCubicMeter), SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER)
						/ Math.max(EPSILON, ratio(dynamicViscosity, REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS)),
				0.20,
				1.90
		);
		double chordScale = MathUtil.clamp(
				chord / (0.0635 * RotorSpec.DEFAULT_REPRESENTATIVE_CHORD_TO_RADIUS_RATIO),
				0.24,
				3.60
		);
		double reynoldsIndex = densityViscosityRatio
				* chordScale
				* MathUtil.clamp(stationSpeed / 34.0, 0.0, 2.8);
		return new RotorOperatingPoint(
				temperature,
				humidity,
				airDensityKgPerCubicMeter,
				dynamicViscosity,
				speedOfSound,
				rotationalTipSpeed,
				helicalTipSpeed,
				tipMach,
				stationSpeed,
				chord,
				reynolds,
				reynoldsIndex
		);
	}

	private static double airDynamicViscosityPascalSeconds(double ambientTemperatureCelsius) {
		return airDynamicViscosityPascalSeconds(ambientTemperatureCelsius, 0.0);
	}

	private static double airDynamicViscosityPascalSeconds(
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		double temperatureKelvin = MathUtil.clamp(ambientTemperatureCelsius + 273.15, 233.15, 338.15);
		double ratio = Math.pow(temperatureKelvin / REFERENCE_AIR_TEMPERATURE_KELVIN, 1.5)
				* (REFERENCE_AIR_TEMPERATURE_KELVIN + AIR_SUTHERLAND_CONSTANT_KELVIN)
				/ (temperatureKelvin + AIR_SUTHERLAND_CONSTANT_KELVIN);
		return REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS
				* MathUtil.clamp(
						ratio * DroneEnvironment.moistAirDynamicViscosityMultiplier(
								ambientTemperatureCelsius,
								ambientHumidity
						),
						0.64,
						1.20
				);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
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

	private static Quaternion finiteQuaternionOrIdentity(Quaternion value) {
		if (value == null
				|| !Double.isFinite(value.w())
				|| !Double.isFinite(value.x())
				|| !Double.isFinite(value.y())
				|| !Double.isFinite(value.z())) {
			return Quaternion.IDENTITY;
		}
		return value;
	}

	private static Vec3 rotateBodyVectorToWorld(Vec3 bodyVector, Quaternion bodyToWorldOrientation) {
		return finiteQuaternionOrIdentity(bodyToWorldOrientation).normalized().rotate(finiteVecOrZero(bodyVector));
	}
}
