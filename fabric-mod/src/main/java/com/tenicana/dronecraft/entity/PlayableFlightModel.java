package com.tenicana.dronecraft.entity;

import com.tenicana.dronecraft.sim.FlightMode;

final class PlayableFlightModel {
	private static final FlightMode DEFAULT_PLAYABLE_MODE = FlightMode.DEFAULT_FIRST_FLIGHT;
	private static final float DESCENT_GAIN = 3.20f;
	private static final float THRUST_GAIN = 4.20f;
	private static final float THRUST_DEADZONE = 0.005f;
	private static final float THRUST_MIN_CLIMB = 0.025f;
	private static final float VERTICAL_ASCENT_SPEED_LIMIT = 9.0f;
	private static final float VERTICAL_DESCENT_SPEED_LIMIT = 18.0f;
	private static final float ZERO_THROTTLE_TERMINAL_DESCENT_METERS_PER_SECOND = 18.0f;
	private static final float VERTICAL_HOVER_EDGE_SOFTENING = 0.0075f;
	private static final float VERTICAL_HOVER_BRAKE_SMOOTHING = 0.72f;
	private static final float HOVER_BAND = 0.035f;
	private static final float PLAYABLE_AXIS_NOISE_EPSILON = 0.006f;
	private static final float VELOCITY_SETTLE_EPSILON_MPS = 0.018f;
	private static final float ATTITUDE_SETTLE_EPSILON_RADIANS = (float) Math.toRadians(0.20f);
	private static final float YAW_SETTLE_EPSILON_DEGREES_PER_TICK = 0.020f;
	private static final float IDLE_RPM = 2200.0f;
	private static final float HOVER_RPM = 6600.0f;
	private static final float MAX_RPM = 12300.0f;
	private static final float MODE_SWITCH_ANGLE_ATTITUDE_KEEP = 0.18f;
	private static final float MODE_SWITCH_HORIZON_ATTITUDE_KEEP = 0.24f;
	private static final float MODE_SWITCH_ACRO_ATTITUDE_KEEP = 1.0f;
	private static final float MODE_SWITCH_ANGLE_YAW_KEEP = 0.10f;
	private static final float MODE_SWITCH_HORIZON_YAW_KEEP = 0.18f;
	private static final float MODE_SWITCH_ACRO_YAW_KEEP = 0.45f;
	private static final float MODE_SWITCH_ANGLE_HORIZONTAL_KEEP = 0.45f;
	private static final float MODE_SWITCH_HORIZON_HORIZONTAL_KEEP = 0.62f;
	private static final float MODE_SWITCH_ACRO_HORIZONTAL_KEEP = 0.82f;
	private static final int MODE_SWITCH_SOFT_CAPTURE_TICKS = 6;
	private static final float MODE_SWITCH_ANGLE_HORIZONTAL_BRAKE = 0.34f;
	private static final float MODE_SWITCH_HORIZON_HORIZONTAL_BRAKE = 0.28f;
	private static final float MODE_SWITCH_ACRO_HORIZONTAL_BRAKE = 0.18f;
	private static final float MODE_SWITCH_ANGLE_YAW_BRAKE = 0.62f;
	private static final float MODE_SWITCH_HORIZON_YAW_BRAKE = 0.52f;
	private static final float MODE_SWITCH_ACRO_YAW_BRAKE = 0.42f;
	private static final float MAX_HIGH_THROTTLE_HORIZONTAL_BOOST = 0.20f;
	private static final float GROUND_ANGLE_HORIZONTAL_AUTHORITY_SCALE = 0.12f;
	private static final float GROUND_HORIZON_HORIZONTAL_AUTHORITY_SCALE = 0.30f;
	private static final float GROUND_ACRO_HORIZONTAL_AUTHORITY_SCALE = 0.45f;
	private static final float ANGLE_TILT_SINK_METERS_PER_SECOND = 1.15f;
	private static final float HORIZON_TILT_SINK_METERS_PER_SECOND = 1.85f;
	private static final float ACRO_TILT_SINK_METERS_PER_SECOND = 2.75f;
	private static final float INVERTED_THRUST_VERTICAL_PROJECTION_MIN = -0.45f;
	private static final float PLAYABLE_TICK_SECONDS = 0.05f;
	private static final float FULL_ROTATION_RADIANS = (float) (Math.PI * 2.0);
	private static final float ACRO_COMPLETED_ROTATION_MIN_RADIANS = (float) Math.toRadians(300.0f);
	private static final float ACRO_COMPLETED_ROTATION_SNAP_RADIANS = (float) Math.toRadians(40.0f);
	private static final float ACRO_COMPLETED_ROTATION_SNAP_MARGIN_RADIANS = (float) Math.toRadians(4.0f);
	private static final float ACRO_COMPLETED_ROTATION_RELEASE_COMMAND = 0.180f;
	private static final float ACRO_COMPLETED_ROTATION_FILTERED_RELEASE_COMMAND = 0.360f;
	private static final float ACRO_COMPLETED_ROTATION_FILTERED_RELEASE_MIN_RADIANS = (float) Math.toRadians(250.0f);
	private static final float ACRO_COMPLETED_ROTATION_FILTERED_RELEASE_RATE_DELTA_RADIANS = (float) Math.toRadians(0.80f);
	private static final float ACRO_COMPLETED_ROTATION_FILTERED_RELEASE_LOOKAHEAD_TICKS = 2.0f;
	private static final float ACRO_COMPLETED_ROTATION_RELEASE_SNAP_RADIANS = (float) Math.PI;
	private static final float ACRO_COMPLETED_ROTATION_DRIFT_TRIM_SPEED_METERS_PER_SECOND = 2.75f;
	private static final float ACRO_COMPLETED_ROLL_SIDE_SLIP_MAX_METERS_PER_SECOND = 0.28f;
	private static final int ACRO_COMPLETED_ROLL_RECOVERY_TICKS = 28;
	private static final float ACRO_COMPLETED_ROLL_RECOVERY_TAIL_COMMAND = 0.42f;
	private static final float ACRO_COMPLETED_ROLL_RECOVERY_SMOOTHING = 0.58f;
	private static final float ACRO_COMPLETED_ROLL_RECOVERY_SIDE_SLIP_MAX_METERS_PER_SECOND = 0.075f;
	private static final float ACRO_COMPLETED_ROLL_RECOVERY_ATTITUDE_RADIANS = (float) Math.toRadians(24.0f);
	private static final float ACRO_GRAVITY_METERS_PER_SECOND_SQUARED = 9.80665f;
	private static final float ACRO_REFERENCE_MASS_KILOGRAMS = 1.10f;
	private static final float ACRO_AIR_DENSITY_KILOGRAMS_PER_CUBIC_METER = 1.225f;
	private static final float ACRO_FULL_THROTTLE_THRUST_TO_WEIGHT = 3.35f;
	private static final float ACRO_ROTOR_COUNT = 4.0f;
	private static final float ACRO_FORWARD_DRAG_AREA_SQUARE_METERS = 0.0128f;
	private static final float ACRO_LATERAL_DRAG_AREA_SQUARE_METERS = 0.0340f;
	private static final float ACRO_VERTICAL_DRAG_AREA_SQUARE_METERS = 0.0180f;
	private static final float ACRO_FORWARD_LINEAR_DRAG_PER_SECOND = 0.060f;
	private static final float ACRO_LATERAL_LINEAR_DRAG_PER_SECOND = 0.14f;
	private static final float ACRO_VERTICAL_LINEAR_DRAG_PER_SECOND = 0.14f;
	private static final float ACRO_FORWARD_QUADRATIC_DRAG_PER_METER = bodyQuadraticDragPerMeter(ACRO_FORWARD_DRAG_AREA_SQUARE_METERS);
	private static final float ACRO_LATERAL_QUADRATIC_DRAG_PER_METER = bodyQuadraticDragPerMeter(ACRO_LATERAL_DRAG_AREA_SQUARE_METERS);
	private static final float ACRO_VERTICAL_QUADRATIC_DRAG_PER_METER = bodyQuadraticDragPerMeter(ACRO_VERTICAL_DRAG_AREA_SQUARE_METERS);
	private static final float ACRO_COUPLED_DYNAMIC_PRESSURE_SPEED_START_METERS_PER_SECOND = 6.0f;
	private static final float ACRO_COUPLED_DYNAMIC_PRESSURE_SPEED_FULL_METERS_PER_SECOND = 24.0f;
	private static final float ACRO_COUPLED_DYNAMIC_PRESSURE_CROSSFLOW_START_RADIANS = (float) Math.toRadians(8.0f);
	private static final float ACRO_COUPLED_DYNAMIC_PRESSURE_CROSSFLOW_FULL_RADIANS = (float) Math.toRadians(55.0f);
	private static final float ACRO_COUPLED_DYNAMIC_PRESSURE_EXTRA_GAIN = 0.55f;
	private static final float ACRO_COUPLED_DYNAMIC_PRESSURE_MAX_ACCELERATION = 2.20f;
	private static final float ACRO_SEPARATION_AOA_START_RADIANS = (float) Math.toRadians(30.0f);
	private static final float ACRO_SEPARATION_AOA_FULL_RADIANS = (float) Math.toRadians(66.0f);
	private static final float ACRO_SEPARATION_SIDESLIP_START_RADIANS = (float) Math.toRadians(32.0f);
	private static final float ACRO_SEPARATION_SIDESLIP_FULL_RADIANS = (float) Math.toRadians(68.0f);
	private static final float ACRO_PITCH_LIFT_AOA_STALL_START_RADIANS = (float) Math.toRadians(34.0f);
	private static final float ACRO_PITCH_LIFT_AOA_STALL_FULL_RADIANS = (float) Math.toRadians(72.0f);
	private static final float ACRO_PITCH_LIFT_GAIN = 0.090f;
	private static final float ACRO_SIDEFORCE_SIDESLIP_STALL_START_RADIANS = (float) Math.toRadians(35.0f);
	private static final float ACRO_SIDEFORCE_SIDESLIP_STALL_FULL_RADIANS = (float) Math.toRadians(75.0f);
	private static final float ACRO_SIDEFORCE_GAIN = 0.430f;
	private static final float ACRO_SIDEFORCE_INDUCED_DRAG_GAIN = 0.27f;
	private static final float ACRO_SIDEFORCE_INDUCED_DRAG_START_RADIANS = (float) Math.toRadians(10.0f);
	private static final float ACRO_SIDEFORCE_INDUCED_DRAG_FULL_RADIANS = (float) Math.toRadians(58.0f);
	private static final float ACRO_SIDEFORCE_INDUCED_DRAG_MAX_ACCELERATION = 1.85f;
	private static final float ACRO_AERO_CROSSFLOW_LAG_SPEED_START_METERS_PER_SECOND = 4.0f;
	private static final float ACRO_AERO_CROSSFLOW_LAG_SPEED_FULL_METERS_PER_SECOND = 14.0f;
	private static final float ACRO_AERO_CROSSFLOW_LAG_START_RADIANS = (float) Math.toRadians(8.0f);
	private static final float ACRO_AERO_CROSSFLOW_LAG_FULL_RADIANS = (float) Math.toRadians(55.0f);
	private static final float ACRO_AERO_CROSSFLOW_LAG_RISE_SMOOTHING = 0.34f;
	private static final float ACRO_AERO_CROSSFLOW_LAG_FALL_SMOOTHING = 0.16f;
	private static final float ACRO_SIDEWASH_MEMORY_SPEED_START_METERS_PER_SECOND = 6.0f;
	private static final float ACRO_SIDEWASH_MEMORY_SPEED_FULL_METERS_PER_SECOND = 18.0f;
	private static final float ACRO_SIDEWASH_MEMORY_SIDESLIP_START_RADIANS = (float) Math.toRadians(7.0f);
	private static final float ACRO_SIDEWASH_MEMORY_SIDESLIP_FULL_RADIANS = (float) Math.toRadians(52.0f);
	private static final float ACRO_SIDEWASH_MEMORY_RISE_SMOOTHING = 0.24f;
	private static final float ACRO_SIDEWASH_MEMORY_FALL_SMOOTHING = 0.14f;
	private static final float ACRO_SIDEWASH_FORCE_MIN_CROSSFLOW_RESPONSE = 0.40f;
	private static final float ACRO_WEATHERCOCK_SIDESLIP_START_RADIANS = (float) Math.toRadians(7.0f);
	private static final float ACRO_WEATHERCOCK_SIDESLIP_FULL_RADIANS = (float) Math.toRadians(48.0f);
	private static final float ACRO_WEATHERCOCK_FORWARD_START_METERS_PER_SECOND = 2.5f;
	private static final float ACRO_WEATHERCOCK_FORWARD_FULL_METERS_PER_SECOND = 16.0f;
	private static final float ACRO_WEATHERCOCK_LATERAL_START_METERS_PER_SECOND = 1.5f;
	private static final float ACRO_WEATHERCOCK_LATERAL_FULL_METERS_PER_SECOND = 12.0f;
	private static final float ACRO_WEATHERCOCK_BROADSIDE_BASE = 0.50f;
	private static final float ACRO_WEATHERCOCK_BROADSIDE_LATERAL_WEIGHT = 0.35f;
	private static final float ACRO_WEATHERCOCK_YAW_GAIN_DEGREES_PER_TICK = 0.085f;
	private static final float ACRO_WEATHERCOCK_YAW_MAX_DEGREES_PER_TICK = 0.72f;
	private static final float ACRO_SIDESLIP_YAW_DAMPING_GAIN = 0.022f;
	private static final float ACRO_SIDESLIP_YAW_DAMPING_MAX = 0.13f;
	private static final float ACRO_WEATHERCOCK_YAW_COMMAND_SUPPRESS = 0.45f;
	private static final float ACRO_SIDESLIP_YAW_LOAD_SPEED_START_METERS_PER_SECOND = 10.0f;
	private static final float ACRO_SIDESLIP_YAW_LOAD_SPEED_FULL_METERS_PER_SECOND = 28.0f;
	private static final float ACRO_SIDESLIP_YAW_LOAD_SIDESLIP_START_RADIANS = (float) Math.toRadians(12.0f);
	private static final float ACRO_SIDESLIP_YAW_LOAD_SIDESLIP_FULL_RADIANS = (float) Math.toRadians(55.0f);
	private static final float ACRO_SIDESLIP_YAW_LOAD_MAX_ACTIVE_LOSS = 0.10f;
	private static final float ACRO_SIDESLIP_YAW_LOAD_FULL_COMMAND = 0.30f;
	private static final float ACRO_YAW_TURN_LOAD_SPEED_START_METERS_PER_SECOND = 8.0f;
	private static final float ACRO_YAW_TURN_LOAD_SPEED_FULL_METERS_PER_SECOND = 24.0f;
	private static final float ACRO_YAW_TURN_LOAD_RATE_START_DEGREES_PER_SECOND = 30.0f;
	private static final float ACRO_YAW_TURN_LOAD_RATE_FULL_DEGREES_PER_SECOND = 120.0f;
	private static final float ACRO_YAW_TURN_LOAD_ACCELERATION_GAIN = 0.045f;
	private static final float ACRO_YAW_TURN_LOAD_MAX_ACCELERATION = 1.65f;
	private static final float ACRO_YAW_TURN_LOAD_SIDESLIP_START_RADIANS = (float) Math.toRadians(10.0f);
	private static final float ACRO_YAW_TURN_LOAD_SIDESLIP_FULL_RADIANS = (float) Math.toRadians(55.0f);
	private static final float ACRO_BODY_RATE_LOAD_SPEED_START_METERS_PER_SECOND = 8.0f;
	private static final float ACRO_BODY_RATE_LOAD_SPEED_FULL_METERS_PER_SECOND = 28.0f;
	private static final float ACRO_BODY_RATE_LOAD_RATE_START_RADIANS_PER_SECOND = (float) Math.toRadians(70.0f);
	private static final float ACRO_BODY_RATE_LOAD_RATE_FULL_RADIANS_PER_SECOND = (float) Math.toRadians(360.0f);
	private static final float ACRO_BODY_RATE_LOAD_CROSSFLOW_START_RADIANS = (float) Math.toRadians(10.0f);
	private static final float ACRO_BODY_RATE_LOAD_CROSSFLOW_FULL_RADIANS = (float) Math.toRadians(58.0f);
	private static final float ACRO_BODY_RATE_LOAD_STRAIGHT_FLOW_WEIGHT = 0.28f;
	private static final float ACRO_BODY_RATE_LOAD_YAW_WEIGHT = 0.35f;
	private static final float ACRO_BODY_RATE_LOAD_ACCELERATION_GAIN = 0.024f;
	private static final float ACRO_BODY_RATE_LOAD_MAX_ACCELERATION = 2.20f;
	private static final float ACRO_THRUST_TURN_LOAD_SPEED_START_METERS_PER_SECOND = 8.0f;
	private static final float ACRO_THRUST_TURN_LOAD_SPEED_FULL_METERS_PER_SECOND = 24.0f;
	private static final float ACRO_THRUST_TURN_LOAD_ACCELERATION_START = 1.20f;
	private static final float ACRO_THRUST_TURN_LOAD_ACCELERATION_FULL = 8.0f;
	private static final float ACRO_THRUST_TURN_LOAD_GAIN = 0.16f;
	private static final float ACRO_THRUST_TURN_LOAD_MAX_ACCELERATION = 1.65f;
	private static final float ACRO_ROTOR_SIDEWASH_TURN_SPEED_START_METERS_PER_SECOND = 10.0f;
	private static final float ACRO_ROTOR_SIDEWASH_TURN_SPEED_FULL_METERS_PER_SECOND = 28.0f;
	private static final float ACRO_ROTOR_SIDEWASH_TURN_ACCELERATION_START = 1.0f;
	private static final float ACRO_ROTOR_SIDEWASH_TURN_ACCELERATION_FULL = 8.0f;
	private static final float ACRO_ROTOR_SIDEWASH_TURN_CROSSFLOW_START_RADIANS = (float) Math.toRadians(10.0f);
	private static final float ACRO_ROTOR_SIDEWASH_TURN_CROSSFLOW_FULL_RADIANS = (float) Math.toRadians(58.0f);
	private static final float ACRO_ROTOR_SIDEWASH_TURN_STRAIGHT_FLOW_WEIGHT = 0.24f;
	private static final float ACRO_ROTOR_SIDEWASH_TURN_GAIN = 0.095f;
	private static final float ACRO_ROTOR_SIDEWASH_TURN_MAX_ACCELERATION = 0.90f;
	private static final float ACRO_BODY_RATE_YAW_COUPLING_SCALE = 0.47f;
	private static final float ACRO_BODY_RATE_VERTICAL_ROLL_YAW_WEIGHT = 1.15f;
	private static final float ACRO_BODY_RATE_VERTICAL_ROLL_START_RADIANS = (float) Math.toRadians(35.0f);
	private static final float ACRO_BODY_RATE_VERTICAL_ROLL_FULL_RADIANS = (float) Math.toRadians(78.0f);
	private static final float ACRO_BODY_RATE_YAW_COUPLING_MAX_DEGREES_PER_TICK = 2.35f;
	private static final float ACRO_BODY_RATE_YAW_COMMAND_SUPPRESS = 0.42f;
	private static final float ACRO_BODY_RATE_VERTICAL_TWIST_YAW_START_RADIANS = (float) Math.toRadians(60.0f);
	private static final float ACRO_BODY_RATE_VERTICAL_TWIST_YAW_FULL_RADIANS = (float) Math.toRadians(88.0f);
	private static final float ACRO_AERO_RATE_DAMPING_START_METERS_PER_SECOND = 9.0f;
	private static final float ACRO_AERO_RATE_DAMPING_FULL_METERS_PER_SECOND = 28.0f;
	private static final float ACRO_PITCH_AERO_RATE_DAMPING_MAX = 0.135f;
	private static final float ACRO_ROLL_AERO_RATE_DAMPING_MAX = 0.105f;
	private static final float ACRO_AERO_RATE_CROSSFLOW_START_RADIANS = (float) Math.toRadians(12.0f);
	private static final float ACRO_AERO_RATE_CROSSFLOW_FULL_RADIANS = (float) Math.toRadians(58.0f);
	private static final float ACRO_RATE_INERTIA_SPEED_START_METERS_PER_SECOND = 10.0f;
	private static final float ACRO_RATE_INERTIA_SPEED_FULL_METERS_PER_SECOND = 28.0f;
	private static final float ACRO_RATE_INERTIA_CROSSFLOW_START_RADIANS = (float) Math.toRadians(10.0f);
	private static final float ACRO_RATE_INERTIA_CROSSFLOW_FULL_RADIANS = (float) Math.toRadians(55.0f);
	private static final float ACRO_RATE_INERTIA_STRAIGHT_FLOW_WEIGHT = 0.28f;
	private static final float ACRO_PITCH_RATE_INERTIA_MAX_SMOOTHING_LOSS = 0.16f;
	private static final float ACRO_ROLL_RATE_INERTIA_MAX_SMOOTHING_LOSS = 0.22f;
	private static final float ACRO_YAW_RATE_INERTIA_IDLE_KEEP = 0.90f;
	private static final float ACRO_YAW_RATE_INERTIA_SPEED_START_METERS_PER_SECOND = 8.0f;
	private static final float ACRO_YAW_RATE_INERTIA_SPEED_FULL_METERS_PER_SECOND = 28.0f;
	private static final float ACRO_YAW_RATE_INERTIA_CROSSFLOW_START_RADIANS = (float) Math.toRadians(10.0f);
	private static final float ACRO_YAW_RATE_INERTIA_CROSSFLOW_FULL_RADIANS = (float) Math.toRadians(55.0f);
	private static final float ACRO_YAW_RATE_INERTIA_STRAIGHT_FLOW_WEIGHT = 0.24f;
	private static final float ACRO_YAW_RATE_INERTIA_MAX_SMOOTHING_LOSS = 0.10f;
	private static final float ACRO_RESIDUAL_TORQUE_LOAD_SPEED_START_METERS_PER_SECOND = 10.0f;
	private static final float ACRO_RESIDUAL_TORQUE_LOAD_SPEED_FULL_METERS_PER_SECOND = 28.0f;
	private static final float ACRO_RESIDUAL_TORQUE_LOAD_RATE_START_RADIANS_PER_SECOND = (float) Math.toRadians(50.0f);
	private static final float ACRO_RESIDUAL_TORQUE_LOAD_RATE_FULL_RADIANS_PER_SECOND = (float) Math.toRadians(260.0f);
	private static final float ACRO_RESIDUAL_TORQUE_LOAD_CROSSFLOW_START_RADIANS = (float) Math.toRadians(12.0f);
	private static final float ACRO_RESIDUAL_TORQUE_LOAD_CROSSFLOW_FULL_RADIANS = (float) Math.toRadians(58.0f);
	private static final float ACRO_RESIDUAL_TORQUE_LOAD_MAX_RATE_LOSS = 0.055f;
	private static final float ACRO_ROTOR_GYRO_RATE_START_RADIANS_PER_SECOND = (float) Math.toRadians(70.0f);
	private static final float ACRO_ROTOR_GYRO_RATE_FULL_RADIANS_PER_SECOND = (float) Math.toRadians(220.0f);
	private static final float ACRO_ROTOR_GYRO_RPM_START_FRACTION = 0.22f;
	private static final float ACRO_ROTOR_GYRO_SINGLE_AXIS_WEIGHT = 0.18f;
	private static final float ACRO_ROTOR_GYRO_DIAGONAL_RATE_LOAD_MAX = 0.085f;
	private static final float ACRO_TRANSVERSE_MOMENT_SPEED_START_METERS_PER_SECOND = 8.0f;
	private static final float ACRO_TRANSVERSE_MOMENT_SPEED_FULL_METERS_PER_SECOND = 24.0f;
	private static final float ACRO_TRANSVERSE_MOMENT_MU_START = 0.055f;
	private static final float ACRO_TRANSVERSE_MOMENT_MU_FULL = 0.220f;
	private static final float ACRO_TRANSVERSE_MOMENT_MU_FADE_START = 0.300f;
	private static final float ACRO_TRANSVERSE_MOMENT_MU_FADE_FULL = 0.700f;
	private static final float ACRO_TRANSVERSE_MOMENT_HIGH_MU_KEEP = 0.45f;
	private static final float ACRO_TRANSVERSE_MOMENT_SIDESLIP_START_RADIANS = (float) Math.toRadians(14.0f);
	private static final float ACRO_TRANSVERSE_MOMENT_SIDESLIP_FULL_RADIANS = (float) Math.toRadians(62.0f);
	private static final float ACRO_TRANSVERSE_ROLL_MOMENT_MAX_RATE_RADIANS_PER_TICK = (float) Math.toRadians(0.74f);
	private static final float ACRO_TRANSVERSE_AIRFRAME_ROLL_MOMENT_MAX_RATE_RADIANS_PER_TICK = (float) Math.toRadians(0.19f);
	private static final float ACRO_TRANSVERSE_ROLL_COMMAND_SUPPRESS = 0.65f;
	private static final float ACRO_TRANSVERSE_ROLL_ACTIVE_KEEP = 0.08f;
	private static final float ACRO_TRANSVERSE_PASSIVE_RATE_HOLD_START_RADIANS_PER_TICK = (float) Math.toRadians(0.07f);
	private static final float ACRO_TRANSVERSE_PASSIVE_RATE_HOLD_FULL_RADIANS_PER_TICK = (float) Math.toRadians(0.34f);
	private static final float ACRO_TRANSVERSE_PASSIVE_RATE_HOLD_KEEP = 0.12f;
	private static final float ACRO_AOA_MOMENT_SPEED_START_METERS_PER_SECOND = 8.0f;
	private static final float ACRO_AOA_MOMENT_SPEED_FULL_METERS_PER_SECOND = 24.0f;
	private static final float ACRO_AOA_MOMENT_FORWARD_START_METERS_PER_SECOND = 2.5f;
	private static final float ACRO_AOA_MOMENT_START_RADIANS = (float) Math.toRadians(10.0f);
	private static final float ACRO_AOA_MOMENT_FULL_RADIANS = (float) Math.toRadians(58.0f);
	private static final float ACRO_AOA_PITCH_MOMENT_MAX_RATE_RADIANS_PER_TICK = (float) Math.toRadians(0.30f);
	private static final float ACRO_AOA_PITCH_COMMAND_SUPPRESS = 0.65f;
	private static final float ACRO_AOA_PITCH_ACTIVE_KEEP = 0.08f;
	private static final float ACRO_AOA_PITCH_PASSIVE_KEEP = 0.36f;
	private static final float ACRO_AOA_PITCH_ACTIVITY_START_RADIANS_PER_TICK = (float) Math.toRadians(0.35f);
	private static final float ACRO_AOA_PITCH_ACTIVITY_FULL_RADIANS_PER_TICK = (float) Math.toRadians(1.10f);
	private static final float ACRO_THRUST_RISE_SMOOTHING = 0.55f;
	private static final float ACRO_THRUST_FALL_SMOOTHING = 0.68f;
	private static final float ACRO_THRUST_SETTLE_EPSILON = 0.004f;
	private static final float ACRO_PROP_DIAMETER_METERS = 0.127f;
	private static final float ACRO_ADVANCE_REFERENCE_MIN_RPM = 13000.0f;
	private static final float ACRO_ADVANCE_LOSS_START_J = 0.18f;
	private static final float ACRO_ADVANCE_LOSS_FULL_J = 0.62f;
	private static final float ACRO_ADVANCE_MAX_THRUST_LOSS = 0.48f;
	private static final float ACRO_ADVANCE_SIDEFLOW_MAX_THRUST_LOSS = 0.62f;
	private static final float ACRO_ADVANCE_SIDEFLOW_START_RADIANS = (float) Math.toRadians(12.0f);
	private static final float ACRO_ADVANCE_SIDEFLOW_FULL_RADIANS = (float) Math.toRadians(48.0f);
	private static final float ACRO_ADVANCE_AXIAL_FLOW_WEIGHT = 0.18f;
	private static final float ACRO_TRANSLATIONAL_LIFT_MU_START = 0.030f;
	private static final float ACRO_TRANSLATIONAL_LIFT_MU_FULL = 0.085f;
	private static final float ACRO_TRANSLATIONAL_LIFT_FADE_START_MU = 0.135f;
	private static final float ACRO_TRANSLATIONAL_LIFT_FADE_FULL_MU = 0.360f;
	private static final float ACRO_TRANSLATIONAL_LIFT_MAX_THRUST_GAIN = 0.055f;
	private static final float ACRO_TRANSLATIONAL_LIFT_SIDEFLOW_KEEP = 0.18f;
	private static final float ACRO_TRANSLATIONAL_LIFT_DRAG_GAIN = 0.32f;
	private static final float ACRO_TRANSLATIONAL_LIFT_DRAG_MAX_ACCELERATION = 0.75f;
	private static final float ACRO_ROTOR_RADIUS_METERS = ACRO_PROP_DIAMETER_METERS * 0.5f;
	private static final float ACRO_ROTOR_REFERENCE_MAX_RPM = 29137.0f;
	private static final float ACRO_ROTOR_DISK_DRAG_COEFFICIENT = 0.0028f;
	private static final float ACRO_ROTOR_FLAPPING_COEFFICIENT = 0.075f;
	private static final float ACRO_ROTOR_FLAPPING_FULL_MU = 0.095f;
	private static final float ACRO_ROTOR_FLAPPING_MAX_TILT_RADIANS = (float) Math.toRadians(18.0f);
	private static final float ACRO_ROTOR_FLAPPING_STRAIGHT_FLOW_WEIGHT = 0.28f;
	private static final float ACRO_ROTOR_FLAPPING_SIDESLIP_START_RADIANS = (float) Math.toRadians(10.0f);
	private static final float ACRO_ROTOR_FLAPPING_SIDESLIP_FULL_RADIANS = (float) Math.toRadians(48.0f);
	private static final float ACRO_ROTOR_IN_PLANE_MAX_ACCELERATION = 5.0f;
	private static final float ACRO_ROTOR_IN_PLANE_STRAIGHT_FLOW_WEIGHT = 0.10f;
	private static final float ACRO_ROTOR_IN_PLANE_SIDESLIP_START_RADIANS = (float) Math.toRadians(12.0f);
	private static final float ACRO_ROTOR_IN_PLANE_SIDESLIP_FULL_RADIANS = (float) Math.toRadians(52.0f);
	private static final float ACRO_DYNAMIC_INFLOW_SPEED_START_METERS_PER_SECOND = 8.0f;
	private static final float ACRO_DYNAMIC_INFLOW_SPEED_FULL_METERS_PER_SECOND = 24.0f;
	private static final float ACRO_DYNAMIC_INFLOW_RATE_START_RADIANS_PER_SECOND = (float) Math.toRadians(80.0f);
	private static final float ACRO_DYNAMIC_INFLOW_RATE_FULL_RADIANS_PER_SECOND = (float) Math.toRadians(300.0f);
	private static final float ACRO_DYNAMIC_INFLOW_CROSSFLOW_START_RADIANS = (float) Math.toRadians(10.0f);
	private static final float ACRO_DYNAMIC_INFLOW_CROSSFLOW_FULL_RADIANS = (float) Math.toRadians(55.0f);
	private static final float ACRO_DYNAMIC_INFLOW_STRAIGHT_FLOW_WEIGHT = 0.35f;
	private static final float ACRO_DYNAMIC_INFLOW_RPM_IDLE_WEIGHT = 0.40f;
	private static final float ACRO_DYNAMIC_INFLOW_MAX_THRUST_LOSS = 0.075f;
	private static final float ACRO_OVERSPEED_SOFT_START_SCALE = 0.96f;
	private static final float ACRO_OVERSPEED_LINEAR_DAMPING_PER_SECOND = 1.60f;
	private static final float ACRO_OVERSPEED_QUADRATIC_DAMPING_PER_METER = 0.12f;
	private static final float ACRO_OVERSPEED_HARD_LIMIT_SCALE = 1.45f;
	private static final float ACRO_RATE_RISE_SMOOTHING = 0.62f;
	private static final float ACRO_RATE_FALL_SMOOTHING = 0.74f;
	private static final float ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK = (float) Math.toRadians(0.025f);
	private static final float ACRO_RATE_AUTHORITY_IDLE_KEEP = 0.72f;
	private static final float ACRO_RATE_AUTHORITY_CROSSFLOW_SPEED_START_METERS_PER_SECOND = 10.0f;
	private static final float ACRO_RATE_AUTHORITY_CROSSFLOW_SPEED_FULL_METERS_PER_SECOND = 28.0f;
	private static final float ACRO_RATE_AUTHORITY_CROSSFLOW_START_RADIANS = (float) Math.toRadians(12.0f);
	private static final float ACRO_RATE_AUTHORITY_CROSSFLOW_FULL_RADIANS = (float) Math.toRadians(58.0f);
	private static final float ACRO_RATE_AUTHORITY_MAX_CROSSFLOW_LOSS = 0.10f;

