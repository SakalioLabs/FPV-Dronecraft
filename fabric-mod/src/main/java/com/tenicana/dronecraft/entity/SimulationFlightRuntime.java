package com.tenicana.dronecraft.entity;

import java.util.Arrays;
import java.util.UUID;

import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.sim.ContactDynamics;
import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.DronePhysics;
import com.tenicana.dronecraft.sim.DroneState;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;
import com.tenicana.dronecraft.sim.flight.ActuatorOutput;
import com.tenicana.dronecraft.sim.flight.FlightModel;
import com.tenicana.dronecraft.sim.flight.FlightStateSnapshot;
import com.tenicana.dronecraft.sim.flight.SimulationFlightModelAdapter;

import net.minecraft.world.entity.EntityDimensions;

final class SimulationFlightRuntime {
	private DronePhysics physics;
	private FlightModel flightModel;

	SimulationFlightRuntime(DroneConfig config) {
		physics = new DronePhysics(config);
		flightModel = new SimulationFlightModelAdapter(physics);
	}

	DroneState state() {
		return physics.state();
	}

	DroneConfig config() {
		return physics.config();
	}

	int rotorCount() {
		return physics.config().rotors().size();
	}

	int motorCount() {
		return physics.state().motorCount();
	}

	EntityDimensions airframeDimensions() {
		return DroneAirframeDimensions.forConfig(physics.config());
	}

	int syncedRotorCount() {
		return Math.max(1, Math.min(8, rotorCount()));
	}

	String rotorLayoutCode() {
		return RotorLayoutCodec.encode(physics.config());
	}

	boolean hasDifferentRotorCount(DroneConfig config) {
		return config.rotors().size() != rotorCount();
	}

	boolean isRotorIndexValid(int rotorIndex) {
		return rotorIndex >= 0 && rotorIndex < rotorCount();
	}

	DroneInput controlInput(UUID owner, int tickCount) {
		return DroneControlManager.get(owner, tickCount, physics.state(), physics.config());
	}

	DroneConfig flightModelConfig() {
		return physics.config();
	}

	double clampedHoverThrottle(double min, double max) {
		return MathUtil.clamp(physics.config().hoverThrottle(), min, max);
	}

	double groundEffectRayLength(double multiplier, double offsetMeters) {
		return Math.max(1.0, physics.config().groundEffectHeightMeters() * multiplier + offsetMeters);
	}

	double ceilingEffectHeightMeters() {
		return physics.config().groundEffectHeightMeters() * 1.25;
	}

	boolean verticalVelocityAtOrBelow(double maxMetersPerSecond) {
		return physics.state().velocityMetersPerSecond().y() <= maxMetersPerSecond;
	}

	double takeoffThrottleRelease(double minThrottle, double hoverThrottleScale) {
		return Math.max(minThrottle, physics.config().hoverThrottle() * hoverThrottleScale);
	}

	double takeoffThrustThresholdNewtons(double thrustToWeight) {
		return physics.config().massKg() * physics.config().gravityMetersPerSecondSquared() * thrustToWeight;
	}

	double verticalRotorThrustNewtons() {
		double thrust = 0.0;
		int rotorCount = Math.min(physics.config().rotors().size(), physics.state().motorCount());
		for (int i = 0; i < rotorCount; i++) {
			Vec3 thrustAxisWorld = physics.state().orientation().rotate(physics.config().rotors().get(i).thrustAxisBody());
			thrust += physics.state().rotorThrustNewtons(i) * Math.max(0.0, thrustAxisWorld.y());
		}
		return thrust;
	}

	void releaseGroundTakeoff(Vec3 positionMeters, double minimumVerticalSpeedMetersPerSecond) {
		Vec3 velocity = physics.state().velocityMetersPerSecond();
		setPositionAndVelocityMeters(positionMeters, new Vec3(
				velocity.x(),
				Math.max(velocity.y(), minimumVerticalSpeedMetersPerSecond),
				velocity.z()
		));
	}

	DroneWakeSource droneWakeSource() {
		DroneState state = physics.state();
		DroneConfig config = physics.config();
		double radius = 0.35;
		for (RotorSpec rotor : config.rotors()) {
			radius = Math.max(radius, rotor.positionBodyMeters().length() + rotor.radiusMeters() * 2.5);
		}
		return new DroneWakeSource(
				state.averageMotorPower(config),
				state.averageRotorInducedVelocityMetersPerSecond(),
				state.velocityMetersPerSecond(),
				MathUtil.clamp(radius, 0.35, 1.25)
		);
	}

	FlightStateSnapshot flightStateSnapshot(Vec3 positionWorldMeters, Quaternion attitude, FlightMode flightMode, boolean armed) {
		return flightStateSnapshot(
				positionWorldMeters,
				attitude,
				physics.state().angularVelocityBodyRadiansPerSecond(),
				flightMode,
				armed
		);
	}

	FlightStateSnapshot flightStateSnapshot(
			Vec3 positionWorldMeters,
			Quaternion attitude,
			Vec3 angularVelocityBodyRadiansPerSecond,
			FlightMode flightMode,
			boolean armed
	) {
		DroneState state = physics.state();
		return new FlightStateSnapshot(
				positionWorldMeters,
				state.velocityMetersPerSecond(),
				attitude,
				angularVelocityBodyRadiansPerSecond,
				flightMode,
				armed
		);
	}

	FlightStateSnapshot simulationStateSnapshot() {
		DroneState state = physics.state();
		DroneInput processed = state.processedControlInput().normalized();
		return new FlightStateSnapshot(
				state.positionMeters(),
				state.velocityMetersPerSecond(),
				state.orientation(),
				state.angularVelocityBodyRadiansPerSecond(),
				processed.flightMode(),
				processed.armed()
		);
	}

	void setPositionMeters(Vec3 positionMeters) {
		physics.state().setPositionMeters(positionMeters);
	}

	void setVelocityMetersPerSecond(Vec3 velocityMetersPerSecond) {
		physics.state().setVelocityMetersPerSecond(velocityMetersPerSecond);
	}

