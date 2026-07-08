package com.tenicana.dronecraft.sim;

import java.util.Arrays;

public final class DroneState {
	private static final double RPM_PER_RADIAN_PER_SECOND = 60.0 / (Math.PI * 2.0);

	private Vec3 positionMeters = Vec3.ZERO;
	private Vec3 velocityMetersPerSecond = Vec3.ZERO;
	private Vec3 linearAccelerationWorldMetersPerSecondSquared = Vec3.ZERO;
	private double contactImpactSpeedMetersPerSecond;
	private double contactSlipSpeedMetersPerSecond;
	private double contactBounceSpeedMetersPerSecond;
	private double contactSurfaceFrictionMultiplier = 1.0;
	private double contactSurfaceRestitutionMultiplier = 1.0;
	private double contactSurfaceScrapeMultiplier = 1.0;
	private Vec3 contactAngularImpulseBodyRadiansPerSecond = Vec3.ZERO;
	private Quaternion orientation = Quaternion.IDENTITY;
	private Quaternion estimatedOrientation = Quaternion.IDENTITY;
	private Vec3 attitudeEstimateErrorRadians = Vec3.ZERO;
	private double attitudeEstimatorAccelerometerTrust;
	private Vec3 angularVelocityBodyRadiansPerSecond = Vec3.ZERO;
	private Vec3 angularAccelerationBodyRadiansPerSecondSquared = Vec3.ZERO;
	private Vec3 gyroAngularVelocityBodyRadiansPerSecond = Vec3.ZERO;
	private Vec3 gyroBiasBodyRadiansPerSecond = Vec3.ZERO;
	private Vec3 accelerometerBodyMetersPerSecondSquared = Vec3.ZERO;
	private Vec3 accelerometerBiasBodyMetersPerSecondSquared = Vec3.ZERO;
	private double gyroClipIntensity;
	private double accelerometerClipIntensity;
	private double imuSupplyNoiseIntensity;
	private double gyroDynamicNotchFrequencyHertz;
	private double gyroDynamicNotchAttenuation;
	private double gyroDynamicNotchSpreadHertz;
	private double gyroRpmHarmonicNotchAttenuation;
	private double gyroBladePassNotchFrequencyHertz;
	private double gyroBladePassNotchAttenuation;
	private double gyroBladePassNotchSpreadHertz;
	private double barometerAltitudeMeters;
	private double barometerVerticalSpeedMetersPerSecond;
	private double barometerPressureHectopascals = 1013.25;
	private double barometerErrorMeters;
	private double barometerSensorNoiseMeters;
	private double barometerPressurePortErrorMeters;
	private double barometerPropwashErrorMeters;
	private FlightMode flightMode = FlightMode.DEFAULT_FIRST_FLIGHT;
	private Vec3 levelTargetAttitudeRadians = Vec3.ZERO;
	private Vec3 levelAttitudeErrorRadians = Vec3.ZERO;
	private double selfLevelBlend;
	private Vec3 targetRatesBodyRadiansPerSecond = Vec3.ZERO;
	private Vec3 rateErrorBodyRadiansPerSecond = Vec3.ZERO;
	private Vec3 pidProportionalTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 pidIntegralTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 pidDerivativeTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 pidFeedForwardTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 pidOutputTorqueBodyNewtonMeters = Vec3.ZERO;
	private double pidIntegralRelax;
	private Vec3 pidIntegralRelaxAxes = Vec3.ZERO;
	private double pidDTermLowPassCutoffHertz;
	private DroneInput rawControlInput = DroneInput.idle();
	private DroneInput processedControlInput = DroneInput.idle();
	private double controlLinkLossSeconds;
	private boolean controlFailsafeActive;
	private double controlFrameAgeSeconds;
	private double controlFrameIntervalSeconds;
	private double controlFrameError;
	private double[] motorOmegaRadiansPerSecond;
	private double[] motorTargetOmegaRadiansPerSecond;
	private double[] motorTrackingError;
	private double[] motorActuatorAuthority;
	private double[] motorAngularAccelerationRadiansPerSecondSquared;
	private double[] motorAerodynamicTorqueNewtonMeters;
	private double[] motorMechanicalLossTorqueNewtonMeters;
	private double[] motorTorqueRippleNewtonMeters;
	private double[] motorShaftPowerWatts;
	private double[] motorPhaseCurrentAmps;
	private double[] motorCurrentRippleAmps;
	private double[] motorElectricalEfficiency;
	private double[] motorVoltageHeadroom;
	private double[] motorWindingResistanceScale;
	private double[] motorCommutationRippleIntensity;
	private double[] motorRpmTelemetryOmegaRadiansPerSecond;
	private double[] motorRpmTelemetryValidity;
	private double[] escOutputCommand;
	private double[] escElectricalOutputCommand;
	private double[] escElectricalOutputError;
	private double escCommandFrameAgeSeconds;
	private double escCommandFrameIntervalSeconds;
	private double escCommandError;
	private double[] escDesyncIntensity;
	private double[] escTemperatureCelsius;
	private double[] escCoolingFactor;
	private double[] escThermalLimitByEsc;
	private double[] motorCurrentAmps;
	private double[] motorRegenerativeCurrentAmps;
	private double[] motorTemperatureCelsius;
	private double[] motorCoolingFactor;
	private double[] rotorA4mcVentilationEfficiency;
	private boolean[] rotorCtCpJReferenceAvailable;
	private boolean[] rotorCtCpJReferenceBlocked;
	private boolean[] rotorCtCpJReferenceClamped;
	private boolean[] rotorCtCpJReferenceRuntimeApplied;
	private int[] rotorCtCpJReferenceRuntimeStatusOrdinal;
	private int[] rotorCtCpJReferenceInterpolationStatusOrdinal;
	private int[] rotorCtCpJReferenceLookupStatusCodeOrdinal;
	private double[] rotorCtCpJReferenceAdvanceRatioJ;
	private double[] rotorCtCpJReferenceRpm;
	private Vec3[] rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond;
	private Vec3[] rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond;
	private double[] rotorCtCpJReferenceTransverseAirSpeedMetersPerSecond;
	private double[] rotorCtCpJReferenceInflowAngleRadians;
	private double[] rotorCtCpJReferenceSpeedOfSoundMetersPerSecond;
	private double[] rotorCtCpJReferenceDynamicViscosityPascalSeconds;
	private double[] rotorCtCpJReferenceTipMach;
	private double[] rotorCtCpJReferenceReynoldsNumber;
	private double[] rotorCtCpJReferenceReynoldsIndex;
	private double[] rotorCtCpJReferenceTipMachRuntimeMargin;
	private double[] rotorCtCpJReferenceReynoldsIndexRuntimeMargin;
	private double[] rotorCtCpJReferenceOperatingEnvelopeMarginFraction;
	private double[] rotorCtCpJReferenceThrustCoefficientCt;
	private double[] rotorCtCpJReferencePowerCoefficientCp;
	private double[] rotorCtCpJReferenceTorqueCoefficientCq;
	private double[] rotorCtCpJReferenceEfficiencyEta;
	private double[] rotorCtCpJReferenceThrustNewtons;
	private double[] rotorCtCpJReferenceShaftPowerWatts;
	private double[] rotorCtCpJReferenceDiskLoadingNewtonsPerSquareMeter;
	private double[] rotorCtCpJReferenceIdealInducedVelocityMetersPerSecond;
	private double[] rotorCtCpJReferenceIdealMomentumPowerWatts;
	private double[] rotorCtCpJReferenceUsefulAxialThrustPowerWatts;
	private double[] rotorCtCpJReferenceIdealInducedPowerWatts;
	private double[] rotorCtCpJReferenceAxialPropulsiveEfficiency;
	private double[] rotorCtCpJReferenceIdealMomentumPowerOverShaftPower;
	private double[] rotorCtCpJReferenceIntrinsicShaftPowerResidualWatts;
	private double[] rotorCtCpJReferenceIntrinsicShaftPowerResidualFraction;
	private double[] rotorCtCpJReferenceDiskMassFlowKilogramsPerSecond;
	private double[] rotorCtCpJReferenceFarWakeAxialVelocityMetersPerSecond;
	private double[] rotorCtCpJReferenceFarWakeContractedAreaSquareMeters;
	private double[] rotorCtCpJReferenceFarWakeEquivalentRadiusMeters;
	private double[] rotorCtCpJReferenceAngularMomentumSwirlRadiusMeters;
	private double[] rotorCtCpJReferenceWakeTangentialVelocityMetersPerSecond;
	private double[] rotorCtCpJReferenceWakeSwirlKineticPowerWatts;
	private double[] rotorCtCpJReferenceTotalWakeKineticPowerWatts;
	private double[] rotorCtCpJReferenceTotalWakeKineticPowerOverShaftPower;
	private double[] rotorCtCpJReferenceWakeSwirlKineticPowerOverShaftPower;
	private double[] rotorCtCpJReferenceTotalWakeKineticPowerResidualWatts;
	private double[] rotorCtCpJReferenceTotalWakeKineticPowerResidualFraction;
	private double[] rotorCtCpJReferenceWakeAngularMomentumTorqueNewtonMeters;
	private double[] rotorCtCpJReferenceWakeAngularMomentumTorqueResidualNewtonMeters;
	private double[] rotorCtCpJReferenceWakeAngularMomentumTorqueResidualFraction;
	private double[] rotorCtCpJReferenceShaftTorqueNewtonMeters;
	private Vec3[] rotorCtCpJReferenceThrustForceBodyNewtons;
	private Vec3[] rotorCtCpJReferenceReactionTorqueBodyNewtonMeters;
	private Vec3[] rotorCtCpJReferenceThrustMomentBodyNewtonMeters;
	private Vec3[] rotorCtCpJReferenceTotalTorqueBodyNewtonMeters;
	private double[] rotorCtCpJReferenceThrustResidualNewtons;
	private double[] rotorCtCpJReferenceShaftPowerResidualWatts;
	private double[] rotorCtCpJReferenceShaftTorqueResidualNewtonMeters;
	private double[] rotorCtCpJReferenceThrustRatio;
	private double[] rotorCtCpJReferenceShaftTorqueRatio;
	private double[] rotorThrustNewtons;
	private Vec3[] rotorForceBodyNewtons;
	private Vec3[] rotorTorqueBodyNewtonMeters;
	private double[] rotorInducedVelocityMetersPerSecond;
	private double[] rotorInducedLagThrustScale;
	private double[] rotorDynamicInflowTimeConstantSeconds;
	private double[] rotorTranslationalLiftIntensity;
	private double[] rotorAdvanceRatio;
	private double[] rotorPropellerAdvanceRatioJ;
	private double[] rotorPropellerThrustScale;
	private double[] rotorPropellerPowerScale;
	private double[] rotorAxialGustThrustScale;
	private double[] rotorReverseFlowInboardFraction;
	private double[] rotorTipMach;
	private double[] rotorCompressibilityThrustScale;
	private double[] rotorReynoldsNumber;
	private double[] rotorReynoldsIndex;
	private double[] rotorLowReynoldsLoss;
	private double[] rotorBladeAngleOfAttackRadians;
	private double[] rotorBladeElementStallIntensity;
	private double[] rotorBladeDissymmetryIntensity;
	private double[] rotorBladePassRippleIntensity;
	private double[] rotorAerodynamicLoadFactor;
	private double[] rotorDiskWindGradientThrustLossFraction;
	private double[] rotorDiskWindGradientLoadFactor;
	private double[] rotorDiskWindGradientVibration;
	private double[] rotorDiskWindGradientStallIntensity;
	private double[] rotorInPlaneDragForceNewtons;
	private double[] rotorInPlaneDragShaftTorqueNewtonMeters;
	private double[] rotorInPlaneDragShaftPowerWatts;
	private double[] rotorFlappingForceNewtons;
	private double[] rotorFlappingTiltRadians;
	private double[] rotorConingIntensity;
	private double[] rotorConingAngleRadians;
	private double[] rotorStallIntensity;
	private double[] rotorWindmillingIntensity;
	private double[] rotorSurfaceScrapeIntensity;
	private double[] rotorWakeInterferenceIntensity;
	private double[] rotorWakeThrustScale;
	private double[] rotorWetThrustScale;
	private double[] rotorIcingSeverity;
	private double[] rotorIcingThrustScale;
	private double[] rotorIcingPowerScale;
	private double[] rotorCoaxialLoadBias;
	private double[] rotorCoaxialLoadBiasTarget;
	private double[] rotorCoaxialLoadBiasClipping;
	private double[] rotorCoaxialAllocationLoadFraction;
	private double[] rotorCoaxialAllocationCommandRatio;
	private double[] rotorCoaxialAllocationMechanicalGainPercent;
	private double[] rotorCoaxialAllocationElectricalGainPercent;
	private double[] rotorCoaxialAllocationUncertaintyPercent;
	private double[] rotorWakeSwirlVelocityMetersPerSecond;
	private double[] rotorArmFlexIntensity;
	private double[] rotorArmFlexDeflectionMeters;
	private double[] rotorArmFlexTiltRadians;
	private double[] rotorDamageVibration;
	private double[] rotorHealth;
	private double rotorVibration;
	private double rotorInflowSkewIntensity;
	private Vec3 rotorInflowSkewTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 rotorBladeDissymmetryTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 rotorWakeSwirlTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 rotorFlappingTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 rotorActiveBrakingTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 rotorInertiaTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 rotorAccelerationReactionTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 rotorGyroscopicTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 rotorAngularDragTorqueBodyNewtonMeters = Vec3.ZERO;
	private double batteryVoltage;
	private double batteryOpenCircuitVoltage;
	private double batteryOhmicSagVoltage;
	private double batteryTransientSagVoltage;
	private double batterySlowPolarizationVoltage;
	private double batteryRegenerativeCurrentAmps;
	private double batteryVoltageSpike;
	private double batteryBusRippleVoltage;
	private double batteryEffectiveResistanceOhms;
	private double batteryTwentyPercentSagCurrentAmps;
	private double batteryTwentyPercentSagCurrentMargin;
	private double batteryTemperatureCelsius = 25.0;
	private double batteryCoolingFactor = 1.0;
	private double a4mcPackVentilationEfficiency = 1.0;
	private double batteryThermalLimit = 1.0;
	private double batteryAmpSecondsConsumed;
	private double batteryEquivalentCycles;
	private double batteryStateOfChargeResistanceScale = 1.0;
	private double batteryTemperatureResistanceScale = 1.0;
	private double batteryResistanceAgingScale = 1.0;
	private double batteryCapacityAgingScale = 1.0;
	private double batteryPolarizationResistanceScale = 1.0;
	private double batteryCurrentAmps;
	private double batteryStateOfCharge = 1.0;
	private double batteryCurrentLimit = 1.0;
	private double batteryPowerLimit = 1.0;
	private double motorThermalLimit = 1.0;
	private double escThermalLimit = 1.0;
	private Vec3 relativeAirVelocityBodyMetersPerSecond = Vec3.ZERO;
	private Vec3 effectiveWindVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 windGustVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 drydenTurbulenceVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 windBurbleVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 a4mcSourceGustVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 a4mcUpdraftVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 a4mcTerrainShearVelocityWorldMetersPerSecond = Vec3.ZERO;
	private double windShearAccelerationMetersPerSecondSquared;
	private double airspeedMetersPerSecond;
	private double angleOfAttackRadians;
	private double sideslipRadians;
	private double airframeSeparatedFlowIntensity;
	private Vec3 airframeBodyDragForceBodyNewtons = Vec3.ZERO;
	private Vec3 linearDampingDragForceWorldNewtons = Vec3.ZERO;
	private double airframeDragAlongFlowNewtons;
	private double airframeDragEquivalentLinearCoefficient;
	private double airframeDragEquivalentCdAMetersSquared;
	private double airframeDragImavReferenceRatio;
	private Vec3 airframeLiftForceBodyNewtons = Vec3.ZERO;
	private Vec3 groundEffectDragForceBodyNewtons = Vec3.ZERO;
	private Vec3 groundEffectLevelingTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 rotorWashDragForceBodyNewtons = Vec3.ZERO;
	private Vec3 rotorWallEffectForceBodyNewtons = Vec3.ZERO;
	private double propwashIntensity;
	private double propwashWakeIntensity;
	private double vortexRingStateIntensity;
	private double vortexRingThrustBuffetAmplitude;
	private double vortexRingMaxThrustBuffetAmplitude;
	private Vec3 vortexRingBuffetForceBodyNewtons = Vec3.ZERO;
	private Vec3 propwashTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 windTurbulenceTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 airframeAerodynamicTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 airframePressureCenterTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 airframeAngularDragTorqueBodyNewtonMeters = Vec3.ZERO;
	private double mixerSaturation;
	private double mixerLowSaturation;
	private double mixerHighSaturation;
	private double mixerLowHeadroom = 1.0;
	private double mixerHighHeadroom = 1.0;
	private Vec3 mixerOutputTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 mixerAxisAuthority = new Vec3(1.0, 1.0, 1.0);
	private double pidAttenuation = 1.0;
	private double antiGravityBoost;

	public DroneState(int motorCount) {
		motorOmegaRadiansPerSecond = new double[motorCount];
		motorTargetOmegaRadiansPerSecond = new double[motorCount];
		motorTrackingError = new double[motorCount];
		motorActuatorAuthority = new double[motorCount];
		motorAngularAccelerationRadiansPerSecondSquared = new double[motorCount];
		motorAerodynamicTorqueNewtonMeters = new double[motorCount];
		motorMechanicalLossTorqueNewtonMeters = new double[motorCount];
		motorTorqueRippleNewtonMeters = new double[motorCount];
		motorShaftPowerWatts = new double[motorCount];
		motorPhaseCurrentAmps = new double[motorCount];
		motorCurrentRippleAmps = new double[motorCount];
		motorElectricalEfficiency = new double[motorCount];
		motorVoltageHeadroom = new double[motorCount];
		motorWindingResistanceScale = new double[motorCount];
		motorCommutationRippleIntensity = new double[motorCount];
		motorRpmTelemetryOmegaRadiansPerSecond = new double[motorCount];
		motorRpmTelemetryValidity = new double[motorCount];
		escOutputCommand = new double[motorCount];
		escElectricalOutputCommand = new double[motorCount];
		escElectricalOutputError = new double[motorCount];
		escDesyncIntensity = new double[motorCount];
		escTemperatureCelsius = new double[motorCount];
		escCoolingFactor = new double[motorCount];
		escThermalLimitByEsc = new double[motorCount];
		motorCurrentAmps = new double[motorCount];
		motorRegenerativeCurrentAmps = new double[motorCount];
		motorTemperatureCelsius = new double[motorCount];
		motorCoolingFactor = new double[motorCount];
		rotorA4mcVentilationEfficiency = new double[motorCount];
		rotorCtCpJReferenceAvailable = new boolean[motorCount];
		rotorCtCpJReferenceBlocked = new boolean[motorCount];
		rotorCtCpJReferenceClamped = new boolean[motorCount];
		rotorCtCpJReferenceRuntimeApplied = new boolean[motorCount];
		rotorCtCpJReferenceRuntimeStatusOrdinal = new int[motorCount];
		rotorCtCpJReferenceInterpolationStatusOrdinal = new int[motorCount];
		rotorCtCpJReferenceLookupStatusCodeOrdinal = new int[motorCount];
		rotorCtCpJReferenceAdvanceRatioJ = new double[motorCount];
		rotorCtCpJReferenceRpm = new double[motorCount];
		rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond = new Vec3[motorCount];
		rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond = new Vec3[motorCount];
		rotorCtCpJReferenceTransverseAirSpeedMetersPerSecond = new double[motorCount];
		rotorCtCpJReferenceInflowAngleRadians = new double[motorCount];
		rotorCtCpJReferenceSpeedOfSoundMetersPerSecond = new double[motorCount];
		rotorCtCpJReferenceDynamicViscosityPascalSeconds = new double[motorCount];
		rotorCtCpJReferenceTipMach = new double[motorCount];
		rotorCtCpJReferenceReynoldsNumber = new double[motorCount];
		rotorCtCpJReferenceReynoldsIndex = new double[motorCount];
		rotorCtCpJReferenceTipMachRuntimeMargin = new double[motorCount];
		rotorCtCpJReferenceReynoldsIndexRuntimeMargin = new double[motorCount];
		rotorCtCpJReferenceOperatingEnvelopeMarginFraction = new double[motorCount];
		rotorCtCpJReferenceThrustCoefficientCt = new double[motorCount];
		rotorCtCpJReferencePowerCoefficientCp = new double[motorCount];
		rotorCtCpJReferenceTorqueCoefficientCq = new double[motorCount];
		rotorCtCpJReferenceEfficiencyEta = new double[motorCount];
		rotorCtCpJReferenceThrustNewtons = new double[motorCount];
		rotorCtCpJReferenceShaftPowerWatts = new double[motorCount];
		rotorCtCpJReferenceDiskLoadingNewtonsPerSquareMeter = new double[motorCount];
		rotorCtCpJReferenceIdealInducedVelocityMetersPerSecond = new double[motorCount];
		rotorCtCpJReferenceIdealMomentumPowerWatts = new double[motorCount];
		rotorCtCpJReferenceUsefulAxialThrustPowerWatts = new double[motorCount];
		rotorCtCpJReferenceIdealInducedPowerWatts = new double[motorCount];
		rotorCtCpJReferenceAxialPropulsiveEfficiency = new double[motorCount];
		rotorCtCpJReferenceIdealMomentumPowerOverShaftPower = new double[motorCount];
		rotorCtCpJReferenceIntrinsicShaftPowerResidualWatts = new double[motorCount];
		rotorCtCpJReferenceIntrinsicShaftPowerResidualFraction = new double[motorCount];
		rotorCtCpJReferenceDiskMassFlowKilogramsPerSecond = new double[motorCount];
		rotorCtCpJReferenceFarWakeAxialVelocityMetersPerSecond = new double[motorCount];
		rotorCtCpJReferenceFarWakeContractedAreaSquareMeters = new double[motorCount];
		rotorCtCpJReferenceFarWakeEquivalentRadiusMeters = new double[motorCount];
		rotorCtCpJReferenceAngularMomentumSwirlRadiusMeters = new double[motorCount];
		rotorCtCpJReferenceWakeTangentialVelocityMetersPerSecond = new double[motorCount];
		rotorCtCpJReferenceWakeSwirlKineticPowerWatts = new double[motorCount];
		rotorCtCpJReferenceTotalWakeKineticPowerWatts = new double[motorCount];
		rotorCtCpJReferenceTotalWakeKineticPowerOverShaftPower = new double[motorCount];
		rotorCtCpJReferenceWakeSwirlKineticPowerOverShaftPower = new double[motorCount];
		rotorCtCpJReferenceTotalWakeKineticPowerResidualWatts = new double[motorCount];
		rotorCtCpJReferenceTotalWakeKineticPowerResidualFraction = new double[motorCount];
		rotorCtCpJReferenceWakeAngularMomentumTorqueNewtonMeters = new double[motorCount];
		rotorCtCpJReferenceWakeAngularMomentumTorqueResidualNewtonMeters = new double[motorCount];
		rotorCtCpJReferenceWakeAngularMomentumTorqueResidualFraction = new double[motorCount];
		rotorCtCpJReferenceShaftTorqueNewtonMeters = new double[motorCount];
		rotorCtCpJReferenceThrustForceBodyNewtons = new Vec3[motorCount];
		rotorCtCpJReferenceReactionTorqueBodyNewtonMeters = new Vec3[motorCount];
		rotorCtCpJReferenceThrustMomentBodyNewtonMeters = new Vec3[motorCount];
		rotorCtCpJReferenceTotalTorqueBodyNewtonMeters = new Vec3[motorCount];
		rotorCtCpJReferenceThrustResidualNewtons = new double[motorCount];
		rotorCtCpJReferenceShaftPowerResidualWatts = new double[motorCount];
		rotorCtCpJReferenceShaftTorqueResidualNewtonMeters = new double[motorCount];
		rotorCtCpJReferenceThrustRatio = new double[motorCount];
		rotorCtCpJReferenceShaftTorqueRatio = new double[motorCount];
		rotorThrustNewtons = new double[motorCount];
		rotorForceBodyNewtons = new Vec3[motorCount];
		rotorTorqueBodyNewtonMeters = new Vec3[motorCount];
		rotorInducedVelocityMetersPerSecond = new double[motorCount];
		rotorInducedLagThrustScale = new double[motorCount];
		rotorDynamicInflowTimeConstantSeconds = new double[motorCount];
		rotorTranslationalLiftIntensity = new double[motorCount];
		rotorAdvanceRatio = new double[motorCount];
		rotorPropellerAdvanceRatioJ = new double[motorCount];
		rotorPropellerThrustScale = new double[motorCount];
		rotorPropellerPowerScale = new double[motorCount];
		rotorAxialGustThrustScale = new double[motorCount];
		rotorReverseFlowInboardFraction = new double[motorCount];
		rotorTipMach = new double[motorCount];
		rotorCompressibilityThrustScale = new double[motorCount];
		rotorReynoldsNumber = new double[motorCount];
		rotorReynoldsIndex = new double[motorCount];
		rotorLowReynoldsLoss = new double[motorCount];
		rotorBladeAngleOfAttackRadians = new double[motorCount];
		rotorBladeElementStallIntensity = new double[motorCount];
		rotorBladeDissymmetryIntensity = new double[motorCount];
		rotorBladePassRippleIntensity = new double[motorCount];
		rotorAerodynamicLoadFactor = new double[motorCount];
		rotorDiskWindGradientThrustLossFraction = new double[motorCount];
		rotorDiskWindGradientLoadFactor = new double[motorCount];
		rotorDiskWindGradientVibration = new double[motorCount];
		rotorDiskWindGradientStallIntensity = new double[motorCount];
		rotorInPlaneDragForceNewtons = new double[motorCount];
		rotorInPlaneDragShaftTorqueNewtonMeters = new double[motorCount];
		rotorInPlaneDragShaftPowerWatts = new double[motorCount];
		rotorFlappingForceNewtons = new double[motorCount];
		rotorFlappingTiltRadians = new double[motorCount];
		rotorConingIntensity = new double[motorCount];
		rotorConingAngleRadians = new double[motorCount];
		rotorStallIntensity = new double[motorCount];
		rotorWindmillingIntensity = new double[motorCount];
		rotorSurfaceScrapeIntensity = new double[motorCount];
		rotorWakeInterferenceIntensity = new double[motorCount];
		rotorWakeThrustScale = new double[motorCount];
		rotorWetThrustScale = new double[motorCount];
		rotorIcingSeverity = new double[motorCount];
		rotorIcingThrustScale = new double[motorCount];
		rotorIcingPowerScale = new double[motorCount];
		rotorCoaxialLoadBias = new double[motorCount];
		rotorCoaxialLoadBiasTarget = new double[motorCount];
		rotorCoaxialLoadBiasClipping = new double[motorCount];
		rotorCoaxialAllocationLoadFraction = new double[motorCount];
		rotorCoaxialAllocationCommandRatio = new double[motorCount];
		rotorCoaxialAllocationMechanicalGainPercent = new double[motorCount];
		rotorCoaxialAllocationElectricalGainPercent = new double[motorCount];
		rotorCoaxialAllocationUncertaintyPercent = new double[motorCount];
		rotorWakeSwirlVelocityMetersPerSecond = new double[motorCount];
		rotorArmFlexIntensity = new double[motorCount];
		rotorArmFlexDeflectionMeters = new double[motorCount];
		rotorArmFlexTiltRadians = new double[motorCount];
		rotorDamageVibration = new double[motorCount];
		rotorHealth = new double[motorCount];
		Arrays.fill(rotorForceBodyNewtons, Vec3.ZERO);
		Arrays.fill(rotorTorqueBodyNewtonMeters, Vec3.ZERO);
		Arrays.fill(rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond, Vec3.ZERO);
		Arrays.fill(rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond, Vec3.ZERO);
		Arrays.fill(rotorCtCpJReferenceThrustForceBodyNewtons, Vec3.ZERO);
		Arrays.fill(rotorCtCpJReferenceReactionTorqueBodyNewtonMeters, Vec3.ZERO);
		Arrays.fill(rotorCtCpJReferenceThrustMomentBodyNewtonMeters, Vec3.ZERO);
		Arrays.fill(rotorCtCpJReferenceTotalTorqueBodyNewtonMeters, Vec3.ZERO);
		Arrays.fill(escTemperatureCelsius, 25.0);
		Arrays.fill(escCoolingFactor, 1.0);
		Arrays.fill(escThermalLimitByEsc, 1.0);
		Arrays.fill(motorActuatorAuthority, 1.0);
		Arrays.fill(motorVoltageHeadroom, 1.0);
		Arrays.fill(motorWindingResistanceScale, 1.0);
		Arrays.fill(motorTemperatureCelsius, 25.0);
		Arrays.fill(motorCoolingFactor, 1.0);
		Arrays.fill(rotorA4mcVentilationEfficiency, 1.0);
		Arrays.fill(rotorCtCpJReferenceRuntimeStatusOrdinal,
				PropellerArchiveCtCpJRotorForceModel.RuntimeForceReplacementStatus.BLOCKED.ordinal());
		Arrays.fill(rotorCtCpJReferenceInterpolationStatusOrdinal,
				PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BLOCKED.ordinal());
		Arrays.fill(rotorInducedLagThrustScale, 1.0);
		Arrays.fill(rotorDynamicInflowTimeConstantSeconds, 0.0);
		Arrays.fill(rotorPropellerThrustScale, 1.0);
		Arrays.fill(rotorPropellerPowerScale, 1.0);
		Arrays.fill(rotorAxialGustThrustScale, 1.0);
		Arrays.fill(rotorCompressibilityThrustScale, 1.0);
		Arrays.fill(rotorWakeThrustScale, 1.0);
		Arrays.fill(rotorWetThrustScale, 1.0);
		Arrays.fill(rotorIcingThrustScale, 1.0);
		Arrays.fill(rotorIcingPowerScale, 1.0);
		Arrays.fill(rotorCoaxialAllocationCommandRatio, 1.0);
		repairAllRotors();
	}

