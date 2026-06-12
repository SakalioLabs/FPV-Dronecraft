package com.tenicana.dronecraft.sim;

public final class SurfaceNearfieldCalibration {
	private static final double EPSILON = 1.0e-12;
	private static final double ZJU_GROUND_EFFECT_G1_METERS_SQUARED = 0.01804;
	private static final double ZJU_GROUND_EFFECT_G2_METERS_SQUARED = 0.007339;
	private static final Vec3 WALL_DIRECTION_BODY = new Vec3(1.0, 0.0, 0.0);
	private static final Vec3[] ROTOR_SIDE_FLOW_SAMPLE_DIRECTIONS_BODY = {
			new Vec3(1.0, 0.0, 0.0),
			new Vec3(-1.0, 0.0, 0.0),
			new Vec3(0.0, 0.0, 1.0),
			new Vec3(0.0, 0.0, -1.0),
			new Vec3(1.0, 0.0, 1.0).normalized(),
			new Vec3(1.0, 0.0, -1.0).normalized(),
			new Vec3(-1.0, 0.0, 1.0).normalized(),
			new Vec3(-1.0, 0.0, -1.0).normalized()
	};

	public static final String SOURCE_ID = "Surface-Nearfield-Calibration-Packet";
	public static final String CAVEAT = "Ground cushion, ceiling cushion, wall obstruction, and wall side-force are separate near-field paths.";
	public static final int PACKET_METRIC_ROW_COUNT = 708;
	public static final int SOURCE_REFERENCE_COUNT = 4;
	public static final int GROUND_CEILING_SCAN_ROW_COUNT = 120;
	public static final int WALL_RUNTIME_MAPPING_ROW_COUNT = 160;
	public static final int WALL_FORCE_SCAN_ROW_COUNT = 240;
	public static final int ZJU_GROUND_CHECK_ROW_COUNT = 150;
	public static final int ZJU_DRAG_OBSERVATION_ROW_COUNT = 7;

	private SurfaceNearfieldCalibration() {
	}

	public record GroundReferenceSample(
			double clearanceOverRadius,
			double clearanceMeters,
			double currentGroundMultiplier,
			double zjuGroundMultiplier,
			double currentExtraOverZjuExtra,
			double cheesemanGroundMultiplier,
			double currentGroundOverCheeseman,
			double currentCeilingMultiplier,
			double currentCeilingOverGround
	) {
	}

	public record WallRuntimeMapping(
			double clearanceOverRadius,
			double diskSegmentBlockedFraction,
			double runtimeObstruction,
			double affectedRotorThrustMultiplier,
			double twoAffectedVehicleThrustMultiplier,
			double twoAffectedVehicleThrustLossFraction,
			double twoAffectedWallForceOverWeight
	) {
	}

	public record WallForceSample(
			double obstruction,
			double speedMetersPerSecond,
			double speedWashout,
			double wallCushion,
			double forcePerRotorNewtons,
			double twoRotorForceOverWeight,
			double fourRotorForceOverWeight
	) {
	}

	public record ZjuDragObservation(
			double lowHeightMeters,
			double highHeightMeters,
			double predictedDragRatioFromSqrtThrust,
			double measuredDragXLowOverHigh,
			double measuredDragYLowOverHigh,
			double measuredXOverPredicted,
			double measuredYOverPredicted
	) {
	}