	void setPositionAndVelocityMeters(Vec3 positionMeters, Vec3 velocityMetersPerSecond) {
		setPositionMeters(positionMeters);
		setVelocityMetersPerSecond(velocityMetersPerSecond);
	}

	void setAngularVelocityBodyRadiansPerSecond(Vec3 angularVelocityBodyRadiansPerSecond) {
		physics.state().setAngularVelocityBodyRadiansPerSecond(angularVelocityBodyRadiansPerSecond);
	}

	void addAngularVelocityBodyRadiansPerSecond(Vec3 deltaBodyRadiansPerSecond, double min, double max) {
		physics.state().setAngularVelocityBodyRadiansPerSecond(
				physics.state().angularVelocityBodyRadiansPerSecond().add(deltaBodyRadiansPerSecond).clamp(min, max)
		);
	}

	void setContactTelemetry(double impactSpeedMetersPerSecond, double slipSpeedMetersPerSecond, double bounceSpeedMetersPerSecond) {
		physics.state().setContactTelemetry(impactSpeedMetersPerSecond, slipSpeedMetersPerSecond, bounceSpeedMetersPerSecond);
	}

	void setContactTelemetry(
			double impactSpeedMetersPerSecond,
			double slipSpeedMetersPerSecond,
			double bounceSpeedMetersPerSecond,
			Vec3 angularImpulseBodyRadiansPerSecond,
			ContactDynamics.ContactSurface contactSurface
	) {
		physics.state().setContactTelemetry(
				impactSpeedMetersPerSecond,
				slipSpeedMetersPerSecond,
				bounceSpeedMetersPerSecond,
				angularImpulseBodyRadiansPerSecond,
				contactSurface
		);
	}

	void setContactSurfaceTelemetry(ContactDynamics.ContactSurface contactSurface) {
		physics.state().setContactSurfaceTelemetry(contactSurface);
	}

	void addRotorSurfaceScrapeIntensity(int rotorIndex, double scrapeIntensity) {
		physics.state().addRotorSurfaceScrapeIntensity(rotorIndex, scrapeIntensity);
	}

	void damageAllRotors(double damage) {
		physics.state().damageAllRotors(damage);
	}

	void damageRotor(int rotorIndex, double damage) {
		physics.state().damageRotor(rotorIndex, damage);
	}

	void repairAllRotors() {
		physics.state().repairAllRotors();
	}

	void setBatteryAmpSecondsConsumed(double ampSecondsConsumed) {
		physics.state().setBatteryAmpSecondsConsumed(ampSecondsConsumed);
	}

	void setBatteryEquivalentCycles(double equivalentCycles) {
		physics.state().setBatteryEquivalentCycles(equivalentCycles);
	}

	double averageMotorRpmTelemetryRpm() {
		return physics.state().averageMotorRpmTelemetryRpm();
	}

	double motorRpmTelemetryRpm(int index) {
		if (index < 0 || index >= physics.state().motorCount()) {
			return 0.0;
		}
		return physics.state().motorRpmTelemetryRpm(index);
	}

	double averageMotorRpmTelemetryValidity() {
		return physics.state().averageMotorRpmTelemetryValidity();
	}

	double motorRpmTelemetryValidity(int index) {
		if (index < 0 || index >= physics.state().motorCount()) {
			return 0.0;
		}
		return physics.state().motorRpmTelemetryValidity(index);
	}

	double averageMotorTelemetryErpm100() {
		return betaflightErpm100FromMechanicalRpm(averageMotorRpmTelemetryRpm(), averageMotorPolePairs());
	}

	double motorTelemetryErpm100(int index) {
		return betaflightErpm100FromMechanicalRpm(motorRpmTelemetryRpm(index), motorPolePairs(index));
	}

	double averageMotorTelemetryEIntervalMicros() {
		return betaflightEIntervalMicrosFromTelemetryRpm(
				averageMotorRpmTelemetryRpm(),
				averageMotorRpmTelemetryValidity(),
				averageMotorPolePairs()
		);
	}

	double motorTelemetryEIntervalMicros(int index) {
		return betaflightEIntervalMicrosFromTelemetryRpm(
				motorRpmTelemetryRpm(index),
				motorRpmTelemetryValidity(index),
				motorPolePairs(index)
		);
	}

	double escCommandFrameAgeSeconds() {
		return physics.state().escCommandFrameAgeSeconds();
	}

	double escCommandFrameIntervalSeconds() {
		return physics.state().escCommandFrameIntervalSeconds();
	}

	double escCommandError() {
		return physics.state().escCommandError();
	}

	float rotorDamageVibration() {
		return (float) physics.state().maxRotorDamageVibration();
	}

	float rotorDynamicInflowTimeConstantSeconds() {
		return (float) physics.state().maxRotorDynamicInflowTimeConstantSeconds();
	}

	float rotorCoaxialLoadBiasTarget() {
		return (float) physics.state().maxAbsRotorCoaxialLoadBiasTarget();
	}

	float rotorCoaxialLoadBiasClipping() {
		return (float) physics.state().maxRotorCoaxialLoadBiasClipping();
	}

	float rotorCoaxialAllocationLoadFraction() {
		return (float) physics.state().maxRotorCoaxialAllocationLoadFraction();
	}

	float rotorCoaxialAllocationCommandRatio() {
		return (float) physics.state().maxRotorCoaxialAllocationCommandRatio();
	}

	float rotorCoaxialAllocationMechanicalGainPercent() {
		return (float) physics.state().maxRotorCoaxialAllocationMechanicalGainPercent();
	}

	float rotorCoaxialAllocationElectricalGainPercent() {
		return (float) physics.state().maxRotorCoaxialAllocationElectricalGainPercent();
	}

	float rotorCoaxialAllocationUncertaintyPercent() {
		return (float) physics.state().maxRotorCoaxialAllocationUncertaintyPercent();
	}

	double rotorIcingSeverity() {
		return physics.state().maxRotorIcingSeverity();
	}

	double rotorIcingThrustScale() {
		return physics.state().minRotorIcingThrustScale();
	}

	double rotorIcingPowerScale() {
		return physics.state().maxRotorIcingPowerScale();
	}

	float airframeBodyDragForceNewtons() {
		return (float) physics.state().airframeBodyDragForceBodyNewtons().length();
	}

