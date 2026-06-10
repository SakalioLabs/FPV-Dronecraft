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
	private double gyroDynamicNotchFrequencyHertz;
	private double gyroDynamicNotchAttenuation;
	private double gyroBladePassNotchFrequencyHertz;
	private double gyroBladePassNotchAttenuation;
	private double barometerAltitudeMeters;
	private double barometerVerticalSpeedMetersPerSecond;
	private double barometerPressureHectopascals = 1013.25;
	private double barometerErrorMeters;
	private double barometerPropwashErrorMeters;
	private FlightMode flightMode = FlightMode.ACRO;
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
	private double[] motorCommutationRippleIntensity;
	private double[] escOutputCommand;
	private double escCommandFrameAgeSeconds;
	private double escCommandFrameIntervalSeconds;
	private double escCommandError;
	private double[] escDesyncIntensity;
	private double[] escTemperatureCelsius;
	private double[] escCoolingFactor;
	private double[] escThermalLimitByEsc;
	private double[] motorCurrentAmps;
	private double[] motorTemperatureCelsius;
	private double[] motorCoolingFactor;
	private double[] rotorThrustNewtons;
	private Vec3[] rotorForceBodyNewtons;
	private Vec3[] rotorTorqueBodyNewtonMeters;
	private double[] rotorInducedVelocityMetersPerSecond;
	private double[] rotorTranslationalLiftIntensity;
	private double[] rotorAdvanceRatio;
	private double[] rotorTipMach;
	private double[] rotorLowReynoldsLoss;
	private double[] rotorBladeAngleOfAttackRadians;
	private double[] rotorBladeElementStallIntensity;
	private double[] rotorBladeDissymmetryIntensity;
	private double[] rotorBladePassRippleIntensity;
	private double[] rotorAerodynamicLoadFactor;
	private double[] rotorFlappingForceNewtons;
	private double[] rotorFlappingTiltRadians;
	private double[] rotorConingIntensity;
	private double[] rotorStallIntensity;
	private double[] rotorSurfaceScrapeIntensity;
	private double[] rotorWakeInterferenceIntensity;
	private double[] rotorWakeSwirlVelocityMetersPerSecond;
	private double[] rotorArmFlexIntensity;
	private double[] rotorHealth;
	private double rotorVibration;
	private double rotorInflowSkewIntensity;
	private Vec3 rotorInflowSkewTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 rotorInertiaTorqueBodyNewtonMeters = Vec3.ZERO;
	private Vec3 rotorAngularDragTorqueBodyNewtonMeters = Vec3.ZERO;
	private double batteryVoltage;
	private double batteryOpenCircuitVoltage;
	private double batteryOhmicSagVoltage;
	private double batteryTransientSagVoltage;
	private double batteryRegenerativeCurrentAmps;
	private double batteryVoltageSpike;
	private double batteryBusRippleVoltage;
	private double batteryTemperatureCelsius = 25.0;
	private double batteryCoolingFactor = 1.0;
	private double batteryThermalLimit = 1.0;
	private double batteryAmpSecondsConsumed;
	private double batteryCurrentAmps;
	private double batteryStateOfCharge = 1.0;
	private double batteryCurrentLimit = 1.0;
	private double batteryPowerLimit = 1.0;
	private double motorThermalLimit = 1.0;
	private double escThermalLimit = 1.0;
	private Vec3 relativeAirVelocityBodyMetersPerSecond = Vec3.ZERO;
	private Vec3 effectiveWindVelocityWorldMetersPerSecond = Vec3.ZERO;
	private Vec3 windGustVelocityWorldMetersPerSecond = Vec3.ZERO;
	private double windShearAccelerationMetersPerSecondSquared;
	private double airspeedMetersPerSecond;
	private double angleOfAttackRadians;
	private double sideslipRadians;
	private double airframeSeparatedFlowIntensity;
	private Vec3 airframeLiftForceBodyNewtons = Vec3.ZERO;
	private Vec3 groundEffectDragForceBodyNewtons = Vec3.ZERO;
	private Vec3 rotorWashDragForceBodyNewtons = Vec3.ZERO;
	private Vec3 rotorWallEffectForceBodyNewtons = Vec3.ZERO;
	private double propwashIntensity;
	private double propwashWakeIntensity;
	private double vortexRingStateIntensity;
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
		motorCommutationRippleIntensity = new double[motorCount];
		escOutputCommand = new double[motorCount];
		escDesyncIntensity = new double[motorCount];
		escTemperatureCelsius = new double[motorCount];
		escCoolingFactor = new double[motorCount];
		escThermalLimitByEsc = new double[motorCount];
		motorCurrentAmps = new double[motorCount];
		motorTemperatureCelsius = new double[motorCount];
		motorCoolingFactor = new double[motorCount];
		rotorThrustNewtons = new double[motorCount];
		rotorForceBodyNewtons = new Vec3[motorCount];
		rotorTorqueBodyNewtonMeters = new Vec3[motorCount];
		rotorInducedVelocityMetersPerSecond = new double[motorCount];
		rotorTranslationalLiftIntensity = new double[motorCount];
		rotorAdvanceRatio = new double[motorCount];
		rotorTipMach = new double[motorCount];
		rotorLowReynoldsLoss = new double[motorCount];
		rotorBladeAngleOfAttackRadians = new double[motorCount];
		rotorBladeElementStallIntensity = new double[motorCount];
		rotorBladeDissymmetryIntensity = new double[motorCount];
		rotorBladePassRippleIntensity = new double[motorCount];
		rotorAerodynamicLoadFactor = new double[motorCount];
		rotorFlappingForceNewtons = new double[motorCount];
		rotorFlappingTiltRadians = new double[motorCount];
		rotorConingIntensity = new double[motorCount];
		rotorStallIntensity = new double[motorCount];
		rotorSurfaceScrapeIntensity = new double[motorCount];
		rotorWakeInterferenceIntensity = new double[motorCount];
		rotorWakeSwirlVelocityMetersPerSecond = new double[motorCount];
		rotorArmFlexIntensity = new double[motorCount];
		rotorHealth = new double[motorCount];
		Arrays.fill(rotorForceBodyNewtons, Vec3.ZERO);
		Arrays.fill(rotorTorqueBodyNewtonMeters, Vec3.ZERO);
		Arrays.fill(escTemperatureCelsius, 25.0);
		Arrays.fill(escCoolingFactor, 1.0);
		Arrays.fill(escThermalLimitByEsc, 1.0);
		Arrays.fill(motorActuatorAuthority, 1.0);
		Arrays.fill(motorVoltageHeadroom, 1.0);
		Arrays.fill(motorTemperatureCelsius, 25.0);
		Arrays.fill(motorCoolingFactor, 1.0);
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

	public void setContactTelemetry(double impactSpeedMetersPerSecond, double slipSpeedMetersPerSecond, double bounceSpeedMetersPerSecond) {
		setContactTelemetry(impactSpeedMetersPerSecond, slipSpeedMetersPerSecond, bounceSpeedMetersPerSecond, Vec3.ZERO);
	}

	public void setContactTelemetry(
			double impactSpeedMetersPerSecond,
			double slipSpeedMetersPerSecond,
			double bounceSpeedMetersPerSecond,
			Vec3 angularImpulseBodyRadiansPerSecond
	) {
		contactImpactSpeedMetersPerSecond = nonNegativeFinite(impactSpeedMetersPerSecond);
		contactSlipSpeedMetersPerSecond = nonNegativeFinite(slipSpeedMetersPerSecond);
		contactBounceSpeedMetersPerSecond = nonNegativeFinite(bounceSpeedMetersPerSecond);
		contactAngularImpulseBodyRadiansPerSecond = finiteVectorOrZero(angularImpulseBodyRadiansPerSecond);
	}

	private static double nonNegativeFinite(double value) {
		return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
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
		this.flightMode = flightMode == null ? FlightMode.ACRO : flightMode;
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

	public double averageMotorCurrentAmps() {
		double sum = 0.0;
		for (double current : motorCurrentAmps) {
			sum += current;
		}
		return sum / motorCurrentAmps.length;
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
		Arrays.fill(motorCommutationRippleIntensity, 0.0);
		Arrays.fill(escOutputCommand, 0.0);
		escCommandFrameAgeSeconds = 0.0;
		escCommandFrameIntervalSeconds = 0.0;
		escCommandError = 0.0;
		Arrays.fill(escDesyncIntensity, 0.0);
		Arrays.fill(escCoolingFactor, 1.0);
		Arrays.fill(motorCurrentAmps, 0.0);
		Arrays.fill(motorCoolingFactor, 1.0);
		Arrays.fill(rotorThrustNewtons, 0.0);
		Arrays.fill(rotorForceBodyNewtons, Vec3.ZERO);
		Arrays.fill(rotorTorqueBodyNewtonMeters, Vec3.ZERO);
		Arrays.fill(rotorInducedVelocityMetersPerSecond, 0.0);
		Arrays.fill(rotorTranslationalLiftIntensity, 0.0);
		Arrays.fill(rotorAdvanceRatio, 0.0);
		Arrays.fill(rotorTipMach, 0.0);
		Arrays.fill(rotorLowReynoldsLoss, 0.0);
		Arrays.fill(rotorBladeAngleOfAttackRadians, 0.0);
		Arrays.fill(rotorBladeElementStallIntensity, 0.0);
		Arrays.fill(rotorBladeDissymmetryIntensity, 0.0);
		Arrays.fill(rotorBladePassRippleIntensity, 0.0);
		Arrays.fill(rotorAerodynamicLoadFactor, 0.0);
		Arrays.fill(rotorFlappingForceNewtons, 0.0);
		Arrays.fill(rotorFlappingTiltRadians, 0.0);
		Arrays.fill(rotorConingIntensity, 0.0);
		Arrays.fill(rotorStallIntensity, 0.0);
		Arrays.fill(rotorSurfaceScrapeIntensity, 0.0);
		Arrays.fill(rotorWakeInterferenceIntensity, 0.0);
		Arrays.fill(rotorWakeSwirlVelocityMetersPerSecond, 0.0);
		Arrays.fill(rotorArmFlexIntensity, 0.0);
		rotorVibration = 0.0;
		rotorInflowSkewIntensity = 0.0;
		rotorInflowSkewTorqueBodyNewtonMeters = Vec3.ZERO;
		rotorInertiaTorqueBodyNewtonMeters = Vec3.ZERO;
		rotorAngularDragTorqueBodyNewtonMeters = Vec3.ZERO;
		airframeSeparatedFlowIntensity = 0.0;
		propwashIntensity = 0.0;
		propwashWakeIntensity = 0.0;
		propwashTorqueBodyNewtonMeters = Vec3.ZERO;
		vortexRingStateIntensity = 0.0;
		groundEffectDragForceBodyNewtons = Vec3.ZERO;
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

	public double rotorVibration() {
		return rotorVibration;
	}

	void setRotorVibration(double rotorVibration) {
		this.rotorVibration = MathUtil.clamp(rotorVibration, 0.0, 1.0);
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

	public Vec3 rotorInertiaTorqueBodyNewtonMeters() {
		return rotorInertiaTorqueBodyNewtonMeters;
	}

	void setRotorInertiaTorqueBodyNewtonMeters(Vec3 rotorInertiaTorqueBodyNewtonMeters) {
		this.rotorInertiaTorqueBodyNewtonMeters = rotorInertiaTorqueBodyNewtonMeters == null ? Vec3.ZERO : rotorInertiaTorqueBodyNewtonMeters;
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

	void setBatteryAmpSecondsConsumed(double batteryAmpSecondsConsumed) {
		this.batteryAmpSecondsConsumed = Math.max(0.0, batteryAmpSecondsConsumed);
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
