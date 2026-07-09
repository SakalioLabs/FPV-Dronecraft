package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CtCpJActuatorDiskVoxelSourceFieldExporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;
	private static final double CELL_SIZE = 0.04;
	private static final int PADDING_CELLS = 1;
	private static final int SUBCELL_SAMPLES = 3;

	@Test
	void csvLinesExportConservativeVoxelFieldsForHoverForwardHighJAndBlockedCases() {
		List<String> lines = CtCpJActuatorDiskVoxelSourceFieldExporter.csvLines(
				"apDrone",
				RHO,
				SOURCE_THICKNESS,
				CELL_SIZE,
				PADDING_CELLS,
				SUBCELL_SAMPLES,
				25.0,
				0.0
		);
		Map<String, Integer> columns = columns(lines);

		assertTrue(lines.get(0).startsWith("preset,case,row_kind,source_group_name,grid_status"));
		assertFalse(lines.stream().anyMatch(line -> line.contains("NaN")));
		assertTrue(lines.size() > 100);

		List<Map<String, String>> hover =
				recordsFor(lines, columns, "static_anchored_source_hover", "raw_source");
		List<Map<String, String>> mid =
				recordsFor(lines, columns, "static_anchored_source_mid_j", "raw_source");
		List<Map<String, String>> high =
				recordsFor(lines, columns, "static_anchored_source_high_j", "raw_source");
		List<Map<String, String>> blocked =
				recordsFor(lines, columns, "static_anchored_source_high_j_block", "raw_source");

		assertActiveGroup(hover);
		assertActiveGroup(mid);
		assertActiveGroup(high);
		assertConservativeClosure(hover);
		assertConservativeClosure(mid);
		assertConservativeClosure(high);

		assertEquals(0.0, number(hover.get(0), "query_j"), 1.0e-15);
		assertEquals(4, integer(hover.get(0), "applied_source_count"));
		assertEquals(SUBCELL_SAMPLES * SUBCELL_SAMPLES * SUBCELL_SAMPLES,
				integer(hover.get(0), "total_subsample_count"));
		assertEquals(0.0, number(hover.get(0), "source_axis_world_x"), 1.0e-15);
		assertEquals(1.0, number(hover.get(0), "source_axis_world_y"), 1.0e-15);
		assertEquals(0.0, number(hover.get(0), "source_axis_world_z"), 1.0e-15);
		assertTrue(number(hover.get(0), "target_body_force_world_y_n") > 0.0);
		assertEquals(sourceTermGroupSum("static_anchored_source_hover", "raw_source",
						"equivalent_body_force_integral_world_y_n"),
				number(hover.get(0), "target_body_force_world_y_n"), 1.0e-12);
		assertEquals(number(hover.get(0), "body_force_density_world_y_n_m3") / RHO,
				number(hover.get(0), "acceleration_source_world_y_m_s2"), 1.0e-12);
		assertEquals(number(hover.get(0), "body_force_density_world_y_n_m3")
						* number(hover.get(0), "cell_volume_m3"),
				number(hover.get(0), "integrated_body_force_world_y_n"), 1.0e-10);
		assertEquals(number(hover.get(0), "wake_angular_momentum_torque_density_world_y_nm_m3")
						* number(hover.get(0), "cell_volume_m3"),
				number(hover.get(0), "integrated_wake_angular_momentum_torque_world_y_nm"), 1.0e-12);
		assertEquals(0.0, number(hover.get(0), "actuator_disk_axial_velocity_world_x_mps"), 1.0e-15);
		assertTrue(number(hover.get(0), "actuator_disk_axial_velocity_world_y_mps") > 0.0);
		assertEquals(0.0, number(hover.get(0), "actuator_disk_axial_velocity_world_z_mps"), 1.0e-15);
		assertTrue(hover.stream().allMatch(row ->
				Math.abs(number(row, "freestream_velocity_world_x_mps")) <= 1.0e-12
						&& Math.abs(number(row, "freestream_velocity_world_y_mps")) <= 1.0e-12
						&& Math.abs(number(row, "freestream_velocity_world_z_mps")) <= 1.0e-12));
		assertTrue(hover.stream().anyMatch(row -> number(row, "far_wake_axial_velocity_world_y_mps")
				> number(row, "actuator_disk_axial_velocity_world_y_mps")));
		assertEquals(0.0, number(hover.get(0), "cell_freestream_axial_speed_mps"), 1.0e-15);
		assertEquals(number(hover.get(0), "far_wake_axial_velocity_world_y_mps"),
				number(hover.get(0), "cell_far_wake_axial_speed_mps"), 1.0e-12);
		assertTrue(number(hover.get(0), "wake_swirl_kinetic_power_loading_w_m2") >= 0.0);
		assertTrue(number(hover.get(0), "total_wake_kinetic_power_loading_w_m2")
				>= number(hover.get(0), "ideal_momentum_power_loading_w_m2"));
		assertEquals(number(hover.get(0), "ideal_momentum_power_loading_w_m2")
						* number(hover.get(0), "cell_volume_m3") / SOURCE_THICKNESS,
				number(hover.get(0), "integrated_ideal_momentum_power_w"), 1.0e-12);
		assertEquals(number(hover.get(0), "wake_swirl_kinetic_power_loading_w_m2")
						* number(hover.get(0), "cell_volume_m3") / SOURCE_THICKNESS,
				number(hover.get(0), "integrated_wake_swirl_kinetic_power_w"), 1.0e-12);
		assertEquals(number(hover.get(0), "total_wake_kinetic_power_loading_w_m2")
						* number(hover.get(0), "cell_volume_m3") / SOURCE_THICKNESS,
				number(hover.get(0), "integrated_total_wake_kinetic_power_w"), 1.0e-12);

		assertEquals(0.4064, number(mid.get(0), "query_j"), 1.0e-12);
		assertTrue(number(mid.get(0), "eta") > 0.0);
		assertTrue(number(mid.get(0), "target_body_force_world_y_n") > 0.0);
		assertTrue(mid.stream().anyMatch(row ->
				number(row, "actuator_disk_axial_velocity_world_y_mps") > 0.0));
		assertTrue(mid.stream().anyMatch(row -> number(row, "far_wake_axial_velocity_world_y_mps") > 0.0));
		assertTrue(mid.stream().allMatch(row ->
				number(row, "freestream_velocity_world_y_mps") > 0.0));
		assertTrue(mid.stream().allMatch(row ->
				number(row, "cell_freestream_axial_speed_mps") > 0.0));
		assertEquals(number(mid.get(0), "freestream_velocity_world_y_mps"),
				number(mid.get(0), "cell_freestream_axial_speed_mps"), 1.0e-12);
		assertTrue(mid.stream().allMatch(row ->
				Math.abs(number(row, "freestream_velocity_world_x_mps")) <= 1.0e-15
						&& Math.abs(number(row, "freestream_velocity_world_z_mps")) <= 1.0e-15));

		assertEquals(0.73152, number(high.get(0), "query_j"), 1.0e-12);
		assertEquals(-1.0, number(high.get(0), "source_axis_world_x"), 1.0e-15);
		assertEquals(0.0, number(high.get(0), "source_axis_world_y"), 1.0e-15);
		assertEquals(0.0, number(high.get(0), "source_axis_world_z"), 1.0e-15);
		assertTrue(number(high.get(0), "target_body_force_world_x_n") < 0.0);
		assertEquals(0.0, number(high.get(0), "target_body_force_world_y_n"), 1.0e-12);
		assertTrue(high.stream().allMatch(row -> number(row, "freestream_velocity_world_x_mps") < 0.0));
		assertTrue(high.stream().allMatch(row -> number(row, "cell_freestream_axial_speed_mps") > 0.0));
		assertEquals(-number(high.get(0), "freestream_velocity_world_x_mps"),
				number(high.get(0), "cell_freestream_axial_speed_mps"), 1.0e-12);
		assertTrue(high.stream().allMatch(row ->
				Math.abs(number(row, "freestream_velocity_world_y_mps")) <= 1.0e-12
						&& Math.abs(number(row, "freestream_velocity_world_z_mps")) <= 1.0e-12));
		assertTrue(integer(high.get(0), "grid_count_x") > 1);
		assertTrue(integer(high.get(0), "grid_count_y") > 1);
		assertTrue(integer(high.get(0), "grid_count_z") > 1);

		assertEquals(1, blocked.size());
		assertEquals("EMPTY_SOURCE_FIELD", blocked.get(0).get("grid_status"));
		assertEquals("true", blocked.get(0).get("blocked"));
		assertEquals(0, integer(blocked.get(0), "applied_source_count"));
		assertEquals(0, integer(blocked.get(0), "active_cell_count"));
		assertEquals("false", blocked.get(0).get("cell_active"));
		assertEquals(0.0, number(blocked.get(0), "target_body_force_world_y_n"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "voxel_body_force_world_y_n"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "sampled_source_volume_m3"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "target_total_wake_kinetic_power_w"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "voxel_total_wake_kinetic_power_w"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "integrated_total_wake_kinetic_power_w"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "target_axial_momentum_thrust_n"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "voxel_axial_momentum_thrust_n"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "integrated_axial_momentum_thrust_n"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "target_axial_momentum_power_w"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "voxel_axial_momentum_power_w"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "integrated_axial_momentum_power_w"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "actuator_disk_axial_velocity_world_y_mps"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "cell_freestream_axial_speed_mps"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "cell_far_wake_axial_speed_mps"), 1.0e-15);
		assertEquals(0.0, number(blocked.get(0), "freestream_velocity_world_y_mps"), 1.0e-15);
	}

	@Test
	void writeCreatesParentDirectoriesAndCsvFile(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("nested").resolve("voxel-source-field.csv");

		CtCpJActuatorDiskVoxelSourceFieldExporter.write("apDrone", output, RHO);

		List<String> lines = Files.readAllLines(output);
		assertTrue(lines.size() > 100);
		assertTrue(lines.get(0).contains("body_force_density_world_y_n_m3"));
		assertTrue(lines.get(0).contains("acceleration_source_world_y_m_s2"));
		assertTrue(lines.get(0).contains("integrated_body_force_world_y_n"));
		assertTrue(lines.get(0).contains("wake_angular_momentum_torque_density_world_y_nm_m3"));
		assertTrue(lines.get(0).contains("wake_swirl_kinetic_power_loading_w_m2"));
		assertTrue(lines.get(0).contains("total_wake_kinetic_power_loading_w_m2"));
		assertTrue(lines.get(0).contains("target_total_wake_kinetic_power_w"));
		assertTrue(lines.get(0).contains("voxel_total_wake_kinetic_power_w"));
		assertTrue(lines.get(0).contains("integrated_total_wake_kinetic_power_w"));
		assertTrue(lines.get(0).contains("source_axis_world_y"));
		assertTrue(lines.get(0).contains("target_axial_momentum_thrust_n"));
		assertTrue(lines.get(0).contains("voxel_axial_momentum_power_w"));
		assertTrue(lines.get(0).contains("cell_freestream_axial_speed_mps"));
		assertTrue(lines.get(0).contains("integrated_axial_momentum_power_w"));
		assertTrue(lines.get(0).contains("actuator_disk_axial_velocity_world_y_mps"));
		assertTrue(lines.get(0).contains("freestream_velocity_world_y_mps"));
		assertTrue(lines.get(0).contains("target_wake_velocity_world_y_mps"));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_source_high_j_block,raw_source,")
						&& line.contains(",EMPTY_SOURCE_FIELD,")));
	}

	private static void assertActiveGroup(List<Map<String, String>> rows) {
		assertFalse(rows.isEmpty());
		assertEquals("ACTIVE_SOURCE_FIELD", rows.get(0).get("grid_status"));
		assertEquals("false", rows.get(0).get("blocked"));
		assertEquals(4, integer(rows.get(0), "source_count"));
		assertTrue(integer(rows.get(0), "active_cell_count") > 0);
		assertTrue(integer(rows.get(0), "active_subsample_count") > 0);
		assertTrue(number(rows.get(0), "sampled_source_volume_m3") > 0.0);
		assertTrue(rows.stream().allMatch(row -> row.get("cell_active").equals("true")));
		assertTrue(rows.stream().allMatch(row -> number(row, "cell_volume_m3") > 0.0));
		assertTrue(rows.stream().allMatch(row -> number(row, "source_volume_fraction") > 0.0));
		assertTrue(rows.stream().allMatch(row -> number(row, "source_volume_fraction") <= 1.0));
	}

	private static void assertConservativeClosure(List<Map<String, String>> rows) {
		Map<String, String> first = rows.get(0);
		assertVectorClose(
				vector(first, "target_body_force_world", "_n"),
				vector(first, "voxel_body_force_world", "_n"),
				1.0e-10
		);
		assertVectorClose(
				vector(first, "target_wake_angular_momentum_torque_world", "_nm"),
				vector(first, "voxel_wake_angular_momentum_torque_world", "_nm"),
				1.0e-12
		);
		assertVectorClose(new double[] {0.0, 0.0, 0.0},
				vector(first, "body_force_residual_world", "_n"), 1.0e-10);
		assertVectorClose(new double[] {0.0, 0.0, 0.0},
				vector(first, "wake_angular_momentum_torque_residual_world", "_nm"), 1.0e-12);
		assertVectorClose(
				vector(first, "voxel_body_force_world", "_n"),
				sum(rows, "integrated_body_force_world", "_n"),
				1.0e-10
		);
		assertVectorClose(
				vector(first, "voxel_wake_angular_momentum_torque_world", "_nm"),
				sum(rows, "integrated_wake_angular_momentum_torque_world", "_nm"),
				1.0e-12
		);
		assertEquals(number(first, "target_ideal_momentum_power_w"),
				number(first, "voxel_ideal_momentum_power_w"), 1.0e-12);
		assertEquals(number(first, "target_wake_swirl_kinetic_power_w"),
				number(first, "voxel_wake_swirl_kinetic_power_w"), 1.0e-12);
		assertEquals(number(first, "target_total_wake_kinetic_power_w"),
				number(first, "voxel_total_wake_kinetic_power_w"), 1.0e-12);
		assertEquals(0.0, number(first, "ideal_momentum_power_residual_w"), 1.0e-12);
		assertEquals(0.0, number(first, "wake_swirl_kinetic_power_residual_w"), 1.0e-12);
		assertEquals(0.0, number(first, "total_wake_kinetic_power_residual_w"), 1.0e-12);
		assertEquals(sourceTermGroupSum(first.get("case"), first.get("row_kind"),
						"source_axial_momentum_thrust_n"),
				number(first, "target_axial_momentum_thrust_n"), 1.0e-10);
		assertEquals(sourceTermGroupSum(first.get("case"), first.get("row_kind"),
						"source_axial_momentum_power_w"),
				number(first, "target_axial_momentum_power_w"), 1.0e-10);
		assertEquals(dot(
						vector(first, "target_body_force_world", "_n"),
						vector(first, "source_axis_world", "")),
				number(first, "target_axial_momentum_thrust_n"), 1.0e-10);
		assertEquals(dot(
						vector(first, "voxel_body_force_world", "_n"),
						vector(first, "source_axis_world", "")),
				number(first, "voxel_axial_momentum_thrust_n"), 1.0e-10);
		assertEquals(number(first, "target_axial_momentum_thrust_n"),
				number(first, "voxel_axial_momentum_thrust_n"), 1.0e-10);
		assertEquals(number(first, "target_axial_momentum_power_w"),
				number(first, "voxel_axial_momentum_power_w"), 1.0e-10);
		assertEquals(0.0, number(first, "axial_momentum_thrust_residual_n"), 1.0e-10);
		assertEquals(0.0, number(first, "axial_momentum_thrust_residual_fraction"), 1.0e-10);
		assertEquals(0.0, number(first, "axial_momentum_power_residual_w"), 1.0e-10);
		assertEquals(0.0, number(first, "axial_momentum_power_residual_fraction"), 1.0e-10);
		assertEquals(number(first, "voxel_ideal_momentum_power_w"),
				sumScalar(rows, "integrated_ideal_momentum_power_w"), 1.0e-10);
		assertEquals(number(first, "voxel_wake_swirl_kinetic_power_w"),
				sumScalar(rows, "integrated_wake_swirl_kinetic_power_w"), 1.0e-10);
		assertEquals(number(first, "voxel_total_wake_kinetic_power_w"),
				sumScalar(rows, "integrated_total_wake_kinetic_power_w"), 1.0e-10);
		assertEquals(number(first, "voxel_axial_momentum_thrust_n"),
				sumScalar(rows, "integrated_axial_momentum_thrust_n"), 1.0e-10);
		assertEquals(number(first, "voxel_axial_momentum_power_w"),
				sumScalar(rows, "integrated_axial_momentum_power_w"), 1.0e-10);
		assertEquals(sourceTermGroupSum(first.get("case"), first.get("row_kind"),
						"wake_swirl_kinetic_power_w"),
				sumCellPower(rows, "wake_swirl_kinetic_power_loading_w_m2"), 1.0e-10);
		assertEquals(sourceTermGroupSum(first.get("case"), first.get("row_kind"),
						"total_wake_kinetic_power_w"),
				sumCellPower(rows, "total_wake_kinetic_power_loading_w_m2"), 1.0e-10);
	}

	private static List<Map<String, String>> recordsFor(
			List<String> lines,
			Map<String, Integer> columns,
			String caseName,
			String rowKind
	) {
		return lines.stream()
				.skip(1)
				.map(line -> record(line, columns))
				.filter(row -> row.get("case").equals(caseName)
						&& row.get("row_kind").equals(rowKind))
				.toList();
	}

	private static double sourceTermGroupSum(String caseName, String rowKind, String columnName) {
		List<String> lines = CtCpJActuatorDiskSourceTermExporter.csvLines(
				"apDrone",
				RHO,
				SOURCE_THICKNESS,
				25.0,
				0.0
		);
		Map<String, Integer> columns = columns(lines);
		return lines.stream()
				.skip(1)
				.map(line -> record(line, columns))
				.filter(row -> row.get("case").equals(caseName)
						&& row.get("row_kind").equals(rowKind))
				.mapToDouble(row -> number(row, columnName))
				.sum();
	}

	private static Map<String, Integer> columns(List<String> lines) {
		String[] header = lines.get(0).split(",", -1);
		Map<String, Integer> columns = new LinkedHashMap<>();
		for (int i = 0; i < header.length; i++) {
			columns.put(header[i], i);
		}
		return columns;
	}

	private static Map<String, String> record(String line, Map<String, Integer> columns) {
		String[] cells = line.split(",", -1);
		Map<String, String> record = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : columns.entrySet()) {
			record.put(entry.getKey(), cells[entry.getValue()]);
		}
		return record;
	}

	private static double[] vector(Map<String, String> row, String prefix, String suffix) {
		return new double[] {
				number(row, prefix + "_x" + suffix),
				number(row, prefix + "_y" + suffix),
				number(row, prefix + "_z" + suffix)
		};
	}

	private static double[] sum(List<Map<String, String>> rows, String prefix, String suffix) {
		double[] sum = new double[3];
		for (Map<String, String> row : rows) {
			double[] vector = vector(row, prefix, suffix);
			sum[0] += vector[0];
			sum[1] += vector[1];
			sum[2] += vector[2];
		}
		return sum;
	}

	private static double sumCellPower(List<Map<String, String>> rows, String loadingColumn) {
		double sum = 0.0;
		for (Map<String, String> row : rows) {
			sum += number(row, loadingColumn) * number(row, "cell_volume_m3") / SOURCE_THICKNESS;
		}
		return sum;
	}

	private static double sumScalar(List<Map<String, String>> rows, String columnName) {
		double sum = 0.0;
		for (Map<String, String> row : rows) {
			sum += number(row, columnName);
		}
		return sum;
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static void assertVectorClose(double[] expected, double[] actual, double tolerance) {
		assertEquals(expected[0], actual[0], tolerance);
		assertEquals(expected[1], actual[1], tolerance);
		assertEquals(expected[2], actual[2], tolerance);
	}

	private static int integer(Map<String, String> row, String columnName) {
		return Integer.parseInt(row.get(columnName));
	}

	private static double number(Map<String, String> row, String columnName) {
		return Double.parseDouble(row.get(columnName));
	}
}