	float linearDampingDragForceNewtons() {
		return (float) physics.state().linearDampingDragForceWorldNewtons().length();
	}

	float airframeDragAlongFlowNewtons() {
		return (float) physics.state().airframeDragAlongFlowNewtons();
	}

	float airframeDragEquivalentLinearCoefficient() {
		return (float) physics.state().airframeDragEquivalentLinearCoefficient();
	}

	float airframeDragEquivalentCdAMetersSquared() {
		return (float) physics.state().airframeDragEquivalentCdAMetersSquared();
	}

	float airframeDragImavReferenceRatio() {
		return (float) physics.state().airframeDragImavReferenceRatio();
	}

	double batteryStateOfChargeResistanceScale() {
		return physics.state().batteryStateOfChargeResistanceScale();
	}

	double batteryTemperatureResistanceScale() {
		return physics.state().batteryTemperatureResistanceScale();
	}

	double batteryPolarizationResistanceScale() {
		return physics.state().batteryPolarizationResistanceScale();
	}

	float rotorHealthOrOne(int index) {
		double[] rotorHealth = physics.state().rotorHealth();
		if (index < 0 || index >= rotorHealth.length) {
			return 1.0f;
		}
		return (float) rotorHealth[index];
	}

	double averageRotorHealth() {
		return physics.state().averageRotorHealth();
	}

	RotorHealthState rotorHealthState() {
		DroneState state = physics.state();
		return new RotorHealthState(state.averageRotorHealth(), state.rotorHealth());
	}

	double controlFrameAgeSeconds() {
		return physics.state().controlFrameAgeSeconds();
	}

	double controlFrameIntervalSeconds() {
		return physics.state().controlFrameIntervalSeconds();
	}

	double controlFrameError() {
		return physics.state().controlFrameError();
	}

	double gyroDynamicNotchSpreadHertz() {
		return physics.state().gyroDynamicNotchSpreadHertz();
	}

	double gyroRpmHarmonicNotchAttenuation() {
		return physics.state().gyroRpmHarmonicNotchAttenuation();
	}

	double gyroBladePassNotchFrequencyHertz() {
		return physics.state().gyroBladePassNotchFrequencyHertz();
	}

	double gyroBladePassNotchAttenuation() {
		return physics.state().gyroBladePassNotchAttenuation();
	}

	double gyroBladePassNotchSpreadHertz() {
		return physics.state().gyroBladePassNotchSpreadHertz();
	}

	BatteryTransientState batteryTransientStateSnapshot() {
		DroneState state = physics.state();
		return new BatteryTransientState(
				state.batterySlowPolarizationVoltage(),
				state.batteryTemperatureCelsius(),
				state.batteryCoolingFactor(),
				state.batteryThermalLimit()
		);
	}

	PersistenceState persistenceStateSnapshot() {
		DroneState state = physics.state();
		int motorCount = state.motorCount();
		double[] motorTemperaturesCelsius = new double[motorCount];
		double[] escTemperaturesCelsius = new double[motorCount];
		double[] motorCoolingFactors = new double[motorCount];
		double[] escCoolingFactors = new double[motorCount];
		for (int i = 0; i < motorCount; i++) {
			motorTemperaturesCelsius[i] = state.motorTemperatureCelsius(i);
			escTemperaturesCelsius[i] = state.escTemperatureCelsius(i);
			motorCoolingFactors[i] = state.motorCoolingFactor(i);
			escCoolingFactors[i] = state.escCoolingFactor(i);
		}
		return new PersistenceState(
				state.batteryAmpSecondsConsumed(),
				state.batteryEquivalentCycles(),
				state.batterySlowPolarizationVoltage(),
				state.batteryTemperatureCelsius(),
				state.batteryCoolingFactor(),
				state.batteryThermalLimit(),
				motorTemperaturesCelsius,
				escTemperaturesCelsius,
				motorCoolingFactors,
				escCoolingFactors,
				state.rotorHealth()
		);
	}

