package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CtCpJActuatorDiskWakeProbeComparisonImporter {
	private static final String HEADER = String.join(",",
			"preset",
			"case",
			"row_kind",
			"rotor_index",
			"source_name",
			"probe_kind",
			"probe_distance_radius",
			"reference_lookup_status",
			"reference_clamped",
			"reference_blocked",
			"reference_source_enabled",
			"cfd_solver_status",
			"source_case_sha256",
			"reference_probe_point_world_x_m",
			"reference_probe_point_world_y_m",
			"reference_probe_point_world_z_m",
			"cfd_probe_point_world_x_m",
			"cfd_probe_point_world_y_m",
			"cfd_probe_point_world_z_m",
			"probe_point_residual_world_x_m",
			"probe_point_residual_world_y_m",
			"probe_point_residual_world_z_m",
			"probe_point_residual_magnitude_m",
			"reference_expected_velocity_world_x_mps",
			"reference_expected_velocity_world_y_mps",
			"reference_expected_velocity_world_z_mps",
			"cfd_probe_velocity_world_x_mps",
			"cfd_probe_velocity_world_y_mps",
			"cfd_probe_velocity_world_z_mps",
			"cfd_probe_p_field",
			"probe_velocity_residual_world_x_mps",
			"probe_velocity_residual_world_y_mps",
			"probe_velocity_residual_world_z_mps",
			"probe_velocity_residual_magnitude_mps",
			"probe_velocity_residual_fraction",
			"reference_expected_axial_velocity_mps",
			"cfd_probe_axial_velocity_mps",
			"axial_velocity_residual_mps",
			"axial_velocity_residual_fraction",
			"reference_expected_transverse_velocity_mps",
			"cfd_probe_transverse_velocity_mps",
			"transverse_velocity_residual_mps",
			"reference_expected_speed_mps",
			"cfd_probe_speed_mps",
			"speed_residual_mps",
			"speed_residual_fraction",
			"reference_pressure_jump_pa",
			"reference_mass_flux_kg_s_m2",
			"reference_ideal_momentum_power_loading_w_m2",
			"query_j",
			"query_rpm",
			"effective_j",
			"effective_rpm",
			"ct",
			"cp",
			"eta",
			"comparable",
			"message"
	);
	private static final double EPSILON = 1.0e-12;

	private CtCpJActuatorDiskWakeProbeComparisonImporter() {
	}

	public record CfdWakeProbeRow(
			String presetName,
			String caseName,
			String rowKind,
			int rotorIndex,
			String probeKind,
			double probeDistanceRadius,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			Vec3 cfdProbePointWorldMeters,
			Vec3 cfdProbeVelocityWorldMetersPerSecond,
			double cfdProbePField,
			String sourceCaseSha256,
			String solverStatus
	) {
		public CfdWakeProbeRow {
			presetName = presetName == null || presetName.isBlank()
					? PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME
					: presetName.trim();
			caseName = caseName == null ? "" : caseName.trim();
			if (caseName.isBlank()) {
				throw new IllegalArgumentException("case must not be blank.");
			}
			rowKind = rowKind == null || rowKind.isBlank() ? "raw_source" : rowKind.trim();
			rotorIndex = Math.max(0, rotorIndex);
			probeKind = probeKind == null || probeKind.isBlank() ? "centerline_axial" : probeKind.trim();
			if (!Double.isFinite(probeDistanceRadius) || probeDistanceRadius <= 0.0) {
				throw new IllegalArgumentException("probeDistanceRadius must be finite and positive.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (!Double.isFinite(sourceThicknessMeters) || sourceThicknessMeters <= 0.0) {
				throw new IllegalArgumentException("sourceThicknessMeters must be finite and positive.");
			}
			cfdProbePointWorldMeters = cfdProbePointWorldMeters == null
					? nanVector()
					: cfdProbePointWorldMeters;
			cfdProbeVelocityWorldMetersPerSecond = cfdProbeVelocityWorldMetersPerSecond == null
					? nanVector()
					: cfdProbeVelocityWorldMetersPerSecond;
			if (!Double.isFinite(cfdProbePField)) {
				cfdProbePField = Double.NaN;
			}
			sourceCaseSha256 = sourceCaseSha256 == null ? "" : sourceCaseSha256.trim();
			solverStatus = solverStatus == null || solverStatus.isBlank() ? "UNSPECIFIED" : solverStatus.trim();
		}
	}

	public record ReferenceWakeProbeRow(
			String presetName,
			String caseName,
			String rowKind,
			int rotorIndex,
			String sourceName,
			boolean sourceEnabled,
			String lookupStatus,
			boolean clamped,
			boolean blocked,
			String probeKind,
			double probeDistanceRadius,
			double probeDistanceMeters,
			Vec3 probePointWorldMeters,
			Vec3 diskNormalWorld,
			Vec3 expectedFarWakeVelocityWorldMetersPerSecond,
			double expectedFarWakeSpeedMetersPerSecond,
			double pressureJumpPascals,
			double massFluxKilogramsPerSecondSquareMeter,
			double idealMomentumPowerLoadingWattsPerSquareMeter,
			double queryJ,
			double queryRpm,
			double effectiveJ,
			double effectiveRpm,
			double ct,
			double cp,
			double eta
	) {
	}

	public record ComparisonRow(
			CfdWakeProbeRow cfd,
			ReferenceWakeProbeRow reference,
			Vec3 probePointResidualWorldMeters,
			Vec3 probeVelocityResidualWorldMetersPerSecond,
			double probeVelocityResidualFraction,
			double referenceExpectedAxialVelocityMetersPerSecond,
			double cfdProbeAxialVelocityMetersPerSecond,
			double axialVelocityResidualMetersPerSecond,
			double axialVelocityResidualFraction,
			double referenceExpectedTransverseVelocityMetersPerSecond,
			double cfdProbeTransverseVelocityMetersPerSecond,
			double transverseVelocityResidualMetersPerSecond,
			double cfdProbeSpeedMetersPerSecond,
			double speedResidualMetersPerSecond,
			double speedResidualFraction,
			boolean comparable,
			String message
	) {
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 2 || args.length > 4) {
			throw new IllegalArgumentException(
					"usage: CtCpJActuatorDiskWakeProbeComparisonImporter "
							+ "<input.csv> <output.csv> [defaultDensityKgM3] [defaultSourceThicknessM]");
		}
		double defaultDensity = args.length >= 3 && !args[2].isBlank()
				? Double.parseDouble(args[2])
				: PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double defaultSourceThickness = args.length >= 4 && !args[3].isBlank()
				? Double.parseDouble(args[3])
				: CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;
		write(Path.of(args[0]), Path.of(args[1]), defaultDensity, defaultSourceThickness);
	}

	public static void write(
			Path input,
			Path output,
			double defaultAirDensityKgPerCubicMeter,
			double defaultSourceThicknessMeters
	) throws IOException {
		if (input == null || output == null) {
			throw new IllegalArgumentException("input and output paths must not be null.");
		}
		List<String> lines = csvLines(
				Files.readString(input, StandardCharsets.UTF_8),
				defaultAirDensityKgPerCubicMeter,
				defaultSourceThicknessMeters
		);
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}

	public static List<String> csvLines(
			String inputCsv,
			double defaultAirDensityKgPerCubicMeter,
			double defaultSourceThicknessMeters
	) {
		List<ComparisonRow> rows = compare(inputCsv, defaultAirDensityKgPerCubicMeter, defaultSourceThicknessMeters);
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (ComparisonRow row : rows) {
			lines.add(csvLine(row));
		}
		return List.copyOf(lines);
	}

	public static List<ComparisonRow> compare(
			String inputCsv,
			double defaultAirDensityKgPerCubicMeter,
			double defaultSourceThicknessMeters
	) {
		if (inputCsv == null) {
			throw new IllegalArgumentException("inputCsv must not be null.");
		}
		if (!Double.isFinite(defaultAirDensityKgPerCubicMeter) || defaultAirDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("defaultAirDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(defaultSourceThicknessMeters) || defaultSourceThicknessMeters <= 0.0) {
			throw new IllegalArgumentException("defaultSourceThicknessMeters must be finite and positive.");
		}
		List<CfdWakeProbeRow> cfdRows = parseCsv(inputCsv).stream()
				.map(record -> cfdRow(record, defaultAirDensityKgPerCubicMeter, defaultSourceThicknessMeters))
				.toList();
		Map<ReferenceContextKey, Map<ReferenceKey, ReferenceWakeProbeRow>> referenceCache =
				new LinkedHashMap<>();
		List<ComparisonRow> rows = new ArrayList<>();
		for (CfdWakeProbeRow cfdRow : cfdRows) {
			Map<ReferenceKey, ReferenceWakeProbeRow> references = referenceCache.computeIfAbsent(
					ReferenceContextKey.from(cfdRow),
					ignored -> referenceRows(cfdRow)
			);
			ReferenceWakeProbeRow reference = references.get(ReferenceKey.from(cfdRow));
			if (reference == null) {
				throw new IllegalArgumentException("no wake probe reference row for "
						+ cfdRow.presetName() + "/" + cfdRow.caseName() + "/" + cfdRow.rowKind()
						+ "/" + cfdRow.rotorIndex() + "/" + cfdRow.probeKind()
						+ "/" + cfdRow.probeDistanceRadius());
			}
			rows.add(compare(cfdRow, reference));
		}
		return List.copyOf(rows);
	}

	public static ComparisonRow compare(CfdWakeProbeRow cfd) {
		if (cfd == null) {
			throw new IllegalArgumentException("cfd row must not be null.");
		}
		return compare(cfd, referenceRow(cfd));
	}

	private static ComparisonRow compare(CfdWakeProbeRow cfd, ReferenceWakeProbeRow reference) {
		Vec3 pointResidual = finiteVector(cfd.cfdProbePointWorldMeters())
				? cfd.cfdProbePointWorldMeters().subtract(reference.probePointWorldMeters())
				: nanVector();
		Vec3 velocityResidual = finiteVector(cfd.cfdProbeVelocityWorldMetersPerSecond())
				? cfd.cfdProbeVelocityWorldMetersPerSecond()
						.subtract(reference.expectedFarWakeVelocityWorldMetersPerSecond())
				: nanVector();
		double referenceAxial = reference.expectedFarWakeVelocityWorldMetersPerSecond()
				.dot(reference.diskNormalWorld());
		double cfdAxial = finiteVector(cfd.cfdProbeVelocityWorldMetersPerSecond())
				? cfd.cfdProbeVelocityWorldMetersPerSecond().dot(reference.diskNormalWorld())
				: Double.NaN;
		double axialResidual = cfdAxial - referenceAxial;
		double referenceTransverse = transverseMagnitude(
				reference.expectedFarWakeVelocityWorldMetersPerSecond(),
				reference.diskNormalWorld()
		);
		double cfdTransverse = finiteVector(cfd.cfdProbeVelocityWorldMetersPerSecond())
				? transverseMagnitude(cfd.cfdProbeVelocityWorldMetersPerSecond(), reference.diskNormalWorld())
				: Double.NaN;
		double cfdSpeed = finiteVector(cfd.cfdProbeVelocityWorldMetersPerSecond())
				? cfd.cfdProbeVelocityWorldMetersPerSecond().length()
				: Double.NaN;
		double speedResidual = cfdSpeed - reference.expectedFarWakeSpeedMetersPerSecond();
		boolean comparable = finiteVector(cfd.cfdProbeVelocityWorldMetersPerSecond());
		String message = comparable
				? "ct-cp-j-actuator-disk-wake-probe-comparison-ready"
				: "cfd-row-missing-finite-probe-velocity";
		return new ComparisonRow(
				cfd,
				reference,
				pointResidual,
				velocityResidual,
				ratio(velocityResidual.length(), reference.expectedFarWakeSpeedMetersPerSecond()),
				referenceAxial,
				cfdAxial,
				axialResidual,
				ratio(axialResidual, referenceAxial),
				referenceTransverse,
				cfdTransverse,
				cfdTransverse - referenceTransverse,
				cfdSpeed,
				speedResidual,
				ratio(speedResidual, reference.expectedFarWakeSpeedMetersPerSecond()),
				comparable,
				message
		);
	}

	private record ReferenceContextKey(String presetName, double airDensityKgPerCubicMeter, double sourceThicknessMeters) {
		static ReferenceContextKey from(CfdWakeProbeRow row) {
			return new ReferenceContextKey(
					row.presetName(),
					row.airDensityKgPerCubicMeter(),
					row.sourceThicknessMeters()
			);
		}
	}

	private record ReferenceKey(
			String caseName,
			String rowKind,
			int rotorIndex,
			String probeKind,
			String probeDistanceRadius
	) {
		static ReferenceKey from(CfdWakeProbeRow row) {
			return new ReferenceKey(
					row.caseName(),
					row.rowKind(),
					row.rotorIndex(),
					row.probeKind(),
					number(row.probeDistanceRadius())
			);
		}

		static ReferenceKey from(Map<String, String> record) {
			return new ReferenceKey(
					text(record, "case", ""),
					text(record, "row_kind", "raw_source"),
					(int) requiredDouble(record, "rotor_index"),
					text(record, "probe_kind", "centerline_axial"),
					number(requiredDouble(record, "probe_distance_radius"))
			);
		}
	}

	private static double transverseMagnitude(Vec3 velocity, Vec3 normal) {
		double axial = velocity.dot(normal);
		return velocity.subtract(normal.multiply(axial)).length();
	}

	private static CfdWakeProbeRow cfdRow(
			Map<String, String> record,
			double defaultAirDensityKgPerCubicMeter,
			double defaultSourceThicknessMeters
	) {
		return new CfdWakeProbeRow(
				text(record, "preset", PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME),
				text(record, "case", ""),
				text(record, "row_kind", "raw_source"),
				(int) requiredDouble(record, "rotor_index"),
				text(record, "probe_kind", "centerline_axial"),
				requiredDouble(record, "probe_distance_radius"),
				optionalDouble(record, "air_density_kg_m3", defaultAirDensityKgPerCubicMeter),
				optionalDouble(record, "source_thickness_m", defaultSourceThicknessMeters),
				vector(record,
						"cfd_probe_point_world_x_m",
						"cfd_probe_point_world_y_m",
						"cfd_probe_point_world_z_m",
						"probe_point_world_x_m",
						"probe_point_world_y_m",
						"probe_point_world_z_m"),
				cfdVelocityVector(record),
				optionalAnyDouble(record, Double.NaN,
						"cfd_probe_p_field",
						"cfd_probe_pressure_field",
						"probe_p_field",
						"sampled_p_field"),
				text(record, "source_case_sha256", text(record, "case_sha256", "")),
				text(record, "solver_status", "UNSPECIFIED")
		);
	}

	private static ReferenceWakeProbeRow referenceRow(CfdWakeProbeRow cfd) {
		ReferenceWakeProbeRow reference = referenceRows(cfd).get(ReferenceKey.from(cfd));
		if (reference != null) {
			return reference;
		}
		throw new IllegalArgumentException("no wake probe reference row for "
				+ cfd.presetName() + "/" + cfd.caseName() + "/" + cfd.rowKind()
				+ "/" + cfd.rotorIndex() + "/" + cfd.probeKind() + "/" + cfd.probeDistanceRadius());
	}

	private static Map<ReferenceKey, ReferenceWakeProbeRow> referenceRows(CfdWakeProbeRow cfd) {
		Map<ReferenceKey, ReferenceWakeProbeRow> rows = new LinkedHashMap<>();
		for (Map<String, String> record : parseCsv(String.join("\n",
				CtCpJActuatorDiskWakeProbeExporter.csvLines(
						cfd.presetName(),
						cfd.airDensityKgPerCubicMeter(),
						cfd.sourceThicknessMeters(),
						25.0,
						0.0)))) {
			rows.put(ReferenceKey.from(record), referenceRow(record));
		}
		return rows;
	}

	private static ReferenceWakeProbeRow referenceRow(Map<String, String> record) {
		return new ReferenceWakeProbeRow(
				text(record, "preset", PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME),
				text(record, "case", ""),
				text(record, "row_kind", "raw_source"),
				(int) requiredDouble(record, "rotor_index"),
				text(record, "source_name", ""),
				Boolean.parseBoolean(text(record, "source_enabled", "false")),
				text(record, "lookup_status", ""),
				Boolean.parseBoolean(text(record, "clamped", "false")),
				Boolean.parseBoolean(text(record, "blocked", "false")),
				text(record, "probe_kind", "centerline_axial"),
				requiredDouble(record, "probe_distance_radius"),
				requiredDouble(record, "probe_distance_m"),
				vector(record, "probe_point_world_x_m", "probe_point_world_y_m", "probe_point_world_z_m"),
				vector(record, "disk_normal_world_x", "disk_normal_world_y", "disk_normal_world_z"),
				vector(
						record,
						"expected_wake_velocity_world_x_mps",
						"expected_wake_velocity_world_y_mps",
						"expected_wake_velocity_world_z_mps",
						"expected_far_wake_velocity_world_x_mps",
						"expected_far_wake_velocity_world_y_mps",
						"expected_far_wake_velocity_world_z_mps"),
				optionalDouble(record, "expected_wake_speed_mps", "expected_far_wake_speed_mps", Double.NaN),
				requiredDouble(record, "pressure_jump_pa"),
				requiredDouble(record, "mass_flux_kg_s_m2"),
				requiredDouble(record, "ideal_momentum_power_loading_w_m2"),
				requiredDouble(record, "query_j"),
				requiredDouble(record, "query_rpm"),
				requiredDouble(record, "effective_j"),
				requiredDouble(record, "effective_rpm"),
				requiredDouble(record, "ct"),
				requiredDouble(record, "cp"),
				requiredDouble(record, "eta")
		);
	}

	private static String csvLine(ComparisonRow row) {
		CfdWakeProbeRow cfd = row.cfd();
		ReferenceWakeProbeRow reference = row.reference();
		return String.join(",",
				escape(cfd.presetName()),
				escape(cfd.caseName()),
				escape(cfd.rowKind()),
				Integer.toString(cfd.rotorIndex()),
				escape(reference.sourceName()),
				escape(cfd.probeKind()),
				number(cfd.probeDistanceRadius()),
				escape(reference.lookupStatus()),
				Boolean.toString(reference.clamped()),
				Boolean.toString(reference.blocked()),
				Boolean.toString(reference.sourceEnabled()),
				escape(cfd.solverStatus()),
				escape(cfd.sourceCaseSha256()),
				vec(reference.probePointWorldMeters()),
				vec(cfd.cfdProbePointWorldMeters()),
				vec(row.probePointResidualWorldMeters()),
				number(row.probePointResidualWorldMeters().length()),
				vec(reference.expectedFarWakeVelocityWorldMetersPerSecond()),
				vec(cfd.cfdProbeVelocityWorldMetersPerSecond()),
				number(cfd.cfdProbePField()),
				vec(row.probeVelocityResidualWorldMetersPerSecond()),
				number(row.probeVelocityResidualWorldMetersPerSecond().length()),
				number(row.probeVelocityResidualFraction()),
				number(row.referenceExpectedAxialVelocityMetersPerSecond()),
				number(row.cfdProbeAxialVelocityMetersPerSecond()),
				number(row.axialVelocityResidualMetersPerSecond()),
				number(row.axialVelocityResidualFraction()),
				number(row.referenceExpectedTransverseVelocityMetersPerSecond()),
				number(row.cfdProbeTransverseVelocityMetersPerSecond()),
				number(row.transverseVelocityResidualMetersPerSecond()),
				number(reference.expectedFarWakeSpeedMetersPerSecond()),
				number(row.cfdProbeSpeedMetersPerSecond()),
				number(row.speedResidualMetersPerSecond()),
				number(row.speedResidualFraction()),
				number(reference.pressureJumpPascals()),
				number(reference.massFluxKilogramsPerSecondSquareMeter()),
				number(reference.idealMomentumPowerLoadingWattsPerSquareMeter()),
				number(reference.queryJ()),
				number(reference.queryRpm()),
				number(reference.effectiveJ()),
				number(reference.effectiveRpm()),
				number(reference.ct()),
				number(reference.cp()),
				number(reference.eta()),
				Boolean.toString(row.comparable()),
				escape(row.message())
		);
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
				.map(CtCpJActuatorDiskWakeProbeComparisonImporter::normalizeHeader)
				.toList();
		List<Map<String, String>> records = new ArrayList<>();
		for (int row = 1; row < rawRows.size(); row++) {
			Map<String, String> record = new LinkedHashMap<>();
			List<String> cells = rawRows.get(row);
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

	private static Vec3 vector(Map<String, String> record, String x, String y, String z) {
		return new Vec3(
				optionalDouble(record, x, Double.NaN),
				optionalDouble(record, y, Double.NaN),
				optionalDouble(record, z, Double.NaN)
		);
	}

	private static Vec3 cfdVelocityVector(Map<String, String> record) {
		return new Vec3(
				optionalAnyDouble(record, Double.NaN,
						"cfd_probe_velocity_world_x_mps",
						"probe_velocity_world_x_mps",
						"sampled_velocity_world_x_mps",
						"expected_wake_velocity_world_x_mps",
						"expected_far_wake_velocity_world_x_mps"),
				optionalAnyDouble(record, Double.NaN,
						"cfd_probe_velocity_world_y_mps",
						"probe_velocity_world_y_mps",
						"sampled_velocity_world_y_mps",
						"expected_wake_velocity_world_y_mps",
						"expected_far_wake_velocity_world_y_mps"),
				optionalAnyDouble(record, Double.NaN,
						"cfd_probe_velocity_world_z_mps",
						"probe_velocity_world_z_mps",
						"sampled_velocity_world_z_mps",
						"expected_wake_velocity_world_z_mps",
						"expected_far_wake_velocity_world_z_mps")
		);
	}

	private static Vec3 vector(
			Map<String, String> record,
			String x,
			String y,
			String z,
			String fallbackX,
			String fallbackY,
			String fallbackZ
	) {
		return new Vec3(
				optionalDouble(record, x, fallbackX, Double.NaN),
				optionalDouble(record, y, fallbackY, Double.NaN),
				optionalDouble(record, z, fallbackZ, Double.NaN)
		);
	}

	private static Vec3 vector(
			Map<String, String> record,
			String x,
			String y,
			String z,
			String fallbackX,
			String fallbackY,
			String fallbackZ,
			String secondFallbackX,
			String secondFallbackY,
			String secondFallbackZ,
			String thirdFallbackX,
			String thirdFallbackY,
			String thirdFallbackZ
	) {
		return new Vec3(
				optionalDouble(record, x, fallbackX, secondFallbackX, thirdFallbackX, Double.NaN),
				optionalDouble(record, y, fallbackY, secondFallbackY, thirdFallbackY, Double.NaN),
				optionalDouble(record, z, fallbackZ, secondFallbackZ, thirdFallbackZ, Double.NaN)
		);
	}

	private static double optionalDouble(
			Map<String, String> record,
			String name,
			String fallbackName,
			double fallback
	) {
		String value = record.get(name);
		if (value != null && !value.isBlank()) {
			return Double.parseDouble(value);
		}
		return optionalDouble(record, fallbackName, fallback);
	}

	private static double optionalDouble(
			Map<String, String> record,
			String name,
			String fallbackName,
			String secondFallbackName,
			String thirdFallbackName,
			double fallback
	) {
		String value = record.get(name);
		if (value != null && !value.isBlank()) {
			return Double.parseDouble(value);
		}
		value = record.get(fallbackName);
		if (value != null && !value.isBlank()) {
			return Double.parseDouble(value);
		}
		value = record.get(secondFallbackName);
		if (value != null && !value.isBlank()) {
			return Double.parseDouble(value);
		}
		return optionalDouble(record, thirdFallbackName, fallback);
	}

	private static double optionalAnyDouble(Map<String, String> record, double fallback, String... names) {
		for (String name : names) {
			String value = record.get(name);
			if (value != null && !value.isBlank()) {
				return Double.parseDouble(value);
			}
		}
		return fallback;
	}

	private static boolean finiteVector(Vec3 value) {
		return value != null && value.isFinite();
	}

	private static Vec3 nanVector() {
		return new Vec3(Double.NaN, Double.NaN, Double.NaN);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return Double.NaN;
		}
		return numerator / denominator;
	}

	private static String vec(Vec3 value) {
		return String.join(",", number(value.x()), number(value.y()), number(value.z()));
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
