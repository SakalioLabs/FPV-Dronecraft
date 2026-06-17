package com.tenicana.dronecraft.entity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.blackbox.DroneBlackboxRecorder;
import com.tenicana.dronecraft.blackbox.DroneBlackboxSample;
import com.tenicana.dronecraft.debug.DroneDebugSettings;
import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.sim.ContactDynamics;
import com.tenicana.dronecraft.registry.DroneItems;
import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneEnvironmentOverride;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.DronePhysics;
import com.tenicana.dronecraft.sim.DroneState;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.PidGains;
import com.tenicana.dronecraft.sim.RotorFlowObstructionModel;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

public class DroneEntity extends Entity {
	private static final double PHYSICS_DT = 0.005;
	private static final int PHYSICS_STEPS_PER_TICK = 10;
	private static final int PROP_STRIKE_COOLDOWN_TICKS = 4;
	private static final boolean PLAYABLE_STAGE_ONE_PHYSICS = true;
	private static final double GROUND_LOCK_CLEARANCE_MARGIN_METERS = 0.12;
	private static final double ADVANCED_EFFECTS_CLEARANCE_METERS = 1.25;
	private static final double TAKEOFF_THRUST_TO_WEIGHT = 0.98;
	private static final double TAKEOFF_POSITION_NUDGE_METERS = 0.045;
	private static final double TAKEOFF_MIN_VERTICAL_SPEED_METERS_PER_SECOND = 0.42;
	private static final double MIN_PROP_STRIKE_TIP_SPEED_METERS_PER_SECOND = 2.0;
	private static final double MIN_PROP_STRIKE_FRAME_SPEED_METERS_PER_SECOND = 1.8;
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final ContactDynamics.ContactSurface ICE_CONTACT_SURFACE = new ContactDynamics.ContactSurface(0.18, 0.70, 0.55);
	private static final ContactDynamics.ContactSurface SLIME_CONTACT_SURFACE = new ContactDynamics.ContactSurface(0.85, 2.25, 0.65);
	private static final ContactDynamics.ContactSurface HONEY_CONTACT_SURFACE = new ContactDynamics.ContactSurface(1.95, 0.35, 1.10);
	private static final ContactDynamics.ContactSurface LOOSE_CONTACT_SURFACE = new ContactDynamics.ContactSurface(1.35, 0.50, 1.25);
	private static final ContactDynamics.ContactSurface STICKY_DIRT_CONTACT_SURFACE = new ContactDynamics.ContactSurface(1.65, 0.45, 1.35);
	private static final ContactDynamics.ContactSurface ABRASIVE_CONTACT_SURFACE = new ContactDynamics.ContactSurface(1.20, 0.35, 2.20);
	private static final ContactDynamics.ContactSurface HARD_MASONRY_CONTACT_SURFACE = new ContactDynamics.ContactSurface(1.12, 0.78, 1.25);
	private static final ContactDynamics.ContactSurface WOOD_CONTACT_SURFACE = new ContactDynamics.ContactSurface(1.05, 0.82, 0.85);
	private static final ContactDynamics.ContactSurface METAL_CONTACT_SURFACE = new ContactDynamics.ContactSurface(0.72, 1.08, 1.70);
	private static final ContactDynamics.ContactSurface SOFT_FIBER_CONTACT_SURFACE = new ContactDynamics.ContactSurface(1.45, 0.28, 0.40);
	private static final double ROTOR_DISK_SURFACE_SAMPLE_RADIUS_SCALE = 0.72;
	private static final double ROTOR_DISK_SURFACE_CENTER_WEIGHT = 0.36;
	private static final double ROTOR_DISK_SURFACE_CARDINAL_WEIGHT = 0.11;
	private static final double ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT = 0.05;
	private static final EntityDataAccessor<Boolean> ARMED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> FLIGHT_MODE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> ROTOR_COUNT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> ROTOR_LAYOUT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Float> MOTOR_POWER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_0_POWER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_1_POWER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_2_POWER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_3_POWER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_4_POWER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_5_POWER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_6_POWER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_7_POWER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_RPM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_0_RPM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_1_RPM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_2_RPM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_3_RPM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_4_RPM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_5_RPM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_6_RPM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_7_RPM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_TEMPERATURE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_THERMAL_LIMIT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_VOLTAGE_HEADROOM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MOTOR_WINDING_RESISTANCE_SCALE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ESC_TEMPERATURE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ESC_THERMAL_LIMIT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ESC_COOLING_FACTOR = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ESC_DESYNC = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_AERODYNAMIC_LOAD = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_IN_PLANE_DRAG_FORCE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_0_THRUST = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_1_THRUST = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_2_THRUST = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_3_THRUST = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_4_THRUST = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_5_THRUST = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_6_THRUST = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_7_THRUST = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_VIBRATION = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_CONING_INTENSITY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_STALL_INTENSITY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_FLAPPING_TILT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_SURFACE_SCRAPE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_TRANSLATIONAL_LIFT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_ADVANCE_RATIO = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_PROPELLER_ADVANCE_RATIO_J = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_PROPELLER_THRUST_SCALE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_PROPELLER_POWER_SCALE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_REVERSE_FLOW = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_TIP_MACH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_COMPRESSIBILITY_THRUST_SCALE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_LOW_REYNOLDS_LOSS = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_BLADE_AOA = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_BLADE_ELEMENT_STALL = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_BLADE_PASS_RIPPLE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_BLADE_DISSYMMETRY_TORQUE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_INFLOW_SKEW = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_INDUCED_VELOCITY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_INDUCED_LAG_THRUST_SCALE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_WAKE_INTERFERENCE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_WAKE_THRUST_SCALE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_COAXIAL_LOAD_BIAS = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_WET_THRUST_SCALE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_WAKE_SWIRL = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_WINDMILLING = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_WAKE_SWIRL_TORQUE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_ACTIVE_BRAKING_TORQUE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_ACCELERATION_REACTION_TORQUE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_GYROSCOPIC_TORQUE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_FLAPPING_TORQUE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MIXER_SATURATION = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> PROPWASH_INTENSITY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> VORTEX_RING_STATE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> VORTEX_RING_THRUST_BUFFET = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> VORTEX_RING_BUFFET_FORCE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> GROUND_EFFECT_MULTIPLIER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> CEILING_EFFECT_MULTIPLIER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> CEILING_EFFECT_INTENSITY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ENV_THRUST_ASYMMETRY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_FLOW_OBSTRUCTION = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> WATER_IMMERSION = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> PRECIPITATION_WETNESS = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> AMBIENT_TEMPERATURE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> WIND_SPEED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> EFFECTIVE_WIND_SPEED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> WIND_GUST_SPEED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> WIND_SHEAR_ACCELERATION = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> TURBULENCE_INTENSITY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> OBSTACLE_PROXIMITY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DRONE_WAKE_INTENSITY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> AIRSPEED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ANGLE_OF_ATTACK = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> SIDESLIP = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> AIRFRAME_SEPARATED_FLOW = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> AIRFRAME_LIFT_FORCE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> GROUND_EFFECT_DRAG_FORCE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_WASH_DRAG_FORCE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_WALL_EFFECT_FORCE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BAROMETER_ALTITUDE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BAROMETER_VERTICAL_SPEED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BAROMETER_PRESSURE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BAROMETER_ERROR = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> CONTACT_IMPACT_SPEED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> CONTACT_SLIP_SPEED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> CONTACT_BOUNCE_SPEED = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> CONTACT_ANGULAR_IMPULSE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BATTERY_VOLTAGE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BATTERY_SAG = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BATTERY_EFFECTIVE_RESISTANCE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BATTERY_REGEN_CURRENT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BATTERY_VOLTAGE_SPIKE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BATTERY_BUS_RIPPLE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BATTERY_STATE_OF_CHARGE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BATTERY_CURRENT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BATTERY_TWENTY_PERCENT_SAG_CURRENT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BATTERY_TWENTY_PERCENT_SAG_CURRENT_MARGIN = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BATTERY_CURRENT_LIMIT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BATTERY_POWER_LIMIT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> FRAME_HEALTH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_HEALTH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_0_HEALTH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_1_HEALTH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_2_HEALTH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_3_HEALTH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_4_HEALTH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_5_HEALTH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_6_HEALTH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ROTOR_7_HEALTH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> PROP_STRIKE_COUNT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> LAST_PROP_STRIKE_ROTOR = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> LAST_PROP_STRIKE_SEVERITY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> CONTROL_THROTTLE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> CONTROL_PITCH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> CONTROL_ROLL = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> CONTROL_YAW = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> CONTROL_LINK_LOSS_SECONDS = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> RAW_CONTROL_LINK_ACTIVE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> PROCESSED_CONTROL_LINK_ACTIVE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> CONTROL_FAILSAFE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> TARGET_PITCH_RATE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> TARGET_YAW_RATE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> TARGET_ROLL_RATE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> GYRO_PITCH_RATE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> GYRO_YAW_RATE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> GYRO_ROLL_RATE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> GYRO_CLIP = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ACCEL_CLIP = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> IMU_SUPPLY_NOISE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> GYRO_NOTCH_FREQUENCY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> GYRO_NOTCH_ATTENUATION = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> PID_PITCH_OUTPUT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> PID_YAW_OUTPUT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> PID_ROLL_OUTPUT = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> PID_ATTENUATION = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> PID_INTEGRAL_RELAX = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> PID_DTERM_LPF_CUTOFF = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ANTI_GRAVITY_BOOST = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ESTIMATED_PITCH = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ESTIMATED_YAW = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ESTIMATED_ROLL = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ATTITUDE_ESTIMATE_ERROR = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> ATTITUDE_ACCEL_TRUST = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Float> GROUND_EFFECT_LEVELING_TORQUE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);

	private DronePhysics physics = new DronePhysics(DroneConfig.racingQuad());
	private String airframePreset = "racing_quad";
	private UUID owner;
	private float debugVelocityX;
	private float debugVelocityY;
	private float debugVelocityZ;
	private float debugVisualPitchRadians;
	private float debugVisualRollRadians;
	private float debugMotorPower;
	private float debugAverageMotorRpm;
	private float debugTargetVelocityX;
	private float debugTargetVelocityY;
	private float debugTargetVelocityZ;
	private float debugTargetYawRate;
	private float debugCommandThrottle;
	private float debugCommandPitch;
	private float debugCommandRoll;
	private float debugCommandYaw;
	private FlightMode debugFlightMode = FlightMode.HORIZON;
	private boolean debugFlightActiveLastTick;
	private int debugFailsafeTicks;
	private boolean simulationInitialized;
	private double frameHealth = 1.0;
	private int collisionDamageCooldown;
	private int[] rotorStrikeCooldownTicks = new int[4];
	private double[] propStrikeSeverityThisTick = new double[4];
	private int propStrikeCount;
	private int lastPropStrikeRotorIndex = -1;
	private double lastPropStrikeSeverity;
	private double lastCollisionSeverity;
	private DroneEnvironment lastEnvironment = DroneEnvironment.calm();
	private DroneEnvironmentOverride environmentOverride = DroneEnvironmentOverride.natural();
	private final DroneBlackboxRecorder blackbox = new DroneBlackboxRecorder();

	private record ObstacleAirflow(Vec3 windVelocityWorldMetersPerSecond, double obstacleProximity, double turbulenceBoost) {
	}

	private record DroneWakeAirflow(Vec3 windVelocityWorldMetersPerSecond, double turbulenceBoost, double intensity) {
		private static final DroneWakeAirflow CALM = new DroneWakeAirflow(Vec3.ZERO, 0.0, 0.0);
	}

	private record RotorEnvironmentEffects(double[] thrustMultipliers, double[] flowObstructions, Vec3[] flowObstructionDirectionsBody, double maxFlowObstruction) {
		private static RotorEnvironmentEffects calm(int rotorCount) {
			double[] multipliers = new double[rotorCount];
			for (int i = 0; i < multipliers.length; i++) {
				multipliers[i] = 1.0;
			}
			return new RotorEnvironmentEffects(multipliers, new double[rotorCount], new Vec3[rotorCount], 0.0);
		}
	}

	private record WaterImmersion(double[] rotorImmersions, double averageImmersion) {
		private static WaterImmersion dry(int rotorCount) {
			return new WaterImmersion(new double[rotorCount], 0.0);
		}
	}

	private record PrecipitationWetness(double[] rotorWetnesses, double averageWetness) {
		private static PrecipitationWetness dry(int rotorCount) {
			return new PrecipitationWetness(new double[rotorCount], 0.0);
		}
	}

	private record RotorPlaneSampleDirection(Vec3 bodyDirection, Vec3 worldDirection) {
	}

	private record RotorDiskSurfaceSample(double[] groundClearancesMeters, double[] ceilingClearancesMeters, double[] weights) {
	}

	private record RotorFlowObstruction(double intensity, Vec3 directionBody) {
		private static final RotorFlowObstruction CLEAR = new RotorFlowObstruction(0.0, Vec3.ZERO);
	}

	public static int physicsStepsPerTick() {
		return PHYSICS_STEPS_PER_TICK;
	}

	public static double physicsDtSeconds() {
		return PHYSICS_DT;
	}

	public static double physicsRateHertz() {
		return 1.0 / PHYSICS_DT;
	}

	public DroneEntity(EntityType<? extends DroneEntity> entityType, Level level) {
		super(entityType, level);
		setNoGravity(true);
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return DroneAirframeDimensions.forConfig(physics.config());
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
		entityData.set(OWNER, owner == null ? "" : owner.toString());
	}

	public UUID getOwner() {
		return ownerFromSyncedData();
	}

	public boolean hasOwner() {
		return getOwner() != null;
	}

	public boolean isOwnedBy(UUID playerId) {
		return playerId != null && playerId.equals(ownerFromSyncedData());
	}

	private UUID ownerFromSyncedData() {
		String ownerId = entityData.get(OWNER);
		if (ownerId == null || ownerId.isBlank()) {
			return owner;
		}

		try {
			return UUID.fromString(ownerId);
		} catch (IllegalArgumentException ignored) {
			return owner;
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(ARMED, false);
		builder.define(FLIGHT_MODE, FlightMode.ANGLE.id());
		builder.define(PITCH, 0.0f);
		builder.define(YAW, 0.0f);
		builder.define(ROLL, 0.0f);
		builder.define(ROTOR_COUNT, 4);
		builder.define(ROTOR_LAYOUT, RotorLayoutCodec.defaultLayout());
		builder.define(MOTOR_POWER, 0.0f);
		builder.define(MOTOR_0_POWER, 0.0f);
		builder.define(MOTOR_1_POWER, 0.0f);
		builder.define(MOTOR_2_POWER, 0.0f);
		builder.define(MOTOR_3_POWER, 0.0f);
		builder.define(MOTOR_4_POWER, 0.0f);
		builder.define(MOTOR_5_POWER, 0.0f);
		builder.define(MOTOR_6_POWER, 0.0f);
		builder.define(MOTOR_7_POWER, 0.0f);
		builder.define(MOTOR_RPM, 0.0f);
		builder.define(MOTOR_0_RPM, 0.0f);
		builder.define(MOTOR_1_RPM, 0.0f);
		builder.define(MOTOR_2_RPM, 0.0f);
		builder.define(MOTOR_3_RPM, 0.0f);
		builder.define(MOTOR_4_RPM, 0.0f);
		builder.define(MOTOR_5_RPM, 0.0f);
		builder.define(MOTOR_6_RPM, 0.0f);
		builder.define(MOTOR_7_RPM, 0.0f);
		builder.define(MOTOR_TEMPERATURE, 25.0f);
		builder.define(MOTOR_THERMAL_LIMIT, 1.0f);
		builder.define(MOTOR_VOLTAGE_HEADROOM, 1.0f);
		builder.define(MOTOR_WINDING_RESISTANCE_SCALE, 1.0f);
		builder.define(ESC_TEMPERATURE, 25.0f);
		builder.define(ESC_THERMAL_LIMIT, 1.0f);
		builder.define(ESC_COOLING_FACTOR, 1.0f);
		builder.define(ESC_DESYNC, 0.0f);
		builder.define(ROTOR_AERODYNAMIC_LOAD, 0.0f);
		builder.define(ROTOR_IN_PLANE_DRAG_FORCE, 0.0f);
		builder.define(ROTOR_0_THRUST, 0.0f);
		builder.define(ROTOR_1_THRUST, 0.0f);
		builder.define(ROTOR_2_THRUST, 0.0f);
		builder.define(ROTOR_3_THRUST, 0.0f);
		builder.define(ROTOR_4_THRUST, 0.0f);
		builder.define(ROTOR_5_THRUST, 0.0f);
		builder.define(ROTOR_6_THRUST, 0.0f);
		builder.define(ROTOR_7_THRUST, 0.0f);
		builder.define(ROTOR_VIBRATION, 0.0f);
		builder.define(ROTOR_CONING_INTENSITY, 0.0f);
		builder.define(ROTOR_STALL_INTENSITY, 0.0f);
		builder.define(ROTOR_FLAPPING_TILT, 0.0f);
		builder.define(ROTOR_SURFACE_SCRAPE, 0.0f);
		builder.define(ROTOR_TRANSLATIONAL_LIFT, 0.0f);
		builder.define(ROTOR_ADVANCE_RATIO, 0.0f);
		builder.define(ROTOR_PROPELLER_ADVANCE_RATIO_J, 0.0f);
		builder.define(ROTOR_PROPELLER_THRUST_SCALE, 1.0f);
		builder.define(ROTOR_PROPELLER_POWER_SCALE, 1.0f);
		builder.define(ROTOR_REVERSE_FLOW, 0.0f);
		builder.define(ROTOR_TIP_MACH, 0.0f);
		builder.define(ROTOR_COMPRESSIBILITY_THRUST_SCALE, 1.0f);
		builder.define(ROTOR_LOW_REYNOLDS_LOSS, 0.0f);
		builder.define(ROTOR_BLADE_AOA, 0.0f);
		builder.define(ROTOR_BLADE_ELEMENT_STALL, 0.0f);
		builder.define(ROTOR_BLADE_PASS_RIPPLE, 0.0f);
		builder.define(ROTOR_BLADE_DISSYMMETRY_TORQUE, 0.0f);
		builder.define(ROTOR_INFLOW_SKEW, 0.0f);
		builder.define(ROTOR_INDUCED_VELOCITY, 0.0f);
		builder.define(ROTOR_INDUCED_LAG_THRUST_SCALE, 1.0f);
		builder.define(ROTOR_WAKE_INTERFERENCE, 0.0f);
		builder.define(ROTOR_WAKE_THRUST_SCALE, 1.0f);
		builder.define(ROTOR_COAXIAL_LOAD_BIAS, 0.0f);
		builder.define(ROTOR_WET_THRUST_SCALE, 1.0f);
		builder.define(ROTOR_WAKE_SWIRL, 0.0f);
		builder.define(ROTOR_WINDMILLING, 0.0f);
		builder.define(ROTOR_WAKE_SWIRL_TORQUE, 0.0f);
		builder.define(ROTOR_ACTIVE_BRAKING_TORQUE, 0.0f);
		builder.define(ROTOR_ACCELERATION_REACTION_TORQUE, 0.0f);
		builder.define(ROTOR_GYROSCOPIC_TORQUE, 0.0f);
		builder.define(ROTOR_FLAPPING_TORQUE, 0.0f);
		builder.define(MIXER_SATURATION, 0.0f);
		builder.define(PROPWASH_INTENSITY, 0.0f);
		builder.define(VORTEX_RING_STATE, 0.0f);
		builder.define(VORTEX_RING_THRUST_BUFFET, 0.0f);
		builder.define(VORTEX_RING_BUFFET_FORCE, 0.0f);
		builder.define(GROUND_EFFECT_MULTIPLIER, 1.0f);
		builder.define(CEILING_EFFECT_MULTIPLIER, 1.0f);
		builder.define(CEILING_EFFECT_INTENSITY, 0.0f);
		builder.define(ENV_THRUST_ASYMMETRY, 0.0f);
		builder.define(ROTOR_FLOW_OBSTRUCTION, 0.0f);
		builder.define(WATER_IMMERSION, 0.0f);
		builder.define(PRECIPITATION_WETNESS, 0.0f);
		builder.define(AMBIENT_TEMPERATURE, 25.0f);
		builder.define(WIND_SPEED, 0.0f);
		builder.define(EFFECTIVE_WIND_SPEED, 0.0f);
		builder.define(WIND_GUST_SPEED, 0.0f);
		builder.define(WIND_SHEAR_ACCELERATION, 0.0f);
		builder.define(TURBULENCE_INTENSITY, 0.0f);
		builder.define(OBSTACLE_PROXIMITY, 0.0f);
		builder.define(DRONE_WAKE_INTENSITY, 0.0f);
		builder.define(AIRSPEED, 0.0f);
		builder.define(ANGLE_OF_ATTACK, 0.0f);
		builder.define(SIDESLIP, 0.0f);
		builder.define(AIRFRAME_SEPARATED_FLOW, 0.0f);
		builder.define(AIRFRAME_LIFT_FORCE, 0.0f);
		builder.define(GROUND_EFFECT_DRAG_FORCE, 0.0f);
		builder.define(ROTOR_WASH_DRAG_FORCE, 0.0f);
		builder.define(ROTOR_WALL_EFFECT_FORCE, 0.0f);
		builder.define(BAROMETER_ALTITUDE, 0.0f);
		builder.define(BAROMETER_VERTICAL_SPEED, 0.0f);
		builder.define(BAROMETER_PRESSURE, 1013.25f);
		builder.define(BAROMETER_ERROR, 0.0f);
		builder.define(SPEED, 0.0f);
		builder.define(CONTACT_IMPACT_SPEED, 0.0f);
		builder.define(CONTACT_SLIP_SPEED, 0.0f);
		builder.define(CONTACT_BOUNCE_SPEED, 0.0f);
		builder.define(CONTACT_ANGULAR_IMPULSE, 0.0f);
		builder.define(BATTERY_VOLTAGE, 16.8f);
		builder.define(BATTERY_SAG, 0.0f);
		builder.define(BATTERY_EFFECTIVE_RESISTANCE, 0.018f);
		builder.define(BATTERY_REGEN_CURRENT, 0.0f);
		builder.define(BATTERY_VOLTAGE_SPIKE, 0.0f);
		builder.define(BATTERY_BUS_RIPPLE, 0.0f);
		builder.define(BATTERY_STATE_OF_CHARGE, 1.0f);
		builder.define(BATTERY_CURRENT, 0.0f);
		builder.define(BATTERY_TWENTY_PERCENT_SAG_CURRENT, 0.0f);
		builder.define(BATTERY_TWENTY_PERCENT_SAG_CURRENT_MARGIN, 0.0f);
		builder.define(BATTERY_CURRENT_LIMIT, 1.0f);
		builder.define(BATTERY_POWER_LIMIT, 1.0f);
		builder.define(FRAME_HEALTH, 1.0f);
		builder.define(ROTOR_HEALTH, 1.0f);
		builder.define(ROTOR_0_HEALTH, 1.0f);
		builder.define(ROTOR_1_HEALTH, 1.0f);
		builder.define(ROTOR_2_HEALTH, 1.0f);
		builder.define(ROTOR_3_HEALTH, 1.0f);
		builder.define(ROTOR_4_HEALTH, 1.0f);
		builder.define(ROTOR_5_HEALTH, 1.0f);
		builder.define(ROTOR_6_HEALTH, 1.0f);
		builder.define(ROTOR_7_HEALTH, 1.0f);
		builder.define(PROP_STRIKE_COUNT, 0);
		builder.define(LAST_PROP_STRIKE_ROTOR, -1);
		builder.define(LAST_PROP_STRIKE_SEVERITY, 0.0f);
		builder.define(CONTROL_THROTTLE, 0.0f);
		builder.define(CONTROL_PITCH, 0.0f);
		builder.define(CONTROL_ROLL, 0.0f);
		builder.define(CONTROL_YAW, 0.0f);
		builder.define(CONTROL_LINK_LOSS_SECONDS, 0.0f);
		builder.define(RAW_CONTROL_LINK_ACTIVE, false);
		builder.define(PROCESSED_CONTROL_LINK_ACTIVE, false);
		builder.define(CONTROL_FAILSAFE, false);
		builder.define(TARGET_PITCH_RATE, 0.0f);
		builder.define(TARGET_YAW_RATE, 0.0f);
		builder.define(TARGET_ROLL_RATE, 0.0f);
		builder.define(GYRO_PITCH_RATE, 0.0f);
		builder.define(GYRO_YAW_RATE, 0.0f);
		builder.define(GYRO_ROLL_RATE, 0.0f);
		builder.define(GYRO_CLIP, 0.0f);
		builder.define(ACCEL_CLIP, 0.0f);
		builder.define(IMU_SUPPLY_NOISE, 0.0f);
		builder.define(GYRO_NOTCH_FREQUENCY, 0.0f);
		builder.define(GYRO_NOTCH_ATTENUATION, 0.0f);
		builder.define(PID_PITCH_OUTPUT, 0.0f);
		builder.define(PID_YAW_OUTPUT, 0.0f);
		builder.define(PID_ROLL_OUTPUT, 0.0f);
		builder.define(PID_ATTENUATION, 1.0f);
		builder.define(PID_INTEGRAL_RELAX, 0.0f);
		builder.define(PID_DTERM_LPF_CUTOFF, 0.0f);
		builder.define(ANTI_GRAVITY_BOOST, 0.0f);
		builder.define(ESTIMATED_PITCH, 0.0f);
		builder.define(ESTIMATED_YAW, 0.0f);
		builder.define(ESTIMATED_ROLL, 0.0f);
		builder.define(ATTITUDE_ESTIMATE_ERROR, 0.0f);
		builder.define(ATTITUDE_ACCEL_TRUST, 0.0f);
		builder.define(OWNER, "");
		builder.define(GROUND_EFFECT_LEVELING_TORQUE, 0.0f);
	}

	@Override
	public void tick() {
		super.tick();
		setNoGravity(true);

		if (!level().isClientSide()) {
			if (!simulationInitialized) {
				physics.state().setPositionMeters(entityPhysicsPosition());
				simulationInitialized = true;
			}

			if (collisionDamageCooldown > 0) {
				collisionDamageCooldown--;
			}
			decrementRotorStrikeCooldowns();

			UUID activeOwner = getOwner();
			DroneInput rawInput = activeOwner == null ? stableIdleInput() : DroneControlManager.get(activeOwner, tickCount, physics.state(), physics.config());
			DroneControlManager.ActiveInput sample = DroneDebugSettings.ownerlessControlEnabled()
					? DroneControlManager.latestActiveInput(tickCount)
					: null;
			boolean usedOwnerlessSample = false;
			if (activeOwner == null && sample != null && sample.input() != null && sample.input().linkActive()) {
				rawInput = sample.input();
				UUID sampleOwner = sample.playerId();
				if (sampleOwner != null) {
					activeOwner = sampleOwner;
					usedOwnerlessSample = true;
					if (getOwner() == null) {
						setOwner(sampleOwner);
					}
				}
			}
			if (rawInput == null) {
				rawInput = stableIdleInput();
			}
			DroneInput input = rawInput;
			boolean airworthy = isAirworthy();
			boolean bypassPhysics = DroneDebugSettings.bypassPhysicsEnabled();
			boolean hasDebugControlInput = hasDebugControlIntent(input);
			if (activeOwner == null) {
				DroneDebugSettings.logNoOwnerInput(tickCount, level().getEntitiesOfClass(DroneEntity.class, getBoundingBox().inflate(24.0), drone -> true).size(), DroneDebugSettings.ownerlessControlEnabled(), activeOwner);
			}
			if (!airworthy && !bypassPhysics) {
				input = new DroneInput(input.throttle(), input.pitch(), input.roll(), input.yaw(), false, input.linkActive(), input.flightMode());
			}
			DroneInput effectiveInput = input;
			String reason = activeOwner == null
					? "no-owner"
					: (!input.linkActive() ? "link-lost" : (bypassPhysics ? "direct-disabled" : "physics-active"));

			boolean directFlightActive = bypassPhysics && input.linkActive() && input.armed();
			boolean directFlightFailsafe = bypassPhysics && debugFlightActiveLastTick && !input.linkActive();
			if (directFlightActive) {
				DroneInput debugInput = input;
				effectiveInput = debugInput;
				debugFailsafeTicks = 0;
				applyDebugFlight(debugInput);
				debugFlightActiveLastTick = true;
				updateDebugFlightState(debugInput, airworthy);
				recordBlackbox(input);
				handleCompletedDiagnostic();
				reason = debugInput.armed() ? "direct-active" : "direct-wait-arm";
				if (usedOwnerlessSample) {
					reason = "direct-ownerless";
				}
			} else if (directFlightFailsafe) {
				debugFailsafeTicks++;
				DroneInput failsafeInput = directFailsafeInput();
				effectiveInput = failsafeInput;
				applyDebugFailsafeFlight(failsafeInput);
				if (isDebugFailsafeSettled()) {
					float linkLossSeconds = directFailsafeLinkLossSeconds();
					clearDebugFlightState();
					debugFlightActiveLastTick = false;
					updateDebugFlightState(groundedDirectFailsafeInput(), airworthy, true, linkLossSeconds);
					debugFailsafeTicks = 0;
					reason = "direct-failsafe-grounded";
				} else {
					debugFlightActiveLastTick = true;
					updateDebugFlightState(failsafeInput, airworthy, true, directFailsafeLinkLossSeconds());
					reason = activeOwner == null ? "direct-failsafe-no-owner" : "direct-failsafe";
				}
				recordBlackbox(failsafeInput);
				handleCompletedDiagnostic();
			} else {
				debugTargetVelocityX = 0.0f;
				debugTargetVelocityY = 0.0f;
				debugTargetVelocityZ = 0.0f;
				debugTargetYawRate = 0.0f;
				clearDebugFlightStateAfterDirectControl();
				reason = activeOwner == null
						? "no-owner"
						: (!input.linkActive() ? "link-lost" : (bypassPhysics ? reason : "physics-active"));
				lastEnvironment = sampleActiveEnvironment(input);
				if (shouldSleepOnGround(input)) {
					sleepOnGround(input);
					reason = "ground-idle";
				} else if (shouldConstrainOnGround(input)) {
					physics.levelAtRest(entityPhysicsPosition());
					for (int i = 0; i < PHYSICS_STEPS_PER_TICK; i++) {
						physics.step(input, PHYSICS_DT, lastEnvironment);
					}
					if (hasTakeoffAuthority(input)) {
						prepareGroundTakeoff(input);
						applyPhysicsMovement(input);
						if (advancedContactEffectsActive(input)) {
							samplePropStrikes();
						}
						reason = "takeoff-release";
					} else {
						constrainOnGround();
						reason = "ground-spool";
					}
				} else {
					for (int i = 0; i < PHYSICS_STEPS_PER_TICK; i++) {
						physics.step(input, PHYSICS_DT, lastEnvironment);
					}
					applyPhysicsMovement(input);
					if (advancedContactEffectsActive(input)) {
						samplePropStrikes();
					}
				}
				updateSyncedFlightState(input);
				recordBlackbox(input);
				handleCompletedDiagnostic();
			}

			DroneDebugSettings.logEntityTick(
					this,
					tickCount,
					rawInput,
					effectiveInput,
					reason,
					activeOwner,
					debugTargetVelocityX,
					debugTargetVelocityY,
					debugTargetVelocityZ,
					debugTargetYawRate,
					airworthy,
					activeOwner != null,
					DroneDebugSettings.bypassPhysicsEnabled(),
					getDeltaMovement()
			);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (isRemoved() || amount <= 0.0f) {
			return false;
		}
		markHurt();
		frameHealth = Math.max(0.0, frameHealth - amount * 0.08);
		updateDamageSyncedState();
		if (frameHealth <= 0.0) {
			discard();
		}
		return true;
	}

	private static final float DEBUG_THROTTLE_SMOOTH = 0.24f;
	private static final float DEBUG_AXIS_RISE_SMOOTH = 0.18f;
	private static final float DEBUG_AXIS_FALL_SMOOTH = 0.44f;
	private static final float DEBUG_THRUST_DEADZONE = 0.005f;
	private static final float DEBUG_MOVEMENT_EPSILON = 0.015f;
	private static final float DEBUG_FAILSAFE_THROTTLE_SCALE = 0.88f;
	private static final float DEBUG_FAILSAFE_COMMAND_DAMPING = 0.28f;
	private static final float DEBUG_FAILSAFE_HORIZONTAL_DAMPING = 0.72f;
	private static final float DEBUG_FAILSAFE_SETTLED_HORIZONTAL_SPEED = 0.12f;
	private static final float DEBUG_FAILSAFE_SETTLED_VERTICAL_SPEED = 0.08f;

	private void applyDebugFlight(DroneInput input) {
		float throttle = (float) MathUtil.clamp(input.throttle(), 0.0, 1.0);
		float pitch = (float) MathUtil.clamp(input.pitch(), -1.0, 1.0);
		float roll = (float) MathUtil.clamp(input.roll(), -1.0, 1.0);
		float yaw = (float) MathUtil.clamp(input.yaw(), -1.0, 1.0);

		float playablePitch = PlayableFlightModel.playableAxisCommand(pitch);
		float playableRoll = PlayableFlightModel.playableAxisCommand(roll);
		float playableYaw = PlayableFlightModel.playableAxisCommand(yaw);

		float smoothedThrottle = applyDebugAxisFilter(
				debugCommandThrottle,
				throttle,
				DEBUG_THROTTLE_SMOOTH,
				DEBUG_THROTTLE_SMOOTH,
				false
		);
		float smoothedPitch = applyDebugAxisFilter(
				debugCommandPitch,
				playablePitch,
				DEBUG_AXIS_RISE_SMOOTH,
				DEBUG_AXIS_FALL_SMOOTH,
				true
		);
		float smoothedRoll = applyDebugAxisFilter(
				debugCommandRoll,
				playableRoll,
				DEBUG_AXIS_RISE_SMOOTH,
				DEBUG_AXIS_FALL_SMOOTH,
				true
		);
		float smoothedYaw = applyDebugAxisFilter(
				debugCommandYaw,
				playableYaw,
				DEBUG_AXIS_RISE_SMOOTH,
				DEBUG_AXIS_FALL_SMOOTH,
				true
		);

		debugCommandThrottle = smoothedThrottle;
		debugCommandPitch = smoothedPitch;
		debugCommandRoll = smoothedRoll;
		debugCommandYaw = smoothedYaw;

		boolean shouldFly = input.armed() || smoothedThrottle > DEBUG_THRUST_DEADZONE || Math.abs(smoothedPitch) > DEBUG_MOVEMENT_EPSILON || Math.abs(smoothedRoll) > DEBUG_MOVEMENT_EPSILON;
		float hoverThrottle = (float) MathUtil.clamp(physics.config().hoverThrottle(), 0.12, 0.55);
		boolean nearGroundLocked = isNearGroundLocked();
		PlayableFlightModel.Step step = PlayableFlightModel.step(
				input.flightMode(),
				smoothedThrottle,
				smoothedPitch,
				smoothedRoll,
				smoothedYaw,
				hoverThrottle,
				nearGroundLocked,
				new PlayableFlightModel.State(debugVelocityX, debugVelocityY, debugVelocityZ, debugVisualPitchRadians, debugVisualRollRadians, debugTargetYawRate, debugFlightMode)
		);
		float targetVx = step.targetVelocityX();
		float targetVy = step.targetVelocityY();
		float targetVz = step.targetVelocityZ();
		float targetYaw = step.yawDegreesPerTick();

		if (!shouldFly) {
			targetVx = 0.0f;
			targetVy = 0.0f;
			targetVz = 0.0f;
			targetYaw = 0.0f;
			clearDebugFlightState();
			setDeltaMovement(0.0, 0.0, 0.0);
			debugTargetVelocityX = targetVx;
			debugTargetVelocityY = targetVy;
			debugTargetVelocityZ = targetVz;
			debugTargetYawRate = targetYaw;
			return;
		}
		if (Math.abs(targetVx) < 0.015f) {
			targetVx = 0.0f;
		}
		if (Math.abs(targetVy) < 0.010f) {
			targetVy = 0.0f;
		}
		if (Math.abs(targetVz) < 0.015f) {
			targetVz = 0.0f;
		}
		if (Math.abs(targetYaw) < 0.015f) {
			targetYaw = 0.0f;
		}

		debugTargetVelocityX = targetVx;
		debugTargetVelocityY = targetVy;
		debugTargetVelocityZ = targetVz;
		debugTargetYawRate = targetYaw;
		debugVelocityX = step.velocityX();
		debugVelocityY = step.velocityY();
		debugVelocityZ = step.velocityZ();
		debugVisualPitchRadians = step.pitchRadians();
		debugVisualRollRadians = step.rollRadians();
		debugFlightMode = step.mode();
		debugMotorPower = step.motorPower();
		debugAverageMotorRpm = step.averageRpm();

		float yawRadians = (float) Math.toRadians(getYRot());
		float cos = (float) Math.cos(yawRadians);
		float sin = (float) Math.sin(yawRadians);
		float worldX = debugVelocityX * cos - debugVelocityZ * sin;
		float worldZ = debugVelocityX * sin + debugVelocityZ * cos;
		float deltaX = worldX / 20.0f;
		float deltaY = debugVelocityY / 20.0f;
		float deltaZ = worldZ / 20.0f;
		float worldVelocityX = worldX;
		float worldVelocityY = debugVelocityY;
		float worldVelocityZ = worldZ;

		applyDebugMovement(deltaX, deltaY, deltaZ, worldVelocityX, worldVelocityY, worldVelocityZ, cos, sin);
		if (Math.abs(targetYaw) > 0.02f) {
			setYRot(getYRot() + targetYaw);
		}
		setXRot((float) Math.toDegrees(debugVisualPitchRadians));
	}

	private void applyDebugMovement(
			float deltaX,
			float deltaY,
			float deltaZ,
			float worldVelocityX,
			float worldVelocityY,
			float worldVelocityZ,
			float yawCos,
			float yawSin
	) {
		double startX = getX();
		double startY = getY();
		double startZ = getZ();
		move(MoverType.SELF, new net.minecraft.world.phys.Vec3(deltaX, deltaY, deltaZ));

		double actualDeltaX = getX() - startX;
		double actualDeltaY = getY() - startY;
		double actualDeltaZ = getZ() - startZ;
		float actualWorldVelocityX = (float) (actualDeltaX * 20.0);
		float actualWorldVelocityY = (float) (actualDeltaY * 20.0);
		float actualWorldVelocityZ = (float) (actualDeltaZ * 20.0);

		if (!horizontalCollision) {
			actualWorldVelocityX = worldVelocityX;
			actualWorldVelocityZ = worldVelocityZ;
		}
		if (!verticalCollision) {
			actualWorldVelocityY = worldVelocityY;
		}

		debugVelocityX = actualWorldVelocityX * yawCos + actualWorldVelocityZ * yawSin;
		debugVelocityY = actualWorldVelocityY;
		debugVelocityZ = -actualWorldVelocityX * yawSin + actualWorldVelocityZ * yawCos;
		if (horizontalCollision) {
			debugVelocityX = Math.abs(debugVelocityX) < 0.025f ? 0.0f : debugVelocityX;
			debugVelocityZ = Math.abs(debugVelocityZ) < 0.025f ? 0.0f : debugVelocityZ;
		}
		if (verticalCollision) {
			debugVelocityY = 0.0f;
		}

		physics.state().setPositionMeters(entityPhysicsPosition());
		physics.state().setVelocityMetersPerSecond(new Vec3(actualWorldVelocityX, actualWorldVelocityY, actualWorldVelocityZ));
		setDeltaMovement(actualDeltaX, actualDeltaY, actualDeltaZ);
	}

	private DroneInput directFailsafeInput() {
		float hoverThrottle = (float) MathUtil.clamp(physics.config().hoverThrottle(), 0.12, 0.55);
		float throttle = hoverThrottle * DEBUG_FAILSAFE_THROTTLE_SCALE;
		return new DroneInput(throttle, 0.0, 0.0, 0.0, true, false, FlightMode.ANGLE);
	}

	private DroneInput groundedDirectFailsafeInput() {
		return new DroneInput(0.0, 0.0, 0.0, 0.0, false, false, FlightMode.ANGLE);
	}

	private void applyDebugFailsafeFlight(DroneInput input) {
		debugCommandThrottle = Math.min(debugCommandThrottle, (float) input.throttle());
		debugCommandPitch *= DEBUG_FAILSAFE_COMMAND_DAMPING;
		debugCommandRoll *= DEBUG_FAILSAFE_COMMAND_DAMPING;
		debugCommandYaw *= DEBUG_FAILSAFE_COMMAND_DAMPING;
		debugVelocityX *= DEBUG_FAILSAFE_HORIZONTAL_DAMPING;
		debugVelocityZ *= DEBUG_FAILSAFE_HORIZONTAL_DAMPING;
		applyDebugFlight(input);
	}

	private boolean isDebugFailsafeSettled() {
		double horizontalSpeed = Math.hypot(debugVelocityX, debugVelocityZ);
		return isNearGroundLocked()
				&& horizontalSpeed <= DEBUG_FAILSAFE_SETTLED_HORIZONTAL_SPEED
				&& Math.abs(debugVelocityY) <= DEBUG_FAILSAFE_SETTLED_VERTICAL_SPEED;
	}

	private float directFailsafeLinkLossSeconds() {
		return Math.max(0.05f, debugFailsafeTicks / 20.0f);
	}

	private void clearDebugFlightState() {
		debugVelocityX = 0.0f;
		debugVelocityY = 0.0f;
		debugVelocityZ = 0.0f;
		debugVisualPitchRadians = 0.0f;
		debugVisualRollRadians = 0.0f;
		debugMotorPower = 0.0f;
		debugAverageMotorRpm = 0.0f;
		debugCommandThrottle = 0.0f;
		debugCommandPitch = 0.0f;
		debugCommandRoll = 0.0f;
		debugCommandYaw = 0.0f;
		debugFlightMode = FlightMode.HORIZON;
		physics.state().setPositionMeters(entityPhysicsPosition());
		physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
	}

	private void clearDebugFlightStateAfterDirectControl() {
		if (!debugFlightActiveLastTick) {
			return;
		}
		clearDebugFlightState();
		debugFlightActiveLastTick = false;
		debugFailsafeTicks = 0;
	}

	private boolean isNearGroundLocked() {
		if (onGround() || verticalCollision) {
			return true;
		}
		double clearance = groundClearanceMetersAt(entityPhysicsPosition());
		return Double.isFinite(clearance) && clearance <= groundLockClearanceMeters();
	}

	private void updateDebugFlightState(DroneInput input, boolean airworthy) {
		updateDebugFlightState(input, airworthy, false, 0.0f);
	}

	private void updateDebugFlightState(DroneInput input, boolean airworthy, boolean failsafe, float linkLossSeconds) {
		DroneInput normalizedInput = input.normalized();
		entityData.set(ARMED, normalizedInput.armed());
		entityData.set(FLIGHT_MODE, normalizedInput.flightMode().id());
		syncAirframeLayout();
		entityData.set(CONTROL_THROTTLE, (float) normalizedInput.throttle());
		entityData.set(CONTROL_PITCH, (float) normalizedInput.pitch());
		entityData.set(CONTROL_ROLL, (float) normalizedInput.roll());
		entityData.set(CONTROL_YAW, (float) normalizedInput.yaw());
		entityData.set(CONTROL_LINK_LOSS_SECONDS, Math.max(0.0f, linkLossSeconds));
		entityData.set(RAW_CONTROL_LINK_ACTIVE, !failsafe && normalizedInput.linkActive());
		entityData.set(PROCESSED_CONTROL_LINK_ACTIVE, !failsafe && normalizedInput.linkActive());
		entityData.set(CONTROL_FAILSAFE, failsafe);
		entityData.set(PITCH, debugVisualPitchRadians);
		entityData.set(YAW, (float) Math.toRadians(getYRot()));
		entityData.set(ROLL, debugVisualRollRadians);
		entityData.set(SPEED, (float) Math.sqrt(debugVelocityX * debugVelocityX + debugVelocityY * debugVelocityY + debugVelocityZ * debugVelocityZ));
		entityData.set(MOTOR_POWER, normalizedInput.armed() ? debugMotorPower : 0.0f);
		entityData.set(MOTOR_RPM, normalizedInput.armed() ? debugAverageMotorRpm : 0.0f);
		setDirectPerRotorState(normalizedInput);
		entityData.set(BAROMETER_ALTITUDE, (float) entityPhysicsPosition().y());
		entityData.set(BAROMETER_VERTICAL_SPEED, debugVelocityY);
		entityData.set(BAROMETER_ERROR, 0.0f);
		entityData.set(GROUND_EFFECT_LEVELING_TORQUE, airworthy ? 0.0f : 0.08f);
	}

	private void setDirectPerRotorState(DroneInput input) {
		int rotorCount = Math.max(1, physics.config().rotors().size());
		double[] motorPower = new double[rotorCount];
		double[] motorRpm = new double[rotorCount];
		double[] rotorThrust = new double[rotorCount];
		double[] rotorHealth = physics.state().rotorHealth();
		double basePower = input.armed() ? debugMotorPower : 0.0;
		double baseRpm = input.armed() ? debugAverageMotorRpm : 0.0;
		for (int i = 0; i < rotorCount; i++) {
			double mix = 1.0 + rotorMixerPreview(i, input);
			motorPower[i] = MathUtil.clamp(basePower * mix, 0.0, 1.0);
			motorRpm[i] = Math.max(0.0, baseRpm * mix);
			rotorThrust[i] = motorPower[i] * physics.config().rotors().get(i).maxThrustNewtons() * 0.45;
		}
		setPerRotorFlightState(motorPower, motorRpm, rotorThrust, rotorHealth);
	}

	private double rotorMixerPreview(int rotorIndex, DroneInput input) {
		double rollSign = switch (rotorIndex & 3) {
			case 0, 3 -> 1.0;
			default -> -1.0;
		};
		double pitchSign = switch (rotorIndex & 3) {
			case 0, 1 -> 1.0;
			default -> -1.0;
		};
		double yawSign = physics.config().rotors().get(rotorIndex).spinDirection();
		return 0.04 * input.roll() * rollSign
				+ 0.04 * input.pitch() * pitchSign
				+ 0.025 * input.yaw() * yawSign;
	}

	private static final float DEBUG_ARM_THRUST_THRESHOLD = 0.005f;
	private static final float DEBUG_AXIS_MOTION_THRESHOLD = 0.02f;

	private static DroneInput stableIdleInput() {
		return new DroneInput(0.0, 0.0, 0.0, 0.0, false, false, FlightMode.ANGLE);
	}

	private static boolean hasDebugControlIntent(DroneInput input) {
		if (input == null || !input.linkActive()) {
			return false;
		}
		return input.throttle() >= DEBUG_ARM_THRUST_THRESHOLD
				|| Math.abs(input.pitch()) >= DEBUG_AXIS_MOTION_THRESHOLD
				|| Math.abs(input.roll()) >= DEBUG_AXIS_MOTION_THRESHOLD
				|| Math.abs(input.yaw()) >= DEBUG_AXIS_MOTION_THRESHOLD;
	}

	private float applyDebugAxisFilter(float current, float target, float riseSmoothing, float fallSmoothing, boolean keepSign) {
		boolean fromCenter = Math.abs(current) < 1.0e-5f;
		boolean sameDirection = fromCenter || Math.signum(current) == Math.signum(target);
		boolean rising = sameDirection && Math.abs(target) > Math.abs(current);
		float smoothing = rising ? riseSmoothing : fallSmoothing;
		float filtered = current + (target - current) * Math.max(0.0f, Math.min(1.0f, smoothing));
		return keepSign ? filtered : Math.max(0.0f, filtered);
	}

	private float clampAxis(float value, float maxAbs) {
		if (value > maxAbs) {
			return maxAbs;
		}
		if (value < -maxAbs) {
			return -maxAbs;
		}
		return value;
	}

	private DroneEnvironment sampleEnvironment() {
		Vec3 sourceWind = environmentOverride.windOr(weatherWindMetersPerSecond());
		DroneWakeAirflow droneWake = sampleDroneWakeAirflow();
		ObstacleAirflow obstacleAirflow = sampleObstacleAirflow(sourceWind.add(droneWake.windVelocityWorldMetersPerSecond()));
		double groundClearance = groundClearanceMeters();
		double ceilingClearance = ceilingClearanceMeters();
		RotorEnvironmentEffects rotorEffects = sampleRotorEnvironmentEffects();
		WaterImmersion waterImmersion = sampleWaterImmersion();
		PrecipitationWetness precipitationWetness = samplePrecipitationWetness(sourceWind.length());
		double ambientTemperature = ambientTemperatureCelsius();
		double turbulenceIntensity = MathUtil.clamp(
				environmentOverride.turbulenceOr(weatherTurbulenceIntensity(sourceWind.length(), groundClearance))
						+ obstacleAirflow.turbulenceBoost()
						+ droneWake.turbulenceBoost()
						+ ceilingTurbulenceBoost(ceilingClearance)
						+ rotorEffects.maxFlowObstruction() * 0.24
						+ precipitationWetness.averageWetness() * 0.07,
				0.0,
				1.5
		);
		return new DroneEnvironment(
				obstacleAirflow.windVelocityWorldMetersPerSecond(),
				environmentOverride.airDensityOr(airDensityRatio(ambientTemperature)),
				groundClearance,
				turbulenceIntensity,
				obstacleAirflow.obstacleProximity(),
				droneWake.intensity(),
				ceilingClearance,
				rotorEffects.thrustMultipliers(),
				rotorEffects.flowObstructions(),
				rotorEffects.flowObstructionDirectionsBody(),
				waterImmersion.rotorImmersions(),
				waterImmersion.averageImmersion(),
				precipitationWetness.rotorWetnesses(),
				precipitationWetness.averageWetness(),
				ambientTemperature
		);
	}

	private DroneEnvironment sampleActiveEnvironment(DroneInput input) {
		return advancedEnvironmentEffectsActive(input) ? sampleEnvironment() : samplePlayableStageOneEnvironment();
	}

	private DroneEnvironment samplePlayableStageOneEnvironment() {
		double groundClearance = groundClearanceMeters();
		double ambientTemperature = ambientTemperatureCelsius();
		return new DroneEnvironment(
				Vec3.ZERO,
				environmentOverride.airDensityOr(airDensityRatio(ambientTemperature)),
				groundClearance,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				ambientTemperature
		);
	}

	private boolean advancedEnvironmentEffectsActive(DroneInput input) {
		if (PLAYABLE_STAGE_ONE_PHYSICS || input == null || !input.armed()) {
			return false;
		}
		double clearance = groundClearanceMetersAt(entityPhysicsPosition());
		return !Double.isFinite(clearance) || clearance >= ADVANCED_EFFECTS_CLEARANCE_METERS;
	}

	private PrecipitationWetness samplePrecipitationWetness(double windSpeedMetersPerSecond) {
		int rotorCount = physics.config().rotors().size();
		if (!level().isRaining()) {
			return PrecipitationWetness.dry(rotorCount);
		}

		Vec3 bodyCenterWorld = entityPhysicsPosition();
		BlockPos bodyPosition = BlockPos.containing(bodyCenterWorld.x(), bodyCenterWorld.y() + 0.18, bodyCenterWorld.z());
		double weatherWetness = level().isThundering() ? 0.78 : 0.45;
		double windFactor = MathUtil.clamp(windSpeedMetersPerSecond / 9.0, 0.0, 1.0);
		double wetnessScale = weatherWetness * (0.82 + 0.18 * windFactor);
		double bodyWetness = wetnessScale * precipitationExposureAt(bodyPosition);
		double weightedWetness = bodyWetness * 1.10;
		double totalWeight = 1.10;
		double[] rotorWetnesses = new double[rotorCount];
		Vec3 bodyXWorld = physics.state().orientation().rotate(new Vec3(1.0, 0.0, 0.0));
		Vec3 bodyZWorld = physics.state().orientation().rotate(new Vec3(0.0, 0.0, 1.0));

		for (int i = 0; i < rotorCount; i++) {
			RotorSpec rotor = physics.config().rotors().get(i);
			Vec3 rotorCenterWorld = bodyCenterWorld.add(physics.state().orientation().rotate(rotor.positionBodyMeters()));
			double rotorWetness = wetnessScale * rotorPrecipitationExposureAt(rotorCenterWorld, rotor, bodyXWorld, bodyZWorld);
			rotorWetnesses[i] = MathUtil.clamp(rotorWetness, 0.0, 1.0);
			weightedWetness += rotorWetnesses[i];
			totalWeight += 1.0;
		}

		double averageWetness = totalWeight <= 0.0 ? 0.0 : MathUtil.clamp(weightedWetness / totalWeight, 0.0, 1.0);
		return new PrecipitationWetness(rotorWetnesses, averageWetness);
	}

	private double precipitationExposureAt(BlockPos position) {
		if (level().isRainingAt(position)) {
			return 1.0;
		}
		return level().canSeeSky(position) ? 0.70 : 0.0;
	}

	private double rotorPrecipitationExposureAt(Vec3 rotorCenterWorld, RotorSpec rotor, Vec3 bodyXWorld, Vec3 bodyZWorld) {
		double diskRadius = MathUtil.clamp(rotor.radiusMeters() * 2.4, 0.10, 0.26);
		double upperDiskOffset = MathUtil.clamp(rotor.radiusMeters() * 0.35, 0.03, 0.10);
		double exposure = 0.0;
		double weight = 0.0;
		exposure += precipitationExposureAt(BlockPos.containing(
				rotorCenterWorld.x(),
				rotorCenterWorld.y() + upperDiskOffset,
				rotorCenterWorld.z()
		)) * 0.40;
		weight += 0.40;

		Vec3[] diskOffsets = {
				bodyXWorld.multiply(diskRadius),
				bodyXWorld.multiply(-diskRadius),
				bodyZWorld.multiply(diskRadius),
				bodyZWorld.multiply(-diskRadius)
		};
		for (Vec3 offset : diskOffsets) {
			Vec3 samplePosition = rotorCenterWorld.add(offset);
			exposure += precipitationExposureAt(BlockPos.containing(
					samplePosition.x(),
					samplePosition.y() + upperDiskOffset,
					samplePosition.z()
			)) * 0.15;
			weight += 0.15;
		}
		return weight <= 0.0 ? 0.0 : MathUtil.clamp(exposure / weight, 0.0, 1.0);
	}

	private double ambientTemperatureCelsius() {
		Vec3 bodyCenterWorld = entityPhysicsPosition();
		BlockPos position = BlockPos.containing(bodyCenterWorld.x(), bodyCenterWorld.y(), bodyCenterWorld.z());
		double biomeTemperature = level().getBiome(position).value().getBaseTemperature();
		double temperature = 15.0 + (biomeTemperature - 0.5) * 18.0;
		double altitudeCooling = Math.max(0.0, bodyCenterWorld.y() - 64.0) * 0.015;
		long dayTime = level().getDayTime() % 24000L;
		double dayPhase = dayTime / 24000.0;
		double diurnalSwing = Math.cos((dayPhase - 0.25) * Math.PI * 2.0) * 3.0;
		double weatherCooling = level().isThundering() ? 5.0 : level().isRaining() ? 3.0 : 0.0;
		return MathUtil.clamp(temperature - altitudeCooling + diurnalSwing - weatherCooling, -40.0, 65.0);
	}

	private WaterImmersion sampleWaterImmersion() {
		int rotorCount = physics.config().rotors().size();
		if (rotorCount <= 0) {
			return WaterImmersion.dry(0);
		}

		double weightedWater = 0.0;
		double totalWeight = 0.0;
		double[] rotorWater = new double[rotorCount];
		Vec3 bodyCenterWorld = entityPhysicsPosition();
		Vec3 bodyXWorld = physics.state().orientation().rotate(new Vec3(1.0, 0.0, 0.0));
		Vec3 bodyZWorld = physics.state().orientation().rotate(new Vec3(0.0, 0.0, 1.0));

		weightedWater += waterSampleAt(bodyCenterWorld) * 1.35;
		totalWeight += 1.35;
		weightedWater += waterSampleAt(bodyCenterWorld.add(new Vec3(0.0, 0.24, 0.0))) * 0.75;
		totalWeight += 0.75;
		weightedWater += waterSampleAt(bodyCenterWorld.add(new Vec3(0.0, -0.10, 0.0))) * 0.55;
		totalWeight += 0.55;

		for (int i = 0; i < rotorCount; i++) {
			RotorSpec rotor = physics.config().rotors().get(i);
			Vec3 rotorCenterWorld = bodyCenterWorld.add(physics.state().orientation().rotate(rotor.positionBodyMeters()));
			double rotorImmersion = rotorWaterImmersionAt(rotorCenterWorld, rotor, bodyXWorld, bodyZWorld);
			rotorWater[i] = rotorImmersion;
			weightedWater += rotorImmersion * 1.30;
			totalWeight += 1.30;
		}

		double averageWater = totalWeight <= 0.0 ? 0.0 : MathUtil.clamp(weightedWater / totalWeight, 0.0, 1.0);
		return new WaterImmersion(rotorWater, averageWater);
	}

	private double rotorWaterImmersionAt(Vec3 rotorCenterWorld, RotorSpec rotor, Vec3 bodyXWorld, Vec3 bodyZWorld) {
		double diskRadius = MathUtil.clamp(rotor.radiusMeters() * 3.6, 0.14, 0.30);
		double lowerDiskOffset = MathUtil.clamp(rotor.radiusMeters() * 0.45, 0.04, 0.13);
		double water = 0.0;
		double weight = 0.0;
		water += waterSampleAt(rotorCenterWorld) * 0.34;
		weight += 0.34;
		water += waterSampleAt(rotorCenterWorld.add(new Vec3(0.0, -lowerDiskOffset, 0.0))) * 0.26;
		weight += 0.26;

		Vec3[] diskOffsets = {
				bodyXWorld.multiply(diskRadius),
				bodyXWorld.multiply(-diskRadius),
				bodyZWorld.multiply(diskRadius),
				bodyZWorld.multiply(-diskRadius)
		};
		for (Vec3 offset : diskOffsets) {
			water += waterSampleAt(rotorCenterWorld.add(offset)) * 0.10;
			weight += 0.10;
		}
		return weight <= 0.0 ? 0.0 : MathUtil.clamp(water / weight, 0.0, 1.0);
	}

	private double waterSampleAt(Vec3 worldPosition) {
		BlockPos position = BlockPos.containing(worldPosition.x(), worldPosition.y(), worldPosition.z());
		return level().getFluidState(position).is(FluidTags.WATER) ? 1.0 : 0.0;
	}

	private RotorEnvironmentEffects sampleRotorEnvironmentEffects() {
		int rotorCount = physics.config().rotors().size();
		if (rotorCount <= 0) {
			return RotorEnvironmentEffects.calm(0);
		}

		double[] multipliers = new double[rotorCount];
		double[] flowObstructions = new double[rotorCount];
		Vec3[] flowObstructionDirectionsBody = new Vec3[rotorCount];
		double maxFlowObstruction = 0.0;
		Vec3 bodyCenterWorld = entityPhysicsPosition();
		RotorPlaneSampleDirection[] rotorPlaneDirections = rotorPlaneSampleDirections();
		for (int i = 0; i < rotorCount; i++) {
			RotorSpec rotor = physics.config().rotors().get(i);
			Vec3 rotorCenterWorld = bodyCenterWorld.add(physics.state().orientation().rotate(rotor.positionBodyMeters()));
			RotorDiskSurfaceSample surfaceSample = rotorDiskSurfaceSample(rotorCenterWorld, rotor, rotorPlaneDirections);
			RotorFlowObstruction flowObstruction = rotorSideFlowObstruction(rotorCenterWorld, rotor, rotorPlaneDirections);
			double flowObstructionIntensity = flowObstruction.intensity();
			flowObstructionDirectionsBody[i] = flowObstruction.directionBody();
			flowObstructions[i] = flowObstructionIntensity;
			maxFlowObstruction = Math.max(maxFlowObstruction, flowObstructionIntensity);
			double obstructionThrustMultiplier = RotorFlowObstructionModel.thrustMultiplier(flowObstructionIntensity);
			multipliers[i] = DroneEnvironment.weightedGroundEffectThrustMultiplier(
							physics.config(),
							surfaceSample.groundClearancesMeters(),
							surfaceSample.weights()
					)
					* DroneEnvironment.weightedCeilingEffectThrustMultiplier(
							physics.config(),
							surfaceSample.ceilingClearancesMeters(),
							surfaceSample.weights()
					)
					* obstructionThrustMultiplier;
		}
		return new RotorEnvironmentEffects(multipliers, flowObstructions, flowObstructionDirectionsBody, maxFlowObstruction);
	}

	private RotorDiskSurfaceSample rotorDiskSurfaceSample(Vec3 rotorCenterWorld, RotorSpec rotor, RotorPlaneSampleDirection[] sampleDirections) {
		int sampleCount = 1 + sampleDirections.length;
		double[] groundClearances = new double[sampleCount];
		double[] ceilingClearances = new double[sampleCount];
		double[] weights = new double[sampleCount];
		groundClearances[0] = groundClearanceMetersAt(rotorCenterWorld);
		ceilingClearances[0] = ceilingClearanceMetersAt(rotorCenterWorld);
		weights[0] = ROTOR_DISK_SURFACE_CENTER_WEIGHT;

		double sampleRadius = rotor.radiusMeters() * ROTOR_DISK_SURFACE_SAMPLE_RADIUS_SCALE;
		for (int i = 0; i < sampleDirections.length; i++) {
			Vec3 samplePosition = rotorCenterWorld.add(sampleDirections[i].worldDirection().multiply(sampleRadius));
			int sampleIndex = i + 1;
			groundClearances[sampleIndex] = groundClearanceMetersAt(samplePosition);
			ceilingClearances[sampleIndex] = ceilingClearanceMetersAt(samplePosition);
			weights[sampleIndex] = i < 4 ? ROTOR_DISK_SURFACE_CARDINAL_WEIGHT : ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT;
		}
		return new RotorDiskSurfaceSample(groundClearances, ceilingClearances, weights);
	}

	private RotorPlaneSampleDirection[] rotorPlaneSampleDirections() {
		Vec3[] bodyDirections = {
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(-1.0, 0.0, 0.0),
				new Vec3(0.0, 0.0, 1.0),
				new Vec3(0.0, 0.0, -1.0),
				new Vec3(1.0, 0.0, 1.0).normalized(),
				new Vec3(1.0, 0.0, -1.0).normalized(),
				new Vec3(-1.0, 0.0, 1.0).normalized(),
				new Vec3(-1.0, 0.0, -1.0).normalized()
		};
		RotorPlaneSampleDirection[] directions = new RotorPlaneSampleDirection[bodyDirections.length];
		for (int i = 0; i < bodyDirections.length; i++) {
			directions[i] = new RotorPlaneSampleDirection(
					bodyDirections[i],
					physics.state().orientation().rotate(bodyDirections[i]).normalized()
			);
		}
		return directions;
	}

	private RotorFlowObstruction rotorSideFlowObstruction(Vec3 rotorCenterWorld, RotorSpec rotor, RotorPlaneSampleDirection[] sampleDirections) {
		double scanDistanceMeters = MathUtil.clamp(rotor.radiusMeters() * 2.4 + 0.22, 0.32, 0.82);
		double[] distancesMeters = new double[sampleDirections.length];
		Vec3[] bodyDirections = new Vec3[sampleDirections.length];
		for (int i = 0; i < sampleDirections.length; i++) {
			RotorPlaneSampleDirection direction = sampleDirections[i];
			distancesMeters[i] = obstacleDistanceMetersFrom(rotorCenterWorld, direction.worldDirection(), scanDistanceMeters);
			bodyDirections[i] = direction.bodyDirection();
		}

		RotorFlowObstructionModel.Result obstruction = RotorFlowObstructionModel.fromDirectionalDistances(
				distancesMeters,
				bodyDirections,
				scanDistanceMeters,
				rotor.radiusMeters()
		);
		return obstruction.intensity() <= 1.0e-6
				? RotorFlowObstruction.CLEAR
				: new RotorFlowObstruction(obstruction.intensity(), obstruction.directionBody());
	}

	private DroneWakeAirflow sampleDroneWakeAirflow() {
		AABB wakeSearch = getBoundingBox().inflate(7.0, 5.5, 7.0);
		List<DroneEntity> drones = level().getEntities(
				EntityTypeTest.forClass(DroneEntity.class),
				wakeSearch,
				drone -> drone != this && drone.isAlive()
		);
		if (drones.isEmpty()) {
			return DroneWakeAirflow.CALM;
		}

		Vec3 totalWind = Vec3.ZERO;
		double turbulence = 0.0;
		double maxIntensity = 0.0;
		Vec3 position = entityPhysicsPosition();
		for (DroneEntity source : drones) {
			DroneWakeAirflow wake = wakeFromDrone(source, position);
			totalWind = totalWind.add(wake.windVelocityWorldMetersPerSecond());
			turbulence += wake.turbulenceBoost();
			maxIntensity = Math.max(maxIntensity, wake.intensity());
		}
		return new DroneWakeAirflow(
				totalWind.clamp(-14.0, 14.0),
				MathUtil.clamp(turbulence, 0.0, 0.85),
				MathUtil.clamp(maxIntensity, 0.0, 1.5)
		);
	}

	private DroneWakeAirflow wakeFromDrone(DroneEntity source, Vec3 receiverPosition) {
		Vec3 sourcePosition = source.entityPhysicsPosition();
		Vec3 offset = receiverPosition.subtract(sourcePosition);
		double verticalDrop = sourcePosition.y() - receiverPosition.y();
		if (verticalDrop < -0.25 || verticalDrop > 5.0) {
			return DroneWakeAirflow.CALM;
		}

		double lateralDistance = Math.hypot(offset.x(), offset.z());
		double wakeRadius = sourceWakeRadiusMeters(source) + verticalDrop * 0.35;
		if (lateralDistance > wakeRadius) {
			return DroneWakeAirflow.CALM;
		}

		double motorPower = source.physics.state().averageMotorPower(source.physics.config());
		if (!source.isArmed() || motorPower < 0.08) {
			return DroneWakeAirflow.CALM;
		}

		double lateralFactor = 1.0 - MathUtil.clamp(lateralDistance / Math.max(0.1, wakeRadius), 0.0, 1.0);
		double verticalFactor = MathUtil.clamp(1.0 - verticalDrop / 5.0, 0.0, 1.0);
		double inducedVelocity = source.physics.state().averageRotorInducedVelocityMetersPerSecond();
		double intensity = MathUtil.clamp(motorPower * (0.35 + 0.65 * verticalFactor) * lateralFactor * lateralFactor, 0.0, 1.5);
		if (intensity <= 1.0e-4) {
			return DroneWakeAirflow.CALM;
		}

		Vec3 carrierVelocity = source.physics.state().velocityMetersPerSecond().multiply(0.18 * intensity);
		double downwashMetersPerSecond = MathUtil.clamp(inducedVelocity * (0.45 + motorPower) * intensity, 0.0, 12.0);
		Vec3 wind = new Vec3(carrierVelocity.x(), -downwashMetersPerSecond, carrierVelocity.z());
		double turbulence = MathUtil.clamp(0.18 + intensity * 0.72, 0.0, 0.85);
		return new DroneWakeAirflow(wind, turbulence, intensity);
	}

	private static double sourceWakeRadiusMeters(DroneEntity source) {
		double radius = 0.35;
		for (RotorSpec rotor : source.physics.config().rotors()) {
			radius = Math.max(radius, rotor.positionBodyMeters().length() + rotor.radiusMeters() * 2.5);
		}
		return MathUtil.clamp(radius, 0.35, 1.25);
	}

	private ObstacleAirflow sampleObstacleAirflow(Vec3 weatherWindMetersPerSecond) {
		double scanDistanceMeters = 4.5;
		double obstacleProximity = 0.0;
		Vec3[] sampleDirections = {
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(-1.0, 0.0, 0.0),
				new Vec3(0.0, 0.0, 1.0),
				new Vec3(0.0, 0.0, -1.0),
				new Vec3(1.0, 0.0, 1.0),
				new Vec3(1.0, 0.0, -1.0),
				new Vec3(-1.0, 0.0, 1.0),
				new Vec3(-1.0, 0.0, -1.0),
				new Vec3(0.0, 1.0, 0.0)
		};
		for (Vec3 direction : sampleDirections) {
			obstacleProximity = Math.max(
					obstacleProximity,
					obstacleProximityFromDistance(obstacleDistanceMeters(direction, scanDistanceMeters), scanDistanceMeters)
			);
		}

		double windSpeed = weatherWindMetersPerSecond.length();
		double upstreamProximity = 0.0;
		if (windSpeed > 0.2) {
			Vec3 upstreamDirection = weatherWindMetersPerSecond.normalized().multiply(-1.0);
			upstreamProximity = obstacleProximityFromDistance(obstacleDistanceMeters(upstreamDirection, scanDistanceMeters), scanDistanceMeters);
		}

		double windShadow = 0.65 * upstreamProximity * upstreamProximity;
		Vec3 shadedWind = weatherWindMetersPerSecond.multiply(1.0 - windShadow);
		double windFactor = MathUtil.clamp(windSpeed / 9.0, 0.0, 1.0);
		double turbulenceBoost = MathUtil.clamp(
				obstacleProximity * 0.22 + upstreamProximity * (0.18 + 0.25 * windFactor),
				0.0,
				0.75
		);
		return new ObstacleAirflow(shadedWind, Math.max(obstacleProximity, upstreamProximity), turbulenceBoost);
	}

	private double obstacleDistanceMeters(Vec3 direction, double maxDistanceMeters) {
		return obstacleDistanceMetersFrom(entityPhysicsPosition(), direction, maxDistanceMeters);
	}

	private double obstacleDistanceMetersFrom(Vec3 startWorld, Vec3 direction, double maxDistanceMeters) {
		Vec3 normalized = direction.normalized();
		if (normalized.lengthSquared() <= 1.0e-9) {
			return Double.POSITIVE_INFINITY;
		}

		net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(startWorld.x(), startWorld.y(), startWorld.z());
		net.minecraft.world.phys.Vec3 end = start.add(
				normalized.x() * maxDistanceMeters,
				normalized.y() * maxDistanceMeters,
				normalized.z() * maxDistanceMeters
		);
		HitResult hit = level().clip(new ClipContext(
				start,
				end,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				this
		));
		if (hit.getType() == HitResult.Type.MISS) {
			return Double.POSITIVE_INFINITY;
		}
		return Math.max(0.0, hit.getLocation().distanceTo(start));
	}

	private static double obstacleProximityFromDistance(double distanceMeters, double maxDistanceMeters) {
		return RotorFlowObstructionModel.proximityFromDistance(distanceMeters, maxDistanceMeters);
	}

	private Vec3 weatherWindMetersPerSecond() {
		Vec3 bodyCenterWorld = entityPhysicsPosition();
		double baseSpeed = level().isThundering() ? 8.0 : level().isRaining() ? 4.5 : 1.2;
		double altitudeFactor = MathUtil.clamp((bodyCenterWorld.y() - 64.0) / 96.0, 0.0, 1.0);
		double speed = baseSpeed * (0.55 + altitudeFactor * 0.65);
		double time = level().getGameTime() * 0.0015;
		double angle = time + Math.sin(time * 0.37 + bodyCenterWorld.x() * 0.01) * 0.45;
		double gust = Math.sin(time * 3.1 + bodyCenterWorld.z() * 0.04) * speed * (level().isThundering() ? 0.32 : 0.16);
		return new Vec3(
				Math.cos(angle) * speed + Math.cos(angle + 1.7) * gust,
				0.0,
				Math.sin(angle) * speed + Math.sin(angle + 1.7) * gust
		);
	}

	private double weatherTurbulenceIntensity(double windSpeedMetersPerSecond, double groundClearanceMeters) {
		Vec3 bodyCenterWorld = entityPhysicsPosition();
		double weatherBase = level().isThundering() ? 0.72 : level().isRaining() ? 0.42 : 0.14;
		double windFactor = MathUtil.clamp(windSpeedMetersPerSecond / 9.0, 0.0, 1.0);
		double altitudeFactor = MathUtil.clamp((bodyCenterWorld.y() - 72.0) / 96.0, 0.0, 0.55);
		double mechanicalTurbulence = Double.isFinite(groundClearanceMeters)
				? 0.18 * (1.0 - MathUtil.clamp(groundClearanceMeters / 6.0, 0.0, 1.0))
				: 0.0;
		return MathUtil.clamp(weatherBase * (0.35 + 0.65 * windFactor) + altitudeFactor + mechanicalTurbulence, 0.0, 1.0);
	}

	private double airDensityRatio(double ambientTemperatureCelsius) {
		return DroneEnvironment.standardAtmosphereAirDensityRatio(entityPhysicsPosition().y(), ambientTemperatureCelsius);
	}

	private double groundClearanceMeters() {
		return groundClearanceMetersAt(entityPhysicsPosition());
	}

	private double groundClearanceMetersAt(Vec3 worldPosition) {
		double rayLength = Math.max(1.0, physics.config().groundEffectHeightMeters() + 0.5);
		net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(worldPosition.x(), worldPosition.y() + 0.08, worldPosition.z());
		net.minecraft.world.phys.Vec3 end = start.add(0.0, -rayLength, 0.0);
		HitResult hit = level().clip(new ClipContext(
				start,
				end,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				this
		));
		if (hit.getType() == HitResult.Type.MISS) {
			return Double.POSITIVE_INFINITY;
		}
		return Math.max(0.0, worldPosition.y() - hit.getLocation().y());
	}

	private double ceilingClearanceMeters() {
		return ceilingClearanceMetersAt(entityPhysicsPosition());
	}

	private double ceilingClearanceMetersAt(Vec3 worldPosition) {
		double rayLength = Math.max(1.0, physics.config().groundEffectHeightMeters() * 1.25 + 0.5);
		net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(worldPosition.x(), worldPosition.y() + 0.18, worldPosition.z());
		net.minecraft.world.phys.Vec3 end = start.add(0.0, rayLength, 0.0);
		HitResult hit = level().clip(new ClipContext(
				start,
				end,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				this
		));
		if (hit.getType() == HitResult.Type.MISS) {
			return Double.POSITIVE_INFINITY;
		}
		return Math.max(0.0, hit.getLocation().y() - worldPosition.y());
	}

	private double ceilingTurbulenceBoost(double ceilingClearanceMeters) {
		double effectHeight = physics.config().groundEffectHeightMeters() * 1.25;
		if (effectHeight <= 1.0e-6 || !Double.isFinite(ceilingClearanceMeters)) {
			return 0.0;
		}

		effectHeight = Math.max(0.35, effectHeight);
		if (ceilingClearanceMeters >= effectHeight) {
			return 0.0;
		}
		double proximity = 1.0 - MathUtil.clamp(ceilingClearanceMeters / effectHeight, 0.0, 1.0);
		return MathUtil.clamp(0.30 * proximity * proximity, 0.0, 0.45);
	}

	public double getPhysicsCenterYOffsetMeters() {
		return physicsCenterYOffsetMeters();
	}

	private Vec3 entityPhysicsPosition() {
		return new Vec3(getX(), getY() + physicsCenterYOffsetMeters(), getZ());
	}

	private double entityYFromPhysicsPosition(Vec3 physicsPosition) {
		return physicsPosition.y() - physicsCenterYOffsetMeters();
	}

	private double physicsCenterYOffsetMeters() {
		return Math.max(0.05, getBoundingBox().getYsize() * 0.5);
	}

	private boolean shouldSleepOnGround(DroneInput input) {
		if (input == null || !wantsGroundSleep(input)) {
			return false;
		}
		if (onGround() || verticalCollision) {
			return true;
		}
		double clearance = groundClearanceMetersAt(entityPhysicsPosition());
		return Double.isFinite(clearance)
				&& clearance <= groundLockClearanceMeters()
				&& physics.state().velocityMetersPerSecond().y() <= 0.05;
	}

	private boolean wantsGroundSleep(DroneInput input) {
		if (input == null) {
			return true;
		}
		return !input.armed();
	}

	private boolean shouldConstrainOnGround(DroneInput input) {
		if (input == null || !input.armed()) {
			return false;
		}
		if (onGround() || verticalCollision) {
			return true;
		}
		double clearance = groundClearanceMetersAt(entityPhysicsPosition());
		return Double.isFinite(clearance) && clearance <= groundLockClearanceMeters();
	}

	private boolean hasTakeoffAuthority(DroneInput input) {
		if (input == null || !input.armed()) {
			return false;
		}
		double throttleRelease = Math.max(0.18, physics.config().hoverThrottle() * 0.95);
		return input.throttle() >= throttleRelease
				&& verticalRotorThrustNewtons() >= physics.config().massKg() * physics.config().gravityMetersPerSecondSquared() * TAKEOFF_THRUST_TO_WEIGHT;
	}

	private double verticalRotorThrustNewtons() {
		double thrust = 0.0;
		int rotorCount = Math.min(physics.config().rotors().size(), physics.state().motorCount());
		for (int i = 0; i < rotorCount; i++) {
			Vec3 thrustAxisWorld = physics.state().orientation().rotate(physics.config().rotors().get(i).thrustAxisBody());
			thrust += physics.state().rotorThrustNewtons(i) * Math.max(0.0, thrustAxisWorld.y());
		}
		return thrust;
	}

	private double groundLockClearanceMeters() {
		return physicsCenterYOffsetMeters() + GROUND_LOCK_CLEARANCE_MARGIN_METERS;
	}

	private void prepareGroundTakeoff(DroneInput input) {
		Vec3 position = entityPhysicsPosition().add(new Vec3(0.0, TAKEOFF_POSITION_NUDGE_METERS, 0.0));
		Vec3 velocity = physics.state().velocityMetersPerSecond();
		double throttleLift = MathUtil.clamp(input.throttle(), 0.0, 1.0) * 0.35;
		double minimumVerticalSpeed = TAKEOFF_MIN_VERTICAL_SPEED_METERS_PER_SECOND + throttleLift;
		physics.state().setPositionMeters(position);
		physics.state().setVelocityMetersPerSecond(new Vec3(
				velocity.x(),
				Math.max(velocity.y(), minimumVerticalSpeed),
				velocity.z()
		));
	}

	private boolean advancedContactEffectsActive(DroneInput input) {
		if (PLAYABLE_STAGE_ONE_PHYSICS || input == null || !input.armed()) {
			return false;
		}
		double clearance = groundClearanceMetersAt(entityPhysicsPosition());
		return !Double.isFinite(clearance) || clearance >= ADVANCED_EFFECTS_CLEARANCE_METERS;
	}

	private void sleepOnGround(DroneInput input) {
		physics.sleepAtRest(entityPhysicsPosition(), input);
		setDeltaMovement(0.0, 0.0, 0.0);
	}

	private void constrainOnGround() {
		physics.levelAtRest(entityPhysicsPosition());
		setDeltaMovement(0.0, 0.0, 0.0);
	}

	private void applyPhysicsMovement(DroneInput input) {
		Vec3 targetPosition = physics.state().positionMeters();
		Vec3 velocityBeforeCollision = physics.state().velocityMetersPerSecond();
		double startX = getX();
		double startY = getY();
		double startZ = getZ();
		net.minecraft.world.phys.Vec3 delta = new net.minecraft.world.phys.Vec3(
				targetPosition.x() - startX,
				entityYFromPhysicsPosition(targetPosition) - startY,
				targetPosition.z() - startZ
		);

		move(MoverType.SELF, delta);
		physics.state().setPositionMeters(entityPhysicsPosition());
		boolean collided = horizontalCollision || verticalCollision;

		Vec3 velocity = physics.state().velocityMetersPerSecond();
		if (collided) {
			Vec3 attemptedDelta = new Vec3(delta.x(), delta.y(), delta.z());
			Vec3 actualDelta = new Vec3(getX() - startX, getY() - startY, getZ() - startZ);
			if (input != null && shouldConstrainOnGround(input) && !hasTakeoffAuthority(input) && verticalCollision && velocityBeforeCollision.y() <= 0.0) {
				constrainOnGround();
				return;
			}
			if (input != null && wantsGroundSleep(input) && verticalCollision && velocityBeforeCollision.y() <= 0.0) {
				sleepOnGround(input);
				return;
			}
			if (!advancedContactEffectsActive(input)) {
				velocity = new Vec3(
						horizontalCollision ? 0.0 : velocity.x(),
						verticalCollision ? 0.0 : velocity.y(),
						horizontalCollision ? 0.0 : velocity.z()
				);
				physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
				physics.state().setContactTelemetry(velocityBeforeCollision.length(), 0.0, 0.0);
				physics.state().setVelocityMetersPerSecond(velocity);
				setDeltaMovement(velocity.x() * 0.05, velocity.y() * 0.05, velocity.z() * 0.05);
				return;
			}
			ContactDynamics.Response preliminaryContact = ContactDynamics.resolve(
					velocityBeforeCollision,
					attemptedDelta,
					actualDelta,
					horizontalCollision,
					verticalCollision
			);
			ContactDynamics.ContactSurface contactSurface = contactSurfaceForNormal(preliminaryContact.contactNormalWorld());
			ContactDynamics.Response contact = ContactDynamics.resolve(
					velocityBeforeCollision,
					attemptedDelta,
					actualDelta,
					horizontalCollision,
					verticalCollision,
					contactSurface
			);
			Vec3 angularImpulse = ContactDynamics.angularVelocityImpulseBody(
					physics.config(),
					physics.state().orientation(),
					velocityBeforeCollision,
					contact.contactNormalWorld(),
					contact.impactSpeedMetersPerSecond(),
					contact.slipSpeedMetersPerSecond(),
					contactSurface
			);
			velocity = contact.velocityMetersPerSecond();
			physics.state().setAngularVelocityBodyRadiansPerSecond(
					physics.state().angularVelocityBodyRadiansPerSecond().add(angularImpulse).clamp(-40.0, 40.0)
			);
			physics.state().setContactTelemetry(
					contact.impactSpeedMetersPerSecond(),
					contact.slipSpeedMetersPerSecond(),
					contact.bounceSpeedMetersPerSecond(),
					angularImpulse,
					contactSurface
			);
			applyCollisionDamage(velocityBeforeCollision, contact.impactSpeedMetersPerSecond());
		} else {
			physics.state().setContactTelemetry(0.0, 0.0, 0.0);
		}
		physics.state().setVelocityMetersPerSecond(velocity);
		setDeltaMovement(velocity.x() * 0.05, velocity.y() * 0.05, velocity.z() * 0.05);
	}

	private void updateSyncedFlightState(DroneInput input) {
		Vec3 euler = physics.state().orientation().toEulerXYZRadians();
		DroneInput processedInput = physics.state().processedControlInput();
		entityData.set(ARMED, processedInput.armed());
		entityData.set(FLIGHT_MODE, syncedFlightMode(input, processedInput).id());
		syncAirframeLayout();
		entityData.set(PITCH, (float) euler.x());
		entityData.set(YAW, (float) euler.y());
		entityData.set(ROLL, (float) euler.z());
		entityData.set(CONTROL_THROTTLE, (float) processedInput.throttle());
		entityData.set(CONTROL_PITCH, (float) processedInput.pitch());
		entityData.set(CONTROL_ROLL, (float) processedInput.roll());
		entityData.set(CONTROL_YAW, (float) processedInput.yaw());
		entityData.set(CONTROL_LINK_LOSS_SECONDS, (float) physics.state().controlLinkLossSeconds());
		entityData.set(RAW_CONTROL_LINK_ACTIVE, input.linkActive());
		entityData.set(PROCESSED_CONTROL_LINK_ACTIVE, processedInput.linkActive());
		entityData.set(CONTROL_FAILSAFE, physics.state().controlFailsafeActive());
		Vec3 targetRates = physics.state().targetRatesBodyRadiansPerSecond();
		Vec3 gyroRates = physics.state().gyroAngularVelocityBodyRadiansPerSecond();
		Vec3 pidOutput = physics.state().pidOutputTorqueBodyNewtonMeters();
		Vec3 estimatedEuler = physics.state().estimatedOrientation().toEulerXYZRadians();
		Vec3 attitudeError = physics.state().attitudeEstimateErrorRadians();
		entityData.set(TARGET_PITCH_RATE, (float) Math.toDegrees(targetRates.x()));
		entityData.set(TARGET_YAW_RATE, (float) Math.toDegrees(targetRates.y()));
		entityData.set(TARGET_ROLL_RATE, (float) Math.toDegrees(targetRates.z()));
		entityData.set(GYRO_PITCH_RATE, (float) Math.toDegrees(gyroRates.x()));
		entityData.set(GYRO_YAW_RATE, (float) Math.toDegrees(gyroRates.y()));
		entityData.set(GYRO_ROLL_RATE, (float) Math.toDegrees(gyroRates.z()));
		entityData.set(GYRO_CLIP, (float) physics.state().gyroClipIntensity());
		entityData.set(ACCEL_CLIP, (float) physics.state().accelerometerClipIntensity());
		entityData.set(IMU_SUPPLY_NOISE, (float) physics.state().imuSupplyNoiseIntensity());
		entityData.set(GYRO_NOTCH_FREQUENCY, (float) physics.state().gyroDynamicNotchFrequencyHertz());
		entityData.set(GYRO_NOTCH_ATTENUATION, (float) physics.state().gyroDynamicNotchAttenuation());
		entityData.set(PID_PITCH_OUTPUT, (float) pidOutput.x());
		entityData.set(PID_YAW_OUTPUT, (float) pidOutput.y());
		entityData.set(PID_ROLL_OUTPUT, (float) pidOutput.z());
		entityData.set(PID_ATTENUATION, (float) physics.state().pidAttenuation());
		entityData.set(PID_INTEGRAL_RELAX, (float) physics.state().pidIntegralRelax());
		entityData.set(PID_DTERM_LPF_CUTOFF, (float) physics.state().pidDTermLowPassCutoffHertz());
		entityData.set(ANTI_GRAVITY_BOOST, (float) physics.state().antiGravityBoost());
		entityData.set(ESTIMATED_PITCH, (float) Math.toDegrees(estimatedEuler.x()));
		entityData.set(ESTIMATED_YAW, (float) Math.toDegrees(estimatedEuler.y()));
		entityData.set(ESTIMATED_ROLL, (float) Math.toDegrees(estimatedEuler.z()));
		entityData.set(ATTITUDE_ESTIMATE_ERROR, (float) Math.toDegrees(attitudeError.length()));
		entityData.set(ATTITUDE_ACCEL_TRUST, (float) physics.state().attitudeEstimatorAccelerometerTrust());
		entityData.set(MOTOR_POWER, (float) physics.state().averageMotorPower(physics.config()));
		entityData.set(MOTOR_RPM, (float) physics.state().averageMotorRpm());
		entityData.set(MOTOR_TEMPERATURE, (float) physics.state().maxMotorTemperatureCelsius());
		entityData.set(MOTOR_THERMAL_LIMIT, (float) physics.state().motorThermalLimit());
		entityData.set(MOTOR_VOLTAGE_HEADROOM, (float) physics.state().minMotorVoltageHeadroom());
		entityData.set(MOTOR_WINDING_RESISTANCE_SCALE, (float) physics.state().maxMotorWindingResistanceScale());
		entityData.set(ESC_TEMPERATURE, (float) physics.state().maxEscTemperatureCelsius());
		entityData.set(ESC_THERMAL_LIMIT, (float) physics.state().escThermalLimit());
		entityData.set(ESC_COOLING_FACTOR, (float) physics.state().averageEscCoolingFactor());
		entityData.set(ESC_DESYNC, (float) physics.state().maxEscDesyncIntensity());
		entityData.set(ROTOR_AERODYNAMIC_LOAD, (float) physics.state().averageRotorAerodynamicLoadFactor());
		entityData.set(ROTOR_IN_PLANE_DRAG_FORCE, (float) physics.state().maxRotorInPlaneDragForceNewtons());
		double[] motorPower = physics.state().motorPower(physics.config());
		double[] motorRpm = physics.state().motorRpm();
		double[] rotorThrust = physics.state().rotorThrustNewtons();
		double[] rotorHealth = physics.state().rotorHealth();
		setPerRotorFlightState(motorPower, motorRpm, rotorThrust, rotorHealth);
		entityData.set(ROTOR_VIBRATION, (float) physics.state().rotorVibration());
		entityData.set(ROTOR_CONING_INTENSITY, (float) physics.state().averageRotorConingIntensity());
		entityData.set(ROTOR_STALL_INTENSITY, (float) physics.state().averageRotorStallIntensity());
		entityData.set(ROTOR_FLAPPING_TILT, (float) Math.toDegrees(physics.state().averageRotorFlappingTiltRadians()));
		entityData.set(ROTOR_SURFACE_SCRAPE, (float) physics.state().maxRotorSurfaceScrapeIntensity());
		entityData.set(ROTOR_TRANSLATIONAL_LIFT, (float) physics.state().averageRotorTranslationalLiftIntensity());
		entityData.set(ROTOR_ADVANCE_RATIO, (float) physics.state().maxRotorAdvanceRatio());
		entityData.set(ROTOR_PROPELLER_ADVANCE_RATIO_J, (float) physics.state().maxRotorPropellerAdvanceRatioJ());
		entityData.set(ROTOR_PROPELLER_THRUST_SCALE, (float) physics.state().minRotorPropellerThrustScale());
		entityData.set(ROTOR_PROPELLER_POWER_SCALE, (float) physics.state().minRotorPropellerPowerScale());
		entityData.set(ROTOR_REVERSE_FLOW, (float) physics.state().maxRotorReverseFlowInboardFraction());
		entityData.set(ROTOR_TIP_MACH, (float) physics.state().maxRotorTipMach());
		entityData.set(ROTOR_COMPRESSIBILITY_THRUST_SCALE, (float) physics.state().minRotorCompressibilityThrustScale());
		entityData.set(ROTOR_LOW_REYNOLDS_LOSS, (float) physics.state().maxRotorLowReynoldsLoss());
		entityData.set(ROTOR_BLADE_AOA, (float) Math.toDegrees(physics.state().maxAbsRotorBladeAngleOfAttackRadians()));
		entityData.set(ROTOR_BLADE_ELEMENT_STALL, (float) physics.state().maxRotorBladeElementStallIntensity());
		entityData.set(ROTOR_BLADE_PASS_RIPPLE, (float) physics.state().maxRotorBladePassRippleIntensity());
		entityData.set(ROTOR_BLADE_DISSYMMETRY_TORQUE, (float) physics.state().rotorBladeDissymmetryTorqueBodyNewtonMeters().length());
		entityData.set(ROTOR_INFLOW_SKEW, (float) physics.state().rotorInflowSkewIntensity());
		entityData.set(ROTOR_INDUCED_VELOCITY, (float) physics.state().maxRotorInducedVelocityMetersPerSecond());
		entityData.set(ROTOR_INDUCED_LAG_THRUST_SCALE, (float) physics.state().minRotorInducedLagThrustScale());
		entityData.set(ROTOR_WAKE_INTERFERENCE, (float) physics.state().maxRotorWakeInterferenceIntensity());
		entityData.set(ROTOR_WAKE_THRUST_SCALE, (float) physics.state().minRotorWakeThrustScale());
		entityData.set(ROTOR_COAXIAL_LOAD_BIAS, (float) physics.state().maxAbsRotorCoaxialLoadBias());
		entityData.set(ROTOR_WET_THRUST_SCALE, (float) physics.state().minRotorWetThrustScale());
		entityData.set(ROTOR_WAKE_SWIRL, (float) physics.state().maxRotorWakeSwirlVelocityMetersPerSecond());
		entityData.set(ROTOR_WINDMILLING, (float) physics.state().maxRotorWindmillingIntensity());
		entityData.set(ROTOR_WAKE_SWIRL_TORQUE, (float) physics.state().rotorWakeSwirlTorqueBodyNewtonMeters().length());
		entityData.set(ROTOR_ACTIVE_BRAKING_TORQUE, (float) physics.state().rotorActiveBrakingTorqueBodyNewtonMeters().length());
		entityData.set(ROTOR_ACCELERATION_REACTION_TORQUE, (float) physics.state().rotorAccelerationReactionTorqueBodyNewtonMeters().length());
		entityData.set(ROTOR_GYROSCOPIC_TORQUE, (float) physics.state().rotorGyroscopicTorqueBodyNewtonMeters().length());
		entityData.set(ROTOR_FLAPPING_TORQUE, (float) physics.state().rotorFlappingTorqueBodyNewtonMeters().length());
		entityData.set(MIXER_SATURATION, (float) physics.state().mixerSaturation());
		entityData.set(PROPWASH_INTENSITY, (float) physics.state().propwashIntensity());
		entityData.set(VORTEX_RING_STATE, (float) physics.state().vortexRingStateIntensity());
		entityData.set(VORTEX_RING_THRUST_BUFFET, (float) physics.state().maxVortexRingThrustBuffetAmplitude());
		entityData.set(VORTEX_RING_BUFFET_FORCE, (float) physics.state().vortexRingBuffetForceBodyNewtons().length());
		entityData.set(GROUND_EFFECT_MULTIPLIER, (float) lastEnvironment.groundEffectThrustMultiplier(physics.config()));
		entityData.set(CEILING_EFFECT_MULTIPLIER, (float) lastEnvironment.ceilingEffectThrustMultiplier(physics.config()));
		entityData.set(CEILING_EFFECT_INTENSITY, (float) lastEnvironment.ceilingEffectIntensity(physics.config()));
		entityData.set(ENV_THRUST_ASYMMETRY, (float) lastEnvironment.rotorThrustAsymmetry(physics.config()));
		entityData.set(ROTOR_FLOW_OBSTRUCTION, (float) lastEnvironment.maxRotorFlowObstruction());
		entityData.set(WATER_IMMERSION, (float) lastEnvironment.maxRotorWaterImmersion());
		entityData.set(PRECIPITATION_WETNESS, (float) lastEnvironment.precipitationWetnessIntensity());
		entityData.set(AMBIENT_TEMPERATURE, (float) lastEnvironment.ambientTemperatureCelsius());
		Vec3 effectiveWind = physics.state().effectiveWindVelocityWorldMetersPerSecond();
		entityData.set(WIND_SPEED, (float) lastEnvironment.windVelocityWorldMetersPerSecond().length());
		entityData.set(EFFECTIVE_WIND_SPEED, (float) effectiveWind.length());
		entityData.set(WIND_GUST_SPEED, (float) physics.state().windGustSpeedMetersPerSecond());
		entityData.set(WIND_SHEAR_ACCELERATION, (float) physics.state().windShearAccelerationMetersPerSecondSquared());
		entityData.set(TURBULENCE_INTENSITY, (float) lastEnvironment.turbulenceIntensity());
		entityData.set(OBSTACLE_PROXIMITY, (float) lastEnvironment.obstacleProximity());
		entityData.set(DRONE_WAKE_INTENSITY, (float) lastEnvironment.droneWakeIntensity());
		entityData.set(AIRSPEED, (float) physics.state().airspeedMetersPerSecond());
		entityData.set(ANGLE_OF_ATTACK, (float) Math.toDegrees(physics.state().angleOfAttackRadians()));
		entityData.set(SIDESLIP, (float) Math.toDegrees(physics.state().sideslipRadians()));
		entityData.set(AIRFRAME_SEPARATED_FLOW, (float) physics.state().airframeSeparatedFlowIntensity());
		entityData.set(AIRFRAME_LIFT_FORCE, (float) physics.state().airframeLiftForceBodyNewtons().length());
		entityData.set(GROUND_EFFECT_DRAG_FORCE, (float) physics.state().groundEffectDragForceBodyNewtons().length());
		entityData.set(GROUND_EFFECT_LEVELING_TORQUE, (float) physics.state().groundEffectLevelingTorqueBodyNewtonMeters().length());
		entityData.set(ROTOR_WASH_DRAG_FORCE, (float) physics.state().rotorWashDragForceBodyNewtons().length());
		entityData.set(ROTOR_WALL_EFFECT_FORCE, (float) physics.state().rotorWallEffectForceBodyNewtons().length());
		entityData.set(BAROMETER_ALTITUDE, (float) physics.state().barometerAltitudeMeters());
		entityData.set(BAROMETER_VERTICAL_SPEED, (float) physics.state().barometerVerticalSpeedMetersPerSecond());
		entityData.set(BAROMETER_PRESSURE, (float) physics.state().barometerPressureHectopascals());
		entityData.set(BAROMETER_ERROR, (float) physics.state().barometerErrorMeters());
		entityData.set(SPEED, (float) physics.state().speedMetersPerSecond());
		entityData.set(CONTACT_IMPACT_SPEED, (float) physics.state().contactImpactSpeedMetersPerSecond());
		entityData.set(CONTACT_SLIP_SPEED, (float) physics.state().contactSlipSpeedMetersPerSecond());
		entityData.set(CONTACT_BOUNCE_SPEED, (float) physics.state().contactBounceSpeedMetersPerSecond());
		entityData.set(CONTACT_ANGULAR_IMPULSE, (float) Math.toDegrees(physics.state().contactAngularImpulseBodyRadiansPerSecond().length()));
		entityData.set(BATTERY_VOLTAGE, (float) physics.state().batteryVoltage());
		entityData.set(BATTERY_SAG, (float) physics.state().batteryTotalSagVoltage());
		entityData.set(BATTERY_EFFECTIVE_RESISTANCE, (float) physics.state().batteryEffectiveResistanceOhms());
		entityData.set(BATTERY_REGEN_CURRENT, (float) physics.state().batteryRegenerativeCurrentAmps());
		entityData.set(BATTERY_VOLTAGE_SPIKE, (float) physics.state().batteryVoltageSpike());
		entityData.set(BATTERY_BUS_RIPPLE, (float) physics.state().batteryBusRippleVoltage());
		entityData.set(BATTERY_STATE_OF_CHARGE, (float) physics.state().batteryStateOfCharge());
		entityData.set(BATTERY_CURRENT, (float) physics.state().batteryCurrentAmps());
		entityData.set(BATTERY_TWENTY_PERCENT_SAG_CURRENT, (float) physics.state().batteryTwentyPercentSagCurrentAmps());
		entityData.set(BATTERY_TWENTY_PERCENT_SAG_CURRENT_MARGIN, (float) physics.state().batteryTwentyPercentSagCurrentMargin());
		entityData.set(BATTERY_CURRENT_LIMIT, (float) physics.state().batteryCurrentLimit());
		entityData.set(BATTERY_POWER_LIMIT, (float) physics.state().batteryPowerLimit());
		entityData.set(FRAME_HEALTH, (float) frameHealth);
		entityData.set(ROTOR_HEALTH, (float) physics.state().averageRotorHealth());
		entityData.set(PROP_STRIKE_COUNT, propStrikeCount);
		entityData.set(LAST_PROP_STRIKE_ROTOR, lastPropStrikeRotorIndex);
		entityData.set(LAST_PROP_STRIKE_SEVERITY, (float) lastPropStrikeSeverity);
		setYRot((float) Math.toDegrees(euler.y()));
		setYHeadRot(getYRot());
	}

	private FlightMode syncedFlightMode(DroneInput rawInput, DroneInput processedInput) {
		if (getOwner() == null && rawInput != null && !rawInput.linkActive()) {
			return FlightMode.ANGLE;
		}
		return processedInput == null ? FlightMode.ANGLE : processedInput.flightMode();
	}

	private void setPerRotorFlightState(double[] motorPower, double[] motorRpm, double[] rotorThrust, double[] rotorHealth) {
		entityData.set(MOTOR_0_POWER, (float) valueOrZero(motorPower, 0));
		entityData.set(MOTOR_1_POWER, (float) valueOrZero(motorPower, 1));
		entityData.set(MOTOR_2_POWER, (float) valueOrZero(motorPower, 2));
		entityData.set(MOTOR_3_POWER, (float) valueOrZero(motorPower, 3));
		entityData.set(MOTOR_4_POWER, (float) valueOrZero(motorPower, 4));
		entityData.set(MOTOR_5_POWER, (float) valueOrZero(motorPower, 5));
		entityData.set(MOTOR_6_POWER, (float) valueOrZero(motorPower, 6));
		entityData.set(MOTOR_7_POWER, (float) valueOrZero(motorPower, 7));
		entityData.set(MOTOR_0_RPM, (float) valueOrZero(motorRpm, 0));
		entityData.set(MOTOR_1_RPM, (float) valueOrZero(motorRpm, 1));
		entityData.set(MOTOR_2_RPM, (float) valueOrZero(motorRpm, 2));
		entityData.set(MOTOR_3_RPM, (float) valueOrZero(motorRpm, 3));
		entityData.set(MOTOR_4_RPM, (float) valueOrZero(motorRpm, 4));
		entityData.set(MOTOR_5_RPM, (float) valueOrZero(motorRpm, 5));
		entityData.set(MOTOR_6_RPM, (float) valueOrZero(motorRpm, 6));
		entityData.set(MOTOR_7_RPM, (float) valueOrZero(motorRpm, 7));
		entityData.set(ROTOR_0_THRUST, (float) valueOrZero(rotorThrust, 0));
		entityData.set(ROTOR_1_THRUST, (float) valueOrZero(rotorThrust, 1));
		entityData.set(ROTOR_2_THRUST, (float) valueOrZero(rotorThrust, 2));
		entityData.set(ROTOR_3_THRUST, (float) valueOrZero(rotorThrust, 3));
		entityData.set(ROTOR_4_THRUST, (float) valueOrZero(rotorThrust, 4));
		entityData.set(ROTOR_5_THRUST, (float) valueOrZero(rotorThrust, 5));
		entityData.set(ROTOR_6_THRUST, (float) valueOrZero(rotorThrust, 6));
		entityData.set(ROTOR_7_THRUST, (float) valueOrZero(rotorThrust, 7));
		setPerRotorHealthState(rotorHealth);
	}

	private void setPerRotorHealthState(double[] rotorHealth) {
		entityData.set(ROTOR_0_HEALTH, (float) valueOrOne(rotorHealth, 0));
		entityData.set(ROTOR_1_HEALTH, (float) valueOrOne(rotorHealth, 1));
		entityData.set(ROTOR_2_HEALTH, (float) valueOrOne(rotorHealth, 2));
		entityData.set(ROTOR_3_HEALTH, (float) valueOrOne(rotorHealth, 3));
		entityData.set(ROTOR_4_HEALTH, (float) valueOrOne(rotorHealth, 4));
		entityData.set(ROTOR_5_HEALTH, (float) valueOrOne(rotorHealth, 5));
		entityData.set(ROTOR_6_HEALTH, (float) valueOrOne(rotorHealth, 6));
		entityData.set(ROTOR_7_HEALTH, (float) valueOrOne(rotorHealth, 7));
	}

	private int syncedRotorCount() {
		return Math.max(1, Math.min(8, physics.config().rotors().size()));
	}

	private void syncAirframeLayout() {
		entityData.set(ROTOR_COUNT, syncedRotorCount());
		entityData.set(ROTOR_LAYOUT, RotorLayoutCodec.encode(physics.config()));
	}

	private static double valueOrZero(double[] values, int index) {
		return index >= 0 && index < values.length ? values[index] : 0.0;
	}

	private static double valueOrOne(double[] values, int index) {
		return index >= 0 && index < values.length ? values[index] : 1.0;
	}

	private void applyCollisionDamage(Vec3 impactVelocity, double impactSpeed) {
		if (collisionDamageCooldown > 0) {
			return;
		}

		if (impactSpeed < 3.2) {
			return;
		}

		double severity = Math.min(1.0, (impactSpeed - 3.2) / 16.0);
		lastCollisionSeverity = Math.max(lastCollisionSeverity, severity);
		frameHealth = Math.max(0.0, frameHealth - severity * 0.22);
		physics.state().damageAllRotors(severity * 0.04);

		int rotorIndex = exposedRotorIndex(impactVelocity);
		physics.state().damageRotor(rotorIndex, severity * 0.34);
		collisionDamageCooldown = 6;
	}

	private void samplePropStrikes() {
		ensureRotorStrikeArrays();
		Vec3 frameVelocity = physics.state().velocityMetersPerSecond();
		double frameSpeed = frameVelocity.length();
		Vec3 bodyXWorld = physics.state().orientation().rotate(new Vec3(1.0, 0.0, 0.0));
		Vec3 bodyZWorld = physics.state().orientation().rotate(new Vec3(0.0, 0.0, 1.0));
		Vec3 bodyCenterWorld = entityPhysicsPosition();

		for (int i = 0; i < physics.config().rotors().size(); i++) {
			RotorSpec rotor = physics.config().rotors().get(i);
			double tipSpeed = physics.state().motorOmegaRadiansPerSecond(i) * rotor.radiusMeters();
			if (tipSpeed < MIN_PROP_STRIKE_TIP_SPEED_METERS_PER_SECOND && frameSpeed < MIN_PROP_STRIKE_FRAME_SPEED_METERS_PER_SECOND) {
				continue;
			}

			ContactDynamics.ContactSurface contactSurface = rotorDiskContactSurface(bodyCenterWorld, bodyXWorld, bodyZWorld, rotor);
			if (contactSurface == null) {
				continue;
			}

			double scrapeIntensity = propScrapeIntensity(tipSpeed, frameSpeed) * contactSurface.scrapeMultiplier();
			physics.state().setContactSurfaceTelemetry(contactSurface);
			physics.state().addRotorSurfaceScrapeIntensity(i, scrapeIntensity);
			if (rotorStrikeCooldownTicks[i] > 0) {
				continue;
			}

			double severity = propStrikeSeverity(tipSpeed, frameSpeed, contactSurface);
			physics.state().damageRotor(i, severity);
			frameHealth = Math.max(0.0, frameHealth - severity * 0.035);
			lastCollisionSeverity = Math.max(lastCollisionSeverity, severity);
			propStrikeSeverityThisTick[i] = Math.max(propStrikeSeverityThisTick[i], severity);
			propStrikeCount++;
			lastPropStrikeRotorIndex = i;
			lastPropStrikeSeverity = severity;
			rotorStrikeCooldownTicks[i] = PROP_STRIKE_COOLDOWN_TICKS;
		}
	}

	private ContactDynamics.ContactSurface rotorDiskContactSurface(Vec3 bodyCenterWorld, Vec3 bodyXWorld, Vec3 bodyZWorld, RotorSpec rotor) {
		Vec3 rotorCenterWorld = bodyCenterWorld.add(physics.state().orientation().rotate(rotor.positionBodyMeters()));
		double radius = effectivePropStrikeRadius(rotor);
		Vec3[] diskSamples = {
				Vec3.ZERO,
				bodyXWorld.multiply(radius),
				bodyXWorld.multiply(-radius),
				bodyZWorld.multiply(radius),
				bodyZWorld.multiply(-radius),
				bodyXWorld.add(bodyZWorld).normalized().multiply(radius),
				bodyXWorld.subtract(bodyZWorld).normalized().multiply(radius),
				bodyXWorld.multiply(-1.0).add(bodyZWorld).normalized().multiply(radius),
				bodyXWorld.multiply(-1.0).subtract(bodyZWorld).normalized().multiply(radius)
		};

		net.minecraft.world.phys.Vec3 rotorCenter = toMinecraftVec(rotorCenterWorld);
		for (Vec3 sampleOffset : diskSamples) {
			net.minecraft.world.phys.Vec3 sample = toMinecraftVec(rotorCenterWorld.add(sampleOffset));
			ContactDynamics.ContactSurface pointSurface = collisionSurfaceAtPoint(sample);
			if (pointSurface != null) {
				return pointSurface;
			}

			HitResult hit = level().clip(new ClipContext(
					rotorCenter,
					sample,
					ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE,
					this
			));
			if (hit.getType() != HitResult.Type.MISS) {
				return surfaceForHit(hit);
			}
		}
		return null;
	}

	private ContactDynamics.ContactSurface contactSurfaceForNormal(Vec3 contactNormalWorld) {
		Vec3 normal = contactNormalWorld == null ? Vec3.ZERO : contactNormalWorld.normalized();
		if (normal.lengthSquared() <= 1.0e-9) {
			return ContactDynamics.DEFAULT_SURFACE;
		}

		AABB bounds = getBoundingBox();
		double centerX = (bounds.minX + bounds.maxX) * 0.5;
		double centerY = (bounds.minY + bounds.maxY) * 0.5;
		double centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
		double x = centerX;
		double y = centerY;
		double z = centerZ;
		if (Math.abs(normal.x()) > 0.20) {
			x = normal.x() > 0.0 ? bounds.minX - 0.03 : bounds.maxX + 0.03;
		}
		if (Math.abs(normal.y()) > 0.20) {
			y = normal.y() > 0.0 ? bounds.minY - 0.03 : bounds.maxY + 0.03;
		}
		if (Math.abs(normal.z()) > 0.20) {
			z = normal.z() > 0.0 ? bounds.minZ - 0.03 : bounds.maxZ + 0.03;
		}

		ContactDynamics.ContactSurface surface = collisionSurfaceAtPoint(new net.minecraft.world.phys.Vec3(x, y, z));
		if (surface != null) {
			return surface;
		}

		double probeDistance = Math.max(0.24, Math.min(bounds.maxY - bounds.minY, Math.max(bounds.maxX - bounds.minX, bounds.maxZ - bounds.minZ)) * 0.65);
		net.minecraft.world.phys.Vec3 center = new net.minecraft.world.phys.Vec3(centerX, centerY, centerZ);
		net.minecraft.world.phys.Vec3 probe = new net.minecraft.world.phys.Vec3(
				center.x - normal.x() * probeDistance,
				center.y - normal.y() * probeDistance,
				center.z - normal.z() * probeDistance
		);
		surface = collisionSurfaceAtPoint(probe);
		return surface == null ? ContactDynamics.DEFAULT_SURFACE : surface;
	}

	private ContactDynamics.ContactSurface collisionSurfaceAtPoint(net.minecraft.world.phys.Vec3 point) {
		BlockPos position = BlockPos.containing(point);
		BlockState state = level().getBlockState(position);
		VoxelShape shape = state.getCollisionShape(level(), position);
		if (shape.isEmpty()) {
			return null;
		}

		double localX = point.x - position.getX();
		double localY = point.y - position.getY();
		double localZ = point.z - position.getZ();
		for (AABB box : shape.toAabbs()) {
			if (localX >= box.minX && localX <= box.maxX
					&& localY >= box.minY && localY <= box.maxY
					&& localZ >= box.minZ && localZ <= box.maxZ) {
				return surfaceForBlock(state);
			}
		}
		return null;
	}

	private ContactDynamics.ContactSurface surfaceForHit(HitResult hit) {
		if (hit instanceof BlockHitResult blockHit) {
			return surfaceForBlock(level().getBlockState(blockHit.getBlockPos()));
		}
		return ContactDynamics.DEFAULT_SURFACE;
	}

	private static ContactDynamics.ContactSurface surfaceForBlock(BlockState state) {
		if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.FROSTED_ICE)) {
			return ICE_CONTACT_SURFACE;
		}
		if (state.is(Blocks.SLIME_BLOCK)) {
			return SLIME_CONTACT_SURFACE;
		}
		if (state.is(Blocks.HONEY_BLOCK)) {
			return HONEY_CONTACT_SURFACE;
		}
		if (state.is(Blocks.CACTUS)) {
			return ABRASIVE_CONTACT_SURFACE;
		}
		if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)) {
			return LOOSE_CONTACT_SURFACE;
		}
		if (state.is(Blocks.MUD) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)) {
			return STICKY_DIRT_CONTACT_SURFACE;
		}
		if (isSoftFiberSurface(state)) {
			return SOFT_FIBER_CONTACT_SURFACE;
		}
		if (isMetalSurface(state)) {
			return METAL_CONTACT_SURFACE;
		}
		if (isWoodSurface(state)) {
			return WOOD_CONTACT_SURFACE;
		}
		if (isHardMasonrySurface(state)) {
			return HARD_MASONRY_CONTACT_SURFACE;
		}
		return ContactDynamics.DEFAULT_SURFACE;
	}

	private static boolean isHardMasonrySurface(BlockState state) {
		return state.is(Blocks.STONE)
				|| state.is(Blocks.SMOOTH_STONE)
				|| state.is(Blocks.COBBLESTONE)
				|| state.is(Blocks.MOSSY_COBBLESTONE)
				|| state.is(Blocks.STONE_BRICKS)
				|| state.is(Blocks.MOSSY_STONE_BRICKS)
				|| state.is(Blocks.CRACKED_STONE_BRICKS)
				|| state.is(Blocks.CHISELED_STONE_BRICKS)
				|| state.is(Blocks.BRICKS)
				|| state.is(Blocks.GRANITE)
				|| state.is(Blocks.POLISHED_GRANITE)
				|| state.is(Blocks.DIORITE)
				|| state.is(Blocks.POLISHED_DIORITE)
				|| state.is(Blocks.ANDESITE)
				|| state.is(Blocks.POLISHED_ANDESITE)
				|| state.is(Blocks.DEEPSLATE)
				|| state.is(Blocks.COBBLED_DEEPSLATE)
				|| state.is(Blocks.POLISHED_DEEPSLATE)
				|| state.is(Blocks.DEEPSLATE_BRICKS)
				|| state.is(Blocks.DEEPSLATE_TILES)
				|| state.is(Blocks.TUFF)
				|| state.is(Blocks.POLISHED_TUFF)
				|| state.is(Blocks.TUFF_BRICKS)
				|| state.is(Blocks.BASALT)
				|| state.is(Blocks.SMOOTH_BASALT)
				|| state.is(Blocks.POLISHED_BASALT)
				|| state.is(Blocks.BLACKSTONE)
				|| state.is(Blocks.POLISHED_BLACKSTONE)
				|| state.is(Blocks.POLISHED_BLACKSTONE_BRICKS)
				|| state.is(Blocks.NETHER_BRICKS)
				|| state.is(Blocks.RED_NETHER_BRICKS)
				|| state.is(Blocks.END_STONE)
				|| state.is(Blocks.END_STONE_BRICKS)
				|| state.is(Blocks.OBSIDIAN)
				|| state.is(Blocks.WHITE_CONCRETE)
				|| state.is(Blocks.LIGHT_GRAY_CONCRETE)
				|| state.is(Blocks.GRAY_CONCRETE)
				|| state.is(Blocks.BLACK_CONCRETE)
				|| state.is(Blocks.BROWN_CONCRETE)
				|| state.is(Blocks.RED_CONCRETE)
				|| state.is(Blocks.ORANGE_CONCRETE)
				|| state.is(Blocks.YELLOW_CONCRETE)
				|| state.is(Blocks.LIME_CONCRETE)
				|| state.is(Blocks.GREEN_CONCRETE)
				|| state.is(Blocks.CYAN_CONCRETE)
				|| state.is(Blocks.LIGHT_BLUE_CONCRETE)
				|| state.is(Blocks.BLUE_CONCRETE)
				|| state.is(Blocks.PURPLE_CONCRETE)
				|| state.is(Blocks.MAGENTA_CONCRETE)
				|| state.is(Blocks.PINK_CONCRETE);
	}

	private static boolean isWoodSurface(BlockState state) {
		return state.is(Blocks.OAK_PLANKS)
				|| state.is(Blocks.SPRUCE_PLANKS)
				|| state.is(Blocks.BIRCH_PLANKS)
				|| state.is(Blocks.JUNGLE_PLANKS)
				|| state.is(Blocks.ACACIA_PLANKS)
				|| state.is(Blocks.DARK_OAK_PLANKS)
				|| state.is(Blocks.MANGROVE_PLANKS)
				|| state.is(Blocks.CHERRY_PLANKS)
				|| state.is(Blocks.PALE_OAK_PLANKS)
				|| state.is(Blocks.BAMBOO_PLANKS)
				|| state.is(Blocks.CRIMSON_PLANKS)
				|| state.is(Blocks.WARPED_PLANKS)
				|| state.is(Blocks.OAK_LOG)
				|| state.is(Blocks.SPRUCE_LOG)
				|| state.is(Blocks.BIRCH_LOG)
				|| state.is(Blocks.JUNGLE_LOG)
				|| state.is(Blocks.ACACIA_LOG)
				|| state.is(Blocks.DARK_OAK_LOG)
				|| state.is(Blocks.MANGROVE_LOG)
				|| state.is(Blocks.CHERRY_LOG)
				|| state.is(Blocks.PALE_OAK_LOG)
				|| state.is(Blocks.BAMBOO_BLOCK)
				|| state.is(Blocks.CRIMSON_STEM)
				|| state.is(Blocks.WARPED_STEM);
	}

	private static boolean isMetalSurface(BlockState state) {
		return state.is(Blocks.IRON_BLOCK)
				|| state.is(Blocks.IRON_BARS)
				|| state.is(Blocks.IRON_TRAPDOOR)
				|| state.is(Blocks.ANVIL)
				|| state.is(Blocks.CHIPPED_ANVIL)
				|| state.is(Blocks.DAMAGED_ANVIL)
				|| state.is(Blocks.GOLD_BLOCK)
				|| state.is(Blocks.COPPER_BLOCK)
				|| state.is(Blocks.EXPOSED_COPPER)
				|| state.is(Blocks.WEATHERED_COPPER)
				|| state.is(Blocks.OXIDIZED_COPPER)
				|| state.is(Blocks.CUT_COPPER)
				|| state.is(Blocks.EXPOSED_CUT_COPPER)
				|| state.is(Blocks.WEATHERED_CUT_COPPER)
				|| state.is(Blocks.OXIDIZED_CUT_COPPER)
				|| state.is(Blocks.WAXED_COPPER_BLOCK)
				|| state.is(Blocks.WAXED_EXPOSED_COPPER)
				|| state.is(Blocks.WAXED_WEATHERED_COPPER)
				|| state.is(Blocks.WAXED_OXIDIZED_COPPER)
				|| state.is(Blocks.WAXED_CUT_COPPER)
				|| state.is(Blocks.WAXED_EXPOSED_CUT_COPPER)
				|| state.is(Blocks.WAXED_WEATHERED_CUT_COPPER)
				|| state.is(Blocks.WAXED_OXIDIZED_CUT_COPPER);
	}

	private static boolean isSoftFiberSurface(BlockState state) {
		return state.is(Blocks.WHITE_WOOL)
				|| state.is(Blocks.LIGHT_GRAY_WOOL)
				|| state.is(Blocks.GRAY_WOOL)
				|| state.is(Blocks.BLACK_WOOL)
				|| state.is(Blocks.BROWN_WOOL)
				|| state.is(Blocks.RED_WOOL)
				|| state.is(Blocks.ORANGE_WOOL)
				|| state.is(Blocks.YELLOW_WOOL)
				|| state.is(Blocks.LIME_WOOL)
				|| state.is(Blocks.GREEN_WOOL)
				|| state.is(Blocks.CYAN_WOOL)
				|| state.is(Blocks.LIGHT_BLUE_WOOL)
				|| state.is(Blocks.BLUE_WOOL)
				|| state.is(Blocks.PURPLE_WOOL)
				|| state.is(Blocks.MAGENTA_WOOL)
				|| state.is(Blocks.PINK_WOOL)
				|| state.is(Blocks.HAY_BLOCK);
	}

	private static double effectivePropStrikeRadius(RotorSpec rotor) {
		return MathUtil.clamp(rotor.radiusMeters() * 4.4, 0.16, 0.34);
	}

	private static double propStrikeSeverity(double tipSpeedMetersPerSecond, double frameSpeedMetersPerSecond) {
		double tipFactor = MathUtil.clamp(tipSpeedMetersPerSecond / 55.0, 0.0, 1.0);
		double speedFactor = MathUtil.clamp(frameSpeedMetersPerSecond / 14.0, 0.0, 1.0);
		return MathUtil.clamp(0.012 + tipFactor * 0.065 + speedFactor * 0.080, 0.01, 0.18);
	}

	private static double propStrikeSeverity(
			double tipSpeedMetersPerSecond,
			double frameSpeedMetersPerSecond,
			ContactDynamics.ContactSurface surface
	) {
		ContactDynamics.ContactSurface contactSurface = surface == null ? ContactDynamics.DEFAULT_SURFACE : surface;
		double multiplier = Math.sqrt(contactSurface.scrapeMultiplier());
		return MathUtil.clamp(propStrikeSeverity(tipSpeedMetersPerSecond, frameSpeedMetersPerSecond) * multiplier, 0.01, 0.24);
	}

	private static double propScrapeIntensity(double tipSpeedMetersPerSecond, double frameSpeedMetersPerSecond) {
		double tipFactor = MathUtil.clamp(tipSpeedMetersPerSecond / 48.0, 0.0, 1.0);
		double speedFactor = MathUtil.clamp(frameSpeedMetersPerSecond / 9.0, 0.0, 1.0);
		return MathUtil.clamp(0.16 + 0.72 * tipFactor + 0.22 * speedFactor, 0.0, 1.0);
	}

	private void decrementRotorStrikeCooldowns() {
		ensureRotorStrikeArrays();
		for (int i = 0; i < rotorStrikeCooldownTicks.length; i++) {
			if (rotorStrikeCooldownTicks[i] > 0) {
				rotorStrikeCooldownTicks[i]--;
			}
		}
	}

	private void ensureRotorStrikeArrays() {
		int rotorCount = physics.config().rotors().size();
		if (rotorStrikeCooldownTicks.length == rotorCount && propStrikeSeverityThisTick.length == rotorCount) {
			return;
		}

		int[] resizedCooldowns = new int[rotorCount];
		System.arraycopy(rotorStrikeCooldownTicks, 0, resizedCooldowns, 0, Math.min(rotorStrikeCooldownTicks.length, resizedCooldowns.length));
		rotorStrikeCooldownTicks = resizedCooldowns;

		double[] resizedSeverities = new double[rotorCount];
		System.arraycopy(propStrikeSeverityThisTick, 0, resizedSeverities, 0, Math.min(propStrikeSeverityThisTick.length, resizedSeverities.length));
		propStrikeSeverityThisTick = resizedSeverities;
	}

	private static net.minecraft.world.phys.Vec3 toMinecraftVec(Vec3 vector) {
		return new net.minecraft.world.phys.Vec3(vector.x(), vector.y(), vector.z());
	}

	private int exposedRotorIndex(Vec3 impactVelocityWorld) {
		Vec3 impactBody = physics.state().orientation().conjugate().rotate(impactVelocityWorld).normalized();
		int bestIndex = 0;
		double bestDot = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < physics.config().rotors().size(); i++) {
			RotorSpec rotor = physics.config().rotors().get(i);
			double dot = rotor.positionBodyMeters().normalized().dot(impactBody);
			if (dot > bestDot) {
				bestDot = dot;
				bestIndex = i;
			}
		}
		return bestIndex;
	}

	private boolean isAirworthy() {
		return frameHealth > 0.12 && physics.state().averageRotorHealth() > 0.18;
	}

	private void repair() {
		frameHealth = 1.0;
		physics.state().repairAllRotors();
		physics.resetControlLoops();
		lastCollisionSeverity = 0.0;
		clearPropStrikePulse();
		propStrikeCount = 0;
		lastPropStrikeRotorIndex = -1;
		lastPropStrikeSeverity = 0.0;
		entityData.set(FRAME_HEALTH, 1.0f);
		entityData.set(ROTOR_HEALTH, 1.0f);
		syncAirframeLayout();
		setPerRotorHealthState(physics.state().rotorHealth());
		entityData.set(PROP_STRIKE_COUNT, 0);
		entityData.set(LAST_PROP_STRIKE_ROTOR, -1);
		entityData.set(LAST_PROP_STRIKE_SEVERITY, 0.0f);
	}

	public void repairDamage() {
		repair();
	}

	public boolean injectRotorFault(int rotorIndex, double damage, boolean recordAsPropStrike) {
		if (rotorIndex < 0 || rotorIndex >= physics.config().rotors().size()) {
			return false;
		}

		double safeDamage = MathUtil.clamp(damage, 0.0, 1.0);
		if (safeDamage <= 0.0) {
			return false;
		}

		physics.state().damageRotor(rotorIndex, safeDamage);
		if (recordAsPropStrike) {
			lastCollisionSeverity = Math.max(lastCollisionSeverity, safeDamage);
			ensureRotorStrikeArrays();
			propStrikeSeverityThisTick[rotorIndex] = Math.max(propStrikeSeverityThisTick[rotorIndex], safeDamage);
			propStrikeCount++;
			lastPropStrikeRotorIndex = rotorIndex;
			lastPropStrikeSeverity = safeDamage;
			rotorStrikeCooldownTicks[rotorIndex] = PROP_STRIKE_COOLDOWN_TICKS;
		}
		updateDamageSyncedState();
		return true;
	}

	private void updateDamageSyncedState() {
		double[] rotorHealth = physics.state().rotorHealth();
		entityData.set(FRAME_HEALTH, (float) frameHealth);
		entityData.set(ROTOR_HEALTH, (float) physics.state().averageRotorHealth());
		syncAirframeLayout();
		setPerRotorHealthState(rotorHealth);
		entityData.set(PROP_STRIKE_COUNT, propStrikeCount);
		entityData.set(LAST_PROP_STRIKE_ROTOR, lastPropStrikeRotorIndex);
		entityData.set(LAST_PROP_STRIKE_SEVERITY, (float) lastPropStrikeSeverity);
	}

	private void recordBlackbox(DroneInput input) {
		blackbox.record(DroneBlackboxSample.from(
				level().getGameTime(),
				tickCount,
				PHYSICS_STEPS_PER_TICK,
				PHYSICS_DT,
				DroneDebugSettings.flightModelMode().id(),
				physics.state(),
				input,
				physics.state().averageMotorPower(physics.config()),
				frameHealth,
				physics.state().averageRotorHealth(),
				lastCollisionSeverity,
				maxPropStrikeRotorIndexThisTick(),
				maxPropStrikeSeverityThisTick(),
				propStrikeCount,
				propStrikeSeverityThisTick,
				lastEnvironment,
				physics.config()
		));
		lastCollisionSeverity *= 0.86;
		if (lastCollisionSeverity < 0.001) {
			lastCollisionSeverity = 0.0;
		}
		clearPropStrikePulse();
	}

	private double maxPropStrikeSeverityThisTick() {
		double maxSeverity = 0.0;
		for (double severity : propStrikeSeverityThisTick) {
			maxSeverity = Math.max(maxSeverity, severity);
		}
		return maxSeverity;
	}

	private int maxPropStrikeRotorIndexThisTick() {
		int rotorIndex = -1;
		double maxSeverity = 0.0;
		for (int i = 0; i < propStrikeSeverityThisTick.length; i++) {
			if (propStrikeSeverityThisTick[i] > maxSeverity) {
				maxSeverity = propStrikeSeverityThisTick[i];
				rotorIndex = i;
			}
		}
		return rotorIndex;
	}

	private void clearPropStrikePulse() {
		for (int i = 0; i < propStrikeSeverityThisTick.length; i++) {
			propStrikeSeverityThisTick[i] = 0.0;
		}
	}

	private void resetPropStrikeTelemetry() {
		clearPropStrikePulse();
		propStrikeCount = 0;
		lastPropStrikeRotorIndex = -1;
		lastPropStrikeSeverity = 0.0;
	}

	private void handleCompletedDiagnostic() {
		if (owner == null) {
			return;
		}

		DroneControlManager.CompletedDiagnostic completed = DroneControlManager.consumeCompletedDiagnostic(owner);
		if (completed == null || !completed.autoSaveBlackbox()) {
			return;
		}

		if (blackbox.size() == 0) {
			return;
		}

		MinecraftServer server = level().getServer();
		if (server == null) {
			return;
		}

		Path directory = server.getFile("fpvdrone-blackbox");
		Path output = directory.resolve("diagnostic-" + owner + "-" + LocalDateTime.now().format(FILE_TIME) + ".csv");
		try {
			Files.createDirectories(directory);
			Files.writeString(output, blackbox.toCsv(), StandardCharsets.UTF_8);
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player != null) {
				player.displayClientMessage(Component.literal("Diagnostic blackbox saved: " + output.toAbsolutePath()), false);
			}
			FpvDronecraftMod.LOGGER.info("Diagnostic blackbox saved: {}", output.toAbsolutePath());
		} catch (IOException exception) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player != null) {
				player.displayClientMessage(Component.literal("Failed to save diagnostic blackbox: " + exception.getMessage()), false);
			}
			FpvDronecraftMod.LOGGER.error("Failed to save diagnostic blackbox", exception);
		}
	}

	public boolean isArmed() {
		return entityData.get(ARMED);
	}

	public FlightMode getFlightMode() {
		return FlightMode.byId(entityData.get(FLIGHT_MODE));
	}

	public float getRenderPitchRadians() {
		return entityData.get(PITCH);
	}

	public float getRenderYawRadians() {
		return entityData.get(YAW);
	}

	public float getRenderRollRadians() {
		return entityData.get(ROLL);
	}

	public float getMotorPower() {
		return entityData.get(MOTOR_POWER);
	}

	public int getRotorCount() {
		return Math.max(1, Math.min(8, entityData.get(ROTOR_COUNT)));
	}

	public String getRotorLayout() {
		return entityData.get(ROTOR_LAYOUT);
	}

	public float getMotorPower(int index) {
		return switch (index) {
			case 0 -> entityData.get(MOTOR_0_POWER);
			case 1 -> entityData.get(MOTOR_1_POWER);
			case 2 -> entityData.get(MOTOR_2_POWER);
			case 3 -> entityData.get(MOTOR_3_POWER);
			case 4 -> entityData.get(MOTOR_4_POWER);
			case 5 -> entityData.get(MOTOR_5_POWER);
			case 6 -> entityData.get(MOTOR_6_POWER);
			case 7 -> entityData.get(MOTOR_7_POWER);
			default -> 0.0f;
		};
	}

	public float getAverageMotorRpm() {
		return entityData.get(MOTOR_RPM);
	}

	public float getMotorRpm(int index) {
		return switch (index) {
			case 0 -> entityData.get(MOTOR_0_RPM);
			case 1 -> entityData.get(MOTOR_1_RPM);
			case 2 -> entityData.get(MOTOR_2_RPM);
			case 3 -> entityData.get(MOTOR_3_RPM);
			case 4 -> entityData.get(MOTOR_4_RPM);
			case 5 -> entityData.get(MOTOR_5_RPM);
			case 6 -> entityData.get(MOTOR_6_RPM);
			case 7 -> entityData.get(MOTOR_7_RPM);
			default -> 0.0f;
		};
	}

	public double getAverageMotorRpmTelemetryRpm() {
		return physics.state().averageMotorRpmTelemetryRpm();
	}

	public double getMotorRpmTelemetryRpm(int index) {
		if (index < 0 || index >= physics.state().motorCount()) {
			return 0.0;
		}
		return physics.state().motorRpmTelemetryRpm(index);
	}

	public double getAverageMotorRpmTelemetryValidity() {
		return physics.state().averageMotorRpmTelemetryValidity();
	}

	public double getMotorRpmTelemetryValidity(int index) {
		if (index < 0 || index >= physics.state().motorCount()) {
			return 0.0;
		}
		return physics.state().motorRpmTelemetryValidity(index);
	}

	public double getAverageMotorTelemetryErpm100() {
		return DronePhysics.betaflightErpm100FromMechanicalRpm(
				physics.state().averageMotorRpmTelemetryRpm(),
				averageMotorPolePairs()
		);
	}

	public double getMotorTelemetryErpm100(int index) {
		return DronePhysics.betaflightErpm100FromMechanicalRpm(getMotorRpmTelemetryRpm(index), motorPolePairs(index));
	}

	public double getAverageMotorTelemetryEIntervalMicros() {
		return DronePhysics.betaflightEIntervalMicrosFromTelemetryRpm(
				physics.state().averageMotorRpmTelemetryRpm(),
				physics.state().averageMotorRpmTelemetryValidity(),
				averageMotorPolePairs()
		);
	}

	public double getMotorTelemetryEIntervalMicros(int index) {
		return DronePhysics.betaflightEIntervalMicrosFromTelemetryRpm(
				getMotorRpmTelemetryRpm(index),
				getMotorRpmTelemetryValidity(index),
				motorPolePairs(index)
		);
	}

	private double averageMotorPolePairs() {
		DroneConfig config = physics.config();
		if (config.rotors().isEmpty()) {
			return RotorSpec.DEFAULT_MOTOR_POLE_PAIRS;
		}
		return config.rotors().stream()
				.mapToDouble(RotorSpec::motorPolePairs)
				.average()
				.orElse(RotorSpec.DEFAULT_MOTOR_POLE_PAIRS);
	}

	private double motorPolePairs(int index) {
		DroneConfig config = physics.config();
		if (index < 0 || index >= config.rotors().size()) {
			return RotorSpec.DEFAULT_MOTOR_POLE_PAIRS;
		}
		return config.rotors().get(index).motorPolePairs();
	}

	public float getMotorTemperatureCelsius() {
		return entityData.get(MOTOR_TEMPERATURE);
	}

	public float getMotorThermalLimit() {
		return entityData.get(MOTOR_THERMAL_LIMIT);
	}

	public float getMotorVoltageHeadroom() {
		return entityData.get(MOTOR_VOLTAGE_HEADROOM);
	}

	public float getMotorWindingResistanceScale() {
		return entityData.get(MOTOR_WINDING_RESISTANCE_SCALE);
	}

	public float getEscTemperatureCelsius() {
		return entityData.get(ESC_TEMPERATURE);
	}

	public float getEscThermalLimit() {
		return entityData.get(ESC_THERMAL_LIMIT);
	}

	public float getEscCoolingFactor() {
		return entityData.get(ESC_COOLING_FACTOR);
	}

	public float getEscDesyncIntensity() {
		return entityData.get(ESC_DESYNC);
	}

	public double getEscCommandFrameAgeSeconds() {
		return physics.state().escCommandFrameAgeSeconds();
	}

	public double getEscCommandFrameIntervalSeconds() {
		return physics.state().escCommandFrameIntervalSeconds();
	}

	public double getEscCommandError() {
		return physics.state().escCommandError();
	}

	public float getRotorAerodynamicLoadFactor() {
		return entityData.get(ROTOR_AERODYNAMIC_LOAD);
	}

	public float getRotorInPlaneDragForceNewtons() {
		return entityData.get(ROTOR_IN_PLANE_DRAG_FORCE);
	}

	public float getRotorThrustNewtons(int index) {
		return switch (index) {
			case 0 -> entityData.get(ROTOR_0_THRUST);
			case 1 -> entityData.get(ROTOR_1_THRUST);
			case 2 -> entityData.get(ROTOR_2_THRUST);
			case 3 -> entityData.get(ROTOR_3_THRUST);
			case 4 -> entityData.get(ROTOR_4_THRUST);
			case 5 -> entityData.get(ROTOR_5_THRUST);
			case 6 -> entityData.get(ROTOR_6_THRUST);
			case 7 -> entityData.get(ROTOR_7_THRUST);
			default -> 0.0f;
		};
	}

	public float getMixerSaturation() {
		return entityData.get(MIXER_SATURATION);
	}

	public float getRotorVibration() {
		return entityData.get(ROTOR_VIBRATION);
	}

	public float getRotorDamageVibration() {
		return (float) physics.state().maxRotorDamageVibration();
	}

	public float getRotorConingIntensity() {
		return entityData.get(ROTOR_CONING_INTENSITY);
	}

	public float getRotorStallIntensity() {
		return entityData.get(ROTOR_STALL_INTENSITY);
	}

	public float getRotorFlappingTiltDegrees() {
		return entityData.get(ROTOR_FLAPPING_TILT);
	}

	public float getRotorSurfaceScrapeIntensity() {
		return entityData.get(ROTOR_SURFACE_SCRAPE);
	}

	public float getRotorTranslationalLiftIntensity() {
		return entityData.get(ROTOR_TRANSLATIONAL_LIFT);
	}

	public float getRotorAdvanceRatio() {
		return entityData.get(ROTOR_ADVANCE_RATIO);
	}

	public float getRotorPropellerAdvanceRatioJ() {
		return entityData.get(ROTOR_PROPELLER_ADVANCE_RATIO_J);
	}

	public float getRotorPropellerThrustScale() {
		return entityData.get(ROTOR_PROPELLER_THRUST_SCALE);
	}

	public float getRotorPropellerPowerScale() {
		return entityData.get(ROTOR_PROPELLER_POWER_SCALE);
	}

	public float getRotorReverseFlowInboardFraction() {
		return entityData.get(ROTOR_REVERSE_FLOW);
	}

	public float getRotorTipMach() {
		return entityData.get(ROTOR_TIP_MACH);
	}

	public float getRotorCompressibilityThrustScale() {
		return entityData.get(ROTOR_COMPRESSIBILITY_THRUST_SCALE);
	}

	public float getRotorLowReynoldsLoss() {
		return entityData.get(ROTOR_LOW_REYNOLDS_LOSS);
	}

	public float getRotorBladeAngleOfAttackDegrees() {
		return entityData.get(ROTOR_BLADE_AOA);
	}

	public float getRotorBladeElementStallIntensity() {
		return entityData.get(ROTOR_BLADE_ELEMENT_STALL);
	}

	public float getRotorBladePassRippleIntensity() {
		return entityData.get(ROTOR_BLADE_PASS_RIPPLE);
	}

	public float getRotorBladeDissymmetryTorqueNewtonMeters() {
		return entityData.get(ROTOR_BLADE_DISSYMMETRY_TORQUE);
	}

	public float getRotorInflowSkewIntensity() {
		return entityData.get(ROTOR_INFLOW_SKEW);
	}

	public float getRotorInducedVelocityMetersPerSecond() {
		return entityData.get(ROTOR_INDUCED_VELOCITY);
	}

	public float getRotorInducedLagThrustScale() {
		return entityData.get(ROTOR_INDUCED_LAG_THRUST_SCALE);
	}

	public float getRotorDynamicInflowTimeConstantSeconds() {
		return (float) physics.state().maxRotorDynamicInflowTimeConstantSeconds();
	}

	public float getRotorWakeInterferenceIntensity() {
		return entityData.get(ROTOR_WAKE_INTERFERENCE);
	}

	public float getRotorWakeThrustScale() {
		return entityData.get(ROTOR_WAKE_THRUST_SCALE);
	}

	public float getRotorCoaxialLoadBias() {
		return entityData.get(ROTOR_COAXIAL_LOAD_BIAS);
	}

	public float getRotorCoaxialLoadBiasTarget() {
		return (float) physics.state().maxAbsRotorCoaxialLoadBiasTarget();
	}

	public float getRotorCoaxialLoadBiasClipping() {
		return (float) physics.state().maxRotorCoaxialLoadBiasClipping();
	}

	public float getRotorCoaxialAllocationLoadFraction() {
		return (float) physics.state().maxRotorCoaxialAllocationLoadFraction();
	}

	public float getRotorCoaxialAllocationCommandRatio() {
		return (float) physics.state().maxRotorCoaxialAllocationCommandRatio();
	}

	public float getRotorCoaxialAllocationMechanicalGainPercent() {
		return (float) physics.state().maxRotorCoaxialAllocationMechanicalGainPercent();
	}

	public float getRotorCoaxialAllocationElectricalGainPercent() {
		return (float) physics.state().maxRotorCoaxialAllocationElectricalGainPercent();
	}

	public float getRotorCoaxialAllocationUncertaintyPercent() {
		return (float) physics.state().maxRotorCoaxialAllocationUncertaintyPercent();
	}

	public float getRotorWetThrustScale() {
		return entityData.get(ROTOR_WET_THRUST_SCALE);
	}

	public double getRotorIcingSeverity() {
		return physics.state().maxRotorIcingSeverity();
	}

	public double getRotorIcingThrustScale() {
		return physics.state().minRotorIcingThrustScale();
	}

	public double getRotorIcingPowerScale() {
		return physics.state().maxRotorIcingPowerScale();
	}

	public float getRotorWakeSwirlVelocityMetersPerSecond() {
		return entityData.get(ROTOR_WAKE_SWIRL);
	}

	public float getRotorWindmillingIntensity() {
		return entityData.get(ROTOR_WINDMILLING);
	}

	public float getRotorWakeSwirlTorqueNewtonMeters() {
		return entityData.get(ROTOR_WAKE_SWIRL_TORQUE);
	}

	public float getRotorActiveBrakingTorqueNewtonMeters() {
		return entityData.get(ROTOR_ACTIVE_BRAKING_TORQUE);
	}

	public float getRotorAccelerationReactionTorqueNewtonMeters() {
		return entityData.get(ROTOR_ACCELERATION_REACTION_TORQUE);
	}

	public float getRotorGyroscopicTorqueNewtonMeters() {
		return entityData.get(ROTOR_GYROSCOPIC_TORQUE);
	}

	public float getRotorFlappingTorqueNewtonMeters() {
		return entityData.get(ROTOR_FLAPPING_TORQUE);
	}

	public float getPropwashIntensity() {
		return entityData.get(PROPWASH_INTENSITY);
	}

	public float getVortexRingStateIntensity() {
		return entityData.get(VORTEX_RING_STATE);
	}

	public float getVortexRingThrustBuffetAmplitude() {
		return entityData.get(VORTEX_RING_THRUST_BUFFET);
	}

	public float getVortexRingBuffetForceNewtons() {
		return entityData.get(VORTEX_RING_BUFFET_FORCE);
	}

	public float getGroundEffectMultiplier() {
		return entityData.get(GROUND_EFFECT_MULTIPLIER);
	}

	public float getCeilingEffectMultiplier() {
		return entityData.get(CEILING_EFFECT_MULTIPLIER);
	}

	public float getCeilingEffectIntensity() {
		return entityData.get(CEILING_EFFECT_INTENSITY);
	}

	public float getEnvironmentThrustAsymmetry() {
		return entityData.get(ENV_THRUST_ASYMMETRY);
	}

	public float getRotorFlowObstruction() {
		return entityData.get(ROTOR_FLOW_OBSTRUCTION);
	}

	public float getWaterImmersionIntensity() {
		return entityData.get(WATER_IMMERSION);
	}

	public float getPrecipitationWetnessIntensity() {
		return entityData.get(PRECIPITATION_WETNESS);
	}

	public float getAmbientTemperatureCelsius() {
		return entityData.get(AMBIENT_TEMPERATURE);
	}

	public float getWindSpeedMetersPerSecond() {
		return entityData.get(WIND_SPEED);
	}

	public float getEffectiveWindSpeedMetersPerSecond() {
		return entityData.get(EFFECTIVE_WIND_SPEED);
	}

	public float getWindGustSpeedMetersPerSecond() {
		return entityData.get(WIND_GUST_SPEED);
	}

	public float getWindShearAccelerationMetersPerSecondSquared() {
		return entityData.get(WIND_SHEAR_ACCELERATION);
	}

	public float getTurbulenceIntensity() {
		return entityData.get(TURBULENCE_INTENSITY);
	}

	public float getObstacleProximity() {
		return entityData.get(OBSTACLE_PROXIMITY);
	}

	public float getDroneWakeIntensity() {
		return entityData.get(DRONE_WAKE_INTENSITY);
	}

	public float getAirspeedMetersPerSecond() {
		return entityData.get(AIRSPEED);
	}

	public float getAngleOfAttackDegrees() {
		return entityData.get(ANGLE_OF_ATTACK);
	}

	public float getSideslipDegrees() {
		return entityData.get(SIDESLIP);
	}

	public float getAirframeSeparatedFlowIntensity() {
		return entityData.get(AIRFRAME_SEPARATED_FLOW);
	}

	public float getAirframeLiftForceNewtons() {
		return entityData.get(AIRFRAME_LIFT_FORCE);
	}

	public float getAirframeBodyDragForceNewtons() {
		return (float) physics.state().airframeBodyDragForceBodyNewtons().length();
	}

	public float getLinearDampingDragForceNewtons() {
		return (float) physics.state().linearDampingDragForceWorldNewtons().length();
	}

	public float getAirframeDragAlongFlowNewtons() {
		return (float) physics.state().airframeDragAlongFlowNewtons();
	}

	public float getAirframeDragEquivalentLinearCoefficient() {
		return (float) physics.state().airframeDragEquivalentLinearCoefficient();
	}

	public float getAirframeDragEquivalentCdAMetersSquared() {
		return (float) physics.state().airframeDragEquivalentCdAMetersSquared();
	}

	public float getAirframeDragImavReferenceRatio() {
		return (float) physics.state().airframeDragImavReferenceRatio();
	}

	public float getGroundEffectDragForceNewtons() {
		return entityData.get(GROUND_EFFECT_DRAG_FORCE);
	}

	public float getGroundEffectLevelingTorqueNewtonMeters() {
		return entityData.get(GROUND_EFFECT_LEVELING_TORQUE);
	}

	public float getRotorWashDragForceNewtons() {
		return entityData.get(ROTOR_WASH_DRAG_FORCE);
	}

	public float getRotorWallEffectForceNewtons() {
		return entityData.get(ROTOR_WALL_EFFECT_FORCE);
	}

	public float getBarometerAltitudeMeters() {
		return entityData.get(BAROMETER_ALTITUDE);
	}

	public float getBarometerVerticalSpeedMetersPerSecond() {
		return entityData.get(BAROMETER_VERTICAL_SPEED);
	}

	public float getBarometerPressureHectopascals() {
		return entityData.get(BAROMETER_PRESSURE);
	}

	public float getBarometerErrorMeters() {
		return entityData.get(BAROMETER_ERROR);
	}

	public float getSpeedMetersPerSecond() {
		return entityData.get(SPEED);
	}

	public float getContactImpactSpeedMetersPerSecond() {
		return entityData.get(CONTACT_IMPACT_SPEED);
	}

	public float getContactSlipSpeedMetersPerSecond() {
		return entityData.get(CONTACT_SLIP_SPEED);
	}

	public float getContactBounceSpeedMetersPerSecond() {
		return entityData.get(CONTACT_BOUNCE_SPEED);
	}

	public float getContactAngularImpulseDegreesPerSecond() {
		return entityData.get(CONTACT_ANGULAR_IMPULSE);
	}

	public float getBatteryVoltage() {
		return entityData.get(BATTERY_VOLTAGE);
	}

	public float getBatterySagVoltage() {
		return entityData.get(BATTERY_SAG);
	}

	public float getBatteryEffectiveResistanceOhms() {
		return entityData.get(BATTERY_EFFECTIVE_RESISTANCE);
	}

	public double getBatteryStateOfChargeResistanceScale() {
		return physics.state().batteryStateOfChargeResistanceScale();
	}

	public double getBatteryTemperatureResistanceScale() {
		return physics.state().batteryTemperatureResistanceScale();
	}

	public double getBatteryPolarizationResistanceScale() {
		return physics.state().batteryPolarizationResistanceScale();
	}

	public float getBatteryRegenerativeCurrentAmps() {
		return entityData.get(BATTERY_REGEN_CURRENT);
	}

	public float getBatteryVoltageSpike() {
		return entityData.get(BATTERY_VOLTAGE_SPIKE);
	}

	public float getBatteryBusRippleVoltage() {
		return entityData.get(BATTERY_BUS_RIPPLE);
	}

	public float getBatteryStateOfCharge() {
		return entityData.get(BATTERY_STATE_OF_CHARGE);
	}

	public float getBatteryCurrentAmps() {
		return entityData.get(BATTERY_CURRENT);
	}

	public float getBatteryTwentyPercentSagCurrentAmps() {
		return entityData.get(BATTERY_TWENTY_PERCENT_SAG_CURRENT);
	}

	public float getBatteryTwentyPercentSagCurrentMargin() {
		return entityData.get(BATTERY_TWENTY_PERCENT_SAG_CURRENT_MARGIN);
	}

	public float getBatteryCurrentLimit() {
		return entityData.get(BATTERY_CURRENT_LIMIT);
	}

	public float getBatteryPowerLimit() {
		return entityData.get(BATTERY_POWER_LIMIT);
	}

	public float getFrameHealth() {
		return entityData.get(FRAME_HEALTH);
	}

	public float getRotorHealth() {
		return entityData.get(ROTOR_HEALTH);
	}

	public float getRotorHealth(int index) {
		return switch (index) {
			case 0 -> entityData.get(ROTOR_0_HEALTH);
			case 1 -> entityData.get(ROTOR_1_HEALTH);
			case 2 -> entityData.get(ROTOR_2_HEALTH);
			case 3 -> entityData.get(ROTOR_3_HEALTH);
			case 4 -> entityData.get(ROTOR_4_HEALTH);
			case 5 -> entityData.get(ROTOR_5_HEALTH);
			case 6 -> entityData.get(ROTOR_6_HEALTH);
			case 7 -> entityData.get(ROTOR_7_HEALTH);
			default -> (float) valueOrOne(physics.state().rotorHealth(), index);
		};
	}

	public int getPropStrikeCount() {
		return entityData.get(PROP_STRIKE_COUNT);
	}

	public int getLastPropStrikeRotorIndex() {
		return entityData.get(LAST_PROP_STRIKE_ROTOR);
	}

	public double getLastPropStrikeSeverity() {
		return entityData.get(LAST_PROP_STRIKE_SEVERITY);
	}

	public float getControlThrottle() {
		return entityData.get(CONTROL_THROTTLE);
	}

	public float getControlPitch() {
		return entityData.get(CONTROL_PITCH);
	}

	public float getControlRoll() {
		return entityData.get(CONTROL_ROLL);
	}

	public float getControlYaw() {
		return entityData.get(CONTROL_YAW);
	}

	public float getControlLinkLossSeconds() {
		return entityData.get(CONTROL_LINK_LOSS_SECONDS);
	}

	public double getControlFrameAgeSeconds() {
		return physics.state().controlFrameAgeSeconds();
	}

	public double getControlFrameIntervalSeconds() {
		return physics.state().controlFrameIntervalSeconds();
	}

	public double getControlFrameError() {
		return physics.state().controlFrameError();
	}

	public boolean isRawControlLinkActive() {
		return entityData.get(RAW_CONTROL_LINK_ACTIVE);
	}

	public boolean isProcessedControlLinkActive() {
		return entityData.get(PROCESSED_CONTROL_LINK_ACTIVE);
	}

	public boolean isControlFailsafeActive() {
		return entityData.get(CONTROL_FAILSAFE);
	}

	public float getTargetPitchRateDegreesPerSecond() {
		return entityData.get(TARGET_PITCH_RATE);
	}

	public float getTargetYawRateDegreesPerSecond() {
		return entityData.get(TARGET_YAW_RATE);
	}

	public float getTargetRollRateDegreesPerSecond() {
		return entityData.get(TARGET_ROLL_RATE);
	}

	public float getGyroPitchRateDegreesPerSecond() {
		return entityData.get(GYRO_PITCH_RATE);
	}

	public float getGyroYawRateDegreesPerSecond() {
		return entityData.get(GYRO_YAW_RATE);
	}

	public float getGyroRollRateDegreesPerSecond() {
		return entityData.get(GYRO_ROLL_RATE);
	}

	public float getGyroClipIntensity() {
		return entityData.get(GYRO_CLIP);
	}

	public float getAccelerometerClipIntensity() {
		return entityData.get(ACCEL_CLIP);
	}

	public float getImuSupplyNoiseIntensity() {
		return entityData.get(IMU_SUPPLY_NOISE);
	}

	public float getGyroNotchFrequencyHertz() {
		return entityData.get(GYRO_NOTCH_FREQUENCY);
	}

	public float getGyroNotchAttenuation() {
		return entityData.get(GYRO_NOTCH_ATTENUATION);
	}

	public double getGyroNotchSpreadHertz() {
		return physics.state().gyroDynamicNotchSpreadHertz();
	}

	public double getGyroRpmHarmonicNotchAttenuation() {
		return physics.state().gyroRpmHarmonicNotchAttenuation();
	}

	public double getGyroBladePassNotchFrequencyHertz() {
		return physics.state().gyroBladePassNotchFrequencyHertz();
	}

	public double getGyroBladePassNotchAttenuation() {
		return physics.state().gyroBladePassNotchAttenuation();
	}

	public double getGyroBladePassNotchSpreadHertz() {
		return physics.state().gyroBladePassNotchSpreadHertz();
	}

	public float getPidPitchOutputNewtonMeters() {
		return entityData.get(PID_PITCH_OUTPUT);
	}

	public float getPidYawOutputNewtonMeters() {
		return entityData.get(PID_YAW_OUTPUT);
	}

	public float getPidRollOutputNewtonMeters() {
		return entityData.get(PID_ROLL_OUTPUT);
	}

	public float getPidAttenuation() {
		return entityData.get(PID_ATTENUATION);
	}

	public float getPidIntegralRelax() {
		return entityData.get(PID_INTEGRAL_RELAX);
	}

	public float getPidDTermLowPassCutoffHertz() {
		return entityData.get(PID_DTERM_LPF_CUTOFF);
	}

	public float getAntiGravityBoost() {
		return entityData.get(ANTI_GRAVITY_BOOST);
	}

	public float getEstimatedPitchDegrees() {
		return entityData.get(ESTIMATED_PITCH);
	}

	public float getEstimatedYawDegrees() {
		return entityData.get(ESTIMATED_YAW);
	}

	public float getEstimatedRollDegrees() {
		return entityData.get(ESTIMATED_ROLL);
	}

	public float getAttitudeEstimateErrorDegrees() {
		return entityData.get(ATTITUDE_ESTIMATE_ERROR);
	}

	public float getAttitudeAccelerometerTrust() {
		return entityData.get(ATTITUDE_ACCEL_TRUST);
	}

	public DroneBlackboxRecorder blackbox() {
		return blackbox;
	}

	public DroneConfig config() {
		return physics.config();
	}

	public DroneEnvironmentOverride environmentOverride() {
		return environmentOverride;
	}

	public DroneEnvironment currentEnvironment() {
		return lastEnvironment;
	}

	public void applyEnvironmentOverride(DroneEnvironmentOverride override) {
		environmentOverride = override == null ? DroneEnvironmentOverride.natural() : override;
	}

	public void applyConfig(DroneConfig config) {
		applyConfig(config, airframePreset);
	}

	public void applyConfig(DroneConfig config, String presetName) {
		airframePreset = normalizeAirframePreset(presetName);
		boolean rotorCountChanged = config.rotors().size() != physics.config().rotors().size();
		if (rotorCountChanged) {
			replacePhysics(config);
			resetPropStrikeTelemetry();
		} else {
			physics.applyConfig(config);
		}
		physics.resetControlLoops();
		ensureRotorStrikeArrays();
		syncAirframeLayout();
		refreshDimensions();
		updateDamageSyncedState();
	}

	private void replacePhysics(DroneConfig config) {
		DroneState previousState = physics.state();
		DronePhysics replacement = new DronePhysics(config);
		replacement.state().setPositionMeters(previousState.positionMeters());
		replacement.state().setVelocityMetersPerSecond(previousState.velocityMetersPerSecond());
		replacement.state().setOrientation(previousState.orientation());
		replacement.state().setEstimatedOrientation(previousState.estimatedOrientation());
		replacement.state().setAngularVelocityBodyRadiansPerSecond(previousState.angularVelocityBodyRadiansPerSecond());
		physics = replacement;
	}

	private static String normalizeAirframePreset(String presetName) {
		String normalized = presetName == null ? "" : presetName.toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "apdrone", "cinewhoop", "heavy_lift", "hex_lift", "octo_lift", "coaxial_x8" -> normalized;
			default -> "racing_quad";
		};
	}

	private static DroneConfig configForPreset(String presetName) {
		return switch (normalizeAirframePreset(presetName)) {
			case "apdrone" -> DroneConfig.apDrone();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavy_lift" -> DroneConfig.heavyLift();
			case "hex_lift" -> DroneConfig.hexLift();
			case "octo_lift" -> DroneConfig.octoLift();
			case "coaxial_x8" -> DroneConfig.coaxialX8();
			default -> DroneConfig.racingQuad();
		};
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (!player.getItemInHand(hand).is(DroneItems.DRONE_CONTROLLER)) {
			return super.interact(player, hand);
		}

		if (!level().isClientSide()) {
			setOwner(player.getUUID());
			if (player.isShiftKeyDown()) {
				repair();
				player.displayClientMessage(Component.translatable("message.fpvdrone.repaired"), true);
			} else {
				player.displayClientMessage(Component.translatable("message.fpvdrone.bound"), true);
			}
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		if (owner != null) {
			output.putString("owner", owner.toString());
		}
		output.putDouble("frame_health", frameHealth);
		output.putDouble("battery_amp_seconds_consumed", physics.state().batteryAmpSecondsConsumed());
		output.putDouble("battery_equivalent_cycles", physics.state().batteryEquivalentCycles());
		output.putDouble("battery_slow_polarization_v", physics.state().batterySlowPolarizationVoltage());
		output.putDouble("battery_temp_c", physics.state().batteryTemperatureCelsius());
		output.putDouble("battery_cooling_factor", physics.state().batteryCoolingFactor());
		output.putDouble("battery_thermal_limit", physics.state().batteryThermalLimit());
		for (int i = 0; i < physics.state().motorCount(); i++) {
			output.putDouble("motor_temperature_c_" + i, physics.state().motorTemperatureCelsius(i));
			output.putDouble("esc_temperature_c_" + i, physics.state().escTemperatureCelsius(i));
			output.putDouble("motor_cooling_factor_" + i, physics.state().motorCoolingFactor(i));
			output.putDouble("esc_cooling_factor_" + i, physics.state().escCoolingFactor(i));
		}
		saveRotorDynamicState(output);
		saveAerodynamicTransientState(output);
		double[] rotorHealth = physics.state().rotorHealth();
		for (int i = 0; i < rotorHealth.length; i++) {
			output.putDouble("rotor_health_" + i, rotorHealth[i]);
		}
		saveEnvironmentOverride(output);
		saveConfig(output);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		input.getString("owner").ifPresent(ownerId -> {
			try {
				setOwner(UUID.fromString(ownerId));
			} catch (IllegalArgumentException ignored) {
				setOwner(null);
			}
		});
		frameHealth = input.getDoubleOr("frame_health", 1.0);
		loadEnvironmentOverride(input);
		loadConfig(input);
		physics.state().setBatteryAmpSecondsConsumed(input.getDoubleOr("battery_amp_seconds_consumed", 0.0));
		physics.state().setBatteryEquivalentCycles(input.getDoubleOr("battery_equivalent_cycles", 0.0));
		loadBatteryTransientState(input);
		loadPowertrainThermalState(input);
		loadRotorDynamicState(input);
		loadAerodynamicTransientState(input);
		physics.state().repairAllRotors();
		for (int i = 0; i < physics.config().rotors().size(); i++) {
			double health = input.getDoubleOr("rotor_health_" + i, 1.0);
			physics.state().damageRotor(i, 1.0 - health);
		}
		updateDamageSyncedState();
	}

	private void loadBatteryTransientState(ValueInput input) {
		double slowPolarization = input.getDoubleOr("battery_slow_polarization_v", Double.NaN);
		double temperatureCelsius = input.getDoubleOr("battery_temp_c", Double.NaN);
		double coolingFactor = input.getDoubleOr("battery_cooling_factor", Double.NaN);
		double thermalLimit = input.getDoubleOr("battery_thermal_limit", Double.NaN);
		if (!Double.isFinite(slowPolarization)
				&& !Double.isFinite(temperatureCelsius)
				&& !Double.isFinite(coolingFactor)
				&& !Double.isFinite(thermalLimit)) {
			return;
		}

		DroneState state = physics.state();
		physics.restoreBatteryTransientState(
				Double.isFinite(slowPolarization) ? slowPolarization : state.batterySlowPolarizationVoltage(),
				Double.isFinite(temperatureCelsius) ? temperatureCelsius : state.batteryTemperatureCelsius(),
				Double.isFinite(coolingFactor) ? coolingFactor : state.batteryCoolingFactor(),
				Double.isFinite(thermalLimit) ? thermalLimit : state.batteryThermalLimit()
		);
	}

	private void saveRotorDynamicState(ValueOutput output) {
		DronePhysics.RotorDynamicState dynamicState = physics.rotorDynamicStateSnapshot();
		double[] motorOmega = dynamicState.motorOmegaRadiansPerSecond();
		double[] escOutput = dynamicState.escOutputCommand();
		double[] escElectricalOutput = dynamicState.escElectricalOutputCommand();
		double[] telemetryRpm = dynamicState.motorRpmTelemetryRpm();
		double[] telemetryValidity = dynamicState.motorRpmTelemetryValidity();
		double[] inducedVelocity = dynamicState.rotorInducedVelocityMetersPerSecond();
		double[] inducedLagScale = dynamicState.rotorInducedLagThrustScale();
		double[] wakeVelocity = dynamicState.rotorInducedWakeVelocityMetersPerSecond();
		double[] wakeCarryover = dynamicState.rotorInducedWakeCarryoverIntensity();
		double[] surfaceWetness = dynamicState.rotorSurfaceWetness();
		double[] icingSeverity = dynamicState.rotorIcingSeverity();
		for (int i = 0; i < physics.state().motorCount(); i++) {
			output.putDouble("motor_omega_rad_s_" + i, motorOmega[i]);
			output.putDouble("esc_output_command_" + i, escOutput[i]);
			output.putDouble("esc_electrical_output_command_" + i, escElectricalOutput[i]);
			output.putDouble("motor_rpm_telemetry_rpm_" + i, telemetryRpm[i]);
			output.putDouble("motor_rpm_telemetry_validity_" + i, telemetryValidity[i]);
			output.putDouble("rotor_induced_velocity_mps_" + i, inducedVelocity[i]);
			output.putDouble("rotor_induced_lag_thrust_scale_" + i, inducedLagScale[i]);
			output.putDouble("rotor_induced_wake_velocity_mps_" + i, wakeVelocity[i]);
			output.putDouble("rotor_induced_wake_carryover_" + i, wakeCarryover[i]);
			output.putDouble("rotor_surface_wetness_" + i, surfaceWetness[i]);
			output.putDouble("rotor_icing_severity_" + i, icingSeverity[i]);
		}
		output.putDouble("propwash_wake_intensity", dynamicState.propwashWakeIntensity());
		output.putDouble("propwash_intensity", dynamicState.propwashIntensity());
		output.putDouble("vrs_intensity", dynamicState.vortexRingStateIntensity());
		output.putDouble("vrs_thrust_buffet_amplitude", dynamicState.vortexRingThrustBuffetAmplitude());
		output.putDouble("vrs_max_thrust_buffet_amplitude", dynamicState.vortexRingMaxThrustBuffetAmplitude());
	}

	private void loadPowertrainThermalState(ValueInput input) {
		int count = physics.config().rotors().size();
		double[] motorTemperaturesCelsius = new double[count];
		double[] escTemperaturesCelsius = new double[count];
		double[] motorCoolingFactors = new double[count];
		double[] escCoolingFactors = new double[count];
		Arrays.fill(motorTemperaturesCelsius, Double.NaN);
		Arrays.fill(escTemperaturesCelsius, Double.NaN);
		Arrays.fill(motorCoolingFactors, Double.NaN);
		Arrays.fill(escCoolingFactors, Double.NaN);

		boolean hasThermalState = false;
		for (int i = 0; i < count; i++) {
			motorTemperaturesCelsius[i] = input.getDoubleOr("motor_temperature_c_" + i, Double.NaN);
			escTemperaturesCelsius[i] = input.getDoubleOr("esc_temperature_c_" + i, Double.NaN);
			motorCoolingFactors[i] = input.getDoubleOr("motor_cooling_factor_" + i, Double.NaN);
			escCoolingFactors[i] = input.getDoubleOr("esc_cooling_factor_" + i, Double.NaN);
			hasThermalState = hasThermalState
					|| Double.isFinite(motorTemperaturesCelsius[i])
					|| Double.isFinite(escTemperaturesCelsius[i])
					|| Double.isFinite(motorCoolingFactors[i])
					|| Double.isFinite(escCoolingFactors[i]);
		}

		if (hasThermalState) {
			physics.restorePowertrainThermalState(
					motorTemperaturesCelsius,
					escTemperaturesCelsius,
					motorCoolingFactors,
					escCoolingFactors
			);
		}
	}

	private void loadRotorDynamicState(ValueInput input) {
		int count = physics.config().rotors().size();
		double[] motorOmega = new double[count];
		double[] escOutput = new double[count];
		double[] escElectricalOutput = new double[count];
		double[] telemetryRpm = new double[count];
		double[] telemetryValidity = new double[count];
		double[] inducedVelocity = new double[count];
		double[] inducedLagScale = new double[count];
		double[] wakeVelocity = new double[count];
		double[] wakeCarryover = new double[count];
		double[] surfaceWetness = new double[count];
		double[] icingSeverity = new double[count];
		Arrays.fill(motorOmega, Double.NaN);
		Arrays.fill(escOutput, Double.NaN);
		Arrays.fill(escElectricalOutput, Double.NaN);
		Arrays.fill(telemetryRpm, Double.NaN);
		Arrays.fill(telemetryValidity, Double.NaN);
		Arrays.fill(inducedVelocity, Double.NaN);
		Arrays.fill(inducedLagScale, Double.NaN);
		Arrays.fill(wakeVelocity, Double.NaN);
		Arrays.fill(wakeCarryover, Double.NaN);
		Arrays.fill(surfaceWetness, Double.NaN);
		Arrays.fill(icingSeverity, Double.NaN);

		boolean hasDynamicState = false;
		for (int i = 0; i < count; i++) {
			motorOmega[i] = input.getDoubleOr("motor_omega_rad_s_" + i, Double.NaN);
			escOutput[i] = input.getDoubleOr("esc_output_command_" + i, Double.NaN);
			escElectricalOutput[i] = input.getDoubleOr("esc_electrical_output_command_" + i, Double.NaN);
			telemetryRpm[i] = input.getDoubleOr("motor_rpm_telemetry_rpm_" + i, Double.NaN);
			telemetryValidity[i] = input.getDoubleOr("motor_rpm_telemetry_validity_" + i, Double.NaN);
			inducedVelocity[i] = input.getDoubleOr("rotor_induced_velocity_mps_" + i, Double.NaN);
			inducedLagScale[i] = input.getDoubleOr("rotor_induced_lag_thrust_scale_" + i, Double.NaN);
			wakeVelocity[i] = input.getDoubleOr("rotor_induced_wake_velocity_mps_" + i, Double.NaN);
			wakeCarryover[i] = input.getDoubleOr("rotor_induced_wake_carryover_" + i, Double.NaN);
			surfaceWetness[i] = input.getDoubleOr("rotor_surface_wetness_" + i, Double.NaN);
			icingSeverity[i] = input.getDoubleOr("rotor_icing_severity_" + i, Double.NaN);
			hasDynamicState = hasDynamicState
					|| Double.isFinite(motorOmega[i])
					|| Double.isFinite(escOutput[i])
					|| Double.isFinite(escElectricalOutput[i])
					|| Double.isFinite(telemetryRpm[i])
					|| Double.isFinite(telemetryValidity[i])
					|| Double.isFinite(inducedVelocity[i])
					|| Double.isFinite(inducedLagScale[i])
					|| Double.isFinite(wakeVelocity[i])
					|| Double.isFinite(wakeCarryover[i])
					|| Double.isFinite(surfaceWetness[i])
					|| Double.isFinite(icingSeverity[i]);
		}

		double propwashWake = input.getDoubleOr("propwash_wake_intensity", Double.NaN);
		double propwash = input.getDoubleOr("propwash_intensity", Double.NaN);
		double vortexRingState = input.getDoubleOr("vrs_intensity", Double.NaN);
		double vortexRingBuffet = input.getDoubleOr("vrs_thrust_buffet_amplitude", Double.NaN);
		double vortexRingMaxBuffet = input.getDoubleOr("vrs_max_thrust_buffet_amplitude", Double.NaN);
		hasDynamicState = hasDynamicState
				|| Double.isFinite(propwashWake)
				|| Double.isFinite(propwash)
				|| Double.isFinite(vortexRingState)
				|| Double.isFinite(vortexRingBuffet)
				|| Double.isFinite(vortexRingMaxBuffet);

		if (hasDynamicState) {
			physics.restoreRotorDynamicState(new DronePhysics.RotorDynamicState(
					motorOmega,
					escOutput,
					escElectricalOutput,
					telemetryRpm,
					telemetryValidity,
					inducedVelocity,
					inducedLagScale,
					wakeVelocity,
					wakeCarryover,
					surfaceWetness,
					icingSeverity,
					propwashWake,
					propwash,
					vortexRingState,
					vortexRingBuffet,
					vortexRingMaxBuffet
			));
		}
	}

	private void saveAerodynamicTransientState(ValueOutput output) {
		DronePhysics.AerodynamicTransientState state = physics.aerodynamicTransientStateSnapshot();
		saveVec(output, "aero_mean_wind", state.meanWindVelocityWorldMetersPerSecond());
		saveVec(output, "aero_wind_burble", state.windBurbleVelocityWorldMetersPerSecond());
		saveVec(output, "aero_dryden_first", state.drydenFirstOrderVelocityWorldMetersPerSecond());
		saveVec(output, "aero_dryden_lag", state.drydenTransverseLagVelocityWorldMetersPerSecond());
		saveVec(output, "aero_dryden_turbulence", state.drydenTurbulenceVelocityWorldMetersPerSecond());
		saveVec(output, "aero_wind_gust", state.windGustVelocityWorldMetersPerSecond());
		output.putString("aero_dryden_random_state", Long.toString(state.drydenRandomState()));
		output.putDouble("aero_dryden_spare_gaussian", state.drydenSpareGaussian());
		output.putString("aero_dryden_has_spare", Boolean.toString(state.hasDrydenSpareGaussian()));
		output.putString("aero_wind_initialized", Boolean.toString(state.windModelInitialized()));
		output.putDouble("aero_wind_gust_phase_a", state.windGustPhaseA());
		output.putDouble("aero_wind_gust_phase_b", state.windGustPhaseB());
		output.putDouble("aero_wind_gust_phase_c", state.windGustPhaseC());
		output.putDouble("aero_turbulence_phase_a", state.turbulencePhaseA());
		output.putDouble("aero_turbulence_phase_b", state.turbulencePhaseB());
		output.putDouble("aero_turbulence_phase_c", state.turbulencePhaseC());
		output.putDouble("aero_airframe_separation", state.airframeSeparatedFlowIntensity());
		output.putDouble("aero_airframe_separation_buffet_phase_a", state.airframeSeparationBuffetPhaseA());
		output.putDouble("aero_airframe_separation_buffet_phase_b", state.airframeSeparationBuffetPhaseB());
		saveVec(output, "aero_rotor_wash_drag", state.rotorWashDragForceBody());
		saveVec(output, "aero_rotor_wash_angular_damping", state.rotorWashAirframeAngularDamping());
		saveVec(output, "aero_dynamic_pressure_center", state.dynamicPressureCenterOffsetBody());
		saveVec(output, "aero_airframe_lift_force", state.airframeLiftForceBody());
		saveVec(output, "aero_airframe_drag_force", state.airframeDragForceBody());
		saveVec(output, "aero_ground_effect_drag", state.groundEffectDragForceBody());
		saveVec(output, "aero_ground_effect_leveling_torque", state.groundEffectLevelingTorqueBody());
	}

	private void loadAerodynamicTransientState(ValueInput input) {
		boolean hasState = hasVec(input, "aero_mean_wind")
				|| hasVec(input, "aero_wind_burble")
				|| hasVec(input, "aero_dryden_first")
				|| hasVec(input, "aero_dryden_lag")
				|| hasVec(input, "aero_dryden_turbulence")
				|| hasVec(input, "aero_wind_gust")
				|| input.getString("aero_dryden_random_state").isPresent()
				|| input.getString("aero_dryden_has_spare").isPresent()
				|| input.getString("aero_wind_initialized").isPresent()
				|| Double.isFinite(input.getDoubleOr("aero_wind_gust_phase_a", Double.NaN))
				|| Double.isFinite(input.getDoubleOr("aero_airframe_separation", Double.NaN))
				|| hasVec(input, "aero_rotor_wash_drag")
				|| hasVec(input, "aero_airframe_drag_force")
				|| hasVec(input, "aero_ground_effect_leveling_torque");
		if (!hasState) {
			return;
		}

		physics.restoreAerodynamicTransientState(new DronePhysics.AerodynamicTransientState(
				loadVec(input, "aero_mean_wind", Vec3.ZERO),
				loadVec(input, "aero_wind_burble", Vec3.ZERO),
				loadVec(input, "aero_dryden_first", Vec3.ZERO),
				loadVec(input, "aero_dryden_lag", Vec3.ZERO),
				loadVec(input, "aero_dryden_turbulence", Vec3.ZERO),
				loadVec(input, "aero_wind_gust", Vec3.ZERO),
				loadLong(input, "aero_dryden_random_state", 0x6A09E667F3BCC909L),
				input.getDoubleOr("aero_dryden_spare_gaussian", 0.0),
				loadBoolean(input, "aero_dryden_has_spare", false),
				loadBoolean(input, "aero_wind_initialized", true),
				input.getDoubleOr("aero_wind_gust_phase_a", 0.0),
				input.getDoubleOr("aero_wind_gust_phase_b", 0.0),
				input.getDoubleOr("aero_wind_gust_phase_c", 0.0),
				input.getDoubleOr("aero_turbulence_phase_a", 0.0),
				input.getDoubleOr("aero_turbulence_phase_b", 0.0),
				input.getDoubleOr("aero_turbulence_phase_c", 0.0),
				input.getDoubleOr("aero_airframe_separation", 0.0),
				input.getDoubleOr("aero_airframe_separation_buffet_phase_a", 0.0),
				input.getDoubleOr("aero_airframe_separation_buffet_phase_b", 0.0),
				loadVec(input, "aero_rotor_wash_drag", Vec3.ZERO),
				loadVec(input, "aero_rotor_wash_angular_damping", Vec3.ZERO),
				loadVec(input, "aero_dynamic_pressure_center", Vec3.ZERO),
				loadVec(input, "aero_airframe_lift_force", Vec3.ZERO),
				loadVec(input, "aero_airframe_drag_force", Vec3.ZERO),
				loadVec(input, "aero_ground_effect_drag", Vec3.ZERO),
				loadVec(input, "aero_ground_effect_leveling_torque", Vec3.ZERO)
		));
	}

	private static void saveVec(ValueOutput output, String prefix, Vec3 value) {
		Vec3 safeValue = value == null || !value.isFinite() ? Vec3.ZERO : value;
		output.putDouble(prefix + "_x", safeValue.x());
		output.putDouble(prefix + "_y", safeValue.y());
		output.putDouble(prefix + "_z", safeValue.z());
	}

	private static Vec3 loadVec(ValueInput input, String prefix, Vec3 fallback) {
		Vec3 safeFallback = fallback == null ? Vec3.ZERO : fallback;
		double x = input.getDoubleOr(prefix + "_x", safeFallback.x());
		double y = input.getDoubleOr(prefix + "_y", safeFallback.y());
		double z = input.getDoubleOr(prefix + "_z", safeFallback.z());
		Vec3 value = new Vec3(x, y, z);
		return value.isFinite() ? value : safeFallback;
	}

	private static boolean hasVec(ValueInput input, String prefix) {
		return Double.isFinite(input.getDoubleOr(prefix + "_x", Double.NaN))
				|| Double.isFinite(input.getDoubleOr(prefix + "_y", Double.NaN))
				|| Double.isFinite(input.getDoubleOr(prefix + "_z", Double.NaN));
	}

	private static long loadLong(ValueInput input, String key, long fallback) {
		return input.getString(key).map(value -> {
			try {
				return Long.parseLong(value);
			} catch (NumberFormatException ignored) {
				return fallback;
			}
		}).orElse(fallback);
	}

	private static boolean loadBoolean(ValueInput input, String key, boolean fallback) {
		return input.getString(key).map(Boolean::parseBoolean).orElse(fallback);
	}

	private void saveEnvironmentOverride(ValueOutput output) {
		output.putString("env_wind_enabled", Boolean.toString(environmentOverride.windEnabled()));
		output.putDouble("env_wind_x", environmentOverride.windVelocityWorldMetersPerSecond().x());
		output.putDouble("env_wind_y", environmentOverride.windVelocityWorldMetersPerSecond().y());
		output.putDouble("env_wind_z", environmentOverride.windVelocityWorldMetersPerSecond().z());
		output.putString("env_turbulence_enabled", Boolean.toString(environmentOverride.turbulenceEnabled()));
		output.putDouble("env_turbulence", environmentOverride.turbulenceIntensity());
		output.putString("env_density_enabled", Boolean.toString(environmentOverride.airDensityEnabled()));
		output.putDouble("env_density", environmentOverride.airDensityRatio());
	}

	private void loadEnvironmentOverride(ValueInput input) {
		boolean windEnabled = input.getString("env_wind_enabled").map(Boolean::parseBoolean).orElse(false);
		boolean turbulenceEnabled = input.getString("env_turbulence_enabled").map(Boolean::parseBoolean).orElse(false);
		boolean densityEnabled = input.getString("env_density_enabled").map(Boolean::parseBoolean).orElse(false);
		environmentOverride = new DroneEnvironmentOverride(
				windEnabled,
				new Vec3(
						input.getDoubleOr("env_wind_x", 0.0),
						input.getDoubleOr("env_wind_y", 0.0),
						input.getDoubleOr("env_wind_z", 0.0)
				),
				turbulenceEnabled,
				input.getDoubleOr("env_turbulence", 0.0),
				densityEnabled,
				input.getDoubleOr("env_density", 1.0)
		);
	}

	private void saveConfig(ValueOutput output) {
		DroneConfig config = physics.config();
		RotorSpec rotor = config.rotors().get(0);
		output.putString("airframe_preset", airframePreset);
		output.putDouble("tune_mass_kg", config.massKg());
		output.putDouble("tune_inertia_x", config.inertiaKgMetersSquared().x());
		output.putDouble("tune_inertia_y", config.inertiaKgMetersSquared().y());
		output.putDouble("tune_inertia_z", config.inertiaKgMetersSquared().z());
		output.putDouble("tune_cg_x", config.centerOfMassOffsetBodyMeters().x());
		output.putDouble("tune_cg_y", config.centerOfMassOffsetBodyMeters().y());
		output.putDouble("tune_cg_z", config.centerOfMassOffsetBodyMeters().z());
		output.putDouble("tune_imu_x", config.imuOffsetBodyMeters().x());
		output.putDouble("tune_imu_y", config.imuOffsetBodyMeters().y());
		output.putDouble("tune_imu_z", config.imuOffsetBodyMeters().z());
		output.putDouble("tune_cp_x", config.centerOfPressureOffsetBodyMeters().x());
		output.putDouble("tune_cp_y", config.centerOfPressureOffsetBodyMeters().y());
		output.putDouble("tune_cp_z", config.centerOfPressureOffsetBodyMeters().z());
		output.putDouble("tune_angular_drag", config.angularDragCoefficient());
		output.putDouble("tune_motor_tau", config.motorTimeConstantSeconds());
		output.putDouble("tune_esc_curve", config.escOutputCurveExponent());
		output.putDouble("tune_throttle_curve", config.throttleCommandCurveExponent());
		output.putDouble("tune_esc_slew", config.escOutputSlewRatePerSecond());
		output.putDouble("tune_esc_down_slew", config.escOutputFallSlewRatePerSecond());
		output.putDouble("tune_esc_deadband", config.escDeadband());
		output.putDouble("tune_motor_brake", config.motorActiveBrakingStrength());
		output.putDouble("tune_voltage_compensation", config.voltageCompensationStrength());
		output.putDouble("tune_esc_frame_rate", config.escCommandFrameRateHertz());
		output.putDouble("tune_esc_resolution", config.escCommandResolutionSteps());
		output.putDouble("tune_esc_dshot_bitrate", config.escCommandProtocol().bitrateKilobitsPerSecond());
		output.putDouble("tune_motor_heat_rate", config.motorThermalRiseCelsiusPerSecond());
		output.putDouble("tune_motor_cooling_rate", config.motorCoolingRatePerSecond());
		output.putDouble("tune_motor_temp_limit", config.motorThermalLimitCelsius());
		output.putDouble("tune_motor_temp_cutoff", config.motorThermalCutoffCelsius());
		output.putDouble("tune_gyro_lpf", config.gyroLowPassCutoffHz());
		output.putDouble("tune_gyro_noise", config.gyroNoiseStdDevRadiansPerSecond());
		output.putDouble("tune_accel_lpf", config.accelerometerLowPassCutoffHz());
		output.putDouble("tune_accel_noise", config.accelerometerNoiseStdDevMetersPerSecondSquared());
		output.putDouble("tune_control_latency", config.controlLatencySeconds());
		output.putDouble("tune_rc_smoothing", config.rcCommandSmoothingTimeConstantSeconds());
		output.putDouble("tune_rc_latency", config.rcCommandLatencySeconds());
		output.putDouble("tune_rc_failsafe", config.rcFailsafeTimeoutSeconds());
		output.putDouble("tune_rc_frame_rate", config.rcFrameRateHertz());
		output.putDouble("tune_rc_resolution", config.rcChannelResolutionSteps());
		output.putDouble("tune_attitude_accel_gain", config.attitudeEstimatorAccelerometerCorrectionGain());
		output.putDouble("tune_attitude_accel_trust", config.attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared());
		output.putDouble("tune_battery_nominal_voltage", config.nominalBatteryVoltage());
		output.putDouble("tune_battery_empty_voltage", config.emptyBatteryVoltage());
		output.putDouble("tune_battery_resistance", config.batteryInternalResistanceOhms());
		output.putDouble("tune_battery_capacity_ah", config.batteryCapacityAmpHours());
		output.putDouble("tune_battery_max_current", config.maxBatteryCurrentAmps());
		output.putDouble("tune_linear_drag", config.linearDragCoefficient());
		output.putDouble("tune_body_drag_x", config.bodyDragCoefficients().x());
		output.putDouble("tune_body_drag_y", config.bodyDragCoefficients().y());
		output.putDouble("tune_body_drag_z", config.bodyDragCoefficients().z());
		output.putDouble("tune_rotor_max_thrust", rotor.maxThrustNewtons());
		output.putDouble("tune_rotor_thrust_coefficient", rotor.thrustCoefficient());
		output.putDouble("tune_rotor_radius", rotor.radiusMeters());
		output.putDouble("tune_rotor_blade_pitch", rotor.bladePitchMeters());
		output.putDouble("tune_rotor_blade_count", rotor.bladeCount());
		output.putDouble("tune_motor_pole_pairs", rotor.motorPolePairs());
		output.putDouble("tune_rotor_transverse_lift", rotor.transverseFlowLiftCoefficient());
		output.putDouble("tune_rotor_axial_loss", rotor.axialFlowThrustLossCoefficient());
		output.putDouble("tune_rotor_disk_drag", rotor.diskDragCoefficient());
		output.putDouble("tune_rotor_flapping", rotor.flappingCoefficient());
		output.putDouble("tune_rotor_stall_loss", rotor.stallThrustLossCoefficient());
		output.putDouble("tune_rotor_yaw_torque", rotor.yawTorquePerThrustMeter());
		output.putDouble("tune_rotor_outward_cant", config.averageRotorOutwardCantDegrees());
		output.putDouble("tune_rotor_inertia", rotor.rotorInertiaKgMetersSquared());
		output.putDouble("tune_rotor_inflow_tau", rotor.inducedInflowTimeConstantSeconds());
		output.putDouble("tune_rotor_inflow_lag", rotor.inducedInflowLagCoefficient());
		output.putDouble("tune_ground_effect_height", config.groundEffectHeightMeters());
		output.putDouble("tune_ground_effect_boost", config.groundEffectMaxThrustBoost());
		output.putDouble("tune_propwash_start", config.propwashStartDescentMetersPerSecond());
		output.putDouble("tune_propwash_full", config.propwashFullDescentMetersPerSecond());
		output.putDouble("tune_propwash_torque", config.propwashMaxTorqueNewtonMeters());
		output.putDouble("tune_motor_idle", config.motorIdleThrustFraction());
		output.putDouble("tune_airmode_strength", config.airmodeStrength());
		output.putDouble("tune_pitch_rate_dps", Math.toDegrees(config.maxPitchRateRadiansPerSecond()));
		output.putDouble("tune_yaw_rate_dps", Math.toDegrees(config.maxYawRateRadiansPerSecond()));
		output.putDouble("tune_roll_rate_dps", Math.toDegrees(config.maxRollRateRadiansPerSecond()));
		output.putDouble("tune_pitch_expo", config.rateExpo().x());
		output.putDouble("tune_yaw_expo", config.rateExpo().y());
		output.putDouble("tune_roll_expo", config.rateExpo().z());
		output.putDouble("tune_pitch_super_rate", config.rateSuper().x());
		output.putDouble("tune_yaw_super_rate", config.rateSuper().y());
		output.putDouble("tune_roll_super_rate", config.rateSuper().z());
		output.putDouble("tune_level_angle_deg", Math.toDegrees(config.selfLevelMaxAngleRadians()));
		output.putDouble("tune_level_gain", config.selfLevelRateGain());
		output.putDouble("tune_horizon_start", config.horizonTransitionStartStick());
		output.putDouble("tune_horizon_end", config.horizonTransitionEndStick());
		output.putDouble("tune_iterm_relax", config.pidIntegralRelaxStrength());
		saveGains(output, "pitch", config.pitchGains());
		saveGains(output, "yaw", config.yawGains());
		saveGains(output, "roll", config.rollGains());
	}

	private static void saveGains(ValueOutput output, String axis, PidGains gains) {
		output.putDouble("tune_" + axis + "_p", gains.p());
		output.putDouble("tune_" + axis + "_i", gains.i());
		output.putDouble("tune_" + axis + "_d", gains.d());
		output.putDouble("tune_" + axis + "_limit", gains.integratorLimit());
		output.putDouble("tune_" + axis + "_feedforward", gains.feedForward());
		output.putDouble("tune_" + axis + "_dterm_lpf", gains.dTermLowPassCutoffHz());
		output.putDouble("tune_" + axis + "_anti_gravity", gains.antiGravityGain());
		output.putDouble("tune_" + axis + "_tpa_breakpoint", gains.tpaBreakpoint());
		output.putDouble("tune_" + axis + "_tpa_strength", gains.tpaStrength());
	}

	private void loadConfig(ValueInput input) {
		String preset = normalizeAirframePreset(input.getString("airframe_preset").orElse("racing_quad"));
		DroneConfig defaults = configForPreset(preset);
		RotorSpec defaultRotor = defaults.rotors().get(0);
		DroneConfig config = defaults
				.withMassKg(input.getDoubleOr("tune_mass_kg", defaults.massKg()))
				.withInertiaKgMetersSquared(new Vec3(
						input.getDoubleOr("tune_inertia_x", defaults.inertiaKgMetersSquared().x()),
						input.getDoubleOr("tune_inertia_y", defaults.inertiaKgMetersSquared().y()),
						input.getDoubleOr("tune_inertia_z", defaults.inertiaKgMetersSquared().z())
				))
				.withCenterOfMassOffsetBodyMeters(new Vec3(
						input.getDoubleOr("tune_cg_x", defaults.centerOfMassOffsetBodyMeters().x()),
						input.getDoubleOr("tune_cg_y", defaults.centerOfMassOffsetBodyMeters().y()),
						input.getDoubleOr("tune_cg_z", defaults.centerOfMassOffsetBodyMeters().z())
				))
				.withImuOffsetBodyMeters(new Vec3(
						input.getDoubleOr("tune_imu_x", defaults.imuOffsetBodyMeters().x()),
						input.getDoubleOr("tune_imu_y", defaults.imuOffsetBodyMeters().y()),
						input.getDoubleOr("tune_imu_z", defaults.imuOffsetBodyMeters().z())
				))
				.withCenterOfPressureOffsetBodyMeters(new Vec3(
						input.getDoubleOr("tune_cp_x", defaults.centerOfPressureOffsetBodyMeters().x()),
						input.getDoubleOr("tune_cp_y", defaults.centerOfPressureOffsetBodyMeters().y()),
						input.getDoubleOr("tune_cp_z", defaults.centerOfPressureOffsetBodyMeters().z())
				))
				.withAngularDragCoefficient(input.getDoubleOr("tune_angular_drag", defaults.angularDragCoefficient()))
				.withMotorTimeConstantSeconds(input.getDoubleOr("tune_motor_tau", defaults.motorTimeConstantSeconds()))
				.withThrottleCommandCurveExponent(
						input.getDoubleOr("tune_throttle_curve", defaults.throttleCommandCurveExponent())
				)
				.withEscMotorResponse(
						input.getDoubleOr("tune_esc_curve", defaults.escOutputCurveExponent()),
						input.getDoubleOr("tune_esc_slew", defaults.escOutputSlewRatePerSecond()),
						input.getDoubleOr("tune_esc_down_slew", defaults.escOutputFallSlewRatePerSecond()),
						input.getDoubleOr("tune_esc_deadband", defaults.escDeadband()),
						input.getDoubleOr("tune_voltage_compensation", defaults.voltageCompensationStrength()),
						input.getDoubleOr("tune_motor_brake", defaults.motorActiveBrakingStrength())
				)
				.withEscCommandSignal(
						input.getDoubleOr("tune_esc_frame_rate", defaults.escCommandFrameRateHertz()),
						input.getDoubleOr("tune_esc_resolution", defaults.escCommandResolutionSteps())
				)
				.withMotorThermal(
						input.getDoubleOr("tune_motor_heat_rate", defaults.motorThermalRiseCelsiusPerSecond()),
						input.getDoubleOr("tune_motor_cooling_rate", defaults.motorCoolingRatePerSecond()),
						input.getDoubleOr("tune_motor_temp_limit", defaults.motorThermalLimitCelsius()),
						input.getDoubleOr("tune_motor_temp_cutoff", defaults.motorThermalCutoffCelsius())
				)
				.withFlightControllerSensors(
						input.getDoubleOr("tune_gyro_lpf", defaults.gyroLowPassCutoffHz()),
						input.getDoubleOr("tune_gyro_noise", defaults.gyroNoiseStdDevRadiansPerSecond()),
						input.getDoubleOr("tune_accel_lpf", defaults.accelerometerLowPassCutoffHz()),
						input.getDoubleOr("tune_accel_noise", defaults.accelerometerNoiseStdDevMetersPerSecondSquared()),
						input.getDoubleOr("tune_control_latency", defaults.controlLatencySeconds())
				)
				.withControlLink(
						input.getDoubleOr("tune_rc_smoothing", defaults.rcCommandSmoothingTimeConstantSeconds()),
						input.getDoubleOr("tune_rc_latency", defaults.rcCommandLatencySeconds()),
						input.getDoubleOr("tune_rc_failsafe", defaults.rcFailsafeTimeoutSeconds())
				)
				.withControlReceiver(
						input.getDoubleOr("tune_rc_frame_rate", defaults.rcFrameRateHertz()),
						input.getDoubleOr("tune_rc_resolution", defaults.rcChannelResolutionSteps())
				)
				.withAttitudeEstimator(
						input.getDoubleOr("tune_attitude_accel_gain", defaults.attitudeEstimatorAccelerometerCorrectionGain()),
						input.getDoubleOr("tune_attitude_accel_trust", defaults.attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared())
				)
				.withBattery(
						input.getDoubleOr("tune_battery_nominal_voltage", defaults.nominalBatteryVoltage()),
						input.getDoubleOr("tune_battery_empty_voltage", defaults.emptyBatteryVoltage()),
						input.getDoubleOr("tune_battery_resistance", defaults.batteryInternalResistanceOhms()),
						input.getDoubleOr("tune_battery_capacity_ah", defaults.batteryCapacityAmpHours()),
						input.getDoubleOr("tune_battery_max_current", defaults.maxBatteryCurrentAmps())
				)
				.withLinearDragCoefficient(input.getDoubleOr("tune_linear_drag", defaults.linearDragCoefficient()))
				.withBodyDragCoefficients(new Vec3(
						input.getDoubleOr("tune_body_drag_x", defaults.bodyDragCoefficients().x()),
						input.getDoubleOr("tune_body_drag_y", defaults.bodyDragCoefficients().y()),
						input.getDoubleOr("tune_body_drag_z", defaults.bodyDragCoefficients().z())
				))
				.withRotorMaxThrustNewtons(input.getDoubleOr("tune_rotor_max_thrust", defaultRotor.maxThrustNewtons()))
				.withRotorThrustCoefficient(input.getDoubleOr("tune_rotor_thrust_coefficient", defaultRotor.thrustCoefficient()))
				.withRotorRadiusMeters(input.getDoubleOr("tune_rotor_radius", defaultRotor.radiusMeters()))
				.withRotorBladePitchMeters(input.getDoubleOr("tune_rotor_blade_pitch", defaultRotor.bladePitchMeters()))
				.withRotorBladeCount((int) Math.round(input.getDoubleOr("tune_rotor_blade_count", defaultRotor.bladeCount())))
				.withRotorMotorPolePairs(input.getDoubleOr("tune_motor_pole_pairs", defaultRotor.motorPolePairs()))
				.withRotorTransverseFlowLiftCoefficient(input.getDoubleOr("tune_rotor_transverse_lift", defaultRotor.transverseFlowLiftCoefficient()))
				.withRotorAxialFlowThrustLossCoefficient(input.getDoubleOr("tune_rotor_axial_loss", defaultRotor.axialFlowThrustLossCoefficient()))
				.withRotorDiskDragCoefficient(input.getDoubleOr("tune_rotor_disk_drag", defaultRotor.diskDragCoefficient()))
				.withRotorFlappingCoefficient(input.getDoubleOr("tune_rotor_flapping", defaultRotor.flappingCoefficient()))
				.withRotorStallThrustLossCoefficient(input.getDoubleOr("tune_rotor_stall_loss", defaultRotor.stallThrustLossCoefficient()))
				.withRotorYawTorquePerThrustMeter(input.getDoubleOr("tune_rotor_yaw_torque", defaultRotor.yawTorquePerThrustMeter()))
				.withRotorOutwardCantDegrees(input.getDoubleOr("tune_rotor_outward_cant", defaults.averageRotorOutwardCantDegrees()))
				.withRotorInertiaKgMetersSquared(input.getDoubleOr("tune_rotor_inertia", defaultRotor.rotorInertiaKgMetersSquared()))
				.withRotorInducedInflow(
						input.getDoubleOr("tune_rotor_inflow_tau", defaultRotor.inducedInflowTimeConstantSeconds()),
						input.getDoubleOr("tune_rotor_inflow_lag", defaultRotor.inducedInflowLagCoefficient())
				)
				.withGroundEffect(
						input.getDoubleOr("tune_ground_effect_height", defaults.groundEffectHeightMeters()),
						input.getDoubleOr("tune_ground_effect_boost", defaults.groundEffectMaxThrustBoost())
				)
				.withPropwash(
						input.getDoubleOr("tune_propwash_start", defaults.propwashStartDescentMetersPerSecond()),
						input.getDoubleOr("tune_propwash_full", defaults.propwashFullDescentMetersPerSecond()),
						input.getDoubleOr("tune_propwash_torque", defaults.propwashMaxTorqueNewtonMeters())
				)
				.withMotorIdleAndAirmode(
						input.getDoubleOr("tune_motor_idle", defaults.motorIdleThrustFraction()),
						input.getDoubleOr("tune_airmode_strength", defaults.airmodeStrength())
				)
				.withRates(
						Math.toRadians(input.getDoubleOr("tune_pitch_rate_dps", Math.toDegrees(defaults.maxPitchRateRadiansPerSecond()))),
						Math.toRadians(input.getDoubleOr("tune_yaw_rate_dps", Math.toDegrees(defaults.maxYawRateRadiansPerSecond()))),
						Math.toRadians(input.getDoubleOr("tune_roll_rate_dps", Math.toDegrees(defaults.maxRollRateRadiansPerSecond())))
				)
				.withRateExpo(new Vec3(
						input.getDoubleOr("tune_pitch_expo", defaults.rateExpo().x()),
						input.getDoubleOr("tune_yaw_expo", defaults.rateExpo().y()),
						input.getDoubleOr("tune_roll_expo", defaults.rateExpo().z())
				))
				.withRateSuper(new Vec3(
						input.getDoubleOr("tune_pitch_super_rate", defaults.rateSuper().x()),
						input.getDoubleOr("tune_yaw_super_rate", defaults.rateSuper().y()),
						input.getDoubleOr("tune_roll_super_rate", defaults.rateSuper().z())
				))
				.withSelfLevel(
						Math.toRadians(input.getDoubleOr("tune_level_angle_deg", Math.toDegrees(defaults.selfLevelMaxAngleRadians()))),
						input.getDoubleOr("tune_level_gain", defaults.selfLevelRateGain()),
						input.getDoubleOr("tune_horizon_start", defaults.horizonTransitionStartStick()),
						input.getDoubleOr("tune_horizon_end", defaults.horizonTransitionEndStick())
				)
				.withPidIntegralRelaxStrength(input.getDoubleOr("tune_iterm_relax", defaults.pidIntegralRelaxStrength()))
				.withPitchGains(loadGains(input, "pitch", defaults.pitchGains()))
				.withYawGains(loadGains(input, "yaw", defaults.yawGains()))
				.withRollGains(loadGains(input, "roll", defaults.rollGains()));
		double escDshotBitrate = input.getDoubleOr("tune_esc_dshot_bitrate", Double.NaN);
		if (Double.isFinite(escDshotBitrate)) {
			config = config.withEscCommandProtocolBitrate(escDshotBitrate);
		}
		applyConfig(config, preset);
	}

	private static PidGains loadGains(ValueInput input, String axis, PidGains defaults) {
		return new PidGains(
				input.getDoubleOr("tune_" + axis + "_p", defaults.p()),
				input.getDoubleOr("tune_" + axis + "_i", defaults.i()),
				input.getDoubleOr("tune_" + axis + "_d", defaults.d()),
				input.getDoubleOr("tune_" + axis + "_limit", defaults.integratorLimit()),
				input.getDoubleOr("tune_" + axis + "_feedforward", defaults.feedForward()),
				input.getDoubleOr("tune_" + axis + "_dterm_lpf", defaults.dTermLowPassCutoffHz()),
				input.getDoubleOr("tune_" + axis + "_anti_gravity", defaults.antiGravityGain()),
				input.getDoubleOr("tune_" + axis + "_tpa_breakpoint", defaults.tpaBreakpoint()),
				input.getDoubleOr("tune_" + axis + "_tpa_strength", defaults.tpaStrength())
		);
	}
}
