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

public final class CtCpJActuatorDiskOpenFoamSourceTableExporter {
	private static final String HEADER = String.join(",",
			"preset",
			"case",
			"row_kind",
			"rotor_index",
			"source_name",
			"source_enabled",
			"lookup_status",
			"clamped",
			"blocked",
			"selection_shape",
			"coordinate_frame",
			"center_x_m",
			"center_y_m",
			"center_z_m",
			"normal_x",
			"normal_y",
			"normal_z",
			"tangent_u_x",
			"tangent_u_y",
			"tangent_u_z",
			"tangent_v_x",
			"tangent_v_y",
			"tangent_v_z",
			"radius_m",
			"half_thickness_m",
			"axis_min_x_m",
			"axis_min_y_m",
			"axis_min_z_m",
			"axis_max_x_m",
			"axis_max_y_m",
			"axis_max_z_m",
			"bounding_sphere_radius_m",
			"source_volume_m3",
			"body_force_density_x_n_m3",
			"body_force_density_y_n_m3",
			"body_force_density_z_n_m3",
			"total_force_x_n",
			"total_force_y_n",
			"total_force_z_n",
			"pressure_jump_pa",
			"mass_flux_kg_s_m2",
			"ideal_momentum_power_loading_w_m2",
			"far_wake_axial_velocity_x_mps",
			"far_wake_axial_velocity_y_mps",
			"far_wake_axial_velocity_z_mps",
			"reaction_torque_x_nm",
			"reaction_torque_y_nm",
			"reaction_torque_z_nm",
			"wake_angular_momentum_torque_x_nm",
			"wake_angular_momentum_torque_y_nm",
			"wake_angular_momentum_torque_z_nm",
			"wake_angular_momentum_torque_density_x_nm_m3",
			"wake_angular_momentum_torque_density_y_nm_m3",
			"wake_angular_momentum_torque_density_z_nm_m3",
			"angular_momentum_swirl_radius_m",
			"wake_tangential_velocity_mps",
			"wake_swirl_support_radius_m",
			"wake_swirl_angular_velocity_rad_s",
			"wake_swirl_reference_point_x_m",
			"wake_swirl_reference_point_y_m",
			"wake_swirl_reference_point_z_m",
			"wake_swirl_velocity_x_mps",
			"wake_swirl_velocity_y_mps",
			"wake_swirl_velocity_z_mps",
			"wake_swirl_kinetic_power_w",
			"total_wake_kinetic_power_w",
			"wake_swirl_kinetic_power_over_shaft_power",
			"total_wake_kinetic_power_over_shaft_power",
			"query_j",
			"query_rpm",
			"effective_j",
			"effective_rpm",
			"ct",
			"cp",
			"eta"
	);

	private CtCpJActuatorDiskOpenFoamSourceTableExporter() {
	}

	public static void main(String[] args) throws IOException {
		String presetName = args.length >= 1 && !args[0].isBlank()
				? args[0]
				: PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME;
		Path output = args.length >= 2 && !args[1].isBlank()
				? Path.of(args[1])
				: Path.of("build", "ct-cp-j-actuator-disk-source-terms", presetName + "-openfoam-source-table.csv");
		double airDensity = args.length >= 3 && !args[2].isBlank()
				? Double.parseDouble(args[2])
				: PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double sourceThickness = args.length >= 4 && !args[3].isBlank()
				? Double.parseDouble(args[3])
				: CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;
		double ambientTemperatureCelsius = args.length >= 5 && !args[4].isBlank()
				? Double.parseDouble(args[4])
				: 25.0;
		double ambientHumidity = args.length >= 6 && !args[5].isBlank()
				? Double.parseDouble(args[5])
				: 0.0;
		write(
				presetName,
				output,
				airDensity,
				sourceThickness,
				ambientTemperatureCelsius,
				ambientHumidity
		);
	}

	public static void write(
			String presetName,
			Path output,
			double airDensityKgPerCubicMeter
	) throws IOException {
		write(
				presetName,
				output,
				airDensityKgPerCubicMeter,
				CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS,
				25.0,
				0.0
		);
	}

	public static void write(
			String presetName,
			Path output,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) throws IOException {
		if (output == null) {
			throw new IllegalArgumentException("output path must not be null.");
		}
		List<String> lines = csvLines(
				presetName,
				airDensityKgPerCubicMeter,
				sourceThicknessMeters,
				ambientTemperatureCelsius,
				ambientHumidity
		);
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}

