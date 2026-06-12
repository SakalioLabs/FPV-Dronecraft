package com.tenicana.dronecraft.sim;

public final class PropellerDamageCalibration {
	private static final double EPSILON = 1.0e-12;

	public static final String SOURCE_ID = "Prop-Damage-Vibration-Packet";
	public static final String CAVEAT =
			"Separate flight-phase fault envelopes, raw accelerometer spectra, normalized FFT features, and dimensionless runtime intensities.";
	public static final int PACKET_ROW_COUNT = 470;
	public static final int SOURCE_INVENTORY_ROW_COUNT = 8;
	public static final int UAV_REALISTIC_FAULT_CLASS_COUNT = 5;
	public static final int UAV_REALISTIC_DATA_FILE_COUNT = 216;
	public static final int UAV_REALISTIC_SENSORCOMBINED_FILE_COUNT = 99;
	public static final int UAV_REALISTIC_AUDIOBUFFER_FILE_COUNT = 99;
	public static final int UAV_REALISTIC_IMU_FILE_COUNT = 9;
	public static final double UAV_REALISTIC_TOTAL_SIZE_MB = 3475.290487;
	public static final double UAV_REALISTIC_SINGLE_BROKEN_GYRO_RMS_RATIO = 1.566803460010257;
	public static final double UAV_REALISTIC_SINGLE_BROKEN_ACCEL_RMS_RATIO = 3.65539974853721;
	public static final double UAV_REALISTIC_STRONGEST_GYRO_RMS_RATIO = 2.34459385954;
	public static final double UAV_REALISTIC_STRONGEST_ACCEL_RMS_RATIO = 7.41126253241;
	public static final double RAW_IMU_STRONGEST_GYRO_RMS_RATIO = 1.06409952825;
	public static final double RAW_IMU_STRONGEST_ACCEL_RMS_RATIO = 1.51580878648;
	public static final double PADRE_SINGLE_ROTOR_ACCEL_FEATURE_RMS_RATIO = 3.000977801285221;
	public static final double PADRE_TWO_POSITION_ACCEL_FEATURE_RMS_RATIO = 3.125830267410634;
	public static final double DJI_MINI2_SAMPLE_RATE_HERTZ = 1023.54145345;
	public static final double DJI_MINI2_NYQUIST_HERTZ = 511.770726726;
	public static final double DJI_MINI2_HEALTHY_DOMINANT_FREQUENCY_HERTZ = 159.678464052;
	public static final double DJI_MINI2_VECTOR_RMS_RATIO_MIN = 0.961371945529;
	public static final double DJI_MINI2_VECTOR_RMS_RATIO_MAX = 1.15481410476;
	public static final double RACING_HOVER_BLADE_PASS_FREQUENCY_HERTZ = 651.154535564;
	public static final double RACING_MAX_BLADE_PASS_FREQUENCY_HERTZ = 1456.88163747;
	public static final double RACING_HOVER_BLADE_PASS_ALIAS_1024_HERTZ = 372.386917888;
	public static final double RACING_MAX_BLADE_PASS_ALIAS_1024_HERTZ = 433.340184022;
	public static final double REFERENCE_AUDIT_DAMAGE = 0.75;
	public static final double REFERENCE_MAX_EFFECTIVE_IMBALANCE = 0.17625;

	private static final double HEALTHY_IMBALANCE_MAX = 0.35;
	private static final double DAMAGE_IMBALANCE_LINEAR = 0.10;
	private static final double DAMAGE_IMBALANCE_QUADRATIC = 0.18;
	private static final double MILD_DAMAGE_VIBRATION = 0.035;
	private static final double BENT_BLADE_DAMAGE_VIBRATION = 0.72;
	private static final double SEVERE_DAMAGE_VIBRATION = 0.20;
	private static final double DAMAGE_VIBRATION_MAX = 1.0;
	private static final double IMBALANCE_VIBRATION_MAX = 0.40;
	private static final double DAMAGE_PROFILE_DRAG_TORQUE_COEFFICIENT = 0.0048;

	private PropellerDamageCalibration() {
	}

	public record FaultDatasetAudit(
			int packetRowCount,
			int sourceInventoryRowCount,
			int uavRealisticFaultClassCount,
			int uavRealisticDataFileCount,
			int uavRealisticSensorcombinedFileCount,
			int uavRealisticAudiobufferFileCount,
			int uavRealisticImuFileCount,
			double uavRealisticTotalSizeMb,
			double singleBrokenGyroRmsRatio,
			double singleBrokenAccelerometerRmsRatio,
			double strongestSensorcombinedGyroRmsRatio,
			double strongestSensorcombinedAccelerometerRmsRatio,
			double strongestRawImuGyroRmsRatio,
			double strongestRawImuAccelerometerRmsRatio,
			double padreSingleRotorAccelerometerFeatureRmsRatio,
			double padreTwoPositionAccelerometerFeatureRmsRatio,
			double djiMini2SampleRateHertz,
			double djiMini2NyquistHertz,
			double djiMini2HealthyDominantFrequencyHertz,
			double djiMini2VectorRmsRatioMin,
			double djiMini2VectorRmsRatioMax
	) {
	}

