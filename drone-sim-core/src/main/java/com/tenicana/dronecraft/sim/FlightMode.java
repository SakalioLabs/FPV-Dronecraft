package com.tenicana.dronecraft.sim;

public enum FlightMode {
	ACRO,
	ANGLE,
	HORIZON;

	public int id() {
		return ordinal();
	}

	public String csvName() {
		return name().toLowerCase();
	}

	public FlightMode next() {
		FlightMode[] modes = values();
		return modes[(ordinal() + 1) % modes.length];
	}

	public static FlightMode byId(int id) {
		FlightMode[] modes = values();
		if (id < 0 || id >= modes.length) {
			return ACRO;
		}
		return modes[id];
	}
}
