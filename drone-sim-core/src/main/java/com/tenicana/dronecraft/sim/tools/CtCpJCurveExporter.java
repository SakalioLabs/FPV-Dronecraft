package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CtCpJCurveExporter {
	private static final String HEADER = String.join(",",
			"preset",
			"case",
			"query_j",
			"query_rpm",
			"effective_j",
			"effective_rpm",
			"interpolation_status",
			"clamped",
			"blocked",
			"ct",
			"cp",
			"eta",
			"axial_speed_mps",
			"thrust_n",
			"shaft_power_w",
			"shaft_torque_nm",
			"disk_loading_n_m2",
			"ideal_induced_velocity_mps",
			"ideal_momentum_power_w",
			"ideal_momentum_power_over_shaft_power"
	);

	private CtCpJCurveExporter() {
	}

	public static void main(String[] args) throws IOException {
		String presetName = args.length >= 1 && !args[0].isBlank()
				? args[0]
				: PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME;
		Path output = args.length >= 2 && !args[1].isBlank()
				? Path.of(args[1])
				: Path.of("build", "ct-cp-j-curves", presetName + ".csv");
		double airDensity = args.length >= 3 && !args[2].isBlank()
				? Double.parseDouble(args[2])
				: PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double diameter = args.length >= 4 && !args[3].isBlank()
				? Double.parseDouble(args[3])
				: defaultPropellerDiameterMeters(presetName);
		write(presetName, output, airDensity, diameter);
	}

	public static void write(
			String presetName,
			Path output,
			double airDensityKgPerCubicMeter,
			double propellerDiameterMeters
	) throws IOException {
		if (output == null) {
			throw new IllegalArgumentException("output path must not be null.");
		}
		List<String> lines = csvLines(presetName, airDensityKgPerCubicMeter, propellerDiameterMeters);
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.write(output, lines, StandardCharsets.UTF_8);
	}

	public static List<String> csvLines(
			String presetName,
			double airDensityKgPerCubicMeter,
			double propellerDiameterMeters
	) {
		List<PropellerArchiveCtCpJLookupEvaluator.LookupQuery> queries =
				PropellerArchiveCtCpJLookupEvaluator.acceptedReferenceCurveQueries(
						presetName,
						propellerDiameterMeters,
						airDensityKgPerCubicMeter
				);
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (PropellerArchiveCtCpJLookupEvaluator.LookupQuery query : queries) {
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample =
					PropellerArchiveCtCpJLookupEvaluator.sampleRotor(query);
			lines.add(csvLine(sample));
		}
		return List.copyOf(lines);
	}

	private static String csvLine(PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample sample) {
		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup = sample.lookup();
		return String.join(",",
				escape(lookup.presetName()),
				escape(lookup.caseName()),
				number(lookup.queryAdvanceRatioJ()),
				number(lookup.queryRpm()),
				number(lookup.effectiveAdvanceRatioJ()),
				number(lookup.effectiveRpm()),
				escape(lookup.interpolationStatus().name()),
				Boolean.toString(lookup.clamped()),
				Boolean.toString(lookup.blocked()),
				number(lookup.thrustCoefficientCt()),
				number(lookup.powerCoefficientCp()),
				number(lookup.propulsiveEfficiencyEta()),
				number(sample.axialAdvanceSpeedMetersPerSecond()),
				number(sample.thrustNewtons()),
				number(sample.shaftPowerWatts()),
				number(sample.shaftTorqueNewtonMeters()),
				number(sample.diskLoadingNewtonsPerSquareMeter()),
				number(sample.idealInducedVelocityMetersPerSecond()),
				number(sample.idealMomentumPowerWatts()),
				number(sample.idealMomentumPowerOverShaftPower())
		);
	}

	private static double defaultPropellerDiameterMeters(String presetName) {
		DroneConfig config = switch (presetName) {
			case "apDrone" -> DroneConfig.apDrone();
			case "racingQuad" -> DroneConfig.racingQuad();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavyLift" -> DroneConfig.heavyLift();
			default -> throw new IllegalArgumentException("unknown DroneConfig preset: " + presetName);
		};
		return config.rotors().get(0).radiusMeters() * 2.0;
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%.15g", value);
	}
}
