package com.tenicana.dronecraft.sim;

import java.util.Locale;

public record DroneEnvironmentOverride(
		boolean windEnabled,
		Vec3 windVelocityWorldMetersPerSecond,
		boolean turbulenceEnabled,
		double turbulenceIntensity,
		boolean airDensityEnabled,
		double airDensityRatio
) {
	public DroneEnvironmentOverride {
		if (windVelocityWorldMetersPerSecond == null || !windVelocityWorldMetersPerSecond.isFinite()) {
			windVelocityWorldMetersPerSecond = Vec3.ZERO;
		}
		if (!windEnabled) {
			windVelocityWorldMetersPerSecond = Vec3.ZERO;
		}
		if (!Double.isFinite(turbulenceIntensity)) {
			turbulenceIntensity = 0.0;
		}
		turbulenceIntensity = MathUtil.clamp(turbulenceIntensity, 0.0, 1.5);
		if (!turbulenceEnabled) {
			turbulenceIntensity = 0.0;
		}
		if (!Double.isFinite(airDensityRatio)) {
			airDensityRatio = 1.0;
		}
		airDensityRatio = MathUtil.clamp(airDensityRatio, 0.35, 1.35);
		if (!airDensityEnabled) {
			airDensityRatio = 1.0;
		}
	}

	public static DroneEnvironmentOverride natural() {
		return new DroneEnvironmentOverride(false, Vec3.ZERO, false, 0.0, false, 1.0);
	}

	public DroneEnvironmentOverride withWind(Vec3 windVelocityWorldMetersPerSecond) {
		return new DroneEnvironmentOverride(true, windVelocityWorldMetersPerSecond, turbulenceEnabled, turbulenceIntensity, airDensityEnabled, airDensityRatio);
	}

	public DroneEnvironmentOverride withoutWind() {
		return new DroneEnvironmentOverride(false, Vec3.ZERO, turbulenceEnabled, turbulenceIntensity, airDensityEnabled, airDensityRatio);
	}

	public DroneEnvironmentOverride withTurbulence(double turbulenceIntensity) {
		return new DroneEnvironmentOverride(windEnabled, windVelocityWorldMetersPerSecond, true, turbulenceIntensity, airDensityEnabled, airDensityRatio);
	}

	public DroneEnvironmentOverride withoutTurbulence() {
		return new DroneEnvironmentOverride(windEnabled, windVelocityWorldMetersPerSecond, false, 0.0, airDensityEnabled, airDensityRatio);
	}

	public DroneEnvironmentOverride withAirDensity(double airDensityRatio) {
		return new DroneEnvironmentOverride(windEnabled, windVelocityWorldMetersPerSecond, turbulenceEnabled, turbulenceIntensity, true, airDensityRatio);
	}

	public DroneEnvironmentOverride withoutAirDensity() {
		return new DroneEnvironmentOverride(windEnabled, windVelocityWorldMetersPerSecond, turbulenceEnabled, turbulenceIntensity, false, 1.0);
	}

	public Vec3 windOr(Vec3 naturalWindVelocityWorldMetersPerSecond) {
		return windEnabled ? windVelocityWorldMetersPerSecond : safeWind(naturalWindVelocityWorldMetersPerSecond);
	}

	public double turbulenceOr(double naturalTurbulenceIntensity) {
		if (turbulenceEnabled) {
			return turbulenceIntensity;
		}
		if (!Double.isFinite(naturalTurbulenceIntensity)) {
			return 0.0;
		}
		return MathUtil.clamp(naturalTurbulenceIntensity, 0.0, 1.5);
	}

	public double airDensityOr(double naturalAirDensityRatio) {
		if (airDensityEnabled) {
			return airDensityRatio;
		}
		if (!Double.isFinite(naturalAirDensityRatio)) {
			return 1.0;
		}
		return MathUtil.clamp(naturalAirDensityRatio, 0.35, 1.35);
	}

	public boolean active() {
		return windEnabled || turbulenceEnabled || airDensityEnabled;
	}

	public String formatForChat() {
		return String.format(
				Locale.ROOT,
				"Environment override: wind %s%s | turbulence %s%s | density %s%s",
				windEnabled ? "fixed " : "natural",
				windEnabled ? String.format(
						Locale.ROOT,
						"%.2f %.2f %.2fm/s",
						windVelocityWorldMetersPerSecond.x(),
						windVelocityWorldMetersPerSecond.y(),
						windVelocityWorldMetersPerSecond.z()
				) : "",
				turbulenceEnabled ? "fixed " : "natural",
				turbulenceEnabled ? String.format(Locale.ROOT, "%.2f", turbulenceIntensity) : "",
				airDensityEnabled ? "fixed " : "natural",
				airDensityEnabled ? String.format(Locale.ROOT, "%.2f", airDensityRatio) : ""
		);
	}

	private static Vec3 safeWind(Vec3 windVelocityWorldMetersPerSecond) {
		if (windVelocityWorldMetersPerSecond == null || !windVelocityWorldMetersPerSecond.isFinite()) {
			return Vec3.ZERO;
		}
		return windVelocityWorldMetersPerSecond;
	}
}
