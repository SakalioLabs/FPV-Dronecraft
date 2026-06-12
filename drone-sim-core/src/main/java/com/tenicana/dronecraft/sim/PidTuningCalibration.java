package com.tenicana.dronecraft.sim;

public final class PidTuningCalibration {
	public static final String APDRONE_PID_TUNING_SOURCE_ID = "APDrone-Mendeley-PID-Sweeps";
	public static final String APDRONE_PID_TUNING_NOTE =
			"APDrone sweep MAE units are relative gyro-vs-setpoint errors, not project torque PID units.";

	private static final double APDRONE_PITCH_P_ONLY_KP = 135.0;
	private static final double APDRONE_PITCH_P_ONLY_MAE = 31.29778899264627;
	private static final double APDRONE_PITCH_PI_KP = 135.0;
	private static final double APDRONE_PITCH_PI_KI = 155.0;
	private static final double APDRONE_PITCH_PI_MAE = 1.8382176024439676;
	private static final double APDRONE_PITCH_PID_KP = 135.0;
	private static final double APDRONE_PITCH_PID_KI = 155.0;
	private static final double APDRONE_PITCH_PID_KD = 24.0;
	private static final double APDRONE_PITCH_PID_MAE = 5.080034776512117;
	private static final double APDRONE_PITCH_CONFIG_KP = 135.0;
	private static final double APDRONE_PITCH_CONFIG_KI = 155.0;
	private static final double APDRONE_PITCH_CONFIG_KD = 90.0;
	private static final double APDRONE_PITCH_CONFIG_D_MIN = 24.0;

	private static final double APDRONE_ROLL_P_ONLY_KP = 65.0;
	private static final double APDRONE_ROLL_P_ONLY_MAE = 49.77701311740217;
	private static final double APDRONE_ROLL_PI_KP = 65.0;
	private static final double APDRONE_ROLL_PI_KI = 85.0;
	private static final double APDRONE_ROLL_PI_MAE = 2.5708982130254863;
	private static final double APDRONE_ROLL_PID_KP = 65.0;
	private static final double APDRONE_ROLL_PID_KI = 85.0;
	private static final double APDRONE_ROLL_PID_KD = 40.0;
	private static final double APDRONE_ROLL_PID_MAE = 13.015482284971233;
	private static final double APDRONE_ROLL_CONFIG_KP = 65.0;
	private static final double APDRONE_ROLL_CONFIG_KI = 85.0;
	private static final double APDRONE_ROLL_CONFIG_KD = 60.0;
	private static final double APDRONE_ROLL_CONFIG_D_MIN = 40.0;

	private static final double APDRONE_YAW_P_ONLY_KP = 135.0;
	private static final double APDRONE_YAW_P_ONLY_MAE = 111.2293700243075;
	private static final double APDRONE_YAW_PI_KP = 140.0;
	private static final double APDRONE_YAW_PI_KI = 100.0;
	private static final double APDRONE_YAW_PI_MAE = 2.3691877538406065;
	private static final double APDRONE_YAW_PID_KP = 140.0;
	private static final double APDRONE_YAW_PID_KI = 100.0;
	private static final double APDRONE_YAW_PID_KD = 50.0;
	private static final double APDRONE_YAW_PID_MAE = 7.007424499776081;
	private static final double APDRONE_YAW_CONFIG_KP = 140.0;
	private static final double APDRONE_YAW_CONFIG_KI = 100.0;
	private static final double APDRONE_YAW_CONFIG_KD = 90.0;
	private static final double APDRONE_YAW_CONFIG_D_MIN = 50.0;

	private PidTuningCalibration() {
	}

	public record AxisPidTuningAudit(
			String axis,
			double bestPOnlyKp,
			double bestPOnlyMae,
			double bestPiKp,
			double bestPiKi,
			double bestPiMae,
			double bestPidKp,
			double bestPidKi,
			double bestPidKd,
			double bestPidMae,
			double piMaeOverPOnlyMae,
			double piMaeReductionVsPOnly,
			double pidMaeOverPiMae,
			double pidMaeReductionVsPi,
			double betaflightConfigKp,
			double betaflightConfigKi,
			double betaflightConfigKd,
			double betaflightConfigDMin,
			boolean betaflightConfigMatchesBestKp,
			boolean betaflightConfigMatchesBestKi,
			boolean betaflightConfigMatchesBestKd,
			boolean betaflightConfigDMinMatchesBestKd,
			double betaflightConfigKdOverBestKd,
			double betaflightConfigDMinOverBestKd
	) {
	}