	public static List<String> csvLines(
			String presetName,
			double airDensityKgPerCubicMeter
	) {
		return csvLines(
				presetName,
				airDensityKgPerCubicMeter,
				CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS,
				25.0,
				0.0
		);
	}

	public static List<String> csvLines(
			String presetName,
			double airDensityKgPerCubicMeter,
			double sourceThicknessMeters,
			double ambientTemperatureCelsius,
			double ambientHumidity
	) {
		List<Map<String, String>> sourceRows = parseCsv(String.join("\n",
				CtCpJActuatorDiskSourceTermExporter.csvLines(
						presetName,
						airDensityKgPerCubicMeter,
						sourceThicknessMeters,
						ambientTemperatureCelsius,
						ambientHumidity
				)));
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (Map<String, String> row : sourceRows) {
			lines.add(csvLine(row));
		}
		return List.copyOf(lines);
	}

	private static String csvLine(Map<String, String> row) {
		String preset = text(row, "preset");
		String caseName = text(row, "case");
		String rowKind = text(row, "row_kind");
		int rotorIndex = (int) number(row, "rotor_index");
		Vec3 bodyForceDensity = vector(
				row,
				"body_force_density_world_x_n_m3",
				"body_force_density_world_y_n_m3",
				"body_force_density_world_z_n_m3"
		);
		Vec3 totalForce = vector(
				row,
				"equivalent_body_force_integral_world_x_n",
				"equivalent_body_force_integral_world_y_n",
				"equivalent_body_force_integral_world_z_n"
		);
		Vec3 reactionTorque = vector(
				row,
				"reaction_torque_world_x_nm",
				"reaction_torque_world_y_nm",
				"reaction_torque_world_z_nm"
		);
		Vec3 wakeAngularMomentumTorque = vector(
				row,
				"wake_angular_momentum_torque_world_x_nm",
				"wake_angular_momentum_torque_world_y_nm",
				"wake_angular_momentum_torque_world_z_nm"
		);
		Vec3 wakeAngularMomentumTorqueDensity = vector(
				row,
				"wake_angular_momentum_torque_density_world_x_nm_m3",
				"wake_angular_momentum_torque_density_world_y_nm_m3",
				"wake_angular_momentum_torque_density_world_z_nm_m3"
		);
		double angularMomentumSwirlRadiusMeters = number(row, "angular_momentum_swirl_radius_m");
		double wakeTangentialVelocityMetersPerSecond = number(row, "wake_tangential_velocity_mps");
		double wakeSwirlSupportRadiusMeters = wakeSwirlSupportRadiusMeters(row);
		double wakeSwirlAngularVelocityRadiansPerSecond = wakeSwirlAngularVelocityRadiansPerSecond(
				Boolean.parseBoolean(text(row, "applied")),
				angularMomentumSwirlRadiusMeters,
				wakeTangentialVelocityMetersPerSecond,
				wakeSwirlSupportRadiusMeters
		);
		return String.join(",",
				escape(preset),
				escape(caseName),
				escape(rowKind),
				Integer.toString(rotorIndex),
				escape(sourceName(preset, caseName, rowKind, rotorIndex)),
				text(row, "applied"),
				escape(text(row, "lookup_status")),
				text(row, "clamped"),
				text(row, "blocked"),
				"cylindrical_slab",
				"world",
				value(row, "disk_center_world_x_m"),
				value(row, "disk_center_world_y_m"),
				value(row, "disk_center_world_z_m"),
				value(row, "disk_normal_world_x"),
				value(row, "disk_normal_world_y"),
				value(row, "disk_normal_world_z"),
				value(row, "disk_tangent_u_world_x"),
				value(row, "disk_tangent_u_world_y"),
				value(row, "disk_tangent_u_world_z"),
				value(row, "disk_tangent_v_world_x"),
				value(row, "disk_tangent_v_world_y"),
				value(row, "disk_tangent_v_world_z"),
				value(row, "disk_radius_m"),
				value(row, "source_half_thickness_m"),
				value(row, "source_axis_min_world_x_m"),
				value(row, "source_axis_min_world_y_m"),
				value(row, "source_axis_min_world_z_m"),
				value(row, "source_axis_max_world_x_m"),
				value(row, "source_axis_max_world_y_m"),
				value(row, "source_axis_max_world_z_m"),
				value(row, "source_bounding_sphere_radius_m"),
				value(row, "source_volume_m3"),
				number(bodyForceDensity.x()),
				number(bodyForceDensity.y()),
				number(bodyForceDensity.z()),
				number(totalForce.x()),
				number(totalForce.y()),
				number(totalForce.z()),
				value(row, "pressure_jump_pa"),
				value(row, "mass_flux_kg_s_m2"),
				value(row, "ideal_momentum_power_loading_w_m2"),
				value(row, "far_wake_axial_velocity_world_x_mps"),
				value(row, "far_wake_axial_velocity_world_y_mps"),
				value(row, "far_wake_axial_velocity_world_z_mps"),
				number(reactionTorque.x()),
				number(reactionTorque.y()),
				number(reactionTorque.z()),
				number(wakeAngularMomentumTorque.x()),
				number(wakeAngularMomentumTorque.y()),
				number(wakeAngularMomentumTorque.z()),
				number(wakeAngularMomentumTorqueDensity.x()),
				number(wakeAngularMomentumTorqueDensity.y()),
				number(wakeAngularMomentumTorqueDensity.z()),
				number(angularMomentumSwirlRadiusMeters),
				number(wakeTangentialVelocityMetersPerSecond),
				number(wakeSwirlSupportRadiusMeters),
				number(wakeSwirlAngularVelocityRadiansPerSecond),
				value(row, "wake_swirl_reference_point_world_x_m"),
				value(row, "wake_swirl_reference_point_world_y_m"),
				value(row, "wake_swirl_reference_point_world_z_m"),
				value(row, "wake_swirl_velocity_world_x_mps"),
				value(row, "wake_swirl_velocity_world_y_mps"),
				value(row, "wake_swirl_velocity_world_z_mps"),
				value(row, "wake_swirl_kinetic_power_w"),
				value(row, "total_wake_kinetic_power_w"),
				value(row, "wake_swirl_kinetic_power_over_shaft_power"),
				value(row, "total_wake_kinetic_power_over_shaft_power"),
				value(row, "query_j"),
				value(row, "query_rpm"),
				value(row, "effective_j"),
				value(row, "effective_rpm"),
				value(row, "ct"),
				value(row, "cp"),
				value(row, "eta")
		);
	}

