package com.tenicana.dronecraft.client.control;

import java.util.HashSet;
import java.util.Set;

/** Standalone regression check: compile directly with the production allocator, without SDL. */
public final class SdlJoystickSlotsCheck {
	private static int assertions;

	public static void main(String[] args) {
		sequentialDistinctDevicesDoNotExhaustLifetimeCapacity();
		ordinaryReconnectKeepsItsSlot();
		activeDevicesSurviveReservationReclamation();
		allActiveSlotsRejectAdditionalDevices();
		identicalDevicesGetDistinctActiveSlots();
		System.out.println("PASS: 5 SDL slot allocation scenarios; " + assertions + " assertions");
	}

	private static void sequentialDistinctDevicesDoNotExhaustLifetimeCapacity() {
		SdlJoystickSlots slots = new SdlJoystickSlots(16);
		for (int device = 0; device < 64; device++) {
			int slot = slots.acquire("distinct-" + device);
			check(slot >= 0 && slot < 16, "Sequential device " + device + " must receive a slot");
			slots.release(slot);
		}
	}

	private static void ordinaryReconnectKeepsItsSlot() {
		SdlJoystickSlots slots = new SdlJoystickSlots(16);
		int radio = slots.acquire("radio");
		int gamepad = slots.acquire("gamepad");
		slots.release(radio);
		int other = slots.acquire("other");
		check(other != radio && other != gamepad, "Unused capacity must preserve reconnect reservations");
		check(slots.acquire("radio") == radio, "Ordinary reconnect must keep its previous slot");
	}

	private static void activeDevicesSurviveReservationReclamation() {
		SdlJoystickSlots slots = new SdlJoystickSlots(16);
		int radio = slots.acquire("active-radio");
		int gamepad = slots.acquire("active-gamepad");
		for (int device = 0; device < 64; device++) {
			int slot = slots.acquire("churn-" + device);
			check(slot >= 0 && slot != radio && slot != gamepad, "Historical churn must not collide with active devices");
			slots.release(slot);
		}
		slots.release(radio);
		check(slots.acquire("active-radio") == radio, "Reclamation must retain the active radio identity");
		slots.release(gamepad);
		check(slots.acquire("active-gamepad") == gamepad, "Reclamation must retain the active gamepad identity");
	}

	private static void allActiveSlotsRejectAdditionalDevices() {
		SdlJoystickSlots slots = new SdlJoystickSlots(16);
		Set<Integer> active = new HashSet<>();
		int[] original = new int[16];
		for (int device = 0; device < 16; device++) {
			original[device] = slots.acquire("active-" + device);
			check(original[device] >= 0 && active.add(original[device]), "Each active device must have a distinct slot");
		}
		check(slots.acquire("overflow") == -1, "All sixteen active slots must reject a seventeenth device");
		check(slots.acquire("active-0") == -1, "A duplicate identity must not steal an active slot");
		int released = original[7];
		slots.release(released);
		check(slots.acquire("overflow") == released, "A new device must reclaim the only inactive slot");
		for (int device = 0; device < 16; device++) {
			if (device == 7) continue;
			slots.release(original[device]);
			check(slots.acquire("active-" + device) == original[device], "Overflow must not alter remaining active identities");
		}
	}

	private static void identicalDevicesGetDistinctActiveSlots() {
		SdlJoystickSlots slots = new SdlJoystickSlots(16);
		int first = slots.acquire("same-model-without-serial");
		int second = slots.acquire("same-model-without-serial");
		check(first >= 0 && second >= 0 && first != second, "Identical device identities need distinct active slots");
		slots.release(first);
		check(slots.acquire("same-model-without-serial") == first, "Identical reconnect must reuse an inactive reservation");
	}

	private static void check(boolean condition, String message) {
		assertions++;
		if (!condition) throw new AssertionError(message);
	}
}
