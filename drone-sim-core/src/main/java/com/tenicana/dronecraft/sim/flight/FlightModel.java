package com.tenicana.dronecraft.sim.flight;

public interface FlightModel {
	String id();

	FlightModelCapabilities capabilities();

	void initialize(FlightModelInitializationContext context);

	void reset(FlightStateSnapshot state);

	FlightStepResult step(FlightStepContext context);

	default void applyResolvedState(FlightStateSnapshot state, StateCorrection correction) {
		reset(state);
	}

	FlightStateSnapshot snapshot();

	FlightModelDiagnostics diagnostics();
}
