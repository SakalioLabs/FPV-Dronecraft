package com.tenicana.dronecraft.sim;

import java.util.List;

/**
 * Rotation-dominated hover profile-power model using measured blade stations.
 * The mean section drag coefficient remains an explicit input because chord and
 * twist geometry alone do not identify an airfoil drag polar.
 */
public final class RotorHoverBladeProfilePowerModel {
	private static final double EPSILON = 1.0e-12;
	private static final double POWER_TOLERANCE_WATTS = 1.0e-9;

	private RotorHoverBladeProfilePowerModel() {
	}

	public enum SeparationStatus {
		SOLVED,
		BLOCKED_POWER_BALANCE,
		BLOCKED_NOT_HOVER,
		BLOCKED_BLADE_COUNT_MISMATCH,
		BLOCKED_ZERO_PROFILE_POWER_SCALE,
		BLOCKED_INDUCED_POWER_EXCEEDS_SHAFT
	}

	public static ProfilePowerSample sample(
			BladeGeometry geometry,
			double rotorRadiusMeters,
			double airDensityKgPerCubicMeter,
			double angularVelocityRadiansPerSecond,
			double powerWeightedMeanSectionDragCoefficient
	) {
		validateSampleInputs(
				geometry,
				rotorRadiusMeters,
				airDensityKgPerCubicMeter,
				angularVelocityRadiansPerSecond,
				powerWeightedMeanSectionDragCoefficient
		);
		double bladeArea = geometry.bladeCount()
				* rotorRadiusMeters
				* rotorRadiusMeters
				* geometry.bladeAreaIntegralDimensionless();
		double profilePowerScale = profilePowerScaleWattsPerMeanDragCoefficient(
				geometry,
				rotorRadiusMeters,
				airDensityKgPerCubicMeter,
				angularVelocityRadiansPerSecond
		);
		double profilePower = profilePowerScale * powerWeightedMeanSectionDragCoefficient;
		double profileTorque = angularVelocityRadiansPerSecond > EPSILON
				? profilePower / angularVelocityRadiansPerSecond
				: 0.0;
		double diskArea = Math.PI * rotorRadiusMeters * rotorRadiusMeters;
		double tipSpeed = angularVelocityRadiansPerSecond * rotorRadiusMeters;
		double rotorPowerDenominator = airDensityKgPerCubicMeter
				* diskArea
				* tipSpeed
				* tipSpeed
				* tipSpeed;
		double revolutionsPerSecond = angularVelocityRadiansPerSecond / (2.0 * Math.PI);
		double propellerDiameter = rotorRadiusMeters * 2.0;
		double propellerPowerDenominator = airDensityKgPerCubicMeter
				* revolutionsPerSecond
				* revolutionsPerSecond
				* revolutionsPerSecond
				* Math.pow(propellerDiameter, 5.0);
		return new ProfilePowerSample(
				geometry,
				rotorRadiusMeters,
				airDensityKgPerCubicMeter,
				angularVelocityRadiansPerSecond,
				tipSpeed,
				powerWeightedMeanSectionDragCoefficient,
				bladeArea,
				geometry.planformSolidity(),
				geometry.profileDragMomentIntegralDimensionless(),
				profilePowerScale,
				profilePower,
				profileTorque,
				ratio(profilePower, rotorPowerDenominator),
				ratio(profilePower, propellerPowerDenominator)
		);
	}