	public record ApDronePidTuningAudit(
			String sourceId,
			String note,
			AxisPidTuningAudit pitch,
			AxisPidTuningAudit roll,
			AxisPidTuningAudit yaw
	) {
	}

	public static ApDronePidTuningAudit apDronePidTuningAudit() {
		return new ApDronePidTuningAudit(
				APDRONE_PID_TUNING_SOURCE_ID,
				APDRONE_PID_TUNING_NOTE,
				axis(
						"pitch",
						APDRONE_PITCH_P_ONLY_KP,
						APDRONE_PITCH_P_ONLY_MAE,
						APDRONE_PITCH_PI_KP,
						APDRONE_PITCH_PI_KI,
						APDRONE_PITCH_PI_MAE,
						APDRONE_PITCH_PID_KP,
						APDRONE_PITCH_PID_KI,
						APDRONE_PITCH_PID_KD,
						APDRONE_PITCH_PID_MAE,
						APDRONE_PITCH_CONFIG_KP,
						APDRONE_PITCH_CONFIG_KI,
						APDRONE_PITCH_CONFIG_KD,
						APDRONE_PITCH_CONFIG_D_MIN
				),
				axis(
						"roll",
						APDRONE_ROLL_P_ONLY_KP,
						APDRONE_ROLL_P_ONLY_MAE,
						APDRONE_ROLL_PI_KP,
						APDRONE_ROLL_PI_KI,
						APDRONE_ROLL_PI_MAE,
						APDRONE_ROLL_PID_KP,
						APDRONE_ROLL_PID_KI,
						APDRONE_ROLL_PID_KD,
						APDRONE_ROLL_PID_MAE,
						APDRONE_ROLL_CONFIG_KP,
						APDRONE_ROLL_CONFIG_KI,
						APDRONE_ROLL_CONFIG_KD,
						APDRONE_ROLL_CONFIG_D_MIN
				),
				axis(
						"yaw",
						APDRONE_YAW_P_ONLY_KP,
						APDRONE_YAW_P_ONLY_MAE,
						APDRONE_YAW_PI_KP,
						APDRONE_YAW_PI_KI,
						APDRONE_YAW_PI_MAE,
						APDRONE_YAW_PID_KP,
						APDRONE_YAW_PID_KI,
						APDRONE_YAW_PID_KD,
						APDRONE_YAW_PID_MAE,
						APDRONE_YAW_CONFIG_KP,
						APDRONE_YAW_CONFIG_KI,
						APDRONE_YAW_CONFIG_KD,
						APDRONE_YAW_CONFIG_D_MIN
				)
		);
	}

	private static AxisPidTuningAudit axis(
			String axis,
			double pOnlyKp,
			double pOnlyMae,
			double piKp,
			double piKi,
			double piMae,
			double pidKp,
			double pidKi,
			double pidKd,
			double pidMae,
			double configKp,
			double configKi,
			double configKd,
			double configDMin
	) {
		return new AxisPidTuningAudit(
				axis,
				pOnlyKp,
				pOnlyMae,
				piKp,
				piKi,
				piMae,
				pidKp,
				pidKi,
				pidKd,
				pidMae,
				ratio(piMae, pOnlyMae),
				1.0 - ratio(piMae, pOnlyMae),
				ratio(pidMae, piMae),
				1.0 - ratio(pidMae, piMae),
				configKp,
				configKi,
				configKd,
				configDMin,
				nearlyEquals(configKp, pidKp),
				nearlyEquals(configKi, pidKi),
				nearlyEquals(configKd, pidKd),
				nearlyEquals(configDMin, pidKd),
				ratio(configKd, pidKd),
				ratio(configDMin, pidKd)
		);
	}

	private static boolean nearlyEquals(double a, double b) {
		return Math.abs(a - b) <= 1.0e-12;
	}

	private static double ratio(double numerator, double denominator) {
		return denominator == 0.0 ? 0.0 : numerator / denominator;
	}
}
