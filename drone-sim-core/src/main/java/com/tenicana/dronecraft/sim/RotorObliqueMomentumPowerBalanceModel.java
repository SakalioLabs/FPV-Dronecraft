package com.tenicana.dronecraft.sim;

import java.util.List;

/**
 * Combines CT/CP shaft power with steady actuator-disk momentum power without
 * assigning the remaining power to an unmeasured blade-profile model.
 * NASA-CR-203092 defines hover figure of merit as ideal over actual power;
 * NASA/TP-20220000355 keeps induced-power factor and profile power separate.
 */
public final class RotorObliqueMomentumPowerBalanceModel {
	private static final double EPSILON = 1.0e-12;
	private static final double POWER_DEFICIT_ABSOLUTE_TOLERANCE_WATTS = 1.0e-9;
	private static final double POWER_DEFICIT_RELATIVE_TOLERANCE = 1.0e-10;

	private RotorObliqueMomentumPowerBalanceModel() {
	}

	public enum Status {
		ZERO_THRUST,
		SOLVED_HOVER,
		SOLVED_NORMAL_WORKING,
		SOLVED_WAKE_CONVECTED_DESCENT,
		BLOCKED_MOMENTUM_INFLOW,
		BLOCKED_SHAFT_POWER_DEFICIT
	}

	public static RotorObliqueMomentumPowerBalanceSample solve(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double thrustNewtons,
			double shaftPowerWatts,
			double angularVelocityRadiansPerSecond,
			double airDensityKgPerCubicMeter
	) {
		validatePowerInputs(shaftPowerWatts, angularVelocityRadiansPerSecond);
		RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample inflow =
				RotorObliqueMomentumInflowModel.solve(
						rotor,
						relativeAirVelocityBodyMetersPerSecond,
						thrustNewtons,
						airDensityKgPerCubicMeter
				);
		if (inflow.status() == RotorObliqueMomentumInflowModel.Status.ZERO_THRUST) {
			return sample(
					inflow,
					Status.ZERO_THRUST,
					shaftPowerWatts,
					angularVelocityRadiansPerSecond
			);
		}
		if (!inflow.solved()) {
			return sample(
					inflow,
					Status.BLOCKED_MOMENTUM_INFLOW,
					shaftPowerWatts,
					angularVelocityRadiansPerSecond
			);
		}

		double idealMomentumPower = inflow.idealMomentumPowerWatts();
		double shaftPowerMargin = shaftPowerWatts - idealMomentumPower;
		double deficitTolerance = Math.max(
				POWER_DEFICIT_ABSOLUTE_TOLERANCE_WATTS,
				Math.max(shaftPowerWatts, idealMomentumPower) * POWER_DEFICIT_RELATIVE_TOLERANCE
		);
		Status status = shaftPowerMargin < -deficitTolerance
				? Status.BLOCKED_SHAFT_POWER_DEFICIT
				: solvedStatus(inflow.status());
		return sample(inflow, status, shaftPowerWatts, angularVelocityRadiansPerSecond);
	}

	public static ConfigurationRotorObliqueMomentumPowerBalanceSample aggregate(
			List<RotorObliqueMomentumPowerBalanceSample> rotorSamples
	) {
		List<RotorObliqueMomentumPowerBalanceSample> requestedSamples = rotorSamples == null
				? List.of()
				: rotorSamples;
		for (RotorObliqueMomentumPowerBalanceSample sample : requestedSamples) {
			if (sample == null) {
				throw new IllegalArgumentException("rotorSamples must not contain null entries.");
			}
		}
		List<RotorObliqueMomentumPowerBalanceSample> samples = List.copyOf(requestedSamples);
		int solvedRotorCount = 0;
		int zeroThrustRotorCount = 0;
		int momentumBlockedRotorCount = 0;
		int shaftPowerDeficitRotorCount = 0;
		double requestedThrust = 0.0;
		double requestedShaftPower = 0.0;
		double partitionedShaftPower = 0.0;
		double usefulAxialPower = 0.0;
		double idealInducedPower = 0.0;
		double idealMomentumPower = 0.0;
		double unresolvedNonidealPower = 0.0;
		double shaftPowerDeficit = 0.0;
		double shaftTorque = 0.0;
		double idealMomentumTorque = 0.0;
		double unresolvedNonidealTorque = 0.0;
		double shaftTorqueDeficit = 0.0;
		double maximumPowerClosureResidual = 0.0;
		double maximumTorqueClosureResidual = 0.0;
		for (RotorObliqueMomentumPowerBalanceSample sample : samples) {
			requestedThrust += sample.inflow().thrustNewtons();
			requestedShaftPower += sample.shaftPowerWatts();
			if (sample.solved()) {
				solvedRotorCount++;
			} else if (sample.status() == Status.ZERO_THRUST) {
				zeroThrustRotorCount++;
			} else if (sample.status() == Status.BLOCKED_SHAFT_POWER_DEFICIT) {
				shaftPowerDeficitRotorCount++;
			} else {
				momentumBlockedRotorCount++;
			}
			if (!sample.partitioned()) {
				continue;
			}
			partitionedShaftPower += sample.shaftPowerWatts();
			usefulAxialPower += sample.usefulAxialPowerWatts();
			idealInducedPower += sample.idealInducedPowerWatts();
			idealMomentumPower += sample.idealMomentumPowerWatts();
			unresolvedNonidealPower += sample.unresolvedNonidealPowerWatts();
			shaftPowerDeficit += sample.shaftPowerDeficitWatts();
			shaftTorque += sample.shaftTorqueNewtonMeters();
			idealMomentumTorque += sample.idealMomentumTorqueNewtonMeters();
			unresolvedNonidealTorque += sample.unresolvedNonidealTorqueNewtonMeters();
			shaftTorqueDeficit += sample.shaftTorqueDeficitNewtonMeters();
			maximumPowerClosureResidual = Math.max(
					maximumPowerClosureResidual,
					Math.abs(sample.powerClosureResidualWatts())
			);
			maximumTorqueClosureResidual = Math.max(
					maximumTorqueClosureResidual,
					Math.abs(sample.torqueClosureResidualNewtonMeters())
			);
		}
		return new ConfigurationRotorObliqueMomentumPowerBalanceSample(
				samples,
				solvedRotorCount,
				zeroThrustRotorCount,
				momentumBlockedRotorCount,
				shaftPowerDeficitRotorCount,
				requestedThrust,
				requestedShaftPower,
				partitionedShaftPower,
				usefulAxialPower,
				idealInducedPower,
				idealMomentumPower,
				unresolvedNonidealPower,
				shaftPowerDeficit,
				shaftTorque,
				idealMomentumTorque,
				unresolvedNonidealTorque,
				shaftTorqueDeficit,
				maximumPowerClosureResidual,
				maximumTorqueClosureResidual
		);
	}