	public static HoverPowerSeparationSample separate(
			RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample powerBalance,
			BladeGeometry geometry,
			double inducedPowerFactor
	) {
		if (powerBalance == null) {
			throw new IllegalArgumentException("powerBalance must not be null.");
		}
		if (geometry == null) {
			throw new IllegalArgumentException("geometry must not be null.");
		}
		if (!Double.isFinite(inducedPowerFactor) || inducedPowerFactor < 1.0) {
			throw new IllegalArgumentException("inducedPowerFactor must be finite and at least one.");
		}
		RotorObliqueMomentumInflowModel.RotorObliqueMomentumInflowSample inflow = powerBalance.inflow();
		RotorSpec rotor = inflow.rotor();
		ProfilePowerSample zeroProfile = sample(
				geometry,
				rotor.radiusMeters(),
				inflow.airDensityKgPerCubicMeter(),
				powerBalance.angularVelocityRadiansPerSecond(),
				0.0
		);
		if (!powerBalance.solved()) {
			return blockedSeparation(
					powerBalance,
					zeroProfile,
					SeparationStatus.BLOCKED_POWER_BALANCE,
					inducedPowerFactor,
					0.0
			);
		}
		if (powerBalance.status()
				!= RotorObliqueMomentumPowerBalanceModel.Status.SOLVED_HOVER) {
			return blockedSeparation(
					powerBalance,
					zeroProfile,
					SeparationStatus.BLOCKED_NOT_HOVER,
					inducedPowerFactor,
					0.0
			);
		}
		if (rotor.bladeCount() != geometry.bladeCount()) {
			return blockedSeparation(
					powerBalance,
					zeroProfile,
					SeparationStatus.BLOCKED_BLADE_COUNT_MISMATCH,
					inducedPowerFactor,
					0.0
			);
		}

		double separatedInducedPower = inducedPowerFactor * powerBalance.idealInducedPowerWatts();
		double signedAvailableProfilePower = powerBalance.shaftPowerWatts()
				- powerBalance.usefulAxialPowerWatts()
				- separatedInducedPower;
		if (signedAvailableProfilePower < -POWER_TOLERANCE_WATTS) {
			return blockedSeparation(
					powerBalance,
					zeroProfile,
					SeparationStatus.BLOCKED_INDUCED_POWER_EXCEEDS_SHAFT,
					inducedPowerFactor,
					signedAvailableProfilePower
			);
		}
		double profilePower = Math.max(0.0, signedAvailableProfilePower);
		double profilePowerScale = zeroProfile.profilePowerScaleWattsPerMeanDragCoefficient();
		if (profilePower > POWER_TOLERANCE_WATTS && profilePowerScale <= EPSILON) {
			return blockedSeparation(
					powerBalance,
					zeroProfile,
					SeparationStatus.BLOCKED_ZERO_PROFILE_POWER_SCALE,
					inducedPowerFactor,
					signedAvailableProfilePower
			);
		}
		double inferredMeanDragCoefficient = profilePowerScale > EPSILON
				? profilePower / profilePowerScale
				: 0.0;
		ProfilePowerSample profileSample = sample(
				geometry,
				rotor.radiusMeters(),
				inflow.airDensityKgPerCubicMeter(),
				powerBalance.angularVelocityRadiansPerSecond(),
				inferredMeanDragCoefficient
		);
		double predictedShaftPower = powerBalance.usefulAxialPowerWatts()
				+ separatedInducedPower
				+ profileSample.profilePowerWatts();
		double angularVelocity = powerBalance.angularVelocityRadiansPerSecond();
		double separatedInducedTorque = torqueForPower(separatedInducedPower, angularVelocity);
		double predictedShaftTorque = torqueForPower(predictedShaftPower, angularVelocity);
		return new HoverPowerSeparationSample(
				powerBalance,
				profileSample,
				SeparationStatus.SOLVED,
				inducedPowerFactor,
				separatedInducedPower,
				separatedInducedTorque,
				signedAvailableProfilePower,
				predictedShaftPower,
				predictedShaftTorque,
				powerBalance.shaftPowerWatts() - predictedShaftPower,
				powerBalance.shaftTorqueNewtonMeters() - predictedShaftTorque
		);
	}

	private static HoverPowerSeparationSample blockedSeparation(
			RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample powerBalance,
			ProfilePowerSample zeroProfile,
			SeparationStatus status,
			double inducedPowerFactor,
			double signedAvailableProfilePowerWatts
	) {
		double separatedInducedPower = powerBalance.idealInducedPowerWatts() * inducedPowerFactor;
		double separatedInducedTorque = torqueForPower(
				separatedInducedPower,
				powerBalance.angularVelocityRadiansPerSecond()
		);
		return new HoverPowerSeparationSample(
				powerBalance,
				zeroProfile,
				status,
				inducedPowerFactor,
				separatedInducedPower,
				separatedInducedTorque,
				signedAvailableProfilePowerWatts,
				0.0,
				0.0,
				0.0,
				0.0
		);
	}

