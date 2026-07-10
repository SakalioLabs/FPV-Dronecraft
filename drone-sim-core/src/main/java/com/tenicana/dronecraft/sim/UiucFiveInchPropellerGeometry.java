package com.tenicana.dronecraft.sim;

import java.util.List;

import com.tenicana.dronecraft.sim.RotorHoverBladeProfilePowerModel.BladeGeometry;
import com.tenicana.dronecraft.sim.RotorHoverBladeProfilePowerModel.BladeStation;

public final class UiucFiveInchPropellerGeometry {
	private static final BladeGeometry DA4052 = new BladeGeometry(
			"uiuc-da4052-5x3.75-three-blade",
			"UIUC DA4052 5x3.75 three-blade",
			"https://m-selig.ae.illinois.edu/props/volume-2/data/da4052_5x3.75_geom.txt",
			3,
			List.of(
					station(0.15, 0.1246, 42.397),
					station(0.20, 0.1476, 43.441),
					station(0.25, 0.2604, 38.976),
					station(0.30, 0.3446, 36.442),
					station(0.35, 0.3645, 33.590),
					station(0.40, 0.3572, 30.501),
					station(0.45, 0.3422, 27.979),
					station(0.50, 0.3208, 26.144),
					station(0.55, 0.2920, 24.601),
					station(0.60, 0.2604, 23.183),
					station(0.65, 0.2318, 21.909),
					station(0.70, 0.2068, 20.912),
					station(0.75, 0.1833, 20.115),
					station(0.80, 0.1586, 19.450),
					station(0.85, 0.1320, 18.957),
					station(0.90, 0.1038, 18.640),
					station(0.95, 0.0735, 18.466),
					station(1.00, 0.0162, 15.278)
			)
	);

	private static final BladeGeometry NR640 = new BladeGeometry(
			"uiuc-nr640-5in-15deg-three-blade",
			"UIUC NR640 5in 15deg three-blade",
			"https://m-selig.ae.illinois.edu/props/volume-2/data/nr640_5_15deg_geom.txt",
			3,
			List.of(
					station(0.15, 0.1264, 41.018),
					station(0.20, 0.1022, 41.522),
					station(0.25, 0.1047, 37.149),
					station(0.30, 0.1141, 33.859),
					station(0.35, 0.1281, 30.931),
					station(0.40, 0.1412, 27.239),
					station(0.45, 0.1504, 24.190),
					station(0.50, 0.1546, 22.020),
					station(0.55, 0.1538, 20.248),
					station(0.60, 0.1491, 18.836),
					station(0.65, 0.1424, 17.910),
					station(0.70, 0.1343, 17.294),
					station(0.75, 0.1249, 16.631),
					station(0.80, 0.1146, 15.989),
					station(0.85, 0.1027, 15.606),
					station(0.90, 0.0893, 15.347),
					station(0.95, 0.0742, 15.089),
					station(1.00, 0.0233, 7.219)
			)
	);

	private static final BladeGeometry MICRO_INVENT = new BladeGeometry(
			"uiuc-microinvent-5x4-three-blade",
			"UIUC MicroInvent 5x4 three-blade",
			"https://m-selig.ae.illinois.edu/props/volume-2/data/mit_5x4_geom.txt",
			3,
			List.of(
					station(0.15, 0.1121, 23.418),
					station(0.20, 0.1297, 28.485),
					station(0.25, 0.1477, 31.062),
					station(0.30, 0.1639, 31.635),
					station(0.35, 0.1764, 30.435),
					station(0.40, 0.1855, 28.445),
					station(0.45, 0.1920, 26.557),
					station(0.50, 0.1947, 24.911),
					station(0.55, 0.1932, 23.346),
					station(0.60, 0.1884, 21.695),
					station(0.65, 0.1826, 20.204),
					station(0.70, 0.1760, 18.790),
					station(0.75, 0.1673, 17.386),
					station(0.80, 0.1559, 16.050),
					station(0.85, 0.1402, 15.075),
					station(0.90, 0.1175, 14.595),
					station(0.95, 0.0850, 14.073),
					station(1.00, 0.0112, 13.817)
			)
	);

	private static final List<BladeGeometry> THREE_BLADE_PROFILES = List.of(
			DA4052,
			NR640,
			MICRO_INVENT
	);

	private UiucFiveInchPropellerGeometry() {
	}

	public static BladeGeometry da4052FiveByThreePointSevenFiveThreeBlade() {
		return DA4052;
	}

	public static BladeGeometry nr640FiveInchFifteenDegreeThreeBlade() {
		return NR640;
	}

	public static BladeGeometry microInventFiveByFourThreeBlade() {
		return MICRO_INVENT;
	}

	public static List<BladeGeometry> threeBladeProfiles() {
		return THREE_BLADE_PROFILES;
	}

	private static BladeStation station(
			double radialFraction,
			double chordToRadius,
			double pitchAngleDegrees
	) {
		return new BladeStation(radialFraction, chordToRadius, Math.toRadians(pitchAngleDegrees));
	}
}
