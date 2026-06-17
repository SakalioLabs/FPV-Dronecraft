package com.tenicana.dronecraft.sim;

public enum FlightMode {
	ACRO,
	ANGLE,
	HORIZON;

	public static final FlightMode DEFAULT_FIRST_FLIGHT = ANGLE;

	public int id() {
		return ordinal();
	}

	public String csvName() {
		return name().toLowerCase();
	}

	public FlightMode next() {
		return switch (this) {
			case ANGLE -> HORIZON;
			case HORIZON -> ACRO;
			case ACRO -> ANGLE;
		};
	}

	public static FlightMode byId(int id) {
		FlightMode[] modes = values();
		if (id < 0 || id >= modes.length) {
			return DEFAULT_FIRST_FLIGHT;
		}
		return modes[id];
	}
}
