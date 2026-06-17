package com.tenicana.dronecraft.client.control;

final class StickArmGestureLatch {
	private final int holdTicks;
	private int ticks;
	private boolean latched;

	StickArmGestureLatch(int holdTicks) {
		this.holdTicks = Math.max(1, holdTicks);
	}

	boolean update(boolean gestureActive) {
		if (!gestureActive) {
			reset();
			return false;
		}
		if (latched) {
			return false;
		}
		ticks++;
		if (ticks < holdTicks) {
			return false;
		}
		latched = true;
		return true;
	}

	void reset() {
		ticks = 0;
		latched = false;
	}
}
