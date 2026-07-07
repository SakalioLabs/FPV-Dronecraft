package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class CtCpJActuatorDiskOpenFoamRawSetImporter {
	private static final String CENTERLINE_HEADER = String.join(",",
			"preset",
			"case",
			"row_kind",
			"rotor_index",
			"probe_kind",
			"probe_distance_radius",
			"air_density_kg_m3",
			"source_thickness_m",
			"cfd_probe_point_world_x_m",
			"cfd_probe_point_world_y_m",
			"cfd_probe_point_world_z_m",
			"cfd_probe_velocity_world_x_mps",
			"cfd_probe_velocity_world_y_mps",
			"cfd_probe_velocity_world_z_mps",
			"source_case_sha256",
			"solver_status"
	);
	private static final String WAKE_PLANE_HEADER = String.join(",",
			"preset",
			"case",
			"row_kind",
			"rotor_index",
			"probe_kind",
			"plane_sample",
			"probe_distance_radius",
			"air_density_kg_m3",
			"source_thickness_m",
			"cfd_probe_point_world_x_m",
			"cfd_probe_point_world_y_m",
			"cfd_probe_point_world_z_m",
			"cfd_probe_velocity_world_x_mps",
			"cfd_probe_velocity_world_y_mps",
			"cfd_probe_velocity_world_z_mps",
			"source_case_sha256",
			"solver_status"
	);

	private CtCpJActuatorDiskOpenFoamRawSetImporter() {
	}

	public record ImportedRawSetCsv(
			List<String> centerlineCsvLines,
			List<String> wakePlaneCsvLines
	) {
		public ImportedRawSetCsv {
			centerlineCsvLines = List.copyOf(centerlineCsvLines);
			wakePlaneCsvLines = List.copyOf(wakePlaneCsvLines);
		}
	}

	private record RawVectorSample(Vec3 pointWorldMeters, Vec3 velocityWorldMetersPerSecond) {
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 4 || args.length > 8) {
			throw new IllegalArgumentException(
					"usage: CtCpJActuatorDiskOpenFoamRawSetImporter "
							+ "<preset> <rawSetDirectory> <centerlineOutput.csv> <wakePlaneOutput.csv> "
							+ "[defaultDensityKgM3] [defaultSourceThicknessM] [solverStatus] [sourceCaseSha256]");
		}
		String presetName = args[0].isBlank()
				? PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME
				: args[0];
		Path rawSetDirectory = Path.of(args[1]);
		Path centerlineOutput = Path.of(args[2]);
		Path wakePlaneOutput = Path.of(args[3]);
		double defaultDensity = args.length >= 5 && !args[4].isBlank()
				? Double.parseDouble(args[4])
				: PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double defaultSourceThickness = args.length >= 6 && !args[5].isBlank()
				? Double.parseDouble(args[5])
				: CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;
		String solverStatus = args.length >= 7 && !args[6].isBlank()
				? args[6]
				: "OPENFOAM_RAW_SET_IMPORTED";
		String sourceCaseSha256 = args.length >= 8 ? args[7] : "";
		write(
				presetName,
				rawSetDirectory,
				centerlineOutput,
				wakePlaneOutput,
				defaultDensity,
				defaultSourceThickness,
				solverStatus,
				sourceCaseSha256
		);
	}

	public static void write(
			String presetName,
			Path rawSetDirectory,
			Path centerlineOutput,
			Path wakePlaneOutput,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			String solverStatus,
			String sourceCaseSha256
	) throws IOException {
		if (centerlineOutput == null || wakePlaneOutput == null) {
			throw new IllegalArgumentException("output paths must not be null.");
		}
		ImportedRawSetCsv imported = importRawSets(
				presetName,
				rawSetDirectory,
				airDensityKgPerCubicMeter,
				sourceThicknessMeters,
				solverStatus,
				sourceCaseSha256
		);
		writeLines(centerlineOutput, imported.centerlineCsvLines());
		writeLines(wakePlaneOutput, imported.wakePlaneCsvLines());
	}

	public static ImportedRawSetCsv importRawSets(
			String presetName,
			Path rawSetDirectory,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			String solverStatus,
			String sourceCaseSha256
	) throws IOException {
		if (rawSetDirectory == null || !Files.isDirectory(rawSetDirectory)) {
			throw new IllegalArgumentException("rawSetDirectory must be an existing directory.");
		}
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(sourceThicknessMeters) || sourceThicknessMeters <= 0.0) {
			throw new IllegalArgumentException("sourceThicknessMeters must be finite and positive.");
		}
		String normalizedPreset = presetName == null || presetName.isBlank()
				? PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME
				: presetName.trim();
		String normalizedSolverStatus = solverStatus == null || solverStatus.isBlank()
				? "OPENFOAM_RAW_SET_IMPORTED"
				: solverStatus.trim();
		String normalizedSha = sourceCaseSha256 == null ? "" : sourceCaseSha256.trim();
		List<Map<String, String>> centerlineRows = parseCsv(String.join("\n",
				CtCpJActuatorDiskWakeProbeExporter.csvLines(
						normalizedPreset,
						airDensityKgPerCubicMeter,
						sourceThicknessMeters,
						25.0,
						0.0
				)));
		List<Map<String, String>> wakePlaneRows = parseCsv(String.join("\n",
				CtCpJActuatorDiskWakePlaneProbeExporter.csvLines(
						normalizedPreset,
						airDensityKgPerCubicMeter,
						sourceThicknessMeters,
						25.0,
						0.0
				)));
		List<String> centerlineCsv = new ArrayList<>();
		List<String> wakePlaneCsv = new ArrayList<>();
		centerlineCsv.add(CENTERLINE_HEADER);
		wakePlaneCsv.add(WAKE_PLANE_HEADER);
		appendCenterlineRows(
				centerlineCsv,
				groupBySource(centerlineRows),
				rawSetDirectory,
				airDensityKgPerCubicMeter,
				sourceThicknessMeters,
				normalizedSolverStatus,
				normalizedSha
		);
		appendWakePlaneRows(
				wakePlaneCsv,
				groupBySource(wakePlaneRows),
				rawSetDirectory,
				airDensityKgPerCubicMeter,
				sourceThicknessMeters,
				normalizedSolverStatus,
				normalizedSha
		);
		return new ImportedRawSetCsv(centerlineCsv, wakePlaneCsv);
	}

	private static void appendCenterlineRows(
			List<String> lines,
			Map<String, List<Map<String, String>>> rowsBySource,
			Path rawSetDirectory,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			String solverStatus,
			String sourceCaseSha256
	) throws IOException {
		for (Map.Entry<String, List<Map<String, String>>> entry : rowsBySource.entrySet()) {
			List<Map<String, String>> rows = entry.getValue();
			List<RawVectorSample> samples = readRawVectorSamples(rawSetDirectory, sanitize(entry.getKey() + "_centerline"));
			if (samples.size() != rows.size()) {
				throw new IllegalArgumentException("raw centerline set " + entry.getKey()
						+ " contains " + samples.size() + " samples, expected " + rows.size());
			}
			for (int index = 0; index < rows.size(); index++) {
				lines.add(centerlineCsvLine(
						rows.get(index),
						samples.get(index),
						airDensityKgPerCubicMeter,
						sourceThicknessMeters,
						solverStatus,
						sourceCaseSha256
				));
			}
		}
	}

	private static void appendWakePlaneRows(
			List<String> lines,
			Map<String, List<Map<String, String>>> rowsBySource,
			Path rawSetDirectory,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			String solverStatus,
			String sourceCaseSha256
	) throws IOException {
		for (Map.Entry<String, List<Map<String, String>>> entry : rowsBySource.entrySet()) {
			List<Map<String, String>> rows = entry.getValue();
			List<RawVectorSample> samples = readRawVectorSamples(rawSetDirectory, sanitize(entry.getKey() + "_wake_plane"));
			if (samples.size() != rows.size()) {
				throw new IllegalArgumentException("raw wake-plane set " + entry.getKey()
						+ " contains " + samples.size() + " samples, expected " + rows.size());
			}
			for (int index = 0; index < rows.size(); index++) {
				lines.add(wakePlaneCsvLine(
						rows.get(index),
						samples.get(index),
						airDensityKgPerCubicMeter,
						sourceThicknessMeters,
						solverStatus,
						sourceCaseSha256
				));
			}
		}
	}

	private static List<RawVectorSample> readRawVectorSamples(Path rawSetDirectory, String setName) throws IOException {
		Path rawFile = findRawVectorFile(rawSetDirectory, setName);
		List<RawVectorSample> samples = new ArrayList<>();
		for (String line : Files.readAllLines(rawFile, StandardCharsets.UTF_8)) {
			if (line == null) {
				continue;
			}
			String trimmed = stripBom(line.trim());
			if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
				continue;
			}
			List<Double> numbers = numbers(trimmed);
			if (numbers.size() < 6) {
				throw new IllegalArgumentException("raw vector set line must contain at least six numbers: " + line);
			}
			samples.add(new RawVectorSample(
					new Vec3(numbers.get(0), numbers.get(1), numbers.get(2)),
					new Vec3(numbers.get(3), numbers.get(4), numbers.get(5))
			));
		}
		return List.copyOf(samples);
	}

	private static String stripBom(String value) {
		return value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF'
				? value.substring(1)
				: value;
	}

	private static Path findRawVectorFile(Path rawSetDirectory, String setName) throws IOException {
		for (String suffix : List.of("_U.xy", "_U.raw", "_U.dat", "_U.csv", "_U")) {
			Path direct = rawSetDirectory.resolve(setName + suffix);
			if (Files.isRegularFile(direct)) {
				return direct;
			}
		}
		try (Stream<Path> paths = Files.walk(rawSetDirectory)) {
			List<Path> matches = paths
					.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().startsWith(setName + "_U"))
					.sorted(Comparator.comparing(Path::toString))
					.toList();
			if (matches.isEmpty()) {
				throw new IllegalArgumentException("missing OpenFOAM raw U set file for " + setName);
			}
			if (matches.size() > 1) {
				throw new IllegalArgumentException("multiple OpenFOAM raw U set files for " + setName
						+ "; pass a single postProcessing/sets time directory.");
			}
			return matches.get(0);
		}
	}

	private static List<Double> numbers(String line) {
		String[] tokens = line
				.replace('(', ' ')
				.replace(')', ' ')
				.replace(',', ' ')
				.trim()
				.split("\\s+");
		List<Double> numbers = new ArrayList<>();
		for (String token : tokens) {
			if (!token.isBlank()) {
				numbers.add(Double.parseDouble(token));
			}
		}
		return numbers;
	}

	private static String centerlineCsvLine(
			Map<String, String> row,
			RawVectorSample sample,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			String solverStatus,
			String sourceCaseSha256
	) {
		return String.join(",",
				escape(text(row, "preset")),
				escape(text(row, "case")),
				escape(text(row, "row_kind")),
				text(row, "rotor_index"),
				escape(text(row, "probe_kind")),
				number(number(row, "probe_distance_radius")),
				number(airDensityKgPerCubicMeter),
				number(sourceThicknessMeters),
				vec(sample.pointWorldMeters()),
				vec(sample.velocityWorldMetersPerSecond()),
				escape(sourceCaseSha256),
				escape(solverStatus)
		);
	}

	private static String wakePlaneCsvLine(
			Map<String, String> row,
			RawVectorSample sample,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			String solverStatus,
			String sourceCaseSha256
	) {
		return String.join(",",
				escape(text(row, "preset")),
				escape(text(row, "case")),
				escape(text(row, "row_kind")),
				text(row, "rotor_index"),
				escape(text(row, "probe_kind")),
				escape(text(row, "plane_sample")),
				number(number(row, "probe_distance_radius")),
				number(airDensityKgPerCubicMeter),
				number(sourceThicknessMeters),
				vec(sample.pointWorldMeters()),
				vec(sample.velocityWorldMetersPerSecond()),
				escape(sourceCaseSha256),
				escape(solverStatus)
		);
	}

	private static void writeLines(Path output, List<String> lines) throws IOException {
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}

	private static Map<String, List<Map<String, String>>> groupBySource(List<Map<String, String>> rows) {
		Map<String, List<Map<String, String>>> grouped = new LinkedHashMap<>();
		for (Map<String, String> row : rows) {
			grouped.computeIfAbsent(required(row, "source_name"), ignored -> new ArrayList<>()).add(row);
		}
		return grouped;
	}

	private static List<Map<String, String>> parseCsv(String inputCsv) {
		List<List<String>> rawRows = new ArrayList<>();
		for (String line : inputCsv.split("\\R")) {
			if (line == null || line.isBlank()) {
				continue;
			}
			rawRows.add(parseCsvLine(line));
		}
		if (rawRows.isEmpty()) {
			return List.of();
		}
		List<String> header = rawRows.get(0).stream()
				.map(CtCpJActuatorDiskOpenFoamRawSetImporter::normalizeHeader)
				.toList();
		List<Map<String, String>> records = new ArrayList<>();
		for (int rowIndex = 1; rowIndex < rawRows.size(); rowIndex++) {
			Map<String, String> record = new LinkedHashMap<>();
			List<String> cells = rawRows.get(rowIndex);
			for (int column = 0; column < header.size(); column++) {
				record.put(header.get(column), column < cells.size() ? cells.get(column).trim() : "");
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
			char ch = line.charAt(i);
			if (quoted) {
				if (ch == '"') {
					if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
						cell.append('"');
						i++;
					} else {
						quoted = false;
					}
				} else {
					cell.append(ch);
				}
			} else if (ch == '"') {
				quoted = true;
			} else if (ch == ',') {
				cells.add(cell.toString());
				cell.setLength(0);
			} else {
				cell.append(ch);
			}
		}
		cells.add(cell.toString());
		return cells;
	}

	private static String normalizeHeader(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
			normalized = normalized.substring(1);
		}
		return normalized;
	}

	private static String required(Map<String, String> row, String columnName) {
		String value = text(row, columnName);
		if (value.isBlank()) {
			throw new IllegalArgumentException("required column is blank: " + columnName);
		}
		return value;
	}

	private static String text(Map<String, String> row, String columnName) {
		return row.getOrDefault(columnName, "");
	}

	private static double number(Map<String, String> row, String columnName) {
		return Double.parseDouble(required(row, columnName));
	}

	private static String vec(Vec3 value) {
		return String.join(",", number(value.x()), number(value.y()), number(value.z()));
	}

	private static String number(double value) {
		return Double.isFinite(value) ? String.format(Locale.ROOT, "%.15g", value) : "";
	}

	private static String sanitize(String value) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char ch = Character.toLowerCase(value.charAt(i));
			if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
				builder.append(ch);
			} else if (builder.length() == 0 || builder.charAt(builder.length() - 1) != '_') {
				builder.append('_');
			}
		}
		while (builder.length() > 0 && builder.charAt(builder.length() - 1) == '_') {
			builder.setLength(builder.length() - 1);
		}
		return builder.toString();
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
