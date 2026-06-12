package com.tenicana.dronecraft.sim;

public final class PropGeometryCalibration {
	private static final double EPSILON = 1.0e-12;
	private static final double METERS_TO_INCHES = 1.0 / 0.0254;
	private static final double INCHES_TO_METERS = 0.0254;
	private static final double RADIANS_PER_SECOND_TO_RPM = 60.0 / (2.0 * Math.PI);

	public static final String SOURCE_ID = "Prop-Pitch-Geometry-Packet";
	public static final String CAVEAT = "Product and UIUC geometry rows are geometric pitch references, not slip-corrected aerodynamic pitch.";
	public static final int PACKET_ROW_COUNT = 294;
	public static final int OFFICIAL_PROP_REFERENCE_COUNT = 8;
	public static final int UIUC_GEOMETRY_REFERENCE_COUNT = 6;

	private static final OfficialPropReference HQ_5X43X3 = new OfficialPropReference(
			"HQProp Durable 5x4.3x3 V1S",
			"https://www.hqprop.com/hq-durable-prop-5x43x3v1s-2cw2ccw-poly-carbonate-p0051.html",
			"5-inch FPV three-blade near current racing default",
			5.0,
			4.3,
			0.86,
			1.8203333333333334,
			21.35879981637402,
			3,
			3.81
	);
	private static final OfficialPropReference HQ_5X45X3 = new OfficialPropReference(
			"HQProp Durable 5x4.5x3 V1S",
			"https://www.hqprop.com/hq-durable-prop-5x45x3v1s-2cw2ccw-poly-carbonate-p0052.html",
			"5-inch FPV three-blade APDrone/Foxeer pitch neighbor",
			5.0,
			4.5,
			0.90,
			1.905,
			22.257104926657533,
			3,
			4.19
	);
	private static final OfficialPropReference HQ_5X5X3 = new OfficialPropReference(
			"HQProp Durable 5x5x3 V1S",
			"https://www.hqprop.com/hq-durable-prop-5x5x3v1s-2cw2ccw-poly-carbonate-p0085.html",
			"high-pitch 5-inch FPV three-blade reference",
			5.0,
			5.0,
			1.0,
			2.1166666666666667,
			24.452641740112796,
			3,
			4.48
	);
	private static final OfficialPropReference GEMFAN_51466 = new OfficialPropReference(
			"Gemfan 51466 MCK V2",
			"https://www.gemfanhobby.com/hurricane-51466-v2-pc-3-blade.html",
			"low-pitch 5.1-inch FPV three-blade reference",
			5.1889763779527565,
			3.6,
			0.6937784522003034,
			1.524,
			17.509483513033405,
			3,
			4.2
	);
	private static final OfficialPropReference HQ_T3X3X3 = new OfficialPropReference(
			"HQProp Durable T3x3x3",
			"https://www.hqprop.com/hq-durable-prop-t3x3x3-2cw2ccw-poly-carbonate-p0091.html",
			"3-inch cinewhoop class reference",
			3.0,
			3.0,
			1.0,
			1.2699999999999998,
			24.452641740112796,
			3,
			1.48
	);
	private static final OfficialPropReference GEMFAN_D90 = new OfficialPropReference(
			"Gemfan D90 ducted 3-blade",
			"https://www.gemfanhobby.com/d90-ducted-pc-3-blade-m5.html",
			"ducted 90 mm three-blade reference",
			3.5433070866141736,
			3.0,
			0.8466666666666666,
			1.2699999999999998,
			21.05687455729283,
			3,
			2.3
	);
	private static final OfficialPropReference GEMFAN_1045 = new OfficialPropReference(
			"Gemfan 1045 glass-fiber nylon 3-blade",
			"https://www.gemfanhobby.com/1045-glass-fiber-nylon-3-blade.html",
			"10-inch low-pitch lift reference",
			10.0,
			4.5,
			0.45,
			1.905,
			11.564658416350353,
			3,
			17.8
	);
	private static final OfficialPropReference GEMFAN_1050 = new OfficialPropReference(
			"Gemfan 1050 Cinelifter glass-fiber nylon 3-blade",
			"https://www.gemfanhobby.com/1050-cinelifter-glass-fiber-nylon-3-blade.html",
			"10-inch cinelifter pitch reference",
			10.0,
			5.0,
			0.50,
			2.1166666666666667,
			12.809249792046058,
			3,
			16.8
	);

