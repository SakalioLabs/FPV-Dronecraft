package com.tenicana.dronecraft.sim;

public final class RotorDynamicsCalibration {
	public static final String SOURCE_ID = "Rotor-Dynamics-Inertia-Inflow-Coning-Packet";
	public static final String CAVEAT =
			"Prop-mass, dynamic-inflow, coning, and arm-flex rows are scale anchors; runtime values reuse DronePhysics rotor dynamics helpers.";
	public static final int PACKET_METRIC_ROW_COUNT = 132;
	public static final int ROTOR_INERTIA_ROW_COUNT = 31;
	public static final int ROTOR_INFLOW_ROW_COUNT = 7;
	public static final int ARM_FLEX_CONING_ROW_COUNT = 94;
	public static final int PHYSICAL_PROP_REFERENCE_COUNT = 8;
	public static final int CURRENT_PRESET_INERTIA_ROW_COUNT = 6;
	public static final int CURRENT_VS_PHYSICAL_PROP_ROW_COUNT = 12;
	public static final int OPEN_SOURCE_ROTOR_INERTIA_REFERENCE_COUNT = 4;
	public static final int PAPER_REPORTED_ROTOR_INERTIA_REFERENCE_COUNT = 1;
	public static final int CURRENT_PRESET_INFLOW_ROW_COUNT = 6;
	public static final int CURRENT_PRESET_ARM_FLEX_CONING_ROW_COUNT = 18;
	public static final int BEAM_THEORY_ARM_SENSITIVITY_ROW_COUNT = 60;
	public static final int MULTICOPTER_CONING_MEASUREMENT_ROW_COUNT = 8;
	public static final double ROTORS_PX4_TIME_CONSTANT_UP_SECONDS = 0.0125;
	public static final double ROTORS_PX4_TIME_CONSTANT_DOWN_SECONDS = 0.0250;
	public static final double ZJU_REPORTED_ROTOR_INERTIA_KG_METERS_SQUARED = 0.00010556;
	public static final double ZJU_REPORTED_ROTOR_RADIUS_METERS = 0.0889;
	public static final double ZJU_REPORTED_ROTOR_MASS_GRAMS = 7.5;
	public static final double DJI_PHANTOM_8500_RPM_CONING_DEGREES = 1.95;
	public static final double DJI_PHANTOM_8500_RPM_DEFLECTION_MILLIMETERS = 4.08;
	public static final double TMOTOR_15X5_5000_RPM_CONING_DEGREES = 0.89;
	public static final double TMOTOR_15X5_5000_RPM_DEFLECTION_MILLIMETERS = 1.87;
	private static final double BODY_RATE_REFERENCE_DEGREES_PER_SECOND = 720.0;
	private static final double FAST_CROSSFLOW_METERS_PER_SECOND = 18.0;
	private static final double FAST_DESCENT_METERS_PER_SECOND = 10.0;
	private static final double MAX_SNAP_FORCE_SLEW_NORMALIZED = 45.0;
	private static final double MAX_SNAP_TORQUE_SLEW_NORMALIZED = 28.0;
	private static final double INCHES_PER_METER = 39.37007874015748;

	private static final PhysicalPropReference[] PHYSICAL_REFERENCES = new PhysicalPropReference[] {
			new PhysicalPropReference("HQProp Durable 5x4.3x3 V1S", 5.0, 4.3, 3, 3.81),
			new PhysicalPropReference("HQProp Durable 5x4.5x3 V1S", 5.0, 4.5, 3, 4.19),
			new PhysicalPropReference("HQProp Durable 5x5x3 V1S", 5.0, 5.0, 3, 4.48),
			new PhysicalPropReference("Gemfan 51466 MCK V2", 5.1889763779527565, 3.6, 3, 4.2),
			new PhysicalPropReference("HQProp Durable T3x3x3", 3.0, 3.0, 3, 1.48),
			new PhysicalPropReference("Gemfan D90 ducted 3-blade", 3.5433070866141736, 3.0, 3, 2.3),
			new PhysicalPropReference("Gemfan 1045 glass-fiber nylon 3-blade", 10.0, 4.5, 3, 17.8),
			new PhysicalPropReference("Gemfan 1050 Cinelifter glass-fiber nylon 3-blade", 10.0, 5.0, 3, 16.8)
	};

	private RotorDynamicsCalibration() {
	}

