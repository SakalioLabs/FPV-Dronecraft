package com.tenicana.dronecraft.sim;

/**
 * Allocation-free APDrone CT/CP/J reference sampler for the rotor-wake runtime.
 *
 * <p>The accepted archive payload is reduced to its seven-point advance-ratio
 * shape. Generic lookup metadata and audit telemetry deliberately stay out of
 * the simulation hot path.</p>
 */
final class ApDroneCtCpJRuntimeWakeReference {
	private static final double EPSILON = 1.0e-9;
	private static final double MOMENTUM_POWER_CLOSURE_TOLERANCE = 1.0e-6;
	private static final double MAX_INFLOW_ANGLE_RADIANS = Math.toRadians(15.0);
	private static final double STATIC_TRANSVERSE_TOLERANCE_METERS_PER_SECOND = 0.35;
	private static final double MAX_TIP_MACH = 0.46;
	private static final double MIN_REYNOLDS_INDEX = 0.52;
	private static final double SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER = 1.225;
	private static final double REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS = 1.837e-5;
	private static final double APDRONE_REFERENCE_RADIUS_METERS = 5.1 * 0.0254 * 0.5;
	private static final double APDRONE_REFERENCE_PITCH_TO_DIAMETER_RATIO = 4.5 / 5.1;
	private static final double APDRONE_REFERENCE_GEOMETRY_TOLERANCE = 1.0e-6;

	/* Raw bits preserve the accepted payload's original materialization order. */
	private static final double[] ADVANCE_RATIO_J = {
			Double.longBitsToDouble(0x0000000000000000L),
			Double.longBitsToDouble(0x3FD4CEC41DD1A21FL),
			Double.longBitsToDouble(0x3FDA027525460AA6L),
			Double.longBitsToDouble(0x3FDF36262CBA732EL),
			Double.longBitsToDouble(0x3FE4CEC41DD1A21FL),
			Double.longBitsToDouble(0x3FE7689CA18BD662L),
			Double.longBitsToDouble(0x3FEA027525460AA6L)
	};
	private static final double[] THRUST_COEFFICIENT_SHAPE = {
			Double.longBitsToDouble(0x3FBEB851EB851EB8L),
			Double.longBitsToDouble(0x3FB8F5C28F5C28F6L),
			Double.longBitsToDouble(0x3FB7DF3B645A1CACL),
			Double.longBitsToDouble(0x3FB6C8B439581062L),
			Double.longBitsToDouble(0x3FB6872B020C49BAL),
			Double.longBitsToDouble(0x3FB5810624DD2F1BL),
			Double.longBitsToDouble(0x3FB47AE147AE147BL)
	};
	private static final double[] POWER_COEFFICIENT_SHAPE = {
			Double.longBitsToDouble(0x3FA47AE147AE147BL),
			Double.longBitsToDouble(0x3FA851EB851EB852L),
			Double.longBitsToDouble(0x3FA9FBE76C8B4395L),
			Double.longBitsToDouble(0x3FABA5E353F7CED9L),
			Double.longBitsToDouble(0x3FABA5E353F7CED9L),
			Double.longBitsToDouble(0x3FAD2F1A9FBE76C8L),
			Double.longBitsToDouble(0x3FAEB851EB851EB8L)
	};

	private ApDroneCtCpJRuntimeWakeReference() {
	}