	private static final UiucGeometryReference DA4052_5X375 = new UiucGeometryReference(
			"DA4052 5x3.75 tested geometry",
			"https://m-selig.ae.illinois.edu/props/volume-2/data/da4052_5x3.75_geom.txt",
			"5-inch UIUC broad-chord geometry",
			5.0,
			18,
			0.2068,
			20.912,
			0.8402880906845006,
			0.2162578642621722,
			3
	);
	private static final UiucGeometryReference NR640_5IN = new UiucGeometryReference(
			"NR640 5in 15deg tested geometry",
			"https://m-selig.ae.illinois.edu/props/volume-2/data/nr640_5_15deg_geom.txt",
			"5-inch UIUC narrow-chord geometry",
			5.0,
			18,
			0.1343,
			17.294,
			0.6846953807285286,
			0.11545942156878926,
			3
	);
	private static final UiucGeometryReference MICROINVENT_5X4 = new UiucGeometryReference(
			"MicroInvent 5x4 geometry",
			"https://m-selig.ae.illinois.edu/props/volume-2/data/mi_5x4_geom.txt",
			"5-inch UIUC mid-chord geometry",
			5.0,
			18,
			0.176,
			18.79,
			0.7482114901729155,
			0.14928640041465022,
			3
	);
	private static final UiucGeometryReference APC_SLOW_10X47 = new UiucGeometryReference(
			"APC Slow Flyer 10x4.7 geometry",
			"https://m-selig.ae.illinois.edu/props/volume-1/data/apcsf_10x47_geom.txt",
			"10-inch slow-flyer low-pitch geometry",
			10.0,
			18,
			0.21,
			11.36,
			0.44182187012962476,
			0.11262552737620596,
			2
	);
	private static final UiucGeometryReference APC_THIN_10X5 = new UiucGeometryReference(
			"APC Thin Electric 10x5 geometry",
			"https://m-selig.ae.illinois.edu/props/volume-1/data/apce_10x5_geom.txt",
			"10-inch thin-electric lift geometry",
			10.0,
			18,
			0.145,
			14.09,
			0.5519714560447619,
			0.09480017374991366,
			2
	);
	private static final UiucGeometryReference APC_THIN_10X7 = new UiucGeometryReference(
			"APC Thin Electric 10x7 geometry",
			"https://m-selig.ae.illinois.edu/props/volume-1/data/apce_10x7_geom.txt",
			"10-inch high-pitch lift geometry",
			10.0,
			18,
			0.145,
			17.98,
			0.7136871503186859,
			0.09541806941132926,
			2
	);

	private PropGeometryCalibration() {
	}

	public record OfficialPropReference(
			String propellerId,
			String sourceUrl,
			String role,
			double diameterInches,
			double pitchInches,
			double pitchToDiameterRatio,
			double pitchSpeedMetersPerSecondAt1000Rpm,
			double geometricPitchAngle70rDegrees,
			int bladeCount,
			double massGrams
	) {
	}

	public record UiucGeometryReference(
			String geometryId,
			String sourceUrl,
			String role,
			double diameterInches,
			int stationCount,
			double chordToRadius70r,
			double beta70rDegrees,
			double localPitchToDiameter70r,
			double planformSolidityProxy,
			int bladeCount
	) {
	}

	public record CurrentPropGeometry(
			double diameterInches,
			double pitchInches,
			double pitchToDiameterRatio,
			double hoverRotorRpm,
			double maxRotorRpm,
			double hoverPitchSpeedMetersPerSecond,
			double maxPitchSpeedMetersPerSecond,
			double geometricPitchAngle70rDegrees,
			double representativeChordToRadius70r,
			double representativeChordMeters,
			int bladeCount
	) {
	}

	public record OfficialPropComparison(
			OfficialPropReference reference,
			double diameterDeltaPercent,
			double currentPitchOverReference,
			double currentPitchToDiameterOverReference,
			double currentPitchSpeedOverReference,
			double currentGeometricPitchAngleOverReference
	) {
	}

	public record UiucGeometryComparison(
			UiucGeometryReference reference,
			double diameterDeltaPercent,
			double currentChordOverReference70r,
			double currentGeometricPitchAngleOverReference70r,
			double currentPitchToDiameterOverReferenceLocal70r
	) {
	}

