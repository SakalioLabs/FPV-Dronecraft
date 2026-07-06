package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJOpenFoamValidationPlan;
import com.tenicana.dronecraft.sim.RotorSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CtCpJOpenFoamDimensionalComparisonImporter {
	private static final String HEADER = String.join(",",
			"preset",
			"case",
			"query_j",
			"query_rpm",
			"air_density_kg_m3",
			"diameter_m",
			"reference_source_id",
			"reference_status",
			"reference_blocked",
			"cfd_solver_status",
			"source_case_sha256",
			"reference_ct",
			"cfd_ct",
			"ct_residual",
			"ct_residual_fraction",
			"reference_cp",
			"cfd_cp",
			"cp_residual",
			"cp_residual_fraction",
			"reference_eta",
			"cfd_eta",
			"eta_residual",
			"reference_thrust_n",
			"cfd_thrust_n",
			"thrust_residual_n",
			"thrust_residual_fraction",
			"reference_shaft_power_w",
			"cfd_shaft_power_w",
			"power_residual_w",
			"power_residual_fraction",
			"reference_shaft_torque_nm",
			"cfd_shaft_torque_nm",
			"torque_residual_nm",
			"torque_residual_fraction",
			"cfd_torque_from_power_nm",
			"cfd_power_torque_residual_nm",
			"reference_induced_velocity_mps",
			"cfd_induced_velocity_mps",
			"induced_velocity_residual_mps",
			"reference_momentum_power_w",
			"cfd_momentum_power_w",
			"momentum_power_residual_w",
			"comparable",
			"message"
	);
	private static final double EPSILON = 1.0e-12;

	private CtCpJOpenFoamDimensionalComparisonImporter() {
	}

	public record CfdDimensionalRow(
			String presetName,
			String caseName,
			double queryAdvanceRatioJ,
			double queryRpm,
			double airDensityKgPerCubicMeter,
			double propellerDiameterMeters,
			double cfdThrustNewtons,
			double cfdShaftPowerWatts,
			double cfdShaftTorqueNewtonMeters,
			double cfdInducedVelocityMetersPerSecond,
			double cfdMomentumPowerWatts,
			double cfdThrustCoefficientCt,
			double cfdPowerCoefficientCp,
			double cfdPropulsiveEfficiencyEta,
			String sourceCaseSha256,
			String solverStatus
	) {
		public CfdDimensionalRow {
			presetName = presetName == null || presetName.isBlank()
					? PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME
					: presetName.trim();
			caseName = caseName == null ? "" : caseName.trim();
			if (caseName.isBlank()) {
				throw new IllegalArgumentException("case must not be blank.");
			}
			if (!Double.isFinite(queryAdvanceRatioJ) || queryAdvanceRatioJ < 0.0) {
				throw new IllegalArgumentException("queryAdvanceRatioJ must be finite and nonnegative.");
			}
			if (!Double.isFinite(queryRpm) || queryRpm <= 0.0) {
				throw new IllegalArgumentException("queryRpm must be finite and positive.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (!Double.isFinite(propellerDiameterMeters) || propellerDiameterMeters <= 0.0) {
				throw new IllegalArgumentException("propellerDiameterMeters must be finite and positive.");
			}
			if (!Double.isFinite(cfdThrustNewtons)) {
				throw new IllegalArgumentException("cfdThrustNewtons must be finite.");
			}
			if (!Double.isFinite(cfdShaftPowerWatts)) {
				throw new IllegalArgumentException("cfdShaftPowerWatts must be finite.");
			}
			sourceCaseSha256 = sourceCaseSha256 == null ? "" : sourceCaseSha256.trim();
			solverStatus = solverStatus == null || solverStatus.isBlank() ? "UNSPECIFIED" : solverStatus.trim();
		}
	}

	public record ComparisonRow(
			CfdDimensionalRow cfd,
			PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample reference,
			double cfdThrustCoefficientCt,
			double cfdPowerCoefficientCp,
			double cfdPropulsiveEfficiencyEta,
			double cfdTorqueFromPowerNewtonMeters,
			double cfdPowerTorqueResidualNewtonMeters,
			double ctResidual,
			double ctResidualFraction,
			double cpResidual,
			double cpResidualFraction,
			double etaResidual,
			double thrustResidualNewtons,
			double thrustResidualFraction,
			double powerResidualWatts,
			double powerResidualFraction,
			double torqueResidualNewtonMeters,
			double torqueResidualFraction,
			double inducedVelocityResidualMetersPerSecond,
			double momentumPowerResidualWatts,
			boolean comparable,
			String message
	) {
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 2 || args.length > 3) {
			throw new IllegalArgumentException(
					"usage: CtCpJOpenFoamDimensionalComparisonImporter <input.csv> <output.csv> [defaultDensityKgM3]");
		}
		double defaultDensity = args.length == 3 && !args[2].isBlank()
				? Double.parseDouble(args[2])
				: PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		write(Path.of(args[0]), Path.of(args[1]), defaultDensity);
	}

	public static void write(Path input, Path output, double defaultAirDensityKgPerCubicMeter)
			throws IOException {
		if (input == null || output == null) {
			throw new IllegalArgumentException("input and output paths must not be null.");
		}
		List<String> lines = csvLines(
				Files.readString(input, StandardCharsets.UTF_8),
				defaultAirDensityKgPerCubicMeter
		);
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}

	public static List<String> csvLines(String inputCsv, double defaultAirDensityKgPerCubicMeter) {
		List<ComparisonRow> rows = compare(inputCsv, defaultAirDensityKgPerCubicMeter);
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (ComparisonRow row : rows) {
			lines.add(csvLine(row));
		}
		return List.copyOf(lines);
	}

	public static List<ComparisonRow> compare(String inputCsv, double defaultAirDensityKgPerCubicMeter) {
		if (inputCsv == null) {
			throw new IllegalArgumentException("inputCsv must not be null.");
		}
		if (!Double.isFinite(defaultAirDensityKgPerCubicMeter) || defaultAirDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("defaultAirDensityKgPerCubicMeter must be finite and positive.");
		}
		List<Map<String, String>> records = parseCsv(inputCsv);
		List<ComparisonRow> rows = new ArrayList<>();
		for (Map<String, String> record : records) {
			rows.add(compare(cfdRow(record, defaultAirDensityKgPerCubicMeter)));
		}
		return List.copyOf(rows);
	}

	public static ComparisonRow compare(CfdDimensionalRow cfd) {
		if (cfd == null) {
			throw new IllegalArgumentException("cfd row must not be null.");
		}
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample reference = referenceSample(cfd);
		double rpm = reference.lookup().blocked() ? cfd.queryRpm() : reference.lookup().effectiveRpm();
		double revolutionsPerSecond = rpm / 60.0;
		double omega = revolutionsPerSecond * 2.0 * Math.PI;
		double cfdTorqueFromPower = omega > EPSILON
				? cfd.cfdShaftPowerWatts() / omega
				: Double.NaN;
		double cfdTorque = Double.isFinite(cfd.cfdShaftTorqueNewtonMeters())
				? cfd.cfdShaftTorqueNewtonMeters()
				: cfdTorqueFromPower;
		double cfdCt = Double.isFinite(cfd.cfdThrustCoefficientCt())
				? cfd.cfdThrustCoefficientCt()
				: thrustCoefficient(cfd.cfdThrustNewtons(), cfd.airDensityKgPerCubicMeter(),
						revolutionsPerSecond, cfd.propellerDiameterMeters());
		double cfdCp = Double.isFinite(cfd.cfdPowerCoefficientCp())
				? cfd.cfdPowerCoefficientCp()
				: powerCoefficient(cfd.cfdShaftPowerWatts(), cfd.airDensityKgPerCubicMeter(),
						revolutionsPerSecond, cfd.propellerDiameterMeters());
		double cfdEta = Double.isFinite(cfd.cfdPropulsiveEfficiencyEta())
				? cfd.cfdPropulsiveEfficiencyEta()
				: eta(cfd.queryAdvanceRatioJ(), cfdCt, cfdCp);
		double cfdMomentumPower = Double.isFinite(cfd.cfdMomentumPowerWatts())
				? cfd.cfdMomentumPowerWatts()
				: momentumPower(cfd.cfdThrustNewtons(), cfd.queryAdvanceRatioJ(), revolutionsPerSecond,
						cfd.propellerDiameterMeters(), cfd.cfdInducedVelocityMetersPerSecond());
		boolean comparable = !reference.blocked()
				&& Double.isFinite(cfdCt)
				&& Double.isFinite(cfdCp)
				&& Double.isFinite(cfdEta)
				&& Double.isFinite(cfdTorque);
		String message = comparable
				? "ct-cp-j-openfoam-dimensional-comparison-ready"
				: reference.lookup().blocked()
						? reference.lookup().message()
						: "cfd-row-missing-finite-dimensional-channel";
		return new ComparisonRow(
				new CfdDimensionalRow(
						cfd.presetName(),
						cfd.caseName(),
						cfd.queryAdvanceRatioJ(),
						cfd.queryRpm(),
						cfd.airDensityKgPerCubicMeter(),
						cfd.propellerDiameterMeters(),
						cfd.cfdThrustNewtons(),
						cfd.cfdShaftPowerWatts(),
						cfdTorque,
						cfd.cfdInducedVelocityMetersPerSecond(),
						cfdMomentumPower,
						cfdCt,
						cfdCp,
						cfdEta,
						cfd.sourceCaseSha256(),
						cfd.solverStatus()
				),
				reference,
				cfdCt,
				cfdCp,
				cfdEta,
				cfdTorqueFromPower,
				cfdTorque - cfdTorqueFromPower,
				cfdCt - reference.lookup().thrustCoefficientCt(),
				ratio(cfdCt - reference.lookup().thrustCoefficientCt(), reference.lookup().thrustCoefficientCt()),
				cfdCp - reference.lookup().powerCoefficientCp(),
				ratio(cfdCp - reference.lookup().powerCoefficientCp(), reference.lookup().powerCoefficientCp()),
				cfdEta - reference.lookup().propulsiveEfficiencyEta(),
				cfd.cfdThrustNewtons() - reference.thrustNewtons(),
				ratio(cfd.cfdThrustNewtons() - reference.thrustNewtons(), reference.thrustNewtons()),
				cfd.cfdShaftPowerWatts() - reference.shaftPowerWatts(),
				ratio(cfd.cfdShaftPowerWatts() - reference.shaftPowerWatts(), reference.shaftPowerWatts()),
				cfdTorque - reference.shaftTorqueNewtonMeters(),
				ratio(cfdTorque - reference.shaftTorqueNewtonMeters(), reference.shaftTorqueNewtonMeters()),
				cfd.cfdInducedVelocityMetersPerSecond() - reference.idealInducedVelocityMetersPerSecond(),
				cfdMomentumPower - reference.idealMomentumPowerWatts(),
				comparable,
				message
		);
	}

	private static CfdDimensionalRow cfdRow(
			Map<String, String> record,
			double defaultAirDensityKgPerCubicMeter
	) {
		String preset = text(record, "preset",
				PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME);
		String caseName = text(record, "case", "");
		PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase target =
				validationTargetOrNull(preset, caseName);
		DroneConfig config = configForPreset(preset);
		RotorSpec rotor = config.rotors().get(0);
		double diameter = optionalDouble(record, "diameter_m", rotor.radiusMeters() * 2.0);
		double density = optionalDouble(record, "air_density_kg_m3", defaultAirDensityKgPerCubicMeter);
		double queryJ = optionalDouble(record, "query_j",
				target == null ? Double.NaN : target.queryAdvanceRatioJ());
		double queryRpm = optionalDouble(record, "query_rpm",
				target == null ? Double.NaN : target.queryRpm());
		return new CfdDimensionalRow(
				preset,
				caseName,
				queryJ,
				queryRpm,
				density,
				diameter,
				requiredDouble(record, "cfd_thrust_n"),
				requiredDouble(record, "cfd_shaft_power_w"),
				optionalDouble(record, "cfd_shaft_torque_nm", Double.NaN),
				optionalDouble(record, "cfd_induced_velocity_mps", Double.NaN),
				optionalDouble(record, "cfd_momentum_power_w", Double.NaN),
				optionalDouble(record, "cfd_ct", Double.NaN),
				optionalDouble(record, "cfd_cp", Double.NaN),
				optionalDouble(record, "cfd_eta", Double.NaN),
				text(record, "source_case_sha256", ""),
				text(record, "solver_status", "UNSPECIFIED")
		);
	}

	private static PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample referenceSample(
			CfdDimensionalRow cfd
	) {
		PropellerArchiveCtCpJLookupEvaluator.LookupQuery query =
				new PropellerArchiveCtCpJLookupEvaluator.LookupQuery(
						cfd.presetName(),
						cfd.caseName(),
						cfd.queryAdvanceRatioJ(),
						cfd.queryRpm(),
						cfd.propellerDiameterMeters(),
						cfd.airDensityKgPerCubicMeter(),
						PropellerArchiveCtCpJLookupEvaluator.EnvelopePolicy.BLOCK_OUT_OF_ENVELOPE
				);
		return PropellerArchiveCtCpJLookupEvaluator.sampleRotor(query);
	}

	private static PropellerArchiveCtCpJOpenFoamValidationPlan.OpenFoamValidationCase validationTargetOrNull(
			String presetName,
			String caseName
	) {
		try {
			return PropellerArchiveCtCpJOpenFoamValidationPlan.caseRow(presetName, caseName);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static List<Map<String, String>> parseCsv(String inputCsv) {
		List<List<String>> rawRows = new ArrayList<>();
		for (String line : inputCsv.split("\\R")) {
			if (line.isBlank()) {
				continue;
			}
			rawRows.add(parseCsvLine(line));
		}
		if (rawRows.isEmpty()) {
			return List.of();
		}
		List<String> header = rawRows.get(0).stream()
				.map(CtCpJOpenFoamDimensionalComparisonImporter::normalizeHeader)
				.toList();
		List<Map<String, String>> records = new ArrayList<>();
		for (int rowIndex = 1; rowIndex < rawRows.size(); rowIndex++) {
			List<String> rawRow = rawRows.get(rowIndex);
			Map<String, String> record = new LinkedHashMap<>();
			for (int column = 0; column < header.size(); column++) {
				String value = column < rawRow.size() ? rawRow.get(column).trim() : "";
				record.put(header.get(column), value);
			}
			records.add(record);
		}
		return records;
	}

	private static List<String> parseCsvLine(String line) {
		List<String> cells = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (quoted) {
				if (c == '"') {
					if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
						cell.append('"');
						i++;
					} else {
						quoted = false;
					}
				} else {
					cell.append(c);
				}
			} else if (c == ',') {
				cells.add(cell.toString());
				cell.setLength(0);
			} else if (c == '"') {
				quoted = true;
			} else {
				cell.append(c);
			}
		}
		cells.add(cell.toString());
		return cells;
	}

	private static String csvLine(ComparisonRow row) {
		PropellerArchiveCtCpJLookupEvaluator.RotorDimensionalSample reference = row.reference();
		PropellerArchiveCtCpJLookupEvaluator.LookupResult lookup = reference.lookup();
		CfdDimensionalRow cfd = row.cfd();
		return String.join(",",
				escape(cfd.presetName()),
				escape(cfd.caseName()),
				number(cfd.queryAdvanceRatioJ()),
				number(cfd.queryRpm()),
				number(cfd.airDensityKgPerCubicMeter()),
				number(cfd.propellerDiameterMeters()),
				escape(lookup.dataSourceId()),
				escape(lookup.status()),
				Boolean.toString(lookup.blocked()),
				escape(cfd.solverStatus()),
				escape(cfd.sourceCaseSha256()),
				number(lookup.thrustCoefficientCt()),
				number(row.cfdThrustCoefficientCt()),
				number(row.ctResidual()),
				number(row.ctResidualFraction()),
				number(lookup.powerCoefficientCp()),
				number(row.cfdPowerCoefficientCp()),
				number(row.cpResidual()),
				number(row.cpResidualFraction()),
				number(lookup.propulsiveEfficiencyEta()),
				number(row.cfdPropulsiveEfficiencyEta()),
				number(row.etaResidual()),
				number(reference.thrustNewtons()),
				number(cfd.cfdThrustNewtons()),
				number(row.thrustResidualNewtons()),
				number(row.thrustResidualFraction()),
				number(reference.shaftPowerWatts()),
				number(cfd.cfdShaftPowerWatts()),
				number(row.powerResidualWatts()),
				number(row.powerResidualFraction()),
				number(reference.shaftTorqueNewtonMeters()),
				number(cfd.cfdShaftTorqueNewtonMeters()),
				number(row.torqueResidualNewtonMeters()),
				number(row.torqueResidualFraction()),
				number(row.cfdTorqueFromPowerNewtonMeters()),
				number(row.cfdPowerTorqueResidualNewtonMeters()),
				number(reference.idealInducedVelocityMetersPerSecond()),
				number(cfd.cfdInducedVelocityMetersPerSecond()),
				number(row.inducedVelocityResidualMetersPerSecond()),
				number(reference.idealMomentumPowerWatts()),
				number(cfd.cfdMomentumPowerWatts()),
				number(row.momentumPowerResidualWatts()),
				Boolean.toString(row.comparable()),
				escape(row.message())
		);
	}

	private static String normalizeHeader(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
			normalized = normalized.substring(1);
		}
		return normalized;
	}

	private static String text(Map<String, String> record, String name, String fallback) {
		String value = record.getOrDefault(name, "");
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static double requiredDouble(Map<String, String> record, String name) {
		String value = record.get(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("required CSV column is missing or blank: " + name);
		}
		return Double.parseDouble(value);
	}

	private static double optionalDouble(Map<String, String> record, String name, double fallback) {
		String value = record.get(name);
		return value == null || value.isBlank() ? fallback : Double.parseDouble(value);
	}

	private static double thrustCoefficient(
			double thrustNewtons,
			double airDensityKgPerCubicMeter,
			double revolutionsPerSecond,
			double propellerDiameterMeters
	) {
		double denominator = airDensityKgPerCubicMeter
				* revolutionsPerSecond
				* revolutionsPerSecond
				* Math.pow(propellerDiameterMeters, 4.0);
		return ratio(thrustNewtons, denominator);
	}

	private static double powerCoefficient(
			double powerWatts,
			double airDensityKgPerCubicMeter,
			double revolutionsPerSecond,
			double propellerDiameterMeters
	) {
		double denominator = airDensityKgPerCubicMeter
				* Math.pow(revolutionsPerSecond, 3.0)
				* Math.pow(propellerDiameterMeters, 5.0);
		return ratio(powerWatts, denominator);
	}

	private static double eta(double advanceRatioJ, double thrustCoefficientCt, double powerCoefficientCp) {
		return Double.isFinite(powerCoefficientCp) && powerCoefficientCp > EPSILON
				? advanceRatioJ * thrustCoefficientCt / powerCoefficientCp
				: 0.0;
	}

	private static double momentumPower(
			double thrustNewtons,
			double advanceRatioJ,
			double revolutionsPerSecond,
			double propellerDiameterMeters,
			double inducedVelocityMetersPerSecond
	) {
		if (!Double.isFinite(inducedVelocityMetersPerSecond)) {
			return Double.NaN;
		}
		double axialSpeed = advanceRatioJ * revolutionsPerSecond * propellerDiameterMeters;
		return thrustNewtons * (Math.max(0.0, axialSpeed) + inducedVelocityMetersPerSecond);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return Double.NaN;
		}
		return numerator / denominator;
	}

	private static DroneConfig configForPreset(String presetName) {
		return switch (presetName) {
			case "apDrone" -> DroneConfig.apDrone();
			case "racingQuad" -> DroneConfig.racingQuad();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavyLift" -> DroneConfig.heavyLift();
			default -> throw new IllegalArgumentException("unknown DroneConfig preset: " + presetName);
		};
	}

	private static String number(double value) {
		return Double.isFinite(value) ? String.format(Locale.ROOT, "%.15g", value) : "";
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
}
