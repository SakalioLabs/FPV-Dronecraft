package com.tenicana.dronecraft.sim;

import java.util.List;

public final class CoaxialAllocationCalibration {
	private static final double EPSILON = 1.0e-12;

	public static final String SOURCE_ID = "Coaxial-Allocation-Calibration-Packet";
	public static final String CAVEAT = "allocation-not-thrust-loss-fit";
	public static final String PLATFORM = "New Dexterity Coaxial Benchmarking Platform";
	public static final double LOAD_BIAS_MAX = 0.115;
	public static final int PACKET_ROW_COUNT = 835;
	public static final int ELEVEN_IN_ZD07_ALLOCATION_ROW_COUNT = 36;
	public static final int COMMAND_ENVELOPE_60_PERCENT_ROW_COUNT = 40;
	public static final int CURRENT_GEOMETRY_ROW_COUNT = 4;
	public static final int CURRENT_WAKE_SCAN_ROW_COUNT = 288;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int MULTI_OPTIMAL_60_PERCENT_ROW_COUNT = 180;
	public static final int MULTI_SHAPE_60_PERCENT_ROW_COUNT = 40;
	public static final int REFERENCE_ALLOCATION_CLAIM_ROW_COUNT = 1;
	public static final int REFERENCE_PLATFORM_ROW_COUNT = 5;
	public static final int REFERENCE_REGION_ROW_COUNT = 12;
	public static final int REFERENCE_SPACING_SCAN_ROW_COUNT = 5;
	public static final int RUNTIME_MODEL_POINT_ROW_COUNT = 100;
	public static final int RUNTIME_RAW_GROUP_60_PERCENT_ROW_COUNT = 44;
	public static final int SUMMARY_ROW_COUNT = 31;
	public static final int SURFACE_FIT_SUMMARY_ROW_COUNT = 48;

	public static final double CURRENT_PACKET_SEPARATION_OVER_DIAMETER = 0.72;
	public static final double CURRENT_PACKET_RADIUS_METERS = 0.115;
	public static final double CURRENT_PACKET_UPPER_LOWER_SEPARATION_METERS = 0.1656;
	public static final double CURRENT_PACKET_SEPARATION_OVER_RADIUS = 1.44;
	public static final double HOVER_WAKE_LOSS_ZD072_PERCENT = 6.99866242649;
	public static final double MAX_WAKE_LOSS_ZD072_PERCENT = 19.0310654545;
	public static final double HOVER_WAKE_LOSS_ZD055_MINUS_ZD072_PERCENT = 0.120965473507;
	public static final double MAX_WAKE_LOSS_ZD055_MINUS_ZD072_PERCENT = 0.328934545455;
	public static final double ALLOCATION_CLAIM_MECHANICAL_GAIN_PERCENT = 11.0;