	private PlayableFlightModel() {
	}

	static Step step(
			FlightMode mode,
			float throttle,
			float pitch,
			float roll,
			float yaw,
			float hoverThrottle,
			boolean nearGroundLocked,
			State previous
	) {
		return step(mode, throttle, pitch, roll, yaw, hoverThrottle, nearGroundLocked, 1.0f, previous);
	}

	static Step step(
			FlightMode mode,
			float throttle,
			float pitch,
			float roll,
			float yaw,
			float hoverThrottle,
			boolean nearGroundLocked,
			float lowAltitudeHorizontalAuthorityScale,
			State previous
	) {
		FlightMode safeMode = safeMode(mode);
		Profile profile = Profile.forMode(safeMode);
		float safeThrottle = clamp(throttle, 0.0f, 1.0f);
		float safePitch = clamp(pitch, -1.0f, 1.0f);
		float safeRoll = clamp(roll, -1.0f, 1.0f);
		float safeYaw = clamp(yaw, -1.0f, 1.0f);
		float safeHover = clamp(hoverThrottle, 0.12f, 0.55f);
		float safeLowAltitudeHorizontalScale = clamp(lowAltitudeHorizontalAuthorityScale, 0.0f, 1.0f);
		State safePrevious = previousStateForMode(safeMode, profile, previous);

		float attitudeCommandAuthority = lowAltitudeAttitudeCommandAuthority(safeMode, nearGroundLocked, safeLowAltitudeHorizontalScale);
		float yawCommandAuthority = lowAltitudeYawCommandAuthority(safeMode, nearGroundLocked, safeLowAltitudeHorizontalScale);
		float attitudePitch = safePitch * attitudeCommandAuthority;
		float attitudeRoll = safeRoll * attitudeCommandAuthority;
		float yawCommand = safeYaw * yawCommandAuthority;

		float acroRateCrossflowLag = acroAeroCrossflowLag(
				safeMode,
				safePrevious.acroAeroCrossflowLag(),
				safePrevious.velocityX(),
				safePrevious.velocityY(),
				safePrevious.velocityZ(),
				safePrevious.pitchRadians(),
				safePrevious.rollRadians()
		);
		float acroRateSidewashMemory = acroSidewashMemory(
				safeMode,
				safePrevious.acroSidewashMemory(),
				safePrevious.velocityX(),
				safePrevious.velocityY(),
				safePrevious.velocityZ(),
				safePrevious.pitchRadians(),
				safePrevious.rollRadians()
		);
		AcroRateResponse acroRate = acroRateResponse(safeMode, safePrevious, attitudePitch, attitudeRoll, safeThrottle, safeHover, acroRateCrossflowLag, acroRateSidewashMemory, profile);
		Attitude attitude = attitude(safeMode, profile, attitudePitch, attitudeRoll, safePrevious, acroRate);
		float pitchRadians = completedAcroRotationAttitude(
				safeMode,
				attitudePitch,
				attitude.pitchRadians(),
				profile.maxPitchRadians(),
				profile.pitchRateRadiansPerTick(),
				safePrevious.acroPitchRateRadiansPerTick()
		);
		float rollRadians = completedAcroRotationAttitude(
				safeMode,
				attitudeRoll,
				attitude.rollRadians(),
				profile.maxRollRadians(),
				profile.rollRateRadiansPerTick(),
				safePrevious.acroRollRateRadiansPerTick()
		);
		boolean acroPitchCaptured = completedAcroAttitudeWasCaptured(safeMode, attitude.pitchRadians(), pitchRadians);
		boolean acroRollCaptured = completedAcroAttitudeWasCaptured(safeMode, attitude.rollRadians(), rollRadians);
		float acroPitchRateRadiansPerTick = acroPitchCaptured
				? 0.0f
				: acroRate.bodyPitchRateRadiansPerTick();
		float acroRollRateRadiansPerTick = acroRollCaptured
				? 0.0f
				: acroRate.bodyRollRateRadiansPerTick();
		pitchRadians = settledAttitude(safeMode, attitudePitch, pitchRadians);
		rollRadians = settledAttitude(safeMode, attitudeRoll, rollRadians);
		int acroRollRecoveryTicksRemaining = nextAcroRollRecoveryTicksRemaining(
				safeMode,
				attitudeRoll,
				rollRadians,
				acroRollCaptured,
				safePrevious.acroRollRecoveryTicksRemaining()
		);
		boolean acroRollRecoveryActive = shouldApplyCompletedAcroRollRecovery(
				safeMode,
				attitudeRoll,
				rollRadians,
				acroRollCaptured,
				acroRollRecoveryTicksRemaining
		);
		if (acroRollRecoveryActive) {
			rollRadians = 0.0f;
			acroRollRateRadiansPerTick = 0.0f;
		}
		float acroAeroCrossflowLag = acroAeroCrossflowLag(
				safeMode,
				safePrevious.acroAeroCrossflowLag(),
				safePrevious.velocityX(),
				safePrevious.velocityY(),
				safePrevious.velocityZ(),
				pitchRadians,
				rollRadians
		);
		float acroSidewashMemory = acroSidewashMemory(
				safeMode,
				safePrevious.acroSidewashMemory(),
				safePrevious.velocityX(),
				safePrevious.velocityY(),
				safePrevious.velocityZ(),
				pitchRadians,
				rollRadians
		);
		float throttleAuthority = horizontalThrottleAuthority(safeMode, safeThrottle, safeHover, nearGroundLocked, safeLowAltitudeHorizontalScale, profile);
		Velocity horizontalTarget = horizontalTargetVelocity(safeMode, pitchRadians, rollRadians, throttleAuthority, profile);
		float targetVelocityX = horizontalTarget.x();
		float targetVelocityZ = horizontalTarget.z();
		Velocity limitedHorizontalTarget = limitHorizontalVector(
				targetVelocityX,
				0.0f,
				targetVelocityZ,
				horizontalTargetSpeedLimit(profile, throttleAuthority)
		);
		targetVelocityX = limitedHorizontalTarget.x();
		targetVelocityZ = limitedHorizontalTarget.z();
		float targetVelocityY = attitudeAdjustedVerticalVelocity(
				safeMode,
				safeThrottle,
				safeHover,
				pitchRadians,
				rollRadians,
				verticalVelocity(safeThrottle, safeHover, profile),
				profile
		);
		if (nearGroundLocked && targetVelocityY < 0.0f) {
			targetVelocityY = 0.0f;
		}
		if (safeThrottle > safeHover + profile.hoverBand() + VERTICAL_HOVER_EDGE_SOFTENING
				&& targetVelocityY > 0.0f
				&& targetVelocityY < THRUST_MIN_CLIMB) {
			targetVelocityY = THRUST_MIN_CLIMB;
		}
		float targetCollectiveThrustToWeight = acroCollectiveThrustToWeight(safeThrottle, safeHover);
		float collectiveThrustToWeight = acroResponsiveCollectiveThrustToWeight(
				safeMode,
				safePrevious.acroCollectiveThrustToWeight(),
				targetCollectiveThrustToWeight
		);
		float targetYawDegreesPerTick = yawCommand * profile.yawDegreesPerTick();
		float predictedYawDegreesPerTick = yawRateStep(
				safeMode,
				safePrevious,
				safeYaw,
				yawCommand,
				targetYawDegreesPerTick,
				safePrevious.velocityX(),
				safePrevious.velocityY(),
				safePrevious.velocityZ(),
				pitchRadians,
				rollRadians,
				safeThrottle,
				safeHover,
				acroAeroCrossflowLag,
				acroSidewashMemory,
				acroPitchRateRadiansPerTick,
				acroRollRateRadiansPerTick,
				profile
		);
		float acroEulerPitchRateRadiansPerTick = safeMode == FlightMode.ACRO
				? pitchRadians - safePrevious.pitchRadians()
				: 0.0f;
		float acroEulerRollRateRadiansPerTick = safeMode == FlightMode.ACRO
				? rollRadians - safePrevious.rollRadians()
				: 0.0f;

		Velocity velocity = velocityStep(
				safeMode,
				safePrevious.velocityX(),
				safePrevious.velocityY(),
				safePrevious.velocityZ(),
				targetVelocityX,
				targetVelocityY,
				targetVelocityZ,
				safeThrottle,
				safeHover,
				collectiveThrustToWeight,
				pitchRadians,
				rollRadians,
				predictedYawDegreesPerTick,
				acroEulerPitchRateRadiansPerTick,
				acroEulerRollRateRadiansPerTick,
				acroPitchRateRadiansPerTick,
				acroRollRateRadiansPerTick,
				acroAeroCrossflowLag,
				acroSidewashMemory,
				profile
		);
		float velocityX = velocity.x();
		float velocityY = velocity.y();
		float velocityZ = velocity.z();
		if (safeMode == FlightMode.ACRO && (acroPitchCaptured || acroRollCaptured || acroRollRecoveryActive)) {
			Velocity trimmed = completedAcroRotationVelocityTrim(
					velocityX,
					velocityY,
					velocityZ,
					pitchRadians,
					rollRadians,
					acroPitchCaptured,
					acroRollCaptured,
					acroRollRecoveryActive
			);
			velocityX = trimmed.x();
			velocityY = trimmed.y();
			velocityZ = trimmed.z();
			acroAeroCrossflowLag = acroAeroCrossflowLag(
					safeMode,
					safePrevious.acroAeroCrossflowLag(),
					velocityX,
					velocityY,
					velocityZ,
					pitchRadians,
					rollRadians
			);
			acroSidewashMemory = acroSidewashMemory(
					safeMode,
					safePrevious.acroSidewashMemory(),
					velocityX,
					velocityY,
					velocityZ,
					pitchRadians,
					rollRadians
			);
		}
		Velocity limitedHorizontalVelocity = limitHorizontalVector(
				velocityX,
				0.0f,
				velocityZ,
				horizontalVelocityHardLimit(safeMode, profile)
		);
		velocityX = limitedHorizontalVelocity.x();
		velocityY = clamp(velocityY, -VERTICAL_DESCENT_SPEED_LIMIT, VERTICAL_ASCENT_SPEED_LIMIT);
		velocityZ = limitedHorizontalVelocity.z();
		velocityX = settledVelocity(velocityX, targetVelocityX);
		velocityY = settledVelocity(velocityY, targetVelocityY);
		velocityZ = settledVelocity(velocityZ, targetVelocityZ);
		if (nearGroundLocked && velocityY < 0.0f) {
			velocityY = 0.0f;
		}
		if (nearGroundLocked && shouldGroundCatchVertical(safeThrottle, safeHover, targetVelocityY, profile)) {
			velocityY = 0.0f;
		}
		if (nearGroundLocked && shouldGroundDamp(safeThrottle, safeHover, targetVelocityX, targetVelocityZ, profile)) {
			velocityX = smooth(velocityX, 0.0f, profile.groundFrictionSmoothing());
			velocityZ = smooth(velocityZ, 0.0f, profile.groundFrictionSmoothing());
		}
		if (shouldAirBrake(safeMode, safeThrottle, safeHover, safePitch, safeRoll, profile)) {
			velocityX = smooth(velocityX, 0.0f, profile.airBrakeSmoothing());
			velocityZ = smooth(velocityZ, 0.0f, profile.airBrakeSmoothing());
		}
		if (shouldModeSwitchBrakeHorizontal(safePrevious, safePitch, safeRoll)) {
			float brake = modeSwitchHorizontalBrake(safeMode);
			velocityX = smooth(velocityX, 0.0f, brake);
			velocityZ = smooth(velocityZ, 0.0f, brake);
		}
		velocityX = settledVelocity(velocityX, targetVelocityX);
		velocityY = settledVelocity(velocityY, targetVelocityY);
		velocityZ = settledVelocity(velocityZ, targetVelocityZ);

		float yawDegreesPerTick = yawRateStep(
				safeMode,
				safePrevious,
				safeYaw,
				yawCommand,
				targetYawDegreesPerTick,
				velocityX,
				velocityY,
				velocityZ,
				pitchRadians,
				rollRadians,
				safeThrottle,
				safeHover,
				acroAeroCrossflowLag,
				acroSidewashMemory,
				acroPitchRateRadiansPerTick,
				acroRollRateRadiansPerTick,
				profile
		);
		float motorPower = safeThrottle <= THRUST_DEADZONE ? 0.14f : clamp(0.14f + safeThrottle * 0.86f, 0.0f, 1.0f);
		float averageRpm = averageRpm(safeThrottle, safeHover);
		return new Step(
				targetVelocityX,
				targetVelocityY,
				targetVelocityZ,
				velocityX,
				velocityY,
				velocityZ,
				pitchRadians,
				rollRadians,
				yawDegreesPerTick,
				motorPower,
				averageRpm,
				collectiveThrustToWeight,
				acroPitchRateRadiansPerTick,
				acroRollRateRadiansPerTick,
				safeMode,
				nextModeSwitchTicksRemaining(safePrevious),
				acroRollRecoveryTicksRemaining,
				acroAeroCrossflowLag,
				acroSidewashMemory
		);
	}

	static float playableAxisCommand(float value) {
		if (!Float.isFinite(value)) {
			return 0.0f;
		}
		float clamped = clamp(value, -1.0f, 1.0f);
		return Math.abs(clamped) <= PLAYABLE_AXIS_NOISE_EPSILON ? 0.0f : clamped;
	}

	static Velocity worldVelocityForYaw(float localX, float localY, float localZ, float yawDegrees) {
		float yawRadians = (float) Math.toRadians(yawDegrees);
		float cos = (float) Math.cos(yawRadians);
		float sin = (float) Math.sin(yawRadians);
		return new Velocity(
				localX * cos - localZ * sin,
				localY,
				localX * sin + localZ * cos
		);
	}

	static Velocity localVelocityForYaw(float worldX, float worldY, float worldZ, float yawDegrees) {
		float yawRadians = (float) Math.toRadians(yawDegrees);
		float cos = (float) Math.cos(yawRadians);
		float sin = (float) Math.sin(yawRadians);
		return new Velocity(
				worldX * cos + worldZ * sin,
				worldY,
				-worldX * sin + worldZ * cos
		);
	}

	static Velocity reframeVelocityForYaw(float localX, float localY, float localZ, float fromYawDegrees, float toYawDegrees) {
		Velocity world = worldVelocityForYaw(localX, localY, localZ, fromYawDegrees);
		return localVelocityForYaw(world.x(), world.y(), world.z(), toYawDegrees);
	}

	private static State previousStateForMode(FlightMode mode, Profile profile, State previous) {
		if (previous == null) {
			return State.zero(mode);
		}
		if (previous.mode() == mode) {
			return previous;
		}
		return new State(
				previous.velocityX() * modeSwitchHorizontalKeep(mode),
				previous.velocityY(),
				previous.velocityZ() * modeSwitchHorizontalKeep(mode),
				modeSwitchCapturedAttitude(mode, previous.pitchRadians() * modeSwitchAttitudeKeep(mode), profile.maxPitchRadians()),
				modeSwitchCapturedAttitude(mode, previous.rollRadians() * modeSwitchAttitudeKeep(mode), profile.maxRollRadians()),
				previous.yawDegreesPerTick() * modeSwitchYawKeep(mode),
				mode,
				MODE_SWITCH_SOFT_CAPTURE_TICKS,
				previous.mode() == FlightMode.ACRO ? previous.acroCollectiveThrustToWeight() : 0.0f,
				previous.mode() == FlightMode.ACRO ? previous.acroPitchRateRadiansPerTick() * modeSwitchYawKeep(mode) : 0.0f,
				previous.mode() == FlightMode.ACRO ? previous.acroRollRateRadiansPerTick() * modeSwitchYawKeep(mode) : 0.0f
		);
	}

	private static float modeSwitchCapturedAttitude(FlightMode mode, float attitudeRadians, float assistedLimitRadians) {
		if (!Float.isFinite(attitudeRadians)) {
			return 0.0f;
		}
		if (mode == FlightMode.ACRO) {
			return attitudeRadians;
		}
		return clamp(attitudeRadians, -assistedLimitRadians, assistedLimitRadians);
	}

	private static float modeSwitchAttitudeKeep(FlightMode mode) {
		return switch (mode) {
			case ANGLE -> MODE_SWITCH_ANGLE_ATTITUDE_KEEP;
			case HORIZON -> MODE_SWITCH_HORIZON_ATTITUDE_KEEP;
			case ACRO -> MODE_SWITCH_ACRO_ATTITUDE_KEEP;
		};
	}

	private static float modeSwitchYawKeep(FlightMode mode) {
		return switch (mode) {
			case ANGLE -> MODE_SWITCH_ANGLE_YAW_KEEP;
			case HORIZON -> MODE_SWITCH_HORIZON_YAW_KEEP;
			case ACRO -> MODE_SWITCH_ACRO_YAW_KEEP;
		};
	}

	private static float modeSwitchHorizontalKeep(FlightMode mode) {
		return switch (mode) {
			case ANGLE -> MODE_SWITCH_ANGLE_HORIZONTAL_KEEP;
			case HORIZON -> MODE_SWITCH_HORIZON_HORIZONTAL_KEEP;
			case ACRO -> MODE_SWITCH_ACRO_HORIZONTAL_KEEP;
		};
	}

	private static int nextModeSwitchTicksRemaining(State previous) {
		return Math.max(0, previous.modeSwitchTicksRemaining() - 1);
	}

	private static int nextAcroRollRecoveryTicksRemaining(
			FlightMode mode,
			float rollCommand,
			float rollRadians,
			boolean rollCaptured,
			int previousTicksRemaining
	) {
		if (safeMode(mode) != FlightMode.ACRO) {
			return 0;
		}
		if (rollCaptured) {
			return ACRO_COMPLETED_ROLL_RECOVERY_TICKS;
		}
		if (previousTicksRemaining <= 0
				|| Math.abs(rollCommand) > completedRollRecoveryCommandLimit(previousTicksRemaining)
				|| Math.abs(signedRotationResidualRadians(rollRadians)) > ACRO_COMPLETED_ROLL_RECOVERY_ATTITUDE_RADIANS) {
			return 0;
		}
		return previousTicksRemaining - 1;
	}

	private static boolean shouldApplyCompletedAcroRollRecovery(
			FlightMode mode,
			float rollCommand,
			float rollRadians,
			boolean rollCaptured,
			int ticksRemaining
	) {
		return safeMode(mode) == FlightMode.ACRO
				&& !rollCaptured
				&& ticksRemaining > 0
				&& Math.abs(rollCommand) <= completedRollRecoveryCommandLimit(ticksRemaining)
				&& Math.abs(signedRotationResidualRadians(rollRadians)) <= ACRO_COMPLETED_ROLL_RECOVERY_ATTITUDE_RADIANS;
	}

	private static float completedRollRecoveryCommandLimit(int ticksRemaining) {
		float recoveryProgress = clamp(ticksRemaining / (float) ACRO_COMPLETED_ROLL_RECOVERY_TICKS, 0.0f, 1.0f);
		return lerp(
				ACRO_COMPLETED_ROTATION_RELEASE_COMMAND,
				ACRO_COMPLETED_ROLL_RECOVERY_TAIL_COMMAND,
				recoveryProgress
		);
	}

	private static boolean shouldModeSwitchBrakeHorizontal(State previous, float pitch, float roll) {
		return previous.modeSwitchTicksRemaining() > 0
				&& Math.abs(pitch) <= PLAYABLE_AXIS_NOISE_EPSILON
				&& Math.abs(roll) <= PLAYABLE_AXIS_NOISE_EPSILON;
	}

	private static boolean shouldModeSwitchBrakeYaw(State previous, float yaw) {
		return previous.modeSwitchTicksRemaining() > 0
				&& Math.abs(yaw) <= PLAYABLE_AXIS_NOISE_EPSILON;
	}

