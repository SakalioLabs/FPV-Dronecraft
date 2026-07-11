package com.tenicana.dronecraft.sim.flight;

import java.util.Map;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;

public interface FlightModel {
	String id();

	FlightModelCapabilities capabilities();

	void initialize(FlightModelInitializationContext context);

	void reset(FlightStateSnapshot state);

	FlightStepResult step(FlightStepContext context);

	/**
	 * Advances canonical model state when the caller does not consume a rich step result.
	 * Implementations may override this to avoid materializing snapshots and diagnostics.
	 */
	default void stepStateOnly(
			DroneInput input,
			DroneEnvironment environment,
			double dtSeconds,
			long tick,
			DroneConfig config,
			Map<String, String> modelConfiguration
	) {
		step(new FlightStepContext(input, snapshot(), environment, dtSeconds, tick, config, modelConfiguration));
	}

	default void applyResolvedState(FlightStateSnapshot state, StateCorrection correction) {
		reset(state);
	}

	FlightStateSnapshot snapshot();

	FlightModelDiagnostics diagnostics();
}
