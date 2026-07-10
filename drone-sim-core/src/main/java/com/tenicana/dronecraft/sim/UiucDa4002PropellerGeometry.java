package com.tenicana.dronecraft.sim;

import java.util.List;

import com.tenicana.dronecraft.sim.RotorHoverBladeProfilePowerModel.BladeGeometry;
import com.tenicana.dronecraft.sim.RotorHoverBladeProfilePowerModel.BladeStation;

/** UIUC low-Reynolds-number DA4002 geometry used with the SDA1075 section. */
public final class UiucDa4002PropellerGeometry {
	public static final double FIVE_INCH_DIAMETER_METERS = 0.127;
	public static final double NINE_INCH_DIAMETER_METERS = 0.2286;
	public static final String FIVE_INCH_STATIC_SOURCE_URL =
			"https://m-selig.ae.illinois.edu/props/volume-2/data/da4002_5x3.75_static_1121md.txt";
	public static final String NINE_INCH_STATIC_SOURCE_URL =
			"https://m-selig.ae.illinois.edu/props/volume-2/data/da4002_9x6.75_static_1107rd.txt";

	private static final BladeGeometry FIVE_BY_THREE_POINT_SEVEN_FIVE = new BladeGeometry(
			"uiuc-da4002-5x3.75-two-blade",
			"UIUC DA4002 5x3.75 two-blade",
			"https://m-selig.ae.illinois.edu/props/volume-2/data/da4002_5x3.75_geom.txt",
			2,
			List.of(
					station(0.15, 0.1253, 41.466),
					station(0.20, 0.1121, 44.054),
					station(0.25, 0.1384, 40.139),
					station(0.30, 0.1594, 36.430),
					station(0.35, 0.1705, 33.121),
					station(0.40, 0.1760, 30.108),
					station(0.45, 0.1780, 27.726),
					station(0.50, 0.1783, 25.858),
					station(0.55, 0.1778, 24.093),
					station(0.60, 0.1788, 22.396),
					station(0.65, 0.1801, 20.921),
					station(0.70, 0.1797, 19.817),
					station(0.75, 0.1804, 18.933),
					station(0.80, 0.1810, 18.173),
					station(0.85, 0.1796, 17.534),
					station(0.90, 0.1793, 16.840),
					station(0.95, 0.1555, 14.427),
					station(1.00, 0.0226, 15.523)
			)
	);

	private static final BladeGeometry NINE_BY_SIX_POINT_SEVEN_FIVE = new BladeGeometry(
			"uiuc-da4002-9x6.75-two-blade",
			"UIUC DA4002 9x6.75 two-blade",
			"https://m-selig.ae.illinois.edu/props/volume-2/data/da4002_9x6.75_geom.txt",
			2,
			List.of(
					station(0.15, 0.1198, 42.481),
					station(0.20, 0.1128, 44.647),
					station(0.25, 0.1436, 41.154),
					station(0.30, 0.1689, 37.475),
					station(0.35, 0.1775, 34.027),
					station(0.40, 0.1782, 30.549),
					station(0.45, 0.1773, 27.875),
					station(0.50, 0.1782, 25.831),
					station(0.55, 0.1790, 23.996),
					station(0.60, 0.1787, 22.396),
					station(0.65, 0.1787, 21.009),
					station(0.70, 0.1786, 19.814),
					station(0.75, 0.1785, 18.786),
					station(0.80, 0.1790, 17.957),
					station(0.85, 0.1792, 17.245),
					station(0.90, 0.1792, 16.657),
					station(0.95, 0.1692, 13.973),
					station(1.00, 0.0154, 2.117)
			)
	);

	private static final List<BladeGeometry> GEOMETRIES = List.of(
			FIVE_BY_THREE_POINT_SEVEN_FIVE,
			NINE_BY_SIX_POINT_SEVEN_FIVE
	);

	private UiucDa4002PropellerGeometry() {
	}

	public static BladeGeometry fiveByThreePointSevenFiveTwoBlade() {
		return FIVE_BY_THREE_POINT_SEVEN_FIVE;
	}

	public static BladeGeometry nineBySixPointSevenFiveTwoBlade() {
		return NINE_BY_SIX_POINT_SEVEN_FIVE;
	}

	public static List<BladeGeometry> geometries() {
		return GEOMETRIES;
	}

	private static BladeStation station(
			double radialFraction,
			double chordToRadius,
			double pitchAngleDegrees
	) {
		return new BladeStation(radialFraction, chordToRadius, Math.toRadians(pitchAngleDegrees));
	}
}
