package com.tenicana.dronecraft.sim;

import java.util.List;

public final class RotorObliqueMomentumInflowModel {
	private static final Vec3 DEFAULT_ROTOR_AXIS_BODY = new Vec3(0.0, 1.0, 0.0);
	private static final double FLOW_EPSILON = 1.0e-9;
	private static final double DIMENSIONLESS_RESIDUAL_TOLERANCE = 1.0e-13;
	private static final double VRS_TRANSVERSE_CONVECTION_FACTOR = 0.65;
	private static final double MINIMUM_VRS_WAKE_CONVECTION_RATIO = 0.74;
	private static final int MAX_SOLVER_ITERATIONS = 80;

	private RotorObliqueMomentumInflowModel() {
	}

	public enum Status {
		ZERO_THRUST,
		SOLVED_HOVER,
		SOLVED_NORMAL_WORKING,
		SOLVED_WAKE_CONVECTED_DESCENT,
		BLOCKED_DESCENT_REQUIRES_VRS_MODEL,
		BLOCKED_NO_CONVERGENCE
	}

	public static RotorObliqueMomentumInflowSample solve(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double thrustNewtons,
			double airDensityKgPerCubicMeter
	) {
		validateInputs(rotor, relativeAirVelocityBodyMetersPerSecond, thrustNewtons,
				airDensityKgPerCubicMeter);
		Vec3 rotorAxisBody = rotorAxisBody(rotor);
		double axialVelocity = relativeAirVelocityBodyMetersPerSecond.dot(rotorAxisBody);
		Vec3 transverseVelocityBody = relativeAirVelocityBodyMetersPerSecond.subtract(
				rotorAxisBody.multiply(axialVelocity)
		);
		double transverseSpeed = transverseVelocityBody.length();
		double diskArea = Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
		double diskLoading = thrustNewtons / diskArea;
		if (thrustNewtons <= 0.0) {
			return sample(
					rotor,
					rotorAxisBody,
					relativeAirVelocityBodyMetersPerSecond,
					transverseVelocityBody,
					Status.ZERO_THRUST,
					airDensityKgPerCubicMeter,
					diskArea,
					diskLoading,
					thrustNewtons,
					axialVelocity,
					transverseSpeed,
					0.0,
					0.0,
					0
			);
		}

		double idealHoverInducedVelocity = Math.sqrt(
				thrustNewtons / (2.0 * airDensityKgPerCubicMeter * diskArea)
		);
		double transverseOverHoverInflow = transverseSpeed / idealHoverInducedVelocity;
		double axialOverHoverInflow = axialVelocity / idealHoverInducedVelocity;
		if (transverseSpeed <= FLOW_EPSILON && Math.abs(axialVelocity) <= FLOW_EPSILON) {
			return sample(
					rotor,
					rotorAxisBody,
					relativeAirVelocityBodyMetersPerSecond,
					transverseVelocityBody,
					Status.SOLVED_HOVER,
					airDensityKgPerCubicMeter,
					diskArea,
					diskLoading,
					thrustNewtons,
					axialVelocity,
					transverseSpeed,
					idealHoverInducedVelocity,
					idealHoverInducedVelocity,
					0
			);
		}

		// NASA/TP-2005-213477: lambda * hypot(mu_x, mu_z + lambda) = 1.
		double lowerLambda = Math.max(0.0, -axialOverHoverInflow);
		double lowerResidual = dimensionlessResidual(
				lowerLambda,
				transverseOverHoverInflow,
				axialOverHoverInflow
		);
		if (lowerResidual > DIMENSIONLESS_RESIDUAL_TOLERANCE) {
			return sample(
					rotor,
					rotorAxisBody,
					relativeAirVelocityBodyMetersPerSecond,
					transverseVelocityBody,
					Status.BLOCKED_DESCENT_REQUIRES_VRS_MODEL,
					airDensityKgPerCubicMeter,
					diskArea,
					diskLoading,
					thrustNewtons,
					axialVelocity,
					transverseSpeed,
					idealHoverInducedVelocity,
					0.0,
					0
			);
		}
		double upperLambda = Math.max(1.0, lowerLambda + 1.0);
		double upperResidual = dimensionlessResidual(
				upperLambda,
				transverseOverHoverInflow,
				axialOverHoverInflow
		);
		while (upperResidual < 0.0 && upperLambda < 64.0) {
			upperLambda *= 2.0;
			upperResidual = dimensionlessResidual(
					upperLambda,
					transverseOverHoverInflow,
					axialOverHoverInflow
			);
		}
		if (upperResidual < 0.0) {
			return sample(
					rotor,
					rotorAxisBody,
					relativeAirVelocityBodyMetersPerSecond,
					transverseVelocityBody,
					Status.BLOCKED_NO_CONVERGENCE,
					airDensityKgPerCubicMeter,
					diskArea,
					diskLoading,
					thrustNewtons,
					axialVelocity,
					transverseSpeed,
					idealHoverInducedVelocity,
					0.0,
					0
			);
		}
		double lambda = 0.5;
		boolean converged = false;
		int iterations = 0;
		for (int iteration = 1; iteration <= MAX_SOLVER_ITERATIONS; iteration++) {
			iterations = iteration;
			lambda = 0.5 * (lowerLambda + upperLambda);
			double residual = dimensionlessResidual(
					lambda,
					transverseOverHoverInflow,
					axialOverHoverInflow
			);
			if (Math.abs(residual) <= DIMENSIONLESS_RESIDUAL_TOLERANCE
					|| upperLambda - lowerLambda <= DIMENSIONLESS_RESIDUAL_TOLERANCE) {
				converged = true;
				break;
			}
			if (residual > 0.0) {
				upperLambda = lambda;
			} else {
				lowerLambda = lambda;
			}
		}
		if (!converged) {
			return sample(
					rotor,
					rotorAxisBody,
					relativeAirVelocityBodyMetersPerSecond,
					transverseVelocityBody,
					Status.BLOCKED_NO_CONVERGENCE,
					airDensityKgPerCubicMeter,
					diskArea,
					diskLoading,
					thrustNewtons,
					axialVelocity,
					transverseSpeed,
					idealHoverInducedVelocity,
					0.0,
					iterations
			);
		}
		Status solvedStatus = Status.SOLVED_NORMAL_WORKING;
		if (axialVelocity < -FLOW_EPSILON) {
			// Newman wake-convection boundary summarized by NASA/TP-2005-213477.
			double wakeConvectionRatio = Math.hypot(
					VRS_TRANSVERSE_CONVECTION_FACTOR * transverseOverHoverInflow,
					axialOverHoverInflow + lambda
			);
			if (wakeConvectionRatio < MINIMUM_VRS_WAKE_CONVECTION_RATIO) {
				return sample(
						rotor,
						rotorAxisBody,
						relativeAirVelocityBodyMetersPerSecond,
						transverseVelocityBody,
						Status.BLOCKED_DESCENT_REQUIRES_VRS_MODEL,
						airDensityKgPerCubicMeter,
						diskArea,
						diskLoading,
						thrustNewtons,
						axialVelocity,
						transverseSpeed,
						idealHoverInducedVelocity,
						0.0,
						iterations
				);
			}
			solvedStatus = Status.SOLVED_WAKE_CONVECTED_DESCENT;
		}
		return sample(
				rotor,
				rotorAxisBody,
				relativeAirVelocityBodyMetersPerSecond,
				transverseVelocityBody,
				solvedStatus,
				airDensityKgPerCubicMeter,
				diskArea,
				diskLoading,
				thrustNewtons,
				axialVelocity,
				transverseSpeed,
				idealHoverInducedVelocity,
				lambda * idealHoverInducedVelocity,
				iterations
		);
	}

