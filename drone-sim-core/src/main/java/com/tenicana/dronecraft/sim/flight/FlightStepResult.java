package com.tenicana.dronecraft.sim.flight;

import java.util.List;

public record FlightStepResult(
		FlightStateSnapshot nextState,
		ActuatorOutput actuatorOutput,
		ForceTorqueDiagnostics forceTorqueDiagnostics,
		List<StateCorrection> stateCorrections,
		FlightModelDiagnostics diagnostics
) {
	public FlightStepResult {
		nextState = nextState == null ? FlightStateSnapshot.zero() : nextState;
		actuatorOutput = actuatorOutput == null ? ActuatorOutput.empty() : actuatorOutput;
		forceTorqueDiagnostics = forceTorqueDiagnostics == null ? ForceTorqueDiagnostics.zero() : forceTorqueDiagnostics;
		stateCorrections = stateCorrections == null ? List.of() : List.copyOf(stateCorrections);
		diagnostics = diagnostics == null ? FlightModelDiagnostics.empty() : diagnostics;
	}
}
