package com.tenicana.dronecraft.sim.flight;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.DronePhysics;
import com.tenicana.dronecraft.sim.DroneState;
import com.tenicana.dronecraft.sim.Vec3;

public final class SimulationFlightModelAdapter implements FlightModel {
	public static final String ID = "simulation_drone_physics";

	private DroneConfig config = DroneConfig.racingQuad();
	private DronePhysics physics = new DronePhysics(config);
	private FlightModelDiagnostics diagnostics = FlightModelDiagnostics.empty();
	private DroneEnvironment deferredDiagnosticsEnvironment = DroneEnvironment.calm();
	private boolean deferredDiagnosticsConfigChanged;
	private boolean diagnosticsDirty;

	public SimulationFlightModelAdapter() {
	}

	public SimulationFlightModelAdapter(DronePhysics physics) {
		this.physics = Objects.requireNonNull(physics, "physics");
		this.config = physics.config();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public FlightModelCapabilities capabilities() {
		return FlightModelCapabilities.simulation();
	}

	@Override
	public void initialize(FlightModelInitializationContext context) {
		config = context.config();
		physics = new DronePhysics(config);
		applySnapshot(context.initialState());
		diagnostics = diagnosticsFor(List.of(), context.environment());
		diagnosticsDirty = false;
	}

	@Override
	public void reset(FlightStateSnapshot state) {
		FlightStateSnapshot safeState = state == null ? FlightStateSnapshot.zero() : state;
		physics.sleepAtRest(
				safeState.positionWorldMeters(),
				new DroneInput(0.0, 0.0, 0.0, 0.0, safeState.armed(), true, safeState.flightMode())
		);
		applySnapshot(safeState);
		diagnostics = diagnosticsFor(List.of(new StateCorrection(StateCorrectionReason.RESET_TELEPORT, "RESET", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO)), DroneEnvironment.calm());
		diagnosticsDirty = false;
	}

	@Override
	public void applyResolvedState(FlightStateSnapshot state, StateCorrection correction) {
		applySnapshot(
				state == null ? FlightStateSnapshot.zero() : state,
				shouldSynchronizeEstimator(correction)
		);
		List<StateCorrection> corrections = correction == null ? List.of() : List.of(correction);
		diagnostics = diagnosticsFor(corrections, DroneEnvironment.calm());
		diagnosticsDirty = false;
	}

	@Override
	public FlightStepResult step(FlightStepContext context) {
		boolean configChanged = synchronizeConfig(context.config());
		List<StateCorrection> corrections = configChanged
				? List.of(configAppliedCorrection())
				: List.of();
		physics.step(context.input(), context.dtSeconds(), context.environment());
		diagnostics = diagnosticsFor(corrections, context.environment());
		diagnosticsDirty = false;
		return new FlightStepResult(
				snapshot(),
				actuatorOutput(),
				forceTorqueDiagnostics(),
				corrections,
				diagnostics
		);
	}

	@Override
	public void stepStateOnly(
			DroneInput input,
			DroneEnvironment environment,
			double dtSeconds,
			long tick,
			DroneConfig requestedConfig,
			Map<String, String> modelConfiguration
	) {
		if (!Double.isFinite(dtSeconds) || dtSeconds <= 0.0) {
			throw new IllegalArgumentException("dtSeconds must be finite and positive");
		}
		DroneEnvironment safeEnvironment = environment == null ? DroneEnvironment.calm() : environment;
		boolean configChanged = synchronizeConfig(
				Objects.requireNonNull(requestedConfig, "config")
		);
		deferredDiagnosticsConfigChanged = diagnosticsDirty
				? deferredDiagnosticsConfigChanged || configChanged
				: configChanged;
		physics.step(input, dtSeconds, safeEnvironment);
		deferredDiagnosticsEnvironment = safeEnvironment;
		diagnosticsDirty = true;
	}

	@Override
	public FlightStateSnapshot snapshot() {
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

	@Override
	public FlightModelDiagnostics diagnostics() {
		if (diagnosticsDirty) {
			List<StateCorrection> corrections = deferredDiagnosticsConfigChanged
					? List.of(configAppliedCorrection())
					: List.of();
			diagnostics = diagnosticsFor(corrections, deferredDiagnosticsEnvironment);
			diagnosticsDirty = false;
			deferredDiagnosticsConfigChanged = false;
		}
		return diagnostics;
	}

	public DronePhysics physics() {
		return physics;
	}

	private boolean synchronizeConfig(DroneConfig requestedConfig) {
		DroneConfig physicsConfig = physics.config();
		if (requestedConfig == config && requestedConfig == physicsConfig) {
			return false;
		}
		boolean adapterChanged = !requestedConfig.equals(config);
		boolean physicsChanged = !requestedConfig.equals(physicsConfig);
		if (physicsChanged) {
			physics.applyConfig(requestedConfig);
		}
		config = requestedConfig;
		return adapterChanged || physicsChanged;
	}

	private static StateCorrection configAppliedCorrection() {
		return new StateCorrection(
				StateCorrectionReason.MODEL_INITIALIZATION,
				"CONFIG_APPLIED",
				Vec3.ZERO,
				Vec3.ZERO,
				Vec3.ZERO
		);
	}

	private void applySnapshot(FlightStateSnapshot snapshot) {
		applySnapshot(snapshot, true);
	}

	private void applySnapshot(FlightStateSnapshot snapshot, boolean synchronizeEstimator) {
		DroneState state = physics.state();
		state.setPositionMeters(snapshot.positionWorldMeters());
		state.setVelocityMetersPerSecond(snapshot.velocityWorldMetersPerSecond());
		state.setOrientation(snapshot.attitude());
		if (synchronizeEstimator) {
			state.setEstimatedOrientation(snapshot.attitude());
		}
		state.setAngularVelocityBodyRadiansPerSecond(snapshot.angularVelocityBodyRadiansPerSecond());
	}

	private static boolean shouldSynchronizeEstimator(StateCorrection correction) {
		if (correction == null) {
			return false;
		}
		return correction.reason() == StateCorrectionReason.RESET_TELEPORT
				|| correction.reason() == StateCorrectionReason.MODEL_INITIALIZATION
				|| correction.reason() == StateCorrectionReason.NETWORK_CORRECTION;
	}

	private ActuatorOutput actuatorOutput() {
		DroneState state = physics.state();
		return new ActuatorOutput(
				state.motorPower(config),
				state.motorRpm(),
				state.rotorThrustNewtons()
		);
	}

	private ForceTorqueDiagnostics forceTorqueDiagnostics() {
		DroneState state = physics.state();
		Vec3 forceBody = sum(state.rotorForceBodyNewtons())
				.add(state.airframeBodyDragForceBodyNewtons())
				.add(state.airframeLiftForceBodyNewtons())
				.add(state.groundEffectDragForceBodyNewtons())
				.add(state.rotorWashDragForceBodyNewtons())
				.add(state.rotorWallEffectForceBodyNewtons())
				.add(state.vortexRingBuffetForceBodyNewtons());
		Vec3 forceWorld = state.orientation().rotate(forceBody)
				.add(state.linearDampingDragForceWorldNewtons());
		Vec3 torqueBody = sum(state.rotorTorqueBodyNewtonMeters())
				.add(state.pidOutputTorqueBodyNewtonMeters())
				.add(state.rotorInflowSkewTorqueBodyNewtonMeters())
				.add(state.rotorBladeDissymmetryTorqueBodyNewtonMeters())
				.add(state.rotorWakeSwirlTorqueBodyNewtonMeters())
				.add(state.rotorFlappingTorqueBodyNewtonMeters())
				.add(state.rotorActiveBrakingTorqueBodyNewtonMeters())
				.add(state.rotorInertiaTorqueBodyNewtonMeters())
				.add(state.rotorAccelerationReactionTorqueBodyNewtonMeters())
				.add(state.rotorGyroscopicTorqueBodyNewtonMeters())
				.add(state.rotorAngularDragTorqueBodyNewtonMeters())
				.add(state.groundEffectLevelingTorqueBodyNewtonMeters())
				.add(state.propwashTorqueBodyNewtonMeters())
				.add(state.windTurbulenceTorqueBodyNewtonMeters())
				.add(state.airframeAerodynamicTorqueBodyNewtonMeters())
				.add(state.airframePressureCenterTorqueBodyNewtonMeters())
				.add(state.airframeAngularDragTorqueBodyNewtonMeters())
				.add(state.mixerOutputTorqueBodyNewtonMeters());
		return new ForceTorqueDiagnostics(
				forceWorld,
				forceBody,
				torqueBody,
				state.linearAccelerationWorldMetersPerSecondSquared()
		);
	}

	private FlightModelDiagnostics diagnosticsFor(List<StateCorrection> corrections, DroneEnvironment environment) {
		DroneState state = physics.state();
		Map<String, String> values = new LinkedHashMap<>();
		values.put("motor_count", Integer.toString(state.motorCount()));
		values.put("average_motor_rpm", Double.toString(state.averageMotorRpm()));
		values.put("average_motor_power", Double.toString(state.averageMotorPower(config)));
		values.put("average_rotor_thrust_n", Double.toString(average(state.rotorThrustNewtons())));
		values.put("environment_ground_clearance_m", Double.toString(environment.groundClearanceMeters()));
		values.put("environment_air_density_ratio", Double.toString(environment.airDensityRatio()));
		return new FlightModelDiagnostics(snapshot().isFinite(), values, corrections, List.of());
	}

	private static Vec3 sum(Vec3[] values) {
		Vec3 sum = Vec3.ZERO;
		if (values == null) {
			return sum;
		}
		for (Vec3 value : values) {
			if (value != null) {
				sum = sum.add(value);
			}
		}
		return sum;
	}

	private static double average(double[] values) {
		if (values == null || values.length == 0) {
			return 0.0;
		}
		double sum = 0.0;
		for (double value : values) {
			sum += value;
		}
		return sum / values.length;
	}
}
