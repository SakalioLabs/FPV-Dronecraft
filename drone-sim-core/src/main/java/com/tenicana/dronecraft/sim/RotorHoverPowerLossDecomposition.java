package com.tenicana.dronecraft.sim;

/**
 * Separates measured hover shaft power into reference-thrust-conditioned
 * lift-induced power, modeled profile power, and unresolved power. The
 * effective profile scale is diagnostic only: unresolved hub, surface,
 * three-dimensional, and other viscous losses are not assumed to be section Cd.
 */
public final class RotorHoverPowerLossDecomposition {
	private static final double EPSILON = 1.0e-12;
	private static final double OPERATING_POINT_RELATIVE_TOLERANCE = 1.0e-9;

	private RotorHoverPowerLossDecomposition() {
	}

	public static PowerLossSample decompose(
			UiucDa4002StaticPerformanceLookup.DimensionalSample reference,
			RotorHoverBladeElementModel.HoverSample bladeElement
	) {
		validateInputs(reference, bladeElement);
		double measuredShaftPower = reference.shaftPowerWatts();
		double measuredShaftTorque = reference.shaftTorqueNewtonMeters();
		double angularVelocity = reference.angularVelocityRadiansPerSecond();
		double referenceIdealPower = reference.idealInducedPowerWatts();
		double inducedPowerFactor = bladeElement.liftInducedPowerOverUniformIdeal();
		double referenceConditionedLiftPower = inducedPowerFactor * referenceIdealPower;
		double modeledProfilePower = bladeElement.profilePowerWatts();
		double referenceConditionedModeledPower = referenceConditionedLiftPower
				+ modeledProfilePower;
		double requiredNonLiftPower = measuredShaftPower - referenceConditionedLiftPower;
		double conditionedUnresolvedPower = measuredShaftPower
				- referenceConditionedModeledPower;
		double rawUnresolvedPower = measuredShaftPower - bladeElement.shaftPowerWatts();
		boolean effectiveProfileScaleAvailable = modeledProfilePower > EPSILON;
		double effectiveProfileScale = effectiveProfileScaleAvailable
				? requiredNonLiftPower / modeledProfilePower
				: 0.0;
		double conditionedLiftTorque = referenceConditionedLiftPower / angularVelocity;
		double requiredNonLiftTorque = requiredNonLiftPower / angularVelocity;
		double conditionedUnresolvedTorque = conditionedUnresolvedPower / angularVelocity;
		double rawUnresolvedTorque = measuredShaftTorque
				- bladeElement.shaftTorqueNewtonMeters();
		double powerClosureResidual = measuredShaftPower
				- referenceConditionedLiftPower
				- requiredNonLiftPower;
		double torqueClosureResidual = measuredShaftTorque
				- conditionedLiftTorque
				- requiredNonLiftTorque;
		double revolutionsPerSecond = reference.revolutionsPerSecond();
		double diameter = reference.propellerDiameterMeters();
		double propellerPowerDenominator = reference.airDensityKgPerCubicMeter()
				* Math.pow(revolutionsPerSecond, 3.0)
				* Math.pow(diameter, 5.0);
		return new PowerLossSample(
				reference,
				bladeElement,
				measuredShaftPower,
				bladeElement.shaftPowerWatts(),
				rawUnresolvedPower,
				ratio(rawUnresolvedPower, measuredShaftPower),
				measuredShaftTorque,
				bladeElement.shaftTorqueNewtonMeters(),
				rawUnresolvedTorque,
				referenceIdealPower,
				inducedPowerFactor,
				referenceConditionedLiftPower,
				modeledProfilePower,
				referenceConditionedModeledPower,
				requiredNonLiftPower,
				conditionedUnresolvedPower,
				ratio(conditionedUnresolvedPower, measuredShaftPower),
				effectiveProfileScale,
				effectiveProfileScaleAvailable,
				conditionedLiftTorque,
				requiredNonLiftTorque,
				conditionedUnresolvedTorque,
				powerClosureResidual,
				torqueClosureResidual,
				ratio(referenceConditionedLiftPower, propellerPowerDenominator),
				ratio(modeledProfilePower, propellerPowerDenominator),
				ratio(requiredNonLiftPower, propellerPowerDenominator),
				ratio(conditionedUnresolvedPower, propellerPowerDenominator)
		);
	}