	public record RuntimeModelAudit(
			double configuredHealthyImbalance,
			double referenceDamage,
			double referenceEffectiveImbalance,
			double referenceDamageVibrationAtMaxSpin,
			double referenceImbalanceVibrationAtMaxSpin,
			double referenceThrustScale,
			double referenceProfileDragTorqueAtMaxSpin,
			double fullDamageVibrationAtMaxSpin,
			double fullDamageThrustScale,
			double racingHoverBladePassFrequencyHertz,
			double racingMaxBladePassFrequencyHertz,
			double racingHoverBladePassAlias1024Hertz,
			double racingMaxBladePassAlias1024Hertz,
			double hoverBladePassOverDjiNyquist,
			double maxBladePassOverDjiNyquist
	) {
	}

	public record PropellerDamageAudit(
			String sourceId,
			String caveat,
			FaultDatasetAudit dataset,
			RuntimeModelAudit runtimeModel
	) {
	}

	public static PropellerDamageAudit audit(DroneConfig config) {
		return new PropellerDamageAudit(
				SOURCE_ID,
				CAVEAT,
				datasetAudit(),
				runtimeModelAudit(config)
		);
	}

	public static FaultDatasetAudit datasetAudit() {
		return new FaultDatasetAudit(
				PACKET_ROW_COUNT,
				SOURCE_INVENTORY_ROW_COUNT,
				UAV_REALISTIC_FAULT_CLASS_COUNT,
				UAV_REALISTIC_DATA_FILE_COUNT,
				UAV_REALISTIC_SENSORCOMBINED_FILE_COUNT,
				UAV_REALISTIC_AUDIOBUFFER_FILE_COUNT,
				UAV_REALISTIC_IMU_FILE_COUNT,
				UAV_REALISTIC_TOTAL_SIZE_MB,
				UAV_REALISTIC_SINGLE_BROKEN_GYRO_RMS_RATIO,
				UAV_REALISTIC_SINGLE_BROKEN_ACCEL_RMS_RATIO,
				UAV_REALISTIC_STRONGEST_GYRO_RMS_RATIO,
				UAV_REALISTIC_STRONGEST_ACCEL_RMS_RATIO,
				RAW_IMU_STRONGEST_GYRO_RMS_RATIO,
				RAW_IMU_STRONGEST_ACCEL_RMS_RATIO,
				PADRE_SINGLE_ROTOR_ACCEL_FEATURE_RMS_RATIO,
				PADRE_TWO_POSITION_ACCEL_FEATURE_RMS_RATIO,
				DJI_MINI2_SAMPLE_RATE_HERTZ,
				DJI_MINI2_NYQUIST_HERTZ,
				DJI_MINI2_HEALTHY_DOMINANT_FREQUENCY_HERTZ,
				DJI_MINI2_VECTOR_RMS_RATIO_MIN,
				DJI_MINI2_VECTOR_RMS_RATIO_MAX
		);
	}

	public static RuntimeModelAudit runtimeModelAudit(DroneConfig config) {
		RotorSpec rotor = config.rotors().get(0);
		double maxOmega = rotor.maxOmegaRadiansPerSecond();
		double radiusScale = MathUtil.clamp(rotor.radiusMeters() / 0.0635, 0.35, 3.0);
		double diskDragScale = MathUtil.clamp(rotor.diskDragCoefficient() / 0.0028, 0.25, 5.0);
		double referenceHealth = 1.0 - REFERENCE_AUDIT_DAMAGE;
		return new RuntimeModelAudit(
				MathUtil.clamp(rotor.imbalanceIntensity(), 0.0, HEALTHY_IMBALANCE_MAX),
				REFERENCE_AUDIT_DAMAGE,
				effectiveImbalanceIntensity(rotor, referenceHealth),
				damageVibrationIntensity(rotor, maxOmega, referenceHealth),
				imbalanceVibrationIntensity(rotor, maxOmega, referenceHealth),
				thrustScale(referenceHealth),
				profileDragTorque(referenceHealth, 1.0, radiusScale, diskDragScale),
				damageVibrationIntensity(rotor, maxOmega, 0.0),
				thrustScale(0.0),
				RACING_HOVER_BLADE_PASS_FREQUENCY_HERTZ,
				RACING_MAX_BLADE_PASS_FREQUENCY_HERTZ,
				RACING_HOVER_BLADE_PASS_ALIAS_1024_HERTZ,
				RACING_MAX_BLADE_PASS_ALIAS_1024_HERTZ,
				ratio(RACING_HOVER_BLADE_PASS_FREQUENCY_HERTZ, DJI_MINI2_NYQUIST_HERTZ),
				ratio(RACING_MAX_BLADE_PASS_FREQUENCY_HERTZ, DJI_MINI2_NYQUIST_HERTZ)
		);
	}

