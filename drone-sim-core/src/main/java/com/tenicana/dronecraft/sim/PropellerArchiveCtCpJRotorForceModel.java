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
	}

	public enum RotorTargetThrustSolveStatus {
		SOLVED,
		LOWER_BOUND_EXCEEDS_TARGET,
		UPPER_BOUND_BLOCKED,
		UPPER_BOUND_BELOW_TARGET,
		NO_CONVERGENCE
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

		RotorForceSample lower = solveTargetSample(
				presetName,
				caseName,
				rotor,
				signedAxialAdvanceSpeedMetersPerSecond,
				lowerOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
		RotorForceSample upper = solveTargetSample(
				presetName,
				caseName,
				rotor,
				signedAxialAdvanceSpeedMetersPerSecond,
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
			RotorForceSample mid = solveTargetSample(
					presetName,
					caseName,
					rotor,
					signedAxialAdvanceSpeedMetersPerSecond,
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

		RotorForceAggregateSample lower = solveTargetConfigurationSample(
				presetName,
				caseName,
				config,
				signedAxialAdvanceSpeedMetersPerSecond,
				lowerOmegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				ambientTemperatureCelsius,
				ambientHumidity
		);
		RotorForceAggregateSample upper = solveTargetConfigurationSample(
				presetName,
				caseName,
				config,
				signedAxialAdvanceSpeedMetersPerSecond,
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
			RotorForceAggregateSample mid = solveTargetConfigurationSample(
					presetName,
					caseName,
					config,
					signedAxialAdvanceSpeedMetersPerSecond,
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
					0.0, 0.0, 0.0, 0.0, 0.0,
					Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
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
		double totalDiskMassFlow = 0.0;
		double totalUsefulAxialPower = 0.0;
		double totalIdealInducedPower = 0.0;
		double totalIdealMomentumPower = 0.0;
		double totalWakeSwirlPower = 0.0;
		double totalWakePower = 0.0;
		double runtimeForceReplacementThrust = 0.0;
		double runtimeForceReplacementPower = 0.0;
		double runtimeForceReplacementShaftTorque = 0.0;
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
		double totalWakeResidual = totalPower - totalWakePower;
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

	private static RotorForceSample solveTargetSample(
			String presetName,
			String caseName,
			RotorSpec rotor,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		return sampleStaticAnchoredFromSignedAxialAdvanceSpeed(
				presetName,
				caseName,
				rotor,
				signedAxialAdvanceSpeedMetersPerSecond,
				omegaRadiansPerSecond,
				airDensityKgPerCubicMeter,
				Vec3.ZERO,
				PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE,
				ambientTemperatureCelsius,
				ambientHumidity
		);
	}

	private static RotorForceAggregateSample solveTargetConfigurationSample(
			String presetName,
			String caseName,
			DroneConfig config,
			double signedAxialAdvanceSpeedMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		int rotorCount = config.rotors().size();
		return sampleStaticAnchoredConfigurationFromSignedAxialAdvanceSpeeds(
				presetName,
				caseName,
				config,
				uniformArray(rotorCount, signedAxialAdvanceSpeedMetersPerSecond),
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

	private static double targetThrustTolerance(double targetThrustNewtons) {
		return Math.max(1.0e-8, Math.abs(targetThrustNewtons) * TARGET_THRUST_SOLVE_RELATIVE_TOLERANCE);
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
}