	private static final double[] COMMAND_MAP_LOAD_FRACTIONS = {0.35, 0.45, 0.60, 0.75, 0.85};
	private static final double[] COMMAND_MAP_RATIOS = {
			1.1434392831646552,
			1.217068040313562,
			1.3275111760369225,
			1.2372418948199526,
			1.1770623740086394
	};
	private static final double[] COMMAND_MAP_MECHANICAL_GAIN_PCT = {
			5.194971593971521,
			4.878985379626062,
			4.405006058107876,
			5.261233603804847,
			5.832051967602827
	};
	private static final double[] COMMAND_MAP_ELECTRICAL_GAIN_PCT = {
			5.246829881486663,
			4.2849996935626855,
			2.842254411676719,
			3.4679138420725897,
			3.8850201290031703
	};
	private static final double[] COMMAND_MAP_RATIO_P10 = {
			1.1434392831646552,
			1.17311275505984,
			1.2176229629026172,
			1.189005930708007,
			1.1034549624034093
	};
	private static final double[] COMMAND_MAP_RATIO_P90 = {
			1.1434392831646552,
			1.26486568658725,
			1.447005291721142,
			1.269025278106009,
			1.1770623740086394
	};
	private static final double[] COMMAND_MAP_MECHANICAL_GAIN_P10_PCT = {
			4.134394291807541,
			3.8432189016912126,
			2.43665922622438,
			4.556839395685327,
			5.684992772966581
	};
	private static final double[] COMMAND_MAP_MECHANICAL_GAIN_P90_PCT = {
			7.863154349719293,
			6.909930826213276,
			6.140265567387578,
			6.376766661995988,
			7.254549722087849
	};
	private static final double[] COMMAND_MAP_ELECTRICAL_GAIN_P10_PCT = {
			5.4329209552945645,
			3.777622247854227,
			-1.0620783466494865,
			2.5941574632063116,
			4.211596163865798
	};
	private static final double[] COMMAND_MAP_ELECTRICAL_GAIN_P90_PCT = {
			8.512947872971823,
			5.542628189182429,
			3.2956922256159595,
			4.947395195300065,
			6.487214643375651
	};
	private static final double[] COMMAND_MAP_SPACING_RATIOS = {0.25, 0.40, 0.55, 0.70, 0.72, 0.85, 1.00};
	private static final double[][] COMMAND_MAP_RATIO_SURFACE = {
			{1.1434392831646552, 1.1362417593463927, 1.1254454736189989, 1.1564156138527832, 1.1770623740086394},
			{1.1434392831646552, 1.1301153561038886, 1.1101294655127385, 1.1502892106102791, 1.1770623740086394},
			{1.1434392831646552, 1.1362417593463927, 1.1254454736189989, 1.1564156138527832, 1.1770623740086394},
			{1.1434392831646552, 1.2295028527700498, 1.3585982071781415, 1.2496767072764403, 1.1770623740086394},
			COMMAND_MAP_RATIOS,
			{1.1434392831646552, 1.1362417593463927, 1.1254454736189989, 1.1564156138527832, 1.1770623740086394},
			{1.1434392831646552, 1.1362417593463927, 1.1254454736189989, 1.1564156138527832, 1.1770623740086394}
	};
	private static final double[][] COMMAND_MAP_MECHANICAL_GAIN_SURFACE_PCT = {
			{4.281545947786891, 4.311961612637245, 4.357585109912776, 5.394673344985956, 6.08606550170141},
			{8.783690531755518, 8.184992614823662, 7.286945739425876, 7.006953002652967, 6.820291178137694},
			{5.661425165994904, 4.431194042918278, 2.585847358303339, 5.10719968937576, 6.788101243424038},
			{5.4940007043495775, 4.872558251056622, 3.9403945711171895, 4.977193238284596, 5.668392349729534},
			COMMAND_MAP_MECHANICAL_GAIN_PCT,
			{3.251282376514153, 4.920761715327427, 7.424980723547336, 7.107495979686473, 6.895839483779231},
			{6.550257104265023, 5.834208300026025, 4.760135093667528, 6.619558702895079, 7.859174442380112}
	};
	private static final double[][] COMMAND_MAP_ELECTRICAL_GAIN_SURFACE_PCT = {
			{5.738746490731184, 4.67109120405237, 3.0696082740341524, 2.9609333593433895, 2.8884834162162143},
			{5.579411589457717, 4.092416302743143, 1.8619233726712814, 3.3934289658893846, 4.414432694701453},
			{5.121455096170857, 4.331030961688942, 3.145394759966069, 3.0113372698400243, 2.9219656097559943},
			{5.446521036517504, 4.322803383457892, 2.6372269038684726, 3.2665743224560684, 3.6861392681811322},
			COMMAND_MAP_ELECTRICAL_GAIN_PCT,
			{3.948837373786196, 4.039275709243846, 4.174933212430321, 4.776620719579978, 5.177745724346416},
			{6.146307869516732, 5.538135435201474, 4.625876783728589, 4.683706389432736, 4.722259459902167}
	};

	private CoaxialAllocationCalibration() {
	}

	public record RowTypeCounts(
			int totalRowCount,
			int elevenInZOverD07AllocationRowCount,
			int commandEnvelope60PercentRowCount,
			int currentGeometryRowCount,
			int currentWakeScanRowCount,
			int methodRowCount,
			int multiOptimal60PercentRowCount,
			int multiShape60PercentRowCount,
			int referenceAllocationClaimRowCount,
			int referencePlatformRowCount,
			int referenceRegionRowCount,
			int referenceSpacingScanRowCount,
			int runtimeModelPointRowCount,
			int runtimeRawGroup60PercentRowCount,
			int summaryRowCount,
			int surfaceFitSummaryRowCount
	) {
	}

	public record ReferencePlatformAudit(
			String platform,
			double thrustLoadCellCapacityKgf,
			double thrustPrecisionGf,
			double torqueCapacityNewtonMeters,
			double torquePrecisionNewtonMeters,
			String measuredChannels
	) {
	}