	public static ConfigurationRotorObliqueMomentumInflowSample aggregate(
			List<RotorObliqueMomentumInflowSample> rotorSamples
	) {
		List<RotorObliqueMomentumInflowSample> requestedSamples = rotorSamples == null
				? List.of()
				: rotorSamples;
		for (RotorObliqueMomentumInflowSample sample : requestedSamples) {
			if (sample == null) {
				throw new IllegalArgumentException("rotorSamples must not contain null entries.");
			}
		}
		List<RotorObliqueMomentumInflowSample> samples = List.copyOf(requestedSamples);
		int solvedRotorCount = 0;
		int zeroThrustRotorCount = 0;
		int blockedRotorCount = 0;
		double requestedThrust = 0.0;
		double solvedThrust = 0.0;
		double normalDiskMassFlow = 0.0;
		double effectiveMomentumMassFlow = 0.0;
		double inducedPower = 0.0;
		double momentumPower = 0.0;
		double maximumThrustClosureResidual = 0.0;
		for (RotorObliqueMomentumInflowSample sample : samples) {
			requestedThrust += sample.thrustNewtons();
			if (sample.solved()) {
				solvedRotorCount++;
				solvedThrust += sample.thrustNewtons();
				normalDiskMassFlow += sample.normalDiskMassFlowKilogramsPerSecond();
				effectiveMomentumMassFlow += sample.effectiveMomentumMassFlowKilogramsPerSecond();
				inducedPower += sample.idealInducedPowerWatts();
				momentumPower += sample.idealMomentumPowerWatts();
				maximumThrustClosureResidual = Math.max(
						maximumThrustClosureResidual,
						Math.abs(sample.thrustClosureResidualNewtons())
				);
			} else if (sample.status() == Status.ZERO_THRUST) {
				zeroThrustRotorCount++;
			} else {
				blockedRotorCount++;
			}
		}
		return new ConfigurationRotorObliqueMomentumInflowSample(
				samples,
				solvedRotorCount,
				zeroThrustRotorCount,
				blockedRotorCount,
				requestedThrust,
				solvedThrust,
				normalDiskMassFlow,
				effectiveMomentumMassFlow,
				inducedPower,
				momentumPower,
				maximumThrustClosureResidual
		);
	}