	public int motorCount() {
		return motorOmegaRadiansPerSecond.length;
	}

	public Vec3 positionMeters() {
		return positionMeters;
	}

	public void setPositionMeters(Vec3 positionMeters) {
		this.positionMeters = positionMeters;
	}

	public Vec3 velocityMetersPerSecond() {
		return velocityMetersPerSecond;
	}

	public void setVelocityMetersPerSecond(Vec3 velocityMetersPerSecond) {
		this.velocityMetersPerSecond = velocityMetersPerSecond;
	}

	public Vec3 linearAccelerationWorldMetersPerSecondSquared() {
		return linearAccelerationWorldMetersPerSecondSquared;
	}

	void setLinearAccelerationWorldMetersPerSecondSquared(Vec3 linearAccelerationWorldMetersPerSecondSquared) {
		this.linearAccelerationWorldMetersPerSecondSquared = linearAccelerationWorldMetersPerSecondSquared == null ? Vec3.ZERO : linearAccelerationWorldMetersPerSecondSquared;
	}

	public double contactImpactSpeedMetersPerSecond() {
		return contactImpactSpeedMetersPerSecond;
	}

	public double contactSlipSpeedMetersPerSecond() {
		return contactSlipSpeedMetersPerSecond;
	}

	public double contactBounceSpeedMetersPerSecond() {
		return contactBounceSpeedMetersPerSecond;
	}

	public Vec3 contactAngularImpulseBodyRadiansPerSecond() {
		return contactAngularImpulseBodyRadiansPerSecond;
	}

	public double contactSurfaceFrictionMultiplier() {
		return contactSurfaceFrictionMultiplier;
	}

	public double contactSurfaceRestitutionMultiplier() {
		return contactSurfaceRestitutionMultiplier;
	}

	public double contactSurfaceScrapeMultiplier() {
		return contactSurfaceScrapeMultiplier;
	}

	public void setContactTelemetry(double impactSpeedMetersPerSecond, double slipSpeedMetersPerSecond, double bounceSpeedMetersPerSecond) {
		setContactTelemetry(impactSpeedMetersPerSecond, slipSpeedMetersPerSecond, bounceSpeedMetersPerSecond, Vec3.ZERO);
	}

	public void setContactTelemetry(
			double impactSpeedMetersPerSecond,
			double slipSpeedMetersPerSecond,
			double bounceSpeedMetersPerSecond,
			Vec3 angularImpulseBodyRadiansPerSecond
	) {
		setContactTelemetry(
				impactSpeedMetersPerSecond,
				slipSpeedMetersPerSecond,
				bounceSpeedMetersPerSecond,
				angularImpulseBodyRadiansPerSecond,
				ContactDynamics.DEFAULT_SURFACE
		);
	}

	public void setContactTelemetry(
			double impactSpeedMetersPerSecond,
			double slipSpeedMetersPerSecond,
			double bounceSpeedMetersPerSecond,
			Vec3 angularImpulseBodyRadiansPerSecond,
			ContactDynamics.ContactSurface surface
	) {
		contactImpactSpeedMetersPerSecond = nonNegativeFinite(impactSpeedMetersPerSecond);
		contactSlipSpeedMetersPerSecond = nonNegativeFinite(slipSpeedMetersPerSecond);
		contactBounceSpeedMetersPerSecond = nonNegativeFinite(bounceSpeedMetersPerSecond);
		contactAngularImpulseBodyRadiansPerSecond = finiteVectorOrZero(angularImpulseBodyRadiansPerSecond);
		setContactSurfaceTelemetry(surface);
	}

	public void setContactSurfaceTelemetry(ContactDynamics.ContactSurface surface) {
		ContactDynamics.ContactSurface contactSurface = surface == null ? ContactDynamics.DEFAULT_SURFACE : surface;
		contactSurfaceFrictionMultiplier = positiveFinite(contactSurface.frictionMultiplier(), 1.0);
		contactSurfaceRestitutionMultiplier = positiveFinite(contactSurface.restitutionMultiplier(), 1.0);
		contactSurfaceScrapeMultiplier = positiveFinite(contactSurface.scrapeMultiplier(), 1.0);
	}

