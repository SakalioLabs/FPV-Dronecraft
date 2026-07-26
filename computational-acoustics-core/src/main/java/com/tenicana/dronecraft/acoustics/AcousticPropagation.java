package com.tenicana.dronecraft.acoustics;

public final class AcousticPropagation {
	public static final double REFERENCE_DISTANCE_METERS = 1.0;

	private AcousticPropagation() {
	}

	public static double speedOfSoundMetersPerSecond(double temperatureCelsius) {
		if (!Double.isFinite(temperatureCelsius) || temperatureCelsius < -80.0 || temperatureCelsius > 80.0) {
			throw new IllegalArgumentException("temperatureCelsius must be finite and in [-80, 80]");
		}
		return 331.3 + 0.606 * temperatureCelsius;
	}

	public static double pressureDistanceGain(double distanceMeters) {
		if (!Double.isFinite(distanceMeters) || distanceMeters < 0.0) {
			throw new IllegalArgumentException("distanceMeters must be finite and non-negative");
		}
		return REFERENCE_DISTANCE_METERS / Math.max(distanceMeters, REFERENCE_DISTANCE_METERS);
	}

	/**
	 * Classical moving-source/moving-listener Doppler ratio. Positive radial
	 * velocities point from listener toward source.
	 */
	public static double dopplerRatio(
			double soundSpeed,
			double listenerRadialVelocity,
			double sourceRadialVelocity
	) {
		if (!Double.isFinite(soundSpeed) || soundSpeed <= 0.0) {
			throw new IllegalArgumentException("soundSpeed must be finite and positive");
		}
		if (!Double.isFinite(listenerRadialVelocity) || !Double.isFinite(sourceRadialVelocity)) {
			throw new IllegalArgumentException("radial velocities must be finite");
		}
		double numerator = soundSpeed + listenerRadialVelocity;
		double denominator = soundSpeed + sourceRadialVelocity;
		if (numerator <= soundSpeed * 0.05 || denominator <= soundSpeed * 0.05) {
			throw new IllegalArgumentException("radial velocity is outside the stable subsonic domain");
		}
		return numerator / denominator;
	}
}