	private static float modeSwitchHorizontalBrake(FlightMode mode) {
		return switch (safeMode(mode)) {
			case ANGLE -> MODE_SWITCH_ANGLE_HORIZONTAL_BRAKE;
			case HORIZON -> MODE_SWITCH_HORIZON_HORIZONTAL_BRAKE;
			case ACRO -> MODE_SWITCH_ACRO_HORIZONTAL_BRAKE;
		};
	}

	private static float modeSwitchYawBrake(FlightMode mode) {
		return switch (safeMode(mode)) {
			case ANGLE -> MODE_SWITCH_ANGLE_YAW_BRAKE;
			case HORIZON -> MODE_SWITCH_HORIZON_YAW_BRAKE;
			case ACRO -> MODE_SWITCH_ACRO_YAW_BRAKE;
		};
	}

	private static Attitude attitude(FlightMode mode, Profile profile, float pitch, float roll, State previous, AcroRateResponse acroRate) {
		FlightMode safeMode = safeMode(mode);
		return switch (safeMode) {
			case ANGLE -> angleAttitude(profile, pitch, roll, previous);
			case HORIZON -> horizonAttitude(profile, pitch, roll, previous);
			case ACRO -> new Attitude(
					previous.pitchRadians() + acroRate.eulerPitchRateRadiansPerTick(),
					previous.rollRadians() + acroRate.eulerRollRateRadiansPerTick()
			);
		};
	}

	private static Attitude angleAttitude(Profile profile, float pitch, float roll, State previous) {
		return new Attitude(
				smoothLimited(
						previous.pitchRadians(),
						pitch * profile.maxPitchRadians(),
						attitudeSmoothing(pitch, profile),
						attitudeStepLimitRadians(pitch, profile)
				),
				smoothLimited(
						previous.rollRadians(),
						roll * profile.maxRollRadians(),
						attitudeSmoothing(roll, profile),
						attitudeStepLimitRadians(roll, profile)
				)
		);
	}

	private static Attitude horizonAttitude(Profile profile, float pitch, float roll, State previous) {
		float pitchBlend = smoothStep((Math.abs(pitch) - 0.62f) / 0.33f);
		float rollBlend = smoothStep((Math.abs(roll) - 0.62f) / 0.33f);
		float anglePitch = smoothLimited(
				previous.pitchRadians(),
				pitch * profile.maxPitchRadians(),
				attitudeSmoothing(pitch, profile),
				attitudeStepLimitRadians(pitch, profile)
		);
		float angleRoll = smoothLimited(
				previous.rollRadians(),
				roll * profile.maxRollRadians(),
				attitudeSmoothing(roll, profile),
				attitudeStepLimitRadians(roll, profile)
		);
		float ratePitch = heldRateAttitude(previous.pitchRadians(), pitch, profile.pitchRateRadiansPerTick(), profile.acroHoldDamping(), profile.maxAcroPitchRadians());
		float rateRoll = heldRateAttitude(previous.rollRadians(), roll, profile.rollRateRadiansPerTick(), profile.acroHoldDamping(), profile.maxAcroRollRadians());
		return new Attitude(
				lerp(anglePitch, ratePitch, pitchBlend),
				lerp(angleRoll, rateRoll, rollBlend)
		);
	}

	private static float attitudeSmoothing(float command, Profile profile) {
		return Math.abs(command) < 0.035f ? profile.attitudeRecenterSmoothing() : profile.attitudeSmoothing();
	}

	private static float attitudeStepLimitRadians(float command, Profile profile) {
		return Math.abs(command) < 0.035f ? profile.attitudeRecenterStepLimitRadians() : profile.attitudeStepLimitRadians();
	}

	private static float heldRateAttitude(float previousRadians, float command, float rateRadiansPerTick, float centerDamping, float limitRadians) {
		float updated = previousRadians + command * rateRadiansPerTick;
		if (Math.abs(command) < 0.035f) {
			updated *= centerDamping;
		}
		return clamp(updated, -limitRadians, limitRadians);
	}

	private static AcroRateResponse acroRateResponse(
			FlightMode mode,
			State previous,
			float pitch,
			float roll,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag,
			float acroSidewashMemory,
			Profile profile
	) {
		if (safeMode(mode) != FlightMode.ACRO) {
			return AcroRateResponse.ZERO;
		}
		boolean completedRollRecoveryTail = isCompletedAcroRollRecoveryTail(mode, previous, roll);
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(
				previous.velocityX(),
				previous.velocityY(),
				previous.velocityZ(),
				previous.pitchRadians(),
				previous.rollRadians()
		);
		float motorRateAuthority = acroMotorRateAuthorityScale(bodyVelocity, throttle, hoverThrottle, acroAeroCrossflowLag, acroSidewashMemory);
		float bodyPitchRate = acroAerodynamicRateDamped(
				responsiveAcroRate(previous.acroPitchRateRadiansPerTick(), pitch * profile.pitchRateRadiansPerTick() * motorRateAuthority, bodyVelocity, true, acroAeroCrossflowLag, acroSidewashMemory),
				bodyVelocity,
				true,
				acroAeroCrossflowLag,
				acroSidewashMemory
		);
		float bodyRollRate = acroAerodynamicRateDamped(
				responsiveAcroRate(previous.acroRollRateRadiansPerTick(), roll * profile.rollRateRadiansPerTick() * motorRateAuthority, bodyVelocity, false, acroAeroCrossflowLag, acroSidewashMemory),
				bodyVelocity,
				false,
				acroAeroCrossflowLag,
				acroSidewashMemory
		);
		if (completedRollRecoveryTail) {
			bodyRollRate = 0.0f;
		} else {
			float transverseRollMoment = acroTransverseFlowRollMomentRate(bodyVelocity, roll, throttle, hoverThrottle)
					* acroSidewashForceResponse(acroAeroCrossflowLag, acroSidewashMemory);
			bodyRollRate += acroPassiveRateHoldLimitedTransverseRollMoment(
					transverseRollMoment,
					previous.acroRollRateRadiansPerTick(),
					roll
			);
		}
		bodyPitchRate += acroAngleOfAttackPitchMomentRate(bodyVelocity, pitch)
				* acroAngleOfAttackPitchMomentScale(
						bodyVelocity,
						previous.pitchRadians(),
						previous.acroPitchRateRadiansPerTick(),
						pitch
				)
				* sanitizedCrossflowLag(acroAeroCrossflowLag);
		float residualTorqueLoad = acroResidualTorqueRateLoadFraction(bodyVelocity, bodyPitchRate, bodyRollRate, acroAeroCrossflowLag, acroSidewashMemory);
		bodyPitchRate *= 1.0f - residualTorqueLoad;
		bodyRollRate *= 1.0f - residualTorqueLoad;
		float rotorGyroLoad = acroRotorGyroRateLoadFraction(bodyPitchRate, bodyRollRate, throttle, hoverThrottle);
		bodyPitchRate *= 1.0f - rotorGyroLoad;
		bodyRollRate *= 1.0f - rotorGyroLoad;
		bodyPitchRate = clamp(bodyPitchRate, -profile.pitchRateRadiansPerTick(), profile.pitchRateRadiansPerTick());
		bodyRollRate = clamp(bodyRollRate, -profile.rollRateRadiansPerTick(), profile.rollRateRadiansPerTick());
		AcroBodyRateAttitudeDelta attitudeDelta = acroBodyRateAttitudeDelta(
				previous.pitchRadians(),
				previous.rollRadians(),
				bodyPitchRate,
				bodyRollRate
		);
		return new AcroRateResponse(
				bodyPitchRate,
				bodyRollRate,
				attitudeDelta.pitchRateRadiansPerTick(),
				attitudeDelta.rollRateRadiansPerTick()
		);
	}

	static AcroBodyRateAttitudeDelta acroBodyRateAttitudeDelta(
			float pitchRadians,
			float rollRadians,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick
	) {
		float bodyPitchRate = finiteOrZero(pitchRateRadiansPerTick);
		float bodyRollRate = finiteOrZero(rollRateRadiansPerTick);
		if (Math.abs(bodyPitchRate) <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK
				&& Math.abs(bodyRollRate) <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK) {
			return AcroBodyRateAttitudeDelta.ZERO;
		}
		float previousPitch = Float.isFinite(pitchRadians) ? pitchRadians : 0.0f;
		float previousRoll = Float.isFinite(rollRadians) ? rollRadians : 0.0f;
		AcroBodyFrame frame = acroBodyFrame(previousPitch, previousRoll);
		Velocity angularAxis = new Velocity(
				frame.right().x() * bodyPitchRate + frame.forward().x() * bodyRollRate,
				frame.right().y() * bodyPitchRate + frame.forward().y() * bodyRollRate,
				frame.right().z() * bodyPitchRate + frame.forward().z() * bodyRollRate
		);
		float angularStep = (float) Math.sqrt(angularAxis.x() * angularAxis.x()
				+ angularAxis.y() * angularAxis.y()
				+ angularAxis.z() * angularAxis.z());
		if (angularStep <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK) {
			return AcroBodyRateAttitudeDelta.ZERO;
		}
		Velocity unitAxis = scaleVelocity(angularAxis, 1.0f / angularStep);
		Velocity rotatedRight = rotateAroundAxis(frame.right(), unitAxis, angularStep);
		Velocity rotatedUp = rotateAroundAxis(frame.up(), unitAxis, angularStep);
		Velocity rotatedForward = rotateAroundAxis(frame.forward(), unitAxis, angularStep);
		float forwardYawRadians = (float) Math.atan2(-rotatedForward.x(), rotatedForward.z());
		float twistYawRadians = bodyTwistYawRadians(rotatedRight, previousPitch, previousRoll);
		float verticalExposure = smoothStep((Math.abs(signedRotationResidualRadians(previousPitch)) - ACRO_BODY_RATE_VERTICAL_TWIST_YAW_START_RADIANS)
				/ Math.max(0.001f, ACRO_BODY_RATE_VERTICAL_TWIST_YAW_FULL_RADIANS - ACRO_BODY_RATE_VERTICAL_TWIST_YAW_START_RADIANS));
		float yawRadians = forwardYawRadians + signedRotationResidualRadians(twistYawRadians - forwardYawRadians) * verticalExposure;
		float yawDegrees = (float) Math.toDegrees(yawRadians);
		Velocity localRight = localVelocityForYaw(rotatedRight.x(), rotatedRight.y(), rotatedRight.z(), yawDegrees);
		Velocity localUp = localVelocityForYaw(rotatedUp.x(), rotatedUp.y(), rotatedUp.z(), yawDegrees);
		Velocity localForward = localVelocityForYaw(rotatedForward.x(), rotatedForward.y(), rotatedForward.z(), yawDegrees);
		float pitchResidual = (float) Math.atan2(-localForward.y(), localForward.z());
		float rollResidual = (float) Math.atan2(-localUp.x(), localRight.x());
		float nextPitch = continuousAttitudeFromResidual(previousPitch, pitchResidual);
		float nextRoll = continuousAttitudeFromResidual(previousRoll, rollResidual);
		return new AcroBodyRateAttitudeDelta(
				nextPitch - previousPitch,
				nextRoll - previousRoll
		);
	}

	private static float bodyTwistYawRadians(Velocity rotatedRight, float previousPitchRadians, float previousRollRadians) {
		float previousPitch = Float.isFinite(previousPitchRadians) ? previousPitchRadians : 0.0f;
		float previousRoll = Float.isFinite(previousRollRadians) ? previousRollRadians : 0.0f;
		float expectedRightX = (float) Math.cos(previousRoll);
		float expectedRightZ = (float) Math.sin(previousPitch) * (float) Math.sin(previousRoll);
		float actualRightX = rotatedRight.x();
		float actualRightZ = rotatedRight.z();
		float cross = actualRightZ * expectedRightX - actualRightX * expectedRightZ;
		float dot = actualRightX * expectedRightX + actualRightZ * expectedRightZ;
		if (Math.abs(cross) <= 1.0e-6f && Math.abs(dot) <= 1.0e-6f) {
			return 0.0f;
		}
		return (float) Math.atan2(cross, dot);
	}

	private static boolean isCompletedAcroRollRecoveryTail(FlightMode mode, State previous, float rollCommand) {
		return safeMode(mode) == FlightMode.ACRO
				&& previous.acroRollRecoveryTicksRemaining() > 0
				&& Math.abs(rollCommand) <= completedRollRecoveryCommandLimit(previous.acroRollRecoveryTicksRemaining())
				&& Math.abs(signedRotationResidualRadians(previous.rollRadians())) <= ACRO_COMPLETED_ROLL_RECOVERY_ATTITUDE_RADIANS;
	}

	static float acroAerodynamicRateDamped(float rateRadiansPerTick, Velocity bodyVelocity, boolean pitchAxis) {
		return acroAerodynamicRateDamped(rateRadiansPerTick, bodyVelocity, pitchAxis, 1.0f, 1.0f);
	}

	static float acroAerodynamicRateDamped(
			float rateRadiansPerTick,
			Velocity bodyVelocity,
			boolean pitchAxis,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		if (Math.abs(rateRadiansPerTick) <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK) {
			return rateRadiansPerTick;
		}
		float damping = acroAerodynamicRateDamping(bodyVelocity, pitchAxis, acroAeroCrossflowLag, acroSidewashMemory);
		if (damping <= 1.0e-6f) {
			return rateRadiansPerTick;
		}
		return rateRadiansPerTick * (1.0f - damping);
	}

	static float acroAerodynamicRateDamping(Velocity bodyVelocity, boolean pitchAxis) {
		return acroAerodynamicRateDamping(bodyVelocity, pitchAxis, 1.0f, 1.0f);
	}