	public record PhysicalPropReference(
			String propellerId,
			double diameterInches,
			double pitchInches,
			int bladeCount,
			double massGrams
	) {
		public double pitchToDiameterRatio() {
			return pitchInches / Math.max(1.0e-9, diameterInches);
		}

		public double radiusMeters() {
			return diameterInches * 0.0254 * 0.5;
		}

		public double hubBiasedInertiaKgMetersSquared() {
			return RotorSpec.estimatedPropInertiaKgMetersSquared(
					radiusMeters(),
					massGrams,
					RotorSpec.HUB_BIASED_PROP_INERTIA_COEFFICIENT
			);
		}

		public double uniformBladeInertiaKgMetersSquared() {
			return RotorSpec.estimatedUniformBladePropInertiaKgMetersSquared(radiusMeters(), massGrams);
		}

		public double tipBiasedInertiaKgMetersSquared() {
			return RotorSpec.estimatedPropInertiaKgMetersSquared(
					radiusMeters(),
					massGrams,
					RotorSpec.TIP_BIASED_PROP_INERTIA_COEFFICIENT
			);
		}
	}

	public record RotorInertiaAudit(
			PhysicalPropReference nearestPhysicalReference,
			int rotorCount,
			double configuredDiameterInches,
			double configuredPitchToDiameterRatio,
			int configuredBladeCount,
			double configuredRotorInertiaKgMetersSquared,
			double configuredOverReferenceHubBiasedInertia,
			double configuredOverReferenceUniformBladeInertia,
			double configuredOverReferenceTipBiasedInertia,
			double configuredEquivalentUniformBladeMassGrams,
			double configuredEquivalentUniformBladeMassOverReferenceMass,
			double zjuReportedRotorInertiaKgMetersSquared,
			double configuredOverZjuReportedInertia,
			double hoverRpm,
			double maxRpm,
			double hoverAngularMomentumNewtonMeterSeconds,
			double maxAngularMomentumNewtonMeterSeconds,
			double bodyRateReferenceDegreesPerSecond,
			double hoverGyroTorquePerRotorNewtonMeters,
			double maxGyroTorquePerRotorNewtonMeters,
			double maxGyroTorqueAllRotorsAbsoluteNewtonMeters,
			double motorTauSpinupReactionTorqueNewtonMeters,
			double fiftyMillisecondSpinupReactionTorqueNewtonMeters
	) {
	}

	public record DynamicInflowAudit(
			double configuredInflowTimeConstantSeconds,
			double configuredInflowLagCoefficient,
			double rotorSPx4ReferenceTauUpSeconds,
			double rotorSPx4ReferenceTauDownSeconds,
			double configuredTauOverReferenceUp,
			double configuredTauOverReferenceDown,
			double rotorRadiusMeters,
			double hoverInducedVelocityMetersPerSecond,
			double maxInducedVelocityMetersPerSecond,
			double wakeTransitOneRadiusHoverSeconds,
			double wakeTransitTwoRadiusHoverSeconds,
			double wakeTransitOneRadiusMaxSeconds,
			double configuredTauOverOneRadiusHoverTransit,
			double configuredTauOverTwoRadiusHoverTransit,
			double configuredTauOverOneRadiusMaxTransit,
			double runtimeHoverDynamicTauSeconds,
			double runtimeHighThrustDynamicTauSeconds,
			double runtimeFastCrossflowDynamicTauSeconds,
			double runtimeFastDescentDynamicTauSeconds
	) {
	}

	public record ConingAudit(
			double hoverTargetIntensity,
			double hoverConingAngleDegrees,
			double maxTargetIntensity,
			double maxConingAngleDegrees,
			double maxConingThrustScale,
			double maxConingLoadFactor,
			double maxConingVibration,
			double maxConingNaturalFrequencyHertz,
			double maxConingDampingRatio,
			double djiPhantom8500RpmConingDegrees,
			double djiPhantom8500RpmDeflectionMillimeters,
			double tmotor15x5_5000RpmConingDegrees,
			double tmotor15x5_5000RpmDeflectionMillimeters,
			double maxConingAngleOverDjiPhantomReference,
			double maxConingAngleOverTmotorReference
	) {
	}

	public record ArmFlexAudit(
			double hoverTargetIntensity,
			double maxSteadyTargetIntensity,
			double maxSnapTargetIntensity,
			double fullFlexVerticalDeflectionMillimeters,
			double maxSteadyVerticalDeflectionMillimeters,
			double maxSnapVerticalDeflectionMillimeters,
			double fullFlexTiltDegrees,
			double maxSteadyTiltDegrees,
			double maxSnapTiltDegrees,
			double maxSpinNaturalFrequencyHertz,
			double maxSpinDampingRatio,
			double maxSnapVibration,
			BeamSensitivityAudit representativeBeamSensitivity
	) {
	}