	public record ReferenceSpacingAudit(
			double zOverDMin,
			double zOverDMax,
			int spacingPointCount,
			int commandGridPointsPerSpacing,
			int pointsPerRotorSet
	) {
	}

	public record ReferenceRegionAudit(
			double localEfficiencyMax1ZOverDMin,
			double localEfficiencyMax1ZOverDMax,
			double localEfficiencyMinimumZOverDCenter,
			double localEfficiencyMax2ZOverDMin,
			double localEfficiencyMax2ZOverDMax,
			boolean currentSpacingInSecondMaxRegion
	) {
	}

	public record CurrentGeometryAudit(
			int rotorCount,
			int coaxialPairCount,
			double radiusMeters,
			double upperLowerSeparationMeters,
			double separationOverRadius,
			double separationOverDiameter,
			boolean matchesPacketGeometry
	) {
	}

	public record WakeLossAudit(
			double hoverWakeLossZOverD072Percent,
			double maxWakeLossZOverD072Percent,
			double hoverWakeLossZOverD055MinusZOverD072Percent,
			double maxWakeLossZOverD055MinusZOverD072Percent
	) {
	}

	public record RuntimeAllocationAudit(
			double lookupZOverD,
			double targetLoadFraction,
			double referenceTargetTotalThrustGrams,
			double recommendedPwmRatioRightOverLeft,
			double recommendedLeftPwmScaleVsEqual,
			double recommendedRightPwmScaleVsEqual,
			double mechanicalGainOverEqualPercent,
			double electricalGainOverEqualPercent,
			double loadBiasTarget,
			double allocationUncertaintyPercent,
			double currentCoaxialDiameterInches,
			double nearestReferencePropDiameterInches,
			double allGroupRatioP10,
			double allGroupRatioMedian,
			double allGroupRatioP90,
			double allGroupMechanicalGainP10Percent,
			double allGroupMechanicalGainMedianPercent,
			double allGroupMechanicalGainP90Percent,
			double allGroupElectricalGainP10Percent,
			double allGroupElectricalGainMedianPercent,
			double allGroupElectricalGainP90Percent,
			int groupSampleCount
	) {
	}

	public record BenchmarkAllocationAudit(
			double elevenInZOverD070RatioAt1000g,
			double elevenInZOverD070MechanicalGainAt1000gPercent,
			double elevenInZOverD070RatioAt1500g,
			double elevenInZOverD070MechanicalGainAt1500gPercent,
			double allocationClaimMechanicalGainPercent
	) {
	}

	public record StrongestAllocationAudit(
			String strongestMultiOptimal60PercentName,
			double strongestMultiOptimal60PercentMechanicalGainPercent,
			double strongestMultiOptimal60PercentPwmRatioRightOverLeft,
			String strongestCommandEnvelope60PercentName,
			double strongestCommandEnvelope60PercentMechanicalGainPercent,
			double strongestCommandEnvelope60PercentPwmRatioRightOverLeft,
			double strongestCommandEnvelope60PercentElectricalGainPercent
	) {
	}

	public record SurfaceFitAudit(
			double thrustMedianCvRmseOverRangePercent,
			double thrustMedianCvR2,
			double mechanicalPowerMedianCvRmseOverRangePercent,
			double mechanicalPowerMedianCvR2
	) {
	}

	public record CoaxialAllocationAudit(
			String sourceId,
			String caveat,
			RowTypeCounts rowTypeCounts,
			ReferencePlatformAudit referencePlatform,
			ReferenceSpacingAudit referenceSpacing,
			ReferenceRegionAudit referenceRegion,
			CurrentGeometryAudit currentGeometry,
			WakeLossAudit wakeLoss,
			RuntimeAllocationAudit runtimeAllocation,
			BenchmarkAllocationAudit benchmarkAllocation,
			StrongestAllocationAudit strongestAllocation,
			SurfaceFitAudit surfaceFit
	) {
	}

	public static CoaxialAllocationAudit audit(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}