	static boolean hasEligibleRotorSet(DroneConfig config) {
		if (config == null || config.rotors().size() != 4) {
			return false;
		}
		for (RotorSpec rotor : config.rotors()) {
			if (!isEligibleRotor(rotor)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isEligibleRotor(RotorSpec rotor) {
		return rotor != null
				&& Math.abs(rotor.radiusMeters() - APDRONE_REFERENCE_RADIUS_METERS)
						<= APDRONE_REFERENCE_GEOMETRY_TOLERANCE
				&& Math.abs(rotor.bladePitchToDiameterRatio() - APDRONE_REFERENCE_PITCH_TO_DIAMETER_RATIO)
						<= APDRONE_REFERENCE_GEOMETRY_TOLERANCE
				&& rotor.bladeCount() == 3;
	}

	static RotorGeometry geometry(RotorSpec rotor) {
		double radius = rotor.radiusMeters();
		double diameter = radius * 2.0;
		double diameterToFourthPower = Math.pow(diameter, 4.0);
		double diameterToFifthPower = Math.pow(diameter, 5.0);
		double diskArea = Math.PI * radius * radius;
		double staticThrustCoefficientCt = rotor.thrustCoefficient()
				* Math.pow(2.0 * Math.PI, 2.0)
				/ (SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * Math.pow(diameter, 4.0));
		double staticPowerCoefficientCp = rotor.yawTorquePerThrustMeter()
				* rotor.thrustCoefficient()
				* Math.pow(2.0 * Math.PI, 3.0)
				/ (SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * Math.pow(diameter, 5.0));
		double maxDiskLoading = diskArea > EPSILON
				? rotor.maxThrustNewtons() / diskArea
				: 0.0;
		double reynoldsChordScale = MathUtil.clamp(
				rotor.representativeBladeChordMeters()
						/ (0.0635 * RotorSpec.DEFAULT_REPRESENTATIVE_CHORD_TO_RADIUS_RATIO),
				0.24,
				3.60
		);
		return new RotorGeometry(
				radius,
				diameter,
				diameterToFourthPower,
				diameterToFifthPower,
				diskArea,
				rotor.maxThrustNewtons(),
				maxDiskLoading,
				staticThrustCoefficientCt,
				staticPowerCoefficientCp,
				reynoldsChordScale
		);
	}

	static void sampleInto(
			RotorGeometry geometry,
			Vec3 thrustAxisBody,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			double omegaRadiansPerSecond,
			double airDensityKgPerCubicMeter,
			double speedOfSoundMetersPerSecond,
			double densityViscosityRatio,
			Sample out
	) {
		out.clear();
		if (geometry == null
				|| relativeAirVelocityBodyMetersPerSecond == null
				|| !relativeAirVelocityBodyMetersPerSecond.isFinite()
				|| !Double.isFinite(omegaRadiansPerSecond)
				|| omegaRadiansPerSecond <= 0.0) {
			return;
		}

		double axisX = 0.0;
		double axisY = 1.0;
		double axisZ = 0.0;
		if (thrustAxisBody != null && thrustAxisBody.isFinite()) {
			double axisLengthSquared = thrustAxisBody.x() * thrustAxisBody.x()
					+ thrustAxisBody.y() * thrustAxisBody.y()
					+ thrustAxisBody.z() * thrustAxisBody.z();
			if (axisLengthSquared > EPSILON) {
				double inverseAxisLength = 1.0 / Math.sqrt(axisLengthSquared);
				axisX = thrustAxisBody.x() * inverseAxisLength;
				axisY = thrustAxisBody.y() * inverseAxisLength;
				axisZ = thrustAxisBody.z() * inverseAxisLength;
			}
		}

		double velocityX = relativeAirVelocityBodyMetersPerSecond.x();
		double velocityY = relativeAirVelocityBodyMetersPerSecond.y();
		double velocityZ = relativeAirVelocityBodyMetersPerSecond.z();
		double signedAxialAdvanceSpeed = velocityX * axisX + velocityY * axisY + velocityZ * axisZ;
		if (signedAxialAdvanceSpeed < 0.0) {
			return;
		}

		double rpm = omegaRadiansPerSecond * 60.0 / (2.0 * Math.PI);
		double revolutionsPerSecond = rpm / 60.0;
		double advanceRatioJ = signedAxialAdvanceSpeed
				/ Math.max(EPSILON, revolutionsPerSecond * geometry.diameterMeters());
		if (advanceRatioJ > ADVANCE_RATIO_J[ADVANCE_RATIO_J.length - 1] + EPSILON) {
			return;
		}

		int lowerIndex = -1;
		int upperIndex = -1;
		for (int index = 0; index < ADVANCE_RATIO_J.length; index++) {
			double point = ADVANCE_RATIO_J[index];
			if (point <= advanceRatioJ + EPSILON) {
				lowerIndex = index;
			}
			if (upperIndex < 0 && point >= advanceRatioJ - EPSILON) {
				upperIndex = index;
			}
		}
		if (lowerIndex < 0 || upperIndex < 0) {
			return;
		}

		double lowerAdvanceRatio = ADVANCE_RATIO_J[lowerIndex];
		double upperAdvanceRatio = ADVANCE_RATIO_J[upperIndex];
		double fraction = Math.abs(lowerAdvanceRatio - upperAdvanceRatio) <= EPSILON
				? 0.0
				: (advanceRatioJ - lowerAdvanceRatio) / (upperAdvanceRatio - lowerAdvanceRatio);
		double shapeCt = lerp(
				THRUST_COEFFICIENT_SHAPE[lowerIndex],
				THRUST_COEFFICIENT_SHAPE[upperIndex],
				fraction
		);
		double shapeCp = lerp(
				POWER_COEFFICIENT_SHAPE[lowerIndex],
				POWER_COEFFICIENT_SHAPE[upperIndex],
				fraction
		);
		double thrustCoefficientCt = geometry.staticThrustCoefficientCt() * shapeCt / 0.120;
		double powerCoefficientCp = geometry.staticPowerCoefficientCp() * shapeCp / 0.040;

		double advanceSpeed = advanceRatioJ * revolutionsPerSecond * geometry.diameterMeters();
		double thrust = thrustCoefficientCt
				* airDensityKgPerCubicMeter
				* revolutionsPerSecond
				* revolutionsPerSecond
				* geometry.diameterToFourthPower();
		double shaftPower = powerCoefficientCp
				* airDensityKgPerCubicMeter
				* Math.pow(revolutionsPerSecond, 3.0)
				* geometry.diameterToFifthPower();
		double torqueCoefficientCq = powerCoefficientCp / (2.0 * Math.PI);
		double shaftTorque = torqueCoefficientCq
				* airDensityKgPerCubicMeter
				* revolutionsPerSecond
				* revolutionsPerSecond
				* geometry.diameterToFifthPower();
		double diskLoading = geometry.diskAreaSquareMeters() > EPSILON
				? thrust / geometry.diskAreaSquareMeters()
				: 0.0;
		double nonnegativeAxialSpeed = Math.max(0.0, advanceSpeed);
		double inducedVelocity = axialMomentumInducedVelocity(
				thrust,
				airDensityKgPerCubicMeter,
				geometry.diskAreaSquareMeters(),
				nonnegativeAxialSpeed
		);
		double usefulAxialThrustPower = thrust > EPSILON ? thrust * nonnegativeAxialSpeed : 0.0;
		double idealInducedPower = thrust > EPSILON ? thrust * inducedVelocity : 0.0;
		double idealMomentumPower = thrust > EPSILON
				? usefulAxialThrustPower + idealInducedPower
				: 0.0;
		double actuatorDiskAxialVelocity = nonnegativeAxialSpeed + inducedVelocity;
		double diskMassFlow = airDensityKgPerCubicMeter
				* geometry.diskAreaSquareMeters()
				* actuatorDiskAxialVelocity;
		double farWakeAxialVelocity = nonnegativeAxialSpeed + 2.0 * inducedVelocity;
		double farWakeArea = farWakeAxialVelocity > EPSILON
				? diskMassFlow / (airDensityKgPerCubicMeter * farWakeAxialVelocity)
				: 0.0;
		double farWakeRadius = farWakeArea > EPSILON
				? Math.sqrt(farWakeArea / Math.PI)
				: 0.0;
		double swirlRadius = farWakeRadius * RotorSpec.BLADE_GEOMETRY_REFERENCE_STATION_FRACTION;
		double tangentialWakeVelocity = diskMassFlow > EPSILON && swirlRadius > EPSILON
				? Math.abs(shaftTorque) / (diskMassFlow * swirlRadius)
				: 0.0;
		double swirlKineticPower = wakeSwirlKineticPower(
				diskMassFlow,
				swirlRadius,
				tangentialWakeVelocity,
				farWakeRadius
		);

		double idealMomentumPowerOverShaftPower = ratio(idealMomentumPower, shaftPower);
		double axialMomentumThrust = diskMassFlow * (farWakeAxialVelocity - nonnegativeAxialSpeed);
		if (!Double.isFinite(axialMomentumThrust)) {
			axialMomentumThrust = 0.0;
		}
		double axialMomentumThrustResidualFraction = ratio(axialMomentumThrust - thrust, thrust);
		double axialMomentumPower = 0.5 * diskMassFlow
				* (farWakeAxialVelocity * farWakeAxialVelocity
				- nonnegativeAxialSpeed * nonnegativeAxialSpeed);
		if (!Double.isFinite(axialMomentumPower)) {
			axialMomentumPower = 0.0;
		}
		double axialMomentumPowerResidualFraction = ratio(
				axialMomentumPower - idealMomentumPower,
				idealMomentumPower
		);
		boolean momentumPowerClosureSatisfied = Double.isFinite(idealMomentumPowerOverShaftPower)
				&& idealMomentumPowerOverShaftPower > 0.0
				&& idealMomentumPowerOverShaftPower <= 1.0 + MOMENTUM_POWER_CLOSURE_TOLERANCE
				&& Math.abs(axialMomentumThrustResidualFraction) <= MOMENTUM_POWER_CLOSURE_TOLERANCE
				&& Math.abs(axialMomentumPowerResidualFraction) <= MOMENTUM_POWER_CLOSURE_TOLERANCE;
		double totalWakeKineticPowerOverShaftPower = ratio(
				idealMomentumPower + swirlKineticPower,
				shaftPower
		);
		boolean wakePowerClosureSatisfied = Double.isFinite(totalWakeKineticPowerOverShaftPower)
				&& totalWakeKineticPowerOverShaftPower > 0.0
				&& totalWakeKineticPowerOverShaftPower <= 1.0 + MOMENTUM_POWER_CLOSURE_TOLERANCE;

		double transverseX = velocityX - axisX * signedAxialAdvanceSpeed;
		double transverseY = velocityY - axisY * signedAxialAdvanceSpeed;
		double transverseZ = velocityZ - axisZ * signedAxialAdvanceSpeed;
		double transverseAirSpeed = Math.sqrt(
				transverseX * transverseX
						+ transverseY * transverseY
						+ transverseZ * transverseZ
		);
		double inflowAngle = inflowAngleRadians(signedAxialAdvanceSpeed, transverseAirSpeed);
		boolean inflowEnvelopeSatisfied = (transverseAirSpeed <= STATIC_TRANSVERSE_TOLERANCE_METERS_PER_SECOND
				&& inflowAngle <= Math.PI * 0.5)
				|| inflowAngle <= MAX_INFLOW_ANGLE_RADIANS;

		double dimensionalOmega = revolutionsPerSecond * 2.0 * Math.PI;
		double rotationalTipSpeed = Math.abs(dimensionalOmega) * geometry.radiusMeters();
		double axialSpeed = Math.abs(signedAxialAdvanceSpeed);
		double helicalTipSpeed = Math.sqrt(
				rotationalTipSpeed * rotationalTipSpeed
						+ 0.25 * transverseAirSpeed * transverseAirSpeed
						+ 0.16 * axialSpeed * axialSpeed
		);
		double tipMach = ratio(helicalTipSpeed, speedOfSoundMetersPerSecond);
		double representativeBladeStationSpeed = Math.sqrt(
				0.75 * rotationalTipSpeed * 0.75 * rotationalTipSpeed
						+ 0.25 * transverseAirSpeed * transverseAirSpeed
						+ 0.16 * axialSpeed * axialSpeed
		);
		double reynoldsIndex = densityViscosityRatio
				* geometry.reynoldsChordScale()
				* MathUtil.clamp(representativeBladeStationSpeed / 34.0, 0.0, 2.8);
		boolean operatingPointEnvelopeSatisfied = MAX_TIP_MACH - tipMach >= -EPSILON
				&& reynoldsIndex - MIN_REYNOLDS_INDEX >= -EPSILON;

		if (!momentumPowerClosureSatisfied
				|| !wakePowerClosureSatisfied
				|| !inflowEnvelopeSatisfied
				|| !operatingPointEnvelopeSatisfied) {
			return;
		}

		double diskLoadingStrength = 0.0;
		if (Double.isFinite(diskLoading)
				&& diskLoading > 0.0
				&& geometry.maxDiskLoadingNewtonsPerSquareMeter() > 1.0e-12) {
			diskLoadingStrength = MathUtil.clamp(
					diskLoading / geometry.maxDiskLoadingNewtonsPerSquareMeter(),
					0.0,
					1.0
			);
		} else if (Double.isFinite(thrust)
				&& thrust > 0.0
				&& geometry.maxThrustNewtons() > 1.0e-12) {
			diskLoadingStrength = MathUtil.clamp(thrust / geometry.maxThrustNewtons(), 0.0, 1.0);
		}
		out.set(
				thrust,
				diskLoading,
				diskLoadingStrength,
				inducedVelocity,
				farWakeAxialVelocity,
				farWakeRadius,
				tangentialWakeVelocity
		);
	}

	static double runtimeAirDensityKgPerCubicMeter(double airDensityRatio) {
		double densityScale = Math.max(
				0.20,
				Double.isFinite(airDensityRatio) ? airDensityRatio : 1.0
		);
		return SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER * densityScale;
	}

	static double runtimeDensityViscosityRatio(
			double airDensityKgPerCubicMeter,
			double dynamicViscosityRatio
	) {
		double dynamicViscosity = REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS
				* dynamicViscosityRatio;
		return MathUtil.clamp(
				ratio(airDensityKgPerCubicMeter, SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER)
						/ Math.max(EPSILON, ratio(
								dynamicViscosity,
								REFERENCE_AIR_DYNAMIC_VISCOSITY_PASCAL_SECONDS
						)),
				0.20,
				1.90
		);
	}

	private static double axialMomentumInducedVelocity(
			double thrustNewtons,
			double airDensityKgPerCubicMeter,
			double diskAreaSquareMeters,
			double axialAdvanceSpeedMetersPerSecond
	) {
		if (thrustNewtons <= EPSILON
				|| airDensityKgPerCubicMeter <= EPSILON
				|| diskAreaSquareMeters <= EPSILON) {
			return 0.0;
		}
		double axialAdvanceSpeed = Double.isFinite(axialAdvanceSpeedMetersPerSecond)
				? Math.max(0.0, axialAdvanceSpeedMetersPerSecond)
				: 0.0;
		double diskTerm = 2.0 * thrustNewtons
				/ (airDensityKgPerCubicMeter * diskAreaSquareMeters);
		return 0.5 * (Math.sqrt(axialAdvanceSpeed * axialAdvanceSpeed + diskTerm) - axialAdvanceSpeed);
	}

	private static double wakeSwirlKineticPower(
			double diskMassFlowKilogramsPerSecond,
			double angularMomentumSwirlRadiusMeters,
			double wakeTangentialVelocityMetersPerSecond,
			double wakeRadiusMeters
	) {
		if (diskMassFlowKilogramsPerSecond <= EPSILON
				|| angularMomentumSwirlRadiusMeters <= EPSILON
				|| wakeTangentialVelocityMetersPerSecond <= EPSILON
				|| wakeRadiusMeters <= EPSILON) {
			return 0.0;
		}
		double specificAngularMomentum = angularMomentumSwirlRadiusMeters
				* wakeTangentialVelocityMetersPerSecond;
		return diskMassFlowKilogramsPerSecond
				* specificAngularMomentum
				* specificAngularMomentum
				/ (wakeRadiusMeters * wakeRadiusMeters);
	}

	private static double inflowAngleRadians(double axialVelocity, double transverseAirSpeed) {
		double axial = Double.isFinite(axialVelocity) ? axialVelocity : 0.0;
		double transverse = Double.isFinite(transverseAirSpeed) ? Math.max(0.0, transverseAirSpeed) : 0.0;
		if (transverse <= EPSILON && axial >= 0.0) {
			return 0.0;
		}
		if (transverse <= EPSILON) {
			return Math.PI;
		}
		return Math.atan2(transverse, axial);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator)
				|| !Double.isFinite(denominator)
				|| Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private static double lerp(double from, double to, double amount) {
		return from + (to - from) * amount;
	}

	record RotorGeometry(
			double radiusMeters,
			double diameterMeters,
			double diameterToFourthPower,
			double diameterToFifthPower,
			double diskAreaSquareMeters,
			double maxThrustNewtons,
			double maxDiskLoadingNewtonsPerSquareMeter,
			double staticThrustCoefficientCt,
			double staticPowerCoefficientCp,
			double reynoldsChordScale
	) {
	}

	static final class Sample {
		private boolean applied;
		private double thrustNewtons;
		private double diskLoadingNewtonsPerSquareMeter;
		private double diskLoadingStrength;
		private double idealInducedVelocityMetersPerSecond;
		private double farWakeAxialVelocityMetersPerSecond;
		private double farWakeEquivalentRadiusMeters;
		private double wakeTangentialVelocityMetersPerSecond;

		private void clear() {
			applied = false;
			thrustNewtons = 0.0;
			diskLoadingNewtonsPerSquareMeter = 0.0;
			diskLoadingStrength = 0.0;
			idealInducedVelocityMetersPerSecond = 0.0;
			farWakeAxialVelocityMetersPerSecond = 0.0;
			farWakeEquivalentRadiusMeters = 0.0;
			wakeTangentialVelocityMetersPerSecond = 0.0;
		}

		private void set(
				double thrustNewtons,
				double diskLoadingNewtonsPerSquareMeter,
				double diskLoadingStrength,
				double idealInducedVelocityMetersPerSecond,
				double farWakeAxialVelocityMetersPerSecond,
				double farWakeEquivalentRadiusMeters,
				double wakeTangentialVelocityMetersPerSecond
		) {
			this.thrustNewtons = thrustNewtons;
			this.diskLoadingNewtonsPerSquareMeter = diskLoadingNewtonsPerSquareMeter;
			this.diskLoadingStrength = diskLoadingStrength;
			this.idealInducedVelocityMetersPerSecond = idealInducedVelocityMetersPerSecond;
			this.farWakeAxialVelocityMetersPerSecond = farWakeAxialVelocityMetersPerSecond;
			this.farWakeEquivalentRadiusMeters = farWakeEquivalentRadiusMeters;
			this.wakeTangentialVelocityMetersPerSecond = wakeTangentialVelocityMetersPerSecond;
			this.applied = true;
		}

		boolean applied() {
			return applied;
		}

		double thrustNewtons() {
			return thrustNewtons;
		}

		double diskLoadingNewtonsPerSquareMeter() {
			return diskLoadingNewtonsPerSquareMeter;
		}

		double diskLoadingStrength() {
			return diskLoadingStrength;
		}

		double idealInducedVelocityMetersPerSecond() {
			return idealInducedVelocityMetersPerSecond;
		}

		double farWakeAxialVelocityMetersPerSecond() {
			return farWakeAxialVelocityMetersPerSecond;
		}

		double farWakeEquivalentRadiusMeters() {
			return farWakeEquivalentRadiusMeters;
		}

		double wakeTangentialVelocityMetersPerSecond() {
			return wakeTangentialVelocityMetersPerSecond;
		}
	}
}