	private static double profilePowerScaleWattsPerMeanDragCoefficient(
			BladeGeometry geometry,
			double rotorRadiusMeters,
			double airDensityKgPerCubicMeter,
			double angularVelocityRadiansPerSecond
	) {
		return 0.5
				* airDensityKgPerCubicMeter
				* geometry.bladeCount()
				* Math.pow(angularVelocityRadiansPerSecond, 3.0)
				* Math.pow(rotorRadiusMeters, 5.0)
				* geometry.profileDragMomentIntegralDimensionless();
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

	private static void validateSampleInputs(
			BladeGeometry geometry,
			double rotorRadiusMeters,
			double airDensityKgPerCubicMeter,
			double angularVelocityRadiansPerSecond,
			double powerWeightedMeanSectionDragCoefficient
	) {
		if (geometry == null) {
			throw new IllegalArgumentException("geometry must not be null.");
		}
		if (!Double.isFinite(rotorRadiusMeters) || rotorRadiusMeters <= 0.0) {
			throw new IllegalArgumentException("rotorRadiusMeters must be finite and positive.");
		}
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(angularVelocityRadiansPerSecond)
				|| angularVelocityRadiansPerSecond < 0.0) {
			throw new IllegalArgumentException(
					"angularVelocityRadiansPerSecond must be finite and nonnegative."
			);
		}
		if (!Double.isFinite(powerWeightedMeanSectionDragCoefficient)
				|| powerWeightedMeanSectionDragCoefficient < 0.0) {
			throw new IllegalArgumentException(
					"powerWeightedMeanSectionDragCoefficient must be finite and nonnegative."
			);
		}
	}

	public record BladeStation(
			double radialFraction,
			double chordToRadius,
			double pitchAngleRadians
	) {
		public BladeStation {
			if (!Double.isFinite(radialFraction) || radialFraction <= 0.0 || radialFraction > 1.0) {
				throw new IllegalArgumentException("radialFraction must be in (0, 1].");
			}
			if (!Double.isFinite(chordToRadius) || chordToRadius < 0.0) {
				throw new IllegalArgumentException("chordToRadius must be finite and nonnegative.");
			}
			if (!Double.isFinite(pitchAngleRadians)) {
				throw new IllegalArgumentException("pitchAngleRadians must be finite.");
			}
		}
	}

