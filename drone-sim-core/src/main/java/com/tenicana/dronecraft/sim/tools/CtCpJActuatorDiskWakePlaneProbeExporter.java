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

public final class CtCpJActuatorDiskWakePlaneProbeExporter {
	private static final List<Double> PROBE_DISTANCE_RADII = List.of(0.5, 1.0, 2.0, 4.0);
	private static final List<PlaneSample> PLANE_SAMPLES = List.of(
			new PlaneSample("center", 0.0, 0.0),
			new PlaneSample("u_pos_0p5", 0.5, 0.0),
			new PlaneSample("u_neg_0p5", -0.5, 0.0),
			new PlaneSample("v_pos_0p5", 0.0, 0.5),
			new PlaneSample("v_neg_0p5", 0.0, -0.5),
			new PlaneSample("u_pos_1p0", 1.0, 0.0),
			new PlaneSample("u_neg_1p0", -1.0, 0.0),
			new PlaneSample("v_pos_1p0", 0.0, 1.0),
			new PlaneSample("v_neg_1p0", 0.0, -1.0),
			new PlaneSample("u_pos_1p25", 1.25, 0.0),
			new PlaneSample("u_neg_1p25", -1.25, 0.0),
			new PlaneSample("v_pos_1p25", 0.0, 1.25),
			new PlaneSample("v_neg_1p25", 0.0, -1.25)
	);
	private static final String PROBE_KIND = "wake_plane_top_hat";
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
			"probe_kind",
			"plane_sample",
			"probe_distance_radius",
			"probe_distance_m",
			"plane_offset_u_radius",
			"plane_offset_v_radius",
			"plane_radial_fraction",
			"probe_region",
			"probe_point_world_x_m",
			"probe_point_world_y_m",
			"probe_point_world_z_m",
			"disk_center_world_x_m",
			"disk_center_world_y_m",
			"disk_center_world_z_m",
			"disk_normal_world_x",
			"disk_normal_world_y",
			"disk_normal_world_z",
			"disk_tangent_u_world_x",
			"disk_tangent_u_world_y",
			"disk_tangent_u_world_z",
			"disk_tangent_v_world_x",
			"disk_tangent_v_world_y",
			"disk_tangent_v_world_z",
			"disk_radius_m",
			"wake_support_radius_m",
			"source_half_thickness_m",
			"source_bounding_sphere_radius_m",
			"expected_top_hat_velocity_world_x_mps",
			"expected_top_hat_velocity_world_y_mps",
			"expected_top_hat_velocity_world_z_mps",
			"expected_top_hat_speed_mps",
			"expected_axial_velocity_mps",
			"expected_transverse_velocity_mps",
			"body_force_density_world_x_n_m3",
			"body_force_density_world_y_n_m3",
			"body_force_density_world_z_n_m3",
			"total_force_world_x_n",
			"total_force_world_y_n",
			"total_force_world_z_n",
			"pressure_jump_pa",
			"mass_flux_kg_s_m2",
			"ideal_momentum_power_loading_w_m2",
			"query_j",
			"query_rpm",
			"effective_j",
			"effective_rpm",
			"ct",
			"cp",
			"eta"
	);

	private CtCpJActuatorDiskWakePlaneProbeExporter() {
	}

	private record PlaneSample(String name, double uRadius, double vRadius) {
		double radialFraction() {
			return Math.sqrt(uRadius * uRadius + vRadius * vRadius);
		}
	}

	public static void main(String[] args) throws IOException {
		String presetName = args.length >= 1 && !args[0].isBlank()
				? args[0]
				: PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME;
		Path output = args.length >= 2 && !args[1].isBlank()
				? Path.of(args[1])
				: Path.of("build", "ct-cp-j-actuator-disk-wake-probes", presetName + "-wake-plane-probes.csv");
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
				CtCpJActuatorDiskOpenFoamSourceTableExporter.csvLines(
						presetName,
						airDensityKgPerCubicMeter,
						sourceThicknessMeters,
						ambientTemperatureCelsius,
						ambientHumidity
				)));
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (Map<String, String> sourceRow : sourceRows) {
			for (double probeDistanceRadius : PROBE_DISTANCE_RADII) {
				for (PlaneSample sample : PLANE_SAMPLES) {
					lines.add(csvLine(sourceRow, probeDistanceRadius, sample));
				}
			}
		}
		return List.copyOf(lines);
	}

	private static String csvLine(Map<String, String> row, double probeDistanceRadius, PlaneSample sample) {
		Vec3 center = vector(row, "center_x_m", "center_y_m", "center_z_m");
		Vec3 normal = vector(row, "normal_x", "normal_y", "normal_z");
		Vec3 tangentU = vector(row, "tangent_u_x", "tangent_u_y", "tangent_u_z");
		Vec3 tangentV = vector(row, "tangent_v_x", "tangent_v_y", "tangent_v_z");
		double diskRadius = number(row, "radius_m");
		double probeDistanceMeters = diskRadius * probeDistanceRadius;
		Vec3 planeOrigin = center.add(normal.multiply(probeDistanceMeters));
		Vec3 planeOffset = tangentU.multiply(sample.uRadius() * diskRadius)
				.add(tangentV.multiply(sample.vRadius() * diskRadius));
		Vec3 probePoint = planeOrigin.add(planeOffset);
		double radialFraction = sample.radialFraction();
		double wakeSupportRadius = number(row, "wake_swirl_support_radius_m");
		double radialDistanceMeters = radialFraction * diskRadius;
		boolean wakeCore = sourceEnabled(row)
				&& wakeSupportRadius > 0.0
				&& radialDistanceMeters <= wakeSupportRadius + 1.0e-12;
		Vec3 expectedVelocity = wakeCore
				? vector(
						row,
						"far_wake_axial_velocity_x_mps",
						"far_wake_axial_velocity_y_mps",
						"far_wake_axial_velocity_z_mps")
				: Vec3.ZERO;
		Vec3 bodyForceDensity = sourceEnabled(row)
				? vector(
						row,
						"body_force_density_x_n_m3",
						"body_force_density_y_n_m3",
						"body_force_density_z_n_m3")
				: Vec3.ZERO;
		Vec3 totalForce = sourceEnabled(row)
				? vector(row, "total_force_x_n", "total_force_y_n", "total_force_z_n")
				: Vec3.ZERO;
		double axialVelocity = expectedVelocity.dot(normal);
		double transverseVelocity = expectedVelocity.subtract(normal.multiply(axialVelocity)).length();
		return String.join(",",
				escape(text(row, "preset")),
				escape(text(row, "case")),
				escape(text(row, "row_kind")),
				text(row, "rotor_index"),
				escape(text(row, "source_name")),
				text(row, "source_enabled"),
				escape(text(row, "lookup_status")),
				text(row, "clamped"),
				text(row, "blocked"),
				PROBE_KIND,
				escape(sample.name()),
				number(probeDistanceRadius),
				number(probeDistanceMeters),
				number(sample.uRadius()),
				number(sample.vRadius()),
				number(radialFraction),
				probeRegion(radialFraction, wakeCore),
				number(probePoint.x()),
				number(probePoint.y()),
				number(probePoint.z()),
				number(center.x()),
				number(center.y()),
				number(center.z()),
				number(normal.x()),
				number(normal.y()),
				number(normal.z()),
				number(tangentU.x()),
				number(tangentU.y()),
				number(tangentU.z()),
				number(tangentV.x()),
				number(tangentV.y()),
				number(tangentV.z()),
				number(diskRadius),
				number(wakeSupportRadius),
				value(row, "half_thickness_m"),
				value(row, "bounding_sphere_radius_m"),
				number(expectedVelocity.x()),
				number(expectedVelocity.y()),
				number(expectedVelocity.z()),
				number(expectedVelocity.length()),
				number(axialVelocity),
				number(transverseVelocity),
				number(bodyForceDensity.x()),
				number(bodyForceDensity.y()),
				number(bodyForceDensity.z()),
				number(totalForce.x()),
				number(totalForce.y()),
				number(totalForce.z()),
				wakeCore ? value(row, "pressure_jump_pa") : "0",
				wakeCore ? value(row, "mass_flux_kg_s_m2") : "0",
				wakeCore ? value(row, "ideal_momentum_power_loading_w_m2") : "0",
				value(row, "query_j"),
				value(row, "query_rpm"),
				value(row, "effective_j"),
				value(row, "effective_rpm"),
				value(row, "ct"),
				value(row, "cp"),
				value(row, "eta")
		);
	}

	private static String probeRegion(double radialFraction, boolean wakeCore) {
		if (radialFraction <= 1.0e-12) {
			return "centerline";
		}
		if (wakeCore) {
			return "wake_core_top_hat";
		}
		return "outer_reference";
	}

	private static boolean sourceEnabled(Map<String, String> row) {
		return Boolean.parseBoolean(text(row, "source_enabled"));
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
				.map(CtCpJActuatorDiskWakePlaneProbeExporter::normalizeHeader)
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
