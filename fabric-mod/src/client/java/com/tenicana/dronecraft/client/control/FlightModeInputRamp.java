package com.tenicana.dronecraft.client.control;

final class FlightModeInputRamp {
	private final int ticks;
	private int remainingTicks;

	FlightModeInputRamp(int ticks) {
		this.ticks = Math.max(1, ticks);
	}

	void trigger() {
		remainingTicks = ticks;
	}

	void reset() {
		remainingTicks = 0;
	}

	float sampleAndAdvance() {
		if (remainingTicks <= 0) {
			return 1.0f;
		}
		int elapsedTicks = ticks - remainingTicks + 1;
		remainingTicks--;
		float normalized = Math.max(0.0f, Math.min(1.0f, (float) elapsedTicks / ticks));
		return normalized * normalized * (3.0f - 2.0f * normalized);
	}
}
