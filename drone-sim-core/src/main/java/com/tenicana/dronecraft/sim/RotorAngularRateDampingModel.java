package com.tenicana.dronecraft.sim;

import java.util.List;

public final class RotorAngularRateDampingModel {
	private static final Vec3 DEFAULT_ROTOR_AXIS_BODY = new Vec3(0.0, 1.0, 0.0);
	private static final double REFERENCE_DISK_DRAG_COEFFICIENT = 0.0028;
	private static final double TORQUE_COMPONENT_LIMIT_NEWTON_METERS = 0.18;

	private RotorAngularRateDampingModel() {
	}

	public static RotorAngularRateDampingSample sample(
			RotorSpec rotor,
			Vec3 bodyAngularVelocityRadiansPerSecond,
			Vec3 rotorDiskAxisBody,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double airDensityRatio,
			double aerodynamicLoadFactor,
			double rotorStallIntensity,
			double wakeInterferenceIntensity
	) {
		validateInputs(
				rotor,
				bodyAngularVelocityRadiansPerSecond,
				rotorDiskAxisBody,
				omegaRadiansPerSecond,
				thrustNewtons,
				airDensityRatio,
				aerodynamicLoadFactor,
				rotorStallIntensity,
				wakeInterferenceIntensity
		);
		double spinRatio = MathUtil.clamp(
				Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(),
				0.0,
				1.0
		);
		Vec3 diskAxisBody = resolvedDiskAxisBody(rotorDiskAxisBody);
		double axialRate = bodyAngularVelocityRadiansPerSecond.dot(diskAxisBody);
		Vec3 axialRateBody = diskAxisBody.multiply(axialRate);
		Vec3 transverseRateBody = bodyAngularVelocityRadiansPerSecond.subtract(axialRateBody);
		double rateLoadFactor = loadFactor(rotor, bodyAngularVelocityRadiansPerSecond, omegaRadiansPerSecond);
		boolean active = rotor.diskDragCoefficient() > 0.0
				&& spinRatio > 0.08
				&& bodyAngularVelocityRadiansPerSecond.lengthSquared() > 1.0e-9;
		double minimumDiskLoadNewtons = rotor.maxThrustNewtons() * spinRatio * spinRatio * 0.18;
		double diskLoadNewtons = active ? Math.max(thrustNewtons, minimumDiskLoadNewtons) : 0.0;
		double effectiveAerodynamicLoadFactor = aerodynamicLoadFactor <= 1.0e-6
				? 1.0
				: MathUtil.clamp(aerodynamicLoadFactor, 0.35, 2.0);
		double dirtyAirFactor = 1.0 + 0.22 * rotorStallIntensity + 0.18 * wakeInterferenceIntensity;
		double transverseMomentPerRadPerSecond = 0.0;
		double axialMomentPerRadPerSecond = 0.0;
		Vec3 rawTransverseTorqueBody = Vec3.ZERO;
		Vec3 rawAxialTorqueBody = Vec3.ZERO;
		Vec3 rawDampingTorqueBody = Vec3.ZERO;
		Vec3 dampingTorqueBody = Vec3.ZERO;
		if (active) {
			transverseMomentPerRadPerSecond = diskLoadNewtons
					* rotor.radiusMeters()
					* rotor.diskDragCoefficient()
					* Math.max(0.2, airDensityRatio)
					* spinRatio
					* (0.85 + 0.15 * effectiveAerodynamicLoadFactor)
					* dirtyAirFactor
					* 2.4;
			axialMomentPerRadPerSecond = transverseMomentPerRadPerSecond * 0.22;
			rawTransverseTorqueBody = transverseRateBody.multiply(-transverseMomentPerRadPerSecond);
			rawAxialTorqueBody = axialRateBody.multiply(-axialMomentPerRadPerSecond);
			rawDampingTorqueBody = rawTransverseTorqueBody.add(rawAxialTorqueBody);
			dampingTorqueBody = rawDampingTorqueBody.clamp(
					-TORQUE_COMPONENT_LIMIT_NEWTON_METERS,
					TORQUE_COMPONENT_LIMIT_NEWTON_METERS
			);
		}
		Vec3 clampResidualBody = dampingTorqueBody.subtract(rawDampingTorqueBody);
		double rawPowerDissipation = Math.max(
				0.0,
				-rawDampingTorqueBody.dot(bodyAngularVelocityRadiansPerSecond)
		);
		double powerDissipation = Math.max(
				0.0,
				-dampingTorqueBody.dot(bodyAngularVelocityRadiansPerSecond)
		);
		return new RotorAngularRateDampingSample(
				rotor,
				bodyAngularVelocityRadiansPerSecond,
				diskAxisBody,
				omegaRadiansPerSecond,
				thrustNewtons,
				airDensityRatio,
				aerodynamicLoadFactor,
				rotorStallIntensity,
				wakeInterferenceIntensity,
				spinRatio,
				axialRate,
				axialRateBody,
				transverseRateBody,
				minimumDiskLoadNewtons,
				diskLoadNewtons,
				effectiveAerodynamicLoadFactor,
				dirtyAirFactor,
				rateLoadFactor,
				transverseMomentPerRadPerSecond,
				axialMomentPerRadPerSecond,
				rawTransverseTorqueBody,
				rawAxialTorqueBody,
				rawDampingTorqueBody,
				dampingTorqueBody,
				clampResidualBody,
				rawPowerDissipation,
				powerDissipation,
				active,
				clampResidualBody.lengthSquared() > 0.0
		);
	}

