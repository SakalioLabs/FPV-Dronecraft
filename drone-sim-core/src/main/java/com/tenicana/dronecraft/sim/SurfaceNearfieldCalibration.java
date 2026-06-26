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
	public static final int PACKET_METRIC_ROW_COUNT = 739;
	public static final int SOURCE_REFERENCE_COUNT = 4;
	public static final int GROUND_CEILING_SCAN_ROW_COUNT = 120;
	public static final int WALL_RUNTIME_MAPPING_ROW_COUNT = 160;
	public static final int WALL_FORCE_SCAN_ROW_COUNT = 240;
	public static final int ZJU_GROUND_CHECK_ROW_COUNT = 150;
	public static final int ZJU_DRAG_OBSERVATION_ROW_COUNT = 7;
	public static final String PARTIAL_SURFACE_LEAD_SOURCE_ID = "Partial-Surface-Effect-Lead-Packet";
	public static final String PARTIAL_SURFACE_LEAD_DOI = "10.2514/1.C036974";
	public static final String PARTIAL_SURFACE_LEAD_CAVEAT =
			"Abstract-level partial ground/ceiling thresholds; raw thrust and power curves still need digitization.";
	public static final int PARTIAL_SURFACE_PUBLIC_BUNDLE_COUNT = 0;
	public static final int PARTIAL_SURFACE_ABSTRACT_CHARACTER_COUNT = 1411;
	public static final double PARTIAL_SURFACE_NEGLIGIBLE_DIAMETER_OVER_PROP_DIAMETER = 0.5;
	public static final double PARTIAL_SURFACE_FULL_LIKE_DIAMETER_OVER_PROP_DIAMETER = 1.0;
	public static final double PARTIAL_SURFACE_NEGLIGIBLE_AREA_OVER_DISK_AREA = 0.25;
	public static final double PARTIAL_SURFACE_CURVE_FIT_RELATIVE_ACCURACY = 0.06;
	public static final double MINECRAFT_BLOCK_WIDTH_METERS = 1.0;
	public static final String JIRS_SURFACE_EFFECT_SOURCE_ID = "JIRS-2024-Surface-Effect-Packet";
	public static final String JIRS_SURFACE_EFFECT_DOI = "10.1007/s10846-024-02155-7";
	public static final String JIRS_SURFACE_EFFECT_SUPPLEMENT_DOI = "10.5281/zenodo.11384638";
	public static final String JIRS_SURFACE_EFFECT_CAVEAT =
			"Raw larger-prop measurements; ground/ceiling thrust uses curve-fit multipliers while wall force/moment remains separate before runtime retuning.";
	public static final int JIRS_SUPPLEMENT_ZIP_SIZE_BYTES = 460164;
	public static final int JIRS_SUPPLEMENT_ZIP_FILE_COUNT = 18;
	public static final int JIRS_SUPPLEMENT_CSV_FILE_COUNT = 9;
	public static final int JIRS_SUPPLEMENT_MAT_FILE_COUNT = 4;
	public static final int JIRS_SUPPLEMENT_PDF_FILE_COUNT = 1;
	public static final int JIRS_NUMERIC_MEASUREMENT_ROW_COUNT = 225;
	public static final int JIRS_UNCERTAINTY_SUMMARY_ROW_COUNT = 40;
	public static final int JIRS_GROUND_SAMPLE_COUNT = 40;
	public static final int JIRS_CEILING_SAMPLE_COUNT = 40;
	public static final int JIRS_WALL_SAMPLE_COUNT = 145;
	public static final double JIRS_GROUND_NEAR_FZ_RATIO_MIN = 0.862999580105;
	public static final double JIRS_GROUND_NEAR_FZ_RATIO_P50 = 1.02584338925;
	public static final double JIRS_GROUND_NEAR_FZ_RATIO_MAX = 1.31490929705;
	public static final double JIRS_GROUND_CLOSEST_FZ_RATIO_P50 = 1.29121270457;
	public static final double JIRS_GROUND_CLOSEST_FZ_RATIO_MAX = 1.31490929705;
	public static final double JIRS_GROUND_CLOSEST_DISTANCE_OVER_RADIUS_MIN = 0.328083989501;
	public static final double JIRS_GROUND_CLOSEST_DISTANCE_OVER_RADIUS_MAX = 0.393700787402;
	public static final double JIRS_CEILING_NEAR_FZ_RATIO_MIN = 0.993352831694;
	public static final double JIRS_CEILING_NEAR_FZ_RATIO_P50 = 1.04620491703;
	public static final double JIRS_CEILING_NEAR_FZ_RATIO_MAX = 1.28879545278;
	public static final double JIRS_CEILING_CLOSEST_FZ_RATIO_P50 = 1.22717806178;
	public static final double JIRS_CEILING_CLOSEST_FZ_RATIO_MAX = 1.28879545278;
	public static final double JIRS_CEILING_CLOSEST_DISTANCE_OVER_RADIUS_MIN = 0.328083989501;
	public static final double JIRS_CEILING_CLOSEST_DISTANCE_OVER_RADIUS_MAX = 0.393700787402;
	public static final double JIRS_GROUND_CEILING_FAR_BASELINE_DISTANCE_CENTIMETERS = 100.0;
	public static final double JIRS_WALL_ABS_FORCE_P50_NEWTONS = 0.09864;
	public static final double JIRS_WALL_ABS_FORCE_MAX_NEWTONS = 0.42777;
	public static final double JIRS_WALL_SIGNED_FORCE_MIN_NEWTONS = -0.42777;
	public static final double JIRS_WALL_SIGNED_FORCE_MAX_NEWTONS = 0.24607;
	public static final double JIRS_WALL_ABS_MOMENT_P50_NEWTON_METERS = 0.027677;
	public static final double JIRS_WALL_ABS_MOMENT_MAX_NEWTON_METERS = 0.11947;
	public static final double JIRS_WALL_DISTANCE_OVER_RADIUS_MIN = 0.964566929134;
	public static final double JIRS_WALL_DISTANCE_OVER_RADIUS_MAX = 3.0;
	public static final String JIRS_WALL_STRONGEST_FORCE_SOURCE = "WallEffect_13_DU2SRI.csv";
	public static final int JIRS_WALL_STRONGEST_FORCE_PWM = 1544;
	public static final double JIRS_WALL_STRONGEST_FORCE_DISTANCE_OVER_RADIUS = 1.75003028468;
	public static final String JIRS_WALL_STRONGEST_MOMENT_SOURCE = "WallEffect_12_txc.csv";
	public static final int JIRS_WALL_STRONGEST_MOMENT_PWM = 1719;
	public static final double JIRS_WALL_STRONGEST_MOMENT_DISTANCE_OVER_RADIUS = 1.0;
	public static final double JIRS_TERRAXCUBE_WALL_FORCE_UNCERTAINTY_P50_NEWTONS = 0.0389109547857;
	public static final double JIRS_DU2SRI_WALL_FORCE_UNCERTAINTY_P50_NEWTONS = 1.1100511642;
	public static final double JIRS_TERRAXCUBE_WALL_MOMENT_UNCERTAINTY_P50_NEWTON_METERS = 0.00687397096237;
	public static final double JIRS_DU2SRI_WALL_MOMENT_UNCERTAINTY_P50_NEWTON_METERS = 0.0560048097306;
	public static final String JIRS_SURFACE_CURVE_FIT_SOURCE_ID = "JIRS-2024-Surface-Curve-Fit-Packet";
	public static final String JIRS_SURFACE_CURVE_FIT_MODEL =
			"multiplier = 1 + A * exp(-k * h_over_R)";
	public static final int JIRS_SURFACE_CURVE_FIT_PACKET_ROW_COUNT = 196;
	public static final int JIRS_SURFACE_CURVE_FIT_INPUT_MEASUREMENT_ROW_COUNT = 225;
	public static final double JIRS_GROUND_CURVE_FIT_A = 0.576141774524;
	public static final double JIRS_GROUND_CURVE_FIT_K = 1.9062;
	public static final double JIRS_GROUND_CURVE_FIT_R2 = 0.980116586669;
	public static final double JIRS_GROUND_CURVE_FIT_RMSE = 0.0159323160145;
	public static final double JIRS_GROUND_CURVE_FIT_MAE = 0.0110665839276;
	public static final int JIRS_GROUND_CURVE_FIT_SAMPLE_COUNT = 10;
	public static final double JIRS_CEILING_CURVE_FIT_A = 0.384690708893;
	public static final double JIRS_CEILING_CURVE_FIT_K = 1.38724;
	public static final double JIRS_CEILING_CURVE_FIT_R2 = 0.951208172755;
	public static final double JIRS_CEILING_CURVE_FIT_RMSE = 0.0194307382179;
	public static final double JIRS_CEILING_CURVE_FIT_MAE = 0.0131115496255;
	public static final int JIRS_CEILING_CURVE_FIT_SAMPLE_COUNT = 10;
	private static final double[] JIRS_CURVE_FIT_RUNTIME_AUDIT_CLEARANCES_OVER_RADIUS = {
			0.5,
			1.0,
			2.0,
			4.0,
			6.0
	};

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

	public record PartialSurfaceGateSample(
			double plateDiameterOverPropDiameter,
			double patchDiameterMeters,
			double circularPatchAreaOverPropDiskArea,
			double gate,
			double fullGroundMultiplier,
			double gatedGroundMultiplier,
			double fullCeilingMultiplier,
			double gatedCeilingMultiplier
	) {
	}

	public record PartialSurfaceLeadAudit(
			String sourceId,
			String doi,
			String caveat,
			int publicBundleCount,
			int abstractCharacterCount,
			boolean mentionsPartialGround,
			boolean mentionsPartialCeiling,
			boolean mentionsCircularAndAnnularPlates,
			boolean mentionsForceBalance,
			boolean mentionsPlateEqualPropDiameter,
			boolean mentionsLessThanHalfPropDiameter,
			boolean mentionsSuperimposedCeiling,
			boolean mentionsCurveFitWithinSixPercent,
			double negligiblePlateDiameterOverPropDiameter,
			double fullLikePlateDiameterOverPropDiameter,
			double negligiblePlateAreaOverDiskArea,
			double curveFitRelativeAccuracy,
			double propellerDiameterMeters,
			double negligiblePatchDiameterMeters,
			double fullLikePatchDiameterMeters,
			double minecraftBlockWidthOverPropDiameter,
			PartialSurfaceGateSample zeroDiameterPatch,
			PartialSurfaceGateSample quarterDiameterPatch,
			PartialSurfaceGateSample halfDiameterPatch,
			PartialSurfaceGateSample threeQuarterDiameterPatch,
			PartialSurfaceGateSample fullDiameterPatch,
			PartialSurfaceGateSample minecraftBlockPatch
	) {
	}

	public record JirsThrustAnchor(
			String effect,
			int sampleCount,
			double nearFzRatioMin,
			double nearFzRatioP50,
			double nearFzRatioMax,
			double closestDistanceOverRadiusMin,
			double closestDistanceOverRadiusMax,
			double closestFzRatioP50,
			double closestFzRatioMax,
			double farBaselineDistanceCentimeters,
			double currentMultiplierAtClosestMin,
			double currentMultiplierAtClosestMax,
			double currentClosestMinOverMeasuredP50,
			double currentClosestMaxOverMeasuredMax
	) {
	}

	public record JirsWallAnchor(
			int sampleCount,
			double distanceOverRadiusMin,
			double distanceOverRadiusMax,
			double absForceP50Newtons,
			double absForceMaxNewtons,
			double signedForceMinNewtons,
			double signedForceMaxNewtons,
			double absMomentP50NewtonMeters,
			double absMomentMaxNewtonMeters,
			String strongestForceSource,
			int strongestForcePwm,
			double strongestForceDistanceOverRadius,
			String strongestMomentSource,
			int strongestMomentPwm,
			double strongestMomentDistanceOverRadius,
			double terraXcubeWallForceUncertaintyP50Newtons,
			double du2sriWallForceUncertaintyP50Newtons,
			double terraXcubeWallMomentUncertaintyP50NewtonMeters,
			double du2sriWallMomentUncertaintyP50NewtonMeters,
			double du2sriOverTerraXcubeWallForceUncertaintyP50,
			double runtimeOneRadiusWallForcePerRotorNewtons,
			double runtimeOneRadiusTwoRotorForceOverWeight,
			double runtimeFullObstructionHoverForcePerRotorNewtons,
			double runtimeFullObstructionTwoRotorForceOverWeight
	) {
	}

	public record JirsCurveFit(
			String effect,
			double a,
			double k,
			double r2,
			double rmse,
			double mae,
			int sampleCount
	) {
	}

	public record JirsCurveFitRuntimeComparison(
			String effect,
			double clearanceOverRadius,
			double runtimeMultiplier,
			double fitMultiplier,
			double runtimeOverFitMultiplier,
			double runtimeExtraOverFitExtra
	) {
	}

	public record JirsSurfaceCurveFitAudit(
			String sourceId,
			String model,
			int packetRowCount,
			int inputMeasurementRowCount,
			JirsCurveFit groundFit,
			JirsCurveFit ceilingFit,
			JirsCurveFitRuntimeComparison[] runtimeComparisons
	) {
		public JirsSurfaceCurveFitAudit {
			runtimeComparisons = runtimeComparisons == null
					? new JirsCurveFitRuntimeComparison[0]
					: runtimeComparisons.clone();
		}

		@Override
		public JirsCurveFitRuntimeComparison[] runtimeComparisons() {
			return runtimeComparisons.clone();
		}
	}

	public record JirsSurfaceEffectAudit(
			String sourceId,
			String doi,
			String supplementDoi,
			String caveat,
			int supplementZipSizeBytes,
			int supplementZipFileCount,
			int supplementCsvFileCount,
			int supplementMatFileCount,
			int supplementPdfFileCount,
			int numericMeasurementRowCount,
			int uncertaintySummaryRowCount,
			JirsSurfaceCurveFitAudit curveFit,
			JirsThrustAnchor ground,
			JirsThrustAnchor ceiling,
			JirsWallAnchor wall
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
			ZjuDragObservation zjuDragObservation,
			JirsSurfaceEffectAudit jirsSurfaceEffectAudit,
			PartialSurfaceLeadAudit partialSurfaceLeadAudit
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
				),
				jirsSurfaceEffectAudit(config),
				partialSurfaceLeadAudit(config)
		);
	}

	private static JirsSurfaceEffectAudit jirsSurfaceEffectAudit(DroneConfig config) {
		return new JirsSurfaceEffectAudit(
				JIRS_SURFACE_EFFECT_SOURCE_ID,
				JIRS_SURFACE_EFFECT_DOI,
				JIRS_SURFACE_EFFECT_SUPPLEMENT_DOI,
				JIRS_SURFACE_EFFECT_CAVEAT,
				JIRS_SUPPLEMENT_ZIP_SIZE_BYTES,
				JIRS_SUPPLEMENT_ZIP_FILE_COUNT,
				JIRS_SUPPLEMENT_CSV_FILE_COUNT,
				JIRS_SUPPLEMENT_MAT_FILE_COUNT,
				JIRS_SUPPLEMENT_PDF_FILE_COUNT,
				JIRS_NUMERIC_MEASUREMENT_ROW_COUNT,
				JIRS_UNCERTAINTY_SUMMARY_ROW_COUNT,
				jirsSurfaceCurveFitAudit(config),
				jirsThrustAnchor(
						config,
						"ground",
						JIRS_GROUND_SAMPLE_COUNT,
						JIRS_GROUND_NEAR_FZ_RATIO_MIN,
						JIRS_GROUND_NEAR_FZ_RATIO_P50,
						JIRS_GROUND_NEAR_FZ_RATIO_MAX,
						JIRS_GROUND_CLOSEST_DISTANCE_OVER_RADIUS_MIN,
						JIRS_GROUND_CLOSEST_DISTANCE_OVER_RADIUS_MAX,
						JIRS_GROUND_CLOSEST_FZ_RATIO_P50,
						JIRS_GROUND_CLOSEST_FZ_RATIO_MAX
				),
				jirsThrustAnchor(
						config,
						"ceiling",
						JIRS_CEILING_SAMPLE_COUNT,
						JIRS_CEILING_NEAR_FZ_RATIO_MIN,
						JIRS_CEILING_NEAR_FZ_RATIO_P50,
						JIRS_CEILING_NEAR_FZ_RATIO_MAX,
						JIRS_CEILING_CLOSEST_DISTANCE_OVER_RADIUS_MIN,
						JIRS_CEILING_CLOSEST_DISTANCE_OVER_RADIUS_MAX,
						JIRS_CEILING_CLOSEST_FZ_RATIO_P50,
						JIRS_CEILING_CLOSEST_FZ_RATIO_MAX
				),
				jirsWallAnchor(config)
		);
	}

	public static double jirsGroundCurveFitExtraLift(double clearanceOverRadius) {
		return jirsCurveFitExtraLift(clearanceOverRadius, JIRS_GROUND_CURVE_FIT_A, JIRS_GROUND_CURVE_FIT_K);
	}

	public static double jirsGroundCurveFitMultiplier(double clearanceOverRadius) {
		return 1.0 + jirsGroundCurveFitExtraLift(clearanceOverRadius);
	}

	public static double jirsCeilingCurveFitExtraLift(double clearanceOverRadius) {
		return jirsCurveFitExtraLift(clearanceOverRadius, JIRS_CEILING_CURVE_FIT_A, JIRS_CEILING_CURVE_FIT_K);
	}

	public static double jirsCeilingCurveFitMultiplier(double clearanceOverRadius) {
		return 1.0 + jirsCeilingCurveFitExtraLift(clearanceOverRadius);
	}

	private static double jirsCurveFitExtraLift(double clearanceOverRadius, double a, double k) {
		if (!Double.isFinite(clearanceOverRadius)) {
			return 0.0;
		}
		double normalizedClearance = Math.max(0.0, clearanceOverRadius);
		return Math.max(0.0, a * Math.exp(-k * normalizedClearance));
	}

	private static JirsSurfaceCurveFitAudit jirsSurfaceCurveFitAudit(DroneConfig config) {
		return new JirsSurfaceCurveFitAudit(
				JIRS_SURFACE_CURVE_FIT_SOURCE_ID,
				JIRS_SURFACE_CURVE_FIT_MODEL,
				JIRS_SURFACE_CURVE_FIT_PACKET_ROW_COUNT,
				JIRS_SURFACE_CURVE_FIT_INPUT_MEASUREMENT_ROW_COUNT,
				new JirsCurveFit(
						"ground",
						JIRS_GROUND_CURVE_FIT_A,
						JIRS_GROUND_CURVE_FIT_K,
						JIRS_GROUND_CURVE_FIT_R2,
						JIRS_GROUND_CURVE_FIT_RMSE,
						JIRS_GROUND_CURVE_FIT_MAE,
						JIRS_GROUND_CURVE_FIT_SAMPLE_COUNT
				),
				new JirsCurveFit(
						"ceiling",
						JIRS_CEILING_CURVE_FIT_A,
						JIRS_CEILING_CURVE_FIT_K,
						JIRS_CEILING_CURVE_FIT_R2,
						JIRS_CEILING_CURVE_FIT_RMSE,
						JIRS_CEILING_CURVE_FIT_MAE,
						JIRS_CEILING_CURVE_FIT_SAMPLE_COUNT
				),
				jirsCurveFitRuntimeComparisons(config)
		);
	}

	private static JirsCurveFitRuntimeComparison[] jirsCurveFitRuntimeComparisons(DroneConfig config) {
		JirsCurveFitRuntimeComparison[] comparisons =
				new JirsCurveFitRuntimeComparison[JIRS_CURVE_FIT_RUNTIME_AUDIT_CLEARANCES_OVER_RADIUS.length * 2];
		int index = 0;
		for (double clearanceOverRadius : JIRS_CURVE_FIT_RUNTIME_AUDIT_CLEARANCES_OVER_RADIUS) {
			comparisons[index++] = jirsCurveFitRuntimeComparison(config, "ground", clearanceOverRadius);
		}
		for (double clearanceOverRadius : JIRS_CURVE_FIT_RUNTIME_AUDIT_CLEARANCES_OVER_RADIUS) {
			comparisons[index++] = jirsCurveFitRuntimeComparison(config, "ceiling", clearanceOverRadius);
		}
		return comparisons;
	}

	private static JirsCurveFitRuntimeComparison jirsCurveFitRuntimeComparison(
			DroneConfig config,
			String effect,
			double clearanceOverRadius
	) {
		RotorSpec rotor = representativeRotor(config);
		double clearanceMeters = rotor.radiusMeters() * clearanceOverRadius;
		boolean ceiling = "ceiling".equals(effect);
		double runtime = jirsSurfaceMultiplier(config, clearanceMeters, ceiling);
		double fit = ceiling
				? jirsCeilingCurveFitMultiplier(clearanceOverRadius)
				: jirsGroundCurveFitMultiplier(clearanceOverRadius);
		return new JirsCurveFitRuntimeComparison(
				effect,
				clearanceOverRadius,
				runtime,
				fit,
				ratio(runtime, fit),
				ratio(runtime - 1.0, fit - 1.0)
		);
	}

	private static JirsThrustAnchor jirsThrustAnchor(
			DroneConfig config,
			String effect,
			int sampleCount,
			double nearFzRatioMin,
			double nearFzRatioP50,
			double nearFzRatioMax,
			double closestDistanceOverRadiusMin,
			double closestDistanceOverRadiusMax,
			double closestFzRatioP50,
			double closestFzRatioMax
	) {
		RotorSpec rotor = representativeRotor(config);
		double closestMinClearanceMeters = closestDistanceOverRadiusMin * rotor.radiusMeters();
		double closestMaxClearanceMeters = closestDistanceOverRadiusMax * rotor.radiusMeters();
		boolean ceiling = "ceiling".equals(effect);
		double currentAtClosestMin = jirsSurfaceMultiplier(config, closestMinClearanceMeters, ceiling);
		double currentAtClosestMax = jirsSurfaceMultiplier(config, closestMaxClearanceMeters, ceiling);
		return new JirsThrustAnchor(
				effect,
				sampleCount,
				nearFzRatioMin,
				nearFzRatioP50,
				nearFzRatioMax,
				closestDistanceOverRadiusMin,
				closestDistanceOverRadiusMax,
				closestFzRatioP50,
				closestFzRatioMax,
				JIRS_GROUND_CEILING_FAR_BASELINE_DISTANCE_CENTIMETERS,
				currentAtClosestMin,
				currentAtClosestMax,
				ratio(currentAtClosestMin, closestFzRatioP50),
				ratio(currentAtClosestMax, closestFzRatioMax)
		);
	}

	private static double jirsSurfaceMultiplier(DroneConfig config, double clearanceMeters, boolean ceiling) {
		if (ceiling) {
			return DroneEnvironment.ceilingEffectThrustMultiplier(config, clearanceMeters);
		}
		return DroneEnvironment.groundEffectThrustMultiplier(config, clearanceMeters);
	}

	private static JirsWallAnchor jirsWallAnchor(DroneConfig config) {
		WallForceSample oneRadiusForce = wallForceSample(config, wallObstruction(config, 1.0), 0.0);
		WallRuntimeMapping oneRadiusMapping = wallRuntimeMapping(config, 1.0);
		WallForceSample fullObstructionHover = wallForceSample(config, 1.0, 0.0);
		WallRuntimeMapping fullObstructionMapping = wallRuntimeMapping(config, 0.0, 1.0);
		return new JirsWallAnchor(
				JIRS_WALL_SAMPLE_COUNT,
				JIRS_WALL_DISTANCE_OVER_RADIUS_MIN,
				JIRS_WALL_DISTANCE_OVER_RADIUS_MAX,
				JIRS_WALL_ABS_FORCE_P50_NEWTONS,
				JIRS_WALL_ABS_FORCE_MAX_NEWTONS,
				JIRS_WALL_SIGNED_FORCE_MIN_NEWTONS,
				JIRS_WALL_SIGNED_FORCE_MAX_NEWTONS,
				JIRS_WALL_ABS_MOMENT_P50_NEWTON_METERS,
				JIRS_WALL_ABS_MOMENT_MAX_NEWTON_METERS,
				JIRS_WALL_STRONGEST_FORCE_SOURCE,
				JIRS_WALL_STRONGEST_FORCE_PWM,
				JIRS_WALL_STRONGEST_FORCE_DISTANCE_OVER_RADIUS,
				JIRS_WALL_STRONGEST_MOMENT_SOURCE,
				JIRS_WALL_STRONGEST_MOMENT_PWM,
				JIRS_WALL_STRONGEST_MOMENT_DISTANCE_OVER_RADIUS,
				JIRS_TERRAXCUBE_WALL_FORCE_UNCERTAINTY_P50_NEWTONS,
				JIRS_DU2SRI_WALL_FORCE_UNCERTAINTY_P50_NEWTONS,
				JIRS_TERRAXCUBE_WALL_MOMENT_UNCERTAINTY_P50_NEWTON_METERS,
				JIRS_DU2SRI_WALL_MOMENT_UNCERTAINTY_P50_NEWTON_METERS,
				ratio(JIRS_DU2SRI_WALL_FORCE_UNCERTAINTY_P50_NEWTONS,
						JIRS_TERRAXCUBE_WALL_FORCE_UNCERTAINTY_P50_NEWTONS),
				oneRadiusForce.forcePerRotorNewtons(),
				oneRadiusMapping.twoAffectedWallForceOverWeight(),
				fullObstructionHover.forcePerRotorNewtons(),
				fullObstructionMapping.twoAffectedWallForceOverWeight()
		);
	}

	private static PartialSurfaceLeadAudit partialSurfaceLeadAudit(DroneConfig config) {
		RotorSpec rotor = representativeRotor(config);
		double propellerDiameterMeters = rotor.radiusMeters() * 2.0;
		return new PartialSurfaceLeadAudit(
				PARTIAL_SURFACE_LEAD_SOURCE_ID,
				PARTIAL_SURFACE_LEAD_DOI,
				PARTIAL_SURFACE_LEAD_CAVEAT,
				PARTIAL_SURFACE_PUBLIC_BUNDLE_COUNT,
				PARTIAL_SURFACE_ABSTRACT_CHARACTER_COUNT,
				true,
				true,
				true,
				true,
				true,
				true,
				true,
				true,
				PARTIAL_SURFACE_NEGLIGIBLE_DIAMETER_OVER_PROP_DIAMETER,
				PARTIAL_SURFACE_FULL_LIKE_DIAMETER_OVER_PROP_DIAMETER,
				PARTIAL_SURFACE_NEGLIGIBLE_AREA_OVER_DISK_AREA,
				PARTIAL_SURFACE_CURVE_FIT_RELATIVE_ACCURACY,
				propellerDiameterMeters,
				propellerDiameterMeters * PARTIAL_SURFACE_NEGLIGIBLE_DIAMETER_OVER_PROP_DIAMETER,
				propellerDiameterMeters * PARTIAL_SURFACE_FULL_LIKE_DIAMETER_OVER_PROP_DIAMETER,
				MINECRAFT_BLOCK_WIDTH_METERS / propellerDiameterMeters,
				partialSurfaceGateSample(config, 0.0),
				partialSurfaceGateSample(config, 0.25),
				partialSurfaceGateSample(config, 0.5),
				partialSurfaceGateSample(config, 0.75),
				partialSurfaceGateSample(config, 1.0),
				partialSurfaceGateSampleForPatchDiameter(config, MINECRAFT_BLOCK_WIDTH_METERS)
		);
	}

	private static PartialSurfaceGateSample partialSurfaceGateSample(
			DroneConfig config,
			double plateDiameterOverPropDiameter
	) {
		RotorSpec rotor = representativeRotor(config);
		double patchDiameterMeters = rotor.radiusMeters() * 2.0 * plateDiameterOverPropDiameter;
		return partialSurfaceGateSampleForPatchDiameter(config, patchDiameterMeters);
	}

	private static PartialSurfaceGateSample partialSurfaceGateSampleForPatchDiameter(
			DroneConfig config,
			double patchDiameterMeters
	) {
		RotorSpec rotor = representativeRotor(config);
		double clearanceMeters = rotor.radiusMeters();
		double plateDiameterOverPropDiameter =
				DroneEnvironment.partialSurfaceDiameterOverPropDiameter(config, patchDiameterMeters);
		double fullGroundMultiplier = DroneEnvironment.groundEffectThrustMultiplier(config, clearanceMeters);
		double fullCeilingMultiplier = DroneEnvironment.ceilingEffectThrustMultiplier(config, clearanceMeters);
		return new PartialSurfaceGateSample(
				plateDiameterOverPropDiameter,
				patchDiameterMeters,
				plateDiameterOverPropDiameter * plateDiameterOverPropDiameter,
				DroneEnvironment.partialSurfaceEffectGate(config, patchDiameterMeters),
				fullGroundMultiplier,
				DroneEnvironment.partialGroundEffectThrustMultiplier(config, clearanceMeters, patchDiameterMeters),
				fullCeilingMultiplier,
				DroneEnvironment.partialCeilingEffectThrustMultiplier(config, clearanceMeters, patchDiameterMeters)
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
