package com.tenicana.dronecraft.sim;

public record DroneInput(
		double throttle,
		double pitch,
		double roll,
		double yaw,
		boolean armed,
		boolean linkActive,
		FlightMode flightMode
) {
	private static final FlightMode LEGACY_SIMULATION_FLIGHT_MODE = FlightMode.ACRO;

	public DroneInput(double throttle, double pitch, double roll, double yaw, boolean armed, boolean linkActive) {
		this(throttle, pitch, roll, yaw, armed, linkActive, LEGACY_SIMULATION_FLIGHT_MODE);
	}

	public DroneInput(double throttle, double pitch, double roll, double yaw, boolean armed, FlightMode flightMode) {
		this(throttle, pitch, roll, yaw, armed, true, flightMode);
	}

	public DroneInput(double throttle, double pitch, double roll, double yaw, boolean armed) {
		this(throttle, pitch, roll, yaw, armed, true, LEGACY_SIMULATION_FLIGHT_MODE);
	}

	public static DroneInput idle() {
		return new DroneInput(0.0, 0.0, 0.0, 0.0, false, false, FlightMode.DEFAULT_FIRST_FLIGHT);
	}

	public static DroneInput failsafe() {
		return idle();
	}

	public DroneInput normalized() {
		return new DroneInput(
				MathUtil.clamp(throttle, 0.0, 1.0),
				MathUtil.clamp(pitch, -1.0, 1.0),
				MathUtil.clamp(roll, -1.0, 1.0),
				MathUtil.clamp(yaw, -1.0, 1.0),
				armed,
				linkActive,
				flightMode == null ? FlightMode.DEFAULT_FIRST_FLIGHT : flightMode
		);
	}
}
