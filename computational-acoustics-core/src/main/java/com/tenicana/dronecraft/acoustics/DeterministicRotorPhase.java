package com.tenicana.dronecraft.acoustics;

/**
 * Stable decorrelated initial rotor phases for sources that do not expose a
 * measured mechanical or commutation phase. This is an explicit fallback, not
 * a reconstruction of physical rotor synchronization.
 */
public final class DeterministicRotorPhase {
	private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;
	private static final double TWO_PI = 2.0 * Math.PI;
	private static final double UNIT_53 = 0x1.0p-53;

	private DeterministicRotorPhase() {
	}

	public static double phaseRadians(long sourceSeed, int rotorIndex) {
		if (rotorIndex < 0) {
			throw new IllegalArgumentException("rotorIndex must be non-negative");
		}
		long mixed = mix64(sourceSeed + GOLDEN_GAMMA * (rotorIndex + 1L));
		double unit = (mixed >>> 11) * UNIT_53;
		return unit * TWO_PI;
	}

	private static long mix64(long value) {
		value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
		value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
		return value ^ (value >>> 31);
	}
}