	public static ConfigurationRotorAngularRateDampingSample aggregate(
			List<RotorAngularRateDampingSample> rotorSamples
	) {
		List<RotorAngularRateDampingSample> requestedSamples = rotorSamples == null ? List.of() : rotorSamples;
		for (RotorAngularRateDampingSample sample : requestedSamples) {
			if (sample == null) {
				throw new IllegalArgumentException("rotorSamples must not contain null entries.");
			}
		}
		List<RotorAngularRateDampingSample> samples = List.copyOf(requestedSamples);
		Vec3 totalRawDampingTorqueBody = Vec3.ZERO;
		Vec3 totalDampingTorqueBody = Vec3.ZERO;
		Vec3 totalClampResidualBody = Vec3.ZERO;
		double totalRawPowerDissipation = 0.0;
		double totalPowerDissipation = 0.0;
		double maximumRateLoadFactor = 0.0;
		int activeRotorCount = 0;
		int clampedRotorCount = 0;
		for (RotorAngularRateDampingSample sample : samples) {
			totalRawDampingTorqueBody = totalRawDampingTorqueBody.add(
					sample.rawDampingTorqueBodyNewtonMeters()
			);
			totalDampingTorqueBody = totalDampingTorqueBody.add(sample.dampingTorqueBodyNewtonMeters());
			totalClampResidualBody = totalClampResidualBody.add(sample.clampResidualBodyNewtonMeters());
			totalRawPowerDissipation += sample.rawPowerDissipationWatts();
			totalPowerDissipation += sample.powerDissipationWatts();
			maximumRateLoadFactor = Math.max(maximumRateLoadFactor, sample.rateLoadFactor());
			if (sample.active()) {
				activeRotorCount++;
			}
			if (sample.torqueClamped()) {
				clampedRotorCount++;
			}
		}
		return new ConfigurationRotorAngularRateDampingSample(
				samples,
				totalRawDampingTorqueBody,
				totalDampingTorqueBody,
				totalClampResidualBody,
				totalRawPowerDissipation,
				totalPowerDissipation,
				maximumRateLoadFactor,
				activeRotorCount,
				clampedRotorCount
		);
	}

	public static double loadFactor(
			RotorSpec rotor,
			Vec3 bodyAngularVelocityRadiansPerSecond,
			double omegaRadiansPerSecond
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		validateFiniteVector(bodyAngularVelocityRadiansPerSecond, "bodyAngularVelocityRadiansPerSecond");
		if (!Double.isFinite(omegaRadiansPerSecond) || omegaRadiansPerSecond < 0.0) {
			throw new IllegalArgumentException("omegaRadiansPerSecond must be finite and nonnegative.");
		}
		double spinRatio = MathUtil.clamp(
				Math.abs(omegaRadiansPerSecond) / rotor.maxOmegaRadiansPerSecond(),
				0.0,
				1.0
		);
		if (rotor.diskDragCoefficient() <= 0.0 || spinRatio <= 0.08) {
			return 0.0;
		}

		Vec3 axis = rotorAxisBody(rotor);
		double axialRate = Math.abs(bodyAngularVelocityRadiansPerSecond.dot(axis));
		double transverseRate = bodyAngularVelocityRadiansPerSecond.subtract(
				axis.multiply(bodyAngularVelocityRadiansPerSecond.dot(axis))
		).length();
		double diskDragScale = MathUtil.clamp(
				rotor.diskDragCoefficient() / REFERENCE_DISK_DRAG_COEFFICIENT,
				0.0,
				3.5
		);
		double rateLoad = smoothStep(
				Math.toRadians(180.0),
				Math.toRadians(900.0),
				transverseRate + 0.22 * axialRate
		);
		return MathUtil.clamp(0.16 * diskDragScale * spinRatio * rateLoad, 0.0, 0.45);
	}

