package com.tenicana.dronecraft.entity;

import java.util.Arrays;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.DronePhysics;
import com.tenicana.dronecraft.sim.DroneState;
import com.tenicana.dronecraft.sim.Vec3;
import com.tenicana.dronecraft.sim.flight.FlightModel;
import com.tenicana.dronecraft.sim.flight.SimulationFlightModelAdapter;

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

	void restoreDirectFlightTelemetry(DroneInput input, double[] motorPower, double[] motorRpm, double[] rotorThrust) {
		physics.restoreDirectFlightTelemetry(input, motorPower, motorRpm, rotorThrust);
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