	public static double damageAmount(double rotorHealth) {
		return 1.0 - MathUtil.clamp(rotorHealth, 0.0, 1.0);
	}

	public static double effectiveImbalanceIntensity(RotorSpec rotor, double rotorHealth) {
		double healthyPropImbalance = MathUtil.clamp(rotor.imbalanceIntensity(), 0.0, HEALTHY_IMBALANCE_MAX);
		return MathUtil.clamp(healthyPropImbalance + damageImbalanceIntensity(damageAmount(rotorHealth)), 0.0, HEALTHY_IMBALANCE_MAX);
	}

	public static double damageImbalanceIntensity(double damageAmount) {
		double damage = MathUtil.clamp(damageAmount, 0.0, 1.0);
		return damage * (DAMAGE_IMBALANCE_LINEAR + DAMAGE_IMBALANCE_QUADRATIC * damage);
	}

	public static double damageVibrationIntensity(RotorSpec rotor, double omegaRadiansPerSecond, double rotorHealth) {
		double damage = damageAmount(rotorHealth);
		if (damage <= EPSILON) {
			return 0.0;
		}
		double spinRatio = spinRatio(rotor, omegaRadiansPerSecond, 1.0);
		double mildFault = MILD_DAMAGE_VIBRATION * smoothStep(0.02, 0.12, damage);
		double bentBladeFault = BENT_BLADE_DAMAGE_VIBRATION * Math.pow(smoothStep(0.12, 0.85, damage), 1.35);
		double severeFault = SEVERE_DAMAGE_VIBRATION * smoothStep(0.70, 1.0, damage);
		double operatingSpin = smoothStep(0.08, 0.42, spinRatio);
		double centrifugalSpin = spinRatio * spinRatio;
		double spinVisibility = MathUtil.clamp(0.55 * operatingSpin + 0.45 * centrifugalSpin, 0.0, 1.0);
		return MathUtil.clamp((mildFault + bentBladeFault + severeFault) * spinVisibility, 0.0, DAMAGE_VIBRATION_MAX);
	}

	public static double imbalanceVibrationIntensity(RotorSpec rotor, double omegaRadiansPerSecond, double rotorHealth) {
		double imbalance = effectiveImbalanceIntensity(rotor, rotorHealth);
		if (imbalance <= EPSILON) {
			return 0.0;
		}
		double spinRatio = spinRatio(rotor, omegaRadiansPerSecond, 1.0);
		return MathUtil.clamp(imbalance * (0.18 + 0.82 * spinRatio * spinRatio), 0.0, IMBALANCE_VIBRATION_MAX);
	}

	public static double thrustScale(double rotorHealth) {
		return Math.pow(MathUtil.clamp(rotorHealth, 0.0, 1.0), 1.10);
	}

	public static double profileDragTorque(double rotorHealth, double spinRatio, double radiusScale, double diskDragScale) {
		double damage = damageAmount(rotorHealth);
		double spin = MathUtil.clamp(spinRatio, 0.0, 1.25);
		if (damage <= EPSILON || spin <= EPSILON) {
			return 0.0;
		}
		double profileArea = MathUtil.clamp(0.70 * Math.sqrt(radiusScale) + 0.30 * diskDragScale, 0.35, 4.0);
		double spinLoad = spin * spin * MathUtil.clamp(0.65 + 0.35 * spin, 0.65, 1.10);
		return DAMAGE_PROFILE_DRAG_TORQUE_COEFFICIENT * profileArea * Math.pow(damage, 1.35) * spinLoad;
	}

	private static double spinRatio(RotorSpec rotor, double omegaRadiansPerSecond, double maxClamp) {
		double maxOmega = Math.max(EPSILON, rotor.maxOmegaRadiansPerSecond());
		return MathUtil.clamp(Math.abs(omegaRadiansPerSecond) / maxOmega, 0.0, maxClamp);
	}

	private static double ratio(double numerator, double denominator) {
		return denominator == 0.0 ? 0.0 : numerator / denominator;
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double x = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return x * x * (3.0 - 2.0 * x);
	}
}