		return new CoaxialAllocationAudit(
				SOURCE_ID,
				CAVEAT,
				rowTypeCounts(),
				referencePlatform(),
				referenceSpacing(),
				referenceRegion(),
				currentGeometry(config),
				wakeLoss(),
				runtimeAllocation(),
				benchmarkAllocation(),
				strongestAllocation(),
				surfaceFit()
		);
	}

	public static double commandMapLoadBias(double spacingRatio, double loadFraction) {
		double ratio = commandMapAllocationRatio(spacingRatio, loadFraction);
		if (ratio <= 1.0 + EPSILON) {
			return 0.0;
		}

		return MathUtil.clamp((ratio - 1.0) / (ratio + 1.0), 0.0, LOAD_BIAS_MAX);
	}

	public static double commandMapAllocationRatio(double spacingRatio, double loadFraction) {
		double activation = commandMapActivation(spacingRatio, loadFraction);
		if (activation <= 1.0e-6) {
			return 1.0;
		}

		double lookupRatio = interpolateCommandMapSurface(
				COMMAND_MAP_RATIO_SURFACE,
				spacingRatio,
				loadFraction
		);
		return MathUtil.clamp(1.0 + (lookupRatio - 1.0) * activation, 1.0, 2.0);
	}

	public static double commandMapMechanicalGainPercent(double spacingRatio, double loadFraction) {
		double activation = commandMapActivation(spacingRatio, loadFraction);
		if (activation <= 1.0e-6) {
			return 0.0;
		}
		return interpolateCommandMapSurface(
				COMMAND_MAP_MECHANICAL_GAIN_SURFACE_PCT,
				spacingRatio,
				loadFraction
		) * activation;
	}

	public static double commandMapElectricalGainPercent(double spacingRatio, double loadFraction) {
		double activation = commandMapActivation(spacingRatio, loadFraction);
		if (activation <= 1.0e-6) {
			return 0.0;
		}
		return interpolateCommandMapSurface(
				COMMAND_MAP_ELECTRICAL_GAIN_SURFACE_PCT,
				spacingRatio,
				loadFraction
		) * activation;
	}

	public static double commandMapAllocationUncertaintyPercent(double spacingRatio, double loadFraction) {
		double activation = commandMapActivation(spacingRatio, loadFraction);
		if (activation <= 1.0e-6) {
			return 0.0;
		}

		double ratioP10 = interpolateCommandMap(COMMAND_MAP_RATIO_P10, loadFraction);
		double ratioP90 = interpolateCommandMap(COMMAND_MAP_RATIO_P90, loadFraction);
		double mechanicalP10 = interpolateCommandMap(COMMAND_MAP_MECHANICAL_GAIN_P10_PCT, loadFraction);
		double mechanicalP90 = interpolateCommandMap(COMMAND_MAP_MECHANICAL_GAIN_P90_PCT, loadFraction);
		double electricalP10 = interpolateCommandMap(COMMAND_MAP_ELECTRICAL_GAIN_P10_PCT, loadFraction);
		double electricalP90 = interpolateCommandMap(COMMAND_MAP_ELECTRICAL_GAIN_P90_PCT, loadFraction);
		double ratioMidpoint = Math.max(1.0, 0.5 * (ratioP10 + ratioP90));
		double ratioUncertaintyPercent = 100.0 * 0.5 * Math.max(0.0, ratioP90 - ratioP10) / ratioMidpoint;
		double mechanicalUncertaintyPercent = 0.5 * Math.max(0.0, mechanicalP90 - mechanicalP10);
		double electricalUncertaintyPercent = 0.5 * Math.max(0.0, electricalP90 - electricalP10);
		return MathUtil.clamp(
				Math.max(ratioUncertaintyPercent, Math.max(mechanicalUncertaintyPercent, electricalUncertaintyPercent))
						* activation,
				0.0,
				25.0
		);
	}

	public static double commandMapActivation(double spacingRatio, double loadFraction) {
		double spacingWindow = smoothStep(0.18, 0.25, spacingRatio)
				* (1.0 - smoothStep(1.05, 1.32, spacingRatio));
		if (spacingWindow <= 1.0e-6) {
			return 0.0;
		}

		double boundedLoadFraction = MathUtil.clamp(loadFraction, 0.0, 1.0);
		double activeLoad = smoothStep(0.18, 0.32, boundedLoadFraction)
				* (1.0 - smoothStep(0.92, 1.0, boundedLoadFraction));
		return MathUtil.clamp(spacingWindow * activeLoad, 0.0, 1.0);
	}

	private static RowTypeCounts rowTypeCounts() {
		return new RowTypeCounts(
				PACKET_ROW_COUNT,
				ELEVEN_IN_ZD07_ALLOCATION_ROW_COUNT,
				COMMAND_ENVELOPE_60_PERCENT_ROW_COUNT,
				CURRENT_GEOMETRY_ROW_COUNT,
				CURRENT_WAKE_SCAN_ROW_COUNT,
				METHOD_ROW_COUNT,
				MULTI_OPTIMAL_60_PERCENT_ROW_COUNT,
				MULTI_SHAPE_60_PERCENT_ROW_COUNT,
				REFERENCE_ALLOCATION_CLAIM_ROW_COUNT,
				REFERENCE_PLATFORM_ROW_COUNT,
				REFERENCE_REGION_ROW_COUNT,
				REFERENCE_SPACING_SCAN_ROW_COUNT,
				RUNTIME_MODEL_POINT_ROW_COUNT,
				RUNTIME_RAW_GROUP_60_PERCENT_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				SURFACE_FIT_SUMMARY_ROW_COUNT
		);
	}

	private static ReferencePlatformAudit referencePlatform() {
		return new ReferencePlatformAudit(
				PLATFORM,
				5.0,
				2.5,
				1.4715,
				0.00073575,
				"thrust, torque, RPM, voltage, current"
		);
	}

	private static ReferenceSpacingAudit referenceSpacing() {
		return new ReferenceSpacingAudit(0.1, 1.0, 7, 100, 700);
	}

	private static ReferenceRegionAudit referenceRegion() {
		return new ReferenceRegionAudit(
				0.25,
				0.40,
				0.55,
				0.70,
				0.85,
				CURRENT_PACKET_SEPARATION_OVER_DIAMETER >= 0.70
						&& CURRENT_PACKET_SEPARATION_OVER_DIAMETER <= 0.85
		);
	}

	private static CurrentGeometryAudit currentGeometry(DroneConfig config) {
		List<RotorSpec> rotors = config.rotors();
		int pairCount = 0;
		double radiusSum = 0.0;
		double separationSum = 0.0;
		for (int i = 0; i < rotors.size(); i++) {
			RotorSpec first = rotors.get(i);
			for (int j = i + 1; j < rotors.size(); j++) {
				RotorSpec second = rotors.get(j);
				if (sameHorizontalPosition(first, second)) {
					double separation = Math.abs(first.positionBodyMeters().y() - second.positionBodyMeters().y());
					if (separation > EPSILON) {
						pairCount++;
						radiusSum += 0.5 * (first.radiusMeters() + second.radiusMeters());
						separationSum += separation;
					}
				}
			}
		}

		double radiusMeters = pairCount > 0 ? radiusSum / pairCount : averageRotorRadius(rotors);
		double separationMeters = pairCount > 0 ? separationSum / pairCount : 0.0;
		double separationOverRadius = ratio(separationMeters, radiusMeters);
		double separationOverDiameter = ratio(separationMeters, radiusMeters * 2.0);
		boolean matchesPacket = Math.abs(radiusMeters - CURRENT_PACKET_RADIUS_METERS) < 1.0e-9
				&& Math.abs(separationMeters - CURRENT_PACKET_UPPER_LOWER_SEPARATION_METERS) < 1.0e-9
				&& Math.abs(separationOverDiameter - CURRENT_PACKET_SEPARATION_OVER_DIAMETER) < 1.0e-9;
		return new CurrentGeometryAudit(
				rotors.size(),
				pairCount,
				radiusMeters,
				separationMeters,
				separationOverRadius,
				separationOverDiameter,
				matchesPacket
		);
	}

	private static WakeLossAudit wakeLoss() {
		return new WakeLossAudit(
				HOVER_WAKE_LOSS_ZD072_PERCENT,
				MAX_WAKE_LOSS_ZD072_PERCENT,
				HOVER_WAKE_LOSS_ZD055_MINUS_ZD072_PERCENT,
				MAX_WAKE_LOSS_ZD055_MINUS_ZD072_PERCENT
		);
	}

	private static RuntimeAllocationAudit runtimeAllocation() {
		return new RuntimeAllocationAudit(
				CURRENT_PACKET_SEPARATION_OVER_DIAMETER,
				0.60,
				1074.55702959,
				1.32751117604,
				0.827324794979,
				1.09514567019,
				4.40500605811,
				2.84225441168,
				commandMapLoadBias(CURRENT_PACKET_SEPARATION_OVER_DIAMETER, 0.60),
				commandMapAllocationUncertaintyPercent(CURRENT_PACKET_SEPARATION_OVER_DIAMETER, 0.60),
				9.05511811024,
				11.0,
				1.2176229629,
				1.38426721762,
				1.44700529172,
				2.43665922622,
				3.69175588554,
				6.14026556739,
				-1.06207834665,
				1.88071159644,
				3.29569222562,
				4
		);
	}

	private static BenchmarkAllocationAudit benchmarkAllocation() {
		return new BenchmarkAllocationAudit(
				1.32105488559,
				5.10391814153,
				1.18825381257,
				6.55695305946,
				ALLOCATION_CLAIM_MECHANICAL_GAIN_PERCENT
		);
	}

	private static StrongestAllocationAudit strongestAllocation() {
		return new StrongestAllocationAudit(
				"22.0in 240kv 25V MN501S T zD 0.40 optimal vs equal 0.60",
				11.5971168978,
				1.16820448818,
				"22.0in 240kv 25V MN501S T command envelope 0.60",
				9.42018897016,
				1.4635645296,
				-0.0119293498523
		);
	}

	private static SurfaceFitAudit surfaceFit() {
		return new SurfaceFitAudit(
				1.07171693717,
				0.997941367477,
				1.24381747572,
				0.997209102716
		);
	}

	private static boolean sameHorizontalPosition(RotorSpec first, RotorSpec second) {
		return Math.abs(first.positionBodyMeters().x() - second.positionBodyMeters().x()) <= 1.0e-9
				&& Math.abs(first.positionBodyMeters().z() - second.positionBodyMeters().z()) <= 1.0e-9;
	}

	private static double averageRotorRadius(List<RotorSpec> rotors) {
		double total = 0.0;
		for (RotorSpec rotor : rotors) {
			total += rotor.radiusMeters();
		}
		return total / rotors.size();
	}

	private static double interpolateCommandMapSurface(
			double[][] valuesBySpacing,
			double spacingRatio,
			double loadFraction
	) {
		double boundedSpacing = MathUtil.clamp(
				spacingRatio,
				COMMAND_MAP_SPACING_RATIOS[0],
				COMMAND_MAP_SPACING_RATIOS[COMMAND_MAP_SPACING_RATIOS.length - 1]
		);
		double lowerValue = interpolateCommandMap(valuesBySpacing[0], loadFraction);
		if (boundedSpacing <= COMMAND_MAP_SPACING_RATIOS[0]) {
			return lowerValue;
		}

		for (int i = 1; i < COMMAND_MAP_SPACING_RATIOS.length; i++) {
			double upperSpacing = COMMAND_MAP_SPACING_RATIOS[i];
			if (boundedSpacing <= upperSpacing) {
				double lowerSpacing = COMMAND_MAP_SPACING_RATIOS[i - 1];
				lowerValue = interpolateCommandMap(valuesBySpacing[i - 1], loadFraction);
				double upperValue = interpolateCommandMap(valuesBySpacing[i], loadFraction);
				double amount = (boundedSpacing - lowerSpacing) / (upperSpacing - lowerSpacing);
				return MathUtil.lerp(lowerValue, upperValue, amount);
			}
		}

		return interpolateCommandMap(
				valuesBySpacing[valuesBySpacing.length - 1],
				loadFraction
		);
	}

	private static double interpolateCommandMap(double[] values, double loadFraction) {
		double boundedLoadFraction = MathUtil.clamp(loadFraction, 0.0, 1.0);
		double lookupValue = values[values.length - 1];
		if (boundedLoadFraction <= COMMAND_MAP_LOAD_FRACTIONS[0]) {
			lookupValue = values[0];
		} else {
			for (int i = 1; i < COMMAND_MAP_LOAD_FRACTIONS.length; i++) {
				double upperLoad = COMMAND_MAP_LOAD_FRACTIONS[i];
				if (boundedLoadFraction <= upperLoad) {
					double lowerLoad = COMMAND_MAP_LOAD_FRACTIONS[i - 1];
					double amount = (boundedLoadFraction - lowerLoad) / (upperLoad - lowerLoad);
					lookupValue = MathUtil.lerp(
							values[i - 1],
							values[i],
							amount
					);
					break;
				}
			}
		}
		return lookupValue;
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
