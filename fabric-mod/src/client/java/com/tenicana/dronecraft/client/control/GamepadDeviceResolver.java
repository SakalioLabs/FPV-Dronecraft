package com.tenicana.dronecraft.client.control;

import java.util.List;

import com.tenicana.dronecraft.client.config.DroneClientConfig;

public final class GamepadDeviceResolver {
	private GamepadDeviceResolver() {
	}

	public static Resolution resolve(DroneClientConfig config, JoystickProvider provider, boolean allowLegacyMigration) {
		DroneClientConfig safeConfig = config == null ? DroneClientConfig.defaults() : config;
		List<JoystickSnapshot> connected = provider == null ? List.of() : List.copyOf(provider.snapshots());
		if (connected.isEmpty()) {
			return new Resolution(connected, null, Status.NO_DEVICES, false);
		}

		String preferredGuid = safeConfig.preferredGamepadGuid();
		if (!preferredGuid.isBlank()) {
			JoystickSnapshot preferred = findPreferred(connected, preferredGuid, safeConfig.preferredGamepadName());
			if (preferred == null) {
				return new Resolution(connected, null, Status.PREFERRED_DISCONNECTED, false);
			}
			if (!preferred.hasRequiredAxes(safeConfig)) {
				return new Resolution(connected, preferred, Status.SELECTED_MISSING_AXES, false);
			}
			return new Resolution(connected, preferred, Status.SELECTED, false);
		}

		if (!allowLegacyMigration) {
			return new Resolution(connected, null, Status.NO_PREFERRED_DEVICE, false);
		}

		for (JoystickSnapshot snapshot : connected) {
			if (snapshot.hasRequiredAxes(safeConfig)) {
				safeConfig.setPreferredGamepadDevice(snapshot.guid(), snapshot.name());
				return new Resolution(connected, snapshot, Status.MIGRATED_LEGACY_DEVICE, true);
			}
		}
		return new Resolution(connected, null, Status.NO_VALID_DEVICE, false);
	}

	private static JoystickSnapshot findPreferred(List<JoystickSnapshot> connected, String guid, String name) {
		JoystickSnapshot guidMatch = null;
		for (JoystickSnapshot snapshot : connected) {
			if (!snapshot.guid().equals(guid)) {
				continue;
			}
			if (guidMatch == null) {
				guidMatch = snapshot;
			}
			if (!name.isBlank() && snapshot.name().equals(name)) {
				return snapshot;
			}
		}
		return guidMatch;
	}

	public enum Status {
		SELECTED,
		MIGRATED_LEGACY_DEVICE,
		NO_DEVICES,
		NO_PREFERRED_DEVICE,
		PREFERRED_DISCONNECTED,
		SELECTED_MISSING_AXES,
		NO_VALID_DEVICE
	}

	public record Resolution(List<JoystickSnapshot> connected, JoystickSnapshot selected, Status status, boolean migrated) {
		public Resolution {
			connected = connected == null ? List.of() : List.copyOf(connected);
			status = status == null ? Status.NO_DEVICES : status;
		}

		public boolean hasController() {
			return !connected.isEmpty();
		}

		public boolean ready(DroneClientConfig config) {
			return selected != null && selected.hasRequiredAxes(config);
		}

		public String selectedLabel() {
			if (selected == null) {
				return "none";
			}
			String guid = selected.guid().isBlank() ? "no-guid" : selected.guid();
			return selected.name() + " [" + guid + "] id=" + selected.glfwId();
		}
	}
}
