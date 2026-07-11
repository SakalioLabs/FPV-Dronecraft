package com.tenicana.dronecraft.sound;

public final class DroneSoundPhysics {
	public static final double MIN_AUDIBLE_RPM = 300.0;
	public static final double MIN_IMPACT_SPEED_METERS_PER_SECOND = 0.60;

	private static final double REFERENCE_RPM = 24_000.0;

	private DroneSoundPhysics() {
	}

	public static float motorVolume(double rpm, double motorPower, int rotorCount) {
		double rpmResponse = normalizedRpm(rpm);
		double activity = smoothStep(MIN_AUDIBLE_RPM, 1_500.0, finiteOrZero(rpm));
		double powerResponse = 0.70 + 0.30 * clamp01(motorPower);
		double volume = activity * rotorGain(rotorCount) * (0.055 + 0.30 * rpmResponse) * powerResponse;
		return (float) clamp(volume, 0.0, 0.45);
	}

	public static float motorPitch(double rpm, double motorPower) {
		double pitch = 0.62 + 1.05 * normalizedRpm(rpm) + 0.16 * clamp01(motorPower);
		return (float) clamp(pitch, 0.55, 1.85);
	}

	public static float propellerVolume(
			double rpm,
			double motorPower,
			double aerodynamicLoad,
			double airspeedMetersPerSecond,
			double turbulence,
			int rotorCount
	) {
		double rpmResponse = normalizedRpm(rpm);
		double activity = smoothStep(MIN_AUDIBLE_RPM, 1_500.0, finiteOrZero(rpm));
		double loadResponse = 0.68
				+ 0.22 * clamp(finiteOrZero(aerodynamicLoad), 0.0, 1.5)
				+ 0.10 * clamp01(motorPower);
		double airResponse = 1.0
				+ 0.18 * Math.sqrt(clamp(finiteOrZero(airspeedMetersPerSecond) / 30.0, 0.0, 1.0))
				+ 0.10 * clamp01(turbulence);
		double volume = activity
				* rotorGain(rotorCount)
				* (0.035 + 0.40 * Math.pow(rpmResponse, 1.20))
				* loadResponse
				* airResponse;
		return (float) clamp(volume, 0.0, 0.62);
	}

	public static float propellerPitch(double rpm, double airspeedMetersPerSecond, double turbulence) {
		double airResponse = Math.sqrt(clamp(finiteOrZero(airspeedMetersPerSecond) / 30.0, 0.0, 1.0));
		double pitch = 0.70 + 0.72 * normalizedRpm(rpm) + 0.08 * airResponse + 0.06 * clamp01(turbulence);
		return (float) clamp(pitch, 0.60, 1.58);
	}

	public static boolean isAudible(double rpm) {
		return Double.isFinite(rpm) && rpm > MIN_AUDIBLE_RPM;
	}

	public static boolean shouldPlayImpact(double impactSpeedMetersPerSecond) {
		return Double.isFinite(impactSpeedMetersPerSecond)
				&& impactSpeedMetersPerSecond >= MIN_IMPACT_SPEED_METERS_PER_SECOND;
	}

	public static float impactVolume(double impactSpeedMetersPerSecond) {
		double response = smoothStep(
				MIN_IMPACT_SPEED_METERS_PER_SECOND,
				8.0,
				finiteOrZero(impactSpeedMetersPerSecond)
		);
		return (float) clamp(0.10 + 0.28 * response, 0.10, 0.38);
	}

	public static float impactPitch(double impactSpeedMetersPerSecond, double randomUnit) {
		double response = smoothStep(
				MIN_IMPACT_SPEED_METERS_PER_SECOND,
				8.0,
				finiteOrZero(impactSpeedMetersPerSecond)
		);
		double variation = (clamp01(randomUnit) - 0.5) * 0.16;
		return (float) clamp(0.88 + 0.13 * response + variation, 0.80, 1.15);
	}

	private static double normalizedRpm(double rpm) {
		return Math.sqrt(clamp(finiteOrZero(rpm) / REFERENCE_RPM, 0.0, 1.0));
	}

	private static double rotorGain(int rotorCount) {
		return clamp(Math.sqrt(Math.max(1, rotorCount) / 4.0), 0.72, 1.35);
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	private static double clamp01(double value) {
		return clamp(finiteOrZero(value), 0.0, 1.0);
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