	SyncedFlightTelemetry syncedTelemetry(DroneEnvironment environment) {
		DroneState state = physics.state();
		DroneConfig config = physics.config();
		Vec3 targetRates = state.targetRatesBodyRadiansPerSecond();
		Vec3 gyroRates = state.gyroAngularVelocityBodyRadiansPerSecond();
		Vec3 estimatedEuler = state.estimatedOrientation().toEulerXYZRadians();
		Vec3 effectiveWind = state.effectiveWindVelocityWorldMetersPerSecond();
		return new SyncedFlightTelemetry(
				state.processedControlInput(),
				state.orientation().toEulerXYZRadians(),
				new Vec3(Math.toDegrees(targetRates.x()), Math.toDegrees(targetRates.y()), Math.toDegrees(targetRates.z())),
				new Vec3(Math.toDegrees(gyroRates.x()), Math.toDegrees(gyroRates.y()), Math.toDegrees(gyroRates.z())),
				state.pidOutputTorqueBodyNewtonMeters(),
				new Vec3(Math.toDegrees(estimatedEuler.x()), Math.toDegrees(estimatedEuler.y()), Math.toDegrees(estimatedEuler.z())),
				Math.toDegrees(state.attitudeEstimateErrorRadians().length()),
				state.motorPower(config),
				state.motorRpm(),
				state.rotorThrustNewtons(),
				state.rotorHealth(),
				new ControlTelemetry(
						(float) state.controlLinkLossSeconds(),
						state.controlFailsafeActive(),
						(float) state.pidAttenuation(),
						(float) state.pidIntegralRelax(),
						(float) state.pidDTermLowPassCutoffHertz(),
						(float) state.antiGravityBoost(),
						(float) state.attitudeEstimatorAccelerometerTrust()
				),
				new ImuTelemetry(
						(float) state.gyroClipIntensity(),
						(float) state.accelerometerClipIntensity(),
						(float) state.imuSupplyNoiseIntensity(),
						(float) state.gyroDynamicNotchFrequencyHertz(),
						(float) state.gyroDynamicNotchAttenuation()
				),
				new MotorTelemetry(
						(float) state.averageMotorPower(config),
						(float) state.averageMotorRpm(),
						(float) state.maxMotorTemperatureCelsius(),
						(float) state.motorThermalLimit(),
						(float) state.minMotorVoltageHeadroom(),
						(float) state.maxMotorWindingResistanceScale(),
						(float) state.maxEscTemperatureCelsius(),
						(float) state.escThermalLimit(),
						(float) state.averageEscCoolingFactor(),
						(float) state.maxEscDesyncIntensity(),
						(float) state.averageRotorAerodynamicLoadFactor(),
						(float) state.maxRotorInPlaneDragForceNewtons()
				),
				new RotorTelemetry(
						(float) state.rotorVibration(),
						(float) state.averageRotorConingIntensity(),
						(float) state.averageRotorStallIntensity(),
						(float) Math.toDegrees(state.averageRotorFlappingTiltRadians()),
						(float) state.maxRotorSurfaceScrapeIntensity(),
						(float) state.averageRotorTranslationalLiftIntensity(),
						(float) state.maxRotorAdvanceRatio(),
						(float) state.maxRotorPropellerAdvanceRatioJ(),
						(float) state.minRotorPropellerThrustScale(),
						(float) state.minRotorPropellerPowerScale(),
						(float) state.maxRotorReverseFlowInboardFraction(),
						(float) state.maxRotorTipMach(),
						(float) state.minRotorCompressibilityThrustScale(),
						(float) state.maxRotorLowReynoldsLoss(),
						(float) Math.toDegrees(state.maxAbsRotorBladeAngleOfAttackRadians()),
						(float) state.maxRotorBladeElementStallIntensity(),
						(float) state.maxRotorBladePassRippleIntensity(),
						(float) state.rotorBladeDissymmetryTorqueBodyNewtonMeters().length(),
						(float) state.rotorInflowSkewIntensity(),
						(float) state.maxRotorInducedVelocityMetersPerSecond(),
						(float) state.minRotorInducedLagThrustScale(),
						(float) state.maxRotorWakeInterferenceIntensity(),
						(float) state.minRotorWakeThrustScale(),
						(float) state.maxAbsRotorCoaxialLoadBias(),
						(float) state.minRotorWetThrustScale(),
						(float) state.maxRotorWakeSwirlVelocityMetersPerSecond(),
						(float) state.maxRotorWindmillingIntensity(),
						(float) state.rotorWakeSwirlTorqueBodyNewtonMeters().length(),
						(float) state.rotorActiveBrakingTorqueBodyNewtonMeters().length(),
						(float) state.rotorAccelerationReactionTorqueBodyNewtonMeters().length(),
						(float) state.rotorGyroscopicTorqueBodyNewtonMeters().length(),
						(float) state.rotorFlappingTorqueBodyNewtonMeters().length(),
						(float) state.mixerSaturation(),
						(float) state.propwashIntensity(),
						(float) state.vortexRingStateIntensity(),
						(float) state.maxVortexRingThrustBuffetAmplitude(),
						(float) state.vortexRingBuffetForceBodyNewtons().length()
				),
				new EnvironmentTelemetry(
						(float) environment.groundEffectThrustMultiplier(config),
						(float) environment.ceilingEffectThrustMultiplier(config),
						(float) environment.ceilingEffectIntensity(config),
						(float) environment.rotorThrustAsymmetry(config),
						(float) environment.maxRotorFlowObstruction(),
						(float) environment.maxRotorWaterImmersion(),
						(float) environment.precipitationWetnessIntensity(),
						(float) environment.ambientTemperatureCelsius(),
						(float) environment.windVelocityWorldMetersPerSecond().length(),
						(float) effectiveWind.length(),
						(float) state.windGustSpeedMetersPerSecond(),
						(float) state.windShearAccelerationMetersPerSecondSquared(),
						(float) environment.turbulenceIntensity(),
						(float) environment.obstacleProximity(),
						(float) environment.droneWakeIntensity()
				),
				new AirframeTelemetry(
						(float) state.airspeedMetersPerSecond(),
						(float) Math.toDegrees(state.angleOfAttackRadians()),
						(float) Math.toDegrees(state.sideslipRadians()),
						(float) state.airframeSeparatedFlowIntensity(),
						(float) state.airframeLiftForceBodyNewtons().length(),
						(float) state.groundEffectDragForceBodyNewtons().length(),
						(float) state.groundEffectLevelingTorqueBodyNewtonMeters().length(),
						(float) state.rotorWashDragForceBodyNewtons().length(),
						(float) state.rotorWallEffectForceBodyNewtons().length(),
						(float) state.barometerAltitudeMeters(),
						(float) state.barometerVerticalSpeedMetersPerSecond(),
						(float) state.barometerPressureHectopascals(),
						(float) state.barometerErrorMeters(),
						(float) state.speedMetersPerSecond()
				),
				new ContactTelemetry(
						(float) state.contactImpactSpeedMetersPerSecond(),
						(float) state.contactSlipSpeedMetersPerSecond(),
						(float) state.contactBounceSpeedMetersPerSecond(),
						(float) Math.toDegrees(state.contactAngularImpulseBodyRadiansPerSecond().length())
				),
				new BatteryTelemetry(
						(float) state.batteryVoltage(),
						(float) state.batteryTotalSagVoltage(),
						(float) state.batteryEffectiveResistanceOhms(),
						(float) state.batteryRegenerativeCurrentAmps(),
						(float) state.batteryVoltageSpike(),
						(float) state.batteryBusRippleVoltage(),
						(float) state.batteryStateOfCharge(),
						(float) state.batteryCurrentAmps(),
						(float) state.batteryTwentyPercentSagCurrentAmps(),
						(float) state.batteryTwentyPercentSagCurrentMargin(),
						(float) state.batteryCurrentLimit(),
						(float) state.batteryPowerLimit()
				),
				(float) state.averageRotorHealth()
		);
	}

	private double averageMotorPolePairs() {
		DroneConfig config = physics.config();
		if (config.rotors().isEmpty()) {
			return RotorSpec.DEFAULT_MOTOR_POLE_PAIRS;
		}
		return config.rotors().stream()
				.mapToDouble(RotorSpec::motorPolePairs)
				.average()
				.orElse(RotorSpec.DEFAULT_MOTOR_POLE_PAIRS);
	}