	private static void validateInputs(
			UiucDa4002StaticPerformanceLookup.DimensionalSample reference,
			RotorHoverBladeElementModel.HoverSample bladeElement
	) {
		if (reference == null || reference.blocked()) {
			throw new IllegalArgumentException("reference must be a non-blocked static sample.");
		}
		if (bladeElement == null || !bladeElement.solved()) {
			throw new IllegalArgumentException("bladeElement must be a solved hover BEMT sample.");
		}
		if (!reference.lookup().curve().geometry().id()
				.equals(bladeElement.query().geometry().id())) {
			throw new IllegalArgumentException("reference and bladeElement geometry ids must match.");
		}
		double bladeElementRpm = bladeElement.query().angularVelocityRadiansPerSecond()
				* 60.0
				/ (2.0 * Math.PI);
		if (!sameOperatingValue(reference.lookup().effectiveRpm(), bladeElementRpm)
				|| !sameOperatingValue(
						reference.propellerDiameterMeters(),
						bladeElement.query().rotorRadiusMeters() * 2.0
				)
				|| !sameOperatingValue(
						reference.airDensityKgPerCubicMeter(),
						bladeElement.query().airDensityKgPerCubicMeter()
				)
				|| !sameOperatingValue(
						reference.dynamicViscosityPascalSeconds(),
						bladeElement.query().dynamicViscosityPascalSeconds()
				)) {
			throw new IllegalArgumentException(
					"reference and bladeElement RPM, diameter, density, and viscosity must match."
			);
		}
	}

	private static boolean sameOperatingValue(double left, double right) {
		double scale = Math.max(1.0, Math.max(Math.abs(left), Math.abs(right)));
		return Math.abs(left - right) <= OPERATING_POINT_RELATIVE_TOLERANCE * scale;
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator)
				|| !Double.isFinite(denominator)
				|| Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		double value = numerator / denominator;
		return Double.isFinite(value) ? value : 0.0;
	}

	public record PowerLossSample(
			UiucDa4002StaticPerformanceLookup.DimensionalSample reference,
			RotorHoverBladeElementModel.HoverSample bladeElement,
			double measuredShaftPowerWatts,
			double bladeElementShaftPowerWatts,
			double rawUnresolvedShaftPowerWatts,
			double rawUnresolvedShaftPowerFraction,
			double measuredShaftTorqueNewtonMeters,
			double bladeElementShaftTorqueNewtonMeters,
			double rawUnresolvedShaftTorqueNewtonMeters,
			double referenceIdealInducedPowerWatts,
			double bladeElementInducedPowerFactor,
			double referenceConditionedLiftInducedPowerWatts,
			double bladeElementProfilePowerWatts,
			double referenceConditionedModeledPowerWatts,
			double requiredNonLiftPowerWatts,
			double conditionedUnresolvedPowerWatts,
			double conditionedUnresolvedPowerFraction,
			double effectiveProfilePowerScaleIfAllNonLiftLossWereProfile,
			boolean effectiveProfilePowerScaleAvailable,
			double referenceConditionedLiftInducedTorqueNewtonMeters,
			double requiredNonLiftTorqueNewtonMeters,
			double conditionedUnresolvedTorqueNewtonMeters,
			double requiredPowerClosureResidualWatts,
			double requiredTorqueClosureResidualNewtonMeters,
			double referenceConditionedLiftInducedPowerCoefficientCp,
			double bladeElementProfilePowerCoefficientCp,
			double requiredNonLiftPowerCoefficientCp,
			double conditionedUnresolvedPowerCoefficientCp
	) {
		public boolean rawModelUnderpredictsPower() {
			return rawUnresolvedShaftPowerWatts > 0.0;
		}

		public boolean conditionedModelUnderpredictsPower() {
			return conditionedUnresolvedPowerWatts > 0.0;
		}
	}
}