	public record RotorAngularRateDampingSample(
			RotorSpec rotor,
			Vec3 bodyAngularVelocityRadiansPerSecond,
			Vec3 rotorDiskAxisBody,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double airDensityRatio,
			double aerodynamicLoadFactor,
			double rotorStallIntensity,
			double wakeInterferenceIntensity,
			double spinRatio,
			double axialRateRadiansPerSecond,
			Vec3 axialRateBodyRadiansPerSecond,
			Vec3 transverseRateBodyRadiansPerSecond,
			double minimumDiskLoadNewtons,
			double diskLoadNewtons,
			double effectiveAerodynamicLoadFactor,
			double dirtyAirFactor,
			double rateLoadFactor,
			double transverseMomentPerRadPerSecond,
			double axialMomentPerRadPerSecond,
			Vec3 rawTransverseTorqueBodyNewtonMeters,
			Vec3 rawAxialTorqueBodyNewtonMeters,
			Vec3 rawDampingTorqueBodyNewtonMeters,
			Vec3 dampingTorqueBodyNewtonMeters,
			Vec3 clampResidualBodyNewtonMeters,
			double rawPowerDissipationWatts,
			double powerDissipationWatts,
			boolean active,
			boolean torqueClamped
	) {
		public Vec3 dampingTorqueWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(dampingTorqueBodyNewtonMeters);
		}
	}

	public record ConfigurationRotorAngularRateDampingSample(
			List<RotorAngularRateDampingSample> rotorSamples,
			Vec3 totalRawDampingTorqueBodyNewtonMeters,
			Vec3 totalDampingTorqueBodyNewtonMeters,
			Vec3 totalClampResidualBodyNewtonMeters,
			double totalRawPowerDissipationWatts,
			double totalPowerDissipationWatts,
			double maximumRateLoadFactor,
			int activeRotorCount,
			int clampedRotorCount
	) {
		public ConfigurationRotorAngularRateDampingSample {
			rotorSamples = List.copyOf(rotorSamples == null ? List.of() : rotorSamples);
		}

		public int rotorCount() {
			return rotorSamples.size();
		}

		public Vec3 totalDampingTorqueWorldNewtonMeters(Quaternion bodyToWorldOrientation) {
			return resolvedOrientation(bodyToWorldOrientation).rotate(totalDampingTorqueBodyNewtonMeters);
		}
	}

	private static Vec3 resolvedDiskAxisBody(Vec3 rotorDiskAxisBody) {
		if (rotorDiskAxisBody == null
				|| !rotorDiskAxisBody.isFinite()
				|| rotorDiskAxisBody.lengthSquared() <= 1.0e-9) {
			return DEFAULT_ROTOR_AXIS_BODY;
		}
		return rotorDiskAxisBody.normalized();
	}

	private static Vec3 rotorAxisBody(RotorSpec rotor) {
		Vec3 axis = rotor.thrustAxisBody();
		if (axis == null || !axis.isFinite() || axis.lengthSquared() <= 1.0e-9 || axis.y() <= 0.0) {
			return DEFAULT_ROTOR_AXIS_BODY;
		}
		return axis.normalized();
	}

	private static void validateInputs(
			RotorSpec rotor,
			Vec3 bodyAngularVelocityRadiansPerSecond,
			Vec3 rotorDiskAxisBody,
			double omegaRadiansPerSecond,
			double thrustNewtons,
			double airDensityRatio,
			double aerodynamicLoadFactor,
			double rotorStallIntensity,
			double wakeInterferenceIntensity
	) {
		if (rotor == null) {
			throw new IllegalArgumentException("rotor must not be null.");
		}
		validateFiniteVector(bodyAngularVelocityRadiansPerSecond, "bodyAngularVelocityRadiansPerSecond");
		if (rotorDiskAxisBody != null && !rotorDiskAxisBody.isFinite()) {
			throw new IllegalArgumentException("rotorDiskAxisBody must be finite when present.");
		}
		validateFiniteNonnegative(omegaRadiansPerSecond, "omegaRadiansPerSecond");
		validateFiniteNonnegative(thrustNewtons, "thrustNewtons");
		validateFiniteNonnegative(airDensityRatio, "airDensityRatio");
		validateFiniteNonnegative(aerodynamicLoadFactor, "aerodynamicLoadFactor");
		validateFiniteNonnegative(rotorStallIntensity, "rotorStallIntensity");
		validateFiniteNonnegative(wakeInterferenceIntensity, "wakeInterferenceIntensity");
	}

	private static void validateFiniteVector(Vec3 value, String name) {
		if (value == null || !value.isFinite()) {
			throw new IllegalArgumentException(name + " must be finite.");
		}
	}

	private static void validateFiniteNonnegative(double value, String name) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(name + " must be finite and nonnegative.");
		}
	}

	private static Quaternion resolvedOrientation(Quaternion bodyToWorldOrientation) {
		if (bodyToWorldOrientation == null
				|| !Double.isFinite(bodyToWorldOrientation.w())
				|| !Double.isFinite(bodyToWorldOrientation.x())
				|| !Double.isFinite(bodyToWorldOrientation.y())
				|| !Double.isFinite(bodyToWorldOrientation.z())) {
			return Quaternion.IDENTITY;
		}
		return bodyToWorldOrientation.normalized();
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