	private double motorPolePairs(int index) {
		DroneConfig config = physics.config();
		if (index < 0 || index >= config.rotors().size()) {
			return RotorSpec.DEFAULT_MOTOR_POLE_PAIRS;
		}
		return config.rotors().get(index).motorPolePairs();
	}

	FlightModel flightModel() {
		return flightModel;
	}

	void applyConfig(DroneConfig config) {
		physics.applyConfig(config);
	}

	void replaceConfigPreservingKinematics(DroneConfig config) {
		DroneState previousState = physics.state();
		DronePhysics replacement = new DronePhysics(config);
		replacement.state().setPositionMeters(previousState.positionMeters());
		replacement.state().setVelocityMetersPerSecond(previousState.velocityMetersPerSecond());
		replacement.state().setOrientation(previousState.orientation());
		replacement.state().setEstimatedOrientation(previousState.estimatedOrientation());
		replacement.state().setAngularVelocityBodyRadiansPerSecond(previousState.angularVelocityBodyRadiansPerSecond());
		physics = replacement;
		flightModel = new SimulationFlightModelAdapter(physics);
	}

	void resetControlLoops() {
		physics.resetControlLoops();
	}

	void clearDirectFlightTelemetry(DroneInput input) {
		physics.clearDirectFlightTelemetry(input);
	}

	DirectPerRotorTelemetry restoreDirectPerRotorTelemetry(
			DroneInput input,
			ActuatorOutput actuatorOutput,
			double debugMotorPower,
			double debugAverageMotorRpm
	) {
		int rotorCount = Math.max(1, rotorCount());
		double[] motorPower = new double[rotorCount];
		double[] motorRpm = new double[rotorCount];
		double[] rotorThrust = new double[rotorCount];
		double[] rotorHealth = physics.state().rotorHealth();
		double[] modelMotorPower = actuatorOutput.motorPower();
		double[] modelMotorRpm = actuatorOutput.motorRpm();
		double[] modelRotorThrust = actuatorOutput.rotorThrustNewtons();
		double basePower = input.armed() ? debugMotorPower : 0.0;
		double baseRpm = input.armed() ? debugAverageMotorRpm : 0.0;
		for (int i = 0; i < rotorCount; i++) {
			double mix = 1.0 + rotorMixerPreview(i, input);
			motorPower[i] = input.armed() && i < modelMotorPower.length
					? MathUtil.clamp(modelMotorPower[i], 0.0, 1.0)
					: MathUtil.clamp(basePower * mix, 0.0, 1.0);
			motorRpm[i] = input.armed() && i < modelMotorRpm.length
					? Math.max(0.0, modelMotorRpm[i])
					: Math.max(0.0, baseRpm * mix);
			rotorThrust[i] = input.armed() && i < modelRotorThrust.length
					? Math.max(0.0, modelRotorThrust[i])
					: motorPower[i] * physics.config().rotors().get(i).maxThrustNewtons() * 0.45;
		}
		restoreDirectFlightTelemetry(input, motorPower, motorRpm, rotorThrust);
		return new DirectPerRotorTelemetry(motorPower, motorRpm, rotorThrust, rotorHealth);
	}

	void restoreDirectFlightTelemetry(DroneInput input, double[] motorPower, double[] motorRpm, double[] rotorThrust) {
		physics.restoreDirectFlightTelemetry(input, motorPower, motorRpm, rotorThrust);
	}

	private double rotorMixerPreview(int rotorIndex, DroneInput input) {
		double rollSign = switch (rotorIndex & 3) {
			case 0, 3 -> 1.0;
			default -> -1.0;
		};
		double pitchSign = switch (rotorIndex & 3) {
			case 0, 1 -> 1.0;
			default -> -1.0;
		};
		double yawSign = physics.config().rotors().get(rotorIndex).spinDirection();
		return 0.04 * input.roll() * rollSign
				+ 0.04 * input.pitch() * pitchSign
				+ 0.025 * input.yaw() * yawSign;
	}

	void sleepAtRest(Vec3 positionMeters, DroneInput input) {
		physics.sleepAtRest(positionMeters, input);
	}

	void levelAtRest(Vec3 positionMeters) {
		physics.levelAtRest(positionMeters);
	}

	void restoreBatteryTransientState(double slowPolarizationVoltage, double temperatureCelsius, double coolingFactor, double thermalLimit) {
		physics.restoreBatteryTransientState(slowPolarizationVoltage, temperatureCelsius, coolingFactor, thermalLimit);
	}

	RotorDynamicState rotorDynamicStateSnapshot() {
		return RotorDynamicState.from(physics.rotorDynamicStateSnapshot());
	}

	void restoreRotorDynamicState(RotorDynamicState state) {
		physics.restoreRotorDynamicState(state.toDronePhysicsState());
	}

	void restorePowertrainThermalState(
			double[] motorTemperaturesCelsius,
			double[] escTemperaturesCelsius,
			double[] motorCoolingFactors,
			double[] escCoolingFactors
	) {
		physics.restorePowertrainThermalState(
				motorTemperaturesCelsius,
				escTemperaturesCelsius,
				motorCoolingFactors,
				escCoolingFactors
		);
	}

	AerodynamicTransientState aerodynamicTransientStateSnapshot() {
		return AerodynamicTransientState.from(physics.aerodynamicTransientStateSnapshot());
	}

	void restoreAerodynamicTransientState(AerodynamicTransientState state) {
		physics.restoreAerodynamicTransientState(state.toDronePhysicsState());
	}

	static double betaflightErpm100FromMechanicalRpm(double mechanicalRpm, double motorPolePairs) {
		return DronePhysics.betaflightErpm100FromMechanicalRpm(mechanicalRpm, motorPolePairs);
	}

	static double betaflightEIntervalMicrosFromTelemetryRpm(
			double mechanicalRpm,
			double telemetryValidity,
			double motorPolePairs
	) {
		return DronePhysics.betaflightEIntervalMicrosFromTelemetryRpm(mechanicalRpm, telemetryValidity, motorPolePairs);
	}

