package com.tenicana.dronecraft.entity;

import java.util.Locale;

public enum PlayableFlightPreset {
	LEGACY_HEAVY_RACING_QUAD("legacy_heavy_racing_quad"),
	FIVE_INCH_AGILE_CANDIDATE("5inch_agile_candidate");

	private final String id;

	PlayableFlightPreset(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static PlayableFlightPreset defaultPreset() {
		return LEGACY_HEAVY_RACING_QUAD;
	}

	public static PlayableFlightPreset byId(String id) {
		if (id == null || id.isBlank()) {
			return defaultPreset();
		}
		String normalized = id.trim().toLowerCase(Locale.ROOT);
		for (PlayableFlightPreset preset : values()) {
			if (preset.id.equals(normalized)) {
				return preset;
			}
		}
		return defaultPreset();
	}
}
