package com.tenicana.dronecraft.client.control;

import java.util.Objects;

/**
 * Keeps reconnect reservations until capacity requires reclaiming an inactive slot.
 * Active slots belong to open joystick handles and are never reassigned.
 */
final class SdlJoystickSlots {
	private final String[] identities;
	private final boolean[] active;
	private final long[] lastUsed;
	private long sequence;

	SdlJoystickSlots(int capacity) {
		if (capacity <= 0) throw new IllegalArgumentException("Slot capacity must be positive");
		identities = new String[capacity];
		active = new boolean[capacity];
		lastUsed = new long[capacity];
	}

	/** Acquires a distinct slot for one open handle, or returns -1 when all are active. */
	int acquire(String identity) {
		Objects.requireNonNull(identity, "identity");
		int unused = -1;
		int oldestInactive = -1;
		for (int slot = 0; slot < identities.length; slot++) {
			if (active[slot]) continue;
			if (identity.equals(identities[slot])) return activate(slot, identity);
			if (identities[slot] == null) {
				if (unused < 0) unused = slot;
			} else if (oldestInactive < 0 || lastUsed[slot] < lastUsed[oldestInactive]) {
				oldestInactive = slot;
			}
		}
		int available = unused >= 0 ? unused : oldestInactive;
		return available < 0 ? -1 : activate(available, identity);
	}

	/** Releases a handle while retaining its identity for ordinary reconnects. */
	void release(int slot) {
		if (slot < 0 || slot >= active.length) throw new IllegalArgumentException("Invalid slot: " + slot);
		active[slot] = false;
		lastUsed[slot] = ++sequence;
	}

	private int activate(int slot, String identity) {
		identities[slot] = identity;
		active[slot] = true;
		lastUsed[slot] = ++sequence;
		return slot;
	}
}