	record BatteryTransientState(
			double slowPolarizationVoltage,
			double temperatureCelsius,
			double coolingFactor,
			double thermalLimit
	) {}

	record RotorHealthState(double averageRotorHealth, double[] rotorHealth) {
		RotorHealthState {
			rotorHealth = copyOrNull(rotorHealth);
		}

		@Override
		public double[] rotorHealth() {
			return copyOrNull(rotorHealth);
		}
	}

	record DirectPerRotorTelemetry(
			double[] motorPower,
			double[] motorRpm,
			double[] rotorThrust,
			double[] rotorHealth
	) {
		DirectPerRotorTelemetry {
			motorPower = copyOrNull(motorPower);
			motorRpm = copyOrNull(motorRpm);
			rotorThrust = copyOrNull(rotorThrust);
			rotorHealth = copyOrNull(rotorHealth);
		}

		@Override
		public double[] motorPower() {
			return copyOrNull(motorPower);
		}

		@Override
		public double[] motorRpm() {
			return copyOrNull(motorRpm);
		}

		@Override
		public double[] rotorThrust() {
			return copyOrNull(rotorThrust);
		}

		@Override
		public double[] rotorHealth() {
			return copyOrNull(rotorHealth);
		}
	}

	record DroneWakeSource(
			double averageMotorPower,
			double averageRotorInducedVelocityMetersPerSecond,
			Vec3 velocityMetersPerSecond,
			double wakeRadiusMeters
	) {}

	record PersistenceState(
			double batteryAmpSecondsConsumed,
			double batteryEquivalentCycles,
			double batterySlowPolarizationVoltage,
			double batteryTemperatureCelsius,
			double batteryCoolingFactor,
			double batteryThermalLimit,
			double[] motorTemperaturesCelsius,
			double[] escTemperaturesCelsius,
			double[] motorCoolingFactors,
			double[] escCoolingFactors,
			double[] rotorHealth
	) {
		PersistenceState {
			motorTemperaturesCelsius = copyOrNull(motorTemperaturesCelsius);
			escTemperaturesCelsius = copyOrNull(escTemperaturesCelsius);
			motorCoolingFactors = copyOrNull(motorCoolingFactors);
			escCoolingFactors = copyOrNull(escCoolingFactors);
			rotorHealth = copyOrNull(rotorHealth);
		}

		@Override
		public double[] motorTemperaturesCelsius() {
			return copyOrNull(motorTemperaturesCelsius);
		}

		@Override
		public double[] escTemperaturesCelsius() {
			return copyOrNull(escTemperaturesCelsius);
		}

		@Override
		public double[] motorCoolingFactors() {
			return copyOrNull(motorCoolingFactors);
		}

		@Override
		public double[] escCoolingFactors() {
			return copyOrNull(escCoolingFactors);
		}

		@Override
		public double[] rotorHealth() {
			return copyOrNull(rotorHealth);
		}
	}

	record SyncedFlightTelemetry(
			DroneInput processedInput,
			Vec3 eulerRadians,
			Vec3 targetRatesDegreesPerSecond,
			Vec3 gyroRatesDegreesPerSecond,
			Vec3 pidOutputTorqueNewtonMeters,
			Vec3 estimatedEulerDegrees,
			double attitudeEstimateErrorDegrees,
			double[] motorPower,
			double[] motorRpm,
			double[] rotorThrustNewtons,
			double[] rotorHealth,
			ControlTelemetry control,
			ImuTelemetry imu,
			MotorTelemetry motor,
			RotorTelemetry rotor,
			EnvironmentTelemetry environment,
			AirframeTelemetry airframe,
			ContactTelemetry contact,
			BatteryTelemetry battery,
			float averageRotorHealth
	) {
		SyncedFlightTelemetry {
			motorPower = copyOrNull(motorPower);
			motorRpm = copyOrNull(motorRpm);
			rotorThrustNewtons = copyOrNull(rotorThrustNewtons);
			rotorHealth = copyOrNull(rotorHealth);
		}

		@Override
		public double[] motorPower() {
			return copyOrNull(motorPower);
		}

		@Override
		public double[] motorRpm() {
			return copyOrNull(motorRpm);
		}

		@Override
		public double[] rotorThrustNewtons() {
			return copyOrNull(rotorThrustNewtons);
		}

		@Override
		public double[] rotorHealth() {
			return copyOrNull(rotorHealth);
		}
	}

	record ControlTelemetry(
			float controlLinkLossSeconds,
			boolean controlFailsafe,
			float pidAttenuation,
			float pidIntegralRelax,
			float pidDTermLowPassCutoffHertz,
			float antiGravityBoost,
			float attitudeAccelerometerTrust
	) {}

	record ImuTelemetry(
			float gyroClipIntensity,
			float accelerometerClipIntensity,
			float imuSupplyNoiseIntensity,
			float gyroNotchFrequencyHertz,
			float gyroNotchAttenuation
	) {}

	record MotorTelemetry(
			float averageMotorPower,
			float averageMotorRpm,
			float maxMotorTemperatureCelsius,
			float motorThermalLimit,
			float minMotorVoltageHeadroom,
			float maxMotorWindingResistanceScale,
			float maxEscTemperatureCelsius,
			float escThermalLimit,
			float averageEscCoolingFactor,
			float maxEscDesyncIntensity,
			float averageRotorAerodynamicLoadFactor,
			float maxRotorInPlaneDragForceNewtons
	) {}

