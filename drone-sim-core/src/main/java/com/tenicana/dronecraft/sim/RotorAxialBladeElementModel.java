package com.tenicana.dronecraft.sim;

import com.tenicana.dronecraft.sim.RotorHoverBladeElementModel.HoverSample;
import com.tenicana.dronecraft.sim.RotorHoverBladeProfilePowerModel.BladeGeometry;

/** Callable geometry-resolved BEMT for hover and positive-through-flow axial flight. */
public final class RotorAxialBladeElementModel {
	private static final double EPSILON = 1.0e-12;

	private RotorAxialBladeElementModel() {
	}

	public enum Status {
		SOLVED,
		BLOCKED_SECTION_POLAR_ENVELOPE,
		BLOCKED_ANNULAR_MOMENTUM_ROOT,
		BLOCKED_NON_POSITIVE_THRUST,
		BLOCKED_NON_POSITIVE_SHAFT_POWER
	}

	public static AxialSample solve(AxialQuery query) {
		if (query == null) {
			throw new IllegalArgumentException("query must not be null.");
		}
		HoverSample bladeElement = RotorHoverBladeElementModel.solve(
				new RotorHoverBladeElementModel.HoverQuery(
						query.geometry(),
						query.rotorRadiusMeters(),
						query.airDensityKgPerCubicMeter(),
						query.dynamicViscosityPascalSeconds(),
						query.angularVelocityRadiansPerSecond(),
						query.annuliPerGeometryInterval(),
						query.polarEnvelopePolicy(),
						query.wakeRotationPolicy(),
						query.rotationalAugmentationPolicy(),
						query.axialFreestreamVelocityMetersPerSecond()
				)
		);
		double revolutionsPerSecond = query.angularVelocityRadiansPerSecond()
				/ (2.0 * Math.PI);
		double diameter = 2.0 * query.rotorRadiusMeters();
		double advanceRatio = query.axialFreestreamVelocityMetersPerSecond()
				/ (revolutionsPerSecond * diameter);
		Status status = status(bladeElement);
		double usefulPower = status == Status.SOLVED
				? bladeElement.thrustNewtons()
						* query.axialFreestreamVelocityMetersPerSecond()
				: 0.0;
		double propulsiveEfficiency = status == Status.SOLVED
				? ratio(usefulPower, bladeElement.shaftPowerWatts())
				: 0.0;
		double idealActuatorDiskPower = status == Status.SOLVED
				? bladeElement.thrustNewtons()
						* (query.axialFreestreamVelocityMetersPerSecond()
						+ bladeElement.idealInducedVelocityMetersPerSecond())
				: 0.0;
		return new AxialSample(
				query,
				status,
				bladeElement,
				advanceRatio,
				usefulPower,
				propulsiveEfficiency,
				idealActuatorDiskPower,
				message(status, advanceRatio)
		);
	}

	private static Status status(HoverSample bladeElement) {
		return switch (bladeElement.status()) {
			case BLOCKED_SECTION_POLAR_ENVELOPE -> Status.BLOCKED_SECTION_POLAR_ENVELOPE;
			case BLOCKED_ANNULAR_MOMENTUM_ROOT -> Status.BLOCKED_ANNULAR_MOMENTUM_ROOT;
			case SOLVED -> {
				if (bladeElement.thrustNewtons() <= EPSILON) {
					yield Status.BLOCKED_NON_POSITIVE_THRUST;
				}
				if (bladeElement.shaftPowerWatts() <= EPSILON) {
					yield Status.BLOCKED_NON_POSITIVE_SHAFT_POWER;
				}
				yield Status.SOLVED;
			}
		};
	}

