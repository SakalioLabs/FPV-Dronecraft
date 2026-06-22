package com.tenicana.dronecraft.sim.flight;

import java.util.Map;
import java.util.Objects;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;

public record FlightStepContext(
		DroneInput input,
		FlightStateSnapshot previousState,
		DroneEnvironment environment,
		double dtSeconds,
		long tick,
		DroneConfig config,
		Map<String, String> modelConfiguration
) {
	public FlightStepContext(
			DroneInput input,
			FlightStateSnapshot previousState,
			DroneEnvironment environment,
			double dtSeconds,
			long tick,
			DroneConfig config
	) {
		this(input, previousState, environment, dtSeconds, tick, config, Map.of());
	}

	public FlightStepContext {
		input = (input == null ? DroneInput.idle() : input).normalized();
		previousState = previousState == null ? FlightStateSnapshot.zero(input.flightMode()) : previousState;
		environment = environment == null ? DroneEnvironment.calm() : environment;
		if (!Double.isFinite(dtSeconds) || dtSeconds <= 0.0) {
			throw new IllegalArgumentException("dtSeconds must be finite and positive");
		}
		config = Objects.requireNonNull(config, "config");
		modelConfiguration = modelConfiguration == null ? Map.of() : Map.copyOf(modelConfiguration);
	}
}