	public record BeamSensitivityAudit(
			String geometryId,
			double youngsModulusGpa,
			double armLengthMeters,
			double loadForceNewtons,
			double secondMomentAreaMeters4,
			double cantileverTipDeflectionMillimeters,
			double cantileverTipStiffnessNewtonsPerMeter,
			double cantileverFirstBendingFrequencyHertz,
			double beamDeflectionOverRuntimeMaxSnap,
			double beamFrequencyOverRuntimeMaxSpin
	) {
	}

	public record RotorDynamicsAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int rotorInertiaRowCount,
			int rotorInflowRowCount,
			int armFlexConingRowCount,
			int physicalPropReferenceCount,
			int currentPresetInertiaRowCount,
			int currentVsPhysicalPropRowCount,
			int openSourceRotorInertiaReferenceCount,
			int paperReportedRotorInertiaReferenceCount,
			int currentPresetInflowRowCount,
			int currentPresetArmFlexConingRowCount,
			int beamTheoryArmSensitivityRowCount,
			int multicopterConingMeasurementRowCount,
			RotorInertiaAudit inertia,
			DynamicInflowAudit dynamicInflow,
			ConingAudit coning,
			ArmFlexAudit armFlex
	) {
	}

	public static RotorDynamicsAudit audit(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}

		RotorSpec rotor = config.rotors().get(0);
		double hoverThrust = hoverThrustNewtons(config, rotor);
		double hoverOmega = Math.sqrt(hoverThrust / rotor.thrustCoefficient());
		double maxOmega = rotor.maxOmegaRadiansPerSecond();
		double hoverSpinRatio = spinRatio(rotor, hoverOmega);
		double maxSpinRatio = spinRatio(rotor, maxOmega);
		Vec3 rotorArm = rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters());
		ArmFlexAudit armFlex = armFlexAudit(rotor, rotorArm, hoverThrust, hoverOmega, maxOmega);

		return new RotorDynamicsAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				ROTOR_INERTIA_ROW_COUNT,
				ROTOR_INFLOW_ROW_COUNT,
				ARM_FLEX_CONING_ROW_COUNT,
				PHYSICAL_PROP_REFERENCE_COUNT,
				CURRENT_PRESET_INERTIA_ROW_COUNT,
				CURRENT_VS_PHYSICAL_PROP_ROW_COUNT,
				OPEN_SOURCE_ROTOR_INERTIA_REFERENCE_COUNT,
				PAPER_REPORTED_ROTOR_INERTIA_REFERENCE_COUNT,
				CURRENT_PRESET_INFLOW_ROW_COUNT,
				CURRENT_PRESET_ARM_FLEX_CONING_ROW_COUNT,
				BEAM_THEORY_ARM_SENSITIVITY_ROW_COUNT,
				MULTICOPTER_CONING_MEASUREMENT_ROW_COUNT,
				rotorInertiaAudit(config, rotor, hoverOmega, maxOmega),
				dynamicInflowAudit(rotor, hoverThrust, hoverOmega, maxOmega),
				coningAudit(rotor, hoverThrust, hoverOmega, maxOmega, hoverSpinRatio, maxSpinRatio),
				armFlex
		);
	}

	private static RotorInertiaAudit rotorInertiaAudit(
			DroneConfig config,
			RotorSpec rotor,
			double hoverOmega,
			double maxOmega
	) {
		PhysicalPropReference reference = nearestPhysicalPropReference(rotor);
		double bodyRateRadiansPerSecond = Math.toRadians(BODY_RATE_REFERENCE_DEGREES_PER_SECOND);
		double hoverAngularMomentum = rotor.rotorInertiaKgMetersSquared() * hoverOmega;
		double maxAngularMomentum = rotor.rotorInertiaKgMetersSquared() * maxOmega;
		double equivalentMassGrams = rotor.rotorInertiaKgMetersSquared()
				/ Math.max(1.0e-12, RotorSpec.UNIFORM_BLADE_PROP_INERTIA_COEFFICIENT * rotor.radiusMeters() * rotor.radiusMeters())
				* 1000.0;
		return new RotorInertiaAudit(
				reference,
				config.rotors().size(),
				diameterInches(rotor),
				rotor.bladePitchToDiameterRatio(),
				rotor.bladeCount(),
				rotor.rotorInertiaKgMetersSquared(),
				ratio(rotor.rotorInertiaKgMetersSquared(), reference.hubBiasedInertiaKgMetersSquared()),
				ratio(rotor.rotorInertiaKgMetersSquared(), reference.uniformBladeInertiaKgMetersSquared()),
				ratio(rotor.rotorInertiaKgMetersSquared(), reference.tipBiasedInertiaKgMetersSquared()),
				equivalentMassGrams,
				ratio(equivalentMassGrams, reference.massGrams()),
				ZJU_REPORTED_ROTOR_INERTIA_KG_METERS_SQUARED,
				ratio(rotor.rotorInertiaKgMetersSquared(), ZJU_REPORTED_ROTOR_INERTIA_KG_METERS_SQUARED),
				rpm(hoverOmega),
				rpm(maxOmega),
				hoverAngularMomentum,
				maxAngularMomentum,
				BODY_RATE_REFERENCE_DEGREES_PER_SECOND,
				hoverAngularMomentum * bodyRateRadiansPerSecond,
				maxAngularMomentum * bodyRateRadiansPerSecond,
				maxAngularMomentum * bodyRateRadiansPerSecond * config.rotors().size(),
				rotor.rotorInertiaKgMetersSquared() * maxOmega / Math.max(1.0e-6, config.motorTimeConstantSeconds()),
				rotor.rotorInertiaKgMetersSquared() * maxOmega / 0.050
		);
	}

	private static DynamicInflowAudit dynamicInflowAudit(
			RotorSpec rotor,
			double hoverThrust,
			double hoverOmega,
			double maxOmega
	) {
		double hoverInducedVelocity = DronePhysics.targetRotorInducedVelocityMetersPerSecond(rotor, hoverThrust, 1.0);
		double maxInducedVelocity = DronePhysics.targetRotorInducedVelocityMetersPerSecond(
				rotor,
				rotor.maxThrustNewtons(),
				1.0
		);
		double wakeTransitOneRadiusHover = rotor.radiusMeters() / Math.max(1.0e-6, hoverInducedVelocity);
		double wakeTransitTwoRadiusHover = 2.0 * rotor.radiusMeters() / Math.max(1.0e-6, hoverInducedVelocity);
		double wakeTransitOneRadiusMax = rotor.radiusMeters() / Math.max(1.0e-6, maxInducedVelocity);
		double hoverDynamicTau = DronePhysics.rotorDynamicInflowTimeConstantSeconds(
				rotor,
				Vec3.ZERO,
				hoverOmega,
				hoverInducedVelocity,
				0.0,
				hoverInducedVelocity
		);
		double highThrustTau = DronePhysics.rotorDynamicInflowTimeConstantSeconds(
				rotor,
				Vec3.ZERO,
				maxOmega * 0.92,
				maxInducedVelocity,
				0.0,
				hoverInducedVelocity
		);
		double crossflowTau = DronePhysics.rotorDynamicInflowTimeConstantSeconds(
				rotor,
				new Vec3(FAST_CROSSFLOW_METERS_PER_SECOND, 0.0, 0.0),
				hoverOmega,
				hoverInducedVelocity,
				0.0,
				hoverInducedVelocity
		);
		double descentTau = DronePhysics.rotorDynamicInflowTimeConstantSeconds(
				rotor,
				new Vec3(0.0, -FAST_DESCENT_METERS_PER_SECOND, 0.0),
				hoverOmega,
				hoverInducedVelocity,
				hoverInducedVelocity,
				hoverInducedVelocity
		);
		return new DynamicInflowAudit(
				rotor.inducedInflowTimeConstantSeconds(),
				rotor.inducedInflowLagCoefficient(),
				ROTORS_PX4_TIME_CONSTANT_UP_SECONDS,
				ROTORS_PX4_TIME_CONSTANT_DOWN_SECONDS,
				ratio(rotor.inducedInflowTimeConstantSeconds(), ROTORS_PX4_TIME_CONSTANT_UP_SECONDS),
				ratio(rotor.inducedInflowTimeConstantSeconds(), ROTORS_PX4_TIME_CONSTANT_DOWN_SECONDS),
				rotor.radiusMeters(),
				hoverInducedVelocity,
				maxInducedVelocity,
				wakeTransitOneRadiusHover,
				wakeTransitTwoRadiusHover,
				wakeTransitOneRadiusMax,
				ratio(rotor.inducedInflowTimeConstantSeconds(), wakeTransitOneRadiusHover),
				ratio(rotor.inducedInflowTimeConstantSeconds(), wakeTransitTwoRadiusHover),
				ratio(rotor.inducedInflowTimeConstantSeconds(), wakeTransitOneRadiusMax),
				hoverDynamicTau,
				highThrustTau,
				crossflowTau,
				descentTau
		);
	}

	private static ConingAudit coningAudit(
			RotorSpec rotor,
			double hoverThrust,
			double hoverOmega,
			double maxOmega,
			double hoverSpinRatio,
			double maxSpinRatio
	) {
		double hoverTarget = DronePhysics.rotorConingTargetIntensity(rotor, hoverThrust, hoverOmega);
		double maxTarget = DronePhysics.rotorConingTargetIntensity(rotor, rotor.maxThrustNewtons(), maxOmega);
		double maxAngleDegrees = Math.toDegrees(DronePhysics.rotorConingAngleRadians(rotor, maxTarget));
		return new ConingAudit(
				hoverTarget,
				Math.toDegrees(DronePhysics.rotorConingAngleRadians(rotor, hoverTarget)),
				maxTarget,
				maxAngleDegrees,
				DronePhysics.rotorConingThrustScale(maxTarget),
				DronePhysics.rotorConingLoadFactor(maxTarget),
				DronePhysics.rotorConingVibration(rotor, maxOmega, maxTarget),
				DronePhysics.rotorConingNaturalFrequencyHertz(rotor, maxSpinRatio),
				DronePhysics.rotorConingDampingRatio(maxSpinRatio),
				DJI_PHANTOM_8500_RPM_CONING_DEGREES,
				DJI_PHANTOM_8500_RPM_DEFLECTION_MILLIMETERS,
				TMOTOR_15X5_5000_RPM_CONING_DEGREES,
				TMOTOR_15X5_5000_RPM_DEFLECTION_MILLIMETERS,
				ratio(maxAngleDegrees, DJI_PHANTOM_8500_RPM_CONING_DEGREES),
				ratio(maxAngleDegrees, TMOTOR_15X5_5000_RPM_CONING_DEGREES)
		);
	}

	private static ArmFlexAudit armFlexAudit(
			RotorSpec rotor,
			Vec3 rotorArm,
			double hoverThrust,
			double hoverOmega,
			double maxOmega
	) {
		double hoverTarget = DronePhysics.rotorArmFlexTargetIntensity(rotor, hoverThrust, 0.0, 0.0, hoverOmega);
		double maxSteadyTarget = DronePhysics.rotorArmFlexTargetIntensity(
				rotor,
				rotor.maxThrustNewtons(),
				0.0,
				0.0,
				maxOmega
		);
		double maxSnapTarget = DronePhysics.rotorArmFlexTargetIntensity(
				rotor,
				rotor.maxThrustNewtons(),
				MAX_SNAP_FORCE_SLEW_NORMALIZED,
				MAX_SNAP_TORQUE_SLEW_NORMALIZED,
				maxOmega
		);
		double fullDeflectionMm = DronePhysics.rotorArmFlexVerticalDeflectionMeters(rotor, rotorArm, 1.0) * 1000.0;
		double fullTiltDegrees = Math.toDegrees(DronePhysics.rotorArmFlexTiltRadians(rotor, rotorArm, 1.0));
		double maxSpinRatio = spinRatio(rotor, maxOmega);
		double maxFrequency = DronePhysics.rotorArmFlexNaturalFrequencyHertz(rotor, rotorArm, maxSpinRatio);
		return new ArmFlexAudit(
				hoverTarget,
				maxSteadyTarget,
				maxSnapTarget,
				fullDeflectionMm,
				fullDeflectionMm * maxSteadyTarget,
				fullDeflectionMm * maxSnapTarget,
				fullTiltDegrees,
				fullTiltDegrees * maxSteadyTarget,
				fullTiltDegrees * maxSnapTarget,
				maxFrequency,
				DronePhysics.rotorArmFlexDampingRatio(maxSpinRatio),
				DronePhysics.rotorArmFlexVibration(rotor, maxOmega, maxSnapTarget),
				beamSensitivityAudit(rotor, rotorArm, fullDeflectionMm * maxSnapTarget, maxFrequency)
		);
	}

	private static BeamSensitivityAudit beamSensitivityAudit(
			RotorSpec rotor,
			Vec3 rotorArm,
			double runtimeMaxSnapDeflectionMillimeters,
			double runtimeMaxSpinFrequencyHertz
	) {
		BeamGeometry geometry = representativeBeamGeometry(rotor);
		double armLength = Math.max(0.08, Math.hypot(rotorArm.x(), rotorArm.z()));
		double youngsModulusPascals = geometry.youngsModulusGpa() * 1.0e9;
		double secondMoment = geometry.secondMomentAreaMeters4();
		double loadForce = rotor.maxThrustNewtons();
		double deflectionMeters = loadForce * Math.pow(armLength, 3.0)
				/ Math.max(1.0e-12, 3.0 * youngsModulusPascals * secondMoment);
		double stiffness = loadForce / Math.max(1.0e-12, deflectionMeters);
		double beamMass = geometry.areaMetersSquared() * armLength * geometry.densityKgMetersCubed();
		double effectiveMass = 0.236 * beamMass + geometry.tipMassKg();
		double frequency = Math.sqrt(stiffness / Math.max(1.0e-9, effectiveMass)) / (2.0 * Math.PI);
		return new BeamSensitivityAudit(
				geometry.geometryId(),
				geometry.youngsModulusGpa(),
				armLength,
				loadForce,
				secondMoment,
				deflectionMeters * 1000.0,
				stiffness,
				frequency,
				ratio(deflectionMeters * 1000.0, runtimeMaxSnapDeflectionMillimeters),
				ratio(frequency, runtimeMaxSpinFrequencyHertz)
		);
	}

	private static BeamGeometry representativeBeamGeometry(RotorSpec rotor) {
		double diameterInches = diameterInches(rotor);
		if (diameterInches < 4.0) {
			return new BeamGeometry("3in_solid_8x3mm_E135GPa", 135.0, 0.008, 0.003, 1600.0, 0.025);
		}
		if (diameterInches > 7.0) {
			return new BeamGeometry("10in_solid_16x6mm_E70GPa", 70.0, 0.016, 0.006, 1600.0, 0.090);
		}
		return new BeamGeometry("5in_solid_10x5mm_E70GPa", 70.0, 0.010, 0.005, 1600.0, 0.035);
	}

	private record BeamGeometry(
			String geometryId,
			double youngsModulusGpa,
			double outerWidthMeters,
			double outerHeightMeters,
			double densityKgMetersCubed,
			double tipMassKg
	) {
		double areaMetersSquared() {
			return outerWidthMeters * outerHeightMeters;
		}

		double secondMomentAreaMeters4() {
			return outerWidthMeters * Math.pow(outerHeightMeters, 3.0) / 12.0;
		}
	}

	private static PhysicalPropReference nearestPhysicalPropReference(RotorSpec rotor) {
		PhysicalPropReference best = PHYSICAL_REFERENCES[0];
		double bestScore = Double.POSITIVE_INFINITY;
		double currentDiameter = diameterInches(rotor);
		for (PhysicalPropReference reference : PHYSICAL_REFERENCES) {
			double diameterScore = Math.abs(reference.diameterInches() - currentDiameter) / Math.max(1.0e-6, currentDiameter);
			double pitchScore = Math.abs(reference.pitchToDiameterRatio() - rotor.bladePitchToDiameterRatio());
			double bladeScore = Math.abs(reference.bladeCount() - rotor.bladeCount()) * 0.25;
			double score = 2.0 * diameterScore + pitchScore + bladeScore;
			if (score < bestScore) {
				bestScore = score;
				best = reference;
			}
		}
		return best;
	}

	private static double hoverThrustNewtons(DroneConfig config, RotorSpec rotor) {
		return config.hoverDirectThrustFraction() * rotor.maxThrustNewtons();
	}

	private static double diameterInches(RotorSpec rotor) {
		return rotor.radiusMeters() * 2.0 * INCHES_PER_METER;
	}

	private static double rpm(double omegaRadiansPerSecond) {
		return omegaRadiansPerSecond * 60.0 / (2.0 * Math.PI);
	}

	private static double spinRatio(RotorSpec rotor, double omegaRadiansPerSecond) {
		return MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.10);
	}

	private static double ratio(double numerator, double denominator) {
		return Math.abs(denominator) <= 1.0e-12 ? 0.0 : numerator / denominator;
	}
}