	public record PropGeometryAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int officialPropReferenceCount,
			int uiucGeometryReferenceCount,
			CurrentPropGeometry current,
			OfficialPropReference hq5x43x3,
			OfficialPropReference hq5x45x3,
			OfficialPropReference hq5x5x3,
			OfficialPropReference gemfan51466,
			OfficialPropReference hqT3x3x3,
			OfficialPropReference gemfanD90,
			OfficialPropReference gemfan1045,
			OfficialPropReference gemfan1050,
			UiucGeometryReference da4052Geometry,
			UiucGeometryReference nr640Geometry,
			UiucGeometryReference microInvent5x4Geometry,
			UiucGeometryReference apcSlow10x47Geometry,
			UiucGeometryReference apcThin10x5Geometry,
			UiucGeometryReference apcThin10x7Geometry,
			OfficialPropComparison hq5x43Comparison,
			OfficialPropComparison hq5x45Comparison,
			OfficialPropComparison gemfan51466Comparison,
			OfficialPropComparison gemfan1050Comparison,
			UiucGeometryComparison da4052Comparison,
			UiucGeometryComparison nr640Comparison,
			UiucGeometryComparison apcThin10x5Comparison,
			UiucGeometryComparison apcThin10x7Comparison
	) {
	}

	public static PropGeometryAudit audit(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}

		CurrentPropGeometry current = currentGeometry(config);
		return new PropGeometryAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				OFFICIAL_PROP_REFERENCE_COUNT,
				UIUC_GEOMETRY_REFERENCE_COUNT,
				current,
				HQ_5X43X3,
				HQ_5X45X3,
				HQ_5X5X3,
				GEMFAN_51466,
				HQ_T3X3X3,
				GEMFAN_D90,
				GEMFAN_1045,
				GEMFAN_1050,
				DA4052_5X375,
				NR640_5IN,
				MICROINVENT_5X4,
				APC_SLOW_10X47,
				APC_THIN_10X5,
				APC_THIN_10X7,
				compareOfficial(current, HQ_5X43X3),
				compareOfficial(current, HQ_5X45X3),
				compareOfficial(current, GEMFAN_51466),
				compareOfficial(current, GEMFAN_1050),
				compareUiuc(current, DA4052_5X375),
				compareUiuc(current, NR640_5IN),
				compareUiuc(current, APC_THIN_10X5),
				compareUiuc(current, APC_THIN_10X7)
		);
	}

	private static CurrentPropGeometry currentGeometry(DroneConfig config) {
		RotorSpec rotor = config.rotors().get(0);
		double diameterInches = rotor.radiusMeters() * 2.0 * METERS_TO_INCHES;
		double pitchInches = rotor.bladePitchMeters() * METERS_TO_INCHES;
		double hoverRpm = averageHoverRotorRpm(config);
		double maxRpm = averageMaxRotorRpm(config);
		double pitchMeters = rotor.bladePitchMeters();
		return new CurrentPropGeometry(
				diameterInches,
				pitchInches,
				rotor.bladePitchToDiameterRatio(),
				hoverRpm,
				maxRpm,
				pitchSpeedMetersPerSecond(hoverRpm, pitchMeters),
				pitchSpeedMetersPerSecond(maxRpm, pitchMeters),
				Math.toDegrees(rotor.geometricBladePitchAngleRadians()),
				rotor.representativeBladeChordToRadiusRatio(),
				rotor.representativeBladeChordMeters(),
				rotor.bladeCount()
		);
	}

	private static OfficialPropComparison compareOfficial(CurrentPropGeometry current, OfficialPropReference reference) {
		return new OfficialPropComparison(
				reference,
				diameterDeltaPercent(current.diameterInches(), reference.diameterInches()),
				ratio(current.pitchInches(), reference.pitchInches()),
				ratio(current.pitchToDiameterRatio(), reference.pitchToDiameterRatio()),
				ratio(current.pitchInches() * INCHES_TO_METERS / 60.0 * 1000.0,
						reference.pitchSpeedMetersPerSecondAt1000Rpm()),
				ratio(current.geometricPitchAngle70rDegrees(), reference.geometricPitchAngle70rDegrees())
		);
	}

	private static UiucGeometryComparison compareUiuc(CurrentPropGeometry current, UiucGeometryReference reference) {
		return new UiucGeometryComparison(
				reference,
				diameterDeltaPercent(current.diameterInches(), reference.diameterInches()),
				ratio(current.representativeChordToRadius70r(), reference.chordToRadius70r()),
				ratio(current.geometricPitchAngle70rDegrees(), reference.beta70rDegrees()),
				ratio(current.pitchToDiameterRatio(), reference.localPitchToDiameter70r())
		);
	}

	private static double averageMaxRotorRpm(DroneConfig config) {
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rotor.maxOmegaRadiansPerSecond() * RADIANS_PER_SECOND_TO_RPM;
		}
		return total / config.rotors().size();
	}

	private static double averageHoverRotorRpm(DroneConfig config) {
		double nominalHoverThrust = config.massKg() * config.gravityMetersPerSecondSquared()
				/ config.rotors().size();
		double total = 0.0;
		for (RotorSpec rotor : config.rotors()) {
			total += rpmForThrustAndCoefficient(nominalHoverThrust, rotor.thrustCoefficient());
		}
		return total / config.rotors().size();
	}

	private static double rpmForThrustAndCoefficient(double thrustNewtons, double thrustCoefficient) {
		if (!Double.isFinite(thrustNewtons)
				|| !Double.isFinite(thrustCoefficient)
				|| thrustNewtons <= 0.0
				|| thrustCoefficient <= 0.0) {
			return 0.0;
		}
		return Math.sqrt(thrustNewtons / thrustCoefficient) * RADIANS_PER_SECOND_TO_RPM;
	}

	private static double pitchSpeedMetersPerSecond(double rpm, double pitchMeters) {
		if (!Double.isFinite(rpm) || !Double.isFinite(pitchMeters) || rpm <= 0.0 || pitchMeters <= 0.0) {
			return 0.0;
		}
		return rpm * pitchMeters / 60.0;
	}

	private static double diameterDeltaPercent(double currentDiameterInches, double referenceDiameterInches) {
		return Math.abs(currentDiameterInches - referenceDiameterInches)
				/ Math.max(EPSILON, referenceDiameterInches)
				* 100.0;
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
