package com.tenicana.dronecraft.sim.flight;

public record FlightModelCapabilities(
		boolean motorTelemetry,
		boolean highFidelityPowertrain,
		boolean environmentalAero,
		boolean assistedStateCorrection,
		boolean lossyStateMapping
) {
	public static FlightModelCapabilities minimal() {
		return new FlightModelCapabilities(false, false, false, false, false);
	}

	public static FlightModelCapabilities playableDirect() {
		return new FlightModelCapabilities(true, false, false, true, true);
	}

	public static FlightModelCapabilities simulation() {
		return new FlightModelCapabilities(true, true, true, true, false);
	}
}