	record RotorTelemetry(
			float rotorVibration,
			float averageRotorConingIntensity,
			float averageRotorStallIntensity,
			float averageRotorFlappingTiltDegrees,
			float maxRotorSurfaceScrapeIntensity,
			float averageRotorTranslationalLiftIntensity,
			float maxRotorAdvanceRatio,
			float maxRotorPropellerAdvanceRatioJ,
			float minRotorPropellerThrustScale,
			float minRotorPropellerPowerScale,
			float maxRotorReverseFlowInboardFraction,
			float maxRotorTipMach,
			float minRotorCompressibilityThrustScale,
			float maxRotorLowReynoldsLoss,
			float maxAbsRotorBladeAngleOfAttackDegrees,
			float maxRotorBladeElementStallIntensity,
			float maxRotorBladePassRippleIntensity,
			float rotorBladeDissymmetryTorqueNewtonMeters,
			float rotorInflowSkewIntensity,
			float maxRotorInducedVelocityMetersPerSecond,
			float minRotorInducedLagThrustScale,
			float maxRotorWakeInterferenceIntensity,
			float minRotorWakeThrustScale,
			float maxAbsRotorCoaxialLoadBias,
			float minRotorWetThrustScale,
			float maxRotorWakeSwirlVelocityMetersPerSecond,
			float maxRotorWindmillingIntensity,
			float rotorWakeSwirlTorqueNewtonMeters,
			float rotorActiveBrakingTorqueNewtonMeters,
			float rotorAccelerationReactionTorqueNewtonMeters,
			float rotorGyroscopicTorqueNewtonMeters,
			float rotorFlappingTorqueNewtonMeters,
			float mixerSaturation,
			float propwashIntensity,
			float vortexRingStateIntensity,
			float maxVortexRingThrustBuffetAmplitude,
			float vortexRingBuffetForceNewtons
	) {}

	record EnvironmentTelemetry(
			float groundEffectMultiplier,
			float ceilingEffectMultiplier,
			float ceilingEffectIntensity,
			float rotorThrustAsymmetry,
			float rotorFlowObstruction,
			float waterImmersionIntensity,
			float precipitationWetnessIntensity,
			float ambientTemperatureCelsius,
			float windSpeedMetersPerSecond,
			float effectiveWindSpeedMetersPerSecond,
			float windGustSpeedMetersPerSecond,
			float windShearAccelerationMetersPerSecondSquared,
			float turbulenceIntensity,
			float obstacleProximity,
			float droneWakeIntensity
	) {}

	record AirframeTelemetry(
			float airspeedMetersPerSecond,
			float angleOfAttackDegrees,
			float sideslipDegrees,
			float airframeSeparatedFlowIntensity,
			float airframeLiftForceNewtons,
			float groundEffectDragForceNewtons,
			float groundEffectLevelingTorqueNewtonMeters,
			float rotorWashDragForceNewtons,
			float rotorWallEffectForceNewtons,
			float barometerAltitudeMeters,
			float barometerVerticalSpeedMetersPerSecond,
			float barometerPressureHectopascals,
			float barometerErrorMeters,
			float speedMetersPerSecond
	) {}

	record ContactTelemetry(
			float contactImpactSpeedMetersPerSecond,
			float contactSlipSpeedMetersPerSecond,
			float contactBounceSpeedMetersPerSecond,
			float contactAngularImpulseDegreesPerSecond
	) {}

	record BatteryTelemetry(
			float batteryVoltage,
			float batterySagVoltage,
			float batteryEffectiveResistanceOhms,
			float batteryRegenerativeCurrentAmps,
			float batteryVoltageSpike,
			float batteryBusRippleVoltage,
			float batteryStateOfCharge,
			float batteryCurrentAmps,
			float batteryTwentyPercentSagCurrentAmps,
			float batteryTwentyPercentSagCurrentMargin,
			float batteryCurrentLimit,
			float batteryPowerLimit
	) {}

	record RotorDynamicState(
			double[] motorOmegaRadiansPerSecond,
			double[] escOutputCommand,
			double[] escElectricalOutputCommand,
			double[] motorRpmTelemetryRpm,
			double[] motorRpmTelemetryValidity,
			double[] rotorInducedVelocityMetersPerSecond,
			double[] rotorInducedLagThrustScale,
			double[] rotorInducedWakeVelocityMetersPerSecond,
			double[] rotorInducedWakeCarryoverIntensity,
			double[] rotorSurfaceWetness,
			double[] rotorIcingSeverity,
			double propwashWakeIntensity,
			double propwashIntensity,
			double vortexRingStateIntensity,
			double vortexRingThrustBuffetAmplitude,
			double vortexRingMaxThrustBuffetAmplitude
	) {
		RotorDynamicState {
			motorOmegaRadiansPerSecond = copyOrNull(motorOmegaRadiansPerSecond);
			escOutputCommand = copyOrNull(escOutputCommand);
			escElectricalOutputCommand = copyOrNull(escElectricalOutputCommand);
			motorRpmTelemetryRpm = copyOrNull(motorRpmTelemetryRpm);
			motorRpmTelemetryValidity = copyOrNull(motorRpmTelemetryValidity);
			rotorInducedVelocityMetersPerSecond = copyOrNull(rotorInducedVelocityMetersPerSecond);
			rotorInducedLagThrustScale = copyOrNull(rotorInducedLagThrustScale);
			rotorInducedWakeVelocityMetersPerSecond = copyOrNull(rotorInducedWakeVelocityMetersPerSecond);
			rotorInducedWakeCarryoverIntensity = copyOrNull(rotorInducedWakeCarryoverIntensity);
			rotorSurfaceWetness = copyOrNull(rotorSurfaceWetness);
			rotorIcingSeverity = copyOrNull(rotorIcingSeverity);
		}

		static RotorDynamicState from(DronePhysics.RotorDynamicState state) {
			return new RotorDynamicState(
					state.motorOmegaRadiansPerSecond(),
					state.escOutputCommand(),
					state.escElectricalOutputCommand(),
					state.motorRpmTelemetryRpm(),
					state.motorRpmTelemetryValidity(),
					state.rotorInducedVelocityMetersPerSecond(),
					state.rotorInducedLagThrustScale(),
					state.rotorInducedWakeVelocityMetersPerSecond(),
					state.rotorInducedWakeCarryoverIntensity(),
					state.rotorSurfaceWetness(),
					state.rotorIcingSeverity(),
					state.propwashWakeIntensity(),
					state.propwashIntensity(),
					state.vortexRingStateIntensity(),
					state.vortexRingThrustBuffetAmplitude(),
					state.vortexRingMaxThrustBuffetAmplitude()
			);
		}

		DronePhysics.RotorDynamicState toDronePhysicsState() {
			return new DronePhysics.RotorDynamicState(
					motorOmegaRadiansPerSecond,
					escOutputCommand,
					escElectricalOutputCommand,
					motorRpmTelemetryRpm,
					motorRpmTelemetryValidity,
					rotorInducedVelocityMetersPerSecond,
					rotorInducedLagThrustScale,
					rotorInducedWakeVelocityMetersPerSecond,
					rotorInducedWakeCarryoverIntensity,
					rotorSurfaceWetness,
					rotorIcingSeverity,
					propwashWakeIntensity,
					propwashIntensity,
					vortexRingStateIntensity,
					vortexRingThrustBuffetAmplitude,
					vortexRingMaxThrustBuffetAmplitude
			);
		}

		@Override
		public double[] motorOmegaRadiansPerSecond() {
			return copyOrNull(motorOmegaRadiansPerSecond);
		}

		@Override
		public double[] escOutputCommand() {
			return copyOrNull(escOutputCommand);
		}

		@Override
		public double[] escElectricalOutputCommand() {
			return copyOrNull(escElectricalOutputCommand);
		}

		@Override
		public double[] motorRpmTelemetryRpm() {
			return copyOrNull(motorRpmTelemetryRpm);
		}

		@Override
		public double[] motorRpmTelemetryValidity() {
			return copyOrNull(motorRpmTelemetryValidity);
		}

		@Override
		public double[] rotorInducedVelocityMetersPerSecond() {
			return copyOrNull(rotorInducedVelocityMetersPerSecond);
		}

		@Override
		public double[] rotorInducedLagThrustScale() {
			return copyOrNull(rotorInducedLagThrustScale);
		}

		@Override
		public double[] rotorInducedWakeVelocityMetersPerSecond() {
			return copyOrNull(rotorInducedWakeVelocityMetersPerSecond);
		}

		@Override
		public double[] rotorInducedWakeCarryoverIntensity() {
			return copyOrNull(rotorInducedWakeCarryoverIntensity);
		}

		@Override
		public double[] rotorSurfaceWetness() {
			return copyOrNull(rotorSurfaceWetness);
		}

		@Override
		public double[] rotorIcingSeverity() {
			return copyOrNull(rotorIcingSeverity);
		}
	}