	public record BladeGeometry(
			String id,
			String displayName,
			String sourceUrl,
			int bladeCount,
			List<BladeStation> stations
	) {
		public BladeGeometry {
			id = id == null ? "" : id.trim();
			displayName = displayName == null ? "" : displayName.trim();
			sourceUrl = sourceUrl == null ? "" : sourceUrl.trim();
			if (id.isEmpty() || displayName.isEmpty() || sourceUrl.isEmpty()) {
				throw new IllegalArgumentException("geometry id, displayName, and sourceUrl must not be blank.");
			}
			if (bladeCount <= 0) {
				throw new IllegalArgumentException("bladeCount must be positive.");
			}
			stations = List.copyOf(stations == null ? List.of() : stations);
			if (stations.size() < 2) {
				throw new IllegalArgumentException("geometry requires at least two blade stations.");
			}
			double previousRadius = 0.0;
			for (BladeStation station : stations) {
				if (station == null) {
					throw new IllegalArgumentException("geometry stations must not contain null entries.");
				}
				if (station.radialFraction() <= previousRadius) {
					throw new IllegalArgumentException("geometry stations must be strictly increasing in radius.");
				}
				previousRadius = station.radialFraction();
			}
		}

		public int stationCount() {
			return stations.size();
		}

		public double rootCutoutFraction() {
			return stations.get(0).radialFraction();
		}

		public double bladeAreaIntegralDimensionless() {
			return integrateChordMoment(0);
		}

		public double profileDragMomentIntegralDimensionless() {
			return integrateChordMoment(3);
		}

		public double planformSolidity() {
			return bladeCount * bladeAreaIntegralDimensionless() / Math.PI;
		}

		public double rotorProfilePowerCoefficientPerMeanDragCoefficient() {
			return bladeCount * profileDragMomentIntegralDimensionless() / (2.0 * Math.PI);
		}

		public double propellerProfilePowerCoefficientPerMeanDragCoefficient() {
			return rotorProfilePowerCoefficientPerMeanDragCoefficient()
					* Math.pow(Math.PI, 4.0)
					/ 4.0;
		}

		public double chordToRadiusAt(double radialFraction) {
			if (!Double.isFinite(radialFraction)
					|| radialFraction < rootCutoutFraction()
					|| radialFraction > stations.get(stations.size() - 1).radialFraction()) {
				return 0.0;
			}
			for (int i = 1; i < stations.size(); i++) {
				BladeStation upper = stations.get(i);
				if (radialFraction > upper.radialFraction()) {
					continue;
				}
				BladeStation lower = stations.get(i - 1);
				double t = (radialFraction - lower.radialFraction())
						/ (upper.radialFraction() - lower.radialFraction());
				return lower.chordToRadius()
						+ (upper.chordToRadius() - lower.chordToRadius()) * t;
			}
			return stations.get(stations.size() - 1).chordToRadius();
		}

		public double pitchAngleRadiansAt(double radialFraction) {
			if (!Double.isFinite(radialFraction)
					|| radialFraction < rootCutoutFraction()
					|| radialFraction > stations.get(stations.size() - 1).radialFraction()) {
				throw new IllegalArgumentException("radialFraction is outside measured blade geometry.");
			}
			for (int i = 1; i < stations.size(); i++) {
				BladeStation upper = stations.get(i);
				if (radialFraction > upper.radialFraction()) {
					continue;
				}
				BladeStation lower = stations.get(i - 1);
				double t = (radialFraction - lower.radialFraction())
						/ (upper.radialFraction() - lower.radialFraction());
				return lower.pitchAngleRadians()
						+ (upper.pitchAngleRadians() - lower.pitchAngleRadians()) * t;
			}
			return stations.get(stations.size() - 1).pitchAngleRadians();
		}

		private double integrateChordMoment(int radialExponent) {
			double integral = 0.0;
			for (int i = 1; i < stations.size(); i++) {
				BladeStation lower = stations.get(i - 1);
				BladeStation upper = stations.get(i);
				double lowerValue = lower.chordToRadius()
						* Math.pow(lower.radialFraction(), radialExponent);
				double upperValue = upper.chordToRadius()
						* Math.pow(upper.radialFraction(), radialExponent);
				integral += 0.5
						* (lowerValue + upperValue)
						* (upper.radialFraction() - lower.radialFraction());
			}
			return integral;
		}
	}

	public record ProfilePowerSample(
			BladeGeometry geometry,
			double rotorRadiusMeters,
			double airDensityKgPerCubicMeter,
			double angularVelocityRadiansPerSecond,
			double rotorTipSpeedMetersPerSecond,
			double powerWeightedMeanSectionDragCoefficient,
			double totalBladePlanformAreaSquareMeters,
			double planformSolidity,
			double profileDragMomentIntegralDimensionless,
			double profilePowerScaleWattsPerMeanDragCoefficient,
			double profilePowerWatts,
			double profileTorqueNewtonMeters,
			double rotorProfilePowerCoefficient,
			double propellerProfilePowerCoefficient
	) {
	}

	public record HoverPowerSeparationSample(
			RotorObliqueMomentumPowerBalanceModel.RotorObliqueMomentumPowerBalanceSample powerBalance,
			ProfilePowerSample profilePower,
			SeparationStatus status,
			double inducedPowerFactor,
			double separatedInducedPowerWatts,
			double separatedInducedTorqueNewtonMeters,
			double signedAvailableProfilePowerWatts,
			double predictedShaftPowerWatts,
			double predictedShaftTorqueNewtonMeters,
			double powerClosureResidualWatts,
			double torqueClosureResidualNewtonMeters
	) {
		public boolean separated() {
			return status == SeparationStatus.SOLVED;
		}

		public boolean blocked() {
			return !separated();
		}
	}
}
