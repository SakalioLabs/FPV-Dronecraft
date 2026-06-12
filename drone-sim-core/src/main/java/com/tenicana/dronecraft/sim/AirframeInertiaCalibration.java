package com.tenicana.dronecraft.sim;

public final class AirframeInertiaCalibration {
	public static final String APDRONE_INERTIA_SOURCE_ID = "APDrone-Mendeley-Inertia-PDF";
	public static final double APDRONE_REFERENCE_MASS_KG = 0.6284;
	public static final double APDRONE_REFERENCE_SOURCE_INERTIA_X_KG_METERS_SQUARED = 0.001346;
	public static final double APDRONE_REFERENCE_SOURCE_INERTIA_Y_KG_METERS_SQUARED = 0.001410;
	public static final double APDRONE_REFERENCE_SOURCE_YAW_INERTIA_Z_KG_METERS_SQUARED = 0.002480;
	public static final double APDRONE_REFERENCE_MOTOR_CENTER_RADIUS_METERS = 0.095;

	private AirframeInertiaCalibration() {
	}

	public record ApDroneInertiaAudit(
			String referenceId,
			double referenceMassKg,
			double currentMassKg,
			double currentMassOverReference,
			double referenceSourceInertiaXKgMetersSquared,
			double referenceSourceInertiaYKgMetersSquared,
			double referenceSourceYawInertiaZKgMetersSquared,
			double currentProjectInertiaXKgMetersSquared,
			double currentProjectYawInertiaYKgMetersSquared,
			double currentProjectInertiaZKgMetersSquared,
			double currentProjectXOverReferenceSourceX,
			double currentProjectZOverReferenceSourceY,
			double currentProjectYawYOverReferenceSourceYawZ,
			double referenceMotorCenterRadiusMeters,
			double currentMotorCenterRadiusMeters,
			double currentMotorCenterRadiusOverReference,
			double referenceRadiusOfGyrationSourceXMeters,
			double referenceRadiusOfGyrationSourceYMeters,
			double referenceRadiusOfGyrationYawZMeters,
			double currentRadiusOfGyrationXMeters,
			double currentRadiusOfGyrationYawYMeters,
			double currentRadiusOfGyrationZMeters,
			double currentRadiusOfGyrationXOverReferenceSourceX,
			double currentRadiusOfGyrationZOverReferenceSourceY,
			double currentRadiusOfGyrationYawYOverReferenceYawZ,
			double referenceYawToRollPitchMeanInertiaRatio,
			double currentYawToRollPitchMeanInertiaRatio,
			double currentYawRatioOverReference
	) {
	}

	public static ApDroneInertiaAudit apDroneInertiaAudit(DroneConfig config) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		double currentMotorCenterRadius = averageMotorCenterRadiusMeters(config);
		double referenceYawRatio = yawToRollPitchMeanRatio(
				APDRONE_REFERENCE_SOURCE_INERTIA_X_KG_METERS_SQUARED,
				APDRONE_REFERENCE_SOURCE_INERTIA_Y_KG_METERS_SQUARED,
				APDRONE_REFERENCE_SOURCE_YAW_INERTIA_Z_KG_METERS_SQUARED
		);
		double currentYawRatio = yawToRollPitchMeanRatio(
				config.inertiaKgMetersSquared().x(),
				config.inertiaKgMetersSquared().z(),
				config.inertiaKgMetersSquared().y()
		);
		return new ApDroneInertiaAudit(
				APDRONE_INERTIA_SOURCE_ID,
				APDRONE_REFERENCE_MASS_KG,
				config.massKg(),
				ratio(config.massKg(), APDRONE_REFERENCE_MASS_KG),
				APDRONE_REFERENCE_SOURCE_INERTIA_X_KG_METERS_SQUARED,
				APDRONE_REFERENCE_SOURCE_INERTIA_Y_KG_METERS_SQUARED,
				APDRONE_REFERENCE_SOURCE_YAW_INERTIA_Z_KG_METERS_SQUARED,
				config.inertiaKgMetersSquared().x(),
				config.inertiaKgMetersSquared().y(),
				config.inertiaKgMetersSquared().z(),
				ratio(config.inertiaKgMetersSquared().x(), APDRONE_REFERENCE_SOURCE_INERTIA_X_KG_METERS_SQUARED),
				ratio(config.inertiaKgMetersSquared().z(), APDRONE_REFERENCE_SOURCE_INERTIA_Y_KG_METERS_SQUARED),
				ratio(config.inertiaKgMetersSquared().y(), APDRONE_REFERENCE_SOURCE_YAW_INERTIA_Z_KG_METERS_SQUARED),
				APDRONE_REFERENCE_MOTOR_CENTER_RADIUS_METERS,
				currentMotorCenterRadius,
				ratio(currentMotorCenterRadius, APDRONE_REFERENCE_MOTOR_CENTER_RADIUS_METERS),
				radiusOfGyration(APDRONE_REFERENCE_SOURCE_INERTIA_X_KG_METERS_SQUARED, APDRONE_REFERENCE_MASS_KG),
				radiusOfGyration(APDRONE_REFERENCE_SOURCE_INERTIA_Y_KG_METERS_SQUARED, APDRONE_REFERENCE_MASS_KG),
				radiusOfGyration(APDRONE_REFERENCE_SOURCE_YAW_INERTIA_Z_KG_METERS_SQUARED, APDRONE_REFERENCE_MASS_KG),
				radiusOfGyration(config.inertiaKgMetersSquared().x(), config.massKg()),
				radiusOfGyration(config.inertiaKgMetersSquared().y(), config.massKg()),
				radiusOfGyration(config.inertiaKgMetersSquared().z(), config.massKg()),
				ratio(
						radiusOfGyration(config.inertiaKgMetersSquared().x(), config.massKg()),
						radiusOfGyration(APDRONE_REFERENCE_SOURCE_INERTIA_X_KG_METERS_SQUARED, APDRONE_REFERENCE_MASS_KG)
				),
				ratio(
						radiusOfGyration(config.inertiaKgMetersSquared().z(), config.massKg()),
						radiusOfGyration(APDRONE_REFERENCE_SOURCE_INERTIA_Y_KG_METERS_SQUARED, APDRONE_REFERENCE_MASS_KG)
				),
				ratio(
						radiusOfGyration(config.inertiaKgMetersSquared().y(), config.massKg()),
						radiusOfGyration(APDRONE_REFERENCE_SOURCE_YAW_INERTIA_Z_KG_METERS_SQUARED, APDRONE_REFERENCE_MASS_KG)
				),
				referenceYawRatio,
				currentYawRatio,
				ratio(currentYawRatio, referenceYawRatio)
		);
	}

	private static double averageMotorCenterRadiusMeters(DroneConfig config) {
		double sum = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			Vec3 position = rotor.positionBodyMeters();
			sum += Math.hypot(position.x(), position.z());
		}
		return sum / config.rotors().size();
	}

	private static double radiusOfGyration(double inertiaKgMetersSquared, double massKg) {
		return inertiaKgMetersSquared <= 0.0 || massKg <= 0.0
				? 0.0
				: Math.sqrt(inertiaKgMetersSquared / massKg);
	}

	private static double yawToRollPitchMeanRatio(double rollAxisInertia, double pitchAxisInertia, double yawAxisInertia) {
		double meanRollPitch = 0.5 * (rollAxisInertia + pitchAxisInertia);
		return ratio(yawAxisInertia, meanRollPitch);
	}

	private static double ratio(double numerator, double denominator) {
		return denominator == 0.0 ? 0.0 : numerator / denominator;
	}
}
