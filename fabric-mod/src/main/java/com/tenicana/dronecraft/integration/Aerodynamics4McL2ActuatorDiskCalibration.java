package com.tenicana.dronecraft.integration;

import java.util.ArrayList;
import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2ActuatorDiskCalibration {
	private static final double AIR_DENSITY_KG_M3 = 1.225;
	private static final double AIR_KINEMATIC_VISCOSITY_M2_S = 1.5e-5;
	private static final double EPSILON = 1.0e-12;
	private static final String[] SPIN_STATE_SAMPLES = { "idle", "hover", "cruise", "max" };

	public static final String SOURCE_ID = "A4MC-L2-Actuator-Disk-Load-Packet";
	public static final String CAVEAT =
			"Momentum-theory actuator-disk load packet for future A4MC L2 powered-rotor source terms; validate pressure jumps against live force and pressure-center runs before feeding gameplay physics.";
	public static final int SOURCE_REFERENCE_COUNT = 5;
	public static final int PRESET_SAMPLE_COUNT = 4;
	public static final int SPIN_STATE_SAMPLE_COUNT = 4;
	public static final int LOAD_SCENARIO_COUNT = PRESET_SAMPLE_COUNT * SPIN_STATE_SAMPLE_COUNT;
	public static final int LOAD_METRIC_COUNT = 12;
	public static final int SUMMARY_METRIC_ROW_COUNT = 8;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ LOAD_SCENARIO_COUNT * LOAD_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2ActuatorDiskCalibration() {
	}

	public record ActuatorDiskLoad(
			String presetName,
			String spinState,
			int rotorCount,
			double spinRatio,
			double thrustNewtonsPerRotor,
			double totalThrustNewtons,
			double thrustToWeight,
			double openFraction,
			double openAreaPerRotorSquareMeters,
			double pressureJumpPascals,
			double idealInducedVelocityMetersPerSecond,
			double farWakeVelocityMetersPerSecond,
			double momentumPowerWattsPerRotor,
			double totalMomentumPowerWatts,
			double tipSpeedMetersPerSecond,
			double edgewiseAdvanceRatio,
			double axialInletOverInducedVelocity,
			double representativeBladeReynoldsNumber
	) {
	}

	public record ActuatorDiskExtrema(
			double maxPressureJumpPascals,
			double maxIdealInducedVelocityMetersPerSecond,
			double maxTotalMomentumPowerWatts,
			double maxEdgewiseAdvanceRatio,
			double maxThrustToWeight,
			double minOpenFraction,
			double maxRepresentativeBladeReynoldsNumber,
			int scenarioCount
	) {
	}

	public record ActuatorDiskAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int presetSampleCount,
			int spinStateSampleCount,
			int loadScenarioCount,
			int loadMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			double airDensityKgM3,
			double airKinematicViscosityM2S,
			List<ActuatorDiskLoad> loads,
			ActuatorDiskExtrema extrema
	) {
		public ActuatorDiskAudit {
			loads = List.copyOf(loads);
		}
	}

	public static ActuatorDiskAudit audit() {
		List<ActuatorDiskLoad> loads = new ArrayList<>(LOAD_SCENARIO_COUNT);
		loads.addAll(loads("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72));
		loads.addAll(loads("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64));
		loads.addAll(loads("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48));
		loads.addAll(loads("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80));
		return new ActuatorDiskAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				PRESET_SAMPLE_COUNT,
				SPIN_STATE_SAMPLE_COUNT,
				LOAD_SCENARIO_COUNT,
				LOAD_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				AIR_DENSITY_KG_M3,
				AIR_KINEMATIC_VISCOSITY_M2_S,
				loads,
				extrema(loads)
		);
	}

	public static List<ActuatorDiskLoad> loads(
			String presetName,
			DroneConfig config,
			Vec3 inletVelocity,
			int steps
	) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		String name = presetName == null || presetName.isBlank() ? "custom" : presetName;
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, inletVelocity, steps);
		List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures =
				Aerodynamics4McL2RotorDiskAperture.apertures(config, probe);
		List<ActuatorDiskLoad> loads = new ArrayList<>(SPIN_STATE_SAMPLES.length);
		for (String spinState : SPIN_STATE_SAMPLES) {
			loads.add(load(name, config, apertures, spinState));
		}
		return List.copyOf(loads);
	}

	public static ActuatorDiskLoad load(
			String presetName,
			DroneConfig config,
			List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures,
			String spinState
	) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		if (apertures == null || apertures.isEmpty()) {
			throw new IllegalArgumentException("apertures must include at least one rotor disk.");
		}
		RotorSpec rotor = config.rotors().get(0);
		double spinRatio = spinRatioForState(config, rotor, spinState);
		double thrustPerRotor = rotor.maxThrustNewtons() * spinRatio * spinRatio;
		double totalThrust = thrustPerRotor * apertures.size();
		double weight = config.massKg() * config.gravityMetersPerSecondSquared();
		double openAreaPerRotor = meanOpenArea(apertures);
		double openFraction = meanOpenFraction(apertures);
		double pressureJump = thrustPerRotor / Math.max(EPSILON, openAreaPerRotor);
		double inducedVelocity = Math.sqrt(Math.max(0.0, thrustPerRotor)
				/ Math.max(EPSILON, 2.0 * AIR_DENSITY_KG_M3 * openAreaPerRotor));
		double tipSpeed = rotor.maxOmegaRadiansPerSecond() * spinRatio * rotor.radiusMeters();
		double meanChord = meanRepresentativeChord(apertures);
		return new ActuatorDiskLoad(
				presetName == null || presetName.isBlank() ? "custom" : presetName,
				sanitizedSpinState(spinState),
				apertures.size(),
				spinRatio,
				thrustPerRotor,
				totalThrust,
				ratio(totalThrust, weight),
				openFraction,
				openAreaPerRotor,
				pressureJump,
				inducedVelocity,
				2.0 * inducedVelocity,
				thrustPerRotor * inducedVelocity,
				totalThrust * inducedVelocity,
				tipSpeed,
				ratio(meanInPlaneInlet(apertures), tipSpeed),
				ratio(meanAxialInlet(apertures), inducedVelocity),
				tipSpeed * meanChord / AIR_KINEMATIC_VISCOSITY_M2_S
		);
	}

	static double spinRatioForState(DroneConfig config, RotorSpec rotor, String spinState) {
		return switch (sanitizedSpinState(spinState)) {
			case "hover" -> hoverSpinRatio(config, rotor);
			case "idle" -> 0.12;
			case "cruise" -> 0.65;
			case "max" -> 1.0;
			default -> 0.0;
		};
	}

	static double hoverSpinRatio(DroneConfig config, RotorSpec rotor) {
		double hoverThrust = config.massKg() * config.gravityMetersPerSecondSquared()
				/ Math.max(1, config.rotors().size());
		return Math.sqrt(MathUtil.clamp(hoverThrust / rotor.maxThrustNewtons(), 0.0, 1.0));
	}

	private static ActuatorDiskExtrema extrema(List<ActuatorDiskLoad> loads) {
		double maxPressureJump = 0.0;
		double maxInducedVelocity = 0.0;
		double maxPower = 0.0;
		double maxEdgewiseAdvance = 0.0;
		double maxThrustToWeight = 0.0;
		double minOpenFraction = Double.POSITIVE_INFINITY;
		double maxReynolds = 0.0;
		for (ActuatorDiskLoad load : loads) {
			maxPressureJump = Math.max(maxPressureJump, load.pressureJumpPascals());
			maxInducedVelocity = Math.max(maxInducedVelocity, load.idealInducedVelocityMetersPerSecond());
			maxPower = Math.max(maxPower, load.totalMomentumPowerWatts());
			maxEdgewiseAdvance = Math.max(maxEdgewiseAdvance, load.edgewiseAdvanceRatio());
			maxThrustToWeight = Math.max(maxThrustToWeight, load.thrustToWeight());
			minOpenFraction = Math.min(minOpenFraction, load.openFraction());
			maxReynolds = Math.max(maxReynolds, load.representativeBladeReynoldsNumber());
		}
		return new ActuatorDiskExtrema(
				maxPressureJump,
				maxInducedVelocity,
				maxPower,
				maxEdgewiseAdvance,
				maxThrustToWeight,
				minOpenFraction,
				maxReynolds,
				loads.size()
		);
	}

	private static double meanOpenArea(List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures) {
		double sum = 0.0;
		for (Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture aperture : apertures) {
			sum += aperture.openAreaSquareMeters();
		}
		return sum / apertures.size();
	}

	private static double meanOpenFraction(List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures) {
		double sum = 0.0;
		for (Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture aperture : apertures) {
			sum += aperture.openFraction();
		}
		return sum / apertures.size();
	}

	private static double meanRepresentativeChord(List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures) {
		double sum = 0.0;
		for (Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture aperture : apertures) {
			sum += aperture.representativeBladeChordMeters();
		}
		return sum / apertures.size();
	}

	private static double meanAxialInlet(List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures) {
		double sum = 0.0;
		for (Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture aperture : apertures) {
			sum += aperture.axialInletSpeedMetersPerSecond();
		}
		return sum / apertures.size();
	}

	private static double meanInPlaneInlet(List<Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture> apertures) {
		double sum = 0.0;
		for (Aerodynamics4McL2RotorDiskAperture.RotorDiskAperture aperture : apertures) {
			sum += aperture.inPlaneInletSpeedMetersPerSecond();
		}
		return sum / apertures.size();
	}

	private static String sanitizedSpinState(String spinState) {
		if (spinState == null || spinState.isBlank()) {
			return "custom";
		}
		return spinState.trim();
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