	private static RotorObliqueMomentumInflowSample sample(
			RotorSpec rotor,
			Vec3 rotorAxisBody,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 transverseVelocityBodyMetersPerSecond,
			Status status,
			double airDensityKgPerCubicMeter,
			double diskAreaSquareMeters,
			double diskLoadingNewtonsPerSquareMeter,
			double thrustNewtons,
			double axialVelocityMetersPerSecond,
			double transverseSpeedMetersPerSecond,
			double idealHoverInducedVelocityMetersPerSecond,
			double inducedVelocityMetersPerSecond,
			int solverIterations
	) {
		boolean solved = status == Status.SOLVED_HOVER
				|| status == Status.SOLVED_NORMAL_WORKING
				|| status == Status.SOLVED_WAKE_CONVECTED_DESCENT;
		double diskAxialVelocity = solved
				? axialVelocityMetersPerSecond + inducedVelocityMetersPerSecond
				: 0.0;
		double resultantDiskVelocity = solved
				? Math.hypot(transverseSpeedMetersPerSecond, diskAxialVelocity)
				: 0.0;
		double normalDiskMassFlow = solved
				? airDensityKgPerCubicMeter * diskAreaSquareMeters * Math.max(0.0, diskAxialVelocity)
				: 0.0;
		double effectiveMomentumMassFlow = solved
				? airDensityKgPerCubicMeter * diskAreaSquareMeters * resultantDiskVelocity
				: 0.0;
		double momentumThrust = solved
				? 2.0 * effectiveMomentumMassFlow * inducedVelocityMetersPerSecond
				: 0.0;
		double thrustResidual = solved ? momentumThrust - thrustNewtons : 0.0;
		double thrustResidualFraction = solved && thrustNewtons > FLOW_EPSILON
				? thrustResidual / thrustNewtons
				: 0.0;
		double farWakeAxialVelocity = solved
				? axialVelocityMetersPerSecond + 2.0 * inducedVelocityMetersPerSecond
				: 0.0;
		double farWakeResultantVelocity = solved
				? Math.hypot(transverseSpeedMetersPerSecond, farWakeAxialVelocity)
				: 0.0;
		double idealInducedPower = solved ? thrustNewtons * inducedVelocityMetersPerSecond : 0.0;
		double usefulAxialPower = solved ? thrustNewtons * axialVelocityMetersPerSecond : 0.0;
		double idealMomentumPower = solved ? thrustNewtons * diskAxialVelocity : 0.0;
		return new RotorObliqueMomentumInflowSample(
				rotor,
				rotorAxisBody,
				relativeAirVelocityBodyMetersPerSecond,
				transverseVelocityBodyMetersPerSecond,
				status,
				airDensityKgPerCubicMeter,
				diskAreaSquareMeters,
				diskLoadingNewtonsPerSquareMeter,
				thrustNewtons,
				axialVelocityMetersPerSecond,
				transverseSpeedMetersPerSecond,
				idealHoverInducedVelocityMetersPerSecond,
				idealHoverInducedVelocityMetersPerSecond <= FLOW_EPSILON
						? 0.0
						: axialVelocityMetersPerSecond / idealHoverInducedVelocityMetersPerSecond,
				idealHoverInducedVelocityMetersPerSecond <= FLOW_EPSILON
						? 0.0
						: transverseSpeedMetersPerSecond / idealHoverInducedVelocityMetersPerSecond,
				inducedVelocityMetersPerSecond,
				diskAxialVelocity,
				resultantDiskVelocity,
				normalDiskMassFlow,
				effectiveMomentumMassFlow,
				momentumThrust,
				thrustResidual,
				thrustResidualFraction,
				farWakeAxialVelocity,
				farWakeResultantVelocity,
				idealInducedPower,
				usefulAxialPower,
				idealMomentumPower,
				solverIterations
		);
	}