	static float acroAerodynamicRateDamping(
			Velocity bodyVelocity,
			boolean pitchAxis,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float speedSquared = bodyVelocity.x() * bodyVelocity.x()
				+ bodyVelocity.y() * bodyVelocity.y()
				+ bodyVelocity.z() * bodyVelocity.z();
		if (speedSquared <= 1.0e-6f) {
			return 0.0f;
		}
		float speed = (float) Math.sqrt(speedSquared);
		float speedExposure = smoothStep((speed - ACRO_AERO_RATE_DAMPING_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_AERO_RATE_DAMPING_FULL_METERS_PER_SECOND - ACRO_AERO_RATE_DAMPING_START_METERS_PER_SECOND));
		if (speedExposure <= 1.0e-6f) {
			return 0.0f;
		}
		float forwardReference = Math.max(2.0f, Math.abs(bodyVelocity.z()));
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), forwardReference);
		float angleOfAttack = (float) Math.atan2(Math.abs(bodyVelocity.y()), forwardReference);
		float sideslipExposure = smoothStep((sideslip - ACRO_AERO_RATE_CROSSFLOW_START_RADIANS)
				/ Math.max(0.001f, ACRO_AERO_RATE_CROSSFLOW_FULL_RADIANS - ACRO_AERO_RATE_CROSSFLOW_START_RADIANS));
		float angleOfAttackExposure = smoothStep((angleOfAttack - ACRO_AERO_RATE_CROSSFLOW_START_RADIANS)
				/ Math.max(0.001f, ACRO_AERO_RATE_CROSSFLOW_FULL_RADIANS - ACRO_AERO_RATE_CROSSFLOW_START_RADIANS));
		float lag = sanitizedCrossflowLag(acroAeroCrossflowLag);
		float yawCrossflow = acroYawSidewashExposure(sideslipExposure, lag, acroSidewashMemory);
		float pitchCrossflow = acroPitchLagExposure(angleOfAttackExposure, lag);
		float crossflowExposure = pitchAxis
				? Math.max(pitchCrossflow, yawCrossflow * 0.35f)
				: Math.max(yawCrossflow, pitchCrossflow * 0.35f);
		float axisMax = pitchAxis ? ACRO_PITCH_AERO_RATE_DAMPING_MAX : ACRO_ROLL_AERO_RATE_DAMPING_MAX;
		return axisMax * speedExposure * (0.55f + 0.45f * crossflowExposure);
	}

	private static float responsiveAcroRate(float previousRateRadiansPerTick, float targetRateRadiansPerTick, Velocity bodyVelocity, boolean pitchAxis) {
		return responsiveAcroRate(previousRateRadiansPerTick, targetRateRadiansPerTick, bodyVelocity, pitchAxis, 1.0f, 1.0f);
	}

	private static float responsiveAcroRate(
			float previousRateRadiansPerTick,
			float targetRateRadiansPerTick,
			Velocity bodyVelocity,
			boolean pitchAxis,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		if (!Float.isFinite(previousRateRadiansPerTick)) {
			return targetRateRadiansPerTick;
		}
		float smoothing = isRateRising(previousRateRadiansPerTick, targetRateRadiansPerTick)
				? ACRO_RATE_RISE_SMOOTHING
				: ACRO_RATE_FALL_SMOOTHING;
		smoothing *= acroRateInertiaSmoothingScale(bodyVelocity, pitchAxis, acroAeroCrossflowLag, acroSidewashMemory);
		float responsive = smooth(previousRateRadiansPerTick, targetRateRadiansPerTick, smoothing);
		if (Math.abs(targetRateRadiansPerTick) <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK
				&& Math.abs(responsive) <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK) {
			return 0.0f;
		}
		if (Math.abs(responsive - targetRateRadiansPerTick) <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK) {
			return targetRateRadiansPerTick;
		}
		return responsive;
	}

	static float acroRateInertiaSmoothingScale(Velocity bodyVelocity, boolean pitchAxis) {
		return acroRateInertiaSmoothingScale(bodyVelocity, pitchAxis, 1.0f, 1.0f);
	}

	static float acroRateInertiaSmoothingScale(
			Velocity bodyVelocity,
			boolean pitchAxis,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float speedSquared = bodyVelocity.x() * bodyVelocity.x()
				+ bodyVelocity.y() * bodyVelocity.y()
				+ bodyVelocity.z() * bodyVelocity.z();
		if (speedSquared <= 1.0e-6f) {
			return 1.0f;
		}
		float speed = (float) Math.sqrt(speedSquared);
		float speedExposure = smoothStep((speed - ACRO_RATE_INERTIA_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_RATE_INERTIA_SPEED_FULL_METERS_PER_SECOND - ACRO_RATE_INERTIA_SPEED_START_METERS_PER_SECOND));
		if (speedExposure <= 1.0e-6f) {
			return 1.0f;
		}
		float forwardReference = Math.max(2.0f, Math.abs(bodyVelocity.z()));
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), forwardReference);
		float angleOfAttack = (float) Math.atan2(Math.abs(bodyVelocity.y()), forwardReference);
		float sideslipExposure = smoothStep((sideslip - ACRO_RATE_INERTIA_CROSSFLOW_START_RADIANS)
				/ Math.max(0.001f, ACRO_RATE_INERTIA_CROSSFLOW_FULL_RADIANS - ACRO_RATE_INERTIA_CROSSFLOW_START_RADIANS));
		float angleOfAttackExposure = smoothStep((angleOfAttack - ACRO_RATE_INERTIA_CROSSFLOW_START_RADIANS)
				/ Math.max(0.001f, ACRO_RATE_INERTIA_CROSSFLOW_FULL_RADIANS - ACRO_RATE_INERTIA_CROSSFLOW_START_RADIANS));
		float lag = sanitizedCrossflowLag(acroAeroCrossflowLag);
		float yawCrossflow = acroYawSidewashExposure(sideslipExposure, lag, acroSidewashMemory);
		float pitchCrossflow = acroPitchLagExposure(angleOfAttackExposure, lag);
		float crossflowExposure = pitchAxis
				? Math.max(pitchCrossflow, yawCrossflow * 0.45f)
				: Math.max(yawCrossflow, pitchCrossflow * 0.45f);
		float flowLoad = ACRO_RATE_INERTIA_STRAIGHT_FLOW_WEIGHT
				+ (1.0f - ACRO_RATE_INERTIA_STRAIGHT_FLOW_WEIGHT) * crossflowExposure;
		float maxLoss = pitchAxis
				? ACRO_PITCH_RATE_INERTIA_MAX_SMOOTHING_LOSS
				: ACRO_ROLL_RATE_INERTIA_MAX_SMOOTHING_LOSS;
		return clamp(1.0f - maxLoss * speedExposure * flowLoad, 0.70f, 1.0f);
	}

	static float acroYawRateInertiaSmoothingScale(
			Velocity bodyVelocity,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag
	) {
		return acroYawRateInertiaSmoothingScale(bodyVelocity, throttle, hoverThrottle, acroAeroCrossflowLag, acroAeroCrossflowLag);
	}

	static float acroYawRateInertiaSmoothingScale(
			Velocity bodyVelocity,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float rpm = averageRpm(throttle, hoverThrottle);
		float rpmProgress = smoothStep((rpm - IDLE_RPM) / Math.max(1.0f, HOVER_RPM - IDLE_RPM));
		float rpmScale = lerp(ACRO_YAW_RATE_INERTIA_IDLE_KEEP, 1.0f, rpmProgress);
		float speedSquared = bodyVelocity.x() * bodyVelocity.x()
				+ bodyVelocity.y() * bodyVelocity.y()
				+ bodyVelocity.z() * bodyVelocity.z();
		if (speedSquared <= 1.0e-6f) {
			return rpmScale;
		}
		float speed = (float) Math.sqrt(speedSquared);
		float speedExposure = smoothStep((speed - ACRO_YAW_RATE_INERTIA_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_YAW_RATE_INERTIA_SPEED_FULL_METERS_PER_SECOND - ACRO_YAW_RATE_INERTIA_SPEED_START_METERS_PER_SECOND));
		if (speedExposure <= 1.0e-6f) {
			return rpmScale;
		}
		float forwardReference = Math.max(2.0f, Math.abs(bodyVelocity.z()));
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), forwardReference);
		float angleOfAttack = (float) Math.atan2(Math.abs(bodyVelocity.y()), forwardReference);
		float lag = sanitizedCrossflowLag(acroAeroCrossflowLag);
		float crossflowExposure = Math.max(
				acroYawSidewashExposure(
						smoothStep((sideslip - ACRO_YAW_RATE_INERTIA_CROSSFLOW_START_RADIANS)
								/ Math.max(0.001f, ACRO_YAW_RATE_INERTIA_CROSSFLOW_FULL_RADIANS - ACRO_YAW_RATE_INERTIA_CROSSFLOW_START_RADIANS)),
						lag,
						acroSidewashMemory
				),
				acroPitchLagExposure(
						smoothStep((angleOfAttack - ACRO_YAW_RATE_INERTIA_CROSSFLOW_START_RADIANS)
								/ Math.max(0.001f, ACRO_YAW_RATE_INERTIA_CROSSFLOW_FULL_RADIANS - ACRO_YAW_RATE_INERTIA_CROSSFLOW_START_RADIANS)),
						lag
				)
		);
		float flowLoad = ACRO_YAW_RATE_INERTIA_STRAIGHT_FLOW_WEIGHT
				+ (1.0f - ACRO_YAW_RATE_INERTIA_STRAIGHT_FLOW_WEIGHT) * crossflowExposure;
		float aerodynamicScale = 1.0f - ACRO_YAW_RATE_INERTIA_MAX_SMOOTHING_LOSS * speedExposure * flowLoad;
		return clamp(rpmScale * aerodynamicScale, ACRO_YAW_RATE_INERTIA_IDLE_KEEP * (1.0f - ACRO_YAW_RATE_INERTIA_MAX_SMOOTHING_LOSS), 1.0f);
	}

	static float acroMotorRateAuthorityScale(
			Velocity bodyVelocity,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag
	) {
		return acroMotorRateAuthorityScale(bodyVelocity, throttle, hoverThrottle, acroAeroCrossflowLag, acroAeroCrossflowLag);
	}

	static float acroMotorRateAuthorityScale(
			Velocity bodyVelocity,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float rpm = averageRpm(throttle, hoverThrottle);
		float rpmProgress = smoothStep((rpm - IDLE_RPM) / Math.max(1.0f, HOVER_RPM - IDLE_RPM));
		float rpmAuthority = lerp(ACRO_RATE_AUTHORITY_IDLE_KEEP, 1.0f, rpmProgress);
		float speedSquared = bodyVelocity.x() * bodyVelocity.x()
				+ bodyVelocity.y() * bodyVelocity.y()
				+ bodyVelocity.z() * bodyVelocity.z();
		if (speedSquared <= 1.0e-6f) {
			return rpmAuthority;
		}
		float speed = (float) Math.sqrt(speedSquared);
		float speedExposure = smoothStep((speed - ACRO_RATE_AUTHORITY_CROSSFLOW_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_RATE_AUTHORITY_CROSSFLOW_SPEED_FULL_METERS_PER_SECOND - ACRO_RATE_AUTHORITY_CROSSFLOW_SPEED_START_METERS_PER_SECOND));
		if (speedExposure <= 1.0e-6f) {
			return rpmAuthority;
		}
		float forwardReference = Math.max(2.0f, Math.abs(bodyVelocity.z()));
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), forwardReference);
		float angleOfAttack = (float) Math.atan2(Math.abs(bodyVelocity.y()), forwardReference);
		float lag = sanitizedCrossflowLag(acroAeroCrossflowLag);
		float crossflowExposure = Math.max(
				acroYawSidewashExposure(
						smoothStep((sideslip - ACRO_RATE_AUTHORITY_CROSSFLOW_START_RADIANS)
								/ Math.max(0.001f, ACRO_RATE_AUTHORITY_CROSSFLOW_FULL_RADIANS - ACRO_RATE_AUTHORITY_CROSSFLOW_START_RADIANS)),
						lag,
						acroSidewashMemory
				),
				acroPitchLagExposure(
						smoothStep((angleOfAttack - ACRO_RATE_AUTHORITY_CROSSFLOW_START_RADIANS)
								/ Math.max(0.001f, ACRO_RATE_AUTHORITY_CROSSFLOW_FULL_RADIANS - ACRO_RATE_AUTHORITY_CROSSFLOW_START_RADIANS)),
						lag
				)
		);
		float crossflowAuthority = 1.0f - ACRO_RATE_AUTHORITY_MAX_CROSSFLOW_LOSS * speedExposure * crossflowExposure;
		return clamp(rpmAuthority * crossflowAuthority, ACRO_RATE_AUTHORITY_IDLE_KEEP * (1.0f - ACRO_RATE_AUTHORITY_MAX_CROSSFLOW_LOSS), 1.0f);
	}

	static float acroResidualTorqueRateLoadFraction(
			Velocity bodyVelocity,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick
	) {
		return acroResidualTorqueRateLoadFraction(bodyVelocity, pitchRateRadiansPerTick, rollRateRadiansPerTick, 1.0f);
	}

	static float acroResidualTorqueRateLoadFraction(
			Velocity bodyVelocity,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick,
			float acroAeroCrossflowLag
	) {
		return acroResidualTorqueRateLoadFraction(bodyVelocity, pitchRateRadiansPerTick, rollRateRadiansPerTick, acroAeroCrossflowLag, acroAeroCrossflowLag);
	}

	static float acroResidualTorqueRateLoadFraction(
			Velocity bodyVelocity,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float speedSquared = bodyVelocity.x() * bodyVelocity.x()
				+ bodyVelocity.y() * bodyVelocity.y()
				+ bodyVelocity.z() * bodyVelocity.z();
		if (speedSquared <= 1.0e-6f) {
			return 0.0f;
		}
		float speed = (float) Math.sqrt(speedSquared);
		float speedExposure = smoothStep((speed - ACRO_RESIDUAL_TORQUE_LOAD_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_RESIDUAL_TORQUE_LOAD_SPEED_FULL_METERS_PER_SECOND - ACRO_RESIDUAL_TORQUE_LOAD_SPEED_START_METERS_PER_SECOND));
		if (speedExposure <= 1.0e-6f) {
			return 0.0f;
		}

		float pitchRateRadiansPerSecond = finiteOrZero(pitchRateRadiansPerTick) / PLAYABLE_TICK_SECONDS;
		float rollRateRadiansPerSecond = finiteOrZero(rollRateRadiansPerTick) / PLAYABLE_TICK_SECONDS;
		float rateMagnitude = horizontalMagnitude(pitchRateRadiansPerSecond, rollRateRadiansPerSecond);
		float rateExposure = smoothStep((rateMagnitude - ACRO_RESIDUAL_TORQUE_LOAD_RATE_START_RADIANS_PER_SECOND)
				/ Math.max(0.001f, ACRO_RESIDUAL_TORQUE_LOAD_RATE_FULL_RADIANS_PER_SECOND - ACRO_RESIDUAL_TORQUE_LOAD_RATE_START_RADIANS_PER_SECOND));
		if (rateExposure <= 1.0e-6f) {
			return 0.0f;
		}

		float forwardReference = Math.max(2.0f, Math.abs(bodyVelocity.z()));
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), forwardReference);
		float angleOfAttack = (float) Math.atan2(Math.abs(bodyVelocity.y()), forwardReference);
		float lag = sanitizedCrossflowLag(acroAeroCrossflowLag);
		float crossflowExposure = Math.max(
				acroYawSidewashExposure(
						smoothStep((sideslip - ACRO_RESIDUAL_TORQUE_LOAD_CROSSFLOW_START_RADIANS)
								/ Math.max(0.001f, ACRO_RESIDUAL_TORQUE_LOAD_CROSSFLOW_FULL_RADIANS - ACRO_RESIDUAL_TORQUE_LOAD_CROSSFLOW_START_RADIANS)),
						lag,
						acroSidewashMemory
				),
				acroPitchLagExposure(
						smoothStep((angleOfAttack - ACRO_RESIDUAL_TORQUE_LOAD_CROSSFLOW_START_RADIANS)
								/ Math.max(0.001f, ACRO_RESIDUAL_TORQUE_LOAD_CROSSFLOW_FULL_RADIANS - ACRO_RESIDUAL_TORQUE_LOAD_CROSSFLOW_START_RADIANS)),
						lag
				)
		);
		if (crossflowExposure <= 1.0e-6f) {
			return 0.0f;
		}

		return ACRO_RESIDUAL_TORQUE_LOAD_MAX_RATE_LOSS
				* speedExposure
				* rateExposure
				* crossflowExposure;
	}

	static float acroRotorGyroRateLoadFraction(
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick,
			float throttle,
			float hoverThrottle
	) {
		float pitchRate = Math.abs(finiteOrZero(pitchRateRadiansPerTick)) / PLAYABLE_TICK_SECONDS;
		float rollRate = Math.abs(finiteOrZero(rollRateRadiansPerTick)) / PLAYABLE_TICK_SECONDS;
		float rateMagnitude = horizontalMagnitude(pitchRate, rollRate);
		if (rateMagnitude <= ACRO_ROTOR_GYRO_RATE_START_RADIANS_PER_SECOND) {
			return 0.0f;
		}
		float rpm = averageRpm(throttle, hoverThrottle);
		float rpmProgress = (rpm - HOVER_RPM) / Math.max(1.0f, MAX_RPM - HOVER_RPM);
		float rpmExposure = smoothStep((rpmProgress - ACRO_ROTOR_GYRO_RPM_START_FRACTION)
				/ Math.max(0.001f, 1.0f - ACRO_ROTOR_GYRO_RPM_START_FRACTION));
		if (rpmExposure <= 1.0e-6f) {
			return 0.0f;
		}
		float rateExposure = smoothStep((rateMagnitude - ACRO_ROTOR_GYRO_RATE_START_RADIANS_PER_SECOND)
				/ Math.max(0.001f, ACRO_ROTOR_GYRO_RATE_FULL_RADIANS_PER_SECOND - ACRO_ROTOR_GYRO_RATE_START_RADIANS_PER_SECOND));
		float largestAxisRate = Math.max(pitchRate, rollRate);
		float diagonalWeight = largestAxisRate <= 1.0e-6f
				? 0.0f
				: Math.min(pitchRate, rollRate) / largestAxisRate;
		return ACRO_ROTOR_GYRO_DIAGONAL_RATE_LOAD_MAX
				* rpmExposure
				* rateExposure
				* (ACRO_ROTOR_GYRO_SINGLE_AXIS_WEIGHT + (1.0f - ACRO_ROTOR_GYRO_SINGLE_AXIS_WEIGHT) * diagonalWeight);
	}

	static float acroTransverseFlowRollMomentRate(Velocity bodyVelocity, float rollCommand) {
		return acroTransverseFlowRollMomentRate(bodyVelocity, rollCommand, 0.45f, 0.20f);
	}

	static float acroTransverseFlowRollMomentRate(
			Velocity bodyVelocity,
			float rollCommand,
			float throttle,
			float hoverThrottle
	) {
		float horizontalSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		if (horizontalSpeed <= 1.0e-6f || Math.abs(bodyVelocity.x()) <= 1.0e-6f) {
			return 0.0f;
		}
		float speedExposure = smoothStep((horizontalSpeed - ACRO_TRANSVERSE_MOMENT_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_TRANSVERSE_MOMENT_SPEED_FULL_METERS_PER_SECOND - ACRO_TRANSVERSE_MOMENT_SPEED_START_METERS_PER_SECOND));
		if (speedExposure <= 1.0e-6f) {
			return 0.0f;
		}
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), Math.max(2.0f, Math.abs(bodyVelocity.z())));
		float sideslipExposure = smoothStep((sideslip - ACRO_TRANSVERSE_MOMENT_SIDESLIP_START_RADIANS)
				/ Math.max(0.001f, ACRO_TRANSVERSE_MOMENT_SIDESLIP_FULL_RADIANS - ACRO_TRANSVERSE_MOMENT_SIDESLIP_START_RADIANS));
		if (sideslipExposure <= 1.0e-6f) {
			return 0.0f;
		}
		float airframeMoment = ACRO_TRANSVERSE_AIRFRAME_ROLL_MOMENT_MAX_RATE_RADIANS_PER_TICK
				* speedExposure
				* sideslipExposure;
		float rpm = averageRpm(throttle, hoverThrottle);
		float activeDisk = smoothStep((rpm - IDLE_RPM) / Math.max(1.0f, HOVER_RPM - IDLE_RPM));
		float poweredMoment = 0.0f;
		if (activeDisk > 1.0e-6f) {
			float advanceRatioMu = acroRotorDiskAdvanceRatioMu(bodyVelocity, throttle, hoverThrottle);
			float muShape = acroTransverseFlowPoweredMuShape(advanceRatioMu);
			poweredMoment = ACRO_TRANSVERSE_ROLL_MOMENT_MAX_RATE_RADIANS_PER_TICK
					* speedExposure
					* activeDisk
					* muShape
					* sideslipExposure;
		}
		float activeRollSuppression = smoothStep(Math.abs(rollCommand) / ACRO_TRANSVERSE_ROLL_COMMAND_SUPPRESS);
		float commandScale = lerp(1.0f, ACRO_TRANSVERSE_ROLL_ACTIVE_KEEP, activeRollSuppression);
		return Math.signum(bodyVelocity.x())
				* (airframeMoment + poweredMoment)
				* commandScale;
	}

	static float acroPassiveRateHoldLimitedTransverseRollMoment(
			float transverseRollMomentRadiansPerTick,
			float previousRollRateRadiansPerTick,
			float rollCommand
	) {
		if (Math.abs(rollCommand) > PLAYABLE_AXIS_NOISE_EPSILON
				|| Math.abs(transverseRollMomentRadiansPerTick) <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK
				|| !Float.isFinite(previousRollRateRadiansPerTick)
				|| Math.signum(previousRollRateRadiansPerTick) != Math.signum(transverseRollMomentRadiansPerTick)) {
			return transverseRollMomentRadiansPerTick;
		}
		float holdExposure = smoothStep((Math.abs(previousRollRateRadiansPerTick) - ACRO_TRANSVERSE_PASSIVE_RATE_HOLD_START_RADIANS_PER_TICK)
				/ Math.max(0.001f, ACRO_TRANSVERSE_PASSIVE_RATE_HOLD_FULL_RADIANS_PER_TICK - ACRO_TRANSVERSE_PASSIVE_RATE_HOLD_START_RADIANS_PER_TICK));
		return transverseRollMomentRadiansPerTick * lerp(1.0f, ACRO_TRANSVERSE_PASSIVE_RATE_HOLD_KEEP, holdExposure);
	}

	static float acroTransverseFlowPoweredMuShape(float advanceRatioMu) {
		if (advanceRatioMu <= ACRO_TRANSVERSE_MOMENT_MU_START) {
			return 0.0f;
		}
		float build = smoothStep((advanceRatioMu - ACRO_TRANSVERSE_MOMENT_MU_START)
				/ Math.max(0.001f, ACRO_TRANSVERSE_MOMENT_MU_FULL - ACRO_TRANSVERSE_MOMENT_MU_START));
		float highMuFade = smoothStep((advanceRatioMu - ACRO_TRANSVERSE_MOMENT_MU_FADE_START)
				/ Math.max(0.001f, ACRO_TRANSVERSE_MOMENT_MU_FADE_FULL - ACRO_TRANSVERSE_MOMENT_MU_FADE_START));
		float highMuScale = lerp(1.0f, ACRO_TRANSVERSE_MOMENT_HIGH_MU_KEEP, highMuFade);
		return clamp(build * highMuScale, 0.0f, 1.0f);
	}

	static float acroAngleOfAttackPitchMomentRate(Velocity bodyVelocity, float pitchCommand) {
		float positiveForwardSpeed = Math.max(0.0f, bodyVelocity.z());
		float verticalSpeed = bodyVelocity.y();
		float pitchPlaneSpeed = horizontalMagnitude(verticalSpeed, positiveForwardSpeed);
		if (positiveForwardSpeed <= ACRO_AOA_MOMENT_FORWARD_START_METERS_PER_SECOND
				|| pitchPlaneSpeed <= 1.0e-6f
				|| Math.abs(verticalSpeed) <= 1.0e-6f) {
			return 0.0f;
		}
		float speedExposure = smoothStep((pitchPlaneSpeed - ACRO_AOA_MOMENT_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_AOA_MOMENT_SPEED_FULL_METERS_PER_SECOND - ACRO_AOA_MOMENT_SPEED_START_METERS_PER_SECOND));
		if (speedExposure <= 1.0e-6f) {
			return 0.0f;
		}
		float angleOfAttack = (float) Math.atan2(Math.abs(verticalSpeed), Math.max(2.0f, positiveForwardSpeed));
		float aoaExposure = smoothStep((angleOfAttack - ACRO_AOA_MOMENT_START_RADIANS)
				/ Math.max(0.001f, ACRO_AOA_MOMENT_FULL_RADIANS - ACRO_AOA_MOMENT_START_RADIANS));
		if (aoaExposure <= 1.0e-6f) {
			return 0.0f;
		}
		float activePitchSuppression = smoothStep(Math.abs(pitchCommand) / ACRO_AOA_PITCH_COMMAND_SUPPRESS);
		float commandScale = lerp(1.0f, ACRO_AOA_PITCH_ACTIVE_KEEP, activePitchSuppression);
		return -Math.signum(verticalSpeed)
				* ACRO_AOA_PITCH_MOMENT_MAX_RATE_RADIANS_PER_TICK
				* speedExposure
				* aoaExposure
				* commandScale;
	}

	static float acroAngleOfAttackPitchMomentActivity(float previousPitchRateRadiansPerTick, float pitchCommand) {
		if (Math.abs(pitchCommand) > PLAYABLE_AXIS_NOISE_EPSILON) {
			return 1.0f;
		}
		if (!Float.isFinite(previousPitchRateRadiansPerTick)) {
			return 0.0f;
		}
		return smoothStep((Math.abs(previousPitchRateRadiansPerTick) - ACRO_AOA_PITCH_ACTIVITY_START_RADIANS_PER_TICK)
				/ Math.max(0.001f, ACRO_AOA_PITCH_ACTIVITY_FULL_RADIANS_PER_TICK - ACRO_AOA_PITCH_ACTIVITY_START_RADIANS_PER_TICK));
	}

	static float acroAngleOfAttackPitchMomentScale(
			Velocity bodyVelocity,
			float pitchRadians,
			float previousPitchRateRadiansPerTick,
			float pitchCommand
	) {
		float activity = acroAngleOfAttackPitchMomentActivity(previousPitchRateRadiansPerTick, pitchCommand);
		float passive = acroAngleOfAttackPassivePitchMomentScale(bodyVelocity, pitchRadians);
		return passive + (1.0f - passive) * activity;
	}

	private static float acroAngleOfAttackPassivePitchMomentScale(Velocity bodyVelocity, float pitchRadians) {
		float verticalSpeed = bodyVelocity.y();
		if (!Float.isFinite(verticalSpeed) || Math.abs(verticalSpeed) <= 1.0e-6f) {
			return 0.0f;
		}
		float pitchResidual = signedRotationResidualRadians(pitchRadians);
		float momentDirection = -Math.signum(verticalSpeed);
		if (Math.abs(pitchResidual) > Math.toRadians(2.0f) && pitchResidual * momentDirection < 0.0f) {
			return 0.0f;
		}
		return ACRO_AOA_PITCH_PASSIVE_KEEP;
	}

	private static boolean isRateRising(float currentRateRadiansPerTick, float targetRateRadiansPerTick) {
		if (Math.abs(targetRateRadiansPerTick) <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK) {
			return false;
		}
		boolean sameDirection = Math.signum(currentRateRadiansPerTick) == 0.0f
				|| Math.signum(currentRateRadiansPerTick) == Math.signum(targetRateRadiansPerTick);
		return sameDirection && Math.abs(targetRateRadiansPerTick) > Math.abs(currentRateRadiansPerTick);
	}

	private static float verticalVelocity(float throttle, float hoverThrottle, Profile profile) {
		if (throttle <= THRUST_DEADZONE) {
			return -ZERO_THROTTLE_TERMINAL_DESCENT_METERS_PER_SECOND;
		}
		float centered = throttle - hoverThrottle;
		float magnitude = Math.abs(centered);
		if (magnitude < profile.hoverBand()) {
			return 0.0f;
		}
		float edgeRamp = smoothStep((magnitude - profile.hoverBand()) / VERTICAL_HOVER_EDGE_SOFTENING);
		if (centered > 0.0f) {
			return centered / Math.max(0.10f, 1.0f - hoverThrottle) * profile.thrustGain() * edgeRamp;
		}
		return centered / Math.max(0.10f, hoverThrottle) * profile.descentGain() * edgeRamp;
	}

	private static float attitudeAdjustedVerticalVelocity(
			FlightMode mode,
			float throttle,
			float hoverThrottle,
			float pitchRadians,
			float rollRadians,
			float verticalVelocity,
			Profile profile
	) {
		if (throttle <= THRUST_DEADZONE) {
			return verticalVelocity;
		}
		float verticalProjection = verticalThrustProjection(pitchRadians, rollRadians);
		float uprightProjection = clamp(verticalProjection, 0.0f, 1.0f);
		float liftProgress = smoothStep(throttle / Math.max(THRUST_DEADZONE, hoverThrottle + profile.hoverBand()));
		float tiltSink = tiltSinkMetersPerSecond(mode) * (1.0f - uprightProjection) * liftProgress;
		if (verticalVelocity > 0.0f) {
			float projectedClimb = verticalVelocity * clamp(verticalProjection, INVERTED_THRUST_VERTICAL_PROJECTION_MIN, 1.0f);
			return projectedClimb - tiltSink;
		}
		if (verticalVelocity < 0.0f) {
			return verticalVelocity - tiltSink * 0.45f;
		}
		return -tiltSink;
	}

	static float verticalThrustProjection(float pitchRadians, float rollRadians) {
		if (!Float.isFinite(pitchRadians) || !Float.isFinite(rollRadians)) {
			return 1.0f;
		}
		return clamp((float) (Math.cos(pitchRadians) * Math.cos(rollRadians)), -1.0f, 1.0f);
	}

	private static float tiltSinkMetersPerSecond(FlightMode mode) {
		return switch (safeMode(mode)) {
			case ANGLE -> ANGLE_TILT_SINK_METERS_PER_SECOND;
			case HORIZON -> HORIZON_TILT_SINK_METERS_PER_SECOND;
			case ACRO -> ACRO_TILT_SINK_METERS_PER_SECOND;
		};
	}

	private static float averageRpm(float throttle, float hoverThrottle) {
		if (throttle <= THRUST_DEADZONE) {
			return IDLE_RPM;
		}
		float safeHover = clamp(hoverThrottle, 0.12f, 0.55f);
		if (throttle <= safeHover) {
			float progress = smoothStep(throttle / Math.max(THRUST_DEADZONE, safeHover));
			return lerp(IDLE_RPM, HOVER_RPM, progress);
		}
		float progress = smoothStep((throttle - safeHover) / Math.max(0.10f, 1.0f - safeHover));
		return lerp(HOVER_RPM, MAX_RPM, progress);
	}

	private static float horizontalThrottleAuthority(
			FlightMode mode,
			float throttle,
			float hoverThrottle,
			boolean nearGroundLocked,
			float lowAltitudeHorizontalAuthorityScale,
			Profile profile
	) {
		float liftWindowTop = hoverThrottle + profile.hoverBand() * 2.0f;
		float liftProgress = smoothStep((throttle - THRUST_DEADZONE) / Math.max(0.10f, liftWindowTop - THRUST_DEADZONE));
		float authority = liftProgress;
		float highThrottleProgress = smoothStep((throttle - liftWindowTop) / Math.max(0.16f, 0.50f - liftWindowTop));
		authority += highThrottleProgress * MAX_HIGH_THROTTLE_HORIZONTAL_BOOST;
		authority *= lowAltitudeHorizontalAuthorityScale;
		if (nearGroundLocked && throttle <= liftWindowTop) {
			authority *= groundHorizontalAuthorityScale(mode);
		}
		return clamp(authority, 0.0f, 1.10f);
	}

	private static float horizontalVelocityCommand(FlightMode mode, float attitudeRadians, float maxAttitudeRadians, Profile profile) {
		float effectiveAttitudeRadians = horizontalVelocityAttitude(mode, attitudeRadians);
		float normalized = clamp(effectiveAttitudeRadians / maxAttitudeRadians, -1.0f, 1.0f);
		float magnitude = Math.abs(normalized);
		float progress = smoothStep(magnitude / Math.max(0.001f, profile.horizontalVelocityLinearStart()));
		float fineScale = clamp(profile.horizontalFineVelocityScale(), 0.0f, 1.0f);
		float gain = lerp(fineScale, 1.0f, progress);
		return Math.copySign(magnitude * gain, normalized);
	}

	private static float horizontalVelocityAttitude(FlightMode mode, float attitudeRadians) {
		if (!Float.isFinite(attitudeRadians)) {
			return 0.0f;
		}
		if (safeMode(mode) != FlightMode.ACRO) {
			return attitudeRadians;
		}
		return (float) Math.asin(clamp((float) Math.sin(attitudeRadians), -1.0f, 1.0f));
	}

	private static Velocity horizontalTargetVelocity(
			FlightMode mode,
			float pitchRadians,
			float rollRadians,
			float throttleAuthority,
			Profile profile
	) {
		if (safeMode(mode) == FlightMode.ACRO) {
			return acroThrustProjectionTargetVelocity(pitchRadians, rollRadians, throttleAuthority, profile);
		}
		return new Velocity(
				-horizontalVelocityCommand(mode, rollRadians, profile.maxRollRadians(), profile)
						* profile.horizontalSpeedMetersPerSecond()
						* throttleAuthority,
				0.0f,
				horizontalVelocityCommand(mode, pitchRadians, profile.maxPitchRadians(), profile)
						* profile.horizontalSpeedMetersPerSecond()
						* throttleAuthority
		);
	}

	private static Velocity acroThrustProjectionTargetVelocity(
			float pitchRadians,
			float rollRadians,
			float throttleAuthority,
			Profile profile
	) {
		Velocity thrustAxis = acroThrustAxis(pitchRadians, rollRadians);
		float horizontalProjection = horizontalMagnitude(thrustAxis.x(), thrustAxis.z());
		if (horizontalProjection <= 1.0e-6f || throttleAuthority <= 0.0f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float projectionMagnitude = acroHorizontalThrustProjectionMagnitude(thrustAxis, profile);
		float targetSpeed = profile.horizontalSpeedMetersPerSecond() * throttleAuthority * projectionMagnitude;
		return new Velocity(
				thrustAxis.x() / horizontalProjection * targetSpeed,
				0.0f,
				thrustAxis.z() / horizontalProjection * targetSpeed
		);
	}

	private static float acroHorizontalThrustProjectionMagnitude(float pitchRadians, float rollRadians, Profile profile) {
		return acroHorizontalThrustProjectionMagnitude(acroThrustAxis(pitchRadians, rollRadians), profile);
	}

	private static float acroHorizontalThrustProjectionMagnitude(Velocity thrustAxis, Profile profile) {
		float verticalProjection = thrustAxis.y();
		float horizontalProjection = (float) Math.sqrt(Math.max(0.0f, 1.0f - verticalProjection * verticalProjection));
		float fullAuthorityProjection = (float) Math.sin(Math.max(profile.maxPitchRadians(), profile.maxRollRadians()));
		return clamp(horizontalProjection / Math.max(0.10f, fullAuthorityProjection), 0.0f, 1.0f);
	}

	private static Velocity acroThrustAxis(float pitchRadians, float rollRadians) {
		return acroBodyFrame(pitchRadians, rollRadians).up();
	}

	private static float completedAcroRotationAttitude(
			FlightMode mode,
			float command,
			float attitudeRadians,
			float completedRotationCaptureRadians,
			float maxRateRadiansPerTick,
			float previousRateRadiansPerTick
	) {
		boolean filteredReleaseTail = isFilteredCompletedAcroRotationReleaseTail(command, maxRateRadiansPerTick, previousRateRadiansPerTick);
		float filteredReleaseAttitude = filteredReleaseTail
				? attitudeRadians + previousRateRadiansPerTick * ACRO_COMPLETED_ROTATION_FILTERED_RELEASE_LOOKAHEAD_TICKS
				: attitudeRadians;
		float minimumCompletedRotation = filteredReleaseTail
				? ACRO_COMPLETED_ROTATION_FILTERED_RELEASE_MIN_RADIANS
				: ACRO_COMPLETED_ROTATION_MIN_RADIANS;
		if (!Float.isFinite(attitudeRadians)
				|| safeMode(mode) != FlightMode.ACRO
				|| Math.max(Math.abs(attitudeRadians), Math.abs(filteredReleaseAttitude)) < minimumCompletedRotation) {
			return attitudeRadians;
		}
		float rotationResidual = signedRotationResidualRadians(attitudeRadians);
		float snapRadians = Math.max(
				ACRO_COMPLETED_ROTATION_SNAP_RADIANS,
				Math.max(0.0f, completedRotationCaptureRadians) + ACRO_COMPLETED_ROTATION_SNAP_MARGIN_RADIANS
		);
		float releasedSnapRadians = Math.max(snapRadians, ACRO_COMPLETED_ROTATION_RELEASE_SNAP_RADIANS);
		if (Math.abs(command) <= PLAYABLE_AXIS_NOISE_EPSILON) {
			return Math.abs(rotationResidual) <= releasedSnapRadians ? 0.0f : attitudeRadians;
		}
		boolean releaseTail = Math.abs(command) <= ACRO_COMPLETED_ROTATION_RELEASE_COMMAND || filteredReleaseTail;
		if (!releaseTail || Math.abs(rotationResidual) > releasedSnapRadians) {
			return attitudeRadians;
		}
		return 0.0f;
	}

	private static boolean isFilteredCompletedAcroRotationReleaseTail(
			float command,
			float maxRateRadiansPerTick,
			float previousRateRadiansPerTick
	) {
		if (!Float.isFinite(command)
				|| !Float.isFinite(maxRateRadiansPerTick)
				|| !Float.isFinite(previousRateRadiansPerTick)
				|| Math.abs(command) > ACRO_COMPLETED_ROTATION_FILTERED_RELEASE_COMMAND) {
			return false;
		}
		float targetRateMagnitude = Math.abs(command * maxRateRadiansPerTick);
		float previousRateMagnitude = Math.abs(previousRateRadiansPerTick);
		return previousRateMagnitude > targetRateMagnitude + ACRO_COMPLETED_ROTATION_FILTERED_RELEASE_RATE_DELTA_RADIANS;
	}

	private static float signedRotationResidualRadians(float attitudeRadians) {
		if (!Float.isFinite(attitudeRadians)) {
			return 0.0f;
		}
		float wrapped = (float) Math.IEEEremainder(attitudeRadians, FULL_ROTATION_RADIANS);
		if (wrapped <= -Math.PI) {
			return wrapped + FULL_ROTATION_RADIANS;
		}
		if (wrapped > Math.PI) {
			return wrapped - FULL_ROTATION_RADIANS;
		}
		return wrapped;
	}

	private static float continuousAttitudeFromResidual(float previousRadians, float nextResidualRadians) {
		float previous = Float.isFinite(previousRadians) ? previousRadians : 0.0f;
		float previousResidual = signedRotationResidualRadians(previous);
		float delta = signedRotationResidualRadians(nextResidualRadians - previousResidual);
		return previous + delta;
	}

	private static boolean completedAcroAttitudeWasCaptured(FlightMode mode, float unsnappedRadians, float snappedRadians) {
		return safeMode(mode) == FlightMode.ACRO
				&& Float.isFinite(unsnappedRadians)
				&& Float.isFinite(snappedRadians)
				&& Math.abs(unsnappedRadians - snappedRadians) > 1.0e-6f;
	}

	private static float horizontalTargetSpeedLimit(Profile profile, float throttleAuthority) {
		float throttleLimitedSpeed = profile.horizontalSpeedMetersPerSecond() * Math.max(0.0f, throttleAuthority);
		return Math.min(profile.horizontalSpeedLimitMetersPerSecond(), throttleLimitedSpeed);
	}

	private static Velocity velocityStep(
			FlightMode mode,
			float previousVelocityX,
			float previousVelocityY,
			float previousVelocityZ,
			float targetVelocityX,
			float targetVelocityY,
			float targetVelocityZ,
			float throttle,
			float hoverThrottle,
			float collectiveThrustToWeight,
			float pitchRadians,
			float rollRadians,
			float yawDegreesPerTick,
			float eulerPitchRateRadiansPerTick,
			float eulerRollRateRadiansPerTick,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick,
			float acroAeroCrossflowLag,
			float acroSidewashMemory,
			Profile profile
	) {
		if (safeMode(mode) == FlightMode.ACRO) {
			return acroPhysicalVelocity(previousVelocityX, previousVelocityY, previousVelocityZ, throttle, hoverThrottle, collectiveThrustToWeight, pitchRadians, rollRadians, yawDegreesPerTick, eulerPitchRateRadiansPerTick, eulerRollRateRadiansPerTick, pitchRateRadiansPerTick, rollRateRadiansPerTick, acroAeroCrossflowLag, acroSidewashMemory, profile);
		}
		Velocity horizontalVelocity = horizontalVelocityStep(
				previousVelocityX,
				previousVelocityZ,
				targetVelocityX,
				targetVelocityZ,
				profile
		);
		return new Velocity(
				horizontalVelocity.x(),
				inertialVelocity(
						previousVelocityY,
						targetVelocityY,
						verticalVelocitySmoothing(previousVelocityY, targetVelocityY, profile),
						profile.verticalAccelerationMetersPerSecondSquared(),
						profile.verticalBrakeAccelerationMetersPerSecondSquared()
				),
				horizontalVelocity.z()
		);
	}

	private static Velocity horizontalVelocityStep(
			float previousVelocityX,
			float previousVelocityZ,
			float targetVelocityX,
			float targetVelocityZ,
			Profile profile
	) {
		return new Velocity(
				inertialVelocity(
						previousVelocityX,
						targetVelocityX,
						velocitySmoothing(previousVelocityX, targetVelocityX, profile),
						profile.horizontalAccelerationMetersPerSecondSquared(),
						profile.horizontalBrakeAccelerationMetersPerSecondSquared()
				),
				0.0f,
				inertialVelocity(
						previousVelocityZ,
						targetVelocityZ,
						velocitySmoothing(previousVelocityZ, targetVelocityZ, profile),
						profile.horizontalAccelerationMetersPerSecondSquared(),
						profile.horizontalBrakeAccelerationMetersPerSecondSquared()
				)
		);
	}

	private static Velocity acroPhysicalVelocity(
			float previousVelocityX,
			float previousVelocityY,
			float previousVelocityZ,
			float throttle,
			float hoverThrottle,
			float collectiveThrustToWeight,
			float pitchRadians,
			float rollRadians,
			float yawDegreesPerTick,
			float eulerPitchRateRadiansPerTick,
			float eulerRollRateRadiansPerTick,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick,
			float acroAeroCrossflowLag,
			float acroSidewashMemory,
			Profile profile
	) {
		float integrationPitchRadians = acroMidpointIntegrationAttitudeRadians(pitchRadians, eulerPitchRateRadiansPerTick);
		float integrationRollRadians = acroMidpointIntegrationAttitudeRadians(rollRadians, eulerRollRateRadiansPerTick);
		Velocity thrustAxis = acroThrustAxis(integrationPitchRadians, integrationRollRadians);
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(previousVelocityX, previousVelocityY, previousVelocityZ, integrationPitchRadians, integrationRollRadians);
		Velocity bodyDragAcceleration = acroBodyAerodynamicAcceleration(bodyVelocity, acroAeroCrossflowLag, acroSidewashMemory);
		Velocity dragAcceleration = yawLocalVelocityForAcroBody(bodyDragAcceleration.x(), bodyDragAcceleration.y(), bodyDragAcceleration.z(), integrationPitchRadians, integrationRollRadians);
		float thrustScale = acroAdvanceRatioThrustScale(previousVelocityX, previousVelocityY, previousVelocityZ, integrationPitchRadians, integrationRollRadians, throttle, hoverThrottle, acroAeroCrossflowLag, acroSidewashMemory)
				* acroTranslationalLiftThrustScale(bodyVelocity, throttle, hoverThrottle, acroAeroCrossflowLag, acroSidewashMemory)
				* acroDynamicInflowThrustScale(bodyVelocity, pitchRateRadiansPerTick, rollRateRadiansPerTick, throttle, hoverThrottle, acroAeroCrossflowLag, acroSidewashMemory);
		float thrustAcceleration = ACRO_GRAVITY_METERS_PER_SECOND_SQUARED * collectiveThrustToWeight * thrustScale;
		Velocity flappingBodyAcceleration = acroRotorFlappingBodyAcceleration(bodyVelocity, thrustAcceleration, throttle, hoverThrottle, acroAeroCrossflowLag, acroSidewashMemory);
		Velocity inPlaneDragBodyAcceleration = acroRotorInPlaneDragBodyAcceleration(bodyVelocity, thrustAcceleration, throttle, hoverThrottle, acroAeroCrossflowLag, acroSidewashMemory);
		Velocity translationalLiftDragBodyAcceleration = acroTranslationalLiftDragBodyAcceleration(bodyVelocity, thrustAcceleration, throttle, hoverThrottle, acroAeroCrossflowLag, acroSidewashMemory);
		Velocity yawTurnLoadBodyAcceleration = acroYawTurnLoadBodyAcceleration(bodyVelocity, yawDegreesPerTick, acroAeroCrossflowLag, acroSidewashMemory);
		Velocity bodyRateLoadBodyAcceleration = acroBodyRateLoadBodyAcceleration(bodyVelocity, pitchRateRadiansPerTick, rollRateRadiansPerTick, yawDegreesPerTick, acroAeroCrossflowLag, acroSidewashMemory);
		Velocity thrustTurnLoadAcceleration = acroThrustVectorTurnLoadAcceleration(
				previousVelocityX,
				previousVelocityZ,
				thrustAxis.x() * thrustAcceleration,
				thrustAxis.z() * thrustAcceleration
		);
		Velocity sidewashTurnAcceleration = acroRotorSidewashTurnAcceleration(
				previousVelocityX,
				previousVelocityZ,
				thrustAxis.x() * thrustAcceleration,
				thrustAxis.z() * thrustAcceleration,
				bodyVelocity,
				acroAeroCrossflowLag,
				acroSidewashMemory
		);
		Velocity rotorDiskAcceleration = yawLocalVelocityForAcroBody(
				flappingBodyAcceleration.x() + inPlaneDragBodyAcceleration.x() + translationalLiftDragBodyAcceleration.x(),
				flappingBodyAcceleration.y() + inPlaneDragBodyAcceleration.y() + translationalLiftDragBodyAcceleration.y(),
				flappingBodyAcceleration.z() + inPlaneDragBodyAcceleration.z() + translationalLiftDragBodyAcceleration.z(),
				integrationPitchRadians,
				integrationRollRadians
		);
		Velocity yawTurnLoadAcceleration = yawLocalVelocityForAcroBody(
				yawTurnLoadBodyAcceleration.x(),
				yawTurnLoadBodyAcceleration.y(),
				yawTurnLoadBodyAcceleration.z(),
				integrationPitchRadians,
				integrationRollRadians
		);
		Velocity bodyRateLoadAcceleration = yawLocalVelocityForAcroBody(
				bodyRateLoadBodyAcceleration.x(),
				bodyRateLoadBodyAcceleration.y(),
				bodyRateLoadBodyAcceleration.z(),
				integrationPitchRadians,
				integrationRollRadians
		);
		float accelerationX = thrustAxis.x() * thrustAcceleration + dragAcceleration.x() + rotorDiskAcceleration.x() + yawTurnLoadAcceleration.x() + bodyRateLoadAcceleration.x();
		float accelerationY = thrustAxis.y() * thrustAcceleration - ACRO_GRAVITY_METERS_PER_SECOND_SQUARED + dragAcceleration.y() + rotorDiskAcceleration.y() + yawTurnLoadAcceleration.y() + bodyRateLoadAcceleration.y();
		float accelerationZ = thrustAxis.z() * thrustAcceleration + dragAcceleration.z() + rotorDiskAcceleration.z() + yawTurnLoadAcceleration.z() + bodyRateLoadAcceleration.z();
		accelerationX += thrustTurnLoadAcceleration.x();
		accelerationZ += thrustTurnLoadAcceleration.z();
		accelerationX += sidewashTurnAcceleration.x();
		accelerationZ += sidewashTurnAcceleration.z();
		Velocity overspeedAcceleration = acroOverspeedSoftBrakeAcceleration(
				previousVelocityX,
				previousVelocityZ,
				profile.horizontalSpeedLimitMetersPerSecond()
		);
		accelerationX += overspeedAcceleration.x();
		accelerationZ += overspeedAcceleration.z();
		return limitHorizontalVector(
				previousVelocityX + accelerationX * PLAYABLE_TICK_SECONDS,
				previousVelocityY + accelerationY * PLAYABLE_TICK_SECONDS,
				previousVelocityZ + accelerationZ * PLAYABLE_TICK_SECONDS,
				acroHorizontalHardLimit(profile)
		);
	}

	static float acroMidpointIntegrationAttitudeRadians(float currentRadians, float rateRadiansPerTick) {
		if (!Float.isFinite(currentRadians)) {
			return 0.0f;
		}
		if (!Float.isFinite(rateRadiansPerTick)
				|| Math.abs(rateRadiansPerTick) <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK) {
			return currentRadians;
		}
		return currentRadians - rateRadiansPerTick * 0.5f;
	}

	private static Velocity acroOverspeedSoftBrakeAcceleration(float velocityX, float velocityZ, float speedLimitMetersPerSecond) {
		float softStart = Math.max(0.0f, speedLimitMetersPerSecond) * ACRO_OVERSPEED_SOFT_START_SCALE;
		float horizontalSpeed = horizontalMagnitude(velocityX, velocityZ);
		if (horizontalSpeed <= softStart || horizontalSpeed <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float excessSpeed = horizontalSpeed - softStart;
		float accelerationMagnitude = excessSpeed
				* (ACRO_OVERSPEED_LINEAR_DAMPING_PER_SECOND + ACRO_OVERSPEED_QUADRATIC_DAMPING_PER_METER * excessSpeed);
		return new Velocity(
				-velocityX / horizontalSpeed * accelerationMagnitude,
				0.0f,
				-velocityZ / horizontalSpeed * accelerationMagnitude
		);
	}

	private static Velocity completedAcroRotationVelocityTrim(
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			boolean pitchCaptured,
			boolean rollCaptured,
			boolean rollRecoveryActive
	) {
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(velocityX, velocityY, velocityZ, pitchRadians, rollRadians);
		Velocity trimmedBodyVelocity = new Velocity(
				completedAcroRollSideVelocity(bodyVelocity.x(), rollCaptured, rollRecoveryActive),
				bodyVelocity.y(),
				pitchCaptured ? completedAcroRotationAxisVelocityTrim(bodyVelocity.z()) : bodyVelocity.z()
		);
		return yawLocalVelocityForAcroBody(trimmedBodyVelocity.x(), trimmedBodyVelocity.y(), trimmedBodyVelocity.z(), pitchRadians, rollRadians);
	}

	private static float completedAcroRollSideVelocity(float velocity, boolean rollCaptured, boolean rollRecoveryActive) {
		if (rollCaptured) {
			return completedAcroRollSideVelocityTrim(velocity);
		}
		if (rollRecoveryActive) {
			return completedAcroRollSideVelocityRecovery(velocity);
		}
		return velocity;
	}

	private static float completedAcroRotationAxisVelocityTrim(float velocity) {
		return Math.abs(velocity) <= ACRO_COMPLETED_ROTATION_DRIFT_TRIM_SPEED_METERS_PER_SECOND ? 0.0f : velocity;
	}

	private static float completedAcroRollSideVelocityTrim(float velocity) {
		float magnitude = Math.abs(velocity);
		if (magnitude <= ACRO_COMPLETED_ROTATION_DRIFT_TRIM_SPEED_METERS_PER_SECOND) {
			return 0.0f;
		}
		float residualMagnitude = Math.min(
				magnitude - ACRO_COMPLETED_ROTATION_DRIFT_TRIM_SPEED_METERS_PER_SECOND,
				ACRO_COMPLETED_ROLL_SIDE_SLIP_MAX_METERS_PER_SECOND
		);
		return Math.copySign(
				residualMagnitude,
				velocity
		);
	}

	private static float completedAcroRollSideVelocityRecovery(float velocity) {
		float recovered = smooth(velocity, 0.0f, ACRO_COMPLETED_ROLL_RECOVERY_SMOOTHING);
		float magnitude = Math.abs(recovered);
		if (magnitude <= ACRO_COMPLETED_ROLL_RECOVERY_SIDE_SLIP_MAX_METERS_PER_SECOND) {
			return recovered;
		}
		return Math.copySign(ACRO_COMPLETED_ROLL_RECOVERY_SIDE_SLIP_MAX_METERS_PER_SECOND, recovered);
	}

	static float acroAdvanceRatioThrustScale(
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			float throttle,
			float hoverThrottle
	) {
		return acroAdvanceRatioThrustScale(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, throttle, hoverThrottle, 1.0f);
	}

	static float acroAdvanceRatioThrustScale(
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag
	) {
		return acroAdvanceRatioThrustScale(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, throttle, hoverThrottle, acroAeroCrossflowLag, acroAeroCrossflowLag);
	}

	static float acroAdvanceRatioThrustScale(
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(velocityX, velocityY, velocityZ, pitchRadians, rollRadians);
		float advanceRatio = acroRotorAdvanceRatio(bodyVelocity, throttle, hoverThrottle);
		float progress = smoothStep((advanceRatio - ACRO_ADVANCE_LOSS_START_J) / Math.max(0.05f, ACRO_ADVANCE_LOSS_FULL_J - ACRO_ADVANCE_LOSS_START_J));
		float sideflowExposure = acroYawSidewashExposure(acroAdvanceSideflowExposure(bodyVelocity), acroAeroCrossflowLag, acroSidewashMemory);
		float maxLoss = lerp(ACRO_ADVANCE_MAX_THRUST_LOSS, ACRO_ADVANCE_SIDEFLOW_MAX_THRUST_LOSS, sideflowExposure);
		float scale = 1.0f - maxLoss * progress;
		return clamp(scale, 1.0f - maxLoss, 1.0f);
	}

	static float acroRotorAdvanceRatio(
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			float throttle,
			float hoverThrottle
	) {
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(velocityX, velocityY, velocityZ, pitchRadians, rollRadians);
		return acroRotorAdvanceRatio(bodyVelocity, throttle, hoverThrottle);
	}

	private static float acroRotorAdvanceRatio(Velocity bodyVelocity, float throttle, float hoverThrottle) {
		float diskPlaneSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		float axialSpeed = Math.abs(bodyVelocity.y());
		float effectiveFlowSpeed = (float) Math.sqrt(diskPlaneSpeed * diskPlaneSpeed + ACRO_ADVANCE_AXIAL_FLOW_WEIGHT * axialSpeed * axialSpeed);
		float rpm = Math.max(ACRO_ADVANCE_REFERENCE_MIN_RPM, averageRpm(throttle, hoverThrottle));
		float revsPerSecond = rpm / 60.0f;
		return effectiveFlowSpeed / Math.max(1.0f, revsPerSecond * ACRO_PROP_DIAMETER_METERS);
	}

	static float acroAdvanceSideflowExposure(Velocity bodyVelocity) {
		float diskPlaneSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		if (diskPlaneSpeed <= 1.0e-6f) {
			return 0.0f;
		}
		float sideflow = (float) Math.atan2(Math.abs(bodyVelocity.x()), Math.max(2.0f, Math.abs(bodyVelocity.z())));
		return smoothStep((sideflow - ACRO_ADVANCE_SIDEFLOW_START_RADIANS)
				/ Math.max(0.001f, ACRO_ADVANCE_SIDEFLOW_FULL_RADIANS - ACRO_ADVANCE_SIDEFLOW_START_RADIANS));
	}

	static float acroTranslationalLiftThrustScale(Velocity bodyVelocity, float throttle, float hoverThrottle) {
		return acroTranslationalLiftThrustScale(bodyVelocity, throttle, hoverThrottle, 1.0f);
	}

	static float acroTranslationalLiftThrustScale(
			Velocity bodyVelocity,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag
	) {
		return acroTranslationalLiftThrustScale(bodyVelocity, throttle, hoverThrottle, acroAeroCrossflowLag, acroAeroCrossflowLag);
	}

	static float acroTranslationalLiftThrustScale(
			Velocity bodyVelocity,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float advanceRatioMu = acroRotorDiskAdvanceRatioMu(bodyVelocity, throttle, hoverThrottle);
		if (advanceRatioMu <= ACRO_TRANSLATIONAL_LIFT_MU_START) {
			return 1.0f;
		}
		float build = smoothStep((advanceRatioMu - ACRO_TRANSLATIONAL_LIFT_MU_START)
				/ Math.max(0.001f, ACRO_TRANSLATIONAL_LIFT_MU_FULL - ACRO_TRANSLATIONAL_LIFT_MU_START));
		float highAdvanceFade = 1.0f - smoothStep((advanceRatioMu - ACRO_TRANSLATIONAL_LIFT_FADE_START_MU)
				/ Math.max(0.001f, ACRO_TRANSLATIONAL_LIFT_FADE_FULL_MU - ACRO_TRANSLATIONAL_LIFT_FADE_START_MU));
		if (build <= 1.0e-6f || highAdvanceFade <= 1.0e-6f) {
			return 1.0f;
		}
		float sideflowExposure = acroYawSidewashExposure(acroAdvanceSideflowExposure(bodyVelocity), acroAeroCrossflowLag, acroSidewashMemory);
		float sideflowWeight = lerp(1.0f, ACRO_TRANSLATIONAL_LIFT_SIDEFLOW_KEEP, sideflowExposure);
		float rpmWeight = smoothStep((averageRpm(throttle, hoverThrottle) - IDLE_RPM) / Math.max(1.0f, HOVER_RPM - IDLE_RPM));
		float gain = ACRO_TRANSLATIONAL_LIFT_MAX_THRUST_GAIN
				* build
				* highAdvanceFade
				* sideflowWeight
				* rpmWeight;
		return clamp(1.0f + gain, 1.0f, 1.0f + ACRO_TRANSLATIONAL_LIFT_MAX_THRUST_GAIN);
	}

	static Velocity acroTranslationalLiftDragBodyAcceleration(
			Velocity bodyVelocity,
			float thrustAcceleration,
			float throttle,
			float hoverThrottle
	) {
		return acroTranslationalLiftDragBodyAcceleration(bodyVelocity, thrustAcceleration, throttle, hoverThrottle, 1.0f);
	}

	static Velocity acroTranslationalLiftDragBodyAcceleration(
			Velocity bodyVelocity,
			float thrustAcceleration,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag
	) {
		return acroTranslationalLiftDragBodyAcceleration(bodyVelocity, thrustAcceleration, throttle, hoverThrottle, acroAeroCrossflowLag, acroAeroCrossflowLag);
	}

	static Velocity acroTranslationalLiftDragBodyAcceleration(
			Velocity bodyVelocity,
			float thrustAcceleration,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float diskPlaneSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		if (diskPlaneSpeed <= 1.0e-6f || thrustAcceleration <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float thrustGain = acroTranslationalLiftThrustScale(bodyVelocity, throttle, hoverThrottle, acroAeroCrossflowLag, acroSidewashMemory) - 1.0f;
		if (thrustGain <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float accelerationMagnitude = clamp(
				thrustAcceleration * thrustGain * ACRO_TRANSLATIONAL_LIFT_DRAG_GAIN,
				0.0f,
				ACRO_TRANSLATIONAL_LIFT_DRAG_MAX_ACCELERATION
		);
		if (accelerationMagnitude <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		return new Velocity(
				-bodyVelocity.x() / diskPlaneSpeed * accelerationMagnitude,
				0.0f,
				-bodyVelocity.z() / diskPlaneSpeed * accelerationMagnitude
		);
	}

	static Velocity acroRotorFlappingBodyAcceleration(
			Velocity bodyVelocity,
			float thrustAcceleration,
			float throttle,
			float hoverThrottle
	) {
		return acroRotorFlappingBodyAcceleration(bodyVelocity, thrustAcceleration, throttle, hoverThrottle, 1.0f, 1.0f);
	}

	static Velocity acroRotorFlappingBodyAcceleration(
			Velocity bodyVelocity,
			float thrustAcceleration,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag
	) {
		return acroRotorFlappingBodyAcceleration(bodyVelocity, thrustAcceleration, throttle, hoverThrottle, acroAeroCrossflowLag, acroAeroCrossflowLag);
	}

	static Velocity acroRotorFlappingBodyAcceleration(
			Velocity bodyVelocity,
			float thrustAcceleration,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float diskPlaneSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		if (diskPlaneSpeed <= 1.0e-6f || thrustAcceleration <= 1.0e-6f || ACRO_ROTOR_FLAPPING_COEFFICIENT <= 0.0f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float advanceRatioMu = acroRotorDiskAdvanceRatioMu(bodyVelocity, throttle, hoverThrottle);
		float advanceResponse = clamp(advanceRatioMu / ACRO_ROTOR_FLAPPING_FULL_MU, 0.0f, 1.0f);
		float thrustFraction = clamp(
				thrustAcceleration / (ACRO_GRAVITY_METERS_PER_SECOND_SQUARED * ACRO_FULL_THROTTLE_THRUST_TO_WEIGHT),
				0.0f,
				1.0f
		);
		float diskLoadingResponse = clamp(0.72f + 0.28f * (float) Math.sqrt(thrustFraction), 0.0f, 1.0f);
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), Math.max(2.0f, Math.abs(bodyVelocity.z())));
		float sideslipExposure = smoothStep((sideslip - ACRO_ROTOR_FLAPPING_SIDESLIP_START_RADIANS)
				/ Math.max(0.001f, ACRO_ROTOR_FLAPPING_SIDESLIP_FULL_RADIANS - ACRO_ROTOR_FLAPPING_SIDESLIP_START_RADIANS));
		float flowWeight = ACRO_ROTOR_FLAPPING_STRAIGHT_FLOW_WEIGHT
				+ (1.0f - ACRO_ROTOR_FLAPPING_STRAIGHT_FLOW_WEIGHT)
						* clamp(sideslipExposure, 0.0f, 1.0f)
						* acroSidewashForceResponse(acroAeroCrossflowLag, acroSidewashMemory);
		float tiltRadians = clamp(
				ACRO_ROTOR_FLAPPING_COEFFICIENT * advanceResponse * diskLoadingResponse * flowWeight,
				0.0f,
				ACRO_ROTOR_FLAPPING_MAX_TILT_RADIANS
		);
		if (tiltRadians <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float tiltX = -bodyVelocity.x() / diskPlaneSpeed * tiltRadians;
		float tiltZ = -bodyVelocity.z() / diskPlaneSpeed * tiltRadians;
		float verticalLoss = thrustAcceleration * (1.0f - (float) Math.sqrt(Math.max(0.0f, 1.0f - tiltRadians * tiltRadians)));
		return new Velocity(
				tiltX * thrustAcceleration,
				-verticalLoss,
				tiltZ * thrustAcceleration
		);
	}

	static float acroRotorDiskAdvanceRatioMu(Velocity bodyVelocity, float throttle, float hoverThrottle) {
		float diskPlaneSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		float rpm = Math.max(ACRO_ADVANCE_REFERENCE_MIN_RPM, averageRpm(throttle, hoverThrottle));
		float tipSpeed = rpm / 60.0f * (float) Math.PI * ACRO_PROP_DIAMETER_METERS;
		return diskPlaneSpeed / Math.max(1.0f, tipSpeed);
	}

	static Velocity acroRotorInPlaneDragBodyAcceleration(
			Velocity bodyVelocity,
			float thrustAcceleration,
			float throttle,
			float hoverThrottle
	) {
		return acroRotorInPlaneDragBodyAcceleration(bodyVelocity, thrustAcceleration, throttle, hoverThrottle, 1.0f, 1.0f);
	}

	static Velocity acroRotorInPlaneDragBodyAcceleration(
			Velocity bodyVelocity,
			float thrustAcceleration,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag
	) {
		return acroRotorInPlaneDragBodyAcceleration(bodyVelocity, thrustAcceleration, throttle, hoverThrottle, acroAeroCrossflowLag, acroAeroCrossflowLag);
	}

	static Velocity acroRotorInPlaneDragBodyAcceleration(
			Velocity bodyVelocity,
			float thrustAcceleration,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float diskPlaneSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		if (diskPlaneSpeed <= 1.0e-6f || thrustAcceleration <= 1.0e-6f || ACRO_ROTOR_DISK_DRAG_COEFFICIENT <= 0.0f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float rpm = Math.max(ACRO_ADVANCE_REFERENCE_MIN_RPM, averageRpm(throttle, hoverThrottle));
		float spinRatio = clamp(rpm / ACRO_ROTOR_REFERENCE_MAX_RPM, 0.0f, 1.10f);
		if (spinRatio <= 0.08f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}

		float advanceRatioMu = acroRotorDiskAdvanceRatioMu(bodyVelocity, throttle, hoverThrottle);
		float activeDisk = smoothStep((spinRatio - 0.10f) / 0.22f);
		float crossflow = smoothStep((advanceRatioMu - 0.025f) / 0.325f);
		float loadedCrossflow = smoothStep((advanceRatioMu - 0.08f) / 0.47f);
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), Math.max(2.0f, Math.abs(bodyVelocity.z())));
		float sideslipExposure = smoothStep((sideslip - ACRO_ROTOR_IN_PLANE_SIDESLIP_START_RADIANS)
				/ Math.max(0.001f, ACRO_ROTOR_IN_PLANE_SIDESLIP_FULL_RADIANS - ACRO_ROTOR_IN_PLANE_SIDESLIP_START_RADIANS));
		float flowWeight = ACRO_ROTOR_IN_PLANE_STRAIGHT_FLOW_WEIGHT
				+ (1.0f - ACRO_ROTOR_IN_PLANE_STRAIGHT_FLOW_WEIGHT)
						* clamp(sideslipExposure, 0.0f, 1.0f)
						* acroSidewashForceResponse(acroAeroCrossflowLag, acroSidewashMemory);

		float diskDragScale = clamp(ACRO_ROTOR_DISK_DRAG_COEFFICIENT / 0.0028f, 0.0f, 3.5f);
		float thrustCoupledCoefficient = diskDragScale
				* activeDisk
				* crossflow
				* (0.030f + 0.105f * loadedCrossflow);
		float thrustCoupledAcceleration = thrustAcceleration * thrustCoupledCoefficient;

		float totalDiskArea = ACRO_ROTOR_COUNT * (float) Math.PI * ACRO_ROTOR_RADIUS_METERS * ACRO_ROTOR_RADIUS_METERS;
		float dynamicPressure = 0.5f * ACRO_AIR_DENSITY_KILOGRAMS_PER_CUBIC_METER * diskPlaneSpeed * diskPlaneSpeed;
		float profileCoefficient = diskDragScale
				* activeDisk
				* (0.020f + 0.045f * loadedCrossflow)
				* smoothStep((advanceRatioMu - 0.04f) / 0.28f);
		float profileAcceleration = dynamicPressure * totalDiskArea * profileCoefficient / ACRO_REFERENCE_MASS_KILOGRAMS;
		float accelerationMagnitude = clamp(
				(thrustCoupledAcceleration + profileAcceleration) * flowWeight,
				0.0f,
				ACRO_ROTOR_IN_PLANE_MAX_ACCELERATION
		);
		if (accelerationMagnitude <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		return new Velocity(
				-bodyVelocity.x() / diskPlaneSpeed * accelerationMagnitude,
				0.0f,
				-bodyVelocity.z() / diskPlaneSpeed * accelerationMagnitude
		);
	}

	static float acroDynamicInflowThrustScale(
			Velocity bodyVelocity,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick,
			float throttle,
			float hoverThrottle
	) {
		return acroDynamicInflowThrustScale(bodyVelocity, pitchRateRadiansPerTick, rollRateRadiansPerTick, throttle, hoverThrottle, 1.0f, 1.0f);
	}

	static float acroDynamicInflowThrustScale(
			Velocity bodyVelocity,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag
	) {
		return acroDynamicInflowThrustScale(bodyVelocity, pitchRateRadiansPerTick, rollRateRadiansPerTick, throttle, hoverThrottle, acroAeroCrossflowLag, acroAeroCrossflowLag);
	}

	static float acroDynamicInflowThrustScale(
			Velocity bodyVelocity,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float speedSquared = bodyVelocity.x() * bodyVelocity.x()
				+ bodyVelocity.y() * bodyVelocity.y()
				+ bodyVelocity.z() * bodyVelocity.z();
		if (speedSquared <= 1.0e-6f) {
			return 1.0f;
		}
		float pitchRate = finiteOrZero(pitchRateRadiansPerTick) / PLAYABLE_TICK_SECONDS;
		float rollRate = finiteOrZero(rollRateRadiansPerTick) / PLAYABLE_TICK_SECONDS;
		float rateMagnitude = horizontalMagnitude(pitchRate, rollRate);
		if (rateMagnitude <= ACRO_DYNAMIC_INFLOW_RATE_START_RADIANS_PER_SECOND) {
			return 1.0f;
		}

		float speed = (float) Math.sqrt(speedSquared);
		float speedExposure = smoothStep((speed - ACRO_DYNAMIC_INFLOW_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_DYNAMIC_INFLOW_SPEED_FULL_METERS_PER_SECOND - ACRO_DYNAMIC_INFLOW_SPEED_START_METERS_PER_SECOND));
		if (speedExposure <= 1.0e-6f) {
			return 1.0f;
		}
		float rateExposure = smoothStep((rateMagnitude - ACRO_DYNAMIC_INFLOW_RATE_START_RADIANS_PER_SECOND)
				/ Math.max(0.001f, ACRO_DYNAMIC_INFLOW_RATE_FULL_RADIANS_PER_SECOND - ACRO_DYNAMIC_INFLOW_RATE_START_RADIANS_PER_SECOND));
		float forwardReference = Math.max(2.0f, Math.abs(bodyVelocity.z()));
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), forwardReference);
		float angleOfAttack = (float) Math.atan2(Math.abs(bodyVelocity.y()), forwardReference);
		float lag = sanitizedCrossflowLag(acroAeroCrossflowLag);
		float yawCrossflow = smoothStep((sideslip - ACRO_DYNAMIC_INFLOW_CROSSFLOW_START_RADIANS)
				/ Math.max(0.001f, ACRO_DYNAMIC_INFLOW_CROSSFLOW_FULL_RADIANS - ACRO_DYNAMIC_INFLOW_CROSSFLOW_START_RADIANS));
		float pitchCrossflow = smoothStep((angleOfAttack - ACRO_DYNAMIC_INFLOW_CROSSFLOW_START_RADIANS)
				/ Math.max(0.001f, ACRO_DYNAMIC_INFLOW_CROSSFLOW_FULL_RADIANS - ACRO_DYNAMIC_INFLOW_CROSSFLOW_START_RADIANS));
		float crossflow = Math.max(
				yawCrossflow * acroSidewashForceResponse(lag, acroSidewashMemory),
				pitchCrossflow * lag
		);
		float flowWeight = ACRO_DYNAMIC_INFLOW_STRAIGHT_FLOW_WEIGHT
				+ (1.0f - ACRO_DYNAMIC_INFLOW_STRAIGHT_FLOW_WEIGHT) * crossflow;
		float rpmProgress = (averageRpm(throttle, hoverThrottle) - HOVER_RPM) / Math.max(1.0f, MAX_RPM - HOVER_RPM);
		float rpmWeight = ACRO_DYNAMIC_INFLOW_RPM_IDLE_WEIGHT
				+ (1.0f - ACRO_DYNAMIC_INFLOW_RPM_IDLE_WEIGHT) * smoothStep(rpmProgress);
		float loss = ACRO_DYNAMIC_INFLOW_MAX_THRUST_LOSS * speedExposure * rateExposure * flowWeight * rpmWeight;
		return clamp(1.0f - loss, 1.0f - ACRO_DYNAMIC_INFLOW_MAX_THRUST_LOSS, 1.0f);
	}

	private static float acroResponsiveCollectiveThrustToWeight(FlightMode mode, float previousCollectiveThrustToWeight, float targetCollectiveThrustToWeight) {
		if (safeMode(mode) != FlightMode.ACRO) {
			return targetCollectiveThrustToWeight;
		}
		if (!Float.isFinite(previousCollectiveThrustToWeight)) {
			return targetCollectiveThrustToWeight;
		}
		float smoothing = targetCollectiveThrustToWeight >= previousCollectiveThrustToWeight
				? ACRO_THRUST_RISE_SMOOTHING
				: ACRO_THRUST_FALL_SMOOTHING;
		float responsive = smooth(previousCollectiveThrustToWeight, targetCollectiveThrustToWeight, smoothing);
		if (Math.abs(responsive - targetCollectiveThrustToWeight) <= ACRO_THRUST_SETTLE_EPSILON) {
			return targetCollectiveThrustToWeight;
		}
		return responsive;
	}

	private static float acroCollectiveThrustToWeight(float throttle, float hoverThrottle) {
		if (throttle <= THRUST_DEADZONE) {
			return 0.0f;
		}
		float safeHover = clamp(hoverThrottle, 0.12f, 0.55f);
		if (throttle <= safeHover) {
			return clamp(throttle / safeHover, 0.0f, 1.0f);
		}
		float climbProgress = (throttle - safeHover) / Math.max(0.10f, 1.0f - safeHover);
		return 1.0f + clamp(climbProgress, 0.0f, 1.0f) * (ACRO_FULL_THROTTLE_THRUST_TO_WEIGHT - 1.0f);
	}

	private static float acroAeroCrossflowLag(
			FlightMode mode,
			float previousLag,
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians
	) {
		if (safeMode(mode) != FlightMode.ACRO) {
			return 0.0f;
		}
		float safePreviousLag = Float.isFinite(previousLag) ? clamp(previousLag, 0.0f, 1.0f) : 0.0f;
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(velocityX, velocityY, velocityZ, pitchRadians, rollRadians);
		float targetLag = acroAeroCrossflowLagTarget(bodyVelocity);
		float smoothing = targetLag >= safePreviousLag
				? ACRO_AERO_CROSSFLOW_LAG_RISE_SMOOTHING
				: ACRO_AERO_CROSSFLOW_LAG_FALL_SMOOTHING;
		return smooth(safePreviousLag, targetLag, smoothing);
	}

	static float acroAeroCrossflowLagTarget(Velocity bodyVelocity) {
		float speedSquared = bodyVelocity.x() * bodyVelocity.x()
				+ bodyVelocity.y() * bodyVelocity.y()
				+ bodyVelocity.z() * bodyVelocity.z();
		if (speedSquared <= 1.0e-6f) {
			return 0.0f;
		}
		float speed = (float) Math.sqrt(speedSquared);
		float speedExposure = smoothStep((speed - ACRO_AERO_CROSSFLOW_LAG_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_AERO_CROSSFLOW_LAG_SPEED_FULL_METERS_PER_SECOND - ACRO_AERO_CROSSFLOW_LAG_SPEED_START_METERS_PER_SECOND));
		if (speedExposure <= 1.0e-6f) {
			return 0.0f;
		}
		float forwardReference = Math.max(2.0f, Math.abs(bodyVelocity.z()));
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), forwardReference);
		float angleOfAttack = (float) Math.atan2(Math.abs(bodyVelocity.y()), forwardReference);
		float sideslipExposure = smoothStep((sideslip - ACRO_AERO_CROSSFLOW_LAG_START_RADIANS)
				/ Math.max(0.001f, ACRO_AERO_CROSSFLOW_LAG_FULL_RADIANS - ACRO_AERO_CROSSFLOW_LAG_START_RADIANS));
		float angleOfAttackExposure = smoothStep((angleOfAttack - ACRO_AERO_CROSSFLOW_LAG_START_RADIANS)
				/ Math.max(0.001f, ACRO_AERO_CROSSFLOW_LAG_FULL_RADIANS - ACRO_AERO_CROSSFLOW_LAG_START_RADIANS));
		return clamp(speedExposure * Math.max(sideslipExposure, angleOfAttackExposure), 0.0f, 1.0f);
	}

	static float acroSidewashMemory(
			FlightMode mode,
			float previousMemory,
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians
	) {
		if (safeMode(mode) != FlightMode.ACRO) {
			return 0.0f;
		}
		float safePreviousMemory = sanitizedSidewashMemory(previousMemory);
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(velocityX, velocityY, velocityZ, pitchRadians, rollRadians);
		float targetMemory = acroSidewashMemoryTarget(bodyVelocity);
		float smoothing = targetMemory >= safePreviousMemory
				? ACRO_SIDEWASH_MEMORY_RISE_SMOOTHING
				: ACRO_SIDEWASH_MEMORY_FALL_SMOOTHING;
		return smooth(safePreviousMemory, targetMemory, smoothing);
	}

	static float acroSidewashMemoryTarget(Velocity bodyVelocity) {
		float horizontalSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		if (horizontalSpeed <= ACRO_SIDEWASH_MEMORY_SPEED_START_METERS_PER_SECOND
				|| Math.abs(bodyVelocity.x()) <= 1.0e-6f) {
			return 0.0f;
		}
		float speedExposure = smoothStep((horizontalSpeed - ACRO_SIDEWASH_MEMORY_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_SIDEWASH_MEMORY_SPEED_FULL_METERS_PER_SECOND - ACRO_SIDEWASH_MEMORY_SPEED_START_METERS_PER_SECOND));
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), Math.max(2.0f, Math.abs(bodyVelocity.z())));
		float sideslipExposure = smoothStep((sideslip - ACRO_SIDEWASH_MEMORY_SIDESLIP_START_RADIANS)
				/ Math.max(0.001f, ACRO_SIDEWASH_MEMORY_SIDESLIP_FULL_RADIANS - ACRO_SIDEWASH_MEMORY_SIDESLIP_START_RADIANS));
		return clamp(speedExposure * sideslipExposure, 0.0f, 1.0f);
	}

	static float acroSidewashForceResponse(float acroAeroCrossflowLag, float acroSidewashMemory) {
		float lag = sanitizedCrossflowLag(acroAeroCrossflowLag);
		float memory = sanitizedSidewashMemory(acroSidewashMemory);
		return clamp(Math.max(memory, lag * ACRO_SIDEWASH_FORCE_MIN_CROSSFLOW_RESPONSE), 0.0f, 1.0f);
	}

	private static float laggedCrossflowExposure(float exposure, float acroAeroCrossflowLag) {
		return clamp(finiteOrZero(exposure), 0.0f, 1.0f) * sanitizedCrossflowLag(acroAeroCrossflowLag);
	}

	private static float acroYawSidewashExposure(float exposure, float acroAeroCrossflowLag, float acroSidewashMemory) {
		return clamp(finiteOrZero(exposure), 0.0f, 1.0f) * acroSidewashForceResponse(acroAeroCrossflowLag, acroSidewashMemory);
	}

	private static float acroPitchLagExposure(float exposure, float acroAeroCrossflowLag) {
		return clamp(finiteOrZero(exposure), 0.0f, 1.0f) * sanitizedCrossflowLag(acroAeroCrossflowLag);
	}

	private static float sanitizedCrossflowLag(float acroAeroCrossflowLag) {
		return Float.isFinite(acroAeroCrossflowLag) ? clamp(acroAeroCrossflowLag, 0.0f, 1.0f) : 0.0f;
	}

	private static float sanitizedSidewashMemory(float acroSidewashMemory) {
		return Float.isFinite(acroSidewashMemory) ? clamp(acroSidewashMemory, 0.0f, 1.0f) : 0.0f;
	}

	static Velocity acroDragAcceleration(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians) {
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(velocityX, velocityY, velocityZ, pitchRadians, rollRadians);
		Velocity bodyDragAcceleration = acroBodyAerodynamicAcceleration(bodyVelocity);
		return yawLocalVelocityForAcroBody(bodyDragAcceleration.x(), bodyDragAcceleration.y(), bodyDragAcceleration.z(), pitchRadians, rollRadians);
	}

	static Velocity acroBodyAerodynamicAcceleration(Velocity bodyVelocity) {
		return acroBodyAerodynamicAcceleration(bodyVelocity, 1.0f);
	}

	static Velocity acroBodyAerodynamicAcceleration(Velocity bodyVelocity, float crossflowLag) {
		return acroBodyAerodynamicAcceleration(bodyVelocity, crossflowLag, crossflowLag);
	}

	static Velocity acroBodyAerodynamicAcceleration(Velocity bodyVelocity, float crossflowLag, float sidewashMemory) {
		float lag = sanitizedCrossflowLag(crossflowLag);
		Velocity baseDragAcceleration = acroBaseBodyDragAcceleration(bodyVelocity, lag);
		float separation = acroAirframeSeparationIntensity(bodyVelocity.x(), bodyVelocity.y(), bodyVelocity.z(), lag, sidewashMemory);
		Velocity coupledDynamicPressureDragAcceleration = acroCoupledDynamicPressureDragAcceleration(bodyVelocity, lag, sidewashMemory);
		Velocity separatedDragAcceleration = acroSeparatedFlowDragAcceleration(bodyVelocity, separation);
		Velocity pitchLiftAcceleration = scaleVelocity(acroPitchPlaneLiftAcceleration(bodyVelocity, separation), lag);
		float sidewashResponse = acroSidewashForceResponse(lag, sidewashMemory);
		Velocity settledSideforceAcceleration = acroSideslipSideforceAcceleration(bodyVelocity, separation);
		Velocity sideforceAcceleration = scaleVelocity(settledSideforceAcceleration, sidewashResponse);
		Velocity sideforceInducedDragAcceleration = acroSideslipInducedDragAcceleration(bodyVelocity, settledSideforceAcceleration, sidewashResponse);
		return new Velocity(
				baseDragAcceleration.x() + coupledDynamicPressureDragAcceleration.x() + separatedDragAcceleration.x() + pitchLiftAcceleration.x() + sideforceAcceleration.x() + sideforceInducedDragAcceleration.x(),
				baseDragAcceleration.y() + coupledDynamicPressureDragAcceleration.y() + separatedDragAcceleration.y() + pitchLiftAcceleration.y() + sideforceAcceleration.y() + sideforceInducedDragAcceleration.y(),
				baseDragAcceleration.z() + coupledDynamicPressureDragAcceleration.z() + separatedDragAcceleration.z() + pitchLiftAcceleration.z() + sideforceAcceleration.z() + sideforceInducedDragAcceleration.z()
		);
	}

	static Velocity acroBaseBodyDragAcceleration(Velocity bodyVelocity, float crossflowLag) {
		float safeLag = sanitizedCrossflowLag(crossflowLag);
		float forwardReference = Math.max(2.0f, Math.abs(bodyVelocity.z()));
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), forwardReference);
		float angleOfAttack = (float) Math.atan2(Math.abs(bodyVelocity.y()), forwardReference);
		float sideslipExposure = smoothStep((sideslip - ACRO_AERO_CROSSFLOW_LAG_START_RADIANS)
				/ Math.max(0.001f, ACRO_AERO_CROSSFLOW_LAG_FULL_RADIANS - ACRO_AERO_CROSSFLOW_LAG_START_RADIANS));
		float angleOfAttackExposure = smoothStep((angleOfAttack - ACRO_AERO_CROSSFLOW_LAG_START_RADIANS)
				/ Math.max(0.001f, ACRO_AERO_CROSSFLOW_LAG_FULL_RADIANS - ACRO_AERO_CROSSFLOW_LAG_START_RADIANS));
		float lateralBroadside = sideslipExposure * safeLag;
		float verticalBroadside = angleOfAttackExposure * safeLag;
		float lateralLinearDrag = lerp(ACRO_FORWARD_LINEAR_DRAG_PER_SECOND, ACRO_LATERAL_LINEAR_DRAG_PER_SECOND, lateralBroadside);
		float lateralQuadraticDrag = lerp(ACRO_FORWARD_QUADRATIC_DRAG_PER_METER, ACRO_LATERAL_QUADRATIC_DRAG_PER_METER, lateralBroadside);
		float verticalLinearDrag = lerp(ACRO_FORWARD_LINEAR_DRAG_PER_SECOND, ACRO_VERTICAL_LINEAR_DRAG_PER_SECOND, verticalBroadside);
		float verticalQuadraticDrag = lerp(ACRO_FORWARD_QUADRATIC_DRAG_PER_METER, ACRO_VERTICAL_QUADRATIC_DRAG_PER_METER, verticalBroadside);
		return new Velocity(
				-dragAcceleration(bodyVelocity.x(), lateralLinearDrag, lateralQuadraticDrag),
				-dragAcceleration(bodyVelocity.y(), verticalLinearDrag, verticalQuadraticDrag),
				-dragAcceleration(bodyVelocity.z(), ACRO_FORWARD_LINEAR_DRAG_PER_SECOND, ACRO_FORWARD_QUADRATIC_DRAG_PER_METER)
		);
	}

	static Velocity acroCoupledDynamicPressureDragAcceleration(Velocity bodyVelocity) {
		return acroCoupledDynamicPressureDragAcceleration(bodyVelocity, 1.0f, 1.0f);
	}

	static Velocity acroCoupledDynamicPressureDragAcceleration(Velocity bodyVelocity, float crossflowLag, float sidewashMemory) {
		float speedSquared = bodyVelocity.x() * bodyVelocity.x()
				+ bodyVelocity.y() * bodyVelocity.y()
				+ bodyVelocity.z() * bodyVelocity.z();
		if (speedSquared <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float speed = (float) Math.sqrt(speedSquared);
		float speedExposure = smoothStep((speed - ACRO_COUPLED_DYNAMIC_PRESSURE_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_COUPLED_DYNAMIC_PRESSURE_SPEED_FULL_METERS_PER_SECOND - ACRO_COUPLED_DYNAMIC_PRESSURE_SPEED_START_METERS_PER_SECOND));
		if (speedExposure <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float forwardReference = Math.max(2.0f, Math.abs(bodyVelocity.z()));
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), forwardReference);
		float angleOfAttack = (float) Math.atan2(Math.abs(bodyVelocity.y()), forwardReference);
		float lag = sanitizedCrossflowLag(crossflowLag);
		float yawCrossflowExposure = smoothStep((sideslip - ACRO_COUPLED_DYNAMIC_PRESSURE_CROSSFLOW_START_RADIANS)
				/ Math.max(0.001f, ACRO_COUPLED_DYNAMIC_PRESSURE_CROSSFLOW_FULL_RADIANS - ACRO_COUPLED_DYNAMIC_PRESSURE_CROSSFLOW_START_RADIANS));
		float pitchCrossflowExposure = smoothStep((angleOfAttack - ACRO_COUPLED_DYNAMIC_PRESSURE_CROSSFLOW_START_RADIANS)
				/ Math.max(0.001f, ACRO_COUPLED_DYNAMIC_PRESSURE_CROSSFLOW_FULL_RADIANS - ACRO_COUPLED_DYNAMIC_PRESSURE_CROSSFLOW_START_RADIANS));
		float crossflowExposure = Math.max(
				yawCrossflowExposure * acroSidewashForceResponse(lag, sidewashMemory),
				pitchCrossflowExposure * lag
		);
		if (crossflowExposure <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float gain = ACRO_COUPLED_DYNAMIC_PRESSURE_EXTRA_GAIN * speedExposure * crossflowExposure;
		return new Velocity(
				coupledAxisDynamicPressureDrag(bodyVelocity.x(), speed, ACRO_LATERAL_QUADRATIC_DRAG_PER_METER, gain),
				coupledAxisDynamicPressureDrag(bodyVelocity.y(), speed, ACRO_VERTICAL_QUADRATIC_DRAG_PER_METER, gain),
				coupledAxisDynamicPressureDrag(bodyVelocity.z(), speed, ACRO_FORWARD_QUADRATIC_DRAG_PER_METER, gain)
		);
	}

	private static float coupledAxisDynamicPressureDrag(float axisVelocity, float totalSpeed, float quadraticDragPerMeter, float gain) {
		float axisSpeed = Math.abs(axisVelocity);
		float extraDynamicPressureSpeed = Math.max(0.0f, totalSpeed - axisSpeed);
		if (axisSpeed <= 1.0e-6f || extraDynamicPressureSpeed <= 1.0e-6f) {
			return 0.0f;
		}
		float accelerationMagnitude = quadraticDragPerMeter * axisSpeed * extraDynamicPressureSpeed * gain;
		return -Math.signum(axisVelocity) * clamp(accelerationMagnitude, 0.0f, ACRO_COUPLED_DYNAMIC_PRESSURE_MAX_ACCELERATION);
	}

	static float acroAirframeSeparationIntensity(float bodyRightVelocity, float bodyUpVelocity, float bodyForwardVelocity) {
		return acroAirframeSeparationIntensity(bodyRightVelocity, bodyUpVelocity, bodyForwardVelocity, 1.0f, 1.0f);
	}

	static float acroAirframeSeparationIntensity(
			float bodyRightVelocity,
			float bodyUpVelocity,
			float bodyForwardVelocity,
			float crossflowLag,
			float sidewashMemory
	) {
		float speedSquared = bodyRightVelocity * bodyRightVelocity + bodyUpVelocity * bodyUpVelocity + bodyForwardVelocity * bodyForwardVelocity;
		if (speedSquared <= 1.0e-6f) {
			return 0.0f;
		}
		float lag = sanitizedCrossflowLag(crossflowLag);
		float forwardReference = Math.max(2.0f, Math.abs(bodyForwardVelocity));
		float angleOfAttack = (float) Math.atan2(bodyUpVelocity, forwardReference);
		float sideslip = (float) Math.atan2(bodyRightVelocity, forwardReference);
		float pitchSeparation = smoothStep((Math.abs(angleOfAttack) - ACRO_SEPARATION_AOA_START_RADIANS)
				/ Math.max(0.001f, ACRO_SEPARATION_AOA_FULL_RADIANS - ACRO_SEPARATION_AOA_START_RADIANS));
		float yawSeparation = smoothStep((Math.abs(sideslip) - ACRO_SEPARATION_SIDESLIP_START_RADIANS)
				/ Math.max(0.001f, ACRO_SEPARATION_SIDESLIP_FULL_RADIANS - ACRO_SEPARATION_SIDESLIP_START_RADIANS));
		pitchSeparation *= lag;
		yawSeparation *= acroSidewashForceResponse(lag, sidewashMemory);
		return clamp(1.0f - (1.0f - pitchSeparation) * (1.0f - yawSeparation), 0.0f, 1.0f);
	}

	static Velocity acroPitchPlaneLiftAcceleration(Velocity bodyVelocity, float separation) {
		float pitchPlaneSpeed = horizontalMagnitude(bodyVelocity.y(), bodyVelocity.z());
		if (pitchPlaneSpeed <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float speedSquared = bodyVelocity.x() * bodyVelocity.x() + bodyVelocity.y() * bodyVelocity.y() + bodyVelocity.z() * bodyVelocity.z();
		if (speedSquared <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float angleOfAttack = (float) Math.atan2(bodyVelocity.y(), bodyVelocity.z());
		float pitchStall = smoothStep((Math.abs(angleOfAttack) - ACRO_PITCH_LIFT_AOA_STALL_START_RADIANS)
				/ Math.max(0.001f, ACRO_PITCH_LIFT_AOA_STALL_FULL_RADIANS - ACRO_PITCH_LIFT_AOA_STALL_START_RADIANS));
		float dynamicPitchStall = Math.max(0.32f * pitchStall, clamp(separation, 0.0f, 1.0f) * pitchStall);
		float stallScale = 1.0f - 0.55f * dynamicPitchStall;
		float liftCoefficient = ACRO_PITCH_LIFT_GAIN
				* (float) Math.sqrt(Math.max(0.0f, ACRO_VERTICAL_QUADRATIC_DRAG_PER_METER * ACRO_FORWARD_QUADRATIC_DRAG_PER_METER));
		float liftMagnitude = liftCoefficient * speedSquared * (float) Math.sin(2.0f * angleOfAttack) * stallScale;
		return new Velocity(
				0.0f,
				bodyVelocity.z() / pitchPlaneSpeed * liftMagnitude,
				-bodyVelocity.y() / pitchPlaneSpeed * liftMagnitude
		);
	}

	static Velocity acroSideslipSideforceAcceleration(Velocity bodyVelocity, float separation) {
		float yawPlaneSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		if (yawPlaneSpeed <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float sideslip = (float) Math.atan2(bodyVelocity.x(), bodyVelocity.z());
		float yawStall = smoothStep((Math.abs(sideslip) - ACRO_SIDEFORCE_SIDESLIP_STALL_START_RADIANS)
				/ Math.max(0.001f, ACRO_SIDEFORCE_SIDESLIP_STALL_FULL_RADIANS - ACRO_SIDEFORCE_SIDESLIP_STALL_START_RADIANS));
		float dynamicYawStall = Math.max(0.32f * yawStall, clamp(separation, 0.0f, 1.0f) * yawStall);
		float stallScale = 1.0f - 0.50f * dynamicYawStall;
		float sideforceCoefficient = ACRO_SIDEFORCE_GAIN
				* (float) Math.sqrt(Math.max(0.0f, ACRO_LATERAL_QUADRATIC_DRAG_PER_METER * ACRO_FORWARD_QUADRATIC_DRAG_PER_METER));
		float sideforceMagnitude = sideforceCoefficient * yawPlaneSpeed * yawPlaneSpeed * (float) Math.sin(2.0f * sideslip) * stallScale;
		return new Velocity(
				-bodyVelocity.z() / yawPlaneSpeed * sideforceMagnitude,
				0.0f,
				bodyVelocity.x() / yawPlaneSpeed * sideforceMagnitude
		);
	}

	static Velocity acroSideslipInducedDragAcceleration(Velocity bodyVelocity, Velocity sideforceAcceleration) {
		float yawPlaneSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		float sideforceMagnitude = horizontalMagnitude(sideforceAcceleration.x(), sideforceAcceleration.z());
		if (yawPlaneSpeed <= 1.0e-6f || sideforceMagnitude <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), Math.max(2.0f, Math.abs(bodyVelocity.z())));
		float sideslipExposure = smoothStep((sideslip - ACRO_SIDEFORCE_INDUCED_DRAG_START_RADIANS)
				/ Math.max(0.001f, ACRO_SIDEFORCE_INDUCED_DRAG_FULL_RADIANS - ACRO_SIDEFORCE_INDUCED_DRAG_START_RADIANS));
		if (sideslipExposure <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float dragMagnitude = clamp(
				sideforceMagnitude * ACRO_SIDEFORCE_INDUCED_DRAG_GAIN * sideslipExposure,
				0.0f,
				ACRO_SIDEFORCE_INDUCED_DRAG_MAX_ACCELERATION
		);
		if (dragMagnitude <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		return new Velocity(
				-bodyVelocity.x() / yawPlaneSpeed * dragMagnitude,
				0.0f,
				-bodyVelocity.z() / yawPlaneSpeed * dragMagnitude
		);
	}

	static Velocity acroSideslipInducedDragAcceleration(Velocity bodyVelocity, Velocity sideforceAcceleration, float sidewashResponse) {
		float response = clamp(finiteOrZero(sidewashResponse), 0.0f, 1.0f);
		return scaleVelocity(acroSideslipInducedDragAcceleration(bodyVelocity, sideforceAcceleration), response * response);
	}

	static Velocity acroYawTurnLoadBodyAcceleration(Velocity bodyVelocity, float yawDegreesPerTick) {
		return acroYawTurnLoadBodyAcceleration(bodyVelocity, yawDegreesPerTick, 1.0f);
	}

	static Velocity acroYawTurnLoadBodyAcceleration(Velocity bodyVelocity, float yawDegreesPerTick, float acroAeroCrossflowLag) {
		return acroYawTurnLoadBodyAcceleration(bodyVelocity, yawDegreesPerTick, acroAeroCrossflowLag, acroAeroCrossflowLag);
	}

	static Velocity acroYawTurnLoadBodyAcceleration(
			Velocity bodyVelocity,
			float yawDegreesPerTick,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float horizontalSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		float yawRateDegreesPerSecond = Math.abs(yawDegreesPerTick) / PLAYABLE_TICK_SECONDS;
		if (horizontalSpeed <= ACRO_YAW_TURN_LOAD_SPEED_START_METERS_PER_SECOND
				|| yawRateDegreesPerSecond <= ACRO_YAW_TURN_LOAD_RATE_START_DEGREES_PER_SECOND) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float speedExposure = smoothStep((horizontalSpeed - ACRO_YAW_TURN_LOAD_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_YAW_TURN_LOAD_SPEED_FULL_METERS_PER_SECOND - ACRO_YAW_TURN_LOAD_SPEED_START_METERS_PER_SECOND));
		float yawRateExposure = smoothStep((yawRateDegreesPerSecond - ACRO_YAW_TURN_LOAD_RATE_START_DEGREES_PER_SECOND)
				/ Math.max(0.001f, ACRO_YAW_TURN_LOAD_RATE_FULL_DEGREES_PER_SECOND - ACRO_YAW_TURN_LOAD_RATE_START_DEGREES_PER_SECOND));
		if (speedExposure <= 1.0e-6f || yawRateExposure <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), Math.max(2.0f, Math.abs(bodyVelocity.z())));
		float sideslipExposure = acroYawSidewashExposure(
				smoothStep((sideslip - ACRO_YAW_TURN_LOAD_SIDESLIP_START_RADIANS)
						/ Math.max(0.001f, ACRO_YAW_TURN_LOAD_SIDESLIP_FULL_RADIANS - ACRO_YAW_TURN_LOAD_SIDESLIP_START_RADIANS)),
				acroAeroCrossflowLag,
				acroSidewashMemory
		);
		float yawRateRadiansPerSecond = (float) Math.toRadians(yawRateDegreesPerSecond);
		float turnAcceleration = horizontalSpeed * yawRateRadiansPerSecond;
		float loadMagnitude = turnAcceleration
				* ACRO_YAW_TURN_LOAD_ACCELERATION_GAIN
				* speedExposure
				* yawRateExposure
				* (0.45f + 0.55f * sideslipExposure);
		loadMagnitude = clamp(loadMagnitude, 0.0f, ACRO_YAW_TURN_LOAD_MAX_ACCELERATION);
		if (loadMagnitude <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		return new Velocity(
				-bodyVelocity.x() / horizontalSpeed * loadMagnitude,
				0.0f,
				-bodyVelocity.z() / horizontalSpeed * loadMagnitude
		);
	}

	static Velocity acroBodyRateLoadBodyAcceleration(
			Velocity bodyVelocity,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick,
			float yawDegreesPerTick
	) {
		return acroBodyRateLoadBodyAcceleration(bodyVelocity, pitchRateRadiansPerTick, rollRateRadiansPerTick, yawDegreesPerTick, 1.0f);
	}

	static Velocity acroBodyRateLoadBodyAcceleration(
			Velocity bodyVelocity,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick,
			float yawDegreesPerTick,
			float acroAeroCrossflowLag
	) {
		return acroBodyRateLoadBodyAcceleration(bodyVelocity, pitchRateRadiansPerTick, rollRateRadiansPerTick, yawDegreesPerTick, acroAeroCrossflowLag, acroAeroCrossflowLag);
	}

	static Velocity acroBodyRateLoadBodyAcceleration(
			Velocity bodyVelocity,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick,
			float yawDegreesPerTick,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float speedSquared = bodyVelocity.x() * bodyVelocity.x()
				+ bodyVelocity.y() * bodyVelocity.y()
				+ bodyVelocity.z() * bodyVelocity.z();
		if (speedSquared <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float speed = (float) Math.sqrt(speedSquared);
		if (speed <= ACRO_BODY_RATE_LOAD_SPEED_START_METERS_PER_SECOND) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}

		float pitchRateRadiansPerSecond = finiteOrZero(pitchRateRadiansPerTick) / PLAYABLE_TICK_SECONDS;
		float rollRateRadiansPerSecond = finiteOrZero(rollRateRadiansPerTick) / PLAYABLE_TICK_SECONDS;
		float yawRateRadiansPerSecond = (float) Math.toRadians(finiteOrZero(yawDegreesPerTick) / PLAYABLE_TICK_SECONDS)
				* ACRO_BODY_RATE_LOAD_YAW_WEIGHT;
		float rateMagnitude = (float) Math.sqrt(
				pitchRateRadiansPerSecond * pitchRateRadiansPerSecond
						+ rollRateRadiansPerSecond * rollRateRadiansPerSecond
						+ yawRateRadiansPerSecond * yawRateRadiansPerSecond
		);
		if (rateMagnitude <= ACRO_BODY_RATE_LOAD_RATE_START_RADIANS_PER_SECOND) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}

		float omegaCrossVelocityX = yawRateRadiansPerSecond * bodyVelocity.z() - rollRateRadiansPerSecond * bodyVelocity.y();
		float omegaCrossVelocityY = rollRateRadiansPerSecond * bodyVelocity.x() - pitchRateRadiansPerSecond * bodyVelocity.z();
		float omegaCrossVelocityZ = pitchRateRadiansPerSecond * bodyVelocity.y() - yawRateRadiansPerSecond * bodyVelocity.x();
		float apparentAcceleration = (float) Math.sqrt(
				omegaCrossVelocityX * omegaCrossVelocityX
						+ omegaCrossVelocityY * omegaCrossVelocityY
						+ omegaCrossVelocityZ * omegaCrossVelocityZ
		);
		if (apparentAcceleration <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}

		float speedExposure = smoothStep((speed - ACRO_BODY_RATE_LOAD_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_BODY_RATE_LOAD_SPEED_FULL_METERS_PER_SECOND - ACRO_BODY_RATE_LOAD_SPEED_START_METERS_PER_SECOND));
		float rateExposure = smoothStep((rateMagnitude - ACRO_BODY_RATE_LOAD_RATE_START_RADIANS_PER_SECOND)
				/ Math.max(0.001f, ACRO_BODY_RATE_LOAD_RATE_FULL_RADIANS_PER_SECOND - ACRO_BODY_RATE_LOAD_RATE_START_RADIANS_PER_SECOND));
		float forwardReference = Math.max(2.0f, Math.abs(bodyVelocity.z()));
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), forwardReference);
		float angleOfAttack = (float) Math.atan2(Math.abs(bodyVelocity.y()), forwardReference);
		float sideslipExposure = smoothStep((sideslip - ACRO_BODY_RATE_LOAD_CROSSFLOW_START_RADIANS)
				/ Math.max(0.001f, ACRO_BODY_RATE_LOAD_CROSSFLOW_FULL_RADIANS - ACRO_BODY_RATE_LOAD_CROSSFLOW_START_RADIANS));
		float angleOfAttackExposure = smoothStep((angleOfAttack - ACRO_BODY_RATE_LOAD_CROSSFLOW_START_RADIANS)
				/ Math.max(0.001f, ACRO_BODY_RATE_LOAD_CROSSFLOW_FULL_RADIANS - ACRO_BODY_RATE_LOAD_CROSSFLOW_START_RADIANS));
		float lag = sanitizedCrossflowLag(acroAeroCrossflowLag);
		float crossflowWeight = ACRO_BODY_RATE_LOAD_STRAIGHT_FLOW_WEIGHT
				+ (1.0f - ACRO_BODY_RATE_LOAD_STRAIGHT_FLOW_WEIGHT)
				* Math.max(
						clamp(sideslipExposure, 0.0f, 1.0f) * acroSidewashForceResponse(lag, acroSidewashMemory),
						clamp(angleOfAttackExposure, 0.0f, 1.0f) * lag
				);
		float loadMagnitude = clamp(
				apparentAcceleration
						* ACRO_BODY_RATE_LOAD_ACCELERATION_GAIN
						* speedExposure
						* rateExposure
						* crossflowWeight,
				0.0f,
				ACRO_BODY_RATE_LOAD_MAX_ACCELERATION
		);
		if (loadMagnitude <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		return new Velocity(
				-bodyVelocity.x() / speed * loadMagnitude,
				-bodyVelocity.y() / speed * loadMagnitude,
				-bodyVelocity.z() / speed * loadMagnitude
		);
	}

	static Velocity acroThrustVectorTurnLoadAcceleration(
			float velocityX,
			float velocityZ,
			float thrustAccelerationX,
			float thrustAccelerationZ
	) {
		float horizontalSpeed = horizontalMagnitude(velocityX, velocityZ);
		float thrustHorizontalAcceleration = horizontalMagnitude(thrustAccelerationX, thrustAccelerationZ);
		if (horizontalSpeed <= ACRO_THRUST_TURN_LOAD_SPEED_START_METERS_PER_SECOND
				|| thrustHorizontalAcceleration <= ACRO_THRUST_TURN_LOAD_ACCELERATION_START) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float perpendicularAcceleration = Math.abs(velocityX * thrustAccelerationZ - velocityZ * thrustAccelerationX)
				/ Math.max(1.0e-6f, horizontalSpeed);
		if (perpendicularAcceleration <= ACRO_THRUST_TURN_LOAD_ACCELERATION_START) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float speedExposure = smoothStep((horizontalSpeed - ACRO_THRUST_TURN_LOAD_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_THRUST_TURN_LOAD_SPEED_FULL_METERS_PER_SECOND - ACRO_THRUST_TURN_LOAD_SPEED_START_METERS_PER_SECOND));
		float turnExposure = smoothStep((perpendicularAcceleration - ACRO_THRUST_TURN_LOAD_ACCELERATION_START)
				/ Math.max(0.001f, ACRO_THRUST_TURN_LOAD_ACCELERATION_FULL - ACRO_THRUST_TURN_LOAD_ACCELERATION_START));
		float loadMagnitude = clamp(
				perpendicularAcceleration * ACRO_THRUST_TURN_LOAD_GAIN * speedExposure * turnExposure,
				0.0f,
				ACRO_THRUST_TURN_LOAD_MAX_ACCELERATION
		);
		if (loadMagnitude <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		return new Velocity(
				-velocityX / horizontalSpeed * loadMagnitude,
				0.0f,
				-velocityZ / horizontalSpeed * loadMagnitude
		);
	}

	static Velocity acroRotorSidewashTurnAcceleration(
			float velocityX,
			float velocityZ,
			float thrustAccelerationX,
			float thrustAccelerationZ,
			Velocity bodyVelocity,
			float acroAeroCrossflowLag
	) {
		return acroRotorSidewashTurnAcceleration(
				velocityX,
				velocityZ,
				thrustAccelerationX,
				thrustAccelerationZ,
				bodyVelocity,
				acroAeroCrossflowLag,
				acroAeroCrossflowLag
		);
	}

	static Velocity acroRotorSidewashTurnAcceleration(
			float velocityX,
			float velocityZ,
			float thrustAccelerationX,
			float thrustAccelerationZ,
			Velocity bodyVelocity,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float horizontalSpeed = horizontalMagnitude(velocityX, velocityZ);
		if (horizontalSpeed <= ACRO_ROTOR_SIDEWASH_TURN_SPEED_START_METERS_PER_SECOND) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float signedPerpendicularAcceleration = (velocityX * thrustAccelerationZ - velocityZ * thrustAccelerationX)
				/ Math.max(1.0e-6f, horizontalSpeed);
		float perpendicularAcceleration = Math.abs(signedPerpendicularAcceleration);
		if (perpendicularAcceleration <= ACRO_ROTOR_SIDEWASH_TURN_ACCELERATION_START) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float speedExposure = smoothStep((horizontalSpeed - ACRO_ROTOR_SIDEWASH_TURN_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_ROTOR_SIDEWASH_TURN_SPEED_FULL_METERS_PER_SECOND - ACRO_ROTOR_SIDEWASH_TURN_SPEED_START_METERS_PER_SECOND));
		float turnExposure = smoothStep((perpendicularAcceleration - ACRO_ROTOR_SIDEWASH_TURN_ACCELERATION_START)
				/ Math.max(0.001f, ACRO_ROTOR_SIDEWASH_TURN_ACCELERATION_FULL - ACRO_ROTOR_SIDEWASH_TURN_ACCELERATION_START));
		float flowWeight = ACRO_ROTOR_SIDEWASH_TURN_STRAIGHT_FLOW_WEIGHT;
		if (bodyVelocity != null) {
			float forwardReference = Math.max(2.0f, Math.abs(bodyVelocity.z()));
			float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), forwardReference);
			float angleOfAttack = (float) Math.atan2(Math.abs(bodyVelocity.y()), forwardReference);
			float sideslipExposure = smoothStep((sideslip - ACRO_ROTOR_SIDEWASH_TURN_CROSSFLOW_START_RADIANS)
					/ Math.max(0.001f, ACRO_ROTOR_SIDEWASH_TURN_CROSSFLOW_FULL_RADIANS - ACRO_ROTOR_SIDEWASH_TURN_CROSSFLOW_START_RADIANS));
			float angleOfAttackExposure = smoothStep((angleOfAttack - ACRO_ROTOR_SIDEWASH_TURN_CROSSFLOW_START_RADIANS)
					/ Math.max(0.001f, ACRO_ROTOR_SIDEWASH_TURN_CROSSFLOW_FULL_RADIANS - ACRO_ROTOR_SIDEWASH_TURN_CROSSFLOW_START_RADIANS));
			float yawCrossflow = acroYawSidewashExposure(sideslipExposure, acroAeroCrossflowLag, acroSidewashMemory);
			float pitchCrossflow = acroPitchLagExposure(angleOfAttackExposure, acroAeroCrossflowLag);
			float crossflowExposure = Math.max(yawCrossflow, pitchCrossflow);
			flowWeight += (1.0f - ACRO_ROTOR_SIDEWASH_TURN_STRAIGHT_FLOW_WEIGHT) * crossflowExposure;
		}
		float accelerationMagnitude = clamp(
				perpendicularAcceleration
						* ACRO_ROTOR_SIDEWASH_TURN_GAIN
						* speedExposure
						* turnExposure
						* flowWeight,
				0.0f,
				ACRO_ROTOR_SIDEWASH_TURN_MAX_ACCELERATION
		);
		if (accelerationMagnitude <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float direction = Math.signum(signedPerpendicularAcceleration);
		return new Velocity(
				-velocityZ / horizontalSpeed * accelerationMagnitude * direction,
				0.0f,
				velocityX / horizontalSpeed * accelerationMagnitude * direction
		);
	}

	static float acroSideslipWeathercockYawDegreesPerTick(Velocity bodyVelocity) {
		float strength = acroWeathercockStrength(bodyVelocity);
		if (strength <= 1.0e-6f) {
			return 0.0f;
		}
		return clamp(
				-Math.signum(bodyVelocity.x()) * strength * ACRO_WEATHERCOCK_YAW_GAIN_DEGREES_PER_TICK,
				-ACRO_WEATHERCOCK_YAW_MAX_DEGREES_PER_TICK,
				ACRO_WEATHERCOCK_YAW_MAX_DEGREES_PER_TICK
		);
	}

	static float acroSideslipYawDampingSmoothing(Velocity bodyVelocity) {
		float strength = acroWeathercockStrength(bodyVelocity);
		if (strength <= 1.0e-6f) {
			return 0.0f;
		}
		return clamp(strength * ACRO_SIDESLIP_YAW_DAMPING_GAIN, 0.0f, ACRO_SIDESLIP_YAW_DAMPING_MAX);
	}

	private static float acroBodyRateYawRate(
			FlightMode mode,
			float yawDegreesPerTick,
			float yawCommand,
			float pitchRadians,
			float rollRadians,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick
	) {
		if (safeMode(mode) != FlightMode.ACRO) {
			return yawDegreesPerTick;
		}
		float yawCoupling = acroBodyRateYawCouplingDegreesPerTick(
				pitchRadians,
				rollRadians,
				pitchRateRadiansPerTick,
				rollRateRadiansPerTick
		);
		if (Math.abs(yawCoupling) <= YAW_SETTLE_EPSILON_DEGREES_PER_TICK) {
			return yawDegreesPerTick;
		}
		float commandSuppression = 1.0f - smoothStep(Math.abs(yawCommand) / ACRO_BODY_RATE_YAW_COMMAND_SUPPRESS);
		if (commandSuppression <= 1.0e-6f) {
			return yawDegreesPerTick;
		}
		yawCoupling *= commandSuppression;
		return settledYawRate(yawDegreesPerTick + yawCoupling, 0.0f);
	}

	static float acroBodyRateYawCouplingDegreesPerTick(
			float pitchRadians,
			float rollRadians,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick
	) {
		if (!Float.isFinite(pitchRateRadiansPerTick) || !Float.isFinite(rollRateRadiansPerTick)) {
			return 0.0f;
		}
		float pitch = signedRotationResidualRadians(pitchRadians);
		float roll = signedRotationResidualRadians(rollRadians);
		float bodyPitchRateRadiansPerTick = pitchRateRadiansPerTick;
		float bodyRollRateRadiansPerTick = rollRateRadiansPerTick;
		float bankedPitchCoupling = bodyPitchRateRadiansPerTick
				* (float) Math.sin(roll)
				* (0.35f + 0.65f * Math.abs((float) Math.cos(pitch)));
		float verticalRollExposure = smoothStep((Math.abs(pitch) - ACRO_BODY_RATE_VERTICAL_ROLL_START_RADIANS)
				/ Math.max(0.001f, ACRO_BODY_RATE_VERTICAL_ROLL_FULL_RADIANS - ACRO_BODY_RATE_VERTICAL_ROLL_START_RADIANS));
		float verticalRollCoupling = bodyRollRateRadiansPerTick
				* (float) Math.sin(pitch)
				* verticalRollExposure
				* ACRO_BODY_RATE_VERTICAL_ROLL_YAW_WEIGHT;
		return clamp(
				(float) Math.toDegrees((bankedPitchCoupling + verticalRollCoupling) * ACRO_BODY_RATE_YAW_COUPLING_SCALE),
				-ACRO_BODY_RATE_YAW_COUPLING_MAX_DEGREES_PER_TICK,
				ACRO_BODY_RATE_YAW_COUPLING_MAX_DEGREES_PER_TICK
		);
	}

	private static float acroAerodynamicYawRate(
			FlightMode mode,
			float yawDegreesPerTick,
			float yawCommand,
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		if (safeMode(mode) != FlightMode.ACRO) {
			return yawDegreesPerTick;
		}
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(velocityX, velocityY, velocityZ, pitchRadians, rollRadians);
		float sidewashResponse = acroSidewashForceResponse(acroAeroCrossflowLag, acroSidewashMemory);
		float commandLoad = acroSideslipYawCommandLoad(bodyVelocity)
				* sidewashResponse
				* smoothStep(Math.abs(yawCommand) / ACRO_SIDESLIP_YAW_LOAD_FULL_COMMAND);
		float loadedYaw = commandLoad <= 1.0e-6f
				? yawDegreesPerTick
				: yawDegreesPerTick * (1.0f - commandLoad);
		float commandSuppression = 1.0f - smoothStep(Math.abs(yawCommand) / ACRO_WEATHERCOCK_YAW_COMMAND_SUPPRESS);
		if (commandSuppression <= 1.0e-6f) {
			return loadedYaw;
		}
		float damping = acroSideslipYawDampingSmoothing(bodyVelocity) * sidewashResponse * commandSuppression;
		float dampedYaw = damping <= 1.0e-6f ? loadedYaw : smooth(loadedYaw, 0.0f, damping);
		float weathercockYaw = acroSideslipWeathercockYawDegreesPerTick(bodyVelocity) * sidewashResponse * commandSuppression;
		return settledYawRate(dampedYaw + weathercockYaw, 0.0f);
	}

	static float acroSideslipYawCommandLoad(Velocity bodyVelocity) {
		float horizontalSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		if (horizontalSpeed <= ACRO_SIDESLIP_YAW_LOAD_SPEED_START_METERS_PER_SECOND
				|| Math.abs(bodyVelocity.x()) <= 1.0e-6f) {
			return 0.0f;
		}
		float speedExposure = smoothStep((horizontalSpeed - ACRO_SIDESLIP_YAW_LOAD_SPEED_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_SIDESLIP_YAW_LOAD_SPEED_FULL_METERS_PER_SECOND - ACRO_SIDESLIP_YAW_LOAD_SPEED_START_METERS_PER_SECOND));
		if (speedExposure <= 1.0e-6f) {
			return 0.0f;
		}
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), Math.max(2.0f, Math.abs(bodyVelocity.z())));
		float sideslipExposure = smoothStep((sideslip - ACRO_SIDESLIP_YAW_LOAD_SIDESLIP_START_RADIANS)
				/ Math.max(0.001f, ACRO_SIDESLIP_YAW_LOAD_SIDESLIP_FULL_RADIANS - ACRO_SIDESLIP_YAW_LOAD_SIDESLIP_START_RADIANS));
		return ACRO_SIDESLIP_YAW_LOAD_MAX_ACTIVE_LOSS * speedExposure * sideslipExposure;
	}

	private static float acroWeathercockStrength(Velocity bodyVelocity) {
		float lateralSpeed = Math.abs(bodyVelocity.x());
		float positiveForwardSpeed = Math.max(0.0f, bodyVelocity.z());
		float horizontalSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		if (lateralSpeed <= 1.0e-6f || horizontalSpeed <= ACRO_WEATHERCOCK_FORWARD_START_METERS_PER_SECOND) {
			return 0.0f;
		}
		float speedSquared = bodyVelocity.x() * bodyVelocity.x() + bodyVelocity.y() * bodyVelocity.y() + bodyVelocity.z() * bodyVelocity.z();
		float sideslip = (float) Math.atan2(bodyVelocity.x(), Math.max(2.0f, positiveForwardSpeed));
		float sideslipExposure = smoothStep((Math.abs(sideslip) - ACRO_WEATHERCOCK_SIDESLIP_START_RADIANS)
				/ Math.max(0.001f, ACRO_WEATHERCOCK_SIDESLIP_FULL_RADIANS - ACRO_WEATHERCOCK_SIDESLIP_START_RADIANS));
		float forwardExposure = smoothStep((positiveForwardSpeed - ACRO_WEATHERCOCK_FORWARD_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_WEATHERCOCK_FORWARD_FULL_METERS_PER_SECOND - ACRO_WEATHERCOCK_FORWARD_START_METERS_PER_SECOND));
		float forwardPresence = smoothStep(positiveForwardSpeed / Math.max(0.001f, ACRO_WEATHERCOCK_FORWARD_START_METERS_PER_SECOND));
		float lateralExposure = smoothStep((lateralSpeed - ACRO_WEATHERCOCK_LATERAL_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_WEATHERCOCK_LATERAL_FULL_METERS_PER_SECOND - ACRO_WEATHERCOCK_LATERAL_START_METERS_PER_SECOND));
		float broadsideExposure = smoothStep((horizontalSpeed - ACRO_WEATHERCOCK_FORWARD_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_WEATHERCOCK_FORWARD_FULL_METERS_PER_SECOND - ACRO_WEATHERCOCK_FORWARD_START_METERS_PER_SECOND));
		float weathercockArea = (float) Math.sqrt(Math.max(0.0f, ACRO_LATERAL_QUADRATIC_DRAG_PER_METER * ACRO_FORWARD_QUADRATIC_DRAG_PER_METER));
		float forwardSlipExposure = sideslipExposure
				* (0.45f + 0.35f * forwardExposure + 0.20f * lateralExposure)
				* forwardPresence;
		float broadsideSlipExposure = sideslipExposure
				* broadsideExposure
				* (ACRO_WEATHERCOCK_BROADSIDE_BASE + ACRO_WEATHERCOCK_BROADSIDE_LATERAL_WEIGHT * lateralExposure)
				* (1.0f - forwardPresence);
		float exposure = Math.max(forwardSlipExposure, broadsideSlipExposure);
		return Math.max(0.0f, speedSquared * weathercockArea * exposure);
	}

	private static Velocity acroSeparatedFlowDragAcceleration(Velocity bodyVelocity, float separation) {
		float speedSquared = bodyVelocity.x() * bodyVelocity.x() + bodyVelocity.y() * bodyVelocity.y() + bodyVelocity.z() * bodyVelocity.z();
		if (speedSquared <= 1.0e-6f || separation <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float speed = (float) Math.sqrt(speedSquared);
		float maxBroadsideDrag = Math.max(ACRO_LATERAL_QUADRATIC_DRAG_PER_METER, ACRO_VERTICAL_QUADRATIC_DRAG_PER_METER);
		float broadsideCoefficient = 0.20f * maxBroadsideDrag
				+ 0.14f * (float) Math.sqrt(Math.max(
						0.0f,
						(ACRO_LATERAL_QUADRATIC_DRAG_PER_METER + ACRO_VERTICAL_QUADRATIC_DRAG_PER_METER)
								* ACRO_FORWARD_QUADRATIC_DRAG_PER_METER
				));
		float separatedDragMagnitude = speedSquared * broadsideCoefficient * clamp(separation, 0.0f, 1.0f);
		return new Velocity(
				-bodyVelocity.x() / speed * separatedDragMagnitude,
				-bodyVelocity.y() / speed * separatedDragMagnitude,
				-bodyVelocity.z() / speed * separatedDragMagnitude
		);
	}

	static Velocity acroBodyVelocityForYawLocal(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians) {
		AcroBodyFrame frame = acroBodyFrame(pitchRadians, rollRadians);
		return new Velocity(
				dot(velocityX, velocityY, velocityZ, frame.right()),
				dot(velocityX, velocityY, velocityZ, frame.up()),
				dot(velocityX, velocityY, velocityZ, frame.forward())
		);
	}

	static Velocity yawLocalVelocityForAcroBody(float bodyRight, float bodyUp, float bodyForward, float pitchRadians, float rollRadians) {
		AcroBodyFrame frame = acroBodyFrame(pitchRadians, rollRadians);
		return new Velocity(
				frame.right().x() * bodyRight + frame.up().x() * bodyUp + frame.forward().x() * bodyForward,
				frame.right().y() * bodyRight + frame.up().y() * bodyUp + frame.forward().y() * bodyForward,
				frame.right().z() * bodyRight + frame.up().z() * bodyUp + frame.forward().z() * bodyForward
		);
	}

	private static AcroBodyFrame acroBodyFrame(float pitchRadians, float rollRadians) {
		float pitch = Float.isFinite(pitchRadians) ? pitchRadians : 0.0f;
		float roll = Float.isFinite(rollRadians) ? rollRadians : 0.0f;
		float sinPitch = (float) Math.sin(pitch);
		float cosPitch = (float) Math.cos(pitch);
		float sinRoll = (float) Math.sin(roll);
		float cosRoll = (float) Math.cos(roll);
		return new AcroBodyFrame(
				new Velocity(cosRoll, cosPitch * sinRoll, sinPitch * sinRoll),
				new Velocity(-sinRoll, cosPitch * cosRoll, sinPitch * cosRoll),
				new Velocity(0.0f, -sinPitch, cosPitch)
		);
	}

	private static float dragAcceleration(float velocity, float linearDragPerSecond, float quadraticDragPerMeter) {
		return velocity * (linearDragPerSecond + quadraticDragPerMeter * Math.abs(velocity));
	}

	private static float bodyQuadraticDragPerMeter(float dragAreaSquareMeters) {
		return 0.5f * ACRO_AIR_DENSITY_KILOGRAMS_PER_CUBIC_METER * dragAreaSquareMeters / ACRO_REFERENCE_MASS_KILOGRAMS;
	}

	private static float dot(float x, float y, float z, Velocity axis) {
		return x * axis.x() + y * axis.y() + z * axis.z();
	}

	private static Velocity rotateAroundAxis(Velocity vector, Velocity unitAxis, float radians) {
		float cos = (float) Math.cos(radians);
		float sin = (float) Math.sin(radians);
		float dot = vector.x() * unitAxis.x() + vector.y() * unitAxis.y() + vector.z() * unitAxis.z();
		float crossX = unitAxis.y() * vector.z() - unitAxis.z() * vector.y();
		float crossY = unitAxis.z() * vector.x() - unitAxis.x() * vector.z();
		float crossZ = unitAxis.x() * vector.y() - unitAxis.y() * vector.x();
		return new Velocity(
				vector.x() * cos + crossX * sin + unitAxis.x() * dot * (1.0f - cos),
				vector.y() * cos + crossY * sin + unitAxis.y() * dot * (1.0f - cos),
				vector.z() * cos + crossZ * sin + unitAxis.z() * dot * (1.0f - cos)
		);
	}

	private static Velocity limitHorizontalVector(float velocityX, float velocityY, float velocityZ, float limitMetersPerSecond) {
		float limit = Math.max(0.0f, limitMetersPerSecond);
		float horizontalSpeed = horizontalMagnitude(velocityX, velocityZ);
		if (horizontalSpeed <= limit || horizontalSpeed <= 1.0e-6f) {
			return new Velocity(velocityX, velocityY, velocityZ);
		}
		float scale = limit / horizontalSpeed;
		return new Velocity(velocityX * scale, velocityY, velocityZ * scale);
	}

	private static Velocity scaleVelocity(Velocity velocity, float scale) {
		float safeScale = Float.isFinite(scale) ? scale : 0.0f;
		return new Velocity(
				velocity.x() * safeScale,
				velocity.y() * safeScale,
				velocity.z() * safeScale
		);
	}

	private static float horizontalVelocityHardLimit(FlightMode mode, Profile profile) {
		return safeMode(mode) == FlightMode.ACRO
				? acroHorizontalHardLimit(profile)
				: profile.horizontalSpeedLimitMetersPerSecond();
	}

	private static float acroHorizontalHardLimit(Profile profile) {
		return profile.horizontalSpeedLimitMetersPerSecond() * ACRO_OVERSPEED_HARD_LIMIT_SCALE;
	}

	private static float horizontalMagnitude(float x, float z) {
		return (float) Math.sqrt(x * x + z * z);
	}

	private static float lowAltitudeAttitudeCommandAuthority(FlightMode mode, boolean nearGroundLocked, float lowAltitudeHorizontalAuthorityScale) {
		float minimum = switch (safeMode(mode)) {
			case ANGLE -> 0.68f;
			case HORIZON -> 0.76f;
			case ACRO -> 0.90f;
		};
		return lowAltitudeCommandAuthority(minimum, nearGroundLocked, lowAltitudeHorizontalAuthorityScale);
	}

	private static float lowAltitudeYawCommandAuthority(FlightMode mode, boolean nearGroundLocked, float lowAltitudeHorizontalAuthorityScale) {
		float minimum = switch (safeMode(mode)) {
			case ANGLE -> 0.66f;
			case HORIZON -> 0.72f;
			case ACRO -> 0.86f;
		};
		return lowAltitudeCommandAuthority(minimum, nearGroundLocked, lowAltitudeHorizontalAuthorityScale);
	}

	private static float lowAltitudeCommandAuthority(float minimum, boolean nearGroundLocked, float lowAltitudeHorizontalAuthorityScale) {
		float safeMinimum = clamp(minimum, 0.0f, 1.0f);
		if (nearGroundLocked) {
			return safeMinimum;
		}
		return lerp(safeMinimum, 1.0f, lowAltitudeHorizontalAuthorityScale);
	}

	private static float groundHorizontalAuthorityScale(FlightMode mode) {
		return switch (safeMode(mode)) {
			case ANGLE -> GROUND_ANGLE_HORIZONTAL_AUTHORITY_SCALE;
			case HORIZON -> GROUND_HORIZON_HORIZONTAL_AUTHORITY_SCALE;
			case ACRO -> GROUND_ACRO_HORIZONTAL_AUTHORITY_SCALE;
		};
	}

	private static float smooth(float current, float target, float smoothing) {
		return current + (target - current) * clamp(smoothing, 0.0f, 1.0f);
	}

	private static float inertialVelocity(float current, float target, float smoothing, float acceleration, float brakeAcceleration) {
		float unconstrained = smooth(current, target, smoothing);
		float accelerationLimit = isVelocityBraking(current, target) ? brakeAcceleration : acceleration;
		float maxDelta = Math.max(0.0f, accelerationLimit) * PLAYABLE_TICK_SECONDS;
		return current + clamp(unconstrained - current, -maxDelta, maxDelta);
	}

	private static float velocitySmoothing(float current, float target, Profile profile) {
		return isVelocityBraking(current, target) ? profile.velocityBrakeSmoothing() : profile.velocitySmoothing();
	}

	private static boolean isVelocityBraking(float current, float target) {
		boolean reversing = Math.signum(current) != 0.0f
				&& Math.signum(target) != 0.0f
				&& Math.signum(current) != Math.signum(target);
		return reversing || Math.abs(target) < Math.abs(current);
	}

	private static float verticalVelocitySmoothing(float current, float target, Profile profile) {
		float smoothing = velocitySmoothing(current, target, profile);
		if (Math.abs(target) <= VELOCITY_SETTLE_EPSILON_MPS && Math.abs(current) > VELOCITY_SETTLE_EPSILON_MPS) {
			return Math.max(smoothing, VERTICAL_HOVER_BRAKE_SMOOTHING);
		}
		return smoothing;
	}

	private static boolean shouldGroundDamp(float throttle, float hoverThrottle, float targetVelocityX, float targetVelocityZ, Profile profile) {
		return throttle <= hoverThrottle + profile.hoverBand()
				&& Math.abs(targetVelocityX) <= profile.groundFrictionTargetVelocityThreshold()
				&& Math.abs(targetVelocityZ) <= profile.groundFrictionTargetVelocityThreshold();
	}

	private static boolean shouldGroundCatchVertical(float throttle, float hoverThrottle, float targetVelocityY, Profile profile) {
		return throttle <= hoverThrottle + profile.hoverBand()
				&& targetVelocityY <= 0.0f;
	}

	private static boolean shouldAirBrake(FlightMode mode, float throttle, float hoverThrottle, float pitch, float roll, Profile profile) {
		return mode != FlightMode.ACRO
				&& profile.airBrakeSmoothing() > 0.0f
				&& throttle >= hoverThrottle - profile.airBrakeThrottleBand()
				&& Math.abs(pitch) <= profile.airBrakeCommandThreshold()
				&& Math.abs(roll) <= profile.airBrakeCommandThreshold();
	}

	private static float settledAttitude(FlightMode mode, float command, float radians) {
		if (mode != FlightMode.ACRO && Math.abs(command) <= PLAYABLE_AXIS_NOISE_EPSILON && Math.abs(radians) <= ATTITUDE_SETTLE_EPSILON_RADIANS) {
			return 0.0f;
		}
		return radians;
	}

	private static float settledVelocity(float velocity, float targetVelocity) {
		if (Math.abs(targetVelocity) <= VELOCITY_SETTLE_EPSILON_MPS && Math.abs(velocity) <= VELOCITY_SETTLE_EPSILON_MPS) {
			return 0.0f;
		}
		return velocity;
	}

	private static float settledYawRate(float yawDegreesPerTick, float targetYawDegreesPerTick) {
		if (Math.abs(targetYawDegreesPerTick) <= YAW_SETTLE_EPSILON_DEGREES_PER_TICK
				&& Math.abs(yawDegreesPerTick) <= YAW_SETTLE_EPSILON_DEGREES_PER_TICK) {
			return 0.0f;
		}
		return yawDegreesPerTick;
	}

	private static float yawRateStep(
			FlightMode mode,
			State previous,
			float safeYaw,
			float yawCommand,
			float targetYawDegreesPerTick,
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag,
			float acroSidewashMemory,
			float acroPitchRateRadiansPerTick,
			float acroRollRateRadiansPerTick,
			Profile profile
	) {
		float yawDegreesPerTick = smooth(
				previous.yawDegreesPerTick(),
				targetYawDegreesPerTick,
				yawSmoothing(
						mode,
						previous.yawDegreesPerTick(),
						targetYawDegreesPerTick,
						profile,
						velocityX,
						velocityY,
						velocityZ,
						pitchRadians,
						rollRadians,
						throttle,
						hoverThrottle,
						acroAeroCrossflowLag,
						acroSidewashMemory
				)
		);
		if (shouldModeSwitchBrakeYaw(previous, safeYaw)) {
			yawDegreesPerTick = smooth(yawDegreesPerTick, 0.0f, modeSwitchYawBrake(mode));
		}
		yawDegreesPerTick = settledYawRate(yawDegreesPerTick, targetYawDegreesPerTick);
		yawDegreesPerTick = acroBodyRateYawRate(
				mode,
				yawDegreesPerTick,
				yawCommand,
				pitchRadians,
				rollRadians,
				acroPitchRateRadiansPerTick,
				acroRollRateRadiansPerTick
		);
		return acroAerodynamicYawRate(
				mode,
				yawDegreesPerTick,
				yawCommand,
				velocityX,
				velocityY,
				velocityZ,
				pitchRadians,
				rollRadians,
				acroAeroCrossflowLag,
				acroSidewashMemory
		);
	}

	private static float yawSmoothing(float current, float target, Profile profile) {
		boolean braking = Math.abs(target) < Math.abs(current)
				|| (Math.signum(current) != 0.0f && Math.signum(target) != 0.0f && Math.signum(current) != Math.signum(target));
		return braking ? profile.yawBrakeSmoothing() : profile.yawSmoothing();
	}

	private static float yawSmoothing(
			FlightMode mode,
			float current,
			float target,
			Profile profile,
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			float throttle,
			float hoverThrottle,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		float smoothing = yawSmoothing(current, target, profile);
		if (safeMode(mode) != FlightMode.ACRO) {
			return smoothing;
		}
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(velocityX, velocityY, velocityZ, pitchRadians, rollRadians);
		return smoothing * acroYawRateInertiaSmoothingScale(bodyVelocity, throttle, hoverThrottle, acroAeroCrossflowLag, acroSidewashMemory);
	}

	private static float smoothLimited(float current, float target, float smoothing, float maxStep) {
		float next = smooth(current, target, smoothing);
		float delta = clamp(next - current, -Math.max(0.0f, maxStep), Math.max(0.0f, maxStep));
		return current + delta;
	}

	private static float lerp(float a, float b, float t) {
		float clamped = clamp(t, 0.0f, 1.0f);
		return a + (b - a) * clamped;
	}

	private static float smoothStep(float value) {
		float clamped = clamp(value, 0.0f, 1.0f);
		return clamped * clamped * (3.0f - 2.0f * clamped);
	}

	private static float clamp(float value, float min, float max) {
		if (!Float.isFinite(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}

	private static float finiteOrZero(float value) {
		return Float.isFinite(value) ? value : 0.0f;
	}

	private static FlightMode safeMode(FlightMode mode) {
		return mode == null ? DEFAULT_PLAYABLE_MODE : mode;
	}

	record State(
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			float yawDegreesPerTick,
			FlightMode mode,
			int modeSwitchTicksRemaining,
			float acroCollectiveThrustToWeight,
			float acroPitchRateRadiansPerTick,
			float acroRollRateRadiansPerTick,
			int acroRollRecoveryTicksRemaining,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
		static final State ZERO = zero(DEFAULT_PLAYABLE_MODE);

		State {
			mode = safeMode(mode);
			modeSwitchTicksRemaining = Math.max(0, modeSwitchTicksRemaining);
			acroRollRecoveryTicksRemaining = Math.max(0, acroRollRecoveryTicksRemaining);
			acroCollectiveThrustToWeight = Float.isFinite(acroCollectiveThrustToWeight)
					? clamp(acroCollectiveThrustToWeight, 0.0f, ACRO_FULL_THROTTLE_THRUST_TO_WEIGHT)
					: Float.NaN;
			acroPitchRateRadiansPerTick = Float.isFinite(acroPitchRateRadiansPerTick) ? acroPitchRateRadiansPerTick : Float.NaN;
			acroRollRateRadiansPerTick = Float.isFinite(acroRollRateRadiansPerTick) ? acroRollRateRadiansPerTick : Float.NaN;
			acroAeroCrossflowLag = Float.isFinite(acroAeroCrossflowLag) ? clamp(acroAeroCrossflowLag, 0.0f, 1.0f) : 0.0f;
			acroSidewashMemory = Float.isFinite(acroSidewashMemory) ? clamp(acroSidewashMemory, 0.0f, 1.0f) : 0.0f;
		}

		State(float velocityX, float velocityY, float velocityZ) {
			this(velocityX, velocityY, velocityZ, 0.0f, 0.0f, 0.0f, DEFAULT_PLAYABLE_MODE, 0, Float.NaN, Float.NaN, Float.NaN, 0);
		}

		State(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, 0.0f, DEFAULT_PLAYABLE_MODE, 0, Float.NaN, Float.NaN, Float.NaN, 0);
		}

		State(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians, float yawDegreesPerTick) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, yawDegreesPerTick, DEFAULT_PLAYABLE_MODE, 0, Float.NaN, Float.NaN, Float.NaN, 0);
		}

		State(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians, float yawDegreesPerTick, FlightMode mode) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, yawDegreesPerTick, mode, 0, Float.NaN, Float.NaN, Float.NaN, 0);
		}

		State(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians, float yawDegreesPerTick, FlightMode mode, int modeSwitchTicksRemaining) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, yawDegreesPerTick, mode, modeSwitchTicksRemaining, Float.NaN, Float.NaN, Float.NaN, 0);
		}

		State(
				float velocityX,
				float velocityY,
				float velocityZ,
				float pitchRadians,
				float rollRadians,
				float yawDegreesPerTick,
				FlightMode mode,
				int modeSwitchTicksRemaining,
				float acroCollectiveThrustToWeight
		) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, yawDegreesPerTick, mode, modeSwitchTicksRemaining, acroCollectiveThrustToWeight, Float.NaN, Float.NaN, 0);
		}

		State(
				float velocityX,
				float velocityY,
				float velocityZ,
				float pitchRadians,
				float rollRadians,
				float yawDegreesPerTick,
				FlightMode mode,
				int modeSwitchTicksRemaining,
				float acroCollectiveThrustToWeight,
				float acroPitchRateRadiansPerTick,
				float acroRollRateRadiansPerTick
		) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, yawDegreesPerTick, mode, modeSwitchTicksRemaining, acroCollectiveThrustToWeight, acroPitchRateRadiansPerTick, acroRollRateRadiansPerTick, 0);
		}

		State(
				float velocityX,
				float velocityY,
				float velocityZ,
				float pitchRadians,
				float rollRadians,
				float yawDegreesPerTick,
				FlightMode mode,
				int modeSwitchTicksRemaining,
				float acroCollectiveThrustToWeight,
				float acroPitchRateRadiansPerTick,
				float acroRollRateRadiansPerTick,
				int acroRollRecoveryTicksRemaining
		) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, yawDegreesPerTick, mode, modeSwitchTicksRemaining, acroCollectiveThrustToWeight, acroPitchRateRadiansPerTick, acroRollRateRadiansPerTick, acroRollRecoveryTicksRemaining, 0.0f, 0.0f);
		}

		State(
				float velocityX,
				float velocityY,
				float velocityZ,
				float pitchRadians,
				float rollRadians,
				float yawDegreesPerTick,
				FlightMode mode,
				int modeSwitchTicksRemaining,
				float acroCollectiveThrustToWeight,
				float acroPitchRateRadiansPerTick,
				float acroRollRateRadiansPerTick,
				int acroRollRecoveryTicksRemaining,
				float acroAeroCrossflowLag
		) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, yawDegreesPerTick, mode, modeSwitchTicksRemaining, acroCollectiveThrustToWeight, acroPitchRateRadiansPerTick, acroRollRateRadiansPerTick, acroRollRecoveryTicksRemaining, acroAeroCrossflowLag, acroAeroCrossflowLag);
		}

		static State zero(FlightMode mode) {
			return new State(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, safeMode(mode), 0, 0.0f, 0.0f, 0.0f, 0);
		}
	}

	record Step(
			float targetVelocityX,
			float targetVelocityY,
			float targetVelocityZ,
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			float yawDegreesPerTick,
			float motorPower,
			float averageRpm,
			float acroCollectiveThrustToWeight,
			float acroPitchRateRadiansPerTick,
			float acroRollRateRadiansPerTick,
			FlightMode mode,
			int modeSwitchTicksRemaining,
			int acroRollRecoveryTicksRemaining,
			float acroAeroCrossflowLag,
			float acroSidewashMemory
	) {
	}

	record Velocity(float x, float y, float z) {
	}

	private record Attitude(float pitchRadians, float rollRadians) {
	}

	private record AcroRateResponse(
			float bodyPitchRateRadiansPerTick,
			float bodyRollRateRadiansPerTick,
			float eulerPitchRateRadiansPerTick,
			float eulerRollRateRadiansPerTick
	) {
		private static final AcroRateResponse ZERO = new AcroRateResponse(0.0f, 0.0f, 0.0f, 0.0f);
	}

	record AcroBodyRateAttitudeDelta(
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick
	) {
		private static final AcroBodyRateAttitudeDelta ZERO = new AcroBodyRateAttitudeDelta(0.0f, 0.0f);
	}

	private record AcroBodyFrame(Velocity right, Velocity up, Velocity forward) {
	}

	private record Profile(
			float horizontalSpeedMetersPerSecond,
			float horizontalSpeedLimitMetersPerSecond,
			float maxPitchRadians,
			float maxRollRadians,
			float maxAcroPitchRadians,
			float maxAcroRollRadians,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick,
			float yawDegreesPerTick,
			float yawSmoothing,
			float yawBrakeSmoothing,
			float attitudeSmoothing,
			float attitudeStepLimitRadians,
			float attitudeRecenterSmoothing,
			float attitudeRecenterStepLimitRadians,
			float acroHoldDamping,
			float velocitySmoothing,
			float velocityBrakeSmoothing,
			float horizontalAccelerationMetersPerSecondSquared,
			float horizontalBrakeAccelerationMetersPerSecondSquared,
			float verticalAccelerationMetersPerSecondSquared,
			float verticalBrakeAccelerationMetersPerSecondSquared,
			float groundFrictionSmoothing,
			float groundFrictionTargetVelocityThreshold,
			float airBrakeSmoothing,
			float airBrakeCommandThreshold,
			float airBrakeThrottleBand,
			float hoverBand,
			float horizontalFineVelocityScale,
			float horizontalVelocityLinearStart,
			float descentGain,
			float thrustGain
	) {
		private static Profile forMode(FlightMode mode) {
			return switch (safeMode(mode)) {
				case ANGLE -> new Profile(3.20f, 4.40f, radians(24.0f), radians(24.0f), radians(48.0f), radians(48.0f), radians(3.0f), radians(3.2f), 1.75f, 0.58f, 0.78f, 0.24f, radians(2.6f), 0.74f, radians(7.2f), 0.84f, 0.20f, 0.42f, 4.50f, 7.50f, 10.50f, 12.00f, 0.74f, 0.12f, 0.48f, 0.070f, 0.10f, 0.055f, 0.82f, 0.85f, DESCENT_GAIN, 3.60f);
				case HORIZON -> new Profile(8.80f, 12.00f, radians(46.0f), radians(48.0f), radians(80.0f), radians(84.0f), radians(6.3f), radians(6.8f), 3.55f, 0.82f, 0.70f, 0.22f, radians(3.8f), 0.30f, radians(5.2f), 0.93f, 0.18f, 0.28f, 8.50f, 9.50f, 10.80f, 13.00f, 0.56f, 0.16f, 0.06f, 0.060f, 0.09f, HOVER_BAND, 0.92f, 0.78f, 3.00f, THRUST_GAIN);
				case ACRO -> new Profile(25.00f, 32.00f, radians(64.0f), radians(68.0f), radians(115.0f), radians(125.0f), radians(8.8f), radians(9.4f), 5.40f, 0.94f, 0.36f, 0.18f, radians(5.80f), 0.15f, radians(5.20f), 1.0f, 0.28f, 0.18f, 14.00f, 8.00f, 11.50f, 13.50f, 0.34f, 0.18f, 0.0f, 0.0f, 0.0f, 0.030f, 1.0f, 1.0f, 3.40f, 5.00f);
			};
		}

		private static float radians(float degrees) {
			return (float) Math.toRadians(degrees);
		}
	}
}