	private static double wakeSwirlSupportRadiusMeters(Map<String, String> row) {
		double farWakeEquivalentRadiusMeters = number(row, "far_wake_equivalent_radius_m");
		if (Double.isFinite(farWakeEquivalentRadiusMeters) && farWakeEquivalentRadiusMeters > 0.0) {
			return farWakeEquivalentRadiusMeters;
		}
		double diskRadiusMeters = number(row, "disk_radius_m");
		return Double.isFinite(diskRadiusMeters) && diskRadiusMeters > 0.0
				? diskRadiusMeters
				: 0.0;
	}

	private static double wakeSwirlAngularVelocityRadiansPerSecond(
			boolean applied,
			double angularMomentumSwirlRadiusMeters,
			double wakeTangentialVelocityMetersPerSecond,
			double wakeSwirlSupportRadiusMeters
	) {
		double specificAngularMomentumMetersSquaredPerSecond =
				angularMomentumSwirlRadiusMeters * wakeTangentialVelocityMetersPerSecond;
		if (!applied
				|| wakeSwirlSupportRadiusMeters <= 0.0
				|| specificAngularMomentumMetersSquaredPerSecond <= 0.0) {
			return 0.0;
		}
		return 2.0
				* specificAngularMomentumMetersSquaredPerSecond
				/ (wakeSwirlSupportRadiusMeters * wakeSwirlSupportRadiusMeters);
	}

	private static String sourceName(String preset, String caseName, String rowKind, int rotorIndex) {
		return sanitize("ctcpj_" + preset + "_" + caseName + "_" + rowKind + "_rotor_" + rotorIndex);
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
				.map(CtCpJActuatorDiskOpenFoamSourceTableExporter::normalizeHeader)
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

	private static String text(Map<String, String> row, String columnName) {
		return row.getOrDefault(columnName, "");
	}

	private static String value(Map<String, String> row, String columnName) {
		return number(number(row, columnName));
	}

	private static double number(Map<String, String> row, String columnName) {
		String value = row.get(columnName);
		return value == null || value.isBlank() ? Double.NaN : Double.parseDouble(value);
	}

	private static Vec3 vector(Map<String, String> row, String x, String y, String z) {
		return new Vec3(number(row, x), number(row, y), number(row, z));
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