	private static double dimensionlessResidual(
			double inducedOverHoverInflow,
			double transverseOverHoverInflow,
			double axialOverHoverInflow
	) {
		return inducedOverHoverInflow * Math.hypot(
				transverseOverHoverInflow,
				axialOverHoverInflow + inducedOverHoverInflow
		) - 1.0;
	}

	public record RotorObliqueMomentumInflowSample(
			RotorSpec rotor,
			Vec3 rotorAxisBody,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			Vec3 transverseAirVelocityBodyMetersPerSecond,
			Status status,
			double airDensityKgPerCubicMeter,
			double diskAreaSquareMeters,
			double diskLoadingNewtonsPerSquareMeter,
			double thrustNewtons,
			double axialVelocityMetersPerSecond,
			double transverseSpeedMetersPerSecond,
			double idealHoverInducedVelocityMetersPerSecond,
			double axialOverHoverInflow,
			double transverseOverHoverInflow,
			double inducedVelocityMetersPerSecond,
			double diskAxialVelocityMetersPerSecond,
			double resultantDiskVelocityMetersPerSecond,
			double normalDiskMassFlowKilogramsPerSecond,
			double effectiveMomentumMassFlowKilogramsPerSecond,
			double momentumTheoryThrustNewtons,
			double thrustClosureResidualNewtons,
			double thrustClosureResidualFraction,
			double farWakeAxialVelocityMetersPerSecond,
			double farWakeResultantVelocityMetersPerSecond,
			double idealInducedPowerWatts,
			double usefulAxialPowerWatts,
			double idealMomentumPowerWatts,
			int solverIterations
	) {
		public boolean solved() {
			return status == Status.SOLVED_HOVER
					|| status == Status.SOLVED_NORMAL_WORKING
					|| status == Status.SOLVED_WAKE_CONVECTED_DESCENT;
		}

		public boolean blocked() {
			return status == Status.BLOCKED_DESCENT_REQUIRES_VRS_MODEL
					|| status == Status.BLOCKED_NO_CONVERGENCE;
		}
	}

	public record ConfigurationRotorObliqueMomentumInflowSample(
			List<RotorObliqueMomentumInflowSample> rotorSamples,
			int solvedRotorCount,
			int zeroThrustRotorCount,
			int blockedRotorCount,
			double requestedThrustNewtons,
			double solvedThrustNewtons,
			double normalDiskMassFlowKilogramsPerSecond,
			double effectiveMomentumMassFlowKilogramsPerSecond,
			double idealInducedPowerWatts,
			double idealMomentumPowerWatts,
			double maximumThrustClosureResidualNewtons
	) {
		public ConfigurationRotorObliqueMomentumInflowSample {
			rotorSamples = List.copyOf(rotorSamples == null ? List.of() : rotorSamples);
		}

		public int rotorCount() {
			return rotorSamples.size();
		}
	}

	private static Vec3 rotorAxisBody(RotorSpec rotor) {
		Vec3 axis = rotor.thrustAxisBody();
		if (axis == null || !axis.isFinite() || axis.lengthSquared() <= 1.0e-9 || axis.y() <= 0.0) {
			return DEFAULT_ROTOR_AXIS_BODY;
		}
		return axis.normalized();
	}

	private static void validateInputs(
			RotorSpec rotor,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double thrustNewtons,
			double airDensityKgPerCubicMeter
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		if (relativeAirVelocityBodyMetersPerSecond == null
				|| !relativeAirVelocityBodyMetersPerSecond.isFinite()) {
			throw new IllegalArgumentException("relativeAirVelocityBodyMetersPerSecond must be finite.");
		}
		if (!Double.isFinite(thrustNewtons) || thrustNewtons < 0.0) {
			throw new IllegalArgumentException("thrustNewtons must be finite and nonnegative.");
		}
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
	}
}