	public record SurfaceNearfieldAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int groundCeilingScanRowCount,
			int wallRuntimeMappingRowCount,
			int wallForceScanRowCount,
			int zjuGroundCheckRowCount,
			int zjuDragObservationRowCount,
			GroundReferenceSample halfRadiusGround,
			GroundReferenceSample oneRadiusGround,
			GroundReferenceSample twoRadiusGround,
			GroundReferenceSample fourRadiusGround,
			WallRuntimeMapping tangentWall,
			WallRuntimeMapping quarterRadiusWall,
			WallRuntimeMapping oneRadiusWall,
			WallRuntimeMapping fullObstructionWall,
			WallForceSample fullObstructionHoverSideForce,
			WallForceSample fullObstructionFastSideForce,
			ZjuDragObservation zjuDragObservation
	) {
	}

	public static SurfaceNearfieldAudit audit(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}

		return new SurfaceNearfieldAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				GROUND_CEILING_SCAN_ROW_COUNT,
				WALL_RUNTIME_MAPPING_ROW_COUNT,
				WALL_FORCE_SCAN_ROW_COUNT,
				ZJU_GROUND_CHECK_ROW_COUNT,
				ZJU_DRAG_OBSERVATION_ROW_COUNT,
				groundReference(config, 0.5),
				groundReference(config, 1.0),
				groundReference(config, 2.0),
				groundReference(config, 4.0),
				wallRuntimeMapping(config, 0.0),
				wallRuntimeMapping(config, 0.25),
				wallRuntimeMapping(config, 1.0),
				wallRuntimeMapping(config, 0.0, 1.0),
				wallForceSample(config, 1.0, 0.0),
				wallForceSample(config, 1.0, 12.0),
				new ZjuDragObservation(
						0.10,
						2.0,
						0.9486,
						0.5963,
						0.6179,
						0.5963 / 0.9486,
						0.6179 / 0.9486
				)
		);
	}

	private static GroundReferenceSample groundReference(DroneConfig config, double clearanceOverRadius) {
		RotorSpec rotor = representativeRotor(config);
		double clearanceMeters = rotor.radiusMeters() * clearanceOverRadius;
		double currentGround = DroneEnvironment.groundEffectThrustMultiplier(config, clearanceMeters);
		double zjuGround = zjuGroundEffectMultiplier(clearanceMeters);
		double cheeseman = cheesemanGroundEffectMultiplier(clearanceOverRadius);
		double currentCeiling = DroneEnvironment.ceilingEffectThrustMultiplier(config, clearanceMeters);
		return new GroundReferenceSample(
				clearanceOverRadius,
				clearanceMeters,
				currentGround,
				zjuGround,
				ratio(currentGround - 1.0, zjuGround - 1.0),
				cheeseman,
				ratio(currentGround, cheeseman),
				currentCeiling,
				ratio(currentCeiling, currentGround)
		);
	}

	private static WallRuntimeMapping wallRuntimeMapping(DroneConfig config, double clearanceOverRadius) {
		return wallRuntimeMapping(config, clearanceOverRadius, wallObstruction(config, clearanceOverRadius));
	}

	private static WallRuntimeMapping wallRuntimeMapping(
			DroneConfig config,
			double clearanceOverRadius,
			double obstruction
	) {
		double rotorMultiplier = RotorFlowObstructionModel.thrustMultiplier(obstruction);
		double affectedRotorFraction = Math.min(2.0, config.rotors().size()) / Math.max(1.0, config.rotors().size());
		double vehicleMultiplier = 1.0 - affectedRotorFraction * (1.0 - rotorMultiplier);
		double wallForceOverWeight = wallForceOverWeight(config, obstruction, 0.0, 2);
		return new WallRuntimeMapping(
				clearanceOverRadius,
				RotorFlowObstructionModel.flatWallDiskBlockedFraction(clearanceOverRadius),
				obstruction,
				rotorMultiplier,
				vehicleMultiplier,
				1.0 - vehicleMultiplier,
				wallForceOverWeight
		);
	}

	private static WallForceSample wallForceSample(DroneConfig config, double obstruction, double speedMetersPerSecond) {
		RotorSpec rotor = representativeRotor(config);
		double hoverThrust = hoverThrustPerRotor(config);
		double hoverOmega = hoverOmegaRadiansPerSecond(rotor, hoverThrust);
		Vec3 force = DronePhysics.calculateSteadyRotorWallEffectForce(
				rotor,
				new Vec3(speedMetersPerSecond, 0.0, 0.0),
				hoverOmega,
				hoverThrust,
				obstruction,
				WALL_DIRECTION_BODY
		);
		double speedWashout = 1.0 - MathUtil.clamp(speedMetersPerSecond / 12.0, 0.0, 0.78);
		double thrustFraction = MathUtil.clamp(hoverThrust / rotor.maxThrustNewtons(), 0.0, 1.15);
		double spinRatio = MathUtil.clamp(Math.abs(hoverOmega) / rotor.maxOmegaRadiansPerSecond(), 0.0, 1.0);
		double blockage = Math.pow(MathUtil.clamp(obstruction, 0.0, 1.0), 1.18);
		double forcePerRotor = force.length();
		double weight = config.massKg() * config.gravityMetersPerSecondSquared();
		return new WallForceSample(
				obstruction,
				speedMetersPerSecond,
				speedWashout,
				blockage * spinRatio * (0.35 + 0.65 * thrustFraction) * speedWashout,
				forcePerRotor,
				ratio(forcePerRotor * 2.0, weight),
				ratio(forcePerRotor * 4.0, weight)
		);
	}

	private static double wallForceOverWeight(
			DroneConfig config,
			double obstruction,
			double speedMetersPerSecond,
			int affectedRotorCount
	) {
		return wallForceSample(config, obstruction, speedMetersPerSecond).forcePerRotorNewtons()
				* Math.min(affectedRotorCount, config.rotors().size())
				/ (config.massKg() * config.gravityMetersPerSecondSquared());
	}

	private static double wallObstruction(DroneConfig config, double clearanceOverRadius) {
		RotorSpec rotor = representativeRotor(config);
		double clearanceMeters = rotor.radiusMeters() * Math.max(0.0, clearanceOverRadius);
		RotorFlowObstructionModel.Result result = RotorFlowObstructionModel.fromDirectionalDistances(
				distancesToBodyWalls(clearanceMeters, WALL_DIRECTION_BODY),
				ROTOR_SIDE_FLOW_SAMPLE_DIRECTIONS_BODY,
				sideFlowSampleMaxDistance(rotor),
				rotor.radiusMeters()
		);
		return result.intensity();
	}

	private static double[] distancesToBodyWalls(double clearanceMeters, Vec3... wallDirectionsBody) {
		double[] distances = new double[ROTOR_SIDE_FLOW_SAMPLE_DIRECTIONS_BODY.length];
		double clearance = Math.max(0.0, clearanceMeters);
		for (int i = 0; i < ROTOR_SIDE_FLOW_SAMPLE_DIRECTIONS_BODY.length; i++) {
			Vec3 sampleDirection = ROTOR_SIDE_FLOW_SAMPLE_DIRECTIONS_BODY[i].normalized();
			double distance = Double.POSITIVE_INFINITY;
			for (Vec3 wallDirection : wallDirectionsBody) {
				double projection = sampleDirection.dot(wallDirection.normalized());
				if (projection > 1.0e-9) {
					distance = Math.min(distance, clearance / projection);
				}
			}
			distances[i] = distance;
		}
		return distances;
	}

	private static double sideFlowSampleMaxDistance(RotorSpec rotor) {
		return MathUtil.clamp(rotor.radiusMeters() * 6.5, 0.32, 0.70);
	}

	private static double zjuGroundEffectMultiplier(double clearanceMeters) {
		double clearance = Math.max(0.0, clearanceMeters);
		return 1.0 + ZJU_GROUND_EFFECT_G2_METERS_SQUARED
				/ (clearance * clearance + ZJU_GROUND_EFFECT_G1_METERS_SQUARED);
	}

	private static double cheesemanGroundEffectMultiplier(double clearanceOverRadius) {
		double normalizedClearance = Math.max(0.251, clearanceOverRadius);
		double denominator = 1.0 - 1.0 / (16.0 * normalizedClearance * normalizedClearance);
		return denominator <= EPSILON ? Double.POSITIVE_INFINITY : 1.0 / denominator;
	}

	private static RotorSpec representativeRotor(DroneConfig config) {
		return config.rotors().get(0);
	}

	private static double hoverThrustPerRotor(DroneConfig config) {
		return config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size();
	}

	private static double hoverOmegaRadiansPerSecond(RotorSpec rotor, double hoverThrust) {
		double usableThrust = MathUtil.clamp(hoverThrust, EPSILON, rotor.maxThrustNewtons());
		return Math.sqrt(usableThrust / rotor.thrustCoefficient());
	}

	private static double ratio(double numerator, double denominator) {
		return Math.abs(denominator) <= EPSILON ? 0.0 : numerator / denominator;
	}
}