	record AerodynamicTransientState(
			Vec3 meanWindVelocityWorldMetersPerSecond,
			Vec3 windBurbleVelocityWorldMetersPerSecond,
			Vec3 drydenFirstOrderVelocityWorldMetersPerSecond,
			Vec3 drydenTransverseLagVelocityWorldMetersPerSecond,
			Vec3 drydenTurbulenceVelocityWorldMetersPerSecond,
			Vec3 windGustVelocityWorldMetersPerSecond,
			long drydenRandomState,
			double drydenSpareGaussian,
			boolean hasDrydenSpareGaussian,
			boolean windModelInitialized,
			double windGustPhaseA,
			double windGustPhaseB,
			double windGustPhaseC,
			double turbulencePhaseA,
			double turbulencePhaseB,
			double turbulencePhaseC,
			double airframeSeparatedFlowIntensity,
			double airframeSeparationBuffetPhaseA,
			double airframeSeparationBuffetPhaseB,
			Vec3 rotorWashDragForceBody,
			Vec3 rotorWashAirframeAngularDamping,
			Vec3 dynamicPressureCenterOffsetBody,
			Vec3 airframeLiftForceBody,
			Vec3 airframeDragForceBody,
			Vec3 groundEffectDragForceBody,
			Vec3 groundEffectLevelingTorqueBody
	) {
		static AerodynamicTransientState from(DronePhysics.AerodynamicTransientState state) {
			return new AerodynamicTransientState(
					state.meanWindVelocityWorldMetersPerSecond(),
					state.windBurbleVelocityWorldMetersPerSecond(),
					state.drydenFirstOrderVelocityWorldMetersPerSecond(),
					state.drydenTransverseLagVelocityWorldMetersPerSecond(),
					state.drydenTurbulenceVelocityWorldMetersPerSecond(),
					state.windGustVelocityWorldMetersPerSecond(),
					state.drydenRandomState(),
					state.drydenSpareGaussian(),
					state.hasDrydenSpareGaussian(),
					state.windModelInitialized(),
					state.windGustPhaseA(),
					state.windGustPhaseB(),
					state.windGustPhaseC(),
					state.turbulencePhaseA(),
					state.turbulencePhaseB(),
					state.turbulencePhaseC(),
					state.airframeSeparatedFlowIntensity(),
					state.airframeSeparationBuffetPhaseA(),
					state.airframeSeparationBuffetPhaseB(),
					state.rotorWashDragForceBody(),
					state.rotorWashAirframeAngularDamping(),
					state.dynamicPressureCenterOffsetBody(),
					state.airframeLiftForceBody(),
					state.airframeDragForceBody(),
					state.groundEffectDragForceBody(),
					state.groundEffectLevelingTorqueBody()
			);
		}

		DronePhysics.AerodynamicTransientState toDronePhysicsState() {
			return new DronePhysics.AerodynamicTransientState(
					meanWindVelocityWorldMetersPerSecond,
					windBurbleVelocityWorldMetersPerSecond,
					drydenFirstOrderVelocityWorldMetersPerSecond,
					drydenTransverseLagVelocityWorldMetersPerSecond,
					drydenTurbulenceVelocityWorldMetersPerSecond,
					windGustVelocityWorldMetersPerSecond,
					drydenRandomState,
					drydenSpareGaussian,
					hasDrydenSpareGaussian,
					windModelInitialized,
					windGustPhaseA,
					windGustPhaseB,
					windGustPhaseC,
					turbulencePhaseA,
					turbulencePhaseB,
					turbulencePhaseC,
					airframeSeparatedFlowIntensity,
					airframeSeparationBuffetPhaseA,
					airframeSeparationBuffetPhaseB,
					rotorWashDragForceBody,
					rotorWashAirframeAngularDamping,
					dynamicPressureCenterOffsetBody,
					airframeLiftForceBody,
					airframeDragForceBody,
					groundEffectDragForceBody,
					groundEffectLevelingTorqueBody
			);
		}
	}

	private static double[] copyOrNull(double[] values) {
		return values == null ? null : Arrays.copyOf(values, values.length);
	}
}