	private static String message(Status status, double advanceRatio) {
		return switch (status) {
			case SOLVED -> advanceRatio <= EPSILON
					? "geometry-bemt-hover-solved"
					: "geometry-bemt-positive-thrust-axial-flight-solved";
			case BLOCKED_SECTION_POLAR_ENVELOPE ->
					"geometry-bemt-section-polar-envelope-blocked";
			case BLOCKED_ANNULAR_MOMENTUM_ROOT ->
					"geometry-bemt-annular-momentum-root-blocked";
			case BLOCKED_NON_POSITIVE_THRUST ->
					"geometry-bemt-windmill-or-negative-thrust-regime-blocked";
			case BLOCKED_NON_POSITIVE_SHAFT_POWER ->
					"geometry-bemt-non-positive-shaft-power-regime-blocked";
		};
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

	public record AxialQuery(
			BladeGeometry geometry,
			double rotorRadiusMeters,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			double angularVelocityRadiansPerSecond,
			double axialFreestreamVelocityMetersPerSecond,
			int annuliPerGeometryInterval,
			Sda1075XfoilSectionPolar.EnvelopePolicy polarEnvelopePolicy,
			RotorHoverBladeElementModel.WakeRotationPolicy wakeRotationPolicy,
			SnelMcCrinkRotationalAugmentation.Policy rotationalAugmentationPolicy
	) {
		public AxialQuery {
			if (geometry == null) {
				throw new IllegalArgumentException("geometry must not be null.");
			}
			if (!Double.isFinite(rotorRadiusMeters) || rotorRadiusMeters <= 0.0) {
				throw new IllegalArgumentException("rotorRadiusMeters must be finite and positive.");
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
			if (!Double.isFinite(angularVelocityRadiansPerSecond)
					|| angularVelocityRadiansPerSecond <= 0.0) {
				throw new IllegalArgumentException(
						"angularVelocityRadiansPerSecond must be finite and positive."
				);
			}
			if (!Double.isFinite(axialFreestreamVelocityMetersPerSecond)
					|| axialFreestreamVelocityMetersPerSecond < 0.0) {
				throw new IllegalArgumentException(
						"axialFreestreamVelocityMetersPerSecond must be finite and non-negative."
				);
			}
			if (annuliPerGeometryInterval <= 0
					|| annuliPerGeometryInterval
							> RotorHoverBladeElementModel.MAX_ANNULI_PER_GEOMETRY_INTERVAL) {
				throw new IllegalArgumentException(
						"annuliPerGeometryInterval must be between 1 and "
								+ RotorHoverBladeElementModel.MAX_ANNULI_PER_GEOMETRY_INTERVAL
								+ "."
				);
			}
			if (polarEnvelopePolicy == null) {
				polarEnvelopePolicy = Sda1075XfoilSectionPolar.EnvelopePolicy
						.BLOCK_OUT_OF_ENVELOPE;
			}
			if (wakeRotationPolicy == null) {
				wakeRotationPolicy = RotorHoverBladeElementModel.WakeRotationPolicy
						.AXIAL_MOMENTUM_ONLY;
			}
			if (rotationalAugmentationPolicy == null) {
				rotationalAugmentationPolicy = SnelMcCrinkRotationalAugmentation.Policy.NONE;
			}
		}

		public static AxialQuery atAdvanceRatio(
				BladeGeometry geometry,
				double rotorRadiusMeters,
				double airDensityKgPerCubicMeter,
				double dynamicViscosityPascalSeconds,
				double angularVelocityRadiansPerSecond,
				double advanceRatio
		) {
			if (!Double.isFinite(advanceRatio) || advanceRatio < 0.0) {
				throw new IllegalArgumentException("advanceRatio must be finite and non-negative.");
			}
			double revolutionsPerSecond = angularVelocityRadiansPerSecond / (2.0 * Math.PI);
			double diameter = 2.0 * rotorRadiusMeters;
			return new AxialQuery(
					geometry,
					rotorRadiusMeters,
					airDensityKgPerCubicMeter,
					dynamicViscosityPascalSeconds,
					angularVelocityRadiansPerSecond,
					advanceRatio * revolutionsPerSecond * diameter,
					RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL,
					Sda1075XfoilSectionPolar.EnvelopePolicy.CLAMP_TO_ENVELOPE,
					RotorHoverBladeElementModel.WakeRotationPolicy.AXIAL_MOMENTUM_ONLY,
					SnelMcCrinkRotationalAugmentation.Policy.NONE
			);
		}
	}

	public record AxialSample(
			AxialQuery query,
			Status status,
			HoverSample bladeElementSample,
			double advanceRatioJ,
			double usefulPropulsivePowerWatts,
			double propulsiveEfficiencyEta,
			double idealActuatorDiskPowerWatts,
			String message
	) {
		public boolean solved() {
			return status == Status.SOLVED;
		}

		public boolean blocked() {
			return !solved();
		}

		public double thrustNewtons() {
			return solved() ? bladeElementSample.thrustNewtons() : 0.0;
		}

		public double shaftPowerWatts() {
			return solved() ? bladeElementSample.shaftPowerWatts() : 0.0;
		}

		public double shaftTorqueNewtonMeters() {
			return solved() ? bladeElementSample.shaftTorqueNewtonMeters() : 0.0;
		}

		public double thrustCoefficientCt() {
			return solved() ? bladeElementSample.thrustCoefficientCt() : 0.0;
		}

		public double powerCoefficientCp() {
			return solved() ? bladeElementSample.powerCoefficientCp() : 0.0;
		}

		public double torqueCoefficientCq() {
			return solved() ? bladeElementSample.torqueCoefficientCq() : 0.0;
		}

		public double idealInducedVelocityMetersPerSecond() {
			return solved() ? bladeElementSample.idealInducedVelocityMetersPerSecond() : 0.0;
		}

		public double diskAxialVelocityMetersPerSecond() {
			return solved()
					? query.axialFreestreamVelocityMetersPerSecond()
							+ idealInducedVelocityMetersPerSecond()
					: 0.0;
		}

		public double diskLoadingNewtonsPerSquareMeter() {
			return solved() ? bladeElementSample.diskLoadingNewtonsPerSquareMeter() : 0.0;
		}

		public double idealInducedPowerWatts() {
			return solved() ? bladeElementSample.idealInducedPowerWatts() : 0.0;
		}

		public double idealMomentumPowerOverShaftPower() {
			return solved() ? ratio(idealActuatorDiskPowerWatts, shaftPowerWatts()) : 0.0;
		}

		public double reynoldsSupportedThrustWeightFraction() {
			return solved() ? bladeElementSample.reynoldsSupportedThrustWeightFraction() : 0.0;
		}

		public double fullySupportedThrustWeightFraction() {
			return solved() ? bladeElementSample.fullySupportedThrustWeightFraction() : 0.0;
		}
	}
}
