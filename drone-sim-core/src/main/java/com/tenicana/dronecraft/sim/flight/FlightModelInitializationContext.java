package com.tenicana.dronecraft.sim.flight;

import java.util.Objects;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;

public record FlightModelInitializationContext(
		DroneConfig config,
		FlightStateSnapshot initialState,
		DroneEnvironment environment,
		long tick
) {
	public FlightModelInitializationContext {
		config = Objects.requireNonNull(config, "config");
		initialState = initialState == null ? FlightStateSnapshot.zero() : initialState;
		environment = environment == null ? DroneEnvironment.calm() : environment;
	}
}