	private static RotorObliqueMomentumPowerBalanceSample sample(
			RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample inflow,
			Status status,
			double shaftPowerWatts,
			double angularVelocityRadiansPerSecond
	) {
		boolean partitioned = status != Status.BLOCKED_MOMENTUM_INFLOW;
		double shaftTorque = torqueForPower(shaftPowerWatts, angularVelocityRadiansPerSecond);
		double usefulAxialPower = partitioned ? inflow.usefulAxialPowerWatts() : 0.0;
		double idealInducedPower = partitioned ? inflow.idealInducedPowerWatts() : 0.0;
		double idealMomentumPower = partitioned ? inflow.idealMomentumPowerWatts() : 0.0;
		double idealMomentumTorque = torqueForPower(
				idealMomentumPower,
				angularVelocityRadiansPerSecond
		);
		double signedShaftPowerMargin = partitioned
				? shaftPowerWatts - idealMomentumPower
				: 0.0;
		double unresolvedNonidealPower = partitioned
				? Math.max(0.0, signedShaftPowerMargin)
				: 0.0;
		double shaftPowerDeficit = partitioned
				? Math.max(0.0, -signedShaftPowerMargin)
				: 0.0;
		double unresolvedNonidealTorque = torqueForPower(
				unresolvedNonidealPower,
				angularVelocityRadiansPerSecond
		);
		double shaftTorqueDeficit = torqueForPower(
				shaftPowerDeficit,
				angularVelocityRadiansPerSecond
		);
		double momentumPowerFraction = partitioned
				? ratio(idealMomentumPower, shaftPowerWatts)
				: 0.0;
		double axialPropulsiveEfficiency = partitioned
				? ratio(usefulAxialPower, shaftPowerWatts)
				: 0.0;
		// NASA rotor power accounting separates induced and profile power. With no measured
		// blade profile model, this ratio is only a CT/CP-constrained upper bound on kappa.
		double nonUsefulPowerOverIdealInducedPower = partitioned
				? ratio(shaftPowerWatts - usefulAxialPower, idealInducedPower)
				: 0.0;
		double hoverFigureOfMerit = status == Status.SOLVED_HOVER
				? ratio(idealInducedPower, shaftPowerWatts)
				: 0.0;
		double powerClosureResidual = partitioned
				? shaftPowerWatts
						- idealMomentumPower
						- unresolvedNonidealPower
						+ shaftPowerDeficit
				: 0.0;
		double torqueClosureResidual = partitioned
				? shaftTorque
						- idealMomentumTorque
						- unresolvedNonidealTorque
						+ shaftTorqueDeficit
				: 0.0;
		return new RotorObliqueMomentumPowerBalanceSample(
				inflow,
				status,
				shaftPowerWatts,
				angularVelocityRadiansPerSecond,
				shaftTorque,
				usefulAxialPower,
				idealInducedPower,
				idealMomentumPower,
				idealMomentumTorque,
				signedShaftPowerMargin,
				unresolvedNonidealPower,
				shaftPowerDeficit,
				unresolvedNonidealTorque,
				shaftTorqueDeficit,
				momentumPowerFraction,
				axialPropulsiveEfficiency,
				nonUsefulPowerOverIdealInducedPower,
				hoverFigureOfMerit,
				powerClosureResidual,
				torqueClosureResidual
		);
	}

