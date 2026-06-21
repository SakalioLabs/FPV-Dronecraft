package com.tenicana.dronecraft.sim.flight;

import java.util.List;
import java.util.Map;

public record FlightModelDiagnostics(
		boolean finite,
		Map<String, String> values,
		List<StateCorrection> stateCorrections,
		List<String> lossyFields
) {
	public FlightModelDiagnostics {
		values = values == null ? Map.of() : Map.copyOf(values);
		stateCorrections = stateCorrections == null ? List.of() : List.copyOf(stateCorrections);
		lossyFields = lossyFields == null ? List.of() : List.copyOf(lossyFields);
	}

	public static FlightModelDiagnostics empty() {
		return new FlightModelDiagnostics(true, Map.of(), List.of(), List.of());
	}
}