	private static double nonNegativeFinite(double value) {
		return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	private static double positiveFinite(double value, double fallback) {
		return Double.isFinite(value) && value > 0.0 ? value : fallback;
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}

	private static Vec3 finiteVectorOrZero(Vec3 value) {
		return value != null && value.isFinite() ? value : Vec3.ZERO;
	}

	public Quaternion orientation() {
		return orientation;
	}

	public void setOrientation(Quaternion orientation) {
		this.orientation = orientation.normalized();
	}

	public Quaternion estimatedOrientation() {
		return estimatedOrientation;
	}

	public void setEstimatedOrientation(Quaternion estimatedOrientation) {
		this.estimatedOrientation = estimatedOrientation == null ? Quaternion.IDENTITY : estimatedOrientation.normalized();
	}

	public Vec3 attitudeEstimateErrorRadians() {
		return attitudeEstimateErrorRadians;
	}

	void setAttitudeEstimateErrorRadians(Vec3 attitudeEstimateErrorRadians) {
		this.attitudeEstimateErrorRadians = attitudeEstimateErrorRadians == null ? Vec3.ZERO : attitudeEstimateErrorRadians;
	}

	public double attitudeEstimatorAccelerometerTrust() {
		return attitudeEstimatorAccelerometerTrust;
	}

	void setAttitudeEstimatorAccelerometerTrust(double attitudeEstimatorAccelerometerTrust) {
		this.attitudeEstimatorAccelerometerTrust = MathUtil.clamp(attitudeEstimatorAccelerometerTrust, 0.0, 1.0);
	}

	public Vec3 angularVelocityBodyRadiansPerSecond() {
		return angularVelocityBodyRadiansPerSecond;
	}

	public void setAngularVelocityBodyRadiansPerSecond(Vec3 angularVelocityBodyRadiansPerSecond) {
		this.angularVelocityBodyRadiansPerSecond = angularVelocityBodyRadiansPerSecond;
	}

	public Vec3 angularAccelerationBodyRadiansPerSecondSquared() {
		return angularAccelerationBodyRadiansPerSecondSquared;
	}

	void setAngularAccelerationBodyRadiansPerSecondSquared(Vec3 angularAccelerationBodyRadiansPerSecondSquared) {
		this.angularAccelerationBodyRadiansPerSecondSquared = angularAccelerationBodyRadiansPerSecondSquared == null ? Vec3.ZERO : angularAccelerationBodyRadiansPerSecondSquared;
	}

	public Vec3 gyroAngularVelocityBodyRadiansPerSecond() {
		return gyroAngularVelocityBodyRadiansPerSecond;
	}

	void setGyroAngularVelocityBodyRadiansPerSecond(Vec3 gyroAngularVelocityBodyRadiansPerSecond) {
		this.gyroAngularVelocityBodyRadiansPerSecond = gyroAngularVelocityBodyRadiansPerSecond == null ? Vec3.ZERO : gyroAngularVelocityBodyRadiansPerSecond;
	}

	public Vec3 gyroBiasBodyRadiansPerSecond() {
		return gyroBiasBodyRadiansPerSecond;
	}

	void setGyroBiasBodyRadiansPerSecond(Vec3 gyroBiasBodyRadiansPerSecond) {
		this.gyroBiasBodyRadiansPerSecond = gyroBiasBodyRadiansPerSecond == null ? Vec3.ZERO : gyroBiasBodyRadiansPerSecond;
	}

	public double gyroClipIntensity() {
		return gyroClipIntensity;
	}

	void setGyroClipIntensity(double gyroClipIntensity) {
		this.gyroClipIntensity = MathUtil.clamp(gyroClipIntensity, 0.0, 1.0);
	}

	public double gyroDynamicNotchFrequencyHertz() {
		return gyroDynamicNotchFrequencyHertz;
	}

	void setGyroDynamicNotchFrequencyHertz(double gyroDynamicNotchFrequencyHertz) {
		this.gyroDynamicNotchFrequencyHertz = Math.max(0.0, gyroDynamicNotchFrequencyHertz);
	}

	public double gyroDynamicNotchAttenuation() {
		return gyroDynamicNotchAttenuation;
	}

	void setGyroDynamicNotchAttenuation(double gyroDynamicNotchAttenuation) {
		this.gyroDynamicNotchAttenuation = MathUtil.clamp(gyroDynamicNotchAttenuation, 0.0, 1.0);
	}

	public double gyroDynamicNotchSpreadHertz() {
		return gyroDynamicNotchSpreadHertz;
	}

	void setGyroDynamicNotchSpreadHertz(double gyroDynamicNotchSpreadHertz) {
		this.gyroDynamicNotchSpreadHertz = Math.max(0.0, gyroDynamicNotchSpreadHertz);
	}

	public double gyroRpmHarmonicNotchAttenuation() {
		return gyroRpmHarmonicNotchAttenuation;
	}

	void setGyroRpmHarmonicNotchAttenuation(double gyroRpmHarmonicNotchAttenuation) {
		this.gyroRpmHarmonicNotchAttenuation = MathUtil.clamp(gyroRpmHarmonicNotchAttenuation, 0.0, 1.0);
	}

	public double gyroBladePassNotchFrequencyHertz() {
		return gyroBladePassNotchFrequencyHertz;
	}

	void setGyroBladePassNotchFrequencyHertz(double gyroBladePassNotchFrequencyHertz) {
		this.gyroBladePassNotchFrequencyHertz = Math.max(0.0, gyroBladePassNotchFrequencyHertz);
	}

	public double gyroBladePassNotchAttenuation() {
		return gyroBladePassNotchAttenuation;
	}

	void setGyroBladePassNotchAttenuation(double gyroBladePassNotchAttenuation) {
		this.gyroBladePassNotchAttenuation = MathUtil.clamp(gyroBladePassNotchAttenuation, 0.0, 1.0);
	}

	public double gyroBladePassNotchSpreadHertz() {
		return gyroBladePassNotchSpreadHertz;
	}

	void setGyroBladePassNotchSpreadHertz(double gyroBladePassNotchSpreadHertz) {
		this.gyroBladePassNotchSpreadHertz = Math.max(0.0, gyroBladePassNotchSpreadHertz);
	}

	public Vec3 accelerometerBodyMetersPerSecondSquared() {
		return accelerometerBodyMetersPerSecondSquared;
	}

	void setAccelerometerBodyMetersPerSecondSquared(Vec3 accelerometerBodyMetersPerSecondSquared) {
		this.accelerometerBodyMetersPerSecondSquared = accelerometerBodyMetersPerSecondSquared == null ? Vec3.ZERO : accelerometerBodyMetersPerSecondSquared;
	}

	public Vec3 accelerometerBiasBodyMetersPerSecondSquared() {
		return accelerometerBiasBodyMetersPerSecondSquared;
	}

	void setAccelerometerBiasBodyMetersPerSecondSquared(Vec3 accelerometerBiasBodyMetersPerSecondSquared) {
		this.accelerometerBiasBodyMetersPerSecondSquared = accelerometerBiasBodyMetersPerSecondSquared == null ? Vec3.ZERO : accelerometerBiasBodyMetersPerSecondSquared;
	}

	public double accelerometerClipIntensity() {
		return accelerometerClipIntensity;
	}

	void setAccelerometerClipIntensity(double accelerometerClipIntensity) {
		this.accelerometerClipIntensity = MathUtil.clamp(accelerometerClipIntensity, 0.0, 1.0);
	}

	public double imuSupplyNoiseIntensity() {
		return imuSupplyNoiseIntensity;
	}

	void setImuSupplyNoiseIntensity(double imuSupplyNoiseIntensity) {
		this.imuSupplyNoiseIntensity = MathUtil.clamp(imuSupplyNoiseIntensity, 0.0, 1.6);
	}

	public double barometerAltitudeMeters() {
		return barometerAltitudeMeters;
	}

	void setBarometerAltitudeMeters(double barometerAltitudeMeters) {
		this.barometerAltitudeMeters = Double.isFinite(barometerAltitudeMeters) ? barometerAltitudeMeters : positionMeters.y();
	}

	public double barometerVerticalSpeedMetersPerSecond() {
		return barometerVerticalSpeedMetersPerSecond;
	}

	void setBarometerVerticalSpeedMetersPerSecond(double barometerVerticalSpeedMetersPerSecond) {
		this.barometerVerticalSpeedMetersPerSecond = Double.isFinite(barometerVerticalSpeedMetersPerSecond) ? barometerVerticalSpeedMetersPerSecond : 0.0;
	}

	public double barometerPressureHectopascals() {
		return barometerPressureHectopascals;
	}

	void setBarometerPressureHectopascals(double barometerPressureHectopascals) {
		this.barometerPressureHectopascals = Double.isFinite(barometerPressureHectopascals) ? Math.max(0.0, barometerPressureHectopascals) : 1013.25;
	}

	public double barometerErrorMeters() {
		return barometerErrorMeters;
	}

	void setBarometerErrorMeters(double barometerErrorMeters) {
		this.barometerErrorMeters = Double.isFinite(barometerErrorMeters) ? barometerErrorMeters : 0.0;
	}

	public double barometerSensorNoiseMeters() {
		return barometerSensorNoiseMeters;
	}

	void setBarometerSensorNoiseMeters(double barometerSensorNoiseMeters) {
		this.barometerSensorNoiseMeters = Double.isFinite(barometerSensorNoiseMeters) ? barometerSensorNoiseMeters : 0.0;
	}

	public double barometerPressurePortErrorMeters() {
		return barometerPressurePortErrorMeters;
	}

	void setBarometerPressurePortErrorMeters(double barometerPressurePortErrorMeters) {
		this.barometerPressurePortErrorMeters = Double.isFinite(barometerPressurePortErrorMeters) ? barometerPressurePortErrorMeters : 0.0;
	}

	public double barometerPropwashErrorMeters() {
		return barometerPropwashErrorMeters;
	}

	void setBarometerPropwashErrorMeters(double barometerPropwashErrorMeters) {
		this.barometerPropwashErrorMeters = Double.isFinite(barometerPropwashErrorMeters) ? barometerPropwashErrorMeters : 0.0;
	}

	public FlightMode flightMode() {
		return flightMode;
	}

	void setFlightMode(FlightMode flightMode) {
		this.flightMode = flightMode == null ? FlightMode.DEFAULT_FIRST_FLIGHT : flightMode;
	}

	public Vec3 levelTargetAttitudeRadians() {
		return levelTargetAttitudeRadians;
	}

	void setLevelTargetAttitudeRadians(Vec3 levelTargetAttitudeRadians) {
		this.levelTargetAttitudeRadians = levelTargetAttitudeRadians == null ? Vec3.ZERO : levelTargetAttitudeRadians;
	}

	public Vec3 levelAttitudeErrorRadians() {
		return levelAttitudeErrorRadians;
	}

	void setLevelAttitudeErrorRadians(Vec3 levelAttitudeErrorRadians) {
		this.levelAttitudeErrorRadians = levelAttitudeErrorRadians == null ? Vec3.ZERO : levelAttitudeErrorRadians;
	}

	public double selfLevelBlend() {
		return selfLevelBlend;
	}

	void setSelfLevelBlend(double selfLevelBlend) {
		this.selfLevelBlend = MathUtil.clamp(selfLevelBlend, 0.0, 1.0);
	}

	public Vec3 targetRatesBodyRadiansPerSecond() {
		return targetRatesBodyRadiansPerSecond;
	}

	void setTargetRatesBodyRadiansPerSecond(Vec3 targetRatesBodyRadiansPerSecond) {
		this.targetRatesBodyRadiansPerSecond = targetRatesBodyRadiansPerSecond == null ? Vec3.ZERO : targetRatesBodyRadiansPerSecond;
	}

	public Vec3 rateErrorBodyRadiansPerSecond() {
		return rateErrorBodyRadiansPerSecond;
	}

	void setRateErrorBodyRadiansPerSecond(Vec3 rateErrorBodyRadiansPerSecond) {
		this.rateErrorBodyRadiansPerSecond = rateErrorBodyRadiansPerSecond == null ? Vec3.ZERO : rateErrorBodyRadiansPerSecond;
	}

	public Vec3 pidProportionalTorqueBodyNewtonMeters() {
		return pidProportionalTorqueBodyNewtonMeters;
	}

	void setPidProportionalTorqueBodyNewtonMeters(Vec3 pidProportionalTorqueBodyNewtonMeters) {
		this.pidProportionalTorqueBodyNewtonMeters = pidProportionalTorqueBodyNewtonMeters == null ? Vec3.ZERO : pidProportionalTorqueBodyNewtonMeters;
	}

	public Vec3 pidIntegralTorqueBodyNewtonMeters() {
		return pidIntegralTorqueBodyNewtonMeters;
	}

	void setPidIntegralTorqueBodyNewtonMeters(Vec3 pidIntegralTorqueBodyNewtonMeters) {
		this.pidIntegralTorqueBodyNewtonMeters = pidIntegralTorqueBodyNewtonMeters == null ? Vec3.ZERO : pidIntegralTorqueBodyNewtonMeters;
	}

	public Vec3 pidDerivativeTorqueBodyNewtonMeters() {
		return pidDerivativeTorqueBodyNewtonMeters;
	}

	void setPidDerivativeTorqueBodyNewtonMeters(Vec3 pidDerivativeTorqueBodyNewtonMeters) {
		this.pidDerivativeTorqueBodyNewtonMeters = pidDerivativeTorqueBodyNewtonMeters == null ? Vec3.ZERO : pidDerivativeTorqueBodyNewtonMeters;
	}

	public Vec3 pidFeedForwardTorqueBodyNewtonMeters() {
		return pidFeedForwardTorqueBodyNewtonMeters;
	}

	void setPidFeedForwardTorqueBodyNewtonMeters(Vec3 pidFeedForwardTorqueBodyNewtonMeters) {
		this.pidFeedForwardTorqueBodyNewtonMeters = pidFeedForwardTorqueBodyNewtonMeters == null ? Vec3.ZERO : pidFeedForwardTorqueBodyNewtonMeters;
	}

	public Vec3 pidOutputTorqueBodyNewtonMeters() {
		return pidOutputTorqueBodyNewtonMeters;
	}

	void setPidOutputTorqueBodyNewtonMeters(Vec3 pidOutputTorqueBodyNewtonMeters) {
		this.pidOutputTorqueBodyNewtonMeters = pidOutputTorqueBodyNewtonMeters == null ? Vec3.ZERO : pidOutputTorqueBodyNewtonMeters;
	}

	public double pidIntegralRelax() {
		return pidIntegralRelax;
	}

	void setPidIntegralRelax(double pidIntegralRelax) {
		this.pidIntegralRelax = MathUtil.clamp(pidIntegralRelax, 0.0, 1.0);
		this.pidIntegralRelaxAxes = new Vec3(this.pidIntegralRelax, this.pidIntegralRelax, this.pidIntegralRelax);
	}

	public Vec3 pidIntegralRelaxAxes() {
		return pidIntegralRelaxAxes;
	}

	void setPidIntegralRelaxAxes(Vec3 pidIntegralRelaxAxes) {
		this.pidIntegralRelaxAxes = pidIntegralRelaxAxes == null || !pidIntegralRelaxAxes.isFinite()
				? Vec3.ZERO
				: pidIntegralRelaxAxes.clamp(0.0, 1.0);
		this.pidIntegralRelax = Math.max(
				this.pidIntegralRelaxAxes.x(),
				Math.max(this.pidIntegralRelaxAxes.y(), this.pidIntegralRelaxAxes.z())
		);
	}

	public double pidDTermLowPassCutoffHertz() {
		return pidDTermLowPassCutoffHertz;
	}

	void setPidDTermLowPassCutoffHertz(double pidDTermLowPassCutoffHertz) {
		this.pidDTermLowPassCutoffHertz = MathUtil.clamp(pidDTermLowPassCutoffHertz, 0.0, 1000.0);
	}

	public DroneInput rawControlInput() {
		return rawControlInput;
	}

	void setRawControlInput(DroneInput rawControlInput) {
		this.rawControlInput = rawControlInput == null ? DroneInput.idle() : rawControlInput.normalized();
	}

	public DroneInput processedControlInput() {
		return processedControlInput;
	}

	void setProcessedControlInput(DroneInput processedControlInput) {
		this.processedControlInput = processedControlInput == null ? DroneInput.idle() : processedControlInput.normalized();
	}

	public double controlLinkLossSeconds() {
		return controlLinkLossSeconds;
	}

	void setControlLinkLossSeconds(double controlLinkLossSeconds) {
		this.controlLinkLossSeconds = Math.max(0.0, controlLinkLossSeconds);
	}

	public boolean controlFailsafeActive() {
		return controlFailsafeActive;
	}

	void setControlFailsafeActive(boolean controlFailsafeActive) {
		this.controlFailsafeActive = controlFailsafeActive;
	}

	public double controlFrameAgeSeconds() {
		return controlFrameAgeSeconds;
	}

	public double controlFrameIntervalSeconds() {
		return controlFrameIntervalSeconds;
	}

	public double controlFrameError() {
		return controlFrameError;
	}

	void setControlFrameTelemetry(double ageSeconds, double intervalSeconds, double frameError) {
		controlFrameAgeSeconds = nonNegativeFinite(ageSeconds);
		controlFrameIntervalSeconds = nonNegativeFinite(intervalSeconds);
		controlFrameError = nonNegativeFinite(frameError);
	}

	public double motorOmegaRadiansPerSecond(int index) {
		return motorOmegaRadiansPerSecond[index];
	}

	public double[] motorOmegaRadiansPerSecond() {
		return Arrays.copyOf(motorOmegaRadiansPerSecond, motorOmegaRadiansPerSecond.length);
	}

	public double motorRpm(int index) {
		return Math.max(0.0, motorOmegaRadiansPerSecond[index] * RPM_PER_RADIAN_PER_SECOND);
	}

	public double[] motorRpm() {
		double[] rpm = new double[motorOmegaRadiansPerSecond.length];
		for (int i = 0; i < rpm.length; i++) {
			rpm[i] = motorRpm(i);
		}
		return rpm;
	}

	public double averageMotorRpm() {
		double sum = 0.0;
		for (int i = 0; i < motorOmegaRadiansPerSecond.length; i++) {
			sum += motorRpm(i);
		}
		return sum / motorOmegaRadiansPerSecond.length;
	}

	void setMotorOmegaRadiansPerSecond(int index, double value) {
		motorOmegaRadiansPerSecond[index] = value;
	}

	public double motorRpmTelemetryRpm(int index) {
		return Math.max(0.0, motorRpmTelemetryOmegaRadiansPerSecond[index] * RPM_PER_RADIAN_PER_SECOND);
	}

	public double[] motorRpmTelemetryRpm() {
		double[] rpm = new double[motorRpmTelemetryOmegaRadiansPerSecond.length];
		for (int i = 0; i < rpm.length; i++) {
			rpm[i] = motorRpmTelemetryRpm(i);
		}
		return rpm;
	}

	public double averageMotorRpmTelemetryRpm() {
		double sum = 0.0;
		int validCount = 0;
		for (int i = 0; i < motorRpmTelemetryOmegaRadiansPerSecond.length; i++) {
			if (motorRpmTelemetryValidity[i] >= 0.5) {
				sum += motorRpmTelemetryRpm(i);
				validCount++;
			}
		}
		return validCount == 0 ? 0.0 : sum / validCount;
	}

	public double motorRpmTelemetryValidity(int index) {
		return motorRpmTelemetryValidity[index];
	}

	public double[] motorRpmTelemetryValidity() {
		return Arrays.copyOf(motorRpmTelemetryValidity, motorRpmTelemetryValidity.length);
	}

	public double averageMotorRpmTelemetryValidity() {
		if (motorRpmTelemetryValidity.length == 0) {
			return 0.0;
		}
		double sum = 0.0;
		for (double validity : motorRpmTelemetryValidity) {
			sum += validity;
		}
		return sum / motorRpmTelemetryValidity.length;
	}

	void setMotorRpmTelemetry(int index, double omegaRadiansPerSecond, double validity) {
		motorRpmTelemetryOmegaRadiansPerSecond[index] = Double.isFinite(omegaRadiansPerSecond)
				? Math.max(0.0, omegaRadiansPerSecond)
				: 0.0;
		motorRpmTelemetryValidity[index] = Double.isFinite(validity)
				? MathUtil.clamp(validity, 0.0, 1.0)
				: 0.0;
	}

	public double motorAngularAccelerationRadiansPerSecondSquared(int index) {
		return motorAngularAccelerationRadiansPerSecondSquared[index];
	}

	public double[] motorAngularAccelerationRadiansPerSecondSquared() {
		return Arrays.copyOf(motorAngularAccelerationRadiansPerSecondSquared, motorAngularAccelerationRadiansPerSecondSquared.length);
	}

	void setMotorAngularAccelerationRadiansPerSecondSquared(int index, double value) {
		motorAngularAccelerationRadiansPerSecondSquared[index] = Double.isFinite(value) ? value : 0.0;
	}

	public double averageMotorAngularAccelerationRadiansPerSecondSquared() {
		double sum = 0.0;
		for (double acceleration : motorAngularAccelerationRadiansPerSecondSquared) {
			sum += acceleration;
		}
		return sum / motorAngularAccelerationRadiansPerSecondSquared.length;
	}

	public double motorAerodynamicTorqueNewtonMeters(int index) {
		return motorAerodynamicTorqueNewtonMeters[index];
	}

	public double[] motorAerodynamicTorqueNewtonMeters() {
		return Arrays.copyOf(motorAerodynamicTorqueNewtonMeters, motorAerodynamicTorqueNewtonMeters.length);
	}

	void setMotorAerodynamicTorqueNewtonMeters(int index, double value) {
		motorAerodynamicTorqueNewtonMeters[index] = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	public double averageMotorAerodynamicTorqueNewtonMeters() {
		double sum = 0.0;
		for (double torque : motorAerodynamicTorqueNewtonMeters) {
			sum += torque;
		}
		return sum / motorAerodynamicTorqueNewtonMeters.length;
	}

	public double motorMechanicalLossTorqueNewtonMeters(int index) {
		return motorMechanicalLossTorqueNewtonMeters[index];
	}

	public double[] motorMechanicalLossTorqueNewtonMeters() {
		return Arrays.copyOf(motorMechanicalLossTorqueNewtonMeters, motorMechanicalLossTorqueNewtonMeters.length);
	}

	void setMotorMechanicalLossTorqueNewtonMeters(int index, double value) {
		motorMechanicalLossTorqueNewtonMeters[index] = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	public double averageMotorMechanicalLossTorqueNewtonMeters() {
		double sum = 0.0;
		for (double torque : motorMechanicalLossTorqueNewtonMeters) {
			sum += torque;
		}
		return sum / motorMechanicalLossTorqueNewtonMeters.length;
	}

	public double motorTorqueRippleNewtonMeters(int index) {
		return motorTorqueRippleNewtonMeters[index];
	}

	public double[] motorTorqueRippleNewtonMeters() {
		return Arrays.copyOf(motorTorqueRippleNewtonMeters, motorTorqueRippleNewtonMeters.length);
	}

	void setMotorTorqueRippleNewtonMeters(int index, double value) {
		motorTorqueRippleNewtonMeters[index] = Double.isFinite(value) ? value : 0.0;
	}

	public double averageMotorTorqueRippleNewtonMeters() {
		double sum = 0.0;
		for (double torque : motorTorqueRippleNewtonMeters) {
			sum += Math.abs(torque);
		}
		return sum / motorTorqueRippleNewtonMeters.length;
	}

	public double motorShaftPowerWatts(int index) {
		return motorShaftPowerWatts[index];
	}

	public double[] motorShaftPowerWatts() {
		return Arrays.copyOf(motorShaftPowerWatts, motorShaftPowerWatts.length);
	}

	void setMotorShaftPowerWatts(int index, double value) {
		motorShaftPowerWatts[index] = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	public double averageMotorShaftPowerWatts() {
		double sum = 0.0;
		for (double power : motorShaftPowerWatts) {
			sum += power;
		}
		return sum / motorShaftPowerWatts.length;
	}

	public double motorPhaseCurrentAmps(int index) {
		return motorPhaseCurrentAmps[index];
	}

	public double[] motorPhaseCurrentAmps() {
		return Arrays.copyOf(motorPhaseCurrentAmps, motorPhaseCurrentAmps.length);
	}

	void setMotorPhaseCurrentAmps(int index, double value) {
		motorPhaseCurrentAmps[index] = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	public double averageMotorPhaseCurrentAmps() {
		double sum = 0.0;
		for (double current : motorPhaseCurrentAmps) {
			sum += current;
		}
		return sum / motorPhaseCurrentAmps.length;
	}

	public double motorCurrentRippleAmps(int index) {
		return motorCurrentRippleAmps[index];
	}

	public double[] motorCurrentRippleAmps() {
		return Arrays.copyOf(motorCurrentRippleAmps, motorCurrentRippleAmps.length);
	}

	void setMotorCurrentRippleAmps(int index, double value) {
		motorCurrentRippleAmps[index] = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	public double averageMotorCurrentRippleAmps() {
		double sum = 0.0;
		for (double current : motorCurrentRippleAmps) {
			sum += current;
		}
		return sum / motorCurrentRippleAmps.length;
	}

	public double motorElectricalEfficiency(int index) {
		return motorElectricalEfficiency[index];
	}

	public double[] motorElectricalEfficiency() {
		return Arrays.copyOf(motorElectricalEfficiency, motorElectricalEfficiency.length);
	}

	void setMotorElectricalEfficiency(int index, double value) {
		motorElectricalEfficiency[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
	}

	public double averageMotorElectricalEfficiency() {
		double sum = 0.0;
		for (double efficiency : motorElectricalEfficiency) {
			sum += efficiency;
		}
		return sum / motorElectricalEfficiency.length;
	}

	public double minMotorElectricalEfficiency() {
		double min = 1.0;
		for (double efficiency : motorElectricalEfficiency) {
			if (efficiency > 1.0e-9) {
				min = Math.min(min, efficiency);
			}
		}
		return min;
	}

	public double motorVoltageHeadroom(int index) {
		return motorVoltageHeadroom[index];
	}

	public double[] motorVoltageHeadroom() {
		return Arrays.copyOf(motorVoltageHeadroom, motorVoltageHeadroom.length);
	}

	void setMotorVoltageHeadroom(int index, double value) {
		motorVoltageHeadroom[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
	}

	public double averageMotorVoltageHeadroom() {
		double sum = 0.0;
		for (double headroom : motorVoltageHeadroom) {
			sum += headroom;
		}
		return sum / motorVoltageHeadroom.length;
	}

	public double minMotorVoltageHeadroom() {
		double min = 1.0;
		for (double headroom : motorVoltageHeadroom) {
			min = Math.min(min, headroom);
		}
		return min;
	}

	public double motorWindingResistanceScale(int index) {
		return motorWindingResistanceScale[index];
	}

	public double[] motorWindingResistanceScale() {
		return Arrays.copyOf(motorWindingResistanceScale, motorWindingResistanceScale.length);
	}

	void setMotorWindingResistanceScale(int index, double value) {
		motorWindingResistanceScale[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.72, 1.90) : 1.0;
	}

	public double averageMotorWindingResistanceScale() {
		double sum = 0.0;
		for (double scale : motorWindingResistanceScale) {
			sum += scale;
		}
		return sum / motorWindingResistanceScale.length;
	}

	public double maxMotorWindingResistanceScale() {
		double max = 1.0;
		for (double scale : motorWindingResistanceScale) {
			max = Math.max(max, scale);
		}
		return max;
	}

	public double motorCommutationRippleIntensity(int index) {
		return motorCommutationRippleIntensity[index];
	}

	public double[] motorCommutationRippleIntensity() {
		return Arrays.copyOf(motorCommutationRippleIntensity, motorCommutationRippleIntensity.length);
	}

	void setMotorCommutationRippleIntensity(int index, double value) {
		motorCommutationRippleIntensity[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
	}

	public double averageMotorCommutationRippleIntensity() {
		double sum = 0.0;
		for (double intensity : motorCommutationRippleIntensity) {
			sum += intensity;
		}
		return sum / motorCommutationRippleIntensity.length;
	}

	public double maxMotorCommutationRippleIntensity() {
		double max = 0.0;
		for (double intensity : motorCommutationRippleIntensity) {
			max = Math.max(max, intensity);
		}
		return max;
	}

	public double escOutputCommand(int index) {
		return escOutputCommand[index];
	}

	public double[] escOutputCommand() {
		return Arrays.copyOf(escOutputCommand, escOutputCommand.length);
	}

	void setEscOutputCommand(int index, double value) {
		escOutputCommand[index] = MathUtil.clamp(value, 0.0, 1.0);
	}

	public double escElectricalOutputCommand(int index) {
		return escElectricalOutputCommand[index];
	}

	public double[] escElectricalOutputCommand() {
		return Arrays.copyOf(escElectricalOutputCommand, escElectricalOutputCommand.length);
	}

	void setEscElectricalOutputCommand(int index, double value) {
		escElectricalOutputCommand[index] = MathUtil.clamp(value, 0.0, 1.0);
	}

	public double escElectricalOutputError(int index) {
		return escElectricalOutputError[index];
	}

	public double[] escElectricalOutputError() {
		return Arrays.copyOf(escElectricalOutputError, escElectricalOutputError.length);
	}

	void setEscElectricalOutputError(int index, double value) {
		escElectricalOutputError[index] = nonNegativeFinite(value);
	}

	public double escDesyncIntensity(int index) {
		return escDesyncIntensity[index];
	}

	public double[] escDesyncIntensity() {
		return Arrays.copyOf(escDesyncIntensity, escDesyncIntensity.length);
	}

	void setEscDesyncIntensity(int index, double value) {
		escDesyncIntensity[index] = MathUtil.clamp(value, 0.0, 1.0);
	}

	public double maxEscDesyncIntensity() {
		double max = 0.0;
		for (double intensity : escDesyncIntensity) {
			max = Math.max(max, intensity);
		}
		return max;
	}

	public double averageEscOutputCommand() {
		double sum = 0.0;
		for (double output : escOutputCommand) {
			sum += output;
		}
		return sum / escOutputCommand.length;
	}

	public double averageEscElectricalOutputCommand() {
		double sum = 0.0;
		for (double output : escElectricalOutputCommand) {
			sum += output;
		}
		return sum / escElectricalOutputCommand.length;
	}

	public double averageEscElectricalOutputError() {
		double sum = 0.0;
		for (double error : escElectricalOutputError) {
			sum += error;
		}
		return sum / escElectricalOutputError.length;
	}

	public double maxEscElectricalOutputError() {
		double max = 0.0;
		for (double error : escElectricalOutputError) {
			max = Math.max(max, error);
		}
		return max;
	}

	public double escCommandFrameAgeSeconds() {
		return escCommandFrameAgeSeconds;
	}

	public double escCommandFrameIntervalSeconds() {
		return escCommandFrameIntervalSeconds;
	}

	public double escCommandError() {
		return escCommandError;
	}

	void setEscCommandTelemetry(double frameAgeSeconds, double frameIntervalSeconds, double commandError) {
		escCommandFrameAgeSeconds = nonNegativeFinite(frameAgeSeconds);
		escCommandFrameIntervalSeconds = nonNegativeFinite(frameIntervalSeconds);
		escCommandError = nonNegativeFinite(commandError);
	}

	public double escTemperatureCelsius(int index) {
		return escTemperatureCelsius[index];
	}

	public double[] escTemperatureCelsius() {
		return Arrays.copyOf(escTemperatureCelsius, escTemperatureCelsius.length);
	}

	void setEscTemperatureCelsius(int index, double value) {
		escTemperatureCelsius[index] = Double.isFinite(value) ? MathUtil.clamp(value, -40.0, 260.0) : 25.0;
	}

	public double averageEscTemperatureCelsius() {
		double sum = 0.0;
		for (double temperature : escTemperatureCelsius) {
			sum += temperature;
		}
		return sum / escTemperatureCelsius.length;
	}

	public double maxEscTemperatureCelsius() {
		double max = 25.0;
		for (double temperature : escTemperatureCelsius) {
			max = Math.max(max, temperature);
		}
		return max;
	}

	public double escCoolingFactor(int index) {
		return escCoolingFactor[index];
	}

	public double[] escCoolingFactor() {
		return Arrays.copyOf(escCoolingFactor, escCoolingFactor.length);
	}

	void setEscCoolingFactor(int index, double value) {
		escCoolingFactor[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.20, 4.0) : 1.0;
	}

	public double averageEscCoolingFactor() {
		double sum = 0.0;
		for (double factor : escCoolingFactor) {
			sum += factor;
		}
		return sum / escCoolingFactor.length;
	}

	public double escThermalLimit(int index) {
		return escThermalLimitByEsc[index];
	}

	public double[] escThermalLimitByEsc() {
		return Arrays.copyOf(escThermalLimitByEsc, escThermalLimitByEsc.length);
	}

	void setEscThermalLimit(int index, double value) {
		escThermalLimitByEsc[index] = MathUtil.clamp(value, 0.0, 1.0);
	}

	public double minEscThermalLimit() {
		double min = 1.0;
		for (double limit : escThermalLimitByEsc) {
			min = Math.min(min, limit);
		}
		return min;
	}

	public double motorCurrentAmps(int index) {
		return motorCurrentAmps[index];
	}

	public double[] motorCurrentAmps() {
		return Arrays.copyOf(motorCurrentAmps, motorCurrentAmps.length);
	}

	void setMotorCurrentAmps(int index, double value) {
		motorCurrentAmps[index] = Math.max(0.0, value);
	}

	public double motorRegenerativeCurrentAmps(int index) {
		return motorRegenerativeCurrentAmps[index];
	}

	public double[] motorRegenerativeCurrentAmps() {
		return Arrays.copyOf(motorRegenerativeCurrentAmps, motorRegenerativeCurrentAmps.length);
	}

	void setMotorRegenerativeCurrentAmps(int index, double value) {
		motorRegenerativeCurrentAmps[index] = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	public double averageMotorCurrentAmps() {
		double sum = 0.0;
		for (double current : motorCurrentAmps) {
			sum += current;
		}
		return sum / motorCurrentAmps.length;
	}

	public double averageMotorRegenerativeCurrentAmps() {
		double sum = 0.0;
		for (double current : motorRegenerativeCurrentAmps) {
			sum += current;
		}
		return sum / motorRegenerativeCurrentAmps.length;
	}

	public double motorTargetOmegaRadiansPerSecond(int index) {
		return motorTargetOmegaRadiansPerSecond[index];
	}

	public double[] motorTargetOmegaRadiansPerSecond() {
		return Arrays.copyOf(motorTargetOmegaRadiansPerSecond, motorTargetOmegaRadiansPerSecond.length);
	}

	void setMotorTargetOmegaRadiansPerSecond(int index, double value) {
		motorTargetOmegaRadiansPerSecond[index] = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	public double motorTargetRpm(int index) {
		return motorTargetOmegaRadiansPerSecond[index] * RPM_PER_RADIAN_PER_SECOND;
	}

	public double averageMotorTargetRpm() {
		double sum = 0.0;
		for (double omega : motorTargetOmegaRadiansPerSecond) {
			sum += omega * RPM_PER_RADIAN_PER_SECOND;
		}
		return sum / motorTargetOmegaRadiansPerSecond.length;
	}

	public double motorTrackingError(int index) {
		return motorTrackingError[index];
	}

	public double[] motorTrackingError() {
		return Arrays.copyOf(motorTrackingError, motorTrackingError.length);
	}

	void setMotorTrackingError(int index, double value) {
		motorTrackingError[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.5) : 0.0;
	}

	public double averageMotorTrackingError() {
		double sum = 0.0;
		for (double error : motorTrackingError) {
			sum += error;
		}
		return sum / motorTrackingError.length;
	}

	public double maxMotorTrackingError() {
		double max = 0.0;
		for (double error : motorTrackingError) {
			max = Math.max(max, error);
		}
		return max;
	}

	public double motorActuatorAuthority(int index) {
		return motorActuatorAuthority[index];
	}

	public double[] motorActuatorAuthority() {
		return Arrays.copyOf(motorActuatorAuthority, motorActuatorAuthority.length);
	}

	void setMotorActuatorAuthority(int index, double value) {
		motorActuatorAuthority[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 1.0;
	}

	public double averageMotorActuatorAuthority() {
		double sum = 0.0;
		for (double authority : motorActuatorAuthority) {
			sum += authority;
		}
		return sum / motorActuatorAuthority.length;
	}

	public double minMotorActuatorAuthority() {
		double min = 1.0;
		for (double authority : motorActuatorAuthority) {
			min = Math.min(min, authority);
		}
		return min;
	}

	public double maxMotorCurrentAmps() {
		double max = 0.0;
		for (double current : motorCurrentAmps) {
			max = Math.max(max, current);
		}
		return max;
	}

	public double maxMotorRegenerativeCurrentAmps() {
		double max = 0.0;
		for (double current : motorRegenerativeCurrentAmps) {
			max = Math.max(max, current);
		}
		return max;
	}

	public double motorTemperatureCelsius(int index) {
		return motorTemperatureCelsius[index];
	}

	public double[] motorTemperatureCelsius() {
		return Arrays.copyOf(motorTemperatureCelsius, motorTemperatureCelsius.length);
	}

	void setMotorTemperatureCelsius(int index, double value) {
		motorTemperatureCelsius[index] = Double.isFinite(value) ? MathUtil.clamp(value, -40.0, 280.0) : 25.0;
	}

	public double motorCoolingFactor(int index) {
		return motorCoolingFactor[index];
	}

	public double[] motorCoolingFactor() {
		return Arrays.copyOf(motorCoolingFactor, motorCoolingFactor.length);
	}

	void setMotorCoolingFactor(int index, double value) {
		motorCoolingFactor[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.20, 4.0) : 1.0;
	}

	public double rotorA4mcVentilationEfficiency(int index) {
		return rotorA4mcVentilationEfficiency[index];
	}

	public double[] rotorA4mcVentilationEfficiency() {
		return Arrays.copyOf(rotorA4mcVentilationEfficiency, rotorA4mcVentilationEfficiency.length);
	}

	void setRotorA4mcVentilationEfficiency(int index, double value) {
		rotorA4mcVentilationEfficiency[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 1.0;
	}

	public double minRotorA4mcVentilationEfficiency() {
		double min = 1.0;
		for (double efficiency : rotorA4mcVentilationEfficiency) {
			min = Math.min(min, efficiency);
		}
		return min;
	}

	public boolean rotorCtCpJReferenceAvailable(int index) {
		return rotorCtCpJReferenceAvailable[index];
	}

	public boolean[] rotorCtCpJReferenceAvailable() {
		return Arrays.copyOf(rotorCtCpJReferenceAvailable, rotorCtCpJReferenceAvailable.length);
	}

	public boolean rotorCtCpJReferenceBlocked(int index) {
		return rotorCtCpJReferenceBlocked[index];
	}

	public boolean[] rotorCtCpJReferenceBlocked() {
		return Arrays.copyOf(rotorCtCpJReferenceBlocked, rotorCtCpJReferenceBlocked.length);
	}

	public boolean rotorCtCpJReferenceClamped(int index) {
		return rotorCtCpJReferenceClamped[index];
	}

	public boolean[] rotorCtCpJReferenceClamped() {
		return Arrays.copyOf(rotorCtCpJReferenceClamped, rotorCtCpJReferenceClamped.length);
	}

	public boolean rotorCtCpJReferenceRuntimeApplied(int index) {
		return rotorCtCpJReferenceRuntimeApplied[index];
	}

	public boolean[] rotorCtCpJReferenceRuntimeApplied() {
		return Arrays.copyOf(rotorCtCpJReferenceRuntimeApplied, rotorCtCpJReferenceRuntimeApplied.length);
	}

	public PropellerArchiveCtCpJRotorForceModel.RuntimeForceReplacementStatus rotorCtCpJReferenceRuntimeStatus(
			int index
	) {
		PropellerArchiveCtCpJRotorForceModel.RuntimeForceReplacementStatus[] values =
				PropellerArchiveCtCpJRotorForceModel.RuntimeForceReplacementStatus.values();
		int ordinal = rotorCtCpJReferenceRuntimeStatusOrdinal[index];
		if (ordinal < 0 || ordinal >= values.length) {
			return PropellerArchiveCtCpJRotorForceModel.RuntimeForceReplacementStatus.BLOCKED;
		}
		return values[ordinal];
	}

	public PropellerArchiveCtCpJRotorForceModel.RuntimeForceReplacementStatus[] rotorCtCpJReferenceRuntimeStatus() {
		PropellerArchiveCtCpJRotorForceModel.RuntimeForceReplacementStatus[] statuses =
				new PropellerArchiveCtCpJRotorForceModel.RuntimeForceReplacementStatus[
						rotorCtCpJReferenceRuntimeStatusOrdinal.length];
		for (int i = 0; i < statuses.length; i++) {
			statuses[i] = rotorCtCpJReferenceRuntimeStatus(i);
		}
		return statuses;
	}

	public PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus rotorCtCpJReferenceInterpolationStatus(int index) {
		PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus[] values =
				PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.values();
		int ordinal = rotorCtCpJReferenceInterpolationStatusOrdinal[index];
		if (ordinal < 0 || ordinal >= values.length) {
			return PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BLOCKED;
		}
		return values[ordinal];
	}

	public PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode rotorCtCpJReferenceLookupStatusCode(int index) {
		PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode[] values =
				PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.values();
		int ordinal = rotorCtCpJReferenceLookupStatusCodeOrdinal[index];
		if (ordinal < 0 || ordinal >= values.length) {
			return PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.UNKNOWN;
		}
		return values[ordinal];
	}

	public double rotorCtCpJReferenceAdvanceRatioJ(int index) {
		return rotorCtCpJReferenceAdvanceRatioJ[index];
	}

	public double[] rotorCtCpJReferenceAdvanceRatioJ() {
		return Arrays.copyOf(rotorCtCpJReferenceAdvanceRatioJ, rotorCtCpJReferenceAdvanceRatioJ.length);
	}

	public double rotorCtCpJReferenceRpm(int index) {
		return rotorCtCpJReferenceRpm[index];
	}

	public double[] rotorCtCpJReferenceRpm() {
		return Arrays.copyOf(rotorCtCpJReferenceRpm, rotorCtCpJReferenceRpm.length);
	}

	public Vec3 rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond(int index) {
		return rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond[index];
	}

	public Vec3[] rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond() {
		return Arrays.copyOf(
				rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond,
				rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond.length
		);
	}

	public Vec3 rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond(int index) {
		return rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond[index];
	}

	public Vec3[] rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond() {
		return Arrays.copyOf(
				rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond,
				rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond.length
		);
	}

	public double rotorCtCpJReferenceTransverseAirSpeedMetersPerSecond(int index) {
		return rotorCtCpJReferenceTransverseAirSpeedMetersPerSecond[index];
	}

	public double[] rotorCtCpJReferenceTransverseAirSpeedMetersPerSecond() {
		return Arrays.copyOf(
				rotorCtCpJReferenceTransverseAirSpeedMetersPerSecond,
				rotorCtCpJReferenceTransverseAirSpeedMetersPerSecond.length
		);
	}

	public double rotorCtCpJReferenceInflowAngleRadians(int index) {
		return rotorCtCpJReferenceInflowAngleRadians[index];
	}

	public double[] rotorCtCpJReferenceInflowAngleRadians() {
		return Arrays.copyOf(rotorCtCpJReferenceInflowAngleRadians, rotorCtCpJReferenceInflowAngleRadians.length);
	}

	public double rotorCtCpJReferenceSpeedOfSoundMetersPerSecond(int index) {
		return rotorCtCpJReferenceSpeedOfSoundMetersPerSecond[index];
	}

	public double[] rotorCtCpJReferenceSpeedOfSoundMetersPerSecond() {
		return Arrays.copyOf(
				rotorCtCpJReferenceSpeedOfSoundMetersPerSecond,
				rotorCtCpJReferenceSpeedOfSoundMetersPerSecond.length
		);
	}

	public double rotorCtCpJReferenceDynamicViscosityPascalSeconds(int index) {
		return rotorCtCpJReferenceDynamicViscosityPascalSeconds[index];
	}

	public double[] rotorCtCpJReferenceDynamicViscosityPascalSeconds() {
		return Arrays.copyOf(
				rotorCtCpJReferenceDynamicViscosityPascalSeconds,
				rotorCtCpJReferenceDynamicViscosityPascalSeconds.length
		);
	}

	public double rotorCtCpJReferenceTipMach(int index) {
		return rotorCtCpJReferenceTipMach[index];
	}

	public double[] rotorCtCpJReferenceTipMach() {
		return Arrays.copyOf(rotorCtCpJReferenceTipMach, rotorCtCpJReferenceTipMach.length);
	}

	public double rotorCtCpJReferenceReynoldsNumber(int index) {
		return rotorCtCpJReferenceReynoldsNumber[index];
	}

	public double[] rotorCtCpJReferenceReynoldsNumber() {
		return Arrays.copyOf(rotorCtCpJReferenceReynoldsNumber, rotorCtCpJReferenceReynoldsNumber.length);
	}

	public double rotorCtCpJReferenceReynoldsIndex(int index) {
		return rotorCtCpJReferenceReynoldsIndex[index];
	}

	public double[] rotorCtCpJReferenceReynoldsIndex() {
		return Arrays.copyOf(rotorCtCpJReferenceReynoldsIndex, rotorCtCpJReferenceReynoldsIndex.length);
	}

	public double rotorCtCpJReferenceTipMachRuntimeMargin(int index) {
		return rotorCtCpJReferenceTipMachRuntimeMargin[index];
	}

	public double[] rotorCtCpJReferenceTipMachRuntimeMargin() {
		return Arrays.copyOf(
				rotorCtCpJReferenceTipMachRuntimeMargin,
				rotorCtCpJReferenceTipMachRuntimeMargin.length
		);
	}

	public double rotorCtCpJReferenceReynoldsIndexRuntimeMargin(int index) {
		return rotorCtCpJReferenceReynoldsIndexRuntimeMargin[index];
	}

	public double[] rotorCtCpJReferenceReynoldsIndexRuntimeMargin() {
		return Arrays.copyOf(
				rotorCtCpJReferenceReynoldsIndexRuntimeMargin,
				rotorCtCpJReferenceReynoldsIndexRuntimeMargin.length
		);
	}

	public double rotorCtCpJReferenceOperatingEnvelopeMarginFraction(int index) {
		return rotorCtCpJReferenceOperatingEnvelopeMarginFraction[index];
	}

	public double[] rotorCtCpJReferenceOperatingEnvelopeMarginFraction() {
		return Arrays.copyOf(
				rotorCtCpJReferenceOperatingEnvelopeMarginFraction,
				rotorCtCpJReferenceOperatingEnvelopeMarginFraction.length
		);
	}

	public double rotorCtCpJReferenceThrustCoefficientCt(int index) {
		return rotorCtCpJReferenceThrustCoefficientCt[index];
	}

	public double[] rotorCtCpJReferenceThrustCoefficientCt() {
		return Arrays.copyOf(rotorCtCpJReferenceThrustCoefficientCt, rotorCtCpJReferenceThrustCoefficientCt.length);
	}

	public double rotorCtCpJReferencePowerCoefficientCp(int index) {
		return rotorCtCpJReferencePowerCoefficientCp[index];
	}

	public double[] rotorCtCpJReferencePowerCoefficientCp() {
		return Arrays.copyOf(rotorCtCpJReferencePowerCoefficientCp, rotorCtCpJReferencePowerCoefficientCp.length);
	}

	public double rotorCtCpJReferenceTorqueCoefficientCq(int index) {
		return rotorCtCpJReferenceTorqueCoefficientCq[index];
	}

	public double[] rotorCtCpJReferenceTorqueCoefficientCq() {
		return Arrays.copyOf(rotorCtCpJReferenceTorqueCoefficientCq, rotorCtCpJReferenceTorqueCoefficientCq.length);
	}

	public double rotorCtCpJReferenceEfficiencyEta(int index) {
		return rotorCtCpJReferenceEfficiencyEta[index];
	}

	public double[] rotorCtCpJReferenceEfficiencyEta() {
		return Arrays.copyOf(rotorCtCpJReferenceEfficiencyEta, rotorCtCpJReferenceEfficiencyEta.length);
	}

	public double rotorCtCpJReferenceThrustNewtons(int index) {
		return rotorCtCpJReferenceThrustNewtons[index];
	}

	public double[] rotorCtCpJReferenceThrustNewtons() {
		return Arrays.copyOf(rotorCtCpJReferenceThrustNewtons, rotorCtCpJReferenceThrustNewtons.length);
	}

	public double rotorCtCpJReferenceShaftPowerWatts(int index) {
		return rotorCtCpJReferenceShaftPowerWatts[index];
	}

	public double[] rotorCtCpJReferenceShaftPowerWatts() {
		return Arrays.copyOf(rotorCtCpJReferenceShaftPowerWatts, rotorCtCpJReferenceShaftPowerWatts.length);
	}

	public double rotorCtCpJReferenceDiskLoadingNewtonsPerSquareMeter(int index) {
		return rotorCtCpJReferenceDiskLoadingNewtonsPerSquareMeter[index];
	}

	public double[] rotorCtCpJReferenceDiskLoadingNewtonsPerSquareMeter() {
		return Arrays.copyOf(
				rotorCtCpJReferenceDiskLoadingNewtonsPerSquareMeter,
				rotorCtCpJReferenceDiskLoadingNewtonsPerSquareMeter.length);
	}

	public double rotorCtCpJReferenceIdealInducedVelocityMetersPerSecond(int index) {
		return rotorCtCpJReferenceIdealInducedVelocityMetersPerSecond[index];
	}

	public double[] rotorCtCpJReferenceIdealInducedVelocityMetersPerSecond() {
		return Arrays.copyOf(
				rotorCtCpJReferenceIdealInducedVelocityMetersPerSecond,
				rotorCtCpJReferenceIdealInducedVelocityMetersPerSecond.length);
	}

	public double rotorCtCpJReferenceIdealMomentumPowerWatts(int index) {
		return rotorCtCpJReferenceIdealMomentumPowerWatts[index];
	}

	public double[] rotorCtCpJReferenceIdealMomentumPowerWatts() {
		return Arrays.copyOf(
				rotorCtCpJReferenceIdealMomentumPowerWatts,
				rotorCtCpJReferenceIdealMomentumPowerWatts.length);
	}

	public double rotorCtCpJReferenceUsefulAxialThrustPowerWatts(int index) {
		return rotorCtCpJReferenceUsefulAxialThrustPowerWatts[index];
	}

	public double[] rotorCtCpJReferenceUsefulAxialThrustPowerWatts() {
		return Arrays.copyOf(
				rotorCtCpJReferenceUsefulAxialThrustPowerWatts,
				rotorCtCpJReferenceUsefulAxialThrustPowerWatts.length);
	}

	public double rotorCtCpJReferenceIdealInducedPowerWatts(int index) {
		return rotorCtCpJReferenceIdealInducedPowerWatts[index];
	}

	public double[] rotorCtCpJReferenceIdealInducedPowerWatts() {
		return Arrays.copyOf(
				rotorCtCpJReferenceIdealInducedPowerWatts,
				rotorCtCpJReferenceIdealInducedPowerWatts.length);
	}

	public double rotorCtCpJReferenceAxialPropulsiveEfficiency(int index) {
		return rotorCtCpJReferenceAxialPropulsiveEfficiency[index];
	}

	public double[] rotorCtCpJReferenceAxialPropulsiveEfficiency() {
		return Arrays.copyOf(
				rotorCtCpJReferenceAxialPropulsiveEfficiency,
				rotorCtCpJReferenceAxialPropulsiveEfficiency.length);
	}

	public double rotorCtCpJReferenceIdealMomentumPowerOverShaftPower(int index) {
		return rotorCtCpJReferenceIdealMomentumPowerOverShaftPower[index];
	}

	public double[] rotorCtCpJReferenceIdealMomentumPowerOverShaftPower() {
		return Arrays.copyOf(
				rotorCtCpJReferenceIdealMomentumPowerOverShaftPower,
				rotorCtCpJReferenceIdealMomentumPowerOverShaftPower.length);
	}

	public double rotorCtCpJReferenceIntrinsicShaftPowerResidualWatts(int index) {
		return rotorCtCpJReferenceIntrinsicShaftPowerResidualWatts[index];
	}

	public double[] rotorCtCpJReferenceIntrinsicShaftPowerResidualWatts() {
		return Arrays.copyOf(
				rotorCtCpJReferenceIntrinsicShaftPowerResidualWatts,
				rotorCtCpJReferenceIntrinsicShaftPowerResidualWatts.length);
	}

	public double rotorCtCpJReferenceIntrinsicShaftPowerResidualFraction(int index) {
		return rotorCtCpJReferenceIntrinsicShaftPowerResidualFraction[index];
	}

	public double[] rotorCtCpJReferenceIntrinsicShaftPowerResidualFraction() {
		return Arrays.copyOf(
				rotorCtCpJReferenceIntrinsicShaftPowerResidualFraction,
				rotorCtCpJReferenceIntrinsicShaftPowerResidualFraction.length);
	}

	public double rotorCtCpJReferenceDiskMassFlowKilogramsPerSecond(int index) {
		return rotorCtCpJReferenceDiskMassFlowKilogramsPerSecond[index];
	}

	public double[] rotorCtCpJReferenceDiskMassFlowKilogramsPerSecond() {
		return Arrays.copyOf(
				rotorCtCpJReferenceDiskMassFlowKilogramsPerSecond,
				rotorCtCpJReferenceDiskMassFlowKilogramsPerSecond.length);
	}

	public double rotorCtCpJReferenceFarWakeAxialVelocityMetersPerSecond(int index) {
		return rotorCtCpJReferenceFarWakeAxialVelocityMetersPerSecond[index];
	}

	public double[] rotorCtCpJReferenceFarWakeAxialVelocityMetersPerSecond() {
		return Arrays.copyOf(
				rotorCtCpJReferenceFarWakeAxialVelocityMetersPerSecond,
				rotorCtCpJReferenceFarWakeAxialVelocityMetersPerSecond.length);
	}

	public double rotorCtCpJReferenceFarWakeContractedAreaSquareMeters(int index) {
		return rotorCtCpJReferenceFarWakeContractedAreaSquareMeters[index];
	}

	public double[] rotorCtCpJReferenceFarWakeContractedAreaSquareMeters() {
		return Arrays.copyOf(
				rotorCtCpJReferenceFarWakeContractedAreaSquareMeters,
				rotorCtCpJReferenceFarWakeContractedAreaSquareMeters.length);
	}

	public double rotorCtCpJReferenceFarWakeEquivalentRadiusMeters(int index) {
		return rotorCtCpJReferenceFarWakeEquivalentRadiusMeters[index];
	}

	public double[] rotorCtCpJReferenceFarWakeEquivalentRadiusMeters() {
		return Arrays.copyOf(
				rotorCtCpJReferenceFarWakeEquivalentRadiusMeters,
				rotorCtCpJReferenceFarWakeEquivalentRadiusMeters.length);
	}

	public double rotorCtCpJReferenceAngularMomentumSwirlRadiusMeters(int index) {
		return rotorCtCpJReferenceAngularMomentumSwirlRadiusMeters[index];
	}

	public double[] rotorCtCpJReferenceAngularMomentumSwirlRadiusMeters() {
		return Arrays.copyOf(
				rotorCtCpJReferenceAngularMomentumSwirlRadiusMeters,
				rotorCtCpJReferenceAngularMomentumSwirlRadiusMeters.length);
	}

	public double rotorCtCpJReferenceWakeTangentialVelocityMetersPerSecond(int index) {
		return rotorCtCpJReferenceWakeTangentialVelocityMetersPerSecond[index];
	}

	public double[] rotorCtCpJReferenceWakeTangentialVelocityMetersPerSecond() {
		return Arrays.copyOf(
				rotorCtCpJReferenceWakeTangentialVelocityMetersPerSecond,
				rotorCtCpJReferenceWakeTangentialVelocityMetersPerSecond.length);
	}

	public double rotorCtCpJReferenceWakeSwirlKineticPowerWatts(int index) {
		return rotorCtCpJReferenceWakeSwirlKineticPowerWatts[index];
	}

	public double[] rotorCtCpJReferenceWakeSwirlKineticPowerWatts() {
		return Arrays.copyOf(
				rotorCtCpJReferenceWakeSwirlKineticPowerWatts,
				rotorCtCpJReferenceWakeSwirlKineticPowerWatts.length);
	}

	public double rotorCtCpJReferenceTotalWakeKineticPowerWatts(int index) {
		return rotorCtCpJReferenceTotalWakeKineticPowerWatts[index];
	}

	public double[] rotorCtCpJReferenceTotalWakeKineticPowerWatts() {
		return Arrays.copyOf(
				rotorCtCpJReferenceTotalWakeKineticPowerWatts,
				rotorCtCpJReferenceTotalWakeKineticPowerWatts.length);
	}

	public double rotorCtCpJReferenceTotalWakeKineticPowerOverShaftPower(int index) {
		return rotorCtCpJReferenceTotalWakeKineticPowerOverShaftPower[index];
	}

	public double[] rotorCtCpJReferenceTotalWakeKineticPowerOverShaftPower() {
		return Arrays.copyOf(
				rotorCtCpJReferenceTotalWakeKineticPowerOverShaftPower,
				rotorCtCpJReferenceTotalWakeKineticPowerOverShaftPower.length);
	}

	public double rotorCtCpJReferenceWakeSwirlKineticPowerOverShaftPower(int index) {
		return rotorCtCpJReferenceWakeSwirlKineticPowerOverShaftPower[index];
	}

	public double[] rotorCtCpJReferenceWakeSwirlKineticPowerOverShaftPower() {
		return Arrays.copyOf(
				rotorCtCpJReferenceWakeSwirlKineticPowerOverShaftPower,
				rotorCtCpJReferenceWakeSwirlKineticPowerOverShaftPower.length);
	}

	public double rotorCtCpJReferenceTotalWakeKineticPowerResidualWatts(int index) {
		return rotorCtCpJReferenceTotalWakeKineticPowerResidualWatts[index];
	}

	public double[] rotorCtCpJReferenceTotalWakeKineticPowerResidualWatts() {
		return Arrays.copyOf(
				rotorCtCpJReferenceTotalWakeKineticPowerResidualWatts,
				rotorCtCpJReferenceTotalWakeKineticPowerResidualWatts.length);
	}

	public double rotorCtCpJReferenceTotalWakeKineticPowerResidualFraction(int index) {
		return rotorCtCpJReferenceTotalWakeKineticPowerResidualFraction[index];
	}

	public double[] rotorCtCpJReferenceTotalWakeKineticPowerResidualFraction() {
		return Arrays.copyOf(
				rotorCtCpJReferenceTotalWakeKineticPowerResidualFraction,
				rotorCtCpJReferenceTotalWakeKineticPowerResidualFraction.length);
	}

	public double rotorCtCpJReferenceWakeAngularMomentumTorqueNewtonMeters(int index) {
		return rotorCtCpJReferenceWakeAngularMomentumTorqueNewtonMeters[index];
	}

	public double[] rotorCtCpJReferenceWakeAngularMomentumTorqueNewtonMeters() {
		return Arrays.copyOf(
				rotorCtCpJReferenceWakeAngularMomentumTorqueNewtonMeters,
				rotorCtCpJReferenceWakeAngularMomentumTorqueNewtonMeters.length);
	}

	public double rotorCtCpJReferenceWakeAngularMomentumTorqueResidualNewtonMeters(int index) {
		return rotorCtCpJReferenceWakeAngularMomentumTorqueResidualNewtonMeters[index];
	}

	public double[] rotorCtCpJReferenceWakeAngularMomentumTorqueResidualNewtonMeters() {
		return Arrays.copyOf(
				rotorCtCpJReferenceWakeAngularMomentumTorqueResidualNewtonMeters,
				rotorCtCpJReferenceWakeAngularMomentumTorqueResidualNewtonMeters.length);
	}

	public double rotorCtCpJReferenceWakeAngularMomentumTorqueResidualFraction(int index) {
		return rotorCtCpJReferenceWakeAngularMomentumTorqueResidualFraction[index];
	}

	public double[] rotorCtCpJReferenceWakeAngularMomentumTorqueResidualFraction() {
		return Arrays.copyOf(
				rotorCtCpJReferenceWakeAngularMomentumTorqueResidualFraction,
				rotorCtCpJReferenceWakeAngularMomentumTorqueResidualFraction.length);
	}

	public double rotorCtCpJReferenceShaftTorqueNewtonMeters(int index) {
		return rotorCtCpJReferenceShaftTorqueNewtonMeters[index];
	}

	public double[] rotorCtCpJReferenceShaftTorqueNewtonMeters() {
		return Arrays.copyOf(rotorCtCpJReferenceShaftTorqueNewtonMeters, rotorCtCpJReferenceShaftTorqueNewtonMeters.length);
	}

	public Vec3 rotorCtCpJReferenceThrustForceBodyNewtons(int index) {
		return rotorCtCpJReferenceThrustForceBodyNewtons[index];
	}

	public Vec3[] rotorCtCpJReferenceThrustForceBodyNewtons() {
		return Arrays.copyOf(rotorCtCpJReferenceThrustForceBodyNewtons,
				rotorCtCpJReferenceThrustForceBodyNewtons.length);
	}

	public Vec3 rotorCtCpJReferenceReactionTorqueBodyNewtonMeters(int index) {
		return rotorCtCpJReferenceReactionTorqueBodyNewtonMeters[index];
	}

	public Vec3[] rotorCtCpJReferenceReactionTorqueBodyNewtonMeters() {
		return Arrays.copyOf(rotorCtCpJReferenceReactionTorqueBodyNewtonMeters,
				rotorCtCpJReferenceReactionTorqueBodyNewtonMeters.length);
	}

	public Vec3 rotorCtCpJReferenceThrustMomentBodyNewtonMeters(int index) {
		return rotorCtCpJReferenceThrustMomentBodyNewtonMeters[index];
	}

	public Vec3[] rotorCtCpJReferenceThrustMomentBodyNewtonMeters() {
		return Arrays.copyOf(rotorCtCpJReferenceThrustMomentBodyNewtonMeters,
				rotorCtCpJReferenceThrustMomentBodyNewtonMeters.length);
	}

	public Vec3 rotorCtCpJReferenceTotalTorqueBodyNewtonMeters(int index) {
		return rotorCtCpJReferenceTotalTorqueBodyNewtonMeters[index];
	}

	public Vec3[] rotorCtCpJReferenceTotalTorqueBodyNewtonMeters() {
		return Arrays.copyOf(rotorCtCpJReferenceTotalTorqueBodyNewtonMeters,
				rotorCtCpJReferenceTotalTorqueBodyNewtonMeters.length);
	}

	public double rotorCtCpJReferenceThrustResidualNewtons(int index) {
		return rotorCtCpJReferenceThrustResidualNewtons[index];
	}

	public double[] rotorCtCpJReferenceThrustResidualNewtons() {
		return Arrays.copyOf(rotorCtCpJReferenceThrustResidualNewtons, rotorCtCpJReferenceThrustResidualNewtons.length);
	}

	public double rotorCtCpJReferenceShaftPowerResidualWatts(int index) {
		return rotorCtCpJReferenceShaftPowerResidualWatts[index];
	}

	public double[] rotorCtCpJReferenceShaftPowerResidualWatts() {
		return Arrays.copyOf(rotorCtCpJReferenceShaftPowerResidualWatts,
				rotorCtCpJReferenceShaftPowerResidualWatts.length);
	}

	public double rotorCtCpJReferenceShaftTorqueResidualNewtonMeters(int index) {
		return rotorCtCpJReferenceShaftTorqueResidualNewtonMeters[index];
	}

	public double[] rotorCtCpJReferenceShaftTorqueResidualNewtonMeters() {
		return Arrays.copyOf(rotorCtCpJReferenceShaftTorqueResidualNewtonMeters,
				rotorCtCpJReferenceShaftTorqueResidualNewtonMeters.length);
	}

	public double rotorCtCpJReferenceThrustRatio(int index) {
		return rotorCtCpJReferenceThrustRatio[index];
	}

	public double[] rotorCtCpJReferenceThrustRatio() {
		return Arrays.copyOf(rotorCtCpJReferenceThrustRatio, rotorCtCpJReferenceThrustRatio.length);
	}

	public double rotorCtCpJReferenceShaftTorqueRatio(int index) {
		return rotorCtCpJReferenceShaftTorqueRatio[index];
	}

	public double[] rotorCtCpJReferenceShaftTorqueRatio() {
		return Arrays.copyOf(rotorCtCpJReferenceShaftTorqueRatio,
				rotorCtCpJReferenceShaftTorqueRatio.length);
	}

	void setRotorCtCpJReferenceSample(
			int index,
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample
	) {
		setRotorCtCpJReferenceSample(index, sample, Double.NaN);
	}

	void setRotorCtCpJReferenceSample(
			int index,
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample,
			double ambientTemperatureCelsius
	) {
		setRotorCtCpJReferenceSample(index, sample, ambientTemperatureCelsius, 0.0);
	}

	void setRotorCtCpJReferenceSample(
			int index,
			PropellerArchiveCtCpJRotorForceModel.RotorForceSample sample,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		if (sample == null) {
			clearRotorCtCpJReferenceSample(index);
			return;
		}

		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup = sample.lookup();
		PropellerArchiveCtCpJRotorForceModel.RuntimeForceReplacementStatus runtimeStatus =
				sample.runtimeForceReplacementStatus(ambientTemperatureCelsius, ambientHumidity);
		rotorCtCpJReferenceAvailable[index] = !sample.blocked();
		rotorCtCpJReferenceBlocked[index] = sample.blocked();
		rotorCtCpJReferenceClamped[index] = sample.clamped();
		rotorCtCpJReferenceRuntimeApplied[index] =
				runtimeStatus == PropellerArchiveCtCpJRotorForceModel.RuntimeForceReplacementStatus.ACCEPTED;
		rotorCtCpJReferenceRuntimeStatusOrdinal[index] = runtimeStatus.ordinal();
		rotorCtCpJReferenceInterpolationStatusOrdinal[index] = lookup.interpolationStatus().ordinal();
		rotorCtCpJReferenceLookupStatusCodeOrdinal[index] = lookup.lookupStatusCode().ordinal();
		rotorCtCpJReferenceAdvanceRatioJ[index] = finiteOrZero(lookup.effectiveAdvanceRatioJ());
		rotorCtCpJReferenceRpm[index] = finiteOrZero(lookup.effectiveRpm());
		rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond[index] =
				finiteVectorOrZero(sample.relativeAirVelocityBodyMetersPerSecond());
		rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond[index] =
				finiteVectorOrZero(sample.transverseAirVelocityBodyMetersPerSecond());
		rotorCtCpJReferenceTransverseAirSpeedMetersPerSecond[index] =
				Math.max(0.0, finiteOrZero(sample.transverseAirSpeedMetersPerSecond()));
		rotorCtCpJReferenceInflowAngleRadians[index] =
				Math.max(0.0, finiteOrZero(sample.inflowAngleRadians()));
		PropellerArchiveCtCpJRotorForceModel.RotorOperatingPoint operatingPoint =
				sample.operatingPoint(ambientTemperatureCelsius, ambientHumidity);
		rotorCtCpJReferenceSpeedOfSoundMetersPerSecond[index] =
				Math.max(0.0, finiteOrZero(operatingPoint.speedOfSoundMetersPerSecond()));
		rotorCtCpJReferenceDynamicViscosityPascalSeconds[index] =
				Math.max(0.0, finiteOrZero(operatingPoint.dynamicViscosityPascalSeconds()));
		rotorCtCpJReferenceTipMach[index] =
				Math.max(0.0, finiteOrZero(operatingPoint.tipMach()));
		rotorCtCpJReferenceReynoldsNumber[index] =
				Math.max(0.0, finiteOrZero(operatingPoint.reynoldsNumber()));
		rotorCtCpJReferenceReynoldsIndex[index] =
				Math.max(0.0, finiteOrZero(operatingPoint.reynoldsIndex()));
		rotorCtCpJReferenceTipMachRuntimeMargin[index] =
				finiteOrZero(operatingPoint.runtimeTipMachMargin());
		rotorCtCpJReferenceReynoldsIndexRuntimeMargin[index] =
				finiteOrZero(operatingPoint.runtimeReynoldsIndexMargin());
		rotorCtCpJReferenceOperatingEnvelopeMarginFraction[index] =
				finiteOrZero(operatingPoint.runtimeOperatingEnvelopeMarginFraction());
		rotorCtCpJReferenceThrustCoefficientCt[index] = sample.blocked() ? 0.0 : finiteOrZero(lookup.thrustCoefficientCt());
		rotorCtCpJReferencePowerCoefficientCp[index] = sample.blocked() ? 0.0 : finiteOrZero(lookup.powerCoefficientCp());
		rotorCtCpJReferenceTorqueCoefficientCq[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().torqueCoefficientCq()));
		rotorCtCpJReferenceEfficiencyEta[index] = sample.blocked() ? 0.0 : finiteOrZero(lookup.propulsiveEfficiencyEta());
		rotorCtCpJReferenceThrustNewtons[index] = sample.blocked() ? 0.0 : Math.max(0.0, finiteOrZero(sample.thrustNewtons()));
		rotorCtCpJReferenceShaftPowerWatts[index] = sample.blocked() ? 0.0 : Math.max(0.0, finiteOrZero(sample.shaftPowerWatts()));
		rotorCtCpJReferenceDiskLoadingNewtonsPerSquareMeter[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().diskLoadingNewtonsPerSquareMeter()));
		rotorCtCpJReferenceIdealInducedVelocityMetersPerSecond[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().idealInducedVelocityMetersPerSecond()));
		rotorCtCpJReferenceIdealMomentumPowerWatts[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().idealMomentumPowerWatts()));
		rotorCtCpJReferenceUsefulAxialThrustPowerWatts[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().usefulAxialThrustPowerWatts()));
		rotorCtCpJReferenceIdealInducedPowerWatts[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().idealInducedPowerWatts()));
		rotorCtCpJReferenceAxialPropulsiveEfficiency[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().axialPropulsiveEfficiency()));
		rotorCtCpJReferenceIdealMomentumPowerOverShaftPower[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().idealMomentumPowerOverShaftPower()));
		rotorCtCpJReferenceIntrinsicShaftPowerResidualWatts[index] =
				sample.blocked() ? 0.0 : finiteOrZero(sample.dimensionalSample().shaftPowerResidualWatts());
		rotorCtCpJReferenceIntrinsicShaftPowerResidualFraction[index] =
				sample.blocked() ? 0.0 : finiteOrZero(sample.dimensionalSample().shaftPowerResidualFraction());
		rotorCtCpJReferenceDiskMassFlowKilogramsPerSecond[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().diskMassFlowKilogramsPerSecond()));
		rotorCtCpJReferenceFarWakeAxialVelocityMetersPerSecond[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().farWakeAxialVelocityMetersPerSecond()));
		rotorCtCpJReferenceFarWakeContractedAreaSquareMeters[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().farWakeContractedAreaSquareMeters()));
		rotorCtCpJReferenceFarWakeEquivalentRadiusMeters[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().farWakeEquivalentRadiusMeters()));
		rotorCtCpJReferenceAngularMomentumSwirlRadiusMeters[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().angularMomentumSwirlRadiusMeters()));
		rotorCtCpJReferenceWakeTangentialVelocityMetersPerSecond[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().wakeTangentialVelocityMetersPerSecond()));
		rotorCtCpJReferenceWakeSwirlKineticPowerWatts[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().wakeSwirlKineticPowerWatts()));
		rotorCtCpJReferenceTotalWakeKineticPowerWatts[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().totalWakeKineticPowerWatts()));
		rotorCtCpJReferenceTotalWakeKineticPowerOverShaftPower[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().totalWakeKineticPowerOverShaftPower()));
		rotorCtCpJReferenceWakeSwirlKineticPowerOverShaftPower[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().wakeSwirlKineticPowerOverShaftPower()));
		rotorCtCpJReferenceTotalWakeKineticPowerResidualWatts[index] =
				sample.blocked() ? 0.0 : finiteOrZero(
						sample.dimensionalSample().totalWakeKineticPowerResidualWatts());
		rotorCtCpJReferenceTotalWakeKineticPowerResidualFraction[index] =
				sample.blocked() ? 0.0 : finiteOrZero(
						sample.dimensionalSample().totalWakeKineticPowerResidualFraction());
		rotorCtCpJReferenceWakeAngularMomentumTorqueNewtonMeters[index] =
				sample.blocked() ? 0.0 : Math.max(0.0,
						finiteOrZero(sample.dimensionalSample().wakeAngularMomentumTorqueNewtonMeters()));
		rotorCtCpJReferenceWakeAngularMomentumTorqueResidualNewtonMeters[index] =
				sample.blocked() ? 0.0 : finiteOrZero(
						sample.dimensionalSample().wakeAngularMomentumTorqueResidualNewtonMeters());
		rotorCtCpJReferenceWakeAngularMomentumTorqueResidualFraction[index] =
				sample.blocked() ? 0.0 : finiteOrZero(
						sample.dimensionalSample().wakeAngularMomentumTorqueResidualFraction());
		rotorCtCpJReferenceShaftTorqueNewtonMeters[index] =
				sample.blocked() ? 0.0 : Math.max(0.0, finiteOrZero(sample.shaftTorqueNewtonMeters()));
		rotorCtCpJReferenceThrustForceBodyNewtons[index] =
				sample.blocked() ? Vec3.ZERO : finiteVectorOrZero(sample.thrustForceBodyNewtons());
		rotorCtCpJReferenceReactionTorqueBodyNewtonMeters[index] =
				sample.blocked() ? Vec3.ZERO : finiteVectorOrZero(sample.reactionTorqueBodyNewtonMeters());
		rotorCtCpJReferenceThrustMomentBodyNewtonMeters[index] =
				sample.blocked() ? Vec3.ZERO : finiteVectorOrZero(sample.thrustMomentBodyNewtonMeters());
		rotorCtCpJReferenceTotalTorqueBodyNewtonMeters[index] =
				sample.blocked() ? Vec3.ZERO : finiteVectorOrZero(sample.totalTorqueBodyNewtonMeters());
	}

	void clearRotorCtCpJReferenceSample(int index) {
		rotorCtCpJReferenceAvailable[index] = false;
		rotorCtCpJReferenceBlocked[index] = false;
		rotorCtCpJReferenceClamped[index] = false;
		rotorCtCpJReferenceRuntimeApplied[index] = false;
		rotorCtCpJReferenceRuntimeStatusOrdinal[index] =
				PropellerArchiveCtCpJRotorForceModel.RuntimeForceReplacementStatus.BLOCKED.ordinal();
		rotorCtCpJReferenceInterpolationStatusOrdinal[index] =
				PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BLOCKED.ordinal();
		rotorCtCpJReferenceLookupStatusCodeOrdinal[index] =
				PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.UNKNOWN.ordinal();
		rotorCtCpJReferenceAdvanceRatioJ[index] = 0.0;
		rotorCtCpJReferenceRpm[index] = 0.0;
		rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond[index] = Vec3.ZERO;
		rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond[index] = Vec3.ZERO;
		rotorCtCpJReferenceTransverseAirSpeedMetersPerSecond[index] = 0.0;
		rotorCtCpJReferenceInflowAngleRadians[index] = 0.0;
		rotorCtCpJReferenceSpeedOfSoundMetersPerSecond[index] = 0.0;
		rotorCtCpJReferenceDynamicViscosityPascalSeconds[index] = 0.0;
		rotorCtCpJReferenceTipMach[index] = 0.0;
		rotorCtCpJReferenceReynoldsNumber[index] = 0.0;
		rotorCtCpJReferenceReynoldsIndex[index] = 0.0;
		rotorCtCpJReferenceTipMachRuntimeMargin[index] = 0.0;
		rotorCtCpJReferenceReynoldsIndexRuntimeMargin[index] = 0.0;
		rotorCtCpJReferenceOperatingEnvelopeMarginFraction[index] = 0.0;
		rotorCtCpJReferenceThrustCoefficientCt[index] = 0.0;
		rotorCtCpJReferencePowerCoefficientCp[index] = 0.0;
		rotorCtCpJReferenceTorqueCoefficientCq[index] = 0.0;
		rotorCtCpJReferenceEfficiencyEta[index] = 0.0;
		rotorCtCpJReferenceThrustNewtons[index] = 0.0;
		rotorCtCpJReferenceShaftPowerWatts[index] = 0.0;
		rotorCtCpJReferenceDiskLoadingNewtonsPerSquareMeter[index] = 0.0;
		rotorCtCpJReferenceIdealInducedVelocityMetersPerSecond[index] = 0.0;
		rotorCtCpJReferenceIdealMomentumPowerWatts[index] = 0.0;
		rotorCtCpJReferenceUsefulAxialThrustPowerWatts[index] = 0.0;
		rotorCtCpJReferenceIdealInducedPowerWatts[index] = 0.0;
		rotorCtCpJReferenceAxialPropulsiveEfficiency[index] = 0.0;
		rotorCtCpJReferenceIdealMomentumPowerOverShaftPower[index] = 0.0;
		rotorCtCpJReferenceIntrinsicShaftPowerResidualWatts[index] = 0.0;
		rotorCtCpJReferenceIntrinsicShaftPowerResidualFraction[index] = 0.0;
		rotorCtCpJReferenceDiskMassFlowKilogramsPerSecond[index] = 0.0;
		rotorCtCpJReferenceFarWakeAxialVelocityMetersPerSecond[index] = 0.0;
		rotorCtCpJReferenceFarWakeContractedAreaSquareMeters[index] = 0.0;
		rotorCtCpJReferenceFarWakeEquivalentRadiusMeters[index] = 0.0;
		rotorCtCpJReferenceAngularMomentumSwirlRadiusMeters[index] = 0.0;
		rotorCtCpJReferenceWakeTangentialVelocityMetersPerSecond[index] = 0.0;
		rotorCtCpJReferenceWakeSwirlKineticPowerWatts[index] = 0.0;
		rotorCtCpJReferenceTotalWakeKineticPowerWatts[index] = 0.0;
		rotorCtCpJReferenceTotalWakeKineticPowerOverShaftPower[index] = 0.0;
		rotorCtCpJReferenceWakeSwirlKineticPowerOverShaftPower[index] = 0.0;
		rotorCtCpJReferenceTotalWakeKineticPowerResidualWatts[index] = 0.0;
		rotorCtCpJReferenceTotalWakeKineticPowerResidualFraction[index] = 0.0;
		rotorCtCpJReferenceWakeAngularMomentumTorqueNewtonMeters[index] = 0.0;
		rotorCtCpJReferenceWakeAngularMomentumTorqueResidualNewtonMeters[index] = 0.0;
		rotorCtCpJReferenceWakeAngularMomentumTorqueResidualFraction[index] = 0.0;
		rotorCtCpJReferenceShaftTorqueNewtonMeters[index] = 0.0;
		rotorCtCpJReferenceThrustForceBodyNewtons[index] = Vec3.ZERO;
		rotorCtCpJReferenceReactionTorqueBodyNewtonMeters[index] = Vec3.ZERO;
		rotorCtCpJReferenceThrustMomentBodyNewtonMeters[index] = Vec3.ZERO;
		rotorCtCpJReferenceTotalTorqueBodyNewtonMeters[index] = Vec3.ZERO;
		rotorCtCpJReferenceThrustResidualNewtons[index] = 0.0;
		rotorCtCpJReferenceShaftPowerResidualWatts[index] = 0.0;
		rotorCtCpJReferenceShaftTorqueResidualNewtonMeters[index] = 0.0;
		rotorCtCpJReferenceThrustRatio[index] = 0.0;
		rotorCtCpJReferenceShaftTorqueRatio[index] = 0.0;
	}

	void updateRotorCtCpJReferenceResidual(int index) {
		if (!rotorCtCpJReferenceAvailable[index]) {
			rotorCtCpJReferenceThrustResidualNewtons[index] = 0.0;
			rotorCtCpJReferenceShaftPowerResidualWatts[index] = 0.0;
			rotorCtCpJReferenceShaftTorqueResidualNewtonMeters[index] = 0.0;
			rotorCtCpJReferenceThrustRatio[index] = 0.0;
			rotorCtCpJReferenceShaftTorqueRatio[index] = 0.0;
			return;
		}
		double referenceThrust = rotorCtCpJReferenceThrustNewtons[index];
		double referencePower = rotorCtCpJReferenceShaftPowerWatts[index];
		double referenceTorque = rotorCtCpJReferenceShaftTorqueNewtonMeters[index];
		double actualThrust = rotorThrustNewtons[index];
		double actualTorque = Math.abs(motorAerodynamicTorqueNewtonMeters[index]);
		double actualPower = actualTorque * Math.max(0.0, motorOmegaRadiansPerSecond[index]);
		rotorCtCpJReferenceThrustResidualNewtons[index] = finiteOrZero(actualThrust - referenceThrust);
		rotorCtCpJReferenceShaftPowerResidualWatts[index] = finiteOrZero(actualPower - referencePower);
		rotorCtCpJReferenceShaftTorqueResidualNewtonMeters[index] = finiteOrZero(actualTorque - referenceTorque);
		rotorCtCpJReferenceThrustRatio[index] = referenceThrust > 1.0e-9
				? finiteOrZero(actualThrust / referenceThrust)
				: 0.0;
		rotorCtCpJReferenceShaftTorqueRatio[index] = referenceTorque > 1.0e-9
				? finiteOrZero(actualTorque / referenceTorque)
				: 0.0;
	}

	public double averageMotorCoolingFactor() {
		double sum = 0.0;
		for (double factor : motorCoolingFactor) {
			sum += factor;
		}
		return sum / motorCoolingFactor.length;
	}

	public double averageMotorTemperatureCelsius() {
		double sum = 0.0;
		for (double temperature : motorTemperatureCelsius) {
			sum += temperature;
		}
		return sum / motorTemperatureCelsius.length;
	}

	public double maxMotorTemperatureCelsius() {
		double max = 25.0;
		for (double temperature : motorTemperatureCelsius) {
			max = Math.max(max, temperature);
		}
		return max;
	}

	public void resetMotors() {
		Arrays.fill(motorOmegaRadiansPerSecond, 0.0);
		Arrays.fill(motorTargetOmegaRadiansPerSecond, 0.0);
		Arrays.fill(motorTrackingError, 0.0);
		Arrays.fill(motorActuatorAuthority, 1.0);
		Arrays.fill(motorAngularAccelerationRadiansPerSecondSquared, 0.0);
		Arrays.fill(motorAerodynamicTorqueNewtonMeters, 0.0);
		Arrays.fill(motorMechanicalLossTorqueNewtonMeters, 0.0);
		Arrays.fill(motorTorqueRippleNewtonMeters, 0.0);
		Arrays.fill(motorShaftPowerWatts, 0.0);
		Arrays.fill(motorPhaseCurrentAmps, 0.0);
		Arrays.fill(motorCurrentRippleAmps, 0.0);
		Arrays.fill(motorElectricalEfficiency, 0.0);
		Arrays.fill(motorVoltageHeadroom, 1.0);
		Arrays.fill(motorWindingResistanceScale, 1.0);
		Arrays.fill(motorCommutationRippleIntensity, 0.0);
		Arrays.fill(motorRpmTelemetryOmegaRadiansPerSecond, 0.0);
		Arrays.fill(motorRpmTelemetryValidity, 0.0);
		Arrays.fill(escOutputCommand, 0.0);
		Arrays.fill(escElectricalOutputCommand, 0.0);
		Arrays.fill(escElectricalOutputError, 0.0);
		escCommandFrameAgeSeconds = 0.0;
		escCommandFrameIntervalSeconds = 0.0;
		escCommandError = 0.0;
		Arrays.fill(escDesyncIntensity, 0.0);
		Arrays.fill(escCoolingFactor, 1.0);
		Arrays.fill(motorCurrentAmps, 0.0);
		Arrays.fill(motorRegenerativeCurrentAmps, 0.0);
		Arrays.fill(motorCoolingFactor, 1.0);
		Arrays.fill(rotorA4mcVentilationEfficiency, 1.0);
		Arrays.fill(rotorCtCpJReferenceAvailable, false);
		Arrays.fill(rotorCtCpJReferenceBlocked, false);
		Arrays.fill(rotorCtCpJReferenceClamped, false);
		Arrays.fill(rotorCtCpJReferenceRuntimeApplied, false);
		Arrays.fill(rotorCtCpJReferenceRuntimeStatusOrdinal,
				PropellerArchiveCtCpJRotorForceModel.RuntimeForceReplacementStatus.BLOCKED.ordinal());
		Arrays.fill(rotorCtCpJReferenceInterpolationStatusOrdinal,
				PropellerArchiveCtCpJLookupEvaluator.InterpolationStatus.BLOCKED.ordinal());
		Arrays.fill(rotorCtCpJReferenceLookupStatusCodeOrdinal,
				PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.UNKNOWN.ordinal());
		Arrays.fill(rotorCtCpJReferenceAdvanceRatioJ, 0.0);
		Arrays.fill(rotorCtCpJReferenceRpm, 0.0);
		Arrays.fill(rotorCtCpJReferenceRelativeAirVelocityBodyMetersPerSecond, Vec3.ZERO);
		Arrays.fill(rotorCtCpJReferenceTransverseAirVelocityBodyMetersPerSecond, Vec3.ZERO);
		Arrays.fill(rotorCtCpJReferenceTransverseAirSpeedMetersPerSecond, 0.0);
		Arrays.fill(rotorCtCpJReferenceInflowAngleRadians, 0.0);
		Arrays.fill(rotorCtCpJReferenceSpeedOfSoundMetersPerSecond, 0.0);
		Arrays.fill(rotorCtCpJReferenceDynamicViscosityPascalSeconds, 0.0);
		Arrays.fill(rotorCtCpJReferenceTipMach, 0.0);
		Arrays.fill(rotorCtCpJReferenceReynoldsNumber, 0.0);
		Arrays.fill(rotorCtCpJReferenceReynoldsIndex, 0.0);
		Arrays.fill(rotorCtCpJReferenceTipMachRuntimeMargin, 0.0);
		Arrays.fill(rotorCtCpJReferenceReynoldsIndexRuntimeMargin, 0.0);
		Arrays.fill(rotorCtCpJReferenceOperatingEnvelopeMarginFraction, 0.0);
		Arrays.fill(rotorCtCpJReferenceThrustCoefficientCt, 0.0);
		Arrays.fill(rotorCtCpJReferencePowerCoefficientCp, 0.0);
		Arrays.fill(rotorCtCpJReferenceTorqueCoefficientCq, 0.0);
		Arrays.fill(rotorCtCpJReferenceEfficiencyEta, 0.0);
		Arrays.fill(rotorCtCpJReferenceThrustNewtons, 0.0);
		Arrays.fill(rotorCtCpJReferenceShaftPowerWatts, 0.0);
		Arrays.fill(rotorCtCpJReferenceDiskLoadingNewtonsPerSquareMeter, 0.0);
		Arrays.fill(rotorCtCpJReferenceIdealInducedVelocityMetersPerSecond, 0.0);
		Arrays.fill(rotorCtCpJReferenceIdealMomentumPowerWatts, 0.0);
		Arrays.fill(rotorCtCpJReferenceUsefulAxialThrustPowerWatts, 0.0);
		Arrays.fill(rotorCtCpJReferenceIdealInducedPowerWatts, 0.0);
		Arrays.fill(rotorCtCpJReferenceAxialPropulsiveEfficiency, 0.0);
		Arrays.fill(rotorCtCpJReferenceIdealMomentumPowerOverShaftPower, 0.0);
		Arrays.fill(rotorCtCpJReferenceIntrinsicShaftPowerResidualWatts, 0.0);
		Arrays.fill(rotorCtCpJReferenceIntrinsicShaftPowerResidualFraction, 0.0);
		Arrays.fill(rotorCtCpJReferenceDiskMassFlowKilogramsPerSecond, 0.0);
		Arrays.fill(rotorCtCpJReferenceFarWakeAxialVelocityMetersPerSecond, 0.0);
		Arrays.fill(rotorCtCpJReferenceFarWakeContractedAreaSquareMeters, 0.0);
		Arrays.fill(rotorCtCpJReferenceFarWakeEquivalentRadiusMeters, 0.0);
		Arrays.fill(rotorCtCpJReferenceAngularMomentumSwirlRadiusMeters, 0.0);
		Arrays.fill(rotorCtCpJReferenceWakeTangentialVelocityMetersPerSecond, 0.0);
		Arrays.fill(rotorCtCpJReferenceWakeSwirlKineticPowerWatts, 0.0);
		Arrays.fill(rotorCtCpJReferenceTotalWakeKineticPowerWatts, 0.0);
		Arrays.fill(rotorCtCpJReferenceTotalWakeKineticPowerOverShaftPower, 0.0);
		Arrays.fill(rotorCtCpJReferenceWakeSwirlKineticPowerOverShaftPower, 0.0);
		Arrays.fill(rotorCtCpJReferenceTotalWakeKineticPowerResidualWatts, 0.0);
		Arrays.fill(rotorCtCpJReferenceTotalWakeKineticPowerResidualFraction, 0.0);
		Arrays.fill(rotorCtCpJReferenceWakeAngularMomentumTorqueNewtonMeters, 0.0);
		Arrays.fill(rotorCtCpJReferenceWakeAngularMomentumTorqueResidualNewtonMeters, 0.0);
		Arrays.fill(rotorCtCpJReferenceWakeAngularMomentumTorqueResidualFraction, 0.0);
		Arrays.fill(rotorCtCpJReferenceShaftTorqueNewtonMeters, 0.0);
		Arrays.fill(rotorCtCpJReferenceThrustForceBodyNewtons, Vec3.ZERO);
		Arrays.fill(rotorCtCpJReferenceReactionTorqueBodyNewtonMeters, Vec3.ZERO);
		Arrays.fill(rotorCtCpJReferenceThrustMomentBodyNewtonMeters, Vec3.ZERO);
		Arrays.fill(rotorCtCpJReferenceTotalTorqueBodyNewtonMeters, Vec3.ZERO);
		Arrays.fill(rotorCtCpJReferenceThrustResidualNewtons, 0.0);
		Arrays.fill(rotorCtCpJReferenceShaftPowerResidualWatts, 0.0);
		Arrays.fill(rotorCtCpJReferenceShaftTorqueResidualNewtonMeters, 0.0);
		Arrays.fill(rotorCtCpJReferenceThrustRatio, 0.0);
		Arrays.fill(rotorCtCpJReferenceShaftTorqueRatio, 0.0);
		a4mcPackVentilationEfficiency = 1.0;
		Arrays.fill(rotorThrustNewtons, 0.0);
		Arrays.fill(rotorForceBodyNewtons, Vec3.ZERO);
		Arrays.fill(rotorTorqueBodyNewtonMeters, Vec3.ZERO);
		Arrays.fill(rotorInducedVelocityMetersPerSecond, 0.0);
		Arrays.fill(rotorInducedLagThrustScale, 1.0);
		Arrays.fill(rotorDynamicInflowTimeConstantSeconds, 0.0);
		Arrays.fill(rotorTranslationalLiftIntensity, 0.0);
		Arrays.fill(rotorAdvanceRatio, 0.0);
		Arrays.fill(rotorPropellerAdvanceRatioJ, 0.0);
		Arrays.fill(rotorPropellerThrustScale, 1.0);
		Arrays.fill(rotorPropellerPowerScale, 1.0);
		Arrays.fill(rotorAxialGustThrustScale, 1.0);
		Arrays.fill(rotorReverseFlowInboardFraction, 0.0);
		Arrays.fill(rotorTipMach, 0.0);
		Arrays.fill(rotorCompressibilityThrustScale, 1.0);
		Arrays.fill(rotorReynoldsNumber, 0.0);
		Arrays.fill(rotorReynoldsIndex, 0.0);
		Arrays.fill(rotorLowReynoldsLoss, 0.0);
		Arrays.fill(rotorBladeAngleOfAttackRadians, 0.0);
		Arrays.fill(rotorBladeElementStallIntensity, 0.0);
		Arrays.fill(rotorBladeDissymmetryIntensity, 0.0);
		Arrays.fill(rotorBladePassRippleIntensity, 0.0);
		Arrays.fill(rotorAerodynamicLoadFactor, 0.0);
		Arrays.fill(rotorInPlaneDragForceNewtons, 0.0);
		Arrays.fill(rotorInPlaneDragShaftTorqueNewtonMeters, 0.0);
		Arrays.fill(rotorInPlaneDragShaftPowerWatts, 0.0);
		Arrays.fill(rotorFlappingForceNewtons, 0.0);
		Arrays.fill(rotorFlappingTiltRadians, 0.0);
		Arrays.fill(rotorConingIntensity, 0.0);
		Arrays.fill(rotorConingAngleRadians, 0.0);
		Arrays.fill(rotorStallIntensity, 0.0);
		Arrays.fill(rotorWindmillingIntensity, 0.0);
		Arrays.fill(rotorSurfaceScrapeIntensity, 0.0);
		Arrays.fill(rotorWakeInterferenceIntensity, 0.0);
		Arrays.fill(rotorWakeThrustScale, 1.0);
		Arrays.fill(rotorWetThrustScale, 1.0);
		Arrays.fill(rotorIcingSeverity, 0.0);
		Arrays.fill(rotorIcingThrustScale, 1.0);
		Arrays.fill(rotorIcingPowerScale, 1.0);
		Arrays.fill(rotorCoaxialLoadBias, 0.0);
		Arrays.fill(rotorCoaxialLoadBiasTarget, 0.0);
		Arrays.fill(rotorCoaxialLoadBiasClipping, 0.0);
		Arrays.fill(rotorCoaxialAllocationLoadFraction, 0.0);
		Arrays.fill(rotorCoaxialAllocationCommandRatio, 1.0);
		Arrays.fill(rotorCoaxialAllocationMechanicalGainPercent, 0.0);
		Arrays.fill(rotorCoaxialAllocationElectricalGainPercent, 0.0);
		Arrays.fill(rotorCoaxialAllocationUncertaintyPercent, 0.0);
		Arrays.fill(rotorWakeSwirlVelocityMetersPerSecond, 0.0);
		Arrays.fill(rotorArmFlexIntensity, 0.0);
		Arrays.fill(rotorArmFlexDeflectionMeters, 0.0);
		Arrays.fill(rotorArmFlexTiltRadians, 0.0);
		Arrays.fill(rotorDamageVibration, 0.0);
		rotorVibration = 0.0;
		rotorInflowSkewIntensity = 0.0;
		rotorInflowSkewTorqueBodyNewtonMeters = Vec3.ZERO;
		rotorBladeDissymmetryTorqueBodyNewtonMeters = Vec3.ZERO;
		rotorWakeSwirlTorqueBodyNewtonMeters = Vec3.ZERO;
		rotorFlappingTorqueBodyNewtonMeters = Vec3.ZERO;
		rotorActiveBrakingTorqueBodyNewtonMeters = Vec3.ZERO;
		rotorInertiaTorqueBodyNewtonMeters = Vec3.ZERO;
		rotorAccelerationReactionTorqueBodyNewtonMeters = Vec3.ZERO;
		rotorGyroscopicTorqueBodyNewtonMeters = Vec3.ZERO;
		rotorAngularDragTorqueBodyNewtonMeters = Vec3.ZERO;
		airframeSeparatedFlowIntensity = 0.0;
		airframeBodyDragForceBodyNewtons = Vec3.ZERO;
		linearDampingDragForceWorldNewtons = Vec3.ZERO;
		airframeDragAlongFlowNewtons = 0.0;
		airframeDragEquivalentLinearCoefficient = 0.0;
		airframeDragEquivalentCdAMetersSquared = 0.0;
		airframeDragImavReferenceRatio = 0.0;
		propwashIntensity = 0.0;
		propwashWakeIntensity = 0.0;
		propwashTorqueBodyNewtonMeters = Vec3.ZERO;
		vortexRingStateIntensity = 0.0;
		vortexRingThrustBuffetAmplitude = 0.0;
		vortexRingMaxThrustBuffetAmplitude = 0.0;
		vortexRingBuffetForceBodyNewtons = Vec3.ZERO;
		groundEffectDragForceBodyNewtons = Vec3.ZERO;
		groundEffectLevelingTorqueBodyNewtonMeters = Vec3.ZERO;
		rotorWashDragForceBodyNewtons = Vec3.ZERO;
		rotorWallEffectForceBodyNewtons = Vec3.ZERO;
		mixerSaturation = 0.0;
		mixerLowSaturation = 0.0;
		mixerHighSaturation = 0.0;
		mixerLowHeadroom = 1.0;
		mixerHighHeadroom = 1.0;
		mixerOutputTorqueBodyNewtonMeters = Vec3.ZERO;
		mixerAxisAuthority = new Vec3(1.0, 1.0, 1.0);
	}

	public double motorPower(DroneConfig config, int index) {
		double maxOmega = config.rotors().get(index).maxOmegaRadiansPerSecond();
		return MathUtil.clamp(motorOmegaRadiansPerSecond[index] / maxOmega, 0.0, 1.0);
	}

	public double[] motorPower(DroneConfig config) {
		double[] power = new double[motorOmegaRadiansPerSecond.length];
		for (int i = 0; i < power.length; i++) {
			power[i] = motorPower(config, i);
		}
		return power;
	}

	public double rotorThrustNewtons(int index) {
		return rotorThrustNewtons[index];
	}

	public double[] rotorThrustNewtons() {
		return Arrays.copyOf(rotorThrustNewtons, rotorThrustNewtons.length);
	}

	void setRotorThrustNewtons(int index, double value) {
		rotorThrustNewtons[index] = Math.max(0.0, value);
	}

	public Vec3 rotorForceBodyNewtons(int index) {
		return rotorForceBodyNewtons[index];
	}

	public Vec3[] rotorForceBodyNewtons() {
		return Arrays.copyOf(rotorForceBodyNewtons, rotorForceBodyNewtons.length);
	}

	void setRotorForceBodyNewtons(int index, Vec3 value) {
		rotorForceBodyNewtons[index] = value == null || !value.isFinite() ? Vec3.ZERO : value;
	}

	public Vec3 rotorTorqueBodyNewtonMeters(int index) {
		return rotorTorqueBodyNewtonMeters[index];
	}

	public Vec3[] rotorTorqueBodyNewtonMeters() {
		return Arrays.copyOf(rotorTorqueBodyNewtonMeters, rotorTorqueBodyNewtonMeters.length);
	}

	void setRotorTorqueBodyNewtonMeters(int index, Vec3 value) {
		rotorTorqueBodyNewtonMeters[index] = value == null || !value.isFinite() ? Vec3.ZERO : value;
	}

	public double rotorInducedVelocityMetersPerSecond(int index) {
		return rotorInducedVelocityMetersPerSecond[index];
	}

	public double[] rotorInducedVelocityMetersPerSecond() {
		return Arrays.copyOf(rotorInducedVelocityMetersPerSecond, rotorInducedVelocityMetersPerSecond.length);
	}

	void setRotorInducedVelocityMetersPerSecond(int index, double value) {
		rotorInducedVelocityMetersPerSecond[index] = Math.max(0.0, value);
	}

	public double averageRotorInducedVelocityMetersPerSecond() {
		double sum = 0.0;
		for (double inducedVelocity : rotorInducedVelocityMetersPerSecond) {
			sum += inducedVelocity;
		}
		return sum / rotorInducedVelocityMetersPerSecond.length;
	}

	public double maxRotorInducedVelocityMetersPerSecond() {
		double max = 0.0;
		for (double inducedVelocity : rotorInducedVelocityMetersPerSecond) {
			max = Math.max(max, inducedVelocity);
		}
		return max;
	}

	public double rotorInducedLagThrustScale(int index) {
		return rotorInducedLagThrustScale[index];
	}

	public double[] rotorInducedLagThrustScale() {
		return Arrays.copyOf(rotorInducedLagThrustScale, rotorInducedLagThrustScale.length);
	}

	void setRotorInducedLagThrustScale(int index, double value) {
		rotorInducedLagThrustScale[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.65, 1.0) : 1.0;
	}

	public double rotorDynamicInflowTimeConstantSeconds(int index) {
		return rotorDynamicInflowTimeConstantSeconds[index];
	}

	public double[] rotorDynamicInflowTimeConstantSeconds() {
		return Arrays.copyOf(rotorDynamicInflowTimeConstantSeconds, rotorDynamicInflowTimeConstantSeconds.length);
	}

	void setRotorDynamicInflowTimeConstantSeconds(int index, double value) {
		rotorDynamicInflowTimeConstantSeconds[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 0.5) : 0.0;
	}

	public double averageRotorInducedLagThrustScale() {
		double sum = 0.0;
		for (double scale : rotorInducedLagThrustScale) {
			sum += scale;
		}
		return sum / rotorInducedLagThrustScale.length;
	}

	public double minRotorInducedLagThrustScale() {
		double min = 1.0;
		for (double scale : rotorInducedLagThrustScale) {
			min = Math.min(min, scale);
		}
		return min;
	}

	public double averageRotorDynamicInflowTimeConstantSeconds() {
		double sum = 0.0;
		for (double timeConstant : rotorDynamicInflowTimeConstantSeconds) {
			sum += timeConstant;
		}
		return sum / rotorDynamicInflowTimeConstantSeconds.length;
	}

	public double maxRotorDynamicInflowTimeConstantSeconds() {
		double max = 0.0;
		for (double timeConstant : rotorDynamicInflowTimeConstantSeconds) {
			max = Math.max(max, timeConstant);
		}
		return max;
	}

	public double rotorTranslationalLiftIntensity(int index) {
		return rotorTranslationalLiftIntensity[index];
	}

	public double[] rotorTranslationalLiftIntensity() {
		return Arrays.copyOf(rotorTranslationalLiftIntensity, rotorTranslationalLiftIntensity.length);
	}

	void setRotorTranslationalLiftIntensity(int index, double value) {
		rotorTranslationalLiftIntensity[index] = MathUtil.clamp(value, 0.0, 1.0);
	}

	public double averageRotorTranslationalLiftIntensity() {
		double sum = 0.0;
		for (double intensity : rotorTranslationalLiftIntensity) {
			sum += intensity;
		}
		return sum / rotorTranslationalLiftIntensity.length;
	}

	public double rotorAdvanceRatio(int index) {
		return rotorAdvanceRatio[index];
	}

	public double[] rotorAdvanceRatio() {
		return Arrays.copyOf(rotorAdvanceRatio, rotorAdvanceRatio.length);
	}

	void setRotorAdvanceRatio(int index, double value) {
		rotorAdvanceRatio[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 2.0) : 0.0;
	}

	public double rotorPropellerAdvanceRatioJ(int index) {
		return rotorPropellerAdvanceRatioJ[index];
	}

	public double[] rotorPropellerAdvanceRatioJ() {
		return Arrays.copyOf(rotorPropellerAdvanceRatioJ, rotorPropellerAdvanceRatioJ.length);
	}

	void setRotorPropellerAdvanceRatioJ(int index, double value) {
		rotorPropellerAdvanceRatioJ[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, Math.PI * 2.0) : 0.0;
	}

	public double rotorPropellerThrustScale(int index) {
		return rotorPropellerThrustScale[index];
	}

	public double[] rotorPropellerThrustScale() {
		return Arrays.copyOf(rotorPropellerThrustScale, rotorPropellerThrustScale.length);
	}

	void setRotorPropellerThrustScale(int index, double value) {
		rotorPropellerThrustScale[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 2.0) : 1.0;
	}

	public double rotorPropellerPowerScale(int index) {
		return rotorPropellerPowerScale[index];
	}

	public double[] rotorPropellerPowerScale() {
		return Arrays.copyOf(rotorPropellerPowerScale, rotorPropellerPowerScale.length);
	}

	void setRotorPropellerPowerScale(int index, double value) {
		rotorPropellerPowerScale[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 2.0) : 1.0;
	}

	public double rotorAxialGustThrustScale(int index) {
		return rotorAxialGustThrustScale[index];
	}

	public double[] rotorAxialGustThrustScale() {
		return Arrays.copyOf(rotorAxialGustThrustScale, rotorAxialGustThrustScale.length);
	}

	void setRotorAxialGustThrustScale(int index, double value) {
		rotorAxialGustThrustScale[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 2.5) : 1.0;
	}

	public double averageRotorAdvanceRatio() {
		double sum = 0.0;
		for (double ratio : rotorAdvanceRatio) {
			sum += ratio;
		}
		return sum / rotorAdvanceRatio.length;
	}

	public double maxRotorAdvanceRatio() {
		double max = 0.0;
		for (double ratio : rotorAdvanceRatio) {
			max = Math.max(max, ratio);
		}
		return max;
	}

	public double averageRotorPropellerAdvanceRatioJ() {
		double sum = 0.0;
		for (double ratio : rotorPropellerAdvanceRatioJ) {
			sum += ratio;
		}
		return sum / rotorPropellerAdvanceRatioJ.length;
	}

	public double maxRotorPropellerAdvanceRatioJ() {
		double max = 0.0;
		for (double ratio : rotorPropellerAdvanceRatioJ) {
			max = Math.max(max, ratio);
		}
		return max;
	}

	public double averageRotorPropellerThrustScale() {
		double sum = 0.0;
		for (double scale : rotorPropellerThrustScale) {
			sum += scale;
		}
		return sum / rotorPropellerThrustScale.length;
	}

	public double minRotorPropellerThrustScale() {
		double min = 1.0;
		for (double scale : rotorPropellerThrustScale) {
			min = Math.min(min, scale);
		}
		return min;
	}

	public double averageRotorPropellerPowerScale() {
		double sum = 0.0;
		for (double scale : rotorPropellerPowerScale) {
			sum += scale;
		}
		return sum / rotorPropellerPowerScale.length;
	}

	public double minRotorPropellerPowerScale() {
		double min = 1.0;
		for (double scale : rotorPropellerPowerScale) {
			min = Math.min(min, scale);
		}
		return min;
	}

	public double averageRotorAxialGustThrustScale() {
		double sum = 0.0;
		for (double scale : rotorAxialGustThrustScale) {
			sum += scale;
		}
		return sum / rotorAxialGustThrustScale.length;
	}

	public double minRotorAxialGustThrustScale() {
		double min = 1.0;
		for (double scale : rotorAxialGustThrustScale) {
			min = Math.min(min, scale);
		}
		return min;
	}

	public double maxRotorAxialGustThrustScale() {
		double max = 1.0;
		for (double scale : rotorAxialGustThrustScale) {
			max = Math.max(max, scale);
		}
		return max;
	}

	public double rotorReverseFlowInboardFraction(int index) {
		return rotorReverseFlowInboardFraction[index];
	}

	public double[] rotorReverseFlowInboardFraction() {
		return Arrays.copyOf(rotorReverseFlowInboardFraction, rotorReverseFlowInboardFraction.length);
	}

	void setRotorReverseFlowInboardFraction(int index, double value) {
		rotorReverseFlowInboardFraction[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
	}

	public double averageRotorReverseFlowInboardFraction() {
		double sum = 0.0;
		for (double fraction : rotorReverseFlowInboardFraction) {
			sum += fraction;
		}
		return sum / rotorReverseFlowInboardFraction.length;
	}

	public double maxRotorReverseFlowInboardFraction() {
		double max = 0.0;
		for (double fraction : rotorReverseFlowInboardFraction) {
			max = Math.max(max, fraction);
		}
		return max;
	}

	public double rotorTipMach(int index) {
		return rotorTipMach[index];
	}

	public double[] rotorTipMach() {
		return Arrays.copyOf(rotorTipMach, rotorTipMach.length);
	}

	void setRotorTipMach(int index, double value) {
		rotorTipMach[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 2.0) : 0.0;
	}

	public double averageRotorTipMach() {
		double sum = 0.0;
		for (double mach : rotorTipMach) {
			sum += mach;
		}
		return sum / rotorTipMach.length;
	}

	public double maxRotorTipMach() {
		double max = 0.0;
		for (double mach : rotorTipMach) {
			max = Math.max(max, mach);
		}
		return max;
	}

	public double rotorCompressibilityThrustScale(int index) {
		return rotorCompressibilityThrustScale[index];
	}

	public double[] rotorCompressibilityThrustScale() {
		return Arrays.copyOf(rotorCompressibilityThrustScale, rotorCompressibilityThrustScale.length);
	}

	void setRotorCompressibilityThrustScale(int index, double value) {
		rotorCompressibilityThrustScale[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 1.0;
	}

	public double averageRotorCompressibilityThrustScale() {
		double sum = 0.0;
		for (double scale : rotorCompressibilityThrustScale) {
			sum += scale;
		}
		return sum / rotorCompressibilityThrustScale.length;
	}

	public double minRotorCompressibilityThrustScale() {
		double min = 1.0;
		for (double scale : rotorCompressibilityThrustScale) {
			min = Math.min(min, scale);
		}
		return min;
	}

	public double maxRotorCompressibilityThrustLossPercent() {
		return (1.0 - minRotorCompressibilityThrustScale()) * 100.0;
	}

	public double rotorReynoldsNumber(int index) {
		return rotorReynoldsNumber[index];
	}

	public double[] rotorReynoldsNumber() {
		return Arrays.copyOf(rotorReynoldsNumber, rotorReynoldsNumber.length);
	}

	void setRotorReynoldsNumber(int index, double value) {
		rotorReynoldsNumber[index] = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	public double averageRotorReynoldsNumber() {
		double sum = 0.0;
		for (double reynolds : rotorReynoldsNumber) {
			sum += reynolds;
		}
		return sum / rotorReynoldsNumber.length;
	}

	public double maxRotorReynoldsNumber() {
		double max = 0.0;
		for (double reynolds : rotorReynoldsNumber) {
			max = Math.max(max, reynolds);
		}
		return max;
	}

	public double rotorReynoldsIndex(int index) {
		return rotorReynoldsIndex[index];
	}

	public double[] rotorReynoldsIndex() {
		return Arrays.copyOf(rotorReynoldsIndex, rotorReynoldsIndex.length);
	}

	void setRotorReynoldsIndex(int index, double value) {
		rotorReynoldsIndex[index] = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	public double averageRotorReynoldsIndex() {
		double sum = 0.0;
		for (double reynoldsIndex : rotorReynoldsIndex) {
			sum += reynoldsIndex;
		}
		return sum / rotorReynoldsIndex.length;
	}

	public double minPositiveRotorReynoldsIndex() {
		double min = Double.POSITIVE_INFINITY;
		for (double reynoldsIndex : rotorReynoldsIndex) {
			if (reynoldsIndex > 1.0e-9) {
				min = Math.min(min, reynoldsIndex);
			}
		}
		return Double.isFinite(min) ? min : 0.0;
	}

	public double rotorLowReynoldsLoss(int index) {
		return rotorLowReynoldsLoss[index];
	}

	public double[] rotorLowReynoldsLoss() {
		return Arrays.copyOf(rotorLowReynoldsLoss, rotorLowReynoldsLoss.length);
	}

	void setRotorLowReynoldsLoss(int index, double value) {
		rotorLowReynoldsLoss[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
	}

	public double averageRotorLowReynoldsLoss() {
		double sum = 0.0;
		for (double loss : rotorLowReynoldsLoss) {
			sum += loss;
		}
		return sum / rotorLowReynoldsLoss.length;
	}

	public double maxRotorLowReynoldsLoss() {
		double max = 0.0;
		for (double loss : rotorLowReynoldsLoss) {
			max = Math.max(max, loss);
		}
		return max;
	}

	public double rotorBladeAngleOfAttackRadians(int index) {
		return rotorBladeAngleOfAttackRadians[index];
	}

	public double[] rotorBladeAngleOfAttackRadians() {
		return Arrays.copyOf(rotorBladeAngleOfAttackRadians, rotorBladeAngleOfAttackRadians.length);
	}

	void setRotorBladeAngleOfAttackRadians(int index, double value) {
		rotorBladeAngleOfAttackRadians[index] = Double.isFinite(value)
				? MathUtil.clamp(value, Math.toRadians(-45.0), Math.toRadians(45.0))
				: 0.0;
	}

	public double averageRotorBladeAngleOfAttackRadians() {
		double sum = 0.0;
		for (double angle : rotorBladeAngleOfAttackRadians) {
			sum += angle;
		}
		return sum / rotorBladeAngleOfAttackRadians.length;
	}

	public double maxAbsRotorBladeAngleOfAttackRadians() {
		double max = 0.0;
		for (double angle : rotorBladeAngleOfAttackRadians) {
			max = Math.max(max, Math.abs(angle));
		}
		return max;
	}

	public double rotorBladeElementStallIntensity(int index) {
		return rotorBladeElementStallIntensity[index];
	}

	public double[] rotorBladeElementStallIntensity() {
		return Arrays.copyOf(rotorBladeElementStallIntensity, rotorBladeElementStallIntensity.length);
	}

	void setRotorBladeElementStallIntensity(int index, double value) {
		rotorBladeElementStallIntensity[index] = MathUtil.clamp(value, 0.0, 1.0);
	}

	public double averageRotorBladeElementStallIntensity() {
		double sum = 0.0;
		for (double stall : rotorBladeElementStallIntensity) {
			sum += stall;
		}
		return sum / rotorBladeElementStallIntensity.length;
	}

	public double maxRotorBladeElementStallIntensity() {
		double max = 0.0;
		for (double stall : rotorBladeElementStallIntensity) {
			max = Math.max(max, stall);
		}
		return max;
	}

	public double rotorBladeDissymmetryIntensity(int index) {
		return rotorBladeDissymmetryIntensity[index];
	}

	public double[] rotorBladeDissymmetryIntensity() {
		return Arrays.copyOf(rotorBladeDissymmetryIntensity, rotorBladeDissymmetryIntensity.length);
	}

	void setRotorBladeDissymmetryIntensity(int index, double value) {
		rotorBladeDissymmetryIntensity[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
	}

	public double averageRotorBladeDissymmetryIntensity() {
		double sum = 0.0;
		for (double intensity : rotorBladeDissymmetryIntensity) {
			sum += intensity;
		}
		return sum / rotorBladeDissymmetryIntensity.length;
	}

	public double maxRotorBladeDissymmetryIntensity() {
		double max = 0.0;
		for (double intensity : rotorBladeDissymmetryIntensity) {
			max = Math.max(max, intensity);
		}
		return max;
	}

	public double rotorBladePassRippleIntensity(int index) {
		return rotorBladePassRippleIntensity[index];
	}

	public double[] rotorBladePassRippleIntensity() {
		return Arrays.copyOf(rotorBladePassRippleIntensity, rotorBladePassRippleIntensity.length);
	}

	void setRotorBladePassRippleIntensity(int index, double value) {
		rotorBladePassRippleIntensity[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
	}

	public double averageRotorBladePassRippleIntensity() {
		double sum = 0.0;
		for (double intensity : rotorBladePassRippleIntensity) {
			sum += intensity;
		}
		return sum / rotorBladePassRippleIntensity.length;
	}

	public double maxRotorBladePassRippleIntensity() {
		double max = 0.0;
		for (double intensity : rotorBladePassRippleIntensity) {
			max = Math.max(max, intensity);
		}
		return max;
	}

	public double rotorAerodynamicLoadFactor(int index) {
		return rotorAerodynamicLoadFactor[index];
	}

	public double[] rotorAerodynamicLoadFactor() {
		return Arrays.copyOf(rotorAerodynamicLoadFactor, rotorAerodynamicLoadFactor.length);
	}

	void setRotorAerodynamicLoadFactor(int index, double value) {
		rotorAerodynamicLoadFactor[index] = MathUtil.clamp(value, 0.0, 2.0);
	}

	public double averageRotorAerodynamicLoadFactor() {
		double sum = 0.0;
		for (double loadFactor : rotorAerodynamicLoadFactor) {
			sum += loadFactor;
		}
		return sum / rotorAerodynamicLoadFactor.length;
	}

	public double rotorDiskWindGradientThrustLossFraction(int index) {
		return rotorDiskWindGradientThrustLossFraction[index];
	}

	public double[] rotorDiskWindGradientThrustLossFraction() {
		return Arrays.copyOf(rotorDiskWindGradientThrustLossFraction, rotorDiskWindGradientThrustLossFraction.length);
	}

	void setRotorDiskWindGradientThrustLossFraction(int index, double value) {
		rotorDiskWindGradientThrustLossFraction[index] =
				Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
	}

	public double averageRotorDiskWindGradientThrustLossFraction() {
		double sum = 0.0;
		for (double loss : rotorDiskWindGradientThrustLossFraction) {
			sum += loss;
		}
		return sum / rotorDiskWindGradientThrustLossFraction.length;
	}

	public double maxRotorDiskWindGradientThrustLossFraction() {
		double max = 0.0;
		for (double loss : rotorDiskWindGradientThrustLossFraction) {
			max = Math.max(max, loss);
		}
		return max;
	}

	public double rotorDiskWindGradientLoadFactor(int index) {
		return rotorDiskWindGradientLoadFactor[index];
	}

	public double[] rotorDiskWindGradientLoadFactor() {
		return Arrays.copyOf(rotorDiskWindGradientLoadFactor, rotorDiskWindGradientLoadFactor.length);
	}

	void setRotorDiskWindGradientLoadFactor(int index, double value) {
		rotorDiskWindGradientLoadFactor[index] =
				Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 0.18) : 0.0;
	}

	public double averageRotorDiskWindGradientLoadFactor() {
		double sum = 0.0;
		for (double load : rotorDiskWindGradientLoadFactor) {
			sum += load;
		}
		return sum / rotorDiskWindGradientLoadFactor.length;
	}

	public double maxRotorDiskWindGradientLoadFactor() {
		double max = 0.0;
		for (double load : rotorDiskWindGradientLoadFactor) {
			max = Math.max(max, load);
		}
		return max;
	}

	public double rotorDiskWindGradientVibration(int index) {
		return rotorDiskWindGradientVibration[index];
	}

	public double[] rotorDiskWindGradientVibration() {
		return Arrays.copyOf(rotorDiskWindGradientVibration, rotorDiskWindGradientVibration.length);
	}

	void setRotorDiskWindGradientVibration(int index, double value) {
		rotorDiskWindGradientVibration[index] =
				Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 0.18) : 0.0;
	}

	public double averageRotorDiskWindGradientVibration() {
		double sum = 0.0;
		for (double vibration : rotorDiskWindGradientVibration) {
			sum += vibration;
		}
		return sum / rotorDiskWindGradientVibration.length;
	}

	public double maxRotorDiskWindGradientVibration() {
		double max = 0.0;
		for (double vibration : rotorDiskWindGradientVibration) {
			max = Math.max(max, vibration);
		}
		return max;
	}

	public double rotorDiskWindGradientStallIntensity(int index) {
		return rotorDiskWindGradientStallIntensity[index];
	}

	public double[] rotorDiskWindGradientStallIntensity() {
		return Arrays.copyOf(rotorDiskWindGradientStallIntensity, rotorDiskWindGradientStallIntensity.length);
	}

	void setRotorDiskWindGradientStallIntensity(int index, double value) {
		rotorDiskWindGradientStallIntensity[index] =
				Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 0.16) : 0.0;
	}

	public double averageRotorDiskWindGradientStallIntensity() {
		double sum = 0.0;
		for (double stall : rotorDiskWindGradientStallIntensity) {
			sum += stall;
		}
		return sum / rotorDiskWindGradientStallIntensity.length;
	}

	public double maxRotorDiskWindGradientStallIntensity() {
		double max = 0.0;
		for (double stall : rotorDiskWindGradientStallIntensity) {
			max = Math.max(max, stall);
		}
		return max;
	}

	public double rotorInPlaneDragForceNewtons(int index) {
		return rotorInPlaneDragForceNewtons[index];
	}

	public double[] rotorInPlaneDragForceNewtons() {
		return Arrays.copyOf(rotorInPlaneDragForceNewtons, rotorInPlaneDragForceNewtons.length);
	}

	void setRotorInPlaneDragForceNewtons(int index, double value) {
		rotorInPlaneDragForceNewtons[index] = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	public double rotorInPlaneDragShaftTorqueNewtonMeters(int index) {
		return rotorInPlaneDragShaftTorqueNewtonMeters[index];
	}

	public double[] rotorInPlaneDragShaftTorqueNewtonMeters() {
		return Arrays.copyOf(rotorInPlaneDragShaftTorqueNewtonMeters,
				rotorInPlaneDragShaftTorqueNewtonMeters.length);
	}

	void setRotorInPlaneDragShaftTorqueNewtonMeters(int index, double value) {
		rotorInPlaneDragShaftTorqueNewtonMeters[index] =
				Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	public double rotorInPlaneDragShaftPowerWatts(int index) {
		return rotorInPlaneDragShaftPowerWatts[index];
	}

	public double[] rotorInPlaneDragShaftPowerWatts() {
		return Arrays.copyOf(rotorInPlaneDragShaftPowerWatts, rotorInPlaneDragShaftPowerWatts.length);
	}

	void setRotorInPlaneDragShaftPowerWatts(int index, double value) {
		rotorInPlaneDragShaftPowerWatts[index] = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	public double averageRotorInPlaneDragForceNewtons() {
		double sum = 0.0;
		for (double force : rotorInPlaneDragForceNewtons) {
			sum += force;
		}
		return sum / rotorInPlaneDragForceNewtons.length;
	}

	public double maxRotorInPlaneDragForceNewtons() {
		double max = 0.0;
		for (double force : rotorInPlaneDragForceNewtons) {
			max = Math.max(max, force);
		}
		return max;
	}

	public double averageRotorInPlaneDragShaftTorqueNewtonMeters() {
		double sum = 0.0;
		for (double torque : rotorInPlaneDragShaftTorqueNewtonMeters) {
			sum += torque;
		}
		return sum / rotorInPlaneDragShaftTorqueNewtonMeters.length;
	}

	public double maxRotorInPlaneDragShaftTorqueNewtonMeters() {
		double max = 0.0;
		for (double torque : rotorInPlaneDragShaftTorqueNewtonMeters) {
			max = Math.max(max, torque);
		}
		return max;
	}

	public double averageRotorInPlaneDragShaftPowerWatts() {
		double sum = 0.0;
		for (double power : rotorInPlaneDragShaftPowerWatts) {
			sum += power;
		}
		return sum / rotorInPlaneDragShaftPowerWatts.length;
	}

	public double maxRotorInPlaneDragShaftPowerWatts() {
		double max = 0.0;
		for (double power : rotorInPlaneDragShaftPowerWatts) {
			max = Math.max(max, power);
		}
		return max;
	}

	public double rotorFlappingForceNewtons(int index) {
		return rotorFlappingForceNewtons[index];
	}

	public double[] rotorFlappingForceNewtons() {
		return Arrays.copyOf(rotorFlappingForceNewtons, rotorFlappingForceNewtons.length);
	}

	void setRotorFlappingForceNewtons(int index, double value) {
		rotorFlappingForceNewtons[index] = Math.max(0.0, value);
	}

	public double averageRotorFlappingForceNewtons() {
		double sum = 0.0;
		for (double flappingForce : rotorFlappingForceNewtons) {
			sum += flappingForce;
		}
		return sum / rotorFlappingForceNewtons.length;
	}

	public double rotorFlappingTiltRadians(int index) {
		return rotorFlappingTiltRadians[index];
	}

	public double[] rotorFlappingTiltRadians() {
		return Arrays.copyOf(rotorFlappingTiltRadians, rotorFlappingTiltRadians.length);
	}

	void setRotorFlappingTiltRadians(int index, double value) {
		rotorFlappingTiltRadians[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, Math.toRadians(18.0)) : 0.0;
	}

	public double averageRotorFlappingTiltRadians() {
		double sum = 0.0;
		for (double tilt : rotorFlappingTiltRadians) {
			sum += tilt;
		}
		return sum / rotorFlappingTiltRadians.length;
	}

	public double maxRotorFlappingTiltRadians() {
		double max = 0.0;
		for (double tilt : rotorFlappingTiltRadians) {
			max = Math.max(max, tilt);
		}
		return max;
	}

	public double rotorConingIntensity(int index) {
		return rotorConingIntensity[index];
	}

	public double[] rotorConingIntensity() {
		return Arrays.copyOf(rotorConingIntensity, rotorConingIntensity.length);
	}

	void setRotorConingIntensity(int index, double value) {
		rotorConingIntensity[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
	}

	public double averageRotorConingIntensity() {
		double sum = 0.0;
		for (double coning : rotorConingIntensity) {
			sum += coning;
		}
		return sum / rotorConingIntensity.length;
	}

	public double maxRotorConingIntensity() {
		double max = 0.0;
		for (double coning : rotorConingIntensity) {
			max = Math.max(max, coning);
		}
		return max;
	}

	public double rotorConingAngleRadians(int index) {
		return rotorConingAngleRadians[index];
	}

	public double[] rotorConingAngleRadians() {
		return Arrays.copyOf(rotorConingAngleRadians, rotorConingAngleRadians.length);
	}

	void setRotorConingAngleRadians(int index, double value) {
		rotorConingAngleRadians[index] = Double.isFinite(value)
				? MathUtil.clamp(value, 0.0, Math.toRadians(6.0))
				: 0.0;
	}

	public double averageRotorConingAngleRadians() {
		double sum = 0.0;
		for (double angle : rotorConingAngleRadians) {
			sum += angle;
		}
		return sum / rotorConingAngleRadians.length;
	}

	public double maxRotorConingAngleRadians() {
		double max = 0.0;
		for (double angle : rotorConingAngleRadians) {
			max = Math.max(max, angle);
		}
		return max;
	}

	public double rotorStallIntensity(int index) {
		return rotorStallIntensity[index];
	}

	public double[] rotorStallIntensity() {
		return Arrays.copyOf(rotorStallIntensity, rotorStallIntensity.length);
	}

	void setRotorStallIntensity(int index, double value) {
		rotorStallIntensity[index] = MathUtil.clamp(value, 0.0, 1.0);
	}

	public double averageRotorStallIntensity() {
		double sum = 0.0;
		for (double stall : rotorStallIntensity) {
			sum += stall;
		}
		return sum / rotorStallIntensity.length;
	}

	public double rotorWindmillingIntensity(int index) {
		return rotorWindmillingIntensity[index];
	}

	public double[] rotorWindmillingIntensity() {
		return Arrays.copyOf(rotorWindmillingIntensity, rotorWindmillingIntensity.length);
	}

	void setRotorWindmillingIntensity(int index, double value) {
		rotorWindmillingIntensity[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
	}

	public double averageRotorWindmillingIntensity() {
		double sum = 0.0;
		for (double intensity : rotorWindmillingIntensity) {
			sum += intensity;
		}
		return sum / rotorWindmillingIntensity.length;
	}

	public double maxRotorWindmillingIntensity() {
		double max = 0.0;
		for (double intensity : rotorWindmillingIntensity) {
			max = Math.max(max, intensity);
		}
		return max;
	}

	public double rotorSurfaceScrapeIntensity(int index) {
		return rotorSurfaceScrapeIntensity[index];
	}

	public double[] rotorSurfaceScrapeIntensity() {
		return Arrays.copyOf(rotorSurfaceScrapeIntensity, rotorSurfaceScrapeIntensity.length);
	}

	public void addRotorSurfaceScrapeIntensity(int index, double value) {
		if (!Double.isFinite(value)) {
			return;
		}
		rotorSurfaceScrapeIntensity[index] = Math.max(
				rotorSurfaceScrapeIntensity[index],
				MathUtil.clamp(value, 0.0, 1.0)
		);
	}

	void setRotorSurfaceScrapeIntensity(int index, double value) {
		rotorSurfaceScrapeIntensity[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
	}

	public double averageRotorSurfaceScrapeIntensity() {
		double sum = 0.0;
		for (double scrape : rotorSurfaceScrapeIntensity) {
			sum += scrape;
		}
		return sum / rotorSurfaceScrapeIntensity.length;
	}

	public double maxRotorSurfaceScrapeIntensity() {
		double max = 0.0;
		for (double scrape : rotorSurfaceScrapeIntensity) {
			max = Math.max(max, scrape);
		}
		return max;
	}

	public double rotorWakeInterferenceIntensity(int index) {
		return rotorWakeInterferenceIntensity[index];
	}

	public double[] rotorWakeInterferenceIntensity() {
		return Arrays.copyOf(rotorWakeInterferenceIntensity, rotorWakeInterferenceIntensity.length);
	}

	void setRotorWakeInterferenceIntensity(int index, double value) {
		rotorWakeInterferenceIntensity[index] = MathUtil.clamp(value, 0.0, 1.0);
	}

	public double averageRotorWakeInterferenceIntensity() {
		double sum = 0.0;
		for (double interference : rotorWakeInterferenceIntensity) {
			sum += interference;
		}
		return sum / rotorWakeInterferenceIntensity.length;
	}

	public double maxRotorWakeInterferenceIntensity() {
		double max = 0.0;
		for (double interference : rotorWakeInterferenceIntensity) {
			max = Math.max(max, interference);
		}
		return max;
	}

	public double rotorWakeThrustScale(int index) {
		return rotorWakeThrustScale[index];
	}

	public double[] rotorWakeThrustScale() {
		return Arrays.copyOf(rotorWakeThrustScale, rotorWakeThrustScale.length);
	}

	void setRotorWakeThrustScale(int index, double value) {
		rotorWakeThrustScale[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.72, 1.0) : 1.0;
	}

	public double averageRotorWakeThrustScale() {
		double sum = 0.0;
		for (double scale : rotorWakeThrustScale) {
			sum += scale;
		}
		return sum / rotorWakeThrustScale.length;
	}

	public double minRotorWakeThrustScale() {
		double min = 1.0;
		for (double scale : rotorWakeThrustScale) {
			min = Math.min(min, scale);
		}
		return min;
	}

	public double rotorWetThrustScale(int index) {
		return rotorWetThrustScale[index];
	}

	public double[] rotorWetThrustScale() {
		return Arrays.copyOf(rotorWetThrustScale, rotorWetThrustScale.length);
	}

	void setRotorWetThrustScale(int index, double value) {
		rotorWetThrustScale[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.08, 1.0) : 1.0;
	}

	public double averageRotorWetThrustScale() {
		double sum = 0.0;
		for (double scale : rotorWetThrustScale) {
			sum += scale;
		}
		return sum / rotorWetThrustScale.length;
	}

	public double minRotorWetThrustScale() {
		double min = 1.0;
		for (double scale : rotorWetThrustScale) {
			min = Math.min(min, scale);
		}
		return min;
	}

	public double rotorIcingSeverity(int index) {
		return rotorIcingSeverity[index];
	}

	public double[] rotorIcingSeverity() {
		return Arrays.copyOf(rotorIcingSeverity, rotorIcingSeverity.length);
	}

	void setRotorIcingSeverity(int index, double value) {
		rotorIcingSeverity[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.25) : 0.0;
	}

	public double averageRotorIcingSeverity() {
		double sum = 0.0;
		for (double severity : rotorIcingSeverity) {
			sum += severity;
		}
		return sum / rotorIcingSeverity.length;
	}

	public double maxRotorIcingSeverity() {
		double max = 0.0;
		for (double severity : rotorIcingSeverity) {
			max = Math.max(max, severity);
		}
		return max;
	}

	public double rotorIcingThrustScale(int index) {
		return rotorIcingThrustScale[index];
	}

	public double[] rotorIcingThrustScale() {
		return Arrays.copyOf(rotorIcingThrustScale, rotorIcingThrustScale.length);
	}

	void setRotorIcingThrustScale(int index, double value) {
		rotorIcingThrustScale[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.70, 1.0) : 1.0;
	}

	public double averageRotorIcingThrustScale() {
		double sum = 0.0;
		for (double scale : rotorIcingThrustScale) {
			sum += scale;
		}
		return sum / rotorIcingThrustScale.length;
	}

	public double minRotorIcingThrustScale() {
		double min = 1.0;
		for (double scale : rotorIcingThrustScale) {
			min = Math.min(min, scale);
		}
		return min;
	}

	public double rotorIcingPowerScale(int index) {
		return rotorIcingPowerScale[index];
	}

	public double[] rotorIcingPowerScale() {
		return Arrays.copyOf(rotorIcingPowerScale, rotorIcingPowerScale.length);
	}

	void setRotorIcingPowerScale(int index, double value) {
		rotorIcingPowerScale[index] = Double.isFinite(value) ? MathUtil.clamp(value, 1.0, 1.90) : 1.0;
	}

	public double averageRotorIcingPowerScale() {
		double sum = 0.0;
		for (double scale : rotorIcingPowerScale) {
			sum += scale;
		}
		return sum / rotorIcingPowerScale.length;
	}

	public double maxRotorIcingPowerScale() {
		double max = 1.0;
		for (double scale : rotorIcingPowerScale) {
			max = Math.max(max, scale);
		}
		return max;
	}

	public double rotorCoaxialLoadBias(int index) {
		return rotorCoaxialLoadBias[index];
	}

	public double[] rotorCoaxialLoadBias() {
		return Arrays.copyOf(rotorCoaxialLoadBias, rotorCoaxialLoadBias.length);
	}

	void setRotorCoaxialLoadBias(int index, double value) {
		rotorCoaxialLoadBias[index] = Double.isFinite(value) ? MathUtil.clamp(value, -0.35, 0.35) : 0.0;
	}

	public double averageAbsRotorCoaxialLoadBias() {
		double sum = 0.0;
		for (double bias : rotorCoaxialLoadBias) {
			sum += Math.abs(bias);
		}
		return sum / rotorCoaxialLoadBias.length;
	}

	public double maxAbsRotorCoaxialLoadBias() {
		double max = 0.0;
		for (double bias : rotorCoaxialLoadBias) {
			max = Math.max(max, Math.abs(bias));
		}
		return max;
	}

	public double rotorCoaxialLoadBiasTarget(int index) {
		return rotorCoaxialLoadBiasTarget[index];
	}

	public double[] rotorCoaxialLoadBiasTarget() {
		return Arrays.copyOf(rotorCoaxialLoadBiasTarget, rotorCoaxialLoadBiasTarget.length);
	}

	void setRotorCoaxialLoadBiasTarget(int index, double value) {
		rotorCoaxialLoadBiasTarget[index] = Double.isFinite(value) ? MathUtil.clamp(value, -0.35, 0.35) : 0.0;
	}

	public double averageAbsRotorCoaxialLoadBiasTarget() {
		double sum = 0.0;
		for (double bias : rotorCoaxialLoadBiasTarget) {
			sum += Math.abs(bias);
		}
		return sum / rotorCoaxialLoadBiasTarget.length;
	}

	public double maxAbsRotorCoaxialLoadBiasTarget() {
		double max = 0.0;
		for (double bias : rotorCoaxialLoadBiasTarget) {
			max = Math.max(max, Math.abs(bias));
		}
		return max;
	}

	public double rotorCoaxialLoadBiasClipping(int index) {
		return rotorCoaxialLoadBiasClipping[index];
	}

	public double[] rotorCoaxialLoadBiasClipping() {
		return Arrays.copyOf(rotorCoaxialLoadBiasClipping, rotorCoaxialLoadBiasClipping.length);
	}

	void setRotorCoaxialLoadBiasClipping(int index, double value) {
		rotorCoaxialLoadBiasClipping[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 0.35) : 0.0;
	}

	public double averageRotorCoaxialLoadBiasClipping() {
		double sum = 0.0;
		for (double clipping : rotorCoaxialLoadBiasClipping) {
			sum += clipping;
		}
		return sum / rotorCoaxialLoadBiasClipping.length;
	}

	public double maxRotorCoaxialLoadBiasClipping() {
		double max = 0.0;
		for (double clipping : rotorCoaxialLoadBiasClipping) {
			max = Math.max(max, clipping);
		}
		return max;
	}

	public double rotorCoaxialAllocationLoadFraction(int index) {
		return rotorCoaxialAllocationLoadFraction[index];
	}

	public double[] rotorCoaxialAllocationLoadFraction() {
		return Arrays.copyOf(rotorCoaxialAllocationLoadFraction, rotorCoaxialAllocationLoadFraction.length);
	}

	void setRotorCoaxialAllocationLoadFraction(int index, double value) {
		rotorCoaxialAllocationLoadFraction[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
	}

	public double averageRotorCoaxialAllocationLoadFraction() {
		double sum = 0.0;
		for (double loadFraction : rotorCoaxialAllocationLoadFraction) {
			sum += loadFraction;
		}
		return sum / rotorCoaxialAllocationLoadFraction.length;
	}

	public double maxRotorCoaxialAllocationLoadFraction() {
		double max = 0.0;
		for (double loadFraction : rotorCoaxialAllocationLoadFraction) {
			max = Math.max(max, loadFraction);
		}
		return max;
	}

	public double rotorCoaxialAllocationCommandRatio(int index) {
		return rotorCoaxialAllocationCommandRatio[index];
	}

	public double[] rotorCoaxialAllocationCommandRatio() {
		return Arrays.copyOf(rotorCoaxialAllocationCommandRatio, rotorCoaxialAllocationCommandRatio.length);
	}

	void setRotorCoaxialAllocationCommandRatio(int index, double value) {
		rotorCoaxialAllocationCommandRatio[index] = Double.isFinite(value) ? MathUtil.clamp(value, 1.0, 2.0) : 1.0;
	}

	public double averageRotorCoaxialAllocationCommandRatio() {
		double sum = 0.0;
		for (double ratio : rotorCoaxialAllocationCommandRatio) {
			sum += ratio;
		}
		return sum / rotorCoaxialAllocationCommandRatio.length;
	}

	public double maxRotorCoaxialAllocationCommandRatio() {
		double max = 1.0;
		for (double ratio : rotorCoaxialAllocationCommandRatio) {
			max = Math.max(max, ratio);
		}
		return max;
	}

	public double rotorCoaxialAllocationMechanicalGainPercent(int index) {
		return rotorCoaxialAllocationMechanicalGainPercent[index];
	}

	public double[] rotorCoaxialAllocationMechanicalGainPercent() {
		return Arrays.copyOf(rotorCoaxialAllocationMechanicalGainPercent, rotorCoaxialAllocationMechanicalGainPercent.length);
	}

	void setRotorCoaxialAllocationMechanicalGainPercent(int index, double value) {
		rotorCoaxialAllocationMechanicalGainPercent[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 25.0) : 0.0;
	}

	public double averageRotorCoaxialAllocationMechanicalGainPercent() {
		double sum = 0.0;
		for (double gain : rotorCoaxialAllocationMechanicalGainPercent) {
			sum += gain;
		}
		return sum / rotorCoaxialAllocationMechanicalGainPercent.length;
	}

	public double maxRotorCoaxialAllocationMechanicalGainPercent() {
		double max = 0.0;
		for (double gain : rotorCoaxialAllocationMechanicalGainPercent) {
			max = Math.max(max, gain);
		}
		return max;
	}

	public double rotorCoaxialAllocationElectricalGainPercent(int index) {
		return rotorCoaxialAllocationElectricalGainPercent[index];
	}

	public double[] rotorCoaxialAllocationElectricalGainPercent() {
		return Arrays.copyOf(rotorCoaxialAllocationElectricalGainPercent, rotorCoaxialAllocationElectricalGainPercent.length);
	}

	void setRotorCoaxialAllocationElectricalGainPercent(int index, double value) {
		rotorCoaxialAllocationElectricalGainPercent[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 25.0) : 0.0;
	}

	public double averageRotorCoaxialAllocationElectricalGainPercent() {
		double sum = 0.0;
		for (double gain : rotorCoaxialAllocationElectricalGainPercent) {
			sum += gain;
		}
		return sum / rotorCoaxialAllocationElectricalGainPercent.length;
	}

	public double maxRotorCoaxialAllocationElectricalGainPercent() {
		double max = 0.0;
		for (double gain : rotorCoaxialAllocationElectricalGainPercent) {
			max = Math.max(max, gain);
		}
		return max;
	}

	public double rotorCoaxialAllocationUncertaintyPercent(int index) {
		return rotorCoaxialAllocationUncertaintyPercent[index];
	}

	public double[] rotorCoaxialAllocationUncertaintyPercent() {
		return Arrays.copyOf(rotorCoaxialAllocationUncertaintyPercent, rotorCoaxialAllocationUncertaintyPercent.length);
	}

	void setRotorCoaxialAllocationUncertaintyPercent(int index, double value) {
		rotorCoaxialAllocationUncertaintyPercent[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 25.0) : 0.0;
	}

	public double averageRotorCoaxialAllocationUncertaintyPercent() {
		double sum = 0.0;
		for (double uncertainty : rotorCoaxialAllocationUncertaintyPercent) {
			sum += uncertainty;
		}
		return sum / rotorCoaxialAllocationUncertaintyPercent.length;
	}

	public double maxRotorCoaxialAllocationUncertaintyPercent() {
		double max = 0.0;
		for (double uncertainty : rotorCoaxialAllocationUncertaintyPercent) {
			max = Math.max(max, uncertainty);
		}
		return max;
	}

	public double rotorWakeSwirlVelocityMetersPerSecond(int index) {
		return rotorWakeSwirlVelocityMetersPerSecond[index];
	}

	public double[] rotorWakeSwirlVelocityMetersPerSecond() {
		return Arrays.copyOf(rotorWakeSwirlVelocityMetersPerSecond, rotorWakeSwirlVelocityMetersPerSecond.length);
	}

	void setRotorWakeSwirlVelocityMetersPerSecond(int index, double value) {
		rotorWakeSwirlVelocityMetersPerSecond[index] = Double.isFinite(value)
				? MathUtil.clamp(value, 0.0, 20.0)
				: 0.0;
	}

	public double averageRotorWakeSwirlVelocityMetersPerSecond() {
		double sum = 0.0;
		for (double velocity : rotorWakeSwirlVelocityMetersPerSecond) {
			sum += velocity;
		}
		return sum / rotorWakeSwirlVelocityMetersPerSecond.length;
	}

	public double maxRotorWakeSwirlVelocityMetersPerSecond() {
		double max = 0.0;
		for (double velocity : rotorWakeSwirlVelocityMetersPerSecond) {
			max = Math.max(max, velocity);
		}
		return max;
	}

	public double rotorArmFlexIntensity(int index) {
		return rotorArmFlexIntensity[index];
	}

	public double[] rotorArmFlexIntensity() {
		return Arrays.copyOf(rotorArmFlexIntensity, rotorArmFlexIntensity.length);
	}

	void setRotorArmFlexIntensity(int index, double value) {
		rotorArmFlexIntensity[index] = MathUtil.clamp(value, 0.0, 1.0);
	}

	public double averageRotorArmFlexIntensity() {
		double sum = 0.0;
		for (double flex : rotorArmFlexIntensity) {
			sum += flex;
		}
		return sum / rotorArmFlexIntensity.length;
	}

	public double maxRotorArmFlexIntensity() {
		double max = 0.0;
		for (double flex : rotorArmFlexIntensity) {
			max = Math.max(max, flex);
		}
		return max;
	}

	public double rotorArmFlexDeflectionMeters(int index) {
		return rotorArmFlexDeflectionMeters[index];
	}

	public double[] rotorArmFlexDeflectionMeters() {
		return Arrays.copyOf(rotorArmFlexDeflectionMeters, rotorArmFlexDeflectionMeters.length);
	}

	void setRotorArmFlexDeflectionMeters(int index, double value) {
		rotorArmFlexDeflectionMeters[index] = Double.isFinite(value)
				? MathUtil.clamp(value, 0.0, 0.25)
				: 0.0;
	}

	public double averageRotorArmFlexDeflectionMeters() {
		double sum = 0.0;
		for (double deflection : rotorArmFlexDeflectionMeters) {
			sum += deflection;
		}
		return sum / rotorArmFlexDeflectionMeters.length;
	}

	public double maxRotorArmFlexDeflectionMeters() {
		double max = 0.0;
		for (double deflection : rotorArmFlexDeflectionMeters) {
			max = Math.max(max, deflection);
		}
		return max;
	}

	public double rotorArmFlexTiltRadians(int index) {
		return rotorArmFlexTiltRadians[index];
	}

	public double[] rotorArmFlexTiltRadians() {
		return Arrays.copyOf(rotorArmFlexTiltRadians, rotorArmFlexTiltRadians.length);
	}

	void setRotorArmFlexTiltRadians(int index, double value) {
		rotorArmFlexTiltRadians[index] = Double.isFinite(value)
				? MathUtil.clamp(value, 0.0, Math.toRadians(16.0))
				: 0.0;
	}

	public double averageRotorArmFlexTiltRadians() {
		double sum = 0.0;
		for (double tilt : rotorArmFlexTiltRadians) {
			sum += tilt;
		}
		return sum / rotorArmFlexTiltRadians.length;
	}

	public double maxRotorArmFlexTiltRadians() {
		double max = 0.0;
		for (double tilt : rotorArmFlexTiltRadians) {
			max = Math.max(max, tilt);
		}
		return max;
	}

	public double rotorVibration() {
		return rotorVibration;
	}

	void setRotorVibration(double rotorVibration) {
		this.rotorVibration = MathUtil.clamp(rotorVibration, 0.0, 1.0);
	}

	public double rotorDamageVibration(int index) {
		return rotorDamageVibration[index];
	}

	public double[] rotorDamageVibration() {
		return Arrays.copyOf(rotorDamageVibration, rotorDamageVibration.length);
	}

	void setRotorDamageVibration(int index, double value) {
		rotorDamageVibration[index] = Double.isFinite(value) ? MathUtil.clamp(value, 0.0, 1.0) : 0.0;
	}

	public double averageRotorDamageVibration() {
		double sum = 0.0;
		for (double vibration : rotorDamageVibration) {
			sum += vibration;
		}
		return sum / rotorDamageVibration.length;
	}

	public double maxRotorDamageVibration() {
		double max = 0.0;
		for (double vibration : rotorDamageVibration) {
			max = Math.max(max, vibration);
		}
		return max;
	}

	public double rotorInflowSkewIntensity() {
		return rotorInflowSkewIntensity;
	}

	void setRotorInflowSkewIntensity(double rotorInflowSkewIntensity) {
		this.rotorInflowSkewIntensity = MathUtil.clamp(rotorInflowSkewIntensity, 0.0, 1.0);
	}

	public Vec3 rotorInflowSkewTorqueBodyNewtonMeters() {
		return rotorInflowSkewTorqueBodyNewtonMeters;
	}

	void setRotorInflowSkewTorqueBodyNewtonMeters(Vec3 rotorInflowSkewTorqueBodyNewtonMeters) {
		this.rotorInflowSkewTorqueBodyNewtonMeters = rotorInflowSkewTorqueBodyNewtonMeters == null ? Vec3.ZERO : rotorInflowSkewTorqueBodyNewtonMeters;
	}

	public Vec3 rotorBladeDissymmetryTorqueBodyNewtonMeters() {
		return rotorBladeDissymmetryTorqueBodyNewtonMeters;
	}

	void setRotorBladeDissymmetryTorqueBodyNewtonMeters(Vec3 rotorBladeDissymmetryTorqueBodyNewtonMeters) {
		this.rotorBladeDissymmetryTorqueBodyNewtonMeters = rotorBladeDissymmetryTorqueBodyNewtonMeters == null ? Vec3.ZERO : rotorBladeDissymmetryTorqueBodyNewtonMeters;
	}

	public Vec3 rotorWakeSwirlTorqueBodyNewtonMeters() {
		return rotorWakeSwirlTorqueBodyNewtonMeters;
	}

	void setRotorWakeSwirlTorqueBodyNewtonMeters(Vec3 rotorWakeSwirlTorqueBodyNewtonMeters) {
		this.rotorWakeSwirlTorqueBodyNewtonMeters = rotorWakeSwirlTorqueBodyNewtonMeters == null ? Vec3.ZERO : rotorWakeSwirlTorqueBodyNewtonMeters;
	}

	public Vec3 rotorFlappingTorqueBodyNewtonMeters() {
		return rotorFlappingTorqueBodyNewtonMeters;
	}

	void setRotorFlappingTorqueBodyNewtonMeters(Vec3 rotorFlappingTorqueBodyNewtonMeters) {
		this.rotorFlappingTorqueBodyNewtonMeters = rotorFlappingTorqueBodyNewtonMeters == null ? Vec3.ZERO : rotorFlappingTorqueBodyNewtonMeters;
	}

	public Vec3 rotorActiveBrakingTorqueBodyNewtonMeters() {
		return rotorActiveBrakingTorqueBodyNewtonMeters;
	}

	void setRotorActiveBrakingTorqueBodyNewtonMeters(Vec3 rotorActiveBrakingTorqueBodyNewtonMeters) {
		this.rotorActiveBrakingTorqueBodyNewtonMeters = rotorActiveBrakingTorqueBodyNewtonMeters == null ? Vec3.ZERO : rotorActiveBrakingTorqueBodyNewtonMeters;
	}

	public Vec3 rotorInertiaTorqueBodyNewtonMeters() {
		return rotorInertiaTorqueBodyNewtonMeters;
	}

	void setRotorInertiaTorqueBodyNewtonMeters(Vec3 rotorInertiaTorqueBodyNewtonMeters) {
		this.rotorInertiaTorqueBodyNewtonMeters = rotorInertiaTorqueBodyNewtonMeters == null ? Vec3.ZERO : rotorInertiaTorqueBodyNewtonMeters;
	}

	public Vec3 rotorAccelerationReactionTorqueBodyNewtonMeters() {
		return rotorAccelerationReactionTorqueBodyNewtonMeters;
	}

	void setRotorAccelerationReactionTorqueBodyNewtonMeters(Vec3 rotorAccelerationReactionTorqueBodyNewtonMeters) {
		this.rotorAccelerationReactionTorqueBodyNewtonMeters = rotorAccelerationReactionTorqueBodyNewtonMeters == null ? Vec3.ZERO : rotorAccelerationReactionTorqueBodyNewtonMeters;
	}

	public Vec3 rotorGyroscopicTorqueBodyNewtonMeters() {
		return rotorGyroscopicTorqueBodyNewtonMeters;
	}

	void setRotorGyroscopicTorqueBodyNewtonMeters(Vec3 rotorGyroscopicTorqueBodyNewtonMeters) {
		this.rotorGyroscopicTorqueBodyNewtonMeters = rotorGyroscopicTorqueBodyNewtonMeters == null ? Vec3.ZERO : rotorGyroscopicTorqueBodyNewtonMeters;
	}

	public Vec3 rotorAngularDragTorqueBodyNewtonMeters() {
		return rotorAngularDragTorqueBodyNewtonMeters;
	}

	void setRotorAngularDragTorqueBodyNewtonMeters(Vec3 rotorAngularDragTorqueBodyNewtonMeters) {
		this.rotorAngularDragTorqueBodyNewtonMeters = rotorAngularDragTorqueBodyNewtonMeters == null ? Vec3.ZERO : rotorAngularDragTorqueBodyNewtonMeters;
	}

	public double rotorHealth(int index) {
		return rotorHealth[index];
	}

	public double[] rotorHealth() {
		return Arrays.copyOf(rotorHealth, rotorHealth.length);
	}

	public double averageRotorHealth() {
		double sum = 0.0;
		for (double health : rotorHealth) {
			sum += health;
		}
		return sum / rotorHealth.length;
	}

	public void damageRotor(int index, double damage) {
		rotorHealth[index] = MathUtil.clamp(rotorHealth[index] - damage, 0.0, 1.0);
	}

	public void damageAllRotors(double damage) {
		for (int i = 0; i < rotorHealth.length; i++) {
			damageRotor(i, damage);
		}
	}

	public void repairAllRotors() {
		Arrays.fill(rotorHealth, 1.0);
	}

	public double averageMotorPower(DroneConfig config) {
		double sum = 0.0;
		for (int i = 0; i < motorOmegaRadiansPerSecond.length; i++) {
			sum += motorPower(config, i);
		}
		return sum / motorOmegaRadiansPerSecond.length;
	}

	public double speedMetersPerSecond() {
		return velocityMetersPerSecond.length();
	}

	public double batteryVoltage() {
		return batteryVoltage;
	}

	void setBatteryVoltage(double batteryVoltage) {
		this.batteryVoltage = batteryVoltage;
	}

	public double batteryOpenCircuitVoltage() {
		return batteryOpenCircuitVoltage;
	}

	void setBatteryOpenCircuitVoltage(double batteryOpenCircuitVoltage) {
		this.batteryOpenCircuitVoltage = Math.max(0.0, batteryOpenCircuitVoltage);
	}

	public double batteryOhmicSagVoltage() {
		return batteryOhmicSagVoltage;
	}

	void setBatteryOhmicSagVoltage(double batteryOhmicSagVoltage) {
		this.batteryOhmicSagVoltage = Math.max(0.0, batteryOhmicSagVoltage);
	}

	public double batteryTransientSagVoltage() {
		return batteryTransientSagVoltage;
	}

	void setBatteryTransientSagVoltage(double batteryTransientSagVoltage) {
		this.batteryTransientSagVoltage = Math.max(0.0, batteryTransientSagVoltage);
	}

	public double batterySlowPolarizationVoltage() {
		return batterySlowPolarizationVoltage;
	}

	void setBatterySlowPolarizationVoltage(double batterySlowPolarizationVoltage) {
		this.batterySlowPolarizationVoltage = Double.isFinite(batterySlowPolarizationVoltage)
				? Math.max(0.0, batterySlowPolarizationVoltage)
				: 0.0;
	}

	public double batteryTotalSagVoltage() {
		return batteryOhmicSagVoltage + batteryTransientSagVoltage + batterySlowPolarizationVoltage;
	}

	public double batteryRegenerativeCurrentAmps() {
		return batteryRegenerativeCurrentAmps;
	}

	void setBatteryRegenerativeCurrentAmps(double batteryRegenerativeCurrentAmps) {
		this.batteryRegenerativeCurrentAmps = Math.max(0.0, batteryRegenerativeCurrentAmps);
	}

	public double batteryVoltageSpike() {
		return batteryVoltageSpike;
	}

	void setBatteryVoltageSpike(double batteryVoltageSpike) {
		this.batteryVoltageSpike = Math.max(0.0, batteryVoltageSpike);
	}

	public double batteryBusRippleVoltage() {
		return batteryBusRippleVoltage;
	}

	void setBatteryBusRippleVoltage(double batteryBusRippleVoltage) {
		this.batteryBusRippleVoltage = Double.isFinite(batteryBusRippleVoltage)
				? Math.max(0.0, batteryBusRippleVoltage)
				: 0.0;
	}

	public double batteryEffectiveResistanceOhms() {
		return batteryEffectiveResistanceOhms;
	}

	void setBatteryEffectiveResistanceOhms(double batteryEffectiveResistanceOhms) {
		this.batteryEffectiveResistanceOhms = Double.isFinite(batteryEffectiveResistanceOhms)
				? Math.max(0.0, batteryEffectiveResistanceOhms)
				: 0.0;
	}

	public double batteryTwentyPercentSagCurrentAmps() {
		return batteryTwentyPercentSagCurrentAmps;
	}

	void setBatteryTwentyPercentSagCurrentAmps(double batteryTwentyPercentSagCurrentAmps) {
		this.batteryTwentyPercentSagCurrentAmps = Double.isFinite(batteryTwentyPercentSagCurrentAmps)
				? Math.max(0.0, batteryTwentyPercentSagCurrentAmps)
				: 0.0;
	}

	public double batteryTwentyPercentSagCurrentMargin() {
		return batteryTwentyPercentSagCurrentMargin;
	}

	void setBatteryTwentyPercentSagCurrentMargin(double batteryTwentyPercentSagCurrentMargin) {
		this.batteryTwentyPercentSagCurrentMargin = Double.isFinite(batteryTwentyPercentSagCurrentMargin)
				? Math.max(0.0, batteryTwentyPercentSagCurrentMargin)
				: 0.0;
	}

	public double batteryTemperatureCelsius() {
		return batteryTemperatureCelsius;
	}

	void setBatteryTemperatureCelsius(double batteryTemperatureCelsius) {
		this.batteryTemperatureCelsius = Double.isFinite(batteryTemperatureCelsius)
				? MathUtil.clamp(batteryTemperatureCelsius, -40.0, 120.0)
				: 25.0;
	}

	public double batteryCoolingFactor() {
		return batteryCoolingFactor;
	}

	void setBatteryCoolingFactor(double batteryCoolingFactor) {
		this.batteryCoolingFactor = Double.isFinite(batteryCoolingFactor)
				? MathUtil.clamp(batteryCoolingFactor, 0.20, 4.0)
				: 1.0;
	}

	public double a4mcPackVentilationEfficiency() {
		return a4mcPackVentilationEfficiency;
	}

	void setA4mcPackVentilationEfficiency(double a4mcPackVentilationEfficiency) {
		this.a4mcPackVentilationEfficiency = Double.isFinite(a4mcPackVentilationEfficiency)
				? MathUtil.clamp(a4mcPackVentilationEfficiency, 0.0, 1.0)
				: 1.0;
	}

	public double batteryThermalLimit() {
		return batteryThermalLimit;
	}

	void setBatteryThermalLimit(double batteryThermalLimit) {
		this.batteryThermalLimit = MathUtil.clamp(batteryThermalLimit, 0.0, 1.0);
	}

	public double batteryAmpSecondsConsumed() {
		return batteryAmpSecondsConsumed;
	}

	void addBatteryAmpSecondsConsumed(double ampSeconds) {
		this.batteryAmpSecondsConsumed = Math.max(0.0, this.batteryAmpSecondsConsumed + ampSeconds);
	}

	public void setBatteryAmpSecondsConsumed(double batteryAmpSecondsConsumed) {
		this.batteryAmpSecondsConsumed = Math.max(0.0, batteryAmpSecondsConsumed);
	}

	public double batteryEquivalentCycles() {
		return batteryEquivalentCycles;
	}

	void addBatteryEquivalentCycles(double equivalentCycles) {
		if (Double.isFinite(equivalentCycles) && equivalentCycles > 0.0) {
			this.batteryEquivalentCycles = MathUtil.clamp(this.batteryEquivalentCycles + equivalentCycles, 0.0, 5000.0);
		}
	}

	public void setBatteryEquivalentCycles(double batteryEquivalentCycles) {
		this.batteryEquivalentCycles = Double.isFinite(batteryEquivalentCycles)
				? MathUtil.clamp(batteryEquivalentCycles, 0.0, 5000.0)
				: 0.0;
	}

	public double batteryStateOfChargeResistanceScale() {
		return batteryStateOfChargeResistanceScale;
	}

	void setBatteryStateOfChargeResistanceScale(double batteryStateOfChargeResistanceScale) {
		this.batteryStateOfChargeResistanceScale = Double.isFinite(batteryStateOfChargeResistanceScale)
				? MathUtil.clamp(batteryStateOfChargeResistanceScale, 1.0, 1.80)
				: 1.0;
	}

	public double batteryTemperatureResistanceScale() {
		return batteryTemperatureResistanceScale;
	}

	void setBatteryTemperatureResistanceScale(double batteryTemperatureResistanceScale) {
		this.batteryTemperatureResistanceScale = Double.isFinite(batteryTemperatureResistanceScale)
				? MathUtil.clamp(batteryTemperatureResistanceScale, 0.72, 2.85)
				: 1.0;
	}

	public double batteryResistanceAgingScale() {
		return batteryResistanceAgingScale;
	}

	void setBatteryResistanceAgingScale(double batteryResistanceAgingScale) {
		this.batteryResistanceAgingScale = Double.isFinite(batteryResistanceAgingScale)
				? MathUtil.clamp(batteryResistanceAgingScale, 1.0, 1.35)
				: 1.0;
	}

	public double batteryCapacityAgingScale() {
		return batteryCapacityAgingScale;
	}

	void setBatteryCapacityAgingScale(double batteryCapacityAgingScale) {
		this.batteryCapacityAgingScale = Double.isFinite(batteryCapacityAgingScale)
				? MathUtil.clamp(batteryCapacityAgingScale, 0.35, 1.0)
				: 1.0;
	}

	public double batteryPolarizationResistanceScale() {
		return batteryPolarizationResistanceScale;
	}

	void setBatteryPolarizationResistanceScale(double batteryPolarizationResistanceScale) {
		this.batteryPolarizationResistanceScale = Double.isFinite(batteryPolarizationResistanceScale)
				? MathUtil.clamp(batteryPolarizationResistanceScale, 1.0, 1.35)
				: 1.0;
	}

	public double batteryCurrentAmps() {
		return batteryCurrentAmps;
	}

	void setBatteryCurrentAmps(double batteryCurrentAmps) {
		this.batteryCurrentAmps = Math.max(0.0, batteryCurrentAmps);
	}

	public double batteryStateOfCharge() {
		return batteryStateOfCharge;
	}

	void setBatteryStateOfCharge(double batteryStateOfCharge) {
		this.batteryStateOfCharge = MathUtil.clamp(batteryStateOfCharge, 0.0, 1.0);
	}

	public double batteryPowerLimit() {
		return batteryPowerLimit;
	}

	public double batteryCurrentLimit() {
		return batteryCurrentLimit;
	}

	void setBatteryCurrentLimit(double batteryCurrentLimit) {
		this.batteryCurrentLimit = MathUtil.clamp(batteryCurrentLimit, 0.0, 1.0);
	}

	void setBatteryPowerLimit(double batteryPowerLimit) {
		this.batteryPowerLimit = MathUtil.clamp(batteryPowerLimit, 0.0, 1.0);
	}

	public double motorThermalLimit() {
		return motorThermalLimit;
	}

	void setMotorThermalLimit(double motorThermalLimit) {
		this.motorThermalLimit = MathUtil.clamp(motorThermalLimit, 0.0, 1.0);
	}

	public double escThermalLimit() {
		return escThermalLimit;
	}

	void setEscThermalLimit(double escThermalLimit) {
		this.escThermalLimit = MathUtil.clamp(escThermalLimit, 0.0, 1.0);
	}

	public Vec3 relativeAirVelocityBodyMetersPerSecond() {
		return relativeAirVelocityBodyMetersPerSecond;
	}

	void setRelativeAirVelocityBodyMetersPerSecond(Vec3 relativeAirVelocityBodyMetersPerSecond) {
		this.relativeAirVelocityBodyMetersPerSecond = relativeAirVelocityBodyMetersPerSecond == null ? Vec3.ZERO : relativeAirVelocityBodyMetersPerSecond;
	}

	public Vec3 effectiveWindVelocityWorldMetersPerSecond() {
		return effectiveWindVelocityWorldMetersPerSecond;
	}

	void setEffectiveWindVelocityWorldMetersPerSecond(Vec3 effectiveWindVelocityWorldMetersPerSecond) {
		this.effectiveWindVelocityWorldMetersPerSecond = effectiveWindVelocityWorldMetersPerSecond == null ? Vec3.ZERO : effectiveWindVelocityWorldMetersPerSecond;
	}

	public Vec3 windGustVelocityWorldMetersPerSecond() {
		return windGustVelocityWorldMetersPerSecond;
	}

	void setWindGustVelocityWorldMetersPerSecond(Vec3 windGustVelocityWorldMetersPerSecond) {
		this.windGustVelocityWorldMetersPerSecond = windGustVelocityWorldMetersPerSecond == null ? Vec3.ZERO : windGustVelocityWorldMetersPerSecond;
	}

	public double windGustSpeedMetersPerSecond() {
		return windGustVelocityWorldMetersPerSecond.length();
	}

	public Vec3 drydenTurbulenceVelocityWorldMetersPerSecond() {
		return drydenTurbulenceVelocityWorldMetersPerSecond;
	}

	void setDrydenTurbulenceVelocityWorldMetersPerSecond(Vec3 drydenTurbulenceVelocityWorldMetersPerSecond) {
		this.drydenTurbulenceVelocityWorldMetersPerSecond = drydenTurbulenceVelocityWorldMetersPerSecond == null
				? Vec3.ZERO
				: drydenTurbulenceVelocityWorldMetersPerSecond;
	}

	public double drydenTurbulenceSpeedMetersPerSecond() {
		return drydenTurbulenceVelocityWorldMetersPerSecond.length();
	}

	public Vec3 windBurbleVelocityWorldMetersPerSecond() {
		return windBurbleVelocityWorldMetersPerSecond;
	}

	void setWindBurbleVelocityWorldMetersPerSecond(Vec3 windBurbleVelocityWorldMetersPerSecond) {
		this.windBurbleVelocityWorldMetersPerSecond = windBurbleVelocityWorldMetersPerSecond == null
				? Vec3.ZERO
				: windBurbleVelocityWorldMetersPerSecond;
	}

	public double windBurbleSpeedMetersPerSecond() {
		return windBurbleVelocityWorldMetersPerSecond.length();
	}

	public Vec3 a4mcSourceGustVelocityWorldMetersPerSecond() {
		return a4mcSourceGustVelocityWorldMetersPerSecond;
	}

	void setA4mcSourceGustVelocityWorldMetersPerSecond(Vec3 a4mcSourceGustVelocityWorldMetersPerSecond) {
		this.a4mcSourceGustVelocityWorldMetersPerSecond = a4mcSourceGustVelocityWorldMetersPerSecond == null
				? Vec3.ZERO
				: a4mcSourceGustVelocityWorldMetersPerSecond;
	}

	public double a4mcSourceGustSpeedMetersPerSecond() {
		return a4mcSourceGustVelocityWorldMetersPerSecond.length();
	}

	public Vec3 a4mcUpdraftVelocityWorldMetersPerSecond() {
		return a4mcUpdraftVelocityWorldMetersPerSecond;
	}

	void setA4mcUpdraftVelocityWorldMetersPerSecond(Vec3 a4mcUpdraftVelocityWorldMetersPerSecond) {
		this.a4mcUpdraftVelocityWorldMetersPerSecond = a4mcUpdraftVelocityWorldMetersPerSecond == null
				? Vec3.ZERO
				: a4mcUpdraftVelocityWorldMetersPerSecond;
	}

	public double a4mcUpdraftSpeedMetersPerSecond() {
		return Math.abs(a4mcUpdraftVelocityWorldMetersPerSecond.y());
	}

	public Vec3 a4mcTerrainShearVelocityWorldMetersPerSecond() {
		return a4mcTerrainShearVelocityWorldMetersPerSecond;
	}

	void setA4mcTerrainShearVelocityWorldMetersPerSecond(Vec3 a4mcTerrainShearVelocityWorldMetersPerSecond) {
		this.a4mcTerrainShearVelocityWorldMetersPerSecond = a4mcTerrainShearVelocityWorldMetersPerSecond == null
				? Vec3.ZERO
				: a4mcTerrainShearVelocityWorldMetersPerSecond;
	}

	public double a4mcTerrainShearSpeedMetersPerSecond() {
		return a4mcTerrainShearVelocityWorldMetersPerSecond.length();
	}

	public double windShearAccelerationMetersPerSecondSquared() {
		return windShearAccelerationMetersPerSecondSquared;
	}

	void setWindShearAccelerationMetersPerSecondSquared(double windShearAccelerationMetersPerSecondSquared) {
		this.windShearAccelerationMetersPerSecondSquared = Double.isFinite(windShearAccelerationMetersPerSecondSquared)
				? Math.max(0.0, windShearAccelerationMetersPerSecondSquared)
				: 0.0;
	}

	public double airspeedMetersPerSecond() {
		return airspeedMetersPerSecond;
	}

	void setAirspeedMetersPerSecond(double airspeedMetersPerSecond) {
		this.airspeedMetersPerSecond = Math.max(0.0, airspeedMetersPerSecond);
	}

	public double angleOfAttackRadians() {
		return angleOfAttackRadians;
	}

	void setAngleOfAttackRadians(double angleOfAttackRadians) {
		this.angleOfAttackRadians = Double.isFinite(angleOfAttackRadians) ? angleOfAttackRadians : 0.0;
	}

	public double sideslipRadians() {
		return sideslipRadians;
	}

	void setSideslipRadians(double sideslipRadians) {
		this.sideslipRadians = Double.isFinite(sideslipRadians) ? sideslipRadians : 0.0;
	}

	public double airframeSeparatedFlowIntensity() {
		return airframeSeparatedFlowIntensity;
	}

	void setAirframeSeparatedFlowIntensity(double airframeSeparatedFlowIntensity) {
		this.airframeSeparatedFlowIntensity = MathUtil.clamp(airframeSeparatedFlowIntensity, 0.0, 1.0);
	}

	public Vec3 airframeBodyDragForceBodyNewtons() {
		return airframeBodyDragForceBodyNewtons;
	}

	void setAirframeBodyDragForceBodyNewtons(Vec3 airframeBodyDragForceBodyNewtons) {
		this.airframeBodyDragForceBodyNewtons = airframeBodyDragForceBodyNewtons == null ? Vec3.ZERO : airframeBodyDragForceBodyNewtons;
	}

	public Vec3 linearDampingDragForceWorldNewtons() {
		return linearDampingDragForceWorldNewtons;
	}

	void setLinearDampingDragForceWorldNewtons(Vec3 linearDampingDragForceWorldNewtons) {
		this.linearDampingDragForceWorldNewtons = linearDampingDragForceWorldNewtons == null ? Vec3.ZERO : linearDampingDragForceWorldNewtons;
	}

	public double airframeDragAlongFlowNewtons() {
		return airframeDragAlongFlowNewtons;
	}

	void setAirframeDragAlongFlowNewtons(double airframeDragAlongFlowNewtons) {
		this.airframeDragAlongFlowNewtons = nonNegativeFinite(airframeDragAlongFlowNewtons);
	}

	public double airframeDragEquivalentLinearCoefficient() {
		return airframeDragEquivalentLinearCoefficient;
	}

	void setAirframeDragEquivalentLinearCoefficient(double airframeDragEquivalentLinearCoefficient) {
		this.airframeDragEquivalentLinearCoefficient = nonNegativeFinite(airframeDragEquivalentLinearCoefficient);
	}

	public double airframeDragEquivalentCdAMetersSquared() {
		return airframeDragEquivalentCdAMetersSquared;
	}

	void setAirframeDragEquivalentCdAMetersSquared(double airframeDragEquivalentCdAMetersSquared) {
		this.airframeDragEquivalentCdAMetersSquared = nonNegativeFinite(airframeDragEquivalentCdAMetersSquared);
	}

	public double airframeDragImavReferenceRatio() {
		return airframeDragImavReferenceRatio;
	}

	void setAirframeDragImavReferenceRatio(double airframeDragImavReferenceRatio) {
		this.airframeDragImavReferenceRatio = nonNegativeFinite(airframeDragImavReferenceRatio);
	}

	public Vec3 airframeLiftForceBodyNewtons() {
		return airframeLiftForceBodyNewtons;
	}

	void setAirframeLiftForceBodyNewtons(Vec3 airframeLiftForceBodyNewtons) {
		this.airframeLiftForceBodyNewtons = airframeLiftForceBodyNewtons == null ? Vec3.ZERO : airframeLiftForceBodyNewtons;
	}

	public Vec3 groundEffectDragForceBodyNewtons() {
		return groundEffectDragForceBodyNewtons;
	}

	void setGroundEffectDragForceBodyNewtons(Vec3 groundEffectDragForceBodyNewtons) {
		this.groundEffectDragForceBodyNewtons = groundEffectDragForceBodyNewtons == null ? Vec3.ZERO : groundEffectDragForceBodyNewtons;
	}

	public Vec3 groundEffectLevelingTorqueBodyNewtonMeters() {
		return groundEffectLevelingTorqueBodyNewtonMeters;
	}

	void setGroundEffectLevelingTorqueBodyNewtonMeters(Vec3 groundEffectLevelingTorqueBodyNewtonMeters) {
		this.groundEffectLevelingTorqueBodyNewtonMeters = groundEffectLevelingTorqueBodyNewtonMeters == null ? Vec3.ZERO : groundEffectLevelingTorqueBodyNewtonMeters;
	}

	public Vec3 rotorWashDragForceBodyNewtons() {
		return rotorWashDragForceBodyNewtons;
	}

	void setRotorWashDragForceBodyNewtons(Vec3 rotorWashDragForceBodyNewtons) {
		this.rotorWashDragForceBodyNewtons = rotorWashDragForceBodyNewtons == null ? Vec3.ZERO : rotorWashDragForceBodyNewtons;
	}

	public Vec3 rotorWallEffectForceBodyNewtons() {
		return rotorWallEffectForceBodyNewtons;
	}

	void setRotorWallEffectForceBodyNewtons(Vec3 rotorWallEffectForceBodyNewtons) {
		this.rotorWallEffectForceBodyNewtons = rotorWallEffectForceBodyNewtons == null ? Vec3.ZERO : rotorWallEffectForceBodyNewtons;
	}

	public double propwashIntensity() {
		return propwashIntensity;
	}

	void setPropwashIntensity(double propwashIntensity) {
		this.propwashIntensity = MathUtil.clamp(propwashIntensity, 0.0, 1.0);
	}

	public double propwashWakeIntensity() {
		return propwashWakeIntensity;
	}

	void setPropwashWakeIntensity(double propwashWakeIntensity) {
		this.propwashWakeIntensity = MathUtil.clamp(propwashWakeIntensity, 0.0, 1.0);
	}

	public double vortexRingStateIntensity() {
		return vortexRingStateIntensity;
	}

	void setVortexRingStateIntensity(double vortexRingStateIntensity) {
		this.vortexRingStateIntensity = MathUtil.clamp(vortexRingStateIntensity, 0.0, 1.0);
	}

	public double vortexRingThrustBuffetAmplitude() {
		return vortexRingThrustBuffetAmplitude;
	}

	void setVortexRingThrustBuffetAmplitude(double vortexRingThrustBuffetAmplitude) {
		this.vortexRingThrustBuffetAmplitude = MathUtil.clamp(vortexRingThrustBuffetAmplitude, 0.0, 1.0);
	}

	public double maxVortexRingThrustBuffetAmplitude() {
		return vortexRingMaxThrustBuffetAmplitude;
	}

	void setVortexRingMaxThrustBuffetAmplitude(double vortexRingMaxThrustBuffetAmplitude) {
		this.vortexRingMaxThrustBuffetAmplitude = MathUtil.clamp(vortexRingMaxThrustBuffetAmplitude, 0.0, 1.0);
	}

	public Vec3 vortexRingBuffetForceBodyNewtons() {
		return vortexRingBuffetForceBodyNewtons;
	}

	void setVortexRingBuffetForceBodyNewtons(Vec3 vortexRingBuffetForceBodyNewtons) {
		this.vortexRingBuffetForceBodyNewtons = vortexRingBuffetForceBodyNewtons == null ? Vec3.ZERO : vortexRingBuffetForceBodyNewtons;
	}

	public Vec3 propwashTorqueBodyNewtonMeters() {
		return propwashTorqueBodyNewtonMeters;
	}

	void setPropwashTorqueBodyNewtonMeters(Vec3 propwashTorqueBodyNewtonMeters) {
		this.propwashTorqueBodyNewtonMeters = propwashTorqueBodyNewtonMeters == null ? Vec3.ZERO : propwashTorqueBodyNewtonMeters;
	}

	public Vec3 windTurbulenceTorqueBodyNewtonMeters() {
		return windTurbulenceTorqueBodyNewtonMeters;
	}

	void setWindTurbulenceTorqueBodyNewtonMeters(Vec3 windTurbulenceTorqueBodyNewtonMeters) {
		this.windTurbulenceTorqueBodyNewtonMeters = windTurbulenceTorqueBodyNewtonMeters == null ? Vec3.ZERO : windTurbulenceTorqueBodyNewtonMeters;
	}

	public Vec3 airframeAerodynamicTorqueBodyNewtonMeters() {
		return airframeAerodynamicTorqueBodyNewtonMeters;
	}

	void setAirframeAerodynamicTorqueBodyNewtonMeters(Vec3 airframeAerodynamicTorqueBodyNewtonMeters) {
		this.airframeAerodynamicTorqueBodyNewtonMeters = airframeAerodynamicTorqueBodyNewtonMeters == null ? Vec3.ZERO : airframeAerodynamicTorqueBodyNewtonMeters;
	}

	public Vec3 airframePressureCenterTorqueBodyNewtonMeters() {
		return airframePressureCenterTorqueBodyNewtonMeters;
	}

	void setAirframePressureCenterTorqueBodyNewtonMeters(Vec3 airframePressureCenterTorqueBodyNewtonMeters) {
		this.airframePressureCenterTorqueBodyNewtonMeters = airframePressureCenterTorqueBodyNewtonMeters == null ? Vec3.ZERO : airframePressureCenterTorqueBodyNewtonMeters;
	}

	public Vec3 airframeAngularDragTorqueBodyNewtonMeters() {
		return airframeAngularDragTorqueBodyNewtonMeters;
	}

	void setAirframeAngularDragTorqueBodyNewtonMeters(Vec3 airframeAngularDragTorqueBodyNewtonMeters) {
		this.airframeAngularDragTorqueBodyNewtonMeters = airframeAngularDragTorqueBodyNewtonMeters == null ? Vec3.ZERO : airframeAngularDragTorqueBodyNewtonMeters;
	}

	public double mixerSaturation() {
		return mixerSaturation;
	}

	void setMixerSaturation(double mixerSaturation) {
		this.mixerSaturation = MathUtil.clamp(mixerSaturation, 0.0, 1.0);
	}

	public double mixerLowSaturation() {
		return mixerLowSaturation;
	}

	void setMixerLowSaturation(double mixerLowSaturation) {
		this.mixerLowSaturation = MathUtil.clamp(mixerLowSaturation, 0.0, 1.0);
	}

	public double mixerHighSaturation() {
		return mixerHighSaturation;
	}

	void setMixerHighSaturation(double mixerHighSaturation) {
		this.mixerHighSaturation = MathUtil.clamp(mixerHighSaturation, 0.0, 1.0);
	}

	public double mixerLowHeadroom() {
		return mixerLowHeadroom;
	}

	void setMixerLowHeadroom(double mixerLowHeadroom) {
		this.mixerLowHeadroom = MathUtil.clamp(mixerLowHeadroom, 0.0, 1.0);
	}

	public double mixerHighHeadroom() {
		return mixerHighHeadroom;
	}

	void setMixerHighHeadroom(double mixerHighHeadroom) {
		this.mixerHighHeadroom = MathUtil.clamp(mixerHighHeadroom, 0.0, 1.0);
	}

	public Vec3 mixerOutputTorqueBodyNewtonMeters() {
		return mixerOutputTorqueBodyNewtonMeters;
	}

	void setMixerOutputTorqueBodyNewtonMeters(Vec3 mixerOutputTorqueBodyNewtonMeters) {
		this.mixerOutputTorqueBodyNewtonMeters = finiteVectorOrZero(mixerOutputTorqueBodyNewtonMeters);
	}

	public Vec3 mixerAxisAuthority() {
		return mixerAxisAuthority;
	}

	void setMixerAxisAuthority(Vec3 mixerAxisAuthority) {
		this.mixerAxisAuthority = mixerAxisAuthority == null || !mixerAxisAuthority.isFinite()
				? new Vec3(1.0, 1.0, 1.0)
				: mixerAxisAuthority.clamp(0.0, 1.0);
	}

	public double minMixerAxisAuthority() {
		return Math.min(mixerAxisAuthority.x(), Math.min(mixerAxisAuthority.y(), mixerAxisAuthority.z()));
	}

	public double pidAttenuation() {
		return pidAttenuation;
	}

	void setPidAttenuation(double pidAttenuation) {
		this.pidAttenuation = MathUtil.clamp(pidAttenuation, 0.0, 1.0);
	}

	public double antiGravityBoost() {
		return antiGravityBoost;
	}

	void setAntiGravityBoost(double antiGravityBoost) {
		this.antiGravityBoost = Math.max(0.0, antiGravityBoost);
	}
}