	private static Status solvedStatus(RotorObliqueMomentumInflowModel.Status status) {
		return switch (status) {
			case SOLVED_HOVER -> Status.SOLVED_HOVER;
			case SOLVED_NORMAL_WORKING -> Status.SOLVED_NORMAL_WORKING;
			case SOLVED_WAKE_CONVECTED_DESCENT -> Status.SOLVED_WAKE_CONVECTED_DESCENT;
			default -> throw new IllegalArgumentException("status is not a solved momentum-inflow state: " + status);
		};
	}

	private static double torqueForPower(double powerWatts, double angularVelocityRadiansPerSecond) {
		return angularVelocityRadiansPerSecond > EPSILON
				? powerWatts / angularVelocityRadiansPerSecond
				: 0.0;
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		double value = numerator / denominator;
		return Double.isFinite(value) ? value : 0.0;
	}

	private static void validatePowerInputs(
			double shaftPowerWatts,
			double angularVelocityRadiansPerSecond
	) {
		if (!Double.isFinite(shaftPowerWatts) || shaftPowerWatts < 0.0) {
			throw new IllegalArgumentException("shaftPowerWatts must be finite and nonnegative.");
		}
		if (!Double.isFinite(angularVelocityRadiansPerSecond)
				|| angularVelocityRadiansPerSecond < 0.0) {
			throw new IllegalArgumentException(
					"angularVelocityRadiansPerSecond must be finite and nonnegative."
			);
		}
		if (shaftPowerWatts > EPSILON && angularVelocityRadiansPerSecond <= EPSILON) {
			throw new IllegalArgumentException("positive shaft power requires positive angular velocity.");
		}
	}

	public record RotorObliqueMomentumPowerBalanceSample(
			RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample inflow,
			Status status,
			double shaftPowerWatts,
			double angularVelocityRadiansPerSecond,
			double shaftTorqueNewtonMeters,
			double usefulAxialPowerWatts,
			double idealInducedPowerWatts,
			double idealMomentumPowerWatts,
			double idealMomentumTorqueNewtonMeters,
			double signedShaftPowerMarginWatts,
			double unresolvedNonidealPowerWatts,
			double shaftPowerDeficitWatts,
			double unresolvedNonidealTorqueNewtonMeters,
			double shaftTorqueDeficitNewtonMeters,
			double momentumPowerFraction,
			double axialPropulsiveEfficiency,
			double nonUsefulPowerOverIdealInducedPower,
			double hoverFigureOfMerit,
			double powerClosureResidualWatts,
			double torqueClosureResidualNewtonMeters
	) {
		public boolean solved() {
			return status == Status.SOLVED_HOVER
					|| status == Status.SOLVED_NORMAL_WORKING
					|| status == Status.SOLVED_WAKE_CONVECTED_DESCENT;
		}

		public boolean blocked() {
			return status == Status.BLOCKED_MOMENTUM_INFLOW
					|| status == Status.BLOCKED_SHAFT_POWER_DEFICIT;
		}

		public boolean partitioned() {
			return status != Status.BLOCKED_MOMENTUM_INFLOW;
		}

		public boolean hoverFigureOfMeritApplicable() {
			return status == Status.SOLVED_HOVER;
		}

		/**
		 * Returns a bound implied by CT/CP power, not a fitted induced-power factor.
		 * Nonnegative profile and other non-induced losses can only lower true kappa.
		 */
		public double inducedPowerFactorUpperBound() {
			return solved() ? nonUsefulPowerOverIdealInducedPower : 0.0;
		}
	}

	public record ConfigurationRotorObliqueMomentumPowerBalanceSample(
			List<RotorObliqueMomentumPowerBalanceSample> rotorSamples,
			int solvedRotorCount,
			int zeroThrustRotorCount,
			int momentumBlockedRotorCount,
			int shaftPowerDeficitRotorCount,
			double requestedThrustNewtons,
			double requestedShaftPowerWatts,
			double partitionedShaftPowerWatts,
			double usefulAxialPowerWatts,
			double idealInducedPowerWatts,
			double idealMomentumPowerWatts,
			double unresolvedNonidealPowerWatts,
			double shaftPowerDeficitWatts,
			double shaftTorqueNewtonMeters,
			double idealMomentumTorqueNewtonMeters,
			double unresolvedNonidealTorqueNewtonMeters,
			double shaftTorqueDeficitNewtonMeters,
			double maximumPowerClosureResidualWatts,
			double maximumTorqueClosureResidualNewtonMeters
	) {
		public ConfigurationRotorObliqueMomentumPowerBalanceSample {
			rotorSamples = List.copyOf(rotorSamples == null ? List.of() : rotorSamples);
		}

		public int rotorCount() {
			return rotorSamples.size();
		}

		public int blockedRotorCount() {
			return momentumBlockedRotorCount + shaftPowerDeficitRotorCount;
		}

		public double momentumPowerFraction() {
			return ratio(idealMomentumPowerWatts, partitionedShaftPowerWatts);
		}

		public double nonUsefulPowerOverIdealInducedPower() {
			return ratio(
					partitionedShaftPowerWatts - usefulAxialPowerWatts,
					idealInducedPowerWatts
			);
		}
	}
}
